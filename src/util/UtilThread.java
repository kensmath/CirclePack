package util;

import javax.swing.JLabel;

/**
 *
 * @author jimrolf
 * In applet/application, the following code will run and stop this thread.
 *
 *          UtilThread myThread=null;
 *          myThread=new UtilThread(jLabel1);
 *          myThread.start();
 *
 *          myThread.setStop(true);
 *
 * Note that I passed in the JLabel jLabel1 in order to see the output. 
 * You can easily comment out the jLabel stuff in this class.
 *
 * The run() method is where you should put code you want to execute.
 *
 */
public class UtilThread extends Thread{

    private boolean stop=false;
    private JLabel label;
    public void setStop(boolean stop){
        this.stop=stop;
    }
    
    // Constructors
    public UtilThread() {
        super();
    }
    public UtilThread(JLabel label) {
        super();
        this.label=label;
    }
    
    public UtilThread(Runnable target, JLabel label){
        super(target);
        this.label=label;
    }
    
    public void run(){
        
        //put code you want executed in here. What outputs the integers updated every second
        
        int counter=0;
        while (!stop){
            label.setText(""+counter);
            counter++;
            try{
                sleep(1000); //1000 represents 1000 milliseconds=1 second.
            } catch(InterruptedException e){}
            
        }
    }
    
}
