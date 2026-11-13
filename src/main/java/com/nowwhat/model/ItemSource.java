package com.nowwhat.model;

import java.util.Collections;
import java.util.List;

/**
 * How to obtain an item. Loaded from item-sources.json, keyed by item id.
 */
public class ItemSource
{
	public enum SourceType
	{
		GE_BUY,
		SHOP,
		MONSTER_DROP,
		CRAFT,
		QUEST_REWARD
	}

	/** A single ingredient line in a CRAFT recipe. */
	public static class Ingredient
	{
		private String itemId;
		private int quantity;

		public Ingredient()
		{
		}

		public Ingredient(String itemId, int quantity)
		{
			this.itemId = itemId;
			this.quantity = quantity;
		}

		public String getItemId()
		{
			return itemId;
		}

		public int getQuantity()
		{
			return quantity;
		}
	}

	private String id;
	private String displayName;
	private SourceType type;
	/** RuneLite item id, so the planner can subtract what you already own. */
	private Integer gameItemId;
	/** Free-text detail: shop name, boss name, notes - meaning depends on type. */
	private String detail;
	/**
	 * How the drop actually works, where that takes more than a place name:
	 * "about 1 kill in 100 drops an unsired...". Kept apart from detail because
	 * a drop's detail is read as a place in "Bludgeon claw from <detail>", and
	 * a sentence there turns the step title into a paragraph.
	 */
	private String note;
	private String wikiUrl;
	/** MONSTER_DROP only: rough expected kills for one drop. Always an estimate. */
	private Integer estimatedKc;
	/**
	 * MONSTER_DROP only: what one of those attempts is called, when "attempts"
	 * is the wrong word. Barrows is counted in chests, not kills - one chest is
	 * six brothers - so "~728 attempts" left the player guessing what an
	 * attempt was. Null means "attempts".
	 */
	private String attemptUnit;
	/**
	 * The activity whose gear and supplies answer "so what do I take?" for this
	 * item - the id of the fight it drops from.
	 * <p>
	 * {@link #detail} already names that fight in prose ("Chambers of Xeric"),
	 * but prose is not a reference: "Smoke devils" is the Thermonuclear smoke
	 * devil and "Cyclopes in the Warriors' Guild" is not any activity's name, so
	 * matching on it would work for some items and silently fail for others.
	 */
	private String preparationFrom;
	/**
	 * SHOP only: the activity whose currency pays for this.
	 * <p>
	 * {@link #detail} names the shop and the price, and a price on its own is a
	 * dead end: "Tithe Farm reward shop, 250 points" tells a player nothing
	 * about where points come from, how long 250 of them take, or what to bring
	 * to earn them. Naming the activity lets the plan put a step in front of the
	 * purchase instead of quoting a number and stopping.
	 */
	private String currencyFrom;
	/** CRAFT only: skill level needed to perform the craft. */
	private Requirement skillRequirement;
	/** CRAFT only. */
	private List<Ingredient> ingredients = Collections.emptyList();

	public String getPreparationFrom()
	{
		// You are going to the activity that mints the currency either way, so
		// its kit answers "what do I take?" for the shop item too. Derived
		// rather than authored twice, so the two cannot drift apart.
		return preparationFrom != null ? preparationFrom : currencyFrom;
	}

	/** For programmatic construction; the shipped data comes from JSON. */
	public ItemSource withCurrencyFrom(String currencyFrom)
	{
		this.currencyFrom = currencyFrom;
		return this;
	}

	/** For programmatic construction; the shipped data comes from JSON. */
	public ItemSource withGameItemId(int gameItemId)
	{
		this.gameItemId = gameItemId;
		return this;
	}

	/** @see #currencyFrom */
	public String getCurrencyFrom()
	{
		return currencyFrom;
	}

	/** No-arg constructor used by Gson when loading item-sources.json. */
	public ItemSource()
	{
	}

	/** Full constructor for programmatic construction (tests). */
	public ItemSource(String id, String displayName, SourceType type, String detail, String wikiUrl,
		Integer estimatedKc, Requirement skillRequirement, List<Ingredient> ingredients)
	{
		this.id = id;
		this.displayName = displayName;
		this.type = type;
		this.detail = detail;
		this.wikiUrl = wikiUrl;
		this.estimatedKc = estimatedKc;
		this.skillRequirement = skillRequirement;
		this.ingredients = ingredients;
	}

	public String getId()
	{
		return id;
	}

	public String getDisplayName()
	{
		return displayName;
	}

	public SourceType getType()
	{
		return type;
	}

	/** @see #note */
	public String getNote()
	{
		return note;
	}

	public String getDetail()
	{
		return detail;
	}

	public String getWikiUrl()
	{
		return wikiUrl;
	}

	public Integer getEstimatedKc()
	{
		return estimatedKc;
	}

	/** What one attempt is called in the plan: "attempts" unless the data says otherwise. */
	public String getAttemptUnit()
	{
		return attemptUnit == null ? "attempts" : attemptUnit;
	}

	public Requirement getSkillRequirement()
	{
		return skillRequirement;
	}

	public List<Ingredient> getIngredients()
	{
		return ingredients;
	}

	public Integer getGameItemId()
	{
		return gameItemId;
	}
}
