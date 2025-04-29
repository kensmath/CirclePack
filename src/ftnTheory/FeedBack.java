package ftnTheory;

import input.CPFileManager;

import java.io.BufferedReader;
import java.io.File;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.util.Vector;

import listManip.NodeLink;
import packing.PackData;
import packing.PackExtender;
import util.CmdStruct;
import util.StringUtil;
import exceptions.ParserException;

/**
 * This 'PackExtender' was initiated for preliminary experiments with 
 * the expectation that it could be developed incrementally. 
 * 
 * Basic notion of feedback is that boundary radii of a euclidean packing
 * can be adjusted based on other radii in the packing (eventually, perhaps
 * another packing). 
 * 
 * I first experimented with feedback from the inner "rectangle" patch of 
 * the dodecahedral tiling to the outer boundary to see if the overall
 * global shape would stabilize.
 * @author kens
 *
 */
public class FeedBack extends PackExtender {
	PackData maxEucl; // hold the maximal packing, converted to euclidean
	Vector<FeedPacket> depMatrix; // vector of dependencies
	int bdryCount;
	
	// Constructor
	public FeedBack(PackData p) {
		super(p);
		extensionType="FEEDBACK";
		extensionAbbrev="FK";
		toolTip="'FeedBack' allows radii (eucl only) adjustments "+
		 "based on other radii. Matrix DM (dependency matrix, nonnegative " +
		 "entries) adjusts radii via vector mutlt NewR = DM*CurR, where " +
		 "CurR is vector of current radii ratios (vis-a-vis eucl max radii) "+
		 "and NewR is the vector of new radii ratios.";
		registerXType();
		
		// convert packData to euclidean, record its maximal packing
		try {
			maxEucl=p.copyPackTo();
			cpCommand(maxEucl,"max_pack 10000");
			cpCommand(maxEucl,"geom_to_e");
			cpCommand(extenderPD,"geom_to_e");
			running=true;
		} catch (Exception ex) {
			errorMsg("FeedBack: error in preparing 'maxPack'");
			running=false;
		}
		// count boundary vertices
		bdryCount=0;
		for (int v=1;v<=extenderPD.nodeCount;v++)
			if (extenderPD.isBdry(v))
				bdryCount++;
		if (bdryCount==0) {
			errorMsg("FeedBack: packing has no boundary");
			running=false;
		}
		
		// yes, seems okay
		if (running) {
			extenderPD.packExtensions.add(this);
			depMatrix=new Vector<FeedPacket>(1);
		}
	}
	
	public int cmdParser(String cmd,Vector<Vector<String>> flagSegs) {
		int count=0;
		Vector<String> items=null;
	
		// ----------- reset ------------
		if (cmd.startsWith("reset")) {
			depMatrix=new Vector<FeedPacket>(1);
			return 1;
		}
	
		// ----------- 
		else if (cmd.startsWith("radMult")) {
			double x=1.0;
			NodeLink sourceVerts=null;
			NodeLink targetVerts=null;
			try {
				items=flagSegs.get(0);
				// not a flag, should be a double
				if (!StringUtil.isFlag(items.get(0))) {
					x=Double.parseDouble(items.remove(0));
					flagSegs.remove(0);
				}

				// should get -s and -t flag lists
				for (int j=0;j<2;j++) {
					items=flagSegs.remove(0);
					String lead=items.get(0);
					if (lead.equals("-s")) {
						items.remove(0);
						sourceVerts=new NodeLink(extenderPD,items);
					}
					else if (lead.equals("-t")) {
						items.remove(0);
						targetVerts=new NodeLink(extenderPD,items);
					}
				}
			} catch (Exception ex) {
				throw new ParserException("didn't get data: "+ex.getMessage());
			}
			
			int snum=sourceVerts.size();
			int tnum=targetVerts.size();
			int min=snum;
			min = (tnum<min) ? tnum: min;
			for (int i=0;i<min;i++) {
				int v=sourceVerts.get(i);
				int w=targetVerts.get(i);
				extenderPD.setRadius(w,x*extenderPD.getRadius(v));
				count++;
			}
			return count;
		}
		
		// ============ set Radii ========================
		else if (cmd.startsWith("setRadii")) {
			boolean useRatio=false;
			double []newRad=new double[extenderPD.nodeCount+1];
			
			// check for 'r' flag to use ratios of radii; this is
			//    analogous to modifying the modulus of the derivative.
			try {
				items=flagSegs.remove(0);
				if (items.get(0).contains("r"))
					useRatio=true;
			} catch(Exception ex) {}

			// accumulate feedback
			Iterator<FeedPacket> fdpk=depMatrix.iterator();
			int v=0;
			int w=0;
			double x=1.0;
			while (fdpk.hasNext()) {
				FeedPacket packet=fdpk.next();
				v=packet.toVert;
				w=packet.fromVert;
				x=packet.coeff;
				if (useRatio)  
					newRad[v] +=x*extenderPD.getRadius(w)/maxEucl.getRadius(w);
				else 
					newRad[v] +=x*extenderPD.getRadius(w);
			}
			
			// apply feedback
			int icount=0;
			for (int i=1;i<=extenderPD.nodeCount;i++) {
				if (newRad[i]>0) {
					extenderPD.setRadius(i,newRad[i]);
					icount++;
				}
			}
			return icount;
		}
		
		else if (cmd.startsWith("setVW")) {
			int v=0;
			int w=0;
			double x=0.0;
			try {
				items=flagSegs.get(0);
				v=NodeLink.grab_one_vert(extenderPD,items.get(0));
				w=NodeLink.grab_one_vert(extenderPD,items.get(1));
				x=Double.parseDouble(items.get(2));
			} catch (Exception ex) {
				Oops("usage: setVW v w {x}");
			}
			if (x<0.0 || v<1 || w<1 || 
					v>extenderPD.nodeCount || w>extenderPD.nodeCount) {
				Oops("usage: setDep v w x (x>=0.0)");
			}

			FeedPacket curP=chkPacket(v,w);
			if (curP==null)
				depMatrix.add(new FeedPacket(v,w,x));
			else 
				curP.coeff=x;
			return 1;
		}
		
		else if (cmd.startsWith("swapVW")) {
			int v=0;
			int w=0;
			double x=0.0;
			try {
				items=flagSegs.get(0);
				v=NodeLink.grab_one_vert(extenderPD,items.get(0));
				w=NodeLink.grab_one_vert(extenderPD,items.get(1));
				x=Double.parseDouble(items.get(2));
			} catch (Exception ex) {
				Oops("usage: setDep v w {x}");
			}
			if (x<0.0 || v<1 || w<1 || 
					v>extenderPD.nodeCount || w>extenderPD.nodeCount) {
				Oops("usage: setDep v w x (x>=0.0)");
			}
			
			FeedPacket curP=chkPacket(v,w);
			if (curP==null)
				depMatrix.add(new FeedPacket(v,w,x));
			else 
				curP.coeff=x;
			return 1;
		}
		
		else if (cmd.startsWith("getDM")) {
			if (depMatrix==null || depMatrix.size()==0)
				return 0;
			Iterator<FeedPacket> fdpk=depMatrix.iterator();
			while (fdpk.hasNext()) {
				FeedPacket packet=fdpk.next();
				if (packet.coeff>0.0) 
					this.msg("v="+packet.toVert+" w="+packet.fromVert+" x="+packet.coeff+"\n");
			}
		}
		
		else if (cmd.startsWith("readDM")) {
			boolean script_flag=false;
			try {
				items=flagSegs.remove(0);
				if (items.get(0).startsWith("-s")) {
					script_flag=true;
					items.remove(0);
				}
			} catch (Exception ex) {}
			
			String filename=StringUtil.reconItem(items);
			BufferedReader fp=null;
			File dir=null;
			
			if (!script_flag) {
				dir=CPFileManager.PackingDirectory;
				try {
					if (filename.startsWith("~/")) 
						filename=new String(CPFileManager.HomeDirectory+File.separator+filename.substring(2));
					dir=new File(filename);
					filename=dir.getName();
					dir=new File(dir.getParent());
				} catch (Exception ex) {
					Oops("problem reading file");
				}
			}

			try {
				fp=CPFileManager.openReadFP(dir,filename,script_flag);
				String line=null;
				while((line=StringUtil.ourNextLine(fp))!=null) {
					StringTokenizer tok = new StringTokenizer(line);
					int v;
					int w;
					double x;
					try {
						v=Integer.parseInt(tok.nextToken());
						w=Integer.parseInt(tok.nextToken());
						x=Double.parseDouble(tok.nextToken());
					} catch (Exception ex) {
						return count;
					}
					depMatrix.add(new FeedPacket(v,w,x));
					count++;
				}
				fp.close();
			} catch (Exception ex) {
				Oops("read error of some sort");
			}
		}
		
		return count;
	}

	/**
	 * Is there a packet for (v,w)?
	 * @param v
	 * @param w
	 * @return
	 */
	public FeedPacket chkPacket(int v,int w) {
		if (depMatrix==null || depMatrix.size()==0)
			return null;
		Iterator<FeedPacket> fdpk=depMatrix.iterator();
		while (fdpk.hasNext()) {
			FeedPacket packet=fdpk.next();
			if (packet.toVert==v && packet.fromVert==w)
				return packet;
		}
		return null;
	}
	
	/** 
	 * Override method for cataloging command structures
	 */
	public void initCmdStruct() {
		super.initCmdStruct();
		cmdStruct.add(new CmdStruct("getDM","v w",null,"report DM(v,w)"));
		cmdStruct.add(new CmdStruct("resetDM",null,null,"set DM to identity"));
		cmdStruct.add(new CmdStruct("readDM","[-s] {filename}",null,"load DM from a file ('-s' means in script)"));
		cmdStruct.add(new CmdStruct("setRadii","[-r]",null,"set radii according to DM (dependency matrix)."+
				"with -r, adjust according to radii ratio with eucl. max"));
		cmdStruct.add(new CmdStruct("setVW","v w {x}",null,"set DM(v,w)=x (x>=0.0)"));
		cmdStruct.add(new CmdStruct("swapVW","v w {x}",null,"set DM(v,w)=x and DM(v,v)=0.0"));
		cmdStruct.add(new CmdStruct("radMult","{x} -s {v..} -t {v..}",null,"give 'source', 'target' vert lists, "+
				"set radii of target by x*radii of source."));
	}

	/**
	 * Utility class. Feedback is about vertices being influenced by 
	 * other vertices. The nature of the influence can vary -- maybe
	 * effect on radius or angle sum, etc -- and the meaning of the
	 * 'coeff' is flexible. This is a storage class to avoid maintaining
	 * a huge matrix, mostly 0's.
	 * @author kstephe2
	 *
	 */
	class FeedPacket {
		int toVert; // vert being affected
		int fromVert; // vert having the effect
		double coeff; // scale of the effect
		
		public FeedPacket(int v,int w,double x) {
			toVert=v;
			fromVert=w;
			coeff=x;
		}
	}
}
