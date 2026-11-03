package com.nextstep.data;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.nextstep.model.Activity;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Loads the bundled activities.json (the ruleset: activities + their
 * requirements) into memory. This is the file the community would maintain
 * and expand over time as OSRS adds content - the plugin code itself
 * shouldn't need to change when new diary tiers, quests, or bosses ship.
 */
@Slf4j
@Singleton
public class ActivityDatabase
{
	private static final String RESOURCE_PATH = "/com/nextstep/activities.json";

	private final Gson gson;
	private List<Activity> activities = Collections.emptyList();

	@Inject
	public ActivityDatabase(Gson gson)
	{
		this.gson = gson;
	}

	public void load()
	{
		try (InputStream is = ActivityDatabase.class.getResourceAsStream(RESOURCE_PATH))
		{
			if (is == null)
			{
				log.error("Could not find bundled activities.json at {}", RESOURCE_PATH);
				return;
			}
			try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8))
			{
				Type listType = new TypeToken<List<Activity>>()
				{
				}.getType();
				activities = gson.fromJson(reader, listType);
				resolvePreparation(activities);
				log.info("Loaded {} activities from bundled database", activities.size());
			}
		}
		catch (IOException e)
		{
			log.error("Failed to load activities.json", e);
		}
	}

	/**
	 * Hands every entry that borrows its preparation the gear of the activity it
	 * borrows from.
	 * <p>
	 * Done at load rather than at render time so the rest of the code never has
	 * to know an activity's gear might live somewhere else, and so a reference
	 * that no longer resolves is noticed in the log on startup instead of being
	 * a blank space in the panel.
	 */
	static void resolvePreparation(List<Activity> activities)
	{
		if (activities == null)
		{
			return;
		}
		Map<String, Activity> byId = new HashMap<>();
		for (Activity activity : activities)
		{
			byId.put(activity.getId(), activity);
		}
		for (Activity activity : activities)
		{
			String from = activity.getPreparationFrom();
			if (from == null)
			{
				continue;
			}
			Activity source = byId.get(from);
			if (source == null || !source.hasPreparation())
			{
				log.warn("{} borrows its preparation from {}, which has none", activity.getId(), from);
				continue;
			}
			activity.inheritPreparation(source);
		}
	}

	public List<Activity> getAllActivities()
	{
		return activities;
	}
}
