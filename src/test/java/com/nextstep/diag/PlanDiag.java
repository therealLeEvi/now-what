package com.nextstep.diag;

import com.google.gson.Gson;
import com.nextstep.data.ActivityDatabase;
import com.nextstep.data.ItemSourceDatabase;
import com.nextstep.data.TrainingMethodDatabase;
import com.nextstep.engine.PathPlanner;
import com.nextstep.engine.PrerequisiteResolver;
import com.nextstep.model.Activity;
import com.nextstep.model.Goal;
import com.nextstep.model.Path;
import com.nextstep.model.PathStep;
import com.nextstep.model.PlayerState;

public class PlanDiag
{
	public static void main(String[] args)
	{
		Gson gson = new Gson();
		ActivityDatabase ad = new ActivityDatabase(gson);
		ad.load();
		TrainingMethodDatabase tm = new TrainingMethodDatabase(gson);
		tm.load();
		ItemSourceDatabase is = new ItemSourceDatabase(gson);
		is.load();

		PrerequisiteResolver pr = new PrerequisiteResolver(ad.getAllActivities());
		PathPlanner planner = new PathPlanner(ad.getAllActivities(), pr, tm, is);

		// Empty state = what latestState holds before any refresh has run.
		PlayerState empty = new PlayerState();

		for (Activity a : ad.getAllActivities())
		{
			Path p = planner.plan(Goal.activity(a.getId(), a.getName(), false), empty);
			System.out.println("ACTIVITY " + a.getId()
				+ " | complete=" + p.isAlreadyComplete()
				+ " | steps=" + p.getSteps().size()
				+ " | warnings=" + p.getWarnings().size());
			for (PathStep s : p.getSteps())
			{
				System.out.println("    step: " + s.getType() + " :: " + s.getTitle()
					+ " :: alts=" + s.getAlternatives().size());
			}
			for (String w : p.getWarnings())
			{
				System.out.println("    warn: " + w);
			}
		}

		Path sk = planner.plan(Goal.skillLevel("MINING", 70, false), empty);
		System.out.println("SKILL MINING->70 | complete=" + sk.isAlreadyComplete()
			+ " | steps=" + sk.getSteps().size() + " | warnings=" + sk.getWarnings().size());
		for (PathStep s : sk.getSteps())
		{
			System.out.println("    step: " + s.getType() + " :: " + s.getTitle()
				+ " :: alts=" + s.getAlternatives().size());
		}
	}
}
