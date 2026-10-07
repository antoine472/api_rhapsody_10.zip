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

		// Position the toast at the bottom-right of the screen
		Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
		int x = screenSize.width - 300; // Adjust the width as needed
		int y = screenSize.height - 100; // Adjust the height as needed
		toastWindow.setBounds(x, y, 250, 50);

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
	 * Displays a toast message at the bottom-right of the screen.
	 * @param message The message to display.
	 * @param duration The duration (in milliseconds) for which the toast is visible.
	 */
	public static void showToast(String message, int duration) {
		// Create a toast window
		JWindow toastWindow = new JWindow();
		toastWindow.setLayout(new BorderLayout());

		// Create a label with an icon and multi-line support
		JLabel label = new JLabel("<html><div style='text-align: center;'>" + message.replaceAll("\n", "<br>") + "</div></html>", SwingConstants.CENTER);
		label.setIcon(UIManager.getIcon("OptionPane.errorIcon")); // Add an icon (change to desired icon)
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


		// Position the toast at the bottom-right of the screen
		Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
		int x = screenSize.width - 350; // Adjust the width as needed
		int y = screenSize.height - 125; // Adjust the height as needed
		toastWindow.setBounds(x, y, 350, 75);  // Adjust size to accommodate layout

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


}
