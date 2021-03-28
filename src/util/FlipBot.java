package util;

import java.awt.Color;
import java.util.Iterator;
import java.util.Random;
import java.util.Vector;

import komplex.EdgeSimple;
import packing.PackData;
import exceptions.CombException;
import exceptions.ParserException;

public class FlipBot {
	
//	static final int FLIP_NEVER=01;
//	static final int FLIP_ALWAYS=02;
//	static final int FLIP_RANDOM=03;
//	static final int FLIP_HHLEFT=04;
//	static final int FLIP_HHRIGHT=05;
//	static final int FLIP_BIG_ENERGY=06;
//	static final int FLIP_GEO=07;
	
//	static final int MOVE_ALWAYS=01;
//	static final int MOVE_NEVER=02;
//	static final int MOVE_ON_FLIP=03;
//	static final int MOVE_RIGHT=04;
//	static final int MOVE_LEFT=05;
//	static final int MOVE_RANDOM=06;
	
	PackData p;
	String myName;
	int homeVert; // vertex on which this bot sits
	int previousHome; // previous homeVert
	EdgeSimple lastFlipped; // last edge this flipbot flipped (may no longer be edge)
	int otherEnd; // if edge is flipped, this holds other end from the original home vertex;
				  // some strategies may need this, but generally it will be zero if no flip
	Color color;  // a distinguishing color
	int homeDegree;
	int bdryFlag;
	int []petalDegrees; // degrees of petals, order as with flower, set in 'update'
	int []edgeEnergies; // for edge, add degrees of end, subtract degrees
						//    of common neighbor(s).
	int []candidates;  // candidates for next flip, generally make random choice among these
	int holdVert; // first in string of non-flipped
	int noflip_count; // keep track of failed flip attempts.
	
	String flipStrategy; // encode decision procedure for doing a flip
	String moveStrategy; // encode decision procedure for moving after a flip
	
	// Constructors
	public FlipBot(PackData pd,String name) {
		p=pd;
		myName=new String(name);
		bdryFlag=p.getBdryFlag(homeVert);
		previousHome=0; // no history at start
		lastFlipped=null;
		flipStrategy="default";
		moveStrategy="default";
		holdVert=-1;
		color=new Color(0,0,250); // default to blue
		otherEnd=0;
		noflip_count=0;
	}
	
	public FlipBot(PackData pd,int v,String name) {
		this(pd,name);
		setHomeVert(v);
	}
	
	/* ======================================================================
	 *                     here are the two key routines 
	 * ================================================================= */
	/**
	 * This simply chooses the edge flip to carry out based on 
	 * currently established 'flipStrategy'.
	 * @return EdgeSimple, 'null' if there is to be no flip, (-1,-1) on error.
	 */
	public EdgeSimple chooseFlip() {
		// "default":
		//   * if deg <= 6, just move
		//   * else choose randomly among largest edge energy if >= 2
		noflip_count++;
		if (flipStrategy.toLowerCase().startsWith("default")) {
			if (homeDegree<=6)
				return null;
			
			// find the vector of petal indices for the biggest energy
			Vector<Integer> biggest=bigEnergyEdges();
			if (biggest.size()==0 || edgeEnergies[biggest.get(0)]<2)
				return null;
			int J=biggest.get(0);
			
			// choose a random one among the biggest
			if (biggest.size()>1) {
				J=biggest.get(new Random().nextInt(biggest.size()));
			}
			int V=p.kData[homeVert].flower[J];
			
			// return this edge for flipping
			noflip_count=0;
			return new EdgeSimple(homeVert,V);
		}
		if (flipStrategy.toLowerCase().startsWith("hhl")) {
			int num=p.countFaces(homeVert);
			if (num<7) { // just move
				return null;
			}
			int pre=p.nghb(homeVert, previousHome);
			if (pre<0) { // if problem, choose random petal index
				Random rand=new Random();
				pre=p.kData[homeVert].flower[rand.nextInt(num)];
			}
			int j=(pre-4+num)%num;
			
			// return edge between hex axis and next vert to right
			// TODO: careful, don't flip (home,previous)
			noflip_count=0;
			return new EdgeSimple(homeVert,p.kData[homeVert].flower[j]);
		}
		
		// Ian Francis idea (6/2014)
		if (flipStrategy.toLowerCase().startsWith("if")) {
			
			// first choice: largest with energy >= 3
			Vector<Integer> candidates=getCandidates(3);
			if (candidates==null) {
				// second choice: 'homeVert' and a petal have deg 7,
				//   commond nghbs degree 6.
				candidates=getCandidates(1);
				if (candidates==null) {
					// third choice: 'homVert' deg 7, nghb degree 6,
					//   and common nghbs degree 6 and 5.
					candidates=getCandidates(2);
					if (candidates==null) {
						// third choice: 'homVert' deg 6, nghb degree 6,
						//   and common nghbs degree 6 and 4.
						candidates=getCandidates(5);
					}
				}
			}
			if (candidates!=null) {
				int sz=candidates.size();
				Random rand=new Random();
				int j=rand.nextInt(sz);
				noflip_count=0;
				return new EdgeSimple(homeVert,candidates.get(j));
			}
			return null; // didn't get any suitable candidates
		}
		
		return null;
	}

	/**
	 * This chooses the vertex the bot is to move to based on 
	 * 'moveStrategy'; the calling routine carries out the move. 
	 * If 'edge' is not null, it is the new edge encoded as
	 * (v_left, v_right) relative to the directed edge that was
	 * flipped. (Usually, if homeVert was one end of old edge,
	 * this is (v_left, v_right) as seen from the 'homeVert'.) 
	 *  
	 * The combinatorics from the flip should already have been 
	 * adjusted.
	 * 
	 * @param int home, home vertex (before flip)
	 * @param int previous, previous vertex (before flip)
	 * @param newEdge EdgeSimple, new edge (v_left,v_right) or null
	 * @return int, 0 if there is to be no move, -1 on error, else
	 * return new 'homeVert', 0 if no move.
	 */
	public int chooseMove(int home, int previous, EdgeSimple newEdge) {
		
		if (moveStrategy.toLowerCase().startsWith("default")) {
			
			// there was a flip
			if (newEdge!=null) {
				
				// find end's degrees (former left and right neighbors', resp.)
				int deg_left=p.countFaces(newEdge.v)+p.getBdryFlag(newEdge.v);
				int deg_right=p.countFaces(newEdge.w)+p.getBdryFlag(newEdge.w);
				
				// if we move, which way is best?
				int best=newEdge.v;
				if (deg_right>deg_left)
					best=newEdge.w;
				else if (deg_left==deg_right && new Random().nextBoolean()) // flip coin
					best=newEdge.w;

				// don't move unless degree is higher than homeDegree
				if (homeDegree>=(p.countFaces(best)+p.getBdryFlag(best)))
					return 0;
				
				// okay, this is the way to move
				return best;
			}

			// if no flip, move to neighbor with larger degree
			Vector<Integer> bigpets=getCandidates(0);
			if (bigpets==null || bigpets.size()==0)
				return 0; // some error occurred
			int deg=bigpets.get(0);
			if (petalDegrees[deg]>=homeDegree) {
				int n=new Random().nextInt(bigpets.size());
				return p.kData[homeVert].flower[n];
			}
			return 0;
		}
		
		if (moveStrategy.toLowerCase().startsWith("hhl")) {
			int num=p.countFaces(home);
			int pre=p.nghb(home, previous);
			if (pre>=0)
				return p.kData[homeVert].flower[(pre-3+num)%num];
			else
				throw new CombException("in move, 'home' and 'previous' not neighbors");
		}
		
		// Ian Francis idea, 6/2014: if there's been a flip, move to 'otherEnd' (the
		//   original opposite end of the flipped edge). Else, move to neighbor across
		//   highest energy edge.
		if (moveStrategy.toLowerCase().startsWith("if")) {
		
			if (otherEnd>0 && otherEnd!=homeVert)
				return otherEnd;
			Vector<Integer> cand=getCandidates(4); // largest energy, even if negative
			int n=0;
			if (cand!=null && cand.size()>0) {
				n=new Random().nextInt(cand.size());
				return cand.get(n);
			}
			n=new Random().nextInt(p.countFaces(homeVert));
			return p.kData[homeVert].flower[n];
		}
			
		return 0;
	}
	
	/**
	 * Find candidate nghb vertices for edge flips from homeVert based on 'mode'. 
	 * Have to make up these codes as we need them:
	 * -1 ==> all petals; 
	 * 0 ==> largest degree; 
	 * 1 ==> Ian's exception 1; 
	 * 2 ==> Ian's exception 2; 
	 * 3 ==> energy >= 3; 
	 * 4 ==> largest energy (whether pos/neg/zero)
	 * 5 ==> deg 6 with deg 4 and 6 nghbs.
	 * @param mode int
	 * @return Vector<Integer>, null if empty
	 */
	public Vector<Integer> getCandidates(int mode) {
		int tick=0;
		
		// all petal verts
		if (mode==-1) {
			Vector<Integer> ptls=new Vector<Integer>(0);
			for (int j=0;j<homeDegree+bdryFlag;j++) {
				ptls.add(p.kData[homeVert].flower[j]);
			}
			return ptls;
		}
			
		// petal verts having largest degree.
		//  Caution: add 1 if bdryFlag is set, so result is not
		//  always the same data as 'petalDegrees'.
		if (mode==0) {
			int big=0;
			for (int j=0;j<=homeDegree;j++) {
				int k=p.kData[homeVert].flower[j];
				int nb=p.countFaces(k)+p.getBdryFlag(k);
				big=(nb>big) ? nb : big; 
			}
			
			Vector<Integer> bigpets=new Vector<Integer>(0);
			for (int j=0;j<=homeDegree;j++) {
				int k=p.kData[homeVert].flower[j];
				int nb=p.countFaces(k)+p.getBdryFlag(k);
				if (nb==big)
					bigpets.add(p.kData[homeVert].flower[j]);
			}
			return bigpets;		
		}
		
		// IanFrancis exception 1: homeVert have degree 7, find petals
		//   of deg 7 with petals on each side of deg 6.
		if (mode==1) { 
			if (homeDegree!=7)
				return null;
			Vector<Integer> ans=new Vector<Integer>();
			for (int j=1;j<=(homeDegree-bdryFlag);j++) {
				int lpd=petalDegrees[(j+1)%homeDegree];
				int rpd=petalDegrees[j-1];
				if (petalDegrees[j]==7 && lpd==6 && rpd==6) {
					ans.add(p.kData[homeVert].flower[j]);
					tick++;
				}
			}
			if (tick==0)
				return null;
			return ans;
		}
		
		// IanFrancis exception2: homeVert degree 7, find petals
		//   of deg 6 with neighbors deg 5 and deg 6.
		if (mode==2) {
			if (homeDegree!=7)
				return null;
			Vector<Integer> ans=new Vector<Integer>();
			for (int j=1;j<=(homeDegree-bdryFlag);j++) {
				int lpd=petalDegrees[(j+1)%homeDegree];
				int rpd=petalDegrees[j-1];
				if (petalDegrees[j]==6 && ((lpd==5 && rpd==6) || (lpd==6 && rpd==5))) {
					ans.add(p.kData[homeVert].flower[j]);
					tick++;
				}
			}
			if (tick==0)
				return null;
			return ans;
		}
		
		// IanFrancis exception3: homeVert degree 6, find petals of deg 6 
		//   with neighbors deg 4 and deg 6.
		if (mode==5) {
			if (homeDegree!=6)
				return null;
			Vector<Integer> ans=new Vector<Integer>();
			for (int j=1;j<=(homeDegree-bdryFlag);j++) {
				int lpd=petalDegrees[(j+1)%homeDegree];
				int rpd=petalDegrees[j-1];
				if (petalDegrees[j]==6 && ((lpd==4 && rpd==6) || (lpd==6 && rpd==4))) {
					ans.add(p.kData[homeVert].flower[j]);
					tick++;
				}
			}
			if (tick==0)
				return null;
			return ans;
		}
		
		// largest energies >= 3
		if (mode==3) {
			Vector<Integer> biggest=bigEnergyEdges(); // always non-empty
			int j=biggest.get(0);
			if (edgeEnergies[j]<3) // none qualify
				return null;
			
			// convert to vertex indices
			Vector<Integer> verts=new Vector<Integer>();
			Iterator<Integer> bgv=biggest.iterator();
			while (bgv.hasNext())
				verts.add(p.kData[homeVert].flower[bgv.next()]);
			return verts;
		}
		
		// largest energies, pos/neg/zero
		if (mode==4) {
			Vector<Integer> biggest=bigEnergyEdges(); // always non-empty

			// convert to vertex indices
			Vector<Integer> verts=new Vector<Integer>();
			Iterator<Integer> bgv=biggest.iterator();
			while (bgv.hasNext())
				verts.add(p.kData[homeVert].flower[bgv.next()]);
			return verts;
		}
		
		throw new ParserException("'getCandidates': no valid mode given");
	}
	
	/**
	 * Assume 'p' itself has already been updated for any combinatorial changes.
	 * This updates the 'FlipBot's locally maintained data: degrees, energy, etc.
	 * @return int, 1 on success
	 */
	public int update() {
		
		// am I on the boundary? This is 0 (no) or 1 (yes)
		bdryFlag=p.getBdryFlag(homeVert);

		// my degree? ('num' faces; add 1 if a bdry vertex)
		homeDegree=p.countFaces(homeVert)+bdryFlag;
		
		// my petals' degrees? 
		petalDegrees=new int[p.countFaces(homeVert)+1];
		for (int j=0;j<=p.countFaces(homeVert);j++) {
			int k=p.kData[homeVert].flower[j];
			petalDegrees[j]=p.countFaces(k)+p.getBdryFlag(k);
		}

		// petal 'energies'? (interior edges only) 
		edgeEnergies=new int[p.countFaces(homeVert)+1];
		edgeEnergies[0]=edgeEnergies[homeDegree]=0;
		for (int j=1;j<p.countFaces(homeVert);j++) {
			edgeEnergies[j]=
				homeDegree+petalDegrees[j]-(petalDegrees[j-1]+petalDegrees[j+1]);
		}
		if (bdryFlag==0) { // if closed, initial edge also
			edgeEnergies[0]=edgeEnergies[homeDegree]=
				homeDegree+petalDegrees[0]-(petalDegrees[1]+petalDegrees[homeDegree]);
		}
		return 1;
	}
	
	/**
	 * energy of interior edge (u,v) is (deg(u)+deg(v)) - (deg(a)+deg(b)),
	 * where a and b are the common neighbors. Assume 'edgeEnergies'
	 * has been updated. Return vector of petal indices for edges
	 * having max energy among all edges from 'homeVert'
	 * @return Vector<Integer>; these are petal indices, not vert indices 
	 */
	public Vector<Integer> bigEnergyEdges() {
		int big=-10000;
		for (int j=0;j<=homeDegree;j++) {
			big=(edgeEnergies[j]>big) ? edgeEnergies[j] : big;
		}
		
		Vector<Integer> biggest=new Vector<Integer>(0);
		for (int j=0;j<=homeDegree;j++) {
			if (edgeEnergies[j]==big)
				biggest.add(j);
		}
		return biggest;
	}
	
	/**
	 * Choose a random petal
	 * @return int, vertex index
	 */
	public int moveRandom() {
		return p.kData[homeVert].flower[new Random().nextInt(p.countFaces(homeVert))+1];
	}
	
	/**
	 * Set the vertex this FlipBot is living on. (This is not the
	 * way we usually move a FlipBot to a vertex; generally this
	 * is done by the FlipBot itself.) 
	 * @param v int, new 'homeVert'. 
	 * @param prev int, previous vertex
	 */
	public void putVert(int v,int prev) {
		int hold=homeVert;
		if (v<1 || v>p.nodeCount)
			homeVert=new Random().nextInt(p.nodeCount)+1;
		else homeVert=v;
		previousHome=hold;
		update();
	}
	
	/**
	 * get name of this FlipBot
	 * @return String
	 */
	public String getName() {
		return new String(myName);
	}
	
	public void setName(String name) {
		myName=new String(name);
	}

	/**
	 * Return the current flip strategy string
	 * @return
	 */
	public String getFlipStrategy() {
		return flipStrategy;
	}
	
	/**
	 * Here is where we set the flip strategy to a string
	 * @param strat String, 
	 */
	public void setFlipStrategy(String strat) {
		flipStrategy=new String(strat.toLowerCase());
	}
	/**
	 * Return the current move strategy code
	 * @return
	 */
	public String getMoveStrategy() {
		return moveStrategy;
	}
	
	/**
	 * Here is where we set the move strategy as a string
	 * @param strat String
	 */
	public void setMoveStrategy(String strat) {
		moveStrategy=new String(strat.toLowerCase());
	}
	
	/**
	 * Set 'homeVert' to 'v' or to random vertex if 'v' is
	 * improper
	 * @param v int
	 */
	public void setHomeVert(int v) {
		if (v<1 || v>p.nodeCount)
			homeVert=new Random().nextInt(p.nodeCount)+1;
		else homeVert=v;
		update();
	}
	
	/**
	 * return the current 'homeVert'
	 * @return int
	 */
	public int getHomeVert() {
		return homeVert;
	}
	
	/**
	 * Set 'otherEnd' to the end of edge to flip which is opposite
	 * to 'homeVert'. May be 0 in general.
	 * @param w int
	 */
	public void setOtherEnd(int w) {
		otherEnd=w;
	}
	
	/**
	 * 'otherEnd' will often be set to zero.
	 * @return int
	 */
	public int getOtherEnd() {
		return otherEnd;
	}
	
	/**
	 * get the edge from 'previousHome' to 'homeVert' if it
	 * exists
	 * @return EdgeSimple, null on error
	 */
	public EdgeSimple getEdge() {
		if (previousHome<1 || previousHome>p.nodeCount ||
			  homeVert<1 || homeVert>p.nodeCount || p.nghb(previousHome, homeVert)<0)	
			return null;
		return new EdgeSimple(previousHome,homeVert);
	}
	
	/**
	 * return 'previoiusHome'
	 * @return int
	 */
	public int getPrevious() {
		return previousHome;
	}
	
	/**
	 * set 'previousHome' to 'v' or random vert if 'v' is
	 * improper
	 * @param v int
	 */
	public void setPrevious(int v) {
		if (v<1 || v>p.nodeCount)
			previousHome=new Random().nextInt(p.nodeCount)+1;
		else previousHome=v;
	}
	
	/**
	 * Assign distinguishing color in [232,248]
	 * @param c
	 */
	public void setColor(int c) {
		color=ColorUtil.coLor(232+(c%16));
	}
	
	/**
	 * Retrieve the color assigned to this bot
	 * @return Color
	 */
	public Color getColor() {
		return new Color(color.getRed(),color.getGreen(),color.getBlue());
	}
	
	/**
	 * Store the edge which this bot last flipped
	 * @param edge EdgeSimple
	 */
	public void setLastFlipped(EdgeSimple edge) {
		lastFlipped=new EdgeSimple(edge);
	}
	
	/**
	 * Return 'lastFlipped' if it's a valid edge
	 * @return EdgeSimple, null if not an edge
	 */
	public EdgeSimple getLastFlipped() {
		if (lastFlipped==null || p.nghb(lastFlipped.v,lastFlipped.w)<0) 
			return null;
		return new EdgeSimple(lastFlipped);
	}

}
