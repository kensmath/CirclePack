package util;

/**
 * Utility class to hold two integers for 'ConformalTiling' use.
 * @author kens
 *
 */
public class T2Tinfo {
	public int nghbIndx; 
	public int eIndx;
		
	public T2Tinfo(int n,int e) {
		nghbIndx=Math.abs(n);
		eIndx=Math.abs(e);
	}

}
