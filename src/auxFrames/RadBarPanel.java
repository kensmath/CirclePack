package auxFrames;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Line2D;

import javax.swing.JPanel;


public class RadBarPanel extends JPanel {
	
	private static final long 
	serialVersionUID = 1L;
	
	int height=200;
	int width=200;
	double rad_bar_power=9.0;

	// Constructor
	public RadBarPanel(int wdh,double rbp) {
		height=SphWidget.RAD_BAR_HEIGHT;
		width=wdh;
		rad_bar_power=rbp;
	}
	
	public void paintComponent(Graphics g) {
//		super.paintComponent(g);
		Graphics2D g2 = (Graphics2D) g;

		// horizontal lines
		double val = Math.PI;
		int denom = 1, maxpow = (int) Math.pow(2.0, rad_bar_power);
		while (denom <= maxpow) {
			double temp = (Math.log(1.0 / (val / Math.PI)) / Math.log(2))
					/ rad_bar_power;
			int y = (int)(Math.round(temp * (double) height));
			g2.draw(new Line2D.Double(SphWidget.TEXT_PADDING-6, 
					SphWidget.BAR_DROP + y, width-4,
					SphWidget.BAR_DROP + y));

			if (denom != 1)
				g2.drawString("Pi/" + denom, 6,SphWidget.BAR_DROP + y+3);
			else
				g2.drawString("Pi", 6,SphWidget.BAR_DROP + y+3);

			val /= 2.0;
			denom *= 2;
		}
	}
}
