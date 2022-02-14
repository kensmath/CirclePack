package ftnTheory;

import java.util.Iterator;
import java.util.Vector;

import allMains.CirclePack;
import complex.Complex;
import dcel.HalfEdge;
import dcel.PackDCEL;
import dcel.RawManip;
import komplex.EdgeSimple;
import listManip.EdgeLink;
import listManip.HalfLink;
import listManip.NodeLink;
import packing.PackData;
import packing.PackExtender;
import util.CmdStruct;
import util.StringUtil;

/**
 * This extender is aimed at the study of 'jammed' packings of
 * equal sized discs. It was started in November 2017 in conjunction
 * with Varda Faghir (Arizona State). The data of interest is a
 * "reduced network" of vertices and edges on a torus. In a jammed
 * configuration there are faces, typically non-triangular, and
 * reduced "coordination" of the vertices. To make a triangulation,
 * we add barycenters to all faces (even triangular ones). We keep
 * track of these because we want to be able to manipulate the
 * combinatorics --- add/remove edges, etc. 
 * 
 * TODO: new goal (5/2018) is to adjust interstice combinatorics
 * in order to reduce variation in the radii of the circles. First
 * attempt is to replace single barycenter by various tree graphs
 * with the same number of leaves. Need to measure effects, e.g.,
 * by variance of neighbors (local and global).
 * 
 * (This same extender might be used to study the "glass" structures
 * of Mahdi Sadjadi.)
 * 
 * We assume the packing was read in as a graph to which barycenters
 * were added and tiles were generated via paving. Keep track of 
 * vertex types in 'homePack' with 'mark': -1 means an original vert
 * (not an interstice, these have mark=1 on reading); n>0 means this
 * is the nth interstice. See also 'istices' list. 
 * @author kstephe2, November 2017
 *
 */
public class JammedPack extends PackExtender {
	
	// packings (in addition to 'packData')
	PackData homePack;    // Copy of the original for reference
	PackData backPack;    // intentional backup: updated to prepare for 'undo'
	PackData addrmPack;   // shortterm backup when rm/add is carried out
	
	// build and keep data on interstices, indexed from 1
	int iNum;             // number of interstices
	int []istices;        // list of interstices
	int [][]iFlowers;     // for each interstice, cclw closed list of rim verts
	
	// utility
	Integer crumb;  	  // null or original vertex held to match for adding edge
	
	public JammedPack(PackData p) {
		super(p);
		extensionType="Jammed Packing";
		extensionAbbrev="JP";
		toolTip="'JammedPack' is for manipulation of triangulations of "
				+"tori in the study of 'jamming'.";
		registerXType();
		
		// the packing should have 'original vertices' marked; if not, fail
		int markCount=0;
		for (int v=1;v<=packData.nodeCount;v++) 
			if (packData.getVertMark(v)==1) {
				markCount++;
			}
		
		// small checks on data
		if (markCount==0 || markCount==packData.nodeCount) {
			errorMsg("'JammedPack' extender failed: packing no "+
					"or all vertices marked");
			running=false;
		}
		
		iNum=packData.nodeCount-markCount;
		homePack=packData.copyPackTo(); // reference, never changes
		iFlowers=new int[iNum+1][]; // indexed from 1
		istices=new int[iNum+1];
		int tick=0;
		
		// mark the vertices, list interstices and their 'rim' flowers
		for (int v=1;v<=homePack.nodeCount;v++) { 
			if (homePack.getVertMark(v)==1) {
				homePack.setVertMark(v,-1); // mark=-1 for original vertex
			}
			else if (homePack.countFaces(v)>3) {   // only degree > 3
				istices[++tick]=v;
				homePack.setVertMark(v,tick); // index of interstice
				iFlowers[tick]=new int[homePack.countFaces(v)+1];
				int[] hflower=homePack.getFlower(v);
				for (int j=0;j<hflower.length;j++)
					iFlowers[tick][j]=hflower[j];
			}
		}
		cpCommand(homePack,"color -c s a");
		cpCommand(packData,"color -c s a");
			
		if (running) {
			packData.packExtensions.add(this);
		}

	}

	public int cmdParser(String cmd,Vector<Vector<String>> flagSegs) {
		Vector<String> items=null;
		int count=0;
		
		// ========= split =========
		if (cmd.startsWith("spli")) {
			if (flagSegs!=null && (items=flagSegs.get(0)).size()>0) {
				EdgeLink elist=new EdgeLink(packData,items);
				Iterator<EdgeSimple> elst=elist.iterator();
				while(elst.hasNext()) {
					EdgeSimple edge=elst.next();
					// neither should be degree 3
					if (packData.countFaces(edge.v)==3 || packData.countFaces(edge.w)==3)
						return count;
					// both can't be original
					if ((edge.v<=homePack.nodeCount && homePack.getVertMark(edge.v)<0) &&
							(edge.w<=homePack.nodeCount && homePack.getVertMark(edge.w)<0))
						return count;
					count += cpCommand(packData,"split_edge "+edge.v+" "+edge.w);
				}
			}
			return count;
		}
		
		// ========= set_rim =======
		else if (cmd.startsWith("set_rim")) {
			if (flagSegs!=null && (items=flagSegs.get(0)).size()>0) {
				NodeLink ilist=new NodeLink(homePack,items);
				Iterator<Integer> ilst=ilist.iterator();
				while(ilst.hasNext()) {
					int i=ilst.next();
					if (i<=iNum) {
						int N=iFlowers[i].length;
						StringBuilder stb=new StringBuilder("set_vlist vlist ");
						for (int j=0;j<N;j++)
							stb.append(iFlowers[i][j]+" ");
						cpCommand(packData,stb.toString());
						count++;
					}
				}
			}
			return count;
		}
		
		// ========= focus ================
		else if (cmd.startsWith("foc")) {
			int i;
			if (flagSegs!=null && (items=flagSegs.get(0)).size()>0 &&
					(i=NodeLink.grab_one_vert(homePack,items.get(0)))>0 &&
					i<=iNum) {  // original vert?
					int N=iFlowers[i].length;
					
					// find centroid of rim centers
					Complex centd=new Complex(0.0);
					for (int j=0;j<N;j++)
						centd=centd.add(packData.getCenter(iFlowers[i][j]));
					centd=centd.divide((double)N);
					
					// find max radius to encircle centers
					double maxR=0.0;
					for (int j=0;j<N;j++) {
						double dist=centd.sub(packData.getCenter(iFlowers[i][j])).abs();
						maxR=(dist>maxR) ? dist:maxR;
					}
					maxR=maxR*2;
					double lx=centd.x-maxR;
					double ux=centd.x+maxR;
					double ly=centd.y-maxR;
					double uy=centd.y+maxR;
					
					cpCommand(packData,"set_screen -b "+lx+" "+ly+" "+ux+" "+uy);
					return 1;
			}

			return 0; 
		}
			
		// ========= deviation =======
		if (cmd.startsWith("dev")) {
			if (flagSegs!=null && (items=flagSegs.get(0)).size()>0) {
				NodeLink ilist=new NodeLink(homePack,items);
				Iterator<Integer> ilst=ilist.iterator();
				while(ilst.hasNext()) {
					int i=ilst.next();
					if (i<=iNum) {
						int []iflower=iFlowers[i];
						double mean=0.0;
						double sqrs=0.0;
						int N=iflower.length;
						for (int j=0;j<N;j++) {
							double r=packData.getRadius(iflower[j]);
							sqrs=sqrs+r*r;
							mean=mean+r;
						}
						mean=mean/(double)N;
						double var=sqrs/N-mean*mean;
						double dev=Math.sqrt(var)/mean; // standard deviation as fraction of mean
						msg("deviation, interstice "+i+" == "+String.format("%f.6",dev)); // report
						count++;
					}
				}
			}
			return count;
		}
		
		// ========== crumb ==============
		else if (cmd.startsWith("crum")) {
			int v=-1;
			if (flagSegs!=null && (items=flagSegs.get(0)).size()>0 &&
					(v=NodeLink.grab_one_vert(packData,items.get(0)))>0 &&
					homePack.getVertMark(v)<0) {  // original vert?
				
				// already have one end
				if (crumb!=null) {
					Vector<Vector<String>> fsegs=new Vector<Vector<String>>(1);
					Vector<String> itms=new Vector<String>(1);
					itms.add(Integer.toString((int)crumb));
					itms.add(Integer.toString(v));
					fsegs.add(itms);
					int rslt=cmdParser("add_edge",fsegs);
					if (rslt>0)
						crumb=null;
				}
				crumb=Integer.valueOf(v);
				return 1;
			}
			return 0;
		}
		
		// ========== add_e ================
		else if (cmd.startsWith("add_e")) {
			if (flagSegs==null || flagSegs.size()==0)
				return 0;
			items=flagSegs.get(0);
			
			int v=-1;
			int w=-1;
			try{
				v=Integer.valueOf(items.get(0));
				w=Integer.valueOf(items.get(1));
			} catch(Exception ex) {
				errorMsg("no edge specified in "+StringUtil.reconItem(items));
				return 0;
			}
				
			// are both ends original vertices?
			if (homePack.getVertMark(v)>=0 || homePack.getVertMark(w)>=0) {
				errorMsg("vertices "+v+" and "+w+" are not original vertices");
				return 0;
			}
			
			if (packData.packDCEL.findHalfEdge(new EdgeSimple(v,w))!=null) {
				errorMsg("vertices "+v+" and "+w+" already share an edge");
				return 0;
			}

			// v and w must share one (and only one) paver
			int bary=0;
			int []vflower=homePack.getPetals(v);
			for (int j=0;j<vflower.length;j++) {
				if (homePack.getVertMark(vflower[j])>=0) { //
					int b=vflower[j];
					if (homePack.packDCEL.findHalfEdge(new EdgeSimple(w,b))!=null) {
						if (bary!=0 && bary!=b) {
							errorMsg("ambiguous: tile bary "+bary+" and "+b);
							return 0;
						}
						bary=b;
					}
				}
			}
			
			// no paver found 
			if (bary==0) 
				return 0;
			
			// save for undo
			PackData holdpack=packData.copyPackTo();

			// add an edge from v to w;
			packData.tileData=null; // toss the old
			if (addEdge(v,w,bary)==null) {
				packData=holdpack;   // restore
				return 0;
			}
			
			// fix the packing
			packData.packDCEL.fixDCEL(packData);
			cpCommand("pave "+bary); // repave
				
			// prepare in case of 'undo'
			addrmPack=holdpack;
			
			return packData.nodeCount;
		} // end of add_e
		
		// ======= rm_e ===================
		else if (cmd.startsWith("rm_e")) {
			if (flagSegs==null || flagSegs.size()==0)
				return 0;
			items=flagSegs.get(0);
			

			// read and process listed edges
			HalfLink hlink=new HalfLink(packData,items);
			if (hlink==null || hlink.size()==0) {
				errorMsg("no edge specified in "+StringUtil.reconItem(items));
				return 0;
			}
			PackData holdpack=null; 
			Iterator<HalfEdge> his=hlink.iterator();
			while (his.hasNext()) {
				HalfEdge edge=his.next();

				int v=edge.origin.vertIndx;
				int w=edge.twin.origin.vertIndx;
				// are both ends original vertices? neighbors?
				if (homePack.getVertMark(v)>=0 || homePack.getVertMark(w)>=0) {
					errorMsg("vertices "+v+" and "+w+" are not original vertices");
					return 0;
				}
			
				// want every network node to have at least three network edges
				int vmark=0;
				int[] flower=packData.getFlower(v);
				int num=packData.countFaces(v);
				for (int j=0;j<num;j++)
					if (homePack.getVertMark(flower[j])<0)
							vmark++;
				int wmark=0;
				flower=packData.getFlower(w);
				num=packData.countFaces(w);
				for (int j=0;j<num;j++)
					if (homePack.getVertMark(flower[j])<0) // original vert?
							wmark++;
				
				if (vmark<3 || wmark<3) {
					errorMsg("Removal failed: end would have < 3 network edges.");
					break;
				}
					
			    // prepare for restore
				int pnum=packData.packNum;
				holdpack=packData.copyPackTo();
				
				// try to remove the edge
				packData.tileData=null;
				int bary=RawManip.rmEdge_raw(packData.packDCEL,edge);
				if (bary>0) {
					packData.packDCEL.fixDCEL(packData);
					cpCommand("pave "+bary); // repave
					addrmPack=packData.copyPackTo();
					count += 1;
				}
				else { // restore 
					CirclePack.cpb.swapPackData(holdpack,pnum,true);
				}
			} // end of while through the list of edges

			if (count==0)
				return 0;
			
			return packData.nodeCount;
		}
		
		// ============== undo =====================
		// TODO: I hope these have pack numbers as usual.
		else if (cmd.startsWith("undo")) {
			if (flagSegs!=null && flagSegs.size()>0) { // call to use 'backpack'
				if (backPack!=null) {
					int pnum=packData.packNum;
					CirclePack.cpb.swapPackData(backPack,pnum,true);
					packData=backPack;
					addrmPack=null; // outdated
					return packData.nodeCount;
				}
				CirclePack.cpb.errMsg("No general backup in place");
			}
			else {
				
				// restore 'addrmPack' if available, else 'backpack' if available
				int pnum=packData.packNum;
				if (addrmPack!=null) { // leave 'backpack' in place
					CirclePack.cpb.swapPackData(addrmPack,pnum,true);
					packData=addrmPack;
					return packData.nodeCount;
				}
				if (backPack!=null) {
					CirclePack.cpb.swapPackData(backPack,pnum,true);
					packData=backPack;
					addrmPack=null; // outdated
					return packData.nodeCount;
				}
				CirclePack.cpb.errMsg("No backups in place");
			}

			return 0;
		}
		
		// ============ backup ================
		else if (cmd.startsWith("backu")) {
			backPack=packData.copyPackTo(); // put a general backup in place
			return 1;
		}
		
		return 0;
	} // end of 'cmdParser'
	
	/**
	 * Add a new edge between original vertices. The edge is from v to w
	 * within the paver with barycenter b. Note that we increase the number
	 * of vertices by 1. Calling routine updates the packing.
	 * @param v int
	 * @param w int
	 * @param b int
	 * @return M on success, 0 on error
	 */
	public HalfEdge addEdge(int v,int w,int b) {
		if (v==w || homePack.getVertMark(v)>=0 || homePack.getVertMark(w)>=0 || 
				homePack.getVertMark(b)<0 || packData.isBdry(b))
			return null;
		
		PackDCEL pdcel=packData.packDCEL;
		// if red chain runs through 'b', then toss it
		if (pdcel.vertices[b].redFlag)
			pdcel.redChain=null;
		
		// first split the flower at b, giving new vertex
		//   and edge connecting it to b
		HalfEdge vedge=pdcel.findHalfEdge(new EdgeSimple(b,v));
		HalfEdge wedge=pdcel.findHalfEdge(new EdgeSimple(b,w));
		if (vedge==null || wedge==null)
			return null;
		HalfEdge tmpEdge=RawManip.splitFlower_raw(pdcel,vedge,wedge);
		if (tmpEdge==null)
			return null;

		// now flip that new edge
		HalfEdge newEdge=RawManip.flipEdge_raw(pdcel,tmpEdge);
		return newEdge;
	}

	public void initCmdStruct() {
		super.initCmdStruct();
		cmdStruct.add(new CmdStruct("add_edge","{v w}",null,"Add an edge between original vertices across a face."));
		cmdStruct.add(new CmdStruct("rm_edge","{v w}",null,"Remove an edge between original vertices."));
		cmdStruct.add(new CmdStruct("undo","[old],[orig]",null,"Replace packing with latest Add/Remove "+
				"backup, optionally with 'backPack' or 'homePack'."));
		cmdStruct.add(new CmdStruct("backup",null,null,"Put aside a general backup packing"));
		cmdStruct.add(new CmdStruct("crumb","v",null,"Save original vertex as a crumb: second one cause an "+
				"attempt to add and edge."));
		cmdStruct.add(new CmdStruct("set_rim","i",null,"append rim of an interstice to 'vlist'."));
		cmdStruct.add(new CmdStruct("dev","i ...",null,"output standard deviation/mean for radii of circles "+
				"around an interstice."));
		cmdStruct.add(new CmdStruct("hist",null,null,"output histogram of current radii."));
		cmdStruct.add(new CmdStruct("focus","i",null,"set the screen to focus on interstice i"));
		cmdStruct.add(new CmdStruct("split","e..",null,"split edge (insert new vert) for edges 'e' which "+
				"at least one non-original end"));

	}
	
}
