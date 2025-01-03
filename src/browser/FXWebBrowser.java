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
import packing.PackData;
import packing.ReadWrite;
import util.FileUtil;
import util.MemComboBox;

public class FXWebBrowser extends JFrame implements ActionListener {

	// Regenerate this every time fields or methods change.
	private static final long serialVersionUID = 7248705697046383784L;
	
	protected JPanel browserPanel; // The main panel of this frame.
	protected WebViewRenderer webViewRenderer;
	protected MemComboBox urlComboBox; // ComboBox for storing and selecting URLs.
	protected JProgressBar activityIndicator; // Progress bar activated when loading a page.
	protected JButton backButton; // Button to navigate the browser back.
	protected JButton forwardButton; // Button to navigate the browser forward.
	protected JButton refreshButton; // Button to refresh the current page.
 	protected JButton htmlButton; // Button for directory scriptLister output.
	protected JLabel statusLabel; // Label to display the URL of the current moused over hyperlink.
	protected IMessenger messenger; // The message output interface, received from instantiator.
	protected Stack<URL> backHistory; // The stack of URLs that have been navigated away from.
	protected Stack<URL> forwardHistory; // The stack of URLs that have been navigated away from by the back button.

	// URL's
	protected URL loadedURL; // currently loaded by this instance.
	protected URL webURL; // processed, ready to load into web page

	/**
	 * Initialize a new BrowserFrame with no message output functionality and
	 * no persistent storage of URLs.
	 */
	public FXWebBrowser() {
		this(null, null);
	}

	/**
	 * Initialize a new BrowserFrame with persistent storage of URLs and message
	 * output functionality.
	 * 
	 * @param messenger the <code>IMessenger</code> to use for output, or
	 * <code>null</code> for no output
	 * @param historyFile the file path to use for persistent URL storage, or
	 * <code>null</code> for no persistent storage
	 */
	public FXWebBrowser(IMessenger messenger, String historyFile) {
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
		ImageIcon htmlIcon = new ImageIcon(getClass().getResource("/Resources/Icons/GUI/hoverH.png"));

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
					// There's something in the back history. Push the loaded URL onto
					// the forward history and enable the forward button. Then load the
					// top URL in the back history.
					forwardHistory.push(loadedURL);
					forwardButton.setEnabled(true);
					loadKnownURL(backHistory.pop());
				}

				// If there's nothing left in the back history, disable the button.
				if (backHistory.empty()) backButton.setEnabled(false);
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
					backHistory.push(loadedURL);
					backButton.setEnabled(true);
					loadKnownURL(forwardHistory.pop());
				}

				// If there's nothing left in the forward history, disable the button.
				if (forwardHistory.empty()) forwardButton.setEnabled(false);
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
					// Clear the stream description property for the document underlying the
					// page display pane. This will allow us to load the same URL again.
//					((Document)webViewRenderer.webEngine.getDocument()).
//						putProperty(Document.StreamDescriptionProperty, null);

					// Empty the loaded URL so load will work correctly.
					URL loadedUrlTemp=null;
					if ((loadedUrlTemp=FileUtil.tryURL(loadedURL.toString()))!=null) {
						loadedURL = null;
					
						// Reload the page.
						loadKnownURL(loadedUrlTemp);
					}
				}
			}
		});

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
		navigationPanel.add(htmlButton);

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

		// create the webViewBrowser
		webViewRenderer=new WebViewRenderer();
				
		// Create the main browser panel to pack into this frame.
		browserPanel = new JPanel();
		browserPanel.setLayout(new BoxLayout(browserPanel, BoxLayout.PAGE_AXIS));
		browserPanel.add(navigationPanel);
		browserPanel.add(webViewRenderer);
		browserPanel.add(statusPanel);

		webViewRenderer.addPropertyChangeListener(new PropertyChangeListener() {
			@Override
			public void propertyChange(PropertyChangeEvent e) {
				if (e.getPropertyName().equals("page") || e.getPropertyName().equals("editorKit")) {
					// New page has been set. Disable the activity indicator.
					activityIndicator.setIndeterminate(false);
				}
			}
		});
		
		// Set up this frame, but don't display it.
		this.setTitle("CirclePack Web Browser");
		this.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
		this.setPreferredSize(new Dimension(600, 400));
		this.add(browserPanel);
		webViewRenderer.setVisible(true);
		this.pack();
	}

	/**
	 * NavigationHyperlinkListener will navigate the browser 
	 * to new URLs in response to hyperlink activations. 
	 * If the user clicks a hyperlink in the browser, it will
	 * be activated, and the browser will navigate to the 
	 * represented URL.
	 * 
	 * @author kens
	 * @author Alex Fawkes
	 */
	protected class NavigationHyperlinkListener implements HyperlinkListener {
		@Override
		public void hyperlinkUpdate(HyperlinkEvent e) {
			if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
				// A hyperlink has been activated, generally by clicking from the user.
				if (loadedURL!=null) {
					// Push the loaded URL onto the back history and enable the back button.
					backHistory.push(loadedURL);
					backButton.setEnabled(true);

					// Clear the forward history and disable the forward button. Navigating
					// to a new page means there is nothing "forward" from it.
					forwardHistory.clear();
					forwardButton.setEnabled(false);
				}

				// check the URL represented by the activated hyperlink.
				URL url=FileUtil.parseURL(e.getURL().toString());

				if (url==null || url.equals(loadedURL))
					return; // url=null;
				
				// process sets 'webURL' 
				int action=processURL(url); // File.separator;
				
				// failed?
				if (action<=0 || webURL==null)
					return;
				
				// directory?
//				else if (action==2 && loadDirectory(webURL)==0) {
//					System.err.println("failed to load directory '"+webURL.toString()+"'");
//					return;
//				}
				
				// script?
				else if (action==3 && loadScript(webURL)==0) {
					System.err.println("failed to load script '"+webURL.toString()+"'");
					return;
				}
				
				// packing?
				else if (action==4 && loadPacking(webURL)==0) {
					System.err.println("failed to load packing '"+webURL.toString()+"'");
					return;
				}
				
				// only 1 and 2 require something to be loaded
				if ((action!=1 && action!=2))
					return; 
				
				int rslt=loadAction(action,webURL);
				
				if (rslt>0)  // success
					urlComboBox.setSuccess();
				else if (rslt==0)
					urlComboBox.setFailure();
				else 
					urlComboBox.setNeutral();
			}

		}
	}

	/**
	 * StatusHyperlinkListener updates the current URL status label in the
	 * browser to whatever the user is currently mousing over. If the user
	 * is not currently mousing over a hyperlink, the label will be cleared.
	 * 
	 * @author kens
	 * @author Alex Fawkes
	 */
	protected class StatusHyperlinkListener implements HyperlinkListener {
		@Override
		public void hyperlinkUpdate(HyperlinkEvent e) {
			if (e.getEventType() == HyperlinkEvent.EventType.ENTERED) {
				// A hyperlink has been entered. Get the URL and set the status label
				// to its string representation.
				statusLabel.setText(e.getURL().toString());
			} else if (e.getEventType() == HyperlinkEvent.EventType.EXITED) {
				// A hyperlink has been exited. Clear the status label.
				statusLabel.setText(null);
			}
		}
	}
	
	/**
	 * Preprocess the given URL, which should be valid.
	 * If there's a file to load into the web page, 
	 * local or web, set 'webURL'. Return int value 
	 * depending on outcome:
	 * * 0 for failure
	 * * -1: is already loaded
	 * * 1: web page to be displayed (goes into list)
	 * * 2: directory to display
	 * * 3: *.cps script (or *.xmd or *.cmd)
	 * * 4: *.p, *.q packing
	 * @param url URL,
	 * @return int, 0 on failure
	 */
	protected int processURL(URL url) {
		webURL=null;
		if (url == null) 
			return 0;

		// Check the Return if entereedURL is null.
		URL enteredURL=FileUtil.parseURL(url.toString());
		
		// Return if the URL is invalid.
		if (enteredURL == null) // failure 
			return 0;

		// Return if the URL is already loaded.
		if (enteredURL.equals(loadedURL)) 
			return -1;
		
		if ((webURL=FileUtil.tryURL(enteredURL.toString()))==null) {
			System.err.println("failed to set 'webURL'");
			return 0;
		}
		
		// ========== if URL is a directory
		File webFile=new File(webURL.getFile());
		if (webFile!=null && webFile.getPath()!=null
				&& webFile.isDirectory() && loadDirectory(webURL)!=0)
			return 2;

		// ======= If the URL is an *.cps (or *.xmd or *.cmd) script:
		String chkurl=enteredURL.toExternalForm().toLowerCase();
		if (chkurl.endsWith(".cps") ||
				chkurl.endsWith(".xmd") ||
				chkurl.endsWith(".cmd")) 
			return 3;

		// ================= If the URL is a *.p or *.q packing file:
		else if (chkurl.endsWith(".p") || 
				chkurl.endsWith(".q")) 
			return 4;
		
		// ================= If URL is a page to load
		else 
			return 1;
	}
	
	/**
	 * Prepare a directory in *.html form to load;
	 * 'url' is already confirmed and is a directory.
	 * Do not put list for past loads.
	 * @param url URL
	 * @return int, 0 on failure
	 */
	public int loadDirectory(URL url) {
// change for FXWebBrowser
		webURL=url;
//		webURL = BrowserUtilities.pageForDirectory(url);
		if (webURL == null) 
			return 0;			
		
		// webURL is the html page ready to load
		return 2;
	}

	/**
	 * Loading a script does not require loading 
	 * the web page and does not affect 'loadedUrl'
	 * @param url URL
	 * @return int, 0 on failure
	 */
	public int loadScript(URL url) {
		activityIndicator.setIndeterminate(true);
		webURL=null;
		
		// If a local file:
		if (url.toString().toLowerCase().startsWith("file")) {
			// Since this is a local file, there is no need to thread.
			
			// Load the script.
			boolean gotNewScript = false;
			if (CPBase.scriptManager.getScript(url.toString(),url.toString(), true) > 0) 
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
						tempScriptFile = BrowserUtilities.downloadTemporaryFile(url.toString());
					} catch (IOException e) {
						// Notify CirclePack of the error and return from thread.
						EventQueue.invokeLater(new Runnable() {
							public void run() {
								messenger.sendErrorMessage("Could not download file " + url.toString() + ".");
								
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
							if (CPBase.scriptManager.getScript(temporaryScriptString, url.toString(), true) > 0) 
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
	 * @param url URL
	 * @return int, 0 on failure
	 */
	public int loadPacking(URL url) {
		
		if (url==null)
			return 0;
		URL targetFile=null;
		if ((targetFile=FileUtil.tryURL(url.toString()))==null)
			return 0;
		
		activityIndicator.setIndeterminate(true);
		webURL=null;
		
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
						localPackingFile = BrowserUtilities.downloadTemporaryFile(url.toString());
					} catch (IOException e) {
						// Notify CirclePack of the error and return from thread.
						EventQueue.invokeLater(new Runnable() {
							public void run() {
								messenger.sendErrorMessage("Could not download file " + url.toString() + ".");
								
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
						messenger.sendErrorMessage("Failed to open " + url.toString() + ".");

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
	 * Loads the page specified by the given URL.
	 * This is a known page --- e.g. from history.
	 * Return value depends on objects: 
	 * @param url URL
	 * @return int, 0 on failure
	 */
	protected int loadKnownURL(URL url) {
		// Return if URL is null.
		if (url == null)
			return 0;

		// Return if the URL is already loaded.
		if (url.equals(loadedURL)) 
			return -1;

		// Indicate that we are loading something.
//		pageDisplayPane.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
		activityIndicator.setIndeterminate(true);
		
		int rslt=loadPage(url);
		activityIndicator.setIndeterminate(false);
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
		
		if ((rslt=loadPage(url))==0) // failure 
			return 0;

		// TODO: not getting right behavior on forward/back buttons

		URL hold_loadedURL=null;
		// try loading; only push successful script or web page onto the history stack.
		hold_loadedURL=FileUtil.tryURL(loadedURL.toString());
		
		loadedURL=url;
		// Add loadedURL to the back history stack and 
		//    enable the back button.
		if (!loadedURL.equals(hold_loadedURL)) {
			backHistory.push(loadedURL);
			backButton.setEnabled(true);

			// If this is a new page, not a directory,
			// we clear the forward history and disable 
			// the forward button. There is no "forward" 
			// pages when a new page is loaded.
			if (action==1) {
				forwardHistory.clear();
				forwardButton.setEnabled(false);
			}
		}
		
		return rslt; 
	}


	/**
	 * This is the actual call to load the page.
	 * @param newURL URL
	 * @return 0 on failure
	 */
	public int loadPage(URL newURL) {
		boolean debug=false;
		refreshButton.setEnabled(true);
		
// debugging: try to change to known file to see it loads
		if (debug) // debug=true;
			newURL=FileUtil.tryURL("file:/C:/Users/kensm/Documents/CmdDetails.html");
		
		webViewRenderer.loadPage(newURL.toString());
//		com.sun.webkit.dom.HTMLDocumentImpl webdoc=(com.sun.webkit.dom.HTMLDocumentImpl)(webViewRenderer.webEngine).getDocument();
//       	if (webdoc==null)
//       		System.err.println("no document for '"+newURL+"'");

		loadedURL = FileUtil.tryURL(newURL.toString());
		if (loadedURL==null) 
			return 0;
		urlComboBox.add2List(loadedURL.toString(),false);
//			pageDisplayPane.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
		activityIndicator.setIndeterminate(false);
		messenger.sendOutputMessage("Browser loaded " + newURL.toString() + ".");
		// Don't update the activity indicator here. The property change listener should
		// handle it when the threaded setPage() method finishes.
		return 1;
		
	}

	/*
	 * This next bit of code should disable the activity indicator whenever a page
	 * loads or fails to load. There should be no need to do it manually.
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
			String urlString = (String) urlComboBox.getSelectedItem();
			
			URL url=FileUtil.parseURL(urlString);
			if (url==null || url.equals(loadedURL))
				return; // url.getPath();
			
			// process sets 'webURL' 
			int action=processURL(url); // File.separator;
			
			// failed?
			if (action<=0 || webURL==null)
				return;
			
			// directory?
//			else if (action==2 && loadDirectory(webURL)==0) {
//				System.err.println("failed to load directory '"+webURL.toString()+"'");
//				return;
//			}
			
			// script?
			else if (action==3 && loadScript(webURL)==0) {
				System.err.println("failed to load script '"+webURL.toString()+"'");
				return;
			}
			
			// packing?
			else if (action==4 && loadPacking(webURL)==0) {
				System.err.println("failed to load packing '"+webURL.toString()+"'");
				return;
			}
			
			// only 1 and 2 require something to be loaded
			if ((action!=1 && action!=2))
				return; 
			
			int rslt=loadAction(action,webURL);
			
			if (rslt>0)  // success
				urlComboBox.setSuccess();
			else if (rslt==0)
				urlComboBox.setFailure();
			else 
				urlComboBox.setNeutral();
		}
	}

} 
