package tools;

import java.util.ArrayList;
import java.util.List;

import com.telelogic.rhapsody.core.IRPApplication;
import com.telelogic.rhapsody.core.IRPClass;
import com.telelogic.rhapsody.core.IRPGeneralization;
import com.telelogic.rhapsody.core.IRPModelElement;


/**
 * <b>Implementation of specific action</b>
 * <p>
 * This class with do something...
 * Description of the admin helper
 * </p>
 * @author s570589
 *
 */
public class DuplicateServiceFunction extends RhapsodyTool {

	public static final String COMMAND = "Safran Toolkit...\\Initialize service functions";
	
	private List<IRPClass> operationalSystemFunctions = new ArrayList<>();
	
	private List<IRPModelElement> functionalSystemFunctions = new ArrayList<>();

	public DuplicateServiceFunction(IRPApplication rpyApp) {
		super(rpyApp);
	}

	/*
	 * All logic is implemented here 
	 */
	@Override
	public void execute() {
		rhpLog.debug("Execution of " + COMMAND);

		IRPModelElement selectedElement = rhApp.getSelectedElement();

		if(!"Functional System".equals(selectedElement.getUserDefinedMetaClass())) {
			rhpLog.info("Selected element is not a \"Functional System\".");
			return;
		}

		IRPClass selectedFunctionalSystem = (IRPClass) selectedElement;
		
		@SuppressWarnings("unchecked")
		List<IRPClass> nestedFunctionalClasses = selectedFunctionalSystem.getNestedClassifiers().toList();

		for (IRPClass rhpClass : nestedFunctionalClasses) {
			if("Function".equals(rhpClass.getUserDefinedMetaClass())) {
				IRPGeneralization generation = (IRPGeneralization) rhpClass.getGeneralizations().getItem(1);
				IRPClass redefinedFunction = (IRPClass) generation.getBaseClass();
				functionalSystemFunctions.add(redefinedFunction);
			}
		}

		IRPGeneralization generation = (IRPGeneralization) selectedFunctionalSystem.getGeneralizations().getItem(1);
		IRPClass operationalSystem = (IRPClass) generation.getBaseClass();


		@SuppressWarnings("unchecked")
		List<IRPClass> nestedOperationalClasses = operationalSystem.getNestedClassifiers().toList();

		for (IRPClass rhpClass : nestedOperationalClasses) {
			if("Function".equals(rhpClass.getUserDefinedMetaClass())) {
				operationalSystemFunctions.add(rhpClass);
			}
		}
		
		// diff between 2 lists - Output unique elements in nestedOperationalClasses
		for (IRPClass firstLevelFunction : operationalSystemFunctions) {
			boolean isFunctionInFunctionalSystem = false;
			for (IRPModelElement function : functionalSystemFunctions) {
				if (function.equals(firstLevelFunction)) {
					isFunctionInFunctionalSystem = true;
					break;
				}
			}
			if (!isFunctionInFunctionalSystem) {
				createFunctionInFunctionalSystem(selectedFunctionalSystem, firstLevelFunction);
			}
		}

		rhpLog.debug("End of execution of " + COMMAND);

	}

	private void createFunctionInFunctionalSystem(IRPClass selectedFunctionalSystem, IRPClass firstLevelFunction) {
		
		IRPClass createdFunctionInFunctionalSystem = (IRPClass) selectedFunctionalSystem.addNewAggr("Function", firstLevelFunction.getName());
		createdFunctionInFunctionalSystem.setDisplayName(firstLevelFunction.getDisplayName());
				
		IRPGeneralization createdGeneralization = (IRPGeneralization) createdFunctionInFunctionalSystem.addNewAggr("Redefines Function", firstLevelFunction.getName());
		createdGeneralization.setBaseClass(firstLevelFunction);
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
