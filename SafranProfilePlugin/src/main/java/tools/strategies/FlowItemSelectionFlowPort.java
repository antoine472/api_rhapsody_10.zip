package tools.strategies;

import java.util.List;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import com.telelogic.rhapsody.core.IRPApplication;
import com.telelogic.rhapsody.core.IRPClassifier;
import com.telelogic.rhapsody.core.IRPModelElement;
import com.telelogic.rhapsody.core.IRPProject;
import com.telelogic.rhapsody.core.IRPSysMLPort;

import logging.RhapsodyLogger;

/**
 * Strategy for assigning a Flow Item to a SysML Port.
 * Flow Item is a NEW TERM (UDMC) based on Class.
 * Allows Select / Create via FlowItemSelectorDialog; OK and Apply type the port
 * with the selected Flow Item (see {@link #assignTo}), live, each in its own
 * undo transaction (see FlowItemSelectionSupport).
 */
public class FlowItemSelectionFlowPort implements FlowItemSelectionStrategy {

    private static final Icon FLOW_ITEM_ICON =
            new ImageIcon("../SafranArchitectureProfile/Icons/FlowItemSingleIcon.png");

    /** Q-2: promote instance boolean fields to static final constants. */
    private static final boolean SYNC_FLOW_ITEM = true;
    private static final boolean ENABLE_CREATE   = true;

    private final IRPApplication rhApp;
    private final RhapsodyLogger rhpLog;

    public FlowItemSelectionFlowPort(IRPApplication rpyApp, RhapsodyLogger rpyLog) {
        this.rhApp = rpyApp;
        this.rhpLog = rpyLog;
    }

    @Override
    public void apply(IRPModelElement selectedModelElement) {

        if (!(selectedModelElement instanceof IRPSysMLPort selectedPort)) {
            rhpLog.error("FlowItemSelectionFlowPort: selected element is not an IRPSysMLPort.");
            return;
        }

        IRPProject project = selectedPort.getProject();
        List<IRPModelElement> availableItems = FlowItemSelectionSupport.findFlowItems(project, rhpLog);

        FlowItemSelectionSupport.selectAndAssignFlowItem(
                rhApp,
                availableItems,
                FLOW_ITEM_ICON,
                "Select Flow Item definition",
                ENABLE_CREATE,
                selectedPort,
                true,   // copyStereotypesFromSource
                false,  // copyNewTerms = false
                (target, flowItem) -> target instanceof IRPSysMLPort live && assignTo(live, flowItem, rhpLog),
                rhpLog
        );
    }

    /**
     * OK / Apply: types {@code port} with {@code flowItem} and gives it the Flow
     * Item's stereotypes (new terms excepted). A port already typed and in sync
     * is not touched. Never throws.
     *
     * @return true when the model was changed
     */
    public static boolean assignTo(IRPSysMLPort port, IRPModelElement flowItem, RhapsodyLogger log) {
        if (!(flowItem instanceof IRPClassifier classifier)) {
            FlowItemSelectionSupport.reportFailure(log, "Cannot type the port: "
                    + FlowItemSelectionSupport.safeName(flowItem) + " is not a classifier.");
            return false;
        }

        boolean changed = false;
        try {
            if (!FlowItemSelectionSupport.sameElement(port.getType(), classifier)) {
                port.setType(classifier);
                changed = true;
            }
        } catch (Exception ex) {
            FlowItemSelectionSupport.reportFailure(log,
                    "Cannot type the port " + FlowItemSelectionSupport.safeName(port), ex);
            return changed;
        }
        if (changed) {
            log.info("Selected Flow Item: " + FlowItemSelectionSupport.safeName(flowItem));
        }

        if (SYNC_FLOW_ITEM && FlowItemSelectionSupport.syncStereotypes(port, flowItem, log)) {
            changed = true;
        }
        return changed;
    }
}
