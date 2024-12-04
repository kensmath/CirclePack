package allMains;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Scanner;

import org.apache.commons.codec.binary.Base64;

import browser.BrowserUtilities;
import util.Base64InOut;

/**
 * This can be standalone or called from Circlepack.
 * It generates 'html' files that accumulate CirclePack 
 * *.cps (or deprecated *.xmd and *.cmd) script files
 * in "{parentdirectoryname}.html". This *.html file
 * is stored in the parent directory if it is local
 * or in the java.io.tmpdir if on a web site. 
 * The code operates in one of two modes:
 * 
 * (1) Can be run standalone with a given URL and file
 * name (optional). (For standalone, 
 * run via 'ScriptLister.jar' and specify a url
 * as the argument.) When the *html file is loaded 
 * into the CirclePack 'WWW' window, it provides 
 * info on each script and links for the full path 
 * to load it into the CirclePack script window.
 * 
 * In this form of html file there is a 
 * <table>...</table> entry for each script. It formats 
 * 4 pieces of data from the XML-encoded script file:
 * + Script Name, entered as html comment preceding the table
 * + Script header text description (if it exists)
 * + Script ABOUT_IMAGE: (if it exists) and link url 
 * (if it exists; default to current directory).
 * Note that the link may have to be adjusted depending on
 * where the script is stored.
 * 
 * (2) When a directory is showing in the WWW window,
 * the "H" button will generate a floating style listing 
 * of the scripts with just the AboutImage and name and
 * active link to load. In this case, if the output cannot 
 * be stored in an *.html file (i.e., if it's a remote 
 * directory), then it will reside in a file in the
 * java.io.tmpdir.
 * 
 * @author kensm, February 2023 and November 2024
 */
public class ScriptLister {

	public URL theURL;
	public File theDirectory;
	public String theFilename;
	public String protocol;

	// mode 1 = standalone format; 0 = directory style
	int mode; 

	// get directory's files and their contents
	ArrayList<URL> cpsFiles;
	ArrayList<StringBuilder> scriptnames;
	ArrayList<StringBuilder> descriptions;
	ArrayList<StringBuilder> aboutImages;
	
	// constructors
	public ScriptLister() { 
		this(null,0,null);
	}
	
	public ScriptLister(String dir_name) {
		this(dir_name,0,null);
	}
	
	public ScriptLister(String dir_name,String outname) {
		this(dir_name,0,outname);
	}
	
	public ScriptLister(String dir_name, int m, String outname) {

		if (dir_name==null || dir_name.length()==0) 
			dir_name="file:/"+System.getProperty("user.dir");

		// figure out whether it is a directory 
		String ourUrl=BrowserUtilities.parseURL(dir_name);
		if (ourUrl==null || !BrowserUtilities.URLisDirectory(ourUrl))
			System.exit(0);
		theDirectory=new File(ourUrl);
		
		// set name for the html file
		if (outname!=null && outname.length()>0)
			theFilename=outname+"_scripts.html";
		else {
			if (!theDirectory.getName().endsWith(".html"))
				theFilename=theDirectory.getName()+"_scripts.html";
		}

		// set mode
		mode=m;
		
// debugging
		System.out.println("from "+theDirectory+", into "+theFilename);
	}
		
	public File go() {
		
// debugging
		try {
			URL dummy=new URL(theDirectory.toString());
			theDirectory=new File(dummy.getPath());
			protocol=dummy.getProtocol();
		} catch (MalformedURLException mex) {
			System.err.println("'theDirectory' listing fails");
		}
		
		File[] paths=theDirectory.listFiles();
		int n=paths.length;
		if (n>0) {
			cpsFiles=new ArrayList<URL>();
			for (int j=0;j<n;j++) {
				File file=paths[j];
				String pname=file.getAbsolutePath();
				if (pname.endsWith(".xmd") || pname.endsWith(".cmd") || 
						pname.endsWith(".cps")) {
					try {
						cpsFiles.add(new URL(protocol+":"+file.getPath()));
					} catch(MalformedURLException mlx) {
						System.err.println("malformed URL; "+mlx.getMessage());
					}
				}
			}
		}
		
		if (cpsFiles.size()==0) {
			System.out.println(theDirectory+" contains no files");
			System.exit(0);
		}

		// gather the contents
		scriptnames=new ArrayList<StringBuilder>();
		descriptions=new ArrayList<StringBuilder>();
		aboutImages=new ArrayList<StringBuilder>();
		Iterator<URL> flst=cpsFiles.iterator();
		while (flst.hasNext()) {
			getContent(flst.next());
		}

		// now put in file
		return fillHTML();
	}
	
	/**
	 * This simply finds the title, description,
	 * and aboutImage. If all three exist, then 
	 * they are added to their individual ArrayList's.
	 * @param file
	 * @return true on success
	 */
	public boolean getContent(URL url) {
		
		File file=new File(url.getFile().replace("%20", " "));
		
		// open the cps file for reading
		Scanner scanner=null;
		try {
			scanner=new Scanner(file);
		} catch (Exception iox) {
			System.err.println("Failed to open cps file "+file.getPath()+" for precessing or to start a file 'scanner'");
			scanner.close();
			return false;
		}

		// get the title code
		StringBuilder title=new StringBuilder("\n<!--"+file.getName()+"-->\n");
		title.append("<a href="+url.toString()+">"
				+"<b>"+file.getName()+"</b></a>");
		
		// find line nums, first/last lines of data targets.
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
				return false;
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
		if (description.length()==0)
			description.append("No description provided");
		
		// next get AboutImage
		StringBuilder aboutImage=new StringBuilder();
		if (about_a>0 && about_b>about_a) {
			try {
				scanner.close();
				scanner=new Scanner(file); // reopen
			} catch (Exception iox) {
				System.err.println("Failed to open cps file "+file.getPath()+" for precessing or to start a file 'scanner'");
				return false;
			}
			// skip to actual image
			int tick=0;
			while (tick<about_a) {
				scanner.nextLine();
				tick++;
			}
			String about=scanner.nextLine(); // this is the image
			if (about.length()>0) {
				aboutImage.append("<a href="+file.getPath()+"> \n");
				aboutImage.append("<img src=\"data:image/jpeg;base64,");
				aboutImage.append(about);
				if (mode==1) // standalone mode size
					aboutImage.append("\" alt=\"HTML5\" style=\"width:140px;height:auto\"></a>");
				else // directory mode size
					aboutImage.append("\" alt=\"HTML5\" style=\"width:100px;height:auto\"></a>");
			}
		}
		// TODO: may want to specify a different image as default
		if (aboutImage.length()==0) {
//			URL url=CPBase.getResourceURL("/Icons/tags/myCPtag.jpg");
			File defaultOwl=new File("C:/Users/kensm/Documents/Owl_250x250.jpg");
			byte[] by=Base64InOut.getBytesFromFile(defaultOwl);
			byte[] outbytes=Base64.encodeBase64(by);
			String str = new String(outbytes,StandardCharsets.UTF_8);
			aboutImage.append("<a href="+file.getPath()+"> \n");
			aboutImage.append("<img src=\"data:image/jpeg;base64,");
			aboutImage.append(str);
			aboutImage.append("\" alt=\"HTML5\" style=\"width:100px;height:auto\"></a>");
		}

		if (title.length()>0 && description.length()>0 &&
				aboutImage.length()>0) {
			scriptnames.add(title);
			descriptions.add(description);
			aboutImages.add(aboutImage);
			return true;
		}
		return false;
	}

	/**
	 * Create new html file, form depending on 'mode'.
	 * If directory is on the web, must save resulting
	 * 'outfile' to "java.io.tmpdir"
	 * @return file, null on error
	 */
	public File fillHTML() {
		
		if (!protocol.startsWith("file"))
			theDirectory=
			new File(System.getProperty("java.io.tmpdir"));
		
		// if file already exists, delete it
		File outfile=new File(theDirectory,theFilename);
		if (outfile.exists()) {
			if (!outfile.delete()) { 
				System.err.println("Cannot delete "+theFilename);
				System.exit(0);
			}
			else
				outfile=new File(theDirectory,theFilename);
		}

		// header
		StringBuilder header=new StringBuilder(
				"<!doctype html>\n"
				+ "<html lang=\"en\">\n\n"
				+ "<head>\n\n"
				+ "<title>currentDirectory.toString()"
				+ "+/+theFilename, "
				+ java.time.LocalDate.now()
				+ "</title>\n"
				+ "<meta charset=\"utf-8\">\n"
				+ "<meta name=\"viewport\" content=\"width=device-width, initial=scale=3\">\n"
				+ "<link href=\"style.css\" rel=\"stylesheet\">\n");
		
		if (mode==1) { // standalone version
			
			header.append(
					"<style>\n\n"
					+ "  p {\n"
					+ "      font-size: 1.0em;\n"
					+ "  }\n\n"
					+ "  body {\n"
					+ "      font-family: Arial, Helvetica, sans-serif;\n"
					+ "  }\n\n");

			header.append("  table {\n"
				+ "      width: 70%;\n"
				+ "  }\n\n"
				+ "  td {\n"
				+ "      border:1px solid black;\n"
				+ "      text-align: center;\n"
				+ "  }\n\n");
		}
		
		else { // directory version, mode==0
			header.append(
					"<style>\r\n"
					+"div {\n\rfloat:left;\n\rpadding: 5px;\n}\n");
		}
		
		header.append("\n</style>\n");
		
		header.append(
				"</head>\n"
				+ "<body>\n"
				+ "<div>"
				+ "<h4><em><b>CirclePack</b></em> Scripts in the "
				+ "<a href=\""+theDirectory+"\">"
				+ theDirectory.getName()+"</a>"
				+" directory</h4>+ <!-- "+java.time.LocalDate.now()+" -->\n");
		
		// footer
		StringBuilder footer=new StringBuilder("</div>\n"); // end the main division
		footer.append("<p><b><em>CirclePack</em></b>"); 
		footer.append(" software is available <a href=\"https://github.com/kensmath/CirclePack\">");
		footer.append(" here</z></p>\n");
		footer.append("</body>\n</html>\n\n");
		
		// Build full file
		StringBuilder htmlContents=new StringBuilder(header.toString());

		// start the main division
		htmlContents.append("<div>\n");

		int N;
		if (scriptnames==null || (N=scriptnames.size())==0)
			return null;
		for (int k=0;k<N;k++) {
			if (mode==1) { // standalone
				// start <table>
				htmlContents.append("<table>\n  <tr>\n");
				
				// smaller vertical size for title-only entries
				htmlContents.append(
						"    <td  style=\"text-align: left;padding: 10px;"
						+ "width:auto;height:auto\">\n\n");
				htmlContents.append(
						"<p>"+scriptnames.get(k).toString()+"</p>\n");
				
				htmlContents.append("<p>"+descriptions.get(k).toString()+"</p>");

				htmlContents.append("<td style=\"text-align: center;padding:10px;width:140px\">\n");
				htmlContents.append(aboutImages.get(k).toString());
				htmlContents.append("</td>\n");
				htmlContents.append("  </tr>\n</table>\n");
			}
			// directory version
			else {
				htmlContents.append("<div>\n");
				htmlContents.append(aboutImages.get(k).toString()+"\n");
				htmlContents.append("<figcaption style=\"center\">"
						+ "<small>"+
						scriptnames.get(k).toString()
						+"</small>\n<p></figcaption>\n");
				htmlContents.append("</div>\n");
			}
		} // done with all entries
		
		htmlContents.append(footer.toString());

		// save; have to save to temp file if the
		//   CurrentDirectory is a web site
		if (theDirectory.getPath().startsWith("htt"))
			theDirectory=new File(System.getProperty("java.io.tmpdir"));
		BufferedWriter fp=null;
		try {
			fp = new BufferedWriter(new FileWriter(outfile,false));
			fp.write(htmlContents.toString());
			fp.flush();
			fp.close();
		} catch(Exception iox) {
			System.err.println("failed to write "+outfile);
			return null;
		}
		System.out.println("ScriptLister listed "+N+" scripts in "+outfile);
		return outfile;
	}
	
	public static void main(String[] args) {
		
		// default directory to get files from
		String myDirectory=System.getProperty("user.dir");
		// default script file name
		String outfileName=null;
		// default mode: short directory style
		int mode=0;
		
		int N=args.length;
		for (int j=0;j<N;j++) {
			String arg=args[j];
			
			// look for flags -f and -m
			if (arg.startsWith("-")) {
				// filename to put list into; needs to be '.html'
				if (arg.startsWith("-f")) {
					j++;
					outfileName=args[j];
				}
			
				// mode: larger (1) or directory style (0)
				else if (arg.startsWith("-m")) {
					j++;
					try {
						mode=Integer.parseInt(args[j]);
					}catch (Exception ex) {
						System.err.println("ScriptList failed to get 'mode'");
					}
				}
			}

			// this should usually be the last argument
			else {
				myDirectory=new String(arg);
			}
		} // end of for loop

		// run
		ScriptLister obj = new ScriptLister(myDirectory,mode,outfileName);
		File theFile=obj.go();
		System.out.println("ScriptList file "+theFile+" has been saved");

	}

}
