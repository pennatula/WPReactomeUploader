package wpreactome;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.rpc.ServiceException;

import org.apache.commons.lang.WordUtils;
import org.pathvisio.core.model.ConverterException;
import org.pathvisio.core.model.ObjectType;
import org.pathvisio.core.model.Pathway;
import org.pathvisio.core.model.PathwayElement;
import org.pathvisio.wikipathways.webservice.WSCurationTag;
import org.pathvisio.wikipathways.webservice.WSPathwayInfo;
import org.wikipathways.client.WikiPathwaysClient;

/**
 * Capitalise Titles
 * Change Data Source
 * Calculate completion score & write to output file
 * Update and tag plant pathways
 * 
 * @author anwesha
 */

public class PlantPathwaysFixer {
	private final File pathwayDir;
	private final File scoreFile;
	private final File prIdFile;
	private final String username;
	private final String password;
	private final String comment;
	private final static String wikiSource = "WikiPathways-description";
	private static String plantReactomeURL = "\nSource:[http://plantreactome.gramene.org/"
			+ " Plant Reactome].";
	private static String wikipathwaysURL = "http://wikipathways.org/wpi/webservice/webservice.php";
	private final WikiPathwaysClient client;
	private static List<String> errorList;
	private final List<String> taggedPathways;
	private final Map<String, Pathway> pathwaysToUpload;
	final String PLANT_DATA_SOURCE = "Plant Reactome - http://plantreactome.gramene.org";
	final String PLANT_VERSION = "43";
	private final Map<String, String> plantReactomePathways;
	private float unconnectedInteractions;
	private float totalInteractions;
	private static HashMap<String, Float> scoreMap;

	public PlantPathwaysFixer(File pathwayDir, File prIdFile, File outputFile, String username, String password, String comment)
			throws MalformedURLException,
			ServiceException {
		client = new WikiPathwaysClient(new URL(wikipathwaysURL));
		scoreMap = new HashMap<String, Float>();
		plantReactomePathways = new HashMap<String, String>();
		pathwaysToUpload = new HashMap<String, Pathway>();
		taggedPathways = new ArrayList<String>();
		this.pathwayDir = pathwayDir;
		this.prIdFile = prIdFile;
		this.scoreFile = outputFile;
		this.username = username;
		this.password = password;
		this.comment = comment;
	}

	public static void main(String args[]) {
		if (args.length == 6) {

			String pathwayDir = args[0];
			String scoreFile = args[1];
			String idFile = args[2];
			String username = args[3];
			String password = args[4];
			String comment = args[5];

			try {
				PlantPathwaysFixer wr = new PlantPathwaysFixer(new File(pathwayDir), new File(scoreFile), new File(idFile),
						username, password, comment);
				
				
				if (wr.checkPathwayDir()) {
					System.out
							.println("Valid pathway directory.\nReading GPML files...");
					wr.plantPwyMechanic();
					wr.createPlantReactomeIdMap(new File(idFile));
					wr.updateNtagPlantReactomePathways();
				}
				
			} catch (MalformedURLException e) {
				e.printStackTrace();
			} catch (ServiceException e) {
				e.printStackTrace();
			} 
		} else {
			System.out.println("Not enough args");
		}
		System.out.println("errors:" + errorList);
		System.out.println("score:" + scoreMap);
		}
	
	private void createPlantReactomeIdMap(File idFile) {
		try (BufferedReader br = new BufferedReader(new FileReader(idFile))) {
		    String line = "";
//		    System.out.println(line);
				while ((line = br.readLine()) != null) {
//					System.out.println(line);
					String idName[] = line.split(":");
					System.out.println(idName[0] +"name"+ idName[1]);
					plantReactomePathways.put(WordUtils.capitalize(idName[1]),idName[0]);
				}
				System.out.println(plantReactomePathways.entrySet());
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		System.out.println(plantReactomePathways.size() + " pathways found.");
	}
	
	private void updateNtagPlantReactomePathways() {
			System.out.println("Trying to connect to WikiPathways ...");
			try {
				client.login(username, password);
			} catch (RemoteException e) {
				System.out
						.println("not able to use this user. check password and permission status.");
			}

			for (String name : pathwaysToUpload.keySet()) {
				System.out.println("uploadname "+ name);
				System.out.println(plantReactomePathways.containsKey(name));
				String wpId = plantReactomePathways.get(name);
				System.out.println("id"+wpId);
				Pathway p = pathwaysToUpload.get(WordUtils.capitalize(name));
				try {
						System.out.println("UPDATING\t" + name + "\t" + wpId);
						String revision = client.getPathway(wpId).getRevision();
						System.out.println("revision" + revision);
						client.updatePathway(wpId, p, comment,
								Integer.parseInt(revision));
						
						/*
						 * Check if under construction tag is to be applied depending on score
						 */
						Float score = scoreMap.get(name);
						if(score >= 25.0){
							WSPathwayInfo info = client.getPathwayInfo(wpId);
							client.saveCurationTag(info.getId(),
									"Curation:UnderConstruction", comment,
									Integer.parseInt(info.getRevision()));
							taggedPathways.add(info.getId() + ": " + name);
						}
						
						
					} catch (Exception e) {
						System.out
								.println("could not update pathways or curation tag for "
										+ wpId);
					}
				
			}
		System.out.println("Tagged pathways\t" + taggedPathways.size() + "\t"
					+ taggedPathways);
		}

	private boolean checkPathwayDir() {
		if (!pathwayDir.exists()) {
			System.out.println("directory does not exist\t"
					+ pathwayDir.getAbsolutePath());
			return false;
		}
		if (pathwayDir.isDirectory()) {
			for (File f : pathwayDir.listFiles()) {
				if (f.getName().endsWith(".gpml")) {
					return true;
				}
			}
		}
		System.out
				.println("Location is no directory or does not contain gpml files.\t"
						+ pathwayDir.getAbsolutePath());
		return false;
	}

	private void plantPwyMechanic() {
		for (File file : pathwayDir.listFiles()) {
			if (file.getName().endsWith(".gpml")) {
				try {
					Pathway pathway = new Pathway();
					totalInteractions = unconnectedInteractions = 0;
					pathway.readFromXml(file, false);
					String pathwayName = pathway.getMappInfo().getMapInfoName();
					System.out.println(pathwayName);
					pathwaysToUpload.put(pathwayName,
							pathway);
					/*
					 * Capitalise Title
					 */
					String title = WordUtils.capitalize(pathwayName);
					System.out.println(title);
					pathway.getMappInfo().setMapInfoName(title);
					
					/*
					 * Add version = 43
					 */
					pathway.getMappInfo().setVersion(PLANT_VERSION);
					/*
					 * Change Data Source on Pathway
					 */
					pathway.getMappInfo().setMapInfoDataSource(PLANT_DATA_SOURCE);
					
					/*
					 * Change data source on WikiPathways description
					 */
//					String commentToFix = pathway.getMappInfo().getComments().get(0).getComment();
//					String commentParts[]= commentToFix.split("Original Pathway at Reactome");
//					pathway.getMappInfo().getComments().clear();
//					pathway.getMappInfo().addComment(commentParts[0]+ plantReactomeURL, wikiSource);
					/*
					 * Remove 2nd data source
					 */
					String commentToFix = pathway.getMappInfo().getComments().get(0).getComment();
					commentToFix = commentToFix.replaceFirst("Source:\\[http://plantreactome.gramene.org/ Plant Reactome\\].", "");
					pathway.getMappInfo().getComments().clear();
					pathway.getMappInfo().addComment(commentToFix, wikiSource);
					
					/*
					 * Save Changes
					 */
					pathway.writeToXml(file, true);
					
					/*
					 * Calculate score and create map to store that
					 */
					String start = "";
					String end = "";
					for(PathwayElement pwe : pathway.getDataObjects()){
						if(pwe.getObjectType() == ObjectType.LINE){
							totalInteractions++;
							start = pwe.getStartGraphRef();
							end = pwe.getStartGraphRef();							
							if(start == null || end == null){
									unconnectedInteractions++;
							}
							
						}
					}
					System.out.println(totalInteractions);
					System.out.println(unconnectedInteractions);
					
					if(totalInteractions > 0){
						float score = (unconnectedInteractions/totalInteractions)*100;
						System.out.println(score);
						scoreMap.put(pathwayName, score);
					}
				} catch (ConverterException e) {
//					errorList.add(file.getAbsolutePath());
//					System.out.println("could not parse pathway from "
//							+ file.getAbsolutePath());
				}
	
			}
		}
	
	}
}
