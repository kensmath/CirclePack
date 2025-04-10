package frames;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.border.TitledBorder;

import allMains.CPBase;
import allMains.CirclePack;
import combinatorics.komplex.HalfEdge;
import complex.Complex;
import dataObject.EdgeData;
import dataObject.FaceData;
import dataObject.NodeData;
import dataObject.TileData;
import exceptions.ParserException;
import komplex.EdgeSimple;
import listManip.EdgeLink;
import listManip.FaceLink;
import listManip.HalfLink;
import listManip.NodeLink;
import listManip.TileLink;
import packing.PackData;
import panels.DataTree;
import panels.VariableControlPanel;
import util.ColorUtil;
import util.ComplexField;
import util.IntegerField;
import util.RealField;
import variables.SliderControlPanel;

/**
 * TabbedPackDataHover is a hover panel displaying pack data. It contains
 * a tabbed pane displaying combinatoric information, a pack data tree,
 * and the current active variables.
 * 
 * @author kens
 * @author Alex Fawkes
 */
public class TabbedPackDataHover extends FluidHoverPanel implements ActionListener {
	/*
	 * Regenerate serialVersionUID whenever the nature of this class's fields change
	 * so that this class may be flattened. As background, serialization provides a
	 * unified interface for writing and reading an instance's current state to and
	 * from the file system. The value of serialVersionUID is used to ensure that an
	 * instance state being read from the file system is compatible.
	 * 
	 * Note that we are sub-classing a class that implements serialization (JPanel),
	 * so we must respect that in our implementation.
	 */
	private static final long serialVersionUID = 2314123463169349022L;
	private static final int SPACER_WIDTH = 2; // Width of spacing gap between components, in pixels.
	private static final int LINE_BORDER_WIDTH = 2; // Width of color borders, in pixels.

	// The main panels of the combinatoric information tab.
	protected JPanel vertexPanel;
	protected JPanel edgePanel;
	protected JPanel facePanel;
	protected JPanel tilePanel;

	// The components of the vertex panel.
	protected JTextField vertexChoiceField;
	protected IntegerField degreeField;
	protected JTextField flowerField;
	protected RealField angleSumField;
	protected JCheckBox boundaryCheckBox;
	protected RealField radiusField;
	protected RealField aimField;
	protected IntegerField vertexColorField;
	protected IntegerField vertMarkField;
	protected ComplexField centerField;
	protected ComplexField redCenterField;

	// The components of the edge panel.
	protected JTextField edgeChoiceField;
	protected RealField invDistanceField;
	protected RealField schwarzianField;
	protected RealField redRadiusField;
	protected RealField edgeLengthField;  // actual in place length
	protected RealField edgeIntendField;  // computed based on radii and inv dist
	protected JCheckBox redEdgeCheckBox;
	protected JCheckBox redTwinCheckBox;
	
	// The components of the face panel.
	protected JTextField faceChoiceField;
	protected JTextField verticesField;
	protected IntegerField faceColorField;
	protected IntegerField faceMarkField;
	protected IntegerField nextField;
	protected IntegerField nextRedField;
	protected JCheckBox redCheckBox;
	
	// The components of the tile panel.
	protected JTextField tileChoiceField;
	protected IntegerField tileColorField;
	protected IntegerField tiledegreeField;
	protected JTextField tileflowerField;
	protected IntegerField tileMarkField;

	// The data tree and its containing panel, which form their own tab.
	protected JPanel dataTreePanel;
	protected DataTree dataTree;
	
	// The button to update the information to reflect packing changes.
	protected JButton updateButton;

	// Subclass instance for containing GUI update functionality.
	protected UpdateActions updateActions = new UpdateActions();
	
	public VariableControlPanel variableControlPanel;
	public SliderControlPanel sliderControlPanel;

	// Constructor
	public TabbedPackDataHover(JComponent parent) {
		super(parent);

		createGUI();
	}

	public void createGUI() {
		// TODO: Add empty borders around components for legible spacing.
		// We want distinct fields to have some separation between them.
		
		/*
		 * Some notes on using BoxLayout:
		 * 
		 * Building a GUI in BoxLayout is about establishing a 
		 * hierarchy of panels and ultimately components in terms 
		 * of rows and columns. In this case, the ultimate parent 
		 * is this instance, which is a JPanel. It contains two
		 * components as rows - the tabbed pane containing the 
		 * bulk of the GUI, and the update button.
		 * 
		 * The first tab of the tabbed pane is the combinatoric
		 * information panel. It contains three rows - the vertex 
		 * panel, the edge panel, and the face panel. Further 
		 * subdividing, the vertex panel has three rows displaying 
		 * different text fields. The second row contains four 
		 * columns - the degree field, the flower field, the angle 
		 * sum field, and the boundary check box. The degree field 
		 * is made up of two rows - the title label and the text field.
		 * 
		 * Ultimately, what you end up with is a tree of components
		 * representing a nested hierarchy of rows and columns.
		 */
	
		/*
		 * 
		 * Initialize vertex panel.
		 * 
		 */
		JLabel vertexChoiceLabel = new JLabel("Choose vertex:");
		vertexChoiceField = new JTextField();
		vertexChoiceField.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent ae) {
				updateActions.updateVertex(
						CirclePack.cpb.getActivePackData(), false);
			}
		});
		vertexChoiceField.setToolTipText(
				"Enter index (or legal description) of vertex.");
		limitFieldHeight(vertexChoiceField);
		JPanel vertexPanelRowOne = new JPanel();
		vertexPanelRowOne.setLayout(new BoxLayout(vertexPanelRowOne,
				BoxLayout.LINE_AXIS));
		vertexPanelRowOne.setBorder(BorderFactory.createEmptyBorder(
				SPACER_WIDTH, SPACER_WIDTH, SPACER_WIDTH, SPACER_WIDTH));
		vertexPanelRowOne.add(vertexChoiceLabel);
		vertexPanelRowOne.add(Box.createHorizontalStrut(SPACER_WIDTH));
		vertexPanelRowOne.add(vertexChoiceField);

		// Combinatoric information.
		degreeField = new IntegerField("Degree");
		degreeField.setEditable(false);
		// Both components in the sub-panel must be left aligned.
		// This is because they align with respect to each other.
		JLabel verticesLabel = new JLabel("Flower");
		verticesLabel.setFont(
				verticesLabel.getFont().deriveFont(Font.PLAIN, 10.0F));
		verticesLabel.setAlignmentX(Box.LEFT_ALIGNMENT);
		flowerField = new JTextField();
		flowerField.setEditable(false);
		flowerField.setAlignmentX(Box.LEFT_ALIGNMENT);
		limitFieldHeight(flowerField);
		// TODO: Consider using (building if necessary) a class for generic strings similar to RealField, etc.
		// It would make this GUI code more consistent.
		JPanel flowerSubPanel = new JPanel();
		flowerSubPanel.setLayout(new BoxLayout(flowerSubPanel,
				BoxLayout.PAGE_AXIS));
		flowerSubPanel.add(verticesLabel);
		flowerSubPanel.add(flowerField);
		boundaryCheckBox = new JCheckBox();
		boundaryCheckBox.setText("Boundary");
		boundaryCheckBox.setFont(
				boundaryCheckBox.getFont().deriveFont(Font.PLAIN, 10.0F));
		vertexColorField = new IntegerField("Color");
		vertexColorField.setActionCommand("vert_color");
		vertexColorField.addActionListener(this);
		vertMarkField = new IntegerField("Mark");
		vertMarkField.setActionCommand("vert_mark");
		vertMarkField.addActionListener(this);
		JPanel vertexPanelRowTwo = new JPanel();
		vertexPanelRowTwo.setLayout(new BoxLayout(vertexPanelRowTwo,
				BoxLayout.LINE_AXIS));
		vertexPanelRowTwo.setBorder(BorderFactory.createEmptyBorder(
				SPACER_WIDTH, SPACER_WIDTH, SPACER_WIDTH, SPACER_WIDTH));
		vertexPanelRowTwo.add(degreeField);
		vertexPanelRowTwo.add(Box.createHorizontalStrut(SPACER_WIDTH));
		vertexPanelRowTwo.add(flowerSubPanel);
		vertexPanelRowTwo.add(Box.createHorizontalStrut(SPACER_WIDTH));
		vertexPanelRowTwo.add(boundaryCheckBox);
		vertexPanelRowTwo.add(Box.createHorizontalStrut(SPACER_WIDTH));
		vertexPanelRowTwo.add(vertexColorField);
		vertexPanelRowTwo.add(Box.createHorizontalStrut(SPACER_WIDTH));
		vertexPanelRowTwo.add(vertMarkField);

		aimField = new RealField("Aim / Pi");
		aimField.setActionCommand("set_aim");
		aimField.addActionListener(this);
		angleSumField = new RealField("AngleSum / Pi");
		angleSumField.setEditable(false);
		JPanel vertexPanelRowThree = new JPanel();
		vertexPanelRowThree.setLayout(new BoxLayout(vertexPanelRowThree,
				BoxLayout.LINE_AXIS));
		vertexPanelRowThree.setBorder(BorderFactory.createEmptyBorder(
				SPACER_WIDTH, SPACER_WIDTH, SPACER_WIDTH, SPACER_WIDTH));
		vertexPanelRowThree.add(aimField);
		vertexPanelRowThree.add(Box.createHorizontalStrut(SPACER_WIDTH));
		vertexPanelRowThree.add(angleSumField);
		
		radiusField = new RealField("Radius");
		radiusField.setActionCommand("set_rad");
		radiusField.addActionListener(this);
		centerField = new ComplexField("Center");
		centerField.setActionCommand("set_center");
		centerField.addActionListener(this);
		JPanel vertexPanelRowFour = new JPanel();
		vertexPanelRowFour.setLayout(new BoxLayout(vertexPanelRowFour,
				BoxLayout.LINE_AXIS));
		vertexPanelRowFour.setBorder(BorderFactory.createEmptyBorder(
				SPACER_WIDTH, SPACER_WIDTH, SPACER_WIDTH, SPACER_WIDTH));
		vertexPanelRowFour.add(radiusField);
		vertexPanelRowFour.add(Box.createHorizontalStrut(SPACER_WIDTH));
		vertexPanelRowFour.add(centerField);

		vertexPanel = new JPanel();
		vertexPanel.setLayout(new BoxLayout(vertexPanel,
				BoxLayout.PAGE_AXIS));
		vertexPanel.setBorder(BorderFactory.createTitledBorder(
				BorderFactory.createLineBorder(Color.RED,
						LINE_BORDER_WIDTH), "Vertex Data",
				TitledBorder.LEADING, TitledBorder.TOP));
		// Add vertical glue between rows to fill empty space as frame grows.
		vertexPanel.add(Box.createVerticalGlue());
		vertexPanel.add(vertexPanelRowOne);
//		vertexPanel.add(Box.createVerticalGlue());
		vertexPanel.add(vertexPanelRowTwo);
//		vertexPanel.add(Box.createVerticalGlue());
		vertexPanel.add(vertexPanelRowThree);
//		vertexPanel.add(Box.createVerticalGlue());
		vertexPanel.add(vertexPanelRowFour);
//		vertexPanel.add(Box.createVerticalGlue());

		/*
		 * 
		 * Initialize edge panel. 
		 * 
		 */
		JLabel edgeChoiceLabel = new JLabel("Choose edge:");
		edgeChoiceField = new JTextField();
		edgeChoiceField.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent ae) {
				updateActions.updateEdge(CirclePack.cpb.getActivePackData());
			}
		});
		edgeChoiceField.setToolTipText(
				"Enter index (or legal description) of edge.");
		limitFieldHeight(edgeChoiceField);
		JPanel edgePanelRowOne = new JPanel();
		edgePanelRowOne.setLayout(new BoxLayout(edgePanelRowOne,
				BoxLayout.LINE_AXIS));
		edgePanelRowOne.setBorder(BorderFactory.createEmptyBorder(
				SPACER_WIDTH, SPACER_WIDTH, SPACER_WIDTH, SPACER_WIDTH));
		edgePanelRowOne.add(edgeChoiceLabel);
		edgePanelRowOne.add(Box.createHorizontalStrut(SPACER_WIDTH));
		edgePanelRowOne.add(edgeChoiceField);

		invDistanceField = new RealField("Inv Distance");
		invDistanceField.setActionCommand("set_inv_dist");
		invDistanceField.addActionListener(this);
		schwarzianField=new RealField("Schwarzian");
		schwarzianField.setActionCommand("put_schwarzian");
		schwarzianField.addActionListener(this);
		edgeLengthField = new RealField("Length (actual)");
		edgeLengthField.setEditable(false);
		edgeIntendField = new RealField("Length (intended)");
		edgeIntendField.setEditable(false);
		
		// row 2
		JPanel edgePanelRowTwo = new JPanel();
		edgePanelRowTwo.setLayout(new BoxLayout(edgePanelRowTwo,
				BoxLayout.LINE_AXIS));
		edgePanelRowTwo.setBorder(BorderFactory.createEmptyBorder(
				SPACER_WIDTH, SPACER_WIDTH, SPACER_WIDTH, SPACER_WIDTH));
		edgePanelRowTwo.add(invDistanceField);
		edgePanelRowTwo.add(Box.createHorizontalStrut(SPACER_WIDTH));
		edgePanelRowTwo.add(schwarzianField);
		edgePanelRowTwo.add(Box.createHorizontalStrut(SPACER_WIDTH));
		edgePanelRowTwo.add(edgeLengthField);
		edgePanelRowTwo.add(Box.createHorizontalStrut(SPACER_WIDTH));
		edgePanelRowTwo.add(edgeIntendField);
		
		redEdgeCheckBox = new JCheckBox();
		redEdgeCheckBox.setText("Red edge?");
		redTwinCheckBox = new JCheckBox();
		redTwinCheckBox.setText("Red Twin?");
		redRadiusField = new RealField("Red radius");
		redRadiusField.setActionCommand("set_red_rad");
		redRadiusField.addActionListener(this);
		redCenterField = new ComplexField("Red Center");
		redCenterField.setActionCommand("set_red_center");
		redCenterField.addActionListener(this);


		// row 3
		JPanel edgePanelRowThree = new JPanel();
		edgePanelRowThree.setLayout(new BoxLayout(edgePanelRowThree,
				BoxLayout.LINE_AXIS));
		edgePanelRowThree.setBorder(BorderFactory.createEmptyBorder(
				SPACER_WIDTH, SPACER_WIDTH, SPACER_WIDTH, SPACER_WIDTH));
		JPanel redPanel=new JPanel();
		redPanel.setLayout(new BoxLayout(redPanel,
				BoxLayout.Y_AXIS));
		redPanel.add(redEdgeCheckBox);
		redPanel.add(redTwinCheckBox);
		edgePanelRowThree.add(redPanel);
		edgePanelRowThree.add(redRadiusField);
		edgePanelRowThree.add(redCenterField);
		
		edgePanel = new JPanel();
		edgePanel.setLayout(new BoxLayout(edgePanel, BoxLayout.PAGE_AXIS));
		edgePanel.setBorder(BorderFactory.createTitledBorder(
				BorderFactory.createLineBorder(Color.GREEN,
						LINE_BORDER_WIDTH), "Edge Data",
				TitledBorder.LEADING, TitledBorder.TOP));
		// Add vertical glue between rows to fill empty space as the frame grows.
//		edgePanel.add(Box.createVerticalGlue());
		edgePanel.add(edgePanelRowOne);
//		edgePanel.add(Box.createVerticalGlue());
		edgePanel.add(edgePanelRowTwo);
//		edgePanel.add(Box.createVerticalGlue());
		edgePanel.add(edgePanelRowThree);
//		edgePanel.add(Box.createVerticalGlue());

		/*
		 * 
		 * Initialize face panel.
		 * 
		 */
		JLabel faceChoiceLabel = new JLabel("Choose face:");
		faceChoiceField = new JTextField();
		faceChoiceField.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent ae) {
				updateActions.updateFace(CirclePack.cpb.getActivePackData());
			}
		});
		faceChoiceField.setToolTipText(
				"Enter index (or legal description) of face.");
		limitFieldHeight(faceChoiceField);
		JPanel facePanelRowOne = new JPanel();
		facePanelRowOne.setLayout(new BoxLayout(facePanelRowOne,
				BoxLayout.LINE_AXIS));
		facePanelRowOne.setBorder(BorderFactory.createEmptyBorder(
				SPACER_WIDTH, SPACER_WIDTH, SPACER_WIDTH, SPACER_WIDTH));
		facePanelRowOne.add(faceChoiceLabel);
		facePanelRowOne.add(Box.createHorizontalStrut(SPACER_WIDTH));
		facePanelRowOne.add(faceChoiceField);

		// Both components in the sub-panel must be left aligned.
		// This is because they are aligned with respect to each other.
		verticesLabel = new JLabel("Vertices");
		verticesLabel.setFont(
				verticesLabel.getFont().deriveFont(Font.PLAIN, 10.0F));
		verticesLabel.setAlignmentX(Box.LEFT_ALIGNMENT);
		verticesField = new JTextField();
		verticesField.setEditable(false);
		verticesField.setAlignmentX(Box.LEFT_ALIGNMENT);
		limitFieldHeight(verticesField);
		JPanel verticesSubpanel = new JPanel();
		verticesSubpanel.setLayout(new BoxLayout(verticesSubpanel,
				BoxLayout.PAGE_AXIS));
		verticesSubpanel.add(verticesLabel);
		verticesSubpanel.add(verticesField);
		// Combinatoric information.
		faceColorField = new IntegerField("Color");
		faceColorField.setEditable(true);
		faceColorField.setActionCommand("face_color");
		faceColorField.addActionListener(this);
		faceMarkField = new IntegerField("Mark");
		faceMarkField.setEditable(true);
		faceMarkField.setActionCommand("face_mark");
		faceMarkField.addActionListener(this);

		JPanel facePanelRowTwo = new JPanel();
		facePanelRowTwo.setLayout(new BoxLayout(facePanelRowTwo,
				BoxLayout.LINE_AXIS));
		facePanelRowTwo.setBorder(BorderFactory.createEmptyBorder(
				SPACER_WIDTH, SPACER_WIDTH, SPACER_WIDTH, SPACER_WIDTH));
		facePanelRowTwo.add(verticesSubpanel);
		facePanelRowTwo.add(Box.createHorizontalStrut(SPACER_WIDTH));
		facePanelRowTwo.add(faceColorField);
		facePanelRowTwo.add(Box.createHorizontalStrut(SPACER_WIDTH));
		facePanelRowTwo.add(Box.createHorizontalStrut(SPACER_WIDTH));
		facePanelRowTwo.add(faceMarkField);

		facePanel = new JPanel();
		facePanel.setLayout(new BoxLayout(facePanel, BoxLayout.PAGE_AXIS));
		facePanel.setBorder(BorderFactory.createTitledBorder(
				BorderFactory.createLineBorder(Color.BLUE, 
						LINE_BORDER_WIDTH), "Face Data",
				TitledBorder.LEADING, TitledBorder.TOP));
		// Add vertical glue between rows to fill empty space as frame grows.
		facePanel.add(Box.createVerticalGlue());
		facePanel.add(facePanelRowOne);
		facePanel.add(Box.createVerticalGlue());
		facePanel.add(facePanelRowTwo);
		facePanel.add(Box.createVerticalGlue());

		/*
		 * 
		 * Initialize tile panel.
		 * 
		 */
		JLabel tileChoiceLabel = new JLabel("Choose tile:");
		tileChoiceField = new JTextField();
		tileChoiceField.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent ae) {
				updateActions.updateTile(CirclePack.cpb.getActivePackData());
			}
		});
		tileChoiceField.setToolTipText("Enter index "
				+ "(or legal description) of tile.");
		limitFieldHeight(tileChoiceField);
		JPanel tilePanelRowOne = new JPanel();
		tilePanelRowOne.setLayout(new BoxLayout(tilePanelRowOne,
				BoxLayout.LINE_AXIS));
		tilePanelRowOne.setBorder(BorderFactory.createEmptyBorder(
				SPACER_WIDTH, SPACER_WIDTH,
				SPACER_WIDTH, SPACER_WIDTH));
		tilePanelRowOne.add(tileChoiceLabel);
		tilePanelRowOne.add(Box.createHorizontalStrut(SPACER_WIDTH));
		tilePanelRowOne.add(tileChoiceField);

		// Combinatoric information.
		tiledegreeField = new IntegerField("Degree");
		tiledegreeField.setEditable(false);
		// Both components in the sub-panel must be left aligned.
		// This is because they align with respect to each other.
		JLabel tileLabel = new JLabel("Tile flower");
		tileLabel.setFont(tileLabel.getFont().deriveFont(Font.PLAIN, 10.0F));
		tileLabel.setAlignmentX(Box.LEFT_ALIGNMENT);
		tileflowerField = new JTextField();
		tileflowerField.setEditable(false);
		tileflowerField.setAlignmentX(Box.LEFT_ALIGNMENT);
		limitFieldHeight(tileflowerField);
		// TODO: Consider using (building if necessary) a class 
		//    for generic strings similar to RealField, etc.
		//    It would make this GUI code more consistent.
		JPanel tileflowerSubPanel = new JPanel();
		tileflowerSubPanel.setLayout(new BoxLayout(tileflowerSubPanel,
				BoxLayout.PAGE_AXIS));
		tileflowerSubPanel.add(tileLabel);
		tileflowerSubPanel.add(tileflowerField);
		tileColorField = new IntegerField("Color");
		tileColorField.setActionCommand("tile_color");
		tileColorField.addActionListener(this);
		tileMarkField = new IntegerField("Mark");
		tileMarkField.setActionCommand("tile_mark");
		tileMarkField.addActionListener(this);
		JPanel tilePanelRowTwo = new JPanel();
		tilePanelRowTwo.setLayout(new BoxLayout(tilePanelRowTwo,
				BoxLayout.LINE_AXIS));
		tilePanelRowTwo.setBorder(
				BorderFactory.createEmptyBorder(
						SPACER_WIDTH, SPACER_WIDTH, 
						SPACER_WIDTH, SPACER_WIDTH));
		tilePanelRowTwo.add(tiledegreeField);
		tilePanelRowTwo.add(Box.createHorizontalStrut(SPACER_WIDTH));
		tilePanelRowTwo.add(tileflowerSubPanel);
		tilePanelRowTwo.add(Box.createHorizontalStrut(SPACER_WIDTH));
		tilePanelRowTwo.add(Box.createHorizontalStrut(SPACER_WIDTH));
		tilePanelRowTwo.add(tileColorField);
		tilePanelRowTwo.add(Box.createHorizontalStrut(SPACER_WIDTH));
		tilePanelRowTwo.add(tileMarkField);

		tilePanel = new JPanel();
		tilePanel.setLayout(new BoxLayout(tilePanel, BoxLayout.PAGE_AXIS));
		tilePanel.setBorder(BorderFactory.
				createTitledBorder(BorderFactory.createLineBorder(
						Color.orange, LINE_BORDER_WIDTH), 
						"Tile Data", TitledBorder.LEADING, TitledBorder.TOP));
		// Add vertical glue between rows to fill empty space as the frame grows.
		tilePanel.add(Box.createVerticalGlue());
		tilePanel.add(tilePanelRowOne);
		tilePanel.add(Box.createVerticalGlue());
		tilePanel.add(tilePanelRowTwo);
		tilePanel.add(Box.createVerticalGlue());

		/*
		 * 
		 * Initialize combinatoric information panel.
		 * 
		 */
		JPanel vefDataPanel = new JPanel();
		vefDataPanel.setLayout(new BoxLayout(vefDataPanel, BoxLayout.PAGE_AXIS));
		vefDataPanel.add(vertexPanel);
		vefDataPanel.add(edgePanel);
		vefDataPanel.add(facePanel);
		vefDataPanel.add(tilePanel);
		
		/*
		 * 
		 * Initialize tree panel.
		 * 
		 */
		dataTree = new DataTree();
		dataTreePanel = new JPanel();
		dataTreePanel.setLayout(new BoxLayout(dataTreePanel,
				BoxLayout.PAGE_AXIS));
		dataTreePanel.setBorder(
				BorderFactory.createTitledBorder(
						BorderFactory.createEtchedBorder(),
						"Pack Data", TitledBorder.LEADING,
						TitledBorder.TOP));
		dataTreePanel.add(dataTree);

		/*
		 * 
		 * Initialize variable control panel. (Alex Fawkes)
		 * 
		 */
		JSplitPane varSplitPane=new JSplitPane(JSplitPane.VERTICAL_SPLIT);
		
		JPanel variablesPanel = new JPanel();
		variablesPanel.setLayout(new BoxLayout(variablesPanel, 
				BoxLayout.PAGE_AXIS));
		variablesPanel.setBorder(BorderFactory.createTitledBorder(
				BorderFactory.createEtchedBorder(), "Variable Control", 
				TitledBorder.LEADING, TitledBorder.TOP));
		variableControlPanel=new VariableControlPanel();
		variablesPanel.add(variableControlPanel);
		varSplitPane.add(variablesPanel);
		
		/*
		 * 
		 * Initialize slider panel. (Ken Stephenson)
		 * 
		 */
		JPanel sliderPanel = new JPanel();
		sliderPanel.setLayout(new BoxLayout(sliderPanel, BoxLayout.PAGE_AXIS));
		sliderPanel.setBorder(BorderFactory.createTitledBorder(
				BorderFactory.createEtchedBorder(), "Slider Controls", 
				TitledBorder.LEADING, TitledBorder.TOP));
		sliderControlPanel=new SliderControlPanel();
		sliderPanel.add(sliderControlPanel);
		varSplitPane.add(sliderPanel);
	
		// where is the split?
		varSplitPane.setDividerLocation(250);
		
		/*
		 * 
		 *  Initialize update button.
		 *  
		 */
		updateButton = new JButton("Update");
		updateButton.setFont(updateButton.getFont().
				deriveFont((float) updateButton.getFont().getSize() - 1.0F));
		updateButton.setAlignmentX(Box.CENTER_ALIGNMENT);
		// Let it grow horizontally without bound, but cap its height.
		updateButton.setMaximumSize(new Dimension(Integer.MAX_VALUE,
				updateButton.getPreferredSize().height));
		updateButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent ae) {
				updateActions.updateData(CirclePack.cpb.getActivePackData());
			}
		});

		/*
		 * 
		 * Create the tabbed pane.
		 * 
		 */
		// TODO: The selection color for the tabbed pane doesn't look great.
		// Would be nice to figure out how to make this look correct.
		JTabbedPane tabbedPane = new JTabbedPane();
		tabbedPane.addTab("VEF Data", vefDataPanel);
		tabbedPane.addTab("Pack Data Tree", dataTreePanel);
		tabbedPane.addTab("Variables", varSplitPane);

		/*
		 * 
		 * Add everything to the main panel.
		 * 
		 */
		this.setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
		this.add(tabbedPane);
		this.add(updateButton);
	}

	/**
	 * Internal convenience method for limiting the maximum height 
	 * of an entry field component. This prevents entry fields from 
	 * growing vertically when the window is resized to be larger.
	 * 
	 * @param toLimit JTextField, component needing a limited height
	 */
	private void limitFieldHeight(JTextField toLimit) {
		// Set max height to the preferred height, but do not change max width.
		Dimension preferredSize = toLimit.getPreferredSize();
		Dimension oldMaximumSize = toLimit.getMaximumSize();
		toLimit.setMaximumSize(
				new Dimension(oldMaximumSize.width, preferredSize.height));
	}

	/**
	 * hack to request this object update its GUI. Should be removed
	 * 	after the real PackDataListener system is implemented.
	 */
	public void update(PackData packData) {
		updateActions.updateData(packData);
	}
	
	/**
	 * UpdateActions encapsulates the UI update functionality 
	 * of TabbedPackDataHover. This is primarily for 
	 * organizational clarity, but also prevents outside classes
	 * from directly manipulating the update functionality of 
	 * TabbedPackDataHover. If external control of update 
	 * functionality is desired, TabbedPackDataHover should
	 * expose an appropriate interface for doing so.
	 * 
	 * @author kens
	 * @author Alex Fawkes
	 *
	 */
	protected class UpdateActions {
		/**
		 * Update the displayed pack data information for the given
		 * packing. This is typically the active packing.
		 * 
		 * @param packData the <code>PackData</code> to reflect in the update
		 */
		public void updateData(PackData packData) {
			lockedFrame.setTitle("Data for Packing p" + packData.packNum);
			dataTree.updatePackingData(packData);
			updateVertex(packData, false);
			updateFace(packData);
			updateEdge(packData);
		}

		/**
		 * Update the vertex information.
		 * 
		 * @param p PackData, to reflect in the update
		 * @param useActiveVertex boolean: true, use packing's active vertex
		 */
		public void updateVertex(PackData p, boolean useActiveVertex) {
			// If packData is null or empty just return.
			if (p == null || !p.status)  // p.packDCEL.vertices[2].center
				return;

			// Update for the current active or chosen vertex, 
			//    depending on the call signature.
			int v;
			try {
				if (useActiveVertex) 
					v = p.activeNode;
				else {
					try {
						v = NodeLink.grab_one_vert(p, vertexChoiceField.getText());
					} catch (Exception iex) {v=p.activeNode;}
				}
				// If the current vertex is invalid, use the first vertex.
				if (v <= 0 || v > p.nodeCount) 
					v = 1;

				// get the data for 'v'
				NodeData vData=new NodeData(p,v);
				
				// Update the UI elements.
				vertexChoiceField.setText(Integer.toString(vData.vindx));
				radiusField.setValue(vData.rad);
				centerField.setValue(new Complex(vData.center));
				aimField.setValue(vData.aim/Math.PI);
				angleSumField.setValue(vData.angsum/Math.PI);
				degreeField.setValue(vData.degree);
				vertexColorField.setValue(vData.colorCode);
				vertMarkField.setValue(vData.mark);

				if (vData.bdryflag) 
					boundaryCheckBox.setSelected(true);
				else 
					boundaryCheckBox.setSelected(false);
				flowerField.setText(vData.flowerStr);

			} catch (Exception ex) {
				CirclePack.cpb.errMsg("error processing vertex update");
			}
		}

		/**
		 * Update the face information.
		 * 
		 * @param packData the <code>PackData</code> to reflect in the update
		 */
		public void updateFace(PackData packData) {
			// If packData is null or empty just return.
			if (packData == null || !packData.status) 
				return;

			// Get the index of the chosen face or use first index
			try {
				int currentFace =0;
				try {currentFace= FaceLink.grab_one_face(packData,
						faceChoiceField.getText());
				} catch (Exception iex) {currentFace=0;}
				if (currentFace==0) 
					currentFace=1;
				
				FaceData fData=new FaceData(packData,currentFace);
				
				// Update the UI elements.
				faceChoiceField.setText(Integer.toString(fData.findx));
				verticesField.setText(fData.vertsStr);
				faceColorField.setValue(fData.colorCode);
				faceMarkField.setValue(fData.mark);
			} catch (Exception ex) {
				CirclePack.cpb.errMsg("error processing face update");
			}
		}

		/**
		 * Update the edge information.
		 * 
		 * @param packData the <code>PackData</code> to reflect in the update
		 */
		public void updateEdge(PackData packData) {
			// If packData is null or empty just return.
			if (packData == null || !packData.status) return;

			// Get the chosen edge and test it for validity.
			try {
				EdgeSimple edge=null;
				try {
					edge = EdgeLink.grab_one_edge(packData,
						edgeChoiceField.getText());
				} catch(Exception iex) {edge=null;}
				if (edge == null) 
					return;
				HalfEdge he=packData.packDCEL.findHalfEdge(edge);
				if (he==null)
					return;
				EdgeData eData=new EdgeData(packData,he);
				edgeChoiceField.setText(eData.edgeStr);
				invDistanceField.setValue(eData.invDist);
				schwarzianField.setValue(eData.schwarzian);
				edgeLengthField.setValue(eData.edgelength);
				edgeIntendField.setValue(eData.intended);
				if (eData.isRed)
					redEdgeCheckBox.setSelected(true);
				else
					redEdgeCheckBox.setSelected(false);
				if (eData.isTwinned)
					redTwinCheckBox.setSelected(true);
				else
					redTwinCheckBox.setSelected(false);
				if (eData.isRed) {
					redRadiusField.setValue(eData.redRad);
					redCenterField.setValue(eData.redCenter);
				}
			} catch (Exception ex) {
				CirclePack.cpb.errMsg("error processing edge update");
			}
		}
		
		/**
		 * Update the tile information.
		 * 
		 * @param packData the <code>PackData</code> to reflect in update
		 * @param useActiveVertex whether or not to use packing's active vertex
		 */
		public void updateTile(PackData packData) {
			// If tileData is null or empty just return.
			if (packData == null || !packData.status || 
					packData.tileData==null ||
					packData.tileData.tileCount==0) 
				return;

			// Update for the current active or chosen vertex, 
			//    depending on the call signature.
			int currentTile=1;
			try {
				try {
					currentTile = TileLink.grab_one_tile(packData.tileData,
						tileChoiceField.getText());
				} catch (Exception iex) {currentTile=0;}

				// If the current tile is invalid, use 1
				if (currentTile <= 0 || currentTile > packData.tileData.tileCount) 
					currentTile = 1;

				// Get the tileData
				TileData tData=new TileData(packData,currentTile);

				// Update the UI elements.
				tileChoiceField.setText(Integer.toString(tData.tindx));
				tiledegreeField.setValue(tData.degree);
				tileColorField.setValue(tData.colorCode);
				tileMarkField.setValue(tData.mark);
				tileflowerField.setText(tData.nghbStr);
			
			} catch (Exception ex) {
				CirclePack.cpb.errMsg("error processing tile update");
			}
		}

		public VariableControlPanel getVarContPan() {
			return variableControlPanel;
		}
		
		public SliderControlPanel getSliderContPan() {
			return sliderControlPanel;
		}
		
		/**
		 * implement changes in Pack Data Tree made by user
		 * @param p PackData
		 */
		public void putVertColor(PackData p) {
			int vert = NodeLink.grab_one_vert(p, vertexChoiceField.getText());
			if (vert==0) 
				return;
			p.setCircleColor(vert,ColorUtil.coLor(vertexColorField.getValue()));
			vertexColorField.setValue(
					ColorUtil.col_to_table(p.getCircleColor(vert)));
		}
		
		public void putVertMark(PackData p) {
			int vert = NodeLink.grab_one_vert(p, vertexChoiceField.getText());
			if (vert==0) 
				return;
			p.setVertMark(vert,vertMarkField.getValue());
			vertMarkField.setValue(p.getVertMark(vert));
		}
		
		public void putFaceColor(PackData p) {
			int face = NodeLink.grab_one_vert(p, faceChoiceField.getText());
			if (face==0) 
				return;
			p.setFaceColor(face,ColorUtil.coLor(faceColorField.getValue()));
			faceColorField.setValue(ColorUtil.col_to_table(p.getFaceColor(face)));
		}
		
		public void putFaceMark(PackData p) {
			int face = NodeLink.grab_one_vert(p, faceChoiceField.getText());
			if (face==0) 
				return;
			p.setFaceMark(face,faceMarkField.getValue());
			faceMarkField.setValue(p.getFaceMark(face));
		}
		
		public void putTileColor(PackData p) {
			int tindx = NodeLink.grab_one_vert(p, tileChoiceField.getText());
			if (tindx==0) 
				return;
			p.tileData.myTiles[tindx].color=
					ColorUtil.coLor(tileColorField.getValue());
			tileColorField.setValue(
					ColorUtil.col_to_table(p.tileData.myTiles[tindx].color));
		}
		
		public void putTileMark(PackData p) {
			int tindx = NodeLink.grab_one_vert(p, tileChoiceField.getText());
			if (tindx==0) 
				return;
			p.tileData.myTiles[tindx].mark=(int)tileMarkField.getValue();
			tileMarkField.setValue(p.tileData.myTiles[tindx].mark);
		}
		
		public void putRadius(PackData p) {
			int vert = NodeLink.grab_one_vert(p, vertexChoiceField.getText());
			if (vert==0) 
				return;
			p.setRadius(vert,radiusField.getValue());
			radiusField.setValue(p.getRadius(vert));
		}
		
		public void putRedRadius(PackData p) {
			HalfEdge he=HalfLink.grab_one_edge(p,edgeChoiceField.getText());
			if (he==null || he.myRedEdge==null)
				return;
			he.myRedEdge.setRadius(redRadiusField.getValue());
		}
		
		public void putAim(PackData p) {
			int vert = NodeLink.grab_one_vert(p, vertexChoiceField.getText());
			if (vert==0) 
				return;
			p.setAim(vert,aimField.getValue());
			aimField.setValue(p.getAim(vert));
		}
		
		public void putCenter(PackData p) {
			int vert = NodeLink.grab_one_vert(p, vertexChoiceField.getText());
			if (vert==0) return;
			
			String xstr=centerField.getTextReal().trim();
			String ystr=centerField.getTextImag().trim();
			if (xstr.charAt(0)=='_') // variable?
				xstr=CPBase.varControl.getValue(xstr.substring(1));
			if (ystr.charAt(0)=='_') // variable?
				ystr=CPBase.varControl.getValue(ystr.substring(1));
			try {
				double xval=Double.parseDouble(xstr);
				double yval=Double.parseDouble(ystr);
				p.setCenter(vert,new Complex(xval,yval));
				centerField.setValue(new Complex(xval,yval));
			} catch (Exception ex) {
				return;
			}
		}
		
		public void putRedCenter(PackData p) {
			HalfEdge he=HalfLink.grab_one_edge(p,edgeChoiceField.getText());
			if (he==null || he.myRedEdge==null)
				return;
			String xstr=redCenterField.getTextReal().trim();
			String ystr=redCenterField.getTextImag().trim();
			if (xstr.charAt(0)=='_') // variable?
				xstr=CPBase.varControl.getValue(xstr.substring(1));
			if (ystr.charAt(0)=='_') // variable?
				ystr=CPBase.varControl.getValue(ystr.substring(1));
			try {
				double xval=Double.parseDouble(xstr);
				double yval=Double.parseDouble(ystr);
				he.myRedEdge.setCenter(new Complex(xval,yval));
				redCenterField.setValue(new Complex(xval,yval));
			} catch (Exception ex) {
				return;
			}
		}
		
		public void putInvDist(PackData p) {
			EdgeSimple edge=EdgeLink.grab_one_edge(p,edgeChoiceField.getText());
			if (edge==null)
				return;
			HalfEdge hedge=p.packDCEL.findHalfEdge(edge);
			String id=invDistanceField.getText().trim();
			if (id.charAt(0)=='_') // variable?
				id=CPBase.varControl.getValue(id.substring(1));
			try {
				StringBuilder outstr=new StringBuilder();
				double invDist=1.0;  // NOTE: may be overlap or inversive distance
				if (id.charAt(0)=='*') { // indicates inv_dist in (1, infty)
					id=id.substring(1,id.length());
					invDist=Double.parseDouble(id);
					if (invDist<0.0) 
						throw new ParserException("'invDist' negative");
					outstr.append("*");
				}
				else {
					invDist=Double.parseDouble(id);
	     			if (invDist<0.0 || invDist>1.0) 
	     				  throw new ParserException(
	     						  "Use '*' for 'inversive distance' parameter");
	     			invDist=Math.cos(invDist*Math.PI);
				}
	     		  
				// 'invDist' essentially default and nothing to reset? 
				if (Math.abs(invDist-1.0)<=.0000001)  
					return;
				hedge.setInvDist(invDist);
				outstr.append(Double.toString(invDist));
				invDistanceField.realField.setText(outstr.toString());

			} catch (Exception ex) {
				return;
			}
			
		}
		
		public void putSchwarzian(PackData p) {
			EdgeSimple edge=EdgeLink.grab_one_edge(p,edgeChoiceField.getText());
			if (edge==null)
				return;
			HalfEdge hedge=p.packDCEL.findHalfEdge(edge);
			String sch=schwarzianField.getText().trim();
			if (sch.charAt(0)=='_') // variable?
				sch=CPBase.varControl.getValue(sch.substring(1));
			try {
				StringBuilder outstr=new StringBuilder();
				double schval=Double.parseDouble(sch);
				hedge.setSchwarzian(schval);
				outstr.append(Double.toString(schval));
				schwarzianField.realField.setText(outstr.toString());
			} catch (Exception ex) {
				return;
			}
		}
		
	}
	
	public void actionPerformed(ActionEvent evt) {
		String cmd=evt.getActionCommand();
		PackData p=CirclePack.cpb.getActivePackData();
		
		if (cmd.equals("vert_color")) {
			updateActions.putVertColor(p);
		}
		else if (cmd.equals("vert_mark")) {
			updateActions.putVertMark(p);
		}
		else if (cmd.equals("face_color")) {
			updateActions.putFaceColor(p);
		}
		else if (cmd.equals("face_mark")) {
			updateActions.putFaceMark(p);
		}
		else if (cmd.equals("tile_color")) {
			updateActions.putTileColor(p);
		}
		else if (cmd.equals("tile_mark")) {
			updateActions.putTileMark(p);
		}
 		else if (cmd.equals("set_center")) {
			updateActions.putCenter(p);
		}
 		else if (cmd.equals("set_red_center")) {
 			updateActions.putRedCenter(p);
 		}
		else if (cmd.equals("set_aim")) {
			updateActions.putAim(p);
		}
		else if (cmd.equals("set_rad")) {
			updateActions.putRadius(p);
		}
		else if (cmd.equals("set_red_rad")) {
			updateActions.putRedRadius(p);
		}
		else if (cmd.equals("set_inv_dist")) {
			updateActions.putInvDist(p);
		}
		else if (cmd.equals("put_schwarzian")) {
			updateActions.putSchwarzian(p);
		}

	}

}