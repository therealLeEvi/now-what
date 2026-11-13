package com.nowwhat;

import static org.junit.Assert.assertEquals;

import com.nowwhat.model.Activity;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class SetupFamilyTest
{
	private static Activity.GearSetup setup(String name)
	{
		return new Activity.GearSetup(name, Collections.singletonList("Abyssal whip"));
	}

	private static List<List<String>> names(Map<String, List<Activity.GearSetup>> families)
	{
		List<List<String>> out = new ArrayList<>();
		for (List<Activity.GearSetup> family : families.values())
		{
			List<String> names = new ArrayList<>();
			family.forEach(s -> names.add(s.getName()));
			out.add(names);
		}
		return out;
	}

	@Test
	public void bestInSlotAndBudgetOfOneStyleAreOneFamily()
	{
		List<Activity.GearSetup> setups = Arrays.asList(
			setup("Melee (best in slot)"),
			setup("Ranged (best in slot)"),
			setup("Melee (budget)"),
			setup("Ranged (budget)"));

		assertEquals(Arrays.asList(
			Arrays.asList("Melee (best in slot)", "Melee (budget)"),
			Arrays.asList("Ranged (best in slot)", "Ranged (budget)")),
			names(GearView.setupFamilies(setups)));
	}

	@Test
	public void aBareNameStaysOnItsOwn()
	{
		List<Activity.GearSetup> setups = Arrays.asList(
			setup("Melee"),
			setup("Melee (budget)"));

		assertEquals(Arrays.asList(
			Collections.singletonList("Melee"),
			Collections.singletonList("Melee (budget)")),
			names(GearView.setupFamilies(setups)));
	}

	@Test
	public void theQualifierIsTheBracketCapitalised()
	{
		assertEquals("Best in slot", GearView.setupQualifier("Melee (best in slot)"));
		assertEquals("Budget", GearView.setupQualifier("Magic switch (budget)"));
	}

	@Test
	public void theBestKitComesFirstWhateverOrderItWasWrittenIn()
	{
		List<Activity.GearSetup> written = Arrays.asList(
			setup("Melee (free to play)"),
			setup("Melee (budget)"),
			setup("Melee (Blood Moon)"),
			setup("Melee (best in slot)"));

		List<String> order = new ArrayList<>();
		GearView.bestFirst(written).forEach(s -> order.add(s.getName()));
		assertEquals(Arrays.asList("Melee (best in slot)", "Melee (Blood Moon)", "Melee (budget)",
			"Melee (free to play)"), order);
	}
}
