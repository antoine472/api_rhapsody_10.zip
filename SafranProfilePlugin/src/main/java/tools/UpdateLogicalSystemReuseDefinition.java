package tools;

import java.util.ArrayList;
import java.util.List;

import com.telelogic.rhapsody.core.IRPApplication;
import com.telelogic.rhapsody.core.IRPClass;
import com.telelogic.rhapsody.core.IRPClassifier;
import com.telelogic.rhapsody.core.IRPGeneralization;
import com.telelogic.rhapsody.core.IRPModelElement;
import com.telelogic.rhapsody.core.IRPStereotype;

/**
 * <b>Updates the functions of a Logical System Reuse</b>
 * <p>
 * This helper create Functions on Logical System Reuse based a Relation named "Logical Reuse"
 * </p>
 * @author s570589
 *
 */
public class UpdateLogicalSystemReuseDefinition extends RhapsodyTool {

	/**
	 * Corresponds to the name given in the HEP file
	 */
	public static final String COMMAND ="Safran Toolkit...\\Get Logical Reuse functions";
	
	private static final String GUID_FUNCTION_REDEFINITION_STEREOTYPE = "GUID ad8472d8-ee69-47c8-83bf-0f18eb410239";
	private IRPStereotype stereotype = null;


	public UpdateLogicalSystemReuseDefinition(IRPApplication rpyApp) {
		super(rpyApp);
		
		stereotype = (IRPStereotype) rpyApp.activeProject().findElementByGUID(GUID_FUNCTION_REDEFINITION_STEREOTYPE);
	}

	/*
	 * All logic is implemented here 
	 */
	@SuppressWarnings("unchecked")
	@Override
	public void execute() {
		IRPModelElement selectedElement = rhApp.getSelectedElement();

		boolean isLogicalSystem = "Logical System Reuse".equals(selectedElement.getUserDefinedMetaClass());
		

		// Check if the selected element is a Logical System Reuse
		if (!isLogicalSystem) {
			rhpLog.info("Not a logical system Reuse.");
			return;
		}
		IRPClass selectedLogicalSystemReuse = (IRPClass) selectedElement;

		IRPGeneralization generation = (IRPGeneralization) selectedLogicalSystemReuse.getGeneralizations().getItem(1);

		if(! "Logical Reuse".equals(generation.getUserDefinedMetaClass())){
			rhpLog.error("Not a valid relation - requires a \"Logical Reuse\" relation to Logical System.");
			return;
		}

		IRPClass logicalSystemDefinition = (IRPClass) generation.getBaseClass();

		if(! "Logical System".equals(logicalSystemDefinition.getUserDefinedMetaClass())){
			rhpLog.error("Not a valid target - the relation shall target an Logical System.");
			return;
		}

		rhpLog.debug("User inputs are valid - tools can be executed");

		// Create two lists to store the targets from the two for-loops
		List<IRPModelElement> logicalSystemFunctionList = new ArrayList<>();
		List<IRPModelElement> logicalSystemReuseFunctionList = new ArrayList<>();

		// Logical System Function List
		List<IRPClass> logicalSystemNestedList = logicalSystemDefinition.getNestedElementsByMetaClass("Class", 0).toList();
		for (IRPClass currentClass : logicalSystemNestedList) {

			if(! "Function".equals(currentClass.getUserDefinedMetaClass())){
				rhpLog.debug("Continue execution, current class that is not a Function: "+currentClass.getFullPathName());
				continue;
			}

			IRPClass functionalClass = (IRPClass) currentClass;

			rhpLog.debug("Logical System - Function found: "+functionalClass.getFullPathName());

			logicalSystemFunctionList.add(functionalClass);
		}

		List<IRPClass> logicalSystemReuseNestedList = selectedLogicalSystemReuse.getNestedElementsByMetaClass("Class", 0).toList();
		for (IRPClass currentClass : logicalSystemReuseNestedList) {

			if(! "Function".equals(currentClass.getUserDefinedMetaClass())){
				rhpLog.debug("Continue execution, current class that is not a Function: "+currentClass.getFullPathName());
				continue;
			}
			IRPGeneralization generalization = (IRPGeneralization) currentClass.getGeneralizations().getItem(1);
			IRPClass functionalClass = (IRPClass) generalization.getBaseClass();

			rhpLog.debug("Logical System Reuse - Function found: "+functionalClass.getFullPathName());

			logicalSystemReuseFunctionList.add(functionalClass);
		}

		// Output unique elements in list1
		for (IRPModelElement element : logicalSystemFunctionList) {
			boolean isInList2 = false;
			for (IRPModelElement couple : logicalSystemReuseFunctionList) {
				if (couple.equals(element)) {
					isInList2 = true;
					break;
				}
			}
			if (!isInList2) {
				createFunctionBasedOnRealization(selectedElement, element);
			}
		}

		rhpLog.debug("Execution done.");
	}

	private void createFunctionBasedOnRealization(IRPModelElement logicalSystem, IRPModelElement function) {
		try {
			rhpLog.debug("Create a new Function: "+function.getFullPathName() +" in "+ logicalSystem.getFullPathName());
			
			IRPClass createdFunction = (IRPClass) logicalSystem.addNewAggr("Function", function.getName());
			createdFunction.addGeneralization((IRPClassifier) function);
			
			if(stereotype ==null) {
				rhpLog.debug("Stereotype is null, cannot applied to "+ createdFunction.getFullPathName());
				return;
			}
			
			createdFunction.addSpecificStereotype(stereotype);
			rhpLog.debug("Function created and stereotype applied.");
		} catch (Exception e) {
			rhpLog.error(e.getMessage());
		}
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

