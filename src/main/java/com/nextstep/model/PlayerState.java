package com.nextstep.model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A plain-data snapshot of the logged-in player's progress, rebuilt on every
 * refresh cycle (e.g. on GameTick or every N minutes) by NextStepPlugin.
 * The SuggestionEngine only ever reads from this - it never talks to the
 * RuneLite Client directly, which keeps the engine unit-testable.
 */
public class PlayerState
{
	private final Map<String, Integer> skillLevels = new HashMap<>();
	private final Map<String, Long> skillXp = new java.util.HashMap<>();
	private final Set<String> completedQuests = new HashSet<>();
	private int questPoints;
	private int combatLevel;
	/** Key format "REGION_TIER", e.g. "ARDOUGNE_HARD" */
	private final Set<String> completedDiaryTiers = new HashSet<>();
	private final Map<String, Integer> bossKillCounts = new HashMap<>();
	private final Set<String> completedCombatAchievementTiers = new HashSet<>();
	/** Collection log slots the player owns, by item name. Only reliable once the log has been opened once. */
	private final Set<String> collectionLogUnlocks = new HashSet<>();
	private int collectionLogSlotsTotal;
	private int collectionLogSlotsObtained;

	// What the player owns, keyed by RuneLite item id, summed across bank,
	// inventory and worn equipment. See ItemTracker for why the bank half of
	// this is a snapshot rather than live.
	private Map<Integer, Integer> ownedItems = new HashMap<>();
	private Set<Integer> equippedItems = new HashSet<>();
	private boolean bankSnapshotAvailable;

	public void setSkillLevel(String skill, int level)
	{
		skillLevels.put(skill.toUpperCase(), level);
	}

	/**
	 * Current experience in a skill, or 0 when it is not known.
	 * <p>
	 * Levels alone were enough while every goal named one skill: you are 76
	 * Thieving, you want 99, the xp between those is the plan. A total level
	 * goal has to compare the <em>next</em> level in 23 skills against each
	 * other, and "level 62" could be one xp short of 63 or the whole bracket
	 * away. Without the xp the cheapest-first ordering is guesswork dressed as
	 * arithmetic.
	 */
	public long getSkillXp(String skill)
	{
		Long xp = skillXp.get(skill.toUpperCase());
		return xp == null ? 0 : xp;
	}

	public void setSkillXp(String skill, long xp)
	{
		skillXp.put(skill.toUpperCase(), xp);
	}

	/** Every level read from the client, for the panel to mark finished steps. */
	public Map<String, Integer> getSkillLevels()
	{
		return java.util.Collections.unmodifiableMap(skillLevels);
	}

	public int getSkillLevel(String skill)
	{
		return skillLevels.getOrDefault(skill.toUpperCase(), 1);
	}

	public void addCompletedQuest(String questName)
	{
		completedQuests.add(questName);
	}

	public boolean isQuestComplete(String questName)
	{
		return completedQuests.contains(questName);
	}

	public void setQuestPoints(int qp)
	{
		this.questPoints = qp;
	}

	public int getQuestPoints()
	{
		return questPoints;
	}

	public void setCombatLevel(int level)
	{
		this.combatLevel = level;
	}

	public int getCombatLevel()
	{
		return combatLevel;
	}

	public void addCompletedDiaryTier(String regionTierKey)
	{
		completedDiaryTiers.add(regionTierKey.toUpperCase());
	}

	public boolean isDiaryTierComplete(String regionTierKey)
	{
		return completedDiaryTiers.contains(regionTierKey.toUpperCase());
	}

	public void setBossKillCount(String bossName, int kc)
	{
		bossKillCounts.put(bossName.toUpperCase(), kc);
	}

	public int getBossKillCount(String bossName)
	{
		return bossKillCounts.getOrDefault(bossName.toUpperCase(), 0);
	}

	public void addCompletedCombatAchievementTier(String tier)
	{
		completedCombatAchievementTiers.add(tier.toUpperCase());
	}

	public boolean isCombatAchievementTierComplete(String tier)
	{
		return completedCombatAchievementTiers.contains(tier.toUpperCase());
	}

	public void addCollectionLogUnlock(String itemName)
	{
		collectionLogUnlocks.add(itemName);
	}

	public boolean hasCollectionLogUnlock(String itemName)
	{
		return collectionLogUnlocks.contains(itemName);
	}

	public void setCollectionLogProgress(int obtained, int total)
	{
		this.collectionLogSlotsObtained = obtained;
		this.collectionLogSlotsTotal = total;
	}

	public int getCollectionLogSlotsObtained()
	{
		return collectionLogSlotsObtained;
	}

	public int getCollectionLogSlotsTotal()
	{
		return collectionLogSlotsTotal;
	}

	public void setOwnedItems(Map<Integer, Integer> items)
	{
		this.ownedItems = items;
	}

	/** How many of this item the player has across bank, inventory and equipment. */
	public int getOwnedCount(int itemId)
	{
		return ownedItems.getOrDefault(itemId, 0);
	}

	public void setEquippedItems(Set<Integer> items)
	{
		this.equippedItems = items;
	}

	public boolean isEquipped(int itemId)
	{
		return equippedItems.contains(itemId);
	}

	public Set<Integer> getEquippedItems()
	{
		return equippedItems;
	}

	/**
	 * False until the player has opened their bank at least once. A plan built
	 * without this should say so rather than assume an empty bank.
	 */
	public boolean isBankSnapshotAvailable()
	{
		return bankSnapshotAvailable;
	}

	public void setBankSnapshotAvailable(boolean available)
	{
		this.bankSnapshotAvailable = available;
	}
}
