package com.nowwhat;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("nowwhat")
public interface NowWhatConfig extends Config
{
	@ConfigItem(
		keyName = "showQuests",
		name = "Show quest suggestions",
		description = "Include unstarted/available quests in suggestions",
		position = 1
	)
	default boolean showQuests()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showDiaries",
		name = "Show diary suggestions",
		description = "Include achievement diary tasks in suggestions",
		position = 2
	)
	default boolean showDiaries()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showPvm",
		name = "Show PvM suggestions",
		description = "Include boss/PvM suggestions",
		position = 3
	)
	default boolean showPvm()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showCollectionLog",
		name = "Show collection log suggestions",
		description = "Include collection log grind suggestions",
		position = 4
	)
	default boolean showCollectionLog()
	{
		return true;
	}

	@ConfigItem(
		keyName = "allowTickManipulation",
		name = "Prefer tick-manipulation methods",
		description = "Default training intensity for path planning: on shows tick-manipulation methods (falling back to normal methods where none are recorded), off shows only normal methods. Exclusive, not additive \u2014 the plan shows one intensity, not both mixed together. Can be overridden per-lookup in the panel.",
		position = 5
	)
	default boolean allowTickManipulation()
	{
		return false;
	}
}
