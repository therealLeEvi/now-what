package com.nextstep.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.nextstep.data.ItemSourceDatabase;
import com.nextstep.data.TrainingMethodDatabase;
import com.nextstep.model.Activity;
import com.nextstep.model.Goal;
import com.nextstep.model.ItemSource;
import com.nextstep.model.Path;
import com.nextstep.model.PathStep;
import com.nextstep.model.PlayerState;
import com.nextstep.model.Requirement;
import com.nextstep.model.TrainingMethod;
import com.nextstep.model.TrainingMethod.Intensity;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class PathPlannerTest
{
	@Test
	public void skillGoalAlreadyMetIsReportedAsComplete()
	{
		PathPlanner planner = new PathPlanner(Collections.emptyList(),
			new PrerequisiteResolver(Collections.emptyList()),
			TrainingMethodDatabase.of(Collections.emptyList()),
			ItemSourceDatabase.of(Collections.emptyList()));

		PlayerState state = new PlayerState();
		state.setSkillLevel("AGILITY", 99);

		Path path = planner.plan(Goal.skillLevel("AGILITY", 70, false), state);

		assertTrue(path.isAlreadyComplete());
		assertTrue(path.getSteps().isEmpty());
	}

	@Test
	public void skillGoalDelegatesToSkillPathPlannerWithMethodData()
	{
		TrainingMethod method = new TrainingMethod("AGILITY", 1, 99, "Some course", 20000, null, null, Intensity.NORMAL);
		PathPlanner planner = new PathPlanner(Collections.emptyList(),
			new PrerequisiteResolver(Collections.emptyList()),
			TrainingMethodDatabase.of(Arrays.asList(method)),
			ItemSourceDatabase.of(Collections.emptyList()));

		PlayerState state = new PlayerState();
		state.setSkillLevel("AGILITY", 1);

		Path path = planner.plan(Goal.skillLevel("AGILITY", 50, false), state);

		assertEquals(false, path.isAlreadyComplete());
		assertEquals(1, path.getSteps().size());
		assertEquals(PathStep.Type.TRAIN_SKILL, path.getSteps().get(0).getType());
	}

	@Test
	public void activityGoalExpandsSkillRequirementsIntoTrainingSteps()
	{
		TrainingMethod method = new TrainingMethod("AGILITY", 1, 99, "Some course", 20000, null, null, Intensity.NORMAL);
		Activity target = new Activity("boss", "Test Boss", Activity.Category.PVM, Arrays.asList(
			new Requirement(Requirement.Type.SKILL_LEVEL, "AGILITY", 50)
		));

		PathPlanner planner = new PathPlanner(Arrays.asList(target),
			new PrerequisiteResolver(Arrays.asList(target)),
			TrainingMethodDatabase.of(Arrays.asList(method)),
			ItemSourceDatabase.of(Collections.emptyList()));

		PlayerState state = new PlayerState();
		state.setSkillLevel("AGILITY", 1);

		Path path = planner.plan(Goal.activity("boss", "Test Boss", false), state);

		// One TRAIN_SKILL step (expanded from the requirement) + one final
		// DO_ACTIVITY step for the target itself.
		assertEquals(2, path.getSteps().size());
		assertEquals(PathStep.Type.TRAIN_SKILL, path.getSteps().get(0).getType());
		assertEquals(PathStep.Type.DO_ACTIVITY, path.getSteps().get(1).getType());
	}

	@Test
	public void anyOfRequirementIsPresentedAsAChoiceNotAutoResolved()
	{
		Activity warriorsGuild = new Activity("wg", "Warriors' Guild entry", Activity.Category.MINIGAME, Arrays.asList(
			Requirement.anyOf(
				new Requirement(Requirement.Type.SKILL_LEVEL, "ATTACK", 99),
				new Requirement(Requirement.Type.SKILL_LEVEL, "STRENGTH", 99),
				new Requirement(Requirement.Type.COMBINED_SKILL_LEVEL, "ATTACK,STRENGTH", 130)
			)
		));

		PathPlanner planner = new PathPlanner(Arrays.asList(warriorsGuild),
			new PrerequisiteResolver(Arrays.asList(warriorsGuild)),
			TrainingMethodDatabase.of(Collections.emptyList()),
			ItemSourceDatabase.of(Collections.emptyList()));

		PlayerState state = new PlayerState();
		state.setSkillLevel("ATTACK", 60);
		state.setSkillLevel("STRENGTH", 60);

		Path path = planner.plan(Goal.activity("wg", "Warriors' Guild entry", false), state);

		PathStep choiceStep = path.getSteps().get(0);
		assertEquals(PathStep.Type.INFO, choiceStep.getType());
		assertEquals(3, choiceStep.getAlternatives().size()); // all three branches shown, none picked for the player
	}

	@Test
	public void itemGoalResolvesBuyableItemDirectly()
	{
		ItemSource logs = new ItemSource("LOGS", "Logs", ItemSource.SourceType.GE_BUY,
			null, null, null, null, Collections.emptyList());

		PathPlanner planner = new PathPlanner(Collections.emptyList(),
			new PrerequisiteResolver(Collections.emptyList()),
			TrainingMethodDatabase.of(Collections.emptyList()),
			ItemSourceDatabase.of(Arrays.asList(logs)));

		Path path = planner.plan(Goal.itemQuantity("LOGS", 10, "Logs", false), new PlayerState());

		assertEquals(1, path.getSteps().size());
		assertEquals(PathStep.Type.ACQUIRE_ITEM, path.getSteps().get(0).getType());
	}

	@Test
	public void craftedItemRecursesThroughIngredients()
	{
		ItemSource berries = new ItemSource("CADAVA_BERRIES", "Cadava berries", ItemSource.SourceType.GE_BUY,
			null, null, null, null, Collections.emptyList());
		ItemSource janger = new ItemSource("JANGERBERRIES", "Jangerberries", ItemSource.SourceType.GE_BUY,
			null, null, null, null, Collections.emptyList());
		ItemSource brew = new ItemSource("ZAMORAK_BREW", "Zamorak brew", ItemSource.SourceType.CRAFT,
			"Mix at a workbench", null, null,
			new Requirement(Requirement.Type.SKILL_LEVEL, "HERBLORE", 78),
			Arrays.asList(
				new ItemSource.Ingredient("CADAVA_BERRIES", 1),
				new ItemSource.Ingredient("JANGERBERRIES", 1)
			));

		PathPlanner planner = new PathPlanner(Collections.emptyList(),
			new PrerequisiteResolver(Collections.emptyList()),
			TrainingMethodDatabase.of(Collections.emptyList()),
			ItemSourceDatabase.of(Arrays.asList(berries, janger, brew)));

		PlayerState state = new PlayerState();
		state.setSkillLevel("HERBLORE", 40); // below the 78 requirement

		Path path = planner.plan(Goal.itemQuantity("ZAMORAK_BREW", 1, "Zamorak brew", false), state);

		// The skill requirement has no bundled training data and no authored
		// hint here, so it correctly produces zero steps (just a warning,
		// checked below) rather than a step with nothing useful to say.
		// Expect: two ingredient buys, then the craft step itself.
		assertEquals(3, path.getSteps().size());
		assertEquals(PathStep.Type.ACQUIRE_ITEM, path.getSteps().get(path.getSteps().size() - 1).getType());
		assertTrue(path.getWarnings().stream().anyMatch(w -> w.contains("HERBLORE")));
	}

	@Test
	public void circularCraftingRecipeIsReportedNotInfiniteLooped()
	{
		// A requires B, B requires A - must terminate with a warning, not hang.
		ItemSource itemA = new ItemSource("A", "Item A", ItemSource.SourceType.CRAFT, null, null, null, null,
			Arrays.asList(new ItemSource.Ingredient("B", 1)));
		ItemSource itemB = new ItemSource("B", "Item B", ItemSource.SourceType.CRAFT, null, null, null, null,
			Arrays.asList(new ItemSource.Ingredient("A", 1)));

		PathPlanner planner = new PathPlanner(Collections.emptyList(),
			new PrerequisiteResolver(Collections.emptyList()),
			TrainingMethodDatabase.of(Collections.emptyList()),
			ItemSourceDatabase.of(Arrays.asList(itemA, itemB)));

		Path path = planner.plan(Goal.itemQuantity("A", 1, "Item A", false), new PlayerState());

		assertTrue(path.getWarnings().stream().anyMatch(w -> w.contains("Circular")));
	}

	/**
	 * Every skill that has a method gets one, so a base-stats or total-level
	 * plan has something to say about all of them rather than a wall of
	 * "no training method data".
	 */
	private static PathPlanner everySkillTrainable()
	{
		java.util.List<TrainingMethod> methods = new java.util.ArrayList<>();
		for (String skill : Arrays.asList("ATTACK", "DEFENCE", "STRENGTH", "RANGED", "PRAYER",
			"MAGIC", "COOKING", "WOODCUTTING", "FLETCHING", "FISHING", "FIREMAKING", "CRAFTING",
			"SMITHING", "MINING", "HERBLORE", "AGILITY", "THIEVING", "SLAYER", "FARMING",
			"RUNECRAFT", "HUNTER", "CONSTRUCTION", "SAILING"))
		{
			methods.add(new TrainingMethod(skill, 1, 99, "Train " + skill, 50000, null, null,
				Intensity.NORMAL));
		}
		return new PathPlanner(Collections.emptyList(),
			new PrerequisiteResolver(Collections.emptyList()),
			TrainingMethodDatabase.of(methods),
			ItemSourceDatabase.of(Collections.emptyList()));
	}

	private static PlayerState allSkillsAt(int level)
	{
		PlayerState state = new PlayerState();
		for (String skill : Arrays.asList("ATTACK", "DEFENCE", "STRENGTH", "HITPOINTS", "RANGED",
			"PRAYER", "MAGIC", "COOKING", "WOODCUTTING", "FLETCHING", "FISHING", "FIREMAKING",
			"CRAFTING", "SMITHING", "MINING", "HERBLORE", "AGILITY", "THIEVING", "SLAYER",
			"FARMING", "RUNECRAFT", "HUNTER", "CONSTRUCTION", "SAILING"))
		{
			state.setSkillLevel(skill, level);
		}
		return state;
	}

	@Test
	public void baseStatsGoalAlreadyMetIsReportedAsComplete()
	{
		Path path = everySkillTrainable().plan(Goal.baseStats(70, false), allSkillsAt(75));

		assertTrue(path.isAlreadyComplete());
		assertTrue(path.getSteps().isEmpty());
	}

	/** Only the skills that fall short, and nothing for the ones that do not. */
	@Test
	public void baseStatsGoalPlansOnlyTheSkillsBelowTheFloor()
	{
		PlayerState state = allSkillsAt(90);
		state.setSkillLevel("AGILITY", 50);
		state.setSkillLevel("RUNECRAFT", 60);

		Path path = everySkillTrainable().plan(Goal.baseStats(80, false), state);

		java.util.Set<String> skills = new java.util.LinkedHashSet<>();
		for (PathStep step : path.getSteps())
		{
			skills.add(step.getSkill());
		}
		assertEquals(new java.util.LinkedHashSet<>(Arrays.asList("RUNECRAFT", "AGILITY")), skills);
	}

	/**
	 * Cheapest first. Runecraft 60 to 80 is less xp than Agility 50 to 80, so
	 * it comes first even though Agility is the lower level - the point of the
	 * order is to get skills over the line, and xp is what that costs.
	 */
	@Test
	public void baseStatsGoalPutsTheCheapestSkillFirst()
	{
		PlayerState state = allSkillsAt(90);
		state.setSkillLevel("AGILITY", 50);
		state.setSkillLevel("RUNECRAFT", 60);

		Path path = everySkillTrainable().plan(Goal.baseStats(80, false), state);

		assertEquals("RUNECRAFT", path.getSteps().get(0).getSkill());
	}

	/** Hitpoints is not something you can be sent to train. */
	@Test
	public void baseStatsGoalNeverPlansHitpoints()
	{
		PlayerState state = allSkillsAt(90);
		state.setSkillLevel("HITPOINTS", 40);

		Path path = everySkillTrainable().plan(Goal.baseStats(80, false), state);

		assertTrue("hitpoints rises out of combat xp and cannot be planned",
			path.isAlreadyComplete());
	}

	@Test
	public void totalLevelGoalAlreadyMetIsReportedAsComplete()
	{
		// 24 skills at 50 is a total of 1200.
		Path path = everySkillTrainable().plan(Goal.totalLevel(1000, false), allSkillsAt(50));

		assertTrue(path.isAlreadyComplete());
	}

	/**
	 * The levels asked for are the levels planned - no more, to avoid sending
	 * someone further than they asked, and no fewer.
	 */
	@Test
	public void totalLevelGoalTakesExactlyTheLevelsAskedFor()
	{
		PlayerState state = allSkillsAt(50);
		Path path = everySkillTrainable().plan(Goal.totalLevel(1200 + 7, false), state);

		int gained = 0;
		for (PathStep step : path.getSteps())
		{
			gained += step.getLevelTo() - step.getLevelFrom();
		}
		assertEquals(7, gained);
	}

	/**
	 * Cheapest next level each time, so the levels come out of the lowest
	 * skills - which is what makes a total level goal worth asking rather than
	 * planning 23 skills by hand.
	 */
	@Test
	public void totalLevelGoalTakesItsLevelsFromTheCheapestSkills()
	{
		PlayerState state = allSkillsAt(90);
		state.setSkillLevel("AGILITY", 30);
		state.setSkillLevel("RUNECRAFT", 31);

		Path path = everySkillTrainable().plan(Goal.totalLevel(totalOf(state) + 4, false), state);

		java.util.Set<String> skills = new java.util.LinkedHashSet<>();
		for (PathStep step : path.getSteps())
		{
			skills.add(step.getSkill());
		}
		assertEquals(new java.util.LinkedHashSet<>(Arrays.asList("AGILITY", "RUNECRAFT")), skills);
	}

	/** A target past every skill being 99 is said to be out of reach, not silently clipped. */
	@Test
	public void totalLevelGoalSaysWhenTheTargetCannotBeReached()
	{
		PlayerState state = allSkillsAt(99);
		Path path = everySkillTrainable().plan(Goal.totalLevel(3000, false), state);

		boolean said = false;
		for (String warning : path.getWarnings())
		{
			if (warning.contains("not reachable") || warning.contains("short of"))
			{
				said = true;
			}
		}
		assertTrue("expected a warning about the target being out of reach, got "
			+ path.getWarnings(), said);
	}

	private static int totalOf(PlayerState state)
	{
		int total = 0;
		for (String skill : Arrays.asList("ATTACK", "DEFENCE", "STRENGTH", "HITPOINTS", "RANGED",
			"PRAYER", "MAGIC", "COOKING", "WOODCUTTING", "FLETCHING", "FISHING", "FIREMAKING",
			"CRAFTING", "SMITHING", "MINING", "HERBLORE", "AGILITY", "THIEVING", "SLAYER",
			"FARMING", "RUNECRAFT", "HUNTER", "CONSTRUCTION", "SAILING"))
		{
			total += state.getSkillLevel(skill);
		}
		return total;
	}

	/**
	 * Sailing is the case this exists for. Its early levels are the cheapest in
	 * the game, so a greedy that only looked at cost took every level from it
	 * and returned a plan with no steps in it - there is no Sailing data to
	 * turn into steps. The levels still count towards the total, so the plan
	 * says so rather than quietly planning something else.
	 */
	@Test
	public void totalLevelGoalSkipsASkillItCannotDescribeTraining()
	{
		PlayerState state = allSkillsAt(90);
		state.setSkillLevel("SAILING", 1);

		// everySkillTrainable() minus Sailing.
		java.util.List<TrainingMethod> methods = new java.util.ArrayList<>();
		for (String skill : Arrays.asList("ATTACK", "DEFENCE", "STRENGTH", "RANGED", "PRAYER",
			"MAGIC", "COOKING", "WOODCUTTING", "FLETCHING", "FISHING", "FIREMAKING", "CRAFTING",
			"SMITHING", "MINING", "HERBLORE", "AGILITY", "THIEVING", "SLAYER", "FARMING",
			"RUNECRAFT", "HUNTER", "CONSTRUCTION"))
		{
			methods.add(new TrainingMethod(skill, 1, 99, "Train " + skill, 50000, null, null,
				Intensity.NORMAL));
		}
		PathPlanner planner = new PathPlanner(Collections.emptyList(),
			new PrerequisiteResolver(Collections.emptyList()),
			TrainingMethodDatabase.of(methods),
			ItemSourceDatabase.of(Collections.emptyList()));

		Path path = planner.plan(Goal.totalLevel(totalOf(state) + 3, false), state);

		assertTrue("the plan should not be empty", !path.getSteps().isEmpty());
		for (PathStep step : path.getSteps())
		{
			assertTrue("no level should have been taken from Sailing",
				!"SAILING".equals(step.getSkill()));
		}
		boolean saidSo = false;
		for (String warning : path.getWarnings())
		{
			if (warning.contains("Sailing"))
			{
				saidSo = true;
			}
		}
		assertTrue("expected the plan to say why Sailing was skipped, got "
			+ path.getWarnings(), saidSo);
	}

	/**
	 * A rate-less method is still a method. Slayer comes a task at a time and
	 * Farming a run at a time, so neither carries an xp/hr - but "do Slayer
	 * tasks" is a real answer, and requiring a rate used to drop both skills
	 * out of total level plans entirely.
	 */
	@Test
	public void totalLevelGoalWillTakeLevelsFromARatelessSkill()
	{
		PlayerState state = allSkillsAt(90);
		state.setSkillLevel("SLAYER", 20);

		java.util.List<TrainingMethod> methods = new java.util.ArrayList<>();
		methods.add(new TrainingMethod("SLAYER", 1, 99, "Slayer tasks", null, null, null,
			Intensity.NORMAL));
		methods.add(new TrainingMethod("MINING", 1, 99, "Mine", 50000, null, null,
			Intensity.NORMAL));
		PathPlanner planner = new PathPlanner(Collections.emptyList(),
			new PrerequisiteResolver(Collections.emptyList()),
			TrainingMethodDatabase.of(methods),
			ItemSourceDatabase.of(Collections.emptyList()));

		Path path = planner.plan(Goal.totalLevel(totalOf(state) + 2, false), state);

		assertEquals("SLAYER", path.getSteps().get(0).getSkill());
	}
}
