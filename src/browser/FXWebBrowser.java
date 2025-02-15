package browser;

import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Stack;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.border.EmptyBorder;
import javax.swing.border.SoftBevelBorder;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.events.Event;
import org.w3c.dom.events.EventListener;
import org.w3c.dom.events.EventTarget;

import allMains.CPBase;
import allMains.CirclePack;
import circlePack.PackControl;
import input.CommandStrParser;
import input.TrafficCenter;
import interfaces.IMessenger;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Worker;
import javafx.concurrent.Worker.State;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import packing.PackData;
import packing.ReadWrite;
import util.FileUtil;
import util.MemComboBox;

/**
 * FXWebBrowser is a simple web browser using javaFX.
 * It has integrated functionality to handle CirclePack 
 * scripts *.cps (and *.xmd and *.cmd), *.p and *.q 
 * packing data files, and local and remote files.
 * 
 * @author kens, 
*/
public class FXWebBrowser extends JFrame implements ActionListener {

	private static final long serialVersionUID = 7248705697046383784L;
	
	protected JPanel browserPanel; // The main panel of this frame.
	protected JFXPanel jfxPanel;
	protected MemComboBox urlComboBox; // ComboBox for storing and selecting URLs.
	protected JProgressBar activityIndicator; // Progress bar activated when loading a page.
	protected JButton backButton; // Button to navigate the browser back.
	protected JButton forwardButton; // Button to navigate the browser forward.
	protected JButton refreshButton; // Button to refresh the current page.
// 	protected JButton htmlButton; // Button for directory scriptLister output.
	protected JLabel statusLabel; // Label to display the URL of the current moused over hyperlink.
	protected IMessenger messenger; // The message output interface, received from instantiator.
	protected Stack<URL> backHistory; // The stack of URLs that have been navigated away from.
	protected Stack<URL> forwardHistory; // The stack of URLs that have been navigated away from by the back button.

	// URL's
	protected URL loadedURL; // currently loaded by this instance.
	protected URL webURL; // processed, ready to load into web page
	protected URL dirURL; // hold directory URL for use in history 
	
    public static final String EVENT_TYPE_CLICK = "click";
    public static final String EVENT_TYPE_MOUSEOVER = "mouseover";
    public static final String EVENT_TYPE_MOUSEOUT = "mouseout";

    // for JFXpanel
    protected WebView webView;
    protected Stage webStage;
    protected WebEngine webEngine;
    
	/**
	 * Initialize a new BrowserFrame with no message output functionality and
	 * no persistent storage of URLs.
	 */
	public FXWebBrowser() {
		this(null, null);
	}

	/**
	 * Initialize a new BrowserFrame with persistent storage 
	 * of URLs and message output functionality.
	 * 
	 * @param messenger the <code>IMessenger</code> to use 
	 *   for output, or null for no output
	 * @param historyFile the file path to use for 
	 *   persistent URL storage, or null for no persistent 
	 *   storage
	 */
	public FXWebBrowser(IMessenger messenger, 
			String historyFile) {
		super();

		// No URL is currently loaded. Initialize to use without checking for null.
		loadedURL =null;

		// Get the IMessenger for this instance. If no IMessenger has been passed, initialize
		// an empty instance that outputs nothing. Then, we can use it without checking if it
		// is null.
		this.messenger = messenger;
		if (this.messenger == null) {
			this.messenger = new IMessenger() {
				@Override
				public void sendDebugMessage(String message) {}
				@Override
				public void sendErrorMessage(String message) {}
				@Override
				public void sendOutputMessage(String message) {}
			};
		}

		// Initialize history stacks.
		backHistory = new Stack<URL>();
		forwardHistory = new Stack<URL>();

		// Get the icons for the navigation buttons.
		ImageIcon backIcon = new ImageIcon(getClass().getResource("/Resources/Icons/main/previous.png"));
		ImageIcon forwardIcon = new ImageIcon(getClass().getResource("/Resources/Icons/main/forward.png"));
		ImageIcon refreshIcon = new ImageIcon(getClass().getResource("/Resources/Icons/main/reload.png"));
//		ImageIcon htmlIcon = new ImageIcon(getClass().getResource("/Resources/Icons/GUI/hoverH.png"));

		/*
		 * 
		 * Initialize navigation controls.
		 * 
		 */
		
		backButton = new JButton(backIcon);
		backButton.setMargin(new Insets(0, 0, 0, 0)); // No big blank margins around icons.
		backButton.setFocusable(false); // No dotted selection indicator for buttons.
		backButton.setEnabled(false); // Don't enable until history exists.
		backButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				// The button has been pressed.
				if (!backHistory.empty()) { 
					URL popper=backHistory.pop();
					backHistory.push(popper);
					urlComboBox.add2List(popper.toString(),false);
				}
			}
		});

		forwardButton = new JButton(forwardIcon);
		forwardButton.setMargin(new Insets(0, 0, 0, 0)); // No big blank margins around icons.
		forwardButton.setFocusable(false); // No dotted selection indicator for buttons.
		forwardButton.setEnabled(false); // Don't enable until history exists.
		forwardButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				// The button has been pressed.
				if (!forwardHistory.empty()) {
					URL popper=forwardHistory.pop();
					forwardHistory.push(popper);
					urlComboBox.add2List(popper.toString(),false);
				}
			}
		});

		refreshButton = new JButton(refreshIcon);
		refreshButton.setMargin(new Insets(0, 0, 0, 0)); // No big blank margins around icons.
		refreshButton.setFocusable(false); // No dotted selection indicator for buttons.
		refreshButton.setEnabled(false); // Don't enable until a page is loaded.
		refreshButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				// The button has been pressed. Only continue if the loaded URL is not empty.
				if (loadedURL!=null) {
					URL loadedUrlTemp=null;
					if ((loadedUrlTemp=FileUtil.tryURL(loadedURL.toString()))!=null) {
						// Empty the page.
						loadWebPage("about:blank");
						urlComboBox.add2List(loadedUrlTemp.toString(),false);
					}
				}
			}
		});

/* TODO: I'm removing the htmlButton for now (1/2025)
         because it needs more sophisticated methods 
         in creation to includ 'AboutImage's.
         
		htmlButton = new JButton(htmlIcon);
		htmlButton.setMargin(new Insets(0, 0, 0, 0)); // No big blank margins around icons.
		htmlButton.setFocusable(false); // No dotted selection indicator for buttons.
		htmlButton.setEnabled(true); // Don't enable until history exists.
		htmlButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
//				 The button has been pressed.
				
				// is window showing a directory
				File loaded=new File(loadedURL.getFile());
				if (!loaded.isDirectory())
					return;
				
				// create a tmp local file with directory html
				ScriptLister scriptLister=
					new ScriptLister(loadedURL,0,loaded.getName());
				File listFile=scriptLister.go();
				if (listFile!=null) {
					URL dirURL=null;
					if ((dirURL=FileUtil.tryURL("file:///"+listFile.toString()))!=null) {
						loadAction(2,dirURL);
					}
				}
			}
		});
*/		
		
		File file=new File(historyFile);
		try {
			file.createNewFile(); // finds or creates 
		} catch (IOException iox) {
			CirclePack.cpb.errMsg("failed to open xmd file");
		}
		urlComboBox = new MemComboBox(file);
		urlComboBox.addActionListener(this);
		// Respond to events in the combo box (changed URLs, etc.).
		// Fix the height of the combo box to the same as the buttons, but allow its width total freedom.
		urlComboBox.setMinimumSize(new Dimension(0, backButton.getPreferredSize().height));
		urlComboBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, backButton.getPreferredSize().height));

		// Set up the navigation bar along the top of the frame.
		JPanel navigationPanel = new JPanel();
		navigationPanel.setLayout(new BoxLayout(navigationPanel, BoxLayout.LINE_AXIS));
		navigationPanel.add(backButton);
		navigationPanel.add(forwardButton);
		navigationPanel.add(refreshButton);
		navigationPanel.add(urlComboBox);
//		navigationPanel.add(htmlButton);

		// Set up the current moused over URL display label.
		// WARNING: This block is EXTREMELY sensitive to the order of the code. The label will size differently
		// for reasons I don't understand if this code runs in slightly different orders.
		statusLabel = new JLabel("INCLUDED_FOR_SIZING"); // Temporarily make not empty to get correct size.
		statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN)); // Make the font plain (and especially not bold).
		statusLabel.setBorder(BorderFactory.createCompoundBorder(
				new EmptyBorder(2, 2, 2, 2), new SoftBevelBorder(SoftBevelBorder.LOWERED)));
		statusLabel.setPreferredSize(statusLabel.getPreferredSize()); // Get and set preferred size to force it to size height according to current text.
		statusLabel.setMinimumSize(new Dimension(0, statusLabel.getPreferredSize().height)); // Fix height, but allow width total freedom.
		statusLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, statusLabel.getPreferredSize().height));
		statusLabel.setText(null); // Make empty again for display.

		// Set up the progress indicator. It should bounce around while a page is loading,
		// and be blank when nothing is occurring.
		activityIndicator = new JProgressBar();
		activityIndicator.setBorder(BorderFactory.createCompoundBorder(
				new EmptyBorder(2, 2, 2, 2), new SoftBevelBorder(SoftBevelBorder.LOWERED)));
		// Constrain the height to the height of the status label above.
		activityIndicator.setMaximumSize(new Dimension(activityIndicator.getMaximumSize().width, statusLabel.getMaximumSize().height));

		// Create the status panel along the bottom of the frame.
		JPanel statusPanel = new JPanel();
		statusPanel.setLayout(new BoxLayout(statusPanel, BoxLayout.LINE_AXIS));
		statusPanel.add(statusLabel);
		statusPanel.add(activityIndicator);

		// create the webViewBrowser, and JFXPanel.
		jfxPanel=new JFXPanel();
	      Platform.runLater(new Runnable() {
	            @Override
	            public void run() {
	            	webView=new WebView();
	            	webEngine=webView.getEngine();
	            	jfxPanel.setScene(new Scene(webView));
	            	webView.setVisible(true);
	            	initListener();
	            }
	        });
				
		// Create the main browser panel to pack into this frame.
		browserPanel = new JPanel();
		browserPanel.setLayout(new BoxLayout(browserPanel, BoxLayout.PAGE_AXIS));
		browserPanel.add(navigationPanel);
		browserPanel.add(jfxPanel);
		browserPanel.add(statusPanel);

		jfxPanel.addPropertyChangeListener(new PropertyChangeListener() {
			@Override
			public void propertyChange(PropertyChangeEvent e) {
				if (e.getPropertyName().equals("page") || e.getPropertyName().equals("editorKit")) {
					// New page has been set. Disable the activity indicator.
					activityIndicator.setIndeterminate(false);
				}
			}
		});
		
		// Set up this frame, but don't display it until asked.
		this.setTitle("CirclePack Web Browser");
		this.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
		this.setPreferredSize(new Dimension(600, 400));
		this.add(browserPanel);
		jfxPanel.setVisible(true);
		this.pack();
	}
	
	/**
	 * Process the given URL, which should be valid.
	 * Classify the type of file, take appropriate
	 * action for packings, scripts, directories (2),
	 * files and sites (1), then call for loading if 
	 * type 1 or 2. Types are these:
	 *   * 0 for failure
	 *   * -1: is already loaded
	 *   * 1: link to be displayed
	 *   * 2: directory to display
	 *   * 3: *.cps script (or *.xmd or *.cmd)
	 *   * 4: *.p, *.q packing
	 * Also call to load the file and manage activity
	 * indicator.
	 * @param url URL,
	 * @return int, 0 on failure
	 */
	protected int processLink(URL url) {
		int action=0;
		
		// Return if the URL is already loaded.
		if (url == null || url.equals(loadedURL)) 
			return 0;
		activityIndicator.setIndeterminate(true);

		// clear any previous directory URL;
		//   gets set iff url is a directory
		//   and *.html was successfully created.
		dirURL=null; 
		
		boolean isRemote=false;
		File webFile=null;
		
		// ========== 
		webURL=url; 
       	String protocol=url.getProtocol().toLowerCase();
       	if (protocol.startsWith("http"))
   			isRemote=true;
		webFile=new File(webURL.getFile());
		
		// remote will already be formated as directory (??)
		if (webFile!=null && !isRemote &&
				webFile.isDirectory()) {
			// Recall that 'loadDirectory' will reset 'webURL'
			if (loadDirectory(webURL)==0) {
				CirclePack.cpb.errMsg("Failed creation of directory *.html");
				activityIndicator.setIndeterminate(false);
				return 0;
			}
			else {
				dirURL=FileUtil.tryURL(url.toString());
				action=2;
			}
		}

		// ======= If the URL is an *.cps (or *.xmd or *.cmd) script:
		String chkurl=webURL.toExternalForm().toLowerCase().trim();
		if (action==0 && (chkurl.endsWith(".cps") ||
				chkurl.endsWith(".xmd") ||
				chkurl.endsWith(".cmd"))) {
			if (loadScript(webURL)!=0)
				action=3;
			else {
				activityIndicator.setIndeterminate(false);
				return 0;
			}
		}
		
		// ================= If the URL is a *.p or *.q packing file:
		else if (action==0 &&
				(chkurl.endsWith(".p") || 
				chkurl.endsWith(".q"))) {
			if (loadPacking(webURL)!=0)
				action=4;
			else {
				activityIndicator.setIndeterminate(false);
				return 0;
			}
		}
		
		// ================= If URL is a page to load
		if (action==0 && webURL!=null)
			action=1;

		// only 1 and 2 require something to be loaded
		if ((action!=1 && action!=2)) {
			activityIndicator.setIndeterminate(false);
			return action;
		}
		
		int rslt=0;
		if (action>0) {
			rslt=loadAction(action,webURL);
		}
		activityIndicator.setIndeterminate(false);
		if (rslt>0)
			return action;
		return rslt;
	}
	
	/**
	 * called only for loading web and directory 
	 * instances. Update 'loadedURL' and add urls 
	 * to history. New web pages (action==1) also
	 * clear the forward list. 
	 * @param action int
	 * @param url URL
	 * @return 0 on failure
	 */
	public int loadAction(int action,URL url) {
		int rslt=0;
		if ((rslt=loadPage(url))==0) // failure to load 
			return 0;

		// Note: in directory case, url is the tmp 
		//   html file, but the directory itself 
		//   becomes 'loadedURL' and so it can go 
		//   into the history
		loadedURL=FileUtil.tryURL(url.toString());
		if (action==2) {
			loadedURL=FileUtil.tryURL(dirURL.toString());
		}
		return rslt; 
	}

	/**
	 * This loads the page, sets 'loadedURL'
	 * @param newURL URL
	 * @return 0 on failure
	 */
	public int loadPage(URL newURL) {
		refreshButton.setEnabled(true);
		
		// loading occurs on the javafx thread 
		loadWebPage(newURL.toString());

		loadedURL = FileUtil.tryURL(newURL.toString());
		if (loadedURL==null) 
			return 0;
		return 1;
	}

	/**
	 * load webView on the javafx thread
	 * @param url String
	 */
    public void loadWebPage(String url) {
        Platform.runLater(() -> {
        	if (FileUtil.isLocal(url)) {
           		String newurl=FileUtil.parseURL(url).toString();
           		webEngine.load(newurl);
           	}
        	else
        		webEngine.load(url);
        	webView.setVisible(true);
        }); 
    }

	/**
	 * Prepare a directory in *.html form to load;
	 * 'url' is already confirmed and is a directory.
	 * @param url URL
	 * @return int, 0 on failure
	 */
	public int loadDirectory(URL url) {
		webURL=url;
		webURL = BrowserUtilities.pageForDirectory(url);
		if (webURL == null) {
			CirclePack.cpb.errMsg("failed to format directory as *.html");
			return 0;
		}
		
		// webURL is the *html page ready to load
		return 2;
	}

	/**
	 * This should only be called after checking
	 * that this is a *cps, *xmd, or *cmd file.
	 * Loading a script does not require loading 
	 * the web page and does not affect 'loadedURL'
	 * @param url URL
	 * @return int, 0 on failure
	 */
	public int loadScript(URL url) {
		webURL=null;
		URL tmpurl=FileUtil.parseURL(url.toString());
		
		if ((tmpurl)==null)
			return 0;
				
		// Prompt the user to confirm loading a new script:
		String confirmDialogText = "Load new script?"; 
		int result = JOptionPane.showConfirmDialog(null, 
				confirmDialogText, "Confirm", JOptionPane.YES_NO_OPTION);
		if (result != JOptionPane.YES_OPTION) 
			return 0;
		
		// If a local file:
		if (url.toString().startsWith("file")) {
			// Since this is a local file, there is no need to thread.
			
			// Load the script.
			boolean gotNewScript = false;
			if (CPBase.scriptManager.getScript(url.toString(),url.toString(), true) > 0) 
				gotNewScript = true;
			if (gotNewScript) 
				PackControl.scriptHover.stackScroll.getViewport().setViewPosition(new Point(0, 0));

			// Update the cursor and activity indicator and exit.
			if (gotNewScript)
				return 3;
			else
				return 0;
		}

		// The web URL for the script, thread the download.
		else {
			new Thread() {
				public void run() {
					/*
					 * The initial implementation passed the 
					 * URL to getScript(String, boolean) for downloading. 
					 * The problem is that this caused 
					 * getScript(String, boolean) to run in a thread other 
					 * than the Swing Event Dispatch Thread, which violates 
					 * the Swing Single Thread Rule and caused crashes and 
					 * nondeterministic behavior.
					 *
					 * The solution is to thread the download up 
					 * front and save it as a temporary file.
					 * If the download is successful, we'll send 
					 * the URL of the local temporary file
					 * to getScript(String, boolean) by pushing 
					 * the code to the end of the Swing EventQueue.
					 */

					// Download a temporary copy of the web script.
					File tempScriptFile = null;
					try {
						tempScriptFile = BrowserUtilities.downloadTemporaryFile(url.toString());
					} catch (IOException e) {
						// Notify CirclePack of the error and return from thread.
						EventQueue.invokeLater(new Runnable() {
							public void run() {
								messenger.sendErrorMessage("Could not download file " + url.toString() + ".");
								return;	
							}
						});
						// Do not continue after exception.
						return;
					}

					// Pass off the downloaded temporary script 
					// file to getScript(String, boolean). Push it to 
					// the end of the EventQueue because it might make 
					// extensive Swing GUI calls. Do any GUI updating and 
					// bookkeeping, then return.
					final String temporaryScriptString = tempScriptFile.getPath();
					EventQueue.invokeLater(new Runnable() {
						public void run() {
							boolean newScript = false;
							if (CPBase.scriptManager.getScript(temporaryScriptString, url.toString(), true) > 0) 
								newScript = true;
							if (newScript) 
								PackControl.scriptHover.stackScroll.getViewport().
							setViewPosition(new Point(0, 0));
							return;			
						}
					});
				}
			}.start();
			return 3;
		} // end of else thread
	} // end of processing for *.cps (or *.xmd or *.cmd) files
	
	/**
	 * Loading a packing does not require loading 
	 * the web page and does not affect 'loadedUrl'
	 * @param url URL
	 * @return int, 0 on failure
	 */
	public int loadPacking(URL url) {
		
		if (url==null)
			return 0;
		URL targetFile=null;
		if ((targetFile=FileUtil.tryURL(url.toString()))==null)
			return 0;
		webURL=null;
		
		// Prompt the user to confirm loading the packing:
		String confirmDialogText = "Load new packing into p" + 
				CirclePack.cpb.getActivePackData().packNum + "?";
		int result = JOptionPane.showConfirmDialog(null, 
				confirmDialogText, "Confirm", JOptionPane.YES_NO_OPTION);

		// If the user has confirmed the loading, start loading 
		// the packing.
		if (result != JOptionPane.YES_OPTION) 
			return 0;
		
		// If the URL is a file URL, don't need to thread
		if (url.toString().toLowerCase().startsWith("file")) {
			try {
				BufferedReader bufferedReader = new BufferedReader(
						new FileReader(new File(targetFile.getFile())));
				if (url.toString().toLowerCase().endsWith(".p")) 
					TrafficCenter.cmdGUI("cleanse");
				PackData tmppd=CirclePack.cpb.getActivePackData();
				ReadWrite.readpack(bufferedReader,tmppd,targetFile.getFile());
				if (CirclePack.cpb.getActivePackData().getDispOptions != null)
					CommandStrParser.jexecute(CirclePack.cpb.getActivePackData(), "disp -wr");
				else 
					TrafficCenter.cmdGUI("disp -w -c");
			} catch (FileNotFoundException e) {
				// Notify CirclePack of the error.
				this.messenger.sendErrorMessage("Failed to open " + url.toString() + ".");
				return 0;
			}
			return 4;			
		}

		// The URL is a web URL for p, q. Thread the download.
		else {
			new Thread() {
				public void run() {
					// Download a temporary copy of the web packing.
					final File localPackingFile;
					try {
						localPackingFile = BrowserUtilities.downloadTemporaryFile(url.toString());
					} catch (IOException e) {
						// Notify CirclePack of the error and return from thread.
						EventQueue.invokeLater(new Runnable() {
							public void run() {
								messenger.sendErrorMessage("Could not download file " + url.toString() + ".");
								return;			
							}
						});

						// Do not continue after exception.
						return;
					}

					// Pass off the downloaded temporary packing file 
					// to readpack(BufferedReader, String). Push it to 
					// the end of the EventQueue because it might make 
					// Swing GUI calls. Do any GUI updating and bookkeeping, 
					// then return.
					final BufferedReader bufferedReader;
					try {
						bufferedReader = new BufferedReader(new FileReader(localPackingFile));
					} catch (FileNotFoundException e) {
						messenger.sendErrorMessage("Failed to open " + url.toString() + ".");
						return;
					} 

					EventQueue.invokeLater(new Runnable() {
						public void run() {
							if (localPackingFile.toString().toLowerCase().endsWith(".p")) 
								TrafficCenter.cmdGUI("cleanse");
							PackData tmppd=CirclePack.cpb.getActivePackData();
							ReadWrite.readpack(bufferedReader,tmppd,localPackingFile.toString());
							if (CirclePack.cpb.getActivePackData().getDispOptions != null)
								CommandStrParser.jexecute(CirclePack.cpb.getActivePackData(), "disp -wr");
							return;
						}
					});
				}
			}.start();
			return 1;
		} // end of loading from web
	}
	



	/*
	 * This next bit of code should disable the activity 
	 * indicator whenever a page loads or fails to load. 
	 * There should be no need to do it manually.
	 */

	/**
	 * Monitor combo box events to respond to changes in the current URL. 
	 * If the user enters or selects a new or different URL from the combo box, 
	 * this listener will navigate the browser to it.
	 * 
	 * @author kens
	 * @author Alex Fawkes
	 */
	public void actionPerformed(ActionEvent e) { 
		if (e.getActionCommand().equals("comboBoxEdited")
				|| e.getActionCommand().equals("comboBoxChanged")) {

			// Check if the URL is invalid or already loaded. 
			// This can happen when a hyperlink is clicked,
			// changing the current selection of the combo box 
			// and triggering this event.
			String urlString = (String)(urlComboBox.getSelectedItem());
			
			URL url=FileUtil.parseURL(urlString);
			if (url==null || url.equals(loadedURL))
				return; // url.getPath();
			URL cur_loadedURL=null;
			if (loadedURL!=null)
				cur_loadedURL=FileUtil.parseURL(loadedURL.toString());
			
			int rslt=processLink(url);
			
			// if we load a script or packing, we don't want
			//    to display that in combobox.
			if (rslt>0 && rslt!=1 && rslt!=2) {
				if (cur_loadedURL!=null)
					urlComboBox.setURLstring(cur_loadedURL.toString());
				else
					urlComboBox.setURLstring(" ");
			}
			
			// if successful with site or directory, adjust histories
			if ((rslt==1 || rslt==2) && cur_loadedURL!=null) {
				
				boolean back_reload=false;
				boolean fore_reload=false;
				// Is this a reload from back/fore? 
				if (!backHistory.empty()) {
					URL popped=backHistory.pop();
					if (!popped.equals(loadedURL)) {
						backHistory.push(popped);
						backHistory.push(cur_loadedURL);
					}
					else 
						back_reload=true;
				}
				else {
					backHistory.push(cur_loadedURL);
					backButton.setEnabled(true);
				}
					
				if (!forwardHistory.empty()) {
					URL popped=forwardHistory.pop();
					if (!popped.equals(loadedURL))  
						forwardHistory.push(popped);
					else
						fore_reload=true;
				}
				
				// are we shifting one way or another
				if (fore_reload) { // move fore to back
					backHistory.push(cur_loadedURL);
					backButton.setEnabled(true);
				}
				if (back_reload) { // move from back to fore
					forwardHistory.push(cur_loadedURL);
					forwardButton.setEnabled(true);
				}
				
				// a new site should wipe out forward history
				if (rslt==1 && !back_reload && !fore_reload) {
					forwardHistory.clear();
					forwardButton.setEnabled(false);
				}
			}
				
			if (backHistory.empty())
				backButton.setEnabled(false);
			if (forwardHistory.empty())
				forwardButton.setEnabled(false);
			
			if (rslt>0)  // success
				urlComboBox.setSuccess();
			else if (rslt==0)
				urlComboBox.setFailure();
			else 
				urlComboBox.setNeutral();
		}
	}
	
	/**
	 * This is a hyperlink listener for javaFX WebView.
	 */
	protected void initListener() {
		webEngine.getLoadWorker().stateProperty().addListener(new ChangeListener<State>() {
			@Override
			public void changed(ObservableValue ov, State oldState, State newState) {
				if (newState == Worker.State.SUCCEEDED) {
					EventListener listener = new EventListener() {
						@Override
						public void handleEvent(Event ev) {
							String domEventType = ev.getType();
							if (domEventType.equals(EVENT_TYPE_CLICK)) {
								String href = ((Element)ev.getTarget()).getAttribute("href");
								mouseWebClick(href);
							} 
							else if (domEventType.equals(EVENT_TYPE_MOUSEOVER)) {
								String href = ((Element)ev.getTarget()).getAttribute("href");
								mouseWebEnter(href);
							} 
							else if (domEventType.equals(EVENT_TYPE_MOUSEOUT)) {
								String href = ((Element)ev.getTarget()).getAttribute("href");
								mouseWebOut(href);
							}
						}
					};

					Document doc = webView.getEngine().getDocument();
					NodeList nodeList = doc.getElementsByTagName("a");
					for (int i = 0; i < nodeList.getLength(); i++) {
						((EventTarget) nodeList.item(i)).addEventListener(EVENT_TYPE_CLICK, listener, false);
						((EventTarget) nodeList.item(i)).addEventListener(EVENT_TYPE_MOUSEOVER, listener, false);
						((EventTarget) nodeList.item(i)).addEventListener(EVENT_TYPE_MOUSEOUT, listener, false);
					}
				}
			}
		});
	}
	
	/**
	 * WebView hypertext listener calls this if the
	 * mouse is clicked on a link.
	 * @param href String
	 * @return int, 0 on failure
	 */
	public int mouseWebClick(String href) {
		URL theURL=null;

		// if this link is from remote page, may
		//    need to append it to loadedURL.
		if (loadedURL!=null) {
			String protocol=loadedURL.getProtocol().toLowerCase();
			if (protocol.startsWith("http")) {
				try {
					String str=loadedURL.toString()+"/"+href;
					theURL=new URL(str);
				} catch (MalformedURLException e) {
					return 0;
				}
			}
		}
		else if ((theURL=FileUtil.parseURL(href))==null)
			return 0;
		int rslt=processLink(theURL);
		if (rslt==1 || rslt==2)
			urlComboBox.add2List(theURL.toString(),false);
		return rslt;
	}
	
	/**
	 * TODO: WebView hypertext listener calls this
	 * when mouse enters a link.
	 * @param href
	 * @return
	 */
	public int mouseWebEnter(String href) {
		statusLabel.setText(href);
		return 1;
	}
	
	/**
	 * TODO: WebView hypertext listener calls this
	 * when mouse leaves a link.
	 * @param href
	 * @return
	 */
	public int mouseWebOut(String href) {
		statusLabel.setText(null);
		return 1;
	}

}
