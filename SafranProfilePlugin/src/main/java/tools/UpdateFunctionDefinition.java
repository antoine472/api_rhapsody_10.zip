package tools;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import utils.StereotypesApplication;


import com.telelogic.rhapsody.core.IRPApplication;
import com.telelogic.rhapsody.core.IRPClass;
import com.telelogic.rhapsody.core.IRPClassifier;
import com.telelogic.rhapsody.core.IRPCollection;
import com.telelogic.rhapsody.core.IRPGeneralization;
import com.telelogic.rhapsody.core.IRPModelElement;
import com.telelogic.rhapsody.core.IRPStereotype;

/**
 * <b>Updates the functions of a Logical System and in Technical System</b>
 * <p>
 * This helper create Function on Logical System with a Relation named "Black Box Definition"
 * </p>
 * @author s570589
 * @update my0598639 - 20/02/2026
 *
 */
public class UpdateFunctionDefinition extends RhapsodyTool {

	/**
	 * Corresponds to the name given in the HEP file
	 */
	public static final String COMMAND ="Safran Toolkit...\\Get function definition";

	private static final String GUID_FUNCTION_REDEFINITION_STEREOTYPE = "GUID b845ee49-501c-4a7c-8b7c-a86580a8abd1";

	private static final String GUID_FUNCTION_REFERENCE_STEREOTYPE = "GUID 33d8930c-eb78-48c8-bb70-0a76eec71123";

	private IRPStereotype funcRedifineStereotype = null;

	private IRPStereotype funcReferenceStereotype = null;


	public UpdateFunctionDefinition(IRPApplication rpyApp) {
		super(rpyApp);

		funcRedifineStereotype = (IRPStereotype) rpyApp.activeProject().findElementByGUID(GUID_FUNCTION_REDEFINITION_STEREOTYPE);
		funcReferenceStereotype = (IRPStereotype) rpyApp.activeProject().findElementByGUID(GUID_FUNCTION_REFERENCE_STEREOTYPE);
	}

	/*
	 * All logic is implemented here 
	 */
	@SuppressWarnings("unchecked")
	@Override
	public void execute() {
		IRPModelElement selectedElement = rhApp.getSelectedElement();

		boolean isLogicalSystem = "Logical System".equals(selectedElement.getUserDefinedMetaClass());
		boolean isTechnicalComponent = "Technical Component".equals(selectedElement.getUserDefinedMetaClass());
		boolean isFunction = "Function".equals(selectedElement.getUserDefinedMetaClass());
		boolean isFunctionalSystem = "Functional System".equals(selectedElement.getUserDefinedMetaClass());

		// Check if the selected element is a Logical System
		if (!isLogicalSystem && !isTechnicalComponent && !isFunction && !isFunctionalSystem) {
			rhpLog.info("Not a logical system or technical component, Function or Functional System.");
			return;
		}
		IRPClass selectedClass = (IRPClass) selectedElement;

		IRPGeneralization generation = null;

		List<IRPGeneralization> genList = selectedClass.getGeneralizations().toList();

		boolean isRedifineRelation = false;

		if(genList.isEmpty()) {
			rhpLog.error("Selected Element : " + selectedClass.getName() + " with no Generalization !\n");
			return;
		}

		for (IRPGeneralization objGen : genList) {
			if("Redefines System".equals(objGen.getUserDefinedMetaClass()) || "Redefines Function".equals(objGen.getUserDefinedMetaClass()) || "Redefines Component".equals(objGen.getUserDefinedMetaClass()))
			{
				generation = objGen;
				isRedifineRelation = true;
				break;
			}
		}


		if(!isRedifineRelation) {
			rhpLog.error("No Redifine relationship in this selected element : " + selectedClass.getName() + "\n");
			return;
		}

		rhpLog.debug("Redifine relationship exists in this selected element!");

		IRPClass targetElementDefinition = (IRPClass) generation.getBaseClass();

		if((! "Operational System".equals(targetElementDefinition.getUserDefinedMetaClass()))
				&& (! "Logical System".equals(targetElementDefinition.getUserDefinedMetaClass()))
				&& (! "Function".equals(targetElementDefinition.getUserDefinedMetaClass()))
				&& (! "Technical Component".equals(targetElementDefinition.getUserDefinedMetaClass()))){

			rhpLog.error("Not a valid target - the relation shall target an Operational System or Logical System or Function or Technical Component.");
			return;
		}

		rhpLog.debug("User inputs are valid - tools can be executed");

		// Create two lists to store the targets from the two for-loops
		List<IRPModelElement> targetedElementFunctionList = new ArrayList<>();
		List<IRPModelElement> selectedElementFunctionList = new ArrayList<>();

		// blackBoxFunctionList 
		List<IRPClass> blackBoxNestedList = targetElementDefinition.getNestedElementsByMetaClass("Class", 0).toList();
		for (IRPClass currentClass : blackBoxNestedList) {

			if((! "Function".equals(currentClass.getUserDefinedMetaClass())) && (! "Function With Reference".equals(currentClass.getUserDefinedMetaClass()))){
				rhpLog.debug("Continue execution, current class that is not a Function or Function With Reference: "+currentClass.getFullPathName());
				continue;
			}

			IRPClass functionalClass = (IRPClass) currentClass;

			rhpLog.debug("Black Box Function found: "+functionalClass.getFullPathName());

			targetedElementFunctionList.add(functionalClass);
		}

		Map<String, IRPClass> existingWBByKey = new HashMap<>();
		Map<String, IRPClass> existingWBByName = new HashMap<>();
		List<IRPClass> whiteBoxNestedList = selectedClass.getNestedElementsByMetaClass("Class", 0).toList();
		for (IRPClass currentClass : whiteBoxNestedList) {

		    // index par nom (pour détecter les clashes)
		    existingWBByName.putIfAbsent(currentClass.getName(), currentClass);

		    String udmc = currentClass.getUserDefinedMetaClass();
		    if((! "Function".equals(udmc)) && (! "Function With Reference".equals(udmc))){
		        continue;
		    }

		    String key = buildKey(udmc, currentClass.getName());
		    existingWBByKey.put(key, currentClass);

		    rhpLog.debug("WB existing: " + key + " (" + currentClass.getFullPathName() + ")");
		}

		// --- sync BB -> WB ---
		for (IRPModelElement element : targetedElementFunctionList) {

		    String udmc = element.getUserDefinedMetaClass();
		    String key = buildKey(udmc, element.getName());

		    // 1) déjà présent (même UDMC + même nom) => update
		    if (existingWBByKey.containsKey(key)) {
		        IRPClass existing = existingWBByKey.get(key);

		        rhpLog.debug("Already exists in WB, update instead of create: " + key);

		        if ("Function".equals(udmc)) {
		            ensureFunctionIsRedefinition(existing, (IRPClass) element);
		        } else {
		            ensureFunctionWithRefIsReference(existing, (IRPClass) element);
		        }
		        continue;
		    }

		    // 2) clash de nom (même nom, UDMC différent / ou non éligible) => SKIP + WARN
		    IRPClass sameNameExisting = existingWBByName.get(element.getName());
		    if (sameNameExisting != null) {
		        rhpLog.warn("Name clash in WB: cannot create '" + udmc + "' named '" + element.getName()
		            + "' under '" + selectedClass.getFullPathName()
		            + "' because an element with the same name already exists (existing UDMC='"
		            + sameNameExisting.getUserDefinedMetaClass() + "', existing path='"
		            + sameNameExisting.getFullPathName() + "'). Skipping.");
		        continue;
		    }

		    // 3) création normale (pas de clash)
		    if ("Function".equals(udmc)) {
		        createFunctionBasedOnRealization(selectedElement, element);
		    } else {
		        createFunctionWithRefBasedOnRealization(selectedElement, element);
		    }
		}

		rhpLog.debug("Execution done.");
	}

	private void ensureFunctionIsRedefinition(IRPClass existingWBFunction, IRPClass blackBoxFunction) {
		if (existingWBFunction == null || blackBoxFunction == null) return;

		IRPGeneralization g = findGeneralizationTo(existingWBFunction, blackBoxFunction);
		if (g == null) {
			existingWBFunction.addGeneralization((IRPClassifier) blackBoxFunction);
			rhpLog.debug("WB Function: generalization added to " + blackBoxFunction.getFullPathName());
			g = findGeneralizationTo(existingWBFunction, blackBoxFunction);
		}

		// stéréotypes (si tu veux aussi “réparer”)
		StereotypesApplication.setStereotypes(blackBoxFunction, existingWBFunction, false);

		if (g != null && funcRedifineStereotype != null) {
			g.addSpecificStereotype(funcRedifineStereotype);
			rhpLog.debug("WB Function: redefine stereotype ensured.");
		}
	}
	
	private void ensureFunctionWithRefIsReference(IRPClass existingWBRef, IRPClass blackBoxRef) {
	    if (existingWBRef == null || blackBoxRef == null) return;

	    // 1) Récupérer la base Function du blackbox ref (via sa généralisation)
	    IRPCollection gensBB = blackBoxRef.getGeneralizations();
	    if (gensBB == null || gensBB.getCount() == 0) {
	        rhpLog.warn("BlackBox Function With Reference has no generalization (cannot find base): " + blackBoxRef.getFullPathName());
	        return;
	    }

	    IRPGeneralization bbGen = (IRPGeneralization) gensBB.getItem(1);
	    if (bbGen == null || bbGen.getBaseClass() == null) {
	        rhpLog.warn("BlackBox Function With Reference has invalid base: " + blackBoxRef.getFullPathName());
	        return;
	    }

	    IRPClass baseFunction = (IRPClass) bbGen.getBaseClass();

	    // 2) S'assurer que la WB ref généralise bien la même base Function
	    IRPGeneralization wbGen = findGeneralizationTo(existingWBRef, baseFunction);
	    if (wbGen == null) {
	        existingWBRef.addGeneralization((IRPClassifier) baseFunction);
	        rhpLog.debug("WB Ref: generalization added to base function " + baseFunction.getFullPathName());
	        wbGen = findGeneralizationTo(existingWBRef, baseFunction);
	    }

	    // 3) S'assurer de la dépendance vers la ref function de la super classe
	    // (on l'ajoute sans check : si tu veux éviter les doublons, on pourra affiner plus tard)
	    existingWBRef.addDependencyTo(blackBoxRef);
	    rhpLog.debug("WB Ref: dependency ensured to BB ref " + blackBoxRef.getFullPathName());

	    // 4) Stereotypes / stéréotype de relation
	    StereotypesApplication.setStereotypes(blackBoxRef, existingWBRef, false);

	    if (wbGen != null && funcReferenceStereotype != null) {
	        wbGen.addSpecificStereotype(funcReferenceStereotype);
	        rhpLog.debug("WB Ref: reference stereotype ensured.");
	    }
	}

	private String buildKey(String udmc, String name) {
		return udmc + "||" + name;
	}

	private IRPGeneralization findGeneralizationTo(IRPClass child, IRPClass base) {
		if (child == null || base == null) return null;

		IRPCollection gens = child.getGeneralizations();
		if (gens == null || gens.getCount() == 0) return null;

		@SuppressWarnings("unchecked")
		List<Object> list = gens.toList();
		for (Object o : list) {
			if (o instanceof IRPGeneralization) {
				IRPGeneralization g = (IRPGeneralization) o;
				IRPClass bc = (IRPClass) g.getBaseClass();
				if (base.equals(bc)) return g;
			}
		}
		return null;
	}

	private void createFunctionWithRefBasedOnRealization(IRPModelElement logicalSystem, IRPModelElement functionWithRef) {

		try {
			rhpLog.debug("Create a new Function With Reference: "+functionWithRef.getFullPathName() +" in "+ logicalSystem.getFullPathName());

			IRPClass createdFunctionWithRef = (IRPClass) logicalSystem.addNewAggr("Function With Reference", functionWithRef.getName());

			rhpLog.debug("Function With Reference created.");

			IRPGeneralization generalizationFunctionWithRef = (IRPGeneralization) ((IRPClass) functionWithRef).getGeneralizations().getItem(1);

			IRPClass functionWithRefClass = (IRPClass) generalizationFunctionWithRef.getBaseClass();

			createdFunctionWithRef.addGeneralization((IRPClassifier) functionWithRefClass);

			rhpLog.debug("Generalization added.");

			createdFunctionWithRef.addDependencyTo(functionWithRef);

			rhpLog.debug("Dependency added.");

			if(funcRedifineStereotype == null) {
				rhpLog.debug("Stereotype is null, cannot applied to generalization of "+ createdFunctionWithRef.getFullPathName());
				return;
			}

			StereotypesApplication.setStereotypes(functionWithRef,createdFunctionWithRef,false);

			IRPGeneralization mygeneralization = (IRPGeneralization) createdFunctionWithRef.getGeneralizations().getItem(1);

			mygeneralization.addSpecificStereotype(funcReferenceStereotype);
			rhpLog.debug("function Reference Stereotype applied.");
		}
		catch (Exception e) {
			rhpLog.error(e.getMessage());
		}

	}

	private void createFunctionBasedOnRealization(IRPModelElement logicalSystem, IRPModelElement function) {
		try {
			rhpLog.debug("Create a new Function: "+function.getFullPathName() +" in "+ logicalSystem.getFullPathName());

			IRPClass createdFunction = (IRPClass) logicalSystem.addNewAggr("Function", function.getName());

			rhpLog.debug("Function created.");

			createdFunction.addGeneralization((IRPClassifier) function);

			rhpLog.debug("Generalization added.");

			if(funcRedifineStereotype ==null) {
				rhpLog.debug("Stereotype is null, cannot applied to generalization of "+ createdFunction.getFullPathName());
				return;
			}

			StereotypesApplication.setStereotypes(function,createdFunction,false);

			IRPGeneralization mygeneralization = (IRPGeneralization) createdFunction.getGeneralizations().getItem(1);

			mygeneralization.addSpecificStereotype(funcRedifineStereotype);
			rhpLog.debug("function Redifine Stereotype applied.");
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

