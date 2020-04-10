package orickStuff;

public class CPI_Ball3View {
    static CPI_Vector3 X = new CPI_Vector3(1.0, 0.0, 0.0);
    static CPI_Vector3 Y = new CPI_Vector3(0.0, 1.0, 0.0);
    static CPI_Vector3 Z = new CPI_Vector3(0.0, 0.0, 1.0);
    
    static CPI_Vector3 N = new CPI_Vector3(), U = new CPI_Vector3(), V = new CPI_Vector3();  // V is the VIEW vector; outward ball normal!
    
    static double fgcolor;
    static double bgcolor;
 
   //static CPI_Vector3 N = new CPI_Vector3;  // V is the VIEW vector; outward ball normal!
    //static CPI_Vector3 U = new CPI_Vector3;
    //static CPI_Vector3 V = new CPI_Vector3;


    CPI_Ball3View() { }

    void init(){
	zstd();

	//N = new CPI_Vector3();
	//N.set(0.0, 0.0, 1.0);
	//U = new CPI_Vector3();
	//U.set(1.0, 0.0, 0.0);
	//V = new CPI_Vector3();
	//V.set(0.0, 1.0, 0.0);

	//N = new CPI_Vector3();
	//N = new CPI_Vector3(0.0, 0.0, 1.0); 
	//U = new CPI_Vector3(1.0, 0.0, 0.0);
	//V = new CPI_Vector3(0.0, 1.0, 0.0);
	
	defaultColor();

    }
    void defaultColor() {
	fgcolor = 0.0;
	bgcolor = 0.5;
    }

    void zstd() { N.set(Z); U.set(X); V.set(Y); N.normalize(); U.normalize(); V.normalize(); }
    void ystd() { N.set(Y); U.set(Z); V.set(X); N.normalize(); U.normalize(); V.normalize(); }
    void xstd() { N.set(X); U.set(Y); V.set(Z); N.normalize(); U.normalize(); V.normalize(); }
    
    void skew() {
	N.set(X).add(Y).add(Z).normalize();
	U.set(N.perp()).normalize();
	V.set(N).cross(U).normalize();

	N.print();
	U.print();
	V.print();

    }
    

}