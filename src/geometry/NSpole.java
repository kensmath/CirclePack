package geometry;

import java.util.Iterator;
import java.util.Vector;

import allMains.CirclePack;
import complex.Complex;
import listManip.NodeLink;
import math.Mobius;
import math.Point3D;
import orickStuff.CPI_CP_PackingUtility;
import packing.PackData;
import util.StringUtil;

/**
 * Select and implement 'NSpole' normalizations. 
 * @author kstephe2
  */
public class NSpole {
	
//	 MAXERR .001;
	PackData packData; 	// parent packing
	Complex []Z;  		// current points (theta,phi), indexed from 1
	Mobius Mob;			// current accumulated Mobius
	int N_pole;
	int S_pole;
	int E_pole;
	double factor;		// ratio factor between size of N and S
	double maxerr;
	double latesterr;
	Point3D centroid;	// latest centroid
	boolean debug=true; // false;
	int edgeCount;      // need for tangency normalization
	
	// Constructor
	public NSpole(PackData p) {
		packData=p;
		N_pole = packData.alpha;
		S_pole = packData.nodeCount;
		E_pole = 0;
		factor = 1.0;
		Mob=new Mobius(); // identity
		maxerr=.001;
		latesterr=2.0;
		edgeCount=0;
	}
	
	/**
	 * Process an NSpole call
	 * @param flagSegs Vector<Vector<String>>
	 * @return int, 0 on failure
	 */
	public int parseNSpole(Vector<Vector<String>> flagSegs) {
		Vector<String> items;
		edgeCount=setEdgeCount();

		// default: put centroid of tangency points or centers at origin, 1/2017
		if (flagSegs == null || flagSegs.size() == 0) {

			// if overlaps are set, use the centers.
			if (packData.overlapStatus) {
				Complex []T=loadCenters(); // from local data
				double best=SphericalMath.getCentroid(T).normSq();

				// Repeat since circle centers not Mobius invariant
				int cnt=0;
				while (best>.001 && cnt<=5) {
					
					// convert to points in the plane
					int N=T.length-1;
					Complex []pts=new Complex[N+1];
					for (int j=1;j<=N;j++)
						pts[j]=SphericalMath.s_pt_to_plane(T[j]);

					Mobius mob=sphNormalizer(pts,20,false,false);
					if (mob==null) {
						CirclePack.cpb.errMsg("centroid with centers failed, revert to Orick's code");
						return CPI_CP_PackingUtility.normalize(packData);
					}
					
					// else save the results and run again
					saveCircles(mob);
					for (int v=1;v<=packData.nodeCount;v++)
						T[v]=packData.getCenter(v);
					
					best=SphericalMath.getCentroid(T).normSq();
					cnt++;
				}
				return cnt;
			}
			
			// typical: tangency points (since they are Mobius invariant)
			int rslt=0;
			for (int rep=1;rep<=1;rep++) {
				Complex []T=loadTangency();
			
				// convert to points in the plane
				int N=T.length-1;
				Complex []pts=new Complex[N+1];
				for (int j=1;j<=N;j++)
					pts[j]=SphericalMath.s_pt_to_plane(T[j]);

				Mob=sphNormalizer(pts,20,false,false);
				if (Mob==null) {
					CirclePack.cpb.errMsg("centroid normalization failed, revert to Orick's code");
					return CPI_CP_PackingUtility.normalize(packData); 
				}
				rslt=saveCircles(Mob);
			}
			return rslt;
		}
		
		items=flagSegs.elementAt(0);
		String cmd_str="";
		
		// 'experimental' mode?
		if (items!=null && items.size()>0 && 
				((cmd_str=items.get(0)).startsWith("-x")) || cmd_str.startsWith("x")) {
			items.remove(0);
			
			// get character 'c' centers, 't' tangency,
			char c='c';
			int cycles=5;
			try {
				c=items.remove(0).charAt(0);
			} catch(Exception ex) {}
			try {
				cycles=Integer.parseInt(items.remove(0));
			} catch(Exception ex) {}
			
			// initialize
			Mob=new Mobius(); // in some cases we accumulate successive Mobius
			
			// tangency case
			if (c=='t') {
				Complex []T=loadTangency();
				double best=SphericalMath.getCentroid(T).normSq();
				System.out.println("starting tangency centroid: "+best);

				// convert to in the plane
				int N=T.length-1;
				Complex []pts=new Complex[N+1];
				for (int j=1;j<=N;j++)
					pts[j]=SphericalMath.s_pt_to_plane(T[j]);

				// 
				Mob=sphNormalizer(pts,20,false,true);
				if (Mob==null) {
					CirclePack.cpb.errMsg("'sphNormalizer' seems to have failed");
					return 0;
				}

				int ans=saveCircles(Mob);
				T=loadTangency();
				best=SphericalMath.getCentroid(T).normSq();
				System.out.println("ending tangency centroid: "+best);
				return ans;
			}
			
			// circle center case
			else if (c=='c') {
				Complex []T=loadCenters();
				double best=SphericalMath.getCentroid(T).normSq();
				System.out.println("starting center centroid: "+best);
				
				// Repeat since circle centers not Mobius invariant
				int cnt=0;
				while (best>.001 && cnt<=cycles) {
					
					// convert to points in the plane
					int N=T.length-1;
					Complex []pts=new Complex[N+1];
					for (int j=1;j<=N;j++)
						pts[j]=SphericalMath.s_pt_to_plane(T[j]);
					
					Mobius mob=sphNormalizer(pts,20,false,true);
					if (mob==null) {
						System.out.println("centroid with centers failed");
						return 0;
					}
					
					// else save the results and run again
					saveCircles(mob);
					T=loadCenters();
					best=SphericalMath.getCentroid(T).normSq();
					System.out.println("next center centroid: "+best);
					cnt++;
				}

				return saveCircles(null);
			}
		}
		
		// standard command parsing
		else{
			Iterator<Vector<String>> nextFlag = flagSegs.iterator();
			while (nextFlag.hasNext()
					&& (items = nextFlag.next()).size() > 0) {
				if (StringUtil.isFlag(items.elementAt(0))) {
					cmd_str = items.remove(0);

					switch (cmd_str.charAt(1)) {

					// Here's the specific parsing of flag itself
					case 'a': // 'antipodal point from given N pole
					{
						N_pole = NodeLink.grab_one_vert(packData, items
								.remove(0));
						S_pole = packData.antipodal_vert(N_pole);
						if (items.size() > 0) // may still have an
												// E_pole
							// designated
							E_pole = NodeLink.grab_one_vert(packData,
									items.remove(0));
						break;
					}
					case 't': // factor for scaling N/S radii.
					{
						factor = Double.parseDouble(items.get(0));
						break;
					}
					} // end of flag switch
				} // done handling a given flagged segment
				else { // only 'N S' or 'N S E' are possible
					if (items.size() < 2)
						return 0;
					N_pole = NodeLink.grab_one_vert(packData, items
							.remove(0));
					S_pole = NodeLink.grab_one_vert(packData, items
							.remove(0));
					if (items.size() > 0) // may still have an E_pole
						// designated
						E_pole = NodeLink.grab_one_vert(packData, items
								.remove(0));
				}
			} // end of while for data

			Complex Ectr = new Complex(0.0);
			double Erad = 0.0;

			if (N_pole <= 0 || S_pole <= 0)
				return 0;

			if (E_pole > 0) {
				Ectr = packData.getCenter(E_pole);
				Erad = packData.getRadius(E_pole);
			}

			Mob = Mobius.NS_mobius(
				packData.getCenter(N_pole),
				packData.getCenter(S_pole), Ectr,
				packData.getRadius(N_pole),
				packData.getRadius(S_pole), Erad, factor);
			if (Mob.error < Mobius.MOB_TOLER) {
				NodeLink vlist = new NodeLink(packData, "a");
				packData.apply_Mobius(Mob, vlist);
				return 1;
			} 
			else
				return 0;
		}
		return 0;
	}
	
	/**
	 * Load a vector with tangency points based on current radii/centers
	 * @return Complex[]
	 */
	public Complex []loadTangency() {
		if (edgeCount==0)
			edgeCount=setEdgeCount();
		
		// store tangency points
		Complex []ans=new Complex[edgeCount+1];
		int tick=0;
		for (int v=1;v<=packData.nodeCount;v++) {
			Complex z=packData.getCenter(v);
			double rz=packData.getRadius(v);
			int[] flower=packData.getFlower(v);
			for (int j=0;j<(packData.getNum(v)+packData.getBdryFlag(v));j++) {
				int k=flower[j];
				if (k>v) {
					ans[++tick]=SphericalMath.sph_tangency(z,packData.getCenter(k),rz,packData.getRadius(k));
				}
			}
		}
		return ans;
	}

	/**
	 * Load a vector based on current packing centers. 
	 * @return Complex[]
	 */
	public Complex []loadCenters() {

		Complex []ans=new Complex[packData.nodeCount+1];
		for (int v=1;v<=packData.nodeCount;v++) {
			ans[v]=packData.getCenter(v);
		}
		return ans;
	}

	/**
	 * Given vector of points in the plane, return a Mobius transformation that
	 * puts the centroid of their stereo projections to the sphere close to the 
	 * origin in 3-space. Return null on error. Note that the resulting Mobius
	 * is linear (fixes infinity). If 'sPole' is true, then we include a point
	 * located at infinity.
	 * 		
	 * TODO: eucl centers are not projections of spherical
	 *   centers, so may improve by using points that better 
	 *   approximate sph centers.
	 *
	 * @param pts Complex[], plane points
	 * @param cycles int, iterative cycles
	 * @param sPole boolean: true->include a point at south pole (infinity)
	 * @param debug boolean
	 * @return Mobius, null on failure to converge
	 */
	public static Mobius sphNormalizer(Complex[] pts,
			int cycles,boolean sPole,boolean debug) {
			
		double N_TOLER=0.001;
		double []p0 = new double[3];
		double []accP = new double[3];
		p0[0]=accP[0]=1.0;
		double bestsq = SphericalMath.transCentroid(pts,p0,sPole).normSq();
		if (debug)
			System.out.println("starting 'bestsq' = "+String.format("%.6f",bestsq));

		// Nested 'while' loops; after an inner loop, adjustments are applied
		//   to 'pts' and the mobius is accumulated in 'accP'.
		int outercount=0;
		while (bestsq > N_TOLER && outercount < cycles) {
			if (debug)
				System.out.println("outercount "+outercount);
			double delt = 2.0;
			p0[0]=1;
			p0[1]=0.0;
			p0[2]=0.0;

			// inner cycle 
			int count = 0;
			while (bestsq > N_TOLER && count < cycles) {
				int gotOne = 0; // indication: which of 6 ways is best?
				for (int i = 0; i < 3; i++) {
					double holdp0 = p0[i];
					p0[i] = p0[i] + delt;
					double newnorm = SphericalMath.transCentroid(pts, p0,sPole).normSq();
					p0[i] = holdp0; // reset for continued tries
					if (newnorm < bestsq) { // improved
						bestsq = newnorm;
						gotOne = i + 1;
					} 
					else {
						p0[i] = p0[i] - delt;
						newnorm = SphericalMath.transCentroid(pts, p0,sPole).normSq();
						p0[i] = holdp0;
						if (newnorm < bestsq) {
							bestsq = newnorm;
							gotOne = -i - 1;
						}
					}
				}

				// if moving in 6 directions didn't improve, then cut delt
				if (gotOne == 0)
					delt = delt / 2;
				// else success: which change was the best?
				else {
					if (debug)
						System.out.println(" at count " + count + ", bestsq = " + String.format("%.6f", bestsq));

					switch (gotOne) {
					case 1: {
						p0[0] += delt;
						break;
					}
					case 2: {
						p0[1] += delt;
						break;
					}
					case 3: {
						p0[2] += delt;
						break;
					}
					case -1: {
						p0[0] -= delt;
						break;
					}
					case -2: {
						p0[1] -= delt;
						break;
					}
					case -3: {
						p0[2] -= delt;
						break;
					}
					} // end of switch
				}
				count++;
			} // end of inner while

			// check if we're done
			if (bestsq<N_TOLER) {
				// apply new 'p0' to previously accumulated transformations in 'accP'
				accP[0] =p0[0]*accP[0];
				accP[1] =p0[0]*accP[1]+p0[1];
				accP[2] =p0[0]*accP[2]+p0[2];
				if (debug) {
					System.out.println("A, B, C = " + String.format("%.6f", accP[0]) + " " + String.format("%.6f", accP[1])
							+ " " + String.format("%.6f", accP[2]));
					System.out.println("end 'bestsq' = "+String.format("%.6f",bestsq));
				}
				return new Mobius(new Complex(accP[0]), new Complex(accP[1], accP[2]), new Complex(0.0), new Complex(1.0));
			}

			// else, apply the new transformation to 'pts'
			double A=p0[0];
			Complex B=new Complex(p0[1],p0[2]);
			for (int v=1;v<=pts.length-1;v++) {
				pts[v]=new Complex(pts[v].times(A)).add(B);
			}
			// accumulate it in 'accP'
			accP[0] =A*accP[0];
			accP[1] =A*accP[1]+p0[1];
			accP[2] =A*accP[2]+p0[2];

			outercount++;
		} // end outer while
			
		if (debug) {
			System.out.println("A, B, C = " + String.format("%.6f", accP[0]) + " " + String.format("%.6f", accP[1])
					+ " " + String.format("%.6f", accP[2]));
			System.out.println("end 'bestsq' = "+String.format("%.6f",bestsq));
		}
		return new Mobius(new Complex(accP[0]), new Complex(accP[1], accP[2]), new Complex(0.0), new Complex(1.0));
	}

	public int setEdgeCount() {
		int eCount=0;
		for (int v=1;v<=packData.nodeCount;v++) {
			int[] flower=packData.getFaceVerts(v);
			for (int j=0;j<(packData.getNum(v)+packData.getBdryFlag(v));j++) {
				int k=flower[j];
				if (k>v)
					eCount++;
			}
		}
		return eCount;
	}

	/**
	 * Apply Mobius to get new centers/radii, or if M==null, transfer
	 * data temporarily held in cent and rad.
	 * @param M Mobius, or null
	 * @return
	 */
	public int saveCircles(Mobius M) {
		
		if (M==null) 
			return 0;
		
		// apply M to circles to set new centers and radii
		CircleSimple sC=new CircleSimple();
		for (int v=1;v<=packData.nodeCount;v++) {
			Mobius.mobius_of_circle(M,1,packData.getCenter(v),packData.getRadius(v),sC,true);
			packData.setCenter(v,new Complex(sC.center));
			packData.setRadius(v,sC.rad);
		}
		return 1;
	}
	
}
