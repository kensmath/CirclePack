package util;

import java.io.BufferedReader;
import java.util.Iterator;
import java.util.Vector;

import allMains.CPBase;
import exceptions.ParserException;

/**
 * various static classes for string manipulations, parsing
 */
public class StringUtil {
	
	/**
	 * Given a string (normally a command string), search for and remove
	 * the "-p.." flag if there is one (there should never be more than one),
	 * return the desired pack number or -1
	 * @return int packnum or -1 on failure
	 */
	public static int extractPackNum(StringBuilder sb) {
		int newpnum=-1;
		int k = sb.indexOf("-p");
		
		// 'rest' is pointer to stuff after -p flag
		int rest=sb.indexOf(" ",k+2);
		if (rest<0) rest=sb.length(); // or to end 
		if (sb.length()>= (k+3)) { // continue by checking for variable after '-p'
			if (sb.charAt(k+2)!='_') { // nope, should be single digit
				newpnum = Integer.parseInt(sb.substring(k + 2, k + 3));
			}
			else if (rest>(k+3)) { // have -p_* construction
				try { // read off the variable value
					newpnum=Integer.parseInt(CPBase.varControl.getValue(sb.substring(k+3,rest)));
				} catch(Exception ex) {
					newpnum=-1;
				}
			}
		}
		sb.delete(k, rest);
		if (newpnum < 0 || newpnum >= CPBase.NUM_PACKS) {
			return -1;
		}
		return newpnum;
	}
	
	/**
	 * If trimmed string starts with character c (one of '(', '{', '[', or '<'), 
	 * then return the string properly between it and its matching closing 
	 * character. Return null if nesting associated with c is inconsistent.
	 * @param startstr String
	 * @param c char
	 * @return String[2], string between, string after (trimmed, either possibly empty);
	 * null on syntax error
	 */
	public static String []getGroupedStr(StringBuilder strbld,char c) {
		
		// check c first
		char rc=')';
		if (c=='(') rc=')';
		else if (c=='[') rc=']';
		else if (c=='{') rc='}';
		else if (c=='<') rc='>';
		else return null;
		
		strbld.trimToSize();
		if (strbld.charAt(0)!=c)
			return null;
		int hit=0;
		for (int i=1;i<strbld.length();i++) {
			char ci=strbld.charAt(i);
			if (ci==rc && hit==0) {
				String []retstrs=new String[2];
				retstrs[0]=strbld.substring(1,i).trim();
				if (i==strbld.length()-1)
					retstrs[1]=""; // empty string
				else 
					retstrs[1]=strbld.substring(i+1).trim();
				return retstrs;
			}
			if (ci==rc)
				hit--;
			if (ci==c)
				hit++;
		}
		return null;
	}
	
	
	/** 
	 * Search str for occurrence of '(n,m)' or '(n m)' and return strings 
	 * 'n' and 'm'; these are usually integers, but calling routine converts 
	 * them. Return null on error. Careful that expressions with parens or 
	 * curly brackets haven't been broken apart because of intervening space;
	 * see 'CommandStrParser.string2vec'.
	 * @param str
	 * @return
	 */
	public static String[] parens_parse(String str) {
		int a,b;

		if ((a=str.indexOf('('))==-1 || (b=str.indexOf(')'))==-1 || b<(a+3))
			return null;
		str=str.substring(a+1,b);
		str=str.replace(',',' '); // turn comma into whitespace
		String par_str[]=str.split("\\s+");
		if (par_str.length!=2) return null;
		return par_str;
	}
	
	/**
	 * Parse 'PackExtender' initials at the start of a command. These
	 * have the form '|bq|' (case is ignored). 
	 * @param str, already trimmed
	 * @return String[2], null if malformed
	 *    [1] = abbreviation, lower case; 
	 *    [2] = String with extender indication removed, trimmed,
	 *          and adjusted for any variable substitutions
	 */
	public static String []getXtender(String str) {
		if (str==null || str.length()<5) return null;
		if (str.charAt(0)!='|') // must start with '|' 
			return null; 
		int k=str.indexOf('|',1);
		if (k<0) // must have a second '|'
			return null;
		for (int j=1;j<k;j++) {
			char c=str.charAt(j);
			if (!Character.isLetterOrDigit(c)) // allow letters and digits
				return null;
		}
		String []sendbacks=new String[2];
		sendbacks[0]=str.substring(1,k).toLowerCase(); // abbrev, lower case
		StringBuilder sbld=new StringBuilder(str.substring(k+1).trim());
		
		// want to pick off command, send rest to varSub if '_' occurs
		k=sbld.indexOf(" ");
		if (k<=0 || sbld.indexOf("_",k+1)<=0) {
			sendbacks[1]=sbld.toString(); 
			return sendbacks;
		}
		
		// set end to varSub
		sendbacks[1]=new String(sbld.substring(0,k+1)+" "+StringUtil.varSub(sbld.substring(k+1)));
		return sendbacks;
	}

	/**
	 * Called for commands starting with 'for' or 'FOR' (which
	 * is removed and str is trimmed before this call). 
	 * This call picks off the specification string. (Processing 
	 * in 'getForSpec' is called for separately.)
	 * str must start with specification of form:
	 * (1) paren '(....)' segment (possibly with white space) or
	 * (2) 'n:m' format, no whitespace (deprecated)
	 * @param str, StringBuffer following 'for' or 'FOR'.
	 * @return int: >0, then gives index of last char in iteration 
	 *              specification (so calling routine can pick it off): 
	 *              <=0 on error.
	 */
	public static int getForString(StringBuffer str) {
		int k=0;
		// bypass initial whitespace
		while (k<str.length() && 
				Character.isWhitespace(str.charAt(k))){
			k++;
		}
		
		// preferred format: '( ....)'
		if (str.charAt(0)=='(') {
			k=str.indexOf(")",k);
			if (k<0) 
				return -1; // faulty format
			return k+1; // so far, looks okay
		}
		
		// go to first non-whitespace
		while (k<str.length() && 
				!Character.isWhitespace(str.charAt(k))){
			k++;
		}
		if (k<str.length())
			return k+1;
		return 0;
	}
	
	/**
	 * Process a given 'for' specification string. Should be
	 * trimmed string ('for' or 'FOR' has been removed) with 
	 * one of these forms:
	 * (1) "([X:=]start,end,inc)" (preferred), inc!=0
	 * (2) "n,m", no whitespace (deprecated), n,m integers
	 * 
	 * @param str, trimmed String
	 * @return ForSpec or null on error
	 */
	public static ForSpec getForSpec(String str) {
		ForSpec forSpec=new ForSpec();
		int k;
		String start_str=null;
		String end_str=null;
		String delta_str=null;
		try {
			// deprecated form "n:m" or "n,m"
			if (str.charAt(0)!='(') {
				k=str.indexOf(',');
				if (k<0) k=str.indexOf(':'); // old form
				if(k<=0 || k==str.length()-1) // malformed 
					return null;
				
				// process first integer
				start_str=str.substring(0,k).trim();
				end_str=str.substring(k+1).trim();
				
				// set increment
				if (forSpec.end<forSpec.start) 
					delta_str =new String("-1.0");
				else
					delta_str=new String("1.0");
			}
			
			else { // else standard form, 
				// trim '(' and ')'
				str=str.substring(1,str.length()-1);

				// split at ','s
				String segs[]=str.split(",");
				if (segs.length!=3) 
					return null;
				for (int j=0;j<3;j++) {
					segs[j]=segs[j].trim();
					if (segs[j].length()==0)
						return null;
				}
				start_str=new String(segs[0]);
				end_str=new String(segs[1]);
				delta_str=new String(segs[2]);
			}
			
			// start_str may begin with variable, form 'X:={start}'.
			if (Character.isLetter(start_str.charAt(0))) {
				k=start_str.indexOf(":=");
				// is there a variable name? must start with character
				if (!Character.isLetter(start_str.charAt(0))) 
					return null;
				forSpec.varName=new String(start_str.substring(0,k));
				start_str=start_str.substring(k+2);
				if (start_str.length()==0) // empty?
					return null;
			}
			
			// any term may be a variable
			String getvar=getVarName(start_str);
			if (getvar!=null) { // variable? get its value string
				start_str=CPBase.varControl.getValue(start_str);
			}
			forSpec.start=Double.parseDouble(start_str);
			getvar=getVarName(end_str);
			if (getvar!=null) { // variable? get its value string
				end_str=CPBase.varControl.getValue(end_str);
			}
			forSpec.end=Double.parseDouble(end_str);
			getvar=getVarName(delta_str);
			if (getvar!=null) { // variable? get its value string
				delta_str=CPBase.varControl.getValue(delta_str);
			}
			forSpec.delta=Double.parseDouble(delta_str);
			return forSpec;
		} catch (Exception ex) {
			return null;
		}
	}
	
	/**
	 * Identify and pull out variable name from string starting
	 * at index k. Character at index k should be '_', following 
	 * character must be letter, preceding character must not be
	 * a letter, a number, or ';' or '_'. 
	 * Then grab all contiguous characters that are letters/digits.
	 * @param str String
	 * @param k int (so charAt(k)='_')
	 * @return String, variable name, null on error or not variable
	 */
	public static String isVarName(String str,int k) {
		return isVarName(new StringBuilder(str),k);
	}
	
	/**
	 * Identify and pull out variable name from string starting
	 * at index k. Character at index k should be '_', following 
	 * character must be letter, preceding character must not be
	 * a letter, a number, or ';' or '_'. 
	 * Then grab all contiguous characters that are letters/digits.
	 * @param str @see StringBuilder,
	 * @param k int (so charAt(k)='_')
	 * @return String, variable name, null on error or not variable
	 */
	public static String isVarName(StringBuilder str,int k) {
		if (str==null || str.length()<(k+2) || str.charAt(k)!='_' ||
				!Character.isLetter(str.charAt(k+1)))
			return null;
		if (k!=0) { // what can precede the '_'?
			char b=str.charAt(k-1);
			if (Character.isLetter(b) || Character.isDigit(b) 
					|| b=='_' || b==';')
				return null;
		}
		int j=k+1;
		char c=str.charAt(j);
		// go until you get to end, whitespace, or various characters,
		// TODO: are there any other legal symbols that end a variable name?
		while (j<str.length() && (Character.isLetter(c) || Character.isDigit(c))) { 
				
//	old description??		!Character.isWhitespace(c) && c!=')'
//			&& c!='(' && c!='[' && c!=']' && c!=';' && c!='}' && c!='{'
//				&& c!='_' && c!='*' && c!='/' && c!='\\' )
			
				j++;
				if (j<str.length()) // otherwise, will kick out anyway
					c=str.charAt(j);
		}
		return str.substring(k+1,j); 
	}
	
	/**
	 * search for variable substrings, replace them by their
	 * string values. Recurse. 
	 * @param str String
	 * @return String
	 */
	public static String varSub(String str) {
		// TODO: need to make this more bulletproof, 
		//       deeper recursion
		int k=0;
		if (str==null || str.length()<2 || (k=str.indexOf('_'))<0 || k>(str.length()-2)) 
			return new String(str);
		// try 4 recursive steps
		String frag=null;
		StringBuilder outbuf=new StringBuilder();
		boolean hit=true;
		for (int j=1;(j<5 && hit);j++) {
			hit=false;
			if (str.indexOf('_')<0) 
				return new String(str);
			int oldk=k=0;
			while (k<str.length()) {
				if (str.charAt(k)=='_' && (frag=isVarName(str,k))!=null) {
					hit=true;
					outbuf.append(str.substring(oldk,k));
					oldk=k=k+frag.length()+1;
					String apd=CPBase.varControl.getValue(frag);
					if (apd==null) 
						throw new ParserException("Attempt to treat '"+frag+"' as variable; unassigned");
					outbuf.append(apd);
				}
				else k++;
			}
			outbuf.append(str.substring(oldk,str.length()));
			str=outbuf.toString();
		}
		return outbuf.toString();		
	}

	/**
	 * Find first proper '_*' variable name specification,
	 * starting at index 0. Char following k must be letter. 
	 * The "name" is the sequence of contiguous letter/digit 
	 * characters immediately following '_'.
	 * See 'isVarName'.
	 * @param str, input string
	 * @return variable name as String or null on failure
	 */
	public static String getVarName(String str) {
		return getVarName(str,0);
	}
	
	/**
	 * Find first proper '_*' variable name specification,
	 * starting at index k. This means character preceding
	 * k must be non-letter, non-digit (or k==0), and char following k
	 * must be letter. The "name" is the sequence of contiguous 
	 * letter/digit characters immediately following '_'.
	 * See 'isVarName'.
	 * @param str, input string
	 * @return variable name as String or null on failure
	 */
	public static String getVarName(String str,int k) {
		int len;
		if (str==null || (len=str.length())<2) return null;
		if (k==0 && Character.isLetter(str.charAt(1)))
				return isVarName(str,k);
		else k++;
		k=str.indexOf('_',k);
		String name=null;
		// keep looking at next '_' character preceeded by non-letter
		while (k>0 && k<(len-2)) {
			if (Character.isWhitespace(str.charAt(k-1))) {
				if ((name=isVarName(str,k))!=null)
					return name;
			}
			k=str.indexOf('_',k);
		}
		return null;
	}
	
	/**
	 * Search for brackets '[n]', eg, vlist[4], flist[n], etc. Return 
	 * the enclosed string, calling routine has to check whether it's 
	 * the right type of return --- int, string, etc. Note: between 
	 * brackets there can be no white space. Return null if error or
	 * no brackets.
	 * @param String str
	 * @return String
	 */
	public static String brackets(String str) {
		int a,b;
		if ((a=str.indexOf('['))==-1 || (b=str.indexOf(']'))==-1 || b<(a+2))
			return null;
		String bracket_str[]=str.substring(a+1,b).split("\\s+");
		if (bracket_str==null || bracket_str.length>1) {
			throw new ParserException("Illegal or empty brackets in string");
		}
		return bracket_str[0];
	}
	
	/**
	 * Break a string into vector of 'flag segment' vectors, groups between successive
	 * command flags; thus all except possibly the first element will start with a flag.
	 * @param str
	 * @return always return a vector, even if empty
	 */
	public static Vector<Vector<String>> flagSeg(String str) {
		Vector<String> vec=string2vec(str);
		return flagSeg(vec);
	}
	
	/**
	 * Break a vector of strings into 'flag segment' subvectors, groups 
	 * between successive command flags; thus all except possibly the 
	 * first element will start with a flag.
	 * @param vec vector of strings
	 * @return vector of vector of strings, may be empty 
	 */
	public static Vector<Vector<String>> flagSeg(Vector<String> vec) {
		int len=0;
		if (vec==null || (len=vec.size())==0)
			return new Vector<Vector<String>>(0);
		Vector<Vector<String>> output=new Vector<Vector<String>>(len);
		int lasthit=0;
		int n=0;
		Vector<String> newSeg;
		while (n<len) {
			if (isFlag(vec.get(n)) && n>lasthit) {
				newSeg=new Vector<String>(n-lasthit);
				for (int i=0;i<(n-lasthit);i++)
					newSeg.add(vec.get(lasthit+i));
				  output.add(newSeg);
				  lasthit=n;
			}
			n++;
		}
		if (n>lasthit) {
			newSeg=new Vector<String>(n-lasthit);
			for (int i=0;i<(n-lasthit);i++)
				newSeg.add(vec.get(lasthit+i));
			output.add(newSeg);
		}
		return output;
	}

	/**
	 * True if string is a flag; that is, starts with '-' and is not a number.
	 * NOTE: this doesn't catch some complex numbers, like '-i*25' or '-I'.
	 * @param str
	 * @return boolean
	 */
	public static boolean isFlag(String str) {
		str=str.trim();
		if (!str.startsWith("-") || str.length()==1) return false;
		char c=str.charAt(1);
		if (c=='.' || (c>='0' && c<='9')) return false;
		return true;
	}

	/** 
	 * Utility to convert string into vector of substrings (by whitespace).
	 * Return empty vector if nothing is found. 
	 * (TODO: not very sophisticated; can't handle nesting, etc.)
	 * @param str String
	 * @param bfix boolean: true, reattach substrings inadvertently
	 * broken between matching parents or curly brackets.
	 * @return Vector<String>, empty on error or nothing found
	 */
	public static Vector<String> string2vec(String str,boolean bfix) {
		  
		// matching pairs of parens and of curly brackets.
		Vector<String> vec= string2vec(str);
		if (!bfix || (!str.contains("(") && !str.contains("{"))) 
			return (vec);
		  
		// put broken pairs back together.
		Vector<String> newvec= new Vector<String>(vec.size());
		boolean fixed=true;
		  String nxt;
		  int k;
		  for (int n=0;n<vec.size();n++) {
			  nxt=(String)vec.get(n);
			  if ((k=nxt.indexOf('('))>=0) {
				  if (nxt.length()==k+1 || !nxt.substring(k+1).contains(")")) { // look downstream to complete
					  fixed=false;
					  int m=n+1;
					  while(m<vec.size()) {
						  String ths=(String)vec.get(m);
						  if (ths.contains(")")) {
							  String remade=new String(nxt); // remade string; substrings with spaces
							  for (int j=n+1;j<=m;j++) 
								  remade=remade.concat(" "+(String)vec.get(j));
							  newvec.add(remade);
							  n=m; // set beyond vector spot we've used
							  m=vec.size(); // kick out of 'while'
							  fixed=true;
						  }
						  m++;
					  }
					  if (!fixed) {
						  throw new ParserException("Unmatched '(' in expression.");
					  }
				  }
				  else newvec.add(nxt);
			  }
			  else newvec.add(nxt);
		  }
		  vec=newvec;
		  newvec=new Vector<String>(vec.size());
		  	  
		  // repeat same thing with '{' '}'
		  for (int n=0;n<vec.size();n++) {
			  nxt=(String)vec.get(n);
			  if ((k=nxt.indexOf('{'))>=0) {
				  if (nxt.length()==k+1 || !nxt.substring(k+1).contains("}")) { // look downstream to complete
					  fixed=false;
					  int m=n+1;
					  while(m<vec.size()) {
						  String ths=(String)vec.get(m);
						  if (ths.contains("}")) {
							  String remade=new String(nxt); // remade string; substrings with spaces
							  for (int j=n+1;j<=m;j++) 
								  remade=remade.concat(" "+(String)vec.get(j));
							  newvec.add(remade);
							  n=m; // set beyond vector spot we've used
							  m=vec.size(); // kick out of 'while'
							  fixed=true;
						  }
						  m++;
					  }
					  if (!fixed) {
						  throw new ParserException("Unmatched '{' in expression.");
					  }
				  }
				  else newvec.add(nxt);
			  }
			  else newvec.add(nxt);
		  }
		  return newvec;
	  }
		  
	/** 
	 * Utility to convert string into vector of substrings (by whitespace).
	 * Return empty vector if nothing is found. see also 'string2vec(String,boolean)' 
	 * (TODO: not very sophisticated; can't handle nesting, etc.)
	 * @param str String
	 * @return Vector<String>, empty on error or nothing found
	 */
	  public static Vector<String> string2vec(String str) {
		  Vector<String> vec= new Vector<String>(10);
		  if (str!=null) {

			  // expand any variable expressions: indicated by " _{vname}"
			  if (str.length()>0 && (str.charAt(0)=='_' || str.indexOf(" _")>=0)) {
				  StringBuilder sbuf=new StringBuilder(str);
				  int k=-1;
				  int m=-1;
				  // recursive search for variables
				  int safety=6; 
				  while (safety>0 && 
						  (sbuf.charAt(0)=='_' || (k=sbuf.indexOf(" _")+1)>=1)) {
					  safety--;
					  // first character
					  if (sbuf.charAt(0)=='_')
						  k=0;
					  // last character? just remove
					  if (k==sbuf.length()-1)
						  sbuf.deleteCharAt(k);
					  else { // name ends with space or end of string
						  m=sbuf.indexOf(" ",k+1);
						  if (m<0)
							  m=sbuf.length();
						  if (m==(k+1)) // just toss
							  sbuf.replace(k,k+1," ");
						  String instr=sbuf.substring(k+1,m);
						  // may have repeat '_' due to user misuse
						  while (instr.length()>0 && instr.charAt(0)=='_')
							  instr=instr.substring(1);
						  String rstg=null;
						  if (instr.length()>0) 
							  rstg=(String)CPBase.varControl.getValue(instr);
						  if (rstg==null)
							  throw new ParserException("variable '_"+instr+"' is not defined");
						  sbuf.replace(k,m,rstg);
					  }
				  } // end of while
				  str=sbuf.toString();
			  }
			  
			  // split the string at whitespace, eliminate blanks
			  String strFrags[]=str.split("\\s+"); // split at ' ' (spaces)
			  for (int m=0;m<strFrags.length;m++) {
				  if (strFrags[m].trim().length()>0)
					  vec.add(strFrags[m].trim());
			  }
		  }
		  return vec; 
	  }
	  
	  /**
	   * Get initial string and convert to an integer. Throw DataException.
	   * In future, may have alternative ways to indicate integer.  
	   * @param str
	   * @return
	   */
	  public static int getOneInt(String str) throws ParserException {
		  String []pstr=str.split("[ ]"); // split on 'space'
		  int n;
		  try {
			  n=Integer.parseInt(pstr[0]);
		  } catch (Exception ex) {
			  throw new ParserException("string not an integer");
		  }
		  return n;
	  }
	  
	  /**
	   * Get initial string and convert to an integer. Throw DataException.
	   * In future, may have alternative ways to indicate integer.  
	   * @param str
	   * @return
	   */
	  public static int getOneInt(Vector<Vector<String>> segs) 
	  throws ParserException {
		  try {
			  Vector<String> items=(Vector<String>)segs.get(0);
			  return getOneInt((String)items.get(0));
		  } catch (Exception ex) {
			  throw new ParserException("string not an integer");
		  }
	  }
	  
	  /**
	   * Get initial string and convert to a double.
	   * In future, may have alternative ways to indicate double.
	   * @param str
	   * @return double
	   * @throws PaserException
	   */
	  public static double getOneDouble(String str) throws ParserException {
		  String []pstr=str.split("[ ]");
		  double x;
		  try {
			  x=Double.parseDouble(pstr[0]);
		  } catch (Exception ex) {
			  throw new ParserException("string not a double");
		  }
		  return x;
	  }
	  
	  /**
	   * Get initial string and convert to a double.
	   * In future, may have alternative ways to indicate double.
	   * @param segs, Vector<Vector<String>>
	   * @return double
	   * @throws PaserException
	   */
	  public static double getOneDouble(Vector<Vector<String>> segs) 
	  throws ParserException {
		  try {
			  Vector<String> items=(Vector<String>)segs.get(0);
			  return getOneDouble((String)items.get(0));
		  } catch (Exception ex) {
			  throw new ParserException("string not a double");
		  }
	  }
	  
	  /**
	   * Given a 'flagSegs' type vector of vectors of strings, this
	   * returns just the first string. If it fails, return null.
	   * @param segs
	   * @return
	   */
	  public static String getOneString(Vector<Vector<String>> segs) { 
		  String str=null;
		  try {
			  Vector<String> firstvec=(Vector<String>)segs.get(0);
			  str=(String)firstvec.get(0);
		  } catch(Exception ex) {
			  return null;
		  }
		  return str;
	  }
	  
	  /** 
	   * If parentheses pairs are broken (e.g., whitespace between),
	   * then may have to recombine some of strings, or on error, 
	   * throw the rest away. 
	   * NOTE: not used, see string2vec (which handles curly brackets also)
	   */
	  public static Vector<String> ckBrokenBraces(Vector<String> vec) {
		  String ns;
		  for (int m=0;m<vec.size();m++) {
			  ns=(String)vec.get(m);
			  int hit=m;
			  if (ns.contains("(") && !ns.contains(")")) { // broken
				  for (int k=m+1;k<vec.size() && hit==m;k++) {
					  if (vec.get(k).contains(")")) {
						  hit=k; // seems that we found matching paren
						  if (vec.get(k).contains("(")) { // oops, error
							  hit=-1;
						  }
					  }
				  }
				  if (hit==-1 || hit==m) { // malformed, remove from m to end
					  for (int j=vec.size()-1;j>=m;j--)
						  vec.remove(j);
				  }
			  }
		  }
		  return vec;
	  }
	  
	/**
	 * This finds and trims string strictly between outermost curly braces '{string}',
	 * null on syntax error, eg., mismatch, faulty nesting, etc. Returned string
	 * may be empty; calling routine has to decide if that's okay.
	 * 
	 * @param orig_str String
	 * @return String between '{' and '}', null on matching error
	 */
	public static String getBracesString(String str) {
		int firstl = str.indexOf('{');
		if (firstl < 0)
			return null;
		int depth = -1;
		int indx = firstl + 1;
		while (indx < str.length()) {
			char n = str.charAt(indx);
			if (n == '{')
				depth--;
			else if (n == '}') {
				depth++;
				if (depth == 0) { // found closing '{'
					String newstr = str.substring(firstl + 1, indx);
					if (newstr.length() == 0)
						return null;
					return newstr.trim();
				}
			}
			indx++;
		} // end of while

		return null; // didn't find matching '{..}'
	}
	  
	  /** 
	   * Reconstitute (with separating spaces) a string from a vector of
	   * vectors of strings. Return null if essentially empty.
	   * @param segs
	   * @return
	   */
	  public static String reconstitute(Vector<Vector<String>> segs) {
		  int count=0;
		  String restring=new String("");
		  if (segs==null)
			  return restring;
    	  Iterator<Vector<String>> its=segs.iterator();
    	  while (its.hasNext()) {
    		  Iterator<String> istr=((Vector<String>)its.next()).iterator();
    		  while (istr.hasNext()) {
    			  restring=restring.concat((String)istr.next()+" ");
    			  count++;
    		  }
    	  }
          if (count==0 || restring.trim().length()==0) return null;
          return restring;
	  }
	  
	  /** 
	   * Reconstitute a single string (with separating spaces)
	   * from a vector of strings. E.g., getting filenames with
	   * blanks in them.
	   * @param items
	   * @return single trimmed string, null if essentially empty
	   */
	  public static String reconItem(Vector<String> items) {
		  if (items==null || items.size()==0)
			  return null;
		  StringBuilder bufstr=new StringBuilder("");
		  int count=0;
    	  Iterator<String> its=items.iterator();
    	  while (its.hasNext()) {
    		  bufstr.append(its.next());
    		  if (its.hasNext())
    			  bufstr.append(" ");
    		  count++;
    	  }
    	  String str=bufstr.toString().trim();
          if (count==0 || str.length()==0) return null;
          return str;
	  }
	  
	  /**
	   * return first open and closed quoted substring.
	   * @param instr
	   * @return string, null on failure
	   */
	  public static String getQuoted(String instr) {
		  if (instr==null) return null;
		  instr=instr.trim();
		  int k;
		  if ((k=instr.indexOf('"'))<0 || instr.length()<k+3) return null;
		  instr=instr.substring(k+1);
		  int first=instr.indexOf('"');
		  if (first<=1) return null;
		  return instr.substring(0,first);
	  }
	  
	  /**
	   * Return the first string (delineated by whitespace) in 'inbuffer' 
	   * @param str String
	   * @return initial String, null on error
	   */
	  public static String grabNext(String str) {
		  try {
			  return grabNext(new StringBuilder(str));
		  } catch (Exception ex) {
			  return null;
		  }
	  }
	  
	  /**
	   * Return the first string (delineated by whitespace) in 'inbuffer' 
	   * and delete it from 'inbuffer'.
	   * @param inbuffer, StringBuilder, will be modified
	   * @return initial string (segment of non-whitespace); null on error or
	   * if 'inbuffer' is empty 
	   */
	  public static String grabNext(StringBuilder inbuffer) {
		  if (inbuffer==null || inbuffer.length()==0)
			  return null;
		  int k=0;
		  // delete leading whitespace characters (<=' ')
		  while (k<inbuffer.length()&& inbuffer.charAt(k)<=' ') k++; // means whitespace
		  if (k>0 && k<inbuffer.length()) inbuffer.delete(0,k);
		  if (inbuffer.length()==0) return null;
		  k=inbuffer.indexOf(" ");
		  String ans=null;
		  if (k<0) {
			  ans=inbuffer.toString();
			  k=inbuffer.length();
		  }
		  else {
			  ans=inbuffer.substring(0,k);
		  }
		  inbuffer.delete(0,k);
		  return ans;
	  }
  
	  /**
	   * Return the next line from the file which is non-empty
	   * and (if 'noComment' is true) does not start with '#'.
	   * Return null if end of file is reached; ourNextLine
	   * catches IOExceptions.
	   * TODO: see if there are other uses for this call
	   * @param reader
	   * @param boolean noComment: true ignore # lines
	   * @return trimmed String or null
	   */
	  public static String ourNextLine(BufferedReader reader,boolean noComment) {
		  String line=ourNextLine(reader);
		  // skip
		  while (noComment && line!=null && line.trim().startsWith("#")) {
			  line=ourNextLine(reader);
		  }
		  return line;
	  }
	  
	  /**
	   * Return the next non-empty line from file; return null if
	   * end of file is reached, (this catches IOExceptions)
	   * @param reader BufferedReader
	   * @return trimmed String or null
	   */
	  public static String ourNextLine(BufferedReader reader) {
		  String line;
		  try {
			  do {
				  if((line = reader.readLine())!=null) {
					  line=line.trim();
				  }
			  } while(line!=null && line.equals(""));
		  } catch(Exception ex) {
			  return null;
		  }
		  return line;
	  }

	  /**
	   * Strings of form '-q{p}' or '-q {p}' (old form) are
	   * parsed to return pack number. 
	   * NOTES: 
	   *   Calling routine might want to check if result is the same 
	   *     as the current packing
	   * @return pack number, -1 on error
	   */
	  public static int qItemParse(Vector<String> itm) {
		  if (itm==null || itm.size()==0)
			  return -1;
		  int ans=qFlagParse(itm.get(0));
		  if (ans==-2) { // old form with space?
			  int pnum=-1;
			  if (itm.size()>2)
				  return -1;
			  try {
				  pnum=Integer.parseInt(itm.get(1));
				  if (pnum<0 || pnum>CPBase.NUM_PACKS)
					  return -1;
				  return pnum;
			  } catch(Exception ex) {
				  throw new ParserException("format error in '-q' flag attempt.");
			  }
		  }

		  return ans; // error or pack number
	  }
	  
	  /**
	   * Strings of form '-q{p}' indicate packings in various commands; 
	   * this methods parses these (depending on how many packings exist)
	   * and returns the packNum or -1 on error.
	   * NOTES: 
	   *   Calling routine might want to check if result is the same 
	   *     as the current packing
	   *   Old form was '-q {}' (with space); return -2 so
	   *     parent program can check.
	   * @return pack number, -1 on error
	   */
	  public static int qFlagParse(String str) {
		  if (!str.startsWith("-q")) return -1;
		  // might be old form
		  if (str.length()<3) return -2;
    	  int qnum=Integer.parseInt(str.substring(2));
    	  if (qnum<0 || qnum>=CPBase.NUM_PACKS) {
    		  return -1;
    	  }
    	  return qnum;
	  }
	  
	  /**
	   * Break a 'setBuilderParser' type string into segments by finding 
	   * '&&' and '||' instances. These connectives are kept with the 
	   * following segment (as are any '!' negation indicators.
	   * @param str
	   * @return vector of segments or null on error
	   */
	  public static Vector<String> setB_segments(String str) {
		  Vector<String> segs=new Vector<String>(2);
		  str=str.trim();
		  if (!str.contains("&&") && !str.contains("||")) {
			  segs.add(str);
			  return segs;
		  }
		  char c=str.charAt(0);
		  if (c=='&' || c=='|') return null; // can't start with connective
		  while (str.contains("&&") || str.contains("||")) {
			  int a=str.indexOf("&&",2);
			  int b=str.indexOf("||",2);
			  if (a>=3 && (b<0 || a<b)) {
				  segs.add(str.substring(0,a));
				  str=str.substring(a);
			  }
			  else if (b>=3 && (a<0 || b<a)) {
				  segs.add(str.substring(0,b));
				  str=str.substring(b);
			  }
			  else { // connective is at start only
				  segs.add(str);
				  return segs;
			  }
		  }
		  return segs;
	  }
	  
	  /** 
	   * Return substring of contiguous digits in 'str' starting
	   * at 'startIndx'. 'null' if no digits.
	   * @param strb, source StringBuilder
	   * @param startIndx
	   * @return substring or null on error of no digits
	   */
	  public static String getDigitStr(StringBuilder strb,int startIndx) {
		  int k=startIndx;
		  if (strb==null || k<0 || strb.length()<=k) 
			  return null;
		  while (k<strb.length()) {
			  if (!Character.isDigit(strb.charAt(k)))
				  break;
			  k++;
		  }
		  if (k==startIndx) return null;
		  return strb.substring(startIndx,k);
	  }
	  
	  /** 
	   * Return substring of contiguous digits in 'str' starting
	   * at 'startIndx'. 'null' if no digits.
	   * @param str, source String
	   * @param startIndx
	   * @return substring or null on error of no digits
	   */
	  public static String getDigitStr(String str,int startIndx) {
		  int k=startIndx;
		  if (str==null || k<0 || str.length()<=k) 
			  return null;
		  while (k<str.length()) {
			  if (!Character.isDigit(str.charAt(k)))
				  break;
			  k++;
		  }
		  if (k==startIndx) return null;
		  return str.substring(startIndx,k);
	  }

	  /**
	   * Find/return an initial substring beginning/ending with '"'
	   * or with ''', but there may be escaped '"'s or '''s which we 
	   * have to ignore as we look for the final '"' or '''. If a 
	   * literal substring is found, it is deleted from the stringbuffer.
	   * @param strbuf, StringBuilder, modified if string is found
	   * @return initial 'quoted' String, null on error
	   */
	  public static String find_literal(StringBuilder strbuf) {
		  if (strbuf==null || strbuf.length()==0)
			  return null;
		  try {
			  int k=0;
			  while (k<strbuf.length() && strbuf.charAt(k)<=' ') k++;
			  char c=' ';
			  if (k==strbuf.length() || ((c=strbuf.charAt(k))!='\"' && c!='\''))
				  return null;
			  if (k>0) strbuf.delete(0,k+1); // remove white space
			  else if (k==0) strbuf.delete(0, 1); // remove '"' or '''.
			  if (c=='\'') 
				  k=strbuf.indexOf("\'");
			  else 
				  k=strbuf.indexOf("\"");
			  if (k<0) return null; // no closing '"' or '''
			  String str=strbuf.substring(0,k).replaceAll("\\\\n","\n").
			  	replaceAll("\\\\t","\t").replaceAll("\\\\'","\'");
			  strbuf.delete(0,k+1);
			  return str;
		  } catch (Exception ex) {
			  System.err.println("find_literal error: string was: "+strbuf);
			  return null;
		  }
	  }

	/**
	   * Check if flag sequence has filename-type flags as last sequence;
	   * that is, last sequence starts with '-f', '-a', or '-s' for
	   * file, append, or script.
	   * 
	   * See 'CPFileManager.trailingFile' for getting filenames.
	   * 
	   * @param fs, Vector<Vector<String>>, flag sequence
	   * @return boolean
	   */
	  public static boolean ckTrailingFileName(Vector<Vector<String>> fs) {
		  if (fs==null || fs.size()==0) return false;
		  Vector<String> itm=fs.get(fs.size()-1);
		  if (itm==null || itm.size()==0) return false;
		  String str=itm.get(0);
		  if (str.startsWith("-a") || str.startsWith("-f") || str.startsWith("-s"))
			  return true;
		  return false;
	  }
}
