package frames;

import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Path2D;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.WindowConstants;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;

import com.jimrolf.functionfield.FunctionField;

import allMains.CPBase;
import allMains.CirclePack;
import circlePack.PackControl;
import util.PathUtil;

public class FtnFrame extends JFrame {

	private static final long 
	serialVersionUID = 1L;
	
	static int WIDE=500;
	static int HIGH=200;
	
	// utility function panel
    public FunctionField ftnField;
    
    // parameterized path panel
    public FunctionField paramField;
    
    // action to set path (TODO)
    public JButton pathButton;
    public AbstractAction pathAction;

    // populate "Function Help" items
	static String[] names={"Function Help","Absolute value: abs()","Angle: angle()",
			"Arc cosin: acos()","Argument: arg()","Arc sine: asin()",
			"Arc tangent: atan()","Conjugate: conj()","Cosine: cos()",
			"E: e","Exponential: exp()","Factorial: fact()",
			"Hyperbolic cosine: cosh()","Hyperbolic sine: sinh()",
			"Hyperbolic tangent: tanh()","I: i","Imaginary part: im()",
			"Inverse hyp cosine: acosh()","Inverse hyp sine: asinh()",
			"Inverse hyp tangent: atanh()","Logarithm base 10: log()",
			"Modulus: mod()","Natural Logarithm: ln()","PI: pi",
			"Random number in [0,1]: rand()","Real part: re()",
			"Sine: sin()","Square root: sqrt()","Sum: sum()","Tangent: tan()"};
	static String[] ftns={"","abs()","angle()","acos()","arg()",
			"asin()","atan()","conj()","cos()","e","exp()",
			"fact()","cosh()","sinh()","tanh()","i","im()","acosh()",
			"asinh()","atanh()","log()","mod()","ln()","pi",
			"rand()","re()","sin()","sqrt()","sum()","tan()"};

    // Constructor
	public FtnFrame() {
		super();
		this.addWindowListener(new WAdapter());
		initGUI();
	}
	
	private void initGUI() {
		this.setTitle("Function Specification");
		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

		Container pane=getContentPane();
		pane.setLayout(null);
		pane.setLayout(new BoxLayout(pane,BoxLayout.Y_AXIS));

		// upper, function panel
		JPanel ftnPanel = new JPanel();
		ftnPanel.setLayout(new BoxLayout(ftnPanel,BoxLayout.Y_AXIS));
		ftnPanel.setBorder(BorderFactory.createTitledBorder(
				new LineBorder(new java.awt.Color(104,226,9), 1, true),
				"Utility function of (complex) 'z'",
				TitledBorder.LEADING, TitledBorder.TOP));
		ftnPanel.setToolTipText("function expression in complex variable 'z'");
		
		ftnField = new FunctionField();
		ftnField.setText("");
		ftnField.setBackground(Color.white);
		ftnField.setColumns(40);
		ftnField.addActionListener(new FtnPanel_actionAdapter(this));
		ftnField.setComplexFunc(true);
		ftnField.setErrorColor(Color.yellow);
		ftnPanel.add(ftnField);

		// lower panel for input help
		JPanel lowerFtnPanel=new JPanel(new FlowLayout(FlowLayout.LEADING));
		
		// function help
	   	JComboBox<String> ftnBox=new JComboBox<String>(names);
		ftnBox.setSelectedIndex(0);
	   	ftnBox.addActionListener(new ActionListener() {
    		public void actionPerformed(ActionEvent e) {
    			JComboBox<?> cb = (JComboBox<?>)e.getSource();
    			int i=cb.getSelectedIndex();
    			StringBuilder strbld=new StringBuilder(ftnField.getText());
    			strbld.append(ftns[i]);
    			CirclePack.cpb.setFtnSpec(strbld.toString());
    			ftnField.setText(strbld.toString());
    			ftnBox.setSelectedIndex(0);
    		}
	   	});
	   	lowerFtnPanel.add(ftnBox);

		lowerFtnPanel.setBounds(0,0,PackControl.ControlDim1.width-5,80);
		ftnPanel.add(lowerFtnPanel);
		pane.add(ftnPanel);
		
		// lower, parameterization panel
		JPanel paramPanel = new JPanel();
		paramPanel.setLayout(new BoxLayout(paramPanel,BoxLayout.Y_AXIS));
		paramPanel.setBorder(BorderFactory.createTitledBorder(
				new LineBorder(new java.awt.Color(128,191,239), 1, true),
				"Utility Path, real t in [0,1]", TitledBorder.LEADING,
				TitledBorder.TOP));
		paramPanel.setToolTipText("function for closed path in plane, variable t in [0,1]");
		
		paramField = new FunctionField();
		paramField.setText("");
		paramField.setBackground(Color.white);
		paramField.setColumns(40);
		paramField.addActionListener(new ParamPanel_actionAdapter(this));
		paramField.setComplexFunc(true);
		paramField.setErrorColor(Color.yellow);
		paramPanel.add(paramField);
		
		JPanel lowerParamPanel=new JPanel(new FlowLayout(FlowLayout.LEADING));

		// function help
		JComboBox<String> pathBox=new JComboBox<String>(names);
		pathBox.setSelectedIndex(0);
//		pathBox.setPreferredSize(new Dimension(220,22));
	   	pathBox.addActionListener(new ActionListener() {
    		public void actionPerformed(ActionEvent e) {
    			JComboBox<?> cb = (JComboBox<?>)e.getSource();
    			int i=cb.getSelectedIndex();
    			StringBuilder strbld=new StringBuilder(paramField.getText());
    			strbld.append(ftns[i]);
    			CirclePack.cpb.setParamSpec(strbld.toString());
    			paramField.setText(strbld.toString());
    			pathBox.setSelectedIndex(0);
    		}
	   	});
	   	lowerParamPanel.add(pathBox);
		
		// button for closed path
		pathButton = new JButton();
		pathButton.setText("Set Closed Path");
		pathButton.setToolTipText(null);//"Creates closed path in plane from the text");
		pathButton.setAction(getPathAction());
		pathButton.setToolTipText("Creates closed path in plane from the text");
		lowerParamPanel.add(pathButton);
		paramPanel.add(lowerParamPanel);
		paramPanel.setBounds(0,0,PackControl.ControlDim1.width-5,80);
		
		pane.add(paramPanel);
		this.pack();
		this.setSize(new Dimension(PackControl.ControlDim1.width+10,190));
	}
 
    /**
     * Can be called separately using 'set_ftn_text' to enter 
     * and process a function. This does not evaluate it -- e.g., 
     * there may be variables yet to be set.
     * @param ftn
     */
    public boolean setFunctionText(String ftn) { 
    	ftnField.setText(ftn);
    	if (!CirclePack.cpb.setFtnSpec(ftn)) {
        	ftnField.setBackground(Color.yellow);
        	return false;
    	}
       	ftnField.setBackground(Color.white);
		return true;
    }
    
    /**
     * Can be called separately using 'set_path_text' to enter 
     * and process a parameterized path. This does not evaluate it -- e.g., 
     * there may be variables yet to be set.
     * @param ftn
     */
    public boolean setPathText(String ftn) { 
    	paramField.setText(ftn);
    	if (!CirclePack.cpb.setParamSpec(ftn)) {
    		paramField.setBackground(Color.yellow);
    		return false;
    	}
    	paramField.setBackground(Color.white);
		return true;
    }

	private AbstractAction getPathAction() {
		if(pathAction == null) {
			pathAction = new AbstractAction("setCurrentPath", null) {

				private static final long serialVersionUID = 1L;

				public void actionPerformed(ActionEvent evt) {
					Path2D.Double closedPath=PathUtil.path_from_text(
							CirclePack.cpb.ParamParser.getFuncInput());
					if (closedPath!=null) {
						CPBase.ClosedPath=closedPath;
						CirclePack.cpb.msg("Path stored from 'Function' panel.");
					}
				}
			};
		}
		return pathAction;
	}
	
	/**
     * When ENTER key is hit, this processes the expression in the 
     * paramField, changing background to yellow if it's in error.
     * @param e
     */
    public void param_actionPerformed(ActionEvent e) {
    	paramField.setBackground(Color.white);
    	String txt=e.getActionCommand().trim();
    	if (txt.length()!=0) {
    		CirclePack.cpb.setParamSpec(txt);
    		if (paramField.hasError()) { // paramField.getText();
    			CirclePack.cpb.myErrorMsg("Path parser error: check for variable 't'.");
    	    	paramField.setBackground(Color.yellow);
    		}    			
    	}
    }
	
	class WAdapter extends WindowAdapter {
		public void windowClosing(WindowEvent wevt) {
			if (wevt.getID()==WindowEvent.WINDOW_CLOSING)
				PackControl.newftnFrame.setVisible(false);
		}
	}
	
	/**
     * When ENTER key is hit, this processes the expression in the 
     * ftnField, changing background to yellow if it's in error.
     * @param e
     */
    public void ftn_actionPerformed(ActionEvent e) {
    	ftnField.setBackground(Color.white);
    	String txt=e.getActionCommand().trim();
    	if (txt.length()!=0) {
    		CirclePack.cpb.setFtnSpec(txt);
    		if (ftnField.hasError()) { // ftnField.getText();
    			CirclePack.cpb.myErrorMsg("Function parser error: check for variable 'z'.");
    	    	ftnField.setBackground(Color.yellow);
    		}    			
    	}
    }
     
}
	
class FtnPanel_actionAdapter implements java.awt.event.ActionListener {
	FtnFrame adaptee;

	public FtnPanel_actionAdapter(FtnFrame adaptee) {
		this.adaptee = adaptee;
	}
	public void actionPerformed(ActionEvent e) {
		adaptee.ftn_actionPerformed(e);
	}
}

class ParamPanel_actionAdapter implements java.awt.event.ActionListener {
	  FtnFrame adaptee;
	  public ParamPanel_actionAdapter(FtnFrame adaptee) {
		  this.adaptee = adaptee;
	  }
	  public void actionPerformed(ActionEvent e) {
		  adaptee.param_actionPerformed(e);
	  }
}