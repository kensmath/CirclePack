package script;

import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.util.Iterator;
import java.util.Vector;

import javax.swing.tree.MutableTreeNode;

import circlePack.PackControl;
import exceptions.MiscException;

/**
 * The 'LineSBox' is an ephemeral abstract node for consolidating 
 * a contiguous sequence of two or more unopened 'CmdSBox's or 
 * 'FileSBox's. They are created only in 'CPTreeNode.consolidateNodes'.
 * @author kens
 *
 */
public class LineSBox extends StackBox {

	private static final long 
	serialVersionUID = 1L;

	int contentMode; // contains? 7=cmd nodes, 8=file nodes
	public int myCompHeight; // height of components going into this 'LineSBox'
	public Dimension myDim;
	
	// Constructor
	public LineSBox(CPTreeNode tnode,int mode) {
		super(tnode,mode);
		contentMode=CPTreeNode.COMMAND; // default to command
		myCompHeight=30;
		isOpen=false; // should never be open; when opened, destroy it
		buildComponents();
	}
	
	/**
	 * add from vector of 'CPTreeNode's (have to be type 7 or 8 (cmd or file))
	 * @param vec, Vector<CPTreeNode>
	 * @return count, 0 on error
	 */
	public int fillEmIn(Vector<CPTreeNode> nodeVec) {
		int count=0;
		if (nodeVec.size()==0) 
			return 0;
		contentMode=nodeVec.get(0).tntype;
		if (contentMode==CPTreeNode.COMMAND)
			myCompHeight=34;
		else if (contentMode==CPTreeNode.FILE)
			myCompHeight=30;
		else 
			throw new MiscException("'LineSBox' can only handle COMMAND and FILE nodes");
		Iterator<CPTreeNode> nodes=nodeVec.iterator();
		while (nodes.hasNext()) {
			this.tNode.add(nodes.next()); // also removes this from previous parent
			count++;
		}
		computeDim();
		setFixedSizes(this,myDim.width,myDim.height);
		this.setSize(myDim);
		return count;
	}

	/** 
	 * set 'myDim' based on 'myWidth' and 'myCompHeight'; height 
	 * is that needed to display all child nodes.
	 */
	public void computeDim() {
//System.err.println("---- 'myWidth'="+myWidth);		
		int kidcount=tNode.getChildCount();
		int solong=0;
		int sohigh=myCompHeight;
		int rowheight=-1;
		for (int i=0;i<kidcount;i++) {
			int addlength=tNode.getChild(i).stackBox.getWidth(); // caution: until displayed, this may be wrong
			solong += addlength;
			int high= tNode.getChild(i).stackBox.getHeight();
			rowheight= (high>rowheight) ? high:rowheight; 
			if (solong>(myWidth-30)) { // NOTE a little margin here.
				solong=addlength;
				sohigh+=rowheight;
				rowheight=-1;
//System.err.println("'myWidth'="+myWidth+": 'solong'="+solong+": 'sohigh'="+sohigh+": tick="+tick);
			}
		}
//System.err.println("end: ---- 'myWidth'="+myWidth+": 'solong'="+solong+": 'sohigh'="+sohigh+": tick="+tick);
		myDim = new Dimension(myWidth,sohigh);
		revalidate();
	}
	
	public void buildComponents() {
		// STACK
		this.setLayout(new FlowLayout(FlowLayout.LEADING,0,0));
//		this.setLayout(new BoxLayout(this,BoxLayout.LINE_AXIS));
		int childCount=this.tNode.getChildCount();
		if (childCount>1)
			isOpen=false;
		this.setBorder(marginBorder);
		// STACK
//		this.setBorder(new LineBorder(Color.orange));
	}
	
	public void redisplaySB(int wide) { // rebuild each time
		myWidth=wide;
		this.removeAll();
		
		// put in child StackBox's
		int childCount=tNode.getChildCount();
		for (int i=0;i<childCount;i++) {
			CPTreeNode tn=tNode.getChild(i);
			if (tn.stackBox.isOpen==true || tn.stackBox.currentMode!=DISPLAY)
				throw new MiscException("improper boxes in LineSBox");
			// command box? Just add its 'compactPanel' 
			if (tn.tntype==CPTreeNode.COMMAND) {
				CmdSBox csb=(CmdSBox)tn.stackBox;
				csb.redisplaySB(myWidth);
				this.add(csb);
			}
			// file box? Just add its icon
			if (tn.tntype==CPTreeNode.FILE) { // file
				FileSBox fsb=(FileSBox)tn.stackBox;
				fsb.redisplaySB(myWidth);
				this.add(fsb);
			}
		}
		if (childCount<2)
			discardThisBox();
		this.isOpen=false;
//		computeDim();
//		setFixedSizes(this,myDim.width+2,myDim.height);
		//AF>>>//
		// Tell Swing to redo layout after changes have been made.
		revalidate();
		//<<<AF//
		// debug
//		this.setBorder(new LineBorder(Color.orange));
	}
	
	public void paintComponent(Graphics g) {
		computeDim();
		setFixedSizes(this,myDim.width+2,myDim.height);
		revalidate();
		super.paintComponent(g);
	}
	
	public void startEdit() { // no editing of 'LineSBox's
		return;
	}
	
	public void cancelEdit() { // no editing of 'LineSBox's
		return;
	}
		
	public void acceptEdit() { // no editing of 'LineSBox's
		return;
	}
	
	/**
	 * destroy this 'LineSBox', puts its contents in its place
	 */
	public void discardThisBox() {
		open();
	}
	
	/**
	 * destroy this 'LineSBox', puts its contents in its place
	 */
	public void open() {
		if (isOpen) // should never be open
			return;
		CPTreeNode parTN=(CPTreeNode)tNode.getParent();
		if (parTN==null)
			throw new MiscException("error: LineSBox has no parent");
		int myIndex=CPTreeNode.getMyIndex(parTN,(CPTreeNode)this.tNode);
		while (this.tNode.getChildCount()>0) 
			parTN.insert((MutableTreeNode)this.tNode.getChild(0),myIndex++);
		myIndex=CPTreeNode.getMyIndex(parTN,(CPTreeNode)this.tNode);
		parTN.remove(myIndex); // remove 'LineSBox' itself
		isOpen=true;
	}
	
	/**
	 * Delete this 'LineSBox' AND all its children. This should only
	 * be called when LineSBox is empty or if directed by some ancestor
	 * SECTION node which is being deleted in its entirety. 
	 */
	public void deleteNode() {
		//AF>>>//
		// Lock the viewport so GUI changes don't jitter and relocate.
//		((LockableJViewport) PackControl.scriptHover.stackScroll.getViewport()).setLocked(true);
		//<<<AF//
		
		deleteChildNodes();
		CPTreeNode parTN=(CPTreeNode)tNode.getParent();
		try{
			if (parTN!=null)
				parTN.remove((MutableTreeNode)tNode);
		} catch(NullPointerException npe){
			//AF>>>//
			// Throwing an exception here seems to be preventing the caller from releasing
			// their viewport lock. The viewport would stay locked forever. Until this
			// NullPointerException is fixed, I'm putting this kludge here to prevent that
			// lock up from occurring.
			// TODO: Fix the NullPointerException and remove this.
//			EventQueue.invokeLater(new Runnable() {
//				public void run() {
//					LockableJViewport viewport = (LockableJViewport) PackControl.scriptHover.stackScroll.getViewport();
//					viewport.locked = false;
//					viewport.lockCount = 0;
//				}
//			});
			//<<<AF//
			throw new MiscException("error in deleting LineSBox");
		}
		manager.repopulateRecurse(parTN);
		
		//AF>>>//
		// Unlock the viewport.
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				((LockableJViewport) PackControl.scriptHover.stackScroll.getViewport()).setLocked(false);
			}
		});
		//<<<AF//
	}
	
	/**
	 * Destroy all the contents, leaving empty LINEUP, which
	 * should then itself be deleted
	 */
	public void deleteChildNodes() {
		for (int i=0;i<tNode.getChildCount();i++) {
			CPTreeNode cpTN=(CPTreeNode)tNode.getChild(i);
			cpTN.stackBox.deleteNode();
		}	
	}
	
}
