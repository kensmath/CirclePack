package script;

import java.io.File;
import java.util.Scanner;

/**
 * static method to build a section of <table> html code describing 
 * a script file. See 
 * @author kensm, February 2023
 *
 */
public class Cps2HTML {

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
	
}
