package branching;

import java.util.Vector;

import canvasses.DisplayParser;
import circlePack.PackControl;
import complex.Complex;
import dcel.DcelCreation;
import ftnTheory.GenModBranching;
import komplex.EdgeSimple;
import listManip.EdgeLink;
import listManip.FaceLink;
import listManip.VertexMap;
import math.Mobius;
import packing.PackData;
import util.UtilPacket;

/**
 * This is a traditional branch point, extra angle assigned to an interior circle.
 * It is defined in this convoluted way to make processing fit the mold of more
 * general branch types.
 * 
 * Changes made to 'packData':
 *   * add to 'poisonEdges' via EdgeLink 'parentPoison'.
 *   * Aim of 'myIndex' set to -1 (so parent doesn't repack)
 *   * send radius of 'myIndex' to parent after repack.
 *    
 * @author kstephe2
 *
 */
public class TradBranchPt extends GenBranchPt {

//	int myIndex;      // vertex we are branching (parent's index, 1 locally)
	
	// Constructor
	public TradBranchPt(PackData p,int bID,double aim,int v) {
		super(p,bID,(FaceLink)null,aim);
		myType=GenBranchPt.TRADITIONAL;
		myIndex=v;
		super.initLocalPacking();
		
		// debug help
		System.out.println("traditional branch attempt: a = "+aim/Math.PI+"; v = "+v);
		
	}

	// create the local packing, 
	public PackData createMyPack() {
		
		// build from a seed
		PackData myPack=DcelCreation.seed(packData.countFaces(myIndex), packData.hes);
		myPack.setAim(1,myAim);
	
		// set up vertexMap, 'bdryLink', transData, etc.
		matchCount=packData.countFaces(myIndex)+1; // petals plus center
		vertexMap=new VertexMap();
		transData=new int[matchCount+1];
		int[] faceFlower=packData.getFaceFlower(myIndex);
		for (int j=0;j<packData.countFaces(myIndex);j++) {
			int vv=packData.kData[myIndex].flower[j];
			vertexMap.add(new EdgeSimple(j+2,vv));
			transData[j+2]=vv;
		}
		vertexMap.add(new EdgeSimple(1,myIndex)); // add center
		transData[1]=-myIndex;
//		if (!packData.isBdry(myIndex)) // close up? 
//			bdryLink.add(bdryLink.get(0)); 
		
		packData.setAim(myIndex,-1.0); // 1 is packed locally 
		
		setPoisonEdges();
		return myPack;
	}
	
	public void delete() {
		
		// reset parent aim at 'myIndex'
		if (!packData.isBdry(myIndex))
			packData.setAim(myIndex,2.0*Math.PI);
		
		// remove poison edges
		packData.poisonEdges.removeUnordered(parentPoison);
	}

	/**
	 * This is a traditional repack computation at 1. Radius is set
	 * in the parent.
	 * @param cycles int, not used
	 * @return uP @see UtilPacket: 'value' is angle sum error at 1. 
	 */
	public UtilPacket riffleMe(int cycles) {
		// get parent radii
		for (int i=2;i<=matchCount;i++)
			myPackData.setRadius(i,packData.getRadius(transData[i]));
		UtilPacket uP=new UtilPacket();
		uP.rtnFlag=-1;
	    if (myPackData.hes<0) 
			uP.rtnFlag +=myPackData.h_riffle_vert(1,myAim);
		else if (myPackData.hes==0)
			uP.rtnFlag +=myPackData.e_riffle_vert(1,myAim);
		uP.value = Math.abs(myPackData.getCurv(1)-myAim);
		packData.setRadius(myIndex,myPackData.getRadius(1));
		return uP;
	}
	
	/**
	 * Assume radii have been updated, what is the angle sum error?
	 * @return double, l^2 angle sum error.
	 */
	public double currentError() {
		myPackData.fillcurves();
		return myPackData.angSumError();
	}
	
	/**
	 * No parameters to set for this branch type.
	 * @return 1
	 */
	public int setParameters(Vector<Vector<String>> flagSegs) {
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
		Vector<String> items=new Vector<String>(2);
		int n=0;
		int fs=flagSegs.size();
		for (int j=fs-1;j>=0;j--) {
			items=flagSegs.get(j);
			String str=items.get(0);

			// flush options designed for other types of branch points
			if (str.startsWith("-h") || str.startsWith("-y") || str.startsWith("-j") || str.startsWith("-s")) { 
			}

			flagSegs.remove((Object)items);
		} // end of while

		// pass rest of display commands to 'super'
		n+=DisplayParser.dispParse(myPackData,packData.cpScreen,flagSegs);
		if (n!=0)
			PackControl.canvasRedrawer.paintMyCanvasses(packData,false); 
		return n;
	}
	
	
	/**
	 * Return string with aim at v
	 * @return String
	 */
	public String getParameters() {
		return new String("Traditional branch point, aim "+
				myPackData.getAim(1)/Math.PI+"*Pi at vertex "+vertexMap.findW(1));
	}
	
	
	public String reportExistence() {
		return new String("Started 'traditional' branch point; center = "+myIndex);
	}
	
	public String reportStatus() {
		return new String("'traditional', ID "+branchID+": vert="+myIndex+
				", aim="+myAim+", holonomy err="+super.myHolonomyError());
	}

	/**
	 * Use usual layout methods for the branch point by itself
	 * @return int, 0 on error or exception
	 */
	public double layout(boolean norm) {
		int opt=2; // use all plotted neighbors, 1=use only those of one face 
		boolean errflag=false; // only use 'well-plotted' in layout
		boolean dflag=false;   // debugging help 
		myPackData.fillcurves();
		try {
			myPackData.CompPackLayout(); // comp_pack_centers(errflag,dflag,opt,CommandStrParser.LAYOUT_THRESHOLD);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		// myHolonomy is just rotation by angle sum in this case
		myPackData.fillcurves();
		myHolonomy=Mobius.rotation(myPackData.getCurv(1)/Math.PI);
		
		return Mobius.frobeniusNorm(myHolonomy);
	}

	/**
	 * Set 'plotFlag' for petals of vert 1, get positions from parent,
	 * then compute and set the center of vertex 1 locally and for 'myIndex' 
	 * in parent.
	 * @return 0 on error
	 */
	public int placeMyCircles() {
		// get parent centers
		for (int i=2;i<=matchCount;i++)
			myPackData.setCenter(i,new Complex(packData.getCenter(transData[i])));
		for (int v=2;v<=matchCount;v++)
			myPackData.kData[v].plotFlag=1;
		int ans=myPackData.fancy_comp_center(1,0,0,myPackData.countFaces(1),2,false,false,0.000001);
		myPackData.kData[1].plotFlag=ans;
		packData.setCenter(myIndex,new Complex(myPackData.getCenter(1)));
		return ans;
	}

	/**
	 * Make all edges in the flower of 'myIndex' into poison edges.
	 * ('myPackData' not yet defined when we call here, so refer
	 * directly to 'packData' itself.)
	 * @return int, count of edges in 'parentPoison'.
	 */
	public int setPoisonEdges() {
		EdgeLink elink=new EdgeLink(packData);
		int w=packData.kData[myIndex].flower[0];
		for (int j=1;j<=packData.countFaces(myIndex);j++) {
			int k=packData.kData[myIndex].flower[j];
			elink.add(new EdgeSimple(w,k));
			elink.add(new EdgeSimple(myIndex,k));
			w=k;
		}
		parentPoison=elink;
		return elink.size();
	}
	
	
}
