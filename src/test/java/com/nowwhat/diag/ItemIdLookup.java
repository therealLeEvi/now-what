package com.nowwhat.diag;

import com.nowwhat.data.ItemIdConstants;
import com.nowwhat.data.ItemTextParser;
import java.util.List;

/**
 * Looks an item id up by name, out of RuneLite's own {@code ItemID} constants.
 * <p>
 * Every {@code gameItemId} in item-sources.json is supposed to have been read
 * out of {@code ItemID} rather than recalled, because two that were typed from
 * memory during the first pass were wrong and a wrong id is invisible until a
 * player sees the wrong picture. This is the reading. Adding an item to
 * {@code tools/gen_data.py} starts here:
 *
 * <pre>
 * ./gradlew itemid --args="osmumtens fang|elidinis ward|lightbearer"
 * </pre>
 *
 * Terms are separated by {@code |} because item names have spaces in them.
 * Each one prints every constant containing it, closest first, so an ambiguous
 * name ("masori body" against its (f) variant) is a choice you make looking at
 * the list rather than a guess.
 */
public final class ItemIdLookup
{
	private ItemIdLookup()
	{
	}

	public static void main(String[] args)
	{
		if (args.length == 0)
		{
			System.out.println("usage: --args=\"name|another name\"");
			return;
		}

		for (String term : String.join(" ", args).split("\\|"))
		{
			String needle = term.trim();
			if (needle.isEmpty())
			{
				continue;
			}
			System.out.println();
			System.out.println("== " + needle);
			List<ItemTextParser.Candidate> hits = ItemIdConstants.search(needle);
			if (hits.isEmpty())
			{
				System.out.println("   (nothing)");
				continue;
			}
			// Shortest first: the plain item before every charged, ornamented
			// and broken variant of it, which is nearly always the one wanted.
			hits.sort((a, b) -> a.getName().length() != b.getName().length()
				? Integer.compare(a.getName().length(), b.getName().length())
				: a.getName().compareTo(b.getName()));
			int shown = 0;
			for (ItemTextParser.Candidate hit : hits)
			{
				System.out.printf("   %-44s %d%n", hit.getName(), hit.getId());
				if (++shown >= 12)
				{
					System.out.println("   ... and " + (hits.size() - shown) + " more");
					break;
				}
			}
		}
	}
}
