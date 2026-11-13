package com.nowwhat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import net.runelite.api.EquipmentInventorySlot;
import org.junit.Test;

public class LoadoutTest
{
	private static final int HEAD = EquipmentInventorySlot.HEAD.getSlotIdx();
	private static final int BODY = EquipmentInventorySlot.BODY.getSlotIdx();
	private static final int WEAPON = EquipmentInventorySlot.WEAPON.getSlotIdx();
	private static final int SHIELD = EquipmentInventorySlot.SHIELD.getSlotIdx();
	private static final int RING = EquipmentInventorySlot.RING.getSlotIdx();

	@Test
	public void firstStepWearsTheFirstOptionInEverySlot()
	{
		Loadout loadout = new Loadout();
		loadout.add(1, HEAD, false);
		loadout.add(2, HEAD, false);
		loadout.add(3, HEAD, false);
		loadout.add(10, BODY, false);
		loadout.add(11, BODY, false);

		assertEquals(3, loadout.steps());
		assertEquals(2, loadout.slotCount());
		assertEquals(Integer.valueOf(1), loadout.worn(HEAD, 0));
		assertEquals(Integer.valueOf(10), loadout.worn(BODY, 0));
	}

	@Test
	public void aSlotThatRunsOutOfOptionsKeepsItsLastOne()
	{
		Loadout loadout = new Loadout();
		loadout.add(1, HEAD, false);
		loadout.add(2, HEAD, false);
		loadout.add(3, HEAD, false);
		loadout.add(20, RING, false);

		assertEquals(Integer.valueOf(3), loadout.worn(HEAD, 2));
		assertEquals(Integer.valueOf(20), loadout.worn(RING, 2));
	}

	@Test
	public void anEmptySlotWearsNothing()
	{
		Loadout loadout = new Loadout();
		loadout.add(1, HEAD, false);

		assertNull(loadout.worn(BODY, 0));
		assertTrue(loadout.options(BODY).isEmpty());
	}

	@Test
	public void repeatsAreListedOnce()
	{
		Loadout loadout = new Loadout();
		loadout.add(1, HEAD, false);
		loadout.add(1, HEAD, false);
		loadout.add(2, HEAD, false);

		assertEquals(Arrays.asList(1, 2), loadout.options(HEAD));
	}

	@Test
	public void aTwoHandedWeaponLeavesTheShieldSlotEmpty()
	{
		// Twisted bow, then a crossbow that does leave a hand free.
		Loadout loadout = new Loadout();
		loadout.add(100, WEAPON, true);
		loadout.add(101, WEAPON, false);
		loadout.add(200, SHIELD, false);
		loadout.add(201, SHIELD, false);

		assertTrue(loadout.isBlockedByTwoHander(0));
		assertNull(loadout.worn(SHIELD, 0));
		assertEquals(Integer.valueOf(201), loadout.worn(SHIELD, 1));
		// The shield is still listed for hovering, just not drawn as worn.
		assertEquals(Arrays.asList(200, 201), loadout.options(SHIELD));
	}
}
