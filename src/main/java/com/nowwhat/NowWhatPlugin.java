package com.nowwhat;

import com.google.inject.Provides;
import com.nowwhat.data.ActivityDatabase;
import com.nowwhat.data.CombatAchievementTracker;
import com.nowwhat.data.DiaryTracker;
import com.nowwhat.data.ItemSourceDatabase;
import com.nowwhat.data.ItemTracker;
import com.nowwhat.data.TrainingMethodDatabase;
import com.nowwhat.engine.PathPlanner;
import com.nowwhat.engine.PrerequisiteResolver;
import com.nowwhat.engine.SuggestionEngine;
import com.nowwhat.model.Goal;
import com.nowwhat.model.Path;
import com.nowwhat.model.PlayerState;
import java.awt.image.BufferedImage;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Quest;
import net.runelite.api.Skill;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import com.nowwhat.model.Activity;

@Slf4j
@PluginDescriptor(
	name = "Now What",
	description = "Suggests what to do next based on your skills, quests, diaries and Combat Achievements",
	tags = {"todo", "goals", "quests", "diaries", "combat achievements", "pvm", "advisor", "planner"}
)
public class NowWhatPlugin extends Plugin
{
	// Refresh isn't event-driven off every relevant varbit change (there are
	// too many to subscribe to individually and reliably); instead we
	// recompute on a timer. Once every few seconds is plenty since none of
	// this data changes faster than a player can read a panel.
	private static final int REFRESH_INTERVAL_TICKS = 50; // ~30 seconds

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ConfigManager configManager;

	@Inject
	private NowWhatConfig config;

	@Inject
	private ActivityDatabase activityDatabase;

	@Inject
	private DiaryTracker diaryTracker;

	@Inject
	private TrainingMethodDatabase trainingMethodDatabase;

	@Inject
	private ItemSourceDatabase itemSourceDatabase;

	@Inject
	private ItemTracker itemTracker;

	// Only ever asked whether Quest Helper is installed and on, so the panel can
	// say the right thing when a path needs quests. No API of theirs is touched.
	@Inject
	private net.runelite.client.plugins.PluginManager pluginManager;

	/**
	 * RuneLite's item index and icon source, handed to the panel so a boss's
	 * "what to bring" list can show the gear as pictures rather than only as
	 * nine item names in a row.
	 */
	@Inject
	private net.runelite.client.game.ItemManager itemManager;

	private final SuggestionEngine suggestionEngine = new SuggestionEngine();
	private final CombatAchievementTracker combatAchievementTracker = new CombatAchievementTracker();
	private PrerequisiteResolver prerequisiteResolver;
	private PathPlanner pathPlanner;

	private NowWhatPanel panel;
	private NavigationButton navButton;
	private int ticksSinceRefresh = 0;

	// Snapshot from the most recent refresh, read by the panel's "check
	// requirements" lookup so that lookups don't need to hop back onto the
	// client thread on every click - a few seconds of staleness is fine here.
	private volatile PlayerState latestState = new PlayerState();

	@Provides
	NowWhatConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(NowWhatConfig.class);
	}

	@Override
	protected void startUp()
	{
		activityDatabase.load();
		// The activities go in too: a method that is really an activity takes that
		// activity's kit, so the skill path pictures the same gear the
		// suggestion list does.
		trainingMethodDatabase.load(activityDatabase.getAllActivities());
		itemSourceDatabase.load();
		itemTracker.load();
		prerequisiteResolver = new PrerequisiteResolver(activityDatabase.getAllActivities());
		pathPlanner = new PathPlanner(activityDatabase.getAllActivities(), prerequisiteResolver,
			trainingMethodDatabase, itemSourceDatabase);

		panel = new NowWhatPanel(config, activityDatabase.getAllActivities(),
			itemSourceDatabase.getAllSources(), this::resolvePath, pluginManager, itemManager);
		// Lets the panel open another plugin's sidebar panel when it points at
		// one - the toolbar is the only thing that can.

		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/com/nowwhat/icon.png");

		navButton = NavigationButton.builder()
			.tooltip("Now What")
			.icon(icon)
			.priority(5)
			.panel(panel)
			.build();

		clientToolbar.addNavigation(navButton);

		if (client.getGameState() == GameState.LOGGED_IN)
		{
			refresh();
		}
	}

	@Override
	protected void shutDown()
	{
		clientToolbar.removeNavigation(navButton);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			ticksSinceRefresh = REFRESH_INTERVAL_TICKS; // force refresh next tick
		}
	}

	/**
	 * Equipment and inventory stream in continuously; the bank arrives only
	 * when the player opens it. Either way a change is worth a refresh, since
	 * what you own feeds straight into what the planner tells you to go get.
	 */
	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (itemTracker.onContainerChanged(event.getContainerId(), event.getItemContainer()))
		{
			ticksSinceRefresh = REFRESH_INTERVAL_TICKS; // refresh on the next tick
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		ticksSinceRefresh++;
		if (ticksSinceRefresh >= REFRESH_INTERVAL_TICKS)
		{
			ticksSinceRefresh = 0;
			refresh();
		}
	}

	/**
	 * Rebuilds PlayerState from the live client (must run on the client
	 * thread) then hands it to the suggestion engine off-thread and pushes
	 * results to the Swing panel.
	 */
	private void refresh()
	{
		clientThread.invoke(() ->
		{
			PlayerState state = buildPlayerState();
			latestState = state;

			SwingUtilities.invokeLater(() ->
			{
				Set<Activity.Category> hidden = hiddenCategories();
				// Before the suggestions, because this can rebuild a rendered
				// plan and the update below repaints around it.
				panel.setProgress(state.getSkillLevels());
				boolean hideWilderness = !config.showWilderness();
				List<Activity> almost = suggestionEngine.getAlmostAvailable(
					activityDatabase.getAllActivities(), state, hidden, hideWilderness);
				Map<String, String> missing = new HashMap<>();
				for (Activity activity : almost)
				{
					missing.put(activity.getId(), suggestionEngine.whatIsMissing(activity, state));
				}
				panel.update(
					suggestionEngine.getAvailableNow(activityDatabase.getAllActivities(), state, hidden,
						hideWilderness),
					almost, missing
				);
			});
		});
	}

	/**
	 * The categories the player has switched off in settings.
	 * <p>
	 * Only the four categories with a switch can appear here. MINIGAME,
	 * SKILLING, COMBAT_ACHIEVEMENT and CLUE_SCROLL have no setting of their
	 * own, so they are never hidden - adding a switch is what would change
	 * that, not adding a category.
	 */
	private Set<Activity.Category> hiddenCategories()
	{
		Set<Activity.Category> hidden = EnumSet.noneOf(Activity.Category.class);
		if (!config.showQuests())
		{
			hidden.add(Activity.Category.QUEST);
		}
		if (!config.showDiaries())
		{
			hidden.add(Activity.Category.DIARY);
		}
		if (!config.showPvm())
		{
			hidden.add(Activity.Category.PVM);
		}
		if (!config.showCollectionLog())
		{
			hidden.add(Activity.Category.COLLECTION_LOG);
		}
		return hidden;
	}

	private PlayerState buildPlayerState()
	{
		PlayerState state = new PlayerState();

		for (Skill skill : Skill.values())
		{
			// Skill.OVERALL is deprecated but still returned by values(); compare by
			// name so this keeps working if it is eventually dropped from the enum.
			if ("OVERALL".equals(skill.name()))
			{
				continue;
			}
			state.setSkillLevel(skill.name(), client.getRealSkillLevel(skill));
			// The xp too, not just the level: a total level plan compares the
			// cost of the next level in one skill against the next level in
			// another, and "level 62" is anywhere from the start of the bracket
			// to one xp short of the end of it.
			state.setSkillXp(skill.name(), client.getSkillExperience(skill));
		}

		for (Quest quest : Quest.values())
		{
			if (quest.getState(client) == net.runelite.api.QuestState.FINISHED)
			{
				state.addCompletedQuest(quest.getName());
			}
		}

		// The Quest enum doesn't expose each quest's reward QP, so an earlier
		// version gave up and left this at 0 - which silently made every
		// QUEST_POINTS requirement permanently unmet. The game already tracks the
		// running total in a varp; just read it.
		state.setQuestPoints(client.getVarpValue(VarPlayerID.QP));

		if (client.getLocalPlayer() != null)
		{
			state.setCombatLevel(client.getLocalPlayer().getCombatLevel());
		}

		diaryTracker.readInto(client, state);
		combatAchievementTracker.readInto(client, state);
		itemTracker.writeInto(state);

		// PvM boss KC and collection log slot data intentionally left as
		// integration points - see README. For boss KC specifically: prefer
		// reading VarPlayerID.TOTAL_<BOSS>_KILLS-style varps directly (this is
		// how Goal Planner does it - no chat parsing or network calls needed)
		// once the per-boss varp ids are mapped; chat-message parsing is a
		// fallback only for bosses without a known varp.

		return state;
	}

	/**
	 * Called from the panel (EDT) when the user sets a skill/item/activity
	 * goal and asks for a path. Uses the cached snapshot from the last
	 * refresh rather than touching the client thread synchronously from a
	 * UI click.
	 */
	private Path resolvePath(Goal goal)
	{
		return pathPlanner.plan(goal, latestState);
	}
}
