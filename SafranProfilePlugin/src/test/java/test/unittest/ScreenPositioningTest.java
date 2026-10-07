package test.unittest;

import org.junit.jupiter.api.Test;

import java.awt.Rectangle;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates the screen-centre geometry used in FlowItemSelectorDialog.centerOnRhapsodyScreen().
 * Does not test AWT/Swing rendering — only the coordinate arithmetic.
 *
 * The helper under test is private static, so this class reproduces the same
 * arithmetic (Option B of the fallback chain) as a standalone computation:
 *
 *   x = bounds.x + Math.max(0, (bounds.width  - dialogWidth)  / 2)
 *   y = bounds.y + Math.max(0, (bounds.height - dialogHeight) / 2)
 *
 * Key invariant: bounds.x is added as offset so that secondary/tertiary screens
 * (whose x coordinate is non-zero) produce absolute screen positions, not
 * positions relative to the primary screen origin.
 */
public class ScreenPositioningTest {

    /**
     * Reproduces the arithmetic from FlowItemSelectorDialog.centerOnRhapsodyScreen()
     * Option B: manual centring on a specific screen bounds.
     *
     * @param screenBounds  the {@code GraphicsDevice.getDefaultConfiguration().getBounds()}
     *                      of the target screen
     * @param dialogWidth   dialog width after {@code pack()}
     * @param dialogHeight  dialog height after {@code pack()}
     * @return {x, y} absolute screen coordinates for the dialog's top-left corner
     */
    private static int[] computeCenter(Rectangle screenBounds, int dialogWidth, int dialogHeight) {
        int x = screenBounds.x + Math.max(0, (screenBounds.width  - dialogWidth)  / 2);
        int y = screenBounds.y + Math.max(0, (screenBounds.height - dialogHeight) / 2);
        return new int[]{x, y};
    }

    /**
     * Primary screen (x=0, y=0, 1920x1080) — dialog 700x460.
     * Expected: (610, 310).
     * Calculation: (1920-700)/2 = 610, (1080-460)/2 = 310.
     */
    @Test
    void primaryScreen_dialogCentred() {
        Rectangle screen = new Rectangle(0, 0, 1920, 1080);
        int[] pos = computeCenter(screen, 700, 460);
        assertEquals(610, pos[0], "x should be (1920-700)/2 = 610");
        assertEquals(310, pos[1], "y should be (1080-460)/2 = 310");
    }

    /**
     * Secondary screen at x=1920 (right of primary), same resolution.
     * Validates that bounds.x (1920) is added as offset so the dialog lands
     * on the correct physical screen, not on the primary screen.
     * Expected: (2530, 310) = 1920 + 610, 310.
     */
    @Test
    void secondaryScreen_offsetApplied() {
        // Secondary screen starting at x=1920
        Rectangle screen = new Rectangle(1920, 0, 1920, 1080);
        int[] pos = computeCenter(screen, 700, 460);
        assertEquals(1920 + 610, pos[0], "x must include screen offset: 1920 + (1920-700)/2");
        assertEquals(310,        pos[1], "y stays (1080-460)/2 since y-offset is 0");
    }

    /**
     * Third monitor above the primary (y=-1080), higher resolution 2560x1440.
     * Validates correct handling of negative y offsets.
     * Expected: x=930, y=-590.
     * Calculation: (2560-700)/2 = 930; -1080 + (1440-460)/2 = -1080 + 490 = -590.
     */
    @Test
    void thirdScreen_verticalOffset() {
        // Screen above primary (common on Windows/macOS multi-monitor arrangements)
        Rectangle screen = new Rectangle(0, -1080, 2560, 1440);
        int[] pos = computeCenter(screen, 700, 460);
        assertEquals(930,        pos[0], "x should be (2560-700)/2 = 930");
        assertEquals(-1080 + 490, pos[1], "y should be -1080 + (1440-460)/2 = -590");
    }

    /**
     * Dialog larger than the screen in both dimensions.
     * Math.max(0, ...) must clamp negative offsets to 0, so the dialog's
     * top-left is placed at the screen origin rather than off-screen.
     * Screen is at x=1920, so expected x=1920 (not 0 or negative).
     */
    @Test
    void dialogLargerThanScreen_noNegativeOffset() {
        // Dialog wider and taller than the screen
        Rectangle screen = new Rectangle(1920, 0, 800, 600);
        int[] pos = computeCenter(screen, 1000, 700);
        assertEquals(1920, pos[0], "Math.max(0,(800-1000)/2)=0, plus offset 1920 \u2192 1920");
        assertEquals(0,    pos[1], "Math.max(0,(600-700)/2)=0, plus y-offset 0 \u2192 0");
    }
}
