package ftnTheory;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Vector;

import circlePack.PackControl;
import complex.Complex;
import dcel.D_SideData;
import dcel.HalfEdge;
import dcel.RedHEdge;
import deBugging.LayoutBugs;
import exceptions.DataException;
import exceptions.ParserException;
import komplex.DualGraph;
import komplex.EdgeSimple;
import komplex.Face;
import komplex.RedEdge;
import komplex.RedList;
import listManip.FaceLink;
import listManip.GraphLink;
import math.Mobius;
import packing.PackData;
import packing.PackExtender;
import util.DispFlags;
import util.TriAspect;


public class ProjStruct extends PackExtender {
	public TriAspect[] aspects;
	public GraphLink dTree; // dual spanning tree
	
	/* 8/2019. Plan to implement construction of portions of
	 * universal cover for a surface in attempt to compute 
	 * projective structures. 
	 * First example is to be 'Bolza' genus 2 surface based 
	 * on triangle group. However, I hope to set up dcel structure
	 * for more general cases.
	 */
		
	// Constructor
	public ProjStruct(PackData p) {
		super(p);
	}

	/**
	 * Layout face-by-face using current 'labels' information
	 * and using 'dTree'. Centers are entered only in 'asp'
	 * @param p PackData
	 * @param dtree GraphLink
	 * @param asp []TriAspect
	 */
	public static void treeLayout(PackData p,GraphLink dtree,
			TriAspect []asp) {
		// log for debugging in /tmp/redface ...
		DispFlags dispflags=null;
		boolean debug=false;
		if (dtree==null || dtree.size()==0) 
			throw new DataException("GraphLink error");
		if (debug) 
			LayoutBugs.log_GraphLink(p, dtree);
		Iterator<EdgeSimple> dlk=dtree.iterator();
		while (dlk.hasNext()) {
			EdgeSimple dedge=dlk.next();
			
			// root faces just get laid in place
			if (dedge.v==0) {
				asp[dedge.w].setCents_by_label();
				for (int j=0;j<3;j++) {
					int k=asp[dedge.w].vert[j];
					p.setCenter(k,asp[dedge.w].getCenter(j));
				}
			}
			else {
				int k=p.face_nghb(dedge.v,dedge.w);
				int v2=p.faces[dedge.w].vert[(k+2)%3];
				HalfEdge dhe=p.packDCEL.findHalfEdge(dedge);
				TriAspect newasp=D_ProjStruct.plopAcrossEdge(asp,dhe);
				if (newasp==null) 
					return;
				asp[dedge.w]=new TriAspect(newasp);
				p.setCenter(v2,asp[dedge.w].getCenter((k+2)%3));
			}
			
			// draw the new face
			if (debug) {
				int []vts=p.faces[dedge.w].vert;
				for (int j=0;j<3;j++)
				p.cpScreen.drawCircle(asp[dedge.w].getCenter(j), 
						p.getRadius(vts[j]), dispflags);
				PackControl.canvasRedrawer.paintMyCanvasses(p,false);
			}
				
		} // end of while
	}



	
	/**
	 * Given a linked list of faces, find successive locations
	 * and draw the faces, and/or circles. Idea is to have
	 * 'last_face' already in place at front of list, then layout
	 * a closed face chain ending with 'last_face'; this can
	 * then be repeated for 'analytic' continuation.
	 * @param facelist
	 * @return LinkedList<TriAspect>, new TriAspect objects
	 */
	public static LinkedList<TriAspect> layout_facelist(PackData p,
			TriAspect []asp, FaceLink facelist) {
		if (facelist == null || facelist.size() == 0)
			return null;

		// iterate through the given face list
		Iterator<Integer> flist = facelist.iterator();
		
		// start linked list; clone the first, assume its centers
		//   are set and radii are scaled as desired.
		LinkedList<TriAspect> aspList=new LinkedList<TriAspect>();
		int first_face=flist.next();
		int last_face=first_face;
		TriAspect last_asp=new TriAspect(asp[first_face]);
		aspList.add(last_asp);
		while (flist.hasNext()) {
			int next_face=flist.next();
			// skip repeated and illegal indices
			if (next_face != last_face && next_face > 0
				&& next_face <= p.faceCount) {

				TriAspect next_asp=new TriAspect(asp[next_face]);
				next_asp.setCents_by_label(); // put centers in normalized position
				int jj = p.face_nghb(last_face,next_face);
				if (jj<0) { // error, stop adding to the list
					throw new ParserException(
							"disconnect in chain of faces.");
				}
				int v2=next_asp.vert[(jj+2)%3];
				next_asp.adjustData(v2,last_asp);
				aspList.add(next_asp);
				last_face=next_face;
				last_asp=next_asp;
			}
		} // end of while
		return aspList;
	}

	/**
	 * Draw a linked list faces
	 * @param p PackData
	 * @param aspList LinkedList<TriAspect>, precomputed list
	 * @param drawFirst; if true, draw the first face
	 * @param faceFlags DispFlags
	 * @param circFlags DispFlags
	 * @return int count
	 */
	public static int dispFaceChain(PackData p,LinkedList<TriAspect> aspList,
			boolean drawfirst, DispFlags faceFlags,DispFlags circFlags) {
		int count=0;
		if (aspList==null || aspList.size()==0)
			return count;
		// faces and/or circles ?
		boolean faceDo=false;
		if (faceFlags.draw)
			faceDo=true;
		boolean circDo=false;
		if (circFlags.draw)
			circDo=true;
		if (!faceDo && !circDo) 
			return 0;

		TriAspect first_asp=aspList.get(0);
		Iterator<TriAspect> aspit=aspList.iterator();
		TriAspect asp=first_asp;
		int past_face=asp.face;
		int next_face=asp.face;
		boolean firstasp=true; // for first face, may draw all circles
		if (!drawfirst) { // skip the first one
			asp=aspit.next();
			next_face=asp.face;
			firstasp=false;
		}
		while (aspit.hasNext()) {
			asp=aspit.next();
			past_face=next_face;
			next_face=asp.face;
			int j=p.face_nghb(past_face,next_face);
			if (j<0) j=0;
			int v0=asp.vert[j];
			int v1=asp.vert[(j+1)%3];
			int v2=asp.vert[(j+2)%3];
			Complex c0=asp.getCenter(asp.vertIndex(v0));
			Complex c1=asp.getCenter(asp.vertIndex(v1));
			Complex c2=asp.getCenter(asp.vertIndex(v2));
			if (faceDo) { // draw the faces
				if (!faceFlags.colorIsSet && 
						(faceFlags.fill || faceFlags.colBorder))
					faceFlags.setColor(p.getFaceColor(asp.face));
				if (faceFlags.label)
					faceFlags.setLabel(Integer.toString(asp.face));
				p.cpScreen.drawFace(c0, c1, c2,null,null,null,faceFlags);
				count++;
			}
			if (circDo) { // also draw the circles
				if (!circFlags.colorIsSet && 
						(circFlags.fill || circFlags.colBorder))
					circFlags.setColor(p.getCircleColor(v2));
				if (circFlags.label)
					circFlags.setLabel(Integer.toString(v2));
				p.cpScreen.drawCircle(c2,
						asp.labels[asp.vertIndex(v2)],circFlags);
				count++;
				if (drawfirst && firstasp) { // draw all circles of first face
					if (!circFlags.colorIsSet && 
							(circFlags.fill || circFlags.colBorder))
						circFlags.setColor(p.getCircleColor(v0));
					if (circFlags.label)
						circFlags.setLabel(Integer.toString(v0));
					p.cpScreen.drawCircle(c0,asp.labels[asp.vertIndex(v0)],
							circFlags);
					if (!circFlags.colorIsSet && 
							(circFlags.fill || circFlags.colBorder))
						circFlags.setColor(p.getCircleColor(v1));
					if (circFlags.label)
						circFlags.setLabel(Integer.toString(v1));
					p.cpScreen.drawCircle(c1,
							asp.labels[asp.vertIndex(v1)],circFlags);
					count++;
				}
			}
			firstasp=false;
		}
		return count;
	}
 
}
