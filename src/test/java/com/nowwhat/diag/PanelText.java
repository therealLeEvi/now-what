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
import com.nowwhat.model.PlayerState;
import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SwingUtilities;

/**
 * Prints what the panel actually renders for a goal, as text, in order.
 * <p>
 * A PNG is the right check for spacing and a wrong one for structure: working
 * out from pixels whether eight level brackets collapsed into one step means
 * counting headings in an image. This dumps the rendered labels instead, so a
 * change to how steps are grouped can be read, diffed and checked in a second.
 * <p>
 * Usage: paneltext --args="skill:THIEVING:99 THIEVING=76 pick=Pyramid+Plunder"
 * <br>A pick= argument spells spaces as +, since the args arrive split on space.
 * <br>Later arguments set starting levels; the goal is the first.
 */
public class PanelText
{
	@SuppressWarnings("unchecked")
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

		PlayerState state = new PlayerState();
		for (int i = 1; i < args.length; i++)
		{
			if (args[i].startsWith("pick=") || args[i].startsWith("click="))
			{
				continue;
			}
			if (args[i].startsWith("all="))
			{
				// Every skill at once, for reaching the "you already meet every
				// requirement" branch without listing twenty-three of them.
				int level = Integer.parseInt(args[i].substring(4));
				for (String skill : new String[]{"ATTACK", "DEFENCE", "STRENGTH", "HITPOINTS",
					"RANGED", "PRAYER", "MAGIC", "COOKING", "WOODCUTTING", "FLETCHING",
					"FISHING", "FIREMAKING", "CRAFTING", "SMITHING", "MINING", "HERBLORE",
					"AGILITY", "THIEVING", "SLAYER", "FARMING", "RUNECRAFT", "HUNTER",
					"CONSTRUCTION"})
				{
					state.setSkillLevel(skill, level);
				}
				continue;
			}
			if (args[i].startsWith("quest="))
			{
				state.addCompletedQuest(args[i].substring(6).replace('+', ' '));
				continue;
			}
			if (args[i].startsWith("own="))
			{
				// own=<gameItemId>x<count>, plus the bank snapshot flag the
				// planner checks before trusting any of it.
				String[] owned = args[i].substring(4).split("x");
				java.util.Map<Integer, Integer> items = new java.util.HashMap<>();
				items.put(Integer.parseInt(owned[0]), Integer.parseInt(owned[1]));
				state.setOwnedItems(items);
				state.setBankSnapshotAvailable(true);
				continue;
			}
			String[] kv = args[i].split("=");
			state.setSkillLevel(kv[0].toUpperCase(), Integer.parseInt(kv[1]));
		}

		String goal = args[0];
		NowWhatConfig config = new NowWhatConfig()
		{
		};

		SwingUtilities.invokeAndWait(() ->
		{
			NowWhatPanel panel = new NowWhatPanel(config, ad.getAllActivities(), is.getAllSources(),
				g -> planner.plan(g, state));

			JFrame f = new JFrame();
			f.setUndecorated(true);
			f.getContentPane().add(panel);
			f.pack();
			f.setSize(240, 900);
			f.validate();

			// The pool a roll draws from, as a live refresh would supply it.
			panel.update(ad.getAllActivities(), ad.getAllActivities().subList(0, 4));

			String[] parts = goal.split(":");
			if ("start".equals(parts[0]) || "roll".equals(parts[0]))
			{
				if ("roll".equals(parts[0]))
				{
					clickIn(panel, "Surprise me");
					for (int i = 1; i < args.length; i++)
					{
						if (args[i].startsWith("click="))
						{
							clickIn(panel, args[i].substring(6).replace('+', ' '));
						}
					}
				}
				print(panel, 0);
				f.dispose();
				return;
			}
			List<JComboBox> combos = new ArrayList<>();
			findAll(panel, JComboBox.class, combos);
			List<JSpinner> spinners = new ArrayList<>();
			findAll(panel, JSpinner.class, spinners);
			List<JButton> buttons = new ArrayList<>();
			findAll(panel, JButton.class, buttons);
			List<JButton> planButtons = new ArrayList<>();
			for (JButton b : buttons)
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
				if (parts.length > 2)
				{
					spinners.get(1).setValue(Integer.parseInt(parts[2]));
				}
			}
			else if ("base".equals(parts[0]))
			{
				row = 3;
				spinners.get(2).setValue(Integer.parseInt(parts[1]));
			}
			else if ("total".equals(parts[0]))
			{
				row = 4;
				spinners.get(3).setValue(Integer.parseInt(parts[1]));
			}
			else
			{
				row = 0;
				select(combos.get(0), parts[1]);
			}
			planButtons.get(row).doClick();

			// Pre-chosen methods, so the collapsed-and-merged view a player sees
			// after clicking a method can be dumped too, not just the first one.
			List<String> picks = new ArrayList<>();
			for (int i = 1; i < args.length; i++)
			{
				if (args[i].startsWith("pick="))
				{
					picks.add(args[i].substring(5).replace('+', ' '));
				}
			}
			if (!picks.isEmpty())
			{
				try
				{
					Field chosen = NowWhatPanel.class.getDeclaredField("chosenOptions");
					chosen.setAccessible(true);
					((java.util.Set<String>) chosen.get(panel)).addAll(picks);
				}
				catch (ReflectiveOperationException ex)
				{
					throw new RuntimeException(ex);
				}
				planButtons.get(row).doClick();
			}

			try
			{
				Field field = NowWhatPanel.class.getDeclaredField("pathResultContainer");
				field.setAccessible(true);
				Container results = (Container) field.get(panel);

				// A click= argument drives the links in the rendered plan, so the
				// expanded views can be dumped too - which is where the grouping
				// code is easiest to get wrong and hardest to see.
				for (int i = 1; i < args.length; i++)
				{
					if (!args[i].startsWith("click="))
					{
						continue;
					}
					String needle = args[i].substring(6).replace('+', ' ');
					if (!click(results, needle))
					{
						System.out.println("!! no clickable label containing: " + needle);
					}
					results = (Container) field.get(panel);
				}
				print(results, 0);
			}
			catch (ReflectiveOperationException ex)
			{
				throw new RuntimeException(ex);
			}
			f.dispose();
		});
		System.exit(0);
	}

	private static void print(Container c, int depth)
	{
		for (Component child : c.getComponents())
		{
			if (!child.isVisible())
			{
				continue;
			}
			if (child instanceof JLabel)
			{
				String text = plain((JLabel) child);
				if (!text.isEmpty())
				{
					System.out.println(indent(depth) + text);
				}
			}
			else if (child instanceof JPanel && ((JPanel) child).getComponentCount() == 0)
			{
				System.out.println(indent(depth) + "---------------");
			}
			else if (child instanceof Container)
			{
				print((Container) child, depth);
			}
		}
	}

	private static void clickIn(Container root, String needle)
	{
		if (!click(root, needle))
		{
			System.out.println("!! nothing clickable containing: " + needle);
		}
	}

	/** Fires the first label whose text contains `needle`, as a mouse click would. */
	private static boolean click(Container c, String needle)
	{
		for (Component child : c.getComponents())
		{
			if (child instanceof JLabel)
			{
				String text = plain((JLabel) child);
				if (text.contains(needle) && ((JLabel) child).getMouseListeners().length > 0)
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
			else if (child instanceof Container && click((Container) child, needle))
			{
				return true;
			}
		}
		return false;
	}

	private static String plain(JLabel label)
	{
		return label.getText().replaceAll("<[^>]*>", "").replace("&nbsp;", " ").trim();
	}

	private static String indent(int depth)
	{
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < depth; i++)
		{
			sb.append("  ");
		}
		return sb.toString();
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

	private static void select(JComboBox combo, String id)
	{
		for (int i = 0; i < combo.getItemCount(); i++)
		{
			Object o = combo.getItemAt(i);
			String key = o instanceof Activity ? ((Activity) o).getId()
				: o instanceof ItemSource ? ((ItemSource) o).getId()
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
	private static <T> void findAll(Container root, Class<T> type, List<T> acc)
	{
		for (Component c : root.getComponents())
		{
			if (type.isInstance(c))
			{
				acc.add((T) c);
			}
			if (c instanceof Container)
			{
				findAll((Container) c, type, acc);
			}
		}
	}
}
