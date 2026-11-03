package com.nowwhat;

import com.nowwhat.model.Activity;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * A birdhouse run written as one paragraph could not be read in the sidebar,
 * so descriptions carry line breaks, headings and lists. Everywhere that shows
 * the text inline still has to get one clean sentence out of that.
 */
public class HowToLayoutTest
{
	private static final String LAID_OUT = "Four spots on Fossil Island.\nRoute:\n* Digsite pendant\n* Magic mushtree\nDone.";

	@Test
	public void theInlineFormJoinsAListWithCommas()
	{
		Activity activity = new Activity("x", "X", Activity.Category.SKILLING, Collections.emptyList())
			.withDescription(LAID_OUT);

		assertEquals("Four spots on Fossil Island. Route: Digsite pendant, Magic mushtree. Done.",
			activity.getDescription());
		assertEquals(LAID_OUT, activity.getHowTo());
	}

	@Test
	public void longProseIsBrokenBetweenSentencesNeverInside()
	{
		String a = "Zulrah keeps diving and coming back up in a new spot and a new form, and each form attacks differently.";
		String b = "Its phases follow one of four fixed rotations, so learn them.";
		String c = "The loot appears under you.";

		assertEquals(Arrays.asList(a + " " + b, c), NowWhatPanel.paragraphs(a + " " + b + " " + c));
		assertEquals(Collections.singletonList("Short. Text."), NowWhatPanel.paragraphs("Short. Text."));
	}
}
