package com.ibm.streamsx.objectstorage.unitest.sink;

import java.io.UnsupportedEncodingException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;

import com.ibm.streams.operator.Tuple;
import com.ibm.streams.operator.logging.TraceLevel;
import com.ibm.streams.operator.types.RString;
import com.ibm.streamsx.objectstorage.test.AuthenticationMode;
import com.ibm.streamsx.objectstorage.test.Constants;
import com.ibm.streamsx.objectstorage.test.Utils;
import com.ibm.streamsx.topology.spl.SPLStream;
import com.ibm.streamsx.topology.tester.Condition;



/**
 * Tests object rolling policy "by object size".
 * Sink operator input schema:  tuple<rstring tsStr, rstring customerId, float64 latitude, float64 longitude, timestamp ts>
 * Sink operator parameterization:
 *  1. output object size: 10K
 *  2. storage format: parquet
 *  3. parquet partition attributes: customerId
 * 
 * @author streamsadmin
 *
 */
public class TestEmptyPartition extends TestObjectStorageBaseSink {

	public TestEmptyPartition() {
		super();
	}
	
	
	@Override
	public void initTestData() throws Exception {	
		//configureTest(Level.FINEST, Constants.STANDALONE);

		// data injection composite	
		_tupleRate = 100; // tuple rate in tuples per second
		_testDataFileName = Constants.OS_MULTI_ATTR_TEST_OBJECT_NAME;
		
		String injectionOutShema = "tuple<rstring tsStr, rstring customerId, float64 latitude, float64 longitude, timestamp ts>"; // input schema
				
		// generates injection logic based on input loaded from file
		// populates sample data stream
		Tuple[] testTuples = Utils.genTuplesFromFile(_osDefaultTestDataFileAbsPath, _testDataFileName, Constants.TEST_DATA_FILE_DELIMITER, injectionOutShema);
				
		_testData = Utils.getTestStreamWithEmptyStr(_testTopology, testTuples, injectionOutShema, _tupleRate); 
	}

	@Test
	public void testCOSBasicAuthSchema() throws Exception {
		String testName = Constants.COS + TestEmptyPartition.class.getName();		
		build(testName, TraceLevel.TRACE, Constants.STANDALONE, Constants.COS, AuthenticationMode.BASIC, Constants.DEFAULT_BUCKET_NAME);
		createObjectTest(Constants.COS);	
	}
	
		
	@Override
	public void genTestSpecificParams(Map<String, Object> params) throws UnsupportedEncodingException {
		String storageFormat = "parquet";
		String objectName = "PROJECT_NAME/TEAM_NAME/TABLE_NAME/" + _protocol + TestEmptyPartition.class.getSimpleName()  + "%OBJECTNUM." + storageFormat; 
		params.put("objectName", objectName);
		params.put("storageFormat", storageFormat);
		//params.put("vmArg", "-Dcom.ibm.tools.attach.enable=yes");
		//params.put("bytesPerObject", 10 * 1024L); // 1OK per object
		params.put("timePerObject", 20.0); // 1OK per object
		params.put("nullPartitionDefaultValue", "__STREAMS_DEFAULT_PARTITION__");
		String[] partitionAttrs = {"customerId"};
		params.put("partitionValueAttributes", partitionAttrs);
	}


	
	public int getTestTimeout() {
		return 40;
	}

	public void validateResults(SPLStream osSink, String protocol) throws Exception {
		// Sink operator generates single output tuple per object
		// containing object name and size
		int minExpectedTupleCount = 10;
		Condition<Long> expectedCount = _tester.atLeastTupleCount(osSink, minExpectedTupleCount);

		
		// @TODO:should returned object name starts with "/"
		Tuple[] expectedTuplesArr = new Tuple[minExpectedTupleCount];
		for (int i = 0; i < minExpectedTupleCount; i++) {
			String expectedObjectName = "customerId=" + i + "/" + ((String) _testConfiguration.get("objectName"));		
			System.out.println("Expected Object name: " + expectedObjectName);
			
			expectedTuplesArr[i] = Constants.OS_SINK_OUT_SCHEMA
					.getTuple(new Object[] { new RString(expectedObjectName), new Long(9178) });
			
		}
		//Condition<List<Tuple>> expectedTuples = _tester.tupleContents(osSink, expectedTuplesArr);

		// build and run application
		complete(_tester, expectedCount, getTestTimeout(), TimeUnit.SECONDS);		

		// check that at least one tuple returned
		//assertTrue(expectedCount.toString(), expectedCount.valid());
		// @TODO: add logic for object name validation
		//assertTrue(expectedTuples.toString(), expectedTuples.valid());
	}

}
