package ftnTheory;

import java.util.Vector;

import circlePack.PackControl;
import complex.Complex;
import dcel.PackDCEL;
import listManip.FaceLink;
import listManip.HalfLink;
import packing.PackData;
import packing.PackExtender;
import util.ColorUtil;
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
		extenderPD=p;
		extensionType="WORD_WALKER";
		extensionAbbrev="WW";
		toolTip="'WordWalker': manipulate walks on trivalent triangulations"; 
		registerXType();
		extenderPD.packExtensions.add(this);
	}
	
	public int cmdParser(String cmd,Vector<Vector<String>> flagSegs) {
		int count=0;
		Vector<String> items=null;
		
		// ======== mtrek =========
		// draw facelist, imprinting first and last
		
		if (cmd.startsWith("mtrek")) {
			boolean useSchw=false;
			// TODO: may want option to use schwarzians
			try {
				items=(Vector<String>)flagSegs.get(0);
				FaceLink facelink=new FaceLink(extenderPD,items);
				if (facelink!=null && facelink.size()>0) {
					int firstface=(int)facelink.remove(0);
					int lastface=(int)facelink.get(facelink.size()-1);
					imprintFace(firstface);
					count++;
					HalfLink hlink=null;
					PackDCEL pdcel=extenderPD.packDCEL;
					if (facelink.size()>1 && 
							(hlink=facelink.getHalfLink(pdcel))!=null) {
						Integer []codes=new Integer[3];
						codes[0]=Integer.valueOf(1);
						codes[1]=null;
						codes[2]=null;
						DispFlags dflags=new DispFlags(null);
						pdcel.layoutFactory(null,hlink,dflags,null,
								true,false,useSchw,-1.0);
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
	 * @param f int
	 */
	public void imprintFace(int f) {
		Complex[] c=new Complex[3];
			c=extenderPD.packDCEL.getFaceCorners(extenderPD.packDCEL.faces[f]);
		Complex cc=extenderPD.getFaceCenter(f);
		DispFlags dflags=new DispFlags("f");
		dflags.setColor(ColorUtil.coLor(232));
		extenderPD.cpDrawing.drawFace(c[0],c[1],cc,null,null,null,dflags);
		dflags.setColor(ColorUtil.coLor(218));
		extenderPD.cpDrawing.drawFace(c[1],c[2],cc,null,null,null,dflags);
		dflags.setColor(ColorUtil.coLor(1));
		extenderPD.cpDrawing.drawFace(c[2],c[0],cc,null,null,null,dflags);
	}
	
	public void helpInfo() {
		helpMsg("Commands for PackExtender "+extensionAbbrev+" (Word Walking)");
		helpMsg("To manipulate "+
				"the layout of faces in a trivalent triangulation as "+
				"prescribed in 'words' giving successive sides to "+
				"extend across.");
	}
	
}
