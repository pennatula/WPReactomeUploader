package wpreactome;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.rpc.ServiceException;

import org.bridgedb.DataSource;
import org.pathvisio.core.model.ConverterException;
import org.pathvisio.core.model.ObjectType;
import org.pathvisio.core.model.Pathway;
import org.pathvisio.core.model.PathwayElement;
import org.pathvisio.core.view.MIMShapes;
import org.pathvisio.wikipathways.webservice.WSCurationTag;
import org.wikipathways.client.WikiPathwaysClient;


/**
 * Maps Wikipathways pathway ids for the corresponding Reactome Pathway IDs
 * Updates version
 * 
 * @author anwesha
 * 
 */
public class WPReactomeMap {
	private final static String organism = "Homo sapiens";
	private static List<String> errorList;
	private static Set<String> pathwayList;
	private static Map<String, String> reactomeIDList;
	private static Map<String, String> wpIDList;
	private static Map<String, String> reactomeWPIDMapper;
	private final File pathwayDir;

	private static String wikipathwaysURL = "http://wikipathways.org/wpi/webservice/webservice.php";
	private final WikiPathwaysClient client;

	public WPReactomeMap(File pathwayDir)
			throws MalformedURLException,
			ServiceException {
		client = new WikiPathwaysClient(new URL(wikipathwaysURL));
		pathwayList = new HashSet<String>();
		errorList = new ArrayList<String>();
		wpIDList = new HashMap<String, String>();
		reactomeIDList = new HashMap<String, String>();
		reactomeWPIDMapper = new HashMap<String, String>();
		this.pathwayDir = pathwayDir;
	}

	public static void main(String args[]) {
		if (args.length == 1) {

			String pathwayDir = args[0];

			try {
				WPReactomeMap wr = new WPReactomeMap(new File(pathwayDir));
				/*
				 * Making wpIDList
				 */
				if(wr.checkOrganism()) {
					System.out.println("Valid organism");
					wr.retrieveReactomePathways();
				}
				/*
				 * Making reactomeIDList
				 */
				if (wr.checkPathwayDir()) {
					System.out
							.println("Valid pathway directory.\nReading GPML files...");
					wr.readGpmlFiles();
				}
				/*
				 * Find WP Reactome pairs
				 */

				for (String wpName : reactomeIDList.keySet()) {
					reactomeWPIDMapper.put(reactomeIDList.get(wpName),
							wpIDList.get(wpName));
				}

				wr.reannotatePathwayNodes();
			} catch (MalformedURLException e) {
				e.printStackTrace();
			} catch (ServiceException e) {
				e.printStackTrace();
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		} else {
			System.out.println("Not enough args");
		}
		System.out.println("errors:" + errorList);
		System.out.println("pathway list = " + pathwayList);
		System.out.println("Reactome ID list \n" + reactomeIDList);
		System.out.println("WikiPathways ID list : " + wpIDList);
		System.out.println("Mapped IDs : " + reactomeWPIDMapper);

	}

	private boolean checkOrganism() throws RemoteException {
		String[] organisms = client.listOrganisms();
		if (Arrays.asList(organisms).contains(organism)) {
			return true;
		}
		return false;
	}

	private void retrieveReactomePathways() {
			System.out.println("Trying to connect to WikiPathways ...");

			WSCurationTag[] tags;
			try {
				tags = client.getCurationTagsByName("Curation:Reactome_Approved");
				for (WSCurationTag tag : tags) {
					if (tag.getPathway().getSpecies()
	.equals(organism)) {
					wpIDList.put(tag.getPathway().getName(), tag
								.getPathway().getId());
		
					}
				}
		
			} catch (RemoteException e) {
				System.out.println("remote problems !!!");
				e.printStackTrace();
			}
		
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

	private void readGpmlFiles() {
		for (File file : pathwayDir.listFiles()) {
			if (file.getName().endsWith(".gpml")) {
				try {
					Pathway pathway = new Pathway();
					pathway.readFromXml(file, true);

					for (PathwayElement pwe : pathway.getDataObjects()) {
						if (pwe.getObjectType() == ObjectType.DATANODE) {
							if (pwe.getDataNodeType().equalsIgnoreCase(
									"pathway")) {
								pathwayList.add(pathway.getMappInfo()
										.getMapInfoName());
								String pathwayName = pwe.getTextLabel();
								pathwayName = pathwayName.replaceAll("\n", " ");
								reactomeIDList.put(pathwayName,
										pwe.getElementID());
							}
						}
					}
				} catch (ConverterException e) {
					errorList.add(file.getName());
					System.out.println("could not parse pathway from "
							+ file.getAbsolutePath());
				}
	
			}
		}
	
	}

	private void reannotatePathwayNodes() {
		for (File file : pathwayDir.listFiles()) {
			if (file.getName().endsWith(".gpml")) {
				try {
					Pathway pathway = new Pathway();
					MIMShapes.registerShapes();
					pathway.readFromXml(file, true);
					for (PathwayElement pwe : pathway.getDataObjects()) {
						if (pwe.getObjectType() == ObjectType.DATANODE) {
							if (pwe.getDataNodeType().equalsIgnoreCase(
									"pathway")) {
								Set<String> reactomeKeys = reactomeWPIDMapper
										.keySet();
								for (String reactomeID : reactomeKeys) {
									if(pwe.getElementID().equalsIgnoreCase(reactomeID)) {
										try {
										pwe.setElementID(reactomeWPIDMapper
												.get(reactomeID));
								pwe.setDataSource(DataSource
										.getBySystemCode("Wp"));
										} catch (Exception e) {
											errorList.add(file.getName());
										}
								}
							}
								pathway.add(pwe);
						}
						
					}
					
					}
					pathway.writeToXml(file, true);
				} catch (ConverterException e) {
					System.out.println("could not parse pathway from "
							+ file.getAbsolutePath());
				}

			}
		}


	}

}