package orickStuff;

import java.io.StreamTokenizer;

public class CPI_CPFileToken {
	static int TT_NUMBER = StreamTokenizer.TT_NUMBER;
	static int TT_WORD= StreamTokenizer.TT_WORD;
	
	int ttyp;
	String sval;
	double nval;

	CPI_CPFileToken(){
		ttyp = 0;
		sval = null;
		nval = 0;
		
	}
	
	CPI_CPFileToken(int t, String s, double v){
		ttyp = t;
		s = new String(s);
		nval = v;
	}
	
	void print(){
		//System.out.printf("%d ", ttyp);
		if (ttyp == TT_WORD) System.out.printf("%s ", sval);	
		if (ttyp == TT_NUMBER) System.out.printf("%f ", nval);	
	}
}
