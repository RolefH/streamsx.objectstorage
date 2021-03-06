import argparse
import sys
import ibm_boto3
import threading
from ibm_botocore.client import Config


staging_credentials = {"API_KEY": "aSqncfwx1_gbocrI-FgkkpjvfPSqXAaXhn4mczQ2Kazj", 
                      "RESOURCE_ID": "5815451d-1aca-4cae-b89c-1285ce2715a2", 
                      "AUTH_ENDPOINT": "https://iam.stage1.ng.bluemix.net/oidc/token", 
                      "SERVICE_ENDPOINT": "https://s3.us-west.objectstorage.uat.softlayer.net"}

prod_credentials = {"API_KEY": "WaYAezQghvoyH51M6cZCrCIks43w4L4up4OQQFKjHShM", 
		    "RESOURCE_ID": "crn:v1:bluemix:public:cloud-object-storage:global:a/166b06133de3b115e20d6201f119da18:396f3af4-a99d-4e19-9469-a48e5b442caf::", 
		    "AUTH_ENDPOINT": "https://iam.bluemix.net/oidc/token", 
		    "SERVICE_ENDPOINT": "https://s3-api.us-geo.objectstorage.softlayer.net"}


parser = argparse.ArgumentParser(prog='bucketCleaner')
parser.add_argument('-env', nargs='?', default='staging', dest='envName',choices=set(('prod', 'staging')), help='environment to connect to. By default "staging" environment is about to be used.')
parser.add_argument('-bucketName', dest='bucketName', help='name of bucket to be cleaned', required=True)
args = parser.parse_args()


env = args.envName
targetBucketName = args.bucketName

print ("About to clean bucket '" + str(targetBucketName) + "' in environment '"+ str(env) + "'")

credentials =  prod_credentials if env == 'prod' else staging_credentials

def pageSplitter(pages, sliceSize):
    for i in range(0, len(pages), sliceSize):
        yield l[i:i + sliceSize]

resource = ibm_boto3.resource('s3',
                      ibm_api_key_id=credentials["API_KEY"],
                      ibm_service_instance_id=credentials["RESOURCE_ID"],
                      ibm_auth_endpoint=credentials["AUTH_ENDPOINT"],
                      config=Config(signature_version='oauth'),
                      endpoint_url=credentials["SERVICE_ENDPOINT"])

class DeleteThread (threading.Thread):
   def __init__(self, name, pagesToDelete):
      threading.Thread.__init__(self)
      self.name = name
      self.pages = pagesToDelete      
      self.deleteCount = 0
   def run(self):
      for page in pages:
         for obj in page:
            print ("Deleting object '" + obj.key + "'...")
            deleteCount += 1
            obj.delete()
      print ("'" + str(deleteCount) + "' objects have been deleted from bucket '" + targetBucketName + "' by thread '" + name + "'")

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

print ("About to clean up content of bucket '"  + targetBucketName + "'")

deletedObjCount = 0;
pages = targetBucket.objects.pages()
numOfDeleteThreads = 5
deleteThreadInput = pageSplitter(pages, numOfDeleteThreads)

for i in range(0, numOfDeleteThreads):
  deleteThread = DeleteThread("DeleteThread" + str(i), deleteThreadInput[i])
  deleteThread.start()


print ("Deleting bucket '"  + targetBucketName + "'...")
targetBucket.delete()
