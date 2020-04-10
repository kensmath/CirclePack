package math.group;
import java.util.Vector;

import math.Mobius;

public class GeneratorTypeGroup {
  protected Vector<GroupElement> generatorValues = new Vector<GroupElement>();
  protected Vector<String> generatorKeys = new Vector<String>();
  protected Vector<GroupElement> groupValues = new Vector<GroupElement>(60000);
  protected Vector<String> groupKeys = new Vector<String>(60000);
  protected static int depth=4;
  protected boolean isGroupComputed = false;

  public String getInverseSymbol(String s) {
    char ch = s.charAt(0);
    if(ch>='A' && ch<='Z')
      return new String()+(char)(ch+32);
    else
      return new String()+(char)(ch-32);
  }

  /**
   * Adds a generator (and its inverse) to the table. Expects letters from A to
   * Z.
   *
   * @param m GroupElement
   * @param s String
   */
  public void addGenerator(GroupElement m, String s) {
    int ind;
    if((ind=generatorKeys.indexOf(s))>=0) {
      generatorValues.remove(ind);
      generatorKeys.remove(s);
    }
    generatorKeys.add(s);
    generatorValues.add(m);
    //groupKeys.add(s);
    //groupValues.add(m);
    this.resetGroup();
  }
  public void addGeneratorWithInverse(GroupElement m, String s) {
    int ind=-1;
    if((ind=generatorKeys.indexOf(s))>=0) {
      generatorValues.remove(ind);
      generatorKeys.remove(s);
      //ind = groupKeys.indexOf(s);
      //groupValues.remove(ind);
      //groupKeys.remove(s);
      //isGroupComputed = false;
    }
    generatorKeys.add(s);
    generatorValues.add(m);
    //groupKeys.add(s);
    //groupValues.add(m);
    GroupElement  mInverse = (GroupElement)m.inverse();
    String sInverse = getInverseSymbol(s);
    generatorKeys.add(sInverse);
    generatorValues.add(mInverse);
    //groupKeys.add(sInverse);
    //groupValues.add(mInverse);
    //isGroupComputed = false;
    this.resetGroup();
  }

  public void setGenerator(GroupElement m, String s) {
    //generatorKeys.remove(s);
    //generatorValues.remove(m);
    addGenerator(m,s);
    this.resetGroup();
  }
  public GroupElement getElement(int i) {
    return (GroupElement)groupValues.get(i);
  }
  public String getKey(int i) {
    return (String)groupKeys.get(i);
  }

  public GroupElement getElement(String s) {
    return (GroupElement)(groupValues.get(groupKeys.indexOf(s)));
  }
  public GroupElement getGenerator(String s) {
    return (GroupElement)(generatorValues.get(generatorKeys.indexOf(s)));
  }
  public void addGroupElement(GroupElement m, String s) {
    groupKeys.add(s);
    groupValues.add(m);
  }
  public void setGroupElement(GroupElement m, String s) {
    groupValues.set(groupKeys.indexOf(s),m);
  }
  public int index(String s) {
    return groupKeys.indexOf(s);
  }

  /**
   * Divides string s into generators, multiples them and returns the value.
   * Doesn't affect the group anyhow. To be used only sporadically. For serious
   * computations the group must be computed.
   * @param s String
   * @return GroupElement
   */
  public GroupElement parse(String s) {
    int temp = groupKeys.indexOf(s);
    if(temp>=0)
      return (GroupElement)(groupValues.get(temp));
    int size = s.length();
    // Stupid Java !!!
    // walk around - pass that as an argument
    GroupElement element = new Mobius();
    for(int i=0;i<size;i++) {
      String gen = new String()+s.charAt(i);
      element = element.rmult(getGenerator(gen));
    }
    return element;
  }

//  private static boolean isInv(char c1, char c2) {
//    if((int)(c1-32)==(int)c2 || (int)(c2-32)==(int)c1)
//      return true;
//    else
//      return false;
//  }
  // applies up to level
  /**
   * Checks if the end of word has an inverse of generator. Returns true if it doesn't.
   * @param word String
   * @param generator String
   * @return boolean
   */
  public boolean isIrreducible(String word, String generator) {
    if(getInverseSymbol(word.substring(word.length()-1,word.length())).compareTo(generator)==0)
      return false;
    return true;
  }
  public void computeGroup() {
    // go over every word in the current group and add a letter
    // let begin and end label the beginning and the end
    // of the previous level computed
    int begin = 0;
    int nGenerators = numberOfGenerators();
    int end = nGenerators;
    // add generators
    for (int j = 0; j < nGenerators; j++) {
      groupValues.add(generatorValues.get(j));
      groupKeys.add(generatorKeys.get(j));
    }
    for (int lev = 1; lev < depth; lev++) {
      int count=0;
      for (int i = begin; i < end; i++) {
        GroupElement word = (GroupElement) groupValues.get(i);
        String key = (String) groupKeys.get(i);
        for (int j = 0; j < nGenerators; j++) {
          GroupElement gen = (GroupElement) generatorValues.get(j);
          String genKey = (String) generatorKeys.get(j);
          if (isIrreducible(key, genKey)) {
            groupKeys.add(key + genKey);
            GroupElement newWord = word.rmult(gen);
            newWord.setLevel(word.getLevel()+1);
            groupValues.add(newWord);
            count++;
          }
        }
      }
      begin = end;
      end = end + count;
    }
    isGroupComputed = true;
  }

//  public static void computeGroup() {
//    if(isGroupComputed)
//      return;
//    String s, st;
//    int[] num = new int[el+1];
//    num[0] = 0;
//    int count = 0;
// do level 1 - should be done
//    for (int i = 0; i < numberOfGenerators(); i++) {
//      s = Letters.substring(i, i + 1);
//      group.add(s);
//      count++;
//      s = invLetters.substring(i, i + 1);
//      group.add(s);
//      count++;
//    }
//    num[1] = count;
//
// do higher levels
//    char ch, lastCh;
//    for (int lev = 1; lev < level; lev++) {
//      count = 0;
//      for (int i = num[lev - 1]; i < num[lev]; i++) {
//        s = (String) group.elementAt(i);
//        lastCh = s.charAt(s.length() - 1);
//        for (int j = 0; j < Letters.length(); j++) {
//          ch = Letters.charAt(j);
//          if (!isInv(lastCh, ch)) {
//            group.add(s + ch);
//            count++;
//          }
//          ch = invLetters.charAt(j);
//          if (!isInv(lastCh, ch)) {
//            group.add(s + ch);
//            count++;
//          }
//        }
//      }
//      num[lev + 1] = num[lev] + count;
//    }
//    isGroupComputed = true;
//  }


//  public static void showFixedPoints() {
//    collection = Trans.values();
//    iterator = collection.iterator();
//    if(!isFixedPointsShown) {
//      while(iterator.hasNext())
//        addFixedPoints((GroupElement)iterator.next());
//      isFixedPointsShown=true;
//    }
//    else {
//      fixedPoints.removeAllElements();
//      isFixedPointsShown=false;
//    }
//    if(DC!=null)
//      DC.repaint();
//  }


//  public void clear() {
//    isGroupComputed=false;
    // keep generators?
//    groupValues.removeAllElements();
//    groupKeys.removeAllElements();
//    for(int i=0;i<generatorValues.size();i++) {
//      groupValues.add(generatorValues.get(i));
//      groupKeys.add(generatorKeys.get(i));
//    }
// }

  public void setGroupElement(GroupElement m) {
  }
  public int numberOfGenerators() {
    return generatorValues.size();
  }
  public void reset() {
    groupKeys.removeAllElements();
    groupValues.removeAllElements();
    generatorKeys.removeAllElements();
    generatorValues.removeAllElements();
    isGroupComputed = false;
  }
  public void resetGroup() {
    groupKeys.removeAllElements();
    groupValues.removeAllElements();
    isGroupComputed = false;
  }
  public static int getDepth() {
    return depth;
  }
  public void setDepth(int d) {
    depth = d; resetGroup();
  }
//  public static GroupElement getElement(char t) {
//    int ind = Letters.indexOf(t);
//    if(ind<0 || ind>Letters.length())
//      return null;
//    return (GroupElement) Trans.elementAt(ind);
//  }
//  public static GroupElement getInverseElement(char t) {
//    int ind = invLetters.indexOf(t);
//    return (GroupElement) invTrans.elementAt(ind);
//  }
  public int groupSize() {
    return groupValues.size();
  }
//  public static String getGroupElement(int k) {
//    return (String) group.get(k);
//  }
  public void printGroup() {
    for (int i=0;i<groupValues.size();i++) {
      System.out.println(groupKeys.get(i)+"="+groupValues.get(i));
    }
  }
  // should it go to Map or it belongs here
//  public void applyN() {
    // assumes map is ready (initialize called)
    //Map.initialize();
//    if(!isGroupComputed)
//      computeGroup();
//    map.removeTransformed();
//    for (int k = 0; k < this.groupSize(); k++)
      // essentially it can be any kind of transformation
//      map.apply((ComplexTransformation)groupValues.get(k));
      //new GroupElement(1f,0f,0f,1f);
//      tr = (String) group.elementAt(k);
//      GraphObject.setLevel(tr.length());
//      for(int l=tr.length()-1;l>=0;l--) {
//        t = tr.charAt(l);
//        if((ind=Letters.indexOf(t))>=0)
//          Mob = Mob.lmult((GroupElement)Trans.elementAt(ind));
//        else {
//            ind = invLetters.indexOf(t);
//            Mob = Mob.lmult( (GroupElement) invTrans.elementAt(ind));
//        }
//      }
//  }
//  public static void setMap(algorithm.Map m) {
//    map = m;
//  }

}
