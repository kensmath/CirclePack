package util;

import java.awt.Color;
import java.awt.Font;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Vector;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JTextField;
import javax.swing.border.LineBorder;

import circlePack.PackControl;
import exceptions.InOutException;

/**
 * Extends 'ComboBox' for situations where file/web 
 * addresses are maintained and stored in lists and 
 * startup files. 
 * @author kens
 *
 */
public class MemComboBox extends JComboBox<String>
  implements KeyListener, MouseMotionListener { //, ItemListener {

	private static final long 
	serialVersionUID = 1L;
	
	public static final int MAX_MEM_LEN = 15;
	public static boolean addOKflag=true;
	File URLfile;   // file containing saved strings for this combo box.
	Vector<String> urlVector;  // strings themselves
	DefaultComboBoxModel<String> model;
	protected JTextField  m_editor;

	// Constructor
	public MemComboBox(File urlFile) {
		m_editor = (JTextField)getEditor().getEditorComponent();
		m_editor.addKeyListener(this);
//		addItemListener(this);
//		m_editor.setBorder(new EmptyBorder(new Insets(3,5,2,5)));
		setFont(new Font(m_editor.getFont().toString(),Font.ROMAN_BASELINE,10));
		setEditable(true);
		addMouseMotionListener(this);
		setBackground(Color.white); // LIGHT_GRAY);
		setNeutral();
		urlVector=new Vector<String>(MAX_MEM_LEN+1);
		URLfile=urlFile;
		if (URLfile==null)
			System.err.println("URLfile is 'null' in 'MemComboBox'");
		try {
			loadURLs(URLfile);
		} catch (Exception e) {
		}
	}

	/**
	 * Loads 'urlVector' of saved URL's from a named file
	 * @param File file, source of saved URL's
	 */
	public void loadURLs(File file) {
		BufferedReader fileReader=null;
		if (file!=null) {
			try {
				fileReader = new BufferedReader(new FileReader(file));
			} catch (FileNotFoundException fnfe) {
				// can't send error via flashErrorMsg because it's not initiated yet
				fileReader=null;
				throw new InOutException("Failed to load '"+file+"' into MemComboBox");
			}
		}
		if (fileReader!=null) {
			URLfile=file; // only change horses if it seems to work
			try {
				String com = fileReader.readLine();
				int count=0;
				while (count<MAX_MEM_LEN && com != null) {
					urlVector.add(com);
					com = fileReader.readLine();
				}
			} catch (IOException ioe) {
				String errmsg="Error in loading web addresses";
				PackControl.consoleCmd.dispConsoleMsg(errmsg);
				PackControl.shellManager.recordError(errmsg);
				urlVector=new Vector<String>(MAX_MEM_LEN+1);
			}
		}
		model=new DefaultComboBoxModel<String>(urlVector);
		setModel(model);
	}
	
	/**
	 * Add 'itemname' to stored list held for this combo box; 
	 * name moves (or is added) to stored list so it comes
	 * up first next time CirclePack is run. However, there are
	 * problems moving it in the model's element list (since
	 * that triggers reloading actions), so we just fix the
	 * saved list.
	 * @param itemname String,
	 * @param loadOK boolean: false, don't actually load this file,
	 * just put it in list (e.g., for name of script when saved).
	 */
	public void add2List(String itemname,boolean loadOK) {
		if (model==null) return;
		int hit=-1;
		for (int i=model.getSize()-1;i>=0;i--) {
			String str=(String)model.getElementAt(i);
			if (str.equals(itemname)) {
				hit=i;
//				model.removeElementAt(i);
			}
		}
		
		// not there? insert at top of list
		if (hit<0 && !itemname.endsWith("new_script.xmd")) {
			model.insertElementAt(itemname,0); 
			hit=0;
		}
		save(hit); // saving each time isn't efficient, 
		// but when else??
		// String getOb=(String)model.getElementAt(0);
		//	if (getOb==null || !getOb.equals(itemname))
		//	model.insertElementAt((Object)itemname,0);
		// setting selection triggers reading of the file if loadOK true
		addOKflag=loadOK;
		model.setSelectedItem((Object)itemname);
		if (model.getSize() > MAX_MEM_LEN)
			model.removeElementAt(model.getSize()-1);
		addOKflag=true;
	}

	/**
	 * Saves the URL's entered in this combobox in the 
	 * designated file, putting the element of index 
	 * 'hitindx' at the top.
	 * @param hit int
	 */
	public void save(int hitindx) {
		BufferedWriter writer;
		try {
			writer = new BufferedWriter(new FileWriter(URLfile));
			if (hitindx>=0) {
				writer.write((String)urlVector.get(hitindx));
				writer.newLine();
			}
			for (int i=0;i<urlVector.size();i++) {
				if (i!=hitindx) {
					writer.write((String)urlVector.get(i));
					writer.newLine();
				}
			}
			writer.flush();
			writer.close();
		}
		catch (Exception e) {
			e.printStackTrace();
			System.err.println("Error writing URLs to '"+URLfile+"'");
		}
	}

	/** 
	 * returns the entry in the combobox edit line, 
	 * suitably adjusted
	 * @return String
	 */
	public String getURLstring() { // add 'http://' if needed
		String address=m_editor.getText().trim();
		if (address.startsWith("www."))
			address=new String("http://"+address);
		return address;
	}
	
	public void setURLstring(String urlstr) { 
//			m_editor.setText(urlstr.trim());
			model.setSelectedItem((Object)urlstr);
	}
	
	/**
	 * set script choose border to black, neutral
	 */
	public void setNeutral() {
		m_editor.setBorder(new LineBorder(Color.black,1,false));
	}
	
	/**
	 * set script chooser border to green, indicating success in loading
	 */
	public void setSuccess() {
		m_editor.setBorder(new LineBorder(Color.green,2,false));
	}
	
	/**
	 * set script chooser border to red, indicating failure in loading
	 */
	public void setFailure() {
		m_editor.setBorder(new LineBorder(Color.red,2,false));
	}
	
	// This seems to give the typing completion feature
	public void keyReleased(KeyEvent e) {
		char ch = e.getKeyChar();
		
		// catch enter and fire action even --- may want to load
		if (ch == KeyEvent.VK_ENTER) { // pressed enter?
//			System.out.println("MemComboBox enter pressed");
			fireActionEvent(); 
			return;
		}
		
		// should we ignore?
		if (ch == KeyEvent.CHAR_UNDEFINED || Character.isISOControl(ch))
			return;
		
		int pos = m_editor.getCaretPosition();
		String str = m_editor.getText();
		if (str.length() == 0) // no characters in the editor
			return;

		// for completion: match with strings already in our list
		for (int k=0; k<this.getItemCount(); k++) {
			String item = this.getItemAt(k).toString();
			if (item.startsWith(str)) {
				m_editor.setText(item);
				m_editor.setCaretPosition(item.length());
				m_editor.moveCaretPosition(pos);
				break;
			}
		}
		
	}
	
	public void keyPressed(KeyEvent e) {
//		System.out.println("MemComboBox keyPressed");
	}
	public void keyTyped(KeyEvent e) {
//		System.out.println("MemComboBox keyTyped");
	}
	
	public void mouseEntered(MouseEvent e) {}
	public void mouseExited(MouseEvent e) {
		setNeutral();
	}
	public void mouseMoved(MouseEvent e) {}
	public void mouseDragged(MouseEvent e) {}
	  
//	public void itemStateChanged(ItemEvent iev) {
//		ItemListener []iltnr=getItemListeners();
//		System.err.println("there are "+iltnr.length+" listeners");
//		iltnr[0].itemStateChanged(iev);
//	}
	
}
