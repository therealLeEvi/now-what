package com.nextstep.model;

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

	public String getDescription()
	{
		return description;
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
}
