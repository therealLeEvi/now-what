package com.nowwhat.engine;

import com.nowwhat.model.Activity;
import com.nowwhat.model.PlayerState;
import com.nowwhat.model.PrerequisiteChain;
import com.nowwhat.model.Requirement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Answers "what do I need to do before I can do X" by walking the
 * requirement graph backwards from a target activity.
 * <p>
 * The key idea: an unmet QUEST_COMPLETE or DIARY_COMPLETE requirement is
 * itself just another Activity in the database (a quest is an Activity with
 * category QUEST, a diary tier is an Activity with category DIARY, and each
 * of THOSE can have its own requirements, including earlier quests or
 * diary tiers). So resolving "what unlocks Zulrah" means recursively
 * resolving "what unlocks Regicide", which might mean resolving "what
 * unlocks [some earlier quest]", and so on, until everything left is a
 * plain skill level / KC / combat level with nothing further to chain to.
 * <p>
 * This depends entirely on activities.json actually containing an entry for
 * every quest/diary tier referenced elsewhere as a requirement - if a quest
 * requirement points at a quest name with no matching Activity entry, that
 * reference is reported back as "unresolved" rather than silently dropped,
 * so gaps in the content database are visible instead of hidden.
 */
public class PrerequisiteResolver
{
	private final Map<String, Activity> questsByName = new HashMap<>();
	private final Map<String, Activity> diaryTiersById = new HashMap<>();
	private final Map<String, Activity> combatAchievementTiersById = new HashMap<>();

	public PrerequisiteResolver(List<Activity> allActivities)
	{
		for (Activity activity : allActivities)
		{
			if (activity.getCategory() == Activity.Category.QUEST)
			{
				questsByName.put(activity.getName(), activity);
			}
			else if (activity.getCategory() == Activity.Category.DIARY)
			{
				diaryTiersById.put(activity.getId().toUpperCase(), activity);
			}
			else if (activity.getCategory() == Activity.Category.COMBAT_ACHIEVEMENT)
			{
				combatAchievementTiersById.put(activity.getId().toUpperCase(), activity);
			}
		}
	}

	public PrerequisiteChain resolve(Activity target, PlayerState state)
	{
		if (RequirementEvaluator.isAlreadyDone(target, state))
		{
			return new PrerequisiteChain(target, true, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
		}

		List<Activity> orderedSteps = new ArrayList<>();
		List<Requirement> standalone = new ArrayList<>();
		List<String> unresolved = new ArrayList<>();
		Set<String> visiting = new LinkedHashSet<>();
		Set<String> visited = new LinkedHashSet<>();

		walk(target, state, orderedSteps, standalone, unresolved, visiting, visited, true);

		return new PrerequisiteChain(target, false, orderedSteps, dedupeRequirements(standalone), unresolved);
	}

	/**
	 * @param isTarget true only on the initial call, so the target itself
	 *                 isn't duplicated into its own prerequisite list.
	 */
	private void walk(Activity activity, PlayerState state, List<Activity> orderedSteps,
		List<Requirement> standalone, List<String> unresolved, Set<String> visiting, Set<String> visited,
		boolean isTarget)
	{
		if (visited.contains(activity.getId()))
		{
			return;
		}
		if (visiting.contains(activity.getId()))
		{
			// Circular requirement in the content data - stop rather than
			// recurse forever. This should never legitimately happen; if it
			// does, activities.json has a data error worth fixing.
			unresolved.add("(circular reference detected involving " + activity.getName() + ")");
			return;
		}

		visiting.add(activity.getId());

		for (Requirement req : activity.getRequirements())
		{
			if (RequirementEvaluator.isMet(req, state))
			{
				continue;
			}

			switch (req.getType())
			{
				case QUEST_COMPLETE:
				{
					Activity questPrereq = questsByName.get(req.getValue());
					if (questPrereq != null)
					{
						walk(questPrereq, state, orderedSteps, standalone, unresolved, visiting, visited, false);
					}
					else
					{
						unresolved.add("Quest: " + req.getValue() + " (not in activity database)");
					}
					break;
				}
				case DIARY_COMPLETE:
				{
					Activity diaryPrereq = diaryTiersById.get(req.getValue().toUpperCase());
					if (diaryPrereq != null)
					{
						walk(diaryPrereq, state, orderedSteps, standalone, unresolved, visiting, visited, false);
					}
					else
					{
						unresolved.add("Diary tier: " + req.getValue() + " (not in activity database)");
					}
					break;
				}
				case COMBAT_ACHIEVEMENT_TIER:
				{
					// A tier is itself an activity, the same way a diary tier is,
					// so "Master needs Elite" plans Elite as a step rather than
					// printing the raw "CA_ELITE" key as a shortfall.
					Activity tierPrereq = combatAchievementTiersById.get(req.getValue().toUpperCase());
					if (tierPrereq != null)
					{
						walk(tierPrereq, state, orderedSteps, standalone, unresolved, visiting, visited, false);
					}
					else
					{
						unresolved.add("Combat Achievement tier: " + req.getValue() + " (not in activity database)");
					}
					break;
				}
				default:
					// SKILL_LEVEL, BOSS_KC, QUEST_POINTS, COMBAT_LEVEL,
					// ANY_OF: nothing further to chain
					// to here - PathPlanner expands each of these appropriately
					// (training steps, or a presented choice for ANY_OF).
					standalone.add(req);
			}
		}

		visiting.remove(activity.getId());
		visited.add(activity.getId());

		if (!isTarget)
		{
			orderedSteps.add(activity);
		}
	}

	private List<Requirement> dedupeRequirements(List<Requirement> requirements)
	{
		List<Requirement> result = new ArrayList<>();
		Set<String> seen = new LinkedHashSet<>();
		for (Requirement req : requirements)
		{
			// Keep only the first occurrence if the same skill/KC shows up more
			// than once across the chain (e.g. two different quests both
			// wanting Agility) - chain output stays de-duplicated for display.
			String key = req.getType() + ":" + req.getValue();
			if (seen.add(key))
			{
				result.add(req);
			}
		}
		return result;
	}
}
