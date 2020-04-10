package frames;

import java.awt.Container;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Insets;
import java.awt.image.BufferedImage;

import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.border.EmptyBorder;

import allMains.CPBase;

/**
 * When Opening CirclePack in "minimal" mode (just the main
 * or paired frame), an "About" frame should automatically
 * popup. It displays the current script "description" and
 * image, a way to give new users a place to start with the GUI.
 * @author kens
 *
 */
public class AboutFrame extends JFrame {
	
	private static final long 
	serialVersionUID = 1L;

	public JTextPane infoPane;
	public JTextPane aboutPane;
	JScrollPane aboutScroller;
	public static int ABOUTWIDTH=250;
	public static int ABOUTHEIGHT=250;
	
	// bounds for tag icon
	public static int ICONHEIGHT=200; // TODO: what size is best? 300;
	public static int ICONWIDTH=200; // 300;

	// Constructor
	public AboutFrame() {
		this.setAlwaysOnTop(false);
		this.setTitle("CirclePack, by Ken Stephenson");
	}
	
	/**
	 * This is not currently in use: it creates 'infoPane' with
	 * image and copyright info.
	 */
	public void makeInfo() {	
		infoPane=new JTextPane();
		infoPane.setContentType("text/html");
		infoPane.setBorder(new EmptyBorder(new Insets(2,2,2,2)));
		infoPane.setEditable(false);
		
		StringBuilder infoStuff=new StringBuilder("<html><body>\n");
		infoStuff.append("<table width=\""+ABOUTWIDTH+"\">");
		
		// owl image
		infoStuff.append("<tr><td width=\"35%\"><center>"+
				"<IMG SRC=\""+CPBase.getResourceURL("/Icons/GUI/Owl_90x90.jpg")+
				"\" WIDTH=90 HEIGHT=90><br>"+
				"<em><strong>CirclePack</strong></em></center></td>");
		
		// copyright, etc.
		infoStuff.append("<td width=\"65%\">"+
				"<center>Ken Stephenson<br>"+
				"University of Tennessee<p>"+
				"kens@math.utk.edu<p>"+
				"Copyright 1992-2011</center></td>");

		// book jacket
//		infoStuff.append("<td width=\"25%\"><center>"+
//				"<IMG SRC=\""+CPBase.getResourceURL(File.separator+
//				"Icons"+File.separator+"GUI"+File.separator+"BookJacket.jpg")+
//				"\" WIDTH=110 HEIGHT=150></center></td>");
		
		infoStuff.append("</tr></table></body></html>");
		infoPane.setText(infoStuff.toString());
	}
	
	public void openAbout() {
		openAbout(50,50); // (50,50);
	}
	
	public void openAbout(int X,int Y) {
		Container pane=this.getContentPane();
		pane.removeAll();
		JPanel panel=new JPanel();
		panel.setLayout(new BoxLayout(panel,BoxLayout.LINE_AXIS));
		
		// Put in pane for description
		aboutPane=new JTextPane();
		aboutPane.setContentType("text/html");
		aboutPane.setBorder(new EmptyBorder(new Insets(1,6,1,6)));
		aboutPane.setEditable(false);

		// add the script name and description in html
		StringBuilder aboutText=new StringBuilder("<html>");
		aboutText.append("<body>");
//		aboutText.append("<basefont size=\"2\">");
		aboutText.append("<h2><em><strong><font color=\"blue\">"+
				"About:  </font></strong>"+
				CPBase.scriptManager.scriptName+"</em></h2>");
		if (CPBase.scriptManager.scriptDescription==null || 
				CPBase.scriptManager.scriptDescription.trim().length()==0)
			aboutText.append("no script 'description' is available<br>");
		else
			aboutText.append(CPBase.scriptManager.scriptDescription.replace("\n","<br>")+"<br>");
		aboutText.append("</body></html>");
		
		// put in aboutPane
		aboutPane.setText(aboutText.toString());
		aboutScroller = new JScrollPane(aboutPane);
		aboutScroller.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		aboutScroller.setPreferredSize(new Dimension(ABOUTWIDTH,ABOUTHEIGHT));
		aboutScroller.setBounds(0,0,ABOUTWIDTH,ABOUTHEIGHT);
		aboutScroller.setMaximumSize(new Dimension(ABOUTWIDTH,ABOUTHEIGHT));
		aboutScroller.setMinimumSize(new Dimension(ABOUTWIDTH,ABOUTHEIGHT));
		aboutScroller.setAlignmentX(0.5f);
		panel.add(aboutScroller);
		
		// add the image
		int wide=-1;
		int high=-1;
		if (CPBase.scriptManager.myScriptTag!=null) {
			wide=CPBase.scriptManager.myScriptTag.getImage().getWidth(null);
			high=CPBase.scriptManager.myScriptTag.getImage().getHeight(null);
			wide = (wide>ABOUTWIDTH) ? ABOUTWIDTH:wide;
			high = (wide>ABOUTHEIGHT) ? ABOUTHEIGHT:high;
		}
		JButton button;
		if (wide<=0 || high<=0) // no image? just display the tag file name
			button=new JButton(CPBase.scriptManager.scriptTagname);
		else {
			ImageIcon iI=new ImageIcon(util.GetScaleImage.scaleBufferedImage((BufferedImage)CPBase.scriptManager.myScriptTag.getImage(),
					AboutFrame.ABOUTWIDTH,AboutFrame.ABOUTHEIGHT));
			button=new JButton(iI);
//			button=new JButton(CPBase.scriptManager.myScriptTag);
//			button.setPreferredSize(new Dimension(wide,high));
//			button.setMaximumSize(new Dimension(wide,high));
			button.setPreferredSize(new Dimension(ABOUTWIDTH,ABOUTHEIGHT));
			button.setMaximumSize(new Dimension(ABOUTWIDTH,ABOUTHEIGHT));
//			button.setMinimumSize(new Dimension(ABOUTWIDTH,ABOUTHEIGHT));
		}
		button.setAlignmentX(0.5f);
		panel.add(button);

		// finish frame setup
		pane.add(panel);
		setVisible(true);
		setState(Frame.NORMAL); // in case it's iconified
		pack();
//		setLocation(X,Y);
		setLocation(50,50);
	}

}
