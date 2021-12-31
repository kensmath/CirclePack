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
 */
public class KData{

	public int num;     // number of faces containing this node 
						// Note: 'flower' has entries from 0 to num 
    public int[] flower; // list of nodes in flower, positive orientation
     					 // last = first if flower is closed (node interior) 
    public boolean redFlag; // DCEL: null unless this vertex is on redChain
    
    public int bdryFlag; // 1 for boundary nodes (last != first);else 0 
    public Color color;  // Java Color class.
    public int mark;     // mark 
    public int qualFlag; // keeps info on quality of layout 
    public int plotFlag; // >0 if node (seems) successfully placed during layout; */
     			   // <=0 usually implies placement problem 
    public double []invDist; // optional list of prescribed 'inversive 
    						 // distances', one for each petal node.
     					   //Equals cosine of overlap angle or inv dist 
    public double []schwarzian; // real Schwarzians for edges: currently used
     						// only with schwarzian packExtender (see Schwarzian.java).
    public int utilFlag; // utility flag (misc temp uses) 
    public int nextVert; // next vert in draw order (misc temporary uses) 

    // Note: ephemeral, non-local, depends on face indexing.
    public int []faceFlower; // indices of num faces containing this vertex; 
     						 // faceFlower[i] has vertices 
    						 // {v,flower[i],flower[i+1]}
     
    // Constructor (needed only to set 'color')
    public KData() {
    	 color=ColorUtil.getFGColor();
    }
     
    public KData clone() {
    	KData Kout=new KData();
    	if (num==0)
    		return Kout;
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
    			 CirclePack.cpb.errMsg(
    				"problem with 'faceFlower' in 'kData' clone");
    			 Kout.faceFlower=null; // this data generally not essential
    		 }
    	}
    	else Kout.faceFlower=null;
    	Kout.bdryFlag=bdryFlag;
    	Kout.color=new Color(color.getRed(),color.getGreen(),
    			color.getBlue());
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
			 CirclePack.cpb.errMsg(
					 "problem with 'overlaps' in 'kData' clone");
			 Kout.invDist=null; // this data generally not essential
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
 
}
