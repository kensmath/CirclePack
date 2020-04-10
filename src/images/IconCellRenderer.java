package images;

import java.awt.Color;
import java.awt.Component;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;

/**
 * for rendering icons in icon combobox. The JComboBox list is a
 * list of CPIcon's, so that's what 'value' will be in the calls.
 * @author kens
 *
 */
public class IconCellRenderer extends JLabel implements ListCellRenderer<Object> {

	private static final long serialVersionUID = 1L;
	
	private static final Color HIGHLIGHT_COLOR = new Color(0, 0, 128);

    // 
    public IconCellRenderer() {
        setOpaque(true);
        setIconTextGap(2);
    }

    public Component getListCellRendererComponent(
        JList<?> list,
        Object value,
        int index,
        boolean isSelected,
        boolean cellHasFocus)
    {
    	CPIcon cpi=(CPIcon)value;
        setIcon((ImageIcon)cpi.getBaseIcon());
        if(isSelected) {
            setBackground(HIGHLIGHT_COLOR);
            setForeground(Color.white);
        } else {
            setBackground(Color.white);
            setForeground(Color.black);
        }
        setSize(26,26);
        return this;
    }
}
