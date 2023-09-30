package packing;

import java.util.Vector;

import exceptions.DataException;
import geometry.HyperbolicMath;
import math.Point3D;
import util.ColorUtil;
import util.RadIvdPacket;

/**
 * Static methods for various colorizing actions --- wanted to move these
 * out of 'PackData'.
 * @author kens 2/2020
 *
 */
public class ColorCoding {

	/** 
	 * Color gradations of area of faces, index 1-19 (blues) in pack p.
	 * @param p PackData, face colors are changed in p 
	 * @return int, 1 okay, 0 ????
	 */
	public static int face_area_comp(PackData p) {
	    int flag=1;

	    int mid=(int)(ColorUtil.color_ramp_size/2);
	    double t=0.0;
	    double b=0.0;
	    // ??? I don't recall the reason for this call, p need not be hyperbolic
//	    double b=HyperbolicMath.h_area(p.getRadius(p.faces[1].vert[0]),
//	    		p.getRadius(p.faces[1].vert[1]),p.getRadius(p.faces[1].vert[2]));
	    double []areas=new double[p.faceCount+1];
	    for (int i=1;i<=p.faceCount;i++) {
	  	  	areas[i]=p.faceArea(i);
	  	  	t=(areas[i]>t) ? areas[i]:t;
	  	  	b=(areas[i]<b) ? areas[i]:b;
	    }
	    if (b==0.0) flag=0;
	    if (t==0 || Math.abs(t-b)/t<.005)
	      for (int i=1;i<=p.faceCount;i++) 
	    	  p.setFaceColor(i,ColorUtil.cloneMe(ColorUtil.coLor(mid)));
	    else for (int i=1;i<=p.faceCount;i++) 
	        p.setFaceColor(i,ColorUtil.cloneMe(ColorUtil.coLor(1+(int)((mid-2)*(areas[i]-t)/(b-t)))));
	    return (flag);
	  }
	
	/** 
	 * Compare ratio of eucl areas of faces from p and q. Color faces
	 * of p using 2-color ramp, lower indices indicating q larger, 
	 * uppers indicating p larger.
	 * @param p Packdata, colors changed
	 * @param q PackData, remains unchanged
	 * @return int
	*/
	public static int e_compare_area(PackData p,PackData q) {
	    int node,flag=1,mid;
	    double b=1.0;
	    double ratio=1.0;

	    mid=(int)(ColorUtil.color_ramp_size/2);
	    node=(p.faceCount>q.faceCount) ? q.faceCount : p.faceCount;
	    double []areas_p=new double[node+1];
	    double []areas_q=new double[node+1];
	    for (int f=1;f<=node;f++) {
	        areas_p[f]=p.faceArea(f);
	        areas_q[f]=q.faceArea(f);
	        ratio=areas_q[f]/areas_p[f];
	        if (ratio>b) b=ratio;
	        if (1.0/ratio>b) b=1.0/ratio;
	    }
	    if (Math.abs(b-1)<PackData.OKERR) flag=0;
	    else {
	        for (int v=1;v<=node;v++) {
	  	  if ((ratio=areas_p[v]/areas_q[v])>1.0)
	  	    p.setFaceColor(v,ColorUtil.cloneMe(ColorUtil.coLor((int)(mid+(mid-1)*(ratio-1.0)/(b-1.0)))));
	  	  else 
	  	    p.setFaceColor(v,ColorUtil.cloneMe(ColorUtil.coLor(1+(int)((mid-2)*(1.0-(1.0/ratio-1.0)/(b-1.0))))));
	  	}
	      }
	    if (node<p.faceCount) for (int v=node+1;v<=p.faceCount;v++)
	      p.setFaceColor(v,ColorUtil.cloneMe(ColorUtil.coLor(mid)));
	    return (flag);
	} 

	/**
	 * Compare ratio of hyperbolic areas of faces from p and q. 
	 * In 2-color ramp, lower indices for q larger, uppers for p 
	 * larger.
	 * @param p Packdata, colors changed
	 * @param q PackData, remains unchanged
	 * @return int
	 */
	public static int h_compare_area(PackData p,PackData q) {
	    int node,mid;
	    double b=1.0;
	    double ratio=1.0;

	    mid=(int)(ColorUtil.color_ramp_size/2);
	    node=(p.faceCount>q.faceCount) ? q.faceCount : p.faceCount;
	    double []areas_p=new double[node+1];
	    double []areas_q=new double[node+1];
	    for (int f=1;f<=node;f++) {
	        areas_p[f]=HyperbolicMath.h_area(p,f);
	        areas_q[f]=HyperbolicMath.h_area(q,f);
	        ratio=areas_q[f]/areas_p[f];
	        if (ratio>b) b=ratio;
	        if ((1.0/ratio)>b) b=1.0/ratio;
	    }
	    if (Math.abs(b-1)<PackData.OKERR) { 
	        for (int v=1;v<=p.faceCount;v++) 
	        	p.setFaceColor(v,ColorUtil.cloneMe(ColorUtil.coLor(mid)));
	    }
	    else {
	        for (int v=1;v<=node;v++) {
	  	  if ((ratio=areas_p[v]/areas_q[v])>1.0) {
	  		  p.setFaceColor(v,ColorUtil.cloneMe(ColorUtil.coLor((int)(mid+(mid-1)*(ratio-1.0)/(b-1.0)))));
	  	  }
	  	  else {
	  	    p.setFaceColor(v,ColorUtil.cloneMe(ColorUtil.coLor(1+(int)((mid-2)*(1.0-(1.0/ratio-1.0)/(b-1.0))))));
	  	  }
	        }
	    }
	    if (node<p.faceCount) 
	    	for (int v=node+1;v<=p.faceCount;v++)
	    		p.setFaceColor(v,ColorUtil.cloneMe(ColorUtil.coLor(mid)));
	    return 1;
	  } 
	  
	  /**
	   * Record eucl areas of 3-space faces based on 'p.xyz' data 
	   * in 'p.utilDouble'. 
	   * @param p PackData, 'p.utilDouble' is changed
	   * @return count; 0 if 'p.xyz' data not available, -1 on error
	   */
	  public static int setXYZ_areas(PackData p) {
		  int count=0;
		  if (p.xyzpoint==null || p.xyzpoint.length<(p.nodeCount+1)) {
			  throw new DataException("xyz data not available for p"+p.packNum);
		  }
		  p.utilDoubles=new Vector<Double>(p.faceCount+1);
		  p.utilDoubles.add((Double)0.0); // empty 0th spot
		  for (int f=1;f<=p.faceCount;f++) {
			  int[] verts=p.packDCEL.faces[f].getVerts();
			  Double dbl=Double.valueOf((double)Point3D.triArea(p.xyzpoint[verts[0]],
					  p.xyzpoint[verts[1]],p.xyzpoint[verts[2]]));
			  p.utilDoubles.add(dbl);
			  count++;
		  }
		  return count;
	  }

}
