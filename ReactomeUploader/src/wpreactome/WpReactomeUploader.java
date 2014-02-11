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
		if(args.length == 5) {
			String organism = args[0];
			String pathwayDir = args[1];
			String username = args[2];
			String password = args[3];
			String comment = args[4];
			WpReactomeUploader uploader;
			try {
				uploader = new WpReactomeUploader(organism, new File(pathwayDir), username, password, comment);
				try {
					
					if(uploader.checkPathwayDir() && uploader.checkOrganism()) {
						System.out.println("pathway directory and organism are valid.\nretrieve pathways...");
						uploader.retrieveReatomePathways();
						uploader.readGpmlFiles();
						uploader.replacePathways();
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

	private static String wikipathwaysURL = "http://test2.wikipathways.org/wpi/webservice/webservice.php"; 
	private Map<String, WSPathwayInfo> reactomePathways;
	private WikiPathwaysClient client;
	private String organism;
	private File pathwayDir;
	private Map<String, Pathway> pathwaysToUpload;
	
	private String username;
	private String password;
	private String comment;
	
	public WpReactomeUploader(String organism, File pathwayDir, String username, String password, String comment) throws MalformedURLException, ServiceException {
		reactomePathways = new HashMap<String, WSPathwayInfo>();
		client = new WikiPathwaysClient(new URL(wikipathwaysURL));
		this.organism = organism;
		this.pathwayDir = pathwayDir;
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
	
	private List<String> updatedPathways;
	private List<String> newPathways;
	private List<String> deletedPathways;
	
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
					client.updatePathway(id, p, comment, Integer.parseInt(revision));
					WSPathwayInfo info = client.getPathwayInfo(id);
					client.saveCurationTag(id, "Curation:Reactome_Approved", comment, Integer.parseInt(info.getId()));
					updatedPathways.add(id + ": " + name);
				} catch(Exception e) {
					System.out.println("could not update pathways or curation tag for WP" + id);
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
	
	public void retrieveReatomePathways() throws Exception {
		WSCurationTag [] tags = client.getCurationTagsByName("Curation:Reactome_Approved");
		for(WSCurationTag tag : tags) {
			if(tag.getPathway().getSpecies().equals(organism)) {
				System.out.println(tag.getPathway().getName());
				reactomePathways.put(tag.getPathway().getName(), tag.getPathway());
			}
		}
		System.out.println(reactomePathways.size() + " pathways found.");
	}
	
	private boolean checkOrganism() throws RemoteException {
		String [] organisms = client.listOrganisms();
		if(Arrays.asList(organisms).contains(organism)) {
			return true;
		}
		return false;
	}
}
