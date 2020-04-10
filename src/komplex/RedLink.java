package komplex;

import java.util.LinkedList;

import packing.PackData;

public class RedLink extends LinkedList<Integer> {

	private static final long 
	serialVersionUID = 1L;
	
	PackData packData;
	int face;
	
	public RedLink(PackData p) {
		packData=p;
	}

	public RedLink(PackData p,int f) {
		packData=p;
		if (face>0 && face<=packData.faceCount)
			face=f;
	}

	
	
}
