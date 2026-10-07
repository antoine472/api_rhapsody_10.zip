package test.unittest;

import org.junit.jupiter.api.Test;

import com.telelogic.rhapsody.core.IRPModelElement;

import main.gui.tools.FlowItemEditOps;
import main.gui.tools.FlowItemEditOps.OpResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Pure unit tests for {@link FlowItemEditOps} — the three Rhapsody model
 * mutations (rename / delete / move) triggered from the Flow Item selector
 * dialog. No live Rhapsody: {@link IRPModelElement} is a Mockito mock so the
 * tests assert exactly which API call each operation makes and how it guards
 * bad input and failures.
 */
class FlowItemEditOpsTest {

    // ── rename ────────────────────────────────────────────────────────────

    @Test
    void rename_setsTrimmedName_andReturnsSuccess() {
        IRPModelElement el = mock(IRPModelElement.class);

        OpResult r = FlowItemEditOps.rename(el, "  NewFlow  ");

        assertTrue(r.ok(), "rename with a valid name should succeed");
        verify(el).setName("NewFlow");
    }

    @Test
    void rename_clearsDisplayNameOverride_soLabelFollowsTheNewName() {
        IRPModelElement el = mock(IRPModelElement.class);

        OpResult r = FlowItemEditOps.rename(el, "NewFlow");

        assertTrue(r.ok());
        verify(el).setDisplayName("");
    }

    @Test
    void rename_displayNameApiThrows_stillReportsSuccess() {
        IRPModelElement el = mock(IRPModelElement.class);
        doThrow(new RuntimeException("boom")).when(el).setDisplayName(anyString());

        OpResult r = FlowItemEditOps.rename(el, "NewFlow");

        assertTrue(r.ok(), "a DisplayName sync failure must not fail the rename itself");
        verify(el).setName("NewFlow");
    }

    @Test
    void rename_nullElement_failsWithoutCallingApi() {
        OpResult r = FlowItemEditOps.rename(null, "Name");
        assertFalse(r.ok(), "rename on null element must fail");
        assertNotNull(r.error());
    }

    @Test
    void rename_blankName_failsWithoutCallingApi() {
        IRPModelElement el = mock(IRPModelElement.class);

        OpResult r = FlowItemEditOps.rename(el, "   ");

        assertFalse(r.ok(), "rename with a blank name must fail");
        verify(el, never()).setName(any());
    }

    @Test
    void rename_apiThrows_isReportedAsFailure() {
        IRPModelElement el = mock(IRPModelElement.class);
        doThrow(new RuntimeException("boom")).when(el).setName(anyString());

        OpResult r = FlowItemEditOps.rename(el, "X");

        assertFalse(r.ok(), "an API exception must be reported as failure, not propagated");
        assertNotNull(r.error());
    }

    // ── delete ────────────────────────────────────────────────────────────

    @Test
    void delete_callsDeleteFromProject_andReturnsSuccess() {
        IRPModelElement el = mock(IRPModelElement.class);

        OpResult r = FlowItemEditOps.delete(el);

        assertTrue(r.ok(), "delete on a valid element should succeed");
        verify(el).deleteFromProject();
    }

    @Test
    void delete_nullElement_failsWithoutCallingApi() {
        OpResult r = FlowItemEditOps.delete(null);
        assertFalse(r.ok(), "delete on null element must fail");
        assertNotNull(r.error());
    }

    @Test
    void delete_apiThrows_isReportedAsFailure() {
        IRPModelElement el = mock(IRPModelElement.class);
        doThrow(new RuntimeException("boom")).when(el).deleteFromProject();

        OpResult r = FlowItemEditOps.delete(el);

        assertFalse(r.ok());
        assertNotNull(r.error());
    }

    // ── move ──────────────────────────────────────────────────────────────

    @Test
    void move_setsOwner_andReturnsSuccess() {
        IRPModelElement el       = mock(IRPModelElement.class);
        IRPModelElement newOwner = mock(IRPModelElement.class);

        OpResult r = FlowItemEditOps.move(el, newOwner);

        assertTrue(r.ok(), "move to a valid owner should succeed");
        verify(el).setOwner(newOwner);
    }

    @Test
    void move_nullElement_failsWithoutCallingApi() {
        IRPModelElement newOwner = mock(IRPModelElement.class);

        OpResult r = FlowItemEditOps.move(null, newOwner);

        assertFalse(r.ok(), "move of a null element must fail");
        verify(newOwner, never()).setOwner(any());
    }

    @Test
    void move_nullOwner_failsWithoutCallingApi() {
        IRPModelElement el = mock(IRPModelElement.class);

        OpResult r = FlowItemEditOps.move(el, null);

        assertFalse(r.ok(), "move to a null owner must fail");
        verify(el, never()).setOwner(any());
    }

    @Test
    void move_apiThrows_isReportedAsFailure() {
        IRPModelElement el       = mock(IRPModelElement.class);
        IRPModelElement newOwner = mock(IRPModelElement.class);
        doThrow(new RuntimeException("boom")).when(el).setOwner(any());

        OpResult r = FlowItemEditOps.move(el, newOwner);

        assertFalse(r.ok());
        assertEquals(false, r.ok());
        assertNotNull(r.error());
    }
}
