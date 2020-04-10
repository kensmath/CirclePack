package listManip;

import java.awt.geom.Path2D;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Vector;

import util.GenPathUtil;
import util.StringUtil;
import allMains.CPBase;
import allMains.CirclePack;

import complex.Complex;

import exceptions.ParserException;

/**
 * Linked list of complex values representing an open or closed
 * polygonal path in the plane or on the sphere. Closure is
 * determined first from the data, but can be toggled independently;
 * closure does NOT result in repeat entry.
 * @author kens
 *
 */
public class PathLink extends LinkedList<Complex> {
	
	private static final long 
	serialVersionUID = 1L;
	
	static final double THRESH=.0000001; // threshold for closure
	public boolean closed; // closure is determined from data
	public int hes; // spherical or euclidean; hes<0 only means have to check in disc.
	
	// Constructors
	public PathLink(int heS,String datastr) {
		super();
		hes=heS;
		if (datastr!=null) addNodeLinks(datastr);
		autoClosure();
	}

	public PathLink(int heS,double x,double y) {
		super();
		hes=heS;
		Complex z=new Complex(x,y);
		add(z);
	}

	public PathLink(int heS,Vector<String> items) {
		super();
		hes=heS;
		addNodeLinks(items);
		autoClosure();
	}

	/**
	 * Empty list, geometry defaults to euclidean
	 */
	public PathLink() {
		this(0,(String)null);
	}

	public void autoClosure() {
		if (this.size()<2) return; // nothing to do
		if (this.getFirst().minus(this.getLast()).abs()<THRESH)
			closed=true;
	}
	
	public boolean isPathClosed() {
		return closed;
	}
	
	public void toggleClosed() {
		closed=!closed;
	}
	
	public boolean add(Complex z) {
		if (hes<0 && z.abs()>1.0) z.divide(z.abs()); // project to unit circle
		return super.add(new Complex(z));
	}
	
	/**
	 * Add links to this list. Note
	 * that argument should not be empty since "a" would have been
	 * added as default.
	 * @param datastr
	 * @return
	 */	
	public int addNodeLinks(String datastr) {
		Vector<String> items=StringUtil.string2vec(datastr,true);
		return addNodeLinks(items);
	}
	
	public int addNodeLinks(Vector<String> items) {
		int count=0;

		if (items==null || items.size()==0) { // default to 'ClosedPath', if it exists
			if (CPBase.ClosedPath!=null) {
				try {
					Vector<Vector<Complex>> gp=GenPathUtil.gpPolygon(CPBase.ClosedPath);
					Vector<Complex> comp1=gp.get(0);
					for (int j=0;j<comp1.size();j++) { 
						add(comp1.get(j));
						count++;
					}
				} catch (Exception ex) {
					CirclePack.cpb.errMsg("error in 'PathLink' option: "+ex.getMessage());
				}
				return count;
			}
			else
				throw new ParserException("no path specified");
		}

		Iterator<String> its=items.iterator();
		String str=null;
		double x=0.0;
		double y=0.0;
		Complex z=new Complex(0.0);
		
		
		while (its!=null && its.hasNext()) {
			str=(String)its.next();
			
			try {
				x=Double.parseDouble(str);
				if (its.hasNext()) {
					str=(String)its.next();
					y=Double.parseDouble(str);
				}
				z=new Complex(x,y);
				add(z);
				count++;
			} catch (Exception ex) {
				CirclePack.cpb.errMsg("error in 'PathLink': "+ex.getMessage());
				return count;
			}
		}
		return count;
	}
	
	/**
	 * Convert this linked list to a Java path
	 * @return Path2D.Double, null on error or empty path
	 */
	public Path2D.Double toPath2D() {
		if (this.size()==0) return null;
		Path2D.Double path=new Path2D.Double();
		Iterator<Complex> itt=this.iterator();
		Complex z=itt.next();
		path.moveTo(z.x,z.y);
		
		while (itt.hasNext()) {
			z=itt.next();
			path.lineTo(z.x,z.y);
		}
		if (closed) path.closePath();
		return path;
	}
	
	// TODO: conversions from plane to/from sphere. (Same in PathManager?)
}
