package previewimage;

import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

/**
 * This class is a handler for SAX parsers that will throw an exception upon
 * successful retrieval of the content contained within "preview-image" tags.
 * Throwing the exception will generally interrupt the SAX parser, preventing
 * unnecessary additional parsing after the desired content has been found.
 * 
 * @author Alex Fawkes
 *
 */
class PreviewImageSearchHandler extends DefaultHandler {
	protected static final String PREVIEW_IMAGE_Q_NAME = "AboutImage"; // The tag to look for.
	protected StringBuilder previewImageBuilder = new StringBuilder(); // Used to construct the base 64 image string.
	protected boolean previewImageFound = false; // Whether or not the preview image opening tag has been encountered.

	@Override
	public void startElement(String uri, String localName, String qName, Attributes attributes) {
		// If we have found the preview image opening tag, note that fact.
		if (qName.equals(PREVIEW_IMAGE_Q_NAME)) previewImageFound = true;
	}
	
	@Override
	public void endElement(String uri, String localName, String qName) throws PreviewImageFoundException {
		/*
		 * If we have previously found the preview image opening tag, and we have
		 * now found the preview image closing tag, construct the base 64 image
		 * string from the string builder and throw it in an exception.
		 */
		if (previewImageFound && qName.equals(PREVIEW_IMAGE_Q_NAME))
			throw new PreviewImageFoundException(previewImageBuilder.toString());
	}

	@Override
	public void characters(char[] ch, int start, int length) {
		// If we have already found the preview image opening tag, copy the content as it is read.
		// This may occur in several chunks, so we use a string builder to accumulate them.
		if (previewImageFound) previewImageBuilder.append(new String(ch, start, length));
	}
}
