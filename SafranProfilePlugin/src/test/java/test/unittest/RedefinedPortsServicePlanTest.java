package test.unittest;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import com.telelogic.rhapsody.core.IRPClass;
import com.telelogic.rhapsody.core.IRPClassifier;
import com.telelogic.rhapsody.core.IRPGeneralization;
import com.telelogic.rhapsody.core.IRPModelElement;
import com.telelogic.rhapsody.core.IRPStereotype;
import com.telelogic.rhapsody.core.IRPSysMLPort;

import tools.RedefinedPortsService;
import tools.RedefinedPortsService.PortPlan;

import static test.unittest.RhpTestMocks.collectionOf;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests (no live Rhapsody) of the decisions taken by
 * {@link RedefinedPortsService}: what {@code computePlan} proposes (add,
 * update, rename candidate, orphan candidate), how the mother chain is walked,
 * what {@code redefinePorts} writes, and the engineer-confirmed helpers
 * ({@code applyRenameToMother}, {@code deleteOrphanPort}).
 *
 * <p>Same conventions as {@link RedefinedPortsServiceTest}: Rhapsody types are
 * Mockito mocks, collections are stubbed with {@code doReturn(...).when(...)}
 * (see {@link RhpTestMocks}), and an unstubbed getter returns null (or 0).</p>
 */
class RedefinedPortsServicePlanTest {

    private static final String REF_FUNCTION = "References Function";
    private static final String REF_LOGICAL_SYSTEM = "References Logical System";

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    /** A class named {@code name} with GUID {@code guid}, owning {@code ports}, no generalization. */
    private static IRPClass classOwning(String name, String guid, IRPSysMLPort... ports) {
        IRPClass c = mock(IRPClass.class);
        when(c.getName()).thenReturn(name);
        when(c.getGUID()).thenReturn(guid);
        for (IRPSysMLPort p : ports) when(p.getOwner()).thenReturn(c);
        doReturn(collectionOf((Object[]) ports)).when(c).getPorts();
        doReturn(collectionOf()).when(c).getGeneralizations();
        return c;
    }

    private static IRPGeneralization generalization(String udmc, IRPClassifier base) {
        IRPGeneralization g = mock(IRPGeneralization.class);
        when(g.getUserDefinedMetaClass()).thenReturn(udmc);
        when(g.getBaseClass()).thenReturn(base);
        return g;
    }

    /** Makes {@code sub} generalize {@code base} ("Redefines Function"). */
    private static void inherits(IRPClass sub, IRPClass base) {
        doReturn(collectionOf(generalization("Redefines Function", base))).when(sub).getGeneralizations();
    }

    /** A reference named "Ref" owning {@code ports}, linked by {@code udmc} to {@code mother}. */
    private static IRPClass reference(String udmc, IRPClassifier mother, IRPSysMLPort... ports) {
        IRPClass ref = mock(IRPClass.class);
        when(ref.getName()).thenReturn("Ref");
        when(ref.getGUID()).thenReturn("GUID-ref-" + System.identityHashCode(ref));
        doReturn(collectionOf((Object[]) ports)).when(ref).getPorts();
        doReturn(collectionOf(generalization(udmc, mother))).when(ref).getGeneralizations();
        return ref;
    }

    private static IRPClass reference(IRPClassifier mother, IRPSysMLPort... ports) {
        return reference(REF_FUNCTION, mother, ports);
    }

    /** A port named {@code name} (no label), no stereotype, no redefinition. */
    private static IRPSysMLPort port(String name, String guid) {
        IRPSysMLPort p = mock(IRPSysMLPort.class);
        when(p.getName()).thenReturn(name);
        when(p.getGUID()).thenReturn(guid);
        doReturn(collectionOf()).when(p).getStereotypes();
        doReturn(collectionOf()).when(p).getRedefines();
        return p;
    }

    /** A reference port named {@code name} that redefines {@code target}. */
    private static IRPSysMLPort mirror(String name, String guid, IRPModelElement target) {
        IRPSysMLPort p = port(name, guid);
        doReturn(collectionOf(target)).when(p).getRedefines();
        return p;
    }

    private static List<String> orphanNames(PortPlan plan) {
        List<String> names = new ArrayList<>();
        for (PortPlan.OrphanCandidate c : plan.orphanCandidates) names.add(c.name);
        return names;
    }

    // ------------------------------------------------------------------
    // computePlan: classification of the reference's own ports
    // ------------------------------------------------------------------

    @Test
    void computePlan_renamedRedefinition_isRenameCandidate_notAddNorOrphan() {
        IRPSysMLPort motherPort = port("Speed", "GUID-mp-rn");
        IRPClass mother = classOwning("Mother", "GUID-m-rn", motherPort);
        IRPSysMLPort child = mirror("Speed_old", "GUID-c-rn", motherPort);
        IRPClass ref = reference(mother, child);

        PortPlan plan = RedefinedPortsService.computePlan(ref);

        assertEquals(1, plan.renameCandidates.size());
        PortPlan.RenameCandidate c = plan.renameCandidates.get(0);
        assertSame(child, c.port);
        assertSame(motherPort, c.motherPort);
        assertEquals("Speed_old", c.currentName);
        assertEquals("Speed", c.motherName);
        assertEquals("Ref", c.referenceName);
        assertTrue(plan.toAdd.isEmpty(), "a renamed mirror is still the mirror: nothing to add");
        assertTrue(plan.orphanCandidates.isEmpty());
        assertTrue(plan.hasChanges());
    }

    @Test
    void computePlan_sameNameButOtherLabel_isRenameCandidate_describedWithLabels() {
        IRPSysMLPort motherPort = port("Speed", "GUID-mp-lbl");
        when(motherPort.getDisplayName()).thenReturn("Vehicle speed");
        IRPClass mother = classOwning("Mother", "GUID-m-lbl", motherPort);
        IRPSysMLPort child = mirror("Speed", "GUID-c-lbl", motherPort);
        when(child.getDisplayName()).thenReturn("Old label");
        IRPClass ref = reference(mother, child);

        PortPlan plan = RedefinedPortsService.computePlan(ref);

        assertEquals(1, plan.renameCandidates.size());
        assertEquals("Speed \"Old label\"", plan.renameCandidates.get(0).currentName);
        assertEquals("Speed \"Vehicle speed\"", plan.renameCandidates.get(0).motherName);
    }

    @Test
    void computePlan_labelEqualToName_isNotShownTwice() {
        IRPSysMLPort motherPort = port("Speed", "GUID-mp-lbl2");
        when(motherPort.getDisplayName()).thenReturn("Speed");
        IRPClass mother = classOwning("Mother", "GUID-m-lbl2", motherPort);
        IRPSysMLPort child = mirror("Speed_x", "GUID-c-lbl2", motherPort);
        when(child.getDisplayName()).thenReturn("Speed_x");
        IRPClass ref = reference(mother, child);

        PortPlan.RenameCandidate c = RedefinedPortsService.computePlan(ref).renameCandidates.get(0);

        assertEquals("Speed_x", c.currentName);
        assertEquals("Speed", c.motherName);
    }

    @Test
    void computePlan_unlinkedHomonym_isAdoptedAsUpdate_neitherAddedNorOrphan() {
        IRPSysMLPort motherPort = port("Speed", "GUID-mp-hom");
        IRPClass mother = classOwning("Mother", "GUID-m-hom", motherPort);
        IRPSysMLPort homonym = port("Speed", "GUID-c-hom"); // no redefinition link
        IRPClass ref = reference(mother, homonym);

        PortPlan plan = RedefinedPortsService.computePlan(ref);

        assertEquals(List.of("Speed"), plan.toUpdate);
        assertTrue(plan.toAdd.isEmpty());
        assertTrue(plan.orphanCandidates.isEmpty(), "a homonym is adopted, never proposed for deletion");
        assertTrue(plan.renameCandidates.isEmpty());
    }

    @Test
    void computePlan_extraPort_isOrphanCandidate_andMissingMirrorIsToAdd() {
        IRPSysMLPort motherPort = port("Speed", "GUID-mp-ex");
        IRPClass mother = classOwning("Mother", "GUID-m-ex", motherPort);
        IRPSysMLPort extra = port("Manual", "GUID-c-ex");
        IRPClass ref = reference(mother, extra);

        PortPlan plan = RedefinedPortsService.computePlan(ref);

        assertEquals(List.of("Manual"), orphanNames(plan));
        assertSame(extra, plan.orphanCandidates.get(0).port);
        assertEquals("Ref", plan.orphanCandidates.get(0).referenceName);
        assertEquals(List.of("Speed"), plan.toAdd);
        assertFalse(plan.hasRemovals(), "toRemove is never filled: deletions need confirmation");
    }

    @Test
    void computePlan_ignoresOwnedElementsThatAreNotPorts() {
        IRPSysMLPort motherPort = port("Speed", "GUID-mp-np");
        IRPClass mother = classOwning("Mother", "GUID-m-np", motherPort);
        IRPClass ref = reference(mother);
        doReturn(collectionOf(mock(IRPModelElement.class))).when(ref).getPorts();

        PortPlan plan = RedefinedPortsService.computePlan(ref);

        assertTrue(plan.orphanCandidates.isEmpty());
        assertEquals(List.of("Speed"), plan.toAdd);
    }

    @Test
    void computePlan_unreadableReferencePorts_listsEveryMotherPortToAdd() {
        IRPSysMLPort p1 = port("P1", "GUID-mp-ur1");
        IRPSysMLPort p2 = port("P2", "GUID-mp-ur2");
        IRPClass mother = classOwning("Mother", "GUID-m-ur", p1, p2);
        IRPClass ref = reference(mother);
        when(ref.getPorts()).thenThrow(new RuntimeException("COM busy"));

        PortPlan plan = RedefinedPortsService.computePlan(ref);

        assertEquals(List.of("P1", "P2"), plan.toAdd);
    }

    @Test
    void computePlan_matchesTheMotherPortByGuid_notByProxyIdentity() {
        // Rhapsody hands out a new Java proxy per call: the child's redefinition
        // target and the mother's port are two proxies of the same element.
        IRPSysMLPort motherPort = port("Speed", "GUID-mp-proxy");
        IRPClass mother = classOwning("Mother", "GUID-m-proxy", motherPort);
        IRPClass motherProxy = mock(IRPClass.class);
        when(motherProxy.getGUID()).thenReturn("GUID-m-proxy");
        IRPSysMLPort motherPortProxy = port("Speed", "GUID-mp-proxy");
        when(motherPortProxy.getOwner()).thenReturn(motherProxy);
        IRPSysMLPort child = mirror("Speed", "GUID-c-proxy", motherPortProxy);
        IRPClass ref = reference(mother, child);

        PortPlan plan = RedefinedPortsService.computePlan(ref);

        assertFalse(plan.hasChanges());
    }

    @Test
    void computePlan_referencesLogicalSystem_isResolvedLikeAFunction() {
        IRPSysMLPort motherPort = port("Power", "GUID-mp-ls");
        IRPClass mother = classOwning("LS", "GUID-m-ls", motherPort);
        IRPClass ref = reference(REF_LOGICAL_SYSTEM, mother);

        assertEquals(List.of("Power"), RedefinedPortsService.computePlan(ref).toAdd);
    }

    @Test
    void computePlan_otherGeneralizationKinds_giveNoMother_emptyPlan() {
        IRPSysMLPort motherPort = port("Power", "GUID-mp-og");
        IRPClass mother = classOwning("F", "GUID-m-og", motherPort);
        IRPClass ref = reference("Redefines Function", mother);

        assertFalse(RedefinedPortsService.computePlan(ref).hasChanges());
    }

    @Test
    void computePlan_unreadableGeneralization_isSkipped_nextReferenceLinkUsed() {
        IRPSysMLPort motherPort = port("Power", "GUID-mp-ug");
        IRPClass mother = classOwning("F", "GUID-m-ug", motherPort);
        IRPGeneralization broken = mock(IRPGeneralization.class);
        when(broken.getUserDefinedMetaClass()).thenThrow(new RuntimeException("COM"));
        IRPGeneralization noBase = generalization(REF_FUNCTION, null);
        IRPClass ref = reference(mother);
        doReturn(collectionOf(broken, noBase, generalization(REF_FUNCTION, mother)))
                .when(ref).getGeneralizations();

        assertEquals(List.of("Power"), RedefinedPortsService.computePlan(ref).toAdd);
    }

    @Test
    void computePlan_motherIsNotAClass_emptyPlan() {
        IRPClass ref = reference(mock(IRPClassifier.class));

        assertFalse(RedefinedPortsService.computePlan(ref).hasChanges());
    }

    // ------------------------------------------------------------------
    // computePlan: the mother chain (ports the mother inherits)
    // ------------------------------------------------------------------

    @Test
    void computePlan_motherChainWithCycle_terminates_eachPortOnce() {
        IRPSysMLPort a = port("A1", "GUID-a1");
        IRPSysMLPort b = port("B1", "GUID-b1");
        IRPClass classA = classOwning("A", "GUID-A", a);
        IRPClass classB = classOwning("B", "GUID-B", b);
        inherits(classA, classB);
        inherits(classB, classA); // malformed model: must not loop forever
        IRPClass ref = reference(classA);

        PortPlan plan = RedefinedPortsService.computePlan(ref);

        assertEquals(List.of("A1", "B1"), plan.toAdd);
    }

    @Test
    void computePlan_diamondInheritance_sharedBasePortListedOnce() {
        IRPSysMLPort d = port("D1", "GUID-d1");
        IRPClass classD = classOwning("D", "GUID-D", d);
        IRPClass classB = classOwning("B", "GUID-Bd");
        IRPClass classC = classOwning("C", "GUID-Cd");
        inherits(classB, classD);
        inherits(classC, classD);
        IRPClass top = classOwning("Top", "GUID-Top");
        doReturn(collectionOf(generalization("Redefines Function", classB),
                generalization("Redefines Function", classC))).when(top).getGeneralizations();
        IRPClass ref = reference(top);

        assertEquals(List.of("D1"), RedefinedPortsService.computePlan(ref).toAdd);
    }

    @Test
    void computePlan_basePortShadowedByNameOnTheMother_onlyTheMothersIsListed() {
        IRPSysMLPort basePort = port("P1", "GUID-base-p1");
        IRPClass base = classOwning("Base", "GUID-base", basePort);
        IRPSysMLPort ownPort = port("P1", "GUID-own-p1"); // same name, no redefinition link
        IRPClass mother = classOwning("Mother", "GUID-mother-sh", ownPort);
        inherits(mother, base);
        IRPSysMLPort child = mirror("P1", "GUID-child-sh", ownPort);
        IRPClass ref = reference(mother, child);

        PortPlan plan = RedefinedPortsService.computePlan(ref);

        assertFalse(plan.hasChanges(), "the base port hidden by the mother's P1 must not be added again");
    }

    @Test
    void computePlan_unreadableBaseGeneralization_keepsTheChainFoundSoFar() {
        IRPSysMLPort ownPort = port("P1", "GUID-own-ub");
        IRPClass mother = classOwning("Mother", "GUID-mother-ub", ownPort);
        when(mother.getGeneralizations()).thenThrow(new RuntimeException("COM"));
        IRPClass ref = reference(mother);

        assertEquals(List.of("P1"), RedefinedPortsService.computePlan(ref).toAdd);
    }

    // ------------------------------------------------------------------
    // portsRedefining / redefinesPortOf
    // ------------------------------------------------------------------

    @Test
    void portsRedefining_keepsMirrorsOfTheMotherAndOfItsAncestors_only() {
        IRPSysMLPort basePort = port("B1", "GUID-pr-b1");
        IRPClass base = classOwning("Base", "GUID-pr-base", basePort);
        IRPSysMLPort motherPort = port("M1", "GUID-pr-m1");
        IRPClass mother = classOwning("Mother", "GUID-pr-mother", motherPort);
        inherits(mother, base);
        IRPSysMLPort foreignPort = port("X1", "GUID-pr-x1");
        classOwning("Other", "GUID-pr-other", foreignPort);

        IRPSysMLPort mirrorM = mirror("M1", "GUID-pr-cm", motherPort);
        IRPSysMLPort mirrorB = mirror("B1", "GUID-pr-cb", basePort);
        IRPSysMLPort foreign = mirror("X1", "GUID-pr-cx", foreignPort);
        IRPSysMLPort plain = port("Plain", "GUID-pr-cp");
        IRPClass ref = reference(mother, mirrorM, mirrorB, foreign, plain);

        assertEquals(List.of(mirrorM, mirrorB), RedefinedPortsService.portsRedefining(ref, mother));
        assertTrue(RedefinedPortsService.redefinesPortOf(mirrorB, mother));
        assertFalse(RedefinedPortsService.redefinesPortOf(foreign, mother));
        assertFalse(RedefinedPortsService.redefinesPortOf(plain, mother));
    }

    @Test
    void portsRedefining_nullOrUnreadableInputs_giveEmptyResult() {
        IRPClass mother = classOwning("Mother", "GUID-pr-null");
        IRPClass ref = reference(mother);
        doReturn(null).when(ref).getPorts();

        assertTrue(RedefinedPortsService.portsRedefining(null, mother).isEmpty());
        assertTrue(RedefinedPortsService.portsRedefining(ref, null).isEmpty());
        assertTrue(RedefinedPortsService.portsRedefining(ref, mother).isEmpty());
        assertFalse(RedefinedPortsService.redefinesPortOf(null, mother));
        assertFalse(RedefinedPortsService.redefinesPortOf(mock(IRPSysMLPort.class), null));
    }

    @Test
    void redefinesPortOf_unreadableRedefines_isFalse() {
        IRPClass mother = classOwning("Mother", "GUID-rp-ur");
        IRPSysMLPort p = mock(IRPSysMLPort.class);
        when(p.getRedefines()).thenThrow(new RuntimeException("COM"));

        assertFalse(RedefinedPortsService.redefinesPortOf(p, mother));
    }

    // ------------------------------------------------------------------
    // redefinePorts: what is written
    // ------------------------------------------------------------------

    @Test
    void redefinePorts_unlinkedHomonym_isReusedAndLinked_notRecreated() {
        IRPSysMLPort motherPort = port("Speed", "GUID-mp-rh");
        when(motherPort.getPortDirection()).thenReturn("out");
        IRPClass mother = classOwning("Mother", "GUID-m-rh", motherPort);
        IRPSysMLPort homonym = port("Speed", "GUID-c-rh");
        when(homonym.getPortDirection()).thenReturn("in");
        IRPClass ref = reference(mother, homonym);

        assertTrue(RedefinedPortsService.redefinePorts(ref));

        verify(ref, never()).addNewAggr(anyString(), anyString());
        verify(homonym, never()).deleteFromProject();
        verify(homonym).addRedefines(motherPort);
        verify(homonym).setPortDirection("out");
    }

    @Test
    void redefinePorts_identicalMirror_writesNoProperty() {
        IRPClassifier type = mock(IRPClassifier.class);
        when(type.getGUID()).thenReturn("GUID-type-id");
        IRPSysMLPort motherPort = port("Speed", "GUID-mp-id");
        when(motherPort.getType()).thenReturn(type);
        when(motherPort.getPortDirection()).thenReturn("in");
        when(motherPort.getDescription()).thenReturn("d");
        IRPClass mother = classOwning("Mother", "GUID-m-id", motherPort);
        IRPSysMLPort child = mirror("Speed", "GUID-c-id", motherPort);
        when(child.getType()).thenReturn(type);
        when(child.getPortDirection()).thenReturn("in");
        when(child.getDescription()).thenReturn("d");
        IRPClass ref = reference(mother, child);

        assertTrue(RedefinedPortsService.redefinePorts(ref));

        verify(child, never()).setType(any());
        verify(child, never()).setPortDirection(anyString());
        verify(child, never()).setDescription(anyString());
        verify(child, never()).addSpecificStereotype(any());
        verify(child, never()).removeStereotype(any());
    }

    @Test
    void redefinePorts_copiesDescriptionAndMissingStereotype_leavesNewTermAlone() {
        IRPStereotype dataFlow = mock(IRPStereotype.class);
        when(dataFlow.getGUID()).thenReturn("GUID-st-df");
        IRPStereotype flowPortTerm = mock(IRPStereotype.class);
        when(flowPortTerm.getGUID()).thenReturn("GUID-st-nt");
        when(flowPortTerm.getIsNewTerm()).thenReturn(1);

        IRPSysMLPort motherPort = port("Speed", "GUID-mp-st");
        when(motherPort.getDescription()).thenReturn("new text");
        doReturn(collectionOf(dataFlow)).when(motherPort).getStereotypes();
        IRPClass mother = classOwning("Mother", "GUID-m-st", motherPort);
        IRPSysMLPort child = mirror("Speed", "GUID-c-st", motherPort);
        when(child.getDescription()).thenReturn("old text");
        doReturn(collectionOf(flowPortTerm)).when(child).getStereotypes();
        IRPClass ref = reference(mother, child);

        assertTrue(RedefinedPortsService.redefinePorts(ref));

        verify(child).setDescription("new text");
        verify(child).addSpecificStereotype(dataFlow);
        verify(child, never()).removeStereotype(flowPortTerm);
    }

    @Test
    void redefinePorts_stereotypeWriteFails_otherPortsStillProcessed() {
        IRPStereotype st = mock(IRPStereotype.class);
        when(st.getGUID()).thenReturn("GUID-st-fail");
        IRPSysMLPort p1 = port("P1", "GUID-mp-sf1");
        doReturn(collectionOf(st)).when(p1).getStereotypes();
        IRPSysMLPort p2 = port("P2", "GUID-mp-sf2");
        when(p2.getPortDirection()).thenReturn("in");
        IRPClass mother = classOwning("Mother", "GUID-m-sf", p1, p2);
        IRPSysMLPort c1 = mirror("P1", "GUID-c-sf1", p1);
        doThrow(new RuntimeException("read only")).when(c1).addSpecificStereotype(st);
        IRPSysMLPort c2 = mirror("P2", "GUID-c-sf2", p2);
        IRPClass ref = reference(mother, c1, c2);

        assertTrue(RedefinedPortsService.redefinePorts(ref));

        verify(c2).setPortDirection("in");
    }

    @Test
    void redefinePorts_creationFails_returnsFalseForRetry_otherPortsStillCreated() {
        IRPSysMLPort p1 = port("P1", "GUID-mp-cf1");
        IRPSysMLPort p2 = port("P2", "GUID-mp-cf2");
        IRPClass mother = classOwning("Mother", "GUID-m-cf", p1, p2);
        IRPClass ref = reference(mother);
        when(ref.addNewAggr("Flow Port", "P1")).thenThrow(new RuntimeException("Rhapsody busy"));
        IRPSysMLPort created2 = port("P2", "GUID-created-cf2");
        when(ref.addNewAggr("Flow Port", "P2")).thenReturn(created2);

        assertFalse(RedefinedPortsService.redefinePorts(ref), "a transient failure must ask for a retry");

        verify(created2, atLeastOnce()).addRedefines(p2);
    }

    @Test
    void redefinePorts_propertyWritesThatThrow_areSwallowed() {
        IRPClassifier type = mock(IRPClassifier.class);
        when(type.getGUID()).thenReturn("GUID-type-thr");
        IRPSysMLPort motherPort = port("Speed", "GUID-mp-thr");
        when(motherPort.getType()).thenReturn(type);
        when(motherPort.getPortDirection()).thenReturn("in");
        when(motherPort.getDescription()).thenReturn("d");
        IRPClass mother = classOwning("Mother", "GUID-m-thr", motherPort);
        IRPSysMLPort child = mirror("Speed", "GUID-c-thr", motherPort);
        when(child.isReadOnly()).thenReturn(1);
        doThrow(new RuntimeException("ro")).when(child).setType(any());
        doThrow(new RuntimeException("ro")).when(child).setPortDirection(anyString());
        doThrow(new RuntimeException("ro")).when(child).setDescription(anyString());
        doThrow(new RuntimeException("already")).when(child).addRedefines(any());
        when(child.getStereotypes()).thenThrow(new RuntimeException("ro"));
        IRPClass ref = reference(mother, child);

        assertTrue(RedefinedPortsService.redefinePorts(ref));
    }

    // ------------------------------------------------------------------
    // Engineer-confirmed helpers
    // ------------------------------------------------------------------

    @Test
    void applyRenameToMother_copiesNameAndLabel() {
        IRPSysMLPort motherPort = port("Speed", "GUID-ar-m");
        when(motherPort.getDisplayName()).thenReturn("Vehicle speed");
        IRPSysMLPort child = port("Speed_old", "GUID-ar-c");

        RedefinedPortsService.applyRenameToMother(child, motherPort);

        verify(child).setName("Speed");
        verify(child).setDisplayName("Vehicle speed");
    }

    @Test
    void applyRenameToMother_blankMotherName_keepsName_clearsLabelWhenNone() {
        IRPSysMLPort motherPort = port("  ", "GUID-ar-blank");
        IRPSysMLPort child = port("Speed_old", "GUID-ar-c2");

        RedefinedPortsService.applyRenameToMother(child, motherPort);

        verify(child, never()).setName(anyString());
        verify(child).setDisplayName("");
    }

    @Test
    void applyRenameToMother_failuresAndNulls_neverThrow() {
        IRPSysMLPort motherPort = port("Speed", "GUID-ar-f");
        IRPSysMLPort child = port("Old", "GUID-ar-fc");
        doThrow(new RuntimeException("name clash")).when(child).setName(anyString());
        doThrow(new RuntimeException("ro")).when(child).setDisplayName(anyString());

        assertDoesNotThrow(() -> RedefinedPortsService.applyRenameToMother(child, motherPort));
        assertDoesNotThrow(() -> RedefinedPortsService.applyRenameToMother(null, motherPort));
        assertDoesNotThrow(() -> RedefinedPortsService.applyRenameToMother(child, null));
    }

    @Test
    void deleteOrphanPort_deletesThePort_neverThrows() {
        IRPSysMLPort orphan = port("Manual", "GUID-del");
        RedefinedPortsService.deleteOrphanPort(orphan);
        verify(orphan).deleteFromProject();

        IRPSysMLPort locked = port("Locked", "GUID-del-l");
        doThrow(new RuntimeException("locked unit")).when(locked).deleteFromProject();
        assertDoesNotThrow(() -> RedefinedPortsService.deleteOrphanPort(locked));
        assertDoesNotThrow(() -> RedefinedPortsService.deleteOrphanPort(null));
    }
}
