package printStuff;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

/**
 * For printing JPG files
 * @author Alexander Fawkes
 *
 */
public class JPGPrintable implements Printable {
	File file;
	
	JPGPrintable(File receivedFile) {
		//Accept the JPG file to print.
		file = receivedFile;
	}
	
	/* Called repeatedly with increasing page numbers until NO_SUCH_PAGE 
	 * is returned.	The Graphics parameter is operated on to render the 
	 * current page, and is printed if PAGE_EXISTS is returned from the 
	 * function. */
	public int print(Graphics graphics, PageFormat pageFormat, int pageNumber)
	throws PrinterException {
		Graphics2D graphics2d;
		BufferedImage image;
		double pageWidth;
		double pageHeight;
		double pageRatio;
		double imageWidth;
		double imageHeight;
		double imageRatio;
		double scaleFactor;
		
		if (pageNumber > 0) {
			/* JPG printing takes a single page. If a page number greater than
			   zero is passed (page numbering is zero indexed), return that the
			   page does not exist. */
			return Printable.NO_SUCH_PAGE;
		}

		image = null;
		try {
			image = ImageIO.read(file);
		} catch (IOException ex) {
			ex.printStackTrace();
			System.exit(1);
		}
		
		/* Get the aspect ratio of the printable page area and the image being printed.
		   getImageableWidth() and getImageableHeight() return the width and height of
		   the printable area for the PageFormat instance in 1/72nds of an inch. One
		   pixel maps to 1/72nd of an inch on printing. */
		pageWidth = pageFormat.getImageableWidth();
		pageHeight = pageFormat.getImageableHeight();
		pageRatio = pageWidth/pageHeight;
		imageWidth = image.getWidth();
		imageHeight = image.getHeight();
		imageRatio = imageWidth/imageHeight;
		
		//If the image is more wide than tall, set the scale factor according to width.
		if (imageRatio >= pageRatio) {
			scaleFactor = pageWidth/imageWidth; 
		}
		//Otherwise, set it according to height.
		else {
			scaleFactor = pageHeight/imageHeight;
		}
		
		/* Translate the graphics to the printable area. getImageableX() and getImageableY()
		   return the width and height of the left and top printing margins. Then, scale the
		   graphics area according to the scale factor and draw the image. */
		graphics2d = (Graphics2D) graphics;
		graphics2d.translate(pageFormat.getImageableX(), pageFormat.getImageableY());
		
		// TODO: image comes out way too small; try increasing 
		graphics2d.scale(scaleFactor, scaleFactor);
		graphics2d.drawImage(image, null, 0, 0);
		
		return Printable.PAGE_EXISTS;
	}
}