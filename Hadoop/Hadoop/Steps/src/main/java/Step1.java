import java.io.IOException;
import java.net.URI;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Partitioner;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.StringTokenizer;
// import org.apache.hadoop.mapreduce.CounterGroup;
// import org.apache.hadoop.mapreduce.Counter;
import java.util.regex.Pattern;

public class Step1 {
    public static String bucketForJars = "jars123123123/jars";

    public static class MapperClass extends Mapper<LongWritable, Text, Text, IntWritable> {

        String[] stopWords_english = new String[] { "a", "about", "above", "across", "after", "afterwards", "again",
                "against", "all", "almost", "alone", "along", "already", "also", "although", "always", "am", "among",
                "amongst",
                "amoungst", "amount", "an", "and", "another", "any", "anyhow", "anyone", "anything", "anyway",
                "anywhere", "are", "around", "as", "at", "back", "be", "became", "because", "become", "becomes",
                "becoming", "been", "before", "beforehand", "behind", "being", "below", "beside", "besides", "between",
                "beyond", "bill", "both", "bottom", "but", "by", "call", "can", "cannot", "cant", "co", "computer",
                "con", "could", "couldnt", "cry", "de", "describe", "detail", "do", "done", "down", "due", "during",
                "each", "eg", "eight", "either", "eleven", "else", "elsewhere", "empty", "enough", "etc", "even",
                "ever", "every", "everyone", "everything", "everywhere", "except", "few", "fifteen", "fify", "fill",
                "find", "fire", "first", "five", "for", "former", "formerly", "forty", "found", "four", "from", "front",
                "full", "further", "get", "give", "go", "had", "has", "hasnt", "have", "he", "hence", "her", "here",
                "hereafter", "hereby", "herein", "hereupon", "hers", "herself", "him", "himself", "his", "how",
                "however", "hundred", "i", "ie", "if", "in", "inc", "indeed", "interest", "into", "is", "it", "its",
                "itself", "keep", "last", "latter", "latterly", "least", "less", "ltd", "made", "many", "may", "me",
                "meanwhile", "might", "mill", "mine", "more", "moreover", "most", "mostly", "move", "much", "must",
                "my", "myself", "name", "namely", "neither", "never", "nevertheless", "next", "nine", "no", "nobody",
                "none", "noone", "nor", "not", "nothing", "now", "nowhere", "of", "off", "often", "on", "once", "one",
                "only", "onto", "or", "other", "others", "otherwise", "our", "ours", "ourselves", "out", "over", "own",
                "part", "per", "perhaps", "please", "put", "rather", "re", "same", "see", "seem", "seemed", "seeming",
                "seems", "serious", "several", "she", "should", "show", "side", "since", "sincere", "six", "sixty",
                "so", "some", "somehow", "someone", "something", "sometime", "sometimes", "somewhere", "still", "such",
                "system", "take", "ten", "than", "that", "the", "their", "them", "themselves", "then", "thence",
                "there", "thereafter", "thereby", "therefore", "therein", "thereupon", "these", "they", "thick", "thin",
                "third", "this", "those", "though", "three", "through", "throughout", "thru", "thus", "to", "together",
                "too", "top", "toward", "towards", "twelve", "twenty", "two", "un", "under", "until", "up", "upon",
                "us", "very", "via", "was", "we", "well", "were", "what", "whatever", "when", "whence", "whenever",
                "where", "whereafter", "whereas", "whereby", "wherein", "whereupon", "wherever", "whether", "which",
                "while", "whither", "who", "whoever", "whole", "whom", "whose", "why", "will", "with", "within",
                "without", "would", "yet", "you", "your", "yours", "yourself", "yourselves"
        };

        private Text word = new Text();
        private Random rand = new Random();
        private static Pattern ENG_PATTERN = Pattern.compile("^[a-zA-Z]+\\s[a-zA-Z]+$");
        private static Pattern YEAR_PATTERN = Pattern.compile("^\\d+$");
        private Set<String> stop_words_hash = new HashSet<String>(Arrays.asList(stopWords_english));

        @Override
        public void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            // if (rand.nextDouble() < 0.5) {
            String[] splits = value.toString().split("\t");
            if (splits.length < 4) {
                return;
            }
            String n_gram = splits[0];
            String count_s = splits[2];
            String year_s = splits[1];
            String[] word1_word2 = n_gram.split(" ");

            StringTokenizer itr = new StringTokenizer(n_gram, " ");

            // validate the input.
            if (itr.countTokens() != 2) {
                return;
            }

            if (!YEAR_PATTERN.matcher(year_s).matches() || !YEAR_PATTERN.matcher(count_s).matches())
                return;
            if (!ENG_PATTERN.matcher(n_gram).matches())
                return;
            if (stop_words_hash.contains(word1_word2[0]) || stop_words_hash.contains(word1_word2[1]))
                return;

            int count = Integer.parseInt(count_s);
            int year = Integer.parseInt(year_s);
            int decade = (year / 10)
                    * 10;
            word.set(decade + "," + word1_word2[0] + "," + word1_word2[1]);
            context.getCounter("NCounter", "N").increment(count); // inc
                                                                  // N
                                                                  // counter
            context.write(word, new IntWritable(count));
        }
    }

    /* For whole the mappers, it will accumlate the results with this single key. */
    public static class ReducerClass extends Reducer<Text, IntWritable, Text, IntWritable> {
        int total = 0;

        @Override
        public void reduce(Text key, Iterable<IntWritable> values, Context context)
                throws IOException, InterruptedException {
            int sum = 0;
            // < <decade,w1,w2>, count>
            for (IntWritable value : values) {
                sum = sum + value.get();
            }
            // System.out.println("[DEBUG - STEP1/REDUCER] Sending this line: " + key + "\t"
            // + sum);
            context.write(key, new IntWritable(sum));
        }
    }

    /* For single mapper, it will accumlate the results. */
    public static class CombinerClass extends Reducer<Text, IntWritable, Text, IntWritable> {
        @Override
        public void reduce(Text key, Iterable<IntWritable> values, Context context)
                throws IOException, InterruptedException {
            int sum = 0;
            for (IntWritable value : values) {
                sum = sum + value.get();
            }
            context.write(key, new IntWritable(sum));
        }
    }

    public static class PartitionerClass extends Partitioner<Text, IntWritable> {
        @Override
        public int getPartition(Text key, IntWritable value, int numPartitions) {
            int hashed = key.hashCode() % numPartitions;
            return Math.abs(hashed);
        }

    }

    public static void main(String[] args) throws Exception {

        System.out.println("[DEBUG] STEP 1 started!");
        System.out.println(args.length > 0 ? args[0] : "no args");
        Configuration conf = new Configuration();
        conf.set("mapred.max.split.size", "33554432");

        Job job = Job.getInstance(conf, "Remove stop Words");
        job.setJarByClass(Step1.class);
        job.setMapperClass(MapperClass.class);
        job.setPartitionerClass(PartitionerClass.class);
        job.setCombinerClass(CombinerClass.class);
        job.setReducerClass(ReducerClass.class);
        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(IntWritable.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(IntWritable.class);
        job.setNumReduceTasks(32);
        FileInputFormat.addInputPath(job, new Path(args[1]));
        FileOutputFormat.setOutputPath(job, new Path(args[2]));

        // Set the number of mappers based on the subset of data you want to process

        if (job.waitForCompletion(true)) {
            long counterValue = job.getCounters().findCounter("NCounter", "N").getValue();
            String s3Region = "us-east-1";
            String s3BucketName = "jars123123123";

            conf.set("fs.s3a.impl", "org.apache.hadoop.fs.s3a.S3AFileSystem");
            conf.set("fs.s3a.endpoint", "s3." + s3Region + ".amazonaws.com");

            FileSystem fs = FileSystem.get(new URI("s3a://" + s3BucketName), conf);
            Path counterFile = new Path("s3a://" + s3BucketName + "/counter.txt");

            try (FSDataOutputStream out = fs.create(counterFile)) {
                out.writeUTF(Long.toString(counterValue));
            }
        } else {
            System.out.println("Step 1 failed ");
        }

    }
}
