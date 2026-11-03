package com.nextstep.data;

import java.util.List;
import java.util.Locale;

/**
 * Finds another RuneLite plugin by the name in its {@code PluginDescriptor},
 * without compiling against it.
 * <p>
 * Name-based on purpose: Next Step points at Quest Helper and at boss helpers
 * rather than reimplementing them, and that pointer has to work the same
 * whether or not the other plugin is installed. A build dependency would make
 * this plugin fail to load without them.
 * <p>
 * The matching lives here, apart from RuneLite's types, because it is the part
 * that can be wrong in a way nobody notices. It already was: the class-name
 * fallback used to test for a hardcoded {@code com.questhelper} package, so
 * <em>every</em> lookup answered "yes, installed" on the strength of Quest
 * Helper being there - asking whether Zulrah Helper was installed said yes. It
 * was caught by chance, in conversation. Now it is a pure function over two
 * lists of strings, and there are tests.
 */
public final class PluginLookup
{
	private PluginLookup()
	{
	}

	/**
	 * The index of the plugin that answers to {@code descriptorName}, or -1.
	 *
	 * @param descriptorName  the name as its PluginDescriptor spells it, e.g. "Quest Helper"
	 * @param descriptorNames every installed plugin's descriptor name, null where it has none
	 * @param classNames      the same plugins' fully qualified class names, same order
	 */
	public static int match(String descriptorName, List<String> descriptorNames,
		List<String> classNames)
	{
		if (descriptorName == null || descriptorNames == null || classNames == null)
		{
			return -1;
		}

		// An exact descriptor match wins, and is checked across every plugin
		// before the loose fallback gets a look in - otherwise a fuzzy hit on
		// whichever plugin happens to be iterated first beats the real match
		// further down the list.
		for (int i = 0; i < descriptorNames.size(); i++)
		{
			if (descriptorName.equals(descriptorNames.get(i)))
			{
				return i;
			}
		}

		// Plugins get forked and renamed, and a fork still does the job, so fall
		// back to the class name. The needle must be derived from the name being
		// searched for and nothing else.
		String needle = descriptorName.replace(" ", "").toLowerCase(Locale.ROOT);
		if (needle.isEmpty())
		{
			return -1;
		}
		for (int i = 0; i < classNames.size() && i < descriptorNames.size(); i++)
		{
			String className = classNames.get(i);
			if (className != null && className.toLowerCase(Locale.ROOT).contains(needle))
			{
				return i;
			}
		}
		return -1;
	}
}
