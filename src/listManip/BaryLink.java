package listManip;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Vector;

import komplex.EdgeSimple;
import komplex.Face;
import packing.PackData;
import util.StringUtil;
import allMains.CPBase;
import baryStuff.BaryPoint;
import complex.Complex;

import exceptions.ParserException;
import geometry.EuclMath;
import geometry.HyperbolicMath;
import geometry.CircleSimple;

/**
 * Linked list of barycentric coordinate 'BaryPoint's 
 * @author kens
 *
 */
public class BaryLink extends LinkedList<BaryPoint> {

	private static final long 
	serialVersionUID = 1L;
	
	PackData packData;
	
	// Constructors
	public BaryLink(PackData p) {
		super();
		packData=p;
	}
	
	public BaryLink(PackData p,Vector<String> items) {
		super();
		packData=p;
		addBaryLinks(items);
	}
	
	public BaryLink(PackData p,String str) {
		super();
		packData=p;
		addBaryLinks(str);
	}
	
	public BaryLink(String str) {
		this((PackData)null,str);		
	}
	
	public BaryLink(Vector<String> items) {
		this((PackData)null,items);
	}
	
	public BaryLink() {
		this((PackData)null);
	}
	
	public boolean add(BaryPoint bp) {
		return super.add(bp);
	}
	
	public int addBaryLinks(String datastr) {
		Vector<String> items=StringUtil.string2vec(datastr,true);
		return addBaryLinks(items);
	}

	public int addBaryLinks(Vector<String>items) {
		if (items==null || items.size()==0)
			return 0;
		
		int count=0;
		
		String str=items.get(0);

		// check for flag character; only first entry can be flag

		// if there is an accidental '-', remove it
		if (StringUtil.isFlag(str)) { 
			str=str.substring(1);
		}
		
		if (str.contains("Bli")) {
			abutMore(CPBase.Blink);
			return CPBase.Blink.size();
		}
		else if (str.contains("bli")) {
			if (packData==null) 
				throw new ParserException("no packing associated with this BaryLink");
			abutMore(packData.blist);
			return packData.blist.size();
		}
		
		// If there is a flag, we get it, process, and return. 
		if (StringUtil.isFlag(str) || (str.charAt(0)!='-' && !Character.isDigit(str.charAt(0)) && str.charAt(0)!='.')) {
			items.remove(0); // toss, but still held in str
			if (items==null || items.size()==0)
				return 0;
			char c=str.charAt(0);
			switch(c) {
			case 'f': // faces
			{
				if (packData==null) {
					throw new ParserException("there is no PackData for this BaryLink");
				}
				
				// get x y points from rest of strings
				PointLink ptlink=new PointLink(items);
				Iterator<Complex> pts=ptlink.iterator();
				
				// create 'BaryPoint's for any triangles containing x,y.
				while (pts.hasNext()) {
					Complex z=new Complex(pts.next());
					FaceLink faces=packData.tri_search(z);
					Iterator<Integer> fcs=faces.iterator();
					while (fcs.hasNext()) {
						int f=fcs.next();
						int []verts=packData.faces[f].vert;
						BaryPoint bp=null;
						if (packData.hes==0) { // eucl
							bp=EuclMath.e_pt_to_bary(z,packData.getCenter(verts[0]),
									packData.getCenter(verts[1]),packData.getCenter(verts[2]));
						}
						else if (packData.hes<0) { // hyp
							bp=HyperbolicMath.h_pt_to_bary(z,packData.getCenter(verts[0]),
									packData.getCenter(verts[1]),packData.getCenter(verts[2]));
						}
						if (bp!=null) {
							bp.face=f;
							if (add(bp))
								count++;
						}
					}
				}
				break;
			}
			case 'i': // interstice
			{
				if (packData==null) {
					throw new ParserException("there is no PackData for this BaryLink");
				}
				
				if (packData.hes>0) {
					throw new ParserException("spherical interstice barycentric coods not yet computed");
				}

				// get x y points from rest of strings
				PointLink ptlink=new PointLink(items);
				Iterator<Complex> pts=ptlink.iterator();
				
				// find any triangles containing x,y.
				while (pts.hasNext()) {
					Complex z=new Complex(pts.next());
					FaceLink faces=packData.tri_search(z);
					Iterator<Integer> fcs=faces.iterator();
					while (fcs.hasNext()) {
						int f=fcs.next();
						Face face=packData.faces[f];
						Complex []m=new Complex[3];
						
						// find tangency points (m[j] opposite to w[j]) 
						m[2]=packData.tangencyPoint(new EdgeSimple(face.vert[0],face.vert[1]));
						m[0]=packData.tangencyPoint(new EdgeSimple(face.vert[1],face.vert[2]));
						m[1]=packData.tangencyPoint(new EdgeSimple(face.vert[2],face.vert[0]));
						
						// eucl circle through them: map this and points to the unit disc
						CircleSimple incir=EuclMath.circle_3(m[0],m[1],m[2]);
						Complex []nz=new Complex[3];
						for (int j=0;j<3;j++) {
							nz[j]=m[j].minus(incir.center);
							nz[j]=nz[j].divide(nz[j].abs());
						}
						Complex Z=z.minus(incir.center).divide(incir.rad);
						
						// now find angle-type barycentric coords for z relative to ideal triangle
						try {
							BaryPoint bpt=HyperbolicMath.ideal_bary(Z, nz[0],nz[1], nz[2]);
							bpt.face=f;
							
							// check that pt is in the closed interstice
							if (bpt.b0>=0 && bpt.b1>=0 && bpt.b2>=0) {
								add(bpt);
								count++;
							}
						} catch (Exception ex) {}
					} // end of while for faces
				} // end of while for points
				

				break;
			}
			} // end of switch

			return count;
		}
		
		// else, raw data of form "f b1 b2"
		while (items.size()>2) {
			try {
				int f=Integer.parseInt(items.remove(0));
				double c1=Double.parseDouble(items.remove(0));
				double c2=Double.parseDouble(items.remove(0));
				BaryPoint bp=new BaryPoint(c1,c2);
				if (f>0)
					bp.face=f;
				if (add(bp))
					count++;
			} catch (Exception ex) {
				throw new ParserException("BaryPoint input usage: f c1 c2");
			}
		}

		return count;
	}
	/**
	 * Pick first bary point of list described in string.
	 * @param p PackData
	 * @param str String
	 * @return BaryPoint, null on error
	 */
	public static BaryPoint grab_one_barypoint(PackData p,String str) {
		BaryLink blist=new BaryLink(p,str);
		if (blist!=null && blist.size()>0) 
			return (BaryPoint)(blist.get(0));
		return null;
	}
	
	/**
	 * Pick first bary point off first string of first vector of vector
	 * of string vectors. 
	 * @param p PackData
	 * @param flagsegs Vector<Vector<String>>
	 * @return BaryPoint, null on error
	 */
	public static BaryPoint grab_one_vert(PackData p,Vector<Vector<String>> flagsegs) {
		try {
			Vector<String> its=(Vector<String>)flagsegs.get(0);
			BaryLink blk=new BaryLink(p,its);
			return blk.getFirst();
		} catch (Exception ex) {
			return null;
		}
	}
		
	/**
	 * Make a distinct copy of this linked list; no check
	 * of validity of the indices.
	 * @return NodeLink
	 */
	public BaryLink makeCopy() {
		Iterator<BaryPoint> blist=this.iterator();
		BaryLink newlist=new BaryLink();
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
	public int abutMore(BaryLink moreNL) {
		if (moreNL==null || moreNL.size()==0)
			return 0;
		int ticks=0;
		Iterator<BaryPoint> mit=moreNL.iterator();
		BaryPoint bp=null;
		while (mit.hasNext()) {
			bp=mit.next();
			add(bp);
			ticks++;
		}
		return ticks;
	}

	 /**
	  * Set 'packData' (which helps determine eligibility of entries)
	  * @param p PackData
	  */
	 public void setPackData(PackData p) {
		 packData=p;
	 }
}
