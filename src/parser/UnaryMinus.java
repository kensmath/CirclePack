package parser;
import complex.Complex;

public class UnaryMinus implements Word {
  // argument
  String arg;
  
  // Constructors
  public UnaryMinus() {
  }
  
  public UnaryMinus(String arg) {
    this.arg = arg;
  }
  
  public String toString() {
    return "(-" + arg +")";
  }
  public Complex evaluate(Evaluator ev) {
    Complex d=ev.evaluate(arg);
    d=d.times(-1);
    return d;
  }
}
