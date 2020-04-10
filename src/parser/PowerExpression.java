package parser;
import complex.Complex;
import complex.MathComplex;

public class PowerExpression implements Word {
  // symbol of this operator
//  String symbol;
  // arguments
  String arg1, arg2;
  
  // Constructors
  public PowerExpression() {
  }
  
  public PowerExpression(String argt1, String argt2) {
    arg1 = argt1; arg2 = argt2;
  }
  
  public String toString() {
    return arg1 + "^" + arg2;
  }
  
  public Complex evaluate(Evaluator ev) {
    Complex d;
    Complex d1=ev.evaluate(arg1);
    Complex d2=ev.evaluate(arg2);
    if(MathComplex.isInteger(d2))
      d=MathComplex.pow(d1,(int)d2.real());
    else
      d=MathComplex.pow(d1,d2);
    return d;
  }
}
