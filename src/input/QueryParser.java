package input;

import java.util.Iterator;
import java.util.Vector;

import allMains.CPBase;
import allMains.CirclePack;
import circlePack.PackControl;
import complex.Complex;
import exceptions.CombException;
import exceptions.DataException;
import exceptions.ParserException;
import ftnTheory.PointEnergies;
import geometry.EuclMath;
import input.CommandStrParser.Energy;
import komplex.EdgeSimple;
import listManip.EdgeLink;
import listManip.FaceLink;
import listManip.NodeLink;
import listManip.TileLink;
import math.Matrix3D;
import math.Mobius;
import packing.PackData;
import panels.CPScreen;
import util.CallPacket;
import util.StringUtil;
import util.ViewBox;

public class QueryParser {
	
	public static int processQuery(PackData p,String queryStr,boolean forMsg) {
		StringBuilder strbld=new StringBuilder(queryStr);
		
		// remove the '?'
		strbld.deleteCharAt(0); 
		while (strbld.length()>0 && strbld.charAt(0)==' ')
			strbld.deleteCharAt(0);
		if (strbld.length()==0)
			return 0;
		
		// split off 'query': up to first " "
		int k=strbld.indexOf(" ");
		String result=null;
		if (k>0) {
			String query=strbld.substring(0,k);
			strbld.delete(0,k);
		
			// break rest into usual flag segments (though generally no flags)
			Vector<Vector<String>> flagSegs=StringUtil.flagSeg(strbld.toString());
			result=queryParse(p,query,flagSegs,forMsg);
		}
		else
			result=queryParse(p,strbld.toString(),(Vector<Vector<String>>)null,forMsg);
		
		if (result==null)
			return 0;
		CirclePack.cpb.msg(result);
		return 1;
	}
	
	/**
	 * Return a String in response to a query. This may have three parts: 'words',
	 * 'ans', and 'suffix'. 'ans' may represent a value, list, etc, and generally 
	 * can be used as a variable value in commands (in string form). 
	 * 
	 * If 'forMsg' is true, query is intended as a message, so 'words' is prepended.
	 * Note that 'words' defaults to "query (p*) = "; may want to change this if it
	 * doesn't depend on the current packing (e.g., 'Vlist') 
	 * 
	 * Also, if 'forMsg', then some lists may be limited to 12 items, 
	 * e.g. if vertex list is longer, put in '...' via a
	 * 'suffix' string. Long strings are likewise truncated.
	 * 
	 * TODO: add functionality as needed 
	 * 
	 * @param query, String of '?<query>' type: the '?' is gone,
	 *    string should have been trimmed already 
	 * @param flagSegs, usual sequence of flag segments
	 * @param forMsg: true if the result will be reported as a message
	 *        rather than used for something else, like setting a variable.
	 * @return String representation of result, null on error
	 */
	public static String queryParse(PackData p,String query,Vector<Vector<String>> flagSegs,boolean forMsg) {
		StringBuilder ans=new StringBuilder(""); // result of the query alone
		// some utility variables
		NodeLink vertlist=null;
	  	int v;
		String firststr=null;
		// by default,  to use if 'forMsg'
		StringBuilder words=new StringBuilder(query+" (p"+p.packNum+") "); 
		String suffix=null;
		boolean gotone=false;
		
		// utility: note first set of strings and its first string
		Vector<String> items=null;
		try {
			items=(Vector<String>)flagSegs.get(0); 
			firststr=items.get(0); // sometimes will want to remove this
		} catch(Exception ex) { // some commands don't need anything
			firststr=null;
		}
		
		try {
			char c=query.charAt(0);
			
			// handle any 'list' requests first: limit is 1000 or
			//    12 if intended as a message.
			if (query.length()>=5 && query.substring(1,5).equalsIgnoreCase("list")) {
				int n=0;
				switch(c) {
				case 'e': {
					if (p.elist==null || p.elist.size()==0) {
						if (forMsg)
							ans.append("empty");
						break;
					}
					gotone=true;
					n=p.elist.size();
					if (forMsg && n>12) {
						n=12;
		  	      		suffix=" ... ";
					}
					n=(n>1000)? 1000:n;
					Iterator<EdgeSimple> elst=p.elist.iterator();
					int click=0;
					while (elst.hasNext() && click<n) {
						EdgeSimple edge=elst.next();
						ans.append(" "+edge.v+" "+edge.w+"  ");
					}
					break;
				} 
				case 'E': {
					if (CPBase.Elink==null || CPBase.Elink.size()==0) {
						if (forMsg)
							ans.append("empty");
						break;
					}
					gotone=true;
					n=CPBase.Elink.size();
					if (forMsg && n>12) {
						n=12;
		  	      		suffix=" ... ";
					}
					n=(n>1000)? 1000:n;
					Iterator<EdgeSimple> elst=PackControl.Elink.iterator();
					int click=0;
					while (elst.hasNext() && click<n) {
						EdgeSimple edge=elst.next();
						ans.append(" "+edge.v+" "+edge.w+"  ");
					}
					break;
				}
				case 'f': {
					if (p.flist==null || p.flist.size()==0) {
						if (forMsg)
							ans.append("empty");
						break;
					}
					gotone=true;
					n=p.flist.size();
					if (forMsg && n>12) {
						n=12;
		  	      		suffix=" ... ";
					}
					n=(n>1000)? 1000:n;
					Iterator<Integer> flst=p.flist.iterator();
					int click=0;
					while (flst.hasNext() && click<n) {
						ans.append(" "+flst.next());
					}
					break;
				}
				case 'F': {
					if (CPBase.Flink==null || CPBase.Flink.size()==0) {
						if (forMsg)
							ans.append("empty");
						break;
					}
					gotone=true;
					n=CPBase.Flink.size();
					if (forMsg && n>12) {
						n=12;
		  	      		suffix=" ... ";
					}
					n=(n>1000)? 1000:n;
					Iterator<Integer> flst=CPBase.Flink.iterator();
					int click=0;
					while (flst.hasNext() && click<n) {
						ans.append(" "+flst.next());
					}
					break;
				}
				case 't': {
					if (p.tlist==null || p.tlist.size()==0) {
						if (forMsg)
							ans.append("empty");
						break;
					}
					gotone=true;
					n=p.tlist.size();
					if (forMsg && n>12) {
						n=12;
		  	      		suffix=" ... ";
					}
					n=(n>1000)? 1000:n;
					Iterator<Integer> tlst=p.tlist.iterator();
					int click=0;
					while (tlst.hasNext() && click<n) {
						ans.append(" "+tlst.next());
					}
					break;
				}
				case 'T': {
					if (CPBase.Tlink==null || CPBase.Tlink.size()==0) {
						if (forMsg)
							ans.append("empty");
						break;
					}
					gotone=true;
					n=CPBase.Tlink.size();
					if (forMsg && n>12) {
						n=12;
		  	      		suffix=" ... ";
					}
					n=(n>1000)? 1000:n;
					Iterator<Integer> tlst=CPBase.Tlink.iterator();
					int click=0;
					while (tlst.hasNext() && click<n) {
						ans.append(" "+tlst.next());
					}
					break;
				}
				case 'v': {
					if (p.vlist==null || p.vlist.size()==0) {
						if (forMsg)
							ans.append("empty");
						break;
					}
					gotone=true;
					n=p.vlist.size();
					if (forMsg && n>12) {
						n=12;
		  	      		suffix=" ... ";
					}
					n=(n>1000)? 1000:n;
					Iterator<Integer> vlst=p.vlist.iterator();
					int click=0;
					while (vlst.hasNext() && click<n) {
						ans.append(" "+vlst.next());
					}
					break;
				}
				case 'V': {
					if (CPBase.Vlink==null || CPBase.Vlink.size()==0) {
						if (forMsg)
							ans.append("empty");
						break;
					}
					gotone=true;
					n=CPBase.Vlink.size();
					if (forMsg && n>12) {
						n=12;
		  	      		suffix=" ... ";
					}
					n=(n>1000)? 1000:n;
					Iterator<Integer> vlst=CPBase.Vlink.iterator();
					int click=0;
					while (vlst.hasNext() && click<n) {
						ans.append(" "+vlst.next());
					}
					break;
				}
				case 'g': {
					if (p.glist==null || p.glist.size()==0) {
						if (forMsg)
							ans.append("empty");
						break;
					}
					gotone=true;
					n=p.glist.size();
					if (forMsg && n>12) {
						n=12;
		  	      		suffix=" ... ";
					}
					n=(n>1000)? 1000:n;
					Iterator<EdgeSimple> glst=p.glist.iterator();
					int click=0;
					while (glst.hasNext() && click<n) {
						EdgeSimple edge=glst.next();
						if (edge.v==0)
							ans.append("root: "+edge.w);
						else 
							ans.append(" "+edge.v+" "+edge.w+"  ");
					}
					break;
				} 
				case 'G': {
					if (CPBase.Glink==null || CPBase.Glink.size()==0) {
						if (forMsg)
							ans.append("empty");
						break;
					}
					gotone=true;
					n=CPBase.Glink.size();
					if (forMsg && n>12) {
						n=12;
		  	      		suffix=" ... ";
					}
					n=(n>1000)? 1000:n;
					Iterator<EdgeSimple> glst=PackControl.Glink.iterator();
					int click=0;
					while (glst.hasNext() && click<n) {
						EdgeSimple edge=glst.next();
						if (edge.v==0)
							ans.append("root: "+edge.w);
						else 
							ans.append(" "+edge.v+" "+edge.w+"  ");
					}
					break;
				}
				case 'z': {
					if (p.zlist==null || p.zlist.size()==0) {
						if (forMsg)
							ans.append("empty");
						break;
					}
					gotone=true;
					n=p.zlist.size();
					if (forMsg && n>12) {
						n=12;
		  	      		suffix=" ... ";
					}
					n=(n>1000)? 1000:n;
					Iterator<Complex> vlst=p.zlist.iterator();
					int click=0;
					while (vlst.hasNext() && click<n) {
						ans.append(" "+vlst.next().toString3());
					}
					break;
				}
				case 'Z': {
					if (CPBase.Zlink==null || CPBase.Zlink.size()==0) {
						if (forMsg)
							ans.append("empty");
						break;
					}
					gotone=true;
					n=CPBase.Zlink.size();
					if (forMsg && n>12) {
						n=12;
		  	      		suffix=" ... ";
					}
					n=(n>1000)? 1000:n;
					Iterator<Complex> vlst=CPBase.Zlink.iterator();
					int click=0;
					while (vlst.hasNext() && click<n) {
						ans.append(" "+vlst.next().toString3());
					}
					break;
				}
				
				// TODO: have to add BaryLink queries, Blink, blist
				
				} // end of switch
				
			}

			// now process the rest
			else switch(c) {
			
			// NOTE: as queries are added, they should be added to 'CmdCompletion.txt'
			
			case 'a': { // --------------------------------------------------------
			
				// angle aim/pi (just one)
				if (query.startsWith("aim")) {
					v=NodeLink.grab_one_vert(p,flagSegs);
					ans.append(Double.toString(p.rData[v].aim/Math.PI));
					gotone=true;
				}
				
				// angle sum/pi (just one)
				else if (query.startsWith("anglesum")) {
					v=NodeLink.grab_one_vert(p,flagSegs);
					if (v!=0) {
						ans.append(Double.toString(p.rData[v].curv/Math.PI));
						if (forMsg)
							words.append("v"+v+": ");
						gotone=true;
					}
				}
				
				else if (query.startsWith("antip")) {
					NodeLink vlist=new NodeLink(p,items);
					if (vlist!=null) {
						ans.append(p.gen_mark(vlist,-1,false));
						if (forMsg) 
							words.append("(furthest away): ");
						gotone=true;
					}
				}

				// sum of areas of given faces, default to all
				else if (query.startsWith("area")) {
					FaceLink flist=new FaceLink(p,items);
					if (flist!=null) {
						double area=0.0;
						int count=0;
						Iterator<Integer> flst=flist.iterator();
						while (flst.hasNext()) {
							int f=flst.next();
							area += p.faceArea(f);
							count++;
						}
						if (forMsg) {
							words.append(" "+count+" faces, area = "+String.format("%." + 4 + "e",area));
						}
						gotone=true;
					}
				}

				break;
			} // end of 'a'
			
			case 'c': { // --------------------------------------------------------

				// center: just one
				if (query.startsWith("cent")) {
					v=NodeLink.grab_one_vert(p,flagSegs);
					if (v!=0) {
						Complex z=p.rData[v].center;
						ans.append(z.x+" "+z.y);
						if (forMsg)
							words.append(" for v"+v+" ");
						gotone=true;
					}
				}
				else if (query.startsWith("count")) {
					String cmd_str=new String("count "+StringUtil.reconstitute(flagSegs));
					int count=CommandStrParser.jexecute(p,cmd_str);
					if (count==-1) count=0;
					ans.append(count);
					if (forMsg) // appropriate message already being given
						forMsg=false;
					gotone=true;
				}
				break;
			} //end of 'c'
				
			case 'e': { // --------------------------------------------------------
				
				// "energy"; items should be the only flag sequence
				if (query.startsWith("energy")) {
					if (firststr==null)
						firststr=new String("-c"); // default to Coulomb
					double energy=1.0;
					if (firststr.startsWith("-c")) { // Coulomb
						energy=PointEnergies.comp_energy(p,Energy.COULOMB);
						words.append(", Coulomb");
					}
					else if (firststr.startsWith("-h")) { // Hilbert (L2)
						energy=Math.sqrt(PointEnergies.comp_energy(p,Energy.L2));
						words.append(", Hilbert (L2 norm)");
					}
					else if (firststr.startsWith("-l")) { // Logarithmic
						energy=PointEnergies.comp_energy(p,Energy.LOG);
						words.append(", Logarithmic");
					}
					else if (firststr.startsWith("-m")) { // Logarithmic
						energy=PointEnergies.comp_energy(p,Energy.MIN_DIST);
						words.append(", Min_distance");
					}
					else 
						throw new ParserException("energy: valid type not indcated");
					// TODO: could consider negative infinity energy (e.g., power -100)
					
					ans.append(energy);
					gotone=true;
				}
				
				else if (query.startsWith("edge_x")) { // cross-ration of edge
					int j,k,v1,v3;
					words=new StringBuilder("Edge cross_ratios (p"+p.packNum);
					EdgeLink edgelist=new EdgeLink(p,flagSegs.get(0));
					Iterator<EdgeSimple> elist=edgelist.iterator();
					EdgeSimple edge=null;
					int count=0;
					if (!forMsg) // just return value
						count=11;
					while(elist.hasNext() && ((!forMsg || count<12) || count<1000)) {
						edge=(EdgeSimple)elist.next();
						if ((j=p.nghb((v1=edge.v),(v3=edge.w)))>=0 
								&& (k=p.nghb(v3,v1))>=0 && j!=p.kData[v1].num && k!=p.kData[v3].num) {

							// TODO: what about case of inv distances/overlaps?
							
							Complex z=EuclMath.tang_cross_ratio(p, edge);
							if (!forMsg) {
								ans.append("edge <"+edge.v+" "+edge.w+"> cross-ratio is "+z.x+" "+z.y+"i");
								count++;
							}
							else { // create message along the way
								words.append(" edge ("+edge.v+" "+edge.w+"): cross_ratio = ("+
										z.x+" i "+z.y+" ");
								count++;
			  			  	}
						}
						else {
							throw new ParserException("error in list of edges.");
						}
						gotone=true;
					} // end of while
					
					// return from here
					if (!forMsg) 
						return ans.toString();
					else
						return words.toString();
				}
				
				break;
			} // end of 'e'
			
			case 'f': { // --------------------------------------------------------
				
				// flower
				if (query.startsWith("flowe")) {
					int vv;
					try {
						vv=NodeLink.grab_one_vert(p,items.get(0));
					} catch (Exception ex) {
						throw new ParserException("usage: 'v'");
					}
					words.append(" v"+vv); // show which vert
					int n=p.kData[vv].num;
					if (forMsg && n>12) {
						n=12;
		  	      		suffix=" ... ";
					}
					int j=0;
					while (j<=n) {
						ans.append(Integer.toString(p.kData[vv].flower[j])+" ");
						j++;
					}
					gotone=true;
				}
				
				// return f(z)
				if (query.startsWith("f(z)")) {
					double x=0;
					double y=0;
					try { // one (real) or two (complex)
						x=Double.parseDouble(items.get(0));
						try {
							y=Double.parseDouble(items.get(1));
						} catch (Exception ex) {}
					} catch (Exception ex) {
						throw new ParserException("usage: 'x [y]' for complex argument");
					}
					Complex w=PackControl.functionPanel.getFtnValue(new Complex(x,y));
					if (Math.abs(y)<CPBase.GENERIC_TOLER) // if real, suppress the y
						ans.append(Double.toString(w.x));
					else 
						ans.append(new String(w.x+" "+w.y));
					gotone=true;
				}
				break;
			} // end of 'f'
			
			case 'g': { // --------------------------------------------------------
				
				// return gam(t), parameterized path
				if (query.startsWith("gam(t)")) {
					double t=0;
					try { // one real
						t=Double.parseDouble(items.get(0));
					} catch (Exception ex) {
						throw new ParserException("usage: 't' for real argument");
					}
					Complex w=PackControl.functionPanel.getParamValue(t);
					ans.append(new String(w.x+" "+w.y));
					gotone=true;
				}
				break;
			} // end of 'g'
			
			case 'i': { // -------------------------------------------------------- 
				
				// inversive distance between 2 circles, not necessarily neighbors
				if (query.startsWith("invdist")) {
					int w;
					NodeLink vlist=new NodeLink(p,items);
					v=vlist.get(0);
					w=vlist.get(1);
					ans.append(p.comp_inv_dist(v,w));
					if (forMsg)
						words.append(" e ("+v+" "+w+")");
					gotone=true;
				}
				break;
			} // end of 'i'
			
			case 'm': {} // fall through
			case 'M': {
				
				// Mobius
				if (query.toLowerCase().startsWith("mob")) {
					forMsg=true;
					
					// side pairing of with some label?
					if (items!=null && items.size()>0) {
						Iterator<String> iit=items.iterator();
						while (iit.hasNext()) {
							String label=iit.next().trim();
							Mobius mb=p.namedSidePair(label);
							if (mb!=null) {
								StringBuilder mobwords=mb.mob2String();
								mobwords.insert(0,new String("Mobius '"+label+":"+System.lineSeparator()+" "));
								words.append(mobwords.toString());
								gotone=true;
							}
						}
					} 
					
					// default
					if (!gotone) {
						StringBuilder mobwords=CPBase.Mob.mob2String();
						mobwords.insert(0, new String("Current Mobius: "+System.lineSeparator()+" "));
						words.append(mobwords.toString());
						gotone=true;
					}
				}
				else if(query.toLowerCase().startsWith("mark")) {
					forMsg=true;
					int ChoiceMode=1;  // 1 for vert (default); 2, face; 3, tile
					if (items!=null && items.size()>0) {
						String str=items.get(0);
						if (StringUtil.isFlag(str)) {
							char c1=str.charAt(1);
							if (c1=='f')
								ChoiceMode=2;
							else if (c1=='t')
								ChoiceMode=3;
							items.remove(0);
						}
						switch(ChoiceMode) {
						case 2: // one face
						{
							v=FaceLink.grab_one_face(p,items.get(0));
							if (v!=0) {
								ans.append(p.faces[v].mark);
								if (forMsg) 
									words.append(" f"+v);
								gotone=true;
							}
						}
						case 3: // one tile
						{
							if (p.tileData==null)
								break;
							v=TileLink.grab_one_tile(p.tileData,items.get(0));
							if (v!=0) {
								ans.append(p.faces[v].mark);
								if (forMsg) 
									words.append(" t"+v);
								gotone=true;
							}
						}
						default: // one vertex
						{
							v=NodeLink.grab_one_vert(p,items.get(0));
							if (v!=0) {
								ans.append(p.kData[v].mark);
								if (forMsg) 
									words.append(" v"+v);
								gotone=true;
							}
						}
						} // end of switch
					}
				}
				break;
			}
			
			case 'n': { } // fall through
			case 'N': {
				
				// nodecount
				if (query.toLowerCase().startsWith("nodecount")) {
					ans.append(p.nodeCount);
					gotone=true;
				}
				break;
			}
			
			case 'p': { // -------------------------------------------------------
				
				// pack number?
				if (query.startsWith("pnum")) {
					ans.append(p.packNum);
					gotone=true;
				}
				break;
			}
			
			case 'q': { // --------------------------------------------------------
				// default: packing 'visual' quality
				// TODO: could bring along flags, but how?
				if (query.startsWith("qual")) { 
					CallPacket cP=CommandStrParser.valueExecute(p,"qual");
					if (cP!=null && cP.double_vec!=null && cP.double_vec.size()==1) {
						ans.append(cP.double_vec.get(0).toString());
						gotone=true;
					}
				}
				break;
			}
			
			case 'R': { // --------------------------------------------------------
				if (query.startsWith("Redchai")) {
					FaceLink redlink=new FaceLink(p,"R");
					int n;
					if (redlink==null || (n=redlink.size())==0)
						throw new CombException("redchain seems to be empty");
					int firstf=redlink.get(0);
					if (forMsg && n>50) {
						n=50;
	  	      			suffix=" ... ";
					}
					n=(n>1000)? 1000:n;
					Iterator<Integer> rlk=redlink.iterator();
					int click=0;
					while (rlk.hasNext() && click<n) {
						ans.append(" "+rlk.next());
					}
					if (!rlk.hasNext()) // if done, close with first face
						ans.append(" "+firstf);
					gotone=true;
				}

				break;
			}
			
			case 'r': { // --------------------------------------------------------
				
				// radius: just one
				if (query.startsWith("rad")) {
					v=NodeLink.grab_one_vert(p, flagSegs);
					if (v!=0) {
						ans.append(p.getRadius(v));
						if (forMsg) 
							words.append(" v"+v);
						gotone=true;
					}
				}
				break;
			} // end of 'r'
			
			case 's': { // --------------------------------------------------------
				
				// socket? 
				if (query.startsWith("socket")) {
					forMsg=true;
					if (CPBase.cpMultiServer!=null) {
						words=new StringBuilder(query+" host: "+
								CPBase.cpSocketHost+", port = "+CPBase.cpSocketPort);
						if (CPBase.socketSources!=null && CPBase.socketSources.size()>0) { 
							words.append("\n  Socket names:)");
							for (int i=0;i<CPBase.socketSources.size();i++)
								words.append(" "+CPBase.socketSources.get(i).sourceName);
						}
						words.append(", socket count ");
					}
					else {
						words=new StringBuilder("Socket Server has not been invoked (see 'socketServe')");
					}
					ans.append(CPBase.socketSources.size());
					gotone=true;
				}
				// screen dimensions
				else if (query.startsWith("screen")) {
					forMsg=true;
					ViewBox vB=p.cpScreen.realBox;
					words=new StringBuilder("Screen for p"+p.packNum+":");
						words.append(System.lineSeparator());
						words.append(" \r\nset_screen -b "+String.format("%." + 4 + "e", vB.lz.x)+" "+
							String.format("%." + 4 + "e", vB.lz.y)+" "+
							String.format("%." + 4 + "e", vB.rz.x)+" "+
							String.format("%." + 4 + "e", vB.rz.y));
					if (p.hes>0 && p.cpScreen.sphView.viewMatrix!=null) {
						Matrix3D m=p.cpScreen.sphView.viewMatrix;
						words.append(String.format("%n","")+"               set_sv -t "+
								String.format("%." + 4 + "e", m.m00)+"  "+
								String.format("%." + 4 + "e", m.m01)+"  "+
								String.format("%." + 4 + "e", m.m02)+"  "+
								String.format("%." + 4 + "e", m.m10)+"  "+
								String.format("%." + 4 + "e", m.m11)+"  "+
								String.format("%." + 4 + "e", m.m12)+"  "+
								String.format("%." + 4 + "e", m.m20)+"  "+
								String.format("%." + 4 + "e", m.m21)+"  "+
								String.format("%." + 4 + "e", m.m22)+"\n");
					}
					gotone=true;
				}
				else if (query.startsWith("status")) {
					if (p.status)
							ans.append("true");
					else
							ans.append("false");
					if (forMsg)
						words.append(" is ");
					gotone=true;
				}
				else if (query.startsWith("schw")) {
					EdgeSimple edge=EdgeLink.grab_one_edge(p, flagSegs);
					if (edge!=null) {
						try {
							int indx=p.nghb(edge.v,edge.w);
							ans.append(String.format("%.6f",p.kData[edge.v].schwarzian[indx]));
						} catch (Exception ex) {
							throw new DataException("query usage: 'schwarzian' is not allocated");
						}
						if (forMsg) 
							words.append(" <v,w>"+edge.v+" "+edge.w);
						gotone=true;
					}
				}
				else if (query.startsWith("sch_flo")) { // add schwarzians around a vertex
					int vv=NodeLink.grab_one_vert(p,flagSegs);
					if (vv!=0) {
						try {
							double accum=0.0;
							for (int j=0;j<p.kData[vv].num;j++)
								accum += p.kData[vv].schwarzian[j];
							ans.append(String.format("%.6f",accum));
						} catch (Exception ex) {
							throw new DataException("query usage: 'schwarzian' may not be allocated");
						}
						if (forMsg)
							words.append(" for vert "+vv);
						gotone=true;
					}	
				}
				break;
			} // end of 's'
			
			case 'v': { // --------------------------------------------------------

				if (query.startsWith("vertexMap")) {
					if (p.vertexMap==null)
						throw new ParserException("packing doesn't have a vertex map");
					int N=p.vertexMap.size();
					Iterator<EdgeSimple> vm=p.vertexMap.iterator();
					int count=0;
					while (vm.hasNext() && (!forMsg || count<12) || count<1000) {
						EdgeSimple edge=vm.next();
						ans.append(edge.v+" "+edge.w+"  ");
						count++;
					}
		  	      	if (count<N)
		  	      		suffix=" ... ";
					gotone=true;
				}
				
				// vert info
				else if (query.startsWith("vertInfo")) {
					forMsg=true; // only do this as a message
		  	      	vertlist=new NodeLink(p,items);
		  	      	int N=vertlist.size();
		  	      	Iterator<Integer> vlist=vertlist.iterator();
		  	      	int count=0;
		  	      	while (vlist.hasNext() && count<5) {
		  	      		v=(Integer)vlist.next();
		  	      		words.append("\n vert#"+v+", p"+p.packNum+": rad="+p.getRadius(v)+
		  	      				", center=("+p.rData[v].center.x+","+p.rData[v].center.y+")"+
		  	      				", ang sum="+p.rData[v].curv/Math.PI+
		  	      				" Pi, aim="+p.rData[v].aim/Math.PI+
		  	      				" Pi, boundaryFlag="+p.kData[v].bdryFlag+
		  	      				", star="+p.kData[v].num+
		  	      				", mark="+p.kData[v].mark+
		  	      				", plotFlag="+p.kData[v].plotFlag+
		  	      				", color="+CPScreen.col_to_table(p.kData[v].color));
		  	      		count++;
		  	      	}
		  	      	if (count<N) {
		  	      		words.append(" ... ");
		  	      	}
	  	      		gotone=true;
		  	      	return words.toString();
				}
				break;
			} // end of 'v'
			case '_': // want current value of a variable.
			{
				forMsg=true; // can only go to message
				int k=query.indexOf(" ");
				if (query.length()<=1 || k==1)
					throw new ParserException("No variable name was given");
				String vkey;
				if (k<0)
					vkey=query.substring(1);
				else
					vkey=query.substring(1,k);
				String varValue=PackControl.varControl.getValue(vkey);
				if (varValue==null || varValue.length()==0)
					throw new ParserException("variable '"+vkey+"' has no stored value");
				if (varValue.length()>100) {
					varValue=varValue.substring(0,100);
					suffix=new String(" ... ");
				}
				words=new StringBuilder("variable '"+vkey+"' ");
				ans.append("\""+varValue.trim()+"\"");
  	      		gotone=true;
				break;
			}
			default: {
				throw new ParserException("");
			}
			
			} // end of switch

			if (!gotone)
				if (forMsg) {
					words.append("Query '"+query+"': none found");
				}
			
		} catch (Exception ex) {
			throw new ParserException("Query '"+query+"' has error or was not recognized: "+ex.getMessage());
		}
		
		// generic return method: note, string depends on 'forMsg'
		if ((ans==null || ans.length()==0) && !forMsg)
			return null;
		if (forMsg) { // for message window? prepend the 'query'
			if (suffix!=null) // may be suffix, e.g. '...'
				return new String(words+" = "+ans.toString()+" "+suffix);
			else if (ans!=null && ans.length()>0)
				return  new String(words+" = "+ans.toString());
			return new String(words);
		}
		return ans.toString();

		// -------------------------------------------- 
		
//	  	  if (query.startsWith("pack_ext") || query.startsWith("extend")) {
//	  		  return jexecute(p,"extender ?");
//	  	  }
//	  	  if (query.startsWith("param")) {}
//	  	  if (query.startsWith("flower")) {}
//	  	  if (query.startsWith("ang_sum")) {}
//	  	  if (query.startsWith("aim")) {}
//	  	  if (query.startsWith("face")) {}
//	  	  if (query.startsWith("over")) {}
//	  	  if (query.startsWith("alt_rad")) {}
//	  	  if (query.startsWith("kap")) {}
//	  	  if (query.startsWith("antip")) {}
//	  	  if (query.startsWith("screen")) {}
//	  	  if (query.startsWith("pk_stat")) {}
//	  	  if (query.startsWith("bdry_dist")) {}
//	  	  if (query.startsWith("edge_p")) {}
//	  	  if (query.startsWith("ratio_ftn")) {}
//	  	  if (query.startsWith("conduct")) {}
//	  	  if (query.startsWith("bdry_length")) {}
//	  	  if (query.startsWith("script")) {}
//	  	  if (query.startsWith("map_rev")) {}
//	  	  if (query.startsWith("map")) {}

	}
	
	/**
	 * Returns String, as in 'queryParse', except the string 
	 * represents the current value of the query quantity, rather 
	 * than the symbolic value.  
	 * 
	 * NOTE: I don't know many instance where this will be used; 
	 *   'queryParse' is more likely.
	 * TODO: add functionality as needed 
	 * 
	 * @param query String of '&<query>' type: the '&' is gone,
	 *    string should be already trimmed.
	 * @return String representation of result
	 */
	public static String curValueParse(PackData p,String query,Vector<Vector<String>> flagSegs) {
		CirclePack.cpb.msg("query '"+query+"' not yet in parser: write Ken");
		return null;
	}
	

}
