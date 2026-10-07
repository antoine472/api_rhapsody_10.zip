package tools.strategies;

import java.util.ArrayList;
import java.util.List;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import com.telelogic.rhapsody.core.IRPApplication;
import com.telelogic.rhapsody.core.IRPCollection;
import com.telelogic.rhapsody.core.IRPFlow;
import com.telelogic.rhapsody.core.IRPModelElement;
import com.telelogic.rhapsody.core.IRPProject;

import logging.RhapsodyLogger;

/**
 * Strategy used when the user selects a SysML Flow element.
 * Flow Item is a NEW TERM (UDMC) based on Class.
 * Allows Select / Create via FlowItemSelectorDialog; OK and Apply make the
 * flow convey the selected Flow Item (see {@link #assignTo}), live, each in
 * its own undo transaction (see FlowItemSelectionSupport).
 */
public class FlowItemSelectionFlow implements FlowItemSelectionStrategy {

    private static final Icon FLOW_ITEM_ICON =
            new ImageIcon("../SafranArchitectureProfile/Icons/FlowItemSingleIcon.png");

    private static final boolean ENABLE_CREATE = true;

    private final IRPApplication rhApp;
    private final RhapsodyLogger log;

    public FlowItemSelectionFlow(IRPApplication rpyApp, RhapsodyLogger rpyLog) {
        this.rhApp = rpyApp;
        this.log = rpyLog;
    }

    @Override
    public void apply(IRPModelElement selectedModelElement) {

        if (!(selectedModelElement instanceof IRPFlow flow)) {
            log.error("FlowItemSelectionFlow: selected element is not an IRPFlow.");
            return;
        }

        IRPProject project = flow.getProject();

        List<IRPModelElement> availableItems = FlowItemSelectionSupport.findFlowItems(project, log);

        FlowItemSelectionSupport.selectAndAssignFlowItem(
                rhApp,
                availableItems,
                FLOW_ITEM_ICON,
                "Select Flow Item definition",
                ENABLE_CREATE,
                flow,
                true,   // copyStereotypesFromSource
                false,  // copyNewTerms = false
                (target, flowItem) -> target instanceof IRPFlow live && assignTo(live, flowItem, log),
                log
        );
    }

    /**
     * OK / Apply: makes {@code flow} convey {@code flowItem} only. C-1 FIX: the
     * other conveyed items are removed, so the tool never accumulates several
     * conveyed Flow Items; the chosen one is kept when already conveyed (no
     * change then). Never throws.
     *
     * @return true when the model was changed
     */
    public static boolean assignTo(IRPFlow flow, IRPModelElement flowItem, RhapsodyLogger log) {
        boolean changed = false;
        try {
            boolean conveyed = false;
            List<IRPModelElement> toRemove = new ArrayList<>();
            IRPCollection current = flow.getConveyed();
            if (current != null) {
                for (Object o : current.toList()) {
                    if (!(o instanceof IRPModelElement existing)) continue;
                    if (!conveyed && FlowItemSelectionSupport.sameElement(existing, flowItem)) {
                        conveyed = true;
                    } else {
                        toRemove.add(existing);
                    }
                }
            }
            for (IRPModelElement existing : toRemove) {
                flow.removeConveyed(existing);
                changed = true;
            }
            if (!conveyed) {
                flow.addConveyed(flowItem);
                changed = true;
            }
        } catch (Exception ex) {
            FlowItemSelectionSupport.reportFailure(log,
                    "Cannot set the Flow Item of " + FlowItemSelectionSupport.safeName(flow), ex);
            return changed;
        }
        if (changed) {
            log.info("Flow Item '" + FlowItemSelectionSupport.safeName(flowItem)
                    + "' assigned to Flow '" + FlowItemSelectionSupport.safeName(flow) + "'");
        }
        return changed;
    }
}
