package com.nextstep.model;

import java.util.List;

/**
 * Result of resolving "what do I need to do to unlock X".
 */
public class PrerequisiteChain
{
	private final Activity target;
	private final boolean alreadyUnlocked;
	/** Other activities (quests/diary tiers) you must complete first, in the order to do them. */
	private final List<Activity> requiredActivitiesInOrder;
	/** Requirements that aren't themselves a trackable activity (skill levels, KC, QP, combat level). */
	private final List<Requirement> standaloneRequirements;
	/** Requirement values referenced (e.g. a quest name) that weren't found in the activity database - can't be chained further. */
	private final List<String> unresolvedReferences;

	public PrerequisiteChain(Activity target, boolean alreadyUnlocked, List<Activity> requiredActivitiesInOrder,
		List<Requirement> standaloneRequirements, List<String> unresolvedReferences)
	{
		this.target = target;
		this.alreadyUnlocked = alreadyUnlocked;
		this.requiredActivitiesInOrder = requiredActivitiesInOrder;
		this.standaloneRequirements = standaloneRequirements;
		this.unresolvedReferences = unresolvedReferences;
	}

	public Activity getTarget()
	{
		return target;
	}

	public boolean isAlreadyUnlocked()
	{
		return alreadyUnlocked;
	}

	public List<Activity> getRequiredActivitiesInOrder()
	{
		return requiredActivitiesInOrder;
	}

	public List<Requirement> getStandaloneRequirements()
	{
		return standaloneRequirements;
	}

	public List<String> getUnresolvedReferences()
	{
		return unresolvedReferences;
	}
}
