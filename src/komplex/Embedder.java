package komplex;

import java.util.Iterator;

import listManip.NodeLink;
import listManip.VertexMap;
import packing.PackData;
import exceptions.DataException;

/**
 * This class contains static methods to embed --- more accurately, to
 * intersect --- to the extent possible, one circle packing complex p1 with 
 * another p2, starting with designated contiguous vertex pairs in each.
 * The principal routine returns a VertexMap giving <v,w> pairs, where v
 * is a vertex in p1, w is its corresponding vertex in p2 under the embedding.  
 * The result is not necessarily unique --- it depends on how the search
 * progresses --- and the embedded result may not be a complex, e.g., it
 * may have dangling edges, incomplete flowers, etc.
 * NOTE: This is NOT a sophisticated routine!
 * @author kens
 *
 */
public class Embedder {

	public static PackData p1;
	public static PackData p2;
	public static int []vstat;
	public static int []Vstat;
	
	public static VertexMap embed(PackData p,PackData q,int a,int b,int A,int B) {
		NodeLink news=null;
		int indv=0;
		int indV=0;
		p1=p;
		p2=q;
		if (!p1.status || !p2.status || a<1 || A<1 || b<1 || B<1 ||
				a>p1.nodeCount || A>p2.nodeCount || b>p1.nodeCount || B>p2.nodeCount
				|| (indv=p1.nghb(a,b))<0 || (indV=p2.nghb(A,B))<0
				|| !embedable(p1,p2,a,indv,A,indV)) {
			throw new DataException("embed: initial data not suitable for embedding");
		}
		vstat=new int[p1.nodeCount+1];
		Vstat=new int[p2.nodeCount+1];
		NodeLink big=new NodeLink(p1,a);
		big.add(b);
		
		// initial matches
		vstat[a]=A;Vstat[A]=a;
		vstat[b]=B;Vstat[B]=b;

		// entering main loop: keep two lists, 'big' and 'news'.
		// provisional matches indicated using negative of index
		int v,V;
		int []result;
		int k,K;
		int jspot,Jspot;
		int num,Num;
		while (big!=null && big.size()>0) {
			news=new NodeLink(p1);
			Iterator<Integer> bigtr=big.iterator();
			while (bigtr.hasNext()) {
				v=(Integer)bigtr.next();
				V=Math.abs(vstat[v]);
				try {
					result=consistent(p1,p2,v,V,-1);
				} catch (Exception ex) {
					result=null;
				}
				if (result!=null) { // okay
					// finalize this match
					vstat[v]=V;
					Vstat[V]=v;
					num=p1.countFaces(v);
					int[] flower1=p1.getFlower(v);
					Num=p2.countFaces(V);
					int[] flower2=p2.getFlower(V);
					jspot=result[0];
					Jspot=result[1];
						
					// Now, make new matches and put in 'news'
					
					// v/V both interior (hence num=Num)
					if (!p1.isBdry(v) && !p2.isBdry(V)) { 
						for (int j=1;j<p1.countFaces(v);j++) {
							k=flower1[(jspot+j)%num];
							K=flower2[(Jspot+j)%Num];
							if (vstat[k]==0) { // not yet matched
								news.add(k); // want to revisit this
								
								// provisional match: negative
								vstat[k]=-K;
								Vstat[K]=-k;
							}
						}
					}
					
					// v interior, V bdry (hence num>Num)
					else if (!p1.isBdry(v))  {
						for (int j=0;j<Num;j++) {
							K=flower2[j];
							int kspot=(jspot-Jspot+2*num)%num;
							k=flower1[(kspot+j)%num];
							if (vstat[k]==0) { // not yet matched
								news.add(k); // want to revisit this
								
								// provisional match: negative
								vstat[k]=-K;
								Vstat[K]=-k;
							}
						}

					}
					
					// V interior, v bdry (hence Num>num)
					else if (!p2.isBdry(V)) {
						for (int j=0;j<num;j++) {
							k=flower1[j];
							int Kspot=(Jspot-jspot+2*Num)%Num;
							K=flower2[(Kspot+j)%Num];
							if (vstat[k]==0) { // not yet matched
								news.add(k); // want to revisit this
								
								// provisional match: negative
								vstat[k]=-K;
								Vstat[K]=-k;
							}
						}
					}
					
					// both v and V are bdry; find range of indices of v to check
					else {
						int jmin=jspot;
						jmin= (Jspot<jmin) ? Jspot:jmin;
						int jmax=num-jspot;
						jmax= ((Num-Jspot)<jmax) ? (Num-Jspot) : jmax;
						for (int j=-jmin;j<=jmax;j++) {
							k=flower1[jspot+j];
							K=flower2[Jspot+j];
							if (vstat[k]==0) { // not yet matched
								news.add(k); // want to revisit this
							
								// provisional match: negative
								vstat[k]=-K;
								Vstat[K]=-k;
							}
						}
					}
				}
			} // end of inner while
			if (news!=null && news.size()>0)
				big=news;
			else big=null;
		} // end of outer while
		
		VertexMap finalMap=new VertexMap();
		for (int j=1;j<=p1.nodeCount;j++) {
			if (vstat[j]>0)
				finalMap.add(new EdgeSimple(j,vstat[j]));
		}
		if (finalMap.size()<=0) return null;
		return finalMap;
	}
	
	/** 
	 * Determine if vertex v of q1 is embedable as V of q2, wrt 
	 * indices j, J, respectively. If v and V are both interior, then
	 * they must have the same 'num' of petals. If one is bdry and one
	 * interior, then interior must have at least one more petal than bdry.
	 * NOTE: should be symmetric wrt interchanging q1 and q2.
	 * @param q1
	 * @param q2
	 * @param v
	 * @param j
	 * @param V
	 * @param J
	 * @return
	 */
	public static boolean embedable(PackData q1,PackData q2,int v,int j,int V,int J) {
		if (v<1 || v>q1.nodeCount || V<1 || V>q2.nodeCount) // improper vertices 
			return false;
		int num=q1.countFaces(v);
		int Num=q2.countFaces(V);
		if (j<0 || j>num || J<0 || J>Num) // improper indices
			return false;
		if ((!q1.isBdry(v) && !q2.isBdry(V) && num!=Num)
				|| (!q1.isBdry(v) && q2.isBdry(V) && num<Num+1)
				|| (q1.isBdry(v) && !q2.isBdry(V) && Num<num+1))
			return false;
		return true;
	}
	
	/**
	 * Check consistency of matches among petals of v and V as given
	 * in vstat and Vstat. First, find one consistent match, then check 
	 * others. If there is no first match, throw exception.
	 * @param q1
	 * @param q2
	 * @param v
	 * @param V
	 * @param hint. if <0, no hint, else this is index in flower of v where
	 *    there should be a match.
	 * @return
	 */
	public static int []consistent(PackData q1,PackData q2,int v,int V,int hint) 
	throws DataException {
		int num=q1.countFaces(v);
		int[] flower=q1.getFlower(v);
		int Num=q2.countFaces(V);
		int[] Flower=q2.getFlower(V);
		int jspot=-1; 
		int Jspot=-1;
		int k,K;
		int []result;
		
		// check that center indices v and V are matched
		if (Math.abs(vstat[v])!=V || Math.abs(Vstat[V])!=v)
			return null;
			
		// find first match among petals, using 'hint' if nonnegative
		if (hint>=0 && hint<=num) {  // hint is supposed to point out an existing match
			k=flower[hint];
			K=Math.abs(vstat[k]);
			if (K==0 || Math.abs(Vstat[K])!=k|| (Jspot=q2.nghb(V,K))<0) 
				throw new DataException();
			jspot=hint;
		}
		else { // must search for an existing match
			for (int j=0;(j<(num+q1.getBdryFlag(v)) && jspot<0);j++) {
				k=flower[j];
				K=Math.abs(vstat[k]);
				if (K!=0) { // there seems to be a match to k
					if ((Math.abs(Vstat[K])!=k) || (Jspot=q2.nghb(V,K))<0)
						throw new DataException();
					jspot=j;
				}
			}
		}
		if (jspot<0) 
			return null;
		
		// should have jspot and Jspot indices pointing to match.
		// Check that v is embedable at V
		if (!embedable(q1,q2,v,jspot,V,Jspot)) 
			return null;
		
		// Now, main work is checking that any further existing matches are 
		//   consistent with the jspot/Jspot match.
		int W,w;
		// v/V both interior (hence same petal count)
		if (!q1.isBdry(v) && !q2.isBdry(V)) { 
			for (int j=1;j<q1.countFaces(v);j++) {
				k=flower[(jspot+j)%num];
				K=Flower[(Jspot+j)%Num];
				W=Math.abs(vstat[k]);
				w=Math.abs(Vstat[K]);
				if ((W!=0 && W!=K) || (w!=0 && w!=k) 
						|| (w==0 && W!=0) || (W==0 && w!=0)) 
					return null;
			}
			result=new int[2];
			result[0]=jspot;
			result[1]=Jspot;
			return result;
		}
		
		// v interior, V bdry (hence num>Num)
		if (!q1.isBdry(v))  {
			for (int j=0;j<Num;j++) {
				K=Flower[j];
				int kspot=(jspot-Jspot+2*num)%num;
				k=flower[(kspot+j)%num];
				W=Math.abs(vstat[k]);
				w=Math.abs(Vstat[K]);
				if ((W!=0 && W!=K) || (w!=0 && w!=k) 
						|| (w==0 && W!=0) || (W==0 && w!=0)) 
					return null;
			}
			result=new int[2];
			result[0]=jspot;
			result[1]=Jspot;
			return result;
		}
		
		// V interior, v bdry (hence Num>num)
		if (!q2.isBdry(V)) {
			for (int j=0;j<num;j++) {
				k=flower[j];
				int Kspot=(Jspot-jspot+2*Num)%Num;
				K=Flower[(Kspot+j)%Num];
				W=Math.abs(vstat[k]);
				w=Math.abs(Vstat[K]);
				if ((W!=0 && W!=K) || (w!=0 && w!=k) 
						|| (w==0 && W!=0) || (W==0 && w!=0)) 
					return null;
			}
			result=new int[2];
			result[0]=jspot;
			result[1]=Jspot;
			return result;
		}
			
		// both v and V are bdry; find range of indices of v to check
		int jmin=jspot;
		jmin= (Jspot<jmin) ? Jspot:jmin;
		int jmax=num-jspot;
		jmax= ((Num-Jspot)<jmax) ? (Num-Jspot) : jmax;
		for (int j=-jmin;j<=jmax;j++) {
			k=flower[jspot+j];
			K=Flower[Jspot+j];
			W=Math.abs(vstat[k]);
			w=Math.abs(Vstat[K]);
			if ((W!=0 && W!=K) || (w!=0 && w!=k) 
					|| (w==0 && W!=0) || (W==0 && w!=0)) 
				return null;
		}
		result=new int[2];
		result[0]=jspot;
		result[1]=Jspot;
		return result;
	}
	
}
