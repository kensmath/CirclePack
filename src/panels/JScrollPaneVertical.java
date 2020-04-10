package panels;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.LayoutManager;

import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneLayout;

public class JScrollPaneVertical extends JScrollPane {

	private static final long 
		serialVersionUID = 1L;
	
	private JComponent
		view = null;

	public JScrollPaneVertical(JComponent view, int vsbPolicy) {
		super(view, vsbPolicy, HORIZONTAL_SCROLLBAR_NEVER);
		setLayout(new MyScrollLayout());
		this.view = view;
	}


	public JScrollPaneVertical(JComponent view) {
		super(view);
		setLayout(new MyScrollLayout());
		setHorizontalScrollBarPolicy(HORIZONTAL_SCROLLBAR_NEVER);
		this.view = view;
	}


	private class MyScrollLayout extends ScrollPaneLayout {

		private static final long 
			serialVersionUID = 1L;

		public void layoutContainer(Container parent) {
			if (view != null) {
				view.setPreferredSize(getViewport().getSize());
				LayoutManager layout = view.getLayout();
				layout.layoutContainer(view);
				int height = 0;
				for (Component c : view.getComponents()) {
					int tmpHeight = c.getBounds().y + c.getBounds().height;
					if (height < tmpHeight)
						height = tmpHeight;
				}
				view.setPreferredSize(new Dimension(getViewport().getWidth(), height));
			}
			super.layoutContainer(parent);
		}
		
	}
	
}
