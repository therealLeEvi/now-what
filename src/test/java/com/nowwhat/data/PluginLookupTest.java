package com.nowwhat.data;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

/**
 * The lookup that decides whether to say "Quest Helper has the walkthrough" or
 * "install it from the Plugin Hub". Getting it wrong is invisible until someone
 * notices the panel claiming a plugin they have never installed.
 */
public class PluginLookupTest
{
	private static final List<String> NAMES = Arrays.asList(
		"Ground Items", "Quest Helper", null, "Zulrah Helper", "Menu Entry Swapper");
	private static final List<String> CLASSES = Arrays.asList(
		"net.runelite.client.plugins.grounditems.GroundItemsPlugin",
		"com.questhelper.QuestHelperPlugin",
		"com.github.someone.zulrahhelper.ZulrahHelperPlugin",
		"com.example.forked.ZulrahPlugin",
		"net.runelite.client.plugins.menuentryswapper.MenuEntrySwapperPlugin");

	@Test
	public void findsAPluginByItsDescriptorName()
	{
		assertEquals(1, PluginLookup.match("Quest Helper", NAMES, CLASSES));
	}

	@Test
	public void exactNameBeatsAnEarlierLooseMatch()
	{
		// Index 2 has no descriptor name but a class name containing
		// "zulrahhelper"; index 3 is the real thing and must win.
		assertEquals(3, PluginLookup.match("Zulrah Helper", NAMES, CLASSES));
	}

	@Test
	public void aRenamedForkIsStillFoundByItsClassName()
	{
		List<String> names = Arrays.asList("Quest Helper Deluxe");
		List<String> classes = Arrays.asList("com.questhelper.QuestHelperPlugin");
		assertEquals(0, PluginLookup.match("Quest Helper", names, classes));
	}

	@Test
	public void askingForOnePluginNeverAnswersWithAnother()
	{
		// The bug this test exists for: a hardcoded com.questhelper check in the
		// fallback made every lookup succeed as long as Quest Helper was
		// installed, so the panel told players Zulrah Helper was ready when it
		// was not installed at all.
		List<String> names = Arrays.asList("Quest Helper");
		List<String> classes = Arrays.asList("com.questhelper.QuestHelperPlugin");
		assertEquals(-1, PluginLookup.match("Zulrah Helper", names, classes));
		assertEquals(-1, PluginLookup.match("Tombs of Amascut", names, classes));
	}

	@Test
	public void nothingInstalledIsNotAMatch()
	{
		assertEquals(-1, PluginLookup.match("Quest Helper",
			Collections.emptyList(), Collections.emptyList()));
	}

	@Test
	public void missingNamesAndClassesAreSurvivable()
	{
		assertEquals(-1, PluginLookup.match(null, NAMES, CLASSES));
		assertEquals(-1, PluginLookup.match("Quest Helper", null, null));
		assertEquals(-1, PluginLookup.match("", NAMES, CLASSES));
	}
}
