package listManip;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Random;
import java.util.Vector;

import packing.PackData;
import util.StringUtil;
import allMains.CPBase;
import allMains.CirclePack;
import circlePack.PackControl;

import complex.Complex;

import exceptions.ParserException;

/**
 * This is a linked list for 'Complex' numbers. Such lists are used,
 * for example, to collect location information from canvas mouse 
 * clicks, also from things like circle centers or dual centers,
 * or complex values run through the current 'function' or 'parametric
 * path' in the Function Window.
 * 
 * NOTE: z may be point of plane or a point (theta,phi) of the sphere;
 * conversions are not implemented, so a change in geometry can invalidate
 * these complex numbers.
 * 
 * Mainly, however, process strings of pairs x y of doubles. 
 * 
 * TODO: not many options completed yet.
 * @author kens
 *
 */
public class PointLink extends LinkedList<Complex> {
	
	private static final long 
	serialVersionUID = 1L;
	
	PackData packData;  // generally null, but may need geometry or 'zlist'
	
	// Constructors
	public PointLink(PackData p,String datastr) {
		super();
		packData=p;
		if (datastr!=null) addPointLinks(datastr);
	}
	
	public PointLink(String datastr) {
		this((PackData)null,datastr);
	}
	
	public PointLink(PackData p,Complex z) {
		super();
		packData=p;
		if (z!=null) 
			add(z);
	}
	
	public PointLink(Complex z) {
		this((PackData)null,z);
	}
	
	public PointLink(PackData p,Vector<String> items) {
		super();
		packData=p;
		if (items!=null && items.size()>0) 
			addPointLinks(items);
	}
	
	public PointLink(Vector<String> items) {
		this((PackData)null,items);
	}
	
	public PointLink(PackData p) {
		this(p,(String)null);
	}
	
	public PointLink() {
		this((PackData)null,(String)null);
	}
	
	public boolean add(Complex z) {
		if (z!=null)
			return super.add((Complex)z);
		return false;
	}
	
	/**
	 * Add links to this list. 
	 * @param datastr
	 * @return
	 */	
	public int addPointLinks(String datastr) {
		Vector<String> items=StringUtil.string2vec(datastr,true);
		return addPointLinks(items);
	}
	
	/**
	 * Add links to this list.
	 * @param items Vector<String>
	 * @return int count
	 */	
	public int addPointLinks(Vector<String> items) {
		
		if (items==null || items.size()==0)
			return 0;
		
		int count=0;

		String str=items.get(0);
		boolean thereIsFlag=false;
		
		// check for flag; only first entry can be flag.
		// if there is an accidental '-', remove it
		char a=str.charAt(0);
		if (StringUtil.isFlag(str)) { 
			str=str.substring(1);
			a=str.charAt(0);
			items.remove(0);
			thereIsFlag=true;
		}
		else if (a!='-' && a!='.' && !Character.isDigit(a)) {
			items.remove(0);
			thereIsFlag=true;
		}

		if (str.startsWith("Zli")) {
			abutMore(CPBase.Zlink);
			return CPBase.Zlink.size();
		}
		else if (str.startsWith("zli")) {
			if (packData==null)
				throw new ParserException("no packdata with this 'zlist'");
			if (packData.zlist==null)
				return 0;
			return abutMore(packData.zlist);
		}

		if (!thereIsFlag && (items==null || items.size()==0)) // nothing to do?
			return 0;
		
		// If there is a flag, we get it, process, and return.
		if (thereIsFlag) {
			String str2=items.get(0);
			char aa=str2.charAt(0);
			
			// because of recursion, must check for second flag: 
			//   should be only points
			if (StringUtil.isFlag(str2) || (aa!='-' && aa!='.' && 
					!Character.isDigit(aa))) {
				// check for 'Zlist' and 'zlist' again (since they may follow a flag) 
				if (str2.startsWith("Zli")) {
					abutMore(CPBase.Zlink);
					return CPBase.Zlink.size();
				}
				else if (str2.startsWith("zli")) {
					if (packData==null)
						throw new ParserException("no packdata with this 'zlist'");
					abutMore(packData.zlist);
					return packData.zlist.size();
				}
				
				// else, failure
				throw new ParserException("'PointLink' data can have only one flag");
			}
			
			// recursive call to process the rest of the input 
			//    (should be just x y pairs)
			PointLink ptlink=new PointLink(items);
			if (ptlink==null || ptlink.size()==0)
				throw new ParserException("failed in getting complex points");
				
			switch(a) {
			case 'f': // run string through 'function', if defined in function window
			{
				if (PackControl.newftnFrame.ftnField.getText().trim().length()==0) {
					CirclePack.cpb.errMsg("'Function' frame is not set");
					return 0;
				}

				Iterator<Complex> pts=ptlink.iterator();
				while (pts.hasNext()) {
					try {
						if (add(CirclePack.cpb.getFtnValue(pts.next())))
							count++;
					} catch (Exception ex) {}
				}
				break;
			}
			case 'g': // run string through 'parameter path', 
						//  if defined in function window
			{
				if (PackControl.newftnFrame.paramField.getText().trim().length()==0) {
					CirclePack.cpb.errMsg("'Parameter' field in Function Frame is not set");
					return 0;
				}

				Iterator<Complex> pts=ptlink.iterator();
				while (pts.hasNext()) {
					try {
						if (add(CirclePack.cpb.getParamValue(pts.next().x)))
							count++;
					} catch (Exception ex) {}
				}
				break;
			}
			case 'z': // generally from mouse location
			{
				// read the x y pairs
				Iterator<Complex> pts=ptlink.iterator();
				while (pts.hasNext()) {
					if (add(pts.next()))
						count++;
				}
				break;
			}
			case 'c': // centers of specified vertices
			{
				if (packData==null)
					throw new ParserException("can't get centers, no packing given");
				NodeLink vlit=new NodeLink(packData,items);
				Iterator<Integer> vlt=vlit.iterator();
				while (vlt.hasNext()) {
					if (add(packData.getCenter(vlt.next())))
						count++;
				}
				break;
			}
			case 'd': // dual objects
			{
				if (str.length()==2)
					break;
				char c2=str.charAt(2);
				switch (c2) {
				case 'c': // face incenters
				{
					// TODO: process and return
					break;
				}
				case 'p': // points of tangency
				{
					// TODO: process and return
					break;
				}
				case 'z': // zlist? Zlist?
				case 'Z':	
				{
					if (str.startsWith("zlist") || str.startsWith("Zlist"))
					break;
				}
				} // end of dual switch

				break;
			}
			} // end of switch
			
			return count;
		} // end of flag processing
		
		// reaching here, just pull off pairs of reals and generate complexes.
		Iterator<String> its=items.iterator();
		while (its!=null && its.hasNext()) {
			str=its.next();
			try {
				double x=Double.parseDouble(str);
				double y=Double.parseDouble(its.next());
				if (add(new Complex(x,y)))
					count++;
			} catch (Exception ex) {
				CirclePack.cpb.errMsg("format error reading complexes, count "+count);
					return count;
			}
		} // end of getting x y points
		return count;
	}
	
	
	
	/**
	 * Make a distinct copy of this linked list; no check
	 * of validity of the entries.
	 * @return NodeLink
	 */
	public PointLink makeCopy() {
		Iterator<Complex> zlist=this.iterator();
		PointLink newlist=new PointLink();
		while (zlist.hasNext()) {
			newlist.add(new Complex(zlist.next()));
		}
		return newlist;
	}
	
	
	/**
	 * Abut a copy of given @see PointLink to the end of this one. 
	 * @param morePL @see PointLin
	 * @return count of new complex numbers (some may be redundant)
	 */
	public int abutMore(PointLink morePL) {
		if (morePL==null || morePL.size()==0)
			return 0;
		int ticks=0;
		Iterator<Complex> mit=morePL.iterator();
		Complex z=null;
		while (mit.hasNext()) {
			z=new Complex(mit.next());
			add(z);
			ticks++;
		}
		return ticks;
	}
	
	/**
	 * Pick first complex off first string of first vector of vector
	 * of string vectors. 
	 * @param flagsegs Vector<Vector<String>>
	 * @return Complex, null on failure
	 */
	public static Complex grab_one_z(Vector<Vector<String>> flagsegs) {
		try {
			Vector<String> its=(Vector<String>)flagsegs.get(0);
			PointLink plk=new PointLink(its);
			return new Complex(plk.getFirst());
		} catch (Exception ex) {
			return null;
		}
	}

	/**
	 * Pick first complex off string
	 * @param str String
	 * @return Complex, null on failure
	 */
	public static Complex grab_one_z(String str) {
		try {
			PointLink plk=new PointLink(str);
			return new Complex(plk.getFirst());
		} catch (Exception ex) {
			return null;
		}
	}

	
	/**
	 * Index 'indx' points to entry z, then rotate, returning a new PointLink
	 * starting and ending with v.
	 * @param link @see PointLink
	 * @param indx new starting index
	 * @return @see PointLink, null if empty or on error.
	 */
	public static PointLink rotateMe(PointLink link,int indx) {
		int sz=0;
		if (link==null || (sz=link.size())==0 || link.get(0)!=link.get(sz-1))
			return null;
		PointLink zlink=new PointLink();
		int i=indx;
		while (i<(sz-1)) { // last one is a repeat
			zlink.add(link.get(i));
			i++;
		}
		i=0;
		while (i<=indx) {
			zlink.add(link.get(i));
			i++;
		}
		return zlink;
	}

	
	/**
	 * Return a new 'PointLink' whose order is the reverse of this
	 * @return new 'PointLink', null if this is empty.
	 */
	public PointLink reverseMe() {
	    PointLink qtmp=new PointLink();
	    if (this.size()==0) return null;
	    Iterator<Complex> it=this.iterator();
	    while (it.hasNext()) {
	    	qtmp.add(0,new Complex(it.next()));
	    }
	    return qtmp;
	}
	
	/**
     * Return random entry from pointlist; caution, does not adjust
     * for repeat entries.
     * @param pointlist
     * @return Complex, null on error
     */
    public static Complex randVert(PointLink ptlist) {
    	if (ptlist==null || ptlist.size()==0) return null;
    	int n=new Random().nextInt(ptlist.size());
    	return ptlist.get(n);
    }

	 /**
	  * Set 'packData' (which helps determine eligibility of entries)
	  * @param p PackData
	  */
	 public void setPackData(PackData p) {
		 packData=p;
	 }
}
