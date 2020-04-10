package random;

import complex.Complex;
import exceptions.ParserException;
import geometry.EuclMath;
import geometry.CircleSimple;
import packing.PackData;

public class Conductance {
	
	/** 
	 * Save adjacency matrix of packing (<= 10,000 verts) in form for
	 * matlab to read; the adjacency matrix has a 1 in (i,j) spot if
	 * i and j are neighboring vertices, else a 0. 

	 * If 'tm' is true, divide every row by sum of its entries and set
	 * the rows for boundary vertices to zero: this yields the 
	 * "transition" matrix for the "simple" (ie. equal probability) 
	 * random walk on the edge-graph of the packing with absorbing
	 * boundary. Also add an ordered list of the boundary vertices to
	 * the matlab file.
	 * @param p 'PackData'
	 * @param stdt boolean; yes, then 
	 * @return adjacency matrix
	*/
	public static double [][]adjacencyMatrix(PackData p,boolean stdt) {
		double [][]admat=new double[p.nodeCount+1][p.nodeCount+1];
		for (int i=1;i<=p.nodeCount;i++) {
			for (int j=0;j<(p.kData[i].num+p.kData[i].bdryFlag);j++) {
				int k=p.kData[i].flower[j];
				if (k<i) {
					admat[i][k]=1;
					admat[k][i]=1;
				}
			}
		}
		return admat;
	}
	
	/**
	 * Conductances for simple random walk are all 1.
	 * @param domData
	 * @return double[v][j], v=vertex, j=petal index (first is repeated for
	 * closed flowers)
	 */
	public static double [][]setSimpleConductances(PackData domData) {
		double [][]conductance=new double[domData.nodeCount+1][];
		for (int v=1;v<=domData.nodeCount;v++) {
			int num=domData.kData[v].num;
			conductance[v]=new double[num+1];
			for (int j=0;j<num;j++)
				conductance[v][j]=1.0;
		}
		return conductance;
	}
				
	/**
	 * Compute conductances of the triangulation based on packing centers. 
	 * If 'domData' is a packing, this is usual set of eucl conductances 
	 * (a la Dubejko). However, in general, use ratio of 
	 * distance between incircle centers of bounding faces to length of
	 * their common edge. (For bdry edges, use inRadius/edgelength.)
	 * (Note: elsewhere, compute transition probability from v to u by 
	 * dividing edge <v,u> conductance by sum of conductances of all 
	 * edges from v.) 
	 * @param domData
	 * @return double[v][j], v=vertex, j=petal index (first is repeated for
	 * closed flowers)
	 */
	public static double [][]setConductances(PackData domData) {
		if (domData==null || domData.nodeCount<=0 || domData.hes!=0 
				|| domData.status==false) {
			throw new ParserException("packing not set or not suitable");
		}
		double []spokes=null;
		Complex []inCenters=null;
		double [][]conductance=new double[domData.nodeCount+1][];
		Complex f1=null;
		Complex f2=null;
		for (int v=1;v<=domData.nodeCount;v++) {
			int num=domData.kData[v].num;
			Complex z=domData.rData[v].center;
			spokes=new double[num+1];
			inCenters=new Complex[num];

			conductance[v]=new double[num+1];

			// store edge lengths, incenters
			f2=domData.rData[domData.kData[v].flower[0]].center;
			spokes[0]=z.minus(f2).abs();
			CircleSimple sc=null;
			for (int j=1;j<=num;j++) {
				f1=f2;
				f2=domData.rData[domData.kData[v].flower[j]].center;
				sc=EuclMath.eucl_tri_incircle(z,f1,f2);
				spokes[j]=z.minus(f2).abs();
				inCenters[j-1]=sc.center;
			}

			// store conductances
			
			// for bdry, use ratio of inRad/length for first and last edges
			if (domData.kData[v].bdryFlag!=0) {
				f1=domData.rData[domData.kData[v].flower[0]].center;
				f2=domData.rData[domData.kData[v].flower[1]].center;
				double inRad=EuclMath.eucl_tri_inradius(spokes[0],spokes[1],f1.minus(f2).abs());
				conductance[v][0]=inRad/spokes[0];
				f1=domData.rData[domData.kData[v].flower[num-1]].center;
				f2=domData.rData[domData.kData[v].flower[num]].center;
				inRad=EuclMath.eucl_tri_inradius(spokes[num-1],spokes[num],f1.minus(f2).abs());
				conductance[v][num]=inRad/spokes[num];
			}
			else { // interior: first conductance repeated in last
				conductance[v][0]=
					conductance[v][num]=inCenters[num-1].minus(inCenters[0]).abs()/spokes[0];
			}
			
			// now the rest
			for (int j=1;j<num;j++) {
				conductance[v][j]=inCenters[j-1].minus(inCenters[j]).abs()/spokes[j];
			}
		} // end of loop on v
		return conductance;
	}
	
}
