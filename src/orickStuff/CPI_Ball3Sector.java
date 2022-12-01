package orickStuff;

public class CPI_Ball3Sector {
    CPI_Vector3 c;
    double r; // in multiples of Math.PI

    CPI_Ball3Sector() { c = new CPI_Vector3(0.0, 0.0, 1.0); r = 0; }
    CPI_Ball3Sector(CPI_Ball3Sector S) { c = S.c; r = S.r; }
//    CPI_Ball3Sector(CPI_Vector3 V, double rad) { c = new CPI_Vector3(V); c.normalize(); r = rad; }
    CPI_Ball3Sector(double x, double y, double z, double rad) { c = new CPI_Vector3(x,y,z); c.normalize(); r = rad; }
    CPI_Ball3Sector(CPI_Complex2Circle C){set(C);};

    CPI_Ball3Sector set(CPI_Ball3Sector S){c = S.c; r = S.r; return this;}
//    CPI_Ball3Sector set(CPI_Vector3 V, double rad) { c.set(V); c.normalize(); r = rad; return this; }
    CPI_Ball3Sector set(double x, double y, double z, double rad) { c.set(x,y,z); c.normalize(); r = rad; return this; }
    CPI_Ball3Sector set(CPI_Complex2Circle C) { 
    	CPI_Ball3Triangle BT = new CPI_Ball3Triangle(C.getTriangle());

    	BT.p[0].print();
    	BT.p[1].print();
    	BT.p[2].print();

    	set(BT.getSector()); 
    	return this; 
    }

	CPI_Ball3Triangle getTriangle() {
		CPI_Vector3 U = new CPI_Vector3(c.z, -c.x, c.y); // will be orthogonal
															// since unit
															// vectors
		U.mod(c).normalize();
		CPI_Vector3 V = new CPI_Vector3(c);
		V.cross(U);

		U.mul(Math.sin(Math.PI * r));
		V.mul(Math.sin(Math.PI * r));
		CPI_Vector3 Z = new CPI_Vector3(c);
		Z.mul(Math.cos(Math.PI * r));

		CPI_Ball3Triangle T = new CPI_Ball3Triangle();
		T.p[0].set(Z).add(U);
		T.p[1].set(Z).add(V);
		T.p[2].set(Z).sub(U);
		return T;
	}
    
	CPI_Ball3Sector mobius(CPI_Vector3 a) {
		CPI_Ball3Sector S;
		S = getTriangle().mobius(a).getSector();
		set(S);
		return this;
	}

	void psplot() {

		CPI_Vector3 Z = new CPI_Vector3(c);
		Z.mul(Math.cos(r * Math.PI));

		double color = CPI_Ball3View.fgcolor;
		if (c.dot(CPI_Ball3View.N) < 0)
			color = CPI_Ball3View.bgcolor;
		System.out.printf("gsave\n%f setgray\n", color);
		System.out.printf("%f rotate\n",
				Math.atan2(c.dot(CPI_Ball3View.V), c.dot(CPI_Ball3View.U)) * 180.0 / Math.PI);
		System.out.printf(
				"%f %f translate\n",
				Math.sqrt(Z.dot(CPI_Ball3View.U) * Z.dot(CPI_Ball3View.U) + Z.dot(CPI_Ball3View.V)
						* Z.dot(CPI_Ball3View.V)), 0.0);
		System.out.printf("%f %f scale\n", Math.cos(c.arg(CPI_Ball3View.N)), 1.0);

		double rad;
		rad = Math.sin(r * Math.PI);

		System.out.printf("0 0 %f 0 360 arc stroke\n", rad);
		System.out.printf("grestore\n");

	}

//	void print() {
//		System.out.printf(" %f %f %f %f \n", c.x, c.y, c.z, r);
//	}

};