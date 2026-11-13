package com.nowwhat;

import com.nowwhat.data.ItemNameIndex;
import com.nowwhat.data.ItemTextParser;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * RuneLite's item search only knows tradeable items, so for an untradeable one
 * it offers a tradeable relative: the avernic defender's hilt, the dragon
 * defender's ornament kit, the uncharged scythe. Those are what the panel used
 * to picture. The bundled table, which knows the real item, now decides.
 */
public class IconPickTest
{
	private static int pick(String text)
	{
		ItemTextParser.Phrase phrase = ItemTextParser.phrases(text).get(0);
		Integer id = ItemTextParser.pick(phrase, ItemNameIndex.search(ItemTextParser.searchTerm(phrase)));
		return id == null ? -1 : id;
	}

	@Test
	public void untradeableGearIsTheItemItselfNotItsTradeableRelative()
	{
		assertEquals("Avernic defender, not the hilt", 22322, pick("Avernic defender"));
		assertEquals("Dragon defender, not the ornament kit", 12954, pick("Dragon defender"));
		assertEquals("charged Scythe of vitur", 22325, pick("Scythe of vitur"));
		assertEquals("Rune pouch, not its note", 12791, pick("Rune pouch"));
		assertEquals("Book of darkness, not the page set", 12612, pick("Book of darkness"));
	}

	/** Every item a line pictures, in order. */
	private static List<Integer> pickAll(String text)
	{
		List<Integer> ids = new java.util.ArrayList<>();
		for (ItemTextParser.Phrase phrase : ItemTextParser.phrases(text))
		{
			Integer id = ItemTextParser.pick(phrase,
				ItemNameIndex.search(ItemTextParser.searchTerm(phrase)));
			ids.add(id == null ? -1 : id);
		}
		return ids;
	}

	@Test
	public void aSetNameForBodyAndLegsIsTheArmourAGuideMeans()
	{
		// Shortest name used to win: the Bandos robe top and Bandos chaps.
		assertEquals(java.util.Arrays.asList(11832, 11834), pickAll("Body/legs: Bandos"));
	}

	@Test
	public void theLegsOfARobeSetAreNotItsTop()
	{
		// "robe" is a legs word too, and "ancestral robe top" used to fill both boxes.
		assertEquals(java.util.Arrays.asList(21018, 21021, 21024),
			pickAll("Head/body/legs: ancestral"));
	}

	@Test
	public void aNameWrittenOutIsNotSwappedForAKnownItem()
	{
		assertEquals(1387, pick("Staff of fire"));
	}

	@Test
	public void tooltipsUseTheGamesSpellingWhereItIsKnown()
	{
		List<ItemTextParser.Candidate> table = Collections.singletonList(
			new ItemTextParser.Candidate("tumekens shadow", 27275));
		List<ItemTextParser.Candidate> tradeable = Arrays.asList(
			new ItemTextParser.Candidate("Tumeken's shadow (uncharged)", 27277),
			new ItemTextParser.Candidate("Tumeken's shadow", 99999));
		assertEquals("Tumeken's shadow", GearView.displayName(27275, table, tradeable));
		assertEquals("Tumekens shadow",
			GearView.displayName(27275, table, Collections.emptyList()));
	}

	/** The words each option of a line is looked up by. */
	private static List<String> lookups(String text)
	{
		List<String> out = new java.util.ArrayList<>();
		for (ItemTextParser.Phrase phrase : ItemTextParser.phrases(text))
		{
			out.add(String.join(" ", phrase.getWords()));
		}
		return out;
	}

	@Test
	public void aSetNameCarriesThroughAListButNotPastAnOr()
	{
		assertEquals(java.util.Arrays.asList("super attack", "super strength", "super defence"),
			lookups("Super attack, strength and defence"));
		assertEquals(java.util.Arrays.asList("oathplate helm", "oathplate chest", "oathplate legs",
			"torva"), lookups("Oathplate helm, chest and legs or Torva"));
		assertEquals(java.util.Arrays.asList("ultor ring", "lightbearer"),
			lookups("Ultor ring or lightbearer"));
	}

	@Test
	public void aListCanShareItsLastWord()
	{
		assertEquals(java.util.Arrays.asList("dust runes", "chaos runes", "law runes"),
			lookups("dust, chaos and law runes"));
		// Fish are not a kind of thing a list shares: these stay three fish.
		assertEquals(java.util.Arrays.asList("anglerfish", "sharks", "manta rays"),
			lookups("anglerfish, sharks or manta rays"));
	}
}
