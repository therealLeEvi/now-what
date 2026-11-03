package com.nextstep.model;

/**
 * A player-set target for the path planner to plan a route towards.
 * Deliberately a plain discriminated-union-style class rather than a class
 * hierarchy, to keep (de)serialization and Swing form-binding simple.
 */
public class Goal
{
	public enum Type
	{
		/** Reach a skill level. skillName + targetLevel. */
		SKILL_LEVEL,
		/** Own N of an item (bank + inventory + equipment). itemId + targetQuantity. */
		ITEM_QUANTITY,
		/** Unlock/complete something already in the Activity database. activityId. */
		ACTIVITY
	}

	private final Type type;
	private final String skillName;
	private final int targetLevel;
	private final String itemId;
	private final int targetQuantity;
	private final String activityId;
	private final String label;
	private final boolean allowTickManipulation;
	/**
	 * Whether an ITEM_QUANTITY goal counts what the player already has towards
	 * the target.
	 * <p>
	 * It does by default, since asking for 3 of something you already have 2 of
	 * is a one-item job. But that makes "how do I get one of these" unanswerable
	 * for anyone who already owns one - the plan comes back "already done", and
	 * the only way to see the route is to ask for two and read a plan for the
	 * wrong quantity. So it is the player's call, not an assumption.
	 */
	private final boolean countOwned;

	private Goal(Type type, String skillName, int targetLevel, String itemId, int targetQuantity,
		String activityId, String label, boolean allowTickManipulation)
	{
		this(type, skillName, targetLevel, itemId, targetQuantity, activityId, label,
			allowTickManipulation, true);
	}

	private Goal(Type type, String skillName, int targetLevel, String itemId, int targetQuantity,
		String activityId, String label, boolean allowTickManipulation, boolean countOwned)
	{
		this.countOwned = countOwned;
		this.type = type;
		this.skillName = skillName;
		this.targetLevel = targetLevel;
		this.itemId = itemId;
		this.targetQuantity = targetQuantity;
		this.activityId = activityId;
		this.label = label;
		this.allowTickManipulation = allowTickManipulation;
	}

	public static Goal skillLevel(String skillName, int targetLevel, boolean allowTickManipulation)
	{
		return new Goal(Type.SKILL_LEVEL, skillName.toUpperCase(), targetLevel, null, 0, null,
			targetLevel + " " + capitalize(skillName), allowTickManipulation);
	}

	public static Goal itemQuantity(String itemId, int targetQuantity, String displayName, boolean allowTickManipulation)
	{
		return itemQuantity(itemId, targetQuantity, displayName, allowTickManipulation, true);
	}

	public static Goal itemQuantity(String itemId, int targetQuantity, String displayName,
		boolean allowTickManipulation, boolean countOwned)
	{
		return new Goal(Type.ITEM_QUANTITY, null, 0, itemId.toUpperCase(), targetQuantity, null,
			displayName + " x" + targetQuantity, allowTickManipulation, countOwned);
	}

	public static Goal activity(String activityId, String displayName, boolean allowTickManipulation)
	{
		return new Goal(Type.ACTIVITY, null, 0, null, 0, activityId, displayName, allowTickManipulation);
	}

	private static String capitalize(String s)
	{
		if (s.isEmpty())
		{
			return s;
		}
		return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
	}

	public Type getType()
	{
		return type;
	}

	public String getSkillName()
	{
		return skillName;
	}

	public int getTargetLevel()
	{
		return targetLevel;
	}

	public String getItemId()
	{
		return itemId;
	}

	public int getTargetQuantity()
	{
		return targetQuantity;
	}

	public String getActivityId()
	{
		return activityId;
	}

	public String getLabel()
	{
		return label;
	}

	/**
	 * Which training intensity the planner should show for skill segments -
	 * true prefers tick-manipulation methods (falling back to normal methods
	 * only where no tick-manip method is recorded), false shows only normal
	 * methods and never falls back to tick-manipulation. Exclusive, not
	 * additive: the plan shows one intensity band per segment, not both
	 * mixed together.
	 */
	public boolean isAllowTickManipulation()
	{
		return allowTickManipulation;
	}

	/** @see #countOwned */
	public boolean isCountOwned()
	{
		return countOwned;
	}

	/** The same goal again, planned as if the player owned none of it yet. */
	public Goal ignoringWhatIsOwned()
	{
		return new Goal(type, skillName, targetLevel, itemId, targetQuantity, activityId, label,
			allowTickManipulation, false);
	}
}
