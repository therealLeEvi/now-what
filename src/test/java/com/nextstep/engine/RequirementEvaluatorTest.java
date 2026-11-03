package com.nextstep.engine;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.nextstep.model.Activity;
import com.nextstep.model.PlayerState;
import com.nextstep.model.Requirement;
import java.util.Collections;
import org.junit.Test;

public class RequirementEvaluatorTest
{
	@Test
	public void skillLevelMetAndUnmet()
	{
		PlayerState state = new PlayerState();
		state.setSkillLevel("AGILITY", 50);

		assertTrue(RequirementEvaluator.isMet(new Requirement(Requirement.Type.SKILL_LEVEL, "AGILITY", 47), state));
		assertTrue(RequirementEvaluator.isMet(new Requirement(Requirement.Type.SKILL_LEVEL, "AGILITY", 50), state));
		assertFalse(RequirementEvaluator.isMet(new Requirement(Requirement.Type.SKILL_LEVEL, "AGILITY", 51), state));
	}

	@Test
	public void questCompleteChecksExactName()
	{
		PlayerState state = new PlayerState();
		state.addCompletedQuest("Dragon Slayer I");

		assertTrue(RequirementEvaluator.isMet(new Requirement(Requirement.Type.QUEST_COMPLETE, "Dragon Slayer I", 0), state));
		assertFalse(RequirementEvaluator.isMet(new Requirement(Requirement.Type.QUEST_COMPLETE, "Dragon Slayer II", 0), state));
	}

	@Test
	public void anyOfIsMetWhenAnySingleChildIsMet()
	{
		PlayerState state = new PlayerState();
		state.setSkillLevel("ATTACK", 60);
		state.setSkillLevel("STRENGTH", 99);

		Requirement anyOf = Requirement.anyOf(
			new Requirement(Requirement.Type.SKILL_LEVEL, "ATTACK", 99),
			new Requirement(Requirement.Type.SKILL_LEVEL, "STRENGTH", 99)
		);

		assertTrue(RequirementEvaluator.isMet(anyOf, state));
	}

	@Test
	public void anyOfIsUnmetWhenNoChildIsMet()
	{
		PlayerState state = new PlayerState();
		state.setSkillLevel("ATTACK", 60);
		state.setSkillLevel("STRENGTH", 60);

		Requirement anyOf = Requirement.anyOf(
			new Requirement(Requirement.Type.SKILL_LEVEL, "ATTACK", 99),
			new Requirement(Requirement.Type.SKILL_LEVEL, "STRENGTH", 99)
		);

		assertFalse(RequirementEvaluator.isMet(anyOf, state));
	}

	@Test
	public void combinedSkillLevelSumsAllListedSkills()
	{
		PlayerState state = new PlayerState();
		state.setSkillLevel("ATTACK", 65);
		state.setSkillLevel("STRENGTH", 65); // combined 130

		assertTrue(RequirementEvaluator.isMet(
			new Requirement(Requirement.Type.COMBINED_SKILL_LEVEL, "ATTACK,STRENGTH", 130), state));
		assertFalse(RequirementEvaluator.isMet(
			new Requirement(Requirement.Type.COMBINED_SKILL_LEVEL, "ATTACK,STRENGTH", 131), state));
	}

	@Test
	public void warriorsGuildStyleGateIsMetByTheCombinedBranchAlone()
	{
		// Regression guard for the exact real-game shape: neither single skill
		// at 99, but the combined branch of the ANY_OF still satisfies it.
		PlayerState state = new PlayerState();
		state.setSkillLevel("ATTACK", 65);
		state.setSkillLevel("STRENGTH", 65);

		Requirement anyOf = Requirement.anyOf(
			new Requirement(Requirement.Type.SKILL_LEVEL, "ATTACK", 99),
			new Requirement(Requirement.Type.SKILL_LEVEL, "STRENGTH", 99),
			new Requirement(Requirement.Type.COMBINED_SKILL_LEVEL, "ATTACK,STRENGTH", 130)
		);

		assertTrue(RequirementEvaluator.isMet(anyOf, state));
	}

	@Test
	public void isAlreadyDoneDelegatesToCategorySpecificState()
	{
		PlayerState state = new PlayerState();
		state.addCompletedQuest("Cook's Assistant");

		Activity quest = new Activity("quest_x", "Cook's Assistant", Activity.Category.QUEST, Collections.emptyList());
		assertTrue(RequirementEvaluator.isAlreadyDone(quest, state));

		Activity unrelatedQuest = new Activity("quest_y", "Some Other Quest", Activity.Category.QUEST, Collections.emptyList());
		assertFalse(RequirementEvaluator.isAlreadyDone(unrelatedQuest, state));
	}

	@Test
	public void isAlreadyDoneIsFalseForOpenEndedCategories()
	{
		// PVM/SKILLING/MINIGAME/CLUE_SCROLL have no single "done" state.
		PlayerState state = new PlayerState();
		Activity pvm = new Activity("pvm_x", "Some Boss", Activity.Category.PVM, Collections.emptyList());
		assertFalse(RequirementEvaluator.isAlreadyDone(pvm, state));
	}
}
