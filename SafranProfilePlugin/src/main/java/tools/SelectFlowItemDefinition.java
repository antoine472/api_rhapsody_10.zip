package tools;

import com.telelogic.rhapsody.core.IRPApplication;
import com.telelogic.rhapsody.core.IRPFlow;
import com.telelogic.rhapsody.core.IRPMessage;
import com.telelogic.rhapsody.core.IRPModelElement;
import com.telelogic.rhapsody.core.IRPSysMLPort;

import tools.strategies.FlowItemSelectionFlow;
import tools.strategies.FlowItemSelectionFlowPort;
import tools.strategies.FlowItemSelectionMessage;
import tools.strategies.FlowItemSelectionStrategy;

/**
 * Tool that lets the user associate a Flow Item definition to a selected
 * Rhapsody model element (Message, SysML Port or Flow).
 * <p>
 * Based on the type of the current selection, a corresponding strategy is chosen
 * that will open a selection dialog and apply the chosen Flow Item definition.
 * </p>
 * Supported targets:
 * <ul>
 *   <li>{@link IRPMessage}</li>
 *   <li>{@link IRPSysMLPort}</li>
 *   <li>{@link IRPFlow}</li>
 * </ul>
 */
public class SelectFlowItemDefinition extends RhapsodyTool {

    /** Command name displayed in Rhapsody menu */
    public static final String COMMAND = "Safran Toolkit...\\Select Flow Item definition";

    public SelectFlowItemDefinition(IRPApplication rpyApp) {
        super(rpyApp);
    }

    @Override
    public void execute() {
        rhpLog.debug("Executing command: " + COMMAND);

        IRPModelElement selected = rhApp.getSelectedElement();

        if (selected == null) {
            rhpLog.error("Execution aborted: No valid model element selected.");
            return;
        }

        FlowItemSelectionStrategy strategy = resolveStrategy(selected);

        if (strategy == null) {
            rhpLog.error("Execution aborted: Unsupported selection type: " + selected.getMetaClass());
            return;
        }

        strategy.apply(selected);
        rhpLog.debug("Command execution complete: " + COMMAND);
    }

    // Package-private for testability
    FlowItemSelectionStrategy resolveStrategy(IRPModelElement element) {

        if (element instanceof IRPMessage) {
            rhpLog.debug("Strategy = FlowItemSelectionMessage");
            return new FlowItemSelectionMessage(rhApp, rhpLog);

        } else if (element instanceof IRPSysMLPort) {
            rhpLog.debug("Strategy = FlowItemSelectionFlowPort");
            return new FlowItemSelectionFlowPort(rhApp, rhpLog);

        } else if (element instanceof IRPFlow) {
            rhpLog.debug("Strategy = FlowItemSelectionFlow");
            return new FlowItemSelectionFlow(rhApp, rhpLog);
        }

        return null;
    }

    @Override
    public String commandName() {
        return COMMAND;
    }

    /**
     * False: the command only opens the non-modal selector and returns. Each
     * OK / Apply and each in-dialog edit then runs on the selector worker in
     * its own undo transaction, so the change shows live and Ctrl+Z reverts
     * it one step at a time.
     */
    @Override
    public boolean isUndoable() {
        return false;
    }

    /** This tool opens a dialog (the selector). */
    @Override
    public boolean isInteractive() {
        return true;
    }
}
