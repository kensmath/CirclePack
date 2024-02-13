package util;

import allMains.CPBase;
import combinatorics.komplex.DcelFace;
import combinatorics.komplex.HalfEdge;
import complex.Complex;
import dcel.PackDCEL;
import geometry.CircleSimple;
import geometry.CommonMath;
import geometry.EuclMath;
import geometry.HyperbolicMath;
import geometry.SphericalMath;
import komplex.DualTri;
import math.Mobius;

/**
 * Utility class holding geometric info localized to triangular
 * faces of some parent circle packing. Used, e.g., with 
 * projective and affine structures and with discrete 
 * Schwarzians. Geometry depends on parent packing.
 * 
 * TODO: some routines assume eucl data; trying to update as needed
 * 
 * Vector 'labels' typically represent radii localized to this face, 
 * often (but not automatically) in eucl case, are represented as
 * homogeneous coords r0:r1:r2. In some cases these are, in fact, 
 * radii and will be used (e.g. to set the 'rad' entry in redChain 
 * faces), but often are local only (hence, the same vertex v might 
 * have different labels in different faces).
 * 
 * Likewise, 'sidelengths' typically represents edge lengths, 
 * often in eucl case in homogeneous coordinates; note that 
 * given the array 'vert' of vertex indices, 
 *    sides[j]=length of edge <vert[j],vert[(j+1)%3]>
 *  
 * We also keep track of which vertices are 'red' (on the
 * outside of the redChain). Their local radii may be kept 
 * here in 'labels' or 'radii'.
 * 
 * Use this class heavily in studying the discrete Schwarzian
 * derivative. We store the Mobius map FROM the 
 * "base equilateral" TO this face. The three edge
 * 'schwarzian' elements are the real coefficients of
 * the edge's complex schwarzian derivatives. These are
 * only used when this data is part of a mapping between
 * circle packings. If eta is outward normal for an edge,
 * then complex Schwarzian derivative is coeff*conj(eta).
 * (Note: don't confuse with 'intrinsic schwarzian' which
 * is stored in 'HalfEdge.schwarzian'.)   
 * 
 * NOTE: The "base equilateral" face is formed by tangent 
 * triple of eucl circles of radius sqrt(3), symmetric 
 * w.r.t. the origin, and with tangency points at the cube 
 * roots of unity (which are also the outward unit normals 
 * of the 0, 1, 2 edges, resp.)
 * The circle centers are:
 *    center[0]=1-sqrt(3)i, 
 *    center[1]=1+sqrt(3)i, 
 *    center[2]=-2.
 *  
 * @author kens
 */
public class TriAspect extends TriData {
	
	boolean need_update; // signal when data changes require update
	
	// various triples of data (other data in 'TriData' super, eg. radii)
	public Complex[] center;    // centers of circles
	public double[] schwarzian; // scalar coeff for compltex schwarzian deriv
	public Complex[] tanPts;  // tangency points, if saved
	public double[] sidelengths; // edge lengths, [j] = <v[j],v[j+1]>
	
	// Mobius FROM "base" equilateral TO this face; 
	//   determined by centers/radii; 1 mapped to 
	//   tangency point of first edge of 'this'
	Mobius baseMobius; 
	
	// store data for use in, e.g., 'dcel.schwarzian.java'
	public Mobius[] MobDeriv; // directed edge Mobius derivative
	
	// constructor(s)
	public TriAspect() { // default euclidean
		super();
	}
	
	public TriAspect(int geom) {
		super();
		hes=geom;
		tanPts=null;
		labels=new double[3];
		sidelengths=new double[3];
		baseMobius=null;
		need_update=true;
		allocCenters();
		schwarzian=new double[3];
	}
	
	public TriAspect(PackDCEL pdcel,DcelFace face) {
		super(pdcel,face);
		center=new Complex[3];
		center[0]=pdcel.getVertCenter(baseEdge);
		center[1]=pdcel.getVertCenter(baseEdge.next);
		center[2]=pdcel.getVertCenter(baseEdge.next.next);
		invDist=new double[3];
		invDist[0]=baseEdge.getInvDist();
		invDist[1]=baseEdge.next.getInvDist();
		invDist[2]=baseEdge.next.next.getInvDist();
	}
	
	// clone
	public TriAspect(TriAspect asp) {
		this(asp.hes);
		baseEdge=asp.baseEdge; // note: same object
		faceIndx=baseEdge.face.faceIndx;
		for (int j=0;j<3;j++) {
			vert[j]=asp.vert[j];
			center[j]=asp.getCenter(j);
			radii[j]=asp.getRadius(j);
			labels[j]=asp.labels[j];
			sidelengths[j]=asp.sidelengths[j];
			setInvDist(j,asp.getInvDist(j));
			schwarzian[j]=asp.schwarzian[j];
		}
		
		if (asp.baseMobius!=null) 
			baseMobius=new Mobius(asp.baseMobius);
		else 
			baseMobius=new Mobius();

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
		if (center==null)
			allocCenters();
		center[j]=new Complex(z);
		need_update=true;
	}
	
	public void setCircleData(int j,CircleSimple cs) {
		if (center==null)
			allocCenters();
		center[j]=cs.center;
		radii[j]=cs.rad;
	}
	
	public CircleSimple getCircleData(int j) {
		return new CircleSimple(center[j],radii[j]);
	}
	
	/**
	 * Find the incircle. For eucl/sph, just use centers; 
	 * for hyp case, use 3 generalized tangency points.
	 * @param faceIndx Face
	 * @return CircleSimple
	 */
	public CircleSimple getFaceIncircle() {
		CircleSimple c0=new CircleSimple(center[0],radii[0]);
		CircleSimple c1=new CircleSimple(center[1],radii[1]);
		CircleSimple c2=new CircleSimple(center[2],radii[2]);
		return PackDCEL.getTriIncircle(c0,c1,c2,hes);
	}
	
	/**
	 * Compute the circle opposite edge (j,j+1) using
	 * centers, radii, and inv distances.
	 * @param j int
	 * @param useLabels boolean
	 * @return CircleSimple
	 */
	public CircleSimple compOppCircle(int j,boolean useLabels) {
		if (useLabels)
			return CommonMath.comp_any_center(center[j],
					center[(j+1)%3],labels[j],labels[(j+1)%3],labels[(j+2)%3],
					invDist[j],invDist[(j+1)%3],invDist[(j+2)%3],hes);
		return CommonMath.comp_any_center(center[j],
					center[(j+1)%3],radii[j],radii[(j+1)%3],radii[(j+2)%3],
					invDist[j],invDist[(j+1)%3],invDist[(j+2)%3],hes);
	}
	
	public void setBaseMobius() {
		if (tanPts==null)
			setTanPts();
		baseMobius=Mobius.mob_xyzXYZ(
				CPBase.omega3[0],CPBase.omega3[1],CPBase.omega3[2],
				tanPts[0],tanPts[1],tanPts[2],0,this.hes);
	}
	
	public Mobius getBaseMobius() {
		if (baseMobius==null)
			setBaseMobius();
		return baseMobius;
	}
	
	public Complex getTangPt(int j) {
		if (hes < 0)
			return HyperbolicMath.hyp_tangency(center[j],center[(j+1)%3],
					radii[j],radii[(j+1)%3]);
		if (hes > 0)
			return SphericalMath.sph_tangency(center[j],center[(j+1)%3],
					radii[j],radii[(j+1)%3]);
		return EuclMath.eucl_tangency(center[j],center[(j+1)%3],
					radii[j],radii[(j+1)%3]);
	}
	
	public CircleSimple compIncircle() {
		return CommonMath.tri_incircle(center[0],center[1],center[2],hes);
	}

	/**
	 * Copy cent/radii by edge to pdcel
	 * @param pdcel PackDCEL
	 */
	public void data2pdcel(PackDCEL pdcel) {
		HalfEdge he=baseEdge;
		for (int j=0;j<3;j++) {
			pdcel.setCent4Edge(he,center[j]);
			pdcel.setRad4Edge(he,radii[j]);
			he=he.next;
		}
	}
		
	/**
	 * Compute/store "tangency" points based on current
	 * centers. Actually these are points where the incircle
	 * of the triangle formed by the centers hits the edges.
	 * (These points are conformally invariant under mobius
	 * mob, unlike the centers themselves. i.e., 
	 * apply mob to circles and use the new centers.)
	 */
	public void setTanPts() {
		DualTri dtri=new DualTri(center[0],center[1],center[2],hes);
		tanPts=new Complex[3];
		for (int j=0;j<3;j++) 
			tanPts[j]=dtri.getTP(j);
	}
	
	/**
	 * Return index k in 'this' of first edge 'this' shares
	 * with 'ntri', if it exists.
	 * @param ntri TriAspect
	 * @return int index, -1 on failure
	 */
	public int nghb_Tri(TriAspect ntri) {
		int[] nverts=ntri.vert;
		for (int j=0;j<3;j++) {
			int v=nverts[j];
			int w=nverts[(j+1)%3];
			// is edge <v,w> of ntri also an edge of 'this'?
			if (vertIndex(v)>=0 && vertIndex(w)>=0) { // shared edge
				return vertIndex(w);
			}
		}
		return -1;
	}
	
	/**
	 * Compute the eucl centers in normalized position 
	 * (v0 at origin, v1 on positive real, v2 in upper half 
	 * plane) using 'labels' as the current eucl radii.
	 * @return true; no checks yet implemented
	 */
	public boolean setCents_by_label() {
		double baselength=EuclMath.e_ivd_length(labels[0],labels[1],
				getInvDist(0));
		center[0]=new Complex(0.0);
		center[1]=new Complex(baselength);
		CircleSimple sp=new CircleSimple();
		sp=EuclMath.e_compcenter(center[0],center[1],
				labels[0],labels[1],labels[2],
				getInvDist(0),getInvDist(1),getInvDist(2));
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
	 * Utility routine: only use 'TriAspect' to 
	 * hold rad/cent data. Create a baseEquilateral 
	 * in geometry 'hes'. In eucl and spherical 
	 * case, the edge tangency points are at the 
	 * cube roots of unity on the unit circle, with 
	 * the 0th edge tangency point at z=1. In hyp 
	 * case, shrink this down by euclidean factor .05.
	 * @return TriAspect, null on error
	 */
	public static TriAspect baseEquilateral(int hes) {
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
	 * Compute the Mobius that would align 'this' with 
	 * 'acrossTri' (the 'TriAspect' across 'edge'). 
	 * Mode determines what to align: 
	 *    mode 1: use 'radii' (typical, default)
	 *    mode 2: use 'labels' in place of radii
	 *    mode 3: use 'sidelengths'
	 * Return identity on error. 
	 * @param acrossTri TriAspect
	 * @param HalfEdge edge, edge in 'this'
	 * @param mode int
	 * @return Mobius, identity on error
	 */
	public Mobius alignMe(TriAspect acrossTri,
			HalfEdge myEdge,int mode) {

		int indx=edgeIndex(myEdge);
		int windx=acrossTri.edgeIndex(myEdge.twin);
		
		// mobius identifying circles
		if (mode==2) // use 'labels'
			return Mobius.mob_MatchCircles(
					new CircleSimple(center[indx],labels[indx]),
					new CircleSimple(center[(indx+1)%3],labels[(indx+1)%3]),
					new CircleSimple(acrossTri.center[(windx+1)%3],acrossTri.labels[(windx+1)%3]),
					new CircleSimple(acrossTri.center[windx],acrossTri.labels[windx]),
					hes,acrossTri.hes);
		if (mode==3) { // use sidelengths
			if (hes==0 && acrossTri.hes==0) {
				double factor=acrossTri.sidelengths[windx]/sidelengths[indx];
				Mobius mob=new Mobius();
				mob.a=new Complex(factor);
				mobiusMe(mob);
				Complex a=center[indx];
				Complex b=center[(indx+1)%3];
				Complex A=acrossTri.center[(windx+1)%3];
				Complex B=acrossTri.center[windx];
				return Mobius.mob_abAB(a, b, A, B);
			}
			
			// TODO: not yet ready in sph/hyp cases
			
			return new Mobius();
		}

		// else assume mode==1, use radii; do we need to adjust?
		CircleSimple csMe_v=new CircleSimple(center[indx], radii[indx]);
		CircleSimple csMe_w=new CircleSimple(center[(indx+1)%3], radii[(indx+1)%3]);
		CircleSimple cs_v=new CircleSimple(acrossTri.center[(windx+1)%3],acrossTri.radii[(windx+1)%3]);
		CircleSimple cs_w=new CircleSimple(acrossTri.center[windx],acrossTri.radii[windx]);
		if (!csMe_v.isEqual(cs_v) || !csMe_w.isEqual(cs_w))
			return Mobius.mob_MatchCircles(csMe_v,csMe_w,cs_v,cs_w,
				hes,acrossTri.hes);
		return new Mobius(); // identity
	}
	
	/**
	 * Apply a Mobius transformation to my centers/radii.
	 * Note: other data, eg. labels, sides, tangPts, 
	 * are NOT adjusted
	 * @param mob Mobius
	 */
	public void mobiusMe(Mobius mob) {
		if (Mobius.frobeniusNorm(mob)>.00001) {
			CircleSimple sC=new CircleSimple();
			for (int j=0;j<3;j++) {
				Mobius.mobius_of_circle(mob,hes,
					getCenter(j),getRadius(j), sC,true);
				setCenter(sC.center, j);
				setRadius(sC.rad, j);
			}
		}
	}
	
	/**
	 * Assume 'center's give eucl face in normalized 
	 * position based on current 'labels'. Given vert 
	 * v2 and centers of opposite edge e, adjust 
	 * 'center', 'labels', and 'sides' data so 
	 * centers of e match given centers. (Used to 
	 * layout this face based on contiguous face 
	 * across e already in place.)
	 * 
	 * TODO: use the newer 'alignMe' code here
	 * 
	 * @param v2 int, vertex to be placed 
	 * @param c0 Complex 
	 * @param c1 Complex, centers of for <v0,v1> of this face
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
	 * Assume 'center's have been computed in 
	 * normalized position* from 'labels'. Adjust 
	 * the data so the face aligns with that across 
	 * edge opposite to v2.
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
			sidelengths[j]=tmp[j];
	}
	
	/**
	 * Sets homogeneous 'labels' based on current 'sides' data. 
	 * In particular, value at corner a is (B+C-A)/2, where A, B, C 
	 * are the side lengths.
	 * 
	 * TODO: have to adjust for invDist
	 */
	public void sides2Labels() {
		for (int j=0;j<3;j++) 
			labels[j]=(0.5)*(sidelengths[j]+sidelengths[(j+2)%3]-sidelengths[(j+1)%3]);
	}
	
	/**
	 * Sets homogeneous 'sides' based on current 'labels' and
	 * 'invDist' data. 
	 */
	public void labels2Sides() {
		for (int j=0;j<3;j++)
			sidelengths[j]=EuclMath.e_ivd_length(labels[j],labels[(j+1)%3],
					getInvDist(j));
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
	 * Normalize 'labels' vector so the max entry is 1.0.
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
	 * Given vertex 'v' (from parent packing) of 
	 * this face, return the skew, log(left/right 
	 * side lengths) (as seen from v), with label 
	 * for v scaled by 't'. Also return the 
	 * derivative with respect to t. I.e., returns 
	 * log((ta+c)/(ta+b)) and t*(b-c)/((ta+c)(ta+b)),
	 * where (a,b,c) are the labels (a is at v).
	 * @param v int, vert in parent
	 * @param t double, scale for label at v
	 * @return double, [0]=log, [1]=derivative
	 */ 
	public double[] skew(int v,double t) {
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
	 * Return the angle at v, but with the recorded 
	 * eucl label at v multiplied by 't'. Thus, for 
	 * t=1 this just computes the angle. Also, 
	 * return the derivative w.r.t. t.
	 * @param v int, vertex in parent packing
	 * @param t double, by which 'labels' at v is scaled.
	 * @return double[2]: [0]=angle sum, [1]=derivative, null on error
	 */
	public double[] angleV(int v,double t) {
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
	 * Find the eucl area of the sector at 'v' 
	 * based on the labels
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
	 * Using center data, compute the area of 
	 * eucl face 'sector' at v. Assume angles 
	 * already in 'angleV'. The 'sector' is the 
	 * sector of the circle at v going through
	 * the points of tangency of the face 
	 * incircle with the sides from v.
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
