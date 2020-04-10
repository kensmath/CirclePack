package panels;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTextField;

import math.group.ComplexTransformation;
import math.group.Schottky;

/**
 * TODO: this is not operational as of May 2014: I don't recall when it
 * was started.
 * The difference between this one and MobiusFrame is this one
 * sets up the transformation as a composition of already existing Mobius maps
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */
public class MobiusTransformPanel extends JFrame implements ActionListener {

  private static final long 
  serialVersionUID = 1L;
	
  static JButton theButton = new JButton("OK");
  static JLabel Legend=new JLabel("Enter the Mobius transform you want to apply");
  static JTextField TransTF = new JTextField("A");
  static JLabel typeL= new JLabel("Type: ");
  static Schottky group;
  //public static panels.DrawCanvas3 DC;
  
  // Constructor
  public MobiusTransformPanel() {
    //this.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
    //this.pack();
    this.setSize(350, 170);
    //this.setVisible(false);
    theButton.addActionListener(this);
    this.getContentPane().setLayout(new GridLayout(4,1));
    this.getContentPane().add(Legend);
    this.getContentPane().add(TransTF);
    this.getContentPane().add(theButton);
    this.getContentPane().add(typeL);
    this.getContentPane().doLayout();
    this.setTitle("Resulting Mobius transform");
  }
  public void actionPerformed(ActionEvent e){
    try {
      ComplexTransformation mob = (ComplexTransformation) group.parse(TransTF.
          getText());
      if(mob==null)
        JOptionPane.showMessageDialog(this, "Can't parse "+TransTF.getText());
//    MobiusTransformation.setTransformation(mob);
    typeL.setText("Type: "+mob.getType());
    }
    catch(ArrayIndexOutOfBoundsException err) {
      JOptionPane.showMessageDialog(this, "Some letters are not assigned");
      return;
    }

  }
  public static void setGroup(Schottky g) {
    group = g;
  }
}
