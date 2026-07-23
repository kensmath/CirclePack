package frames;

import java.awt.BorderLayout;
import java.awt.Component;

import javax.swing.JFrame;
import javax.swing.JTextField;

import org.cef.CefClient;
import org.cef.browser.CefBrowser;

import allMains.CPBase;

/**
 * A CirclePack browser window — one of these per open browser panel/tab.
 * Only the things that are genuinely per-window live here. The shared
 * CefApp singleton lives in CefAppHolder and is NOT created or disposed
 * from this class.
 *
 * Drop this in next to CirclePack's other *Frame window classes and adapt
 * to whatever base class / windowing convention they use (this sketch
 * extends JFrame directly for clarity).
 */
public class BrowserFrame extends JFrame {
	private static final long serialVersionUID = 1L;
	
    private final CefClient client;
    private final CefBrowser browser;
    private boolean browserClosed = false;

    public BrowserFrame(String startUrl) {
        super("CirclePack Browser");

        // Per-window: a client and a browser, created off the shared CefApp.
        client = CPBase.getCefApp().createClient();
        browser = client.createBrowser(startUrl, false, false);
        Component browserUI = browser.getUIComponent();

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(600,300);
        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(browserUI, BorderLayout.CENTER);

        JTextField addressBar = new JTextField(startUrl);
        addressBar.addActionListener(e -> browser.loadURL(addressBar.getText()));
        getContentPane().add(addressBar, BorderLayout.NORTH);

        // Per-window cleanup only — do NOT touch CefAppHolder/CefApp here.
        // This fires when the USER closes this window individually (click
        // the X). It does NOT fire from a bulk frame.dispose() loop like
        // CirclePack's exit(), which is why closeBrowser() below also
        // exists as something exit() can call directly and explicitly.
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                closeBrowser();
            }
        });
    }

    /**
     * Releases this window's CEF resources (browser + client). Safe to
     * call more than once. Called from windowClosing() for a normal
     * single-window close, and should ALSO be called explicitly by
     * CirclePack's exit()/shutdown path for every open BrowserFrame
     * BEFORE that path calls CefAppHolder.shutdown() — plain
     * frame.dispose() alone does not trigger windowClosing, so relying
     * on the listener alone would leak CEF resources on app exit.
     */
    public synchronized void closeBrowser() {
        if (browserClosed) {
            return;
        }
        browserClosed = true;
        browser.close(true);
        client.dispose();
    }
}
