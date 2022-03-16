/*
 * FunctionFieldBeanInfo.java
 *
 * Created on October 10, 2007, 10:35 AM
 */

package com.jimrolf.functionfield;

import java.beans.*;

/**
 * @author jimrolf
 */
public class FunctionFieldBeanInfo extends SimpleBeanInfo {
    
    // Bean descriptor//GEN-FIRST:BeanDescriptor
    /*lazy BeanDescriptor*/
    private static BeanDescriptor getBdescriptor(){
        BeanDescriptor beanDescriptor = new BeanDescriptor  ( com.jimrolf.functionfield.FunctionField.class , null ); // NOI18N//GEN-HEADEREND:BeanDescriptor
        
        // Here you can add code for customizing the BeanDescriptor.
        
        return beanDescriptor;     }//GEN-LAST:BeanDescriptor
    
    
    // Property identifiers//GEN-FIRST:Properties
    private static final int PROPERTY_alignmentY = 0;
    private static final int PROPERTY_ancestorListeners = 1;
    private static final int PROPERTY_background = 2;
    private static final int PROPERTY_backgroundColor = 3;
    private static final int PROPERTY_backgroundSet = 4;
    private static final int PROPERTY_border = 5;
    private static final int PROPERTY_bounds = 6;
    private static final int PROPERTY_caret = 7;
    private static final int PROPERTY_caretColor = 8;
    private static final int PROPERTY_caretPosition = 9;
    private static final int PROPERTY_complexFunc = 10;
    private static final int PROPERTY_displayable = 11;
    private static final int PROPERTY_doubleBuffered = 12;
    private static final int PROPERTY_dragEnabled = 13;
    private static final int PROPERTY_editable = 14;
    private static final int PROPERTY_enabled = 15;
    private static final int PROPERTY_errorColor = 16;
    private static final int PROPERTY_focusable = 17;
    private static final int PROPERTY_focusCycleRoot = 18;
    private static final int PROPERTY_focusOwner = 19;
    private static final int PROPERTY_focusTraversable = 20;
    private static final int PROPERTY_focusTraversalPolicy = 21;
    private static final int PROPERTY_focusTraversalPolicyProvider = 22;
    private static final int PROPERTY_focusTraversalPolicySet = 23;
    private static final int PROPERTY_font = 24;
    private static final int PROPERTY_foreground = 25;
    private static final int PROPERTY_height = 26;
    private static final int PROPERTY_highlighter = 27;
    private static final int PROPERTY_horizontalAlignment = 28;
    private static final int PROPERTY_horizontalVisibility = 29;
    private static final int PROPERTY_ignoreRepaint = 30;
    private static final int PROPERTY_insets = 31;
    private static final int PROPERTY_layout = 32;
    private static final int PROPERTY_lightweight = 33;
    private static final int PROPERTY_location = 34;
    private static final int PROPERTY_managingFocus = 35;
    private static final int PROPERTY_margin = 36;
    private static final int PROPERTY_maximumSize = 37;
    private static final int PROPERTY_maximumSizeSet = 38;
    private static final int PROPERTY_minimumSize = 39;
    private static final int PROPERTY_name = 40;
    private static final int PROPERTY_nextFocusableComponent = 41;
    private static final int PROPERTY_opaque = 42;
    private static final int PROPERTY_preferredSize = 43;
    private static final int PROPERTY_requestFocusEnabled = 44;
    private static final int PROPERTY_scrollOffset = 45;
    private static final int PROPERTY_selectedText = 46;
    private static final int PROPERTY_selectedTextColor = 47;
    private static final int PROPERTY_selectionColor = 48;
    private static final int PROPERTY_selectionEnd = 49;
    private static final int PROPERTY_selectionStart = 50;
    private static final int PROPERTY_size = 51;
    private static final int PROPERTY_text = 52;
    private static final int PROPERTY_toolTipText = 53;
    private static final int PROPERTY_visible = 54;
    private static final int PROPERTY_width = 55;

    // Property array 
    /*lazy PropertyDescriptor*/
    private static PropertyDescriptor[] getPdescriptor(){
        PropertyDescriptor[] properties = new PropertyDescriptor[56];
    
        try {
            properties[PROPERTY_alignmentY] = new PropertyDescriptor ( "alignmentY", com.jimrolf.functionfield.FunctionField.class, "getAlignmentY", "setAlignmentY" ); // NOI18N
            properties[PROPERTY_ancestorListeners] = new PropertyDescriptor ( "ancestorListeners", com.jimrolf.functionfield.FunctionField.class, "getAncestorListeners", null ); // NOI18N
            properties[PROPERTY_background] = new PropertyDescriptor ( "background", com.jimrolf.functionfield.FunctionField.class, "getBackground", "setBackground" ); // NOI18N
            properties[PROPERTY_backgroundColor] = new PropertyDescriptor ( "backgroundColor", com.jimrolf.functionfield.FunctionField.class, "getBackgroundColor", "setBackgroundColor" ); // NOI18N
            properties[PROPERTY_backgroundSet] = new PropertyDescriptor ( "backgroundSet", com.jimrolf.functionfield.FunctionField.class, "isBackgroundSet", null ); // NOI18N
            properties[PROPERTY_border] = new PropertyDescriptor ( "border", com.jimrolf.functionfield.FunctionField.class, "getBorder", "setBorder" ); // NOI18N
            properties[PROPERTY_bounds] = new PropertyDescriptor ( "bounds", com.jimrolf.functionfield.FunctionField.class, "getBounds", "setBounds" ); // NOI18N
            properties[PROPERTY_caret] = new PropertyDescriptor ( "caret", com.jimrolf.functionfield.FunctionField.class, "getCaret", "setCaret" ); // NOI18N
            properties[PROPERTY_caretColor] = new PropertyDescriptor ( "caretColor", com.jimrolf.functionfield.FunctionField.class, "getCaretColor", "setCaretColor" ); // NOI18N
            properties[PROPERTY_caretPosition] = new PropertyDescriptor ( "caretPosition", com.jimrolf.functionfield.FunctionField.class, "getCaretPosition", "setCaretPosition" ); // NOI18N
            properties[PROPERTY_complexFunc] = new PropertyDescriptor ( "complexFunc", com.jimrolf.functionfield.FunctionField.class, "isComplexFunc", "setComplexFunc" ); // NOI18N
            properties[PROPERTY_displayable] = new PropertyDescriptor ( "displayable", com.jimrolf.functionfield.FunctionField.class, "isDisplayable", null ); // NOI18N
            properties[PROPERTY_doubleBuffered] = new PropertyDescriptor ( "doubleBuffered", com.jimrolf.functionfield.FunctionField.class, "isDoubleBuffered", "setDoubleBuffered" ); // NOI18N
            properties[PROPERTY_dragEnabled] = new PropertyDescriptor ( "dragEnabled", com.jimrolf.functionfield.FunctionField.class, "getDragEnabled", "setDragEnabled" ); // NOI18N
            properties[PROPERTY_editable] = new PropertyDescriptor ( "editable", com.jimrolf.functionfield.FunctionField.class, "isEditable", "setEditable" ); // NOI18N
            properties[PROPERTY_enabled] = new PropertyDescriptor ( "enabled", com.jimrolf.functionfield.FunctionField.class, "isEnabled", "setEnabled" ); // NOI18N
            properties[PROPERTY_errorColor] = new PropertyDescriptor ( "errorColor", com.jimrolf.functionfield.FunctionField.class, "getErrorColor", "setErrorColor" ); // NOI18N
            properties[PROPERTY_focusable] = new PropertyDescriptor ( "focusable", com.jimrolf.functionfield.FunctionField.class, "isFocusable", "setFocusable" ); // NOI18N
            properties[PROPERTY_focusCycleRoot] = new PropertyDescriptor ( "focusCycleRoot", com.jimrolf.functionfield.FunctionField.class, "isFocusCycleRoot", "setFocusCycleRoot" ); // NOI18N
            properties[PROPERTY_focusOwner] = new PropertyDescriptor ( "focusOwner", com.jimrolf.functionfield.FunctionField.class, "isFocusOwner", null ); // NOI18N
            properties[PROPERTY_focusTraversable] = new PropertyDescriptor ( "focusTraversable", com.jimrolf.functionfield.FunctionField.class, "isFocusTraversable", null ); // NOI18N
            properties[PROPERTY_focusTraversalPolicy] = new PropertyDescriptor ( "focusTraversalPolicy", com.jimrolf.functionfield.FunctionField.class, "getFocusTraversalPolicy", "setFocusTraversalPolicy" ); // NOI18N
            properties[PROPERTY_focusTraversalPolicyProvider] = new PropertyDescriptor ( "focusTraversalPolicyProvider", com.jimrolf.functionfield.FunctionField.class, "isFocusTraversalPolicyProvider", "setFocusTraversalPolicyProvider" ); // NOI18N
            properties[PROPERTY_focusTraversalPolicySet] = new PropertyDescriptor ( "focusTraversalPolicySet", com.jimrolf.functionfield.FunctionField.class, "isFocusTraversalPolicySet", null ); // NOI18N
            properties[PROPERTY_font] = new PropertyDescriptor ( "font", com.jimrolf.functionfield.FunctionField.class, "getFont", "setFont" ); // NOI18N
            properties[PROPERTY_foreground] = new PropertyDescriptor ( "foreground", com.jimrolf.functionfield.FunctionField.class, "getForeground", "setForeground" ); // NOI18N
            properties[PROPERTY_height] = new PropertyDescriptor ( "height", com.jimrolf.functionfield.FunctionField.class, "getHeight", null ); // NOI18N
            properties[PROPERTY_highlighter] = new PropertyDescriptor ( "highlighter", com.jimrolf.functionfield.FunctionField.class, "getHighlighter", "setHighlighter" ); // NOI18N
            properties[PROPERTY_horizontalAlignment] = new PropertyDescriptor ( "horizontalAlignment", com.jimrolf.functionfield.FunctionField.class, "getHorizontalAlignment", "setHorizontalAlignment" ); // NOI18N
            properties[PROPERTY_horizontalVisibility] = new PropertyDescriptor ( "horizontalVisibility", com.jimrolf.functionfield.FunctionField.class, "getHorizontalVisibility", null ); // NOI18N
            properties[PROPERTY_ignoreRepaint] = new PropertyDescriptor ( "ignoreRepaint", com.jimrolf.functionfield.FunctionField.class, "getIgnoreRepaint", "setIgnoreRepaint" ); // NOI18N
            properties[PROPERTY_insets] = new PropertyDescriptor ( "insets", com.jimrolf.functionfield.FunctionField.class, "getInsets", null ); // NOI18N
            properties[PROPERTY_layout] = new PropertyDescriptor ( "layout", com.jimrolf.functionfield.FunctionField.class, "getLayout", "setLayout" ); // NOI18N
            properties[PROPERTY_lightweight] = new PropertyDescriptor ( "lightweight", com.jimrolf.functionfield.FunctionField.class, "isLightweight", null ); // NOI18N
            properties[PROPERTY_location] = new PropertyDescriptor ( "location", com.jimrolf.functionfield.FunctionField.class, "getLocation", "setLocation" ); // NOI18N
            properties[PROPERTY_managingFocus] = new PropertyDescriptor ( "managingFocus", com.jimrolf.functionfield.FunctionField.class, "isManagingFocus", null ); // NOI18N
            properties[PROPERTY_margin] = new PropertyDescriptor ( "margin", com.jimrolf.functionfield.FunctionField.class, "getMargin", "setMargin" ); // NOI18N
            properties[PROPERTY_maximumSize] = new PropertyDescriptor ( "maximumSize", com.jimrolf.functionfield.FunctionField.class, "getMaximumSize", "setMaximumSize" ); // NOI18N
            properties[PROPERTY_maximumSizeSet] = new PropertyDescriptor ( "maximumSizeSet", com.jimrolf.functionfield.FunctionField.class, "isMaximumSizeSet", null ); // NOI18N
            properties[PROPERTY_minimumSize] = new PropertyDescriptor ( "minimumSize", com.jimrolf.functionfield.FunctionField.class, "getMinimumSize", "setMinimumSize" ); // NOI18N
            properties[PROPERTY_name] = new PropertyDescriptor ( "name", com.jimrolf.functionfield.FunctionField.class, "getName", "setName" ); // NOI18N
            properties[PROPERTY_nextFocusableComponent] = new PropertyDescriptor ( "nextFocusableComponent", com.jimrolf.functionfield.FunctionField.class, "getNextFocusableComponent", "setNextFocusableComponent" ); // NOI18N
            properties[PROPERTY_opaque] = new PropertyDescriptor ( "opaque", com.jimrolf.functionfield.FunctionField.class, "isOpaque", "setOpaque" ); // NOI18N
            properties[PROPERTY_preferredSize] = new PropertyDescriptor ( "preferredSize", com.jimrolf.functionfield.FunctionField.class, "getPreferredSize", "setPreferredSize" ); // NOI18N
            properties[PROPERTY_requestFocusEnabled] = new PropertyDescriptor ( "requestFocusEnabled", com.jimrolf.functionfield.FunctionField.class, "isRequestFocusEnabled", "setRequestFocusEnabled" ); // NOI18N
            properties[PROPERTY_scrollOffset] = new PropertyDescriptor ( "scrollOffset", com.jimrolf.functionfield.FunctionField.class, "getScrollOffset", "setScrollOffset" ); // NOI18N
            properties[PROPERTY_selectedText] = new PropertyDescriptor ( "selectedText", com.jimrolf.functionfield.FunctionField.class, "getSelectedText", null ); // NOI18N
            properties[PROPERTY_selectedTextColor] = new PropertyDescriptor ( "selectedTextColor", com.jimrolf.functionfield.FunctionField.class, "getSelectedTextColor", "setSelectedTextColor" ); // NOI18N
            properties[PROPERTY_selectionColor] = new PropertyDescriptor ( "selectionColor", com.jimrolf.functionfield.FunctionField.class, "getSelectionColor", "setSelectionColor" ); // NOI18N
            properties[PROPERTY_selectionEnd] = new PropertyDescriptor ( "selectionEnd", com.jimrolf.functionfield.FunctionField.class, "getSelectionEnd", "setSelectionEnd" ); // NOI18N
            properties[PROPERTY_selectionStart] = new PropertyDescriptor ( "selectionStart", com.jimrolf.functionfield.FunctionField.class, "getSelectionStart", "setSelectionStart" ); // NOI18N
            properties[PROPERTY_size] = new PropertyDescriptor ( "size", com.jimrolf.functionfield.FunctionField.class, "getSize", "setSize" ); // NOI18N
            properties[PROPERTY_text] = new PropertyDescriptor ( "text", com.jimrolf.functionfield.FunctionField.class, "getText", "setText" ); // NOI18N
            properties[PROPERTY_toolTipText] = new PropertyDescriptor ( "toolTipText", com.jimrolf.functionfield.FunctionField.class, "getToolTipText", "setToolTipText" ); // NOI18N
            properties[PROPERTY_visible] = new PropertyDescriptor ( "visible", com.jimrolf.functionfield.FunctionField.class, "isVisible", "setVisible" ); // NOI18N
            properties[PROPERTY_width] = new PropertyDescriptor ( "width", com.jimrolf.functionfield.FunctionField.class, "getWidth", null ); // NOI18N
        }
        catch(IntrospectionException e) {
            e.printStackTrace();
        }//GEN-HEADEREND:Properties
        
        // Here you can add code for customizing the properties array.
        
        return properties;     }//GEN-LAST:Properties
    
    // EventSet identifiers//GEN-FIRST:Events
    private static final int EVENT_actionListener = 0;
    private static final int EVENT_ancestorListener = 1;
    private static final int EVENT_caretListener = 2;
    private static final int EVENT_componentListener = 3;
    private static final int EVENT_containerListener = 4;
    private static final int EVENT_focusListener = 5;
    private static final int EVENT_hierarchyBoundsListener = 6;
    private static final int EVENT_hierarchyListener = 7;
    private static final int EVENT_inputMethodListener = 8;
    private static final int EVENT_keyListener = 9;
    private static final int EVENT_mouseListener = 10;
    private static final int EVENT_mouseMotionListener = 11;
    private static final int EVENT_mouseWheelListener = 12;
    private static final int EVENT_propertyChangeListener = 13;
    private static final int EVENT_vetoableChangeListener = 14;

    // EventSet array
    /*lazy EventSetDescriptor*/
    private static EventSetDescriptor[] getEdescriptor(){
        EventSetDescriptor[] eventSets = new EventSetDescriptor[15];
    
        try {
            eventSets[EVENT_actionListener] = new EventSetDescriptor ( com.jimrolf.functionfield.FunctionField.class, "actionListener", java.awt.event.ActionListener.class, new String[] {"actionPerformed"}, "addActionListener", "removeActionListener" ); // NOI18N
            eventSets[EVENT_ancestorListener] = new EventSetDescriptor ( com.jimrolf.functionfield.FunctionField.class, "ancestorListener", javax.swing.event.AncestorListener.class, new String[] {"ancestorAdded", "ancestorRemoved", "ancestorMoved"}, "addAncestorListener", "removeAncestorListener" ); // NOI18N
            eventSets[EVENT_caretListener] = new EventSetDescriptor ( com.jimrolf.functionfield.FunctionField.class, "caretListener", javax.swing.event.CaretListener.class, new String[] {"caretUpdate"}, "addCaretListener", "removeCaretListener" ); // NOI18N
            eventSets[EVENT_componentListener] = new EventSetDescriptor ( com.jimrolf.functionfield.FunctionField.class, "componentListener", java.awt.event.ComponentListener.class, new String[] {"componentResized", "componentMoved", "componentShown", "componentHidden"}, "addComponentListener", "removeComponentListener" ); // NOI18N
            eventSets[EVENT_containerListener] = new EventSetDescriptor ( com.jimrolf.functionfield.FunctionField.class, "containerListener", java.awt.event.ContainerListener.class, new String[] {"componentAdded", "componentRemoved"}, "addContainerListener", "removeContainerListener" ); // NOI18N
            eventSets[EVENT_focusListener] = new EventSetDescriptor ( com.jimrolf.functionfield.FunctionField.class, "focusListener", java.awt.event.FocusListener.class, new String[] {"focusGained", "focusLost"}, "addFocusListener", "removeFocusListener" ); // NOI18N
            eventSets[EVENT_hierarchyBoundsListener] = new EventSetDescriptor ( com.jimrolf.functionfield.FunctionField.class, "hierarchyBoundsListener", java.awt.event.HierarchyBoundsListener.class, new String[] {"ancestorMoved", "ancestorResized"}, "addHierarchyBoundsListener", "removeHierarchyBoundsListener" ); // NOI18N
            eventSets[EVENT_hierarchyListener] = new EventSetDescriptor ( com.jimrolf.functionfield.FunctionField.class, "hierarchyListener", java.awt.event.HierarchyListener.class, new String[] {"hierarchyChanged"}, "addHierarchyListener", "removeHierarchyListener" ); // NOI18N
            eventSets[EVENT_inputMethodListener] = new EventSetDescriptor ( com.jimrolf.functionfield.FunctionField.class, "inputMethodListener", java.awt.event.InputMethodListener.class, new String[] {"inputMethodTextChanged", "caretPositionChanged"}, "addInputMethodListener", "removeInputMethodListener" ); // NOI18N
            eventSets[EVENT_keyListener] = new EventSetDescriptor ( com.jimrolf.functionfield.FunctionField.class, "keyListener", java.awt.event.KeyListener.class, new String[] {"keyTyped", "keyPressed", "keyReleased"}, "addKeyListener", "removeKeyListener" ); // NOI18N
            eventSets[EVENT_mouseListener] = new EventSetDescriptor ( com.jimrolf.functionfield.FunctionField.class, "mouseListener", java.awt.event.MouseListener.class, new String[] {"mouseClicked", "mousePressed", "mouseReleased", "mouseEntered", "mouseExited"}, "addMouseListener", "removeMouseListener" ); // NOI18N
            eventSets[EVENT_mouseMotionListener] = new EventSetDescriptor ( com.jimrolf.functionfield.FunctionField.class, "mouseMotionListener", java.awt.event.MouseMotionListener.class, new String[] {"mouseDragged", "mouseMoved"}, "addMouseMotionListener", "removeMouseMotionListener" ); // NOI18N
            eventSets[EVENT_mouseWheelListener] = new EventSetDescriptor ( com.jimrolf.functionfield.FunctionField.class, "mouseWheelListener", java.awt.event.MouseWheelListener.class, new String[] {"mouseWheelMoved"}, "addMouseWheelListener", "removeMouseWheelListener" ); // NOI18N
            eventSets[EVENT_propertyChangeListener] = new EventSetDescriptor ( com.jimrolf.functionfield.FunctionField.class, "propertyChangeListener", java.beans.PropertyChangeListener.class, new String[] {"propertyChange"}, "addPropertyChangeListener", "removePropertyChangeListener" ); // NOI18N
            eventSets[EVENT_vetoableChangeListener] = new EventSetDescriptor ( com.jimrolf.functionfield.FunctionField.class, "vetoableChangeListener", java.beans.VetoableChangeListener.class, new String[] {"vetoableChange"}, "addVetoableChangeListener", "removeVetoableChangeListener" ); // NOI18N
        }
        catch(IntrospectionException e) {
            e.printStackTrace();
        }//GEN-HEADEREND:Events
        
        // Here you can add code for customizing the event sets array.
        
        return eventSets;     }//GEN-LAST:Events
    
    // Method identifiers//GEN-FIRST:Methods
    private static final int METHOD_derivHasError0 = 0;
    private static final int METHOD_hasError1 = 1;

    // Method array 
    /*lazy MethodDescriptor*/
    private static MethodDescriptor[] getMdescriptor(){
        MethodDescriptor[] methods = new MethodDescriptor[2];
    
        try {
            methods[METHOD_derivHasError0] = new MethodDescriptor(com.jimrolf.functionfield.FunctionField.class.getMethod("derivHasError", new Class[] {})); // NOI18N
            methods[METHOD_derivHasError0].setDisplayName ( "" );
            methods[METHOD_hasError1] = new MethodDescriptor(com.jimrolf.functionfield.FunctionField.class.getMethod("hasError", new Class[] {})); // NOI18N
            methods[METHOD_hasError1].setDisplayName ( "" );
        }
        catch( Exception e) {}//GEN-HEADEREND:Methods
        
        // Here you can add code for customizing the methods array.
        
        return methods;     }//GEN-LAST:Methods
    
    
    private static final int defaultPropertyIndex = -1;//GEN-BEGIN:Idx
    private static final int defaultEventIndex = -1;//GEN-END:Idx
    
    
//GEN-FIRST:Superclass
    
    // Here you can add code for customizing the Superclass BeanInfo.
    
//GEN-LAST:Superclass
    
    /**
     * Gets the bean's <code>BeanDescriptor</code>s.
     *
     * @return BeanDescriptor describing the editable
     * properties of this bean.  May return null if the
     * information should be obtained by automatic analysis.
     */
    public BeanDescriptor getBeanDescriptor() {
        return getBdescriptor();
    }
    
    /**
     * Gets the bean's <code>PropertyDescriptor</code>s.
     *
     * @return An array of PropertyDescriptors describing the editable
     * properties supported by this bean.  May return null if the
     * information should be obtained by automatic analysis.
     * <p>
     * If a property is indexed, then its entry in the result array will
     * belong to the IndexedPropertyDescriptor subclass of PropertyDescriptor.
     * A client of getPropertyDescriptors can use "instanceof" to check
     * if a given PropertyDescriptor is an IndexedPropertyDescriptor.
     */
    public PropertyDescriptor[] getPropertyDescriptors() {
        return getPdescriptor();
    }
    
    /**
     * Gets the bean's <code>EventSetDescriptor</code>s.
     *
     * @return  An array of EventSetDescriptors describing the kinds of
     * events fired by this bean.  May return null if the information
     * should be obtained by automatic analysis.
     */
    public EventSetDescriptor[] getEventSetDescriptors() {
        return getEdescriptor();
    }
    
    /**
     * Gets the bean's <code>MethodDescriptor</code>s.
     *
     * @return  An array of MethodDescriptors describing the methods
     * implemented by this bean.  May return null if the information
     * should be obtained by automatic analysis.
     */
    public MethodDescriptor[] getMethodDescriptors() {
        return getMdescriptor();
    }
    
    /**
     * A bean may have a "default" property that is the property that will
     * mostly commonly be initially chosen for update by human's who are
     * customizing the bean.
     * @return  Index of default property in the PropertyDescriptor array
     * 		returned by getPropertyDescriptors.
     * <P>	Returns -1 if there is no default property.
     */
    public int getDefaultPropertyIndex() {
        return defaultPropertyIndex;
    }
    
    /**
     * A bean may have a "default" event that is the event that will
     * mostly commonly be used by human's when using the bean.
     * @return Index of default event in the EventSetDescriptor array
     *		returned by getEventSetDescriptors.
     * <P>	Returns -1 if there is no default event.
     */
    public int getDefaultEventIndex() {
        return defaultEventIndex;
    }
}

