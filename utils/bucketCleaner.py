import argparse
import sys
import fnmatch
import ibm_boto3
from ibm_botocore.client import Config

staging_credentials = {"API_KEY": "U80LGbtrsSGinOZ7e6SJ7IiDyDLE4iNt5IRcBZUrvgsU", 
					  "RESOURCE_ID": "92d7b7a2-50f4-4d35-b64e-4dc0a09653d9", 
					  "AUTH_ENDPOINT": "https://iam.stage1.ng.bluemix.net/oidc/token", 
					  "SERVICE_ENDPOINT": "https://s3.us-west.objectstorage.uat.softlayer.net"}

prod_credentials = {"API_KEY": "WaYAezQghvoyH51M6cZCrCIks43w4L4up4OQQFKjHShM", 
			"RESOURCE_ID": "crn:v1:bluemix:public:cloud-object-storage:global:a/166b06133de3b115e20d6201f119da18:396f3af4-a99d-4e19-9469-a48e5b442caf::", 
			"AUTH_ENDPOINT": "https://iam.bluemix.net/oidc/token", 
			"SERVICE_ENDPOINT": "https://s3-api.us-geo.objectstorage.softlayer.net"}


parser = argparse.ArgumentParser(prog='bucketCleaner')
parser.add_argument('-env', nargs='?', default='staging', dest='envName',choices=set(('prod', 'staging')), help='environment to connect to. By default "staging" environment is about to be used.')
parser.add_argument('-bucketName', dest='bucketName', help='name of bucket to be cleaned', required=True)
parser.add_argument('-objectPattern', dest='objectPattern', help='pattern of object to be deleted', required=False, default='*')
args = parser.parse_args()


env = args.envName
targetBucketName = args.bucketName
objPattern = args.objectPattern

print ("About to clean bucket '" + str(targetBucketName) + "' in environment '"+ str(env) + "'")

credentials =  prod_credentials if env == 'prod' else staging_credentials


resource = ibm_boto3.resource('s3',
					  ibm_api_key_id=credentials["API_KEY"],
					  ibm_service_instance_id=credentials["RESOURCE_ID"],
					  ibm_auth_endpoint=credentials["AUTH_ENDPOINT"],
					  config=Config(signature_version='oauth'),
					  endpoint_url=credentials["SERVICE_ENDPOINT"])


targetBucket = None
allbuckets = resource.buckets.all()
print ("Found the following buckets:")
for b in allbuckets:   
   print ('\t' + b.name)
   if b.name == targetBucketName:
        targetBucket = b

if targetBucket is None:
   print ("Bucket '" + targetBucketName + "' not found");
   raise SystemExit

print ("About to clean up content of bucket '"	+ targetBucketName + "'")

def getTotalObjectsCount(): 
    res = 0
    for page in targetBucket.objects.pages():
        for obj in page:
            if objPattern != None:
                if fnmatch.fnmatch(obj.key, objPattern):
                    res += 1
    return res

totalObjCount = getTotalObjectsCount()
deletedObjCount = 0;
for page in targetBucket.objects.pages():
    for obj in page:
        if objPattern != None:
            print("About to delete objects matching pattern '" + objPattern  + "'")
            if fnmatch.fnmatch(obj.key, objPattern):
                print ("Deleting object '" + obj.key + "'...")
                deletedObjCount += 1
                obj.delete()
        else:		 
            print ("Deleting object '" + obj.key + "'...")
            deletedObjCount += 1
            obj.delete()
        print("Number of deleted objects is '" + str(deletedObjCount)  + "/" + str(totalObjCount) + "'")

print ("'" + str(deletedObjCount) + "' objects have been deleted from bucket '" + targetBucketName + "'")

print ("Deleting bucket '"	+ targetBucketName + "'...")
#targetBucket.delete()
