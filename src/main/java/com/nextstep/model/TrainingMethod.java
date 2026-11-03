package com.nextstep.model;

/**
 * One way to train a skill, valid over [minLevel, maxLevel). Loaded from
 * methods.json. The path planner walks a skill's methods to build a
 * sequence of TRAIN_SKILL steps covering a level range.
 * <p>
 * Ranges for the same skill are allowed to overlap - a tick-manipulation
 * method and its calmer fallback commonly cover the exact same level
 * bracket at very different xp/hr and very different click intensity.
 * SkillPathPlanner picks between overlapping candidates based on the
 * caller's tick-manipulation preference rather than always taking the
 * highest xp/hr option.
 * <p>
 * {@link Risk} is the same idea applied to danger rather than click rate:
 * a method that can only be trained in the Wilderness is not interchangeable
 * with an equally fast safe one, and a plan that quotes an xp/hr figure
 * without saying where it's earned is hiding the part a player would most
 * want to weigh.
 */
public class TrainingMethod
{
	public enum Intensity
	{
		/** Ordinary play speed - the default assumption if a method omits this field. */
		NORMAL,
		/**
		 * Requires precise, repeated timing against the game's tick rate
		 * (dropping/reclicking, 3-ticking, etc.) to reach the stated xp/hr.
		 * Legitimate and wiki-documented, but far more click-intensive than
		 * a NORMAL method - many players deliberately avoid it.
		 */
		TICK_MANIPULATION
	}

	public enum Risk
	{
		/** No PvP exposure - the default assumption if a method omits this field. */
		SAFE,
		/**
		 * Trained in the Wilderness, where other players can attack you and
		 * your gear and caught stock are lost on death. This is a cost that
		 * doesn't show up anywhere in an xp/hr figure, so it's recorded as
		 * data and labelled in the plan rather than left for the player to
		 * already know.
		 */
		PVP
	}

	private String skill;
	private int minLevel;
	private int maxLevel;
	private String name;
	private Integer xpPerHour;
	private String notes;
	private String wikiUrl;
	/** Defaults to NORMAL when absent from methods.json (see getIntensity()). */
	private Intensity intensity;
	/** Defaults to SAFE when absent from methods.json (see getRisk()). */
	private Risk risk;
	/**
	 * Quests that cover this level range outright, for a method that is "do
	 * these quests" rather than an hourly grind. Named here rather than buried
	 * in prose so the panel can list them and hand them to Quest Helper the
	 * same way it hands over a quest prerequisite.
	 */
	private java.util.List<String> quests;
	/**
	 * What to wear for this method, and what to bring, written the same way the
	 * activity gear lines are so the panel can picture both. Recorded on the
	 * lowest bracket of a method and shared up the rest at load, exactly as the
	 * note is.
	 */
	private java.util.List<String> gear;
	private java.util.List<String> supplies;

	/** No-arg constructor used by Gson when loading methods.json. */
	public TrainingMethod()
	{
	}

	/** Convenience constructor for safe methods - equivalent to passing {@link Risk#SAFE}. */
	public TrainingMethod(String skill, int minLevel, int maxLevel, String name, Integer xpPerHour,
		String notes, String wikiUrl, Intensity intensity)
	{
		this(skill, minLevel, maxLevel, name, xpPerHour, notes, wikiUrl, intensity, Risk.SAFE);
	}

	/** Full constructor for programmatic construction (tests). */
	public TrainingMethod(String skill, int minLevel, int maxLevel, String name, Integer xpPerHour,
		String notes, String wikiUrl, Intensity intensity, Risk risk)
	{
		this.risk = risk;
		this.skill = skill;
		this.minLevel = minLevel;
		this.maxLevel = maxLevel;
		this.name = name;
		this.xpPerHour = xpPerHour;
		this.notes = notes;
		this.wikiUrl = wikiUrl;
		this.intensity = intensity;
	}

	public String getSkill()
	{
		return skill;
	}

	public int getMinLevel()
	{
		return minLevel;
	}

	public int getMaxLevel()
	{
		return maxLevel;
	}

	public String getName()
	{
		return name;
	}

	public Integer getXpPerHour()
	{
		return xpPerHour;
	}

	public String getNotes()
	{
		return notes;
	}

	/**
	 * Fills in a note this row left blank, from a sibling bracket of the same
	 * method.
	 * <p>
	 * A method split into level brackets records its note once, on the lowest
	 * bracket, so the panel does not print the same paragraph under nine
	 * headings. But a plan that starts above that bracket - Thieving from 76,
	 * where the Knights of Ardougne note lives on the 55-60 row - would then
	 * never see the note at all, which is exactly where it is most wanted. The
	 * database copies it down the brackets at load time and the panel's own
	 * repeat check stops it printing twice.
	 */
	public void inheritNotes(String inherited)
	{
		if (notes == null || notes.isEmpty())
		{
			notes = inherited;
		}
	}

	public String getWikiUrl()
	{
		return wikiUrl;
	}

	public Intensity getIntensity()
	{
		return intensity == null ? Intensity.NORMAL : intensity;
	}

	public boolean isTickManipulation()
	{
		return getIntensity() == Intensity.TICK_MANIPULATION;
	}

	/** Never null - a method with no quest route returns an empty list. */
	public java.util.List<String> getGear()
	{
		return gear == null ? java.util.Collections.emptyList() : gear;
	}

	public java.util.List<String> getSupplies()
	{
		return supplies == null ? java.util.Collections.emptyList() : supplies;
	}

	/** Takes the gear recorded on the lowest bracket of the same method. */
	public void inheritPreparation(java.util.List<String> inheritedGear,
		java.util.List<String> inheritedSupplies)
	{
		if (getGear().isEmpty() && inheritedGear != null)
		{
			gear = inheritedGear;
		}
		if (getSupplies().isEmpty() && inheritedSupplies != null)
		{
			supplies = inheritedSupplies;
		}
	}

	public java.util.List<String> getQuests()
	{
		return quests == null ? java.util.Collections.emptyList() : quests;
	}

	public Risk getRisk()
	{
		return risk == null ? Risk.SAFE : risk;
	}

	public boolean isPvpRisk()
	{
		return getRisk() == Risk.PVP;
	}
}

