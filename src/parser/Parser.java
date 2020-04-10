package parser;
import java.util.Vector;

import complex.Complex;

public class Parser {
  private String parsedString="";
  private Vector<String> v=new Vector<String>();
  private Evaluator evaluator;
  
  // Constructor
  public Parser() {
    evaluator = new Evaluator();
    evaluator.init();
  }
  
  /**
   * recursively parse 'parsedString'
   *
   */
  private void parse() {
	parsedString=parsedString.trim();
    if(parsedString.length()==0)
      return;
    char c = parsedString.charAt(0);
    try {
    	if (Evaluator.isValid(c)) { 
    		if (Evaluator.isDigit(c))
    			v.add(readNumber());
    		else if (Evaluator.isLetter(c))
    			v.add(readLetters());
    		else if (Evaluator.isTerminal(c)) {
    			String t = "";
    			t += c;
    			v.add(t);
    			parsedString=parsedString.substring(1);
    		}
    	}
        parse();
    }
    catch(Unexpected e) {
      System.out.println("Unexpected character");
      v.removeAllElements();
      return;
    }
  }

  /**
   * Read numerical characters from the parsedString until a
   * terminal character is encountered; remove results from
   * parsedString and return number as a substring.
   * @return
   */
  private String readNumber() {
    String temp="";
    char c;
    int i=0;
    while((i!=parsedString.length()) && (!Evaluator.isTerminal(c = parsedString.charAt(i)))) {
      temp += c;
      i++;
    }
    parsedString=parsedString.substring(i);
    return temp;
  }
  
 /**
   * Read letters from the parsedString until a
   * terminal character is encountered; remove results from
   * parsedString and return them.
   * @return
   */
  private String readLetters() {
    String temp = "";
    char c;
    int i = 0;
    while ( (i != parsedString.length()) &&
           (!Evaluator.isTerminal(c = parsedString.charAt(i)))) {
      temp += c;
      i++;
    }
    parsedString = parsedString.substring(i);
    return temp;
  }
  
  /**
   * Given a fresh function expression as a string, do the
   * standard replacements, clear out the old vector, reset,
   * parse the new expression, then send it for recursive
   * analysis. This does not 'compute' the value of the
   * expression; in particular, the expression may have variables
   * which must be set first.
   * @param in
   * @return
   */
  public boolean setExpression(String in) {
    parsedString = in;
    doStandardReplacement();
    v.removeAllElements();
    evaluator.reset();
    parse();
    return evaluator.doIt(v);
  }

  /**
   * Standard replacements are performed: I --> i, Pi --> PI on a
   * function expression string.
   */
  private void doStandardReplacement() {
    parsedString = parsedString.replace('I', 'i');
    parsedString = parsedString.replaceAll("Pi", "PI");
    int ind = 0;
    boolean goOn = true;
    while ( ( (ind = parsedString.indexOf("i", ind + 1)) >= 0) && goOn) {
      if (Evaluator.isDigit(parsedString.charAt(ind - 1)))
        parsedString = parsedString.substring(0, ind) + "*" +
            parsedString.substring(ind);
      else {
        if (ind == parsedString.lastIndexOf("i"))
          goOn = false;
      }
    }
  }
  
  public Complex evaluate() {
    evaluator.computeDictionary();
    return evaluator.evaluate(v);
  }
  
  /**
   * Call the evaluator's setVariable method.
   * @param z
   */
  public void setVariable(Complex z) {
    evaluator.setVariable(z);
  }
}
