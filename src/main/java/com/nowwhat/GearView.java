package com.nowwhat;

import static com.nowwhat.PanelStyle.*;

import com.nowwhat.data.ItemNameIndex;
import com.nowwhat.data.ItemTextParser;
import com.nowwhat.model.Activity;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import net.runelite.client.ui.ColorScheme;
import net.runelite.http.api.item.ItemPrice;

/**
 * Gear and supplies as pictures: written lines matched to items, a row of
 * icons, and the worn-equipment loadout with its arrows.
 * <p>
 * This used to live inside the panel, which had grown to do the start screen,
 * the plan and all of this at once. It owns everything that needs RuneLite's
 * item index or the game thread, so the rest of the panel needs neither.
 */
@Slf4j
class GearView
{
	/** Makes one line of wrapped text the way the panel does, at the panel's current width. */
	interface Lines
	{
		JLabel line(String text, Color color, int indent);
	}

	/**
	 * RuneLite's item index and icon source, or null outside a client (the
	 * offline panel renders text only, which is also the fallback when a written
	 * gear line cannot be matched to an item).
	 */
	private final ItemManager itemManager;
	/**
	 * Resolved icons per written gear line. The lookup walks the whole item
	 * index, and a boss page re-renders on every click in the plan.
	 */
	private final Map<String, EntryIcons> iconCache = new HashMap<>();
	/**
	 * The game thread. RuneLite only answers "which slot is this worn in" from
	 * there, and the panel draws on Swing's thread. Null outside a client, where
	 * gear falls back to a plain row of icons.
	 */
	private ClientThread clientThread;
	/**
	 * Item id to the slot it is worn in, with TWO_HANDED added for weapons that
	 * take both hands, or NOT_WORN for anything that is not equipment. Filled on
	 * the game thread and read on Swing's, and never emptied: an item's slot
	 * does not change.
	 */
	private final Map<Integer, Integer> wornSlots = new ConcurrentHashMap<>();
	/** Ids waiting for the next slot lookup. Swing thread only. */
	private final Set<Integer> slotsToLookUp = new LinkedHashSet<>();
	private static final int NOT_WORN = -1;
	private static final int TWO_HANDED = 0x100;
	/** Where a setup drawn under its own name goes: the plan being built. */
	private final JPanel into;
	private final Lines lines;
	/** Draws the open plan again, once slots that were being looked up have arrived. */
	private final Runnable redraw;

	GearView(ItemManager itemManager, JPanel into, Lines lines, Runnable redraw)
	{
		this.itemManager = itemManager;
		this.into = into;
		this.lines = lines;
		this.redraw = redraw;
	}

	/** Lets gear be drawn as a worn-equipment loadout, which needs the game thread for slots. */
	void setClientThread(ClientThread clientThread)
	{
		this.clientThread = clientThread;
	}

	/**
	 * The worn-equipment tab, as the game lays it out: three columns, five rows,
	 * with the head and legs alone in the middle column. -1 is a gap.
	 */
	private static final int[][] EQUIPMENT_LAYOUT = {
		{-1, EquipmentInventorySlot.HEAD.getSlotIdx(), -1},
		{EquipmentInventorySlot.CAPE.getSlotIdx(), EquipmentInventorySlot.AMULET.getSlotIdx(),
			EquipmentInventorySlot.AMMO.getSlotIdx()},
		{EquipmentInventorySlot.WEAPON.getSlotIdx(), EquipmentInventorySlot.BODY.getSlotIdx(),
			EquipmentInventorySlot.SHIELD.getSlotIdx()},
		{-1, EquipmentInventorySlot.LEGS.getSlotIdx(), -1},
		{EquipmentInventorySlot.GLOVES.getSlotIdx(), EquipmentInventorySlot.BOOTS.getSlotIdx(),
			EquipmentInventorySlot.RING.getSlotIdx()},
	};
	/**
	 * Below this many slots the loadout is mostly empty boxes - a pickaxe and a
	 * pair of boots - and the plain row says the same thing in a fifth of the
	 * height.
	 */
	private static final int MIN_LOADOUT_SLOTS = 3;
	/** A 36x32 item sprite plus its 1px box. */
	private static final int SLOT_WIDTH = 38;
	private static final int SLOT_HEIGHT = 34;
	private static final int SLOT_GAP = 3;
	/** Wide enough for the step caption between its two arrows. */
	private static final int LOADOUT_WIDTH = 156;
	/** A shade under both the panel and the option cards, so the boxes read in either. */
	private static final Color SLOT_BG = new Color(0x1E, 0x1D, 0x1C);
	private static final Color SLOT_EDGE = new Color(0x45, 0x41, 0x3D);
	private static final Color ARROW_OFF = new Color(0x4A, 0x47, 0x44);
	private static final Font ARROW_FONT = installed(UI_FAMILIES, Font.BOLD, 18);

	/**
	 * Gear lines drawn as a loadout in the shape of the worn-equipment tab, with
	 * anything that is not worn - a rune pouch, a teleport - in the ordinary row
	 * beneath it. Returns what still has to be said in words.
	 * <p>
	 * Falls back to the plain row while RuneLite has not yet said which slot
	 * each item goes in, and for a kit too small to be worth the shape. The
	 * slots arrive a frame later and the plan is drawn again with them.
	 */
	Pictured stackGear(JPanel target, List<String> entries)
	{
		GearParts parts = gearParts(entries);
		if (parts == null)
		{
			Pictured flat = iconsFor(entries);
			stackIcons(target, flat);
			return flat;
		}
		stack(target, loadoutView(java.util.Collections.singletonList(null),
			java.util.Collections.singletonList(parts.loadout), parts.all.names, parts.all.counts));
		stackIcons(target, parts.rest);
		return parts.rest;
	}

	/** One gear setup split into what is worn and what is carried. */
	private static final class GearParts
	{
		/** Every item the lines named, worn or not: the tooltip names and counts. */
		private final Pictured all;
		private final Loadout loadout = new Loadout();
		/** The items that are not worn, and the lines that still need words. */
		private final Pictured rest = new Pictured();

		private GearParts(Pictured all)
		{
			this.all = all;
		}
	}

	/**
	 * Splits gear lines into a loadout and the rest, or null when they should
	 * stay a plain row: the slots are not known yet (they are being looked up),
	 * or the kit fills too few slots to be worth the shape.
	 */
	private GearParts gearParts(List<String> entries)
	{
		// Uncapped: the loadout has a box for every worn item, so the row's
		// three-line limit only applies to whatever is left over.
		Pictured all = iconsFor(entries, Integer.MAX_VALUE);
		if (!slotsKnown(all.names.keySet()))
		{
			return null;
		}
		GearParts parts = new GearParts(all);
		parts.rest.inWords.addAll(all.inWords);
		for (Map.Entry<Integer, String> item : all.names.entrySet())
		{
			int id = item.getKey();
			int slot = wornSlots.get(id);
			if (slot != NOT_WORN)
			{
				parts.loadout.add(id, slot & ~TWO_HANDED, (slot & TWO_HANDED) != 0);
			}
			else if (parts.rest.names.size() < MAX_ICONS)
			{
				parts.rest.names.put(id, item.getValue());
				if (all.counts.containsKey(id))
				{
					parts.rest.counts.put(id, all.counts.get(id));
				}
			}
			else
			{
				parts.rest.inWords.add(item.getValue());
			}
		}
		return parts.loadout.slotCount() < MIN_LOADOUT_SLOTS ? null : parts;
	}

	/**
	 * Draws a family of setups - "Melee (best in slot)", "Melee (mid)", "Melee
	 * (budget)" - as one loadout whose arrows walk from the first kit into the
	 * next, instead of three loadouts stacked on top of each other. The caption
	 * names the kit on show.
	 * <p>
	 * Returns false, having drawn nothing, when any one of them cannot be a
	 * loadout; the family is then drawn a setup at a time, as before.
	 */
	boolean renderSetupFamily(String base, List<Activity.GearSetup> written)
	{
		List<Activity.GearSetup> family = bestFirst(written);
		List<GearParts> kits = new ArrayList<>();
		for (Activity.GearSetup setup : family)
		{
			GearParts parts = gearParts(setup.getItems());
			if (parts == null)
			{
				return false;
			}
			kits.add(parts);
		}

		List<String> labels = new ArrayList<>();
		List<Loadout> loadouts = new ArrayList<>();
		Map<Integer, String> names = new LinkedHashMap<>();
		Map<Integer, String> counts = new LinkedHashMap<>();
		Pictured rest = new Pictured();
		List<String> words = new ArrayList<>();
		for (int i = 0; i < family.size(); i++)
		{
			GearParts parts = kits.get(i);
			String label = setupQualifier(family.get(i).getName());
			labels.add(label);
			loadouts.add(parts.loadout);
			parts.all.names.forEach(names::putIfAbsent);
			parts.all.counts.forEach(counts::putIfAbsent);
			for (Map.Entry<Integer, String> item : parts.rest.names.entrySet())
			{
				if (rest.names.size() < MAX_ICONS)
				{
					rest.names.putIfAbsent(item.getKey(), item.getValue());
				}
			}
			rest.counts.putAll(parts.rest.counts);
			if (!parts.rest.inWords.isEmpty())
			{
				// Said once per kit and labelled, since the loadout above only
				// shows one kit at a time and the words belong to a particular one.
				words.add(label + ": " + String.join(", ", parts.rest.inWords));
			}
		}

		JLabel name = lines.line(base, PRIMARY, 0);
		name.setBorder(new EmptyBorder(6, 0, 0, 0));
		stack(into, name);
		stack(into, loadoutView(labels, loadouts, names, counts));
		stackIcons(rest);
		for (String line : words)
		{
			JLabel items = lines.line(line, DETAIL, 12);
			items.setFont(SMALL_FONT);
			stack(into, items);
		}
		return true;
	}

	/**
	 * Setups grouped by the name before their bracket, in the order each name
	 * first appears: "Melee (best in slot)" and "Melee (budget)" are one
	 * family, keyed "Melee". A setup with no bracket is a family of its own.
	 */
	static Map<String, List<Activity.GearSetup>> setupFamilies(List<Activity.GearSetup> setups)
	{
		Map<String, List<Activity.GearSetup>> families = new LinkedHashMap<>();
		for (Activity.GearSetup setup : setups)
		{
			String name = setup.getName() == null ? "" : setup.getName().trim();
			int bracket = name.indexOf(" (");
			// Bracketed and bare names key differently, so a bare "Melee" never
			// swallows "Melee (budget)".
			String key = bracket > 0 && name.endsWith(")")
				? "family:" + name.substring(0, bracket)
				: "alone:" + name;
			families.computeIfAbsent(key, k -> new ArrayList<>()).add(setup);
		}
		return families;
	}

	/**
	 * A family's kits from the best down, so the loadout opens on the best one
	 * and the right arrow only ever steps down. The data does not always say
	 * them in that order: Brutus lists "Melee (free to play)" before "Melee
	 * (best in slot)", and the loadout opened on rune armour. Kits with any
	 * other name - "Blood Moon", "Dharok's" - sit between the top kit and the
	 * budget one, in the order written.
	 */
	static List<Activity.GearSetup> bestFirst(List<Activity.GearSetup> family)
	{
		List<Activity.GearSetup> sorted = new ArrayList<>(family);
		sorted.sort(java.util.Comparator.comparingInt(s -> tierOf(setupQualifier(s.getName()))));
		return sorted;
	}

	private static int tierOf(String qualifier)
	{
		String q = qualifier.toLowerCase(Locale.ROOT);
		if (q.contains("best in slot") || q.contains("best to worst"))
		{
			return 0;
		}
		if (q.contains("budget") || q.contains("cheap"))
		{
			return 2;
		}
		if (q.contains("free to play") || q.contains("low level") || q.contains("low risk")
			|| q.contains("learner"))
		{
			return 3;
		}
		return 1;
	}

	/** "Melee (best in slot)" to "Best in slot". */
	static String setupQualifier(String name)
	{
		int bracket = name.indexOf(" (");
		String inside = name.substring(bracket + 2, name.length() - 1).trim();
		return inside.isEmpty() ? name
			: Character.toUpperCase(inside.charAt(0)) + inside.substring(1);
	}

	/**
	 * Whether every one of these items has a known slot. Any that do not are
	 * queued for one lookup on the game thread, after the plan in progress has
	 * finished drawing, so a page of six setups costs one trip and one redraw.
	 */
	private boolean slotsKnown(Set<Integer> ids)
	{
		if (clientThread == null || itemManager == null)
		{
			return false;
		}
		boolean known = true;
		for (Integer id : ids)
		{
			if (wornSlots.containsKey(id))
			{
				continue;
			}
			known = false;
			if (slotsToLookUp.isEmpty())
			{
				SwingUtilities.invokeLater(this::lookUpSlots);
			}
			slotsToLookUp.add(id);
		}
		return known;
	}

	private void lookUpSlots()
	{
		List<Integer> ids = new ArrayList<>(slotsToLookUp);
		slotsToLookUp.clear();
		if (ids.isEmpty())
		{
			return;
		}
		clientThread.invoke(() ->
		{
			for (int id : ids)
			{
				wornSlots.put(id, slotOf(id));
			}
			SwingUtilities.invokeLater(() ->
			{
				// The player did not ask for this redraw; it only swaps a row
				// of icons for the loadout.
				redraw.run();
			});
		});
	}

	/** Game thread only. Any failure counts as "not worn", which keeps the plain row. */
	private int slotOf(int id)
	{
		try
		{
			ItemStats stats = itemManager.getItemStats(id);
			if (stats == null || !stats.isEquipable() || stats.getEquipment() == null)
			{
				return NOT_WORN;
			}
			ItemEquipmentStats equipment = stats.getEquipment();
			return equipment.getSlot() | (equipment.isTwoHanded() ? TWO_HANDED : 0);
		}
		catch (RuntimeException e)
		{
			log.debug("No equipment slot for item {}", id, e);
			return NOT_WORN;
		}
	}

	/**
	 * The loadout itself: a box per slot in the game's own arrangement, the best
	 * kit in it first, and arrows underneath that step down to the next option
	 * in every slot. Hovering a box lists all the options written for it.
	 */
	private JPanel loadoutView(List<String> labels, List<Loadout> loadouts,
		Map<Integer, String> names, Map<Integer, String> counts)
	{
		// Every step of every kit, in order: {kit, step within that kit}.
		List<int[]> walk = new ArrayList<>();
		for (int kit = 0; kit < loadouts.size(); kit++)
		{
			for (int inner = 0; inner < loadouts.get(kit).steps(); inner++)
			{
				walk.add(new int[]{kit, inner});
			}
		}

		JPanel grid = new JPanel(new java.awt.GridLayout(EQUIPMENT_LAYOUT.length, 3,
			SLOT_GAP, SLOT_GAP));
		grid.setOpaque(false);
		Dimension gridSize = new Dimension(3 * SLOT_WIDTH + 2 * SLOT_GAP,
			EQUIPMENT_LAYOUT.length * SLOT_HEIGHT + (EQUIPMENT_LAYOUT.length - 1) * SLOT_GAP);
		grid.setPreferredSize(gridSize);
		grid.setMaximumSize(gridSize);

		Map<Integer, JLabel> cells = new LinkedHashMap<>();
		for (int[] row : EQUIPMENT_LAYOUT)
		{
			for (int slot : row)
			{
				if (slot < 0)
				{
					grid.add(new JLabel());
					continue;
				}
				JLabel cell = new JLabel();
				cell.setHorizontalAlignment(JLabel.CENTER);
				cell.setOpaque(true);
				cell.setBackground(SLOT_BG);
				cell.setBorder(BorderFactory.createLineBorder(SLOT_EDGE));
				cells.put(slot, cell);
				grid.add(cell);
			}
		}

		JPanel gridRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 0, 0));
		gridRow.setOpaque(false);
		gridRow.add(grid);

		JPanel column = new JPanel(new BorderLayout(0, 2));
		column.setOpaque(false);
		column.add(gridRow, BorderLayout.CENTER);

		int steps = walk.size();
		int[] step = {0};
		// Two lines: which kit is on show, and where this is in the whole run.
		// "Budget 1/2" counted inside one kit only, so nothing said the arrows
		// went on past it into the next one.
		JLabel caption = new JLabel("", JLabel.CENTER);
		caption.setFont(SMALL_FONT);
		caption.setForeground(DETAIL);
		JLabel position = new JLabel("", JLabel.CENTER);
		position.setFont(MICRO_FONT);
		position.setForeground(SUBTLE);
		boolean named = labels.stream().anyMatch(java.util.Objects::nonNull);
		JLabel back = stepArrow("\u2039", "The option before, in every slot");
		JLabel forward = stepArrow("\u203a", "The next option down, in every slot");

		Runnable show = () ->
		{
			int kit = walk.get(step[0])[0];
			int at = walk.get(step[0])[1];
			Loadout loadout = loadouts.get(kit);
			for (Map.Entry<Integer, JLabel> cell : cells.entrySet())
			{
				int slot = cell.getKey();
				JLabel label = cell.getValue();
				Integer id = loadout.worn(slot, at);
				label.setIcon(null);
				if (id != null)
				{
					// Async: addTo sets the icon now and repaints when the
					// sprite arrives, so a fast click cannot leave a stale one.
					itemManager.getImage(id).addTo(label);
				}
				label.setToolTipText(slotTooltip(slot, loadout, at, names, counts));
			}
			// A count, not "cheaper": the setup's own name already says whether it
			// is the best-in-slot kit or the budget one, and in a hybrid kit the
			// second weapon is a switch rather than a downgrade.
			String count = (step[0] + 1) + " of " + steps;
			String label = labels.get(kit);
			caption.setText(named ? label : count);
			// The caption can be cut short in its 112px; the tooltip never is.
			caption.setToolTipText(named ? label : null);
			position.setText(named ? count : "");
			forward.setToolTipText(step[0] >= steps - 1 ? null
				: named && walk.get(step[0] + 1)[0] != kit
					? "Next: " + labels.get(walk.get(step[0] + 1)[0])
					: "The next option down, in every slot");
			back.setForeground(step[0] > 0 ? QUIET_LINK : ARROW_OFF);
			forward.setForeground(step[0] < steps - 1 ? QUIET_LINK : ARROW_OFF);
		};

		if (steps > 1)
		{
			whenClicked(back, () ->
			{
				if (step[0] > 0)
				{
					step[0]--;
					show.run();
				}
			}, () -> step[0] > 0);
			whenClicked(forward, () ->
			{
				if (step[0] < steps - 1)
				{
					step[0]++;
					show.run();
				}
			}, () -> step[0] < steps - 1);

			JPanel nav = new JPanel(new BorderLayout());
			nav.setOpaque(false);
			nav.add(back, BorderLayout.WEST);
			JPanel middle = new JPanel(new java.awt.GridLayout(named ? 2 : 1, 1));
			middle.setOpaque(false);
			middle.add(caption);
			if (named)
			{
				middle.add(position);
			}
			nav.add(middle, BorderLayout.CENTER);
			nav.add(forward, BorderLayout.EAST);
			column.add(nav, BorderLayout.SOUTH);
		}
		show.run();

		int height = column.getPreferredSize().height;
		Dimension columnSize = new Dimension(LOADOUT_WIDTH, height);
		column.setPreferredSize(columnSize);
		column.setMaximumSize(columnSize);
		column.setAlignmentX(Component.LEFT_ALIGNMENT);

		JPanel view = new JPanel();
		view.setLayout(new BoxLayout(view, BoxLayout.Y_AXIS));
		view.setOpaque(false);
		view.setBorder(new EmptyBorder(4, 12, 2, 0));
		view.add(column);
		return view;
	}

	private JLabel stepArrow(String glyph, String tooltip)
	{
		JLabel arrow = new JLabel(glyph, JLabel.CENTER);
		arrow.setFont(ARROW_FONT);
		arrow.setToolTipText(tooltip);
		arrow.setPreferredSize(new Dimension(22, 22));
		return arrow;
	}

	/**
	 * Hand cursor and an orange hover while the arrow has somewhere to go.
	 * makeClickable cannot do this: it restores one fixed colour on exit, and an
	 * arrow at the end of the steps has to go back to looking switched off.
	 */
	private static void whenClicked(JLabel arrow, Runnable onClick,
		java.util.function.BooleanSupplier enabled)
	{
		arrow.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
		arrow.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				onClick.run();
				// The click may have just switched this arrow off.
				arrow.setForeground(enabled.getAsBoolean() ? ColorScheme.BRAND_ORANGE : ARROW_OFF);
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				if (enabled.getAsBoolean())
				{
					arrow.setForeground(ColorScheme.BRAND_ORANGE);
				}
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				arrow.setForeground(enabled.getAsBoolean() ? QUIET_LINK : ARROW_OFF);
			}
		});
	}

	/** The slot's name, then every option for it best first, with the one shown in bold. */
	private static String slotTooltip(int slot, Loadout loadout, int step,
		Map<Integer, String> names, Map<Integer, String> counts)
	{
		StringBuilder tip = new StringBuilder("<html><b>").append(slotName(slot)).append("</b>");
		Integer worn = loadout.worn(slot, step);
		List<Integer> options = loadout.options(slot);
		if (options.isEmpty())
		{
			return tip.append("<br>Nothing listed for this slot</html>").toString();
		}
		if (worn == null)
		{
			tip.append("<br><i>Empty: the weapon takes both hands</i>");
		}
		for (int id : options)
		{
			String name = escapeHtml(names.getOrDefault(id, ""));
			String count = counts.get(id);
			if (count != null)
			{
				name = escapeHtml(count) + " \u00d7 " + name;
			}
			tip.append("<br>").append(Integer.valueOf(id).equals(worn) ? "<b>" + name + "</b>" : name);
		}
		return tip.append("</html>").toString();
	}

	private static String slotName(int slot)
	{
		for (EquipmentInventorySlot each : EquipmentInventorySlot.values())
		{
			if (each.getSlotIdx() == slot)
			{
				String name = each.name().toLowerCase(Locale.ROOT);
				return Character.toUpperCase(name.charAt(0)) + name.substring(1);
			}
		}
		return "Slot";
	}

	private static String escapeHtml(String text)
	{
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	/** One setup under its own name. */
	void renderSetup(Activity.GearSetup setup)
	{
		JLabel name = lines.line(setup.getName(), PRIMARY, 0);
		name.setBorder(new EmptyBorder(6, 0, 0, 0));
		stack(into, name);

		// Pictures instead of the words. A loadout is something players
		// recognise by sight, and nine item names in a row is work the panel
		// can do for them. The words stay only for an item that could not be
		// matched to a picture; a condition or caveat moves to the hover.
		Pictured gear = stackGear(into, setup.getItems());

		if (!gear.getInWords().isEmpty())
		{
			JLabel items = lines.line(String.join(", ", gear.getInWords()), DETAIL, 12);
			items.setFont(SMALL_FONT);
			stack(into, items);
		}
	}

	/** At 240px wide, six 36px icons and their gaps is what fits inside the gutter. */
	private static final int ICON_COLUMNS = 6;
	/** Three rows. Past that the pictures stop being a glance and start being a page. */
	static final int MAX_ICONS = ICON_COLUMNS * 3;

	/** What one written line turned into. */
	private static final class EntryIcons
	{
		/** Item id to item name, for the tooltip. */
		private final Map<Integer, String> names = new LinkedHashMap<>();
		/** Item id to the count the line asked for, where it named one. */
		private final Map<Integer, String> counts = new LinkedHashMap<>();
		/** True when every option the line named was matched to a picture. */
		private boolean complete;
	}

	/** Several lines' worth: the pictures, and whatever still has to be read. */
	static final class Pictured
	{
		final Map<Integer, String> names = new LinkedHashMap<>();
		final Map<Integer, String> counts = new LinkedHashMap<>();
		final List<String> inWords = new ArrayList<>();

		List<String> getInWords()
		{
			return inWords;
		}
	}

	/**
	 * The items a set of written gear or supply lines points at, in the order
	 * they were written, and the lines that still need their words.
	 * <p>
	 * Lines are resolved one at a time rather than joined, because a line is the
	 * unit that carries a slot - "Neck: occult then fury" only means amulets
	 * while it is still its own sentence.
	 */
	Pictured iconsFor(List<String> entries)
	{
		return iconsFor(entries, MAX_ICONS);
	}

	private Pictured iconsFor(List<String> entries, int cap)
	{
		Pictured pictured = new Pictured();
		for (String entry : entries)
		{
			EntryIcons icons = itemManager == null ? null : iconsForEntry(entry);
			// Past the cap the rest of the lines go back to being words, which is
			// the honest fallback: a fourth row of icons would be a page, and
			// dropping them silently would lose them.
			if (icons == null || icons.names.isEmpty() || pictured.names.size() >= cap)
			{
				pictured.inWords.add(entry);
				continue;
			}
			for (Map.Entry<Integer, String> item : icons.names.entrySet())
			{
				pictured.names.putIfAbsent(item.getKey(), item.getValue());
			}
			pictured.counts.putAll(icons.counts);
			if (!icons.complete)
			{
				pictured.inWords.add(entry);
			}
		}
		return pictured;
	}

	/**
	 * The one item a single written line means, or null when it cannot be
	 * pictured - no client to ask, or nothing matched.
	 */
	Integer firstItemFor(String entry)
	{
		EntryIcons icons = itemManager == null ? null : iconsForEntry(entry);
		return icons == null || icons.names.isEmpty() ? null : icons.names.keySet().iterator().next();
	}

	private EntryIcons iconsForEntry(String entry)
	{
		EntryIcons cached = iconCache.get(entry);
		if (cached != null)
		{
			return cached;
		}

		EntryIcons icons = new EntryIcons();
		String note = ItemTextParser.explanation(entry);
		// One option can expand into several phrases (a set names several slots),
		// and matching any one of them is enough for that option to be pictured.
		Map<String, Boolean> optionMatched = new LinkedHashMap<>();

		for (ItemTextParser.Phrase phrase : ItemTextParser.phrases(entry))
		{
			String term = ItemTextParser.searchTerm(phrase);
			List<ItemTextParser.Candidate> tradeable = new ArrayList<>();
			for (ItemPrice price : itemManager.search(term))
			{
				tradeable.add(new ItemTextParser.Candidate(price.getName(), price.getId()));
			}
			// The bundled table decides which item a phrase means. RuneLite's
			// search only covers tradeable items, so for anything untradeable it
			// offers the nearest tradeable relative instead: "avernic defender"
			// finds the hilt, "dragon defender" the ornament kit, "scythe of
			// vitur" the uncharged scythe. The table was built from every item
			// in the game, so it holds the real one. The tradeable search is
			// only the fallback for a phrase the table cannot place, and the
			// source of the properly capitalised name for the tooltip.
			List<ItemTextParser.Candidate> candidates = ItemNameIndex.search(term);
			Integer id = ItemTextParser.pick(phrase, candidates);
			if (id == null)
			{
				candidates = tradeable;
				id = ItemTextParser.pick(phrase, candidates);
			}
			optionMatched.merge(phrase.getText(), id != null, (a, b) -> a || b);
			if (id == null)
			{
				continue;
			}
			// What the line said beyond the item goes on the hover, not under
			// the pictures. A stand-in ("god cape" drawn as the Saradomin one)
			// says so there too, so "any god's" does not quietly become
			// "Saradomin's".
			String name = displayName(id, candidates, tradeable);
			if (ItemTextParser.namesAStandIn(phrase.getText()))
			{
				name += " (any version works)";
			}
			if (note != null)
			{
				name += " - " + note;
			}
			icons.names.putIfAbsent(id, name);
			if (phrase.getQuantity() != null)
			{
				icons.counts.putIfAbsent(id, phrase.getQuantity());
			}
		}

		// Words stay under the pictures only for an option that could not be
		// drawn at all. They used to stay for any line with a reason or a
		// stand-in on it as well, which printed "imbued god cape" directly
		// under a picture of one.
		icons.complete = !optionMatched.isEmpty()
			&& !optionMatched.containsValue(Boolean.FALSE);
		iconCache.put(entry, icons);
		return icons;
	}

	/**
	 * The tooltip name for an item. The table's names come from item constants
	 * and have lost their capitals and apostrophes ("tumekens shadow"), so the
	 * game's own spelling is used wherever the tradeable search has it: the same
	 * item, or a tradeable twin with the same name under another id.
	 */
	static String displayName(int id, List<ItemTextParser.Candidate> chosenFrom,
		List<ItemTextParser.Candidate> tradeable)
	{
		String name = null;
		for (ItemTextParser.Candidate candidate : chosenFrom)
		{
			if (candidate.getId() == id)
			{
				name = candidate.getName();
				break;
			}
		}
		if (name == null)
		{
			return "";
		}
		String flat = ItemNameIndex.flatten(name);
		for (ItemTextParser.Candidate candidate : tradeable)
		{
			if (candidate.getId() == id || ItemNameIndex.flatten(candidate.getName()).equals(flat))
			{
				return candidate.getName();
			}
		}
		return Character.toUpperCase(name.charAt(0)) + name.substring(1);
	}

	/**
	 * A strip of item icons, six to a row, each one hovering to its item name and
	 * captioned with the count its line asked for.
	 * <p>
	 * A fixed grid rather than a wrapping flow: FlowLayout inside a vertical
	 * BoxLayout reports the height of a single row however many rows it draws,
	 * so the strip would be clipped to its first six icons.
	 */
	private void stackIcons(Pictured pictured)
	{
		stackIcons(into, pictured);
	}

	void stackIcons(JPanel target, Pictured pictured)
	{
		if (pictured.names.isEmpty())
		{
			return;
		}
		int rows = (pictured.names.size() + ICON_COLUMNS - 1) / ICON_COLUMNS;
		boolean counted = !pictured.counts.isEmpty();
		JPanel grid = new JPanel(new java.awt.GridLayout(rows, ICON_COLUMNS, 2, 2));
		grid.setOpaque(false);
		grid.setAlignmentX(Component.LEFT_ALIGNMENT);
		grid.setBorder(new EmptyBorder(4, 12, 2, 0));

		for (Map.Entry<Integer, String> item : pictured.names.entrySet())
		{
			String count = pictured.counts.get(item.getKey());
			JLabel icon = new JLabel(count == null ? "" : count);
			icon.setToolTipText(item.getValue());
			icon.setHorizontalAlignment(JLabel.CENTER);
			icon.setHorizontalTextPosition(JLabel.CENTER);
			icon.setVerticalTextPosition(JLabel.BOTTOM);
			icon.setFont(MICRO_FONT);
			icon.setForeground(SUBTLE);
			// Async: the sprite may not be cached yet, and addTo repaints the
			// label once it arrives.
			itemManager.getImage(item.getKey()).addTo(icon);
			grid.add(icon);
		}
		// GridLayout stretches its cells to fill the row, so the last row is
		// padded rather than left to spread four icons across the full width.
		for (int i = pictured.names.size(); i < rows * ICON_COLUMNS; i++)
		{
			grid.add(new JLabel());
		}
		grid.setMaximumSize(new Dimension(Integer.MAX_VALUE,
			rows * (counted ? 44 : 34) + 8));
		stack(target, grid);
	}
}
