package com.nextstep;

import com.google.gson.Gson;
import com.nextstep.data.ActivityDatabase;
import com.nextstep.data.ItemSourceDatabase;
import com.nextstep.data.TrainingMethodDatabase;
import com.nextstep.engine.PathPlanner;
import com.nextstep.engine.PrerequisiteResolver;
import com.nextstep.model.PlayerState;
import java.awt.Component;
import java.awt.Container;
import java.awt.FontMetrics;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JFormattedTextField;
import javax.swing.JFrame;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.PluginPanel;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * The goal rows, laid out at real sidebar width.
 * <p>
 * Every bug these cover was a size pinned from the wrong thing - a row told to
 * be as tall as the caption in the middle of it, a spinner told to be 56px wide
 * whatever it had to show. Nothing throws when that happens; a digit just loses
 * its top half, and only a player looking at it can tell.
 */
public class GoalRowTest
{
	@Test
	public void everySpinnerGetsTheHeightItAsksFor() throws Exception
	{
		List<String> squeezed = new ArrayList<>();
		forEachSpinner((spinner, frame) ->
		{
			int want = spinner.getPreferredSize().height;
			if (spinner.getHeight() < want)
			{
				squeezed.add(describe(spinner) + ": wants " + want + "px tall, got "
					+ spinner.getHeight());
			}
		});
		assertTrue("digits get clipped in a spinner shorter than it asked to be:\n  "
			+ String.join("\n  ", squeezed), squeezed.isEmpty());
	}

	/**
	 * A total level goes to 2376 and a spinner has to be able to show it. Four
	 * digits did not fit the fixed width this replaced, so the last one sat
	 * under the arrow box.
	 */
	@Test
	public void everySpinnerCanShowItsLargestValue() throws Exception
	{
		List<String> cramped = new ArrayList<>();
		forEachSpinner((spinner, frame) ->
		{
			if (!(spinner.getModel() instanceof SpinnerNumberModel)
				|| !(spinner.getEditor() instanceof JSpinner.DefaultEditor))
			{
				return;
			}
			Comparable<?> max = ((SpinnerNumberModel) spinner.getModel()).getMaximum();
			if (!(max instanceof Number))
			{
				return;
			}
			String biggest = String.valueOf(((Number) max).longValue());
			JFormattedTextField text =
				((JSpinner.DefaultEditor) spinner.getEditor()).getTextField();
			FontMetrics metrics = text.getFontMetrics(text.getFont());
			Insets insets = text.getInsets();
			int room = text.getWidth() - insets.left - insets.right;
			int needs = metrics.stringWidth(biggest);
			if (room < needs)
			{
				cramped.add(describe(spinner) + ": \"" + biggest + "\" needs " + needs
					+ "px, the field has " + room);
			}
		});
		assertTrue("a spinner cannot show its own maximum:\n  " + String.join("\n  ", cramped),
			cramped.isEmpty());
	}

	private interface SpinnerCheck
	{
		void check(JSpinner spinner, JFrame frame);
	}

	/** Lays the panel out in a real frame at sidebar width, then checks each spinner. */
	private static void forEachSpinner(SpinnerCheck check) throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			NextStepPanel panel = panel();
			JFrame frame = new JFrame();
			try
			{
				frame.add(panel);
				frame.setSize(PluginPanel.PANEL_WIDTH + PluginPanel.SCROLLBAR_WIDTH, 1400);
				frame.setVisible(true);
				frame.validate();

				List<JSpinner> spinners = new ArrayList<>();
				findAll(panel, JSpinner.class, spinners);
				assertTrue("expected the goal rows to have spinners", spinners.size() >= 4);
				for (JSpinner spinner : spinners)
				{
					check.check(spinner, frame);
				}
			}
			finally
			{
				frame.dispose();
			}
		});
	}

	private static String describe(JSpinner spinner)
	{
		Object max = spinner.getModel() instanceof SpinnerNumberModel
			? ((SpinnerNumberModel) spinner.getModel()).getMaximum()
			: "?";
		return "spinner(max=" + max + ")";
	}

	@SuppressWarnings("unchecked")
	private static <T> void findAll(Container root, Class<T> type, List<T> acc)
	{
		for (Component child : root.getComponents())
		{
			if (type.isInstance(child))
			{
				acc.add((T) child);
			}
			if (child instanceof Container)
			{
				findAll((Container) child, type, acc);
			}
		}
	}

	private static NextStepPanel panel()
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
			NextStepConfig config = new NextStepConfig()
			{
			};
			return new NextStepPanel(config, ad.getAllActivities(), is.getAllSources(),
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
