/*
 * FunctionField.java
 *
 * Created on August 16, 2006, 3:41 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package com.jimrolf.functionfield;
import com.jimrolf.functionparser.FunctionParser;
import java.awt.Color;
import javax.swing.JLabel;
import javax.swing.JTextArea;

/**
 * FunctionFieldWithWrapping provides same functionality as FunctionField, and adds 
 * automatic line wrapping for text.
 * 
 * <CODE><p>User must import FunctionParser class when using FunctionField class</CODE>
 * @author Jim Rolf
 * @email jim@jimrolf.com
 */
public class FunctionFieldWithWrapping extends  JTextArea{
    
    /** Creates a new instance of FunctionField */
    public FunctionFieldWithWrapping() {
        super();
        parser=new FunctionParser();
        setBackground(backgroundColor);
        setFont(new java.awt.Font("Lucida Sans", 0, 11));
        setBorder(javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.LOWERED));
        setText("");
        setLineWrap(true);
        
    }
    
    /**
     * If error occurs when parsing, the background of the DoubleField object is changed to errorColor.
     * The default is java.awt.Color.yellow.
     * @return Returns boolean
     */
    public boolean hasError(){
        boolean hasProblems=false;
        
        parser.parseExpression(this.getText());
        if (parser.funcHasError()){
            hasProblems=true;
            this.setBackground(errorColor);
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
        
        parser.parseExpression(this.getText());
        if (parser.funcHasError()){
            hasProblems=true;
            errorLabel.setText(parser.getFuncErrorInfo());
            this.setBackground(errorColor);
        }
        
        return hasProblems;
    }
    /**
     * Holds value of property errorColor.
     * Default value is yellow.
     */
    private Color errorColor=java.awt.Color.yellow;;
    
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
    
    /**
     * Holds value of property parser.
     */
    private FunctionParser parser;
    
    
    /**
     * Getter for property errorColor.
     * @return Value of property errorColor.
     */
    public Color getErrorColor() {
        return this.errorColor;
    }
    
    
    /**
     * Getter for property parser.
     *
     * @return Value of property parser.
     */
    public FunctionParser getParser() {
        return this.parser;
    }
    
    
    /**
     * Setter for property errorColor.
     * @parasetm errorColor New value of property errorColor.
     */
    public void setErrorColor(Color errorColor) {
        this.errorColor = errorColor;
    }
    
    
    /**
     * Setter for property parser.
     * @param inputParser FunctionParser object.
     */
    public void setParser(FunctionParser parser) {
        this.parser = parser;
    }
    
    /**
     * Holds value of property complexFunc.
     */
    private boolean complexFunc=false;
    
    /**
     * Getter for property complexFunc.
     * @return Value of property complexFunc.
     */
    public boolean isComplexFunc() {
        return this.complexFunc;
    }
    
    /**
     * Setter for property complexFunc.
     * @param complexFunc New value of property complexFunc.
     */
    public void setComplexFunc(boolean complexFunc) {
        this.complexFunc = complexFunc;
        if (complexFunc==true){
            parser.setComplex(true);
            parser.removeVariable(parser.getVariable());
            parser.setVariable("z");
        }else{
            parser.setComplex(false);
            parser.removeVariable("z");
            parser.setVariable("x");//maybe need to think whether or not we should update with 'x' if user wants to use another variable
        }
    }

}
