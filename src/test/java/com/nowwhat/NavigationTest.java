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
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * Getting back out of a screen.
 * <p>
 * The goal form had no way back to the opening fork, so picking "Plan a path"
 * once removed the random suggestion for the rest of the session - the half of
 * the panel for a player with nothing particular in mind became unreachable by
 * using the other half.
 */
public class NavigationTest
{
	@Test
	public void theGoalFormOffersTheRollAndTheWayBack() throws Exception
	{
		final List<String> onScreen = new ArrayList<>();
		SwingUtilities.invokeAndWait(() ->
		{
			NowWhatPanel panel = panel();
			click(panel, "Plan a path");
			onScreen.addAll(visibleText(panel));
		});

		assertTrue("expected the goal form to offer a roll: " + onScreen,
			onScreen.stream().anyMatch(line -> line.contains("Surprise me")));
		assertTrue("expected the goal form to offer a way back: " + onScreen,
			onScreen.stream().anyMatch(line -> line.contains("Back")));
	}

	@Test
	public void theRollIsReachableFromTheGoalForm() throws Exception
	{
		final List<String> rolled = new ArrayList<>();
		SwingUtilities.invokeAndWait(() ->
		{
			NowWhatPanel panel = panel();
			click(panel, "Plan a path");
			click(panel, "Surprise me");
			rolled.addAll(visibleText(panel));
		});

		// Logged out, the roll draws from the whole catalogue and says so.
		assertTrue("expected a rolled suggestion: " + rolled,
			rolled.stream().anyMatch(line -> line.contains("Roll again")));
	}

	private static void click(Container root, String fragment)
	{
		for (JLabel label : labels(root, true))
		{
			if (strip(label.getText()).contains(fragment) && label.getMouseListeners().length > 0)
			{
				for (java.awt.event.MouseListener listener : label.getMouseListeners())
				{
					listener.mouseClicked(new java.awt.event.MouseEvent(label,
						java.awt.event.MouseEvent.MOUSE_CLICKED, 0, 0, 1, 1, 1, false));
				}
				return;
			}
		}
		throw new AssertionError("nothing clickable reading " + fragment
			+ " on screen; found " + visibleText(root));
	}

	private static List<String> visibleText(Container root)
	{
		List<String> lines = new ArrayList<>();
		for (JLabel label : labels(root, true))
		{
			lines.add(strip(label.getText()));
		}
		return lines;
	}

	/**
	 * Labels under containers that are actually showing. {@code isVisible} is
	 * what the panel switches between screens, and a test that reads hidden
	 * containers would pass whatever screen was on.
	 */
	private static List<JLabel> labels(Container root, boolean visibleOnly)
	{
		List<JLabel> found = new ArrayList<>();
		collect(root, visibleOnly, found);
		return found;
	}

	private static void collect(Container container, boolean visibleOnly, List<JLabel> found)
	{
		for (Component child : container.getComponents())
		{
			if (visibleOnly && !child.isVisible())
			{
				continue;
			}
			if (child instanceof JLabel && ((JLabel) child).getText() != null)
			{
				found.add((JLabel) child);
			}
			if (child instanceof Container)
			{
				collect((Container) child, visibleOnly, found);
			}
		}
	}

	private static String strip(String html)
	{
		return html == null ? "" : html.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
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
