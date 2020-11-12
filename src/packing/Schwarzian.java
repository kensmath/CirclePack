package packing;

import java.awt.Color;
import java.util.Iterator;
import java.util.Vector;

import allMains.CPBase;
import allMains.CirclePack;
import complex.Complex;
import deBugging.LayoutBugs;
import exceptions.DataException;
import geometry.CircleSimple;
import geometry.CommonMath;
import geometry.SphericalMath;
import komplex.DualTri;
import komplex.EdgeSimple;
import komplex.RedEdge;
import komplex.RedList;
import komplex.SideDescription;
import listManip.EdgeLink;
import listManip.NodeLink;
import math.CirMatrix;
import math.Mobius;
import util.ColorUtil;
import util.DispFlags;
import util.StringUtil;
import util.TriAspect;

/**
 * These are static routines for working with discrete Schwarzian derivatives.
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
	 * Compute real schwarzians for specified edges based 
	 * only on radii (mode=1) or only on centers (mode=2).
	 * For each edge, find the 4 radii/centers involved, 
	 * layout them out to compute the schwarzian. 
	 * 
	 * Multi-connected cases are much more complicated for 
	 * some edegs; we must assume here that we have updated 
	 * the 'RedChain', 'sidePairs', and Mobius maps. Edges fall
	 * into three categories: outer edges of the 'RedChain' faces, 
	 * successive edges between 'RedChain' faces, and remaining 
	 * 'interior' edges. (As an example, keep affine tori in mind.)
	 * @param p PackData
	 * @param elink EdgeLink, default to all (Note, edges, not dual edges)
	 * @param mode int, 1=radii, 2=centers
	 * @return count, 0 on error
	 */
	public static int set_rad_or_cents(PackData p,EdgeLink elink,int mode) {
		int count=0;
		if (elink==null || elink.size()==0) // default to all
			elink=new EdgeLink(p,"a");
		
		// allocate space
		if (!p.haveSchwarzians()) {
			for (int v=1;v<=p.nodeCount;v++) 
				p.kData[v].schwarzian=new double[p.kData[v].num+1];
		}

		// easy case (e.g. simply connected) or error, use stored data
		if (p.redChain == null || p.isSimplyConnected() || p.sidePairs==null) {
			Iterator<EdgeSimple> elst = elink.iterator();
			while (elst.hasNext()) {
				EdgeSimple edge = elst.next();
				int v = edge.v;
				int w = edge.w;
				// In hyp case, may swap v,w if v not interior
				if (p.hes < 0 && p.kData[v].bdryFlag != 0) {
					int hv = v;
					v = w;
					w = hv;
				}
				int indx_vw = p.nghb(v, w);
				int indx_wv = p.nghb(w, v);

				// <v,w,u> is face f, <w,v,a> will be face g
				int u_indx = p.find_common_left_nghb(v, w);
				int a_indx = p.find_common_left_nghb(w, v);
				if (u_indx < 0 || a_indx < 0) { // bdry edge
					if (u_indx >= 0)
						p.kData[v].schwarzian[indx_vw] = 0.0;
					if (a_indx >= 0)
						p.kData[w].schwarzian[indx_wv] = 0.0;
					continue;
				}
				int u = p.kData[v].flower[u_indx];
				int a = p.kData[w].flower[a_indx];

				if (mode==1) { // use radii only
					double[] rad = new double[4];
					rad[0] = p.getRadius(v);
					rad[1] = p.getRadius(w);
					rad[2] = p.getRadius(u);
					rad[3] = p.getRadius(a);

					// now get the schwarzian using the radii
					try {
						double schn = rad_to_schwarzian(rad, p.hes);
						p.kData[v].schwarzian[indx_vw] = schn;
						p.kData[w].schwarzian[indx_wv] = schn;
						count++;
					} catch (DataException dex) {
						throw new DataException(dex.getMessage());
					}
				}
				else if (mode==2) { // use centers only
					Complex[] cents=new Complex[4];
					cents[0]=p.getCenter(v);
					cents[1]=p.getCenter(2);
					cents[2]=p.getCenter(u);
					cents[3]=p.getCenter(a);
					
					// now get the schwarzian using the radii
					try {
						double schn = cents_to_schwarzian(cents,p.hes);
						p.kData[v].schwarzian[indx_vw] = schn;
						p.kData[w].schwarzian[indx_wv] = schn;
						count++;
					} catch (DataException dex) {
						throw new DataException(dex.getMessage());
					}
				}
			}
			return count;
		}
		
		// More complicated case: 'RedChain', 'RedList', and side-pairing Mobius.

		// +++++ First concern is 'outer' red edges. Since we need the associated
		//       Mobius to align the faces, we build a vector of vectors of 'RedEdge's
		//       and parallel lists of 'Mobius' and 'EdgeSimple's.
		Vector<Vector<RedEdge>> vec_vec=new Vector<Vector<RedEdge>>(0);
		Vector<Mobius> mob_vec=new Vector<Mobius>(0);
		Vector<EdgeLink> elink_vec=new Vector<EdgeLink>(0);
		for (int i=0;i<p.sidePairs.size();i++) {
			SideDescription sideDes=p.sidePairs.get(i);
			Vector<RedEdge> rE_vec=new Vector<RedEdge>(0);
			EdgeLink el=new EdgeLink(p);
			RedEdge rtrace=sideDes.startEdge;
			while (rtrace!=sideDes.endEdge.nextRed) {
				int f=rtrace.face;
				int v=p.faces[f].vert[rtrace.startIndex];
				int w=p.faces[f].vert[(rtrace.startIndex+1)%3];
				if (v<w) {
					el.add(new EdgeSimple(v,w));
					rE_vec.add(rtrace);
				}
				rtrace=rtrace.nextRed;
			} 
			
			// if non-empty, add to vectors
			if (el.size()>0) {
				elink_vec.add(el);
				vec_vec.add(rE_vec); 
				mob_vec.add(sideDes.mob); 
			}
		}
		
		// ++++++ Second, need vector of 'RedList's
		Vector<RedList> rList_vec=new Vector<RedList>(0);
		boolean done=false;
		RedList rlist=p.redChain;
		while (rlist!=p.redChain || !done) {
			done=true;
			rList_vec.add(rlist);
			rlist=rlist.next;
		}
		
		// list edges between redlist and redlist.next
		EdgeLink rlist_Link = new EdgeLink(p);  // 'util' gives index of red face f in <f,g>
		for (int j=0;j<rList_vec.size();j++) {
			rlist=rList_vec.get(j);
			int f=rlist.face;
			int g=rlist.next.face;
			int indx=p.face_nghb(g,f);
			int v=p.faces[f].vert[indx];
			int w=p.faces[f].vert[(indx+1)%3];
			if (v>w) {
				int hld=v;
				v=w;
				w=hld;
			}
			
			// is it already there?
			if (rlist_Link.isThereVW(v,w)>=0) {
				continue;
			}
			EdgeSimple edge=new EdgeSimple(v,w);
			edge.util=j; // index of relevant 'RedList'
			rlist_Link.add(edge);
		}

		// -------------
		// Now ready to process the given 'EdgeLink'.
		Iterator<EdgeSimple> elst=elink.iterator();
		while (elst.hasNext()) {
			EdgeSimple ege=elst.next();
			int v=ege.v;
			int w=ege.w;
			
			// put in increasing order
			if (v>w) {
				int hld=v;
				v=w;
				w=hld;
			}
			EdgeSimple edge=new EdgeSimple(v,w);			
			// first, check if <v,w> in rededge, redlist, or interior? 0,1,2, resp.
			RedEdge isRedEdge=null;
			Mobius mob=null;
			RedList isRedList=null;
			
			boolean debug=false;  // debug=true;
			if (debug) { 
				LayoutBugs.log_RedCenters(p);
				debug=false;
			}
			
			// is outer 'RedEdge'?
			for (int j=0;(j<elink_vec.size() && isRedEdge==null);j++) {
				EdgeLink el=elink_vec.get(j);
				int indx=el.isThereVW(v, w); 
				if (indx<0)
					continue; // not here
				edge=el.get(indx);
				isRedEdge=vec_vec.get(j).get(indx);
				mob=new Mobius(mob_vec.get(j));
			}
			
			// if not, is it 'RedList'?
			if (isRedEdge==null) {
				int indx=rlist_Link.isThereVW(v,w);
				if (indx>=0) {
					edge=rlist_Link.get(indx);
					isRedList=rList_vec.get(edge.util);
				}
			}

			// now we know how to process this edge
			if (isRedEdge!=null) {
				if (isRedEdge.crossRed==null) {
					p.setSchwarzian(edge, 0.0);
					count++;
				}
				else {
					
					// create two 'TriAspects' to hold the data
					RedList ftrace=(RedList)isRedEdge;
					TriAspect redTri=new TriAspect(p.hes,(RedList)isRedEdge);
					int f=isRedEdge.face;
					redTri.vert=p.faces[f].vert;
					for (int j=0;j<3;j++) {
						double rad=RedList.whos_your_daddy(ftrace,j).rad;
						redTri.setRadius(rad, j);
						Complex z=new Complex(RedList.whos_your_daddy(ftrace,j).center);
						redTri.setCenter(z, j);
					}

					RedList gtrace=(RedList)isRedEdge.crossRed;
					TriAspect crossTri=new TriAspect(p.hes,gtrace);
					int g=gtrace.face;
					crossTri.vert=p.faces[g].vert;
					for (int j=0;j<3;j++) {
						double rad=RedList.whos_your_daddy(gtrace,j).rad;
						crossTri.setRadius(rad, j);
						Complex z=new Complex(RedList.whos_your_daddy(gtrace,j).center);
						crossTri.setCenter(z, j);
					}
					
					// debug=true;
					if (debug) {
						int[] vf=p.faces[f].vert;
						int[] vg=p.faces[g].vert;
						System.out.println("f/g = "+f+"/"+g+"; <"+vf[0]+","+vf[1]+","+vf[2]+"> / <"
								+vg[0]+","+vg[1]+","+vg[2]+">");
						System.out.println("edge: <"+edge.v+","+edge.w+">");
						System.out.println("mob = "+mob.a+","+mob.b+","+mob.c+","+mob.d);
						debug=false;
					}
					
					// apply mob to crossTri so it aligns with 'redTri'.
					if (Mobius.frobeniusNorm(mob)>.00001) {
						CircleSimple sC=new CircleSimple();
						for (int j=0;j<3;j++) {
							Mobius.mobius_of_circle(mob, p.hes,
									crossTri.getCenter(j),crossTri.getRadius(j), 
									sC,true);
							crossTri.setCenter(sC.center, j);
							crossTri.setRadius(sC.rad, j);
						}
					}
					int findx=p.face_nghb(g,f);
					int gindx=p.face_nghb(f,g);
					if (mode==1) { // radii
						double[] rad=new double[4];
						rad[0]=redTri.getRadius(findx);
						rad[1]=redTri.getRadius((findx+1)%3);
						rad[2]=redTri.getRadius((findx+2)%3);
						rad[3]=crossTri.getRadius((gindx+2)%3);
						double sch=rad_to_schwarzian(rad,p.hes);
						p.setSchwarzian(edge,sch);
						count++;
					}
					else if (mode==2) { // centers
						Complex[] cents=new Complex[4];
						cents[0]=redTri.getCenter(findx);
						cents[1]=redTri.getCenter((findx+1)%3);
						cents[2]=redTri.getCenter((findx+2)%3);
						cents[3]=crossTri.getCenter((gindx+2)%3);
						double sch=cents_to_schwarzian(cents,p.hes);
						p.setSchwarzian(edge,sch);
						count++;
					}
				}
			}
			
			else if (isRedList!=null) {
				int f=isRedList.face;
				int g=isRedList.next.face;
				int indxfg=p.face_nghb(g,f);
				int indxgf=p.face_nghb(f, g);
				if (indxfg<0 || indxgf<0)
					throw new DataException("faces "+f+" and "+g+" should share an edge.");
				int vv=p.faces[f].vert[indxfg];
				int ww=p.faces[g].vert[indxgf];
				if (vv!=v && ww!=v)
					throw new DataException("faces "+f+" and "+g+" should share vertex "+v);
				
				// find daddys (responsible for the data); shared edges data should be the same
				RedList[] daddys=new RedList[4];
				for (int j=0;j<3;j++)
					daddys[j]=RedList.whos_your_daddy(isRedList,(indxfg+j)%3);
				daddys[3]=RedList.whos_your_daddy(isRedList.next, (indxgf+2)%3);
				
				if (mode==1) { // radii
//					System.out.println(" doing faces "+f+" and "+g);
					double[] rad=new double[4];
					for (int j=0;j<4;j++)
						rad[j]=daddys[j].rad;
					double sch=rad_to_schwarzian(rad,p.hes);
					p.setSchwarzian(edge,sch);
					count++;
				}
				else if (mode==2) { // centers
					Complex[] cents=new Complex[4];
					for (int j=0;j<4;j++)
						cents[j]=new Complex(daddys[j].center);
					double sch=cents_to_schwarzian(cents,p.hes);
					p.setSchwarzian(edge,sch);
					count++;
				}
			}
			
			// normal interior edge (may get redundants hits here)
			else { 
				int f=p.face_right_of_edge(v, w);
				int g=p.face_right_of_edge(w, v);
				if (f<0 && g<0) {
					p.setSchwarzian(edge,0.0);
				}
				int indxfg=p.face_nghb(g, f);
				int indxgf=p.face_nghb(f, g);
				int[] vrts=new int[4];
				vrts[0]=p.faces[f].vert[indxfg];
				vrts[1]=p.faces[f].vert[(indxfg+1)%3];
				vrts[2]=p.faces[f].vert[(indxfg+2)%3];
				vrts[3]=p.faces[g].vert[(indxgf+2)%3];
				
				if (mode==1) { // radii
					double[] rad=new double[4];
					for (int j=0;j<4;j++)
						rad[j]=p.getRadius(vrts[j]);
					double sch=rad_to_schwarzian(rad,p.hes);
					p.setSchwarzian(edge,sch);
					count++;
				}
				else if (mode==2) { // centers
					Complex[] cents=new Complex[4];
					for (int j=0;j<4;j++)
						cents[j]=new Complex(p.getCenter(vrts[j]));
					double sch=cents_to_schwarzian(cents,p.hes);
					p.setSchwarzian(edge,sch);
					count++;
				}
			}
		} // done with while through given edges
		return count;
	}
	
	/**
	 * Given radii (r0,r1,r2) for oriented face <v,w,u> and radius r4 for
	 * a in the oriented face <w,v,a> and geometry, find edge schwarzian.
	 * @param rad double[]
	 * @param hes int, geometry
	 * @return double
	 * @throws DataException
	 */
	public static double rad_to_schwarzian(double[] rad,int hes) {
		CircleSimple[] sC=new CircleSimple[4];
		for (int i=0;i<4;i++) {
			sC[i]=new CircleSimple();
			sC[i].rad=rad[i];
		}

		// get the four centers in the appropriate geometry
		Complex []Z=new Complex[4];
		
		// find centers for face f <v,w,u>
		int ans=CommonMath.placeOneFace(sC[0],sC[1],sC[2],hes);
		if (ans<0) {
			throw new DataException("Problem in 'rad_to_schwarzian' placeOneFace");
		}
		Z[0]=new Complex(sC[0].center);
		Z[1]=new Complex(sC[1].center);
		Z[2]=new Complex(sC[2].center);
		
		// find center for a in <w,v,a>
		sC[3]=CommonMath.comp_any_center(sC[1].center, sC[0].center, sC[1].rad, sC[0].rad, rad[3], hes);
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

		Mobius dMob = Schwarzian.getMobDeriv(fbase,gbase,0,0);
		if (Math.abs(dMob.c.y)>.001) {
			throw new DataException("error: Schwarzian is not real");
		}
		return dMob.c.x;
	}
	
	/**
	 * Given centers for oriented face <v,w,u> and center for a in 
	 * oriented face <w,v,a> and geometry, find edge schwarzian.
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

		Mobius dMob = Schwarzian.getMobDeriv(fbase,gbase,0,0);
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
			DualTri dtri=new DualTri(p.hes,p.getCenter(triasp[f].vert[0]),
			    p.getCenter(triasp[f].vert[1]),p.getCenter(triasp[f].vert[2]));
			triasp[f].tanPts=new Complex[3];
			for (int j=0;j<3;j++) {
				triasp[f].setCenter(p.getCenter(triasp[f].vert[j]),j);
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
						p.cpScreen.drawCircle(p.getCenter(v),p.getRadius(v),dflags);
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
			Z[j]=p.getCenter(p.faces[f].vert[j]);
		if (p.hes > 0) { // sph? check for circles containing infinity
			for (int j=0;j<3;j++) {
				if ((Z[j].y+p.getRadius(p.faces[f].vert[j]))>Math.PI)
					Z[j]=SphericalMath.getAntipodal(Z[j]);
			}
		}
		
		// find tangency points
		Complex []tpts=new Complex[3];
		for (int j=0;j<3;j++) {
			Complex z1=p.getCenter(p.faces[f].vert[j]);
			Complex z2=p.getCenter(p.faces[f].vert[(j+1)%3]);
			double r1=p.getRadius(p.faces[f].vert[j]);
			double r2=p.getRadius(p.faces[f].vert[(j+1)%3]);
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
	
	/** 
	 * inner class to keep track of edges involving red faces on one
	 * or both sides. Use the neighboring red faces to find the pertinent
	 * stored centers/radii. If left and/or right red is missing, get
	 * remaining data from the packing intself.
	 * @author kstephe2
	 *
	 */
	class BookEdge extends EdgeSimple {
		RedList left_red;
		RedList right_red;
		
		public BookEdge(int vv,int ww) {
			super(vv,ww);
			left_red=null;
			right_red=null;
		}
	}

}
