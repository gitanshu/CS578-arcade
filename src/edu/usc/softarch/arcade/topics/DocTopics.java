package edu.usc.softarch.arcade.topics;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

import cc.mallet.pipe.CharSequence2TokenSequence;
import cc.mallet.pipe.CharSequenceLowercase;
import cc.mallet.pipe.CharSequenceReplace;
import cc.mallet.pipe.Pipe;
import cc.mallet.pipe.SerialPipes;
import cc.mallet.pipe.TokenSequence2FeatureSequence;
import cc.mallet.pipe.TokenSequenceRemoveStopwords;
import cc.mallet.topics.ParallelTopicModel;
import cc.mallet.topics.TopicInferencer;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import edu.usc.softarch.arcade.config.Config;
import edu.usc.softarch.arcade.util.FileListing;
import edu.usc.softarch.arcade.util.FileUtil;

/**
 * @author joshua
 *
 */
public class DocTopics {
	ArrayList<DocTopicItem> dtItemList = new ArrayList<DocTopicItem>();
	private Logger logger = Logger.getLogger(DocTopics.class);
	
	public DocTopics() {
		super();
	}
	
	public DocTopics(DocTopics docTopics) {
		for (DocTopicItem docTopicItem : docTopics.dtItemList) {
			dtItemList.add(new DocTopicItem(docTopicItem));
		}
	}
	
	public DocTopics(String srcDir, String artifactsDir, int numTopics, String topicModelFilename, String docTopicsFilename, String topWordsFilename) throws Exception {
//		FileWriter fw = new FileWriter("/Users/retina15/Desktop/DocTopics.txt");
//		fw.write("Creating DocTopics, called by ConcernClusteringRunner -> initializeDocTopicsForEachFastCluster \n");
		// Begin by importing documents from text to feature sequences
		ArrayList<Pipe> pipeList = new ArrayList<Pipe>();
		numTopics = 2;
		// Pipes: alphanumeric only, camel case separation, lowercase, tokenize,
		// remove stopwords english, remove stopwords java, stem, map to
		// features
		pipeList.add(new CharSequenceReplace(Pattern.compile("[^A-Za-z]"), " "));
		pipeList.add(new CamelCaseSeparatorPipe());
		pipeList.add(new CharSequenceLowercase());
		pipeList.add(new CharSequence2TokenSequence(Pattern
				.compile("\\p{L}[\\p{L}\\p{P}]+\\p{L}")));
		pipeList.add(new TokenSequenceRemoveStopwords(new File(
				"stoplists/en.txt"), "UTF-8", false, false, false));
		
		if (Config.getSelectedLanguage().equals(Config.Language.c)) {
			pipeList.add(new TokenSequenceRemoveStopwords(new File(
					"res/ckeywords"), "UTF-8", false, false, false));
			pipeList.add(new TokenSequenceRemoveStopwords(new File(
					"res/cppkeywords"), "UTF-8", false, false, false));
		}
		else {
			pipeList.add(new TokenSequenceRemoveStopwords(new File(
					"res/javakeywords"), "UTF-8", false, false, false));
		}
		pipeList.add(new StemmerPipe());
		pipeList.add(new TokenSequence2FeatureSequence());

		InstanceList instances = new InstanceList(new SerialPipes(pipeList));
		String testDir = srcDir;
		logger.debug("Building instances for mallet...");
//		fw.write("Creating instances :");
		
//		go through every file in srcDir
//		if it is a java file create an instance (data in the file, 'x', fullClassName, path)
//		add the instance to instances
//		
		for (File file : FileListing.getFileListing(new File(testDir))) {
			logger.debug("Should I add " + file.getName() + " to instances?");
//			fw.write("Should " + file.getName() + " be added to instances? \n");
			if (file.isFile() && file.getName().endsWith(".java")) {
				String shortClassName = file.getName().replace(".java", "");
				BufferedReader reader = new BufferedReader(new FileReader(file));
				String line = null;
				String fullClassName = "";
				while ((line = reader.readLine()) != null) {
					String packageName = FileUtil.findPackageName(line);
					if (packageName != null) {
						fullClassName = packageName + "." + shortClassName;
						logger.debug("\t I've identified the following full class name from analyzing files: " + fullClassName);
//						fw.write("\t identified the following full class name from analyzing files: " + fullClassName + "\n");
					}
				}
				reader.close();
				logger.debug("I'm going to add this file to instances: " + file);
//				fw.write("Adding to instances: " + file);
				String data = FileUtil.readFile(file.getAbsolutePath(),
						Charset.defaultCharset());
				Instance instance = new Instance(data, "X", fullClassName,
						file.getAbsolutePath());
				instances.addThruPipe(instance);
			}
			Pattern p = Pattern.compile("\\.(c|cpp|cc|s|h|hpp|icc|ia|tbl|p)$");
			// if we found a c or c++ file
			if ( p.matcher(file.getName()).find() ) {
				logger.debug("I'm going to add this file to instances: " + file);
				String depsStyleFilename = file.getAbsolutePath().replace(testDir, "");
				String data = FileUtil.readFile(file.getAbsolutePath(),
						Charset.defaultCharset());
				Instance instance = new Instance(data, "X", depsStyleFilename,
						file.getAbsolutePath());
				instances.addThruPipe(instance);
			}
		}
		
//		fw.write("\n created and added instance created from java files to instances");

		InstanceList previousInstances = InstanceList.load(new File(artifactsDir+"/output.pipe"));
		
		/*
		 * Reader fileReader = new InputStreamReader(new FileInputStream(new
		 * File( args[0])), "UTF-8"); instances.addThruPipe(new
		 * CsvIterator(fileReader, Pattern
		 * .compile("^(\\S*)[\\s,]*(\\S*)[\\s,]*(.*)$"), 3, 2, 1)); // data, //
		 * label, // name // fields
		 */
		// Create a model with 100 topics, alpha_t = 0.01, beta_w = 0.01
		// Note that the first parameter is passed as the sum over topics, while
		// the second is
		//int numTopics = 40;
		double alpha = (double) 50 / (double) numTopics;
		double beta = .01;
		ParallelTopicModel model = null ;
		File topicModelFile = new File(topicModelFilename);
		File docTopicsFile = new File(docTopicsFilename);
		File topWordsFile = new File(topWordsFilename);
		
		TopicInferencer inferencer = 
				TopicInferencer.read(new File(artifactsDir+"/infer.mallet"));
		
		
//		create dtItem for every instance from output.pipe 
//		dtItem has a list of TopicItem called topics
//		
		
		for (int instIndex = 0; instIndex < previousInstances.size(); instIndex++) {
//			fw.write("instIndex : " + instIndex + " previousInstances.size() : " + previousInstances.size() + "\n");
			DocTopicItem dtItem = new DocTopicItem();
			dtItem.doc = instIndex;
			dtItem.source = (String) previousInstances.get(instIndex).getName();
			
//			fw.write("dtItem.doc : " +  instIndex);
//			fw.write("dtItem.source : " + (String) previousInstances.get(instIndex).getName() + " \n");
			dtItem.topics = new ArrayList<TopicItem>();

//			double[] topicDistribution = model.getTopicProbabilities(instIndex);
			double[] topicDistribution = inferencer.getSampledDistribution(previousInstances.get(instIndex), 10, 1, 5);
//			fw.write("len topicdistributoin" + topicDistribution.length + "\n");
			for (int j = 0; j < topicDistribution.length; j++) {
//				fw.write(topicDistribution[j] + "  ");
			}
//			fw.write("\n");
			for (int topicIdx = 0; topicIdx < numTopics; topicIdx++) {
				TopicItem t = new TopicItem();
				t.topicNum = topicIdx;
				t.proportion = topicDistribution[topicIdx];
				dtItem.topics.add(t);
//				fw.write("\t added TopicItem \n");
			}
			dtItemList.add(dtItem);

		}
		
//		fw.write("\n Done with DocTopics");
//		fw.close();
		
	}
	
	/**
	 * @param filename
	 * @throws FileNotFoundException
	 */
	public DocTopics(String filename) throws FileNotFoundException {
		loadFromFile(filename);
	}
	
	public DocTopicItem getDocTopicItemForJava(String name) {
//		return the a DocTopicItem with the same name 
		for (DocTopicItem dti : dtItemList) {
			String altName = name.replaceAll("/", ".").replaceAll(".java", "").trim();
			if (dti.source.endsWith(name)) {
				return dti;
			}
			else if (altName.equals(dti.source.trim())) {
				return dti;
			}
		}
		return null;
	}
	
	public DocTopicItem getDocTopicItemForC(String name) {
		for (DocTopicItem dti : dtItemList) {
			String strippedSource = null;
			String nameWithoutQuotations = null;
			if (dti.source.endsWith(".func")) {
				strippedSource = dti.source.substring(dti.source.lastIndexOf('/')+1,dti.source.lastIndexOf(".func"));
				nameWithoutQuotations = name.replaceAll("\"", "");
				if (strippedSource.contains(nameWithoutQuotations)) {
					return dti;
				}
			} else if (dti.source.endsWith(".c") || dti.source.endsWith(".h")
					|| dti.source.endsWith(".tbl") || dti.source.endsWith(".p")
					|| dti.source.endsWith(".cpp") || dti.source.endsWith(".s")
					|| dti.source.endsWith(".hpp")
					|| dti.source.endsWith(".icc")
					|| dti.source.endsWith(".ia")) {
				//strippedSource = dti.source.substring(dti.source.lastIndexOf('/')+1,dti.source.length());
				nameWithoutQuotations = name.replaceAll("\"", "");
				if (dti.source.endsWith(nameWithoutQuotations)) {
					return dti;
				}
			}
			else if (dti.source.endsWith(".S")) {
				String dtiSourceRenamed = dti.source.replace(".S", ".c");
				nameWithoutQuotations = name.replaceAll("\"", "");
				if (dtiSourceRenamed.endsWith(nameWithoutQuotations)) {
					return dti;
				}
			}
			else {
				//System.err.println("Unknown file type for " + dti.source);
				//System.exit(1);
				//return dti;
				continue;
			}
			
		}
		logger.error("Cannot find doc topic for: " + name);
		return null;
	}
	
	public ArrayList<DocTopicItem> getDocTopicItemList() {
		return dtItemList;
	}

	public void loadFromFile(String filename) throws FileNotFoundException {
		logger.debug("Loading DocTopics from file...");
		boolean localDebug = false;
		File f = new File(filename);

		Scanner s = new Scanner(f);

		dtItemList = new ArrayList<DocTopicItem>();
		
		while (s.hasNext()) {
			String line = s.nextLine();
			if (line.startsWith("#")) {
				continue;
			}
			String[] items = line.split("\\s+");

			DocTopicItem dtItem = new DocTopicItem();
			dtItem.doc = (new Integer(items[0])).intValue();
			dtItem.source = items[1];

			dtItem.topics = new ArrayList<TopicItem>();

			TopicItem t = new TopicItem();
			for (int i = 2; i < items.length; i++) {
				if (i % 2 == 0) {
					t.topicNum = (new Integer(items[i])).intValue();
				} else {
					t.proportion = (new Double(items[i])).doubleValue();
					dtItem.topics.add(t);
					t = new TopicItem();
				}
			}
			dtItemList.add(dtItem);
			if (localDebug)
				logger.debug(line);

		}
		
		if (localDebug)
			logger.debug("\n");
		for (DocTopicItem dtItem : dtItemList) {
			if (localDebug)
				logger.debug(dtItem);
		}

	}	

}
