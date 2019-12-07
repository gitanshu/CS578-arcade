package edu.usc.softarch.arcade.clustering;

import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.File;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

import edu.usc.softarch.arcade.clustering.util.ClusterUtil;
import edu.usc.softarch.arcade.config.Config;
import edu.usc.softarch.arcade.config.Config.Granule;
import edu.usc.softarch.arcade.config.Config.Language;
import edu.usc.softarch.arcade.util.FileListing;


public class ClusteringAlgoRunner {

	private static Logger logger = Logger.getLogger(ClusteringAlgoRunner.class);
	protected static ArrayList<FastCluster> fastClusters;
	public static ArrayList<FastCluster> getFastClusters() {
		return fastClusters;
	}

	protected static ArrayList<Cluster> clusters;
	protected static FastFeatureVectors fastFeatureVectors;
	protected static double maxClusterGain = 0;
	protected static int numClustersAtMaxClusterGain = 0;
	protected static int numberOfEntitiesToBeClustered = 0;
	
//	creates a list of fastCluster called fastClusters
//	create fastCluster for every Feature in fastFeatureVector
//	create fastCluster for every java file in srcDir
	protected static void initializeClusters(String srcDir) throws IOException {
//		FileWriter fw = new FileWriter("/Users/retina15/Desktop/ConcernAlgoRunner.txt");
//		fw.write("initializeClusters(srcDir) Called by ConcernClusteringRunner constructor\n");
		fastClusters = new ArrayList<FastCluster>();

		for (String name : fastFeatureVectors.getFeatureVectorNames()) {
//			fw.write("name : " + name + "\n");
			BitSet featureSet = (BitSet) fastFeatureVectors
					.getNameToFeatureSetMap().get(name);
			FastCluster fastCluster = new FastCluster(name, featureSet,
					fastFeatureVectors.getNamesInFeatureSet());
			
			addClusterConditionally(fastCluster);
		}
		
		
//		Create a Fast cluster for every java file
		try {
			if (fastClusters.isEmpty()) {
//				fw.write("fastClusters was empty \n");
				List<File> javaFiles = FileListing.getFileListing(new File(srcDir),
						".java");
				
				for (File javaFile : javaFiles) {
					FastCluster cluster = new FastCluster(javaFile.getPath().toString());
//					fw.write("javaFile Name : " + javaFile.getPath().toString() + "\n");
					fastClusters.add(cluster);
				}
			}
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
//		List<File> javaFiles = FileListing.getFileListing(new File(srcDir),
//				".java");
//		System.out.println(javaFiles.toString());
		
		FastCluster myCluster = new FastCluster("/Users/retina15/Documents/CS578-arcade/../CS578/hw3/CWE287-Test/src/tomcat7/java/vul.java");
		fastClusters.add(myCluster);
		logger.debug("Listing initial cluster names using for-each...");
//		fw.write("List of fastCluster in fastClusters : \n");
		for (FastCluster cluster : fastClusters) {
			logger.debug(cluster.getName());
//			fw.write(cluster.getName() + "\n");
		}
		
//		logger.debug("Listing initial cluster names using indexed loop...");
//		for (int i = 0; i < fastClusters.size(); i++) {
//			FastCluster cluster = (FastCluster) fastClusters.get(i);
//			logger.debug(cluster.getName());
//		}

		numberOfEntitiesToBeClustered = fastClusters.size();
		logger.debug("number of initial clusters: " + numberOfEntitiesToBeClustered);
//		fw.write("Number of initial clusters (fastClusters) : " + numberOfEntitiesToBeClustered);
//		fw.close();

	}

	private static void addClusterConditionally(FastCluster fastCluster) {
		if (Config.ignoreDependencyFilters) {
			fastClusters.add(fastCluster);
			return;
		}
		
		if (Config.getSelectedLanguage().equals(Language.c)) {
			Pattern p = Pattern.compile("\\.(c|cpp|cc|s|h|hpp|icc|ia|tbl|p)$");
			if (	
					Config.getClusteringGranule().equals(Granule.file) && 
					isSingletonClusterNonexcluded(fastCluster) &&
					!fastCluster.getName().startsWith("/") &&
				
					(	
							p.matcher(fastCluster.getName()).find()
					)
				)  
				{
						fastClusters.add(fastCluster);
				}
				else {
					logger.debug("Excluding file: " + fastCluster.getName());
				}
		}
		if (Config.getClusteringGranule().equals(Granule.func)) {
			if (fastCluster.getName().equals("\"##\"")) {
				return;
			}
			fastClusters.add(fastCluster);
		}
		if (Config.getSelectedLanguage().equals(Language.java)) {
			if (Config.isClassInSelectedPackages(fastCluster.getName())) {
					fastClusters.add(fastCluster);
			}
		}
	}

	public static boolean isSingletonClusterNonexcluded(FastCluster fastCluster) {
		if (Config.getExcludedEntities() == null) {
			return true;
		}
		return !Config.getExcludedEntities().contains(fastCluster.getName());
	}
	
	protected static void checkAndUpdateClusterGain(double clusterGain) {
		if (logger.isDebugEnabled()) {
			logger.debug("Current cluster gain: " + clusterGain);
			logger.debug("Current max cluster gain: " + maxClusterGain);
		}

		if (clusterGain > maxClusterGain) {
			if (logger.isDebugEnabled()) {
				logger.debug("Updating max cluster gain and num clusters at it...");
			}
			maxClusterGain = clusterGain;
			numClustersAtMaxClusterGain = fastClusters.size();
		}
	}
	
	protected static void printTwoMostSimilarClustersUsingStructuralData(
			MaxSimData maxSimData) {
		if (logger.isDebugEnabled()) {
			logger.debug("In, "
					+ Thread.currentThread().getStackTrace()[1].getMethodName()
					+ ", \nMax Similar Clusters: ");

			ClusterUtil.printSimilarFeatures(maxSimData.c1, maxSimData.c2,
					fastFeatureVectors);

			logger.debug(maxSimData.currentMaxSim);
			logger.debug("\n");

			logger.debug("before merge, clusters size: " + fastClusters.size());
			
		}
	}
	
	public static void setFastFeatureVectors(
			FastFeatureVectors inFastFeatureVectors) {
		fastFeatureVectors = inFastFeatureVectors;
		
	}
	
	protected static void performPostProcessingConditionally() {
		if (Config.getClustersToWriteList() == null) {
			logger.debug("Config.getClustersToWriteList() == null so skipping post processing");
			return;
		}
		if (Config.getClustersToWriteList().contains(fastClusters.size())) {
			String postProcMsg = "Performing post processing at " + fastClusters.size() + " number of clusters";
			logger.debug(postProcMsg);
			ClusterUtil.fastClusterPostProcessing(fastClusters,fastFeatureVectors);
		}
	}
}
