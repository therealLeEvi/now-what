package com.nextstep.diag;

import com.google.gson.Gson;
import com.nextstep.data.ActivityDatabase;
import com.nextstep.data.ItemNameIndex;
import com.nextstep.data.ItemTextParser;
import com.nextstep.model.Activity;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.runelite.api.ItemID;

/**
 * How much of the bundled "what to bring" text turns into item pictures.
 * <p>
 * The icons themselves only exist in a running client, which makes this feature
 * awkward to judge from outside one: the question "does the gear show up" cannot
 * be answered by a screenshot of an offline panel, because offline there is no
 * item index at all. So this stands a proxy index in its place - RuneLite's
 * ItemID constants, which ship in the api jar - and reports, line by line, what
 * the parser would resolve.
 * <p>
 * The proxy is coarser than the real index: constant names lose the brackets
 * around variants ("MASORI_BODY_F" against "Masori body (f)") and dose numbers
 * run into the word ("PRAYER_POTION4"), so some of the misses listed here do
 * resolve in a client. It is a floor, not a measurement.
 */
public class IconCoverage
{
	public static void main(String[] args) throws Exception
	{
		List<ItemTextParser.Candidate> index = java.util.Collections.emptyList();
		System.out.println("fallback item index: " + ItemNameIndex.search("scythe").size()
			+ " names matching \"scythe\"");

		Gson gson = new Gson();
		ActivityDatabase ad = new ActivityDatabase(gson);
		ad.load();
		com.nextstep.data.TrainingMethodDatabase tm = new com.nextstep.data.TrainingMethodDatabase(gson);
		tm.load(ad.getAllActivities());

		int lines = 0;
		int resolved = 0;
		int picturesOnly = 0;
		List<String> misses = new ArrayList<>();

		// Activities first, then the training methods that carry a kit of their
		// own - both end up in the same box in the panel, so both are measured.
		Map<String, List<String>> sources = new LinkedHashMap<>();
		for (Activity activity : ad.getAllActivities())
		{
			List<String> entries = new ArrayList<>();
			for (Activity.GearSetup setup : activity.getSetups())
			{
				entries.addAll(setup.getItems());
			}
			entries.addAll(activity.getSupplies());
			if (!entries.isEmpty())
			{
				sources.put(activity.getName(), entries);
			}
		}
		for (com.nextstep.model.TrainingMethod method : tm.getAllMethods())
		{
			List<String> entries = new ArrayList<>();
			entries.addAll(method.getGear());
			entries.addAll(method.getSupplies());
			// The shopping list goes through the same parser and the same icon
			// row, so a consumable that resolves to nothing would quietly print
			// as words where the count was meant to sit under a picture.
			for (com.nextstep.model.TrainingMethod.Consumed consumed : method.getConsumes())
			{
				entries.add(consumed.getItem());
			}
			if (!entries.isEmpty())
			{
				sources.put(method.getSkill() + " - " + method.getName(), entries);
			}
		}

		for (Map.Entry<String, List<String>> source : sources.entrySet())
		{
			List<String> entries = source.getValue();
			if (entries.isEmpty())
			{
				continue;
			}

			System.out.println();
			System.out.println("== " + source.getKey());
			for (String entry : entries)
			{
				lines++;
				Map<Integer, String> found = new LinkedHashMap<>();
				Map<String, Boolean> optionMatched = new LinkedHashMap<>();
				for (ItemTextParser.Phrase phrase : ItemTextParser.phrases(entry))
				{
					List<ItemTextParser.Candidate> candidates = search(index,
						ItemTextParser.searchTerm(phrase));
					Integer id = ItemTextParser.pick(phrase, candidates);
					optionMatched.merge(phrase.getText(), id != null, (a, b) -> a || b);
					if (id == null)
					{
						continue;
					}
					for (ItemTextParser.Candidate candidate : candidates)
					{
						if (candidate.getId() == id)
						{
							found.putIfAbsent(id, candidate.getName());
							break;
						}
					}
				}
				if (found.isEmpty())
				{
					misses.add(source.getKey() + ": " + entry);
					System.out.println("   --  " + entry);
				}
				else
				{
					resolved++;
					// Whether the panel will drop the words for this line, which
					// is the same test NextStepPanel applies.
					boolean complete = !ItemTextParser.hasExplanation(entry)
						&& !optionMatched.containsValue(Boolean.FALSE);
					if (complete)
					{
						picturesOnly++;
					}
					System.out.println("   " + found.size() + (complete ? "x! " : "x  ") + entry
						+ "   ->  " + String.join(", ", found.values()));
				}
			}
		}

		System.out.println();
		System.out.println("lines with at least one icon: " + resolved + "/" + lines
			+ " (" + Math.round(100.0 * resolved / lines) + "%)");
		System.out.println("lines the pictures replace outright: " + picturesOnly
			+ "/" + lines + " (" + Math.round(100.0 * picturesOnly / lines) + "%)");
		System.out.println("lines with none: " + misses.size());
	}

	/**
	 * The same fallback index the panel uses when RuneLite's tradeable-only
	 * search comes up empty, so this measures the real thing rather than a
	 * stand-in. In a client the tradeable half additionally resolves against
	 * proper display names, so these numbers are a floor.
	 */
	private static List<ItemTextParser.Candidate> search(List<ItemTextParser.Candidate> unused,
		String term)
	{
		return ItemNameIndex.search(term);
	}
}
