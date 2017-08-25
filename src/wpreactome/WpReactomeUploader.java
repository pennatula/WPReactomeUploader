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

import org.jdom.Element;
import org.pathvisio.core.biopax.BiopaxElement;
import org.pathvisio.core.biopax.BiopaxNode;
import org.pathvisio.core.biopax.BiopaxProperty;
import org.pathvisio.core.model.ConverterException;
import org.pathvisio.core.model.GpmlFormat;
import org.pathvisio.core.model.OntologyTag;
import org.pathvisio.core.model.Pathway;
import org.pathvisio.core.model.PathwayElement;
import org.pathvisio.wikipathways.webservice.WSCurationTag;
import org.pathvisio.wikipathways.webservice.WSOntologyTerm;
import org.pathvisio.wikipathways.webservice.WSPathway;
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
	 * @throws MalformedURLException 
	 * @throws ConverterException 
	 * @throws RemoteException 
	 */
	public static void main (String [] args) throws MalformedURLException, RemoteException, ConverterException {
		System.out.println(args.length + "\n");
//		if(args.length == 4) {
			//required arguments for the uploader script
			String organism = "Homo sapiens";
			String pathwayDir = args[0];
			String username = args[1];
			String password = args[2];
			String comment = args[3];
									
			WpReactomeUploader uploader;

//			WikiPathwaysClient client = new WikiPathwaysClient(new URL(wikipathwaysURL));
			
//			System.out.println(wrapped.getAttributeValue("id"));
						
			
//			newPath.writeToXml(new File("/home/ryan/test.xml"),true );
					

			
			
			try {
				uploader = new WpReactomeUploader(organism, new File(pathwayDir), username, password, comment);
				try {
					wpIDList.clear();
					// System.out.println("reaches");
					if(uploader.checkPathwayDir() && uploader.checkOrganism()) {
						// System.out.println("pathway directory and organism are valid.\nretrieve pathways...");
						 uploader.retrieveReactomePathways();
						// // System.out.println(reactomePathwaysIDList);
						// // System.out.println(reactomePathwaysIDList.size());
						 uploader.readGpmlFiles();
						 uploader.replacePathways();
						// uploader.replacePathways();
						/*
						 * Script to replace pathways when WPID is known
						 */
						// File file = new File(
						// "/home/anwesha/Plant_Primary_Metabolism_WP2499r75849.gpml");
						// String wpid = "WP2499";
//						File newReactomeFile = new File(
//								"/home/martina/workspace/pathvisio/Abacavir transport and metabolism.gpml");
//						String wpid = "WP4";
//						uploader.replacePathwaysbyWPID(newReactomeFile, wpid,
//								true);
//						uploader.findMetapathways();
					}
				} catch (Exception e) {
					System.out.println("could not retrieve pathways from wikipathways\n" + e.getMessage()+ "\n");
				}
			} catch (Exception e1) {
				e1.printStackTrace();
				System.out.println("cannot connect to WP\t" + e1.getMessage()+ "\n");
			} 
			
//		} else {
//			System.out.println("please provide organism name, pathway directory, username, password, update comment.");
//		}
	}

//	change to set wikiPathwaysURL to either live or the release candidate branch.
	private static String wikipathwaysURL = "http://rcbranch.wikipathways.org/wpi/webservicetest"; 
//	private static String wikipathwaysURL = "http://webservice.wikipathways.org";

	private final Map<String, WSPathwayInfo> reactomePathways;
	private static Map<String, String> wpIDList;
	private final WikiPathwaysClient client;
	private final String organism;
	private final File pathwayDir;
	private final Map<String, Pathway> pathwaysToUpload;
	private final Map<String, String> wpReactomeIdMapper;
	
	private final String username;
	private final String password;
	private final String comment;
//	int i = 2;
	private final List<String> updatedPathways;
	private final List<String> newPathways;
	private final List<String> deletedPathways;
	private List<OntologyTag> ontologyTags;

	public WpReactomeUploader(String organism, File pathwayDir, String username, String password, String comment) throws MalformedURLException {
		reactomePathways = new HashMap<String, WSPathwayInfo>();
		wpIDList = new HashMap<String, String>();
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
			System.out.println("directory does not exist\t" + pathwayDir.getAbsolutePath()+ "\n");
			return false;
		} 
		if(pathwayDir.isDirectory()) {
			for(File f : pathwayDir.listFiles()) {
				if(f.getName().endsWith(".gpml")) {
					return true;
				}
			}
		}
		System.out.println("Location is no directory or does not contain gpml files.\t" + pathwayDir.getAbsolutePath()+ "\n");
		return false;
	}
	
	//checks pathways to be replaced
	public void replacePathways() {
		try {
			client.login(username, password);
		} catch (RemoteException e) {
			System.out.println("not able to use this user. check password and permission status.\n");
		}
		
		//checks pathway names
		for(String name : pathwaysToUpload.keySet()) {
			Pathway p = pathwaysToUpload.get(name);
//			p.getMappInfo().addComment("Pathway is converted from Reactome id: ", "Reactome Converter");
			if(reactomePathways.containsKey(name)) {
				String id = reactomePathways.get(name).getId();
				try{
					System.out.println("UPDATING\t" + name + "\t" + id + "\n");
					String revision = reactomePathways.get(name).getRevision();
					System.out.println("revision"+revision + "\n");

					WSPathway wpPathway = client.getPathway(id);
					WSOntologyTerm [] terms = client.getOntologyTermsByPathway(wpPathway.getId());
					
					BiopaxElement bp = p.getBiopax();
					
					//retrieving ontology tags from WP without overwriting them with nothing
					for (WSOntologyTerm o : terms){
						BiopaxNode bn = new BiopaxNode();
						Element el = new Element("TERM");
						el.setText(o.getName());
						Element el1 = new Element("ID");
						el1.setText(o.getId());
						Element el2 = new Element("Ontology");
						el2.setText(o.getOntology());
						bn.addProperty(new BiopaxProperty(el));
						bn.addProperty(new BiopaxProperty(el1));
						bn.addProperty(new BiopaxProperty(el2));
						bp.addElement(bn);
						Element wrapped = bn.getWrapped();
						wrapped.setName("openControlledVocabulary");
						wrapped.removeAttribute("id", GpmlFormat.RDF);
					}
					
					//comment below writes to XML file for testing
//					p.writeToXml(new File("test"+info.getId()+".gpml"),true );

					client.updatePathway(id, p, comment, Integer.parseInt(revision));
					WSPathwayInfo newPwy = client.getPathwayInfo(id);
					//deletes old curation tag and reapplies the tag to the new pathway
					client.removeCurationTag(id, "Curation:Reactome_Approved");
					client.saveCurationTag(id, "Curation:Reactome_Approved", comment, Integer.parseInt(newPwy.getRevision()));
					updatedPathways.add(id + ": " + name);
					
				} catch(Exception e) {
					System.out.println("could not update pathways or curation tag for " + id + "\n");
				}
			} else {
				try {
					WSPathwayInfo info = client.createPathway(p);
					client.saveCurationTag(info.getId(), "Curation:Reactome_Approved", comment, Integer.parseInt(info.getRevision()));
					newPathways.add(info.getId() + ": " + info.getName());
					System.out.println("NEW PATHWAY\t" + name + "\t" + "\n" /* + info.getId()*/);
				} catch(Exception e) {
					System.out.println("could not upload new pathway " + name + "\n");
				}
			}
		}
		
//		for(String name : reactomePathways.keySet()) {
//			if(!pathwaysToUpload.containsKey(name)) {
//				deletedPathways.add(reactomePathways.get(name).getId() + ": " + name);
//			}
//		}
		System.out.println("Wikipathways ID list" + wpIDList + "\n");
		System.out.println("Updated pathways\t" + updatedPathways.size() + "\t" + updatedPathways + "\n");
		System.out.println("New pathways\t" + newPathways.size() + "\t" + newPathways + "\n");
		System.out.println("Deleted pathways\t" + deletedPathways.size() + "\t" + deletedPathways + "\n");
	}

	 
	//read in the GPML files
	public void readGpmlFiles() {
		for(File file : pathwayDir.listFiles()) {
				try {
					Pathway pathway = new Pathway();
					pathway.readFromXml(file, true);
					int flag = 0;
					for( PathwayElement dn : pathway.getDataObjects()){
						

						
						if ( (dn.getDataNodeType().equals("Unknown")) ||							
						(dn.getDataNodeType().equals("Pathway")))
							flag++;
					}

					if (flag!=pathway.getDataObjects().size()){
//						System.out.println("meta pwy:	" + pathway.getSourceFile());
						pathwaysToUpload.put(pathway.getMappInfo()
									.getMapInfoName(), pathway);

//						System.out.println(pathway.getSourceFile());
					}
			} catch (ConverterException e) {
					System.out.println("could not parse pathway from " + file.getAbsolutePath() + "\n");
				}
		}
	}
	
	//sets curation tag to to 'Reactome_Approved'
	public void retrieveReactomePathways() throws Exception {
		WSCurationTag [] tags = client.getCurationTagsByName("Curation:Reactome_Approved");
		for(WSCurationTag tag : tags) {
			if(tag.getPathway().getSpecies().equals(organism)) {
				System.out.println(tag.getPathway().getName() + "\n");
				reactomePathways.put(tag.getPathway().getName(), tag.getPathway());
				wpIDList.put(tag.getPathway().getName(), tag
						.getPathway().getId());

			}
		}
		System.out.println(reactomePathways.size() + " pathways found.\n");
	}
	
	private boolean checkOrganism() throws RemoteException {
		String [] organisms = client.listOrganisms();
		System.out.println(Arrays.asList(organisms) + "\n");
		if(Arrays.asList(organisms).contains(organism)) {
			return true;
		}
		return false;
	}
}
