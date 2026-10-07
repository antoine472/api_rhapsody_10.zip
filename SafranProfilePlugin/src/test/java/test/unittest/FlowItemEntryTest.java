package test.unittest;

import main.gui.tools.model.FlowItemEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FlowItemEntryTest {

    private static FlowItemEntry entry(String name, String stereo, List<String> path) {
        return new FlowItemEntry("key-" + name, name, stereo, path, null /* no Rhapsody */);
    }

    // ── displayLabel ────────────────────────────────────────────────────────

    @Test
    void displayLabel_noStereo_showsPathAndName() {
        var e = entry("MyFlow", "", List.of("FlowPkg1", "SubPkg"));
        assertEquals("FlowPkg1 \u203A SubPkg \u203A MyFlow", e.displayLabel());
    }

    @Test
    void displayLabel_withStereo_showsStereoFirst() {
        var e = entry("MyFlow", "<<DataFlow>>", List.of("FlowPkg1"));
        assertEquals("<<DataFlow>> FlowPkg1 \u203A MyFlow", e.displayLabel());
    }

    @Test
    void displayLabel_emptyPath_showsNameOnly() {
        var e = entry("Bare", "", List.of());
        assertEquals("Bare", e.displayLabel());
    }

    // ── matches ─────────────────────────────────────────────────────────────

    @Test
    void matches_emptyTokens_alwaysTrue() {
        var e = entry("Anything", "", List.of("Pkg"));
        assertTrue(e.matches(new String[0]));
    }

    /**
     * matches() takes tokens that are ALREADY lowercased (parameter
     * {@code lowerTokens}): the selector lowercases the query once, then
     * tests every entry. The case-insensitivity therefore comes from the
     * label side (lowercased here) plus the caller's lowercasing.
     */
    @Test
    void matches_nameToken_caseInsensitive() {
        var e = entry("ThermalFlow", "", List.of("FlowPkg1"));
        assertTrue(e.matches(new String[]{"thermal"}));
        assertTrue(e.matches(lowerTokensLikeTheSelector("THERMAL")));
        assertTrue(e.matches(lowerTokensLikeTheSelector("  tHeRmAlFlOw  ")));
        assertFalse(e.matches(lowerTokensLikeTheSelector("electric")));
    }

    /** Same tokenisation as FlowItemSelectorDialog's filter. */
    private static String[] lowerTokensLikeTheSelector(String query) {
        return query.trim().toLowerCase(java.util.Locale.ROOT).split("\\s+");
    }

    @Test
    void matches_packageToken_findsViaPath() {
        var e = entry("ItemA", "", List.of("FlowPkg2", "SubPkgX"));
        assertTrue(e.matches(new String[]{"flowpkg2"}));
        assertTrue(e.matches(new String[]{"subpkgx"}));
    }

    @Test
    void matches_andSemantics_allTokensMustMatch() {
        var e = entry("ThermalFlow", "", List.of("FlowPkg1"));
        assertTrue(e.matches(new String[]{"thermal", "flowpkg1"}));
        assertFalse(e.matches(new String[]{"thermal", "flowpkg2"}));
    }

    @Test
    void matches_stereoToken_findsViaStereo() {
        var e = entry("DataItem", "<<DataFlow>>", List.of("Pkg"));
        assertTrue(e.matches(new String[]{"dataflow"}));
    }

    // ── immutability ─────────────────────────────────────────────────────────

    @Test
    void pkgPath_isImmutable() {
        var mutable = new java.util.ArrayList<>(List.of("Pkg1"));
        var e = entry("X", "", mutable);
        mutable.add("Pkg2");                    // mutate original
        assertEquals(1, e.pkgPath().size());    // record must be unaffected
    }
}
