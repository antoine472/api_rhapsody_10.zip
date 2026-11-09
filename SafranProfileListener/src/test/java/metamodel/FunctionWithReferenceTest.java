package metamodel;

import org.junit.jupiter.api.Test;

import com.telelogic.rhapsody.core.IRPApplication;
import com.telelogic.rhapsody.core.IRPClass;
import com.telelogic.rhapsody.core.IRPCollection;
import com.telelogic.rhapsody.core.IRPDiagram;
import com.telelogic.rhapsody.core.IRPGeneralization;
import com.telelogic.rhapsody.core.IRPGraphElement;
import com.telelogic.rhapsody.core.IRPModelElement;
import com.telelogic.rhapsody.core.RhapsodyRuntimeException;

import static metamodel.RhpTestMocks.collectionOf;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires purs (sans instance Rhapsody vivante) pour la partie
 * SYNCHRONE de {@link FunctionWithReference#processDroppedSelection(IRPApplication)}.
 *
 * <p><b>Pourquoi si peu de tests, et pourquoi seulement des "ne plante pas" ?</b>
 * Le vrai retrait graphique des ports hérités est toujours différé d'environ
 * 400ms sur le timer propre à {@code FunctionWithReference} (voir sa Javadoc de
 * classe) : ce n'est pas quelque chose qu'un test unitaire doit attendre (ce
 * serait lent et fragile). La seule partie testable ici, de façon fiable et
 * rapide, est la logique SYNCHRONE de filtrage/garde-fou qui s'exécute avant
 * la programmation du timer : quelle que soit la sélection graphique
 * courante, la méthode ne doit jamais planter le listener appelant. La
 * logique de masquage elle-même ({@code RedefinedPortsService.hideRedefinedPorts})
 * est déjà testée en détail dans {@code RedefinedPortsServiceTest}.</p>
 *
 * <p>Deux tests vérifient quand même le retrait différé de bout en bout, avec
 * {@code verify(mock, timeout(...))} de Mockito : le test attend que le timer
 * ait appelé le diagramme (environ 400 ms), sans {@code sleep} fixe.</p>
 */
class FunctionWithReferenceTest {

    /**
     * Garde-fou le plus basique : si {@code app} est {@code null} (l'appli
     * Rhapsody active n'a pas pu être retrouvée), la méthode doit se contenter
     * de ne rien faire, sans lever d'exception.
     */
    @Test
    void processDroppedSelection_doesNotThrow_onNullApp() { 
        assertDoesNotThrow(() -> FunctionWithReference.processDroppedSelection(null));
    }

    /**
     * Robustesse COM : si {@code app.getSelectedGraphElements()} lève une
     * exception (appli occupée, etc.), la méthode doit la rattraper et sortir
     * proprement plutôt que de remonter l'exception jusqu'au listener Rhapsody
     * (qui pourrait alors afficher une erreur à l'utilisateur pour rien).
     */
    @Test
    void processDroppedSelection_doesNotThrow_whenSelectionApiThrows() {
        IRPApplication app = mock(IRPApplication.class);
        when(app.getSelectedGraphElements()).thenThrow(new RuntimeException("COM error"));

        assertDoesNotThrow(() -> FunctionWithReference.processDroppedSelection(app));
    }

    /**
     * Cas où Rhapsody renvoie {@code null} au lieu d'une collection vide pour
     * la sélection graphique courante (aucun élément sélectionné). La méthode
     * doit gérer ce cas sans planter.
     */
    @Test
    void processDroppedSelection_doesNotThrow_onNullSelection() {
        IRPApplication app = mock(IRPApplication.class);
        when(app.getSelectedGraphElements()).thenReturn(null);

        assertDoesNotThrow(() -> FunctionWithReference.processDroppedSelection(app));
    }

    /**
     * Cas "rien n'est sélectionné" (collection vide, pas {@code null} cette
     * fois) : c'est le cas le plus fréquent en pratique (l'utilisateur ne
     * vient pas forcément de déposer un élément). La méthode doit ressortir
     * immédiatement sans erreur.
     */
    @Test
    void processDroppedSelection_doesNotThrow_onEmptySelection() {
        IRPApplication app = mock(IRPApplication.class);
        doReturn(collectionOf()).when(app).getSelectedGraphElements();

        assertDoesNotThrow(() -> FunctionWithReference.processDroppedSelection(app));
    }

    /**
     * Robustesse COM (bis) : la collection de sélection existe, mais la
     * convertir en liste Java ({@code toList()}) échoue. Là encore, la méthode
     * doit rattraper l'exception au lieu de la laisser remonter.
     */
    @Test
    void processDroppedSelection_doesNotThrow_whenToListThrows() {
        IRPApplication app = mock(IRPApplication.class);
        com.telelogic.rhapsody.core.IRPCollection selection = mock(com.telelogic.rhapsody.core.IRPCollection.class);
        when(selection.toList()).thenThrow(new RuntimeException("COM error"));
        when(app.getSelectedGraphElements()).thenReturn(selection);

        assertDoesNotThrow(() -> FunctionWithReference.processDroppedSelection(app));
    }

    /**
     * La sélection contient un élément graphique, mais son objet modèle n'est
     * PAS une classe de référence ({@code RedefinedPortsService.isReferenceClass}
     * renvoie {@code false} pour un simple {@code IRPModelElement}). Cet
     * élément doit donc être filtré (ignoré) : aucun retrait de port ne doit
     * être programmé pour lui, et surtout, la méthode ne doit pas planter en
     * l'examinant.
     */
    @Test
    void processDroppedSelection_filtersOutNonReferenceElements_withoutThrowing() {
        IRPApplication app = mock(IRPApplication.class);

        IRPModelElement notAReference = mock(IRPModelElement.class); // not an IRPClass -> isReferenceClass() = false
        IRPGraphElement graphElement = mock(IRPGraphElement.class);
        when(graphElement.getModelObject()).thenReturn(notAReference);

        doReturn(collectionOf(graphElement)).when(app).getSelectedGraphElements();

        assertDoesNotThrow(() -> FunctionWithReference.processDroppedSelection(app));
    }

    // ==================================================================
    // Retrait différé (timer ~400 ms, vérifié avec Mockito timeout)
    // ==================================================================

    /** Classe de référence (généralisation "References Function"). */
    private static IRPClass referenceClass() {
        IRPGeneralization gen = mock(IRPGeneralization.class);
        when(gen.getUserDefinedMetaClass()).thenReturn("References Function");
        IRPClass reference = mock(IRPClass.class);
        doReturn(collectionOf(gen)).when(reference).getGeneralizations();
        return reference;
    }

    /**
     * Noeud graphique de {@code model} sur son propre diagramme, avec un port
     * hérité et son port redéfini dessinés dessus.
     */
    private static IRPGraphElement droppedNode(IRPModelElement model, IRPDiagram diagram) {
        IRPGraphElement node = mock(IRPGraphElement.class);
        when(node.getModelObject()).thenReturn(model);
        when(node.getDiagram()).thenReturn(diagram);
        IRPModelElement inherited = mock(IRPModelElement.class);
        when(inherited.getUserDefinedMetaClass()).thenReturn("Flow Port");
        IRPModelElement redefined = mock(IRPModelElement.class);
        when(redefined.getUserDefinedMetaClass()).thenReturn("Flow Port");
        doReturn(collectionOf(inherited)).when(redefined).getRedefines();
        IRPGraphElement inheritedGraphic = mock(IRPGraphElement.class);
        when(inheritedGraphic.getModelObject()).thenReturn(inherited);
        when(inheritedGraphic.getGraphicalParent()).thenReturn(node);
        IRPGraphElement redefinedGraphic = mock(IRPGraphElement.class);
        when(redefinedGraphic.getModelObject()).thenReturn(redefined);
        when(redefinedGraphic.getGraphicalParent()).thenReturn(node);
        doReturn(collectionOf(inheritedGraphic, redefinedGraphic)).when(diagram).getGraphicalElements();
        return node;
    }

    /**
     * Multi-sélection après un drop : les ports hérités de chaque référence
     * déposée sont retirés, mais seulement après le délai (jamais pendant le
     * callback). Les éléments qui ne sont pas des références, pas graphiques
     * ou illisibles sont ignorés.
     */
    @Test
    void processDroppedSelection_hidesInheritedPortsOfEachDroppedReference_afterTheDelay() {
        IRPApplication app = mock(IRPApplication.class);
        IRPCollection removal = mock(IRPCollection.class);
        when(app.createNewCollection()).thenReturn(removal);

        IRPDiagram d1 = mock(IRPDiagram.class);
        IRPDiagram d2 = mock(IRPDiagram.class);
        IRPGraphElement ref1 = droppedNode(referenceClass(), d1);
        IRPGraphElement ref2 = droppedNode(referenceClass(), d2);
        IRPGraphElement notARef = mock(IRPGraphElement.class);
        when(notARef.getModelObject()).thenReturn(mock(IRPClass.class));
        IRPGraphElement unreadable = mock(IRPGraphElement.class);
        when(unreadable.getModelObject()).thenThrow(new RhapsodyRuntimeException("busy"));

        doReturn(collectionOf(notARef, "not a graphic", unreadable, ref1, ref2))
                .when(app).getSelectedGraphElements();

        FunctionWithReference.processDroppedSelection(app);

        verify(d1, never()).removeGraphElements(any()); // deferred, not inside the callback
        verify(d1, timeout(3000)).removeGraphElements(removal);
        verify(d2, timeout(3000)).removeGraphElements(removal);
        verify(notARef, never()).getDiagram();
    }

    /** Un échec sur une référence n'empêche pas le retrait sur les suivantes. */
    @Test
    void processDroppedSelection_continuesWithNextReference_whenOneHideFails() {
        IRPApplication app = mock(IRPApplication.class);
        IRPCollection removal = mock(IRPCollection.class);
        when(app.createNewCollection()).thenReturn(removal);

        IRPDiagram broken = mock(IRPDiagram.class);
        when(broken.getGraphicalElements()).thenThrow(new RhapsodyRuntimeException("busy"));
        IRPClass brokenReference = referenceClass();
        IRPGraphElement brokenNode = mock(IRPGraphElement.class);
        when(brokenNode.getModelObject()).thenReturn(brokenReference);
        when(brokenNode.getDiagram()).thenReturn(broken);
        IRPDiagram good = mock(IRPDiagram.class);
        IRPGraphElement goodNode = droppedNode(referenceClass(), good);

        doReturn(collectionOf(brokenNode, goodNode)).when(app).getSelectedGraphElements();

        FunctionWithReference.processDroppedSelection(app);

        verify(good, timeout(3000)).removeGraphElements(removal);
    }
}
