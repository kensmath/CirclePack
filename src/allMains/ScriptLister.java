package allMains;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Scanner;

/**
 * This standalone code generates 'ScriptList.html' in the
 * directory specified by the argument to the call. it is
 * run via 'ScriptLister.jar'.
 * 
 * The resulting html file lists all its CirclePack *.cps 
 * (or deprecated *.xmd, *.cmd) script files. When loaded in 
 * the CirclePack 'WWW' window, this html file provides 
 * info on each script and links that load then into the
 * CirclePack script window.
 * 
 * In the html file there is a <table>...</table> entry
 * for each script. It formats 4 pieces of data from the 
 * XML-encoded script file:
 * + Script Name, entered as html comment preceding the table
 * + Script header text description (if it exists)
 * + Script ABOUT_IMAGE: (if it exists)
 * + link url (if it exists; default to current directory)
 * Note that the link may have to be adjusted depending on
 * where the script is stored.
 * 
 * @author kensm, February 2023
 *
 */
public class ScriptLister {

	public File CurrentDirectory=new File(System.getProperty("user.dir"));

	StringBuilder header;
	StringBuilder footer;
	StringBuilder htmlContents;
	ArrayList<StringBuilder> tables;
	
	// constructor
	public ScriptLister(String dir_name) {
		
		CurrentDirectory=new File(dir_name);
		
		File[] paths=CurrentDirectory.listFiles();
		
		int n=paths.length;
		
		if (n>0) {
			ArrayList<File> cpsFiles=new ArrayList<File>();
			int tick=0;
			for (int j=0;j<n;j++) {
				File file=paths[j];
				String pname=file.getAbsolutePath();
				if (pname.endsWith(".xmd") || pname.endsWith(".cmd") || 
						pname.endsWith(".cps")) {
					cpsFiles.add(file);
					tick++;
				}
			}
			if (tick>0) {
				tables=new ArrayList<StringBuilder>();
				Iterator<File> flst=cpsFiles.iterator();
				while (flst.hasNext()) {
					StringBuilder strbld=tableText(flst.next());
					if (strbld!=null) {
						tables.add(strbld);
					}
				}
				fillHTML();
			}
			else {
				System.out.println(dir_name+" contains no files");
			}
		}
		else { 
			System.out.println(dir_name+" contains no files");
		}

	}
	
	public static StringBuilder tableText(File file) {
		
		// name comment for searching
		StringBuilder contents=new StringBuilder("\n<!--"+file.getName()+"-->\n");
		
		// open the cps file for reading
		Scanner scanner=null;
		try {
			scanner=new Scanner(file);
		} catch (Exception iox) {
			System.err.println("Failed to open cps file "+file.getPath()+" for precessing or to start a file 'scanner'");
			return contents;
		}
		
		// form 
		StringBuilder linkheader=new StringBuilder("<a href="+file.getPath()+">");
		
		// first find line numbers for first and last 
		//    lines of data targets.
		int des_a=-1;
		int des_b=-1;
		int about_a=-1;
		int about_b=-1;
		int line_num=0;
		while(scanner.hasNext()) {
			String line=scanner.nextLine();
			line_num++;
			if (line.contains("<description>"))
				des_a=line_num;
			if (line.contains("</description>"))
				des_b=line_num;
			if (line.contains("<AboutImage"))
				about_a=line_num;
			if (line.contains("</AboutImage"))
				about_b=line_num;
		}
		
		// look for description first
		StringBuilder description=new StringBuilder();
		if (des_a>0 && des_b>=des_a) {
			try {
				scanner.close();
				scanner=new Scanner(file);
			} catch (Exception iox) {
				System.err.println("Failed to open cps file "+file.getPath()+" for precessing or to start a file 'scanner'");
				return contents;
			}
			int tick=1;
			while (tick<des_a) {
				scanner.nextLine();
				tick++;
			}
			tick++;
			String desline=scanner.nextLine(); // starts here
			int k1=desline.indexOf('>')+1;
			
			// description on single line
			if (des_a==des_b) { 
				if (k1>0) {
					int k2=desline.indexOf('<',k1);
					String dtion=desline.substring(k1,k2).trim();
					if (dtion.length()>0)
						description.append(dtion);
				}
			}
			else {
				description.append(desline.substring(k1));
				while (tick<des_b) {
					description.append(scanner.nextLine());
					tick++;
				}
				desline=scanner.nextLine();
				int k2=desline.indexOf('<');
				if (k2>0) {
					desline=desline.substring(0,k2).trim();
					if (desline.length()>0)
						description.append(desline);
				}
			}
		} // done with 'description', but may be empty
		
		// next check for AboutImage
		StringBuilder aboutImage=new StringBuilder();
		if (about_a>0 && about_b>about_a) {
			try {
				scanner.close();
				scanner=new Scanner(file);
			} catch (Exception iox) {
				System.err.println("Failed to open cps file "+file.getPath()+" for precessing or to start a file 'scanner'");
				return contents;
			}
			int tick=0;
			while (tick<about_a) {
				scanner.nextLine();
				tick++;
			}
			String about=scanner.nextLine();
			if (about.length()>0) {
				aboutImage.append("<a href=\"+file.getPath()+\"> \n");
				aboutImage.append("<img src=\"data:image/jpeg;base64,");
				aboutImage.append(about);
				aboutImage.append("\" alt=\"HTML5\" style=\"width:140px;height:auto\"></a>");
			}
		}

		// start <table>
		contents.append("<table>\n  <tr>\n");
		
		// smaller vertical size for title-only entries
		contents.append("    <td  style=\"text-align: left;padding: 10px;"
				+ "width:auto;height:auto\">\n\n");
		contents.append("<p>"+linkheader.toString()+"<b>"+file.getName()
				+ "</b></a></p>\n");
		
		// if there's a description, with or without image
		if (description.length()>0) {
			contents.append("<p>"+description.toString()+"</p>");
			if (aboutImage.length()==0) {
				contents.append("</td>\n");
				contents.append("<td style=\"text-align:center;padding:10px;width:140px\">\n"
						+"<p>No Image</p></td>\n");
			}
			else
				contents.append("</td>\n");
		}
		
		// if there's an image
		if (aboutImage.length()>0) {
			contents.append("<td style=\"text-align: center;padding:10px;width:140px\">\n");
			contents.append(aboutImage.toString());
			contents.append("</td>\n");
		}
		contents.append("  </tr>\n"
				+ "</table>\n");
		
		scanner.close();
		return contents;
	}
	
	/**
	 * Fill in the html <table> item for this script
	 * @return boolean
	 */
	public boolean fillHTML() {
		// header
		header=new StringBuilder("<!doctype html>\n"
				+ "<html lang=\"en\">\n\n"
				+ "<head>\n\n"
				+ "<title>ScriptList.html, "
				+java.time.LocalDate.now());    

		header.append("</title>\n"
				+ "<meta charset=\"utf-8\">\n"
				+ "<meta name=\"viewport\" content=\"width=device-width, initial=scale=3\">\n"
				+ "<link href=\"style.css\" rel=\"stylesheet\">\n"
				+ "<style>\n\n"
				+ "  p {\n"
				+ "      font-size: 1.0em;\n"
				+ "  }\n\n"
				+ "  body {\n"
				+ "      font-family: Arial, Helvetica, sans-serif;\n"
				+ "  }\n\n"
				+ "  table {\n"
				+ "      width: 70%;\n"
				+ "  }\n\n"
				+ "  td {\n"
				+ "      border:1px solid black;\n"
				+ "      text-align: center;\n"
				+ "  }\n\n"
				+ "</style>\n"
				+ "</head>\n"
				+ "<body>\n"
				+ "<h4><em><b>CirclePack</b></em> Scripts in "
				+ "<a href=\""+CurrentDirectory+"\">"+CurrentDirectory+"</a></h4>"
				+ "<!-- "+java.time.LocalDate.now()+" -->\n");
		
		// footer
		footer=new StringBuilder("<p><b><em>CirclePack</em></b>"); 
		footer.append(" software is available <a href=\"https://github.com/kensmath/CirclePack\">");
		footer.append(" here</z></p>\n");
		footer.append("</body>\n</html>\n\n");
		
		// Build full file
		htmlContents=new StringBuilder(header.toString());
		
		int count=0;
		Iterator<StringBuilder> sblst=tables.iterator();
		while (sblst.hasNext()) {
			htmlContents.append(sblst.next().toString());
			count++;
		}
		
		htmlContents.append(footer.toString());

		// save

		File outfile=null;
		BufferedWriter fp=null;
		try {
			outfile=new File(CurrentDirectory,"ScriptList.html");
			fp = new BufferedWriter(new FileWriter(outfile,false));
			fp.write(htmlContents.toString());
			fp.flush();
			fp.close();
		} catch(Exception iox) {
			System.err.println("failed to write "+outfile);
		}
		System.out.println("ScriptLister listed "+count+" scripts in "+outfile);
		return true;
	}
	
	public static void main(String[] args) {

		ScriptLister obj = new ScriptLister(args[0]);
		
	}

}
