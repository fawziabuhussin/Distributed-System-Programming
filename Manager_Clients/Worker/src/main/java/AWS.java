import java.util.List;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

public class AWS {
    private final SqsClient sqs;
    private final Ec2Client ec2;

    public static Region region1 = Region.US_WEST_2;
    public static Region region2 = Region.US_EAST_1;

    public static String amiId = "ami-00e95a9222311e8ed";

    private static final AWS instance = new AWS();

    private AWS() {
        sqs = SqsClient.builder().region(region2).build();
        ec2 = Ec2Client.builder().region(region2).build();
    }

    public static AWS getInstance() {
        return instance;
    }

    public String bucketName = "bucket-free-11";
    public String awsProfileName = "default";

    

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

    public void sendMsg(String queueUrl, String msg) {

        SendMessageRequest send_msg_request = SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(msg)
                .delaySeconds(5)
                .build();
        sqs.sendMessage(send_msg_request);

    }

    public List<Message> receiveMsg(String queueUrl, int maxNumberOfMessages) {
        // receive messages from the queue
        ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .maxNumberOfMessages(maxNumberOfMessages)
                .visibilityTimeout(300)
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

    public void temrinate() {
        String instanceId = getInstanceId();
        // Terminate the instance
        TerminateInstancesRequest request = TerminateInstancesRequest.builder()
                .instanceIds(instanceId)
                .build();
        ec2.terminateInstances(request);

        System.out.println("[DEBUG] Instance terminated successfully.");
    }

}
