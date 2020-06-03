package widgets;

/*
   This program is a part of the companion code for Core Java 8th ed.
   (http://horstmann.com/corejava)

   This program is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   This program is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.util.Dictionary;
import java.util.Hashtable;

import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import util.xNumField;

/**
 * @version 1.13 2007-06-12
 * @author Cay Horstmann
 */
public class MultiSlider {
	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				SliderTestFrame frame = new SliderTestFrame();
				frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
				frame.setVisible(true);
			}
		});
	}
}

/**
 * A frame with many sliders and a text field to show slider values.
 */
class SliderTestFrame extends JFrame {
	private static final long serialVersionUID = 1L;

	public SliderTestFrame() {
		setTitle("SliderTest");
		setSize(DEFAULT_WIDTH, DEFAULT_HEIGHT);

		sliderPanel = new JPanel();
		BoxLayout bl=new BoxLayout(sliderPanel,BoxLayout.PAGE_AXIS);
		sliderPanel.setLayout(bl);
		sliderPanel.setAlignmentX(0);
//		sliderPanel.setLayout(); // new FlowLayout(FlowLayout.LEFT));

		// common listener for all sliders
		listener = new ChangeListener() {
			public void stateChanged(ChangeEvent event) {
				// update text field when the slider value changes
				JSlider source = (JSlider) event.getSource();
				textField.setText("" + source.getValue());
			}
		};

		// add a plain slider

		JSlider slider = new JSlider();
		addSlider(slider, "Plain");

		// add a slider with major and minor ticks

		slider = new JSlider();
		slider.setPaintTicks(true);
		slider.setMajorTickSpacing(20);
		slider.setMinorTickSpacing(5);
		addSlider(slider, "Ticks");

		// add a slider that snaps to ticks

		slider = new JSlider();
		slider.setPaintTicks(true);
		slider.setSnapToTicks(true);
		slider.setMajorTickSpacing(20);
		slider.setMinorTickSpacing(5);
		addSlider(slider, "Snap to ticks");

		// add a slider with no track

		slider = new JSlider();
		slider.setPaintTicks(true);
		slider.setMajorTickSpacing(20);
		slider.setMinorTickSpacing(5);
		slider.setPaintTrack(false);
		addSlider(slider, "No track");

		// add an inverted slider

		slider = new JSlider();
		slider.setPaintTicks(true);
		slider.setMajorTickSpacing(20);
		slider.setMinorTickSpacing(5);
		slider.setInverted(true);
		addSlider(slider, "Inverted");

		// add a slider with numeric labels

		slider = new JSlider();
		slider.setPaintTicks(true);
		slider.setPaintLabels(true);
		slider.setMajorTickSpacing(20);
		slider.setMinorTickSpacing(5);
		addSlider(slider, "Labels");

		// add a slider with alphabetic labels

		slider = new JSlider();
		slider.setPaintLabels(true);
		slider.setPaintTicks(true);
		slider.setMajorTickSpacing(20);
		slider.setMinorTickSpacing(5);

		Dictionary<Integer, Component> labelTable = new Hashtable<Integer, Component>();
		labelTable.put(0, new JLabel("A"));
		labelTable.put(20, new JLabel("B"));
		labelTable.put(40, new JLabel("C"));
		labelTable.put(60, new JLabel("D"));
		labelTable.put(80, new JLabel("E"));
		labelTable.put(100, new JLabel("F"));

		slider.setLabelTable(labelTable);
		addSlider(slider, "Custom labels");

		// add a slider with icon labels

		slider = new JSlider();
		slider.setPaintTicks(true);
		slider.setPaintLabels(true);
		slider.setSnapToTicks(true);
		slider.setMajorTickSpacing(20);
		slider.setMinorTickSpacing(20);

		labelTable = new Hashtable<Integer, Component>();

		// add card images

		labelTable.put(0, new JLabel(new ImageIcon("nine.gif")));
		labelTable.put(20, new JLabel(new ImageIcon("ten.gif")));
		labelTable.put(40, new JLabel(new ImageIcon("jack.gif")));
		labelTable.put(60, new JLabel(new ImageIcon("queen.gif")));
		labelTable.put(80, new JLabel(new ImageIcon("king.gif")));
		labelTable.put(100, new JLabel(new ImageIcon("ace.gif")));

		slider.setLabelTable(labelTable);
		addSlider(slider, "Icon labels");

		// add the text field that displays the slider value

		JScrollPane barScroll=new JScrollPane(sliderPanel);
		add(barScroll, BorderLayout.CENTER);

		textField = new JTextField();
		add(textField, BorderLayout.SOUTH);
	}

	/**
	 * Adds a slider to the slider panel and hooks up the listener
	 * 
	 * @param s           the slider
	 * @param description the slider description
	 */
	public void addSlider(JSlider s, String description) {
		s.addChangeListener(listener);
		JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
//		panel.setBorder(new LineBorder(Color.BLUE));
		JLabel jlb=new JLabel(description);
		panel.add(jlb);
		panel.add(s);
		panel.add(new xNumField("",10));
		sliderPanel.add(panel);
	}

	public static final int DEFAULT_WIDTH = 650;
	public static final int DEFAULT_HEIGHT = 450;

	private JPanel sliderPanel;
	private JTextField textField;
	private ChangeListener listener;
}
