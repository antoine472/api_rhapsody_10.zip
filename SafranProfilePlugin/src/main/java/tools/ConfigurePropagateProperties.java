package tools;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.LinkedHashMap;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import com.telelogic.rhapsody.core.IRPApplication;

public class ConfigurePropagateProperties extends RhapsodyTool {

    public static final String COMMAND = "Safran Toolkit...\\Configure Propagate properties";

    private final LinkedHashMap<String, String> propertiesMap = new LinkedHashMap<>();
    private final LinkedHashMap<String, Boolean> propagateProperties = new LinkedHashMap<>();
    private final LinkedHashMap<String, JCheckBox> checkBoxes = new LinkedHashMap<>();

    private JDialog dialog;

    public ConfigurePropagateProperties(IRPApplication rpyApp) {
        super(rpyApp);

        initProperties();
        refreshPropertyValues();
    }

    private void initProperties() {

        propertiesMap.put("Direction", "SafranToolsSettings.PortPropagation.DirectionPropagation");
        propertiesMap.put("Type", "SafranToolsSettings.PortPropagation.TypePropagation");
        propertiesMap.put("Stereotypes", "SafranToolsSettings.PortPropagation.StereotypesPropagation");
        propertiesMap.put("Name", "SafranToolsSettings.PortPropagation.NamePropagation");
        propertiesMap.put("Label", "SafranToolsSettings.PortPropagation.LabelPropagation");
        propertiesMap.put("Conveys Flow", "SafranToolsSettings.PortPropagation.ConveysFlowUpdate");
        propertiesMap.put("Description", "SafranToolsSettings.PortPropagation.DescriptionPropagation");
    }

    @Override
    public void execute() {
        rhpLog.debug("Start - Configuration Propagation");

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {

                if (dialog == null) {
                    createUI();
                }

                refreshPropertyValues();
                refreshCheckBoxes();

                dialog.pack();
                centerOnMouseScreen();

                dialog.setAlwaysOnTop(true);
                dialog.toFront();
                dialog.requestFocus();
                dialog.setVisible(true);
                dialog.setAlwaysOnTop(false);
            }
        });

        rhpLog.debug("End - Configuration Propagation");
    }

    private void createUI() {

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignore) {
        }

        dialog = new JDialog((Frame) null, "Configuration Propagation", true);
        dialog.setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
        dialog.setMinimumSize(new Dimension(500, 360));
        dialog.setLayout(new BorderLayout());

        JPanel headerPanel = new JPanel(new BorderLayout(4, 4));
        headerPanel.setBackground(new Color(45, 62, 80));
        headerPanel.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        JLabel titleLabel = new JLabel("Port Propagation Settings");
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setFont(titleLabel.getFont().deriveFont(16f));

        JLabel subtitleLabel = new JLabel("Select properties to propagate");
        subtitleLabel.setForeground(new Color(210, 210, 210));

        headerPanel.add(titleLabel, BorderLayout.NORTH);
        headerPanel.add(subtitleLabel, BorderLayout.SOUTH);

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setBackground(Color.WHITE);
        centerPanel.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        JPanel propertiesPanel = new JPanel(new GridLayout(0, 1, 6, 6));
        propertiesPanel.setBackground(Color.WHITE);

        for (String key : propertiesMap.keySet()) {

            JCheckBox checkBox = new JCheckBox(key);
            checkBox.setBackground(Color.WHITE);
            checkBox.setFocusPainted(false);
            checkBox.setToolTipText(propertiesMap.get(key));

            checkBoxes.put(key, checkBox);
            propertiesPanel.add(checkBox);
        }

        JScrollPane scrollPane = new JScrollPane(propertiesPanel);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getViewport().setBackground(Color.WHITE);

        centerPanel.add(scrollPane, BorderLayout.CENTER);

        JPanel footerPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        footerPanel.setBackground(new Color(245, 245, 245));
        footerPanel.setBorder(BorderFactory.createEmptyBorder(10, 16, 12, 16));

        JButton cancelButton = new JButton("Cancel");
        JButton okButton = new JButton("OK");

        okButton.setBackground(new Color(45, 62, 80));
        okButton.setForeground(Color.WHITE);
        okButton.setFocusPainted(false);
        okButton.setOpaque(true);
        okButton.setBorderPainted(false);

        cancelButton.addActionListener(e -> dialog.setVisible(false));

        okButton.addActionListener(e -> {
            applyValues();
            dialog.setVisible(false);
        });

        footerPanel.add(cancelButton);
        footerPanel.add(okButton);

        dialog.add(headerPanel, BorderLayout.NORTH);
        dialog.add(centerPanel, BorderLayout.CENTER);
        dialog.add(footerPanel, BorderLayout.SOUTH);

        dialog.getRootPane().setDefaultButton(okButton);
    }

    private void refreshPropertyValues() {

        for (String key : propertiesMap.keySet()) {
            String value = rhApp.activeProject().getPropertyValue(propertiesMap.get(key));
            propagateProperties.put(key, "True".equalsIgnoreCase(value));
        }
    }

    private void refreshCheckBoxes() {

        for (String key : checkBoxes.keySet()) {
            checkBoxes.get(key).setSelected(Boolean.TRUE.equals(propagateProperties.get(key)));
        }
    }

    private void applyValues() {

        for (String key : checkBoxes.keySet()) {
            boolean selected = checkBoxes.get(key).isSelected();
            propagateProperties.put(key, selected);
            rhApp.activeProject().setPropertyValue(
                    propertiesMap.get(key),
                    selected ? "True" : "False");
        }
    }

    private void centerOnMouseScreen() {

        Point mouse = MouseInfo.getPointerInfo().getLocation();

        GraphicsDevice[] screens = GraphicsEnvironment
                .getLocalGraphicsEnvironment()
                .getScreenDevices();

        for (GraphicsDevice screen : screens) {
            Rectangle bounds = screen.getDefaultConfiguration().getBounds();
            if (bounds.contains(mouse)) {
                int x = bounds.x + (bounds.width - dialog.getWidth()) / 2;
                int y = bounds.y + (bounds.height - dialog.getHeight()) / 2;
                dialog.setLocation(x, y);
                return;
            }
        }

        dialog.setLocationRelativeTo(null);
    }

    @Override
    public String commandName() {
        return COMMAND;
    }

    @Override
    public boolean isUndoable() {
        return false;
    }

    @Override
    public boolean isInteractive() {
        return true;
    }
}
