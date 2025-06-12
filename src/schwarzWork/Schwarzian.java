package schwarzWork;

import java.awt.Color;
import java.io.BufferedWriter;
import java.io.File;
import java.util.Iterator;
import java.util.Vector;

import allMains.CPBase;
import allMains.CirclePack;
import combinatorics.komplex.HalfEdge;
import combinatorics.komplex.RedEdge;
import combinatorics.komplex.Vertex;
import complex.Complex;
import dcel.PackDCEL;
import dcel.SideData;
import exceptions.CombException;
import exceptions.DataException;
import exceptions.MiscException;
import exceptions.MobException;
import geometry.CircleSimple;
import geometry.CommonMath;
import geometry.HyperbolicMath;
import geometry.SphericalMath;
import input.CPFileManager;
import komplex.DualTri;
import listManip.EdgeLink;
import listManip.HalfLink;
import listManip.NodeLink;
import math.CirMatrix;
import math.Mobius;
import packing.PackData;
import util.ColorUtil;
import util.DispFlags;
import util.StringUtil;
import util.TriAspect;

/**
 * Static routines for working with discrete Schwarzian 
 * derivatives and intrinsic schwarzians. These are for
 * tangency packings: Schwarzian Derivative is associated
 * with discrete mappings between packings for the same 
 * complex, which intrinsice schwarzians are associated 
 * with individual packing based on identification of 
 * faces with the "base equilateral"; namely, the tangent 
 * triple of circles of radius sqrt(3), symmetric w.r.t. 
 * the origin, and having its first edge vertical through 
 * z=1. See "ftnTheory.SchwarzMap.java" for related work 
 * in the setting of maps based on the original 
 * notions in Gerald Orick's thesis.
 * 
 * The "intrinsic" schwarzians are real numbers stored 
 * in 'HalfEdge.schwarzian', defaulting to 0.0. The 
 * methods here are for creating, analyzing, manipulating 
 * this data. Some routines do not apply in the 
 * hyperbolic setting since layouts can leave the disc. 
 * The "normalized" presentation of a flower puts 
 * the center as the upper half plane, its 
 * halfedge neighbor c_{n-1} as half plane y <= -2, 
 * and petal c_0 as tangent at 0, radius 1. 
 * 
 * The variable preferred for formulas is u=1-s, 
 * where s is the intrinsic schwarzian; 'u' is called 
 * a "uzian". We restict to u positive, so s in (-infty,1).
 * 
 * @author kens, January 2020
 */
public class Schwarzian {
	
	// positioning face g requires pre-composition:
	//   This may involve rotation by omega or 
	//   omegaL^2, preceeded by by rotation 
	//   about 1 (by z->2-z).
	final static Mobius gFix0=
			new Mobius(new Complex(-1.0),new Complex(2.0),
					new Complex(0.0),new Complex(1.0));
	final static Mobius gFix1=
			new Mobius(CPBase.omega3[1].times(-1.0),CPBase.omega3[1].times(2.0),
					new Complex(0.0),new Complex(1.0));
	final static Mobius gFix2=
			new Mobius(CPBase.omega3[2].times(-1.0),CPBase.omega3[2].times(2.0),
					new Complex(0.0),new Complex(1.0));
	final static double oosq3=1/Math.sqrt(3);
	final static CirMatrix fourthCircle=new CirMatrix(new CircleSimple(new Complex(4.0),Math.sqrt(3.0)));
		
	/**
	 * Compute and set intrinsic schwarzians for 
	 * given interior edges based only on radii. For 
	 * each edge, find the 4 radii involved, find the 
	 * base Mobius transformations and compute the 
	 * schwarzian. 
	 * 
	 * Multi-connected cases are more complicated if 
	 * side-pairing maps are not isometries --- 
	 * e.g. for projective structures such as affine tori.
	 * @param p PackData
	 * @param hlink HalfLink, default to all
	 * @return count, 0 on error
	 */
	public static int comp_schwarz(PackData p,HalfLink hlink) {
		PackDCEL pdcel=p.packDCEL;
		int count=0;
		if (hlink==null || hlink.size()==0) // default to all
			hlink=new HalfLink(p,"a");
		
		// to avoid redundancy, use 'eutil': 
		//    -1 bdry; 0 ignore; 1 do; n = side index along paired edge
		pdcel.zeroEUtil();
		Iterator<HalfEdge> hlst = hlink.iterator();
		while (hlst.hasNext()) {
			HalfEdge he=hlst.next();
			if (!he.isBdry() && he.eutil==0 && he.twin.eutil==0)
				he.eutil=1;
			if (he.isBdry()) { 
				he.eutil=-1;
				he.twin.eutil=-1;
			}
		}
		
		// easy case (e.g. simply connected)
		if (p.isSimplyConnected()) {
			hlst = hlink.iterator();
			HalfEdge edge=null;
			while (hlst.hasNext()) {
				edge=hlst.next();
				if (edge.eutil==-1) // bdry edge
					edge.setSchwarzian(0.0);
				else if (edge.eutil>0) {
					double[] rad = ordinary_radii(p,edge);
					double schn;
					try {
						// for sphere: routine lay out new circles
						if (p.hes>0)  
							schn=Schwarzian.rad_to_schwarzian(rad,p.hes);

						// for hyperbolic, convert radii to eucl
						else 
							schn=Schwarzian.rad_to_schwarzian(rad,0);
					} catch (DataException dex) {
						throw new DataException(dex.getMessage());
					}
					edge.setSchwarzian(schn);
					edge.twin.setSchwarzian(schn);
					edge.eutil=edge.twin.eutil=0;
					count++;
				}
			} // done with while
			return count;
		} // done with simply connected
		
		// Multi-connected? set 'eutil' to index of side-pairing Mobius
		int S=pdcel.pairLink.size()-1;
		Mobius[] sideMobs=new Mobius[S+1];
		for (int j=1;j<=S;j++) {
			sideMobs[j]=new Mobius(); // default to identity
			SideData sd=pdcel.pairLink.get(j);
			if (sd.mateIndex>=0) { // has mate
				if (Mobius.frobeniusNorm(sd.mob)>.0001)
					sideMobs[j]=sd.mob;
				RedEdge rtrace=sd.startEdge;
				do {
					if (rtrace.myEdge.eutil>0)
						rtrace.myEdge.eutil=j;
					rtrace=rtrace.nextRed;
				} while (rtrace!=sd.endEdge.nextRed);
			}
		}

		hlst = hlink.iterator();
		while (hlst.hasNext()) {
			HalfEdge edge=hlst.next();
			
			// normal interior edges
			if (edge.eutil>0 && edge.myRedEdge==null) {
				double[] rad = ordinary_radii(p,edge);
				double schn;
				try {
					// for sphere: routine lay out new circles
					if (p.hes>0)  
						schn=Schwarzian.rad_to_schwarzian(rad,p.hes);

					// for hyperbolic, convert radii to eucl
					else 
						schn=Schwarzian.rad_to_schwarzian(rad,0);
				} catch (DataException dex) {
					throw new DataException(dex.getMessage());
				}
				edge.setSchwarzian(schn);
				edge.twin.setSchwarzian(schn);
				edge.eutil=edge.twin.eutil=0;
				count++;
			}

			// else a twinned red edge
			else if (edge.eutil>0) {
				Mobius mob=sideMobs[edge.eutil];
				CircleSimple cs=pdcel.getVertData(edge.twin.prev);
				CircleSimple csout=new CircleSimple();
				Mobius.mobius_of_circle(mob,p.hes,cs,csout,true);
				double[] rad = ordinary_radii(p,edge);
				double schn;
				rad[3] = csout.rad;
				try {
					// for sphere: routine lay out new circles
					if (p.hes>0)  
						schn=Schwarzian.rad_to_schwarzian(rad,p.hes);

					// for hyperbolic, convert radii to eucl
					else 
						schn=Schwarzian.rad_to_schwarzian(rad,0);
				} catch (DataException dex) {
					throw new DataException(dex.getMessage());
				}
				edge.setSchwarzian(schn);
				edge.twin.setSchwarzian(schn);
				edge.eutil=edge.twin.eutil=0;
				count++;
			}
		} // end of while
		return count;
	}
	
	/**
	 * Get 4 radii for edge to use in calculating schwarzian.
	 * Ordinary because there are no complications due to 
	 * multi-connectedness.
	 * @param p PackData
	 * @param edge HalfEdge
	 * @return double[]
	 */
	public static double[] ordinary_radii(PackData p,HalfEdge edge) {
		double[] rad=new double[4];
		PackDCEL pdcel=p.packDCEL;
		if (p.hes>0) { // sphere: we lay out
			rad[0] = pdcel.getVertRadius(edge);
			rad[1] = pdcel.getVertRadius(edge.next);
			rad[2] = pdcel.getVertRadius(edge.next.next);
			rad[3] = pdcel.getVertRadius(edge.twin.prev);
		}
		// for hyperbolic, convert radii to eucl
		else {
			if (p.hes<0) {
				CircleSimple[] cS=new CircleSimple[4];
				Vertex V=edge.origin;
				cS[0]=new CircleSimple(V.center,V.rad);
				cS[0]=HyperbolicMath.h_to_e_data(cS[0]);
				rad[0]=cS[0].rad;
				V=edge.next.origin;
				cS[1]=new CircleSimple(V.center,V.rad);
				cS[1]=HyperbolicMath.h_to_e_data(cS[1]);
				rad[1]=cS[1].rad;
				V=edge.next.next.next.origin;
				cS[2]=new CircleSimple(V.center,V.rad);
				cS[2]=HyperbolicMath.h_to_e_data(cS[2]);
				rad[2]=cS[2].rad;
				V=edge.twin.prev.origin;
				cS[3]=new CircleSimple(V.center,V.rad);
				cS[3]=HyperbolicMath.h_to_e_data(cS[2]);
				rad[3]=cS[3].rad;
			}
			else {
				rad[0] = pdcel.getVertRadius(edge);
				rad[1] = pdcel.getVertRadius(edge.next);
				rad[2] = pdcel.getVertRadius(edge.next.next);
				rad[3] = pdcel.getVertRadius(edge.twin.prev);
			}
		}
		return rad;
	}
	
	/**
	 * Given first m=n-3 uzians (1-schwarians),
	 * find remaining 3 uzians. Return full list.
	 * @param uzians
	 * @return double[], full list of uzians
	 * 
	 * TODO: compute formulas for larger n-flowers
	 * 
	 */
	public static double[] final_three(double[] uzians) {
		int m=uzians.length;
		int n=m+3;
		// return originals and add the last 3
		double[] fl=new double[n];
		for (int j=0;j<m;j++) {
			fl[j]=uzians[j];
		}
		if (n==3) { // 3-flower
			fl[m]=fl[m+1]=fl[m+2]=oosq3;
			return fl;
		}
		if (n==4) { // 4-flower
			fl[m]=fl[m+2]=2.0/(3.0*fl[0]);
			fl[2]=fl[0];
			return fl;
		}
		if (n==5) { // 5-flower
			for (int k=0;k<3;k++) {
				fl[m+k]=
					(fl[k]-oosq3)/(3*fl[k]*fl[k+1]-1.0);
			}
			return fl;
		}
		if (n==6) { // 6-flower
			for (int k=0;k<3;k++) {
				double num=fl[0+k]*fl[1+k];
				double denom=3.0*fl[0+k]*fl[1+k]*fl[2+k]-fl[0+k]-fl[2+k];
				fl[m+k]=num/denom;
			}
			return fl;
		}			
		if (n==7) { // 7-flower
			for (int k=0;k<3;k++) {
				double num=3.0*(3.0*fl[0+k]*fl[1+k]*fl[2+k]-fl[0+k]-fl[2+k])+oosq3;
				double denom=3.0*(3.0*fl[0+k]*fl[1+k]*fl[2+k]*fl[3+k]-
						fl[0+k]*fl[1+k]-fl[0+k]*fl[3+k]-fl[2+k]*fl[3+k])+1.0;
				fl[m+k]=num/denom;
			}
			return fl;
		}
		if (n==8) { // 8-flower
			for (int k=0;k<3;k++) {
				double num=3.0*(3.0*fl[0+k]*fl[1+k]*fl[2+k]*fl[3+k]-
						fl[0+k]*fl[1+k]-fl[0+k]*fl[3+k]-fl[2+k]*fl[3+k])+2.0;
				double denom=27.0*fl[0+k]*fl[1+k]*fl[2+k]*fl[3+k]*fl[4+k]-
						9.0*(fl[0+k]*fl[1+k]*fl[2+k]+fl[0+k]*fl[1+k]*fl[4+k]-fl[2+k]*fl[3+k]*fl[4+k])-
						3.0*(fl[0+k]+fl[2+k]-fl[4+k]);
				fl[m+k]=num/denom;
			}
			return fl;
		}

		else {
			throw new DataException("No formulas next schwarzian for degree > 8");
		}
	}
	
		
	/**
	 * Given radii (r0,r1,r2) for oriented face {v,w,a},
	 * radius r4 for b in the oriented face {w,v,b}, and
	 * the geometry, find the schwarzian for {v,w} and {w,v}.
	 * TODO: can't yet handle 1 or 2 infinite radii.
	 * @param rad double[4]
	 * @param hes int, geometry
	 * @return double
	 */
	public static double rad_to_schwarzian(double[] rad,int hes) {
		CircleSimple[] sC=new CircleSimple[4];
		for (int i=0;i<4;i++) {
			sC[i]=new CircleSimple();
			sC[i].rad=rad[i];
		}

		// get the four centers in the appropriate geometry
		Complex []Z=new Complex[4];
		
		// find centers for face f <v,w,a>
		double[] ivd= {1.0,1.0,1.0}; // tangency packings only
		int ans=CommonMath.placeOneFace(sC[0],sC[1],sC[2],ivd,hes);
		if (ans<0) {
			throw new DataException(
					"Problem in 'rad_to_schwarzian' placeOneFace");
		}
		Z[0]=new Complex(sC[0].center);
		Z[1]=new Complex(sC[1].center);
		Z[2]=new Complex(sC[2].center);
		
		// find center for b in <w,v,b>
		sC[3]=CommonMath.comp_any_center(sC[1].center,
				sC[0].center,sC[1].rad,sC[0].rad,
				rad[3],ivd[0],ivd[1],ivd[2], hes);
		sC[3].rad=rad[3];
		Z[3]=new Complex(sC[3].center);
		
		// compute the face tangency points, then face mobius, i.e.,
		//    the mobius maps FROM the base equilateral to the face
		DualTri dtri=new DualTri(Z[0],Z[1],Z[2],hes); // <v,w,u>
		if (dtri.TangPts==null)
			throw new DataException("'rad_to_schwarzian' failed to get 'dri'");
		Complex []tanPts=new Complex[3];
		for (int j=0;j<3;j++) 
			tanPts[j]=new Complex(dtri.TangPts[j]);
		Mobius fbase=Mobius.mob_xyzXYZ(
				CPBase.omega3[0],CPBase.omega3[1],CPBase.omega3[2],
				tanPts[0],tanPts[1],tanPts[2],0,hes);
		
		dtri=new DualTri(Z[1],Z[0],Z[3],hes); // <w,v,b>
		if (dtri.TangPts==null)
			throw new DataException("'rad_to_schwarzian' failed with second 'dri'");
		for (int j=0;j<3;j++)
			tanPts[j]=new Complex(dtri.TangPts[j]);
		Mobius gbase=Mobius.mob_xyzXYZ(
				CPBase.omega3[0],CPBase.omega3[1],CPBase.omega3[2],
				tanPts[0],tanPts[1],tanPts[2],0,hes);

		Mobius dMob = Schwarzian.getIntrinsicSch(fbase,gbase,0,0);
		if (Math.abs(dMob.c.y)>.001) 
			throw new DataException("error: Schwarzian is not real");
		return dMob.c.x;
	}
		
	/**
	 * Compute intrinsic schwarzian mobius transformation 
	 * for edge between f and g via 'baseMobius's, ensuring 
	 * that trace=+2, det=1. 
	 * The intrinsic schwarzian is the edge Mobius derivative of
	 * the map from the base equilaterals F G to f and g. So
	 * The fixed point is 1, the outward normal is 1,
	 * so the complex Schwarzian derivative is real s and
	 * the Mobius we return has the form 
	 *    inv(bm_g)*bm_f = [1 + s, -s;s, 1-s]
	 * Calling routine must insure g aligned with f before 
	 * computing the baseMobius maps 'bm_g' and 'bm_f' (maps 
	 * FROM the base equilateral TO f and g). 
	 * @param bm_f Mobius
	 * @param bm_g Mobius
	 * @param indx_f int, index of shared edge in f
	 * @param indx_g int, index of shared edge in g
	 * @return Mobius, identity on error
	 */
	public static Mobius getIntrinsicSch(Mobius bm_f,Mobius bm_g,
			int indx_f,int indx_g) {
		
		// Let F be the base equilateral, edge 0 centered on 1, 
		//   let G be the contiguous equilateral across that edge. 
		//   To get the edge Mobius derivative, we must pre-compose 
		//   bm_f and bm_g, which map F to f and g, to get mu_f, mu_g.
		//   For mu_f, pre-rotate so the first edge of F maps 
		//   to the indx_f edge of f; i.e., rotation by omega3[indx_f]. 
		//   For mu_g we want to map G to g. We pre-compose bm_g 
		//   by translation by -2, rotation by omega3[indx_g], then 
		//   rotation by pi. 
		Mobius pre_f=new Mobius(CPBase.omega3[indx_f],
				new Complex(0.0),new Complex(0.0),new Complex(1.0));
		Mobius mu_f=(Mobius)bm_f.rmultby(pre_f);
	
		Complex wi=new Complex(CPBase.omega3[indx_g]).times(-1.0);
		Mobius pre_g=new Mobius(wi,wi.times(-2.0),
				new Complex(0.0),new Complex(1.0));
		Mobius mu_g=(Mobius)bm_g.rmultby(pre_g); // mob_g.det();
		Mobius edgeMob=(Mobius)mu_g.inverse().rmultby(mu_f);
		edgeMob.normalize();
		
		// check c essentially real? trace essentially 2?
		if (Math.abs(edgeMob.c.y)>.00001)
			throw new MobException("c entry should be real");
		if (edgeMob.a.add(edgeMob.d).abs()-2.0>.0001)
			throw new MobException("trace should be 2.0");
		double s=edgeMob.c.x;
		
		boolean debug=false;
		if (debug) { // debug=true;
			deBugging.DebugHelp.mob4matlab("bm_f",bm_f);
			deBugging.DebugHelp.mob4matlab("bm_g",bm_g);
			deBugging.DebugHelp.mob4matlab("pre_f",pre_f);
			deBugging.DebugHelp.mob4matlab("mob_f",mu_f);
			deBugging.DebugHelp.mob4matlab("pre_g",pre_g);
			deBugging.DebugHelp.mob4matlab("mob_g",mu_g);
			deBugging.DebugHelp.mob4matlab("edgeMob",edgeMob);
			debug=false;
		}
		
		// return clean Mobius
		Mobius outmob=new Mobius();
		outmob.a=new Complex(1.0+s);
		outmob.b=new Complex(-1.0*s);
		outmob.c=new Complex(s);
		outmob.d=new Complex(1.0-s);
		
		return outmob;
	}

	/**
	 * Find the circle across the 'j' edge of the target face f
	 * if the schwarzian is 's' and 'bm_f' is the Mobius FROM the 
	 * base equilateral TO the target face.
	 * @param s double, schwarzian
	 * @param j int, index of shared edge
	 * @param bm_f Mbbius, map from base to f
	 * @param hes int, geometry
	 * @return CircleSimple
	 */
	public static CircleSimple getThirdCircle(double s,
			int j,Mobius bm_f,int hes) {
		
		// dMob_inv(C_b) would give the new fourth circle 
		// across vertical edge of the base equilateral
		Mobius dMob_inv=new Mobius(new Complex(1-s),new Complex(s),
				new Complex(-s),new Complex(1+s));
		// pre_f rotates it across the edge that bm_f takes to j
		Mobius pre_f=new Mobius();
		if (j==2)
			pre_f=new Mobius(CPBase.omega3[2],
					new Complex(0.0),new Complex(0.0),new Complex(1.0));
		else if (j==1)
			pre_f=new Mobius(CPBase.omega3[1],
					new Complex(0.0),new Complex(0.0),new Complex(1.0));
		// bm_f carries base equilateral plus new circle to target location
		Mobius M=(Mobius)dMob_inv.lmultby(pre_f).lmultby(bm_f);

		// apply M to 'fourthCircle' to get the target circle
		CirMatrix outCM=CirMatrix.applyTransform(M,fourthCircle,true);
		
		boolean debug=false; // debug=true;
		if (debug) {// debug=true;
			Mobius tmpm=(Mobius)pre_f.rmultby(dMob_inv);
			deBugging.DebugHelp.mob4matlab("pre_f(dMob_inv)",tmpm);
			CirMatrix tmpcm=CirMatrix.applyTransform(tmpm,fourthCircle,true);
			CircleSimple cS=CirMatrix.cirMatrix_to_geom(tmpcm, 0);
			System.out.println("tmpcm eucl  z/r: "+cS.center+" "+cS.rad);
//			deBugging.DebugHelp.mob4matlab("dMob_inv",dMob_inv);
//			deBugging.DebugHelp.mob4matlab("pre_f", pre_f);
			deBugging.DebugHelp.mob4matlab("M", M);
			cS=CirMatrix.cirMatrix_to_geom(outCM,0);
			System.out.println("outCM eucl z/r: "+cS.center+" "+cS.rad);
		}

		return CirMatrix.cirMatrix_to_geom(outCM, hes);
	}
	
	/**
	 * Develop various schemes to help understand schwarzians: e.g., 
	 * draw circles with color blue-to-red based on sum of 
	 * schwarzians; draw edges blue-to-red based on schwarzians.
	 * @param p PackData
	 * @param flagsegs Vector<Vector<String>> options
	 * @return
	 */
	public static int schwarzReport(PackData p,
			Vector<Vector<String>> flagsegs) {
		int count=0;
		Vector<String> items=null;
		
		if (flagsegs==null || flagsegs.size()==0) {
			CirclePack.cpb.errMsg("usage: sch_report [flags]");
			return count;
		}
		
		Iterator<Vector<String>> its=flagsegs.iterator();
		while (its.hasNext()) {
			items=its.next();
			String str=items.remove(0);
			if (!StringUtil.isFlag(str)) {
				CirclePack.cpb.errMsg(
						"usage: sch_report -[?] : must have c or e flag");
			}
			char c=str.charAt(1);
			str=str.substring(2);
			DispFlags dflags=new DispFlags(str);
			
			// TODO: might add typical 'DispFlag' options to call, then
			//    could have immediate drawing as one option with -c flag
			switch(c) {
			// color vertices by schwarzian sum: blue<0, red>0; 
			//   don't display
			case 'c': {
				
				// set all the colors for validity of the color ramp
				Vector<Double> c_sch=new Vector<Double>();
				for (int v=1;v<=p.nodeCount;v++) {
					HalfLink spokes=p.packDCEL.vertices[v].getEdgeFlower();
					Iterator<HalfEdge> his=spokes.iterator();					
					double accum=0.0;
					while (his.hasNext()) {
						HalfEdge he=his.next();
						double sch=he.getSchwarzian();
						accum +=sch;
					}
					c_sch.add(accum);
				}
				Vector<Color> c_color=
						util.ColorUtil.blue_red_diff_ramp_Color(c_sch);
				Iterator<Color> clst=c_color.iterator();
				// store the color
				for (int v=1;v<=p.nodeCount;v++) {
					p.setCircleColor(v,ColorUtil.cloneMe(clst.next())); 
					count++;
				}
				
				// now, draw just those requested (default to none)
				NodeLink vlist=null;
				if (items!=null && items.size()>0)
					vlist=new NodeLink(p,items);
				if (vlist!=null) {
					Iterator<Integer> vlst=vlist.iterator();
					while(vlst.hasNext()) {
						int v=vlst.next();
						if (dflags.draw || dflags.fill) 
							dflags.setColor(p.getCircleColor(v));
						if (dflags.label)
							dflags.setLabel(Integer.toString(v));
						p.cpDrawing.drawCircle(p.getCenter(v),
								p.getRadius(v),dflags);
						count++;
					}
					p.cpDrawing.repaint();
				}
				break;
			}
			// color all edges so color ramp is valid; 
			//    then draw requested (default all) 
			case 'e': { // color edges for schwarzian: blue < 0, red > 0
				Vector<Double> e_sch=new Vector<Double>(); 
				for (int v=1;v<=p.nodeCount;v++) {
					HalfLink spokes=p.packDCEL.vertices[v].getEdgeFlower();
					Iterator<HalfEdge> sis=spokes.iterator();
					while (sis.hasNext()) {
						HalfEdge he=sis.next();
						if (he.twin.origin.vertIndx>v) 
							e_sch.add(he.getSchwarzian());
					}
				}
				Vector<Color> e_color=
						ColorUtil.blue_red_diff_ramp_Color(e_sch);
				EdgeLink elink=new EdgeLink(p,items);
				if (elink==null || elink.size()==0)
					elink=new EdgeLink(p,"a"); // default to all
				for (int v=1;v<=p.nodeCount;v++) {
					int[] flower=p.getFlower(v);
					for (int j=0;j<flower.length;j++) {
						int w=flower[j];
						if (w>v) {
							if (EdgeLink.ck_in_elist(elink, v, w)) {
								dflags.setColor(e_color.remove(0));
								if (dflags.thickness==0)
									dflags.thickness=5;
								p.cpDrawing.drawEdge(p.getCenter(v),
										p.getCenter(w), dflags);
								count++;
							}
						}
					}
				}
				p.cpDrawing.repaint();
				break;
			}
			} // end of switch
			return count;
		}
		
		return count;
	}
	
	/**
	 * For debugging: print center of circle and of image 
	 * circle under a Mobius.
	 * @param mob Mobius
	 * @param hes int
	 * @param r double
	 * @param z Complex
	 */
	public static void CirMobCir(Mobius mob, int hes,
			double r,Complex z) {
		CircleSimple sC=new CircleSimple();
		Mobius.mobius_of_circle(mob, hes, z, r, sC, false);
		System.out.println("  domain z and r: "+z.toString()+" "+r+
				"   range z and r: "+sC.center.toString()+" "+sC.rad);
	}
	
	/**
	 * Fillin the 'SchwarzData' utility package
	 * @param domf TriAspects
	 * @param tmpg
	 * @param rngF
	 * @param tmpG
	 * @return SchwarzData
	 */
	public static SchwarzData getSchData(TriAspect domf,TriAspect tmpg,
			TriAspect rngF,TriAspect tmpG) {
		boolean debug=false; 

		// duplicates, to avoid messing up originals
		TriAspect domg=new TriAspect(tmpg);
		TriAspect rngG=new TriAspect(tmpG);
		SchwarzData schData=null;
		try {
			schData=comp_Sch_Deriv(domf,domg,rngF,rngG);
		} catch (Exception ex) {
			throw new CombException("failed to get Sch_Deriv");
		}
		
		// unit outward normal eta; depends on geom
		int indx_f=domf.nghb_Tri(domg);
		Complex cv=domf.getCenter(indx_f);
		Complex cw=domf.getCenter((indx_f+1)%3);
		if (domf.hes>0) { // sph
			CircleSimple sC1=SphericalMath.s_to_e_data(cv,
				domf.getRadius(indx_f));
			cv=sC1.center;
			CircleSimple sC2=SphericalMath.s_to_e_data(cw,
				domf.getRadius((indx_f+1)%3));
			cw=sC2.center;
			if (sC1.flag==-1 || sC2.flag==-1)
				throw new MiscException("A disc contains "+
						"infinity: Schwarz not ready "+
						"for this.");
		}
		if (domf.hes<0) { // hyp
			CircleSimple sC=HyperbolicMath.h_to_e_data(cv,
					domf.getRadius(indx_f));
			cv=sC.center;
			sC=HyperbolicMath.h_to_e_data(cw,
					domf.getRadius((indx_f+1)%3));
			cw=sC.center;
		}
		
		// get outward unit normal
		Complex eta=cw.minus(cv).times(new Complex(0,-1));
		eta=eta.divide(eta.abs());

		Complex tmp=schData.Schw_Deriv.divide(eta.conj());
		schData.Schw_coeff=tmp.x;
		
		// this should be real; if not, set flag
		if (Math.abs(schData.Schw_Deriv.y)>.001)
			schData.flag=1;
		
		// get intrinsic schwarzians for domain/range pairs
		schData.domain_schwarzian=getIntrinsicSch(domf,domg);
		schData.range_schwarzian=getIntrinsicSch(rngF,rngG);
		
		if (debug) { // debug=true;
			File tmpdir=new File(System.getProperty("java.io.tmpdir"));
			BufferedWriter dbw;
			dbw=CPFileManager.openWriteFP(tmpdir,
					"SchwarzData.mlab",false);
			try {
				dbw.write("%% matlab output from CirclePack");
				
				// tangency points for four faces
				dbw.write("Tangency points of faces:\n");
				dbw.write("trif="+domf.tanPts2Str()+";\n");
				dbw.write("trig="+tmpg.tanPts2Str()+";\n");
				dbw.write("triF="+rngF.tanPts2Str()+";\n");
				dbw.write("triG="+tmpG.tanPts2Str()+";\n\n");

				// results
				dbw.write("Schwarzian Derivative = "+
						schData.Schw_Deriv.toString()+"\n");
				dbw.write("Schwarzian coeff = "+
						schData.Schw_coeff+"\n");
				dbw.write("domain schwarzian = "+
						schData.domain_schwarzian+"\n");
				dbw.write("range schwarzian = "+
						schData.range_schwarzian+"\n");
				
				Complex sm=schData.Schw_Deriv.times(schData.dmf_deriv);
				dbw.write("dmf_deriv = "+schData.dmf_deriv+"; sch_deriv*m'(1) = "+sm+"\n");
				Complex ssdss=new Complex(schData.domain_schwarzian).minus(schData.range_schwarzian);
				ssdss=ssdss.add(sm);
				dbw.write("check s+SD.m'(1)-s' "+ssdss);

				dbw.write("\n\nthe end");
				dbw.flush();
				dbw.close();
			} catch (Exception ex ) {
				CirclePack.cpb.errMsg("failed some debug output in 'schwarzian.java'");
			}
		}

		return schData;
	}
	
	/** 
	 * This creates 'SchwaraData', but only computes 'Sch_Deriv'.
	 * @param domf TriAspects
	 * @param domg
	 * @param rngF
	 * @param rngG
	 * @return SchwarzData
	 */
	public static SchwarzData comp_Sch_Deriv(TriAspect domf,TriAspect domg,
			TriAspect rngF,TriAspect rngG) {
		boolean debug=false; 
		File tmpdir=null;
		BufferedWriter fbw=null;

		// recompute baseMob's
		Mobius dmf=new Mobius(domf.setBaseMobius());
		Mobius dmg=new Mobius(domg.setBaseMobius());
		Mobius rmF=new Mobius(rngF.setBaseMobius());
		Mobius rmG=new Mobius(rngG.setBaseMobius());
		if (dmf==null || dmg==null || rmF==null || rmG==null)
			return null;

		
		if (debug) { // debug=true;
			tmpdir=new File(System.getProperty("java.io.tmpdir"));
			// for debugging, comparing to matlab output
			fbw=CPFileManager.openWriteFP(tmpdir,
					"InitialBaseMobs.mlab",false);
			try {
				fbw.write("dmf="+dmf.toMatlabString()+"\n");
				fbw.write("dmg="+dmg.toMatlabString()+"\n");
				fbw.write("rmF="+rmF.toMatlabString()+"\n");
				fbw.write("rmG="+rmG.toMatlabString()+"\n");
			} catch(Exception ex) {				
				CirclePack.cpb.errMsg("failed initial output in 'schwarzian.java'");
			}
		}
		
		// Find shared edge index for g, align g to f if necessary
		int indx_g=domg.nghb_Tri(domf);
		HalfEdge g_he=domg.baseEdge;
		if (indx_g==1)
			g_he=g_he.next;
		else if (indx_g==2)
			g_he=g_he.next.next;
		Mobius tmob;
		if ((tmob=domg.alignMe(domf, g_he, 1))!=null)
			dmg=(Mobius)dmg.lmultby(tmob);
	
		// Find shared edge index for G, align G to F if necessary
		int indx_G=rngG.nghb_Tri(rngF);
		HalfEdge G_he=rngG.baseEdge;
		if (indx_G==1)
			G_he=G_he.next;
		else if (indx_G==2)
			G_he=G_he.next.next;
		if ((tmob=(Mobius)rngG.alignMe(rngF, G_he, 1))!=null)
			rmG=(Mobius)rmG.lmultby(tmob);
		
		// Find shared edge indices for f/F
		int indx_f=domf.nghb_Tri(domg);
		int indx_F=rngF.nghb_Tri(rngG);

		// get v/w to start 'schData'
		HalfEdge f_he=domf.baseEdge;
		if (indx_f==1)
			f_he=f_he.next;
		else if (indx_f==2)
			f_he=f_he.next.next;
		
		// get started: assume vert indices agree in domain/range
		SchwarzData schData=new SchwarzData(
				f_he.origin.vertIndx,f_he.next.origin.vertIndx);
		
		// must pre-rotate 'baseMob's so shared edge
		//   corresponds to base edge through 1
		dmf.a=dmf.a.times(CPBase.omega3[indx_f]);
		dmf.c=dmf.c.times(CPBase.omega3[indx_f]);
		dmf.normalize();
		
		// get dmf derivative at 1
		Complex dmfd1=new Complex(dmf.c).add(dmf.d);
		schData.dmf_deriv=new Complex(1.0).divide(dmfd1.times(dmfd1));
		
		rmF.a=rmF.a.times(CPBase.omega3[indx_F]);
		rmF.c=rmF.c.times(CPBase.omega3[indx_F]);
		rmF.normalize();
		
		dmg.a=dmg.a.times(CPBase.omega3[indx_g]);
		dmg.c=dmg.c.times(CPBase.omega3[indx_g]);
		dmg.normalize();
		
		rmG.a=rmG.a.times(CPBase.omega3[indx_G]);
		rmG.c=rmG.c.times(CPBase.omega3[indx_G]);
		rmG.normalize();
		
		Mobius mf=(Mobius)rmF.rmultby(dmf.inverse());
		mf.normalize();
		Mobius mg=(Mobius)rmG.rmultby(dmg.inverse());
		mg.normalize();
		Mobius mgimf=(Mobius)mg.inverse().rmultby(mf);
		mgimf.normalize();
		schData.Schw_Deriv=mgimf.c;

		if (debug && tmpdir!=null) {
			try {
			// baseMob's, adjusted for indices
			fbw.write("\nAdjusted baseMob's:\n");
			fbw.write("dmf="+dmf.toMatlabString()+"\n");
			fbw.write("dmg="+dmg.toMatlabString()+"\n");
			fbw.write("rmF="+rmF.toMatlabString()+"\n");
			fbw.write("rmG="+rmG.toMatlabString()+"\n");
			fbw.write("\n");
		
			// maps f->F and g->G
			fbw.write("mf="+mf.toMatlabString()+"\n");
			fbw.write("mg="+mg.toMatlabString()+"\n");
			fbw.write("\n");
			
			fbw.flush();
			fbw.close();
			} catch(Exception ex) {				
				CirclePack.cpb.errMsg("failed initial output in 'schwarzian.java'");
			}
		}

		return schData;
	}

	/**
	 * Compute the INtrinsic Schwarzian for edge shared by 
	 * two face. Note that g should already have been
	 * adjusted to align along shared edge with f.
	 * @param tri_f TriAspect
	 * @param tri_g TriAspect
	 * @return Double, null if 'baseMob's not set
	 */
	public static Double getIntrinsicSch(TriAspect tri_f,TriAspect tri_g) {
		Mobius dmf=new Mobius(tri_f.setBaseMobius());
		Mobius dmg=new Mobius(tri_g.setBaseMobius());
		if (dmf==null || dmg==null)
			return null;
		boolean debug=false; // debug=true;
		
		// Find shared edges
		int indx_f=tri_f.nghb_Tri(tri_g);
		int indx_g=tri_g.nghb_Tri(tri_f);
		
		// modify dmf; pre-rotate base by omega[indx_f]
		if (indx_f>0) {
			
			dmf.a=dmf.a.times(CPBase.omega3[indx_f]);
			dmf.c=dmf.c.times(CPBase.omega3[indx_f]);
		}

		// modify dmg; pre-rotate, then pre-compose
		//   by rotation about 1 (so map is from equilateral
		//   across edge through 1).
		if (indx_g==0)
			dmg=(Mobius)dmg.rmultby(gFix0);
		else if (indx_g==1)
			dmg=(Mobius)dmg.rmultby(gFix1);
		else if (indx_g==2)
			dmg=(Mobius)dmg.rmultby(gFix2);
		
		if (debug) { // debug=true;
			// draw rgb dots, red should be shared edge
			tri_f.deBugHelp(dmf,true);
			tri_g.deBugHelp(dmg,false);
		}
		
		// our definition uses dmg^{-1}.dmf
		Mobius dom_sch=(Mobius)dmg.inverse().rmultby(dmf);
		dom_sch.normalize();
		Complex c=dom_sch.c;
		if (Math.abs(c.y)>.001) 
			throw new MobException("intrinsic schwarzian has complex part");
		return (Double)c.x;
	}

	/**
	 * The 'uzian' is 1-s where s the intrinsic
	 * schwarzian for some edge. The uzian is better
	 * in many formulas. Typically, schvector is length
	 * n for an n-flower, but often only need n-3 
	 * schwarzians. 
	 * @param schvector double[];
	 * @return double[], same length as input
	 */
	public double[] uzianFunction(double[] schvector) {
		int N=schvector.length-1;
		double[] uzian=new double [N+1];
		for (int j=1;j<=N;j++)
			uzian[j]=1.0-schvector[j];
		return uzian;
	}
	
}
