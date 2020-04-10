package math.group;
import java.util.Vector;

import math.Mobius;

import complex.Complex;
public class KleinianGroup extends GeneratorTypeGroup { // implements listeners.MobiusListener {
  private Vector<Complex> fixedPoints = new Vector<Complex>();
  private Vector<Complex> limitPoints = new Vector<Complex>();
  public void addMobius(Mobius m, String s) {
    // what if s is already there
    addGeneratorWithInverse(m,s);
  }
  public void setMobius(Mobius m) {
    addGeneratorWithInverse(m,"A");
  }
  public void addFixedPoints(Mobius m) {
    fixedPoints.add(m.getFixedPoint1());
    fixedPoints.add(m.getFixedPoint2());
  }
  public void computeLimitPoints() {
    if(generatorKeys.size()==0)
      return;
    limitPoints.removeAllElements();
    if(!isGroupComputed)
      computeGroup();
    if(fixedPoints.size()==0)
      for (int i=0;i<groupValues.size();i++)
        addFixedPoints( (Mobius) groupValues.elementAt(i));
    // finally apply all those
    for (int k = 0; k < groupValues.size(); k++) {
      Mobius Mob = (Mobius)(groupValues.elementAt(k));
      for(int j=1;j<fixedPoints.size();j++)
        limitPoints.add(Mob.apply((Complex)(fixedPoints.elementAt(j))));
    }
  }
  public void computeFixedPoints() {
    fixedPoints.removeAllElements();
    for (int k = 0; k < groupValues.size(); k++)
      this.addFixedPoints((Mobius)groupValues.elementAt(k));
  }
  public Complex getFixedPoint(int i) {
    return (Complex)fixedPoints.elementAt(i);
  }
  public Complex getLimitPoint(int i) {
    return (Complex)limitPoints.elementAt(i);
  }
  public int fixedPointsSize() {
    return fixedPoints.size();
  }
  public int limitPointsSize() {
    return limitPoints.size();
  }



}
