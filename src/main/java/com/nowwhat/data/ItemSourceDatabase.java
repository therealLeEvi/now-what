package com.nowwhat.data;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.nowwhat.model.ItemSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class ItemSourceDatabase
{
	private static final String RESOURCE_PATH = "/com/nowwhat/item-sources.json";

	private final Gson gson;
	private final Map<String, ItemSource> sourcesById = new HashMap<>();

	@Inject
	public ItemSourceDatabase(Gson gson)
	{
		this.gson = gson;
	}

	/** Direct in-memory construction - used by tests so they don't depend on the real bundled item-sources.json. */
	public static ItemSourceDatabase of(List<ItemSource> sources)
	{
		ItemSourceDatabase db = new ItemSourceDatabase(null);
		for (ItemSource source : sources)
		{
			db.sourcesById.put(source.getId().toUpperCase(), source);
		}
		return db;
	}

	public void load()
	{
		try (InputStream is = ItemSourceDatabase.class.getResourceAsStream(RESOURCE_PATH))
		{
			if (is == null)
			{
				log.error("Could not find bundled item-sources.json at {}", RESOURCE_PATH);
				return;
			}
			try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8))
			{
				Type listType = new TypeToken<List<ItemSource>>()
				{
				}.getType();
				List<ItemSource> all = gson.fromJson(reader, listType);
				for (ItemSource source : all)
				{
					sourcesById.put(source.getId().toUpperCase(), source);
				}
				log.info("Loaded {} item sources", sourcesById.size());
			}
		}
		catch (IOException e)
		{
			log.error("Failed to load item-sources.json", e);
		}
	}

	/** Null if this item has no bundled source data - the planner reports that as unresolved rather than guessing. */
	public ItemSource getSource(String itemId)
	{
		if (itemId == null)
		{
			return null;
		}
		return sourcesById.get(normalizeId(itemId));
	}

	/**
	 * Ids in the data file are SCREAMING_SNAKE_CASE, but people type item
	 * names ("Twisted bow", "twisted-bow"). Normalise rather than make the
	 * exact id the price of getting an answer.
	 */
	public static String normalizeId(String raw)
	{
		return raw.trim().toUpperCase().replaceAll("[^A-Z0-9]+", "_").replaceAll("^_|_$", "");
	}

	public java.util.List<ItemSource> getAllSources()
	{
		return new java.util.ArrayList<>(sourcesById.values());
	}
}
