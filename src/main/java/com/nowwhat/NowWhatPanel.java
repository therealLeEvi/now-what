package com.nowwhat;

import static com.nowwhat.PanelStyle.*;

import com.nowwhat.model.Activity;
import com.nowwhat.model.Goal;
import com.nowwhat.model.ItemSource;
import com.nowwhat.model.Path;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

public class NowWhatPanel extends PluginPanel
{
	// Matches the skill name strings NowWhatPlugin writes into PlayerState
	// (Skill.name() from RuneLite's Skill enum) - kept as a plain constant
	// here so the panel doesn't need a Client reference just to populate a dropdown.
	private static final String[] SKILLS = {
		"ATTACK", "DEFENCE", "STRENGTH", "HITPOINTS", "RANGED", "PRAYER", "MAGIC",
		"COOKING", "WOODCUTTING", "FLETCHING", "FISHING", "FIREMAKING", "CRAFTING",
		"SMITHING", "MINING", "HERBLORE", "AGILITY", "THIEVING", "SLAYER", "FARMING",
		"RUNECRAFT", "HUNTER", "CONSTRUCTION"
	};

	private static final String WIKI_URL = "https://oldschool.runescape.wiki/";
	private static final String LICENCE_URL = "https://creativecommons.org/licenses/by-nc-sa/3.0/";

	private final Function<Goal, Path> pathResolver;
	/** Activities by id, so a finished plan can look up what to wear and bring. */
	private final Map<String, Activity> activitiesById = new HashMap<>();
	/** The scrolling column everything is stacked into. */
	private JPanel content;
	/** The measured gap that holds the attribution at the foot of the panel. */
	private final JPanel footerSpacer = new JPanel();
	private final Map<String, ItemSource> itemSourcesById = new HashMap<>();

	private final JPanel availableNowContainer = new JPanel();
	private final JPanel almostThereContainer = new JPanel();
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
		RANDOM,
		/** The list of things the player is close to being able to do. */
		ALMOST
	}

	private Mode mode = Mode.START;
	/**
	 * True while a plan is on screen. The plan then has the sidebar to itself:
	 * the pickers above it and the suggestions below it made one very long
	 * column out of three unrelated things, and the plan - the answer to what
	 * the player just asked - was the part they had to scroll to find.
	 */
	private boolean planOpen;
	private JLabel almostThereHeader;
	private JPanel almostBackRow;
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
	/** Wrapped lines, links and the other small pieces every screen is built from. */
	private final PanelKit kit;
	/** The plan to a goal: its steps, the choices made in it, and what to bring. */
	private final PlanView plan;

	public NowWhatPanel(NowWhatConfig config, List<Activity> allActivities,
		List<ItemSource> allItemSources, Function<Goal, Path> pathResolver)
	{
		this(config, allActivities, allItemSources, pathResolver, null, null);
	}

	public NowWhatPanel(NowWhatConfig config, List<Activity> allActivities,
		List<ItemSource> allItemSources, Function<Goal, Path> pathResolver,
		PluginManager pluginManager)
	{
		this(config, allActivities, allItemSources, pathResolver, pluginManager, null);
	}

	public NowWhatPanel(NowWhatConfig config, List<Activity> allActivities,
		List<ItemSource> allItemSources, Function<Goal, Path> pathResolver,
		PluginManager pluginManager, ItemManager itemManager)
	{
		super(false);
		this.pathResolver = pathResolver;
		this.kit = new PanelKit(pluginManager);
		this.plan = new PlanView(kit, itemManager, pathResolver, activitiesById, itemSourcesById,
			this::sinkFooter);
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

		// The way back, and the roll.
		//
		// This screen had neither: once you picked "Plan a path" the opening
		// fork was gone for the rest of the session, and with it the only route
		// to a random suggestion - which is the half of the panel meant for the
		// player who has no goal in mind. Small, and in one row, because it is a
		// way out of this screen rather than part of the form.
		stack(chooseContainer, kit.linkRow(java.util.Arrays.asList(
			kit.actionLink("\u2190 Back", () -> setMode(Mode.START)),
			kit.actionLink("Surprise me", () ->
			{
				setMode(Mode.RANDOM);
				roll();
			}))));

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
				showPlan(pathResolver.apply(Goal.activity(selected.getId(), selected.getName(),
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
			showPlan(pathResolver.apply(Goal.skillLevel((String) skillCombo.getSelectedItem(),
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
			"Search for an item", "Type to search, or pick from the list");
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
				if (selected == null)
				{
					itemPicker.promptForChoice();
					return;
				}
				showPlan(pathResolver.apply(Goal.itemQuantity(selected.getId(),
					(Integer) itemQuantitySpinner.getValue(), selected.getDisplayName(),
					tickManipulationCheckbox.isSelected(), countOwnedCheckbox.isSelected())));
			}));

		// --- Goal: a floor under every skill ---
		// "I want 80+ in everything" is a question the panel could not be asked.
		// Answering it one skill at a time means working out which are short and
		// in what order by hand, which is the part a plan is for.
		baseStatsSpinner = new JSpinner(new SpinnerNumberModel(70, 2, 99, 1));
		constrainSpinner(baseStatsSpinner);
		stack(chooseContainer, goalBlock("Base stats", goalCaption("Every skill at least"),
			baseStatsSpinner, "Plan path", e ->
				showPlan(pathResolver.apply(Goal.baseStats(
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
				showPlan(pathResolver.apply(Goal.totalLevel(
					(Integer) totalLevelSpinner.getValue(),
					tickManipulationCheckbox.isSelected())))));

		// Sits with the other goal pickers, not under the plan: it is one of the
		// things you choose between when deciding what to do next.
		stack(chooseContainer, questsSection());

		plan.setOnClose(this::closePlan);
		content.add(plan.container());

		// --- Suggestions ---
		// "Available now" was a list of cards whose only action was to open the
		// wiki - the one thing this panel is trying to stop players needing to
		// do - so the space goes to the plan instead. The container survives
		// because the logged-out placeholder still renders into it.
		availableNowContainer.setLayout(new BoxLayout(availableNowContainer, BoxLayout.Y_AXIS));
		availableNowContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
		availableNowContainer.setAlignmentX(Component.LEFT_ALIGNMENT);
		content.add(availableNowContainer);

		almostThereHeader = sectionHeader("Almost there");
		stack(content, almostThereHeader);
		almostBackRow = kit.linkRow(java.util.Collections.singletonList(
			kit.actionLink("\u2190 Back", () -> setMode(Mode.START))));
		stack(content, almostBackRow);
		almostThereContainer.setLayout(new BoxLayout(almostThereContainer, BoxLayout.Y_AXIS));
		almostThereContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
		almostThereContainer.setAlignmentX(Component.LEFT_ALIGNMENT);
		content.add(almostThereContainer);
		setMode(Mode.START);

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
		kit.scrollPane = scrollPane;
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

	/**
	 * Pins a spinner to the width its own digits need.
	 * <p>
	 * This was a flat 56px, which fits two digits and an arrow box and clips the
	 * last digit off a total level of 2000. The editor is told how many columns
	 * the model's maximum actually takes, and the width then comes from the
	 * spinner rather than from a guess about fonts and arrow widths.
	 */
	private static void constrainSpinner(JSpinner spinner)
	{
		if (spinner.getEditor() instanceof JSpinner.DefaultEditor
			&& spinner.getModel() instanceof SpinnerNumberModel)
		{
			Comparable<?> max = ((SpinnerNumberModel) spinner.getModel()).getMaximum();
			int digits = max instanceof Number
				? String.valueOf(((Number) max).longValue()).length()
				: 2;
			((JSpinner.DefaultEditor) spinner.getEditor()).getTextField()
				.setColumns(Math.max(2, digits));
		}
		Dimension want = spinner.getPreferredSize();
		Dimension size = new Dimension(Math.max(56, want.width), want.height);
		spinner.setPreferredSize(size);
		spinner.setMinimumSize(size);
		spinner.setMaximumSize(size);
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
		// Its height is asked for every layout, never fixed. stack() caps a
		// block at the height it has when the panel is built, and this one is
		// built with the matches closed - so typing opened a list inside a block
		// that could not grow, and the list came out 6px tall: the field took
		// the text, and nothing showed underneath to say what it had found.
		JPanel block = new JPanel()
		{
			@Override
			public Dimension getMaximumSize()
			{
				return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
			}
		};
		block.setLayout(new BoxLayout(block, BoxLayout.Y_AXIS));
		block.setBackground(ColorScheme.DARK_GRAY_COLOR);
		block.setBorder(new EmptyBorder(6, 0, 2, 0));

		stack(block, kit.microLabel(caption, SUBTLE, 0));
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

		stack(block, kit.microLabel(caption, SUBTLE, 0));

		JPanel inputRow = new JPanel(new BorderLayout(4, 0));
		inputRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
		inputRow.add(input, BorderLayout.CENTER);
		if (trailing != null)
		{
			inputRow.add(trailing, BorderLayout.EAST);
		}
		// Tall enough for everything on the row, not just the thing in the
		// middle of it. Two of these rows have a plain caption in the centre and
		// the spinner beside it; a caption is some 15px tall, so taking the
		// height from the centre alone squeezed the spinner and cut the tops and
		// bottoms off its digits.
		int rowHeight = input.getPreferredSize().height;
		if (trailing != null)
		{
			rowHeight = Math.max(rowHeight, trailing.getPreferredSize().height);
		}
		inputRow.setPreferredSize(new Dimension(CONTENT_WIDTH, rowHeight));
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
		block.add(kit.hairline());
		block.add(Box.createVerticalStrut(8));

		// One line. The credit the licence asks for is the wiki's name and
		// the licence's, and both are here; the rest is on the hover, because
		// three paragraphs of it under a two-choice screen read as content.
		JLabel credit = kit.actionLink("Data: OSRS Wiki \u2197", () -> PanelKit.openUrl(WIKI_URL));
		credit.setToolTipText("<html>Data from the Old School RuneScape Wiki, used under "
			+ "CC BY-NC-SA 3.0.<br>This plugin owns none of it.<br>Not affiliated with or endorsed "
			+ "by Jagex. RuneScape and Old School<br>RuneScape are trademarks of Jagex Ltd.</html>");
		JLabel licence = kit.actionLink("CC BY-NC-SA 3.0 \u2197", () -> PanelKit.openUrl(LICENCE_URL));
		licence.setToolTipText(LICENCE_URL);
		block.add(kit.linkRow(java.util.Arrays.asList(credit, licence)));

		return block;
	}

	/**
	 * Plans a path to a suggested activity, as if it had been picked from the
	 * Activity dropdown - which it also is, so the controls agree with the
	 * result on screen rather than the plan appearing to come from nowhere.
	 */
	void planFor(Activity activity)
	{
		activityCombo.setSelectedItem(activity);
		showPlan(pathResolver.apply(Goal.activity(activity.getId(), activity.getName(),
			tickManipulationCheckbox.isSelected())));
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

		block.add(kit.microLabel("Quests", SUBTLE, 0));

		JLabel body = kit.resultLine("Pick a quest in Quest Helper \u2014 it lists every one you "
			+ "can start and walks you through it. Quests needed for a goal here still "
			+ "show up in its plan.", DETAIL, 0);
		body.setFont(SMALL_FONT);
		body.setBorder(new EmptyBorder(3, 0, 0, 0));
		block.add(body);

		JLabel pointer = kit.helperPointer(PanelKit.QUEST_HELPER, "quest-helper",
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
		stack(startContainer, startOption("Almost there",
			"What you are a few levels or a quest away from being able to do.",
			() -> setMode(Mode.ALMOST)));
	}

	private JPanel startOption(String title, String blurb, Runnable action)
	{
		// The two things to do on the opening screen are the clearest case of
		// all for a box: nothing else is on screen to tell them apart from the
		// sentence sitting under each of them.
		JPanel block = kit.optionCard(false);
		int outer = kit.cardInset;
		kit.cardInset = CARD_INSET;
		try
		{
			JLabel heading = kit.resultLine(title + " \u2192", ACCENT, 0);
			heading.setFont(STEP_TITLE_FONT);
			kit.makeClickable(heading, action);
			stack(block, heading);

			JLabel detail = kit.resultLine(blurb, DETAIL, 0);
			detail.setFont(SMALL_FONT);
			stack(block, detail);
		}
		finally
		{
			kit.cardInset = outer;
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
		planOpen = false;
		// A plan belongs to the question that produced it.
		plan.clearPath();
		if (next != Mode.RANDOM)
		{
			randomContainer.removeAll();
		}
		showScreen();
	}

	/**
	 * Puts a plan on screen by itself; its own Back link closes it. Whatever screen
	 * asked for it is hidden, not cleared, so going back finds the pickers as
	 * they were left and a rolled suggestion still rolled.
	 */
	private void showPlan(Path path)
	{
		planOpen = true;
		showScreen();
		plan.renderPath(path);
	}

	private void closePlan()
	{
		planOpen = false;
		plan.clearPath();
		showScreen();
	}

	/** Makes what is visible agree with the mode and with whether a plan is open. */
	private void showScreen()
	{
		startContainer.setVisible(!planOpen && mode == Mode.START);
		chooseContainer.setVisible(!planOpen && mode == Mode.CHOOSE);
		randomContainer.setVisible(!planOpen && mode == Mode.RANDOM);
		plan.container().setVisible(planOpen);
		availableNowContainer.setVisible(!planOpen);
		// A screen of its own, reached from the opening one. Sitting under
		// every other screen it made each of them a long page, and the opening
		// screen - two choices - the longest of all.
		boolean almost = !planOpen && mode == Mode.ALMOST;
		almostThereHeader.setVisible(almost);
		almostBackRow.setVisible(almost);
		almostThereContainer.setVisible(almost);
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
			stack(randomContainer, kit.resultLine("Nothing to pick from in that category.", SUBTLE, 0));
		}
		else
		{
			stack(randomContainer, kit.hairline());
			String kind = rolled.getCategory() == null
				? "Activity"
				: rolled.getCategory().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
			stack(randomContainer, kit.microLabel(kind, SUBTLE, 11));

			JLabel name = kit.resultLine(rolled.getName(), PRIMARY, 0);
			name.setFont(STEP_TITLE_FONT);
			stack(randomContainer, name);

			if (rolled.getDescription() != null && !rolled.getDescription().isEmpty())
			{
				JLabel description = kit.resultLine(rolled.getDescription(), DETAIL, 0);
				description.setFont(SMALL_FONT);
				stack(randomContainer, description);
			}

			if (availableNow.isEmpty())
			{
				stack(randomContainer, kit.resultLine("! Rolled from everything \u2014 log in so this "
					+ "can pick something you can actually do now.", Color.ORANGE, 0));
			}

			final Activity picked = rolled;
			stack(randomContainer, kit.linkRow(java.util.Arrays.asList(
				kit.actionLink("Plan it \u2192", () -> planFor(picked)),
				kit.actionLink("Roll again", this::roll))));
		}

		stack(randomContainer, kit.linkRow(java.util.Collections.singletonList(
			kit.actionLink("\u2190 Back", () -> setMode(Mode.START)))));
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
		rows.add(kit.linkRow(top));
		rows.add(kit.linkRow(bottom));
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
			kit.makeClickable(chip, () ->
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
		stack(availableNowContainer, sectionHeader("Now What"));
		stack(availableNowContainer, kit.resultLine(
			"Log in to see suggestions based on your progress.", SUBTLE, 0));
	}

	/** Lets gear be drawn as a worn-equipment loadout, which needs the game thread for slots. */
	public void setClientThread(ClientThread clientThread)
	{
		plan.setClientThread(clientThread);
	}

	/**
	 * The player's current levels, pushed in by NowWhatPlugin on the Swing
	 * thread after each refresh.
	 * <p>
	 * A plan is rendered once and then sat there while the player went and did
	 * it, so a step stayed on screen after it was finished and the list only
	 * ever got longer to scroll through. The rendered plan is rebuilt when, and
	 * only when, this changes which steps count as finished - it is called on
	 * every refresh, and rebuilding the panel each time would make it flicker
	 * for no reason.
	 */
	public void setProgress(Map<String, Integer> currentLevels)
	{
		plan.setProgress(currentLevels, java.util.Collections.emptySet());
	}

	/**
	 * @param finishedActivities the name of every quest, diary tier and other
	 *                           activity with a done state that the player has
	 *                           reached
	 */
	public void setProgress(Map<String, Integer> currentLevels, Set<String> finishedActivities)
	{
		plan.setProgress(currentLevels, finishedActivities);
	}

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
		update(availableNowList, almostAvailable, java.util.Collections.emptyMap());
	}

	/**
	 * @param missingById per activity id, the line saying what is still in the
	 *                    way ("Needs 91 Slayer, you have 87"). A suggestion
	 *                    that only named a category and a boss left the player
	 *                    to work out for themselves why it was "almost".
	 */
	public void update(List<Activity> availableNowList, List<Activity> almostAvailable,
		Map<String, String> missingById)
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
			stack(almostThereContainer, kit.resultLine("Nothing close enough to list yet.", SUBTLE, 0));
		}
		else
		{
			for (Activity activity : almostAvailable)
			{
				stack(almostThereContainer, new ActivityCard(activity, missingById.get(activity.getId()),
					kit.wrapWidth(14), this::planFor));
			}
		}

		availableNowContainer.revalidate();
		availableNowContainer.repaint();
		almostThereContainer.revalidate();
		almostThereContainer.repaint();
	}
}
