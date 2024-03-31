import java.io.IOException;
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

public class Step3 {
    public static String bucketForJars = "jars123123123/jars";

    public static class MapperClass extends Mapper<LongWritable, Text, Text, Text> {
        private Text word_key = new Text();
        private Text word_value = new Text();

        @Override
        public void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {

            try {
                // Received : <Year,W2    W1,Count(W1, W2),Log(C(W1))>
                StringTokenizer itr = new StringTokenizer(value.toString());
                String[] key_split = itr.nextToken().split(","); // <decade,w1,w2 count>
                String[] value_split = itr.nextToken().split(","); // <count>
                    
                // 1990,bosmona	cosmos,1,24
                if (key_split.length == 2 && value_split.length == 4) {

                    String decade = key_split[0];
                    String w2 = key_split[1];
                    String w1 = value_split[0]; 
                    String count_word1_word2 = value_split[1]; 
                    String count_word1 = value_split[2];
                    String N = value_split[3]; 

                    word_key.set(decade + "," + w2);
                    word_value.set(count_word1_word2 + "," + count_word1 + "," + N);
                    

                    context.write(new Text(word_key + "," + w1) , word_value);
                    context.write(new Text(word_key + ",*")     , word_value);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static class ReducerClass extends Reducer<Text, Text, Text, Text> {
        int counter_w2 = 0;
        Boolean star_flag = false;
        @Override
        /* C(W2) */
        public void reduce(Text key, Iterable<Text> values, Context context) throws IOException, InterruptedException {
            // (<year,w1>, <w2,count>)
            System.out.println("[DEBUG - STEP3/REDUCER] Entering the reduce: ");
            String[] keySplit = key.toString().split(",");
            String decade = keySplit[0];
            String w2 = keySplit[1];
            String word2_star = keySplit[2];

            for (Text value : values) { // Some whole the w1 in the decade.
                
                String counter_string = value.toString().split(",")[0];
                String counter_w1 = value.toString().split(",")[1];
                String N = value.toString().split(",")[2];

                int count_w1_w2 = Integer.parseInt(counter_string);

                if (word2_star.equals("*") && star_flag) {
                    counter_w2 = counter_w2 + count_w1_w2;

                } else if (word2_star.equals("*") && !star_flag ){ // new reducer.
                    counter_w2 = count_w1_w2;
                    star_flag = !star_flag;
                } else {
                    if (star_flag)
                        star_flag = !star_flag;

                    // <decade>     <w1, w2, c(w1,w2),c(w1),c(w2), N>.
                    context.write(new Text(decade), new Text(word2_star + "," + w2 + "," + 
                    count_w1_w2 + "," + counter_w1 + "," + counter_w2 + "," + N));
                }

            }
            System.out.println("Exited the reudce.");
        }
    }


    public static class PartitionerClass extends Partitioner<Text, Text> {
        @Override
        public int getPartition(Text key, Text value, int numPartitions) {
            String[] keySplit = key.toString().split(",");
                    String year = keySplit[0];
                    String w2 = keySplit[1];
            Text key_text = new Text(year + "," + w2);
            int hashed = key_text.hashCode() % numPartitions;
            return Math.abs(hashed);
        }
    }

    public static void main(String[] args) throws Exception {

        System.out.println("[DEBUG] STEP 3 started!");
        System.out.println(args.length > 0 ? args[0] : "no args");
        Configuration conf = new Configuration();
        conf.set("mapred.max.split.size", "33554432");

        Job job = Job.getInstance(conf, "C(W2)");
        job.setJarByClass(Step3.class);
        job.setMapperClass(Step3.MapperClass.class);
        job.setPartitionerClass(Step3.PartitionerClass.class);
        job.setReducerClass(Step3.ReducerClass.class);
        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(Text.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(Text.class);

        FileInputFormat.addInputPath(job, new Path(args[1]));
        FileOutputFormat.setOutputPath(job, new Path(args[2]));

        job.waitForCompletion(true);
    }
}
