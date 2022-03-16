/*
 * FunctionFieldWithWrappingBeanInfo.java
 *
 * Created on October 16, 2007, 11:16 AM
 */

package com.jimrolf.functionfield;

import java.beans.*;

/**
 * @author jimrolf
 */
public class FunctionFieldWithWrappingBeanInfo extends SimpleBeanInfo {
    
    // Bean descriptor//GEN-FIRST:BeanDescriptor
    /*lazy BeanDescriptor*/
    private static BeanDescriptor getBdescriptor(){
        BeanDescriptor beanDescriptor = new BeanDescriptor  ( com.jimrolf.functionfield.FunctionFieldWithWrapping.class , null ); // NOI18N//GEN-HEADEREND:BeanDescriptor
        
        // Here you can add code for customizing the BeanDescriptor.
        
        return beanDescriptor;     }//GEN-LAST:BeanDescriptor
    
    
    // Property identifiers//GEN-FIRST:Properties
    private static final int PROPERTY_accessibleContext = 0;
    private static final int PROPERTY_actionMap = 1;
    private static final int PROPERTY_actions = 2;
    private static final int PROPERTY_alignmentX = 3;
    private static final int PROPERTY_alignmentY = 4;
    private static final int PROPERTY_ancestorListeners = 5;
    private static final int PROPERTY_autoscrolls = 6;
    private static final int PROPERTY_background = 7;
    private static final int PROPERTY_backgroundColor = 8;
    private static final int PROPERTY_backgroundSet = 9;
    private static final int PROPERTY_border = 10;
    private static final int PROPERTY_bounds = 11;
    private static final int PROPERTY_caret = 12;
    private static final int PROPERTY_caretColor = 13;
    private static final int PROPERTY_caretListeners = 14;
    private static final int PROPERTY_caretPosition = 15;
    private static final int PROPERTY_colorModel = 16;
    private static final int PROPERTY_columns = 17;
    private static final int PROPERTY_complexFunc = 18;
    private static final int PROPERTY_component = 19;
    private static final int PROPERTY_componentCount = 20;
    private static final int PROPERTY_componentListeners = 21;
    private static final int PROPERTY_componentOrientation = 22;
    private static final int PROPERTY_componentPopupMenu = 23;
    private static final int PROPERTY_components = 24;
    private static final int PROPERTY_containerListeners = 25;
    private static final int PROPERTY_cursor = 26;
    private static final int PROPERTY_cursorSet = 27;
    private static final int PROPERTY_debugGraphicsOptions = 28;
    private static final int PROPERTY_disabledTextColor = 29;
    private static final int PROPERTY_displayable = 30;
    private static final int PROPERTY_document = 31;
    private static final int PROPERTY_doubleBuffered = 32;
    private static final int PROPERTY_dragEnabled = 33;
    private static final int PROPERTY_dropTarget = 34;
    private static final int PROPERTY_editable = 35;
    private static final int PROPERTY_enabled = 36;
    private static final int PROPERTY_errorColor = 37;
    private static final int PROPERTY_focusable = 38;
    private static final int PROPERTY_focusAccelerator = 39;
    private static final int PROPERTY_focusCycleRoot = 40;
    private static final int PROPERTY_focusCycleRootAncestor = 41;
    private static final int PROPERTY_focusListeners = 42;
    private static final int PROPERTY_focusOwner = 43;
    private static final int PROPERTY_focusTraversable = 44;
    private static final int PROPERTY_focusTraversalKeys = 45;
    private static final int PROPERTY_focusTraversalKeysEnabled = 46;
    private static final int PROPERTY_focusTraversalPolicy = 47;
    private static final int PROPERTY_focusTraversalPolicyProvider = 48;
    private static final int PROPERTY_focusTraversalPolicySet = 49;
    private static final int PROPERTY_font = 50;
    private static final int PROPERTY_fontSet = 51;
    private static final int PROPERTY_foreground = 52;
    private static final int PROPERTY_foregroundSet = 53;
    private static final int PROPERTY_graphics = 54;
    private static final int PROPERTY_graphicsConfiguration = 55;
    private static final int PROPERTY_height = 56;
    private static final int PROPERTY_hierarchyBoundsListeners = 57;
    private static final int PROPERTY_hierarchyListeners = 58;
    private static final int PROPERTY_highlighter = 59;
    private static final int PROPERTY_ignoreRepaint = 60;
    private static final int PROPERTY_inheritsPopupMenu = 61;
    private static final int PROPERTY_inputContext = 62;
    private static final int PROPERTY_inputMap = 63;
    private static final int PROPERTY_inputMethodListeners = 64;
    private static final int PROPERTY_inputMethodRequests = 65;
    private static final int PROPERTY_inputVerifier = 66;
    private static final int PROPERTY_insets = 67;
    private static final int PROPERTY_keyListeners = 68;
    private static final int PROPERTY_keymap = 69;
    private static final int PROPERTY_layout = 70;
    private static final int PROPERTY_lightweight = 71;
    private static final int PROPERTY_lineCount = 72;
    private static final int PROPERTY_lineEndOffset = 73;
    private static final int PROPERTY_lineOfOffset = 74;
    private static final int PROPERTY_lineStartOffset = 75;
    private static final int PROPERTY_lineWrap = 76;
    private static final int PROPERTY_locale = 77;
    private static final int PROPERTY_location = 78;
    private static final int PROPERTY_locationOnScreen = 79;
    private static final int PROPERTY_managingFocus = 80;
    private static final int PROPERTY_margin = 81;
    private static final int PROPERTY_maximumSize = 82;
    private static final int PROPERTY_maximumSizeSet = 83;
    private static final int PROPERTY_minimumSize = 84;
    private static final int PROPERTY_minimumSizeSet = 85;
    private static final int PROPERTY_mouseListeners = 86;
    private static final int PROPERTY_mouseMotionListeners = 87;
    private static final int PROPERTY_mousePosition = 88;
    private static final int PROPERTY_mouseWheelListeners = 89;
    private static final int PROPERTY_name = 90;
    private static final int PROPERTY_navigationFilter = 91;
    private static final int PROPERTY_nextFocusableComponent = 92;
    private static final int PROPERTY_opaque = 93;
    private static final int PROPERTY_optimizedDrawingEnabled = 94;
    private static final int PROPERTY_paintingTile = 95;
    private static final int PROPERTY_parent = 96;
    private static final int PROPERTY_parser = 97;
    private static final int PROPERTY_peer = 98;
    private static final int PROPERTY_preferredScrollableViewportSize = 99;
    private static final int PROPERTY_preferredSize = 100;
    private static final int PROPERTY_preferredSizeSet = 101;
    private static final int PROPERTY_propertyChangeListeners = 102;
    private static final int PROPERTY_registeredKeyStrokes = 103;
    private static final int PROPERTY_requestFocusEnabled = 104;
    private static final int PROPERTY_rootPane = 105;
    private static final int PROPERTY_rows = 106;
    private static final int PROPERTY_scrollableTracksViewportHeight = 107;
    private static final int PROPERTY_scrollableTracksViewportWidth = 108;
    private static final int PROPERTY_selectedText = 109;
    private static final int PROPERTY_selectedTextColor = 110;
    private static final int PROPERTY_selectionColor = 111;
    private static final int PROPERTY_selectionEnd = 112;
    private static final int PROPERTY_selectionStart = 113;
    private static final int PROPERTY_showing = 114;
    private static final int PROPERTY_size = 115;
    private static final int PROPERTY_tabSize = 116;
    private static final int PROPERTY_text = 117;
    private static final int PROPERTY_toolkit = 118;
    private static final int PROPERTY_toolTipText = 119;
    private static final int PROPERTY_topLevelAncestor = 120;
    private static final int PROPERTY_transferHandler = 121;
    private static final int PROPERTY_treeLock = 122;
    private static final int PROPERTY_UI = 123;
    private static final int PROPERTY_UIClassID = 124;
    private static final int PROPERTY_valid = 125;
    private static final int PROPERTY_validateRoot = 126;
    private static final int PROPERTY_verifyInputWhenFocusTarget = 127;
    private static final int PROPERTY_vetoableChangeListeners = 128;
    private static final int PROPERTY_visible = 129;
    private static final int PROPERTY_visibleRect = 130;
    private static final int PROPERTY_width = 131;
    private static final int PROPERTY_wrapStyleWord = 132;
    private static final int PROPERTY_x = 133;
    private static final int PROPERTY_y = 134;

    // Property array 
    /*lazy PropertyDescriptor*/
    private static PropertyDescriptor[] getPdescriptor(){
        PropertyDescriptor[] properties = new PropertyDescriptor[135];
    
        try {
            properties[PROPERTY_accessibleContext] = new PropertyDescriptor ( "accessibleContext", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getAccessibleContext", null ); // NOI18N
            properties[PROPERTY_actionMap] = new PropertyDescriptor ( "actionMap", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getActionMap", "setActionMap" ); // NOI18N
            properties[PROPERTY_actions] = new PropertyDescriptor ( "actions", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getActions", null ); // NOI18N
            properties[PROPERTY_alignmentX] = new PropertyDescriptor ( "alignmentX", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getAlignmentX", "setAlignmentX" ); // NOI18N
            properties[PROPERTY_alignmentY] = new PropertyDescriptor ( "alignmentY", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getAlignmentY", "setAlignmentY" ); // NOI18N
            properties[PROPERTY_ancestorListeners] = new PropertyDescriptor ( "ancestorListeners", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getAncestorListeners", null ); // NOI18N
            properties[PROPERTY_autoscrolls] = new PropertyDescriptor ( "autoscrolls", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getAutoscrolls", "setAutoscrolls" ); // NOI18N
            properties[PROPERTY_background] = new PropertyDescriptor ( "background", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getBackground", "setBackground" ); // NOI18N
            properties[PROPERTY_backgroundColor] = new PropertyDescriptor ( "backgroundColor", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getBackgroundColor", "setBackgroundColor" ); // NOI18N
            properties[PROPERTY_backgroundSet] = new PropertyDescriptor ( "backgroundSet", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "isBackgroundSet", null ); // NOI18N
            properties[PROPERTY_border] = new PropertyDescriptor ( "border", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getBorder", "setBorder" ); // NOI18N
            properties[PROPERTY_bounds] = new PropertyDescriptor ( "bounds", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getBounds", "setBounds" ); // NOI18N
            properties[PROPERTY_caret] = new PropertyDescriptor ( "caret", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getCaret", "setCaret" ); // NOI18N
            properties[PROPERTY_caretColor] = new PropertyDescriptor ( "caretColor", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getCaretColor", "setCaretColor" ); // NOI18N
            properties[PROPERTY_caretListeners] = new PropertyDescriptor ( "caretListeners", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getCaretListeners", null ); // NOI18N
            properties[PROPERTY_caretPosition] = new PropertyDescriptor ( "caretPosition", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getCaretPosition", "setCaretPosition" ); // NOI18N
            properties[PROPERTY_colorModel] = new PropertyDescriptor ( "colorModel", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getColorModel", null ); // NOI18N
            properties[PROPERTY_columns] = new PropertyDescriptor ( "columns", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getColumns", "setColumns" ); // NOI18N
            properties[PROPERTY_complexFunc] = new PropertyDescriptor ( "complexFunc", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "isComplexFunc", "setComplexFunc" ); // NOI18N
            properties[PROPERTY_component] = new IndexedPropertyDescriptor ( "component", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, null, null, "getComponent", null ); // NOI18N
            properties[PROPERTY_componentCount] = new PropertyDescriptor ( "componentCount", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getComponentCount", null ); // NOI18N
            properties[PROPERTY_componentListeners] = new PropertyDescriptor ( "componentListeners", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getComponentListeners", null ); // NOI18N
            properties[PROPERTY_componentOrientation] = new PropertyDescriptor ( "componentOrientation", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getComponentOrientation", "setComponentOrientation" ); // NOI18N
            properties[PROPERTY_componentPopupMenu] = new PropertyDescriptor ( "componentPopupMenu", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getComponentPopupMenu", "setComponentPopupMenu" ); // NOI18N
            properties[PROPERTY_components] = new PropertyDescriptor ( "components", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getComponents", null ); // NOI18N
            properties[PROPERTY_containerListeners] = new PropertyDescriptor ( "containerListeners", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getContainerListeners", null ); // NOI18N
            properties[PROPERTY_cursor] = new PropertyDescriptor ( "cursor", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getCursor", "setCursor" ); // NOI18N
            properties[PROPERTY_cursorSet] = new PropertyDescriptor ( "cursorSet", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "isCursorSet", null ); // NOI18N
            properties[PROPERTY_debugGraphicsOptions] = new PropertyDescriptor ( "debugGraphicsOptions", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getDebugGraphicsOptions", "setDebugGraphicsOptions" ); // NOI18N
            properties[PROPERTY_disabledTextColor] = new PropertyDescriptor ( "disabledTextColor", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getDisabledTextColor", "setDisabledTextColor" ); // NOI18N
            properties[PROPERTY_displayable] = new PropertyDescriptor ( "displayable", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "isDisplayable", null ); // NOI18N
            properties[PROPERTY_document] = new PropertyDescriptor ( "document", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getDocument", "setDocument" ); // NOI18N
            properties[PROPERTY_doubleBuffered] = new PropertyDescriptor ( "doubleBuffered", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "isDoubleBuffered", "setDoubleBuffered" ); // NOI18N
            properties[PROPERTY_dragEnabled] = new PropertyDescriptor ( "dragEnabled", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getDragEnabled", "setDragEnabled" ); // NOI18N
            properties[PROPERTY_dropTarget] = new PropertyDescriptor ( "dropTarget", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getDropTarget", "setDropTarget" ); // NOI18N
            properties[PROPERTY_editable] = new PropertyDescriptor ( "editable", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "isEditable", "setEditable" ); // NOI18N
            properties[PROPERTY_enabled] = new PropertyDescriptor ( "enabled", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "isEnabled", "setEnabled" ); // NOI18N
            properties[PROPERTY_errorColor] = new PropertyDescriptor ( "errorColor", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getErrorColor", "setErrorColor" ); // NOI18N
            properties[PROPERTY_focusable] = new PropertyDescriptor ( "focusable", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "isFocusable", "setFocusable" ); // NOI18N
            properties[PROPERTY_focusAccelerator] = new PropertyDescriptor ( "focusAccelerator", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getFocusAccelerator", "setFocusAccelerator" ); // NOI18N
            properties[PROPERTY_focusCycleRoot] = new PropertyDescriptor ( "focusCycleRoot", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "isFocusCycleRoot", "setFocusCycleRoot" ); // NOI18N
            properties[PROPERTY_focusCycleRootAncestor] = new PropertyDescriptor ( "focusCycleRootAncestor", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getFocusCycleRootAncestor", null ); // NOI18N
            properties[PROPERTY_focusListeners] = new PropertyDescriptor ( "focusListeners", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getFocusListeners", null ); // NOI18N
            properties[PROPERTY_focusOwner] = new PropertyDescriptor ( "focusOwner", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "isFocusOwner", null ); // NOI18N
            properties[PROPERTY_focusTraversable] = new PropertyDescriptor ( "focusTraversable", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "isFocusTraversable", null ); // NOI18N
            properties[PROPERTY_focusTraversalKeys] = new IndexedPropertyDescriptor ( "focusTraversalKeys", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, null, null, null, "setFocusTraversalKeys" ); // NOI18N
            properties[PROPERTY_focusTraversalKeysEnabled] = new PropertyDescriptor ( "focusTraversalKeysEnabled", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getFocusTraversalKeysEnabled", "setFocusTraversalKeysEnabled" ); // NOI18N
            properties[PROPERTY_focusTraversalPolicy] = new PropertyDescriptor ( "focusTraversalPolicy", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getFocusTraversalPolicy", "setFocusTraversalPolicy" ); // NOI18N
            properties[PROPERTY_focusTraversalPolicyProvider] = new PropertyDescriptor ( "focusTraversalPolicyProvider", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "isFocusTraversalPolicyProvider", "setFocusTraversalPolicyProvider" ); // NOI18N
            properties[PROPERTY_focusTraversalPolicySet] = new PropertyDescriptor ( "focusTraversalPolicySet", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "isFocusTraversalPolicySet", null ); // NOI18N
            properties[PROPERTY_font] = new PropertyDescriptor ( "font", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getFont", "setFont" ); // NOI18N
            properties[PROPERTY_fontSet] = new PropertyDescriptor ( "fontSet", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "isFontSet", null ); // NOI18N
            properties[PROPERTY_foreground] = new PropertyDescriptor ( "foreground", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getForeground", "setForeground" ); // NOI18N
            properties[PROPERTY_foregroundSet] = new PropertyDescriptor ( "foregroundSet", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "isForegroundSet", null ); // NOI18N
            properties[PROPERTY_graphics] = new PropertyDescriptor ( "graphics", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getGraphics", null ); // NOI18N
            properties[PROPERTY_graphicsConfiguration] = new PropertyDescriptor ( "graphicsConfiguration", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getGraphicsConfiguration", null ); // NOI18N
            properties[PROPERTY_height] = new PropertyDescriptor ( "height", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getHeight", null ); // NOI18N
            properties[PROPERTY_hierarchyBoundsListeners] = new PropertyDescriptor ( "hierarchyBoundsListeners", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getHierarchyBoundsListeners", null ); // NOI18N
            properties[PROPERTY_hierarchyListeners] = new PropertyDescriptor ( "hierarchyListeners", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getHierarchyListeners", null ); // NOI18N
            properties[PROPERTY_highlighter] = new PropertyDescriptor ( "highlighter", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getHighlighter", "setHighlighter" ); // NOI18N
            properties[PROPERTY_ignoreRepaint] = new PropertyDescriptor ( "ignoreRepaint", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getIgnoreRepaint", "setIgnoreRepaint" ); // NOI18N
            properties[PROPERTY_inheritsPopupMenu] = new PropertyDescriptor ( "inheritsPopupMenu", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getInheritsPopupMenu", "setInheritsPopupMenu" ); // NOI18N
            properties[PROPERTY_inputContext] = new PropertyDescriptor ( "inputContext", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getInputContext", null ); // NOI18N
            properties[PROPERTY_inputMap] = new PropertyDescriptor ( "inputMap", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getInputMap", null ); // NOI18N
            properties[PROPERTY_inputMethodListeners] = new PropertyDescriptor ( "inputMethodListeners", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getInputMethodListeners", null ); // NOI18N
            properties[PROPERTY_inputMethodRequests] = new PropertyDescriptor ( "inputMethodRequests", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getInputMethodRequests", null ); // NOI18N
            properties[PROPERTY_inputVerifier] = new PropertyDescriptor ( "inputVerifier", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getInputVerifier", "setInputVerifier" ); // NOI18N
            properties[PROPERTY_insets] = new PropertyDescriptor ( "insets", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getInsets", null ); // NOI18N
            properties[PROPERTY_keyListeners] = new PropertyDescriptor ( "keyListeners", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getKeyListeners", null ); // NOI18N
            properties[PROPERTY_keymap] = new PropertyDescriptor ( "keymap", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getKeymap", "setKeymap" ); // NOI18N
            properties[PROPERTY_layout] = new PropertyDescriptor ( "layout", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getLayout", "setLayout" ); // NOI18N
            properties[PROPERTY_lightweight] = new PropertyDescriptor ( "lightweight", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "isLightweight", null ); // NOI18N
            properties[PROPERTY_lineCount] = new PropertyDescriptor ( "lineCount", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getLineCount", null ); // NOI18N
            properties[PROPERTY_lineEndOffset] = new IndexedPropertyDescriptor ( "lineEndOffset", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, null, null, "getLineEndOffset", null ); // NOI18N
            properties[PROPERTY_lineOfOffset] = new IndexedPropertyDescriptor ( "lineOfOffset", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, null, null, "getLineOfOffset", null ); // NOI18N
            properties[PROPERTY_lineStartOffset] = new IndexedPropertyDescriptor ( "lineStartOffset", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, null, null, "getLineStartOffset", null ); // NOI18N
            properties[PROPERTY_lineWrap] = new PropertyDescriptor ( "lineWrap", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getLineWrap", "setLineWrap" ); // NOI18N
            properties[PROPERTY_locale] = new PropertyDescriptor ( "locale", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getLocale", "setLocale" ); // NOI18N
            properties[PROPERTY_location] = new PropertyDescriptor ( "location", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getLocation", "setLocation" ); // NOI18N
            properties[PROPERTY_locationOnScreen] = new PropertyDescriptor ( "locationOnScreen", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getLocationOnScreen", null ); // NOI18N
            properties[PROPERTY_managingFocus] = new PropertyDescriptor ( "managingFocus", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "isManagingFocus", null ); // NOI18N
            properties[PROPERTY_margin] = new PropertyDescriptor ( "margin", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getMargin", "setMargin" ); // NOI18N
            properties[PROPERTY_maximumSize] = new PropertyDescriptor ( "maximumSize", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getMaximumSize", "setMaximumSize" ); // NOI18N
            properties[PROPERTY_maximumSizeSet] = new PropertyDescriptor ( "maximumSizeSet", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "isMaximumSizeSet", null ); // NOI18N
            properties[PROPERTY_minimumSize] = new PropertyDescriptor ( "minimumSize", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getMinimumSize", "setMinimumSize" ); // NOI18N
            properties[PROPERTY_minimumSizeSet] = new PropertyDescriptor ( "minimumSizeSet", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "isMinimumSizeSet", null ); // NOI18N
            properties[PROPERTY_mouseListeners] = new PropertyDescriptor ( "mouseListeners", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getMouseListeners", null ); // NOI18N
            properties[PROPERTY_mouseMotionListeners] = new PropertyDescriptor ( "mouseMotionListeners", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getMouseMotionListeners", null ); // NOI18N
            properties[PROPERTY_mousePosition] = new PropertyDescriptor ( "mousePosition", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getMousePosition", null ); // NOI18N
            properties[PROPERTY_mouseWheelListeners] = new PropertyDescriptor ( "mouseWheelListeners", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getMouseWheelListeners", null ); // NOI18N
            properties[PROPERTY_name] = new PropertyDescriptor ( "name", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getName", "setName" ); // NOI18N
            properties[PROPERTY_navigationFilter] = new PropertyDescriptor ( "navigationFilter", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getNavigationFilter", "setNavigationFilter" ); // NOI18N
            properties[PROPERTY_nextFocusableComponent] = new PropertyDescriptor ( "nextFocusableComponent", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getNextFocusableComponent", "setNextFocusableComponent" ); // NOI18N
            properties[PROPERTY_opaque] = new PropertyDescriptor ( "opaque", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "isOpaque", "setOpaque" ); // NOI18N
            properties[PROPERTY_optimizedDrawingEnabled] = new PropertyDescriptor ( "optimizedDrawingEnabled", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "isOptimizedDrawingEnabled", null ); // NOI18N
            properties[PROPERTY_paintingTile] = new PropertyDescriptor ( "paintingTile", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "isPaintingTile", null ); // NOI18N
            properties[PROPERTY_parent] = new PropertyDescriptor ( "parent", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getParent", null ); // NOI18N
            properties[PROPERTY_parser] = new PropertyDescriptor ( "parser", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getParser", "setParser" ); // NOI18N
            properties[PROPERTY_peer] = new PropertyDescriptor ( "peer", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getPeer", null ); // NOI18N
            properties[PROPERTY_preferredScrollableViewportSize] = new PropertyDescriptor ( "preferredScrollableViewportSize", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getPreferredScrollableViewportSize", null ); // NOI18N
            properties[PROPERTY_preferredSize] = new PropertyDescriptor ( "preferredSize", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getPreferredSize", "setPreferredSize" ); // NOI18N
            properties[PROPERTY_preferredSizeSet] = new PropertyDescriptor ( "preferredSizeSet", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "isPreferredSizeSet", null ); // NOI18N
            properties[PROPERTY_propertyChangeListeners] = new PropertyDescriptor ( "propertyChangeListeners", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getPropertyChangeListeners", null ); // NOI18N
            properties[PROPERTY_registeredKeyStrokes] = new PropertyDescriptor ( "registeredKeyStrokes", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getRegisteredKeyStrokes", null ); // NOI18N
            properties[PROPERTY_requestFocusEnabled] = new PropertyDescriptor ( "requestFocusEnabled", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "isRequestFocusEnabled", "setRequestFocusEnabled" ); // NOI18N
            properties[PROPERTY_rootPane] = new PropertyDescriptor ( "rootPane", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getRootPane", null ); // NOI18N
            properties[PROPERTY_rows] = new PropertyDescriptor ( "rows", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getRows", "setRows" ); // NOI18N
            properties[PROPERTY_scrollableTracksViewportHeight] = new PropertyDescriptor ( "scrollableTracksViewportHeight", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getScrollableTracksViewportHeight", null ); // NOI18N
            properties[PROPERTY_scrollableTracksViewportWidth] = new PropertyDescriptor ( "scrollableTracksViewportWidth", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getScrollableTracksViewportWidth", null ); // NOI18N
            properties[PROPERTY_selectedText] = new PropertyDescriptor ( "selectedText", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getSelectedText", null ); // NOI18N
            properties[PROPERTY_selectedTextColor] = new PropertyDescriptor ( "selectedTextColor", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getSelectedTextColor", "setSelectedTextColor" ); // NOI18N
            properties[PROPERTY_selectionColor] = new PropertyDescriptor ( "selectionColor", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getSelectionColor", "setSelectionColor" ); // NOI18N
            properties[PROPERTY_selectionEnd] = new PropertyDescriptor ( "selectionEnd", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getSelectionEnd", "setSelectionEnd" ); // NOI18N
            properties[PROPERTY_selectionStart] = new PropertyDescriptor ( "selectionStart", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getSelectionStart", "setSelectionStart" ); // NOI18N
            properties[PROPERTY_showing] = new PropertyDescriptor ( "showing", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "isShowing", null ); // NOI18N
            properties[PROPERTY_size] = new PropertyDescriptor ( "size", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getSize", "setSize" ); // NOI18N
            properties[PROPERTY_tabSize] = new PropertyDescriptor ( "tabSize", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getTabSize", "setTabSize" ); // NOI18N
            properties[PROPERTY_text] = new PropertyDescriptor ( "text", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getText", "setText" ); // NOI18N
            properties[PROPERTY_toolkit] = new PropertyDescriptor ( "toolkit", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getToolkit", null ); // NOI18N
            properties[PROPERTY_toolTipText] = new PropertyDescriptor ( "toolTipText", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getToolTipText", "setToolTipText" ); // NOI18N
            properties[PROPERTY_topLevelAncestor] = new PropertyDescriptor ( "topLevelAncestor", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getTopLevelAncestor", null ); // NOI18N
            properties[PROPERTY_transferHandler] = new PropertyDescriptor ( "transferHandler", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getTransferHandler", "setTransferHandler" ); // NOI18N
            properties[PROPERTY_treeLock] = new PropertyDescriptor ( "treeLock", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getTreeLock", null ); // NOI18N
            properties[PROPERTY_UI] = new PropertyDescriptor ( "UI", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getUI", "setUI" ); // NOI18N
            properties[PROPERTY_UIClassID] = new PropertyDescriptor ( "UIClassID", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getUIClassID", null ); // NOI18N
            properties[PROPERTY_valid] = new PropertyDescriptor ( "valid", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "isValid", null ); // NOI18N
            properties[PROPERTY_validateRoot] = new PropertyDescriptor ( "validateRoot", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "isValidateRoot", null ); // NOI18N
            properties[PROPERTY_verifyInputWhenFocusTarget] = new PropertyDescriptor ( "verifyInputWhenFocusTarget", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getVerifyInputWhenFocusTarget", "setVerifyInputWhenFocusTarget" ); // NOI18N
            properties[PROPERTY_vetoableChangeListeners] = new PropertyDescriptor ( "vetoableChangeListeners", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getVetoableChangeListeners", null ); // NOI18N
            properties[PROPERTY_visible] = new PropertyDescriptor ( "visible", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "isVisible", "setVisible" ); // NOI18N
            properties[PROPERTY_visibleRect] = new PropertyDescriptor ( "visibleRect", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getVisibleRect", null ); // NOI18N
            properties[PROPERTY_width] = new PropertyDescriptor ( "width", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getWidth", null ); // NOI18N
            properties[PROPERTY_wrapStyleWord] = new PropertyDescriptor ( "wrapStyleWord", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getWrapStyleWord", "setWrapStyleWord" ); // NOI18N
            properties[PROPERTY_x] = new PropertyDescriptor ( "x", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getX", null ); // NOI18N
            properties[PROPERTY_y] = new PropertyDescriptor ( "y", com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "getY", null ); // NOI18N
        }
        catch(IntrospectionException e) {
            e.printStackTrace();
        }//GEN-HEADEREND:Properties
        
        // Here you can add code for customizing the properties array.
        
        return properties;     }//GEN-LAST:Properties
    
    // EventSet identifiers//GEN-FIRST:Events
    private static final int EVENT_ancestorListener = 0;
    private static final int EVENT_caretListener = 1;
    private static final int EVENT_componentListener = 2;
    private static final int EVENT_containerListener = 3;
    private static final int EVENT_focusListener = 4;
    private static final int EVENT_hierarchyBoundsListener = 5;
    private static final int EVENT_hierarchyListener = 6;
    private static final int EVENT_inputMethodListener = 7;
    private static final int EVENT_keyListener = 8;
    private static final int EVENT_mouseListener = 9;
    private static final int EVENT_mouseMotionListener = 10;
    private static final int EVENT_mouseWheelListener = 11;
    private static final int EVENT_propertyChangeListener = 12;
    private static final int EVENT_vetoableChangeListener = 13;

    // EventSet array
    /*lazy EventSetDescriptor*/
    private static EventSetDescriptor[] getEdescriptor(){
        EventSetDescriptor[] eventSets = new EventSetDescriptor[14];
    
        try {
            eventSets[EVENT_ancestorListener] = new EventSetDescriptor ( com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "ancestorListener", javax.swing.event.AncestorListener.class, new String[] {"ancestorAdded", "ancestorRemoved", "ancestorMoved"}, "addAncestorListener", "removeAncestorListener" ); // NOI18N
            eventSets[EVENT_caretListener] = new EventSetDescriptor ( com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "caretListener", javax.swing.event.CaretListener.class, new String[] {"caretUpdate"}, "addCaretListener", "removeCaretListener" ); // NOI18N
            eventSets[EVENT_componentListener] = new EventSetDescriptor ( com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "componentListener", java.awt.event.ComponentListener.class, new String[] {"componentResized", "componentMoved", "componentShown", "componentHidden"}, "addComponentListener", "removeComponentListener" ); // NOI18N
            eventSets[EVENT_containerListener] = new EventSetDescriptor ( com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "containerListener", java.awt.event.ContainerListener.class, new String[] {"componentAdded", "componentRemoved"}, "addContainerListener", "removeContainerListener" ); // NOI18N
            eventSets[EVENT_focusListener] = new EventSetDescriptor ( com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "focusListener", java.awt.event.FocusListener.class, new String[] {"focusGained", "focusLost"}, "addFocusListener", "removeFocusListener" ); // NOI18N
            eventSets[EVENT_hierarchyBoundsListener] = new EventSetDescriptor ( com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "hierarchyBoundsListener", java.awt.event.HierarchyBoundsListener.class, new String[] {"ancestorMoved", "ancestorResized"}, "addHierarchyBoundsListener", "removeHierarchyBoundsListener" ); // NOI18N
            eventSets[EVENT_hierarchyListener] = new EventSetDescriptor ( com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "hierarchyListener", java.awt.event.HierarchyListener.class, new String[] {"hierarchyChanged"}, "addHierarchyListener", "removeHierarchyListener" ); // NOI18N
            eventSets[EVENT_inputMethodListener] = new EventSetDescriptor ( com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "inputMethodListener", java.awt.event.InputMethodListener.class, new String[] {"inputMethodTextChanged", "caretPositionChanged"}, "addInputMethodListener", "removeInputMethodListener" ); // NOI18N
            eventSets[EVENT_keyListener] = new EventSetDescriptor ( com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "keyListener", java.awt.event.KeyListener.class, new String[] {"keyTyped", "keyPressed", "keyReleased"}, "addKeyListener", "removeKeyListener" ); // NOI18N
            eventSets[EVENT_mouseListener] = new EventSetDescriptor ( com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "mouseListener", java.awt.event.MouseListener.class, new String[] {"mouseClicked", "mousePressed", "mouseReleased", "mouseEntered", "mouseExited"}, "addMouseListener", "removeMouseListener" ); // NOI18N
            eventSets[EVENT_mouseMotionListener] = new EventSetDescriptor ( com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "mouseMotionListener", java.awt.event.MouseMotionListener.class, new String[] {"mouseDragged", "mouseMoved"}, "addMouseMotionListener", "removeMouseMotionListener" ); // NOI18N
            eventSets[EVENT_mouseWheelListener] = new EventSetDescriptor ( com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "mouseWheelListener", java.awt.event.MouseWheelListener.class, new String[] {"mouseWheelMoved"}, "addMouseWheelListener", "removeMouseWheelListener" ); // NOI18N
            eventSets[EVENT_propertyChangeListener] = new EventSetDescriptor ( com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "propertyChangeListener", java.beans.PropertyChangeListener.class, new String[] {"propertyChange"}, "addPropertyChangeListener", "removePropertyChangeListener" ); // NOI18N
            eventSets[EVENT_vetoableChangeListener] = new EventSetDescriptor ( com.jimrolf.functionfield.FunctionFieldWithWrapping.class, "vetoableChangeListener", java.beans.VetoableChangeListener.class, new String[] {"vetoableChange"}, "addVetoableChangeListener", "removeVetoableChangeListener" ); // NOI18N
        }
        catch(IntrospectionException e) {
            e.printStackTrace();
        }//GEN-HEADEREND:Events
        
        // Here you can add code for customizing the event sets array.
        
        return eventSets;     }//GEN-LAST:Events
    
    // Method identifiers//GEN-FIRST:Methods
    private static final int METHOD_hasError0 = 0;

    // Method array 
    /*lazy MethodDescriptor*/
    private static MethodDescriptor[] getMdescriptor(){
        MethodDescriptor[] methods = new MethodDescriptor[1];
    
        try {
            methods[METHOD_hasError0] = new MethodDescriptor(com.jimrolf.functionfield.FunctionFieldWithWrapping.class.getMethod("hasError", new Class[] {})); // NOI18N
            methods[METHOD_hasError0].setDisplayName ( "" );
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

