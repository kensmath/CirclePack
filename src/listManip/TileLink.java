package listManip;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Vector;

import allMains.CPBase;
import allMains.CirclePack;
import circlePack.PackControl;
import exceptions.ParserException;
import ftnTheory.ConformalTiling;
import input.SetBuilderParser;
import packing.PackData;
import tiling.Tile;
import tiling.TileData;
import util.MathUtil;
import util.SelectSpec;
import util.StringUtil;
import util.UtilPacket;

public class TileLink extends LinkedList<Integer> {
	
	private static final long serialVersionUID = 1L;
	
	TileData myTD;
	
	// Constructors
	public TileLink(TileData td,String datastr) {
		super();
		myTD=td;
		if (myTD!=null && datastr!=null) 
			addTileLinks(datastr);
	}
	
	public TileLink(TileData td,int n) {
		super();
		myTD=td;
		if (myTD!=null && (n>0 && n<=myTD.tileCount)) add(n);
	}
	
	public TileLink(TileData td,Vector<String> items) {
		super();
		myTD=td;
		if (myTD!=null) {
			if (items==null || items.size()==0) { // default to 'a' (all tiles)
				items=new Vector<String>(1);
				items.add("a");
			}
			addTileLinks(items);
		}
	}
	
	/**
	 * utility, no 'TileData' specified
	 */
	public TileLink() {
		super();
	}

	/**
	 * Initiate empty list
	 * @param td TileData
	 */
	public TileLink(TileData td) {
		super();
		myTD=td;
	}
	
	/**
	 * Add any positive integer (regardless of whether it seems to be
	 * a valid tile index).
	 * @param v int
	 * @return boolean
	 */
	public boolean add(int v) {
//		if (myTD!=null && v>0 && v<=myTD.tileCount)
		if (v>0)
			return super.add((Integer)v);
		return false;
	}
	
	public boolean add(Integer v) {
		if (v==null)
			return false;
		return add(v.intValue());
	}
	
	/**
	 * Add links to this list (if it is associated with TileData). Note
	 * that argument should not be empty since 'a' would have been
	 * added as default.
	 * @param datastr String
	 * @return int, count
	 */	
	public int addTileLinks(String datastr) {
		Vector<String> items=StringUtil.string2vec(datastr,true);
		return addTileLinks(items);
	}
	
	/**
	 * Add links to this list (if it is associated with TileData). 
	 * Note that argument should not be empty since "a" would have been
	 * added as default.
	 * @param items Vector<String>
	 * @return int count
	 */	
	public int addTileLinks(Vector<String> items) {
		int count=0;
	
		if (myTD==null || myTD.tileCount<=0) 
			return -1;
		int tilecount=myTD.tileCount;
		
		Iterator<String> its=items.iterator();
		
		while (its!=null && its.hasNext()) {

			/* =============== here's the work ================== */
			
			String str=(String)its.next();
			// it's easy to put in '-' flags by mistake
			if (str.startsWith("-")) { 
				str=str.substring(1);
			}

			its.remove(); // shuck the entry (though still in 'str')

			// self call if str is a variable
			if (str.startsWith("_")) {
				count+=addTileLinks(CPBase.varControl.getValue(str));
			}
			
			// may point to 'dual'or 'quad' tiling
			if (str.charAt(0)=='D') {
				if (myTD.dualTileData==null)
					return 0;
				myTD=myTD.dualTileData;
				str=str.substring(1);
			}
			else if (str.charAt(0)=='Q') {
				if (myTD.quadTileData==null)
					return 0;
				myTD=myTD.quadTileData;
				str=str.substring(1);
			}
			
			if (str.length()==0)
				str="a";
			
			if (str.substring(1).startsWith("list")) {
				int t;
				TileLink tlink=null;
				boolean ck=false;
				
				if ((str.startsWith("t") && (tlink=myTD.packData.tlist)!=null
						&& tlink.size()>0) ||
						(str.startsWith("T") && (tlink=CPBase.Tlink)!=null
								&& CPBase.Tlink.size()>0)) {
					if (str.startsWith("T")) // v legal tile for packData?
						ck=true;
					
					String strdata=str.substring(5).trim(); // remove '?list'
					
					// check for parens listing range of indices 
					int lsize=tlink.size()-1;
					int[] irange=StringUtil.get_int_range(strdata, 0,lsize);
					if (irange!=null) {
						int a=irange[0];
						int b=(irange[1]>lsize) ? lsize : irange[1]; 
						for (int j=a;j<=b;j++) {
							t=tlink.get(j);
							if (ck && t>myTD.tileCount) {}
							else {
								add(t);
								count++;
							}
						}
					}
					
					// check for brackets first
					String brst=StringUtil.get_bracket_strings(str)[0];
					if (brst!=null) {
						if (brst.startsWith("r")) { // rotate list
							tlink.add(tlink.getFirst());
						}
						if (brst.startsWith("r") 
								|| brst.startsWith("n")) { // use up first
							t=(Integer)tlink.remove(0);
							if (ck && t>myTD.tileCount) {}
							else { 
								add(t);
								count++;
							}
						}
						if (brst.startsWith("l")) { // last
							t=(Integer)tlink.getLast();
							if (ck && t>myTD.tileCount) {}
							else { 
								add(t);
								count++;
							}
						}
						else {
							try{
								int n=MathUtil.MyInteger(brst);
								if (n>=0 && n<tlink.size()) {
									t=tlink.get(n);
									if (ck && t>myTD.tileCount) {}
									else { 
										add(t);
										count++;
									} 
								}
							} catch (NumberFormatException nfe) {}
						}
					}
					// else just adjoin the lists
					else { 
						if (!ck) {
							int n=size();
							addAll(n,tlink);
							count +=tlink.size();
						}
						else {
							Iterator<Integer> tlst=tlink.iterator();
							while (tlst.hasNext()) {
								t=(Integer)tlst.next();
								if (t>0 && t<=myTD.tileCount) {
									add(t);
									count++;
								}
							}
						}
					}
				}	
				else // no appropriate list found
					return count;
			}
			
			/******************************************************
			 * Now parse remaining options based on first character;
	 		 * default case, see if it's a number, i.e., a tile index. */

			else {
			switch(str.charAt(0)) {
			// all; check for parens
			case 'a':
			{
				int first=1;
				int last=myTD.tileCount;
				String []pair_str=StringUtil.get_paren_range(str); // get two strings
				if (pair_str!=null && pair_str.length==2) { // must have 2 strings
					int a,b;
					if ((a=NodeLink.grab_one_vert(myTD.packData,pair_str[0]))!=0) first=a;
					if ((b=NodeLink.grab_one_vert(myTD.packData,pair_str[1]))!=0) last=b;
				}
				for (int i=first;i<=last;i++) {
					add(i);
					count++;
				}
				break;
			}
			case 'c': // children -c {t..} using canonicalPack
			{
				TileLink inlink=new TileLink(myTD,items);
				Iterator<Integer> ink=inlink.iterator();
				while (ink.hasNext()) {
					int t=ink.next();
					if (t>=1 && t<myTD.tileCount) { // legal index?
						Tile atile=myTD.myTiles[t];
						TileData atd=atile.myTileData;
						if (atd!=null) { // has children?
							for (int cld=1;cld<=atd.tileCount;cld++) {
								add(atd.myTiles[cld].tileIndex); // add child's index
								count++;
							}
						}
					}
				}
				break; // under development
			}	
			case 'b': // have boundary vertex
			{
				for (int t=1;t<=tilecount;t++) {
					Tile tile=myTD.myTiles[t];			
					int hit=0;
					for (int j=0;(j<tile.vertCount && hit==0);j++)
						if (myTD.packData.isBdry(tile.vert[j])) {
							add(t);
							count++;
							hit=1;
						}
				}				
				break;
			}
			
			case 'B': // convert 'baryVert's to tile indices
			{
				NodeLink rawlist=new NodeLink(myTD.packData,items);
				if (rawlist.size()>0) {
					for (int t=1;t<=myTD.tileCount;t++) 
						if (rawlist.containsV(myTD.myTiles[t].baryVert)>=0) {
							add(t); 
							count++;
						}
				}
				break;
			}

			case 'I': // vertices 'Incident' to: Iv {v..} or It {t..}
			{
				if (str.length()<=1) break;
				int []hits=new int[tilecount+1];
				switch(str.charAt(1)) {
				case 'c': // fall through, same as 'v' 
				case 'v': // corner vertices
				{
					if (items.size()==0 || items.get(0).length()==0)
						break;
				    NodeLink vertlist=new NodeLink(myTD.packData,items);
					its=null; // eat rest of items
					if (vertlist==null || vertlist.size()==0) break;
				    Iterator<Integer> vlist=vertlist.iterator();
				    int v;
				    while (vlist.hasNext()) {
				    	v=(Integer)vlist.next();
				    	for (int t=1;t<=tilecount;t++) {
				    		if (myTD.myTiles[t].vertIndx(v)>=0)
				    			if (hits[t]==0) {
				    				add(t);
				    				count++;
				    				hits[t]=1;
				    			}
					    }
					} // end of while
				    break;
				}
				case 't': // edge-neighboring tiles
				{
					TileLink tilelist=new TileLink(myTD,items);
					its=null; // eat rest of items
					if (tilelist==null || tilelist.size()==0) break;
					Iterator<Integer> tlist=tilelist.iterator();
					int t,tilen;
					while (tlist.hasNext()) {
						t=(Integer)tlist.next();
						Tile tile=myTD.myTiles[t];
						if (tile.tileFlower!=null) {
							for (int j=0;j<tile.vertCount;j++) {
								tilen=Math.abs(tile.tileFlower[j][0]);
								if (tilen!=0) {
									add(tilen);
									hits[tilen]=1;
									count++;
								}
							}
						}
					} // end of while
					break;
				}
				} // end of local switch
				break;
			} // end of 'I' parsing
			
			case 'i': // interior (no boundary vertices)
			{
				for (int t=1;t<=tilecount;t++) {
					Tile tile=myTD.myTiles[t];			
					int hit=0;
					for (int j=0;(j<=tile.vertCount && hit==0);j++)
						if (myTD.packData.isBdry(tile.vert[j])) {
							hit=1;
						}
					if (hit==0) {
						add(t);
						count++;
					}
				}				
				break;
			}
			
			case 'm': // marked (or not-marked)
			{
				// check for -mc or -mq<p> or -mcq<p>
				boolean notmarked=false;
				if (str.contains("c")) notmarked=true;
				TileData qackTD=myTD;
				int qtilecount=tilecount;
				try {	// use those marked in another packing? pack[q]?
					int qnum;
					if (str.contains("q") && (qnum=Integer.parseInt(str.substring(str.length()-2)))>=0
							&& qnum<3 && PackControl.cpDrawing[qnum].getPackData().status
							&& PackControl.cpDrawing[qnum].getPackData().tileData!=null) {
						qackTD=PackControl.cpDrawing[qnum].getPackData().tileData;
					}
					if (qackTD==null || (qtilecount=qackTD.tileCount)<=0)
						return count;
				} catch(Exception ex) {}
				for (int t=1;t<=tilecount;t++)
					if (t<=qtilecount
							&& ((notmarked && qackTD.myTiles[t].mark==0) 
									|| (!notmarked && qackTD.myTiles[t].mark!=0))) {
						add(t);
						count++;
					}
				break;
			}
			
			case 'M': // only if 'ConformalTiling' pack extender and 'gradedTileData' exist;
				// find marked tiles whose parent tiles up to coarsest level are also marked.
			{
				// check for -mc 
				boolean marked=true;
				if (str.contains("c")) 
					marked=false;
				PackData p=myTD.packData;
				
				
				ConformalTiling ct=(ConformalTiling)p.findXbyAbbrev("ct");
				PackData canonPack=null;
				if (ct==null || (canonPack=ct.getCanonicalPack())==null || 
						canonPack.tileData.gradedTileData==null)
					return count;

				Vector<Integer> indList=ct.listMarkedTiles(marked);
				Iterator<Integer> il=indList.iterator();
				while (il.hasNext()) {
					add(il.next());
					count++;
				}
				break;
			}
			
			case 't': // type: -t {t...} (must be last flag sequence)
			{
				
				Vector<Integer> tts=new Vector<Integer>();
				while (its.hasNext()) {
					try {
						tts.add(Integer.parseInt(its.next()));
					} catch(Exception ex) {
						CirclePack.cpb.errMsg("usage: -t {t..} for tile types");
						break;
					}
				} 
				int vecsz=tts.size();

				// now find tiles with type in tts
				for (int j=1;j<=tilecount;j++) {
					int tt=myTD.myTiles[j].tileType;
					boolean hit=false;
					for (int k=0;k<vecsz;k++) {
						if (tt==(int)tts.get(k))
							hit=true;
					}
					if (hit) {
						add(j);
						count++;
					}
				}
				break;
			}
			case '{': // set-builder notation; reap results
			{
				SetBuilderParser sbp=new SetBuilderParser(myTD.packData,str,'t');
				if (!sbp.isOkay()) 
					return 0;
				Vector<SelectSpec> specs=sbp.getSpecVector();
				PackData qackData=sbp.packData;
				TileLink nl=tileSpecs(qackData,specs);
				if (nl!=null && nl.size()>0) {
					this.addAll(nl);
					count+=nl.size();
				}
				break;
			}
			
			default: // if nothing else, if an integer, take it as tile index
			{
				try{
					int t=MathUtil.MyInteger(str);
					if (t>0 && t<=myTD.tileCount) {
						add(t);
						count++;
					}
				} catch (NumberFormatException nfe) {
					return count;
				}
			}		
			} // end of switch
			}
			
		} // end of 'while' through items iterator
		return count;
	}
	
	/**
	 * Pick first tile of list described in string.
	 * @param p PackData
	 * @param str String
	 * @return 0 on failure
	 */
	public static int grab_one_tile(TileData td,String str) {
		TileLink tlist=new TileLink(td,str);
		if (tlist!=null && tlist.size()>0) 
			return (int)(tlist.get(0));
		return 0;
	}
	
	/**
	 * Pick first tile index from first string of first vector of vector
	 * of string vectors. 
	 * @param p PackData
	 * @param flagsegs Vector<Vector<String>>
	 * @return 0 on failure
	 */
	public static int grab_one_tile(TileData td,Vector<Vector<String>> flagsegs) {
		if (flagsegs==null || flagsegs.size()==0)
			return 0;
		return grab_one_tile(td,StringUtil.reconItem(flagsegs.get(0)));
	}

	/**
	 * Is 't' an entry?
	 * @param t int
	 * @return int, index of t or -1 on error or not found
	 */
	public int containsV(int t) {
		for (int j=0;j<this.size();j++)
			if ((int)this.get(j)==t)
				return j;
		return -1;
	}

	/**
	 * Make up list by looking through SetBuilder specs 
	 * (from {..} set-builder notation). Use 'tmpFlag' to 
	 * collect information before creating the TileLink for return.
	 * @param p PackData
	 * @param specs Vector<SelectSpec>
	 * @return TileLink list of specified tiles.
	 */
	public static TileLink tileSpecs(PackData p,Vector<SelectSpec> specs) {
		if (specs==null || specs.size()==0 || p.tileData==null) 
			return null;
		SelectSpec sp=null;
		int count=0;

		// will store results in 'tmpFlag'
		int[] tmpUtil=new int[p.tileData.tileCount+1];
		// loop through all the specifications: these should alternate
		//   between 'specifications' and 'connectives', starting with 
		//   the former, although typically there will be just one 
		//   specification in the vector and no connective.
		UtilPacket uPx=null;
		UtilPacket uPy=null;
		boolean isAnd=false; // true for '&&' connective, false for '||'.
		for (int j=0;j<specs.size();j++) {
			sp=(SelectSpec)specs.get(j);
			if (sp.object!='t') 
				throw new ParserException(); // spec must be for tiles
			try {
				for (int t=1;t<=p.tileData.tileCount;t++) {
					
					// success?
					boolean outcome=false;
					uPx=sp.node_to_value(p,t,0);
					if (sp.unary) {
						if (uPx.rtnFlag!=0)
							outcome=sp.comparison(uPx.value,0);
					}
					else {
						uPy=sp.node_to_value(p,t,1);
						if (uPy.rtnFlag!=0)
							outcome=sp.comparison(uPx.value, uPy.value);
					}
					if (outcome) { // yes, this value satisfies condition
						if (!isAnd && tmpUtil[t]==0) { // 'or' situation
							tmpUtil[t]=1;
							count++;
						}
					}
					else { // no, fails this condition
						if (isAnd && tmpUtil[t]!=0) { // 'and' situation
							tmpUtil[t]=0;
							count--;
						}
					}
				}
			} catch (Exception ex) {
				throw new ParserException();
			}
			
			// if specs has 2 or more additional specifications, the next must
			//    be a connective. Else, finish loop.
			if ((j+2)<specs.size()) {
				sp=(SelectSpec)specs.get(j+1);
				if (!sp.isConnective) 
					throw new ParserException();
				isAnd=sp.isAnd; 
				j++;
			}
			else j=specs.size(); // kick out of loop
		}
		
		if (count>0) {
			TileLink nl=new TileLink(p.tileData);
			for (int t=1;t<=p.tileData.tileCount;t++)
				if (tmpUtil[t]!=0) nl.add(t);
			return nl;
		}
		else return null;
	}
	
	
}
