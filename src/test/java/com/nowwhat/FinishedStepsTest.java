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
import java.util.Arrays;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JSpinner;
import javax.swing.SwingUtilities;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A rendered plan folds away the steps the player has since finished.
 * <p>
 * The plan used to be rendered once and then left alone while the player went
 * and did it, so a step they had completed stayed on the list and the only way
 * to reach the step they were on was to scroll past the ones they were not.
 */
public class FinishedStepsTest
{
	private static final List<String> ALL_SKILLS = Arrays.asList(
		"ATTACK", "DEFENCE", "STRENGTH", "HITPOINTS", "RANGED", "PRAYER", "MAGIC",
		"COOKING", "WOODCUTTING", "FLETCHING", "FISHING", "FIREMAKING", "CRAFTING",
		"SMITHING", "MINING", "HERBLORE", "AGILITY", "THIEVING", "SLAYER", "FARMING",
		"RUNECRAFT", "HUNTER", "CONSTRUCTION");

	@Test
	public void aStepThePlayerHasSinceFinishedFoldsAway() throws Exception
	{
		// Planned at 70 everywhere, so every skill is a step towards 80.
		PlayerState planning = new PlayerState();
		for (String skill : ALL_SKILLS)
		{
			planning.setSkillLevel(skill, 70);
		}

		final NowWhatPanel[] built = new NowWhatPanel[1];
		SwingUtilities.invokeAndWait(() ->
		{
			built[0] = panel(planning);
			planBaseStats(built[0], 80);
		});

		List<String> before = text(built[0]);
		assertTrue("expected a Prayer step in the plan: " + before,
			before.stream().anyMatch(line -> line.contains("Prayer 70")));
		assertFalse("nothing is finished yet, so nothing should offer to unfold: " + before,
			before.stream().anyMatch(line -> line.contains("done already")));

		// Now the player has Prayer 80 and nothing else has moved.
		java.util.Map<String, Integer> now = new java.util.HashMap<>();
		for (String skill : ALL_SKILLS)
		{
			now.put(skill, 70);
		}
		now.put("PRAYER", 80);
		SwingUtilities.invokeAndWait(() -> built[0].setProgress(now));

		List<String> after = text(built[0]);
		assertTrue("expected an offer to unfold the finished step: " + after,
			after.stream().anyMatch(line -> line.contains("done already")));
		assertFalse("the finished Prayer step should be folded away: " + after,
			after.stream().anyMatch(line -> line.contains("Prayer 70")));
		assertTrue("the unfinished steps should still be there: " + after,
			after.stream().anyMatch(line -> line.contains("Agility 70")));
	}

	/** Unfolding brings them back, marked as done rather than as work left. */
	@Test
	public void unfoldingShowsTheFinishedStepMarkedDone() throws Exception
	{
		PlayerState planning = new PlayerState();
		for (String skill : ALL_SKILLS)
		{
			planning.setSkillLevel(skill, 70);
		}

		final NowWhatPanel[] built = new NowWhatPanel[1];
		SwingUtilities.invokeAndWait(() ->
		{
			built[0] = panel(planning);
			planBaseStats(built[0], 80);
			java.util.Map<String, Integer> now = new java.util.HashMap<>();
			for (String skill : ALL_SKILLS)
			{
				now.put(skill, 70);
			}
			now.put("PRAYER", 80);
			built[0].setProgress(now);
			click(built[0], "done already");
		});

		List<String> after = text(built[0]);
		// The caption and the title are separate labels, and the caption is
		// drawn upper-case, so what the player reads is "STEP 8 . 1.2M XP . DONE"
		// above "Prayer 70 -> 80".
		assertTrue("expected a step captioned done: " + after,
			after.stream().anyMatch(line ->
				line.toLowerCase(java.util.Locale.ROOT).contains(" done")));
		assertTrue("expected the finished step back: " + after,
			after.stream().anyMatch(line -> line.contains("Prayer 70")));
	}

	private static void planBaseStats(NowWhatPanel panel, int level)
	{
		List<JSpinner> spinners = new ArrayList<>();
		findAll(panel, JSpinner.class, spinners);
		List<JButton> buttons = new ArrayList<>();
		findAll(panel, JButton.class, buttons);
		buttons.removeIf(b -> !"Plan path".equals(b.getText()));
		spinners.get(2).setValue(level);
		buttons.get(3).doClick();
	}

	/** Clicks the first label whose text contains {@code fragment}. */
	private static void click(NowWhatPanel panel, String fragment)
	{
		List<JLabel> labels = new ArrayList<>();
		findAll(panel, JLabel.class, labels);
		for (JLabel label : labels)
		{
			if (label.getText() != null && strip(label.getText()).contains(fragment))
			{
				for (java.awt.event.MouseListener listener : label.getMouseListeners())
				{
					listener.mouseClicked(new java.awt.event.MouseEvent(label,
						java.awt.event.MouseEvent.MOUSE_CLICKED, 0, 0, 1, 1, 1, false));
				}
				return;
			}
		}
		throw new AssertionError("no clickable label containing " + fragment);
	}

	private static List<String> text(NowWhatPanel panel)
	{
		List<JLabel> labels = new ArrayList<>();
		findAll(panel, JLabel.class, labels);
		List<String> lines = new ArrayList<>();
		for (JLabel label : labels)
		{
			if (label.getText() != null)
			{
				lines.add(strip(label.getText()));
			}
		}
		return lines;
	}

	/** Labels are HTML-wrapped for width, so the tags come back off to read them. */
	private static String strip(String html)
	{
		return html.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
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

	private static NowWhatPanel panel(PlayerState state)
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
