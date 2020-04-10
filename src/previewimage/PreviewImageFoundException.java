package previewimage;

import org.xml.sax.SAXException;

/**
 * Signals that a preview image has been successfully extracted from an
 * XML document by the SAX parser. This exception stops the SAX parser
 * from continuing with unnecessary parsing once the preview image has
 * been found and carries the new preview image reference up to the
 * controller of the SAX parser.
 * 
 * @author Alex Fawkes
 *
 */
class PreviewImageFoundException extends SAXException {
	private static final long serialVersionUID = -7924539313131765089L;
	protected String base64Image;
	
	/**
	 * Create a new instance containing the successfully located base 64 string
	 * representation of the preview image.
	 * 
	 * @param base64Image the successfully located preview image
	 */
	protected PreviewImageFoundException(String base64Image) {this.base64Image = base64Image;}
	
	/**
	 * Get the successfully located base 64 string representation of the
	 * preview image encapsulated by this instance.
	 * 
	 * @return a base 64 string representation of the successfully located preview image
	 */
	protected String getBase64Image() {return base64Image;}
}