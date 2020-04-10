package panels;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.table.AbstractTableModel;

import com.jimrolf.functionfield.FunctionField;

import allMains.CPBase;
import allMains.CirclePack;
import circlePack.PackControl;
import exceptions.DataException;
import util.SliderPacket;
import util.xNumField;


/**
 * Slider panels occur in 'Pack Info' under the 'Variables' tab
 * in a region below the variables table. Each displays its variable 
 * name, the slider, slider min/max settings, a 'ftn?' checkbox (e.g.
 * with z*pi to multiply values by pi), slider, current value, 
 * and optional command execution button and string.
 * 
 * See 'VariableControlTableModel' in 'VarControl.java'
 * 
 * @author Ken Stephenson
 *
 */
public class SliderPanel extends JPanel 
	implements ChangeListener, ActionListener, ItemListener {
	
	private static final long 
	serialVersionUID = 1L;

	public SliderPacket sliderPacket;   // holds the details
	public JButton varNameButton;
	
	// live command structure: execute on slide/value change
	protected JTextField liveCommand; // optional live command
	protected JCheckBox liveCheck;    // use the live command?
	
	// function structure
	protected JCheckBox ftnCheck;   // use function
	public parser.Parser functionParser;
    public FunctionField ftnField;
	
	protected double value;
	protected xNumField currentValue;
	protected xNumField sliderMin;
	protected xNumField sliderMax;
	protected JSlider theSlider;
	protected boolean fireFlag;
	
	AbstractTableModel varModel;

	// Constructors
	public SliderPanel(String name,String specs,String valueStr) { // empty for new variable
		super();
		varModel=CPBase.varControl.getVarTableModel(); // model of variable tab for refreshing
		
		sliderPacket=new SliderPacket(name,specs);
		
		// create the layout
		this.setLayout(new BoxLayout(this,BoxLayout.PAGE_AXIS));
		this.setBorder(BorderFactory.createCompoundBorder(new LineBorder(Color.blue),
				new EmptyBorder(5, 5, 5, 5)));
		this.setMinimumSize(new Dimension(350,45));
		this.setMaximumSize(new Dimension(Integer.MAX_VALUE,100));

		varNameButton=new JButton(name);
		varNameButton.setToolTipText("variable name");
//		varName.setPreferredSize(new Dimension(30,20));
		varNameButton.setMaximumSize(new Dimension(Integer.MAX_VALUE,20));
		value=Double.valueOf(5.0);
		try {
			value=Double.parseDouble(valueStr);
		} catch (Exception ex) {
			throw new DataException("value is not a double");
		}

		// optional ftn
		ftnCheck=new JCheckBox("ftn?");
		ftnCheck.setToolTipText("if checked, apply function to variable");
		ftnCheck.addItemListener(this);
		
		ftnField = new FunctionField();
		ftnField.setText("z*pi");
    	ftnField.setBackground(Color.white);
    	if (ftnField.hasError()) {
    		CirclePack.cpb.myErrorMsg("Function parser error.");
    	}
		ftnField.setBackground(Color.white);
		ftnField.setColumns(20);
		
		// 'enter' will parse the function string
		ftnField.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
		    	ftnField.setBackground(Color.white);
		    	if (ftnField.hasError()) {
		    		CirclePack.cpb.myErrorMsg("Function parser error.");
		    	}
			}
		});

		ftnField.setComplexFunc(true);
		ftnField.setErrorColor(Color.yellow);
		ftnField.setToolTipText("Function to be applied to slider values");
		ftnField.setMaximumSize(new Dimension(Integer.MAX_VALUE,20));

		// optional command
		liveCheck=new JCheckBox("Cmd?");
		liveCheck.setToolTipText("if checked, execute this command on change");
		liveCheck.addItemListener(this);
		
		liveCommand=new JTextField("",6);
		liveCommand.setToolTipText("Command to execute on slider change (preferably a key '[*]')");
		liveCommand.setMaximumSize(new Dimension(Integer.MAX_VALUE,20));

		// current value (just for display)
		currentValue=new xNumField("Current value",10);
		currentValue.setField(value);
		
		// scale minimum
		sliderMin=new xNumField("min",8);
		sliderMin.setActionCommand("minval");
		sliderMin.addActionListener(this);
		sliderMin.setToolTipText("minimum value");
	
		// scale maximum
		sliderMax=new xNumField("max",8);
		sliderMax.setActionCommand("maxval");
		sliderMax.addActionListener(this);
		sliderMax.setToolTipText("maximum value");
		
		// slider itself
		fireFlag=false;
		theSlider=new JSlider(JSlider.HORIZONTAL,0,100,50);
		theSlider.addChangeListener(this);
	    theSlider.setMajorTickSpacing(10);
	    theSlider.setMinorTickSpacing(2);
	    theSlider.setPaintTicks(true);
	    theSlider.setPaintLabels(false);
	    
//		ImageIcon ii= new ImageIcon(CPBase.getResourceURL("/Icons/script/kill_16x16.png"));
//	    killMe=new JButton();
//		killMe.setIcon(ii);
//		killMe.setOpaque(false);
//		killMe.setBorderPainted(false);
//	    killMe.setBackground(Color.white);
//		killMe.setToolTipText("Delete this variable");
//		killMe.setSize(new Dimension(ii.getIconWidth(),ii.getIconHeight()));	    
//		killMe.setActionCommand("killme");
//	    killMe.addActionListener(this);

	    // ------------ top, value, sliderZone panels
	    
		// top panel has fields and buttons
		JPanel topPanel=new JPanel();
		topPanel.setLayout(new BoxLayout(topPanel,BoxLayout.LINE_AXIS));
		topPanel.add(Box.createRigidArea(new Dimension(20,0)));
		topPanel.add(varNameButton);
		topPanel.add(Box.createRigidArea(new Dimension(20,0)));
		topPanel.add(Box.createHorizontalGlue());
		topPanel.add(ftnCheck);
		topPanel.add(ftnField);
//		topPanel.add(killMe);
		
		// value panel
		JPanel valuePanel=new JPanel();
		valuePanel.setLayout(new BoxLayout(valuePanel,BoxLayout.LINE_AXIS));
		valuePanel.add(sliderMin);
		valuePanel.add(Box.createHorizontalGlue());
		valuePanel.add(currentValue);
		valuePanel.add(Box.createHorizontalGlue());
		valuePanel.add(sliderMax);
		
		// slide panel has slider and Pi check
		JPanel slideZone=new JPanel();
		slideZone.setLayout(new BoxLayout(slideZone,BoxLayout.LINE_AXIS));
		slideZone.add(theSlider);
		slideZone.add(Box.createRigidArea(new Dimension(20,0)));
		slideZone.add(liveCheck);
		slideZone.add(liveCommand);

		ftnCheck.setSelected(false);
		liveCheck.setSelected(false);
		
		// set some items
		if (sliderPacket.getCommandAction()) {
			liveCheck.setSelected(true);
			liveCommand.setText(sliderPacket.getCommand());
		}
		if (sliderPacket.getFunctionApply()) {
			ftnCheck.setSelected(true);
			ftnField.setText(sliderPacket.getFunction());
	    	ftnField.setBackground(Color.white);
	    	if (ftnField.hasError()) {
	    		CirclePack.cpb.myErrorMsg("Function parser error.");
	    	}
		}
		
		// check if max/min affects value
		adjustValue4Range();

		// add all to this panel
		this.add(topPanel);
		this.add(valuePanel);
		this.add(slideZone);
	}
	
	public void adjustValue4Range() {
		if (sliderPacket.getMin()>value)
			sliderPacket.setMin(value);
		sliderMin.setField(sliderPacket.getMin());
		if (sliderPacket.getMax()<value)
			sliderPacket.setMax(value);
		sliderMax.setField(sliderPacket.getMax());
	    int tick=(int)(100*(value-sliderPacket.getMin()/(sliderPacket.getMax()-sliderPacket.getMin())));
	    theSlider.setValue(tick);
	    fireFlag=true;
	}
	
	/**
	 * Get current value as a string; it should be a double, may send it 
	 * through a function first, but should still be a double, e.g., z*pi.
	 * 
	 * CAUTION: this is called 'toString' to override the parent class 'toString'
	 * so that we get the double value in 'QueryParser' instead of the class.
	 * 
	 * @return String, representation of variable's current value
	 */
	public String toString() {
		if (ftnCheck.isSelected()) {
		  	try {
	    		com.jimrolf.complex.Complex w=ftnField.parser.evalFunc(new com.jimrolf.complex.Complex(value,0.0));
	    		return Double.toString(w.re());
	    	} catch (Exception ex) {
	    		throw new DataException("Ftn Panel error: "+ex.getMessage());
	    	}
		}
		return Double.toString(value);
	}
	
	// Listen to the slider; fireFlag==false, do catch the change
    public void stateChanged(ChangeEvent ce) {
    	if (!fireFlag) return; // ignore some changes
        JSlider source = (JSlider)ce.getSource();
        if (!source.getValueIsAdjusting()) {  // ignore intermediate events 
            double factor = (double)(source.getValue()/100.0);
            value=sliderPacket.getMin()+(sliderPacket.getMax()-sliderPacket.getMin())*factor;
            currentValue.setField(value);
            
            // try to update by adding using the same key
            PackControl.varControl.variables.put(varNameButton.getText(),new String("[SLIDER] "+Double.toString(value)));
            varModel.fireTableDataChanged();

            // execute specified command?
            if (sliderPacket.getCommandAction())
    			CPBase.trafficCenter.parseWrapper(liveCommand.getText(),CirclePack.cpb.getActivePackData(),false,true,0,null);
        }
    }
    
    // Listen for various actions
    public void actionPerformed(ActionEvent ae) {
		String command = ae.getActionCommand();
		
		if (command.equals("minval")) { // adjust min
			double minVal=sliderMin.getValue();
			if (minVal>=value) {
				minVal=value;
				sliderMin.setField(minVal);
			}
			sliderPacket.setMin(minVal);
			int tick=(int)(100*(value-minVal)/(sliderPacket.getMax()-minVal));
			fireFlag=false; // suppress event
			theSlider.setValue(tick);
			fireFlag=true;
			return;
		}
		else if (command.equals("maxval")) { // adjust max
			double maxVal=sliderMax.getValue();
			if (maxVal<=value) {
				maxVal=value;
				sliderMax.setField(maxVal);
			}
			sliderPacket.setMax(maxVal);
			int tick=(int)(100*(value-sliderPacket.getMin())/(maxVal-sliderPacket.getMin()));
			fireFlag=false; // suppress event
			theSlider.setValue(tick);
			fireFlag=true;
			return;
		}
    }
    
    public void itemStateChanged(ItemEvent ie) {
    	int state=ie.getStateChange();
    	
    	if (ie.getSource()==(Object)liveCheck) {
    		if (state==ItemEvent.SELECTED)
    			sliderPacket.setCommandAction(true);
    		else
    			sliderPacket.setCommandAction(false);
    	}
    }
    
    public void resetValue(double newValue) {
    	int tick=0;
    	if (newValue<sliderMin.getValue()) {
    		tick=0;
    		sliderPacket.setMin(newValue);
    		sliderMin.setField(newValue);
    	}
    	else if (newValue>sliderMax.getValue()) {
    		tick=100;
    		sliderPacket.setMax(newValue);
    		sliderMax.setField(newValue);
    	}
    	tick=(int)(100.0*(newValue-sliderPacket.getMin())/(sliderPacket.getMax()-sliderPacket.getMin()));
    	
    	// TODO: will this automatically call 
    	theSlider.setValue(tick);

    	currentValue.setField(newValue);
    	value=newValue;
    }
    
	/**
     * value is set or changed, this processes the expression in the 
     * ftnField, changing background to yellow if it's in error.
     * @param e
     */
    public void ftn_actionPerformed(ActionEvent e) {
    	ftnField.setBackground(Color.white);
    	if (ftnField.hasError()) {
    		CirclePack.cpb.myErrorMsg("Function parser error.");
    	}
    }
}
