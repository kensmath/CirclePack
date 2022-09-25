package browser;

import java.awt.Cursor;
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
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Stack;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.border.EmptyBorder;
import javax.swing.border.SoftBevelBorder;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.Document;

import allMains.CPBase;
import allMains.CirclePack;
import circlePack.PackControl;
import exceptions.ParserException;
import input.CommandStrParser;
import input.TrafficCenter;
import interfaces.IMessenger;
import packing.PackData;
import packing.ReadWrite;
import previewimage.PreviewImageHyperlinkListener;
import util.MemComboBox;

/**
 * BrowserFrame is a simple web browser. It has integrated functionality
 * to handle CirclePack XMD scripts and P and Q packing data files.
 * 
 * @author kens
 * @author Alex Fawkes
 */
public class BrowserFrame extends JFrame implements ActionListener {
	// Regenerate this every time fields or methods change.
	private static final long serialVersionUID = 7248705697046383784L;
	
	protected JPanel browserPanel; // The main panel of this frame.
	protected JEditorPane pageDisplayPane; // Actually holds and displays the current page.
	protected MemComboBox urlComboBox; // ComboBox for storing and selecting URLs.
	protected JProgressBar activityIndicator; // Progress bar activated when loading a page.
	protected JButton backButton; // Button to navigate the browser back.
	protected JButton forwardButton; // Button to navigate the browser forward.
	protected JButton refreshButton; // Button to refresh the current page.
	protected JLabel statusLabel; // Label to display the URL of the current moused over hyperlink.
	protected String loadedUrl; // The URL currently loaded by this instance.
	protected IMessenger messenger; // The message output interface, received from instantiator.
	protected Stack<String> backHistory; // The stack of URLs that have been navigated away from.
	protected Stack<String> forwardHistory; // The stack of URLs that have been navigated away from by the back button.

	/**
	 * Initialize a new BrowserFrame with no message output functionality and
	 * no persistent storage of URLs.
	 */
	public BrowserFrame() {
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
	public BrowserFrame(IMessenger messenger, String historyFile) {
		super();

		// No URL is currently loaded. Initialize to use without checking for null.
		this.loadedUrl = "";

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
		backHistory = new Stack<String>();
		forwardHistory = new Stack<String>();

		// Get the icons for the navigation buttons.
		ImageIcon backIcon = new ImageIcon(getClass().getResource("/Resources/Icons/main/previous.png"));
		ImageIcon forwardIcon = new ImageIcon(getClass().getResource("/Resources/Icons/main/forward.png"));
		ImageIcon refreshIcon = new ImageIcon(getClass().getResource("/Resources/Icons/main/reload.png"));

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
					forwardHistory.push(loadedUrl);
					forwardButton.setEnabled(true);
					load(backHistory.pop());
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
					backHistory.push(loadedUrl);
					backButton.setEnabled(true);
					load(forwardHistory.pop());
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
				if (!loadedUrl.trim().isEmpty()) {
					// Clear the stream description property for the document underlying the
					// page display pane. This will allow us to load the same URL again.
					pageDisplayPane.getDocument().putProperty(Document.StreamDescriptionProperty, null);

					// Empty the loaded URL so load will work correctly.
					String loadedUrlTemp = loadedUrl;
					loadedUrl = "";
					
					// Reload the page.
					load(loadedUrlTemp);
				}
			}
		});

		File file=new File(historyFile);
		try {
			file.createNewFile(); // finds or creates 
		} catch (IOException iox) {
			CirclePack.cpb.errMsg("failed to open xmd (or cps) file");
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

		// Set up the pane that displays the content of URLs.
		pageDisplayPane = new JEditorPane();
		pageDisplayPane.setEditable(false); // Editable hyperlinks can't be clicked to navigate.
		pageDisplayPane.addHyperlinkListener(new NavigationHyperlinkListener()); // Load clicked hyperlinks.
		pageDisplayPane.addHyperlinkListener(new StatusHyperlinkListener()); // Show current moused over URL in a label.
		pageDisplayPane.addHyperlinkListener(new PreviewImageHyperlinkListener()); // Display a tooltip when certain XMD scripts are moused over.

		// Page display pane should be scrollable, so put it in a scroll pane.
		JScrollPane scrollPane = new JScrollPane(pageDisplayPane);

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

		/*
		 * This next bit of code should disable the activity indicator whenever a page
		 * loads or fails to load. There should be no need to do it manually.
		 */
		pageDisplayPane.addPropertyChangeListener(new PropertyChangeListener() {
			@Override
			public void propertyChange(PropertyChangeEvent e) {
				if (e.getPropertyName().equals("page") || e.getPropertyName().equals("editorKit")) {
					// New page has been set. Disable the activity indicator.
					activityIndicator.setIndeterminate(false);
				}
			}
		});

		// Create the status panel along the bottom of the frame.
		JPanel statusPanel = new JPanel();
		statusPanel.setLayout(new BoxLayout(statusPanel, BoxLayout.LINE_AXIS));
		statusPanel.add(statusLabel);
		statusPanel.add(activityIndicator);

		// Create the main browser panel to pack into this frame.
		browserPanel = new JPanel();
		browserPanel.setLayout(new BoxLayout(browserPanel, BoxLayout.PAGE_AXIS));
		browserPanel.add(navigationPanel);
		browserPanel.add(scrollPane);
		browserPanel.add(statusPanel);

		// Set up this frame, but don't display it.
		this.setTitle("Web Browser");
		this.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
		this.setPreferredSize(new Dimension(600, 400));
		this.add(browserPanel);
		this.pack();
	}

	/**
	 * NavigationHyperlinkListener will navigate the browser to new URLs in response
	 * to hyperlink activations. If the user clicks a hyperlink in the browser, it will
	 * be activated, and the browser will navigate to the represented URL.
	 * 
	 * @author kens
	 * @author Alex Fawkes
	 */
	protected class NavigationHyperlinkListener implements HyperlinkListener {
		@Override
		public void hyperlinkUpdate(HyperlinkEvent e) {
			if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
				// A hyperlink has been activated, generally by clicking from the user.
				if (!loadedUrl.trim().isEmpty()) {
					// Push the loaded URL onto the back history and enable the back button.
					backHistory.push(loadedUrl);
					backButton.setEnabled(true);

					// Clear the forward history and disable the forward button. Navigating
					// to a new page means there is nothing "forward" from it.
					forwardHistory.clear();
					forwardButton.setEnabled(false);
				}

				// Load the URL represented by the activated hyperlink.
				load(e.getURL().toString());
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
	 * Loads the page specified by the passed <code>String</code> object. 
	 * Return value depends on objects: 
	 * * 0 for failure
	 * * -1 for something already loaded
	 * * 1 for web page (goes into list)
	 * * 2 for directory (goes into list)
	 * * 3 for *.xmd script or *.cps script
	 * * 4 for packing
	 * @param url a <code>String</code> representation of the URL to load
	 * @return int
	 */
	protected int load(String url) {
		// Return if URL is null.
		if (url == null || url.length()==0) 
			return 0;
		
		// Return if the URL is invalid.
		final URL enteredUrl = BrowserUtilities.parseURL(url);
		if (enteredUrl == null) 
			return 0;

		// Return if the URL is already loaded.
		if (enteredUrl.equals(loadedUrl)) 
			return -1;

		// Indicate that we are loading something.
		pageDisplayPane.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
		activityIndicator.setIndeterminate(true);

		// ================== If the URL is an CPS (or XMD) script:
		if (enteredUrl.getFile().toLowerCase().endsWith(".xmd") ||
				enteredUrl.getFile().toLowerCase().endsWith(".cps")) {
			// If the URL is a file URL:
			if (enteredUrl.getProtocol().toLowerCase().equals("file")) {
				// Since this is a local file, there is no need to thread.
				
				// Load the script.
				boolean newScript = false;
				if (CPBase.scriptManager.getScript(enteredUrl.getFile(), enteredUrl.getFile(), true) > 0) 
					newScript = true;
				if (newScript) PackControl.scriptHover.stackScroll.getViewport().setViewPosition(new Point(0, 0));

				// Update the cursor and activity indicator and exit.
				pageDisplayPane.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
				activityIndicator.setIndeterminate(false);
				if (newScript)
					return 3;
				else
					return 0;
			}

			// The URL is a web URL. Thread the download.
			else {
				new Thread() {
					public void run() {
						/*
						 * The initial implementation passed the URL to getScript(String, boolean)
						 * for downloading. The problem is that this caused getScript(String, boolean)
						 * to run in a thread other than the Swing Event Dispatch Thread, which
						 * violates the Swing Single Thread Rule and caused crashes and nondeterministic
						 * behavior.
						 *
						 * The solution is to thread the download up front and save it as a temporary file.
						 * If the download is successful, we'll send the URL of the local temporary file
						 * to getScript(String, boolean) by pushing the code to the end of the Swing EventQueue.
						 */

						// Download a temporary copy of the web script.
						File temporaryScriptFile = null;
						try {
							temporaryScriptFile = BrowserUtilities.downloadTemporaryFile(enteredUrl);
						} catch (IOException e) {
							// Notify CirclePack of the error and return from thread.
							EventQueue.invokeLater(new Runnable() {
								public void run() {
									messenger.sendErrorMessage("Could not download file " + enteredUrl + ".");
									
									// Update the cursor and activity indicator and exit.
									pageDisplayPane.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
									activityIndicator.setIndeterminate(false);
									return;	
								}
							});

							// Do not continue after exception.
							return;
						}

						// Pass off the downloaded temporary script file to getScript(String, boolean).
						// Push it to the end of the EventQueue because it might make extensive Swing
						// GUI calls. Do any GUI updating and bookkeeping, then return.
						final String temporaryScriptString = temporaryScriptFile.getPath();
						final String enteredUrlString = enteredUrl.toString(); // KS: want url as true location
						EventQueue.invokeLater(new Runnable() {
							public void run() {
								boolean newScript = false;
								if (CPBase.scriptManager.getScript(temporaryScriptString, enteredUrlString, true) > 0) 
									newScript = true;
								if (newScript) PackControl.scriptHover.stackScroll.getViewport().
								setViewPosition(new Point(0, 0));

								// Update the cursor and activity indicator and exit.
								pageDisplayPane.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
								activityIndicator.setIndeterminate(false);
								return;			
							}
						});
					}
				}.start();
				
				pageDisplayPane.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
				activityIndicator.setIndeterminate(false);
				return 3;
			}
		} // end of processing for *.cps (or *.xmd) files

		// ================= If the URL is a P, Q, or PL packing file:
		if (enteredUrl.getFile().toLowerCase().endsWith(".p") || 
				enteredUrl.getFile().toLowerCase().endsWith(".q") ||
				enteredUrl.getFile().toLowerCase().endsWith(".pl")) {
			// Prompt the user to confirm loading the packing:
			String confirmDialogText = "Load into pack " + CirclePack.cpb.getActivePackData().packNum + "?";
			int result = JOptionPane.showConfirmDialog(null, confirmDialogText, "Confirm", JOptionPane.YES_NO_OPTION);

			// If the user has confirmed the loading, start loading the packing.
			if (result == JOptionPane.YES_OPTION) {
				// If the URL is a file URL:
				if (enteredUrl.getProtocol().toLowerCase().equals("file")) {
					// We don't need to thread opening a local file.
					try {
						BufferedReader bufferedReader = new BufferedReader(new FileReader(enteredUrl.getFile()));
						if (enteredUrl.getFile().toLowerCase().endsWith(".p")) 
							TrafficCenter.cmdGUI("cleanse");
						PackData tmppd=CirclePack.cpb.getActivePackData();
						ReadWrite.readpack(bufferedReader,tmppd,enteredUrl.getFile());
						if (CirclePack.cpb.getActivePackData().getDispOptions != null)
							CommandStrParser.jexecute(CirclePack.cpb.getActivePackData(), "disp -wr");
						else 
							TrafficCenter.cmdGUI("disp -w -c");
					} catch (FileNotFoundException e) {
						// Notify CirclePack of the error.
						this.messenger.sendErrorMessage("Failed to open " + enteredUrl + ".");
						pageDisplayPane.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
						activityIndicator.setIndeterminate(false);
						return 0;
					}
					
					// On either success or failure, update the GUI and return.
					pageDisplayPane.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
					activityIndicator.setIndeterminate(false);
					return 4;			
				}

				// The URL is a web URL for p, q, or pl. Thread the download.
				else {
					new Thread() {
						public void run() {
							// Download a temporary copy of the web packing.
							final File localPackingFile;
							try {
								localPackingFile = BrowserUtilities.downloadTemporaryFile(enteredUrl);
							} catch (IOException e) {
								// Notify CirclePack of the error and return from thread.
								EventQueue.invokeLater(new Runnable() {
									public void run() {
										messenger.sendErrorMessage("Could not download file " + enteredUrl + ".");
										
										pageDisplayPane.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
										activityIndicator.setIndeterminate(false);
										return;			
									}
								});

								// Do not continue after exception.
								return;
							}

							// Pass off the downloaded temporary packing file to readpack(BufferedReader, String).
							// Push it to the end of the EventQueue because it might make Swing GUI calls.
							// Do any GUI updating and bookkeeping, then return.
							final BufferedReader bufferedReader;
							try {
								bufferedReader = new BufferedReader(new FileReader(localPackingFile));
							} catch (FileNotFoundException e) {
								messenger.sendErrorMessage("Failed to open " + enteredUrl + ".");

								pageDisplayPane.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
								activityIndicator.setIndeterminate(false);
								return;
							} 

							EventQueue.invokeLater(new Runnable() {
								public void run() {
									if (localPackingFile.toString().toLowerCase().endsWith(".p")) TrafficCenter.cmdGUI("cleanse");
									PackData tmppd=CirclePack.cpb.getActivePackData();
									ReadWrite.readpack(bufferedReader,tmppd,localPackingFile.toString());
									if (CirclePack.cpb.getActivePackData().getDispOptions != null)
										CommandStrParser.jexecute(CirclePack.cpb.getActivePackData(), "disp -wr");
									else TrafficCenter.cmdGUI("disp -w -c");
									
									pageDisplayPane.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
									activityIndicator.setIndeterminate(false);
									return;
								}
							});
						}
					}.start();
					pageDisplayPane.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
					activityIndicator.setIndeterminate(false);
					return 4;
				}
			} // end of approved load
			pageDisplayPane.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
			activityIndicator.setIndeterminate(false);
			return 0; // load not confirmed
		} // end of processing p, q, pl files

		// ================== If the URL is a local directory:
		if (enteredUrl.getProtocol().toLowerCase().equals("file") && 
			new File(enteredUrl.getFile().replace("%20", " ")).isDirectory()) {
			
			File directory = new File(enteredUrl.getFile().replace("%20", " "));

			String pageText = BrowserUtilities.pageForDirectory(directory.getPath());
			if (pageText == null) {
				pageDisplayPane.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
				activityIndicator.setIndeterminate(false);
				return 0;			
			}

			// Set the browser to display our constructed HTML code.
			pageDisplayPane.getDocument().putProperty(Document.StreamDescriptionProperty, null);
			pageDisplayPane.setContentType("text/html");
			pageDisplayPane.setText(pageText.toString());

			refreshButton.setEnabled(true);
			loadedUrl = enteredUrl.toString(); 
			urlComboBox.add2List(loadedUrl,false);
			messenger.sendOutputMessage("Browser loaded " + enteredUrl.toString() + ".");
			pageDisplayPane.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
			activityIndicator.setIndeterminate(false);
			return 2;			
		} // end of processing local directories

		// The URL is a web page:
		try {
			
			// if a web location, check that it exists.
			if (!enteredUrl.getProtocol().toLowerCase().equals("file")) { 
				HttpURLConnection huc = (HttpURLConnection) enteredUrl.openConnection();
				if (huc.getResponseCode()==404) {
					pageDisplayPane.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
					activityIndicator.setIndeterminate(false);
					return 0;
				}
			}
			
			refreshButton.setEnabled(true);
			pageDisplayPane.setPage(enteredUrl);
			loadedUrl = enteredUrl.toString(); 
			urlComboBox.add2List(loadedUrl,false);
			pageDisplayPane.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
			activityIndicator.setIndeterminate(false);
			messenger.sendOutputMessage("Browser loaded " + enteredUrl.toString() + ".");
			// Don't update the activity indicator here. The property change listener should
			// handle it when the threaded setPage() method finishes.
			return 1;
		} catch (IOException e) {
			// "\n" is not a cross-platform newline character.
			// To get the appropriate newline character for the platform, use
			// String.format("%n") or System.getProperty("line.separator").
			String errorMessage = "Browser failed to load " + String.format("%n") + enteredUrl + String.format("%n") + e;
			pageDisplayPane.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
			activityIndicator.setIndeterminate(false);
			messenger.sendErrorMessage(errorMessage);
			return 0;
		}
		
	}

	/**
	 * Monitor combo box events to respond to changes in the current URL. 
	 * If the user enters or selects a new or different URL from the combo box, 
	 * this listener will navigate the browser to it.
	 * 
	 * @author kens
	 * @author Alex Fawkes
	 */
	public void actionPerformed(ActionEvent e) { 
		if (e.getActionCommand().equals("comboBoxChanged")
				|| e.getActionCommand().equals("comboBoxEdited")) {

			// Check if the URL is invalid or already loaded. This can happen when a hyperlink is clicked,
			// changing the current selection of the combo box and triggering this event.
			String url = (String) urlComboBox.getSelectedItem();
			boolean validAndNovelURL = url != null && !url.trim().isEmpty() && !url.trim().equals(loadedUrl.trim());
			if (!validAndNovelURL) return; // Return if the URL is not valid and novel.

			// try loading; only push successful script or web page onto the history stack.
			String in_loadedUrl=new String(loadedUrl);
			int rslt=load(url); // 0=failure, -1=already loaded, 1=web,2=directory,3=xml,4=packing
			
			// only put web, directory, and xml files in history
			// TODO: not getting right behavior on forward/back buttons
			if (rslt>0 && rslt<4) {
				// Add the loaded URL to the back history stack and enable the back button.
				if (!loadedUrl.equals(in_loadedUrl)) {
					backHistory.push(loadedUrl);
					backButton.setEnabled(true);
				}

				// Clear the forward history and disable the forward button. A new page is about
				// to be loaded, from which there are no "forward" pages.
				forwardHistory.clear();
				forwardButton.setEnabled(false);
			}
			if (rslt>0)  // success
				urlComboBox.setSuccess();
			else if (rslt==0)
				urlComboBox.setFailure();
			else 
				urlComboBox.setNeutral();
		}
	}
	
	public void setWelcomePage() {
		pageDisplayPane.setContentType("text/html");
		try {
			pageDisplayPane.setPage(CPBase.getResourceURL("/doc/Welcome.html"));
			pageDisplayPane.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
		} catch (Exception ex){
			throw new ParserException("failed to load 'Welcome.html' page into browser");
		}
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