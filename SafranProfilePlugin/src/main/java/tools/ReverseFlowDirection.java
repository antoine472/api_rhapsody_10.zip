package tools;

import java.util.List;

import com.telelogic.rhapsody.core.IRPApplication;
import com.telelogic.rhapsody.core.IRPFlow;
import com.telelogic.rhapsody.core.IRPGraphElement;
import com.telelogic.rhapsody.core.IRPModelElement;

/**
 * <b>Reverse Flow Direction Tool</b>
 * <p>
 * Reverses the direction of a selected IRPFlow (toEnd1 <-> toEnd2).
 * Does nothing for bidirectional flows.
 * @author MY0598639
 * </p>
 */
public class ReverseFlowDirection extends RhapsodyTool {

    public static final String COMMAND = "Safran Toolkit...\\Reverse flow direction";

    private static final String DIR_TO_END1 = "toEnd1";
    private static final String DIR_TO_END2 = "toEnd2";
    private static final String DIR_BIDIR  = "bidirectional";

    public ReverseFlowDirection(IRPApplication rhapsodyApp) {
        super(rhapsodyApp);
    }

    @Override
    public void execute() {
        rhpLog.debug("Starting flow reverse direction...");

        @SuppressWarnings("unchecked")
        List<IRPModelElement> selected = rhApp.getListOfSelectedElements().toList();

        int reversed = 0;
        for (IRPModelElement sel : selected) {
            IRPFlow flow = resolveToFlow(sel);
            if (flow == null) continue;

            if (reverse(flow)) {
                reversed++;
            }
        }

        rhpLog.debug("Flow reverse direction completed. Reversed=" + reversed);
    }

    /**
     * Handles cases where selection may be the flow itself or a graphical element.
     */
    private IRPFlow resolveToFlow(IRPModelElement sel) {
        if (sel instanceof IRPFlow) {
            return (IRPFlow) sel;
        }
        if (sel instanceof IRPGraphElement) {
            IRPModelElement mo = ((IRPGraphElement) sel).getModelObject();
            if (mo instanceof IRPFlow) {
                return (IRPFlow) mo;
            }
        }
        return null;
    }

    /**
     * Reverse direction: toEnd1 <-> toEnd2. Ignore bidirectional.
     */
    private boolean reverse(IRPFlow flow) {
        String dir = flow.getDirection();
        if (dir == null || dir.isBlank()) {
            rhpLog.warn("Cannot reverse flow with empty direction: " + flow.getFullPathName());
            return false;
        }

        String newDir;
        if (DIR_TO_END1.equalsIgnoreCase(dir)) {
            newDir = DIR_TO_END2;
        } else if (DIR_TO_END2.equalsIgnoreCase(dir)) {
            newDir = DIR_TO_END1;
        } else if (DIR_BIDIR.equalsIgnoreCase(dir)) {
            rhpLog.debug("Skip reverse: flow is bidirectional: " + flow.getFullPathName());
            return false;
        } else {
            rhpLog.warn("Unknown flow direction '" + dir + "' for flow: " + flow.getFullPathName());
            return false;
        }

        rhpLog.debug("Reverse flow: " + flow.getFullPathName() + " dir=" + dir + " -> " + newDir);
        flow.setDirection(newDir); // valid values include toEnd1/toEnd2/bidirectional :contentReference[oaicite:1]{index=1}
        return true;
    }

    @Override
    public boolean isUndoable() {
        return true;
    }

    @Override
    public String commandName() {
        return COMMAND;
    }
}