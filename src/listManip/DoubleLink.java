package listManip;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Random;
import java.util.Vector;

import allMains.CPBase;
import allMains.CirclePack;
import circlePack.PackControl;
import complex.Complex;
import exceptions.ParserException;
import packing.PackData;
import util.MathUtil;
import util.StringUtil;

/**
 * Linked list of Doubles, used to pass values when
 * necessary, as with 'schwarzians' or 'uzians'.
 * @author kensm
 *
 */
public class DoubleLink extends LinkedList<Double> {
	
	private static final long 
	serialVersionUID = 1L;
	
	PackData packData;  // generally null, but may need geometry or 'zlist'
	
	// Constructors
	public DoubleLink(PackData p,String datastr) {
		super();
		packData=p;
		if (datastr!=null) addDoubleLinks(datastr);
	}
	
	public DoubleLink(String datastr) {
		this((PackData)null,datastr);
	}
	
	public DoubleLink(PackData p,Double x) {
		super();
		packData=p;
		if (x!=null) 
			add(x);
	}
	
	public DoubleLink(PackData p,double x) {
		super();
		packData=p;
		add(x);
	}
	
	public DoubleLink(Double x) {
		this((PackData)null,x);
	}
	
	public DoubleLink(PackData p,Vector<String> items) {
		super();
		packData=p;
		if (items!=null && items.size()>0) 
			addDoubleLinks(items);
	}
	
	public DoubleLink(Vector<String> items) {
		this((PackData)null,items);
	}
	
	public DoubleLink(PackData p) {
		this(p,(String)null);
	}
	
	public DoubleLink() {
		this((PackData)null,(String)null);
	}
	
	public boolean add(Double X) {
		if (X!=null)
			return super.add((Double)X);
		return false;
	}
	
	public boolean add(double x) {
		return super.add(Double.valueOf(x));
	}
	
	/**
	 * Add links to this list. 
	 * @param datastr
	 * @return
	 */	
	public int addDoubleLinks(String datastr) {
		Vector<String> items=StringUtil.string2vec(datastr,true);
		return addDoubleLinks(items);
	}
	
	/**
	 * Add links to this list.
	 * @param items Vector<String>
	 * @return int count
	 */	
	public int addDoubleLinks(Vector<String> items) {
		
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

		// self call if str is a variable
		if (str.startsWith("_")) {
			count+=addDoubleLinks(CPBase.varControl.getValue(str));
		}

		// look for 'Dlist'
		else if (str.substring(1).startsWith("list")) {
			DoubleLink dlink=null;

			if ((str.startsWith("Dli") && 
					(dlink=CPBase.Dlink)!=null && dlink.size()>0)) {
				String[] b_string;
				String brst;
				String strdata=str.substring(5).trim(); // remove '?list'
			
				// check for parens listing range of indices 
				int lsize=dlink.size()-1;
				int[] irange=StringUtil.get_int_range(strdata, 0,lsize);
				if (irange!=null) {
					int aa=irange[0];
					int bb=(irange[1]>lsize) ? lsize : irange[1]; 
					for (int j=aa;j<=bb;j++) {
						add(dlink.get(j));
						count++;
					}
				}
			
				// else check for brackets
				else if ((b_string=StringUtil.get_bracket_strings(strdata))!=null 
						&& (brst=b_string[0])!=null) {
					if (brst.startsWith("r")) { // rotate: copy first at end
						dlink.add(dlink.getFirst());
					}
					if (brst.startsWith("r") 
							|| brst.startsWith("n")) { // use an remove first
						Double d=dlink.removeFirst();
						add(d);
						count++;
					}
					if (brst.startsWith("l")) { // last
						add(dlink.getLast());
						count++;
					}						
					else { // else specified index
						try{
							int n=MathUtil.MyInteger(brst);
							if (n>=0 && n<dlink.size()) {
								add(dlink.get(n));
								count++;
							}
						} catch (NumberFormatException nfe) {}
					}
				}
				// else just adjoin the current list
				else { 
					int n=size();
					addAll(n,dlink);
					count +=dlink.size();
				}
			}
			else // no appropriate list found
				return count;
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
				// check for 'Dlist' again (since it may follow a flag) 
				if (str2.startsWith("Dli")) {
					abutMore(CPBase.Dlink);
					return CPBase.Dlink.size();
				}
				
				// else, failure
				throw new ParserException("'DoubleLink' data can have only one flag");
			}
			
			// recursive call to process the rest of the input 
			//    (should be just x y pairs)
			DoubleLink dlink=new DoubleLink(items);
			if (dlink==null || dlink.size()==0)
				throw new ParserException("failed in getting Doubles");
				
			switch(a) {
			case 'f': // run string through 'function', if defined in function window
				// CAUTION: only add real part of function output
			{
				if (PackControl.newftnFrame.ftnField.getText().trim().length()==0) {
					CirclePack.cpb.errMsg("'Function' frame is not set");
					return 0;
				}

				Iterator<Double> dts=dlink.iterator();
				while (dts.hasNext()) {
					try {
						Complex z=CirclePack.cpb.getFtnValue(new Complex(dts.next()));
						if (add(z.x)) // use only real part 
							count++;
					} catch (Exception ex) {}
				}
				break;
			}
			} // end of switch
			
			return count;
		} // end of flag processing
		
		// reaching here, just pull off doubles
		Iterator<String> its=items.iterator();
		while (its!=null && its.hasNext()) {
			str=its.next();
			try {
				double x=Double.parseDouble(str);
				if (add(Double.valueOf(x)))
					count++;
			} catch (Exception ex) {
				CirclePack.cpb.errMsg("format error reading Doubles, count "+count);
					return count;
			}
		} // end of getting x y points
		return count;
	}
	
	
	
	/**
	 * Make a distinct copy of this linked list; no check
	 * of validity of the entries.
	 * @return DoubleLink
	 */
	public DoubleLink makeCopy() {
		Iterator<Double> dlist=this.iterator();
		DoubleLink newlist=new DoubleLink();
		while (dlist.hasNext()) {
			newlist.add(dlist.next());
		}
		return newlist;
	}
		
	/**
	 * Abut a copy of given @see DoubleLink to the end of this one. 
	 * @param moreDL @see DoubleLink
	 * @return count of new Doubles (some may be redundant)
	 */
	public int abutMore(DoubleLink moreDL) {
		if (moreDL==null || moreDL.size()==0)
			return 0;
		int ticks=0;
		Iterator<Double> mit=moreDL.iterator();
		Double d=null;
		while (mit.hasNext()) {
			d=Double.valueOf(mit.next());
			add(d);
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
	public static Double grab_one_d(Vector<Vector<String>> flagsegs) {
		try {
			Vector<String> its=(Vector<String>)flagsegs.get(0);
			DoubleLink dlk=new DoubleLink(its);
			return Double.valueOf(dlk.getFirst());
		} catch (Exception ex) {
			return null;
		}
	}

	/**
	 * Pick first complex off string
	 * @param str String
	 * @return Double, null on failure
	 */
	public static Double grab_one_d(String str) {
		try {
			DoubleLink dlk=new DoubleLink(str);
			return Double.valueOf(dlk.getFirst());
		} catch (Exception ex) {
			return null;
		}
	}
	
	/**
	 * Index 'indx' points to entry d, then rotate, returning a new DoubleLink
	 * starting and ending with d.
	 * @param link @see DoubleLink
	 * @param indx new starting index
	 * @return @see DoubleLink, null if empty or on error.
	 */
	public static DoubleLink rotateMe(DoubleLink link,int indx) {
		int sz=0;
		if (link==null || (sz=link.size())==0 || link.get(0)!=link.get(sz-1))
			return null; // not closed list
		DoubleLink dlink=new DoubleLink();
		int i=indx;
		while (i<(sz-1)) { // last one is a repeat
			dlink.add(link.get(i));
			i++;
		}
		i=0;
		while (i<=indx) {
			dlink.add(link.get(i));
			i++;
		}
		return dlink;
	}
	
	/**
	 * Return a new 'PointLink' whose order is the reverse of this
	 * @return new 'PointLink', null if this is empty.
	 */
	public DoubleLink reverseMe() {
	    DoubleLink qtmp=new DoubleLink();
	    if (this.size()==0) 
	    	return null;
	    Iterator<Double> it=this.iterator();
	    while (it.hasNext()) {
	    	qtmp.add(0,Double.valueOf(it.next()));
	    }
	    return qtmp;
	}
	
	/**
     * Return random entry from pointlist; caution, does not adjust
     * for repeat entries.
     * @param pointlist
     * @return Complex, null on error
     */
    public static Double randVert(DoubleLink dlist) {
    	if (dlist==null || dlist.size()==0) 
    		return null;
    	int n=new Random().nextInt(dlist.size());
    	return dlist.get(n);
    }

	 /**
	  * Set 'packData' (which helps determine eligibility of entries)
	  * @param p PackData
	  */
	 public void setPackData(PackData p) {
		 packData=p;
	 }
}

