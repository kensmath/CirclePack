package input;

import java.util.Vector;

import allMains.CPBase;
import allMains.CirclePack;
import circlePack.PackControl;
import exceptions.ParserException;
import packing.PackData;
import util.SelectSpec;
import util.StringUtil;

/**
 * This is a parser for set-builder type expressions describing 
 * sets of objects (circles, faces, tiles, or edges (??)) in 
 * CirclePack: e.g. {c:d.eq.5} lists verts of degree 5. 
 * This class is created when a {..} string occurs among the 
 * flags in a command (note that commands can also be enclosed 
 * with braces if we want a return value). This parser builds a 
 * vector of 'SelectSpec's. Calling routine must check 'status' 
 * and 'packData' before using the results.
 * 
 * Form is very rigid; here are six main portions: 
     1. Outer curly brackets (this is how we identify the 
     	initial string)
     2. Target object: v (or c) circles, f faces, t tiles 
     	(TODO: might add edges, etc.) 
     3. Target packing -p* (optional; subscript for 
     	'packData' ptr) (with 'c' or 'f')	
     4. ':' separator 
     5. One or more 'specifications': these may be unary or 
     	have 'left' and 'right' side target quantities separated 
     	by some comparison symbols; left or right might be 
     	explicit double value.
     
     	a. Target quantities: 
     	
     	   Unary: 
     	     bdry=b 
     	     int=i
     	     in list=?list (appropriate to target object type, eg 'vlist')
     	     
     	   Binary:
     	   	 aim=a
     	   	 degree=d 
     	   	 marked=m
     	   	 rad=r 
     	   	 tile type = t
     	   	 angle sum=s 
     	   	 modulus of center=z (or ze for eucl center)
     	   	 plot_flag=x  (face or vert, as appropriate)
     	   	 X(resp. Y,Z)-coord in xyz data=X(resp. Y,Z)
     	   	 eucl_ratio(p,q)=epq
     	   	 hyp_ratio(p,q)=cpq
     	   
        b. Condition: =, ==, =<, <=, <, >=, =>, > (old style)
           (new: .eq., .le., .lt., .ge., .gt., .ne.)
           
     6. Connectives between selection specs: 
            &&, || (inclusive or), or ! 
           (new: .and., .or., .not.)
	
	(Note: multiple specifications are taken first-to-last in order, 
	without any logical grouping -- not syntactically general.)
	
	See 'util.SelectSpec.note_to_value' for retreiving values in
	processing specifications.
 *	
 *	@author kens
 */
public class SetBuilderParser {
	public PackData packData; // normally need only for '-p{p}' flag
	
	public char object;        // 'c' circle, 'f' face, 't' tile, 'e' edge
	public boolean status;    // ready to go with non-empty string.
	public String errMsg;     // store indication of errors here. 
	public Vector<SelectSpec> specs; // vector of 'SelectSpec' results
	String fullDeal;   // original string between {..} brackets
	boolean specHit;   // yes if we have found a specification
	
	// Constructor
	public SetBuilderParser(PackData p,String datastr,char objt) {
		packData=p; // default packing, but can be changed/set 
					// by datastr; check it on return. 
					// (TODO: what's this mean? I think calling
					// routine has to check if packing is right.)
		object=objt;
		status=true;
		specHit=false;
		errMsg=null;
		specs=new Vector<SelectSpec>();
		fullDeal=StringUtil.getBracesString(datastr);
		if (fullDeal!=null && fullDeal.length()>0) {
			try {
				hauptProcess();
			} catch (ParserException pex) {
				if (errMsg==null) {
					errMsg=new String("Unexplained ParserExpression "+
							"in hauptProcess");
				}
				CirclePack.cpb.myErrorMsg("SetBuilder error: "+errMsg);
				if (specs.size()==0) {
					CirclePack.cpb.myErrorMsg("No specifications "+
							"were obtained.");
					status=false;
				}
				else status=true;
			}
		}
		else status=false;  // calling routine needs to check status.
	}
	
	/**
	 * This parses 'fullDeal' and then goes through 'right' to create all
	 * possible valid 'SelectSpec's. It handles many exceptions.
	 * @throws ParserException
	 */
	public void hauptProcess() throws ParserException {
		// break into left/right at ':'
		String left=null;
		String right=null;
		int j;
		try {
			left=fullDeal.substring(0,(j=fullDeal.indexOf(':'))).trim();
			right=fullDeal.substring(j+1,fullDeal.length()).trim();
			if (right.length()==0) { // only need 'v'/'c' on left if '-p' flag
				throw new ParserException();
			}
		} catch (Exception ex) {
			errMsg=new String("No separator ':' or 'left'/'right' missing");
			throw new ParserException();
		}
	
		// circles? faces? tiles? 
		if (left.length()>0) {
			if (left.startsWith("f")) {
				object='f';
			}
			else if (left.startsWith("c") || left.startsWith("v")) {
				object='c';
			}
			else if (left.startsWith("t")) {
				object='t';
			}
			// TODO: edges?
		}
		
		// Check left for '-p{p}' flag
		if (left.length()>0 && (j=left.indexOf("-p"))>0) {
			int pnum=-1;
			try {
				// expect integer
				pnum=Integer.parseInt(left.substring(j+2,j+3));
				if (pnum<0 || pnum>=CPBase.NUM_PACKS 
						|| !PackControl.cpScreens[pnum].getPackData().status)
					throw new ParserException();
			} catch (Exception ex) {
				errMsg=new String("Malformed '-p{p}' pack or pack is empty");
				throw new ParserException();
			}
			// change to this packing
			packData=PackControl.cpScreens[pnum].getPackData(); 
		}
		
		// Done with left; now work on 'right' string
		
		/* ---- find/replace new-format conditions by traditional ones:
	      .eq.,.ne.,.lt.,.le.,.gt.,.ge.,.and.,.or. --> =,==,!=,
	      <,<=,>,>=,&&,|| (resp.) */
		right=right.replace(".eq."," == ");
		right=right.replace(".ne."," != ");
		right=right.replace(".neq."," != "); // alternate
		right=right.replace(".lt."," < ");
		right=right.replace(".le."," <= ");
		right=right.replace(".gt."," > ");
		right=right.replace(".ge."," >= ");
		right=right.replace(".and."," && ");
		right=right.replace(".or."," || ");
		right=right.replace(".not."," ! ");
		right=right.replace("=<"," <= "); 
		right=right.replace("=>"," >= ");
		// TODO: convert single '=' to ' == ' without infinite looping.
		
		// main processing loop; on each pass need to check for possible negation
		fullDeal=right;
		specHit=false;
		
		// break into segments by splitting at '&&' or '||',
		Vector<String> segments=StringUtil.setB_segments(fullDeal);
		for (int k=0;k<segments.size();k++) {
			String nextseg=(String)segments.get(k).trim();
			// process this segment, adding specifications to 'specs' vector
			if (parseSeg(nextseg)==0) { 
				throw new ParserException("Failed processing set builder "+
						"specification '"+fullDeal+"'");
			}
		} 
	}

	/**
	 * A "segment" is a string starting (possibly) with a 
	 * connective '&&' or '||', then (possibly) a '!' indicating 
	 * negation, then there must be a "specification", possibly 
	 * delimited by parens, possibly "unary". The processed 
	 * results are added to the 'specs' vector. 
	 * Return 0 on error, 1 on success. 
	 * 
	 * If there is a connective, the previous 'spec' entry must be a 
	 * specification (versus a connective). If there is a '!', that 
	 * sets 'negation' true for the specification. Typically, 
	 * specification will be "lstr [condition] rstr", but may also 
	 * be unary (no condition). Process 'lstr' and 'rstr' for 
	 * 'target_str' and 'value_str'; in latter case, set 'value', 
	 * if appropriate.
	 * @param str String
	 * @return 0 on error
	 */
	public int parseSeg(String str) {
		int j=-1;
		int k=-1;
		String lstr=null;
		String rstr=null;
		SelectSpec ent=new SelectSpec(object);
		
		// check for leading connective first
		if (str.startsWith("&&") || str.startsWith("||")) {
			// must have preceeding specification
			if (specs.size()==0 || specs.get(specs.size()-1).isConnective) return 0; 
			SelectSpec nspec=new SelectSpec(object);
			nspec.isConnective=true;
			nspec.isAnd=true;
			if (str.startsWith("||")) nspec.isAnd=false;
			specs.add(nspec); // add to 'specs'
			str=str.substring(2).trim(); // get rid 
		}

		ent.isConnective=false;
		ent.negation=false;
		
		// check for negation
		if (str.startsWith("!")) {
			ent.negation=true;
			str=str.substring(1);
		}
		
		// check for parens --- should be first and last characters
		j=str.indexOf('(');
		k=str.indexOf(')',j+1);
		if ((j<0 && k>=0) || (j>=0 && k<0) || k<j) return 0; // malformed
		if (k>=0 && j>=0) {
			str=str.substring(j+1,k).trim();
			if (str.length()==0) return 0; // nothing left
		}

		// should have specification only now, look for 
		//    typcial situations first
		j=k=-1;
		if ((j=str.indexOf("=="))>=0 || 
				(j=str.indexOf(">="))>=0 || 
				(j=str.indexOf("<="))>=0 || 
				(j=str.indexOf("!="))>=0) { // 2-character condition?
			k=j+2;
			ent.setCondition(str.substring(j,k));
		}
		else if ((j=str.indexOf("="))>=0 || (j=str.indexOf(">"))>=0 
			    || (j=str.indexOf("<"))>=0) { // 1-character condition?
			k=j+1;
			ent.setCondition(str.substring(j,k));
		}
		else { // can only be unary (TODO: should check?)
			ent.unary=true;
			ent.left_str=new String(str).trim();
			ent.right_str=null;
			specs.add(ent);
			return 1;
		}
		
		// if not unary, should have j and k; 
		if (j>=0 && k>=0) {
			if (k>=str.length()-1 || j<=0) return 0; // error: lstr/rstr missing
			lstr=str.substring(0,j).trim();
			rstr=str.substring(k).trim();
			
			// check for explicit double(s)
			try {
				ent.left_value=Double.parseDouble(lstr);
			} catch (Exception ex) {
				ent.left_value=null;
				ent.left_str=lstr;
			}
			try {
				ent.right_value=Double.valueOf(rstr);
				ent.right_str=rstr;
			} catch (Exception ex) {
				ent.right_value=null;
				ent.right_str=rstr;
			}
		}
		
		specs.add(ent);
		return 1;
	}

	/**
	 * The connectives '&&' and '||' are stored as 'SelectSpec's.
	 * @param str
	 * @return
	 */
	public SelectSpec storeConnective(String str) {
		str=str.trim();
		if (!str.equals("&&") && !str.equals("||")) return null;
		SelectSpec ss=new SelectSpec(object);
		ss.isConnective=true;
		if (str.equals("&&")) ss.isAnd=true;
		else ss.isAnd=false;
		return ss;
	}
	
	/** Return the vector of (seemingly good) specifications. 
	 * Calling routine needs to check its 'status' and its 
	 * 'packData' (which may have changed).
	 * @return Vector<SelectSpec>
	 */ 
	public Vector<SelectSpec> getSpecVector() {
		if (specs==null || specs.size()==0) return null;
		return specs;
	}
	
	/** Does 'status' indicate success? */
	public boolean isOkay() {
		return status;
	}
}
