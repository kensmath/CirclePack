package JNI;

/**
 * OBE: As of 3/2022 the JNI calls to C code have been deleted.
 * This container might be used in the future.
 * 
 * Data container for linear system sparse matrix calls.
 * The native code, such as C++, is given access to this
 * when asked to solving a linear system via UMFPACK. The
 * sizes and the arrays 'Ap' and 'Ai' need to be set just once.
 * Entries 'Aentries', 'rhsX', and 'rhsY' are set on each
 * iteration. GOPacker uses the solutions 'Zx' 'Zy' to set
 * new entries for next iteration. (I don't know if this
 * 'status' is useful, depends too much on synchronization 
 * I imagine.)
 * @author Kens
  */
public class SolverData {
	// --- status: 
	//     1 = data ready; 
	//     2 = in native processing;
	//     3 = solutions available ----
	public int status;
	
	// ------ this stuff needs to be set just once ----------
	// array size info
	public int intNum;			// set to 'intVertCount'
	public int bdryNum;		// set to 'bdryCount'
	public int nz_entries;		// number of non-zero entries in 'Aentries'
	
	// arrays for compressed column sparse format
	public int []Ap;	// array of size 'intNum+1'	
	public int []Ai;	// array of size 'nz_entries'
	
	// ------ this stuff changes as we iterate -----------
	// array of size 'nz_entries' of entry data
	public double []Aentries;	// non-zero entries of intNum x intNum sparse matrix
	
	// arrays of size 'bdryNum', real/imag parts of system right hand side
	public double []rhsX;
	public double []rhsY;
	
	// ------ these are solutions provided by native code --------
	// arrays of size 'intNum'
	public double []Zx;
	public double []Zy;
	
	// array size of 'intNum'+'bdryNum'
	public double []radii;
	
	public SolverData() {
		status=0;
	}
}

