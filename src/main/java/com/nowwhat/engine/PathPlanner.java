package com.nowwhat.engine;

import com.nowwhat.data.ItemSourceDatabase;
import com.nowwhat.data.TrainingMethodDatabase;
import com.nowwhat.model.Activity;
import com.nowwhat.model.CombatAchievementTier;
import com.nowwhat.model.Goal;
import com.nowwhat.model.ItemSource;
import com.nowwhat.model.Path;
import com.nowwhat.model.PathStep;
import com.nowwhat.model.PlayerState;
import com.nowwhat.model.PrerequisiteChain;
import com.nowwhat.model.Requirement;
import com.nowwhat.model.TrainingMethod;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns a Goal into an ordered Path of concrete actions - the piece
 * neither Goal Planner nor QuestPath provide (see README). Delegates to:
 * <ul>
 *   <li>{@link SkillPathPlanner} for SKILL_LEVEL goals and for expanding any
 *       SKILL_LEVEL requirement found along an activity's prerequisite chain</li>
 *   <li>{@link PrerequisiteResolver} for ACTIVITY goals (quests/diaries/etc)</li>
 *   <li>recursive item-source resolution for ITEM_QUANTITY goals, including
 *       crafting recipes that bottom out in other items</li>
 * </ul>
 */
public class PathPlanner
{
	private final PrerequisiteResolver prerequisiteResolver;
	private final TrainingMethodDatabase trainingMethodDatabase;
	private final ItemSourceDatabase itemSourceDatabase;
	private final SkillPathPlanner skillPathPlanner = new SkillPathPlanner();
	private final Map<String, Activity> activitiesById;

	public PathPlanner(List<Activity> allActivities, PrerequisiteResolver prerequisiteResolver,
		TrainingMethodDatabase trainingMethodDatabase, ItemSourceDatabase itemSourceDatabase)
	{
		this.prerequisiteResolver = prerequisiteResolver;
		this.trainingMethodDatabase = trainingMethodDatabase;
		this.itemSourceDatabase = itemSourceDatabase;
		this.activitiesById = new java.util.HashMap<>();
		for (Activity a : allActivities)
		{
			activitiesById.put(a.getId(), a);
		}
	}

	public Path plan(Goal goal, PlayerState state)
	{
		List<PathStep> steps = new ArrayList<>();
		List<String> warnings = new ArrayList<>();
		boolean alreadyComplete;
		boolean requirementsAlreadyMet = false;

		switch (goal.getType())
		{
			case SKILL_LEVEL:
			{
				int current = state.getSkillLevel(goal.getSkillName());
				alreadyComplete = current >= goal.getTargetLevel();
				if (!alreadyComplete)
				{
					steps.addAll(skillPathPlanner.plan(goal.getSkillName(), current, goal.getTargetLevel(),
						trainingMethodDatabase.getMethodsFor(goal.getSkillName()),
						goal.isAllowTickManipulation(), warnings));
				}
				break;
			}
			case ITEM_QUANTITY:
			{
				// Plan for what's still missing, not the full target: asking for 3
				// of something you already have 2 of is a one-item job. Unless the
				// player said not to count what they own - someone who wants the
				// route to an item they already have one of gets told "already
				// done" otherwise, and the goal becomes unaskable.
				ItemSource owned = itemSourceDatabase.getSource(goal.getItemId());
				int have = goal.isCountOwned() && owned != null && owned.getGameItemId() != null
					? state.getOwnedCount(owned.getGameItemId())
					: 0;
				int remaining = goal.getTargetQuantity() - have;

				alreadyComplete = remaining <= 0;
				if (alreadyComplete)
				{
					// Say what made it complete. "Already done" on its own looks
					// like the plugin has no route, when what it means is that the
					// item is already sitting in the bank.
					warnings.add("You already have " + have
						+ ", counting your bank, inventory and gear.");
				}
				if (!alreadyComplete)
				{
					Set<String> visiting = new LinkedHashSet<>();
					planItem(goal.getItemId(), remaining, state, steps, warnings, visiting,
						goal.isAllowTickManipulation());

					if (!state.isBankSnapshotAvailable())
					{
						// Say so rather than quietly telling someone to go buy
						// something that's already sitting in their bank.
						warnings.add("Bank contents unknown \u2014 open your bank once so this "
							+ "can account for what you already have.");
					}
					else if (have > 0)
					{
						warnings.add("You already have " + have + " \u2014 planning for the remaining "
							+ remaining + ".");
					}
				}
				break;
			}
			case ACTIVITY:
			{
				Activity target = activitiesById.get(goal.getActivityId());
				if (target == null)
				{
					warnings.add("Unknown activity id: " + goal.getActivityId());
					return new Path(goal, false, steps, warnings);
				}
				PrerequisiteChain chain = prerequisiteResolver.resolve(target, state);
				alreadyComplete = chain.isAlreadyUnlocked();
				if (!alreadyComplete)
				{
					for (Activity prereq : chain.getRequiredActivitiesInOrder())
					{
						steps.add(new PathStep(stepTypeFor(prereq.getCategory()), prereq.getName(),
							prereq.getCategory() + (prereq.getDescription() != null ? " \u2014 " + prereq.getDescription() : ""),
							prereq.getWikiUrl()));
					}
					for (Requirement req : chain.getStandaloneRequirements())
					{
						expandStandaloneRequirement(req, state, steps, warnings, goal.isAllowTickManipulation());
					}
					warnings.addAll(chain.getUnresolvedReferences());
					// A Combat Achievement tier is a points total, not a set of
					// requirements, so "you meet every requirement" was true and
					// useless: the combat level was the only thing it checked.
					// What stands between the player and the tier is the points,
					// so the points become the step.
					CombatAchievementTier tier = CombatAchievementTier.fromKey(target.getId());
					if (tier != null)
					{
						steps.add(combatAchievementPointsStep(tier, state.getCombatAchievementPoints()));
					}
					// Nothing was produced by the chain walk, so every requirement is
					// already satisfied - the activity just isn't the kind of thing
					// that gets marked "done" (a boss, a minigame, an unlock).
					requirementsAlreadyMet = steps.isEmpty() && warnings.isEmpty();
					steps.add(new PathStep(stepTypeFor(target.getCategory()), target.getName(),
						"Target: " + target.getCategory(), target.getWikiUrl(), true));
				}
				break;
			}
			case BASE_STATS:
			{
				List<Shortfall> shortfalls = new ArrayList<>();
				for (String skill : PLANNABLE_SKILLS)
				{
					int current = state.getSkillLevel(skill);
					if (current < goal.getTargetLevel())
					{
						shortfalls.add(new Shortfall(skill, current, goal.getTargetLevel(),
							XpTable.xpBetween(current, goal.getTargetLevel())));
					}
				}
				alreadyComplete = shortfalls.isEmpty();
				if (!alreadyComplete)
				{
					// Cheapest first. The player asked for a floor, not an order,
					// so the order is ours to choose - and the one that gets the
					// most skills over the line soonest is the one that keeps the
					// list visibly shrinking.
					shortfalls.sort(Comparator.comparingLong(sf -> sf.xp));
					long total = 0;
					for (Shortfall sf : shortfalls)
					{
						total += sf.xp;
					}
					warnings.add(shortfalls.size() + " skill"
						+ (shortfalls.size() == 1 ? " is" : "s are") + " below "
						+ goal.getTargetLevel() + ", "
						+ XpTable.formatXp(total) + " xp in all \u2014 cheapest first.");
					for (Shortfall sf : shortfalls)
					{
						steps.addAll(skillPathPlanner.plan(sf.skill, sf.from, sf.to,
							trainingMethodDatabase.getMethodsFor(sf.skill),
							goal.isAllowTickManipulation(), warnings));
					}
				}
				break;
			}
			case TOTAL_LEVEL:
			{
				// Every skill counts towards the total, but only a skill we can
				// describe how to train is a candidate to take levels from.
				// Sailing is the case that proved it: its early levels are the
				// cheapest in the game, so the greedy took all twelve from it
				// and produced a plan with no steps in it at all.
				Map<String, Integer> levels = new LinkedHashMap<>();
				List<String> untrainable = new ArrayList<>();
				int current = 0;
				for (String skill : PLANNABLE_SKILLS)
				{
					int level = state.getSkillLevel(skill);
					current += level;
					if (hasTrainingData(skill))
					{
						levels.put(skill, level);
					}
					else
					{
						untrainable.add(capitalize(skill));
					}
				}
				// Hitpoints counts towards the total but cannot be chosen: it
				// rises out of combat xp on its own. Counting it and then
				// planning it would be telling the player to go and train
				// something there is no way to go and train.
				current += state.getSkillLevel("HITPOINTS");

				int needed = goal.getTargetLevel() - current;
				alreadyComplete = needed <= 0;
				if (alreadyComplete)
				{
					warnings.add("Your total level is already " + current + ".");
					break;
				}

				if (!untrainable.isEmpty())
				{
					warnings.add("No training data for " + String.join(", ", untrainable)
						+ ", so no levels were planned there \u2014 they still count "
						+ "towards your total.");
				}

				int ceiling = levels.size() * 99 + 99;
				if (goal.getTargetLevel() > ceiling)
				{
					warnings.add("The highest total level these skills reach is " + ceiling
						+ ", so " + goal.getTargetLevel() + " is not reachable.");
				}

				// Take the cheapest next level, over and over. Each skill's
				// levels get dearer as it climbs, so repeatedly taking the
				// cheapest available one is also the cheapest set of that many
				// levels overall - there is no arrangement that beats it.
				Map<String, Integer> targets = new LinkedHashMap<>(levels);
				int taken = 0;
				while (taken < needed)
				{
					String best = null;
					long bestCost = Long.MAX_VALUE;
					for (Map.Entry<String, Integer> entry : targets.entrySet())
					{
						int at = entry.getValue();
						if (at >= 99)
						{
							continue;
						}
						long cost = XpTable.xpForLevel(at + 1) - Math.max(
							XpTable.xpForLevel(at), state.getSkillXp(entry.getKey()));
						if (cost < bestCost)
						{
							bestCost = cost;
							best = entry.getKey();
						}
					}
					if (best == null)
					{
						warnings.add("Every skill with training data is already 99 \u2014 "
							+ (needed - taken) + " level"
							+ (needed - taken == 1 ? "" : "s") + " short of "
							+ goal.getTargetLevel() + ".");
						break;
					}
					targets.put(best, targets.get(best) + 1);
					taken++;
				}

				List<Shortfall> picked = new ArrayList<>();
				for (Map.Entry<String, Integer> entry : targets.entrySet())
				{
					int from = levels.get(entry.getKey());
					if (entry.getValue() > from)
					{
						picked.add(new Shortfall(entry.getKey(), from, entry.getValue(),
							XpTable.xpBetween(from, entry.getValue())));
					}
				}
				picked.sort(Comparator.comparingLong(sf -> sf.xp));

				long total = 0;
				for (Shortfall sf : picked)
				{
					total += sf.xp;
				}
				warnings.add(taken + " level" + (taken == 1 ? "" : "s")
					+ " from " + current + " to " + (current + taken) + ", "
					+ XpTable.formatXp(total) + " xp in all \u2014 the cheapest "
					+ "next level each time.");

				for (Shortfall sf : picked)
				{
					steps.addAll(skillPathPlanner.plan(sf.skill, sf.from, sf.to,
						trainingMethodDatabase.getMethodsFor(sf.skill),
						goal.isAllowTickManipulation(), warnings));
				}
				break;
			}
			default:
				warnings.add("Unsupported goal type: " + goal.getType());
				alreadyComplete = false;
		}

		return new Path(goal, alreadyComplete, steps, warnings, requirementsAlreadyMet);
	}

	/**
	 * The skills a plan can actually send someone to train.
	 * <p>
	 * Hitpoints is absent on purpose. It counts towards a total level and it is
	 * in the game's skill list, but there is no such thing as going and
	 * training it - it rises out of combat xp, which is why its one entry in
	 * methods.json has no rate and says so. A total level plan that said
	 * "Hitpoints 70 to 73" would be telling the player to do something that
	 * cannot be done.
	 * <p>
	 * Sailing is present. There is no Sailing data yet, so asking for it
	 * produces SkillPathPlanner's "no training method data" warning - which is
	 * the honest answer to "get every skill to 80", and better than quietly
	 * leaving a skill out of a goal that said "every".
	 */
	private static final List<String> PLANNABLE_SKILLS = java.util.Collections.unmodifiableList(
		java.util.Arrays.asList(
			"ATTACK", "DEFENCE", "STRENGTH", "RANGED", "PRAYER", "MAGIC",
			"COOKING", "WOODCUTTING", "FLETCHING", "FISHING", "FIREMAKING", "CRAFTING",
			"SMITHING", "MINING", "HERBLORE", "AGILITY", "THIEVING", "SLAYER", "FARMING",
			"RUNECRAFT", "HUNTER", "CONSTRUCTION", "SAILING"));

	/**
	 * Whether methods.json has anything at all to say about training this.
	 * <p>
	 * Deliberately "any method", not "any method with an xp/hr". Slayer and
	 * Farming are recorded without rates on purpose - Slayer comes a task at a
	 * time and Farming a run at a time, and an hourly figure would be a
	 * fiction - but "do Slayer tasks" is still a real answer to "where do I get
	 * a level". Requiring a rate excluded both of them from total level plans,
	 * which is a worse answer than one without a time estimate on it.
	 */
	private boolean hasTrainingData(String skill)
	{
		return !trainingMethodDatabase.getMethodsFor(skill).isEmpty();
	}

	private static String capitalize(String skill)
	{
		return skill.charAt(0) + skill.substring(1).toLowerCase(java.util.Locale.ROOT);
	}

	/** One skill that is short of where a goal needs it, and what closing that costs. */
	private static final class Shortfall
	{
		private final String skill;
		private final int from;
		private final int to;
		private final long xp;

		private Shortfall(String skill, int from, int to, long xp)
		{
			this.skill = skill;
			this.from = from;
			this.to = to;
			this.xp = xp;
		}
	}

	/**
	 * A SKILL_LEVEL requirement gets expanded into real training steps via
	 * SkillPathPlanner. Everything else (BOSS_KC, QUEST_POINTS, COMBAT_LEVEL,
	 * COMBAT_ACHIEVEMENT_TIER) has no further data-driven expansion in this
	 * scaffold, so it's surfaced as an INFO step using the same description
	 * logic the panel already had, plus the requirement's own `hint` field
	 * if one was authored for it.
	 */
	/**
	 * Quests and diary tiers get their own step types because the panel hands
	 * both off to Quest Helper rather than describing how to do them; everything
	 * else is a plain activity.
	 */
	private static PathStep.Type stepTypeFor(Activity.Category category)
	{
		switch (category)
		{
			case QUEST:
				return PathStep.Type.DO_QUEST;
			case DIARY:
				return PathStep.Type.DO_DIARY;
			default:
				return PathStep.Type.DO_ACTIVITY;
		}
	}

	private void expandStandaloneRequirement(Requirement req, PlayerState state, List<PathStep> steps,
		List<String> warnings, boolean allowTickManipulation)
	{
		if (req.getType() == Requirement.Type.SKILL_LEVEL)
		{
			int current = state.getSkillLevel(req.getValue());
			if (current >= req.getThreshold())
			{
				return; // already met, nothing to plan
			}
			List<PathStep> skillSteps = skillPathPlanner.plan(req.getValue(), current, req.getThreshold(),
				trainingMethodDatabase.getMethodsFor(req.getValue()), allowTickManipulation, warnings);
			if (skillSteps.isEmpty() && req.getHint() != null)
			{
				// Fall back to the authored hint if we have no structured method data for this skill.
				steps.add(new PathStep(PathStep.Type.TRAIN_SKILL,
					req.getValue() + " to " + req.getThreshold(), req.getHint(), null));
			}
			else
			{
				steps.addAll(skillSteps);
			}
			return;
		}

		if (req.getType() == Requirement.Type.COMBINED_SKILL_LEVEL)
		{
			String[] skillNames = req.getValue().split(",");
			int combinedCurrent = 0;
			for (String s : skillNames)
			{
				combinedCurrent += state.getSkillLevel(s.trim());
			}
			if (combinedCurrent >= req.getThreshold())
			{
				return; // already met
			}
			// Deliberately not decomposed into per-skill training steps: raising
			// Attack vs Strength vs splitting between them are all valid ways to
			// close the gap, and there's no basis here to prefer one split over
			// another the way there is for a single-skill requirement - that
			// would need an explicit player preference this scaffold doesn't
			// collect yet, so it's surfaced as information instead of a plan.
			String detail = "Currently " + combinedCurrent + "/" + req.getThreshold() + " combined. Raise "
				+ String.join(" and/or ", skillNames) + " in whatever split suits you"
				+ (req.getHint() != null ? " \u2014 " + req.getHint() : "");
			steps.add(new PathStep(PathStep.Type.INFO, describeRequirement(req), detail, null));
			return;
		}

		if (req.getType() == Requirement.Type.ANY_OF)
		{
			// Not recursively expanded into full sub-chains per branch (that's
			// a meaningfully bigger undertaking - each branch could itself
			// need its own quest/skill chain resolved). Presented as an
			// explicit choice instead, same philosophy as multiple training
			// methods: don't silently collapse to one branch when the data
			// doesn't give a basis to prefer one over another.
			List<PathStep.Option> options = new ArrayList<>();
			for (Requirement child : req.getAnyOf())
			{
				options.add(new PathStep.Option(describeRequirement(child), child.getHint(), null));
			}
			if (!options.isEmpty())
			{
				steps.add(new PathStep(PathStep.Type.INFO, "Any ONE of the following", options));
			}
			return;
		}

		steps.add(new PathStep(PathStep.Type.INFO, describeRequirement(req), req.getHint(), null));
	}

	/**
	 * How many Combat Achievement points stand between the player and a tier,
	 * and what that means in tasks.
	 * <p>
	 * The task count is a floor, not a forecast: it assumes every point comes
	 * from the tier's own tasks. Lower-tier tasks still count toward the total,
	 * and they are usually the quicker points, which the detail says.
	 */
	static PathStep combatAchievementPointsStep(CombatAchievementTier tier, int points)
	{
		String where = "Find them in the Character Summary tab, then Combat Achievements, "
			+ "filtered to " + tier.getLabel() + " and Incomplete.";
		if (points < 0)
		{
			return new PathStep(PathStep.Type.DO_ACTIVITY,
				"Reach " + format(tier.getPointsToUnlock()) + " Combat Achievement points",
				tier.getLabel() + " tasks are worth " + tier.getPointsPerTask() + " points each, and "
					+ "tasks from any easier tier still count toward the total. "
					+ "Log in for your current total. " + where,
				tier.getTaskListUrl());
		}

		int missing = tier.getPointsToUnlock() - points;
		if (missing <= 0)
		{
			return new PathStep(PathStep.Type.DO_ACTIVITY,
				"Claim the " + tier.getLabel() + " tier rewards",
				"You have " + format(points) + " points, past the " + format(tier.getPointsToUnlock())
					+ " this tier needs. Speak to Ghommal at the Warriors' Guild for the rewards.",
				"https://oldschool.runescape.wiki/w/Combat_Achievements#Rewards");
		}

		int tasks = (missing + tier.getPointsPerTask() - 1) / tier.getPointsPerTask();
		return new PathStep(PathStep.Type.DO_ACTIVITY,
			"Earn " + format(missing) + " more Combat Achievement points",
			"You have " + format(points) + " of the " + format(tier.getPointsToUnlock()) + " needed. "
				+ "That is " + tasks + " " + tier.getLabel() + " task" + (tasks == 1 ? "" : "s")
				+ " at " + tier.getPointsPerTask() + " points each (the tier has " + tier.getTaskCount()
				+ "), or more tasks from easier tiers you skipped, which are often the faster points. "
				+ where,
			tier.getTaskListUrl());
	}

	/** True when {@code b} already covers {@code a}: same kind, same skill, at least as high. */
	private static boolean sameRequirement(Requirement a, Requirement b)
	{
		return b != null && a.getType() == b.getType()
			&& java.util.Objects.equals(a.getValue(), b.getValue())
			&& b.getThreshold() >= a.getThreshold();
	}

	private static String format(int n)
	{
		return String.format(java.util.Locale.ROOT, "%,d", n);
	}

	/** Shortfall human-readable label for a single requirement, used both standalone and inside ANY_OF choices. */
	private String describeRequirement(Requirement req)
	{
		switch (req.getType())
		{
			case SKILL_LEVEL:
				return req.getValue() + " level " + req.getThreshold();
			case BOSS_KC:
				return req.getValue() + ": " + req.getThreshold() + " kills";
			case QUEST_POINTS:
				return req.getThreshold() + " Quest Points";
			case COMBAT_LEVEL:
				return "Combat level " + req.getThreshold();
			case COMBAT_ACHIEVEMENT_TIER:
				return "Combat Achievements: " + req.getValue() + " tier";
			case QUEST_COMPLETE:
				return "Complete " + req.getValue();
			case DIARY_COMPLETE:
				return req.getValue() + " diary";
			case COMBINED_SKILL_LEVEL:
				return req.getThreshold() + " combined " + String.join("+", req.getValue().split(","));
			default:
				return req.getType() + " " + req.getValue() + " " + req.getThreshold();
		}
	}

	/**
	 * Recursively resolves how to obtain `quantity` of `itemId`, expanding
	 * CRAFT recipes into their ingredients (and any skill requirement the
	 * craft needs) until everything bottoms out in a GE_BUY / SHOP /
	 * MONSTER_DROP leaf, or we give up with a warning.
	 */
	private void planItem(String itemId, int quantity, PlayerState state, List<PathStep> steps,
		List<String> warnings, Set<String> visiting, boolean allowTickManipulation)
	{
		String key = itemId.toUpperCase();
		if (visiting.contains(key))
		{
			warnings.add("Circular crafting recipe detected involving " + itemId);
			return;
		}

		ItemSource source = itemSourceDatabase.getSource(itemId);
		if (source == null)
		{
			warnings.add("No source data for item: " + itemId + " \u2014 check the wiki manually.");
			return;
		}

		visiting.add(key);

		// Any source can gate on a requirement, not just a crafting recipe: you
		// cannot farm a raid drop without the stats to survive the raid. Expanded
		// before the acquisition step so the prerequisite reads first.
		if (source.getSkillRequirement() != null)
		{
			expandStandaloneRequirement(source.getSkillRequirement(), state, steps, warnings, allowTickManipulation);
		}

		switch (source.getType())
		{
			case GE_BUY:
				steps.add(new PathStep(PathStep.Type.ACQUIRE_ITEM,
					"Buy " + quantity + "x " + source.getDisplayName(),
					"Grand Exchange" + (source.getDetail() != null ? " \u2014 " + source.getDetail() : ""),
					source.getWikiUrl()));
				break;
			case SHOP:
			{
				// A points shop that only quotes its price is a dead end. "250
				// points" says nothing about where points come from, how long
				// they take or what to bring, and the player is left to go and
				// find out somewhere else - which is the whole thing this panel
				// is for. Where the currency has an activity behind it, that
				// activity becomes a step in its own right, ahead of the
				// purchase, carrying its own description and kit.
				String earns = source.getCurrencyFrom();
				Activity earner = earns == null ? null : activitiesById.get(earns);
				if (earner != null)
				{
					// The earner's own entry requirements come first. The herb
					// sack only asks for 58 Herblore, but its points come from
					// Tithe Farm, which asks for 34 Farming - and without this the
					// plan sent someone at 20 Farming to a minigame they could not
					// enter. One the item already asked for is not repeated.
					for (Requirement entry : earner.getRequirements())
					{
						if (!RequirementEvaluator.isMet(entry, state)
							&& !sameRequirement(entry, source.getSkillRequirement()))
						{
							expandStandaloneRequirement(entry, state, steps, warnings, allowTickManipulation);
						}
					}
					steps.add(new PathStep(stepTypeFor(earner.getCategory()), earner.getName(),
						earner.getCategory() + (earner.getDescription() != null
							? " \u2014 " + earner.getDescription()
							: ""),
						earner.getWikiUrl()));
				}
				else if (earns != null)
				{
					warnings.add("Unknown activity id: " + earns);
				}
				steps.add(new PathStep(PathStep.Type.ACQUIRE_ITEM,
					"Buy " + quantity + "x " + source.getDisplayName(),
					"Shop: " + source.getDetail(), source.getWikiUrl()));
				break;
			}
			case MONSTER_DROP:
			{
				String kcNote = source.getEstimatedKc() != null
					? ("~" + String.format(java.util.Locale.ROOT, "%,d", source.getEstimatedKc() * quantity)
					+ " " + source.getAttemptUnit() + " for " + quantity
					+ (quantity == 1 ? " drop" : " drops") + " \u2014 rough average, not a guaranteed rate")
					: "Drop rate unknown \u2014 check the wiki's droptable page";
				steps.add(new PathStep(PathStep.Type.KILL_BOSS,
					source.getDisplayName() + " from " + source.getDetail(),
					kcNote, source.getWikiUrl()));
				break;
			}
			case QUEST_REWARD:
				steps.add(new PathStep(PathStep.Type.DO_QUEST,
					"Complete quest for " + source.getDisplayName(),
					source.getDetail(), source.getWikiUrl()));
				break;
			case CRAFT:
				for (ItemSource.Ingredient ingredient : source.getIngredients())
				{
					planItem(ingredient.getItemId(), ingredient.getQuantity() * quantity, state, steps,
						warnings, visiting, allowTickManipulation);
				}
				steps.add(new PathStep(PathStep.Type.ACQUIRE_ITEM,
					"Craft " + quantity + "x " + source.getDisplayName(),
					source.getDetail(), source.getWikiUrl()));
				break;
			default:
				warnings.add("Unknown source type for " + itemId);
		}

		visiting.remove(key);
	}
}
