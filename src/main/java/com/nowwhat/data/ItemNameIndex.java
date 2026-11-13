package com.nowwhat.data;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * Names and ids for the items the bundled gear and supply lines mention, for
 * the ones RuneLite's own search cannot find.
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
 * The table is a resource, {@code item-names.txt}, generated from RuneLite's
 * {@code ItemID} constants by {@code ./gradlew itemnames}. It used to be read
 * from those constants at runtime by reflection, which Plugin Hub plugins may
 * not use; it holds only the items the bundled text resolves to, and
 * {@code ItemNamesTableTest} fails when the text and the table drift apart.
 * Constant names are not display names - they have lost apostrophes, hyphens
 * and brackets - so this stays the <em>fallback</em>: a real display name from
 * RuneLite's index is always preferred when there is one.
 */
@Slf4j
public final class ItemNameIndex
{
	static final String RESOURCE = "/com/nowwhat/item-names.txt";
	/** A typographic apostrophe, written as a number so the source stays ASCII. */
	private static final String CURLY_APOSTROPHE = String.valueOf((char) 0x2019);

	private static List<ItemTextParser.Candidate> index;
	/**
	 * Flattened names of the items the plugin itself knows as goals - the rows
	 * marked "known" in the table. When a phrase fits several items equally
	 * ("Bandos" for legs: tassets or chaps), the known one is what a guide
	 * means.
	 */
	private static Set<String> known = Collections.emptySet();

	private ItemNameIndex()
	{
	}

	/**
	 * Every item in the table whose name contains {@code term}, compared without
	 * the punctuation these names cannot carry.
	 */
	public static List<ItemTextParser.Candidate> search(String term)
	{
		List<ItemTextParser.Candidate> hits = new ArrayList<>();
		if (term == null || term.trim().isEmpty())
		{
			return hits;
		}
		String needle = flatten(term);
		for (ItemTextParser.Candidate candidate : index())
		{
			if (flatten(candidate.getName()).contains(needle))
			{
				hits.add(candidate);
			}
		}
		return hits;
	}

	/** The whole table, for the test that keeps it in step with the bundled text. */
	/** The table's third column for an item the plugin knows as a goal. */
	static final String KNOWN = "known";

	/** @see #known */
	public static Set<String> known()
	{
		index();
		return known;
	}

	static List<ItemTextParser.Candidate> all()
	{
		return Collections.unmodifiableList(index());
	}

	/** Apostrophes and hyphens are absent from constant names; ignore them on both sides. */
	public static String flatten(String text)
	{
		return text.toLowerCase(Locale.ROOT)
			.replace("'", "")
			.replace(CURLY_APOSTROPHE, "")
			.replace("-", "")
			.replace("+", "");
	}

	private static synchronized List<ItemTextParser.Candidate> index()
	{
		if (index == null)
		{
			index = load();
		}
		return index;
	}

	/** One item per line, "id TAB name". A missing or broken table leaves the fallback empty. */
	private static List<ItemTextParser.Candidate> load()
	{
		List<ItemTextParser.Candidate> loaded = new ArrayList<>();
		Set<String> knownNames = new HashSet<>();
		try (InputStream in = ItemNameIndex.class.getResourceAsStream(RESOURCE))
		{
			if (in == null)
			{
				log.warn("Item name table {} is missing", RESOURCE);
				return loaded;
			}
			BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
			String line;
			while ((line = reader.readLine()) != null)
			{
				String[] columns = line.split("\t");
				if (columns.length < 2 || columns[0].isEmpty())
				{
					continue;
				}
				loaded.add(new ItemTextParser.Candidate(columns[1], Integer.parseInt(columns[0])));
				if (columns.length > 2 && KNOWN.equals(columns[2]))
				{
					knownNames.add(flatten(columns[1]));
				}
			}
		}
		catch (IOException | NumberFormatException broken)
		{
			log.warn("Could not read the item name table", broken);
			loaded.clear();
			knownNames.clear();
		}
		known = Collections.unmodifiableSet(knownNames);
		return loaded;
	}
}
