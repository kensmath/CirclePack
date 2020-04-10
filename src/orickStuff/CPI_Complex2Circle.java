package orickStuff;

public class CPI_Complex2Circle {
    CPI_Complex2 c;
    double r;

    CPI_Complex2Circle() { c = new CPI_Complex2(); r = 0; }
    CPI_Complex2Circle(CPI_Complex2Circle C) { c = C.c; r = C.r; }
    CPI_Complex2Circle(CPI_Complex2 cent, double rad) { c = new CPI_Complex2(cent); r = rad; }
    CPI_Complex2Circle(double x, double y, double rad) { c = new CPI_Complex2(x,y); r = rad; }

    CPI_Complex2Circle set(CPI_Complex2Circle C){c = C.c; r = C.r; return this;}
    CPI_Complex2Circle set(CPI_Complex2 cent, double rad) { c.set(cent); r = rad; return this; }
    CPI_Complex2Circle set(double x, double y, double rad) { c.set(x,y); r = rad; return this; }
    

    CPI_Complex2Triangle getTriangle() { 
	CPI_Complex2Triangle T = new CPI_Complex2Triangle();
	T.p[0].set(c); T.p[0].add(r);
	T.p[2].set(c); T.p[2].add(-r);
	T.p[1].set(c); T.p[1].add(0.0, -r);
	return T;
    }
    
    CPI_Complex2Circle mobius(CPI_Complex2 a) {
	CPI_Complex2Circle C;
	C=getTriangle().mobius(a).getCircle();
	set(C);
	return this;
    }

    CPI_Ball3Sector Ball3Project() {
	CPI_Ball3Triangle BT = new CPI_Ball3Triangle(getTriangle());
	return BT.getSector();
    }
    
    void psplot() {
	System.out.printf(" %f %f %f 0 360 arc stroke\n", c.re, c.im, r);
    }

    void print() {
	System.out.printf(" %f %f %f \n", c.re, c.im, r);
    }

};