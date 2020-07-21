package infoProcessing;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.xerces.parsers.DOMParser;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;


public class Info2HTML {
	public static File infofile; // input file, XML format
	public static File cmdfile; // file of command details in html for "Help" frame
	public static File indexfile; // file of command names/flags/hints
	public static File completionfile; // file for 'command completion' text file
	
	StringWriter fp;
	StringWriter indxfp;
	StringWriter compfp;

	static String cmdName;
	static String indexName;
	static String compName;

	public Info2HTML() throws IOException {
		DOMParser parser = new DOMParser();
		try {
			parser.parse(infofile.toString());
			String htmlname=new String(infofile.getName());
			int idx=htmlname.indexOf('.');
			if (idx>0)
				htmlname=htmlname.substring(0,idx); // hold this for later use
	    	fp = new StringWriter(); 
	    	indxfp = new StringWriter();
	    	compfp = new StringWriter();
	    	
		} catch(Exception ex) {
			System.err.println(ex.getMessage());
		}
		org.w3c.dom.Document doc = parser.getDocument();
		NodeList allNodes=doc.getDocumentElement().getChildNodes();
		if (allNodes==null || allNodes.getLength()==0) 
			System.err.println("Nodelist was null");
		
		// 'cmdfile' and 'indexfile' header info
		fp.write("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" "+
				"\"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">"+
				"<html xmlns=\"http://www.w3.org/1999/xhtml\">"+
				"<head><meta http-equiv=\"Content-Type\" content=\""+
				"text/html; charset=utf-8\" />\n");
		indxfp.write("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" "+
				"\"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">"+
				"<html xmlns=\"http://www.w3.org/1999/xhtml\">"+
				"<head><meta http-equiv=\"Content-Type\" content=\""+
				"text/html; charset=utf-8\" />\n"+
				"<style llink=\"text/css\">\nA:link {text_decoration: none}</style>\n");
		
		for (int k=0;k<allNodes.getLength();k++) {
			Node mainNode=allNodes.item(k);
			NamedNodeMap mainMap=mainNode.getAttributes();
			String type;
			
			String strtstr=mainNode.getNodeName();
			if (strtstr.equals("CPdocs")) {
			
			Node typeNode;
			if ((typeNode=mainMap.getNamedItem("type"))==null) {
				throw new IOException("'CPdocs needs a 'type'");
			}
			type=typeNode.getNodeValue();
			
			// CmdDetails file
			if (type.equals("CmdDetails")) {
				fp.write("<title>CirclePack command details</title>"+
						"</head>\n\n<body>");
				
				// title/intro
				fp.write("<center><h1><em><strong><font color=\"blue\">"+
						"CirclePack</font></strong></em> command details</h1></center>\n");
				fp.write("Here is an alphabetic listing of all the commands "+
						"which the user can issue to <em>CirclePack</em> via "+
						"its <strong>command</strong> line or in its scripts, "+
						"along with their various flags and options.<br/><br/>\n");
						
				NodeList cmdNodes=mainNode.getChildNodes();
				for (int j=0;j<cmdNodes.getLength();j++) {
					Node cmd=(Node)cmdNodes.item(j);
					NamedNodeMap cmdMap=cmd.getAttributes();
					Node node;
					if (cmd.getNodeName().equals("command")) {
						NodeList cmdStuff=cmd.getChildNodes();
						String cmdname=null;
						String flags=null;
						String hint=null;
						if ((node=cmdMap.getNamedItem("cmd"))==null)
							break;
						
						// command name also used as an anchor
						cmdname=node.getNodeValue().trim();
						fp.write("<font size=\"+1\" color=\"blue\"><strong>"+
								"<a name=\""+cmdname+"\">"+cmdname+
								"</a></strong></font>&nbsp;&nbsp; <font face=\"sans-serif\">");
						
						// get flags
						if ((node=cmdMap.getNamedItem("flags"))!=null) {
							flags=node.getNodeValue();
							fp.write(flags+"&nbsp;&nbsp; ");
						}
						
						// get hint
						if ((node=cmdMap.getNamedItem("hint"))!=null) {
							hint=node.getNodeValue();
							fp.write(hint);
						}
						fp.write("</font>\n");
						
						// write in index file
						indxfp.write("<strong> <font size=\"-2\"><a class=\"llink\" href=\""
								+cmdname+"\">"+cmdname+
								"</a></strong></font><font face=\"sans-serif\" size=\"-1\">");
						if (flags!=null)
							indxfp.write("&nbsp; "+flags);
						indxfp.write("</font><br/>\n\n");
						
						// write in completion file (except for ?{*})
						if (!cmdname.startsWith("?{*")) {
							compfp.write(cmdname+" ");
							if (flags!=null) compfp.write(flags+" ");
							if (hint!=null) compfp.write(hint);
							compfp.write("\n");
						}
						
						// cycle through children nodes
						for (int n=0;n<cmdStuff.getLength();n++) {
							Node next=(Node)cmdStuff.item(n);
							NamedNodeMap nextMap=next.getAttributes();
							String word=next.getNodeName();

							// description
							if (word.equals("description")) {
								String str=null;
								if (next.getFirstChild()!=null 
										&& (str=next.getFirstChild().getNodeValue())!=null);
									fp.write("<blockquote>\n"+"<strong>Description: "+
											"</strong>"+str+"</blockquote>\n");
							} // done with 'description'
							else if (word.equals("options")) {
								String hdg=new String("Options");
								if ((node=nextMap.getNamedItem("heading"))!=null) {
									hdg=node.getNodeValue();
								}
								fp.write("<strong>&nbsp;&nbsp;&nbsp;&nbsp; "+hdg+":</strong>\n");
								fp.write("<blockquote>\n");
								
								// cycle through for options
								NodeList optList=next.getChildNodes();
								if (optList.getLength()>0) {
									fp.write("<table>\n");
									Node opt;
									Node detail;
									for (int m=0;m<optList.getLength();m++) {
										opt=optList.item(m);
										if (opt.getNodeName().equals("opt")) {
											NamedNodeMap optMap=opt.getAttributes();
											if ((detail=optMap.getNamedItem("flag"))!=null) {
												String detval=detail.getNodeValue().trim();
												
												// is this a query? put in completion
												if (cmdname.startsWith("?{*}")
														&& detval!=null && detval.length()>0 && 
														detval.charAt(0)=='?') {
													int kj=detval.indexOf(" ");
													if (kj>0) {
														String qflags=detval.substring(kj+1).trim();
														String qname=detval.substring(0,kj);
														// write in completion file
														compfp.write(qname+" ");
														if (qflags.length()>0) compfp.write(qflags+" ");
														compfp.write("\n");
													}
												}
												
												fp.write("<tr><td width=\"25%\" valign=\"top\">"+
														detval+"</td>\n");
												String expl=null;
												if (opt.getFirstChild()!=null 
														&& (expl=opt.getFirstChild().getNodeValue())!=null);
													fp.write("<td width=\"65%\" align=\"left\">"+expl+"</td>");
												fp.write("</tr>\n");
											}
										}
										else if (opt.getNodeName().equals("comment")) {
											String str=null;
											if (opt.getFirstChild()!=null 
													&& (str=opt.getFirstChild().getNodeValue())!=null);
												fp.write(str+"\n");
										} 
									} // cycle through instances
									fp.write("</table>");
								}
								fp.write("</blockquote>\n");
							} // done with 'options'						
							else if (word.equals("examples")) {
								String hdg=new String("Examples");
								if ((node=nextMap.getNamedItem("heading"))!=null) {
									hdg=node.getNodeValue();
								}
								fp.write("<strong>"+
										"&nbsp;&nbsp;&nbsp;&nbsp; "+hdg+":</strong>\n");
								fp.write("<blockquote>\n");
								if ((node=nextMap.getNamedItem("text"))!=null) {
									fp.write(node.getNodeValue());
								}
								
								// cycle through for examples
								NodeList exList=next.getChildNodes();
								if (exList.getLength()>0) {
									fp.write("<table>\n");
									Node txt;
									Node explain;
									for (int m=0;m<exList.getLength();m++) {
										txt=exList.item(m);
										if (txt.getNodeName().equals("instance")) {
											NamedNodeMap exMap=txt.getAttributes();
											if ((explain=exMap.getNamedItem("text"))!=null) {
												fp.write("<tr><td width=\"30%\"><font color=\"blue\">"
														+explain.getNodeValue()+"</font></td>\n");
											}
											else // no text
												fp.write("<tr><td width=\"30%\"></td>\n");
											String expl=null;
											if (txt.getFirstChild()!=null 
													&& (expl=txt.getFirstChild().getNodeValue())!=null);
											fp.write("<td width=\"68%\">"+expl+"</td>");
											fp.write("</tr>\n");
										}
									} // cycle through instances
									fp.write("</table>");
								}
								fp.write("</blockquote>\n");
							} // done with 'examples'
							else if (word.equals("seealso")) {
								Node see;
								NodeList seeList=next.getChildNodes();
								boolean seeAny=false;
								StringBuilder strbuf=new StringBuilder("<strong>"+
										"&nbsp;&nbsp;&nbsp;&nbsp; See Also:</strong>\n"+
										"&nbsp;&nbsp;&nbsp; ");
								for (int m=0;m<seeList.getLength();m++) {
									see=seeList.item(m);
									String ref=null;
									if (see.getNodeName().equals("see")) {
										if (see.getFirstChild()!=null 
												&& (ref=see.getFirstChild().getNodeValue())!=null)
										strbuf.append("<a href=\""+ref+"\">"+ref.trim()+"</a>&nbsp;&nbsp; ");
										seeAny=true;	
									}
								}
								strbuf.append("\n");
								if (seeAny) 
								{ 
									fp.write(strbuf.toString() + "<br/><br/>\n"); //"<blockquote>\n");
								}
							} // done with 'seealso'
						}
						//fp.write("</blockquote><br/>\n");
					}
				}
				
				fp.write("</body>\n");
				fp.write("</html>\n");
			}
			}
		
		} // end of loop for 'CPdoc'
		
		try
		{
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
			DocumentBuilder builder = dbf.newDocumentBuilder();
			
			
			System.out.println("Checking parsing");
			builder.parse(new ByteArrayInputStream(fp.toString().getBytes()));
			//builder.parse(new ByteArrayInputStream(indxfp.toString().getBytes()));
			//builder.parse(new ByteArrayInputStream(compfp.toString().getBytes()));
			System.out.println("Done parsing");
			System.out.println("after parsing, directory is: "+System.getProperty("user.dir"));

			
			File outfile=new File("CirclePack/src/Resources/doc/CmdDetails.html");
			if (!outfile.exists())
				System.err.println("'CirclePack/src/Resources/doc/CmdDetails.html' doesn't see to exist");
			
//			FileWriter fw = new FileWriter("CirclePack/src/Resources/doc/CmdDetails.html", false);
			BufferedWriter fw = new BufferedWriter(
					new FileWriter("CirclePack/src/Resources/doc/CmdDetails.html", false));
	    	fw.write(fp.toString());
	    	fw.flush();
	    	fw.close();
	    	
	    	fw = new BufferedWriter(new FileWriter("CirclePack/src/Resources/doc/CmdIndex.html", false));
	    	fw.write(indxfp.toString());
	    	fw.flush();
	    	fw.close();
	    	
	    	fw = new BufferedWriter(new FileWriter("CirclePack/src/Resources/doc/CmdCompletion.txt", false));
	    	fw.write(compfp.toString());
	    	fw.flush();
	    	fw.close();
	    	System.out.println("Have written: "+
	    			"\n   'CirclePack/src/Resources/doc/CmdDetails.html',"+
	    			"\n   'CirclePack/src/Resources/doc/CmdIndex.html', and"+
	    			"\n   'CirclePack/src/Resources/doc/CmdCompletion.txt'.");
	    	

		}catch(IOException e)
		{
			System.err.println(e);
			System.exit(1);
			
		}catch(org.xml.sax.SAXException e)
		{
			System.err.println(e);
			System.exit(1);
		}catch(javax.xml.parsers.ParserConfigurationException e)
		{
			System.err.println(e);
			System.exit(1);
		}finally
		{
			fp.close();
			indxfp.close();
			compfp.close();
		}
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		
		
		// what is the current directory?
		System.out.println("user directory is: "+System.getProperty("user.dir"));
		
		// create new file
		infofile=new File("CirclePack/src/Resources/doc/CmdDetails.txt");
		if (!infofile.exists())
			System.err.println("'"+infofile.toString()+"' doesn't seem to exist");
			
		try 
		{
			new Info2HTML();
		}catch (IOException iox) 
		{
			iox.printStackTrace();
		}
	}
}
