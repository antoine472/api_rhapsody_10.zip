package utils;

import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.KeyboardFocusManager;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;

/**
 * Places a Swing dialog on the screen the user is actually working on (where
 * Rhapsody is), instead of the primary screen.
 *
 * <p>Rhapsody's main window is native, so no Java window is "active" when a
 * plugin dialog opens; the reliable heuristic is the screen that contains the
 * mouse cursor. Strategy: (A) a visible active Java window, else (B) the screen
 * under the mouse, else (C) the primary screen.</p>
 */
public final class DialogPlacement {

    private DialogPlacement() {}

    /** Centres {@code dialog} on the screen the user is working on. Call after pack(). */
    public static void centerOnActiveScreen(Window dialog) {
        if (dialog == null) return;

        // Option A — a visible active Java window (rare for a native-host plugin)
        try {
            Window active = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
            if (active != null && active.isVisible()) {
                dialog.setLocationRelativeTo(active);
                return;
            }
        } catch (Exception ignore) {}

        // Option B — the screen that contains the mouse cursor (where Rhapsody is)
        try {
            Point mouse = MouseInfo.getPointerInfo().getLocation();
            for (GraphicsDevice gd :
                    GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
                Rectangle b = gd.getDefaultConfiguration().getBounds();
                if (b.contains(mouse)) {
                    int x = b.x + Math.max(0, (b.width  - dialog.getWidth())  / 2);
                    int y = b.y + Math.max(0, (b.height - dialog.getHeight()) / 2);
                    dialog.setLocation(x, y);
                    return;
                }
            }
        } catch (Exception ignore) {}

        // Option C — primary screen fallback
        dialog.setLocationRelativeTo(null);
    }
}
