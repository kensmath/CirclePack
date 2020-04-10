package orickStuff;

public class CPI_Complex2Triangle {
    CPI_Complex2  p[]  = new CPI_Complex2[3];

    void alloc() {// p = new CPI_Complex2[3]; 
    p[0] = new CPI_Complex2(); p[1] = new CPI_Complex2(); p[2] = new CPI_Complex2();}
    CPI_Complex2Triangle() {
	alloc();
	p[0].set(1.0, 0.0); p[1].set(0.0, 1.0); p[2].set(0.0, 0.0);
    }
    CPI_Complex2Triangle(CPI_Complex2Circle C) { alloc(); set(C.getTriangle()); }
    CPI_Complex2Triangle(CPI_Complex2Triangle T) { alloc(); set(T);}
    CPI_Complex2Triangle(CPI_Complex2 A, CPI_Complex2 B, CPI_Complex2 C) { alloc(); set(A,B,C);}
    
    CPI_Complex2Triangle set(CPI_Complex2Triangle T) { p[0] = T.p[0]; p[1] = T.p[1]; p[2] = T.p[2]; return this; }
    CPI_Complex2Triangle set(CPI_Complex2 A, CPI_Complex2 B, CPI_Complex2 C) { p[0] = A; p[1] = B; p[2]=C; return this; }

    CPI_Complex2[] getSides(){
	CPI_Complex2[] side = new CPI_Complex2[3];
	side[0] = new CPI_Complex2(p[2]); side[0].sub(p[1]);
	side[1] = new CPI_Complex2(p[0]); side[1].sub(p[2]);
	side[2] = new CPI_Complex2(p[1]); side[2].sub(p[0]);
	return side;
    }

    double[] getLengths() {
	/** CPI_Complex2[] getLengths()
	 ** Get lengths of opposite edges	   
	 */
	CPI_Complex2[] side = getSides();
	double[] len = new double[3];
	len[0] = side[0].abs();
	len[1] = side[1].abs();
	len[2] = side[2].abs();
	return len;
    }

    CPI_Complex2Circle getIncircle(){
	CPI_Complex2Circle C = new CPI_Complex2Circle();
	CPI_Complex2 z = new CPI_Complex2(0,0);
	double[] len = getLengths();

	double sum;
	sum = len[0] + len[1] + len[2];
	C.c.set(0,0);
	C.c.add(z.set(p[0]).mul(len[0]));
	C.c.add(z.set(p[1]).mul(len[1]));
	C.c.add(z.set(p[2]).mul(len[2]));
	C.c.div(sum);

	double s = sum / 2.0;
	double t;
	t = Math.abs((s-len[0])*(s-len[1])*(s-len[2])/s);
	C.r = Math.sqrt(t);
	return C;
    }

    double[] getLocalRadii( double[] len ){
	double[] r = new double[3];
	r[0] = (len[1] + len[2] - len[0])/2.0;
	r[1] = (len[2] + len[0] - len[1])/2.0;
	r[2] = (len[0] + len[1] - len[2])/2.0;
	return len;
    }
    double[] getLocalAngles(){
	double[] th= new double[3];
	CPI_Complex2[] side = getSides();
	CPI_Complex2 t = new CPI_Complex2();
	th[0] = t.set(side[1]).neg().div(side[2]).arg();
	th[1] = t.set(side[2]).neg().div(side[0]).arg();
	th[2] = t.set(side[0]).neg().div(side[1]).arg();
	
	return th;
    }
    
    

    CPI_Complex2Circle getCircle() {
	CPI_Complex2Circle D = new CPI_Complex2Circle();
	double K, a, b, c, s, R, wa, wb, wc, w;
	CPI_Complex2 A = new CPI_Complex2(p[0]);
	CPI_Complex2 B = new CPI_Complex2(p[1]);
	CPI_Complex2 C = new CPI_Complex2(p[2]);

	A.sub(p[1]);
	B.sub(p[2]);
	C.sub(p[0]);
	a = A.abs();
	b = B.abs();
	c = C.abs();
	
	s = (a+b+c)/2.0;
	K = Math.sqrt(s*(s-a)*(s-b)*(s-c));
	if (Double.isNaN(K)) K=0;
	R=a*b*c/(4*K);

	wa = a*a*(-a*a+b*b+c*c);
	wb = b*b*(+a*a-b*b+c*c);
	wc = c*c*(+a*a+b*b-c*c);
	w = wa+wb+wc;
	
	A.set(p[0]).mul(wb);
	B.set(p[1]).mul(wc);
	C.set(p[2]).mul(wa);

	A.add(B).add(C).div(w);
	D.c.set(A);
	D.r = R;
	return D;
    }

    CPI_Complex2Triangle mobius(CPI_Complex2 a) {
	p[0].mobius(a);
	p[1].mobius(a);
	p[2].mobius(a);
	return this;
    }

    void psplot() {
	System.out.printf("%f %f moveto\n", p[0].re, p[0].im );
	System.out.printf("%f %f lineto\n", p[1].re, p[1].im );
	System.out.printf("%f %f lineto\n", p[2].re, p[2].im );
	System.out.printf("%f %f lineto stroke\n", p[0].re, p[0].im );
    }
    void print() {
	System.out.printf("%f %f %f %f %f %f\n",  p[0].re, p[0].im, p[1].re, p[1].im, p[2].re, p[2].im);
    }

    public static void testIncircleMmain(String[] args) {

	CPI_Complex2Triangle T = new CPI_Complex2Triangle();
	
	T.psplot();
	T.getCircle().psplot();
	T.getIncircle().psplot();



    }


}

