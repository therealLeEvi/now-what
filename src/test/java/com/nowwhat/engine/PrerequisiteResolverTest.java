package com.nowwhat.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.nowwhat.model.Activity;
import com.nowwhat.model.PlayerState;
import com.nowwhat.model.PrerequisiteChain;
import com.nowwhat.model.Requirement;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class PrerequisiteResolverTest
{
	@Test
	public void alreadyUnlockedActivityReturnsEmptyChain()
	{
		Activity quest = new Activity("q1", "Simple Quest", Activity.Category.QUEST, Arrays.asList());
		PrerequisiteResolver resolver = new PrerequisiteResolver(Arrays.asList(quest));

		PlayerState state = new PlayerState();
		state.addCompletedQuest("Simple Quest");

		PrerequisiteChain chain = resolver.resolve(quest, state);

		assertTrue(chain.isAlreadyUnlocked());
		assertTrue(chain.getRequiredActivitiesInOrder().isEmpty());
	}

	@Test
	public void transitiveQuestChainIsResolvedInPrerequisiteFirstOrder()
	{
		Activity questOne = new Activity("q1", "Quest One", Activity.Category.QUEST, Arrays.asList());
		Activity questTwo = new Activity("q2", "Quest Two", Activity.Category.QUEST, Arrays.asList(
			new Requirement(Requirement.Type.QUEST_COMPLETE, "Quest One", 0)
		));
		Activity targetBoss = new Activity("boss", "Some Boss", Activity.Category.PVM, Arrays.asList(
			new Requirement(Requirement.Type.QUEST_COMPLETE, "Quest Two", 0)
		));

		PrerequisiteResolver resolver = new PrerequisiteResolver(Arrays.asList(questOne, questTwo, targetBoss));
		PlayerState state = new PlayerState(); // nothing completed

		PrerequisiteChain chain = resolver.resolve(targetBoss, state);

		List<Activity> steps = chain.getRequiredActivitiesInOrder();
		assertEquals(2, steps.size());
		assertEquals("Quest One", steps.get(0).getName()); // deepest prerequisite first
		assertEquals("Quest Two", steps.get(1).getName());
		assertTrue(chain.getUnresolvedReferences().isEmpty());
	}

	@Test
	public void alreadyMetLinksInTheChainAreSkipped()
	{
		Activity questOne = new Activity("q1", "Quest One", Activity.Category.QUEST, Arrays.asList());
		Activity questTwo = new Activity("q2", "Quest Two", Activity.Category.QUEST, Arrays.asList(
			new Requirement(Requirement.Type.QUEST_COMPLETE, "Quest One", 0)
		));

		PrerequisiteResolver resolver = new PrerequisiteResolver(Arrays.asList(questOne, questTwo));
		PlayerState state = new PlayerState();
		state.addCompletedQuest("Quest One"); // already done - shouldn't appear in the chain

		PrerequisiteChain chain = resolver.resolve(questTwo, state);

		assertTrue(chain.getRequiredActivitiesInOrder().isEmpty());
	}

	@Test
	public void unknownQuestReferenceIsReportedNotSilentlyDropped()
	{
		Activity target = new Activity("q1", "Quest With Gap", Activity.Category.QUEST, Arrays.asList(
			new Requirement(Requirement.Type.QUEST_COMPLETE, "Nonexistent Quest", 0)
		));

		PrerequisiteResolver resolver = new PrerequisiteResolver(Arrays.asList(target));
		PrerequisiteChain chain = resolver.resolve(target, new PlayerState());

		assertEquals(1, chain.getUnresolvedReferences().size());
		assertTrue(chain.getUnresolvedReferences().get(0).contains("Nonexistent Quest"));
	}

	@Test
	public void circularQuestReferenceDoesNotInfiniteLoop()
	{
		// A -> requires B, B -> requires A. Must terminate, not hang.
		Activity questA = new Activity("qa", "Quest A", Activity.Category.QUEST, Arrays.asList(
			new Requirement(Requirement.Type.QUEST_COMPLETE, "Quest B", 0)
		));
		Activity questB = new Activity("qb", "Quest B", Activity.Category.QUEST, Arrays.asList(
			new Requirement(Requirement.Type.QUEST_COMPLETE, "Quest A", 0)
		));

		PrerequisiteResolver resolver = new PrerequisiteResolver(Arrays.asList(questA, questB));

		// The assertion here is really "this call returns at all" - a hang
		// would time out the test run rather than fail an assertion.
		PrerequisiteChain chain = resolver.resolve(questA, new PlayerState());

		assertTrue(chain.getUnresolvedReferences().stream().anyMatch(s -> s.contains("circular")));
	}

	@Test
	public void theSameSkillAskedTwiceKeepsTheHigherLevel()
	{
		// The low ask is walked first, as The Dig Site's 10 Agility is on the
		// way to Desert Treasure II's 69.
		Activity early = new Activity("q1", "Early Quest", Activity.Category.QUEST, Arrays.asList(
			new Requirement(Requirement.Type.SKILL_LEVEL, "AGILITY", 10)
		));
		Activity late = new Activity("q2", "Late Quest", Activity.Category.QUEST, Arrays.asList(
			new Requirement(Requirement.Type.QUEST_COMPLETE, "Early Quest", 0),
			new Requirement(Requirement.Type.SKILL_LEVEL, "AGILITY", 69)
		));

		PrerequisiteResolver resolver = new PrerequisiteResolver(Arrays.asList(early, late));
		PrerequisiteChain chain = resolver.resolve(late, new PlayerState());

		assertEquals(1, chain.getStandaloneRequirements().size());
		assertEquals(69, chain.getStandaloneRequirements().get(0).getThreshold());
	}

	@Test
	public void twoDifferentChoicesAreBothKept()
	{
		Activity target = new Activity("boss", "Some Boss", Activity.Category.PVM, Arrays.asList(
			Requirement.anyOf(
				new Requirement(Requirement.Type.SKILL_LEVEL, "ATTACK", 99),
				new Requirement(Requirement.Type.SKILL_LEVEL, "STRENGTH", 99)),
			Requirement.anyOf(
				new Requirement(Requirement.Type.SKILL_LEVEL, "RANGED", 99),
				new Requirement(Requirement.Type.SKILL_LEVEL, "MAGIC", 99))
		));

		PrerequisiteResolver resolver = new PrerequisiteResolver(Arrays.asList(target));
		PrerequisiteChain chain = resolver.resolve(target, new PlayerState());

		assertEquals(2, chain.getStandaloneRequirements().size());
	}

	@Test
	public void diaryTierChainResolvesThroughLowerTierFirst()
	{
		Activity easyTier = new Activity("ARDOUGNE_EASY", "Ardougne Easy", Activity.Category.DIARY, Arrays.asList());
		Activity mediumTier = new Activity("ARDOUGNE_MEDIUM", "Ardougne Medium", Activity.Category.DIARY, Arrays.asList(
			new Requirement(Requirement.Type.DIARY_COMPLETE, "ARDOUGNE_EASY", 0)
		));

		PrerequisiteResolver resolver = new PrerequisiteResolver(Arrays.asList(easyTier, mediumTier));
		PrerequisiteChain chain = resolver.resolve(mediumTier, new PlayerState());

		assertEquals(1, chain.getRequiredActivitiesInOrder().size());
		assertEquals("Ardougne Easy", chain.getRequiredActivitiesInOrder().get(0).getName());
	}
}
