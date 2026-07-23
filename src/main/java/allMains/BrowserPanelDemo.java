package allMains;

import org.cef.CefApp;
import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import me.friwi.jcefmaven.CefAppBuilder;
import me.friwi.jcefmaven.CefInitializationException;
import me.friwi.jcefmaven.UnsupportedPlatformException;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

/**
 * Minimal standalone demo of embedding a Chromium browser panel in a Swing
 * JFrame using jcefmaven. Run this on its own first to confirm the setup
 * works in Eclipse, then adapt BrowserPanel into a JPanel you drop into
 * CirclePack's existing Swing layout (e.g. as a tab, a docked panel, or a
 * dialog for viewing help pages / rendered output).
 *
 * pom.xml dependency needed (check mvnrepository.com/artifact/me.friwi/jcefmaven
 * for the current version number before using this):
 *
 * <dependency>
 *     <groupId>me.friwi</groupId>
 *     <artifactId>jcefmaven</artifactId>
 *     <version>146.0.10</version>
 * </dependency>
 */
public class BrowserPanelDemo {

    public static void main(String[] args) throws IOException,
            UnsupportedPlatformException, CefInitializationException, InterruptedException {

        // 1. Build/download the CEF runtime for the current OS (cached after first run).
        CefAppBuilder builder = new CefAppBuilder();
        builder.getCefSettings().windowless_rendering_enabled = false;
        CefApp cefApp = builder.build();

        // 2. Create a client and a browser instance pointed at a URL.
        CefClient client = cefApp.createClient();
        CefBrowser browser = client.createBrowser(
                "https://www.circlepack.com/", // swap for whatever URL/local HTML you need
                false,  // offscreen rendering off (we're using the native windowed component)
                false
        );

        // 3. browser.getUIComponent() is a plain java.awt.Component you can
        //    add anywhere in a Swing hierarchy, same as any other JComponent.
        Component browserUI = browser.getUIComponent();

        // 4. Wire it into a normal JFrame for this demo.
        JFrame frame = new JFrame("CirclePack Browser Panel Demo");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1000, 700);
        frame.getContentPane().setLayout(new BorderLayout());
        frame.getContentPane().add(browserUI, BorderLayout.CENTER);

        // Optional: a simple address bar to show it's a fully interactive browser.
        JTextField addressBar = new JTextField("https://www.circlepack.com/");
        addressBar.addActionListener(e -> browser.loadURL(addressBar.getText()));
        frame.getContentPane().add(addressBar, BorderLayout.NORTH);

        frame.setVisible(true);

        // Make sure CEF shuts down cleanly when the window closes.
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                cefApp.dispose();
            }
        });
    }
}
