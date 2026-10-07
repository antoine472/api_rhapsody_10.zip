package test.unittest;

import org.junit.jupiter.api.Test;

import com.telelogic.rhapsody.core.IRPApplication;
import com.telelogic.rhapsody.core.IRPFlow;
import com.telelogic.rhapsody.core.IRPModelElement;

import tools.ReverseFlowDirection;

import static test.unittest.RhpTestMocks.collectionOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests (no live Rhapsody) of the "Reverse flow direction" tool:
 * toEnd1 and toEnd2 swap, anything else is left alone.
 */
class ReverseFlowDirectionTest {

    private static IRPFlow flow(String direction) {
        IRPFlow f = mock(IRPFlow.class);
        when(f.getDirection()).thenReturn(direction);
        when(f.getFullPathName()).thenReturn("Pkg::flow");
        return f;
    }

    private static void runOn(Object... selection) {
        IRPApplication app = mock(IRPApplication.class);
        doReturn(collectionOf(selection)).when(app).getListOfSelectedElements();
        new ReverseFlowDirection(app).execute();
    }

    @Test
    void execute_swapsToEnd1AndToEnd2_caseInsensitive() {
        IRPFlow toEnd1 = flow("toEnd1");
        IRPFlow toEnd2 = flow("TOEND2");

        runOn(toEnd1, toEnd2);

        verify(toEnd1).setDirection("toEnd2");
        verify(toEnd2).setDirection("toEnd1");
    }

    @Test
    void execute_ignoresSelectedElementsThatAreNotFlows() {
        IRPFlow f = flow("toEnd2");
        IRPModelElement notAFlow = mock(IRPModelElement.class);

        runOn(notAFlow, f);

        verify(f).setDirection("toEnd1");
    }

    @Test
    void execute_leavesBidirectionalBlankAndUnknownDirectionsAlone() {
        IRPFlow bidir = flow("bidirectional");
        IRPFlow blank = flow(" ");
        IRPFlow none = flow(null);
        IRPFlow unknown = flow("sideways");

        runOn(bidir, blank, none, unknown);

        for (IRPFlow f : new IRPFlow[] { bidir, blank, none, unknown }) {
            verify(f, never()).setDirection(anyString());
        }
    }

    @Test
    void toolContract_undoableCommand() {
        ReverseFlowDirection tool = new ReverseFlowDirection(mock(IRPApplication.class));
        assertEquals(ReverseFlowDirection.COMMAND, tool.commandName());
        assertTrue(tool.isUndoable());
    }
}
