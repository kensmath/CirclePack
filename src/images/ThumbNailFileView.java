package images;
import java.awt.Component;
import java.awt.Graphics;
import java.io.File;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.filechooser.FileView;
import javax.swing.plaf.metal.MetalIconFactory;

/**
 * a FileView class that provides a 22x22 image of each GIF, JPG, or PNG 
 * file for its icon. This could be SLOW for large images, as we
 *simply load the real image and then scale it.
 */
public class ThumbNailFileView extends FileView {

  private Icon fileIcon = MetalIconFactory.getTreeLeafIcon();
  private Icon folderIcon = MetalIconFactory.getTreeFolderIcon();

  public ThumbNailFileView() { //Component c) {
    // We need a component around to create our icon’s image
//    observer = c;
  }

  public String getDescription(File f) {
    // We won’t store individual descriptions, so just return the
    // type description.
    return getTypeDescription(f);
  }

  public Icon getIcon(File f) {
    // Is it a folder?
    if (f.isDirectory()) { return folderIcon; }

    // Ok, it’s a file, so return a custom icon if it’s an image file
    String name = f.getName().toLowerCase();
    if (name.endsWith(".jpg") || name.endsWith(".gif") || name.endsWith(".png")) {
      return new Icon22(f.getAbsolutePath());
    }

    // Return the generic file icon if it’s not
    return fileIcon;
  }

  public String getName(File f) {
    String name = f.getName();
    return name.equals("") ? f.getPath() : name;
  }

  public String getTypeDescription(File f) {
    String name = f.getName().toLowerCase();
    if (f.isDirectory()) { return "Folder"; }
    if (name.endsWith(".jpg")) { return "JPEG Image"; }
    if (name.endsWith(".gif")) { return "GIF Image"; }
    if (name.endsWith(".png")) { return "PNG Image"; }
    return "Generic File";
  }

  public Boolean isTraversable(File f) {
    // We’ll mark all directories as traversable
    return f.isDirectory() ? Boolean.TRUE : Boolean.FALSE;
  }

  public class Icon22 extends ImageIcon {

	private static final long 
	serialVersionUID = 1L;

	// Constructor
    public Icon22(String f) { 
      super(f);
//      Image i = observer.createImage(22, 22);
//      i.getGraphics().drawImage(getImage(), 0, 0, 22, 22, observer);
//      setImage(i);
    }

    public int getIconHeight() { return 22; }
    public int getIconWidth() { return 22; }
    
    public void paintIcon(Component c, Graphics g, int x, int y) {
      g.drawImage(getImage(), x, y, c);
    }
  }
}
