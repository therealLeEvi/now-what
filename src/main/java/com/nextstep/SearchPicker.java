package com.nextstep;

import com.nextstep.data.NameSearch;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.Function;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.ui.ColorScheme;

/**
 * A type-to-filter picker: a text field, and the matches underneath it.
 * <p>
 * It replaces a {@link javax.swing.JComboBox} for the goal lists, which were
 * already awkward and would only get worse. A dropdown of 35 items is a scroll;
 * a dropdown of 300 is unusable, and the content database is supposed to keep
 * growing. A search box is the one control that improves as the list does.
 * <p>
 * The results list is only on screen while it is wanted - when the field has
 * focus, or when nothing has been picked yet. A sidebar is 240px wide and shares
 * its height with three other goal blocks, so a permanently open six-row list
 * would cost more than it gives. Picking something closes it and leaves the name
 * in the field, so the control reads as "this is your choice" at rest and as a
 * search while you are using it.
 * <p>
 * Keyboard: up and down move through the matches, Enter takes the highlighted
 * one, Escape puts the field back to what is already chosen. That matters more
 * here than in most pickers, because the player is typing anyway.
 *
 * @param <T> the kind of thing being picked
 */
public class SearchPicker<T> extends JPanel
{
	/** Enough to show the answer and a couple of near misses without eating the panel. */
	private static final int VISIBLE_ROWS = 6;
	/** Beyond this the list is noise; refine the query instead of scrolling. */
	private static final int MAX_RESULTS = 40;

	private final JTextField field = new JTextField();
	private final DefaultListModel<T> model = new DefaultListModel<>();
	private final JList<T> results = new JList<>(model);
	private final JScrollPane scroller = new JScrollPane(results);
	private final JLabel empty;
	private final JPanel fieldRow;

	private final List<T> all;
	private final Function<T, String> name;

	private T selected;
	/** Guards the document listener while the field is being set programmatically. */
	private boolean updating;

	SearchPicker(List<T> all, Function<T, String> name, String hint)
	{
		this.all = all;
		this.name = name;

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setAlignmentX(Component.LEFT_ALIGNMENT);

		field.setToolTipText(hint);
		field.setAlignmentX(Component.LEFT_ALIGNMENT);

		results.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		results.setVisibleRowCount(VISIBLE_ROWS);
		results.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		results.setBorder(new EmptyBorder(0, 0, 0, 0));
		results.setCellRenderer(new DefaultListCellRenderer()
		{
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index,
				boolean isSelected, boolean focused)
			{
				super.getListCellRendererComponent(list, value, index, isSelected, focused);
				setText(value == null ? "" : label(value));
				return this;
			}

			@SuppressWarnings("unchecked")
			private String label(Object value)
			{
				String text = name.apply((T) value);
				return text == null ? "" : text;
			}
		});
		scroller.setAlignmentX(Component.LEFT_ALIGNMENT);
		scroller.setBorder(new EmptyBorder(2, 0, 0, 0));
		scroller.getViewport().setBackground(ColorScheme.DARKER_GRAY_COLOR);

		empty = new JLabel("Nothing matches that");
		empty.setAlignmentX(Component.LEFT_ALIGNMENT);
		empty.setBorder(new EmptyBorder(3, 2, 1, 0));
		empty.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		empty.setFont(field.getFont().deriveFont(field.getFont().getSize2D() - 1f));
		empty.setVisible(false);

		fieldRow = new JPanel(new BorderLayout(4, 0));
		fieldRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
		fieldRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		fieldRow.add(field, BorderLayout.CENTER);
		add(fieldRow);
		add(empty);
		add(scroller);

		field.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				typed();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				typed();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				typed();
			}
		});

		field.addKeyListener(new KeyAdapter()
		{
			@Override
			public void keyPressed(KeyEvent e)
			{
				switch (e.getKeyCode())
				{
					case KeyEvent.VK_DOWN:
						move(1);
						e.consume();
						break;
					case KeyEvent.VK_UP:
						move(-1);
						e.consume();
						break;
					case KeyEvent.VK_ENTER:
						take();
						e.consume();
						break;
					case KeyEvent.VK_ESCAPE:
						showChosen();
						e.consume();
						break;
					default:
						break;
				}
			}
		});

		// Clicking into the field starts a new search rather than editing the
		// name already in it. Without this, a field reading "Fire cape" and a
		// player typing "drag" produces "Fire capedrag" and no matches - which
		// reads as the search being broken.
		field.addFocusListener(new java.awt.event.FocusAdapter()
		{
			@Override
			public void focusGained(java.awt.event.FocusEvent e)
			{
				field.selectAll();
				filter("");
				setListVisible(true);
			}
		});

		results.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				int index = results.locationToIndex(e.getPoint());
				if (index >= 0)
				{
					results.setSelectedIndex(index);
					take();
				}
			}
		});

		// Start on the first entry so "Plan path" works without typing anything,
		// exactly as the dropdown did.
		filter("");
		if (!model.isEmpty())
		{
			selected = model.get(0);
		}
		showChosen();
	}

	/**
	 * Puts a control beside the field - the quantity spinner, in practice. It
	 * belongs on the field's own row rather than above the results, so the row
	 * still reads as one input.
	 */
	void setTrailing(Component trailing)
	{
		fieldRow.add(trailing, BorderLayout.EAST);
		fieldRow.setMaximumSize(new Dimension(Integer.MAX_VALUE,
			field.getPreferredSize().height));
		resize();
	}

	/** The current choice, or null if the list is empty or nothing matched. */
	public T getSelected()
	{
		return selected;
	}

	/**
	 * Everything this picker can offer, in the order it was given them.
	 * <p>
	 * Public for the diagnostic harnesses, which render the panel with no game
	 * client and need to choose a goal by id - the job they used to do by
	 * walking a {@code JComboBox}'s model.
	 */
	public List<T> getEntries()
	{
		return java.util.Collections.unmodifiableList(all);
	}

	/** Chooses {@code value} as though the player had clicked it. */
	public void select(T value)
	{
		selected = value;
		showChosen();
	}

	private void typed()
	{
		if (updating)
		{
			return;
		}
		filter(field.getText());
		// Typing is a search, so the matches come back into view even if the
		// player had collapsed them by picking something.
		setListVisible(true);
	}

	private void filter(String query)
	{
		// No cap while nothing is typed: that is the whole list being browsed,
		// and truncating it would hide entries a dropdown would have shown.
		boolean searching = query != null && !query.trim().isEmpty();
		List<T> hits = NameSearch.rank(query, all, name, searching ? MAX_RESULTS : 0);
		model.clear();
		for (T hit : hits)
		{
			model.addElement(hit);
		}
		if (!model.isEmpty())
		{
			results.setSelectedIndex(0);
			results.ensureIndexIsVisible(0);
		}
		empty.setVisible(model.isEmpty());
		scroller.setVisible(!model.isEmpty());
		resize();
	}

	private void move(int by)
	{
		if (model.isEmpty())
		{
			return;
		}
		setListVisible(true);
		int next = Math.max(0, Math.min(model.size() - 1, results.getSelectedIndex() + by));
		results.setSelectedIndex(next);
		results.ensureIndexIsVisible(next);
	}

	/** Commits the highlighted match and collapses the list. */
	private void take()
	{
		T value = results.getSelectedValue();
		if (value == null)
		{
			return;
		}
		selected = value;
		showChosen();
	}

	/**
	 * Back to the resting state: the chosen name in the field, the matches out
	 * of the way. Also what Escape does, so a half-typed query is never left
	 * looking like a selection.
	 */
	private void showChosen()
	{
		updating = true;
		try
		{
			field.setText(selected == null ? "" : text(selected));
			field.setCaretPosition(field.getText().length());
		}
		finally
		{
			updating = false;
		}
		empty.setVisible(false);
		setListVisible(false);
	}

	private String text(T value)
	{
		String label = name.apply(value);
		return label == null ? "" : label;
	}

	private void setListVisible(boolean visible)
	{
		scroller.setVisible(visible && !model.isEmpty());
		resize();
	}

	private void resize()
	{
		// BoxLayout honours the maximum, and a scroll pane's is unbounded, so
		// without this the list stretches to fill whatever height is going.
		int rows = Math.min(VISIBLE_ROWS, Math.max(1, model.size()));
		int rowHeight = Math.max(16, results.getFixedCellHeight() > 0
			? results.getFixedCellHeight()
			: field.getPreferredSize().height - 4);
		scroller.setMaximumSize(new Dimension(Integer.MAX_VALUE, rows * rowHeight + 6));
		scroller.setPreferredSize(new Dimension(10, rows * rowHeight + 6));
		setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
		revalidate();
		repaint();
	}
}
