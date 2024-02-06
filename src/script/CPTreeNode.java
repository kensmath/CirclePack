package script;
/*--*--*--*--*--*--*--*--*--*--*--*--*--*--*--*--*--*--*--*
  Benjamin A. Pack III; Ken Stephenson
  Use:  Used by the script handler and script panel to display
    information pertaining to the script in tree form.  The JTree
    within the script panel is populated with CPTreeNodes
    created from DOM tree nodes when the script is loaded or added
    later. These are used both for the script tree display and the 
    StackBox's of the script display itself. Command CPTreeNode's 
    (and only these) must have MyTools.
 *--*--*--*--*--*--*--*--*--*--*--*--*--*--*--*--*--*--*--*/

import java.util.Vector;

import javax.swing.ImageIcon;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.MutableTreeNode;

import circlePack.PackControl;
import exceptions.MiscException;
import images.CPIcon;
import mytools.MyTool;

public class CPTreeNode extends DefaultMutableTreeNode {

  private static final long 
  serialVersionUID = 1L;
	
  // Instance Variables
  public String displayString;
  protected boolean isActiveCmdNode;
  protected int tntype;
  protected boolean isInline;
  protected boolean isCursor;
  protected boolean isHandy;
  protected boolean isNamed;
  protected boolean tipStart; // cmd nodes: set on creation only if tip is set
  protected MyTool tTool; // contains image and name of icon
  protected ImageIcon nodeIcon;
  public StackBox stackBox;
//  public int width; // moved this info to 'stackBox's, 8/11
  protected CPTreeNode parent;

  // Class Variables
  // Things that can have children
  public static final int ROOT = 1;
  public static final int CPSCRIPT = 2;
  public static final int CPDATA = 3;
  public static final int SECTION = 4;
  public static final int LINEUP = 5;
  
  // leaf nodes
  public static final int TEXT = 6; 
  public static final int COMMAND = 7;
  public static final int FILE = 8;
  public static final int MODE = 9;
  public static final int OTHER = 10;

  // in midst of creation/editing
  public static final int EDIT_CMDorMODE=11;
  public static final int EDIT_TEXT=12;
  public static final int EDIT_SECTION=13;
  public static final int EDIT_FILE=14;
  
  // static default icons
  protected static ImageIcon rootIcon=CPIcon.CPImageIcon("script/rootIcon.png");
  protected static ImageIcon scriptIcon=CPIcon.CPImageIcon("script/scriptIcon.png");
  protected static ImageIcon  dataIcon=CPIcon.CPImageIcon("script/dataIcon.png");
  protected static ImageIcon  sectionIcon=CPIcon.CPImageIcon("script/sectionIcon.png");
  protected static ImageIcon  commandIcon=CPIcon.CPImageIcon("script/commandIcon.png");
  protected static ImageIcon  regionIcon=CPIcon.CPImageIcon("script/small_T_Icon.png");
  protected static ImageIcon  otherIcon=CPIcon.CPImageIcon("script/otherIcon.png");
  protected static ImageIcon  nowEditingIcon=CPIcon.CPImageIcon("script/e_edit.png");
  protected static ImageIcon  fileIcon=CPIcon.CPImageIcon("script/fileIcon.png");

  // Constructors
  public CPTreeNode(String s,int mytype,boolean inLine,MyTool ttool) {
    displayString = s;
    tntype = mytype;
    isInline=false; // isInline can only be true for COMMAND nodes.
    tTool=ttool;
    nodeIcon=null; // not needed for all nodes (e.g., file)
    if (tntype==COMMAND || tntype==MODE || tntype==EDIT_CMDorMODE) {
    	isInline=inLine;
    	try {
        	nodeIcon=tTool.getCPIcon().getImageIcon();
    	} catch(Exception ex) {
    		System.err.println("error creating COMMAND/MODE CPTreeNode, no MyTool.");
        	nodeIcon=commandIcon;
    	}
    }
    else {
    	if (tntype==ROOT) nodeIcon=rootIcon;
    	else if (tntype==CPSCRIPT) nodeIcon=scriptIcon;
    	else if (tntype==CPDATA) nodeIcon=dataIcon;
    	else if (tntype==SECTION || tntype==EDIT_SECTION) nodeIcon=sectionIcon;
    	else if (tntype==TEXT || tntype==EDIT_TEXT) nodeIcon=regionIcon;
    	else if (tntype==FILE || tntype==EDIT_FILE) nodeIcon=fileIcon;
    	else if (tntype==OTHER) nodeIcon=otherIcon;
    	else if (tntype>OTHER) nodeIcon=nowEditingIcon;
    	else nodeIcon=PackControl.defaultCPIcon.getImageIcon();
    }
    isActiveCmdNode = false;
    isNamed = false; // only COMMAND type can be named.

    if((tntype == COMMAND || tntype==MODE) && ttool!=null){ // must have MyTool
    	if (ttool.getName()!=null && ttool.getName().trim().length()>0) 
    		isNamed=true;
    }
    
    // deprecated: don't us icons now
//    set appropriate icon based on first char of filename extension
//    else if (tntype==FILE || tntype==EDIT_FILE) {  
//		int lindx;
//		if ((lindx=s.lastIndexOf("."))>0 && lindx<(s.length()-1)) {
//			nodeIcon=fileIcon_r;
//			char li=s.charAt(lindx+1);
//			if (li=='p' || li=='q') nodeIcon=fileIcon_p;
//			else if (li=='g') nodeIcon=fileIcon_g;
//			else if (li=='c') nodeIcon=fileIcon_c;
//			else if (li=='x') nodeIcon=fileIcon_x;
//			else if (li=='r') nodeIcon=fileIcon_r;
//		}
//    }
    // set text icon
    else if (tntype==TEXT || tntype==EDIT_TEXT) nodeIcon=regionIcon;
    
	if (tntype==CPSCRIPT || tntype==CPDATA || tntype==ROOT ||
			tntype==SECTION || tntype==LINEUP || 
			tntype==EDIT_SECTION)
		allowsChildren=true;
	else allowsChildren=false;
	
    // create appropriate StackBox for script display
    if (tntype!=ROOT) {
    	switch(tntype) {
    	case CPSCRIPT: {
    		stackBox=new ScriptSBox(this,StackBox.DISPLAY);
    		break;
    	}
    	case CPDATA: {
    		stackBox=new DataSBox(this,StackBox.DISPLAY);
    		break;
    	}
    	// Note: 'LineSBox's are created/destroyed on the fly
    	case LINEUP: {
    		stackBox=new LineSBox(this,StackBox.DISPLAY);
    		break;
    	}
    	case SECTION: {
    		stackBox=new SectionSBox(this,StackBox.DISPLAY);
    		break;
    	}
    	case EDIT_SECTION: {
    		stackBox=new SectionSBox(this,StackBox.NEW);
    		break;
    	}
    	case TEXT: {
    		stackBox=new TextSBox(this,StackBox.DISPLAY);
    		break;
    	}
    	case EDIT_TEXT: {
    		stackBox=new TextSBox(this,StackBox.NEW);
    		break;
    	}
    	case FILE: {
    		boolean gotit=false;
    		int j;
			for (j=PackControl.scriptManager.includedFiles.size()-1;(j>=0 && !gotit);j--) {
				IncludedFile iFile=(IncludedFile)PackControl.scriptManager.includedFiles.get(j);
				if (iFile.origName.equals(displayString)) {
		    		stackBox=new FileSBox(this,StackBox.DISPLAY,iFile);
		    		gotit=true;
				}
			}
    		break;
    	}
    	case COMMAND: {
    		stackBox=new CmdSBox(this,StackBox.DISPLAY);
    		break;
    	}
    	case MODE: {
    		stackBox=new CmdSBox(this,StackBox.DISPLAY);
    		break;
    	}
    	case EDIT_CMDorMODE: {
    		stackBox=new CmdSBox(this,StackBox.NEW);
    		break;
    	}
    	} // end of switch
    }
  }
  
  public CPTreeNode(String s,int mytype,MyTool ttool) {
	  this(s,mytype,false,ttool);
  }

  public CPTreeNode(String s,int mytype) {
	  this(s,mytype,false,null);
  }

  public String toString(){
    return displayString;
  }

  public int getType(){
    return tntype;
  }
  
  public String getName(){ // convenience to get to MyTool's name
	  if (!isNamed()) return null;
	  return tTool.getName();
  }

  /**
   * Only inline command nodes can be active.
   */
  public boolean isActive(){
    return isActiveCmdNode;
  }
 
  public String getImageString() {
	  return nodeIcon.toString();
  }
  
  public void setNodeIcon(ImageIcon imIc) {
	  nodeIcon=imIc;
  }

  public boolean isInline() {
    return isInline;
  }
  
  public void setInline(boolean bool) {
	  isInline=bool;
  }
  
  public boolean isDropable() {
	  if ((tntype==COMMAND || tntype==MODE || tntype==EDIT_CMDorMODE) && 
			  tTool!=null)
		  return tTool.isDropable();
	  else return false;
  }
  
  public boolean isCursor() {
	  return isCursor;
  }
  
  /**
   * Can be 'handy' only if it is a cursor first
   * @return
   */
  public boolean isHandy() {
	  if (isCursor) return isHandy;
	  return false;
  }

  public boolean isNamed() {
	  if (tTool==null) return false;
	  return tTool.isNamed();
  }
  
  /**
   * Does this COMMAND or MODE node have #XY?
   * @return
   */
  public boolean isXY() {
	  if (tTool==null || (tntype!=COMMAND && tntype!=MODE)) return false;
	  if ((tntype==MODE || tntype==COMMAND) && tTool.getCommand().contains("#XY")) return true;
	  else return false;
  }

  /**
   * Return index of 'refNode' as child of 'parentTN'.
   * @param parentTN
   * @param refNode
   * @return int, -1 if not a child
   */
  public static int getMyIndex(CPTreeNode parentTN,CPTreeNode refNode) {
	  int hit=-1;
	  int childcount=parentTN.getChildCount();
	  CPTreeNode aTN;
	  for (int i=0;(i<childcount && hit<0);i++) {
		  aTN=(CPTreeNode)parentTN.getChild(i);
		  if (aTN==refNode)
			  hit=i;
	  }
	  return hit;
  }
  
	/**
	 * Find maximal contiguous sequence of nodes containing (and of
	 * same type, either 7==COMMAND, or 8=FILE, or 8=MODE, or 5=LINEUP) 
	 * as refNode which are closed and in DISPLAY mode.
	 * (all these are to be children of same parent.)
	 * This is used in creating 'LineSBox's to organize members of this
	 * sequence.
	 * @return int[4]: [0] set to -1 if type is not 5,7, or 8. Otherwise,
	 * these are indices within parent node: 
	 * [0]=first in contig sequence, [1]=index of refNode itself, 
	 * [2]=last in contig sequence, [3]=childcount.
	 */
	public int []findContig578() {
		int []ans=new int[4];
		int type=tntype;
		if ((type!=CPTreeNode.COMMAND && type!=CPTreeNode.MODE &&
				type!=CPTreeNode.FILE && type!=CPTreeNode.LINEUP) || 
		  stackBox.isOpen) {
			ans[0]=-1;
			return ans;
		}
		CPTreeNode parTN=(CPTreeNode)getParent();
		int childCount=ans[3]=parTN.getChildCount();
		int refspot=CPTreeNode.getMyIndex(parTN,this);
		if (refspot<0) {
			throw new MiscException("Error is finding node in tree");
		}
		
		ans[0]=ans[1]=ans[2]=refspot;
		CPTreeNode aTN;
		while (ans[0]>0 && (aTN=(CPTreeNode)parTN.getChild(ans[0]-1)).tntype==type &&
				!aTN.stackBox.isOpen()) {
			ans[0]--;
		}
		
		while (ans[2]<(childCount-1) && (aTN=(CPTreeNode)parTN.getChild(ans[2]+1)).tntype==type &&
				!aTN.stackBox.isOpen()) {
			ans[2]++;
		}
		return ans;
	}
	
	/** 
	 * Consolidation: recursively check child structures under CPSCRIPT, 
	 * SECTION, or CPData for possible consolidations into 'LineSBox's. 
	 * This is where 'LineSBox's are created, they are destroyed when 
	 * nodes in them are opened, deleted, created, edited, etc.
	 * @return -1 on error, count of consolidation actions
	 */
	public int consolidateNodes() {
		
		int count=0;
		if ((tntype!=CPTreeNode.CPSCRIPT && tntype!=CPTreeNode.CPDATA &&
			tntype!=CPTreeNode.SECTION) || !stackBox.isOpen()) 
			return -1;
		int spot;
		boolean hit=true;
		while (hit) {
			hit=false;
			for (spot=getChildCount()-1;spot>=0;spot--) {
				CPTreeNode spotNode=getChild(spot);
				int []contigData=spotNode.findContig578();
				int n;
				Vector<CPTreeNode> nodeVec=new Vector<CPTreeNode>();
				CPTreeNode lineTN;
			
				// first, do we have 'LineSbox's to consolidate?
				if (spotNode.tntype==CPTreeNode.LINEUP && contigData[0]>=0) {
					n=(contigData[1]-contigData[0])+1;
					if (n>1) {
						// gather nodes from the downstream 'LineSBox's
						for (int j=n-1;j>0;j--) {
							lineTN=getChild(contigData[0]+j);
							int kidCount=lineTN.getChildCount();
							for (int k=(kidCount-1);k>=0;k--)
								nodeVec.add((CPTreeNode)lineTN.getChild(k));
							remove(lineTN);
						}
						// put them into the first; careful with order
						lineTN=getChild(contigData[0]);
						for (int v=nodeVec.size()-1;v>=0;v--)
							lineTN.add((MutableTreeNode)nodeVec.elementAt(v));
						count++;
						hit=true;
						spot=contigData[0];
					}
				}
				
				// do we have nodes to consolidate?
				else if ( (((tntype==CPTreeNode.CPSCRIPT || tntype==CPTreeNode.SECTION) && 
						(spotNode.tntype==CPTreeNode.COMMAND || spotNode.tntype==CPTreeNode.MODE)) ||
						(tntype==CPTreeNode.CPDATA && spotNode.tntype==CPTreeNode.FILE)) && 
						contigData[0]>=0) {
					n=(contigData[1]-contigData[0])+1;
					if (n>1) {
						nodeVec=new Vector<CPTreeNode>(n+1);
						for (int j=0;j<n;j++) { // have to get in right order
							int m=contigData[0]+j;
							nodeVec.add(j,(CPTreeNode)getChild(m));
						}
						for (int j=(n-1);j>=0;j--) {
							int m=contigData[0]+j;
							remove(m);
						}
				
						// create new 'LineSBox' node, move these nodes into it
						CPTreeNode newTN=new CPTreeNode((String)null,CPTreeNode.LINEUP);
						LineSBox lsb=(LineSBox)(newTN.stackBox);
						lsb.fillEmIn(nodeVec); 
						insert((MutableTreeNode)lsb.tNode,contigData[0]);
						count++;
						lsb.redisplaySB(lsb.myWidth);
						hit=true;
						spot=contigData[0];
					}
					else { // only one node, closed and in DISPLAY mode
					
						// absorb into following 'LineSBox'?
						if (contigData[0]<(getChildCount()-1)) {
							CPTreeNode postTN=getChild(contigData[0]+1);
							if (postTN.tntype==CPTreeNode.LINEUP) {
								postTN.insert(getChild(contigData[0]),0);
								count++;
								hit=true;
								spot=contigData[0]-1;
							}
						}
						// add to preceeding 'LineSBox'?
						else if (contigData[0]>0) {
							CPTreeNode postTN=getChild(contigData[0]-1);
							if (postTN.tntype==CPTreeNode.LINEUP) {
								postTN.add(getChild(contigData[0]));
								count++;
								hit=true;
								spot=contigData[0]-1;
							}
						}
						
					}
				}
			}
		} // end of for loop on 'spot'
		return count;
	}
	
  public CPTreeNode getChild(int j) {
	return (CPTreeNode)super.getChildAt(j);
  }
  
  public void debugSize() {
	  String state=" closed ";
	  if (stackBox.isOpen)
		  state=" open ";
	  System.err.println("type="+tntype+state+
				" w="+stackBox.getWidth()+" h="+stackBox.getHeight()+"; depth="+stackBox.depth);
  }
  
}
