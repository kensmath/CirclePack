package packing;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.Iterator;
import java.util.Vector;

import javax.swing.ImageIcon;

import allMains.CPBase;
import allMains.CirclePack;
import circlePack.PackControl;
import dcel.PackDCEL;
import exceptions.ExtenderException;
import exceptions.PackingException;
import images.CPIcon;
import input.CommandStrParser;
import input.MyConsole;
import mytools.MyTool;
import panels.CPScreen;
import util.CmdStruct;
import util.PopupBuilder;
import util.UtilPacket;

/**
 * An abstract class for optional structures/methods extending
 * 'CirclePack'. Examples are (or will be): Brooks packings,
 * dessin d'enfants, complex analysis, curvature flow, etc.
 * Some are called by abbreviations: several are hard coded 
 * in 'CommandStrParser.jexecute', but 'PackExtenderLoader'
 * should allow the user to load self-written 'PackExtenders'
 * (once they have been compiled to *.class) 
 * 
 * The idea is that when a packing is created for these special 
 * purposes, then the packing points to the PackExtender and vice 
 * verse. Commands can be constructed that apply to a packing 
 * iff it has the appropriate extender abbreviation. 
 * @author kens
 *
 */
public abstract class PackExtender {
	public String extensionType; // 'PackExtender' type, e.g., RIEMANN-HILBERT
	public String extensionAbbrev; // Abbreviation, e.g.,'RH','rh', used in commands
	public CPScreen cpScreen;  // keep cpScreen because 'PackData' can get swapped out.
	public PackData packData;  // every 'PackExtender' is associated with a single packing
	public PackDCEL pdc;       // convenience
	public String iconName="GUI/Xtender.png";  // Extender icon in Resources/Icon
	public MyTool XtenderTool;
	public String toolTip;  // for icon and startup message
	public boolean running;
	public UtilPacket extUP;  // 'UtilPacket' for capturing values from command calls
							  // (e.g., see 'CommandStrParser.valueExecute')
	public Vector<CmdStruct> cmdStruct; // catalog of commands, flags, descriptions
	
	// Constructor
	public PackExtender(PackData p) {
		packData=p;
		pdc=p.packDCEL;
		cpScreen=p.cpScreen;
		running=false;
		toolTip="No startup information provided on this PackExtender";
		XtenderTool=null;
		extUP=null;
		initCmdStruct();
	}
	
	/**
	 * Called from 'CommandStrParser'; calls first go to derived classes, then here
	 * @param cmd String
	 * @param flagSegs Vector<Vector<String>>
	 * @return UtilPacket
	 */
	public int cmdParser(String cmd,Vector<Vector<String>> flagSegs) {
		Vector<String> items=null;
		
		// ============ help ===============
		if (cmd.startsWith("help")) {
			helpInfo();
			return 1;
		}
		
		// ============ export ============
		if (cmd.startsWith("export")) {
			try {
				items=(Vector<String>)flagSegs.get(0);
				int pnum=Integer.parseInt((String)items.get(0));
				CPScreen cpS=CPBase.cpScreens[pnum];
				if (cpS!=null) {
					PackData p=packData.copyPackTo();
					return CirclePack.cpb.swapPackData(p, pnum, false);
				}
			} catch (Exception ex) {}
			return 0;
		}
		
		errorMsg("|"+extensionType+"| command not found");
		return 0;
	}
	
	/** This is called from 'CommandStrParser.valueExecute' only; calls go
	 * first to the derived class, then here
	 * @param cmd String
	 * @param flagSegs Vector<Vector<String>>
	 * @return UtilPacket
	 */
	public UtilPacket valueParser(String cmd,Vector<Vector<String>> flagSegs) {
		// nothing to do (yet); commands generally handled in the derived class 
		return null;
	}
	
	/**
	 * Replace 'this.packData' with a copy of 'newPD'. Maintain 
	 * 'this' as PackExtension, but no others. Any particular 
	 * PackExtender may have additional cleanup to do.
	 * @param newPD PackData
	 * @return NodeCount, 0 on error
	 */
	public int swapPackData(PackData newPD) {
		if (newPD==null)
			return 0;
		CPScreen holdcpS=packData.cpScreen;
		packData=newPD.copyPackTo();
		packData.packExtensions=new Vector<PackExtender>(1);
		packData.packExtensions.add(this);
		pdc=newPD.packDCEL;
		
		// reconnect pack and screen
		packData.cpScreen=holdcpS;
		holdcpS.setPackData(packData);
		packData.packNum=holdcpS.getPackNum();
		packData.setGeometry(packData.hes);

		return packData.nodeCount;
	}

	/**
	 * Show vector of commands in 'Message' tab.
	 */
	public void helpInfo() {
		helpMsg("Commands for PackExtender "+extensionType);
		for (int j=0;j<cmdStruct.size();j++) {
			CmdStruct cS=cmdStruct.get(j);
			StringBuilder bstr=new StringBuilder(cS.xCmd);
			if (cS.xFlags!=null && cS.xFlags.length()>0)
				bstr.append("  "+cS.xFlags);
			if (cS.xHint!=null && cS.xHint.length()>0)
				bstr.append("  "+cS.xHint);
			if (cS.xDescription!=null && cS.xDescription.length()>0)
				bstr.append("  "+cS.xDescription);
			helpMsg(bstr.toString());
		}
	}
	
	/** 
	 * register the extension type after confirming it does not duplicate 
	 *   existing extension for this PackData; also store help info in
	 *   'extenderArea' of Help Frame (if not already there) and add
	 *   commands to command completion hash table.
	 */
	public void registerXType() {
		Iterator<PackExtender> pXs=packData.packExtensions.iterator();
		while (pXs.hasNext()) {
			PackExtender pext=(PackExtender)pXs.next();
			if (pext.extensionAbbrev==this.extensionAbbrev) {
				running=false;
				throw new PackingException("Packing "+packData.packNum+
						" already has a PackExtender "+"'"+this.extensionAbbrev+"'");
			}
		}
		makeXTool();
		running=true;
		setHelpInfo();
	}

	/**
	 * Create tool for small canvas info area and embellish icon
	 * with initials; use 'iconName' in 'Resources/Icon'. 
	 * Can be overriden.
	 */
	public void makeXTool() {
		XtenderTool=new MyTool(new CPIcon(iconName),null,null,extensionAbbrev,
				extensionAbbrev+": "+toolTip,
				"XTEND:",false,null,(PopupBuilder)null);
		
		// embellish icon
		ImageIcon startImg =XtenderTool.getCPIcon().getImageIcon();
		Image img=startImg.getImage();
		BufferedImage bufImage=new BufferedImage(img.getWidth(null),16,BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2d=bufImage.createGraphics();
		g2d.drawImage(img,new AffineTransform(1,0,0,1,0,0),null);
		
		String abbrev=extensionAbbrev.trim();
		if (abbrev.length()>=2)
			abbrev=abbrev.substring(0,2);
		else if (abbrev.length()>0)
			abbrev=abbrev.substring(0,1);
		abbrev=abbrev.toLowerCase();
			
		Rectangle2D.Double rect=new Rectangle2D.Double(14,1,18,14);
		g2d.setColor(Color.WHITE);
		g2d.fill(rect);
		g2d.setColor(Color.BLACK);
		g2d.draw(rect);
		g2d.setFont(new Font("Helvetica",Font.ITALIC,10));
		try {
			g2d.drawString(abbrev,18,11);
		} catch (Exception ex) {}
		
		XtenderTool.getCPIcon().setImageIcon(new ImageIcon(bufImage));
		XtenderTool.setButtonIcon();
	}
	
	/**
	 * Report status: generally called just after instantiation to see if
	 * it was started successfully.
	 */
	public boolean xStatus() {
		return running;
	}
	
	public String getType() {
		return new String(extensionType);
	}
	
	public String getAbbrev() {
		return new String(extensionAbbrev);
	}

	/**
	 * Derived extenders should call this to initiate cataloging
	 * its commands.
	 */
	public void initCmdStruct() {
		cmdStruct = new Vector<CmdStruct>(5);
		cmdStruct.add(new CmdStruct("help",null,null,"List extender commands in 'Message' panel"));
		cmdStruct.add(new CmdStruct("export","p",null,"Export a copy of the parent packing to pack p"));
	}
	
	/**
	 * Before destroying a packextender, should detach it from its packData.
	 * Then the calling routine can set the extender to null.
	 */
	public void killMe() {
		packData.packExtensions.remove((Object)this);
	}
	
	/**
	 * Help info from extenders goes here
	 */
	public void helpMsg(String helpinfo) {
		CirclePack.cpb.msg(helpinfo);
	}
	
	/**
	 * Save the handle to this packExtender: eg, so not lost with copy
	 */
	public PackExtender transfer(int nodecount) {
		if (nodecount!=packData.nodeCount) {
			errorMsg("Note: "+extensionAbbrev+" 'PackExtender' is lost due to nodeCount mismatch");
			return null;
		}
		return this;
	}
	
	/**
	 * Refresh the canvas(es) for the parent packing.
	 */
	public void repaintMe() {
		PackControl.canvasRedrawer.paintMyCanvasses(packData,false);	
	}
	
	/**
	 * transparent way to send a single command to CirclePack for 'packData';
	 * handle catching of exceptions.
	 * @param cmdstr
	 * @return int count
	 */
	public int cpCommand(String cmdstr) {
		int count=0;
		try {
			count=CommandStrParser.jexecute(packData,cmdstr);
		} catch (Exception ex) {
			Oops(ex.getMessage());
		}
		return count;
	}
	
	/**
	 * transparent way to send a string of commands to CirclePack for specified
	 * packing; handle catching of exceptions.
	 * @param p PackData
	 * @param cmdstr String 
	 * @return int count
	 */
	public int cpCommand(PackData p,String cmdstr) {
		int count=0;
		try {
			count=CommandStrParser.jexecute(p,cmdstr);
		} catch (Exception ex) {
			Oops(ex.getMessage());
		}
		return count;
	}
	
	/**
	 * Kicks out an exception:
	 * @param exmsg
	 */
	public void Oops(String exmsg) {
		String str=new String("|"+extensionAbbrev+"| exception: "+exmsg);
		throw new ExtenderException(str);
	}
	
	/**
	 * Error message
	 */
	public void errorMsg(String errmsg) {
		CirclePack.cpb.myErrorMsg(extensionAbbrev+" p"+packData.packNum+" error: "+errmsg);
	}
	
	/**
	 * Regular message
	 */
	public void msg(String msG) {
		CirclePack.cpb.myMsg(extensionAbbrev+" p"+packData.packNum+": "+msG);
	}

	// Can be overridden to give general info on the extender; 
	public void StartUpMsg() {
		helpMsg(toolTip);
	}
	
	/**
	 * Add the extender type and command information to the Help Frame
	 * 'extenderArea' and add extender commands to the hash table for 
	 * command completion.
	 */
	public void setHelpInfo() {
		if (cmdStruct==null || cmdStruct.size()==0)
			return;
		StringBuilder helpinfo=new StringBuilder(toolTip+"<br>");
		String str=null;
		helpinfo.append("<table border=\"1\" width=\"100%\"></p><tr>"+
				"<td width=\"20%\" align=\"left\" valign=\"top\"><strong>Command:</strong></td>"+
				"<td width=\"18%\" align=\"left\" valign=\"top\"><strong>Flags:</strong></td>"+
				"<td align=\"left\" valign=\"top\"><strong>Description:</strong></td>"+
				"</tr>");

		for (int j=0;j<cmdStruct.size();j++) {
			CmdStruct cstr=cmdStruct.get(j);
			StringBuilder strbuf=new StringBuilder("|"+extensionAbbrev+"| ");
			helpinfo.append("<tr><td width=\"20%\" align=\"left\" valign=\"top\">");
			if ((str=cstr.getxCmd())!=null) {
				strbuf.append(str);
				helpinfo.append("<font color=\"blue\"><strong>"+
						str+"</strong></font> ");
			}
			helpinfo.append("</td>");
			helpinfo.append("<td width=\"18%\" align=\"left\" valign=\"top\">");
			if ((str=cstr.getxFlags())!=null) { 
				strbuf.append(" "+str);
				helpinfo.append(str);
			}
			helpinfo.append("</td>");
			helpinfo.append("<td align=\"left\" valign=\"top\">");
			if ((str=cstr.getxDescrip())!=null)
				helpinfo.append(str);
			helpinfo.append("</td>");
			helpinfo.append("</tr>");
			MyConsole.add2CmdCompletion(strbuf.toString());
		}
		helpinfo.append("</table>");
		PackControl.helpHover.AddXtendInfo(extensionType,extensionAbbrev,helpinfo.toString());
	}
	
}


