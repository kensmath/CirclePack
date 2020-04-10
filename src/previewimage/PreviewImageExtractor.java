package previewimage;

import java.awt.Image;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;

import javax.imageio.ImageIO;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.commons.codec.binary.Base64;
import org.xml.sax.SAXException;


/**
 * This class can extract an image from a valid and compatible XML resource.
 * Valid but incompatible XML resources may be passed without error. In this
 * implementation, compatibility means that the XML resource contains an
 * element whose content is a preview image encoded as a valid base 64 string
 * and whose tag is as defined in {@link PreviewImageSearchHandler}. Instances
 * should be initialized with the target URL, after which the image may be
 * requested.
 * 
 * @author Alex Fawkes
 *
 */
public class PreviewImageExtractor {
	// How long to allow unsuccessful connection or read attempts to continue
	// before giving up, in milliseconds.
	protected static final int TIMEOUT = 3000;
	protected URL xmlResource; // The target XML resource.

	/**
	 * Create a new PreviewImageExtractor for a specified XML resource. The
	 * caller is responsible for verifying that both the URL and the XML
	 * resource it specifies are valid. The XML resource is permitted to not
	 * contain a preview image.
	 * 
	 * @param xmlResource the target XML resource
	 */
	public PreviewImageExtractor(URL xmlResource) {this.xmlResource = xmlResource;}

	/**
	 * Retrieve the preview image from this instance's specified XML resource.
	 * This call may take a long time, especially for large or remote files.
	 * 
	 * @return the image found in the XML resource; <code>null</code> on failure
	 * @throws IOException if an I/O exception occurs
	 */
	public Image getImage() throws IOException {
		/*
		 * Due to difficulties with the network coding involved with extracting preview
		 * images from remote resources, specifically in regards to connection failure
		 * and time delays, we'll only support local file resources for now. If the URL
		 * does not point to a local resource, return a null image.
		 * 
		 * TODO: Add support for remote network resources. This will likely require
		 * custom code to connect to the remote host and iteratively download partial
		 * chunks of the resource, as well as a custom XMD parser to find the preview
		 * image tag. Local caching of remote resources would help tremendously.
		 * Consider checking the modification date of remote XMD scripts (most servers
		 * will provide this on request) to determine if a resource needs to be
		 * redownloaded and recached.
		 */
		if (!xmlResource.getProtocol().equals("file")) return null;
		
		// Get a SAX parser to interpret our XML resource.
		SAXParser sp;
		try {
			sp = SAXParserFactory.newInstance().newSAXParser();
		} catch (ParserConfigurationException e) {
			// I'm not certain, but this exception should never occur.
			// Print a stack trace for debugging, and return a null image.
			e.printStackTrace();
			return null;
		} catch (SAXException e) {
			// Same as above.
			e.printStackTrace();
			return null;
		}

		/*
		 *  Get a stream to the XML resource.
		 *  
		 *  Set connection and read timeouts on the stream. By default no
		 *  timeouts are set, allowing network sockets that have silently
		 *  failed to block access to the resource indefinitely. This
		 *  potentially floods the thread pool and causes deadlock.
		 */
		final InputStream is;
		try {
			URLConnection connection = xmlResource.openConnection();
			connection.setConnectTimeout(TIMEOUT);
			connection.setReadTimeout(TIMEOUT);
			is = connection.getInputStream();
		} catch (SocketTimeoutException e) {
			// Connection timed out. Return a null image.
			return null;
		}

		/*
		 * Parse the XML resource to search for the preview image. If a preview image is
		 * found, catch the PreviewImageFoundException and get the preview image from it.
		 */
		String base64Image = null;
		try {
			// Start parsing.
			sp.parse(is, new PreviewImageSearchHandler());
		} catch (PreviewImageFoundException e) {
			// The base 64 string encoded image has been found. Grab it.
			base64Image = e.getBase64Image();
		} catch (SAXException e) {
			/*
			 * This is likely caused by an invalid XML resource. This could mean anything
			 * from minor XML formatting errors to the resource not being XML at all.
			 * This shouldn't happen given that the caller is responsible for the validity
			 * of the XML resource, so print the stack trace for debugging and return a
			 * null image.
			 */
			e.printStackTrace();
			return null;
		}  catch (SocketTimeoutException e) {
			// Connection timed out. Return a null image.
			return null;
		} finally {
			// Whatever happens, close the stream. This shouldn't fail. If it does, print
			// a stack trace for debugging and continue.
			try {is.close();}
			catch (IOException e) {e.printStackTrace();}
		}

		// If we haven't found an image, return null. Otherwise, decode and construct the
		// image and return the result.
		if (base64Image == null) return null;
		// TODO: used to be 'decodeBase64' instead of 'decode', but it seems to be
		//   absent; maybe I have the wrong "codec"??
		else return ImageIO.read(new ByteArrayInputStream(Base64.decodeBase64(base64Image)));
	}
}