package deBugging;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;

public class PrintIcon {
	
	public static void printImageIcon(ImageIcon IIcon,String name) {
		try {
			Image image = IIcon.getImage();
			RenderedImage render = null;
			BufferedImage bI = new BufferedImage(IIcon.getIconWidth(), 
					IIcon.getIconHeight(), BufferedImage.TYPE_INT_RGB);
			Graphics2D gd = bI.createGraphics();
			gd.drawImage(image, 0, 0, null);
			gd.dispose();
			File file = new File("/tmp/"+name);
			render = bI;
			ImageIO.write(render, "JPEG", file);
		} catch (IOException exc) {
			System.err.println("failed to write icon image");
		}
		System.out.println("ImageIcon printed in "+name);
	}
	
}
