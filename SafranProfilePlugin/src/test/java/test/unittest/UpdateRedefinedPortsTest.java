package test.unittest;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import com.telelogic.rhapsody.core.IRPApplication;
import com.telelogic.rhapsody.core.IRPClass;
import com.telelogic.rhapsody.core.IRPGeneralization;
import com.telelogic.rhapsody.core.IRPModelElement;

import tools.UpdateRedefinedPorts;

import static test.unittest.RhpTestMocks.collectionOf;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires purs (sans instance Rhapsody vivante) pour la résolution de
 * portée (REQ-5) de {@link UpdateRedefinedPorts} — la commande de menu
 * <em>Update Redefined Ports</em> (artefact 2 — Toolkit).
 *
 * <p><b>Périmètre volontairement restreint à {@code resolveTargets()}</b>,
 * exercée via réflexion ({@code setAccessible(true)} sur une méthode
 * {@code private}, vérifié au préalable qu'il fonctionne sans problème de
 * module sur ce projet — classpath simple, pas de {@code module-info.java}).
 * C'est la seule partie de {@code execute()} qui ne touche pas Swing/AWT :
 * dès que des cibles sont résolues, {@code execute()} appelle inconditionnellement
 * {@code main.gui.tools.Toast.showToast(...)} (construit un {@code JWindow}
 * réel et appelle {@code Toolkit.getDefaultToolkit()}), et s'il y a des
 * suppressions à confirmer, {@code confirmRemovals(...)} ouvre un vrai
 * {@code JOptionPane} modal. Les deux nécessitent un affichage et
 * bloqueraient (ou planteraient en environnement headless/CI) un test
 * automatisé — <b>ni l'un ni l'autre n'est donc exercé ici</b>. La logique
 * qu'ils orchestrent ({@code computePlan}, {@code redefinePorts},
 * {@code hideRedefinedPorts}) est déjà entièrement couverte dans
 * {@code RedefinedPortsServiceTest}.</p>
 */
class UpdateRedefinedPortsTest {

    private static final String REF_FUNCTION = "References Function";

    @SuppressWarnings("unchecked")
    private static List<IRPModelElement> resolveTargets(UpdateRedefinedPorts tool) throws Exception {
        Method m = UpdateRedefinedPorts.class.getDeclaredMethod("resolveTargets");
        m.setAccessible(true);
        return (List<IRPModelElement>) m.invoke(tool);
    }

    /** Builds an IRPClass mock that {@code RedefinedPortsService.isReferenceClass(...)} recognizes as a reference. */
    private static IRPClass referenceClass(String guid) {
        IRPGeneralization gen = mock(IRPGeneralization.class);
        when(gen.getUserDefinedMetaClass()).thenReturn(REF_FUNCTION);

        IRPClass ref = mock(IRPClass.class);
        when(ref.getGUID()).thenReturn(guid);
        doReturn(collectionOf(gen)).when(ref).getGeneralizations();
        return ref;
    }

    // ==================================================================
    // Construction & trivial contract (RhapsodyTool)
    // ==================================================================

    @Test
    void constructor_doesNotThrow_withMockApplication() {
        IRPApplication app = mock(IRPApplication.class);
        assertDoesNotThrow(() -> new UpdateRedefinedPorts(app));
    }

    @Test
    void commandName_matchesPublicConstant() {
        UpdateRedefinedPorts tool = new UpdateRedefinedPorts(mock(IRPApplication.class));
        assertEquals(UpdateRedefinedPorts.COMMAND, tool.commandName());
    }

    @Test
    void isUndoable_isTrue() {
        UpdateRedefinedPorts tool = new UpdateRedefinedPorts(mock(IRPApplication.class));
        assertTrue(tool.isUndoable());
    }

    @Test
    void isInteractive_isTrue() {
        UpdateRedefinedPorts tool = new UpdateRedefinedPorts(mock(IRPApplication.class));
        assertTrue(tool.isInteractive());
    }

    // ==================================================================
    // resolveTargets() — scope resolution (REQ-5)
    // ==================================================================

    @Test
    void resolveTargets_returnsEmpty_whenSelectionIsNull() throws Exception {
        IRPApplication app = mock(IRPApplication.class);
        when(app.getListOfSelectedElements()).thenReturn(null);

        List<IRPModelElement> targets = resolveTargets(new UpdateRedefinedPorts(app));

        assertTrue(targets.isEmpty());
    }

    @Test
    void resolveTargets_returnsEmpty_whenSelectionApiThrows() throws Exception {
        IRPApplication app = mock(IRPApplication.class);
        when(app.getListOfSelectedElements()).thenThrow(new RuntimeException("COM error"));

        List<IRPModelElement> targets = resolveTargets(new UpdateRedefinedPorts(app));

        assertTrue(targets.isEmpty());
    }

    @Test
    void resolveTargets_returnsEmpty_whenSelectionIsEmpty() throws Exception {
        IRPApplication app = mock(IRPApplication.class);
        doReturn(collectionOf()).when(app).getListOfSelectedElements();

        List<IRPModelElement> targets = resolveTargets(new UpdateRedefinedPorts(app));

        assertTrue(targets.isEmpty());
    }

    /** Case 1 (Javadoc §"Scope"): the selected element is itself a reference -> itself. */
    @Test
    void resolveTargets_includesDirectlySelectedReference() throws Exception {
        IRPClass reference = referenceClass("GUID-ref-direct");

        IRPApplication app = mock(IRPApplication.class);
        doReturn(collectionOf(reference)).when(app).getListOfSelectedElements();

        List<IRPModelElement> targets = resolveTargets(new UpdateRedefinedPorts(app));

        assertEquals(1, targets.size());
        assertTrue(targets.contains(reference));
    }

    /** Case 2: the selected element is a parent -> its derived references are added, non-references filtered out. */
    @Test
    void resolveTargets_expandsParentToDerivedReferences_filteringNonReferences() throws Exception {
        IRPClass derivedReference = referenceClass("GUID-derived-ref");

        IRPClass derivedNonReference = mock(IRPClass.class);
        when(derivedNonReference.getGUID()).thenReturn("GUID-derived-nonref");
        doReturn(collectionOf()).when(derivedNonReference).getGeneralizations(); // not a reference class

        IRPClass parent = mock(IRPClass.class);
        when(parent.getGUID()).thenReturn("GUID-parent");
        doReturn(collectionOf()).when(parent).getGeneralizations(); // parent itself is not a reference
        doReturn(collectionOf(derivedReference, derivedNonReference)).when(parent).getDerivedClassifiers();

        IRPApplication app = mock(IRPApplication.class);
        doReturn(collectionOf(parent)).when(app).getListOfSelectedElements();

        List<IRPModelElement> targets = resolveTargets(new UpdateRedefinedPorts(app));

        assertEquals(1, targets.size());
        assertTrue(targets.contains(derivedReference));
    }

    /** Dedup: the same reference reachable twice (selected directly AND via a parent) must appear once. */
    @Test
    void resolveTargets_deduplicatesByGuid() throws Exception {
        IRPClass reference = referenceClass("GUID-ref-dedup");

        IRPClass parent = mock(IRPClass.class);
        when(parent.getGUID()).thenReturn("GUID-parent-dedup");
        doReturn(collectionOf()).when(parent).getGeneralizations();
        doReturn(collectionOf(reference)).when(parent).getDerivedClassifiers();

        IRPApplication app = mock(IRPApplication.class);
        // Same reference selected directly AND its parent selected (which would also add it) in one shot.
        doReturn(collectionOf(reference, parent)).when(app).getListOfSelectedElements();

        List<IRPModelElement> targets = resolveTargets(new UpdateRedefinedPorts(app));

        assertEquals(1, targets.size());
        assertTrue(targets.contains(reference));
    }

    @Test
    void resolveTargets_ignoresElementThatIsNeitherReferenceNorClassifier() throws Exception {
        IRPModelElement plainElement = mock(IRPModelElement.class); // not IRPClassifier, not a reference

        IRPApplication app = mock(IRPApplication.class);
        doReturn(collectionOf(plainElement)).when(app).getListOfSelectedElements();

        List<IRPModelElement> targets = resolveTargets(new UpdateRedefinedPorts(app));

        assertTrue(targets.isEmpty());
    }

    @Test
    void resolveTargets_ignoresParentWhoseDerivedClassifiersLookupThrows() throws Exception {
        IRPClass parent = mock(IRPClass.class);
        when(parent.getGUID()).thenReturn("GUID-parent-throws");
        doReturn(collectionOf()).when(parent).getGeneralizations();
        when(parent.getDerivedClassifiers()).thenThrow(new RuntimeException("COM error"));

        IRPApplication app = mock(IRPApplication.class);
        doReturn(collectionOf(parent)).when(app).getListOfSelectedElements();

        List<IRPModelElement> targets = resolveTargets(new UpdateRedefinedPorts(app));

        assertTrue(targets.isEmpty());
    }
}
