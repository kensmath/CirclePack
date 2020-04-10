package fauxScript;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Point;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.Vector;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.border.LineBorder;

/**
 * try to understand how to format CirclePack's 'script' frame.
 * @author kens
 *
 */
public class oldFauxFrame extends JFrame implements KeyListener, MouseListener {

	private static final long serialVersionUID = 1L;
	
	JFrame cmdFrame;
	JTextArea cmdHistory;
	JTextField cmdLine;
	JPanel fHoverPanel; // everything goes in here
	JPanel fScriptPanel; // panel where scriptBar would be
	JTextArea fhelpBar;
	JPanel fStackArea;
	JScrollPane fStackScroll;
	JPanel fCPScriptBox;
	JPanel fCPDataBox;
	Component fGlueBox;
	StringBuffer history;
	
	Vector<Component> compVector;  // contains the components, how we index them
	
    int depth;
	
	// Constructor
	public oldFauxFrame() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setTitle("Frame for testing Script layouts");
		compVector=new Vector<Component>(30);
		
		createCmdStuff();  // the command frame
		createPersistent(); // create top level stuff
		loadPersistent();  // load up the basics
		createContent(); // create cmd, text, file panels
		loadContent(); // put in all the boxes and panels
		
		// finish up the faux script frame itself
		this.setLayout(new FlowLayout());
		this.add(fHoverPanel);
		this.setLocation(new Point(80,350));
		this.setMinimumSize(new Dimension(150,30));
		this.setMaximumSize(new Dimension(600,600)); //Integer.MAX_VALUE));
		this.pack();
		this.setVisible(true);
	}

	public void createCmdStuff() {
		cmdFrame=new JFrame();
        cmdFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		cmdFrame.setTitle("faux commands");
		
		// containing panel
		JPanel cmdPanel=new JPanel();
		cmdPanel.setLayout(new BoxLayout(cmdPanel,BoxLayout.PAGE_AXIS));
		cmdPanel.setBorder(new LineBorder(Color.magenta,3,false));
		
		// Lesson: as with cmdFrame, this max doesn't limit size.
		cmdPanel.setMaximumSize(new Dimension(500,600));

		history=new StringBuffer("Commands and output go here\n");
		cmdHistory=new JTextArea();
		cmdHistory.setText(history.toString());
		cmdHistory.setEditable(false);
		cmdHistory.setBorder(new LineBorder(Color.orange,3,false));
		cmdHistory.setLineWrap(true);
		
		JScrollPane jsp=new JScrollPane(cmdHistory);
		jsp.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		jsp.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		
		// Lesson:  (??) min size on scrollpane doesn't have an effect, it fills 
		//         grows with the frame
		// Lesson: the max needs to be on the scrollpane; not its contents.
		// Lesson: long line will increase cmdHistory width, pushing beyond the
		//         window limits of both jsp and cmdHistory; Lesson: use lineWrap
		//         when available.
		
		jsp.setMinimumSize(new Dimension(305,100));
		jsp.setMaximumSize(new Dimension(405,400));
//		cmdHistory.setMinimumSize(new Dimension(300,100));
//		cmdHistory.setMaximumSize(new Dimension(300,400));
		
		
		cmdPanel.add(jsp);
		cmdLine=new JTextField();
		cmdLine.setMinimumSize(new Dimension(-1,20));
		cmdLine.setMaximumSize(new Dimension(500,20));
		cmdLine.addKeyListener(this);
		cmdPanel.add(cmdLine);
		
		// all 0 to keep to left 
		jsp.setAlignmentX(0);
		cmdLine.setAlignmentX(0);
		
		// cmdFrame itself
		
		// Lesson: max has no effect; but min does
		cmdFrame.setMinimumSize(new Dimension(400,200));
//		cmdFrame.setMaximumSize(new Dimension(300,600));
		cmdFrame.add(cmdPanel);
		cmdFrame.pack();
		cmdFrame.setLocation(new Point(20,10));
		cmdFrame.setVisible(true);
	}
	
	public void createPersistent() {
		// indices
		fhelpBar=new JTextArea("0=hoverPanel; 1=scriptPanel; 2=stackArea; 3=stackScroll; "+
				"4=CPScriptBox; 5=CPDataBox; 6=glueBox; 7-9 empty are 'cmdBox's; 10-12 are "+
				"'textBox's; 13-15 are 'fileBox's; ");
	
		// overarching panel, holds all the rest
		fHoverPanel=new JPanel(new GridBagLayout()); // 0
		fHoverPanel.addMouseListener(this);
		compVector.add(0,fHoverPanel);
	
		// fScriptPanel show panel numbers
		fScriptPanel=new JPanel(); // 1 
		fScriptPanel.setBorder(new LineBorder(Color.magenta));
		fScriptPanel.add(fhelpBar); // put the bar in here
		fScriptPanel.addMouseListener(this);
		compVector.add(1,fScriptPanel);
	
		// fStackArea
		fStackArea=new JPanel(); // 2
		fStackArea.setLayout(new BoxLayout(fStackArea,BoxLayout.PAGE_AXIS));
		fStackArea.setBorder(new LineBorder(Color.red));
		fStackArea.addMouseListener(this);
		compVector.add(2,fStackArea);
			
		fStackScroll=new JScrollPane(fStackArea); // 3
		fStackScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		compVector.add(3,fStackScroll);
	
		fCPScriptBox=new JPanel(); // 4
		fCPScriptBox.setLayout(new BoxLayout(fCPScriptBox,BoxLayout.PAGE_AXIS));
		fCPScriptBox.setBorder(new LineBorder(Color.green));
		JEditorPane jep=new JEditorPane();
		jep.setText("Colors: 1=red;2=blue;3=green;4=yellow;5=magenta;6=gray;7=orange;8=cyan");
		jep.setBorder(new LineBorder(Color.black,3,false));
		jep.setMaximumSize(new Dimension(500,20));
		jep.setAlignmentX(0);
		fCPScriptBox.add(jep);
		fCPScriptBox.addMouseListener(this);
		compVector.add(4,fCPScriptBox);
	
		fCPDataBox=new JPanel(); // 5
		fCPDataBox.setLayout(new BoxLayout(fCPDataBox,BoxLayout.PAGE_AXIS));
		fCPDataBox.setBorder(new LineBorder(Color.blue));
		fCPDataBox.addMouseListener(this);
		compVector.add(5,fCPDataBox);
	
		fGlueBox=Box.createVerticalGlue(); // 6
		compVector.add(6,fGlueBox);
	}
	
	public void loadPersistent() {
		fHoverPanel.removeAll();
		GridBagConstraints c=new GridBagConstraints();
		c.fill = GridBagConstraints.HORIZONTAL;
		c.weightx = 0.0;
		c.weighty=0.0;
		c.gridwidth = 1;
		c.gridheight=1;
		c.gridx = 0;
		c.gridy = 0;
		fHoverPanel.add(fScriptPanel,c);
		c=new GridBagConstraints();
		c.fill = GridBagConstraints.BOTH;
		c.anchor=GridBagConstraints.NORTHWEST;
		c.weightx = 1.0;
		c.weighty = 1.0;
		c.gridwidth = 1;
		c.gridheight=1;
		c.gridx = 0;
		c.gridy = 2;
		fHoverPanel.add(fStackScroll,c);
		
		fCPScriptBox.setAlignmentX(0);
		fStackArea.add(fCPScriptBox);
		fCPDataBox.setAlignmentX(0);
		fStackArea.add(fCPDataBox);
		
		fStackArea.add(fGlueBox);
	}
	
	public void createContent() {
		
		// cmd boxes
		for (int j=7;j<10;j++) {
			
			// Lesson: label versus button: button catches mouse click and has
			//         size limits; label seems to pass click on to panel
			
			JLabel label=new JLabel("label "+j);
			JEditorPane jep=new JEditorPane();
			jep.setText("cmd box "+j);
			Box box=Box.createVerticalBox();
			label.setAlignmentX(0);
			
			// Lesson: label is transparent, background of CPScriptBox shows through
			//     also 
			
			// Lesson: label seemed to be just right size without being set
//			label.setMaximumSize(new Dimension(30,-1));
			label.setBorder(new LineBorder(Color.green,4,false));
			box.add(label);
			jep.setAlignmentX(0);
			box.add(jep);
			box.addMouseListener(this);
			compVector.add(j,box);
			box.setAlignmentX(0);
		}
		
		// text boxes
		for (int j=10;j<13;j++) {
			JEditorPane jep=new JEditorPane();
			jep.setText("text box "+j);
			JScrollPane jsp=new JScrollPane(jep);
			jsp.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
			jsp.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
			JPanel panel=new JPanel();
			panel.addMouseListener(this);
			compVector.add(j,panel);
			panel.setAlignmentX(0);
		}
		
		// files boxes
		for (int j=13;j<16;j++) {
			JPanel panel=new JPanel();
			JButton button=new JButton("File "+j);
			panel.add(button);
			panel.addMouseListener(this);
			compVector.add(j,panel);
			panel.setAlignmentX(0);
		}
		
	}
	
	// put the cmd/text/file boxes in place
	public void loadContent() {
		
		// put cmd/text in stackArea
		for (int j=7;j<13;j++) {
			fCPScriptBox.add(compVector.get(j));
		}
		for (int j=13;j<16;j++) {
			fCPDataBox.add(compVector.get(j));
		}
			
	}
	
	public int execute(String cmdstr) {
		int count=0;
		String cmds[]=cmdstr.split(" ");
		Vector<Integer> numbers=getNumbers(cmds);
		
		String cmd=new String(cmds[0]);
		JComponent component=null;
		if (numbers!=null && numbers.size()>0)
			component=(JComponent)compVector.get(numbers.get(0));
		
		try {
			
			// each command expects a certain number of numbers
			if (cmd.startsWith("back")) { // set background color
				component.setBackground(myColor(numbers.get(1)));
				count++;
			}
			else if (cmd.startsWith("col")) { // set border color
				component.setBorder(new LineBorder(myColor(numbers.get(1))));
				count++;
			}
			else if (cmd.startsWith("+")) { // add component
				count=-10;
			}
			else if (cmd.startsWith("-")) { // remove component
				count=-10;
			}
			else if (cmd.startsWith("pack")) { // pack FauxFrame
				this.pack();
				count++;
			}
			else if (cmd.startsWith("val")) { // validate component
				component.validate();
				count++;
			}
			else if (cmd.startsWith("reval")) { // revalidate component
				component.validate();
				count++;
			}
			else if (cmd.startsWith("pack")) { // pack FauxFrame
				this.pack();
				count++;
			}
			else if (cmd.startsWith("ReVal")) { // revalidate fHoverPanel 
				fHoverPanel.revalidate();
				count++;
			}
			
			// look for dimension changes
			if (count==0) {
				int wide=numbers.get(1);
				int high=numbers.get(2);
				if (wide<=0) wide=-1;
				if (high<=0) high=-1;
				Dimension dim=new Dimension(wide,high);
				
				if (cmd.startsWith("md")) { // min dimension
					component.setMinimumSize(dim);
					count++;
				}
				else if (cmd.startsWith("Md")) { // max dimension
					component.setMaximumSize(dim);
					count++;
				}
				else if (cmd.startsWith("pd")) { // preferred dimension
					component.setPreferredSize(dim);
					count++;
				}
				else if (cmd.startsWith("ad")) { // max/min/pref set to same
					component.setMinimumSize(dim);
					component.setMaximumSize(dim);
					component.setPreferredSize(dim);
					count++;
				}
			}
			
			// report back
			history.append("("+count+") "+cmdstr+"\n");
			
		} catch (Exception ex) {
			history.append("error in 'execute' for "+cmdstr+"\n");
		}
		showText();
		cmdLine.setText("");
		return count;
	}
	
	public Color myColor(int col) {
		if (col==1) {return Color.red;}
		if (col==2) {return Color.blue;}
		if (col==3) {return Color.green;}
		if (col==4) {return Color.yellow;}
		if (col==5) {return Color.magenta;}
		if (col==6) {return Color.gray;}
		if (col==7) {return Color.orange;}
		if (col==8) {return Color.cyan;}
		return Color.gray;
	}
	
	// get numbers given in the command
	public Vector<Integer> getNumbers(String []str) {
		Vector<Integer> vec=new Vector<Integer>(1);
		// first entry is the command itself, rest should be numbers
		for (int j=1;j<str.length;j++) {
			try {
				vec.add(Integer.parseInt(str[j]));
			} catch (Exception ex) {
				return null;
			}
		}
		return vec;
	}
	
	public void showText() {
		cmdHistory.setText(history.toString());
	}
	
	@Override
	public void keyPressed(KeyEvent e) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void keyReleased(KeyEvent e) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void keyTyped(KeyEvent e) {
		// TODO Auto-generated method stub
		if (e.getKeyChar()==KeyEvent.VK_ENTER) {
			execute(cmdLine.getText());
		}
	}

	@Override
	public void mouseClicked(MouseEvent e) {
		// TODO Auto-generated method stub
		
		// Lesson: caught in mousePressed
//		Component comp=e.getComponent();
//		int cn=compVector.indexOf((Object)comp);
//		history.append("Component "+cn+": (w,h)=("+comp.getWidth()+" "+comp.getHeight()+")\n");
//		cmdHistory.setText(history.toString());
	}

	@Override
	public void mouseEntered(MouseEvent e) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void mouseExited(MouseEvent e) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void mousePressed(MouseEvent e) {
		// TODO Auto-generated method stub
		Component comp=e.getComponent();
		int cn=compVector.indexOf((Object)comp);
		history.append("Component "+cn+": (w,h)=("+comp.getWidth()+" "+comp.getHeight()+")\n");
		cmdHistory.setText(history.toString());
		
		// Lesson: doesn't prevent 'e' being caught by mouseClicked as well
		e.consume();
	}

	@Override
	public void mouseReleased(MouseEvent e) {
		// TODO Auto-generated method stub
		
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		JFrame frame=new oldFauxFrame();
		frame.pack();
		frame.setVisible(true);
	}

}
