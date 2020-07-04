package packing;

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
import komplex.EdgeSimple;
import listManip.EdgeLink;
import listManip.NodeLink;
import math.CirMatrix;
import math.Mobius;
import util.ColorUtil;
import util.DispFlags;
import util.StringUtil;
import util.TriAspect;

/**
 * These are routines for working with discrete Schwarzian derivatives.
 * Here schwarzians are associated with a packing based on identification
 * of faces with the "base equilateral"; namely, the tangent triple of 
 * circles of radius sqrt(3), symmetric w.r.t. the origin, and having
 * its first edge vertical through z=1. See "ftnTheory.SchwarzMap.java"
 * for related work in the setting of maps, which is based on the original
 * notions of Gerald Orick in his thesis.
 * 
 * Here we work with parameters associated with edge in packing complexes,
 * the "real" schwarzians. These are stored in "KData.schwarzian". (Note:
 * schwarzian length is num+1; last is redundant or always zero, but kept
 * to avoid errors.) The methods here are for creating, analyzing, 
 * manipulating this data. To start we restrict to tangency packing, and 
 * some routines do not apply in the hyperbolic setting since layouts 
 * can leave the disc. 
 * 
 * @author kens, January 2020
 *
 */
public class Schwarzian {
	
	/**
	 * Compute real schwarzians for specified edges based relevant radii 
	 * only, not positions. For each edge, layout the four circles, then 
	 * compute/store schwarzian.
	 * @param p PackData
	 * @param elink EdgeLink, default to all
	 * @return count, 0 on error
	 */
	public static int setByRadii(PackData p,EdgeLink elink) {
		int count=0;
		if (elink==null || elink.size()==0) // default to all
			elink=new EdgeLink(p,"a");
		
		// allocate space
		if (!p.haveSchwarzians()) {
			for (int v=1;v<=p.nodeCount;v++) 
				p.kData[v].schwarzian=new double[p.kData[v].num+1];
		}
		
		Iterator<EdgeSimple> elst=elink.iterator();
		while (elst.hasNext()) {
			EdgeSimple edge=elst.next();
			double []rad=new double[4];
			int v=edge.v;
			int w=edge.w;
			// In hyp case, may swap v,w if v not interior
			if (p.hes<0 && p.kData[v].bdryFlag!=0) {
				int hv=v;
				v=w;
				w=hv;
			}
			int indx_vw=p.nghb(v, w);
			int indx_wv=p.nghb(w, v);
			
			// <v,w,u> is face f, <w,v,a> will be face g
			int u_indx=p.find_common_left_nghb(v,w);
			int a_indx=p.find_common_left_nghb(w,v);
			if (u_indx<0 || a_indx<0) { // bdry edge
				if (u_indx>=0)
					p.kData[v].schwarzian[indx_vw]=0.0;
				if (a_indx>=0)
					p.kData[w].schwarzian[indx_wv]=0.0;
				continue;
			}
			int u=p.kData[v].flower[u_indx];
			int a=p.kData[w].flower[a_indx];
			
			rad[0]=p.rData[v].rad;
			rad[1]=p.rData[w].rad;
			rad[2]=p.rData[u].rad;
			rad[3]=p.rData[a].rad;
			CircleSimple sC0=new CircleSimple();
			CircleSimple sC1=new CircleSimple();
			CircleSimple sC2=new CircleSimple();
			CircleSimple sC3=new CircleSimple();
			
			sC0.rad=rad[0];
			sC1.rad=rad[1];
			sC2.rad=rad[2];
			sC3.rad=rad[3];
			
			// get the four centers in the appropriate geometry
			Complex []Z=new Complex[4];
			
			// find centers for face f <v,w,u>
			int ans=CommonMath.placeOneFace(sC0,sC1,sC2,p.hes);
			if (ans<0) {
				throw new DataException("Problem in 'setByRadii' for v, w ="+v+" "+w);
			}
			Z[0]=new Complex(sC0.center);
			Z[1]=new Complex(sC1.center);
			Z[2]=new Complex(sC2.center);
			
			// find center for a
			sC3=CommonMath.comp_any_center(sC1.center, sC0.center, sC1.rad, sC0.rad, sC3.rad, p.hes);
			Z[3]=new Complex(sC3.center);
			
			// compute the face tangency points, then face mobius, i.e.,
			//    the mobius maps FROM the base equilateral to the face
			DualTri dtri=new DualTri(p.hes,Z[0],Z[1],Z[2]); // <v,w,u>
			Complex []tanPts=new Complex[3];
			for (int j=0;j<3;j++)
				tanPts[j]=new Complex(dtri.TangPts[j]);
			Mobius fbase=Mobius.mob_xyzXYZ(
					CPBase.omega3[0],CPBase.omega3[1],CPBase.omega3[2],
					tanPts[0],tanPts[1],tanPts[2],0,p.hes);
			
			dtri=new DualTri(p.hes,Z[1],Z[0],Z[3]); // <w,v,a>
			for (int j=0;j<3;j++)
				tanPts[j]=new Complex(dtri.TangPts[j]);
			Mobius gbase=Mobius.mob_xyzXYZ(
					CPBase.omega3[0],CPBase.omega3[1],CPBase.omega3[2],
					tanPts[0],tanPts[1],tanPts[2],0,p.hes);

			Mobius dMob = Schwarzian.getMobDeriv(fbase,gbase,0,0);
			if (Math.abs(dMob.c.y)>.001) {
				CirclePack.cpb.errMsg("error: Schwarzian <"+v+" "+w+"> is not real");
			}
			else {
				p.kData[v].schwarzian[indx_vw]=dMob.c.x;
				p.kData[w].schwarzian[indx_wv]=dMob.c.x;
				count++;
			}
		}
		return count;
	}
	
	/**
	 * Compute/store real schwarzians using current centers.
	 * @param p PackData
	 * @param elink EdgeLink, default to all
	 * @return count, 0 on error
	 */
	public static int setByLayout(PackData p,EdgeLink elink) {
		int count=0;
		if (elink==null || elink.size()==0) { // default to all
			elink=new EdgeLink(p,"a"); 
		}
		
		// allocate space
		if (!p.haveSchwarzians()) {
			for (int v=1;v<=p.nodeCount;v++) 
				p.kData[v].schwarzian=new double[p.kData[v].num+1];
		}
		
		Iterator<EdgeSimple> elst=elink.iterator();
		while (elst.hasNext()) {
			EdgeSimple edge=elst.next();
			int v=edge.v;
			int w=edge.w;
			// In hyp case, may swap v,w if v not interior
			if (p.hes<0 && p.kData[v].bdryFlag!=0) {
				int hv=v;
				v=w;
				w=hv;
			}
			int indx_vw=p.nghb(v, w);
			int indx_wv=p.nghb(w, v);
			
			// <v,w,u> is face f, <w,v,a> will be face g
			int u_indx=p.find_common_left_nghb(v,w);
			int a_indx=p.find_common_left_nghb(w,v);
			if (u_indx<0 || a_indx<0) { // bdry edge
				if (u_indx>=0)
					p.kData[v].schwarzian[indx_vw]=0.0;
				if (a_indx>=0)
					p.kData[w].schwarzian[indx_wv]=0.0;
				continue;
			}
			int u=p.kData[v].flower[u_indx];
			int a=p.kData[w].flower[a_indx];
			
			// debug
			// System.out.println("v, w = "+v+","+w);
			
			// get the four centers in the appropriate geometry
			Complex []Z=new Complex[4];
			Z[0]=new Complex(p.rData[v].center);
			Z[1]=new Complex(p.rData[w].center);
			Z[2]=new Complex(p.rData[u].center);
			Z[3]=new Complex(p.rData[a].center);
			
			// compute the face tangency points, then face mobius, i.e.,
			//    the mobius maps FROM the base equilateral to the face
			DualTri dtri=new DualTri(p.hes,Z[0],Z[1],Z[2]); // <v,w,u>
			Complex []tanPts=new Complex[3];
			for (int j=0;j<3;j++)
				tanPts[j]=new Complex(dtri.TangPts[j]);
			Mobius fbase=Mobius.mob_xyzXYZ(
					CPBase.omega3[0],CPBase.omega3[1],CPBase.omega3[2],
					tanPts[0],tanPts[1],tanPts[2],0,p.hes);
			
			dtri=new DualTri(p.hes,Z[1],Z[0],Z[3]); // <w,v,a>
			for (int j=0;j<3;j++)
				tanPts[j]=new Complex(dtri.TangPts[j]);
			Mobius gbase=Mobius.mob_xyzXYZ(
					CPBase.omega3[0],CPBase.omega3[1],CPBase.omega3[2],
					tanPts[0],tanPts[1],tanPts[2],0,p.hes);

			Mobius dMob = Schwarzian.getMobDeriv(fbase,gbase,0,0);
			if (Math.abs(dMob.c.y)>.001) {
				CirclePack.cpb.errMsg("error: Schwarzian <"+v+" "+"> is not real");
			}
			else {
				p.kData[v].schwarzian[indx_vw]=dMob.c.x;
				p.kData[w].schwarzian[indx_wv]=dMob.c.x;
				count++;
			}
		}
		return count;
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
	public static Mobius getMobDeriv(Mobius bm_f,Mobius bm_g,int indx_f,int indx_g) {
		
		// Let F be the base equilateral, edge 0 centered on 1, let G be the contiguous
		//   equilateral across that edge. To get the edge Mobius derivative, we must
		//   pre-compose bm_f and bm_g, which map F to f and g, to get mu_f, mu_g.
		//   For mu_f, pre-rotate so the first edge of F maps to the indx_f edge
		//   of f; i.e., rotation by omega3[indx_f]. 
		//   For mu_g we want to map G to g. We pre-compose bm_g by translation by
		//   -2, rotation by omega3[indx_g], then rotation by pi. 
		Mobius pre_f=new Mobius(CPBase.omega3[indx_f],new Complex(0.0),new Complex(0.0),new Complex(1.0));
		Mobius mu_f=(Mobius)bm_f.rmult(pre_f);
	
		Complex wi=new Complex(CPBase.omega3[indx_g]).times(-1.0);
		Mobius pre_g=new Mobius(wi,wi.times(-2.0),new Complex(0.0),new Complex(1.0));
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
			triasp[f]=new TriAspect(p.hes);
			for (int j=0;j<3;j++)
				triasp[f].vert[j]=p.faces[f].vert[j];
			DualTri dtri=new DualTri(p.hes,p.rData[triasp[f].vert[0]].center,
			    p.rData[triasp[f].vert[1]].center,p.rData[triasp[f].vert[2]].center);
			triasp[f].tanPts=new Complex[3];
			for (int j=0;j<3;j++) {
				triasp[f].setCenter(new Complex(p.rData[triasp[f].vert[j]].center),j);
				triasp[f].tanPts[j]=dtri.getTP(j);
			}
		}
		return triasp;
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
		if (!p.haveSchwarzians()) {
			CirclePack.cpb.errMsg("schwarzians are not allocated");
		}
		
		Iterator<Vector<String>> its=flagsegs.iterator();
		while (its.hasNext()) {
			items=its.next();
			String str=items.remove(0);
			if (!StringUtil.isFlag(str)) {
				CirclePack.cpb.errMsg("usage: sch_report -[?] : must have c or e flag");
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
					double accum=0.0;
					for (int j=0;j<p.kData[v].num;j++)
						accum+=p.kData[v].schwarzian[j];
					c_sch.add(accum);
				}
				Vector<Color> c_color=util.ColorUtil.blue_red_diff_ramp_Color(c_sch);
				Iterator<Color> clst=c_color.iterator();
				for (int v=1;v<=p.nodeCount;v++) {
					p.kData[v].color=clst.next(); // store the color
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
							dflags.setColor(p.kData[v].color);
						if (dflags.label)
							dflags.setLabel(Integer.toString(v));
						p.cpScreen.drawCircle(p.rData[v].center,p.rData[v].rad,dflags);
						count++;
					}
					p.cpScreen.repaint();
				}
				break;
			}
			// color all edges so color ramp valid, then draw requested (default all) 
			case 'e': { // color edges for schwarzian: blue < 0, red > 0
				Vector<Double> e_sch=new Vector<Double>(); 
				for (int v=1;v<=p.nodeCount;v++) {
					for (int j=0;j<p.kData[v].num+p.kData[v].bdryFlag;j++) {
						int w=p.kData[v].flower[j];
						if (w>v) 
							e_sch.add(p.kData[v].schwarzian[j]);
					}
				}
				Vector<Color> e_color=ColorUtil.blue_red_diff_ramp_Color(e_sch);
				EdgeLink elink=new EdgeLink(p,items);
				if (elink==null || elink.size()==0)
					elink=new EdgeLink(p,"a"); // default to all
				for (int v=1;v<=p.nodeCount;v++) {
					for (int j=0;j<p.kData[v].num+p.kData[v].bdryFlag;j++) {
						int w=p.kData[v].flower[j];
						if (w>v) {
							if (EdgeLink.ck_in_elist(elink, v, w)) {
								dflags.setColor(e_color.remove(0));
								if (dflags.thickness==0)
									dflags.thickness=5;
								p.cpScreen.drawEdge(p.rData[v].center, p.rData[w].center, dflags);
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
	 * Compute Mobius transformation from base equilateral to packing 
	 * face f. (The "base" refers to eucl equilateral triangle
	 * symmetric about origin with tangency points the cube roots 
	 * of unity; 1 is the tangency point of the first edge.)
	 * @param p CirclePack
	 * @param f int, face index
	 * @return Mobius
	 */
	public static Mobius faceBaseMob(PackData p,int f) {
		Complex []Z=new Complex[3];
		for (int j=0;j<3;j++) 
			Z[j]=new Complex(p.rData[p.faces[f].vert[j]].center);
		if (p.hes > 0) { // sph? check for circles containing infinity
			for (int j=0;j<3;j++) {
				if ((Z[j].y+p.rData[p.faces[f].vert[j]].rad)>Math.PI)
					Z[j]=SphericalMath.getAntipodal(Z[j]);
			}
		}
		
		// find tangency points
		Complex []tpts=new Complex[3];
		for (int j=0;j<3;j++) {
			Complex z1=p.rData[p.faces[f].vert[j]].center;
			Complex z2=p.rData[p.faces[f].vert[(j+1)%3]].center;
			double r1=p.rData[p.faces[f].vert[j]].rad;
			double r2=p.rData[p.faces[f].vert[(j+1)%3]].rad;
			tpts[j]=CommonMath.get_tang_pt(z1, z2, r1, r2, p.hes);
		}

		Mobius tmpMob=Mobius.mob_xyzXYZ(CPBase.omega3[0],CPBase.omega3[1],CPBase.omega3[2],
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

}
