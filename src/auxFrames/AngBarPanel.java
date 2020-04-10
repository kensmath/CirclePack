package auxFrames;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Line2D;

import javax.swing.JPanel;


public class AngBarPanel extends JPanel {

	private static final long 
	serialVersionUID = 1L;
	
	int height=200;
	int width=200;

	// Constructor
	public AngBarPanel(int wdh) {
		height=SphWidget.ANG_BAR_HEIGHT;
		width=wdh;
	}
	
	public void paintComponent(Graphics g) {
//		super.paintComponent(g);
		Graphics2D g2=(Graphics2D)g;
		
		// horizontal lines
		double high=((double)height)/5.0;
		for (int j=0;j<=2;j++) {
			g2.draw(new Line2D.Double((double)SphWidget.TEXT_PADDING-10,
					(double)(SphWidget.BAR_DROP+high*(2*j+1)),
					(double)(width)-4,(double)(SphWidget.BAR_DROP+high*(2*j+1))));
		}
		g2.drawString("4Pi",6,SphWidget.BAR_DROP+(int)high);
		g2.drawString("2Pi",6,SphWidget.BAR_DROP+3*(int)high);
		g2.drawString("0",6,SphWidget.BAR_DROP+5*(int)high);

	}
}
