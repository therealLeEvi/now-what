package com.nextstep.data;

import java.util.List;
import java.util.Locale;

/**
 * Picks another plugin's sidebar button out of the ones RuneLite is showing, by
 * the tooltip under it.
 * <p>
 * A plugin's sidebar button is labelled with its own tooltip, which is normally
 * the name in its {@code PluginDescriptor} but is not required to be: it can be
 * cased differently, or carry a suffix. So the match is tried exact first, then
 * ignoring case, and only then as a prefix - in that order, because a loose
 * match that wins over an exact one is how you end up opening the wrong panel.
 * <p>
 * Pure, and tested, for the same reason {@link PluginLookup} is: the lookup it
 * feeds reaches into the client by reflection, which cannot be exercised in a
 * test, so everything that can be decided without the client is decided here.
 */
public final class NavButtonLookup
{
	private NavButtonLookup()
	{
	}

	/**
	 * The index of the button for {@code wanted}, or -1 when none of them is it.
	 *
	 * @param tooltips the tooltip of each button, in the order they are held;
	 *                 a null entry is a button with no tooltip and never matches
	 */
	public static int match(String wanted, List<String> tooltips)
	{
		if (wanted == null || tooltips == null)
		{
			return -1;
		}

		for (int i = 0; i < tooltips.size(); i++)
		{
			if (wanted.equals(tooltips.get(i)))
			{
				return i;
			}
		}

		String lower = wanted.toLowerCase(Locale.ROOT);
		for (int i = 0; i < tooltips.size(); i++)
		{
			String tooltip = tooltips.get(i);
			if (tooltip != null && lower.equals(tooltip.toLowerCase(Locale.ROOT)))
			{
				return i;
			}
		}

		for (int i = 0; i < tooltips.size(); i++)
		{
			String tooltip = tooltips.get(i);
			if (tooltip != null && tooltip.toLowerCase(Locale.ROOT).startsWith(lower))
			{
				return i;
			}
		}
		return -1;
	}
}
