package frames;

import input.CPFileManager;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.border.EmptyBorder;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;

import org.apache.xerces.parsers.DOMParser;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import allMains.CPBase;
import allMains.CirclePack;
import circlePack.PackControl;

/**
 * Constructs a hover version of the Help frame with various 
 * help info in tabbed panes. In the future, should add search 
 * and other capabilities, improve formating, add tabs such as:
 * * functional grouping of commands
 * * illustrations (jpg's) of display options.
 * @author kens
 *
 */
public class HelpHover extends HoverPanel implements HyperlinkListener {

	private static final long 
	serialVersionUID = 1L;

	static JPanel leftPanel; // left side with list and search
	static JTabbedPane helpTabbedPane = new JTabbedPane(); // main tabbed panel

	// in the leftPanel
	private JTextField search_field;
	private JTextPane index_list;

	// in the tabbed pane
	private static JTextPane detailArea;
	private static JTextPane DISPArea;
	private JScrollPane detailScroller;
	private JScrollPane DISPScroller;
	public static StringBuilder extendText;
	public static JTextPane extenderArea;
	private JScrollPane extenderScroller;
	private IncrementalSearch isearch;

	// Constructor
	public HelpHover(String helpfilename) {
		super(700,600,"CirclePack Help Information");
	}

	public void initComponents() {
		this.setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));

		// Here are the various tabs (so far)
		JTextPane aboutArea=new JTextPane();
		aboutArea.setContentType("text/html");
		aboutArea.addHyperlinkListener(new IndexHyperlinkListener());
		aboutArea.setBorder(new EmptyBorder(new Insets(5,10,5,10)));
		aboutArea.setEditable(false);
		aboutArea.addFocusListener(new util.NavFocusListener(aboutArea));
		try {
			aboutArea.setPage(CPBase.getResourceURL("/doc/About.html"));
		} catch (Exception ex) {}
		JScrollPane aboutScroller = new JScrollPane(aboutArea);
		helpTabbedPane.add(aboutScroller,"About");

		// need to know the name of the "CmdDetail" tab
		detailArea=new JTextPane();
		detailArea.setContentType("text/html");
		detailArea.addHyperlinkListener(new IndexHyperlinkListener());
		detailArea.setBorder(new EmptyBorder(new Insets(5,10,5,10)));
		detailArea.setEditable(false);
		detailArea.addFocusListener(new util.NavFocusListener(detailArea));
		try {
			detailArea.setPage(CPBase.getResourceURL("/doc/CmdDetails.html"));
		} catch (Exception ex) {}
		detailScroller = new JScrollPane(detailArea);
		helpTabbedPane.add(detailScroller,"Command Details");

		// need to know the name of the "DISP area" tab
		DISPArea=new JTextPane();
		DISPArea.setContentType("text/html");
		DISPArea.setBorder(new EmptyBorder(new Insets(5,10,5,10)));
		DISPArea.setEditable(false);
		DISPArea.addFocusListener(new util.NavFocusListener(DISPArea));
		try {
			DISPArea.setPage(CPBase.getResourceURL("/doc/DispCmds.html"));
		} catch (Exception ex) {}
		DISPScroller = new JScrollPane(DISPArea);
		helpTabbedPane.add(DISPScroller,"Display Calls");

		//		TODO: addScrollArea("GUI Basics","GUI_Basics.info");

		extenderArea=new JTextPane();
		extenderArea.setContentType("text/html");
		extenderArea.setBorder(new EmptyBorder(new Insets(5,10,5,10)));
		extenderArea.setEditable(false);
		extenderArea.addFocusListener(new util.NavFocusListener(extenderArea));

		// create buffer to hold text
		extendText=new StringBuilder("<html>\n<head>\n");
		extendText.append("<p align=\"center\"><title><big><b>PackExtender</b> help information</big></title></p>\n");
		extendText.append("</head>\n<body>\n<hr><br>");
		extendText.append("<basefont size=\"2\">");
		extendText.append("Users can extend the class 'PackExtender' to "+
				"their own specialized classes. Each gets an abbreviation "+
				"'xx' so its commands can be called from CirclePack via "+
				"'|xx| mycall'.<br>\n<hr><br>\n");

		// load the actual info from an xml-formated file
		// NOTE: disabled for now
		//		if (loadXMLinfo("Xtenders.inf","packextender",extendText)==0)
		//			CirclePack.cpb.errMsg("'Xtender.info' did not get parsed correctly");
		//		extendText.append("(Not yet ready for prime time)");

		//		extendText.append("\n</body>\n</html>");
		extenderArea.setText(extendText.toString());
		extenderScroller = new JScrollPane(extenderArea);
		helpTabbedPane.add(extenderScroller,"Pack Extenders");

		JTextPane miscArea=new JTextPane();
		miscArea.setContentType("text/html");
		miscArea.addHyperlinkListener(new IndexHyperlinkListener());
		miscArea.setBorder(new EmptyBorder(new Insets(5,10,5,10)));
		miscArea.setEditable(false);
		miscArea.addFocusListener(new util.NavFocusListener(aboutArea));
		try {
			miscArea.setPage(CPBase.getResourceURL("/doc/MiscInfo.html"));
		} catch (Exception ex) {}
		JScrollPane miscScroller = new JScrollPane(miscArea);
		helpTabbedPane.add(miscScroller,"Misc");

		JTextPane myNotesArea = new JTextPane();
		myNotesArea.setContentType("text/html");
		myNotesArea.addHyperlinkListener(new IndexHyperlinkListener());
		myNotesArea.setBorder(new EmptyBorder(new Insets(5, 10, 5, 10)));
		myNotesArea.setEditable(true);
		myNotesArea.addFocusListener(new util.NavFocusListener(aboutArea));

		// AF: MyNotes saved as <user.home>/myCirclePack/myNotes.txt
		File myNotesFile = new File(CPFileManager.HomeDirectory.toString() + File.separator + "myCirclePack" + File.separator + "myNotes.txt");
		if (!myNotesFile.exists()) {
			// AF: If MyNotes file doesn't exist, create the file with default contents.
			BufferedWriter myFileWriter = null;
			try {
				myFileWriter = new BufferedWriter(new FileWriter(myNotesFile));
				// AF: String.format() will convert the %n token into the system dependent newline character.
				myFileWriter.write(String.format("Put your own notes here.%n"));
			} catch (IOException e) {
				// AF: If we can't write the default file, output the error and move on.
				System.err.println("Failed to write default MyNotes file.");
			} finally {
				// AF: Close the writer. If it fails to close, output the error and move on.
				if (myFileWriter != null) try {myFileWriter.close();} catch (IOException e) {
					System.err.println("Failed to close MyNotes file writer after writing default file.");
				}
			}
		}
		
		try {
			// AF: Set the MyNotes text pane to the MyNotes file.
			myNotesArea.setPage(myNotesFile.toURI().toURL());
		} catch (IOException e) {
			// AF: If the IO operations fail, output an error and move on. The text pane will be blank.
			System.err.println("Failed to load MyNotes file.");
		}
		
		// AF: Create immutable references to the MyNotes file and text pane for saving behavior.
		final File myNotesFileReference = myNotesFile;
		final JTextPane myNotesAreaReference = myNotesArea;
		
		// AF: Create a window adapter to save the text pane when the help window is disposed on program exit.
		WindowAdapter saveOnClose = new WindowAdapter() {
			@Override
			public void windowClosed(WindowEvent e) {
				// AF: Save the contents of the text pane to the file.
				BufferedWriter myFileWriter = null;
				try {
					myFileWriter = new BufferedWriter(new FileWriter(myNotesFileReference));
					myFileWriter.write(myNotesAreaReference.getText());
				} catch (IOException ioe) {
					// AF: If the write fails, output an error and move on.
					System.err.println("Failed to write current MyNotes file.");
				} finally {
					// AF: Close the writer. If it fails to close, output the error and move on.
					if (myFileWriter != null) try {myFileWriter.close();} catch (IOException ioe) {
						System.err.println("Failed to close MyNotes file writer after writing on program exit.");
					}
				}
			}
		};
		// AF: Attach the window adapter to the lockedFrame of the help window.
		// AF: We could attach it to the hoverFrame instead. The choice is arbitrary.
		lockedFrame.addWindowListener(saveOnClose);

/*		6/1/13: disabled: something is slowing CirclePack --- maybe this thread??
		// AF: Create the thread that auto-saves the MyNotes text area.
		Thread autoSave = new Thread() {
			@Override
			public void run() {
				// AF: This thread loops indefinitely, periodically saving the contents of MyNotes.
				while (true) {
					 //  AF: Sleep for three minutes. If the thread is interrupted, do nothing. The
					 // thread won't be interrupted unless it is explicitly and intentionally
					 // interrupted, or if the program is exiting. Either way, we don't need to know
					 // or care.

					try {Thread.sleep(3 * 60 * 1000);} catch (InterruptedException e) {}
					
					 // AF: Create the sub-thread to actually save the contents of MyNotes. Here's why
					 // we do this: the autoSave thread is a daemon thread, which means it may be killed
					 // by the JVM if the JVM wants to exit and only daemon threads remain running. This
					 // is fine, because all it does is wait and periodically start a new thread.
					 
					 // However, the save thread is not a daemon thread, but a user thread. If this thread
					 // is running, the JVM will wait for it to finish before exiting. It will successfully
					 // complete its IO operations and then exit, at which point the JVM will exit if only
					 // daemon threads remain running.
					 // 
					 // If we performed actual IO in the daemon thread, the JVM might kill it during an IO
					 // operation and corrupt the file. If the indefinite loop of sleeping and saving
					 // periodically was a user thread, the JVM could never exit.

					Thread save = new Thread() {
						@Override
						public void run() {
							// AF: Write the contents of MyNotes to the file.
							BufferedWriter myFileWriter = null;
							try {
								myFileWriter = new BufferedWriter(new FileWriter(myNotesFileReference));
								myFileWriter.write(myNotesAreaReference.getText());
							} catch (IOException ioe) {
								// AF: If the write fails, output an error and move on.
								System.err.println("Failed to auto-save current MyNotes file.");
							} finally {
								// AF: Close the file. If it fails, output an error and move on.
								if (myFileWriter != null) try {myFileWriter.close();} catch (IOException ioe) {
									System.err.println("Failed to close MyNotes file after auto-saving.");
								}
							}
						}
					};
					
					// AF: This is a user thread.
					save.setDaemon(false);
					save.start();
				}
				
			}
		};
		// AF: This is a daemon thread.
		autoSave.setDaemon(true);
		autoSave.start();
*/
		
		JScrollPane myScroller = new JScrollPane(myNotesArea);
		helpTabbedPane.add(myScroller, "MyNotes");

		addScrollArea("List Specs","List.info");
		addScrollArea("Formats","DataFormats.info");

		// left panel has searchbox and index of commands
		leftPanel=new JPanel(new BorderLayout());
		JScrollPane jsp=(JScrollPane)helpTabbedPane.getSelectedComponent();
		JTextComponent jta=(JTextComponent)jsp.getViewport().getView();
		util.EmacsBindings.addEmacsBindings(jta);
		// search panel
		Box jp=Box.createVerticalBox();
		jp.setBorder(new EtchedBorder());
		JLabel label=new JLabel("search:");
		label.setFont(new Font("Serif",Font.BOLD,10));
		jp.add(label);
		search_field=new JTextField(12);
		isearch=new IncrementalSearch(helpTabbedPane,search_field,jta);
		helpTabbedPane.addChangeListener(isearch);
		search_field.getDocument().addDocumentListener(isearch);
		search_field.addActionListener(isearch);
		jp.add(search_field);

		index_list=new JTextPane();
		index_list.setContentType("text/html");
		index_list.addHyperlinkListener(new IndexHyperlinkListener());
		index_list.addMouseListener(this);
		index_list.setEditable(false);
		index_list.addFocusListener(new util.NavFocusListener(index_list));
		try {
			index_list.setPage(CPBase.getResourceURL("/doc/CmdIndex.html"));
		} catch (Exception ex) {}
		JScrollPane scroll = new JScrollPane(index_list);
		scroll.setBorder(new TitledBorder(new EtchedBorder(),"Command List:"));

		leftPanel.add(jp,BorderLayout.NORTH);
		leftPanel.add(scroll,BorderLayout.CENTER);
		leftPanel.setPreferredSize(new Dimension(180,-1));
	}

	void addScrollArea(String title,String filename) {
		JTextArea textArea=new JTextArea();
		textArea.setLineWrap(true);
		textArea.setWrapStyleWord(true);
		textArea.setEditable(false);
		util.EmacsBindings.addEmacsBindings(textArea);
		textArea.setBorder(new EmptyBorder(new Insets(5,10,5,10)));
		textArea.setTabSize(3);
		open(filename,textArea);
		JScrollPane textScroll = new JScrollPane(textArea);
		helpTabbedPane.add(textScroll,title);
	}

	public void loadHover() {
		this.removeAll();
		helpTabbedPane.setPreferredSize(new Dimension(myWidth,myHeight));
		this.add(helpTabbedPane);
	}

	public void loadLocked() {
		this.removeAll();
		helpTabbedPane.setPreferredSize(new Dimension((int)(myWidth*0.75),myHeight));
		leftPanel.setPreferredSize(new Dimension((int)(myWidth*0.25),myHeight));
		this.add(leftPanel);
		this.add(helpTabbedPane);
	}

	/**
	 * Opens given helpfile and puts contents (or error msg)
	 * in given text area.
	 * @param filename String
	 * @param textArea
	 */
	public void open(String filename,JTextArea textArea) {
		BufferedReader fileReader=null;
		try {
			URL urlFile=CPBase.getResourceURL("/doc/"+filename);
			fileReader = new BufferedReader(new InputStreamReader(urlFile.openStream()));
		} catch (Exception ex) {
			ex.printStackTrace();
			System.err.println("failed to read resource");
			return;
		}

		if (fileReader!=null) {
			try {
				String com = fileReader.readLine();
				while (com != null) {
					textArea.append(com+"\n");
					com = fileReader.readLine();
				}
				return;
			} catch (IOException ioe) {
				System.err.println("Bombed reading '"+filename+"'");
				return;
			}
		}
	}

	/**
	 * Given a word, this looks for it in "CmdDetail"; if found,
	 * we select the "CmdDetail" tab and mark/position word.
	 * @param word
	 */
	public void positionCmd(String word) {
		if (word==null || word.length()==0) return;
		Document cmd_doc=detailArea.getDocument();
		Pattern pattern = Pattern.compile(word);
		try {
			String body = cmd_doc.getText(0,cmd_doc.getLength());
			Matcher matcher=pattern.matcher(body);
			if (matcher!= null && matcher.find()) {
				// need to find the 'details' tab index
				int indx=0;
				while (indx<helpTabbedPane.getTabCount() && 
						!helpTabbedPane.getTitleAt(indx).startsWith("Command D"))
					indx++;
				if (indx<helpTabbedPane.getTabCount())
					helpTabbedPane.setSelectedIndex(indx); 
				detailArea.getCaret().setDot(matcher.start());
				detailArea.getCaret().moveDot(matcher.end());
				detailArea.getCaret().setSelectionVisible(true);

				// fixups: reset viewport so chosen word is at top, not bottom
				Element el = cmd_doc.getDefaultRootElement();
				int y = 0;
				for(int j=0; j<el.getElementCount(); j++) {
					if( el.getElement(j).getStartOffset()>=matcher.start() ) {
						y=j-5;
						if(y<0) y=0;
						break;
					}
				}

				int lineHeight = detailArea.getFontMetrics( detailArea.getFont()).getHeight();
				int maxheight = detailArea.getPreferredSize().height;
				y=y*lineHeight;
				if( y>maxheight ) y=maxheight;
				detailScroller.getViewport().setViewPosition(new Point(0,y));
				detailScroller.repaint();
			}
		} catch(Exception ex) {}
	}

	/**
	 * Select CmdDetail tab and position it at command 'cmd'
	 * @param str
	 */
	public static void placeCmd(String cmd) {
		helpTabbedPane.setSelectedIndex(1);
		try {
			detailArea.scrollToReference(cmd); 
		} catch (Exception ex){
			ex.printStackTrace();
		}
	}

	/**
	 * Search 'extendText' to see if given extender class is
	 * already included. If not, add html formated description
	 * strings to 'extendText' and install in 'extenderArea'.
	 * @param xname, the 'PackExtender' class name
	 * @param text
	 */
	public void AddXtendInfo(String xname,String abbrev,String text) {
		if (extendText.indexOf(xname)>0) return; // already here
		extendText.append("<p><font=\"+1\" color=\"red\">"+xname+"</font>  ("+abbrev+") ");
		extendText.append(text);
		extenderArea.setText(extendText.toString());
	}

	/**
	 * Read an info file in XML format, process it and add 
	 * text to open 'textbuf'.
	 * @param filename, assumed in 'Resources/doc/'
	 * @param keyword, what type of element we're after
	 * @param textbuf, open StringBuilder
	 * @return
	 */
	public int loadXMLinfo(String filename,String keyword,StringBuilder textbuf) {
		int count=0;
		String str=null;
		File file=new File(CPBase.getResourceURL("/doc/"+filename).toString());
		DOMParser parser = new DOMParser();
		try {
			parser.parse(file.toString());
		} catch(Exception ex) {
			if (PackControl.consoleCmd!=null) // this happens during startup
				CirclePack.cpb.errMsg("Caught (probably) SAXParseException in "+
						"trying to load script.");
			System.err.println(ex.getMessage());
			ex.printStackTrace(System.err);
			return count;
		}
		org.w3c.dom.Document doc = parser.getDocument();
		NodeList allNodes=doc.getDocumentElement().getChildNodes();
		if (allNodes==null || allNodes.getLength()==0) return count;
		NamedNodeMap map=null;
		Node XType=null;
		for (int k=0;k<allNodes.getLength();k++) {
			Node curNode=allNodes.item(k);
			if (!curNode.getNodeName().equals(keyword) ||
					(map=curNode.getAttributes())==null || 
					(XType=map.getNamedItem("name"))==null) 
				break;

			// packextender information
			if (keyword.equals("packextender")) {
				// name
				textbuf.append("<hr><b>"+(String)XType.getNodeValue()+"</b>");

				// abbreviation
				Node ab=map.getNamedItem("abbrev");
				if (ab!=null)
					textbuf.append("   <i>"+(String)ab.getNodeValue()+"</i>");
				textbuf.append("<br>");

				// cycle through nodes for this extender
				NodeList pXkids=curNode.getChildNodes();
				for (int j=0;j<pXkids.getLength();j++) {
					Node domKid=(Node)(pXkids).item(j);
					NamedNodeMap kmap = domKid.getAttributes();

					// tooltip
					if (domKid.getNodeName().equals("tooltip")) {
						String tip_text = domKid.getFirstChild().getNodeValue();
						textbuf.append("<p allign=\"center\">"+tip_text+"</p>");
					}

					// command
					else if (domKid.getNodeName().equals("cmd")) {
						// must have command name
						if((str=xmlText(kmap,"name"))!=null) {
							textbuf.append("<p align=\"left\" color=\"blue\" size=\"1\">"+str);

							// possible flags/options
							if((str=xmlText(kmap,"flags"))!=null)
								textbuf.append("  <color=\"black\""+str);
							textbuf.append("</p>");

							// look through sub nodes
							NodeList kids=domKid.getChildNodes();
							for (int kk=0;kk<kids.getLength();kk++) {
								Node kidskid=(Node)kids.item(kk);
								if (kidskid.getNodeName().equals("param")) {
									NamedNodeMap kkmap = kidskid.getAttributes();
									textbuf.append("<p>");
									if ((str=xmlText(kkmap,"abbrev"))!=null)
										textbuf.append("<tt>"+str+"</tt>  ");
									if ((str=kidskid.getFirstChild().getNodeValue().trim())!=null)
										textbuf.append(str);
									textbuf.append("</p>");
								}
							} 
						} 
					} // end of 'command' branch
				} // done with nodes of this 'PackExtender'
				textbuf.append("<br>");
				count++;
			} // done if element is 'packextender'

			else if (keyword.equals("command")) {
				// name
				textbuf.append("<hr><b>"+(String)XType.getNodeValue()+"</b>");

				// possible flags/options
				if((str=xmlText(map,"flags"))!=null)
					textbuf.append("  <color=\"black\"<tt>"+str+"</tt>");
				textbuf.append("</p>");

				// look through sub nodes
				NodeList kids=curNode.getChildNodes();
				for (int kk=0;kk<kids.getLength();kk++) {
					Node kidskid=(Node)kids.item(kk);
					if (kidskid.getNodeName().equals("param")) {
						NamedNodeMap kkmap = kidskid.getAttributes();
						textbuf.append("<p>");
						if ((str=xmlText(kkmap,"abbrev"))!=null)
							textbuf.append("<tt>"+str+"</tt>  ");
						if ((str=kidskid.getFirstChild().getNodeValue().trim())!=null)
							textbuf.append(str);
						textbuf.append("</p>");
					}
				} // 
				count++;
			} // done if element is 'command'
			textbuf.append("<br>");
		} // done looping through 'allNodes'
		return count;
	}

	/**
	 * Get the target string from a node; null on failure
	 * @param map
	 * @param target, name of attribute sought
	 * @return, string value, null on failure
	 */
	public String xmlText(NamedNodeMap map,String target) {
		if (map==null || target==null || target.length()==0) return null;
		Node e=map.getNamedItem(target);
		if (e!=null) {
			String str=e.getNodeValue();
			if (str!=null && str.trim().length()!=0) {
				return str.trim();
			}
		}
		return null;
	}

	class IndexHyperlinkListener implements HyperlinkListener
	{
		public void hyperlinkUpdate(HyperlinkEvent e) {
			if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
				StringTokenizer st = new StringTokenizer(e.getDescription(), " ");
				if (st.hasMoreTokens()) {
					String s = st.nextToken();
					HelpHover.placeCmd(s);
				}
			}
		}
	}

	@Override
	public void hyperlinkUpdate(HyperlinkEvent e) {
		// TODO Auto-generated method stub

	}

}
