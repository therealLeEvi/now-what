package com.nowwhat.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The item table the plugin ships has to answer every gear line exactly the way
 * the full set of item constants would. It replaced a runtime reflection read of
 * those constants, and a table that quietly fell behind the gear text would turn
 * pictures back into words with nothing to say why.
 */
public class ItemNamesTableTest
{
	private static final String REGENERATE = " - run ./gradlew itemnames and commit the result";

	@Test
	public void theShippedTableIsUpToDate()
	{
		Map<Integer, String> shipped = new TreeMap<>();
		for (ItemTextParser.Candidate candidate : ItemNameIndex.all())
		{
			shipped.put(candidate.getId(), candidate.getName());
		}
		assertEquals("item-names.txt is out of step with the gear and supply text" + REGENERATE,
			ItemNamesTable.required(), shipped);
	}

	/**
	 * The table is a cut-down candidate list, so the same phrase now chooses
	 * among fewer items. It has to choose the same one.
	 */
	@Test
	public void everyPhrasePicksWhatTheFullIndexPicks()
	{
		List<String> differ = new ArrayList<>();
		Map<String, Integer> checked = ItemNamesTable.checkedIds();
		for (String line : ItemNamesTable.kitLines())
		{
			for (ItemTextParser.Phrase phrase : ItemTextParser.phrases(line))
			{
				String term = ItemTextParser.searchTerm(phrase);
				List<ItemTextParser.Candidate> all = ItemIdConstants.search(term);
				Integer full = ItemTextParser.pick(phrase, all, checked.keySet());
				// Through the same correction the table was built with: the full
				// index still picks ULTOR_RING's unused id, the table the real one.
				for (ItemTextParser.Candidate candidate : all)
				{
					if (full != null && candidate.getId() == full)
					{
						full = ItemNamesTable.realId(full, candidate.getName(), checked);
						break;
					}
				}
				Integer shipped = ItemTextParser.pick(phrase, ItemNameIndex.search(term));
				if (!Objects.equals(full, shipped))
				{
					differ.add("\"" + phrase.getText() + "\" in \"" + line + "\": " + full + " vs " + shipped);
				}
			}
		}
		assertTrue("the shipped table picks differently for:\n  " + String.join("\n  ", differ)
			+ "\n" + REGENERATE, differ.isEmpty());
	}

	/** The untradeables RuneLite's own search cannot find, which is why the table exists. */
	@Test
	public void theUntradeablesTheGearListsNameAreInIt()
	{
		for (String name : new String[]{"scythe of vitur", "barrows gloves", "infernal cape",
			"void ranger helm"})
		{
			assertTrue(name + " should be findable", holdsSomethingNamed(name));
		}
	}

	@Test
	public void aNameNoItemHasFindsNothing()
	{
		assertFalse(holdsSomethingNamed("sword of definitely not a real item"));
		assertTrue(ItemNameIndex.search("").isEmpty());
	}

	private static boolean holdsSomethingNamed(String name)
	{
		for (ItemTextParser.Candidate hit : ItemNameIndex.search(name))
		{
			if (hit.getName().contains(name))
			{
				return true;
			}
		}
		return false;
	}

	@Test
	public void anUnusedPlainIdGivesWayToTheIdTheGameUses()
	{
		java.util.Map<String, Integer> checked = new java.util.HashMap<>();
		checked.put("ultor ring", 28307);
		checked.put("eye of ayak", 31115);

		// ULTOR_RING (25485) was never used; ULTOR_RING_28307 is the ring.
		org.junit.Assert.assertEquals(28307, ItemNamesTable.realId(25485, "ultor ring", checked));
		// EYE_OF_AYAK_UNCHARGED is a real item, just not the one worn.
		org.junit.Assert.assertEquals(31113, ItemNamesTable.realId(31113, "eye of ayak", checked));
	}
}
