package orickStuff;

public class CPI_Complex2 {
	double re, im;

	CPI_Complex2() {
		re = im = 0.0;
	}

	CPI_Complex2(CPI_Complex2 c) {
		re = c.re;
		im = c.im;
	}

	CPI_Complex2(double x) {
		re = x;
		im = 0.0;
	}

	CPI_Complex2(double x, double y) {
		re = x;
		im = y;
	}

	CPI_Complex2 normalize() {
		double d;
		d = abs();
		if (d > 0.0) {
			re /= d;
			im /= d;
		}
		return this;
	}

	CPI_Complex2 set(CPI_Complex2 c) {
		re = c.re;
		im = c.im;
		return this;
	};

	CPI_Complex2 set(double x, double y) {
		re = x;
		im = y;
		return this;
	};

	double real() {
		return re;
	}

	double imag() {
		return im;
	}

	CPI_Complex2 neg() {
		re = -re;
		im = -im;
		return this;
	}

	CPI_Complex2 add(double m) {
		re += m;
		return this;
	};

	CPI_Complex2 sub(double m) {
		re -= m;
		return this;
	};

	CPI_Complex2 mul(double m) {
		re *= m;
		im *= m;
		return this;
	};

	CPI_Complex2 div(double m) {
		re /= m;
		im /= m;
		return this;
	};

	CPI_Complex2 add(double a, double b) {
		CPI_Complex2 w = new CPI_Complex2(a, b);
		add(w);
		return this;
	}

	CPI_Complex2 sub(double a, double b) {
		CPI_Complex2 w = new CPI_Complex2(a, b);
		sub(w);
		return this;
	}

	CPI_Complex2 mul(double a, double b) {
		CPI_Complex2 w = new CPI_Complex2(a, b);
		mul(w);
		return this;
	}

	CPI_Complex2 div(double a, double b) {
		CPI_Complex2 w = new CPI_Complex2(a, b);
		div(w);
		return this;
	}

	CPI_Complex2 add(CPI_Complex2 z) {
		re += z.re;
		im += z.im;
		return this;
	};

	CPI_Complex2 sub(CPI_Complex2 z) {
		re -= z.re;
		im -= z.im;
		return this;
	};

	CPI_Complex2 mul(CPI_Complex2 z) {
		CPI_Complex2 w = new CPI_Complex2();
		w.re = re * z.re - im * z.im;
		w.im = re * z.im + im * z.re;
		set(w);
		return this;
	}

	CPI_Complex2 inv() {
		double d;
		d = abs();
		conj();
		div(d * d);
		return this;
	}

	CPI_Complex2 div(CPI_Complex2 z) {
		z.inv();
		mul(z);
		return this;
	}

	double abs() {
		double d;
		d = Math.sqrt(re * re + im * im);
		return d;
	};

	double arg() {
		double a;
		a = Math.atan2(im, re);
		return a;
	};

	CPI_Complex2 conj() {
		im = -im;
		return this;
	};

	CPI_Complex2 pow(int n) {
		double a;
		double r;
		CPI_Complex2 w = new CPI_Complex2(this);
		a = arg();
		r = abs();
		r = Math.pow(r, n);
		a *= n;
		w.re = r * Math.cos(a);
		w.im = r * Math.sin(a);
		set(w);
		return this;
	};

	CPI_Complex2 pow(double x) {
		double a;
		double r;
		CPI_Complex2 w = this;
		a = arg();
		r = abs();
		r = Math.pow(r, x);
		a *= x;
		w.re = r * Math.cos(a);
		w.im = r * Math.sin(a);
		set(w);
		return this;
	};

	/**
	 * Apply z --> (z-lambda)/(1-lambda*z)
	 * @param lambda double
	 * @return this
	 */
	CPI_Complex2 mobius(double lambda) {
		CPI_Complex2 A, B;
		A = new CPI_Complex2(this);
		B = new CPI_Complex2(this);
		A.sub(lambda);
		B.mul(lambda).sub(1.0).neg();
		A.div(B);
		set(A);
		return this;
	};

	/**
	 * Apply z->(z-a)/(1-conj(a)*z)
	 * @param a CPI_Complex2
	 * @return
	 */
	CPI_Complex2 mobius(CPI_Complex2 a) {
		CPI_Complex2 A, B;
		A = new CPI_Complex2(this);
		B = new CPI_Complex2(this);
		A.sub(a);
		B.mul(a.conj()).sub(1.0).neg();
		A.div(B);
		set(A);
		return this;
	};

	CPI_Complex2 mobius(CPI_Complex2 a, CPI_Complex2 b, CPI_Complex2 c,
			CPI_Complex2 d) {
		CPI_Complex2 A;
		CPI_Complex2 C;
		A = new CPI_Complex2(this);
		C = new CPI_Complex2(this);
		A.mul(a);
		C.mul(c);
		A.add(b);
		C.add(d);
		A.div(C);
		set(A);
		return this;
	};

	CPI_Vector3 sphereProject() {
		double x, y, z, r;
		r = abs();
		x = 2 * re / (1 + r * r);
		y = 2 * im / (1 + r * r);
		z = (r * r - 1) / (r * r + 1);
		CPI_Vector3 V = new CPI_Vector3(x, y, z);
		return V;
	}

	void print() {
		System.out.printf("%f %f\n", re, im);
	};

	static void test() {
		System.out.printf("\n\nCPI_Complex2 TESTPROC\n");
		CPI_Complex2 A = new CPI_Complex2(1.0, 2.0);
		A.print();
		A.add(A);
		A.print();
		System.out.printf("arg %f\n", A.arg());
		System.out.printf("abs %f\n", A.abs());
		System.out.printf("inv ");
		A.inv().print();
		System.out.printf("mul 2.0 \t");
		A.mul(2.0).print();
		System.out.printf("mul 1.0 \t");
		A.mul(1.0, 0.0).print();
		System.out.printf("mul 2.0 \t");
		A.mul(2.0).print();
		System.out.printf("div 1.0 \t");
		A.div(1.0, 0.0).print();
		System.out.printf("div 0.0 + I*1.0 \t");
		A.div(0.0, 1.0).print();

		int j;
		double x;
		CPI_Complex2 B = new CPI_Complex2();
		B.set(1.0, 1.0).normalize();
		B.print();
		for (j = 0; j < 10; j++) {
			x = 1.0 / 10 * j;
			System.out.printf("mob %f \t", x);
			B.mobius(x).print();
		}
		B.set(0.0, -1.0).normalize();
		B.print();
		for (j = 0; j < 10; j++) {
			x = 1.0 / 10 * j;
			System.out.printf("mob %f \t", x);
			B.mobius(x).print();
		}

		A.print();
	};
}
