package util;

import input.CommandStrParser;
import input.QueryParser;

import java.util.Vector;

import packing.PackData;
import exceptions.ParserException;

/**
 * This object encapsulates a string which may be evaluated as
 * true or false.
 * This processes whole strings, substrings, and connectives for 
 * true/false recursively.
 * 
 * This is not sophisticated, so e.g., in absence of grouping, we 
 * process in order, so A and B or C is (A and B) or C.
 * 
 * There is a fixed list of logical and mathematical, unary and binary
 * operations and strings, possibly grouped using '(..)'. Some strings
 * may involve commands which return objects --- values, strings, "true",
 * "false", etc.
 * @author kstephe2
 *
 */
public class BoolParser {
	
	enum Connective {AND,OR,NOR,NOT,NULL};
	enum Condition {LT,LE,GT,GE,EQ,NE,NULL};
	static final double TOLER=.0000000001;

	/**
	 * Process a string to determine if it is true or false. 
	 * * Break the string into 'content' and 'connective' pieces.
	 * * Process each 'content' piece and replace by "true" or "false";
	 *   this involves recursion of this routine.
	 * * Pass through and absorb any "NOT' unary 'connective' to switch
	 *   the subsequent 'content' state.
	 * * Now linearly work through the list in pairs, replacing any
	 *   'content-connective-content' piece by single "true" or "false".
	 * @param p PackData
	 * @param ifStr String
	 * @return Boolean, null on error
	 */
	public static Boolean trueFalse(PackData p,String ifStr) {
		
		// break into 'content' and 'connective' pieces

		Vector<String> cc_vec=parse4Connectives(ifStr);
		
		// if there were connectives, recurse until 'content' pieces 
		//    are isolated and evaluated
		if (cc_vec.size()>1) {
			for (int i=0;i<cc_vec.size();i++) {
				// if this is 'content', replace it by 'true' or 'false'
				if (getConnective(cc_vec.get(i))==Connective.NULL) {
					String tf=cc_vec.remove(i);
					cc_vec.insertElementAt(trueFalse(p,tf).toString(), i);
				}
			}
		}
		
		// this must be an isolated 'content' piece
		else if (cc_vec.size()==1) {
			return contentTruth(p,ifStr);
		}
		
		// absorb 'not' connectives
		for (int i=0;i<cc_vec.size();i++) {
			if (getConnective(cc_vec.get(i))==Connective.NOT) {
				if (i==cc_vec.size()-1 || getConnective(cc_vec.get(i+1))!=Connective.NULL)
					throw new ParserException("trueFalse: improper 'not'");
				cc_vec.remove(i); // remove the 'not'
				String ans=cc_vec.get(i); // get next 'content'
				cc_vec.remove(i); // remove it
				if (ans.equals("true")) // replace it
					cc_vec.insertElementAt(new String("false"), i);
				else 
					cc_vec.insertElementAt(new String("true"), i);
			}
		}

		// Collapse triples content-connective-content, left to right,
		//   replacing by single "true" or "false".
		while (cc_vec.size()>=1) {
			if (cc_vec.size()==1) {
				String bo=cc_vec.remove(0);
				if (bo.equalsIgnoreCase("true"))
					return Boolean.valueOf(true);
				else if (bo.equalsIgnoreCase("false"))
					return Boolean.valueOf(false);
				else
					throw new ParserException("trueFalse: didn't parse correctly");
			}
			if (cc_vec.size()==2)
				throw new ParserException("trueFalse: must have at least 3");
			Connective cnct=getConnective(cc_vec.remove(1));
			if (cnct==Connective.NULL)
				throw new ParserException("trueFalse: expected connective");
			Boolean lhs=getBoolean(cc_vec.remove(0));
			Boolean rhs=getBoolean(cc_vec.remove(0));
			if (lhs==null || rhs==null)
				throw new ParserException("trueFalse: content item neither 'true' nor 'false'");
			switch(cnct) {
			case AND: {
				if (lhs && rhs)
					cc_vec.insertElementAt(new String("true"),0);
				break;
			}
			case OR: {
				if (lhs || rhs)
					cc_vec.insertElementAt(new String("true"),0);
				break;
			}
			case NOR: {
				if (lhs || rhs && !(lhs && rhs))
					cc_vec.insertElementAt(new String("true"),0);
				break;
			}
			default: {
				cc_vec.insertElementAt(new String("false"),0);
			}
			} // end of switch

		} // end of while

		// reaching here, some error
		return null;
	}
	
	/**
	 * Break given string (trimmed) into Vector of 'connective' and 
	 * 'content' strings. Content strings may have four forms, '(..)',
	 * '!(..)', or same but without parens. Note that with parens, there
	 * may be connectives between that are cought in our recursion.
	 * @param fullStr String
	 * @return Vector<String> or null, vector may be empty
	 */
	public static Vector<String> parse4Connectives(String fullStr) {
		StringBuilder sb=new StringBuilder(fullStr.trim());
		Vector<String> reslt=new Vector<String>();

		boolean hit=true;
		while (hit && sb.length()>0) {
			hit=false;
			char c=sb.charAt(0);
				
			// pick off unary negative
			if (c=='!') {
				reslt.add("!");
				sb.deleteCharAt(0);
				sb.trimToSize();
				hit=true;
				continue; // loop again
			}
				
			// pick off next '(..)' string
			if (c=='(') {
				String []breakup=StringUtil.getGroupedStr(sb,'(');
				if (breakup[0].length()>0) {
					reslt.add(breakup[0]);

				}
				if (breakup[1].length()>0) {
					sb=new StringBuilder(breakup[1].trim());
				}
				hit=true; // want another pass
				continue;
			}
			
			// else assume non-paren content, so search for next connective
			else {
				int []nc=getNextConnective(sb);
				
				// no more connectives? all is a content string 
				if (nc==null) {
					if (sb.length()>0);
					reslt.add(sb.toString());
					continue; // loop will finish 
				}
				
				// must be a negation
				if (nc[1]==nc[0]) {
					if (nc[0]!=0)
						throw new ParserException("'BoolParser' syntax error, naked negation");
					reslt.add("!");
					sb.deleteCharAt(0);
					hit=true;
					continue; // loop again
				}

				String wegot=sb.substring(nc[0], nc[1]+1);
				if (getConnective(wegot)==Connective.NULL)
					throw new ParserException("'BoolParser': connective? = "+wegot);
				reslt.add(wegot);
				hit=true;
				sb=new StringBuilder(sb.substring(nc[1]+1));
			}
		} // end of while
		
		return reslt;
	}
	
	/**
	 * Given a 'content' string (no connectives), determine truth,
	 * return null on error. 'content' may be unary, or has 'left',
	 * 'condition', 'right'
	 * @param p PackData, may need to run commands or inquiries
	 * @param content String
	 * @return Boolean or null on error
	 */
	public static Boolean contentTruth(PackData p,String content) {
		
		// first determine if there's a 'condition' in the middle.
		StringBuilder sb=new StringBuilder(content.trim());
		if (sb.length()==0) // shouldn't happen
			return null;
		int []nc=getNextCondition(sb);
		
		// no condition? expect a query (TODO: eventually other operations)
		if (nc==null) { 
			// returning string 'true' or 'false'. e.g. ?status for pack status 
			if (sb.charAt(0)=='?') {
				sb.deleteCharAt(0);
				// split off 'query': from '?' up to first " "
				int k=sb.indexOf(" ");
				String result=null;
				if (k>0) {
					String query=sb.substring(0,k);
					sb.delete(0,k);
				
					// break rest into usual flag segments (though generally no flags)
					Vector<Vector<String>> flagSegs=StringUtil.flagSeg(sb.toString());
					result=QueryParser.queryParse(p,query,flagSegs,false);
				}
				else
					result=QueryParser.queryParse(p,sb.toString(),(Vector<Vector<String>>)null,false);
				if (result.equalsIgnoreCase("true"))
					return Boolean.valueOf(true);
				if (result.equalsIgnoreCase("false"))
					return Boolean.valueOf(false);
				return null; // failed to get true/false
			}
		}
		
		// otherwise should get 'left' (a double), condition 'cond', and 'right' (a double)
		if (nc[0]==0 || nc[1]==sb.length()-1) {
			throw new ParserException("'BoolParse': dangling condition");
		}
		Condition condition=getCondition(sb.substring(nc[0],nc[1]+1));
		if (condition==Condition.NULL)
			throw new ParserException("'BoolParse': failed to get 'condition'");
		String left=sb.substring(0,nc[0]).trim();
		String right=sb.substring(nc[1]+1).trim();
		Object leftObj=null;
		Object rightObj=null;
		
		// interpret 'left' as object: Double or String
		if (left.charAt(0)=='{') { // run a command and check for Double or Integer return
			String []grpstr=StringUtil.getGroupedStr(new StringBuilder(left),'{');
			if (grpstr[1]!=null && grpstr[1].length()>0)
				throw new ParserException("'BoolParse': dangling string");
			CallPacket cP=CommandStrParser.valueExecute(p,grpstr[0]);
			if (cP!=null) {
				if (cP.double_vec!=null && cP.double_vec.size()>0)
					leftObj=Double.valueOf(cP.double_vec.get(0));
				else if (cP.int_vec!=null && cP.int_vec.size()>0)
					leftObj=Double.valueOf(cP.int_vec.get(0));
				else if (cP.strValue!=null && cP.strValue.length()>0)
					leftObj=new String(cP.strValue);
				else 
					throw new ParserException("'BoolParse': 'left' failed");
			}
		}
		else {
			try {
				leftObj=Double.parseDouble(left);
			} catch(Exception ex) {
				leftObj=left; // remains as String object
			}
		}
		
		if (leftObj==null) 
			throw new ParserException("'BoolParser': failed left object");

		// interpret 'right' as object: Double or String
		if (right.charAt(0)=='{') { // run a command and check for Double or Integer return
			String []grpstr=StringUtil.getGroupedStr(new StringBuilder(right),'{');
			if (grpstr[1]!=null || grpstr[1].length()>0)
					throw new ParserException("'BoolParse': dangling string");
			CallPacket cP=CommandStrParser.valueExecute(p,grpstr[0]);
			if (cP!=null) {
				if (cP.double_vec!=null && cP.double_vec.size()>0)
					rightObj=Double.valueOf(cP.double_vec.get(0));
				else if (cP.int_vec!=null && cP.int_vec.size()>0)
					rightObj=Double.valueOf(cP.int_vec.get(0));
				else if (cP.strValue!=null && cP.strValue.length()>0)
					rightObj=new String(cP.strValue);
				else 
					throw new ParserException("'BoolParse': 'right' failed");
			}
		}
		else {
			try {
				rightObj=Double.parseDouble(right);
			} catch(Exception ex) {
				rightObj=right; // remains as String object
			}
		}
				
		if (rightObj==null) 
			throw new ParserException("'BoolParser': failed right object");
		
		// now do the comparisons
		if (leftObj instanceof String && rightObj instanceof String) {
			if (leftObj.equals(rightObj))
				return Boolean.valueOf(true);
			else
				return Boolean.valueOf(false);
		}
		if (leftObj instanceof Double && rightObj instanceof Double) {
			double x=(double)leftObj;
			double y=(double)rightObj;
			double diff=Math.abs(x-y);
			if (condition == Condition.EQ) {
				if (diff > TOLER) 
					return Boolean.valueOf(false);
				else
					return Boolean.valueOf(true);
			}
			if (condition == Condition.NE) {
				if (diff > TOLER) 
					return Boolean.valueOf(true);
				else
					return Boolean.valueOf(false);
			}
			if (condition == Condition.GT) {
				if (x > y) 
					return Boolean.valueOf(true);
				else
					return Boolean.valueOf(false);
			}
			if (condition == Condition.GE) {
				if (x > y || diff<TOLER) 
					return Boolean.valueOf(true);
				else
					return Boolean.valueOf(false);
			}
			if (condition == Condition.LT) {
				if (x < y) 
					return Boolean.valueOf(true);
				else
					return Boolean.valueOf(false);
			}
			if (condition == Condition.LE) {
				if (x < y || diff<TOLER) 
					return Boolean.valueOf(true);
				else
					return Boolean.valueOf(false);
			}
			return Boolean.valueOf(false);
		}
		
		// not matching types of objects
		else 
			throw new ParserException("'BoolParse'; left/right different objects");
	}
	
	/**
	 * If trimmed string is a connective, return its type, else
	 * return NULL type.
	 * @param constr String
	 * @return Connective {AND,OR,NOT,NULL}
	 */
	public static Connective getConnective(String constr) {
		String tstr=constr.trim();
		if (tstr.equalsIgnoreCase(".and.")) return Connective.AND;
		if (tstr.equalsIgnoreCase(".or.")) return Connective.OR;
		if (tstr.equalsIgnoreCase(".nor.")) return Connective.NOR;
		if (tstr.equalsIgnoreCase(".not.")) return Connective.NOT;
		if (tstr.equals("!")) return Connective.NOT;
		else return Connective.NULL;
	}
	
	/**
	 * If trimmed string is a condition, return its type, else
	 * return NULL type.
	 * @param condstr String
	 * @return Condition {LT,LE,GT,GE,EQ,NE,NULL}
	 */
	public static Condition getCondition(String condstr) {
		String tstr=condstr.trim();
		if (tstr.equalsIgnoreCase(".eq.")) return Condition.EQ;
		if (tstr.equalsIgnoreCase(".neq.")) return Condition.NE;
		if (tstr.equalsIgnoreCase(".ne.")) return Condition.NE;
		if (tstr.equalsIgnoreCase(".gt.")) return Condition.GT;
		if (tstr.equalsIgnoreCase(".ge.")) return Condition.GE;
		if (tstr.equalsIgnoreCase(".lt.")) return Condition.LT;
		if (tstr.equalsIgnoreCase(".le.")) return Condition.LE;
		else return Condition.NULL;
	}
	
	/**
	 * Return Boolean=true/false depending on whether trimmed string is "true" or
	 * "false".
	 * @param str String
	 * @return Boolean, null on error
	 */
	public static Boolean getBoolean(String str) {
		String tstr=str.trim();
		if (tstr.equalsIgnoreCase("true")) 
			return Boolean.valueOf(true);
		if (tstr.equalsIgnoreCase("false")) 
			return Boolean.valueOf(false);
		return null;
	}
	

	/**
	 * find indices i, k defining next 'connective' in given string
	 * @param sb StringBuilder
	 * @return int[2], null on error
	 */
	public static int []getNextConnective(StringBuilder sb) {
		for (int i=0;i<sb.length()-1;i++) {
			char c=sb.charAt(i);
			if (c=='!') {
				int []ans=new int[2];
				ans[0]=i;
				ans[1]=i;
				return ans;
			}
			if (c=='.') {
				int k=sb.indexOf(".",i+2); // connectives are 2 or 3 characters
				if (k<0 || k==sb.length()-1)
					return null;
				String stuf=sb.substring(i+1,k);
				// could be something else involving '.'
				if (!stuf.equalsIgnoreCase("and") && !stuf.equalsIgnoreCase("or") 
						&& !stuf.equalsIgnoreCase("nor") && !stuf.equalsIgnoreCase("not")) {
					i++;
					continue; // keep looking
				}
				int []ans=new int[2];
				ans[0]=i;
				ans[1]=k;
				return ans;
			}
		} // end of loop
		return null;
	}

	/**
	 * Find indices i, k defining next 'condition' in given string; 
	 * eg. such as ".eq.".
	 * @param sb StringBuilder
	 * @return int[2], null on error
	 */
	public static int []getNextCondition(StringBuilder sb) {
		for (int i=0;i<sb.length()-2;i++) {
			char c=sb.charAt(i);
			if (c=='.') {
				int k=sb.indexOf(".",i+2); // all 'Condition's are 2 characters
				if (k<0 || k==sb.length()-1)
					return null;
				String stuf=sb.substring(i+1,k);
				// could be something else involving '.'
				if (!stuf.equalsIgnoreCase("eq") && !stuf.equalsIgnoreCase("ne") &&
						!stuf.equalsIgnoreCase("ge") && !stuf.equalsIgnoreCase("gt") &&
						!stuf.equalsIgnoreCase("le") && !stuf.equalsIgnoreCase("lt")) {
					i++;
					continue; // keep looking
				}
				int []ans=new int[2];
				ans[0]=i;
				ans[1]=k;
				return ans;
			}
		} // end of loop
		return null;
	}

}
