package cpContributed;

import java.util.Iterator;
import java.util.Vector;

import allMains.CPBase;
import allMains.CirclePack;
import canvasses.DisplayParser;
import circlePack.PackControl;
import complex.Complex;
import exceptions.ParserException;
import geometry.EuclMath;
import geometry.StreamLiner;
import listManip.BaryCoordLink;
import listManip.FaceLink;
import listManip.NodeLink;
import listManip.PointLink;
import packing.PackData;
import packing.PackExtender;
import util.CmdStruct;
import util.StringUtil;

/**
 * "Curvature Flow" originated in a paper by Stephenson, Collins, Driscoll.
 * 
 * This class allows one to experiment. 
 * @author kens
 */

public class CurvFlow extends PackExtender {
	PackData domainData; 	// starts with euclidean version of maximal packing
	double []anglesumDiff; 	// anglesum differences: anglesums(packData) - anglesums(domainPack)
	double []aimDiff;       // aim differences: aim(packData) - aim(domainPack)
	double []radRatio; 		// radii ratios: radii(packData)/radii(domainPack)
	public static double []bdryCurv; // vector of boundary curvatures (Pi-anglesum); 
	public StreamLiner streamLiner;  // 
	double []rad1;   		// first radii
	double []rad2;
	double []logmod;		// logmod is analogue of log|phi'|, namely r2-r1/r1;
	Vector<BaryCoordLink> curveVector;  // utility vector of 'BaryCoordLink's for streamlines  
	
	// store a complex valued function on the unit circle; typically the 
	//   ratio function for packing with one complex for use with others.
	//   (Last entry of vectors equals first, to help with interpolation.)
	int drSize; // size of following vectors
	double []domArgs; // arguments in [0,2pi] for centers of maximal pack bdry.
	Complex []rangeZ;   // function values --- generally real
	
	// Constructor
	public CurvFlow(PackData p) {
		super(p);
		extensionType="CURVATURE_FLOW";
		extensionAbbrev="CF";
		toolTip="'CurvFlow' for manipulation of relative angle sums (resp. radii) of"+
				" two packings (with identical combinatorics). By default, comparisons "+
				"are made to stored 'domainData', which must be euclidean.";
		registerXType();
		
		// set up the local 'domainPack', max packing converted to euclidean
		try {
			domainData=p.copyPackTo();
			
			// TODO: used to store euclidean version of max packing, 
			//       but problems have been encountered with max packing code.
//			cpCommand(domainData,"max_pack 10000");
			cpCommand(domainData,"geom_to_e");
			domainData.fillcurves();
		} catch (Exception ex) {
			errorMsg("CurvFlow: error in preparing 'domainPack'");
			running=false;
		}
		if (running) {
			anglesumDiff=new double[packData.nodeCount+1];
			radRatio=new double[packData.nodeCount+1];
			if (setAngDiff(domainData,packData)==0 || setRadRatio(domainData,packData)==0)
				errorMsg("CF: failed to initialize 'domainPack'");
			packData.packExtensions.add(this);
		}
		rad1=null;
		rad2=null;
		logmod=null;
		streamLiner=null;
	}
	
	public int cmdParser(String cmd,Vector<Vector<String>> flagSegs) {
		int count=0;
		Vector<String> items=null;
		String str=null;
		
		// ============= disp ================= (pull off -b option) ======
		if (cmd.startsWith("disp")) {
			return displayMe(flagSegs);
		}
		
		// ============= set_stre ======================
		if (cmd.startsWith("set_stre")) {
			
			if (logmod==null) {
				throw new ParserException("'logmod' is not yet set: see 'rad_diff");
			}
			
			PointLink zlink=new PointLink();
			FaceLink flink=null;
			boolean append=false;
			boolean uphill=true;
			
			if (flagSegs!=null && flagSegs.size()>0) {
				Iterator<Vector<String>> flgs=flagSegs.iterator();
				while (flgs.hasNext()) {
					items=flgs.next();
					try {
						str=items.get(0);
						if (StringUtil.isFlag(str)) {
							items.remove(0); // toss, still in 'str'
							char c=str.charAt(1);
							switch (c) {
							case 'a': // append
							{
								append=true;
								break;
							}
							case 'd': // downhill
							{
								uphill=false;
								break;
							}
							case 'z': // points
							{
								zlink=new PointLink(items);
								break;
							}
							case 'f': // faces
							{
								flink=new FaceLink(packData,items);
								break;
							}
							} // end of switch
						}
					} catch (Exception ex) {
						// default to faces having an edge in the border
					}
				} // end of while
			}
			
			// no flags, default to bdry faces
			else {
				flink=new FaceLink(packData,"Iv b");
			}
			
			// get list of points
			if (flink!=null && flink.size()>0) {
				Iterator<Integer> flst=flink.iterator();
				while (flst.hasNext()) {
					combinatorics.komplex.DcelFace face=packData.packDCEL.faces[flst.next()];
					int[] vert=face.getVerts();
					// put face barycenter in the list
					zlink.add(EuclMath.eucl_tri_center(
							domainData.getCenter(vert[0]),
							domainData.getCenter(vert[1]),
							domainData.getCenter(vert[2])));
				}
			}

			// initiate a new 'StreamLiner'?
			if (!append) {
				streamLiner=new StreamLiner(domainData);
				int numb=streamLiner.setDataValues(logmod);
				if (numb<0)
					throw new ParserException(
							"problem setting 'streamLiner.dataValues'");
				streamLiner.setNormals();

				// old data is lost
				curveVector=new Vector<BaryCoordLink>();
			}
			
			if (streamLiner==null || curveVector==null || 
					zlink==null || zlink.size()==0)
				throw new ParserException(
						"'streamliner missing or no data given");
			
			
			// go through list of points
			Iterator<Complex> zlst=zlink.iterator();
			while (zlst.hasNext()) {
				Complex z=zlst.next();
				BaryCoordLink bcl=streamLiner.getStreamline(z,uphill);
				if (bcl!=null && bcl.size()>0) {
					curveVector.add(bcl);
					count++;
				}
			}
			
			return count;
		}
		
		// ===================== set_rad_rat ====================
		// set radius ratio vector: use 'packData' as domain. If no
		//   flags, then 'domainData' is range. Flag -q{n} indicates pack[n]
		//   as the range, else -u to use stored 'utilDoubles' as the ratios
		else if (cmd.startsWith("set_rad_rat")) {
			// default: compare to 'domainPack'
			if (flagSegs==null || flagSegs.size()==0) { 
				return setRadRatio(packData,domainData);
			}
			try { // try to read -q{p} flag
				items=(Vector<String>)flagSegs.get(0);
				if (StringUtil.isFlag(items.get(0))) {
					char c=items.get(0).charAt(1);
					switch(c) {
					case 'y': // deprecated, fall through
					case 'u': // get from packData.UtilDouble
					{
						if (packData.utilDoubles==null)
							Oops("'utilDoubles' was null");
						for (int v=1;v<=packData.nodeCount;v++) {
							if (v<=packData.utilDoubles.size())
									radRatio[v]=packData.utilDoubles.get(v-1);
						}
						return 1;
					}
					case 'q':
					{ // compare to another packing
						int qnum=StringUtil.qFlagParse(items.get(0));
						if (qnum<0)
							throw new ParserException("failed to read 'q' flag");
						return setRadRatio(packData,PackControl.packings[qnum]);
					}
					} // end of switch
				}
			} catch (Exception ex) {}
			return 0;
		}
		
		// ======================== set_rad ================
		else if (cmd.startsWith("set_rad")) {
			try {
				items=flagSegs.get(0);
				int wr=Integer.parseInt(items.get(0));
				if (wr==1) {
					rad1=new double[domainData.nodeCount+1];
					for (int v=1;v<=packData.nodeCount;v++) 
						if (v<=domainData.nodeCount)
							rad1[v]=packData.getRadius(v);
				}
				else {
					rad2=new double[domainData.nodeCount+1];
					for (int v=1;v<=packData.nodeCount;v++) 
						if (v<=domainData.nodeCount)
							rad2[v]=packData.getRadius(v);
				}
				return 1;
			} catch (Exception ex) {
				throw new ParserException("didn't get '1' or '2'");
			}
		}
		
		// ============= logmod =================
		else if (cmd.startsWith("logmod")) {
			if (rad1==null || rad1.length<domainData.nodeCount+1 ||
					rad2==null || rad2.length<domainData.nodeCount+1)
				throw new ParserException("rad1/rad2 don't have right size");
			logmod=new double[domainData.nodeCount+1];
			for (int v=1;v<=packData.nodeCount;v++)
				if (v<=domainData.nodeCount)
					logmod[v]=(rad2[v]-rad1[v])/rad1[v];
			return logmod.length;
		}
		
		// ========= store_rad/ang/aim_diff =====
		else if (cmd.startsWith("store_")) {
			
			// which type of data? radii or angles?
			boolean angdata=true; // default
			boolean aimdata=false;
			boolean raddata=false;
			if (cmd.contains("store_rad")) {
				angdata=false;
				raddata=true;
			}
			else if (cmd.contains("store_aim")) {
				angdata=false;
				aimdata=true;
			}
			
			if ((angdata && anglesumDiff!=null) || 
					(raddata && radRatio!=null) ||
					aimdata && aimDiff!=null) { 
				packData.utilDoubles=new Vector<Double>(packData.nodeCount);
				for (int v=1;v<=packData.nodeCount;v++) {
					double data=0.0;
					
					if (raddata)
						data=radRatio[v];
					else if (aimdata)
						data=aimDiff[v];
					else
						data=anglesumDiff[v];
					
					packData.utilDoubles.add(Double.valueOf(data));
					count++;
				}
			}
			return count;
		}

		// ============ export ==== (preempts PackExtender)
		else if (cmd.startsWith("export")) {
			try {
				items=(Vector<String>)flagSegs.get(0);
				int pnum=Integer.parseInt((String)items.get(0));
				PackData p=domainData.copyPackTo();
				int ans=CirclePack.cpb.swapPackData(p,pnum,false);
				msg("put 'domainPack' in pack p"+pnum);
				return ans; 
			} catch (Exception ex) {}
			return 0;
		}
		
		// =========== move by mean curvature ============
		else if (cmd.startsWith("mmc")) {
			// TODO: want to provide more options
			double total=fillBdryCurv(packData);
			@SuppressWarnings("unused")
			double radsum=0.0;
			for (int v=1;v<=packData.nodeCount;v++) {
				if (packData.isBdry(v)) {
					radsum += packData.getRadius(v);
				}
			}

			// adjust each radius by moderated proportion that 
			//     that radius forms of total
			for (int v=1;v<=packData.nodeCount;v++) {
				if (packData.isBdry(v)) {
					double factor=.1; // moderating factor
					double prad=packData.getRadius(v);
					double adjustment=(-1.0)*(bdryCurv[v]/total)*prad;
					packData.setRadius(v,prad+ factor*adjustment);
				}
			}
			
			cpCommand("repack 2000");
			cpCommand("layout");
			cpCommand("norm_scale -c A .2");
			cpCommand("disp -wr");
			return 1;
		}
		
		// =========== chg_aims =============
		// Adjust 'packData' aims
		else if (cmd.startsWith("chg_aims")) {
			boolean useUtilDoubles=false;
			boolean incremental=false;
			NodeLink vertlist=null;
			
			// read the flag info
			Iterator<Vector<String>> fs=flagSegs.iterator();
			double x=0.0;
			while (fs.hasNext()) {
				items=(Vector<String>)fs.next();
				str=(String)items.remove(0);
				if (StringUtil.isFlag(str)) {
					char c=str.charAt(1); 
					switch(c) {
					case 'i': 
					{
						incremental=true; // (fall thru) increment 'ratio'; rad=rad*(ratio)^x 
					}
					case 't': // set by parameter: rad=domain_rad*(ratio)^x
					{
						String tstr=(String)items.remove(0);
						
						// set factor
						try {
							x=Double.valueOf(tstr);
						} catch (Exception ex) {
							errorMsg("usage: -"+c+" {x} {v..}");
							return 0;
						}
						
						// get vertices
						if (items.size()==0) { // no vertices given
							vertlist=new NodeLink(packData,"a");
						}
						else vertlist=new NodeLink(packData,items);
						if (vertlist==null || vertlist.size()==0) {
							errorMsg("no vertices specified");
							return 0;
						}
						break;
					}			
					case 'u': // use 'utilDoubles' vector
					{
						if (packData.utilDoubles==null || 
								packData.utilDoubles.size()!=packData.nodeCount) {
							errorMsg("'utilDoubles' vector empty or wrong size");
							return 0;
						}
						useUtilDoubles=true;
						break;
					}
					} // end of switch
				} // end of flags
			}  // end of while	

			// reaching here, should have vertlist
			if (vertlist==null || x==0.0) {
				CirclePack.cpb.errMsg("No vertices specified, or increment is zero");
				return count;
			}
			Iterator<Integer> vlist=vertlist.iterator();
			int v=1;
			while (vlist.hasNext()) {
				v=(Integer)vlist.next();
				double curraim=0.0;
				if (incremental) 
					curraim=packData.getAim(v);
				else
					curraim=domainData.getAim(v);
				// what data are we using? does it exist?
				if ((!useUtilDoubles && v<aimDiff.length) ||
						(useUtilDoubles && v<=packData.utilDoubles.size())) {
					double term=0.0;
					if (useUtilDoubles)
						term=packData.utilDoubles.get(v-1);
					else 
						term=aimDiff[v];
					
					// apply
					packData.setAim(v,curraim+x*(term));
					count++;
				}
			} // end of while
			return count;
		}

		// =========== chg_ratios =============
		// adjust 'packData' radii
		else if (cmd.startsWith("chg_rad")) {
			Iterator<Vector<String>> fs=flagSegs.iterator();
			boolean useUtilDoubles=false;
			boolean incremental=false;
			NodeLink vertlist=null;
			double x=0.0;
			while (fs.hasNext()) {
				items=(Vector<String>)fs.next();
				str=(String)items.remove(0);
				if (StringUtil.isFlag(str)) {
					char c=str.charAt(1); 
					switch(c) {
					case 'i': 
					{
						incremental=true; // (fall thru) increment 'ratio'; rad=rad*(ratio)^x 
					}
					case 't': // set by parameter: rad=domain_rad*(ratio)^x
					{
						String tstr=(String)items.remove(0);
						
						// set factor
						try {
							x=Double.valueOf(tstr);
						} catch (Exception ex) {
							errorMsg("usage: -"+c+" {x} {v..}");
							return 0;
						}
						
						// get vertices
						if (items.size()==0) { // no vertices given
							vertlist=new NodeLink(packData,"a");
						}
						else vertlist=new NodeLink(packData,items);
						if (vertlist==null || vertlist.size()==0) {
							errorMsg("no vertices specified");
							return 0;
						}
						break;
					}			
					case 'u': // use 'utilDoubles' vector
					{
						if (packData.utilDoubles==null || packData.utilDoubles.size()!=packData.nodeCount) {
							errorMsg("'utilDoubles' vector is empty or size is too small");
							return 0;
						}
						useUtilDoubles=true;
						break;
					}
					} // end of switch 
				} 
					
			} // end of while
			
			
			// reaching here, should have vertlist
			if (vertlist==null || x==0.0)
				return count;
			Iterator<Integer> vlist=vertlist.iterator();
			int v=1;
			while (vlist.hasNext()) {
				v=(Integer)vlist.next();
				double rad=domainData.getRadius(v);
				if (incremental) rad=packData.getRadius(v);
					
				// what data are we using? does it exist?
				if ((!useUtilDoubles && v<radRatio.length) ||
						(useUtilDoubles && v<=packData.utilDoubles.size())) {
					double factor=radRatio[v];
					if (useUtilDoubles)
						factor=packData.utilDoubles.get(v-1);

					// apply
					packData.setRadius(v,rad*(Math.exp(x*Math.log(factor))));
					count++;
				}
			} // end of while

			return count;
		}
		
		// ==================== set_ang_diff ==================
		// set curvature difference: use 'packData' as domain. If no
		//   flags, then 'domainData' is range. Flag -q{p} indicates pack[p]
		//   as the range, else -u to use stored 'utilDoubles' as the differences.
		else if (cmd.startsWith("set_ang_dif")) {
			// default: compare to 'domainPack' comparison
			if (flagSegs==null || flagSegs.size()==0) { 
				return setAngDiff(packData,domainData);
			}
			try { // try to read -q{p} flag
				items=(Vector<String>)flagSegs.get(0);
				if (StringUtil.isFlag(items.get(0))) {
					char c=items.get(0).charAt(1);
					switch(c) {
					case 'y': // deprecated, fall through to 'u'
					case 'u':
					{ // get from packData.UtilDouble
						if (packData.utilDoubles==null)
							Oops("'utilDoubles' was null");
						for (int v=1;v<=packData.nodeCount;v++) {
							if (v<=packData.utilDoubles.size())
								anglesumDiff[v]=packData.utilDoubles.get(v-1);
						}
						return 1;
					}
					case 'q':
					{ // compare to another packing
						int qnum=StringUtil.qFlagParse(items.get(0));
						if (qnum<0)
							throw new ParserException("failed to read 'q' flag");
						return setAngDiff(packData,PackControl.packings[qnum]);
					}
					} // end of switch
				}
			} catch (Exception ex) {}
			return 0;
		}
		
		// ==================== set_aim_diff ==================
		// set aim difference: use 'packData' as domain. If no
		//   flags, then 'domainData' is range. Flag -q{p} indicates pack[p]
		//   as the range, else -u to use stored 'utilDoubles' as the differences.
		else if (cmd.startsWith("set_aim_dif")) {
			// default: compare to 'domainPack' comparison
			if (flagSegs==null || flagSegs.size()==0) { 
				return setAimDiff(packData,domainData);
			}
			try { // try to read -q{p} flag
				items=(Vector<String>)flagSegs.get(0);
				if (StringUtil.isFlag(items.get(0))) {
					char c=items.get(0).charAt(1);
					switch(c) {
					case 'y': // deprecated, fall through to 'u'
					case 'u':
					{ // get from packData.UtilDouble
						if (packData.utilDoubles==null)
							Oops("'utilDoubles' was null");
						for (int v=1;v<=packData.nodeCount;v++) {
							if (v<=packData.utilDoubles.size())
								aimDiff[v]=packData.utilDoubles.get(v-1);
						}
						return 1;
					}
					case 'q':
					{ // compare to another packing
						int qnum=StringUtil.qFlagParse(items.get(0));
						if (qnum<0)
							throw new ParserException("failed to read 'q' flag");
						return setAimDiff(packData,PackControl.packings[qnum]);
					}
					} // end of switch
				}
			} catch (Exception ex) {}
			return 0;
		}
		
		/* ======================= adr ================================
		// TODO: This routine is not functional yet --- don't recall the intention?
		// adr -q{p1} -q{p2} a
		//   p1 is max packing domain, but euclidean, p2 is for range packing.
		//   Interpolate domArgs/rangeZ function raised to power 'a' at bdry 
		//   circles of p1 to set radii of p2. User then can repack p2.
		else if (cmd.startsWith("adr")) {
			errorMsg("|cf| adr: command not yet implemented");
			return 0;
		}
/*			int p1num,p2num;
			double a;
			try {
				// -q{p1}
				items=(Vector<String>)flagSegs.get(0);
				p1num=StringUtil.qFlagParse(CPBase.NUM_PACKS,(String)items.get(0));
				// -q{p2}
				items=(Vector<String>)flagSegs.get(1);
				p2num=StringUtil.qFlagParse(CPBase.NUM_PACKS,(String)items.get(0));
				// parameter at end of second flag sequence
				a=1; // default
				if (items.size()>1)
					a=Double.parseDouble((String)items.get(1));
			} catch (Exception ex) {
				throw new ParserException("CurvFlow usage: adr -q{p1} -q{p2} a");
			}
			PackData p1=PackControl.pack[p1num].packData;
			PackData p2=PackControl.pack[p2num].packData;
			NodeLink bdry=new NodeLink(p1,"b");
			Iterator<Integer> blist=bdry.iterator();
			int w;
			Integer start=Integer.valueOf(0);
			double val;
			double t;
			int click=0;
			while (blist.hasNext()) {
				w=(int)blist.next();
				t=p1.rData[w].center.arg();
				// interpolate value from domArgs/rangeZ for argument t,
				//   suggested starting point index 'start'
				val=interp(t,start);
				p2.rData[w].rad=p1.rData[w].rad*(Math.exp(a*val));
				click++;
			}
			return click;
		}
*/		
		return super.cmdParser(cmd, flagSegs);
	}
	
	/**
	 * Store the curvatures for boundary vertices in 'bdryCurv',
	 * others set to zero: entry is Pi-anglesum. For (eucl) packing, 
	 * should sum to 2*Pi(1+branch order). 
	 * @param p packing
	 * @return double = sum of absolute values - 2*Pi(1 + branch order)
	 */
	public static double fillBdryCurv(PackData p) {
		NodeLink bdryV=new NodeLink(p,"b");
		if (bdryV==null || bdryV.size()==0) {
			bdryCurv=null;
			return 0.0;
		}
		p.fillcurves();
		bdryCurv=new double[p.nodeCount+1];
		double accum=0.0;
		int branchOrder=0;
		
		// collect info
		for (int v=1;v<=p.nodeCount;v++) {
			if (p.isBdry(v)) {
				bdryCurv[v]=Math.PI-p.getCurv(v);
				accum += Math.abs(bdryCurv[v]);
			}
			else {
				int n=(int)((p.getAim(v)+.01)/(2.0*Math.PI));
				if (n>1)
					branchOrder += n-1;
			}
		}
		
		return (accum-2.0*(1.0+(double)branchOrder));
	}
	
	/**
	 * Store array 'anglesumDiff' of anglesum differences, p-q.
	 * Initially and by default, q=='domainData', so by adding
	 * 'angDiff' to 'domainData' angles sums, you get the angle
	 * sums for p. Can also set using two given packings. Packings
	 * must be euclidean and share nodecount of 'packData'.   
	 * @param q PackData: considered the 'initial' data, 'null', then use 'domainData'
	 * @param p PackData: considered the 'target' data, euclidean
	 * @return count or zero on error
	 */
	public int setAngDiff(PackData q,PackData p) {
		if (q==null)
			q=domainData;
		if (q==null || p==null || !q.status || !p.status || q.hes!=0 || p.hes!=0 ||
				q.nodeCount!=packData.nodeCount || q.nodeCount!=packData.nodeCount) {
			Oops("CF: set_ang_diff: nodeCount's not matching (or some other problem)");
		}
		
		// update angle sums 
		p.fillcurves();
		q.fillcurves();
		anglesumDiff=new double[p.nodeCount+1];
		for (int v=1;v<=p.nodeCount;v++) {
			anglesumDiff[v]=p.getCurv(v)-q.getCurv(v);
		}
		return 1;
	}
	
	/**
	 * Store array 'aimDiff' of aim differences, p-q. 
	 * CAUTION: the user must consider situations when aim
	 * is negative, as typical with boundary vertices.
	 * Initially and by default, q=='domainData', so by adding
	 * 'aimDiff' to 'domainData' aims, you get the aims for p.
	 * Can also set using two given packings. Packings
	 * must be euclidean and share nodecount of 'packData'.   
	 * @param q PackData: considered the 'initial' data, 'null', then use 'domainData'
	 * @param p PackData: considered the 'target' data, euclidean
	 * @return count or zero on error
	 */
	public int setAimDiff(PackData q,PackData p) {
		if (q==null)
			q=domainData;
		if (q==null || p==null || !q.status || !p.status || q.hes!=0 || p.hes!=0 ||
				q.nodeCount!=packData.nodeCount || q.nodeCount!=packData.nodeCount) {
			Oops("CF: set_ang_diff: nodeCount's not matching (or some other problem)");
		}
		
		// update angle sums 
		aimDiff=new double[p.nodeCount+1];
		for (int v=1;v<=p.nodeCount;v++) {
			aimDiff[v]=p.getAim(v)-q.getAim(v);
		}
		return 1;
	}
	
	/**
	 * Store array 'radRatio' of radii ratios, p/q. 
	 * Initially and by default, q=='domainData', so multiplying
	 * its radii by 'radRatio' give radii for p. Can also use
	 * two specified packings. Packings should be euclidean.
	 *    
	 * Also, set initial bdry function, putting values in 'domArgs' and 'rangeZ'.
	 *  
	 * @param q PackData: considered the 'initial' data, 'null', then use 'domainData'
	 * @param p PackData: considered the 'target' data. packings must be euclidean, have same nodeCount as 'packData'
	 * @return count or zero on error
	 */
	public int setRadRatio(PackData q,PackData p) {
		if (q==null)
			q=domainData;
		if (q==null || p==null || !q.status || !p.status || q.hes!=0 || p.hes!=0 ||
				q.nodeCount!=packData.nodeCount || q.nodeCount!=packData.nodeCount) {
			errorMsg("CF: set_rad_rat: nodeCount's not matching or other problem");
			return 0;
		}
		for (int v=1;v<=p.nodeCount;v++) {
			radRatio[v]=p.getRadius(v)/q.getRadius(v);
		}
		NodeLink bdry=new NodeLink(q,"b");
		drSize=bdry.size()+1;
		domArgs=new double[drSize];
		rangeZ=new Complex[drSize];
		Iterator<Integer> blist=bdry.iterator();
		int w;
		int count=0;
		while (blist.hasNext()) {
			w=(int)blist.next();
			domArgs[count]=q.getCenter(w).arg();
			rangeZ[count]=new Complex(p.getRadius(w)/q.getRadius(w));
			count++;
		}
		// close up
		domArgs[count]=domArgs[0];
		rangeZ[count]=new Complex(rangeZ[0]);
		
		return 1;
	}
	
	 /**
	  * TODO: OBE, I've built the -bs option for this
	  * If option '-b' is given and 'curveVector' has something, then substitute
	  * 'curveVector' temporarily in place of 'CPBase.baryVector' for the drawing
	  * command. 
	  * @param flagSegs flag sequences
	  * @return int count of display actions
	  */
	public int displayMe(Vector<Vector<String>> flagSegs) {
		int count=0;
		boolean b_hit=false;
		for (int j=0;j<flagSegs.size();j++) {
			Vector<String> items=flagSegs.get(j);
			String str=items.get(0);
			if (str.startsWith("-b"))
				b_hit=true;
		}
		
		// save data, display, replace data
		if (b_hit && curveVector!=null && curveVector.size()>0) {
			Vector<BaryCoordLink> holdVector=CPBase.gridLines;
			CPBase.gridLines=curveVector;
			if ((count=DisplayParser.dispParse(packData,flagSegs))>0)
				PackControl.canvasRedrawer.paintMyCanvasses(packData,false);
			CPBase.gridLines=holdVector;
		}
		return count;
	}
	
	/**
     * store info on the commands this extender will be getting from the user.
     */
	public void initCmdStruct() {
		super.initCmdStruct();
		cmdStruct.add(new CmdStruct("chg_aims","-[u] -[it] {x} {v..}",null,
				"Adjust aims by adding 'x' times current 'ang_diff' "+
						"to current aims ('i' option) or to 'domainData' "+
						"aims ('t' option); -u to use 'utilDoubles'"));
		cmdStruct.add(new CmdStruct("chg_rad","-[u] -[it] {x} {v..}",null,
				"Adjust radii to 'rad_ratio' to 'x' power times "+
						"current radii ('i' option) or 'domainData' "+
						"radii ('t' option); -u to use 'utilDoubles'"));
		cmdStruct.add(new CmdStruct("set_ang_diff","[-q{q}] [-u]",null,
				"Set angle difference: 'q - parent' (default to "+
				"'parent - domainPack'); '-u' means use differences stored "+
						"in 'utilDoubles'"));
		cmdStruct.add(new CmdStruct("set_aim_diff","[-q{q}] [-u]",null,
				"Set aim difference: 'q - parent' (default to "+
				"'parent - domainPack'); '-u' means use differences stored "+
						"in 'utilDoubles'"));
		cmdStruct.add(new CmdStruct("set_rad_rat","[-q{q}] [-u]",null,
		        "Set radius ratio: 'q/parent' (default to domainData/parent); "+
		        "'-u' means use ratio stored as 'utilDoubles'"+
				"or'parent/q' (default is 'pack/domainPack')"));
		cmdStruct.add(new CmdStruct("store_ang_diff",null,null,"store vector of ang_diff's in 'packData.utilDoubles'"));
		cmdStruct.add(new CmdStruct("store_rad_rat",null,null,"store vector of rad_rat's in 'packData.utilDoubles'"));
		cmdStruct.add(new CmdStruct("store_aim_diff",null,null,"store vector of aim_diff's in 'packData.utilDoubles'"));
		cmdStruct.add(new CmdStruct("mmc","-[] [x]",null,
		        "Move via mean curvature: not yet ready for prime time; see 'MeanMove.java'."));
		cmdStruct.add(new CmdStruct("set_rad","[12]",null,"Record radii, 1 or 2, for use with 'logdiff'"));
		cmdStruct.add(new CmdStruct("logmod",null,null,"Use rad1, rad2 to compute logdiff values (r2-r1/r1)"));
		cmdStruct.add(new CmdStruct("set_stream","[-f {f..}] [-v {z..}] -[ad]",null,"Populate 'curveVector' with "+
				"selected streamlines: '-f' (default), start at barycenters of given faces; " +
				"'-z', from given points (relative to 'domainData'; '-a' append to current; 'd' donwhill (up is default). "+
				"(Must run 'set_rad' and 'set_diff' first.)"));
		cmdStruct.add(new CmdStruct("disp","-b {f..} [normal options ..]",null,"Pick off '-b' option to "+
				"dispay one or more streamlines, set other options through normal processing. If no faces are "+
				"given, display 'curveVector' entries (if any exist)."));
		cmdStruct.add(new CmdStruct("export","{pnum}",null,"export a copy of 'domainData' (the max (eucl) "+
				"packing behind the scenes) to pack pnum"));
	}
	
	public void helpInfo() {
		helpMsg("Commands for PackExtender "+extensionAbbrev+" (Curvature Flow)");
		helpMsg("  chg_aims -[it] {x} {v..}    Set by 'increment' or 'parameter' x\n"+
				"  \n"+ 
				"  \n"+
				"  set_rad_rat -q{p}     Set radius ratio vector 'parent/p' (default to 'domainPack')\n");
				// "  adr -q{p1} -q{p2} a     Setting radii using dom/range vector");
	}
	
	public void StartUpMsg() {
		helpMsg("\nOverview of PackExtender "+extensionAbbrev+" (Curvature Flow):");
		helpMsg("The goal is to manipulate the relative angle sums (resp. radii, aims) of"+
				" two packings (with identical combinatorics). "+
				"On startup, CF stores the parent packing as 'domainPack' " +
				"(converted to euclidean)."+
				"Typically, the parent, 'packData', is then modified and angle sum, aim, or "+
				"radii ratios are stored; e.g., (angle sum in 'packData') - (ang sum in 'domainPack')."+
				"Calling 'chg_aims', 'chg_rad', or 'chg_aim' will apply the vector of stored "
				+ "changes to 'packData', after which the user applies additional manipulations, "+
				"such as repacking. "+
				"The user can also reset the angle/radii/aim differences "+
				"for comparison of 'packData' to pack p with call 'set_ang_diff' "+
				"(resp. 'set_rad_rat', 'set_aim_diff'). These calls default to the original "
				+ "comparisons of 'packData' to 'domainPack'.\n");
		helpInfo(); // print the commands
	}
	
	public double interp(double t,Integer start) {
		return 1.0;
	}
}
