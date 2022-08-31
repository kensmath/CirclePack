package panels;
import java.awt.Component;
import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.LayoutStyle;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import allMains.CPBase;
import circlePack.PackControl;
import packing.CPdrawing;

public class ScreenPanel extends JPanel implements ChangeListener {

	private static final long 
	serialVersionUID = 1L;
	
	private JSlider lineSlider;
	private JSlider fillOpacitySlider;
	private JSlider sphOpacitySlider;
	private JSlider textSizeSlider;
	private JCheckBox aliasCkBox;
	private JButton jButton1;
	private AbstractAction aliasAction;
	private AbstractAction resetAction;
	private JLabel jLabel3;
	private JLabel jLabel2;
	private JLabel jLabel1;
	private JPanel sliderPanel;
	private JPanel jPanel1;
	private JLabel jLabel4;

	// Constructor
	public ScreenPanel() {
		super();
		initGUI();
	}
	
	private void initGUI() {
		try {
			GroupLayout thisLayout = new GroupLayout(this);
			setLayout(thisLayout);
			{
				sliderPanel = new JPanel();
				GroupLayout jPanel2Layout = new GroupLayout((JComponent)sliderPanel);
				sliderPanel.setLayout(jPanel2Layout);
				{
					lineSlider = new JSlider();
					lineSlider.setToolTipText("Set line thickness, 1 to 12.");
					lineSlider.setName("Line Thickness");
					lineSlider.setSnapToTicks(true);
					lineSlider.setValueIsAdjusting(true);
					lineSlider.setMaximum(12);
					lineSlider.setMinimum(1);
					lineSlider.setValue(CPBase.DEFAULT_LINETHICKNESS);
					lineSlider.setMajorTickSpacing(1);
					lineSlider.setPaintTicks(true);
					lineSlider.setSize(190, 32);
					lineSlider.addChangeListener(this);
				}
				{
					fillOpacitySlider = new JSlider();
					fillOpacitySlider.setName("fillOpacitySlider");
					fillOpacitySlider.setLayout(null);
					fillOpacitySlider.setMaximum(255);
					fillOpacitySlider.setToolTipText("Fill opacity: larger = denser colors");
					fillOpacitySlider.setValueIsAdjusting(true);
					fillOpacitySlider.setValue(CPBase.cpDrawing[0].getFillOpacity());
					fillOpacitySlider.setMajorTickSpacing(25);
					fillOpacitySlider.setPaintTicks(true);
					fillOpacitySlider.setSize(190, 32);
					fillOpacitySlider.addChangeListener(this);
				}
				{
					sphOpacitySlider = new JSlider();
					sphOpacitySlider.setName("sphOpacitySlider");
					sphOpacitySlider.setMaximum(255);
					sphOpacitySlider.setToolTipText("Sphere opacity: smaller lets back show through");
					sphOpacitySlider.setValueIsAdjusting(true);
					sphOpacitySlider.setValue(CPBase.cpDrawing[0].getSphereOpacity());
					sphOpacitySlider.setMajorTickSpacing(25);
					sphOpacitySlider.setPaintTicks(true);
					sphOpacitySlider.setSize(190, 32);
					sphOpacitySlider.addChangeListener(this);
				}
				{
					textSizeSlider = new JSlider();
					textSizeSlider.setName("textSizeSlider");
					textSizeSlider.setMaximum(30);
					textSizeSlider.setToolTipText("text size on screen, 1-30");
					textSizeSlider.setValueIsAdjusting(true);
					textSizeSlider.setValue(CPBase.DEFAULT_INDEX_FONT.getSize());
					textSizeSlider.setMajorTickSpacing(25);
					textSizeSlider.setPaintTicks(true);
					textSizeSlider.setSize(190, 32);
					textSizeSlider.addChangeListener(this);
				}
				{
					jLabel1 = new JLabel();
					jLabel1.setText("Line Thickness");
				}
				{
					jLabel2 = new JLabel();
					jLabel2.setText("Fill Opacity");
				}
				{
					jLabel3 = new JLabel();
					jLabel3.setText("Sphere Opacity");
				}
				{
					jLabel4 = new JLabel();
					jLabel4.setText("Font Size");
				}
					jPanel2Layout.setHorizontalGroup(jPanel2Layout.createSequentialGroup()
					.addGroup(jPanel2Layout.createParallelGroup()
					    .addComponent(lineSlider, GroupLayout.Alignment.LEADING, GroupLayout.PREFERRED_SIZE, 190, GroupLayout.PREFERRED_SIZE)
					    .addComponent(fillOpacitySlider, GroupLayout.Alignment.LEADING, GroupLayout.PREFERRED_SIZE, 190, GroupLayout.PREFERRED_SIZE)
					    .addComponent(sphOpacitySlider, GroupLayout.Alignment.LEADING, GroupLayout.PREFERRED_SIZE, 190, GroupLayout.PREFERRED_SIZE)
					    .addComponent(textSizeSlider, GroupLayout.Alignment.LEADING, GroupLayout.PREFERRED_SIZE, 190, GroupLayout.PREFERRED_SIZE))
					.addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
					.addGroup(jPanel2Layout.createParallelGroup()
					    .addComponent(jLabel1, GroupLayout.Alignment.LEADING, GroupLayout.PREFERRED_SIZE, 151, GroupLayout.PREFERRED_SIZE)
					    .addComponent(jLabel2, GroupLayout.Alignment.LEADING, GroupLayout.PREFERRED_SIZE, 151, GroupLayout.PREFERRED_SIZE)
					    .addComponent(jLabel3, GroupLayout.Alignment.LEADING, GroupLayout.PREFERRED_SIZE, 151, GroupLayout.PREFERRED_SIZE)
					    .addComponent(jLabel4, GroupLayout.Alignment.LEADING, GroupLayout.PREFERRED_SIZE, 151, GroupLayout.PREFERRED_SIZE))
					.addContainerGap(18, 18));
				jPanel2Layout.setVerticalGroup(jPanel2Layout.createSequentialGroup()
					.addGroup(jPanel2Layout.createParallelGroup()
					    .addComponent(lineSlider, GroupLayout.Alignment.LEADING, GroupLayout.PREFERRED_SIZE, 32, GroupLayout.PREFERRED_SIZE)
					    .addGroup(GroupLayout.Alignment.LEADING, jPanel2Layout.createSequentialGroup()
					        .addGap(7)
					        .addComponent(jLabel1, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
					        .addGap(11)))
					.addGap(17)
					.addGroup(jPanel2Layout.createParallelGroup()
					    .addGroup(jPanel2Layout.createSequentialGroup()
					        .addGap(0, 0, Short.MAX_VALUE)
					        .addComponent(fillOpacitySlider, GroupLayout.PREFERRED_SIZE, 38, GroupLayout.PREFERRED_SIZE))
					    .addGroup(GroupLayout.Alignment.LEADING, jPanel2Layout.createSequentialGroup()
					        .addGap(10)
					        .addComponent(jLabel2, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
					        .addGap(14)))
					.addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
					.addGroup(jPanel2Layout.createParallelGroup()
					    .addComponent(sphOpacitySlider, GroupLayout.Alignment.LEADING, GroupLayout.PREFERRED_SIZE, 38, GroupLayout.PREFERRED_SIZE)
					    .addGroup(GroupLayout.Alignment.LEADING, jPanel2Layout.createSequentialGroup()
					        .addGap(0, 8, GroupLayout.PREFERRED_SIZE)
					        .addComponent(jLabel3, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
					        .addGap(16)))
					.addGroup(jPanel2Layout.createParallelGroup()
					    .addGroup(jPanel2Layout.createSequentialGroup()
					        .addGap(0, 0, Short.MAX_VALUE)
					        .addComponent(textSizeSlider, GroupLayout.PREFERRED_SIZE, 38, GroupLayout.PREFERRED_SIZE))
					    .addGroup(GroupLayout.Alignment.LEADING, jPanel2Layout.createSequentialGroup()
					        .addGap(10)
					        .addComponent(jLabel4, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
					        .addGap(14)))
					.addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
					.addContainerGap(26, 26));
			}
			{
				jPanel1 = new JPanel();
				GroupLayout jPanel1Layout = new GroupLayout((JComponent)jPanel1);
				jPanel1.setLayout(jPanel1Layout);
				{
					aliasCkBox = new JCheckBox();
					aliasCkBox.setText("Antialiasing");
					aliasCkBox.setToolTipText("Drawing quality (some speed cost)");
					aliasCkBox.setSelected(true);
					aliasCkBox.setAction(getAliasAction());
				}
				{
					jButton1 = new JButton();
					jButton1.setText("Reset defaults");
					jButton1.setAction(getResetAction());
				}
					jPanel1Layout.setHorizontalGroup(jPanel1Layout.createSequentialGroup()
					.addContainerGap(21, 21)
					.addGroup(jPanel1Layout.createParallelGroup()
					    .addComponent(aliasCkBox, GroupLayout.Alignment.LEADING, GroupLayout.PREFERRED_SIZE, 124, GroupLayout.PREFERRED_SIZE)
					    .addComponent(jButton1, GroupLayout.Alignment.LEADING, GroupLayout.PREFERRED_SIZE, 124, GroupLayout.PREFERRED_SIZE))
					.addContainerGap(22, 22));
					jPanel1Layout.setVerticalGroup(jPanel1Layout.createSequentialGroup()
					.addContainerGap()
					.addComponent(aliasCkBox, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
					.addPreferredGap(LayoutStyle.ComponentPlacement.RELATED, 0, Short.MAX_VALUE)
					.addComponent(jButton1, GroupLayout.PREFERRED_SIZE, 21, GroupLayout.PREFERRED_SIZE)
					.addContainerGap());
			}
			thisLayout.setVerticalGroup(thisLayout.createSequentialGroup()
				.addContainerGap()
				.addComponent(sliderPanel, GroupLayout.PREFERRED_SIZE, 200  // 144
						, GroupLayout.PREFERRED_SIZE)
				.addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
				.addComponent(jPanel1, GroupLayout.PREFERRED_SIZE, 65, GroupLayout.PREFERRED_SIZE));
			thisLayout.setHorizontalGroup(thisLayout.createSequentialGroup()
				.addContainerGap()
				.addGroup(thisLayout.createParallelGroup()
				    .addComponent(sliderPanel, GroupLayout.Alignment.LEADING, 0, 410 //371
				    		, Short.MAX_VALUE)
				    .addGroup(GroupLayout.Alignment.LEADING, thisLayout.createSequentialGroup()
				        .addComponent(jPanel1, GroupLayout.PREFERRED_SIZE, 167, GroupLayout.PREFERRED_SIZE)
				        .addGap(204)))
				.addGap(7));
//			this.setSize(396, 262);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void resetSliders() {
		lineSlider.setValue(CPBase.DEFAULT_LINETHICKNESS);
		fillOpacitySlider.setValue(CPBase.DEFAULT_FILL_OPACITY);
		sphOpacitySlider.setValue(CPBase.DEFAULT_SPHERE_OPACITY);
		textSizeSlider.setValue(CPBase.DEFAULT_INDEX_FONT.getSize());
	}
	
	/**
	 * Set line thickness in 'lineSlider', 1 to 12
	 * @param t, integer
	 */
	public void setLine(int t) {
		if (t>=0 && t<=12) 
			lineSlider.setValue(t);
	}
	
	/**
	 * Get line thickness (as set in 'lineSlider')
	 * @return integer (should be between 1 and 12)
	 */
	public int getLine() {
		return lineSlider.getValue();
	}
	
	public void setFillOp(int n) {
		if (n>=0 && n<256)
			fillOpacitySlider.setValue(n);
	}
	
	public void setSphOp(int n)  {
		if (n>=0 && n<256)
			sphOpacitySlider.setValue(n);
	}
	
	public void setFont(int t) {
		if (t>=1 && t<=30)
			textSizeSlider.setValue(t);
	}
	
	/**
	 * Values for the sliders are maintained in CPDrawing;
	 * this sets them, e.g., when active screen is changed.
	 */
	public void setSliders() {
		CPdrawing aP=PackControl.getActiveCPDrawing();
		
		setLine(aP.getLineThickness());
		setFillOp(aP.getFillOpacity());
		setSphOp(aP.getSphereOpacity());
		setFont(aP.getIndexFont().getSize());
	}
		
	private AbstractAction getResetAction() {
		if(resetAction == null) {
			resetAction = new AbstractAction("resetDefaults", null) {

				private static final long 
				serialVersionUID = 1L;

				public void actionPerformed(ActionEvent evt) {
					CPdrawing aP=PackControl.getActiveCPDrawing();
					aP.setLineThickness(CPBase.DEFAULT_LINETHICKNESS);
					aP.setFillOpacity(CPBase.DEFAULT_FILL_OPACITY);
					aP.setSphereOpacity(CPBase.DEFAULT_SPHERE_OPACITY);
					aP.setIndexFont(CPBase.DEFAULT_INDEX_FONT.getSize());
					resetSliders();
				}
			};
		}
		return resetAction;
	}
	
	private AbstractAction getAliasAction() {
		if(aliasAction == null) {
			aliasAction = new AbstractAction("antiAliasing", null) {

				private static final long 
				serialVersionUID = 1L;

				public void actionPerformed(ActionEvent evt) {
					if (aliasCkBox.isSelected()) PackControl.getActiveCPDrawing().setAntialiasing(true);
					else PackControl.getActiveCPDrawing().setAntialiasing(false);
				}
			};
		}
		return aliasAction;
	}
	
	/*
	 * stateChanged
	 * Use: Necessary to implement change listener interface. Listens for events
	 * on JSliders.
	 * see
	 * javax.swing.event.ChangeListener#stateChanged(javax.swing.event.ChangeEvent)
	 */
	public void stateChanged(ChangeEvent e) {
		String name = ((Component) e.getSource()).getName();
		JSlider slider = (JSlider) e.getSource();

		CPdrawing aP=PackControl.getActiveCPDrawing();

		if (name.equals("Line Thickness")) {
			int value = slider.getValue();
			aP.setLineThickness(value);

		} else if (name.equals("fillOpacitySlider")) {
			int value = slider.getValue();
			aP.setFillOpacity(value);
			
		} else if (name.equals("sphOpacitySlider")) {
			int value = slider.getValue();
			aP.setSphereOpacity(value);
		} 

		else if (name.equals("textSizeSlider")) {
			int value = slider.getValue();
			aP.setIndexFont(value);
		}
	}

}
