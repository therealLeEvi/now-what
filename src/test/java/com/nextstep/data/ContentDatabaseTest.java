package com.nextstep.data;

import com.google.gson.Gson;
import com.nextstep.model.Activity;
import com.nextstep.data.ItemIdConstants;
import com.nextstep.model.ItemSource;
import com.nextstep.model.Requirement;
import com.nextstep.model.TrainingMethod;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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
	private List<TrainingMethod> methods;

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

		// Loaded through the database rather than read straight from JSON, so
		// these see the file exactly as the panel does - notes, gear and
		// attention already shared down each method's level brackets.
		TrainingMethodDatabase methodDatabase = new TrainingMethodDatabase(gson);
		methodDatabase.load(activities);
		methods = methodDatabase.getAllMethods();
	}

	/**
	 * A method split into brackets records its attention judgement once, and the
	 * database copies it down. If that copy ever stops happening, the panel
	 * silently drops the label from every bracket but the lowest - and a missing
	 * label does not look like a bug, it looks like an unjudged method.
	 */
	@Test
	public void everyBracketOfAJudgedMethodCarriesTheJudgement()
	{
		Set<String> judged = new HashSet<>();
		for (TrainingMethod method : methods)
		{
			if (method.getAttention() != null)
			{
				judged.add(method.getSkill() + "/" + method.getName());
			}
		}

		List<String> missing = new ArrayList<>();
		for (TrainingMethod method : methods)
		{
			String key = method.getSkill() + "/" + method.getName();
			if (judged.contains(key) && method.getAttention() == null)
			{
				missing.add(key + " " + method.getMinLevel() + "-" + method.getMaxLevel());
			}
		}

		assertEquals("brackets missing the attention judgement their siblings carry: " + missing,
			0, missing.size());
	}

	/**
	 * Tick manipulation already gets its own label in the panel. A method
	 * carrying both would print two tags that say the same thing, so the
	 * generator refuses the combination and this is the check on the shipped
	 * file.
	 */
	@Test
	public void noTickManipulationMethodAlsoClaimsAnAttentionJudgement()
	{
		List<String> both = new ArrayList<>();
		for (TrainingMethod method : methods)
		{
			if (method.isTickManipulation() && method.getAttention() != null)
			{
				both.add(method.getSkill() + "/" + method.getName());
			}
		}

		assertEquals("tick-manipulation methods should carry no attention judgement: " + both,
			0, both.size());
	}

	/**
	 * An item that borrows a fight's kit has to name a fight that exists.
	 * <p>
	 * Nothing throws if it does not - {@code NextStepPanel} looks the id up,
	 * gets null, and shows the drop with no answer to "what do I take?", which
	 * looks exactly like an item nobody wrote a kit for.
	 */
	@Test
	public void everyItemBorrowingKitNamesAKnownActivity()
	{
		Set<String> ids = new HashSet<>();
		for (Activity activity : activities)
		{
			ids.add(activity.getId());
		}

		List<String> dangling = new ArrayList<>();
		int borrowing = 0;
		for (ItemSource item : items)
		{
			if (item.getPreparationFrom() == null)
			{
				continue;
			}
			borrowing++;
			if (!ids.contains(item.getPreparationFrom()))
			{
				dangling.add(item.getDisplayName() + " -> " + item.getPreparationFrom());
			}
		}

		assertEquals("items borrowing kit from no such activity: " + dangling,
			0, dangling.size());
		assertTrue("no item borrows kit any more - did the link get dropped?", borrowing > 0);
	}

	/**
	 * A shop price quoted in a currency you earn has to say where you earn it.
	 * <p>
	 * "Tithe Farm reward shop, 250 points" was the complaint that produced this:
	 * the panel named a shop and a number and stopped, with nothing about what
	 * Tithe Farm is, how points accrue or how long 250 of them take. The ones
	 * still missing a link are listed here by name, so adding a new points shop
	 * fails this test until it either gets its activity or is admitted to the
	 * list - a dead end has to be a decision, not an oversight.
	 */
	@Test
	public void everyShopPricedInEarnedCurrencyNamesWhereItIsEarned()
	{
		// Empty on purpose. Graceful, the herb sack and the rune pouch were the
		// last three, and each now names where its currency is earned. A new
		// points shop that cannot do the same is admitted here by name.
		Set<String> knownGaps = new HashSet<>();

		List<String> deadEnds = new ArrayList<>();
		for (ItemSource item : items)
		{
			if (item.getType() != ItemSource.SourceType.SHOP || item.getDetail() == null)
			{
				continue;
			}
			String detail = item.getDetail().toLowerCase(java.util.Locale.ROOT);
			boolean earned = detail.contains("point") || detail.contains("mark")
				|| detail.contains("token") || detail.contains("reward");
			if (earned && item.getCurrencyFrom() == null && !knownGaps.contains(item.getId()))
			{
				deadEnds.add(item.getId() + " (" + item.getDetail() + ")");
			}
		}

		assertEquals("shop items quoting an earned price with nowhere to earn it: " + deadEnds,
			0, deadEnds.size());
	}

	/**
	 * Every {@code gameItemId} has to be a real item, and has to be the item it
	 * claims to be.
	 * <p>
	 * This is the check the first pass did not have, and two ids typed from
	 * memory back then were wrong. A wrong id is silent: nothing throws, the
	 * panel simply shows a picture of a different item, and the only way to
	 * notice is to recognise the sprite. The name a constant reads back as is
	 * approximate - {@code MASORI_BODY_F} comes back as "masori body (f)" - so
	 * the match is deliberately loose: the first word of the display name has
	 * to appear in the constant. That is enough to catch an id pointing at
	 * something else entirely, which is the failure that matters.
	 */
	@Test
	public void everyGameItemIdNamesTheItemItClaimsTo()
	{
		Map<Integer, List<String>> byId = new HashMap<>();
		for (ItemTextParser.Candidate candidate : ItemIdConstants.all())
		{
			byId.computeIfAbsent(candidate.getId(), k -> new ArrayList<>())
				.add(candidate.getName());
		}
		assertTrue("no ItemID constants on the classpath - this test proves nothing",
			byId.size() > 1000);

		List<String> wrong = new ArrayList<>();
		for (ItemSource item : items)
		{
			List<String> names = byId.get(item.getGameItemId());
			if (names == null)
			{
				wrong.add(item.getDisplayName() + " -> " + item.getGameItemId()
					+ " is not an item id at all");
				continue;
			}
			String first = flattenFirstWord(item.getDisplayName());
			boolean ok = false;
			for (String name : names)
			{
				if (flatten(name).contains(first))
				{
					ok = true;
					break;
				}
			}
			if (!ok)
			{
				wrong.add(item.getDisplayName() + " -> " + item.getGameItemId()
					+ " which is " + names);
			}
		}

		assertEquals("item ids naming the wrong item: " + wrong, 0, wrong.size());
	}

	private static String flattenFirstWord(String text)
	{
		String[] words = flatten(text).split(" ");
		return words.length == 0 ? "" : words[0];
	}

	private static String flatten(String text)
	{
		return text.toLowerCase(java.util.Locale.ROOT)
			.replace("'", "")
			.replace("-", "")
			.replace("(", "")
			.replace(")", "");
	}

	/**
	 * A method may borrow its kit from an activity, and a borrow that names an
	 * id nothing answers to is the same silent failure as a dangling quest
	 * reference: the plan renders, the gear is just missing.
	 */
	@Test
	public void everyBorrowedKitNamesAKnownActivity()
	{
		Set<String> ids = new HashSet<>();
		for (Activity activity : activities)
		{
			ids.add(activity.getId());
		}

		List<String> dangling = new ArrayList<>();
		for (TrainingMethod method : methods)
		{
			if (method.getActivity() != null && !ids.contains(method.getActivity()))
			{
				dangling.add(method.getName() + " -> " + method.getActivity());
			}
		}

		assertEquals("methods borrowing kit from no such activity: " + dangling,
			0, dangling.size());
	}

	/**
	 * And the borrow has to actually land. Wintertodt is the regression this
	 * guards: it is a Firemaking method whose four warm-clothing slots live in
	 * the minigame entry, and for a while the skill path named it and then said
	 * nothing about what to wear.
	 */
	@Test
	public void aMethodThatBorrowsKitEndsUpWithIt()
	{
		int checked = 0;
		for (TrainingMethod method : methods)
		{
			if (method.getActivity() == null)
			{
				continue;
			}
			checked++;
			assertTrue(method.getName() + " borrows from " + method.getActivity()
					+ " but came out with no gear and no supplies",
				!method.getGear().isEmpty() || !method.getSupplies().isEmpty());
		}
		assertTrue("no method borrows kit any more - did the link get dropped?", checked > 0);
	}

	/**
	 * A shopping list needs both halves: the xp of one action and what that
	 * action uses up. Half of it recorded is a count that silently never
	 * appears, which is the hardest kind of data bug to notice.
	 */
	@Test
	public void everyCountableMethodRecordsBothHalvesOfItsShoppingList()
	{
		List<String> broken = new ArrayList<>();
		for (TrainingMethod method : methods)
		{
			boolean hasRate = method.getXpPerAction() != null && method.getXpPerAction() > 0;
			boolean hasItems = !method.getConsumes().isEmpty();
			if (hasRate != hasItems)
			{
				broken.add(method.getSkill() + "/" + method.getName()
					+ " xpPerAction=" + method.getXpPerAction()
					+ " consumes=" + method.getConsumes().size());
			}
			for (TrainingMethod.Consumed consumed : method.getConsumes())
			{
				if (consumed.getItem() == null || consumed.getItem().trim().isEmpty())
				{
					broken.add(method.getName() + " consumes a nameless item");
				}
			}
		}

		assertEquals("methods with a half-recorded shopping list: " + broken, 0, broken.size());
	}

	/**
	 * Woodcutting is judged end to end, and should stay that way. The axis is
	 * populated skill by skill, so a partially judged skill is the dangerous
	 * state: an unlabelled method next to labelled ones reads as "ordinary"
	 * when it actually means "nobody has looked". Add the judgement when you
	 * add the method, or add the skill to the exemption below with a reason.
	 */
	@Test
	public void everyWoodcuttingMethodIsJudgedForAttention()
	{
		List<String> unjudged = new ArrayList<>();
		for (TrainingMethod method : methods)
		{
			if (!"WOODCUTTING".equals(method.getSkill()) || method.isTickManipulation())
			{
				continue;
			}
			if (method.getAttention() == null)
			{
				unjudged.add(method.getName() + " " + method.getMinLevel()
					+ "-" + method.getMaxLevel());
			}
		}

		assertEquals("Woodcutting methods with no attention judgement: " + unjudged,
			0, unjudged.size());
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
