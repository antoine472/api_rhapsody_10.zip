package tools;

import java.util.List;

import com.telelogic.rhapsody.core.IRPApplication;
import com.telelogic.rhapsody.core.IRPCollection;
import com.telelogic.rhapsody.core.IRPFlow;
import com.telelogic.rhapsody.core.IRPGraphElement;
import com.telelogic.rhapsody.core.IRPModelElement;

/**
 * <b>Locate Flow Item Tool</b>
 * <p>
 * @author S655683 
 * Localise dans le browser Rhapsody le ou les Flow Item(s) convoyes
 * par le Flow actuellement selectionne.
 * </p>
 */
public class LocateFlowItemInBrowser extends RhapsodyTool {

    public static final String COMMAND = "Safran Toolkit...\\Locate Flow Item in Browser";

    public LocateFlowItemInBrowser(IRPApplication rhapsodyApp) {
        super(rhapsodyApp);
    }

    @Override
    public void execute() {
        rhpLog.debug("Starting locate flow item...");

        // Recupere l'element actuellement selectionne dans Rhapsody.
        IRPModelElement selectedElement = rhApp.getSelectedElement();

        // Verifie qu'un element est effectivement selectionne.
        if (selectedElement == null) {
            rhpLog.warn("LocateFlowItem : aucun element n'est selectionne.");
            return;
        }

        // Resout la selection vers un Flow, qu'il s'agisse du Flow
        // lui-meme ou d'un element graphique qui le represente.
        IRPFlow flow = resolveToFlow(selectedElement);
        if (flow == null) {
            rhpLog.warn(
                    "LocateFlowItem : \""
                    + selectedElement.getFullPathName()
                    + "\" n'est pas un Flow. Selectionnez un Generic, "
                    + "Operational, Functional ou Logical Flow.");
            return;
        }

        // Localise le ou les Flow Items associes au Flow.
        locateConveyedFlowItems(flow);
        
    	rhpLog.info("Element selectionne : " + selectedElement.getFullPathName());
    }

    /**
     * Gere les cas ou la selection est le Flow lui-meme ou un
     * element graphique qui le represente sur un diagramme.
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
     * Recupere les elements convoyes par le Flow et les localise
     * successivement dans le browser Rhapsody.
     *
     * @param flow Flow selectionne dans Rhapsody
     * @return nombre de Flow Items localises
     */
    private int locateConveyedFlowItems(IRPFlow flow) {
        // getConveyed retourne la collection des elements associes au Flow.
        IRPCollection conveyedElements = flow.getConveyed();

        // Verifie que la collection retournee est exploitable.
        if (conveyedElements == null || conveyedElements.getCount() == 0) {
            rhpLog.warn(
                    "Aucun Flow Item n'est associe au Flow \""
                    + flow.getName()
                    + "\".");
            return 0;
        }

        // Convertit la collection Rhapsody en liste Java.
        List<?> elements = conveyedElements.toList();

        // Compte uniquement les vrais objets IRPFlowItem.
        int flowItemCount = 0;

        for (Object element : elements) {
            // Ignore defensivement les objets d'un autre type.
            if (!(element instanceof IRPModelElement)) {
                continue;
            }

            // Convertit l'objet vers l'interface IRPModelElement.
            IRPModelElement flowItem = (IRPModelElement) element;
            flowItemCount++;

            // Localise et selectionne le Flow Item dans le browser.
            int result = flowItem.locateInBrowser();

            rhpLog.debug(
                    "Flow Item localise : "
                    + flowItem.getFullPathName()
                    + " (code retour locateInBrowser = " + result + ")");
        }

        // Cas defensif : la collection conveyed ne contenait
        // aucun objet implementant IRPFlowItem.
        if (flowItemCount == 0) {
            rhpLog.warn(
                    "Le Flow contient des elements convoyes, "
                    + "mais aucun n'implemente IRPFlowItem.");
            return 0;
        }

        // Informe l'utilisateur lorsque plusieurs Flow Items sont associes.
        if (flowItemCount > 1) {
            rhpLog.info(
                    flowItemCount
                    + " Flow Items sont associes au Flow \""
                    + flow.getName()
                    + "\". Ils ont ete localises successivement ; "
                    + "le dernier reste selectionne dans le browser.");
        }

        return flowItemCount;
    }

    @Override
    public boolean isUndoable() {
        return false;
    }

    @Override
    public String commandName() {
        return COMMAND;
    }
}