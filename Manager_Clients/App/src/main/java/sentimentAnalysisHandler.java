// import java.util.Properties;
// import edu.stanford.nlp.ling.CoreAnnotations;
// import edu.stanford.nlp.neural.rnn.RNNCoreAnnotations;
// import edu.stanford.nlp.pipeline.Annotation;
// import edu.stanford.nlp.pipeline.StanfordCoreNLP;
// import edu.stanford.nlp.sentiment.SentimentCoreAnnotations;
// import edu.stanford.nlp.trees.Tree;
// import edu.stanford.nlp.util.CoreMap;
// import java.io.BufferedReader;
// import java.io.FileReader;
// import java.io.IOException;

// import com.fasterxml.jackson.databind.JsonNode;
// import com.fasterxml.jackson.databind.ObjectMapper;

// public class sentimentAnalysisHandler {

//     private static Properties props;
//     private static StanfordCoreNLP sentimentPipeline;

//     static {
//         // This block will be executed once when the class is loaded
//         props = new Properties();
//         props.put("annotators", "tokenize, ssplit, parse, sentiment");
//         sentimentPipeline = new StanfordCoreNLP(props);
//     }


//     public static void main(String[] args) {
//         String filePath = "/home/spl211/Desktop/aws/src/main/input1.txt";
//         BufferedReader reader = null;
        
//         try {
//              reader = new BufferedReader(new FileReader(filePath));
//             String line;
//             ObjectMapper objectMapper = new ObjectMapper();

//             while ((line = reader.readLine()) != null) {
//                 JsonNode jsonNode = objectMapper.readTree(line);
//                 JsonNode reviewsNode = jsonNode.get("reviews");
//                 if (reviewsNode != null && reviewsNode.isArray()) {
//                     for (JsonNode review : reviewsNode) {
//                         System.out.println("Sentiment: " + findSentiment(review.toString()));
//                     }
//                 }
//             }
            
//         } catch (IOException e) {
//             e.printStackTrace();
//         }

//         finally {
//             if (reader != null) {
//                 try {
//                     reader.close();
//                 } catch (IOException e) {
//                     e.printStackTrace();
//                 }
//             }
//         }
//     }

//     public static int findSentiment(String review) {

//         int mainSentiment = 0;
//         if (review != null && review.length() > 0) {
//             int longest = 0;
//             Annotation annotation = sentimentPipeline.process(review);
//             for (CoreMap sentence : annotation
//                     .get(CoreAnnotations.SentencesAnnotation.class)) {
//                 Tree tree = sentence.get(
//                         SentimentCoreAnnotations.SentimentAnnotatedTree.class);
//                 int sentiment = RNNCoreAnnotations.getPredictedClass(tree);
//                 String partText = sentence.toString();
//                 if (partText.length() > longest) {
//                     mainSentiment = sentiment;
//                     longest = partText.length();
//                 }
//             }
//         }
//         return mainSentiment;
//     }

    

// }
