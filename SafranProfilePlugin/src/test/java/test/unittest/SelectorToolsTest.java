package test.unittest;

import org.junit.jupiter.api.Test;

import com.telelogic.rhapsody.core.IRPApplication;

import tools.RhapsodyTool;
import tools.SelectFlowItemDefinition;
import tools.SelectFunctionDefinition;
import tools.SelectModeDefinition;
import tools.SetReferenceDefinition;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Pure unit tests (no live Rhapsody) of the plugin-level contract of the four
 * selector tools: their menu command only opens the non-modal selector and
 * returns, so SafranProfilePlugin must not wrap it in a transaction or undo it
 * ({@code isUndoable() == false}); each OK / Apply / in-dialog edit gets its
 * own undo transaction on the selector worker instead.
 */
class SelectorToolsTest {

    private static final IRPApplication APP = mock(IRPApplication.class);

    @Test
    void selectorTools_areNotUndoableAtPluginLevel() {
        RhapsodyTool[] selectors = {
                new SelectFlowItemDefinition(APP),
                new SelectFunctionDefinition(APP),
                new SelectModeDefinition(APP),
                new SetReferenceDefinition(APP),
        };
        for (RhapsodyTool tool : selectors) {
            assertFalse(tool.isUndoable(), tool.commandName() + " must not run inside the plugin transaction");
            assertTrue(tool.isInteractive(), tool.commandName() + " opens a dialog");
        }
    }
}
