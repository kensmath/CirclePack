package browser;

import java.net.URL;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.events.Event;
import org.w3c.dom.events.EventListener;
import org.w3c.dom.events.EventTarget;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Worker;
import javafx.concurrent.Worker.State;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import util.FileUtil;

public class WebViewRenderer extends JFXPanel {
	
	private static final long serialVersionUID = 7248705697046383784L;

    public static final String EVENT_TYPE_CLICK = "click";
    public static final String EVENT_TYPE_MOUSEOVER = "mouseover";
    public static final String EVENT_TYPE_MOUSEOUT = "mouseclick";

    protected WebView webView;
    protected Stage webStage;
    protected WebEngine webEngine;
    
    // constructor
    public WebViewRenderer() {
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
            	webView=new WebView();
            	webEngine=webView.getEngine();
            	setScene(new Scene(webView));
            	webView.setVisible(true);
                initListener();
            }
        });
    }
    
    protected void initListener() {
        webEngine.getLoadWorker().stateProperty().addListener(new ChangeListener<State>() {
            @Override
            public void changed(ObservableValue ov, State oldState, State newState) {
                if (newState == Worker.State.SUCCEEDED) {
//                  webStage.setTitle(webEngine.getLocation());
                    EventListener listener = new EventListener() {
                        @Override
                        public void handleEvent(Event ev) {
                            String domEventType = ev.getType();
                            //System.err.println("EventType: " + domEventType);
                            if (domEventType.equals(EVENT_TYPE_CLICK)) {
                                String href = ((Element)ev.getTarget()).getAttribute("href");
                                System.out.println("in WebViewRenderer, href = "+href);
                                
                                URL theURL=null;
                                if ((theURL=FileUtil.tryURL(href))==null)
                                	return;
        
//                                FXWebBrowser.processLink(theURL);
                                
                                ////////////////////// 
                                // here do what you want with that clicked event 
                                // and the content of href 
                                //////////////////////                               
                            } 
                        }
                    };

                    Document doc = webView.getEngine().getDocument();
                    NodeList nodeList = doc.getElementsByTagName("a");
                    for (int i = 0; i < nodeList.getLength(); i++) {
                        ((EventTarget) nodeList.item(i)).addEventListener(EVENT_TYPE_CLICK, listener, false);
                        //((EventTarget) nodeList.item(i)).addEventListener(EVENT_TYPE_MOUSEOVER, listener, false);
                        //((EventTarget) nodeList.item(i)).addEventListener(EVENT_TYPE_MOUSEOUT, listener, false);
                    }
                }
            }
        });
    }
    
    public void loadWebPage(String url) {
        Platform.runLater(() -> {
        	if (FileUtil.isLocal(url)) {
           		String newurl=FileUtil.parseURL(url).toString();
           		webEngine.load(newurl);
           	}
        	else
        		webEngine.load(url);
        	webView.setVisible(true);
        }); // 
    }
    
/*
	public void addHyperlinkListener(HyperlinkListener listener) {
        listenerList.add(HyperlinkListener.class, listener);
    }
 
    public void removeHyperlinkListener(HyperlinkListener listener) {
        listenerList.remove(HyperlinkListener.class, listener);
    }
 
    protected void fireHyperlinkUpdate(javax.swing.event.HyperlinkEvent.EventType eventType, String desc) {
        HyperlinkEvent event = new HyperlinkEvent(this, eventType, null, desc);
 
        // Guaranteed to return a non-null array
        Object[] listeners = listenerList.getListenerList();
        // Process the listeners last to first, notifying
        // those that are interested in this event
        for (int i = listeners.length - 2; i >= 0; i -= 2) {
            if (listeners[i] == HyperlinkListener.class) {
                ((HyperlinkListener) listeners[i + 1]).hyperlinkUpdate(event);
            }
        }
    }
*/    
}
