package panels;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Toolkit;

import javax.swing.JTextField;

public class StatusPanel extends javax.swing.JPanel {

	private static final long 
	serialVersionUID = 1L;
	
	private JTextField cmdCount;
	private JTextField errorMsgs;

	// Constructor
	public StatusPanel() {
		super();
		initGUI();
	}
	
	private void initGUI() {
		try {
			setLayout(new BorderLayout());
			{
				cmdCount = new JTextField();
				cmdCount.setPreferredSize(new java.awt.Dimension(45, 21));
				cmdCount.setBackground(new Color(255,180,180));
				add(cmdCount, BorderLayout.WEST);
			}
			{
				errorMsgs = new JTextField();
				errorMsgs.setEditable(false);
			    Font font=new Font(errorMsgs.getFont().toString(),Font.PLAIN,12);
				errorMsgs.setPreferredSize(new java.awt.Dimension(-1, 21));
			    errorMsgs.setForeground(Color.red);
			    errorMsgs.setFont(font);
//			    errorMsgs.setBackground(Color.white); // new Color(0,255,0));
				add(errorMsgs, BorderLayout.CENTER);
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	 /**
	   * Shows how many commands were executed in the last string.
	   * @param count
	   */
	  public void cmdCount(int count) {
		  cmdCount.setText(new String(Integer.toString(count)));
	  }
	  
	  /**
	   * Shows error messages from CPack in StatusPanel and Error tab.
	   * @param msg
	   */
	  public void flashErrorMsg(String msg) {
		  errorMsgs.setText(msg);
		  Toolkit.getDefaultToolkit().beep();
//			  System.out.print("\007");
		  System.out.flush();
	 }
}
