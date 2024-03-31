import java.io.IOException;
import java.util.PriorityQueue;
import java.util.StringTokenizer;

import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Partitioner;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

public class Step4 {
    public static String bucketForJars = "jars123123123/jars";

    public static class MapperClass extends Mapper<LongWritable, Text, Text, Text> {
        private Text word_key = new Text();
        private Text word_value = new Text();
        public static double rpmi;
        public static double rnpmi;

        @Override

        protected void setup(Context context) throws IOException, InterruptedException {
            Configuration conf = context.getConfiguration();
            rpmi = conf.getDouble("rpmi", 0.0);
            rnpmi = conf.getDouble("rnpmi", 0.0);
        }

        public void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            System.out.println("[DEBUG - STEP4/MAPPER] Value is: " + value);

            try {
                // Received : <Year,W2 W1,Count(W1, W2),Log(C(W1))>
                StringTokenizer itr = new StringTokenizer(value.toString());
                String[] key_split = itr.nextToken().split(","); // <decade>
                String[] value_split = itr.nextToken().split(","); // <w1 , w2 count(w1,w2), count(w1), count(w2), N>

                // 1990,bosmona cosmos,1,24
                if (key_split.length == 1 && value_split.length == 6) {

                    String decade = key_split[0];
                    String w1 = value_split[0];
                    String w2 = value_split[1];
                    int count_word1_word2 = Integer.parseInt(value_split[2]);
                    int count_word1 = Integer.parseInt(value_split[3]);
                    int count_word2 = Integer.parseInt(value_split[4]);
                    int N = Integer.parseInt(value_split[5]);

                    double pmi = Math.log(count_word1_word2) + Math.log(N) - Math.log(count_word1)
                            - Math.log(count_word2);

                    double c_w1_w2_d = count_word1_word2;
                    double N_d = N;
                    double p_w1_w2 = -1 * Math.log(c_w1_w2_d / N_d);

                    double npmi = pmi / p_w1_w2;
                    word_key.set(decade);
                    word_value.set(w1 + "," + w2 + "," + npmi);

                    if (pmi > rpmi || npmi > rnpmi)
                        // if (npmi < 0.99) {
                        context.write(word_key, word_value);
                    // }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static class ReducerClass extends Reducer<Text, Text, Text, Text> {
        int counter_w2 = 0;
        Boolean star_flag = false;
        private static final int TOP_N = 10;

        /* NPMI */
        // <decade> <w1,w2,npmi>.
        @Override
        public void reduce(Text key, Iterable<Text> values, Context context)
                throws IOException, InterruptedException {
            PriorityQueue<String> topNpmis = new PriorityQueue<>(
                    (s1, s2) -> Double.compare(Double.parseDouble(s2.split(" ")[2]),
                            Double.parseDouble(s1.split(" ")[2])));

            System.out.println("[DEBUG - STEP4/REDUCER] Entering the reduce: ");

            for (Text value : values) { // Some whole the w1 in the decade.
                String w1 = value.toString().split(",")[0];
                String w2 = value.toString().split(",")[1];
                double npmi = Double.parseDouble(value.toString().split(",")[2]);

                // Add the value to the PriorityQueue
                topNpmis.offer(w1 + " " + w2 + " " + npmi);

                // Remove the lowest value if the size exceeds TOP_N
                if (topNpmis.size() > TOP_N) {
                    topNpmis.poll();
                }
            }

            while (!topNpmis.isEmpty()) {
                String[] entryParts = topNpmis.poll().split(" ");
                System.out.println(key + "\t" + entryParts[0] + " " + entryParts[1] + " " + entryParts[2]);
                context.write(key, new Text(entryParts[0] + " " + entryParts[1] + " " + entryParts[2]));
            }

            System.out.println("Exited the reudce.");
        }
    }

    public static class PartitionerClass extends Partitioner<Text, Text> {
        @Override
        public int getPartition(Text key, Text value, int numPartitions) {
            int hashed = key.hashCode() % numPartitions;
            return Math.abs(hashed);
        }

    }

    public static void main(String[] args) throws Exception {

        System.out.println("[DEBUG] STEP 4 started!");
        System.out.println(args.length > 0 ? args[0] : "no args");
        Configuration conf = new Configuration();
        conf.setDouble("rpmi", Double.parseDouble(args[3]));
        conf.setDouble("rnpmi", Double.parseDouble(args[4]));
        conf.set("mapred.max.split.size", "33554432");

        Job job = Job.getInstance(conf, "NPMI");
        job.setJarByClass(Step4.class);
        job.setMapperClass(Step4.MapperClass.class);
        job.setPartitionerClass(Step4.PartitionerClass.class);
        job.setReducerClass(Step4.ReducerClass.class);
        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(Text.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(Text.class);

        FileInputFormat.addInputPath(job, new Path(args[1]));
        FileOutputFormat.setOutputPath(job, new Path(args[2]));

        job.waitForCompletion(true);
    }
}
