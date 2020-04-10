package parser;
import complex.Complex;

public class BinaryExpression implements Word {
  // symbol of this operator
  String symbol;
  // arguments
  String arg1, arg2;
  
  // Constructors
  
  public BinaryExpression() {
  }
  
  public BinaryExpression(String s, String argt1, String argt2) {
    symbol = s; arg1 = argt1; arg2 = argt2;
  }
  
  public String toString() {
    return arg1 + symbol + arg2;
  }
  
  public Complex evaluate(Evaluator ev) {
    Complex d;
    Complex d1=ev.evaluate(arg1);
    Complex d2=ev.evaluate(arg2);
//    if(symbol.compareTo("^")==0)
//      d=MathComplex.pow(d1,d2); else
    if(symbol.compareTo("*")==0)
      d=d1.mult(d2);
    else if(symbol.compareTo("/")==0)
      d=d1.divide(d2);
    else if(symbol.compareTo("+")==0)
      d=d1.plus(d2);
    else if(symbol.compareTo("-")==0)
      d=d1.sub(d2);
    else
      d = null;
     return d;
  }
}
