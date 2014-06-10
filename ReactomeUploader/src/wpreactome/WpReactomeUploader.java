package wpreactome;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.rpc.ServiceException;

import org.pathvisio.core.model.ConverterException;
import org.pathvisio.core.model.Pathway;
import org.pathvisio.core.model.PathwayElement;
import org.pathvisio.wikipathways.webservice.WSCurationTag;
import org.pathvisio.wikipathways.webservice.WSPathwayInfo;
import org.wikipathways.client.WikiPathwaysClient;

public class WpReactomeUploader {

	/**
	 * arguments:
	 * 1 = organism
	 * 2 = pathway directory
	 * 3 = username
	 * 4 = password
	 * 5 = update comment
	 * @param args
	 */
	public static void main (String [] args) {
		System.out.println(args.length);
		if(args.length == 4) {
			String organism = "Homo sapiens";
			String pathwayDir = args[0];
			String username = args[1];
			String password = args[2];
			String comment = args[3];
									
			WpReactomeUploader uploader;
			try {
				uploader = new WpReactomeUploader(organism, new File(pathwayDir), username, password, comment);
				try {
					// System.out.println("reaches");
					if(uploader.checkPathwayDir() && uploader.checkOrganism()) {
						System.out.println("pathway directory and organism are valid.\nretrieve pathways...");
						uploader.retrieveReactomePathways();
						System.out.println(reactomePathwaysIDList);
						System.out.println(reactomePathwaysIDList.size());
						uploader.readGpmlFiles();
						uploader.replacePathways();

//						uploader.findMetapathways();
					}
				} catch (Exception e) {
					System.out.println("could not retrieve pathways from wikipathways\n" + e.getMessage());
				}
			} catch (Exception e1) {
				e1.printStackTrace();
				System.out.println("cannot connect to WP\t" + e1.getMessage());
			} 
			
		} else {
			System.out.println("please provide organism name, pathway directory, username, password, update comment.");
		}
	}

//	private static String wikipathwaysURL = "http://test2.wikipathways.org/wpi/webservice/webservice.php"; 
	private static String wikipathwaysURL = "http://wikipathways.org/wpi/webservice/webservice.php";
	private final Map<String, WSPathwayInfo> reactomePathways;
	private static Map<String, String> reactomePathwaysIDList;
	private final WikiPathwaysClient client;
	private final String organism;
	private final File pathwayDir;
	private final Map<String, Pathway> pathwaysToUpload;
	private final Map<String, String> wpReactomeIdMapper;
	
	private final String username;
	private final String password;
	private final String comment;
	
	private final List<String> updatedPathways;
	private final List<String> newPathways;
	private final List<String> deletedPathways;

	public WpReactomeUploader(String organism, File pathwayDir, String username, String password, String comment) throws MalformedURLException, ServiceException {
		reactomePathways = new HashMap<String, WSPathwayInfo>();
		reactomePathwaysIDList = new HashMap<String, String>();
		client = new WikiPathwaysClient(new URL(wikipathwaysURL));
		this.organism = organism;
		this.pathwayDir = pathwayDir;
		wpReactomeIdMapper = new HashMap<String, String>();
		pathwaysToUpload = new HashMap<String, Pathway>();
		updatedPathways = new ArrayList<String>();
		newPathways = new ArrayList<String>();
		deletedPathways = new ArrayList<String>();
		this.username = username;
		this.password = password;
		this.comment = comment;
	}
	
	public boolean checkPathwayDir() {
		if(!pathwayDir.exists()) {
			System.out.println("directory does not exist\t" + pathwayDir.getAbsolutePath());
			return false;
		} 
		if(pathwayDir.isDirectory()) {
			for(File f : pathwayDir.listFiles()) {
				if(f.getName().endsWith(".gpml")) {
					return true;
				}
			}
		}
		System.out.println("Location is no directory or does not contain gpml files.\t" + pathwayDir.getAbsolutePath());
		return false;
	}
	
	public void replacePathways() {
		// TODO: filter out meta-pathways!
		try {
			client.login(username, password);
		} catch (RemoteException e) {
			System.out.println("not able to use this user. check password and permission status.");
		}

		for(String name : pathwaysToUpload.keySet()) {
			Pathway p = pathwaysToUpload.get(name);
			if(reactomePathways.containsKey(name)) {
				String id = reactomePathways.get(name).getId();
				try{
					System.out.println("UPDATING\t" + name + "\t" + id);
					String revision = reactomePathways.get(name).getRevision();
					System.out.println("revision"+revision);
					client.updatePathway(id, p, comment, Integer.parseInt(revision));
					WSPathwayInfo info = client.getPathwayInfo(id);
					client.saveCurationTag(info.getId(), "Curation:Reactome_Approved", comment, Integer.parseInt(info.getRevision()));
					updatedPathways.add(info.getId() + ": " + name);
				} catch(Exception e) {
					System.out.println("could not update pathways or curation tag for " + id);
				}
			} else {
				try {
					WSPathwayInfo info = client.createPathway(p);
					client.saveCurationTag(info.getId(), "Curation:Reactome_Approved", comment, Integer.parseInt(info.getRevision()));
					newPathways.add(info.getId() + ": " + info.getName());
					System.out.println("NEW PATHWAY\t" + name + "\t" + info.getId());
				} catch(Exception e) {
					System.out.println("could not upload new pathway " + name);
				}
			}
		}
		
		for(String name : reactomePathways.keySet()) {
			if(!pathwaysToUpload.containsKey(name)) {
				deletedPathways.add(reactomePathways.get(name).getId() + ": " + name);
			}
		}
		
		System.out.println("Updated pathways\t" + updatedPathways.size() + "\t" + updatedPathways);
		System.out.println("New pathways\t" + newPathways.size() + "\t" + newPathways);
		System.out.println("Deleted pathways\t" + deletedPathways.size() + "\t" + deletedPathways);
	}
	
	public void readGpmlFiles() {
		for(File file : pathwayDir.listFiles()) {
			if(file.getName().endsWith(".gpml")) {
				try {
					Pathway pathway = new Pathway();
					pathway.readFromXml(file, true);
					/*
					 * Fixing comment source for displaying WikiPathways description
					 */
					// if (!pathway.getMappInfo().getComments().isEmpty()){
					// if(pathway.getMappInfo().getComments().get(0).getSource().equalsIgnoreCase("Wikipathways-description")){
					// System.out.println("changing p to P ...");
					// pathway.getMappInfo().getComments().get(0).setSource("WikiPathways-description");
					// pathway.writeToXml(file, true);
					// }
					// }
					if(pathway.getMappInfo().getOrganism().equals(organism)) {
						pathwaysToUpload.put(pathway.getMappInfo().getMapInfoName(), pathway);
					} else {
						System.out.println("pathway does not have correct species\t" + file.getAbsolutePath());
					}
				} catch (ConverterException e) {
					System.out.println("could not parse pathway from " + file.getAbsolutePath());
				}
				
			}
		}
	}
	
	public void findMetapathways() {
		for(File file : pathwayDir.listFiles()) {
			if(file.getName().endsWith(".gpml")) {
				try {
					Pathway pathway = new Pathway();
					pathway.readFromXml(file, true);
					/*
					 * Find metapathways
					 */
					 List<PathwayElement> pathwayElements = pathway.getDataObjects();
					 for(PathwayElement pathwayElement : pathwayElements){
						 if (pathwayElement.getDataNodeType().equalsIgnoreCase("Pathway")){
							 pathwayElement.getElementID();
							 System.out.println(file.getName());
						 }
					 }
				} catch (ConverterException e) {
					System.out.println("could not parse pathway from " + file.getAbsolutePath());
				}
				
			}
		}
	}
	
	public void retrieveReactomePathways() throws Exception {
		WSCurationTag [] tags = client.getCurationTagsByName("Curation:Reactome_Approved");
		for(WSCurationTag tag : tags) {
			if(tag.getPathway().getSpecies().equals(organism)) {
				System.out.println(tag.getPathway().getName());
				reactomePathways.put(tag.getPathway().getName(), tag.getPathway());
				reactomePathwaysIDList.put(tag.getPathway().getName(), tag
						.getPathway().getId());

			}
		}
		System.out.println(reactomePathways.size() + " pathways found.");
	}
	
	private boolean checkOrganism() throws RemoteException {
		String [] organisms = client.listOrganisms();
		System.out.println(Arrays.asList(organisms));
		if(Arrays.asList(organisms).contains(organism)) {
			return true;
		}
		return false;
	}
}
