package tools.strategies;

import java.util.List;
import java.util.stream.Collectors;

import com.telelogic.rhapsody.core.IRPApplication;
import com.telelogic.rhapsody.core.IRPClass;
import com.telelogic.rhapsody.core.IRPClassifier;
import com.telelogic.rhapsody.core.IRPCollection;
import com.telelogic.rhapsody.core.IRPDiagram;
import com.telelogic.rhapsody.core.IRPFlow;
import com.telelogic.rhapsody.core.IRPGraphElement;
import com.telelogic.rhapsody.core.IRPGraphNode;
import com.telelogic.rhapsody.core.IRPModelElement;
import com.telelogic.rhapsody.core.IRPStereotype;
import com.telelogic.rhapsody.core.IRPSysMLPort;

import logging.RhapsodyLogger;
import main.constants.ProfilConstants;
import utils.PortCreation;

public class GenericFlowClasses implements GenericFlowStrategy {

    protected static RhapsodyLogger rhpLog = RhapsodyLogger.getInstance();

    protected IRPApplication rhApp;

    private IRPFlow flow = null;

    public GenericFlowClasses(IRPApplication rpyApp) {
        rhApp = rpyApp;
    }

    @Override
    public void apply(IRPGraphElement selectedGraphElement) {

        IRPModelElement mo = selectedGraphElement.getModelObject();
        if (!(mo instanceof IRPFlow)) {
            rhpLog.error("Selected element is not a Flow.");
            return;
        }

        IRPFlow originalFlow = (IRPFlow) mo;
        flow = originalFlow;

        IRPModelElement end1 = originalFlow.getEnd1();
        IRPModelElement end2 = originalFlow.getEnd2();

        if (!(end1 instanceof IRPClass) || !(end2 instanceof IRPClass)) {
            rhpLog.error("GenericFlowClasses expects Flow between 2 Classes.");
            return;
        }

        IRPClass sourceClass = (IRPClass) end1;
        IRPClass targetClass = (IRPClass) end2;

        IRPDiagram diagram = selectedGraphElement.getDiagram();
        if (diagram == null) {
            rhpLog.error("Diagram is null.");
            return;
        }

        String sourcePosition = selectedGraphElement.getGraphicalProperty("SourcePosition").getValue();
        String targetPosition = selectedGraphElement.getGraphicalProperty("TargetPosition").getValue();

        // 1) Déterminer le type de flow à appliquer
        String sourceMetaClass = sourceClass.getUserDefinedMetaClass();
        String targetMetaClass = targetClass.getUserDefinedMetaClass();

        String desiredFlowType = null;

        if (("Operational System".equals(sourceMetaClass) || "Stakeholder".equals(sourceMetaClass)) &&
            ("Operational System".equals(targetMetaClass) || "Stakeholder".equals(targetMetaClass))) {
            desiredFlowType = "Operational Flow";
        } else if ("Function".equals(sourceMetaClass) && "Function".equals(targetMetaClass)) {
            desiredFlowType = "Functional Flow";
        } else if ("Logical System".equals(sourceMetaClass) && "Logical System".equals(targetMetaClass)) {
            desiredFlowType = "Logical Flow";
        } else if ("Technical Component".equals(sourceMetaClass) && "Technical Component".equals(targetMetaClass)) {
            desiredFlowType = "Technical Flow";
        } else {
            rhpLog.error("Invalid source/target meta-class combination: source=" + sourceMetaClass + ", target=" + targetMetaClass);
        }

        // 2) Supprimer l’arête graphique sélectionnée (elle correspond à l'ancien flow qui va être détruit)
        try {
            IRPCollection toRemove = rhApp.createNewCollection();
            toRemove.addGraphicalItem(selectedGraphElement);
            diagram.removeGraphElements(toRemove);
        } catch (Exception e) {
            rhpLog.error("Failed to remove original flow graph element: " + e);
        }

        // 3) changeTo (IMPORTANT : récupérer l’élément retourné)
        if (desiredFlowType != null) {
            IRPModelElement changed = null;
            try {
                changed = flow.changeTo(desiredFlowType);
            } catch (Exception e) {
                rhpLog.error("flow.changeTo failed: " + e);
                return;
            }

            if (!(changed instanceof IRPFlow)) {
                rhpLog.error("changeTo did not return an IRPFlow. Returned=" + (changed == null ? "null" : changed.getMetaClass()));
                return;
            }
            flow = (IRPFlow) changed;
        }

        // 4) Créer les ports
        IRPSysMLPort sourcePort = PortCreation.createPort(sourceClass);
        IRPSysMLPort targetPort = PortCreation.createPort(targetClass);

        // 5) Typage ports + stéréotypes (sur le BON flow)
        if (isTypedPortsWithFlowItem) {
            typePort(sourcePort, targetPort, flow);
        }

        // 6) Trouver les graph nodes des ports
        IRPGraphNode sourceGraphElement = null;
        IRPGraphNode targetGraphElement = null;

        @SuppressWarnings("unchecked")
        List<IRPGraphElement> graphElementList = diagram.getGraphicalElements().toList();

        for (IRPGraphElement ge : graphElementList) {
            IRPModelElement geMo = ge.getModelObject();
            if (geMo == null) {
                rhpLog.debug("modelElementGraphical is null");
                continue;
            }
            if (geMo.equals(sourcePort)) {
                sourceGraphElement = (IRPGraphNode) ge;
            } else if (geMo.equals(targetPort)) {
                targetGraphElement = (IRPGraphNode) ge;
            }
        }

        if (sourceGraphElement == null || targetGraphElement == null) {
            rhpLog.error("Error source/target port graphElement is null (ports not shown on diagram?)");
            return;
        }

        // 7) Positionner
        sourceGraphElement.setGraphicalProperty("Position", sourcePosition);
        targetGraphElement.setGraphicalProperty("Position", targetPosition);

        // 8) Changer End1/End2 vers les ports
        flow.setEnd1(sourcePort);
        flow.setEnd2(targetPort);

        // 9) Direction ports
        if ("bidirectional".equals(flow.getDirection())) {
            sourcePort.setPortDirection("InOut");
            targetPort.setPortDirection("InOut");
        } else {
            sourcePort.setPortDirection("Out");
            targetPort.setPortDirection("In");
        }

        // 10) Redessiner l’arête du nouveau flow
        utils.DiagramFlowRedraw.redrawFlowOnDiagram(rhApp, diagram, flow, sourceGraphElement, targetGraphElement);
    }

    private void typePort(IRPSysMLPort sourcePort, IRPSysMLPort targetPort, IRPFlow selectedFlow) {

        List<?> conveyedList = selectedFlow.getConveyed().toList();

        if (conveyedList.isEmpty()) {
            rhpLog.debug("Flow conveyes no flow items, use UNTYPED.");
            IRPModelElement untyped = rhApp.activeProject().findElementByGUID(ProfilConstants.GUID_PREDIFINED_UNTYPED);

            sourcePort.setType((IRPClassifier) untyped);
            targetPort.setType((IRPClassifier) untyped);
            return;
        }

        if (conveyedList.size() > 1) {
            rhpLog.info("Can not type port with multiple Flow Items. Use UNTYPED.");
            IRPModelElement untyped = rhApp.activeProject().findElementByGUID(ProfilConstants.GUID_PREDIFINED_UNTYPED);

            sourcePort.setType((IRPClassifier) untyped);
            targetPort.setType((IRPClassifier) untyped);
            return;
        }

        IRPClass conveyedClass = (IRPClass) conveyedList.get(0);
        sourcePort.setType(conveyedClass);
        targetPort.setType(conveyedClass);

        // copy stereotypes (non-newTerm)
        @SuppressWarnings("unchecked")
        List<IRPStereotype> stereotypes = (List<IRPStereotype>) conveyedClass.getStereotypes()
                .toList()
                .stream()
                .filter(st -> ((IRPStereotype) st).getIsNewTerm() != 1)
                .map(st -> (IRPStereotype) st)
                .collect(Collectors.toList());

        for (IRPStereotype st : stereotypes) {
            sourcePort.addSpecificStereotype(st);
            targetPort.addSpecificStereotype(st);
            selectedFlow.addSpecificStereotype(st); // ✅ sur le bon flow
        }
    }
}