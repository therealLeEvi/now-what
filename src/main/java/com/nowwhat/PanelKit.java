package com.nowwhat;

import static com.nowwhat.PanelStyle.*;

import com.nowwhat.data.PluginLookup;
import com.nowwhat.model.PathStep;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.border.EmptyBorder;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.LinkBrowser;

/**
 * The small pieces every screen in the sidebar is built from: a line of text
 * wrapped to the sidebar's real width, a caption, a rule, a link, a pointer to
 * another plugin.
 * <p>
 * One object rather than loose helpers because wrapping depends on two things
 * that change while the panel is alive - how wide the viewport is, and whether
 * the text is going inside an option box.
 */
@Slf4j
class PanelKit
{
	/** The plugin that actually walks you through a quest, as its Plugin Hub descriptor names it. */
	static final String QUEST_HELPER = "Quest Helper";
	static final String PLUGIN_HUB_URL = "https://runelite.net/plugin-hub/";

	/** Used only to ask whether a named helper plugin is present; null outside a real client. */
	private final PluginManager pluginManager;
	/** Whose viewport text is wrapped to. Null until the panel has built it. */
	JScrollPane scrollPane;

	PanelKit(PluginManager pluginManager)
	{
		this.pluginManager = pluginManager;
	}

	/**
	 * An empty option box: filled, edged, padded, and spaced off the one above.
	 * A chosen option is edged in green instead, so the decision is visible from
	 * the shape of the panel rather than only from the tick in its title.
	 */
	JPanel optionCard(boolean selected)
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

	/** A 1px rule between steps - the whole separation budget of this layout. */
	JPanel hairline()
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
	JLabel microLabel(String text, Color color, int topPad)
	{
		return microLabel(text, color, topPad, 0);
	}

	JLabel microLabel(String text, Color color, int topPad, int indent)
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
	JPanel linkRow(List<JLabel> links)
	{
		return linkRow(links, 0);
	}

	JPanel linkRow(List<JLabel> links, int indent)
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
	JLabel actionLink(String text, Runnable onClick)
	{
		JLabel label = new JLabel(text);
		label.setForeground(QUIET_LINK);
		label.setFont(SMALL_FONT);
		makeClickable(label, onClick);
		return label;
	}

	/** Hand cursor plus a hover tint, so a clickable line looks clickable. */
	void makeClickable(JLabel label, Runnable onClick)
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

	JLabel wikiLink(String url, int indent)
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

	static void openUrl(String url)
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

	/**
	 * A line of wrapped text. The HTML width is what makes the label wrap
	 * instead of stretching the panel; the indent is applied as a border so it
	 * doesn't eat into the wrap width.
	 */
	JLabel resultLine(String text, Color color, int indent)
	{
		JLabel label = new JLabel(wrapped(escape(text), wrapWidth(indent)));
		label.setForeground(color);
		label.setFont(BODY_FONT);
		label.setBorder(new EmptyBorder(1, indent, 1, 0));
		label.setToolTipText(text);
		return label;
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
	int cardInset;

	int wrapWidth(int indent)
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

	String escape(String s)
	{
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	/**
	 * One line about Quest Helper, worded for what the player actually has
	 * installed - telling someone to "install Quest Helper" when it is already
	 * running in the same sidebar is the kind of thing that makes a plugin feel
	 * like it isn't paying attention.
	 */
	JLabel questHelperPointer(PathStep.Type type, int count)
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
	JLabel helperPointer(String pluginName, String does, String offer, int indent)
	{
		return helperPointer(pluginName, null, does, offer, indent);
	}

	JLabel helperPointer(String pluginName, String hubId, String does, String offer,
		int indent)
	{
		Plugin helper = findPlugin(pluginName);
		if (helper != null && pluginManager.isPluginEnabled(helper))
		{
			// Installed and running: name it and what it does. Opening its panel
			// for the player would mean reaching into RuneLite's sidebar, which
			// has no public way in.
			JLabel line = resultLine(pluginName + " " + does, SUBTLE, indent);
			line.setFont(SMALL_FONT);
			return line;
		}

		if (helper != null)
		{
			// Installed but switched off. Said rather than done: switching on
			// someone else's plugin is the player's call, made in RuneLite's own
			// plugin list.
			JLabel line = resultLine(pluginName + " is installed but switched off. Turn it "
				+ "on in RuneLite's plugin list for " + offer + ".", SUBTLE, indent);
			line.setFont(SMALL_FONT);
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
	 * Looks a plugin up by the name in its PluginDescriptor. Name-based on
	 * purpose: it keeps this a one-way, read-only nod at another plugin with no
	 * compile-time dependency on it, so Now What still builds and runs whether
	 * or not Quest Helper is installed.
	 *
	 * @see PluginLookup for the matching itself, and for the bug that put it in
	 * its own tested class
	 */
	Plugin findPlugin(String descriptorName)
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
}
