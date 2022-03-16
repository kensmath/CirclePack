package frames;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.geom.Path2D;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;

import com.jimrolf.functionchoicebox.ComplexFunctionChoiceBox;
import com.jimrolf.functionfield.FunctionField;

import allMains.CPBase;
import allMains.CirclePack;
import complex.Complex;
import exceptions.DataException;
import exceptions.ParserException;

public class FunctionHover extends HoverPanel {

	private static final long 
	serialVersionUID = 1L;
	
	static int WIDE=500;
	static int HIGH=200;
	
	// utility function panel
    public JPanel ftnPanel;
    public JPanel lowerFtnPanel;
//	public parser.Parser functionParser;
    public FunctionField ftnField;
    public ComplexFunctionChoiceBox fcb;
    
    // parameterized path panel
    public JPanel paramPanel;
    public JPanel lowerParamPanel;
//    public parser.Parser paramParser;
    public FunctionField paramField;
    public ComplexFunctionChoiceBox ppfb;
    
    // utility parser
    public FunctionField utilField;
//    public parser.Parser utilParser;
    
    // action to set path (TODO)
    public JButton pathButton;
    public AbstractAction pathAction;

    // Constructor
	public FunctionHover() {
		super(WIDE,HIGH,"Function Definition");
	}

	/**
	 * Override method
	 */
	public void initComponents() {
		this.setLayout(new BoxLayout(this,BoxLayout.Y_AXIS));
		this.setBorder(new LineBorder(new Color(250,0,0)));

    	// utility field for internal only
		utilField = new FunctionField();
		utilField.setText("");
		utilField.setComplexFunc(true);

		// 'parser's do most of the work?? these don't seem to be used
//    	functionParser=new Parser(); 
//    	paramParser=new Parser(); 
//    	utilParser=new Parser();
    	
		// upper, function panel
		ftnPanel = new JPanel();
		ftnPanel.setLayout(new BoxLayout(ftnPanel,BoxLayout.Y_AXIS));
		ftnPanel.setBorder(BorderFactory.createTitledBorder(
				new LineBorder(new java.awt.Color(104,226,9), 1, true),
				"Utility function of (complex) 'z'",
				TitledBorder.LEADING, TitledBorder.TOP));
		ftnPanel.setPreferredSize(new Dimension(WIDE,70));
		ftnPanel.setToolTipText(null);
		
		ftnField = new FunctionField();
		ftnField.setText("");
		ftnField.setBackground(Color.white);
		ftnField.setColumns(40);
		ftnField.addActionListener(new FtnPanel_actionAdapter(this));
		ftnField.setComplexFunc(true);
		ftnField.setErrorColor(Color.yellow);
		
		// lower panel for input when locked
		lowerFtnPanel=new JPanel(new FlowLayout(FlowLayout.LEADING));
		
		fcb = new ComplexFunctionChoiceBox();
		fcb.addActionListener(new FtnBox_actionAdapter(this));
		fcb.setPreferredSize(new Dimension(220,22));

		lowerFtnPanel.add(fcb);
			
		// lower, parameterization panel
		paramPanel = new JPanel();
		paramPanel.setLayout(new BoxLayout(paramPanel,BoxLayout.Y_AXIS));
		paramPanel.setBorder(BorderFactory.createTitledBorder(
				new LineBorder(new java.awt.Color(128,191,239), 1, true),
				"Utility Path, real z in [0,1]", TitledBorder.LEADING,
				TitledBorder.TOP));
		paramPanel.setPreferredSize(new Dimension(WIDE,70));
		
		paramField = new FunctionField();
		paramField.setText("");
		paramField.setBackground(Color.white);
		paramField.setColumns(40);
		paramField.addActionListener(new ParamPanel_actionAdapter(this));
		paramField.setComplexFunc(true);
		paramField.setErrorColor(Color.yellow);
		
		lowerParamPanel=new JPanel(new FlowLayout(FlowLayout.LEADING));
		ppfb = new ComplexFunctionChoiceBox();
		ppfb.addActionListener(new ParamBox_actionAdapter(this));
		ppfb.setPreferredSize(new Dimension(220,22));
		
		lowerParamPanel.add(ppfb);
		pathButton = new JButton();
		pathButton.setText("Set Closed Path");
		pathButton.setToolTipText(null);//"Creates closed path in plane from the text");
		pathButton.setAction(getPathAction());
		lowerParamPanel.add(pathButton);
	}
	
	public void loadHover() {
		this.removeAll();
		ftnPanel.setToolTipText(null);
		paramPanel.setToolTipText(null);
		pathButton.setToolTipText(null);
		ftnPanel.removeAll();
		ftnPanel.add(ftnField);
//		ftnPanel.setPreferredSize(new Dimension(WIDE,40));
		paramPanel.removeAll();
		paramPanel.add(paramField);
//		paramPanel.setPreferredSize(new Dimension(WIDE,40));
		this.add(ftnPanel);
		this.add(paramPanel);
		hoverFrame.setPreferredSize(new Dimension(WIDE,100));
		hoverFrame.add(this);
	}
	
	public void loadLocked() {
		this.removeAll();
		ftnPanel.setToolTipText("function expression in complex variable 'z'");
		paramPanel.setToolTipText("function expression for close path in plane, z in [0,1]");
		pathButton.setToolTipText("Creates closed path in plane from the text");
		ftnPanel.removeAll();
		ftnPanel.add(ftnField);
		ftnPanel.add(lowerFtnPanel);
		paramPanel.removeAll();
		paramPanel.add(paramField);
		paramPanel.add(lowerParamPanel);
		this.add(ftnPanel);
		this.add(paramPanel);
		lockedFrame.setPreferredSize(new Dimension(WIDE,170));
	}
	
	/**
     * When ENTER key is hit, this processes the expression in the 
     * ftnField, changing background to yellow if it's in error.
     * @param e
     */
    public void ftn_actionPerformed(ActionEvent e) {
    	ftnField.setBackground(Color.white);
    	if (ftnField.hasError()) {
    		CirclePack.cpb.myErrorMsg("Function parser error.");
    	}
    }
    
    public void ftnBox_actionPerformed(ActionEvent e) {
    	fcb.setFunctionString(ftnField);
    }
    
	/**
     * When ENTER key is hit, this processes the expression in the 
     * paramField, changing background to yellow if it's in error.
     * @param e
     */
    public void param_actionPerformed(ActionEvent e) {
    	paramField.setBackground(Color.white);
    	if (paramField.hasError()) {
    		CirclePack.cpb.myErrorMsg("Function parser error.");
    	}
    }
	
    public void paramBox_actionPerformed(ActionEvent e) {
    	ppfb.setFunctionString(paramField);
    }
    
    /**
     * Can be called separately using 'set_ftn_text' to enter 
     * and process a function. This does not evaluate it -- e.g., 
     * there may be variables yet to be set.
     * @param ftn
     */
    public boolean setFunctionText(String ftn) { 
    	ftnField.setText(ftn);
    	ftnField.setBackground(Color.white);
    	if (ftnField.hasError()) {
    		throw new ParserException("Given function string has error");
    	}
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
    	paramField.setBackground(Color.white);
    	if (paramField.hasError()) {
    		throw new ParserException("Given 'path' string has error");
    	}
		return true;
    }

    /** 
     * Set the function string for 'utilParser' to use.
     * @param ftn, String, function describing the path
     * @return
     */
    public boolean setUtilText(String ftn) {
    	utilField.setText(ftn);
    	if (utilField.hasError()) {
    		throw new ParserException("Specified 'path' string has error");
    	}
		return true;
    }

    /**
     * Create a Path2D.Double in the complex plane by parsing a function 
     * description using real variable 'z' for z in [0,1]. 
     * @param path_text, 
     * @return
     */
    public Path2D.Double setClosedPath(String path_text) {
		Path2D.Double closedPath=new Path2D.Double();
		setUtilText(path_text);
		// create path, 1000 segments
		try {
			for (int i=0;i<=1000;i++) {
				com.jimrolf.complex.Complex z=
					new com.jimrolf.complex.Complex(((double)(i))/1000.0,0.0);
				com.jimrolf.complex.Complex w=utilField.parser.evalFunc(z);
				if (i==0)
					closedPath.moveTo(w.re(),w.im());
				else
					closedPath.lineTo(w.re(),w.im());
			}
			closedPath.closePath();
		} catch (Exception ex) {
			throw new ParserException("Failed to parse path description");
		}
    	return closedPath;
    }
    
	private AbstractAction getPathAction() {
		if(pathAction == null) {
			pathAction = new AbstractAction("setCurrentPath", null) {

				private static final long serialVersionUID = 1L;

				public void actionPerformed(ActionEvent evt) {
					Path2D.Double closedPath=setClosedPath(paramField.getText()); 
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
     * The parser treats 'z' as denoting a complex variable. 
     * This tells parser to set z to a specific value and evaluate 
     * the function.
     * @param z, Complex
     * @return Complex
     */
    public Complex getFtnValue(Complex z) {
    	try {
    		com.jimrolf.complex.Complex w=
    				ftnField.parser.evalFunc(new com.jimrolf.complex.Complex(z.x,z.y));
    		return new Complex(w.re(),w.im());
    	} catch (Exception ex) {
    		throw new DataException("Ftn Panel error: "+ex.getMessage());
    	}
    }
    
    /**
     * TODO: have to figure out how to designate variable character 't'
     * 
     * The parser treats 't' as denoting a double variable. 
     * This tells parser to set t to a specific value and evaluate 
     * the parameter expression.
     */
    public Complex getParamValue(double t) {
    	try {
    		com.jimrolf.complex.Complex w=ftnField.parser.evalFunc(new com.jimrolf.complex.Complex(t,0.0));
    		return new Complex(w.re(),w.im());
    	} catch (Exception ex) {
    		throw new DataException("Ftn Panel error: "+ex.getMessage());
    	}
    }
    
}

class FtnPanel_actionAdapter implements java.awt.event.ActionListener {
	  FunctionHover adaptee;

	  public FtnPanel_actionAdapter(FunctionHover adaptee) {
	    this.adaptee = adaptee;
	  }
	  public void actionPerformed(ActionEvent e) {
	    adaptee.ftn_actionPerformed(e);
	  }
}

class ParamPanel_actionAdapter implements java.awt.event.ActionListener {
	  FunctionHover adaptee;

	  public ParamPanel_actionAdapter(FunctionHover adaptee) {
	    this.adaptee = adaptee;
	  }
	  public void actionPerformed(ActionEvent e) {
	    adaptee.param_actionPerformed(e);
	  }
}

class FtnBox_actionAdapter implements java.awt.event.ActionListener {
	  FunctionHover adaptee;

	  public FtnBox_actionAdapter(FunctionHover adaptee) {
	    this.adaptee = adaptee;
	  }
	  public void actionPerformed(ActionEvent e) {
	    adaptee.ftnBox_actionPerformed(e);
	  }
}	    

class ParamBox_actionAdapter implements java.awt.event.ActionListener {
	  FunctionHover adaptee;

	  public ParamBox_actionAdapter(FunctionHover adaptee) {
	    this.adaptee = adaptee;
	  }
	  public void actionPerformed(ActionEvent e) {
	    adaptee.paramBox_actionPerformed(e);
	  }
}
