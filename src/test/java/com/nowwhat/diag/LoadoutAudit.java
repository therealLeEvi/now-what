package com.nowwhat.diag;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.nowwhat.data.ActivityDatabase;
import com.nowwhat.data.ItemNameIndex;
import com.nowwhat.data.ItemTextParser;
import com.nowwhat.model.Activity;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Which slots each gear setup leaves empty, and which of its words never became
 * an item - the two reasons a loadout looks unfinished in the panel.
 * <p>
 * Slots come from RuneLite's own item stats, the file the client downloads at
 * startup. It is not bundled, so pass a saved copy:
 * {@code ./gradlew loadoutaudit -Pstats=path/to/stats.ids.min.json}
 * (from https://static.runelite.net/item/stats.ids.min.json).
 * <p>
 * Only the bundled name table is used to match words, which is what the panel
 * has for untradeable items; it also tries RuneLite's tradeable search, so a
 * tradeable item reported unmatched here can still be pictured in the client.
 */
public final class LoadoutAudit
{
	private static final Map<Integer, String> SLOTS = new LinkedHashMap<>();

	static
	{
		SLOTS.put(0, "head");
		SLOTS.put(1, "cape");
		SLOTS.put(2, "neck");
		SLOTS.put(3, "weapon");
		SLOTS.put(4, "body");
		SLOTS.put(5, "shield");
		SLOTS.put(7, "legs");
		SLOTS.put(9, "hands");
		SLOTS.put(10, "feet");
		SLOTS.put(12, "ring");
		SLOTS.put(13, "ammo");
	}

	private LoadoutAudit()
	{
	}

	public static void main(String[] args) throws IOException
	{
		if (args.length < 1)
		{
			throw new IllegalArgumentException("pass the path of a saved stats.ids.min.json");
		}
		Map<Integer, Integer> slotOf = new TreeMap<>();
		Set<Integer> twoHanded = new LinkedHashSet<>();
		try (Reader reader = Files.newBufferedReader(Paths.get(args[0]), StandardCharsets.UTF_8))
		{
			JsonObject stats = new Gson().fromJson(reader, JsonObject.class);
			for (Map.Entry<String, JsonElement> entry : stats.entrySet())
			{
				JsonObject item = entry.getValue().getAsJsonObject();
				if (item.has("equipment"))
				{
					JsonObject equipment = item.getAsJsonObject("equipment");
					int id = Integer.parseInt(entry.getKey());
					// The minified file drops zero fields, and head is slot 0.
					slotOf.put(id, equipment.has("slot") ? equipment.get("slot").getAsInt() : 0);
					if (equipment.has("is2h") && equipment.get("is2h").getAsBoolean())
					{
						twoHanded.add(id);
					}
				}
			}
		}

		ActivityDatabase activities = new ActivityDatabase(new Gson());
		activities.load();
		String only = args.length > 1 ? args[1] : null;

		for (Activity activity : activities.getAllActivities())
		{
			if (only != null && !activity.getId().contains(only))
			{
				continue;
			}
			for (Activity.GearSetup setup : activity.getSetups())
			{
				Set<Integer> filled = new LinkedHashSet<>();
				boolean twoHander = false;
				List<String> unmatched = new ArrayList<>();
				for (String line : setup.getItems())
				{
					for (ItemTextParser.Phrase phrase : ItemTextParser.phrases(line))
					{
						String term = ItemTextParser.searchTerm(phrase);
						Integer id = ItemTextParser.pick(phrase, ItemNameIndex.search(term));
						if (id == null)
						{
							unmatched.add(phrase.getText());
							continue;
						}
						Integer slot = slotOf.get(id);
						if (only != null)
						{
							// Asked about one fight: say what every phrase became.
							System.out.println("    \"" + phrase + "\" -> " + id + " "
								+ ItemNameIndex.search(term).stream().filter(c -> c.getId() == id)
									.map(ItemTextParser.Candidate::getName).findFirst().orElse("?")
								+ " -> " + (slot == null ? "not worn" : SLOTS.get(slot)));
						}
						if (slot == null)
						{
							continue;
						}
						filled.add(slot);
						twoHander |= slot == 3 && twoHanded.contains(id);
					}
				}
				List<String> empty = new ArrayList<>();
				for (Map.Entry<Integer, String> slot : SLOTS.entrySet())
				{
					boolean shieldTaken = slot.getKey() == 5 && twoHander;
					if (!filled.contains(slot.getKey()) && !shieldTaken)
					{
						empty.add(slot.getValue());
					}
				}
				System.out.println(activity.getId() + " | " + setup.getName()
					+ " | empty: " + String.join(",", empty)
					+ (unmatched.isEmpty() ? "" : " | unmatched: " + String.join("; ", unmatched)));
			}
		}
	}
}
