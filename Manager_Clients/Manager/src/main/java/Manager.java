import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.ConcurrentHashMap;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sqs.model.Message;

public class Manager {
    final static AWS aws = AWS.getInstance();
    public static HashMap<String, String> urls_map = new HashMap<>(); // inputUrlQ : TimeStamp
    public static ConcurrentHashMap<String, String[]> out_in_queues_hash = new ConcurrentHashMap<>();// OutputQueue :
                                                                                                     // [InputQueue,
    // num_messages]
    public static boolean output_flag_terminate = false;
    public static int output_counter = 1;
    public static int input_queue_counter = 1;
    public static int output_queue_counter = 1;

    public static String managerToWorkers = aws.createQueue("ManagerToWorkers");
    public static String appsToManager = aws.getQueue("AppsToManager");
    public static String managerToApps = aws.getQueue("ManagerToApps");

    public static void main(String[] args) {

        OutputManager outputThread = new OutputManager();
        outputThread.start(); // Start the thread
        System.out.println("[DEBUG] Starting output monitor thread successfully.");

        while (true) {
            List<Message> msgs1 = aws.receiveMsg(appsToManager);
            if (!msgs1.isEmpty()) {
                // TOODO: Assure the msg is on this syntax : "terminate" or URL
                System.out.println(
                        "\n------------------------------------- NEW SESSION -------------------------------------\n");
                if (msgs1.get(0).body().equals("terminate")) {
                    aws.deleteMsg(msgs1.get(0), appsToManager);
                    break;
                }

                for (Message msg : msgs1) {
                    application_dealer(msg);
                }
            }
        }
        /** Code to recover from crisis. */
        while (!out_in_queues_hash.isEmpty() || aws.instancesCounter() != 0) {
            int i = 0;
            if (aws.instancesCounter() == 0 && !out_in_queues_hash.isEmpty()) {
                for (Map.Entry<String, String[]> entry : out_in_queues_hash.entrySet()) {
                    if (i < 10) {
                        String key = entry.getKey(); // output url queue.
                        String[] value = entry.getValue();
                        aws.sendMsg(managerToWorkers, value[0] + " " + key);
                        aws.createInstance("Worker");
                        i++;
                    }
                }
            }
        } // processing outputs.
        output_flag_terminate = true;
        aws.temrinate();
    }

    private static void create_instances(String inputQueue, String outputQueue, int msg_counter,
            double tasksPerWorker) {
        int numOfworkers = aws.instancesCounter();

        int temp = ((int) (Math.ceil(msg_counter / tasksPerWorker)));
        int workers_required = temp > 10 ? 10 : temp;
        if (numOfworkers != 0) {
            if (workers_required > numOfworkers)
                workers_required -= numOfworkers;
        }
        // [5,3,2,2]
        // Sending message to distribute tasks to workers, for active workers.
        for (int i = 0; i < workers_required; i++) {
            aws.sendMsg(managerToWorkers, inputQueue + " " + outputQueue);
        }
        // Add active users if you can.
        for (int i = 0; i < workers_required && numOfworkers < 10; i++) {
            aws.createInstance("Worker");
            numOfworkers++;
        }
    }

    private static void application_dealer(Message msg) {
        int msg_counter;
        String[] msg_parts = msg.body().split(" ");
        if (msg_parts.length != 3) { // Structure : TS URL TaskPerWorker
            return;
        }
        String url = msg_parts[1]; // [timestamp url]
        int tasksPerworker = Integer.parseInt(msg_parts[2]);

        String filePath = aws.downloadFile(url);
        try {
            URI uri = new URI(url);
            String bucketName = uri.getHost().split("\\.")[0]; // Extract bucket name from URL
            aws.deleteMsg(msg, appsToManager);

            String inputQueue = aws.createQueue("inputQueue" + String.valueOf(input_queue_counter++));
            String outputQueue = aws.createQueue("outputQueue" + String.valueOf(output_queue_counter++));
            msg_counter = proccssFile(filePath, inputQueue);
            addToMap(msg.body(), inputQueue);

            String[] str = { inputQueue, String.valueOf(msg_counter), bucketName };
            out_in_queues_hash.put(outputQueue, str);
            create_instances(inputQueue, outputQueue, msg_counter, tasksPerworker); //
            System.out.println("[DEBUG] Successfully sent " + msg_counter + " messages.");
        } catch (URISyntaxException | SdkException e) {
            e.printStackTrace();

        }

    }

    public static int proccssFile(String filePath, String queue) {
        System.out.println("[DEBUG] Sending the messages to SQS. \n ...");

        BufferedReader reader = null;
        int counter = 0;
        try {
            reader = new BufferedReader(new FileReader(filePath));
            String line;
            ObjectMapper objectMapper = new ObjectMapper();

            while ((line = reader.readLine()) != null) {
                JsonNode jsonNode = objectMapper.readTree(line);
                JsonNode reviewsNode = jsonNode.get("reviews");
                if (reviewsNode != null && reviewsNode.isArray()) {
                    for (JsonNode review : reviewsNode) {
                        aws.sendMsg(queue, review.toString());
                        counter++;
                    }
                }
            }
            return counter;
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return counter;
    }

    private static void addToMap(String msg, String url_queue) {
        String[] identifiers = msg.split(" ");
        urls_map.put(url_queue, identifiers[0]); // [inputurl timestamp]
    }

}
