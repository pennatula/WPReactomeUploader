// Copyright 2018 WikiPathways
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package wpreactome;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.RemoteException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.jdom.Element;
import org.pathvisio.core.biopax.BiopaxElement;
import org.pathvisio.core.biopax.BiopaxNode;
import org.pathvisio.core.biopax.BiopaxProperty;
import org.pathvisio.core.model.ConverterException;
import org.pathvisio.core.model.GpmlFormat;
import org.pathvisio.core.model.Pathway;
import org.pathvisio.core.model.PathwayElement;
import org.pathvisio.core.view.MIMShapes;
import org.pathvisio.wikipathways.webservice.WSCurationTag;
import org.pathvisio.wikipathways.webservice.WSHistoryRow;
import org.pathvisio.wikipathways.webservice.WSOntologyTerm;
import org.pathvisio.wikipathways.webservice.WSPathway;
import org.pathvisio.wikipathways.webservice.WSPathwayHistory;
import org.pathvisio.wikipathways.webservice.WSPathwayInfo;
import org.wikipathways.client.WikiPathwaysClient;

/**
 * 
 * Main class uploaded newly converted Reactome pathways
 * to WikiPathways (updating existing pathways, uploading
 * new pathways, removing outdated pathways)
 * 
 * @author anwesha, desl, mkutmon
 *
 */
public class WpReactomeUploader {

	/**
	 * arguments:
	 * 1 = organism
	 * 2 = pathway directory containing converted Reactome pathway GPML files
	 * 3 = username used to upload pathways (should be ReactomeTeam)
	 * 4 = password
	 * 5 = update comment (shown in history - should contain Reactome version number)
	 * 6 = date of last reactome update (YYYYmmdd)
	 */
	public static void main (String [] args) {
		if(args.length == 6) {
			String org = args[0];
			String pathwayDir = args[1];
			String username = args[2];
			String password = args[3];
			String comment = args[4];
			String date = args[5];
			
			WpReactomeUploader uploader;
			try {
				uploader = new WpReactomeUploader(org, new File(pathwayDir), username, password, comment, date);
				if(uploader.checkPathwayDir() && uploader.checkOrganism()) {
					System.out.println("[INFO]\tValid settings:\n" + pathwayDir + "\t" + org);
					
					// retrieve current Reactome pathways from WikiPathways
					System.out.println("[INFO]\tGet pathways from WikiPathways");
					uploader.retrieveReactomePathways();
					
					// load pathways from local folder
					System.out.println("[INFO]\tGet Reactome pathways from local directory");
					uploader.readLocalReactomeFiles();
					
					// only change to true after extensive checking and validating!
					// First upload to RC branch, check again (with developers community).
					// Then upload and update in the WPs database
					// make sure appropriate user name and revision comment is set in run configuration!
					uploader.updatePathways(false);					
				} else {
					System.err.println("Invalid pathway directory or organism.");
				}
			} catch (MalformedURLException e) {
				System.err.println("Invalid webservice URL");
			} catch (RemoteException e) {
				System.err.println("Cannot retrieve data from webservice");
				e.printStackTrace();
			} catch (ParseException e) {
				System.err.println("Invalid last update date (format: YYYYmmdd");
			}  
		} else {
			System.err.println("Invalid argument set.");
		}
	}
	
	// reactome pathways on WikiPathways
	private Map<String, Pathway> wpPathways;
	private Map<String, String> react2Wp;
	
	// pathways that have curation changes since last update
	private Set<String> wpCurated;
	
	// new reactome pathways from local directory
	private Map<String, Pathway> newReactPathways;
	private Map<String, Pathway> metaPathways;

//	change to set wikiPathwaysURL to either live or the release candidate branch.
	private static String wikipathwaysURL = "http://rcbranch.wikipathways.org/wpi/webservice2.0"; 
//	private static String wikipathwaysURL = "http://webservice.wikipathways.org";

	private final WikiPathwaysClient client;
	private final String organism;
	private final File pathwayDir;
	private final String username;
	private final String password;
	private final String comment;
	
	private Date lastUpdate;
	
	private Boolean updateMode = false;

	public WpReactomeUploader(String organism, File pathwayDir, String username, String password, String comment, String date) throws MalformedURLException, ParseException {
		wpPathways = new HashMap<String, Pathway>();
		react2Wp = new HashMap<String, String>();
		newReactPathways = new HashMap<String, Pathway>();
		metaPathways = new HashMap<String, Pathway>();
		wpCurated = new HashSet<String>();
		
		client = new WikiPathwaysClient(new URL(wikipathwaysURL));
		this.organism = organism;
		this.pathwayDir = pathwayDir;
		this.username = username;
		this.password = password;
		this.comment = comment;
		SimpleDateFormat sdf = new SimpleDateFormat("yyyymmdd");
		lastUpdate = sdf.parse(date);
		
		MIMShapes.registerShapes();
	}
	
	/**
	 * Currently just printing stats
	 * TODO: implement update / upload / tag for removal
	 * @throws RemoteException 
	 */
	public void updatePathways(boolean uploadMode) throws RemoteException {
		updateMode = uploadMode;
		if(updateMode) {
			client.login(username, password);
		}
		
		// check which pathways are in online and local files to be overwritten
		Set<String> wpReactIds = react2Wp.keySet();
		Set<String> newReactIds = newReactPathways.keySet();
		
		// how many and which pathways need to be updated?
		Set<String> toUpdate = new HashSet<String>(wpReactIds); 
		toUpdate.retainAll(newReactIds);
		System.out.println("Update\t" + toUpdate.size() + "\t" + toUpdate);
		if(updateMode) {
			updateExistingPathways(toUpdate);
		}
		
		if(!updateMode) {
			// how many and which pathways have recent changes after release?
			// these pathways need to be checked after the update and changes need to 
			// be reintroduced if necessary
			System.out.println("Curation changes\t" + wpCurated.size() + "\t" + wpCurated);
		}
		
		// how many and which pathways need to be added to WP?
		Set<String> newPathways = new HashSet<String>(newReactIds);
		newPathways.removeAll(wpReactIds);
		System.out.println("New pathways\t" + newPathways.size() + "\t" + newPathways);
		if(updateMode) {
			uploadNewPathways(newPathways);
		}

		// how many and which pathways are deprecated/replaced/split and should be removed
		// need to be checked why they were not created by the converter!
		Set<String> removePathways = new HashSet<String>(wpReactIds); 
		removePathways.removeAll(newReactIds);
		System.out.print("Remove\t" + removePathways.size() + "\t");
		for(String s : removePathways) {
			System.out.print(react2Wp.get(s) + "\t");
		}
		System.out.println();
		
		// how many meta pathways will not be uploaded?
		System.out.println("Metapathways\t" + metaPathways.size() + "\t" + metaPathways.keySet());
		
		// are there any of the meta pathways on wikipathways?
		// additional check for cleanup of reactome collection on WP
		Set<String> removemetaPathways = new HashSet<String>(metaPathways.keySet()); 
		removemetaPathways.retainAll(removePathways);
		System.out.print("MetaPWs in Remove Set\t" + removemetaPathways.size() + "\t");
		for(String s : removemetaPathways) {
			System.out.print(react2Wp.get(s) + "\t");
		}
		System.out.println();

	}
	
	/**
	 * updates all existing pathways for the new reactome version
	 */
	private void updateExistingPathways(Set<String> pathways) {
		for(String reactId : pathways) {
			Pathway newP = newReactPathways.get(reactId);
			String wpId = react2Wp.get(reactId);
			try {
				WSPathwayInfo wspi = client.getPathwayInfo(wpId);
				addExistingOntTags(wpId, newP);
				client.updatePathway(wpId, newP, comment, Integer.parseInt(wspi.getRevision()));
				WSPathwayInfo wspi1 = client.getPathwayInfo(wpId);
				//deletes old Curation tag and Reapplies the tag to the new pathway
				client.removeCurationTag(wpId, "Curation:Reactome_Approved");
				client.saveCurationTag(wpId, "Curation:Reactome_Approved", comment, Integer.parseInt(wspi1.getRevision()));
				System.out.println("[INFO]\tPathway was updated " + wpId + " (" + newP.getMappInfo().getMapInfoName() + ")");
			} catch (RemoteException | ConverterException e) {
				System.err.println("Could not update pathway " + wpId);
			}
		}
			
	}
	
	/**
	 * retrieves the ontology tags of the current WP version
	 * of the pathway and adds it to the new GPML pathway version
	 */
	@SuppressWarnings("deprecation")
	private void addExistingOntTags(String id, Pathway p) throws RemoteException {
		WSOntologyTerm [] terms = client.getOntologyTermsByPathway(id);
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
	}
	
	/**
	 * Uploads all new pathways to WikiPathways
	 */
	private void uploadNewPathways(Set<String> pathways) {
		for(String reactId : pathways) {
			Pathway p = newReactPathways.get(reactId);
			try {
				WSPathwayInfo wspi = client.createPathway(p);
				client.saveCurationTag(wspi.getId(), "Curation:Reactome_Approved", comment, Integer.parseInt(wspi.getRevision()));	
				System.out.println("[INFO]\tNew pathway added " + wspi.getId() + " (" + wspi.getName() + ")");
			} catch (RemoteException | ConverterException e) {
				System.err.println("Could not upload new Reactome pathway " + p.getMappInfo().getMapInfoName() + " (" + reactId + ")");
			}
		}
	}

	/**
	 * Reads all local GPML files of newly converted
	 * Reactome pathways - need to be created with
	 * Reactome2GPML converter
	 */
	public void readLocalReactomeFiles() {
		for(File file : pathwayDir.listFiles()) {
			try {
				Pathway pathway = new Pathway();
				pathway.readFromXml(file, false);
					
				String reactId = pathway.getMappInfo().getDynamicProperty("reactome_id");
				if(reactId.equals("")) {
					System.err.println("Reactome pathway without valid Comment!!! " + file.getName());
					System.exit(0);
				}
				
				// check if pathway is meta pathway
				if(!isMetaPathway(pathway)) {			
					newReactPathways.put(reactId, pathway);
				} else {
					metaPathways.put(reactId, pathway);
				}
			} catch (ConverterException e) {
				System.err.println("Parsing error: " + file.getName());
			}
		}
		System.out.println("[INFO]: Loaded " + newReactPathways.size() + " valid Reactome pathways from file out of " + pathwayDir.listFiles().length);
		System.out.println(newReactPathways);
	}
	
	/**
	 * checks if pathway contains actual pathway elements
	 * or only pathway nodes = meta pathway
	 */
	private boolean isMetaPathway(Pathway p) {
		for(PathwayElement dn : p.getDataObjects()){
			if(dn.getDataNodeType().equals("Protein") ||
				dn.getDataNodeType().equals("Metabolite") ||
				dn.getDataNodeType().equals("Complex") ||
				dn.getDataNodeType().equals("GeneProduct") ||
				dn.getDataNodeType().equals("RNA")) {
				return false;
			}
		}
		return true;
	}
	
	/**
	 * use webservice to retrieve current Reactome pathways
	 */
	public void retrieveReactomePathways() throws RemoteException {
		WSCurationTag [] tags = client.getCurationTagsByName("Curation:Reactome_Approved");
		for(WSCurationTag tag : tags) {
			if(tag.getPathway().getSpecies().equals(organism)) {
				try {
					WSPathway wsp = client.getPathway(tag.getPathway().getId());
					
					if(!updateMode) {
						// check if last changes on the pathways were made by the last release
						WSPathwayHistory h = client.getPathwayHistory(wsp.getId(), lastUpdate);
						if(h.getHistory().length > 0) {
							WSHistoryRow last = h.getHistory(h.getHistory().length-1);
							if(!last.getComment().contains("reactome version") 
									&& !last.getComment().contains("New pathway") 
									&& !last.getComment().contains("Reactome release")) {
								wpCurated.add(wsp.getId());
							}
						}
					}
					
					Pathway p = WikiPathwaysClient.toPathway(wsp);
					
					String reactId = p.getMappInfo().getDynamicProperty("reactome_id");
					if(reactId.equals("")) {
						System.err.println("Reactome pathway without valid Attribute Key (should contain original Reactome pathway ID)!!! " + tag.getPathway().getId());
						System.exit(0);
					}
					wpPathways.put(tag.getPathway().getId(), p);
					react2Wp.put(reactId, tag.getPathway().getId());
				} catch (ConverterException e) {
					e.printStackTrace();
					System.err.println("Parsing error: " + tag.getPathway().getId());
				}
			}
		}
		System.out.println("[INFO]\t" + wpPathways.size() + " pathways loaded from WikiPathways.");
		System.out.println(react2Wp);
		System.out.println(wpPathways);
	}
	
	/**
	 * checks if organism is annotated
	 */
	private boolean checkOrganism() throws RemoteException {
		String [] organisms = client.listOrganisms();
		if(Arrays.asList(organisms).contains(organism)) {
			return true;
		}
		return false;
	}
	
	/**
	 * checks if pathway directory exists and contains
	 * GPML files
	 */
	private boolean checkPathwayDir() {
		if(!pathwayDir.exists()) {
			return false;
		} 
		if(pathwayDir.isDirectory()) {
			for(File f : pathwayDir.listFiles()) {
				if(f.getName().endsWith(".gpml")) {
					return true;
				}
			}
		}
		return false;
	}
	
//	//checks pathways to be replaced
//		public void replacePathways() {
//			try {
//				client.login(username, password);
//			} catch (RemoteException e) {
//				System.out.println("not able to use this user. check password and permission status.\n");
//			}
//			
//			//checks pathway names
//			for(String name : pathwaysToUpload.keySet()) {
//				Pathway p = pathwaysToUpload.get(name);
////				p.getMappInfo().addComment("Pathway is converted from Reactome id: ", "Reactome Converter");
//				if(reactomePathways.containsKey(name)) {
//					String id = reactomePathways.get(name).getId();
//					try{
//						System.out.println("UPDATING\t" + name + "\t" + id + "\n");
//						String revision = reactomePathways.get(name).getRevision();
//						System.out.println("revision"+revision + "\n");
//
//						WSPathway wpPathway = client.getPathway(id);
//						WSOntologyTerm [] terms = client.getOntologyTermsByPathway(wpPathway.getId());
//						
//						BiopaxElement bp = p.getBiopax();
//						
//						//retrieving ontology tags from WP without overwriting them with nothing
//						for (WSOntologyTerm o : terms){
//							BiopaxNode bn = new BiopaxNode();
//							Element el = new Element("TERM");
//							el.setText(o.getName());
//							Element el1 = new Element("ID");
//							el1.setText(o.getId());
//							Element el2 = new Element("Ontology");
//							el2.setText(o.getOntology());
//							bn.addProperty(new BiopaxProperty(el));
//							bn.addProperty(new BiopaxProperty(el1));
//							bn.addProperty(new BiopaxProperty(el2));
//							bp.addElement(bn);
//							Element wrapped = bn.getWrapped();
//							wrapped.setName("openControlledVocabulary");
//							wrapped.removeAttribute("id", GpmlFormat.RDF);
//						}
//						
//						//comment below writes to XML file for testing
////						p.writeToXml(new File("test"+info.getId()+".gpml"),true );
//
//						client.updatePathway(id, p, comment, Integer.parseInt(revision));
//						WSPathwayInfo newPwy = client.getPathwayInfo(id);
//						//deletes old curation tag and reapplies the tag to the new pathway
//						client.removeCurationTag(id, "Curation:Reactome_Approved");
//						client.saveCurationTag(id, "Curation:Reactome_Approved", comment, Integer.parseInt(newPwy.getRevision()));
//						updatedPathways.add(id + ": " + name);
//						
//					} catch(Exception e) {
//						System.out.println("could not update pathways or curation tag for " + id + "\n");
//					}
//				} else {
//					try {
//						WSPathwayInfo info = client.createPathway(p);
//						client.saveCurationTag(info.getId(), "Curation:Reactome_Approved", comment, Integer.parseInt(info.getRevision()));
//						newPathways.add(info.getId() + ": " + info.getName());
//						System.out.println("NEW PATHWAY\t" + name + "\t" + "\n" /* + info.getId()*/);
//					} catch(Exception e) {
//						System.out.println("could not upload new pathway " + name + "\n");
//					}
//				}
//			}
//			
////			for(String name : reactomePathways.keySet()) {
////				if(!pathwaysToUpload.containsKey(name)) {
////					deletedPathways.add(reactomePathways.get(name).getId() + ": " + name);
////				}
////			}
//			System.out.println("Wikipathways ID list" + wpIDList + "\n");
//			System.out.println("Updated pathways\t" + updatedPathways.size() + "\t" + updatedPathways + "\n");
//			System.out.println("New pathways\t" + newPathways.size() + "\t" + newPathways + "\n");
//			System.out.println("Deleted pathways\t" + deletedPathways.size() + "\t" + deletedPathways + "\n");
//		}
}
