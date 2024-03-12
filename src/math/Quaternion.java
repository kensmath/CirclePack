package math;

import math.group.GroupElement;
import packing.PackData;
import exceptions.DataException;

/**
 * For eventual conversion of some operations (e.g., rotations) to
 * quaternion representations. Here we consider quaterions as a
 * (noncommutative) group under multiplication. We can, of course,
 * also add them.
 * 
 * A rotation in 3-space can be specified by a non-zero "rotation" vector
 * v, where v gives the direction of the axis, and norm(v)= theta is the
 * angle of rotation about the axis (counterclockwise when looking backward
 * along the axis. See "SO(3)" in Wikipedia, for example. 
 * @author kstephe2
 *
 */
public class Quaternion  implements GroupElement {
	
	double q1;
	double q2;
	double q3;
	double q4; // scalar term
	int level; // needed for GroupElements
	
    @SuppressWarnings("unused")
	private static Quaternion multIdentity = 
        	new Quaternion(1.0,0.0,0.0,0.0);

    @SuppressWarnings("unused")
	private static Quaternion addIdentity = 
        	new Quaternion(0.0,0.0,0.0,0.0);

	// Constructor(s)
	public Quaternion() {
		q1=1.0;
		q2=q3=q4=0.0;
		level=1;
	}
	
	public Quaternion(double k1,double k2,double k3, double k4) {
		q1=k1;
		q2=k2;
		q3=k3;
		q4=k4;
		level=1;
	}			
		
	public double norm() {
		return Math.sqrt(q1*q1+q2*q2+q3*q3+q4*q4);
	}

	public void normalize() {
		double len=norm();
		if (len<PackData.TOLER)
			return;
		q1 /= len;
		q2 /= len;
		q3 /= len;
		q4 /= len;
	}

	public double scalarPart() {
		return q1;
	}
	
	/**
	 * The 'vector' part is also called the imaginary part,
	 * namely q2*i + q3*j + q4*k.
	 * @return new Point3D
	 */
	public Point3D vectorPart() {
		return new Point3D(q2,q3,q4);
	}
	
	/**
	 * For "conjugate", change sign of vector part
	 * @return new Quaternion
	 */
	public Quaternion conj() {
		return new Quaternion(q1,-q2,-q3,-q4);
	}
	
	public Quaternion add(Quaternion qtn) {
		return new Quaternion(q1+qtn.q1,q2+qtn.q2,q3+qtn.q3,q4+qtn.q4);
	}

	/**
	 * return 'this' minus qtn
	 * @param qtn Quaternion
	 * @return new Quaternion
	 */
	public Quaternion minus(Quaternion qtn) {
		return new Quaternion(q1-qtn.q1,q2-qtn.q2,q3-qtn.q3,q4-qtn.q4);
	}

	@Override
	/**
	 * multiply 'this' on the left by g
	 */
	public GroupElement lmultby(GroupElement g) {
		Quaternion G=(Quaternion)g;
		return new Quaternion(
				G.q1*q1-G.q2*q2-G.q3*q3-G.q4*q4,
				G.q1*q2+G.q2*q1+G.q3*q4-G.q4*q3,
				G.q1*q3-G.q2*q4+G.q3*q1+G.q4*q2,
				G.q1*q4+G.q2*q3-G.q3*q2+G.q4*q1);
	}

	@Override
	/**
	 * multiply 'this' on right by g
	 */
	public GroupElement rmultby(GroupElement g) {
		Quaternion G=(Quaternion)g;
		return new Quaternion(
				q1*G.q1-q2*G.q2-q3*G.q3-q4*G.q4,
				q1*G.q2+q2*G.q1+q3*G.q4-q4*G.q3,
				q1*G.q3-q2*G.q4+q3*G.q1+q4*G.q2,
				q1*G.q4+q2*G.q3-q3*G.q2+q4*G.q1);
	}

	@Override
	
	/**
	 * return inverse
	 * @return new Quaternion
	 */
	public GroupElement inverse() {
		double ns=norm();
		ns=ns*ns;
		if (ns<PackData.TOLER)
			throw new DataException("quaternion norm is too small to invert");
		return new Quaternion(q1/ns,-q2/ns,-q3/ns,-q4/ns);
	}

	@Override
	/**
	 * Currently 'level' has no meaning for quaternions
	 */
	public void setLevel(int t) {
		// TODO Auto-generated method stub
		level=t;
	}

	@Override
	/**
	 * Currently 'level' has no meaning for quaternions
	 * @return 1
	 */
	public int getLevel() {
		// TODO Auto-generated method stub
		return level;
	}

}
