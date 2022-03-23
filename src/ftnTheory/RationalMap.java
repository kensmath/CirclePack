package ftnTheory;

import java.io.BufferedReader;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.StringTokenizer;
import java.util.Vector;

import allMains.CPBase;
import allMains.CirclePack;
import combinatorics.komplex.HalfEdge;
import combinatorics.komplex.Vertex;
import complex.Complex;
import dcel.CombDCEL;
import dcel.PackDCEL;
import exceptions.CombException;
import exceptions.DataException;
import exceptions.InOutException;
import exceptions.ParserException;
import input.CPFileManager;
import komplex.EdgeSimple;
import listManip.EdgeLink;
import listManip.HalfLink;
import listManip.NodeLink;
import listManip.VertexMap;
import packing.PackData;
import packing.PackExtender;
import util.CmdStruct;
import util.EdgeSeg;
import util.StringUtil;

/**
 * 'RationalMap' is used to construct discrete meromorphic mappings
 * on compact surfaces based on given 'slit' and 'pasting' 
 * instructions. (It was started in response to inquiries by Laurent 
 * Bartholdi about generating discrete approximations to rational 
 * maps using Hurwitz data for the pastings, with the aim of studying 
 * Thurston obstructions and post-critically finite maps of the sphere.)
 * 
 * The construction is of "domain" type: i.e., it starts with an
 * image packing P, cuts it open and pastes together several copies
 * to form the domain complex. Max packing of that gives the domain
 * packing Q. Identifications of domain vertices with their image
 * vertices in P must be maintained during construction, so the
 * final 'vertexMap' of Q contains the map f: Q --> P.
 * 
 * Initial mode will involve using the original packing (stored
 * for backup) as 'rangePack'. The slits to be used for construction
 * are described using slit and sheet numbers. The slits are encoded
 * via a "tree" in the sphere whose nodes are the potential branch
 * values. The slits are made in every sheet of the intended domain;
 * in general, slits may get repasted without being used in the
 * construction. Each slit results in two 'EdgeSeg's; numbering starts
 * with 1, and paired segments have consecutive numbers. Here's the
 * procedure:
 * 
 *    Suppose T is the tree. Proceed counterclockwise around the 
 *    tree starting with some node N (must start from a 'leaf' 
 *    node, the end of a branch). The first slit starts at N and 
 *    extends to the next node of the tree counterclockwise from 
 *    outside T. NOTE, however, that all edge segments are CLOCKWISE
 *    <startV, endV> w.r.t. the bdry of the opened complex. Now 
 *    proceed counterclockwise around T, and each time a new edge
 *    of T is encountered (one not yet slit from the other side), 
 *    the next two numbered edge segments are created. Note: each
 *    edge segment must have at least 3 vertices, as we need to 
 *    use the second clw vertex from the start of the edge to
 *    properly identify the new vertex numbers of its opposite
 *    edge. 
 * 
 * There is also a numbering of the sheets to be used in the 
 * construction, so eventually, edge segments are indexed using
 * 'sheetNumber' and 'slitNumber'. The total number and indexing
 * of the sheets is determined by the user. Indices must be positive,
 * but are not necessarily sequential; the 'baseSheet' is taken as
 * the sheet having the smallest index.
 *  
 * Data input format (from *.rmd file or in script data section):
 * 
 * 	SLITCOUNT: <n>
 * 		x1 y1   u1 v1	(complex endpoints of the slit: these 
 * 						are spherical, (theta,phi); delineated 
 * 						by linebreak)
 *      ..
 *      xn yn   un vn
 *      
 *      (alternately, as vertex lists
 *      v11 v12 ... v1k
 *      ..
 *      vn1 vn2 ... vnj)
 *      
 *  PASTECOUNT: <k>
 *      ss1 se1 ts1 te1 (source sheet/edge, target sheet/edge)
 *      ss2 se2 ts2 te2 (delineated by linebreak)  
 *      ..
 *      ssk sek tsk tek 
 *  END
 *  
 * After reading the data, 'rangePack' is slit open to create 'slitPack'.
 * The slits may be given as vertex list, otherwise the endpoints are given
 * as spherical points (theta,phi) and the end vertices are the circles of 
 * P nearest to these points and the slits are computed automatically as 
 * combinatorial geodesics between them (which could be a problem if the 
 * combinatorics are too coarse, branching at a vertex is too high, or 
 * local geodesics coincide as they enter end vertex). Copies of 'slitPack' 
 * comprise the various sheets attached successively to 'domainPack'. 
 * When all prescribed pastings are done, default pastings (which should 
 * just be the reclosing of un-used slits on the same sheet) are done, and
 * the final result lies in 'domainPack'. This can be max_packed by the user 
 * to create the domain packing. The 'vertexMap' of 'domainPack' should 
 * indicate where each vertex maps in 'rangePack'. Also, the vertex 
 * 'mark' data in 'domainPack' reflects the sheet numbers of the vertices.
 * 
 * @author kens, started April 2008
 */

public class RationalMap extends PackExtender {

	final int ERROR=0;
	final int RANGE=1;
	final int RM_DATA=2;
	final int SLIT=3;
	final int BUILT=4;
	public String []stateStr={"ERROR","RANGE","RM_DATA","SLIT","BUILT"};
	public int rmState;  // current state in processing

	PackData rangePack; // range packing (copy of original parent packing)
	PackData slitPack; // range packing with introduced slits
	PackData domainPack; // growing pasted packing; will contain result
	ArrayList<HalfLink> slitLinks;  // slits using edges of 'slitPack' before
									//  it is slit open. Only needed until
									//  'slits' is set up.  
	LinkedList<EdgeSeg> slits; // permanent list of slits, used on all sheets
	Vector<NodeLink> vlists; // lists of vertices defining the slits
	int numSlits; // number of slits; identical for all sheets
	int numSheets; // number of sheets in final packing
	int baseSheet; // index of the 'base' sheet; others are pasted to this
	int numCodes; // number of pasting codes specified
	int pasteCount; // count of pairs of edge segments that have been pasted
	int []sheetCheck; // initially 0; 1 if in a PasteCode; 
					  // -1 when attached to base
	LinkedList<PasteCode> pasteCodes; // ss se ts te sourcesheet/edge 
									  // targetsheet/edge
	LinkedList<EdgeSeg> masterESlist; // master list of unpasted 
									  // edge segments
	public VertexMap masterMap; // latest map from 'domainPack' to 'rangePack'
	public VertexMap slitMap; // persistent 'VertexMap' from 'slitPack' 
							  // to 'rangePack'
	public NodeLink branchList; // vertices in 'rangePack' at ends of slits
	
	// Constructor
	public RationalMap(PackData p) {
		super(p);
		packData=p;
		extensionType="RATIONAL_MAP";
		extensionAbbrev="RM";
		toolTip="'RationalMap' builds discrete meromorphic mappings "+
		"from branch values and tree branch structure";
		registerXType();
		
		numSlits=numSheets=baseSheet=numCodes=pasteCount=0;
		
		// parent packing 'rangePack' and also utility 'slitPack',
		rangePack=packData.copyPackTo();
		slitPack=packData.copyPackTo();
		
		// init 'slitMap': tracks original vertex index from 'rangePack'
		slitPack.vertexMap=new VertexMap();
		for (int v=1;v<=packData.nodeCount;v++) {
			slitPack.vertexMap.add(new EdgeSimple(v,v));
		}
		slitMap=slitPack.vertexMap.makeCopy();
		
		// seems okay?
		if (rangePack!=null)
			rmState=RANGE;
		else rmState=ERROR;
		branchList=null;
		packData.packExtensions.add(this);
	}
	
	public int cmdParser(String cmd,Vector<Vector<String>> flagSegs) {
		Vector<String> items=null;

		// ============= read_data, infile_data
		// read a file containing slit lists and sheet pastings
		if (cmd.startsWith("read_da") || cmd.startsWith("infile_da")) { 
			CPBase.Elink=new EdgeLink();
			if (rmState!=RANGE) {
				CirclePack.cpb.errMsg("Can't read data; not in RANGE state");
				return 0;
			}
			
			try {
				items=(Vector<String>)flagSegs.get(0);
				String filename=StringUtil.reconItem(items);
				boolean infile_flag=false;
				File dir=null;
				if (cmd.charAt(0)=='i')
					infile_flag=true;
				else 
					dir=CPFileManager.PackingDirectory;
				BufferedReader fp=
					CPFileManager.openReadFP(dir,filename,infile_flag);
				if (fp==null) { 
					CirclePack.cpb.errMsg("failed to open "+filename+
							" in directory "+dir.toString());
					return 0;
				}
				
				// process the slit/sheet data
				if (readRMData(fp)==0) {
					CirclePack.cpb.errMsg("failed to read 'vlists' from "+
							filename);
					rmState=ERROR;
					fp.close();
					return 0;
				}
				fp.close();
				rmState=RM_DATA;
			} catch (Exception ex) {
				throw new InOutException("failed in looking for slit lists");
			}

			// Process sheet info: number of sheets, 'baseSheet' 
			//   (lowest number), 'topSheet', initial 'sheetCheck' 
			//   for keeping track of existing/used sheets
			numSheets=0;
			int topSheet=0;
			baseSheet=((PasteCode)pasteCodes.get(0)).sourceSheet; // initiate
			Iterator<PasteCode> pclist=pasteCodes.iterator();
			while (pclist.hasNext()) {
				PasteCode pc=(PasteCode)pclist.next();
				baseSheet=(pc.sourceSheet<baseSheet) ? 
						pc.sourceSheet : baseSheet;
				baseSheet=(pc.targetSheet<baseSheet) ? 
						pc.targetSheet : baseSheet;
				topSheet=(pc.sourceSheet>topSheet) ? 
						pc.sourceSheet : topSheet;
				topSheet=(pc.targetSheet>topSheet) ? 
						pc.targetSheet : topSheet;
			}

			sheetCheck=new int[topSheet+1]; // list sheets that occur
			for (int i=1;i<=topSheet;i++) 
				sheetCheck[i]=0;
			
			pclist=pasteCodes.iterator();
			while (pclist.hasNext()) {
				PasteCode pc=(PasteCode)pclist.next();
				if (sheetCheck[pc.sourceSheet]==0) {
					sheetCheck[pc.sourceSheet]=1;
					numSheets++;
				}
				if (sheetCheck[pc.targetSheet]==0) {
					sheetCheck[pc.targetSheet]=1;
					numSheets++;
				}
			}
			
			msg("read data: "+numSlits+" slits, "+
					numCodes+" pastings, "+numSheets+" sheets");
			return numSheets;
		}
		
		// ========== doSlits ====================
		if (cmd.startsWith("doSli")) { // apply the slits to slitPack
			if (rmState!=RM_DATA) {
				throw new ParserException(
						"Can't doSlits: not in RM_DATA state");
			}
			
			// first have to process the slits, currently stored
			//   only in 'slitLinks'. 
			//  + accumulate all as "forbidden" edges.
			//  + create 'slitPack' via 'extractDCEL'
			//  + call to 'catalogSlits', which needs 'slitLinks'
			// all sheets are copies of 'slitPack'.

			try {
				
				// accumulate all the slit list vertices
				HalfLink forbid=new HalfLink();
				CPBase.Elink=new EdgeLink(); // for debugging
				Iterator<HalfLink> his=slitLinks.iterator();
				while (his.hasNext()) {
					HalfLink hlk=his.next();
					forbid.abutMore(hlk);
					CPBase.Elink.abutHalfLink(hlk); // for debugging
				}
				
				// extract 'slitPack'
				PackDCEL pdcel=CombDCEL.extractDCEL(slitPack.packDCEL,
					forbid,slitPack.packDCEL.alpha);
				VertexMap holdvm=pdcel.oldNew;
				slitPack.attachDCEL(pdcel);
				slitPack.vertexMap=holdvm;
				pdcel.layoutPacking();
				
// debugging, copy into pack[2]				
//			PackData debugPack=slitPack.copyPackTo();
//			CirclePack.cpb.swapPackData(debugPack, 2, false);
			
			} catch(Exception ex) {
				rmState=ERROR;
				errorMsg("RM: error in creating slitPack");
				return 0;
			}

			// seems okay: 'domainPack' is the base sheet to which 
			//    we attach others
			domainPack=slitPack.copyPackTo();
			sheetCheck[baseSheet]=-1; // always indicated as 'attached'
			for (int v=1;v<=domainPack.nodeCount;v++) // 'mark'= sheet index
				domainPack.setVertMark(v,baseSheet);
			
			// arrange the 'EdgeSeg's for the various slits.
			try {
				catalogSlits();
			} catch(Exception ex) {
				rmState=ERROR;
				errorMsg("RM: error in arranging the 'EdgeSeg's");
				return 0;
			}
			
			// initiate master list of edge segments
			masterESlist=new LinkedList<EdgeSeg>();
			Iterator<EdgeSeg> esl=slits.iterator();
			while (esl.hasNext()) {
				EdgeSeg es=(EdgeSeg)esl.next();
				masterESlist.add(es.clone(baseSheet));
			}
			pasteCount=0;

			// initiate 'masterMap' to keep track of vertex images
			masterMap=slitMap.makeCopy();
			
			rmState=SLIT; 
			return masterESlist.size();
		}

		// ============= paste ======================
		// set_[EV]list with specified slit(s)
		if (cmd.startsWith("paste")) { 
			// do all the pastings, including default
			if (rmState!=SLIT) {
				throw new ParserException("Can't paste: not in SLIT state");
			}
			int pcount=0;
			while (rmState<BUILT && pasteNext()>0) 
				pcount++;
			
			if (domainPack.getBdryCompCount()!=0) {
				errorMsg("RM: don't seem able to complete all pastings");
				rmState=ERROR;
			}
			else {
				rmState=BUILT;
				domainPack.status=true;
				domainPack.hes=1;
				domainPack.set_aim_default();
				domainPack.vertexMap=masterMap.makeCopy();
				msg("Build succeeded.");
			}
			return pcount;
		}

		// ========== status
		else if (cmd.startsWith("status")) {
			msg("|RM| is currently in '"+stateStr[rmState]+"' state");
			return 1;
		}
		
		// ========== slit_[EV] {n ..}
		// set_[EV]list with specified slit(s)
		else if (cmd.startsWith("slit_")) { 
			char c='V';
			if (rmState<RM_DATA || cmd.length()<6 ||
					((c=cmd.charAt(5))!='V' && c!='E')) 
				return 0;
			
			// put desired slit numbers in a string
			String numbs=null;
			if (flagSegs==null || flagSegs.size()==0) {
				StringBuilder tmp=new StringBuilder();
				for (int i=1;i<=numSlits;i++)
					tmp.append(" "+i);
				numbs=tmp.toString();
			}
			else {
				items=(Vector<String>)flagSegs.get(0);
				numbs=items.get(0);
			}
			
			// create edge list
			NodeLink Vlink=new NodeLink(rangePack);
			EdgeLink Elink=new EdgeLink(rangePack);
			StringTokenizer tok=new StringTokenizer(numbs);
			int count=0;
			while (tok.hasMoreTokens()) {
				int n=Integer.parseInt(tok.nextToken());
				if (n>0 && n<=numSlits) {
					if (c=='E') {
						Elink.abutMore(
							EdgeLink.verts2edges(rangePack.packDCEL,
								vlists.get(n),false));
					}
					else {
						Vlink.abutMore(vlists.get(n));
					}
					count++;
				}
			}
			
			// put results in global lists
			if (c=='E' && Elink.size()>0) {
				CPBase.Elink=Elink;
				return count;
			}
			else if (Vlink.size()>0) {
				CPBase.Vlink=Vlink;
				return count;
			}
			return 0;
		}
		
		// =============== paste_next ================
		// paste the next segments in the list
		if (cmd.startsWith("paste_n")) { 
			if (rmState>SLIT) {
				throw new ParserException(
						"Can't paste_next: not in SLIT state");
			}
			if (pasteCodes.size()==0) {
				CirclePack.cpb.msg("all paste codes seem to have been used");
				return 0;
			}
			int ans=pasteNext();
			if (ans<=0) {
				rmState=ERROR;
				throw new ParserException("failed next pasting");
			}
			return pasteCount;
		}
		
		// ========== branchValues
		// global 'Vlist' to branch image vertices 
		if (cmd.startsWith("branchVal")) { 
			if (rmState<RM_DATA) return 0;
			CPBase.Vlink=new NodeLink((PackData)null);
			return CPBase.Vlink.abutMore(branchList);
		}
		
		// =========== find[zoib]
		// get lists of verts mapping to zero/one/infty
		if (cmd.startsWith("find")) { 
			if (rmState<BUILT) {
				errorMsg("image packing not yet in BUILT state");
				return 0;
			}
			
			char c=cmd.charAt(4);
			NodeLink vlist=null;
			
			// else looking for zeros/ones/infinities
			String zoib=null;
			switch(c) {
			case 'z': // zero verts
			{
				zoib=new String(" zero ");
				vlist=new NodeLink(packData,"Z 0.0 0.0");
				break;
			}
			case 'o': // one verts
			{
				zoib=new String(" one ");
				vlist=new NodeLink(packData,"Z 0.0 "+Math.PI/2.0);
				break;
			}
			case 'i': // infinity verts
			{
				zoib=new String(" infinity ");
				vlist=new NodeLink(packData,"Z 0.0 "+Math.PI);
				break;
			}
			case 'b': // branch vertices and siblings
			{
				zoib=new String(" branch points ");
				vlist=branchList.makeCopy();
				break;
			}
			} // end of switch

			vlist = EdgeLink.findAllV(domainPack.vertexMap,vlist);
			if (vlist==null || vlist.size()==0) {
				errorMsg("no circles found");
				return 0;
			}
			CPBase.Vlink=vlist;
			msg("The "+vlist.size()+" vertices lying over"+zoib+"are in Vlist");
			return vlist.size();
		}
		
		// ========== copy <pnum> 
		if (cmd.startsWith("copy")) { // copy 'domainPack' somewhere 
									  // (e.g. for inspection)
			if (rmState==ERROR) {
				CirclePack.cpb.errMsg("can't copy; packing is in ERROR state");
				return 0;
			}
			try {
				items=(Vector<String>)flagSegs.get(0);
				int pnum=Integer.parseInt((String)items.get(0));
				CirclePack.cpb.swapPackData(domainPack,pnum,false);
				domainPack.vertexMap=masterMap.makeCopy();
			} catch (Exception ex) {
				return 0;
			}
			return 1;
		}
		
		// ============= dbs
		if (cmd.startsWith("dbs")) { // debug info on current edge segments
			if (displayES()==0)
				CirclePack.cpb.errMsg("Some problem among RM edge segments");
			return 1;
		}

		return super.cmdParser(cmd, flagSegs);
	}
	
	/**
	 * Look for and paste the next pasting in 'pasteCodes', if there
	 * is one, else the next "default" pasting (two sides of same slit 
	 * on same sheet); otherwise, set ERROR or BUILT state.
	 * @return -1 on error
	 */
	public int pasteNext() {
		boolean debug=false; // true
		PasteCode pc=nextPasteCode();
		
		// no remaining 'PasteCode'? ERROR or BUILT?
		if (pc==null) {
			if (domainPack.getBdryCompCount()>0) {
				msg("RM: all pastings done, but the boundary is non empty");
				rmState=ERROR;
				return -1;
			}
			msg("We seem to have done all the pastings");
			rmState=BUILT;
			domainPack.status=true;
			domainPack.hes=1;
			domainPack.set_aim_default();
			domainPack.vertexMap=masterMap.makeCopy();
			return 1;
		}
		
		// debug
		if (debug) {
			displayES();
			msg("Try next pasteCode: <ss,sc,ts,tc>: "+
					pc.sourceSheet+" "+pc.sourceEdge+
					" "+pc.targetSheet+" "+pc.targetEdge);
		}

		// both sheets already attached to base? 'edgeSeg's 
		//    should be in 'masterESlist'
		if (sheetCheck[pc.sourceSheet]<0 && sheetCheck[pc.targetSheet]<0) {
			EdgeSeg sES=findMasterES(pc.sourceSheet,pc.sourceEdge);
			EdgeSeg tES=findMasterES(pc.targetSheet,pc.targetEdge);
			if (pasteEm(sES,tES)>0) {
				pasteCount++;
				masterMap=VertexMap.followedBy(domainPack.vertexMap,
						masterMap);
				domainPack.vertexMap=masterMap.makeCopy();
				if (debug) 
					msg("success (target sheet already attached)");
			}
			else if (debug) {
				msg("failed with this pasteCode");
				return -1;
			}
		}
			
		// one sheet is attached to base, one isn't
		else if (sheetCheck[pc.sourceSheet]*sheetCheck[pc.targetSheet]<0) {
			EdgeSeg aES; // the one attached
			int toSlit;  // the slit number for the unattached sheet
			int newSheetNum; // the number of this new sheet
			if (sheetCheck[pc.sourceSheet]<0 && 
					sheetCheck[pc.targetSheet]>0) { 
				aES=findMasterES(pc.sourceSheet,pc.sourceEdge);
				toSlit=pc.targetEdge;
				newSheetNum=pc.targetSheet;
			}
			else {
				aES=findMasterES(pc.targetSheet,pc.targetEdge);
				toSlit=pc.sourceEdge;
				newSheetNum=pc.sourceSheet;
			}
			
			// do the attachment
			if (attachIt(aES,newSheetNum,toSlit)>0) { 
				pasteCount++;
				sheetCheck[newSheetNum]=-1;
				if (debug) 
					msg("New sheet "+newSheetNum+
							" attached via slit "+toSlit);
			}
			else if (debug) {
				msg("Failed to attach new sheet "+newSheetNum);
				return -1;
			}
		}
		if (pasteCodes.indexOf((Object)pc)>=0) {
//			if (debug) msg("remove this pasteCode.");
			pasteCodes.remove(pc);
		}
		
		// are we done?
		if (domainPack.getBdryCompCount()==0) { 
			rmState=BUILT;
			domainPack.status=true;
			domainPack.hes=1;
			domainPack.set_aim_default();
			domainPack.vertexMap=masterMap.makeCopy();
		}
		return 1;
	}
	
	/** 
	 * In domainPack, self-adjoin the two given segments, remove them 
	 * from 'masterESlist', and adjust indices in remaining edge segments.
	 */
	public int pasteEm(EdgeSeg es1,EdgeSeg es2) {
		if (es1==null || es2==null) return 0;
		int length=1;
		int v=es1.endV;
		int next=domainPack.getFirstPetal(v);
		int safety=domainPack.nodeCount;
		while (next!=es1.startV && next!=es1.endV && safety>0) {
			v=next;
			next=domainPack.getFirstPetal(v);
			length++;
			safety--;
		}
		if (safety<=0 || next==es1.endV) 
			return 0;
		domainPack.packDCEL=CombDCEL.adjoin(domainPack.packDCEL,
				domainPack.packDCEL, es1.startV,es2.endV,length);
		domainPack.vertexMap=domainPack.packDCEL.oldNew;
		domainPack.packDCEL.fixDCEL(domainPack);
		
		// remove these 'EdgeSeg's from 'masterESlist'
		masterESlist.remove((EdgeSeg)es1);
		masterESlist.remove((EdgeSeg)es2);
		
		// adjust numbering of remaining EdgeSeg's
		VertexMap vM=domainPack.vertexMap;
		Iterator<EdgeSeg> ml=masterESlist.iterator();
		EdgeSeg es=null;
		while (ml.hasNext()) {
			es=(EdgeSeg)ml.next();
			if (!es.convertIndices(vM)) 
				throw new ParserException("Missing vertexMap entry");
		}
		
		// fix 'masterMap': should <dp,rp> pairs, dp in 
		//    domainPack to rp in rangePack
		VertexMap tmpVM=new VertexMap();
		for (int i=1;i<=domainPack.nodeCount;i++) {
			int oi=domainPack.vertexMap.findV(i); // find former index
			tmpVM.add(new EdgeSimple(oi,masterMap.findW(oi)));
		}
		masterMap=tmpVM;
		
		return length;
	}
	
	/**
	 * Paste new sheet 'tSheet' to domainPack by attaching its 'tSlit' to
	 * the given edge segment of domainPack. We remove the used slit 
	 * and add all but 'tSlit' to 'masterESlist'. Also, update 'masterMap'. 
	 * @param es
	 * @param tSheet, sheet number
	 * @param tSlit, slit number
	 * @return 0 on error
	 */
	public int attachIt(EdgeSeg es,int tSheet,int tSlit) {
		if (es==null) 
			return 0;
		
		// 'domainPack' is the base packing we attach to.
		int holdCount=domainPack.nodeCount;
		
		// check validity, length of this edge segment
		int length=es.validate(domainPack);
		
		// need pointer 'EdgeSeg' in 'slits' linked list
		Iterator<EdgeSeg> esl=slits.iterator();
		EdgeSeg tES=null;
		while (tES==null && esl.hasNext()) {
			EdgeSeg ees=(EdgeSeg)esl.next();
			if (ees.slitNumber==tSlit)
				tES=ees;
		}
		if (tES==null) {
			throw new CombException("Error: given slit not located");
		}
		int len=tES.validate(slitPack);
		if (len==0 || len!=length) { // not matching lengths
			errorMsg("'edgeSeg' lengths didn't match");
			return 0;
		}
		
		// do the 'adjoin'
		domainPack.packDCEL=CombDCEL.adjoin(domainPack.packDCEL,
				slitPack.packDCEL, es.startV,tES.endV,length);
		VertexMap holdvm=domainPack.packDCEL.oldNew;
		domainPack.packDCEL.fixDCEL(domainPack);
		domainPack.vertexMap=holdvm;
		
		// must remove the 'EdgeSeg' from 'masterESlist'
		masterESlist.remove(es);
		
		// clone 'slits', shift indices, remove tES, add rest to 
		//    'masterESlist'; hold conversion from 'slitPack'
		Iterator<EdgeSeg> sls=slits.iterator();
		VertexMap vM=domainPack.vertexMap;
		while (sls.hasNext()) {
			EdgeSeg ees=(EdgeSeg)sls.next();
			if (ees.slitNumber!=tSlit) {
				ees=ees.clone(tSheet);
				ees.convertIndices(vM);
				masterESlist.add(ees);
			}
		}
		
		// augment 'masterMap' for new vertices
		Iterator<EdgeSimple> doml=domainPack.vertexMap.iterator();
		while (doml.hasNext()) {
			EdgeSimple edge=(EdgeSimple)doml.next();
			if (edge.w>holdCount) { // this is a new vertex
				int w=slitMap.findW(edge.v); // w=image vert in 'rangePack'
				masterMap.add(new EdgeSimple(edge.w,w));
			}
		}
		
		// set 'mark' of 'domainPack' to sheet number
		for (int v=(holdCount+1);v<=domainPack.nodeCount;v++) {
			domainPack.setVertMark(v,tSheet);
		}
		return 1;
	}
	
	/**
	 * Find the segment for sheet sht,slit cut in 'masterESlist'.
	 * @return null on error
	 */
	public EdgeSeg findMasterES(int sht,int cut) {
		Iterator<EdgeSeg> msl=masterESlist.iterator();
		while(msl.hasNext()) {
			EdgeSeg es=(EdgeSeg)msl.next();
			if (es.sheetNumber==sht && es.slitNumber==cut)
				return es;
		}
		return null;
	}
	
	/**
	 * See format in comments at top of this file:
	 * @param fp BufferedReader, opened by calling routine
	 * @return 0 on error. 
	 */
	public int readRMData(BufferedReader fp) {
		try {
			String line=null;
			String nexttok=null;
			StringTokenizer tok;
			line=StringUtil.ourNextLine(fp);
			while (line!=null) {
				tok = new StringTokenizer(line);
				nexttok=tok.nextToken();
				if (nexttok.equals("SLITCOUNT:")) {
					
					// get number of slits 
					numSlits=Integer.parseInt(tok.nextToken());
			
					// get that many pairs of complex slits endpts 
					//    (delineated by linebreaks)
					slitLinks=new ArrayList<HalfLink>();
					// keep list of endpoints of slits
					branchList=new NodeLink(rangePack); 
					line=StringUtil.ourNextLine(fp);
					int tick=0;
					
					// with 'nonos' we try to insure each new geo 
					//    misses previous
					NodeLink nonos=new NodeLink(rangePack);
					while (tick<numSlits && line!=null) {
						// line of form "v1 v2 ... vn" or "x1 y1 x2 y2"
						//    check if first entry is integer.
						boolean zform=false;
						try {
							String first=StringUtil.grabNext(line);
							int dumy=Integer.parseInt(first);
							if (dumy<1 || dumy>rangePack.nodeCount)
								zform=true;
						} catch(NumberFormatException nfe) {
							zform=true;
						}
						
						NodeLink nlk=null;
						try {
							if (zform) { // create geodesic based on endpoints
								String []splits=line.split("\\s+"); // whitespace
								Complex z1=new Complex(Double.parseDouble(splits[0]),
										Double.parseDouble(splits[1]));
								Complex z2=new Complex(Double.parseDouble(splits[2]),
										Double.parseDouble(splits[3]));
								int v1=0;
								int v2=0;
								try {
									v1=rangePack.cir_closest(z1,false).get(0);
									v2=rangePack.cir_closest(z2,false).get(0);
								} catch (Exception ex) {
									throw new DataException(
										"Didn't find closest circle to an endpoint");
								}
								
								// get geodesic halflinks (may throw exception)
								HalfLink elk=HalfLink.getCombGeo(rangePack.packDCEL,
									new NodeLink(rangePack,v1),
									new NodeLink(rangePack,v2),nonos);
								
								// convert to vertlist
								nlk=new NodeLink(rangePack,
										elk.get(0).origin.vertIndx);
								Iterator<HalfEdge> hlst=elk.iterator();
								while (hlst.hasNext()) {
									HalfEdge edge=hlst.next();
									nlk.add(edge.next.origin.vertIndx);
								}
							}
							else { // vertices are specified directly
								nlk=new NodeLink(rangePack,line);
							}
							if (nlk.size()<3)
								throw new CombException(
										"Slit starting with "+nlk.get(0)+
										" does not have 3 vertices.");
							slitLinks.add(CombDCEL.verts2Edges(
									slitPack.packDCEL,nlk,false));
							branchList.add(nlk.getFirst());
							branchList.add(nlk.getLast());
							nonos.abutMore(nlk); // accumulate with others
							tick++;
							line=StringUtil.ourNextLine(fp);
						} catch (Exception ex) {
							throw new ParserException(
								"error creating slit lists; "+ex.getMessage());
						}
					}
					if (tick<numSlits)
						return 0;
				}
				else if (nexttok.equals("PASTECOUNT:")) {
					
					// get number of pasting codes
					numCodes=Integer.parseInt(tok.nextToken());

					// read 'PasteCode' entries
					pasteCodes=new LinkedList<PasteCode>();
					int tick=0;
					boolean hit=true;
					line=StringUtil.ourNextLine(fp);
					while (hit && tick<numCodes && line!=null) {
						hit=false;
						PasteCode pC=new PasteCode();
						tok=new StringTokenizer(line);
						int n=tok.countTokens();
						if (n==4) {
							pC.sourceSheet=Integer.parseInt(tok.nextToken());
							pC.sourceEdge=Integer.parseInt(tok.nextToken());
							pC.targetSheet=Integer.parseInt(tok.nextToken());
							pC.targetEdge=Integer.parseInt(tok.nextToken());
							tick++;
							hit=true;
							pasteCodes.add(pC);
							line=StringUtil.ourNextLine(fp);
						}
					}
					if (tick<numCodes) 
						return 0;
					
				}
				else line=StringUtil.ourNextLine(fp);
			} // end of while
			if (numCodes<=0 || numSlits<=0) 
				throw new ParserException();
		} catch (Exception ex) {
			throw new ParserException("failed in reading RM data");
		}
		return 1;
	}
	
	/**
	 * The 'slitLinks' are original half edges of 'slitPack',
	 * so should be inherited as the cclw oriented half edges
	 * in bdry of current 'slitPack' (i.e., after 'extractDCEL').
	 * Now we must set up the various 'EdgeSeg's in 'slits', two
	 * successive ones for each slit.
	 * @return int count
	 */
	public int catalogSlits() {
		PackDCEL pdcel=slitPack.packDCEL;
		slits=new LinkedList<EdgeSeg>();
		int tick=0;
		Iterator<HalfLink> his=slitLinks.iterator();
		while (his.hasNext()) {
			HalfLink hlist=his.next(); // cclw segment
			int length=hlist.size();
			Vertex startVert=hlist.getFirst().origin;
			Vertex endVert=hlist.getLast().twin.origin;
			int startV=startVert.vertIndx;
			int endV=endVert.vertIndx;
			EdgeSeg edgeSeg=new EdgeSeg(++tick,startV,endV);
			edgeSeg.setLength(length);
			slits.add(edgeSeg);
			
			// identify opposite edge, which may be new or 
			//   original indices. One edge downstream should
			//   provide unambiguous vertex for pasting, so we
			//   leverage this.
			int downV=hlist.get(1).origin.vertIndx; // downstream one edge
			Vertex oppV=pdcel.vertices[slitPack.vertexMap.findW(downV)];
			if (downV>rangePack.nodeCount) { // 'downV' is new
				oppV=pdcel.vertices[slitPack.vertexMap.findV(downV)];
			}
			HalfEdge he=oppV.halfedge;
			endV=he.twin.origin.vertIndx;
			
			// go clw along bdry to find start
			for (int j=0;j<length;j++)
				he=he.twin.next.twin;
			startV=he.twin.origin.vertIndx;
			edgeSeg=new EdgeSeg(++tick,startV,endV);
			edgeSeg.setLength(length);
			slits.add(edgeSeg);
		} // end of while
		return tick;
	}

	/**
	 * Display shell messages with edge segment info; see if all
	 * startV, endV pairs are matched. If not return 0.
	 * @return 0 on inconsistency
	 */
	public int displayES() {
		boolean hit=false;
		try {
			int []starts=new int[domainPack.nodeCount+1];
			int []ends=new int[domainPack.nodeCount+1];
			for (int j=1;j<=domainPack.nodeCount;j++) 
				starts[j]=ends[j]=0;
			Iterator<EdgeSeg> dsl=masterESlist.iterator();
			System.err.println("Current 'masterSGlist' of 'EdgeSeg's");
			while (dsl.hasNext()) {
				EdgeSeg es=(EdgeSeg)dsl.next();
				int next=es.endV;
				int tcnt=1;
				while ((next=domainPack.getFirstPetal(next))!=
						es.startV) tcnt++;
				System.err.println(" sheet "+es.sheetNumber+", slit: "+
					es.slitNumber+" <"+es.startV+","+es.endV+">, "
							+ "length = "+tcnt);
				starts[es.startV]++;
				ends[es.endV]++;
			}
			for (int j=1;j<=domainPack.nodeCount;j++) {
				int k=starts[j]+ends[j];
				if ((k!=0 && k!=2) || starts[j]>1 || ends[j]>1) {
					hit=true;
					System.err.println("Bdry error in vert "+j);
				}
			}
			
		} catch(Exception ex) {
			ex.printStackTrace();
			return 0;
		}
		if (hit) 
			return 0;
		return 1;
	}
	
	/**
	 * Find the next pastecode, either specified or default
	 * @return PasteCode, or null if none found
	 */
	public PasteCode nextPasteCode() {
		// return specified code, if one still exists
		if (pasteCodes!=null && pasteCodes.size()>0) 
			return pasteCodes.get(0);
		
		// else find a default (re-paste) situation
		else if (masterESlist!=null && masterESlist.size()>0) {
			EdgeSeg es=(EdgeSeg)masterESlist.get(0);
			int st=es.sheetNumber;
			int ct=es.slitNumber;
			int dSlit=0;
			if ((ct%2)!=0) // odd?
				dSlit=ct+1;
			else dSlit=ct-1;
			EdgeSeg des=findMasterES(st,dSlit);
			if (des==null) 
				return null;
			PasteCode PC=new PasteCode();
			PC.sourceSheet=st;
			PC.sourceEdge=ct;
			PC.targetSheet=des.sheetNumber;
			PC.targetEdge=des.slitNumber;
			return PC;
		}
		return null;
	}

	/**
	 * Expand a vertex list to its "hex-extended" version, as when the
	 * packing has been hex-refined. May just return vlist as is. 
	 * @param packing p, NodeLink vlist
	 * @return NodeLink (perhaps just vlist itself), null on error/empty
	 */
	public NodeLink hexExtendNL(PackData p,NodeLink vlist) {
		if (vlist==null || vlist.size()==0) return null;
		
		// put the original list in a string
		StringBuilder bstr=new StringBuilder("ee ");
		Iterator<Integer> vls=vlist.iterator();
		int v;
		while (vls.hasNext()) {
			v=vls.next();
			bstr.append(v+" ");
		}
		
		// get a hex-extended edge list
		EdgeLink elink=new EdgeLink(p,bstr.toString());
		
		// create a new NodeLink
		NodeLink newL=new NodeLink(p);
		Iterator<EdgeSimple> els=elink.iterator();
		EdgeSimple edge=null;
		int vcount=0;
		while (els.hasNext()) {
			edge=els.next();
			newL.add(edge.v);
			vcount++;
		}
		newL.add(edge.w); // don't forget the last one
		if (vcount>0) return newL;
		else return null;
	}
	
	/** 
	 * Override method for cataloging command structures
	 */
	public void initCmdStruct() {
		super.initCmdStruct();
		cmdStruct.add(new CmdStruct("doSlits",null,null,
				"make the prescribe slits to create 'slitPack'"));
		cmdStruct.add(new CmdStruct("read_data (infile_data)",
				"{filename}",null,"read the *.rmd data"));
		cmdStruct.add(new CmdStruct("copy","{pnum}",null,
				"copy 'domainPack' to the indicated packing"));
		cmdStruct.add(new CmdStruct("paste_next",null,null,
				"paste the next unused slit"));
		cmdStruct.add(new CmdStruct("paste",null,null,
				"do all remaining pastings"));
		cmdStruct.add(new CmdStruct("slit_[EV]list","{n1, n2, ...}",null,
				"set [EV]list to the designated slits"));
		cmdStruct.add(new CmdStruct("status",null,null,
				"Give RM state: ERROR, RANGE, RM_DATA, SLIT, BUILT"));
		cmdStruct.add(new CmdStruct("branchVal",null,null,
				"set Vlist with the branch circle indices"));
		cmdStruct.add(new CmdStruct("find[zoib]",null,null,
				"If BUILT, set Vlist to verts over "+
						"zero/one/infty or branched"));
	}
	
}

class PasteCode {
	public int sourceSheet;
	public int sourceEdge;
	public int targetSheet;
	public int targetEdge;
}
