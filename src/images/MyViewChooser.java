package images;

import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;

/** 
 * An example that uses custom file views to show thumbnails of 
 * graphic files rather than the regular file icon.  
 * (see ThumbnailFileView.java)
 */
public class MyViewChooser extends JFrame {
  
	private static final long serialVersionUID = 1L;
	
	JFrame parent;
  
	// Constructor
  
	public MyViewChooser() {
		super("Icon View Test Frame");
		setSize(350, 200);
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		parent = this;

		Container c = getContentPane();
		c.setLayout(new FlowLayout());
		
		JButton openButton = new JButton("Open");
		final JLabel statusbar = 
				new JLabel("Output of your selection will go here");

		openButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ae) {
				JFileChooser chooser = new JFileChooser();

				// Ok, set up our own file view for the chooser
				chooser.setFileView(new ThumbNailFileView());

				int option = chooser.showOpenDialog(parent);
				if (option == JFileChooser.APPROVE_OPTION) {
					statusbar.setText("You chose " + 
							chooser.getSelectedFile().getName());
				}
				else {
					statusbar.setText("You cancelled.");
				}
			}
		});

		c.add(openButton);
		c.add(statusbar);
	}

}
