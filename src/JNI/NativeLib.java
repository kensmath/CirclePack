package JNI;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * This code is needed for cross-platform access to the C/C++ libraries. 
 * Various versions of the libraries are compiled.
 * At runtime the libraries appropriate to the operating system and
 * computer are stored in temporary directories and the system is told
 * where to find them.
 * 
 * @author Chris Brumgard
 *
 */
public class NativeLib
{

	public static String writeLibrary(String path, String libname) 
			throws IOException
	{
		/* Gets the name of the platform dependent library */
		libname = System.mapLibraryName(libname);
		

        String jarURL = null;

        /* Gets the URL for the file in the jar */
        if(path.length() > 0)
        {
           jarURL = path+"/"+libname;
        }else
        {
           jarURL = libname;
        }
        
        System.out.printf("%s:%s\n", System.getProperty("os.name"), 
        		System.getProperty("os.arch"));
        
        /* For Linux, either the 32 or 64 bit library gets selected */
        if(System.getProperty("os.name").equals("Linux"))
        {
        	String arch = System.getProperty("os.arch");
        	
        	System.out.println("arch = " + arch);
        	
        	if(arch.equals("amd64") || arch.equals("x86_64"))
        	{
        		jarURL = jarURL.replaceFirst(".so$", "-x86_64.so");
        		
        	}else if(arch.equals("i386"))
        	{
        		jarURL = jarURL.replaceFirst(".so$", "-i586.so");
        	}
        }

        
        System.out.printf("Jar url = %s\n", jarURL);
        
        /* For Mac OS X, check if there is a 32 or 64 bit variant */
        if(System.getProperty("os.name").equals("Mac OS X"))
        {
        	String arch = System.getProperty("os.arch");
        	
        	System.out.println("arch = " + arch);
        }
        
		System.out.printf("Jar url = %s\n", jarURL);

		/* Gets an input stream to the library in the jar */
		InputStream is = NativeLib.class.getClassLoader().getResourceAsStream(jarURL);
		
		System.out.printf("Inputstream is = %s\n", is);
		
		/* Creates a file in the current working directory of the java process
		 * and sets the file to being deleted on exit. */
		File libFile = new File(new File (".").getCanonicalPath() + "/" + libname);
		libFile.createNewFile();
		libFile.deleteOnExit();
		
		/* Creates an output stream to the temporary library file */
		FileOutputStream os = new FileOutputStream(libFile);

		/* Creates a buffer for copying data */
		byte[] buf = new byte[4192];
	 	
		/* Copies the library file in the jar to the temporary library file */
	    for(int amtRead = is.read(buf); amtRead > 0; amtRead = is.read(buf))
	    {
	    	os.write(buf, 0, amtRead);
	    }
	    
	    /* Closes the streams */
	    os.flush();
	    os.close();
	    is.close();
	    
	    System.out.printf("Path to file %s\n", libFile.getPath());
	    
	    /* Returns the path to the library on the system */
	    return libFile.getPath();
	}
	
	public static String writeLibrary(String libname) throws IOException
    {
        return writeLibrary("", libname);
    }
	
	public static void loadLibrary(String path, String libname) throws IOException
	{
	    /* Writes the library from the jar file to the system and then gets
	     * the path to the library file so it can be loaded */
	    String pathToLibrary = writeLibrary(path, libname);
	    
	    try
	    {
	    	System.out.printf("Loading %s\n", pathToLibrary);
	    	
	    	System.out.printf("library path = %s\n", System.getProperty("java.library.path"));
        	
	    	System.out.printf("new path = %s\n", new File(pathToLibrary).getAbsoluteFile().getParent());
	    	
	    	System.setProperty("java.library.path", String.format("%s:%s", new File(pathToLibrary).getAbsoluteFile().getParent(), System.getProperty("java.library.path")));
	    	
	    	/* Loads the library */
	    	System.load(pathToLibrary);
	    	
	    } catch(Error e) {
	    	System.out.println(e);
	    	throw e;
	    }
	}

	public static void loadLibrary(String libname) throws IOException
	{
	    /* Writes the library from the jar file to the system and then gets
	     * the path to the library file so it can be loaded */
		String pathToLibrary = writeLibrary(libname);
	    
	    try {
	    	/* Loads the library */
	    	System.load(pathToLibrary);
	    	
	    } catch(Error e) {
	    	System.out.println(e);
	    	throw e;
	    }
	    
	}
}
