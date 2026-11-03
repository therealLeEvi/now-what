package com.nextstep.model;

import java.util.List;

/**
 * A single prerequisite gate for an {@link Activity}.
 * <p>
 * Every activity in activities.json carries zero or more of these. The
 * {@link com.nextstep.engine.SuggestionEngine} evaluates each one against
 * live {@link PlayerState} and only surfaces activities where every
 * requirement is satisfied.
 */
public class Requirement
{
	public enum Type
	{
		/** value = OSRS skill name (e.g. "AGILITY"), threshold = level */
		SKILL_LEVEL,
		/** value = quest name exactly as it appears in the RuneLite Quest enum */
		QUEST_COMPLETE,
		/** value = "REGION_TIER" e.g. "ARDOUGNE_MEDIUM" */
		DIARY_COMPLETE,
		/** value = combat achievement tier key, e.g. "CA_GRANDMASTER" (matches the activity id) */
		COMBAT_ACHIEVEMENT_TIER,
		/** value = boss/activity name as tracked in hiscores, threshold = kill count */
		BOSS_KC,
		/** value = quest point total, threshold = minimum QP */
		QUEST_POINTS,
		/** value = combat level, threshold = minimum combat level */
		COMBAT_LEVEL,
		/**
		 * value = comma-separated skill names (e.g. "ATTACK,STRENGTH"),
		 * threshold = minimum sum of those skills' levels. Real gates like
		 * this exist in OSRS (Warriors' Guild's 130 combined Attack+Strength
		 * alternative being the canonical example).
		 */
		COMBINED_SKILL_LEVEL,
		/**
		 * Satisfied if ANY ONE of `anyOf` is satisfied - mirrors the OR-groups
		 * the OSRS Wiki's `achievement_requirement` bucket already models
		 * (e.g. Warriors' Guild-style "99 Attack OR 99 Strength" gates).
		 * value/threshold are unused for this type; only `anyOf` matters.
		 * Only one level of OR-nesting is supported for now - an ANY_OF
		 * whose children are themselves ANY_OF isn't handled specially, since
		 * nothing in the bundled sample data needed it yet. Worth revisiting
		 * once real Bucket-derived data shows how deep the nesting actually
		 * gets in practice.
		 */
		ANY_OF,
		/**
		 * value = RuneLite item id, threshold = how many are needed (0/1 means
		 * "own one"). Gear gates are real - a boss recommendation that assumes
		 * a blowpipe is wrong for someone who doesn't have one - and this is
		 * how the content data expresses them.
		 */
		ITEM_OWNED
	}

	private Type type;
	private String value;
	private int threshold;
	/** ANY_OF only: the alternatives, any one of which satisfies this requirement. */
	private List<Requirement> anyOf;
	/**
	 * Optional: a short, concrete suggestion for how to actually satisfy this
	 * requirement - a training method, a course, a shop, etc. This is the
	 * piece existing tools (Goal Planner, QuestPath) don't provide: they'll
	 * tell you that you need 47 Agility, but not the fastest reasonable way
	 * to get there from where you are. Null is fine for requirements where
	 * "just go do the thing" is self-explanatory (e.g. QUEST_COMPLETE).
	 */
	private String hint;

	public Requirement()
	{
	}

	public Requirement(Type type, String value, int threshold)
	{
		this.type = type;
		this.value = value;
		this.threshold = threshold;
	}

	/** Builds an ANY_OF requirement from its alternatives - used by both production data loading callers and tests. */
	public static Requirement anyOf(Requirement... children)
	{
		Requirement req = new Requirement();
		req.type = Type.ANY_OF;
		req.anyOf = java.util.Arrays.asList(children);
		return req;
	}

	public Type getType()
	{
		return type;
	}

	public String getValue()
	{
		return value;
	}

	public int getThreshold()
	{
		return threshold;
	}

	public String getHint()
	{
		return hint;
	}

	public List<Requirement> getAnyOf()
	{
		return anyOf == null ? java.util.Collections.emptyList() : anyOf;
	}
}
