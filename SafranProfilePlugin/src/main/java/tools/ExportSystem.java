package tools;

import javax.swing.JFrame;
import javax.swing.JOptionPane;

import com.telelogic.rhapsody.core.IRPApplication;
import com.telelogic.rhapsody.core.IRPClass;
import com.telelogic.rhapsody.core.IRPCollection;
import com.telelogic.rhapsody.core.IRPGeneralization;
import com.telelogic.rhapsody.core.IRPModelElement;
import com.telelogic.rhapsody.core.IRPPackage;
import com.telelogic.rhapsody.core.IRPPort;

/**
 * <b>Extract a Logical System to library</b>
 * <p>
 * This feature extracts all informations inside a Logical System to be exported to library of a Rhapsody Model.
 * </p>
 * @author s570589
 *
 */
public class ExportSystem extends RhapsodyTool {
	
	public static final String COMMAND = "Safran Toolkit...\\Export to library";

	@SuppressWarnings("unused")
	private IRPPackage libMainPackage, libDomainPackage, libLogicalPackage, libTechnicalPackage;
	
	private IRPClass libLogicalSystem, libOperationalSystem;
	
	
	public ExportSystem(IRPApplication rpyApp) {
		super(rpyApp);
	}

	/*
	 * All logic is implemented here 
	 */
	@Override
	public void execute() {
		IRPModelElement selectedElement = rhApp.getSelectedElement();

		JFrame jf=new JFrame();
		jf.setAlwaysOnTop(true);

		int response = JOptionPane.showConfirmDialog(jf,
				"Where to export the system :"+selectedElement.getName()+" ?\nSelect package",
				"Exporting System",
				JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.QUESTION_MESSAGE);

		if (response == JOptionPane.CANCEL_OPTION) {
			return;
		}

		IRPModelElement selectedPackageDestination = rhApp.getSelectedElement();

		rhpLog.debug(selectedElement.getName() +" will be exported to "+selectedPackageDestination.getName());
		
		if("Logical System".equals(selectedElement.getUserDefinedMetaClass())) {
			// build import structure
			buildPackageStructure((IRPPackage) selectedPackageDestination, selectedElement);
			
			// migrate same level data
			libLogicalSystem = (IRPClass) libLogicalPackage.addNewAggr("Logical System", selectedElement.getName());
			migrateLogicalData((IRPClass) selectedElement, libLogicalSystem, true);
			selectedElement.changeTo("Logical System Reuse");
			
			// create a BB defition
			libOperationalSystem = (IRPClass) libDomainPackage.addNewAggr("Operational System", selectedElement.getName());
			migrateLogicalData(libLogicalSystem, libOperationalSystem, false);
		}





	}

	private void buildPackageStructure(IRPPackage selectedPackageDestination, IRPModelElement selectedElement) {
		// system definition
		libMainPackage = selectedPackageDestination.addNestedPackage(selectedElement.getName()+" Definition");
		// packageDefinition.setSeparateSaveUnit(1);

		// create a domain package
		libDomainPackage = (IRPPackage) libMainPackage.addNewAggr("Domain Package", selectedElement.getName()+" BB");
		// create a Logical Architecture Package
		libLogicalPackage = (IRPPackage) libMainPackage.addNewAggr("C - Logical Architecture Package", selectedElement.getName()+" Logical WB");

		// create a Technical Analysis Package
		libTechnicalPackage = (IRPPackage) libMainPackage.addNewAggr("D - Technical Analysis Package", selectedElement.getName()+" Technical WB");
	}

	/**
	 * 
	 * @param sourceSystem
	 * @param targetSystem
	 */
	private void migrateLogicalData(IRPClass sourceSystem, IRPClass targetSystem, boolean withNested) {
		sourceSystem.addGeneralization(targetSystem);

		moveAllPorts(sourceSystem, targetSystem);



		// create all functions

		for(Object obj : sourceSystem.getNestedElementsByMetaClass("Class", 0).toList()){
			IRPClass function = (IRPClass) obj;
			rhpLog.debug(function.getUserDefinedMetaClass());
			if("Function".equals(function.getUserDefinedMetaClass())) {
				
				IRPCollection generalizationLinks = function.getGeneralizations();
				if(generalizationLinks.getCount() == 0) {
					IRPClass createdFunction = (IRPClass) targetSystem.addNewAggr("Function", function.getName());

					function.addGeneralization(createdFunction);
					function.changeTo("Function Call");
					moveAllPorts(function, createdFunction);
				}
				else {
					function.changeTo("Function Call");
					
					IRPClass parentFunction = (IRPClass) ((IRPGeneralization) generalizationLinks.getItem(1)).getBaseClass();
					IRPClass createdFunction = (IRPClass) targetSystem.addNewAggr("Function", parentFunction.getName());

					parentFunction.addGeneralization(createdFunction);
					parentFunction.changeTo("Function Call");
					moveAllPorts(parentFunction, createdFunction);
				}
				
			}

			if(withNested) {
				// manage nested logical systems
				if("Logical System".equals(function.getUserDefinedMetaClass())) {
					IRPClass createdNestedLogicalSystem = (IRPClass) targetSystem.addNewAggr("Logical System", function.getName());

					function.addGeneralization(createdNestedLogicalSystem);
					function.changeTo("Logical System Reuse");
					
					moveAllPorts(function, createdNestedLogicalSystem);		
				}
			}
		}

	}

	/**
	 * Moves all ports
	 * @param source
	 * @param target
	 */
	private void moveAllPorts(IRPClass source, IRPClass target) {
		// move all ports
		for(Object obj : source.getPorts().toList()) {
			IRPPort port = (IRPPort) obj;
			port.setOwner(target);
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
