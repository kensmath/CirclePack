package ftnTheory;

import java.util.Vector;

import circlePack.PackControl;
import complex.Complex;
import listManip.FaceLink;
import packing.PackData;
import packing.PackExtender;
import panels.CPScreen;
import util.DispFlags;

/**
 * This class is for "word walking" experiments: a tripartite 
 * triangulation is one in which the vertices are partitioned
 * into 3 types, and every triangle has one vertex of each type.
 * (The types are typically associated with 0, 1, and infinity,
 * as with the study of 'dessins d'Enfants'.)
 * 
 * "Word walking" refers to identification of words in three
 * letters with chains of contiguous faces, where each letter
 * indicates an edge identification across the edge between
 * two particular types of vertices, say, 0-1 edge is associated
 * with a, 1-infty edge with b, infty-1 edge with c.
 * 
 * The idea is to follow/generate such words on one or two
 * triangulations simultaneously. This can then be associated with
 * 'function pairs' that occur in my research.
 * @author kens, 10/07
 *
 */
public class WordWalker extends PackExtender {
	

	// Constructor
	public WordWalker(PackData p) {
		super(p);
		packData=p;
		extensionType="WORD_WALKER";
		extensionAbbrev="WW";
		toolTip="'WordWalker': manipulate walks on trivalent triangulations"; 
		registerXType();
		packData.packExtensions.add(this);
	}
	
	public int cmdParser(String cmd,Vector<Vector<String>> flagSegs) {
		int count=0;
		Vector<String> items=null;
		
		// ======== mtrek =========
		// draw facelist, imprinting first and last
		
		if (cmd.startsWith("mtrek")) {
			try {
				items=(Vector<String>)flagSegs.get(0);
				FaceLink facelink=new FaceLink(packData,items);
				if (facelink!=null && facelink.size()>0) {
					int firstface=(int)facelink.remove(0);
					int lastface=(int)facelink.get(facelink.size()-1);
					imprintFace(firstface);
					count++;
					if (facelink.size()>1) {
						Integer []codes=new Integer[3];
						codes[0]=Integer.valueOf(1);
						codes[1]=null;
						codes[2]=null;
						DispFlags dflags=new DispFlags(null);
						packData.layout_facelist(null,facelink,dflags,null,true,false,firstface,-1.0);
						count++;
					}
					imprintFace(lastface);
					PackControl.activeFrame.reDisplay();
					count++;
				}
					
			} catch (Exception ex){
				return count;
			}
		}
		return super.cmdParser(cmd, flagSegs);
	}
	
	/**
	 * Draw a face with special coloring: red, green, blue on the
	 * 0-1, 1-2, 2-0 sectors, respectively.
	 * @param face
	 */
	public void imprintFace(int face) {
		int []vert=packData.faces[face].vert;
		Complex c0=packData.rData[vert[0]].center;
		Complex c1=packData.rData[vert[1]].center;
		Complex c2=packData.rData[vert[2]].center;
		Complex cc=packData.face_center(face);
		
		DispFlags dflags=new DispFlags("f");
		dflags.setColor(CPScreen.coLor(232));
		packData.cpScreen.drawFace(c0,c1,cc,null,null,null,dflags);
		dflags.setColor(CPScreen.coLor(218));
		packData.cpScreen.drawFace(c1,c2,cc,null,null,null,dflags);
		dflags.setColor(CPScreen.coLor(1));
		packData.cpScreen.drawFace(c2,c0,cc,null,null,null,dflags);
	}
	
	public void helpInfo() {
		helpMsg("Commands for PackExtender "+extensionAbbrev+" (Word Walking)");
		helpMsg("To manipulate "+
				"the layout of faces in a trivalent triangulation as "+
				"prescribed in 'words' giving successive sides to "+
				"extend across.");
	}
	
}
