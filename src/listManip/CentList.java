package listManip;
import complex.Complex;

/**
 * Linked list of vertices.
 * @author kens
 *
 */
public class CentList {
	
	public Complex z;      // Complex value
	public CentList next; 
	
	//Constructors
	// create empty entry
	public CentList() { 
		z=new Complex(0.0);
		next=null;
    }
	
}