package test.unittest;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import com.telelogic.rhapsody.core.IRPModelElement;
import com.telelogic.rhapsody.core.IRPProject;
import com.telelogic.rhapsody.core.IRPStereotype;

import main.gui.tools.model.FlowItemEntry;
import main.gui.tools.model.FlowItemScanResult;
import main.gui.tools.model.PackageEntry;
import main.gui.tools.model.RhapsodyFlowItemScanner;

import static test.unittest.RhpTestMocks.collectionOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests (no live Rhapsody) of {@link RhapsodyFlowItemScanner}: how
 * the selector tree is built from the model (Flow Packages, nested Flow Items,
 * owner chains, stereotype labels, stable keys).
 *
 * <p>The model is a small tree of mocks:</p>
 * <pre>
 *   Project
 *   +-- Flows   (Package, "F - Flow Package")
 *   |   +-- Sub (Package)
 *   |       +-- A   (Flow Item, &lt;&lt;DataFlow&gt;&gt;)
 *   |           +-- B (Flow Item nested in A)
 *   +-- Other   (Package)
 *       +-- C   (Flow Item outside any Flow Package)
 *       +-- Blk (Block, not a Flow Item)
 * </pre>
 */
class RhapsodyFlowItemScannerTest {

    private static final String FLOW_PKG = "F - Flow Package";

    private final IRPProject project = mock(IRPProject.class);
    private final IRPModelElement flows;
    private final IRPModelElement sub;
    private final IRPModelElement other;
    private final IRPModelElement a;
    private final IRPModelElement b;
    private final IRPModelElement c;
    private final IRPModelElement block;

    RhapsodyFlowItemScannerTest() {
        when(project.getMetaClass()).thenReturn("Project");
        flows = element("Flows", "Package", FLOW_PKG, project);
        when(flows.getFullPathName()).thenReturn("Flows");
        sub = element("Sub", "Package", "", flows);
        other = element("Other", "Package", null, project);
        a = element("A", "Class", "Flow Item", sub);
        b = element("B", "Class", "Flow Item", a);
        c = element("C", "Class", "Flow Item", other);
        block = element("Blk", "Class", "Block", other);

        IRPStereotype dataFlow = stereotype("DataFlow", false);
        IRPStereotype newTerm = stereotype("Flow Item", true);
        IRPStereotype sameNameAsTerm = stereotype("Flow Item", false);
        doReturn(collectionOf(newTerm, dataFlow, sameNameAsTerm)).when(a).getStereotypes();

        doReturn(collectionOf(flows, sub, other)).when(project).getNestedElementsByMetaClass("Package", 1);
        doReturn(collectionOf(block, c, b, a)).when(project).getNestedElementsByMetaClass("Class", 1);
    }

    private static IRPModelElement element(String name, String metaClass, String udmc, IRPModelElement owner) {
        IRPModelElement el = mock(IRPModelElement.class);
        when(el.getName()).thenReturn(name);
        when(el.getGUID()).thenReturn("GUID-" + name);
        when(el.getMetaClass()).thenReturn(metaClass);
        when(el.getUserDefinedMetaClass()).thenReturn(udmc);
        when(el.getOwner()).thenReturn(owner);
        doReturn(collectionOf()).when(el).getStereotypes();
        return el;
    }

    private static IRPStereotype stereotype(String name, boolean newTerm) {
        IRPStereotype st = mock(IRPStereotype.class);
        when(st.getName()).thenReturn(name);
        when(st.getIsNewTerm()).thenReturn(newTerm ? 1 : 0);
        return st;
    }

    private static FlowItemEntry byName(FlowItemScanResult r, String name) {
        return r.entries().stream().filter(e -> e.name().equals(name)).findFirst().orElseThrow();
    }

    // ------------------------------------------------------------------
    // scan()
    // ------------------------------------------------------------------

    @Test
    void scan_listsFlowItemsUnderFlowPackages_withTheirContainerPath() {
        FlowItemScanResult r = new RhapsodyFlowItemScanner(project).scan();

        assertEquals(2, r.entries().size(), "C (outside Flow Packages) and the Block are dropped");
        FlowItemEntry ea = byName(r, "A");
        assertEquals(List.of("Flows", "Sub"), ea.pkgPath());
        assertEquals(Arrays.asList(flows, sub), ea.pathElements());
        assertEquals("GUID-A", ea.key());
        assertSame(a, ea.element());
        FlowItemEntry eb = byName(r, "B");
        assertEquals(List.of("Flows", "Sub", "A"), eb.pkgPath(), "a nested Flow Item keeps its parent item");
        assertEquals(Arrays.asList(flows, sub, a), eb.pathElements());
    }

    @Test
    void scan_stereotypeLabel_skipsNewTermsAndTheFlowItemName() {
        FlowItemScanResult r = new RhapsodyFlowItemScanner(project).scan();

        assertEquals("<<DataFlow>>", byName(r, "A").stereotypeLabel());
        assertEquals("", byName(r, "B").stereotypeLabel());
    }

    @Test
    void scan_entriesAreSortedByDisplayLabel_packagesListed() {
        FlowItemScanResult r = new RhapsodyFlowItemScanner(project).scan();

        // "<<DataFlow>> Flows > Sub > A" sorts before "Flows > Sub > A > B".
        assertEquals("A", r.entries().get(0).name());
        assertTrue(r.hasFlowPackages());
        assertEquals(1, r.flowPackages().size());
        PackageEntry pkg = r.flowPackages().get(0);
        assertEquals("GUID-Flows", pkg.key());
        assertEquals("Flows", pkg.fullPath());
        assertEquals("Flows", pkg.toString());
        assertSame(flows, pkg.element());
    }

    @Test
    void scan_noFlowPackage_orNoProject_isEmpty() {
        when(flows.getUserDefinedMetaClass()).thenReturn("");

        FlowItemScanResult r = new RhapsodyFlowItemScanner(project).scan();

        assertTrue(r.entries().isEmpty());
        assertFalse(r.hasFlowPackages());
        assertTrue(new RhapsodyFlowItemScanner(null).scan().entries().isEmpty());
    }

    @Test
    void scan_failingClassScan_givesNoEntryButKeepsPackages() {
        when(project.getNestedElementsByMetaClass("Class", 1)).thenThrow(new RuntimeException("COM"));

        FlowItemScanResult r = new RhapsodyFlowItemScanner(project).scan();

        assertTrue(r.entries().isEmpty());
        assertEquals(1, r.flowPackages().size());
    }

    // ------------------------------------------------------------------
    // fromCandidates()
    // ------------------------------------------------------------------

    @Test
    void fromCandidates_withoutCreate_keepsEveryCandidate_noPackageScan() {
        FlowItemScanResult r = RhapsodyFlowItemScanner.fromCandidates(List.of(a, c), project, false);

        assertEquals(2, r.entries().size());
        assertEquals(List.of(), byName(r, "C").pkgPath(), "outside a Flow Package: shown at the root");
        assertEquals(List.of("Flows", "Sub"), byName(r, "A").pkgPath());
        assertTrue(r.flowPackages().isEmpty());
        assertTrue(r.hasFlowPackages(), "A has a Flow Package ancestor");
    }

    @Test
    void fromCandidates_withoutCreate_noFlowPackageAncestor_hasNoPackages() {
        FlowItemScanResult r = RhapsodyFlowItemScanner.fromCandidates(Arrays.asList(c, null), project, false);

        assertFalse(r.hasFlowPackages());
        assertEquals(1, r.entries().size());
    }

    @Test
    void fromCandidates_withCreate_offersTheFlowPackages() {
        FlowItemScanResult r = RhapsodyFlowItemScanner.fromCandidates(List.of(b), project, true);

        assertEquals(1, r.flowPackages().size());
        assertTrue(r.hasFlowPackages());
        assertTrue(RhapsodyFlowItemScanner.fromCandidates(List.of(b), null, true).entries().isEmpty());
    }

    @Test
    void fromCandidates_keyFallsBackOnFullPath_thenOnIdentity() {
        IRPModelElement noGuid = element("NoGuid", "Class", "Flow Item", c);
        when(noGuid.getGUID()).thenReturn(" ");
        when(noGuid.getFullPathName()).thenReturn("Other::NoGuid");
        IRPModelElement nothing = element("Nothing", "Class", "Flow Item", c);
        when(nothing.getGUID()).thenThrow(new RuntimeException("COM"));

        FlowItemScanResult r = RhapsodyFlowItemScanner.fromCandidates(List.of(noGuid, nothing), project, false);

        assertEquals("fp:Other::NoGuid", byName(r, "NoGuid").key());
        assertTrue(byName(r, "Nothing").key().startsWith("id:"));
    }

    @Test
    void fromCandidates_labelWinsOverName_blankNamesGivePlaceholder() {
        when(a.getDisplayName()).thenReturn("Item A");
        IRPModelElement unnamed = element(" ", "Class", "Flow Item", c);

        FlowItemScanResult r = RhapsodyFlowItemScanner.fromCandidates(List.of(a, unnamed), project, false);

        assertEquals("Item A", r.entries().stream().filter(e -> e.element() == a).findFirst().orElseThrow().name());
        assertEquals("<unnamed>", r.entries().stream().filter(e -> e.element() == unnamed).findFirst().orElseThrow().name());
    }

    // ------------------------------------------------------------------
    // fromRoot()
    // ------------------------------------------------------------------

    @Test
    void fromRoot_pathGoesFromTheRootDownToTheParent() {
        IRPModelElement system = element("Sys", "Class", "System", project);
        IRPModelElement fn = element("F1", "Class", "Function", system);
        IRPModelElement subFn = element("F11", "Class", "Function", fn);

        FlowItemScanResult r = RhapsodyFlowItemScanner.fromRoot(List.of(subFn, fn), system, true);

        assertEquals(List.of("Sys"), byName(r, "F1").pkgPath());
        assertEquals(List.of("Sys", "F1"), byName(r, "F11").pkgPath());
        assertEquals(Arrays.asList(system, fn), byName(r, "F11").pathElements());
        assertEquals(1, r.flowPackages().size(), "the root is the only create target");
        assertSame(system, r.flowPackages().get(0).element());
        assertTrue(r.hasFlowPackages());
    }

    @Test
    void fromRoot_rootMatchedByGuid_andProjectStopsTheWalk() {
        IRPModelElement system = element("Sys", "Class", "System", project);
        IRPModelElement systemProxy = element("Sys", "Class", "System", project); // same GUID
        IRPModelElement fn = element("F1", "Class", "Function", systemProxy);
        IRPModelElement stray = element("X", "Class", "Function", project);  // not under the root

        FlowItemScanResult r = RhapsodyFlowItemScanner.fromRoot(List.of(fn, stray), system, false);

        assertEquals(List.of("Sys"), byName(r, "F1").pkgPath());
        assertEquals(List.of(), byName(r, "X").pkgPath());
        assertTrue(r.flowPackages().isEmpty());
        assertTrue(r.hasFlowPackages(), "without create, containers exist as soon as there are entries");
    }

    @Test
    void fromRoot_noItems_hasNoContainers() {
        FlowItemScanResult r = RhapsodyFlowItemScanner.fromRoot(null, null, false);

        assertTrue(r.entries().isEmpty());
        assertFalse(r.hasFlowPackages());
    }
}
