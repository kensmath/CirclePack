package printStuff;

import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.io.File;
import allMains.CirclePack;

/**
 * For sending files to a printer. Hope for platform independence.
 * May need user to configure a print command in "Configuration"
 * window.
 * @author kens
 *
 */
public class PrintUtil {
	/**
	 * Send a jpg File to the printer.
	 * @param jpgfile, File
	 */
	public static void PrintJPG(File jpgfile) {
		// set up printerJob and page format
		PrinterJob printerJob = PrinterJob.getPrinterJob();
		PageFormat pageFormat = printerJob.defaultPage();

		// NOTE: Paper() chooses letter size with 1 inch margins
		pageFormat.setPaper(new Paper());

		//Set the job name to the selected file name.
		printerJob.setJobName(jpgfile.getName());

		// get printable
		JPGPrintable jpgPrintable = new JPGPrintable(jpgfile);

		printerJob.setPrintable(jpgPrintable, pageFormat);

		// TODO: print dialog is hanging (on office machine)
//		if (printerJob.printDialog()) { 
			try {
				printerJob.print();
			} catch (PrinterException e) {
				CirclePack.cpb.errMsg("Printing error: " + e.getMessage());
			}
//		}
	}
	
/* ---------------- old version --------------------
		//Construct a new, empty print request attribute set, essentially the requirements of a print job.

	    PrintRequestAttributeSet pras = new HashPrintRequestAttributeSet();

	    //Add an attribute to the requirements. In this case, set the job to print a single copy.

	    pras.add(new Copies(1));

	    //Fill array pss with all printers capable of printing with selected requirements and file type,
	    //passed as a DocFlavor.

	    PrintService pss[] = PrintServiceLookup.
	    	lookupPrintServices(DocFlavor.INPUT_STREAM.JPEG, pras);

	    //Empty array means no printers with the passed capabilities are avaiable.
	    if (pss.length == 0) throw new RuntimeException("No printer services available.");

	    //Select the printer. In this case, the first available printer is arbitrarily selected.
	    PrintService ps = pss[0];
	    System.out.println("Printing to " + ps);

	    //Creates a PrintJob capable of handling data from the specified document flavors.
	    DocPrintJob job = ps.createPrintJob();

	    try {
	    	// Create a bytestream from the data to be printed.
	    	FileInputStream fin = new FileInputStream(jpgfile);

	    	// Create a new Doc, specifying the bytestream, DocFlavor, and DocAttributeSet.
	    	Doc doc = new SimpleDoc(fin, DocFlavor.INPUT_STREAM.PNG, null);
	    
	    	// Print the Doc using the print request attribute set.
	    	job.print(doc, pras);
	    	fin.close();
	    } catch (Exception ex) {
	    	CirclePack.cpb.errMsg("Printing error: "+ex.getMessage());
	    }
	    
	    CirclePack.cpb.msg("File "+jpgfile+" sent to printer");
	  }
	  */	
}