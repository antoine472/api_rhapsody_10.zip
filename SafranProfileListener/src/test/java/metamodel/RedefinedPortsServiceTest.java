package metamodel;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.telelogic.rhapsody.core.IRPApplication;
import com.telelogic.rhapsody.core.IRPClass;
import com.telelogic.rhapsody.core.IRPClassifier;
import com.telelogic.rhapsody.core.IRPCollection;
import com.telelogic.rhapsody.core.IRPDiagram;
import com.telelogic.rhapsody.core.IRPGeneralization;
import com.telelogic.rhapsody.core.IRPFlow;
import com.telelogic.rhapsody.core.IRPGraphEdge;
import com.telelogic.rhapsody.core.IRPGraphElement;
import com.telelogic.rhapsody.core.IRPGraphNode;
import com.telelogic.rhapsody.core.IRPGraphicalProperty;
import com.telelogic.rhapsody.core.IRPModelElement;
import com.telelogic.rhapsody.core.IRPPort;
import com.telelogic.rhapsody.core.IRPStereotype;
import com.telelogic.rhapsody.core.IRPSysMLPort;
import com.telelogic.rhapsody.core.RhapsodyRuntimeException;

import static metamodel.RhpTestMocks.collectionOf;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires purs (sans instance Rhapsody vivante) pour {@link RedefinedPortsService}.
 *
 * <p><b>Pourquoi des mocks et pas un vrai modèle Rhapsody ?</b> Tous les types de
 * l'API Rhapsody utilisés ici ({@code IRPClass}, {@code IRPGeneralization},
 * {@code IRPSysMLPort}, ...) sont de simples interfaces Java. On peut donc créer
 * de "fausses" implémentations avec Mockito ({@code mock(...)}) et leur dire quoi
 * répondre ({@code when(...).thenReturn(...)}), sans avoir besoin de lancer
 * Rhapsody. Ces tests s'exécutent donc en quelques millisecondes et ne dépendent
 * d'aucune installation. Ils complètent {@code FlowTest}/{@code RhapsodyBaseTest},
 * qui restent des tests d'intégration contre un vrai fichier {@code .rpyx}.</p>
 *
 * <p><b>Pourquoi {@code doReturn(...).when(mock).methode()} au lieu de
 * {@code when(mock.methode()).thenReturn(...)} ?</b> {@link RhpTestMocks#collectionOf}
 * crée elle-même un mock et le configure. Si on l'appelle comme argument de
 * {@code .thenReturn(...)}, Java l'exécute AVANT que le {@code .thenReturn(...)}
 * externe ne soit posé, ce qui perturbe Mockito ("stubbing non terminé"). La forme
 * {@code doReturn(valeur).when(mock).methode()} évalue {@code valeur} en premier,
 * donc elle est sûre dans ce cas précis.</p>
 */
class RedefinedPortsServiceTest {

    private static final String REF_FUNCTION = "References Function";
    private static final String REF_LOGICAL_SYSTEM = "References Logical System";

    // ==================================================================
    // isReferenceClass
    // ==================================================================

    /**
     * Cas nominal : une classe qui porte une généralisation de métaclasse
     * "References Function" doit être reconnue comme une "classe de référence"
     * (ex. {@code FunctionWithReference}).
     *
     * Given : une classe avec une généralisation dont la métaclasse utilisateur
     *         vaut "References Function".
     * When  : on appelle {@code isReferenceClass(element)}.
     * Then  : le résultat doit être {@code true}.
     */
    @Test
    void isReferenceClass_trueForReferencesFunctionGeneralization() {
        IRPGeneralization gen = mock(IRPGeneralization.class);
        when(gen.getUserDefinedMetaClass()).thenReturn(REF_FUNCTION);

        IRPClass element = mock(IRPClass.class);
        doReturn(collectionOf(gen)).when(element).getGeneralizations();

        assertTrue(RedefinedPortsService.isReferenceClass(element));
    }

    /**
     * Même test que ci-dessus, mais pour l'autre métaclasse gérée :
     * "References Logical System" (ex. {@code LogicalSystemReference}).
     */
    @Test
    void isReferenceClass_trueForReferencesLogicalSystemGeneralization() {
        IRPGeneralization gen = mock(IRPGeneralization.class);
        when(gen.getUserDefinedMetaClass()).thenReturn(REF_LOGICAL_SYSTEM);

        IRPClass element = mock(IRPClass.class);
        doReturn(collectionOf(gen)).when(element).getGeneralizations();

        assertTrue(RedefinedPortsService.isReferenceClass(element));
    }

    /**
     * Garde-fou de type : la méthode ne fait un {@code instanceof IRPClass} qu'au
     * début. Si on lui donne un élément qui n'est pas une classe (ici un simple
     * {@code IRPModelElement}), elle doit répondre {@code false} immédiatement,
     * sans lever d'exception.
     */
    @Test
    void isReferenceClass_falseForNonClassElement() {
        IRPModelElement element = mock(IRPModelElement.class);
        assertFalse(RedefinedPortsService.isReferenceClass(element));
    }

    /**
     * Une classe peut avoir des généralisations "normales" (héritage classique)
     * qui n'ont rien à voir avec le mécanisme de référence Safran. Ici la seule
     * généralisation présente a une métaclasse quelconque ("Some Other Link") :
     * la classe ne doit donc PAS être considérée comme une classe de référence.
     */
    @Test
    void isReferenceClass_falseWhenGeneralizationsDoNotMatch() {
        IRPGeneralization gen = mock(IRPGeneralization.class);
        when(gen.getUserDefinedMetaClass()).thenReturn("Some Other Link");

        IRPClass element = mock(IRPClass.class);
        doReturn(collectionOf(gen)).when(element).getGeneralizations();

        assertFalse(RedefinedPortsService.isReferenceClass(element));
    }

    /**
     * Robustesse face aux erreurs COM : si l'appel Rhapsody
     * {@code getGeneralizations()} lève une exception (ex. objet du modèle
     * momentanément indisponible), la méthode ne doit pas planter le listener :
     * elle doit rattraper l'exception et répondre {@code false}.
     */
    @Test
    void isReferenceClass_falseWhenApiThrows() {
        IRPClass element = mock(IRPClass.class);
        when(element.getGeneralizations()).thenThrow(new RuntimeException("COM error"));

        assertFalse(RedefinedPortsService.isReferenceClass(element));
    }

    // ==================================================================
    // redefinePorts
    // ==================================================================

    /**
     * Cas trivial "rien à faire" : {@code redefinePorts} ne s'applique qu'aux
     * classes. Si on lui passe un élément qui n'est pas une {@code IRPClass},
     * la méthode doit retourner {@code true} tout de suite (succès "vide"), et
     * ne doit surtout PAS dire "false" (ce qui déclencherait une re-tentative
     * infinie côté appelant pour un élément qui ne sera jamais une classe).
     */
    @Test
    void redefinePorts_returnsTrueForNonClassElement() {
        IRPModelElement element = mock(IRPModelElement.class);
        assertTrue(RedefinedPortsService.redefinePorts(element));
    }

    /**
     * Cas "re-tentable" : juste après la création du lien de généralisation, la
     * classe mère (l'extrémité "base") peut ne pas encore être résolue côté COM
     * (elle est {@code null}). Dans ce cas précis, {@code redefinePorts} doit
     * répondre {@code false} pour dire à l'appelant "réessaie plus tard" — ce
     * n'est PAS une erreur définitive.
     */
    @Test
    void redefinePorts_returnsFalseWhenMotherNotResolvedYet() {
        IRPGeneralization gen = mock(IRPGeneralization.class);
        when(gen.getUserDefinedMetaClass()).thenReturn(REF_FUNCTION);
        when(gen.getBaseClass()).thenReturn(null); // extremity not resolved yet

        IRPClass reference = mock(IRPClass.class);
        doReturn(collectionOf(gen)).when(reference).getGeneralizations();

        assertFalse(RedefinedPortsService.redefinePorts(reference));
    }

    /**
     * Cas "structurel, ne pas réessayer" : la classe mère EST résolue (non
     * {@code null}), mais ce n'est pas une {@code IRPClass} (par ex. une autre
     * sorte de classifieur). Ça ne changera jamais en réessayant : la méthode
     * doit donc répondre {@code true} (pas d'erreur à signaler, mais rien à
     * mirorer non plus).
     */
    @Test
    void redefinePorts_returnsTrueWhenMotherIsNotAClass() {
        // Base class resolved, but not an IRPClass: structural case, no retry.
        IRPClassifier motherNotAClass = mock(IRPClassifier.class);
        when(motherNotAClass.getName()).thenReturn("NotAClass");

        IRPGeneralization gen = mock(IRPGeneralization.class);
        when(gen.getUserDefinedMetaClass()).thenReturn(REF_FUNCTION);
        when(gen.getBaseClass()).thenReturn(motherNotAClass);

        IRPClass reference = mock(IRPClass.class);
        doReturn(collectionOf(gen)).when(reference).getGeneralizations();

        assertTrue(RedefinedPortsService.redefinePorts(reference));
    }

    /**
     * Le cœur du "Besoin A" : la classe mère a deux ports (P1 en entrée, P2 en
     * sortie) et la référence n'a encore AUCUN port redéfini.
     *
     * Given : mother.getPorts() = [P1, P2] ; reference.getPorts() = [] (vide).
     * When  : on appelle {@code redefinePorts(reference)}.
     * Then  :
     *   - le résultat est {@code true} (tout s'est bien passé) ;
     *   - un nouveau port est créé dans la référence pour CHAQUE port de la
     *     mère (via {@code addNewAggr("Flow Port", nomDuPort)}) ;
     *   - chaque port créé récupère le même type, la même direction, et un
     *     lien "addRedefines" vers le port d'origine (c'est ce qui matérialise
     *     la redéfinition dans le modèle Rhapsody).
     *
     * Remarque : on vérifie avec {@code atLeastOnce()} et pas {@code times(1)}
     * car le code de production applique ces propriétés deux fois de suite par
     * construction (une fois à la création du port, une fois juste après) — ce
     * doublon est voulu et sans effet de bord, donc pas la peine d'être strict
     * sur le nombre d'appels ici.
     */
    @Test
    void redefinePorts_createsAndMirrorsPortsFromMother() {
        IRPClass mother = mock(IRPClass.class);
        when(mother.getGUID()).thenReturn("GUID-mother");

        IRPClass reference = mock(IRPClass.class);
        when(reference.getGUID()).thenReturn("GUID-reference");
        when(reference.getName()).thenReturn("MyReference");
        doReturn(collectionOf()).when(reference).getPorts(); // no existing children -> nothing to prune

        IRPGeneralization gen = mock(IRPGeneralization.class);
        when(gen.getUserDefinedMetaClass()).thenReturn(REF_FUNCTION);
        when(gen.getBaseClass()).thenReturn(mother);
        doReturn(collectionOf(gen)).when(reference).getGeneralizations();

        IRPClassifier type1 = mock(IRPClassifier.class);
        IRPSysMLPort port1 = mock(IRPSysMLPort.class);
        when(port1.getName()).thenReturn("P1");
        when(port1.getGUID()).thenReturn("GUID-p1");
        when(port1.getType()).thenReturn(type1);
        when(port1.getPortDirection()).thenReturn("in");
        doReturn(collectionOf()).when(port1).getStereotypes();

        IRPClassifier type2 = mock(IRPClassifier.class);
        IRPSysMLPort port2 = mock(IRPSysMLPort.class);
        when(port2.getName()).thenReturn("P2");
        when(port2.getGUID()).thenReturn("GUID-p2");
        when(port2.getType()).thenReturn(type2);
        when(port2.getPortDirection()).thenReturn("out");
        doReturn(collectionOf()).when(port2).getStereotypes();

        doReturn(collectionOf(port1, port2)).when(mother).getPorts();

        // No redefined port exists yet in the reference -> must be created.
        when(reference.findNestedElement("P1", "Flow Port")).thenReturn(null);
        when(reference.findNestedElement("P2", "Flow Port")).thenReturn(null);

        IRPSysMLPort createdPort1 = mock(IRPSysMLPort.class);
        when(createdPort1.getGUID()).thenReturn("GUID-created-p1");
        when(reference.addNewAggr("Flow Port", "P1")).thenReturn(createdPort1);

        IRPSysMLPort createdPort2 = mock(IRPSysMLPort.class);
        when(createdPort2.getGUID()).thenReturn("GUID-created-p2");
        when(reference.addNewAggr("Flow Port", "P2")).thenReturn(createdPort2);

        boolean result = RedefinedPortsService.redefinePorts(reference);

        assertTrue(result);
        verify(reference).addNewAggr("Flow Port", "P1");
        verify(reference).addNewAggr("Flow Port", "P2");
        verify(createdPort1, atLeastOnce()).setType(type1);
        verify(createdPort1, atLeastOnce()).setPortDirection("in");
        verify(createdPort1, atLeastOnce()).addRedefines(port1);
        verify(createdPort2, atLeastOnce()).setType(type2);
        verify(createdPort2, atLeastOnce()).setPortDirection("out");
        verify(createdPort2, atLeastOnce()).addRedefines(port2);
    }

    /**
     * Idempotence : si on appelle {@code redefinePorts} une deuxième fois (par
     * ex. l'utilisateur retouche le modèle), et que le port redéfini existe déjà
     * (trouvé via {@code findNestedElement}), la méthode ne doit PAS en créer un
     * doublon. Elle doit juste réutiliser le port existant et rafraîchir ses
     * propriétés (type, direction, redéfinition).
     *
     * Given : la référence a déjà un port "P1" qui redéfinit correctement le
     *         port "P1" de la mère.
     * When  : on rappelle {@code redefinePorts(reference)}.
     * Then  : {@code addNewAggr(...)} n'est JAMAIS appelé, et le port existant
     *         n'est pas supprimé.
     */
    @Test
    void redefinePorts_isIdempotent_reusesExistingRedefinedPortInsteadOfCreating() {
        IRPClass mother = mock(IRPClass.class);
        when(mother.getGUID()).thenReturn("GUID-mother-2");

        IRPSysMLPort motherPort = mock(IRPSysMLPort.class);
        when(motherPort.getName()).thenReturn("P1");
        when(motherPort.getGUID()).thenReturn("GUID-motherport");
        when(motherPort.getOwner()).thenReturn(mother);
        doReturn(collectionOf(motherPort)).when(mother).getPorts();

        // Redefined port already created by a previous call -> valid mirror.
        IRPSysMLPort existingChildPort = mock(IRPSysMLPort.class);
        when(existingChildPort.getName()).thenReturn("P1");
        when(existingChildPort.getGUID()).thenReturn("GUID-existing");
        doReturn(collectionOf(motherPort)).when(existingChildPort).getRedefines();
        doReturn(collectionOf()).when(existingChildPort).getStereotypes();

        IRPClass reference = mock(IRPClass.class);
        when(reference.getGUID()).thenReturn("GUID-reference-2");
        when(reference.getName()).thenReturn("MyReference2");
        doReturn(collectionOf(existingChildPort)).when(reference).getPorts();
        when(reference.findNestedElement("P1", "Flow Port")).thenReturn(existingChildPort);

        IRPGeneralization gen = mock(IRPGeneralization.class);
        when(gen.getUserDefinedMetaClass()).thenReturn(REF_FUNCTION);
        when(gen.getBaseClass()).thenReturn(mother);
        doReturn(collectionOf(gen)).when(reference).getGeneralizations();

        boolean result = RedefinedPortsService.redefinePorts(reference);

        assertTrue(result);
        verify(reference, never()).addNewAggr(anyString(), anyString());
        verify(existingChildPort, never()).deleteFromProject();
        verify(existingChildPort, atLeastOnce()).addRedefines(motherPort);
    }

    /**
     * Nettoyage ("pruning") des ports qui ne devraient plus être là. Avant de
     * (re)créer les redéfinitions valides, {@code redefinePorts} passe en revue
     * les ports déjà présents dans la référence et en supprime deux catégories :
     * <ul>
     *   <li>les ports "en trop" ajoutés manuellement, sans aucune redéfinition
     *       ({@code childB}) ;</li>
     *   <li>les redéfinitions "orphelines", dont la cible n'appartient plus à
     *       la classe mère actuelle — par ex. après un changement de mère
     *       ({@code childC}, qui redéfinit un port d'une AUTRE classe).</li>
     * </ul>
     * Un port qui redéfinit correctement un port de la mère actuelle
     * ({@code childA}) doit lui, être conservé (pas supprimé), et réutilisé
     * pour le port "portA" au lieu d'en créer un nouveau.
     *
     * Given : reference.getPorts() = [childA (valide), childB (en trop),
     *         childC (orpheline)] ; mother.getPorts() = [portA].
     * When  : on appelle {@code redefinePorts(reference)}.
     * Then  : childB.deleteFromProject() et childC.deleteFromProject() sont
     *         appelés ; childA n'est jamais supprimé ; aucun nouveau port
     *         "portA" n'est créé (childA est réutilisé).
     */
    @Test
    void redefinePorts_prunesExtraAndOrphanPorts_keepsValidMirror() {
        IRPClass mother = mock(IRPClass.class);
        when(mother.getGUID()).thenReturn("GUID-mother-3");

        IRPClass otherClass = mock(IRPClass.class);
        when(otherClass.getGUID()).thenReturn("GUID-other-class");

        IRPSysMLPort motherPortA = mock(IRPSysMLPort.class);
        when(motherPortA.getName()).thenReturn("portA");
        when(motherPortA.getGUID()).thenReturn("GUID-mpA");
        when(motherPortA.getOwner()).thenReturn(mother);
        doReturn(collectionOf(motherPortA)).when(mother).getPorts();

        IRPSysMLPort otherPort = mock(IRPSysMLPort.class);
        when(otherPort.getGUID()).thenReturn("GUID-otherport");
        when(otherPort.getOwner()).thenReturn(otherClass); // not owned by the current mother

        // Valid mirror: redefines a port actually owned by the current mother -> kept.
        IRPSysMLPort childA = mock(IRPSysMLPort.class);
        when(childA.getName()).thenReturn("portA");
        when(childA.getGUID()).thenReturn("GUID-childA");
        doReturn(collectionOf(motherPortA)).when(childA).getRedefines();
        doReturn(collectionOf()).when(childA).getStereotypes();

        // Extra port manually added, no redefinition at all -> deleted.
        IRPSysMLPort childB = mock(IRPSysMLPort.class);
        when(childB.getName()).thenReturn("portB");
        when(childB.getGUID()).thenReturn("GUID-childB");
        doReturn(collectionOf()).when(childB).getRedefines();

        // Orphan redefinition, target no longer owned by the current mother -> deleted.
        IRPSysMLPort childC = mock(IRPSysMLPort.class);
        when(childC.getName()).thenReturn("portC");
        when(childC.getGUID()).thenReturn("GUID-childC");
        doReturn(collectionOf(otherPort)).when(childC).getRedefines();

        IRPClass reference = mock(IRPClass.class);
        when(reference.getGUID()).thenReturn("GUID-reference-3");
        when(reference.getName()).thenReturn("MyReference3");
        doReturn(collectionOf(childA, childB, childC)).when(reference).getPorts();
        when(reference.findNestedElement("portA", "Flow Port")).thenReturn(childA);

        IRPGeneralization gen = mock(IRPGeneralization.class);
        when(gen.getUserDefinedMetaClass()).thenReturn(REF_FUNCTION);
        when(gen.getBaseClass()).thenReturn(mother);
        doReturn(collectionOf(gen)).when(reference).getGeneralizations();

        boolean result = RedefinedPortsService.redefinePorts(reference);

        assertTrue(result);
        verify(childB).deleteFromProject();
        verify(childC).deleteFromProject();
        verify(childA, never()).deleteFromProject();
        verify(reference, never()).addNewAggr("Flow Port", "portA"); // reused, not recreated
    }

    // ==================================================================
    // hideRedefinedPorts
    // ==================================================================

    /**
     * Le cœur du "Besoin B" : quand un port est redéfini, Rhapsody affiche à la
     * fois le port hérité (celui de la mère) ET le port redéfini dans le même
     * diagramme, en double. {@code hideRedefinedPorts} doit retirer UNIQUEMENT
     * la représentation graphique du port hérité — pas le port redéfini, et pas
     * le modèle lui-même (juste le dessin dans le diagramme).
     *
     * Given : deux ports graphiques enfants de {@code parentGraphElement} :
     *         {@code motherGraphic} (le port hérité, "Flow Port") et
     *         {@code childGraphic} (le port redéfini, qui redéfinit le premier).
     * When  : on appelle {@code hideRedefinedPorts(app, parentGraphElement)}.
     * Then  : 1 port est retiré, c'est bien {@code motherGraphic} (le port
     *         hérité) qui est ajouté à la collection de retrait puis effacé du
     *         diagramme ; {@code childGraphic} (le port redéfini) est conservé.
     */
    @Test
    void hideRedefinedPorts_removesGraphicOfRedefinedMotherPort_keepsChild() {
        IRPApplication app = mock(IRPApplication.class);

        IRPModelElement parentModel = mock(IRPModelElement.class);
        IRPDiagram diagram = mock(IRPDiagram.class);

        IRPGraphElement parentGraphElement = mock(IRPGraphElement.class);
        when(parentGraphElement.getModelObject()).thenReturn(parentModel);
        when(parentGraphElement.getDiagram()).thenReturn(diagram);

        IRPModelElement motherPortModel = mock(IRPModelElement.class);
        when(motherPortModel.getUserDefinedMetaClass()).thenReturn("Flow Port");
        doReturn(collectionOf()).when(motherPortModel).getRedefines();

        IRPModelElement childPortModel = mock(IRPModelElement.class);
        when(childPortModel.getUserDefinedMetaClass()).thenReturn("Flow Port");
        doReturn(collectionOf(motherPortModel)).when(childPortModel).getRedefines();

        IRPGraphElement motherGraphic = mock(IRPGraphElement.class);
        when(motherGraphic.getModelObject()).thenReturn(motherPortModel);
        when(motherGraphic.getGraphicalParent()).thenReturn(parentGraphElement);

        IRPGraphElement childGraphic = mock(IRPGraphElement.class);
        when(childGraphic.getModelObject()).thenReturn(childPortModel);
        when(childGraphic.getGraphicalParent()).thenReturn(parentGraphElement);

        doReturn(collectionOf(motherGraphic, childGraphic)).when(diagram).getGraphicalElements();

        IRPCollection removalCollection = mock(IRPCollection.class);
        when(app.createNewCollection()).thenReturn(removalCollection);

        int removed = RedefinedPortsService.hideRedefinedPorts(app, parentGraphElement);

        assertEquals(1, removed);
        verify(removalCollection).addGraphicalItem(motherGraphic);
        verify(removalCollection, never()).addGraphicalItem(childGraphic);
        verify(diagram).removeGraphElements(removalCollection);
    }

    /**
     * Garde-fou : si on ne passe aucun élément graphique (paramètre
     * {@code null}), la méthode doit renvoyer 0 sans lever d'exception — utile
     * car cette méthode est appelée depuis un listener où l'élément peut très
     * bien être absent.
     */
    @Test
    void hideRedefinedPorts_returnsZero_whenGraphElementNull() {
        IRPApplication app = mock(IRPApplication.class);
        assertEquals(0, RedefinedPortsService.hideRedefinedPorts(app, (IRPGraphElement) null));
    }

    /**
     * Garde-fou similaire : l'élément graphique existe, mais son "objet modèle"
     * ({@code getModelObject()}) est {@code null} (cas rare mais possible côté
     * Rhapsody). La méthode doit renvoyer 0 sans planter.
     */
    @Test
    void hideRedefinedPorts_returnsZero_whenModelObjectNull() {
        IRPApplication app = mock(IRPApplication.class);
        IRPGraphElement graphElement = mock(IRPGraphElement.class);
        when(graphElement.getModelObject()).thenReturn(null);

        assertEquals(0, RedefinedPortsService.hideRedefinedPorts(app, graphElement));
    }

    /**
     * Cas "rien à nettoyer" : le diagramme ne contient aucun port graphique du
     * tout. La méthode doit renvoyer 0, et surtout ne PAS aller créer une
     * collection de retrait pour rien ({@code app.createNewCollection()} ne
     * doit jamais être appelé) — ça évite un aller-retour COM inutile.
     */
    @Test
    void hideRedefinedPorts_returnsZero_whenNoPortsAreRedefined() {
        IRPApplication app = mock(IRPApplication.class);

        IRPModelElement parentModel = mock(IRPModelElement.class);
        IRPDiagram diagram = mock(IRPDiagram.class);
        doReturn(collectionOf()).when(diagram).getGraphicalElements();

        IRPGraphElement graphElement = mock(IRPGraphElement.class);
        when(graphElement.getModelObject()).thenReturn(parentModel);
        when(graphElement.getDiagram()).thenReturn(diagram);

        assertEquals(0, RedefinedPortsService.hideRedefinedPorts(app, graphElement));
        verify(app, never()).createNewCollection();
    }

    /**
     * Robustesse : il y a bien des ports à retirer, mais Rhapsody n'arrive pas
     * à créer la collection de retrait ({@code createNewCollection()} renvoie
     * {@code null}, ce qui peut arriver si l'appli est occupée). La méthode
     * doit alors abandonner proprement (retour 0) sans jamais appeler
     * {@code diagram.removeGraphElements(...)} avec une collection invalide.
     */
    @Test
    void hideRedefinedPorts_returnsZero_whenRemovalCollectionCannotBeCreated() {
        IRPApplication app = mock(IRPApplication.class);
        when(app.createNewCollection()).thenReturn(null);

        IRPModelElement parentModel = mock(IRPModelElement.class);
        IRPDiagram diagram = mock(IRPDiagram.class);

        IRPGraphElement parentGraphElement = mock(IRPGraphElement.class);
        when(parentGraphElement.getModelObject()).thenReturn(parentModel);
        when(parentGraphElement.getDiagram()).thenReturn(diagram);

        IRPModelElement motherPortModel = mock(IRPModelElement.class);
        when(motherPortModel.getUserDefinedMetaClass()).thenReturn("Flow Port");
        doReturn(collectionOf()).when(motherPortModel).getRedefines();

        IRPModelElement childPortModel = mock(IRPModelElement.class);
        when(childPortModel.getUserDefinedMetaClass()).thenReturn("Flow Port");
        doReturn(collectionOf(motherPortModel)).when(childPortModel).getRedefines();

        IRPGraphElement motherGraphic = mock(IRPGraphElement.class);
        when(motherGraphic.getModelObject()).thenReturn(motherPortModel);
        when(motherGraphic.getGraphicalParent()).thenReturn(parentGraphElement);

        IRPGraphElement childGraphic = mock(IRPGraphElement.class);
        when(childGraphic.getModelObject()).thenReturn(childPortModel);
        when(childGraphic.getGraphicalParent()).thenReturn(parentGraphElement);

        doReturn(collectionOf(motherGraphic, childGraphic)).when(diagram).getGraphicalElements();

        assertEquals(0, RedefinedPortsService.hideRedefinedPorts(app, parentGraphElement));
        verify(diagram, never()).removeGraphElements(any());
    }

    // ==================================================================
    // getGraphicalRepresentations
    // ==================================================================

    /**
     * Garde-fou basique : appeler la méthode avec {@code null} doit renvoyer
     * une liste vide, jamais {@code null} ni une exception (ça évite un
     * {@code NullPointerException} chez l'appelant qui fait un {@code for} sur
     * le résultat).
     */
    @Test
    void getGraphicalRepresentations_returnsEmptyList_whenModelElementNull() {
        List<IRPGraphElement> result = RedefinedPortsService.getGraphicalRepresentations(null);
        assertTrue(result.isEmpty());
    }

    /**
     * Si l'élément du modèle n'est représenté dans aucun diagramme
     * ({@code getReferences()} vide), la méthode doit renvoyer une liste vide.
     */
    @Test
    void getGraphicalRepresentations_returnsEmptyList_whenNoReferences() {
        IRPModelElement modelElement = mock(IRPModelElement.class);
        when(modelElement.getName()).thenReturn("Elem");
        doReturn(collectionOf()).when(modelElement).getReferences();

        List<IRPGraphElement> result = RedefinedPortsService.getGraphicalRepresentations(modelElement);
        assertTrue(result.isEmpty());
    }

    /**
     * Cas nominal : l'élément du modèle est dessiné dans un diagramme. La
     * méthode doit retrouver ce diagramme via {@code getReferences()}, puis
     * demander à ce diagramme sa représentation graphique correspondante via
     * {@code getCorrespondingGraphicElements(...)}, et renvoyer cette
     * représentation dans la liste de résultat.
     */
    @Test
    void getGraphicalRepresentations_collectsGraphicsFromReferencingDiagrams() {
        IRPModelElement modelElement = mock(IRPModelElement.class);
        when(modelElement.getName()).thenReturn("Elem");

        IRPGraphElement graphic = mock(IRPGraphElement.class);

        IRPDiagram diagram = mock(IRPDiagram.class);
        when(diagram.getName()).thenReturn("Diagram1");
        doReturn(collectionOf(graphic)).when(diagram).getCorrespondingGraphicElements(modelElement);

        doReturn(collectionOf(diagram)).when(modelElement).getReferences();

        List<IRPGraphElement> result = RedefinedPortsService.getGraphicalRepresentations(modelElement);

        assertEquals(1, result.size());
        assertTrue(result.contains(graphic));
    }

    // ==================================================================
    // Mother whose ports are inherited: a WhiteBox Function with no port of
    // its own, which "Redefines Function" a BlackBox Function owning them.
    // Rhapsody shows those ports on the reference, so they must be mirrored,
    // and the prune must keep their mirrors.
    // ==================================================================

    /** BlackBox Function owning {@code port}. */
    private static IRPClass blackBoxOwning(String guid, IRPSysMLPort port) {
        IRPClass blackBox = mock(IRPClass.class);
        when(blackBox.getGUID()).thenReturn(guid);
        when(blackBox.getName()).thenReturn("Check_BB");
        when(port.getOwner()).thenReturn(blackBox);
        doReturn(collectionOf(port)).when(blackBox).getPorts();
        return blackBox;
    }

    /** WhiteBox mother with no port of its own and a "Redefines Function" generalization to {@code base}. */
    private static IRPClass whiteBoxOver(String guid, IRPClass base) {
        IRPClass whiteBox = mock(IRPClass.class);
        when(whiteBox.getGUID()).thenReturn(guid);
        when(whiteBox.getName()).thenReturn("Check");
        doReturn(collectionOf()).when(whiteBox).getPorts();
        IRPGeneralization redefines = mock(IRPGeneralization.class);
        when(redefines.getUserDefinedMetaClass()).thenReturn("Redefines Function");
        when(redefines.getBaseClass()).thenReturn(base);
        doReturn(collectionOf(redefines)).when(whiteBox).getGeneralizations();
        return whiteBox;
    }

    /** Reference owning {@code ownPorts}, with a "References Function" generalization to {@code mother}. */
    private static IRPClass referenceOf(String guid, IRPClass mother, IRPSysMLPort... ownPorts) {
        IRPClass reference = mock(IRPClass.class);
        when(reference.getGUID()).thenReturn(guid);
        when(reference.getName()).thenReturn("UIUI");
        doReturn(collectionOf((Object[]) ownPorts)).when(reference).getPorts();
        IRPGeneralization refGen = mock(IRPGeneralization.class);
        when(refGen.getUserDefinedMetaClass()).thenReturn(REF_FUNCTION);
        when(refGen.getBaseClass()).thenReturn(mother);
        doReturn(collectionOf(refGen)).when(reference).getGeneralizations();
        return reference;
    }

    private static IRPSysMLPort port(String name, String guid) {
        IRPSysMLPort p = mock(IRPSysMLPort.class);
        when(p.getName()).thenReturn(name);
        when(p.getGUID()).thenReturn(guid);
        when(p.getPortDirection()).thenReturn("in");
        doReturn(collectionOf()).when(p).getStereotypes();
        return p;
    }

    @Test
    void redefinePorts_mirrorsPortsTheMotherInheritsFromItsOwnBase() {
        IRPSysMLPort bbPort = port("P1", "GUID-bb-p1");
        IRPClass whiteBox = whiteBoxOver("GUID-wb", blackBoxOwning("GUID-bb", bbPort));
        IRPClass reference = referenceOf("GUID-ref-inh", whiteBox);

        IRPSysMLPort created = mock(IRPSysMLPort.class);
        when(created.getGUID()).thenReturn("GUID-created-inh");
        doReturn(collectionOf()).when(created).getStereotypes();
        when(reference.addNewAggr("Flow Port", "P1")).thenReturn(created);

        assertTrue(RedefinedPortsService.redefinePorts(reference));
        verify(reference).addNewAggr("Flow Port", "P1");
        verify(created, atLeastOnce()).addRedefines(bbPort);
    }

    @Test
    void redefinePorts_keepsTheMirrorOfAPortTheMotherInherits() {
        IRPSysMLPort bbPort = port("P1", "GUID-bb-p1-keep");
        IRPClass whiteBox = whiteBoxOver("GUID-wb-keep", blackBoxOwning("GUID-bb-keep", bbPort));
        IRPSysMLPort mirror = port("P1", "GUID-mirror-keep");
        doReturn(collectionOf(bbPort)).when(mirror).getRedefines();
        IRPClass reference = referenceOf("GUID-ref-keep", whiteBox, mirror);
        when(reference.findNestedElement("P1", "Flow Port")).thenReturn(mirror);

        assertTrue(RedefinedPortsService.redefinePorts(reference));
        verify(mirror, never()).deleteFromProject();
        verify(reference, never()).addNewAggr(anyString(), anyString());
    }

    // ==================================================================
    // isReferenceClass : cas limites
    // ==================================================================

    /** Une classe sans collection de généralisations n'est pas une référence. */
    @Test
    void isReferenceClass_falseWhenGeneralizationsNull() {
        IRPClass element = mock(IRPClass.class);
        when(element.getGeneralizations()).thenReturn(null);

        assertFalse(RedefinedPortsService.isReferenceClass(element));
    }

    /** Un objet qui n'est pas une généralisation est ignoré, la recherche continue. */
    @Test
    void isReferenceClass_skipsItemsThatAreNotGeneralizations() {
        IRPGeneralization gen = mock(IRPGeneralization.class);
        when(gen.getUserDefinedMetaClass()).thenReturn(REF_FUNCTION);

        IRPClass element = mock(IRPClass.class);
        doReturn(collectionOf(mock(IRPModelElement.class), gen)).when(element).getGeneralizations();

        assertTrue(RedefinedPortsService.isReferenceClass(element));
    }

    // ==================================================================
    // redefinePorts : re-tentatives et robustesse
    // ==================================================================

    /**
     * Juste après la création du lien, la référence peut ne pas encore exposer
     * sa généralisation : pas de mère trouvée, donc false (re-tentable), et
     * surtout aucun port supprimé.
     */
    @Test
    void redefinePorts_returnsFalse_whenNoReferenceGeneralizationYet() {
        IRPSysMLPort existing = port("P1", "GUID-existing-nogen");
        IRPClass reference = mock(IRPClass.class);
        when(reference.getGeneralizations()).thenReturn(null);
        doReturn(collectionOf(existing)).when(reference).getPorts();

        assertFalse(RedefinedPortsService.redefinePorts(reference));
        verify(existing, never()).deleteFromProject();
    }

    /**
     * Un échec COM à la création d'un port ne doit pas empêcher le miroir des
     * autres ports ; le résultat est false pour que l'appelant re-tente.
     */
    @Test
    void redefinePorts_returnsFalse_whenOnePortCannotBeCreated_butMirrorsTheOthers() {
        IRPSysMLPort p1 = port("P1", "GUID-fail-p1");
        IRPSysMLPort p2 = port("P2", "GUID-fail-p2");
        IRPClass mother = mock(IRPClass.class);
        when(mother.getGUID()).thenReturn("GUID-mother-fail");
        doReturn(collectionOf(p1, p2)).when(mother).getPorts();
        IRPClass reference = referenceOf("GUID-ref-fail", mother);

        when(reference.addNewAggr("Flow Port", "P1")).thenThrow(new RhapsodyRuntimeException("busy"));
        IRPSysMLPort created2 = port("P2", "GUID-created-fail-p2");
        when(reference.addNewAggr("Flow Port", "P2")).thenReturn(created2);

        assertFalse(RedefinedPortsService.redefinePorts(reference));
        verify(created2, atLeastOnce()).addRedefines(p2);
    }

    /** Si la recherche d'un port existant échoue, le port est créé normalement. */
    @Test
    void redefinePorts_createsPort_whenSearchForExistingPortFails() {
        IRPSysMLPort p1 = port("P1", "GUID-search-p1");
        IRPClass mother = mock(IRPClass.class);
        when(mother.getGUID()).thenReturn("GUID-mother-search");
        doReturn(collectionOf(p1)).when(mother).getPorts();
        IRPClass reference = referenceOf("GUID-ref-search", mother);
        when(reference.findNestedElement("P1", "Flow Port")).thenThrow(new RhapsodyRuntimeException("busy"));
        IRPSysMLPort created = port("P1", "GUID-created-search");
        when(reference.addNewAggr("Flow Port", "P1")).thenReturn(created);

        assertTrue(RedefinedPortsService.redefinePorts(reference));
        verify(created, atLeastOnce()).addRedefines(p1);
    }

    /** Si les ports de la mère ne sont pas lisibles, false (re-tentable). */
    @Test
    void redefinePorts_returnsFalse_whenMotherPortsCannotBeRead() {
        IRPCollection unreadable = mock(IRPCollection.class);
        when(unreadable.toList()).thenThrow(new RhapsodyRuntimeException("busy"));
        IRPClass mother = mock(IRPClass.class);
        when(mother.getGUID()).thenReturn("GUID-mother-unreadable");
        doReturn(unreadable).when(mother).getPorts();
        IRPClass reference = referenceOf("GUID-ref-unreadable", mother);

        assertFalse(RedefinedPortsService.redefinePorts(reference));
        verify(reference, never()).addNewAggr(anyString(), anyString());
    }

    /**
     * Copie des stéréotypes : seuls les stéréotypes « simples » sont copiés, pas
     * les New Terms (un port ne peut en porter qu'un, celui de sa métaclasse).
     * Un stéréotype qui ne s'applique pas n'empêche pas de copier les suivants.
     */
    @Test
    void redefinePorts_copiesOnlyNonNewTermStereotypes() {
        IRPStereotype plain = mock(IRPStereotype.class);
        when(plain.getIsNewTerm()).thenReturn(0);
        IRPStereotype newTerm = mock(IRPStereotype.class);
        when(newTerm.getIsNewTerm()).thenReturn(1);
        IRPStereotype refused = mock(IRPStereotype.class);
        when(refused.getIsNewTerm()).thenReturn(0);
        IRPStereotype last = mock(IRPStereotype.class);
        when(last.getIsNewTerm()).thenReturn(0);

        IRPSysMLPort p1 = port("P1", "GUID-st-p1");
        doReturn(collectionOf(plain, newTerm, null, refused, last)).when(p1).getStereotypes();
        IRPClass mother = mock(IRPClass.class);
        when(mother.getGUID()).thenReturn("GUID-mother-st");
        doReturn(collectionOf(p1)).when(mother).getPorts();
        IRPClass reference = referenceOf("GUID-ref-st", mother);
        IRPSysMLPort created = port("P1", "GUID-created-st");
        doThrow(new RhapsodyRuntimeException("not applicable")).when(created).addSpecificStereotype(refused);
        when(reference.addNewAggr("Flow Port", "P1")).thenReturn(created);

        assertTrue(RedefinedPortsService.redefinePorts(reference));
        verify(created, atLeastOnce()).addSpecificStereotype(plain);
        verify(created, never()).addSpecificStereotype(newTerm);
        verify(created, atLeastOnce()).addSpecificStereotype(refused);
        verify(created, atLeastOnce()).addSpecificStereotype(last);
    }

    /** Port de la mère sans type : on ne touche pas au type, mais direction et redéfinition sont posées. */
    @Test
    void redefinePorts_leavesTypeAlone_whenMotherPortHasNoType() {
        IRPSysMLPort p1 = port("P1", "GUID-notype-p1");
        when(p1.getType()).thenReturn(null);
        IRPClass mother = mock(IRPClass.class);
        when(mother.getGUID()).thenReturn("GUID-mother-notype");
        doReturn(collectionOf(p1)).when(mother).getPorts();
        IRPClass reference = referenceOf("GUID-ref-notype", mother);
        IRPSysMLPort created = port("P1", "GUID-created-notype");
        when(reference.addNewAggr("Flow Port", "P1")).thenReturn(created);

        assertTrue(RedefinedPortsService.redefinePorts(reference));
        verify(created, never()).setType(any());
        verify(created, atLeastOnce()).setPortDirection("in");
        verify(created, atLeastOnce()).addRedefines(p1);
    }

    /** Un échec sur la copie du type n'empêche pas de copier la direction et la redéfinition. */
    @Test
    void redefinePorts_stillCopiesDirectionAndRedefines_whenTypeCopyFails() {
        IRPClassifier type = mock(IRPClassifier.class);
        IRPSysMLPort p1 = port("P1", "GUID-typefail-p1");
        when(p1.getType()).thenReturn(type);
        IRPClass mother = mock(IRPClass.class);
        when(mother.getGUID()).thenReturn("GUID-mother-typefail");
        when(p1.getOwner()).thenReturn(mother);
        doReturn(collectionOf(p1)).when(mother).getPorts();
        IRPSysMLPort existing = port("P1", "GUID-existing-typefail");
        doReturn(collectionOf(p1)).when(existing).getRedefines();
        doThrow(new RhapsodyRuntimeException("type refused")).when(existing).setType(type);
        IRPClass reference = referenceOf("GUID-ref-typefail", mother, existing);
        when(reference.findNestedElement("P1", "Flow Port")).thenReturn(existing);

        assertTrue(RedefinedPortsService.redefinePorts(reference));
        verify(existing).setPortDirection("in");
        verify(existing).addRedefines(p1);
    }

    /** Une suppression qui échoue n'arrête pas l'élagage des autres ports. */
    @Test
    void redefinePorts_keepsPruning_whenADeleteFails() {
        IRPClass mother = mock(IRPClass.class);
        when(mother.getGUID()).thenReturn("GUID-mother-delfail");
        doReturn(collectionOf()).when(mother).getPorts();
        IRPSysMLPort extra1 = port("X1", "GUID-extra1");
        doReturn(collectionOf()).when(extra1).getRedefines();
        doThrow(new RhapsodyRuntimeException("locked")).when(extra1).deleteFromProject();
        IRPSysMLPort extra2 = port("X2", "GUID-extra2");
        doReturn(collectionOf()).when(extra2).getRedefines();
        IRPClass reference = referenceOf("GUID-ref-delfail", mother, extra1, extra2);

        assertTrue(RedefinedPortsService.redefinePorts(reference));
        verify(extra1).deleteFromProject();
        verify(extra2).deleteFromProject();
    }

    /** L'élagage ne concerne que les Flow Ports (IRPSysMLPort) : un port standard n'est jamais supprimé. */
    @Test
    void redefinePorts_neverDeletesChildrenThatAreNotFlowPorts() {
        IRPClass mother = mock(IRPClass.class);
        when(mother.getGUID()).thenReturn("GUID-mother-std");
        doReturn(collectionOf()).when(mother).getPorts();
        IRPPort standardPort = mock(IRPPort.class);
        IRPClass reference = mock(IRPClass.class);
        when(reference.getGUID()).thenReturn("GUID-ref-std");
        doReturn(collectionOf(standardPort)).when(reference).getPorts();
        IRPGeneralization refGen = mock(IRPGeneralization.class);
        when(refGen.getUserDefinedMetaClass()).thenReturn(REF_LOGICAL_SYSTEM);
        when(refGen.getBaseClass()).thenReturn(mother);
        doReturn(collectionOf(refGen)).when(reference).getGeneralizations();

        assertTrue(RedefinedPortsService.redefinePorts(reference));
        verify(standardPort, never()).deleteFromProject();
    }

    // ==================================================================
    // motherChain / effectiveMotherPorts
    // ==================================================================

    /** Pose sur {@code derived} une généralisation "Redefines Function" vers chaque base. */
    private static void generalize(IRPClass derived, IRPClassifier... bases) {
        Object[] gens = new Object[bases.length];
        for (int i = 0; i < bases.length; i++) {
            IRPGeneralization g = mock(IRPGeneralization.class);
            when(g.getUserDefinedMetaClass()).thenReturn("Redefines Function");
            when(g.getBaseClass()).thenReturn(bases[i]);
            gens[i] = g;
        }
        doReturn(collectionOf(gens)).when(derived).getGeneralizations();
    }

    private static IRPClass classWithGuid(String guid) {
        IRPClass c = mock(IRPClass.class);
        when(c.getGUID()).thenReturn(guid);
        return c;
    }

    @Test
    void motherChain_isEmpty_whenMotherIsNotAClass() {
        assertTrue(RedefinedPortsService.motherChain(mock(IRPClassifier.class)).isEmpty());
        assertTrue(RedefinedPortsService.motherChain(null).isEmpty());
    }

    /** Parcours en largeur, la mère d'abord ; un cycle de généralisations ne boucle pas. */
    @Test
    void motherChain_listsNearestFirst_eachClassOnce_evenWithACycle() {
        IRPClass a = classWithGuid("GUID-A");
        IRPClass b = classWithGuid("GUID-B");
        IRPClass c = classWithGuid("GUID-C");
        generalize(a, b, c);
        generalize(b, a); // cycle A -> B -> A
        doReturn(collectionOf()).when(c).getGeneralizations();

        assertEquals(Arrays.asList(a, b, c), RedefinedPortsService.motherChain(a));
    }

    /**
     * Deux proxys COM différents du même élément (même GUID) ne comptent
     * qu'une fois (héritage en losange).
     */
    @Test
    void motherChain_mergesTwoProxiesOfTheSameClass() {
        IRPClass top = classWithGuid("GUID-top");
        IRPClass left = classWithGuid("GUID-left");
        IRPClass right = classWithGuid("GUID-right");
        IRPClass baseViaLeft = classWithGuid("GUID-base");
        IRPClass baseViaRight = classWithGuid("GUID-base");
        generalize(top, left, right);
        generalize(left, baseViaLeft);
        generalize(right, baseViaRight);
        doReturn(collectionOf()).when(baseViaLeft).getGeneralizations();

        List<IRPClass> chain = RedefinedPortsService.motherChain(top);

        assertEquals(Arrays.asList(top, left, right, baseViaLeft), chain);
    }

    /** Sans GUID lisible, deux classes distinctes restent distinctes (pas fusionnées sous "null"). */
    @Test
    void motherChain_keepsDistinctClasses_whenGuidsAreUnreadable() {
        IRPClass mother = mock(IRPClass.class);
        when(mother.getGUID()).thenThrow(new RhapsodyRuntimeException("busy"));
        IRPClass base = mock(IRPClass.class);
        when(base.getGUID()).thenReturn(null);
        generalize(mother, base, base);
        doReturn(collectionOf()).when(base).getGeneralizations();

        assertEquals(Arrays.asList(mother, base), RedefinedPortsService.motherChain(mother));
    }

    /** Une généralisation illisible arrête la remontée à cet endroit, sans erreur. */
    @Test
    void motherChain_keepsWhatWasFound_whenGeneralizationsCannotBeRead() {
        IRPClass mother = classWithGuid("GUID-m");
        IRPClass base = classWithGuid("GUID-b");
        IRPClassifier notAClass = mock(IRPClassifier.class);
        generalize(mother, notAClass, base);
        when(base.getGeneralizations()).thenThrow(new RhapsodyRuntimeException("busy"));

        assertEquals(Arrays.asList(mother, base), RedefinedPortsService.motherChain(mother));
    }

    /** Un port de la mère masque le port de même nom hérité de sa base. */
    @Test
    void effectiveMotherPorts_nearestPortWins_bySameName() {
        IRPSysMLPort own = port("P1", "GUID-own-p1");
        IRPSysMLPort ownP2 = port("P2", "GUID-own-p2");
        IRPSysMLPort inheritedSameName = port("P1", "GUID-bb-p1-name");
        IRPSysMLPort inheritedOther = port("P3", "GUID-bb-p3");
        IRPClass mother = classWithGuid("GUID-wb-name");
        IRPClass base = classWithGuid("GUID-bb-name");
        doReturn(collectionOf(own, ownP2)).when(mother).getPorts();
        doReturn(collectionOf(inheritedSameName, inheritedOther)).when(base).getPorts();
        generalize(mother, base);

        assertEquals(Arrays.asList(own, ownP2, inheritedOther),
                RedefinedPortsService.effectiveMotherPorts(mother));
    }

    /** Un port de la base redéfini par la mère (même sous un autre nom) n'est pas repris. */
    @Test
    void effectiveMotherPorts_dropsInheritedPortRedefinedNearer() {
        IRPSysMLPort inherited = port("P1", "GUID-bb-p1-redef");
        IRPSysMLPort inheritedProxy = port("P1", "GUID-bb-p1-redef");
        IRPSysMLPort renamed = port("P1_renamed", "GUID-wb-p1-redef");
        doReturn(collectionOf(inheritedProxy)).when(renamed).getRedefines();
        IRPClass mother = classWithGuid("GUID-wb-redef");
        IRPClass base = classWithGuid("GUID-bb-redef");
        doReturn(collectionOf(renamed)).when(mother).getPorts();
        doReturn(collectionOf(inherited)).when(base).getPorts();
        generalize(mother, base);

        assertEquals(Arrays.asList(renamed), RedefinedPortsService.effectiveMotherPorts(mother));
    }

    /** Les éléments qui ne sont pas des Flow Ports et les classes illisibles sont ignorés. */
    @Test
    void effectiveMotherPorts_skipsNonFlowPortsAndUnreadableClasses() {
        IRPSysMLPort p1 = port("P1", "GUID-skip-p1");
        IRPClass mother = classWithGuid("GUID-skip-m");
        IRPClass unreadable = classWithGuid("GUID-skip-u");
        IRPClass base = classWithGuid("GUID-skip-b");
        when(mother.getPorts()).thenThrow(new RhapsodyRuntimeException("busy"));
        when(unreadable.getPorts()).thenReturn(null);
        doReturn(collectionOf(mock(IRPPort.class), p1)).when(base).getPorts();
        generalize(mother, unreadable, base);

        assertEquals(Arrays.asList(p1), RedefinedPortsService.effectiveMotherPorts(mother));
    }

    // ==================================================================
    // hideRedefinedPorts : filtrage des ports graphiques et transaction
    // ==================================================================

    /** Noeud graphique (la référence) dessiné sur {@code diagram}. */
    private static IRPGraphElement nodeOn(IRPDiagram diagram) {
        IRPGraphElement node = mock(IRPGraphElement.class);
        when(node.getModelObject()).thenReturn(mock(IRPModelElement.class));
        when(node.getDiagram()).thenReturn(diagram);
        return node;
    }

    /** Elément graphique dont le parent graphique est {@code parent}. */
    private static IRPGraphElement graphicUnder(IRPGraphElement parent, IRPModelElement model) {
        IRPGraphElement g = mock(IRPGraphElement.class);
        when(g.getModelObject()).thenReturn(model);
        when(g.getGraphicalParent()).thenReturn(parent);
        return g;
    }

    /** Modèle de port reconnu par sa métaclasse utilisateur ou sa métaclasse, redéfinissant {@code targets}. */
    private static IRPModelElement portModel(String udm, String metaClass, IRPModelElement... targets) {
        IRPModelElement m = mock(IRPModelElement.class);
        when(m.getUserDefinedMetaClass()).thenReturn(udm);
        when(m.getMetaClass()).thenReturn(metaClass);
        doReturn(collectionOf((Object[]) targets)).when(m).getRedefines();
        return m;
    }

    /** Port hérité + son port redéfini sous {@code node} ; renvoie {hérité, redéfini}. */
    private static IRPGraphElement[] inheritedAndRedefinedUnder(IRPGraphElement node) {
        IRPModelElement inherited = portModel("Flow Port", "SysMLPort");
        IRPModelElement redefined = portModel("Flow Port", "SysMLPort", inherited);
        return new IRPGraphElement[] { graphicUnder(node, inherited), graphicUnder(node, redefined) };
    }

    /**
     * Seuls les ports hérités dessinés SOUS le noeud traité sont retirés : un
     * port "SysMLPort" ou "Port" (sans métaclasse utilisateur) compte comme
     * port, mais pas un autre élément, ni un port d'un autre noeud, ni un
     * élément sans parent ou sans modèle.
     */
    @Test
    void hideRedefinedPorts_removesOnlyInheritedPortsDrawnUnderTheNode() {
        IRPApplication app = mock(IRPApplication.class);
        IRPDiagram diagram = mock(IRPDiagram.class);
        IRPGraphElement node = nodeOn(diagram);
        IRPGraphElement otherNode = nodeOn(diagram);

        IRPModelElement inheritedSysML = portModel(null, "SysMLPort");
        IRPModelElement inheritedStd = portModel(null, "Port");
        IRPModelElement notAPort = portModel(null, "Class");
        IRPModelElement redefining = portModel("Flow Port", "SysMLPort", inheritedSysML, inheritedStd, notAPort);

        IRPGraphElement gSysML = graphicUnder(node, inheritedSysML);
        IRPGraphElement gStd = graphicUnder(node, inheritedStd);
        IRPGraphElement gNotAPort = graphicUnder(node, notAPort);
        IRPGraphElement gRedefining = graphicUnder(node, redefining);
        IRPGraphElement gOnOtherNode = graphicUnder(otherNode, inheritedSysML);
        IRPGraphElement gNoParent = graphicUnder(null, inheritedSysML);
        IRPGraphElement gNoModel = graphicUnder(node, null);

        doReturn(collectionOf(gSysML, gStd, gNotAPort, gRedefining, gOnOtherNode, gNoParent, gNoModel, "not a graphic"))
                .when(diagram).getGraphicalElements();
        IRPCollection removal = mock(IRPCollection.class);
        when(app.createNewCollection()).thenReturn(removal);

        assertEquals(2, RedefinedPortsService.hideRedefinedPorts(app, node));
        verify(removal).addGraphicalItem(gSysML);
        verify(removal).addGraphicalItem(gStd);
        verify(removal, never()).addGraphicalItem(gNotAPort);
        verify(removal, never()).addGraphicalItem(gRedefining);
        verify(removal, never()).addGraphicalItem(gOnOtherNode);
        verify(removal, never()).addGraphicalItem(gNoParent);
    }

    /** Le retrait graphique est encadré par une transaction undo (sinon la pile d'undo se corrompt). */
    @Test
    void hideRedefinedPorts_removesInsideAnUndoTransaction() {
        IRPApplication app = mock(IRPApplication.class);
        IRPDiagram diagram = mock(IRPDiagram.class);
        IRPGraphElement node = nodeOn(diagram);
        doReturn(collectionOf((Object[]) inheritedAndRedefinedUnder(node))).when(diagram).getGraphicalElements();
        IRPCollection removal = mock(IRPCollection.class);
        when(app.createNewCollection()).thenReturn(removal);

        RedefinedPortsService.hideRedefinedPorts(app, node);

        InOrder order = inOrder(app, diagram);
        order.verify(app).startUndoTransaction();
        order.verify(diagram).removeGraphElements(removal);
        order.verify(app).endUndoTransaction();
    }

    /** Un retrait refusé par Rhapsody (« Membre introuvable ») ne remonte pas au listener. */
    @Test
    void hideRedefinedPorts_doesNotThrow_whenRemovalFails() {
        IRPApplication app = mock(IRPApplication.class);
        IRPDiagram diagram = mock(IRPDiagram.class);
        IRPGraphElement node = nodeOn(diagram);
        doReturn(collectionOf((Object[]) inheritedAndRedefinedUnder(node))).when(diagram).getGraphicalElements();
        IRPCollection removal = mock(IRPCollection.class);
        when(app.createNewCollection()).thenReturn(removal);
        doThrow(new RhapsodyRuntimeException("Membre introuvable")).when(diagram).removeGraphElements(removal);

        assertDoesNotThrow(() -> RedefinedPortsService.hideRedefinedPorts(app, node));
        verify(app).endUndoTransaction();
    }

    // ==================================================================
    // hideRedefinedPorts (liste de représentations)
    // ==================================================================

    @Test
    void hideRedefinedPortsList_returnsZero_forNullOrEmptyList() {
        IRPApplication app = mock(IRPApplication.class);
        assertEquals(0, RedefinedPortsService.hideRedefinedPorts(app, (List<IRPGraphElement>) null));
        assertEquals(0, RedefinedPortsService.hideRedefinedPorts(app, new ArrayList<IRPGraphElement>()));
        verify(app, never()).createNewCollection();
    }

    /**
     * Référence dessinée dans deux diagrammes : chaque diagramme ne reçoit que
     * SES ports hérités, dans sa propre collection (sinon « Membre introuvable »).
     */
    @Test
    void hideRedefinedPortsList_removesEachDiagramsOwnPorts() {
        IRPApplication app = mock(IRPApplication.class);
        IRPDiagram d1 = mock(IRPDiagram.class);
        IRPDiagram d2 = mock(IRPDiagram.class);
        IRPGraphElement node1 = nodeOn(d1);
        IRPGraphElement node2 = nodeOn(d2);
        IRPGraphElement[] pair1 = inheritedAndRedefinedUnder(node1);
        IRPGraphElement[] pair2 = inheritedAndRedefinedUnder(node2);
        doReturn(collectionOf((Object[]) pair1)).when(d1).getGraphicalElements();
        doReturn(collectionOf((Object[]) pair2)).when(d2).getGraphicalElements();
        IRPCollection c1 = mock(IRPCollection.class);
        IRPCollection c2 = mock(IRPCollection.class);
        when(app.createNewCollection()).thenReturn(c1, c2);

        int removed = RedefinedPortsService.hideRedefinedPorts(app, Arrays.asList(node1, null, node2));

        assertEquals(2, removed);
        verify(c1).addGraphicalItem(pair1[0]);
        verify(c1, never()).addGraphicalItem(pair2[0]);
        verify(c2).addGraphicalItem(pair2[0]);
        verify(c2, never()).addGraphicalItem(pair1[0]);
        verify(d1).removeGraphElements(c1);
        verify(d2).removeGraphElements(c2);
    }

    /** Un diagramme en échec ne bloque pas les autres, et n'est pas compté. */
    @Test
    void hideRedefinedPortsList_countsOnlySuccessfulDiagrams_andContinuesAfterAFailure() {
        IRPApplication app = mock(IRPApplication.class);
        IRPDiagram d1 = mock(IRPDiagram.class);
        IRPDiagram d2 = mock(IRPDiagram.class);
        IRPGraphElement node1 = nodeOn(d1);
        IRPGraphElement node2 = nodeOn(d2);
        doReturn(collectionOf((Object[]) inheritedAndRedefinedUnder(node1))).when(d1).getGraphicalElements();
        doReturn(collectionOf((Object[]) inheritedAndRedefinedUnder(node2))).when(d2).getGraphicalElements();
        IRPCollection c1 = mock(IRPCollection.class);
        IRPCollection c2 = mock(IRPCollection.class);
        when(app.createNewCollection()).thenReturn(c1, c2);
        doThrow(new RhapsodyRuntimeException("Membre introuvable")).when(d1).removeGraphElements(c1);

        assertEquals(1, RedefinedPortsService.hideRedefinedPorts(app, Arrays.asList(node1, node2)));
        verify(d2).removeGraphElements(c2);
    }

    /** Sans collection de retrait pour un diagramme, on passe au suivant. */
    @Test
    void hideRedefinedPortsList_skipsDiagram_whenRemovalCollectionCannotBeCreated() {
        IRPApplication app = mock(IRPApplication.class);
        IRPDiagram d1 = mock(IRPDiagram.class);
        IRPDiagram d2 = mock(IRPDiagram.class);
        IRPGraphElement node1 = nodeOn(d1);
        IRPGraphElement node2 = nodeOn(d2);
        doReturn(collectionOf((Object[]) inheritedAndRedefinedUnder(node1))).when(d1).getGraphicalElements();
        doReturn(collectionOf((Object[]) inheritedAndRedefinedUnder(node2))).when(d2).getGraphicalElements();
        IRPCollection c2 = mock(IRPCollection.class);
        when(app.createNewCollection()).thenReturn(null, c2);

        assertEquals(1, RedefinedPortsService.hideRedefinedPorts(app, Arrays.asList(node1, node2)));
        verify(d1, never()).removeGraphElements(any());
        verify(d2).removeGraphElements(c2);
    }

    // ==================================================================
    // getGraphicalRepresentations : robustesse
    // ==================================================================

    @Test
    void getGraphicalRepresentations_returnsEmptyList_whenReferencesCannotBeRead() {
        IRPModelElement modelElement = mock(IRPModelElement.class);
        when(modelElement.getReferences()).thenThrow(new RhapsodyRuntimeException("busy"));

        assertTrue(RedefinedPortsService.getGraphicalRepresentations(modelElement).isEmpty());
    }

    /**
     * Seuls les diagrammes comptent parmi les références ; un diagramme
     * illisible ou sans représentation est sauté, les autres sont collectés.
     */
    @Test
    void getGraphicalRepresentations_skipsNonDiagramsAndUnreadableDiagrams() {
        IRPModelElement modelElement = mock(IRPModelElement.class);
        IRPDiagram unreadable = mock(IRPDiagram.class);
        when(unreadable.getCorrespondingGraphicElements(modelElement)).thenThrow(new RhapsodyRuntimeException("busy"));
        IRPDiagram empty = mock(IRPDiagram.class);
        doReturn(collectionOf()).when(empty).getCorrespondingGraphicElements(modelElement);
        IRPDiagram none = mock(IRPDiagram.class);
        when(none.getCorrespondingGraphicElements(modelElement)).thenReturn(null);
        IRPGraphElement g1 = mock(IRPGraphElement.class);
        IRPGraphElement g2 = mock(IRPGraphElement.class);
        IRPDiagram good = mock(IRPDiagram.class);
        doReturn(collectionOf(g1, "not a graphic", g2)).when(good).getCorrespondingGraphicElements(modelElement);
        doReturn(collectionOf(mock(IRPClass.class), unreadable, empty, none, good)).when(modelElement).getReferences();

        assertEquals(Arrays.asList(g1, g2), RedefinedPortsService.getGraphicalRepresentations(modelElement));
    }

    // ==================================================================
    // Prune: never delete a port it cannot read (a COM error must not
    // turn a valid mirror, and its flows, into an "orphan" to delete).
    // ==================================================================

    @Test
    void redefinePorts_keepsAPortWhoseRedefinitionsCannotBeRead() {
        IRPClass mother = mock(IRPClass.class);
        when(mother.getGUID()).thenReturn("GUID-m-unreadable");
        doReturn(collectionOf()).when(mother).getPorts();
        IRPSysMLPort unreadable = port("P9", "GUID-p9-unreadable");
        when(unreadable.getRedefines()).thenThrow(new RuntimeException("COM error"));
        IRPClass reference = referenceOf("GUID-ref-unreadable", mother, unreadable);

        RedefinedPortsService.redefinePorts(reference);

        verify(unreadable, never()).deleteFromProject();
    }

    @Test
    void redefinePorts_keepsAPortWhoseRedefinedTargetOwnerCannotBeRead() {
        IRPClass mother = mock(IRPClass.class);
        when(mother.getGUID()).thenReturn("GUID-m-owner");
        doReturn(collectionOf()).when(mother).getPorts();
        IRPSysMLPort target = mock(IRPSysMLPort.class);
        when(target.getOwner()).thenThrow(new RuntimeException("COM error"));
        IRPSysMLPort child = port("P1", "GUID-child-owner");
        doReturn(collectionOf(target)).when(child).getRedefines();
        IRPClass reference = referenceOf("GUID-ref-owner", mother, child);

        RedefinedPortsService.redefinePorts(reference);

        verify(child, never()).deleteFromProject();
    }

    @Test
    void redefinePorts_prunesNothing_whenTheMothersAncestorsCannotBeRead() {
        IRPSysMLPort bbPort = port("P1", "GUID-bb-p1-unreadable-chain");
        blackBoxOwning("GUID-bb-unreadable-chain", bbPort);
        IRPClass whiteBox = mock(IRPClass.class);
        when(whiteBox.getGUID()).thenReturn("GUID-wb-unreadable-chain");
        doReturn(collectionOf()).when(whiteBox).getPorts();
        when(whiteBox.getGeneralizations()).thenThrow(new RuntimeException("COM error"));
        IRPSysMLPort mirror = port("P1", "GUID-mirror-unreadable-chain");
        doReturn(collectionOf(bbPort)).when(mirror).getRedefines();
        IRPSysMLPort extra = port("X", "GUID-extra-unreadable-chain");
        doReturn(collectionOf()).when(extra).getRedefines();
        IRPClass reference = referenceOf("GUID-ref-unreadable-chain", whiteBox, mirror, extra);

        RedefinedPortsService.redefinePorts(reference);

        verify(mirror, never()).deleteFromProject();
        verify(extra, never()).deleteFromProject();
    }

    // ==================================================================
    // Redefined port drawn AT THE PLACE of the inherited port it replaces
    // ==================================================================

    private static IRPGraphicalProperty property(String value) {
        IRPGraphicalProperty p = mock(IRPGraphicalProperty.class);
        when(p.getValue()).thenReturn(value);
        return p;
    }

    @Test
    void hideRedefinedPorts_bothDrawn_redefinedPortTakesThePlaceOfTheInheritedOne_insideTheTransaction() {
        IRPApplication app = mock(IRPApplication.class);
        IRPDiagram diagram = mock(IRPDiagram.class);
        IRPGraphElement node = nodeOn(diagram);
        IRPGraphElement[] pair = inheritedAndRedefinedUnder(node);
        IRPGraphicalProperty pos = property("120,340");
        doReturn(pos).when(pair[0]).getGraphicalProperty("Position");
        doReturn(collectionOf(pair[0], pair[1])).when(diagram).getGraphicalElements();
        IRPCollection removal = mock(IRPCollection.class);
        when(app.createNewCollection()).thenReturn(removal);

        assertEquals(1, RedefinedPortsService.hideRedefinedPorts(app, node));

        InOrder order = inOrder(app, pair[1], diagram);
        order.verify(app).startUndoTransaction();
        order.verify(pair[1]).setGraphicalProperty("Position", "120,340");
        order.verify(diagram).removeGraphElements(removal);
        order.verify(app).endUndoTransaction();
        verify(removal).addGraphicalItem(pair[0]);
        verify(removal, never()).addGraphicalItem(pair[1]);
    }

    // ------------------------------------------------------------------
    // Flows drawn on a replaced inherited port: definition flows copied
    // (never touched), reference flows reconnected, nothing deleted
    // ------------------------------------------------------------------

    /** A box-like graphic (IRPGraphNode) under {@code parent} showing {@code model}. */
    private static IRPGraphNode boxGraphic(IRPGraphElement parent, IRPModelElement model, String position) {
        IRPGraphNode g = mock(IRPGraphNode.class);
        when(g.getModelObject()).thenReturn(model);
        when(g.getGraphicalParent()).thenReturn(parent);
        IRPGraphicalProperty p = property(position);
        when(g.getGraphicalProperty("Position")).thenReturn(p);
        return g;
    }

    /** A class named {@code name} that references a mother ("References Function"). */
    private static IRPClass referenceBox(String name) {
        IRPClass c = mock(IRPClass.class);
        when(c.getGUID()).thenReturn("GUID-" + name + "-" + System.identityHashCode(c));
        when(c.getName()).thenReturn(name);
        IRPGeneralization gen = mock(IRPGeneralization.class);
        when(gen.getUserDefinedMetaClass()).thenReturn(REF_FUNCTION);
        doReturn(collectionOf(gen)).when(c).getGeneralizations();
        return c;
    }

    /** A class named {@code name} that is NOT a reference (a definition, or any other box). */
    private static IRPClass plainBox(String name) {
        IRPClass c = mock(IRPClass.class);
        when(c.getGUID()).thenReturn("GUID-" + name + "-" + System.identityHashCode(c));
        when(c.getName()).thenReturn(name);
        doReturn(collectionOf()).when(c).getGeneralizations();
        return c;
    }

    /** Flow Port named {@code name} owned by {@code owner}, redefining {@code targets}. */
    private static IRPSysMLPort portOf(String name, IRPModelElement owner, IRPModelElement... targets) {
        IRPSysMLPort p = mock(IRPSysMLPort.class);
        when(p.getGUID()).thenReturn("GUID-" + name + "-" + System.identityHashCode(p));
        when(p.getName()).thenReturn(name);
        when(p.getUserDefinedMetaClass()).thenReturn("Flow Port");
        when(p.getOwner()).thenReturn(owner);
        doReturn(collectionOf((Object[]) targets)).when(p).getRedefines();
        return p;
    }

    /** Flow named {@code name} whose end 1 is {@code end1} and end 2 {@code end2}. */
    private static IRPFlow flowNamed(String name, IRPModelElement end1, IRPModelElement end2) {
        IRPFlow f = mock(IRPFlow.class);
        when(f.getGUID()).thenReturn("GUID-flow-" + name + "-" + System.identityHashCode(f));
        when(f.getName()).thenReturn(name);
        when(f.getEnd1SysMLPort()).thenReturn(end1 instanceof IRPSysMLPort ? (IRPSysMLPort) end1 : null);
        when(f.getEnd2SysMLPort()).thenReturn(end2 instanceof IRPSysMLPort ? (IRPSysMLPort) end2 : null);
        when(f.getEnd1()).thenReturn(end1);
        when(f.getEnd2()).thenReturn(end2);
        return f;
    }

    private static IRPGraphEdge trait(IRPFlow flow, IRPGraphElement source, IRPGraphElement target) {
        IRPGraphEdge e = mock(IRPGraphEdge.class);
        when(e.getModelObject()).thenReturn(flow);
        when(e.getSource()).thenReturn(source);
        when(e.getTarget()).thenReturn(target);
        return e;
    }

    /**
     * The usual scene: reference R1 drawn in a diagram owned by {@code pkg},
     * with inherited p_2 (of mother Check) and redefined p_2 both drawn; box X
     * with port q; the definition flow f1 (Check.p_2 -> X.q, "Functional Flow",
     * toEnd2, one conveyed item, one New Term + one plain stereotype, a
     * description) drawn from R1's inherited p_2 to X.q. The copy that
     * {@code pkg.addNewAggr("Functional Flow", "R1_p_2_X_q")} returns is drawn by
     * completeRelations (first read: no trait; then one).
     */
    private static final class Scene {
        final IRPApplication app = mock(IRPApplication.class);
        final IRPDiagram diagram = mock(IRPDiagram.class);
        final IRPModelElement pkg = mock(IRPModelElement.class);
        final IRPClass mother = plainBox("Check");
        final IRPClass r1 = referenceBox("R1");
        final IRPClass x = plainBox("X");
        final IRPGraphElement node = mock(IRPGraphElement.class);
        final IRPGraphElement xNode = mock(IRPGraphElement.class);
        final IRPSysMLPort inherited = portOf("p_2", mother);
        final IRPSysMLPort redefined = portOf("p_2", r1, inherited);
        final IRPSysMLPort q = portOf("q", x);
        final IRPGraphNode gInh = boxGraphic(node, inherited, "100,200");
        final IRPGraphNode gRed = boxGraphic(node, redefined, "10,10");
        final IRPGraphNode gQ = boxGraphic(xNode, q, "400,200");
        final IRPModelElement item = mock(IRPModelElement.class);
        final IRPStereotype newTerm = mock(IRPStereotype.class);
        final IRPStereotype plain = mock(IRPStereotype.class);
        final IRPFlow flow;
        final IRPGraphEdge edge;
        final IRPFlow copy = mock(IRPFlow.class);
        final IRPGraphEdge copyEdge;
        final IRPCollection removal = mock(IRPCollection.class);
        final IRPCollection oldTrait = mock(IRPCollection.class);
        final IRPCollection ends = mock(IRPCollection.class);

        Scene() {
            this(1);
        }

        /** {@code portEnd}: which end of f1 names Check.p_2 (1: p_2 -> q; 2: q -> p_2). */
        Scene(int portEnd) {
            when(node.getModelObject()).thenReturn(r1);
            when(node.getDiagram()).thenReturn(diagram);
            when(xNode.getModelObject()).thenReturn(x);
            when(diagram.getOwner()).thenReturn(pkg);
            doReturn(collectionOf(redefined)).when(r1).getPorts();
            flow = portEnd == 1 ? flowNamed("f1", inherited, q) : flowNamed("f1", q, inherited);
            when(flow.getUserDefinedMetaClass()).thenReturn("Functional Flow");
            when(flow.getDirection()).thenReturn("toEnd2");
            when(flow.getDescription()).thenReturn("moves water");
            doReturn(collectionOf(item)).when(flow).getConveyed();
            when(newTerm.getIsNewTerm()).thenReturn(1);
            when(newTerm.getName()).thenReturn("Functional Flow");
            when(plain.getIsNewTerm()).thenReturn(0);
            when(plain.getName()).thenReturn("Critical");
            doReturn(collectionOf(newTerm, plain)).when(flow).getStereotypes();
            edge = portEnd == 1 ? trait(flow, gInh, gQ) : trait(flow, gQ, gInh);
            doReturn(collectionOf(gInh, gRed, gQ, edge)).when(diagram).getGraphicalElements();
            doReturn(collectionOf(diagram)).when(flow).getReferences();
            doReturn(collectionOf(edge)).when(diagram).getCorrespondingGraphicElements(flow);
            when(copy.getName()).thenReturn("R1_p_2_X_q");
            copyEdge = portEnd == 1 ? trait(copy, gRed, gQ) : trait(copy, gQ, gRed);
            doReturn(collectionOf(), collectionOf(copyEdge)).when(diagram).getCorrespondingGraphicElements(copy);
            when(pkg.addNewAggr(any(), any())).thenReturn(copy);
            when(app.createNewCollection()).thenReturn(removal, oldTrait, ends);
        }

        int clean() {
            return RedefinedPortsService.hideRedefinedPorts(app, node);
        }
    }

    @Test
    void hideRedefinedPorts_definitionFlow_isCopiedOntoTheRedefinedPort_definitionUntouched_insideTheTransaction() {
        Scene s = new Scene();

        assertEquals(1, s.clean());

        // The copy: same New Term, named and labelled after the two boxes, ends then direction.
        verify(s.pkg).addNewAggr("Functional Flow", "R1_p_2_X_q");
        InOrder order = inOrder(s.app, s.copy, s.diagram);
        order.verify(s.app).startUndoTransaction();
        order.verify(s.copy).setEnd1(s.redefined);
        order.verify(s.copy).setEnd2(s.q);
        order.verify(s.copy).setDirection("toEnd2");
        order.verify(s.diagram).removeGraphElements(s.removal);
        order.verify(s.app).endUndoTransaction();
        verify(s.copy).addConveyed(s.item);
        verify(s.copy).addSpecificStereotype(s.plain);
        verify(s.copy, never()).addSpecificStereotype(s.newTerm);
        verify(s.copy).setDescription("moves water");
        verify(s.copy).setDisplayName("R1_p_2_X_q");
        // The definition flow is never touched.
        verify(s.flow, never()).setEnd1(any());
        verify(s.flow, never()).setEnd2(any());
        verify(s.flow, never()).setName(any());
        verify(s.flow, never()).deleteFromProject();
        // Rhapsody draws the copy between the redefined port and the other end; the inherited port goes.
        verify(s.ends).addGraphicalItem(s.gRed);
        verify(s.ends).addGraphicalItem(s.gQ);
        verify(s.diagram).completeRelations(s.ends, 0);
        verify(s.diagram, never()).addNewEdgeForElement(any(), any(), anyInt(), anyInt(), any(), anyInt(), anyInt());
        verify(s.removal).addGraphicalItem(s.gInh);
    }

    /** The definition flow is also drawn on the mother's box elsewhere: still copied here, still kept. */
    @Test
    void hideRedefinedPorts_definitionFlowAlsoDrawnOnAnotherBox_isCopiedToo() {
        Scene s = new Scene();
        IRPDiagram other = mock(IRPDiagram.class);
        IRPGraphElement motherBox = mock(IRPGraphElement.class);
        when(motherBox.getModelObject()).thenReturn(s.mother);
        IRPGraphNode gInhElsewhere = boxGraphic(motherBox, s.inherited, "5,5");
        IRPGraphEdge edgeElsewhere = trait(s.flow, gInhElsewhere, boxGraphic(mock(IRPGraphElement.class), s.q, "9,9"));
        doReturn(collectionOf(s.diagram, other)).when(s.flow).getReferences();
        doReturn(collectionOf(edgeElsewhere)).when(other).getCorrespondingGraphicElements(s.flow);

        assertEquals(1, s.clean());

        verify(s.pkg).addNewAggr("Functional Flow", "R1_p_2_X_q");
        verify(s.copy).setEnd1(s.redefined);
        verify(s.flow, never()).setEnd1(any());
        verify(s.flow, never()).setEnd2(any());
        verify(s.diagram).completeRelations(s.ends, 0);
        verify(other, never()).removeGraphElements(any());
    }

    @Test
    void hideRedefinedPorts_copyKeepsTheOrientation_whenThePortIsEnd2() {
        Scene s = new Scene(2);

        assertEquals(1, s.clean());

        verify(s.pkg).addNewAggr("Functional Flow", "X_q_R1_p_2");
        verify(s.copy).setEnd1(s.q);
        verify(s.copy).setEnd2(s.redefined);
        verify(s.copy).setDisplayName("X_q_R1_p_2");
        verify(s.ends).addGraphicalItem(s.gQ);
        verify(s.ends).addGraphicalItem(s.gRed);
        verify(s.diagram).completeRelations(s.ends, 0);
    }

    /** A flow already joining the redefined port and the other end (either orientation): no second copy. */
    @Test
    void hideRedefinedPorts_copyNotRecreated_whenAFlowAlreadyJoinsTheMappedEnds() {
        Scene s = new Scene();
        IRPFlow existing = flowNamed("R1_p_2_X_q", s.q, s.redefined);
        doReturn(collectionOf(existing, mock(IRPClass.class))).when(s.redefined).getReferences();
        doReturn(collectionOf(), collectionOf(trait(existing, s.gRed, s.gQ)))
                .when(s.diagram).getCorrespondingGraphicElements(existing);

        assertEquals(1, s.clean());

        verify(s.pkg, never()).addNewAggr(any(), any());
        verify(existing, never()).setEnd1(any());
        verify(existing, never()).setEnd2(any());
        verify(s.flow, never()).setEnd1(any());
        verify(s.diagram).completeRelations(s.ends, 0);
        verify(s.removal).addGraphicalItem(s.gInh);
    }

    /** A flow between two inherited ports of the SAME box (Check.p -> Check.r on R1): one copy, both ends mapped. */
    @Test
    void hideRedefinedPorts_flowBetweenTwoInheritedPortsOfTheSameBox_oneCopyWithBothEndsMapped() {
        Scene s = new Scene();
        IRPSysMLPort inheritedR = portOf("r", s.mother);
        IRPSysMLPort redefinedR = portOf("r", s.r1, inheritedR);
        doReturn(collectionOf(s.redefined, redefinedR)).when(s.r1).getPorts();
        IRPGraphNode gInhR = boxGraphic(s.node, inheritedR, "100,300");
        IRPGraphNode gRedR = boxGraphic(s.node, redefinedR, "20,20");
        IRPFlow loop = flowNamed("loop", s.inherited, inheritedR);
        when(loop.getUserDefinedMetaClass()).thenReturn("Functional Flow");
        IRPGraphEdge loopEdge = trait(loop, s.gInh, gInhR);
        doReturn(collectionOf(s.gInh, s.gRed, gInhR, gRedR, loopEdge)).when(s.diagram).getGraphicalElements();
        doReturn(collectionOf(loopEdge)).when(s.diagram).getCorrespondingGraphicElements(loop);
        IRPFlow copy = mock(IRPFlow.class);
        when(s.pkg.addNewAggr(any(), any())).thenReturn(copy);
        doReturn(collectionOf(), collectionOf(trait(copy, s.gRed, gRedR)))
                .when(s.diagram).getCorrespondingGraphicElements(copy);

        assertEquals(2, s.clean());

        verify(s.pkg, times(1)).addNewAggr(any(), any());
        verify(s.pkg).addNewAggr("Functional Flow", "R1_p_2_R1_r");
        verify(copy).setEnd1(s.redefined);
        verify(copy).setEnd2(redefinedR);
        verify(loop, never()).setEnd1(any());
        verify(loop, never()).setEnd2(any());
        verify(s.ends).addGraphicalItem(s.gRed);
        verify(s.ends).addGraphicalItem(gRedR);
        verify(s.removal).addGraphicalItem(s.gInh);
        verify(s.removal).addGraphicalItem(gInhR);
    }

    /**
     * R1.p_2 to R2.p_2, same mother, both ends of the definition flow name
     * Check.p_2. Cleaning R1 copies it as R1_R2 (R1.p_2 -> Check.p_2 still
     * drawn under R2); cleaning R2 then finds a flow owned by a reference and
     * reconnects its end 2 to R2.p_2. One copy, the definition flow untouched.
     */
    @Test
    void hideRedefinedPorts_twoReferencesOfTheSameMother_firstNodeCopies_secondNodeReconnectsTheCopy() {
        Scene s = new Scene();
        IRPClass r2 = referenceBox("R2");
        IRPGraphElement node2 = mock(IRPGraphElement.class);
        when(node2.getModelObject()).thenReturn(r2);
        when(node2.getDiagram()).thenReturn(s.diagram);
        IRPSysMLPort r2Redefined = portOf("p_2", r2, s.inherited);
        doReturn(collectionOf(r2Redefined)).when(r2).getPorts();
        IRPGraphNode gInh2 = boxGraphic(node2, s.inherited, "400,200");
        IRPGraphNode gRed2 = boxGraphic(node2, r2Redefined, "30,30");
        IRPFlow d = flowNamed("f1", s.inherited, s.inherited);
        when(d.getUserDefinedMetaClass()).thenReturn("Functional Flow");
        IRPGraphEdge dEdge = trait(d, s.gInh, gInh2);
        doReturn(collectionOf(s.gInh, s.gRed, gInh2, gRed2, dEdge)).when(s.diagram).getGraphicalElements();
        doReturn(collectionOf(s.diagram)).when(d).getReferences();
        doReturn(collectionOf(dEdge)).when(s.diagram).getCorrespondingGraphicElements(d);
        IRPFlow c = flowNamed("R1_p_2_R2_p_2", s.redefined, s.inherited);   // the copy, as Rhapsody will report it
        when(s.pkg.addNewAggr(any(), any())).thenReturn(c);
        IRPGraphEdge cEdge = trait(c, s.gRed, gInh2);
        doReturn(collectionOf(), collectionOf(cEdge)).when(s.diagram).getCorrespondingGraphicElements(c);
        when(s.app.createNewCollection()).thenReturn(mock(IRPCollection.class), mock(IRPCollection.class),
                mock(IRPCollection.class), mock(IRPCollection.class), mock(IRPCollection.class), s.ends);

        // Node R1: copy.
        assertEquals(1, s.clean());
        verify(s.pkg).addNewAggr("Functional Flow", "R1_p_2_R2_p_2");
        verify(c).setEnd1(s.redefined);
        verify(c).setEnd2(s.inherited);
        verify(d, never()).setEnd1(any());
        verify(d, never()).setEnd2(any());

        // Node R2: the diagram now shows the copy's trait from R1.p_2 to R2's inherited p_2.
        doReturn(collectionOf(s.gRed, gInh2, gRed2, cEdge)).when(s.diagram).getGraphicalElements();
        doReturn(collectionOf(s.diagram)).when(c).getReferences();
        doReturn(collectionOf(cEdge), collectionOf(), collectionOf(trait(c, s.gRed, gRed2)))
                .when(s.diagram).getCorrespondingGraphicElements(c);

        assertEquals(1, RedefinedPortsService.hideRedefinedPorts(s.app, node2));

        verify(s.pkg, times(1)).addNewAggr(any(), any());
        verify(c).setEnd2(r2Redefined);
        verify(c, never()).setEnd1(r2Redefined);
        verify(d, never()).setEnd1(any());
        verify(d, never()).setEnd2(any());
        verify(s.ends).addGraphicalItem(s.gRed);
        verify(s.ends).addGraphicalItem(gRed2);
        verify(s.diagram).completeRelations(s.ends, 0);
    }

    /** A reference flow (an end owned by a reference) drawn on p under another box too is left as is. */
    @Test
    void hideRedefinedPorts_referenceFlowAlsoDrawnOnAnotherBox_isLeftAsIs() {
        Scene s = new Scene();
        IRPClass r2 = referenceBox("R2");
        IRPSysMLPort r2Port = portOf("q", r2);
        IRPFlow ref = flowNamed("R2_R1", r2Port, s.inherited);
        IRPGraphNode gR2 = boxGraphic(mock(IRPGraphElement.class), r2Port, "400,200");
        IRPGraphEdge edge = trait(ref, gR2, s.gInh);
        doReturn(collectionOf(s.gInh, s.gRed, gR2, edge)).when(s.diagram).getGraphicalElements();
        IRPDiagram other = mock(IRPDiagram.class);
        IRPGraphElement motherBox = mock(IRPGraphElement.class);
        when(motherBox.getModelObject()).thenReturn(s.mother);
        IRPGraphEdge elsewhere = trait(ref, boxGraphic(mock(IRPGraphElement.class), r2Port, "1,1"),
                boxGraphic(motherBox, s.inherited, "5,5"));
        doReturn(collectionOf(s.diagram, other)).when(ref).getReferences();
        doReturn(collectionOf(edge)).when(s.diagram).getCorrespondingGraphicElements(ref);
        doReturn(collectionOf(elsewhere)).when(other).getCorrespondingGraphicElements(ref);

        assertEquals(1, s.clean());

        verify(ref, never()).setEnd1(any());
        verify(ref, never()).setEnd2(any());
        verify(ref, never()).deleteFromProject();
        verify(s.pkg, never()).addNewAggr(any(), any());
        verify(s.diagram, never()).completeRelations(any(), anyInt());
    }

    /**
     * A reference flow drawn only here is reconnected, like before. The user's
     * Rhapsody log: once the flow's end changes, Rhapsody drops the old trait
     * and any call on its proxy fails ("Membre introuvable"). The redraw must
     * not abort: completeRelations still draws the flow.
     */
    @Test
    void hideRedefinedPorts_referenceFlowOnTheInheritedPort_isReconnected_evenWhenTheOldTraitIsGone() {
        Scene s = new Scene();
        IRPClass r2 = referenceBox("R2");
        IRPSysMLPort r2Port = portOf("q", r2);
        IRPGraphNode gR2 = boxGraphic(mock(IRPGraphElement.class), r2Port, "400,200");
        IRPFlow ref = flowNamed("R1_R2", s.inherited, r2Port);
        boolean[] endChanged = { false };
        doAnswer(inv -> { endChanged[0] = true; return null; }).when(ref).setEnd1(any());
        IRPGraphEdge oldTrait = mock(IRPGraphEdge.class);
        when(oldTrait.getModelObject()).thenReturn(ref);
        when(oldTrait.getSource()).thenAnswer(inv -> {
            if (endChanged[0]) throw new RuntimeException("Membre introuvable.");
            return s.gInh;
        });
        when(oldTrait.getTarget()).thenAnswer(inv -> {
            if (endChanged[0]) throw new RuntimeException("Membre introuvable.");
            return gR2;
        });
        IRPCollection withTrait = collectionOf(oldTrait);
        IRPCollection noTrait = collectionOf();
        doReturn(collectionOf(s.gInh, s.gRed, gR2, oldTrait)).when(s.diagram).getGraphicalElements();
        doReturn(collectionOf(s.diagram)).when(ref).getReferences();
        when(s.diagram.getCorrespondingGraphicElements(ref)).thenAnswer(inv -> endChanged[0] ? noTrait : withTrait);

        assertEquals(1, s.clean());

        verify(ref).setEnd1(s.redefined);
        verify(ref, never()).setEnd2(any());
        verify(s.pkg, never()).addNewAggr(any(), any());
        verify(s.ends).addGraphicalItem(s.gRed);
        verify(s.ends).addGraphicalItem(gR2);
        verify(s.diagram).completeRelations(s.ends, 0);
    }

    @Test
    void hideRedefinedPorts_copyFallsBackToTheFlowMetaclass_whenTheNewTermIsUnknown() {
        Scene s = new Scene();
        when(s.flow.getUserDefinedMetaClass()).thenReturn(null);

        s.clean();

        verify(s.pkg).addNewAggr("Flow", "R1_p_2_X_q");
    }

    @Test
    void hideRedefinedPorts_copyOwnedByTheDefinitionFlowsOwner_whenTheDiagramHasNone() {
        Scene s = new Scene();
        when(s.diagram.getOwner()).thenReturn(null);
        IRPModelElement flowOwner = mock(IRPModelElement.class);
        when(s.flow.getOwner()).thenReturn(flowOwner);
        when(flowOwner.addNewAggr(any(), any())).thenReturn(s.copy);

        s.clean();

        verify(flowOwner).addNewAggr("Functional Flow", "R1_p_2_X_q");
        verify(s.pkg, never()).addNewAggr(any(), any());
    }

    /** "R1_p_2_X_q" already exists under the owner (same metaclass, or any nested element): "R1_p_2_X_q_1". */
    @Test
    void hideRedefinedPorts_copyNameIsMadeUniqueUnderTheOwner() {
        Scene s = new Scene();
        when(s.pkg.findNestedElement("R1_p_2_X_q", "Functional Flow")).thenReturn(mock(IRPFlow.class));
        IRPModelElement homonym = mock(IRPModelElement.class);
        when(homonym.getName()).thenReturn("R1_p_2_X_q_1");
        doReturn(collectionOf(homonym)).when(s.pkg).getNestedElements();

        s.clean();

        verify(s.pkg).addNewAggr("Functional Flow", "R1_p_2_X_q_2");
        verify(s.copy).setDisplayName("R1_p_2_X_q_2");
    }

    /** The user's example: "R1_p_2_X_q" taken once -> "R1_p_2_X_q_1". */
    @Test
    void hideRedefinedPorts_copyNameSuffixedWith1_onTheFirstCollision() {
        Scene s = new Scene();
        when(s.pkg.findNestedElement("R1_p_2_X_q", "Functional Flow")).thenReturn(mock(IRPFlow.class));

        s.clean();

        verify(s.pkg).addNewAggr("Functional Flow", "R1_p_2_X_q_1");
        verify(s.copy).setDisplayName("R1_p_2_X_q_1");
    }

    /** A flow ending on a class itself (GenericFlow): that class is the box, not its package. */
    @Test
    void hideRedefinedPorts_copyNamedAfterTheClass_whenTheOtherEndIsAClass() {
        Scene s = new Scene();
        IRPClass y = plainBox("Y");
        when(y.getOwner()).thenReturn(mock(IRPModelElement.class));
        IRPGraphNode gY = boxGraphic(null, y, "500,100");
        IRPFlow toClass = flowNamed("f2", s.inherited, y);
        when(toClass.getUserDefinedMetaClass()).thenReturn("Functional Flow");
        IRPGraphEdge edge = trait(toClass, s.gInh, gY);
        doReturn(collectionOf(s.gInh, s.gRed, gY, edge)).when(s.diagram).getGraphicalElements();
        doReturn(collectionOf(edge)).when(s.diagram).getCorrespondingGraphicElements(toClass);
        doReturn(collectionOf(), collectionOf(trait(s.copy, s.gRed, gY)))
                .when(s.diagram).getCorrespondingGraphicElements(s.copy);

        assertEquals(1, s.clean());

        verify(s.pkg).addNewAggr("Functional Flow", "R1_p_2_Y");
        verify(s.copy).setEnd1(s.redefined);
        verify(s.copy).setEnd2(y);
        verify(toClass, never()).setEnd1(any());
    }

    @Test
    void sanitizeName_keepsLettersAccentsDigitsUnderscores_neverEmpty_neverStartsWithADigit() {
        assertEquals("R_1_X", RedefinedPortsService.sanitizeName("R 1-X"));
        assertEquals("_1R_X", RedefinedPortsService.sanitizeName("1R_X"));
        assertEquals("Pompe_\u00e0_eau_Vanne", RedefinedPortsService.sanitizeName("Pompe \u00e0 eau_Vanne"));
        assertEquals("flow", RedefinedPortsService.sanitizeName(""));
        assertEquals("flow", RedefinedPortsService.sanitizeName(null));
    }

    /** The copy exists but could not be drawn: the inherited port stays drawn, so the flow stays visible. */
    @Test
    void hideRedefinedPorts_copyNotDrawn_inheritedPortKept() {
        Scene s = new Scene();
        doReturn(collectionOf()).when(s.diagram).getCorrespondingGraphicElements(s.copy);

        assertEquals(0, s.clean());

        verify(s.pkg).addNewAggr("Functional Flow", "R1_p_2_X_q");
        verify(s.diagram).completeRelations(s.ends, 0);
        verify(s.diagram).addNewEdgeForElement(eq(s.copy), eq(s.gRed), anyInt(), anyInt(), eq(s.gQ), anyInt(), anyInt());
        verify(s.removal, never()).addGraphicalItem(any());
        verify(s.diagram, never()).removeGraphElements(any());
    }

    /** completeRelations drew nothing: the explicit connector is the fallback. */
    @Test
    void hideRedefinedPorts_copyNotDrawnByCompleteRelations_fallsBackToAnExplicitConnector() {
        Scene s = new Scene();
        doReturn(collectionOf(), collectionOf(), collectionOf(s.copyEdge))
                .when(s.diagram).getCorrespondingGraphicElements(s.copy);

        assertEquals(1, s.clean());

        verify(s.diagram).completeRelations(s.ends, 0);
        verify(s.diagram).addNewEdgeForElement(eq(s.copy), eq(s.gRed), anyInt(), anyInt(), eq(s.gQ), anyInt(), anyInt());
        verify(s.removal).addGraphicalItem(s.gInh);
    }

    @Test
    void planTransfer_traitNotOnAReplacedPort_isLeftAsIs() {
        Scene s = new Scene();
        IRPGraphElement elsewhere = boxGraphic(mock(IRPGraphElement.class), s.q, "1,1");
        IRPGraphEdge edge = trait(s.flow, elsewhere, s.gQ);

        RedefinedPortsService.FlowTransfer t = RedefinedPortsService.planTransfer(s.flow, edge,
                List.of(s.gInh), List.of(s.gRed), s.r1);

        assertEquals(RedefinedPortsService.FlowKind.LEFT, t.kind);
        assertEquals("not attached to this port", t.reason);
    }

    @Test
    void hideRedefinedPorts_redefinedPortAlone_isLeftAsIs() {
        IRPApplication app = mock(IRPApplication.class);
        IRPDiagram diagram = mock(IRPDiagram.class);
        IRPGraphElement node = nodeOn(diagram);
        IRPGraphElement[] pair = inheritedAndRedefinedUnder(node);
        doReturn(collectionOf(pair[1])).when(diagram).getGraphicalElements();
        when(app.createNewCollection()).thenReturn(mock(IRPCollection.class));

        assertEquals(0, RedefinedPortsService.hideRedefinedPorts(app, node));

        verify(pair[1], never()).setGraphicalProperty(anyString(), anyString());
        verify(diagram, never()).removeGraphElements(any());
        verify(diagram, never()).addNewNodeForElement(any(), anyInt(), anyInt(), anyInt(), anyInt());
    }

    @Test
    void hideRedefinedPorts_inheritedPortAlone_drawsTheRedefinedPortAtItsPlace() {
        IRPApplication app = mock(IRPApplication.class);
        IRPDiagram diagram = mock(IRPDiagram.class);
        IRPClass reference = mock(IRPClass.class);
        when(reference.getGUID()).thenReturn("GUID-ref-draw");
        IRPGraphElement node = mock(IRPGraphElement.class);
        when(node.getModelObject()).thenReturn(reference);
        when(node.getDiagram()).thenReturn(diagram);

        IRPSysMLPort inherited = port("P1", "GUID-inh-draw");
        when(inherited.getUserDefinedMetaClass()).thenReturn("Flow Port");
        doReturn(collectionOf()).when(inherited).getRedefines();
        IRPSysMLPort redefined = port("P1", "GUID-red-draw");
        doReturn(collectionOf(inherited)).when(redefined).getRedefines();
        when(redefined.getOwner()).thenReturn(reference);
        doReturn(collectionOf(redefined)).when(reference).getPorts();

        IRPGraphElement inhGraphic = graphicUnder(node, inherited);
        doReturn(property("120,340")).when(inhGraphic).getGraphicalProperty("Position");
        doReturn(property("12")).when(inhGraphic).getGraphicalProperty("Width");
        doReturn(property("14")).when(inhGraphic).getGraphicalProperty("Height");
        doReturn(collectionOf(inhGraphic)).when(diagram).getGraphicalElements();
        IRPCollection removal = mock(IRPCollection.class);
        when(app.createNewCollection()).thenReturn(removal);
        when(diagram.addNewNodeForElement(redefined, 120, 340, 12, 14)).thenReturn(mock(IRPGraphNode.class));

        assertEquals(1, RedefinedPortsService.hideRedefinedPorts(app, node));

        verify(diagram).addNewNodeForElement(redefined, 120, 340, 12, 14);
        verify(removal).addGraphicalItem(inhGraphic);
        verify(diagram).removeGraphElements(removal);
    }

    @Test
    void hideRedefinedPorts_inheritedPortAlone_keptWhenTheRedefinedPortCannotBeDrawn() {
        IRPApplication app = mock(IRPApplication.class);
        IRPDiagram diagram = mock(IRPDiagram.class);
        IRPClass reference = mock(IRPClass.class);
        when(reference.getGUID()).thenReturn("GUID-ref-nodraw");
        IRPGraphElement node = mock(IRPGraphElement.class);
        when(node.getModelObject()).thenReturn(reference);
        when(node.getDiagram()).thenReturn(diagram);

        IRPSysMLPort inherited = port("P1", "GUID-inh-nodraw");
        when(inherited.getUserDefinedMetaClass()).thenReturn("Flow Port");
        doReturn(collectionOf()).when(inherited).getRedefines();
        IRPSysMLPort redefined = port("P1", "GUID-red-nodraw");
        doReturn(collectionOf(inherited)).when(redefined).getRedefines();
        when(redefined.getOwner()).thenReturn(reference);
        doReturn(collectionOf(redefined)).when(reference).getPorts();

        IRPGraphElement inhGraphic = graphicUnder(node, inherited);
        doReturn(property("120,340")).when(inhGraphic).getGraphicalProperty("Position");
        doReturn(collectionOf(inhGraphic)).when(diagram).getGraphicalElements();
        when(app.createNewCollection()).thenReturn(mock(IRPCollection.class));
        when(diagram.addNewNodeForElement(any(), anyInt(), anyInt(), anyInt(), anyInt())).thenReturn(null);

        assertEquals(0, RedefinedPortsService.hideRedefinedPorts(app, node));

        verify(diagram, never()).removeGraphElements(any());
    }
}
