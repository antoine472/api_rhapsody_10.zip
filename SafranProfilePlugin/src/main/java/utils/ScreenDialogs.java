package utils;

import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.Rectangle;

import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.UIManager;

/**
 * Modal option dialogs shown on the screen where Rhapsody is used, always on top.
 * <p>
 * Rhapsody's main window is native (not an AWT window), so Java cannot read its
 * position. The dialogs are opened from a Rhapsody menu, so the mouse cursor is on
 * Rhapsody's screen: the dialog is centred on the screen that contains the cursor.
 * Without an owner window, {@code JOptionPane.showXxxDialog(null, ...)} would use
 * the primary screen and could open behind Rhapsody.
 * </p>
 */
public final class ScreenDialogs {

	private ScreenDialogs() {
	}

	/**
	 * Selection among {@code options} (combo box).
	 * @return the selected value, or {@code null} if the user cancelled
	 */
	public static Object showInput(String message, String title, Object[] options,
			Object initialValue) {
		JOptionPane pane = new JOptionPane(message, JOptionPane.QUESTION_MESSAGE,
				JOptionPane.OK_CANCEL_OPTION);
		pane.setWantsInput(true);
		pane.setSelectionValues(options);
		pane.setInitialSelectionValue(initialValue);
		show(pane, title);

		Object button = pane.getValue();
		if (!(button instanceof Integer) || (Integer) button != JOptionPane.OK_OPTION) {
			return null;
		}
		Object input = pane.getInputValue();
		return input == JOptionPane.UNINITIALIZED_VALUE ? null : input;
	}

	/** Yes / No question. @return true only if the user answered Yes */
	public static boolean confirm(String message, String title) {
		JOptionPane pane = new JOptionPane(message, JOptionPane.QUESTION_MESSAGE,
				JOptionPane.YES_NO_OPTION);
		show(pane, title);
		Object button = pane.getValue();
		return button instanceof Integer && (Integer) button == JOptionPane.YES_OPTION;
	}

	/** Information / warning / error message ({@code JOptionPane.*_MESSAGE}). */
	public static void message(String message, String title, int messageType) {
		show(new JOptionPane(message, messageType, JOptionPane.DEFAULT_OPTION), title);
	}

	/** Shows the pane as a modal, always-on-top dialog on Rhapsody's screen. */
	private static void show(JOptionPane pane, String title) {
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
			pane.updateUI();
		} catch (Exception ignore) {
			// Default look and feel is acceptable.
		}

		JDialog dialog = pane.createDialog(null, title);
		dialog.setAlwaysOnTop(true);
		centerOnCursorScreen(dialog);
		dialog.toFront();
		dialog.setVisible(true); // blocks until the user answers (modal)
		dialog.dispose();
	}

	/** Centres the dialog on the screen containing the mouse cursor. */
	public static void centerOnCursorScreen(JDialog dialog) {
		try {
			PointerInfo pointer = MouseInfo.getPointerInfo();
			if (pointer != null) {
				Point mouse = pointer.getLocation();
				for (GraphicsDevice screen : GraphicsEnvironment
						.getLocalGraphicsEnvironment().getScreenDevices()) {
					Rectangle bounds = screen.getDefaultConfiguration().getBounds();
					if (bounds.contains(mouse)) {
						int x = bounds.x + Math.max(0, (bounds.width - dialog.getWidth()) / 2);
						int y = bounds.y + Math.max(0, (bounds.height - dialog.getHeight()) / 2);
						dialog.setLocation(x, y);
						return;
					}
				}
			}
		} catch (Exception ignore) {
			// Fall back to the primary screen below.
		}
		dialog.setLocationRelativeTo(null);
	}
}