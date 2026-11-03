package com.nextstep.model;

import java.util.List;

/**
 * The result of planning a route to a Goal: an ordered sequence of concrete
 * steps, plus anything the planner couldn't resolve (missing training
 * method data, missing item source data, circular crafting recipes, etc).
 */
public class Path
{
	private final Goal goal;
	private final boolean alreadyComplete;
	private final List<PathStep> steps;
	private final List<String> warnings;
	private final boolean requirementsAlreadyMet;

	public Path(Goal goal, boolean alreadyComplete, List<PathStep> steps, List<String> warnings)
	{
		this(goal, alreadyComplete, steps, warnings, false);
	}

	public Path(Goal goal, boolean alreadyComplete, List<PathStep> steps, List<String> warnings,
		boolean requirementsAlreadyMet)
	{
		this.goal = goal;
		this.alreadyComplete = alreadyComplete;
		this.steps = steps;
		this.warnings = warnings;
		this.requirementsAlreadyMet = requirementsAlreadyMet;
	}

	public Goal getGoal()
	{
		return goal;
	}

	public boolean isAlreadyComplete()
	{
		return alreadyComplete;
	}

	public List<PathStep> getSteps()
	{
		return steps;
	}

	public List<String> getWarnings()
	{
		return warnings;
	}

	/**
	 * True when nothing stands in the way: every requirement is already
	 * satisfied, but the activity itself isn't something you "complete" (a
	 * boss, a minigame), so it can't be reported as already done either.
	 * Without this the panel shows a one-item plan that reads like a bug.
	 */
	public boolean isRequirementsAlreadyMet()
	{
		return requirementsAlreadyMet;
	}
}
