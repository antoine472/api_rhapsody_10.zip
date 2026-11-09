package metamodel;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.telelogic.rhapsody.core.IRPApplication;
import com.telelogic.rhapsody.core.IRPClass;
import com.telelogic.rhapsody.core.IRPFlow;
import com.telelogic.rhapsody.core.IRPModelElement;
import com.telelogic.rhapsody.core.IRPPackage;
import com.telelogic.rhapsody.core.IRPProject;
import com.telelogic.rhapsody.core.IRPSysMLPort;
import com.telelogic.rhapsody.core.RhapsodyRuntimeException;

import static metamodel.RhpTestMocks.collectionOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires purs (sans instance Rhapsody vivante) pour {@link Flow}.
 *
 * <p>{@code FlowTest} reste le test d'intégration contre un vrai modèle. Ici on
 * ne teste que ce qui se pilote avec des mocks : l'égalité de deux flows (base
 * de la détection de doublons), le cas « pas de doublon » de
 * {@code afterAdded}, et la file de re-tentative du déplacement de flow
 * ({@code enqueueReowner} / {@code pumpReowners}).</p>
 *
 * <p>Non testé ici : le dialogue Swing affiché quand un doublon est trouvé, et
 * le déplacement immédiat de {@code afterAdded} dans les sous-classes (il passe
 * par {@code RhapsodyAppServer.getActiveRhapsodyApplication()}, un appel natif
 * qui n'existe qu'à l'intérieur de Rhapsody).</p>
 */
class FlowUnitTest {

    /** La file des déplacements est statique : on la vide avant et après chaque test. */
    @BeforeEach
    void clearQueueBefore() {
        Flow.clearPendingReownersForTests();
    }

    @AfterEach
    void clearQueueAfter() {
        Flow.clearPendingReownersForTests();
    }

    private static IRPFlow flowBetween(IRPModelElement end1, IRPModelElement end2) {
        IRPFlow f = mock(IRPFlow.class);
        when(f.getEnd1()).thenReturn(end1);
        when(f.getEnd2()).thenReturn(end2);
        return f;
    }

    // ==================================================================
    // equals / hashCode (détection de doublons)
    // ==================================================================

    /** Deux flows entre les mêmes ports, dans le même sens, sont des doublons. */
    @Test
    void equals_trueForSameSourceAndTargetPorts() {
        IRPSysMLPort a = mock(IRPSysMLPort.class);
        IRPSysMLPort b = mock(IRPSysMLPort.class);
        Flow f1 = new Flow(flowBetween(a, b));
        Flow f2 = new Flow(flowBetween(a, b));

        assertEquals(f1, f2);
        assertEquals(f1.hashCode(), f2.hashCode());
    }

    /** Le sens compte : A vers B n'est pas un doublon de B vers A. */
    @Test
    void equals_falseForReversedDirection() {
        IRPSysMLPort a = mock(IRPSysMLPort.class);
        IRPSysMLPort b = mock(IRPSysMLPort.class);

        assertNotEquals(new Flow(flowBetween(a, b)), new Flow(flowBetween(b, a)));
    }

    /** Hors ports (ex. flow entre deux classes), deux flows ne sont jamais des doublons. */
    @Test
    void equals_falseWhenAnEndIsNotAPort() {
        IRPSysMLPort a = mock(IRPSysMLPort.class);
        IRPClass block = mock(IRPClass.class);
        Flow f1 = new Flow(flowBetween(a, block));
        Flow f2 = new Flow(flowBetween(a, block));

        assertNotEquals(f1, f2);
        assertEquals(0, f1.hashCode());
    }

    /** Extrémités illisibles (erreur COM) : pas de doublon, pas d'exception. */
    @Test
    void equals_falseWhenEndsCannotBeRead() {
        IRPSysMLPort a = mock(IRPSysMLPort.class);
        IRPFlow unreadable = mock(IRPFlow.class);
        when(unreadable.getEnd1()).thenThrow(new RhapsodyRuntimeException("busy"));
        when(unreadable.getEnd2()).thenReturn(a);

        assertNotEquals(new Flow(flowBetween(a, a)), new Flow(unreadable));
        assertEquals(0, new Flow(unreadable).hashCode());
    }

    @Test
    void equals_trueForSameInstance_falseForOtherTypes() {
        Flow f = new Flow(flowBetween(mock(IRPClass.class), mock(IRPClass.class)));

        assertEquals(f, f);
        assertNotEquals(f, "not a flow");
        assertNotEquals(f, new ModelElement(mock(IRPFlow.class)));
    }

    // ==================================================================
    // afterAdded (Flow de base : pas de déplacement)
    // ==================================================================

    /**
     * Les autres flows du port source ne sont pas des doublons (autre cible) :
     * le nouveau flow est laissé tel quel, sans dialogue ni déplacement.
     */
    @Test
    void afterAdded_leavesFlowAlone_whenNoDuplicateExists() {
        IRPSysMLPort source = mock(IRPSysMLPort.class);
        IRPSysMLPort target = mock(IRPSysMLPort.class);
        IRPSysMLPort otherTarget = mock(IRPSysMLPort.class);
        IRPFlow created = flowBetween(source, target);
        IRPFlow toAnotherPort = flowBetween(source, otherTarget);
        doReturn(collectionOf(created, toAnotherPort, mock(IRPClass.class))).when(source).getReferences();

        new Flow(created).afterAdded();

        verify(source).getReferences();
        verify(created, never()).deleteFromProject();
        verify(created, never()).setOwner(any());
    }

    /** La recherche de doublons ne concerne que les flows entre deux ports. */
    @Test
    void afterAdded_skipsDuplicateCheck_whenEndsAreNotBothPorts() {
        IRPSysMLPort source = mock(IRPSysMLPort.class);
        IRPClass block = mock(IRPClass.class);
        IRPFlow created = flowBetween(source, block);

        new Flow(created).afterAdded();

        verify(source, never()).getReferences();
        verify(created, never()).deleteFromProject();
    }

    /** Seules les sous-classes de flow Safran demandent le déplacement sous le propriétaire du diagramme. */
    @Test
    void needsDiagramReowner_onlyForSafranFlowSubclasses() {
        IRPFlow f = mock(IRPFlow.class);

        assertFalse(new Flow(f).needsDiagramReowner());
        assertTrue(new FunctionalFlow(f).needsDiagramReowner());
        assertTrue(new LogicalFlow(f).needsDiagramReowner());
        assertTrue(new OperationalFlow(f).needsDiagramReowner());
        assertTrue(new TechnicalConnection(f).needsDiagramReowner());
        assertTrue(new GenericFlow(f).needsDiagramReowner());
    }

    // ==================================================================
    // File des déplacements : enqueueReowner / pumpReowners
    // ==================================================================

    private IRPApplication app;
    private IRPProject project;
    private IRPFlow flow;
    private IRPPackage oldOwner;
    private IRPPackage newOwner;

    /** Un flow "F" sous {@code oldOwner}, à déplacer sous {@code newOwner}, tous deux retrouvés par GUID. */
    private void givenFlowToMove() {
        app = mock(IRPApplication.class);
        project = mock(IRPProject.class);
        when(app.activeProject()).thenReturn(project);
        flow = mock(IRPFlow.class);
        when(flow.getName()).thenReturn("F");
        when(flow.getMetaClass()).thenReturn("Flow");
        oldOwner = mock(IRPPackage.class);
        newOwner = mock(IRPPackage.class);
        when(project.findElementByGUID("GUID-flow")).thenReturn(flow);
        when(project.findElementByGUID("GUID-owner")).thenReturn(newOwner);
    }

    @Test
    void enqueueReowner_ignoresMissingGuids() {
        Flow.enqueueReowner(null, "GUID-owner");
        Flow.enqueueReowner("GUID-flow", null);

        assertFalse(Flow.hasPendingReowners());
    }

    @Test
    void pumpReowners_doesNothing_whenAppIsNull() {
        Flow.enqueueReowner("GUID-flow", "GUID-owner");

        Flow.pumpReowners(null);

        assertTrue(Flow.hasPendingReowners());
    }

    /** Cas nominal : déplacement dans une transaction undo, puis la tâche est retirée. */
    @Test
    void pumpReowners_movesFlowInsideUndoTransaction_andClearsJob() {
        givenFlowToMove();
        when(flow.getOwner()).thenReturn(oldOwner, newOwner);
        Flow.enqueueReowner("GUID-flow", "GUID-owner", "Diagram");

        Flow.pumpReowners(app);

        assertFalse(Flow.hasPendingReowners());
        InOrder order = inOrder(app, flow);
        order.verify(app).startUndoTransaction();
        order.verify(flow).setOwner(newOwner);
        order.verify(app).endUndoTransaction();
        verify(flow, never()).setName(anyString());
    }

    /** Le flow a été supprimé entre-temps : la tâche est abandonnée. */
    @Test
    void pumpReowners_dropsJob_whenFlowWasDeleted() {
        givenFlowToMove();
        when(project.findElementByGUID("GUID-flow")).thenReturn(null);
        Flow.enqueueReowner("GUID-flow", "GUID-owner");

        Flow.pumpReowners(app);

        assertFalse(Flow.hasPendingReowners());
        verify(flow, never()).setOwner(any());
    }

    /** Le propriétaire cible a été supprimé entre-temps : la tâche est abandonnée. */
    @Test
    void pumpReowners_dropsJob_whenTargetOwnerWasDeleted() {
        givenFlowToMove();
        when(project.findElementByGUID("GUID-owner")).thenReturn(null);
        Flow.enqueueReowner("GUID-flow", "GUID-owner");

        Flow.pumpReowners(app);

        assertFalse(Flow.hasPendingReowners());
        verify(flow, never()).setOwner(any());
    }

    /** Déjà au bon endroit : rien à faire. */
    @Test
    void pumpReowners_dropsJob_whenFlowIsAlreadyUnderTargetOwner() {
        givenFlowToMove();
        when(flow.getOwner()).thenReturn(newOwner);
        Flow.enqueueReowner("GUID-flow", "GUID-owner");

        Flow.pumpReowners(app);

        assertFalse(Flow.hasPendingReowners());
        verify(flow, never()).setOwner(any());
    }

    /**
     * Collision de nom sous le nouveau propriétaire : le flow est renommé
     * "F__moved_k" avec le premier k libre, AVANT d'être déplacé.
     */
    @Test
    void pumpReowners_renamesFlowToFirstFreeName_beforeMoving() {
        givenFlowToMove();
        when(flow.getOwner()).thenReturn(oldOwner, newOwner);
        when(newOwner.findNestedElement("F", "Flow")).thenReturn(mock(IRPFlow.class));
        when(newOwner.findNestedElement("F__moved_1", "Flow")).thenReturn(mock(IRPFlow.class));
        Flow.enqueueReowner("GUID-flow", "GUID-owner");

        Flow.pumpReowners(app);

        InOrder order = inOrder(flow);
        order.verify(flow).setName("F__moved_2");
        order.verify(flow).setOwner(newOwner);
        assertFalse(Flow.hasPendingReowners());
    }

    /** La collision est aussi détectée avec un élément d'une autre métaclasse portant le même nom. */
    @Test
    void pumpReowners_detectsNameCollisionWithAnotherMetaclass() {
        givenFlowToMove();
        when(flow.getOwner()).thenReturn(oldOwner, newOwner);
        IRPClass sameName = mock(IRPClass.class);
        when(sameName.getName()).thenReturn("F");
        doReturn(collectionOf(flow, sameName)).when(newOwner).getNestedElements();
        Flow.enqueueReowner("GUID-flow", "GUID-owner");

        Flow.pumpReowners(app);

        verify(flow).setName("F__moved_1");
        verify(flow).setOwner(newOwner);
    }

    /**
     * Déplacement refusé (élément verrouillé) : la tâche reste en file, avec
     * un délai avant la prochaine tentative (un pump immédiat ne retente pas).
     */
    @Test
    void pumpReowners_keepsJobAndWaits_whenMoveFails() {
        givenFlowToMove();
        when(flow.getOwner()).thenReturn(oldOwner);
        doThrow(new RhapsodyRuntimeException("locked")).when(flow).setOwner(newOwner);
        Flow.enqueueReowner("GUID-flow", "GUID-owner");

        Flow.pumpReowners(app);
        Flow.pumpReowners(app);

        assertTrue(Flow.hasPendingReowners());
        verify(flow, times(1)).setOwner(newOwner);
        verify(app).endUndoTransaction();
    }

    /** Déplacement accepté mais propriétaire pas encore à jour : la tâche reste en file. */
    @Test
    void pumpReowners_keepsJob_whenOwnerIsNotStableAfterMove() {
        givenFlowToMove();
        when(flow.getOwner()).thenReturn(oldOwner);
        Flow.enqueueReowner("GUID-flow", "GUID-owner");

        Flow.pumpReowners(app);

        assertTrue(Flow.hasPendingReowners());
        verify(flow).setOwner(newOwner);
    }

    /** Pas de projet actif : la tâche est gardée pour plus tard. */
    @Test
    void pumpReowners_keepsJob_whenNoActiveProject() {
        givenFlowToMove();
        when(app.activeProject()).thenReturn(null);
        Flow.enqueueReowner("GUID-flow", "GUID-owner");

        Flow.pumpReowners(app);

        assertTrue(Flow.hasPendingReowners());
        verify(flow, never()).setOwner(any());
    }

    /**
     * Si Rhapsody signale une erreur à l'ouverture de la transaction, elle
     * n'est pas considérée comme ouverte : le déplacement a lieu, mais sans
     * {@code endUndoTransaction}.
     */
    @Test
    void pumpReowners_doesNotEndTransaction_whenStartReportsAnError() {
        givenFlowToMove();
        when(flow.getOwner()).thenReturn(oldOwner, newOwner);
        when(app.errorMessage()).thenReturn("transaction refused");
        Flow.enqueueReowner("GUID-flow", "GUID-owner");

        Flow.pumpReowners(app);

        verify(flow).setOwner(newOwner);
        verify(app, never()).endUndoTransaction();
        assertFalse(Flow.hasPendingReowners());
    }

    /** Un nouveau propriétaire cible remplace l'ancien pour le même flow. */
    @Test
    void enqueueReowner_newTargetOwnerReplacesThePreviousOne() {
        givenFlowToMove();
        IRPPackage firstTarget = mock(IRPPackage.class);
        when(project.findElementByGUID("GUID-first")).thenReturn(firstTarget);
        when(flow.getOwner()).thenReturn(oldOwner, newOwner);
        Flow.enqueueReowner("GUID-flow", "GUID-first");
        Flow.enqueueReowner("GUID-flow", "GUID-owner");

        Flow.pumpReowners(app);

        verify(flow).setOwner(newOwner);
        verify(flow, never()).setOwner(firstTarget);
    }

    /** Le même déplacement demandé deux fois n'est fait qu'une fois. */
    @Test
    void enqueueReowner_sameRequestTwice_isProcessedOnce() {
        givenFlowToMove();
        when(flow.getOwner()).thenReturn(oldOwner, newOwner);
        Flow.enqueueReowner("GUID-flow", "GUID-owner");
        Flow.enqueueReowner("GUID-flow", "GUID-owner");

        Flow.pumpReowners(app);

        verify(project, times(1)).findElementByGUID("GUID-flow");
        verify(flow, times(1)).setOwner(newOwner);
    }
}
