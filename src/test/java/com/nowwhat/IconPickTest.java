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

	@Test
	public void tooltipsUseTheGamesSpellingWhereItIsKnown()
	{
		List<ItemTextParser.Candidate> table = Collections.singletonList(
			new ItemTextParser.Candidate("tumekens shadow", 27275));
		List<ItemTextParser.Candidate> tradeable = Arrays.asList(
			new ItemTextParser.Candidate("Tumeken's shadow (uncharged)", 27277),
			new ItemTextParser.Candidate("Tumeken's shadow", 99999));
		assertEquals("Tumeken's shadow", NowWhatPanel.displayName(27275, table, tradeable));
		assertEquals("Tumekens shadow",
			NowWhatPanel.displayName(27275, table, Collections.emptyList()));
	}
}
