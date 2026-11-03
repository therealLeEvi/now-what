package com.nextstep.data;

import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * The fallback index exists for the items RuneLite's own search cannot see -
 * everything untradeable - so what matters is that the names it rebuilds from
 * constants are close enough to match against.
 */
public class ItemIdConstantsTest
{
	@Test
	public void variantMarkersComeBackAsBrackets()
	{
		assertEquals("masori body (f)", ItemIdConstants.displayName("MASORI_BODY_F"));
		assertEquals("slayer helmet (i)", ItemIdConstants.displayName("SLAYER_HELMET_I"));
	}

	@Test
	public void aDoseGluedToTheWordIsSeparated()
	{
		assertEquals("prayer potion(4)", ItemIdConstants.displayName("PRAYER_POTION4"));
		assertEquals("super restore(1)", ItemIdConstants.displayName("SUPER_RESTORE1"));
	}

	@Test
	public void plainNamesAreLeftAlone()
	{
		assertEquals("scythe of vitur", ItemIdConstants.displayName("SCYTHE_OF_VITUR"));
		assertEquals("twisted bow", ItemIdConstants.displayName("TWISTED_BOW"));
	}

	@Test
	public void theUntradeablesRuneLiteCannotSearchAreInHere()
	{
		// Each of these is absent from the Grand Exchange price list, which is
		// the whole reason this index exists.
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
		assertTrue(ItemIdConstants.search("").isEmpty());
	}

	private boolean holdsSomethingNamed(String name)
	{
		List<ItemTextParser.Candidate> hits = ItemIdConstants.search(name);
		for (ItemTextParser.Candidate hit : hits)
		{
			if (hit.getName().contains(name))
			{
				return true;
			}
		}
		return false;
	}
}
