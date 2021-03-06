package komplex;

import java.awt.Color;

import packing.PackData;
import panels.CPScreen;

/**
 * Structure for face information; this was designed for triangular faces,
 * later modified for more general polygonal faces. Note that in CirclePack,
 * face indices are not persistent but created on the fly in 'complex_count'.
 * They could change, e.g., if 'alpha' is changed.
 * @author kens
 *
 */
public class Face{
     public int vert[];      // ordered tuple of vertices 
     						 //   Note: first index is not repeated at end
     public int vertCount;	 // number of vertices (default 3)
     public int indexFlag;   // which circles to use for drawing? use
     					     // verts 'indexFlag', '(indexFlag+1)%3'
                             // are used to plot '(indexFlag+2)%3'
     						 // for triangular faces
     public int nextFace;    // next face in drawing order. 
     public int nextRed;     // next red face (if this face is red) 
     public int plotFlag;    // >0: face location (seems) reliable for plotting;
                             // <=0 indicates some layout problem */
     public Color color;     // Java Color class.
     public int mark;        // mark
     PackData packData;      // packing this is associated with (if any)
     // TODO: not sure 'packData' is ever needed.
     public int rwbFlag;	 // "red/white/blue" indicator: see comment below:
     /* used to monitor status of faces during and after red chain manipulations.
      *    rwbFlag=0: "free" face, generally, not yet processed
      *    rwbFlag>0: "red" face, generally, currently in the 'red chain'
      *    rwbFlag<0: "white", generally, passed over by (hence inside) red chain,
      *       hence inside the final red chain. 
      */
     
     // Constructors
     public Face(PackData p,int vCount) {
    	 packData=p;
    	 vertCount=vCount;
    	 vert=new int[vCount];
    	 color=CPScreen.getFGColor();
     }
     
     public Face(PackData p) {
    	 this(p,3);
     }

     public Face(int vCount) {
    	 this(null,vCount);
     }
     
     public Face() {
    	 this(null,3);
     }

     /**
      * A face is 'boundary' if one or more of its vertices
      * is boundary.
      * @param p PackData
      * @return boolean, false if p==null 
      */
     public boolean isBdryFace(PackData p) {
    	 if (p==null) return false;
    	 for (int j=0;j<vertCount;j++)
    		 if (p.kData[vert[j]].bdryFlag==1)
    			 return true;
    	 return false;
     }
  
     /**
      * Find index of v in 'vert'.
      * @param v int
      * @return index or -1 if v is not a vertex.
      */
     public int vertIndx(int v) {
    	 for (int j=0;j<vertCount;j++)
    		 if (v==vert[j]) return j;
    	 return -1;
     }

     /** 
      * Return a new 'Face' object whose data duplicates this.
      * @return Face
      */
     public Face clone() {
    	 Face sface=new Face(vertCount);
    	 sface.color=new Color(color.getRed(),color.getGreen(),color.getBlue());
    	 sface.indexFlag=indexFlag;
    	 sface.mark=mark;
    	 sface.nextFace=nextFace;
    	 sface.nextRed=nextRed;
    	 sface.plotFlag=plotFlag;
    	 sface.rwbFlag=rwbFlag;
		 sface.vert=new int[vertCount];
		 for (int j=0;j<vertCount;j++) sface.vert[j]=vert[j];
		 return sface;
     }
}
