import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.semgraph.*;
import edu.stanford.nlp.util.*;
import java.util.*;
import java.io.*;
import java.util.regex.*;

public class Main {

    public static void main(String[] args) throws FileNotFoundException {

        //variables to store parts of use case
        String preCondition = "";
        String postCondition = "";
        String useCaseName = "";
        List<String> mainSuccessScenario = new ArrayList<>();
        List<String> extensions = new ArrayList<>();
        List<String> textForAnalysis = new ArrayList<>();

        //pre-condition patterns to look for
        String ifThenPattern = "(?i)if\\s+(.*?)(?:\\s+then)?[,:]\\s+(.*)";
        String whenPattern = "(?i)when\\s+(.*?)\\s*[:,]\\s*(.*)";
        String forEachPattern = "(?i)for each\\s+(\\w+)\\s*[,:](.*)";
        String whilePattern = "(?i)while\\s+(.*?)\\s*[:,]\\s*(.*)";


        // Open the file and create a scanner to read from it
        File file = new File("src/main/java/sample.txt");
        Scanner scanner = new Scanner(file);

        // Split use case in parts
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine().trim();

            // Extract the pre-condition
            if (line.startsWith("Preconditions:")) {
                preCondition = line.substring("Preconditions:".length()).trim();
            }

            // Extract the post-condition
            if (line.startsWith("Success End Condition:")) {
                postCondition = line.substring("Success End Condition:".length()).trim();
            }

            // Extract the use case name
            if (line.startsWith("Use Case:")) {
                useCaseName = line.substring("Use Case:".length()).trim();
            }

            // Extract the main success scenario
            if (line.equals("MAIN SUCCESS SCENARIO:")) {
                while (scanner.hasNextLine()) {
                    line = scanner.nextLine().trim();
                    if (line.startsWith("EXTENSIONS:") || line.equals("SUB-VARIATIONS:")) {
                        break;
                    }
                    mainSuccessScenario.add(line);
                }
            }

            // Extract the extensions
            if (line.equals("EXTENSIONS:")) {
                while (scanner.hasNextLine()) {
                    line = scanner.nextLine().trim();
                    if (line.startsWith("SUB-VARIATIONS:")) {
                        break;
                    }
                    extensions.add(line);
                }
            }
        }

        // Add all the lines from mainSuccessScenario, extensions, and subVariations to the textForAnalysis list
        textForAnalysis.add(useCaseName);
        textForAnalysis.addAll(mainSuccessScenario);
        textForAnalysis.addAll(extensions);

        // Close the scanner
        scanner.close();

        // set up pipeline properties
        Properties props = new Properties();
        // set the list of annotators to run
        props.setProperty("annotators", "tokenize,ssplit,pos,parse,depparse");
        // set a property for an annotator, in this case the coref annotator is being set to use the neural algorithm
        props.setProperty("coref.algorithm", "neural");
        // build pipeline
        StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
        // Create an Annotation object to represent the text you want to analyze
        Annotation document = new Annotation(String.join(". ", textForAnalysis));
        // Annotate the document using the pipeline
        pipeline.annotate(document);

        List<FunctionalFeature> ffsBig = new ArrayList<>(); //all ffs
        int id = 1, i = 0, iPrecond = 0, idPrecond = 0, iExtension = -2;
        String preCond="";
        List<CoreMap> sentences = document.get(CoreAnnotations.SentencesAnnotation.class);
        boolean loopOrCon = true; // is the ff inside a loop or conditional statement

        // iterate over each sentence
        for (CoreMap sentence : sentences)  {
            System.out.println("\n" + sentence);
            List<FunctionalFeature> ffs = new ArrayList<>(); //ffs of the sentence

            SemanticGraph dependencies = sentence.get(SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class);

            //step numbering
            if (dependencies.edgeCount() == 1){
                for (SemanticGraphEdge edge : dependencies.edgeIterable()) {
                    try {
                        i = Integer.parseInt(edge.getSource().word());
                    } catch (NumberFormatException e){
                        i=-2;
                        iExtension=Integer.parseInt(edge.getSource().word().substring(0,1));
                    }
                }
            }

            //create new functional feature
            String action="", result= "", object="", n1 = "", actor = "";
            FunctionalFeature ff = new FunctionalFeature(actor, action, result, object, id++, i);
            ffs.add(ff);

            //Check if pre-condition is set for this sentence
            if (!preCond.equals("")){
                if (i==iPrecond && i!=-2) {
                    ff.setPrCond(preCond);
                    ff.condOrLoop = loopOrCon; //this ff is inside a loop or conditional statement
                    if (FunctionalFeature.getFF(ffsBig, idPrecond) != null)
                        ff.addToCauseID(idPrecond);
                }
            }

            //Check for "the use case begins when" sentence
            if (sentence.toString().contains("The use case begins when")){
                sentence.set(CoreAnnotations.TextAnnotation.class,
                        sentence.toString().replaceFirst("(?i)^the use case begins when\\s+", ""));
                preCond=sentence.toString();
                iPrecond=i+1;
                idPrecond=ff.getID();
                loopOrCon=false;
            }

            // Check if sentence matches any of the  patterns
            Matcher ifThenMatcher = Pattern.compile(ifThenPattern).matcher(sentence.toString());
            Matcher forEachMatcher = Pattern.compile(forEachPattern).matcher(sentence.toString());
            Matcher whileMatcher = Pattern.compile(whilePattern).matcher(sentence.toString());
            Matcher whenMatcher = Pattern.compile(whenPattern).matcher(sentence.toString());

            if (i==-2){
                preCond=sentence.toString().split(":")[0];
                iPrecond=-2;
                List<Integer> idsOfStep = FunctionalFeature.getFfsByStep(ffsBig, iExtension);
                loopOrCon=true;
                if (!idsOfStep.isEmpty()){
                    for (Integer ffID : idsOfStep)
                        ff.addToCauseID(ffID);
                }
            }
            if (ifThenMatcher.matches()){
                preCond=ifThenMatcher.group(1);
                iPrecond=i;
                idPrecond=ff.getID();
                loopOrCon=true;
            } else if (whenMatcher.matches()){
                preCond=whenMatcher.group(1);
                iPrecond=i;
                idPrecond=ff.getID();
                loopOrCon=true;
            } else if (whileMatcher.matches()){
                preCond=whileMatcher.group(1);
                iPrecond=i;
                idPrecond=ff.getID();
                loopOrCon=true;
            } else if (forEachMatcher.matches()){
                preCond="For each" + forEachMatcher.group(1);
                iPrecond=i;
                idPrecond=ff.getID();
                loopOrCon=true;
            }

            //Rule 7.2. Search for redirection to the indicated step
            Pattern pattern = Pattern.compile(".*step\\s+(\\d+).*");
            Matcher matcher = pattern.matcher(sentence.toString());
            if (matcher.matches()){
                ff.addToEffectStepID(Integer.parseInt(matcher.group(1)));
            }

            //for the use case name, precondition is defined
            if(i==1)
                ff.setPrCond(preCondition);

            // iterate over the edges of the semantic graph, find action and n1
            for (SemanticGraphEdge edge : dependencies.edgeIterable()) {

                String depType = edge.getRelation().getShortName();
                String sourceWord = edge.getSource().word();
                String targetWord = edge.getTarget().word();
                String sourcePos = edge.getSource().get(CoreAnnotations.PartOfSpeechAnnotation.class);
                String targetPos = edge.getTarget().get(CoreAnnotations.PartOfSpeechAnnotation.class);

                System.out.println(sourceWord + " (" +  sourcePos + ") -> " + depType + " -> " + targetWord +  "(" + targetPos + ")");

                //find action and n1
                if (sourcePos.matches("VBZ|VBP|VBD|VBN|VBG|VB.*((compound:prt)|(particle))?.*"))
                    if(targetPos.matches("NN|NNS|PRP")) {
                    if (depType.matches("obj|obl")) {
                        //check if this is second part of extension - if so add precondition
                        boolean extensionEvent = i==-2 && sentence.toString().indexOf(":")<sentence.toString().indexOf(sourceWord);
                        if(ff.getAction().equals("")){
                            ff.setAction(sourceWord);
                            ff.setN1(targetWord);
                            if (extensionEvent){
                                ff.setPrCond(preCond);
                            }
                        } else {
                            // add new ff for each action
                            FunctionalFeature ffNew = new FunctionalFeature(actor,sourceWord, result, object, id++, i);
                            ffNew.setN1(targetWord);
                            ffs.add(ffNew);
                            if (i==-2){
                              ffNew.setCauseID(ff.getCauseID());
                            }
                            if (extensionEvent){
                                ffNew.setPrCond(preCond);
                            }
                        }
                    }
                }
            }

            //Iterate each action to find object, result and actor
            boolean flag = false, rule21c = false, rule22 = false;
            int size = ffs.size(); //the size will change as new ffs are added
            for (int j = 0; j < size; j++){
                FunctionalFeature obj = ffs.get(j);
                for (SemanticGraphEdge edge : dependencies.edgeIterable()) {
                    String depType = edge.getRelation().getShortName();
                    String sourceWord = edge.getSource().word();
                    String targetWord = edge.getTarget().word();
                    String targetPos = edge.getTarget().get(CoreAnnotations.PartOfSpeechAnnotation.class);

                    if (sourceWord.equals(obj.getAction()) && depType.equals("nsubj") && targetPos.matches("NN|NNS|PRP")) {
                        if (obj.getActor()=="")
                            obj.setActor(targetWord);
                        else {
                            FunctionalFeature ffNew = new FunctionalFeature(targetWord,obj.getAction(), obj.getResult(), obj.getObject(), id++, i);
                            ffs.add(ffNew);
                        }
                    }
                    if (depType.equals("compound:prt") && sourceWord.equals(obj.getAction())) {
                        obj.setAction(obj.getAction() + " " + targetWord);
                    }

                    if (sourceWord.equals(obj.getAction()) && depType.contains("nmod") && !depType.equals("nmod:agent")){
                        if (targetPos.matches("NN|NNS|PRP")){
                            if(obj.getResult()==""){
                                rule22 = true;
                                obj.setObject(targetWord);
                                obj.setResult(obj.getN1());
                            } else {
                                FunctionalFeature ffNew = new FunctionalFeature(obj.getActor(), obj.getAction(), obj.getN1(), targetWord, id++, i);
                                ffs.add(ffNew);
                            }

                        }
                    }
                    if (rule22 && sourceWord.equals(obj.getObject()) && depType.equals("case")){
                        obj.setResult(obj.getResult() + " " + targetWord);
                    }

                    if (sourceWord.equals(obj.getN1())) {
                        if (depType.equals("compound")) {
                            flag = true;
                            if (obj.getResult()==""){
                                obj.setResult(obj.getN1() + " of");
                                obj.setObject(targetWord);
                            } else {
                                FunctionalFeature ffNew = new FunctionalFeature(obj.getActor(), obj.getAction(), obj.getN1() + " of", targetWord, id++, i);
                                ffs.add(ffNew);
                            }
                        } else if (depType.matches("nmod:(poss|of|to|into|from|for)")){
                            flag = true;
                            rule21c = true;
                            if (obj.getResult()==""){
                                obj.setResult(obj.getN1());
                                obj.setObject(targetWord);
                            } else {
                                FunctionalFeature ffNew = new FunctionalFeature(obj.getActor(), obj.getAction(), obj.getN1(), targetWord, id++, i);
                                ffs.add(ffNew);
                            }
                        }
                    }
                    if (rule21c && sourceWord.equals(object) && depType.equals("case"))
                        obj.setResult(obj.getResult() + " " + targetWord);
                }
                if (!flag){
                    if (obj.getObject()==""){
                        obj.setObject(obj.getN1());
                    } else {
                        FunctionalFeature ffNew = new FunctionalFeature(obj.getActor(), obj.getAction(),"", obj.getN1(), id++, i);
                        ffs.add(ffNew);
                    }

                }
            }

            //for each ff with the same action but different object/actor/result - match all other missing attributes

            for (FunctionalFeature ff1 : ffs) {
                boolean unique = true;
                for (FunctionalFeature ff2 : ffs) {
                    if (ff1==ff2) continue;
                    if (ff1.getAction().equals(ff2.getAction())){
                        if(ff2.getObject().equals(""))
                            ff2.setObject(ff1.getObject());
                        if(ff2.getResult().equals(""))
                            ff2.setResult(ff1.getResult());
                        if(ff2.getActor().equals(""))
                            ff2.setActor(ff1.getActor());
                        if(ff2.getPrCond().equals(""))
                            ff2.setPrCond(ff1.getPrCond());
                        if(ff1.getObject().equals(ff2.getObject()) && ff1.getResult().equals(ff2.getResult())
                        && ff1.getActor().equals(ff2.getActor()))
                            unique = false;
                    }
                }
                if (unique && !ff1.getAction().equals(""))
                    ffsBig.add(ff1); //add ffs to the full list
            }
        }

        //delete duplicate ffs and empty ffs
        Iterator<FunctionalFeature> iter = ffsBig.iterator();
        Set<String> seen = new HashSet<String>();
        while (iter.hasNext()) {
            FunctionalFeature ff = iter.next();
            if (ff.getAction().equals("")){
                iter.remove();
                continue;
            }
        }

        //Rule 7.1. Search for a sequence of steps + Rule 7.2. Search for redirection to the indicated step
        for (FunctionalFeature ff : ffsBig){
            if (ff.condOrLoop)
                continue;

            ff.addPreviousStepIds(ffsBig);
            if (!ff.getEffectStepID().isEmpty()){
                for (Integer effect : ff.getEffectStepID()){
                    for (FunctionalFeature ff2 : ffsBig){
                        if (ff2.getStepID() == effect){
                            ff2.addToCauseID(ff.getID());
                            break;
                        }
                    }
                }
            }
        }

        //Results:
        for (FunctionalFeature ff : ffsBig) {
            System.out.println("Actor: " + ff.getActor());
            System.out.println("Action: " + ff.getAction());
            System.out.println("Result: " + ff.getResult());
            System.out.println("Object: " + ff.getObject());
            System.out.println("ID: " + ff.getID());
            System.out.println("Sentence: " + ff.getStepID());
            System.out.println("Precondition: " + ff.getPrCond());
            System.out.print("Cause: ");
            if (ff.getCauseID()!=null){
                for (Integer cause : ff.getCauseID()){
                    System.out.print(cause + " ");
                }
            }
            System.out.println("\n");
        }

    }
}