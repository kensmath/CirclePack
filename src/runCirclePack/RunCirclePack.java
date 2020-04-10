
package runCirclePack;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Map;

/**
 * Starting CirclePack as a 'jar' file is complicated. Two main 
 * issues: (1) need to store C libraries appropriate to the system,
 * (2) want splash screen (have to circumvent error in standard
 * splashscreen functionality).
 * 
 * So, when running the final 'CirclePack*.jar' file, we first run
 * the 'main' method here. It creates the necessary temp directory
 * and loads the system-appropriate libraries, writes "cpcore.jar" 
 * to the temp directory, then launches it via a 'java -jar *' call.
 * 'cpcore.jar' then starts with the 'SplashMain.main'.
 * 
 * @author Chris Brumgard (Sellers)
  */
class RunCirclePack
{
	
	protected static String createTemporaryDirectory() throws IOException
	{
		/* Creates a temporary file */
		File tempFile = File.createTempFile("temp", Long.toString(System.nanoTime()));

		/* Deletes the temporary file */
		if(!tempFile.delete())
		{
			throw new IOException("Could not delete temp file: " + 
					tempFile.getAbsolutePath());
		}

		/* Makes a directory based on the name instead */
		if(!tempFile.mkdir())
		{
			throw new IOException("Could not create temp directory: " + 
					tempFile.getAbsolutePath());
		}

		/* Returns the path to the temporary directory */
		return tempFile.getAbsolutePath();
	}

	protected static void writeJarFile(String jarName, String dest) 
		throws IOException
	{
		/* Gets an input stream to the library in the jar */
		InputStream is = RunCirclePack.class.getClassLoader().getResourceAsStream(jarName);
		
		
		/* Creates a file in the current working directory of the java process
		 * and sets the file to being deleted on exit. */
		File jarFile = new File(dest + "/" + jarName);
		jarFile.createNewFile();
		jarFile.deleteOnExit();
		
		/* Creates an output stream to the temporary library file */
		FileOutputStream os = new FileOutputStream(jarFile);

		/* Creates a buffer for copying data */
		byte[] buf = new byte[1048576];
	 	
		/* Copies the library file in the jar to the temporary library file */
	    for(int amtRead = is.read(buf); amtRead > 0; amtRead = is.read(buf))
	    {
	    	os.write(buf, 0, amtRead);
	    }
	    
	    /* Closes the streams */
	    os.close();
	    is.close();
	}
	
	protected static void deleteTemporaryDirectory(String tempDir)
	{
		/* Gets a File object for working with the tempDir */
		File dir = new File(tempDir);
		
		/* Gets a list of all the child files and deletes them */
		File[] files = dir.listFiles();
		
		for(int i=0; i<files.length; i++)
		{
			files[i].delete();
		}
		
		/* Deletes the temporary directory */
		dir.delete();
	}
	
	public static void launchCirclePark(String tempDir, String jarFile, 
			String[] args) 
		throws IOException, InterruptedException
	{
		
		LinkedList<String> processArgs = new LinkedList<String>();
		
		processArgs.add("java");
		processArgs.add("-jar");
		processArgs.add(jarFile);
		
		for (String arg : args) 
		{
			processArgs.add(arg);
		}
		
		ProcessBuilder pb = new ProcessBuilder(processArgs);
		
		Map<String, String> env = pb.environment();
		
		env.put("LD_LIBRARY_PATH", ".:"+env.get("LD_LIBRARY_PATH"));
		
		pb.directory(new File(tempDir));
	
        System.out.println("Starting process");    
		Process p = pb.start();
	
		System.out.println(pb.environment().get("LD_LIBRARY_PATH"));
		
		//AF>>>//
		/* Rewrote the code below to fix the freezing issue. When we start a process,
		 * we're responsible for its standard streams (output, error, and input).
		 * Depending on the specific system, these streams have limited buffer sizes.
		 * In the original code, standard error isn't handled. The buffer would
		 * eventually fill, and the next time CirclePack attempted to write to
		 * standard error, the call would hang until whoever is on the other end
		 * of the stream got around to handling it. This is us, and we never did
		 * so. Killing the launcher probably handed control of the streams over to
		 * the JVM or OS, which promptly flushed the buffers and kept them clear,
		 * allowing CirclePack to continue.
		 * 
		 * In the new code, I spin two threads which forward the streams appropriately.
		 * Threading is necessary to avoid certain deadlock conditions, like standard
		 * output filling up while we're waiting to read from standard error.
		 * 
		 * Java 1.7 has methods to handle this redirection built-in, so we could do all
		 * of this in two lines, but 1.7 is cutting-edge and the feature is relatively
		 * minor so I just did it "by hand" to avoid compatibility issues for users
		 * with older JVMs.
		/*
        System.out.println("Waiting on process");    
		//p.waitFor();
    
        InputStream os = p.getInputStream();

        byte[] buf = new byte[4096];
        for(int amt=os.read(buf); amt>0; amt=os.read(buf))
        {
             System.out.write(buf, 0, amt);
        }
        os.close();
        */
		
		System.out.println("RunCirclePack: Spinning standard output and error forwarding threads.");
		
		// Get CirclePack's standard output and error as something we can read from.
		final BufferedReader circlePackOut = new BufferedReader(new InputStreamReader(p.getInputStream()));
		final BufferedReader circlePackErr = new BufferedReader(new InputStreamReader(p.getErrorStream()));
		
		// Create threads that forward CirclePack's standard output and error to this
		// launcher's standard output and error.
		Thread outputForwarder = new Thread() {
			public void run() {
				String line;
				try {
					while ((line = circlePackOut.readLine()) != null) System.out.println(line);
					circlePackOut.close();
				} catch (IOException e) {
					System.err.println("RunCirclePack: Error forwarding standard output. Standard output will no longer be forwarded.");
				}
			}
		};
		Thread errorForwarder = new Thread() {
			public void run() {
				String line;
				try {
					while ((line = circlePackErr.readLine()) != null) System.err.println(line);
					circlePackErr.close();
				} catch (IOException e) {
					System.err.println("RunCirclePack: Error forwarding standard error. Standard error will no longer be forwarded.");
				}
			}
		};
		
		// Start the threads, then wait for them to complete. They will complete when CirclePack closes
		// its standard output and error, such as on program termination. They will also complete on
		// IO failure, which must be handled.
		outputForwarder.start();
		errorForwarder.start();
		outputForwarder.join();
		errorForwarder.join();
		
		// The threads may have finished due to IO failure rather than program termination. Just in case,
		// we'll wait on the CirclePack process to complete.
		p.waitFor();
        //<<<AF//
	}
	
	public static void main(String[] args) 
		throws IOException, InterruptedException
	{
		// locale-sensitive classes (e.g., numerical output) in US format
		Locale.setDefault(new Locale("en","US"));
		
		final String jarFilename = "cpcore.jar";
	
        System.out.println("Creating Temporary Directory");    
		String tempDir = createTemporaryDirectory();
	
        System.out.println("Writing jar file to "+tempDir);    
		writeJarFile(jarFilename, tempDir);
	
        System.out.println("Launching CirclePack");    
		launchCirclePark(tempDir, jarFilename, args);
	
        System.out.println("Deleting temporary files");    
		deleteTemporaryDirectory(tempDir);
	}
}
