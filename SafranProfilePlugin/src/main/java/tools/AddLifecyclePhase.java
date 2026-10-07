package tools;

import java.util.List;

import com.telelogic.rhapsody.core.IRPApplication;
import com.telelogic.rhapsody.core.IRPGraphElement;
import com.telelogic.rhapsody.core.IRPModelElement;
import com.telelogic.rhapsody.core.IRPPackage;

/**
 * <b>Implementation of specific action</b>
 * <p>
 * This class with do something...
 * Description of the admin helper
 * </p>
 * @author s570589
 *
 */
public class AddLifecyclePhase extends RhapsodyTool {

	public static final String COMMAND = "Add New\\Add Lifecycle Phase";

	public AddLifecyclePhase(IRPApplication rpyApp) {
		super(rpyApp);
	}

	/*
	 * All logic is implemented here 
	 */
	@Override
	public void execute() {
		rhpLog.debug("Execution of " + COMMAND);

		@SuppressWarnings("unchecked")
		List<IRPGraphElement> selectedGraphElements = rhApp.getSelectedGraphElements().toList();
				
		if(selectedGraphElements.isEmpty() || selectedGraphElements.size() > 1) {
			rhpLog.error("Select only 1 element");
			return;
		}
		
		IRPGraphElement selectedGraphElement = selectedGraphElements.get(0);
		IRPModelElement selectedModelElement = selectedGraphElement.getModelObject();
		
		
		if (!(selectedModelElement instanceof IRPPackage)) {
			rhpLog.error("Selected element is not a Lifecycle Phase");
			return;
		}
		
		IRPModelElement createdNewLifecyclePhase = selectedModelElement.addNewAggr("Lifecycle Phase", "new_lifecycle");
		
		String[] position = selectedGraphElement.getGraphicalProperty("Position").getValue().split(",");
		selectedGraphElement.getDiagram().addNewNodeForElement(createdNewLifecyclePhase, Integer.valueOf(position[0])+ 10, Integer.valueOf(position[1])+50, 250, 100);
		
		rhpLog.debug("End of execution of " + COMMAND);

	}

	@Override
	public String commandName() {
		return COMMAND;
	}

	@Override
	public boolean isUndoable() {
		return true;
	}

}
