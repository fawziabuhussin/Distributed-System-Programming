import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Base64;
import java.util.List;

import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
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
import software.amazon.awssdk.services.ec2.model.IamInstanceProfileSpecification;
import software.amazon.awssdk.services.ec2.model.RunInstancesRequest;

public class AWS {
    private final S3Client s3;
    private final SqsClient sqs;
    private final Ec2Client ec2;

    public static Region region1 = Region.US_WEST_2;
    public static Region region2 = Region.US_EAST_1;

    public static String amiId = "ami-00e95a9222311e8ed";
    public String bucketName = "bucket-";
    public String managerJarKey = "Manager.jar";
    public String bucketForJars = "bucket-jars24";
    public String awsProfileName = "default";
    public String managerName = "Manager";


    private static final AWS instance = new AWS();

    private AWS() {
        s3 = S3Client.builder().region(region1).build();
        sqs = SqsClient.builder().region(region2).build();
        ec2 = Ec2Client.builder().region(region2).build();
    }

    public static AWS getInstance() {
        return instance;
    }



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
            System.out.println("[DEBUG] You have created a bucket: " + bucketName + ".");

        } catch (S3Exception e) {
            System.out.println("[DEBUG] You have the bucket " + bucketName + " already.");
        }
    }

    public void createInstance(String name) {
        uploadFile("/home/users/bsc/fawziabu/Desktop/DSP/AWS/Manager/target/Manager-1.0.jar", "Manager.jar", bucketForJars);
        uploadFile("/home/users/bsc/fawziabu/Desktop/DSP/AWS/Worker/target/Worker-1.0.jar", "Worker.jar", bucketForJars);

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

        AwsCredentialsProvider credentialsProvider = ProfileCredentialsProvider.builder()
                .profileName(awsProfileName)
                .build();

        AwsCredentials awsCredentials = credentialsProvider.resolveCredentials();

        String accessKey = awsCredentials.accessKeyId();
        String secretKey = awsCredentials.secretAccessKey();

        return "#!/bin/bash\n"
                + "aws configure set aws_secret_key_id " + accessKey + "\n"
                + "aws configure set aws_secret_access_key " + secretKey + "\n"
                + "echo Manager jar running\n" +
                "echo s3://" + bucketForJars + "/" + managerJarKey + "\n" +
                "mkdir ManagerFiles\n" +
                "aws s3 cp s3://" + bucketForJars + "/" + managerJarKey + " ./ManagerFiles/" + managerJarKey + "\n" +
                "echo manager copy the jar from s3\n" +
                "java -jar /ManagerFiles/" + managerJarKey + "\n";
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
                    "Successfully started EC2 instance %s based on AMI %s",
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
                                    .values(managerName)
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

    public java.net.URI uploadFile(String path, String key, String bucket_name) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket_name)
                .key(key)
                .build();

        // Upload the file to S3
        s3.putObject(putObjectRequest,
                RequestBody.fromFile(new File(path)));

        URL url = s3.utilities().getUrl(GetUrlRequest.builder()
                .bucket(bucket_name)
                .key(key)
                .build());

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

    public String createQueue(String queueName) {
        try {
            // Check if the queue already exists
            GetQueueUrlRequest getQueueUrlRequest = GetQueueUrlRequest.builder()
                    .queueName(queueName)
                    
                    .build();

            GetQueueUrlResponse getQueueUrlResponse = sqs.getQueueUrl(getQueueUrlRequest);
            System.out.println("[DEBUG] You have this queue already.");
            return getQueueUrlResponse.queueUrl();
        }
        // If the queue exists, return its URL
        catch (QueueDoesNotExistException e) {

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
                .visibilityTimeout(5)
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


    
    public String downloadFile(String s3Url, String localFilePath) {
        System.out.println("[DEBUG] Downloading the output file: " + localFilePath);

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

    public void deleteBucketAndContents() {
        // Delete objects in the bucket
        ListObjectsRequest listObjectsRequest = ListObjectsRequest.builder().bucket(bucketName).build();
        ListObjectsResponse listObjectsResponse = s3.listObjects(listObjectsRequest);
        for (S3Object s3Object : listObjectsResponse.contents()) {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucketName).key(s3Object.key()).build());
        }

        // Delete the bucket
        s3.deleteBucket(DeleteBucketRequest.builder().bucket(bucketName).build());

        System.out.println("[DEBUG] Bucket " + bucketName + " and its contents deleted successfully.");
    }
}
