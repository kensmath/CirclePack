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
import java.text.DecimalFormat;
import javax.swing.JLabel;
import javax.swing.JTextField;

/**
 * DoubleField is useful for inputting double and using in further calculations. Use by calling
 * the hasError() or hasError(JLabel errorLabel) methods.  These will parse the inputted string
 * and check to see if this is in fact a double.
 *
 * <CODE><p></CODE> The privary benefit of this field is that it allows the user to input sqrt(2), pi, e, etc. instead of
 * having to compute these separately and then transcribing into a usual JTextField.
 * <CODE><p></CODE>
 * Error checking is handled behind the scenes and requires the user to import the location of the
 * FunctionParser class.
 * <CODE><p></CODE>
 * The double representation of the String text is contained in doubleVal.
 * @author Jim Rolf
 * @email jim@jimrolf.com
 */
public class DoubleField extends JTextField{
    
    private DecimalFormat outNumber=new DecimalFormat();
    private String pattern="0.000000000000000";
    
    /** Creates a new instance of FunctionField */
    public DoubleField() {
        parser=new FunctionParser();
        errorColor=java.awt.Color.yellow;
        
        setBackground(backgroundColor);
        setFont(new java.awt.Font("Lucida Sans", 0, 10));
        setBorder(javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.LOWERED));
        super.setText("0.0");
        outNumber.applyPattern(pattern);
        outNumber.setMinimumIntegerDigits(1);
        outNumber.setMinimumFractionDigits(1);
        outNumber.setMaximumFractionDigits(numDecimalPlaces);
    }
    
    /**
     * Holds value of property parser.
     */
    public FunctionParser parser;
    
    /**
     * Getter for property parser.
     * @return Value of property parser.
     */
    public FunctionParser getInputParser() {
        return this.parser;
    }
    
    /**
     * Setter for property parser.
     * @param parser New value of property parser.
     */
    public void setInputParser(FunctionParser inputParser) {
        this.parser = inputParser;
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

    public void highlightError(){
        this.setBackground(errorColor);
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
     * @return Returns boolean.
     */
    public boolean hasError(){
        boolean hasProblems=false;
        
        parser.parseExpression(this.getText());
        
        if (parser.funcHasError() || Double.isNaN(parser.evalFunc(0.0)) || Double.isInfinite(parser.evalFunc(0.0))){
            this.setBackground(errorColor);
            errorInfo="Error with constant.";
            hasProblems=true;
        } else{
            doubleVal=parser.evalFunc(0.0);
        }
        
        
        return hasProblems;
    }
    
    /**
     * If error occurs when parsing, the background of the DoubleField object is changed to errorColor.
     * The default is java.awt.Color.yellow.
     *
     * Error message is sent to errorLabel.
     * @param errorLabel Label that error message is sent to.
     * @return Returns boolean.
     */
    public boolean hasError(JLabel errorLabel){
        boolean hasProblems=false;
        
        parser.parseExpression(this.getText());
        
        if (parser.funcHasError() || Double.isNaN(parser.evalFunc(0.0)) || Double.isInfinite(parser.evalFunc(0.0))){
            this.setBackground(errorColor);
            errorInfo="Error with constant.";
            errorLabel.setText(errorInfo);
            hasProblems=true;
        } else{
            doubleVal=parser.evalFunc(0.0);
        }
        
        
        return hasProblems;
    }
    
    /**
     * Holds value of property doubleVal.
     */
    private double doubleVal;
    
    /**
     * Getter for property doubleVal.
     * @return Value of property doubleVal.
     */
    public double getDoubleVal() {
        return this.doubleVal;
    }
    
    /**
     * Setter for property doubleVal.
     * @param doubleVal New value of property doubleVal.
     */
    public void setDoubleVal(double doubleVal) {
        this.doubleVal=doubleVal;
    }
    
    /**
     * Overirides usual setText(String) method to ensure user can see the first part of the number in the DoubleField
     * rather than the last part.
     * @param input String representation for double
     */
    @Override
    public void setText(String input){
        super.setText(input);
        moveCaretPosition(0);
    }
    
    /**
     * This is a formatted version of the setText() method.
     * @param x Double value
     */
    public void setDoubleText(double x){
        super.setText(""+outNumber.format(x));
        moveCaretPosition(0);
        this.doubleVal=x;
    }
    
    /**
     * Holds value of property numDecimalPlaces.
     */
    private int numDecimalPlaces=8;
    
    /**
     * Getter for property numDecimalPlaces.
     * @return Value of property numDecimalPlaces.
     */
    public int getNumDecimalPlaces() {
        return this.numDecimalPlaces;
    }
    
    /**
     * Setter for property numDecimalPlaces.
     * @param numDecimalPlaces New value of property numDecimalPlaces.
     */
    public void setNumDecimalPlaces(int numDecimalPlaces) {
        this.numDecimalPlaces = numDecimalPlaces;
        outNumber.setMaximumFractionDigits(numDecimalPlaces);
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
