package parser;
import complex.Complex;
import complex.MathComplex;

// function of one argument
public class FunctionalExpression implements Word {
  // symbol of this function
  String symbol;
  // argument
  String arg;
  
  // Constructors
  public FunctionalExpression() {
  }
  
  public FunctionalExpression(String fun, String arg) {
    symbol = fun; this.arg = arg;
  }
  
  public String toString() {
    return symbol + "("+arg+")";
  }
  public Complex evaluate(Evaluator ev) {
    Complex d = ev.evaluate(arg);
    if (symbol.compareTo("sin")==0)
      return MathComplex.sin(d);
    if (symbol.compareTo("cos")==0)
      return MathComplex.cos(d);
    if (symbol.compareTo("sqrt")==0)
      return MathComplex.sqrt(d);
    if (symbol.compareTo("exp")==0)
      return MathComplex.exp(d);
    if (symbol.compareTo("ln")==0)
      return MathComplex.ln(d);
    if (symbol.compareTo("arg")==0 || symbol.compareTo("Arg")==0)
      return new Complex(Math.atan2(d.getImaginary(),d.getReal()),0);
    if (symbol.compareTo("conj")==0) {
      d.setImaginary(-d.getImaginary());
      return d;
    }
    if (symbol.compareTo("abs")==0)
      return new Complex(MathComplex.abs(d),0);
    
    // Have to define complex calls for acos, asin, atan, cosh, sinh,
    // tanh, asinh,acosh,asinh ????

    return null;
  }
}
