// import java.util.List;
// import java.util.Properties;
// import edu.stanford.nlp.ling.CoreAnnotations.NamedEntityTagAnnotation;
// import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
// import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
// import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
// import edu.stanford.nlp.ling.CoreLabel;
// import edu.stanford.nlp.pipeline.Annotation;
// import edu.stanford.nlp.pipeline.StanfordCoreNLP;
// import edu.stanford.nlp.util.CoreMap;

// import java.io.BufferedReader;
// import java.io.FileReader;
// import java.io.IOException;

// import com.fasterxml.jackson.databind.JsonNode;
// import com.fasterxml.jackson.databind.ObjectMapper;


// public class nameEntityRecognitionHandler {
    
//     private static Properties props;
//     private static StanfordCoreNLP NERPipeline;

//     static {

//         props = new Properties();
//         props.put("annotators", "tokenize , ssplit, pos, lemma, ner");
//         NERPipeline = new StanfordCoreNLP(props);
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
//                         printEntities(review.toString());
//                     }
//                 }
//                 // System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(json));
                
                
//                 break;
//             }

//             // printEntities(content_splits[0]);

            
//         } catch (IOException e) {
//             e.printStackTrace();
//         }

//         finally {
//             // Close the reader in the finally block to ensure it is closed even if an exception occurs
//             if (reader != null) {
//                 try {
//                     reader.close();
//                 } catch (IOException e) {
//                     e.printStackTrace();
//                 }
//             }
//         }
//     }


    
//     public static void printEntities(String review) {

//         // create an empty Annotation just with the given text
//         Annotation document = new Annotation(review);
//         // run all Annotators on this text
//         NERPipeline.annotate(document);
//         // these are all the sentences in this document
//         // a CoreMap is essentially a Map that uses class objects as keys and has values
//         // with
//         // custom types
//         List<CoreMap> sentences = document.get(SentencesAnnotation.class);
//         for (CoreMap sentence : sentences) {
//             // traversing the words in the current sentence
//             // a CoreLabel is a CoreMap with additional token-specific methods
//             for (CoreLabel token : sentence.get(TokensAnnotation.class)) {
//                 // this is the text of the token
//                 String word = token.get(TextAnnotation.class);
//                 // this is the NER label of the token
//                 String ne = token.get(NamedEntityTagAnnotation.class);
//                 System.out.println("\t-" + word + ":" + ne);
//             }
//         }
//     }
// }
