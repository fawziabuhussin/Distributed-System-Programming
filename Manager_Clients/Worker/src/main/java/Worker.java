import java.util.List;

import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.QueueDeletedRecentlyException;
import software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException;

public class Worker {
    public static sentimentAnalysisHandler sentimentAnalysis = new sentimentAnalysisHandler();
    public static namedEntityRecognitionHandler namedEntityRecognition = new namedEntityRecognitionHandler();
    final static AWS aws = AWS.getInstance();

    public static void main(String[] args) {
        while (true) {
            String managerToWorkers = aws.getQueue("ManagerToWorkers");
            List<Message> msgs = aws.receiveMsg(managerToWorkers, 1);
            if (msgs.isEmpty())
                break;
            String inputQ = msgs.get(0).body().split(" ")[0];
            String outputQ = msgs.get(0).body().split(" ")[1];
            aws.deleteMsg(msgs.get(0), managerToWorkers);
            while (true) {
                try {
                    List<Message> msgs1 = aws.receiveMsg(inputQ, 10);
                    if (!msgs1.isEmpty()) {

                        System.out.println("[DEBUG] Length of the messages is: " + msgs1.size());

                        for (Message msg : msgs1) {
                            String send_msg = make_msg(msg);
                            aws.sendMsg(outputQ, send_msg);
                            aws.deleteMsg(msg, inputQ);
                        }
                    } else {
                        break;
                    }
                } catch (QueueDoesNotExistException | QueueDeletedRecentlyException e) {
                    break;
                }

            }
        }
        System.out.println("[DEBUG] Worker terminated successfully!");
        aws.temrinate();
    }

    public static String make_msg(Message msg) {
        String link = extractLink(msg.body());
        int returnSent = sentimentAnalysis.findSentiment(msg.body());
        List<String> returnNer = namedEntityRecognition.printEntities(msg.body());

        String send_msg = "link: " + link + "\n" + "Sent: " + returnSent + "\n" +
                "NER: [" + listToString(returnNer) + "]";

        send_msg += "\n" + "Sarcasm: " + detect_sarcasm(returnSent, msg.body());

        return send_msg;
    }

    public static String detect_sarcasm(int sentiment, String review) {
        int rating = Integer.parseInt(review.split("\"rating\":")[1].split(",\"author")[0]);
        if (rating > sentiment)
            return "Sarcasm";
        else
            return "Not Sarcasm";

    }

    public static String extractLink(String body) {
        String[] parts = body.split("\"link\":");
        return parts[1].split(",\"title\":")[0].replace("\"", "");
    }

    public static String listToString(List<String> list) {
        StringBuilder sb = new StringBuilder();
        for (String item : list) {
            sb.append(item).append(", "); // Add each item followed by a delimiter
        }
        // Remove the last delimiter if the list is not empty
        if (!list.isEmpty()) {
            sb.setLength(sb.length() - 2);
        }
        return sb.toString();
    }
}
