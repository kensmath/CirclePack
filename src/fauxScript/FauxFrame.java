package fauxScript;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.Vector;

import javax.swing.BorderFactory;
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
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

/**
 * try to understand how to format CirclePack's 'script' frame.
 * @author kens
 */
public class FauxFrame extends JFrame implements KeyListener, MouseListener {
	private static final long serialVersionUID = 1L;
	
	final static int MIN_WIDTH = 400; //minimum width of script window
	
	JFrame cmdFrame;
	JTextArea cmdHistory;
	JTextField cmdLine;
	JPanel fHoverPanel; //everything goes in here
	JPanel fScriptPanel; //panel where scriptBar would be
	JTextArea fhelpBar;
	JPanel fStackArea;
	JScrollPane fStackScroll;
	JPanel fCPScriptBox;
	JPanel fCPDataBox;
	Component fGlueBox;
	StringBuffer history;
	
	Vector<Component> compVector;  //contains the components, how we index them
	
    int depth;
	
	FauxFrame() {
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.setTitle("frame for testing script layouts");
		
        compVector = new Vector<Component>(30);
		
		createCmdStuff();  //the command frame
		createPersistent(); //create top level stuff
		loadPersistent();  //load up the basics
		createContent(); //create cmd, text, file panels
		loadContent(); //put in all the boxes and panels
		
		//finish up the faux script frame itself
		this.add(fHoverPanel);
		this.setLocation(new Point(160,80));
		this.setMinimumSize(new Dimension(MIN_WIDTH, 200));
		/* Frame will open at preferred size. */
		this.setPreferredSize(new Dimension(600, 300));
		/* Frames do not respect maximum size. More on this later. */
		//this.setMaximumSize(new Dimension(600,600)); //Integer.MAX_VALUE));
		this.pack();
		this.setVisible(true);
	}

	public void createCmdStuff() {
		cmdFrame = new JFrame();
        cmdFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		cmdFrame.setTitle("faux commands");
		
		//containing panel
		JPanel cmdPanel = new JPanel();
		cmdPanel.setLayout(new BoxLayout(cmdPanel, BoxLayout.PAGE_AXIS));
		cmdPanel.setBorder(new LineBorder(Color.magenta, 3, false));
		
		//Lesson: as with cmdFrame, this max doesn't limit size.
		/* It actually does limit the size of the panel. However, while the panel
		 * will stop growing once it reaches its maximum size, the frame will not.
		 * Frames do not respect their own maximum size. There is no clean way to
		 * limit frame size; instead, the interface should be designed so that the
		 * user can expand it without limit.
		 * 
		 * To accommodate this, I've set the maximum size of the panel to be
		 * practically unlimited in both height and width. The text area containing
		 * the command history is set the same way. However, I've capped the height
		 * of the command entry at 20, for both minimum and maximum, and adjusted
		 * the minimum height of the command history area accordingly (by subtracting
		 * 20).
		 */
		cmdPanel.setMinimumSize(new Dimension(400, 200));
		cmdPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

		history = new StringBuffer("Commands and output go here\n");
		cmdHistory = new JTextArea();
		cmdHistory.setText(history.toString());
		cmdHistory.setEditable(false);
		cmdHistory.setBorder(new LineBorder(Color.orange, 3, false));
		cmdHistory.setLineWrap(true);
		
		JScrollPane jsp = new JScrollPane(cmdHistory);
		jsp.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		jsp.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		
		// Lesson:  (??) min size on scrollpane doesn't have an effect, it fills 
		//         grows with the frame
		/* I believe this depends on the layout manager of the containing panel. 
		 * Not all layout managers enforce all sizes (minimum, maximum, and preferred).
		 */
		// Lesson: the max needs to be on the scrollpane; not its contents.
		/* Size enforcement is performed by the layout manager on the direct children
		 * of the panel to which it is set, but not the children's children. This is
		 * why you must set the size on the JScrollPane, not the JEditorPane it contains.
		 */
		// Lesson: long line will increase cmdHistory width, pushing beyond the
		//         window limits of both jsp and cmdHistory; Lesson: use lineWrap
		//         when available.
		
		jsp.setMinimumSize(new Dimension(400, 180)); //subtract 20 from height for command entry pane
		jsp.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
		//cmdHistory.setMinimumSize(new Dimension(300, 100));
		//cmdHistory.setMaximumSize(new Dimension(300, 400));
		
		
		cmdPanel.add(jsp);
		
		cmdLine = new JTextField();
		cmdLine.setMinimumSize(new Dimension(400, 20));
		cmdLine.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
		//cmdLine is always 20 pixels tall
		cmdLine.addKeyListener(this);
		cmdPanel.add(cmdLine);
		
		//all 0 to keep to left
		jsp.setAlignmentX(0);
		cmdLine.setAlignmentX(0);
		
		//cmdFrame itself
		//Lesson: max has no effect; but min does
		cmdFrame.setMinimumSize(new Dimension(400, 200));
		//cmdFrame.setMaximumSize(new Dimension(800, 400));
		/* I left the following code in to demonstrate how to set the layout on a frame,
		 * but honestly it's easier to just put everything in a main panel, set the layout
		 * on that, then add the panel to the frame.
		 */
		//cmdFrame.getContentPane().setLayout(new BoxLayout(cmdFrame.getContentPane(), BoxLayout.PAGE_AXIS));
		cmdFrame.add(cmdPanel);
		cmdFrame.pack();
		cmdFrame.setLocation(new Point(20,10));
		cmdFrame.setVisible(true);
	}
	
	public void createPersistent() {
		//indices
		fhelpBar = new JTextArea("components: 0=hoverPanel; 1=scriptPanel; 2=stackArea; 3=stackScroll; "+
				"4=CPScriptBox; 5=CPDataBox; 6=glueBox; 7-9 cmdBoxes; 10-12 "+
				"textBoxes; 13-15 fileBoxes;");
		/* Not totally sure why this is sizing correctly, but it is.
		 * Somehow, by not setting a minimum size, but a reasonable
		 * maximum size, the JTextArea is fitting itself to its content.
		 * Can't complain about problems solving themselves.
		 */
		//fhelpBar.setMinimumSize(new Dimension(MIN_WIDTH, 20));
		fhelpBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 640));
		fhelpBar.setLineWrap(true);
		
		//over-arching panel, holds all the rest
		/* I killed all the GridBagLayout stuff, because it was more
		 * complicated than it needed to be. It's all BoxLayout now.
		 */
		fHoverPanel = new JPanel(); //new GridBagLayout()); //0
		fHoverPanel.setLayout(new BoxLayout(fHoverPanel, BoxLayout.PAGE_AXIS));
		fHoverPanel.addMouseListener(this);
		compVector.add(0, fHoverPanel);
	
		//fScriptPanel show panel numbers
		fScriptPanel = new JPanel(); //1 
		fScriptPanel.setBorder(new LineBorder(Color.magenta));
		fScriptPanel.setLayout(new BoxLayout(fScriptPanel, BoxLayout.PAGE_AXIS));
		fScriptPanel.add(fhelpBar); //put the bar in here
		fScriptPanel.addMouseListener(this);
		compVector.add(1, fScriptPanel);
	
		//fStackArea
		/* FWSJPanel is a simple custom JPanel I wrote that will size itself
		 * correctly given the containing JScrollPane width. Check out the class
		 * file for more information.
		 */
		fStackArea = new FWSJPanel(); //2
		fStackArea.setLayout(new BoxLayout(fStackArea, BoxLayout.PAGE_AXIS));
		fStackArea.setBorder(new LineBorder(Color.red));
		fStackArea.addMouseListener(this);
		compVector.add(2, fStackArea);
			
		fStackScroll = new JScrollPane(fStackArea); //3
		fStackScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		fStackScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		compVector.add(3, fStackScroll);
	
		fCPScriptBox = new JPanel(); //4
		fCPScriptBox.setLayout(new BoxLayout(fCPScriptBox, BoxLayout.PAGE_AXIS));
		fCPScriptBox.setBorder(new LineBorder(Color.green));
		JEditorPane jep = new JEditorPane();
		jep.setText("colors: 1=red; 2=blue; 3=green; 4=yellow; 5=magenta; 6=gray; 7=orange; 8=cyan");
		jep.setBorder(new LineBorder(Color.black, 3, false));
		/* Again, not sure exactly why this is sizing correctly. BoxLayout seems to
		 * force text areas to shrink and grow to fit their contents.
		 */
		//jep.setMaximumSize(new Dimension(500,20));
		jep.setAlignmentX(0);
		fCPScriptBox.add(jep);
		fCPScriptBox.addMouseListener(this);
		compVector.add(4, fCPScriptBox);
	
		fCPDataBox = new JPanel(); //5
		fCPDataBox.setLayout(new BoxLayout(fCPDataBox, BoxLayout.PAGE_AXIS));
		fCPDataBox.setBorder(new LineBorder(Color.blue));
		fCPDataBox.addMouseListener(this);
		compVector.add(5, fCPDataBox);
	
		/* I don't think this is actually doing anything. I comment
		 * out the adding later in the code; nothing changed.
		 * (Careful, it is still needed to fill spot 6 in vector)
		 */
		fGlueBox = Box.createVerticalGlue(); //6
		compVector.add(6, fGlueBox);
	}
	
	public void loadPersistent() {
		/* Moved over GridBag stuff to BoxLayout for simplicity. */
		/*
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
		*/
		
		fHoverPanel.add(fScriptPanel);
		fHoverPanel.add(fStackScroll);
		fStackArea.add(fCPScriptBox);
		fStackArea.add(fCPDataBox);
		/* Glue doesn't seem to do anything in this case. */
		//fStackArea.add(fGlueBox);
	}
	
	public void createContent() {
		
		//cmd boxes
		for (int j = 7; j < 10; j++) {
			JLabel label = new JLabel("label " + j);
			label.setBorder(new LineBorder(Color.green, j, false));
			label.setAlignmentX(0);
			//Lesson: label versus button: button catches mouse click and has
			//        size limits; label seems to pass click on to panel
			//Lesson: label is transparent, background of CPScriptBox shows through
			//Lesson: label seemed to be just right size without being set
			/* Sizing is probably done automatically by BoxLayout. */
			//label.setMaximumSize(new Dimension(30, -1));
			
			JEditorPane jep = new JEditorPane();
			jep.setText("cmd box " + j);
			jep.setAlignmentX(0);
			
			JPanel box = new JPanel();
			box.setLayout(new BoxLayout(box,BoxLayout.PAGE_AXIS));
			box.setBorder(new EmptyBorder(0,j*j,0,j*j));//new LineBorder(Color.LIGHT_GRAY,j*j,false));
			box.setAlignmentX(0);
			box.add(label);
			box.add(jep);
			box.addMouseListener(this);
			compVector.add(j, box);
		}
		
		//text boxes
		for (int j = 10; j < 13; j++) {
			/* These weren't adding initially, because the scroll panes
			 * were never added to their panels. Fixed that, but for the life
			 * of me I cannot get them to size in a sensible fashion - I think
			 * it has something to do with putting JScrollPanes in JScrollPanes.
			 * They do really bizarre things, especially when you minimize and then
			 * restore. Ran out of time to keep investigating this, unfortunately.
			 */
			JEditorPane jep = new JEditorPane();
			jep.setText("text box " + j);
			jep.setMinimumSize(new Dimension(MIN_WIDTH, 100));
			jep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));
			
			JScrollPane jsp = new JScrollPane(jep);
			jsp.setMinimumSize(new Dimension(MIN_WIDTH, 100));
			jsp.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));
			jsp.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
			jsp.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
			
			JPanel panel = new JPanel();
			panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));
			panel.setMinimumSize(new Dimension(MIN_WIDTH, 100));
			panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));
			panel.add(jsp);
			panel.addMouseListener(this);
			panel.setAlignmentX(0);
			compVector.add(j, panel);
		}
		
		//file boxes
		for (int j = 13; j < 16; j++) {
			JPanel panel = new JPanel();
			
			JButton button = new JButton("File " + j);
			/* We are probably setting the alignment more than necessary, but
			 * I can't figure out exactly when it is. I think it has to do with
			 * components having different default alignments: most are left, but
			 * some (I believe JButtons and JLabels particularly) are center
			 * instead. It might only be necessary to set the alignment on buttons
			 * and labels in our case. I didn't look too much into this, though,
			 * because I doubt there is much overhead on setAlignment calls.
			 */
			button.setAlignmentX(0);
			
			/* Empty border for pretty spacing, though not necessary. */
			panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
			panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));
			panel.add(button);
			panel.addMouseListener(this);
			compVector.add(j,panel);
		}
	}
	
	//put the cmd/text/file boxes in place
	public void loadContent() {
		//put cmd/text in stackArea
		for (int j = 7; j < 13; j++) fCPScriptBox.add(compVector.get(j));
		for (int j=13;j<16;j++) fCPDataBox.add(compVector.get(j));
	}
	
	public int execute(String cmdstr) {
		int count = 0;
		String cmds[] = cmdstr.split(" ");
		Vector<Integer> numbers = getNumbers(cmds);
		
		String cmd = new String(cmds[0]);
		JComponent component = null;
		if (numbers != null && numbers.size() > 0)
			component = (JComponent) compVector.get(numbers.get(0));
		
		try {
			//each command expects a certain number of numbers
			if (cmd.startsWith("back")) { //set background color
				component.setBackground(myColor(numbers.get(1)));
				count++;
			}
			else if (cmd.startsWith("col")) { //set border color
				component.setBorder(new LineBorder(myColor(numbers.get(1))));
				count++;
			}
			else if (cmd.startsWith("+")) { //add component
				count=-10;
			}
			else if (cmd.startsWith("-")) { //remove component
				count=-10;
			}
			else if (cmd.startsWith("pack")) { //pack FauxFrame
				this.pack();
				count++;
			}
			else if (cmd.startsWith("val")) { //validate component
				component.validate();
				count++;
			}
			else if (cmd.startsWith("reval")) { //revalidate component
				component.validate();
				count++;
			}
			else if (cmd.startsWith("pack")) { //pack FauxFrame
				this.pack();
				count++;
			}
			else if (cmd.startsWith("ReVal")) { //revalidate fHoverPanel 
				fHoverPanel.revalidate();
				count++;
			}
			
			//look for dimension changes
			if (count == 0) {
				int wide = numbers.get(1);
				int high = numbers.get(2);
				if (wide <= 0) wide = -1;
				if (high <= 0) high = -1;
				Dimension dim = new Dimension(wide, high);
				
				if (cmd.startsWith("md")) { //min dimension
					component.setMinimumSize(dim);
					count++;
				}
				else if (cmd.startsWith("Md")) { //max dimension
					component.setMaximumSize(dim);
					count++;
				}
				else if (cmd.startsWith("pd")) { //preferred dimension
					component.setPreferredSize(dim);
					count++;
				}
				else if (cmd.startsWith("ad")) { //max/min/pref set to same
					component.setMinimumSize(dim);
					component.setMaximumSize(dim);
					component.setPreferredSize(dim);
					count++;
				}
			}
			
			//report back
			history.append("("+count+") "+cmdstr+"\n");
		} catch (Exception ex) {
			history.append("error in 'execute' for "+cmdstr+"\n");
		}
		
		showText();
		cmdLine.setText("");
		return count;
	}
	
	public Color myColor(int col) {
		if (col == 1) {return Color.red;}
		if (col == 2) {return Color.blue;}
		if (col == 3) {return Color.green;}
		if (col == 4) {return Color.yellow;}
		if (col == 5) {return Color.magenta;}
		if (col == 6) {return Color.gray;}
		if (col == 7) {return Color.orange;}
		if (col == 8) {return Color.cyan;}
		return Color.gray;
	}
	
	//get numbers given in the command
	public Vector<Integer> getNumbers(String []str) {
		Vector<Integer> vec = new Vector<Integer>(1);
		//first entry is the command itself, rest should be numbers
		for (int j = 1; j < str.length; j++) {
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
	public void keyPressed(KeyEvent ke) {
		
	}

	@Override
	public void keyReleased(KeyEvent ke) {
		
	}

	@Override
	public void keyTyped(KeyEvent ke) {
		if (ke.getKeyChar() == KeyEvent.VK_ENTER) {
			execute(cmdLine.getText());
		}
	}

	@Override
	public void mouseClicked(MouseEvent me) {
		//Lesson: caught in mousePressed
		//Component comp = me.getComponent();
		//int cn = compVector.indexOf((Object) comp);
		//history.append("Component " + cn + ": (w,h)=(" + comp.getWidth() + " " + comp.getHeight() + ")\n");
		//cmdHistory.setText(history.toString());
	}

	@Override
	public void mouseEntered(MouseEvent me) {
		
	}

	@Override
	public void mouseExited(MouseEvent me) {
		
	}

	@Override
	public void mousePressed(MouseEvent me) {
		Component comp = me.getComponent();
		int cn = compVector.indexOf((Object) comp);
		history.append("Component " + cn + ": (w,h)=(" + comp.getWidth() + " " + comp.getHeight() + ")\n");
		cmdHistory.setText(history.toString());
		
		//Lesson: doesn't prevent 'e' being caught by mouseClicked as well
		/* mouseClicked events will also fire mousePressed and mouseReleased
		 * events, as a mouse click entails both a pressing and a release.
		 * It is best not to use all three together, as a mouse click will fire
		 * them all at once.
		 * 
		 * Consuming mouse events prevents components below from also receiving
		 * the events. The mouse event will drop through all components
		 * at those coordinates until it is consumed.
		 */
		me.consume();
	}

	@Override
	public void mouseReleased(MouseEvent me) {
		
	}

	public static void main(String[] args) {
		@SuppressWarnings("unused")
		JFrame frame = new FauxFrame();
	}
}