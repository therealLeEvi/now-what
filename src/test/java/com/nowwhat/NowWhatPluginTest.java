package com.nowwhat;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/**
 * Dev-mode launcher, per RuneLite's own convention: starts a real client with
 * this plugin side-loaded so the panel can be exercised by hand. Not a unit
 * test -- run it from the IDE, or with {@code ./gradlew run}.
 */
public class NowWhatPluginTest
{
	@SuppressWarnings("unchecked") // loadBuiltin takes a generic varargs array
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(NowWhatPlugin.class);
		RuneLite.main(args);
	}
}
