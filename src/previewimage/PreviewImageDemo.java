package previewimage;

import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Point;
import java.awt.Toolkit;
import java.io.IOException;
import javax.swing.JEditorPane;
import javax.swing.JFrame;

/**
 * This demo class contains a main method to demonstrate the use and
 * functionality of the <code>PreviewImageHyperlinkListener</code>
 * class. It displays a remote directory on the author's web server
 * containing XMD scripts with preview image elements. Mousing over
 * an XMD script will display its preview image in a tool tip.
 * 
 * @author Alex Fawkes
 *
 */
public class PreviewImageDemo {
	public static void main(String[] args) {
		// Always do GUI work on the EventQueue (main is run on a separate thread).
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				// Create an editor pane with an attached PreviewImageHyperlinkListener
				// and set it to the remote script folder.
				JEditorPane editorPane = new JEditorPane();
				editorPane.addHyperlinkListener(new PreviewImageHyperlinkListener());
				editorPane.setEditable(false);
				try {
					//editorPane.setPage("http://bokencraft.dyndns.org/scripts");
					editorPane.setPage("file:///Program Files (x86)/Apache Software Foundation/Apache2.2/htdocs/scripts/index.html");
				} catch (IOException e) {
					// Print the error and exit.
					e.printStackTrace();
					return;
				}
				
				// Set up and show a frame to display the editor pane.
				JFrame frame = new JFrame();
				frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
				Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
				frame.setLocation(new Point(screenSize.width/3, screenSize.height/3));
				frame.add(editorPane);
				frame.pack();
				frame.setVisible(true);
			}
		});
	}
}