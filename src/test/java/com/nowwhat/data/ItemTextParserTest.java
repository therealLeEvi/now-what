package com.nowwhat.data;

import com.nowwhat.data.ItemTextParser.Candidate;
import com.nowwhat.data.ItemTextParser.Phrase;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * The gear lines in activities.json are prose, and turning prose into item ids
 * is guesswork. These tests are where the guessing is held to account: a wrong
 * icon beside a boss setup is worse than no icon, so the cases that must come
 * back empty matter as much as the ones that must resolve.
 */
public class ItemTextParserTest
{
	/**
	 * Stands in for RuneLite's item index. Substring matching on the longest
	 * word, which is what ItemManager.search does, so the filtering under test
	 * sees the same shape of input it will see in a client.
	 */
	private static final List<Candidate> ITEMS = Arrays.asList(
		new Candidate("Occult necklace", 19553),
		new Candidate("Amulet of fury", 6585),
		new Candidate("Amulet of blood fury", 24780),
		new Candidate("Amulet of torture", 19553),
		new Candidate("Amulet of rancour", 29801),
		new Candidate("Masori mask", 27226),
		new Candidate("Masori mask (f)", 27235),
		new Candidate("Masori body", 27229),
		new Candidate("Masori body (f)", 27238),
		new Candidate("Masori chaps", 27232),
		new Candidate("Masori chaps (f)", 27241),
		new Candidate("Crystal key", 989),
		new Candidate("Crystal pickaxe", 23863),
		new Candidate("Crystal boots", 23644),
		new Candidate("Twisted bow", 20997),
		new Candidate("Toxic blowpipe", 12926),
		new Candidate("Toxic blowpipe (empty)", 12922),
		new Candidate("Prayer potion(4)", 2434),
		new Candidate("Prayer potion(3)", 139),
		new Candidate("Berserker ring", 6737),
		new Candidate("Berserker ring (i)", 11773),
		new Candidate("Ultor ring", 28307),
		new Candidate("Dizana's quiver", 28947),
		new Candidate("Dizana's quiver (uncharged)", 28902),
		new Candidate("Elidinis' ward (f)", 25988),
		new Candidate("Elidinis' ward", 25985),
		new Candidate("Zaryte vambraces", 26235),
		new Candidate("Barrows gloves", 7462),
		new Candidate("Twisted buckler", 21000),
		new Candidate("Bandos chestplate", 11832),
		new Candidate("Bandos tassets", 11834),
		new Candidate("Void ranger helm", 11664),
		new Candidate("Magic shortbow (i)", 12788),
		new Candidate("Torag's platebody", 4749),
		new Candidate("Torag's platelegs", 4751),
		new Candidate("Facemask", 4166),
		new Candidate("Shark", 385),
		new Candidate("Ava's accumulator", 10499));

	/** The id a whole written line resolves to, phrase by phrase. */
	private List<Integer> resolve(String entry)
	{
		List<Integer> ids = new ArrayList<>();
		for (Phrase phrase : ItemTextParser.phrases(entry))
		{
			Integer id = ItemTextParser.pick(phrase, search(phrase));
			if (id != null && !ids.contains(id))
			{
				ids.add(id);
			}
		}
		return ids;
	}

	/** Substring search on the term the parser asks for, as the real index does. */
	private List<Candidate> search(Phrase phrase)
	{
		String longest = ItemTextParser.searchTerm(phrase);
		List<Candidate> hits = new ArrayList<>();
		for (Candidate candidate : ITEMS)
		{
			if (candidate.getName().toLowerCase(Locale.ROOT).contains(longest))
			{
				hits.add(candidate);
			}
		}
		return hits;
	}

	private String nameOf(int id)
	{
		for (Candidate candidate : ITEMS)
		{
			if (candidate.getId() == id)
			{
				return candidate.getName();
			}
		}
		return "?" + id;
	}

	private List<String> names(String entry)
	{
		List<String> names = new ArrayList<>();
		for (Integer id : resolve(entry))
		{
			names.add(nameOf(id));
		}
		return names;
	}

	@Test
	public void plainItemNameResolves()
	{
		assertEquals(Arrays.asList("Twisted bow"), names("twisted bow"));
	}

	@Test
	public void alternativesEachResolve()
	{
		assertEquals(Arrays.asList("Twisted bow", "Toxic blowpipe"),
			names("twisted bow or toxic blowpipe"));
	}

	@Test
	public void shortestNameWinsOverItsVariants()
	{
		// "Toxic blowpipe (empty)" also contains every word of the phrase.
		assertEquals(Arrays.asList("Toxic blowpipe"), names("toxic blowpipe"));
	}

	@Test
	public void slotPinsDownAOneWordOption()
	{
		assertEquals(Arrays.asList("Occult necklace", "Amulet of fury"),
			names("Neck: occult then fury"));
	}

	@Test
	public void oneWordOptionWithoutASlotResolvesToNothing()
	{
		// This is the whole reason the slot prefix is read: bare "crystal" is a
		// key, a pickaxe and a pair of boots, and guessing is worse than silence.
		assertTrue(names("Best pickaxe you can wield - crystal, dragon or infernal").isEmpty());
	}

	@Test
	public void slotKeepsAOneWordOptionOffTheWrongItem()
	{
		assertEquals(Arrays.asList("Crystal boots"), names("Boots: crystal"));
	}

	@Test
	public void aSetExpandsOverEverySlotItNames()
	{
		assertEquals(Arrays.asList("Masori mask (f)", "Masori body (f)", "Masori chaps (f)"),
			names("Head/body/legs: Masori (f)"));
	}

	@Test
	public void severalSetsAcrossSeveralSlots()
	{
		assertEquals(Arrays.asList("Masori mask (f)", "Masori body (f)", "Masori chaps (f)",
			"Masori mask", "Masori body", "Masori chaps"),
			names("Head/body/legs: Masori (f), Masori"));
	}

	@Test
	public void commaSeparatedSetInOneOption()
	{
		// Only the first piece says "Bandos"; the second is named on its own.
		assertEquals(Arrays.asList("Bandos chestplate", "Bandos tassets"),
			names("Bandos chestplate and tassets"));
	}

	@Test
	public void quantityPrefixAndPluralSupplyStillResolve()
	{
		assertEquals(Arrays.asList("Prayer potion(4)"), names("8-10 prayer potions (4)"));
	}

	@Test
	public void trailingExplanationIsNotTreatedAsAnItem()
	{
		assertEquals(Arrays.asList("Twisted bow"),
			names("twisted bow - borrow one if you have to"));
	}

	@Test
	public void imbuedVariantIsMatchedWhenAskedFor()
	{
		assertEquals(Arrays.asList("Berserker ring (i)"), names("Ring: berserker (i)"));
	}

	@Test
	public void prosePhraseResolvesToNothing()
	{
		assertTrue(names("40 Prayer for Protect from Magic").isEmpty());
		assertTrue(names("An active gargoyle Slayer task").isEmpty());
	}

	@Test
	public void armourWrittenByItsShortNameStillResolves()
	{
		// "Torag's body" is Torag's platebody; only the slot groups know that.
		assertEquals(Arrays.asList("Torag's platebody", "Torag's platelegs"),
			names("Torag's body and legs"));
	}

	@Test
	public void aWordThatIsAWholeItemNameNeedsNoSlot()
	{
		assertEquals(Arrays.asList("Facemask"), names("Facemask or slayer helmet").subList(0, 1));
		assertEquals(Arrays.asList("Shark"), names("Sharks to fill the rest"));
	}

	@Test
	public void apostrophesAreNotLoadBearing()
	{
		assertEquals(Arrays.asList("Ava's accumulator"), names("Avas accumulator"));
	}

	@Test
	public void anUnchargedVariantStandsInWhenItIsAllThereIs()
	{
		// The charged scythe is untradeable, so an index built from Grand
		// Exchange prices only has the uncharged one. A picture of it is still
		// a picture of the right weapon.
		List<Candidate> onlyUncharged = Arrays.asList(
			new Candidate("Scythe of vitur (uncharged)", 22486));
		Phrase phrase = ItemTextParser.phrases("scythe of vitur").get(0);
		assertEquals(Integer.valueOf(22486), ItemTextParser.pick(phrase, onlyUncharged));
	}

	@Test
	public void theChargedItemWinsWhenBothExist()
	{
		List<Candidate> both = Arrays.asList(
			new Candidate("Scythe of vitur (uncharged)", 22486),
			new Candidate("Scythe of vitur", 22325));
		Phrase phrase = ItemTextParser.phrases("scythe of vitur").get(0);
		assertEquals(Integer.valueOf(22325), ItemTextParser.pick(phrase, both));
	}

	@Test
	public void noCandidatesIsNull()
	{
		Phrase phrase = ItemTextParser.phrases("twisted bow").get(0);
		assertNull(ItemTextParser.pick(phrase, new ArrayList<>()));
	}
}
