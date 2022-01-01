package branching;

import java.util.Iterator;
import java.util.Vector;

import dcel.HalfEdge;
import exceptions.ParserException;
import ftnTheory.GenModBranching;
import komplex.EdgeSimple;
import listManip.EdgeLink;
import listManip.FaceLink;
import listManip.VertexMap;
import math.Mobius;
import packing.PackData;

/**
 * The "quad" branching was intended to branching that occurs 
 * precisely on an edge between two faces. The numerics are
 * sensitive near tangency points where two circles and two
 * interstices come together, so James Ashe had an idea on
 * a special type of generalized branch point. Unfortunately,
 * it was not implemented, and as of 10/21, neither of us 
 * recall the idea.
 * br
 * @author kstephe2
 *
 */
public class QuadBrModPt extends GenBrModPt {
	// faces sharing edge supporting the branching ('myIndex' set to singFace_f)
	public int singFace_f;		
	public int singFace_g;
	double cosOver;        		// cos of overlap angle between 1 and 2.
	public int fracFace;        // which face (f=1, g=2) is doing the flipping? 
	FaceLink borderLink;  		// chain used for layout (local indexing)
	
	// TODO: tmp fields during transition to new version
	
	VertexMap vertexMap;
	int matchCount;
	PackData myPackData;

	// Constructor
	public QuadBrModPt(GenModBranching g,int bID,HalfEdge edge,double aim) {
		super(g,bID,aim);
		gmb=g;
		myType=GenBrModPt.FRACTURED;
		myEdge=edge;
		singFace_f=myEdge.face.faceIndx;
		singFace_g=myEdge.twin.face.faceIndx;
		
		myPackData=p;
		
		modifyPackData();

		success=true;
	}
	
	// ***************** needed abstract methods *******************
	public int modifyPackData() {
		return 1;
	}

	public void dismantle() {
		
	}
	
	public void renew() {
		
	}
	
	/**
	 * Create packing via a cookie method; set 'vertexMap' and 'bdryLink'
	 * @return @see PackData
	 */
	public PackData createMyPack() {
		return null;
	}
	
	public void delete() {
	}
	
	/**
	 * TODO: 
	 */
	public double currentError() {
		// TODO: yet to code this
		return 0.0;
	}
	
	/**
	 * Quadface branch point parameters are the overlap angles associated with 
	 * the shared edge and face designation for layout. Data should be "n x",
	 * where n is 1 or 2 (meaning, face f or g, resp) and overlap is x*Pi,
	 * x in [-1,1]. 
	 * @param flagSegs Vector<Vector<String>>
	 * @return int, count
	 */
	public int setParameters(Vector<Vector<String>> flagSegs) {
		int count=0;
		if (flagSegs==null || flagSegs.size()==0)
			throw new ParserException("missing parameters");
		Iterator<Vector<String>> fit=flagSegs.iterator();
		while (fit.hasNext()) {
			Vector<String> items=fit.next();
			try {
				// which face?
				int n=Integer.parseInt(items.get(0));
				if (n==2)
					fracFace=singFace_g;
				else
					fracFace=singFace_f;
				
				// what overlap?
				double ovlp=Double.parseDouble(items.get(1));
				if (ovlp<-1.0 || ovlp>1.0)
					throw new ParserException("overlap not in [-1,1]");
				cosOver=Math.cos(ovlp*Math.PI);
				myPackData.set_single_invDist(1,2,cosOver);
				count=1;
			} catch(Exception ex) {
				throw new ParserException();
			}
		}
		return count;	
	}
	
	/**
	 * 
	 * @return String
	 */
	public String getParameters() {
		return new String("QuadFace branch point: aim "+
				myAim/Math.PI+"*Pi on faces "+singFace_f+" "+singFace_g);
	}
	
	public String reportExistence() {
		return new String("Started 'QuadFace' branch point; faces = "+singFace_f+" and "+singFace_g);
	}
	
	public String reportStatus() {
		return new String("'QuadFace', ID "+branchID+": faces f,g ="+singFace_f+", "+singFace_g+
				", aim="+myAim+", holonomy err="+
				Mobius.frobeniusNorm(getLocalHolonomy()));
	}
}
