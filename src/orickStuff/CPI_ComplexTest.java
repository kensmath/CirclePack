package orickStuff;

import allMains.CPBase;

public class CPI_ComplexTest {

    /******* TO RUN  EXAMPLE:

	     javac CPI*.java
	     java CPI_ComplexTest > plot.ps; kghostview  plot.ps

     */


	/**
	 * @param args
	 */

    static CPI_Ball3View view;

    static void test1() {
	CPI_Complex2Circle C = new CPI_Complex2Circle(.8,.21,.1);
	C.psplot();
	//C.print();
	CPI_Complex2Triangle T = new CPI_Complex2Triangle(C);
	
	//T = C.getTriangle();
	
	T.psplot();
	T.getCircle().psplot();
	
	int i;
	for (i=1; i<10; i++) {
	    double x;
	    x = i*1.0/10.0;
	    CPI_Complex2 z = new CPI_Complex2(x);
	    CPI_Complex2Circle D = new CPI_Complex2Circle(C);
	    D.mobius(z).psplot();
	    
	    
	    
	}
	
	C.set(0.0,0.0,1.0).psplot();
    }

   static void test2() {
      CPI_Complex2Circle cc = new CPI_Complex2Circle(0.0, 0.0, 1.0);
       //CPI_Ball3Sector S = new CPI_Ball3Sector(0.25, 0.4, 0.8, .2);
       CPI_Ball3Sector S = new CPI_Ball3Sector(0.0, 0.0, 1.0, .25);
       CPI_Ball3Triangle B = new CPI_Ball3Triangle();
       //S.print();
       //B.print();
       B.set(S.getTriangle());
       //B.print();
       B.psplot();
       S.psplot();
       B.getSector().psplot();
   
       int i;
       for (i=1; i<10; i++) {
	   CPI_Ball3View.fgcolor = i*1.0/10;
	   S.set(0.0, 0.0, 1.0, i*1.0/20);
	   B.set(S.getTriangle());
	   //B.print();
	   B.psplot();
	   S.psplot();
       }

       cc.psplot();

       for (i=1; i<10; i++) {
       CPI_Ball3View.fgcolor = i*1.0/10;
	   S.set(1.0, 1.0, 1.0, i*1.0/20);
	   B.set(S.getTriangle());
	   //B.print();
	   B.psplot();
	   S.psplot();
	   B.getSector().psplot();
      }
       
       //System.out.println("0 2 translate\n");
       cc.psplot();
       int NNN = 10;
       for (i=0; i<NNN; i++) {
    	   CPI_Ball3View.bgcolor = CPI_Ball3View.fgcolor = i*1.0/NNN;
	   CPI_Vector3 V = new CPI_Vector3(0.0, 1.0, 0.0);
	   CPI_Vector3 W = new CPI_Vector3(0.0, 1.0, 0.0);
	   CPI_Vector3 a = new CPI_Vector3(i*1.0/NNN/2, 0.0, 0.0);
	   CPI_Ball3Triangle T = new CPI_Ball3Triangle();
	   T.p[0].set(0.0, 0.0, 0.0);
	   T.p[1].set(V);
	   W.mobius(a);
	   T.p[2].set(W);
	   T.psplot();
       }
       NNN = 100;
            System.out.println("0 2 translate\n");
       cc.psplot();

       CPI_Ball3Sector SS = new CPI_Ball3Sector(1.0, 0.0, 0.0, 0.25);
       view.skew();//zstd();
       for (i=0; i<NNN; i++) {
	   CPI_Ball3View.fgcolor = i*1.0/NNN;
	   S.set(SS);
	   CPI_Vector3 a = new CPI_Vector3(i*1.0/NNN, 0.0, 0.0);
 	   S.getTriangle().psplot();
	   S.psplot();
  	   S.mobius(a);
	   S.getTriangle().psplot();
  	   //S.psplot();
       }
       System.out.println("0 2 translate\n");
       cc.psplot();
       SS.set(0.0, 1.0, 0.0, 0.25);
       for (i=0; i<NNN; i++) {
	   CPI_Ball3View.fgcolor = i*1.0/NNN;
	   S.set(SS);
	   CPI_Vector3 a = new CPI_Vector3(0.0, i*1.0/NNN, 0.0);
 	   S.psplot();
  	   S.mobius(a);
	   S.psplot();
       }
       System.out.println("0 2 translate\n");
       cc.psplot();
       SS.set(0.0, 0.0, 1.0, 0.25);
       for (i=0; i<NNN; i++) {
	   CPI_Ball3View.fgcolor = i*1.0/NNN;
	   S.set(SS);
	   CPI_Vector3 a = new CPI_Vector3(0.0, 0.0, i*1.0/NNN);
 	   S.psplot();
  	   S.mobius(a);
	   S.psplot();
       }


    
       cc.psplot();
       System.out.println("0 2 translate\n");
   }
    
    static void test3() {
	// projections

	CPI_Complex2 U = new CPI_Complex2(1.0, 2.0*CPBase.sqrt3by2);
	CPI_Complex2 V = new CPI_Complex2(2.0, 0.0);

	int i,j, NNN;
	NNN = 100;
	for (i=-NNN; i<NNN; i++){
	    for (j=-NNN; j<NNN; j++) {
		CPI_Complex2 DX = new CPI_Complex2(U);
		CPI_Complex2 DY = new CPI_Complex2(V);
		DX.mul(i*1.0);
		DY.mul(j*1.0);

		CPI_Complex2Circle C = new CPI_Complex2Circle();
		C.r = 1.0;
		C.c.set(DX).add(DY);
		CPI_Ball3Sector S = new CPI_Ball3Sector(C);
		S.psplot();
	    }
	}


    }
    static void test4() {
	// projections && normalizations

	//view.zstd();
	view.skew();

	CPI_Complex2 U = new CPI_Complex2(1.0, 2.0*CPBase.sqrt3by2);
	CPI_Complex2 V = new CPI_Complex2(2.0, 0.0);

	int i,j, NNN;
	NNN = 2;
	
	CPI_Ball3Sector[] SA = new CPI_Ball3Sector[(2*NNN+1)*(2*NNN+1)];
	int ndx = 0;
	CPI_Ball3Sector S = new CPI_Ball3Sector();
	for (i=-NNN; i<=NNN; i++){
	    for (j=-NNN; j<=NNN; j++) {
		CPI_Complex2 DX = new CPI_Complex2(U);
		CPI_Complex2 DY = new CPI_Complex2(V);
		DX.mul(i*1.0);
		DY.mul(j*1.0);

		CPI_Complex2Circle C = new CPI_Complex2Circle();
		C.r = 1.0;
		C.c.set(DX).add(DY);
		S.set(C);
		//C.print();

		SA[ndx++] = new CPI_Ball3Sector(S);
		//S.psplot();
	    }
	}
	CPI_Ball3View.fgcolor= CPI_Ball3View.bgcolor = 0.8;


	for (j=1; j<2; j++) {
	    
	    double xs = 0.0, ys = 0.0, zs = 0.0;
	    for (i=0; i<ndx; i++) {
		SA[i].psplot();
		xs += SA[i].c.x;
		ys += SA[i].c.y;
		zs += SA[i].c.z;
	    }
	    CPI_Vector3 a = new CPI_Vector3(xs, ys, zs);
	    a.div(1.0*ndx*1.61);
	    

	    a.print();
	    a.print();
	    a.print();
	    
	    CPI_Ball3View.fgcolor = CPI_Ball3View.bgcolor = 0.0;
	    for (i=0; i<ndx; i++) {
		SA[i].mobius(a);
		SA[i].psplot();
	    }
	}
	
	    
    }
  static void test5() {
	// projections && normalizations

	view.xstd();
	//view.skew();

	CPI_Complex2 U = new CPI_Complex2(1.0, CPBase.sqrt3by2);
	CPI_Complex2 V = new CPI_Complex2(2.0, 0.0);

	int i,j, NNN;
	NNN = 20;
	CPI_Ball3View.fgcolor= CPI_Ball3View.bgcolor = 0.8;
	CPI_Ball3Sector[] SA = new CPI_Ball3Sector[(2*NNN+1)*(2*NNN+1)];
	int ndx = 0;
	CPI_Ball3Sector S = new CPI_Ball3Sector();
	for (i=-NNN; i<=NNN; i++){
	    for (j=-NNN; j<=NNN; j++) {
		CPI_Complex2 DX = new CPI_Complex2(U);
		CPI_Complex2 DY = new CPI_Complex2(V);
		DX.mul(i*1.0);
		DY.mul(j*1.0);

		CPI_Complex2Circle C = new CPI_Complex2Circle();
		C.r = 1.0;
		C.c.set(DX).add(DY);
		S.set(C);

		SA[ndx] = new CPI_Ball3Sector(S);
		ndx++;
		
		S.psplot();
	    }
	}
	CPI_Ball3View.fgcolor= CPI_Ball3View.bgcolor = 0.8;


	for (j=1; j<4; j++) {
	    
	    double xs = 0.0, ys = 0.0, zs = 0.0;
	    for (i=0; i<ndx; i++) {
		//SA[i].psplot();
		xs += SA[i].c.x;
		ys += SA[i].c.y;
		zs += SA[i].c.z;
	    }
	    CPI_Vector3 a = new CPI_Vector3(xs, ys, zs);
	    a.div(1.0*ndx*1.61);
	    CPI_Ball3View.fgcolor = CPI_Ball3View.bgcolor = 0.0;
	    for (i=0; i<ndx; i++) {
		SA[i].mobius(a);
	    }
	}
	for (i=0; i<ndx; i++) {
	    SA[i].psplot();
	}
	

	System.out.println("0 2 translate\n");
	new CPI_Complex2Circle(0.0,0.0,1.0).psplot();
	view.skew();
	for (i=0; i<ndx; i++) {
	    SA[i].psplot();
	}
	System.out.println("0 2 translate\n");
	new CPI_Complex2Circle(0.0,0.0,1.0).psplot();
	view.xstd();
	for (i=0; i<ndx; i++) {
	    SA[i].psplot();
	}
	
	
	    
    }

/*  
  static void test6() {
	// projections && normalizations

	view.xstd();
	//view.skew();

	CPI_Complex2 U = new CPI_Complex2(1.0, 2.0*CPBase.sqrt3by2);
	CPI_Complex2 V = new CPI_Complex2(2.0, 0.0);

	int i,j, NNN;
	NNN = 20;
	view.fgcolor= view.bgcolor = 0.8;
	CPI_Ball3Sector[] SA = new CPI_Ball3Sector[(2*NNN+1)*(2*NNN+1)];
	int ndx = 0;
	CPI_Ball3Sector S = new CPI_Ball3Sector();
	for (i=-NNN; i<=NNN; i++){
	    for (j=-NNN; j<=NNN; j++) {
		CPI_Complex2 DX = new CPI_Complex2(U);
		CPI_Complex2 DY = new CPI_Complex2(V);
		DX.mul(i*1.0);
		DY.mul(j*1.0);

		CPI_Complex2Circle C = new CPI_Complex2Circle();
		C.r = 1.0;
		C.c.set(DX).add(DY);
		S.set(C);

		SA[ndx] = new CPI_Ball3Sector(S);
		ndx++;
		
		//S.psplot();
	    }
	}
	view.fgcolor= view.bgcolor = 0.8;

	//Vector<CPI_Ball3Sector> VSA = new Vector<CPI_Ball3Sector>(SA);
	CPI_PackingUtility.normalize( SA );

	//SA = VSA.toArray();

	view.fgcolor= view.bgcolor = 0.2;

	for (i=0; i<ndx; i++) {
	    SA[i].psplot();
	}
	
	view.fgcolor= view.bgcolor = 0.4;

	System.out.println("0 2 translate\n");
	new CPI_Complex2Circle(0.0,0.0,1.0).psplot();
	view.skew();
	for (i=0; i<ndx; i++) {
	    SA[i].psplot();
	}
	System.out.println("0 2 translate\n");
	new CPI_Complex2Circle(0.0,0.0,1.0).psplot();
	view.xstd();
	for (i=0; i<ndx; i++) {
	    SA[i].psplot();
	}
	
	
	    
    }
    public static void main(String[] args) {
	// TODO Auto-generated method stub
	//System.out.println("Hello world!");
	
	
	//CPI_Complex2 A = new CPI_Complex2();
	//A.test();
	
	//System.out.println("ITERETESTHello world!");
	//A.print();
	//A.set(1.0, -2.0);
	//A.print();
    	PrintStream ps;
    	FileOutputStream out;
    	try {
    	out = new FileOutputStream("outfile.ps");
    	ps = new PrintStream(out);
           	} 
    	catch (Exception e){
    		System.err.println ("Error in writing to file");
    		ps=null;
    	}	
    	if (ps != null) System.setOut(ps);
    	System.out.println("%!PS-Adobe-2.0 EPSF-2.0 ");
       	System.out.println("%%BoundingBox: 0 0 200 200 ");
 
	
	view = new CPI_Ball3View();
	view.init();

	System.out.println("50 50 scale\n0 setlinewidth\n1 1 translate\n");	
	//test1();
	//test2();
	//test3();
	//test4();
	//test5();
	//test6();

    }
*/	

	
	
}
