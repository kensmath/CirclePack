package orickStuff;

public class CPI_Ball3Triangle {
	CPI_Vector3 p[];

	void alloc() {
		p = new CPI_Vector3[3];
		p[0] = new CPI_Vector3();
		p[1] = new CPI_Vector3();
		p[2] = new CPI_Vector3();
	}

	CPI_Ball3Triangle() {
		alloc();
	}

	CPI_Ball3Triangle(CPI_Ball3Sector S) {
		alloc();
		set(S.getTriangle());
	}

	CPI_Ball3Triangle(CPI_Complex2Triangle T) {
		alloc();
		p[0].set(T.p[0].sphereProject());
		p[1].set(T.p[1].sphereProject());
		p[2].set(T.p[2].sphereProject());
	}

	CPI_Ball3Triangle set(CPI_Ball3Triangle T) {
		p[0] = T.p[0];
		p[1] = T.p[1];
		p[2] = T.p[2];
		return this;
	}

	CPI_Ball3Sector getSector() {
		CPI_Ball3Sector D = new CPI_Ball3Sector();
		CPI_Vector3 B = new CPI_Vector3(p[1]);
		CPI_Vector3 C = new CPI_Vector3(p[2]);
		B.sub(p[0]);
		C.sub(p[0]);
		CPI_Vector3 N = new CPI_Vector3(B);
		N.cross(C).normalize();
		D.c.set(N);
		D.r = N.arg(p[0]) / Math.PI;
		return D;
	}

	/**
	 * return the unit normal vector to the triangle
	 * @return
	 */
	CPI_Vector3 normal() {
		CPI_Vector3 B = new CPI_Vector3(p[1]);
		CPI_Vector3 C = new CPI_Vector3(p[2]);
		B.sub(p[0]);
		C.sub(p[0]);
		B.cross(C).normalize();
		return B;
	}

	/** 
	 * mobius(a) moves a to the origin; apply this 
	 * to each vertex of the triangle
	 * @param a CPI_Vector3
	 * @return
	 */
	CPI_Ball3Triangle mobius(CPI_Vector3 a) {
		p[0].mobius(a);
		p[1].mobius(a);
		p[2].mobius(a);
		return this;
	}

	void psplot() {

		double color = CPI_Ball3View.fgcolor;
		if (normal().dot(CPI_Ball3View.N) < 0)
			color = CPI_Ball3View.bgcolor;
		System.out.printf("gsave\n%f setgray\n", color);

		System.out.printf("%f %f moveto\n", p[0].dot(CPI_Ball3View.U), p[0].dot(CPI_Ball3View.V));
		System.out.printf("%f %f lineto\n", p[1].dot(CPI_Ball3View.U), p[1].dot(CPI_Ball3View.V));
		System.out.printf("%f %f lineto\n", p[2].dot(CPI_Ball3View.U), p[2].dot(CPI_Ball3View.V));
		System.out.printf("%f %f lineto stroke\n", p[0].dot(CPI_Ball3View.U),
				p[0].dot(CPI_Ball3View.V));
		System.out.printf("grestore\n");
	}

	void print() {
		System.out.printf("%f %f %f \t%f %f %f \t%f %f %f\n", p[0].x, p[0].y,
				p[0].z, p[1].x, p[1].y, p[1].z, p[2].x, p[2].y, p[2].z);
	}
}
