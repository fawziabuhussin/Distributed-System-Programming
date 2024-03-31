import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.InstanceType;
import com.amazonaws.services.elasticmapreduce.AmazonElasticMapReduce;
import com.amazonaws.services.elasticmapreduce.AmazonElasticMapReduceClientBuilder;
import com.amazonaws.services.elasticmapreduce.model.*;
import java.util.Random;


public class App {
    public static AWSCredentialsProvider credentialsProvider;
    public static AmazonS3 S3;
    public static AmazonEC2 ec2;
    public static AmazonElasticMapReduce emr;
    public static String bucketForJars = "jars123123123";
    public static int numberOfInstances = 6;


    public static void main(String[] args) {

        credentialsProvider = new ProfileCredentialsProvider();
        System.out.println("[INFO] Connecting to aws");
        ec2 = AmazonEC2ClientBuilder.standard()
                .withCredentials(credentialsProvider)
                .withRegion("us-east-1")
                .build();
        // S3 = S3Client.builder().region("us-east-1").build();
        S3 = AmazonS3ClientBuilder.standard()
                .withCredentials(credentialsProvider)
                .withRegion("us-east-1")
                .build();
        emr = AmazonElasticMapReduceClientBuilder.standard()
                .withCredentials(credentialsProvider)
                .withRegion("us-east-1")
                .build();

        S3 = AmazonS3ClientBuilder.standard()
        .withCredentials(credentialsProvider)
        .withRegion("us-east-1")
        .build();



        // Step 1
        Random rand = new Random();
        String input_step1 = "s3://datasets.elasticmapreduce/ngrams/books/20090715/eng-all/2gram/data";
        String output_step1 = "s3://jars123123123/outputs/step1_output/" + rand.nextInt(1000);

        HadoopJarStepConfig step1 = new HadoopJarStepConfig()
        .withJar("s3://" + bucketForJars + "/jars/Step1.jar") 
        .withArgs(input_step1, output_step1)
        .withMainClass("Step1");

        StepConfig stepConfig1 = new StepConfig()
                .withName("Step1")
                .withHadoopJarStep(step1)
                .withActionOnFailure("TERMINATE_JOB_FLOW");

                
        // Step 2
        String input_step2 = output_step1;
        String output_step2 = "s3://jars123123123/outputs/step2_output/" + rand.nextInt(1000);
        HadoopJarStepConfig step2 = new HadoopJarStepConfig()

        .withJar("s3://" + bucketForJars + "/jars/Step2.jar") 
        .withArgs(input_step2, output_step2)
        .withMainClass("Step2");

        StepConfig stepConfig2 = new StepConfig()
                .withName("Step2")
                .withHadoopJarStep(step2)
                .withActionOnFailure("TERMINATE_JOB_FLOW");


        // Step 3
        String input_step3 = output_step2;
        String output_step3 = "s3://jars123123123/outputs/step3_output/" + rand.nextInt(1000);
        HadoopJarStepConfig step3 = new HadoopJarStepConfig()

        .withJar("s3://" + bucketForJars + "/jars/Step3.jar") 
        .withArgs(input_step3, output_step3)
        .withMainClass("Step3");

        StepConfig stepConfig3 = new StepConfig()
                .withName("Step3")
                .withHadoopJarStep(step3)
                .withActionOnFailure("TERMINATE_JOB_FLOW");

        // Step 3
        String input_step4 = output_step3;
        String output_step4 = "s3://jars123123123/outputs/step4_output/" + rand.nextInt(1000);
        HadoopJarStepConfig step4 = new HadoopJarStepConfig()

        .withJar("s3://" + bucketForJars + "/jars/Step4.jar") 
        .withArgs(input_step4, output_step4, args[0], args[1])
        .withMainClass("Step4");

        StepConfig stepConfig4 = new StepConfig()
                .withName("Step4")
                .withHadoopJarStep(step4)
                .withActionOnFailure("TERMINATE_JOB_FLOW");


        //Job flow
        JobFlowInstancesConfig instances = new JobFlowInstancesConfig()
                .withInstanceCount(numberOfInstances)
                .withMasterInstanceType(InstanceType.M4Large.toString())
                .withSlaveInstanceType(InstanceType.M4Large.toString())
                .withHadoopVersion("2.9.2")
                .withEc2KeyName("vockey")
                .withKeepJobFlowAliveWhenNoSteps(false)
                .withPlacement(new PlacementType("us-east-1a"));

        System.out.println("Set steps");
        RunJobFlowRequest runFlowRequest = new RunJobFlowRequest()
                .withName("Extract Collations - 100% Of Eng-All.")
                .withInstances(instances)
                .withSteps(stepConfig1, stepConfig2, stepConfig3, stepConfig4)
                .withLogUri("s3://" + bucketForJars + "/logs/")
                .withServiceRole("EMR_DefaultRole")
                .withJobFlowRole("EMR_EC2_DefaultRole")
                .withReleaseLabel("emr-5.11.0");

        RunJobFlowResult runJobFlowResult = emr.runJobFlow(runFlowRequest);
        String jobFlowId = runJobFlowResult.getJobFlowId();
        System.out.println("Ran job flow with id: " + jobFlowId);
        
    }

    
}


