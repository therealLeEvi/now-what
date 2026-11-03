package com.nextstep.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.nextstep.model.PathStep;
import com.nextstep.model.TrainingMethod;
import com.nextstep.model.TrainingMethod.Intensity;
import com.nextstep.model.TrainingMethod.Risk;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class SkillPathPlannerTest
{
	private final SkillPathPlanner planner = new SkillPathPlanner();

	private static TrainingMethod normal(int min, int max, String name, Integer xpHr)
	{
		return new TrainingMethod("MINING", min, max, name, xpHr, null, null, Intensity.NORMAL);
	}

	private static TrainingMethod tick(int min, int max, String name, Integer xpHr)
	{
		return new TrainingMethod("MINING", min, max, name, xpHr, null, null, Intensity.TICK_MANIPULATION);
	}

	private static TrainingMethod wilderness(int min, int max, String name, Integer xpHr)
	{
		return new TrainingMethod("MINING", min, max, name, xpHr, null, null, Intensity.NORMAL, Risk.PVP);
	}

	@Test
	public void noDataProducesNoStepsAndAWarning()
	{
		List<String> warnings = new ArrayList<>();
		List<PathStep> steps = planner.plan("MINING", 1, 50, new ArrayList<>(), false, warnings);

		assertTrue(steps.isEmpty());
		assertEquals(1, warnings.size());
	}

	@Test
	public void alreadyAtOrAboveTargetProducesNoSteps()
	{
		List<String> warnings = new ArrayList<>();
		List<PathStep> steps = planner.plan("MINING", 50, 40,
			Arrays.asList(normal(1, 99, "Iron ore", 20000)), false, warnings);

		assertTrue(steps.isEmpty());
		assertTrue(warnings.isEmpty());
	}

	@Test
	public void segmentsSplitAtMethodBoundaries()
	{
		// Two non-overlapping methods covering the full range: expect exactly
		// two segments, split precisely at the boundary between them.
		List<TrainingMethod> methods = Arrays.asList(
			normal(1, 15, "Copper/Tin", 8000),
			normal(15, 40, "Iron ore", 20000)
		);
		List<String> warnings = new ArrayList<>();

		List<PathStep> steps = planner.plan("MINING", 1, 40, methods, false, warnings);

		assertEquals(2, steps.size());
		assertTrue(steps.get(0).getTitle().contains("1"));
		assertTrue(steps.get(0).getTitle().contains("15"));
		assertTrue(steps.get(1).getTitle().contains("15"));
		assertTrue(steps.get(1).getTitle().contains("40"));
		assertTrue(warnings.isEmpty());
	}

	@Test
	public void overlappingNormalMethodsAreAllOfferedAsAlternatives()
	{
		// The exact scenario from the conversation: several normal methods,
		// none pruned, all offered together with the widest-agreement range
		// grouped as one step.
		List<TrainingMethod> methods = Arrays.asList(
			normal(40, 99, "Motherlode Mine", 45000),
			normal(43, 99, "Blast Mine", 50000),
			normal(40, 99, "Gem rocks", 35000),
			normal(45, 99, "Calcified rocks", 40000)
		);
		List<String> warnings = new ArrayList<>();

		List<PathStep> steps = planner.plan("MINING", 40, 99, methods, false, warnings);

		// Breakpoints at 40, 43, 45, 99 -> three segments.
		assertEquals(3, steps.size());

		// Segment [40,43): only Motherlode + Gem rocks cover it.
		assertEquals(2, steps.get(0).getAlternatives().size());
		// Segment [43,45): Motherlode + Gem rocks + Blast Mine.
		assertEquals(3, steps.get(1).getAlternatives().size());
		// Segment [45,99): all four.
		assertEquals(4, steps.get(2).getAlternatives().size());

		// Every step should offer a choice given more than one alternative.
		assertTrue(steps.get(2).hasChoice());
	}

	@Test
	public void alternativesAreSortedByXpPerHourDescending()
	{
		List<TrainingMethod> methods = Arrays.asList(
			normal(1, 99, "Slow method", 10000),
			normal(1, 99, "Fast method", 50000),
			normal(1, 99, "Medium method", 25000)
		);
		List<String> warnings = new ArrayList<>();

		List<PathStep> steps = planner.plan("MINING", 1, 99, methods, false, warnings);

		assertEquals(1, steps.size());
		List<PathStep.Option> options = steps.get(0).getAlternatives();
		assertEquals(3, options.size());
		// Titles are the bare method name now, so these compare exactly.
		assertEquals("Fast method", options.get(0).getTitle());
		assertEquals("Medium method", options.get(1).getTitle());
		assertEquals("Slow method", options.get(2).getTitle());
	}

	@Test
	public void wildernessMethodsAreFlaggedAndShownAlongsideSafeOnes()
	{
		// The reason risk is recorded at all: a rate alone can't tell you that
		// one of these two options can get you killed by another player.
		List<TrainingMethod> methods = Arrays.asList(
			wilderness(1, 99, "Wilderness rocks", 90000),
			normal(1, 99, "Safe rocks", 80000)
		);
		List<String> riskWarnings = new ArrayList<>();

		List<PathStep> riskSteps = planner.plan("MINING", 1, 40, methods, false, riskWarnings);

		assertEquals(1, riskSteps.size());
		List<PathStep.Option> riskOptions = riskSteps.get(0).getAlternatives();
		// Risk is flagged, not filtered - the safe option stays on the list.
		assertEquals(2, riskOptions.size());
		// The flag travels as data rather than as text welded into the name, so
		// the panel can render it as a tag instead of a longer sentence.
		assertTrue(riskOptions.get(0).isPvpRisk());
		assertEquals("Wilderness rocks", riskOptions.get(0).getTitle());
		assertTrue(!riskOptions.get(1).isPvpRisk());
	}

	@Test
	public void safeMethodsCarryNoRiskLabel()
	{
		List<String> warnings = new ArrayList<>();
		List<PathStep> steps = planner.plan("MINING", 1, 40,
			Arrays.asList(normal(1, 99, "Iron ore", 20000)), false, warnings);

		assertTrue(!steps.get(0).getAlternatives().get(0).isPvpRisk());
	}

	@Test
	public void adjacentSegmentsResolvingToTheSameMethodsAreMerged()
	{
		// A finely level-bracketed tick method (as 1.5-tick teaks are, running
		// 93k at 35 to 222k at 90) adds breakpoints the normal plan has no use
		// for. Without merging, a normal-pace player got consecutive steps
		// listing the identical single method.
		List<TrainingMethod> methods = Arrays.asList(
			normal(1, 99, "Steady rocks", 30000),
			tick(1, 25, "Tick rocks A", 90000),
			tick(25, 50, "Tick rocks B", 120000),
			tick(50, 99, "Tick rocks C", 150000)
		);
		List<String> warnings = new ArrayList<>();

		List<PathStep> normalPlan = planner.plan("MINING", 1, 70, methods, false, warnings);

		// One unbroken step, not three slices of the same method.
		assertEquals(1, normalPlan.size());
		assertEquals(1, normalPlan.get(0).getAlternatives().size());
		assertTrue(normalPlan.get(0).getTitle().contains("1"));
		assertTrue(normalPlan.get(0).getTitle().contains("70"));

		// The tick plan still gets every bracket, because each one is a
		// genuinely different method with a different rate.
		List<PathStep> tickPlan = planner.plan("MINING", 1, 70, methods, true, new ArrayList<>());
		assertEquals(3, tickPlan.size());
	}

	@Test
	public void mergingStopsWhereTheMethodSetActuallyChanges()
	{
		List<TrainingMethod> methods = Arrays.asList(
			normal(1, 99, "Steady rocks", 30000),
			normal(40, 99, "Better rocks", 50000)
		);
		List<String> warnings = new ArrayList<>();

		List<PathStep> steps = planner.plan("MINING", 1, 60, methods, false, warnings);

		assertEquals(2, steps.size());
		assertEquals(1, steps.get(0).getAlternatives().size());
		assertEquals(2, steps.get(1).getAlternatives().size());
	}

	@Test
	public void questRouteMethodsCarryTheirQuestsIntoTheOption()
	{
		// A rate-less "do these quests" method has to reach the panel with its
		// quest names attached, or the panel has nothing to hand to Quest Helper.
		TrainingMethod quests = new TrainingMethod("MINING", 1, 40, "Quests", null,
			"These cover the early levels", null, Intensity.NORMAL);
		TrainingMethod grind = normal(1, 40, "Copper ore", 20000);
		List<String> warnings = new ArrayList<>();

		List<PathStep> steps = planner.plan("MINING", 1, 30,
			Arrays.asList(quests, grind), false, warnings);

		assertEquals(1, steps.size());
		// Training leads - the quest route is an alternative the panel keeps
		// visible, not the headline for the whole bracket.
		assertTrue(steps.get(0).getAlternatives().get(0).getTitle().contains("Copper ore"));
		assertEquals("Quests", steps.get(0).getAlternatives().get(1).getTitle());
	}
}
