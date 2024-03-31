import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.List;

import software.amazon.awssdk.services.sqs.model.Message;


public class OutputManager extends Thread{
    public void run() {
        while (!Manager.output_flag_terminate) {
            process_output();
            try {
                Thread.sleep(1000); 
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }


    public static void process_output() {
        for (Map.Entry<String, String[]> entry : Manager.out_in_queues_hash.entrySet()) {
            String key = entry.getKey(); // output url queue.
            String[] value = entry.getValue();
            send_output_file(key, value);
        }
    }

    private static void send_output_file(String output_url, String[] value) {
        if (Manager.aws.msgCount(output_url) == Integer.parseInt(value[1])) {

            System.out.println("[DEBUG] Creating output file \n ...");
            outputFile(output_url);

            String filePath = System.getProperty("user.dir") + "/output" + String.valueOf(Manager.output_counter) + ".html";
            URI uri = Manager.aws.uploadFile(filePath, "output" + String.valueOf(Manager.output_counter++), value[2]);

            String timestamp = Manager.urls_map.get(value[0]);
            Manager.aws.sendMsg(Manager.managerToApps, timestamp + " " + uri.toString()); // Send the url to the App.
            Manager.aws.removeQueue(output_url);
            Manager.aws.removeQueue(value[0]);
            Manager.urls_map.remove(value[0]);
            Manager.out_in_queues_hash.remove(output_url);
            System.out.println("[DEBUG] Message to local App has been sent.");
        }
    }


    /** Methods to create HTML output file. */
    public static void outputFile(String queueUrl) {
        String filePath = "output" + String.valueOf(Manager.output_counter) + ".html";
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            writer.write(
                    "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><title>My HTML Page</title></head><body><h1>OUTPUT FILE OF REVIEWS:</h1>");

            List<Message> messages = Manager.aws.receiveMsg(queueUrl);
            while (!messages.isEmpty()) {
                processMessages(messages, writer, queueUrl);
                messages = Manager.aws.receiveMsg(queueUrl);
            }
            writer.write("</body></html>");
            System.out.println("[DEBUG] HTML file created successfully.");
        } catch (IOException e) {
            System.err.println("[DEBUG] Error creating HTML file: " + e.getMessage());
        }
    }

    private static void processMessages(List<Message> messages, BufferedWriter writer, String queueUrl)
            throws IOException {
        for (Message msg : messages) {
            String[] lines = msg.body().split("\n");
            String link = getValue(lines[0]);
            int sentiment = Integer.parseInt(getValue(lines[1]));
            String namedEntities = getValue(lines[2]);
            String sarcasm = getValue(lines[3]);
            Manager.aws.deleteMsg(msg, queueUrl);
            String color = getColor(sentiment);
            writer.write("<h2>Review: " + sarcasm + "</h2>");
            writer.newLine();
            writer.write("<p>&emsp;" + "<a style=\"color: " + color + "\" href=\"" + link + "\">" + link + "</a></p>");
            writer.newLine();
            writer.write("<p>&emsp;<strong>Entities:</strong>" + namedEntities + "</p>");
            writer.newLine();
        }
    }

    private static String getValue(String line) {
        return line.split(": ")[1];
    }

    private static String getColor(int sentiment) {
        switch (sentiment) {
            case 1:
                return "darkred";
            case 2:
                return "red";
            case 3:
                return "black";
            case 4:
                return "lightgreen";
            case 5:
                return "darkgreen";
            default:
                return "black";
        }
    }
}
