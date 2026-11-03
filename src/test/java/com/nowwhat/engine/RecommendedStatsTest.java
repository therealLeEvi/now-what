package com.nowwhat.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.nowwhat.model.Activity;
import com.nowwhat.model.PlayerState;
import com.nowwhat.model.Requirement;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

/**
 * Plenty of fights have no requirement at all, and with only requirements to
 * go on a fresh account was told to do the Inferno. The wiki's recommended
 * stats decide what is suggested; they never decide what can be planned.
 */
public class RecommendedStatsTest
{
	private final SuggestionEngine engine = new SuggestionEngine();
	private PlayerState state;

	@Before
	public void setUp()
	{
		state = new PlayerState();
	}

	private static Activity fight(boolean wilderness, Requirement... recommended)
	{
		return new Activity("pvm_x", "Test fight", Activity.Category.PVM, Collections.emptyList())
			.withRecommended(Arrays.asList(recommended), wilderness);
	}

	private static Requirement level(String skill, int level)
	{
		return new Requirement(Requirement.Type.SKILL_LEVEL, skill, level);
	}

	@Test
	public void aFightWithNoRequirementsIsNotSuggestedBelowItsRecommendedStats()
	{
		state.setSkillLevel("RANGED", 40);
		List<Activity> all = Collections.singletonList(fight(false, level("RANGED", 90)));

		assertTrue(engine.getAvailableNow(all, state).isEmpty());
		assertTrue("50 levels short is not almost", engine.getAlmostAvailable(all, state).isEmpty());
	}

	@Test
	public void aFewLevelsShortOfTheRecommendationIsAlmostThere()
	{
		state.setSkillLevel("RANGED", 87);
		state.setSkillLevel("DEFENCE", 70);
		Activity fight = fight(false, level("RANGED", 90), level("DEFENCE", 70));
		List<Activity> all = Collections.singletonList(fight);

		assertTrue(engine.getAvailableNow(all, state).isEmpty());
		assertEquals(all, engine.getAlmostAvailable(all, state));
		assertEquals("Recommended 90 Ranged, you have 87", engine.whatIsMissing(fight, state));
	}

	@Test
	public void meetingTheRecommendationMakesItAvailable()
	{
		state.setSkillLevel("RANGED", 90);
		List<Activity> all = Collections.singletonList(fight(false, level("RANGED", 90)));

		assertEquals(all, engine.getAvailableNow(all, state));
	}

	@Test
	public void anyCombatStyleTheWikiOffersIsEnough()
	{
		state.setSkillLevel("ATTACK", 85);
		state.setSkillLevel("STRENGTH", 85);
		Requirement meleeOrRanged = Requirement.anyOf(
			new Requirement(Requirement.Type.COMBINED_SKILL_LEVEL, "ATTACK,STRENGTH", 170),
			level("RANGED", 90));
		List<Activity> all = Collections.singletonList(fight(false, meleeOrRanged));

		assertEquals(all, engine.getAvailableNow(all, state));
	}

	@Test
	public void theWildernessSwitchHidesWildernessFightsOnly()
	{
		Activity wild = fight(true);
		Activity safe = new Activity("pvm_y", "Safe fight", Activity.Category.PVM, Collections.emptyList());
		List<Activity> all = Arrays.asList(wild, safe);

		assertEquals(2, engine.getAvailableNow(all, state).size());
		assertEquals(Collections.singletonList(safe),
			engine.getAvailableNow(all, state, EnumSet.noneOf(Activity.Category.class), true));
	}
}
