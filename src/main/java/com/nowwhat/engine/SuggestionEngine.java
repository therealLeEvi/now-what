package com.nowwhat.engine;

import com.nowwhat.model.Activity;
import com.nowwhat.model.CombatAchievementTier;
import com.nowwhat.model.PlayerState;
import com.nowwhat.model.Requirement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Filters and ranks activities for a given player.
 * <p>
 * Deliberately kept free of any RuneLite/Client dependency so it can be
 * unit tested with a hand-built PlayerState and doesn't need a running
 * game client.
 */
public class SuggestionEngine
{
	/**
	 * Returns activities the player is NOT yet done with, but for which every
	 * requirement is currently met (i.e. "you can do this right now"),
	 * sorted by priority descending.
	 */
	public List<Activity> getAvailableNow(List<Activity> allActivities, PlayerState state)
	{
		return getAvailableNow(allActivities, state, Collections.emptySet());
	}

	/**
	 * As {@link #getAvailableNow(List, PlayerState)}, but leaving out whole
	 * categories the player has switched off in settings.
	 * <p>
	 * A hidden category is dropped here rather than in the panel so that the
	 * "nothing is available" case stays honest: a player who has turned quests
	 * off should be told there is nothing left to do, not shown a count that
	 * includes the things being hidden from them.
	 *
	 * @param hiddenCategories categories to leave out; a category nobody has a
	 *                         switch for is simply never in this set
	 */
	public List<Activity> getAvailableNow(List<Activity> allActivities, PlayerState state,
		Set<Activity.Category> hiddenCategories)
	{
		List<Activity> result = new ArrayList<>();
		for (Activity activity : allActivities)
		{
			if (isHidden(activity, hiddenCategories))
			{
				continue;
			}
			if (isAlreadyDone(activity, state))
			{
				continue;
			}
			if (meetsAllRequirements(activity, state))
			{
				result.add(activity);
			}
		}
		result.sort(Comparator.comparingInt(Activity::getPriority).reversed());
		return result;
	}

	/**
	 * Returns activities the player is close to unlocking - exactly one
	 * requirement away, and that one a short step (see {@link #CLOSE_LEVELS}) -
	 * so the panel can show "almost there" suggestions
	 * (e.g. "2 more Mining levels unlocks Diary tier X").
	 */
	public List<Activity> getAlmostAvailable(List<Activity> allActivities, PlayerState state)
	{
		return getAlmostAvailable(allActivities, state, Collections.emptySet());
	}

	/**
	 * As {@link #getAlmostAvailable(List, PlayerState)}, but leaving out whole
	 * categories the player has switched off in settings.
	 */
	public List<Activity> getAlmostAvailable(List<Activity> allActivities, PlayerState state,
		Set<Activity.Category> hiddenCategories)
	{
		List<Activity> result = new ArrayList<>();
		for (Activity activity : allActivities)
		{
			if (isHidden(activity, hiddenCategories))
			{
				continue;
			}
			if (isAlreadyDone(activity, state))
			{
				continue;
			}
			Requirement missing = theOneMissing(activity, state);
			if (missing != null && isClose(missing, state))
			{
				result.add(activity);
			}
		}
		return result;
	}

	/**
	 * How many levels short still counts as "almost". One requirement away used
	 * to be the whole test, so 70 Slayer put Cerberus (91) on the list beside
	 * things two levels off, and on a high-level account the far-off ones were
	 * most of what was left.
	 */
	static final int CLOSE_LEVELS = 5;
	/** Quest points come a few at a time, so the margin is wider than for a level. */
	static final int CLOSE_QUEST_POINTS = 10;

	/** The single requirement standing in the way, or null when there are none or several. */
	private Requirement theOneMissing(Activity activity, PlayerState state)
	{
		Requirement missing = null;
		for (Requirement req : activity.getRequirements())
		{
			if (!isRequirementMet(req, state))
			{
				if (missing != null)
				{
					return null;
				}
				missing = req;
			}
		}
		return missing;
	}

	/**
	 * Whether a requirement is a short step away. A level is close within a
	 * few levels; a quest or a Combat Achievement tier is one thing to do, so
	 * it counts as close however long that thing takes - the plan says how long.
	 */
	private boolean isClose(Requirement req, PlayerState state)
	{
		switch (req.getType())
		{
			case SKILL_LEVEL:
				return req.getThreshold() - state.getSkillLevel(req.getValue()) <= CLOSE_LEVELS;
			case COMBAT_LEVEL:
				return req.getThreshold() - state.getCombatLevel() <= CLOSE_LEVELS;
			case COMBINED_SKILL_LEVEL:
				return req.getThreshold() - combinedLevel(req, state) <= CLOSE_LEVELS;
			case QUEST_POINTS:
				return req.getThreshold() - state.getQuestPoints() <= CLOSE_QUEST_POINTS;
			case ANY_OF:
				for (Requirement option : req.getAnyOf())
				{
					if (isClose(option, state))
					{
						return true;
					}
				}
				return false;
			default:
				return true;
		}
	}

	/**
	 * The line under an "Almost there" suggestion saying what is in the way,
	 * e.g. "Needs 91 Slayer, you have 87". Null when nothing is missing or more
	 * than one thing is.
	 */
	public String whatIsMissing(Activity activity, PlayerState state)
	{
		Requirement missing = theOneMissing(activity, state);
		return missing == null ? null : "Needs " + describe(missing, state);
	}

	private String describe(Requirement req, PlayerState state)
	{
		switch (req.getType())
		{
			case SKILL_LEVEL:
				return req.getThreshold() + " " + titleCase(req.getValue())
					+ ", you have " + state.getSkillLevel(req.getValue());
			case COMBAT_LEVEL:
				return "combat level " + req.getThreshold() + ", you are " + state.getCombatLevel();
			case COMBINED_SKILL_LEVEL:
			{
				List<String> names = new ArrayList<>();
				for (String skill : req.getValue().split(","))
				{
					names.add(titleCase(skill.trim()));
				}
				return req.getThreshold() + " " + String.join(" + ", names)
					+ " combined, you have " + combinedLevel(req, state);
			}
			case QUEST_POINTS:
				return req.getThreshold() + " Quest points, you have " + state.getQuestPoints();
			case QUEST_COMPLETE:
				return req.getValue() + " done first";
			case COMBAT_ACHIEVEMENT_TIER:
			{
				CombatAchievementTier tier = CombatAchievementTier.fromKey(req.getValue());
				return (tier != null ? "the " + tier.getLabel() : req.getValue())
					+ " Combat Achievement tier first";
			}
			case ANY_OF:
			{
				// Only the options that are close: the far ones are not what
				// put this on the list.
				List<String> close = new ArrayList<>();
				for (Requirement option : req.getAnyOf())
				{
					if (isClose(option, state))
					{
						close.add(describe(option, state));
					}
				}
				return String.join(", or ", close);
			}
			default:
				return req.getValue();
		}
	}

	private static int combinedLevel(Requirement req, PlayerState state)
	{
		int total = 0;
		for (String skill : req.getValue().split(","))
		{
			total += state.getSkillLevel(skill.trim());
		}
		return total;
	}

	private static String titleCase(String skill)
	{
		String lower = skill.toLowerCase(java.util.Locale.ROOT);
		return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
	}

	/** An activity with no category at all is never hidden: it has no switch. */
	private boolean isHidden(Activity activity, Set<Activity.Category> hiddenCategories)
	{
		return activity.getCategory() != null && hiddenCategories.contains(activity.getCategory());
	}

	private boolean isAlreadyDone(Activity activity, PlayerState state)
	{
		return RequirementEvaluator.isAlreadyDone(activity, state);
	}

	private boolean meetsAllRequirements(Activity activity, PlayerState state)
	{
		for (Requirement req : activity.getRequirements())
		{
			if (!isRequirementMet(req, state))
			{
				return false;
			}
		}
		return true;
	}

	private boolean isRequirementMet(Requirement req, PlayerState state)
	{
		return RequirementEvaluator.isMet(req, state);
	}
}
