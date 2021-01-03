package branching;

import java.awt.Color;
import java.util.Iterator;
import java.util.Vector;

import komplex.CookieMonster;
import komplex.EdgeSimple;
import listManip.EdgeLink;
import listManip.FaceLink;
import listManip.NodeLink;
import listManip.VertexMap;
import math.Mobius;
import packing.PackData;
import util.UtilPacket;
import allMains.CirclePack;

import complex.Complex;

import deBugging.DebugHelp;
import exceptions.CombException;
import exceptions.ParserException;
import geometry.EuclMath;
import geometry.HyperbolicMath;

/**
 * A @see GenBranchPt of "shifted" type developed by Ed Crane; related to "shepherd" type.
 * 'singVert' is represented by two "sister" circles. These layout internally
 * tangent and the petals of 'singVert' march around one and then the other before
 * closing up. 
 * 
 * In this version of "shifted", the parameters are t and s. The angle 't' is 
 * measured around the big sister from the tangency point of 'singPetal'. The
 * double 's' is the ratio between radii, sister2/sister1.
 * 
 * The data for sister1 is in the usual storage for myPackData vertex 1, whereas 
 * sister2's info is kept in myPackData vertex 0; so we have to create kData[0]
 * and rData[0].
 * 
 * Note: many routines started out in @see FracBranching.
 * 
 * @author kens
 */

public class ShiftBranchPt extends GenBranchPt {
	
//	int myIndex;		          // vertex we are shifting (parent's index, 1 locally)
	int singPetal;                // base petal (parent's index, 2 locally (flower[0]).
	FaceLink borderLink;  		  // chain used for layout (local indexing)
	EdgeLink rad2parent;		  // for some interiors, need to send radii to parent: list (loc,par)
	EdgeLink radFromparent;		  // for bdry, need to get radii from parent: list (loc,par)
	
	// parameters
	double petalPhase;			  // The angle 't' where singPetal is tangent
	double sisterRatio;			  // Ratio sister2/sister1, normally in (0,1].
	
	// for conversion to/from 'shepherd' type
	int jump1,jump2;			  // petals of sister1, indices jump1 to jump2, are
								  //   tangent to sister2.

	// Constructor
	public ShiftBranchPt(PackData p,int bID,int v,int w,double aim) {
		super(p,bID,(FaceLink)null,aim);
		myType=GenBranchPt.SHIFTED;
		myIndex=v;
		singPetal=w;
		if (packData.isBdry(myIndex))
			throw new CombException("singular vert must be interior");
		
		// is singPetal actually a petal of singVert? default to index 0.
		if (packData.nghb(myIndex,singPetal)<0)
			singPetal=packData.kData[myIndex].flower[0];
		
		super.initLocalPacking(); // this calls createMyPack
	}

	/**
	 * Create packing via a cookie method; set 'vertexMap' and 'bdryLink'
	 */
	public PackData createMyPack() {
		PackData myPack=null;
		
		NodeLink petals=new NodeLink(packData);
		for (int j=0;j<packData.getNum(myIndex);j++)
			petals.add(packData.kData[myIndex].flower[j]);
		
		bdryLink=PackData.islandSurround(packData,petals);
		if (bdryLink==null)
			throw new CombException("Failed to build surround faces");

		// cookie
		NodeLink seedlist=new NodeLink(packData,myIndex);
		packData.gen_mark(seedlist,-1,true); // mark generations from myVert
		
		// Note: need to save/restore parent 'PoisonVerts'
		NodeLink holdPoison=null;
		if (packData.poisonVerts!=null && packData.poisonVerts.size()>0)
			holdPoison=packData.poisonVerts.makeCopy();
		
		// now set poisonVerts
		packData.poisonVerts=new NodeLink(packData,"{c:m.gt.2}");
		packData.poisonEdges=new EdgeLink(packData,"Ivw P");

  	  	CookieMonster cM=null; // (note: may get all of parent)
  	  	try {
  	  		cM=new CookieMonster(packData,new String("-v "+myIndex));
  	  		int outcome=cM.goCookie();
  	  		if (outcome<0)
  	  			throw new CombException();
  	  		if (outcome>0) {
  	  			myPack=cM.getPackData();
  	  	  		myPack.cpScreen=null;
  	  	  		myPack.hes=packData.hes;  
  	  	  		vertexMap=myPack.vertexMap.makeCopy();
  	  		}
  	  		else { // no vertices were cut out 
  	  			myPack=packData.copyPackTo();
  	  			myPack.cpScreen=null;
  	  			vertexMap=myPack.vertexMap=new VertexMap();
  	  			for (int v=1;v<=myPack.nodeCount;v++)
  	  				vertexMap.add(new EdgeSimple(v,v));
  	  		}
  	  		
  	  		// if spherical, best we can do for now is eucl
  	  		if (myPack.hes>0) 
  	  			myPack.hes=0;
  	  		
  	  		// swap nodes: 'singVert' is 1, 'singPetal' is 2
  	  		myPack.swap_nodes(vertexMap.findV(myIndex), 1);
  	  		myPack.swap_nodes(vertexMap.findV(singPetal), 2);
  	  		vertexMap=myPack.vertexMap.makeCopy(); // reset to get the swaps
  	  		myPack.alpha=1;
  	  		myPack.gamma=2;
  	  		myPack.setCombinatorics();
  	  		boolean debug=false;
  	  		if (debug) 
  	  			DebugHelp.debugPackWrite(myPack,"Shifted.p");
    	  		
  	  	} catch (Exception ex) {
  	  		packData.poisonVerts=holdPoison;
  	  		throw new CombException("cookie failed: "+ex.getMessage());
  	  	}
  	  	packData.poisonVerts=holdPoison;
  	  	
		// default colors 
		myPack.kData[1].color=new Color(200,0,0); // red
		myPack.kData[2].color=new Color(0,200,0); // green
		
		// need to convert 'bdryLink' to local face numbers
		borderLink=new FaceLink(myPack);
		Iterator<Integer> dL=bdryLink.iterator();
		while (dL.hasNext()) {
			int F=dL.next();
			borderLink.add(myPack.what_face(vertexMap.findV(packData.faces[F].vert[0]),
					vertexMap.findV(packData.faces[F].vert[1]),
					vertexMap.findV(packData.faces[F].vert[2])));
		}
	  		
		// vertex 0 is the second sister; don't change nodeCount
		myPack.kData[0]=myPack.kData[1].clone();
		myPack.rData[0]=myPack.rData[1].clone();
		
		myPack.set_aim_default();
		myPack.alloc_overlaps();

		matchCount=myPack.nodeCount;
		
		// 'singVert' and its petals are packed here; set aim < 0 in parent
		packData.setAim(myIndex,-1.0);
		for (int j=0;j<packData.getNum(myIndex)+packData.getBdryFlag(myIndex);j++)
			packData.setAim(vertexMap.findW(packData.kData[myIndex].flower[j]),-1.0);

		setPoisonEdges();
		return myPack;
	}
	
	public void delete() {
		
		// reset aims in parent
		if (!packData.isBdry(myIndex))
			packData.setAim(myIndex,2.0*Math.PI);
		for (int j=0;j<(packData.getNum(myIndex)+packData.getBdryFlag(myIndex));j++) {
			int k=vertexMap.findW(packData.kData[myIndex].flower[j]);
			if (!packData.isBdry(k))
				packData.setAim(k,2.0*Math.PI);
		}
		
		// remove poison edges
		packData.poisonEdges.removeUnordered(parentPoison);
	}
	

	/**
	 * Iterate to find how to distribute extra angle sum among the three
	 * fractured branch points to give a coherent packing; idea is James 
	 * Ashe's. The extra at each needs to be twice the angle there in the 
	 * triangle they form. This is a fixed point of the map that takes a
	 * given distribution, does the repacking, and puts out the resulting
	 * angles.
	 * @param cycles max iterations (if <0, set default)
	 * @return @see UtilPacket: 'rtnFlag' -1 on error, 'value' l^2 norm 
	 * of angle sum error.
	 */
	public UtilPacket riffleMe(int cycles) {
		if (cycles<0)
			cycles = 5; // subject to trial and error
		UtilPacket uP=new UtilPacket();	 
		uP.rtnFlag=-1;
		// TODO: do the calculations
		return uP;
	}
	
	/**
	 * Normalized position has sister 1 centered at the origin and
	 * sister 2 centered on the positive real axis (should be internally
	 * tangent). (Note that we may want to allow sister2 to be larger than
	 * sister1.) Update 'myHolonomy'.
	 * @param norm boolean, if true, freshly layout the first face an normalize
	 * @return double, error in myHolonomy
	 */
	public double layout(boolean norm) {

		int F=borderLink.get(0);

		// place first face? 
		if (norm) { // 1 should be at origin
			int indx=myPackData.face_index(F,1);
			if (indx<0) 
				throw new CombException("vert 1 is not in the first face");
			myPackData.place_face(F,indx);
		}
		
		// initial location of F
		Complex []firstF=new Complex[3];
		firstF[0]=myPackData.getCenter(myPackData.faces[F].vert[0]);
		firstF[1]=myPackData.getCenter(myPackData.faces[F].vert[1]);
		firstF[2]=myPackData.getCenter(myPackData.faces[F].vert[2]);
		
		// layout in order
		myPackData.layout_report(0,false,false);
		
		// final location of F (only different if 'bdryLink' is closed)
		Complex []lastF=new Complex[3];
		lastF[0]=myPackData.getCenter(myPackData.faces[F].vert[0]);
		lastF[1]=myPackData.getCenter(myPackData.faces[F].vert[1]);
		lastF[2]=myPackData.getCenter(myPackData.faces[F].vert[2]);

		// update myHolonomy
		myHolonomy=Mobius.mob_xyzXYZ(firstF[0],firstF[1],firstF[2],lastF[0],lastF[1],lastF[2],0,0);
		
		// rotate to get 2 on positive y-axis
		if (norm) {
			double theta=(-1.0)*myPackData.getCenter(2).arg();
			myPackData.rotate(theta+Math.PI/2.0);
		}
		
		return Mobius.frobeniusNorm(myHolonomy);
	}
	
	/**
	 * 
	 */
	public Mobius myHolonomy() {
		// TODO: have to do this
		return null;
	}
	
	/**
	 * Assume radii have been updated, what is the angle sum error?
	 * @return double, l^2 angle sum error.
	 */
	public double currentError() {
		// TODO: have to do this
		return 0.0;
	}
	
	/**
	 * Shifted branch point parameters (for SHIFTED type) are 'petalPhase' angle
	 * t=param[0]*PI and 'sisterRatio' s=param[1]. Normally we expect s to be in (0,1], 
	 * but let's allow s>1. t=(param[0]*PI)modulo(aim).
	 * 
	 * @param flagSegs Vector<Vector<String>>, normal flag sequences
	 * @return int, count of parameters set
	 */
	public int setParameters(Vector<Vector<String>> flagSegs) {
		int count=0;
		if (flagSegs==null || flagSegs.size()==0)
			throw new ParserException("missing parameters");
		Iterator<Vector<String>> fit=flagSegs.iterator();
		while (fit.hasNext()) {
			Vector<String> items=fit.next();
			try {
				
				// get petalPhase = (value*PI)modulo(aim).
				petalPhase=Double.parseDouble(items.get(0))*Math.PI;
				while (petalPhase>myAim)
					petalPhase -= myAim;
				while (petalPhase<0)
					petalPhase += myAim;
				
				// get sisterRatio > 0
				sisterRatio=Double.parseDouble(items.get(1));
				if (sisterRatio<=0)
					throw new ParserException("sister ratio must be > 0");
				myPackData.setRadius(0,sisterRatio*myPackData.getRadius(1));
				
				count += 2;
			} catch(Exception ex) {
				throw new ParserException();
			}
		}
		return count;
	}
		
	/**
	 * The parameters (for this version of "shifted") are angle 't' and
	 * radius ration 's'.
	 * @return String
	 */
	public String getParameters() {
		return new String("'shifted' branch point: aim "+
				myAim/Math.PI+"*Pi at vert "+myIndex+", base petal "+singPetal);
	}
	
	public String reportExistence() {
		return new String("Started 'shifted' branch point; center = "+myIndex+
				" and petal = "+singPetal);
	}
	
	public String reportStatus() {
		return new String("'shifted', ID "+branchID+": vert="+myIndex+", basepetal="+singPetal+
				", aim="+myAim+", holonomy err="+super.myHolonomyError());
	}
	
    /**
     * Compute angle at shift point in face where shift from tangency to
     * sister1 to tangency to sister 2 is made.
     * @param Rf double, rad of circle moving 'from'
     * @param Rt double, rad of circle moving 'to'
     * @param Af double, accumulated angle up to circle c2, assuming tangency pt of
     *    Rt and Rf is at angle 0.
     * @param R2 double, radius of c2
     * @param R3 double, radius of c3
     * @return
     */
    public double jumpAngle(double Rf,double Rt, double Af,double R2,double R3) {
//  !!!accum=PI+jasheroutine!!!
//    		Complex cRt_smaller=HES_Norm(xRadToH(Rt)-xRadToH(Rf),Math.PI); 
//    		//when Rt is smaller
//    		Complex cRt_larger=HES_Norm(xRadToH(Rt)-xRadToH(Rf),(Rt-Rf)/Math.abs(Rt-Rf)*Math.PI); 
//    		//when Rf is bigger
//    		Complex cRt = (Rt>=Rf) ? cRt_larger : cRt_smaller;
    		double Rotate = 0; if (Rf-Rt<0) Rotate =-1*Math.PI; 
    		//determines rotate pt. is 0 or Pi. 
    		Complex cRt = HES_Norm(HyperbolicMath.x_to_h_rad(Rt)-HyperbolicMath.x_to_h_rad(Rf),
    				Rotate); 
    		//Rf needs to be centered at (0,0). If Rf<Rt, Rt is at (-|Rf-Rt|,0); 
    		// else it is (|Rf-Rt|,0).
    		Complex c2=HES_Norm(HyperbolicMath.x_to_h_rad(Rf)+HyperbolicMath.x_to_h_rad(R2),Af); 
    		//normalized center of R2. Af should be given assuming p is at 0.
    		Complex xPt=new Complex(.5,0); //arbitrary pt on the x-axis 
    		double d = genPtDist(cRt,c2);
    		double u=HyperbolicMath.x_to_h_rad(Rt)+HyperbolicMath.x_to_h_rad(R3);//TODO allow for inv distances her 
   			double op=HyperbolicMath.x_to_h_rad(R2)+HyperbolicMath.x_to_h_rad(R3);// and here
   			double At=genAngleSides(d,u,op);
   			return At-1*genAngleSides(d,genPtDist(cRt,xPt),genPtDist(c2,xPt)); 
    		}

    /**
     * Compute angle at shift point in face where shift from large
     * to small circle is made.
     * This method replaces 'jumpAngle'
     * @param Rf, rad of circle moving 'from'
     * @param Rt, rad of circle moving 'to'
     * @param Af, accumulated angle up to circle c2
     * @param R2, radius of c2
     * @param R3, radius of c3
     * @return
     */
    public double jumpAngle2(double Rf,double Rt, double Af,double R2,double R3) {
//  !!!accum=PI+jasheroutine!!!
    		Complex cRt_smaller=new Complex((HES_Norm(HyperbolicMath.x_to_h_rad(Rt)-HyperbolicMath.x_to_h_rad(Rf),
    				2*Math.PI).real()),0); 
    		//when Rt is little circle
    		Complex cRt_bigger=new Complex(-(HES_Norm(HyperbolicMath.x_to_h_rad(Rt)-HyperbolicMath.x_to_h_rad(Rf),
    				2*Math.PI).real()),0); 
    		//when Rf is big circle
    		Complex cRt = (Rt>=Rf) ? cRt_bigger : cRt_smaller;
    		
    		Complex c2=HES_Norm(HyperbolicMath.x_to_h_rad(Rf)+HyperbolicMath.x_to_h_rad(R2),Af); //normalized center of R2
    		Complex xPt=new Complex(0.5,0); //arbitrary pt on the x-axis 
    		double d = genPtDist(cRt,c2);
    		double u=HyperbolicMath.x_to_h_rad(Rt)+HyperbolicMath.x_to_h_rad(R3);
   			double op=HyperbolicMath.x_to_h_rad(R2)+HyperbolicMath.x_to_h_rad(R3);
   			double At=genAngleSides(d,u,op);
   			double ang= -1*genAngleSides(genPtDist(cRt,c2),genPtDist(cRt,xPt)
    					,genPtDist(c2,xPt))+At; 
   			return ang;
    		}
    
    /**
     * Computes the 'ghost radius'. A radius which replaces the shiftpoint
     * to a shiftpoint neighbor's flower so that it can be computed like a
     * normal CP.
     * @param Rv, center radius, normally a shift neighbor.
     * @param rl, petal before shift point.
     * @param rr, petal after shift point.
     * @param gr, best guess for intial ghost radius.
     * @param theta, desired angle for (Rv;rl,gr,rr)
     * @return
     */
    public double ghostrad(double Rv, double rl, double rr, double gr, double theta) {
        double bestR=gr;
        double bestAS=quadfind(Rv, rl, rr, bestR);
		double diff=bestAS-theta;
		int counter=0;
		while (Math.abs(diff)>PackData.TOLER && counter < 100000) { //TODO: adjust
			double factor=.5;  // TODO: adjust
			if (diff<0) factor = 1/factor;
			double newdiff=diff;
			double newR=bestR;
			// keep adjusting until change sign
			while (diff/newdiff>0) { 
				newR = bestR*(factor);
				newdiff=quadfind(Rv, rl, rr, newR)-theta;
				counter++;
				if (Math.abs(newdiff)<PackData.TOLER || counter>=10) {
					return newR;
				}
				if (diff/newdiff>0) {
					bestR=newR;
				}
			}
			
			double lowR;
			double highR;
			if (factor<1) {   
				lowR=newR;
				highR=bestR;
			}
			else {
				lowR=bestR; 
				highR=newR;
			}
			bestR=(lowR+highR)/2.0;
			diff=quadfind(Rv, rl, rr, bestR)-theta;
			while(Math.abs(diff)>PackData.TOLER && counter< 10000) { //TODO adjust 
				if (diff<0) { //prev diff>0
					lowR=bestR;
				}
				else {
					highR=bestR;
				}
				bestR=(lowR+highR)/2.0;
				diff=quadfind(Rv, rl, rr, bestR)-theta;
			} // end of inner while
		} // end of outer while
        return bestR;
    }
    
    /**
     * Compute the angle at Rv in two faces {Rv,rr,gr} and
     * {Rv,gr,rr}. If rr or rl is negative, then there is just
     * the other face. 
     * @param Rv, rad whose angle we're computing
     * @param rl
     * @param rr
     * @param gr (ghost radius)
     * @return double, angle
     */
    public double quadfind(double Rv, double rl, double rr, double gr) {
    	double angl=0.0;
    	double angr=0.0;
    	UtilPacket uPq = new UtilPacket();
    	if (rl>0.0) {
    		genCosOverlap(Rv, rl, gr, uPq);
    		angl=Math.acos(uPq.value);
    	}
    	if (rr>0.0) {
    		genCosOverlap(Rv,rr,gr,uPq);
    		angr=Math.acos(uPq.value);
    	}
    	return angl+angr;
    }

    /**
     * Compute the Complex coords in the unit disc for point with given
     * hyperbolic distance from origin and positively oriented angle 
     * from x-axis.
     * @param D double = Hyperbolic distance from origin
     * @param A double = angle from x-axis
     * @return Complex coord
     */
    public Complex HES_to_Ecent(double D, double A) {
    	switch (packData.hes) {
    		case -1: { //Hyperbolic case
    			Complex r = new Complex((Math.exp(D)-1)/(Math.exp(D)+1),0);
    			return r.times((new Complex(0,A)).exp());
    		}
    		case 1: { //Spherical case
    			CirclePack.cpb.errMsg("spherical case not complete");
    			return new Complex(2,2);
    		}
    		default: { //Euclidean Case
    			Complex r = new Complex(D,0);
    			return r.times((new Complex(0,A)).exp());
    		}
    	}
    }
    
    /**
     * Gives the Complex coords on the unit disc for the provided 
     * distance from center and positively oriented angle from x-axis
     * @param D = distance from origin
     * @param A = Pos. angle from x-axis
     * @return comlex coord
     */
    public Complex HES_Norm(double D, double A) {
    	D = Math.abs(D);
    	int G = packData.hes;
    	switch (G) {
    		case -1: { //Hyperbolic case
    			Complex r = new Complex((Math.exp(D)-1)/(Math.exp(D)+1),0);
    			return r.times((new Complex(0,A)).exp());
    		}
    		case 1: { //Spherical case
    			CirclePack.cpb.errMsg("spherical case not complete");
    			return new Complex(2,2);
    		}
    		default: { //Euclidean Case
    			Complex r = new Complex(D,0);
    			return r.times((new Complex(0,A)).exp());
    		}
    	}
    }
    
	/**
	 * Using Law of Cosines, find angle in a triangle face given lengths of sides.
	 * @param right double, right of angle
	 * @param left double, left of angle
	 * @param opp double, opposite of angle
	 * @param uP @see Utilpacket
	 * @return double, angle (depending on geometry)
	 */
	public double genAngleSides(double right, double left, double opp) {
		switch (packData.hes) {
			case -1: { //Hyperbolic case 
				return Math.acos((Math.cosh(right)*Math.cosh(left)-Math.cosh(opp))
					/(Math.sinh(right)*Math.sinh(left)));
			}
			case 1: { //Spherical case
				return Math.acos((Math.cos(right)*Math.cos(left)-Math.cos(opp))
					/(Math.sin(right)*Math.sin(left)));
			}
			default: { //Euclidean case
				return Math.acos((right*right+left*left-opp*opp)
					/(2*right*left));
			}
		}
	}
	
    /**
     * Directs cos overlap checks according to geometry
     * @param R, radius at vertex
     * @param rp, positively oriented petal
     * @param rn, negatively oriented petal
     * @param up, utilpacket
     * @return 
     */
    public boolean genCosOverlap(double R, double rn, double rp, UtilPacket uP) {
    	int G = packData.hes; //geometry, 0=Euc -1=Hyp 1=Sphr
    	switch(G) {
    		case -1: {
    			//uP.value = ((Math.cosh(R+rn)*Math.cosh(R+rp)-Math.cosh(rn+rp))
					//	/(Math.sinh(R+rn)*Math.sinh(R+rp)));
    			//return true;
	    			return HyperbolicMath.h_cos_s_overlap(R, rn, rp, 1.0, 1.0, 1.0, uP);
	    			//ask about x radii;
    			}
    		case 1: {
    			CirclePack.cpb.errMsg("cannot compute spherical case; need to insert spherical case.");
    			return false;
    		}
    		default: {
    			return EuclMath.e_cos_overlap(R, rn, rp, uP);
    		}
    	}
    }
    
    /**
     * Convert phase angle to tangency point in the complex plane. The 'phase' 
     * angle of a point on the union of sister1 and sister2 circles is like an 
     * argument, but adjusted for twin circles. Sisters 1 and 2 are to be tangent, 
     * and in normalized position, sister 1 will be at the origin and sister 2 
     * internally tangent at a point on the positive x-axis. Suppose radii are 
     * R and r, resp. Define curve gamma around sister1, then around sister2 back
     * to start by
     * 
     *   gamma(t)= R exp(it),       t in [0,2pi]; 
     *   gamma(t)=(R-r)+r exp(it),  t in [2pi,4pi]
     *   
     * and define gamma(t)=gamma((t)mod(4pi)) in general.
     * @param t double, treated mod(4) and multiplied by Pi
     * @param R double, radius of sister1 (generally the larger)
     * @param r double, radius of sister2
     */
    public static Complex phase2pt(double t,double R,double r) {
    	
    	// get t mod(4.0)
    	while (t>4.0)
    		t -= 4.0;
    	while (t<0.0)
    		t +=4.0;
    	
    	if (t<=2.0)
    		return new Complex(R,t*Math.PI).exp();
    	else
    		return new Complex(r,t*Math.PI).exp().add(new Complex(R-r,0.0));
    }
    
	/**
	 * We make the outside edges of the flower of singVert into poison edges
	 */
	public int setPoisonEdges() {
		EdgeLink elink=new EdgeLink(packData);
		int v=myPackData.kData[1].flower[0];
		for (int j=1;j<=myPackData.getNum(1);j++) {
			int k=myPackData.kData[1].flower[j];
			elink.add(new EdgeSimple(vertexMap.findW(v),vertexMap.findW(k)));
			elink.add(new EdgeSimple(myIndex,vertexMap.findW(k)));
			v=k;
		}
		parentPoison=elink;
		return elink.size();
	}    
	
	/**
	 * Vert 0 is sister2, so we have to adjust this center.
	 */
	public void applyMob(Mobius mob) {
		myPackData.setCenter(0,mob.apply(myPackData.getCenter(0)));
	}

	/**
	 * No additional parent circles need to be placed
	 * @return 
	 */
	public int placeMyCircles() {
		return 1;
	}
	
}
