package browser;

import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
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
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;

import allMains.CPBase;
import allMains.CirclePack;
import allMains.ScriptLister;
import circlePack.PackControl;
import input.CommandStrParser;
import input.TrafficCenter;
import interfaces.IMessenger;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Worker.State;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import packing.PackData;
import packing.ReadWrite;
import util.MemComboBox;

/**
 * BrowserFrame is a simple web browser. It has 
 * functionality to load CirclePack scripts,
 * *cps (or deprecated *.xmd and *.cmd), and also 
 * *p, *q packing data files, web/file pages, and 
 * directories. 
 * 
 * @author Alex Fawkes
 * @author kens, JavaFX introduced 12/2024
 */
public class BrowserFrame extends JFrame implements ActionListener {
	// Regenerate this every time fields or methods change.
	private static final long serialVersionUID = 7248705697046383784L;
	
	protected JPanel browserPanel; // The main panel of this frame.
	protected MemComboBox urlComboBox; // ComboBox for storing and selecting URLs.
	protected JProgressBar activityIndicator; // Progress bar activated when loading a page.
	protected JButton backButton; // Button to navigate the browser back.
	protected JButton forwardButton; // Button to navigate the browser forward.
	protected JButton refreshButton; // Button to refresh the current page.
	protected JButton htmlButton; // calls for creation of Script List for a directory
	protected JLabel statusLabel; // Label to display the URL of the current moused over hyperlink.
	protected IMessenger messenger; // The message output interface, received from instantiator.
	protected Stack<String> backHistory; // stack of URLs that have been navigated away from.
	protected Stack<String> forwardHistory; // stack of URLs navigated away from by the back button.

	// URL's
	protected String loadedUrl; // currently loaded by this instance.
	protected String enteredUrl; // actively begin processed
	protected String webUrl; // processed, ready to load into web page

	protected JFXPanel webViewPanel;
    protected WebEngine webEngine;
    
    public int webState; // reflect state of web page:
    	// -1: nothing loaded
    	//  0: load failure
    	//  1: web page
    	//  2: file
    	//  3: directory
	
	/**
	 * Initialize a new BrowserFrame with no message output 
	 * functionality and no persistent storage of URLs.
	 */
	public BrowserFrame() {
		this(null, null);
	}

	/**
	 * Initialize a new BrowserFrame with persistent 
	 * storage of URLs and message output functionality.
	 * 
	 * @param messenger IMessenger, to use for output, or
	 *    null for no output
	 * @param historyFile String, file path to use for 
	 *    persistent URL storage, or null for no 
	 *    persistent storage
	 */
	public BrowserFrame(IMessenger messenger, String historyFile) {
		super();

		// No URL is currently loaded. Initialize to use without checking for null.
		this.loadedUrl = "";
		webState=-1;

		// Get the IMessenger for this instance. If no 
		//   IMessenger has been passed, initialize
		//   an empty instance that outputs nothing. Then 
		//   we can use without checking if it's null.

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
		backHistory = new Stack<String>();
		forwardHistory = new Stack<String>();

		// Get the icons for the navigation buttons.
		ImageIcon backIcon = new ImageIcon(getClass().getResource("/Resources/Icons/main/previous.png"));
		ImageIcon forwardIcon = new ImageIcon(getClass().getResource("/Resources/Icons/main/forward.png"));
		ImageIcon refreshIcon = new ImageIcon(getClass().getResource("/Resources/Icons/main/reload.png"));
		ImageIcon htmlIcon = new ImageIcon(getClass().getResource("/Resources/Icons/GUI/hoverH.png"));
		
		/* =========== Initialize navigation controls ======== */
		backButton = new JButton(backIcon);
		backButton.setMargin(new Insets(0, 0, 0, 0)); // No big blank margins around icons.
		backButton.setFocusable(false); // No dotted selection indicator for buttons.
		backButton.setEnabled(false); // Don't enable until history exists.
		backButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				// The button has been pressed.
				if (!backHistory.empty()) {
					// There's something in the back history. 
					// Push the loaded URL onto the forward 
					// history and enable the forward button. 
					// Then load the top URL in the back history.
					forwardHistory.push(loadedUrl);
					forwardButton.setEnabled(true);
					loadPage(1,backHistory.pop());
				}

				// If there's nothing left in the back history, disable the button.
				if (backHistory.empty()) 
					backButton.setEnabled(false);
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
					// There's something in the forward history. Push the loaded URL
					// onto the back history and enable the back button. Then load
					// the top URL in the forward history.
					backHistory.push(loadedUrl);
					backButton.setEnabled(true);
					loadPage(1,forwardHistory.pop());
				}

				// If there's nothing left in the forward history, disable the button.
				if (forwardHistory.empty()) 
					forwardButton.setEnabled(false);
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
				if (loadedUrl.length()>0 && 
						!loadedUrl.trim().isEmpty()) {
					// Reload the page.
					reLoadPage();
				}
			}
		});

		File file=new File(historyFile);
		try {
			file.createNewFile(); // finds or creates 
		} catch (IOException iox) {
			CirclePack.cpb.errMsg("failed to open cps (or xmd or cmd) file");
		}
		urlComboBox = new MemComboBox(file);
		urlComboBox.addActionListener(this);
		// Respond to events in the combo box 
		// (changed URLs, etc.). Fix the height of 
		// the combo box to the same as the buttons, 
		// but allow its width total freedom.
		urlComboBox.setMinimumSize(new Dimension(0, backButton.getPreferredSize().height));
		urlComboBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, backButton.getPreferredSize().height));

		htmlButton = new JButton(htmlIcon);
		htmlButton.setMargin(new Insets(0, 0, 0, 0)); // No big blank margins around icons.
		htmlButton.setFocusable(false); // No dotted selection indicator for buttons.
		htmlButton.setEnabled(true); // Don't enable until history exists.
		htmlButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				// The button has been pressed.
				
				// is window showing a directory
				if (!BrowserUtilities.URLisDirectory(loadedUrl))
					return;
				File dummy=new File(loadedUrl);
				ScriptLister scriptLister=
					new ScriptLister(loadedUrl,0,dummy.getName());
				File listFile=scriptLister.go();
				if (listFile!=null) {
					loadPage(5,"file:/"+listFile.toString());
				}
			}
		});

		// Set up the navigation bar along the top of the frame.
		JPanel navigationPanel = new JPanel();
		navigationPanel.setLayout(new BoxLayout(navigationPanel, BoxLayout.LINE_AXIS));
		navigationPanel.add(backButton);
		navigationPanel.add(forwardButton);
		navigationPanel.add(refreshButton);
		navigationPanel.add(urlComboBox);
		navigationPanel.add(htmlButton);

        // Create WebView panel
        webViewPanel = new JFXPanel();
        Platform.runLater(() -> {
            WebView webView = new WebView();
            webEngine = webView.getEngine();
            webViewPanel.setScene(new Scene(webView));
        });

		// Set up the current moused over URL display label.
		// WARNING: This block is EXTREMELY sensitive 
		// to the order of the code. The label will size 
		// differently for reasons I don't understand if 
		// this code runs in slightly different orders.
		statusLabel = new JLabel("INCLUDED_FOR_SIZING"); // Temporarily make not empty to get correct size.
		statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN)); // Make the font plain (and especially not bold).
		statusLabel.setBorder(BorderFactory.createCompoundBorder(
				new EmptyBorder(2, 2, 2, 2), new SoftBevelBorder(SoftBevelBorder.LOWERED)));
		statusLabel.setPreferredSize(statusLabel.getPreferredSize()); // Get and set preferred size to force it to size height according to current text.
		statusLabel.setMinimumSize(new Dimension(0, statusLabel.getPreferredSize().height)); // Fix height, but allow width total freedom.
		statusLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, statusLabel.getPreferredSize().height));
		statusLabel.setText(null); // Make empty again for display.

		// Set up the progress indicator. It should 
		// bounce around while a page is loading, and 
		// be blank when nothing is occurring.
		activityIndicator = new JProgressBar();
		activityIndicator.setBorder(BorderFactory.createCompoundBorder(
				new EmptyBorder(2, 2, 2, 2), new SoftBevelBorder(SoftBevelBorder.LOWERED)));
		// Constrain the height to the height of the status label above.
		activityIndicator.setMaximumSize(new Dimension(activityIndicator.getMaximumSize().width, statusLabel.getMaximumSize().height));

		/*
		 * This next bit of code should disable the activity 
		 * indicator whenever a page loads or fails to load. 
		 * There should be no need to do it manually.
		 */
//		pageDisplayPane.addPropertyChangeListener(new PropertyChangeListener() {
//			@Override
//			public void propertyChange(PropertyChangeEvent e) {
//				if (e.getPropertyName().equals("page") || e.getPropertyName().equals("editorKit")) {
					// New page has been set. Disable the activity indicator.
//					activityIndicator.setIndeterminate(false);
//				}
//			}
//		});

		// Create the status panel along the bottom of the frame.
		JPanel statusPanel = new JPanel();
		statusPanel.setLayout(new BoxLayout(statusPanel, BoxLayout.LINE_AXIS));
		statusPanel.add(statusLabel);
		statusPanel.add(activityIndicator);

		// Create the main browser panel to pack into 
		// this frame.
		browserPanel = new JPanel();
		browserPanel.setLayout(new BoxLayout(browserPanel, BoxLayout.PAGE_AXIS));
		browserPanel.add(navigationPanel);
		browserPanel.add(webViewPanel);
		browserPanel.add(statusPanel);
		browserPanel.add(statusPanel);

		// Set up this frame, but don't display it.
		this.setTitle("Web Browser");
		this.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
		this.setPreferredSize(new Dimension(600, 400));
//		this.setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
//		this.add(navigationPanel);
		this.add(browserPanel);

		this.pack();
	}

	/**
	 * NavigationHyperlinkListener will navigate the 
	 * browser to a URL or load script or packing in 
	 * response to user clicking a hyperlink in the 
	 * browser. Either a script or packing is read in 
	 * or the browser navigates to the represented URL.
	 * 
	 * @author kens
	 * @author Alex Fawkes
	 */
	protected class NavigationHyperlinkListener 
		implements HyperlinkListener {
		@Override
		public void hyperlinkUpdate(HyperlinkEvent e) {
			if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
				// A hyperlink has been activated, generally by clicking from the user.
				if (loadedUrl.length()>0 &&
						!loadedUrl.trim().isEmpty()) {
					// Push the loaded URL onto the back history and enable the back button.
					backHistory.push(loadedUrl);
					backButton.setEnabled(true);

					// Clear the forward history and disable 
					// the forward button. Navigating to a 
					// new page means there is nothing 
					// "forward" from it.
					forwardHistory.clear();
					forwardButton.setEnabled(false);
				}

				// Load the URL represented by the 
				//   activated hyperlink.
				processURL(e.getURL().toString().replace("%20", " "));
			}
		}
	}

	/**
	 * StatusHyperlinkListener updates the current URL 
	 * status label in the browser to whatever the user 
	 * is currently mousing over. If the user is not 
	 * currently mousing over a hyperlink, the label 
	 * will be cleared.
	 * 
	 * @author kens
	 * @author Alex Fawkes
	 */
	protected class StatusHyperlinkListener 
		implements HyperlinkListener {
		@Override
		public void hyperlinkUpdate(HyperlinkEvent e) {
			if (e.getEventType() == HyperlinkEvent.EventType.ENTERED) {
				// A hyperlink has been entered. Get the URL 
				// and set the status label to its string 
				// representation.
				statusLabel.setText(e.getURL().toString().replace("%20", " "));
			} else if (e.getEventType() == HyperlinkEvent.EventType.EXITED) {
				// A hyperlink has been exited. Clear the 
				// status label.
				statusLabel.setText(null);
			}
		}
	}

	/**
	 * Preprocess url specified by the passed String object. 
	 * If there's a file to load into the web page, whether
	 * local or on the web, set 'webUrl'
	 * Return int value depending on outcome:
	 * * 0 for failure
	 * * -1: is already loaded
	 * * 1: web page to be displayed (goes into list)
	 * * 2: directory to display (goes into list)
	 * * 3: *.cps script (or *.xmd or *.cmd)
	 * * 4: *.p, *.q packing
	 * @param url String, representation of URL to process
	 * @return int, 0 on failure
	 */
	protected int processURL(String url) {
		webUrl=null;

		// Return if URL is null.
		if (url == null || url.length()==0) 
			return 0;
		
		// Return if the URL is invalid.
		final String enteredUrl = BrowserUtilities.parseURL(url);
		if (enteredUrl == null) // failure 
			return 0;

		// Return if the URL is already loaded.
		if (enteredUrl.equals(loadedUrl)) 
			return -1;
		
		webUrl=enteredUrl;		

		// ========== if URL is a directory
		if (BrowserUtilities.URLisDirectory(webUrl))
			return 2;

		// ======= If the URL is an *.cps (or *.xmd or *.cmd) script:
		if (enteredUrl.toLowerCase().endsWith(".cps") ||
				enteredUrl.toLowerCase().endsWith(".xmd") ||
				enteredUrl.toLowerCase().endsWith(".cmd")) 
			return 3;

		// ================= If the URL is a *.p or *.q packing file:
		else if (enteredUrl.toLowerCase().endsWith(".p") || 
				enteredUrl.toLowerCase().endsWith(".q")) 
			return 4;
		
		// ================= If URL is a page to load
		else 
			return 2;
		
	}
	
	/**
	 * Prepare a directory in *.html form to load;
	 * 'url' is already confirmed and is a directory.
	 * Do not put list for past loads.
	 * @param urlString String
	 * @return int, 0 on failure
	 */
	public int loadDirectory(String urlString) {

		File directory = new File(urlString);

		String dirPageText = BrowserUtilities.pageForDirectory(directory.getPath());
		if (dirPageText == null) 
			return 0;			
		
		// this is the html page ready to load
		webUrl=dirPageText;
		return 2;
	}

	/**
	 * Loading a script does not require loading 
	 * the web page and does not affect 'loadedUrl'
	 * @param urlString String
	 * @return int, 0 on failure
	 */
	public int loadScript(String urlString) {
		activityIndicator.setIndeterminate(true);
		webUrl=null;
		
		// If a local file:
		if (urlString.toLowerCase().startsWith("file")) {
			// Since this is a local file, there is no need to thread.
			
			// Load the script.
			boolean gotNewScript = false;
			if (CPBase.scriptManager.getScript(urlString,urlString, true) > 0) 
				gotNewScript = true;
			if (gotNewScript) 
				PackControl.scriptHover.stackScroll.getViewport().setViewPosition(new Point(0, 0));

			// Update the cursor and activity indicator and exit.
//			pageDisplayPane.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
			activityIndicator.setIndeterminate(false);
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
						tempScriptFile = BrowserUtilities.downloadTemporaryFile(urlString);
					} catch (IOException e) {
						// Notify CirclePack of the error and return from thread.
						EventQueue.invokeLater(new Runnable() {
							public void run() {
								messenger.sendErrorMessage("Could not download file " + enteredUrl + ".");
								
								// Update the cursor and activity indicator and exit.
//								pageDisplayPane.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
								activityIndicator.setIndeterminate(false);
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
							if (CPBase.scriptManager.getScript(temporaryScriptString, urlString, true) > 0) 
								newScript = true;
							if (newScript) 
								PackControl.scriptHover.stackScroll.getViewport().
							setViewPosition(new Point(0, 0));

							// Update the cursor and activity indicator and exit.
//							pageDisplayPane.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
							activityIndicator.setIndeterminate(false);
							return;			
						}
					});
				}
			}.start();
			
//			pageDisplayPane.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
			activityIndicator.setIndeterminate(false);
			return 3;
		} // end of else thread
	} // end of processing for *.cps (or *.xmd or *.cmd) files
	
	/**
	 * Loading a packing does not require loading 
	 * the web page and does not affect 'loadedUrl'
	 * @param urls String
	 * @return int, 0 on failure
	 */
	public int loadPacking(String urls) {
		
		String urlString=BrowserUtilities.parseURL(urls);
		if (urlString==null)
			return 0;
		String targetFile;
		try {
			targetFile = new URL(urlString).getFile();
		} catch (MalformedURLException e) {
			return 0;
		}
		
		activityIndicator.setIndeterminate(true);
		webUrl=null;
		
		// Prompt the user to confirm loading the packing:
		String confirmDialogText = "Load into pack " + 
				CirclePack.cpb.getActivePackData().packNum + "?";
		int result = JOptionPane.showConfirmDialog(null, 
				confirmDialogText, "Confirm", JOptionPane.YES_NO_OPTION);

		// If the user has confirmed the loading, start loading 
		// the packing.
		if (result != JOptionPane.YES_OPTION) 
			return 0;
		
		// If the URL is a file URL, don't need to thread
		if (urlString.toLowerCase().startsWith("file")) {
			try {
				BufferedReader bufferedReader = new BufferedReader(
						new FileReader(targetFile));
				if (urlString.toLowerCase().endsWith(".p")) 
					TrafficCenter.cmdGUI("cleanse");
				PackData tmppd=CirclePack.cpb.getActivePackData();
				ReadWrite.readpack(bufferedReader,tmppd,targetFile);
				if (CirclePack.cpb.getActivePackData().getDispOptions != null)
					CommandStrParser.jexecute(CirclePack.cpb.getActivePackData(), "disp -wr");
				else 
					TrafficCenter.cmdGUI("disp -w -c");
			} catch (FileNotFoundException e) {
				// Notify CirclePack of the error.
				this.messenger.sendErrorMessage("Failed to open " + urlString + ".");
//				pageDisplayPane.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
				activityIndicator.setIndeterminate(false);
				return 0;
			}
				
			// On either success or failure, update the GUI 
//			pageDisplayPane.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
			activityIndicator.setIndeterminate(false);
			return 4;			
		}

		// The URL is a web URL for p, q. Thread the download.
		else {
			new Thread() {
				public void run() {
					// Download a temporary copy of the web packing.
					final File localPackingFile;
					try {
						localPackingFile = BrowserUtilities.downloadTemporaryFile(urlString);
					} catch (IOException e) {
						// Notify CirclePack of the error and return from thread.
						EventQueue.invokeLater(new Runnable() {
							public void run() {
								messenger.sendErrorMessage("Could not download file " + urlString + ".");
								
//								pageDisplayPane.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
								activityIndicator.setIndeterminate(false);
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
						messenger.sendErrorMessage("Failed to open " + urlString + ".");

//						pageDisplayPane.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
						activityIndicator.setIndeterminate(false);
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
					
//							pageDisplayPane.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
							activityIndicator.setIndeterminate(false);
							return;
						}
					});
				}
			}.start();
//			pageDisplayPane.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
			activityIndicator.setIndeterminate(false);
			return 1;
		} // end of loading from web
	}
		
	/**
	 * Monitor combo box events to respond to changes 
	 * in the current URL. If the user enters or 
	 * selects a new or different URL from the combo box, 
	 * this listener will preprocess and possibly
	 * call for loading a new page.
	 * 
	 * @author kens
	 * @author Alex Fawkes
	 */
	public void actionPerformed(ActionEvent e) { 
		if (e.getActionCommand().equals("comboBoxChanged")
				|| e.getActionCommand().equals("comboBoxEdited")) {

			// Check if the URL is invalid or already loaded. 
			//   This can happen when a hyperlink is clicked,
			//   changing the current selection of the combo box 
			//   and triggering this event.
			String url=BrowserUtilities.parseURL((String)urlComboBox.getSelectedItem());
			if (url==null || url.equals(loadedUrl)) // error should have been caught in 'parseURL'
				return;
			
			int action=processURL(url);
			if (action<=0)
				return;
			if (action==2 && loadDirectory(webUrl)==0) {
				System.err.println("failed to load directory '"+webUrl+"'");
				return;
			}
			if (action==3 && loadScript(webUrl)==0) {
				System.err.println("failed to load script '"+webUrl+"'");
				return;
			}
			if (action==4 && loadPacking(webUrl)==0) {
				System.err.println("failed to load packing '"+webUrl+"'");
				return;
			}
			if ((action!=1 && action!=2) || webUrl==null)
				return; 
			loadPage(action,webUrl);
		}
	}
	
//	public void setWelcomePage() {
//		pageDisplayPane.setContentType("text/html");
//		try {
//			pageDisplayPane.setPage(CPBase.getResourceURL("/doc/Welcome.html"));
//			pageDisplayPane.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
//		} catch (Exception ex){
//			throw new ParserException("failed to load 'Welcome.html' page into browser");
//		}
//	}
	
	/**
	 * This loads a URL into the web page. 
	 * If type==1, load a new page and save 'loadedUrl'; 
	 * if type==2, load a directory *.html, but don't
	 *    save 'loadedUrl'. 
	 * @param type int, should be 1 or 2 only
	 * @param url URL
	 * @return 
	 */
	private void loadPage(int type,String url) {
		if (url==null || url.length()==0)
			return;
		activityIndicator.setIndeterminate(true);
		
		String in_loadedUrl=loadedUrl;
		
		Platform.runLater(() -> {
		
			webEngine.getLoadWorker().stateProperty().addListener(
		        new ChangeListener<State>() {
		            public void changed(@SuppressWarnings("rawtypes") ObservableValue ov, 
		            		State oldState, State newState) {
		                if (newState == State.SUCCEEDED) {
		                	loadedUrl=webUrl;
		    				if (loadedUrl.length()>0 && !loadedUrl.equals(in_loadedUrl)&& type!=5) {
		    					backHistory.push(loadedUrl);
		    					backButton.setEnabled(true);
		    				}

		    				// Clear the forward history and disable 
		    				// the forward button. A new page is about
		    				// to be loaded, from which there are no "forward" pages.
		    				forwardHistory.clear();
		    				forwardButton.setEnabled(false);
		                    
		                	CirclePack.cpb.msg("WWW has loaded "+webUrl);
		            		activityIndicator.setIndeterminate(false);
		                }
		                else if (newState == State.FAILED) {
		            		activityIndicator.setIndeterminate(false);

		                }
		            }
		        });
		
			webEngine.load(url.toString());
		});
	}
	
	private void reLoadPage() {
		Platform.runLater(() -> {
			webEngine.reload();
		});
	}
	
}

// AF: The commenting below was part of earlier browser changes and is kept for
// relevance. It contains decent information concerning the Swing Single Thread Rule.

/* 
 * Completely reworked this. See this link on the Swing Single Thread Rule:
 * http://java.sun.com/products/jfc/tsc/articles/threads/threads1.html
 *
 * Basically, Swing isn't designed to handle GUI calls made directly
 * from multiple threads. If you make direct GUI calls to Swing in threads other
 * than the Swing Event Dispatch Thread (primary offenders are main() and spun
 * threads), you run a high risk of the OS switching threads in the middle of
 * Swing's GUI processing, wreaking havok on internal flow of control and data
 * structure state. This is the cause of the deadlock issues I found when loading
 * scripts through the browser; this thread and the Swing Event Dispatch thread
 * are simultaneously giving conflicting instructions to Swing, causing deadlocks
 * and nondeterministic GUI updating.
 * 
 * The solution is to push all GUI changes called outside of the Swing Event
 * Dispatch thread to the end of the Swing EventQueue, using
 * EventQueue.invokeLater(Runnable) or SwingUtilities.invokeLater(Runnable)
 * which are equivalent. This way, it is guaranteed that GUI changes will occur
 * both sequentially and in order within the sole Swing Event Dispatch Thread.
 * 
 * Because the original implementation of this thread included so much logic
 * that is rapid enough to not necessitate threading (that is, it will not lock
 * up the GUI perceptibly) and so many minor GUI calls that would need to be wrapped
 * in the EventQueue.invokeLater(Runnable) block (which inhibits readability), I've
 * refactored it such that it is no longer a thread, but a normal object that spins
 * anonymous subclassed threads as needed for long tasks.
 * 
 * Note that I have not commented changes above this block to denote accommodations
 * for the new organization.
 */
