package orickStuff;

import math.Point3D;
import packing.PackData;

public class CPI_CP_PackingUtility extends CPI_PackingUtility{
	
	public static int normalize( PackData p) {
		
		CPI_Ball3Sector[] BList = new CPI_Ball3Sector[p.nodeCount];
		
		// Note: vertices are indexed from 1 to p.nodeCount
		int i;
		for (i=1; i<=p.nodeCount; i++){
			Point3D v = p.rData[i].center.getAsPoint(); 
			BList[i-1] = new CPI_Ball3Sector(v.x, v.y, v.z, p.rData[i].rad/Math.PI);
		}		
		normalize(BList);
		
		for (i=1; i<=p.nodeCount; i++){
			Point3D p3 = new Point3D(BList[i-1].c.x ,BList[i-1].c.y ,BList[i-1].c.z  );
			p.rData[i].center = p3.getAsSphPoint();
			p.rData[i].rad = BList[i-1].r*Math.PI;
		}		
		return 1;	
	}
}