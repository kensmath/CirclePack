package branching;

import java.util.Iterator;
import java.util.Vector;

import exceptions.DataException;
import exceptions.ParserException;
import ftnTheory.GenModBranching;
import listManip.FaceLink;
import math.Mobius;
import packing.PackData;
import util.StringUtil;

/**
 * A @see GenBranchPt of the "singular" type conceived by Ed Crane. 
 * The bdryLink surrounds the specified face 'singFace' and the
 * connection circles (so named by James Ashe). The face vertices 
 * 1,2,3 correspond in order with vertices of 'singFace' in the parent.
 * We add a central vertex (with aim 4*pi) and 3 chaperone circles 
 * between the original vertices of 'singFace'. Two overlap angles are 
 * the parameters (along with a third so they sum to PI). Note that
 * because the vertices of 'singFace' will end up overlapping, they 
 * will not get the right angle sum in the parent. We therefore have
 * to scoop them inside the bdryLink and layout more circles in the
 * branch point.
 * Changes made to 'packData':
 *   * add to 'poisonEdges' via EdgeLink 'parentPoison'.
 *   * aims of parent set <0 for vertices of 'myIndex' face (so parent
 *     won't repack them).
 *   * send radius of v to parent after repack if transData[v]<0.
 *   * set overlaps in parent for edges of 'myIndex' face (this affects
 *     its computation of angle sums. 
 *   
 *    
 * @author kens, May 2012
 *
 */

public class SingBrModPt extends GenBrModPt {
	
//	public int myIndex;		// face supporting the branching
	double []cos_overs;         // cos of overlap angle attached to an edge.
	FaceLink borderLink;  		// chain used for layout (local indexing)
	int []chap;					// chaperone circles: sj is across from vert j, j=1,2,3 (chap[0] empty)
	int []connect;				// 'connection' circles (local indices); jth is across from j vert (connect[0] empty)
	int newBrSpot;				// central chaperone should be radius 0, centered at point where three other chaperone intersect
	
	// Constructor
	public SingBrModPt(GenModBranching gmb,int bID,double aim, int f, double o1,double o2) {
		super(gmb,bID,(FaceLink)null,aim);
		myType=GenBranchPt.SINGULAR;
		myIndex=f;
		
		// set default overlaps to  pi/3
		cos_overs=new double[3];
		cos_overs[0]=cos_overs[1]=cos_overs[2]=0.5; // cosine of PI/3.0
		
		// debug help
		System.out.println("sing attempt: a = "+aim/Math.PI+"; f = "+f+"; overlaps/Pi "+o1+" "+o2+" "+(1.0-o1-o2));
		
		resetOverlaps(o1,o2);
	}

	/**
	 * Create packing via a cookie method; set 'vertexMap' and 'bdryLink'
	 */
	public int modifyPackData() {
	
		// TODO: 
		
		return 1;
	}		
	
	/**
	 * Intend to delete this branch point, so reestablish parent packing.
	 */
	public void dismantle() {
	}
		
	/**
	 * Assume radii have been updated, what is the angle sum error?
	 * @return double, l^2 angle sum error.
	 */
	public double currentError() {
		p.fillcurves();
		return p.angSumError();
	}
		
	/**
	 * Singular branch point parameters are the overlap angles associated
	 * with the three edges of 'singFace'. This is similar to 'fractured'
	 * branch points, but since the overlap angles must sum to Pi, we 
	 * specify overlaps for 1,2 and for 2,3, then compute that for 3,1. 
	 * Vertices 1,2,3 are packData.getFaceVerts(myIndx)[0],[1],[2], resp.
	 * so overlap [j] is for circles forming edge opposite vert [j+1]. 
	 * @param Vector<Vector<String>> flagSegs, overlaps
	 * @return int, 3 for success
	 */
	 public int setParameters(Vector<Vector<String>> flagSegs) {
		int count=0;
		boolean gotovers=false;
		double []ovlp=new double[2];
		
		// get flags: -a aim, -o o1 o2
		Iterator<Vector<String>> fit=flagSegs.iterator();
		while (fit.hasNext()) {
			Vector<String> items=fit.next();
			try {
				if (!StringUtil.isFlag(items.get(0)))
					throw new ParserException("usage: -a aim -o o1 o2");
				String str=items.remove(0);
				switch (str.charAt(1)) {
				case 'a': // new aim
				{
					myAim=Double.parseDouble(items.get(0));
					count++;
					break;
				}
				case 'o': // overlaps
				{
					for (int i=0;i<2;i++) {
						ovlp[i]=Double.parseDouble(items.remove(0));
						if (ovlp[i]<0 || ovlp[i]>1.0)
							throw new ParserException("overlap not in [0,1]");
						count++;
					}
					if ((ovlp[0]+ovlp[1])>1.0000001)
						throw new ParserException("sum of o1 o2 overlaps not in [0,1]");
					gotovers=true;
					break;
				}
				} // end of switch
			} catch(Exception ex) {
				throw new ParserException("usage: -a aim, -o o1 o2");
			}
		} // end of while
		
		// set overlaps
		if (gotovers)
			count+=resetOverlaps(ovlp[0],ovlp[1]);
		
		return count;
	 }
	
	 /**
	  * For an already established 'singular' branch point, this just
	  * adjusts the overlap angles. 'o1' and 'o2' in [0,1] (they are
	  * multiplied by Pi here) 
	  * @param o1 double
	  * @param o2 double
	  * @return 1
	  */
	 public int resetOverlaps(double o1,double o2) {
		 double o3=1.0-(o1+o2);
		if (o1<0.0 || o1>1.0 || o2<0.0 || o2>1.0 || o3<0.0)
			throw new DataException("'singular' usage: 2 overlaps in [0,1], sum <= 1");

		cos_overs[0]=Math.cos(o1*Math.PI);
		cos_overs[1]=Math.cos(o2*Math.PI);
		cos_overs[2]=Math.cos(o3*Math.PI);
		
		// To get overlap ov on edge (2,3), we have vertex 1 overlap 
		//    chap2 and chap3 (i.e. the neighboring chaperone circles) by ov.

		double invdist=Math.cos(Math.PI-Math.acos(cos_overs[0])-Math.acos(cos_overs[1]));
//		packData.set_single_invDist(v1, v2, invdist);
		invdist=Math.cos(Math.PI-Math.acos(cos_overs[1])-Math.acos(cos_overs[2]));
//		packData.set_single_invDist(v2, v3, invdist);
		invdist=Math.cos(Math.PI-Math.acos(cos_overs[2])-Math.acos(cos_overs[0]));
//		packData.set_single_invDist(v3, v1, invdist);
		
		return 1;
	 }
	 
	 /**
	  * See if there are special actions for display on screen of parent packing.
	  * If so, do them, remove them, and pass the rest to 'super'. May flush some
	  * commands designed for other types of branch points.
	  * 
	  * @param flagSegs flag sequences
	  * @return int count of display actions
	  */
	public int displayMe(Vector<Vector<String>> flagSegs) {
		return 0;
	}
	
	/**
	 * 
	 * @return String
	 */
	public String getParameters() {
		return new String("Singular branch face, aim "+
				myAim/Math.PI+"*Pi on face "+myIndex);
	}
	
	public String reportExistence() {
		return new String("Started 'singular' branch point; face = "+myIndex);
	}
	
	public String reportStatus() {
		return new String("'singular', ID "+branchID+": face="+myIndex+
				", aim="+myAim+", holonomy err="+
				Mobius.frobeniusNorm(getLocalHolonomy()));
	}

}
