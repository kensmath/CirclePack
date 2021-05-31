package util;

import allMains.CPBase;
import complex.Complex;
import geometry.EuclMath;
import geometry.HyperbolicMath;
import geometry.SphericalMath;
import geometry.CircleSimple;
import komplex.DualTri;
import komplex.RedList;
import math.Mobius;

/**
 * Utility class holding geometric info localized to 
 * (triangular only) faces of some parent circle packing. 
 * Used, e.g., with projective and affine structures and with 
 * discrete Schwarzians. Geometry depends on parent packing.
 * 
 * TODO: some routines assume eucl data; trying to update as needed
 * 
 * Vector 'labels' typically represents radii localized to this face, 
 * often (but not automatically) in eucl case, are represented as
 * homogeneous coords r0:r1:r2. In some cases these are, in fact, 
 * radii and will be used (e.g. to set the 'rad' entry in redChain 
 * faces), but often are local only (hence, the same vertex v might 
 * have different labels in different faces).
 * 
 * Likewise, 'sides' typically represents edge lengths, often in
 * eucl case, in homogeneous coordinates; note that 
 *    sides[j]=length of edge <vert[j],vert[(j+1)%3]>
 *  
 * We also keep track of which vertices are 'red' (on the
 * outside of the redChain). Their local radii may be kept 
 * here in 'labels' or 'radii'.
 * 
 * For use with Schwarzian derivative, we store Mobius of an
 * equilateral face to this face. The "base" equilateral face
 * is formed by tangent triple of eucl circles of radius 
 * sqrt(3), symmetric w.r.t. the origin, and with tangeny 
 * points at the cube roots of unity (which are also the outward
 * unit normals of the 0, 1, 2 edges, resp. Also, circle centers are
 * center[0]=1-sqrt(3)i, center[1]=1+sqrt(3)i, center[2]=-2.
 *  
 * @author kens
 */
public class TriAspect extends TriData {
	
	public RedList redList; // 'packData.redChain' entry for this (generally 'null')
	boolean need_update; // signal when data changes require update
	
	// various triples of data (other data in 'TriData' super)
	public Complex[] center;    // centers of circles
	public double []schwarzian; // signed scalar coeffs for schwarzian derivative 
	public boolean []redFlags; // true if vertex is on outside of redChain
	public Complex []tanPts;  // tangency points, if saved
	public double []labels; // labels for verts: often, treated as homogeneous coords
	public double []sides; // edge lengths, [j] = <v[j],v[j+1]>
	public double []t_vals;  // label ratios: t_vals[j]=labels[(j+1)%3]/labels[j]
	
	// Base data is determined by centers and radii; relate the actual face to
	//   the "base" equilateral. 
	public Mobius baseMobius; 
	public double []baseSchwarz;  
	
	// store data for use in, e.g., 'Schwarzian.java'
	public Mobius []MobDeriv; // directed edge Mobius derivative
	
	// constructor(s)
	public TriAspect() { // default euclidean
		super();
		
	}
	
	public TriAspect(int geom) {
		this(geom,(RedList)null);
	}

	public TriAspect(int geom,RedList rl) {
		hes=geom;
		if (rl!=null) {
			face=rl.face;
		}
		redList=rl;
		redFlags=new boolean[3];
		tanPts=null;
		labels=new double[3];
		sides=new double[3];
		t_vals=new double[3];
		baseMobius=new Mobius();
		baseSchwarz=new double[3];
		need_update=true;
		allocCenters();
		schwarzian=new double[3];
		for (int j=0;j<3;j++) {
			schwarzian[j]=0.0;
		}
	}
	
	// clone
	public TriAspect(TriAspect asp) {
		this(asp.hes,asp.redList);
		face=asp.face;
		for (int j=0;j<3;j++) {
			vert[j]=asp.vert[j];
			center[j]=asp.getCenter(j);
			radii[j]=asp.getRadius(j);
			labels[j]=asp.labels[j];
			sides[j]=asp.sides[j];
			redFlags[j]=asp.redFlags[j];
			t_vals[j]=asp.t_vals[j];
			invDist[j]=asp.invDist[j];
			schwarzian[j]=asp.schwarzian[j];
		}
		if (asp.baseMobius!=null) {
			baseMobius=new Mobius(asp.baseMobius);
			for (int j=0;j<3;j++)
				baseSchwarz[j]=asp.baseSchwarz[j];
		}
		else {
			baseMobius=new Mobius();
			baseSchwarz=new double[3];
		}
		if (asp.MobDeriv!=null) {
			MobDeriv = new Mobius[3];
			for (int j=0;j<3;j++) 
				MobDeriv[j]=new Mobius(asp.MobDeriv[j]);
		}
		if (asp.tanPts!=null) {
			tanPts=new Complex[3];
			for (int j=0;j<3;j++)
				tanPts[j]=new Complex(asp.tanPts[j]);
		}
	}
	
	public void allocCenters() {
		center=new Complex[3];
		for (int j=0;j<3;j++)
			center[j]=new Complex(0.0);
	}

	public void setRadius(double r,int j) {
		radii[j]=r;
		need_update=true;
	}
	
	public void setCenter(Complex z,int j) {
		center[j]=new Complex(z);
		need_update=true;
	}

	/**
	 * Compute 'baseMobius' based on current 'tanPts'. This is
	 * Mobius mapping FROM base equilateral TO this face. 
	 */
	public void setBaseMob() {
		Complex rt3=new Complex(-.5,CPBase.sqrt3by2);
		baseMobius=Mobius.mob_xyzXYZ(new Complex(1.0),rt3,rt3.conj(),
				tanPts[0],tanPts[1],tanPts[2],0,hes);
	}
		
	/**
	 * Compute and store the tangency points based on current
	 * centers (computation assumes the radii are those of a tangent 
	 * triple with the given centers).
	 */
	public void setTanPts() {
		DualTri dtri=new DualTri(hes,center[0],center[1],center[2]);
		tanPts=new Complex[3];
		for (int j=0;j<3;j++) 
			tanPts[j]=dtri.getTP(j);
	}
	
	/**
	 * Return index of initial vertex of first shared edge 
	 * with 'ntri', it it exists.
	 * @param ntri
	 * @return int index, -1 on failure
	 */
	public int nghb_Tri(TriAspect ntri) {
		int[] nverts=ntri.vert;
		for (int j=0;j<3;j++) {
			int v=nverts[j];
			int w=nverts[(j+1)%3];
			if (vertIndex(v)>=0 && vertIndex(w)>=0) { // shared edge
				return vertIndex(w);
			}
		}
		return -1;
	}
	
	/**
	 * Compute the centers in normalized position (v0 at origin,
	 * v1 on positive real, v2 in upper half plane) using 'labels' 
	 * entries as the current euclidean radii.
	 *
	 * @return true; no checks yet implemented
	 */
	public boolean setCenters() {
		center[0]=new Complex(0.0);
		center[1]=new Complex(labels[0]+labels[1]);
		center[2]=new Complex(0.0);
		CircleSimple sp=new CircleSimple();
		sp=EuclMath.e_compcenter(center[0],center[1],
				labels[0],labels[1],labels[2],1.0,1.0,1.0);
		center[2]=sp.center;
		return true;
	}
	
	/**
	 * Get the center as new Complex.
	 * @param j int, index in 'vert'
	 * @return new Complex
	 */
	public Complex getCenter(int j) {
		return new Complex(center[j]);
	}
	
	public double getSchwarzian(int j) {
		return schwarzian[j];
	}

	public void setSchwarzian(int j, double sch) {
		schwarzian[j]=sch;
	}

	/**
	 * Utility routine: only use 'TriAspect' to hold rad/cent data.
	 * Create a baseEquilateral in geometry 'hes'. In eucl and spherical 
	 * case, the edge tangency points are at the cube roots of unit 
	 * on the unit circle, with the 0th edge tangency point at z=1. 
	 * In hyp case, shrink this down by euclidean factor .05.
	 * @return TriAspect, null on error
	 */
	public static TriAspect baseEquilaterl(int hes) {
		TriAspect tri=new TriAspect(hes);
		CircleSimple cS=new CircleSimple();
		for (int j=0;j<3;j++) {
			Complex rot=new Complex(1.0,-CPBase.sqrt3);
			cS.center=CPBase.omega3[j].times(rot);  // rotate clw by pi/3 or 2pi/3
			cS.rad=CPBase.sqrt3;
			if (hes<0)  // shrink factor .05, convert
				cS=HyperbolicMath.e_to_h_data(cS.center.times(0.05), cS.rad*0.05);
			else if (hes>0)  // sph, convert
			cS=SphericalMath.e_to_s_data(cS.center, cS.rad);
			tri.setRadius(cS.rad, j);
			tri.setCenter(cS.center, j);
		}
		return tri;
	}
	
	/**
	 * Compute the Mobius that would align this 'TriAspect' 
	 * with the 'TriAspect' across edge <v,w>. Return identity on error 
	 * @param indx int, index of v for my oriented edge <v,w> 
	 * @param across TriAspect, face across edge <v,w>
	 * @return Mobius
	 */
	public Mobius alignMe(int indx,TriAspect across) {

		int w=vert[(indx+1)%3];
		int windx=across.vertIndex(w);
		
		// mobius identifying circles
		Mobius mob=Mobius.mob_MatchCircles(center[indx], radii[indx],
				center[(indx+1)%3], radii[(indx+1)%3],
				across.center[(windx+1)%3],across.radii[(windx+1)%3],
				across.center[windx],across.radii[windx],
				hes,across.hes);
		return mob;
	}
	
	/**
	 * Assume 'center's give euclidean face in normalized position 
	 * based on current 'labels'. Given vert v2 and centers of 
	 * opposite edge e, adjust 'center', 'labels', and 'sides' data
	 * so centers of e match given centers. (Used to layout this face 
	 * based on contiguous face across e already in place.)
	 * 
	 * TODO: use the newer 'alignMe' code here
	 * 
	 * @param v2 vertex to be placed 
	 * @param c0 c1 centers of edge for <v0,v1>.
	 * @return true if it seems to work
	 */
	public boolean adjustData(int v2,Complex c0,Complex c1) {
		int k2=vertIndex(v2);
		int k0=(k2+1)%3;
		int k1=(k2+2)%3;
		Complex inc=c1.minus(c0);
		Complex dis=center[k1].minus(center[k0]);
		Complex Z=inc.divide(dis); 
		center[k2]=center[k2].minus(center[k0]).times(Z).add(c0);
		center[k0]=new Complex(c0);
		center[k1]=new Complex(c1);
		double absZ=Z.abs();
		for (int j=0;j<3;j++) 
			labels[j] *=absZ;
		labels2Sides(); // update the sides	
		return true;
	}
	
	/** 
	 * Assume 'center's have been computed in normalized position
	 * from 'labels'. Adjust the data so the face aligns with 
	 * that across edge opposite to v2.
	 * 
	 * TODO: use the newer 'alignMe' code here
	 * 
	 * @param v2 vertex
	 * @param across TriAspect
	 * @return true if seemed to work
	 */
	public boolean adjustData(int v2,TriAspect across) {
		int myJ=vertIndex(v2);
		if (myJ<0) return false; // v2 isn't one of my vertices
		int v0=vert[(myJ+1)%3];
		int hit0=across.vertIndex(v0);
		int v1=vert[(myJ+2)%3];
		int hit1=across.vertIndex(v1);
		if (hit0<0 || hit1<0) return false; // v0 or v1 isn't shared with across
		return adjustData(v2,across.center[hit0],across.center[hit1]);
	}

	/**
	 * Sets 'labels' based on current 'center' data. In particular,
	 * value at corner a is (B+C-A)/2, where A, B, C are the
	 * side lengths.
	 */
	public void centers2Labels() {
		double []tmp=new double[3];
		try {
			for (int j=0;j<3;j++) {
				tmp[j]=(.5)*(center[(j+1)%3].minus(center[j]).abs()+
						center[(j+2)%3].minus(center[j]).abs()-
						center[(j+2)%3].minus(center[(j+1)%3]).abs());
			} 
		} catch(Exception ex) {
			return;
		}
		for (int j=0;j<3;j++) 
			labels[j]=tmp[j];
	}
	
	/**
	 * Sets 'sides' based on current 'center' data. 
	 */
	public void centers2Sides() {
		double []tmp=new double[3];
		try {
			for (int j=0;j<3;j++) {
				tmp[j]=center[(j+1)%3].minus(center[j]).abs();
			} 
		} catch(Exception ex) {
			return;
		}
		for (int j=0;j<3;j++) 
			sides[j]=tmp[j];
	}
	
	/**
	 * Sets homogeneous 'labels' based on current 'sides' data. 
	 * In particular, value at corner a is (B+C-A)/2, where A, B, C 
	 * are the side lengths.
	 */
	public void sides2Labels() {
		for (int j=0;j<3;j++) 
			labels[j]=(0.5)*(sides[j]+sides[(j+2)%3]-sides[(j+1)%3]);
	}
	
	/**
	 * Sets homogeneous 'sides' based on current 'labels' data. 
	 */
	public void labels2Sides() {
		for (int j=0;j<3;j++)
			sides[j]=labels[j]+labels[(j+1)%3];
	}
	
	/**
	 * Set 'labels' randomly, values in (0,1).
	 */
	public void randomRatio() {
		for (int j=0;j<3;j++) 
			labels[j]=Math.random();
	}
	
	/**
	 * Normalize so sum of 'labels' is zero by subtracting 
	 * 1/3 of the sum from each entry. (Use when these represent 
	 * logs of ratios.)
	 */
	public void logNorm() {
		double sm=(labels[0]+labels[1]+labels[2])/3.0;
		for (int j=0;j<3;j++)
			labels[j] -= sm;
	}
	
	/**
	 * Normalize the 'labels' vector so the max entry
	 * is 1.0.
	 */
	public void normRatio() {
		int n=0;
		double max=labels[n];
		if (labels[1]>max) {
			max=labels[1];
			n=1;
		}
		if (labels[2]>max) 
			n=2;
		for (int j=0;j<3;j++)
			labels[j] /= labels[n];
	}
	
	/**
	 * Given vertex 'v' (from parent packing) of this face, 
	 * return the 2-vector of other entries in 'labels' after 
	 * scaling so value at 'v' is 1.0.
	 * @param v, vertex from parent packing
	 * @return double[2] of other labels, null on error.
	 */
	public double []getRatio(int v) {
		int k=vertIndex(v);
		if (k<0) return null;
		double []newRatio = new double[2];
		newRatio[0]=labels[(k+1)%3]/labels[k];
		return newRatio;
	}
	
	/**
	 * Given vertex 'v' (from parent packing) of this face, return
	 * the skew, log(left/right side lengths) (as seen from v), with 
	 * label for v scaled by 't'. Also return the derivative with respect
	 * to t. I.e., returns 
	 * log((ta+c)/(ta+b)) and t*(b-c)/((ta+c)(ta+b)),
	 * where (a,b,c) are the labels (a is at v).
	 * @param v int, vert in parent
	 * @param t double, scale for label at v
	 * @return double, [0]=log, [1]=derivative
	 */ 
	public double []skew(int v,double t) {
		int k=vertIndex(v);
		if (k<0) return null;
		double []newRatio = new double[2];
		double ta=t*labels[k];
		double b=labels[(k+1)%3];
		double c=labels[(k+2)%3];
		newRatio[0]=Math.log((ta+c)/(ta+b));
		newRatio[1]=t*(b-c)/((ta+c)*(ta+b));
		return newRatio;
	}

	/**
	 * Return the angle at v, but with the recorded eucl label
	 * at v multiplied by 't'. Thus, for t=1 this just computes 
	 * the angle. Also, return the derivative w.r.t. t.
	 * @param v int, vertex in parent packing
	 * @param t double, by which 'labels' at v is scaled.
	 * @return double[2]: [0]=angle sum, [1]=derivative, null on error
	 */
	public double []angleV(int v,double t) {
		int k=vertIndex(v);
		if (k<0) 
			return null;
		double r1=labels[k];
		double r2=labels[(k+1)%3];
		double r3=labels[(k+2)%3];
		// TODO: adjust for inv distances
		double []ans=new double[2];
		ans[0]=Math.acos(EuclMath.e_cos_overlap(r1*t,r2,r3));
		ans[1]=EuclMath.Fx(r1*t,r2,r3)*r1;
		return ans;
	}
	
	/**
	 * Find the eucl area of the sector at 'v' based on the labels
	 * @param v int, parent index of 'v'
	 * @return double, rad at 'v' times angle at 'v'
	 */
	public double pieSliceArea(int v) {
		int j=vertIndex(v);
		double angV=angleV(v,1.0)[0];
		double r=labels[j];
		return r*r*angV/2.0;
	}
	
	/**
	 * Using center data, compute the area of eucl face 'sector' at v.
	 * Assume angles already in 'angleV'.
	 * The 'sector' is the sector of the circle at v going through
	 * the points of tangency of the face incircle with the sides
	 * from v.
	 * @param v, vert index in parent packing 
	 * @return area, or -1 on error
	 */
	public double sectorAreaZ(int v) {
		int j=vertIndex(v);
		double r=(.5)*(center[(j+1)%3].minus(center[j]).abs()+
				center[(j+2)%3].minus(center[j]).abs()-
				center[(j+2)%3].minus(center[(j+1)%3]).abs());
		return (0.5*r*r*angleV(v,1.0)[0]);
	}

}
