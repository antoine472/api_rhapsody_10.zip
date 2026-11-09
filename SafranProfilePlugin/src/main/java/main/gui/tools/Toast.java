package main.gui.tools;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class Toast {

	public static void showToast(String message) {
		// Create a small JFrame for the toast message
		JWindow toastWindow = new JWindow();
		toastWindow.setLayout(new BorderLayout());

		// Set the toast message with styling
		JLabel label = new JLabel(message, SwingConstants.CENTER);
		label.setOpaque(true);
		label.setBackground(new Color(0, 0, 0, 200)); // Semi-transparent background
		label.setForeground(Color.WHITE);
		label.setFont(new Font("Arial", Font.BOLD, 16));
		label.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
		toastWindow.add(label, BorderLayout.CENTER);

		// Bottom-right of the screen the user works on (where Rhapsody is)
		Point at = bottomRightOfActiveScreen(250, 50);
		toastWindow.setBounds(at.x, at.y, 250, 50);

		// Display the toast
		toastWindow.setVisible(true);

		// Hide the toast after the specified duration
		new Timer(3000, new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				toastWindow.setVisible(false);
				toastWindow.dispose();
			}
		}).start();
	}
	/**
	 * Displays an information toast at the bottom-right of the screen the user
	 * works on (the one under the mouse, where Rhapsody is).
	 * @param message The message to display.
	 * @param duration The duration (in milliseconds) for which the toast is visible.
	 */
	public static void showToast(String message, int duration) {
		// Create a toast window
		JWindow toastWindow = new JWindow();
		toastWindow.setLayout(new BorderLayout());

		// Create a label with an icon and multi-line support
		JLabel label = new JLabel("<html><div style='text-align: center;'>" + message.replaceAll("\n", "<br>") + "</div></html>", SwingConstants.CENTER);
		label.setIcon(UIManager.getIcon("OptionPane.informationIcon"));
		label.setOpaque(true);
		label.setBackground(new Color(0, 0, 0, 200)); // Semi-transparent background
		label.setForeground(Color.WHITE);
		label.setFont(new Font("Arial", Font.BOLD, 16));
		label.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
		toastWindow.add(label, BorderLayout.CENTER);

		
		// Create a progress bar
		JProgressBar progressBar = new JProgressBar(0, duration);
		progressBar.setValue(duration);
		progressBar.setForeground(Color.GRAY);
		progressBar.setBorder(BorderFactory.createEmptyBorder());

		// Set custom preferred size
		progressBar.setPreferredSize(new Dimension(toastWindow.getWidth(), 5));
		toastWindow.add(progressBar, BorderLayout.SOUTH);


		// Bottom-right of the screen the user works on (where Rhapsody is)
		Point at = bottomRightOfActiveScreen(350, 75);
		toastWindow.setBounds(at.x, at.y, 350, 75);

		// Display the toast
		toastWindow.setVisible(true);

		// Timer to update progress bar and handle toast duration
		int interval = 50; // 10 ms update interval
		Timer timer = new Timer(interval, new ActionListener() {
			int timeLeft = duration;

			@Override
			public void actionPerformed(ActionEvent e) {
				timeLeft -= interval;
				progressBar.setValue(timeLeft);
				if (timeLeft <= 0) {
					toastWindow.setVisible(false);
					toastWindow.dispose();
					((Timer) e.getSource()).stop();
				}
			}
		});
		timer.start();
	}

	/** Gap between the toast and the screen edges (taskbar excluded). */
	private static final int MARGIN = 16;

	/**
	 * Top-left corner for a {@code width} x {@code height} toast in the
	 * bottom-right corner of the screen under the mouse (Rhapsody's main window
	 * is native, so the mouse is the reliable hint of the screen in use), above
	 * its taskbar. Falls back to the primary screen.
	 */
	private static Point bottomRightOfActiveScreen(int width, int height) {
		GraphicsConfiguration screen = null;
		try {
			PointerInfo pointer = MouseInfo.getPointerInfo();
			if (pointer != null) {
				Point mouse = pointer.getLocation();
				for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
					GraphicsConfiguration gc = device.getDefaultConfiguration();
					if (gc.getBounds().contains(mouse)) {
						screen = gc;
						break;
					}
				}
			}
		} catch (Exception ignore) {
		}
		if (screen == null) {
			screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
		}

		Rectangle area = screen.getBounds();
		try {
			Insets taskbar = Toolkit.getDefaultToolkit().getScreenInsets(screen);
			area = new Rectangle(area.x + taskbar.left, area.y + taskbar.top,
					area.width - taskbar.left - taskbar.right,
					area.height - taskbar.top - taskbar.bottom);
		} catch (Exception ignore) {
		}
		return new Point(area.x + area.width - width - MARGIN,
				area.y + area.height - height - MARGIN);
	}


}