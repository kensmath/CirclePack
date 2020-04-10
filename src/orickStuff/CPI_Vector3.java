package orickStuff;

/**
 * Converted from Orick's C++ code. This should be replaced 
 * by 'Point3' objects. For now I will comment out some
 * things never called.
 * @author kens
 *
 */
public class CPI_Vector3 {

	double x, y, z;

	// constructors
	CPI_Vector3() {  // gotit
		x = y = z = 0.0;
	}

	CPI_Vector3(double a, double b, double c) {  // gotit
		x = a;
		y = b;
		z = c;
	}

	CPI_Vector3(CPI_Vector3 v) {  // gotit
		x = v.x;
		y = v.y;
		z = v.z;
	}

/*	CPI_Vector3(CPI_Complex2 u) {
		if (Double.isNaN(u.re + u.im)) {
			x = y = 0.0;
			z = 1.0;
		} else {
			double R;
			R = u.abs();
			x = 2 * u.re / (R * R + 1);
			y = 2 * u.im / (R * R + 1);
			z = (R * R - 1) / (R * R + 1);
		}
	}
*/
	
	CPI_Vector3 set(double a, double b, double c) {  // gotit
		x = a;
		y = b;
		z = c;
		return this;
	}

/*	CPI_Vector3 set(CPI_Complex2 u) {
		if (Double.isNaN(u.re + u.im)) {
			x = y = 0.0;
			z = 1.0;
		} else {
			double R;
			R = u.abs();
			x = 2 * u.re / (R * R + 1);
			y = 2 * u.im / (R * R + 1);
			z = (R * R - 1) / (R * R + 1);
		}
		return this;
	}
*/

	CPI_Vector3 set(CPI_Vector3 v) {  // gotit
		x = v.x;
		y = v.y;
		z = v.z;
		return this;
	}

	CPI_Vector3 normalize() {  // gotit
		double d;
		d = abs();
		x /= d;
		y /= d;
		z /= d;
		return this;
	}

/*	double[] vec() {
		double[] v = new double[3];
		v[0] = x;
		v[1] = y;
		v[2] = z;
		return v;
	}

	CPI_Vector3 neg() {
		x = -x;acos
		y = -y;
		z = -z;
		return this;
	}

	CPI_Vector3 add(double m) {
		x += m;
		return this;
	}

	CPI_Vector3 sub(double m) {
		x -= m;
		return this;
	}

	CPI_Vector3 add(double a, double b, double c) {
		x += a;
		y += b;
		z += c;
		return this;
	}

	CPI_Vector3 sub(double a, double b, double c) {
		x -= a;
		y -= b;
		z -= c;
		return this;
	}
*/

	CPI_Vector3 mul(double m) {  // gotit
		x *= m;
		y *= m;
		z *= m;
		return this;
	}

	CPI_Vector3 div(double m) {  // gotit
		x /= m;
		y /= m;
		z /= m;
		return this;
	}

	CPI_Vector3 add(CPI_Vector3 v) {  // gotit
		x += v.x;
		y += v.y;
		z += v.z;
		return this;
	}

	CPI_Vector3 sub(CPI_Vector3 v) { // gotit
		x -= v.x;
		y -= v.y;
		z -= v.z;
		return this;
	}

	CPI_Vector3 cross(CPI_Vector3 v) {  // gotit
		CPI_Vector3 w = new CPI_Vector3();
		w.x = y * v.z - z * v.y;
		w.y = -(x * v.z - z * v.x);
		w.z = x * v.y - y * v.x;
		set(w);
		return this;
	}

	double dot(CPI_Vector3 v) {  // gotit
		double d;
		d = x * v.x + y * v.y + z * v.z;
		return d;
	}

	/**
	 * Length of remaining vector after projection in
	 * direction of v is removed.
	 * @param v CPI_Vector3
	 * @return double
	 */
	double rem(CPI_Vector3 v) { // gotit
		double d;
		d = dot(v) / v.abs();
		return Math.sqrt(x * x + y * y + z * z - d * d);
	}

	/**
	 * Return this vector minus projection in direction of v
	 * @param v CPI_Vector3
	 * @return this
	 */
	CPI_Vector3 mod(CPI_Vector3 v) { // gotit
		double d;
		CPI_Vector3 V = new CPI_Vector3(v);
		V.normalize();
		d = dot(V);
		V.mul(d);
		sub(V);
		return (this);
	}

	/**
	 * Given point a in the sphere, apply the Mobius transformation
	 * of the sphere that maps a to the origin while fixing the endpoints
	 * of the vector through a.
	 * @param a CPI_Vector3
	 * @return this
	 */
	CPI_Vector3 mobius(CPI_Vector3 a) { // 'move3Dpoint2origin' of Mobius.java does this
		CPI_Vector3 V = new CPI_Vector3();
		double th, phi, lambda;
		CPI_Vector3 N = new CPI_Vector3(a);
		N.normalize();
		if (Double.isNaN(N.norminfty()))
			return this;

		th = arg(N);

		lambda = a.abs();
		CPI_Complex2 z = new CPI_Complex2(Math.cos(th), Math.sin(th));
		z.mobius(lambda);
		phi = z.arg();

		CPI_Vector3 M = new CPI_Vector3(this);
		M.mod(N).normalize();
		N.mul(Math.cos(phi));
		M.mul(Math.sin(phi));
		V.set(N).add(M);
		set(V);

		return this;
	}

	double norminfty() { // replace with 'L1Norm' in Point3D
		return (Math.abs(x) + Math.abs(y) + Math.abs(z));
	}

	CPI_Vector3 perp() { // gotit
		// produce a perp vector; default is <1 0 0>
		CPI_Vector3 U = new CPI_Vector3(z, -x, y); // will be orthogonal since
													// unit vectors
//		print();
//		U.print();
		U.mod(this);
//		U.print();
		normalize();
//		U.print();
		if (Double.isNaN(norminfty()))
			U.set(1.0, 0.0, 0.0);
//		U.print();
		return U;
	}

	double abs() { // convert to 'norm' of Point3D
		return Math.sqrt(x * x + y * y + z * z);
	};

/*	double arg() {
		double a;
		a = Math.atan2(x, y);
		return a;
	};
*/	

	/**
	 * angle from v to 'this' in the plane they form.
	 * @param v
	 * @return
	 */
	double arg(CPI_Vector3 v) { // use 'intersectAng' of Point3D
		double a;
		a = Math.atan2(rem(v), dot(v) / v.abs());
		return a;
	};

	void print() {
		// System.out.printf("%f %f %f \n", x, y, z);
	}

/*	void psplot() {
		CPI_Ball3View view = new CPI_Ball3View();
		System.out.printf("new path 0 0 moveto %f %f lineto stroke\n",
				dot(view.U), dot(view.V));

	}
*/

}
