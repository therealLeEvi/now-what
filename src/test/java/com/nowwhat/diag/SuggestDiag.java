package com.nowwhat.diag;

import com.google.gson.Gson;
import com.nowwhat.data.ActivityDatabase;
import com.nowwhat.engine.SuggestionEngine;
import com.nowwhat.model.Activity;
import com.nowwhat.model.PlayerState;

/** Prints the fights suggested to an account with every skill at the given level. */
public final class SuggestDiag
{
	private SuggestDiag()
	{
	}

	public static void main(String[] args) throws Exception
	{
		ActivityDatabase db = new ActivityDatabase(new Gson());
		db.load();
		SuggestionEngine engine = new SuggestionEngine();
		for (String arg : args)
		{
			int level = Integer.parseInt(arg);
			PlayerState state = new PlayerState();
			for (String skill : new String[]{"ATTACK", "STRENGTH", "DEFENCE", "RANGED", "MAGIC", "PRAYER",
				"HITPOINTS", "SLAYER", "AGILITY", "HERBLORE", "FARMING", "HUNTER", "CRAFTING", "MINING",
				"SMITHING", "FISHING", "COOKING", "FIREMAKING", "WOODCUTTING", "RUNECRAFT", "THIEVING",
				"FLETCHING", "CONSTRUCTION", "SAILING"})
			{
				state.setSkillLevel(skill, skill.equals("HITPOINTS") ? Math.max(10, level) : level);
			}
			state.setCombatLevel(Math.max(3, (int) (level * 1.24)));
			StringBuilder now = new StringBuilder();
			for (Activity a : engine.getAvailableNow(db.getAllActivities(), state))
			{
				if (a.getCategory() == Activity.Category.PVM || a.getCategory() == Activity.Category.MINIGAME)
				{
					now.append(a.getName()).append(", ");
				}
			}
			StringBuilder almost = new StringBuilder();
			for (Activity a : engine.getAlmostAvailable(db.getAllActivities(), state))
			{
				if (a.getCategory() == Activity.Category.PVM)
				{
					almost.append(a.getName()).append(" (").append(engine.whatIsMissing(a, state)).append("); ");
				}
			}
			System.out.println("== every skill " + level + "\nNOW: " + now + "\nALMOST: " + almost);
		}
	}
}
