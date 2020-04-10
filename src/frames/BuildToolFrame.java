package frames;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;

public class BuildToolFrame extends JFrame {
	
	private static final long 
	serialVersionUID = 1L;

	private JPanel topPanel;
	private JPanel middlePanel;
	private JPanel bottomPanel;
	private JTextField tooltipField;
	private JPanel iconBoxPanel;
	private JButton clearButton;
	private JButton acceptButton;
	private JButton dismissButton;
	private JTextField nameField;
	private JCheckBox dropBox;
	private JLabel browseLabel;
	private JLabel ttLabel;
	private JLabel nameLabel;

	public BuildToolFrame() {
		super();
		initGUI();
	}
	
	private void initGUI() {
		try {
			BoxLayout thisLayout = new BoxLayout(getContentPane(), javax.swing.BoxLayout.Y_AXIS);
			getContentPane().setLayout(thisLayout);
			setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
			{
				topPanel = new JPanel();
				getContentPane().add(topPanel);
			}
			{
				middlePanel = new JPanel();
				getContentPane().add(middlePanel);
				middlePanel.setLayout(null);
				middlePanel.setBorder(BorderFactory.createTitledBorder("Standard items (optional)"));
				middlePanel.setPreferredSize(new java.awt.Dimension(611, 102));
				{
					nameLabel = new JLabel();
					nameLabel.setText("Name");
					nameLabel.setHorizontalTextPosition(SwingConstants.LEADING);
					nameLabel.setHorizontalAlignment(SwingConstants.RIGHT);
				}
				{
					ttLabel = new JLabel();
					ttLabel.setText("Tool Tip");
					ttLabel.setHorizontalAlignment(SwingConstants.RIGHT);
				}
				{
					iconBoxPanel = new JPanel();
				}
				{
					browseLabel = new JLabel();
					middlePanel.add(browseLabel);
					middlePanel.add(iconBoxPanel);
					iconBoxPanel.setBounds(535, 14, 59, 39);
					browseLabel.setText("Browse Icons");
					browseLabel.setHorizontalAlignment(SwingConstants.RIGHT);
					browseLabel.setBounds(400, 21, 128, 15);
				}
				{
					dropBox = new JCheckBox();
					dropBox.setText("Dropable?");
				}
				{
					nameField = new JTextField();
					middlePanel.add(nameField);
					middlePanel.add(dropBox);
					dropBox.setBounds(287, 19, 113, 20);
					nameField.setBounds(107, 20, 148, 20);
				}
				{
					tooltipField = new JTextField();
					middlePanel.add(tooltipField);
					middlePanel.add(nameLabel);
					middlePanel.add(ttLabel);
					ttLabel.setBounds(12, 49, 87, 16);
					nameLabel.setBounds(12, 22, 83, 16);
					tooltipField.setBounds(107, 47, 422, 21);
				}
			}
			{
				bottomPanel = new JPanel();
				getContentPane().add(bottomPanel);
				bottomPanel.setPreferredSize(new java.awt.Dimension(619, 40));
				{
					acceptButton = new JButton();
					bottomPanel.add(acceptButton);
					acceptButton.setText("Accept");
					acceptButton.setPreferredSize(new java.awt.Dimension(96, 21));
				}
				{
					dismissButton = new JButton();
					bottomPanel.add(dismissButton);
					dismissButton.setText("Dismiss");
					dismissButton.setPreferredSize(new java.awt.Dimension(91, 21));
				}
				{
					clearButton = new JButton();
					bottomPanel.add(clearButton);
					clearButton.setText("Clear");
					clearButton.setPreferredSize(new java.awt.Dimension(88, 21));
				}
			}
			pack();
			this.setSize(621, 190);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
}
