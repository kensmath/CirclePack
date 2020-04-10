package frames;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;

public class IncrementalSearch implements DocumentListener, ActionListener, ChangeListener {
    protected JTextComponent content;
    protected Matcher matcher;
    protected JTabbedPane tabbedPane;
    protected JTextField query_field;
    
    // Constructor
    
    public IncrementalSearch(JTabbedPane tab,JTextField qf,JTextComponent comp) {
        this.tabbedPane=tab;
        this.query_field=qf;
        this.content = comp;
    }
    
    /* DocumentListener implementation */
    public void insertUpdate(DocumentEvent evt) {
        runNewSearch(evt.getDocument());
    }
    public void removeUpdate(DocumentEvent evt) {
        runNewSearch(evt.getDocument());
    }
    public void changedUpdate(DocumentEvent evt) {
        runNewSearch(evt.getDocument());
    }
    
    /* ActionListener implementation */
    public void actionPerformed(ActionEvent evt) {
    	conductSearch();
    }
    
    private void runNewSearch(Document query_doc) {
        try {
            String query = query_doc.getText(0,query_doc.getLength());
            Pattern pattern = Pattern.compile(query,Pattern.CASE_INSENSITIVE);
            Document content_doc = content.getDocument();
            String body = content_doc.getText(0,content_doc.getLength());
            matcher = pattern.matcher(body);
            conductSearch();
        } catch (Exception ex) {
            p("exception: " + ex);
            ex.printStackTrace();
        }
    }
    
    private void conductSearch() {
        if(matcher != null) {
            if(matcher.find()) {
                content.getCaret().setDot(matcher.start());
                content.getCaret().moveDot(matcher.end());
                content.getCaret().setSelectionVisible(true);
            }
        }
//        else runNewSearch(query_field.getDocument());
    }
    
    public void stateChanged(ChangeEvent event) {
    	if ((JTabbedPane)event.getSource()!=tabbedPane) return;
    	JScrollPane jsp=(JScrollPane)tabbedPane.getSelectedComponent();
    	JTextComponent newcontent=(JTextComponent)jsp.getViewport().getView();
    	if (newcontent!=content) {
    		content.getCaret().setDot(0); // remove selection in old document
    		content=newcontent;
    	}
    	runNewSearch(query_field.getDocument());
    }
            
    public static void p(String str) {
        System.out.println(str);
    }

}
