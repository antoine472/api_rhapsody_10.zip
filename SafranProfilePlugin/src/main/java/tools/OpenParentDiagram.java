package tools;

import java.util.List;

import com.telelogic.rhapsody.core.IRPApplication;
import com.telelogic.rhapsody.core.IRPClass;
import com.telelogic.rhapsody.core.IRPDiagram;
import com.telelogic.rhapsody.core.IRPModelElement;

import tools.RhapsodyTool;

public class OpenParentDiagram extends RhapsodyTool {

    public static final String COMMAND = "Open parent diagram";

    public OpenParentDiagram(IRPApplication rpyApp) {
        super(rpyApp);
    }

    @Override
    public String commandName() {
        return COMMAND;
    }

    @Override
    public void execute() {
    	try {
    		rhpLog.debug("Start execution of: "+COMMAND);
    		
    		IRPDiagram selectedDiagram = (IRPDiagram) rhApp.getSelectedElement();
    		
    		IRPModelElement parentOfDiagram =  selectedDiagram.getOwner().getOwner();
    		
    		if (parentOfDiagram instanceof IRPClass) {
    			IRPClass function = (IRPClass) parentOfDiagram;
    			@SuppressWarnings("unchecked")
				List<IRPDiagram> listFunctionalBehaviorDiagrams = function.getStructureDiagrams().toList();
    			if(listFunctionalBehaviorDiagrams.isEmpty()) {
    				// TODO: toast notification ?
    				rhpLog.info("Parent function "+function.getDisplayName() + " has no diagram.");
    			}
    			else {
    				// TODO: opens the first ?
    				listFunctionalBehaviorDiagrams.get(0).openDiagram();
    				selectedDiagram.getOwner().highLightElement(); //FIXME: the highlight does not work
    			}
			}
    	}
    	catch (Exception e) {
    		rhpLog.error("Exception: "+e.getMessage());
		} finally {
			rhpLog.debug("End execution of: "+COMMAND);
		}
    }

	@Override
	public boolean isUndoable() {
		return false;
	}
}
