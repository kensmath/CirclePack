package util;

import java.awt.Image;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

/** 
 * static routines to load, scale, and/or store images.
 * @author kstephe2
 *
 */
public class GetScaleImage {
	
	/**
	 * scale given image (equally in both directions) to fit inside given width x height
	 * @param bI @see BufferedImage
	 * @param width int
	 * @param height int
	 * @return @see BufferedImage or null
	 */
	public static BufferedImage scaleBufferedImage(BufferedImage bI,int width,int height) {
		if (bI==null)
			return null;
		int wide=bI.getWidth(null);
		int high=bI.getHeight(null);
		if (wide<=0 || high<=0) 
			return null;
		double wf=(double)wide/(double)width;
		double hf=(double)high/(double)height;
		double denom=1.0;
		if (wf>1.0 || hf>1.0) {
			denom=wf;
			denom=(hf>wf)? hf:wf;
		}
		wide=(int)((double)wide/denom);
		high=(int)((double)high/denom);
		BufferedImage outImage = new BufferedImage(wide,high,BufferedImage.TYPE_INT_RGB); 
		outImage.createGraphics().drawImage(bI.getScaledInstance(wide,high,Image.SCALE_SMOOTH),
				new AffineTransform(),null);
		return outImage;
	}

}
