package tools;

import com.telelogic.rhapsody.core.IRPApplication;
import com.telelogic.rhapsody.core.IRPClass;
import com.telelogic.rhapsody.core.IRPClassifier;
import com.telelogic.rhapsody.core.IRPCollection;
import com.telelogic.rhapsody.core.IRPGeneralization;
import com.telelogic.rhapsody.core.IRPPort;

/**
 * <b>Implementation of specific action</b>
 * <p>
 * This class with do something...
 * Description of the admin helper
 * </p>
 * @author s570589
 *
 */
public class SendToDefinition extends RhapsodyTool {

	public static final String COMMAND = "Safran Toolkit...\\Send to definition";

	public SendToDefinition(IRPApplication rpyApp) {
		super(rpyApp);
	}

	/*
	 * All logic is implemented here 
	 */
	@Override
	public void execute() {
		IRPCollection selectedElements = rhApp.getListOfSelectedElements();
		
		if(selectedElements.getCount() == 0) {
			rhpLog.info("Nothing has been selected.");
			return;
		}

		for (Object obj : selectedElements.toList()) {
			if (obj instanceof IRPPort) {
				IRPPort port = (IRPPort) obj;
				rhpLog.debug("Selected element: "+ port.getFullPathName());
				IRPGeneralization generalization= (IRPGeneralization) ((IRPClass) port.getOwner()).getGeneralizations().getItem(1);

				IRPClassifier baseClass = generalization.getBaseClass();
				rhpLog.debug("Selected element: "+ port.getFullPathName() +" moved to: "+ baseClass.getFullPathName());
				try {
					port.setOwner(baseClass);
				}catch (Exception e) {
					rhpLog.error("Selected element: "+ port.getFullPathName()+" cannot be moved to "+baseClass.getFullPathName());
				}finally {
					rhApp.activeProject().save();
				}
				
			}

			if(obj instanceof IRPClass) {
				IRPClass function = (IRPClass) obj;
				rhpLog.debug("Selected element: "+ function.getFullPathName());
				IRPGeneralization generalization= (IRPGeneralization) ((IRPClass) function.getOwner()).getGeneralizations().getItem(1);

				IRPClassifier baseClass = generalization.getBaseClass();
				function.setOwner(baseClass);
				rhpLog.debug("Selected element: "+ function.getFullPathName() +" moved to: "+ baseClass.getFullPathName());
			}
		}
		rhpLog.info("End execution of "+COMMAND);
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
