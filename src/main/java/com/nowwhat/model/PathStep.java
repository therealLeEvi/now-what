package com.nowwhat.model;

import java.util.Collections;
import java.util.List;

/**
 * One concrete, actionable step in a Path. Unlike a Requirement (which just
 * states a gate that must be met), a PathStep says what to actually go do.
 * <p>
 * A TRAIN_SKILL step covers a level range and may carry more than one
 * {@link Option} - e.g. when tick manipulation is disallowed, a level
 * bracket often has several genuinely different non-tick-manip methods
 * (Motherlode Mine, Blast Mine, gem rocks, calcified rocks...) rather than
 * one clear winner, and the planner presents all of them instead of
 * arbitrarily picking one. Every other step type carries exactly one
 * Option, kept in `alternatives` for a single consistent shape.
 */
public class PathStep
{
	public enum Type
	{
		/** Train a skill from one level to another - see `alternatives` for the method(s). */
		TRAIN_SKILL,
		/** Acquire N of an item via buying, a shop, or a crafting action. */
		ACQUIRE_ITEM,
		/** Kill a boss/monster some number of times for a drop. */
		KILL_BOSS,
		/** Complete a quest/diary tier/other Activity as a prerequisite. */
		DO_ACTIVITY,
		/**
		 * Complete a quest. Split out from DO_ACTIVITY so the panel can gather a
		 * run of them into one list and hand the actual walkthrough to Quest
		 * Helper, rather than spending a screen of the plan restating what a far
		 * better plugin already does.
		 */
		DO_QUEST,
		/** Complete an Achievement Diary tier. Handed off the same way as DO_QUEST. */
		DO_DIARY,
		/** The planner couldn't find data to expand this further - informational only. */
		INFO
	}

	/** One concrete way to accomplish a step - a named method, a place to buy from, etc. */
	public static class Option
	{
		private final String title;
		private final String detail;
		private final String wikiUrl;
		private final List<String> quests;
		/**
		 * Flags rather than text in the title. They used to be appended as
		 * "[Wilderness - PvP risk]" and "[Tick manipulation]", which made an
		 * already-wrapping name longer and left the panel no way to render them
		 * as anything but more prose.
		 */
		private final boolean pvpRisk;
		private final boolean tickManipulation;
		/** Null means no judgement was recorded; see {@link TrainingMethod.Attention}. */
		private final TrainingMethod.Attention attention;
		/** How much of what this option's range will use up; empty when unknown. */
		private final List<Needed> needed;
		/**
		 * The scannable fact - "~3 hrs - 160k xp/hr". Kept apart from the note
		 * because welding it on the end made every note a five-line paragraph
		 * whose last clause was the only part most players were looking for.
		 */
		private final String meta;
		/**
		 * The hourly rate behind {@link #getMeta()}, kept as a number as well as
		 * a formatted string. The panel merges consecutive level brackets that
		 * resolve to the same method into one step, and a merged step's estimate
		 * has to be recomputed over the whole range - which cannot be done by
		 * reading "~3 hrs" back out of a label.
		 */
		private final Integer xpPerHour;
		/**
		 * What to wear and what to bring for this method, in the same loose
		 * prose the activity gear lines use, so the panel turns both into the
		 * same pictures.
		 * <p>
		 * A training step used to answer "where" and stop. "Maniacal monkeys"
		 * is a fine answer to where, and no answer at all to the question the
		 * player actually has standing in a bank: what do I take.
		 */
		private final List<String> gear;
		private final List<String> supplies;

		public Option(String title, String detail, String wikiUrl)
		{
			this(title, detail, wikiUrl, Collections.emptyList(), false, false);
		}

		public Option(String title, String detail, String wikiUrl, List<String> quests)
		{
			this(title, detail, wikiUrl, quests, false, false);
		}

		public Option(String title, String detail, String wikiUrl, List<String> quests,
			boolean pvpRisk, boolean tickManipulation)
		{
			this(title, detail, null, wikiUrl, quests, pvpRisk, tickManipulation);
		}

		public Option(String title, String detail, String meta, String wikiUrl,
			List<String> quests, boolean pvpRisk, boolean tickManipulation)
		{
			this(title, detail, meta, wikiUrl, quests, pvpRisk, tickManipulation, null);
		}

		public Option(String title, String detail, String meta, String wikiUrl,
			List<String> quests, boolean pvpRisk, boolean tickManipulation, Integer xpPerHour)
		{
			this(title, detail, meta, wikiUrl, quests, pvpRisk, tickManipulation, xpPerHour,
				Collections.emptyList(), Collections.emptyList());
		}

		public Option(String title, String detail, String meta, String wikiUrl,
			List<String> quests, boolean pvpRisk, boolean tickManipulation, Integer xpPerHour,
			List<String> gear, List<String> supplies)
		{
			this(title, detail, meta, wikiUrl, quests, pvpRisk, tickManipulation, xpPerHour,
				gear, supplies, null, Collections.emptyList());
		}

		private Option(String title, String detail, String meta, String wikiUrl,
			List<String> quests, boolean pvpRisk, boolean tickManipulation, Integer xpPerHour,
			List<String> gear, List<String> supplies, TrainingMethod.Attention attention,
			List<Needed> needed)
		{
			this.needed = needed == null ? Collections.emptyList() : needed;
			this.attention = attention;
			this.gear = gear == null ? Collections.emptyList() : gear;
			this.supplies = supplies == null ? Collections.emptyList() : supplies;
			this.xpPerHour = xpPerHour;
			this.meta = meta;
			this.title = title;
			this.detail = detail;
			this.wikiUrl = wikiUrl;
			this.quests = quests == null ? Collections.emptyList() : quests;
			this.pvpRisk = pvpRisk;
			this.tickManipulation = tickManipulation;
		}

		public List<String> getGear()
		{
			return gear;
		}

		public List<String> getSupplies()
		{
			return supplies;
		}

		/** True when there is anything to show about what to take. */
		public boolean hasPreparation()
		{
			return !gear.isEmpty() || !supplies.isEmpty();
		}

		/** One line of a shopping list: an item and how many of it. */
		public static final class Needed
		{
			private final String item;
			private final long quantity;

			public Needed(String item, long quantity)
			{
				this.item = item;
				this.quantity = quantity;
			}

			public String getItem()
			{
				return item;
			}

			public long getQuantity()
			{
				return quantity;
			}
		}

		/** This option with gear and supplies attached - everything else unchanged. */
		public Option withPreparation(List<String> gear, List<String> supplies)
		{
			return new Option(title, detail, meta, wikiUrl, quests, pvpRisk, tickManipulation,
				xpPerHour, gear, supplies, attention, needed);
		}

		/**
		 * This option with its shopping list attached. Computed per level range
		 * rather than stored on the method, because the answer <em>is</em> the
		 * range: the same method costs 44,000 bones over one stretch and 3,000
		 * over another.
		 */
		public Option withNeeded(List<Needed> needed)
		{
			return new Option(title, detail, meta, wikiUrl, quests, pvpRisk, tickManipulation,
				xpPerHour, gear, supplies, attention, needed);
		}

		/** Empty when the method does not consume anything countable. */
		public List<Needed> getNeeded()
		{
			return needed;
		}

		/**
		 * This option with an attention judgement attached - everything else
		 * unchanged. A wither rather than another constructor parameter: the
		 * constructor chain is already five deep, and every existing caller
		 * means "no judgement recorded" anyway.
		 */
		public Option withAttention(TrainingMethod.Attention attention)
		{
			return new Option(title, detail, meta, wikiUrl, quests, pvpRisk, tickManipulation,
				xpPerHour, gear, supplies, attention, needed);
		}

		/**
		 * Null when the method behind this option carries no attention
		 * judgement, which is not the same as judging it ordinary.
		 */
		public TrainingMethod.Attention getAttention()
		{
			return attention;
		}

		/** Null when the method has no hourly rate, e.g. a quest route. */
		public String getMeta()
		{
			return meta;
		}

		/** Null when the method has no hourly rate, e.g. a quest route. */
		public Integer getXpPerHour()
		{
			return xpPerHour;
		}

		public boolean isPvpRisk()
		{
			return pvpRisk;
		}

		public boolean isTickManipulation()
		{
			return tickManipulation;
		}

		/** Quests that complete this option outright; empty for an ordinary method. */
		public List<String> getQuests()
		{
			return quests;
		}

		public String getTitle()
		{
			return title;
		}

		public String getDetail()
		{
			return detail;
		}

		public String getWikiUrl()
		{
			return wikiUrl;
		}
	}

	private final Type type;
	private final String title;
	private final String detail;
	private final String wikiUrl;
	private final List<Option> alternatives;
	/**
	 * True for the one step that <em>is</em> the goal, as opposed to something
	 * needed on the way to it. The panel groups runs of prerequisite quests and
	 * diaries into a single "needed first" list, and the destination must not
	 * be swept into that list alongside them.
	 */
	private final boolean target;
	/**
	 * Experience this step costs, or 0 where the notion doesn't apply. Carried
	 * as a number so the panel can place it where its layout wants it, instead
	 * of having it welded into the title.
	 */
	private long xpCost;
	/**
	 * The level range a TRAIN_SKILL step covers, and the skill it covers it in.
	 * Null/0 for every other step type. Carried as numbers because the panel
	 * has to be able to restate a run of brackets as one range - recovering
	 * "76" and "99" by parsing the title back apart is not a thing to build on.
	 */
	private String skill;
	private int levelFrom;
	private int levelTo;

	/** Single-option convenience constructor - used by every step type except TRAIN_SKILL. */
	public PathStep(Type type, String title, String detail, String wikiUrl)
	{
		this(type, title, detail, wikiUrl, false);
	}

	/** As above, for the step that is the goal itself. */
	public PathStep(Type type, String title, String detail, String wikiUrl, boolean target)
	{
		this.target = target;
		this.type = type;
		this.title = title;
		this.detail = detail;
		this.wikiUrl = wikiUrl;
		this.alternatives = Collections.singletonList(new Option(title, detail, wikiUrl));
	}

	/** Multi-option constructor - used by TRAIN_SKILL when a level range has several viable methods. */
	public PathStep(Type type, String title, List<Option> alternatives)
	{
		this.target = false;
		this.type = type;
		this.title = title;
		this.alternatives = alternatives;
		// detail/wikiUrl mirror the first (highest-priority) alternative for
		// any rendering path that only looks at the single-option fields.
		Option first = alternatives.isEmpty() ? null : alternatives.get(0);
		this.detail = first != null ? first.getDetail() : null;
		this.wikiUrl = first != null ? first.getWikiUrl() : null;
	}

	public boolean isTarget()
	{
		return target;
	}

	public long getXpCost()
	{
		return xpCost;
	}

	public PathStep withXpCost(long xp)
	{
		this.xpCost = xp;
		return this;
	}

	public PathStep withLevels(String skill, int levelFrom, int levelTo)
	{
		this.skill = skill;
		this.levelFrom = levelFrom;
		this.levelTo = levelTo;
		return this;
	}

	/** True when this step carries a real level range, i.e. it trains a skill. */
	public boolean hasLevels()
	{
		return skill != null && levelTo > levelFrom;
	}

	public String getSkill()
	{
		return skill;
	}

	public int getLevelFrom()
	{
		return levelFrom;
	}

	public int getLevelTo()
	{
		return levelTo;
	}

	public Type getType()
	{
		return type;
	}

	public String getTitle()
	{
		return title;
	}

	public String getDetail()
	{
		return detail;
	}

	public String getWikiUrl()
	{
		return wikiUrl;
	}

	/** Always has at least one entry when the step has any content at all. */
	public List<Option> getAlternatives()
	{
		return alternatives;
	}

	public boolean hasChoice()
	{
		return alternatives.size() > 1;
	}
}

