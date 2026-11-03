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

	/**
	 * How much of the player's attention a method actually costs, which is a
	 * separate question from how fast it is and from whether it is tick
	 * manipulation.
	 * <p>
	 * Bloodwood is why this field exists. The wiki classes it as the fastest
	 * Woodcutting method that is not tick manipulation, so it is correctly
	 * {@link Intensity#NORMAL} - but its 130k/hr assumes an efficient two-tick
	 * cycle filling sap buckets across three trees at once, and the panel was
	 * presenting that beside an ironwood tree you click once every four minutes
	 * as though the only difference between them were the xp. That is the same
	 * kind of hidden cost as {@link Risk#PVP}: real, decision-changing, and
	 * invisible in an xp/hr figure, so it is recorded as data and labelled
	 * rather than left for the player to already know.
	 * <p>
	 * Unset is not {@link #ORDINARY}. A method with no value here has not been
	 * judged yet and gets no label, because printing "ordinary" over a method
	 * nobody has assessed would be inventing a fact. Tick-manipulation methods
	 * are deliberately left unset too - they already carry their own label and
	 * do not need a second one saying the same thing.
	 */
	public enum Attention
	{
		/** Little input: one tree every few minutes, or no banking to do. */
		AFK,
		/** Ordinary clicking - assessed and unremarkable. Carries no label. */
		ORDINARY,
		/** Near-continuous input to reach the stated rate, without being tick manipulation. */
		DEMANDING
	}

	/**
	 * One counted thing an action of this method uses up, and how many of it per
	 * action.
	 * <p>
	 * Recorded so the panel can answer the question a player actually goes to a
	 * bank screen with. "Gilded altar with dragon bones, 643k xp/hr, ~17 hours"
	 * tells you the time but not the shopping list, and the arithmetic to get
	 * from one to the other - 11.1M xp divided by 252 xp a bone - is exactly the
	 * sort of thing people leave the client to do in a calculator.
	 */
	public static class Consumed
	{
		private String item;
		private double each = 1;

		public Consumed()
		{
		}

		public Consumed(String item, double each)
		{
			this.item = item;
			this.each = each;
		}

		public String getItem()
		{
			return item;
		}

		/** How many per action; fractional for something used across several actions. */
		public double getEach()
		{
			return each <= 0 ? 1 : each;
		}
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
	/** Null when absent from methods.json, meaning "not judged" - see {@link Attention}. */
	private Attention attention;
	/**
	 * The xp one action of this method gives - one bone offered, one bow strung,
	 * one gem cut. Null for a method whose xp does not come in countable units,
	 * such as a minigame or a stretch of combat.
	 */
	private Double xpPerAction;
	/** What one action uses up. Empty for a method that consumes nothing. */
	private java.util.List<Consumed> consumes;
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
	/**
	 * An activity id to take the kit from, for a method that <i>is</i> an
	 * activity the database already describes.
	 * <p>
	 * Wintertodt is the case this exists for. It is a Firemaking method and a
	 * minigame with four warm-clothing slots and a hammer, and the minigame
	 * entry already says so - so planning 99 Firemaking used to name Wintertodt
	 * and then say nothing at all about what to wear, while clicking the same
	 * minigame in the suggestion list pictured the whole kit. Two copies of one
	 * answer was the wrong fix; this borrows it, exactly as
	 * {@code Activity.preparationFrom} does for a collection log slot that is
	 * really a boss.
	 */
	private String activity;

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

	/**
	 * Null unless this method's xp arrives in countable actions. Paired with
	 * {@link #getConsumes()}: together they are what turns a level range into a
	 * shopping list.
	 */
	public Double getXpPerAction()
	{
		return xpPerAction;
	}

	/** Never null - a method that consumes nothing returns an empty list. */
	public java.util.List<Consumed> getConsumes()
	{
		return consumes == null ? java.util.Collections.emptyList() : consumes;
	}

	/** True when this method can say how much of what a level range will cost. */
	public boolean hasShoppingList()
	{
		return xpPerAction != null && xpPerAction > 0 && !getConsumes().isEmpty();
	}

	/** Null for an ordinary method; an activity id for one that borrows its kit. */
	public String getActivity()
	{
		return activity;
	}

	public java.util.List<String> getQuests()
	{
		return quests == null ? java.util.Collections.emptyList() : quests;
	}

	public Risk getRisk()
	{
		return risk == null ? Risk.SAFE : risk;
	}

	/**
	 * Null when the method carries no attention judgement at all, which is
	 * deliberately distinct from {@link Attention#ORDINARY}.
	 */
	public Attention getAttention()
	{
		return attention;
	}

	/**
	 * Sets the attention judgement, for programmatic construction - tests and
	 * the diagnostic harnesses, which build methods rather than loading JSON.
	 */
	public TrainingMethod withAttention(Attention attention)
	{
		this.attention = attention;
		return this;
	}

	/**
	 * Sets the countable-action fields, for programmatic construction - tests
	 * and the diagnostic harnesses, which build methods rather than loading
	 * JSON.
	 */
	public TrainingMethod withConsumption(double xpPerAction, Consumed... consumes)
	{
		this.xpPerAction = xpPerAction;
		this.consumes = java.util.Arrays.asList(consumes);
		return this;
	}

	/** Takes the attention judgement recorded on the lowest bracket of the same method. */
	public void inheritAttention(Attention inherited)
	{
		if (attention == null)
		{
			attention = inherited;
		}
	}

	public boolean isPvpRisk()
	{
		return getRisk() == Risk.PVP;
	}
}

