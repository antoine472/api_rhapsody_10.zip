package tools;

import java.util.ArrayList;
import java.util.List;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import com.telelogic.rhapsody.core.IRPApplication;
import com.telelogic.rhapsody.core.IRPClassifierRole;
import com.telelogic.rhapsody.core.IRPConditionMark;
import com.telelogic.rhapsody.core.IRPGraphElement;
import com.telelogic.rhapsody.core.IRPModelElement;
import com.telelogic.rhapsody.core.IRPState;

import main.constants.ProfileMetaClassConstants;
import main.constants.RhpMetaClassConstants;
import main.gui.tools.FlowItemSelectorDialog;
import main.gui.tools.SelectorSpec;
import main.gui.tools.SelectorWorker;
import main.gui.tools.Toast;
import main.gui.tools.model.FlowItemScanResult;
import main.gui.tools.model.RhapsodyFlowItemScanner;

/**
 * Tool used to assign a Mode (State with <<OPERATING_MODE>> meta-class)
 * to a ConditionMark placed on a Sequence Diagram.
 * When a ConditionMark is selected, this tool locates the lifeline's represented
 * classifier and retrieves all underlying States marked as Operating Modes.
 * The user selects one via a dialog and it becomes the formal type of the ConditionMark.
 * The selector is non-modal and the command returns at once: OK and Apply set
 * the mode on the selector worker thread, each in its own undo transaction, so
 * the diagram shows it live; Escape / close box just close the selector.
 */
public class SelectModeDefinition extends RhapsodyTool {

    public static final String COMMAND = "Safran Toolkit...\\Select Mode definition";

    private static final Icon MODE_ICON =
            new ImageIcon("../SafranArchitectureProfile/Icons/ModeSingleIcon.png");

    public SelectModeDefinition(IRPApplication rpyApp) {
        super(rpyApp);
    }

    @Override
    public void execute() {
        rhpLog.debug("Executing command: " + COMMAND);

        List<?> graphElements = rhApp.getSelectedGraphElements().toList();
        if (graphElements.isEmpty() || !(graphElements.get(0) instanceof IRPGraphElement)) {
            rhpLog.error("Execution aborted: No valid graphical element selected.");
            return;
        }

        IRPGraphElement selectedGraph = (IRPGraphElement) graphElements.get(0);

        if (!(selectedGraph.getModelObject() instanceof IRPConditionMark conditionMark)) {
            rhpLog.info("Execution aborted: Selected element is not a ConditionMark.");
            return;
        }

        IRPModelElement parent = selectedGraph.getGraphicalParent().getModelObject();
        if (!(parent instanceof IRPClassifierRole)) {
            rhpLog.error("Execution aborted: ConditionMark is not attached to a Lifeline.");
            return;
        }

        IRPModelElement classifier = ((IRPClassifierRole) parent).getFormalClassifier();
        if (classifier == null) {
            rhpLog.error("Execution aborted: No System bound to lifeline.");
            return;
        }

        rhpLog.debug("Collecting operating mode states under: " + classifier.getFullPathName());

        String conditionMarkGuid = guidOf(conditionMark);
        if (conditionMarkGuid == null) {
            rhpLog.error("Execution aborted: cannot read the GUID of the ConditionMark.");
            Toast.showToast("Cannot read the GUID of the condition mark.", 3500);
            return;
        }

        List<IRPModelElement> modes = collectOperatingModes(classifier);

        FlowItemScanResult data =
                RhapsodyFlowItemScanner.fromRoot(modes, classifier, true);
        SelectorSpec spec = new SelectorSpec("Select Mode definition", MODE_ICON,
                ProfileMetaClassConstants.OPERATING_MODE, "Mode",
                (key, element, owner) -> applyMode(conditionMarkGuid, key, element),
                false);

        // Non-blocking: the command returns now; OK / Apply run on the selector worker.
        new FlowItemSelectorDialog(data, spec, SelectorWorker.shared(rhApp)).open();

        rhpLog.debug("Command execution complete: " + COMMAND + " (selector left open)");
    }

    /**
     * OK / Apply (selector worker thread): sets the chosen mode as the formal
     * type of the condition mark. Both are re-fetched by GUID first (the model
     * may have changed while the selector was open); a deleted one is reported
     * and nothing changes. The mode already set changes nothing (OK right after
     * Apply). Never throws.
     *
     * @return true when the model was changed
     */
    private boolean applyMode(String conditionMarkGuid, String modeKey, IRPModelElement mode) {
        try {
            IRPModelElement target = findByGuid(conditionMarkGuid);
            if (!(target instanceof IRPConditionMark conditionMark)) {
                rhpLog.error("Select Mode definition: the condition mark no longer exists (" + conditionMarkGuid + ").");
                Toast.showToast("The condition mark no longer exists.", 3500);
                return false;
            }
            if (isGuid(modeKey)) {
                mode = findByGuid(modeKey);
                if (mode == null) {
                    rhpLog.error("Select Mode definition: the selected Mode no longer exists (" + modeKey + ").");
                    Toast.showToast("The selected Mode no longer exists.", 3500);
                    return false;
                }
            }
            if (mode == null) return false;
            if (sameElement(conditionMark.getFormalType(), mode)) {
                rhpLog.debug("Mode already set: " + safeName(mode));
                return false;
            }
            conditionMark.setFormalType(mode);
            rhpLog.info("Mode selected: " + safeName(mode));
            Toast.showToast("Mode definition set: " + safeName(mode), 3500);
            return true;
        } catch (Throwable t) {
            rhpLog.error("Select Mode definition failed: " + t);
            Toast.showToast("Select Mode definition failed: " + t.getMessage(), 4500);
            return false;
        }
    }

    private static boolean sameElement(IRPModelElement a, IRPModelElement b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        try {
            String ga = a.getGUID();
            return ga != null && !ga.isBlank() && ga.equals(b.getGUID());
        } catch (Exception e) {
            return false;
        }
    }

    private static String safeName(IRPModelElement el) {
        try { return el != null ? el.getName() : "null"; } catch (Exception e) { return "<?>"; }
    }

    private List<IRPModelElement> collectOperatingModes(IRPModelElement parent) {
        rhpLog.debug("Scanning for Operating Mode states...");

        List<?> allStates =
                parent.getNestedElementsByMetaClass(RhpMetaClassConstants.STATE, 1).toList();

        List<IRPModelElement> modes = new ArrayList<>();
        for (Object state : allStates) {
            if (!(state instanceof IRPState s)) continue;
            try {
                if (ProfileMetaClassConstants.OPERATING_MODE.equals(s.getUserDefinedMetaClass())) {
                    rhpLog.debug("Operating Mode found: " + s.getName());
                    modes.add(s);
                }
            } catch (Exception ignore) {}
        }

        rhpLog.debug("Operating Mode count: " + modes.size());
        return modes;
    }

    @Override public String commandName()    { return COMMAND; }

    /**
     * False: the command only opens the non-modal selector and returns. Each
     * OK / Apply and each in-dialog edit then runs on the selector worker in
     * its own undo transaction (live change, one Ctrl+Z each).
     */
    @Override public boolean isUndoable()    { return false; }

    /** This tool opens a dialog (the selector). */
    @Override public boolean isInteractive() { return true; }
}
