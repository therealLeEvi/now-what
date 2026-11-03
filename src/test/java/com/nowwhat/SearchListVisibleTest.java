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
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The item search's matches have to be on screen in the real panel, not just
 * in a picker tested on its own.
 * <p>
 * The picker grows when its list opens, but the block it sits in had its
 * height fixed when the panel was built - with the list closed - so typing
 * produced a list zero pixels tall: the field worked, and nothing showed
 * underneath it to say what it had found. SearchPickerTest measured the
 * picker by itself and could not see this.
 */
public class SearchListVisibleTest
{
	@Test
	public void typingShowsTheMatchesInsideThePanel() throws Exception
	{
		final int[] listHeight = {-1};
		SwingUtilities.invokeAndWait(() ->
		{
			NowWhatPanel panel = panel();
			SearchPicker<?> picker = find(panel, SearchPicker.class);
			assertNotNull("expected an item search in the panel", picker);

			// Show the "choose a goal" screen the picker lives on.
			for (Container c = picker.getParent(); c != null && c != panel; c = c.getParent())
			{
				c.setVisible(true);
			}
			panel.setSize(242, 3000);
			layOut(panel);

			find(picker, JTextField.class).setText("rune");
			layOut(panel);

			JScrollPane results = find(picker, JScrollPane.class);
			listHeight[0] = results.isVisible() ? results.getHeight() : 0;
		});

		assertTrue("the matches list is " + listHeight[0] + "px tall after typing",
			listHeight[0] >= 40);
	}

	/**
	 * What a real frame does after a revalidate: every level re-measures. A
	 * panel with no window around it gets no such pass, and BoxLayout keeps
	 * the sizes it measured before the typing.
	 */
	private static void layOut(Container root)
	{
		invalidateAll(root);
		doLayoutAll(root);
	}

	/** Top down, so each container lays out with its parent's final size. */
	private static void doLayoutAll(Container root)
	{
		root.doLayout();
		for (Component c : root.getComponents())
		{
			if (c instanceof Container)
			{
				doLayoutAll((Container) c);
			}
		}
	}

	private static void invalidateAll(Container root)
	{
		root.invalidate();
		for (Component c : root.getComponents())
		{
			if (c instanceof Container)
			{
				invalidateAll((Container) c);
			}
		}
	}

	@SuppressWarnings("unchecked")
	private static <T> T find(Container root, Class<T> type)
	{
		for (Component c : root.getComponents())
		{
			if (type.isInstance(c))
			{
				return (T) c;
			}
			if (c instanceof Container)
			{
				T found = find((Container) c, type);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
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
