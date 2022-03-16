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
import javax.swing.JTextField;

/**
 * FunctionField provides error checking for string inputs that will be parsed by FunctionParser.
 * Use by calling the hasError() or hasError(errorLabel) methods.
 *
 * <CODE><p>User must import location of FunctionParser class when using FunctionField class</CODE>
 * @author Jim Rolf
 * @email jim@jimrolf.com
 */
public class FunctionField extends  JTextField{
    
    /** Creates a new instance of FunctionField */
    public FunctionField() {
        parser=new FunctionParser();
        setBackground(backgroundColor);
        setFont(new java.awt.Font("Lucida Sans", 0, 11));
        setBorder(javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.LOWERED));
        setText("");
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

    public void highlightError(){
        this.setBackground(errorColor);
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
            errorInfo="Error with function(s).";
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
            errorInfo="Error with function(s).";
            errorLabel.setText(errorInfo);
            this.setBackground(errorColor);
        }
        
        return hasProblems;
    }
    
    /**
     * Deprecated. Use getParser().derivHasError()
     */
    public boolean derivHasError(){
        boolean hasProblems=false;
        
        if (parser.derivHasError()){
            hasProblems=true;
            errorInfo="Error in derivative";
            this.setBackground(errorColor);
        }
        return hasProblems;
    }
    
    /**
     * Deprecated. Use getParser().derivHasError(errorLabel)
     * @param errorLabel
     * @return
     */
    public boolean derivHasError(JLabel errorLabel){
        boolean hasProblems=false;
        
        if (parser.derivHasError()){
            hasProblems=true;
            errorInfo="Error in derivative";
            errorLabel.setText(errorInfo);
            this.setBackground(errorColor);
        }
        return hasProblems;
    }
    
    
    private Color backgroundColor = new Color(235, 235, 235);

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
    public FunctionParser parser;
    /**
     * Holds value of property errorColor. Default value is yellow.
     */
    private Color errorColor = java.awt.Color.yellow;

    /**
     * Getter for property errorColor.
     *
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
     * Setter for property errorColor. @parasetm errorColor New value of
     * property errorColor.
     */
    public void setErrorColor(Color errorColor) {
        this.errorColor = errorColor;
    }

    /**
     * Setter for property parser.
     *
     * @param parser FunctionParser object.
     */
    public void setParser(FunctionParser parser) {
        this.parser = parser;
    }
    /**
     * Holds value of property complexFunc. 'True' will turn on the parameter
     * "complex" in the FunctionParser and automatically assign the variable 'z'
     * to be the domain variable in the complex function. To change variable to
     * something else, use the getParser().removeVariable("z") command followed
     * by getParser.setVariable(string).
     */
    private boolean complexFunc = false;

    /**
     * Getter for property complexFunc.
     *
     * @return Value of property complexFunc.
     */
    public boolean isComplexFunc() {
        return this.complexFunc;
    }

    /**
     * Setter for property complexFunc.
     *
     * @param complexFunc New value of property complexFunc.
     */
    public void setComplexFunc(boolean complexFunc) {
        this.complexFunc = complexFunc;
        if (complexFunc == true) {
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
