package images;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Vector;

import javax.swing.DefaultComboBoxModel;
import javax.swing.Icon;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.BevelBorder;

/**
 * ComboBox with custom renderer for choosing icons 
 * for CirclePack. The icons are passed as a prepared 
 * vector of CPIcon's.
 */
public class IconComboBox extends JPanel {
	
	private static final long 
	serialVersionUID = 1L;

	public ImagePanel panel;
	public JComboBox<CPIcon> iconBox;
	
	// Constructor
    public IconComboBox() {
    	setLayout(new BorderLayout());
    	panel=new ImagePanel();
    	panel.setSize(28,26);
    	iconBox= new JComboBox<CPIcon>();
    	iconBox.setRenderer(new IconCellRenderer());
    	iconBox.setMaximumRowCount(15);
    	iconBox.addActionListener(new ActionListener() {
    		public void actionPerformed(ActionEvent e) {

    		}
    	});
        add(iconBox, BorderLayout.CENTER);
    }
    
    /**
     * Reset list of icons (have to convert vector to list)
     *
     */
    public void setIconList(Vector<CPIcon> cpIcons) {
    	CPIcon cpIconList[]= new CPIcon[cpIcons.size()];
    	for (int i=0;i<cpIcons.size();i++){
    		CPIcon cpIcon=(CPIcon)cpIcons.get(i);
    		cpIconList[i]=cpIcon;
    	}
    	iconBox.setModel(new DefaultComboBoxModel<CPIcon>(cpIconList));
    }
    
    //  We create our own inner class to handle setting and
    //  repainting the image and the text.
    class ImagePanel extends JPanel {

    	private static final long 
    	serialVersionUID = 1L;
     
        JLabel imageIconLabel;
    
        public ImagePanel() {
            setLayout(new BorderLayout());

            imageIconLabel = new JLabel();
            imageIconLabel.setBorder(new BevelBorder(BevelBorder.RAISED));
            add(imageIconLabel,BorderLayout.CENTER);
        }

        public void setIcon(Icon icon) {
            imageIconLabel.setIcon(icon);
            repaint();
        }

    }
   
}
