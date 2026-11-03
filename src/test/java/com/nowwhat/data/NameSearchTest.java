package com.nowwhat.data;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The goal pickers are only better than the dropdowns they replaced if what the
 * player types lands on what they meant. These are the cases that decide that.
 */
public class NameSearchTest
{
	private static final Function<String, String> SELF = s -> s;

	private static final List<String> ITEMS = Arrays.asList(
		"Abyssal whip",
		"Dragon boots",
		"Dragon claws",
		"Dragon hunter lance",
		"Dragon warhammer",
		"Seers' ring",
		"Toxic blowpipe",
		"Twisted bow",
		"Zamorak brew (4)");

	private static List<String> search(String query)
	{
		return NameSearch.rank(query, ITEMS, SELF, 0);
	}

	/**
	 * An untouched box is the whole list being browsed, not a search being
	 * ranked, so it keeps the order it was handed - alphabetical here. Ranking
	 * it by length instead opened the picker on whichever name was shortest,
	 * which reads as a bug.
	 */
	@Test
	public void aBlankQueryKeepsTheGivenOrder()
	{
		assertEquals(ITEMS, search(""));
		assertEquals(ITEMS, search("   "));
		assertEquals(ITEMS, NameSearch.rank(null, ITEMS, SELF, 0));
	}

	/**
	 * The three ranks, in order: the query prefixes the whole name, the query
	 * starts one of its words, the query is merely in there somewhere. A
	 * purpose-built list, because the real one has no name with "bow" buried
	 * mid-word to be outranked.
	 */
	@Test
	public void prefixBeatsWordStartBeatsAnywhere()
	{
		List<String> names = Arrays.asList("Elbow guard", "Twisted bow", "Bow string");
		assertEquals(Arrays.asList("Bow string", "Twisted bow", "Elbow guard"),
			NameSearch.rank("bow", names, SELF, 0));
	}

	@Test
	public void shorterNamesWinWithinTheSameRank()
	{
		assertEquals(
			Arrays.asList("Dragon boots", "Dragon claws", "Dragon warhammer",
				"Dragon hunter lance"),
			search("dragon"));
	}

	/** Every term has to land somewhere, which is what makes "drag cl" precise. */
	@Test
	public void everyTermMustMatch()
	{
		assertEquals(Arrays.asList("Dragon claws"), search("drag cl"));
		assertEquals(Arrays.asList("Dragon hunter lance"), search("dragon lance"));
		assertTrue(search("dragon scythe").isEmpty());
	}

	/** Nobody types the apostrophe, and several of these names have lost theirs. */
	@Test
	public void punctuationIsIgnoredOnBothSides()
	{
		assertEquals(Arrays.asList("Seers' ring"), search("seers ring"));
		assertEquals(Arrays.asList("Seers' ring"), search("seers' ring"));
		assertEquals(Arrays.asList("Zamorak brew (4)"), search("zamorak brew 4"));
	}

	@Test
	public void theLimitCaps()
	{
		assertEquals(2, NameSearch.rank("dragon", ITEMS, SELF, 2).size());
		assertEquals(4, NameSearch.rank("dragon", ITEMS, SELF, 0).size());
	}

	@Test
	public void nothingMatchingComesBackEmpty()
	{
		assertTrue(search("elysian").isEmpty());
	}

	/** A null name must be skipped rather than thrown over. */
	@Test
	public void entriesWithNoNameAreIgnored()
	{
		List<String> withNull = Arrays.asList("Dragon claws", null, "Dragon boots");
		assertEquals(2, NameSearch.rank("dragon", withNull, SELF, 0).size());
		assertEquals(2, NameSearch.rank("", withNull, SELF, 0).size());
	}
}
