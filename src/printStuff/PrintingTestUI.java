package printStuff;

import input.CPFileManager;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;


/**
 * Standalone test program for print actions
 * @author Alexander Fawkes
 *
 */
public class PrintingTestUI extends JPanel
implements ActionListener {
	private static final long serialVersionUID = 1L;
	JButton browseButton;
	JButton pageFormatButton;
	JButton printButton;
	JLabel fileLabel;
	JFileChooser chooser;
	PrinterJob printerJob;
	PageFormat pageFormat;
	BufferedImage image;
	JPGPrintable jpgPrintable;
	HTMLPrintable htmlPrintable;

	public static void main(String[] args) {
		createGUI();
	}

	private static void createGUI() {
		JFrame mainFrame;

		mainFrame = new JFrame();
		mainFrame.setSize(560, 65);
		mainFrame.setLocation(240, 120);
		mainFrame.setResizable(false);
		mainFrame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		mainFrame.add(new PrintingTestUI());
		mainFrame.pack();
		mainFrame.setVisible(true);
	}

	PrintingTestUI () {
		browseButton = new JButton("Browse");
		browseButton.setVerticalTextPosition(SwingConstants.CENTER);
		browseButton.setHorizontalTextPosition(SwingConstants.CENTER);
		browseButton.setActionCommand("browse");
		browseButton.addActionListener(this);

		pageFormatButton = new JButton("Page Setup");
		pageFormatButton.setVerticalTextPosition(SwingConstants.CENTER);
		pageFormatButton.setHorizontalTextPosition(SwingConstants.CENTER);
		pageFormatButton.setActionCommand("pageformat");
		pageFormatButton.addActionListener(this);
		pageFormatButton.setEnabled(false);

		printButton = new JButton("Print");
		printButton.setVerticalTextPosition(SwingConstants.CENTER);
		printButton.setHorizontalTextPosition(SwingConstants.CENTER);
		printButton.setActionCommand("print");
		printButton.addActionListener(this);
		printButton.setEnabled(false);

		fileLabel = new JLabel("Select a JPG or HTML file to continue.");
		fileLabel.setVerticalTextPosition(SwingConstants.CENTER);
		fileLabel.setHorizontalTextPosition(SwingConstants.LEADING);
		fileLabel.setPreferredSize(new Dimension(280, 20));

		printerJob = PrinterJob.getPrinterJob();
		pageFormat = printerJob.defaultPage();

		add(fileLabel);
		add(browseButton);
		add(pageFormatButton);
		add(printButton);
	}

	public void actionPerformed(ActionEvent e) {

		//Browse button is pressed. Open JFileChooser.
		if ("browse".equals(e.getActionCommand())) {
			chooser = new JFileChooser();
			chooser.setFileFilter(new JPGHTMLFilter());
			int returnVal = chooser.showOpenDialog(null);
			if (returnVal == JFileChooser.APPROVE_OPTION) {
				String extension = CPFileManager.getFileExt(chooser.getSelectedFile());
				if (extension != null) {
					if (extension.equalsIgnoreCase("jpg") ||
							extension.equalsIgnoreCase("jpeg")) {
						//If file is JPG, initialize the JPG Printable.
						jpgPrintable = new JPGPrintable(chooser.getSelectedFile());
						printerJob.setPrintable(jpgPrintable, pageFormat);
					}
					else if (extension.equalsIgnoreCase("html") ||
							extension.equalsIgnoreCase("htm")) {
						//If file is HTML, initialize the HTML Printable.
						htmlPrintable = new HTMLPrintable(chooser.getSelectedFile());
						printerJob.setPrintable(htmlPrintable, pageFormat);
					}
					else {
						//User has somehow selected an invalid file.
						System.exit(1);
					}
				}
				fileLabel.setText(chooser.getSelectedFile().getName());
				//Set the job name to the selected file name.
				printerJob.setJobName(chooser.getSelectedFile().getName());
				//Enable the page format and print buttons.
				pageFormatButton.setEnabled(true);
				printButton.setEnabled(true);
			}
		}
		//Page format button is pressed.
		if ("pageformat".equals(e.getActionCommand())) {
			/* pageDialog() brings up the page dialog window and returns the
			   PageFormat instance with any changes. */
			pageFormat = printerJob.pageDialog(pageFormat);
		}
		//Print button is pressed.
		if ("print".equals(e.getActionCommand())) {
			if (printerJob.printDialog()) {
				try {
					/* print() brings up the print dialog window and attempts to
					   print according to the current Printable on confirmation. */
					printerJob.print();
				} catch (PrinterException ex) {
					ex.printStackTrace();
					System.exit(1);
				}
			}
		}
	}
}