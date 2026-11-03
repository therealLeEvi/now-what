package com.nowwhat.engine;

import com.nowwhat.model.Activity;
import com.nowwhat.model.PlayerState;
import com.nowwhat.model.Requirement;

/**
 * Single source of truth for "is this requirement currently met". Used by
 * both SuggestionEngine (what can I do now) and PrerequisiteResolver
 * (what do I need to do to unlock X) so the two never disagree.
 */
public final class RequirementEvaluator
{
	private RequirementEvaluator()
	{
	}

	public static boolean isMet(Requirement req, PlayerState state)
	{
		switch (req.getType())
		{
			case SKILL_LEVEL:
				return state.getSkillLevel(req.getValue()) >= req.getThreshold();
			case QUEST_COMPLETE:
				return state.isQuestComplete(req.getValue());
			case DIARY_COMPLETE:
				return state.isDiaryTierComplete(req.getValue());
			case COMBAT_ACHIEVEMENT_TIER:
				return state.isCombatAchievementTierComplete(req.getValue());
			case BOSS_KC:
				return state.getBossKillCount(req.getValue()) >= req.getThreshold();
			case QUEST_POINTS:
				return state.getQuestPoints() >= req.getThreshold();
			case COMBAT_LEVEL:
				return state.getCombatLevel() >= req.getThreshold();
			case ITEM_OWNED:
				return state.getOwnedCount(parseItemId(req.getValue()))
					>= Math.max(1, req.getThreshold());
			case COMBINED_SKILL_LEVEL:
				return combinedLevel(req, state) >= req.getThreshold();
			case ANY_OF:
				for (Requirement child : req.getAnyOf())
				{
					if (isMet(child, state))
					{
						return true;
					}
				}
				return req.getAnyOf().isEmpty(); // an empty ANY_OF is vacuously met rather than an unsatisfiable gate
			default:
				return false;
		}
	}

	private static int parseItemId(String value)
	{
		try
		{
			return Integer.parseInt(value.trim());
		}
		catch (NumberFormatException e)
		{
			return -1;
		}
	}

	private static int combinedLevel(Requirement req, PlayerState state)
	{
		int sum = 0;
		for (String skill : req.getValue().split(","))
		{
			sum += state.getSkillLevel(skill.trim());
		}
		return sum;
	}

	/**
	 * True if the activity itself is something with a clear "done" state
	 * that the player has already reached (a quest, a diary tier, a combat
	 * achievement tier, or a specific collection log unlock). Open-ended
	 * grinds (PVM/SKILLING/MINIGAME/CLUE_SCROLL) have no single "done" state
	 * here - that's left to user-set goals rather than static content data.
	 */
	public static boolean isAlreadyDone(Activity activity, PlayerState state)
	{
		switch (activity.getCategory())
		{
			case QUEST:
				return state.isQuestComplete(activity.getName());
			case DIARY:
				return state.isDiaryTierComplete(activity.getId());
			case COMBAT_ACHIEVEMENT:
				return state.isCombatAchievementTierComplete(activity.getId());
			case COLLECTION_LOG:
				return state.hasCollectionLogUnlock(activity.getName());
			default:
				return false;
		}
	}
}
