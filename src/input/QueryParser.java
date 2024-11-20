package input;

import java.util.Iterator;
import java.util.Vector;

import allMains.CPBase;
import allMains.CirclePack;
import circlePack.PackControl;
import combinatorics.komplex.DcelFace;
import combinatorics.komplex.HalfEdge;
import combinatorics.komplex.RedEdge;
import complex.Complex;
import dataObject.EdgeData;
import dataObject.FaceData;
import dataObject.NodeData;
import dataObject.TileData;
import exceptions.CombException;
import exceptions.DataException;
import exceptions.ParserException;
import ftnTheory.PointEnergies;
import geometry.CommonMath;
import geometry.SphericalMath;
import input.CommandStrParser.Energy;
import komplex.EdgeSimple;
import listManip.FaceLink;
import listManip.HalfLink;
import listManip.NodeLink;
import listManip.TileLink;
import math.Matrix3D;
import math.Mobius;
import packing.PackData;
import util.CallPacket;
import util.StringUtil;
import util.ViewBox;

public class QueryParser {
	
	public static int processQuery(PackData p,
			Vector<String> queryStr,boolean forMsg) {
		StringBuilder strbld=new StringBuilder();
		for (int j=0;j<queryStr.size();j++) 
			strbld.append((String)queryStr.get(j)+" ");
			
		
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
	 * Return a String in response to a query. 
	 * This may have three parts: 'words', 'ans', 
	 * and 'suffix'. 'ans' may represent (in 'String' 
	 * form) a value, list, etc, and generally can 
	 * be used as a variable value in commands.
	 * 
	 * If 'forMsg' is true, query is intended as a 
	 * message, so 'words' is prepended. Note that 
	 * 'words' defaults to "query (p*) = "; may want 
	 * to change this if it doesn't depend on the 
	 * current packing (e.g., '?Vlist'). 
	 * 
	 * Also, if 'forMsg', then some lists may be limited 
	 * to 12 items, e.g. if vertex list is longer, put in 
	 * '...' via a 'suffix' string. Long strings are 
	 * likewise truncated.
	 * 
	 * TODO: add additional queries and functionality 
	 * as needed 
	 * 
	 * @param query, String of '?<query>' type: the '?' is gone,
	 *    string should have been trimmed already 
	 * @param flagSegs, usual sequence of flag segments
	 * @param forMsg boolean: true if result will be 
	 * 		reported as a message rather than used 
	 * 		for something else, like setting a variable.
	 * @return String representation of result, null 
	 * 		on error
	 */
	public static String queryParse(PackData p,
			String query,Vector<Vector<String>> flagSegs,
			boolean forMsg) {
		StringBuilder ans=new StringBuilder(""); // result of the query alone
		// some utility variables
	  	int v;
		String firststr=null;
		// by default,  to use if 'forMsg'
		StringBuilder words=
			new StringBuilder(query+" (p"+p.packNum+") "); 
		String suffix=null;
		boolean gotone=false;
		String exception_words=null; // words when an exception is caught
		
		// utility: note first set of strings and 
		// 	its first string
		Vector<String> items=null;
		try {
			items=(Vector<String>)flagSegs.get(0); 
			firststr=items.get(0); // sometimes will want to remove this
		} catch(Exception ex) { // some commands don't need anything
			firststr=null;
		}
		
		try {
			char c=query.charAt(0);
			
			// handle any 'list' requests first: 
			//    limit is 1000 or 12 if intended 
			//    as a message.
			if (query.length()>=5 && query.substring(1,5).
					equalsIgnoreCase("list")) {
				int n=0;
				switch(c) {
				case 'h': {
					if (p.hlist==null || p.hlist.size()==0) {
						if (forMsg)
							ans.append("empty");
						break;
					}
					gotone=true;
					n=p.hlist.size();
					if (forMsg && n>12) {
						n=12;
		  	      		suffix=" ... ";
					}
					n=(n>1000)? 1000:n;
					Iterator<HalfEdge> hlst=p.hlist.iterator();
					int click=0;
					while (hlst.hasNext() && click<n) {
						HalfEdge edge=hlst.next();
						ans.append(" "+edge+"  ");
					}
					break;
				} 
				case 'H': {
					if (CPBase.Hlink==null || CPBase.Hlink.size()==0) {
						if (forMsg)
							ans.append("empty");
						break;
					}
					gotone=true;
					n=CPBase.Hlink.size();
					if (forMsg && n>12) {
						n=12;
		  	      		suffix=" ... ";
					}
					n=(n>1000)? 1000:n;
					Iterator<HalfEdge> hlst=PackControl.Hlink.iterator();
					int click=0;
					while (hlst.hasNext() && click<n) {
						HalfEdge edge=hlst.next();
						ans.append(" "+edge+"  ");
					}
					break;
				}
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
				case 'D': {
					if (CPBase.Dlink==null || CPBase.Dlink.size()==0) {
						if (forMsg)
							ans.append("empty");
						break;
					}
					gotone=true;
					n=CPBase.Dlink.size();
					if (forMsg && n>12) {
						n=12;
		  	      		suffix=" ... ";
					}
					n=(n>1000)? 1000:n;
					Iterator<Double> vlst=CPBase.Dlink.iterator();
					int click=0;
					while (vlst.hasNext() && click<n) {
						ans.append(" "+vlst.next().toString());
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
				
				} // end of switch for "list" entries
				
			}

			// now process the rest
			else switch(c) {
			
			// NOTE: as queries added, also add to 'CmdCompletion.txt'
			
			case 'a': { // -----------------------------------
			
				// angle aim/pi (just one)
				if (query.startsWith("aim")) {
					v=NodeLink.grab_one_vert(p,flagSegs);
					ans.append(Double.toString(p.getAim(v)/Math.PI));
					gotone=true;
				}
				
				// list all (non-ideal) face angles (NOT divided by PI) 
				//   at given v. Assume packing is laid out so we can
				//   return actual angles computed from centers,
				//   along with angles computed from radii
				else if (query.startsWith("angles_at")) {
					v=NodeLink.grab_one_vert(p,flagSegs);
					if (v!=0) {
						words.append("vert "+v+", faces: ");
						int[] fflower=p.getFaceFlower(v);
						int n=fflower.length;
						double angsum=0.0;
						double intsum=0.0;
						if (!p.packDCEL.vertices[v].isBdry())
							n=n-1; // remove the repeat
						for (int k=0;k<n;k++) {
							DcelFace face=p.packDCEL.faces[fflower[k]];
							int[] verts=face.getVerts(v);
							
							// Compute actual (i.e., based on centers)
							Complex z0=p.getCenter(verts[0]);
							Complex z1=p.getCenter(verts[1]);
							Complex z2=p.getCenter(verts[2]);
							double ang=0.0;
							if (p.hes<0) { // hyperbolic
								if (p.getRadius(verts[0])>0) {
									Mobius mob=Mobius.mobNormDisc(z0,z1);
									Complex newz2=mob.apply(z2); 
									// mob.apply(z0);
									// mob.apply(z1); 
									// mob.apply(z2).arg();
									double arg2=newz2.arg();
									if (arg2<=0)
										arg2+=2.0*Math.PI;
									ang=arg2-Math.PI/2;
								}
							}
							else if (p.hes==0) { // eucl
								ang=z2.minus(z0).divide(z1.minus(z0)).arg();
							}
							else { // sph -- may be ambiguous, not checked
								double[] tanvec1=SphericalMath.sph_tangent(z0,z1);
								double[] tanvec2=SphericalMath.sph_tangent(z0,z2);
								ang=Math.acos(SphericalMath.dot_prod(tanvec1, tanvec2));
							}
							angsum+=ang;
							
							// Compute intneded (i.e., based on radii)
							
							// find inv distances
							HalfEdge he=face.edge;
							HalfEdge startedge=null;
							for (int j=0;j<3 && (startedge==null);j++) {
								if (he.origin.vertIndx==v)
									startedge=he;
								he=he.next;
							}
							double ivd0=startedge.getInvDist();
							double ivd1=startedge.next.getInvDist();
							double ivd2=startedge.next.next.getInvDist();
							double r0=p.getRadius(verts[0]);
							double r1=p.getRadius(verts[1]);
							double r2=p.getRadius(verts[2]);
							double intang=CommonMath.get_face_angle(r0, r1, r2, ivd0,ivd1,ivd2,p.hes);
							intsum +=intang;
							
							// insert results
							words.append(face.faceIndx+",");
							ans.append(ang+" ("+intang+")  ");
							gotone=true;
						} // end of if
						words.append("; actual (intended) angles: ");
						ans.append("; actual (intended) angle sum= "+angsum+" ("+intsum+")");
					}
					else
						CirclePack.cpb.errMsg("Usage: ?angle_at {v}");
				}
				
				// angle sum/pi (just one)
				else if (query.startsWith("anglesum")) {
					v=NodeLink.grab_one_vert(p,flagSegs);
					if (v!=0) {
						ans.append(Double.toString(p.getCurv(v)/Math.PI));
						if (forMsg) 
							words.append("v"+v+": ");
						gotone=true;
					}
				}
				
				else if (query.startsWith("antip")) {
					NodeLink vlist=new NodeLink(p,items);
					if (vlist!=null) {
						ans.append(p.gen_mark(vlist,-1,false));
						if (forMsg) { 
							words.append("(furthest away): ");
						}
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
						Complex z=p.getCenter(v);
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
				
			case 'd': 
			case 'D': 
			{
				if (query.toLowerCase().startsWith("dce")) {
					if (p.packDCEL!=null)
						words.append("yes, DCEL exists");
					else
						words.append("no, DCEL does NOT exist");
					gotone=true;
				}
				break;
			} // end of 'd', 'D'
			
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
					else {
						exception_words="?energy usage: valid type not indicated";
						throw new ParserException("");
					}
					// TODO: could consider negative infinity energy (e.g., power -100)
					
					ans.append(energy);
					gotone=true;
				}
				else if(query.startsWith("edge")) {
					forMsg=true; // only do this as a message
					HalfEdge he=HalfLink.grab_one_edge(p,
							StringUtil.reconItem(items));
					if (he!=null) {
						EdgeData eData=new EdgeData(p,he);
						words.append("p"+p.packNum+"; edge ("+eData.edgeStr+
								"); inv distance="+eData.invDist+
								"; Schwarzian="+eData.schwarzian+"; edgelength="+
								eData.edgelength+"; intended edgelength="+
								eData.intended);
						return words.toString();
					}
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
						exception_words="?flower usage: 'v'";
						throw new ParserException("");
					}
					words.append(" v"+vv); // show which vert
					int num=p.countFaces(vv);
					if (forMsg && num>12) {
						num=12;
		  	      		suffix=" ... ";
					}
					int j=0;
					int[] flower=p.packDCEL.vertices[vv].getFlower(true);
					while (j<=num) {
						ans.append(Integer.toString(flower[j])+" ");
						j++;
					}
					gotone=true;
				}
				
				// return f(z)
				else if (query.startsWith("f(z)")) {
					double x=0;
					double y=0;
					try { // one (real) or two (complex)
						x=Double.parseDouble(items.get(0));
						try {
							y=Double.parseDouble(items.get(1));
						} catch (Exception ex) {}
					} catch (Exception ex) {
						exception_words="?f(z) usage: 'x [y]' for complex argument";
						throw new ParserException("");
					}
					Complex w=CirclePack.cpb.getFtnValue(new Complex(x,y));
					if (Math.abs(y)<CPBase.GENERIC_TOLER) // if real, suppress the y
						ans.append(Double.toString(w.x));
					else 
						ans.append(new String(w.x+" "+w.y));
					gotone=true;
				}
				
				// face data
				else if (query.startsWith("face")) {
					forMsg=true; // only do this as a message
					FaceData fData;
					try { // one integer
						int f=Integer.parseInt(items.get(0));
						fData=new FaceData(p,f);
					} catch (Exception ex) {
						exception_words="?face <f> needs 'f'";
						throw new ParserException("");
					}
					words.append("p"+p.packNum+"; face "+fData.findx+
							"; vertices={"+fData.vertsStr+"}; colorcode="+
							fData.colorCode+"; mark="+fData.mark);
					return words.toString();
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
						exception_words="?gam(t) usage: 't' for real argument";
						throw new ParserException("");
					}
					Complex w=CirclePack.cpb.getParamValue(t);
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

				// vertex_map inverse
				if (query.startsWith("map_i")) {
					if (p.vertexMap==null) {
						exception_words="?vertexMap usage: packing has no vertex map";
						throw new ParserException("");
					}
					NodeLink vlist=new NodeLink(p,items);
					int N=vlist.size();
					int count=0;
					Iterator<Integer> vlst=vlist.iterator();
					while (vlst.hasNext() && ((!forMsg || count<12) || count<1000)) {
						int w=vlst.next();
						int vv=p.vertexMap.findV(w);
						if (vv>0) {
							ans.append("{"+vv+" , "+w+"} ");
							count++;
						}
					}
		  	      	if (count<N)
		  	      		suffix=" ... ";
					gotone=true;
				}

				// vertex_map
				else if (query.startsWith("map")) {
					if (p.vertexMap==null) {
						exception_words="?vertexMap usage: packing has no vertex map";
						throw new ParserException("");
					}
					NodeLink vlist=new NodeLink(p,items);
					int N=vlist.size();
					int count=0;
					Iterator<Integer> vlst=vlist.iterator();
					while (vlst.hasNext() && ((!forMsg || count<12) || count<1000)) {
						int vv=vlst.next();
						int w=p.vertexMap.findW(vv);
						if (w>0) {
							ans.append("{"+vv+" , "+w+"} ");
							count++;
						}
					}
		  	      	if (count<N)
		  	      		suffix=" ... ";
					gotone=true;
				}

				// Mobius
				else if (query.toLowerCase().startsWith("mob")) {
					forMsg=true;
					
					// side pairing with some label?
					if (items!=null && items.size()>0) {
						Iterator<String> iit=items.iterator();
						while (iit.hasNext()) {
							String label=iit.next().trim();
							Mobius mb=p.namedSidePair(label);
							if (mb!=null) {
								StringBuilder mobwords=mb.mob2String();
								mobwords.insert(0,new String("Mobius '"+label+"':"+System.lineSeparator()+" "));
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
								ans.append(p.getFaceMark(v));
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
								ans.append(p.getFaceMark(v));
								if (forMsg) 
									words.append(" t"+v);
								gotone=true;
							}
						}
						default: // one vertex
						{
							v=NodeLink.grab_one_vert(p,items.get(0));
							if (v!=0) {
								ans.append(p.getVertMark(v));
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
					if (p.packDCEL.redChain==null) {
						exception_words="?Redchain usage: appears to be empty";
						throw new CombException("");
					}
					RedEdge rtrace=p.packDCEL.redChain;
					int click=0;
					do {
						ans.append(" <"+rtrace.myEdge+">");
						rtrace=rtrace.nextRed;
					} while (rtrace!=p.packDCEL.redChain && click<12);
					if (click==12) // if done, close with first face
						ans.append(" ... ");
					gotone=true;
				}
				break;
			}
			
			case 'r': { // --------------------------------------------------------
				
				// radius: just one
				if (query.startsWith("rad")) {
					v=NodeLink.grab_one_vert(p, flagSegs);
					if (v!=0) {
						ans.append(p.getActualRadius(v));
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
					ViewBox vB=p.cpDrawing.realBox;
					words=new StringBuilder("Screen for p"+p.packNum+":");
						words.append(System.lineSeparator());
						words.append(" \r\nset_screen -b "+String.format("%." + 4 + "e", vB.lz.x)+" "+
							String.format("%." + 4 + "e", vB.lz.y)+" "+
							String.format("%." + 4 + "e", vB.rz.x)+" "+
							String.format("%." + 4 + "e", vB.rz.y));
					if (p.hes>0 && p.cpDrawing.sphView.viewMatrix!=null) {
						Matrix3D m=p.cpDrawing.sphView.viewMatrix;
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
				else if (query.startsWith("sch_flo")) { // add schwarzians around a vertex
					int vv=NodeLink.grab_one_vert(p,flagSegs);
					if (vv!=0) {
						try {
							double accum=0.0;
							HalfLink spokes=p.packDCEL.vertices[vv].getEdgeFlower();
							Iterator<HalfEdge> sis=spokes.iterator();
							while (sis.hasNext()) {
								HalfEdge he=sis.next();
								accum+=he.getSchwarzian();
							}
							ans.append(String.format("%.6f",accum));
						} catch (Exception ex) {
							throw new DataException("");
						}
						if (forMsg)
							words.append(" for vert "+vv);
						gotone=true;
					}	
				}
				else if (query.startsWith("uerr")) {
					// int vv=NodeLink.grab_one_vert(p,flagSegs);
					
					// TODO: finish this; need code in
					// 'schwarzian.java' for checking schwarzian
					// packing condition at interior vertex.
					
					break;
				}
				else if (query.startsWith("sch")) {
					HalfLink hlink=new HalfLink(p,flagSegs.get(0));
					int tick=0;
					Iterator<HalfEdge> his=hlink.iterator();
					while (tick<10 && his.hasNext()) {
						HalfEdge edge=his.next();
						if (edge!=null) {
							try {
								ans.append(String.format(" %.6f ",edge.getSchwarzian()));
							} catch (Exception ex) {
								throw new DataException("");
							}
							if (forMsg) 
								words.append(" <v,w>"+edge);
							gotone=true;
						}
					}
				}
				break;
			} // end of 's'
			
			
			case 't': { // ----------------------------------------
				if (query.startsWith("tile") && p.tileData!=null) {
					forMsg=true; // only do this as a message
					int t=TileLink.grab_one_tile(p.tileData,StringUtil.reconItem(items));
		  	      	TileData tData=new TileData(p,t);
		  	      	words.append("p"+p.packNum+"; tile indx="+tData.tindx+
		  	      			"; degree="+tData.degree+"; tileflower={"+
		  	      			tData.nghbStr+"}; mark="+tData.mark+
		  	      			"; colorCode="+tData.colorCode);
		  	      	return words.toString();
				}
				break;
			}
			
			case 'v': { // --------------------------------------------------------
				
				// vert info
				if (query.startsWith("vert")) {
					forMsg=true; // only do this as a message
					v=NodeLink.grab_one_vert(p,StringUtil.reconItem(items));
	  	      		NodeData vData=new NodeData(p,v);
	  	      		words.append("p"+p.packNum+"; vert="+vData.vindx+
	  	      				"; rad="+vData.rad+"; center=("+vData.center+
	  	      				"); flower={"+vData.flowerStr+"}; sum="+
	  	      				vData.angsum/Math.PI+" Pi; aim="+vData.aim/Math.PI+
	  	      				" Pi; boundary?="+vData.bdryflag+
	  	      				"; degree="+vData.degree+"; mark="+vData.mark+
	  	      				"; colorCode="+vData.colorCode);
		  	      	return words.toString();
				}
				break;
			} // end of 'v'
			case '_': // want current value of a variable.
			{
				forMsg=true; // can only go to message
				int k=query.indexOf(" ");
				if (query.length()<=1 || k==1) {
					exception_words="?_<variable> usage: No variable name was given";
					throw new ParserException("");
				}
				String vkey;
				if (k<0)
					vkey=query.substring(1);
				else
					vkey=query.substring(1,k);
				String varValue=PackControl.varControl.getValue(vkey);
				if (varValue==null || varValue.length()==0) {
					exception_words="?_<variable> usage: variable '\"+vkey+\"' has no stored value";
					throw new ParserException("");
				}
				if (varValue.length()>100) {
					varValue=varValue.substring(0,100);
					suffix=new String(" ... ");
				}
				words=new StringBuilder("variable '"+vkey+"' ");
				ans.append("\""+varValue.trim()+"\"");
  	      		gotone=true;
				break;
			}
			case '$': // enclosed to be sent for math evaluation
			{
				try {
					ans.append(StringUtil.getMathString(query));
					gotone=true;
				} catch(Exception ex) {
					exception_words="$ no valid math expression";
					throw new ParserException("");
				}
				break;
			}
			default: {
				exception_words="? no valid query key word";
				throw new ParserException("");
			}
			
			} // end of switch

			if (!gotone)
				if (forMsg) {
					words.append("Query '"+query+"': none found");
				}
			
		} catch (Exception ex) {
			if (exception_words!=null) 
				throw new ParserException(" Query problem: "+exception_words);
			throw new ParserException("Query '"+query+"' has error or was not recognized: ");
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
//	  	  if (query.startsWith("alt_rad")) {}
//	  	  if (query.startsWith("kap")) {}
//	  	  if (query.startsWith("pk_stat")) {}
//	  	  if (query.startsWith("bdry_dist")) {}
//	  	  if (query.startsWith("ratio_ftn")) {}
//	  	  if (query.startsWith("conduct")) {}
//	  	  if (query.startsWith("bdry_length")) {}
//	  	  if (query.startsWith("script")) {}

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
