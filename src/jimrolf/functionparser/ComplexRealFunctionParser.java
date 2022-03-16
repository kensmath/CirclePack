/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.jimrolf.functionparser;

import com.jimrolf.complex.Complex;

/**
 *
 * @author jimrolf
 */

/**
 *This parser allows a combination of real and complex variables.
 *This was written for use in initialValFields in MinSurfTool
 * 
 **/
public class ComplexRealFunctionParser extends FunctionParser {
    private String[] complexVariables=null;
    private String[] realVariables=null;
    //private String complexVariable=null;

    /**
     *
     */
    public ComplexRealFunctionParser(){
        super();
        this.setComplex(true);
    }

    /**
     *
     * @param complexVariableVals
     * @param realVariableVals
     * @return
     */
    public Complex evalFunc(Complex[] complexVariableVals, double[] realVariableVals){
        for (int i=0; i<=complexVariableVals.length-1; i++){
            funcParser.addVariable(complexVariables[i],complexVariableVals[i]);
        }
        
        for (int i=0; i<=realVariableVals.length-1; i++){
            funcParser.addVariable(realVariables[i],realVariableVals[i]);
        }

        return  funcParser.getComplexValue();
    }

    /**
     *
     * @param complexVariableVal
     * @param realVariableVals
     * @return
     */
    public Complex evalFunc(Complex complexVariableVal, double[] realVariableVals){

        funcParser.addVariable(complexVariable,complexVariableVal);


        for (int i=0; i<=realVariableVals.length-1; i++){
            funcParser.addVariable(realVariables[i],realVariableVals[i]);
        }

        return  funcParser.getComplexValue();
    }

    @Override
    public void setVariables(String complexVariable, String[] realVariables){
        if (this.variables != null) {
            for (int i = 0; i <= variables.length - 1; i++) {
                funcParser.removeVariable(this.variables[i]);
            }
        }
       funcParser.removeVariable(this.variable);

       if (this.complexVariables!=null){
           for (int i=0; i<=complexVariables.length-1; i++){
               funcParser.removeVariable(this.complexVariables[i]);
           }
       }

       if (this.complexVariable!=null){
           funcParser.removeVariable(this.complexVariable);
       }

       if (this.realVariables!=null){
           for (int i=0; i<=realVariables.length-1; i++){
               funcParser.removeVariable(this.realVariables[i]);
           }
       }

        this.dimension=0;

        if (complexVariable!=null){
            funcParser.addVariable(complexVariable,0.0);
            this.complexVariable=complexVariable;
            this.dimension+=1;

            this.complexVariables=null;//do this so we can determine case in parseExpression()
        }

        if (realVariables!=null){
            this.dimension+=realVariables.length;
            for (int i=0; i<=realVariables.length-1; i++){
                funcParser.addVariable(realVariables[i],0.0);
            }
            this.realVariables=realVariables;
        }
    }

     /**
     * Use this to start parser over (i.e. when computing multiple derivatives) or when funcInput is known
     */
    @Override
    public void parseExpression(){
        funcHasError = false; //need to do this in case we parse a different function
        funcErrorInfo = null;

        this.derivInput = funcInput; //need this in order to take derivative

        if (complexVariables == null) {//so we've passed in only complexVariable
            funcParser.addVariable(complexVariable, 0.0);
            derivParser.addVariable(complexVariable, 0.0);
        } else {
            for (int i = 0; i <= complexVariables.length - 1; i++) {
                funcParser.addVariable(complexVariables[i], 0.0);
                derivParser.addVariable(complexVariables[i], 0.0);
            }

        }

        for (int i = 0; i <= realVariables.length - 1; i++) {
            funcParser.addVariable(realVariables[i], 0.0);
            derivParser.addVariable(realVariables[i], 0.0);
        }

        funcParser.parseExpression(funcInput);
        if (funcParser.getErrorInfo() != null) {
            funcHasError = true;
            funcErrorInfo = funcParser.getErrorInfo();
        }
        
        derivParser.parseExpression(derivInput);
        if (derivParser.getErrorInfo() != null) {
            derivHasError = true;
            derivErrorInfo = derivParser.getErrorInfo();
        }
    }

    /**
     *
     * @param complexVariables
     * @param realVariables
     */
    public void setVariables(String[] complexVariables, String[] realVariables){
        if (this.variables != null) {
            for (int i = 0; i <= variables.length - 1; i++) {
                funcParser.removeVariable(this.variables[i]);
            }
        }
       funcParser.removeVariable(this.variable);

       if (this.complexVariables!=null){
           for (int i=0; i<=complexVariables.length-1; i++){
               funcParser.removeVariable(this.complexVariables[i]);
           }
       }
       
       if (this.complexVariable!=null){
           funcParser.removeVariable(this.complexVariable);
       }

       if (this.realVariables!=null){
           for (int i=0; i<=realVariables.length-1; i++){
               funcParser.removeVariable(this.realVariables[i]);
           }
       }
        
        this.dimension=0;

        if (complexVariables!=null){
            this.dimension+=complexVariables.length;
            for (int i=0; i<=complexVariables.length-1; i++){
                funcParser.addVariable(complexVariables[i],0.0);
            }
            this.complexVariables=complexVariables;
            this.complexVariable=null;//do this so we can determine case in parseExpression()
        }

        if (realVariables!=null){
            this.dimension+=realVariables.length;
            for (int i=0; i<=realVariables.length-1; i++){
                funcParser.addVariable(realVariables[i],0.0);
            }
            this.realVariables=realVariables;
        }
    }

}
