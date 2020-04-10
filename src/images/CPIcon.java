package images;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.net.URL;

import javax.swing.ImageIcon;

import allMains.CPBase;
import allMains.CirclePack;
import exceptions.ParserException;

/**
 * An icon object for CirclePack; connects file name with ImageIcon.
 * @author kens
  */
public class CPIcon {

	public ImageIcon baseIcon;  // underlying icon; used, eg, in iconChooser.
	public ImageIcon imageIcon; // icon with embellishments to show with button
	static ImageIcon defaultIcon=CPIcon.CPImageIcon("GUI/inq_tile.jpg");
	String name; // just the subdirectory/name, 'Mainwindow.IconDirectory/' is assumed. 
	
	static ImageIcon teardropImg
		=CPIcon.CPImageIcon("script/teardrop.png");
	static ImageIcon popImg
		=CPIcon.CPImageIcon("main/menuPop.png");
	static ImageIcon hotPtImg
		=CPIcon.CPImageIcon("script/hotCross.png"); // cursorHotPt.png");
	static ImageIcon handyImg
		=CPIcon.CPImageIcon("main/handy.png");
	static ImageIcon astrixImg
		=CPIcon.CPImageIcon("script/astrix.png");
	
	// Constructor
	public CPIcon(String iconname) {
		name=iconname;
		baseIcon = CPIcon.CPImageIcon(iconname);
		if (baseIcon==null) {
			CirclePack.cpb.errMsg("Couldn't load icon "+iconname);
			baseIcon=defaultIcon;
		}
		imageIcon=baseIcon;
	}
	
	public ImageIcon getImageIcon() {
		return imageIcon;
	}

	public ImageIcon getBaseIcon() {
		return baseIcon;
	}
	
	public Dimension getDimension() {
		return (new Dimension(imageIcon.getIconWidth(),imageIcon.getIconHeight()));
	}
	
	public String getIconName() {
		return name;
	}
	
	public String toString() {
		return baseIcon.toString();
	}
	
	public void setImageIcon(ImageIcon imgIcon) {
		if (imgIcon!=null) imageIcon=imgIcon;
	}
	
	/**
	 * This returns modified image with embellishments:
	 *  1. letter for mnemonic
	 *  2. Abbreviation for 'PackExtender's (not yet implemented)
	 *  3. blue drop for dropable with #XY (or #xy)
	 *  4. hotpoint for cursor, lower left
	 *  5. hand for cursor, lower right
	 *  6. popup menu indicator
	 * Note: icon can change size
	 * @param startImg, ImageIcon
	 * @param key, String, for key associated to tool
	 * @param boolean: xy_tear (blue teardrop for dropable at XY point)
	 *                 hot_arrow (arrow point (lower left) for mouse)
	 *                 handy (hand at lower right, precedence over tear)
	 *                 menu_pop
	 * @return 
	 */
	public static ImageIcon embellishIcon(ImageIcon startImg,
			String key,boolean xy_tear,boolean hot_arrow,boolean handy,
			boolean menu_pop) {
		
		if (startImg==null) return null;
		if (key!=null && key.trim().length()>0)
			key=key.trim().substring(0,1);
		else key=null;
		
		Image img=startImg.getImage();
		int high=img.getHeight(null);
		int wide=img.getWidth(null);
		int voffset=0;
		int hoffset=0;
		
		// extra height for xy_tear, handy, key, and hotpoint
		if (xy_tear || handy || hot_arrow) 
			high +=3;
		if (xy_tear || handy)
			wide +=6;
		else if (hot_arrow) {
			wide +=6;
			hoffset+=5;
		}
		if (key!=null) {
			high +=3;
			voffset+=3;
		}

		BufferedImage bufImage=null;
		try {
			bufImage=new BufferedImage(wide,high,BufferedImage.TYPE_INT_ARGB);
		} catch (Exception ex) {
			if (wide<1 || high <1) {
				wide=high=22;
				bufImage=new BufferedImage(wide,high,
						BufferedImage.TYPE_INT_ARGB);
			}
			else throw new ParserException("Problem embellishing: "+ex.getMessage());
		}
		Graphics2D g2d=bufImage.createGraphics();
		g2d.drawImage(img,new AffineTransform(1,0,0,1,hoffset,voffset),null);
		
		// in future add small hand if dragging allowed; 'handy.png' is 12x12
		if (handy) {
//			g2d.drawImage(handyImg.getImage(),new AffineTransform(1,0,0,1,wide-12,high-12),null);
		}

		// add 'teardrop' if the command has an '#XY', 'teardrop.png' is 7x12
		if (xy_tear && !handy) {
			g2d.drawImage(teardropImg.getImage(),new AffineTransform(1,0,0,1,wide-7,high-12),null);
		}

		// add hotpoint cursor spot (lower left), 'cursorHotPt.png' is 10x12
		if (hot_arrow) {
			g2d.drawImage(hotPtImg.getImage(),new AffineTransform(1,0,0,1,0,high-10),null);
		}
		
		// add letter/number, upper right hand corner
		if (key!=null && key.trim().length()>0) {
			if (key.equals("*")) {
				g2d.drawImage(astrixImg.getImage(),new AffineTransform(1,0,0,1,wide-15,0),null);
			}
			else {
				Rectangle2D.Double rect=new Rectangle2D.Double(wide-11,0,10,12);
				g2d.setColor(Color.WHITE);
				g2d.fill(rect);
				g2d.setColor(Color.BLACK);
				g2d.draw(rect);
				try {
					g2d.setFont(new Font("truetype",Font.ROMAN_BASELINE,10));
					g2d.drawString(key,wide-8,9);
				} catch (Exception ex) {}
			}
		}
		
		// Expand length for arrow for popup
		if (menu_pop) {
			// have to extend the basic image to make room
			BufferedImage bufImage2=new BufferedImage(wide+18,high,BufferedImage.TYPE_INT_ARGB);
			Graphics2D gd=bufImage2.createGraphics();
			// load current image
			gd.drawImage(bufImage,new AffineTransform(1,0,0,1,0,0),null);
			gd.drawImage(popImg.getImage(),new AffineTransform(1,0,0,1,wide+1,2),null);
			gd.dispose();
			return new ImageIcon(bufImage2);
		}

		return new ImageIcon(bufImage); // Note: may have returned earlier
	}
	
	/**
	 * Applies 'embellishIcon' to 'baseIcon', puts in 'imageIcon'
	 * @param key (for key execution)
	 * @param xy_tear
	 * @param hot_arrow
	 * @param handy
	 * @param menu_pop
	 */
	public void embellishMe(String key,boolean xy_tear,
			boolean hot_arrow,boolean handy,boolean menu_pop) {
		imageIcon=embellishIcon(baseIcon,key,
				xy_tear,hot_arrow,handy,menu_pop);
	}

	/**
	 * Return icon as in 'embellishIcon', but start with 'baseIcon'
	 * @param key (key execution)
	 * @param xy_tear
	 * @param hot_arrow
	 * @param handy
	 * @param menu_pop
	 */
	public ImageIcon embellishBase(String key,boolean xy_tear,
			boolean hot_arrow,boolean handy,boolean menu_pop) {
		return embellishIcon(baseIcon,key,
				xy_tear,hot_arrow,handy,menu_pop);
	}

	
	/**
	 * Circle packing version of 'ImageIcon'; need to look in right place for icons
	 * so that we can 'jar' up the code.
	 * @param iconname should start without a leading '/'
	 * @return ImageIcon, null on error
	 * 
	 * TODO: Should use 'GlobFilter' 'GlobResources' to find
	 * iconname in any subdirectory of "Icons/" if it doesn't
	 * already have a subdirectory name.
	 */
	public static ImageIcon CPImageIcon(String iconname) {
		URL url=CPBase.getResourceURL("/Icons/"+iconname);
		if (url==null) 
			CirclePack.cpb.errMsg("failed to find icon: '"+iconname+"'");
		ImageIcon ii= new ImageIcon(CPBase.getResourceURL("/Icons/"+iconname));
		return ii;
	}
	
	public CPIcon clone() {
		CPIcon nIn = new CPIcon(name);
		nIn.imageIcon=new ImageIcon(imageIcon.getImage());
		nIn.baseIcon=new ImageIcon(baseIcon.getImage());
		return nIn;
	}
}
