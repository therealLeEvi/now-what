package com.nowwhat;

import static com.nowwhat.PanelStyle.*;

import com.nowwhat.engine.XpTable;
import com.nowwhat.model.Activity;
import com.nowwhat.model.Goal;
import com.nowwhat.model.ItemSource;
import com.nowwhat.model.Path;
import com.nowwhat.model.PathStep;
import com.nowwhat.model.TrainingMethod;
import java.awt.Color;
import java.awt.Component;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;

/**
 * The plan to a goal as the sidebar shows it: the steps in order, which
 * method the player picked for each, what is already behind them, how to do
 * the thing at the end and what to take to it.
 * <p>
 * Split out of the panel, which keeps the goal pickers and the opening
 * screens. Everything a plan remembers between redraws lives here.
 */
class PlanView
{
	private final JPanel pathResultContainer = new JPanel();
	/**
	 * The player's real levels, for telling which plan steps are behind them.
	 * Empty when logged out, which means nothing is finished - the honest answer
	 * when the panel has nothing to go on.
	 */
	private Map<String, Integer> levels = java.util.Collections.emptyMap();
	/** Names of the quests, diary tiers and the like that the game reports as complete. */
	private Set<String> doneActivities = java.util.Collections.emptySet();
	/**
	 * Whether finished steps are listed or folded away. Folded by default: a
	 * base-stats plan over thirteen skills is long enough that scrolling past
	 * the part you have already done to reach the part you are on is the main
	 * thing wrong with it.
	 */
	private boolean showFinishedSteps;
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

	private final PanelKit kit;
	/** Everything that turns written gear and supply lines into pictures. */
	private final GearView gear;
	private final Function<Goal, Path> pathResolver;
	/** Activities by id, so a finished plan can look up what to wear and bring. */
	private final Map<String, Activity> activitiesById;
	private final Map<String, ItemSource> itemSourcesById;
	/** Asks the panel to re-seat its footer once a plan has changed the page's height. */
	private final Runnable sinkFooter;

	PlanView(PanelKit kit, ItemManager itemManager, Function<Goal, Path> pathResolver,
		Map<String, Activity> activitiesById, Map<String, ItemSource> itemSourcesById,
		Runnable sinkFooter)
	{
		this.kit = kit;
		this.pathResolver = pathResolver;
		this.activitiesById = activitiesById;
		this.itemSourcesById = itemSourcesById;
		this.sinkFooter = sinkFooter;
		pathResultContainer.setLayout(new BoxLayout(pathResultContainer, BoxLayout.Y_AXIS));
		pathResultContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
		pathResultContainer.setAlignmentX(Component.LEFT_ALIGNMENT);
		this.gear = new GearView(itemManager, pathResultContainer, kit::resultLine, () ->
		{
			if (lastPath != null)
			{
				// Not scrolled to the top: the player did not ask for this redraw.
				renderPath(lastPath, false);
			}
		});
	}

	/** What the plan's Back link does. The panel replaces this to bring its own screens back. */
	private Runnable onClose = this::clearPath;

	void setOnClose(Runnable onClose)
	{
		this.onClose = onClose;
	}

	/** The column the plan is drawn into, for the panel to place. */
	JPanel container()
	{
		return pathResultContainer;
	}

	/** Lets gear be drawn as a worn-equipment loadout, which needs the game thread for slots. */
	void setClientThread(ClientThread clientThread)
	{
		gear.setClientThread(clientThread);
	}

	/**
	 * Renders a Path (from any of the three goal types) as an ordered,
	 * numbered list of concrete steps, plus any warnings the planner hit.
	 */
	void renderPath(Path path)
	{
		renderPath(path, true);
	}

	private void renderPath(Path path, boolean scrollToTop)
	{
		lastPath = path;
		optionBodiesShown.clear();
		pathResultContainer.removeAll();

		JLabel titleLabel = kit.resultLine(path.getGoal().getLabel(), ACCENT, 0);
		titleLabel.setFont(STEP_TITLE_FONT);
		titleLabel.setBorder(new EmptyBorder(10, 0, 0, 0));
		stack(pathResultContainer, titleLabel);

		// The way out. A plan has the sidebar to itself, so this is the only
		// route back to the pickers and the suggestions.
		stack(pathResultContainer, kit.linkRow(java.util.Collections.singletonList(
			kit.actionLink("\u2190 Back", () -> onClose.run()))));

		if (path.isAlreadyComplete())
		{
			stack(pathResultContainer, kit.resultLine("Already done.", ColorScheme.PROGRESS_COMPLETE_COLOR, 0));

			// An item goal can be "already done" purely because one is sitting in
			// the bank, which is a dead end for the player who wants a second one
			// - or whose bank this plugin has read wrong. Offer the route anyway
			// rather than making them inflate the quantity until a plan appears.
			Goal goal = path.getGoal();
			if (goal.getType() == Goal.Type.ITEM_QUANTITY && goal.isCountOwned())
			{
				stack(pathResultContainer, kit.linkRow(java.util.Collections.singletonList(
					kit.actionLink("Plan it anyway, as if I had none",
						() -> renderPath(pathResolver.apply(goal.ignoringWhatIsOwned()))))));
			}
		}
		else if (path.isRequirementsAlreadyMet())
		{
			stack(pathResultContainer, kit.resultLine("You already meet every requirement, so you can "
				+ "start this now.", ColorScheme.PROGRESS_COMPLETE_COLOR, 0));
			// Meeting the requirements answers "can I", not "what do I do". For
			// a birdhouse or herb run the how is the whole answer, and it lives
			// in the description; without this the plan stopped at the line
			// above. A quest or diary's description is a one-word label, and
			// Quest Helper below says the rest.
			String howTo = howTo(targetActivity(path));
			if (howTo != null)
			{
				renderHowTo(howTo);
			}
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
						stack(pathResultContainer, kit.questHelperPointer(step.getType(), 1));
						break;
					}
				}

				for (PathStep step : path.getSteps())
				{
					for (PathStep.Option option : step.getAlternatives())
					{
						if (option.getWikiUrl() != null)
						{
							stack(pathResultContainer, kit.wikiLink(option.getWikiUrl(), 0));
						}
					}
				}
			}
		}
		else if (path.getSteps().isEmpty() && path.getWarnings().isEmpty())
		{
			stack(pathResultContainer, kit.resultLine("Nothing to plan \u2014 should be achievable right now.", PRIMARY, 0));
		}
		else
		{
			int i = 1;
			List<PathStep> steps = path.getSteps();

			if (onlyRecommendedInTheWay(path))
			{
				// The suggestion list holds a fight back until the recommended
				// stats are met; the plan has to say the same thing it does,
				// without pretending the game is what stops the player.
				stack(pathResultContainer, kit.resultLine("You meet every requirement, so nothing stops "
					+ "you going now. The steps below only close the gap to the stats the wiki "
					+ "recommends.", ColorScheme.PROGRESS_COMPLETE_COLOR, 0));
			}

			// Steps the player is already past come out of the list, so what is
			// left is what there is left to do. They are a link away, not gone:
			// a plan that quietly drops things is a plan you cannot check.
			int done = finishedCount(path);
			if (done > 0)
			{
				stack(pathResultContainer, doneLink(
					showFinishedSteps
						? "Hide the " + done + " finished"
						: done + " step" + (done == 1 ? "" : "s") + " done already "
							+ "\u2014 show " + (done == 1 ? "it" : "them")));
				if (!showFinishedSteps)
				{
					List<PathStep> left = new ArrayList<>();
					for (PathStep step : steps)
					{
						if (!isFinished(step))
						{
							left.add(step);
						}
					}
					steps = left;
				}
			}
			if (steps.isEmpty() && done > 0 && !showFinishedSteps)
			{
				stack(pathResultContainer, kit.resultLine("Every step in this plan is behind you "
					+ "\u2014 plan it again to see what is next.",
					ColorScheme.PROGRESS_COMPLETE_COLOR, 0));
			}
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

			// What you actually do there, whether or not anything was in the
			// way. It used to show only once every requirement was met, so the
			// player who most needed telling got a list of levels and no word on
			// what the levels were for.
			String howTo = howTo(targetActivity(path));
			if (howTo != null)
			{
				renderHowTo(howTo);
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
			stack(pathResultContainer, kit.resultLine("! " + warning, Color.ORANGE, 0));
		}

		pathResultContainer.revalidate();
		pathResultContainer.repaint();

		// Results render below three goal blocks, so on a short sidebar they can
		// land off-screen; bring them into view rather than leaving the user to
		// wonder whether the click did anything.
		SwingUtilities.invokeLater(() ->
		{
			sinkFooter.run();
			if (!scrollToTop)
			{
				return;
			}
			int visible = Math.min(pathResultContainer.getHeight(), kit.scrollPane.getViewport().getHeight());
			pathResultContainer.scrollRectToVisible(new Rectangle(0, 0, 1, visible));
		});
	}

	/** True when the plan has steps on the way to its goal and every one is only recommended. */
	private static boolean onlyRecommendedInTheWay(Path path)
	{
		boolean any = false;
		for (PathStep step : path.getSteps())
		{
			if (step.isTarget())
			{
				continue;
			}
			if (!step.isRecommended())
			{
				return false;
			}
			any = true;
		}
		return any;
	}

	/** The activity a path is aimed at, or null for skill and item goals. */
	private Activity targetActivity(Path path)
	{
		String id = path.getGoal().getActivityId();
		return id == null ? null : activitiesById.get(id);
	}

	/** The activity's own description, when it says more than what kind of thing it is. */
	static String howTo(Activity activity)
	{
		if (activity == null || activity.getHowTo() == null)
		{
			return null;
		}
		Activity.Category category = activity.getCategory();
		if (category == Activity.Category.QUEST || category == Activity.Category.DIARY)
		{
			return null;
		}
		String text = activity.getHowTo().trim();
		return text.isEmpty() ? null : text;
	}

	/**
	 * The how-to text, laid out: a gap before each paragraph, a short line
	 * ending in a colon as a heading, and "* " lines as an indented list under
	 * it, in the same dash-and-indent style as the inventory below.
	 */
	/** The list marker the inventory uses, as a number so the source stays ASCII. */
	private static final String EN_DASH = String.valueOf((char) 0x2013);

	private void renderHowTo(String text)
	{
		for (String raw : text.split("\n"))
		{
			String line = raw.trim();
			if (line.isEmpty())
			{
				continue;
			}
			JLabel label;
			if (line.startsWith("* "))
			{
				label = kit.resultLine(EN_DASH + " " + line.substring(2).trim(), DETAIL, 12);
				label.setFont(SMALL_FONT);
			}
			else if (line.endsWith(":") && line.length() <= 60)
			{
				label = kit.resultLine(line.substring(0, line.length() - 1), PRIMARY, 0);
				label.setBorder(new EmptyBorder(8, 0, 1, 0));
			}
			else
			{
				// A paragraph of five sentences is a wall in a column this
				// narrow, so prose is broken into a couple of sentences at a time.
				for (String chunk : paragraphs(line))
				{
					JLabel paragraph = kit.resultLine(chunk, DETAIL, 0);
					paragraph.setBorder(new EmptyBorder(6, 0, 1, 0));
					stack(pathResultContainer, paragraph);
				}
				continue;
			}
			stack(pathResultContainer, label);
		}
	}

	/** Sentences regrouped into paragraphs of about two, never splitting one. */
	static List<String> paragraphs(String prose)
	{
		List<String> out = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		for (String sentence : prose.split("(?<=[.!?])\\s+(?=[A-Z])"))
		{
			if (current.length() > 0 && current.length() + sentence.length() > PARAGRAPH_CHARS)
			{
				out.add(current.toString());
				current.setLength(0);
			}
			if (current.length() > 0)
			{
				current.append(' ');
			}
			current.append(sentence);
		}
		if (current.length() > 0)
		{
			out.add(current.toString());
		}
		return out;
	}

	/** Roughly two sentences, or four or five lines at sidebar width. */
	private static final int PARAGRAPH_CHARS = 170;

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
		if (source == null)
		{
			return null;
		}
		// A crafted item has no fight of its own, but its pieces do. An abyssal
		// bludgeon is three Sire drops and a venator bow is Muspah shards, and
		// the plan for either is a trip to that boss - so its gear is what the
		// player needs. Only when every piece comes from the same boss, though:
		// a godsword's hilt and blade shards are four different fights, and
		// picking one boss's gear would answer a quarter of the question.
		Set<String> fights = new LinkedHashSet<>();
		fightsBehind(source, fights, new HashSet<>());
		if (fights.size() != 1)
		{
			return null;
		}
		Activity fight = activitiesById.get(fights.iterator().next());
		return fight != null && fight.hasPreparation() ? fight : null;
	}

	/** Every boss page an item or its recipe's pieces come from. */
	private void fightsBehind(ItemSource source, Set<String> fights, Set<String> visiting)
	{
		if (!visiting.add(source.getId()))
		{
			return;
		}
		if (source.getPreparationFrom() != null)
		{
			fights.add(source.getPreparationFrom());
		}
		for (ItemSource.Ingredient ingredient : source.getIngredients())
		{
			ItemSource part = itemSourcesById.get(ingredient.getItemId());
			if (part != null)
			{
				fightsBehind(part, fights, visiting);
			}
		}
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
		stack(pathResultContainer, kit.hairline());
		// Named when the gear is someone else's: asking for a pet snakeling and
		// being handed a blowpipe and anti-venom only makes sense once the panel
		// has said the word "Zulrah" out loud.
		String whose = borrowedFrom == null
			? "What to bring"
			: "What to bring to " + borrowedFrom;
		stack(pathResultContainer, kit.microLabel(whose, SUBTLE, 11));

		for (List<Activity.GearSetup> family : GearView.setupFamilies(activity.getSetups()).values())
		{
			if (family.size() > 1)
			{
				String first = family.get(0).getName().trim();
				if (gear.renderSetupFamily(first.substring(0, first.indexOf(" (")), family))
				{
					continue;
				}
			}
			for (Activity.GearSetup setup : family)
			{
				gear.renderSetup(setup);
			}
		}

		if (!activity.getSupplies().isEmpty())
		{
			JLabel label = kit.resultLine("Inventory", PRIMARY, 0);
			label.setBorder(new EmptyBorder(6, 0, 0, 0));
			stack(pathResultContainer, label);
			GearView.Pictured supplies = gear.iconsFor(activity.getSupplies());
			gear.stackIcons(pathResultContainer, supplies);
			for (String supply : supplies.getInWords())
			{
				JLabel line = kit.resultLine("\u2013 " + supply, DETAIL, 12);
				line.setFont(SMALL_FONT);
				stack(pathResultContainer, line);
			}
		}

		if (activity.getHelperPlugin() != null)
		{
			stack(pathResultContainer, kit.helperPointer(activity.getHelperPlugin(),
				activity.getHelperPluginId(), "will help you through the fight itself.",
				"in-fight help", 10));
		}

		if (activity.getStrategyUrl() != null)
		{
			JLabel link = kit.resultLine("Full strategy on the wiki \u2197",
				QUIET_LINK, 10);
			link.setFont(SMALL_FONT);
			link.setToolTipText(activity.getStrategyUrl());
			kit.makeClickable(link, () -> PanelKit.openUrl(activity.getStrategyUrl()));
			stack(pathResultContainer, link);
		}
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
		stack(pathResultContainer, kit.hairline());
		stack(pathResultContainer, kit.microLabel("Step " + number + " \u00b7 "
			+ (diaries ? "Diaries" : "Quests"), SUBTLE, 11));
		JLabel header = kit.resultLine(items.size() + " " + noun + " needed first", PRIMARY, 0);
		header.setFont(STEP_TITLE_FONT);
		stack(pathResultContainer, header);

		for (PathStep item : items)
		{
			JLabel line = kit.resultLine("\u2022 " + item.getTitle(), PRIMARY, 6);
			if (item.getWikiUrl() != null)
			{
				line.setToolTipText(item.getWikiUrl());
				kit.makeClickable(line, () -> PanelKit.openUrl(item.getWikiUrl()));
			}
			stack(pathResultContainer, line);
		}

		stack(pathResultContainer, kit.questHelperPointer(items.get(0).getType(), items.size()));
	}

	/** One step on its own: caption, title, and its option or option list. */
	private void renderStep(int number, PathStep step)
	{
		stack(pathResultContainer, kit.hairline());
		stack(pathResultContainer, kit.microLabel(stepCaption(number, step), SUBTLE, 11));
		JLabel stepTitle = kit.resultLine(step.getTitle(), PRIMARY, 0);
		stepTitle.setFont(STEP_TITLE_FONT);
		stack(pathResultContainer, stepTitle);

		if (!step.hasChoice())
		{
			renderOption(step, step.getAlternatives().get(0), false);
			// Reaching a quest or diary you asked for is exactly where
			// Quest Helper takes over.
			if (isHandedOff(step))
			{
				stack(pathResultContainer, kit.questHelperPointer(step.getType(), 1));
			}
		}
		else if (expandedSteps.contains(step.getTitle()))
		{
			JLabel hint = kit.resultLine("Click one to keep just that method:", SUBTLE, 10);
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
				|| next.isRecommended() != first.isRecommended()
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
		if (first.isRecommended())
		{
			mergedStep.asRecommended();
		}

		stack(pathResultContainer, kit.hairline());
		stack(pathResultContainer, kit.microLabel(stepCaption(number, mergedStep), SUBTLE, 11));
		JLabel stepTitle = kit.resultLine(title, PRIMARY, 0);
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
						stack(pathResultContainer, kit.microLabel("Shortcut \u00b7 covers "
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
				JLabel hint = kit.resultLine("Click one to keep just that method:", SUBTLE, 10);
				hint.setFont(SMALL_FONT);
				stack(pathResultContainer, hint);
				for (Across across : alternatives)
				{
					// Only where it does not cover the whole run. "Covers 91 to
					// 99" is the fact worth a line; repeating the step's own
					// levels under every option would not be.
					if (across.from > first.getLevelFrom() || across.to < last.getLevelTo())
					{
						stack(pathResultContainer, kit.microLabel("Covers " + across.from
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
	/**
	 * Folds the steps the player is already past away, or brings them back.
	 * <p>
	 * Its own state rather than an entry in {@code expandedSteps}: that set is
	 * keyed by step, and this is one switch for the whole plan.
	 */
	private JLabel doneLink(String text)
	{
		JLabel link = kit.resultLine(text, QUIET_LINK, 10);
		link.setFont(SMALL_FONT);
		kit.makeClickable(link, () ->
		{
			showFinishedSteps = !showFinishedSteps;
			renderPath(lastPath);
		});
		return link;
	}

	private JLabel runLink(String runKey, String text)
	{
		JLabel link = kit.resultLine(text, QUIET_LINK, 10);
		link.setFont(SMALL_FONT);
		kit.makeClickable(link, () ->
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
		JPanel card = kit.optionCard(selected);
		int outer = kit.cardInset;
		kit.cardInset = CARD_INSET;
		try
		{
			fillOption(card, step, option, selected);
		}
		finally
		{
			kit.cardInset = outer;
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
			JLabel title = kit.resultLine(bullet + option.getTitle(), selected ? CHOSEN : PRIMARY, 0);
			title.setFont(OPTION_FONT);
			if (step.hasChoice() && !selected)
			{
				kit.makeClickable(title, () -> choose(step, option));
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
			stack(card, kit.microLabel("Wilderness \u00b7 PvP risk", WARN, 3, 0));
		}
		if (option.isTickManipulation())
		{
			stack(card, kit.microLabel("Tick manipulation", SUBTLE, 3, 0));
		}
		// The attention cost, where it has been judged. Deliberately not amber:
		// amber is reserved for "you can lose what you brought". A method being
		// hard work is a trade-off to weigh, not a warning.
		if (option.getAttention() == TrainingMethod.Attention.DEMANDING)
		{
			stack(card, kit.microLabel("Constant input", SUBTLE, 3, 0));
		}
		else if (option.getAttention() == TrainingMethod.Attention.AFK)
		{
			stack(card, kit.microLabel("Nearly AFK", SUBTLE, 3, 0));
		}
		if (option.getMeta() != null && !option.getMeta().isEmpty())
		{
			stack(card, kit.microLabel(option.getMeta(), SUBTLE, 3, 0));
		}

		if (alreadySaid)
		{
			// Said in full once already. A quest route is worth pointing back to;
			// for an ordinary method the estimate above is the news.
			if (questRoute)
			{
				JLabel repeat = kit.resultLine("The same quests as above \u2014 they cover this "
					+ "range too.", DETAIL, 0);
				repeat.setFont(SMALL_FONT);
				stack(card, repeat);
			}
			return;
		}

		if (option.getDetail() != null && !option.getDetail().isEmpty())
		{
			JLabel detail = kit.resultLine(option.getDetail(), DETAIL, 0);
			detail.setFont(SMALL_FONT);
			stack(card, detail);
		}

		// What to take, in the box with the method that needs it. The same
		// pictures a boss setup gets: "Maniacal monkeys" answers where to go and
		// leaves the player in the bank wondering what to bring, which is the
		// question they were going to the wiki for.
		renderKit(card, "Wear", option.getGear(), true);
		renderKit(card, "Bring", option.getSupplies(), false);
		renderNeeded(card, option.getNeeded());

		// Name the quests, then let Quest Helper take the player through each
		// one and its own requirements; they come back here afterwards to pick
		// the path up.
		if (questRoute)
		{
			for (String quest : option.getQuests())
			{
				stack(card, kit.resultLine("\u2022 " + quest, PRIMARY, 0));
			}
			stack(card,
				kit.questHelperPointer(PathStep.Type.DO_QUEST, option.getQuests().size()));
		}

		if (option.getWikiUrl() != null)
		{
			stack(card, kit.linkRow(java.util.Collections.singletonList(
				kit.actionLink("Wiki \u2197", () -> PanelKit.openUrl(option.getWikiUrl()))), 0));
		}
	}

	/**
	 * Supplies no shop or Grand Exchange offer will get you. "Buy 167,500 blessed
	 * bone shards" sends a player looking for something that cannot be traded.
	 */
	private static final Set<String> MADE_NOT_BOUGHT =
		java.util.Collections.singleton("blessed bone shards");

	/**
	 * What this range will use up, one to a line, each picture with its full count beside it.
	 * <p>
	 * Pictured like the gear, because it is the same question one step
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

		boolean allMade = true;
		for (PathStep.Option.Needed line : needed)
		{
			allMade &= MADE_NOT_BOUGHT.contains(line.getItem().toLowerCase(java.util.Locale.ROOT));
		}
		stack(card, kit.microLabel(allMade ? "Make" : "Buy", SUBTLE, 6, 12));
		GearView.Pictured pictured = new GearView.Pictured();
		for (PathStep.Option.Needed line : needed)
		{
			Integer id = gear.firstItemFor(line.getItem());
			if (id == null || pictured.names.size() >= GearView.MAX_ICONS)
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
		gear.stackCountedRows(card, pictured);
		for (String words : pictured.getInWords())
		{
			JLabel line = kit.resultLine(words, DETAIL, 0);
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
	private void renderKit(JPanel card, String label, List<String> items, boolean worn)
	{
		if (items.isEmpty())
		{
			return;
		}
		stack(card, kit.microLabel(label, SUBTLE, 6, 0));
		GearView.Pictured pictured;
		if (worn)
		{
			pictured = gear.stackGear(card, items);
		}
		else
		{
			pictured = gear.iconsFor(items);
			gear.stackIcons(card, pictured);
		}
		for (String remaining : pictured.getInWords())
		{
			JLabel line = kit.resultLine(remaining, DETAIL, 0);
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
		JLabel link = kit.resultLine(text + (suffix == null ? "" : " (" + suffix + ")"),
			QUIET_LINK, 10);
		link.setFont(SMALL_FONT);
		kit.makeClickable(link, () ->
		{
			if (!expandedSteps.remove(step.getTitle()))
			{
				expandedSteps.add(step.getTitle());
			}
			renderPath(lastPath);
		});
		return link;
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
	 * The uppercase line above a step: its number, plus the one fact worth
	 * putting there - what the step costs in xp, or what kind of thing it is.
	 */
	private String stepCaption(int number, PathStep step)
	{
		String caption = "Step " + number;
		if (step.getXpCost() > 0)
		{
			caption = caption + " \u00b7 " + XpTable.formatXp(step.getXpCost()) + " xp";
		}
		else
		{
			String kind = prefixFor(step.getType()).replace("[", "").replace("]", "").trim();
			caption = kind.isEmpty() ? caption : caption + " \u00b7 " + kind;
		}
		// Reached only while finished steps are being shown, since otherwise
		// they are not in the list to caption. Without it they read as work
		// still to do, which is the opposite of why they were unfolded.
		if (step.isRecommended())
		{
			// Nothing in the game insists on this one, and a step that looks
			// like the rest reads as one more thing in the way.
			caption = caption + " \u00b7 recommended";
		}
		return isFinished(step) ? caption + " \u00b7 done" : caption;
	}

	/** Drops the rendered plan and puts the goal pickers back in view. */
	void clearPath()
	{
		lastPath = null;
		optionBodiesShown.clear();
		pathResultContainer.removeAll();
		pathResultContainer.revalidate();
		pathResultContainer.repaint();
		SwingUtilities.invokeLater(() ->
			kit.scrollPane.getViewport().setViewPosition(new java.awt.Point(0, 0)));
	}

	/**
	 * @param finishedActivities the name of every quest, diary tier and other
	 *                           activity with a done state that the player has
	 *                           reached. A plan is not planned again while it is
	 *                           open, so without this a quest finished since
	 *                           stayed listed as still to do.
	 */
	void setProgress(Map<String, Integer> currentLevels, Set<String> finishedActivities)
	{
		int before = lastPath == null ? 0 : finishedCount(lastPath);
		Map<String, Integer> levelsBefore = levels;
		levels = currentLevels == null
			? java.util.Collections.emptyMap()
			: new java.util.HashMap<>(currentLevels);
		doneActivities = finishedActivities == null
			? java.util.Collections.emptySet()
			: new java.util.HashSet<>(finishedActivities);
		if (lastPath != null && levelsBefore.isEmpty() && !levels.isEmpty())
		{
			// The plan was opened before the player's levels were known, so it
			// starts every skill at 1. None of its steps were ever theirs to
			// finish: it is made again outright, with nothing kept as done.
			renderPath(pathResolver.apply(lastPath.getGoal()), false);
		}
		else if (lastPath != null && !levelsBefore.isEmpty() && !levels.isEmpty()
			&& !levels.equals(levelsBefore))
		{
			// A level went up while the plan was open. Folding finished steps
			// away is not enough here: a step from 75 to 77 is not finished at
			// 76, but its xp, its hours and its shopping list are all wrong.
			// So the plan is made again from where the player now stands, with
			// the methods they picked kept, and without moving the view.
			renderPath(keepingFinished(lastPath, pathResolver.apply(lastPath.getGoal())), false);
		}
		else if (lastPath != null && finishedCount(lastPath) != before)
		{
			// Not scrolled to the top: this re-render was not asked for, and
			// yanking the view while the player is mid-read is worse than the
			// stale step it is fixing.
			renderPath(lastPath, false);
		}
	}

	/**
	 * Whether the player is already past this step.
	 * <p>
	 * Skill training is judged by level, and a quest or diary tier on the way
	 * by whether the game says it is complete. A purchase or a boss kill has
	 * nothing to compare, and guessing would hide a step that still needs doing
	 * - so those are never folded away. Nor is the goal itself: a plan whose
	 * destination vanished from it would read as a plan to nowhere.
	 */
	private boolean isFinished(PathStep step)
	{
		if ((step.getType() == PathStep.Type.DO_QUEST || step.getType() == PathStep.Type.DO_DIARY
			|| step.getType() == PathStep.Type.DO_ACTIVITY) && !step.isTarget())
		{
			return doneActivities.contains(step.getTitle());
		}
		if (step.getType() != PathStep.Type.TRAIN_SKILL || step.getSkill() == null
			|| step.getLevelTo() <= 0 || levels.isEmpty())
		{
			return false;
		}
		Integer at = levels.get(step.getSkill().toUpperCase(Locale.ROOT));
		return at != null && at >= step.getLevelTo();
	}

	/**
	 * The new plan with the old one's finished steps put back at the front.
	 * <p>
	 * A plan made again starts from where the player stands, so everything
	 * already behind them is simply absent from it - and the steps they
	 * finished while watching would vanish instead of folding away as done.
	 */
	private Path keepingFinished(Path old, Path fresh)
	{
		if (fresh == null || fresh.isAlreadyComplete())
		{
			return fresh;
		}
		List<PathStep> steps = new ArrayList<>();
		for (PathStep step : old.getSteps())
		{
			if (isFinished(step))
			{
				steps.add(step);
			}
		}
		if (steps.isEmpty())
		{
			return fresh;
		}
		steps.addAll(fresh.getSteps());
		return new Path(fresh.getGoal(), false, steps, fresh.getWarnings(),
			fresh.isRequirementsAlreadyMet());
	}

	private int finishedCount(Path path)
	{
		int done = 0;
		for (PathStep step : path.getSteps())
		{
			if (isFinished(step))
			{
				done++;
			}
		}
		return done;
	}
}
