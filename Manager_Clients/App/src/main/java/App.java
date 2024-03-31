import java.net.URI;
import java.util.HashMap;
import java.util.List;

import software.amazon.awssdk.services.sqs.model.Message;

public class App {

    static int id = 0;
    final static AWS aws = AWS.getInstance();
    private static String appsToManager;
    public static URI uri;
    private static String managerToApps;
    public static HashMap<String, String> in_out = new HashMap<>();
    static int tasksPerWorker;
    static String path_jar = "/home/spl211/Desktop/AWS/App/src/main/resources/input1.txt";
    public static int input_counter = 0;
    public static int output_counter = 0;
    public static boolean terminateMode;
    public static String timmestamp;

    public static void main(String[] args) { // args = [inFilePath, outFilePath, tasksPerWorker, -t,
                                             // (terminate,optional)]

        if (parseArguments(args) == -1)
            return;

        timmestamp = "" + System.currentTimeMillis();
        try {
            System.out.println("\n-------------------------------------- SETTING UP --------------------------------------\n");
            setup();

            while (true) {
                List<Message> msgs = aws.receiveMsg(managerToApps); 
                if (!msgs.isEmpty()) {
                    for (Message msg : msgs) {
                        take_response(msgs, msg);
                    }
                }

                if (input_counter == output_counter)
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        end();
    }

    /** Takes response from the queue of the manager. */
    private static void take_response(List<Message> msgs, Message msg) {
        String identifiers[] = msg.body().split(" ");
        int last_digit = Integer
                .parseInt(String.valueOf(identifiers[0].charAt(identifiers[0].length() - 1)));

        String equString = identifiers[0].substring(0, identifiers[0].length() - 1);        
        if (timmestamp.equals(equString)) { 
            String inputurl = (String) in_out.keySet().toArray()[last_digit];
        String outputurl = in_out.get(inputurl);
            System.out.println("[DEBUG] Recived message successfully :" + msgs.get(0).body());
            aws.downloadFile(identifiers[1], outputurl);
            aws.deleteMsg(msgs.get(0), managerToApps);
            output_counter++;
            System.out.println(
                    "\n-------------------------------------- OUTPUT GENERATED --------------------------------------");
        }
    }

    /** Ends the connection with the manager */
    private static void end() {
        aws.deleteBucketAndContents();
        // if(terminateMode)
        //     aws.removeQueueByName("ManagerToApps");
        System.out.println("[DEBUG] Bye..");
    }

    /** Create Buckets, Create Queues, Upload JARs to S3 */
    private static void setup() {
        System.out.println("[DEBUG] Create bucket if not exist.");
        aws.bucketName += (new StringBuilder(System.currentTimeMillis() + "").reverse().toString());
        aws.createBucketIfNotExists(aws.bucketName);
        aws.createBucketIfNotExists(aws.bucketForJars);

        

        appsToManager = aws.createQueue("AppsToManager");
        managerToApps = aws.createQueue("ManagerToApps");
        

        for (String key : in_out.keySet()) {
            String cnt = String.valueOf(input_counter);
            uri = aws.uploadFile(key, "input" + cnt, aws.bucketName);
            aws.sendMsg(appsToManager, timmestamp + cnt + " " + uri.toString() + " " + tasksPerWorker); // "12312312  URL TaskperWorker. 
            input_counter++;
        }

        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            // Handle the exception if necessary
            e.printStackTrace();
        }

        if(terminateMode){
            System.out.println("[DEBUG] Termination message has been sent successfully.");
            aws.sendMsg(appsToManager, "terminate"); 
        }

        if (!aws.isManagerActive()) {
            aws.createInstance("Manager");
        System.out.println("[DEBUG] You have created the Manager.. Now we start.");
        } else
            System.out.println("[DEBUG] You have the manager already, keep working!");
        System.out.println(
                "\n-------------------------------------- WAITING FOR RESPONSE --------------------------------------\n");

    }

    /** Takes the args, starts the required fields. */
    public static int parseArguments(String[] args) {

        if (args[args.length - 1].equals("-t")) {
            terminateMode = true;
        }

        if (terminateMode) {
            if (args.length < 4) {
                System.err.println("Invalid number of arguments.");
                return -1;
            }
            try {
                tasksPerWorker = Integer.parseInt(args[args.length - 2]); // Parse tasksPerWorker
                for (int i = 0; i < (args.length - 2) / 2; i++) {
                    in_out.put(args[i], (args[i + (args.length - 2) / 2]));
                }
            } catch (NumberFormatException e) {
                System.err.println("Invalid tasks per worker value.");
                return -1;
            }
        } else { // If terminateMode is false
            // Check if there are enough arguments
            if (args.length < 3) {
                System.err.println("Invalid number of arguments.");
                return -1;
            }
            try {
                tasksPerWorker = Integer.parseInt(args[args.length - 1]); // Parse tasksPerWorker
                for (int i = 0; i < (args.length - 1) / 2; i++) {
                    in_out.put(args[i], (args[i + (args.length - 1) / 2]));
                }

            } catch (NumberFormatException e) {
                System.err.println("Invalid tasks per worker value.");
                return -1;
            }
        }
        return tasksPerWorker; // > -1
    }

}
