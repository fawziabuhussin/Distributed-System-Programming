
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkException;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.Tag;
import software.amazon.awssdk.services.ec2.model.*;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesResponse;

public class AWS {
    private final S3Client s3;
    private final SqsClient sqs;
    private final Ec2Client ec2;

    public static Region region1 = Region.US_WEST_2;
    public static Region region2 = Region.US_EAST_1;

    public static String amiId = "ami-00e95a9222311e8ed";

    private static final AWS instance = new AWS();

    public String workerJarKey = "Worker.jar";
    public String bucketForJars = "bucket-jars24";

    private AWS() {
        s3 = S3Client.builder().region(region1).build();
        sqs = SqsClient.builder().region(region2).build();
        ec2 = Ec2Client.builder().region(region2).build();
    }

    public static AWS getInstance() {
        return instance;
    }

    public String bucketName = "bucket-free-11";
    public String awsProfileName = "default";

    // S3
    public void createBucketIfNotExists(String bucketName) {
        try {
            s3.createBucket(CreateBucketRequest
                    .builder()
                    .bucket(bucketName)
                    .createBucketConfiguration(
                            CreateBucketConfiguration.builder()
                                    .locationConstraint(BucketLocationConstraint.US_WEST_2)
                                    .build())
                    .build());
            s3.waiter().waitUntilBucketExists(HeadBucketRequest.builder()
                    .bucket(bucketName)
                    .build());
            System.out.println("[DEBUG] You have created a bucket.");

        } catch (S3Exception e) {
            System.out.println("[DEBUG] You have the bucket already.");
        }
    }

    public void createInstance(String name) {
        String userDataScript = getData();

        RunInstancesRequest runRequest = RunInstancesRequest.builder()
                .instanceType(InstanceType.M4_LARGE)
                .imageId(amiId)
                .maxCount(1)
                .minCount(1)
                .keyName("vockey")
                .iamInstanceProfile(IamInstanceProfileSpecification.builder().name("LabInstanceProfile").build())
                .userData(Base64.getEncoder().encodeToString(userDataScript.getBytes()))
                .build();

        RunInstancesResponse response = ec2.runInstances(runRequest);
        this.createTag(response, name);
    }

    private String getData() {

        return "#!/bin/bash\n"
                + "echo Worker jar running\n" +
                "echo s3://" + bucketForJars + "/" + workerJarKey + "\n" +
                "mkdir WorkerFiles\n" +
                "aws s3 cp s3://" + bucketForJars + "/" + workerJarKey + " ./WorkerFiles/" + workerJarKey + "\n" +
                "echo worker copy the jar from s3\n" +
                "java -jar /WorkerFiles/" + workerJarKey + "\n";
    }

    public void createTag(RunInstancesResponse rsp, String value) {
        String instanceId = rsp.instances().get(0).instanceId();

        Tag tag = Tag.builder()
                .key("Name")
                .value(value)
                .build();

        CreateTagsRequest tagRequest = CreateTagsRequest.builder()
                .resources(instanceId)
                .tags(tag)
                .build();

        try {
            ec2.createTags(tagRequest);
            System.out.printf(
                    "Successfully started EC2 instance %s based on AMI %s ",
                    instanceId, amiId);

        } catch (Ec2Exception e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }

    public boolean isManagerActive() {
        try {
            // You can filter instances based on different criteria, here using the tag
            // "Name"
            DescribeInstancesResponse response = ec2.describeInstances(
                    DescribeInstancesRequest.builder()
                            .filters(Filter.builder()
                                    .name("tag:Name")
                                    .values("Manager")
                                    .build())
                            .build());

            // Check if any reservations (groups of instances) are returned
            for (Reservation reservation : response.reservations()) {
                for (Instance instance : reservation.instances()) {
                    // Get and print the tags of the instance
                    for (Tag tag : instance.tags()) {
                        if (tag.value().equals("Manager") &&
                                (instance.state().name().equals(InstanceStateName.RUNNING) ||
                                        instance.state().name().equals(InstanceStateName.PENDING))) {
                            // The Manager is active (either running or pending)
                            return true;
                        }
                    }
                }
            }
        } catch (Ec2Exception e) {
            System.err.println("Error checking instance tags: " + e.getMessage());
            e.printStackTrace();
        }
        // Either there is no Manager or it is not active (not running or pending)
        return false;
    }

    public java.net.URI uploadFile(String path, String key, String bucketName) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        // Upload the file to S3
        s3.putObject(putObjectRequest,
                RequestBody.fromFile(new File(path)));

        URL url = s3.utilities().getUrl(GetUrlRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build());

        System.out.println("[DEBUG] Uploaded " + key + " the file successfully.");
        // Convert URL to URI
        try {
            return url.toURI();
        } catch (Exception e) {
            // Handle the URI conversion exception (e.g., MalformedURLException or
            // URISyntaxException)
            e.printStackTrace();
            return null; // Or throw an exception or handle accordingly based on your application's
                         // requirements
        }
    }

    public String downloadFile(String s3Url) {
        String localFilePath = System.getProperty("user.dir");

        try {
            URI uri = new URI(s3Url);
            String bucketName = uri.getHost().split("\\.")[0]; // Extract bucket name from URL
            String key = uri.getPath().substring(1); // Extract object key from URL (remove leading '/')
            // Create a GetObjectRequest
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            // Send the GetObjectRequest to Amazon S3
            ResponseBytes<GetObjectResponse> objectBytes = s3.getObjectAsBytes(getObjectRequest);

            localFilePath += key + ".txt";
            // Write the objectBytes to a file
            FileOutputStream fos = new FileOutputStream(localFilePath);
            fos.write(objectBytes.asByteArray());
            fos.close();

            System.out.println("[DEBUG] File downloaded successfully!");
        } catch (URISyntaxException | SdkException | IOException e) {
            e.printStackTrace();
        }
        return localFilePath;
    }

    public String getQueue(String queueName) {
        try {
            // Check if the queue already exists
            GetQueueUrlRequest getQueueUrlRequest = GetQueueUrlRequest.builder()
                    .queueName(queueName)
                    .build();

            GetQueueUrlResponse getQueueUrlResponse = sqs.getQueueUrl(getQueueUrlRequest);
            System.out.println("[DEBUG] You have the queue, enjoy!");
            return getQueueUrlResponse.queueUrl();
        } catch (QueueDoesNotExistException e) {
            return null;
        }

    }

    public String createQueue(String queueName) {
        CreateQueueRequest request = CreateQueueRequest.builder()
                .queueName(queueName)
                .build();
        sqs.createQueue(request);
        GetQueueUrlRequest getQueueRequest = GetQueueUrlRequest.builder()
                .queueName(queueName)
                .build();

        String queueUrl = sqs.getQueueUrl(getQueueRequest).queueUrl();
        System.out.println("[DEBUG] Created a new queue with url: " + queueUrl);
        return queueUrl;
    }

    public void sendMsg(String queueUrl, String msg) {

        SendMessageRequest send_msg_request = SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(msg)
                .delaySeconds(5)
                .build();
        sqs.sendMessage(send_msg_request);

    }

    public List<Message> receiveMsg(String queueUrl) {
        // receive messages from the queue
        ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .build();
        List<Message> messages = sqs.receiveMessage(receiveRequest).messages();
        return messages;
    }

    public void deleteMsg(Message m, String queueUrl) {
        DeleteMessageRequest deleteRequest = DeleteMessageRequest.builder()
                .queueUrl(queueUrl)
                .receiptHandle(m.receiptHandle())
                .build();
        sqs.deleteMessage(deleteRequest);
    }

    public int instancesCounter() {
        String tagKey = "Name";
        String tagValue = "Worker";

        // Create a filter for instances with the specified tag
        Filter filter = Filter.builder()
                .name("tag:" + tagKey)
                .values(tagValue)
                .build();

        try {
            // Describe instances with the specified filter
            DescribeInstancesRequest request = DescribeInstancesRequest.builder()
                    .filters(filter)
                    .build();

            DescribeInstancesResponse response = ec2.describeInstances(request);

            // Count the instances
            int instanceCount = 0;
            for (Reservation reservation : response.reservations()) {
                for (Instance instance : reservation.instances()) {
                    for (Tag tag : instance.tags()) {
                        if (tag.value().equals("Worker") &&
                                (instance.state().name().equals(InstanceStateName.RUNNING) ||
                                        instance.state().name().equals(InstanceStateName.PENDING))) {
                            instanceCount++;
                        }
                    }
                }
            }

            return instanceCount;
        } finally {

        }
    }

    public int msgCount(String queueUrl) {
        try {
            // Create a request to get queue attributes
            GetQueueAttributesRequest request = GetQueueAttributesRequest.builder()
                    .queueUrl(queueUrl)
                    .attributeNames(Collections.singleton(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES))
                    .build();

            // Get the queue attributes response
            GetQueueAttributesResponse response = sqs.getQueueAttributes(request);

            // Retrieve the approximate number of messages from the response
            String numberOfMessages = response.attributes().get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES);
            // Convert the string to an integer and return
            return Integer.parseInt(numberOfMessages);
        } catch (SqsException e) {
            // Handle SQS specific exceptions
            System.err.println("SQS Exception: " + e.getMessage());
            return -1; // Or handle the exception according to your application's requirements
        } catch (Exception e) {
            // Handle other exceptions
            System.err.println("Exception: " + e.getMessage());
            return -1; // Or handle the exception according to your application's requirements
        }
    }

    public void removeQueue(String queueUrl) {
        // Create SQS client

        // Prepare request to delete the queue
        DeleteQueueRequest deleteQueueRequest = DeleteQueueRequest.builder()
                .queueUrl(queueUrl)
                .build();

        try {
            // Send request to delete the queue
            DeleteQueueResponse deleteQueueResponse = sqs.deleteQueue(deleteQueueRequest);
            System.out.println("[DEBUG] Queue successfully deleted: " + deleteQueueResponse.toString());
        } catch (SqsException e) {
            System.err.println("[DEBUG] Error deleting queue: " + e.getMessage());
        }
    }

    public void removeQueueByName(String queueName) {
        try {
            // Get the URL of the queue by name
            GetQueueUrlRequest getQueueUrlRequest = GetQueueUrlRequest.builder()
                    .queueName(queueName)
                    .build();
            GetQueueUrlResponse getQueueUrlResponse = sqs.getQueueUrl(getQueueUrlRequest);
            String queueUrl = getQueueUrlResponse.queueUrl();

            // Prepare request to delete the queue
            DeleteQueueRequest deleteQueueRequest = DeleteQueueRequest.builder()
                    .queueUrl(queueUrl)
                    .build();

            // Send request to delete the queue
            DeleteQueueResponse deleteQueueResponse = sqs.deleteQueue(deleteQueueRequest);
            System.out.println("[DEBUG] Queue successfully deleted: " + deleteQueueResponse.toString());
        } catch (QueueDoesNotExistException e) {
            System.err.println("[DEBUG] Queue with name " + queueName + " does not exist.");
        } catch (SqsException e) {
            System.err.println("[DEBUG] Error deleting queue: " + e.getMessage());
        }
    }

    private static String getInstanceId() {
        // You can use any method to retrieve the instance ID
        // For example, you can make an HTTP request to the instance metadata service
        // Here's one way to do it:
        // You can also use AWS SDK for Java to fetch metadata, but this is a simpler
        // approach
        String instanceId = "";
        try {
            java.net.URL instanceURL = new java.net.URL("http://169.254.169.254/latest/meta-data/instance-id");
            try (java.util.Scanner s = new java.util.Scanner(instanceURL.openStream())) {
                instanceId = s.useDelimiter("\\A").next();
            }
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
        return instanceId;
    }

    public void deleteBucketAndContents(String requ_bucket) {
        // Delete objects in the bucket
        ListObjectsRequest listObjectsRequest = ListObjectsRequest.builder().bucket(requ_bucket).build();
        ListObjectsResponse listObjectsResponse = s3.listObjects(listObjectsRequest);
        for (S3Object s3Object : listObjectsResponse.contents()) {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(requ_bucket).key(s3Object.key()).build());
        }

        // Delete the bucket
        s3.deleteBucket(DeleteBucketRequest.builder().bucket(requ_bucket).build());

        System.out.println("[DEBUG] Bucket " + requ_bucket + " and its contents deleted successfully.");
    }

    public void temrinate() {
        String instanceId = getInstanceId();

        removeQueueByName("AppsToManager");
        removeQueueByName("ManagerToWorkers");
        deleteBucketAndContents(bucketForJars);


        System.out.println("[DEBUG] Queues deleted successfully.");

        // Terminate the instance
        TerminateInstancesRequest request = TerminateInstancesRequest.builder()
                .instanceIds(instanceId)
                .build();
        ec2.terminateInstances(request);

        System.out.println("[DEBUG] Instance terminated successfully.");
    }


    public int countQueues() {
        try {
            // List all queues
            ListQueuesResponse response = sqs.listQueues();

            // Get the count of queues
            return response.queueUrls().size();
        } catch (Exception e) {
            System.err.println("Error occurred while counting queues: " + e.getMessage());
            return -1; // Return -1 to indicate an error
        }
    }
}
