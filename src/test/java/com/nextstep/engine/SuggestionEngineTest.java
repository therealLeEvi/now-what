package com.nextstep.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.nextstep.model.Activity;
import com.nextstep.model.PlayerState;
import com.nextstep.model.Requirement;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class SuggestionEngineTest
{
	private final SuggestionEngine engine = new SuggestionEngine();
	private PlayerState state;

	@Before
	public void setUp()
	{
		state = new PlayerState();
	}

	private Activity questRequiringAgility(int requiredLevel)
	{
		return new Activity("q1", "Test Quest", Activity.Category.QUEST,
			Arrays.asList(new Requirement(Requirement.Type.SKILL_LEVEL, "AGILITY", requiredLevel)));
	}

	@Test
	public void availableNowExcludesActivitiesWithUnmetRequirements()
	{
		state.setSkillLevel("AGILITY", 40);
		List<Activity> all = Arrays.asList(questRequiringAgility(50));

		assertTrue(engine.getAvailableNow(all, state).isEmpty());
	}

	@Test
	public void availableNowIncludesActivitiesWithMetRequirements()
	{
		state.setSkillLevel("AGILITY", 50);
		Activity quest = questRequiringAgility(50);
		List<Activity> all = Arrays.asList(quest);

		List<Activity> result = engine.getAvailableNow(all, state);
		assertEquals(1, result.size());
		assertEquals(quest, result.get(0));
	}

	@Test
	public void availableNowExcludesAlreadyCompletedQuests()
	{
		state.setSkillLevel("AGILITY", 50);
		state.addCompletedQuest("Test Quest");
		List<Activity> all = Arrays.asList(questRequiringAgility(50));

		assertTrue(engine.getAvailableNow(all, state).isEmpty());
	}

	@Test
	public void availableNowIsSortedByPriorityDescending()
	{
		Activity low = new Activity("low", "Low priority", Activity.Category.SKILLING, Arrays.asList(), 1);
		Activity high = new Activity("high", "High priority", Activity.Category.SKILLING, Arrays.asList(), 9);

		List<Activity> result = engine.getAvailableNow(Arrays.asList(low, high), state);

		assertEquals(2, result.size());
		assertEquals("high", result.get(0).getId());
		assertEquals("low", result.get(1).getId());
	}

	@Test
	public void almostAvailableRequiresExactlyOneUnmetRequirement()
	{
		state.setSkillLevel("AGILITY", 40);
		state.setSkillLevel("THIEVING", 32); // met
		Activity twoReqsOneUnmet = new Activity("a", "Two reqs", Activity.Category.DIARY, Arrays.asList(
			new Requirement(Requirement.Type.SKILL_LEVEL, "AGILITY", 47),
			new Requirement(Requirement.Type.SKILL_LEVEL, "THIEVING", 32)
		));
		Activity twoReqsBothUnmet = new Activity("b", "Two unmet", Activity.Category.DIARY, Arrays.asList(
			new Requirement(Requirement.Type.SKILL_LEVEL, "AGILITY", 47),
			new Requirement(Requirement.Type.SKILL_LEVEL, "MAGIC", 70)
		));

		List<Activity> result = engine.getAlmostAvailable(Arrays.asList(twoReqsOneUnmet, twoReqsBothUnmet), state);

		assertEquals(1, result.size());
		assertEquals("a", result.get(0).getId());
	}

	@Test
	public void almostAvailableExcludesAlreadyDoneActivities()
	{
		state.setSkillLevel("AGILITY", 40);
		state.addCompletedQuest("Test Quest");
		Activity quest = questRequiringAgility(47); // one unmet req, but already done

		assertFalse(engine.getAlmostAvailable(Arrays.asList(quest), state).contains(quest));
	}

	private Activity available(String id, Activity.Category category)
	{
		return new Activity(id, id, category,
			Arrays.asList(new Requirement(Requirement.Type.SKILL_LEVEL, "AGILITY", 10)));
	}

	@Test
	public void availableNowDropsCategoriesThePlayerSwitchedOff()
	{
		state.setSkillLevel("AGILITY", 50);
		List<Activity> all = Arrays.asList(
			available("quest", Activity.Category.QUEST),
			available("boss", Activity.Category.PVM));

		List<Activity> result = engine.getAvailableNow(all, state,
			EnumSet.of(Activity.Category.QUEST));

		assertEquals(1, result.size());
		assertEquals("boss", result.get(0).getId());
	}

	@Test
	public void availableNowKeepsCategoriesThatHaveNoSwitch()
	{
		// Only four categories have a setting. A minigame cannot be hidden,
		// so it must survive a filter that hides everything that can be.
		state.setSkillLevel("AGILITY", 50);
		List<Activity> all = Arrays.asList(available("pc", Activity.Category.MINIGAME));

		List<Activity> result = engine.getAvailableNow(all, state,
			EnumSet.of(Activity.Category.QUEST, Activity.Category.DIARY,
				Activity.Category.PVM, Activity.Category.COLLECTION_LOG));

		assertEquals(1, result.size());
	}

	@Test
	public void almostAvailableDropsCategoriesThePlayerSwitchedOff()
	{
		state.setSkillLevel("AGILITY", 40);
		Activity quest = questRequiringAgility(47);

		assertTrue(engine.getAlmostAvailable(Arrays.asList(quest), state,
			EnumSet.of(Activity.Category.QUEST)).isEmpty());
		assertEquals(1, engine.getAlmostAvailable(Arrays.asList(quest), state,
			EnumSet.of(Activity.Category.PVM)).size());
	}

	@Test
	public void noHiddenCategoriesMeansTheOldBehaviour()
	{
		state.setSkillLevel("AGILITY", 50);
		List<Activity> all = Arrays.asList(
			available("quest", Activity.Category.QUEST),
			available("boss", Activity.Category.PVM));

		assertEquals(engine.getAvailableNow(all, state).size(),
			engine.getAvailableNow(all, state, EnumSet.noneOf(Activity.Category.class)).size());
	}
}
