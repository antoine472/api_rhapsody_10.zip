package test.unittest;

import org.junit.jupiter.api.Test;

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
import org.mockito.InOrder;
import com.telelogic.rhapsody.core.IRPStereotype;
import com.telelogic.rhapsody.core.IRPSysMLPort;

import tools.RedefinedPortsService;
import tools.RedefinedPortsService.PortPlan;

import static test.unittest.RhpTestMocks.collectionOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires purs (sans instance Rhapsody vivante) pour la copie
 * <b>plugin</b> de {@link RedefinedPortsService} (artefact 2 — Toolkit,
 * commande <em>Update Redefined Ports</em>). Complète les tests équivalents
 * du côté écouteur (artefact 1, projet {@code SafranProfileListener}) : cette
 * copie contient en plus {@link RedefinedPortsService.PortPlan}/
 * {@code computePlan} (dry-run, REQ-5) et une synchronisation de propriétés
 * plus complète (type, direction, <b>description</b>, stéréotypes
 * <b>ajout ET retrait</b> — REQ-3).
 *
 * <p>Comme côté écouteur, les types OpenAPI Rhapsody utilisés
 * ({@code IRPClass}, {@code IRPGeneralization}, {@code IRPSysMLPort}, ...)
 * sont de simples interfaces Java, mockées avec Mockito. Les stubs de
 * collection utilisent {@code doReturn(...).when(mock).method()} plutôt que
 * {@code when(mock.method()).thenReturn(...)} : {@link RhpTestMocks#collectionOf}
 * stubant lui-même un mock, l'appeler comme argument de {@code .thenReturn(...)}
 * s'intercalerait entre un {@code when(...)} externe et son
 * {@code .thenReturn(...)}, corrompant l'état de stubbing de Mockito
 * ({@code UnfinishedStubbingException}).</p>
 */
class RedefinedPortsServiceTest {

    private static final String REF_FUNCTION = "References Function";
    private static final String REF_LOGICAL_SYSTEM = "References Logical System";

    // ==================================================================
    // isReferenceClass
    // ==================================================================

    @Test
    void isReferenceClass_trueForReferencesFunctionGeneralization() {
        IRPGeneralization gen = mock(IRPGeneralization.class);
        when(gen.getUserDefinedMetaClass()).thenReturn(REF_FUNCTION);

        IRPClass element = mock(IRPClass.class);
        doReturn(collectionOf(gen)).when(element).getGeneralizations();

        assertTrue(RedefinedPortsService.isReferenceClass(element));
    }

    @Test
    void isReferenceClass_trueForReferencesLogicalSystemGeneralization() {
        IRPGeneralization gen = mock(IRPGeneralization.class);
        when(gen.getUserDefinedMetaClass()).thenReturn(REF_LOGICAL_SYSTEM);

        IRPClass element = mock(IRPClass.class);
        doReturn(collectionOf(gen)).when(element).getGeneralizations();

        assertTrue(RedefinedPortsService.isReferenceClass(element));
    }

    @Test
    void isReferenceClass_falseForNonClassElement() {
        IRPModelElement element = mock(IRPModelElement.class);
        assertFalse(RedefinedPortsService.isReferenceClass(element));
    }

    @Test
    void isReferenceClass_falseWhenApiThrows() {
        IRPClass element = mock(IRPClass.class);
        when(element.getGeneralizations()).thenThrow(new RuntimeException("COM error"));

        assertFalse(RedefinedPortsService.isReferenceClass(element));
    }

    // ==================================================================
    // redefinePorts (mutation)
    // ==================================================================

    @Test
    void redefinePorts_returnsTrueForNonClassElement() {
        IRPModelElement element = mock(IRPModelElement.class);
        assertTrue(RedefinedPortsService.redefinePorts(element));
    }

    @Test
    void redefinePorts_returnsFalseWhenMotherNotResolvedYet() {
        IRPGeneralization gen = mock(IRPGeneralization.class);
        when(gen.getUserDefinedMetaClass()).thenReturn(REF_FUNCTION);
        when(gen.getBaseClass()).thenReturn(null);

        IRPClass reference = mock(IRPClass.class);
        doReturn(collectionOf(gen)).when(reference).getGeneralizations();

        assertFalse(RedefinedPortsService.redefinePorts(reference));
    }

    @Test
    void redefinePorts_returnsTrueWhenMotherIsNotAClass() {
        IRPClassifier motherNotAClass = mock(IRPClassifier.class);
        when(motherNotAClass.getName()).thenReturn("NotAClass");

        IRPGeneralization gen = mock(IRPGeneralization.class);
        when(gen.getUserDefinedMetaClass()).thenReturn(REF_FUNCTION);
        when(gen.getBaseClass()).thenReturn(motherNotAClass);

        IRPClass reference = mock(IRPClass.class);
        doReturn(collectionOf(gen)).when(reference).getGeneralizations();

        assertTrue(RedefinedPortsService.redefinePorts(reference));
    }

    @Test
    void redefinePorts_createsAndMirrorsPortsFromMother() {
        IRPClass mother = mock(IRPClass.class);
        when(mother.getGUID()).thenReturn("GUID-mother");

        IRPClass reference = mock(IRPClass.class);
        when(reference.getGUID()).thenReturn("GUID-reference");
        when(reference.getName()).thenReturn("MyReference");
        doReturn(collectionOf()).when(reference).getPorts(); // no existing children

        IRPGeneralization gen = mock(IRPGeneralization.class);
        when(gen.getUserDefinedMetaClass()).thenReturn(REF_FUNCTION);
        when(gen.getBaseClass()).thenReturn(mother);
        doReturn(collectionOf(gen)).when(reference).getGeneralizations();

        IRPClassifier type1 = mock(IRPClassifier.class);
        when(type1.getGUID()).thenReturn("GUID-type1");
        IRPSysMLPort port1 = mock(IRPSysMLPort.class);
        when(port1.getName()).thenReturn("P1");
        when(port1.getGUID()).thenReturn("GUID-p1");
        when(port1.getType()).thenReturn(type1);
        when(port1.getPortDirection()).thenReturn("in");
        doReturn(collectionOf()).when(port1).getStereotypes();

        doReturn(collectionOf(port1)).when(mother).getPorts();

        when(reference.findNestedElement("P1", "Flow Port")).thenReturn(null);

        IRPSysMLPort createdPort1 = mock(IRPSysMLPort.class);
        when(createdPort1.getGUID()).thenReturn("GUID-created-p1");
        doReturn(collectionOf()).when(createdPort1).getStereotypes();
        when(reference.addNewAggr("Flow Port", "P1")).thenReturn(createdPort1);

        boolean result = RedefinedPortsService.redefinePorts(reference);

        assertTrue(result);
        verify(reference).addNewAggr("Flow Port", "P1");
        verify(createdPort1, atLeastOnce()).setType(type1);
        verify(createdPort1, atLeastOnce()).setPortDirection("in");
        verify(createdPort1, atLeastOnce()).addRedefines(port1);
    }

    @Test
    void redefinePorts_isIdempotent_reusesExistingRedefinedPortInsteadOfCreating() {
        IRPClass mother = mock(IRPClass.class);
        when(mother.getGUID()).thenReturn("GUID-mother-2");

        IRPSysMLPort motherPort = mock(IRPSysMLPort.class);
        when(motherPort.getName()).thenReturn("P1");
        when(motherPort.getGUID()).thenReturn("GUID-motherport");
        when(motherPort.getOwner()).thenReturn(mother);
        doReturn(collectionOf()).when(motherPort).getStereotypes();
        doReturn(collectionOf(motherPort)).when(mother).getPorts();

        IRPSysMLPort existingChildPort = mock(IRPSysMLPort.class);
        when(existingChildPort.getName()).thenReturn("P1");
        when(existingChildPort.getGUID()).thenReturn("GUID-existing");
        doReturn(collectionOf(motherPort)).when(existingChildPort).getRedefines();
        doReturn(collectionOf()).when(existingChildPort).getStereotypes();

        IRPClass reference = mock(IRPClass.class);
        when(reference.getGUID()).thenReturn("GUID-reference-2");
        when(reference.getName()).thenReturn("MyReference2");
        doReturn(collectionOf(existingChildPort)).when(reference).getPorts();

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
     * Point de conception clé (§5 du dossier cycle en V) : l'appariement
     * port-mère ↔ port-redéfini se fait par le lien {@code getRedefines()},
     * <b>jamais par le nom</b>. Ici le port redéfini existant porte un nom
     * différent du port mère ("P1_renamed" vs "P1") : il doit quand même être
     * reconnu et réutilisé (ni recréation via {@code addNewAggr}, ni
     * recherche par nom via {@code findNestedElement}).
     */
    @Test
    void redefinePorts_matchesExistingPortByRedefinesLink_evenWhenRenamed() {
        IRPClass mother = mock(IRPClass.class);
        when(mother.getGUID()).thenReturn("GUID-mother-renamed");

        IRPSysMLPort motherPort = mock(IRPSysMLPort.class);
        when(motherPort.getName()).thenReturn("P1");
        when(motherPort.getGUID()).thenReturn("GUID-motherport-renamed");
        when(motherPort.getOwner()).thenReturn(mother);
        doReturn(collectionOf()).when(motherPort).getStereotypes();
        doReturn(collectionOf(motherPort)).when(mother).getPorts();

        IRPSysMLPort renamedChildPort = mock(IRPSysMLPort.class);
        when(renamedChildPort.getName()).thenReturn("P1_renamed");
        when(renamedChildPort.getGUID()).thenReturn("GUID-renamed-child");
        doReturn(collectionOf(motherPort)).when(renamedChildPort).getRedefines();
        doReturn(collectionOf()).when(renamedChildPort).getStereotypes();

        IRPClass reference = mock(IRPClass.class);
        when(reference.getGUID()).thenReturn("GUID-reference-renamed");
        when(reference.getName()).thenReturn("MyReferenceRenamed");
        doReturn(collectionOf(renamedChildPort)).when(reference).getPorts();

        IRPGeneralization gen = mock(IRPGeneralization.class);
        when(gen.getUserDefinedMetaClass()).thenReturn(REF_FUNCTION);
        when(gen.getBaseClass()).thenReturn(mother);
        doReturn(collectionOf(gen)).when(reference).getGeneralizations();

        boolean result = RedefinedPortsService.redefinePorts(reference);

        assertTrue(result);
        verify(reference, never()).addNewAggr(anyString(), anyString());
        verify(reference, never()).findNestedElement(anyString(), anyString());
        verify(renamedChildPort, never()).deleteFromProject();
        verify(renamedChildPort, atLeastOnce()).addRedefines(motherPort);
    }

    /**
     * Since "per-port confirmation" (91017a3) redefinePorts never deletes a
     * port: an extra port (childB, no redefinition) and an orphan redefinition
     * (childC, target owned by another class) are left in place, and are
     * proposed to the engineer by computePlan as orphan candidates instead.
     */
    @Test
    void redefinePorts_neverDeletesExtraOrOrphanPorts_keepsValidMirror() {
        IRPClass mother = mock(IRPClass.class);
        when(mother.getGUID()).thenReturn("GUID-mother-3");

        IRPClass otherClass = mock(IRPClass.class);
        when(otherClass.getGUID()).thenReturn("GUID-other-class");

        IRPSysMLPort motherPortA = mock(IRPSysMLPort.class);
        when(motherPortA.getName()).thenReturn("portA");
        when(motherPortA.getGUID()).thenReturn("GUID-mpA");
        when(motherPortA.getOwner()).thenReturn(mother);
        doReturn(collectionOf()).when(motherPortA).getStereotypes();
        doReturn(collectionOf(motherPortA)).when(mother).getPorts();

        IRPSysMLPort otherPort = mock(IRPSysMLPort.class);
        when(otherPort.getGUID()).thenReturn("GUID-otherport");
        when(otherPort.getOwner()).thenReturn(otherClass);

        IRPSysMLPort childA = mock(IRPSysMLPort.class);
        when(childA.getName()).thenReturn("portA");
        when(childA.getGUID()).thenReturn("GUID-childA");
        doReturn(collectionOf(motherPortA)).when(childA).getRedefines();
        doReturn(collectionOf()).when(childA).getStereotypes();

        IRPSysMLPort childB = mock(IRPSysMLPort.class);
        when(childB.getName()).thenReturn("portB");
        when(childB.getGUID()).thenReturn("GUID-childB");
        doReturn(collectionOf()).when(childB).getRedefines();

        IRPSysMLPort childC = mock(IRPSysMLPort.class);
        when(childC.getName()).thenReturn("portC");
        when(childC.getGUID()).thenReturn("GUID-childC");
        doReturn(collectionOf(otherPort)).when(childC).getRedefines();

        IRPClass reference = mock(IRPClass.class);
        when(reference.getGUID()).thenReturn("GUID-reference-3");
        when(reference.getName()).thenReturn("MyReference3");
        doReturn(collectionOf(childA, childB, childC)).when(reference).getPorts();

        IRPGeneralization gen = mock(IRPGeneralization.class);
        when(gen.getUserDefinedMetaClass()).thenReturn(REF_FUNCTION);
        when(gen.getBaseClass()).thenReturn(mother);
        doReturn(collectionOf(gen)).when(reference).getGeneralizations();

        boolean result = RedefinedPortsService.redefinePorts(reference);

        assertTrue(result);
        verify(childA, never()).deleteFromProject();
        verify(childB, never()).deleteFromProject();
        verify(childC, never()).deleteFromProject();
        verify(reference, never()).addNewAggr(anyString(), anyString()); // childA is the mirror
        verify(childA, atLeastOnce()).addRedefines(motherPortA);

        PortPlan plan = RedefinedPortsService.computePlan(reference);
        assertEquals(List.of("portB", "portC"), orphanNames(plan));
        assertTrue(plan.toAdd.isEmpty());
    }

    private static List<String> orphanNames(PortPlan plan) {
        List<String> names = new java.util.ArrayList<>();
        for (PortPlan.OrphanCandidate c : plan.orphanCandidates) names.add(c.name);
        return names;
    }

    /**
     * REQ-3 (retrait) : un stéréotype présent sur le port redéfini mais absent
     * du port mère doit être RETIRÉ (pas seulement : pas ajouté). C'est la
     * différence concrète avec la copie écouteur, qui ne fait qu'ajouter.
     */
    @Test
    void redefinePorts_removesStereotypeNoLongerOnMother() {
        IRPClass mother = mock(IRPClass.class);
        when(mother.getGUID()).thenReturn("GUID-mother-stereo");

        IRPStereotype obsoleteStereotype = mock(IRPStereotype.class);
        when(obsoleteStereotype.getGUID()).thenReturn("GUID-stereo-obsolete");
        when(obsoleteStereotype.getIsNewTerm()).thenReturn(0);

        IRPSysMLPort motherPort = mock(IRPSysMLPort.class);
        when(motherPort.getName()).thenReturn("P1");
        when(motherPort.getGUID()).thenReturn("GUID-motherport-stereo");
        when(motherPort.getOwner()).thenReturn(mother);
        doReturn(collectionOf()).when(motherPort).getStereotypes(); // mother no longer has it
        doReturn(collectionOf(motherPort)).when(mother).getPorts();

        IRPSysMLPort childPort = mock(IRPSysMLPort.class);
        when(childPort.getName()).thenReturn("P1");
        when(childPort.getGUID()).thenReturn("GUID-childport-stereo");
        doReturn(collectionOf(motherPort)).when(childPort).getRedefines();
        doReturn(collectionOf(obsoleteStereotype)).when(childPort).getStereotypes(); // still has it

        IRPClass reference = mock(IRPClass.class);
        when(reference.getGUID()).thenReturn("GUID-reference-stereo");
        when(reference.getName()).thenReturn("MyReferenceStereo");
        doReturn(collectionOf(childPort)).when(reference).getPorts();

        IRPGeneralization gen = mock(IRPGeneralization.class);
        when(gen.getUserDefinedMetaClass()).thenReturn(REF_FUNCTION);
        when(gen.getBaseClass()).thenReturn(mother);
        doReturn(collectionOf(gen)).when(reference).getGeneralizations();

        boolean result = RedefinedPortsService.redefinePorts(reference);

        assertTrue(result);
        verify(childPort).removeStereotype(obsoleteStereotype);
    }

    // ==================================================================
    // computePlan / PortPlan (dry-run, REQ-5)
    // ==================================================================

    @Test
    void computePlan_returnsEmptyPlan_whenNotAClass() {
        IRPModelElement element = mock(IRPModelElement.class);
        PortPlan plan = RedefinedPortsService.computePlan(element);
        assertFalse(plan.hasChanges());
    }

    @Test
    void computePlan_returnsEmptyPlan_whenMotherNotResolved() {
        IRPClass reference = mock(IRPClass.class);
        doReturn(collectionOf()).when(reference).getGeneralizations(); // no matching generalization

        PortPlan plan = RedefinedPortsService.computePlan(reference);

        assertFalse(plan.hasChanges());
    }

    @Test
    void computePlan_addsMissingMotherPort() {
        IRPClass mother = mock(IRPClass.class);
        when(mother.getGUID()).thenReturn("GUID-mother-add");

        IRPSysMLPort motherPort = mock(IRPSysMLPort.class);
        when(motherPort.getName()).thenReturn("P1");
        when(motherPort.getGUID()).thenReturn("GUID-mp-add");
        doReturn(collectionOf(motherPort)).when(mother).getPorts();

        IRPClass reference = referenceOf(mother, /* childPorts */ collectionOf());

        PortPlan plan = RedefinedPortsService.computePlan(reference);

        assertTrue(plan.toAdd.contains("P1"));
        assertTrue(plan.toRemove.isEmpty());
        assertTrue(plan.toUpdate.isEmpty());
    }

    @Test
    void computePlan_noChanges_whenOwnedPortIsIdenticalMirror() {
        IRPClass mother = mock(IRPClass.class);
        when(mother.getGUID()).thenReturn("GUID-mother-nochange");

        IRPClassifier type = mock(IRPClassifier.class);
        when(type.getGUID()).thenReturn("GUID-type-nochange");

        IRPSysMLPort motherPort = mock(IRPSysMLPort.class);
        when(motherPort.getName()).thenReturn("P1");
        when(motherPort.getGUID()).thenReturn("GUID-mp-nochange");
        when(motherPort.getOwner()).thenReturn(mother);
        when(motherPort.getType()).thenReturn(type);
        when(motherPort.getPortDirection()).thenReturn("in");
        when(motherPort.getDescription()).thenReturn("desc");
        doReturn(collectionOf()).when(motherPort).getStereotypes();
        doReturn(collectionOf(motherPort)).when(mother).getPorts();

        IRPSysMLPort childPort = mock(IRPSysMLPort.class);
        when(childPort.getName()).thenReturn("P1"); // a mirror carries the mother port's name
        when(childPort.getGUID()).thenReturn("GUID-child-nochange");
        when(childPort.getType()).thenReturn(type);
        when(childPort.getPortDirection()).thenReturn("in");
        when(childPort.getDescription()).thenReturn("desc");
        doReturn(collectionOf(motherPort)).when(childPort).getRedefines();
        doReturn(collectionOf()).when(childPort).getStereotypes();

        IRPClass reference = referenceOf(mother, collectionOf(childPort));

        PortPlan plan = RedefinedPortsService.computePlan(reference);

        assertFalse(plan.hasChanges());
    }

    @Test
    void computePlan_flagsUpdate_whenTypeDiffers() {
        IRPClassifier motherType = mock(IRPClassifier.class);
        when(motherType.getGUID()).thenReturn("GUID-type-mother");
        IRPClassifier childType = mock(IRPClassifier.class);
        when(childType.getGUID()).thenReturn("GUID-type-child");

        PortPlan plan = computePlanForSingleUpdateCandidate(port -> when(port.getType()).thenReturn(motherType),
                port -> when(port.getType()).thenReturn(childType));

        assertTrue(plan.toUpdate.contains("P1"));
    }

    @Test
    void computePlan_flagsUpdate_whenDirectionDiffers() {
        PortPlan plan = computePlanForSingleUpdateCandidate(
                port -> when(port.getPortDirection()).thenReturn("in"),
                port -> when(port.getPortDirection()).thenReturn("out"));

        assertTrue(plan.toUpdate.contains("P1"));
    }

    @Test
    void computePlan_flagsUpdate_whenDescriptionDiffers() {
        PortPlan plan = computePlanForSingleUpdateCandidate(
                port -> when(port.getDescription()).thenReturn("mother description"),
                port -> when(port.getDescription()).thenReturn("stale description"));

        assertTrue(plan.toUpdate.contains("P1"));
    }

    @Test
    void computePlan_flagsUpdate_whenStereotypeMissingOnChild() {
        IRPStereotype stereotype = mock(IRPStereotype.class);
        when(stereotype.getGUID()).thenReturn("GUID-stereo-missing");
        when(stereotype.getIsNewTerm()).thenReturn(0);

        PortPlan plan = computePlanForSingleUpdateCandidate(
                port -> doReturn(collectionOf(stereotype)).when(port).getStereotypes(),
                port -> doReturn(collectionOf()).when(port).getStereotypes());

        assertTrue(plan.toUpdate.contains("P1"));
    }

    @Test
    void computePlan_flagsUpdate_whenStereotypeExtraOnChild() {
        IRPStereotype stereotype = mock(IRPStereotype.class);
        when(stereotype.getGUID()).thenReturn("GUID-stereo-extra");
        when(stereotype.getIsNewTerm()).thenReturn(0);

        PortPlan plan = computePlanForSingleUpdateCandidate(
                port -> doReturn(collectionOf()).when(port).getStereotypes(),
                port -> doReturn(collectionOf(stereotype)).when(port).getStereotypes());

        assertTrue(plan.toUpdate.contains("P1"));
    }

    @Test
    void computePlan_ignoresNewTermStereotypeDifference() {
        IRPStereotype newTermStereotype = mock(IRPStereotype.class);
        when(newTermStereotype.getGUID()).thenReturn("GUID-stereo-newterm");
        when(newTermStereotype.getIsNewTerm()).thenReturn(1); // "Flow Port" New Term itself

        PortPlan plan = computePlanForSingleUpdateCandidate(
                port -> doReturn(collectionOf(newTermStereotype)).when(port).getStereotypes(),
                port -> doReturn(collectionOf()).when(port).getStereotypes());

        assertFalse(plan.hasChanges());
    }

    /**
     * Asymétrie clé entre les deux moitiés de {@code computePlan} (relevée en
     * revue) : le côté "à supprimer" (fille) exige que la cible du
     * {@code getRedefines()} appartienne à la mère COURANTE
     * ({@code redefinedMotherPort}), alors que le côté "à ajouter" (mère)
     * recherche un appariement par identité du port mère
     * ({@code findOwnedRedefinition}), indépendamment du propriétaire actuel
     * de la cible. Avec un enfant qui redéfinit un port appartenant à une
     * AUTRE classe : il doit être proposé comme orphelin
     * ({@code orphanCandidates}, suppression à confirmer : {@code toRemove}
     * n'est plus alimenté depuis 91017a3) ET le port de la mère courante doit
     * simultanément apparaître dans {@code toAdd} (aucun port de la fille ne
     * le redéfinit "valablement").
     */
    @Test
    void computePlan_orphanRedefinition_isOrphanCandidateAndMotherPortIsToAdd() {
        IRPClass mother = mock(IRPClass.class);
        when(mother.getGUID()).thenReturn("GUID-mother-asym");

        IRPClass otherClass = mock(IRPClass.class);
        when(otherClass.getGUID()).thenReturn("GUID-other-asym");

        IRPSysMLPort motherPort = mock(IRPSysMLPort.class);
        when(motherPort.getName()).thenReturn("portA");
        when(motherPort.getGUID()).thenReturn("GUID-mp-asym");
        doReturn(collectionOf(motherPort)).when(mother).getPorts();

        IRPSysMLPort otherOwnedPort = mock(IRPSysMLPort.class);
        when(otherOwnedPort.getGUID()).thenReturn("GUID-otherport-asym");
        when(otherOwnedPort.getOwner()).thenReturn(otherClass);

        IRPSysMLPort orphanChild = mock(IRPSysMLPort.class);
        when(orphanChild.getName()).thenReturn("portOrphan");
        when(orphanChild.getGUID()).thenReturn("GUID-orphanchild-asym");
        doReturn(collectionOf(otherOwnedPort)).when(orphanChild).getRedefines();

        IRPClass reference = referenceOf(mother, collectionOf(orphanChild));

        PortPlan plan = RedefinedPortsService.computePlan(reference);

        assertEquals(List.of("portOrphan"), orphanNames(plan),
                "orphan redefinition must be proposed for deletion");
        assertSame(orphanChild, plan.orphanCandidates.get(0).port);
        assertTrue(plan.toRemove.isEmpty(), "nothing is ever removed without confirmation");
        assertTrue(plan.toAdd.contains("portA"), "current mother's port has no valid mirror -> must be added");
    }

    /** Builds a reference IRPClass wired to a "References Function" generalization onto {@code mother}. */
    private static IRPClass referenceOf(IRPClass mother, IRPCollection childPorts) {
        IRPClass reference = mock(IRPClass.class);
        when(reference.getGUID()).thenReturn("GUID-reference-" + System.identityHashCode(reference));
        doReturn(childPorts).when(reference).getPorts();

        IRPGeneralization gen = mock(IRPGeneralization.class);
        when(gen.getUserDefinedMetaClass()).thenReturn(REF_FUNCTION);
        when(gen.getBaseClass()).thenReturn(mother);
        doReturn(collectionOf(gen)).when(reference).getGeneralizations();

        return reference;
    }

    @FunctionalInterface
    private interface PortStub {
        void apply(IRPSysMLPort port);
    }

    /**
     * Builds a single-port mother/reference pair sharing name/GUID pattern,
     * applies {@code motherStub} to the mother's port and {@code childStub} to
     * the redefined (owned) child port, and returns the resulting
     * {@link PortPlan}. Used to isolate one {@code portPropertiesDiffer}
     * criterion per test.
     */
    private static PortPlan computePlanForSingleUpdateCandidate(PortStub motherStub, PortStub childStub) {
        IRPClass mother = mock(IRPClass.class);
        when(mother.getGUID()).thenReturn("GUID-mother-" + System.identityHashCode(motherStub));

        IRPSysMLPort motherPort = mock(IRPSysMLPort.class);
        when(motherPort.getName()).thenReturn("P1");
        when(motherPort.getGUID()).thenReturn("GUID-mp-" + System.identityHashCode(motherStub));
        when(motherPort.getOwner()).thenReturn(mother);
        motherStub.apply(motherPort);
        doReturn(collectionOf(motherPort)).when(mother).getPorts();

        IRPSysMLPort childPort = mock(IRPSysMLPort.class);
        when(childPort.getName()).thenReturn("P1"); // same name: no rename candidate
        when(childPort.getGUID()).thenReturn("GUID-cp-" + System.identityHashCode(childStub));
        childStub.apply(childPort);
        doReturn(collectionOf(motherPort)).when(childPort).getRedefines();

        IRPClass reference = referenceOf(mother, collectionOf(childPort));

        return RedefinedPortsService.computePlan(reference);
    }

    // ==================================================================
    // hideRedefinedPorts
    // ==================================================================

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

    // ==================================================================
    // Redefined port drawn AT THE PLACE of the inherited port it replaces
    // ==================================================================

    /** A graphic port under {@code node} showing {@code model}. */
    private static IRPGraphElement portGraphic(IRPGraphElement node, IRPModelElement model, String position) {
        IRPGraphElement g = mock(IRPGraphElement.class);
        when(g.getModelObject()).thenReturn(model);
        when(g.getGraphicalParent()).thenReturn(node);
        if (position != null) {
            IRPGraphicalProperty p = mock(IRPGraphicalProperty.class);
            when(p.getValue()).thenReturn(position);
            when(g.getGraphicalProperty("Position")).thenReturn(p);
        }
        return g;
    }

    private static IRPGraphicalProperty property(String value) {
        IRPGraphicalProperty p = mock(IRPGraphicalProperty.class);
        when(p.getValue()).thenReturn(value);
        return p;
    }

    /** A Flow Port model redefining {@code targets}. */
    private static IRPSysMLPort flowPort(String guid, IRPModelElement... targets) {
        IRPSysMLPort p = mock(IRPSysMLPort.class);
        when(p.getGUID()).thenReturn(guid);
        when(p.getName()).thenReturn(guid);
        when(p.getUserDefinedMetaClass()).thenReturn("Flow Port");
        doReturn(collectionOf((Object[]) targets)).when(p).getRedefines();
        return p;
    }

    @Test
    void hideRedefinedPorts_bothDrawn_redefinedPortTakesThePlaceOfTheInheritedOne_beforeRemoval() {
        IRPApplication app = mock(IRPApplication.class);
        IRPDiagram diagram = mock(IRPDiagram.class);
        IRPClass reference = mock(IRPClass.class);
        IRPGraphElement node = mock(IRPGraphElement.class);
        when(node.getModelObject()).thenReturn(reference);
        when(node.getDiagram()).thenReturn(diagram);

        IRPSysMLPort inherited = flowPort("GUID-inh");
        IRPSysMLPort redefined = flowPort("GUID-red", inherited);
        IRPGraphElement inhGraphic = portGraphic(node, inherited, "120,340");
        IRPGraphElement redGraphic = portGraphic(node, redefined, "10,10");
        doReturn(collectionOf(inhGraphic, redGraphic)).when(diagram).getGraphicalElements();
        IRPCollection removal = mock(IRPCollection.class);
        when(app.createNewCollection()).thenReturn(removal);

        assertEquals(1, RedefinedPortsService.hideRedefinedPorts(app, node));

        InOrder order = inOrder(redGraphic, diagram);
        order.verify(redGraphic).setGraphicalProperty("Position", "120,340");
        order.verify(diagram).removeGraphElements(removal);
        verify(removal).addGraphicalItem(inhGraphic);
        verify(removal, never()).addGraphicalItem(redGraphic);
    }

    @Test
    void hideRedefinedPorts_portRedefiningTwoDrawnPorts_takesTheFirstPlace_removesBoth() {
        IRPApplication app = mock(IRPApplication.class);
        IRPDiagram diagram = mock(IRPDiagram.class);
        IRPGraphElement node = mock(IRPGraphElement.class);
        when(node.getModelObject()).thenReturn(mock(IRPClass.class));
        when(node.getDiagram()).thenReturn(diagram);

        IRPSysMLPort inhA = flowPort("GUID-inh-a");
        IRPSysMLPort inhB = flowPort("GUID-inh-b");
        IRPSysMLPort redefined = flowPort("GUID-red-ab", inhA, inhB);
        IRPGraphElement gA = portGraphic(node, inhA, "7,8");
        IRPGraphElement gB = portGraphic(node, inhB, "9,10");
        IRPGraphElement gRed = portGraphic(node, redefined, "1,1");
        doReturn(collectionOf(gA, gB, gRed)).when(diagram).getGraphicalElements();
        IRPCollection removal = mock(IRPCollection.class);
        when(app.createNewCollection()).thenReturn(removal);

        assertEquals(2, RedefinedPortsService.hideRedefinedPorts(app, node));

        verify(gRed).setGraphicalProperty("Position", "7,8");
        verify(gRed, never()).setGraphicalProperty("Position", "9,10");
        verify(removal).addGraphicalItem(gA);
        verify(removal).addGraphicalItem(gB);
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
        IRPSysMLPort p = flowPort("GUID-" + name + "-" + System.identityHashCode(owner), targets);
        when(p.getName()).thenReturn(name);
        when(p.getOwner()).thenReturn(owner);
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
    void hideRedefinedPorts_definitionFlow_isCopiedOntoTheRedefinedPort_definitionUntouched() {
        Scene s = new Scene();

        assertEquals(1, s.clean());

        // The copy: same New Term, named and labelled after the two boxes, ends then direction.
        verify(s.pkg).addNewAggr("Functional Flow", "R1_p_2_X_q");
        InOrder order = inOrder(s.copy);
        order.verify(s.copy).setEnd1(s.redefined);
        order.verify(s.copy).setEnd2(s.q);
        order.verify(s.copy).setDirection("toEnd2");
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
        verify(s.diagram).removeGraphElements(s.removal);
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

    /** Dry run before the mirror exists: the copy and its name are announced from the boxes. */
    @Test
    void planFlowReconnections_reportsTheCopyWithItsName() {
        Scene s = new Scene();
        IRPGeneralization gen = (IRPGeneralization) s.r1.getGeneralizations().toList().get(0);
        when(gen.getBaseClass()).thenReturn(s.mother);
        doReturn(collectionOf()).when(s.r1).getPorts();   // not redefined yet
        doReturn(collectionOf(s.gInh, s.gQ, s.edge)).when(s.diagram).getGraphicalElements();

        List<RedefinedPortsService.FlowPlan> plans = RedefinedPortsService.planFlowReconnections(s.node);

        assertEquals(1, plans.size());
        RedefinedPortsService.FlowPlan plan = plans.get(0);
        assertEquals(RedefinedPortsService.FlowKind.COPY, plan.kind);
        assertEquals("R1_p_2_X_q", plan.copyName);
        assertEquals("p_2", plan.portName);
        assertEquals("R1", plan.referenceName);
        assertEquals("f1 -> copy R1_p_2_X_q on p_2 (definition flow kept)", plan.describe());
        assertTrue(plan.sameTrait(plan));
    }

    @Test
    void planFlowReconnections_reportsAReferenceFlowAsReconnect() {
        Scene s = new Scene();
        IRPGeneralization gen = (IRPGeneralization) s.r1.getGeneralizations().toList().get(0);
        when(gen.getBaseClass()).thenReturn(s.mother);
        IRPClass r2 = referenceBox("R2");
        IRPSysMLPort r2Port = portOf("q", r2);
        IRPFlow ref = flowNamed("R1_R2", s.inherited, r2Port);
        IRPGraphEdge edge = trait(ref, s.gInh, boxGraphic(mock(IRPGraphElement.class), r2Port, "1,1"));
        doReturn(collectionOf(s.gInh, s.gRed, edge)).when(s.diagram).getGraphicalElements();
        doReturn(collectionOf(s.diagram)).when(ref).getReferences();
        doReturn(collectionOf(edge)).when(s.diagram).getCorrespondingGraphicElements(ref);

        List<RedefinedPortsService.FlowPlan> plans = RedefinedPortsService.planFlowReconnections(s.node);

        assertEquals(1, plans.size());
        assertEquals(RedefinedPortsService.FlowKind.RECONNECT, plans.get(0).kind);
        assertEquals("R1_R2", plans.get(0).describe());
    }

    /**
     * A reference flow already reconnected to R1's redefined p_2 (from another
     * diagram) while its trait here still hangs on the inherited p_2: the dry
     * run must classify it like the real swap (RECONNECT), not "not attached".
     */
    @Test
    void planFlowReconnections_referenceFlowAlreadyNamingTheRedefinedPort_isReconnect() {
        Scene s = new Scene();
        IRPGeneralization gen = (IRPGeneralization) s.r1.getGeneralizations().toList().get(0);
        when(gen.getBaseClass()).thenReturn(s.mother);
        IRPClass r2 = referenceBox("R2");
        IRPSysMLPort r2Port = portOf("q", r2);
        IRPFlow ref = flowNamed("R1_p_2_R2_q", s.redefined, r2Port);
        IRPGraphEdge edge = trait(ref, s.gInh, boxGraphic(mock(IRPGraphElement.class), r2Port, "1,1"));
        doReturn(collectionOf(s.gInh, s.gRed, edge)).when(s.diagram).getGraphicalElements();
        doReturn(collectionOf(s.diagram)).when(ref).getReferences();
        doReturn(collectionOf(edge)).when(s.diagram).getCorrespondingGraphicElements(ref);

        List<RedefinedPortsService.FlowPlan> plans = RedefinedPortsService.planFlowReconnections(s.node);

        assertEquals(1, plans.size());
        assertEquals(RedefinedPortsService.FlowKind.RECONNECT, plans.get(0).kind);
        assertEquals("p_2", plans.get(0).portName);
        assertEquals("R1_p_2_R2_q", plans.get(0).describe());
    }

    /** Same flow, but the reference owns no redefinition yet: the end names nothing drawn here, left as is. */
    @Test
    void planFlowReconnections_referenceFlowNamingAnUnknownPort_isLeftNotAttached() {
        Scene s = new Scene();
        IRPGeneralization gen = (IRPGeneralization) s.r1.getGeneralizations().toList().get(0);
        when(gen.getBaseClass()).thenReturn(s.mother);
        doReturn(collectionOf()).when(s.r1).getPorts();   // no redefined port owned yet
        IRPClass r2 = referenceBox("R2");
        IRPSysMLPort r2Port = portOf("q", r2);
        IRPFlow ref = flowNamed("R1_p_2_R2_q", s.redefined, r2Port);
        IRPGraphEdge edge = trait(ref, s.gInh, boxGraphic(mock(IRPGraphElement.class), r2Port, "1,1"));
        doReturn(collectionOf(s.gInh, edge)).when(s.diagram).getGraphicalElements();

        List<RedefinedPortsService.FlowPlan> plans = RedefinedPortsService.planFlowReconnections(s.node);

        assertEquals(1, plans.size());
        assertEquals(RedefinedPortsService.FlowKind.LEFT, plans.get(0).kind);
        assertEquals("not attached to this port", plans.get(0).reason);
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
        IRPClass reference = mock(IRPClass.class);
        IRPGraphElement node = mock(IRPGraphElement.class);
        when(node.getModelObject()).thenReturn(reference);
        when(node.getDiagram()).thenReturn(diagram);

        IRPSysMLPort inherited = flowPort("GUID-inh-alone");
        IRPSysMLPort redefined = flowPort("GUID-red-alone", inherited);
        IRPGraphElement redGraphic = portGraphic(node, redefined, "10,10");
        doReturn(collectionOf(redGraphic)).when(diagram).getGraphicalElements();
        when(app.createNewCollection()).thenReturn(mock(IRPCollection.class));

        assertEquals(0, RedefinedPortsService.hideRedefinedPorts(app, node));

        verify(redGraphic, never()).setGraphicalProperty(anyString(), anyString());
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

        IRPSysMLPort inherited = flowPort("GUID-inh-draw");
        IRPSysMLPort redefined = flowPort("GUID-red-draw", inherited);
        when(redefined.getOwner()).thenReturn(reference);
        doReturn(collectionOf(redefined)).when(reference).getPorts();
        IRPGraphElement inhGraphic = portGraphic(node, inherited, "120,340");
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

        IRPSysMLPort inherited = flowPort("GUID-inh-nodraw");
        IRPSysMLPort redefined = flowPort("GUID-red-nodraw", inherited);
        when(redefined.getOwner()).thenReturn(reference);
        doReturn(collectionOf(redefined)).when(reference).getPorts();
        IRPGraphElement inhGraphic = portGraphic(node, inherited, "120,340");
        doReturn(collectionOf(inhGraphic)).when(diagram).getGraphicalElements();
        when(app.createNewCollection()).thenReturn(mock(IRPCollection.class));
        when(diagram.addNewNodeForElement(any(), anyInt(), anyInt(), anyInt(), anyInt())).thenReturn(null);

        assertEquals(0, RedefinedPortsService.hideRedefinedPorts(app, node));

        verify(diagram, never()).removeGraphElements(any());
    }

    @Test
    void hideRedefinedPorts_pairsTwoProxiesOfTheSameInheritedPort() {
        IRPApplication app = mock(IRPApplication.class);
        IRPDiagram diagram = mock(IRPDiagram.class);
        IRPGraphElement node = mock(IRPGraphElement.class);
        when(node.getModelObject()).thenReturn(mock(IRPClass.class));
        when(node.getDiagram()).thenReturn(diagram);

        IRPSysMLPort inheritedAsDrawn = flowPort("GUID-inh-proxy");
        IRPSysMLPort inheritedAsRedefined = flowPort("GUID-inh-proxy");   // other proxy, same element
        IRPSysMLPort redefined = flowPort("GUID-red-proxy", inheritedAsRedefined);
        IRPGraphElement inhGraphic = portGraphic(node, inheritedAsDrawn, "5,6");
        IRPGraphElement redGraphic = portGraphic(node, redefined, "1,1");
        doReturn(collectionOf(inhGraphic, redGraphic)).when(diagram).getGraphicalElements();
        IRPCollection removal = mock(IRPCollection.class);
        when(app.createNewCollection()).thenReturn(removal);

        assertEquals(1, RedefinedPortsService.hideRedefinedPorts(app, node));

        verify(redGraphic).setGraphicalProperty("Position", "5,6");
        verify(removal).addGraphicalItem(inhGraphic);
    }

    @Test
    void hideRedefinedPorts_list_eachDiagramRemovesOnlyItsOwnPorts() {
        IRPApplication app = mock(IRPApplication.class);
        IRPCollection removal1 = mock(IRPCollection.class);
        IRPCollection removal2 = mock(IRPCollection.class);
        when(app.createNewCollection()).thenReturn(removal1, removal2);

        IRPDiagram d1 = mock(IRPDiagram.class);
        IRPGraphElement n1 = mock(IRPGraphElement.class);
        when(n1.getModelObject()).thenReturn(mock(IRPClass.class));
        when(n1.getDiagram()).thenReturn(d1);
        IRPSysMLPort inh1 = flowPort("GUID-inh-d1");
        IRPGraphElement i1 = portGraphic(n1, inh1, "1,1");
        IRPGraphElement r1 = portGraphic(n1, flowPort("GUID-red-d1", inh1), "2,2");
        doReturn(collectionOf(i1, r1)).when(d1).getGraphicalElements();

        IRPDiagram d2 = mock(IRPDiagram.class);
        IRPGraphElement n2 = mock(IRPGraphElement.class);
        when(n2.getModelObject()).thenReturn(mock(IRPClass.class));
        when(n2.getDiagram()).thenReturn(d2);
        IRPSysMLPort inh2 = flowPort("GUID-inh-d2");
        IRPGraphElement i2 = portGraphic(n2, inh2, "3,3");
        IRPGraphElement r2 = portGraphic(n2, flowPort("GUID-red-d2", inh2), "4,4");
        doReturn(collectionOf(i2, r2)).when(d2).getGraphicalElements();

        assertEquals(2, RedefinedPortsService.hideRedefinedPorts(app, List.of(n1, n2)));

        verify(removal1).addGraphicalItem(i1);
        verify(removal1, never()).addGraphicalItem(i2);
        verify(removal2).addGraphicalItem(i2);
        verify(removal2, never()).addGraphicalItem(i1);
        verify(d1).removeGraphElements(removal1);
        verify(d2).removeGraphElements(removal2);
    }

    @Test
    void hideRedefinedPorts_returnsZero_whenGraphElementNull() {
        IRPApplication app = mock(IRPApplication.class);
        assertEquals(0, RedefinedPortsService.hideRedefinedPorts(app, (IRPGraphElement) null));
    }

    @Test
    void hideRedefinedPorts_returnsZero_whenModelObjectNull() {
        IRPApplication app = mock(IRPApplication.class);
        IRPGraphElement graphElement = mock(IRPGraphElement.class);
        when(graphElement.getModelObject()).thenReturn(null);

        assertEquals(0, RedefinedPortsService.hideRedefinedPorts(app, graphElement));
    }

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

    @Test
    void getGraphicalRepresentations_returnsEmptyList_whenModelElementNull() {
        List<IRPGraphElement> result = RedefinedPortsService.getGraphicalRepresentations(null);
        assertTrue(result.isEmpty());
    }

    @Test
    void getGraphicalRepresentations_returnsEmptyList_whenNoReferences() {
        IRPModelElement modelElement = mock(IRPModelElement.class);
        when(modelElement.getName()).thenReturn("Elem");
        doReturn(collectionOf()).when(modelElement).getReferences();

        List<IRPGraphElement> result = RedefinedPortsService.getGraphicalRepresentations(modelElement);
        assertTrue(result.isEmpty());
    }

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
    // Rhapsody shows those ports on the reference, so they must be mirrored.
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

    /** WhiteBox mother owning {@code ownPorts}, with a "Redefines Function" generalization to {@code base}. */
    private static IRPClass whiteBoxOver(String guid, IRPClass base, IRPSysMLPort... ownPorts) {
        IRPClass whiteBox = mock(IRPClass.class);
        when(whiteBox.getGUID()).thenReturn(guid);
        when(whiteBox.getName()).thenReturn("Check");
        for (IRPSysMLPort p : ownPorts) when(p.getOwner()).thenReturn(whiteBox);
        doReturn(collectionOf((Object[]) ownPorts)).when(whiteBox).getPorts();
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

    private static IRPSysMLPort port(String name, String guid, IRPClassifier type) {
        IRPSysMLPort p = mock(IRPSysMLPort.class);
        when(p.getName()).thenReturn(name);
        when(p.getGUID()).thenReturn(guid);
        when(p.getType()).thenReturn(type);
        when(p.getPortDirection()).thenReturn("in");
        doReturn(collectionOf()).when(p).getStereotypes();
        return p;
    }

    @Test
    void redefinePorts_mirrorsPortsTheMotherInheritsFromItsOwnBase() {
        IRPClassifier type = mock(IRPClassifier.class);
        when(type.getGUID()).thenReturn("GUID-type-inh");
        IRPSysMLPort bbPort = port("P1", "GUID-bb-p1", type);
        IRPClass blackBox = blackBoxOwning("GUID-bb", bbPort);
        IRPClass whiteBox = whiteBoxOver("GUID-wb", blackBox);
        IRPClass reference = referenceOf("GUID-ref-inh", whiteBox);

        IRPSysMLPort created = mock(IRPSysMLPort.class);
        when(created.getGUID()).thenReturn("GUID-created-inh");
        doReturn(collectionOf()).when(created).getStereotypes();
        when(reference.addNewAggr("Flow Port", "P1")).thenReturn(created);

        assertTrue(RedefinedPortsService.redefinePorts(reference));
        verify(reference).addNewAggr("Flow Port", "P1");
        verify(created, atLeastOnce()).addRedefines(bbPort);
        verify(created, atLeastOnce()).setType(type);
    }

    @Test
    void redefinePorts_portTheMotherRedefines_mirrorsTheMothersVersionOnly() {
        IRPClassifier type = mock(IRPClassifier.class);
        when(type.getGUID()).thenReturn("GUID-type-near");
        IRPSysMLPort bbPort = port("P1", "GUID-bb-p1-near", type);
        IRPClass blackBox = blackBoxOwning("GUID-bb-near", bbPort);
        IRPSysMLPort wbPort = port("P1", "GUID-wb-p1-near", type);
        doReturn(collectionOf(bbPort)).when(wbPort).getRedefines();
        IRPClass whiteBox = whiteBoxOver("GUID-wb-near", blackBox, wbPort);
        IRPClass reference = referenceOf("GUID-ref-near", whiteBox);

        IRPSysMLPort created = mock(IRPSysMLPort.class);
        when(created.getGUID()).thenReturn("GUID-created-near");
        doReturn(collectionOf()).when(created).getStereotypes();
        when(reference.addNewAggr("Flow Port", "P1")).thenReturn(created);

        assertTrue(RedefinedPortsService.redefinePorts(reference));
        verify(reference, times(1)).addNewAggr("Flow Port", "P1");
        verify(created, atLeastOnce()).addRedefines(wbPort);
        verify(created, never()).addRedefines(bbPort);
    }

    @Test
    void computePlan_portTheMotherInherits_isToAdd_whenMissing() {
        IRPClassifier type = mock(IRPClassifier.class);
        IRPSysMLPort bbPort = port("P1", "GUID-bb-p1-plan", type);
        IRPClass blackBox = blackBoxOwning("GUID-bb-plan", bbPort);
        IRPClass whiteBox = whiteBoxOver("GUID-wb-plan", blackBox);
        IRPClass reference = referenceOf("GUID-ref-plan", whiteBox);

        PortPlan plan = RedefinedPortsService.computePlan(reference);

        assertEquals(List.of("P1"), plan.toAdd);
        assertTrue(plan.orphanCandidates.isEmpty());
    }

    @Test
    void computePlan_mirrorOfAPortTheMotherInherits_isNotAnOrphan() {
        IRPClassifier type = mock(IRPClassifier.class);
        when(type.getGUID()).thenReturn("GUID-type-orph");
        IRPSysMLPort bbPort = port("P1", "GUID-bb-p1-orph", type);
        IRPClass blackBox = blackBoxOwning("GUID-bb-orph", bbPort);
        IRPClass whiteBox = whiteBoxOver("GUID-wb-orph", blackBox);
        IRPSysMLPort mirror = port("P1", "GUID-mirror-orph", type);
        doReturn(collectionOf(bbPort)).when(mirror).getRedefines();
        IRPClass reference = referenceOf("GUID-ref-orph", whiteBox, mirror);

        PortPlan plan = RedefinedPortsService.computePlan(reference);

        assertTrue(plan.orphanCandidates.isEmpty(),
                "a mirror of a port the mother inherits must never be proposed for deletion");
        assertTrue(plan.toAdd.isEmpty());
    }
}
