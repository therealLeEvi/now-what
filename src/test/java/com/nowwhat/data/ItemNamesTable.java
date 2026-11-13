package com.nowwhat.data;

import com.google.gson.Gson;
import com.nowwhat.model.Activity;
import com.nowwhat.model.ItemSource;
import com.nowwhat.model.TrainingMethod;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Builds {@code item-names.txt}, the table {@link ItemNameIndex} ships with.
 * <p>
 * Every gear, supply and shopping-list line in the bundled data is parsed the
 * way the panel parses it, and each phrase is resolved against the full set of
 * RuneLite's item constants ({@link ItemIdConstants}). The items those phrases
 * resolve to are the table - the only names the panel's fallback can ever be
 * asked for. Reflection is fine here: this runs at build time, in test code,
 * and never ships.
 * <p>
 * Run {@code ./gradlew itemnames} after changing the gear or supply text.
 */
public final class ItemNamesTable
{
	static final String SOURCE_PATH = "src/main/resources/com/nowwhat/item-names.txt";

	private ItemNamesTable()
	{
	}

	public static void main(String[] args) throws IOException
	{
		String body = render(required(), checkedIds().keySet());
		Files.write(Paths.get(SOURCE_PATH), body.getBytes(StandardCharsets.UTF_8));
		System.out.println("wrote " + required().size() + " items to " + SOURCE_PATH);
	}

	/**
	 * The id the game actually uses for a picked item.
	 * <p>
	 * RuneLite keeps some names on an id the game never used: ULTOR_RING is
	 * 25485, and the ring players wear is ULTOR_RING_28307. The parser takes
	 * the plain constant, so where item-sources.json - whose ids were checked
	 * against the wiki - gives the same name with a number on the end, that id
	 * wins. A different form of the item does not: the data lists the Eye of
	 * Ayak by its uncharged id because that is how it drops, but the gear
	 * picture should be the charged staff a player actually holds.
	 */
	static int realId(int picked, String name, Map<String, Integer> checked)
	{
		Integer real = checked.get(ItemNameIndex.flatten(name));
		if (real == null || real == picked)
		{
			return picked;
		}
		String realName = constantNames().get(real);
		String prefix = name + " ";
		if (realName == null || !realName.startsWith(prefix)
			|| realName.length() == prefix.length())
		{
			return picked;
		}
		for (char c : realName.substring(prefix.length()).toCharArray())
		{
			if (!Character.isDigit(c))
			{
				return picked;
			}
		}
		return real;
	}

	private static Map<Integer, String> constantNames;

	private static synchronized Map<Integer, String> constantNames()
	{
		if (constantNames == null)
		{
			constantNames = new HashMap<>();
			for (ItemTextParser.Candidate candidate : ItemIdConstants.all())
			{
				constantNames.putIfAbsent(candidate.getId(), candidate.getName());
			}
		}
		return constantNames;
	}

	/** Wiki-checked item ids from item-sources.json, by flattened display name. */
	static Map<String, Integer> checkedIds()
	{
		ItemSourceDatabase sources = new ItemSourceDatabase(new Gson());
		try
		{
			sources.load();
		}
		catch (Exception e)
		{
			throw new IllegalStateException("could not load item-sources.json", e);
		}
		Map<String, Integer> ids = new HashMap<>();
		for (ItemSource source : sources.getAllSources())
		{
			if (source.getGameItemId() != null && source.getDisplayName() != null)
			{
				ids.put(ItemNameIndex.flatten(source.getDisplayName()), source.getGameItemId());
			}
		}
		return ids;
	}

	/** Every line of text the panel turns into item pictures. */
	static List<String> kitLines()
	{
		Gson gson = new Gson();
		ActivityDatabase activities = new ActivityDatabase(gson);
		TrainingMethodDatabase methods = new TrainingMethodDatabase(gson);
		try
		{
			activities.load();
			methods.load(activities.getAllActivities());
		}
		catch (Exception e)
		{
			throw new IllegalStateException("could not load the bundled data", e);
		}

		List<String> lines = new ArrayList<>();
		for (Activity activity : activities.getAllActivities())
		{
			for (Activity.GearSetup setup : activity.getSetups())
			{
				lines.addAll(setup.getItems());
			}
			lines.addAll(activity.getSupplies());
		}
		for (TrainingMethod method : methods.getAllMethods())
		{
			lines.addAll(method.getGear());
			lines.addAll(method.getSupplies());
			for (TrainingMethod.Consumed consumed : method.getConsumes())
			{
				lines.add(consumed.getItem());
			}
		}
		return lines;
	}

	/** What the panel's fallback can be asked for, by id, resolved against every item constant. */
	static Map<Integer, String> required()
	{
		Map<String, Integer> checked = checkedIds();
		Map<Integer, String> needed = new TreeMap<>();
		for (String line : kitLines())
		{
			for (ItemTextParser.Phrase phrase : ItemTextParser.phrases(line))
			{
				List<ItemTextParser.Candidate> candidates =
					ItemIdConstants.search(ItemTextParser.searchTerm(phrase));
				Integer id = ItemTextParser.pick(phrase, candidates, checked.keySet());
				if (id == null)
				{
					continue;
				}
				for (ItemTextParser.Candidate candidate : candidates)
				{
					if (candidate.getId() == id)
					{
						needed.putIfAbsent(realId(id, candidate.getName(), checked), candidate.getName());
						break;
					}
				}
			}
		}
		return needed;
	}

	/** One item per line, "id TAB name", in id order so a regenerated table diffs cleanly. */
	static String render(Map<Integer, String> items, java.util.Set<String> known)
	{
		StringBuilder out = new StringBuilder();
		for (Map.Entry<Integer, String> item : items.entrySet())
		{
			out.append(item.getKey()).append('\t').append(item.getValue());
			if (known.contains(ItemNameIndex.flatten(item.getValue())))
			{
				out.append('\t').append(ItemNameIndex.KNOWN);
			}
			out.append('\n');
		}
		return out.toString();
	}
}
