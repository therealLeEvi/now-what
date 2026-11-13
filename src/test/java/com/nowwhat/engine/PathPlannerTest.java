package com.nowwhat.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.nowwhat.data.ItemSourceDatabase;
import com.nowwhat.data.TrainingMethodDatabase;
import com.nowwhat.model.Activity;
import com.nowwhat.model.Goal;
import com.nowwhat.model.ItemSource;
import com.nowwhat.model.Path;
import com.nowwhat.model.PathStep;
import com.nowwhat.model.PlayerState;
import com.nowwhat.model.Requirement;
import com.nowwhat.model.TrainingMethod;
import com.nowwhat.model.TrainingMethod.Intensity;
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

	/**
	 * Hitpoints is not something you can be sent to train, so it gets no step.
	 * It is still one of "every skill", so the goal is not done while it is
	 * short, and the plan says why there is nothing to do about it.
	 */
	@Test
	public void baseStatsGoalNeverPlansHitpointsButSaysItIsShort()
	{
		PlayerState state = allSkillsAt(90);
		state.setSkillLevel("HITPOINTS", 40);

		Path path = everySkillTrainable().plan(Goal.baseStats(80, false), state);

		assertTrue("hitpoints rises out of combat xp and cannot be planned",
			path.getSteps().isEmpty());
		assertEquals(false, path.isAlreadyComplete());
		assertTrue(path.getWarnings().stream().anyMatch(w -> w.contains("Hitpoints is 40")));
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

	/**
	 * A shop item whose currency is earned somewhere gets that somewhere as a
	 * step of its own, ahead of the purchase. Without it the plan ends on "250
	 * points" and the player has to leave the panel to find out what that means.
	 */
	@Test
	public void aShopPricedInPointsPlansTheActivityThatMintsThem()
	{
		Activity tithe = new Activity("minigame_tithe_farm", "Tithe Farm",
			Activity.Category.MINIGAME, Collections.emptyList())
			.withDescription("1 point per 3 fruits");

		ItemSource seedBox = new ItemSource("SEED_BOX", "Seed box", ItemSource.SourceType.SHOP,
			"Tithe Farm reward shop, 250 points", null, null, null, Collections.emptyList())
			.withCurrencyFrom("minigame_tithe_farm");

		PathPlanner planner = new PathPlanner(Collections.singletonList(tithe),
			new PrerequisiteResolver(Collections.singletonList(tithe)),
			TrainingMethodDatabase.of(Collections.emptyList()),
			ItemSourceDatabase.of(Collections.singletonList(seedBox)));

		Path path = planner.plan(Goal.itemQuantity("SEED_BOX", 1, "Seed box", false, false),
			new PlayerState());

		assertEquals(2, path.getSteps().size());
		PathStep earn = path.getSteps().get(0);
		assertEquals(PathStep.Type.DO_ACTIVITY, earn.getType());
		assertEquals("Tithe Farm", earn.getTitle());
		assertTrue("the step should carry the activity's own description, got "
			+ earn.getDetail(), earn.getDetail().contains("1 point per 3 fruits"));
		assertEquals(PathStep.Type.ACQUIRE_ITEM, path.getSteps().get(1).getType());
	}

	/**
	 * The herb sack asks for 58 Herblore, but its points come from Tithe Farm,
	 * which asks for 34 Farming. The plan has to get the player into the
	 * minigame before it sends them there.
	 */
	@Test
	public void aShopPlansTheEntryRequirementsOfWhereItsCurrencyIsEarned()
	{
		Activity tithe = new Activity("minigame_tithe_farm", "Tithe Farm",
			Activity.Category.MINIGAME, Arrays.asList(
				new Requirement(Requirement.Type.SKILL_LEVEL, "FARMING", 34)));
		ItemSource herbSack = new ItemSource("HERB_SACK", "Herb sack", ItemSource.SourceType.SHOP,
			"Tithe Farm reward shop, 250 points", null, null,
			new Requirement(Requirement.Type.SKILL_LEVEL, "HERBLORE", 58), Collections.emptyList())
			.withCurrencyFrom("minigame_tithe_farm");
		TrainingMethod farming = new TrainingMethod("FARMING", 1, 99, "Tree runs", 50000, null, null,
			Intensity.NORMAL);

		PathPlanner planner = new PathPlanner(Collections.singletonList(tithe),
			new PrerequisiteResolver(Collections.singletonList(tithe)),
			TrainingMethodDatabase.of(Collections.singletonList(farming)),
			ItemSourceDatabase.of(Collections.singletonList(herbSack)));

		PlayerState state = new PlayerState();
		state.setSkillLevel("HERBLORE", 58);
		state.setSkillLevel("FARMING", 20);

		Path path = planner.plan(Goal.itemQuantity("HERB_SACK", 1, "Herb sack", false, false), state);

		assertEquals(PathStep.Type.TRAIN_SKILL, path.getSteps().get(0).getType());
		assertEquals("FARMING", path.getSteps().get(0).getSkill());
		assertEquals("Tithe Farm", path.getSteps().get(1).getTitle());
	}

	/** The seed box and Tithe Farm both ask for 34 Farming; the plan says it once. */
	@Test
	public void aRequirementTheItemAndItsEarnerShareIsPlannedOnce()
	{
		Requirement farming34 = new Requirement(Requirement.Type.SKILL_LEVEL, "FARMING", 34);
		Activity tithe = new Activity("minigame_tithe_farm", "Tithe Farm",
			Activity.Category.MINIGAME, Collections.singletonList(farming34));
		ItemSource seedBox = new ItemSource("SEED_BOX", "Seed box", ItemSource.SourceType.SHOP,
			"Tithe Farm reward shop, 250 points", null, null,
			new Requirement(Requirement.Type.SKILL_LEVEL, "FARMING", 34), Collections.emptyList())
			.withCurrencyFrom("minigame_tithe_farm");
		TrainingMethod farming = new TrainingMethod("FARMING", 1, 99, "Tree runs", 50000, null, null,
			Intensity.NORMAL);

		PathPlanner planner = new PathPlanner(Collections.singletonList(tithe),
			new PrerequisiteResolver(Collections.singletonList(tithe)),
			TrainingMethodDatabase.of(Collections.singletonList(farming)),
			ItemSourceDatabase.of(Collections.singletonList(seedBox)));

		PlayerState state = new PlayerState();
		state.setSkillLevel("FARMING", 20);

		Path path = planner.plan(Goal.itemQuantity("SEED_BOX", 1, "Seed box", false, false), state);

		long farmingSteps = path.getSteps().stream()
			.filter(step -> step.getType() == PathStep.Type.TRAIN_SKILL)
			.count();
		assertEquals(1, farmingSteps);
	}

	/** A currency pointing at no such activity is said out loud, not dropped. */
	@Test
	public void aShopPointingAtAnUnknownActivityWarns()
	{
		ItemSource seedBox = new ItemSource("SEED_BOX", "Seed box", ItemSource.SourceType.SHOP,
			"Some shop, 250 points", null, null, null, Collections.emptyList())
			.withCurrencyFrom("minigame_nonexistent");

		PathPlanner planner = new PathPlanner(Collections.emptyList(),
			new PrerequisiteResolver(Collections.emptyList()),
			TrainingMethodDatabase.of(Collections.emptyList()),
			ItemSourceDatabase.of(Collections.singletonList(seedBox)));

		Path path = planner.plan(Goal.itemQuantity("SEED_BOX", 1, "Seed box", false, false),
			new PlayerState());

		boolean said = false;
		for (String warning : path.getWarnings())
		{
			if (warning.contains("minigame_nonexistent"))
			{
				said = true;
			}
		}
		assertTrue("expected a warning naming the missing activity, got " + path.getWarnings(),
			said);
	}

	/**
	 * Planning a Combat Achievement tier used to stop at "you meet every
	 * requirement", because the only requirement was a combat level. A tier is
	 * a points total, so the plan has to say how many points are left.
	 */
	@Test
	public void combatAchievementTierPlansThePointsStillMissing()
	{
		Activity elite = new Activity("CA_ELITE", "Combat Achievements: Elite tier",
			Activity.Category.COMBAT_ACHIEVEMENT, Collections.emptyList());
		Activity master = new Activity("CA_MASTER", "Combat Achievements: Master tier",
			Activity.Category.COMBAT_ACHIEVEMENT, Arrays.asList(
				new Requirement(Requirement.Type.COMBAT_LEVEL, "COMBAT_LEVEL", 110),
				new Requirement(Requirement.Type.COMBAT_ACHIEVEMENT_TIER, "CA_ELITE", 0)));
		PathPlanner planner = new PathPlanner(Arrays.asList(elite, master),
			new PrerequisiteResolver(Arrays.asList(elite, master)),
			TrainingMethodDatabase.of(Collections.emptyList()),
			ItemSourceDatabase.of(Collections.emptyList()));

		PlayerState state = new PlayerState();
		state.setCombatLevel(126);
		state.addCompletedCombatAchievementTier("CA_ELITE");
		state.setCombatAchievementPoints(1500);

		Path path = planner.plan(Goal.activity("CA_MASTER", "Master", false), state);

		assertTrue("a tier with points still to earn is not 'already met'",
			!path.isRequirementsAlreadyMet());
		PathStep points = path.getSteps().get(0);
		assertEquals("Earn 465 more Combat Achievement points", points.getTitle());
		assertTrue(points.getDetail(), points.getDetail().contains("93 Master tasks"));
		assertEquals("https://oldschool.runescape.wiki/w/Combat_Achievements/Master", points.getWikiUrl());
	}

	/** Master needs Elite first, and the plan names Elite as a step rather than "CA_ELITE". */
	@Test
	public void combatAchievementTierPlansTheTierBeforeIt()
	{
		Activity elite = new Activity("CA_ELITE", "Combat Achievements: Elite tier",
			Activity.Category.COMBAT_ACHIEVEMENT, Collections.emptyList());
		Activity master = new Activity("CA_MASTER", "Combat Achievements: Master tier",
			Activity.Category.COMBAT_ACHIEVEMENT, Arrays.asList(
				new Requirement(Requirement.Type.COMBAT_ACHIEVEMENT_TIER, "CA_ELITE", 0)));
		PathPlanner planner = new PathPlanner(Arrays.asList(elite, master),
			new PrerequisiteResolver(Arrays.asList(elite, master)),
			TrainingMethodDatabase.of(Collections.emptyList()),
			ItemSourceDatabase.of(Collections.emptyList()));

		Path path = planner.plan(Goal.activity("CA_MASTER", "Master", false), new PlayerState());

		assertEquals("Combat Achievements: Elite tier", path.getSteps().get(0).getTitle());
		assertTrue(path.getWarnings().toString(), path.getWarnings().isEmpty());
	}

	@Test
	public void piecesFromTheSameFightAreOneKillStepWithOneRequirement()
	{
		Requirement slayer = new Requirement(Requirement.Type.SKILL_LEVEL, "SLAYER", 95);
		ItemSource eye = new ItemSource("EYE", "Hydra's eye", ItemSource.SourceType.MONSTER_DROP,
			"Hydras", null, 1801, slayer, Collections.emptyList());
		ItemSource fang = new ItemSource("FANG", "Hydra's fang", ItemSource.SourceType.MONSTER_DROP,
			"Hydras", null, 1801, slayer, Collections.emptyList());
		ItemSource heart = new ItemSource("HEART", "Hydra's heart", ItemSource.SourceType.MONSTER_DROP,
			"Hydras", null, 1801, slayer, Collections.emptyList());
		ItemSource ring = new ItemSource("RING", "Brimstone ring", ItemSource.SourceType.CRAFT,
			"Combine the three", null, null, null, Arrays.asList(
				new ItemSource.Ingredient("EYE", 1),
				new ItemSource.Ingredient("FANG", 1),
				new ItemSource.Ingredient("HEART", 1)));
		TrainingMethod slaying = new TrainingMethod("SLAYER", 1, 99, "Slay", 20000, null, null,
			Intensity.NORMAL);

		PathPlanner planner = new PathPlanner(Collections.emptyList(),
			new PrerequisiteResolver(Collections.emptyList()),
			TrainingMethodDatabase.of(Arrays.asList(slaying)),
			ItemSourceDatabase.of(Arrays.asList(eye, fang, heart, ring)));

		PlayerState state = new PlayerState();
		state.setSkillLevel("SLAYER", 80);

		Path path = planner.plan(Goal.itemQuantity("RING", 1, "Brimstone ring", false), state);

		// Slayer once, one trip to the hydras for all three pieces, then the ring.
		assertEquals(path.getSteps().toString(), 3, path.getSteps().size());
		assertEquals(PathStep.Type.TRAIN_SKILL, path.getSteps().get(0).getType());
		PathStep kill = path.getSteps().get(1);
		assertEquals(PathStep.Type.KILL_BOSS, kill.getType());
		assertEquals("Hydra's eye, Hydra's fang and Hydra's heart from Hydras", kill.getTitle());
		assertTrue(kill.getDetail(), kill.getDetail().startsWith("~1,801 attempts on average for each piece"));
		assertEquals(PathStep.Type.ACQUIRE_ITEM, path.getSteps().get(2).getType());
	}

	@Test
	public void aPrerequisiteTwoIngredientsShareIsPlannedOnce()
	{
		Requirement quest = new Requirement(Requirement.Type.QUEST_COMPLETE, "Regicide", 0);
		ItemSource a = new ItemSource("A", "Part A", ItemSource.SourceType.GE_BUY,
			null, null, null, quest, Collections.emptyList());
		ItemSource b = new ItemSource("B", "Part B", ItemSource.SourceType.GE_BUY,
			null, null, null, quest, Collections.emptyList());
		ItemSource whole = new ItemSource("WHOLE", "Whole", ItemSource.SourceType.CRAFT,
			"Put together", null, null, null, Arrays.asList(
				new ItemSource.Ingredient("A", 1),
				new ItemSource.Ingredient("B", 1)));

		PathPlanner planner = new PathPlanner(Collections.emptyList(),
			new PrerequisiteResolver(Collections.emptyList()),
			TrainingMethodDatabase.of(Collections.emptyList()),
			ItemSourceDatabase.of(Arrays.asList(a, b, whole)));

		Path path = planner.plan(Goal.itemQuantity("WHOLE", 1, "Whole", false), new PlayerState());

		long questSteps = path.getSteps().stream()
			.filter(s -> s.getType() != PathStep.Type.ACQUIRE_ITEM)
			.count();
		assertTrue(path.getSteps().toString(), questSteps <= 1);
		assertEquals(path.getSteps().toString(), 3,
			path.getSteps().stream().filter(s -> s.getType() == PathStep.Type.ACQUIRE_ITEM).count());
	}

	/**
	 * Levels come before the quests that ask for them: a plan that opens with
	 * quests the player cannot start yet has its steps in the wrong order.
	 */
	@Test
	public void activityGoalListsTrainingBeforeTheQuestsThatNeedIt()
	{
		Activity quest = new Activity("q1", "Gate Quest", Activity.Category.QUEST, Arrays.asList(
			new Requirement(Requirement.Type.SKILL_LEVEL, "AGILITY", 30)));
		Activity boss = new Activity("boss", "Some Boss", Activity.Category.PVM, Arrays.asList(
			new Requirement(Requirement.Type.QUEST_COMPLETE, "Gate Quest", 0)));
		TrainingMethod method = new TrainingMethod("AGILITY", 1, 99, "Some course", 20000, null, null,
			Intensity.NORMAL);
		PathPlanner planner = new PathPlanner(Arrays.asList(quest, boss),
			new PrerequisiteResolver(Arrays.asList(quest, boss)),
			TrainingMethodDatabase.of(Arrays.asList(method)),
			ItemSourceDatabase.of(Collections.emptyList()));

		Path path = planner.plan(Goal.activity("boss", "Some Boss", false), new PlayerState());

		assertEquals(path.getSteps().toString(), 3, path.getSteps().size());
		assertEquals(PathStep.Type.TRAIN_SKILL, path.getSteps().get(0).getType());
		assertEquals(PathStep.Type.DO_QUEST, path.getSteps().get(1).getType());
		assertTrue(path.getSteps().get(2).isTarget());
	}

	/**
	 * The suggestion list holds a fight back for its recommended stats, so the
	 * plan has to know about them too - as steps marked recommended, picking up
	 * where the required level leaves off.
	 */
	@Test
	public void recommendedStatsBecomeRecommendedStepsAfterTheRequiredOnes()
	{
		Activity boss = new Activity("boss", "Some Boss", Activity.Category.PVM, Arrays.asList(
			new Requirement(Requirement.Type.SKILL_LEVEL, "RANGED", 70)))
			.withRecommended(Arrays.asList(
				new Requirement(Requirement.Type.SKILL_LEVEL, "RANGED", 85)), false);
		TrainingMethod method = new TrainingMethod("RANGED", 1, 99, "Some monster", 50000, null, null,
			Intensity.NORMAL);
		PathPlanner planner = new PathPlanner(Arrays.asList(boss),
			new PrerequisiteResolver(Arrays.asList(boss)),
			TrainingMethodDatabase.of(Arrays.asList(method)),
			ItemSourceDatabase.of(Collections.emptyList()));

		PlayerState state = new PlayerState();
		state.setSkillLevel("RANGED", 60);
		Path path = planner.plan(Goal.activity("boss", "Some Boss", false), state);

		assertEquals(path.getSteps().toString(), 3, path.getSteps().size());
		PathStep required = path.getSteps().get(0);
		assertEquals(60, required.getLevelFrom());
		assertEquals(70, required.getLevelTo());
		assertEquals(false, required.isRecommended());
		PathStep recommended = path.getSteps().get(1);
		assertEquals(70, recommended.getLevelFrom());
		assertEquals(85, recommended.getLevelTo());
		assertTrue(recommended.isRecommended());
		assertEquals(false, path.isRequirementsAlreadyMet());

		// With the recommended stats met as well, nothing is left to say.
		state.setSkillLevel("RANGED", 85);
		assertTrue(planner.plan(Goal.activity("boss", "Some Boss", false), state)
			.isRequirementsAlreadyMet());
	}

	/**
	 * Only the finished item used to be checked against what the player owns,
	 * so holding two of three parts still meant being sent for all three.
	 */
	@Test
	public void ingredientsAlreadyOwnedAreLeftOutOfAnItemPlan()
	{
		ItemSource a = new ItemSource("A", "Part A", ItemSource.SourceType.GE_BUY,
			null, null, null, null, Collections.emptyList()).withGameItemId(101);
		ItemSource b = new ItemSource("B", "Part B", ItemSource.SourceType.GE_BUY,
			null, null, null, null, Collections.emptyList()).withGameItemId(102);
		ItemSource whole = new ItemSource("WHOLE", "Whole", ItemSource.SourceType.CRAFT,
			"Put together", null, null, null, Arrays.asList(
				new ItemSource.Ingredient("A", 1),
				new ItemSource.Ingredient("B", 5))).withGameItemId(100);
		PathPlanner planner = new PathPlanner(Collections.emptyList(),
			new PrerequisiteResolver(Collections.emptyList()),
			TrainingMethodDatabase.of(Collections.emptyList()),
			ItemSourceDatabase.of(Arrays.asList(a, b, whole)));

		PlayerState state = new PlayerState();
		java.util.Map<Integer, Integer> owned = new java.util.HashMap<>();
		owned.put(101, 1);
		owned.put(102, 2);
		state.setOwnedItems(owned);
		state.setBankSnapshotAvailable(true);

		Path path = planner.plan(Goal.itemQuantity("WHOLE", 1, "Whole", false), state);

		// Part A is covered outright; Part B is three short; then the craft.
		assertEquals(path.getSteps().toString(), 2, path.getSteps().size());
		assertEquals("Buy 3x Part B", path.getSteps().get(0).getTitle());
		assertEquals("Craft 1x Whole", path.getSteps().get(1).getTitle());
		assertTrue(path.getWarnings().toString(),
			path.getWarnings().stream().anyMatch(w -> w.contains("Part A")));

		// Asked for as if nothing were owned, everything is planned.
		Path fromNothing = planner.plan(
			Goal.itemQuantity("WHOLE", 1, "Whole", false).ignoringWhatIsOwned(), state);
		assertEquals(fromNothing.getSteps().toString(), 3, fromNothing.getSteps().size());
	}
}
