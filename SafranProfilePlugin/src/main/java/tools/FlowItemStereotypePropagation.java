package tools;

import java.util.ArrayList;
import java.util.List;
import com.telelogic.rhapsody.core.*;

import tools.strategies.FlowItemSelectionSupport;

/**
 * <b>Propagation of Stereotypes for Flow Items</b>
 * <p>
 * This feature propagates exclusive stereotypes (having a "Exclusive" tag) 
 * from the selected Flow Items to their referenced elements.
 * </p>
 */
public class FlowItemStereotypePropagation extends RhapsodyTool {

	public static final String COMMAND = "Safran Toolkit...\\Propagate stereotype to all";
	private List<IRPClass> selectedFlowItems = new ArrayList<>();

	public FlowItemStereotypePropagation(IRPApplication rpyApp) {
		super(rpyApp);
	}

	@Override
	public void execute() {
		rhpLog.debug("Starting execution of " + COMMAND);

		IRPCollection selectedElements = rhApp.getListOfSelectedElements();
		if (selectedElements == null || selectedElements.getCount() == 0) {
			rhpLog.info("No element selected.");
			return;
		}

		for (Object selected : selectedElements.toList()) {
			if (selected instanceof IRPClass) {
				IRPClass selectedModelElement = (IRPClass) selected;

				if ("Flow Item".equals(selectedModelElement.getUserDefinedMetaClass())) {
					selectedFlowItems.add(selectedModelElement);
					rhpLog.debug("Selected Flow Item: " + selectedModelElement.getFullPathName());

					IRPCollection references = selectedModelElement.getReferences();
					rhpLog.debug("Number of references found for " + selectedModelElement.getFullPathName() + ": " + references.getCount());

					for (Object obj : references.toList()) {
						if (obj instanceof IRPDependency) {
							IRPDependency dependency = (IRPDependency) obj;
							String metaClass = dependency.getUserDefinedMetaClass();
							rhpLog.info("Propagating to Data Flow (dependency): " + dependency.getFullPathName() + " (Type: " + metaClass + ")");
							IRPModelElement dependent = dependency.getDependent();
							propagateStereotype(selectedModelElement, dependent);

							// A sequence-diagram message copies its name/label from the Flow Item
							// at assignment; a native rename of the Flow Item does not propagate to
							// that stored name, so the drawn message label goes stale. Re-sync it
							// here (messages only) so this tool also fixes the diagram labels.
							if (dependent instanceof IRPMessage) {
								FlowItemSelectionSupport.syncNames(dependent, selectedModelElement, rhpLog);
								rhpLog.info("Synced message name/label from Flow Item '"
										+ selectedModelElement.getName() + "' on " + dependent.getFullPathName());
							}

						} else if (obj instanceof IRPModelElement) {
							IRPModelElement referencedElement = (IRPModelElement) obj;
							String metaClass = referencedElement.getUserDefinedMetaClass();
							if ("Flow Port".equals(metaClass)) {
								rhpLog.info("Propagating to Flow Port: " + referencedElement.getFullPathName() + " (Type: " + metaClass + ")");
								propagateStereotype(selectedModelElement, referencedElement);
							} else if (referencedElement instanceof IRPFlow) {
								rhpLog.info("Propagating to Flow: " + referencedElement.getFullPathName() + " (Type: " + metaClass + ")");
								propagateStereotype(selectedModelElement, referencedElement);
							} else {
								rhpLog.debug("Reference ignored (Non-relevant Type): " + referencedElement.getFullPathName());
							}
						} else {
							rhpLog.info("Unknown reference: " + obj);
						}
					}
				}
			}
		}

		if (selectedFlowItems.isEmpty()) {
			rhpLog.info("No valid Flow Item selected.");
			return;
		}

		rhpLog.debug("Total number of selected Flow Items: " + selectedFlowItems.size());
		rhpLog.info("End of execution of " + COMMAND);
	}

	@Override
	public String commandName() {
		return COMMAND;
	}

	/**
	 * Propagates exclusive stereotypes from the Flow Item to the target element. 
	 * If multiple exclusive stereotypes are present, the propagation is canceled.
	 */
	private void propagateStereotype(IRPClass flowItem, IRPModelElement target) {
		IRPCollection flowItemStereotypes = flowItem.getStereotypes();
		if (flowItemStereotypes.getCount() == 0) {
			rhpLog.info("No stereotype found on " + flowItem.getFullPathName() + ", nothing to propagate.");
			return;
		}

		List<IRPStereotype> exclusiveToPropagate = new ArrayList<>();

		// Étape 1 : extraire les stéréotypes exclusifs à propager
		for (Object obj : flowItemStereotypes.toList()) {
			IRPStereotype st = (IRPStereotype) obj;
			if (isExclusiveStereotype(st)) {
				exclusiveToPropagate.add(st);
				rhpLog.debug("Exclusive stereotype to propagate: " + st.getName());
			}
		}

		// Vérification : un seul exclusif autorisé
		if (exclusiveToPropagate.size() > 1) {
			rhpLog.info("Multiple exclusive stereotypes on " + flowItem.getFullPathName() + ", propagation canceled.");
			return;
		}

		if (exclusiveToPropagate.isEmpty()) {
			rhpLog.info("No exclusive stereotype found to propagate from " + flowItem.getFullPathName());
			return;
		}

		IRPStereotype toPropagate = exclusiveToPropagate.get(0);
		// Vérification : si cible est un Flow Port sans type, ignorer // TODO: check if this case can happen
		if ("Flow Port".equals(target.getUserDefinedMetaClass()) && target instanceof IRPSysMLPort) {
			IRPSysMLPort port = (IRPSysMLPort) target;
			if (port.getType() == null) {
				rhpLog.info("Skipping stereotype propagation on untyped Flow Port: " + port.getFullPathName());
				return;
			}
		}


		// Étape 2 : récupérer les stéréotypes actuels de la cible
		IRPCollection targetStereotypes = target.getStereotypes();
		List<IRPStereotype> toRemove = new ArrayList<>();

		for (Object obj : targetStereotypes.toList()) {
			if (obj instanceof IRPStereotype) {
				IRPStereotype st = (IRPStereotype) obj;
				if (isExclusiveStereotype(st) && !st.getName().equals(toPropagate.getName())) {
					toRemove.add(st);
					rhpLog.info("Replacing existing exclusive stereotype '" + st.getName() +
							"' on " + target.getFullPathName() + " with '" + toPropagate.getName() + "'");
				}
			}
		}

		// Étape 3 : supprimer les exclusifs existants différents
		for (IRPStereotype st : toRemove) {
			target.removeStereotype(st);
		}

		// Étape 4 : ajouter le nouveau stéréotype s’il n’est pas déjà là
		if (!targetStereotypes.toList().contains(toPropagate)) {
			target.addSpecificStereotype(toPropagate);
			rhpLog.info("Applied stereotype '" + toPropagate.getName() + "' to " + target.getFullPathName());
		} else {
			rhpLog.debug("Stereotype '" + toPropagate.getName() + "' already present on " + target.getFullPathName());
		}
	}


	/**
	 * Checks if a stereotype has the "Exclusive" tag.
	 */
	private boolean isExclusiveStereotype(IRPStereotype stereotype) {
		return stereotype.getTag("Exclusive") != null;
	}

	@Override
	public boolean isUndoable() {
		return true;
	}
}
