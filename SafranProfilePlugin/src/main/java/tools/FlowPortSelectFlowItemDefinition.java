package tools;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import com.telelogic.rhapsody.core.IRPApplication;
import com.telelogic.rhapsody.core.IRPClass;
import com.telelogic.rhapsody.core.IRPClassifier;
import com.telelogic.rhapsody.core.IRPMessage;
import com.telelogic.rhapsody.core.IRPModelElement;
import com.telelogic.rhapsody.core.IRPPackage;
import com.telelogic.rhapsody.core.IRPStereotype;
import com.telelogic.rhapsody.core.IRPSysMLPort;

import main.constants.RhpMetaClassConstants;
import main.gui.tools.ItemTreeSelector;

/**
 * <b>Implementation of specific action</b>
 * <p>
 * This class lists all available Flow Items in the model and creates
 * a dependency with the selected Flow Item.
 * Also sync the name and label
 * Description of the admin helper
 * </p>
 * @author s570589
 *
 */
public class FlowPortSelectFlowItemDefinition extends RhapsodyTool {

	public static final String COMMAND = "Safran Toolkit...\\Select Flow Item";

	private Icon functionIcon = new ImageIcon("../SafranArchitectureProfile/Icons/FlowItemSingleIcon.png");

	private boolean isSyncDataFlow = true;


	private List<IRPModelElement> objectItems = new ArrayList<>();
	public FlowPortSelectFlowItemDefinition(IRPApplication rpyApp) {
		super(rpyApp);
	}

	/*
	 * All logic is implemented here 
	 */
	@SuppressWarnings("unchecked")
	@Override
	public void execute() {
		rhpLog.debug("Execution of " + COMMAND);

		IRPModelElement selectedModelElement = rhApp.getSelectedElement();

		if (! (selectedModelElement instanceof IRPSysMLPort)) {
			rhpLog.info("Flow Port not selected.");
			return;
		}

		IRPSysMLPort selectedPort = (IRPSysMLPort) selectedModelElement;
		iterateOverFlowItems();

		ItemTreeSelector selector = new ItemTreeSelector(rhApp, objectItems, functionIcon, "Select flow item...");

		IRPModelElement selectedFlowItem = selector.showSelectionDialog();

		if (selectedFlowItem != null) {
			rhpLog.info("Selected Flow Item: " + selectedFlowItem.getName());
			selectedPort.setType((IRPClassifier) selectedFlowItem);

			if(isSyncDataFlow) {
				// sync Stereotypes
				
				
				List<IRPStereotype> flowItemStereotypes = (List<IRPStereotype>) selectedFlowItem.getStereotypes().toList().stream()
				.filter(stereotype -> ((IRPStereotype) stereotype).getIsNewTerm() != 1)
				.collect(Collectors.toList());
				
				List<IRPStereotype> portStereotypes = (List<IRPStereotype>) selectedPort.getStereotypes().toList().stream()
						.filter(stereotype -> ((IRPStereotype) stereotype).getIsNewTerm() != 1)
						.collect(Collectors.toList());
				
				for(IRPStereotype stereotype : portStereotypes) {
					selectedPort.removeStereotype(stereotype);
				}
				
				for(IRPStereotype stereotype : flowItemStereotypes) {
					selectedPort.addSpecificStereotype(stereotype);
				}
				
			}
		} else {
			rhpLog.info("No Flow Item selected.");
		}
		rhpLog.debug("End of execution of " + COMMAND);
	}

	@Override
	public String commandName() {
		return COMMAND;
	}

	public void iterateOverFlowItems(){
		rhpLog.info("start - class");

		List<?> allClasses = rhApp.activeProject().getNestedElementsByMetaClass(RhpMetaClassConstants.CLASS, 1).toList();

		for (Object rhpClass : allClasses) {
			if (rhpClass instanceof IRPClass) {
				IRPClass currentClass = (IRPClass) rhpClass;
				if("Flow Item".equals(currentClass.getUserDefinedMetaClass())) {
					objectItems.add(currentClass);
				}
			}
		}

		rhpLog.info("end - class");
	}

	/**
	 * This function lists all packages in the model,
	 * Filters "Flow Packages" and retieves all Flow Items
	 * 
	 * Perhaps has better performances ?
	 */
	@SuppressWarnings({ "unused", "unchecked" })
	private void iterateOverPackageThenFlowItems(){
		rhpLog.info("start - package");

		List<IRPPackage> allPackages = rhApp.activeProject().getNestedElementsByMetaClass(RhpMetaClassConstants.PACKAGE, 1).toList();

		for (IRPPackage rhpPackage : allPackages) {

			if("F - Flow Package".equals(rhpPackage.getUserDefinedMetaClass())) {

				List<IRPClass> allClasses = rhpPackage.getNestedElementsByMetaClass(RhpMetaClassConstants.CLASS, 1).toList();
				for (Object rhpClass : allClasses) {

					if (rhpClass instanceof IRPClass) {
						IRPClass currentClass = (IRPClass) rhpClass;
						if("Flow Item".equals(currentClass.getUserDefinedMetaClass())) {


							rhpLog.info("class: "+currentClass.getName());
						}
					}


				}
			}
		}

		rhpLog.info("end - package");

	}

	@Override
	public boolean isUndoable() {
		return true;
	}

}
