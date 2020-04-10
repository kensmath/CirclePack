package printStuff;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.print.Printable;
import java.awt.print.PageFormat;
import java.awt.print.PrinterException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import javax.swing.JEditorPane;
import javax.swing.RepaintManager;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.JFrame;
import javax.swing.WindowConstants;

/* This class reads the HTML file, displays it in a JEditorPane, then
   contains the print() function to print the JEditorPane. */
public class HTMLPrintable extends JEditorPane
implements Printable {
	private static final long serialVersionUID = 1L;
	File file;
	
	HTMLPrintable(File receivedFile) {
		//Accept the HTML file to print.
		file = receivedFile;
	}
	
	/* Upon confirmation in a print dialog window, this function is called
	repeatedly with increasing page numbers until NO_SUCH_PAGE is returned.
	The Graphics parameter is operated on to render the current page,
	and is printed if PAGE_EXISTS is returned from the function. */
	public int print(Graphics graphics, PageFormat pageFormat, int pageNumber)
	throws PrinterException {
		Graphics2D graphics2d;
		double panelWidth;
		double panelHeight;
		double pageWidth;
		double pageHeight;
		double scaleFactor;
		int numberOfPages;
		FileInputStream fileInputStream;
		InputStream inputStream;
		HTMLDocument htmlDocument;
		
		//Prepare the JEditorPane to read and display HTML content.
		this.setContentType("text/html");
		this.setEditorKit(new HTMLEditorKit());
		htmlDocument = new HTMLDocument();
		/* The IgnoreCharsetDirective will prevent ChangedCharSetExceptions
		   if the HTML file contains meta tags. */
		htmlDocument.putProperty("IgnoreCharsetDirective", true);
		//Read the HTML content.
		fileInputStream = null;
		try {
			fileInputStream = new FileInputStream(file);
		} catch (FileNotFoundException ex) {
			ex.printStackTrace();
			System.exit(1);
		}
		inputStream = fileInputStream;
		try {
			this.read(inputStream, htmlDocument);
		} catch (IOException ex) {
			ex.printStackTrace();
			System.exit(1);
		}
		
		/* This was originally included as a preview window, as content was
		   not printing. After embedding the JEditorPane in a JFrame, content
		   printed fine. I couldn't get the JEditorPane to print without
		   putting it in a JFrame, so I just left this and made it invisible. */
		JFrame testFrame = new JFrame();
		testFrame.setPreferredSize(new Dimension(640, 480));
		testFrame.setResizable(false);
		testFrame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		testFrame.add(this);
		testFrame.pack();
		testFrame.setVisible(false);
		
		//Calculate the scale factor and the number of pages.
		panelWidth = this.getSize().width;
		panelHeight = this.getSize().height;
		pageWidth = pageFormat.getImageableWidth();
		pageHeight = pageFormat.getImageableHeight();
		scaleFactor = pageWidth/panelWidth;
		numberOfPages = (int) Math.ceil(scaleFactor * panelHeight/pageHeight);

		if (pageNumber > numberOfPages) {
			//If the current page number is higher than the total number of pages, stop printing.
			testFrame.dispose();
			return Printable.NO_SUCH_PAGE;
		}
		
		/* Leaving double buffering enabled on the component will cause low resolution
		   printing. This can be re-enabled after the paint() call if the component will
		   still be used. */
		RepaintManager.currentManager(this).setDoubleBufferingEnabled(false);
		graphics2d = (Graphics2D) graphics;
		graphics2d.setColor(Color.black);
		//Translate the graphics across the margins to the printable area.
		graphics2d.translate(pageFormat.getImageableX(), pageFormat.getImageableY());
		//Translate the graphics to render the proper page.
		graphics2d.translate(0D, -pageNumber * pageHeight);
		//Scale the graphics according to the calculated scale factor.
		graphics2d.scale(scaleFactor, scaleFactor);
		//Paint the JEditorPane onto the graphics.
		this.paint(graphics2d);

		testFrame.dispose();
		return Printable.PAGE_EXISTS;
	}
}