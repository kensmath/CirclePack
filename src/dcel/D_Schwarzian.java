package dcel;

import java.awt.Color;
import java.util.Iterator;
import java.util.Vector;

import allMains.CPBase;
import allMains.CirclePack;
import complex.Complex;
import exceptions.DataException;
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
import util.TriAspect;

/**
 * These are static routines for working with discrete Schwarzian 
 * derivatives. Here schwarzians are associated with a packing 
 * based on identification of faces with the "base equilateral"; 
 * namely, the tangent triple of circles of radius sqrt(3), 
 * symmetric w.r.t. the origin, and having its first edge 
 * vertical through z=1. See "ftnTheory.SchwarzMap.java"
 * for related work in the setting of maps, which is based on 
 * the original notions of Gerald Orick in his thesis.
 * 
 * Here we work with parameters associated with edges in 
 * packing complexes, the "real" schwarzians. These are stored in 
 * in 'HalfEdge.schwarzian', defaulting to 0.0. The methods here 
 * are for creating, analyzing, manipulating this data. To start 
 * we restrict to tangency packing, and some routines do not apply 
 * in the hyperbolic setting since layouts can leave the disc. 
 * 
 * @author kens, January 2020
 */
public class D_Schwarzian {

	
	/**
	 * Compute real schwarzians for give interior edges based 
	 * only on radii (mode=1) or on centers (mode=2). 
	 * For each edge, find the 4 radii/centers involved, 
	 * and compute the schwarzian. 
	 * 
	 * Multi-connected cases are much more complicated for 
	 * some edegs; we must assume here that we have updated 
	 * the 'RedChain', 'sidePairs', and Mobius maps. Edges fall
	 * into three categories: outer edges of the 'RedChain' faces, 
	 * successive edges between 'RedChain' faces, and remaining 
	 * 'interior' edges. (As an example, keep affine tori in mind.)
	 * @param p PackData
	 * @param hlink HalfLink, default to all (Note, edges, not dual edges)
	 * @param mode int, 1=radii, 2=centers
	 * @return count, 0 on error
	 */
	public static int set_rad_or_cents(PackData p,HalfLink hlink,int mode) {
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
		if (pdcel.pairLink==null) {
			hlst = hlink.iterator();
			HalfEdge edge=null;
			while (hlst.hasNext()) {
				edge=hlst.next();
				if (edge.eutil==-1)
					edge.setSchwarzian(0.0);
				else if (edge.eutil>0) {
					if (mode==1) { // use radii only
						double[] rad = new double[4];
						rad[0] = pdcel.getVertRadius(edge);
						rad[1] = pdcel.getVertRadius(edge.next);
						rad[2] = pdcel.getVertRadius(edge.next.next);
						rad[3] = pdcel.getVertRadius(edge.twin.prev);

						// now get the schwarzian using the radii
						try {
							double[] ivd= {1.0,1.0,1.0,1.0,1.0,1.0};
							double schn = D_Schwarzian.rad_to_schwarzian(rad,ivd,p.hes);
							edge.setSchwarzian(schn);
							count++;
						} catch (DataException dex) {
							throw new DataException(dex.getMessage());
						}
					}
					else if (mode==2) { // use centers only
						Complex[] cents=new Complex[4];
						cents[0]=pdcel.getVertCenter(edge);
						cents[1]=pdcel.getVertCenter(edge.next);
						cents[2]=pdcel.getVertCenter(edge.next.next);
						cents[3]=pdcel.getVertCenter(edge.twin.prev);
						
						// now get the schwarzian using the radii
						try {
							double schn = D_Schwarzian.cents_to_schwarzian(cents,p.hes);
							edge.setSchwarzian(schn);
							count++;
						} catch (DataException dex) {
							throw new DataException(dex.getMessage());
						}
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
			D_SideData sd=pdcel.pairLink.get(j);
			if (sd.mateIndex>=0) { // has mate
				if (Mobius.frobeniusNorm(sd.mob)>.0001)
					sideMobs[j]=sd.mob;
				RedHEdge rtrace=sd.startEdge;
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
				if (mode==1) { // use radii only
					double[] rad = new double[4];
					rad[0] = pdcel.getVertRadius(edge);
					rad[1] = pdcel.getVertRadius(edge.next);
					rad[2] = pdcel.getVertRadius(edge.next.next);
					rad[3] = pdcel.getVertRadius(edge.twin.prev);

					// now get the schwarzian using the radii
					try {
						double[] ivd= {1.0,1.0,1.0,1.0,1.0,1.0};
						double schn = D_Schwarzian.rad_to_schwarzian(rad,ivd,p.hes);
						edge.setSchwarzian(schn);
						count++;
					} catch (DataException dex) {
						throw new DataException(dex.getMessage());
					}
				}
				else if (mode==2) { // use centers only
					Complex[] cents=new Complex[4];
					cents[0]=pdcel.getVertCenter(edge);
					cents[1]=pdcel.getVertCenter(edge.next);
					cents[2]=pdcel.getVertCenter(edge.next.next);
					cents[3]=pdcel.getVertCenter(edge.twin.prev);
					
					// now get the schwarzian using the radii
					try {
						double schn = D_Schwarzian.cents_to_schwarzian(cents,p.hes);
						edge.setSchwarzian(schn);
						count++;
					} catch (DataException dex) {
						throw new DataException(dex.getMessage());
					}
				}				
			}
			// else a twinned red edge
			else if (edge.eutil>0) {
				Mobius mob=sideMobs[edge.eutil];
				CircleSimple cs=pdcel.getVertData(edge.twin.prev);
				CircleSimple csout=new CircleSimple();
				Mobius.mobius_of_circle(mob,p.hes,cs,csout,true);
				if (mode==1) { // use radii only
					double[] rad = new double[4];
					rad[0] = pdcel.getVertRadius(edge);
					rad[1] = pdcel.getVertRadius(edge.next);
					rad[2] = pdcel.getVertRadius(edge.next.next);
					rad[3] = csout.rad;

					// now get the schwarzian using the radii
					try {
						double[] ivd= {1.0,1.0,1.0,1.0,1.0,1.0};
						double schn = D_Schwarzian.rad_to_schwarzian(rad,ivd,p.hes);
						edge.setSchwarzian(schn);
						count++;
					} catch (DataException dex) {
						throw new DataException(dex.getMessage());
					}
				}
				else if (mode==2) { // use centers only
					Complex[] cents=new Complex[4];
					cents[0]=pdcel.getVertCenter(edge);
					cents[1]=pdcel.getVertCenter(edge.next);
					cents[2]=pdcel.getVertCenter(edge.next.next);
					cents[3]=csout.center;
					
					// now get the schwarzian using the radii
					try {
						double schn = D_Schwarzian.cents_to_schwarzian(cents,p.hes);
						edge.setSchwarzian(schn);
						count++;
					} catch (DataException dex) {
						throw new DataException(dex.getMessage());
					}
				}
			}
		} // end of while
		return count;
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
	
	public static double rads_to_schwarzian(PackData p,HalfEdge edge) {
		double[] rads=new double[4];
		double[] invDist=new double[6];
		HalfEdge he=edge;
		HalfEdge htw=edge.twin;
		int tick=0;
		do {
			rads[tick]=p.packDCEL.getVertRadius(he);
			invDist[(tick+2)%3]=he.invDist;
			invDist[(tick+2)%3+3]=htw.invDist;
			he=he.next;
			htw=htw.next;
			tick++;
		} while (tick<=3);
		rads[4]=p.packDCEL.getVertRadius(edge.twin.next.next);
		return rad_to_schwarzian(rads,invDist,p.hes);
	}
	
	/**
	 * Given centers for oriented face <v,w,u>, center for a in 
	 * oriented face <w,v,a>, and geometry, find schwarzian for <v,w>.
	 * Note, we assume tangency.
	 * @param Z Complex[4]
	 * @param hes int, geometry
	 * @return double
	 * @throws DataException
	 */
	public static double cents_to_schwarzian(Complex[] Z,int hes) {
		// compute the face tangency points, then face mobius, i.e.,
		//    the mobius maps FROM the base equilateral to the face
		DualTri dtri=new DualTri(hes,Z[0],Z[1],Z[2]); // <v,w,u>
		Complex []tanPts=new Complex[3];
		for (int j=0;j<3;j++)
			tanPts[j]=new Complex(dtri.TangPts[j]);
		Mobius fbase=Mobius.mob_xyzXYZ(
			CPBase.omega3[0],CPBase.omega3[1],CPBase.omega3[2],
			tanPts[0],tanPts[1],tanPts[2],0,hes);
			
		dtri=new DualTri(hes,Z[1],Z[0],Z[3]); // <w,v,a>
		for (int j=0;j<3;j++)
			tanPts[j]=new Complex(dtri.TangPts[j]);
		Mobius gbase=Mobius.mob_xyzXYZ(
				CPBase.omega3[0],CPBase.omega3[1],CPBase.omega3[2],
				tanPts[0],tanPts[1],tanPts[2],0,hes);

		Mobius dMob = D_Schwarzian.getMobDeriv(fbase,gbase,0,0);
		return dMob.c.x;
	}
	
	/**
	 * Given radii (r0,r1,r2) for oriented face <v,w,u>,
	 * radius r4 for a in the oriented face <w,v,a>, and
	 * inversive distances invDist for the five edges, 
	 * and geometry, find edge schwarzian for <v,w>
	 * @param rad double[4]
	 * @param invDist double[6]: first 3 for <v,w,u>, second 3
	 *                           for <w,v,a>, usual conventions
	 * @param hes int, geometry
	 * @return double
	 * @throws DataException
	 */
	public static double rad_to_schwarzian(double[] rad,double[] invDist,
			int hes) {
		CircleSimple[] sC=new CircleSimple[4];
		for (int i=0;i<4;i++) {
			sC[i]=new CircleSimple();
			sC[i].rad=rad[i];
		}

		// get the four centers in the appropriate geometry
		Complex []Z=new Complex[4];
		
		// find centers for face f <v,w,u>
		double[] ivd= new double[3];
		ivd[0]=invDist[0];
		ivd[1]=invDist[1];
		ivd[2]=invDist[2];
		int ans=CommonMath.placeOneFace(sC[0],sC[1],sC[2],ivd,hes);
		if (ans<0) {
			throw new DataException("Problem in 'rad_to_schwarzian' placeOneFace");
		}
		Z[0]=new Complex(sC[0].center);
		Z[1]=new Complex(sC[1].center);
		Z[2]=new Complex(sC[2].center);
		
		// find center for a in <w,v,a>
		ivd=new double[3];
		ivd[0]=invDist[3];
		ivd[1]=invDist[4];
		ivd[2]=invDist[5];
		sC[3]=CommonMath.comp_any_center(sC[1].center,sC[0].center,sC[1].rad,
				sC[0].rad, rad[3],ivd[0],ivd[1],ivd[2], hes);
		sC[3].rad=rad[3];
		Z[3]=new Complex(sC[3].center);
		
		// compute the face tangency points, then face mobius, i.e.,
		//    the mobius maps FROM the base equilateral to the face
		DualTri dtri=new DualTri(hes,Z[0],Z[1],Z[2]); // <v,w,u>
		Complex []tanPts=new Complex[3];
		for (int j=0;j<3;j++) {
			if (dtri.TangPts==null || dtri.TangPts[j]==null) {
				System.out.println("stop here");
			}
			tanPts[j]=new Complex(dtri.TangPts[j]);
		}
		Mobius fbase=Mobius.mob_xyzXYZ(
				CPBase.omega3[0],CPBase.omega3[1],CPBase.omega3[2],
				tanPts[0],tanPts[1],tanPts[2],0,hes);
		
		dtri=new DualTri(hes,Z[1],Z[0],Z[3]); // <w,v,a>
		for (int j=0;j<3;j++)
			tanPts[j]=new Complex(dtri.TangPts[j]);
		Mobius gbase=Mobius.mob_xyzXYZ(
				CPBase.omega3[0],CPBase.omega3[1],CPBase.omega3[2],
				tanPts[0],tanPts[1],tanPts[2],0,hes);

		Mobius dMob = D_Schwarzian.getMobDeriv(fbase,gbase,0,0);
		if (Math.abs(dMob.c.y)>.001) {
			throw new DataException("error: Schwarzian is not real");
		}
		return dMob.c.x;
	}

	/**
	 * Compute Mobius derivative mobDeriv = mu_g^{-1}*mu_f 
	 * via 'baseMobius's, ensuring that trace=+2, det=1.
	 * The fixed point is 1. Schwarzian is s, mobDiv form is
	 *    [1 + s, -s;s, 1-s]
	 * Calling routine must insure g aligned with f before 
	 * computing the baseMobius maps 'bm_g' and 'bm_f' (maps from 
	 * the base equilateral to f and g). 
	 * @param bm_f Mobius
	 * @param bm_g Mobius
	 * @param indx_f int, index of shared edge in f
	 * @param indx_g int, index of shared edge in g
	 * @return Mobius, identity on error
	 */
	public static Mobius getMobDeriv(Mobius bm_f,Mobius bm_g,
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
		Complex tc=edgeMob.a.plus(edgeMob.d);
		if (tc.x<0.0) {
			edgeMob.a=edgeMob.a.times(-1.0);
			edgeMob.b=edgeMob.b.times(-1.0);
			edgeMob.c=edgeMob.c.times(-1.0);
			edgeMob.d=edgeMob.d.times(-1.0);
			tc=tc.times(-1.0);
		}
		
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
		
		return edgeMob;
	}

	/**
	 * Given a face 'f' in geometry 'hes' and schwarzian 's' across
	 * edge j of f, find the circle across that edge. All we need about f 
	 * is the mobius 'mob_f' from the base equilateral to f and j for the 
	 * shared edge.
	 * @param s double, schwarzian
	 * @param j int, index of shared edge
	 * @param bm_f Mbbius, map from base to f
	 * @param hes int, geometry
	 * @return CircleSimple
	 */
	public static CircleSimple getThirdCircle(double s,int j,Mobius bm_f,int hes) {
		Mobius dMob_inv=new Mobius(new Complex(1-s),new Complex(s),new Complex(-s),new Complex(1+s));
		Mobius pre_f=new Mobius(CPBase.omega3[j],new Complex(0.0),new Complex(0.0),new Complex(1.0));
		
		Mobius mu_g=(Mobius)bm_f.rmult(pre_f);
		mu_g=(Mobius)mu_g.rmult(dMob_inv);

		CirMatrix circle3=new CirMatrix(new Complex(4.0),CPBase.sqrt3by2*2.0);
		CirMatrix outCM=CirMatrix.applyTransform(mu_g,circle3,true);
		
		boolean debug=false; // debug=true;
		if (debug) {// debug=true;
			Mobius tmpm=(Mobius)pre_f.rmult(dMob_inv);
			deBugging.DebugHelp.mob4matlab("pre_f(dMob_inv)",tmpm);
			CirMatrix tmpcm=CirMatrix.applyTransform(tmpm,circle3,true);
			CircleSimple cS=CommonMath.cirMatrix_to_geom(tmpcm, 0);
			System.out.println("tmpcm eucl  z/r: "+cS.center+" "+cS.rad);
//			deBugging.DebugHelp.mob4matlab("dMob_inv",dMob_inv);
//			deBugging.DebugHelp.mob4matlab("pre_f", pre_f);
			deBugging.DebugHelp.mob4matlab("mu_g", mu_g);
			cS=CommonMath.cirMatrix_to_geom(outCM,0);
			System.out.println("outCM eucl z/r: "+cS.center+" "+cS.rad);
		}

		return CommonMath.cirMatrix_to_geom(outCM, hes);
	}
	
	/**
	 * Compute Mobius transformation from base equilateral 
	 * face for 'edge'. (The "base" refers to eucl 
	 * equilateral triangle symmetric about origin with 
	 * tangency points the cube roots of unity; 1 is the 
	 * tangency point of the first edge.)
	 * @param p CirclePack
	 * @param edge HalfEdge
	 * @return Mobius
	 */
	public static Mobius faceBaseMob(PackData p,HalfEdge edge) {
		if (edge.face!=null && edge.face.faceIndx<0)
			return new Mobius();
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
	public static int schwarzReport(PackData p,Vector<Vector<String>> flagsegs) {
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
			// color vertices by schwarzian sum: blue <0, red > 0; don't display
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
				Vector<Color> c_color=util.ColorUtil.blue_red_diff_ramp_Color(c_sch);
				Iterator<Color> clst=c_color.iterator();
				for (int v=1;v<=p.nodeCount;v++) {
					p.setCircleColor(v,ColorUtil.cloneMe(clst.next())); // store the color
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
						p.cpScreen.drawCircle(p.getCenter(v),p.getRadius(v),dflags);
						count++;
					}
					p.cpScreen.repaint();
				}
				break;
			}
			// color all edges so color ramp is valid; 
			//    then draw requested (default all) 
			case 'e': { // color edges for schwarzian: blue < 0, red > 0
				Vector<Double> e_sch=new Vector<Double>(); 
				for (int v=1;v<=p.nodeCount;v++) {
					int[] flower=p.getFlower(v);
					for (int j=0;j<flower.length;j++) {
						int w=flower[j];
						if (w>v) 
							e_sch.add(p.kData[v].schwarzian[j]);
					}
				}
				Vector<Color> e_color=ColorUtil.blue_red_diff_ramp_Color(e_sch);
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
								p.cpScreen.drawEdge(p.getCenter(v), p.getCenter(w), dflags);
								count++;
							}
						}
					}
				}
				p.cpScreen.repaint();
				break;
			}
			} // end of switch
			return count;
		}
		
		return count;
	}

	/**
	 * For debugging: print center of circle and of image circle under a Mobius.
	 * @param mob Mobius
	 * @param hes int
	 * @param r double
	 * @param z Complex
	 */
	public static void CirMobCir(Mobius mob, int hes,double r,Complex z) {
		CircleSimple sC=new CircleSimple();
		Mobius.mobius_of_circle(mob, hes, z, r, sC, false);
		System.out.println("  domain z and r: "+z.toString()+" "+r+
				"   range z and r: "+sC.center.toString()+" "+sC.rad);
	}

	/**
	 * TODO: is this a duplicate of some other routine?
	 * 
	 * Create TriAspects for packing p; generally this is only done
	 * if TriAspects are not available in an active 'AffinePack' or 
	 * 'ProjStruct' packextender. The resulting dual triangles have 
	 * tanPts[j] = tangency point of edge (j,(j+1)%3). 
	 * 
	 * TODO: If p is not simply connected, centers for some faces will 
	 * be in the wrong place. Should process like in p.layoutTree.
	 * @param p PackData
	 * @return TriAspect[]
	 */
	public static TriAspect []DualTriData(PackData p) {
		TriAspect []triasp=new TriAspect[p.faceCount+1];
		for (int f=1;f<=p.faceCount;f++) {
			dcel.Face face=p.packDCEL.faces[f];
			triasp[f]=new TriAspect(p.hes);
			
			HalfEdge he=face.edge;
			
			triasp[f].vert=face.getVerts();
			DualTri dtri=new DualTri(p.hes,
				p.packDCEL.getVertCenter(he),
			    p.packDCEL.getVertCenter(he.next),
			    p.packDCEL.getVertCenter(he.next.next));
			
			triasp[f].tanPts=new Complex[3];
			int j=0;
			do {
				triasp[f].setCenter(p.packDCEL.getVertCenter(he),j);
				triasp[f].tanPts[j]=dtri.getTP(j);
				j++;
				he=he.next;
			} while (he!=face.edge);
		}
		return triasp;
	}


}
