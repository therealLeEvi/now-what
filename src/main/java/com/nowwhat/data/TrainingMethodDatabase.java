package com.nowwhat.data;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.nowwhat.model.TrainingMethod;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class TrainingMethodDatabase
{
	private static final String RESOURCE_PATH = "/com/nowwhat/methods.json";

	private final Gson gson;
	private final Map<String, List<TrainingMethod>> methodsBySkill = new HashMap<>();

	@Inject
	public TrainingMethodDatabase(Gson gson)
	{
		this.gson = gson;
	}

	/** Direct in-memory construction - used by tests so they don't depend on the real bundled methods.json. */
	public static TrainingMethodDatabase of(List<TrainingMethod> methods)
	{
		TrainingMethodDatabase db = new TrainingMethodDatabase(null);
		for (TrainingMethod method : methods)
		{
			db.methodsBySkill.computeIfAbsent(method.getSkill().toUpperCase(), k -> new ArrayList<>()).add(method);
		}
		for (List<TrainingMethod> perSkill : db.methodsBySkill.values())
		{
			perSkill.sort(Comparator.comparingInt(TrainingMethod::getMinLevel));
			shareNotesAcrossBrackets(perSkill);
		}
		return db;
	}

	/**
	 * Loads the bundled methods and resolves every method that borrows its kit
	 * from an activity.
	 * <p>
	 * The activities are a parameter rather than a second call the caller has to
	 * remember, because forgetting it is invisible: the plan still renders, the
	 * gear is simply absent, and that was exactly the bug - Wintertodt on the
	 * Firemaking path named the minigame and said nothing about warm clothing.
	 * Six places build this database (the plugin and five diagnostic harnesses)
	 * and one signature keeps all six honest.
	 */
	public void load(List<com.nowwhat.model.Activity> activities)
	{
		try (InputStream is = TrainingMethodDatabase.class.getResourceAsStream(RESOURCE_PATH))
		{
			if (is == null)
			{
				log.error("Could not find bundled methods.json at {}", RESOURCE_PATH);
				return;
			}
			try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8))
			{
				Type listType = new TypeToken<List<TrainingMethod>>()
				{
				}.getType();
				List<TrainingMethod> all = gson.fromJson(reader, listType);
				for (TrainingMethod method : all)
				{
					methodsBySkill
						.computeIfAbsent(method.getSkill().toUpperCase(), k -> new ArrayList<>())
						.add(method);
				}
				// Sorted ascending by minLevel so the planner can walk them in order.
				for (List<TrainingMethod> methods : methodsBySkill.values())
				{
					methods.sort(Comparator.comparingInt(TrainingMethod::getMinLevel));
					shareNotesAcrossBrackets(methods);
				}
				linkActivityKit(activities);
				log.info("Loaded {} training methods across {} skills", all.size(), methodsBySkill.size());
			}
		}
		catch (IOException e)
		{
			log.error("Failed to load methods.json", e);
		}
	}

	/**
	 * Gives every bracket of a method the note recorded on its lowest bracket.
	 * <p>
	 * The data file states a note once per method rather than once per bracket -
	 * see tools/README.md - which keeps it from being restated nine times down a
	 * single skill. The cost is that a plan starting part-way up a method begins
	 * above the row holding the note. Copying it down here puts it back within
	 * reach of every plan; the panel's repeat check is what stops it appearing
	 * more than once on screen.
	 *
	 * @param methods one skill's methods, sorted ascending by minLevel
	 */
	private static void shareNotesAcrossBrackets(List<TrainingMethod> methods)
	{
		Map<String, String> firstNote = new HashMap<>();
		for (TrainingMethod method : methods)
		{
			String key = method.getName().toLowerCase(java.util.Locale.ROOT);
			if (method.getNotes() != null && !method.getNotes().isEmpty())
			{
				firstNote.putIfAbsent(key, method.getNotes());
			}
		}
		Map<String, TrainingMethod.Attention> firstAttention = new HashMap<>();
		for (TrainingMethod method : methods)
		{
			if (method.getAttention() != null)
			{
				firstAttention.putIfAbsent(method.getName().toLowerCase(java.util.Locale.ROOT),
					method.getAttention());
			}
		}
		Map<String, List<String>> firstGear = new HashMap<>();
		Map<String, List<String>> firstSupplies = new HashMap<>();
		for (TrainingMethod method : methods)
		{
			String key = method.getName().toLowerCase(java.util.Locale.ROOT);
			if (!method.getGear().isEmpty())
			{
				firstGear.putIfAbsent(key, method.getGear());
			}
			if (!method.getSupplies().isEmpty())
			{
				firstSupplies.putIfAbsent(key, method.getSupplies());
			}
		}
		for (TrainingMethod method : methods)
		{
			String key = method.getName().toLowerCase(java.util.Locale.ROOT);
			method.inheritNotes(firstNote.get(key));
			method.inheritPreparation(firstGear.get(key), firstSupplies.get(key));
			method.inheritAttention(firstAttention.get(key));
		}
	}

	/**
	 * Fills in the kit of every method that names an activity to borrow it from,
	 * then re-shares it down that method's level brackets.
	 * <p>
	 * The activities are a different file with its own database, and this is the
	 * one point where the two meet. A method that already carries its own gear
	 * keeps it: the borrow is a default, not an override.
	 * <p>
	 * A setup's name is dropped on the way in. The activity view has room to
	 * head three kits "Ranged (budget)", "Magic (best in slot)"; a training
	 * option inside a plan is a 240px card that has already spent two lines on
	 * the rate and the note, so it gets one row of pictures. Where an activity
	 * offers several kits they are concatenated in written order, the first
	 * being the one the data recommends.
	 */
	private void linkActivityKit(List<com.nowwhat.model.Activity> activities)
	{
		Map<String, com.nowwhat.model.Activity> byId = new HashMap<>();
		for (com.nowwhat.model.Activity activity : activities)
		{
			byId.put(activity.getId(), activity);
		}

		for (List<TrainingMethod> perSkill : methodsBySkill.values())
		{
			for (TrainingMethod method : perSkill)
			{
				if (method.getActivity() == null)
				{
					continue;
				}
				com.nowwhat.model.Activity source = byId.get(method.getActivity());
				if (source == null)
				{
					log.warn("Method '{}' borrows kit from unknown activity '{}'",
						method.getName(), method.getActivity());
					continue;
				}
				List<String> gear = new ArrayList<>();
				for (com.nowwhat.model.Activity.GearSetup setup : source.getSetups())
				{
					gear.addAll(setup.getItems());
				}
				method.inheritPreparation(gear, source.getSupplies());
			}
			shareNotesAcrossBrackets(perSkill);
		}
	}

	/** Every bundled method, in no particular order. For diagnostics over the whole file. */
	public List<TrainingMethod> getAllMethods()
	{
		List<TrainingMethod> all = new java.util.ArrayList<>();
		for (List<TrainingMethod> perSkill : methodsBySkill.values())
		{
			all.addAll(perSkill);
		}
		return all;
	}

	/** Methods for a skill, sorted ascending by minLevel. Empty list if the skill has no bundled data. */
	public List<TrainingMethod> getMethodsFor(String skill)
	{
		return methodsBySkill.getOrDefault(skill.toUpperCase(), Collections.emptyList());
	}
}
