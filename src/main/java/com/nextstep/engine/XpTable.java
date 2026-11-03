package com.nextstep.engine;

/**
 * The standard OSRS experience curve, plus the formatting helpers the planner
 * needs to say something useful about a training segment.
 *
 * <p>"Nightmare Zone, ~45,000 xp/hr" on its own doesn't answer the question a
 * player is actually asking, which is how long this is going to take. Knowing
 * that Attack 70 to 99 is 12.3M xp - roughly 273 hours at that rate - is the
 * difference between a plan and a restatement of the wiki.
 */
public final class XpTable
{
	/** Levels above 99 exist for the virtual-level/combat maths; 126 is the ceiling. */
	private static final int MAX_LEVEL = 126;

	private static final long[] XP_FOR_LEVEL = new long[MAX_LEVEL + 1];

	static
	{
		// xp(n) = floor( sum(k=1..n-1) floor(k + 300 * 2^(k/7)) / 4 )
		double points = 0;
		for (int level = 1; level < MAX_LEVEL; level++)
		{
			points += Math.floor(level + 300 * Math.pow(2, level / 7.0));
			XP_FOR_LEVEL[level + 1] = (long) Math.floor(points / 4);
		}
	}

	private XpTable()
	{
	}

	public static long xpForLevel(int level)
	{
		if (level <= 1)
		{
			return 0;
		}
		return XP_FOR_LEVEL[Math.min(level, MAX_LEVEL)];
	}

	/** Experience needed to go from one level to another; never negative. */
	public static long xpBetween(int fromLevel, int toLevel)
	{
		return Math.max(0, xpForLevel(toLevel) - xpForLevel(fromLevel));
	}

	/** Compact xp for a UI that has ~200px to play with: 12.3M, 737k, 4,120. */
	public static String formatXp(long xp)
	{
		if (xp >= 1_000_000)
		{
			return trimZero(String.format(java.util.Locale.ROOT, "%.1f", xp / 1_000_000.0)) + "M";
		}
		if (xp >= 10_000)
		{
			return Math.round(xp / 1000.0) + "k";
		}
		return String.format(java.util.Locale.ROOT, "%,d", xp);
	}

	/**
	 * Rough wall-clock estimate. Deliberately coarse - these rates are
	 * averages from a bundled data file, not a measurement of the player, so
	 * presenting "272.6 hours" would imply a precision that isn't there.
	 */
	public static String formatHours(double hours)
	{
		if (hours < 1)
		{
			return Math.max(1, Math.round(hours * 60)) + " min";
		}
		if (hours < 10)
		{
			return trimZero(String.format(java.util.Locale.ROOT, "%.1f", hours)) + " hrs";
		}
		return Math.round(hours) + " hrs";
	}

	private static String trimZero(String s)
	{
		return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
	}
}
