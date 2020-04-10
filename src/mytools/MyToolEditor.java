package mytools;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Iterator;
import java.util.Random;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;

import allMains.CPBase;
import circlePack.PackControl;
import images.CPIcon;
import images.IconComboBox;
import util.PopupBuilder;

public abstract class MyToolEditor extends JFrame implements ActionListener {

	private static final long 
	serialVersionUID = 1L;
	
	public MyToolHandler parentHandler;
	public JTextArea cmdArea;
	public CPIcon cpIcon;
	public JCheckBox jcb;
	public int Width=565;
	public int topSize; // set by subclasses
	public String iconDir; // directory containing the icon files
	public IconComboBox iconCombo;
	public Vector<CPIcon> theCPIcons;
	
	// dropability stuff set by subclasses. User sets state, dropable or not?
	// 'dropable' means you carry to the target canvas with the mouse,
	// and the command is NOT executed with l-mouse click. Else, l-mouse 
	// click applies command (to the active pack).
	public boolean wantDropBox; // display 'dropBox' in 'middlePanel'
	public boolean dropMode; // is tool dropable?
	

	private JPanel topPanel;
	private JPanel middlePanel;
	private JPanel bottomPanel;
	private JTextField tooltipField;
	private JPanel iconBoxPanel;
	private JButton clearButton;
	private JButton acceptButton;
	private JButton dismissButton;
	private JTextField nameField;
	private JCheckBox dropBox;
	private JLabel browseLabel;
	private JLabel ttLabel;
	private JLabel nameLabel;
	
	// Constructor
	public MyToolEditor() { // default constructor
		
	}
	
	public MyToolEditor(String tool_type,MyToolHandler par) {
		parentHandler=par;
		setVisible(false);
		if (tool_type==null || tool_type.length()==0) tool_type="MISC:";
		if (tool_type.equals("MAIN:")) this.setTitle("Create a Tool for the main toolbar");
		else if (tool_type.equals("BASIC:")) this.setTitle("Create a Tool for the 'basic' toolbar");
		else if (tool_type.equals("MYTOOL:")) this.setTitle("Create a Tool for a personal toolbar");
		else if (tool_type.equals("SCRIPT:")) this.setTitle("Create a Tool for the script");
		else if (tool_type.equals("MOBIUS:")) this.setTitle("Create a Mobius Transformation Tool");
		else this.setTitle("Create a miscellaneous Tool");
		dropableCheckBox(); // show dropable checkbox?

		// Build GUI: topPanel is specialized by subclass, middle/bottom standard
		initGUI(); // this is from BuildToolFrame
		if (!wantDropBox) {
			middlePanel.remove(dropBox);
		}
		else {
			dropBox.setSelected(dropMode);
		}
		this.setSize(637, topPanel.getHeight()+middlePanel.getHeight()+bottomPanel.getHeight());
		
		// add combobox for icons
	    iconCombo=new IconComboBox();
		iconBoxPanel.add(iconCombo);
	    iconCombo.setToolTipText("Select an icon image");
		theCPIcons=new Vector<CPIcon>();

	    // Set actions for bottomPanel
	    acceptButton.addActionListener(this);
	    acceptButton.setActionCommand("accept_tool");
	    dismissButton.addActionListener(this);
	    dismissButton.setActionCommand("dismiss_tool");
	    clearButton.addActionListener(this);
	    clearButton.setActionCommand("clear_tool");
	    
	}

	// subclasses must supply these routines
	public abstract String substanceText(); // error popup text for the user
	public abstract JPanel topPanel(); // return null if there's no middle panel
	public abstract String formulateCmd(); // put final CPack command in 'cmd_text'
	public abstract void dropableCheckBox(); // set 'dropBox' and 'dropMode'
	public abstract void initMoreFields(MyTool mytool); // initialize any subclass-specific datafields
	public abstract void resetMoreFields(); // clear any subclass-specific datafields
	public abstract boolean setDropDefault(); // determine default dropability

	/**
	 * update the iconlist that appears with the editor
	 *
	 */
	public void resetIconList() {
		iconCombo.setIconList(theCPIcons);
	}

	/**
	 * As MyTool's are created by the handler, copies added to theCPIcons
	 * so that the user can choose (or rechoose) them in the editors.
	 * (May eventually want info on which have been used.)
	 * @param cpIc
	 */
	public void addTool(CPIcon cpIc) {
		Iterator<CPIcon> iter=theCPIcons.iterator();
		while (iter.hasNext()) {
			CPIcon nextI=(CPIcon)iter.next();
			if (nextI.getIconName().equals(cpIc.getIconName()))
				return;
		}
		// Not already there? add new CPIcon with image of cpIc
		theCPIcons.addElement(new CPIcon(cpIc.getIconName()));
		resetIconList();
	}

	/**
	 * choose index of random CPIcon from theCPIcons
	 */
	public int randomCPIcon() {
		int sz=theCPIcons.size();
		return (new Random()).nextInt(sz);
	}

	/**
	 * returns the index if '(Object)cpIcon' is in 'theCPIcons' vector.
	 * If it doesn't find the object, it looks for an object with the
	 * same name (including directory, I think).
	 * @param cpIcon, CPIcon
	 * @return index or -1 if not found.
	 */
	public int getCPIconIndx(CPIcon cpIcon) {
		int indx=(int)theCPIcons.indexOf((Object)cpIcon);
		if (indx>=0)
			return indx;
		return getCPIconIndx(cpIcon.getIconName());
	}
	
	/**
	 * returns the index if 'theCPIcons' vector contains 
	 * CPIcon with 'name' matching 'iconname' (include directory, 
	 * eg., "script/idea.png")
	 * @param String iconname 
	 * @return -1 if not found
	 */
	public int getCPIconIndx(String iconname) {
		for (int i=0;i<theCPIcons.size();i++)
			if ((theCPIcons.get(i).getIconName().equals(iconname))) 
				return i;
		return -1;
	}

	/**
	 * returns the CPIcon at a particular index in theCPIcons
	 * @param indx
	 * @return
	 */
	public CPIcon getCPIconAt(int indx) {
		try {
			return ((CPIcon)theCPIcons.get(indx));
		} catch(ArrayIndexOutOfBoundsException ex) {
			System.err.println("Icon index out of range.");
			return (PackControl.defaultCPIcon);
		}
	}

	/**
	 * Can this be dropped into canvasses?
	 * @return boolean
	 */
	public boolean isDropable() {
		return dropMode;
	}

	/**
	 * Enter the info from MyTool into fields
	 * @param button
	 */
	public void setEntries(MyTool mytool) {
		tooltipField.setText(mytool.getToolTip());
		nameField.setText(mytool.getName());
		dropMode=isDropable();
		int indx=getCPIconIndx(mytool.getCPIcon());
		if (indx<0)	iconCombo.iconBox.setSelectedIndex(randomCPIcon());
		else iconCombo.iconBox.setSelectedIndex(indx);
		initMoreFields(mytool); // extras required in subclasses
	}

	public void clearfields() {
		tooltipField.setText("");
		nameField.setText("");
		dropMode=isDropable();
		iconCombo.iconBox.setSelectedIndex(randomCPIcon());
		resetMoreFields();
	}

	/**
	 * This abstract class only handles actions for the 3 buttons on the
	 * bottom of the edit frame.
	 */
	public void actionPerformed(ActionEvent e){
	 	String command = e.getActionCommand();
	 	if (command.equals("dismiss_tool")) {
	 		this.setVisible(false);
	 	}
	 	if (command.equals("clear_tool")) {
	 		clearfields();
	 		this.repaint();
	 	}
	 	if (command.equals("accept_tool")) {
	 		String name=(String)nameField.getText();
	 		String cmd=formulateCmd();
	 		String tip=tooltipField.getText();
	 		if (tip!=null && tip.length()==0) tip=null;
	 		String mnemonic=null;
	 		char c=' ';
	 		if (name!=null && name.length()>0 && (((c=name.charAt(0))>='A' && c<='z') ||
	 				(c>='0' && c<='9') || c=='*')) mnemonic=String.valueOf(c);
	 		CPBase.hashedTools.remove(name);
	 		// now createTool will put it back in
	 		CPIcon cpIcon=(CPIcon)iconCombo.iconBox.getSelectedItem();
	 		parentHandler.createTool(cpIcon,cmd,name,mnemonic,tip,
	 				isDropable(),(PopupBuilder)null);
	 		parentHandler.repopulateTools();
	 		this.setVisible(false);
	 	}
	}
	
	// taken from BuildToolFrame; just change topPanel to get it from subclass
	private void initGUI() {
		try {
			BoxLayout thisLayout = new BoxLayout(getContentPane(), javax.swing.BoxLayout.Y_AXIS);
			getContentPane().setLayout(thisLayout);
			setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
			{
				topPanel = topPanel();
				getContentPane().add(topPanel);
			}
			{
				middlePanel = new JPanel();
				getContentPane().add(middlePanel);
				middlePanel.setLayout(null);
				middlePanel.setBorder(BorderFactory.createTitledBorder("Standard items (optional)"));
				middlePanel.setPreferredSize(new java.awt.Dimension(611, 102));
				{
					nameLabel = new JLabel();
					nameLabel.setText("Name");
					nameLabel.setHorizontalTextPosition(SwingConstants.LEADING);
					nameLabel.setHorizontalAlignment(SwingConstants.RIGHT);
				}
				{
					ttLabel = new JLabel();
					ttLabel.setText("Tool Tip");
					ttLabel.setHorizontalAlignment(SwingConstants.RIGHT);
				}
				{
					iconBoxPanel = new JPanel();
				}
				{
					browseLabel = new JLabel();
					middlePanel.add(browseLabel);
					middlePanel.add(iconBoxPanel);
					iconBoxPanel.setBounds(535, 14, 59, 39);
					browseLabel.setText("Browse Icons");
					browseLabel.setHorizontalAlignment(SwingConstants.RIGHT);
					browseLabel.setBounds(400, 21, 128, 15);
				}
				{
					dropBox = new JCheckBox();
					dropBox.setText("Dropable?");
				}
				{
					nameField = new JTextField();
					middlePanel.add(nameField);
					middlePanel.add(dropBox);
					dropBox.setBounds(287, 19, 113, 20);
					nameField.setBounds(107, 20, 148, 20);
				}
				{
					tooltipField = new JTextField();
					middlePanel.add(tooltipField);
					middlePanel.add(nameLabel);
					middlePanel.add(ttLabel);
					ttLabel.setBounds(12, 49, 87, 16);
					nameLabel.setBounds(12, 22, 83, 16);
					tooltipField.setBounds(107, 47, 422, 21);
				}
			}
			{
				bottomPanel = new JPanel();
				getContentPane().add(bottomPanel);
				bottomPanel.setPreferredSize(new java.awt.Dimension(619, 40));
				{
					acceptButton = new JButton();
					bottomPanel.add(acceptButton);
					acceptButton.setText("Accept");
					acceptButton.setPreferredSize(new java.awt.Dimension(96, 21));
				}
				{
					dismissButton = new JButton();
					bottomPanel.add(dismissButton);
					dismissButton.setText("Dismiss");
					dismissButton.setPreferredSize(new java.awt.Dimension(91, 21));
				}
				{
					clearButton = new JButton();
					bottomPanel.add(clearButton);
					clearButton.setText("Clear");
					clearButton.setPreferredSize(new java.awt.Dimension(88, 21));
				}
			}
			pack();
			this.setSize(621, 190);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
