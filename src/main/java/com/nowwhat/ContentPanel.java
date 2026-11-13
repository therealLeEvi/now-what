package com.nowwhat;

import java.awt.Dimension;
import java.awt.Rectangle;
import javax.swing.JPanel;
import javax.swing.Scrollable;

/**
 * Makes the scrolled content adopt the viewport's width instead of its own
 * preferred width. This is what actually removes horizontal scrolling - a
 * plain JPanel would keep reporting the width of its widest child and the
 * viewport would happily scroll sideways to show it.
 */
class ContentPanel extends JPanel implements Scrollable
{
	@Override
	public Dimension getPreferredScrollableViewportSize()
	{
		return getPreferredSize();
	}

	@Override
	public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction)
	{
		return 16;
	}

	@Override
	public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction)
	{
		return visibleRect.height;
	}

	@Override
	public boolean getScrollableTracksViewportWidth()
	{
		return true;
	}

	/**
	 * False, always - and deliberately, because the obvious way to sink a
	 * footer is to return true while the content is shorter than the viewport
	 * and that hangs the layout.
	 * <p>
	 * Tracking the viewport height stretches the view, which can make the
	 * vertical scrollbar appear; the scrollbar takes width away, which is what
	 * this panel wraps its text against; narrower text is taller, so the
	 * content no longer fits, so tracking turns off, so the scrollbar goes
	 * away. Swing revalidates forever and the panel never paints. The footer
	 * is held down by a spacer this panel measures instead.
	 */
	@Override
	public boolean getScrollableTracksViewportHeight()
	{
		return false;
	}
}
