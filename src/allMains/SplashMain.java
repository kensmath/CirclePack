package allMains;


import java.awt.EventQueue;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Locale;

import javax.imageio.ImageIO;

import frames.SplashFrame;

/**
 * This class provides an alternative main() function which will pop up a
 * splash screen, load the real main class in the background, then remove 
 * the splash screen and run the real main() function.
 * 
 * @author Bradford Smith
 */
public class SplashMain {
	private static final String splashImageFilename = "/Resources/Icons/GUI/CPSplash.jpg";
	private SplashFrame splashScreen;
		
	/**
	 * Put up the splash screen.
	 * @throws InvocationTargetException 
	 * @throws InterruptedException 
	 * @throws IOException 
	 */
	private void showSplashScreen()
	throws InterruptedException, InvocationTargetException, IOException, java.net.URISyntaxException
	{
		// read in the image
                
		boolean inJar=SplashMain.class.getProtectionDomain().getCodeSource()
						.getLocation().toURI().getPath().endsWith(".jar");

		BufferedImage image = null;
		
		if(inJar)
		{
			System.out.println("HERE");
			image = ImageIO.read(
					getClass().getResourceAsStream(splashImageFilename)
				);

		}else
		{
			String imageFilename=new String("bin"+splashImageFilename);
			try {
				image =  ImageIO.read(new File(imageFilename));
			} catch (Exception iio) {  // javax.imageio.IIOException
				System.err.println("Splash screen failed, hopefully, program continues");
			}
		}
		
		final BufferedImage fimage = image;
		
		EventQueue.invokeAndWait(new Runnable(){
			public void run(){
				splashScreen = new SplashFrame(fimage);
				splashScreen.setVisible(true);
			}
		});
	}
	
	private void destroySplashScreen(){
		EventQueue.invokeLater(new Runnable(){
			public void run(){
				splashScreen.dispose();
				splashScreen = null;
			}
		});
	}
	
	/**
	 * main routine to be invoked with command line arguments (if any)
	 * 
	 * @param args, the command line arguments
	 */
	public static void main(String[] args) {
		
		// want locale-sensitive classes (e.g., numerical output) to be 
		//    in us format
		Locale.setDefault(new Locale("en","US"));
		
		// get an object of this class to manage the splash screen.
		SplashMain obj = new SplashMain();
		try {
			// put up the splashscreen
			obj.showSplashScreen();
			// now load the real main class.
			Class<?> cl = Class.forName("allMains.CP_after_Splash");
			// take down the splash screen
			obj.destroySplashScreen();
			// execute the real main routine
			Method realMain = cl.getMethod("main", args.getClass());
			realMain.invoke(cl, (Object)args);
		} catch (Exception e) {
			// just print a stack trace and exit on exception
			e.printStackTrace();
		}
	}
}
