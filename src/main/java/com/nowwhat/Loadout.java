package com.nowwhat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.EquipmentInventorySlot;

/**
 * A gear setup sorted into the slots it is worn in, so it can be drawn the way
 * the game draws it: helm on top, body under it, boots at the bottom.
 * <p>
 * The written setups list several options per slot, best first: "Head: Torva
 * full helm, Neitiznot faceguard, serpentine helm". A loadout can only wear
 * one helm at a time, so the options become steps. Step one wears the first
 * option in every slot - the best-in-slot kit. Each step after it wears the
 * next option down, and a slot that has run out of options keeps its last one,
 * so a player who has the Neitiznot faceguard but no Torva can step down until
 * the picture matches what is in their bank.
 * <p>
 * Lining options up by position is an approximation: the second helm and the
 * second body were not necessarily written as a pair, and in a hybrid kit the
 * second weapon is a switch rather than a downgrade. It is the same reading
 * the wiki's own gear tables invite, though, and every option for a slot is
 * still listed when the slot is hovered.
 */
final class Loadout
{
	private static final int WEAPON = EquipmentInventorySlot.WEAPON.getSlotIdx();
	private static final int SHIELD = EquipmentInventorySlot.SHIELD.getSlotIdx();

	/** Slot index to the items written for it, best first. */
	private final Map<Integer, List<Integer>> bySlot = new LinkedHashMap<>();
	private final Set<Integer> twoHanded = new HashSet<>();

	/** Adds an item to the end of its slot's options. Repeats are ignored. */
	void add(int itemId, int slot, boolean isTwoHanded)
	{
		List<Integer> options = bySlot.computeIfAbsent(slot, s -> new ArrayList<>());
		if (!options.contains(itemId))
		{
			options.add(itemId);
		}
		if (isTwoHanded)
		{
			twoHanded.add(itemId);
		}
	}

	/** How many different slots the setup fills. */
	int slotCount()
	{
		return bySlot.size();
	}

	/** How many steps there are from best in slot down to the last option listed. */
	int steps()
	{
		int steps = 0;
		for (List<Integer> options : bySlot.values())
		{
			steps = Math.max(steps, options.size());
		}
		return steps;
	}

	/** Every option written for a slot, best first; empty when the setup leaves it bare. */
	List<Integer> options(int slot)
	{
		List<Integer> options = bySlot.get(slot);
		return options == null ? Collections.emptyList() : options;
	}

	/**
	 * What the given step wears in a slot, or null for nothing.
	 * <p>
	 * The shield slot is left empty while the step's weapon is two-handed. A
	 * twisted bow and a twisted buckler cannot be worn together, and drawing
	 * them side by side would show a loadout the game refuses.
	 */
	Integer worn(int slot, int step)
	{
		if (slot == SHIELD && isBlockedByTwoHander(step))
		{
			return null;
		}
		List<Integer> options = bySlot.get(slot);
		if (options == null || options.isEmpty())
		{
			return null;
		}
		return options.get(Math.min(step, options.size() - 1));
	}

	/** Whether this step's weapon takes both hands, so the shield slot has to stay empty. */
	boolean isBlockedByTwoHander(int step)
	{
		List<Integer> weapons = bySlot.get(WEAPON);
		if (weapons == null || weapons.isEmpty())
		{
			return false;
		}
		return twoHanded.contains(weapons.get(Math.min(step, weapons.size() - 1)));
	}
}
