package parser;
import java.util.HashMap;
import java.util.Stack;
import java.util.Vector;

import complex.Complex;
import complex.MathComplex;

import exceptions.ParserException;

/**
 * The evaluator contains the 'dictionary' and 
 * 'values' hashmaps for evaluating functions of 
 * a complex variable. The dictionary should
 * have only objects implementing the 'word' 
 * interface so that they have a complex 
 * 'evaluate' method.
 */

public class Evaluator {
  private HashMap<String, Object> dictionary = new HashMap<String, Object>();
  private HashMap<String, Complex> values = new HashMap<String, Complex>();
  private int dictCounter=0;

  //12 symbols
  private static String[] terminals = {
      "+", "-", "*", "/", "!", "^", "(", ")",
      "=", "%", ",", ";"};
  //17 functions
  private static String[] functions = {
      "sin", "cos", "tan", "exp", "ln", "log",
      "sinh", "cosh", "tanh", "asin", "acos", "atan", "sqrt",
      "arg","Arg","conj","abs"};
  //8 operators
  private static String[] operators = {
      "+", "-", "*", "/", "!", "^", "%", ","};
  // grouping symbols (not yet used)
  @SuppressWarnings("unused")
private static String[] groupers = {
	  "{", "}", "(", ")", "[", "]", "|"};
  //18 unexpected symbols
  private static String[] unexpected = {
      "`", "~", "@", "#", "&", "_", "|", "[", "]",
      "{", "}", "\""
      , ":", ";", "?", ">", "<", "\\"};

  // Constructor
  public Evaluator() {
  }
  
  /**
   * Puts i, PI, and e in the 'values' hashtable 
   */
  public void init() {
    values.put("i", MathComplex.IM);
    values.put("I", MathComplex.IM);
    values.put("PI", new Complex(Math.PI));
    values.put("Pi", new Complex(Math.PI));
    values.put("pi", new Complex(Math.PI));
    values.put("e", MathComplex.exp(new Complex(1.0)));
  }

  /**
   * Clears the 'dictionary' and 'values' hashtables
   */
  public void reset() {
    dictionary.clear();
    values.clear();
    dictCounter=0;
    init();
  }
  
  public static boolean isTerminal(char c) {
    for (int i = 0; i < terminals.length; i++)
      if (terminals[i].charAt(0) == c)
        return true;
    return false;
  }

  public static boolean isOperator(String s) {
    for (int i = 0; i < operators.length; i++)
      if (operators[i].equals(s))
        return true;
    return false;
  }

  public static boolean isValid(char c) throws Unexpected {
	  if ((int) c==32) return true; // space
    for (int i = 0; i < unexpected.length; i++)
      if (unexpected[i].charAt(0) == c)
        throw new Unexpected();
    if ( (int) c < (int) '!')
      throw new Unexpected();
    return true;
  }

  public static boolean isLetter(char c) {
    if ( (c >= 'A') && (c <= 'Z'))
      return true;
    if ( (c >= 'a') && (c <= 'z'))
      return true;
    else
      return false;
  }

  public static boolean isDigit(char c) {
    if ( ( (c <= '9') && (c >= '0')) || (c == '.'))
      return true;
    return false;
  }

  public static boolean isNumber(String s) {
    try {
      return true;
    }
    catch (NumberFormatException e) {
      return false;
    }
  }

  public static boolean isFunction(String s) {
    for (int i = 0; i < functions.length; i++)
      if (functions[i].equals(s))
        return true;
    return false;
  }

  public static boolean isVariable(String s) {
    if (!isLetter(s.charAt(0)))
      return false;
    return!isFunction(s);
  }

  public static boolean isOpenParenthesis(String s) {
    if (s.compareTo("(") == 0)
      return true;
    return false;
  }

  public static boolean isCloseParenthesis(String s) {
    if (s.compareTo(")") == 0)
      return true;
    return false;
  }

  public static boolean isOperand(String s) {
    return !isFunction(s) && !isOperator(s);
  }

  public static int priority(String s) {
    if (s.compareTo("+") == 0)
      return 1;
    if (s.compareTo("-") == 0)
      return 1;
    if (s.compareTo("*") == 0)
      return 2;
    if (s.compareTo("/") == 0)
      return 2;
    if (s.compareTo("(") == 0)
      return 0;
    else
      return 0;
  }

  /**
   * checks vector v as a unary expression; throws out leading
   * + signs, incorporates - sign if there's a following operand. 
   * Returns true or false.
   */
  private boolean checkUnaryExpression(Vector<String> v) {
    if (v.size() < 2)
      return false;
    String first = (String) v.get(0);
    if (first.compareTo("+") == 0) {
      v.remove(0);
      return true;
    }
    String arg = (String) v.get(1);
    if ( (first.compareTo("-") == 0) && isOperand(arg)) {
      v.remove(1);
      v.remove(0);
      UnaryMinus w = new UnaryMinus(arg);
      String word = getNewWord(w);
      v.add(0, word);
      return true;
    }
    else
      return false;
  }

  /**
   * determines if v(ind-1) v(ind) v(ind+1) consitutes a power 
   * binary expression and if so replaces it with a word. Always
   * check this before checking for other binary expressions.
   */
  private boolean checkPowerExpression(int ind, Vector<String> v) {
//    String st, prev;
    if (v.size() < 3 || v.size()<ind+2)
      return false;
    //try {
    String s0 = (String) v.get(ind-1);
    String s1 = (String) v.get(ind);
    String s2 = (String) v.get(ind+1);
    if (s2.compareTo(" ")==0)
      System.out.println("how come");
    if (isOperand(s0) &&
        (s1.compareTo("^")==0) &&
        isOperand(s2)) {
      v.remove(ind+1);
      v.remove(ind);
      v.remove(ind-1);
      parser.PowerExpression w = new parser.PowerExpression(s0, s2);
      String word = getNewWord(w);
      if(word.compareTo("")==0)
        System.out.println("We are in trouble");
      v.add(ind-1, word);
      return true;
    }
    else
      return false;
    //}
    //catch(ParserException err) {
    //}
  }

  /**
   * determines if v(ind-1) v(ind) v(ind+1) consitutes a valid 
   * binary expression and if so replaces it with a word
   */
  private boolean checkBinaryExpression(int ind, Vector<String> v) {
    if (v.size() < 3 || v.size()<ind+2)
      return false;
    //try {
    String s0 = (String) v.get(ind-1);
    String s1 = (String) v.get(ind);
    String s2 = (String) v.get(ind+1);
    if (isOperand(s0) &&
        isOperator(s1) &&
        isOperand(s2)) {
      v.remove(ind+1);
      v.remove(ind);
      v.remove(ind-1);
      parser.BinaryExpression w = new parser.BinaryExpression(s1, s0, s2);
      String word = getNewWord(w);
      v.add(ind-1, word);
      return true;
    }
    else
      return false;
    //}
    //catch(ParserException err) {
    //}
  }


  /**
   * determines if v(ind) v(ind+1) consitutes a function/arg pair
   * and if so replaces it with a word
   */
  private boolean checkFunctionalExpression(int ind, Vector<String> v) {
    if (v.size() < 2 || v.size()<ind+2)
      return false;
    //try {
    String fun = (String) v.get(ind);
    String arg = (String) v.get(ind + 1);
    if (isOperand(arg) &&
        isFunction(fun)) {
      v.remove(ind + 1);
      v.remove(ind);
      FunctionalExpression f = new FunctionalExpression(fun, arg);
      String word = getNewWord(f);
      v.add(ind, word);
      return true;
    }
    else
      return false;
  }
  
  /**
   * Runs 'analyze', which recursively consolidates expressions.
   * @param v
   */
  public boolean doIt(Vector<String> v) {
    try {
      analyze(v);
    }
    catch (UnmatchedParenthesis m) {
      return false;
    }
    return true;
  }

  /**
   * Recursively analyze the vector v which contains our 
   * expression: consolidate innermost parenthetical expression
   * and analyse parentless stuff.
   */
  private void analyze(Vector<String> v) throws UnmatchedParenthesis {
    int begin = -1;
    int end = -1;
    int counter = 0;
    int maxCounter = -1;
    for (int i = 0; i < v.size(); i++) {
      String s = (String) v.get(i);
      if (isOpenParenthesis(s)) {
        counter++;
        // must be >= otherwise would have from first to last
        if (counter >= maxCounter) {
          maxCounter = counter;
          begin = i;
        }
      }
      if (isCloseParenthesis(s)) {
        counter--;
        if (counter < 0)
          throw new UnmatchedParenthesis();
        if (counter == maxCounter - 1)
          end = i;
      }
    }
    
    if (end<begin || (end>begin && begin==-1))
      throw new UnmatchedParenthesis();
    
    // no parentheses?
    if (begin == -1) {
    	try {
    		analyzeParentLess(v);
    	} catch (Unexpected ue) {
    		// should be different exception
    		throw new UnmatchedParenthesis();
    	}
      return;
    }
    
    // else, inner parentheses run from begin to end
    int size = end - begin - 1;
    Vector<String> v1 = new Vector<String>(size);
    for (int i = 0; i < size; i++)
      v1.add(v.get(i + begin + 1));
    String res;
    try {
    	res = analyzeParentLess(v1);
    } catch (Unexpected ue) {
    	// should be using different exception
    	throw new UnmatchedParenthesis();
    }
    if(res.length()==0)
      System.out.println("We are in trouble here");

    v.subList(begin, end + 1).clear();
    v.add(begin, res);
    analyze(v); // recursion
  }

  /**
   * Analyze segments without parentheses, consolidates with 
   * repeated while loops and returns the single object that 
   * results. (I think this is done only after variables are 
   * set numerically in 'values'.)
   * @param v
   * @return
   * @throws Unexpected
   */
  private String analyzeParentLess(Vector<String> v) throws Unexpected {
    int ind;
    boolean hit=true;
    while (hit && v.size() > 1) {
    	hit=false;
   		while((ind=findFunction(v))>=0) {
   			hit=true;
   			checkFunctionalExpression(ind,v);
   		}
   		while((ind=findPower(v))>=1) {
   			hit=true;
   			checkPowerExpression(ind, v);
   		}
  		if (checkUnaryExpression(v)) hit=true; // should be after powers
    	while((ind=findBinary(v))>=1) {
    		hit=true;
    		checkBinaryExpression(ind, v);
    	}
    	if (!hit) throw new Unexpected();
    }
    return (String) v.get(0);
  }
  
  /**
   * Evaluates the expression. Assumes that v consists of a single element
   * already computed, so it just gets the 'toString' of this element.
   * It must be a double, but a complex is returned with this as real part.
   * @param v Vector
   * @return Complex
   */
  public Complex evaluate(Vector<String> v) {
	if (v.size()<1) return new Complex(0.0); // function not set??
    return evaluate(v.elementAt(0).toString());
  }

  /**
   * Evaluates a string if it is a double, else returns null.
   * @param s
   * @return
   */
  public Complex evaluate(String s) {
    Complex comp;
    try {
      Double c = Double.parseDouble(s);
      return new Complex(c.doubleValue());
    }
    catch (NumberFormatException e) {
      comp = (Complex)values.get(s);
    }
    if (comp==null)
      System.out.println("Not in the dictionary");
    return comp;
  }
  
  /**
   * Puts a new object w in the dictionary and returns its
   * name in the hashtable as a string. 
   * @param w
   * @return
   */
  private String getNewWord(Object w) {
    String s = "z" + Integer.toString(dictCounter++);
    dictionary.put(s, w);
    return s;
  }

  /**
   * Returns index of first symbol * or / operator, or if not
   * present, first + or - sign.
   * @param v
   * @return 
   */
  public static int findBinary(Vector<String> v) {
    for (int i = 0; i < v.size(); i++)
      if ( (v.elementAt(i).toString().compareTo("*") == 0) ||
          (v.elementAt(i).toString().compareTo("/") == 0))
        return i;
    for (int i = 0; i < v.size(); i++)
      if ( (v.elementAt(i).toString().compareTo("+") == 0) ||
          (v.elementAt(i).toString().compareTo("-") == 0))
        return i;
    return -1;
  }

  /**
   * Returns index to first power ^ symbol in the given vector
   * @param v
   * @return
   */
  public static int findPower(Vector<String> v) {
    for (int i = 0; i < v.size(); i++)
      if ( (v.elementAt(i).toString().compareTo("^") == 0))
        return i;
    return -1;
  }
  
  /**
   * Returns index of first function located in the vector v.
   * @param v
   * @return
   */
  public static int findFunction(Vector<String> v) {
    for (int i = 0; i < v.size(); i++)
      if ( (isFunction((String)v.elementAt(i))))
        return i;
    return -1;
  }

  /**
   * Puts the given complex number in the hashtable of 
   * 'values' under the symbol "z" and computes the dictionary.
   * @param z
   */
  public void setVariable(Complex z) {
    values.put("z",z);
    computeDictionary();
  }
  
  /**
   * Puts the given complex number in the hashtable of 'values' 
   * under the given symbol s.
   * @param s
   * @param value
   */
  public void setVariable(String s, Complex value) {
    values.put(s,value);
  }

  /**
   * Goes through the 'dictionary' and puts successively computed
   * numerical values in the 'values' hashtable. 
   *
   */
  public void computeDictionary() {
    for (int i = 0; i < dictionary.size(); i++) {
      String s = "z" + Integer.toString(i);
      Complex result = ( (Word) dictionary.get(s)).evaluate(this);
      if(result==null)
        System.out.println("A value that has not been computed yet is present in the dictionary");
      values.put(s, result);
    }
  }

  // following seem to be debugging aids.
  public static void printV(Vector<String> v) {
	    System.out.println("Printing vector:");
	    for (int i = 0; i < v.size(); i++)
	      System.out.print(v.elementAt(i) + " ");
	    System.out.println();
	  }

  // Don't know what this is for; not in use currently
  // input is a vector of tokens (formula)
  @SuppressWarnings({ "unused" })
private static String inToPostFix(Vector<String> f) {
    Stack<String> stack = new Stack<String>();
    String pFix = "";
    for (int i=0;i<f.size();i++) {
      String token = (String)f.elementAt(i);
      if(isOpenParenthesis(token))
        stack.push(token);
      else if(isCloseParenthesis(token)) {
        token = (String)stack.pop();
        while(!isOpenParenthesis(token)) {
          pFix += token;
          token = (String)stack.pop();
        }
      }
      else if (isOperator(token)) {
        String topToken="";
        if(!stack.isEmpty())
          topToken = (String)stack.peek();
        while((!stack.isEmpty()) && (priority(token)<=priority(topToken))) {
          String tokenOut = (String)stack.pop();
          pFix += tokenOut;
          if(!stack.isEmpty())
            topToken = (String)stack.peek();
        }
        stack.push(token);
      }
      else // char is operand
        pFix += token;
    }
    while(!stack.isEmpty()) {
       String token = (String)stack.pop();
       pFix += token;
     }
    return pFix;
  }

}

class UnrecognizedBinary
    extends ParserException {

	private static final long 
	serialVersionUID = 1L;
}

class UnmatchedParenthesis
    extends ParserException {

	private static final long 
	serialVersionUID = 1L;
}

class TwoOperatorsInARow
    extends ParserException {

	private static final long 
	serialVersionUID = 1L;
}
