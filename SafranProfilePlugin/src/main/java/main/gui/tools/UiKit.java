package main.gui.tools;

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.Collections;

import javax.swing.BorderFactory;
import javax.swing.ButtonModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.KeyStroke;
import java.awt.font.TextAttribute;

/**
 * Shared Swing styling for the Safran plugin dialogs (validated on the
 * "Update Redefined Ports" window): a small token palette, a filled accent
 * primary button, section titles, link buttons and an Esc-to-close helper.
 * Pure Swing, no third-party dependency and no global Look and Feel change,
 * so it is safe inside Rhapsody's shared JVM.
 */
public final class UiKit {

    private UiKit() {
    }

    // -- Token palette (contrasts validated on white) ---------------------------
    public static final Color ACCENT      = new Color(0x0B, 0x5F, 0xA5);
    public static final Color DANGER      = new Color(0x9A, 0x33, 0x33);
    public static final Color INK         = new Color(0x1D, 0x1D, 0x1F);
    public static final Color INK2        = new Color(0x5F, 0x5F, 0x5F);
    public static final Color HAIR        = new Color(0xD9, 0xDD, 0xE2);
    public static final Color SURFACE     = Color.WHITE;
    public static final Color ACCENT_WEAK = new Color(0xE7, 0xF0, 0xF8);
    public static final Color DANGER_WEAK = new Color(0xFA, 0xEC, 0xEC);
    public static final Color GROUP_BG    = new Color(0xFA, 0xFB, 0xFC);

    public static final Cursor HAND = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);

    // -- Text ------------------------------------------------------------------

    /** Uppercase, bold, slightly smaller and letter-spaced section title. */
    public static JLabel sectionTitle(String text, Color color) {
        JLabel l = new JLabel(text);
        Font base = l.getFont();
        Font small = base.deriveFont(Font.BOLD, Math.max(9f, base.getSize2D() - 1f));
        l.setFont(small.deriveFont(Collections.singletonMap(TextAttribute.TRACKING, Float.valueOf(0.08f))));
        l.setForeground(color);
        return l;
    }

    /** Muted (INK2), left-aligned label. */
    public static JLabel muted(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(INK2);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    // -- Buttons ---------------------------------------------------------------

    /** Filled, rounded accent button for the primary action (e.g. Apply / Select). */
    public static AccentButton primary(String text) {
        return new AccentButton(text);
    }

    /** Standard button, comfortably wide (avoids truncation on Windows L&F/DPI). */
    public static JButton neutral(String text) {
        JButton b = new JButton(text);
        Dimension d = b.getPreferredSize();
        b.setPreferredSize(new Dimension(Math.max(d.width + 28, 96), Math.max(d.height, 30)));
        return b;
    }

    /** Borderless, accent-colored text button ("Select all", "None", ...). */
    public static JButton link(String text, final ActionListener action) {
        JButton b = new JButton(text);
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setOpaque(false);
        b.setFocusable(false);
        b.setForeground(ACCENT);
        b.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
        b.setCursor(HAND);
        b.addActionListener(action);
        return b;
    }

    // -- Dialog helper ---------------------------------------------------------

    /** Wire the Escape key of a dialog to {@code onEscape} (typically Cancel). */
    public static void onEscape(JDialog dialog, final Runnable onEscape) {
        dialog.getRootPane().registerKeyboardAction(e -> onEscape.run(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    /**
     * Filled, rounded accent button (primary action). Custom-painted but still a
     * regular JButton: default-button (Return), focus ring and rollover / pressed
     * shades all work through the standard model.
     */
    public static final class AccentButton extends JButton {
        private static final long serialVersionUID = 1L;

        AccentButton(String text) {
            super(text);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setForeground(Color.WHITE);
            setFont(getFont().deriveFont(Font.BOLD));
            setCursor(HAND);
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            return new Dimension(Math.max(d.width + 24, 96), Math.max(d.height, 30));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            ButtonModel m = getModel();
            Color fill = ACCENT;
            if (!m.isEnabled()) fill = shade(ACCENT, 1.35f);
            else if (m.isPressed() && m.isArmed()) fill = shade(ACCENT, 0.85f);
            else if (m.isRollover()) fill = shade(ACCENT, 0.92f);

            int w = getWidth(), h = getHeight();
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, w, h, 10, 10);
            if (isFocusOwner()) {
                g2.setColor(new Color(255, 255, 255, 150));
                g2.drawRoundRect(2, 2, w - 5, h - 5, 7, 7);
            }

            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics(getFont());
            String t = getText() != null ? getText() : "";
            int tx = (w - fm.stringWidth(t)) / 2;
            int ty = (h - fm.getHeight()) / 2 + fm.getAscent();
            g2.setColor(getForeground());
            g2.drawString(t, tx, ty);
            g2.dispose();
        }

        private static Color shade(Color c, float k) {
            return new Color(clamp(c.getRed() * k), clamp(c.getGreen() * k), clamp(c.getBlue() * k));
        }

        private static int clamp(float v) {
            return Math.max(0, Math.min(255, Math.round(v)));
        }
    }
}
