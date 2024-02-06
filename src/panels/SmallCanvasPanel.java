package panels;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;

import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

import allMains.CPBase;
import circlePack.PackControl;
import exceptions.MiscException;
import packing.CPdrawing;

/**
 * Panel containing the three (or more??) small packing images
 * and associated packing info
 */
public class SmallCanvasPanel extends JPanel {

	private static final long 
	serialVersionUID = 1L;

	static Dimension cpSDim=new Dimension(PackControl.smallSide,PackControl.smallSide);
	static Dimension infoDim=new Dimension(PackControl.smallSide,25);
	static Dimension smallDim=new Dimension(PackControl.smallSide+2,PackControl.smallSide+25);
	
	static Color actColor=new Color(150,250,150); // sickly green for active pack
	static Color nonColor=new Color(200,200,200); // light grey for nonactive

	public JPanel []smallPanel;
	public JPanel []cpInfo;
	public JLabel []packName;
	public CPdrawing []ourScreens;

	// Constructor
	public SmallCanvasPanel(CPdrawing []screens) {
		super();
		ourScreens=screens;
		smallPanel=new JPanel[CPBase.NUM_PACKS];
		cpInfo=new JPanel[CPBase.NUM_PACKS];
		packName=new JLabel[CPBase.NUM_PACKS];
		
		// create individual panels
		try {
			for (int i=0;i<CPBase.NUM_PACKS;i++) {
				createSmall(i);
			}
		} catch (Exception ex) {
			throw new MiscException("Failed to create small canvases");
		}
		cpInfo[0].setBackground(actColor);
		cpInfo[0].setBorder(new LineBorder(Color.black,2,false));
		
		initGUI();
	}
	
	/**
	 * Each packing has a canvas surmounted by a panel for name and icons,
	 * also to be colored to indicate active.
	 */
	private void createSmall(int i) {
		smallPanel[i]=new JPanel(new BorderLayout());
		cpInfo[i]=new JPanel(new FlowLayout(FlowLayout.LEFT));
		
		smallPanel[i].add(cpInfo[i],BorderLayout.NORTH);
		smallPanel[i].add(ourScreens[i],BorderLayout.CENTER);
		smallPanel[i].setBorder(new EmptyBorder(2,5,2,5));
		
		smallPanel[i].setPreferredSize(smallDim);
		smallPanel[i].setMaximumSize(smallDim);
		smallPanel[i].setMinimumSize(smallDim);
		smallPanel[i].setPreferredSize(smallDim);
		
		cpInfo[i].setPreferredSize(infoDim);
		cpInfo[i].setMaximumSize(infoDim);
		cpInfo[i].setMinimumSize(infoDim);
		cpInfo[i].setPreferredSize(infoDim);
		cpInfo[i].setBorder(new LineBorder(Color.BLACK,1,false));
		
		ourScreens[i].setPreferredSize(cpSDim);
		ourScreens[i].setMaximumSize(cpSDim);
		ourScreens[i].setMinimumSize(cpSDim);
		ourScreens[i].setPreferredSize(cpSDim);
		
		packName[i]=new JLabel();
		packName[i].setFont(new Font(packName[i].getFont().toString(),Font.ITALIC,9));
		packName[i].setText("P"+i+" empty");
		
		cpInfo[i].add(packName[i]);
	}
	
	private void initGUI() {
		try {
			this.setLayout(new BoxLayout(this,BoxLayout.LINE_AXIS)); //FlowLayout(FlowLayout.LEFT));
			for (int i=0;i<CPBase.NUM_PACKS;i++) { 
				this.add(smallPanel[i]);
			}
			this.setBorder(new LineBorder(Color.black,2,false));
			this.validate();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/** 
	 * Change active packing indicators, pack 'n' active.
	 */
	public void changeActive(int n) {
		if (n<0 || n>=CPBase.NUM_PACKS) 
			return;
		for (int i=0;i<CPBase.NUM_PACKS;i++) {
			if (i==n) {
				cpInfo[i].setBackground(actColor);
				cpInfo[i].setBorder(new LineBorder(Color.black,1,false));
			}
			else {
				cpInfo[i].setBackground(nonColor);
				cpInfo[i].setBorder(new LineBorder(Color.black,1,false));
			}
		}
	}
	
}
