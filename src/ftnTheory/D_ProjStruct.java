package ftnTheory;

import java.util.Iterator;
import java.util.Vector;

import allMains.CirclePack;
import complex.Complex;
import dcel.CombDCEL;
import dcel.D_PairLink;
import dcel.D_SideData;
import dcel.HalfEdge;
import dcel.PackDCEL;
import dcel.RedHEdge;
import exceptions.CombException;
import exceptions.DataException;
import exceptions.ParserException;
import listManip.HalfLink;
import listManip.NodeLink;
import math.Mobius;
import packing.PackData;
import packing.PackExtender;
import rePack.d_EuclPacker;
import util.CmdStruct;
import util.StringUtil;
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
			aspects=setupAspects(packData);
			packData.packExtensions.add(this);
		}
	}
	
	/**
	 * This is were the user's commands are "parsed"
	 */
	public int cmdParser(String cmd, Vector<Vector<String>> flagSegs) {
		Vector<String> items = null;
		int count=0;
		
		// ======== torAB ===========
		if (cmd.startsWith("torAB")) {
			// this routine is tailored for tori: specify side-pair
			// scaling in an attempt to build general affine tori
			if (aspects==null || aspects.length!=(packData.faceCount+1))
				aspects=setupAspects(packData);

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
				Oops("torAB has failed");
			msg("Affine data set: A = " + A + " B = " + B);
			return 1;
		}
		
		// ======== affpack ===========
		else if (cmd.startsWith("affpack")) {
			int passes=-1;
			try {
				items=flagSegs.get(0);
				passes=Integer.parseInt(items.get(0));
			} catch(Exception ex) {
				passes=-1;
			}
			d_EuclPacker e_packer=new d_EuclPacker(packData,-1);
			
			// set 'triData' to 'aspects' and repack using 'labels'
			e_packer.pdcel.triData=aspects;
			d_EuclPacker.affinePack(packData,passes);
			
			// store results as radii
			NodeLink vlist=new NodeLink();
		   	for (int i=0;i<e_packer.aimnum;i++) {
	    		vlist.add(e_packer.index[i]);
		   	}
			d_EuclPacker.reapLabels(packData,vlist);
			return packData.packDCEL.layoutPacking();
		}
		
		// ======== TorusData =========
		else if (cmd.startsWith("tD")) {
			TorusData torusData=getTorusData();
			if (torusData==null) 
				throw new CombException("failed to compute 'TorusData'; "+
						"is it in 2-sidepair form?");

			// print out torusData
			try {
				if (torusData.flat) { 
					CirclePack.cpb.msg("Flat Torus: corner vert = "+
							torusData.cornerVert+", locations are:\n");
					}
				else {
					CirclePack.cpb.msg("Affine Torus: corner vert = "+
							torusData.cornerVert+", locations are:\n");
				}
				for (int j=0;j<4;j++) 
					CirclePack.cpb.msg(torusData.cornerPts[j].toString());
				CirclePack.cpb.msg("Teich = "+torusData.teich.toString());
				CirclePack.cpb.msg("tau = "+torusData.tau.toString());
				CirclePack.cpb.msg("cross_ratio = "+torusData.x_ratio.toString());

				if (!torusData.flat)
					CirclePack.cpb.msg("Affine parameter 'c' = "+
						torusData.affCoeff.toString());
			} catch(Exception ex){}
			return 1;
		}
		
		// ======== status ==========
		else if (cmd.startsWith("stat")) {
			NodeLink vlist=null;
			double Angsum_err=0.0;
			double TLog_err=0.0;
			double SLog_err=0.0;
			count=0;
			
			// if one or more flags, report for just first object only
			if (flagSegs!=null && flagSegs.size()>0) {
				Iterator<Vector<String>> flgs=flagSegs.iterator();
				while (flgs.hasNext()) {
					items=flgs.next();
					String str=items.remove(0);
					if (StringUtil.isFlag(str)) {
						char c=str.charAt(1);
						switch(c) {
						case 's': // strong consistency: (t.t') for edges
						{
							HalfLink hlist=new HalfLink(packData,items);
							if (hlist!=null && hlist.size()>0) {
								HalfEdge edge=hlist.get(0);
								msg("Edge <"+edge+">, t*t' = "+
									String.format("%.8e",
									edgeRatioError(packData,aspects,edge)));
								return 1;
							}
							break;
						}
						case 'c': // curvature error (angle sum-aim)
						{
							vlist=new NodeLink(packData,items);
							if (vlist!=null && vlist.size()>0) {
								int v=(int)vlist.get(0);
								msg("Angle sum error of "+v+" is "+
										String.format("%.8e",angsumError(v)));
								return 1;
							}
							break;
						}
						} // end of switch
					}
				}
				return 0; // didn't find valid flag??
			}
			
			// if no flags?
			
			// find sum[angsum-aim]^2 (for verts with aim>0)
			for (int v=1;v<=packData.nodeCount;v++) {
				double diff=angsumError(v);
				Angsum_err += diff*diff;
			}
				
			// report
			msg("Status: anglesum error norm = "+
					String.format("%.8e",Math.sqrt(Angsum_err)));
			msg("Edge ratio (Log(t.t')) error norm = "+
					String.format("%.8e",Math.sqrt(TLog_err)));
			msg("Weak consistency (Log(ll../rr..)) error norm = "+
					String.format("%.8e",Math.sqrt(SLog_err)));
			return count;
		}
		
		// ======== TorusData =========
		if (cmd.startsWith("tD")) {
			TorusData torusData=getTorusData(packData,false);
			if (torusData==null) 
				throw new CombException("failed to compute 'TorusData'; "+
						"is it in 2-sidepair form?");

			// print out torusData
			try {
				if (torusData.flat) { 
					CirclePack.cpb.msg("Flat Torus: corner vert = "+
							torusData.cornerVert+", locations are:\n");
					}
				else {
					CirclePack.cpb.msg("Affine Torus: corner vert = "+
							torusData.cornerVert+", locations are:\n");
				}
				for (int j=0;j<4;j++) 
					CirclePack.cpb.msg(torusData.cornerPts[j].toString());
				CirclePack.cpb.msg("Teich = "+torusData.teich.toString());
				CirclePack.cpb.msg("tau = "+torusData.tau.toString());
				CirclePack.cpb.msg("cross_ratio = "+torusData.x_ratio.toString());

				if (!torusData.flat)
					CirclePack.cpb.msg("Affine parameter 'c' = "+
						torusData.affCoeff.toString());
			} catch(Exception ex){}
			
			return 1;
		}
		
		return super.cmdParser(cmd, flagSegs);
	}
	
	/**
	 * Compute skew and its derivative at 'v' face-by-face using 'labels'
	 * with labels at v multiplied by factor t>0. Also, return
	 * the derivative w.r.t. t.
	 * @param p @see PackData
	 * @param v int, vertex
	 * @param t double, factor > 0 for multiplying labels at 'v'
	 * @param asps[] @see TriAspect
	 * @return double[2]: [0]=skew, [1]=deriv
	 */
	public static double []skewTri(PackData p, int v,double t,
			TriAspect[] asps) {
		double []ans = new double[2];
		int[] faceFlower=p.getFaceFlower(v);
		for (int j = 0; j < p.countFaces(v); j++) {
			int f = faceFlower[j];
			double []sd= asps[f].skew(v,t);
			ans[0] += sd[0];
			ans[1] += sd[1];
		}
		return ans;
	}
	
	/** 
	 * Return weak consistency error for interior 'v'.
	 * This is product of leftlength/rightlength for all 
	 * faces in star(v).
	 * @param p @see PackData
	 * @param aspects []TriAspect
	 * @param v int, vertex
	 * @return double, 1.0 if v not interior.
	 */  
	public static double weakConError(PackData p,
			TriAspect []aspects,int v) {
		if (p.isBdry(v))
			return 1.0;
		double rtio=1.0;
		int[] faceFlower=p.getFaceFlower(v);
		for (int j=0;j<faceFlower.length;j++) {
			int ff=faceFlower[j];
			int k=aspects[ff].vertIndex(v);
			rtio *= aspects[ff].sides[(k+2)%3]; // left sidelength
			rtio /= aspects[ff].sides[k]; // right sidelength
		}
		return rtio;
	}	

	/**
	 * Compute weak, strong consistency, and angle sum errors, both in l^2
	 * and sup norm.
	 * @return double[6]: 
	 *   [0]=weak l^2;[1]=weak max (among vertices);
	 *   [2]=strong l^2;[3]=strong max (among edge); 
	 *   [4]=angle sum l^2;[5]=angle sum max
	 */
	public static double []getErrors(PackData p,TriAspect []aspects) {
		double weak_err=0.0;
		double weak_max=0.0;
		double TLog_err=0.0;
		double TLog_max=0.0;
		double ang_err=0.0;
		double ang_max=0.0;
		double []ans=new double[6];

		for (int v=1;v<=p.nodeCount;v++) {
			// Strong: find sum[|Log(t.t')|]^2 for interior edges
			HalfLink vspokes=p.packDCEL.vertices[v].getEdgeFlower();
			Iterator<HalfEdge> sis=vspokes.iterator();
			while (sis.hasNext()) {
				HalfEdge he=sis.next();
				int w=he.twin.origin.vertIndx;
				if (w>v && !he.isBdry()) {
					double prd=Math.abs(Math.log(Math.abs(
							edgeRatioError(p,aspects,he))));
					TLog_max=(prd>TLog_max) ? prd:TLog_max;
					TLog_err += prd*prd;
				}
			}

			// weak;
			double werr=Math.abs(Math.log(
					D_ProjStruct.weakConError(p,aspects,v)));
			weak_err += werr*werr;
			weak_max=(werr>weak_max) ? werr:weak_max;
			
			// angle sum
			double ang=Math.abs(angSumTri(p,v,1.0,aspects)[0]-p.getAim(v));
			ang_err += ang*ang;
			ang_max=(ang>ang_max) ? ang:ang_max;
		}
		ans[0]=Math.sqrt(weak_err);
		ans[1]=weak_max;
		ans[2]=Math.sqrt(TLog_err);
		ans[3]=TLog_max;
		ans[4]=Math.sqrt(ang_err);
		ans[5]=ang_max;
		
		return ans;
	}

	/**
	 * Return angle sum error at 'v' based on TriAspect 'labels'.
	 * @param v int, vertex index in packing
	 * @return double, abs(error); 0 if 'aim' <=0
	 */
	public double angsumError(int v) {
		if (packData.getAim(v)<=0)
			return 0;
		return Math.abs(angSumTri(packData,v,1.0,
				aspects)[0]-packData.getAim(v));
	}
	
	/**
	 * Compute angle sum at 'v' face-by-face using 'labels'
	 * with labels at v multiplied by factor t>0. Also, return
	 * the derivative w.r.t. t.
	 * @param p @see PackData
	 * @param v int, vertex
	 * @param t double, factor > 0 for multiplying labels at 'v'
	 * @param asps[] @see TriAspect
	 * @return double
	 */
	public static double []angSumTri(PackData p, int v, double t,
			TriAspect[] asps) {
		double []ans=new double[2];
		int[] faceFlower=p.getFaceFlower(v);
		for (int j = 0; j < p.countFaces(v); j++) {
			int f = faceFlower[j];
			double []sd= asps[f].angleV(v,t);
			ans[0] += sd[0];
			ans[1] += sd[1];
		}
		return ans;
	}

	/**
	 * Return 'edge' consistency error computed from 'labels'. 
	 * For interior edge, this is t*t', where t is the ratio 
	 * of 'labels' for 'edge' in lefthand face, t' is that of
	 * righthand face. 
	 * @param edge HalfEdge
	 * @param p PackData,
	 * @param asps TriAspect[]
	 * @return 1.0 if not interior edge.
	 */
	public static double edgeRatioError(PackData p,
			TriAspect []asps,HalfEdge edge) {
		if (edge.isBdry()) // bdry edge 
			return 1.0;
		int v=edge.origin.vertIndx;
		int w=edge.twin.origin.vertIndx;
		int lface=edge.face.faceIndx;
		int rface=edge.twin.face.faceIndx;
		int lj=asps[lface].vertIndex(v);
		int rj=asps[rface].vertIndex(w);
		double prd=asps[lface].labels[(lj+1)%3];
		prd /=asps[lface].labels[lj];
		prd *=asps[rface].labels[(rj+1)%3];
		prd /=asps[rface].labels[rj];
		return prd;
	}
	
	/**
	 * create the 'aspects' array, putting current
	 * radii/centers from packData into 'TriData.labels'
	 * and 'TriAspect.center' slots.
	 * @param p PackData
	 */
	public static TriAspect[] setupAspects(PackData p) {
		TriAspect[] asp=new TriAspect[p.faceCount+1];
		for (int f=1;f<=p.faceCount;f++) {
			asp[f]=new TriAspect(p.hes);
			TriAspect tas=asp[f];
			tas.baseEdge=p.packDCEL.faces[f].edge;
			tas.face=tas.baseEdge.face.faceIndx;
			tas.vert=p.packDCEL.faces[f].getVerts();
			HalfEdge he=tas.baseEdge;
			int tick=0;
			do {
				tas.labels[tick]=p.packDCEL.getVertRadius(he);
				tas.center[tick++]=p.packDCEL.getVertCenter(he);
				he=he.next;
			} while (he!=tas.baseEdge);

			// set 'sides' from 'center's
			tas.centers2Sides();
		}
		return asp;
	}
	
	/**
	 * For setting prescribed parameters for affine torus
	 * construction.
	 * @param p PackData
	 * @param A double
	 * @param B double
	 * @return boolean true
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
		
		if (asps==null) {
			asps=setupAspects(p);
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
	 * Change label for v by multiplicative 'factor' in 
	 * all faces containing 'v' 
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
	 * Change side lengths at v by 'factor' in every face containing v.
	 * @param p PackData 
	 * @param v vertex
	 * @param factor double
	 * @param asp []TriAspect
	 * @return 1
	 */
	public static int adjustSides(PackData p,int v,double factor,
			TriAspect []asp) {
		int[] faceFlower=p.getFaceFlower(v);
		for (int j=0;j<p.countFaces(v);j++) {
			int f=faceFlower[j];
			int k=asp[f].vertIndex(v);
			asp[f].sides[k] *=factor;
			asp[f].sides[(k+2)%3] *=factor;
		}
		return 1;
	}
	
	/**
	 * Compute angle sum at v face-by-face using the TriAspect
	 * 'sides' data, with sides ending at v multiplied by 'factor'.
	 * @param p PackData
	 * @param v int
	 * @param factor double
	 * @param asps []TriAspect
	 * @return angsum double
	 */
	public static double angSumSide(PackData p,int v,double factor,
			TriAspect []asps) {
		double angsum=0.0;
		int[] faceFlower=p.getFaceFlower(v);
		for (int j=0;j<faceFlower.length;j++) {
			int f=faceFlower[j];
			int k=asps[f].vertIndex(v);
			double s0=factor*asps[f].sides[k];
			double s1=asps[f].sides[(k+1)%3];
			double s2=factor*asps[f].sides[(k+2)%3];
			angsum += Math.acos((s0*s0+s2*s2-s1*s1)/(2.0*s0*s2));
		}
		return angsum;
	}
	
	
	/**
	 * Put a torus in normalized position and compute 
	 * various data for 'TorusData' structure. Calling
	 * routine should have arranged 2 side-pairings and 
	 * repacked and laid out the affine packing.
	 * @param p PackData 
	 * @param writ boolean; false, don't write output messages.
	 * @return TorusData object or null on error
	 */
	public static TorusData getTorusData(PackData p,boolean writ) {
		TorusData torusData=new TorusData();
		Complex[] Z=new Complex[4];

		D_PairLink plink=p.packDCEL.pairLink;
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
		
		Mobius.mobiusDirect(p,mob);
		
		torusData.mean=new Complex(0.0);
		for (int i=0;i<4;i++) {
			torusData.mean.add(torusData.cornerPts[i].times(.25));
		}

		// compute Teichmuller parameter 'T' and the modulus 'tau'
		//   and the affine parameter 'c' of Sass.
		torusData.a=Math.log(torusData.cornerPts[1].abs());
		torusData.M=Math.log(torusData.cornerPts[3].abs());
		torusData.affCoeff=new Complex(torusData.a,torusData.b);
		double x=(torusData.a*torusData.M+torusData.b*torusData.N)/torusData.affCoeff.absSq();
		double y=(torusData.a*torusData.N-torusData.b*torusData.M)/torusData.affCoeff.absSq();
		torusData.teich=new Complex(x,y);
		torusData.tau=TorusModulus.Teich2Tau(torusData.teich);
	
		return torusData;
	}
	
	public double ratioErr() {
		double err=0;
		for (int f=1;f<=packData.faceCount;f++) {
			dcel.Face face=packData.packDCEL.faces[f];
			HalfEdge he=face.edge;
			do {
				int lf=he.face.faceIndx;
				int rf=he.twin.face.faceIndx;
				double er=aspects[f].labels[lf] - aspects[f].labels[rf];
				err += er*er;
				he=he.next;
			} while (he!=face.edge);
		}
		return err;
	}
			
	/**
  	 * Return upper/lower bounds on factor by which sides 
  	 * in 'asps' can be adjusted at v while preserving the 
  	 * triangle inequality for all faces containing v.
  	 * @param p PackData
  	 * @param v vertex
  	 * @param asps TriAspect[]
  	 * @return int[2]: [0]=lower; [1]=upper
  	 */
  	public static double []sideBounds(PackData p,int v,TriAspect []asps) {
  		double lower=0.0;
  		double upper=100000000;
  		int[] faceFlower=p.getFaceFlower(v);
		for (int j=0;j<faceFlower.length;j++) {
			int f=faceFlower[j];
			int k=asps[f].vertIndex(v);
			double rSide=asps[f].sides[k];
			double lSide=asps[f].sides[(k+2)%3];
			double oppSide=asps[f].sides[(k+1)%3];
			if ((rSide+lSide)<oppSide || oppSide<Math.abs(rSide-lSide))
				throw new DataException(
						"Triangle inequality fails for face "+f);
			double a=oppSide/(lSide+rSide);
			lower=(a>lower) ? a : lower;
			double b=oppSide/Math.abs(rSide-lSide);
			upper=(b<upper) ? b : upper;
		}
  		double []ans=new double[2];
  		ans[0]=lower;
  		ans[1]=upper;
  		return ans;
  	}
 	  
	/**
	 * Change label for v in all faces containing 'v' 
	 * by multiplicative 'factor'.
	 * 
	 * TODO: Is this OBE? do I want to change radius in packing?
	 * @param p PackData 
	 * @param v int, parent vertex
	 * @param factor double
	 * @param asp TriAspect[]
	 * @return int -1 on error
	 */
	public static int adjustRadii(PackData p,int v,double factor,
			TriAspect []asp) {
//		p.setRadius(v,factor*p.getRadius(v));
		int[] faceFlower=p.getFaceFlower(v);
		for (int j=0;j<faceFlower.length;j++) {
			int f=faceFlower[j];
			int k=asp[f].vertIndex(v);
			asp[f].labels[k] *=factor;
		}
		return 1;
	}
	
	/**
	 * Normalize an existing torus packing, update rad/cents,
	 * update side pair mobius. 
	 * @param p PackData
	 * @return Mobius
	 */
	public static Mobius normalizeTorus(PackData p) {
		PackDCEL pdcel=p.packDCEL;
		Mobius mob=null;
		if (pdcel.pairLink==null || pdcel.pairLink.size()!=5 ||
				pdcel.idealFaceCount!=0 || p.genus!=1) {
			CirclePack.cpb.msg("packing is not a 2-sidepair torus");
			return null;
		}
		try {
			Complex[] Z=new Complex[4];
			for (int j=1;j<=4;j++) {
				D_SideData sd=pdcel.pairLink.get(j);
				Z[j-1]=sd.startEdge.getCenter();
			}
			
			mob=Mobius.mob_NormQuad(Z);
			Mobius.mobiusDirect(p,mob);
			pdcel.updatePairMob();
		} catch (Exception ex) {
			CirclePack.cpb.errMsg("Error normalizing torus: msg "+ex.getMessage());
			return null;
		}
		return mob;
	}
	
	/**
	 * Put a torus in normalized position and fill 'TorusData'
	 * object. Calling routine must arranged 2 side-pairings 
	 * and should have repacked and laid out the affine packing. 
	 * @return TorusData object or null on error
	 */
	public TorusData getTorusData() {
		TorusData torusData=new TorusData();
		
		// normalize and store new rad/cent, update mobius
		Mobius mob=normalizeTorus(packData);
		if (mob==null)
			throw new ParserException("error processing this torus");

		torusData.cornerPts=new Complex[4];
		for (int j=1;j<=4;j++) {
			D_SideData sdata=pdc.pairLink.get(j);
			torusData.cornerPts[j-1]=sdata.startEdge.getCenter();
		}
		torusData.cornerVert=pdc.pairLink.get(1).startEdge.
				myEdge.origin.vertIndx;
		
		// cross-ratio (z1-z2)*(z3-z4)/((z1-z4)*(z3-z2))
		torusData.x_ratio=torusData.cornerPts[0].minus(torusData.cornerPts[1]).
				times(torusData.cornerPts[2].minus(torusData.cornerPts[3])).
				divide(torusData.cornerPts[0].minus(torusData.cornerPts[3]).
				times(torusData.cornerPts[2].minus(torusData.cornerPts[1])));
		
		// for viewing purposes
		torusData.mean=new Complex(0.0);
		for (int i=0;i<4;i++) {
			torusData.mean.add(torusData.cornerPts[i].times(.25));
		}
		
		// is this a flat torus? (vs. affine)
		if (torusData.cornerPts[0].abs()<.5) {
			torusData.flat=true;
			torusData.affCoeff=new Complex(0.0);
			torusData.teich=torusData.cornerPts[3];
			torusData.tau=TorusModulus.Teich2Tau(torusData.teich);
			torusData.a=torusData.b=torusData.M=torusData.N=0.0;
			return torusData;
		}
		
		// else, affine: compute Teichmuller parameter 'T'
		//   Sass affCoeff parameter c. Corners of affine 
		//   fundamental domain are <1, exp(c), exp(cT+c), exp(T)>, 
		//   which is image of parallelogram <0,1,T+1,T> under 
		//   z -> exp(cz). Multi-valued nature of logs requires
		//   finding change in argument along edges.
		torusData.flat=false;
		torusData.a=Math.log(torusData.cornerPts[1].abs());
		torusData.b=0.0; // arg change along [z1,z2] edge
		D_SideData sd=pdc.pairLink.get(1);
		RedHEdge rtrace=sd.startEdge;
		do {
			torusData.b += rtrace.nextRed.getCenter().
					divide(rtrace.getCenter()).arg();
			rtrace=rtrace.nextRed;
		} while (rtrace!=sd.endEdge.nextRed);
		
		torusData.M=Math.log(torusData.cornerPts[3].abs());
		torusData.N=0.0;  // arg change along [z1,z4] edge 
		sd=pdc.pairLink.get(4);
		rtrace=sd.startEdge;
		do {
			torusData.N -= rtrace.nextRed.getCenter().
					divide(rtrace.getCenter()).arg();
			rtrace=rtrace.nextRed;
		} while (rtrace!=sd.endEdge.nextRed);

		torusData.affCoeff=new Complex(torusData.a,torusData.b);
		torusData.teich=new Complex(torusData.M,torusData.N).
				divide(torusData.affCoeff);
		torusData.tau=TorusModulus.Teich2Tau(torusData.teich);

		return torusData;
	}
	
	/** 
	 * Override method for cataloging command structures
	 */
	public void initCmdStruct() {
		super.initCmdStruct();
		cmdStruct.add(new CmdStruct("torAB","{A B}",null,
				"initialize data for affine torus, side scaling factors A, B"));
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
 * Specialized class for accumulating torus data. Torus
 * may be flat or affine; these use different normalizations.
 * Torus must have been given layout in 2-sidepair form; 
 * side-pair Mobius maps are stored as usual.
 */
class TorusData {
	boolean flat;   // yes for regular torus, no for affine 
	int cornerVert; // corner vertex
	Complex []cornerPts; // four locations for single corner vertex
	Complex x_ratio; // cross-ratio (z1-z2)*(z3-z4)/((z1-z4)*(z3-z2))
	Complex mean;  // average of four corners --- for display use
	Complex teich;  // Teichmuller parameter
	Complex tau;    // conformal modulus
	Complex affCoeff; // affine coefficient, 0.0 for flat
	
	// for affine case only
	double a;   // log(|z2|)
	double b;   // argument change on [z1 z2]
	double M;   // log(|z4|)
	double N;   // argument change on [z1,z4]
}