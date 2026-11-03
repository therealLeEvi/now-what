package com.nextstep.data;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Ranked name matching for the goal pickers.
 * <p>
 * A dropdown is the wrong control once a list stops being short: it cannot be
 * typed at, it has no idea what you meant, and it gets worse with every entry
 * added - which is the opposite of what a content database should do as it
 * grows. A search box gets better.
 * <p>
 * The ranking is deliberately plain, because a player typing "drag cl" has a
 * precise item in mind and wants it first, not a cleverer guess:
 * <ul>
 *   <li>every whitespace-separated term must appear somewhere in the name, so
 *       "drag cl" finds "Dragon claws" and "bandos tas" finds "Bandos
 *       tassets";</li>
 *   <li>a name the query prefixes outright comes first, then one where a word
 *       starts with the first term, then the rest;</li>
 *   <li>within a rank, shorter names first - "Dragon boots" before "Dragon
 *       hunter lance" for "dragon" - then alphabetically, so the order is
 *       stable rather than dependent on file order.</li>
 * </ul>
 * Punctuation is ignored on both sides for the same reason {@link ItemNameIndex}
 * ignores it: nobody types the apostrophe in "Seers' ring", and several of these
 * names have lost theirs already.
 */
public final class NameSearch
{
	private NameSearch()
	{
	}

	/**
	 * The entries matching {@code query}, best first.
	 *
	 * @param query  what the player typed; blank returns the entries unranked
	 *               and in their given order, so an untouched search box shows
	 *               the whole list exactly as a dropdown would have
	 * @param limit  at most this many results, or 0 for all of them
	 */
	public static <T> List<T> rank(String query, List<T> entries, Function<T, String> name,
		int limit)
	{
		List<String> terms = terms(query);
		if (terms.isEmpty())
		{
			// Nothing typed yet, so this is the whole list being browsed rather
			// than a search being ranked. It keeps the order it was given -
			// alphabetical, for the pickers - because the length tie-break below
			// would otherwise open the box on whichever name happens to be
			// shortest, which looks like a bug and is no use to anyone.
			List<T> everything = new ArrayList<>();
			for (T entry : entries)
			{
				if (limit > 0 && everything.size() >= limit)
				{
					break;
				}
				if (name.apply(entry) != null)
				{
					everything.add(entry);
				}
			}
			return everything;
		}

		List<Ranked<T>> hits = new ArrayList<>();
		for (T entry : entries)
		{
			String label = name.apply(entry);
			if (label == null)
			{
				continue;
			}
			int rank = rank(terms, flatten(label));
			if (rank >= 0)
			{
				hits.add(new Ranked<>(entry, label, rank));
			}
		}

		hits.sort(Comparator.<Ranked<T>>comparingInt(h -> h.rank)
			.thenComparingInt(h -> h.label.length())
			.thenComparing(h -> h.label, String.CASE_INSENSITIVE_ORDER));

		List<T> out = new ArrayList<>();
		for (Ranked<T> hit : hits)
		{
			if (limit > 0 && out.size() >= limit)
			{
				break;
			}
			out.add(hit.entry);
		}
		return out;
	}

	/**
	 * @return 0 for a prefix of the whole name, 1 for a word start, 2 for a
	 *     match anywhere, or -1 for no match at all
	 */
	private static int rank(List<String> terms, String flattened)
	{
		for (String term : terms)
		{
			if (!flattened.contains(term))
			{
				return -1;
			}
		}

		String first = terms.get(0);
		if (flattened.startsWith(first))
		{
			return 0;
		}
		// A word start, which is what makes "claws" rank above a name that
		// merely contains the letters mid-word.
		for (int i = 1; i < flattened.length(); i++)
		{
			if (flattened.charAt(i - 1) == ' ' && flattened.startsWith(first, i))
			{
				return 1;
			}
		}
		return 2;
	}

	private static List<String> terms(String query)
	{
		List<String> terms = new ArrayList<>();
		if (query == null)
		{
			return terms;
		}
		for (String raw : flatten(query).split(" "))
		{
			if (!raw.isEmpty())
			{
				terms.add(raw);
			}
		}
		return terms;
	}

	/** Lowercased, with the punctuation nobody types removed and runs of space collapsed. */
	private static String flatten(String text)
	{
		StringBuilder out = new StringBuilder(text.length());
		String lower = text.toLowerCase(Locale.ROOT);
		for (int i = 0; i < lower.length(); i++)
		{
			char c = lower.charAt(i);
			if (c == '\'' || c == '\u2019' || c == '-' || c == '+' || c == '('
				|| c == ')' || c == '.' || c == ',')
			{
				continue;
			}
			if (Character.isWhitespace(c) || c == '_' || c == '/')
			{
				if (out.length() > 0 && out.charAt(out.length() - 1) != ' ')
				{
					out.append(' ');
				}
				continue;
			}
			out.append(c);
		}
		return out.toString().trim();
	}

	private static final class Ranked<T>
	{
		private final T entry;
		private final String label;
		private final int rank;

		private Ranked(T entry, String label, int rank)
		{
			this.entry = entry;
			this.label = label;
			this.rank = rank;
		}
	}
}
