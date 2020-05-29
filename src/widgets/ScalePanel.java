package widgets;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Line2D;
import java.util.ArrayList;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

/**
 * General purpose panel with scale markings and panel for 'ActiveBar's.
 * Put equally spaced markings
 * @author kstephe2 5/2020
 *
 */

public class ScalePanel extends JPanel {
	
	private static final long 
	serialVersionUID = 1L;
	
	public JPanel contentPanel; // holds the 'ActiveBar's
	JScrollPane barScroll; // scroll for larger counts
	int scheight;
	int scwidth;
	double val_min;
	double val_max;
	
	// markings for horizontal lines: start at bottom, last is top line
	ArrayList<String> scale_ticks; 

	// Constructor
	public ScalePanel(int wde,int hgh,double v_min,double v_max ,ArrayList<String> mks) {
		scheight=hgh;
		scwidth=wde;
		setSize(new Dimension(scwidth,scheight));
		setPreferredSize(new Dimension(scwidth,scheight));
		val_min=v_min;
		val_max=v_max;
		scale_ticks=mks;
		
		contentPanel=new JPanel();
		contentPanel.setLayout(new FlowLayout());
//		contentPanel.setSize(new Dimension(scwidth,scheight));
		contentPanel.setPreferredSize(new Dimension(scwidth,scheight));
		contentPanel.setBounds(BarGraphFrame.SCALE_PADDING,
				scheight,scwidth-BarGraphFrame.SCALE_PADDING,scheight);
		contentPanel.setBorder(BorderFactory.createLineBorder(java.awt.Color.GREEN));
//		contentPanel.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.RAISED));
//		contentPanel.setBorder(BorderFactory.createTitledBorder("say what"));
		contentPanel.setOpaque(false);
		add(contentPanel);
		
//		barScroll=new JScrollPane(contentPanel);
//		barScroll.setBounds(1,51,scwidth+20,scheight+5);
//		barScroll.setSize(new Dimension(scwidth+20,scheight+5));
//		barScroll.setPreferredSize(new Dimension(scwidth+20,scheight+5));
//		barScroll.setOpaque(false);
//		add(barScroll);
		
		

	}
	
	public void paintComponent(Graphics g) {
		Graphics2D g2 = (Graphics2D) g;
		int n=scale_ticks.size();
		double inc=scheight;
		if (n>2)
			inc=(double)scheight/(double)(n-1);

		// horizontal lines. labels
		for (int j=n-1;j>=0;j--) {
			int y=(int)(Math.round(inc*(n-1-j)));
			g2.draw(new Line2D.Double(BarGraphFrame.SCALE_PADDING-6, 
					BarGraphFrame.BAR_DROP + y, scwidth-4,
					BarGraphFrame.BAR_DROP + y));
			g2.drawString((String)scale_ticks.get(j), 6,BarGraphFrame.BAR_DROP + y+3);
		}
	}
}
