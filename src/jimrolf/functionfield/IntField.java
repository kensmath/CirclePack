/*
 * FunctionField.java
 *
 * Created on August 16, 2006, 3:41 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package com.jimrolf.functionfield;
import com.jimrolf.convert.Convert;
import java.awt.Color;
import javax.swing.JLabel;
import javax.swing.JTextField;

/**
 * IntField is useful for inputting an int and using it in further calculations. Use this class by calling 
 * the hasError() or hasError(JLabel errorLabel) methods.  These will parse the inputted string
 * and check to see if this is in fact an int.
 * <CODE><p></CODE>
 * Error checking is handled behind the scenes and requires the user to import the location of the 
 * FunctionParser class.
 * <CODE><p></CODE>
 * The int representation of the String text is contained in intVal.
 * @author Jim Rolf
 * @email jim@jimrolf.com
 */
public class IntField extends JTextField{
    
    /** Creates a new instance of FunctionField */
    public IntField() {
        errorColor=java.awt.Color.yellow;
        setBackground(backgroundColor);
        setFont(new java.awt.Font("Lucida Sans", 0, 10));
        setBorder(javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.LOWERED));
        setText("0");
    }
    
    /**
     * Holds value of property errorColor.
     */
    private Color errorColor;
    
    /**
     * Getter for property errorColor.
     * @return Value of property errorColor.
     */
    public Color getErrorColor() {
        return this.errorColor;
    }
    
    /**
     * Setter for property errorColor.
     * @param errorColor New value of property errorColor.
     */
    public void setErrorColor(Color errorColor) {
        this.errorColor = errorColor;
    }
    
    /**
     * Holds value of property errorInfo.
     */
    private String errorInfo;
    
    /**
     * Getter for property errorInfo.
     * @return Value of property errorInfo.
     */
    public String getErrorInfo() {
        return this.errorInfo;
    }
    
    /**
     * Setter for property errorInfo.
     * @param errorInfo New value of property errorInfo.
     */
    public void setErrorInfo(String errorInfo) {
        this.errorInfo = errorInfo;
    }
    
    /**
     * If error occurs when parsing, the background of the DoubleField object is changed to errorColor.
     * The default is java.awt.Color.yellow.
     * @return Returns boolean
     */
    public boolean hasError(){
        boolean hasProblems=false;
        
        
        if (!Convert.isInt(this.getText())){
            hasProblems=true;
            errorInfo="Error with constant.";
            this.setBackground(errorColor);
        }else{
            intVal=Convert.toInt(this.getText());
        }
        
        return hasProblems;
    }
    
    /**
     * If error occurs when parsing, the background of the DoubleField object is changed to errorColor.
     * The default is java.awt.Color.yellow.
     * 
     * Error message is sent to errorLabel.
     * @param errorLabel Error message is sent to errorLabel.
     * @return Returns boolean
     */
    public boolean hasError(JLabel errorLabel){
        boolean hasProblems=false;
        
        if (!Convert.isInt(this.getText())){
            hasProblems=true;
            errorInfo="Error with constant.";
            errorLabel.setText(errorInfo);
            this.setBackground(errorColor);
        }else{
            intVal=Convert.toInt(this.getText());
        }   
        return hasProblems;
    }
    
    /**
     * Holds value of property intVal.
     */
    private int intVal;
    
    /**
     * Getter for property intVal.
     * @return Value of property intVal.
     */
    public int getIntVal() {
        return this.intVal;
    }
    
    /**
     * Setter for property intVal.
     * @param intVal New value of property intVal.
     */
    public void setIntVal(int intVal) {
        this.intVal=intVal;
    }

    public void setIntText(int intVal){
        this.setText(""+intVal);
        this.intVal=intVal;
    }
    
    /**
     * Overirides usual setText(String) method to ensure user can see the first part of the number in the 
     * IntField rather than the last part.
     * @param input String representation of int.
     */
    @Override
    public void setText(String input){
        super.setText(input);
        moveCaretPosition(0);
    }
    
    protected Color backgroundColor = new Color(235, 235, 235);

    /**
     * Get the value of backgroundColor
     *
     * @return the value of backgroundColor
     */
    public Color getBackgroundColor() {
        return backgroundColor;
    }

    /**
     * Set the value of backgroundColor
     *
     * @param backgroundColor new value of backgroundColor
     */
    
    public void setBackgroundColor(Color backgroundColor) {
        this.backgroundColor = backgroundColor;
        this.setBackground(backgroundColor);
    }
    
}
