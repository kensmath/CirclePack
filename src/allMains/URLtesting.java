package allMains;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;

import util.FileUtil;

/** 
 * Testing to figure out how to manage files, directories,
 * and URLs. For example, it appears that for local 
 * files/directories the URL should be 'file:///C:<filename>'
 * or 'file:/C:<filename>'.
 */
public class URLtesting {
	URL testURL;
	File testFile;
	String testInput;
	String testOutput;

	public URLtesting() {	
		
		testInput="file://C:/users/kensm/Documents/testscripts";

//		testInput="http://localhost:/Users/kensm/Documents/testscripts";

		URL testURL=FileUtil.parseURL(testInput);

		int k=0;
		while((k=testInput.indexOf(":"))>0 && k<(testInput.length()-1))
			testInput=testInput.substring(k+1);
		
		testFile=new File(testInput);
		
		if (testFile.isDirectory()&& testInput.substring(testInput.length()-1)!="/")
			testInput=testInput+"/";


		FileUtil.isContactable(testURL);

		printURLInfo(testURL);
		testFile=new File(testURL.getFile());
		
		printFileInfo(testFile);
		
		printFileInfo(new File(testURL.getFile()));
		
		FileUtil.AddressReadable("web.math.utk.edu/");
		FileUtil.AddressReadable(testURL.getFile());
		
//		testInput
//		testFile.getPath();
//		testFile.toURI();
//		testFile.toURI().toURL();
		
		try {
			System.out.println("'testFile' canonicalpath: "+testFile.getCanonicalPath());
			System.out.println("'testFile' protocol: "+testFile.toString());
		} catch (IOException e) {
			System.err.println("'testFile' canonical path problem.");
		}
//		System.out.println("'testFile' path: "+testFile.getPath());
//		File secFile=null;
//		try {
//			secFile = testFile.getCanonicalFile();
//		} catch (IOException e) {
//			System.err.println("'secFile' problems.");
//		}
		
//		System.out.println("'testFile' path: "+secFile.getPath());
//		testFile.getPath();
//		testFile.getCanonicalPath();

		
		System.err.println("URL fail with :"+testInput);
		System.out.println("'testURL' protocol: "+testURL.getProtocol());
		System.out.println("'testURL' host: "+testURL.getHost());
		System.out.println("'testURL' file: "+testURL.getFile());
		System.out.println("'testURL' path: "+testURL.getPath());

		if (Files.isReadable(Paths.get(testURL.getPath())))
				System.out.println(testURL.getPath() + " is readable");

//		testURL.getPath();
//		testURL.getFile();

	}
	
	public void printURLInfo(URL url) {
		if (url==null) {
			System.err.println("url is null");
			return;
		}
		System.out.println("url protocol: "+url.getProtocol());
		System.out.println("url host: "+url.getHost());
		System.out.println("url path: "+url.getPath());
		System.out.println("url file: "+url.getFile());
		
	}
	
	public void printFileInfo(File file) {
		if (file==null) {
			System.err.println("file is null");
			return;
		}
		System.out.println("file name: "+file.getName());
		System.out.println("file is directory?: "+file.isDirectory());
		System.out.println("file time: "+file.lastModified());
		System.out.println("file length: "+file.length());
		System.out.println("file path: "+file.getPath());
		System.out.println("file parent: "+file.getParent());
	}
	
	
	public static void main(String[] args) {
		@SuppressWarnings("unused")
		URLtesting obj=new URLtesting();
	}




}
