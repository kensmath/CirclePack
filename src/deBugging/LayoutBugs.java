package deBugging;

import java.io.BufferedWriter;
import java.io.File;
import java.util.Iterator;

import allMains.CirclePack;
import combinatorics.komplex.Face;
import dcel.PairLink;
import dcel.SideData;
import dcel.PackDCEL;
import input.CPFileManager;
import komplex.EdgeSimple;
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
				  dcel.DcelFace face=dcel.faces[f];
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
				  dcel.DcelFace face=dcel.idealFaces[k];
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