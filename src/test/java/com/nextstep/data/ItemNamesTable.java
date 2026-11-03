package com.nextstep.data;

import com.google.gson.Gson;
import com.nextstep.model.Activity;
import com.nextstep.model.TrainingMethod;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
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
	static final String SOURCE_PATH = "src/main/resources/com/nextstep/item-names.txt";

	private ItemNamesTable()
	{
	}

	public static void main(String[] args) throws IOException
	{
		String body = render(required());
		Files.write(Paths.get(SOURCE_PATH), body.getBytes(StandardCharsets.UTF_8));
		System.out.println("wrote " + required().size() + " items to " + SOURCE_PATH);
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
		Map<Integer, String> needed = new TreeMap<>();
		for (String line : kitLines())
		{
			for (ItemTextParser.Phrase phrase : ItemTextParser.phrases(line))
			{
				List<ItemTextParser.Candidate> candidates =
					ItemIdConstants.search(ItemTextParser.searchTerm(phrase));
				Integer id = ItemTextParser.pick(phrase, candidates);
				if (id == null)
				{
					continue;
				}
				for (ItemTextParser.Candidate candidate : candidates)
				{
					if (candidate.getId() == id)
					{
						needed.putIfAbsent(id, candidate.getName());
						break;
					}
				}
			}
		}
		return needed;
	}

	/** One item per line, "id TAB name", in id order so a regenerated table diffs cleanly. */
	static String render(Map<Integer, String> items)
	{
		StringBuilder out = new StringBuilder();
		for (Map.Entry<Integer, String> item : items.entrySet())
		{
			out.append(item.getKey()).append('\t').append(item.getValue()).append('\n');
		}
		return out.toString();
	}
}
