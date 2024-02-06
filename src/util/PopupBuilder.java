package util;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Vector;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import allMains.CPBase;
import canvasses.ActiveWrapper;
import exceptions.ParserException;

/**
 * A utility class for constructing popup menus from
 * xml data elements in 'myTool' files. Currently (3/09)
 * the only elements are <menu> <item> <cmd>.
 * @author kens
 *
 */
public class PopupBuilder extends JPopupMenu {

	private static final long 
	serialVersionUID = 1L;

	private Vector<Num2Cmd> ncVector;
	public String menuName;
	Popup_actionAdapter pop_actionAdapter; // class at end of this file
	ActiveWrapper parentWrapper;

	// Constructor
	public PopupBuilder(Node node,ActiveWrapper aW) { // node should be the 'menu' Element
		super();
		parentWrapper=aW;
		menuName=null;
		ncVector=new Vector<Num2Cmd>(5);
		int N=0;
		pop_actionAdapter = new Popup_actionAdapter(this);
		
		// see if there's a menu 'heading' attribute, use it as name
		NamedNodeMap nMap=node.getAttributes();
		String menuHeading=nMap.getNamedItem("heading").getFirstChild().getNodeValue().trim();
		if (menuHeading!=null && menuHeading.length()>0) 
			menuName=menuHeading;
		
		// look at children: currently 'item' and 'submenu' elements
		NodeList ellist=node.getChildNodes();
		for (int j=0;j<ellist.getLength();j++) {
			Node item=(Node)ellist.item(j);
			NamedNodeMap itemMap=item.getAttributes();
			String name=item.getNodeName();
			if (name.equals("item")) {
				
				// get 'text' attribute
				String text=itemMap.getNamedItem("text").getNodeValue().trim();
				
				// search children for 'cmd'
				NodeList nl=item.getChildNodes();
				for (int k=0;k<nl.getLength();k++) {
					Node nn=nl.item(k);
					if (nn.getNodeName().equals("cmd")) {
						String command=nn.getFirstChild().getNodeValue().trim();
						ncVector.add(new Num2Cmd(N,command));
						JMenuItem mi=new JMenuItem(text);
						mi.setActionCommand(Integer.valueOf(N).toString());
						mi.addActionListener(pop_actionAdapter);
						this.add(mi);
						N++;
					}
				}
			}
			else if (name.equals("submenu")) {
				
				// get 'heading'
				String subHeading=itemMap.getNamedItem("heading").getFirstChild().getNodeValue().trim();
				if (subHeading==null || subHeading.length()==0)
					subHeading=new String("<no heading>");
				JMenu submenu=new JMenu(subHeading);
				
				// search for 'cmd' and 'item' elements
				NodeList ellist2=item.getChildNodes();
				for (int jj=0;jj<ellist2.getLength();jj++) {
					Node item2=(Node)ellist2.item(jj);
					NamedNodeMap itemMap2=item2.getAttributes();
					String name2=item2.getNodeName();
					// might be 'cmd' for the submenu itself
					if (name2.equals("cmd")) {
						String command2=item2.getFirstChild().getNodeValue().trim();
						ncVector.add(new Num2Cmd(N,command2));
						JMenuItem mi=new JMenuItem(subHeading);
						mi.setActionCommand(Integer.valueOf(N).toString());
						mi.addActionListener(pop_actionAdapter);
						// don't add to menu; must figure out how to access
//						this.add(mi); 
						N++;
					}
					if (name2.equals("item")) {
						// get 'text' attribute
						String text2=itemMap2.getNamedItem("text").getNodeValue().trim();
						// search for 'cmd'
						NodeList nl2=item2.getChildNodes();
						for (int kk=0;kk<nl2.getLength();kk++) {
							Node nn2=nl2.item(kk);
							String name3=nn2.getNodeName();
							if (name3.equals("cmd")) {
								String command3=nn2.getFirstChild().getNodeValue().trim();
								ncVector.add(new Num2Cmd(N,command3));
								JMenuItem mi=new JMenuItem(text2);
								mi.setActionCommand(Integer.valueOf(N).toString());
								mi.addActionListener(pop_actionAdapter);
								submenu.add(mi);
								N++;
							}
						}
					}
				} // end of loop through submenu children
				this.add(submenu);
			} // done with submenu branch
		} // end of loop through menu children
	}
	
	public void do_action(ActionEvent e) {
		int actN=Integer.parseInt(e.getActionCommand());
		for (int n=0;n<ncVector.size();n++) {
			Num2Cmd n2c=ncVector.get(n);
			if (actN==n2c.Num) {
				try {
					CPBase.trafficCenter.parseWrapper(n2c.actionCmd,
							parentWrapper.getCPDrawing().getPackData(),false,true,0,null);
					n=ncVector.size(); // kick out
				} catch (Exception ex) {
					throw new ParserException("popup menu error: "+ex.getMessage());
				}
			}
		}
	}

}

/**
 * Local class for holding number (the 'ActionCommand')
 * and the string of CirclePack commands.
 * @author kens
 *
 */
class Num2Cmd {

	public int Num;
	public String actionCmd;
	
	public Num2Cmd(int N,String action) {
		Num=N;
		actionCmd=new String(action);
	}
	
}

class Popup_actionAdapter implements ActionListener {
	private PopupBuilder adaptee;

	Popup_actionAdapter(PopupBuilder adaptee) {
		this.adaptee = adaptee;
	}

	public void actionPerformed(ActionEvent e) {
		adaptee.do_action(e);
	}
} 