package com.nowwhat.diag;

import com.google.gson.Gson;
import com.nowwhat.NowWhatConfig;
import com.nowwhat.NowWhatPanel;
import com.nowwhat.data.ActivityDatabase;
import com.nowwhat.data.ItemSourceDatabase;
import com.nowwhat.data.TrainingMethodDatabase;
import com.nowwhat.engine.PathPlanner;
import com.nowwhat.engine.PrerequisiteResolver;
import com.nowwhat.model.Activity;
import com.nowwhat.model.ItemSource;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.PluginPanel;

/**
 * Renders NowWhatPanel offline at real sidebar width so the layout can be
 * eyeballed without launching a client and logging in.
 */
public class PanelShot
{
	static final com.nowwhat.model.PlayerState STATE = new com.nowwhat.model.PlayerState();

	public static void main(String[] args) throws Exception
	{
		Gson gson = new Gson();
		ActivityDatabase ad = new ActivityDatabase(gson);
		ad.load();
		TrainingMethodDatabase tm = new TrainingMethodDatabase(gson);
		tm.load(ad.getAllActivities());
		ItemSourceDatabase is = new ItemSourceDatabase(gson);
		is.load();
		PathPlanner planner = new PathPlanner(ad.getAllActivities(),
			new PrerequisiteResolver(ad.getAllActivities()), tm, is);

		NowWhatConfig config = new NowWhatConfig()
		{
		};

		// An account that already meets most things, so the render exercises the
		// same code paths a logged-in player hits rather than the empty-state one.
		STATE.setSkillLevel("ATTACK", 75);
		STATE.setSkillLevel("RANGED", 78);
		STATE.setSkillLevel("MAGIC", 80);
		STATE.setSkillLevel("AGILITY", 60);
		STATE.addCompletedQuest("Regicide");

		final int width = Integer.parseInt(args[0]);
		final String out = args[1];
		final String pickActivity = args.length > 2 ? args[2] : null;
		// A long skill plan is taller than a sidebar ever is, and anything past
		// the frame height is simply not painted - so the height has to be
		// settable or the shot silently truncates the thing being checked.
		final int height = args.length > 3 ? Integer.parseInt(args[3]) : 900;

		// Built and clicked in one pass, captured in a second, with the event
		// queue drained in between. Anything the panel schedules for itself -
		// a resize listener, an invokeLater - only runs once this thread lets
		// the queue turn over, and a shot taken inside the first pass is a shot
		// of a layout that has not finished happening.
		final JFrame[] window = new JFrame[1];
		final NowWhatPanel[] built = new NowWhatPanel[1];
		SwingUtilities.invokeAndWait(() ->
		{
			NowWhatPanel panel = new NowWhatPanel(config, ad.getAllActivities(), is.getAllSources(),
				goal -> planner.plan(goal, STATE));
			built[0] = panel;

			// Populate the suggestion lists the way a refresh would.
			List<Activity> some = ad.getAllActivities();
			panel.update(some.subList(0, Math.min(2, some.size())));

			// pack() makes the frame displayable (so Swing will lay out and
			// paint) without ever putting a window on screen.
			JFrame f = new JFrame();
			f.setUndecorated(true);
			f.getContentPane().add(panel);
			f.pack();
			f.setSize(width, height);
			f.validate();

			if ("choose".equals(pickActivity) || "roll".equals(pickActivity))
			{
				// The opening fork: click through to the screen being checked,
				// the way a player reaches it.
				panel.update(ad.getAllActivities(), ad.getAllActivities().subList(0, 4));
				clickLabel(panel, "choose".equals(pickActivity) ? "Plan a path" : "Surprise me");
			}
			else if (pickActivity != null)
			{
				// mode is "activity:<id>", "skill:<SKILL>:<level>" or "item:<ID>"
				String[] parts = pickActivity.split(":");
				List<JComboBox> combos = new ArrayList<>();
				findAll(panel, JComboBox.class, combos);
				List<javax.swing.JSpinner> spinners = new ArrayList<>();
				findAll(panel, javax.swing.JSpinner.class, spinners);
				// NB: a JComboBox contains its own arrow JButton, so filter by label.
				List<JButton> all = new ArrayList<>();
				findAll(panel, JButton.class, all);
				List<JButton> planButtons = new ArrayList<>();
				for (JButton b : all)
				{
					if ("Plan path".equals(b.getText()))
					{
						planButtons.add(b);
					}
				}

				int row;
				if ("skill".equals(parts[0]))
				{
					row = 1;
					combos.get(1).setSelectedItem(parts[1]);
					spinners.get(0).setValue(Integer.parseInt(parts[2]));
				}
				else if ("item".equals(parts[0]))
				{
					row = 2;
					// The item goal is a SearchPicker now, not a dropdown.
					List<com.nowwhat.SearchPicker> pickers = new ArrayList<>();
					findAll(panel, com.nowwhat.SearchPicker.class, pickers);
					selectIn(pickers.get(0), parts[1]);
				}
				else
				{
					row = 0;
					select(combos.get(0), parts[1]);
				}
				planButtons.get(row).doClick();
			}
			window[0] = f;
		});

		SwingUtilities.invokeAndWait(() ->
		{
			NowWhatPanel panel = built[0];
			JFrame f = window[0];
			panel.doLayout();
			panel.validate();

			// What actually happened inside the results container?
			List<java.awt.Container> results = new ArrayList<>();
			dumpResults(panel, results);

			// PluginPanel is PANEL_WIDTH + SCROLLBAR_WIDTH wide on the outside by
			// design - 225 of viewport and 17 for the scrollbar - so comparing
			// its preferred width against the frame width reported an overflow
			// on every single run. It cried wolf for long enough to be believed
			// once and chased as a bug. What actually matters is whether
			// anything inside the viewport is too wide for it, and that is
			// PanelWidthTest's job, component by component.
			Dimension pref = panel.getPreferredSize();
			int sidebar = PluginPanel.PANEL_WIDTH + PluginPanel.SCROLLBAR_WIDTH;
			System.out.println("panel preferred size = " + pref.width + "x" + pref.height
				+ "  (frame width " + width + ", a real sidebar is " + sidebar + " outside)");
			System.out.println(pref.width > sidebar
				? "!! wider than a sidebar -> run PanelWidthTest to find what overflows"
				: "ok: fits a sidebar");

			BufferedImage img = new BufferedImage(panel.getWidth(), panel.getHeight(),
				BufferedImage.TYPE_INT_RGB);
			panel.printAll(img.getGraphics());
			try
			{
				ImageIO.write(img, "png", new File(out));
			}
			catch (Exception ex)
			{
				throw new RuntimeException(ex);
			}
			f.dispose();
		});
		System.exit(0);
	}

	/** Fires the first clickable label containing this text, as a mouse click would. */
	static boolean clickLabel(Container c, String needle)
	{
		for (Component child : c.getComponents())
		{
			if (child instanceof javax.swing.JLabel)
			{
				String text = ((javax.swing.JLabel) child).getText()
					.replaceAll("<[^>]*>", "").replace("&nbsp;", " ").trim();
				if (text.contains(needle) && child.getMouseListeners().length > 0)
				{
					java.awt.event.MouseEvent event = new java.awt.event.MouseEvent(child,
						java.awt.event.MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(),
						0, 1, 1, 1, false);
					for (java.awt.event.MouseListener listener : child.getMouseListeners())
					{
						listener.mouseClicked(event);
					}
					return true;
				}
			}
			else if (child instanceof Container && clickLabel((Container) child, needle))
			{
				return true;
			}
		}
		return false;
	}

	/** Finds the results container by identity and reports what Swing did with it. */
	static void dumpResults(Container root, List<Container> acc)
	{
		for (Component c : root.getComponents())
		{
			if (c instanceof javax.swing.JPanel && c.getName() == null
				&& ((Container) c).getComponentCount() > 0
				&& ((Container) c).getComponent(0) instanceof javax.swing.JLabel)
			{
				javax.swing.JLabel first = (javax.swing.JLabel) ((Container) c).getComponent(0);
				if (first.getForeground().equals(java.awt.Color.CYAN))
				{
					System.out.println("RESULTS container: children=" + ((Container) c).getComponentCount()
						+ " bounds=" + c.getBounds()
						+ " pref=" + c.getPreferredSize()
						+ " max=" + ((javax.swing.JComponent) c).getMaximumSize()
						+ " visible=" + c.isVisible());
					for (Component k : ((Container) c).getComponents())
					{
						System.out.println("    child " + k.getClass().getSimpleName()
							+ " bounds=" + k.getBounds() + " pref=" + k.getPreferredSize());
					}
					return;
				}
			}
			if (c instanceof Container)
			{
				dumpResults((Container) c, acc);
			}
		}
	}

	/**
	 * Picks an entry out of a {@link com.nowwhat.SearchPicker} by id, which is
	 * what the harness means by "item:TWISTED_BOW". The picker is a text field
	 * and a filtered list, so there is no index to set - the entry is looked up
	 * and handed back to it.
	 */
	@SuppressWarnings("unchecked")
	private static void selectIn(com.nowwhat.SearchPicker picker, String id)
	{
		for (Object o : picker.getEntries())
		{
			String key = o instanceof Activity ? ((Activity) o).getId()
				: o instanceof ItemSource ? ((ItemSource) o).getId()
				: String.valueOf(o);
			if (key.equalsIgnoreCase(id))
			{
				picker.select(o);
				return;
			}
		}
		throw new IllegalArgumentException("no picker entry for " + id);
	}

	static void select(JComboBox combo, String id)
	{
		for (int i = 0; i < combo.getItemCount(); i++)
		{
			Object o = combo.getItemAt(i);
			String key = o instanceof Activity ? ((Activity) o).getId()
				: o instanceof com.nowwhat.model.ItemSource ? ((com.nowwhat.model.ItemSource) o).getId()
				: String.valueOf(o);
			if (key.equalsIgnoreCase(id))
			{
				combo.setSelectedIndex(i);
				return;
			}
		}
		throw new IllegalArgumentException("no combo entry for " + id);
	}

	@SuppressWarnings("unchecked")
	static <T> void findAll(Container root, Class<T> type, List<T> into)
	{
		for (Component c : root.getComponents())
		{
			if (type.isInstance(c))
			{
				into.add((T) c);
			}
			if (c instanceof Container)
			{
				findAll((Container) c, type, into);
			}
		}
	}
}
