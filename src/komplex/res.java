package komplex;

import java.util.Vector;

import packing.PackData;

/**
 * Started 3/2017 to mimic Rich Schwartz's Triangle coloring javascript code.
 * @author kstephe2
 *
 */
public class res {
	
	PackData packData;
	int []free;		// .FREE vector
	int []B;        //
	int [][]incidence;  // incidence matrix 
	
	public res(PackData p){
		packData=p;
		free=new int[p.faceCount+1];
//		getIncidence();
	}
	
//	public MatrixMod3 getIncidence
	
	public double energy() {
		
		return 0.0;
	}
	
	// reverse the entry in list of cells.
	public void cellReverse(int []B) {
		int a=-1;
		for (int j=0;j<B.length;j++)
			a = B[j];
		free[a] *= -1;
	}
	
	public void rowReduce(MatrixMod3 m) {
		m.rowReverse();
		int q = 0;
		for (int i = 0; i < m.H; i++) {
			q = i;
			if (i < m.H - 1) {
				q = m.nextRowIndex(i);
				if (q > i)
					m.rowSwap(i, q);
			}
			m.clearColumn(i); // only call to 'clearColumn'
		}
		m.leftToOne();
		m.rowReverse();
	}
	
	public int randomLength(int n) {
		int count = 1;
		boolean test = false;
		while (!test) {
			double a = Math.random();
			if (a < 1 - 4 / n)
				return count;
			count++;
			if (count == n - 5)
				return count;
		}
		test = true;
		return count;
	}

}


// ========================== 

class MatrixMod3 {
	int H;			// number of rows
	int W;			// number of columns
	int [][]m;		// the matrix itself
	
	public MatrixMod3(int w,int h) {
		H=h;
		W=w;
		m=new int[H][];
		for (int j=0;j<W;j++) 
			m[j]=new int[W];
	}
	
	public void rowReduce() {
		this.rowReverse();
		int q=0;
		for(int i=0;i<this.H;i++) {
			q=i;
		    if(i<this.H-1) {
		       q=this.nextRowIndex(i);
		       if(q>i) this.rowSwap(i,q);
		    }
		    this.clearColumn(i); 
		} 
		this.leftToOne(); 
		this.rowReverse();
	}

	public void rowReverse() {
		for(int i=0;i<this.H;i++) 
			this.m[i]=reverse(this.m[i]);
	}

	public void leftToOne() {
		for(int i=0;i<this.H;i++) 
			leftToOne0(this,i);
	}

	public void leftToOne0(MatrixMod3 M,int u) {
		int k=M.leftNonzeroIndex(u);
		if(k==-1) 
			return;
		int a=M.m[u][k];
		for(int i=0;i<M.W;i++) {
		    M.m[u][i]=(M.m[u][i]*a)%3;
		}
	}

	public void addRow(int u,int v,int a) {
		for(int i=0;i<this.W;i++) {
		    int b=a*this.m[u][i];
		    int c=this.m[v][i];
		    this.m[v][i]=(b+c)%3;
		}
	}

	public void rowSwap(int u,int v) {
		for(int i=0;i<this.W;i++) {
			int A=this.m[u][i];
		    this.m[u][i]=this.m[v][i];
		    this.m[v][i]=A;
		}
	}

	/**
	 * find index of first non-zero entry in row 'u',
	 * -1 if none found.
	 * @param u int
	 * @return int, -1 if not found
	 */
	public int leftNonzeroIndex(int u) {
		for(int i=0;i<this.W;i++) {
			if(this.m[u][i]!=0) 
				return i;
		}
		return -1;
	}

	public int nextRowIndex(int k) {
		int index=-1;
		int min=this.W+1;
		for(int i=k;i<this.H;i++) {
			int j=this.leftNonzeroIndex(i);
			if((j>=k)&&(j<min)) {
				index=i;
				min=j;
		    }
		}
		return index;
	}

	public void clearColumn(int u) {
		int k = this.leftNonzeroIndex(u);
		if (k == -1)
			return;
		int a = this.m[u][k];
		for (int v = 0; v < this.H; v++) {
			if (v != u) {
				int b = this.m[v][k];
				int c = (2 * a * b) % 3;
				this.addRow(u, v, c);
			}
		}
	}

	public Vector<Vector<Integer>> variableDependency() {
		  Vector<Integer> D=new Vector<Integer>(0);
		  Vector<Integer> I=new Vector<Integer>(0);
		  for(int i=0;i<this.W;++i) {
		    int []z=columnSum(this,i);
		    if(z[0]>1)
		    	I.add(i);
		    else if(z[0]==1)
		    	D.add(i);
		  }
		  Vector<Integer> DD=new Vector<Integer>(0);
		  for (int i=D.size()-1;i>=0;i--)
			  DD.add(D.get(i));
		  Vector<Vector<Integer>> ans=new Vector<Vector<Integer>>(0);
		  ans.add(I);
		  ans.add(DD);
		  return ans;
	}

	public int []columnSum(MatrixMod3 M, int j) {
		int tot = 0;
		int index = 0;
		for (int i = 0; i < M.H; ++i) {
			tot = tot + M.m[i][j];
			if (M.m[i][j] != 0)
				index = i;
		}
		int[] ans = new int[2];
		ans[0] = tot;
		ans[1] = index;
		return ans;
	}

	public int[] evaluateVector(int[] F) {
		int[] E = new int[H];
		for (int i = 0; i < this.H; ++i) {
			E[i] = vectorDot(this.m[i], F);
			E[i] = (E[i] + 3000) % 3;
		}
		return E;
	}

	public int vectorDot(int[] A, int[] B) {
		int tot = 0;
		for (int i = 0; i < A.length; ++i) {
			tot = tot + A[i] * B[i];
		}
		return tot;
	}
	
	/**
	 * reverse a vector
	 * @param A int[]
	 * @return int[]
	 */
	public int []reverse(int []A) {
		int []B=new int[A.length];
		for(int i=A.length-1;i>-1;i--) {
			B[i]=A[A.length-i-1];
		}
		return B;
	}

}
