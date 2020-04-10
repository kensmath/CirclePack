package util;

import java.awt.Cursor;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import javax.swing.text.Caret;
import javax.swing.text.DefaultCaret;
import javax.swing.text.JTextComponent;

/* Add NavFocusListener to a JTextComponent to show the
 * caret even with setEditable(false).
 * 
 * Usage:
 *     JTextComponent exampleTextComponent;
 *     exampleTextComponent.addFocusListener(
 *         new NavFocusListener(
 *         exampleTextComponent));
 */
public class NavFocusListener implements FocusListener {
	JTextComponent attachToThis;
	Caret textCaret;
	
	public NavFocusListener(JTextComponent passedAttachToThis) {
		//save the component to attach to
		attachToThis = passedAttachToThis;
		
		//instantiate and set a new caret and cursor
		textCaret = new DefaultCaret();
		textCaret.setBlinkRate(500);
		attachToThis.setCaret(textCaret);
		attachToThis.setCursor(new Cursor(Cursor.TEXT_CURSOR));
	}

	public void focusGained(FocusEvent fe) {
		//when text component gains focus
		//show the caret
		textCaret.setVisible(true);
	}

	public void focusLost(FocusEvent fe) {
		//when text component loses focus
		//hide the caret
		textCaret.setVisible(false);
	}
}