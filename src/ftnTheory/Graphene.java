package ftnTheory;

import java.awt.Color;
import java.util.Iterator;
import java.util.Vector;

import allMains.CPBase;
import complex.Complex;
import exceptions.CombException;
import exceptions.ParserException;
import geometry.EuclMath;
import komplex.CookieMonster;
import komplex.EdgeSimple;
import komplex.PackCreation;
import listManip.EdgeLink;
import listManip.FaceLink;
import listManip.NodeLink;
import packing.PackData;
import packing.PackExtender;
import panels.CPScreen;
import util.CmdStruct;
import util.ColorUtil;
import util.StringUtil;
import util.UtilPacket;

/**
 * Pack Extender to experiment with a "graphene" model of
 * circle packing. Here one considers the geometry that
 * nature imposes on carbon sheets --- graphene with
 * possible dislocations --- to compare it to circle
 * packings. The "carbon" atoms are associated with the
 * vertices of the dual of the triangulation for the
 * circle packing, i.e., they live in the interstices
 * of the packing.
 * 
 * There is an energy (actually, difference from the ground
 * state of the regular hex) associated with the carbons 
 * consisting of bond energies and angle energies. We will
 * also impose an anglesum energy --- think of it as reflecting
 * lack of planarity. Outcome depending of the relative weights alpha, 
 * beta, gamma given to these energies.
 * 
 * NOTE: for computational convenience, we assume the relaxed
 * bond length (for regular hex carbon sheet) is 1, making the
 * relaxed radii sqrt(3)/2. (Actual bond length value for 
 * carbon is roughly 1.421 anstroms.)
 * 
 * ADDED DIRECTION: 5/2013. Deploy 'stitch' models, where we 
 * stitch together sheets of hex with 5/7 nodes along the stitch
 * line. Eventually, experiment with stitching more varied pieces.
 * 
 * @author kens, April 2011
 */

public class Graphene extends PackExtender {

	Vector<Integer> colorVec; // color by vertices
	
	// store bond lengths, angles, energies, colors in vector
	Vector<CarbonEnergy> carbonEnergies;
	
	// experimental value for angle parameter is
	static double ANGLEPARAMETER=24.5; 
	// experimental value for bond parameter is
	static double BONDPARAMETER=155.5/4.0;
	// guesstimated planar parameter 'gamma'. Hope to refine this
	static double PLANARPARAMETER=100;
	
	double angleParam; // this is alpha
	double bondParam; // this is beta
	double planarParam; // this is gamma 
	
	UtilPacket uPkt; // utility packet
	
	// stitch packings
	PackData stitchBase;  	// base for stitching operations
	PackData leftPack; 		// left half plane packing
	PackData rightPack;		// right half plane packing
	// init data associated with 'stitchBase' 
	int currN;				// number of generations
	double currAng1;		
	double currAng2;
	double currPasteMode;
	Vector<Stitch> stitches;  // vector of 'Stitch' objects
	NodeLink stitchVerts; // vertices along the stitched line, from south to north
	int northpole;        // vertex at apex of top stitch line
	int southpole;        // vertex at apex of bottom stitch line
	
	// Constructor
	public Graphene(PackData p) {
		super(p);
		extensionType="GRAPHENE";
		extensionAbbrev="GP";
		toolTip="'Graphene' models carbon nono-structures as the " +
				"dual graphs of circle packings";

		registerXType();
		if (running) {
			packData.packExtensions.add(this);
		}	
		
		// initial parameters: bond and angle are roughly
		//   the physical parameters from simulations.
		//   The planar parameter is pure speculation
		bondParam=40; // 
		angleParam=25;
		planarParam=100; // 
		
		colorVec=new Vector<Integer>(packData.nodeCount+1);
		uPkt=new UtilPacket();
		createCarbons();
		
		// stitch prep
		stitchVerts=new NodeLink();
		northpole=southpole=0;
	}

	/**
	 * create and fill 'carbonEnergies' vector and update it
	 */
	public void createCarbons() {
		carbonEnergies=new Vector<CarbonEnergy>(packData.faceCount+1);
		carbonEnergies.add(0,null);
		for (int f=1;f<=packData.faceCount;f++) 
			carbonEnergies.add(new CarbonEnergy(f));
		updateData();
	}
	
	/**
	 * This is were the user's commands are "parsed"
	 */
	public int cmdParser(String cmd, Vector<Vector<String>> flagSegs) {
		Vector<String> items = null;
		
/*		// ============ mvPole ===============
		if (cmd.startsWith("mv")) {
			int w;
			w=NodeLink.grab_one_vert(packData,flagSegs);
			if (w<=0 || w>packData.nodeCount || packData.kData[w].bdryFlag!=1
					|| stitchVerts.size()<3 || w==northpole || w==southpole ||
					packData.nghb(w,stitchVerts.get(stitchVerts.size()-2))<0)
				return 0;
			
			if (packData.nghb(northpole,w)>=0) {
				stitchVerts.remove(stitchVerts.size()-1);
				northpole=w;
				stitchVerts.add(w);
				fixUp();
			}
			
			if (packData.nghb(southpole,w)>=0) {
				stitchVerts.remove(0);
				southpole=w;
				stitchVerts.add(0,w);
				fixUp();
			}
			return w;
		}
*/		
		// ============ undo ==================
		if (cmd.startsWith("undo")) {
			int n=1;
			if (flagSegs!=null && flagSegs.size()>0) {
				try {
					n=Integer.parseInt(flagSegs.get(0).get(0));
				} catch(Exception ex) {
					n=1;
				}
			}
			// don't allow undo of original pasting stitch
			if (stitches==null || stitches.size()<2) {
				errorMsg("nothing to 'undo'"); 
				return 0;
			}

			// remove all
			if (n>stitches.size()-1) {
				Stitch holdst=stitches.get(0);
				stitches=new Vector<Stitch>(2);
				stitches.add(holdst);
			}
			// remove n stitches
			else while (n>0 && stitches.size()>1) {
				n--;
				stitches.remove(stitches.size()-1);
			}
			
			// TODO: should I avoid new paste if that hasn't changed?
			// reconstruct from ground up
			stitches=stitchFactory(stitches);
			
			return fixUp();
		}
		
		// ============ listStitches ==========
		if (cmd.startsWith("listS")) {
			int count=0;
			if (stitches==null || stitches.size()==0)
				msg("There is currently no stitch list; use 'initS'");
			StringBuilder strb=new StringBuilder("Current Stitch list: \n");
			
			// get first entry to check for initial pasting
			Stitch stch=stitches.get(0);
			boolean paste=false;
			if (stch.getKey()=='P') {
				strb.append("P" +stch.getMode()+" "+stch.getGenerations()+" "+
						stch.getAngle1()+" "+stch.getAngle2()+" ");
				paste=true;
				count++;
			}
			
			// process the rest; exception if there's another 'P'
			Iterator<Stitch> slist=stitches.iterator();
			if (paste) // ignore the paste command
				slist.next(); 
			while (slist.hasNext()) {
				stch=slist.next();
				if (stch.getKey()=='P')
					throw new ParserException("There cannot be a second paste stitch");
				strb.append(Character.toString(stch.getKey())+stch.getMode()+" ");
				count++;
			}
			msg(strb.toString());
			return count;
		}
		
		// ============ export ============
		if (cmd.startsWith("export")) { // preempts 'PackExtender' call
			try {
				items=(Vector<String>)flagSegs.get(0);
				PackData thePack=stitchBase;
				
				// flag specifying packing; default 'stitchBase'
				if (StringUtil.isFlag(items.get(0))) {
					String str=items.remove(0);
					char c=str.charAt(1);
					switch(c) {
					case 'l':
					{
						thePack=leftPack;
						break;
					}
					case 'r':
					{
						thePack=rightPack;
						break;
					}
					default:
					{
						thePack=stitchBase;
					}
					} // end of switch
				}
				
				// pack number
				int pnum=Integer.parseInt((String)items.get(0));
				CPScreen cpS=CPBase.pack[pnum];
				if (cpS!=null) {
					PackData p=thePack.copyPackTo();
					return cpS.swapPackData(p,false);
				}
			} catch (Exception ex) {}
			return 0;
		}
		
		// =========== fix =======================
		else if (cmd.startsWith("fix")) {
			return fixUp();
		}
		
		// ============ stitch action ============
		if (cmd.startsWith("stit")) {

			Vector<Stitch> stitVec=new Vector<Stitch>(3);
			
			// specification one of:
			//    (1) single stitch: "-[code] {v}"; code default 'd', v is pole
			//        see Help for codes.
			//    (2) 'P{k} n a1 a2' (possibly) and one or more 'N{k}' and/or 'S{k}'

			String str=null;
			try {
				items=flagSegs.get(0);
				str=items.get(0);
			
				char c=str.charAt(0);
				// try single stitch form first
				if (c!='N' &&  c!='S' && c!='P') { 
					int type=0;
					if (c=='-') {
						items.remove(0); 
						char t=str.charAt(1);
						switch(t) {
						case 'c':
						{
							type=1;
							break;
						}
						case 'C':
						{
							type=2;
							break;
						}
						case 'a':
						{
							type=3;
							break;
						}
						default:  // 'd' 
						{
							type=0;
						}
						} // end of switch
					}
					int v=NodeLink.grab_one_vert(packData,items.get(0));
					if (v==northpole) 
						stitVec.add(new Stitch("N"+type));
					else if (v==southpole)  
						stitVec.add(new Stitch("S"+type));
					else return 0;
				}
				
				else { // second type of specification
					@SuppressWarnings("unused")
					String holdfs=StringUtil.reconstitute(flagSegs);
					stitVec=stitchParser(flagSegs);
				}
			} catch (Exception ex) {
				throw new ParserException("usage: stitch -[dcCa] {v}");
			}
			
			// now stitch away, get full new 'stitches' vector
			stitches=stitchFactory(stitVec);
			return fixUp();
		}
		
		// ============= initiate stitch ==========
		else if (cmd.startsWith("initS")) {
			int startmode=0;
			double ang1=0.0;
			double ang2=0.0;
			int N=10;
			try {
				items=flagSegs.get(0);
				if (items.get(0).charAt(0)=='P') // optional whether 'P{m}' is here
					startmode=Integer.parseInt(items.remove(0).substring(1));
				N=Integer.parseInt(items.remove(0));
				ang1=Double.parseDouble(items.remove(0));
				ang2=Double.parseDouble(items.remove(0));
			} catch(Exception ex) {
				throw new ParserException("usage: P{m} N ang1 ang2");
			}
			
			Vector<Stitch> stv=new Vector<Stitch>(1);
			stv.add(new Stitch(N,ang1,ang2,startmode));
			
			stitches=stitchFactory(stv);
			return fixUp();
		}
		
		else if (cmd.startsWith("adjust")) {
			int count=0;
			int N=1;
			double eps=.01; // epsilon is adjustment increment
			try {
				while (flagSegs.size()>0) {
					items=flagSegs.remove(0);
					String str=items.get(0);
					if (util.StringUtil.isFlag(str)) {
						if (str.startsWith("-e"))
							eps=Double.parseDouble(items.get(1));
						else if (str.startsWith("-n"))
							N=Integer.parseInt(items.get(1));
					}
					else // else non-flagged number is taken as epsilon
						eps=Double.parseDouble(items.get(0));
				}
			} catch(Exception ex) {
				eps=.01;
			}

			// update the 'carbonEnergies'; computations use these
			//   energies during this full cycle, only adjust their
			//   data afterward.
			updateData();
			// get energy to start
			double endE=quickEnergy();
			double startE=endE;
			boolean wflag=true;
			
			// loop 
			while (count<N && (wflag || (startE-endE)>.00001)) {
				wflag=false;
				startE=endE;
//				eps *= startE;
			
				// adjust r to r-eps*grad (leave bdry unchanged)
				double []grad=energyGrad();
				double gradNorm=0.0;
				for (int v=1;v<=packData.nodeCount;v++) {
					gradNorm += grad[v]*grad[v];
					if (packData.kData[v].bdryFlag!=1) { // leave bdry unchanged
						double newrad=packData.rData[v].rad-eps*grad[v];
						if (newrad<=0)
							newrad=packData.rData[v].rad/2.0;
						packData.rData[v].rad=newrad;
					}
				}
				gradNorm=Math.sqrt(gradNorm);
			
				// get energy at end
				
				updateData();
				endE=quickEnergy();
				
				msg("E = "+String.format("%.6e",endE)+" Change = "+String.format("%.6e",endE-startE)+"; |grad| = "+
					String.format("%.6e",gradNorm));
				count++;
			}
			
			msg("Exit count "+count);
			return count;
		}
		
		// ********* energy
		if (cmd.startsWith("energ")) {
			updateData();
			reportEnergy();
			return 1;
		}
		
		// ********** flip
		else if (cmd.startsWith("flip")) {
			FaceLink flist=null;
			try {
				items=flagSegs.get(0);
				flist=new FaceLink(packData,items);
			} catch(Exception ex) {}
			
			if (flist==null || flist.size()<2)
				return 0;
			int f=flist.get(0);
			int g=flist.get(1);
			EdgeSimple duale=packData.reDualEdge(f,g);
			if (duale==null)
				return 0;
			int ans=cpCommand("flip "+duale.v+" "+duale.w);
			if (ans>0) createCarbons();
			return ans;
		}
		
		// ********** set parameters
		else if (cmd.startsWith("set_para")) {
			try {
				while (flagSegs!=null && flagSegs.size()>0) {
					items=flagSegs.remove(0);
					String flg=items.remove(0);
					if (flg.startsWith("-b")) // 'bend' parameter
						bondParam=Double.parseDouble(items.get(0));
					else if (flg.startsWith("-a")) // 'angle' parameter
						angleParam=Double.parseDouble(items.get(0));
					else if (flg.startsWith("-g")) // 'gamma' parameter
						planarParam=Double.parseDouble(items.get(0));
					else if (flg.startsWith("-d")) { // all to default
						bondParam=BONDPARAMETER;
						angleParam=ANGLEPARAMETER;
						planarParam=PLANARPARAMETER;
					}
				} 
			} catch(Exception ex) {
				msg("energy: error in reading one of the parameters");
			}
			msg("Total Energy: "+String.format("%.8e", totalEnergy()));
			for (int v=1;v<=packData.nodeCount;v++)
				packData.kData[v].color=CPScreen.coLor(colorVec.get(v));
			return 1;
		}
		
		// ********** relax
		else if (cmd.startsWith("relax")) {
			// we assume a relaxed bond length of 1
			double rad=CPBase.sqrt3by2;
			try {
				rad=Double.parseDouble(flagSegs.get(0).get(0));
			} catch(Exception ex){
				rad=CPBase.sqrt3by2;
			}
			for (int v=1;v<=packData.nodeCount;v++)
				packData.rData[v].rad=rad;
			
			return 1;
		}
		
		// ************** bond
		else if (cmd.startsWith("bond")) {
			int count=0;
			EdgeLink edges=null;
			try {
				edges=new EdgeLink(packData,flagSegs.get(0));
			} catch(Exception ex) {
				edges=new EdgeLink(packData,"a");
			}
			Iterator<EdgeSimple> list=edges.iterator();
			while (list.hasNext()) {
				EdgeSimple edge=list.next();
				int v=edge.v;
				int w=edge.w;
				if (edge.w<v) {
					w=v;
					v=edge.w;
				}
				EdgeSimple dual=packData.dualEdge(v,w);
				if (dual!=null) {
					int fa=dual.v;
					CarbonEnergy carbon=carbonEnergies.get(fa);
					int k=packData.faces[fa].vertIndx(v);
					int colindx=CPScreen.col_to_table(carbon.bondColors[k]); // get index
					cpCommand("disp -det4c"+colindx+" "+v+" "+w);
					count++;
				}
			}
			return count;
		}
		
		return 0;
	}

	/**
	 * Have 'carbonEnergies' update their radii and overlaps, 
	 * then update their bond, angle, and energy data.
	 */
	public void updateData() {
		for (int f=1;f<=packData.faceCount;f++) {
			carbonEnergies.get(f).update();
		}
	}
	
	/**
	 * Color coding of faces, vertices, and edges. 
	 * CAUTION: calling routine must insure that 
	 * Vector<CarbonEnergy> 
	 * data has been updated.
	 */
	public void updateColors() {
		// for carbons
		Vector<Double> carbs=new Vector<Double>(packData.faceCount+1);
		carbs.add(0,null); // first spot unused
		for (int f=1;f<=packData.faceCount;f++)
			carbs.add(carbonEnergies.get(f).carbonEnergy());
		Vector<Color> carbColors=ColorUtil.richter_red_ramp(carbs); // first spot also unused
		for (int f=1;f<=packData.faceCount;f++) {
			Color colf=carbColors.get(f);
			carbonEnergies.get(f).carbonColor=colf;
			packData.faces[f].color=new Color(colf.getRed(),colf.getGreen(),colf.getBlue());
		}
		
		// edges, but stored in 'carbonEnergies' --- interior edges
		//   are included twice.
		Vector<Double> edgeE=new Vector<Double>(3*packData.faceCount+1);
		edgeE.add(0,null); // first spot unused
		for (int f=1;f<=packData.faceCount;f++) {
			CarbonEnergy cE=carbonEnergies.get(f);
			for (int j=0;j<3;j++) {
				edgeE.add(cE.bondLengths[j]*cE.bondLengths[j]-1.0);
			}
		}
		Vector<Color> edgeColors=ColorUtil.richter_red_ramp(edgeE); // first spot also unused
		int tick=1;
		for (int f=1;f<=packData.faceCount;f++) {
			CarbonEnergy cE=carbonEnergies.get(f);
			for (int j=0;j<3;j++) {
				Color cole=edgeColors.get(tick++);
				cE.bondColors[j]=cole;
			}
		}

		// ring energies to color the vertices
		Vector<Double> ringEnergies=new Vector<Double>(packData.nodeCount+1);
		ringEnergies.add(0,null); // first spot unused
		for (int v=1;v<=packData.nodeCount;v++) {
			ringEnergies.add(ringEnergy(v));
		}
		Vector<Color> vertColors=ColorUtil.richter_red_ramp(ringEnergies); // first spot also unused
		for (int v=1;v<=packData.nodeCount;v++) {
			Color col=vertColors.get(v);
			packData.kData[v].color=new Color(col.getRed(),col.getGreen(),col.getBlue());
		}
	}

	// ========= energy computations ===============
	
	/**
	 * Just energy total for adjust routine.
	 * CAUTION: calling routine must insure that 'CarbonEnergy'
	 * data has been updated.
	 */
	public double quickEnergy() {
		double totalE=0.0;
		for (int v=1;v<=packData.nodeCount;v++) 
			totalE += ringEnergy(v);
		return totalE;
	}
	
	/** 
	 * Compute various energy contributions, max contributors,
	 * set colors, report results.
	 * CAUTION: calling routine must insure that 'CarbonEnergy'
	 * data has been updated.
	 * @return total energy
	 */
	public double reportEnergy() {
		double totalE=0.0;
//		double carbonE=0.0;
		double topRingE=0.0;
		int topRingV=0;
		double topCarbonE=0.0;
		int topCarbonFace=0;
		double topBondE=0.0;
		int topAngSumV=0;
		double topAngSumE=0.0;
		EdgeSimple topBondEdge=new EdgeSimple(0,0);

		for (int f = 1; f <= packData.faceCount; f++) {
			CarbonEnergy cE = carbonEnergies.get(f);
			double newC = cE.carbonEnergy();

			// look for largest carbon
			if (newC > topCarbonE) {
				topCarbonE = newC;
				topCarbonFace = f;
			}

			int k = -1;
			for (int j = 0; (j < 3 && k < 0); j++) {
				int myf = cE.face;
				int vj = packData.faces[myf].vert[j];
				int oppf = packData.face_opposite(packData.faces[myf], vj);
				if (oppf >= 0) { // ignore phantom bonds
					double L = cE.bondLengths[j];
					L = L * L;
					double newB = (bondParam / 4.0) * (L - 1.0) * (L - 1.0);
					if (newB > topBondE && myf < oppf) {
						topBondE = newB;
						topBondEdge = new EdgeSimple(myf, oppf);
						k = j;
					}
				}
			}
		}

		// identify largest ring, angle sum energy and its vert
		for (int v = 1; v <= packData.nodeCount; v++) {
			double newE = ringEnergy(v);
			double newASE=0.0;
			if (packData.kData[v].bdryFlag==0) {
				packData.e_anglesum_overlap(v,packData.rData[v].rad,uPkt);
				double term=2.0*Math.PI-uPkt.value;
				newASE=planarParam*term*term;
			}
			if (newE > topRingE) {
				topRingE = newE;
				topRingV = v;
			}
			if (packData.kData[v].bdryFlag==0 && (newASE>topAngSumE)) {
				topAngSumE=newASE;
				topAngSumV=v;
			}
			totalE += newE+newASE;
		}

		msg("Total energy is " + String.format("%.8e", totalE) + "\n"
				+ "  Largest ring energy: " + String.format("%.6e", topRingE)
				+ " at " + topRingV + "\n"
				+ "  Largest anglesum energy: " + String.format("%.6e", topAngSumE)
				+ " at " + topAngSumV + "\n"				
				+ "  Largest carbon (angle) energy: "
				+ String.format("%.6e", topCarbonE) + " at " + topCarbonFace
				+ "\n" + "  Largest bond energy:"
				+ String.format("%6e", topBondE) + ", dual edge <"
				+ topBondEdge.v + "," + topBondEdge.w + ">");

		updateColors();
		return totalE;
	}

	/**
	 * Total energy with current parameters and relaxedBond.
	 * fill 'colorVec' with ramp of energies by vertex.
	 * @return double
	 */
	public double totalEnergy() {
		double TE=0.0;
		Vector<Double> vertEnergy=new Vector<Double>(packData.nodeCount+1);
		vertEnergy.add(0,null);
		for (int v=1;v<=packData.nodeCount;v++) {
			double rE=ringEnergy(v);
			vertEnergy.add(Double.valueOf(rE));
			TE+=rE;
		}
		colorVec=ColorUtil.blue_red_diff_ramp(vertEnergy);
		return TE;
	}
	
	/**
	 * Return energy for the ring of carbons around v. This is
	 * in bonds, angles at the carbons, and anglesum at v.
	 * Use half the bond energy to compensate for double counting; 
	 * use only the carbon angles toward v; include anglesum energy 
	 * only for interior v.
	 * CAUTION: calling routine must insure that 'CarbonEnergy'
	 * data has been updated. 
	 * @param v
	 * @return double energy
	 */
	public double ringEnergy(int v) {
		int []fflower=packData.kData[v].faceFlower;
		double bondE=0.0;
		double angE=0.0;
		for (int j=0;j<packData.kData[v].num;j++) {
			CarbonEnergy cE=carbonEnergies.get(fflower[j]);
			if (j>0 || packData.kData[v].bdryFlag==0) {// skip first for bdry
				bondE+=cE.getBondEnergy(v)/2.0;
			}
			angE += cE.getAngleEnergy(v);
		}
		if (!packData.e_anglesum_overlap(v,packData.rData[v].rad,uPkt))
			Oops("Error in computing angle sum");
		double planarE=0.0;
		if (packData.kData[v].bdryFlag==0) { // interior?
			double angleErr=Math.PI*2-uPkt.value;
			planarE=planarParam*(angleErr*angleErr);
		}
		return (bondE+angE+planarE);
	}
	
	/**
	 * Find the euclidean inradius for face f
	 * @param p
	 * @param f
	 * @return double
	 */
	public static double inRad(PackData p,int f) {
		double a=p.intendedEdgeLength(p.faces[f].vert[0],p.faces[f].vert[1]);
		double b=p.intendedEdgeLength(p.faces[f].vert[1],p.faces[f].vert[2]);
		double c=p.intendedEdgeLength(p.faces[f].vert[2],p.faces[f].vert[0]);
		return EuclMath.eucl_tri_inradius(a,b,c);
	}
	
	// ********************* calculus stuff ********************************
	
	/**
	 * Compute bond length for carbons associated with faces having 
	 * radii <r,s,t> and <r,t,u>.
	 * @param r
	 * @param s
	 * @param t
	 * @param u
	 * @return double
	 */
	public static double L(double r,double s,double t,double u) {
		return Math.sqrt((r*s*t)/(r+s+t))+Math.sqrt((r*u*t)/(r+u+t));
	}
	
	/**
	 * Cosine of bond angle at r, radii {r,t,u}
	 * @param r,t,u, radii 
	 * @return double
	 */
	public static double C(double r,double t,double u) {
		return (2.0*(t/(r+t))*(u/(r+u))-1.0);
	}
	
	public static double dCdr(double r,double t,double u) {
		return (-2.0*t*u/((r+t)*(r+u))*(1/(r+t)+1/(r+u)));
	}

	public static double dCdt(double r,double t,double u) {
		return (2*r*u/((r+t)*(r+t)*(r+u)));
	}
	
	public static double dCdu(double r,double t,double u) {
		return dCdt(r,u,t);
	}
	
	/**
	 * Angle energy
	 * @param r, s, t, u, v radii
	 * @return double
	 */
	public double A(double r,double s,double t,double u,double v) {
		double term=L(r,s,t,u)*L(r,t,u,v)*C(r,t,u)+0.5;
		return angleParam*term*term;
	}
	
	public double dAdr(double r,double s,double t,double u,double v) {
		return (2.0*angleParam)*(L(r,s,t,u)*L(r,t,u,v)*C(r,t,u)+0.5)*
			(L(r,s,t,u)*(L(r,t,u,v)*dCdr(r,t,u)+dLdr(r,t,u,v)*C(r,t,u))+
			dLdr(r,s,t,u)*L(r,t,u,v)*C(r,t,u));
	}
	
	public double dAds(double r,double s,double t,double u,double v) {
		return (2.0*angleParam)*(L(r,s,t,u)*L(r,t,u,v)*C(r,t,u)+0.5)*
			dLds(r,s,t,u)*L(r,t,u,v)*C(r,t,u);
	}
	
	public double dAsv(double r,double s,double t,double u,double v) {
		return dAds(r,v,u,t,s);
	}
	
	public double dAdt(double r,double s,double t,double u,double v) {
		return (2.0*angleParam)*(L(r,s,t,u)*L(r,t,u,v)*C(r,t,u)+0.5)*
			(L(r,s,t,u)*(L(r,t,u,v)*dCdt(r,t,u)+dLdt(r,t,u,v)*C(r,t,u))+
			dLdt(r,s,t,u)*L(r,t,u,v)*C(r,t,u));
	}
	
	public double dAdu(double r,double s,double t,double u,double v) {
		return dAdt(r,v,u,t,s);
	}
	
	public double dAdv(double r,double s,double t,double u,double v) {
		return dAds(r,v,u,t,s);
	}
	
	public static double dLdr(double r,double s,double t,double u) {
		return (0.5)*Math.sqrt(t/r)*
			(Math.sqrt(s)*(s+t)/(Math.pow(r+s+t,3/2))+
					Math.sqrt(u)*(u+t)/(Math.pow(r+u+t,3/2)));
	}
	
	public static double dLdt(double r,double s,double t,double u) {
		return dLdr(t,s,r,u);
	}
	
	public static double dLds(double r,double s,double t,double u) {
		return (0.5)*Math.sqrt(r*t/s)*(r+t)/Math.pow(r+s+t,3/2);
	}
	
	public static double dLdu(double r,double s,double t,double u) {
		return dLds(r,u,t,s);
	}
	
	public double dBdr(double r,double s,double t,double u) {
		double L=L(r,s,t,u);
		return bondParam*(L*L-1)*L*dLdr(r,s,t,u);
	}

	public double dBdt(double r,double s,double t,double u) {
		double L=L(r,s,t,u);
		return bondParam*(L*L-1)*L*dLdt(r,s,t,u);
	}

	public double dBds(double r,double s,double t,double u) {
		double L=L(r,s,t,u);
		return bondParam*(L*L-1)*L*dLdt(r,s,t,u);
	}

	public double dBdu(double r,double s,double t,double u) {
		double L=L(r,s,t,u);
		return bondParam*(L*L-1)*L*dLdu(r,s,t,u);
	}
	
	// Angle Sum energy
	public double phi(int v,double r) {
		packData.e_anglesum_overlap(v,r,uPkt);
		return uPkt.value;
	}
	
	public double dphidr(double r,double t,double u) {
		return (-1.0)*Math.sqrt(u*t/r)*(1.0/(r+t)+1.0/(r+u))/Math.sqrt(r+u+t);
	}
	
	public double dphidt(double r,double t,double u) {
		return Math.sqrt(r*u/t)*(1/Math.sqrt(r+t+u))/(r+t);
	}
	
	public double dphidu(double r,double t,double u) {
		return dphidt(r,u,t);
	}
	
	/**
	 * Return array, gradient of energy w.r.t. radii.
	 * @return double[nodeCount+1]
	 */
	public double []energyGrad() {
		double []grad=new double[packData.nodeCount+1];
		for (int vert=1;vert<=packData.nodeCount;vert++) {
			int num=packData.kData[vert].num;
			double r=packData.rData[vert].rad;
			boolean bdryvert=packData.kData[vert].bdryFlag==1;
			
			// each contribution due to anglesums = ASfactor*partial.
			packData.e_anglesum_overlap(vert,r,uPkt);
			double ASfactor=(-2.0)*planarParam*(Math.PI*2.0-uPkt.value);
			
			// get data for faces {r,s,t}, {r,t,u}, and {r,u,v}
			for (int j=0;j<num;j++) {
				int sv=packData.kData[vert].flower[(j-1+num)%num];
				double s=packData.rData[sv].rad;
				int tv=packData.kData[vert].flower[j];
				double t=packData.rData[tv].rad;
				int uv=packData.kData[vert].flower[(j+1)%num];
				double u=packData.rData[uv].rad;
				int vv=packData.kData[vert].flower[(j+2)%num];
				double v=packData.rData[vv].rad;

				if (j==0 && bdryvert) { 
					s=u; // reflected data for phantom bond
				}
				else if (j==(num-1) && bdryvert) {
					v=t; // reflected data for phantom bond
				}
				
				if ((j>0 || !bdryvert) && (j<(num-1) || !bdryvert)) {
					// bond contributions (cut in half due to duplication)
					grad[vert] += 0.5*dBdr(r,s,t,u);
					if (j==0) { // not bdry, 
						grad[sv] += 0.5*dBds(r,s,t,u);
					}
					grad[tv] += 0.5*dBdt(r,s,t,u);
					if (j==(num-1)) // not bdry
							grad[uv] += 0.5*dBdu(r,s,t,u);
					
					// angle contributions
					grad[vert] += dAdr(r,s,t,u,v);
					grad[sv] +=dAds(r,s,t,u,v);
					grad[tv] +=dAdt(r,s,t,u,v);
					grad[uv] +=dAdu(r,s,t,u,v);
					grad[vv] +=dAdv(r,s,t,u,v);
				}
				
				// anglesum contributions if vert interior
				if (packData.kData[vert].bdryFlag==0) {
					grad[vert]+=ASfactor*dphidr(r,t,u);
					grad[tv]+=ASfactor*dphidt(r,t,u);
					grad[uv]+=ASfactor*dphidu(r,t,u);
				}
			}

		}
		return grad;
	}

	/******************** stitch stuff ****************************/
	
	/**
	 * Given a 'Stitch' (N or S, not P), then apply it to 
	 * 'packData'. This may be called several times; ultimately
	 * calling routine must update packData.
	 * @param stitch Stitch
	 * @return 0 on error
	 */
	public int stitchIt(Stitch stitch) {
		if (stitchBase==null || stitch.getKey()=='P')
			return 0;
		int v=0;
		int newIndx=0;
		int oldnorth=northpole;
//		int oldsouth=southpole;
		if (stitch.getKey()=='N')
			v=northpole;
		else if (stitch.getKey()=='S')
			v=southpole;
		packData.vertexMap=null;
		switch (stitch.getMode()) {
		case 0: // close flower by identifying
		{
			int origv=packData.kData[v].flower[0];
			packData.adjoin(packData, v, v, 1);
			
			// fix up numbering
			newIndx=packData.vertexMap.findW(origv);
			break;
		}	
		case 1: // close flower with edge, choose left as pole
		{
			int w=packData.kData[v].flower[0];
			cpCommand("enclose 0 "+v);
			newIndx=w;
			break;
		}
		case 2: // close flower with edge, choose right as pole
		{
			int u=packData.kData[v].flower[packData.kData[v].num];
			cpCommand("enclose 0 "+v);
			newIndx=u;
			break;
		}
		case 3: // enclose with 1 new circle
		{
			int lft=packData.kData[v].flower[0];
			int rght=packData.kData[v].flower[packData.kData[v].num];
			cpCommand("enclose 1 "+v);
			cpCommand("enclose 0 "+lft);
			cpCommand("enclose 0 "+rght);
			newIndx=packData.nodeCount;
			break;
		}
		} // end of switch
		
		if (newIndx==0)
			throw new CombException("stitch failed at "+v);
		if (packData.vertexMap!=null) {
			NodeLink nwNL=new NodeLink();
			Iterator<Integer> stv=stitchVerts.iterator();
			while (stv.hasNext()) 
				nwNL.add(packData.vertexMap.findW(stv.next()));
			northpole=packData.vertexMap.findW(northpole);
			southpole=packData.vertexMap.findW(southpole);
			stitchVerts=nwNL;
		}
		if (v==oldnorth) {
			northpole=newIndx;
			stitchVerts.add(northpole);
		}
		else {
			southpole=newIndx;
			stitchVerts.add(0,newIndx);
		}
		return 1;
	}
	
	/**
	 * After a stitch action, need to fix things up.
	 */
	public int fixUp() {	
		int count=0;
		try {
			// combinatorics, aims
			packData.setCombinatorics();
			packData.set_aim_default();
			
			// set boundary, pole radii
			count=cpCommand("set_rad .075 b");
			count += cpCommand("set_rad .15 "+southpole+" "+northpole);
			count += cpCommand("gamma "+northpole);
			
			// need better control on the edges of the slits
			count += cpCommand("set_aim 1.75 "+southpole+" "+northpole);
			int su=packData.kData[southpole].flower[0];
			int sd=packData.kData[southpole].flower[packData.kData[southpole].num];
			int nu=packData.kData[northpole].flower[0];
			int nd=packData.kData[northpole].flower[packData.kData[northpole].num];
			count += cpCommand("set_aim 1.0 "+su+" "+sd+" "+nu+" "+nd);
			
			// repack, layout, color by degree
			count += cpCommand("repack");
			count += cpCommand("layout");
			count += cpCommand("color -c d");
			
			// display stitched edges, graph, dots
			packData.elist=EdgeLink.verts2edges(packData,stitchVerts,false);
			count += cpCommand("Disp -w -f -tf b -t1ft3 "+northpole+" -t2ft3 "+southpole+" -cf i -et8 elist");
		} catch (Exception ex) {
			errorMsg("Graphene: problem with 'fixUp'");
			count=0;
		}
		return count;
	}
	
	/**
	 * Parse strings and create add-ons to vector of 'Stitch's. 
	 * Calling routines checks if initial pasting is specified.
	 * @param flagSegs Vector<Vector<String>>
	 * @return Vector<Stitch>, new additions, exception or null on error
	 */
	public Vector<Stitch> stitchParser(Vector<Vector<String>> flagSegs) {
		Vector<Stitch> stchvec=null;
		try {
			stchvec=new Vector<Stitch>(3);
			Vector<String>items=null;
			if (flagSegs==null || (items=flagSegs.get(0)).size()==0)
				return null;
			
			// check for 'P' option first
			if (items.get(0).charAt(0)=='P') {
				int mode=0;
				int N;
				double a1;
				double a2;
				try {
					mode=Integer.parseInt(items.remove(0).substring(1));
					N=Integer.parseInt(items.remove(0));
					a1=Double.parseDouble(items.remove(0));
					a2=Double.parseDouble(items.remove(0));
					stchvec.add(new Stitch(N,a1,a2,mode));
				} catch (Exception ex) {
					throw new ParserException("usage: P{m} n ang1 ang2");
				}
			}

			// process the rest, should be form 'N{k}' or 'S{k}' 
			Iterator<String> its=items.iterator();
			while (its.hasNext()) {
				stchvec.add(new Stitch(its.next()));
			}
		} catch (Exception e1) {
			// TODO Auto-generated catch block
			throw new ParserException("Failed to parse stitches: "+e1.getMessage());
		}
		return stchvec;
	}
	
	/**
	 * Create and store 'stitchBase' packing and half-planes. The 'basePack'
	 * is n generations of hex. Cut two half planes 'leftPack' and 'rightPack' 
	 * after rotating by phi1 and phi2, respectively. Adjoin these using 
	 * alpha and the neighbors on either side, giving 'stitchBase'.
	 * (Note that things are moved into packData elsewhere.)
	 * 
	 * Initialize data that we keep track of: tips of slits, where stitching
	 * takes place, angles phi1/phi2, edges on the seam, boundary radii.
	 * @param phi1
	 * @param phi2
	 * @return int count
	 */
	public int stitchStart(int n,double phi1,double phi2,int startMode) {

		// build hex to cut half planes from
		PackData basePack=PackCreation.hexBuild(n);
		cpCommand(basePack,"repack");
		cpCommand(basePack,"layout");
		cpCommand("norm_scale -u M");
		cpCommand("scale 2.0");
		
		// create leftPack/rightPack: poison determined by phi1/phi2
		leftPack=cut4halfplane(basePack, phi1);
  	  	msg("cookie for 'leftPack' seems to have succeeded");
		rightPack=cut4halfplane(basePack, phi2);
  	  	msg("cookie for 'rightPack' seems to have succeeded");

  	  	// now attach along 2 edges, <v, alpha, w> to <rw, alpha,rv>,
  	  	int v=leftPack.kData[1].flower[0];
  	  	int w=leftPack.kData[1].flower[leftPack.kData[1].num];
  	  	
  	  	// may need this in future
  	  	int rw=rightPack.kData[1].flower[rightPack.kData[1].num];
  	  	
  	  	stitchBase=leftPack.copyPackTo();
  	  	
		// restart stitch history (more modes are needed)
		stitches=new Vector<Stitch>(3);
		stitches.add(new Stitch(n,phi1,phi2,startMode));
  	  	switch(startMode) {
  	  	default: // (currently the only)
  	  	{
  	  	  	// adjoin at alpha's and one vert either side
  	  	  	stitchBase.adjoin(rightPack, v, rw, 2);
  	  	  	stitchBase.complex_count(false);
  	  	  	
  	  	  	// these shouldn't have changed indices
  	  	  	northpole=v;
  	  	  	southpole=w;
  	  	  	break;
  	  	}
  	  	} // end of switch

  	  	// swap poles for 2 and 3, 
  	  	stitchBase.swap_nodes(northpole, 2);
  	  	stitchBase.swap_nodes(southpole, 3);
  	  	northpole=2;
  	  	southpole=3;
  	  	
  	  	stitchBase.complex_count(true);
  	  	stitchBase.setAlpha(1);
  	  	stitchBase.setGamma(2);
  	  	cpCommand(stitchBase,"set_rad .075 a");
  	  	stitchBase.facedraworder(false);
  	  	stitchBase.set_aim_default();
  	  	
		// keep track of the seam
  	  	stitchVerts=new NodeLink();
  	  	stitchVerts.add(3);
  	  	stitchVerts.add(1);
  	  	stitchVerts.add(2);

  	  	return 1;
	}
	
	/**
	 * Process a vector of stitches, which may start anew or 
	 * add to current stitches. Either way, return the full new 
	 * stitch vector. Calling routine must update 'packData'.
	 * @param stVec Vector<Stitch>
	 * @return Vector<Stitch> or null
	 */
	public Vector<Stitch> stitchFactory(Vector<Stitch> stVec) {
		Vector<Stitch> newSV=new Vector<Stitch>(3);
		if (stVec==null || stVec.size()==0) { 
			errorMsg("Error, no stitching specified");
			return null;
		}
		
		// start a new one? then create 'stitchBase' and 'packData'
		boolean newPaste=false;
		Stitch stitch=stVec.get(0);
		if (stitch.getKey()=='P') {
			if (currPasteMode!=stitch.getMode() || currN!=stitch.getGenerations() ||
					currAng1==stitch.ang1 || currAng2==stitch.ang2) {
				if (stitchStart(stitch.getGenerations(),stitch.getAngle1(),
							stitch.getAngle2(),stitch.getMode())==0) {
					errorMsg("Failed on start");
					return null;
				}
				currN=stitch.getGenerations();
				currAng1=stitch.getAngle1();
				currAng2=stitch.getAngle2();
				currPasteMode=stitch.getMode();
			}
			newSV.add(stVec.remove(0));
			newPaste=true;
			
	  	  	// copy to packData
			CPScreen cps=packData.cpScreen; 
			cps.swapPackData(stitchBase,true);
			packData=cps.packData;
		}
		
		// no more?
		if (stVec.size()==0) {
			return newSV;
		}
		
		// if a new start, trash old 'stitches'; else append to it
		if (!newPaste) {
			Iterator<Stitch> getold=stitches.iterator();
			while (getold.hasNext()) {
				newSV.add(getold.next());
			}
		}
			
		Iterator<Stitch> sti=stVec.iterator();
		boolean okay=true;
		while (sti.hasNext() && okay) {
			stitch=sti.next();
  	  		int ans=stitchIt(stitch);
  	  		if (ans==0) {
  	  			okay=false;
  	  			errorMsg("stitch "+stitch.getKey()+stitch.getMode()+" failed");
  	  			if (newSV.size()==0)
  	  				return null;
  	  			else
  	  				return newSV;
  	  		}
  	  		newSV.add(stitch);
		} // end of while
		if (newSV.size()==0) {
			errorMsg("No stitches done.");
			return null;
		}
		return newSV;
	}
	
	/**
	 * Given a packing (laid out), assume alpha = 1 is at origin.
	 * Rotate by 'angle', cut out below the x-axis, return
	 * the result, p unchanged. 
	 * @param p PackData
	 * @param angle double
	 * @return PackData
	 */
	public PackData cut4halfplane(PackData p, double angle) {
		PackData newPack=p.copyPackTo();
		Complex rot=new Complex(0.0,Math.PI*(-angle)).exp();
		newPack.poisonEdges=null;
		newPack.poisonVerts=new NodeLink(newPack);
		for (int v=1;v<=newPack.nodeCount;v++) {
			if (newPack.rData[v].center.times(rot).y<(-0.01*newPack.rData[1].rad))
				newPack.poisonVerts.add(v);
		}
		newPack.gen_mark(newPack.poisonVerts, -1, true);
		
		// add one more layer of poisonverts, find an alpha
		int newAlp=-1;
		for (int v=1;v<=newPack.nodeCount;v++) {
			if (newPack.kData[v].mark<=2)
				newPack.poisonVerts.add(v);
			if (newAlp<0 && newPack.kData[v].bdryFlag==0 && newPack.kData[v].mark==3)
				newAlp=v;
		}

  	  	CookieMonster cM=null;
  	  	int origAlpha=newPack.alpha; // expect this to be 1
  	  	int outcome=0;
  	  	try {
  	  		cM=new CookieMonster(newPack,"-v "+newAlp);
  	  		outcome=cM.goCookie();
  	  	  	if (outcome<0) {
  	  	  		cM=null;
  	  	  		errorMsg("cookie failed in 'cut4halfplane'.");
  	  	  		return null;
  	  	  	}
  	  	} catch (Exception ex) {
  	  		this.errorMsg("cookie problem for half plane: "+ex.getMessage());
  	  		return null;
  	  	}
  	  	newPack=cM.getPackData();
  	  	newPack.poisonEdges=null;
  	  	newPack.poisonVerts=null;
  	  	cM=null;
  	  	int new1=newPack.vertexMap.findV(origAlpha);
		if (new1>0)  // would like 1 to be the original alpha
			newPack.swap_nodes(1, new1);
		
  	  	newPack.complex_count(true);
		return newPack;
	}
	
	/********************************************
	 * Utility class:
	 * Holds stitch instructions: 'key' is N or S for north/south 
	 * pole, P for initial pasting. 'mode' is appropriate integer.
	 * Input string for stitches is form 'N{k}' or 'S{k}', N/S for north
	 * or south, k for 'mode', the type of stitch. Modes so far:
	 *   0: identify bdry neighbors ('d' command
	 *   1: close up with edge, pole to left
	 *   2: close up with edge, pole to right
	 *   3: add vert and close, new vert is pole
	 *   ????? more to come?
	 *   
	 * There is also a start stitch
	 */
	class Stitch {
		char key; // N=northpole, S=southpole, P=paste to start
		int mode;
		double ang1;
		double ang2;
		int N;
		
		// Constructor
		public Stitch(String sstr) {
			mode=0;
			String str=sstr.trim();
			if (str.charAt(0)=='N')
				key='N';
			else if (str.charAt(0)=='S')
				key='S';
			else if (str.charAt(0)=='P')
				throw new ParserException("use 'initS' to start a new packing");
			else
				throw new ParserException("failed to build Stitch with '"+sstr+"'.");
			try {
				mode=Integer.parseInt(str.substring(1));
			} catch (NumberFormatException e) {
				// TODO Auto-generated catch block
				throw new ParserException("usage: stitch [NS]{k} (e.g. N2 or S0; mode malformed");
			}
			
			// set default -- shouldn't be used
			ang1=0.0;
			ang2=0.0;
			N=10;
		}
			
		public Stitch(int n,double a1,double a2,int startMode) {
			key='P';
			ang1=a1;
			ang2=a2;
			mode=startMode;
			N=n;
		}
		
		/**
		 * What is the pole designation?
		 * @return String, one of S N or P
		 */
		private char getKey() {
			return key;
		}
		
		/**
		 * What is the mode?
		 * @return int 
		 */
		private int getMode() {
			return mode;
		}
		
		private double getAngle1() {
			return ang1;
		}
		
		private double getAngle2() {
			return ang2;
		}
		
		private int getGenerations() {
			return N;
		}

	}

	/********************************************
	 * Utility class:
	 * Holds data on carbons: bond lengths, bond angles, angle
	 * energies. Have three bonds, hence three bond angles/energies. 
	 * 'verts' typically taken from the associated face.
	 * Note that for carbons associated with boundary faces, one
	 * or two bonds are 'phantom' bonds to allow for the angle
	 * energy computation. Not yet settled on best approach; for 
	 * now, assume phantom neighboring face has same inradius as
	 * this face.
	 */
	class CarbonEnergy {
		int face; // associated face
		int []verts=new int[3];
		double []rad=new double[3];
		double []ov=new double[3];
		public Color carbonColor;
		public Color []bondColors;
		public double []bondLengths;
		public double []bondAngles;  // angle[j] associated with verts[j].
		UtilPacket up=new UtilPacket();

		// Constructor
		public CarbonEnergy(int f) {
			face=f;
			for (int j=0;j<3;j++) {
				verts[j]=packData.faces[face].vert[j];
			}
			bondColors=new Color[3];
			for (int j=0;j<3;j++)
				bondColors[j]=CPScreen.getBGColor();
			bondLengths=new double[3]; // j bond is dual to edge opposite j vert
			bondAngles=new double[3]; // j angle is complement of that at j vert
			carbonColor =CPScreen.getBGColor();
		}

		/**
		 * Update local data, e.g., when radii or overlaps change.
		 */
		public void update() {
			
			// update radii and overlaps
			for (int j=0;j<3;j++) 
				rad[j]=packData.rData[verts[j]].rad;
			if (packData.overlapStatus) {
				for (int j=0;j<3;j++) {
					ov[j]=packData.kData[verts[(j+1)%3]].
						overlaps[packData.nghb((j+1)%3,(j+2)%3)];
				}
			}

			// compute and store the angles. Recall, the j
			//    angle at the carbon is the complement of the
			//    angle in the face at vert j.
			for (int j=0;j<3;j++) {
				double r=rad[j];
				double rr=rad[(j+1)%3];
				double rl=rad[(j+2)%3];			
				if (!packData.overlapStatus) {
					bondAngles[j]=Math.PI-Math.acos(1-2*(rr/(r+rr))*(rl/(r+rl)));
				}
				else  {
					double ovl=ov[j];
					double ovlr=ov[(j+1)%3];
					double ovll=ov[(j+2)%3];
					EuclMath.e_cos_overlap(r,rr,rl,ovl,ovlr,ovll,up);
					bondAngles[j]=Math.PI-Math.acos(up.value);
			      }
			} // done with bond angles 
			
			// store bond lengths: j length is for bond opposite j vert
			for (int j=0;j<3;j++) {
				bondLengths[j]=inRad(packData,face);
				int opface=packData.face_opposite(packData.faces[face],verts[j]);
				if (opface<0) // bdry edge
					bondLengths[j] *= 2.0;
				else
					bondLengths[j] +=inRad(packData,opface);
			} // done with bond length
		}

		/**
		 * Each bond angle is the complement of a vertex angle in the face,
		 * so their sum should always be 2pi.
		 * @return double
		 */
		public double carbonAngleSum() {
			return (bondAngles[0]+bondAngles[1]+bondAngles[2]);
		}
		
		/**
		 * The carbon energy is the sum of 'angle' energies from bond lengths 
		 * and bond angles.
		 * @return
		 */
		public double carbonEnergy() {
			double energy=0.0;
			for (int j=0;j<3;j++) {
				double term=bondLengths[(j+1)%3]*bondLengths[(j+2)%3]*Math.cos(bondAngles[j])+.5;
				energy +=angleParam*term*term;
			}
			return energy;
		}
		
		/**
		 * If v is a vert, return the associated bond length, namely,
		 * that for dual to edge opposite v. 
		 * @param v
		 * @return 0.0 on error
		 */
		public double getBondLength(int v) {
			int j=packData.faces[face].vertIndx(v);
			if (j>=0) {
				return bondLengths[j];
			}
			return 0.0;
		}

		/**
		 * If v is a vert, return the associated bond energy
		 * @param v
		 * @return 0 on error
		 */
		public double getBondEnergy(int v) {
			int j=packData.faces[face].vertIndx(v);
			if (j>=0) {
				double L=bondLengths[j]*bondLengths[j];
				return (bondParam/4.0)*(L-1.0)*(L-1.0);
			}
			return 0.0;
		}
		
		/**
		 * If v is a vert, return the associated angle energy in
		 * this face.
		 * @param v
		 * @return 0 on error
		 */
		public double getAngleEnergy(int v) {
			int j=packData.faces[face].vertIndx(v);
			if (j>=0) {
				double term=bondLengths[(j+1)%3]*bondLengths[(j+2)%3]*Math.cos(bondAngles[j])+.5;
				return angleParam*term*term;
			}
			return 0.0; 
		}

	}

	/** 
	 * Override method for cataloging command structures
	 */
	public void initCmdStruct() {
		super.initCmdStruct();
		cmdStruct.add(new CmdStruct("adjust","x",null,"adjust interior radii down gradient, factor 'x'"));
		cmdStruct.add(new CmdStruct("flip","f g",null,"flip the edges between successive faces in the list"));
		cmdStruct.add(new CmdStruct("energy",null,null,"Compute energy for current parameters, set colors"));
		cmdStruct.add(new CmdStruct("set_param","[-b {b} -a {a} -g {g} -d]",null,"Set parameters: "+
				"bond, angle, planar, or -d to set all to default."));
		cmdStruct.add(new CmdStruct("bonds","{e ..}",null,"draw the duals to given edges using color ramp based on energy"));
		cmdStruct.add(new CmdStruct("relax","{r}",null,"set all radii to our relaxed length, sqrt(3)/2.0"));
		cmdStruct.add(new CmdStruct("fix",null,null,"repack, layout, display"));
		cmdStruct.add(new CmdStruct("initS","n a1 a2",null,"initiate stitch setting, 'n' generations, angles 'a1' and 'a2' times pi"));
		cmdStruct.add(new CmdStruct("stitch","-[dcCa..] v",null,"stitch at 'v' using given option (default 'd'): "+
				"d= identify nghbs, c/C = close with edge, pole left/right, a = 1 new circle nghb."));
		cmdStruct.add(new CmdStruct("export","-[lrb] {p}",null,"export left/right/base packing to packing p"));
		cmdStruct.add(new CmdStruct("listStitches",null,null,"See 'Message' window for current list of stitch specifications"));
		cmdStruct.add(new CmdStruct("undo","[n]",null,"Undo the last n stitches, stopping "+
				"at initial paste (or all down to initial paste if n less than 0)"));
//		cmdStruct.add(new CmdStruct("mvPole","w",null,"Move the pole to a bdry neighbor 'w'."));
	}
	
}

