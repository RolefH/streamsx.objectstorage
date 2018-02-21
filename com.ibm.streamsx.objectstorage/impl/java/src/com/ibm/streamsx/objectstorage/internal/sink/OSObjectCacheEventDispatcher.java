package com.ibm.streamsx.objectstorage.internal.sink;

import org.ehcache.Cache;
import org.ehcache.core.CacheConfigurationChangeListener;
import org.ehcache.core.CacheConfigurationProperty;
import org.ehcache.core.events.CacheEventDispatcher;
import org.ehcache.core.events.CacheEvents;
import org.ehcache.core.internal.events.EventListenerWrapper;
import org.ehcache.event.CacheEvent;
import org.ehcache.event.CacheEventListener;
import org.ehcache.event.EventFiring;
import org.ehcache.event.EventOrdering;
import org.ehcache.event.EventType;
import org.ehcache.core.spi.store.events.StoreEvent;
import org.ehcache.core.spi.store.events.StoreEventListener;
import org.ehcache.core.spi.store.events.StoreEventSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * Per-cache component that manages cache event listener registrations, and provides event delivery based on desired
 * firing mode for specified event types.
 * <p>
 * Use of this class is linked to having cache events on a {@link org.ehcache.UserManagedCache user managed cache}.
 * <p>
 * <em>Note on event ordering guarantees:</em> Events are received and transmitted to register listeners through the
 * registration of a {@link StoreEventListener} on the linked {@link StoreEventSource} which is responsible for event
 * ordering.
 */
public class OSObjectCacheEventDispatcher<K, V> implements CacheEventDispatcher<K, V> {

  private static final Logger LOGGER = LoggerFactory.getLogger(OSObjectCacheEventDispatcher.class);
  private final ExecutorService unOrderedExectuor;
  private final ExecutorService orderedExecutor;
  private int listenersCount = 0;
  private int orderedListenerCount = 0;
  private final List<EventListenerWrapper<K, V>> syncListenersList = new CopyOnWriteArrayList<>();
  private final List<EventListenerWrapper<K, V>> aSyncListenersList = new CopyOnWriteArrayList<>();
  private final StoreEventListener<K, V> eventListener = new StoreListener();
  private boolean updateFireRequired = false;
  
  private volatile Cache<K, V> listenerSource;
  private volatile StoreEventSource<K, V> storeEventSource;
  

  /**
   * Creates a new {@link CacheEventDispatcher} instance that will use the provided {@link ExecutorService} to handle
   * events firing.
   *
   * @param unOrderedExecutor the executor service used when ordering is not required
   * @param orderedExecutor the executor service used when ordering is required
   */
  public OSObjectCacheEventDispatcher(ExecutorService unOrderedExecutor, ExecutorService orderedExecutor) {
    this.unOrderedExectuor = unOrderedExecutor;
    this.orderedExecutor = orderedExecutor;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void registerCacheEventListener(CacheEventListener<? super K, ? super V> listener,
                                  EventOrdering ordering, EventFiring firing, EnumSet<EventType> forEventTypes) {
    EventListenerWrapper<K, V> wrapper = new EventListenerWrapper<>(listener, firing, ordering, forEventTypes);
    
    // performance optimization - without it even
    // when no listeners are registered to UPDATED event
    // it will be submitted to Executor (but not consumed by listeners).
    // Due to the way EHCache used by ObjectStorageSink
    // we have to avoid the submit if no listeners are registered for this event.
    if (forEventTypes.contains(EventType.UPDATED)) {
    	updateFireRequired = true;
    }
    	

    registerCacheEventListener(wrapper);
  }

  /**
   * Synchronized to make sure listener addition is atomic in order to prevent having the same listener registered
   * under multiple configurations
   *
   * @param wrapper the listener wrapper to register
   */
  private synchronized void registerCacheEventListener(EventListenerWrapper<K, V> wrapper) {
    if(aSyncListenersList.contains(wrapper) || syncListenersList.contains(wrapper)) {
      throw new IllegalStateException("Cache Event Listener already registered: " + wrapper.getListener());
    }

    if (wrapper.isOrdered() && orderedListenerCount++ == 0) {
      storeEventSource.setEventOrdering(true);
    }

    switch (wrapper.getFiringMode()) {
      case ASYNCHRONOUS:
        aSyncListenersList.add(wrapper);
        break;
      case SYNCHRONOUS:
        syncListenersList.add(wrapper);
        break;
      default:
        throw new AssertionError("Unhandled EventFiring value: " + wrapper.getFiringMode());
    }

    if (listenersCount++ == 0) {
      storeEventSource.addEventListener(eventListener);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void deregisterCacheEventListener(CacheEventListener<? super K, ? super V> listener) {
    EventListenerWrapper<K, V> wrapper = new EventListenerWrapper<>(listener);

    if (!removeWrapperFromList(wrapper, aSyncListenersList)) {
      if (!removeWrapperFromList(wrapper, syncListenersList)) {
        throw new IllegalStateException("Unknown cache event listener: " + listener);
      }
    }
  }

  /**OSObjectEventDispatchTask
   * Synchronized to make sure listener removal is atomic
   *
   * @param wrapper the listener wrapper to unregister
   * @param listenersList the listener list to remove from
   */
  private synchronized boolean removeWrapperFromList(EventListenerWrapper wrapper, List<EventListenerWrapper<K, V>> listenersList) {
    int index = listenersList.indexOf(wrapper);
    if (index != -1) {
      EventListenerWrapper containedWrapper = listenersList.remove(index);
      if(containedWrapper.isOrdered() && --orderedListenerCount == 0) {
        storeEventSource.setEventOrdering(false);
      }
      if (--listenersCount == 0) {
        storeEventSource.removeEventListener(eventListener);
      }
      return true;
    }
    return false;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public synchronized void shutdown() {
    storeEventSource.removeEventListener(eventListener);
    storeEventSource.setEventOrdering(false);
    syncListenersList.clear();
    aSyncListenersList.clear();
    unOrderedExectuor.shutdown();
    orderedExecutor.shutdown();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public synchronized void setListenerSource(Cache<K, V> source) {
    this.listenerSource = source;
  }

  void onEvent(CacheEvent<K, V> event) {
    ExecutorService executor;
    if (storeEventSource.isEventOrdering()) {
      executor = orderedExecutor;
    } else {
      executor = unOrderedExectuor;
    }
    if (!aSyncListenersList.isEmpty()) {
      executor.submit(new OSObjectEventDispatchTask<>(event, aSyncListenersList));
    }
    if (!syncListenersList.isEmpty()) {
      Future<?> future = executor.submit(new OSObjectEventDispatchTask<>(event, syncListenersList));
      try {
        future.get();
      } catch (Exception e) {
        LOGGER.error("Exception received as result from synchronous listeners", e);
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @SuppressWarnings("unchecked")
  public List<CacheConfigurationChangeListener> getConfigurationChangeListeners() {
    List<CacheConfigurationChangeListener> configurationChangeListenerList = new ArrayList<>();
    configurationChangeListenerList.add(event -> {
      if (event.getProperty().equals(CacheConfigurationProperty.ADD_LISTENER)) {
        registerCacheEventListener((EventListenerWrapper<K, V>)event.getNewValue());
      } else if (event.getProperty().equals(CacheConfigurationProperty.REMOVE_LISTENER)) {
        CacheEventListener<? super K, ? super V> oldListener = (CacheEventListener<? super K, ? super V>)event.getOldValue();
        deregisterCacheEventListener(oldListener);
      }
    });
    return configurationChangeListenerList;
  }

  private final class StoreListener implements StoreEventListener<K, V> {

    @Override
    public void onEvent(StoreEvent<K, V> event) {
      switch (event.getType()) {
        case CREATED:
        	OSObjectCacheEventDispatcher.this.onEvent(CacheEvents.creation(event.getKey(), event.getNewValue(), listenerSource));
          break;
        case UPDATED:
        	if (updateFireRequired)
        		OSObjectCacheEventDispatcher.this.onEvent(CacheEvents.update(event.getKey(), event.getOldValue(), event.getNewValue(), listenerSource));
          break;
        case REMOVED:
        	OSObjectCacheEventDispatcher.this.onEvent(CacheEvents.removal(event.getKey(), event.getOldValue(), listenerSource));
          break;
        case EXPIRED:
        	OSObjectCacheEventDispatcher.this.onEvent(CacheEvents.expiry(event.getKey(), event.getOldValue(), listenerSource));
          break;
        case EVICTED:
        	OSObjectCacheEventDispatcher.this.onEvent(CacheEvents.eviction(event.getKey(), event.getOldValue(), listenerSource));
          break;
        default:
          throw new AssertionError("Unexpected StoreEvent value: " + event.getType());
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public synchronized void setStoreEventSource(StoreEventSource<K, V> eventSource) {
    this.storeEventSource = eventSource;
  }
}
