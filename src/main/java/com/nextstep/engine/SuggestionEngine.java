package com.nextstep.engine;

import com.nextstep.model.Activity;
import com.nextstep.model.PlayerState;
import com.nextstep.model.Requirement;
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
	 * requirement away - so the panel can show "almost there" suggestions
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
			long unmet = activity.getRequirements().stream()
				.filter(r -> !isRequirementMet(r, state))
				.count();
			if (unmet == 1)
			{
				result.add(activity);
			}
		}
		return result;
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
