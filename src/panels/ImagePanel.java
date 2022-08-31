package panels;

import images.ThumbNail;
import input.CPFileManager;
import input.FileDialogs;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.io.File;
import java.io.IOException;
import java.util.Random;
import java.util.Vector;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;

import packing.PackData;
import printStuff.PrintUtil;
import script.IncludedFile;
import util.StringUtil;
import allMains.CPBase;
import allMains.CirclePack;
import circlePack.PackControl;
import exceptions.InOutException;
import frames.AboutFrame;

/**
 * Class for organizing screenshots; thumbnails, name, 
 * vectors of images in /tmp, etc.
 * @author kens
 *
 */
public class ImagePanel extends JPanel implements ActionListener, ImageObserver {

	private static final long 
	serialVersionUID = 1L;
	
	// screendump format
	static String IMG="jpg";
	
	static JTextField nameField;
	static JTextField dirField;
	static JPanel thumbPanel;
	public static JScrollPaneVertical scrollPane;
	public static int imageCount;
	public static Vector<ThumbNail> thumbNails;
	public int id;    // unique identifier for file names
	public JPopupMenu thumbMenu;
//	static private FileSystem fs = FileSystem.getFileSystem();

	// TODO: want 'gif' or 'jpg' options
	
	// Constructor
	public ImagePanel() {
		super();
		this.setLayout(new BorderLayout());
		imageCount=0;
		thumbNails=new Vector<ThumbNail>(5);
		thumbMenu=createThumbMenu();

		// file name info
		id = new Random().nextInt(32000); // random number for directory
		JPanel nP=new JPanel(new FlowLayout());
		nP.add(new JLabel(" Dir:"));
		dirField=new JTextField(new String(System.getProperty("java.io.tmpdir")+
				File.separator+"cp_"+id+File.separator),20);
		nP.add(dirField);
		nP.add(new JLabel("Base name:"));
		nameField=new JTextField("cpShot",10);
		nP.add(nameField);
		
		add(nP,BorderLayout.NORTH);
		
//		thumbPanel=new JPanel(new FlowLayout(FlowLayout.LEFT));
		thumbPanel=new JPanel();
		thumbPanel.setBackground(Color.LIGHT_GRAY);
		scrollPane=new JScrollPaneVertical(thumbPanel);
//		,ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		add(scrollPane,BorderLayout.CENTER);
	}

	/**
	 * Store the screen as a jpg
	 * @param p @see PackData (null in case we want pair double screen)
	 * @return boolean
	 */
	public boolean storeCPImage(PackData p) {
		String num=String.format("%03d",imageCount);
		String prefix=dirField.getText().trim();
		if (prefix.startsWith("~")) {
			try {
				prefix=new String(CPFileManager.HomeDirectory.getCanonicalPath()+
					prefix.substring(1));
			} catch (Exception ex) {
			}
		}
		String tmpName=new String(prefix+File.separator+nameField.getText().trim()
				+"-"+num+"."+IMG);
		File locFile = new File(tmpName);
		BufferedImage bI;
		try {
			
			// create any directories necessary
			File canonFile=null;
	        try {
	            canonFile = locFile.getCanonicalFile();
	        	String parent = canonFile.getParent();
	        	if (parent!=null) {
	        		File parentFile=new File(parent);
	        		if (!parentFile.exists()) 
	        			if (!parentFile.mkdirs())
	        				throw new InOutException("Couldn't create directory '"+parent+"'");
	        	}
	        } catch (IOException e) {
	            return false;
	        }
			if (p!=null) // capture 'packImage' for this packing
				bI=p.cpDrawing.packImage;
			else { // capture Map frame 'pairPanel' of two packings
				BufferedImage domPI=PackControl.mapPairFrame.getDomainCPS().packImage;
				BufferedImage rangePI=PackControl.mapPairFrame.getRangeCPS().packImage;
				int pairWidth=domPI.getWidth()+9+rangePI.getWidth(); // border: each end 2, mid 3
				int pairHeight=domPI.getHeight()+6; // border: 2 top/bottom
				
				// put in new BufferedImage
				bI=new BufferedImage(pairWidth,pairHeight,BufferedImage.TYPE_INT_RGB);
				Graphics2D g2=(Graphics2D)bI.getGraphics();
				
				// domain 
				g2.drawImage(domPI,3,3,domPI.getWidth(),domPI.getHeight(),this);
				// range
				g2.drawImage(rangePI,domPI.getWidth()+6,3,
						rangePI.getWidth(),rangePI.getHeight(),null);
			}
			
			ImageIO.write(bI,IMG,locFile);
		} catch (Exception ex) {
			CirclePack.cpb.myErrorMsg("Screen dump to '"+tmpName+"' has failed.");
			return false;
		}
		CirclePack.cpb.msg("Saved screendump to '"+tmpName+"'");
		ThumbNail tnail=null;
		try {
			tnail=new ThumbNail(locFile,thumbMenu);

		} catch (InOutException ine) {
			CirclePack.cpb.myErrorMsg(ine.getMessage());
			return false;
		}
		thumbNails.add(tnail);
		thumbPanel.add(tnail);
		scrollPane.revalidate();
		imageCount++;
		return true;
	}
	
	public JPopupMenu createThumbMenu() { // menu for the ThumbNail
		JPopupMenu bMenu = new JPopupMenu("Managing");
		JMenuItem menuItem;

		menuItem = new JMenuItem("Delete");
		menuItem.setActionCommand("delete thumb");
		menuItem.addActionListener(this);
		bMenu.add(menuItem);

		menuItem = new JMenuItem("Set as 'About'");
		menuItem.setActionCommand("set about");
		menuItem.addActionListener(this);
		bMenu.add(menuItem);
		
		menuItem = new JMenuItem("Export");
		menuItem.setActionCommand("copy snap");
		menuItem.addActionListener(this);
		bMenu.add(menuItem);
		
		menuItem = new JMenuItem("Print");
		menuItem.setActionCommand("print (lpr)");
		menuItem.addActionListener(this);
		bMenu.add(menuItem);
		

/*		menuItem = new JMenuItem("Swap to right");
		menuItem.setActionCommand("swap_right");
		menuItem.addActionListener(this);
		bMenu.add(menuItem);
		
		menuItem = new JMenuItem("Swap to left");
		menuItem.setActionCommand("swap_left");
		menuItem.addActionListener(this);
		bMenu.add(menuItem);*/
		
		return bMenu;
	}
	
	/**
	 * Set the screendump image format: choices are "jpg", "png",
	 * "gif", "bmp", "wbmp".
	 * @param img
	 * @return 1 on success
	 */
	public int setIMG(String img) {
		if (img.equalsIgnoreCase("JPG"))
			IMG="jpg";
		else if (img.equalsIgnoreCase("PNG"))
			IMG="png";
		else if (img.equalsIgnoreCase("GIF"))
			IMG="gif";
		else if (img.equalsIgnoreCase("BMP"))
			IMG="bmp";
		else if (img.equalsIgnoreCase("WBMP"))
			IMG="wbmp";
		else return 0;
		return 1;
	}

	/**
	 * Sets the JTextField to given directory
	 * @param dir
	 */
	public void setDirectory(String dir) {
		File getdir =new File(dir.trim());
		dirField.setText(getdir.getPath());
//		else
//			dirField.setText(new String(System.getProperty("java.io.tmpdir")+
//					File.separator+"cp_"+id+File.separator));
	}
	
	/**
	 * Set the name base for the files
	 * @param nf String
	 */
	public void setNameField(String nf) {
		String name=StringUtil.grabNext(nf);
		nameField.setText(name);
	}

	public void actionPerformed(ActionEvent e) {
		JMenuItem source=(JMenuItem)e.getSource();
		JPopupMenu jpm=(JPopupMenu)source.getParent();
		ThumbNail whichThumb=(ThumbNail)jpm.getInvoker();
		String command = e.getActionCommand();
		if (command.equals("delete thumb")) {
			// TODO: how do you kill yourself?
			thumbNails.remove((Object)whichThumb);
			thumbPanel.remove((Component)whichThumb);
		}
		else if (command.equals("set about")) {
			String confirmDialogText = "Set this as script 'About' image?";
			int result = JOptionPane.showConfirmDialog(null, confirmDialogText, "Confirm", JOptionPane.YES_NO_OPTION);

			// User confirmed? then read file, store (not encoded)
			if (result == JOptionPane.YES_OPTION) {
//				int tmp_id=new Random().nextInt(10000);
//				String temp_name=new String("AboutImage"+tmp_id+"."+IMG);
				String temp_name=new String("AboutImage."+IMG);
				File temp=null;
				try {
	
					// open new file
					temp=new File(System.getProperty("java.io.tmpdir"),temp_name);
					temp.createNewFile();
					temp.deleteOnExit();
					
					// put into java image
					BufferedImage img = null;
					try {
					    img = ImageIO.read(whichThumb.imageFileName);
					} catch (IOException ex) {
						throw new InOutException("problem loading image");
					}
					
					// scale to size for saving
					BufferedImage after=util.GetScaleImage.
						scaleBufferedImage(img,AboutFrame.ICONWIDTH,AboutFrame.ICONHEIGHT);
					
					// write scaled image to temp
					ImageIO.write(after,IMG,temp);
					
					// store as About ImageIcon
					CPBase.scriptManager.myScriptTag=new ImageIcon(after);
					CPBase.scriptManager.hasChanged=true;
					PackControl.scriptHover.scriptTitle(CPBase.scriptManager.scriptName,true);

					
				} catch (Exception ex) {
					throw new InOutException("problem with 'AboutImage'");
				}
				
				// remove any previous AboutImage
				for (int i=(CPBase.scriptManager.includedFiles.size()-1);i>=0;i--) {
					IncludedFile nextfile=CPBase.scriptManager.includedFiles.get(i);
					if (nextfile.dataType==IncludedFile.ABOUT_IMAGE) {
						nextfile.tmpFile.delete();
						CPBase.scriptManager.includedFiles.remove(i);
					}
				}
				
				// this call adds id to name, stores, and repopulates data
				CPBase.scriptManager.includeNewFile(temp_name);

			}
		}
		else if (command.equals("print (lpr)")) {
			// TODO: how to set up printing
			PrintUtil.PrintJPG(whichThumb.imageFileName);
		}
		else if (command.equals("copy snap")) {
			File jpgOutFile=null;
			if ((jpgOutFile = FileDialogs.saveDialog(FileDialogs.JPG, true)) != null) {
				try {
					CPFileManager.copyFile(whichThumb.imageFileName,jpgOutFile);
				} catch (Exception ex) {
					CirclePack.cpb.errMsg("Failed in copying "+whichThumb.imageFileName);
					return;
				}
				CirclePack.cpb.msg("Saved JPG file to "+jpgOutFile.getPath());
			} 
			else return;
		}
		
		thumbPanel.repaint();
	}
	
	/** Override the ImageObserver imageUpdate method and monitor
	 * the loading of the image. Set a flag when it is loaded.
	 **/
	public boolean imageUpdate (Image img, int info_flags,
	                             int x, int y, int w, int h) {
	    if (info_flags != ALLBITS) {
	        // Indicates image has not finished loading
	        // Returning true will tell the image loading
	        // thread to keep drawing until image fully
	        // drawn loaded.
	        return true;
	    } else {
	        return false;
	    }
	  } // imageUpdate
}
