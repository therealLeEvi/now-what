package com.nowwhat.diag;

import net.runelite.api.Quest;

/**
 * Prints every quest and miniquest RuneLite can report as finished, one per
 * line. tools/audit_quests.py reads this to tell a trackable prerequisite from
 * a wiki link to something the plugin has no completion state for.
 */
public final class QuestNames
{
	private QuestNames()
	{
	}

	public static void main(String[] args)
	{
		for (Quest quest : Quest.values())
		{
			System.out.println(quest.getName());
		}
	}
}
