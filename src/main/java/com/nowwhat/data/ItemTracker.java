package com.nowwhat.data;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.config.ConfigManager;

/**
 * Tracks what the player actually owns: worn equipment, inventory, and bank.
 *
 * <p>This is what lets the planner answer "do you already have this" instead
 * of assuming you own nothing, and it is also the only way to know what gear
 * someone is working with - which matters because a training rate or a boss
 * recommendation is meaningless if the player can't equip the kit it assumes.
 *
 * <p>Three containers, with genuinely different availability, and the
 * difference is worth understanding rather than papering over:
 *
 * <ul>
 *   <li><b>Equipment and inventory</b> are pushed by the client continuously
 *       while logged in. These are always current.</li>
 *   <li><b>The bank</b> is only sent to the client when the player opens it.
 *       Until then there is nothing to read - not a RuneLite limitation, the
 *       data simply isn't client-side. So the bank is a <em>snapshot</em>
 *       from the last time it was opened, persisted to config so it survives
 *       logout instead of resetting to "you own nothing" every login - one
 *       snapshot per account, so two accounts never see each other's bank.</li>
 * </ul>
 *
 * <p>Because of that, {@link #hasBankSnapshot()} exists: a plan built with no
 * bank data should say so rather than quietly claiming the player needs to
 * acquire things they may already have stacked in their bank.
 */
@Slf4j
@Singleton
public class ItemTracker
{
	private static final String CONFIG_GROUP = "nowwhat";
	private static final String BANK_KEY = "bankSnapshot";

	private final ConfigManager configManager;

	private final Map<Integer, Integer> bank = new HashMap<>();
	private final Map<Integer, Integer> inventory = new HashMap<>();
	private final Map<Integer, Integer> equipment = new HashMap<>();

	/** The account the bank in memory belongs to, or null before anyone has logged in. */
	private String loadedFor;

	@Inject
	public ItemTracker(ConfigManager configManager)
	{
		this.configManager = configManager;
	}

	/**
	 * Restores the bank snapshot of whoever is logged in. Safe to call as often
	 * as you like: it only does anything when the account has changed.
	 * <p>
	 * The snapshot is kept per account. It used to be one setting for the whole
	 * client, so a second account was planned against the first one's bank and
	 * told it already owned things it had never seen. Inventory and gear are
	 * dropped on a change too; the plugin reads those from the client again on
	 * its next refresh.
	 */
	public void load()
	{
		String account = configManager.getRSProfileKey();
		if (account == null || account.equals(loadedFor))
		{
			return;
		}
		loadedFor = account;
		bank.clear();
		inventory.clear();
		equipment.clear();

		String raw = configManager.getRSProfileConfiguration(CONFIG_GROUP, BANK_KEY);
		if (raw == null || raw.isEmpty())
		{
			// A snapshot saved before they were kept per account. It belongs to
			// whoever opened a bank last, which for nearly everyone is the one
			// account they play; handed to the first account that logs in and
			// then removed, so it can never reach a second one.
			raw = configManager.getConfiguration(CONFIG_GROUP, BANK_KEY);
			if (raw != null)
			{
				configManager.unsetConfiguration(CONFIG_GROUP, BANK_KEY);
				if (!raw.isEmpty())
				{
					configManager.setRSProfileConfiguration(CONFIG_GROUP, BANK_KEY, raw);
				}
			}
		}
		if (raw == null || raw.isEmpty())
		{
			return;
		}

		try
		{
			for (String pair : raw.split(","))
			{
				int split = pair.indexOf(':');
				if (split > 0)
				{
					bank.put(Integer.parseInt(pair.substring(0, split)),
						Integer.parseInt(pair.substring(split + 1)));
				}
			}
			log.debug("Restored bank snapshot of {} distinct items", bank.size());
		}
		catch (NumberFormatException e)
		{
			// A corrupt snapshot is not worth failing over; drop it and let the
			// next bank visit rebuild it.
			log.warn("Discarding unreadable bank snapshot", e);
			bank.clear();
		}
	}

	/**
	 * Feeds one container update in. Returns true if this was a container we
	 * care about, so the caller can decide whether to trigger a refresh.
	 */
	public boolean onContainerChanged(int containerId, ItemContainer container)
	{
		switch (containerId)
		{
			case InventoryID.BANK:
				snapshot(container, bank);
				persistBank();
				return true;
			case InventoryID.INV:
				snapshot(container, inventory);
				return true;
			case InventoryID.WORN:
				snapshot(container, equipment);
				return true;
			default:
				return false;
		}
	}

	private static void snapshot(ItemContainer container, Map<Integer, Integer> into)
	{
		into.clear();
		if (container == null)
		{
			return;
		}
		for (Item item : container.getItems())
		{
			if (item.getId() > 0 && item.getQuantity() > 0)
			{
				into.merge(item.getId(), item.getQuantity(), Integer::sum);
			}
		}
	}

	private void persistBank()
	{
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<Integer, Integer> e : bank.entrySet())
		{
			if (sb.length() > 0)
			{
				sb.append(',');
			}
			sb.append(e.getKey()).append(':').append(e.getValue());
		}
		configManager.setRSProfileConfiguration(CONFIG_GROUP, BANK_KEY, sb.toString());
	}

	/** Copies everything owned into the state accumulator. */
	public void writeInto(com.nowwhat.model.PlayerState state)
	{
		Map<Integer, Integer> total = new HashMap<>(bank);
		inventory.forEach((id, qty) -> total.merge(id, qty, Integer::sum));
		equipment.forEach((id, qty) -> total.merge(id, qty, Integer::sum));

		state.setOwnedItems(total);
		state.setEquippedItems(new LinkedHashSet<>(equipment.keySet()));
		state.setBankSnapshotAvailable(hasBankSnapshot());
	}

	public boolean hasBankSnapshot()
	{
		return !bank.isEmpty();
	}

	public Set<Integer> getEquippedItemIds()
	{
		return new LinkedHashSet<>(equipment.keySet());
	}
}
