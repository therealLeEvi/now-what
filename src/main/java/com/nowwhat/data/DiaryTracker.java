package com.nowwhat.data;

import java.util.LinkedHashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.gameval.VarbitID;

/**
 * Reads Achievement Diary completion out of varbits.
 *
 * <p>An earlier version of this class assumed each region packed all four
 * tiers into one varbit as a bitmask, and shipped with every id set to -1 -
 * which meant the loop skipped every region and diary completion was never
 * actually read. RuneLite exposes a separate varbit per region per tier
 * ({@link VarbitID#ARDOUGNE_DIARY_EASY_COMPLETE} and friends), so there is no bitmask
 * and no placeholder ids: the real values are used directly.
 *
 * <p>Karamja is the exception. Its easy/medium/hard varbits are progress
 * counters (0 = not started, 1 = started, 2 = complete) rather than the plain
 * 0/1 flags every other region uses, so they need a different comparison.
 * Karamja elite is a normal flag. If a diary tier ever reports as incomplete
 * when it isn't, this quirk is the first thing to check with the Var
 * Inspector in developer mode.
 */
public class DiaryTracker
{
	/** Region name (as used in activity ids and requirement values) -> per-tier varbit ids. */
	private static final Map<String, int[]> REGION_VARBITS = new LinkedHashMap<>();

	/** Regions whose varbits count progress instead of flagging completion. */
	private static final String KARAMJA = "KARAMJA";

	private static final int EASY = 0;
	private static final int MEDIUM = 1;
	private static final int HARD = 2;
	private static final int ELITE = 3;

	private static final String[] TIER_NAMES = {"EASY", "MEDIUM", "HARD", "ELITE"};

	static
	{
		REGION_VARBITS.put("ARDOUGNE", new int[]{
			VarbitID.ARDOUGNE_DIARY_EASY_COMPLETE, VarbitID.ARDOUGNE_DIARY_MEDIUM_COMPLETE,
			VarbitID.ARDOUGNE_DIARY_HARD_COMPLETE, VarbitID.ARDOUGNE_DIARY_ELITE_COMPLETE});
		REGION_VARBITS.put("DESERT", new int[]{
			VarbitID.DESERT_DIARY_EASY_COMPLETE, VarbitID.DESERT_DIARY_MEDIUM_COMPLETE,
			VarbitID.DESERT_DIARY_HARD_COMPLETE, VarbitID.DESERT_DIARY_ELITE_COMPLETE});
		REGION_VARBITS.put("FALADOR", new int[]{
			VarbitID.FALADOR_DIARY_EASY_COMPLETE, VarbitID.FALADOR_DIARY_MEDIUM_COMPLETE,
			VarbitID.FALADOR_DIARY_HARD_COMPLETE, VarbitID.FALADOR_DIARY_ELITE_COMPLETE});
		REGION_VARBITS.put("FREMENNIK", new int[]{
			VarbitID.FREMENNIK_DIARY_EASY_COMPLETE, VarbitID.FREMENNIK_DIARY_MEDIUM_COMPLETE,
			VarbitID.FREMENNIK_DIARY_HARD_COMPLETE, VarbitID.FREMENNIK_DIARY_ELITE_COMPLETE});
		REGION_VARBITS.put("KANDARIN", new int[]{
			VarbitID.KANDARIN_DIARY_EASY_COMPLETE, VarbitID.KANDARIN_DIARY_MEDIUM_COMPLETE,
			VarbitID.KANDARIN_DIARY_HARD_COMPLETE, VarbitID.KANDARIN_DIARY_ELITE_COMPLETE});
		// Karamja's first three tiers predate the standard naming and are still
		// called ATJUN_* (the island's internal name) rather than KARAMJA_DIARY_*;
		// only the elite tier, added later, follows the usual pattern.
		REGION_VARBITS.put(KARAMJA, new int[]{
			VarbitID.ATJUN_EASY_DONE, VarbitID.ATJUN_MED_DONE,
			VarbitID.ATJUN_HARD_DONE, VarbitID.KARAMJA_DIARY_ELITE_COMPLETE});
		REGION_VARBITS.put("KOUREND", new int[]{
			VarbitID.KOUREND_DIARY_EASY_COMPLETE, VarbitID.KOUREND_DIARY_MEDIUM_COMPLETE,
			VarbitID.KOUREND_DIARY_HARD_COMPLETE, VarbitID.KOUREND_DIARY_ELITE_COMPLETE});
		REGION_VARBITS.put("LUMBRIDGE", new int[]{
			VarbitID.LUMBRIDGE_DIARY_EASY_COMPLETE, VarbitID.LUMBRIDGE_DIARY_MEDIUM_COMPLETE,
			VarbitID.LUMBRIDGE_DIARY_HARD_COMPLETE, VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE});
		REGION_VARBITS.put("MORYTANIA", new int[]{
			VarbitID.MORYTANIA_DIARY_EASY_COMPLETE, VarbitID.MORYTANIA_DIARY_MEDIUM_COMPLETE,
			VarbitID.MORYTANIA_DIARY_HARD_COMPLETE, VarbitID.MORYTANIA_DIARY_ELITE_COMPLETE});
		REGION_VARBITS.put("VARROCK", new int[]{
			VarbitID.VARROCK_DIARY_EASY_COMPLETE, VarbitID.VARROCK_DIARY_MEDIUM_COMPLETE,
			VarbitID.VARROCK_DIARY_HARD_COMPLETE, VarbitID.VARROCK_DIARY_ELITE_COMPLETE});
		REGION_VARBITS.put("WESTERN", new int[]{
			VarbitID.WESTERN_DIARY_EASY_COMPLETE, VarbitID.WESTERN_DIARY_MEDIUM_COMPLETE,
			VarbitID.WESTERN_DIARY_HARD_COMPLETE, VarbitID.WESTERN_DIARY_ELITE_COMPLETE});
		REGION_VARBITS.put("WILDERNESS", new int[]{
			VarbitID.WILDERNESS_DIARY_EASY_COMPLETE, VarbitID.WILDERNESS_DIARY_MEDIUM_COMPLETE,
			VarbitID.WILDERNESS_DIARY_HARD_COMPLETE, VarbitID.WILDERNESS_DIARY_ELITE_COMPLETE});
	}

	/**
	 * Populates completedDiaryTiers on the given state accumulator.
	 * Call once per refresh cycle from the plugin, on the client thread.
	 */
	public void readInto(Client client, com.nowwhat.model.PlayerState state)
	{
		for (Map.Entry<String, int[]> entry : REGION_VARBITS.entrySet())
		{
			String region = entry.getKey();
			int[] tiers = entry.getValue();

			for (int tier = EASY; tier <= ELITE; tier++)
			{
				if (isComplete(client.getVarbitValue(tiers[tier]), region, tier))
				{
					state.addCompletedDiaryTier(region + "_" + TIER_NAMES[tier]);
				}
			}
		}
	}

	private static boolean isComplete(int value, String region, int tier)
	{
		if (KARAMJA.equals(region) && tier != ELITE)
		{
			// Progress counter, not a flag: 1 means started, not finished.
			return value >= 2;
		}
		return value > 0;
	}
}
