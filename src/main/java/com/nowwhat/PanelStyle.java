package com.nowwhat;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.swing.JPanel;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * The sidebar's colours, fonts and widths, and the two layout helpers every
 * part of it leans on. Kept in one place so the panel, the gear view and the
 * suggestion rows cannot drift apart in how they look.
 */
final class PanelStyle
{
	private PanelStyle()
	{
	}

	// The sidebar is a fixed 225px wide and never gets wider, so every piece of
	// text has to be told what width to wrap at - Swing will happily report a
	// 600px preferred width for a long label and drag the whole panel sideways.
	static final int CONTENT_WIDTH = PluginPanel.PANEL_WIDTH - (2 * PluginPanel.BORDER_OFFSET);
	// Fallback only, for the first render before the viewport has a real width.
	static final int TEXT_WIDTH = CONTENT_WIDTH - PluginPanel.SCROLLBAR_WIDTH;

	static final Color SUBTLE = new Color(0xAD, 0xA6, 0x9C);
	// Detail text sits under every option, so it is the most-read text in the
	// panel and was the least legible: 0x808080 pixel-font on a dark background.
	static final Color DETAIL = new Color(0xC6, 0xC0, 0xB5);
	static final Color CHOSEN = new Color(0x86, 0xC9, 0x8A);
	/**
	 * Text, instead of pure white. White at 13px against this grey is the glare
	 * the panel was accused of being flat by - a warm off-white carries the same
	 * weight without the hard edge, and leaves somewhere above the body text for
	 * the eye to go.
	 */
	static final Color PRIMARY = new Color(0xF1, 0xED, 0xE6);
	/**
	 * Links and the "show N others" controls. Kept above the note grey rather
	 * than below it: a control that reads fainter than the prose around it looks
	 * disabled.
	 */
	static final Color QUIET_LINK = new Color(0xB9, 0xB2, 0xA8);
	/**
	 * Softer than Color.CYAN, which was the loudest thing in the panel and sat
	 * above the content rather than beside it.
	 */
	static final Color ACCENT = new Color(0x8F, 0xD3, 0xE0);
	/** Caution, for the Wilderness tag. Amber over red: it survives colourblindness. */
	static final Color WARN = new Color(0xE3, 0xA8, 0x55);
	static final Color HAIRLINE = new Color(0x35, 0x33, 0x31);
	/**
	 * The things you can click sit in a box of their own.
	 * <p>
	 * Everything in this panel used to be the same flat column of text, and the
	 * only thing marking a method you could pick out from the sentence
	 * describing it was a 12px indent. A fill one step off the background and a
	 * 1px edge is enough to say "this is a thing, and it is one of several"
	 * without the panel turning into a stack of widgets.
	 */
	static final Color OPTION_BG = new Color(0x2F, 0x2D, 0x2B);
	static final Color OPTION_EDGE = new Color(0x3D, 0x3A, 0x37);
	/** Card padding (9px each side) plus its two 1px edges. */
	static final int CARD_INSET = 20;

	// RuneLite's bundled RuneScape font is a pixel face designed for the game
	// viewport; at sidebar size, for paragraphs rather than labels, it smears.
	// So it is used where it is at home - the things you navigate by: the
	// goal, each step, each method - and the platform UI sans carries
	// everything you read. Titles were a serif for a while, which next to the
	// client's own buttons and dropdowns made three faces in one sidebar and
	// looked like it belonged to a different program.
	static final String[] UI_FAMILIES = {"Segoe UI", "Noto Sans", "DejaVu Sans",
		Font.SANS_SERIF};
	/** Windows ships this as its own family; elsewhere bold stands in for it. */
	static final String[] UI_MEDIUM_FAMILIES = {"Segoe UI Semibold", "Noto Sans Medium",
		"DejaVu Sans"};

	static final Font BODY_FONT = installed(UI_FAMILIES, Font.PLAIN, 12);
	static final Font BODY_BOLD = BODY_FONT.deriveFont(Font.BOLD);
	/**
	 * The smallest prose in the panel. 12px, not 11: a line of 11px sans at this
	 * grey is the one thing in the sidebar that genuinely is hard to read, and
	 * the pixel saved by shrinking it buys nothing.
	 */
	static final Font SMALL_FONT = installed(UI_FAMILIES, Font.PLAIN, 12);
	/** A step title, a method name, a section heading: the game's own face, at its native 16px. */
	static final Font STEP_TITLE_FONT = FontManager.getRunescapeBoldFont();
	static final Font OPTION_FONT = FontManager.getRunescapeFont();
	static final Font HEADER_FONT = FontManager.getRunescapeBoldFont();
	/**
	 * The little uppercase label over each step. Letter-spacing is what stops
	 * all-caps at 10px reading as a smudge - and the extra weight is what stops
	 * it reading as a smudge in grey, which tracking alone could not fix.
	 */
	static final Font MICRO_FONT = microFont();

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
	static Font installed(String[] families, int style, int size)
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

	/**
	 * Adds a component to the vertical stack, pinned to the left and capped so
	 * BoxLayout can stretch it horizontally but never vertically. Without the
	 * maximum-size cap, BoxLayout hands leftover height to whichever component
	 * will take it and the layout goes to pieces.
	 */
	static void stack(JPanel parent, Component child)
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
	 * Wraps text at an exact pixel width.
	 *
	 * <p>This uses a one-cell table rather than the more obvious
	 * {@code <body style='width:Npx'>}: Swing's CSS support treats that width
	 * as advisory and still reports a preferred width ~45px larger, so the
	 * label overflows the sidebar and the tail of every line gets clipped. A
	 * table width is honoured exactly.
	 */
	static String wrapped(String escapedText, int width)
	{
		return "<html><table cellpadding=0 cellspacing=0 border=0 width='" + width + "'>"
			+ "<tr><td>" + escapedText + "</td></tr></table></html>";
	}
}
