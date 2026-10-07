package test.unittest;

import org.junit.jupiter.api.Test;

import com.telelogic.rhapsody.core.IRPClass;
import com.telelogic.rhapsody.core.IRPDependency;
import com.telelogic.rhapsody.core.IRPFlow;
import com.telelogic.rhapsody.core.IRPMessage;
import com.telelogic.rhapsody.core.IRPModelElement;
import com.telelogic.rhapsody.core.IRPStereotype;
import com.telelogic.rhapsody.core.IRPSysMLPort;

import logging.RhapsodyLogger;
import tools.strategies.FlowItemSelectionFlow;
import tools.strategies.FlowItemSelectionFlowPort;
import tools.strategies.FlowItemSelectionMessage;
import tools.strategies.FlowItemSelectionSupport;

import static test.unittest.RhpTestMocks.collectionOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests (no live Rhapsody) of the OK / Apply actions of the Flow Item
 * selectors. OK and Apply both run them, so each one must be idempotent: OK
 * right after Apply on the same item changes nothing and reports no change
 * (each OK / Apply runs in its own undo transaction, and a no-op must not
 * leave a spurious undo entry or a second "set" toast).
 *
 * <p>Rhapsody types are Mockito mocks. No GUID is stubbed, so the same mock
 * instance stands for the same model element (identity decides). Every
 * collection is stubbed with {@code doReturn(...).when(mock)} (see
 * {@link RhpTestMocks}).</p>
 */
class FlowItemAssignmentTest {

    private static final RhapsodyLogger LOG = RhapsodyLogger.getInstance();

    // ── helpers ───────────────────────────────────────────────────────────

    private static IRPStereotype stereotype(boolean newTerm) {
        IRPStereotype st = mock(IRPStereotype.class);
        when(st.getIsNewTerm()).thenReturn(newTerm ? 1 : 0);
        return st;
    }

    /** A Flow Item named {@code name} (name and label) carrying {@code stereotypes}. */
    private static IRPClass flowItem(String name, IRPStereotype... stereotypes) {
        IRPClass fi = mock(IRPClass.class);
        when(fi.getName()).thenReturn(name);
        when(fi.getDisplayName()).thenReturn(name);
        when(fi.getUserDefinedMetaClass()).thenReturn("Flow Item");
        doReturn(collectionOf((Object[]) stereotypes)).when(fi).getStereotypes();
        return fi;
    }

    /** A requirement-like element: never a Flow Item. */
    private static IRPModelElement requirement() {
        IRPModelElement req = mock(IRPModelElement.class);
        when(req.getUserDefinedMetaClass()).thenReturn("Requirement");
        return req;
    }

    private static IRPDependency dependencyTo(IRPModelElement dependsOn) {
        IRPDependency dep = mock(IRPDependency.class);
        when(dep.getDependsOn()).thenReturn(dependsOn);
        return dep;
    }

    // ── syncStereotypes ───────────────────────────────────────────────────

    @Test
    void syncStereotypes_addsMissing_removesOthers_leavesNewTerms() {
        IRPStereotype shared        = stereotype(false);
        IRPStereotype onlyOnItem    = stereotype(false);
        IRPStereotype onlyOnTarget  = stereotype(false);
        IRPStereotype targetNewTerm = stereotype(true);
        IRPStereotype itemNewTerm   = stereotype(true);
        IRPClass item = flowItem("Speed", shared, onlyOnItem, itemNewTerm);
        IRPSysMLPort target = mock(IRPSysMLPort.class);
        doReturn(collectionOf(shared, onlyOnTarget, targetNewTerm)).when(target).getStereotypes();

        assertTrue(FlowItemSelectionSupport.syncStereotypes(target, item, LOG));

        verify(target).removeStereotype(onlyOnTarget);
        verify(target, never()).removeStereotype(shared);
        verify(target, never()).removeStereotype(targetNewTerm);
        verify(target).addSpecificStereotype(onlyOnItem);
        verify(target, never()).addSpecificStereotype(shared);
        verify(target, never()).addSpecificStereotype(itemNewTerm);
    }

    @Test
    void syncStereotypes_alreadyInSync_changesNothing() {
        IRPStereotype shared = stereotype(false);
        IRPClass item = flowItem("Speed", shared);
        IRPSysMLPort target = mock(IRPSysMLPort.class);
        doReturn(collectionOf(shared)).when(target).getStereotypes();

        assertFalse(FlowItemSelectionSupport.syncStereotypes(target, item, LOG));

        verify(target, never()).removeStereotype(any());
        verify(target, never()).addSpecificStereotype(any());
    }

    // ── syncNames ─────────────────────────────────────────────────────────

    @Test
    void syncNames_sameNameAndLabel_changesNothing() {
        IRPClass item = flowItem("Speed");
        IRPMessage msg = mock(IRPMessage.class);
        when(msg.getName()).thenReturn("Speed");
        when(msg.getDisplayName()).thenReturn("Speed");

        assertFalse(FlowItemSelectionSupport.syncNames(msg, item, LOG));

        verify(msg, never()).setName(anyString());
        verify(msg, never()).setDisplayName(anyString());
    }

    @Test
    void syncNames_otherName_copiesNameAndLabel() {
        IRPClass item = flowItem("Speed");
        IRPMessage msg = mock(IRPMessage.class);
        when(msg.getName()).thenReturn("msg_1");
        when(msg.getDisplayName()).thenReturn("msg_1");

        assertTrue(FlowItemSelectionSupport.syncNames(msg, item, LOG));

        verify(msg).setName("Speed");
        verify(msg).setDisplayName("Speed");
    }

    // ── SysML port ────────────────────────────────────────────────────────

    @Test
    void port_alreadyTypedAndInSync_isQuietNoOp() {
        IRPStereotype st = stereotype(false);
        IRPClass item = flowItem("Speed", st);
        IRPSysMLPort port = mock(IRPSysMLPort.class);
        when(port.getType()).thenReturn(item);
        doReturn(collectionOf(st)).when(port).getStereotypes();

        assertFalse(FlowItemSelectionFlowPort.assignTo(port, item, LOG));

        verify(port, never()).setType(any());
        verify(port, never()).addSpecificStereotype(any());
        verify(port, never()).removeStereotype(any());
    }

    @Test
    void port_typedByAnotherItem_getsTheItemAndItsStereotypes() {
        IRPStereotype st = stereotype(false);
        IRPClass item = flowItem("Speed", st);
        IRPClass other = flowItem("Torque");
        IRPSysMLPort port = mock(IRPSysMLPort.class);
        when(port.getType()).thenReturn(other);
        doReturn(collectionOf()).when(port).getStereotypes();

        assertTrue(FlowItemSelectionFlowPort.assignTo(port, item, LOG));

        verify(port).setType(item);
        verify(port).addSpecificStereotype(st);
    }

    // ── Flow ──────────────────────────────────────────────────────────────

    @Test
    void flow_alreadyConveyingTheItem_isQuietNoOp() {
        IRPClass item = flowItem("Speed");
        IRPFlow flow = mock(IRPFlow.class);
        doReturn(collectionOf(item)).when(flow).getConveyed();

        assertFalse(FlowItemSelectionFlow.assignTo(flow, item, LOG));

        verify(flow, never()).removeConveyed(any());
        verify(flow, never()).addConveyed(any());
    }

    @Test
    void flow_conveyingAnotherItem_conveysTheChosenOneOnly() {
        IRPClass item = flowItem("Speed");
        IRPClass other = flowItem("Torque");
        IRPFlow flow = mock(IRPFlow.class);
        doReturn(collectionOf(other)).when(flow).getConveyed();

        assertTrue(FlowItemSelectionFlow.assignTo(flow, item, LOG));

        verify(flow).removeConveyed(other);
        verify(flow).addConveyed(item);
    }

    @Test
    void flow_conveyingTheItemAndAnother_dropsTheOtherOnly() {
        IRPClass item = flowItem("Speed");
        IRPClass other = flowItem("Torque");
        IRPFlow flow = mock(IRPFlow.class);
        doReturn(collectionOf(item, other)).when(flow).getConveyed();

        assertTrue(FlowItemSelectionFlow.assignTo(flow, item, LOG));

        verify(flow).removeConveyed(other);
        verify(flow, never()).removeConveyed(item);
        verify(flow, never()).addConveyed(any());
    }

    // ── Message ───────────────────────────────────────────────────────────

    @Test
    void message_alreadyLinkedAndInSync_isQuietNoOp() {
        IRPStereotype st = stereotype(false);
        IRPClass item = flowItem("Speed", st);
        IRPDependency toItem = dependencyTo(item);
        IRPDependency toRequirement = dependencyTo(requirement());
        IRPMessage msg = mock(IRPMessage.class);
        doReturn(collectionOf(toItem, toRequirement)).when(msg).getDependencies();
        doReturn(collectionOf(st)).when(msg).getStereotypes();
        when(msg.getName()).thenReturn("Speed");
        when(msg.getDisplayName()).thenReturn("Speed");

        assertFalse(FlowItemSelectionMessage.assignTo(msg, item, LOG));

        verify(toItem, never()).deleteFromProject();
        verify(toRequirement, never()).deleteFromProject();
        verify(msg, never()).addDependencyTo(any());
        verify(msg, never()).setName(anyString());
    }

    @Test
    void message_linkedToAnotherItem_isRelinked_otherLinksKept() {
        IRPClass item = flowItem("Speed");
        IRPClass other = flowItem("Torque");
        IRPDependency toOther = dependencyTo(other);
        IRPDependency toRequirement = dependencyTo(requirement());
        IRPMessage msg = mock(IRPMessage.class);
        doReturn(collectionOf(toOther, toRequirement)).when(msg).getDependencies();
        doReturn(collectionOf()).when(msg).getStereotypes();
        when(msg.getName()).thenReturn("Torque");
        when(msg.getDisplayName()).thenReturn("Torque");

        assertTrue(FlowItemSelectionMessage.assignTo(msg, item, LOG));

        verify(toOther).deleteFromProject();
        verify(toRequirement, never()).deleteFromProject();
        verify(msg).addDependencyTo(item);
        verify(msg).setName("Speed");
    }

    // -- edge cases: never throw, never duplicate ------------------------

    @Test
    void port_itemIsNotAClassifier_isRefused_portUntouched() {
        IRPModelElement notAClassifier = mock(IRPModelElement.class);
        IRPSysMLPort port = mock(IRPSysMLPort.class);

        assertFalse(FlowItemSelectionFlowPort.assignTo(port, notAClassifier, LOG));

        verify(port, never()).setType(any());
        verify(port, never()).addSpecificStereotype(any());
    }

    @Test
    void port_typedByAnotherProxyOfTheItem_isNotRetyped() {
        IRPClass item = flowItem("Speed");
        when(item.getGUID()).thenReturn("GUID-speed");
        IRPClass itemProxy = flowItem("Speed");
        when(itemProxy.getGUID()).thenReturn("GUID-speed");
        IRPSysMLPort port = mock(IRPSysMLPort.class);
        when(port.getType()).thenReturn(itemProxy);
        doReturn(collectionOf()).when(port).getStereotypes();

        assertFalse(FlowItemSelectionFlowPort.assignTo(port, item, LOG));

        verify(port, never()).setType(any());
    }

    @Test
    void port_setTypeRejected_returnsWithoutSyncingStereotypes() {
        IRPStereotype st = stereotype(false);
        IRPClass item = flowItem("Speed", st);
        IRPSysMLPort port = mock(IRPSysMLPort.class);
        doThrow(new RuntimeException("read only unit")).when(port).setType(item);

        assertFalse(FlowItemSelectionFlowPort.assignTo(port, item, LOG));

        verify(port, never()).addSpecificStereotype(any());
    }

    @Test
    void flow_withoutConveyedCollection_conveysTheItem() {
        IRPClass item = flowItem("Speed");
        IRPFlow flow = mock(IRPFlow.class); // getConveyed() -> null

        assertTrue(FlowItemSelectionFlow.assignTo(flow, item, LOG));

        verify(flow).addConveyed(item);
    }

    @Test
    void flow_conveyingTheItemTwice_dropsTheDuplicate() {
        IRPClass item = flowItem("Speed");
        IRPFlow flow = mock(IRPFlow.class);
        doReturn(collectionOf(item, item, "not an element")).when(flow).getConveyed();

        assertTrue(FlowItemSelectionFlow.assignTo(flow, item, LOG));

        verify(flow).removeConveyed(item);
        verify(flow, never()).addConveyed(any());
    }

    @Test
    void flow_addConveyedRejected_returnsFalse_doesNotThrow() {
        IRPClass item = flowItem("Speed");
        IRPFlow flow = mock(IRPFlow.class);
        doReturn(collectionOf()).when(flow).getConveyed();
        doThrow(new RuntimeException("read only unit")).when(flow).addConveyed(item);

        assertFalse(FlowItemSelectionFlow.assignTo(flow, item, LOG));
    }

    @Test
    void message_withoutDependencies_isLinkedAndRenamed() {
        IRPClass item = flowItem("Speed");
        IRPMessage msg = mock(IRPMessage.class); // getDependencies() -> null
        doReturn(collectionOf()).when(msg).getStereotypes();

        assertTrue(FlowItemSelectionMessage.assignTo(msg, item, LOG));

        verify(msg).addDependencyTo(item);
        verify(msg).setName("Speed");
    }

    @Test
    void message_linkedTwiceToTheItem_keepsOneLink_keepsUnresolvedDependencies() {
        IRPClass item = flowItem("Speed");
        IRPDependency first = dependencyTo(item);
        IRPDependency duplicate = dependencyTo(item);
        IRPDependency dangling = dependencyTo(null);
        IRPDependency unreadable = mock(IRPDependency.class);
        when(unreadable.getDependsOn()).thenThrow(new RuntimeException("COM"));
        IRPMessage msg = mock(IRPMessage.class);
        doReturn(collectionOf(first, duplicate, dangling, unreadable, "x")).when(msg).getDependencies();
        doReturn(collectionOf()).when(msg).getStereotypes();
        when(msg.getName()).thenReturn("Speed");
        when(msg.getDisplayName()).thenReturn("Speed");

        assertTrue(FlowItemSelectionMessage.assignTo(msg, item, LOG));

        verify(first, never()).deleteFromProject();
        verify(duplicate).deleteFromProject();
        verify(dangling, never()).deleteFromProject();
        verify(unreadable, never()).deleteFromProject();
        verify(msg, never()).addDependencyTo(any());
    }

    @Test
    void message_linkRejected_returnsFalse_namesNotTouched() {
        IRPClass item = flowItem("Speed");
        IRPMessage msg = mock(IRPMessage.class);
        doReturn(collectionOf()).when(msg).getDependencies();
        doThrow(new RuntimeException("read only unit")).when(msg).addDependencyTo(item);

        assertFalse(FlowItemSelectionMessage.assignTo(msg, item, LOG));

        verify(msg, never()).setName(anyString());
    }
}
