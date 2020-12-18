package listManip;

import java.awt.geom.Path2D;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Vector;

import baryStuff.BaryPacket;
import complex.Complex;
import packing.PackData;

/**
 * Linked list of 'BaryPacket's holding information on
 * barycentric coordinate defined paths.
 * @author kens
 *
 */
public class BaryCoordLink extends LinkedList<BaryPacket> {

	private static final long 
	serialVersionUID = 1L;
	
	PackData packData;
	
	// Constructors
	public BaryCoordLink(PackData p) {
		super();
		packData=p;
	}
	
	public BaryCoordLink(PackData p,Vector<String> items) {
		super();
		packData=p;
//		addBaryLinks(items);
	}
	
	public BaryCoordLink(PackData p,String str) {
		super();
		packData=p;
//		addBaryLinks(str);
	}
	
	public BaryCoordLink(String str) {
		this((PackData)null,str);		
	}
	
	public BaryCoordLink(Vector<String> items) {
		this((PackData)null,items);
	}
	
	public BaryCoordLink() {
		this((PackData)null);
	}
	
	public boolean add(BaryPacket bp) {
		if (bp.vert[0]>0 && bp.vert[1]>0 && bp.vert[2]>0 &&
				(packData==null || bp.vert[0]<=packData.nodeCount &&
				bp.vert[1]<=packData.nodeCount &&
				bp.vert[2]<=packData.nodeCount))
			return super.add(bp);
		return false;
	}
	
/*
	public int addBaryLinks(String datastr) {
		Vector<String> items=StringUtil.string2vec(datastr,true);
		return addBaryLinks(items);
	}


	public int addBaryLinks(Vector<String>items) {
		if (items==null || items.size()==0)
			return 0;
		
		int count=0;
		boolean fvalues=false;
		
		Iterator<String> its=items.iterator();
		String str=its.next();

		// check for flag character; only first entry can be flag

		// if there is an accidental '-', remove it
		if (StringUtil.isFlag(str)) { 
			str=str.substring(1);
		}
		
		// must be non-numeric characters first
		if (!Character.isDigit(str.charAt(0))) {
			items.remove(0); // toss, but still held in str
			if (items==null || items.size()==0)
				return 0;
			char c=str.charAt(1);
			switch(c) {
			case 'f': // faces
			{
				if (packData==null) {
					throw new ParserException("there is no PackData for this BaryLink");
				}
				
				// get x y points
				PointLink ptlink=new PointLink(items);
				Iterator<Complex> pts=ptlink.iterator();
				while (pts.hasNext()) {
					Complex z=new Complex(pts.next());
					FaceLink faces=packData.tri_search(z);
					Iterator<Integer> fcs=faces.iterator();
					while (fcs.hasNext()) {
						int f=fcs.next();
						int []verts=packData.faces[f].vert;
//						if (add((BaryPoint)(BaryPoint.getBaryPoint(z,packData.rData[verts[0]].center,
//								packData.rData[verts[1]].center,packData.rData[verts[2]].center))))
//							count++;
					}

				
				fvalues=true;
				// TODO:
				break;
			}
			} // end of switch
		}
		
		return count;
	}
*/
	
	/**
	 * Make a distinct copy of this linked list; no check
	 * of validity of the indices.
	 * @return NodeLink
	 */
	public BaryCoordLink makeCopy() {
		Iterator<BaryPacket> blist=this.iterator();
		BaryCoordLink newlist=new BaryCoordLink();
		while (blist.hasNext()) {
			newlist.add(blist.next());
		}
		return newlist;
	}
	
	/**
	 * Abut a 'BaryLink' to the end of this one.
	 * @param moreNL
	 * @return count of new BaryPackets (some may be improper, 
	 *  some redundant)
	 */
	public int abutMore(BaryCoordLink moreNL) {
		if (moreNL==null || moreNL.size()==0)
			return 0;
		int ticks=0;
		Iterator<BaryPacket> mit=moreNL.iterator();
		BaryPacket bp=null;
		while (mit.hasNext()) {
			bp=mit.next();
			add(bp);
			ticks++;
		}
		return ticks;
	}
	
	/**
	 * Create path for packing p (not necessarily 'this.packData')
	 * @param p PackData
	 * @return Path2D.Double or null on error
	 */
	public Path2D.Double getPath(PackData p) {
		Path2D.Double path=new Path2D.Double();
		if (this.size()==0) return null;
		Iterator<BaryPacket> blk=this.iterator();
		BaryPacket bp=blk.next();
		Complex z=bp.getStartZ(p);
		Complex start=new Complex(z);
		path.moveTo(z.x,z.y);
		z=bp.getEndZ(p);
		path.lineTo(z.x,z.y);
		while (blk.hasNext()) {
			bp=blk.next();
			z=bp.getEndZ(p);
			path.lineTo(z.x,z.y);
		}
		if (start.minus(z).abs()<.000001)
			path.closePath();
		return path;
	}
	
	 /**
	  * Set 'packData' (which helps determine eligibility of entries)
	  * @param p PackData
	  */
	 public void setPackData(PackData p) {
		 packData=p;
	 }
}
