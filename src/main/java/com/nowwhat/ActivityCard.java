package com.nowwhat;

import static com.nowwhat.PanelStyle.*;

import com.nowwhat.model.Activity;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;

/**
 * A single clickable suggestion row: name on top, category underneath.
 * Stacked rather than side-by-side because two labels sharing a 225px row
 * is what forced the panel wider than the sidebar.
 */
/**
 * A suggestion in the "Almost there" list. Clicking one plans the path to it
 * rather than opening its wiki page: the list is the one place the panel
 * volunteers something you didn't ask for, and sending that straight out to
 * the browser wasted it.
 */
/**
 * One suggestion in the "almost there" list.
 * <p>
 * Not a card any more. A boxed, filled panel was the one thing left in the
 * sidebar still drawing chrome around its content, and it sat directly under
 * the new opening screen, where the difference was impossible to miss. It
 * reads like a step now: a rule, a caption, a name - the same three parts in
 * the same order, so the eye learns one pattern instead of two.
 */
class ActivityCard extends JPanel
{
	ActivityCard(Activity activity, String missing, int wrapWidth, Consumer<Activity> onSelect)
	{
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(new EmptyBorder(0, 0, 8, 0));
		setAlignmentX(Component.LEFT_ALIGNMENT);

		JPanel rule = new JPanel();
		rule.setBackground(HAIRLINE);
		rule.setAlignmentX(Component.LEFT_ALIGNMENT);
		rule.setPreferredSize(new Dimension(10, 1));
		rule.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
		add(rule);

		JLabel category = new JLabel(activity.getCategory().name()
			.replace('_', ' '));
		category.setForeground(SUBTLE);
		category.setFont(MICRO_FONT);
		category.setBorder(new EmptyBorder(8, 0, 1, 0));
		category.setAlignmentX(Component.LEFT_ALIGNMENT);
		add(category);

		JLabel title = new JLabel(wrapped(
			activity.getName().replace("&", "&amp;").replace("<", "&lt;"), wrapWidth));
		title.setForeground(PRIMARY);
		title.setFont(OPTION_FONT);
		title.setAlignmentX(Component.LEFT_ALIGNMENT);
		add(title);

		if (missing != null)
		{
			JLabel why = new JLabel(wrapped(
				missing.replace("&", "&amp;").replace("<", "&lt;"), wrapWidth));
			why.setForeground(DETAIL);
			why.setFont(SMALL_FONT);
			why.setBorder(new EmptyBorder(2, 0, 0, 0));
			why.setAlignmentX(Component.LEFT_ALIGNMENT);
			add(why);
		}

		setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));

		setToolTipText("Click to plan a path to " + activity.getName());
		setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				onSelect.accept(activity);
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				title.setForeground(ACCENT);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				title.setForeground(PRIMARY);
			}
		});
	}
}
