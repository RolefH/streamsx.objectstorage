package com.ibm.streamsx.objectstorage;

import com.ibm.streams.operator.model.InputPortSet;
import com.ibm.streams.operator.model.InputPorts;
import com.ibm.streams.operator.model.Libraries;
import com.ibm.streams.operator.model.OutputPortSet;
import com.ibm.streams.operator.model.OutputPorts;
import com.ibm.streams.operator.model.Parameter;
import com.ibm.streams.operator.model.PrimitiveOperator;
import com.ibm.streams.operator.model.SharedLoader;
import com.ibm.streams.operator.model.InputPortSet.WindowMode;
import com.ibm.streams.operator.model.InputPortSet.WindowPunctuationInputMode;
import com.ibm.streams.operator.model.OutputPortSet.WindowPunctuationOutputMode;


@PrimitiveOperator(name="ObjectStorageSource", namespace="com.ibm.streamsx.objectstorage",
description=ObjectStorageSource.DESC+ObjectStorageSource.BASIC_DESC+AbstractObjectStorageOperator.AUTHENTICATION_DESC)
@InputPorts({@InputPortSet(description="The `ObjectStorageSource` operator has one optional input port. If an input port is specified, the operator expects an input tuple with a single attribute of type rstring. The input tuples contain the object names that the operator opens for reading. The input port is non-mutating.", cardinality=1, optional=true, windowingMode=WindowMode.NonWindowed, windowPunctuationInputMode=WindowPunctuationInputMode.Oblivious)})
@OutputPorts({@OutputPortSet(description="The `ObjectStorageSource` operator has one output port. The tuples on the output port contain the data that is read from the objects. The operator supports two modes of reading.  To read an object line-by-line, the expected output schema of the output port is tuple<rstring line>. To read an object as binary, the expected output schema of the output port is tuple<blob data>. Use the blockSize parameter to control how much data to retrieve on each read. The operator includes a punctuation marker at the conclusion of each object.", cardinality=1, optional=false, windowPunctuationOutputMode=WindowPunctuationOutputMode.Generating)})
@Libraries({"opt/*","opt/downloaded/*" })
@SharedLoader
public class ObjectStorageSource extends BaseObjectStorageSource implements IObjectStorageAuth {
	
	public static final String DESC = 
			"Operator reads objects from S3 compliant object storage. The operator supports basic (HMAC) and IAM authentication.";

	public static final String BASIC_DESC = 					
			"\\n\\nThe operator opens an object on object storage and sends out its contents in tuple format on its output port.\\n" +
			"\\nIf the optional input port is not specified, the operator reads the object that is specified in the **objectName** parameter and " +
			"provides the object contents on the output port. If the optional input port is configured, the operator reads the objects that are " +
			"named by the attribute in the tuples that arrive on its input port and places a punctuation marker between each object." +
			"\\n"+
			"\\n# Behavior in a consistent region\\n" +
			"\\nThe operator can participate in a consistent region. " +
			"The operator can be at the start of a consistent region if there is no input port.\\n" +
			"\\nThe operator supports periodic and operator-driven consistent region policies. " +
			"If the consistent region policy is set as operator driven, the operator initiates a drain after a object is fully read. " +
			"If the consistent region policy is set as periodic, the operator respects the period setting and establishes consistent states accordingly. " +
			"This means that multiple consistent states can be established before a object is fully read.\\n" +
			"\\nAt checkpoint, the operator saves the current object name and object cursor location. " +
			"If the operator does not have an input port, upon application failures, the operator resets " +
			"the object cursor back to the checkpointed location, and starts replaying tuples from the cursor location. " +
			"If the operator has an input port and is in a consistent region, the operator relies on its upstream operators " +
			"to properly reply the object names for it to re-read the objects from the beginning."
		   	;
	
	@Parameter(optional=true, description = "Specifies username for connection to a Cloud Object Storage (COS), also known as 'AccessKeyID' for S3-compliant COS.")
	public void setObjectStorageUser(String objectStorageUser) {
		super.setUserID(objectStorageUser);
	}
	
	public String getObjectStorageUser() {
		return super.getUserID();
	}
	
	@Parameter(optional=true, description = "Specifies password for connection to a Cloud Object Storage (COS), also known as 'SecretAccessKey' for S3-compliant COS.")
	public void setObjectStoragePassword(String objectStoragePassword) {
		super.setPassword(objectStoragePassword);
	}
	
	public String getObjectStoragePassword() {
		return super.getPassword();
	}
	
	@Parameter(optional=false, description = "Specifies URI for connection to Cloud Object Storage (COS). For S3-compliant COS the URI should be in 'cos://bucket/ or s3a://bucket/' format. The bucket or container must exist. The operator does not create a bucket or container.")
	public void setObjectStorageURI(String objectStorageURI) {
		super.setURI(objectStorageURI);
	}
	
	public String getObjectStorageURI() {
		return super.getURI();
	}

	@Parameter(optional=false, description = "Specifies endpoint for connection to Cloud Object Storage (COS). For example, for S3 the endpoint might be 's3.amazonaws.com'.")
	public void setEndpoint(String endpoint) {
		super.setEndpoint(endpoint);
	}

	@Parameter(optional=true, description = "Specifies IAM API Key. Relevant for IAM authentication case only.")
	public void setIAMApiKey(String iamApiKey) {
		super.setIAMApiKey(iamApiKey);
	}
	
	public String getIAMApiKey() {
		return super.getIAMApiKey();
	}
	
	@Parameter(optional=true, description = "Specifies IAM token endpoint. Relevant for IAM authentication case only. Default value is 'https://iam.bluemix.net/oidc/token'.")
	public void setIAMTokenEndpoint(String iamTokenEndpoint) {
		super.setIAMTokenEndpoint(iamTokenEndpoint);;
	}
	
	public String getIAMTokenEndpoint() {
		return super.getIAMTokenEndpoint();
	}
	
	@Parameter(optional=true, description = "Specifies IAM service instance ID for connection to Cloud Object Storage (COS). Relevant for IAM authentication case only.")
	public void setIAMServiceInstanceId(String iamServiceInstanceId) {
		super.setIAMServiceInstanceId(iamServiceInstanceId);
	}
	
	public String getIAMServiceInstanceId() {
		return super.getIAMServiceInstanceId();
	}


}
