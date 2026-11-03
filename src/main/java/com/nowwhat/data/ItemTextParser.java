package com.nowwhat.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Turns the free-text gear and supply lines in activities.json into item names
 * concrete enough to look up and show as a picture.
 * <p>
 * The lines were written to be read, not parsed: "Neck: rancour, torture, blood
 * fury", "Head/body/legs: Masori (f), Masori, Armadyl", "8-10 prayer potions
 * (4)". That is the right way round - the words are what a player actually
 * needs - so this reads them rather than asking the data to become a list of
 * item ids. Two things make it tractable:
 * <ul>
 *   <li>the lines name their slot often enough to disambiguate shorthand.
 *       "occult" is only an amulet because the line says "Neck:", and without
 *       that context a bare "crystal" could be a key, a pickaxe or a halberd -
 *       so a one-word option with no slot named resolves to nothing at all
 *       rather than to a guess;</li>
 *   <li>a slot prefix naming several slots ("Head/body/legs") means the option
 *       is a set, and expands into one lookup per slot.</li>
 * </ul>
 * Everything here is deliberately pure: it takes text and returns phrases and
 * ids, with no RuneLite types in sight, so the guessing can be unit-tested
 * instead of eyeballed in a running client.
 */
public final class ItemTextParser
{
	/** Separators between alternatives in one line, longest first so " or " wins over ",". */
	private static final String[] SEPARATORS = {" such as ", " then ", " with ", " or ", " and ",
		" plus ", ", ", ",", " / "};

	/**
	 * Words that start an explanation rather than another item: "4+ cooked
	 * karambwan <b>for</b> combo eating", "Any Bandos item <b>to</b> stop the
	 * bodyguards". Everything from there on is advice, and feeding it to a
	 * search only buys a wrong picture.
	 */
	private static final String[] PROSE_TAILS = {" for ", " to ", " if ", " when ", " unless ",
		" depending ", " must ", " that ", " which "};

	/** Dropped from the front of a phrase: quantities and determiners, never item words. */
	private static final Set<String> LEADING_NOISE = new LinkedHashSet<>(Arrays.asList(
		"a", "an", "any", "some", "the", "x", "best", "your", "about", "around",
		// Quantities again, spelled out: "Several super restores", "8 doses of
		// Saradomin brew". Stripped one word at a time, so "doses of" goes too.
		"several", "plenty", "lots", "extra", "spare", "more", "bring", "dose",
		"doses", "of", "or", "and"));

	/**
	 * The words that identify an item as belonging to a slot. Used only to pin
	 * down a one-word option: "ultor" plus "Ring:" is the Ultor ring, while
	 * "ultor" on its own is not safely anything.
	 */
	private static final Map<String, List<String>> SLOT_NOUNS = new LinkedHashMap<>();

	static
	{
		SLOT_NOUNS.put("head", Arrays.asList("helm", "helmet", "hat", "mask", "hood", "coif",
			"faceguard", "faceguard", "crown"));
		SLOT_NOUNS.put("helm", SLOT_NOUNS.get("head"));
		SLOT_NOUNS.put("body", Arrays.asList("body", "platebody", "chestplate", "top", "robetop",
			"hauberk", "torso", "robe", "chainbody"));
		SLOT_NOUNS.put("chest", SLOT_NOUNS.get("body"));
		SLOT_NOUNS.put("legs", Arrays.asList("legs", "platelegs", "chaps", "skirt", "plateskirt",
			"robeskirt", "bottom", "tassets", "robe"));
		SLOT_NOUNS.put("neck", Arrays.asList("necklace", "amulet"));
		SLOT_NOUNS.put("amulet", SLOT_NOUNS.get("neck"));
		SLOT_NOUNS.put("cape", Arrays.asList("cape", "cloak", "quiver", "assembler", "accumulator"));
		SLOT_NOUNS.put("back", SLOT_NOUNS.get("cape"));
		SLOT_NOUNS.put("shield", Arrays.asList("shield", "ward", "defender", "buckler", "book",
			"tome", "kiteshield"));
		SLOT_NOUNS.put("offhand", SLOT_NOUNS.get("shield"));
		SLOT_NOUNS.put("hands", Arrays.asList("gloves", "vambraces", "gauntlets", "bracelet",
			"bracers", "gloves"));
		SLOT_NOUNS.put("gloves", SLOT_NOUNS.get("hands"));
		SLOT_NOUNS.put("boots", Arrays.asList("boots", "shoes"));
		SLOT_NOUNS.put("feet", SLOT_NOUNS.get("boots"));
		SLOT_NOUNS.put("ring", Collections.singletonList("ring"));
		SLOT_NOUNS.put("ammo", Arrays.asList("arrow", "arrows", "bolts", "darts", "javelins",
			"knives"));
		SLOT_NOUNS.put("ammunition", SLOT_NOUNS.get("ammo"));
		// A weapon slot has no noun of its own - there is nothing all weapons are
		// called - so it is registered with none. That still gets the prefix out
		// of the way, which is the other half of the job: without it, "Weapon:
		// twisted bow plus toxic blowpipe" asks for an item called "weapon:
		// twisted bow" and finds nothing.
		SLOT_NOUNS.put("weapon", Collections.emptyList());
		SLOT_NOUNS.put("mainhand", Collections.emptyList());
		SLOT_NOUNS.put("2h", Collections.emptyList());
	}

	/** Names that are a different thing wearing the same name, and never what a guide means. */
	private static final String[] JUNK = {"placeholder", "(broken)", "(deadman", "(last man",
		"(lms)", "(bh)", "(beta)"};

	/**
	 * The same object, unloaded. Taken only when nothing better matches.
	 * <p>
	 * These used to be rejected outright, which quietly lost whole weapons: a
	 * charged scythe of vitur cannot be traded, so the only scythe in an index
	 * built from Grand Exchange prices is "Scythe of vitur (uncharged)". The
	 * picture is the same weapon either way.
	 */
	private static final String[] UNLOADED = {"(uncharged)", "(empty)", "(u)", "(unf)"};

	private ItemTextParser()
	{
	}

	/** One thing to look up: the words of an option, plus the slot words that pin it down. */
	public static final class Phrase
	{
		private final String text;
		private final List<String> words;
		private final List<String> slotNouns;
		private final String quantity;

		Phrase(String text, List<String> words, List<String> slotNouns, String quantity)
		{
			this.text = text;
			this.words = words;
			this.slotNouns = slotNouns;
			this.quantity = quantity;
		}

		public String getText()
		{
			return text;
		}

		public List<String> getWords()
		{
			return words;
		}

		/** Empty when the line named no slot - in which case a one-word phrase is dropped. */
		public List<String> getSlotNouns()
		{
			return slotNouns;
		}

		/**
		 * How many the line asked for - "8-10", "4+", "12-14" - or null where it
		 * said no number.
		 * <p>
		 * Stripped out of the words before looking the item up, because no item is
		 * called "8-10 prayer potions", but kept, because a picture of a prayer
		 * potion that does not say "8-10" has quietly dropped the part the player
		 * most needed.
		 */
		public String getQuantity()
		{
			return quantity;
		}

		@Override
		public String toString()
		{
			return text + (slotNouns.isEmpty() ? "" : " " + slotNouns);
		}
	}

	/** A possible item: whatever an item index offered for a phrase. */
	public static final class Candidate
	{
		private final String name;
		private final int id;

		public Candidate(String name, int id)
		{
			this.name = name;
			this.id = id;
		}

		public String getName()
		{
			return name;
		}

		public int getId()
		{
			return id;
		}
	}

	/**
	 * The lookups one written line asks for, in the order they were written.
	 * <p>
	 * A line naming several slots is a set, so it expands: "Head/body/legs:
	 * Masori (f), Masori, Armadyl" becomes nine phrases, which is nine pictures
	 * of three real kits rather than one arbitrarily chosen helmet.
	 */
	public static List<Phrase> phrases(String entry)
	{
		List<Phrase> phrases = new ArrayList<>();
		if (entry == null || entry.trim().isEmpty())
		{
			return phrases;
		}

		String text = entry.trim();
		List<String> slots = new ArrayList<>();

		// "Neck: occult then fury" - the part before the colon names the slot(s),
		// never the item. A colon anywhere else in the line is left alone.
		int colon = text.indexOf(':');
		if (colon > 0 && colon < 24)
		{
			String prefix = text.substring(0, colon).toLowerCase(Locale.ROOT);
			for (String part : prefix.split("[/ ]+"))
			{
				if (SLOT_NOUNS.containsKey(part))
				{
					slots.add(part);
				}
			}
			if (!slots.isEmpty())
			{
				text = text.substring(colon + 1).trim();
			}
		}

		text = itemPart(text);

		// "Bandos chestplate and tassets" is one kit written once: the pieces after
		// the first are named by their own word alone, and only the first option
		// says which set they belong to. Without carrying that word across, every
		// line of this shape loses all but its first piece.
		String setWord = null;

		for (String option : split(text))
		{
			Cleaned cleaned = words(option);
			List<String> words = cleaned.getWords();
			if (words.isEmpty())
			{
				continue;
			}
			if (words.size() > 1 && setWord == null)
			{
				setWord = words.get(0);
			}
			else if (words.size() == 1 && setWord != null && slots.isEmpty())
			{
				words = Arrays.asList(setWord, words.get(0));
				option = setWord + " " + option;
			}
			if (slots.isEmpty())
			{
				phrases.add(new Phrase(option, words, Collections.emptyList(),
					cleaned.getQuantity()));
				continue;
			}
			for (String slot : slots)
			{
				phrases.add(new Phrase(option, words, SLOT_NOUNS.get(slot),
					cleaned.getQuantity()));
			}
		}
		return phrases;
	}

	/**
	 * The part of a line that names items: everything before a trailing
	 * explanation. "Hespori seed - no seed, no fight" is one item and a warning.
	 */
	static String itemPart(String text)
	{
		String part = text.trim();
		for (String dash : new String[]{" - ", " \u2014 ", " \u2013 "})
		{
			int at = part.indexOf(dash);
			if (at > 0)
			{
				part = part.substring(0, at).trim();
			}
		}
		for (String tail : PROSE_TAILS)
		{
			int at = part.toLowerCase(Locale.ROOT).indexOf(tail);
			if (at > 0)
			{
				part = part.substring(0, at).trim();
			}
		}
		return part;
	}

	/**
	 * Whether a line says anything beyond naming items - a condition, a reason, a
	 * caveat.
	 * <p>
	 * This is what decides whether pictures can stand in for the words. Icons can
	 * replace "twisted bow or toxic blowpipe" completely; they cannot replace
	 * "Extended anti-venom+ (4) - not optional here", where the sentence is the
	 * point and the item is only where it starts.
	 */
	public static boolean hasExplanation(String entry)
	{
		if (entry == null)
		{
			return false;
		}
		return itemPart(entry).length() < entry.trim().length();
	}

	private static List<String> split(String text)
	{
		List<String> parts = new ArrayList<>();
		parts.add(text);
		for (String separator : SEPARATORS)
		{
			List<String> next = new ArrayList<>();
			for (String part : parts)
			{
				for (String piece : part.split(java.util.regex.Pattern.quote(separator)))
				{
					if (!piece.trim().isEmpty())
					{
						next.add(piece.trim());
					}
				}
			}
			parts = next;
		}
		return parts;
	}

	/** An option's words, lowercased, with leading quantities and determiners dropped. */
	/** An option's words and the count in front of them, separated. */
	static final class Cleaned
	{
		private final List<String> words = new ArrayList<>();
		private String quantity;

		List<String> getWords()
		{
			return words;
		}

		String getQuantity()
		{
			return quantity;
		}
	}

	/**
	 * An option's words, lowercased, with leading quantities and determiners
	 * dropped - and the quantity handed back rather than thrown away.
	 */
	static Cleaned words(String option)
	{
		Cleaned cleanedOption = new Cleaned();
		for (String word : option.toLowerCase(Locale.ROOT).split("[\\s]+"))
		{
			String cleaned = word.replace("\"", "").trim();
			if (cleaned.isEmpty())
			{
				continue;
			}
			if (cleanedOption.words.isEmpty())
			{
				if (cleaned.matches("[0-9]+[-+x][0-9]*") || cleaned.matches("[0-9]+"))
				{
					cleanedOption.quantity = cleanedOption.quantity == null
						? word.trim()
						: cleanedOption.quantity;
					continue;
				}
				if (LEADING_NOISE.contains(cleaned))
				{
					continue;
				}
			}
			cleanedOption.words.add(cleaned);
		}
		return cleanedOption;
	}

	/**
	 * What to ask an item index for, so that it offers the right candidates.
	 * <p>
	 * The longest word, since that is the most distinctive one, minus a trailing
	 * plural: the guides write "prayer potions" where the item is "Prayer
	 * potion(4)", and searching for the plural finds nothing at all.
	 */
	public static String searchTerm(Phrase phrase)
	{
		String longest = "";
		for (String word : phrase.getWords())
		{
			String cleaned = trimPunctuation(word);
			if (cleaned.length() > longest.length())
			{
				longest = cleaned;
			}
		}
		if (longest.length() >= 5 && longest.endsWith("s"))
		{
			longest = longest.substring(0, longest.length() - 1);
		}
		return longest;
	}

	/**
	 * The one item a phrase means, or null when the phrase does not pin an item
	 * down well enough to show a picture of it.
	 * <p>
	 * Every word of the phrase has to appear in the item's name, so "Masori body"
	 * cannot land on Masori chaps. A one-word phrase additionally has to be
	 * carrying a slot, and the slot's noun has to appear too - this is what keeps
	 * "crystal" under "Boots:" off a crystal key. Where several items still
	 * qualify the shortest name wins, since the longer ones are variants
	 * ("Masori body" over "Masori body (f)").
	 * <p>
	 * Before length, though, a name that has the very words the line was
	 * written with beats one that only matched through a slot's other nouns.
	 * "Shield" stands for the whole off-hand slot, so "dragonfire shield" also
	 * matches "Dragonfire ward", and the ward's shorter name used to win; the
	 * same went for "robe bottom" landing on the robe top, since "robe" is in
	 * both the body and the legs lists.
	 */
	public static Integer pick(Phrase phrase, List<Candidate> candidates)
	{
		if (candidates == null || candidates.isEmpty())
		{
			return null;
		}
		// A one-word option normally needs a slot to pin it to an item. The
		// exception is a word that is the whole name of something - "Facemask",
		// "Antipoison", "Sharks" - where there is nothing left to disambiguate.
		// "crystal" still resolves to nothing, because no item is called that.
		boolean nameInFull = phrase.getWords().size() < 2 && phrase.getSlotNouns().isEmpty();

		Candidate best = null;
		Candidate bestWritten = null;
		Candidate unloaded = null;
		for (Candidate candidate : candidates)
		{
			String lower = candidate.getName().toLowerCase(Locale.ROOT);
			if (contains(lower, JUNK))
			{
				continue;
			}

			List<String> nameWords = Arrays.asList(lower.split("[\\s]+"));
			if (!containsAll(nameWords, phrase.getWords()))
			{
				continue;
			}
			if (nameInFull && nameWords.size() > 1)
			{
				continue;
			}
			if (!phrase.getSlotNouns().isEmpty() && !containsAny(nameWords, phrase.getSlotNouns()))
			{
				continue;
			}
			if (contains(lower, UNLOADED))
			{
				if (unloaded == null || beats(candidate, unloaded))
				{
					unloaded = candidate;
				}
				continue;
			}
			if (best == null || beats(candidate, best))
			{
				best = candidate;
			}
			if (containsAllExactly(nameWords, phrase.getWords())
				&& (bestWritten == null || beats(candidate, bestWritten)))
			{
				bestWritten = candidate;
			}
		}
		if (bestWritten != null)
		{
			best = bestWritten;
		}
		if (best == null)
		{
			best = unloaded;
		}
		return best == null ? null : best.getId();
	}

	private static boolean contains(String name, String[] markers)
	{
		for (String marker : markers)
		{
			if (name.contains(marker))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Which of two equally valid matches to show. The shorter name is the plainer
	 * item, so it wins; between names of the same length the bigger dose does,
	 * because a guide that says "prayer potions" without a number means full
	 * ones, and a 1-dose icon would be quietly wrong.
	 */
	private static boolean beats(Candidate candidate, Candidate best)
	{
		int length = candidate.getName().length() - best.getName().length();
		if (length != 0)
		{
			return length < 0;
		}
		int dose = dose(candidate.getName()) - dose(best.getName());
		if (dose != 0)
		{
			return dose > 0;
		}
		return candidate.getId() < best.getId();
	}

	/** The dose in a name like "Prayer potion(4)", or 0 where there is none. */
	private static int dose(String name)
	{
		String trimmed = name.trim();
		if (trimmed.length() < 3 || !trimmed.endsWith(")"))
		{
			return 0;
		}
		char digit = trimmed.charAt(trimmed.length() - 2);
		return trimmed.charAt(trimmed.length() - 3) == '(' && Character.isDigit(digit)
			? digit - '0'
			: 0;
	}

	private static boolean containsAll(List<String> nameWords, List<String> needed)
	{
		for (String word : needed)
		{
			if (!containsWord(nameWords, word, true))
			{
				return false;
			}
		}
		return true;
	}

	/**
	 * Does this name belong to the slot the line named?
	 * <p>
	 * Strict, with no synonym groups: the slot nouns <em>are</em> the groups, so
	 * widening them here would make every group match every other through the
	 * words they share - "robe" sits in both the body list and the legs list -
	 * and "Head/body/legs: Masori (f)" would answer every one of its three slots
	 * with the same piece.
	 */
	private static boolean containsAny(List<String> nameWords, List<String> options)
	{
		for (String word : options)
		{
			if (containsWord(nameWords, word, false))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Word match, tolerating a plural on either side and trailing punctuation.
	 * The guides say "prayer potions (4)" where the item is "Prayer potion(4)",
	 * and refusing that pairing would cost most of the supply icons.
	 */
	private static boolean containsWord(List<String> nameWords, String word, boolean synonyms)
	{
		String needle = trimPunctuation(word);
		if (needle.isEmpty())
		{
			return true;
		}
		for (String name : nameWords)
		{
			String candidate = trimPunctuation(name);
			if (samish(candidate, needle))
			{
				return true;
			}
			// "potion(4)" is one word in an item name but two ideas; match either.
			int bracket = candidate.indexOf('(');
			if (bracket > 0 && (samish(candidate.substring(0, bracket), needle)
				|| samish(candidate.substring(bracket), needle)))
			{
				return true;
			}
			if (synonyms && sameSlotWord(candidate, needle))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Whether two words are the same piece of armour under different names.
	 * <p>
	 * The guides say "Torag's body and legs" where the items are "Torag's
	 * platebody" and "Torag's platelegs". Matching those needs the slot groups
	 * that are already written down for disambiguating one-word options - and
	 * using them this way keeps the guarantee that matters, because a body word
	 * and a legs word are never in the same group: "Masori body" still cannot
	 * land on Masori chaps.
	 */
	private static boolean sameSlotWord(String a, String b)
	{
		if (a.equals(b))
		{
			return true;
		}
		for (List<String> group : SLOT_NOUNS.values())
		{
			boolean hasA = false;
			boolean hasB = false;
			for (String word : group)
			{
				hasA = hasA || samish(word, a);
				hasB = hasB || samish(word, b);
			}
			if (hasA && hasB)
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Equal, give or take a plural and an apostrophe on either side. Item names
	 * and the guides disagree about apostrophes often enough to matter: a line
	 * reading "Avas accumulator" should still find "Ava's accumulator".
	 */
	private static boolean samish(String a, String b)
	{
		a = a.replace("'", "").replace("\u2019", "");
		b = b.replace("'", "").replace("\u2019", "");
		return a.equals(b) || a.equals(b + "s") || b.equals(a + "s")
			|| a.equals(b + "es") || b.equals(a + "es");
	}

	/** As {@link #containsAll}, but each word has to be there itself, not through its slot's other nouns. */
	private static boolean containsAllExactly(List<String> nameWords, List<String> needed)
	{
		for (String word : needed)
		{
			if (!containsWord(nameWords, word, false))
			{
				return false;
			}
		}
		return true;
	}

	private static String trimPunctuation(String word)
	{
		return word.replaceAll("^[^a-z0-9('\u2019+]+", "").replaceAll("[^a-z0-9)'\u2019+]+$", "");
	}
}
