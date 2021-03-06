package com.ibm.streamsx.objectstorage.test;

public interface Credentials {
	
	/**
	 * Basic Credentials
	 */
	
	public String getEndpoint();

	public String getUserId();

	public String getPassword();

	public String getProjectId();
	

	/**
	 * IAM Credentials
	 */
	
	public String getIAMApiKey();
	
	public String getIAMServiceInstanceId();
	
	public String getIAMTokenEndpoint();
}
