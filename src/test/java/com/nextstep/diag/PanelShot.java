package com.nextstep.diag;

import com.google.gson.Gson;
import com.nextstep.NextStepConfig;
import com.nextstep.NextStepPanel;
import com.nextstep.data.ActivityDatabase;
import com.nextstep.data.ItemSourceDatabase;
import com.nextstep.data.TrainingMethodDatabase;
import com.nextstep.engine.PathPlanner;
import com.nextstep.engine.PrerequisiteResolver;
import com.nextstep.model.Activity;
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

/**
 * Renders NextStepPanel offline at real sidebar width so the layout can be
 * eyeballed without launching a client and logging in.
 */
public class PanelShot
{
	static final com.nextstep.model.PlayerState STATE = new com.nextstep.model.PlayerState();

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

		NextStepConfig config = new NextStepConfig()
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
		final NextStepPanel[] built = new NextStepPanel[1];
		SwingUtilities.invokeAndWait(() ->
		{
			NextStepPanel panel = new NextStepPanel(config, ad.getAllActivities(), is.getAllSources(),
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
					select(combos.get(2), parts[1]);
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
			NextStepPanel panel = built[0];
			JFrame f = window[0];
			panel.doLayout();
			panel.validate();

			// What actually happened inside the results container?
			List<java.awt.Container> results = new ArrayList<>();
			dumpResults(panel, results);

			Dimension pref = panel.getPreferredSize();
			System.out.println("panel preferred size = " + pref.width + "x" + pref.height
				+ "  (frame width " + width + ")");
			System.out.println(pref.width > width
				? "!! content wider than the sidebar -> horizontal scrolling"
				: "ok: content fits the sidebar width");

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

	static void select(JComboBox combo, String id)
	{
		for (int i = 0; i < combo.getItemCount(); i++)
		{
			Object o = combo.getItemAt(i);
			String key = o instanceof Activity ? ((Activity) o).getId()
				: o instanceof com.nextstep.model.ItemSource ? ((com.nextstep.model.ItemSource) o).getId()
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
