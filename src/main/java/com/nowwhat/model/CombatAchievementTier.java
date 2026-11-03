package com.nowwhat.model;

/**
 * The six Combat Achievement tiers, and the numbers that say how far away
 * each one is.
 * <p>
 * A tier is not a list you clear; it is a points total. Every task is worth
 * a fixed number of points by its own tier (an Easy task 1, a Master task 5),
 * and a tier's rewards unlock once your running total reaches its threshold,
 * whichever tasks the points came from. So "plan the Master tier" is really
 * "earn this many more points", and that is the number the plan leads with.
 * <p>
 * The thresholds move whenever Jagex adds tasks, so they are written down
 * here with the date they were taken from the wiki's Combat Achievements page
 * (2026-11-03) rather than presented as permanent. When the panel says a tier
 * needs a different number of points than the in-game interface does, this
 * table is what to update.
 */
public enum CombatAchievementTier
{
	EASY("Easy", 1, 41, 41),
	MEDIUM("Medium", 2, 169, 64),
	HARD("Hard", 3, 436, 89),
	ELITE("Elite", 4, 1100, 166),
	MASTER("Master", 5, 1965, 173),
	GRANDMASTER("Grandmaster", 6, 2697, 122);

	private final String label;
	private final int pointsPerTask;
	private final int pointsToUnlock;
	private final int taskCount;

	CombatAchievementTier(String label, int pointsPerTask, int pointsToUnlock, int taskCount)
	{
		this.label = label;
		this.pointsPerTask = pointsPerTask;
		this.pointsToUnlock = pointsToUnlock;
		this.taskCount = taskCount;
	}

	/** The key used by activity ids and requirements: {@code CA_MASTER}. */
	public String getKey()
	{
		return "CA_" + name();
	}

	public String getLabel()
	{
		return label;
	}

	public int getPointsPerTask()
	{
		return pointsPerTask;
	}

	/** The running points total at which this tier's rewards unlock. */
	public int getPointsToUnlock()
	{
		return pointsToUnlock;
	}

	public int getTaskCount()
	{
		return taskCount;
	}

	/** The wiki's list of this tier's tasks. */
	public String getTaskListUrl()
	{
		return "https://oldschool.runescape.wiki/w/Combat_Achievements/" + label;
	}

	/** The tier a {@code CA_<TIER>} key names, or null for anything else. */
	public static CombatAchievementTier fromKey(String key)
	{
		if (key == null)
		{
			return null;
		}
		for (CombatAchievementTier tier : values())
		{
			if (tier.getKey().equalsIgnoreCase(key))
			{
				return tier;
			}
		}
		return null;
	}
}
