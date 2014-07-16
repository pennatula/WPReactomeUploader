/**
 * 
 */
package wpreactome;

import java.io.File;
import java.util.ArrayList;

import org.pathvisio.core.model.ConverterException;
import org.pathvisio.core.model.Pathway;
import org.pathvisio.core.model.PathwayElement;
import org.pathvisio.core.view.MIMShapes;

/**
 * Scripts to fix small errors on Reactome pathways
 * 
 * @author anwesha
 * 
 */
public class PrettyWpReactome {
	private final File pathwayDir;
	private final static String reactomeURL = "http://www.reactome.org/PathwayBrowser/#DB=gk_current&FOCUS_SPECIES_ID=48887&FOCUS_PATHWAY_ID=";
	private final static String wikiSource = "WikiPathways-description";
	static ArrayList<String> noMainComment;
	static ArrayList<String> notWikiPathways;
	static ArrayList<String> hasMainComment;

	public PrettyWpReactome(File pathwayDir) {
		this.pathwayDir = pathwayDir;
	}

	/**
	 * @param args
	 * @throws ConverterException
	 */
	public static void main(String[] args) throws ConverterException {
		noMainComment = new ArrayList<String>();
		notWikiPathways = new ArrayList<String>();
		hasMainComment = new ArrayList<String>();
		if (args.length == 1) {
			String pathwayDir = args[0];

			PrettyWpReactome pwp = new PrettyWpReactome(new File(pathwayDir));
			if (pwp.checkPathwayDir()) {
				System.out
						.println("Valid pathway directory.\nReading GPML files...");
				// pwp.readGpmlFiles();
				pwp.checkWikiMain();
			}

		} else {
			System.out.println("Not enough args");
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

	private Boolean countReactomeURLs(String comment) {
		Boolean answer = false;
		if (comment.contains(reactomeURL)) {
			answer = true;
		}
		return answer;
	}
	private void checkWikiMain() throws ConverterException {
		for (File file : pathwayDir.listFiles()) {
				Pathway pathway = new Pathway();
				MIMShapes.registerShapes();
				pathway.readFromXml(file, true);
			/*
			 * Check if Main Comment is set
			 */
			if (pathway.getMappInfo().getComments().size() > 0) {
				/*
				 * Check if Comment Source is correct
				 */
				if (pathway.getMappInfo().getComments().get(0).getSource()
						.equalsIgnoreCase("WikiPathways-description")) {
					
					/*
					 * Check if URL is correct and present once bug fix : for
					 * URL getting saved twice
					 */
					String comment = pathway.getMappInfo().getComments().get(0).getComment();
					int present = 0;
					while (comment.contains(reactomeURL)) {
						present ++;
						comment = comment.replaceFirst(reactomeURL, "");
					}
					if (present > 1) {
						int start = pathway.getMappInfo().getComments().get(0)
								.getComment().indexOf(reactomeURL, 0);
						String newcomment = pathway.getMappInfo().getComments().get(0)
						.getComment().substring(0, start);
						PathwayElement pwecom = pathway.getMappInfo();
						pwecom.getComments().clear();
						pwecom
								.addComment(newcomment, wikiSource);
						pathway.add(pwecom);
						pathway.writeToXml(file, true);
					}
					if (present == 0) {
						String newcomment = pathway.getMappInfo().getComments()
								.get(0).getComment();
						PathwayElement pwecom = pathway.getMappInfo();
						pwecom.getComments().clear();
						pwecom.addComment(
								newcomment
										+ reactomeURL
										+ pathway.getMappInfo()
												.getDynamicProperty(
														"reactome_id"),
								wikiSource);
						pathway.add(pwecom);
						pathway.writeToXml(file, true);
					}
					hasMainComment.add(pathway.getMappInfo().getMapInfoName()
							+ "\t" + String.valueOf(present)
							);
				} else {
					notWikiPathways.add(pathway.getMappInfo().getMapInfoName());
				}
			} else {
				noMainComment.add(pathway.getMappInfo().getMapInfoName());
				PathwayElement pwecom = pathway.getMappInfo();
				pwecom.getComments().clear();
				pwecom.addComment(
						"Original Pathway at Reactome: "
								+ reactomeURL
								+ pathway.getMappInfo().getDynamicProperty(
										"reactome_id"), wikiSource);
				pathway.add(pwecom);
				pathway.writeToXml(file, true);
			}

	}
		System.out.println("WP Main Comment \n : " + hasMainComment);
		System.out.println("Not WP Main Comment \n : " + notWikiPathways);
		System.out.println("No Main Comment \n : " + noMainComment);
	}

}
