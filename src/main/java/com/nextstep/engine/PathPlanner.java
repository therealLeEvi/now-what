package com.nextstep.engine;

import com.nextstep.data.ItemSourceDatabase;
import com.nextstep.data.TrainingMethodDatabase;
import com.nextstep.model.Activity;
import com.nextstep.model.Goal;
import com.nextstep.model.ItemSource;
import com.nextstep.model.Path;
import com.nextstep.model.PathStep;
import com.nextstep.model.PlayerState;
import com.nextstep.model.PrerequisiteChain;
import com.nextstep.model.Requirement;
import java.util.ArrayList;
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
					// Nothing was produced by the chain walk, so every requirement is
					// already satisfied - the activity just isn't the kind of thing
					// that gets marked "done" (a boss, a minigame, an unlock).
					requirementsAlreadyMet = steps.isEmpty() && warnings.isEmpty();
					steps.add(new PathStep(stepTypeFor(target.getCategory()), target.getName(),
						"Target: " + target.getCategory(), target.getWikiUrl(), true));
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

	/** Short human-readable label for a single requirement, used both standalone and inside ANY_OF choices. */
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
				steps.add(new PathStep(PathStep.Type.ACQUIRE_ITEM,
					"Buy " + quantity + "x " + source.getDisplayName(),
					"Shop: " + source.getDetail(), source.getWikiUrl()));
				break;
			case MONSTER_DROP:
			{
				String kcNote = source.getEstimatedKc() != null
					? ("~" + String.format(java.util.Locale.ROOT, "%,d", source.getEstimatedKc() * quantity) + " attempts for " + quantity
					+ " drop(s) \u2014 rough average, not a guaranteed rate")
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
