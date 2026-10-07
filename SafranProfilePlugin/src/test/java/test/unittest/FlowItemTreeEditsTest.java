package test.unittest;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.telelogic.rhapsody.core.IRPModelElement;

import main.gui.tools.FlowItemTreeEdits;
import main.gui.tools.model.FlowItemEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * Pure unit tests for {@link FlowItemTreeEdits#relocate} — the working-model
 * update that keeps a Flow Item's children under it when the item is renamed
 * or moved. Regression guard for the bug where renaming a Flow Item that has
 * nested Flow Items left the children under a phantom package with the old name.
 *
 * <p>{@link FlowItemEntry} carries a null {@code element} here (allowed for tests);
 * only {@code key}, {@code name} and {@code pkgPath} matter.</p>
 */
class FlowItemTreeEditsTest {

    private static FlowItemEntry entry(String key, String name, String... pkg) {
        return new FlowItemEntry(key, name, "", List.of(pkg), null);
    }

    private static FlowItemEntry byKey(List<FlowItemEntry> es, String key) {
        return es.stream().filter(e -> e.key().equals(key)).findFirst().orElseThrow();
    }

    @Test
    void rename_keepsChildrenUnderTheNewName() {
        // A under package P; B under A; C under B.
        List<FlowItemEntry> in = List.of(
                entry("a", "A", "P"),
                entry("b", "B", "P", "A"),
                entry("c", "C", "P", "A", "B"));

        List<FlowItemEntry> out = FlowItemTreeEdits.relocate(
                in, "a", List.of("P", "A"), List.of("P", "X"), "X");

        assertEquals("X",            byKey(out, "a").name());
        assertEquals(List.of("P"),   byKey(out, "a").pkgPath());
        assertEquals(List.of("P", "X"),      byKey(out, "b").pkgPath());
        assertEquals(List.of("P", "X", "B"), byKey(out, "c").pkgPath());
        // child names unchanged
        assertEquals("B", byKey(out, "b").name());
        assertEquals("C", byKey(out, "c").name());
    }

    @Test
    void move_reparentsItemAndAllDescendants() {
        List<FlowItemEntry> in = List.of(
                entry("a", "A", "P"),
                entry("b", "B", "P", "A"),
                entry("c", "C", "P", "A", "B"));

        // Move A (name unchanged) from package P to package Q.
        List<FlowItemEntry> out = FlowItemTreeEdits.relocate(
                in, "a", List.of("P", "A"), List.of("Q", "A"), "A");

        assertEquals(List.of("Q"),           byKey(out, "a").pkgPath());
        assertEquals(List.of("Q", "A"),      byKey(out, "b").pkgPath());
        assertEquals(List.of("Q", "A", "B"), byKey(out, "c").pkgPath());
    }

    @Test
    void rename_leafWithoutChildren_onlyRenamesItself() {
        List<FlowItemEntry> in = List.of(
                entry("a", "A", "P"),
                entry("z", "Z", "P"));   // unrelated sibling

        List<FlowItemEntry> out = FlowItemTreeEdits.relocate(
                in, "a", List.of("P", "A"), List.of("P", "X"), "X");

        assertEquals("X",          byKey(out, "a").name());
        assertEquals(List.of("P"), byKey(out, "a").pkgPath());
        // unrelated entry untouched
        assertEquals("Z",          byKey(out, "z").name());
        assertEquals(List.of("P"), byKey(out, "z").pkgPath());
    }

    @Test
    void doesNotTouchEntriesOutsideTheSubtree() {
        // "AA" shares a name prefix with "A" but is a different sibling — must not match.
        List<FlowItemEntry> in = List.of(
                entry("a",  "A",  "P"),
                entry("aa", "AA", "P"),
                entry("x",  "X",  "P", "AA"));   // child of AA, not of A

        List<FlowItemEntry> out = FlowItemTreeEdits.relocate(
                in, "a", List.of("P", "A"), List.of("P", "R"), "R");

        assertEquals("R",                byKey(out, "a").name());
        assertEquals(List.of("P", "AA"), byKey(out, "x").pkgPath()); // untouched
    }

    @Test
    void move_carriesTheNewAncestorElements_toTheItemAndItsDescendants() {
        // Given A (element ea) in P/Sub and its child B, moved under Q/R where only Q's element is known.
        IRPModelElement p = mock(IRPModelElement.class);
        IRPModelElement sub = mock(IRPModelElement.class);
        IRPModelElement ea = mock(IRPModelElement.class);
        IRPModelElement eb = mock(IRPModelElement.class);
        IRPModelElement q = mock(IRPModelElement.class);
        List<FlowItemEntry> in = List.of(
                new FlowItemEntry("a", "A", "", List.of("P", "Sub"), ea, List.of(p, sub)),
                new FlowItemEntry("b", "B", "", List.of("P", "Sub", "A"), eb, List.of(p, sub, ea)));

        // When
        List<FlowItemEntry> out = FlowItemTreeEdits.relocate(
                in, "a", List.of("P", "Sub", "A"), List.of("Q", "R", "A"), "A", List.of(q));

        // Then: the missing R element becomes null, the item keeps its own element.
        assertEquals(Arrays.asList(q, null), byKey(out, "a").pathElements());
        assertEquals(List.of("Q", "R", "A"), byKey(out, "b").pkgPath());
        assertEquals(Arrays.asList(q, null, ea), byKey(out, "b").pathElements());
        assertEquals(eb, byKey(out, "b").element());
    }

    @Test
    void relocate_unknownKey_onlyRewritesTheSubtreePaths() {
        List<FlowItemEntry> in = List.of(entry("b", "B", "P", "A"));

        List<FlowItemEntry> out = FlowItemTreeEdits.relocate(
                in, "missing", List.of("P", "A"), List.of("P", "X"), "X", null);

        assertEquals(List.of("P", "X"), byKey(out, "b").pkgPath());
        assertEquals(Arrays.asList(null, null), byKey(out, "b").pathElements());
    }
}
