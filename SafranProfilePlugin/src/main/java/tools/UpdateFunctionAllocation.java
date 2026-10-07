package tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.telelogic.rhapsody.core.IRPApplication;
import com.telelogic.rhapsody.core.IRPClass;
import com.telelogic.rhapsody.core.IRPClassifier;
import com.telelogic.rhapsody.core.IRPDependency;
import com.telelogic.rhapsody.core.IRPGeneralization;
import com.telelogic.rhapsody.core.IRPModelElement;

/**
 * <b>Updates the functions allocated on a Logical System</b>
 * <p>
 * This helper manage functions that need to be created, deleted or updated of a Logical System.
 * </p>
 * @author s570589
 *
 */
public class UpdateFunctionAllocation extends RhapsodyTool {

	/**
	 * Corresponds to the name given in the HEP file
	 */
	public static final String COMMAND ="Safran Toolkit...\\Update function allocation";


	public UpdateFunctionAllocation(IRPApplication rpyApp) {
		super(rpyApp);
	}

	// TODO: change to implementation for Logical SOI functions are inhereted
	// and allocated functions cannot be created
	/*
	 * All logic is implemented here 
	 */
	@SuppressWarnings("unchecked")
	@Override
	public void execute() {
		IRPModelElement selectedElement = rhApp.getSelectedElement();

		// Check if the selected element is a Logical System
		if(! "Logical System".equals(selectedElement.getUserDefinedMetaClass())){
			rhpLog.info("Execution not performed, selected element is not a Logical System.");
			return;
		}

		IRPClass selectedLogicalSystem = (IRPClass) selectedElement;
		rhpLog.debug("Execution started on a Logical System: "+selectedElement.getFullPathName());

		// Create two lists to store the targets from the two for-loops
		List<IRPModelElement> list1 = new ArrayList<>();
		List<LogicalFunctionalCouple> list2 = new ArrayList<>();

		// First loop: Add dependencies targets to list1
		List<IRPDependency> listDependencies= selectedLogicalSystem.getDependencies().toList();
		for (IRPDependency dependency : listDependencies) {

			if(! "Function Allocation".equals(dependency.getUserDefinedMetaClass())){
				rhpLog.debug("Logical System has a dependency: that is not a Function Allocation"+dependency.getFullPathName());
				continue;
			}
			IRPModelElement target = dependency.getDependsOn();

			rhpLog.debug("Target of the dependency: "+target.getFullPathName());
			// Add target to list1
			list1.add(target);
		}

		// Second loop: Add nested class targets to list2
		List<IRPClass> listFunctions = selectedLogicalSystem.getNestedElementsByMetaClass("Class", 0).toList();
		for (IRPClass currentClass : listFunctions) {

			if(! "Function".equals(currentClass.getUserDefinedMetaClass())){
				rhpLog.debug("Continue execution, current class that is not a Function: "+currentClass.getFullPathName());
				continue;
			}

			IRPClass functionalClass = (IRPClass) currentClass;
			IRPGeneralization generation = (IRPGeneralization) functionalClass.getGeneralizations().getItem(1);
			if(generation == null){
				rhpLog.error("Function has not generalization relation: "+currentClass.getFullPathName());
				continue;
			}
			IRPClass targetedFunction = (IRPClass) generation.getBaseClass();
			
			rhpLog.debug("Target of the Function: "+targetedFunction.getFullPathName());

			LogicalFunctionalCouple couple = new LogicalFunctionalCouple(targetedFunction, functionalClass);
			// Add Couple to list2
			list2.add(couple);
		}

		// Now compare both lists to find unique elements in each list

		// Output unique elements in list1
		for (IRPModelElement element : list1) {
			boolean isInList2 = false;
			for (LogicalFunctionalCouple couple : list2) {
				if (couple.getTargetedFunction().equals(element)) {
					isInList2 = true;
					break;
				}
			}
			if (!isInList2) {
				createFunctionBasedOnRealization(selectedElement, element);
			}
		}

		// Output unique elements in list2
		for (LogicalFunctionalCouple couple : list2) {
			if (!list1.contains(couple.getTargetedFunction())) {
				// element to be deleted
				rhpLog.debug("Deleted an existing Function without Allocation: " + couple.getFunctionalClass().getFullPathName());
				couple.getFunctionalClass().deleteFromProject();
			}
		}

		rhpLog.debug("Execution done.");

	}

	private void createFunctionBasedOnRealization(IRPModelElement logicalSystem, IRPModelElement function) {
		IRPClass createdFunction = (IRPClass) logicalSystem.addNewAggr("Function", function.getName());
		createdFunction.setDisplayName(function.getDisplayName());
		createdFunction.addGeneralization((IRPClassifier) function);
		rhpLog.debug("Create a new Function: " + createdFunction.getFullPathName());
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

class LogicalFunctionalCouple {

	private IRPClass targetedFunction, functionalClass;

	public LogicalFunctionalCouple(IRPClass targetedFunction, IRPClass functionalClass) {
		this.targetedFunction = targetedFunction;
		this.functionalClass = functionalClass;
	}

	public IRPClass getTargetedFunction() {
		return targetedFunction;
	}

	public IRPClass getFunctionalClass() {
		return functionalClass;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		LogicalFunctionalCouple couple = (LogicalFunctionalCouple) o;
		return Objects.equals(targetedFunction, couple.targetedFunction);
	}

	@Override
	public int hashCode() {
		return Objects.hash(targetedFunction);
	}
}
