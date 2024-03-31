import java.io.IOException;
import java.net.URI;

import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Partitioner;
import org.apache.hadoop.mapreduce.Reducer;
import java.util.StringTokenizer;

import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

public class Step2 {

    public static String bucketForJars = "jars123123123/jars";
    public static Configuration confg2 = new Configuration();

    // <Year, W1, W2> , Count.
    public static class MapperClass extends Mapper<LongWritable, Text, Text, Text> {
        public static double rpmi;
        private Text word = new Text();
        private Text val = new Text();
        private long N;
        public void setup(Context context)  throws IOException, InterruptedException {
            String s3Region = "us-east-1";
            String s3BucketName = "jars123123123";

            confg2.set("fs.s3a.impl", "org.apache.hadoop.fs.s3a.S3AFileSystem");
            confg2.set("fs.s3a.endpoint", "s3." + s3Region + ".amazonaws.com");

            try {
                FileSystem fs = FileSystem.get(new URI("s3a://" + s3BucketName), confg2);
                Path counterFile = new Path("s3a://" + s3BucketName + "/counter.txt");
                try (FSDataInputStream in = fs.open(counterFile)) {
                    String counterValueString = in.readUTF();
                    N = Long.parseLong(counterValueString);
                    System.out.println("Counter value from file: " + N);
                }
            } catch (Exception e) {
                System.out.println("CATCHED ERROR");
            }
        }
        @Override
        public void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {

            try {
                // // Split the input value based on comma to extract the components
                StringTokenizer itr = new StringTokenizer(value.toString());
                // System.out.println("[DEBUG - STEP2/MAPPER] Value is: "+ value);
                String[] key_split = itr.nextToken().split(","); // <decade,w1,w2 count>
                String count_split = itr.nextToken(); // <count>

                if (key_split.length == 3) { // Ensure that all components are present
                    String year = key_split[0];
                    String w1 = key_split[1];
                    String w2 = key_split[2];
                    int count = Integer.parseInt(count_split);
                    word.set(year + "," + w1);
                    val.set(count + "," + N);
                    System.out.println("[DEBUG - STEP2/MAPPER] Sending: " + "<" + word + "> " + " <" + val + ">");
                    context.write(new Text(word.toString() + ",*"), val);
                    context.write(new Text(word.toString() + "," + w2), val);
                }
            } catch (Exception e) {
                System.out.println("[DEBUG - STEP2/MAPPER] CATCHED THE ERROR HERE OF THE TOKENIZER.");
                e.printStackTrace();
            }
        }
    }

    /* For whole the mapper, it will accumlate the results with this single key. */
    public static class ReducerClass extends Reducer<Text, Text, Text, Text> {
        int total = 0;
        Boolean star_flag = false;
        @Override

        /** C(W1) : (<year,w1, *>, <count(w1,w2), N>) || (<year,w1, w2>, <count(w1,w2), N>)*/
        public void reduce(Text key, Iterable<Text> values, Context context) throws IOException, InterruptedException {
            String[] keySplit = key.toString().split(",");
            String year = keySplit[0];
            String w1 = keySplit[1];
            String word2_star = keySplit[2];

            for (Text value : values) { // Some whole the w1 in the decade.


                int count_w1_w2 = Integer.parseInt(value.toString().split(",")[0]);
                int N = Integer.parseInt(value.toString().split(",")[1]);

                if (word2_star.equals("*") && star_flag) {
                    total = total + count_w1_w2;
                } else if (word2_star.equals("*") && !star_flag ){ // new reducer.
                    total = count_w1_w2;
                    star_flag = !star_flag;
                } else {
                    if (star_flag)
                        star_flag = !star_flag;
                    // Send for each <w1,w2> and append the c(w1).
                    System.out.println("[DEBUG - STEP2/REDUCER] Sending this line " + year + "," + word2_star + "\t" + w1 + "," + count_w1_w2 + "," + total);
                    context.write(new Text(year + "," + word2_star), new Text(w1 + "," + count_w1_w2 + "," + total + "," + N));
                }
            }
        }
    }

    public static class PartitionerClass extends Partitioner<Text, Text> {
        @Override
        public int getPartition(Text key, Text value, int numPartitions) {
            String[] keySplit = key.toString().split(",");
                    String year = keySplit[0];
                    String w1 = keySplit[1];
            Text key_text = new Text(year + "," + w1);
            int hashed = key_text.hashCode() % numPartitions;
            return Math.abs(hashed);
        }
    }

    public static void main(String[] args) throws Exception {

        System.out.println("[DEBUG] STEP 2 started!");
        System.out.println(args.length > 0 ? args[0] : "no args");
        confg2.set("mapred.max.split.size", "33554432");

        Job job = Job.getInstance(confg2, "C(W1)");
        job.setJarByClass(Step2.class);
        job.setMapperClass(Step2.MapperClass.class);
        job.setPartitionerClass(Step2.PartitionerClass.class);
        job.setReducerClass(Step2.ReducerClass.class);
        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(Text.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(Text.class);

        FileInputFormat.addInputPath(job, new Path(args[1]));
        FileOutputFormat.setOutputPath(job, new Path(args[2]));

        job.waitForCompletion(true);
    }
}
