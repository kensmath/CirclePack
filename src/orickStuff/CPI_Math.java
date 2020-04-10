package orickStuff;


public class CPI_Math {

    static CPI_Complex2 randBox() { CPI_Complex2 z = new CPI_Complex2(Math.random(), Math.random()); return z;}
    static CPI_Complex2 rand() {
	double r, th;
	r = Math.sqrt(Math.random());
	th = Math.random()*Math.PI*2;
	CPI_Complex2 z = new CPI_Complex2(r*Math.cos(th), r*Math.sin(th));
	return z;
    }

    


    //---- TESTING
    public static void randomtest(String[] args) {
	int i;
	for (i=1; i<1000; i++) {
	    CPI_Complex2 z = rand();
	    z.print();
	}
    }

}

