package com.nextstep.data;

import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class NavButtonLookupTest
{
	@Test
	public void findsTheExactTooltip()
	{
		assertEquals(1, NavButtonLookup.match("Quest Helper",
			Arrays.asList("Next Step", "Quest Helper", "Screenshot")));
	}

	@Test
	public void ignoresCaseWhenNothingMatchesExactly()
	{
		assertEquals(0, NavButtonLookup.match("Quest Helper",
			Collections.singletonList("quest helper")));
	}

	@Test
	public void prefersAnExactMatchOverALooserOne()
	{
		assertEquals(2, NavButtonLookup.match("Quest Helper",
			Arrays.asList("quest helper", "Quest Helper beta", "Quest Helper")));
	}

	@Test
	public void acceptsATooltipThatAddsASuffix()
	{
		assertEquals(0, NavButtonLookup.match("Quest Helper",
			Collections.singletonList("Quest Helper v5")));
	}

	@Test
	public void doesNotMatchAButtonWithNoTooltip()
	{
		assertEquals(-1, NavButtonLookup.match("Quest Helper",
			Arrays.asList(null, "Screenshot")));
	}

	@Test
	public void returnsNoMatchWhenThePluginHasNoButton()
	{
		// A plugin can be installed, enabled, and have no sidebar panel at all -
		// which is the case the caller has to fall back on rather than opening
		// whatever happens to be first.
		assertEquals(-1, NavButtonLookup.match("Quest Helper",
			Arrays.asList("Next Step", "Screenshot")));
	}

	@Test
	public void survivesMissingInput()
	{
		assertEquals(-1, NavButtonLookup.match(null, Collections.singletonList("Quest Helper")));
		assertEquals(-1, NavButtonLookup.match("Quest Helper", null));
	}
}
