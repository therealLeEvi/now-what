package com.nextstep;

import com.nextstep.data.ItemNameIndex;
import com.nextstep.data.NavButtonLookup;
import com.nextstep.data.PluginLookup;
import com.nextstep.data.ItemTextParser;
import com.nextstep.engine.XpTable;
import com.nextstep.model.Activity;
import com.nextstep.model.Goal;
import com.nextstep.model.ItemSource;
import com.nextstep.model.Path;
import com.nextstep.model.PathStep;
import com.nextstep.model.TrainingMethod;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.Scrollable;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.game.ItemManager;
import net.runelite.http.api.item.ItemPrice;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.LinkBrowser;

@Slf4j
public class NextStepPanel extends PluginPanel
{
	// Matches the skill name strings NextStepPlugin writes into PlayerState
	// (Skill.name() from RuneLite's Skill enum) - kept as a plain constant
	// here so the panel doesn't need a Client reference just to populate a dropdown.
	private static final String[] SKILLS = {
		"ATTACK", "DEFENCE", "STRENGTH", "HITPOINTS", "RANGED", "PRAYER", "MAGIC",
		"COOKING", "WOODCUTTING", "FLETCHING", "FISHING", "FIREMAKING", "CRAFTING",
		"SMITHING", "MINING", "HERBLORE", "AGILITY", "THIEVING", "SLAYER", "FARMING",
		"RUNECRAFT", "HUNTER", "CONSTRUCTION"
	};

	// The sidebar is a fixed 225px wide and never gets wider, so every piece of
	// text has to be told what width to wrap at - Swing will happily report a
	// 600px preferred width for a long label and drag the whole panel sideways.
	private static final int CONTENT_WIDTH = PluginPanel.PANEL_WIDTH - (2 * PluginPanel.BORDER_OFFSET);
	// Fallback only, for the first render before the viewport has a real width.
	private static final int TEXT_WIDTH = CONTENT_WIDTH - PluginPanel.SCROLLBAR_WIDTH;

	private static final Color SUBTLE = new Color(0xAD, 0xA6, 0x9C);
	// Detail text sits under every option, so it is the most-read text in the
	// panel and was the least legible: 0x808080 pixel-font on a dark background.
	private static final Color DETAIL = new Color(0xC6, 0xC0, 0xB5);
	private static final Color CHOSEN = new Color(0x86, 0xC9, 0x8A);
	/**
	 * Text, instead of pure white. White at 13px against this grey is the glare
	 * the panel was accused of being flat by - a warm off-white carries the same
	 * weight without the hard edge, and leaves somewhere above the body text for
	 * the eye to go.
	 */
	private static final Color PRIMARY = new Color(0xF1, 0xED, 0xE6);
	/**
	 * Links and the "show N others" controls. Kept above the note grey rather
	 * than below it: a control that reads fainter than the prose around it looks
	 * disabled.
	 */
	private static final Color QUIET_LINK = new Color(0xB9, 0xB2, 0xA8);
	/**
	 * Softer than Color.CYAN, which was the loudest thing in the panel and sat
	 * above the content rather than beside it.
	 */
	private static final Color ACCENT = new Color(0x8F, 0xD3, 0xE0);
	/** Caution, for the Wilderness tag. Amber over red: it survives colourblindness. */
	private static final Color WARN = new Color(0xE3, 0xA8, 0x55);
	private static final Color HAIRLINE = new Color(0x35, 0x33, 0x31);
	/**
	 * The things you can click sit in a box of their own.
	 * <p>
	 * Everything in this panel used to be the same flat column of text, and the
	 * only thing marking a method you could pick out from the sentence
	 * describing it was a 12px indent. A fill one step off the background and a
	 * 1px edge is enough to say "this is a thing, and it is one of several"
	 * without the panel turning into a stack of widgets.
	 */
	private static final Color OPTION_BG = new Color(0x2F, 0x2D, 0x2B);
	private static final Color OPTION_EDGE = new Color(0x3D, 0x3A, 0x37);
	/** Card padding (9px each side) plus its two 1px edges. */
	private static final int CARD_INSET = 20;

	/** The plugin that actually walks you through a quest, as its Plugin Hub descriptor names it. */
	private static final String QUEST_HELPER = "Quest Helper";
	private static final String PLUGIN_HUB_URL = "https://runelite.net/plugin-hub/";
	private static final String WIKI_URL = "https://oldschool.runescape.wiki/";
	private static final String LICENCE_URL = "https://creativecommons.org/licenses/by-nc-sa/3.0/";

	// RuneLite's bundled RuneScape font is a pixel face designed for the game
	// viewport; at sidebar size, for paragraphs rather than labels, it smears.
	// The plan uses two installed faces instead: a serif for the things you
	// navigate by - the goal, each step, each method - and the platform UI sans
	// for everything you read. One font doing both jobs at 11, 12 and 13px with
	// nothing between plain and bold is what made the panel read flat.
	private static final String[] TITLE_FAMILIES = {"Georgia", "Noto Serif", "DejaVu Serif",
		Font.SERIF};
	private static final String[] UI_FAMILIES = {"Segoe UI", "Noto Sans", "DejaVu Sans",
		Font.SANS_SERIF};
	/** Windows ships this as its own family; elsewhere bold stands in for it. */
	private static final String[] UI_MEDIUM_FAMILIES = {"Segoe UI Semibold", "Noto Sans Medium",
		"DejaVu Sans"};

	private static final Font BODY_FONT = installed(UI_FAMILIES, Font.PLAIN, 12);
	private static final Font BODY_BOLD = BODY_FONT.deriveFont(Font.BOLD);
	/**
	 * The smallest prose in the panel. 12px, not 11: a line of 11px sans at this
	 * grey is the one thing in the sidebar that genuinely is hard to read, and
	 * the pixel saved by shrinking it buys nothing.
	 */
	private static final Font SMALL_FONT = installed(UI_FAMILIES, Font.PLAIN, 12);
	/** A step title, a method name, a section heading: the serif voice. */
	private static final Font STEP_TITLE_FONT = installed(TITLE_FAMILIES, Font.PLAIN, 16);
	private static final Font OPTION_FONT = installed(TITLE_FAMILIES, Font.PLAIN, 14);
	private static final Font HEADER_FONT = installed(TITLE_FAMILIES, Font.PLAIN, 15);
	/**
	 * The little uppercase label over each step. Letter-spacing is what stops
	 * all-caps at 10px reading as a smudge - and the extra weight is what stops
	 * it reading as a smudge in grey, which tracking alone could not fix.
	 */
	private static final Font MICRO_FONT = microFont();

	private static Font microFont()
	{
		Map<java.awt.font.TextAttribute, Object> attrs = new HashMap<>();
		attrs.put(java.awt.font.TextAttribute.TRACKING, 0.1);
		Font base = installed(UI_MEDIUM_FAMILIES, Font.PLAIN, 10);
		if (base.getFamily().equals(Font.SANS_SERIF) || base.getFamily().equals("DejaVu Sans"))
		{
			base = base.deriveFont(Font.BOLD);
		}
		return base.deriveFont(attrs);
	}

	/**
	 * The first of these families the machine actually has, so a face can be
	 * named without the panel falling back to a logical font on a box that has
	 * something better.
	 * <p>
	 * Java silently substitutes for an unknown family rather than failing, and
	 * the substitute is Dialog - which is how a panel ends up looking the same
	 * everywhere regardless of what it asked for.
	 */
	private static Font installed(String[] families, int style, int size)
	{
		Set<String> available = availableFamilies();
		for (String family : families)
		{
			if (available.contains(family.toLowerCase(java.util.Locale.ROOT)))
			{
				return new Font(family, style, size);
			}
		}
		return new Font(families[families.length - 1], style, size);
	}

	private static Set<String> installedFamilies;

	private static Set<String> availableFamilies()
	{
		if (installedFamilies == null)
		{
			installedFamilies = new HashSet<>();
			try
			{
				for (String family : java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
					.getAvailableFontFamilyNames())
				{
					installedFamilies.add(family.toLowerCase(java.util.Locale.ROOT));
				}
			}
			catch (Throwable headlessOrNoFonts)
			{
				// Nothing to do but take the logical fonts.
			}
		}
		return installedFamilies;
	}

	private final Function<Goal, Path> pathResolver;
	/** Used only to ask whether a named helper plugin is present; null outside a real client. */
	private final PluginManager pluginManager;
	/** Activities by id, so a finished plan can look up what to wear and bring. */
	private final Map<String, Activity> activitiesById = new HashMap<>();
	/** The scrolling column everything is stacked into. */
	private JPanel content;
	/** The measured gap that holds the attribution at the foot of the panel. */
	private final JPanel footerSpacer = new JPanel();
	/**
	 * RuneLite's sidebar, for opening another plugin's panel. Handed over after
	 * construction so every existing caller - and every test - still builds a
	 * panel without one.
	 */
	private ClientToolbar clientToolbar;
	private final Map<String, ItemSource> itemSourcesById = new HashMap<>();

	private final JPanel availableNowContainer = new JPanel();
	private final JPanel almostThereContainer = new JPanel();
	private final JPanel pathResultContainer = new JPanel();
	private final JScrollPane scrollPane;
	private final JComboBox<Activity> activityCombo;
	private final JComboBox<String> skillCombo;
	private final JSpinner skillLevelSpinner;
	private final SearchPicker<ItemSource> itemPicker;
	private final JSpinner baseStatsSpinner;
	private final JSpinner totalLevelSpinner;
	private final JSpinner itemQuantitySpinner;
	/**
	 * Which of the two ways in the panel is open.
	 * <p>
	 * The sidebar used to open straight onto three dropdowns, which answers
	 * "what do I want" and has nothing to say to the more common "I don't know
	 * what to do tonight". START is a fork between the two.
	 */
	private enum Mode
	{
		START,
		CHOOSE,
		RANDOM
	}

	private Mode mode = Mode.START;
	/** The fork. */
	private final JPanel startContainer = new JPanel();
	/** The three goal pickers and the quest pointer - everything the panel had. */
	private final JPanel chooseContainer = new JPanel();
	/** One rolled suggestion at a time. */
	private final JPanel randomContainer = new JPanel();
	/** Every activity, for a roll made when the player's own state is unknown. */
	private final List<Activity> allActivities;
	/** What the player can do right now, from the suggestion engine. */
	private List<Activity> availableNow = java.util.Collections.emptyList();
	/**
	 * The last few rolls, so "Roll again" actually changes the answer. Small on
	 * purpose: with a narrow category filter the pool can be only a few deep,
	 * and a long memory would empty it.
	 */
	private final java.util.Deque<String> recentRolls = new java.util.ArrayDeque<>();
	private Activity rolled;
	/** The category a roll is restricted to, or null for anything. */
	private Activity.Category rollCategory;
	private final java.util.Random random = new java.util.Random();

	private final JCheckBox tickManipulationCheckbox;
	/**
	 * Whether an item goal counts what the player already owns. Sits in the item
	 * block rather than with the global tick-manipulation preference because it
	 * is part of asking the question, not a standing preference.
	 */
	private final JCheckBox countOwnedCheckbox;
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
	 * Helper plugins this panel tried and failed to start. Asking the player to
	 * click the same dead link again is worse than telling them to do it
	 * themselves.
	 */
	private final Set<String> failedToStart = new HashSet<>();

	/**
	 * Option titles the player has picked, so a step with a choice can collapse
	 * to the one they chose instead of listing five every time.
	 * <p>
	 * Keyed by option title rather than by step, deliberately: pick "Hunter
	 * rumours" once and every later bracket that still offers it stays
	 * collapsed to it, while the bracket where it stops being available
	 * re-opens the choice on its own - which is exactly the point at which a
	 * player wants to be asked again.
	 */
	private final Set<String> chosenOptions = new HashSet<>();
	/**
	 * Step titles whose full option list is currently open. Steps start closed
	 * showing their top-rated option, because a plan that lists five methods for
	 * each of a dozen brackets is unreadable before it is informative - the
	 * alternatives are one click away and the count is always visible, so
	 * nothing is hidden, just folded.
	 */
	private final Set<String> expandedSteps = new HashSet<>();
	/** Last rendered path, so choosing an option can re-render without re-planning. */
	private Path lastPath;
	/**
	 * Option bodies already spelled out in the render currently being built,
	 * keyed on their exact text.
	 * <p>
	 * A method that spans a wide range is an option in every segment inside it
	 * - Runecraft's quest route covers four, Farming's tree runs cover three -
	 * and repeating its note, its quest list and its Quest Helper pointer under
	 * each one rebuilds the wall of text the collapsing exists to prevent.
	 * <p>
	 * Keying on the body text rather than on the method is what makes this safe:
	 * a method with an xp/hr rate carries a per-segment time estimate in its
	 * detail line ("~4.1 hrs at 130k xp/hr"), so its text differs between
	 * segments and it is never suppressed. Only genuinely identical bodies -
	 * which in practice means the rate-less, passive methods - collapse.
	 */
	private final Set<String> optionBodiesShown = new HashSet<>();

	public NextStepPanel(NextStepConfig config, List<Activity> allActivities,
		List<ItemSource> allItemSources, Function<Goal, Path> pathResolver)
	{
		this(config, allActivities, allItemSources, pathResolver, null, null);
	}

	public NextStepPanel(NextStepConfig config, List<Activity> allActivities,
		List<ItemSource> allItemSources, Function<Goal, Path> pathResolver,
		PluginManager pluginManager)
	{
		this(config, allActivities, allItemSources, pathResolver, pluginManager, null);
	}

	public NextStepPanel(NextStepConfig config, List<Activity> allActivities,
		List<ItemSource> allItemSources, Function<Goal, Path> pathResolver,
		PluginManager pluginManager, ItemManager itemManager)
	{
		super(false);
		this.pathResolver = pathResolver;
		this.pluginManager = pluginManager;
		this.itemManager = itemManager;
		this.allActivities = allActivities;
		for (Activity activity : allActivities)
		{
			activitiesById.put(activity.getId(), activity);
		}
		for (ItemSource source : allItemSources)
		{
			itemSourcesById.put(source.getId(), source);
		}

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		content = new ContentPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBackground(ColorScheme.DARK_GRAY_COLOR);

		column(startContainer);
		column(chooseContainer);
		column(randomContainer);
		buildStartScreen();
		content.add(startContainer);
		content.add(chooseContainer);
		content.add(randomContainer);

		// --- Goal: an existing activity (quest/diary/pvm/etc) ---
		stack(chooseContainer, sectionHeader("Plan a path to\u2026"));

		tickManipulationCheckbox = new JCheckBox("Use tick-manipulation methods");
		tickManipulationCheckbox.setBackground(ColorScheme.DARK_GRAY_COLOR);
		tickManipulationCheckbox.setForeground(SUBTLE);
		tickManipulationCheckbox.setFont(SMALL_FONT);
		tickManipulationCheckbox.setSelected(config.allowTickManipulation());
		tickManipulationCheckbox.setFocusPainted(false);
		tickManipulationCheckbox.setToolTipText(
			"<html>On: shows tick-manipulation methods (falls back to normal methods only where none are recorded).<br>"
				+ "Off: shows only normal methods, never tick-manipulation.<br>"
				+ "Either way you see one intensity, not both mixed together.</html>");
		stack(chooseContainer, tickManipulationCheckbox);

		// Quests are deliberately absent. 168 of the 256 activities are quests,
		// so they drowned every boss, diary and minigame in the dropdown - and
		// picking one as a goal is a question Quest Helper answers better than a
		// path ever could. They get their own section below instead. Quests that
		// are *prerequisites* on a path are unaffected: naming those, in order,
		// is still this plugin's job.
		List<Activity> sortedActivities = allActivities.stream()
			.filter(a -> a.getCategory() != Activity.Category.QUEST)
			.sorted(Comparator.comparing(Activity::getName))
			.collect(Collectors.toList());
		activityCombo = new JComboBox<>(sortedActivities.toArray(new Activity[0]));
		activityCombo.setRenderer(new DefaultListCellRenderer()
		{
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index,
				boolean isSelected, boolean cellHasFocus)
			{
				super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				setText(value == null ? "" : ((Activity) value).getName());
				return this;
			}
		});
		constrainInput(activityCombo);
		stack(chooseContainer, goalBlock("Activity", activityCombo, null, "Plan path", e ->
		{
			Activity selected = (Activity) activityCombo.getSelectedItem();
			if (selected != null)
			{
				renderPath(pathResolver.apply(Goal.activity(selected.getId(), selected.getName(),
					tickManipulationCheckbox.isSelected())));
			}
		}));

		// --- Goal: a skill level ---
		skillCombo = new JComboBox<>(SKILLS);
		// The values stay SHOUTED because that is how RuneLite's Skill enum names
		// them and what PlayerState is keyed on; only the label is title-cased.
		skillCombo.setRenderer(new DefaultListCellRenderer()
		{
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index,
				boolean selected, boolean focused)
			{
				super.getListCellRendererComponent(list, value, index, selected, focused);
				if (value instanceof String)
				{
					setText(titleCase((String) value));
				}
				return this;
			}
		});
		constrainInput(skillCombo);
		skillLevelSpinner = new JSpinner(new SpinnerNumberModel(99, 2, 99, 1));
		constrainSpinner(skillLevelSpinner);
		stack(chooseContainer, goalBlock("Skill level", skillCombo, skillLevelSpinner, "Plan path", e ->
			renderPath(pathResolver.apply(Goal.skillLevel((String) skillCombo.getSelectedItem(),
				(Integer) skillLevelSpinner.getValue(), tickManipulationCheckbox.isSelected())))));

		// --- Goal: an item ---
		// Typed at, not scrolled through. A bare free-text field was worse than
		// useless when this was tried before, because anything absent from
		// item-sources.json came back as "no source data" and the only way to
		// get an answer was to already know what was in there. A search over
		// what is known keeps the typing and loses the guessing - and unlike a
		// dropdown it gets better as the database grows rather than worse.
		List<ItemSource> sortedItems = allItemSources.stream()
			.sorted(Comparator.comparing(ItemSource::getDisplayName))
			.collect(Collectors.toList());
		itemPicker = new SearchPicker<>(sortedItems, ItemSource::getDisplayName,
			"Type to search, or pick from the list");
		itemQuantitySpinner = new JSpinner(new SpinnerNumberModel(1, 1, 10000, 1));
		constrainSpinner(itemQuantitySpinner);
		countOwnedCheckbox = new JCheckBox("Count what I already own");
		countOwnedCheckbox.setBackground(ColorScheme.DARK_GRAY_COLOR);
		countOwnedCheckbox.setForeground(SUBTLE);
		countOwnedCheckbox.setFont(SMALL_FONT);
		countOwnedCheckbox.setSelected(true);
		countOwnedCheckbox.setFocusPainted(false);
		countOwnedCheckbox.setToolTipText("On: plans only what is missing from your bank, "
			+ "inventory and gear. Off: plans the full route as if you had none, which is what "
			+ "you want when you already own one and are after another.");
		itemPicker.setTrailing(itemQuantitySpinner);
		stack(chooseContainer, goalBlock("Item", itemPicker, countOwnedCheckbox,
			"Plan path", e ->
			{
				ItemSource selected = itemPicker.getSelected();
				if (selected != null)
				{
					renderPath(pathResolver.apply(Goal.itemQuantity(selected.getId(),
						(Integer) itemQuantitySpinner.getValue(), selected.getDisplayName(),
						tickManipulationCheckbox.isSelected(), countOwnedCheckbox.isSelected())));
				}
			}));

		// --- Goal: a floor under every skill ---
		// "I want 80+ in everything" is a question the panel could not be asked.
		// Answering it one skill at a time means working out which are short and
		// in what order by hand, which is the part a plan is for.
		baseStatsSpinner = new JSpinner(new SpinnerNumberModel(70, 2, 99, 1));
		constrainSpinner(baseStatsSpinner);
		stack(chooseContainer, goalBlock("Base stats", goalCaption("Every skill at least"),
			baseStatsSpinner, "Plan path", e ->
				renderPath(pathResolver.apply(Goal.baseStats(
					(Integer) baseStatsSpinner.getValue(),
					tickManipulationCheckbox.isSelected())))));

		// --- Goal: a total level ---
		// The lower bound is 33, which is every skill at 1 and Hitpoints at 10;
		// the upper is every skill at 99. Outside that the number means nothing.
		totalLevelSpinner = new JSpinner(new SpinnerNumberModel(1500, 33, 2376, 1));
		// Plain digits. The default editor groups by locale, so 1500 renders as
		// "1.500" for anyone in Europe - which reads as one and a half, not
		// fifteen hundred.
		totalLevelSpinner.setEditor(new JSpinner.NumberEditor(totalLevelSpinner, "0"));
		constrainSpinner(totalLevelSpinner);
		stack(chooseContainer, goalBlock("Total level", goalCaption("Reach a total of"),
			totalLevelSpinner, "Plan path", e ->
				renderPath(pathResolver.apply(Goal.totalLevel(
					(Integer) totalLevelSpinner.getValue(),
					tickManipulationCheckbox.isSelected())))));

		pathResultContainer.setLayout(new BoxLayout(pathResultContainer, BoxLayout.Y_AXIS));
		pathResultContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
		pathResultContainer.setAlignmentX(Component.LEFT_ALIGNMENT);
		// Sits with the other goal pickers, not under the plan: it is one of the
		// things you choose between when deciding what to do next.
		stack(chooseContainer, questsSection());

		content.add(pathResultContainer);
		setMode(Mode.START);

		// --- Suggestions ---
		// "Available now" was a list of cards whose only action was to open the
		// wiki - the one thing this panel is trying to stop players needing to
		// do - so the space goes to the plan instead. The container survives
		// because the logged-out placeholder still renders into it.
		availableNowContainer.setLayout(new BoxLayout(availableNowContainer, BoxLayout.Y_AXIS));
		availableNowContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
		availableNowContainer.setAlignmentX(Component.LEFT_ALIGNMENT);
		content.add(availableNowContainer);

		stack(content, sectionHeader("Almost there"));
		almostThereContainer.setLayout(new BoxLayout(almostThereContainer, BoxLayout.Y_AXIS));
		almostThereContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
		almostThereContainer.setAlignmentX(Component.LEFT_ALIGNMENT);
		content.add(almostThereContainer);

		// A measured gap, so the credit sits at the foot of the sidebar instead
		// of following whatever is on screen. On a fresh login there is almost
		// nothing above it, and the licence text arriving directly under
		// "Almost there" reads as the answer to the question the player just
		// asked. See sinkFooter for why this is measured rather than glued.
		footerSpacer.setOpaque(false);
		footerSpacer.setAlignmentX(Component.LEFT_ALIGNMENT);
		footerSpacer.setPreferredSize(new Dimension(1, 0));
		footerSpacer.setMaximumSize(new Dimension(Integer.MAX_VALUE, 0));
		content.add(footerSpacer);
		stack(content, attributionBlock());

		scrollPane = new JScrollPane(content);
		scrollPane.setBorder(BorderFactory.createEmptyBorder());
		scrollPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
		scrollPane.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
		// The ContentPanel below tracks the viewport width, so there is never
		// anything to scroll to horizontally.
		scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		scrollPane.getVerticalScrollBar().setUnitIncrement(16);
		scrollPane.getViewport().addComponentListener(new java.awt.event.ComponentAdapter()
		{
			@Override
			public void componentResized(java.awt.event.ComponentEvent resized)
			{
				sinkFooter();
			}
		});
		add(scrollPane, BorderLayout.CENTER);

		showPlaceholder();
	}

	/**
	 * Adds a component to the vertical stack, pinned to the left and capped so
	 * BoxLayout can stretch it horizontally but never vertically. Without the
	 * maximum-size cap, BoxLayout hands leftover height to whichever component
	 * will take it and the layout goes to pieces.
	 */
	private static void stack(JPanel parent, Component child)
	{
		if (child instanceof javax.swing.JComponent)
		{
			javax.swing.JComponent c = (javax.swing.JComponent) child;
			c.setAlignmentX(Component.LEFT_ALIGNMENT);
			c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
		}
		parent.add(child);
	}

	/**
	 * Inputs report a preferred width based on their widest content - a long
	 * activity name or a 23-entry skill list will ask for far more than the
	 * 225px the sidebar has. Pin them to the panel width instead.
	 */
	private static void constrainInput(javax.swing.JComponent input)
	{
		int h = input.getPreferredSize().height;
		input.setPreferredSize(new Dimension(CONTENT_WIDTH, h));
		input.setMinimumSize(new Dimension(40, h));
		input.setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
	}

	private static void constrainSpinner(JSpinner spinner)
	{
		int h = spinner.getPreferredSize().height;
		spinner.setPreferredSize(new Dimension(56, h));
		spinner.setMinimumSize(new Dimension(56, h));
		spinner.setMaximumSize(new Dimension(56, h));
	}

	/**
	 * One goal row: a caption, the input (optionally with a small trailing
	 * spinner), and a full-width action button underneath.
	 */
	private JPanel goalBlock(String caption, Component input, JSpinner trailing, String buttonText,
		java.awt.event.ActionListener action)
	{
		return goalBlock(caption, input, trailing, null, buttonText, action);
	}

	/**
	 * The left half of a goal row that has no list to pick from - just a
	 * sentence and the number that finishes it. "Every skill at least [80]"
	 * reads as one thing, where a bare spinner under a heading reads as a
	 * setting.
	 */
	private JLabel goalCaption(String text)
	{
		JLabel label = new JLabel(text);
		label.setForeground(DETAIL);
		label.setFont(SMALL_FONT);
		return label;
	}

	/**
	 * A goal block built round a {@link SearchPicker} instead of a dropdown.
	 * <p>
	 * Separate from the dropdown version because the picker owns its own height:
	 * it is a field with a results list that appears and disappears underneath,
	 * so pinning the input row to one line - which is right for a combo box -
	 * would clip the matches away.
	 */
	private JPanel goalBlock(String caption, SearchPicker<?> picker, Component extra,
		String buttonText, java.awt.event.ActionListener action)
	{
		JPanel block = new JPanel();
		block.setLayout(new BoxLayout(block, BoxLayout.Y_AXIS));
		block.setBackground(ColorScheme.DARK_GRAY_COLOR);
		block.setBorder(new EmptyBorder(6, 0, 2, 0));

		stack(block, microLabel(caption, SUBTLE, 0));
		stack(block, picker);
		if (extra != null)
		{
			stack(block, extra);
		}

		JButton button = new JButton(buttonText);
		button.setFocusPainted(false);
		button.addActionListener(action);
		stack(block, button);

		block.setAlignmentX(Component.LEFT_ALIGNMENT);
		return block;
	}

	/** As above, with one extra control between the inputs and the button. */
	private JPanel goalBlock(String caption, Component input, JSpinner trailing, Component extra,
		String buttonText, java.awt.event.ActionListener action)
	{
		JPanel block = new JPanel();
		block.setLayout(new BoxLayout(block, BoxLayout.Y_AXIS));
		block.setBackground(ColorScheme.DARK_GRAY_COLOR);
		block.setBorder(new EmptyBorder(6, 0, 2, 0));

		stack(block, microLabel(caption, SUBTLE, 0));

		JPanel inputRow = new JPanel(new BorderLayout(4, 0));
		inputRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
		inputRow.add(input, BorderLayout.CENTER);
		if (trailing != null)
		{
			inputRow.add(trailing, BorderLayout.EAST);
		}
		inputRow.setPreferredSize(new Dimension(CONTENT_WIDTH, input.getPreferredSize().height));
		stack(block, inputRow);

		if (extra != null)
		{
			stack(block, extra);
		}

		JButton button = new JButton(buttonText);
		button.setFocusPainted(false);
		button.addActionListener(action);
		stack(block, button);

		block.setAlignmentX(Component.LEFT_ALIGNMENT);
		block.setMaximumSize(new Dimension(Integer.MAX_VALUE, block.getPreferredSize().height));
		return block;
	}

	/**
	 * Visible credit for where every figure in this panel came from.
	 * <p>
	 * None of this content is ours: the xp rates, requirements, drop rates and
	 * quest chains are facts read off the Old School RuneScape Wiki, which
	 * publishes under CC BY-NC-SA 3.0 and asks for attribution. The wiki link
	 * on each individual step is not a substitute for saying so once, plainly,
	 * where the player can see it - and the game itself is Jagex's, which a
	 * third-party tool should state rather than leave ambiguous.
	 */
	private JPanel attributionBlock()
	{
		JPanel block = new JPanel();
		block.setLayout(new BoxLayout(block, BoxLayout.Y_AXIS));
		block.setBackground(ColorScheme.DARK_GRAY_COLOR);
		block.setAlignmentX(Component.LEFT_ALIGNMENT);
		block.setBorder(new EmptyBorder(16, 0, 4, 0));

		// A rule above it, for the same reason the steps have one: it marks this
		// off as the edge of the panel rather than one more thing to read.
		block.add(hairline());
		block.add(Box.createVerticalStrut(8));

		JLabel credit = resultLine("Data from the Old School RuneScape Wiki, used under "
			+ "CC BY-NC-SA 3.0. This plugin owns none of it.", DETAIL, 0);
		credit.setFont(SMALL_FONT);
		credit.setToolTipText(WIKI_URL);
		makeClickable(credit, () -> openUrl(WIKI_URL));
		block.add(credit);

		JLabel licence = resultLine("Read the licence \u2197", QUIET_LINK, 0);
		licence.setFont(SMALL_FONT);
		licence.setToolTipText(LICENCE_URL);
		makeClickable(licence, () -> openUrl(LICENCE_URL));
		block.add(licence);

		JLabel jagex = resultLine("Not affiliated with or endorsed by Jagex. RuneScape and Old "
			+ "School RuneScape are trademarks of Jagex Ltd.", DETAIL, 0);
		jagex.setFont(SMALL_FONT);
		block.add(jagex);

		return block;
	}

	/**
	 * Plans a path to a suggested activity, as if it had been picked from the
	 * Activity dropdown - which it also is, so the controls agree with the
	 * result on screen rather than the plan appearing to come from nowhere.
	 */
	private void planFor(Activity activity)
	{
		activityCombo.setSelectedItem(activity);
		renderPath(pathResolver.apply(Goal.activity(activity.getId(), activity.getName(),
			tickManipulationCheckbox.isSelected())));
	}

	/** A 1px rule between steps - the whole separation budget of this layout. */
	/**
	 * The uppercase line above a step: its number, plus the one fact worth
	 * putting there - what the step costs in xp, or what kind of thing it is.
	 */
	private String stepCaption(int number, PathStep step)
	{
		String caption = "Step " + number;
		if (step.getXpCost() > 0)
		{
			return caption + " \u00b7 " + XpTable.formatXp(step.getXpCost()) + " xp";
		}
		String kind = prefixFor(step.getType()).replace("[", "").replace("]", "").trim();
		return kind.isEmpty() ? caption : caption + " \u00b7 " + kind;
	}

	/** Drops the rendered plan and puts the goal pickers back in view. */
	private void clearPath()
	{
		lastPath = null;
		optionBodiesShown.clear();
		pathResultContainer.removeAll();
		pathResultContainer.revalidate();
		pathResultContainer.repaint();
		SwingUtilities.invokeLater(() ->
			scrollPane.getViewport().setViewPosition(new java.awt.Point(0, 0)));
	}

	/**
	 * An empty option box: filled, edged, padded, and spaced off the one above.
	 * A chosen option is edged in green instead, so the decision is visible from
	 * the shape of the panel rather than only from the tick in its title.
	 */
	private JPanel optionCard(boolean selected)
	{
		JPanel card = new JPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBackground(OPTION_BG);
		card.setAlignmentX(Component.LEFT_ALIGNMENT);
		card.setBorder(BorderFactory.createCompoundBorder(
			new EmptyBorder(6, 0, 0, 0),
			BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(selected ? CHOSEN : OPTION_EDGE, 1),
				new EmptyBorder(7, 9, 8, 9))));
		return card;
	}

	private JPanel hairline()
	{
		JPanel rule = new JPanel();
		rule.setBackground(HAIRLINE);
		rule.setAlignmentX(Component.LEFT_ALIGNMENT);
		rule.setPreferredSize(new Dimension(10, 1));
		rule.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
		rule.setBorder(null);
		return rule;
	}

	/** Uppercase, tracked, dim: the "STEP 3 - 1.3M XP" line above a step. */
	private JLabel microLabel(String text, Color color, int topPad)
	{
		return microLabel(text, color, topPad, 0);
	}

	private JLabel microLabel(String text, Color color, int topPad, int indent)
	{
		JLabel label = resultLine(text.toUpperCase(java.util.Locale.ROOT), color, indent);
		label.setFont(MICRO_FONT);
		label.setBorder(new EmptyBorder(topPad, indent, 1, 0));
		return label;
	}

	/**
	 * Puts links side by side instead of stacking them. Four stacked grey links
	 * under every step was most of what made the plan look like a wall.
	 */
	private JPanel linkRow(List<JLabel> links)
	{
		return linkRow(links, 0);
	}

	private JPanel linkRow(List<JLabel> links, int indent)
	{
		JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
		// Transparent rather than painted: inside an option box the box's fill
		// has to show through, and everywhere else the parent is the panel
		// background this used to paint by hand.
		row.setOpaque(false);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setBorder(new EmptyBorder(5, indent, 0, 0));
		for (int i = 0; i < links.size(); i++)
		{
			if (i > 0)
			{
				JLabel gap = new JLabel("   ");
				gap.setFont(SMALL_FONT);
				row.add(gap);
			}
			JLabel link = links.get(i);
			link.setBorder(null);
			link.setAlignmentY(Component.CENTER_ALIGNMENT);
			row.add(link);
		}
		row.add(javax.swing.Box.createHorizontalGlue());
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return row;
	}

	/** A bare clickable label, for a link row. */
	private JLabel actionLink(String text, Runnable onClick)
	{
		JLabel label = new JLabel(text);
		label.setForeground(QUIET_LINK);
		label.setFont(SMALL_FONT);
		makeClickable(label, onClick);
		return label;
	}

	/** HUNTER -> Hunter. Display only; the underlying value is untouched. */
	private static String titleCase(String skill)
	{
		if (skill == null || skill.isEmpty())
		{
			return skill;
		}
		return skill.substring(0, 1).toUpperCase(java.util.Locale.ROOT)
			+ skill.substring(1).toLowerCase(java.util.Locale.ROOT);
	}

	/**
	 * Quests get a signpost rather than a picker.
	 * <p>
	 * Listing 168 quests to choose a goal from would be rebuilding the front
	 * page of a plugin that already does it better - Quest Helper lists what you
	 * can start, with its requirements, and then walks you through it. What this
	 * panel keeps is the part Quest Helper doesn't do: when a quest stands
	 * between you and something you asked for, it is named in the plan, in order.
	 */
	private JPanel questsSection()
	{
		JPanel block = new JPanel();
		block.setLayout(new BoxLayout(block, BoxLayout.Y_AXIS));
		block.setBackground(ColorScheme.DARK_GRAY_COLOR);
		block.setAlignmentX(Component.LEFT_ALIGNMENT);
		block.setBorder(new EmptyBorder(4, 0, 8, 0));

		block.add(microLabel("Quests", SUBTLE, 0));

		JLabel body = resultLine("Pick a quest in Quest Helper \u2014 it lists every one you "
			+ "can start and walks you through it. Quests needed for a goal here still "
			+ "show up in its plan.", DETAIL, 0);
		body.setFont(SMALL_FONT);
		body.setBorder(new EmptyBorder(3, 0, 0, 0));
		block.add(body);

		JLabel pointer = helperPointer(QUEST_HELPER, "quest-helper",
			"is installed and ready.", "the quest list and walkthroughs", 0);
		block.add(pointer);

		block.setMaximumSize(new Dimension(Integer.MAX_VALUE, block.getPreferredSize().height));
		return block;
	}

	/** A vertical, left-aligned, transparent strip - the shape every screen uses. */
	private void column(JPanel panel)
	{
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
	}

	/**
	 * The two ways in, as two offers rather than a menu bar: naming what each
	 * one is for is the whole point, since the second one only makes sense once
	 * you have read it.
	 */
	private void buildStartScreen()
	{
		stack(startContainer, sectionHeader("What now?"));
		stack(startContainer, startOption("Plan a path",
			"A boss, a skill level, an item - and everything it takes to get there.",
			() -> setMode(Mode.CHOOSE)));
		stack(startContainer, startOption("Surprise me",
			"One thing you can already do, picked at random. For when nothing in "
				+ "particular is calling.",
			() ->
			{
				setMode(Mode.RANDOM);
				roll();
			}));
	}

	private JPanel startOption(String title, String blurb, Runnable action)
	{
		// The two things to do on the opening screen are the clearest case of
		// all for a box: nothing else is on screen to tell them apart from the
		// sentence sitting under each of them.
		JPanel block = optionCard(false);
		int outer = cardInset;
		cardInset = CARD_INSET;
		try
		{
			JLabel heading = resultLine(title + " \u2192", ACCENT, 0);
			heading.setFont(STEP_TITLE_FONT);
			makeClickable(heading, action);
			stack(block, heading);

			JLabel detail = resultLine(blurb, DETAIL, 0);
			detail.setFont(SMALL_FONT);
			stack(block, detail);
		}
		finally
		{
			cardInset = outer;
		}

		block.setMaximumSize(new Dimension(Integer.MAX_VALUE, block.getPreferredSize().height));
		return block;
	}

	/**
	 * Grows or shrinks the gap above the attribution so it ends up at the bottom
	 * of the sidebar.
	 * <p>
	 * Measured rather than glued: a Scrollable that tracks the viewport height
	 * hangs this panel (see ContentPanel), and glue inside a scroll pane gets no
	 * leftover height to take because the view is sized to its own preferred
	 * height. So the gap is worked out here - viewport height minus what the
	 * content comes to without it - and left at zero whenever the content is
	 * already taller than the viewport, which is the usual case once a plan is
	 * on screen. Changes under 4px are ignored: the one thing worse than a
	 * footer in the wrong place is a layout that keeps relaying itself.
	 */
	private void sinkFooter()
	{
		if (scrollPane == null || content == null)
		{
			return;
		}
		int spacer = footerSpacer.getPreferredSize().height;
		int withoutSpacer = content.getPreferredSize().height - spacer;
		int want = Math.max(0, scrollPane.getViewport().getHeight() - withoutSpacer);
		if (Math.abs(want - spacer) < 4)
		{
			return;
		}
		footerSpacer.setPreferredSize(new Dimension(1, want));
		footerSpacer.setMaximumSize(new Dimension(Integer.MAX_VALUE, want));
		footerSpacer.setMinimumSize(new Dimension(1, want));
		content.revalidate();
	}

	/** Shows one screen and clears whatever the last one left on the page. */
	private void setMode(Mode next)
	{
		mode = next;
		startContainer.setVisible(next == Mode.START);
		chooseContainer.setVisible(next == Mode.CHOOSE);
		randomContainer.setVisible(next == Mode.RANDOM);
		if (next != Mode.CHOOSE)
		{
			// A plan belongs to the question that produced it.
			clearPath();
		}
		if (next != Mode.RANDOM)
		{
			randomContainer.removeAll();
		}
		revalidate();
		repaint();
		SwingUtilities.invokeLater(this::sinkFooter);
	}

	/**
	 * Picks one thing to do, from what the player can actually do right now.
	 * <p>
	 * Weighted by the activity's own priority rather than drawn flat: two thirds
	 * of the bundled activities are quests, so a flat draw would answer "do a
	 * quest" two times in three and the feature would feel broken. Weighting
	 * keeps the long tail reachable while the notable content comes up more
	 * often, and the category filter is there for when the player does have a
	 * mood in mind.
	 */
	private void roll()
	{
		List<Activity> pool = new ArrayList<>();
		// Logged out there is no player state to filter by, so the whole
		// catalogue is the pool and the panel says so rather than sitting empty.
		for (Activity activity : availableNow.isEmpty() ? allActivities : availableNow)
		{
			if (rollCategory != null && activity.getCategory() != rollCategory)
			{
				continue;
			}
			if (!recentRolls.contains(activity.getId()))
			{
				pool.add(activity);
			}
		}
		if (pool.isEmpty())
		{
			// Everything left is something we just offered: start the memory over
			// rather than refusing to roll.
			recentRolls.clear();
			for (Activity activity : availableNow.isEmpty() ? allActivities : availableNow)
			{
				if (rollCategory == null || activity.getCategory() == rollCategory)
				{
					pool.add(activity);
				}
			}
		}

		rolled = weightedPick(pool);
		if (rolled != null)
		{
			recentRolls.addLast(rolled.getId());
			while (recentRolls.size() > 6)
			{
				recentRolls.removeFirst();
			}
		}
		renderRoll();
	}

	private Activity weightedPick(List<Activity> pool)
	{
		if (pool.isEmpty())
		{
			return null;
		}
		int total = 0;
		for (Activity activity : pool)
		{
			total += Math.max(1, activity.getPriority());
		}
		int target = random.nextInt(total);
		for (Activity activity : pool)
		{
			target -= Math.max(1, activity.getPriority());
			if (target < 0)
			{
				return activity;
			}
		}
		return pool.get(pool.size() - 1);
	}

	private void renderRoll()
	{
		randomContainer.removeAll();
		stack(randomContainer, sectionHeader("Something to do"));
		stack(randomContainer, categoryRow());

		if (rolled == null)
		{
			stack(randomContainer, resultLine("Nothing to pick from in that category.", SUBTLE, 0));
		}
		else
		{
			stack(randomContainer, hairline());
			String kind = rolled.getCategory() == null
				? "Activity"
				: rolled.getCategory().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
			stack(randomContainer, microLabel(kind, SUBTLE, 11));

			JLabel name = resultLine(rolled.getName(), PRIMARY, 0);
			name.setFont(STEP_TITLE_FONT);
			stack(randomContainer, name);

			if (rolled.getDescription() != null && !rolled.getDescription().isEmpty())
			{
				JLabel description = resultLine(rolled.getDescription(), DETAIL, 0);
				description.setFont(SMALL_FONT);
				stack(randomContainer, description);
			}

			if (availableNow.isEmpty())
			{
				stack(randomContainer, resultLine("! Rolled from everything \u2014 log in so this "
					+ "can pick something you can actually do now.", Color.ORANGE, 0));
			}

			final Activity picked = rolled;
			stack(randomContainer, linkRow(java.util.Arrays.asList(
				actionLink("Plan it \u2192", () -> planFor(picked)),
				actionLink("Roll again", this::roll))));
		}

		stack(randomContainer, linkRow(java.util.Collections.singletonList(
			actionLink("\u2190 Back", () -> setMode(Mode.START)))));
		randomContainer.revalidate();
		randomContainer.repaint();
	}

	/**
	 * The mood filter. Not a dropdown: at six entries a row of words is fewer
	 * clicks than opening a combo, and it shows what the choices are.
	 * <p>
	 * Three to a line, because a link row lays its children out horizontally and
	 * never wraps: six of them walked straight off the right edge of a 190px
	 * sidebar, leaving "Anything" and two letters of "Quests" on screen.
	 */
	private JPanel categoryRow()
	{
		List<JLabel> top = new ArrayList<>();
		top.add(categoryChip("Anything", null));
		top.add(categoryChip("Quests", Activity.Category.QUEST));
		top.add(categoryChip("Bosses", Activity.Category.PVM));

		List<JLabel> bottom = new ArrayList<>();
		bottom.add(categoryChip("Diaries", Activity.Category.DIARY));
		bottom.add(categoryChip("Minigames", Activity.Category.MINIGAME));
		bottom.add(categoryChip("Skilling", Activity.Category.SKILLING));

		JPanel rows = new JPanel();
		column(rows);
		rows.add(linkRow(top));
		rows.add(linkRow(bottom));
		rows.setMaximumSize(new Dimension(Integer.MAX_VALUE, rows.getPreferredSize().height));
		return rows;
	}

	/**
	 * A plain label, not a {@link #resultLine}: those wrap text inside a table
	 * of the full content width, so a row of them is a row of 190px-wide blocks
	 * and only the first one is on screen.
	 */
	private JLabel categoryChip(String text, Activity.Category category)
	{
		boolean on = rollCategory == category;
		JLabel chip = new JLabel(text);
		chip.setForeground(on ? ACCENT : QUIET_LINK);
		chip.setFont(SMALL_FONT);
		if (!on)
		{
			makeClickable(chip, () ->
			{
				rollCategory = category;
				roll();
			});
		}
		return chip;
	}

	private JLabel sectionHeader(String text)
	{
		JLabel label = new JLabel(text);
		label.setForeground(PRIMARY);
		label.setFont(HEADER_FONT);
		label.setBorder(new EmptyBorder(12, 0, 4, 0));
		return label;
	}

	/**
	 * The logged-out state, in this panel's own typography rather than
	 * RuneLite's PluginErrorPanel.
	 * <p>
	 * PluginErrorPanel puts its description in an HTML label with no wrap width,
	 * so it asked for 253px inside a 225px sidebar. Horizontal scrolling is off
	 * here, so nothing scrolls to reach it - the tail of the sentence is simply
	 * cut off at the viewport edge, on the one screen a new user sees first.
	 * Text wrapped by this panel has a width by construction, and
	 * PanelWidthTest is the guard that keeps it that way.
	 */
	private void showPlaceholder()
	{
		stack(availableNowContainer, sectionHeader("Next Step"));
		stack(availableNowContainer, resultLine(
			"Log in to see suggestions based on your progress.", SUBTLE, 0));
	}

	/**
	 * Renders a Path (from any of the three goal types) as an ordered,
	 * numbered list of concrete steps, plus any warnings the planner hit.
	 */
	private void renderPath(Path path)
	{
		lastPath = path;
		optionBodiesShown.clear();
		pathResultContainer.removeAll();

		JLabel titleLabel = resultLine(path.getGoal().getLabel(), ACCENT, 0);
		titleLabel.setFont(STEP_TITLE_FONT);
		titleLabel.setBorder(new EmptyBorder(10, 0, 0, 0));
		stack(pathResultContainer, titleLabel);

		// Once a plan is open there was no way out of it but re-picking from a
		// dropdown you had already scrolled past.
		stack(pathResultContainer, linkRow(java.util.Collections.singletonList(
			actionLink("\u2190 Clear plan", this::clearPath))));

		if (path.isAlreadyComplete())
		{
			stack(pathResultContainer, resultLine("Already done.", ColorScheme.PROGRESS_COMPLETE_COLOR, 0));

			// An item goal can be "already done" purely because one is sitting in
			// the bank, which is a dead end for the player who wants a second one
			// - or whose bank this plugin has read wrong. Offer the route anyway
			// rather than making them inflate the quantity until a plan appears.
			Goal goal = path.getGoal();
			if (goal.getType() == Goal.Type.ITEM_QUANTITY && goal.isCountOwned())
			{
				stack(pathResultContainer, linkRow(java.util.Collections.singletonList(
					actionLink("Plan it anyway, as if I had none",
						() -> renderPath(pathResolver.apply(goal.ignoringWhatIsOwned()))))));
			}
		}
		else if (path.isRequirementsAlreadyMet())
		{
			stack(pathResultContainer, resultLine("You already meet every requirement \u2014 nothing is "
				+ "blocking this.", ColorScheme.PROGRESS_COMPLETE_COLOR, 0));
			Activity target = preparationFor(path);
			if (target != null)
			{
				renderPreparation(target, borrowedFrom(path, target));
			}
			else
			{
				// The one branch that had nothing to hand off to.
				//
				// A quest or a diary you already qualify for is exactly when
				// Quest Helper is most useful, and this is the branch every
				// rolled suggestion lands in - the roll only offers things whose
				// requirements are already met. So "Surprise me" would answer
				// "Lumbridge & Draynor Hard Diary", and then the only thing on
				// offer was a wiki link, with the plugin that has the whole task
				// list sitting in the same sidebar unmentioned.
				for (PathStep step : path.getSteps())
				{
					if (step.isTarget() && isHandedOff(step))
					{
						stack(pathResultContainer, questHelperPointer(step.getType(), 1));
						break;
					}
				}

				for (PathStep step : path.getSteps())
				{
					for (PathStep.Option option : step.getAlternatives())
					{
						if (option.getWikiUrl() != null)
						{
							stack(pathResultContainer, wikiLink(option.getWikiUrl(), 0));
						}
					}
				}
			}
		}
		else if (path.getSteps().isEmpty() && path.getWarnings().isEmpty())
		{
			stack(pathResultContainer, resultLine("Nothing to plan \u2014 should be achievable right now.", PRIMARY, 0));
		}
		else
		{
			int i = 1;
			List<PathStep> steps = path.getSteps();
			for (int at = 0; at < steps.size(); at++)
			{
				PathStep step = steps.get(at);

				// A run of prerequisite quests (or diary tiers) collapses into one
				// listed block: naming them and their order is this plugin's job,
				// walking you through them is Quest Helper's. The goal itself is
				// excluded - it's the destination, not something needed first.
				if (isHandedOff(step) && !step.isTarget())
				{
					int end = at;
					while (end + 1 < steps.size()
						&& steps.get(end + 1).getType() == step.getType()
						&& !steps.get(end + 1).isTarget())
					{
						end++;
					}
					renderHandoffBlock(i, steps.subList(at, end + 1));
					at = end;
					i++;
					continue;
				}

				// Consecutive brackets that resolve to the same method are one
				// stretch of play, not several steps. Thieving 76-99 breaks into
				// eight brackets because the Knights of Ardougne rate ramps and
				// other methods come and go around it - but a player blackjacking
				// thugs is doing one thing for all 23 levels, and reading the same
				// method name under eight headings says otherwise. The brackets are
				// still there, one link away.
				int runEnd = sameMethodRunEnd(steps, at);
				if (runEnd > at)
				{
					List<PathStep> run = steps.subList(at, runEnd + 1);
					String runKey = runKey(run);
					if (expandedSteps.contains(runKey))
					{
						for (PathStep bracket : run)
						{
							renderStep(i, bracket);
							i++;
						}
						stack(pathResultContainer, runLink(runKey, "\u2190 Back to one step ("
							+ run.get(0).getLevelFrom() + " \u2192 "
							+ run.get(run.size() - 1).getLevelTo() + ")"));
					}
					else
					{
						renderMergedRun(i, run, runKey);
						i++;
					}
					at = runEnd;
					continue;
				}

				renderStep(i, step);
				i++;
			}

			// The last step of a plan is the content itself; what to take to it
			// is the next thing the player needs and the reason they'd otherwise
			// open the wiki.
			Activity target = preparationFor(path);
			if (target != null)
			{
				renderPreparation(target, borrowedFrom(path, target));
			}

		}

		for (String warning : path.getWarnings())
		{
			stack(pathResultContainer, resultLine("! " + warning, Color.ORANGE, 0));
		}

		pathResultContainer.revalidate();
		pathResultContainer.repaint();

		// Results render below three goal blocks, so on a short sidebar they can
		// land off-screen; bring them into view rather than leaving the user to
		// wonder whether the click did anything.
		SwingUtilities.invokeLater(() ->
		{
			sinkFooter();
			int visible = Math.min(pathResultContainer.getHeight(), scrollPane.getViewport().getHeight());
			pathResultContainer.scrollRectToVisible(new Rectangle(0, 0, 1, visible));
		});
	}

	public void setClientToolbar(ClientToolbar clientToolbar)
	{
		this.clientToolbar = clientToolbar;
	}

	/**
	 * Another plugin's sidebar button, if RuneLite is showing one for it.
	 * <p>
	 * There is no registry of other plugins' buttons: {@code ClientToolbar} can
	 * open a {@code NavigationButton}, but only the plugin that built one holds
	 * a reference to it. The set of them does exist though - the client keeps
	 * the sidebar's entries - so this reads that set and matches on the tooltip.
	 * <p>
	 * It is reflection into the client's own fields, which is why every part of
	 * it is wrapped: a RuneLite release that renames a field costs this feature
	 * and nothing else, and the pointer falls back to the sentence it used to
	 * be. {@link NavButtonLookup} holds the matching, which is the part worth
	 * testing.
	 */
	private NavigationButton sidebarButton(String pluginName)
	{
		if (clientToolbar == null)
		{
			return null;
		}
		try
		{
			java.lang.reflect.Field uiField = ClientToolbar.class.getDeclaredField("clientUI");
			uiField.setAccessible(true);
			Object clientUi = uiField.get(clientToolbar);
			java.lang.reflect.Field entriesField = clientUi.getClass()
				.getDeclaredField("sidebarEntries");
			entriesField.setAccessible(true);
			Object entries = entriesField.get(clientUi);
			if (!(entries instanceof java.util.Collection))
			{
				return null;
			}

			List<NavigationButton> buttons = new ArrayList<>();
			List<String> tooltips = new ArrayList<>();
			for (Object entry : (java.util.Collection<?>) entries)
			{
				if (entry instanceof NavigationButton)
				{
					NavigationButton button = (NavigationButton) entry;
					buttons.add(button);
					tooltips.add(button.getTooltip());
				}
			}
			int at = NavButtonLookup.match(pluginName, tooltips);
			return at < 0 ? null : buttons.get(at);
		}
		catch (ReflectiveOperationException | RuntimeException | LinkageError notThisVersion)
		{
			log.debug("Could not read the sidebar's buttons", notThisVersion);
			return null;
		}
	}

	/** The activity a path is aimed at, or null for skill and item goals. */
	private Activity targetActivity(Path path)
	{
		String id = path.getGoal().getActivityId();
		return id == null ? null : activitiesById.get(id);
	}

	/**
	 * The activity whose gear answers this plan, which is not always the thing
	 * the plan was asked for.
	 * <p>
	 * "Ancestral robe top" is a plan that ends in Chambers of Xeric and "Pet
	 * snakeling" is a plan that ends in Zulrah, and in both cases the player's
	 * next question is the one Chambers of Xeric and Zulrah already answer. The
	 * panel used to hand that answer over only when the goal happened to be the
	 * fight itself, so asking for the drop told you where to go and then stopped
	 * - exactly where picking the boss off the Activity list keeps going.
	 */
	private Activity preparationFor(Path path)
	{
		Activity own = targetActivity(path);
		if (own != null)
		{
			return own.hasPreparation() ? own : null;
		}

		ItemSource source = itemSourcesById.get(path.getGoal().getItemId());
		if (source == null || source.getPreparationFrom() == null)
		{
			return null;
		}
		Activity fight = activitiesById.get(source.getPreparationFrom());
		return fight != null && fight.hasPreparation() ? fight : null;
	}

	/**
	 * Whose gear this is, when it is not the goal's own - the name to put in the
	 * heading. Null when the player asked for the fight itself and no heading
	 * beyond "What to bring" is needed.
	 */
	private String borrowedFrom(Path path, Activity preparation)
	{
		if (preparation.getId().equals(path.getGoal().getActivityId()))
		{
			// An activity that borrowed its own gear from another one already
			// knows whose it is.
			return preparation.getPreparationSource();
		}
		return preparation.getName();
	}

	/**
	 * What to wear, what to bring, and who helps once you're there.
	 * <p>
	 * Items are joined into a paragraph per kit rather than listed one slot per
	 * line: eleven slots across four kits is forty-odd lines in a 225px column,
	 * which is a wall, and the player is reading this to fill an inventory, not
	 * to audit a slot table.
	 */
	private void renderPreparation(Activity activity, String borrowedFrom)
	{
		stack(pathResultContainer, hairline());
		// Named when the gear is someone else's: asking for a pet snakeling and
		// being handed a blowpipe and anti-venom only makes sense once the panel
		// has said the word "Zulrah" out loud.
		String whose = borrowedFrom == null
			? "What to bring"
			: "What to bring to " + borrowedFrom;
		stack(pathResultContainer, microLabel(whose, SUBTLE, 11));

		for (Activity.GearSetup setup : activity.getSetups())
		{
			JLabel name = resultLine(setup.getName(), PRIMARY, 0);
			name.setBorder(new EmptyBorder(6, 0, 0, 0));
			stack(pathResultContainer, name);

			// Pictures instead of the words, wherever the pictures say the same
			// thing. A loadout is something players recognise by sight, and nine
			// item names in a row is work the panel can do for them. The words
			// stay only for the lines a picture cannot carry - a condition, a
			// caveat, or an item that could not be matched at all - so nothing
			// is lost to the tidying.
			Pictured gear = iconsFor(setup.getItems());
			stackIcons(gear);

			if (!gear.getInWords().isEmpty())
			{
				JLabel items = resultLine(String.join(", ", gear.getInWords()), DETAIL, 12);
				items.setFont(SMALL_FONT);
				stack(pathResultContainer, items);
			}
		}

		if (!activity.getSupplies().isEmpty())
		{
			JLabel label = resultLine("Inventory", PRIMARY, 0);
			label.setBorder(new EmptyBorder(6, 0, 0, 0));
			stack(pathResultContainer, label);
			Pictured supplies = iconsFor(activity.getSupplies());
			stackIcons(supplies);
			for (String supply : supplies.getInWords())
			{
				JLabel line = resultLine("\u2013 " + supply, DETAIL, 12);
				line.setFont(SMALL_FONT);
				stack(pathResultContainer, line);
			}
		}

		if (activity.getHelperPlugin() != null)
		{
			stack(pathResultContainer, helperPointer(activity.getHelperPlugin(),
				activity.getHelperPluginId(), "will help you through the fight itself.",
				"in-fight help", 10));
		}

		if (activity.getStrategyUrl() != null)
		{
			JLabel link = resultLine("Full strategy on the wiki \u2197",
				QUIET_LINK, 10);
			link.setFont(SMALL_FONT);
			link.setToolTipText(activity.getStrategyUrl());
			makeClickable(link, () -> openUrl(activity.getStrategyUrl()));
			stack(pathResultContainer, link);
		}
	}

	/** At 240px wide, six 36px icons and their gaps is what fits inside the gutter. */
	private static final int ICON_COLUMNS = 6;
	/** Three rows. Past that the pictures stop being a glance and start being a page. */
	private static final int MAX_ICONS = ICON_COLUMNS * 3;

	/** What one written line turned into. */
	private static final class EntryIcons
	{
		/** Item id to item name, for the tooltip. */
		private final Map<Integer, String> names = new LinkedHashMap<>();
		/** Item id to the count the line asked for, where it named one. */
		private final Map<Integer, String> counts = new LinkedHashMap<>();
		/**
		 * True when the pictures say everything the line said: every option in it
		 * was matched, and it carried no explanation beyond naming items.
		 */
		private boolean complete;
	}

	/** Several lines' worth: the pictures, and whatever still has to be read. */
	private static final class Pictured
	{
		private final Map<Integer, String> names = new LinkedHashMap<>();
		private final Map<Integer, String> counts = new LinkedHashMap<>();
		private final List<String> inWords = new ArrayList<>();

		private List<String> getInWords()
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
	private Pictured iconsFor(List<String> entries)
	{
		Pictured pictured = new Pictured();
		for (String entry : entries)
		{
			EntryIcons icons = itemManager == null ? null : iconsForEntry(entry);
			// Past the cap the rest of the lines go back to being words, which is
			// the honest fallback: a fourth row of icons would be a page, and
			// dropping them silently would lose them.
			if (icons == null || icons.names.isEmpty() || pictured.names.size() >= MAX_ICONS)
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

	private EntryIcons iconsForEntry(String entry)
	{
		EntryIcons cached = iconCache.get(entry);
		if (cached != null)
		{
			return cached;
		}

		EntryIcons icons = new EntryIcons();
		// One option can expand into several phrases (a set names several slots),
		// and matching any one of them is enough for that option to be pictured.
		Map<String, Boolean> optionMatched = new LinkedHashMap<>();

		for (ItemTextParser.Phrase phrase : ItemTextParser.phrases(entry))
		{
			String term = ItemTextParser.searchTerm(phrase);
			List<ItemTextParser.Candidate> candidates = new ArrayList<>();
			for (ItemPrice price : itemManager.search(term))
			{
				candidates.add(new ItemTextParser.Candidate(price.getName(), price.getId()));
			}
			Integer id = ItemTextParser.pick(phrase, candidates);
			if (id == null)
			{
				// RuneLite's own search only covers tradeable items, so half the
				// gear in a boss guide - void, barrows gloves, an infernal cape -
				// is simply not in it. Fall back to the full item list, which has
				// coarser names but all of them.
				candidates = ItemNameIndex.search(term);
				id = ItemTextParser.pick(phrase, candidates);
			}
			optionMatched.merge(phrase.getText(), id != null, (a, b) -> a || b);
			if (id == null)
			{
				continue;
			}
			for (ItemTextParser.Candidate candidate : candidates)
			{
				if (candidate.getId() == id)
				{
					icons.names.putIfAbsent(id, candidate.getName());
					break;
				}
			}
			if (phrase.getQuantity() != null)
			{
				icons.counts.putIfAbsent(id, phrase.getQuantity());
			}
		}

		icons.complete = !ItemTextParser.hasExplanation(entry)
			&& !optionMatched.isEmpty()
			&& !optionMatched.containsValue(Boolean.FALSE);
		iconCache.put(entry, icons);
		return icons;
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
		stackIcons(pathResultContainer, pictured);
	}

	private void stackIcons(JPanel target, Pictured pictured)
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

	/** Quests and diary tiers are both Quest Helper's territory, not ours. */
	private static boolean isHandedOff(PathStep step)
	{
		return step.getType() == PathStep.Type.DO_QUEST
			|| step.getType() == PathStep.Type.DO_DIARY;
	}

	/**
	 * Renders the quests or diary tiers a path needs as a plain numbered list -
	 * name, order, and a wiki link on each - followed by a pointer to Quest
	 * Helper.
	 * <p>
	 * Deliberately shallow. Quest Helper is a large, mature plugin that walks
	 * players through quests, diaries, miniquests and combat tasks properly, and
	 * a second, worse walkthrough helps nobody. What this plugin knows that
	 * Quest Helper doesn't is *which* of them a particular goal requires and in
	 * what order - and that is the part that otherwise costs a player a long
	 * session of reading wiki pages.
	 */
	private void renderHandoffBlock(int number, List<PathStep> items)
	{
		boolean diaries = items.get(0).getType() == PathStep.Type.DO_DIARY;
		String noun = diaries
			? (items.size() == 1 ? "diary tier" : "diary tiers")
			: (items.size() == 1 ? "quest" : "quests");
		stack(pathResultContainer, hairline());
		stack(pathResultContainer, microLabel("Step " + number + " \u00b7 "
			+ (diaries ? "Diaries" : "Quests"), SUBTLE, 11));
		JLabel header = resultLine(items.size() + " " + noun + " needed first", PRIMARY, 0);
		header.setFont(STEP_TITLE_FONT);
		stack(pathResultContainer, header);

		for (PathStep item : items)
		{
			JLabel line = resultLine("\u2022 " + item.getTitle(), PRIMARY, 6);
			if (item.getWikiUrl() != null)
			{
				line.setToolTipText(item.getWikiUrl());
				makeClickable(line, () -> openUrl(item.getWikiUrl()));
			}
			stack(pathResultContainer, line);
		}

		stack(pathResultContainer, questHelperPointer(items.get(0).getType(), items.size()));
	}

	/**
	 * One line about Quest Helper, worded for what the player actually has
	 * installed - telling someone to "install Quest Helper" when it is already
	 * running in the same sidebar is the kind of thing that makes a plugin feel
	 * like it isn't paying attention.
	 */
	private JLabel questHelperPointer(PathStep.Type type, int count)
	{
		boolean diary = type == PathStep.Type.DO_DIARY;
		return helperPointer(QUEST_HELPER,
			"has " + (diary ? "the task list for " : "the walkthrough for ")
				+ (count == 1 ? (diary ? "this diary" : "this quest") : "each") + ".",
			"step-by-step help", 10);
	}

	/**
	 * One line about another plugin, worded for what the player actually has
	 * installed - telling someone to install a plugin that is already running in
	 * the same sidebar is the kind of thing that makes a plugin feel like it
	 * isn't paying attention.
	 */
	private JLabel helperPointer(String pluginName, String does, String offer, int indent)
	{
		return helperPointer(pluginName, null, does, offer, indent);
	}

	private JLabel helperPointer(String pluginName, String hubId, String does, String offer,
		int indent)
	{
		Plugin helper = findPlugin(pluginName);
		if (helper != null && pluginManager.isPluginEnabled(helper))
		{
			// Installed and running. If it has a sidebar panel, the sentence
			// opens it: naming a plugin and leaving the player to go and find
			// its icon is the same half-handoff as naming a wiki page and not
			// linking it.
			NavigationButton button = sidebarButton(pluginName);
			if (button == null)
			{
				JLabel line = resultLine(pluginName + " " + does, SUBTLE, indent);
				line.setFont(SMALL_FONT);
				return line;
			}

			JLabel line = resultLine(pluginName + " " + does + " Open it.", QUIET_LINK, indent);
			line.setFont(SMALL_FONT);
			line.setToolTipText("Opens the " + pluginName + " panel");
			makeClickable(line, () -> clientToolbar.openPanel(button));
			return line;
		}

		if (helper != null)
		{
			// Installed but switched off used to be a dead end: a sentence
			// telling the player to go and do something the panel could do
			// itself. One click, and the next redraw says it is ready.
			if (failedToStart.contains(pluginName))
			{
				JLabel failed = resultLine(pluginName + " would not start \u2014 switch it on in "
					+ "RuneLite's plugin list.", WARN, indent);
				failed.setFont(SMALL_FONT);
				return failed;
			}
			JLabel line = resultLine(pluginName + " is installed but switched off \u2014 turn it "
				+ "on for " + offer + ".", QUIET_LINK, indent);
			line.setFont(SMALL_FONT);
			line.setToolTipText("Enables " + pluginName + " in RuneLite");
			makeClickable(line, () -> enableHelper(helper, pluginName));
			return line;
		}

		JLabel line = resultLine("Install " + pluginName + " from the Plugin Hub for "
			+ offer + " \u2197", QUIET_LINK, indent);
		line.setFont(SMALL_FONT);
		String url = hubId == null ? PLUGIN_HUB_URL : PLUGIN_HUB_URL + "show/" + hubId;
		line.setToolTipText(url);
		makeClickable(line, () -> openUrl(url));
		return line;
	}

	/**
	 * Switches another plugin on, on the player's say-so.
	 * <p>
	 * This is the one thing the panel does to someone else's plugin, and it only
	 * happens on a click of a line that says so. Off the EDT, because starting a
	 * plugin runs its own startUp() and that is not this thread's work to block
	 * on; the redraw afterwards goes back to Swing.
	 */
	private void enableHelper(Plugin helper, String pluginName)
	{
		Thread worker = new Thread(() ->
		{
			try
			{
				pluginManager.setPluginEnabled(helper, true);
				pluginManager.startPlugin(helper);
			}
			catch (Exception couldNotStart)
			{
				failedToStart.add(pluginName);
			}
			SwingUtilities.invokeLater(this::redrawAfterHelperChange);
		}, "nextstep-enable-plugin");
		worker.setDaemon(true);
		worker.start();
	}

	/** Puts whatever is on screen back up, now that a helper's state has changed. */
	private void redrawAfterHelperChange()
	{
		if (lastPath != null)
		{
			renderPath(lastPath);
		}
		else if (mode == Mode.RANDOM)
		{
			renderRoll();
		}
		revalidate();
		repaint();
	}

	/**
	 * Looks a plugin up by the name in its PluginDescriptor. Name-based on
	 * purpose: it keeps this a one-way, read-only nod at another plugin with no
	 * compile-time dependency on it, so Next Step still builds and runs whether
	 * or not Quest Helper is installed.
	 *
	 * @see PluginLookup for the matching itself, and for the bug that put it in
	 * its own tested class
	 */
	private Plugin findPlugin(String descriptorName)
	{
		if (pluginManager == null)
		{
			return null;
		}
		List<Plugin> plugins = new ArrayList<>(pluginManager.getPlugins());
		List<String> names = new ArrayList<>(plugins.size());
		List<String> classNames = new ArrayList<>(plugins.size());
		for (Plugin plugin : plugins)
		{
			PluginDescriptor descriptor = plugin.getClass().getAnnotation(PluginDescriptor.class);
			names.add(descriptor == null ? null : descriptor.name());
			classNames.add(plugin.getClass().getName());
		}
		int at = PluginLookup.match(descriptorName, names, classNames);
		return at < 0 ? null : plugins.get(at);
	}

	/** One step on its own: caption, title, and its option or option list. */
	private void renderStep(int number, PathStep step)
	{
		stack(pathResultContainer, hairline());
		stack(pathResultContainer, microLabel(stepCaption(number, step), SUBTLE, 11));
		JLabel stepTitle = resultLine(step.getTitle(), PRIMARY, 0);
		stepTitle.setFont(STEP_TITLE_FONT);
		stack(pathResultContainer, stepTitle);

		if (!step.hasChoice())
		{
			renderOption(step, step.getAlternatives().get(0), false);
			// Reaching a quest or diary you asked for is exactly where
			// Quest Helper takes over.
			if (isHandedOff(step))
			{
				stack(pathResultContainer, questHelperPointer(step.getType(), 1));
			}
		}
		else if (expandedSteps.contains(step.getTitle()))
		{
			JLabel hint = resultLine("Click one to keep just that method:", SUBTLE, 10);
			hint.setFont(SMALL_FONT);
			stack(pathResultContainer, hint);

			PathStep.Option picked = chosenFrom(step);
			for (PathStep.Option option : step.getAlternatives())
			{
				renderOption(step, option, option == picked);
			}
			stack(pathResultContainer, toggleLink(step, "Collapse", null));
		}
		else
		{
			PathStep.Option picked = chosenFrom(step);
			PathStep.Option shown = picked != null ? picked : step.getAlternatives().get(0);
			renderOption(step, shown, picked != null);

			// Undecided steps also show the quest route, if there is one.
			// Training is the plugin's own path and stays the headline,
			// but a bracket a player can quest straight past shouldn't
			// have that fact folded away behind "show 1 other" - that
			// would be deciding for them in the other direction. Once
			// they have actually picked something, their choice stands
			// alone.
			int shownCount = 1;
			if (picked == null)
			{
				for (PathStep.Option other : step.getAlternatives())
				{
					if (other != shown && !other.getQuests().isEmpty())
					{
						renderOption(step, other, false);
						shownCount++;
					}
				}
			}

			int others = step.getAlternatives().size() - shownCount;
			if (picked != null)
			{
				stack(pathResultContainer, toggleLink(step, "Change method",
					others + (others == 1 ? " other" : " others")));
			}
			else if (others > 0)
			{
				stack(pathResultContainer, toggleLink(step, "Show " + others
					+ " other method" + (others == 1 ? "" : "s"), null));
			}
		}
	}

	/**
	 * The last index of the run of level brackets starting at `at` that all show
	 * the same method, or `at` itself when the next bracket differs.
	 * <p>
	 * Which method a bracket shows depends on what the player has chosen, so this
	 * cannot be settled in the planner - it is recomputed on every redraw. A
	 * bracket whose option list is open is never swept into a run: its choice is
	 * on screen and merging would hide it mid-decision.
	 */
	private int sameMethodRunEnd(List<PathStep> steps, int at)
	{
		PathStep first = steps.get(at);
		PathStep.Option shown = displayed(first);
		if (shown == null || !mergeable(first))
		{
			return at;
		}

		int end = at;
		while (end + 1 < steps.size())
		{
			PathStep next = steps.get(end + 1);
			if (!mergeable(next)
				|| !next.getSkill().equals(first.getSkill())
				|| next.getLevelFrom() != steps.get(end).getLevelTo())
			{
				break;
			}
			PathStep.Option nextShown = displayed(next);
			if (nextShown == null || !nextShown.getTitle().equals(shown.getTitle()))
			{
				break;
			}
			end++;
		}
		return end;
	}

	private boolean mergeable(PathStep step)
	{
		return step.getType() == PathStep.Type.TRAIN_SKILL
			&& step.hasLevels()
			&& !expandedSteps.contains(step.getTitle());
	}

	/** The option a collapsed step shows: the chosen one, else the top-rated one. */
	private PathStep.Option displayed(PathStep step)
	{
		PathStep.Option picked = chosenFrom(step);
		if (picked != null)
		{
			return picked;
		}
		return step.getAlternatives().isEmpty() ? null : step.getAlternatives().get(0);
	}

	/** This run's identity in the expanded set - its range plus its method. */
	private String runKey(List<PathStep> run)
	{
		PathStep first = run.get(0);
		PathStep last = run.get(run.size() - 1);
		PathStep.Option shown = displayed(first);
		return "run:" + first.getSkill() + ":" + first.getLevelFrom() + "-" + last.getLevelTo()
			+ ":" + (shown == null ? "" : shown.getTitle());
	}

	private PathStep.Option matching(PathStep step, String title)
	{
		for (PathStep.Option option : step.getAlternatives())
		{
			if (option.getTitle().equals(title))
			{
				return option;
			}
		}
		return null;
	}

	/**
	 * A run of brackets as one step: the whole range in the title, the summed xp
	 * in the caption, one estimate covering the lot.
	 * <p>
	 * The estimate is summed bracket by bracket rather than divided once, because
	 * the same method is often recorded as several rows with a rising rate -
	 * Knights of Ardougne goes 141k at 75 to 240k at 95 - and dividing the total
	 * xp by any one of those rates would be wrong for most of the range. The rate
	 * is then shown as the span it actually covers.
	 */

	/**
	 * One method's whole showing across a merged run of brackets: the levels it
	 * actually covers, the xp it would carry over them, and its rate as the span
	 * it really spans rather than any single bracket's figure.
	 * <p>
	 * Pulled out because the headline and the alternatives need exactly the same
	 * arithmetic. It used to exist only for the headline, which is why the other
	 * ways to train a skill were only visible by expanding the brackets - and a
	 * method recorded in eight brackets then printed eight near-identical cards
	 * for the player to read past. Pyramid Plunder opening at 91 is not a
	 * footnote to Thieving; it is the answer for a third of the range.
	 */
	private static final class Across
	{
		private PathStep.Option option;
		private int from = Integer.MAX_VALUE;
		private int to = 0;
		private long xp;
		private int rateLow = Integer.MAX_VALUE;

		private boolean found()
		{
			return option != null;
		}
	}

	private Across mergeAcross(List<PathStep> run, String title)
	{
		Across across = new Across();
		PathStep.Option template = null;
		String detail = null;
		double hours = 0;
		boolean timed = true;
		int rateHigh = 0;

		for (PathStep bracket : run)
		{
			PathStep.Option here = matching(bracket, title);
			if (here == null)
			{
				continue;
			}
			if (template == null)
			{
				template = here;
			}
			across.from = Math.min(across.from, bracket.getLevelFrom());
			across.to = Math.max(across.to, bracket.getLevelTo());
			across.xp += bracket.getXpCost();
			if (detail == null && here.getDetail() != null && !here.getDetail().isEmpty())
			{
				// A bracketed method carries its note on its first bracket only,
				// so a run starting part-way up it would otherwise show no note
				// at all - the one place the note is most wanted.
				detail = here.getDetail();
			}
			Integer rate = here.getXpPerHour();
			if (rate == null || rate <= 0)
			{
				// A rate-less bracket (a quest route) would make any total a lie
				// about part of the range, so no estimate is shown at all.
				timed = false;
				continue;
			}
			across.rateLow = Math.min(across.rateLow, rate);
			rateHigh = Math.max(rateHigh, rate);
			hours += bracket.getXpCost() / (double) rate;
		}

		// Shopping lists add up over the run. Taking the first bracket's would
		// understate a merged range by however many brackets followed it, which
		// is the one way a count like this can be worse than no count at all.
		Map<String, Long> totals = new LinkedHashMap<>();
		for (PathStep bracket : run)
		{
			PathStep.Option here = matching(bracket, title);
			if (here == null)
			{
				continue;
			}
			for (PathStep.Option.Needed line : here.getNeeded())
			{
				totals.merge(line.getItem(), line.getQuantity(), Long::sum);
			}
		}
		List<PathStep.Option.Needed> needed = new ArrayList<>();
		for (Map.Entry<String, Long> entry : totals.entrySet())
		{
			needed.add(new PathStep.Option.Needed(entry.getKey(), entry.getValue()));
		}

		if (template == null)
		{
			return across;
		}

		String meta = null;
		if (timed && hours > 0)
		{
			meta = "~" + XpTable.formatHours(hours) + " \u00b7 "
				+ (across.rateLow == rateHigh
					? XpTable.formatXp(across.rateLow) + " xp/hr"
					: XpTable.formatXp(across.rateLow) + "\u2013" + XpTable.formatXp(rateHigh)
						+ " xp/hr");
		}
		across.option = new PathStep.Option(template.getTitle(), detail, meta,
			template.getWikiUrl(), template.getQuests(), template.isPvpRisk(),
			template.isTickManipulation(), template.getXpPerHour(), template.getGear(),
			template.getSupplies())
			.withAttention(template.getAttention())
			.withNeeded(needed);
		return across;
	}

	private void renderMergedRun(int number, List<PathStep> run, String runKey)
	{
		PathStep first = run.get(0);
		PathStep last = run.get(run.size() - 1);
		PathStep.Option shown = displayed(first);
		boolean picked = chosenFrom(first) != null;

		long totalXp = 0;
		Set<String> allTitles = new LinkedHashSet<>();
		for (PathStep bracket : run)
		{
			totalXp += bracket.getXpCost();
			for (PathStep.Option option : bracket.getAlternatives())
			{
				allTitles.add(option.getTitle());
			}
		}

		String title = capitalize(first.getSkill()) + " " + first.getLevelFrom()
			+ " \u2192 " + last.getLevelTo();
		PathStep.Option merged = mergeAcross(run, shown.getTitle()).option;
		// Single-option by construction, so renderOption leaves the name
		// unclickable: choosing happens in the brackets, which are one link away.
		PathStep mergedStep = new PathStep(PathStep.Type.TRAIN_SKILL, title,
			java.util.Collections.singletonList(merged))
			.withXpCost(totalXp)
			.withLevels(first.getSkill(), first.getLevelFrom(), last.getLevelTo());

		stack(pathResultContainer, hairline());
		stack(pathResultContainer, microLabel(stepCaption(number, mergedStep), SUBTLE, 11));
		JLabel stepTitle = resultLine(title, PRIMARY, 0);
		stepTitle.setFont(STEP_TITLE_FONT);
		stack(pathResultContainer, stepTitle);
		renderOption(mergedStep, merged, picked);

		// A quest route that skips part of this range stays visible even merged.
		// Folding it away behind a link would be the same mistake as sorting it to
		// the top, in the other direction: the player is told to train, with the
		// shortcut off screen.
		Set<String> questsAlreadyShown = new HashSet<>();
		if (!picked)
		{
			Set<String> questsShown = questsAlreadyShown;
			for (PathStep bracket : run)
			{
				for (PathStep.Option option : bracket.getAlternatives())
				{
					if (option.getQuests().isEmpty()
						|| option.getTitle().equals(shown.getTitle())
						|| !questsShown.add(option.getTitle()))
					{
						continue;
					}

					int from = first.getLevelTo();
					int to = first.getLevelFrom();
					for (PathStep other : run)
					{
						if (matching(other, option.getTitle()) != null)
						{
							from = Math.min(from, other.getLevelFrom());
							to = Math.max(to, other.getLevelTo());
						}
					}
					if (from > first.getLevelFrom() || to < last.getLevelTo())
					{
						stack(pathResultContainer, microLabel("Shortcut \u00b7 covers "
							+ from + " \u2192 " + to, SUBTLE, 6, 12));
					}
					renderOption(bracket, option, false);
				}
			}
		}

		// The other ways to train this range, each once, merged the same way the
		// headline is - and deliberately NOT behind the bracket expansion. Those
		// are two different questions ("what else could I do?" and "where does
		// the rate change?"), and one link answering both meant the second
		// buried the first: Thieving 65-84 printed five near-identical
		// blackjacking cards, with Rogues' Castle and Pyramid Plunder somewhere
		// underneath them.
		List<Across> alternatives = new ArrayList<>();
		if (!picked)
		{
			for (String other : allTitles)
			{
				if (other.equals(shown.getTitle()) || questsAlreadyShown.contains(other))
				{
					continue;
				}
				Across across = mergeAcross(run, other);
				if (across.found())
				{
					alternatives.add(across);
				}
			}
			// Fastest first, by the rate at the bottom of each one's range: the
			// same figure, and the same order, the planner ranks a single
			// bracket's options by.
			alternatives.sort((a, b) -> b.rateLow != a.rateLow
				? Integer.compare(b.rateLow, a.rateLow)
				: a.option.getTitle().compareTo(b.option.getTitle()));
		}

		String altKey = runKey + "|alts";
		if (!alternatives.isEmpty())
		{
			if (expandedSteps.contains(altKey))
			{
				// A multi-option step, so each title stays clickable: picking one
				// of these is the same act as picking it inside a bracket.
				List<PathStep.Option> options = new ArrayList<>();
				for (Across across : alternatives)
				{
					options.add(across.option);
				}
				PathStep altStep = new PathStep(PathStep.Type.TRAIN_SKILL, title, options)
					.withLevels(first.getSkill(), first.getLevelFrom(), last.getLevelTo());
				JLabel hint = resultLine("Click one to keep just that method:", SUBTLE, 10);
				hint.setFont(SMALL_FONT);
				stack(pathResultContainer, hint);
				for (Across across : alternatives)
				{
					// Only where it does not cover the whole run. "Covers 91 to
					// 99" is the fact worth a line; repeating the step's own
					// levels under every option would not be.
					if (across.from > first.getLevelFrom() || across.to < last.getLevelTo())
					{
						stack(pathResultContainer, microLabel("Covers " + across.from
							+ " \u2192 " + across.to, SUBTLE, 6, 12));
					}
					renderOption(altStep, across.option, false);
				}
				stack(pathResultContainer, runLink(altKey, "Hide other methods"));
			}
			else
			{
				int others = alternatives.size();
				stack(pathResultContainer, runLink(altKey, "Show " + others + " other way"
					+ (others == 1 ? "" : "s") + " to train this"));
			}
		}

		stack(pathResultContainer, runLink(runKey, picked
			? "Change method (" + run.size() + " level brackets)"
			: "Show " + run.size() + " level brackets"));
	}

	/** Opens or closes a merged run of level brackets. */
	private JLabel runLink(String runKey, String text)
	{
		JLabel link = resultLine(text, QUIET_LINK, 10);
		link.setFont(SMALL_FONT);
		makeClickable(link, () ->
		{
			if (!expandedSteps.remove(runKey))
			{
				expandedSteps.add(runKey);
			}
			renderPath(lastPath);
		});
		return link;
	}

	private String capitalize(String text)
	{
		if (text == null || text.isEmpty())
		{
			return text == null ? "" : text;
		}
		return text.substring(0, 1).toUpperCase(java.util.Locale.ROOT)
			+ text.substring(1).toLowerCase(java.util.Locale.ROOT);
	}

	/**
	 * The option this step is collapsed to, or null while the choice is still
	 * open. Only collapses on an unambiguous match - if two of a step's options
	 * were somehow both chosen, showing the list again beats picking for them.
	 */
	private PathStep.Option chosenFrom(PathStep step)
	{
		PathStep.Option found = null;
		for (PathStep.Option option : step.getAlternatives())
		{
			if (chosenOptions.contains(option.getTitle()))
			{
				if (found != null)
				{
					return null;
				}
				found = option;
			}
		}
		return found;
	}

	/**
	 * One option: its title, its detail line and its wiki link. When the step
	 * still has an open choice the title is clickable, and clicking it collapses
	 * the step to that option.
	 */
	private void renderOption(PathStep step, PathStep.Option option, boolean selected)
	{
		JPanel card = optionCard(selected);
		int outer = cardInset;
		cardInset = CARD_INSET;
		try
		{
			fillOption(card, step, option, selected);
		}
		finally
		{
			cardInset = outer;
		}

		// A repeat whose every line was suppressed as already-said would
		// otherwise leave an empty box behind.
		if (card.getComponentCount() > 0)
		{
			stack(pathResultContainer, card);
		}
	}

	/** The contents of one option box. */
	private void fillOption(JPanel card, PathStep step, PathStep.Option option, boolean selected)
	{
		// A single-option step mirrors its own title into that option, so
		// printing both just says the same thing twice.
		if (!option.getTitle().equals(step.getTitle()))
		{
			String bullet = selected ? "\u2714 " : "";
			JLabel title = resultLine(bullet + option.getTitle(), selected ? CHOSEN : PRIMARY, 0);
			title.setFont(OPTION_FONT);
			if (step.hasChoice() && !selected)
			{
				makeClickable(title, () -> choose(step, option));
			}
			stack(card, title);
		}

		boolean questRoute = !option.getQuests().isEmpty();
		// The time estimate is deliberately NOT part of this key. It differs for
		// every segment a method spans, so counting it would mean the prose was
		// never a repeat and the same three-line note printed under all nine
		// Knights of Ardougne brackets.
		String body = String.join("", option.getTitle(), String.valueOf(option.getDetail()),
			String.valueOf(option.getQuests()));
		boolean alreadySaid = !optionBodiesShown.add(body);

		// Tags and the estimate always render. A Wilderness warning is not
		// something to suppress as a duplicate, and the estimate is the one part
		// that genuinely changed since the last time this method appeared.
		if (option.isPvpRisk())
		{
			stack(card, microLabel("Wilderness \u00b7 PvP risk", WARN, 3, 0));
		}
		if (option.isTickManipulation())
		{
			stack(card, microLabel("Tick manipulation", SUBTLE, 3, 0));
		}
		// The attention cost, where it has been judged. Deliberately not amber:
		// amber is reserved for "you can lose what you brought". A method being
		// hard work is a trade-off to weigh, not a warning.
		if (option.getAttention() == TrainingMethod.Attention.DEMANDING)
		{
			stack(card, microLabel("Constant input", SUBTLE, 3, 0));
		}
		else if (option.getAttention() == TrainingMethod.Attention.AFK)
		{
			stack(card, microLabel("Nearly AFK", SUBTLE, 3, 0));
		}
		if (option.getMeta() != null && !option.getMeta().isEmpty())
		{
			stack(card, microLabel(option.getMeta(), SUBTLE, 3, 0));
		}

		if (alreadySaid)
		{
			// Said in full once already. A quest route is worth pointing back to;
			// for an ordinary method the estimate above is the news.
			if (questRoute)
			{
				JLabel repeat = resultLine("The same quests as above \u2014 they cover this "
					+ "range too.", DETAIL, 0);
				repeat.setFont(SMALL_FONT);
				stack(card, repeat);
			}
			return;
		}


		if (option.getDetail() != null && !option.getDetail().isEmpty())
		{
			JLabel detail = resultLine(option.getDetail(), DETAIL, 0);
			detail.setFont(SMALL_FONT);
			stack(card, detail);
		}

		// What to take, in the box with the method that needs it. The same
		// pictures a boss setup gets: "Maniacal monkeys" answers where to go and
		// leaves the player in the bank wondering what to bring, which is the
		// question they were going to the wiki for.
		renderKit(card, "Wear", option.getGear());
		renderKit(card, "Bring", option.getSupplies());
		renderNeeded(card, option.getNeeded());

		// Name the quests, then let Quest Helper take the player through each
		// one and its own requirements; they come back here afterwards to pick
		// the path up.
		if (questRoute)
		{
			for (String quest : option.getQuests())
			{
				stack(card, resultLine("\u2022 " + quest, PRIMARY, 0));
			}
			stack(card,
				questHelperPointer(PathStep.Type.DO_QUEST, option.getQuests().size()));
		}

		if (option.getWikiUrl() != null)
		{
			stack(card, linkRow(java.util.Collections.singletonList(
				actionLink("Wiki \u2197", () -> openUrl(option.getWikiUrl()))), 0));
		}
	}

	/**
	 * What this range will use up, as pictures with the counts under them.
	 * <p>
	 * The same row the gear gets, because it is the same question one step
	 * later: the plan says 16 hours at a gilded altar, and the player's next
	 * move is to work out how many dragon bones that is and go and buy them.
	 * Doing that division in the panel is the difference between a plan you can
	 * read and a plan you can act on - and it was being done on the wiki, in
	 * another tab, with the numbers copied across by hand.
	 */
	private void renderNeeded(JPanel card, List<PathStep.Option.Needed> needed)
	{
		if (needed.isEmpty())
		{
			return;
		}

		stack(card, microLabel("Buy", SUBTLE, 6, 12));
		Pictured pictured = new Pictured();
		for (PathStep.Option.Needed line : needed)
		{
			EntryIcons icons = itemManager == null ? null : iconsForEntry(line.getItem());
			Integer id = null;
			if (icons != null && !icons.names.isEmpty())
			{
				id = icons.names.keySet().iterator().next();
			}
			if (id == null || pictured.names.size() >= MAX_ICONS)
			{
				// No sprite for it: the count is the point, so it goes back to
				// words rather than being dropped.
				pictured.inWords.add(exact(line.getQuantity()) + " "
					+ line.getItem().toLowerCase(java.util.Locale.ROOT));
				continue;
			}
			pictured.names.putIfAbsent(id, line.getItem());
			pictured.counts.put(id, exact(line.getQuantity()));
		}
		stackIcons(card, pictured);
		for (String words : pictured.getInWords())
		{
			JLabel line = resultLine(words, DETAIL, 0);
			line.setFont(SMALL_FONT);
			stack(card, line);
		}
	}

	/**
	 * A count in full, with thousands separators - never abbreviated.
	 * <p>
	 * Everywhere else in the panel an xp figure is rounded to "12.3M", because
	 * nobody needs the digits of an xp total. A shopping list is the opposite:
	 * "23k superior dragon bones" is not a number you can take to the Grand
	 * Exchange, and rounding it is how the player ends up two bones short at
	 * level 98.
	 */
	private static String exact(long quantity)
	{
		return String.format(java.util.Locale.ROOT, "%,d", quantity);
	}

	/** One labelled row of gear or supplies inside an option box. */
	private void renderKit(JPanel card, String label, List<String> items)
	{
		if (items.isEmpty())
		{
			return;
		}
		stack(card, microLabel(label, SUBTLE, 6, 0));
		Pictured pictured = iconsFor(items);
		stackIcons(card, pictured);
		for (String remaining : pictured.getInWords())
		{
			JLabel line = resultLine(remaining, DETAIL, 0);
			line.setFont(SMALL_FONT);
			stack(card, line);
		}
	}

	/** Records a choice for this step and redraws the plan around it. */
	private void choose(PathStep step, PathStep.Option option)
	{
		// Drop any sibling first, so a step never has two chosen options.
		for (PathStep.Option other : step.getAlternatives())
		{
			chosenOptions.remove(other.getTitle());
		}
		chosenOptions.add(option.getTitle());
		expandedSteps.remove(step.getTitle());
		// Picking a method re-forms the runs around it, so no run stays expanded
		// under a key that described the old arrangement.
		expandedSteps.removeIf(key -> key.startsWith("run:"));
		renderPath(lastPath);
	}

	/** Opens or closes a step's full option list. */
	private JLabel toggleLink(PathStep step, String text, String suffix)
	{
		JLabel link = resultLine(text + (suffix == null ? "" : " (" + suffix + ")"),
			QUIET_LINK, 10);
		link.setFont(SMALL_FONT);
		makeClickable(link, () ->
		{
			if (!expandedSteps.remove(step.getTitle()))
			{
				expandedSteps.add(step.getTitle());
			}
			renderPath(lastPath);
		});
		return link;
	}

	/** Hand cursor plus a hover tint, so a clickable line looks clickable. */
	private void makeClickable(JLabel label, Runnable onClick)
	{
		Color resting = label.getForeground();
		label.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
		label.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				onClick.run();
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				label.setForeground(ColorScheme.BRAND_ORANGE);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				label.setForeground(resting);
			}
		});
	}

	private JLabel wikiLink(String url, int indent)
	{
		JLabel link = resultLine("Wiki \u2197", QUIET_LINK, indent);
		link.setFont(SMALL_FONT);
		link.setToolTipText(url);
		link.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
		link.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				openUrl(url);
			}
		});
		return link;
	}

	private static void openUrl(String url)
	{
		try
		{
			LinkBrowser.browse(url);
		}
		catch (Exception ex)
		{
			log.warn("Failed to open wiki url", ex);
		}
	}

	private String prefixFor(PathStep.Type type)
	{
		switch (type)
		{
			case TRAIN_SKILL:
				return "[Train] ";
			case ACQUIRE_ITEM:
				return "[Get] ";
			case KILL_BOSS:
				return "[Drop] ";
			case DO_ACTIVITY:
				return "[Do] ";
			case DO_QUEST:
				return "[Quest] ";
			case DO_DIARY:
				return "[Diary] ";
			default:
				return "";
		}
	}

	/**
	 * A line of wrapped text. The HTML width is what makes the label wrap
	 * instead of stretching the panel; the indent is applied as a border so it
	 * doesn't eat into the wrap width.
	 */
	private JLabel resultLine(String text, Color color, int indent)
	{
		JLabel label = new JLabel(wrapped(escape(text), wrapWidth(indent)));
		label.setForeground(color);
		label.setFont(BODY_FONT);
		label.setBorder(new EmptyBorder(1, indent, 1, 0));
		label.setToolTipText(text);
		return label;
	}

	/**
	 * Wraps text at an exact pixel width.
	 *
	 * <p>This uses a one-cell table rather than the more obvious
	 * {@code <body style='width:Npx'>}: Swing's CSS support treats that width
	 * as advisory and still reports a preferred width ~45px larger, so the
	 * label overflows the sidebar and the tail of every line gets clipped. A
	 * table width is honoured exactly.
	 */
	private static String wrapped(String escapedText, int width)
	{
		return "<html><table cellpadding=0 cellspacing=0 border=0 width='" + width + "'>"
			+ "<tr><td>" + escapedText + "</td></tr></table></html>";
	}

	/**
	 * Wrap width for a line of text, measured from the live viewport rather
	 * than computed from panel constants - border, scrollbar and look-and-feel
	 * insets all eat into the real width, and guessing at them is what left
	 * text clipped off the right edge.
	 */
	/**
	 * How much of the width the current option box is eating, while one is being
	 * filled. Text wraps to a width this panel works out itself (Swing will not
	 * wrap a label otherwise), so a box drawn around it has to be subtracted or
	 * every line inside one runs under its right edge.
	 */
	private int cardInset;

	private int wrapWidth(int indent)
	{
		int available = scrollPane == null ? 0 : scrollPane.getViewport().getWidth();
		if (available <= 40)
		{
			available = TEXT_WIDTH;
		}
		else if (!scrollPane.getVerticalScrollBar().isVisible())
		{
			// Adding content is about to make the scrollbar appear and take
			// this width away again.
			available -= PluginPanel.SCROLLBAR_WIDTH;
		}
		return Math.max(60, available - indent - cardInset - 8);
	}

	private String escape(String s)
	{
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	/**
	 * Called from the Swing thread by NextStepPlugin after each refresh.
	 */
	public void update(List<Activity> almostAvailable)
	{
		update(java.util.Collections.emptyList(), almostAvailable);
	}

	/**
	 * @param availableNowList what the player can do right now, which is also
	 *                         the pool a roll draws from
	 */
	public void update(List<Activity> availableNowList, List<Activity> almostAvailable)
	{
		this.availableNow = availableNowList;
		if (mode == Mode.RANDOM && rolled == null)
		{
			// The panel can open before the first refresh arrives; once it does,
			// a roll that had nothing to draw from gets its answer.
			roll();
		}
		availableNowContainer.removeAll();
		almostThereContainer.removeAll();

		if (almostAvailable.isEmpty())
		{
			stack(almostThereContainer, resultLine("Nothing close enough to list yet.", SUBTLE, 0));
		}
		else
		{
			for (Activity activity : almostAvailable)
			{
				stack(almostThereContainer, new ActivityCard(activity, wrapWidth(14), this::planFor));
			}
		}

		availableNowContainer.revalidate();
		availableNowContainer.repaint();
		almostThereContainer.revalidate();
		almostThereContainer.repaint();
	}

	/**
	 * Makes the scrolled content adopt the viewport's width instead of its own
	 * preferred width. This is what actually removes horizontal scrolling - a
	 * plain JPanel would keep reporting the width of its widest child and the
	 * viewport would happily scroll sideways to show it.
	 */
	private static class ContentPanel extends JPanel implements Scrollable
	{
		@Override
		public Dimension getPreferredScrollableViewportSize()
		{
			return getPreferredSize();
		}

		@Override
		public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction)
		{
			return 16;
		}

		@Override
		public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction)
		{
			return visibleRect.height;
		}

		@Override
		public boolean getScrollableTracksViewportWidth()
		{
			return true;
		}

		/**
		 * False, always - and deliberately, because the obvious way to sink a
		 * footer is to return true while the content is shorter than the viewport
		 * and that hangs the layout.
		 * <p>
		 * Tracking the viewport height stretches the view, which can make the
		 * vertical scrollbar appear; the scrollbar takes width away, which is what
		 * this panel wraps its text against; narrower text is taller, so the
		 * content no longer fits, so tracking turns off, so the scrollbar goes
		 * away. Swing revalidates forever and the panel never paints. The footer
		 * is held down by a spacer this panel measures instead.
		 */
		@Override
		public boolean getScrollableTracksViewportHeight()
		{
			return false;
		}
	}

	/**
	 * A single clickable suggestion row: name on top, category underneath.
	 * Stacked rather than side-by-side because two labels sharing a 225px row
	 * is what forced the panel wider than the sidebar.
	 */
	/**
	 * A suggestion in the "Almost there" list. Clicking one plans the path to it
	 * rather than opening its wiki page: the list is the one place the panel
	 * volunteers something you didn't ask for, and sending that straight out to
	 * the browser wasted it.
	 */
	/**
	 * One suggestion in the "almost there" list.
	 * <p>
	 * Not a card any more. A boxed, filled panel was the one thing left in the
	 * sidebar still drawing chrome around its content, and it sat directly under
	 * the new opening screen, where the difference was impossible to miss. It
	 * reads like a step now: a rule, a caption, a name - the same three parts in
	 * the same order, so the eye learns one pattern instead of two.
	 */
	private static class ActivityCard extends JPanel
	{
		ActivityCard(Activity activity, int wrapWidth, Consumer<Activity> onSelect)
		{
			setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
			setBackground(ColorScheme.DARK_GRAY_COLOR);
			setBorder(new EmptyBorder(0, 0, 8, 0));
			setAlignmentX(Component.LEFT_ALIGNMENT);

			JPanel rule = new JPanel();
			rule.setBackground(HAIRLINE);
			rule.setAlignmentX(Component.LEFT_ALIGNMENT);
			rule.setPreferredSize(new Dimension(10, 1));
			rule.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
			add(rule);

			JLabel category = new JLabel(activity.getCategory().name()
				.replace('_', ' '));
			category.setForeground(SUBTLE);
			category.setFont(MICRO_FONT);
			category.setBorder(new EmptyBorder(8, 0, 1, 0));
			category.setAlignmentX(Component.LEFT_ALIGNMENT);
			add(category);

			JLabel title = new JLabel(wrapped(
				activity.getName().replace("&", "&amp;").replace("<", "&lt;"), wrapWidth));
			title.setForeground(PRIMARY);
			title.setFont(OPTION_FONT);
			title.setAlignmentX(Component.LEFT_ALIGNMENT);
			add(title);

			setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));

			setToolTipText("Click to plan a path to " + activity.getName());
			setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
			addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent e)
				{
					onSelect.accept(activity);
				}

				@Override
				public void mouseEntered(MouseEvent e)
				{
					title.setForeground(ACCENT);
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					title.setForeground(PRIMARY);
				}
			});
		}
	}
}
