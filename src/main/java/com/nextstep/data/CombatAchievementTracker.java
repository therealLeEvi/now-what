package com.nextstep.data;

import net.runelite.api.Client;
import net.runelite.api.gameval.VarbitID;

/**
 * Reads Combat Achievement tier completion out of varbits.
 *
 * <p>Unlike the collection log, this needs no interface scraping and no
 * snapshotting: the game keeps one varbit per tier and the client has them
 * for the whole session, so tier completion is simply readable whenever the
 * player is logged in.
 *
 * <p>The tier varbits are progress counters rather than plain flags - 0 is
 * not started, 1 is in progress, 2 is complete - which is the same shape as
 * the Karamja diary varbits. If a completed tier ever fails to register, that
 * threshold is the first thing to check with the Var Inspector in developer
 * mode.
 *
 * <p>Keys written here are {@code CA_<TIER>}, matching both the activity ids
 * in activities.json and the values a {@code COMBAT_ACHIEVEMENT_TIER}
 * requirement uses, so "have I done this tier" and "does this need that tier"
 * agree on one spelling.
 */
public class CombatAchievementTracker
{
	private static final int COMPLETE = 2;

	private static final String[] TIERS = {
		"EASY", "MEDIUM", "HARD", "ELITE", "MASTER", "GRANDMASTER"
	};

	private static final int[] TIER_VARBITS = {
		VarbitID.CA_TIER_STATUS_EASY,
		VarbitID.CA_TIER_STATUS_MEDIUM,
		VarbitID.CA_TIER_STATUS_HARD,
		VarbitID.CA_TIER_STATUS_ELITE,
		VarbitID.CA_TIER_STATUS_MASTER,
		VarbitID.CA_TIER_STATUS_GRANDMASTER,
	};

	/**
	 * Populates completed Combat Achievement tiers on the state accumulator.
	 * Call once per refresh cycle from the plugin, on the client thread.
	 */
	public void readInto(Client client, com.nextstep.model.PlayerState state)
	{
		for (int i = 0; i < TIERS.length; i++)
		{
			if (client.getVarbitValue(TIER_VARBITS[i]) >= COMPLETE)
			{
				state.addCompletedCombatAchievementTier("CA_" + TIERS[i]);
			}
		}

		// The running total every tier threshold is measured against. Read
		// alongside the tier flags so "how far am I from Master" can be
		// answered with a number rather than a wiki link. If the panel ever
		// shows a total that disagrees with the in-game interface, check
		// this varbit in the Var Inspector first.
		state.setCombatAchievementPoints(client.getVarbitValue(VarbitID.CA_POINTS));
	}
}
