package microLattice;

import java.util.Vector;

import allMains.CPBase;
import allMains.CirclePack;
import complex.Complex;
import geometry.EuclMath;
import input.CommandStrParser;
import packing.PackData;
import packing.RData;
import util.PathUtil;
import util.StringUtil;

/**
 * This code was started by John Bowers in our work on 3D printing.
 * It does the "field-based smoothing" step in our infill pattern work,
 * which modifies an existing packing by moving centers in response
 * to some combination of the usual packing criterion and the effects
 * of an ambient field, such as a stress field. 
 * 
 * This normally is created in concert with a 'MicroGrid' PackExtender,
 * 'parentMG' because of the access the ambient field and the path.
 * Commands may then be sent via the 'MicrGrid' parent. 
 * However, even then, it is associated with a separate packing
 * obtained in MicroGrid's "tailored sectioning" step.
 * 
 * If 'parentMG' is null, then the packing must be euclidean.
 * Intensity defaults to 1.0 everywhere, the path defaults to 
 * 'CPBase.ClosedPath' or to unit circle, min/max radii are
 * taken from the packing.
 * 
 * @author kstephe2, June 2020
 *
 */
public class Smoother {
	
	public final double MIN_INV_DIST=0.5;
	public final double MAX_INV_DIST=3.0;
	public final double TARGET_INV_DIST=1.0;
	
	MicroGrid parentMG;     // parent MicroGrid, null for standalone
	PackData myPackData;    // access to parent packing
	RData[] newRData;       // adjusted data; can swap out to visualize changes
	Vector<Complex> myPolygon;   // polygonal

	double minRadius;       // slightly smaller than smallest radius (of parentMG)
	double maxRadius;       // slightly larger than largest
	double min_inv_dist;
	double max_inv_dist;
	Complex[] ctr_force;    // resultant force at each vert
	Complex[] ctr_vel;      // velocity vector at each vert ?? not used
	double[] rad_vel;       // ?? not used
	double[] rad_force;     // radius pressure at each vert
	Complex[] newCenters;   // as adjusted
	double[] newRadii;      // as adjusted
	
	// parameters/settings
	int bdryMode;          // 0 = frozen bdry centers -- default
						   // 1 = move bdry centers to polygon
						   // 2 = bdry circles tangent to polygon
	double radPressure;    // in [0,1]. Pressure for radius to move toward
						   // value based on intensity
	double speed;          // positive, typically small for conservative adjustments
	
	// Constructor
	public Smoother(PackData p,MicroGrid mg) {
		myPackData=p;
		parentMG=mg;
		min_inv_dist=MIN_INV_DIST;
		max_inv_dist=MAX_INV_DIST;
		ctr_force=new Complex[myPackData.nodeCount+1];
		
		// set the polygon; should be a single component
		if (CPBase.ClosedPath!=null) {
			Vector<Vector<Complex>> comps=PathUtil.gpPolygon(CPBase.ClosedPath);
			myPolygon=comps.get(0);
		}
		else { // unit circle by default
			myPolygon=new Vector<Complex>();
			double incr=Math.PI/90.0;
			for (int j=0;j<180;j++) {
				double ang=(double)(j*incr);
				myPolygon.add(new Complex(Math.cos(ang),Math.sin(ang)));
			}
		}
		
		// TODO: default parameters?
		bdryMode=0; // default to frozen bdry
		radPressure=0.05;
		speed=0.1;
		
		if (parentMG!=null) {
			minRadius=.75*parentMG.stepRad[1];
			maxRadius=1.25*parentMG.stepRad[parentMG.levelCount];
		}
		else {
			minRadius=Double.MAX_VALUE;
			maxRadius=0.0;
			for (int v=1;v<=myPackData.nodeCount;v++) {
				double rad=myPackData.getRadius(v);
				minRadius=(rad<minRadius) ? rad:minRadius;
				maxRadius=(rad>maxRadius) ? rad:maxRadius;
			}
			// make a little room
			minRadius *=0.75;
			maxRadius *=1.25;
		}
		
		// copy rData
		newRData=new RData[myPackData.rData.length+1];
		for (int v=1;v<=myPackData.nodeCount;v++) 
			newRData[v]=myPackData.rData[v].clone();

		// allocate space, insert data
		newRadii=new double[myPackData.nodeCount+1];
		newCenters=new Complex[myPackData.nodeCount+1];
		for (int v=1;v<=myPackData.nodeCount;v++) {
			newRadii[v]=myPackData.getRadius(v);
			newCenters[v]=myPackData.getCenter(v);
		}
	}
	
	/**
	 * This actually iterates the radius/center adjustments some
	 * given number of times using the current parameters.
	 * There are two modes (for now): 
	 *  * mode=1 (default): first compute all the adjustments based 
	 *    on current data, then apply all the adjustments.
	 *  * mode=2: Compute and apply one after the other 
	 * @param cycles
	 * @param t
	 * @param mode int,
	 */
	public int computeCycles(int cycles,int mode) {
		int count=0;
		for (int i=1;i<=cycles;i++) {
			if (mode==1) { // default
				// first compute all velocities, forces
				for (int v=1;v<=myPackData.nodeCount;v++)
					calcForce(v);
				// then apply
				for (int v=1;v<=myPackData.nodeCount;v++)
					applyForce(v,speed);
			}
			else if (mode==2) {
				for (int v=1;v<=myPackData.nodeCount;v++) {
					calcForce(v);
					applyForce(v,speed);
				}
			}
			count++;
		} // end of loop
		
		// put the info into the alternate RData
		for (int v=1;v<=myPackData.nodeCount;v++) {
			newRData[v].rad=newRadii[v];
			newRData[v].center=new Complex(newCenters[v]);
		}
		
		return count;
	}

	/** 
	 * Compute using default mode=1
	 * @param cycles
	 * @return
	 */
	public int computeCycles(int cycles) {
		return computeCycles(cycles,1);
	}

	/**
	 * display on the screen for myPackData using normal display flags
	 * @param flagSegs Vector<Vector<String>>
	 * @return int result for execution call
	 */
	public int dispNewData(Vector<Vector<String>> flagSegs) {
		RData[] holdRData=myPackData.rData;
		myPackData.rData=newRData;
		StringBuilder strbld=new StringBuilder("disp ");
		strbld.append(StringUtil.reconstitute(flagSegs));
		int ans=CommandStrParser.jexecute(myPackData,strbld.toString());
		
		// restore original data
		myPackData.rData=holdRData;
		return ans;
	}
	
	/**
	 * Replace 'myPackData.rdata' by 'newRData', which has newly
	 * computed radii and centers
	 * @return 1
	 */
	public int acceptNewData() {
		myPackData.rData=newRData;
		return 1;
	}
	
	/**
	 * TODO: I don't understand what John's code in 'ForceGraphCirclesWithPolygon'
	 * code does here, have to revisit this.
	 * compute 'ctr_force' and 'rad_force' for vertex v
	 * @param v int 
	 */
	public void calcForce(int v) {
		
		PackData p=myPackData;
		Complex F=new Complex(0.0);
		double radF=0.0;
		int N=0;
		int num=p.getNum(v)+p.getBdryFlag(v);
		int[] flower=p.kData[v].flower;
		for (int j=0;j<num;j++) {
			int w=flower[j];
			double inv_d=EuclMath.inv_dist(newCenters[v],newCenters[w],
					newRadii[v],newRadii[w]);
			if ((p.isBdry(v) && bdryMode>0) || inv_d<min_inv_dist || inv_d>max_inv_dist) {
				double x=TARGET_INV_DIST-inv_d; // currently working with tangency
				double k=1.0; // ?? for future use?
				Complex uvec=newCenters[w].minus(newCenters[w]);
				double uvecabs=uvec.abs();
				F.add(uvec.times(k*x/uvecabs));
				radF =+ k*(uvecabs-newRadii[v]-newRadii[w]);
				N++;
			}
		}

		if (p.isBdry(v) && bdryMode>0) { 
			Complex bdryPt=PathUtil.getClosestPoint(newCenters[v],myPolygon);
			Complex vec=bdryPt.minus(newCenters[v]);
			F.add(vec);
			radF += (vec.abs()-newRadii[v]);
			N++;
		}
//		else { // ??? doesn't seem to be used
//			double desiredRad=parentMG.getRadius(newCenters[v]);
//		}
		ctr_force[v]=F;
		rad_force[v]=radF/N;
	}

	/**
	 * Use the force data to compute 'newRadii', 'newCenters'
	 * @param v int
	 * @param t double
	 */
	public void applyForce(int v,double t) {
		newRadii[v] += rad_force[v]*t;
		if (newRadii[v]<=minRadius)
			newRadii[v]=minRadius;
		else if (newRadii[v]>=maxRadius)
			newRadii[v]=maxRadius;
		newCenters[v]=newCenters[v].add(ctr_force[v].times(t));
	}
	
	/**
	 * What happens to bdry circles? mode=0, freeze in place; mode=1, move to put
	 * centers on the polygon; mode=2, make tangent to the polygon. Default is 0.
	 * @param mode int
	 * @return 1
	 */
	public int setBdryMode(int mode) {
		bdryMode=mode;
		return 1;
	}

	/**
	 * Set the 'radPressure' in [0,1]. Towards 1 means more weight
	 * on matching radius to the value based on intensity.
	 * @param b double
	 * @return 1 on success, else 0
	 */
	public int setRadPressure(double b) {
		if (b>=0 && b<=1) {
			radPressure=b;
			return 1;
		}
		return 0;
	}

	/**
	 * Set the 'speed', normally small in (0,1]. Larger means more
	 * aggressive adjustments.
	 * @param s double
	 * @return 1 on success, else 0
	 */
	public int setSpeed(double s) {
		if (s>0 && s<=1) {
			speed=s;
			return 1;
		}
		return 0;
	}
	
	/**
	 * kill this smoother
	 * @return int, 0 on failure
	 */
	public int exit() {
		if (parentMG!=null)
			return parentMG.cmdParser("smoother -x",null);
		if (myPackData!=null && myPackData.smoother==this) {
			myPackData.smoother=null;
			return 1;
		}
		CirclePack.cpb.errMsg("failed to exit 'smoother'");
		return 0;
	}

	/**
	 * Reset new rad/centers to values in the packing
	 * @return 1
	 */
	public int reset() { 
		newRData=new RData[myPackData.rData.length+1];
		for (int v=1;v<=myPackData.nodeCount;v++) 
			newRData[v]=myPackData.rData[v].clone();
		return 1;
	}
	
}
