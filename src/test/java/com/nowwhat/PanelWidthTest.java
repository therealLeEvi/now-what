package com.nowwhat;

import com.google.gson.Gson;
import com.nowwhat.data.ActivityDatabase;
import com.nowwhat.data.ItemSourceDatabase;
import com.nowwhat.data.TrainingMethodDatabase;
import com.nowwhat.engine.PathPlanner;
import com.nowwhat.engine.PrerequisiteResolver;
import com.nowwhat.model.PlayerState;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.PluginPanel;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Everything the panel scrolls has {@link PluginPanel#PANEL_WIDTH} to live in
 * and never gets more. Horizontal scrolling is switched off, so a component
 * that asks for more does not get a scrollbar to reach it - it overflows the
 * viewport and whatever sits past the right edge is cut off, silently.
 * <p>
 * The check runs inside the scroll pane's viewport, not on the panel itself:
 * PluginPanel is deliberately PANEL_WIDTH + SCROLLBAR_WIDTH wide on the
 * outside, so the panel reporting 242px is correct and the viewport is the
 * 225px that content actually gets.
 * <p>
 * Swing reports a preferred width from content, so any new label, row or button
 * can reintroduce the overflow. This is the guard, not a one-time fix.
 */
public class PanelWidthTest
{
	@Test
	public void nothingInThePanelAsksToBeWiderThanTheSidebar() throws Exception
	{
		final NowWhatPanel[] built = new NowWhatPanel[1];
		SwingUtilities.invokeAndWait(() -> built[0] = panel());
		NowWhatPanel panel = built[0];

		JViewport viewport = viewportOf(panel);
		assertNotNull("expected the panel's content to live in a scroll pane", viewport);

		List<String> offenders = new ArrayList<>();
		// Only the deepest offender is reported: a parent is wide because its
		// child is, and naming the parent sends you looking in the wrong file.
		collect(viewport.getView(), describe(viewport.getView()), offenders);

		assertTrue("these components ask to be wider than the " + PluginPanel.PANEL_WIDTH
			+ "px sidebar:\n  " + String.join("\n  ", offenders), offenders.isEmpty());
	}

	/**
	 * The same check over a rendered plan, which is where the panel's widest
	 * content lives - step cards, option rows, shopping-list quantities and
	 * warning text. The empty state passing says nothing about those.
	 */
	@Test
	public void nothingInARenderedPlanAsksToBeWiderThanTheSidebar() throws Exception
	{
		final List<String> offenders = new ArrayList<>();
		SwingUtilities.invokeAndWait(() ->
		{
			NowWhatPanel panel = panel();
			plan(panel, "WOODCUTTING", 99);
			Component view = viewportOf(panel).getView();
			// Without this the test passes by rendering nothing at all, which is
			// the one way a width check can be green and worthless.
			assertTrue("expected the click to render a plan", rendersAStep(view));
			collect(view, describe(view), offenders);
		});

		assertTrue("these components ask to be wider than the " + PluginPanel.PANEL_WIDTH
			+ "px sidebar:\n  " + String.join("\n  ", offenders), offenders.isEmpty());
	}

	private static boolean rendersAStep(Component component)
	{
		if (component instanceof JLabel)
		{
			String text = ((JLabel) component).getText();
			if (text != null && text.contains("STEP"))
			{
				return true;
			}
		}
		if (component instanceof Container)
		{
			for (Component child : ((Container) component).getComponents())
			{
				if (rendersAStep(child))
				{
					return true;
				}
			}
		}
		return false;
	}

	/** Drives the skill goal row the way a player would, then plans it. */
	private static void plan(NowWhatPanel panel, String skill, int level)
	{
		List<JComboBox> combos = new ArrayList<>();
		findAll(panel, JComboBox.class, combos);
		List<JSpinner> spinners = new ArrayList<>();
		findAll(panel, JSpinner.class, spinners);
		List<JButton> planButtons = new ArrayList<>();
		findAll(panel, JButton.class, planButtons);
		planButtons.removeIf(b -> !"Plan path".equals(b.getText()));

		combos.get(1).setSelectedItem(skill);
		spinners.get(0).setValue(level);
		planButtons.get(1).doClick();
	}

	@SuppressWarnings("unchecked")
	private static <T> void findAll(Container root, Class<T> type, List<T> acc)
	{
		for (Component c : root.getComponents())
		{
			if (type.isInstance(c))
			{
				acc.add((T) c);
			}
			if (c instanceof Container)
			{
				findAll((Container) c, type, acc);
			}
		}
	}

	private static JViewport viewportOf(Container container)
	{
		for (Component child : container.getComponents())
		{
			if (child instanceof JScrollPane)
			{
				return ((JScrollPane) child).getViewport();
			}
			if (child instanceof Container)
			{
				JViewport found = viewportOf((Container) child);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	private static void collect(Component component, String path, List<String> offenders)
	{
		int width = component.getPreferredSize().width;
		boolean blamedAChild = false;
		if (component instanceof Container)
		{
			for (Component child : ((Container) component).getComponents())
			{
				int before = offenders.size();
				collect(child, path + " > " + describe(child), offenders);
				blamedAChild |= offenders.size() > before;
			}
		}
		if (width > PluginPanel.PANEL_WIDTH && !blamedAChild)
		{
			offenders.add(width + "px: " + path);
		}
	}

	private static String describe(Component component)
	{
		String name = component.getClass().getSimpleName();
		if (name.isEmpty())
		{
			name = component.getClass().getName();
		}
		if (component instanceof JLabel)
		{
			String text = ((JLabel) component).getText();
			if (text != null && !text.isEmpty())
			{
				name += "(\"" + (text.length() > 60 ? text.substring(0, 60) + "..." : text) + "\")";
			}
		}
		return name;
	}

	private static NowWhatPanel panel()
	{
		try
		{
			Gson gson = new Gson();
			ActivityDatabase ad = new ActivityDatabase(gson);
			ad.load();
			TrainingMethodDatabase tm = new TrainingMethodDatabase(gson);
			tm.load(ad.getAllActivities());
			ItemSourceDatabase is = new ItemSourceDatabase(gson);
			is.load();
			PathPlanner planner = new PathPlanner(ad.getAllActivities(),
				new PrerequisiteResolver(ad.getAllActivities()), tm, is);
			PlayerState state = new PlayerState();
			NowWhatConfig config = new NowWhatConfig()
			{
			};
			return new NowWhatPanel(config, ad.getAllActivities(), is.getAllSources(),
				goal -> planner.plan(goal, state));
		}
		catch (RuntimeException e)
		{
			throw e;
		}
		catch (Exception e)
		{
			throw new RuntimeException(e);
		}
	}
}
