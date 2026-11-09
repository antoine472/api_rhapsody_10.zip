package metamodel;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.telelogic.rhapsody.core.IRPApplication;
import com.telelogic.rhapsody.core.IRPClass;
import com.telelogic.rhapsody.core.IRPClassifier;
import com.telelogic.rhapsody.core.IRPCollection;
import com.telelogic.rhapsody.core.IRPDiagram;
import com.telelogic.rhapsody.core.IRPGeneralization;
import com.telelogic.rhapsody.core.IRPGraphElement;
import com.telelogic.rhapsody.core.IRPModelElement;
import com.telelogic.rhapsody.core.IRPProject;
import com.telelogic.rhapsody.core.RhapsodyRuntimeException;

import static metamodel.RhpTestMocks.collectionOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires purs (sans instance Rhapsody vivante) pour {@link ReferencesFunction}.
 *
 * <p><b>Pourquoi ne pas attendre le vrai timer de 400ms ?</b> En production,
 * {@code handleCreated} met en file d'attente puis programme un
 * {@code java.util.Timer} qui exécutera le vrai traitement 400ms plus tard,
 * hors du callback Rhapsody. Attendre 400ms dans un test le rendrait lent et
 * fragile. Heureusement, la mise en file ({@code enqueue}) est SYNCHRONE :
 * elle se produit avant même de programmer le timer. On peut donc appeler
 * {@code handleCreated(...)} puis, nous-mêmes, {@code pumpPending(app)}
 * directement depuis le thread du test : c'est exactement le même code que
 * celui que le timer exécuterait, mais de façon déterministe et instantanée.</p>
 *
 * <p><b>Pourquoi toujours passer une {@link IRPApplication} non {@code null} ?</b>
 * Si on passait {@code null} à {@code handleCreated}, le code irait chercher
 * l'application active via {@code RhapsodyAppServer.getActiveRhapsodyApplication()},
 * un appel natif (JNI) qui échoue en dehors d'un vrai processus Rhapsody. En
 * fournissant toujours un mock d'application, on ne passe jamais par ce
 * chemin-là.</p>
 */
class ReferencesFunctionTest {

    private static final String REF_FUNCTION = "References Function";
    private static final String REF_LOGICAL_SYSTEM = "References Logical System";
    private static final int MAX_ATTEMPTS = 15;

    /**
     * La file d'attente {@code PENDING} est un champ {@code static} partagé par
     * TOUS les tests de cette classe (et même d'autres classes, dans la même
     * JVM). Si un test précédent a laissé une entrée dedans, elle pourrait
     * fausser le test suivant. On la vide donc avant ET après chaque test.
     */
    @BeforeEach
    void clearQueueBefore() {
        ReferencesFunction.clearPendingForTests();
    }

    @AfterEach
    void clearQueueAfter() {
        ReferencesFunction.clearPendingForTests();
    }

    // ==================================================================
    // isReferenceLink
    // ==================================================================

    /**
     * Un lien de généralisation ({@code IRPGeneralization}) dont la métaclasse
     * utilisateur vaut "References Function" doit être reconnu comme un "lien
     * de référence" géré par le mécanisme de redéfinition.
     */
    @Test
    void isReferenceLink_trueForReferencesFunction() {
        IRPGeneralization gen = mock(IRPGeneralization.class);
        when(gen.getUserDefinedMetaClass()).thenReturn(REF_FUNCTION);

        assertTrue(ReferencesFunction.isReferenceLink(gen));
    }

    /** Même vérification que ci-dessus, pour l'autre métaclasse gérée. */
    @Test
    void isReferenceLink_trueForReferencesLogicalSystem() {
        IRPGeneralization gen = mock(IRPGeneralization.class);
        when(gen.getUserDefinedMetaClass()).thenReturn(REF_LOGICAL_SYSTEM);

        assertTrue(ReferencesFunction.isReferenceLink(gen));
    }

    /**
     * Un élément qui n'est même pas une généralisation (ici un simple
     * {@code IRPModelElement}) ne peut évidemment pas être un lien de
     * référence : la méthode doit répondre {@code false}.
     */
    @Test
    void isReferenceLink_falseForNonGeneralizationElement() {
        IRPModelElement element = mock(IRPModelElement.class);
        assertFalse(ReferencesFunction.isReferenceLink(element));
    }

    /**
     * Robustesse : si l'appel COM {@code getUserDefinedMetaClass()} lève une
     * exception, la méthode doit la rattraper et répondre {@code false} plutôt
     * que de faire planter le listener appelant.
     */
    @Test
    void isReferenceLink_falseWhenApiThrows() {
        IRPGeneralization gen = mock(IRPGeneralization.class);
        when(gen.getUserDefinedMetaClass()).thenThrow(new RuntimeException("COM error"));

        assertFalse(ReferencesFunction.isReferenceLink(gen));
    }

    // ==================================================================
    // handleCreated / hasPending
    // ==================================================================

    /**
     * Quand un vrai lien de référence est créé, {@code handleCreated} doit
     * immédiatement (de façon synchrone) le mettre en file d'attente : juste
     * après l'appel, {@code hasPending()} doit passer de {@code false} à
     * {@code true}. C'est ce "avant/après" qui prouve que la mise en file ne
     * dépend pas du timer asynchrone.
     */
    @Test
    void handleCreated_enqueuesReferenceLink() {
        IRPGeneralization gen = mock(IRPGeneralization.class);
        when(gen.getUserDefinedMetaClass()).thenReturn(REF_FUNCTION);
        when(gen.getGUID()).thenReturn("GUID-enqueue");

        IRPApplication app = mock(IRPApplication.class);

        assertFalse(ReferencesFunction.hasPending());
        ReferencesFunction.handleCreated(gen, app);
        assertTrue(ReferencesFunction.hasPending());
    }

    /**
     * Si l'élément créé n'est PAS un lien de référence (ex. n'importe quel
     * autre {@code IRPModelElement}), {@code handleCreated} ne doit rien mettre
     * en file : la file doit rester vide.
     */
    @Test
    void handleCreated_ignoresNonReferenceElement() {
        IRPModelElement element = mock(IRPModelElement.class);
        IRPApplication app = mock(IRPApplication.class);

        ReferencesFunction.handleCreated(element, app);

        assertFalse(ReferencesFunction.hasPending());
    }

    // ==================================================================
    // pumpPending
    // ==================================================================

    /**
     * Garde-fou : appeler {@code pumpPending(null)} (par ex. si l'application
     * Rhapsody active n'a pas pu être retrouvée) ne doit RIEN faire — surtout
     * pas planter, ni vider la file par erreur. L'entrée doit rester en attente
     * pour être retraitée plus tard, avec une vraie application.
     */
    @Test
    void pumpPending_noopOnNullApp() {
        IRPGeneralization gen = mock(IRPGeneralization.class);
        when(gen.getUserDefinedMetaClass()).thenReturn(REF_FUNCTION);
        when(gen.getGUID()).thenReturn("GUID-nullapp");

        IRPApplication app = mock(IRPApplication.class);
        ReferencesFunction.handleCreated(gen, app);
        assertTrue(ReferencesFunction.hasPending());

        ReferencesFunction.pumpPending(null);

        assertTrue(ReferencesFunction.hasPending());
    }

    /**
     * Scénario "tout se passe bien" : le lien de référence est retrouvé dans le
     * projet actif via son GUID, et la redéfinition réussit du premier coup.
     *
     * Astuce de test : pour ne pas avoir à simuler tout {@code RedefinedPortsService}
     * ici, on choisit une classe dérivée qui N'EST PAS une {@code IRPClass}
     * ({@code mock(IRPClassifier.class)}) : {@code redefinePorts(...)} prend
     * alors sa branche triviale "rien à faire" et répond {@code true}
     * immédiatement. Ça suffit pour vérifier que la MÉCANIQUE de la file
     * (retirer l'entrée en cas de succès) fonctionne, sans dupliquer les tests
     * détaillés de {@code RedefinedPortsServiceTest}.
     *
     * Given : le lien est en file, et se résout bien via {@code findElementByGUID}.
     * When  : on appelle {@code pumpPending(app)}.
     * Then  : la file est vide ensuite, et la mutation a bien été encadrée par
     *         une transaction "undo" ({@code startUndoTransaction}/{@code endUndoTransaction}),
     *         ce qui la rend annulable par l'utilisateur avec Ctrl+Z.
     */
    @Test
    void pumpPending_removesEntry_onSuccess() {
        IRPGeneralization gen = mock(IRPGeneralization.class);
        when(gen.getUserDefinedMetaClass()).thenReturn(REF_FUNCTION);
        when(gen.getGUID()).thenReturn("GUID-success");

        // Derived class resolved but not an IRPClass -> RedefinedPortsService.redefinePorts(...)
        // takes the trivial "nothing to do" branch and returns true immediately.
        IRPClassifier derived = mock(IRPClassifier.class);
        when(gen.getDerivedClass()).thenReturn(derived);

        IRPApplication app = mock(IRPApplication.class);
        IRPProject project = mock(IRPProject.class);
        when(app.activeProject()).thenReturn(project);
        when(project.findElementByGUID("GUID-success")).thenReturn(gen);

        ReferencesFunction.handleCreated(gen, app);
        assertTrue(ReferencesFunction.hasPending());

        ReferencesFunction.pumpPending(app);

        assertFalse(ReferencesFunction.hasPending());
        verify(app).startUndoTransaction();
        verify(app).endUndoTransaction();
    }

    /**
     * Scénario "l'utilisateur a supprimé le lien entre-temps" : le GUID mis en
     * file ne correspond plus à rien dans le projet ({@code findElementByGUID}
     * renvoie {@code null}). Il n'y a alors plus rien à redéfinir : l'entrée
     * doit simplement être retirée de la file (pas d'erreur, pas de blocage).
     */
    @Test
    void pumpPending_removesEntry_whenGuidNoLongerResolves() {
        IRPGeneralization gen = mock(IRPGeneralization.class);
        when(gen.getUserDefinedMetaClass()).thenReturn(REF_LOGICAL_SYSTEM);
        when(gen.getGUID()).thenReturn("GUID-deleted");

        IRPApplication app = mock(IRPApplication.class);
        IRPProject project = mock(IRPProject.class);
        when(app.activeProject()).thenReturn(project);
        when(project.findElementByGUID("GUID-deleted")).thenReturn(null); // link removed from the model meanwhile

        ReferencesFunction.handleCreated(gen, app);
        assertTrue(ReferencesFunction.hasPending());

        ReferencesFunction.pumpPending(app);

        assertFalse(ReferencesFunction.hasPending());
    }

    /**
     * Scénario "ça ne se résoudra jamais" : la classe dérivée du lien ne
     * devient jamais disponible ({@code getDerivedClass()} renvoie toujours
     * {@code null}), donc chaque tentative échoue. On appelle {@code pumpPending}
     * exactement {@code MAX_ATTEMPTS} (15) fois, comme le ferait le vrai timer
     * qui se reprogramme tout seul tant qu'il reste des entrées. Après la
     * 15e tentative, le code de production doit ABANDONNER et retirer
     * l'entrée de la file (pour ne pas retenter indéfiniment un lien cassé).
     */
    @Test
    void pumpPending_givesUpAfterMaxAttempts() {
        IRPGeneralization gen = mock(IRPGeneralization.class);
        when(gen.getUserDefinedMetaClass()).thenReturn(REF_FUNCTION);
        when(gen.getGUID()).thenReturn("GUID-maxattempts");
        when(gen.getDerivedClass()).thenReturn(null); // "mother"/derived never resolves -> always retry

        IRPApplication app = mock(IRPApplication.class);
        IRPProject project = mock(IRPProject.class);
        when(app.activeProject()).thenReturn(project);
        when(project.findElementByGUID("GUID-maxattempts")).thenReturn(gen);

        ReferencesFunction.handleCreated(gen, app);
        assertTrue(ReferencesFunction.hasPending());

        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            ReferencesFunction.pumpPending(app);
        }

        assertFalse(ReferencesFunction.hasPending());
    }

    // ==================================================================
    // File d'attente : cas limites
    // ==================================================================

    /** Lien de référence : GUID {@code guid}, dérivée {@code derived}, mère {@code mother}. */
    private static IRPGeneralization referenceLink(String guid, IRPClassifier derived, IRPClassifier mother) {
        IRPGeneralization gen = mock(IRPGeneralization.class);
        when(gen.getUserDefinedMetaClass()).thenReturn(REF_FUNCTION);
        when(gen.getGUID()).thenReturn(guid);
        when(gen.getDerivedClass()).thenReturn(derived);
        when(gen.getBaseClass()).thenReturn(mother);
        return gen;
    }

    /** Application dont le projet actif retrouve {@code element} par {@code guid}. */
    private static IRPApplication appFinding(String guid, IRPModelElement element) {
        IRPApplication app = mock(IRPApplication.class);
        IRPProject project = mock(IRPProject.class);
        when(app.activeProject()).thenReturn(project);
        when(project.findElementByGUID(guid)).thenReturn(element);
        return app;
    }

    /** Un lien dont le GUID est illisible ne peut pas être retrouvé plus tard : il n'est pas mis en file. */
    @Test
    void handleCreated_ignoresLinkWithoutGuid() {
        IRPGeneralization gen = referenceLink(null, null, null);

        ReferencesFunction.handleCreated(gen, mock(IRPApplication.class));

        assertFalse(ReferencesFunction.hasPending());
    }

    /** Deux notifications pour le même lien ne donnent qu'une seule redéfinition. */
    @Test
    void handleCreated_sameLinkTwice_isProcessedOnce() {
        IRPGeneralization gen = referenceLink("GUID-twice", mock(IRPClassifier.class), null);
        IRPApplication app = appFinding("GUID-twice", gen);

        ReferencesFunction.handleCreated(gen, app);
        ReferencesFunction.handleCreated(gen, app);
        ReferencesFunction.pumpPending(app);

        assertFalse(ReferencesFunction.hasPending());
        verify(app.activeProject(), times(1)).findElementByGUID("GUID-twice");
    }

    /** Le GUID désigne maintenant autre chose qu'une généralisation : l'entrée est retirée, rien n'est fait. */
    @Test
    void pumpPending_removesEntry_whenGuidNoLongerDesignatesAGeneralization() {
        IRPGeneralization gen = referenceLink("GUID-other", mock(IRPClassifier.class), null);
        IRPClass somethingElse = mock(IRPClass.class);
        IRPApplication app = appFinding("GUID-other", somethingElse);

        ReferencesFunction.handleCreated(gen, app);
        ReferencesFunction.pumpPending(app);

        assertFalse(ReferencesFunction.hasPending());
        verify(app, never()).startUndoTransaction();
    }

    /**
     * La mère du lien n'est pas encore résolue : {@code redefinePorts} répond
     * false, l'entrée reste en file pour une nouvelle tentative.
     */
    @Test
    void pumpPending_keepsEntry_whenMotherNotResolvedYet() {
        IRPClass reference = mock(IRPClass.class);
        IRPGeneralization gen = referenceLink("GUID-nomother", reference, null);
        doReturn(collectionOf(gen)).when(reference).getGeneralizations();
        IRPApplication app = appFinding("GUID-nomother", gen);

        ReferencesFunction.handleCreated(gen, app);
        ReferencesFunction.pumpPending(app);

        assertTrue(ReferencesFunction.hasPending());
    }

    /**
     * Une erreur Rhapsody pendant la redéfinition (appli occupée) est
     * absorbée par la transaction : l'entrée reste en file, la transaction est
     * refermée.
     */
    @Test
    void pumpPending_keepsEntry_whenRedefinitionHitsARhapsodyError() {
        IRPClass reference = mock(IRPClass.class);
        when(reference.getGeneralizations()).thenThrow(new RhapsodyRuntimeException("busy"));
        IRPGeneralization gen = referenceLink("GUID-busy", reference, null);
        IRPApplication app = appFinding("GUID-busy", gen);

        ReferencesFunction.handleCreated(gen, app);
        ReferencesFunction.pumpPending(app);

        assertTrue(ReferencesFunction.hasPending());
        verify(app).startUndoTransaction();
        verify(app).endUndoTransaction();
    }

    /** Limite exacte : après MAX_ATTEMPTS - 1 échecs, le lien est encore en file. */
    @Test
    void pumpPending_stillRetries_justBeforeMaxAttempts() {
        IRPGeneralization gen = referenceLink("GUID-almost", null, null); // derived never resolves
        IRPApplication app = appFinding("GUID-almost", gen);

        ReferencesFunction.handleCreated(gen, app);
        for (int i = 0; i < MAX_ATTEMPTS - 1; i++) {
            ReferencesFunction.pumpPending(app);
        }
        assertTrue(ReferencesFunction.hasPending());

        ReferencesFunction.pumpPending(app);
        assertFalse(ReferencesFunction.hasPending());
    }

    /**
     * Garde de ré-entrance : un pump déclenché pendant un pump en cours (autre
     * timer, onSelectionChanged) ne retraite pas la même entrée.
     */
    @Test
    void pumpPending_isNotReentrant() {
        IRPClass reference = mock(IRPClass.class);
        IRPGeneralization gen = referenceLink("GUID-reentrant", reference, null);
        IRPApplication app = appFinding("GUID-reentrant", gen);
        when(reference.getGeneralizations()).thenAnswer(invocation -> {
            ReferencesFunction.pumpPending(app); // nested pump: must be a no-op
            return null;
        });

        ReferencesFunction.handleCreated(gen, app);
        ReferencesFunction.pumpPending(app);

        verify(app.activeProject(), times(1)).findElementByGUID("GUID-reentrant");
    }

    /**
     * Après une redéfinition complète, le nettoyage graphique est programmé
     * sur toutes les représentations existantes de la référence : le port
     * hérité dessiné dans le diagramme est retiré (hors callback, ~400 ms).
     */
    @Test
    void pumpPending_schedulesDiagramCleanup_afterCompleteRedefinition() {
        IRPClass mother = mock(IRPClass.class);
        when(mother.getGUID()).thenReturn("GUID-cleanup-mother");
        doReturn(collectionOf()).when(mother).getPorts();

        IRPClass reference = mock(IRPClass.class);
        when(reference.getGUID()).thenReturn("GUID-cleanup-ref");
        doReturn(collectionOf()).when(reference).getPorts();
        IRPGeneralization gen = referenceLink("GUID-cleanup", reference, mother);
        doReturn(collectionOf(gen)).when(reference).getGeneralizations();

        // The reference is already drawn in a diagram, with an inherited port and its redefinition.
        IRPDiagram diagram = mock(IRPDiagram.class);
        IRPGraphElement node = mock(IRPGraphElement.class);
        when(node.getModelObject()).thenReturn(reference);
        when(node.getDiagram()).thenReturn(diagram);
        IRPModelElement inheritedPort = mock(IRPModelElement.class);
        when(inheritedPort.getUserDefinedMetaClass()).thenReturn("Flow Port");
        IRPModelElement redefinedPort = mock(IRPModelElement.class);
        when(redefinedPort.getUserDefinedMetaClass()).thenReturn("Flow Port");
        doReturn(collectionOf(inheritedPort)).when(redefinedPort).getRedefines();
        IRPGraphElement inheritedGraphic = mock(IRPGraphElement.class);
        when(inheritedGraphic.getModelObject()).thenReturn(inheritedPort);
        when(inheritedGraphic.getGraphicalParent()).thenReturn(node);
        IRPGraphElement redefinedGraphic = mock(IRPGraphElement.class);
        when(redefinedGraphic.getModelObject()).thenReturn(redefinedPort);
        when(redefinedGraphic.getGraphicalParent()).thenReturn(node);
        doReturn(collectionOf(inheritedGraphic, redefinedGraphic)).when(diagram).getGraphicalElements();
        doReturn(collectionOf(node)).when(diagram).getCorrespondingGraphicElements(reference);
        doReturn(collectionOf(diagram)).when(reference).getReferences();

        IRPApplication app = appFinding("GUID-cleanup", gen);
        IRPCollection removal = mock(IRPCollection.class);
        when(app.createNewCollection()).thenReturn(removal);

        ReferencesFunction.handleCreated(gen, app);
        ReferencesFunction.pumpPending(app);

        assertFalse(ReferencesFunction.hasPending());
        verify(diagram, timeout(3000)).removeGraphElements(removal);
        verify(removal).addGraphicalItem(inheritedGraphic);
        verify(removal, never()).addGraphicalItem(redefinedGraphic);
    }
}
