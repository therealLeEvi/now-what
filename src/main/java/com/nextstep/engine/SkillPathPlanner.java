package com.nextstep.engine;

import com.nextstep.model.PathStep;
import com.nextstep.model.TrainingMethod;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

/**
 * Given a skill's bundled TrainingMethods and a [fromLevel, toLevel) range,
 * produces an ordered list of TRAIN_SKILL steps.
 * <p>
 * Two design choices worth calling out:
 * <p>
 * <b>Multiple alternatives within one intensity.</b> A segment is never
 * collapsed to a single "best" method - Mining 45+ with normal methods
 * genuinely has several different valid options (Motherlode Mine, Blast
 * Mine, gem rocks, calcified rocks) at different xp/hr with different
 * trade-offs, and the planner has no basis to pick a winner among them. All
 * of them are surfaced as {@link PathStep.Option}s, sorted by xp/hr as a
 * hint but never pruned to one.
 * <p>
 * <b>Exclusive intensity selection, not additive.</b> `preferTickManipulation`
 * picks ONE intensity band per segment rather than showing both together -
 * mixing a tick-manip option into a list of normal ones is clutter for
 * someone who already said they don't want it, and vice versa. The only
 * fallback is one-directional: asking for tick-manipulation falls back to
 * normal methods where none is recorded (since intensity is a preference,
 * not a hard requirement), but asking for normal never falls back to
 * tick-manipulation - that would defeat the entire point of choosing it.
 */
public class SkillPathPlanner
{
	public List<PathStep> plan(String skill, int fromLevel, int toLevel, List<TrainingMethod> methods,
		boolean preferTickManipulation, List<String> warningsOut)
	{
		List<PathStep> steps = new ArrayList<>();
		if (fromLevel >= toLevel)
		{
			return steps;
		}

		if (methods.isEmpty())
		{
			warningsOut.add("No training method data for " + skill + " \u2014 check the wiki's training guide manually.");
			return steps;
		}

		// Breakpoints come from ALL methods regardless of intensity, so segment
		// boundaries are identical whichever preference is chosen - only which
		// methods populate each segment's options differs.
		TreeSet<Integer> breakpoints = new TreeSet<>();
		breakpoints.add(fromLevel);
		breakpoints.add(toLevel);
		for (TrainingMethod method : methods)
		{
			if (method.getMinLevel() > fromLevel && method.getMinLevel() < toLevel)
			{
				breakpoints.add(method.getMinLevel());
			}
			if (method.getMaxLevel() > fromLevel && method.getMaxLevel() < toLevel)
			{
				breakpoints.add(method.getMaxLevel());
			}
		}

		Integer[] points = breakpoints.toArray(new Integer[0]);

		// Two passes. The first resolves each raw segment to the methods that
		// survive the intensity preference; the second merges neighbours that
		// resolved to the *same* methods before emitting steps.
		//
		// The merge matters because breakpoints come from every method regardless
		// of intensity (so the boundaries don't shift when the preference does).
		// A method bracketed finely by level - 1.5-tick teaks, which run from 93k
		// at 35 to 222k at 90 - therefore splits the *normal* plan at 41, 50, 61,
		// 71, 80 and 90 too, where nothing about the normal options changes. That
		// produced consecutive steps reading "Teak trees" and then "Teak trees".
		List<Segment> segments = new ArrayList<>();
		for (int i = 0; i < points.length - 1; i++)
		{
			int segmentStart = points[i];
			int segmentEnd = points[i + 1];
			if (segmentStart >= segmentEnd)
			{
				continue;
			}

			List<TrainingMethod> tickCovering = new ArrayList<>();
			List<TrainingMethod> normalCovering = new ArrayList<>();
			for (TrainingMethod method : methods)
			{
				if (method.getMinLevel() <= segmentStart && method.getMaxLevel() >= segmentEnd)
				{
					(method.isTickManipulation() ? tickCovering : normalCovering).add(method);
				}
			}

			List<TrainingMethod> chosen;
			boolean fellBack = false;
			if (preferTickManipulation)
			{
				if (!tickCovering.isEmpty())
				{
					chosen = tickCovering;
				}
				else
				{
					chosen = normalCovering;
					fellBack = !normalCovering.isEmpty();
				}
			}
			else
			{
				// Normal preferred: never falls back to tick-manipulation, even
				// if that's the only thing covering this segment.
				chosen = normalCovering;
			}

			if (chosen.isEmpty())
			{
				String note = "No training method covers " + skill + " level " + segmentStart + "\u2013" + segmentEnd
					+ " \u2014 gap in the bundled data.";
				if (!preferTickManipulation && !tickCovering.isEmpty())
				{
					note += " (a tick-manipulation method exists here but is excluded by your preference)";
				}
				warningsOut.add(note);
				continue;
			}

			chosen = new ArrayList<>(chosen);
			// Plain xp/hr order, rate-less methods last. A quest route ranking
			// first would make it the headline for its whole range, which turns
			// "here is a shortcut" into "go and quest" - the panel keeps such an
			// option visible instead of promoting it (see renderStep).
			chosen.sort(Comparator.comparingInt(this::rate).reversed());

			Segment previous = segments.isEmpty() ? null : segments.get(segments.size() - 1);
			if (previous != null && previous.end == segmentStart
				&& previous.fellBack == fellBack && previous.methods.equals(chosen))
			{
				previous.end = segmentEnd;
			}
			else
			{
				segments.add(new Segment(segmentStart, segmentEnd, chosen, fellBack));
			}

			if (segments.size() > 200)
			{
				warningsOut.add("Aborted planning " + skill + " after 200 segments \u2014 check methods.json for overlapping/malformed ranges.");
				break;
			}
		}

		for (Segment segment : segments)
		{
			if (segment.fellBack)
			{
				warningsOut.add("No tick-manipulation method recorded for " + skill + " "
					+ segment.start + "\u2013" + segment.end + " \u2014 showing normal methods instead.");
			}

			long segmentXp = XpTable.xpBetween(segment.start, segment.end);

			List<PathStep.Option> options = new ArrayList<>();
			for (TrainingMethod method : segment.methods)
			{
				// Compact rather than fully separated: at sidebar width a label
				// like "137,000" gets wrapped after the comma, splitting the
				// number across two lines.
				// The name alone. Rate, time, intensity and Wilderness exposure all
				// travel beside it as data now, so the panel can lay them out rather
				// than hand the player one long wrapped sentence to read back.
				options.add(new PathStep.Option(
					method.getName(),
					method.getNotes(),
					timeEstimate(method, segmentXp),
					method.getWikiUrl(),
					method.getQuests(),
					method.isPvpRisk(),
					method.isTickManipulation(),
					method.getXpPerHour(),
					method.getGear(),
					method.getSupplies()
				));
			}

			// No "(choose one)" here: whether a choice is still open depends on
			// what the player has already picked, and only the panel knows that.
			// Baked into the title, a collapsed step went on saying "choose one"
			// after they had chosen.
			String title = capitalize(skill) + " " + segment.start + " \u2192 " + segment.end ;
			steps.add(new PathStep(PathStep.Type.TRAIN_SKILL, title, options)
				.withXpCost(segmentXp)
				.withLevels(skill, segment.start, segment.end));
		}

		return steps;
	}

	/** A level range that resolved to one particular set of methods. */
	private static final class Segment
	{
		private final int start;
		private int end;
		private final List<TrainingMethod> methods;
		private final boolean fellBack;

		private Segment(int start, int end, List<TrainingMethod> methods, boolean fellBack)
		{
			this.start = start;
			this.end = end;
			this.methods = methods;
			this.fellBack = fellBack;
		}
	}

	/**
	 * The wall-clock estimate for this method over this segment, as its own
	 * short line. How long a segment takes is what a player actually compares
	 * methods on, and it cannot be read off an xp/hr figure without doing the
	 * arithmetic - but buried at the end of a three-clause note it may as well
	 * not be there.
	 *
	 * @return null for a method with no hourly rate, such as a quest route.
	 */
	private String timeEstimate(TrainingMethod method, long segmentXp)
	{
		Integer xpPerHour = method.getXpPerHour();
		if (xpPerHour == null || xpPerHour <= 0 || segmentXp <= 0)
		{
			return null;
		}
		return "~" + XpTable.formatHours(segmentXp / (double) xpPerHour)
			+ " \u00b7 " + XpTable.formatXp(xpPerHour) + " xp/hr";
	}

	private int rate(TrainingMethod method)
	{
		return method.getXpPerHour() != null ? method.getXpPerHour() : -1;
	}

	private String capitalize(String s)
	{
		if (s.isEmpty())
		{
			return s;
		}
		return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
	}
}
