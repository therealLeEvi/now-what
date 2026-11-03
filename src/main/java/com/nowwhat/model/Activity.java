package com.nowwhat.model;

import java.util.Collections;
import java.util.List;

/**
 * One suggestable "thing to do" - a quest, a diary tier, a boss, a skilling
 * milestone, a collection log grind, etc. Loaded from activities.json.
 */
public class Activity
{
	public enum Category
	{
		QUEST,
		DIARY,
		PVM,
		SKILLING,
		MINIGAME,
		COLLECTION_LOG,
		COMBAT_ACHIEVEMENT,
		CLUE_SCROLL
	}

	private String id;
	private String name;
	private Category category;
	private String description;
	/** Short human-readable reason shown in the panel, e.g. "Unlocks Zulrah" or "83% done". */
	private String reasonTemplate;
	private String wikiUrl;
	private List<Requirement> requirements = Collections.emptyList();
	/** Higher = shown first when multiple suggestions are otherwise tied. */
	private int priority;
	/**
	 * The stats the wiki recommends before attempting this, as opposed to what
	 * the game enforces. They decide whether it is <em>suggested</em>, never
	 * whether it can be planned: plenty of fights have no requirement at all,
	 * and without this a level 3 account was being pointed at the Inferno.
	 */
	private List<Requirement> recommended = Collections.emptyList();
	/** Takes place in the Wilderness, where other players can attack you. */
	private boolean wilderness;

	/**
	 * What to wear and what to bring, for content you can now actually attempt.
	 * <p>
	 * A plan that ends at "you already meet every requirement" stops exactly
	 * where it became useful: the next question is always "so what do I take?",
	 * and answering it with a wiki link sends the player out of the sidebar to
	 * find out. These fields let the panel answer it in place.
	 */
	private List<GearSetup> setups = Collections.emptyList();
	/** Consumables and utility items to withdraw before going. */
	private List<String> supplies = Collections.emptyList();
	/** The wiki's full strategy page, for the depth the panel shouldn't try to hold. */
	private String strategyUrl;
	/**
	 * The RuneLite plugin that helps during the fight itself, by the name in its
	 * PluginDescriptor (e.g. "Zulrah Helper"). Looked up by name at runtime, so
	 * there is no build dependency on it and nothing breaks if it isn't installed.
	 */
	private String helperPlugin;
	/**
	 * That plugin's Plugin Hub id (e.g. "zulrah-helper"), used to link straight
	 * to its hub page rather than dropping the player on the hub's front door
	 * to go looking.
	 */
	private String helperPluginId;
	/**
	 * Another activity's id to take the preparation from, for an entry that is a
	 * reason to do a fight rather than a fight of its own.
	 * <p>
	 * A collection log slot is the clearest case: "Pet snakeling (Zulrah)" is
	 * Zulrah, over and over, so the answer to "what do I take?" is Zulrah's
	 * answer and duplicating four kits and seven supply lines per drop would
	 * mean two copies to keep right. Resolved once at load.
	 */
	private String preparationFrom;
	/**
	 * The name of the activity the preparation was borrowed from, set during
	 * resolution so the panel can say whose gear this is - a player who asked
	 * for a pet and got handed a blowpipe is owed the word "Zulrah".
	 */
	private String preparationSource;

	/** One named kit - "Ranged (budget)", "Magic (best in slot)" - and its items. */
	public static class GearSetup
	{
		private String name;
		private List<String> items = Collections.emptyList();

		public GearSetup()
		{
		}

		public GearSetup(String name, List<String> items)
		{
			this.name = name;
			this.items = items;
		}

		public String getName()
		{
			return name;
		}

		public List<String> getItems()
		{
			return items == null ? Collections.emptyList() : items;
		}
	}

	public List<GearSetup> getSetups()
	{
		return setups == null ? Collections.emptyList() : setups;
	}

	public List<String> getSupplies()
	{
		return supplies == null ? Collections.emptyList() : supplies;
	}

	public String getStrategyUrl()
	{
		return strategyUrl;
	}

	public String getHelperPlugin()
	{
		return helperPlugin;
	}

	public String getHelperPluginId()
	{
		return helperPluginId;
	}

	public String getPreparationFrom()
	{
		return preparationFrom;
	}

	/** The activity this preparation belongs to, or null when it is this one's own. */
	public String getPreparationSource()
	{
		return preparationSource;
	}

	/**
	 * Takes another activity's gear, supplies, strategy page and in-fight helper.
	 * <p>
	 * Only ever fills gaps: an entry that says anything for itself keeps all of
	 * it, so adding a line of its own to a borrowing entry later does not
	 * silently mix the two.
	 */
	public void inheritPreparation(Activity source)
	{
		if (source == null || hasPreparation() || !source.hasPreparation())
		{
			return;
		}
		setups = source.getSetups();
		supplies = source.getSupplies();
		strategyUrl = source.getStrategyUrl();
		helperPlugin = source.getHelperPlugin();
		helperPluginId = source.getHelperPluginId();
		preparationSource = source.getName();
	}

	/** True when there is anything worth rendering in a preparation block. */
	public boolean hasPreparation()
	{
		return !getSetups().isEmpty() || !getSupplies().isEmpty()
			|| strategyUrl != null || helperPlugin != null;
	}

	/** No-arg constructor used by Gson when loading activities.json. */
	public Activity()
	{
	}

	/**
	 * Minimal constructor for programmatic construction (tests, and any
	 * future in-code activity registration) - everything beyond the
	 * essentials defaults to null/empty, matching how a bare JSON entry
	 * with only these fields would deserialize.
	 */
	public Activity(String id, String name, Category category, List<Requirement> requirements)
	{
		this.id = id;
		this.name = name;
		this.category = category;
		this.requirements = requirements;
	}

	/**
	 * Adds the description to a programmatically built activity.
	 * <p>
	 * Not decoration: a plan step built from an activity shows its description,
	 * so a test about what a step says has to be able to set one.
	 */
	public Activity withDescription(String description)
	{
		this.description = description;
		return this;
	}

	/** Adds recommended stats and the Wilderness flag to a programmatically built activity. */
	public Activity withRecommended(List<Requirement> recommended, boolean wilderness)
	{
		this.recommended = recommended;
		this.wilderness = wilderness;
		return this;
	}

	/** Same as above, with an explicit priority - used where ranking order matters (e.g. tests). */
	public Activity(String id, String name, Category category, List<Requirement> requirements, int priority)
	{
		this(id, name, category, requirements);
		this.priority = priority;
	}

	public String getId()
	{
		return id;
	}

	public String getName()
	{
		return name;
	}

	public Category getCategory()
	{
		return category;
	}

	/**
	 * The description as one line of prose, for the places that show it inline:
	 * a plan step, a rolled suggestion. See {@link #getHowTo()} for the laid-out
	 * version.
	 */
	public String getDescription()
	{
		return description == null ? null : flatten(description);
	}

	/**
	 * The description with its layout: paragraphs on their own lines, a short
	 * line ending in a colon as a heading, and lines starting "* " as a list
	 * under it. A birdhouse run is a route, a list of spots and a table of
	 * tiers, and written as one paragraph it could not be read in a sidebar.
	 */
	public String getHowTo()
	{
		return description;
	}

	/** Joins the laid-out form into one line, a heading's list joined with commas. */
	static String flatten(String text)
	{
		List<String> out = new java.util.ArrayList<>();
		List<String> list = new java.util.ArrayList<>();
		for (String raw : text.split("\n"))
		{
			String line = raw.trim();
			if (line.isEmpty())
			{
				continue;
			}
			if (line.startsWith("* "))
			{
				list.add(line.substring(2).trim());
				continue;
			}
			if (!list.isEmpty())
			{
				out.add(String.join(", ", list) + ".");
				list.clear();
			}
			out.add(line);
		}
		if (!list.isEmpty())
		{
			out.add(String.join(", ", list) + ".");
		}
		return String.join(" ", out);
	}

	public String getReasonTemplate()
	{
		return reasonTemplate;
	}

	public String getWikiUrl()
	{
		return wikiUrl;
	}

	public List<Requirement> getRequirements()
	{
		return requirements;
	}

	public int getPriority()
	{
		return priority;
	}

	/** Never null; Gson leaves the field null when the JSON has no such key. */
	public List<Requirement> getRecommended()
	{
		return recommended == null ? Collections.emptyList() : recommended;
	}

	public boolean isWilderness()
	{
		return wilderness;
	}
}
