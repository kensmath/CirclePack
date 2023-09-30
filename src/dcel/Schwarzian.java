package dcel;

import java.awt.Color;
import java.util.Iterator;
import java.util.Vector;

import allMains.CPBase;
import allMains.CirclePack;
import combinatorics.komplex.HalfEdge;
import combinatorics.komplex.RedEdge;
import complex.Complex;
import exceptions.DataException;
import exceptions.MobException;
import geometry.CircleSimple;
import geometry.CommonMath;
import geometry.SphericalMath;
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

/**
 * These are static routines for working with discrete Schwarzian 
 * derivatives and intrinsic schwarzians, which are associated 
 * with tangency packings based on identification of faces with 
 * the "base equilateral"; namely, the tangent triple of circles 
 * of radius sqrt(3), symmetric w.r.t. the origin, and having its 
 * first edge vertical through z=1. See "ftnTheory.SchwarzMap.java"
 * for related work in the setting of maps based on the original 
 * notions in Gerald Orick's thesis.
 * 
 * Here we work with parameters associated with edges in 
 * packing complexes, the "intrinsic" schwarzians. These are stored in 
 * in 'HalfEdge.schwarzian', defaulting to 0.0. The methods here 
 * are for creating, analyzing, manipulating this data. To start 
 * we restrict to tangency packings, and some routines do not apply 
 * in the hyperbolic setting since layouts can leave the disc. 
 * 
 * @author kens, January 2020
 */
public class Schwarzian {

	/**
	 * Compute intrinsic schwarzians for give interior edges 
	 * based only on radii. For each edge, find the 4 radii 
	 * involved, find the base Mobius transformations and 
	 * compute the schwarzian. 
	 * 
	 * Multi-connected cases are more complicated for interior
	 * red edges (i.e., with red twins; e.g., keep affine tori 
	 * in mind.)
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
				if (edge.eutil==-1)
					edge.setSchwarzian(0.0);
				else if (edge.eutil>0) {
					double[] rad = new double[4];
					rad[0] = pdcel.getVertRadius(edge);
					rad[1] = pdcel.getVertRadius(edge.next);
					rad[2] = pdcel.getVertRadius(edge.next.next);
					rad[3] = pdcel.getVertRadius(edge.twin.prev);

					// now get the schwarzian using the radii
					try {
						double schn=Schwarzian.rad_to_schwarzian(rad,p.hes);
						edge.setSchwarzian(schn);
						edge.twin.setSchwarzian(schn);
						edge.eutil=edge.twin.eutil=0;
						count++;
					} catch (DataException dex) {
						throw new DataException(dex.getMessage());
					}
				}
			} // done with while
			return count;
		} // done with simply connected
		
		// Multi-connected case. set 'eutil' to account for side-pairings;
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
				double[] rad = new double[4];
				rad[0] = pdcel.getVertRadius(edge);
				rad[1] = pdcel.getVertRadius(edge.next);
				rad[2] = pdcel.getVertRadius(edge.next.next);
				rad[3] = pdcel.getVertRadius(edge.twin.prev);

				// now get the schwarzian using the radii
				try {
					double schn=Schwarzian.rad_to_schwarzian(rad,p.hes);
					edge.setSchwarzian(schn);
					edge.twin.setSchwarzian(schn);
					edge.eutil=edge.twin.eutil=0;
					count++;
				} catch (DataException dex) {
					throw new DataException(dex.getMessage());
				}
			}
			// else a twinned red edge
			else if (edge.eutil>0) {
				Mobius mob=sideMobs[edge.eutil];
				CircleSimple cs=pdcel.getVertData(edge.twin.prev);
				CircleSimple csout=new CircleSimple();
				Mobius.mobius_of_circle(mob,p.hes,cs,csout,true);
				double[] rad = new double[4];
				rad[0] = pdcel.getVertRadius(edge);
				rad[1] = pdcel.getVertRadius(edge.next);
				rad[2] = pdcel.getVertRadius(edge.next.next);
				rad[3] = csout.rad;

				// now get the schwarzian using the radii
				try {
					double schn=Schwarzian.rad_to_schwarzian(rad,p.hes);
					edge.setSchwarzian(schn);
					edge.twin.setSchwarzian(schn);
					edge.eutil=edge.twin.eutil=0;
					count++;
				} catch (DataException dex) {
					throw new DataException(dex.getMessage());
				}
			}
		} // end of while
		return count;
	}

	/**
	 * Given centers for oriented face <v,w,a>, center for b in 
	 * oriented face <w,v,b>, and geometry, find schwarzian for <v,w>.
	 * Note, we assume tangency.
	 * @param Z Complex[4]
	 * @param hes int, geometry
	 * @return double
	 * @throws DataException
	 */
	public static double cents_to_schwarzian(Complex[] Z,int hes) {
		// compute the face tangency points, then face mobius, i.e.,
		//    the mobius maps FROM the base equilateral to the face
		DualTri dtri=new DualTri(Z[0],Z[1],Z[2],hes); // <v,w,u>
		Complex []tanPts=new Complex[3];
		for (int j=0;j<3;j++)
			tanPts[j]=new Complex(dtri.TangPts[j]);
		Mobius fbase=Mobius.mob_xyzXYZ(
			CPBase.omega3[0],CPBase.omega3[1],CPBase.omega3[2],
			tanPts[0],tanPts[1],tanPts[2],0,hes);
			
		dtri=new DualTri(Z[1],Z[0],Z[3],hes); // <w,v,b>
		for (int j=0;j<3;j++)
			tanPts[j]=new Complex(dtri.TangPts[j]);
		Mobius gbase=Mobius.mob_xyzXYZ(
				CPBase.omega3[0],CPBase.omega3[1],CPBase.omega3[2],
				tanPts[0],tanPts[1],tanPts[2],0,hes);

		Mobius dMob = Schwarzian.getIntrinsicSch(fbase,gbase,0,0);
		return dMob.c.x;
	}
	
	public static double cents_to_schwarzian(PackData p,HalfEdge edge) {
		if (edge.isBdry())
			return 1.0;
		Complex[] cents=new Complex[4];
		HalfEdge he=edge;
		int tick=0;
		do {
			cents[tick++]=p.packDCEL.getVertCenter(he);
			he=he.next;
		} while (tick<=3);
		cents[4]=p.packDCEL.getVertCenter(edge.twin.next.next);
		return cents_to_schwarzian(cents,p.hes);
	}
	
	/**
	 * Given radii (r0,r1,r2) for oriented face {v,w,a},
	 * radius r4 for b in the oriented face {w,v,b}, and
	 * the geometry, find the schwarzian for {v,w} and {w,v}.
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

	public static double rad_to_schwarzian(PackData p,HalfEdge edge) {
		double[] rads=new double[4];
		HalfEdge he=edge;
		HalfEdge htw=edge.twin;
		int tick=0;
		do {
			rads[tick]=p.packDCEL.getVertRadius(he);
			he=he.next;
			htw=htw.next;
			tick++;
		} while (tick<=3);
		rads[3]=p.packDCEL.getVertRadius(edge.twin.next.next);
		return rad_to_schwarzian(rads,p.hes);
	}
	
	/**
	 * Compute intrinsic schwarzian for edge between f and g
	 * via 'baseMobius's, ensuring that trace=+2, det=1. The
	 * intrinsic schwarzian is the edge Mobius derivative of
	 * the map from the base equilaterals F G to f and g. So
	 * The fixed point is 1, the outward normal is 1,
	 * so the complex Schwarzian derivative is real s and
	 * the Mobius we return has the form 
	 *    [1 + s, -s^2;s, 1-s]
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
		Mobius mu_f=(Mobius)bm_f.rmult(pre_f);
	
		Complex wi=new Complex(CPBase.omega3[indx_g]).times(-1.0);
		Mobius pre_g=new Mobius(wi,wi.times(-2.0),
				new Complex(0.0),new Complex(1.0));
		Mobius mu_g=(Mobius)bm_g.rmult(pre_g); // mob_g.det();
		Mobius edgeMob=(Mobius)mu_g.inverse().rmult(mu_f);
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
		outmob.b=new Complex(-1.0*s*s);
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
		Mobius M=(Mobius)dMob_inv.lmult(pre_f).lmult(bm_f);

		// this is the fourth circle in the base equilateral
		CircleSimple cb=
				new CircleSimple(new Complex(4.0),CPBase.sqrt3by2*2.0);
		CirMatrix circle3=new CirMatrix(cb);
		
		// apply M to get the target circle
		CirMatrix outCM=CirMatrix.applyTransform(M,circle3,true);
		
		boolean debug=false; // debug=true;
		if (debug) {// debug=true;
			Mobius tmpm=(Mobius)pre_f.rmult(dMob_inv);
			deBugging.DebugHelp.mob4matlab("pre_f(dMob_inv)",tmpm);
			CirMatrix tmpcm=CirMatrix.applyTransform(tmpm,circle3,true);
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
	 * Compute Mobius mapping FROM "base equilateral" TO the 
	 * face associated with 'edge'. 
	 * @param p CirclePack
	 * @param edge HalfEdge
	 * @return Mobius, return null if 'edge.face' is ideal face
	 */
	public static Mobius base2faceMob(PackData p,HalfEdge edge) {
		if (edge.face!=null && edge.face.faceIndx<0)
			return null;
		Complex[] Z=new Complex[3];
		double[] rads=new double[3]; 
		int tick=0;
		do {
			rads[tick]=p.packDCEL.getVertRadius(edge);
			Z[tick]=p.packDCEL.getVertCenter(edge);
			tick++;
			edge=edge.next;
		} while (tick<3);
		if (p.hes > 0) { // sph? check for circles containing infinity
			for (int j=0;j<3;j++) {
				if ((Z[j].y+rads[j])>Math.PI)
					Z[j]=SphericalMath.getAntipodal(Z[j]);
			}
		}
		
		// find tangency points
		Complex []tpts=new Complex[3];
		for (int j=0;j<3;j++) {
			Complex z1=Z[j];
			Complex z2=Z[(j+1)%3];
			double r1=rads[j];
			double r2=rads[(j+1)%3];
			tpts[j]=CommonMath.get_tang_pt(z1, z2, r1, r2, p.hes);
		}

		Mobius tmpMob=Mobius.mob_xyzXYZ(CPBase.omega3[0],
				CPBase.omega3[1],CPBase.omega3[2],
				tpts[0],tpts[1],tpts[2],0,p.hes);
		
		boolean debug=false; // debug=true;
		if (debug) {
			Complex tp0=SphericalMath.proj_pt_to_sph(
					tmpMob.apply(CPBase.omega3[0])).minus(tpts[0]);
			Complex tp1=SphericalMath.proj_pt_to_sph(
					tmpMob.apply(CPBase.omega3[1])).minus(tpts[1]);
			Complex tp2=SphericalMath.proj_pt_to_sph(
					tmpMob.apply(CPBase.omega3[2])).minus(tpts[2]);
			System.err.println("check: "+tp0+" "+tp1+" "+tp2);
		}
		
		return tmpMob;
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
			// color vertices by schwarzian sum: blue <0, red > 0; 
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
	 * Compute the directed Mobius edge derivative (essentially as 
	 * defined by Orick). Calling routine must align f and g to 
	 * and F and G to share common edge, before providing the
	 * base2face Mobius maps. Note that the complex Schwarzian is
	 * the 2,1 entry of this matrix. 
	 * @param bm_f Mobius
	 * @param bm_g Mobius
	 * @param bm_F Mobius
	 * @param bm_G Mobius
	 * @param indx_fg int, index in f of shared edge with g
	 * @param indx_gf int, 
	 * @param indx_FG int, index in F of shared edge woth G
	 * @param indx_GF int
	 * @return Mobius
	 */
	public static Mobius edgeMobDeriv(
			Mobius bm_f,Mobius bm_g,Mobius bm_F, Mobius bm_G,
			int indx_fg,int indx_gf,int indx_FG,int indx_GF) {
	
		// precompose each Mobius so it identifies the 0 edge
		//    of the base equilateral with appropriate indexed 
		//    edge of the face itself
		Mobius pre_mob=new Mobius(CPBase.omega3[indx_fg],
				new Complex(0.0),new Complex(0.0),new Complex(1.0));
		Mobius mu_f=(Mobius)bm_f.rmult(pre_mob);

		pre_mob=new Mobius(CPBase.omega3[indx_gf],
				new Complex(0.0),new Complex(0.0),new Complex(1.0));
		Mobius mu_g=(Mobius)bm_g.rmult(pre_mob);

		pre_mob=new Mobius(CPBase.omega3[indx_FG],
				new Complex(0.0),new Complex(0.0),new Complex(1.0));
		Mobius mu_F=(Mobius)bm_F.rmult(pre_mob);

		pre_mob=new Mobius(CPBase.omega3[indx_GF],
				new Complex(0.0),new Complex(0.0),new Complex(1.0));
		Mobius mu_G=(Mobius)bm_G.rmult(pre_mob);
		
		// Compose to get Mobius face maps mob_fF and mob_gG
		Mobius mob_fF=(Mobius)mu_F.rmult(mu_f.inverse());
		Mobius mob_gG=(Mobius)mu_G.rmult(mu_g.inverse());
		
		// resulting directed Mobius edge derivative is
		//    inv(mob_gG).mob_fF
		Mobius dMob=(Mobius)mob_gG.inverse().rmult(mob_fF);
		dMob.normalize();
		
		if (dMob.a.add(dMob.d).minus(2.0).abs()>.0001)
			throw new MobException("the trace should be 2.0");

		return dMob;
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

}
