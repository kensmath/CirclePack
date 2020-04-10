package ftnTheory;

import java.util.Iterator;
import java.util.Vector;

import complex.Complex;

import allMains.CirclePack;
import exceptions.CombException;
import komplex.EdgeSimple;
import komplex.KData;
import listManip.EdgeLink;
import listManip.NodeLink;
import packing.PackData;
import packing.PackExtender;
import panels.CPScreen;
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
			if (packData.kData[v].mark==1) {
				markCount++;
			}
		
		// small checks on data
		if (markCount==0 || markCount==packData.nodeCount) {
			errorMsg("'JammedPack' extender failed: packing no or all vertices marked");
			running=false;
		}
		
		iNum=packData.nodeCount-markCount;
		homePack=packData.copyPackTo(); // reference, never changes
		iFlowers=new int[iNum+1][]; // indexed from 1
		istices=new int[iNum+1];
		int tick=0;
		
		// mark the vertices, list interstices and their 'rim' flowers
		for (int v=1;v<=homePack.nodeCount;v++) { 
			if (homePack.kData[v].mark==1) {
				homePack.kData[v].mark=-1; // mark=-1 for original vertex
			}
			else if (homePack.kData[v].num>3) {   // only degree > 3
				istices[++tick]=v;
				homePack.kData[v].mark=tick; // index of interstice
				iFlowers[tick]=new int[homePack.kData[v].num+1];
				for (int j=0;j<=homePack.kData[v].num;j++)
					iFlowers[tick][j]=homePack.kData[v].flower[j];
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
					if (packData.kData[edge.v].num==3 || packData.kData[edge.w].num==3)
						return count;
					// both can't be original
					if ((edge.v<=homePack.nodeCount && homePack.kData[edge.v].mark<0) &&
							(edge.w<=homePack.nodeCount && homePack.kData[edge.w].mark<0))
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
						centd=centd.add(packData.rData[iFlowers[i][j]].center);
					centd=centd.divide((double)N);
					
					// find max radius to encircle centers
					double maxR=0.0;
					for (int j=0;j<N;j++) {
						double dist=centd.sub(packData.rData[iFlowers[i][j]].center).abs();
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
							double r=packData.rData[iflower[j]].rad;
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
					homePack.kData[v].mark<0) {  // original vert?
				
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
			if (homePack.kData[v].mark>=0 || homePack.kData[w].mark>=0) {
				errorMsg("vertices "+v+" and "+w+" are not original vertices");
				return 0;
			}
			
			if (packData.nghb(v,w)>=0) {
				errorMsg("vertices "+v+" and "+w+" already share an edge");
				return 0;
			}

			// v and w must share one (and only one) paver
			int bary=0;
			int []vflower=homePack.kData[v].flower;
			for (int j=0;j<homePack.kData[v].num;j++) {
				if (homePack.kData[vflower[j]].mark>=0) { //
					int b=vflower[j];
					if (homePack.nghb(w,b)>=0) {
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
			packData.tileData=null;
			if (addEdge(v,w,bary)==0) {
				packData=holdpack;   // restore
				return 0;
			}
			
			// fix the packing
			packData.setCombinatorics();
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
			EdgeLink elink=new EdgeLink(packData,items);
			if (elink==null || elink.size()==0) {
				errorMsg("no edge specified in "+StringUtil.reconItem(items));
				return 0;
			}
			PackData holdpack=null; 
			Iterator<EdgeSimple> elst=elink.iterator();
			while (elst.hasNext()) {
				EdgeSimple edge=elst.next();

				int v=edge.v;
				int w=edge.w;
				// are both ends original vertices? neighbors?
				if (homePack.kData[v].mark>=0 || homePack.kData[w].mark>=0) {
					errorMsg("vertices "+v+" and "+w+" are not original vertices");
					return 0;
				}
				int vw_indx=packData.nghb(v,w);
				if (vw_indx<0 || (vw_indx==0 && packData.kData[v].bdryFlag==1)) {
					errorMsg("vertices "+v+", "+w+" not neighbors or this is bdry edge");
					break;
				}
			
				// we want every network node to have at least three network edges
				int vmark=0;
				int []flower=packData.kData[v].flower;
				int num=packData.kData[v].num;
				for (int j=0;j<num;j++)
					if (homePack.kData[flower[j]].mark<0)
							vmark++;
				int wmark=0;
				flower=packData.kData[w].flower;
				num=packData.kData[w].num;
				for (int j=0;j<num;j++)
					if (homePack.kData[flower[j]].mark<0) // original vert?
							wmark++;
				
				if (vmark<3 || wmark<3) {
					errorMsg("Removal failed: end would have < 3 network edges.");
					break;
				}
					
			    // prepare for restore
				CPScreen cps=packData.cpScreen; 
				holdpack=packData.copyPackTo();
				
				// try to remove the edge
				packData.tileData=null;
				int bary=removeEdge(v,w);
				if (bary>0) {
					packData.setCombinatorics(); // fix packing
					cpCommand("pave "+bary); // repave
					addrmPack=packData.copyPackTo();
					count += 1;
				}
				else { // restore 
					cps.swapPackData(holdpack,true);
				}
			} // end of while through the list of edges

			if (count==0)
				return 0;
			
			return packData.nodeCount;
		}
		
		// ============== undo =====================
		else if (cmd.startsWith("undo")) {
			if (flagSegs!=null && flagSegs.size()>0) { // call to use 'backpack'
				if (backPack!=null) {
					CPScreen cps=packData.cpScreen; 
					cps.swapPackData(backPack,true);
					packData=cps.packData;
					addrmPack=null; // outdated
					return packData.nodeCount;
				}
				CirclePack.cpb.errMsg("No general backup in place");
			}
			else {
				
				// restore 'addrmPack' if available, else 'backpack' if available
				if (addrmPack!=null) { // leave 'backpack' in place
					CPScreen cps=packData.cpScreen; 
					cps.swapPackData(addrmPack,true);
					packData=cps.packData;
					return packData.nodeCount;
				}
				if (backPack!=null) {
					CPScreen cps=packData.cpScreen; 
					cps.swapPackData(backPack,true);
					packData=cps.packData;
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
	 * Remove an edge between original vertices; legality has been checked.
	 * Calling routine must update the packing.
	 * @param v int
	 * @param w int
	 * @return 0 on error, bary center 'bary' on success
	 */
	public int removeEdge(int v,int w) {

		int vw_indx=packData.nghb(v,w);
		int wv_indx=packData.nghb(w,v);
		int vtogo=packData.kData[w].flower[wv_indx+1]; // common nghb to remove, right of <v,w>
		int bary=packData.kData[v].flower[vw_indx+1]; // common nghb to keep, left of <v,w>
		
		// we will remove 'vtogo', so swap it for max 'M' 
		int M=packData.nodeCount;
		if (cpCommand("swap "+vtogo+" "+M)<=0) {
			errorMsg("error in swappng vertices");
			return 0;
		}

		// proceed to fix things up: 'bary' first
		KData []kData=packData.kData; // update

		// some petals for 'bary' will come from those of 'M'
		int numM=kData[M].num;
		int M2v=packData.nghb(M,v);
		int M2w=packData.nghb(M,w);
		int Mcount=(M2w-M2v+numM)%numM;
		int []Mhalf=new int[Mcount];
		
		// list cclw portion of 'M's flower, [v,w)
		for (int j=0;j<Mcount;j++)
			Mhalf[j]=kData[M].flower[(M2v+j)%numM];

		// rest from flower of 'bary', cclw [w, v) 
		int numbary=kData[bary].num;
		int b2v=packData.nghb(bary,v);
		int b2w=packData.nghb(bary,w);
		int bcount=(b2v-b2w+numbary)%numbary;
		int petalcount=bcount+Mcount;
		int []newbflower=new int[petalcount+1];
		for (int j=0;j<Mcount;j++)
			newbflower[j]=Mhalf[j];
		for (int j=0;j<bcount;j++)
			newbflower[j+Mcount]=kData[bary].flower[(b2w+j+numbary)%numbary];
		newbflower[petalcount]=newbflower[0]; // close up
		kData[bary].flower=newbflower;
		kData[bary].num=petalcount;

		// replace 'M' by 'bary' in petal flowers of petals (v,w)
		for (int j=1;j<Mcount;j++) {
			int ptl=Mhalf[j];
			int ptlnum=kData[ptl].num;
			for (int pj=0;pj<=ptlnum;pj++) 
				if (kData[ptl].flower[pj]==M)
					kData[ptl].flower[pj]=bary;
		}
		
		// fix v flower; remove 'M' and 'w'
		int numv=kData[v].num;
		int []vflower=kData[v].flower;
		NodeLink newflower=new NodeLink(packData);
		for (int j=0;j<numv;j++) {
			int u=vflower[j];
			if (u!=M && u!=w) 
				newflower.add(u);
		}
		vflower=new int[numv-1];
		Iterator<Integer> nit=newflower.iterator();
		int tick=0;
		while(nit.hasNext())
			vflower[tick++]=(int)nit.next();
		vflower[tick]=vflower[0]; // close up
		packData.kData[v].flower=vflower;
		packData.kData[v].num=tick;
		
		// fix w flower; remove 'M' and 'v'
		int numw=kData[w].num;
		int []wflower=kData[w].flower;
		newflower=new NodeLink(packData);
		for (int j=0;j<numw;j++) {
			int u=wflower[j];
			if (u!=M && u!=v) 
				newflower.add(u);
		}
		wflower=new int[numw-1];
		nit=newflower.iterator();
		tick=0;
		while(nit.hasNext())
			wflower[tick++]=(int)nit.next();
		wflower[tick]=wflower[0]; // close up
		kData[w].flower=wflower;
		kData[w].num=tick;			

		// remove 'M'
		packData.nodeCount--;
		
		return bary;
	}
	
	/**
	 * Add a new edge between original vertices. The edge is from v to w
	 * within the paver with barycenter b. Note that we increase the number
	 * of vertices by 1. Calling routine updates the packing
	 * @param v int
	 * @param w int
	 * @param b int
	 * @return M on success, 0 on error
	 */
	public int addEdge(int v,int w,int b) {
		KData []kData=packData.kData;
		if (v==w || homePack.kData[v].mark>=0 || homePack.kData[w].mark>=0 || 
				homePack.kData[b].mark<0 || kData[b].bdryFlag!=0)
			return 0;
		int b2v=-1;
		int b2w=-1;
		if ((b2v=packData.nghb(b, v))<0 || (b2w=packData.nghb(b, w))<0)
			return 0;
		int num=kData[b].num;
		int []bflower=kData[b].flower;
		
		// make sure there is space for new vertex
		int M=packData.nodeCount+1;
		if (M > (packData.sizeLimit)
				&& packData.alloc_pack_space(M, true) == 0) {
			throw new CombException("Pack space allocation failure");
		}
		
		// create the new vertex 'M'
		int numM=(b2w-b2v+num)%num+1;
		KData new_kData=new KData();
		new_kData.num=numM;
		new_kData.bdryFlag=0;
		new_kData.flower=new int[numM+1];
		for (int j=0;j<numM;j++)
			new_kData.flower[j]=bflower[(b2v+j)%numM];
		new_kData.flower[numM]=new_kData.flower[0]; // close up
		packData.nodeCount=M;
		kData[M]=new_kData;
		packData.rData[M]=packData.rData[b].clone();
		
		// fix petals now next to 'M'
		int vwdiff=(b2w-b2v-1+num)%num;
		for (int j=0;j<vwdiff;j++) {
			int u=bflower[(b2v+j+1)%num];
			int numu=kData[u].num;
			for (int jj=0;jj<=numu;jj++)
				if (kData[u].flower[jj]==b)
					kData[u].flower[jj]=M;
		}
		
		// fix 'v'; add 'M' and 'w'
		int numv=kData[v].num;
		int v2b=packData.nghb(v,b);
		int []vflower=new int[numv+2+1];
		if (v2b==0) { // insert at end; 'v' must be interior
			for (int jj=0;jj<numv;jj++)
				vflower[jj]=kData[v].flower[jj];
			vflower[numv]=M;
			vflower[numv+1]=w;
			vflower[numv+2]=b; // close up again
		}
		else {
			for (int jj=0;jj<v2b;jj++) 
				vflower[jj]=kData[v].flower[jj];
			vflower[v2b]=M;
			vflower[v2b+1]=b;
			for (int jj=v2b;jj<=numv;jj++)
				vflower[jj+2]=kData[v].flower[jj];
		}
		kData[v].flower=vflower;
		kData[v].num=numv+2;

		// fix 'w'; add 'M' and 'v'
		int numw=kData[w].num;
		int w2b=packData.nghb(w,b);
		int []wflower=new int[numw+2+1];
		for (int jj=0;jj<=w2b;jj++)
			wflower[jj]=kData[w].flower[jj];
		wflower[w2b+1]=v;
		wflower[w2b+2]=M;
		for (int jj=w2b+1;jj<=numw;jj++)
			wflower[jj+2]=kData[w].flower[jj];
		kData[w].flower=wflower;
		kData[w].num=numw+2;
	
		// fix data for 'b'
		int numb=(b2v-b2w+num)%num+1;
		kData[b].num=numb;
		kData[b].flower=new int[numb+1];
		for (int j=0;j<numb;j++)
			kData[b].flower[j]=bflower[(b2w+j)%num];
		kData[b].flower[numb]=kData[b].flower[0]; // close up
		
		return M;
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
