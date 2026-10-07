package test.unittest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import com.telelogic.rhapsody.core.IRPClass;
import com.telelogic.rhapsody.core.IRPCollection;
import com.telelogic.rhapsody.core.IRPModelElement;
import com.telelogic.rhapsody.core.IRPProject;
import com.telelogic.rhapsody.core.IRPStereotype;
import com.telelogic.rhapsody.core.IRPSysMLPort;

import logging.RhapsodyLogger;
import tools.strategies.FlowItemSelectionSupport;

import static test.unittest.RhpTestMocks.collectionOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests (no live Rhapsody) of the shared helpers of the Flow Item
 * selectors: the session cache of {@code findFlowItems}, element identity,
 * safe names, and the edge cases of {@code syncNames} / {@code syncStereotypes}
 * (the nominal cases are in {@link FlowItemAssignmentTest}).
 *
 * <p>A failure inside a sync is reported with a toast; with
 * {@code -Djava.awt.headless=true} the toast cannot be built and is skipped
 * (the report swallows it), otherwise it briefly shows on screen.</p>
 */
class FlowItemSelectionSupportTest {

    private static final RhapsodyLogger LOG = RhapsodyLogger.getInstance();

    @BeforeEach
    @AfterEach
    void clearSessionCache() {
        FlowItemSelectionSupport.invalidateCache();
    }

    private static IRPClass classWithUdmc(String udmc) {
        IRPClass c = mock(IRPClass.class);
        when(c.getUserDefinedMetaClass()).thenReturn(udmc);
        return c;
    }

    private static IRPProject projectWithClasses(String guid, Object... classes) {
        IRPProject p = mock(IRPProject.class);
        when(p.getGUID()).thenReturn(guid);
        doReturn(collectionOf(classes)).when(p).getNestedElementsByMetaClass("Class", 1);
        return p;
    }

    private static IRPStereotype stereotype(String guid, boolean newTerm) {
        IRPStereotype st = mock(IRPStereotype.class);
        when(st.getGUID()).thenReturn(guid);
        when(st.getIsNewTerm()).thenReturn(newTerm ? 1 : 0);
        return st;
    }

    // ------------------------------------------------------------------
    // findFlowItems
    // ------------------------------------------------------------------

    @Test
    void findFlowItems_keepsOnlyClassesWhoseUdmcIsFlowItem() {
        IRPClass item = classWithUdmc("Flow Item");
        IRPClass block = classWithUdmc("Block");
        IRPClass broken = mock(IRPClass.class);
        when(broken.getUserDefinedMetaClass()).thenThrow(new RuntimeException("COM"));
        IRPModelElement notAClass = mock(IRPModelElement.class);
        IRPProject project = projectWithClasses("GUID-prj", block, item, broken, notAClass);

        List<IRPModelElement> found = FlowItemSelectionSupport.findFlowItems(project, LOG);

        assertEquals(List.of(item), found);
        assertThrows(UnsupportedOperationException.class, () -> found.add(block));
    }

    @Test
    void findFlowItems_sameProject_isServedFromTheCache_untilInvalidated() {
        IRPProject project = projectWithClasses("GUID-prj-cache", classWithUdmc("Flow Item"));

        List<IRPModelElement> first = FlowItemSelectionSupport.findFlowItems(project, LOG);
        List<IRPModelElement> second = FlowItemSelectionSupport.findFlowItems(project, null);

        assertSame(first, second);
        verify(project, times(1)).getNestedElementsByMetaClass(anyString(), anyInt());

        FlowItemSelectionSupport.invalidateCache();
        FlowItemSelectionSupport.findFlowItems(project, LOG);
        verify(project, times(2)).getNestedElementsByMetaClass(anyString(), anyInt());
    }

    @Test
    void findFlowItems_otherProject_isRescanned() {
        IRPClass itemA = classWithUdmc("Flow Item");
        IRPClass itemB = classWithUdmc("Flow Item");
        IRPProject a = projectWithClasses("GUID-prj-a", itemA);
        IRPProject b = projectWithClasses("GUID-prj-b", itemB);

        FlowItemSelectionSupport.findFlowItems(a, LOG);

        assertEquals(List.of(itemB), FlowItemSelectionSupport.findFlowItems(b, LOG));
    }

    @Test
    void findFlowItems_blankGuid_fallsBackOnFullPathAsCacheKey() {
        IRPProject project = projectWithClasses("  ", classWithUdmc("Flow Item"));
        when(project.getFullPathName()).thenReturn("MyProject");

        FlowItemSelectionSupport.findFlowItems(project, LOG);
        FlowItemSelectionSupport.findFlowItems(project, LOG);

        verify(project, times(1)).getNestedElementsByMetaClass(anyString(), anyInt());
    }

    @Test
    void findFlowItems_noProjectOrFailedScan_givesEmptyList() {
        IRPProject failing = mock(IRPProject.class);
        when(failing.getGUID()).thenReturn("GUID-prj-fail");
        when(failing.getNestedElementsByMetaClass(anyString(), anyInt()))
                .thenThrow(new RuntimeException("COM busy"));

        assertTrue(FlowItemSelectionSupport.findFlowItems(null, LOG).isEmpty());
        assertTrue(FlowItemSelectionSupport.findFlowItems(failing, null).isEmpty());
    }

    /**
     * A scan that fails (transient COM error) must not be cached: otherwise
     * the selector would show no Flow Item until something invalidates the
     * cache. The next call scans again and finds the items.
     */
    @Test
    void findFlowItems_failedScan_isNotCached_nextCallRescans() {
        IRPClass item = mock(IRPClass.class);
        when(item.getUserDefinedMetaClass()).thenReturn("Flow Item");
        IRPCollection classes = collectionOf(item);
        IRPProject project = mock(IRPProject.class);
        when(project.getGUID()).thenReturn("GUID-prj-transient");
        when(project.getNestedElementsByMetaClass(anyString(), anyInt()))
                .thenThrow(new RuntimeException("COM busy"))
                .thenReturn(classes);

        assertTrue(FlowItemSelectionSupport.findFlowItems(project, LOG).isEmpty());
        assertEquals(List.of(item), FlowItemSelectionSupport.findFlowItems(project, LOG));
    }

    // ------------------------------------------------------------------
    // sameElement / safeName
    // ------------------------------------------------------------------

    @Test
    void sameElement_sameProxyOrSameGuid_only() {
        IRPModelElement a = mock(IRPModelElement.class);
        when(a.getGUID()).thenReturn("GUID-1");
        IRPModelElement aProxy = mock(IRPModelElement.class);
        when(aProxy.getGUID()).thenReturn("GUID-1");
        IRPModelElement other = mock(IRPModelElement.class);
        when(other.getGUID()).thenReturn("GUID-2");
        IRPModelElement blank1 = mock(IRPModelElement.class);
        when(blank1.getGUID()).thenReturn(" ");
        IRPModelElement blank2 = mock(IRPModelElement.class);
        when(blank2.getGUID()).thenReturn(" ");
        IRPModelElement broken = mock(IRPModelElement.class);
        when(broken.getGUID()).thenThrow(new RuntimeException("COM"));

        assertTrue(FlowItemSelectionSupport.sameElement(a, a));
        assertTrue(FlowItemSelectionSupport.sameElement(a, aProxy));
        assertTrue(FlowItemSelectionSupport.sameElement(null, null));
        assertFalse(FlowItemSelectionSupport.sameElement(a, other));
        assertFalse(FlowItemSelectionSupport.sameElement(a, null));
        assertFalse(FlowItemSelectionSupport.sameElement(null, a));
        assertFalse(FlowItemSelectionSupport.sameElement(blank1, blank2), "a blank GUID identifies nothing");
        assertFalse(FlowItemSelectionSupport.sameElement(broken, a));
    }

    @Test
    void safeName_prefersLabel_thenName_thenPlaceholder() {
        IRPModelElement labelled = mock(IRPModelElement.class);
        when(labelled.getDisplayName()).thenReturn("Vehicle speed");
        when(labelled.getName()).thenReturn("speed");
        IRPModelElement unlabelled = mock(IRPModelElement.class);
        when(unlabelled.getDisplayName()).thenReturn(" ");
        when(unlabelled.getName()).thenReturn("speed");
        IRPModelElement broken = mock(IRPModelElement.class);
        when(broken.getDisplayName()).thenThrow(new RuntimeException("COM"));
        when(broken.getName()).thenThrow(new RuntimeException("COM"));

        assertEquals("Vehicle speed", FlowItemSelectionSupport.safeName(labelled));
        assertEquals("speed", FlowItemSelectionSupport.safeName(unlabelled));
        assertEquals("<unnamed>", FlowItemSelectionSupport.safeName(mock(IRPModelElement.class)));
        assertEquals("<unnamed>", FlowItemSelectionSupport.safeName(broken));
        assertEquals("<null>", FlowItemSelectionSupport.safeName(null));
    }

    // ------------------------------------------------------------------
    // syncNames: edge cases
    // ------------------------------------------------------------------

    @Test
    void syncNames_nullArguments_changeNothing() {
        IRPModelElement el = mock(IRPModelElement.class);
        assertFalse(FlowItemSelectionSupport.syncNames(null, el, LOG));
        assertFalse(FlowItemSelectionSupport.syncNames(el, null, LOG));
    }

    @Test
    void syncNames_blankOrUnreadableSource_isNeverCopied() {
        IRPModelElement item = mock(IRPModelElement.class);
        when(item.getName()).thenReturn("  ");
        when(item.getDisplayName()).thenThrow(new RuntimeException("COM"));
        IRPModelElement target = mock(IRPModelElement.class);
        when(target.getName()).thenReturn("msg_1");

        assertFalse(FlowItemSelectionSupport.syncNames(target, item, LOG));

        verify(target, never()).setName(anyString());
        verify(target, never()).setDisplayName(anyString());
    }

    @Test
    void syncNames_onlyTheLabelDiffers_copiesTheLabelOnly() {
        IRPModelElement item = mock(IRPModelElement.class);
        when(item.getName()).thenReturn("Speed");
        when(item.getDisplayName()).thenReturn("Vehicle speed");
        IRPModelElement target = mock(IRPModelElement.class);
        when(target.getName()).thenReturn("Speed");
        when(target.getDisplayName()).thenReturn("Speed");

        assertTrue(FlowItemSelectionSupport.syncNames(target, item, LOG));

        verify(target, never()).setName(anyString());
        verify(target).setDisplayName("Vehicle speed");
    }

    @Test
    void syncNames_setNameRejected_stillCopiesTheLabel() {
        IRPModelElement item = mock(IRPModelElement.class);
        when(item.getName()).thenReturn("Speed");
        when(item.getDisplayName()).thenReturn("Vehicle speed");
        IRPModelElement target = mock(IRPModelElement.class);
        when(target.getName()).thenReturn("msg_1");
        doThrow(new RuntimeException("name already used")).when(target).setName("Speed");

        assertTrue(FlowItemSelectionSupport.syncNames(target, item, null));

        verify(target).setDisplayName("Vehicle speed");
    }

    // ------------------------------------------------------------------
    // syncStereotypes: edge cases
    // ------------------------------------------------------------------

    @Test
    void syncStereotypes_sameStereotypeThroughAnotherProxy_isInSync() {
        IRPStereotype onItem = stereotype("GUID-st", false);
        IRPStereotype onTarget = stereotype("GUID-st", false);
        IRPModelElement item = mock(IRPModelElement.class);
        doReturn(collectionOf(onItem)).when(item).getStereotypes();
        IRPSysMLPort target = mock(IRPSysMLPort.class);
        doReturn(collectionOf(onTarget)).when(target).getStereotypes();

        assertFalse(FlowItemSelectionSupport.syncStereotypes(target, item, LOG));

        verify(target, never()).removeStereotype(any());
        verify(target, never()).addSpecificStereotype(any());
    }

    @Test
    void syncStereotypes_targetWithoutStereotypeCollection_getsTheItemOnes() {
        IRPStereotype st = stereotype("GUID-st2", false);
        IRPModelElement item = mock(IRPModelElement.class);
        doReturn(collectionOf(st, "not a stereotype")).when(item).getStereotypes();
        IRPSysMLPort target = mock(IRPSysMLPort.class); // getStereotypes() -> null

        assertTrue(FlowItemSelectionSupport.syncStereotypes(target, item, LOG));

        verify(target).addSpecificStereotype(st);
    }

    @Test
    void syncStereotypes_writeRejected_stopsWithoutThrowing_reportsWhatWasDone() {
        IRPStereotype stale = stereotype("GUID-stale", false);
        IRPStereotype wanted = stereotype("GUID-wanted", false);
        IRPModelElement item = mock(IRPModelElement.class);
        doReturn(collectionOf(wanted)).when(item).getStereotypes();
        IRPSysMLPort target = mock(IRPSysMLPort.class);
        doReturn(collectionOf(stale)).when(target).getStereotypes();
        doThrow(new RuntimeException("read only unit")).when(target).addSpecificStereotype(wanted);

        assertTrue(FlowItemSelectionSupport.syncStereotypes(target, item, null),
                "the stale stereotype was removed before the failure");

        verify(target).removeStereotype(stale);
    }

    @Test
    void reportFailure_neverThrows_evenWithoutLogger() {
        FlowItemSelectionSupport.reportFailure(null, "message");
        FlowItemSelectionSupport.reportFailure(LOG, "what", new RuntimeException("why"));
    }
}
