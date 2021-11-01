package ftnTheory;

import java.util.Vector;

import allMains.CirclePack;
import complex.Complex;
import dcel.CombDCEL;
import dcel.D_PairLink;
import dcel.D_SideData;
import dcel.PackDCEL;
import dcel.RedHEdge;
import exceptions.CombException;
import exceptions.ParserException;
import komplex.RedEdge;
import komplex.RedList;
import math.Mobius;
import packing.PackData;
import packing.PackExtender;
import rePack.d_EuclPacker;
import util.CmdStruct;
import util.TriAspect;
import util.TriData;

public class D_ProjStruct extends PackExtender {
	public TriAspect[] aspects;
	public static int PASSES=10000;
	
	// Constructor
	public D_ProjStruct(PackData p) {
		super(p);
		packData=p;
		extensionType="PROJSTRUCT";
		extensionAbbrev="PS";
		toolTip="'ProjStruct' is for handling discrete projective structures, "+
			"that is, projective structures associated with circle packings.";
		registerXType();
		if (running) {
			packData.packExtensions.add(this);
		}
	}
	
	/**
	 * This is were the user's commands are "parsed"
	 */
	public int cmdParser(String cmd, Vector<Vector<String>> flagSegs) {
		Vector<String> items = null;
		int count=0;
		
		// ======== affine ===========
		if (cmd.startsWith("affine")) {
			// this routine is tailored for tori: specify side-pair
			// scaling in an attempt to build general affine tori
			if (aspects==null || aspects.length!=(packData.faceCount+1))
				setupAspects();

			if (packData.genus != 1 || packData.getBdryCompCount()>0) {
				int cnt=0;
				msg("Simply connected case: 'affine' defaults to all 'labels' 1");
				for (int f=1;f<=packData.faceCount;f++) {
					for (int j=0;j<3;j++) 
						aspects[f].labels[j]=1.0;
					cnt++;
				}
				return cnt;
			}

			// get the user-specified
			double A = 1.2; // default
			double B = .75;
			try {
				items = flagSegs.get(0);
				A = Double.parseDouble((String) items.get(0));
				B = Double.parseDouble((String) items.get(1));
			} catch (Exception ex) {
			}

			boolean result = affineSet(packData,aspects,A, B);
			if (!result)
				Oops("affine has failed");
			msg("Affine data set: A = " + A + " B = " + B);
			return 1;
		}
		
		// ======== affpack ===========
		else if (cmd.startsWith("affpack")) {
			d_EuclPacker e_packer=new d_EuclPacker(packData,-1);
			
			// set 'triData' to 'aspects'
			e_packer.pdcel.triData=aspects;
			
			// repack using 'labels' for computation
			e_packer.affinePack(-1);
			
			// store results as radii 
			e_packer.reapLabels();
			
			packData.packDCEL.layoutPacking();
			
			
		}
		
		return super.cmdParser(cmd, flagSegs);
	}
	
	/**
	 * Normalize an existing torus packing. 
	 * @param p PackData
	 * @return count
	 */
	public static int normalizeTorus(PackData p) {
		PackDCEL pdcel=p.packDCEL;
		if (pdcel.pairLink==null || pdcel.pairLink.size()!=5)
			throw new ParserException("packing in not 2-sidepair torus");
		Complex[] Z=new Complex[4];
		for (int j=1;j<=4;j++) {
			D_SideData sd=pdcel.pairLink.get(j);
			Z[j-1]=sd.startEdge.getCenter();
		}
			
		Mobius mob=Mobius.mob_NormQuad(Z);
		return Mobius.mobiusDirect(p,mob);
	}
	
	/**
	 * create the 'aspects' array, putting current
	 * radii/centers from packData into 'TriData.labels'
	 * and 'TriAspect.center' slots.
	 */
	public void setupAspects() {
		aspects=new TriAspect[packData.faceCount+1];
		for (int f=1;f<=packData.faceCount;f++) {
			aspects[f]=new TriAspect(packData.hes);
			TriAspect tas=aspects[f];
			tas.baseEdge=pdc.faces[f].edge;
			tas.face=tas.baseEdge.face.faceIndx;
			for (int j=0;j<3;j++) {
				int v=packData.faces[f].vert[j];
				tas.vert[j]=v;
				// set 'labels'
				tas.labels[j]=packData.getRadius(v);
				// set 'centers'
				tas.setCenter(packData.getCenter(v),j);
			}
			// set 'sides' from 'center's
			tas.centers2Sides();
		}
	}
	
	/**
	 * For setting prescribed parameters for affine torus
	 * construction.
	 * @param p PackData
	 * @param A double
	 * @param B double
	 */
	public static boolean affineSet(PackData p,TriAspect[] asps,
			double A,double B) {
		if (p.getSidePairs().size()>5) { // want just 2 side-pairings
			CombDCEL.torus4Sides(p.packDCEL);
			p.packDCEL.fixDCEL_raw(p);
		}
		if (p.getSidePairs().size()!=5) {
			throw new CombException("failed to layout 2-side paired edges");
		}
		
		// first set all 'label's to 1.0
		for (int j=1;j<=p.faceCount;j++) {
			for (int k=0;k<3;k++)
				asps[j].labels[k]=1.0;
		}
		
		/* Idea: prescribe (via A and B) the 'labels' for vertices 
		 * along the outside of the red chain. Use these and 
		 * existing interior labels in 'aspects'.
		 * When there are just two side-pairings (preferable),
		 * want A to be scale factor from #1 to #3, and B to be 
		 * scale factor from #2 to #4. TODO: ??? verify that this
		 * lines up with the generic (3 side-pairing) situation.
		 */
		D_SideData side=p.getSidePairs().get(3);
		RedHEdge rtrace=side.startEdge;
		do {
			int f=rtrace.myEdge.face.faceIndx;
			int j=asps[f].vertIndex(rtrace.myEdge.origin.vertIndx);
			asps[f].labels[j] *=A;
			rtrace=rtrace.nextRed;
		} while(rtrace!=side.endEdge.nextRed);
		
		side=p.getSidePairs().get(4);
		rtrace=side.startEdge;
		do {
			int f=rtrace.myEdge.face.faceIndx;
			int j=asps[f].vertIndex(rtrace.myEdge.origin.vertIndx);
			asps[f].labels[j] *=B;
			rtrace=rtrace.nextRed;
		} while(rtrace!=side.endEdge.nextRed);
		return true;
	}
	
	/**
	 * Compute angle sum at 'v' face-by-face using 'labels'
	 * stored in 'triData' with current label at v multiplied 
	 * by factor t>0. Also, return the derivative w.r.t. t.
	 * TODO: also use packData inv distances.
	 * @param p PackData
	 * @param asp TriData[], case to 'TriAspect'
	 * @param v int, vertex
	 * @param t double, factor > 0 for multiplying labels at 'v'
	 * @return double
	 */
	public static double[] labelAngSum(PackData p,TriData[] trid,
			int v, double t) {
		double []ans=new double[2];
		int[] faceFlower=p.getFaceFlower(v);
		for (int j = 0; j < faceFlower.length; j++) {
			int f = faceFlower[j];
			double[] sd= ((TriAspect)trid[f]).angleV(v,t);
			ans[0] += sd[0];
			ans[1] += sd[1];
		}
		return ans;
	}
	
	/**
	 * Change label for v in all faces containing 'v' 
	 * by multiplicative 'factor'.
	 * @param p PackData 
	 * @param v int
	 * @param factor double
	 * @param trid TriData[], cast to 'TriAspect'
	 * @return int 1
	 */
	public static int adjustLabel(PackData p,TriData[] trid,
			int v,double factor) {
		int[] faceFlower=p.getFaceFlower(v);
		for (int j=0;j<faceFlower.length;j++) {
			int f=faceFlower[j];
			int k=trid[f].vertIndex(v);
			trid[f].labels[k] *=factor;
		}
		return 1;
	}
	
	/**
	 * Put a torus in normalized position and compute 
	 * various data for 'TorusData' structure. Calling
	 * routine must arranged 2 side-pairings and should 
	 * have repacked and laid out the affine packing. 
	 * @param writ boolean; false, don't write output messages.
	 * @return TorusData object or null on error
	 */
	public TorusData getTorusData(boolean writ) {
		TorusData torusData=new TorusData();
		Complex[] Z=new Complex[4];

		D_PairLink plink=packData.packDCEL.pairLink;
		if (plink==null || plink.size()!=5) 
			throw new ParserException("torus appears to have no "+
					"'pairLink' or there are not two side-pairings.");
		for (int j=1;j<=4;j++) {
			D_SideData sdata=plink.get(j);
			Z[j-1]=sdata.startEdge.getCenter();
		}
		
		// make sure the torus is normalized
		Mobius mob=Mobius.mob_NormQuad(Z);
		for (int j=0;j<4;j++)
			Z[j]=mob.apply(Z[j]);
		torusData.cornerPts=Z;
		
		torusData.x_ratio=torusData.cornerPts[0].minus(torusData.cornerPts[1]).
				times(torusData.cornerPts[2].minus(torusData.cornerPts[3])).
				divide(torusData.cornerPts[0].minus(torusData.cornerPts[3]).
				times(torusData.cornerPts[2].minus(torusData.cornerPts[1])));
				// cross-ratio (z1-z2)*(z3-z4)/((z1-z4)*(z3-z2))
		
		Mobius.mobiusDirect(packData,mob);
		
		torusData.mean=new Complex(0.0);
		for (int i=0;i<4;i++) {
			torusData.mean.add(torusData.cornerPts[i].times(.25));
		}
		
		// compute Teichmuller parameter 'T' and the modulus 'tau'
		//   and the affine parameter 'c' of Sass.
		torusData.a=Math.log(torusData.cornerPts[1].abs());
		torusData.b=sideArgChanges.get(0); // arg change on [z1,z2]
		torusData.M=Math.log(torusData.cornerPts[3].abs());
		torusData.N=(-1.0)*sideArgChanges.get(3); // arg change on [z4,z1]
		torusData.affCoeff=new Complex(torusData.a,torusData.b);
		double x=(torusData.a*torusData.M+torusData.b*torusData.N)/torusData.affCoeff.absSq();
		double y=(torusData.a*torusData.N-torusData.b*torusData.M)/torusData.affCoeff.absSq();
		torusData.teich=new Complex(x,y);
		torusData.tau=TorusModulus.Teich2Tau(torusData.teich);
		torusData.alpha=torusData.cornerPts[3].minus(torusData.cornerPts[2]).
		divide(torusData.cornerPts[0].minus(torusData.cornerPts[1]));
		torusData.beta=torusData.cornerPts[3].minus(torusData.cornerPts[0]).
		divide(torusData.cornerPts[2].minus(torusData.cornerPts[1]));
		

	}
		
	
	/** 
	 * Override method for cataloging command structures
	 */
	public void initCmdStruct() {
		super.initCmdStruct();
		cmdStruct.add(new CmdStruct("affine","{a b}",null,
				"set face ratio data for torus, side pairing factors a, b"));
		cmdStruct.add(new CmdStruct("corners","v1 v2 v3 v4",null,
				"vertices at corners, v1,v2=bottom, v2,v3=right"));
		cmdStruct.add(new CmdStruct("affpack","{v..}",null,
				"run iterative affine packing method"));
		cmdStruct.add(new CmdStruct("afflayout",null,null,
				"layout a fundamental domain using computed ratios"));
		cmdStruct.add(new CmdStruct("set_labels","-[rzst] f..",null,
				"face label data using: -r = radii, -z = centers, -s= random"));
		cmdStruct.add(new CmdStruct("draw","-[cfB]flags",null,
				"faces, f, circles, c, both B, plus normal flags"));
		cmdStruct.add(new CmdStruct("set_screen",null,null,
				"set screen to get the full fundamental domain"));
		cmdStruct.add(new CmdStruct("log_radii",null,null,
				"write /tmp file with labels"));
		cmdStruct.add(new CmdStruct("status",null,null,
				"No flags? error norms: curvatures, strong consistency\n"+
				"With flags: return single vert info"));
		cmdStruct.add(new CmdStruct("set_eff",null,null,
				"Using centers, set packing rad to the 'effective' radii"));
		cmdStruct.add(new CmdStruct("ccode","-[cfe] -m m j..",null,
				"Color code faces, vertices, or edges, mode m"));
		cmdStruct.add(new CmdStruct("Lface",null,null,
				"draw faces using TriAspect centers, spanning tree"));
		cmdStruct.add(new CmdStruct("Ltree",null,null,
				"draw dual spanning tree using TriAspect centers"));
		cmdStruct.add(new CmdStruct("LinCircs",null,null,
				"Draw the incircles of the faces, using aspects 'center's"));
		cmdStruct.add(new CmdStruct("equiSides",null,null,
				"set 'sides' to 1; faces are equilateral"));
		cmdStruct.add(new CmdStruct("sideRif","v..",null,
				"Riffle by adjusting 'sides'"));
		cmdStruct.add(new CmdStruct("update","-[sl] f..",null,
				"Update: -s centers to sides; -l sides to labels"));
		cmdStruct.add(new CmdStruct("sI",null,null,
				"Side information: corners, angles, etc."));

	}
	
}

/** 
 * Specialized class for accumulating torus data.
 * Torus must have been given layout in 2-sidepair form.
 */
class TorusData {
	int cornerVert; // corner vertex
	Complex []cornerPts; // four locations for single corner vertex
	Complex x_ratio; // cross-ratio (z1-z2)*(z3-z4)/((z1-z4)*(z3-z2))
	Complex mean;  // average of four corners --- for display use
	Complex teich;  // Teichmuller parameter
	Complex tau;    // conformal modulus
	Complex affCoeff; // affine coefficient
	double a;   // log(|z2|)
	double b;   // argument change on [z1 z2]
	double M;   // log(|z4|)
	double N;   // argument change on [z1,z4]
	Complex alpha; // side pair maps in normalized situation
	Complex beta; // z goes to alpha*z and beta*z.
}