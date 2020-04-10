package orickStuff;

import java.util.Vector;

public class CPI_CPFileSection {
	String label;
	Vector<CPI_CPFileToken> tokenList;
	Vector<Double> data;
	Vector<Integer> lineStarts;
	
	CPI_CPFileSection(){
		tokenList = new Vector<CPI_CPFileToken>();
		data = new Vector<Double>();
		lineStarts = new Vector<Integer>();
		
	}
	CPI_CPFileSection(String s){
		label = s;
		tokenList = new Vector< CPI_CPFileToken >();
		data = new Vector<Double>();
		lineStarts = new Vector<Integer>();
}
	void append(CPI_CPFileToken tok) {
		tokenList.add(tok);		
	}

	void printHead(){
		System.out.printf("%s ",label);
			int i;
		for (i=0; i<tokenList.size(); i++)
			tokenList.get(i).print();		
		System.out.printf("\n");
	}
	
	void printData(){
		int i, j=0;
		for (i=0; i<lineStarts.size(); i++){
			for (; j<lineStarts.get(i); j++)
				System.out.printf("%f ", data.get(j));
			System.out.printf("\n");		
		} 
		if (j<data.size()){
			for (; j<data.size(); j++)
				System.out.printf("%f ", data.get(j));
			System.out.printf("\n");		
		}	
	}
	
	void print() {
		printHead();
		printData();
		System.out.printf("\n");		
	}
	
	public static void main(String[] args){
		CPI_CPFileSection h = new CPI_CPFileSection("HEADER:");
		CPI_CPFileToken t = new CPI_CPFileToken();
		t.ttyp = CPI_CPFileToken.TT_NUMBER;
		t.sval = null;
		t.nval=10;
			
		h.append(t);
		h.print();
	
		
		
	}
}
