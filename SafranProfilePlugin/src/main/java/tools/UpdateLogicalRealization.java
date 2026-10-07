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
 * <b>Implementation of specific action</b>
 * <p>
 * This class will do something...
 * Description of the admin helper
 * </p>
 * @author s570589
 *
 */
public class UpdateLogicalRealization extends RhapsodyTool {

	public static final String COMMAND ="Safran Toolkit...\\Update logical realization";
	
	public UpdateLogicalRealization(IRPApplication rpyApp) {
		super(rpyApp);
	}

	/*
	 * All logic is implemented here 
	 */
	@Override
	public void execute() {
		IRPModelElement selectedElement = rhApp.getSelectedElement();

		// Check if the selected element is a Logical System
		if (selectedElement.getUserDefinedMetaClass().equals("Technical Component")) {
		    
		    // Create two lists to store the targets from the two for-loops
		    List<IRPModelElement> list1 = new ArrayList<>();
		    List<TechnicalLogicalCouple> list2 = new ArrayList<>();
		    
		    // First loop: Add dependencies targets to list1
		    for (Object obj : selectedElement.getDependencies().toList()) {
		        if (obj instanceof IRPDependency) {
		            IRPDependency dependency = (IRPDependency) obj;
		            IRPModelElement target = dependency.getDependsOn();
		            // add logical System
		            rhpLog.debug(target.getName());
		            // Add target to list1
		            list1.add(target);
		        }
		    }
		    
		    // Second loop: Add nested class targets to list2
		    for (Object obj : selectedElement.getNestedElementsByMetaClass("Class", 0).toList()) {
		    	rhpLog.debug(obj.toString());
		        if (obj instanceof IRPClass) {
		            IRPClass logicalSystem = (IRPClass) obj;
		            IRPGeneralization generation = (IRPGeneralization) logicalSystem.getGeneralizations().getItem(1);
		            IRPClass targetedLogicalSystem = (IRPClass) generation.getBaseClass();
		            
		            TechnicalLogicalCouple couple = new TechnicalLogicalCouple(targetedLogicalSystem, logicalSystem);
		            // Add Couple to list2
		            list2.add(couple);
		        }
		    }

		    // Now compare both lists to find unique elements in each list

		    // Output unique elements in list1
		    rhpLog.debug("Elements in list1 but not in list2:");
		    for (IRPModelElement element : list1) {
		        boolean isInList2 = false;
		        for (TechnicalLogicalCouple couple : list2) {
		            if (couple.getTargetedFunction().equals(element)) {
		                isInList2 = true;
		                break;
		            }
		        }
		        if (!isInList2) {
		        	rhpLog.debug("Unique in list1: " + element);
		            createFunctionBasedOnRealization(selectedElement, element);
		        }
		    }

		    // Output unique elements in list2
		    rhpLog.debug("Elements in list2 but not in list1:");
		    for (TechnicalLogicalCouple couple : list2) {
		        if (!list1.contains(couple.getTargetedFunction())) {
		        	rhpLog.debug("Unique in list2: " + couple.getFunctionalClass());
		            // element to be deleted 
		            couple.getFunctionalClass().deleteFromProject();
		        }
		    }

		} else {
			rhpLog.debug("Not a Technical Component system");
		}
	}

	private void createFunctionBasedOnRealization(IRPModelElement technicalComponent, IRPModelElement logicalSystem) {
		IRPClass createdLogicalSystem = (IRPClass) technicalComponent.addNewAggr("Logical System", logicalSystem.getName());
		createdLogicalSystem.addGeneralization((IRPClassifier) logicalSystem);
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


class TechnicalLogicalCouple {
	
	private IRPClass targetedFunction, functionalClass;

	public TechnicalLogicalCouple(IRPClass targetedFunction, IRPClass functionalClass) {
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
		TechnicalLogicalCouple couple = (TechnicalLogicalCouple) o;
		return Objects.equals(targetedFunction, couple.targetedFunction);
	}

	@Override
	public int hashCode() {
		return Objects.hash(targetedFunction);
	}
}


