package images;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Image;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.File;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;

import exceptions.InOutException;

/**
 * Container for images on 'ImagePanel'; contains thumbnail image,
 * name of actual file (with thumb index, 'id', '.*' extension), name
 * for label, attach menu for actions, movement.
 * @author kens
 *
 */
public class ThumbNail extends JPanel implements MouseListener {
	// Regenerate whenever class fields or methods change.
	private static final long serialVersionUID = 2958505778678412003L;
	
	public File imageFileName;
	public String imageLabel;
	public JLabel label;
	public JLabel theThumb;
	public JPopupMenu thumbMenu;
	
	// Constructor
	public ThumbNail(File file,JPopupMenu tM) throws InOutException {
		super(new BorderLayout());
		imageFileName=file;
		thumbMenu=tM;
		// AF: Add components to this panel to display them in this panel, not
		// to "attach" things like context menus. Use the show method, like in
		// the mouse listener.
		//add(thumbMenu);
		addMouseListener(this);
		
		ImageIcon tmp=loadThumbImage(file);
		if (tmp==null) 
			throw new InOutException("Failed to load thumbnail "+file.getName());
		theThumb=new JLabel(tmp);
		
		add(theThumb,BorderLayout.CENTER);
		imageLabel=file.getName();
		label=new JLabel(imageLabel);
		add(label,BorderLayout.SOUTH);
	}
	
	public ImageIcon loadThumbImage(File file) {
		ImageIcon tmp=null;
		try {
//			URL url = new URL(file.getName());
			Image image=ImageIO.read(file);
			return new ImageIcon(image.getScaledInstance(100,-1,4));
		} catch (Exception e) {
			e.printStackTrace();
		}
		return tmp;
	}

	public void mouseEntered(MouseEvent ev) {}
	public void mouseExited(MouseEvent ev) {}
	public void mouseClicked(MouseEvent ev) {}
	public void mouseReleased(MouseEvent ev) {}
	public void mousePressed(MouseEvent e) {
		thumbMenu.show((Component)e.getSource(),e.getX(),e.getY());
	}
}