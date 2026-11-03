package com.nextstep.data;

import com.google.gson.Gson;
import com.nextstep.model.Activity;
import com.nextstep.model.ItemSource;
import com.nextstep.model.Requirement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Integrity checks on the bundled content database.
 *
 * <p>These exist because the failure mode they catch is invisible: a
 * requirement naming a quest that isn't in the file doesn't crash anything,
 * it just quietly degrades into an "unresolved reference" warning in the
 * panel, and a chain that should have been eight steps long silently becomes
 * three. Content is edited far more often than code here, so the build should
 * be what notices.
 */
public class ContentDatabaseTest
{
	private static final Set<String> VALID_SKILLS = new HashSet<>(java.util.Arrays.asList(
		"ATTACK", "DEFENCE", "STRENGTH", "HITPOINTS", "RANGED", "PRAYER", "MAGIC", "COOKING",
		"WOODCUTTING", "FLETCHING", "FISHING", "FIREMAKING", "CRAFTING", "SMITHING", "MINING",
		"HERBLORE", "AGILITY", "THIEVING", "SLAYER", "FARMING", "RUNECRAFT", "HUNTER",
		"CONSTRUCTION", "SAILING"));

	private List<Activity> activities;
	private List<ItemSource> items;

	@Before
	public void setUp()
	{
		Gson gson = new Gson();
		ActivityDatabase activityDatabase = new ActivityDatabase(gson);
		activityDatabase.load();
		activities = activityDatabase.getAllActivities();

		ItemSourceDatabase itemDatabase = new ItemSourceDatabase(gson);
		itemDatabase.load();
		items = itemDatabase.getAllSources();
	}

	@Test
	public void activitiesLoad()
	{
		assertTrue("activities.json should not be empty", activities.size() > 50);
	}

	@Test
	public void activityIdsAreUnique()
	{
		Set<String> seen = new HashSet<>();
		List<String> duplicates = new ArrayList<>();
		for (Activity activity : activities)
		{
			if (!seen.add(activity.getId()))
			{
				duplicates.add(activity.getId());
			}
		}
		assertEquals("duplicate activity ids: " + duplicates, 0, duplicates.size());
	}

	@Test
	public void everyQuestRequirementNamesAKnownQuest()
	{
		Set<String> questNames = new HashSet<>();
		for (Activity activity : activities)
		{
			if (activity.getCategory() == Activity.Category.QUEST)
			{
				questNames.add(activity.getName());
			}
		}

		List<String> dangling = new ArrayList<>();
		forEachRequirement((activity, req) ->
		{
			if (req.getType() == Requirement.Type.QUEST_COMPLETE && !questNames.contains(req.getValue()))
			{
				dangling.add(activity.getId() + " -> \"" + req.getValue() + "\"");
			}
		});
		assertEquals("quest requirements with no matching QUEST activity: " + dangling,
			0, dangling.size());
	}

	@Test
	public void everyDiaryRequirementNamesAKnownDiaryTier()
	{
		Set<String> diaryIds = new HashSet<>();
		for (Activity activity : activities)
		{
			if (activity.getCategory() == Activity.Category.DIARY)
			{
				diaryIds.add(activity.getId().toUpperCase());
			}
		}

		List<String> dangling = new ArrayList<>();
		forEachRequirement((activity, req) ->
		{
			if (req.getType() == Requirement.Type.DIARY_COMPLETE
				&& !diaryIds.contains(req.getValue().toUpperCase()))
			{
				dangling.add(activity.getId() + " -> \"" + req.getValue() + "\"");
			}
		});
		assertEquals("diary requirements with no matching DIARY activity: " + dangling,
			0, dangling.size());
	}

	@Test
	public void everySkillRequirementNamesARealSkill()
	{
		List<String> bad = new ArrayList<>();
		forEachRequirement((activity, req) ->
		{
			if (req.getType() == Requirement.Type.SKILL_LEVEL && !VALID_SKILLS.contains(req.getValue()))
			{
				bad.add(activity.getId() + " -> \"" + req.getValue() + "\"");
			}
			if (req.getType() == Requirement.Type.SKILL_LEVEL
				&& (req.getThreshold() < 1 || req.getThreshold() > 99))
			{
				bad.add(activity.getId() + " -> " + req.getValue() + " level " + req.getThreshold());
			}
		});
		assertEquals("invalid skill requirements: " + bad, 0, bad.size());
	}

	@Test
	public void itemsLoadAndCarryGameIds()
	{
		assertTrue("item-sources.json should not be empty", items.size() > 10);

		List<String> missing = new ArrayList<>();
		for (ItemSource item : items)
		{
			// Without a game item id the planner cannot subtract what the
			// player already owns, which is the whole point of bank tracking.
			if (item.getGameItemId() == null || item.getGameItemId() <= 0)
			{
				missing.add(item.getId());
			}
		}
		assertEquals("item sources with no usable gameItemId: " + missing, 0, missing.size());
	}

	@Test
	public void everyCraftIngredientResolves()
	{
		Set<String> itemIds = new HashSet<>();
		for (ItemSource item : items)
		{
			itemIds.add(item.getId().toUpperCase());
		}

		List<String> dangling = new ArrayList<>();
		for (ItemSource item : items)
		{
			if (item.getIngredients() == null)
			{
				continue;
			}
			for (ItemSource.Ingredient ingredient : item.getIngredients())
			{
				if (!itemIds.contains(ingredient.getItemId().toUpperCase()))
				{
					dangling.add(item.getId() + " -> " + ingredient.getItemId());
				}
			}
		}
		assertEquals("craft ingredients with no matching item source: " + dangling,
			0, dangling.size());
	}

	private interface RequirementVisitor
	{
		void visit(Activity activity, Requirement requirement);
	}

	/** Walks every requirement, including the children of ANY_OF groups. */
	private void forEachRequirement(RequirementVisitor visitor)
	{
		for (Activity activity : activities)
		{
			for (Requirement req : activity.getRequirements())
			{
				visitor.visit(activity, req);
				if (req.getAnyOf() != null)
				{
					for (Requirement child : req.getAnyOf())
					{
						visitor.visit(activity, child);
					}
				}
			}
		}
	}
}
