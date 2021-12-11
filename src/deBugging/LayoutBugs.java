package deBugging;

import java.io.BufferedWriter;
import java.io.File;
import java.util.Iterator;

import allMains.CirclePack;
import dcel.D_PairLink;
import dcel.D_SideData;
import dcel.PackDCEL;
import input.CPFileManager;
import komplex.EdgeSimple;
import komplex.Face;
import listManip.GraphLink;
import listManip.VertList;
import packing.PackData;

/**
 * Debugging aids: 
 * 
 * * "print" involves writing to standard out, often useful while in
 *   the debugger, since threads don't get in the way.
 *   
 * * "log" goes to file in the tmp directory.
 * 
 * Some of these are internal only; a limited number are available to 
 * the user via the 'debug' command.
 * 
 * NOTE: many redundancies here; whole thing needs to be rationalized.
 * @author kens
 *
 */
public class LayoutBugs {
	
	static File tmpdir=new File(System.getProperty("java.io.tmpdir"));
	static int rankStamp=1; // progressive number to distinguish file instances

	/**
	 * log order pairs from a 'GraphLink', typically a face tree, 
	 * in 'graphLink_xx_log.txt'. See 'DualGraph.printGraph(dlink)', 
	 * which may be more convenient.
	 * @param p PackData
	 * @param dlink GraphLink
	 * @return count, 0 on exception
	 */
	public static int log_GraphLink(PackData p,GraphLink dlink) {
		  int count=0;

		  String filename=new String("graphLink_"+(rankStamp++)+"_log.txt");
		  BufferedWriter dbw=CPFileManager.appendToFP(tmpdir,filename,false);
		  try {
			  CirclePack.cpb.msg("debug GraphLink to: "+tmpdir.toString()+File.separator+filename);
		      dbw.write("\nGraphLink of directed pairs of faces: faceCount "+p.faceCount+
		    		  "\n  firstFace "+p.firstFace+
		    		  ", firstRedFace "+p.firstRedFace+
		    		  ", redChain "+p.redChain.face+" --- \n\n");
		      
		      Iterator<EdgeSimple> dlk=dlink.iterator();
		      while (dlk.hasNext()) {
		    	  EdgeSimple edge=dlk.next();
		    	  dbw.write(edge.v+"  "+edge.w+"\n");
		    	  count++;
		      }
			  dbw.flush();
			  dbw.close();
		  } catch (Exception ex){
			  return 0;
		  }
		  return count;
	}

	/**
	 * log faces in 'VertList' in 'build_faceOrder_xx_log.txt'
	 * @param p, PackData
	 * @param fDo, VertList
	 * @return count, or 0 on error or exception
	 */
	public static int log_build_faceOrder(PackData p,VertList fDo) {
		  int count=0;

		  String filename=new String("build_faceOrder_"+(rankStamp++)+"_log.txt");
		  BufferedWriter dbw=CPFileManager.appendToFP(tmpdir,filename,false);
		  try {
			  CirclePack.cpb.msg("debug faceOrder to: "+tmpdir.toString()+File.separator+filename);
		      dbw.write("\nFace order from 'build_redchain': nodeCount "+p.nodeCount+
		    		  ", faceCount "+p.faceCount+", firstFace "+p.firstFace+
		    		  ", firstRedFace "+p.firstRedFace+" --- \n\n");
		      VertList trace=fDo;
		      while (trace!=null) {
		    	  if (trace.v<1 || trace.v>p.faceCount) { 
		    		  dbw.write("\nError: face number "+trace.v+" in error.\n");
		    		  dbw.flush();
		    		  dbw.close();
		    		  return 0;
		    	  }
		    	  int nf=trace.v;
		    	  Face face=p.faces[nf];
		    	  dbw.write("Face "+nf+": ["+face.vert[0]+","+face.vert[1]+","+face.vert[2]+"]; "+
		    			  "indexFlag "+face.indexFlag+
		    			  ",plotvert="+face.vert[(face.indexFlag+2)%3]+
		    			  ",rwbFlag="+face.rwbFlag+
		    			  ",nextFace="+face.nextFace+
		    			  ",nextRed="+face.nextRed+
		    			  ",plotFlag="+face.plotFlag+"\n");
		    	  	count++;
		    	  trace=trace.next;
		      }
			  dbw.flush();
			  dbw.close();
		  } catch (Exception ex){
			  return 0;
		  }
		  return count;
	}

	/** 
	 * Log details of 'PairLink' of 'SideDescription's in 'SideDescriptions_xxxx_log.txt'
	 * @param p, PackData
	 * @param pairs, D_PairLink
	 * @return count 
	*/
	public static int log_PairLink(PackData p,D_PairLink pairs) {
		  int count=0;

		  String filename=new String("SideDescriptions_"+(rankStamp++)+"_log.txt");
		  BufferedWriter dbw=CPFileManager.appendToFP(tmpdir,filename,false);
		  try {
			  CirclePack.cpb.msg("debug 'SideDescription' to: "+tmpdir.toString()+File.separator+filename);
			  dbw.write("SideDesriptions ============================= \n\n");
			  
			  Iterator<D_SideData> eps=pairs.iterator();
			  while (eps.hasNext()) {
				  D_SideData ep=(D_SideData)eps.next();
				  dbw.write("Index "+count+":\n");
				  count++;
				  dbw.write("spIndex="+ep.spIndex+";  mateIndex="+ep.mateIndex+"\n");
				  if (ep.startEdge!=null) dbw.write("startEdge (face) "+
						  ep.startEdge.myEdge.face.faceIndx);
				  if (ep.endEdge!=null) dbw.write(";  endEdge (face) "+
						  ep.endEdge.myEdge.face.faceIndx+"; \n");
			  }
			  dbw.write("=============\n");
			  dbw.flush();
			  dbw.close();
		  } catch(Exception ex) {
		      System.err.print(ex.toString());
		  }
		  return count;
	}

	/**
	 * Internal utility routine to write key data on a 'Face' to a
	 * BufferedWriter
	 * @param p, PackData
	 * @param f, face index
	 * @param dbw, writer
	 */
	private static void writeFace(PackDCEL pdcel, int f, BufferedWriter dbw) {
		dcel.Face face = pdcel.faces[f];
		try {
			dbw.write("Face " + f + "=<"+face+">; \n");
			dbw.write("   plots circle " + face.edge.prev.origin+"\n");
		} catch (Exception ex) {
			System.err.print(ex.toString());
		}
	}
	
	/**
	 * Log triples <v,u,w> of dcel faces and ideal faces. Results 
	 * in 'DCEL_faces_*_log.txt'.
	 * @param dcel PackDCEL
	 * @return count, -1 on error
	 */
	public static int log_DCEL_faces(PackDCEL dcel) {
		  int count=0;

		  if (dcel==null || dcel.faces==null || (dcel.faces.length-1)!=dcel.faceCount)
			  return -1;
		  
		  // open file
		  String filename=new String("DCEL_faces_"+(rankStamp++)+"_log.txt");
		  BufferedWriter dbw=CPFileManager.openWriteFP(tmpdir,filename,false);
 
		  try {
			  dbw.write("Dcel faces in order of 'dcel.faces' vector.\n");;
			  for (int f=1;f<=dcel.faceCount;f++) {
				  dcel.Face face=dcel.faces[f];
				  int[] verts=face.getVerts();
				  dbw.write("\nFace "+face.faceIndx+": <");
				  int s=verts.length;
				  for (int j=0;j<s;j++)
					  dbw.write(verts[j]+", ");
				  dbw.write(">");
				  count++;
			  }
			  dbw.write("\n\nDecel ideal faces in order of 'dcel.idealFaces' vector.\n");
			  for (int k=1;k<=dcel.idealFaceCount;k++) {
				  dcel.Face face=dcel.idealFaces[k];
				  int[] verts=face.getVerts();
				  dbw.write("\nidealFace "+face.faceIndx+": <");
				  int s=verts.length;
				  for (int j=0;j<s;j++)
					  dbw.write(verts[j]+", ");
				  dbw.write(">");
				  count++;
			  }
			  dbw.flush();
			  dbw.close();
		  } catch (Exception ex) {
			  CirclePack.cpb.errMsg("error in 'log_DCEL_faces' routine");
		  }
		  return count;
	}

}