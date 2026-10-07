package tools.strategies;

import com.telelogic.rhapsody.core.*;
import logging.RhapsodyLogger;
import utils.PortCreation;

import java.util.List;
import java.util.stream.Collectors;

public class GenericFlowPortClass implements GenericFlowStrategy {

	protected static RhapsodyLogger rhpLog = RhapsodyLogger.getInstance();

	protected IRPApplication rhApp;

	private IRPFlow flow = null;

	private IRPSysMLPort port = null;
	private IRPClass clazz = null;
	private IRPDiagram diagram = null;

	private String targetPosition;

	public GenericFlowPortClass(IRPApplication rpyApp, IRPSysMLPort sourcePort, IRPClass targetClass) {
		rhApp = rpyApp;
		port = sourcePort;
		clazz = targetClass;
	}

	@Override
	public void apply(IRPGraphElement selectedGraphElement) {
		try {
			diagram = selectedGraphElement.getDiagram();

			targetPosition = selectedGraphElement.getGraphicalProperty("TargetPosition").getValue();

			flow = (IRPFlow) selectedGraphElement.getModelObject();

			createFlowBetweenPortAndClass(port,clazz);
			flow.addConveyed(port.getType());


		} catch (Exception e) {
			rhpLog.error("Exception in apply: " + e.getMessage());
		}
	}

	private void createFlowBetweenPortAndClass(IRPSysMLPort sourcePort, IRPClass targetClass) {
		try {
			IRPSysMLPort createdPort = PortCreation.createPort(targetClass);

			IRPGraphNode createdPortGraphElement = findGraphNodeForModelElement(diagram, createdPort);

			if (createdPortGraphElement == null) {
				rhpLog.error("Graph nodes for port or class are missing.");
				throw new Exception("Graph nodes for created port are missing.");
			}

			createdPortGraphElement.setGraphicalProperty("Position", targetPosition);

			flow.setEnd1(sourcePort);
			flow.setEnd2(createdPort);

			defineFlowMetaClass((IRPClass) sourcePort.getOwner(), targetClass);

			createdPort.setType(sourcePort.getType());

			if ("bidirectional".equals(flow.getDirection())) {
				createdPort.setPortDirection("InOut");
				sourcePort.setPortDirection("InOut");
			}else {
				createdPort.setPortDirection("In"); // always true
				
				if(sourcePort.getSaveUnit().isReadOnly() == 1){
					// TODO define behavior
				}
				else {
					// Set the target port direction based on the nature check and exception
					if ((doesNatureDiffer(targetClass, (IRPClass) sourcePort.getOwner()) && !isExceptionalCase(targetClass, (IRPClass) sourcePort.getOwner())) || 
				             isTargetChild(targetClass, (IRPClass) sourcePort.getOwner())) {
						sourcePort.setPortDirection("In");
			        } else {
			        	sourcePort.setPortDirection("Out");
			        }
				}
			}

			// copy stereotypes
			List<IRPStereotype> sourcePortStereotypes = (List<IRPStereotype>) sourcePort.getStereotypes().toList().stream()
					.filter(stereotype -> ((IRPStereotype) stereotype).getIsNewTerm() != 1)
					.collect(Collectors.toList());

			// Apply source stereotypes to target
			for (IRPStereotype stereotype : sourcePortStereotypes) {
				createdPort.addSpecificStereotype(stereotype);
				flow.addSpecificStereotype(stereotype);
			}


			// Represent the flow in the diagram
			IRPCollection graphElements = rhApp.createNewCollection();
			IRPGraphNode portGraphElement = findGraphNodeForModelElement(diagram, sourcePort);
			graphElements.addGraphicalItem(portGraphElement);
			graphElements.addGraphicalItem(createdPortGraphElement);

			diagram.completeRelations(graphElements, 0);

			rhpLog.debug("Flow between Port and Class created successfully.");
		} catch (Exception e) {
			rhpLog.error("Failed to create flow between Port and Class: " + e.getMessage());
		}
	}
	

	// Method to determine if the nature of the source class and target class differ
    private boolean doesNatureDiffer(IRPClass sourceClass, IRPClass targetClass) {
        String sourceMetaClass = sourceClass.getUserDefinedMetaClass();
		String targetMetaClass = targetClass.getUserDefinedMetaClass();
        
        return !sourceMetaClass.equals(targetMetaClass);
    }
    
    // Method to determine the exceptional case
    private boolean isExceptionalCase(IRPClass sourceClass, IRPClass targetClass) {
        String sourceNature = sourceClass.getUserDefinedMetaClass();
        String targetNature = targetClass.getUserDefinedMetaClass();

        boolean sourceIsOperationalSystem = sourceNature.equals("Operational System");
        boolean sourceIsStakeholder = sourceNature.equals("Stakeholder");
        boolean targetIsOperationalSystem = targetNature.equals("Operational System");
        boolean targetIsStakeholder = targetNature.equals("Stakeholder");


        return (sourceIsOperationalSystem && targetIsStakeholder) || 
               (sourceIsStakeholder && targetIsOperationalSystem);
    }
    
 // Method to check if target is a child of source class
    boolean isTargetChild(IRPClass sourceClass, IRPClass targetClass) {
        return targetClass.equals(sourceClass.getOwner());
    }

    private void defineFlowMetaClass(IRPClass sourceClass, IRPClass targetClass) {
		String sourceMetaClass = sourceClass.getUserDefinedMetaClass();
		String targetMetaClass = targetClass.getUserDefinedMetaClass();

		if("Function".equals(sourceMetaClass) || "Function".equals(targetMetaClass)) {
			flow.changeTo("Functional Flow");
		} else if (("Operational System".equals(sourceMetaClass) || "Stakeholder".equals(sourceMetaClass))
				&& ("Operational System".equals(targetMetaClass) || "Stakeholder".equals(targetMetaClass))) {
			flow.changeTo("Operational Flow");
		} else if ("Logical System".equals(sourceMetaClass) || "Logical System".equals(targetMetaClass)) {
			flow.changeTo("Logical Flow");
		} else if ("Technical Component".equals(sourceMetaClass) && "Technical Component".equals(targetMetaClass)) {
			flow.changeTo("Technical Flow");
		} else {
			// Optional: Handle invalid cases where none of the conditions are met
			rhpLog.error("Invalid source or target meta-class combination: source=" + sourceMetaClass + ", target=" + targetMetaClass);
		}
	}


	private IRPGraphNode findGraphNodeForModelElement(IRPDiagram diagram, IRPModelElement modelElement) {
		List<IRPGraphElement> graphElementList = diagram.getGraphicalElements().toList();
		for (IRPGraphElement graphElement : graphElementList) {
			if (modelElement.equals(graphElement.getModelObject())) {
				return (IRPGraphNode) graphElement;
			}
		}
		return null;
	}
}
