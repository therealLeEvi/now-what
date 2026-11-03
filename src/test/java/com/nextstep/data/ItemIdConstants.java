package com.nextstep.data;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * A name-to-id index for every item in the game, built from RuneLite's
 * {@code ItemID} constants. <b>Test and build-time only.</b>
 * <p>
 * The plugin itself ships {@link ItemNameIndex}, a table generated from this by
 * {@link ItemNamesTable}: reading these constants by reflection at runtime is
 * something Plugin Hub plugins may not do. Here it checks that item ids in the
 * data name the items they claim to, and builds that table.
 * <p>
 * This exists because {@code ItemManager.search} only covers <b>tradeable</b>
 * items: it walks the Grand Exchange price map, so nothing untradeable is in
 * it. Half the gear a boss guide names is untradeable - void, barrows gloves,
 * an infernal cape, a slayer helmet - and a charged weapon is the sharpest
 * case of all, because the charged scythe of vitur cannot be traded and only
 * "Scythe of vitur (uncharged)" appears in the price list. A player looking at
 * a best-in-slot setup with no scythe in it reasonably concludes the pictures
 * are broken.
 * <p>
 * The constants are read by reflection, once, with no client and no network, so
 * this also works in tests and in the offline diagnostics. The cost is that a
 * constant name is not a display name: it has lost apostrophes, hyphens and
 * brackets ({@code MASORI_BODY_F}, {@code PRAYER_POTION4}). Those are put back
 * where the pattern is unambiguous, and {@link ItemTextParser} matches
 * leniently enough to absorb the rest. Because of that, this is the
 * <em>fallback</em>: a real display name from the item index is always
 * preferred when there is one.
 */
public final class ItemIdConstants
{
	private static final String ITEM_ID_CLASS = "net.runelite.api.ItemID";

	private static List<ItemTextParser.Candidate> index;

	private ItemIdConstants()
	{
	}

	/**
	 * Every item constant, name and id. Used to check that the ids written into
	 * item-sources.json point at the items they say they do.
	 */
	public static List<ItemTextParser.Candidate> all()
	{
		return Collections.unmodifiableList(index());
	}

	/**
	 * Every item whose name contains {@code term}, compared without the
	 * punctuation these names cannot carry.
	 */
	public static List<ItemTextParser.Candidate> search(String term)
	{
		List<ItemTextParser.Candidate> all = index();
		List<ItemTextParser.Candidate> hits = new ArrayList<>();
		if (term == null || term.isEmpty())
		{
			return hits;
		}
		String needle = flatten(term);
		for (ItemTextParser.Candidate candidate : all)
		{
			if (flatten(candidate.getName()).contains(needle))
			{
				hits.add(candidate);
			}
		}
		return hits;
	}

	/** Apostrophes and hyphens are absent from constant names; ignore them on both sides. */
	private static String flatten(String text)
	{
		return text.toLowerCase(Locale.ROOT)
			.replace("'", "")
			.replace("\u2019", "")
			.replace("-", "")
			.replace("+", "");
	}

	private static synchronized List<ItemTextParser.Candidate> index()
	{
		if (index != null)
		{
			return index;
		}

		List<ItemTextParser.Candidate> built = new ArrayList<>();
		try
		{
			Class<?> itemId = Class.forName(ITEM_ID_CLASS);
			for (Field field : itemId.getFields())
			{
				if (field.getType() != int.class || !Modifier.isStatic(field.getModifiers()))
				{
					continue;
				}
				built.add(new ItemTextParser.Candidate(displayName(field.getName()),
					field.getInt(null)));
			}
		}
		catch (ReflectiveOperationException | LinkageError noItemIdOnTheClasspath)
		{
			// Leaves the index empty: every lookup then simply falls through to
			// whatever the real item index offered, which is where it started.
			built = Collections.emptyList();
		}
		index = built;
		return index;
	}

	/**
	 * A constant name read back as something like the item's name.
	 * <p>
	 * {@code MASORI_BODY_F} is "masori body (f)" and {@code PRAYER_POTION4} is
	 * "prayer potion(4)": a trailing one or two letter segment is a variant
	 * marker in every real item name that has one, and a digit run glued to the
	 * last word is a dose.
	 */
	static String displayName(String constant)
	{
		String[] parts = constant.toLowerCase(Locale.ROOT).split("_");
		StringBuilder name = new StringBuilder();
		for (int i = 0; i < parts.length; i++)
		{
			if (name.length() > 0)
			{
				name.append(' ');
			}
			if (i > 0 && i == parts.length - 1 && parts[i].length() <= 2
				&& !parts[i].isEmpty() && !Character.isDigit(parts[i].charAt(0)))
			{
				name.append('(').append(parts[i]).append(')');
			}
			else
			{
				name.append(parts[i]);
			}
		}

		// "potion4" -> "potion(4)"
		String built = name.toString();
		int at = built.length();
		while (at > 0 && Character.isDigit(built.charAt(at - 1)))
		{
			at--;
		}
		if (at > 0 && at < built.length() && Character.isLetter(built.charAt(at - 1)))
		{
			built = built.substring(0, at) + "(" + built.substring(at) + ")";
		}
		return built;
	}
}
