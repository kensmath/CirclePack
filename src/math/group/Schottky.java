package math.group;

import graphObjects.CPCircle;
import math.Mobius;

import complex.Complex;

/**
 *
 * <p>Title: </p>
 * <p>Description: Specifics of the class: Only two generators A, B. <p>
 * Words apply only to circles that don't contradict the last letter in the word.<p>
 * If the word ends with A, we don't apply it to circle a, for instance.</p>
 * <p>Copyright: Copyright (c) 2004</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */
public class Schottky extends KleinianGroup {
  static graphObjects.CPCircle circleA, circlea, circleB, circleb;
//  private static algorithm.Map map;
  private static int scheme = 1;

  public void computeGenerators() {
    if (circleA != null && circleB != null && circlea != null && circleb != null) {
      addGeneratorWithInverse(new Mobius(circleA,
                     circleA.times( -1f).times(circlea).
                     plus(
          circleA.getRadius() * circlea.getRadius()),
                     new Complex(1f, 0f), circlea.times( -1f)),"A");
      addGeneratorWithInverse(new Mobius(circleB,
                     circleB.times( -1f).times(circleb).
                     plus(
          circleB.getRadius() * circleb.getRadius()),
                     new Complex(1f, 0f), circleb.times( -1f)),"B");
//      group.addMobius(A, "A");
//      group.addMobius(B, "B");
//      circleA.setInsideColor(Color.green);
//      circleB.setInsideColor(Color.yellow);
//      circlea.setInsideColor(Color.pink);
//      circleb.setInsideColor(Color.blue);
    }
  }
//  private static Mobius computeWord(String s) {
//    char c;
//    Mobius Mob = new Mobius();
//    for (int l = s.length() - 1; l >= 0; l--) {
//      c = s.charAt(l);
//      Mobius m = group.getElement(c);
//      if (m != null)
//        Mob = Mob.lmult(m);
//      else
//        Mob = Mob.lmult(ManagerMobiusOld.getInverseElement(c));
//    }
//    return Mob;
//  }

/*  public void apply() {
    if (numberOfGenerators() == 0)
      return;
    computeGroup();

    //finally apply all those
    //String tr;
    char lastChar;
    for (int k = 0; k < groupSize(); k++) {
      math.group.ComplexTransformation Mob = (math.group.ComplexTransformation)getElement(k);//ManagerMobiusOld.getGroupElement(k);
      String key = getKey(k);
      //graphObjects.GraphObject.setCurrentLevel(key.length());
      lastChar=key.charAt(key.length() - 1);
      if (lastChar == 'A') {
        map.addTransformedObject(circleA.apply(Mob));
        map.addTransformedObject(circleB.apply(Mob));
        map.addTransformedObject(circleb.apply(Mob));
      }
      else if (lastChar == 'a') {
        map.addTransformedObject(circlea.apply(Mob));
        map.addTransformedObject(circleB.apply(Mob));
        map.addTransformedObject(circleb.apply(Mob));
      }
      else if (lastChar == 'B') {
        map.addTransformedObject(circleA.apply(Mob));
        map.addTransformedObject(circleB.apply(Mob));
        map.addTransformedObject(circlea.apply(Mob));
      }
      else if (lastChar == 'b') {
        map.addTransformedObject(circleA.apply(Mob));
        map.addTransformedObject(circleb.apply(Mob));
        map.addTransformedObject(circlea.apply(Mob));
      }
    }
  }*/

  public static CPCircle getCircleA() {
    return circleA;
  }

  public static CPCircle getCirclea() {
    return circlea;
  }

  public static CPCircle getCircleB() {
    return circleB;
  }

  public static CPCircle getCircleb() {
    return circleb;
  }

  public static void setCircleA(CPCircle c) {
    circleA = c;
  }

  public static void setCirclea(CPCircle c) {
    circlea = c;
  }

  public static void setCircleB(CPCircle c) {
    circleB = c;
  }

  public static void setCircleb(CPCircle c) {
    circleb = c;
  }
  public static void setScheme(int in) {
    scheme = in;
  }
  public static int getScheme() {
    return scheme;
  }
//  public static void setMap(algorithm.Map m) {
//    map = m;
//  }
}
