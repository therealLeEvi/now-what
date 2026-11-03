package com.nowwhat;

import java.awt.Component;
import java.awt.Container;
import java.util.Arrays;
import java.util.List;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SearchPickerTest
{
	private static final List<String> ITEMS = Arrays.asList(
		"Abyssal bludgeon", "Abyssal whip", "Dragon claws", "Seers' ring");

	/**
	 * Nothing is chosen until the player chooses it, so the field is empty and
	 * the placeholder is what they see. The dropdown this replaced opened on its
	 * first entry; a search box doing that would be both a choice nobody made
	 * and the reason the box looks like a dropdown.
	 */
	@Test
	public void startsWithNothingChosen() throws Exception
	{
		SearchPicker<String>[] picker = newPicker();
		assertNull(picker[0].getSelected());
		assertEquals("", field(picker[0]).getText());
	}

	@Test
	public void typingAndPickingCommitsTheMatch() throws Exception
	{
		SearchPicker<String>[] picker = newPicker();
		SwingUtilities.invokeAndWait(() -> field(picker[0]).setText("bludg"));
		SwingUtilities.invokeAndWait(() -> list(picker[0]).setSelectedIndex(0));
		picker[0].select(list(picker[0]).getSelectedValue());

		assertEquals("Abyssal bludgeon", picker[0].getSelected());
	}

	/**
	 * A result row has to be tall enough to show its own text. The height was
	 * guessed at as the field's height minus 4, and a guess is wrong as soon as
	 * the row is taller than the guess: the list is then capped below the height
	 * of its own rows and the match shows sliced in half along the bottom edge.
	 * <p>
	 * The row is forced taller than any guess here on purpose. The default look
	 * and feel in a test JVM is not the client's, and under this one the old
	 * arithmetic happened to come out large enough - so a test that just typed a
	 * query passed either way and guarded nothing.
	 */
	@Test
	public void aResultRowIsNotClippedByTheListItSitsIn() throws Exception
	{
		SearchPicker<String>[] picker = newPicker();
		SwingUtilities.invokeAndWait(() ->
		{
			JList<String> tall = list(picker[0]);
			tall.setFont(tall.getFont().deriveFont(28f));
			field(picker[0]).setText("Abyssal bludgeon");
		});

		JList<String> list = list(picker[0]);
		assertEquals(1, list.getModel().getSize());
		int rowHeight = list.getCellRenderer()
			.getListCellRendererComponent(list, list.getModel().getElementAt(0), 0, false, false)
			.getPreferredSize().height;
		int available = scroller(picker[0]).getPreferredSize().height;

		assertTrue("one row wants " + rowHeight + "px but the list is only " + available
			+ "px tall, so its text is cut off", available >= rowHeight);
	}

	@SuppressWarnings("unchecked")
	private static SearchPicker<String>[] newPicker() throws Exception
	{
		final SearchPicker<String>[] built = new SearchPicker[1];
		SwingUtilities.invokeAndWait(() -> built[0] = new SearchPicker<>(ITEMS, name -> name,
			"Search for an item", "Type to search, or pick from the list"));
		return built;
	}

	private static JTextField field(Container root)
	{
		return find(root, JTextField.class);
	}

	@SuppressWarnings("unchecked")
	private static JList<String> list(Container root)
	{
		return find(root, JList.class);
	}

	private static JScrollPane scroller(Container root)
	{
		return find(root, JScrollPane.class);
	}

	private static <T> T find(Container root, Class<T> type)
	{
		for (Component child : root.getComponents())
		{
			if (type.isInstance(child))
			{
				return type.cast(child);
			}
			if (child instanceof Container)
			{
				T found = find((Container) child, type);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}
}
