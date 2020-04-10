package frames;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.image.BufferedImage;

import javax.swing.JComponent;
import javax.swing.JFrame;

import circlePack.PackControl;

/**
 * Class to display a SplashScreen while CirclePack initiates
 * 
 * @author Bradford Smith
 */
public class SplashFrame extends JFrame {
	private static final long serialVersionUID = 1L;

	// Constructor
	public SplashFrame(BufferedImage image){
		// splashscreen shouldn't have a title bar
		setUndecorated(true);
		
		// Add a component to display the image
		add(new ImageComponent(image), BorderLayout.CENTER);
		// make this frame just large enough to contain the image
		pack();
		// set location to center of the screen
		int x = (PackControl.displayDimension.width - getWidth()) / 2;
		int y = (PackControl.displayDimension.height - getHeight()) / 2;
		setLocation(x, y);
	}	

	/**
	 * Component to actually display the splashscreen image.
	 */
	private class ImageComponent extends JComponent {
		private static final long serialVersionUID = 1L;
		private BufferedImage image;
		
		ImageComponent(BufferedImage image){
			this.image = image;

			if (image == null) return;
			// set preferred size just large enough to contain the image.
			int w = image.getWidth();
			int h = image.getHeight();
			setPreferredSize(new Dimension(w, h));
		}

		// called when the component needs to be drawn
		public void paintComponent(Graphics g){
			if (image == null) return;
			g.drawImage(image, 0, 0, null);
		}
	}
}
