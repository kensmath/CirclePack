package util;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.codec.binary.Base64;

import exceptions.InOutException;

/**
 * Binary files (currently, just image files) need to be 
 * stored in base64 encoded form in scripts. This class has 
 * routines for en/decoding files.
 * Code mostly stolen from web.
 * @author kstephe2
 */
public class Base64InOut {
	
	/**
	 * encode a file (presumably binary, e.g. 'jpg' or 'png') 
	 * in base64 for inclusion in script. File name is unchanged. 
	 * @param file (File created/checked by calling routine)
	 */
	public static void fileInto64(File file) {
		byte[] by=getBytesFromFile(file);
		byte[] outbytes=Base64.encodeBase64(by);
		String filename=null;
		try {
			filename=file.getCanonicalPath();
			FileOutputStream fos = new FileOutputStream(file);
			BufferedOutputStream bos = new BufferedOutputStream(fos);
			bos.write(outbytes);
			bos.flush();
			fos.flush();
			fos.close();
		} catch (Exception ex) {
			throw new InOutException("error encoding image file "+filename);
		}
	}
	
	/**
	 * decodes a file from base64 (presumably to binary, 
	 * e.g. 'jpg' or 'png'). File name is unchanged.  
	 * @param file (File created/checked by calling routine)
	 */
	public static File fileOutof64(File file) {
		byte[] by=getBytesFromFile(file);
		byte[] outbytes=Base64.decodeBase64(by);
		String filename=null;
		try {
			filename=file.getCanonicalPath();
			File outFile = new File(filename); //System.getProperty("java.io.tmpdir"),newName);
			outFile.createNewFile();
			outFile.deleteOnExit();
			FileOutputStream fos = new FileOutputStream(outFile);
			BufferedOutputStream bos = new BufferedOutputStream(fos);
			bos.write(outbytes);
			bos.flush();
			fos.flush();
			fos.close();
			return outFile;
		} catch (Exception ex) {
			throw new InOutException("error encoding image file "+filename);
		}
	} 
	
	/** Returns the contents of the file in a byte array.
	 * (from 'Example Depot' on the web)
	 * @param file
	 * @return
	 * @throws IOException
	 */
	public static byte[] getBytesFromFile(File file) {
		byte[] bytes=null;
		InputStream is=null;
		try {
			is = new FileInputStream(file);

			long length = file.length();
			if (length > Integer.MAX_VALUE) {
				is.close();
				throw new InOutException("File was too long. "+file.getName());
			}
			bytes = new byte[(int)length];

			// Read in the bytes
			int offset = 0;
			int numRead = 0;
			while (offset < bytes.length
	           && (numRead=is.read(bytes, offset, bytes.length-offset)) >= 0) {
				offset += numRead;
			}

			// Ensure all the bytes have been read in
			if (offset < bytes.length) {
				is.close();
				throw new InOutException("Could not completely read file "+file.getName());
			}

			// Close the input stream and return bytes
			is.close();
		} catch(Exception ex) {
			throw new InOutException("failed to open input stream");
		}
		
	    return bytes;
	}

}
