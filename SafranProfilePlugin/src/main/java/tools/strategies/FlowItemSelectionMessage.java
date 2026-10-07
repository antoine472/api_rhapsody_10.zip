package tools.strategies;

import java.util.ArrayList;
import java.util.List;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import com.telelogic.rhapsody.core.IRPApplication;
import com.telelogic.rhapsody.core.IRPCollection;
import com.telelogic.rhapsody.core.IRPDependency;
import com.telelogic.rhapsody.core.IRPMessage;
import com.telelogic.rhapsody.core.IRPModelElement;
import com.telelogic.rhapsody.core.IRPProject;

import logging.RhapsodyLogger;
import main.constants.ProfileMetaClassConstants;

/**
 * Strategy for assigning a Flow Item to a sequence-diagram Message.
 * Allows Select / Create via FlowItemSelectorDialog; OK and Apply link the
 * message to the selected Flow Item (see {@link #assignTo}), live, each in
 * its own undo transaction (see FlowItemSelectionSupport).
 */
public class FlowItemSelectionMessage implements FlowItemSelectionStrategy {

    private static final Icon FLOW_ITEM_ICON =
            new ImageIcon("../SafranArchitectureProfile/Icons/FlowItemSingleIcon.png");

    /** Q-2: was a non-final instance field; promoted to constant. */
    private static final boolean SYNC_DATA_FLOW = true;

    private final IRPApplication rhApp;
    private final RhapsodyLogger rhpLog;

    public FlowItemSelectionMessage(IRPApplication rpyApp, RhapsodyLogger rpyLog) {
        this.rhApp = rpyApp;
        this.rhpLog = rpyLog;
    }

    @Override
    public void apply(IRPModelElement selectedModelElement) {
        IRPMessage selectedMessage = (IRPMessage) selectedModelElement;
        IRPProject project = selectedMessage.getProject();

        List<IRPModelElement> availableItems =
                FlowItemSelectionSupport.findFlowItems(project, rhpLog);

        FlowItemSelectionSupport.selectAndAssignFlowItem(
                rhApp,
                availableItems,
                FLOW_ITEM_ICON,
                "Select Flow Item definition",
                true,              // enableCreate
                selectedMessage,   // target (and source of the stereotypes of a created item)
                true,              // copyStereotypesFromSource
                false,             // copyNewTerms = false
                (target, flowItem) -> target instanceof IRPMessage live && assignTo(live, flowItem, rhpLog),
                rhpLog
        );
    }

    /**
     * OK / Apply: links {@code message} to {@code flowItem} (dependency) and,
     * with SYNC_DATA_FLOW, gives it the Flow Item's name, label and stereotypes
     * (new terms excepted). A message already linked and in sync is not
     * touched. Never throws.
     *
     * @return true when the model was changed
     */
    public static boolean assignTo(IRPMessage message, IRPModelElement flowItem, RhapsodyLogger log) {
        boolean changed = false;
        try {
            // C-2 FIX: only delete dependencies that point to a Flow Item.
            // Previously ALL dependencies were deleted, destroying unrelated traceability links.
            // The dependency already on the chosen Flow Item is kept (OK after Apply: no change).
            // R-1 FIX: null-guard getDependencies() before calling toList().
            boolean linked = false;
            List<IRPDependency> toDelete = new ArrayList<>();
            IRPCollection depsCollection = message.getDependencies();
            if (depsCollection != null) {
                for (Object o : depsCollection.toList()) {
                    if (!(o instanceof IRPDependency dep)) continue;
                    if (!isFlowItemDependency(dep)) continue;
                    if (!linked && FlowItemSelectionSupport.sameElement(dep.getDependsOn(), flowItem)) {
                        linked = true;
                    } else {
                        toDelete.add(dep);
                    }
                }
            }
            for (IRPDependency dep : toDelete) {
                dep.deleteFromProject();
                changed = true;
            }
            if (!linked) {
                message.addDependencyTo(flowItem);
                changed = true;
                log.info("Selected Flow Item: " + FlowItemSelectionSupport.safeName(flowItem));
            }
        } catch (Exception ex) {
            FlowItemSelectionSupport.reportFailure(log,
                    "Cannot link the message to " + FlowItemSelectionSupport.safeName(flowItem), ex);
            return changed;
        }

        if (SYNC_DATA_FLOW) {
            if (FlowItemSelectionSupport.syncNames(message, flowItem, log)) changed = true;
            if (FlowItemSelectionSupport.syncStereotypes(message, flowItem, log)) changed = true;
        }
        return changed;
    }

    /**
     * Returns true if {@code dep} is a Dependency whose target has UDMC == "Flow Item".
     * Used to avoid deleting unrelated dependency relations (e.g., requirement traceability).
     */
    private static boolean isFlowItemDependency(IRPModelElement dep) {
        if (!(dep instanceof IRPDependency irpDep)) return false;
        try {
            IRPModelElement target = irpDep.getDependsOn();
            if (target == null) return false;
            return ProfileMetaClassConstants.FLOW_ITEM.equals(target.getUserDefinedMetaClass());
        } catch (Exception ignore) {
            return false;
        }
    }
}
