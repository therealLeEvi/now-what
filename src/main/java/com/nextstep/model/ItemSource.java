package com.nextstep.model;

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
	private String wikiUrl;
	/** MONSTER_DROP only: rough expected kills for one drop. Always an estimate. */
	private Integer estimatedKc;
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
	/** CRAFT only: skill level needed to perform the craft. */
	private Requirement skillRequirement;
	/** CRAFT only. */
	private List<Ingredient> ingredients = Collections.emptyList();

	public String getPreparationFrom()
	{
		return preparationFrom;
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
