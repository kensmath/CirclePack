package util;

import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.JTextComponent;
import javax.swing.text.TextAction;

/**
 * For emacs-like key bindings on various input JComponents.
 * TODO: cannot get correct action to set the 'mark'; see
 * attempt below.
 * @author kens
 *
 */
public class EmacsBindings {
	
	public static void addEmacsBindings(JComponent jcomponent) {
		InputMap inputMap = jcomponent.getInputMap();

		// Ctrl-b to go backward one character
		KeyStroke key = KeyStroke.getKeyStroke(KeyEvent.VK_B, InputEvent.CTRL_DOWN_MASK);
		inputMap.put(key, DefaultEditorKit.backwardAction);
		// Ctrl-f to go forward one character
		key = KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK);
		inputMap.put(key, DefaultEditorKit.forwardAction);
		// Ctrl-d delete next character
		key = KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK);
		inputMap.put(key, DefaultEditorKit.deleteNextCharAction);
		// Ctrl-p up one line
		key = KeyStroke.getKeyStroke(KeyEvent.VK_P, InputEvent.CTRL_DOWN_MASK);
		inputMap.put(key, DefaultEditorKit.upAction);
		// Ctrl-n down one line
		key = KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK);
		inputMap.put(key, DefaultEditorKit.downAction);
		// Ctrl-e to end of line
		key = KeyStroke.getKeyStroke(KeyEvent.VK_E, InputEvent.CTRL_DOWN_MASK);
		inputMap.put(key, DefaultEditorKit.endLineAction);
		// Ctrl-a to beginning of line
		key = KeyStroke.getKeyStroke(KeyEvent.VK_A, InputEvent.CTRL_DOWN_MASK);
		inputMap.put(key, DefaultEditorKit.beginLineAction);
		// Ctrl-y insert from clipboard
		key = KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK);
		inputMap.put(key, DefaultEditorKit.pasteAction);
		// Ctrl-k delete to end of line
		key = KeyStroke.getKeyStroke(KeyEvent.VK_K, InputEvent.CTRL_DOWN_MASK);
		inputMap.put(key, DefaultEditorKit.deleteNextWordAction);
		// Ctrl-w cut selection, move to clipboard
		key = KeyStroke.getKeyStroke(KeyEvent.VK_W, InputEvent.CTRL_DOWN_MASK);
		inputMap.put(key, DefaultEditorKit.cutAction);
	}   
	
	/**
	 * TODO: doesn't do what I want --- emacs type mark setting
     * Set the 'mark' at the caret location. 
     * @see DefaultEditorKit#beginAction
     */
    static class MarkAction extends TextAction {
    	
    	private static final long 
    	serialVersionUID = 1L;
    		
        /* Create this object with the appropriate identifier. */
        MarkAction(String nm) {
            super(nm);
        }

        /** The operation to perform when this action is triggered. */
        public void actionPerformed(ActionEvent e) {
            JTextComponent target = getTextComponent(e);
            if (target != null) {
            	int spot=target.getCaretPosition();
            	target.getCaret().setDot(spot);
            }
        }
    }

}
