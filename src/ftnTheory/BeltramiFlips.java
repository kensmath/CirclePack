package ftnTheory;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Random;
import java.util.Vector;

import komplex.EdgeSimple;
import listManip.EdgeLink;
import packing.PackData;
import packing.PackExtender;
import util.CmdStruct;
import allMains.CirclePack;
import circlePack.PackControl;

import complex.Complex;

import exceptions.DataException;
import exceptions.ParserException;
import geometry.EuclMath;

/**
 * Suppose that it is shown that the maps associated with
 * circle packings of random Delaunay triangulations of a region 
 * do in fact converge to a conformal mapping. We might leverage
 * this into a method for numerically solving Beltrami's equation.
 * 
 * Beltrami's equation in a simply connected region Omega is 
 *     delBar(f)(z) = mu(z)*del(f)(z),
 * where the "Beltrami coefficient" mu(z) is a measurable complex
 * function with sup norm ||mu|| < 1.
 * 
 * There is always a solution f, and it is unique up to post
 * composition with an analytic function. In particular, univalent
 * solutions are quasiconformal maps, with dilatation determined
 * by ||mu||.
 * 
 * Our idea is to build a random Delaunay triangulation in Omega,
 * but with the Delaunay condition skewed based on mu(z). When
 * this is circle packed, the result should be quasiconformal.
 * 
 * We start here with Omega the unit square, then bias the 
 * triangulation as though it were stetched by twice in the x 
 * direction. The bias (not in general expected to be 
 * unambiguously defined) is incorporated via
 * edge flips.
 * 
 * @author kens
 *
 */
public class BeltramiFlips extends PackExtender {
	
	static final double Pix2=Math.PI*2.0;
	Random rand;
	EdgeData []edgeData;
	
	// Constructor
	public BeltramiFlips(PackData p) {
		super(p);
		extensionType="BELTRAMI_FLIP";
		extensionAbbrev="BL";
		toolTip="'BeltramiFlip': random edge flips to adjust "+
			"for quasiconformal dilatation";
		registerXType();
		int rslt;
		try {
			rslt=cpCommand(packData,"geom_to_e");
		} catch(Exception ex) {
			rslt=0;
		}
		if (rslt==0) {
			CirclePack.cpb.errMsg("CA: failed to convert to euclidean");
			running=false;
		}
		if (running) {
			packData.packExtensions.add(this);
			rand=new Random(1); // seed for debugging
		}
		edgeData=null;
	}
	
	public int cmdParser(String cmd, Vector<Vector<String>> flagSegs) {
		Vector<String> items = null;
		
		// -------- set function ------------
		// currently use Function frame, but may want more generality
		if (cmd.startsWith("func")) {
			
		}
		
		// ---------- color_e ----------
		if (cmd.startsWith("color_e")) {
			double []iln=sortEdges();
			CirclePack.cpb.msg(iln[0]+" illegal edges, norm "+iln[1]);
			
			// draw colored edges
			for (int j=0;j<edgeData.length;j++) {
				if (edgeData[j].color>100) 
					cpCommand("disp -ec"+edgeData[j].color+" "+
						edgeData[j].edge.v+" "+edgeData[j].edge.w);
			}
			return edgeData.length;
		}
		
		// -------- try_flip -----------
		if (cmd.startsWith("try_fl")) {
			int count=0;
			if (flagSegs!=null && flagSegs.size()>0) {
				items=flagSegs.get(0);
				EdgeLink elist=new EdgeLink(packData,items);
				if (elist!=null && elist.size()>0) {
					Iterator<EdgeSimple> elst=elist.iterator();
					while(elst.hasNext()) {
						EdgeSimple edge=elst.next();
						double x=getLegality(edge.v,edge.w);
						if (x>0 && flip2Legal(edge.v,edge.w)>0) {
							msg("flip <"+edge.v+" "+edge.w+">, legality "+x);
							count++;
						}
					} // end of while
				}
			}
			return count;
		}
		
		// --------- go ---------------
		
		if (cmd.startsWith("go")) {
			int n;
			try {
				items = flagSegs.get(0);
				n = Integer.parseInt((String) items.get(0));
			} catch (Exception ex) {
				n = 1;
			}
			int count=0;
			try {
				count=goOrder(n);
				msg("go: "+count+" flips");
			} catch (Exception ex) {
				throw new ParserException("Problem with 'goOrder'");
			}
			if (count>0) {
				packData.facedraworder(false);
				// resort the edges
				double []iln=sortEdges();
				CirclePack.cpb.msg(iln[0]+" illegal edges, norm "+iln[1]);
			}
			return count;
		}
		return super.cmdParser(cmd, flagSegs);
	}
	
	/**
	 * Compute and enter illegality values in 'edgeData', then
	 * sort. 
	 * Caution: edges can be invalidated when flips are done
	 * @return, double[2]: number of positive illegalities, L2 norm
	 */
	public double []sortEdges() {
		EdgeLink elist=new EdgeLink(packData,"a"); 
		double l2error=0.0;
		int count=0;
		edgeData =new EdgeData[elist.size()];
		Iterator<EdgeSimple> elst=elist.iterator();

		int hits=0;
		while (elst.hasNext()) {
			EdgeSimple edge=elst.next();
			double x=0.0;
			try {
				x=getLegality(edge.v,edge.w);
			} catch(Exception ex) {
				System.err.println("v w "+edge.v+" "+edge.w);
			}
			if (x>0) {
				l2error += x*x;
				count++;
			}
			edgeData[hits++]=new EdgeData(edge,x,100);
		}
		
		// sort
	    Comparator<EdgeData> EDComparator = new EDComparator();
		Arrays.sort(edgeData,EDComparator);
		
		// set color ramp
		double mx=0.0;
		double mn=0.0;
		for (int j=0;j<edgeData.length;j++) {
			mx = (edgeData[j].illegality>mx) ? edgeData[j].illegality : mx;
			mn = (edgeData[j].illegality<mn) ? edgeData[j].illegality : mn;
		}
		for (int j=0;j<edgeData.length;j++) {
			if (edgeData[j].illegality>0)
				edgeData[j].color=(int)(100.0+95.0*(edgeData[j].illegality/mx));
			else if (edgeData[j].illegality<0)
				edgeData[j].color=(int)(100.0-95.0*(edgeData[j].illegality/mn));
		}
				
		double []ans=new double[2];
		ans[0]=(double)count;
		ans[1]=Math.sqrt(l2error/packData.nodeCount);
		return ans;
	}
	
	/**
	 * Consider N random edge flips.
	 * @param N
	 */
	public int gogo(int N) {
		int count=0;
		int v=1;
		int w=1;
		int j=0;
		int num=0;
		int flipCount=0;
		int node=packData.nodeCount;
		while (count<N) {
			// random vert, random petal
			v=rand.nextInt(node)+1;
			num=packData.kData[v].num;
			// degree must be >3 for interior, >2 for bdry
			if ((num+packData.kData[v].bdryFlag)>3) {
				// random petal
				if (packData.kData[v].bdryFlag!=0) // boundary v   
					j=rand.nextInt(num-1)+1;
				else  
					j=rand.nextInt(num);
				w=packData.kData[v].flower[j];
				flipCount += flip2Legal(v,w);
			}
			count++;
		}
		return flipCount;
	}

	/**
	 * Consider N flips in sorted 'edgeData' order
	 * @param N
	 */
	public int goOrder(int N) {
		int count=0;
		int flipCount=0;
		if (edgeData==null) sortEdges();
		N = (edgeData.length<N) ? edgeData.length : N;
		int tick=0;
		while (count<N && tick<packData.nodeCount &&
				edgeData[tick].illegality>0) {
			int v=edgeData[tick].edge.v;
			int w=edgeData[tick].edge.w;
			if (packData.flipable(v,w)) {
				flipCount += flip2Legal(v,w);
			}
			count++;
			tick++;
		}
		return flipCount;
	}
	
	/**
	 * Compute 'legality' value; namely, for quad {v,r,w.l}
	 * for edge {v,w}, return 
	 * x = log[(ang(l)+ang(r))/(ang(v)+ang(w))].
	 * If x > 0, then edge can and should be flipped and x
	 * reflects how 'illegal' the edge is.
	 * @param v
	 * @param w
	 * @return double
	 */
	public double getLegality(int v,int w) {
		double []angs=getQuadAngles(v,w);
		if (angs==null) return 0; // e.g., bdry edge
		return Math.log((angs[1]+angs[3])/(angs[0]+angs[2]));
	}
	
	/**
	 * Get the 4 angles for the quad determined by edge {v,w} 
	 * after locations are adjusted by affine map associated 
	 * to Beltrami coeff. NOTE: use coeff for the centroid of 
	 * the four vertices.
	 * @param v
	 * @param w
	 * @return double[4]: v, r, w, l angles
	 */
	public double []getQuadAngles(int v,int w) {
		if ((packData.kData[v].bdryFlag==1 && packData.kData[w].bdryFlag==1) ||
				packData.nghb(v,w)<0)
			return null;
		int []corn_vert=new int[4];

		// set up the four vertices about this edge 
		int vw=packData.nghb(v,w);
		int num=packData.kData[v].num;
		int j=(vw-1+num)%num;
		int k=(vw+1)%num;
		
		corn_vert[0]=v;
		corn_vert[1]=packData.kData[v].flower[j];
		corn_vert[2]=w;
		corn_vert[3]=packData.kData[v].flower[k];

		// affine map based on Beltrami coeff at centroid of these 4 verts  
		Complex midZ=packData.getCenter(v).add(packData.getCenter(corn_vert[1])).
		add(packData.getCenter(w)).add(packData.getCenter(corn_vert[3]));
		midZ=midZ.times(0.25);

		// get 2x2 real matrix
		double []affine=getAffine(getCoefficient(midZ));
		Complex []corner=new Complex[4];
		for (int i=0;i<4;i++) {
			Complex Z=packData.getCenter(corn_vert[i]);
			// apply affine
			double x=affine[0]*Z.x+affine[1]*Z.y;
			double y=affine[2]*Z.x+affine[3]*Z.y;
			corner[i]=new Complex(x,y);
		}

		return EuclMath.QuadAngles(corner[0],corner[1],corner[2],corner[3]);
	}
	
	/**
	 * If edge {v,w} can and should be flipped, then flip it.
	 * @param v
	 * @param w
	 * @return 0 if bdry edge, failed to flip, or flip not warranted
	 */
	public int flip2Legal(int v,int w) {
		if ((packData.kData[v].bdryFlag==1 && packData.kData[w].bdryFlag==1) ||
				packData.nghb(v,w)<0)
			return 0;
		double x=getLegality(v,w);
		if (x>0) {
			if (packData.flip_edge(v,w,2)!=0) {
				// fix up combinatorics
				packData.complex_count(false);
				return 1;
			}
			else 
				return 0;
		} 
		return 0;
	}
	
	/**
	 * Compute the affine map associated with Beltrami coeff z.
	 * If z=u+iv, then for [ a b;c d] have a=(1+u)/2, d=(1-u)/2, 
	 * and b=c=v/2. (In theory, want |z|<1 to preserve orientation, 
	 * but that is irrelevant as to whether flip is legal. However,
	 * |z|=1 gives a singular affine map.)  
	 * E.g., for [a 0;0 1] (stretch in x direction by a>0),
	 * have u=(a-1)/(a+1); note, u>0 for a>1, u<0 for 0<a<1.
	 * @param z Complex, Beltrami coeff
	 * @return double[4] representing affine transform. 
	 * 
	 * TODO: figure out how to compute this.
	 * NOTE: For testing, just do 'factor' in x direction 
	 */
	public double []getAffine(Complex z) {
		if (z.abs()>=1.0) 
			throw new DataException("Beltrami: |z| must be < 1");
		double []affine=new double[4];
		affine[0]=(1+z.x)/2.0;
		affine[3]=(1-z.x)/2.0;
		affine[1]=affine[2]=z.y/2.0;
		return affine;
	}
	
	/**
	 * Specify Beltrami coefficient at point z. Currently,
	 * testing various hard coded examples. Typically, want
	 * to specify function in "Function Panel", though it
	 * will often involve real/imaginary parts.
	 * Examples: to get affine map [a 0;0 1], put in z=(a-1)/(a+1).
	 * @param z Complex
	 * @return Complex, Beltrami coeff
	 * 
	 * TODO: want to specify more general functions
	 */
	public Complex getCoefficient(Complex z) {
		try {
			Complex ans=PackControl.functionPanel.getFtnValue(z);
			return ans;
		} catch (Exception ex) {
			System.err.println("error in 'getFtnValue'");
			return new Complex(z);
		}
		
		// stretch a=.75 in x direction (should get wide/short)
//		return (new Complex(-.25,0.0));
		
		// cos curve on -1, 1; return stretch factor 1/2 to 2 to y coord
//		return new Complex(Math.exp(Math.log(2)*Math.cos(Pix2*(z.x))),0.0);
	}
	
	public void initCmdStruct() {
		super.initCmdStruct();
		cmdStruct.add(new CmdStruct("gogo","{n}",null,"do n edge flips"));
		cmdStruct.add(new CmdStruct("pick_e",null,null,"get random edge, determine if should be flipped"));
		cmdStruct.add(new CmdStruct("try_flip","v w",null,"Do a flip if legal, report"));
		cmdStruct.add(new CmdStruct("color_e",null,null,"Color edge, redder means more illegal; return L2 error"));
	}
	
}

class EdgeData {
	EdgeSimple edge;
	double illegality; // if positive, edge should be flipped  
	int color;
    
	public EdgeData(EdgeSimple es,double illy,int col) {
		edge=es;
		illegality=illy;
		color=col;
	}
	
}

class EDComparator implements Comparator<EdgeData> {

    // Comparator interface requires defining compare method.
    public int compare(EdgeData ed1,EdgeData ed2) {
    	if (ed1.illegality>ed2.illegality)
    		return -1;
    	if (ed1.illegality<ed2.illegality)
    		return 1;
    	return 0;
    }

}
