package circlePack;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.SplashScreen;

/** creates a splash screen to occupy the user while CirclePack
 * initiates.
 * @author kens
 *
 */
public class CPSplash implements Runnable {

//	public static boolean keepon;
	
	Graphics2D g;
	final SplashScreen splash = SplashScreen.getSplashScreen();
	static void renderSplashFrame(Graphics2D g,int frame) {
		g.setComposite(AlphaComposite.Clear);
		g.fillRect(10,325,300,40);
		g.setPaintMode();
		g.setColor(Color.BLACK);
		g.setFont(new Font("Serif",Font.BOLD,15));
		g.drawString("CirclePack, Version J0.1, 2008",10,320);
		g.drawString("Loading:",10,340);
		g.fillRect(10,355,(frame*10)%290,10);
	}
	
	public CPSplash() {
		if (splash == null) {
			System.out.println("SplashScreen.getSplashScreen() returned null");
			return;
		}
		g = (Graphics2D)splash.createGraphics();
		if (g == null) {
			System.out.println("g is null");
			return;
		}
	}
	
	public void run() {
		for(int i=0; i<100; i++) {
			renderSplashFrame(g, i);
			splash.update();
			try {
				Thread.sleep(500);
			}
			catch(InterruptedException e) {
			}
		}
		splash.close();
	}
}
