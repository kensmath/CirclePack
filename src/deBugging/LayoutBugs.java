package deBugging;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;

import allMains.CirclePack;
import dcel.PackDCEL;
import input.CPFileManager;
import komplex.EdgeSimple;
import komplex.Face;
import komplex.RedEdge;
import komplex.RedList;
import komplex.SideDescription;
import listManip.FaceLink;
import listManip.GraphLink;
import listManip.PairLink;
import listManip.VertList;
import packing.PackData;

/**
 * Debugging aids: 
 * 
 * * "print" involves writing to standard out, often useful while in
 *   the debugger, since threads don't get in the way.
 *   
 * * "log" goes to file in the tmp directory.
 * 
 * Some of these are internal only; a limited number are available to 
 * the user via the 'debug' command.
 * 
 * NOTE: many redundancies here; whole thing needs to be rationalized.
 * @author kens
 *
 */
public class LayoutBugs {
	
	static File tmpdir=new File(System.getProperty("java.io.tmpdir"));
	static int rankStamp=1; // progressive number to distinguish file instances

	/** 
	 * log details of 'RedList' and/or 'RedEdge' lists in 'redface_xx_log.txt'.
	 * Includes hash codes to confirm that the correct objects are references.
	 * (Safer than just looking at face index, e.g.)
	 * @param p, PackData
	 * @param redface, RedList, can be null
	 * @param rededge, RedEdge, can be null
	 * @return count, 0 if one of the pointers is missing
	*/
	public static int log_Red_Hash(PackData p,RedList redface,RedEdge rededge) {
		  int count=0;
		  RedList rtrace;
		  RedEdge etrace;

		  String filename=new String("redface_"+(rankStamp++)+"_log.txt");
		  BufferedWriter dbw=CPFileManager.appendToFP(tmpdir,filename,false);
		  try {
			  CirclePack.cpb.msg("'RedList' and 'RedEdge' info logged to: "+
					  tmpdir.toString()+File.separator+filename);

			  // 'RedList'
			  if (redface==null) {
		    	  dbw.write("   No pointer to red chain ??\n");
		    	  dbw.flush();
		    	  dbw.close();
		      }
		      else {
			      dbw.write("\nRed face list: ------------------- \n\n");
		    	  print_one_redface(dbw,p,redface);
		    	  rtrace=redface.next;
		    	  while (rtrace!= redface && count<1000) {
		    		  print_one_redface(dbw,p,rtrace);
		    		  count++;
		    		  rtrace=rtrace.next;
		    	  }
		      }
		      
			  // 'RedList'
		      if (rededge==null) {
		    	  dbw.write("   No pointer to red EDGE chain ??\n");
		    	  dbw.flush();
		    	  dbw.close();
		    	  return 1;
		      }
		      dbw.write("\nRed Edge list: ================ \n\n");
		      print_one_redEdge(dbw,p,rededge);
		      etrace=rededge.nextRed;
		      while (etrace!= rededge && count<1000) {
		    	  print_one_redEdge(dbw,p,etrace);
		    	  count++;
		    	  etrace=etrace.nextRed;
		      }
		      		      
		      dbw.flush();
		      dbw.close();
		  } catch(Exception ex) {
		      System.err.print(ex.toString());
		  }
		  return count;
	}
	
	/**
	 * log main information on 'RedList' and/or 'RedEdge', put in 'RedList_xx_log.txt'. 
	 * @param p, PackData
	 * @param redface, RedList
	 * @return count
	 */
	public static int log_RedList(PackData p,RedList redface) {
		  int count=0;
		  RedList rtrace;

		  String filename=new String("RedList_"+(rankStamp++)+"_log.txt");
		  BufferedWriter dbw=CPFileManager.openWriteFP(tmpdir,filename,false);
		  try {
			  CirclePack.cpb.msg("debug RedList to: "+tmpdir.toString()+File.separator+filename);
			  dbw.write("List from 'log_RedList' ------------------- "+
					  "'firstRedEdge'="+p.firstRedEdge+"; 'firstRedFace'="+p.firstRedFace+"\n\n");
			  if (redface==null) {
				  dbw.write("   No pointer to red chain ??\n");
				  dbw.flush();
				  dbw.close();
				  return 1;
			  }
			  writeRed(redface.packData,redface,dbw);
		      rtrace=redface.next;
		      while (rtrace!= redface && count<1000) {
				  writeRed(redface.packData,rtrace,dbw);
			      count++;
			      rtrace=rtrace.next;
		      }
		      
		      dbw.flush();
		      dbw.close();
		  } catch(Exception ex) {
		      System.err.print(ex.toString());
		  }
		  return count;
	}
	
	/**
	 * log full redChain face/center/rad info, put in 'RedCenters_xx_log.txt'. 
	 * @param p, PackData
	 * @return count
	 */
	public static int log_RedCenters(PackData p) {
		  int count=0;
		  RedList trace=p.redChain;

		  String filename=new String("RedCenters_"+(rankStamp++)+"_log.txt");
		  System.out.println("writing "+tmpdir+"\\"+filename);
		  BufferedWriter dbw=CPFileManager.openWriteFP(tmpdir,filename,false);
		  try {
			  CirclePack.cpb.msg("debug RedCenters to: "+tmpdir.toString()+File.separator+filename);
			  dbw.write("List from 'log_RedCenters' ------ "+
					  "'firstRedEdge'="+p.firstRedEdge.face+"; 'redChain'="+p.redChain.face+"\n\n");
			  
			  boolean goon=true;
		      while ((trace!= p.redChain || goon) && count<1000) {
		    	  goon=false;
		    	  try {
		    		  if (trace instanceof RedEdge) {
		    			  RedEdge redg=(RedEdge)trace;
		    			  if (redg.face==redg.prevRed.face)
		    				  dbw.write("RedEdge: ++++++++++++++++++ Second copy");
		    			  dbw.write("RedEdge:\n");
		    		  }
		    		  else { 
		    			  dbw.write("--- List:\n");
		    		  }
		    		  int[] verts=p.faces[trace.face].vert;
		    		  dbw.write(" Face " + trace.face+", <"+verts[0]+","+verts[1]+","+verts[2]+
		    				  ">    (trace hash="+trace.hashCode()+")");
		    		  dbw.write("\n          prev/next face = "+trace.prev.face+"/"+trace.next.face);
		    		  if (trace.next.face==trace.prev.face)
		    			  dbw.write(" ++++++++++ [blue face:]");
		    		  dbw.write("\n    Lays out vert "+p.faces[trace.face].vert[trace.vIndex]);
		    		  dbw.write("\n          center = ("+trace.center+"), and rad = "+trace.rad+"\n");
		    	  } catch (Exception ex) {
		    		  System.err.print(ex.toString());
		    	  }		  
		    	  count++;
		    	  if (trace instanceof RedEdge) { // handle duplicated 'RedEdge'
		    		  RedEdge redg=(RedEdge)trace;
		    		  if (redg.face==redg.nextRed.face)
		    			  trace=(RedList)redg.nextRed;
		    		  else
		    			  trace=trace.next;
		    	  }
		    	  else 
		    		  trace=trace.next;
		      }
			  dbw.write("----------------------------------------------- \n\n");

		  } catch(Exception ex) {
		      System.err.print(ex.toString());
		  }
		  
		  if (p.getSidePairs()!=null) {
			  int n=p.getSidePairs().size();
			  try{
			  dbw.write("There are "+n+" side pairs:\n");

			  for (int k=0;k<n;k++) {
				  RedEdge startedge=((SideDescription)p.getSidePairs().get(k)).startEdge;
				  RedEdge endedge=((SideDescription)p.getSidePairs().get(k)).endEdge;

		    		  dbw.write("startEdge("+k+") face = "+startedge.face+" (with hash="+startedge.hashCode()+")");
		    		  dbw.write("\n   vert = "+p.faces[startedge.face].vert[startedge.vIndex]+", radius="+startedge.rad);
		    		  dbw.write("\n   center ("+startedge.center.x+","+startedge.center.y+")\n\n");
		    		  dbw.write("endEdge("+k+") face = "+endedge.face+" (with hash="+endedge.hashCode()+")");
		    		  dbw.write("\n   vert = "+p.faces[endedge.face].vert[endedge.vIndex]+", radius="+endedge.rad);
		    		  dbw.write("\n   center ("+endedge.center.x+","+endedge.center.y+")\n\n");
			  }
		      dbw.flush();
		      dbw.close();
	    	  } catch (Exception ex) {
	    		  System.err.print(ex.toString());
	    	  }		  

		  }
		  
		  return count;
	}
	
	/**
	 * log packing "face order" and main details in 'faceOrder_xx_log.txt'
	 * @param p PackData
	 * @return count, 0 on exception
	 */
	public static int log_faceOrder(PackData p) {
		  int count=0;

		  String filename=new String("faceOrder_"+(rankStamp++)+"_log.txt");
		  BufferedWriter dbw=CPFileManager.openWriteFP(tmpdir,filename,false);
		  try {
			  CirclePack.cpb.msg("debug faceorder to: "+tmpdir.toString()+File.separator+filename);
		      dbw.write("\nFace order: nodeCount "+p.nodeCount+
		    		  ", faceCount "+p.faceCount+"\n  firstFace "+p.firstFace+
		    		  ", firstRedFace "+p.firstRedFace+
		    		  ", redChain "+p.redChain.face+" --- \n\n");
		      int nf=p.firstFace;
		      boolean keepon=true;
		      while ((nf>0 && nf<=p.faceCount && nf!=p.firstFace && count<=2*p.faceCount) || keepon) {
		    	  keepon=false;
		    	  Face face=p.faces[nf];
		    	  writeFace(p,nf,dbw);
		    	  nf=face.nextFace;
		    	  count++;
		      }
			  dbw.flush();
			  dbw.close();
		  } catch (Exception ex){
		      System.err.print(ex.toString());
		  }
		  return count;
	}

	/**
	 * log order pairs from a 'GraphLink', typically a face tree, in 'graphLink_xx_log.txt'
	 * See 'DualGraph.printGraph(dlink)', which may be more convenient.
	 * @param p PackData
	 * @param dlink GraphLink
	 * @return count, 0 on exception
	 */
	public static int log_GraphLink(PackData p,GraphLink dlink) {
		  int count=0;

		  String filename=new String("graphLink_"+(rankStamp++)+"_log.txt");
		  BufferedWriter dbw=CPFileManager.appendToFP(tmpdir,filename,false);
		  try {
			  CirclePack.cpb.msg("debug GraphLink to: "+tmpdir.toString()+File.separator+filename);
		      dbw.write("\nGraphLink of directed pairs of faces: faceCount "+p.faceCount+
		    		  "\n  firstFace "+p.firstFace+
		    		  ", firstRedFace "+p.firstRedFace+
		    		  ", redChain "+p.redChain.face+" --- \n\n");
		      
		      Iterator<EdgeSimple> dlk=dlink.iterator();
		      while (dlk.hasNext()) {
		    	  EdgeSimple edge=dlk.next();
		    	  dbw.write(edge.v+"  "+edge.w+"\n");
		    	  count++;
		      }
			  dbw.flush();
			  dbw.close();
		  } catch (Exception ex){
			  return 0;
		  }
		  return count;
	}

	/**
	 * log faces in 'VertList' in 'build_faceOrder_xx_log.txt'
	 * @param p, PackData
	 * @param fDo, VertList
	 * @return count, or 0 on error or exception
	 */
	public static int log_build_faceOrder(PackData p,VertList fDo) {
		  int count=0;

		  String filename=new String("build_faceOrder_"+(rankStamp++)+"_log.txt");
		  BufferedWriter dbw=CPFileManager.appendToFP(tmpdir,filename,false);
		  try {
			  CirclePack.cpb.msg("debug faceOrder to: "+tmpdir.toString()+File.separator+filename);
		      dbw.write("\nFace order from 'build_redchain': nodeCount "+p.nodeCount+
		    		  ", faceCount "+p.faceCount+", firstFace "+p.firstFace+
		    		  ", firstRedFace "+p.firstRedFace+" --- \n\n");
		      VertList trace=fDo;
		      while (trace!=null) {
		    	  if (trace.v<1 || trace.v>p.faceCount) { 
		    		  dbw.write("\nError: face number "+trace.v+" in error.\n");
		    		  dbw.flush();
		    		  dbw.close();
		    		  return 0;
		    	  }
		    	  int nf=trace.v;
		    	  Face face=p.faces[nf];
		    	  dbw.write("Face "+nf+": ["+face.vert[0]+","+face.vert[1]+","+face.vert[2]+"]; "+
		    			  "indexFlag "+face.indexFlag+
		    			  ",plotvert="+face.vert[(face.indexFlag+2)%3]+
		    			  ",rwbFlag="+face.rwbFlag+
		    			  ",nextFace="+face.nextFace+
		    			  ",nextRed="+face.nextRed+
		    			  ",plotFlag="+face.plotFlag+"\n");
		    	  	count++;
		    	  trace=trace.next;
		      }
			  dbw.flush();
			  dbw.close();
		  } catch (Exception ex){
			  return 0;
		  }
		  return count;
	}

	/** 
	 * Log details of 'PairLink' of 'SideDescription's in 'SideDescriptions_xxxx_log.txt'
	 * @param p, PackData
	 * @param pairs, PairLink
	 * @return count 
	*/
	public static int log_PairLink(PackData p,PairLink pairs) {
		  int count=0;

		  String filename=new String("SideDescriptions_"+(rankStamp++)+"_log.txt");
		  BufferedWriter dbw=CPFileManager.appendToFP(tmpdir,filename,false);
		  try {
			  CirclePack.cpb.msg("debug 'SideDescription' to: "+tmpdir.toString()+File.separator+filename);
			  dbw.write("SideDesriptions ============================= \n\n");
			  
			  Iterator<SideDescription> eps=pairs.iterator();
			  while (eps.hasNext()) {
				  SideDescription ep=(SideDescription)eps.next();
				  dbw.write("Index "+count+":\n");
				  count++;
				  dbw.write("spIndex="+ep.spIndex+";  mateIndex="+ep.mateIndex+"\n");
				  if (ep.startEdge!=null) dbw.write("startEdge (face) "+ep.startEdge.face);
				  if (ep.endEdge!=null) dbw.write(";  endEdge (face) "+ep.endEdge.face+"; \n");
				  if (ep.pairedEdge!=null) { 
					  try {
					  dbw.write("pairedEdge: (face, mateface): ("+ep.pairedEdge.startEdge.face+","+ep.pairedEdge.endEdge.face+")\n");
					  } catch(Exception ex) {};
				  }
			  }
			  dbw.write("=============\n");
			  dbw.flush();
			  dbw.close();
		  } catch(Exception ex) {
		      System.err.print(ex.toString());
		  }
		  return count;
	}

	/**
	 * List faces indices of given 'RedList' in string. (Usually included
	 * in message printed to System.err.)
	 * @param redface, RedList
	 * @return String
	 */
	public static String quick_redlist(RedList redface) {
	  RedList trace=redface;
	  StringBuilder strb=new StringBuilder(" "+trace.face); // first
	  trace=trace.next;
	  while (trace!=redface) {
		  strb.append(" "+trace.face);
		  trace=trace.next;
	  }
	  strb.append(" "+trace.face); //  last 
	  return strb.toString();
	}

	/**
	 * List face indices of given 'RedList' in FaceLink; for debugging red chain
	 * @param redface, RedList
	 * @return FaceLink
	 */
	public static FaceLink redChain2redLink(RedList redface) {
	  RedList trace=redface;
	  FaceLink flink=new FaceLink();
	  trace=trace.next;
	  while (trace!=redface) {
		  flink.add(trace.face);
		  trace=trace.next;
	  }
	  if (flink.size()==0)
		  return null;
	  return flink;
	}

    /** 
     * print ordered 'RedFace' list to System.err 
     * @param p, PackData
     * @return count
     */
	public static int pfacered(PackData p) {

		int f,count=1;

		int stop=p.firstRedFace;
		System.err.println("Pack red face order: "+stop+"\n");
		f=stop;
		while (p.faces[f].nextRed!=stop  && count < 2*p.faceCount
				&& p.faces[f].nextRed >0 && p.faces[f].nextRed <=p.faceCount 
				&& (count++)>0)
			System.err.println(" "+(f=p.faces[f].nextRed)+" ");
		return count;
	}

    /** 
     * print ordered 'RedFace' list to System.err 
     * @param p, PackData
     * @return count
     */
	public static int pRedEdges(PackData p) {

		int count=1;

		RedEdge stop=p.firstRedEdge;
		RedEdge rtrace= stop;
		boolean keepon=true;
		System.err.println("Pack RedEdge order: "+stop.face+"\n");
		while (rtrace!=stop || keepon) {
			keepon=false;
			System.err.println(" f="+(rtrace.face)+" , v="+
					p.faces[rtrace.face].vert[rtrace.vIndex]+
					", center = "+rtrace.center.x+" "+rtrace.center.y);
			rtrace=rtrace.nextRed;
			count++;
		}

		System.err.println("count was "+count);
		return count;
	}

	/** 
     * print ordered 'RedList' list to System.err 
     * @param p, PackData
     * @return count
     */
	public static int pRedList(PackData p) {

		int count=1;

		RedList stop=p.redChain;
		RedList rtrace= stop;
		boolean keepon=true;
		System.err.println("Pack RedList: "+stop.face+"\n");
		while (rtrace!=stop || keepon) {
			keepon=false;
			System.err.println(" f="+(rtrace.face)+" , v="+
					p.faces[rtrace.face].vert[rtrace.vIndex]+
					", center = "+rtrace.center.x+" "+rtrace.center.y);
			rtrace=rtrace.next;
			count++;
		}

		System.err.println("count was "+count);
		return count;
	}
		
	/** 
	 * Check doubly linked 'RedList' and print info to System.err
	 * @param handle, RedList
	 * @return count 
	 */
	public static int showhandle(RedList handle) {
		int count=0,total=1,first,last=0;
		RedList trace;

		if ((trace=handle.next)==null) return 0;
		first=trace.face;
		while (trace!=handle && total < 2000) {
			total++;
			last=trace.face;
			trace=trace.next;
		}
		System.err.println("\nCheck double linked face list: count="+total+
					", first="+first+",last="+last);
		while (trace!=handle && count <= total+1) {
			count++;
			System.err.println("  "+trace.face);
			if (trace.prev.next!=trace)
				System.err.println(" prev.next wrong: "+handle.prev.face+" "+
						handle.prev.next.face+" skips "+handle.face);
			if (trace.next.prev!=trace)
				System.err.println(" next.prev wrong: "+handle.next.face+" "+
						handle.next.prev.face+" skips "+handle.face);
		}
		return count;
	}

	/**
	 * Internal utility routine to write partial info on a single red face to 
	 * BufferedWriter. Red/Blue are distinguished, 'red' edges and non-contiguous 
	 * errors are reported. Hash codes help verify the actual objects.
	 * @param dbw, writer
	 * @param p, PackData
	 * @param redface, RedList
	 * @return 1
	 * @throws IOException
	 */
	private static int print_one_redface(BufferedWriter dbw,PackData p,RedList redface) 
	throws IOException {
		// face and its vert[]
		dbw.write(" Face "+redface.face+": {"+
				p.faces[redface.face].vert[0]+" "+
				p.faces[redface.face].vert[1]+" "+
				p.faces[redface.face].vert[2]+"} "+"(redface hash="+redface.hashCode()+") \n");
		// prev and next
		dbw.write("  redface.prev="+redface.prev.hashCode()+"; redface.next="+redface.next.hashCode()+"\n");
		int nt1,nt2;

		if (redface.next.face==redface.prev.face) {
			// BLUE instance
			dbw.write("BLUE, ");
			  nt1=p.face_nghb(redface.prev.face,redface.face);
			  
			  // a common error in handling blue faces
			  if (nt1<0) 
				  dbw.write("(error: red faces not contiguous \n");
			  
			  // there are 2 red edges in this case
			  else 
				  dbw.write("red edges: {"+
					  p.faces[redface.face].vert[(nt1+1)%3]+","+
					  p.faces[redface.face].vert[(nt1+2)%3]+"}, {"+
					  p.faces[redface.face].vert[(nt1+2)%3]+","+
					  p.faces[redface.face].vert[nt1]+"}");
		}
		else {
			// RED instance
			dbw.write("RED, ");
			nt1=p.face_nghb(redface.face,redface.prev.face);
			nt2=p.face_nghb(redface.next.face,redface.face);
			
			// catch non-contiguous
			if (nt1<0 || nt2<0) 
				dbw.write("(error: red faces not contiguous \n");
			
			// is there a red edge?
			else {
				int vert1=p.faces[redface.prev.face].vert[nt1];
				int vert2=p.faces[redface.face].vert[nt2];
				
				// must be red edge if vert1!=vert2
				if (vert1!=vert2) 
					dbw.write("red edge: {"+vert1+" "+vert2+"},");
			}
		}
		dbw.write("\n");
		return 1;
	}
	
	public static int print_drawingorder(PackData p) {
		return print_drawingorder(p,true);
	}

	/**
	 * Print drawing order of faces in System.out. Include vertex being
	 * placed.
	 * @param p PackData
	 * @param verts_too boolean: true, then also print face's vertices
	 * @return 1;
	 */
	public static int print_drawingorder(PackData p, boolean verts_too) {
		int nf;
		int tick = 0;
		int tickstop = 15;

		if (verts_too)
			tickstop = 8;
		nf = p.firstFace;
		System.out.print("Circle-drawing order, faceCount=" + p.faceCount
				+ ": firstFace=" + nf + "\n");
		if (nf < 1 || nf > p.faceCount) {
			System.out.print("xx " + nf + " xx \n");
			return 0;
		}
		while ((nf = p.faces[nf].nextFace) != p.firstFace && nf >= 1
				&& nf <= p.faceCount) {
			if (verts_too)
				System.out
						.print(" nf=" + nf + ",[" + p.faces[nf].vert[0] + ","
								+ p.faces[nf].vert[1] + ","
								+ p.faces[nf].vert[2] + "],v="+p.faces[nf].vert[(p.faces[nf].indexFlag+2)%3]+"; ");
			else
				System.out.print(" " + nf);
			if (nf == p.firstRedFace) {
				if (verts_too)
					System.out.print("\n firstRedFace=" + nf + ":["
							+ p.faces[nf].vert[0] + "," + p.faces[nf].vert[1]
							+ "," + p.faces[nf].vert[2] + "]");
				else
					System.out.print("\n firstRedFace=" + nf);
				tick = 0;
			}
			tick++;
			if ((tick % tickstop) == 0)
				System.out.print("\n");
		}
		System.out.print(" done.\n");
		return 1;
	}

	/**
	 * Internal utility routine to write detail on 'RedEges': start/stop,
	 * cornerFlags, etc. to BufferedWriter. Hashcodes help verify the actual
	 * objects. See also 'writeRed'.
	 * 
	 * @param dbw, writer
	 * @param p, PackData
	 * @param redge, RedEdge
	 * @return 1
	 * @throws IOException
	 */
	private static int print_one_redEdge(BufferedWriter dbw, PackData p,
			RedEdge redge) throws IOException {
		dbw.write("Face " + redge.face + " (redge hash=" + redge.hashCode()
				+ ") ; \n");
		dbw.write("  redge.prev=" + redge.prev.hashCode() + "; redge.next="
				+ redge.next.hashCode() + "; redge.prevRed="
				+ redge.prevRed.hashCode() + "; redge.nextRed="
				+ redge.nextRed.hashCode() + "\n");
		// begin/end?
		dbw.write("   RedEdge: startIndex = " + redge.startIndex + ";\n");
		if ((redge.cornerFlag & 1) == 1) {
			dbw.write("    BEGIN EDGE, start vert = "
					+ redge.vert(redge.startIndex) + "\n");
		}
		if ((redge.cornerFlag & 2) == 2) {
			dbw.write("    END EDGE, end vert = "
					+ redge.vert((redge.startIndex + 1) % 3) + "\n");
		}
		// cross face?
		if (redge.crossRed != null)
			dbw.write("   crossRed = " + redge.crossRed.face + " ");
		dbw.write("\n");
		return 1;
	}

	/**
	 * Internal utility routine to write key data on a 'Face' to a
	 * BufferedWriter
	 * @param p, PackData
	 * @param f, face index
	 * @param dbw, writer
	 */
	private static void writeFace(PackData p, int f, BufferedWriter dbw) {
		Face face = p.faces[f];
		try {
			dbw.write("Face " + f + ": [" + face.vert[0] + "," + face.vert[1]
					+ "," + face.vert[2] + "]; \n");
			dbw.write("   plots circle " + face.vert[(face.indexFlag + 2) % 3]
					+ "    " + ",rwbFlag=" + face.rwbFlag + ",plotFlag="
					+ face.plotFlag + "rwbFlag=" + face.rwbFlag + ",plotFlag="
					+ face.plotFlag + ",indexFlag=" + face.indexFlag + "\n");
			dbw.write("     nextFace=" + face.nextFace + ", nextRed="
					+ face.nextRed + ")\n");
		} catch (Exception ex) {
			System.err.print(ex.toString());
		}
	}

	/**
	 * Internal utility routine to write data on one 'RedList' or 'RedEdge'
	 * entry to a BufferedWriter. See also 'print_one_redEdge' for
	 * @param p, PackData
	 * @param red, RedList
	 * @param dbw, writer
	 */
	private static void writeRed(PackData p, RedList red, BufferedWriter dbw) {
		Face face = p.faces[red.face];
		try {

			// is this a 'RedEdge' (type of 'RedList' containing actual red
			// edge)
			if (red instanceof RedEdge) {
				RedEdge rede = (RedEdge) red;
				dbw.write("RedEdge: face=" + red.face + ": [" + face.vert[0]
						+ "," + face.vert[1] + "," + face.vert[2] + "]; \n");
				dbw.write("   plots circle "
						+ face.vert[(face.indexFlag + 2) % 3] + "    "
						+ "rwbFlag=" + face.rwbFlag + ",plotFlag="
						+ face.plotFlag + ",indexFlag=" + face.indexFlag + "\n");
				dbw.write("       next=" + rede.next.face + ",prev="
						+ rede.prev.face + ",nextRed=" + rede.nextRed.face
						+ ",prevRed=" + rede.prevRed.face + "\n");
				if (rede.nextRed.face == red.face) { // blue, so it repeat
					rede = rede.nextRed;
					dbw.write("  Face " + rede.face
							+ " is BLUE, so it repeats in the redchain.\n");
					dbw.write("     now it plots circle "
							+ face.vert[(face.indexFlag + 2) % 3] + "\n");
					dbw.write("       next=" + rede.next.face + ",prev="
							+ rede.prev.face + ",nextRed=" + rede.nextRed.face
							+ ",prevRed=" + rede.prevRed.face + "\n");
				}
			}

			// 'RedList' but not 'RedEdge'
			else {
				dbw.write("RedList: face=" + red.face + ": [" + face.vert[0]
						+ "," + face.vert[1] + "," + face.vert[2] + "]; \n");
				dbw.write("   plots circle "
						+ face.vert[(face.indexFlag + 2) % 3] + "    "
						+ "rwbFlag=" + face.rwbFlag + ",plotFlag="
						+ face.plotFlag + ",indexFlag=" + face.indexFlag + "\n");
				dbw.write("       next=" + red.next.face + ",prev="
						+ red.prev.face + "\n");
			}

			// as check, see what 'Face' info shows
			dbw.write("    (check 'Face' info: nextFace=" + face.nextFace
					+ ", nextRed=" + face.nextRed + ")\n");
		} catch (Exception ex) {
			System.err.print(ex.toString());
		}
	}
	
	/**
	 * Log triples <v,u,w> of dcel faces and ideal faces. Results 
	 * in 'DCEL_faces_*_log.txt'.
	 * @param dcel PackDCEL
	 * @return count, -1 on error
	 */
	public static int log_DCEL_faces(PackDCEL dcel) {
		  int count=0;

		  if (dcel==null || dcel.tmpFaceList==null || dcel.tmpFaceList.size()==0)
			  return -1;
		  
		  // open file
		  String filename=new String("DCEL_faces_"+(rankStamp++)+"_log.txt");
		  BufferedWriter dbw=CPFileManager.openWriteFP(tmpdir,filename,false);
 
		  try {
			  dbw.write("Dcel faces in order of 'dcel.faces' vector.\n");;
			  Iterator<dcel.Face> flst=dcel.tmpFaceList.iterator();
			  while (flst.hasNext()) {
				  dcel.Face face=flst.next();
				  int[] verts=face.getVerts();
				  dbw.write("\nFace "+face.faceIndx+": <");
				  int s=verts.length;
				  for (int j=0;j<s;j++)
					  dbw.write(verts[j]+", ");
				  dbw.write(">");
				  count++;
			  }
			  dbw.write("\n\nDecel ideal faces in order of 'dcel.idealFaces' vector.\n");
			  flst=dcel.tmpIdeals.iterator();
			  while (flst.hasNext()) {
				  dcel.Face face=flst.next();
				  int[] verts=face.getVerts();
				  dbw.write("\nidealFace "+face.faceIndx+": <");
				  int s=verts.length;
				  for (int j=0;j<s;j++)
					  dbw.write(verts[j]+", ");
				  dbw.write(">");
				  count++;
			  }
			  dbw.flush();
			  dbw.close();
		  } catch (Exception ex) {
			  CirclePack.cpb.errMsg("error in 'log_DCEL_faces' routine");
		  }
		  return count;
	}

}