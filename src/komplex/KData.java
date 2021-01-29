package komplex;

import java.awt.Color;

import allMains.CirclePack;
import util.ColorUtil;


/**
 * Stucture for combinatoric info on packings. 
 * Being modified 8/2020 to accommodate DCEL structure; there can be
 * problems with 'flower', e.g., when vert is in more than one bdry
 * segment and may not have contiguous fan. 
 * @author kens
 *
 */
public class KData{

	public int num;     // number of faces containing this node 
						// Note: 'flower' has entries from 0 to num 
    public int []flower; // list of nodes in flower, positive orientation
     					  //last = first if flower is closed (node is interior) 
    public boolean redFlag;  // DCEL: null unless this vertex is on the redChain
    
    public int bdryFlag; // 1 for boundary nodes (last != first);else 0 
    public Color color;		 // Java Color class.
    public int mark;     // mark 
    public int qualFlag; /* keeps info on quality of layout */
    public int plotFlag; /* >0 if node (seems) successfully placed during layout; */
     			   /* <=0 usually implies placement problem */
    public double []invDist; /* optional list of prescribed 'inversive distances',
     					   one for each petal node.
     					   Equals cosine of overlap angle or inv dist */
    public double []schwarzian; // real Schwarzians for edges: currently used
     						// only with schwarzian packExtender (see Schwarzian.java).
    public int utilFlag;     /* utility flag (misc temp uses) */
    public int nextVert;     /* next vert in draw order (misc temporary uses) */

    // Note: ephemeral, non-local, depends on face indexing.
    public int []faceFlower;   // indices of num faces containing this vertex; 
     							// faceFlower[i] has vertices {v,flower[i],flower[i+1]}
     							// History: originally in large 'face_org' array, then
     							// moved to 'faces' vector with indexing starting at 1
     
    // Constructor (needed only to set 'color')
    public KData() {
    	 color=ColorUtil.getFGColor();
    }
     
    public KData clone() {
    	KData Kout=new KData();
    	Kout.num=num;
    	Kout.flower=new int[num+1];
    	for (int j=0;j<=num;j++) 
    	Kout.flower[j]=flower[j];
    	if (faceFlower!=null) {
    		try {
    			 Kout.faceFlower=new int[num];
    			 for (int j=0;j<num;j++)
    				 Kout.faceFlower[j]=faceFlower[j];
    		 } catch (Exception ex) {
    			 CirclePack.cpb.errMsg("problem with 'faceFlower' in 'kData' clone");
    			 Kout.faceFlower=null; // this data is generally not essential
    		 }
    	}
    	else Kout.faceFlower=null;
    	Kout.bdryFlag=bdryFlag;
    	Kout.color=new Color(color.getRed(),color.getGreen(),color.getBlue());
    	Kout.mark=mark;     
    	Kout.qualFlag=qualFlag; 
    	Kout.plotFlag=plotFlag; 
    	Kout.utilFlag=0;
    	Kout.nextVert=0;
    	if (invDist!=null) {
   		 try{
			 Kout.invDist=new double[num+1];
			 for (int j=0;j<=num;j++)
				 Kout.invDist[j]=invDist[j];
		 } catch (Exception ex) {
			 CirclePack.cpb.errMsg("problem with 'overlaps' in 'kData' clone");
			 Kout.invDist=null; // this data is generally not essential
		 }
    	}
    	else Kout.invDist=null;
    	if (schwarzian!=null) {
   		 try{
			 Kout.schwarzian=new double[num+1];
			 for (int j=0;j<=num;j++)
				 Kout.schwarzian[j]=schwarzian[j];
		 } catch (Exception ex) {
			 CirclePack.cpb.errMsg("problem with 'schwarzian's in 'kData' clone");
			 Kout.schwarzian=null; // this data is generally not essential
		 }
    	}
    	else Kout.schwarzian=null;

    	return Kout;
    }
     
     /**
      * Remove the edge cclw from petal w. Only works for
      * closed flowers, converting them to open flowers
      * @param w int, petal
      * @return int, new 'num' on success, 0 if doesn't apply,
      * -1 on failure
      */
     public int remove_edge(int w) {
    	if (bdryFlag!=0)
    		return 0;
    	int j=-1;
    	for (int k=0;(k<num && j<0);k++)
    		if (flower[k]==w)
    			j=k;
    	if (j<1)
    		return -1;
    	int []newflower=new int[num+1];
    	int start=(j+1)%num;
    	for (int k=0;k<num;k++)
    		newflower[k]=flower[(start+k)%num];
    	num--;
    	flower=newflower;
    	bdryFlag=1;
    	return num;
     }
     
     /**
      * Add v as a petal in flower after index j
      * @param v int, new petal
      * @param j int, index
      * @return, int, index of v or -1 on error 
      */
     public int add_petal_j(int v,int j) {
    	 if (j<0 || j>num || v<0) 
    		 return -1;
    	 if (bdryFlag==0 && j==num)
    		 j=0;
    	 
    	 // repeat petal??
    	 if ((j<num && flower[j+1]==v) || (j>0 && flower[j-1]==v))
    		 return 0;
    	 
    	 // fix flower
    	 int []newflower=new int[num+2];
    	 for (int k=0;k<=j;k++)
    		 newflower[k]=flower[k];
    	 newflower[j+1]=v;
    	 for (int k=(j+1);k<=num;k++)
    		 newflower[k+1]=flower[k];
    	 flower=newflower;
    	 
    	 // fix overlaps
    	 if (invDist!=null) {
    		 try {
    	    	 double []newov=new double[num+2];
    	    	 for (int k=0;k<=j;k++)
    	    		 newov[k]=invDist[k];
    	    	 newov[j+1]=1.0;
    	    	 for (int k=(j+1);k<=num;k++)
    	    		 newov[k+1]=invDist[k];
    	    	 invDist=newov;
    		 } catch (Exception ex) {}
    	 }
    	 
    	 num++;
    	 return j+1;
     }
     
     /**
      * Add v as a petal in flower after petal w
      * @param v int, new petal
      * @param w int, position
      * @return int, index of v or -1 on error
      */
     public int add_petal_w(int v,int w) {
    	 if (v<0 || w<0) 
    		 return -1;
    	 int j=-1;
    	 for (int k=0;(k<=num && j<0);k++)
    		 if (flower[k]==w)
    			 j=k;
    	 if (j<0)
    		 return 0;
    	 return add_petal_j(v,j);
     }
     
     /**
      * remove petal at index j. If flower is closed,
      * it remains closed.
      * @param j int, index of petal to be removed
      * @return int petal or -1 on error
      */
     public int remove_petal_j(int j) {
    	 if (j<0 || j>num)
    		 return -1;
    	 int v=flower[j];
    	 
    	 // closed flower?
    	 if (bdryFlag==0) {
    		 if (num==3)
    			 return -1;
    		 
    		 // new flower
    		 if (j==0 || j==num) {
    			 for (int k=1;k<num;k++)
    				 flower[k-1]=flower[k];
    			 flower[num-1]=flower[0]; // close up again
    		 }
    		 else {
    			 int []newflower=new int[num];
    			 int m=num-j;
    			 for (int k=1;k<m;k++)
    				 newflower[k-1]=flower[j+k];
    			 for (int k=0;k<j;k++)
    				 newflower[m-1+k]=flower[k];
    			 flower=newflower;
    			 flower[num-1]=flower[0]; // close up
    		 }
    		 
    		 // fix overlaps
    		 if (invDist!=null) {
    			 try {
    	    		 if (j==0 || j==num) {
    	    			 for (int k=1;k<num;k++)
    	    				 invDist[k-1]=invDist[k];
    	    			 invDist[num-1]=invDist[0]; // close up again
    	    		 }
    	    		 else {
    	    			 double []newov=new double[num];
    	    			 for (int k=0;k<=(num-j);k++)
    	    				 newov[k]=invDist[j+k];
    	    			 for (int k=0;k<j;k++)
    	    				 newov[j+k]=invDist[k];
    	    			 invDist=newov;
    	    			 invDist[num-1]=invDist[0]; // close up
    	    		 }
    			 } catch (Exception ex) {} // ignore on error
    		 }
    		 num--;
    		 return v;
    	 }

    	 // open flower
    	 if (num<2)
    		 return -1;
    	 if (j==num) {
    		 num--;
    	 	 return v;
    	 }
    	 
    	 // fix flower
    	 for (int k=(j+1);k<=num;k++)
    		 flower[k-1]=flower[k];
    	 
    	 // fix overlaps
    	 if (invDist!=null) {
    		 try {
    			 for (int k=(j+1);k<=num;k++)
    				 invDist[k-1]=invDist[k];
    		 } catch (Exception ex) {}
    	 }
    	 
    	 num--;
    	 return v;
     }

     /**
      * Remove petal v from flower. If flower is closed,
      * it remains closed.
      * @param v int, petal to remove
      * @return int petal or -1 on error
      */
     public int remove_petal_v(int v) {
    	 for (int k=0;k<=num;k++)
    		 if (flower[k]==v)
    			 return remove_petal_j(k);
    	 return -1;
     }
     
     /** 
      * For closed flower only, this resets petal v to 0 
      * index in the closed flower
      * @param v int, petal to go first/last
      * @return int, v or -1 on error
      */
     public int resetBase(int v) {
    	 if (bdryFlag!=0 || v<0)
    		 return -1;
    	 if (v==flower[0]) // no action needed
    		 return v;
    	 int j=-1;
    	 for (int k=0;(k<=num && j<0);k++)
    		 if (flower[k]==v)
    			 j=k;
    	 if (j<0) // v not a petal
    		 return -1;
    	 
    	 // cycle flower
    	 int []newflower=new int[num+1];
    	 for (int k=0;k<num;k++)
    		 newflower[k]=flower[(j+k)%num];
    	 newflower[num]=newflower[0];
    	 flower=newflower;
    	 
    	 // fix overlaps, too
    	 if (invDist!=null) {
    		 double []newov=new double[num+1];
    		 try {
    			 for (int k=0;k<num;k++)
    				 newov[k]=invDist[(j+k)%num];
    			 newov[num]=newov[0];
    		 } catch(Exception ex) {}
    		 invDist=newov;
    	 }
    	 return v;
     }
     
}
