package math;

import complex.Complex;
import geometry.SphericalMath;

/**
 * For manipulations of 3x3 real matrices 
 *
// NOTE.  The (x,y,z) coordinate system is assumed to be right-handed.
// Coordinate axis rotation matrices are of the form
//   RX =    1       0       0
//           0     cos(t) -sin(t)
//           0     sin(t)  cos(t)
// where t > 0 indicates a counterclockwise rotation in the yz-plane
//   RY =  cos(t)    0     sin(t)
//           0       1       0
//        -sin(t)    0     cos(t)
// where t > 0 indicates a counterclockwise rotation in the zx-plane
//   RZ =  cos(t) -sin(t)    0
//         sin(t)  cos(t)    0
//           0       0       1
// where t > 0 indicates a counterclockwise rotation in the xy-plane.
//
// These are elements of SO(3): they are orthogonal with determinant +1.
//   Inverse is simply the transpose.
*/
    public class Matrix3D {
        public double m00, m01, m02;
        public double m10, m11, m12;
        public double m20, m21, m22;

        private static Matrix3D identityMatrix = 
        	new Matrix3D(1.0,0.0,0.0,0.0,1.0,0.0,0.0,0.0,1.0);

        private static Matrix3D zeroMatrix = 
        	new Matrix3D(0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0);

        // Constructors
        public Matrix3D() { // starts as zero matrix
            this.m00 = 0d; this.m01 = 0d; this.m02 = 0d;
            this.m10 = 0d; this.m11 = 0d; this.m12 = 0d;
            this.m20 = 0d; this.m21 = 0d; this.m22 = 0d;
        }
        
        /**
         * Clone existing matrix; identity on error
         * @param inM
         */
        public Matrix3D(Matrix3D inM) {
        	if (inM==null)
        		inM=new Matrix3D();// identity
            this.m00 = inM.m00; this.m01 = inM.m01; this.m02 = inM.m02;
            this.m10 = inM.m10; this.m11 = inM.m11; this.m12 = inM.m12;
            this.m20 = inM.m20; this.m21 = inM.m21; this.m22 = inM.m22;
        }
        
        /**
         * Matrix from 0 entries
         * @param m00
         * @param m01
         * @param m02
         * @param m10
         * @param m11
         * @param m12
         * @param m20
         * @param m21
         * @param m22
         */
        public Matrix3D(double m00, double m01, double m02,
            double m10, double m11, double m12,
            double m20, double m21, double m22) {
            this.m00 = m00; this.m01 = m01; this.m02 = m02;
            this.m10 = m10; this.m11 = m11; this.m12 = m12;
            this.m20 = m20; this.m21 = m21; this.m22 = m22;
        }

        /**
         * Identity 3x3 real matrix 
         * @return
         */
        public static Matrix3D Identity() {
            return identityMatrix;
        }

        /**
         * Zero 3x3 real matrix
         * @return
         */
        public static Matrix3D Zero() {
          return zeroMatrix;
        }

        /**
         * Transpose of real 3x3 matrix
         * @return new Matrix3D
         */
        public Matrix3D Transpose() {
            return new Matrix3D(m00, m10, m20,
                m01, m11, m21,
                m02, m12, m22);
        }
        
        /**
         * Matrix determined from Euler angles. Product of rotations,
         * each in radian angles, counterclockwise rotation as viewed
         * from the positive end of the axis involved; first apply z-rotation,
         * the x-rotation, then y-rotation.
         * @param xRot, rotation of (y,z) plane
         * @param yRot, rotation of (x,z) plane
         * @param zRot, rotation of (x,y) plane
         * @return Matrix3D=yRot*xRot*zRot
         * (Note: this is different from the original methods of CirclePack,
         * which rotated about y in the opposite direction and used order
         * xRot*yRot*zRot.)
         */
        public static Matrix3D FromEulerAnglesXYZ(double xRot, double yRot, double zRot) {
          //Matrix3D result = new Matrix3D();
            double cs = Math.cos(xRot);
            double sn = Math.sin(xRot);
            Matrix3D xMat = new Matrix3D(1f, 0f, 0f, 0f, cs, -sn, 0, sn, cs);
            cs = Math.cos(yRot);
            sn = Math.sin(yRot);
            Matrix3D yMat = new Matrix3D(cs, 0, sn, 0, 1, 0, -sn, 0, cs);
            cs = Math.cos(zRot);
            sn = Math.sin(zRot);
            Matrix3D zMat = new Matrix3D(cs, -sn, 0, sn, cs, 0, 0, 0, 1);
            return times(yMat,times(xMat,zMat));
        }
        
        /**
         * Build matrix for rigid motion that fixes axis through 
         * spherical point sph_z. The motion is cclw rotation by
         * theta radians in looking toward the origin from sph_z.
         * @param theta double
         * @param fixed Point3D, pt on sphere
         * @return Matrix3D
         */
        public static Matrix3D rigid(double theta,Point3D fixed) {
        	Matrix3D RZ = new Matrix3D(Math.cos(theta),-Math.sin(theta),0.0,
        			Math.sin(theta),Math.cos(theta),0.0,
        			0.0,0.0,1.0);
        	if (fixed.z>.9999) // essentially at north pole
        		return RZ;
        	if (Math.abs(fixed.z-1.0)<.0001) { // essentially at south pole
        		RZ.m01=-1.0*RZ.m01; 
        		RZ.m10=-1.0*RZ.m10;
        		return RZ;
        	}
        	
        	// build new o.n.b {x_axis,y_axis,fixed}
        	Point3D np=new Point3D(0.0,0.0,1.0);
        	Point3D x_axis=Point3D.CrossProduct(np,fixed);
        	x_axis=x_axis.divide(x_axis.norm());
        	Point3D y_axis=Point3D.CrossProduct(fixed,x_axis);
        	y_axis=y_axis.divide(y_axis.norm());
        	
        	// invT maps standard o.n.b to new o.n.b.
        	Matrix3D invT=new Matrix3D(x_axis.x,y_axis.x,fixed.x,
        			x_axis.y,y_axis.y,fixed.y,
        			x_axis.z,y_axis.z,fixed.z);
        	Matrix3D T=invT.Transpose();
        	Matrix3D mat=Matrix3D.times(invT,RZ);
        	mat=Matrix3D.times(mat,T);
        	return mat; // invT*RZ*T
        }
        
    	/**
    	 * Given (theta,phi) point on the sphere, find rigid motion of sphere
    	 * moving it to the north pole. Compute angle and then cross product to
    	 * get axis of rotation.
    	 * @param sph_z Complex (theta,phi)
    	 * @return Mobius (normalized)
    	 */
    	public static Matrix3D rigid2North(Complex sph_z) {
    		
    		// is direction already almost north? return identity
    		if (sph_z.y<.001)
    			return Matrix3D.Identity();
    		
    		// is it almost south? return inversion z->1/z
    		if (Math.abs(sph_z.y-Math.PI)<.001)
    			return new Matrix3D(1.0,0.0,0.0,0.0,-1.0,0.0,0.0,0.0,-1.0);
    		
    		double []vec=SphericalMath.s_pt_to_vec(sph_z);
    		Point3D pt=new Point3D(vec[0],vec[1],vec[2]); // pt.norm();
    		Point3D np=new Point3D(0,0,1.0);
    		double theta=Math.acos(Point3D.DotProduct(pt,np));
    		Point3D fixed=Point3D.CrossProduct(pt,np); // fixed.norm();
    		fixed=fixed.divide(fixed.norm());
    		return rigid(theta,fixed);
    	}    		

        /**
         * Matrix multiplication, Mleft*Mright
         * @param left, right matrices in the matrix product
         * @return Matrix3D=left*right
         */
        public static Matrix3D times(Matrix3D left, Matrix3D right) {
            Matrix3D result = new Matrix3D();
            result.m00 = left.m00 * right.m00 + left.m01 * right.m10 + left.m02 * right.m20;
            result.m01 = left.m00 * right.m01 + left.m01 * right.m11 + left.m02 * right.m21;
            result.m02 = left.m00 * right.m02 + left.m01 * right.m12 + left.m02 * right.m22;

            result.m10 = left.m10 * right.m00 + left.m11 * right.m10 + left.m12 * right.m20;
            result.m11 = left.m10 * right.m01 + left.m11 * right.m11 + left.m12 * right.m21;
            result.m12 = left.m10 * right.m02 + left.m11 * right.m12 + left.m12 * right.m22;

            result.m20 = left.m20 * right.m00 + left.m21 * right.m10 + left.m22 * right.m20;
            result.m21 = left.m20 * right.m01 + left.m21 * right.m11 + left.m22 * right.m21;
            result.m22 = left.m20 * right.m02 + left.m21 * right.m12 + left.m22 * right.m22;
            return result;
        }

        /**
         * matrix times column vector M.v
         * @param vector Point3D (i.e., 3x1)
         * @param matrix Matrix3D
         * @return Point3D, matrix*vector
         */
        public static Point3D times(Point3D vector, Matrix3D matrix) {
          Point3D product = new Point3D(
          matrix.m00 * vector.x + matrix.m01 * vector.y + matrix.m02 * vector.z,
          matrix.m10 * vector.x + matrix.m11 * vector.y + matrix.m12 * vector.z,
          matrix.m20 * vector.x + matrix.m21 * vector.y + matrix.m22 * vector.z);
          return product;
        }

        /**
         * matrix times column vector M.v
         * @param matrix Matrix3D
         * @param vector Point3D (column),
         * @return Point3D, matrix*vector
         */
        public static Point3D times(Matrix3D matrix, Point3D vector) {
          return times(vector, matrix);
        }

        /**
         * matric times scalar
         * @param matrix Matrix3D
         * @param scalar double
         * @return Matrix3D scalar*matrix
         */
        public static Matrix3D times(Matrix3D matrix, double scalar) {
          Matrix3D result = new Matrix3D();
          result.m00 = matrix.m00 * scalar;
          result.m01 = matrix.m01 * scalar;
          result.m02 = matrix.m02 * scalar;
          result.m10 = matrix.m10 * scalar;
          result.m11 = matrix.m11 * scalar;
          result.m12 = matrix.m12 * scalar;
          result.m20 = matrix.m20 * scalar;
          result.m21 = matrix.m21 * scalar;
          result.m22 = matrix.m22 * scalar;
          return result;
        }

        /**
         * matrix addition
         * @param left Matrix3D
         * @param right Matrix3D
         * @return Matrix3D left+right
         */
        public static Matrix3D add(Matrix3D left, Matrix3D right ) {
            Matrix3D result = new Matrix3D();
            result.m00 = left.m00+right.m00;
            result.m01 = left.m01+right.m01;
            result.m02 = left.m02+right.m02;
            result.m10 = left.m10+right.m10;
            result.m11 = left.m11+right.m11;
            result.m12 = left.m12+right.m12;
            result.m20 = left.m20+right.m20;
            result.m21 = left.m21+right.m21;
            result.m22 = left.m22+right.m22;
            return result;
        }

        /**
         * matric subtraction, Mleft-Mright
         * @param left Matrix3D
         * @param right Matrix3D
         * @return Matrix3D left-right
         */
        public static Matrix3D sub( Matrix3D left, Matrix3D right ) {
          Matrix3D result = new Matrix3D();
          result.m00 = left.m00-right.m00;
          result.m01 = left.m01-right.m01;
          result.m02 = left.m02-right.m02;
          result.m10 = left.m10-right.m10;
          result.m11 = left.m11-right.m11;
          result.m12 = left.m12-right.m12;
          result.m20 = left.m20-right.m20;
          result.m21 = left.m21-right.m21;
          result.m22 = left.m22-right.m22;
          return result;
        }

        /**
         * matrix negation
         * @param matrix Matrix3D
         * @return Matrix3D (-1)*matrix
         */
        public static Matrix3D Negate(Matrix3D matrix) {
            Matrix3D result = new Matrix3D();
            result.m00 = -matrix.m00;
            result.m01 = -matrix.m01;
            result.m02 = -matrix.m02;
            result.m10 = -matrix.m10;
            result.m11 = -matrix.m11;
            result.m12 = -matrix.m12;
            result.m20 = -matrix.m20;
            result.m21 = -matrix.m21;
            result.m22 = -matrix.m22;
            return result;
        }

        /**
         * Are these matrices equal?
         * @param left Matrix3D
         * @param right Matrix3D
         * @return boolean
         */
        public static boolean equals(Matrix3D left, Matrix3D right) {
            if (
                left.m00 == right.m00 && left.m01 == right.m01 && left.m02 == right.m02 &&
                left.m10 == right.m10 && left.m11 == right.m11 && left.m12 == right.m12 &&
                left.m20 == right.m20 && left.m21 == right.m21 && left.m22 == right.m22) {
                return true;
            }
            return false;
        }

        /**
         * Returns the determinant of 'this'
         * @return double
         */
        public double Determinant() {
          double cofactor00 = m11 * m22 - m12 * m21;
          double cofactor10 = m12 * m20 - m10 * m22;
          double cofactor20 = m10 * m21 - m11 * m20;
          double result =
              m00 * cofactor00 +
              m01 * cofactor10 +
              m02 * cofactor20;
              return result;
          }

        /**
         * @return String, six entries separated by spaces
         */
        public String toString() {
            StringBuilder builder = new StringBuilder("");
            builder.append(m00+" "+m01+" "+m02+"    "+ m10+" "+m11+" "+m12+"    "+m20+" "+m21+" "+m22);
            return builder.toString();
        }
        
        /**
         * Returns inverse of a matrix
         * @param m Matrix3D
         * @return Matrix3D inverse of m
         */
        public static Matrix3D Inverse(Matrix3D m) {
          Matrix3D matrix = new Matrix3D();
          double d = m.Determinant();
          // TODO: if(MathUtil.isSmall(d))
          // complain loudly
          matrix.m00=(m.m11*m.m22-m.m12*m.m21)/d;
          matrix.m01=-(m.m01*m.m22-m.m02*m.m21)/d;
          matrix.m02=(m.m01*m.m12-m.m02*m.m11)/d;
          matrix.m10=-(m.m10*m.m22-m.m12*m.m20)/d;
          matrix.m11=(m.m00*m.m22-m.m02*m.m20)/d;
          matrix.m12=-(m.m00*m.m12-m.m02*m.m10)/d;
          matrix.m20=(m.m10*m.m21-m.m11*m.m20)/d;
          matrix.m21=-(m.m00*m.m21-m.m01*m.m20)/d;
          matrix.m22=(m.m00*m.m11-m.m01*m.m10)/d;
          return matrix;
        }
        
        /**
         * Return l^2 norm of matrix (i.e., as though a 9-vector)
         * @return double
         */
        public double norm() {
         return Math.sqrt(m00*m00+m01*m01+m02*m02+m10*m10+m11*m11+m12*m12+m20*m20+m21*m21+m22*m22);
       }
        
       /**
        * Return true if 'Matrix3D' has a NaN entry.
        * @param m Matrix3D
        * @return boolean: true if NaN, else false
        */
        public static boolean isNaN(Matrix3D m) {
    	   if (Double.isNaN(m.m00) || Double.isNaN(m.m01) || Double.isNaN(m.m02) ||
    			   Double.isNaN(m.m10) || Double.isNaN(m.m11) || Double.isNaN(m.m12) ||
    			   Double.isNaN(m.m20) || Double.isNaN(m.m21) || Double.isNaN(m.m22)) 
    		   return true;
    	   return false;
       }
       
}
