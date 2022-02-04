package branching;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Vector;

import canvasses.DisplayParser;
import circlePack.PackControl;
import dcel.HalfEdge;
import dcel.RawManip;
import dcel.Vertex;
import exceptions.DataException;
import exceptions.ParserException;
import ftnTheory.GenModBranching;
import input.CommandStrParser;
import komplex.EdgeSimple;
import listManip.HalfLink;
import math.Mobius;
import util.StringUtil;

/**
 * A @see GenBranchPt of the "singular" type conceived 
 * by Ed Crane. The event horizon consists of the three
 * vertices of the specified 'singFace' and the neighboring
 * "connection" circles (so named by James Ashe).
 * The face index is ephemeral, so depend on the event
 * horizon to build and dismantle. The face vertices 
 * 1,2,3 correspond in order with vertices of 'singFace' 
 * in the parent. We add a central "vanishing" circle 
 * (with aim 4*pi) and 3 "guide" circles between the 
 * original vertices. Two overlap angles are the parameters 
 * (along with a third so they sum to PI). Each original
 * vertex j shares overlap[j] with each of its guide 
 * circle neighbors. (Terminology from paper by Ashe,
 * Crane, and Stephenson.) 
 * 
 * Note: E.g., to get overlap ov on edge (2,3) of 'singFace', 
 * we have corner[1] overlap nghbs guide[2] and guide[3] 
 * by ov. 
 * 
 * Construction goes like this:
 * (1) 'corner's and 'faceEdge's are the circles/edges
 *     of 'singFace'.
 * (2) 'vanishing' is created as barycenter of 'singFace'
 * (3) each 'faceEdge' is split, creating a 'guide' vertex
 *     and a 'secondEdge'.
 * 
 * Associations: (Note: indexing will be from 0)
 * (*) corner[j] is the origin of faceEdge[j]. 
 * (*) when we split faceEdge[j], the new vertex
 *     created is guide[(j+2)%3] (so guide[j] is
 *     opposite corner[j])
 * (*) after all the splittings, corner[j] has
 *     two edges from it, faceEdge[j] and also 
 *     secondEdge[(j+2)%3]. These edges both
 *     are assigned cos_overs[j] as inversive
 *     distance.
 *     
 * To dismantle, we meld faceEdge[3]/[2]/[1] (in that
 * order) and the remove the barycenter 'vanishing'.
 *    
 * @author kens, May 2012
 *
 */

public class SingBrModPt extends GenBrModPt {
	
	int singFace;       // original face index (but ephermeral)
	double[] overlaps;  // parameters, 2 specified, on computed

	// need to save some info
	Vertex[] corner;  // verts of 'singFace'
	HalfEdge[] faceEdge; // edges of original face (these get split)
	HalfEdge[] secondEdge; // created when faceEdge is split

	// four added circles: 3 guides and 'vanishing'
	Vertex[] guide;     // guide[j] is across from corner[j]
	Vertex vanishing;	// barycenter, carries 'myAim', ultimately radius 0.
	Vertex[] connect;	// connect[j] is across from corner[j]
	
	// Constructor
	public SingBrModPt(GenModBranching g,int bID,double aim, int f, double o1,double o2) {
		super(g,bID,aim);
		gmb=g;
		myType=GenBrModPt.SINGULAR;
		myEdge=pdc.faces[f].edge; // halfedge associated with f
		
		// set default overlaps to  pi/3; indexing from 0
		overlaps=new double[3];
		overlaps[0]=overlaps[1]=overlaps[2]=0.3333333; // Pi/3
		
		// debug help
		System.out.println("sing attempt: a = "+aim/Math.PI+"; f = "+f+
				"; overlaps/Pi "+o1+" "+o2+" "+(1.0-o1-o2));
		
		modifyPackData();
		success=true;
	}

	/**
	 * Create packing
	 */
	public int modifyPackData() {
		faceEdge=new HalfEdge[3];
		secondEdge=new HalfEdge[3];
		corner=new Vertex[3];
		connect=new Vertex[3];
		guide=new Vertex[3];
		
		faceEdge[0]=myEdge;
		faceEdge[1]=faceEdge[0].next;
		faceEdge[2]=faceEdge[1].next;
		for (int j=0;j<3;j++) {
			corner[j]=faceEdge[j].origin;
			HalfEdge he=faceEdge[(j+1)%3];
			connect[j]=he.twin.prev.origin;
		}

		// create 'eventHorizon'
		eventHorizon=new HalfLink();
		for (int j=0;j<3;j++) {
			int v=corner[j].vertIndx;
			int w=corner[(j+1)%3].vertIndx;
			int u=connect[(j+2)%3].vertIndx;
			EdgeSimple es=new EdgeSimple(v,u);
			eventHorizon.add(pdc.findHalfEdge(es));
			es=new EdgeSimple(u,w);
			eventHorizon.add(pdc.findHalfEdge(es));
		}

		// create 'vanishing'
		int van=RawManip.addBary_raw(pdc,faceEdge[0],false);
		vanishing=pdc.vertices[van];
		
		// successively split 'faceEdge's
		for (int j=0;j<3;j++) {
			RawManip.splitEdge_raw(pdc,faceEdge[j]);
			guide[(j+2)%3]=faceEdge[j].twin.origin;
			secondEdge[(j+1)%3]=faceEdge[j].twin.prev.twin.prev;
		}
		
		// get encircling link for local holonomy   
		myHoloBorder=RawManip.leftsideLink(pdc,eventHorizon);
		myHoloBorder.add(0,eventHorizon.get(0));
		
		// add 'myHoloBorder' to 'layoutAddons'
		layoutAddons=new HalfLink();
		layoutAddons.abutMore(myHoloBorder);

		// record exclusions
		myExclusions=new ArrayList<dcel.Vertex>();
		Iterator<HalfEdge> eis=eventHorizon.iterator();
		while (eis.hasNext()) {
			myExclusions.add(eis.next().origin);
		}
		myExclusions.add(vanishing);
		myExclusions.add(guide[0]);
		myExclusions.add(guide[1]);
		myExclusions.add(guide[2]);
		
		return pdc.vertCount;
	}		
	
	/**
	 * Intend to delete this branch point, so reestablish parent packing.
	 */
	public void dismantle() {
	}
		
	/**
	 * Singular branch point parameters are the overlap angles associated
	 * with the three edges of 'singFace'. This is similar to 'fractured'
	 * branch points, but since the overlap angles must sum to Pi, we 
	 * specify two overlaps and compute the third.
	 * Vertices are 'corner[j]', j=0,1,2, so overlap [j] is for circles 
	 * forming edge opposite corner[j+1]. 
	 * @param Vector<Vector<String>> flagSegs, overlaps
	 * @return int, 3 for success
	 */
	 public int setParameters(Vector<Vector<String>> flagSegs) {
		int count=0;
		boolean gotovers=false;
		double []ovlp=new double[2];
		
		// get flags: -a aim, -o o1 o2
		Iterator<Vector<String>> fit=flagSegs.iterator();
		while (fit.hasNext()) {
			Vector<String> items=fit.next();
			try {
				if (!StringUtil.isFlag(items.get(0)))
					throw new ParserException("usage: -a aim -o o1 o2");
				String str=items.remove(0);
				switch (str.charAt(1)) {
				case 'a': // new aim
				{
					myAim=Double.parseDouble(items.get(0));
					count++;
					break;
				}
				case 'o': // overlaps
				{
					for (int i=0;i<2;i++) {
						ovlp[i]=Double.parseDouble(items.remove(0));
						if (ovlp[i]<=0 || ovlp[i]>=1.0)
							throw new ParserException("overlap not in (0,1)");
						count++;
					}
					if ((ovlp[0]+ovlp[1])>.99999999)
						throw new ParserException("sum of o1 o2 overlaps not in (0,1)");
					gotovers=true;
					break;
				}
				} // end of switch
			} catch(Exception ex) {
				throw new ParserException("usage: -a aim, -o o1 o2");
			}
		} // end of while
		
		// set overlaps
		if (gotovers)
			count+=resetOverlaps(ovlp[0],ovlp[1]);
		
		return count;
	 }
	
	 /**
	  * For an already established 'singular' branch point, this just
	  * adjusts the overlap angles. 'o0', 'o1' in [0,1], 'o0+o1+o2'=1.0.
	  * Record in 'overlaps' and set inv distances. 
	  * @param o0 double
	  * @param o1 double
	  * @return 1
	  */
	 public int resetOverlaps(double o0,double o1) {
		 if (o0<=0.0 || o0>=1.0 || o1<=0.0 || o1>=1.0 || (o0+o1)>=1.0)
			throw new DataException(
					"'singular' usage: 2 overlaps in [0,1], sum <= 1");
		 overlaps[0]=o0;
		 overlaps[1]=o1;
		 overlaps[2]=1-(overlaps[0]+overlaps[1]);
		 for (int j=0;j<3;j++) {
			 faceEdge[j].setInvDist(Math.cos(overlaps[j]*Math.PI));
			 secondEdge[j].setInvDist(Math.cos(overlaps[j]*Math.PI));
		 }
		 return 1;
	 }
	 
	 /**
	  * See if there are special actions for display.
	  * If so, do them, remove them, and pass the rest 
	  * to 'super'. May flush some commands designed for 
	  * other types of branch points.
	  * 
	  * @param flagSegs flag sequences
	  * @return int count of display actions
	  */
	public int displayMe(Vector<Vector<String>> flagSegs) {
		Vector<Vector<String>> newFlagSegs=new Vector<Vector<String>>(1);
		Vector<String> items=new Vector<String>(2);
		int n=0;
		for (int j=0;j<flagSegs.size();j++) {
			String dispCode=null;
			items=flagSegs.get(j);
			String str=items.get(0);
			boolean hit=false;
				
			// get info to reconstruct new command: 
			// e.g. -h1fc20 converts to -cfc20 <chap[1]>
			String suff="";  // save suffex of original, e.g. 'fc5t4' 
			String target=null; // build target list
			Character c=null;
			if (str.length()>2)   // possible number here
				c=Character.valueOf(str.charAt(2));

			// look for objects to parse here
			char c2;
			if (str.startsWith("-s") || str.startsWith("-j")) { // flush these for singular points
				hit=false;
			}
			else if (str.length()>1 && ((c2=str.charAt(1))=='h' || c2=='y')) {
				
				if (str.startsWith("-h")) { // guides
					if (c!=null && ((char)c=='1' || (char)c=='2' || (char)c=='3')) {
						if ((char)c=='1')
							target=new String(" "+guide[0]+" ");
						else if ((char)c=='2')
							target=new String(" "+guide[1]+" ");
						else
								target=new String(" "+guide[2]+" ");
							if (str.length()>3)
								suff=str.substring(3);
						}
					else { // default to all three
						target=new String(" "+guide[0]+" "+guide[1]+" "+guide[2]+" ");
						if (str.length()>2)
							suff=str.substring(2);
					}
					dispCode="-c";
					hit=true;
				}
				else if (str.startsWith("-y")) { // newBrSpot
					target=new String(" "+vanishing+" ");
					dispCode="-c";
					hit=true;
				}

			} // h/y circle case
				
			// In singular case, edges between the three circles (r,g,b) go through
			//   the branch value. This option draws those edges.
			else if (str.startsWith("-e")) {
				dispCode="-e";
				hit=true;
				target=new String(" "+vanishing+" corner[0] "+
						vanishing+" corner[1]  "+vanishing+" corner[2]");
			}
				
			// else if -w or -wr flag, have parent run it, or store it to pass on to parent
			else if (items.size()>0 && items.get(0).startsWith("-w")) {
				String fs=items.remove(0);
				CommandStrParser.jexecute(p,"disp "+fs);
				n++;
				if (items.size()>0) // save anything after the -w flag
					newFlagSegs.add(items);
			}
				
			// just past along other items
			else { 
				newFlagSegs.add(items);
				hit=false;
			}
				
			// build display string and display on packData's screen
			if (hit) {
				StringBuilder pulloff=new StringBuilder();
				pulloff.append(dispCode);
				pulloff.append(suff);
				pulloff.append(target);
				
				// found something? display on parent canvas
				if (pulloff.length()>0) {
					n=DisplayParser.dispParse(p,p.cpScreen,
							StringUtil.flagSeg(pulloff.toString()));
				}
			}
		} // end of loop through items

		n+=DisplayParser.dispParse(p,p.cpScreen,newFlagSegs);
			
		if (n!=0)
			PackControl.canvasRedrawer.paintMyCanvasses(p,false); 
		return n;
	}
	
	public String getParameters() {
		return new String("Singular branch face, aim "+
				myAim/Math.PI+"*Pi on face "+singFace);
	}
	
	public String reportExistence() {
		if (success)
			return new String(
				"Started 'singular' branch point; face = "+singFace);
		else 
			return new String(
				"Failed to initiate 'singular' branching for face = "+singFace);
	}
	
	public String reportStatus() {
		return new String("'singular', ID "+branchID+": face="+singFace+
				", aim="+myAim+", holonomy err="+
				Mobius.frobeniusNorm(getLocalHolonomy()));
	}
	
	/**
	 * Changes in 'packData' may require us to reassert
	 * the data for this branch point. This is also called
	 * when a newly created branch point is first installed.
	 */
	public void renew() {
		// reset the overlaps
		resetOverlaps(overlaps[0],overlaps[1]);
		// reset aim
		p.setAim(vanishing.vertIndx,myAim);
		// set colors
		p.setCircleColor(vanishing.vertIndx,new Color(200,200,200)); // black
		p.setCircleColor(guide[0].vertIndx,new Color(200,0,0)); // red
		p.setCircleColor(corner[0].vertIndx,new Color(255,0,0)); // light red
		p.setCircleColor(guide[1].vertIndx,new Color(0,200,0)); // green
		p.setCircleColor(corner[1].vertIndx,new Color(0,255,0)); // light green
		p.setCircleColor(guide[2].vertIndx,new Color(0,0,200)); // blue
		p.setCircleColor(corner[2].vertIndx,new Color(0,0,255)); // light blue
		p.setCircleColor(myEdge.origin.vertIndx,new Color(125,0,0)); // light red
	}
	
}
