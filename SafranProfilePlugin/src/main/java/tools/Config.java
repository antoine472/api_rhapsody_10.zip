package tools;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import com.telelogic.rhapsody.core.IRPApplication;

public class Config extends RhapsodyTool {

    public static final String COMMAND = "SAFRAN tool configuration";

    public Config(IRPApplication rpyApp) {
        super(rpyApp);
        setLookAndFeel();
    }

    @Override
    public String commandName() {
        return COMMAND;
    }

    @Override
    public void execute() {
        SwingUtilities.invokeLater(() -> {
            try {
                createAndShowGUI();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }
    
    private void setLookAndFeel() {
        try {
            UIManager.setLookAndFeel("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void createAndShowGUI() throws IOException {
        JFrame frame = new JFrame("Configuration");
        frame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        frame.setSize(400, 300);
        
        // Make the frame always on top
        frame.setAlwaysOnTop(true);

        // Center the frame on the screen
        frame.setLocationRelativeTo(null);

        // Load configuration from properties file, or use defaults if file is missing
        Properties props = new Properties();
        File configFile = new File("config.properties");
        if (!configFile.exists()) {
            // Use default configuration
            props.setProperty("type", "true");
            props.setProperty("name", "false");
            props.setProperty("stereotype", "true");
            // Save default configuration to file
            try (FileOutputStream fos = new FileOutputStream(configFile)) {
                props.store(fos, "Default Configuration");
            }
        } else {
            // Load existing configuration
            try (FileInputStream fis = new FileInputStream(configFile)) {
                props.load(fis);
            }
        }

        // Create a tabbed pane
        JTabbedPane tabbedPane = new JTabbedPane();

        // Create a panel with a grid layout for checkboxes
        JPanel propagationPanel = new JPanel(new GridLayout(0, 1));
        JCheckBox[] checkBoxes = new JCheckBox[props.size()];

        int index = 0;
        for (String key : props.stringPropertyNames()) {
            boolean value = Boolean.parseBoolean(props.getProperty(key));
            JCheckBox checkBox = new JCheckBox(key, value);
            checkBoxes[index++] = checkBox;
            propagationPanel.add(checkBox);
        }

        // Add the propagation panel to the tabbed pane
        tabbedPane.addTab("Propagation", propagationPanel);

        // Create "Cancel" and "Apply" buttons
        JButton cancelButton = new JButton("Cancel");
        JButton applyButton = new JButton("Apply");

        cancelButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                frame.setVisible(false); // hide the frame without saving
            }
        });

        applyButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                try (FileOutputStream fos = new FileOutputStream("config.properties")) {
                    // Save current checkbox states to properties file
                    for (JCheckBox checkBox : checkBoxes) {
                        props.setProperty(checkBox.getText(), Boolean.toString(checkBox.isSelected()));
                    }
                    props.store(fos, "Updated Configuration");
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
                frame.setVisible(false); // Close the frame after saving
            }
        });

        // Create a panel for the buttons and align them to the bottom-right
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(cancelButton);
        buttonPanel.add(applyButton);

        // Add components to the frame
        frame.setLayout(new BorderLayout());
        frame.add(tabbedPane, BorderLayout.CENTER);
        frame.add(buttonPanel, BorderLayout.SOUTH);

        // Display the window
        frame.setVisible(true);
    }

	public void setTools(Map<String, RhapsodyTool> tools) {
		
		
	}

	@Override
	public boolean isUndoable() {
		return false;
	}
}
