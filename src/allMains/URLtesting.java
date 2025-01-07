package allMains;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;

import browser.BrowserUtilities;
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
		
		URL url1=null;
		URL url2=null;
		URL url3=null;
		URL url4=null;
		try {
			url1=new URL("file:///C:/users/kensm/");
			url2=new URL("file:/C:/users/kensm/");
			url3=new URL("file:///C:/users/kensm");
			url4=new URL("file:/C:/users/kensm");
		} catch (MalformedURLException mu) {
			System.out.println("Error: "+url1+"\n"+url2+"\n"+url3+"\n"+url4);
		}
		System.out.println(url1+"\n"+url2+"\n"+url3+"\n"+url4);

		url1.equals(url2);
		url1.equals(url3);
		File file1=new File(url1.getFile()+"/");
		File file3=new File(url3.getFile());
		file1.isDirectory();
		file3.isDirectory();
//		url3.equals(url4);
//		url2.equals(url3);
		
		
		testInput="file:///C:/users/kensm/Documents/smalltest";
		testURL=FileUtil.parseURL(testInput);
		System.out.println("testInput = "+testInput+"\nAfter call, result is = "+
				testURL.toString());
		
		URL outURL=BrowserUtilities.pageForDirectory(testURL);
		System.out.println("tmp directory html page is:\n"+outURL.toString());

		testInput="http://circlepack.com";
		System.out.println("testInput = "+testInput+"\nAfter call, result is = "+
				FileUtil.parseURL(testInput));

		testInput="C:/users/kensm/Documents";
		
		System.out.println("testInput = "+testInput+"\nAfter call, result is = "+
				FileUtil.parseURL(testInput));

		URL testURL=FileUtil.parseURL(testInput);
// System.getProperty("user.home").toLowerCase().replace("\\","/");
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
