package tools;

import java.util.ArrayList;
import java.util.List;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import com.telelogic.rhapsody.core.IRPActionBlock;
import com.telelogic.rhapsody.core.IRPApplication;
import com.telelogic.rhapsody.core.IRPClass;
import com.telelogic.rhapsody.core.IRPClassifierRole;
import com.telelogic.rhapsody.core.IRPGraphElement;
import com.telelogic.rhapsody.core.IRPModelElement;

import main.constants.ProfileMetaClassConstants;
import main.constants.RhpMetaClassConstants;
import main.gui.tools.FlowItemSelectorDialog;
import main.gui.tools.SelectorSpec;
import main.gui.tools.SelectorWorker;
import main.gui.tools.Toast;
import main.gui.tools.model.FlowItemScanResult;
import main.gui.tools.model.RhapsodyFlowItemScanner;

/**
 * Tool implementation that allows the user to associate a selected ActionBlock
 * (on a sequence diagram) with a Function contained inside the related system model.
 * When the user selects an ActionBlock and runs this tool, it scans all nested
 * elements under the lifeline's represented system and lists all items <<FUNCTION>>.
 * The user can then choose one, which will be set as the formal type of the ActionBlock.
 * The selector is non-modal and the command returns at once: OK and Apply set
 * the function on the selector worker thread, each in its own undo transaction,
 * so the diagram shows it live; Escape / close box just close the selector.
 */
public class SelectFunctionDefinition extends RhapsodyTool {

    public static final String COMMAND = "Safran Toolkit...\\Select Function definition";

    private static final Icon FUNCTION_ICON =
            new ImageIcon("../SafranArchitectureProfile/Icons/FunctionSingleIcon.png");

    public SelectFunctionDefinition(IRPApplication rpyApp) {
        super(rpyApp);
    }

    @Override
    public void execute() {
        rhpLog.debug("Executing command: " + COMMAND);

        IRPModelElement selectedModelElement = rhApp.getSelectedElement();
        if (!(selectedModelElement instanceof IRPActionBlock actionBlock)) {
            rhpLog.error("Execution aborted: An ActionBlock must be selected.");
            return;
        }

        List<?> graphElements = rhApp.getSelectedGraphElements().toList();
        if (graphElements.isEmpty() || !(graphElements.get(0) instanceof IRPGraphElement)) {
            rhpLog.error("Execution aborted: No valid ActionBlock element selected.");
            return;
        }

        IRPGraphElement selectedGraphElement = (IRPGraphElement) graphElements.get(0);
        IRPModelElement graphicalParent = selectedGraphElement.getGraphicalParent().getModelObject();

        if (!(graphicalParent instanceof IRPClassifierRole)) {
            rhpLog.error("Execution aborted: ActionBlock is not attached to a lifeline.");
            return;
        }

        IRPModelElement representedClass = ((IRPClassifierRole) graphicalParent).getFormalClassifier();
        if (representedClass == null) {
            rhpLog.error("Execution aborted: No System bound to lifeline.");
            return;
        }

        rhpLog.debug("Lifeline system: " + representedClass.getFullPathName());

        String actionBlockGuid = guidOf(actionBlock);
        if (actionBlockGuid == null) {
            rhpLog.error("Execution aborted: cannot read the GUID of the ActionBlock.");
            Toast.showToast("Cannot read the GUID of the action block.", 3500);
            return;
        }

        List<IRPModelElement> functionList = collectNestedFunctions(representedClass);

        FlowItemScanResult data =
                RhapsodyFlowItemScanner.fromRoot(functionList, representedClass, true);
        SelectorSpec spec = new SelectorSpec("Select Function definition", FUNCTION_ICON,
                ProfileMetaClassConstants.FUNCTION, "Function",
                (key, element, owner) -> applyFunction(actionBlockGuid, key, element),
                false);

        // Non-blocking: the command returns now; OK / Apply run on the selector worker.
        new FlowItemSelectorDialog(data, spec, SelectorWorker.shared(rhApp)).open();

        rhpLog.debug("Command execution complete: " + COMMAND + " (selector left open)");
    }

    /**
     * OK / Apply (selector worker thread): sets the chosen function as the formal
     * type of the action block. Both are re-fetched by GUID first (the model may
     * have changed while the selector was open); a deleted one is reported and
     * nothing changes. The function already set changes nothing (OK right after
     * Apply). Never throws.
     *
     * @return true when the model was changed
     */
    private boolean applyFunction(String actionBlockGuid, String functionKey, IRPModelElement function) {
        try {
            IRPModelElement target = findByGuid(actionBlockGuid);
            if (!(target instanceof IRPActionBlock actionBlock)) {
                rhpLog.error("Select Function definition: the action block no longer exists (" + actionBlockGuid + ").");
                Toast.showToast("The action block no longer exists.", 3500);
                return false;
            }
            if (isGuid(functionKey)) {
                function = findByGuid(functionKey);
                if (function == null) {
                    rhpLog.error("Select Function definition: the selected Function no longer exists (" + functionKey + ").");
                    Toast.showToast("The selected Function no longer exists.", 3500);
                    return false;
                }
            }
            if (function == null) return false;
            if (sameElement(actionBlock.getFormalType(), function)) {
                rhpLog.debug("Function already set: " + safeName(function));
                return false;
            }
            actionBlock.setFormalType(function);
            rhpLog.info("Function selected: " + safeName(function));
            Toast.showToast("Function definition set: " + safeName(function), 3500);
            return true;
        } catch (Throwable t) {
            rhpLog.error("Select Function definition failed: " + t);
            Toast.showToast("Select Function definition failed: " + t.getMessage(), 4500);
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

    private List<IRPModelElement> collectNestedFunctions(IRPModelElement parent) {
        rhpLog.debug("Scanning nested elements for Functions...");

        @SuppressWarnings("unchecked")
        List<IRPClass> allClasses =
                parent.getNestedElementsByMetaClass(RhpMetaClassConstants.CLASS, 1).toList();

        List<IRPModelElement> found = new ArrayList<>();
        for (IRPClass currentClass : allClasses) {
            try {
                String udmc = currentClass.getUserDefinedMetaClass();
                if (ProfileMetaClassConstants.FUNCTION.equals(udmc)
                        || ProfileMetaClassConstants.FUNCTION_WITH_REFERENCE.equals(udmc)) {
                    found.add(currentClass);
                }
            } catch (Exception ignore) {}
        }

        rhpLog.debug("Total functions discovered: " + found.size());
        return found;
    }

    @Override public String commandName()     { return COMMAND; }

    /**
     * False: the command only opens the non-modal selector and returns. Each
     * OK / Apply and each in-dialog edit then runs on the selector worker in
     * its own undo transaction (live change, one Ctrl+Z each).
     */
    @Override public boolean isUndoable()     { return false; }

    /** This tool opens a dialog (the selector). */
    @Override public boolean isInteractive()  { return true; }
}
