// modificationCounter = 5
//
// changelog:
// - 2026-07-17 - Implemented all 40 Safran checker business rules using the verified Rhapsody Java API.
// - 2026-07-20 - Used the exact IRPSysMLPort direction values In, Out and InOut.
// - 2026-07-20 - Used the exact Rhapsody API values for Flow Port and Flow directions.
// - 2026-07-20 - Aligned reference, sequence, lifecycle, use-case and operating-mode rules with the Safran profile.
// - 2026-07-24 - Made operational connectivity diagram-local and enforced source-oriented include/extend validation.

package com.safran.checker.service;

import com.safran.checker.core.CheckOutcome;
import com.telelogic.rhapsody.core.IRPActionBlock;
import com.telelogic.rhapsody.core.IRPClassifier;
import com.telelogic.rhapsody.core.IRPClassifierRole;
import com.telelogic.rhapsody.core.IRPConditionMark;
import com.telelogic.rhapsody.core.IRPConnector;
import com.telelogic.rhapsody.core.IRPDependency;
import com.telelogic.rhapsody.core.IRPFlow;
import com.telelogic.rhapsody.core.IRPGeneralization;
import com.telelogic.rhapsody.core.IRPMessage;
import com.telelogic.rhapsody.core.IRPModelElement;
import com.telelogic.rhapsody.core.IRPPort;
import com.telelogic.rhapsody.core.IRPRelation;
import com.telelogic.rhapsody.core.IRPState;
import com.telelogic.rhapsody.core.IRPStateVertex;
import com.telelogic.rhapsody.core.IRPStatechart;
import com.telelogic.rhapsody.core.IRPSysMLPort;
import com.telelogic.rhapsody.core.IRPTransition;
import com.telelogic.rhapsody.core.IRPUseCase;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class CheckerRuleService {
    private static final CheckerRuleService INSTANCE = new CheckerRuleService();

    private static final String PORT_DIRECTION_IN = "In";
    private static final String PORT_DIRECTION_OUT = "Out";
    private static final String PORT_DIRECTION_IN_OUT = "InOut";

    private static final String FLOW_DIRECTION_TO_END_2 = "toEnd2";
    private static final String FLOW_DIRECTION_TO_END_1 = "toEnd1";
    private static final String FLOW_DIRECTION_BIDIRECTIONAL = "bidirectional";

    private CheckerRuleService() { }
    public static CheckerRuleService getInstance() { return INSTANCE; }

    public CheckOutcome checkOperationalFlowItemTypeCoherence(IRPModelElement element) {
        return checkFlowItemTypeCoherence(element, ProfileNewTerms.OPERATIONALFLOW);
    }
    public CheckOutcome checkFunctionalFlowItemTypeCoherence(IRPModelElement element) {
        return checkFlowItemTypeCoherence(element, ProfileNewTerms.FUNCTIONALFLOW);
    }
    public CheckOutcome checkLogicalFlowItemTypeCoherence(IRPModelElement element) {
        return checkFlowItemTypeCoherence(element, ProfileNewTerms.LOGICALFLOW);
    }

    private CheckOutcome checkFlowItemTypeCoherence(IRPModelElement element, String expectedFlowTerm) {
        if (!(element instanceof IRPFlow) || !RhapsodyModelQueries.isNewTerm(element, expectedFlowTerm)) {
            return CheckOutcome.passed();
        }
        IRPFlow flow = (IRPFlow) element;
        IRPSysMLPort firstPort = RhapsodyModelQueries.end1FlowPort(flow);
        IRPSysMLPort secondPort = RhapsodyModelQueries.end2FlowPort(flow);
        if (firstPort == null || secondPort == null) {
            return CheckOutcome.passed();
        }
        IRPModelElement firstType = firstPort.getType();
        IRPModelElement secondType = secondPort.getType();
        List<IRPModelElement> conveyedItems = RhapsodyModelQueries.asModelElements(flow.getConveyed());
        List<IRPModelElement> failed = new ArrayList<>();
        if (!sameOptionalType(firstType, secondType)) {
            failed.add(firstPort);
            failed.add(secondPort);
        }
        for (IRPModelElement conveyedItem : conveyedItems) {
            if (!sameOptionalType(firstType, conveyedItem)) {
                failed.add(firstPort);
                failed.add(conveyedItem);
            }
            if (!sameOptionalType(secondType, conveyedItem)) {
                failed.add(secondPort);
                failed.add(conveyedItem);
            }
        }
        if (failed.isEmpty()) {
            return CheckOutcome.passed();
        }
        failed.add(flow);
        return CheckOutcome.failed(RhapsodyModelQueries.distinct(failed),
                "Flow Item type inconsistency on " + RhapsodyModelQueries.elementLabel(flow)
                        + ": source port type=" + typeLabel(firstType)
                        + ", target port type=" + typeLabel(secondType)
                        + ", conveyed items=" + elementLabels(conveyedItems) + '.');
    }

    private boolean sameOptionalType(IRPModelElement first, IRPModelElement second) {
        if (first == null && second == null) return true;
        return first != null && second != null && RhapsodyModelQueries.sameElement(first, second);
    }

    private String typeLabel(IRPModelElement type) { return type == null ? "<unset>" : RhapsodyModelQueries.elementLabel(type); }

    public CheckOutcome checkOperationalFlowPortStereotype(IRPModelElement element) {
        return checkFlowPortStereotype(element, ProfileNewTerms.OPERATIONALFLOW);
    }
    public CheckOutcome checkFunctionalFlowPortStereotype(IRPModelElement element) {
        return checkFlowPortStereotype(element, ProfileNewTerms.FUNCTIONALFLOW);
    }
    public CheckOutcome checkLogicalFlowPortStereotype(IRPModelElement element) {
        return checkFlowPortStereotype(element, ProfileNewTerms.LOGICALFLOW);
    }

    private CheckOutcome checkFlowPortStereotype(IRPModelElement element, String flowTerm) {
        if (!(element instanceof IRPFlow) || !RhapsodyModelQueries.isNewTerm(element, flowTerm)) {
            return CheckOutcome.passed();
        }
        IRPFlow flow = (IRPFlow) element;
        IRPSysMLPort firstPort = RhapsodyModelQueries.end1FlowPort(flow);
        IRPSysMLPort secondPort = RhapsodyModelQueries.end2FlowPort(flow);
        List<IRPModelElement> failed = new ArrayList<>();
        if (firstPort == null || !RhapsodyModelQueries.isNewTerm(firstPort, ProfileNewTerms.FLOW_PORT)) {
            if (firstPort != null) failed.add(firstPort);
        }
        if (secondPort == null || !RhapsodyModelQueries.isNewTerm(secondPort, ProfileNewTerms.FLOW_PORT)) {
            if (secondPort != null) failed.add(secondPort);
        }
        if (firstPort != null && secondPort != null && failed.isEmpty()) {
            return CheckOutcome.passed();
        }
        failed.add(flow);
        return CheckOutcome.failed(RhapsodyModelQueries.distinct(failed),
                "Every endpoint of " + RhapsodyModelQueries.elementLabel(flow)
                        + " must be a " + ProfileNewTerms.FLOW_PORT + '.');
    }

    public CheckOutcome checkOperationalFlowPortDirection(IRPModelElement element) {
        return checkFlowPortDirection(element, ProfileNewTerms.OPERATIONALFLOW);
    }
    public CheckOutcome checkFunctionalFlowPortDirection(IRPModelElement element) {
        return checkFlowPortDirection(element, ProfileNewTerms.FUNCTIONALFLOW);
    }
    public CheckOutcome checkLogicalFlowPortDirection(IRPModelElement element) {
        return checkFlowPortDirection(element, ProfileNewTerms.LOGICALFLOW);
    }

    private CheckOutcome checkFlowPortDirection(IRPModelElement element, String flowTerm) {
        if (!(element instanceof IRPFlow)
                || !RhapsodyModelQueries.isNewTerm(element, flowTerm)) {
            return CheckOutcome.passed();
        }

        IRPFlow flow = (IRPFlow) element;
        IRPSysMLPort firstPort = RhapsodyModelQueries.end1FlowPort(flow);
        IRPSysMLPort secondPort = RhapsodyModelQueries.end2FlowPort(flow);

        if (firstPort == null || secondPort == null) {
            return CheckOutcome.passed();
        }

        String firstPortDirection = firstPort.getPortDirection();
        String secondPortDirection = secondPort.getPortDirection();
        String flowDirection = safeFlowDirection(flow);

        boolean coherent;
        String expectedDirections;

        if (FLOW_DIRECTION_TO_END_2.equals(flowDirection)) {
            coherent = PORT_DIRECTION_OUT.equals(firstPortDirection)
                    && PORT_DIRECTION_IN.equals(secondPortDirection);
            expectedDirections = PORT_DIRECTION_OUT + "/"
                    + PORT_DIRECTION_IN;
        } else if (FLOW_DIRECTION_TO_END_1.equals(flowDirection)) {
            coherent = PORT_DIRECTION_IN.equals(firstPortDirection)
                    && PORT_DIRECTION_OUT.equals(secondPortDirection);
            expectedDirections = PORT_DIRECTION_IN + "/"
                    + PORT_DIRECTION_OUT;
        } else if (FLOW_DIRECTION_BIDIRECTIONAL.equals(flowDirection)) {
            coherent = PORT_DIRECTION_IN_OUT.equals(firstPortDirection)
                    && PORT_DIRECTION_IN_OUT.equals(secondPortDirection);
            expectedDirections = PORT_DIRECTION_IN_OUT + "/"
                    + PORT_DIRECTION_IN_OUT;
        } else {
            return CheckOutcome.failed(
                    List.of(flow, firstPort, secondPort),
                    "Unsupported flow direction on "
                            + RhapsodyModelQueries.elementLabel(flow)
                            + ": flowDirection='"
                            + flowDirection
                            + "'. Expected one of: "
                            + FLOW_DIRECTION_TO_END_2
                            + ", "
                            + FLOW_DIRECTION_TO_END_1
                            + ", "
                            + FLOW_DIRECTION_BIDIRECTIONAL
                            + ".");
        }

        if (coherent) {
            return CheckOutcome.passed();
        }

        return CheckOutcome.failed(
                List.of(flow, firstPort, secondPort),
                "Incoherent Flow Port directions on "
                        + RhapsodyModelQueries.elementLabel(flow)
                        + ": end1="
                        + firstPortDirection
                        + ", end2="
                        + secondPortDirection
                        + ", flowDirection="
                        + flowDirection
                        + ". Expected end1/end2 directions="
                        + expectedDirections
                        + ".");
    }

    private String safeFlowDirection(IRPFlow flow) {
        try {
            String flowDirection = flow.getDirection();
            return flowDirection == null ? "" : flowDirection;
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    public CheckOutcome checkUnclassifiedGenericFlow(IRPModelElement element) {
        if (!RhapsodyModelQueries.isNewTerm(element, ProfileNewTerms.GENERICFLOW)) {
            return CheckOutcome.passed();
        }
        if (RhapsodyModelQueries.isNewTerm(element, ProfileNewTerms.OPERATIONALFLOW)
                || RhapsodyModelQueries.isNewTerm(element, ProfileNewTerms.FUNCTIONALFLOW)
                || RhapsodyModelQueries.isNewTerm(element, ProfileNewTerms.LOGICALFLOW)
                || RhapsodyModelQueries.isNewTerm(element, ProfileNewTerms.TECHNICALFLOW)) {
            return CheckOutcome.passed();
        }
        return CheckOutcome.failed(element,
                RhapsodyModelQueries.elementLabel(element) + " remains an unclassified " + ProfileNewTerms.GENERICFLOW + '.');
    }

    /**
     * Checks operational connectivity independently in every Context Diagram
     * or Environment Diagram where the element is represented.
     *
     * <p>A connection is valid only when the represented Operational System or
     * Stakeholder is connected, in the same diagram, to a different
     * Operational System or Stakeholder by an Operational Flow.</p>
     *
     * @param element element evaluated by Rhapsody
     * @return passed when every containing operational diagram has one valid
     *         local connection
     */
    public CheckOutcome checkOperationalContextConnectivity(
            IRPModelElement element) {
        boolean applicable =
                RhapsodyModelQueries.isNewTerm(
                        element,
                        ProfileNewTerms.OPERATIONALSYSTEM)
                        || RhapsodyModelQueries.isNewTerm(
                                element,
                                ProfileNewTerms.STAKEHOLDER);

        if (!applicable) {
            return CheckOutcome.passed();
        }

        List<IRPModelElement> containingDiagrams =
                operationalContextDiagramsRepresenting(element);

        if (containingDiagrams.isEmpty()) {
            return CheckOutcome.passed();
        }

        List<IRPModelElement> diagramsWithoutValidConnection =
                new ArrayList<>();

        for (IRPModelElement diagram : containingDiagrams) {
            if (!hasValidOperationalContextConnection(
                    diagram,
                    element)) {
                diagramsWithoutValidConnection.add(diagram);
            }
        }

        if (diagramsWithoutValidConnection.isEmpty()) {
            return CheckOutcome.passed();
        }

        List<IRPModelElement> failedElements =
                new ArrayList<>(diagramsWithoutValidConnection);
        failedElements.add(element);

        return CheckOutcome.failed(
                RhapsodyModelQueries.distinct(failedElements),
                RhapsodyModelQueries.elementLabel(element)
                        + " must be connected, in every Context Diagram or "
                        + "Environment Diagram where it is represented, to a "
                        + "different Operational System or Stakeholder by an "
                        + "Operational Flow. Diagrams without a valid local "
                        + "connection: "
                        + elementLabels(diagramsWithoutValidConnection)
                        + '.');
    }

    /**
     * Returns the accepted operational diagrams that directly represent the
     * supplied element.
     */
    private List<IRPModelElement> operationalContextDiagramsRepresenting(
            IRPModelElement element) {
        List<IRPModelElement> result = new ArrayList<>();

        for (IRPModelElement candidate :
                RhapsodyModelQueries.allProjectElements(element)) {
            boolean acceptedDiagram =
                    RhapsodyModelQueries.isNewTerm(
                            candidate,
                            ProfileNewTerms.CONTEXTDIAGRAM)
                            || RhapsodyModelQueries.isNewTerm(
                                    candidate,
                                    ProfileNewTerms.ENVIRONMENTDIAGRAM);

            if (!acceptedDiagram) {
                continue;
            }

            if (containsElement(
                    RhapsodyModelQueries.diagramSemanticElements(candidate),
                    element)) {
                result.add(candidate);
            }
        }

        return RhapsodyModelQueries.distinct(result);
    }

    /**
     * Tests whether one diagram contains a valid local operational connection
     * for the supplied participant.
     */
    private boolean hasValidOperationalContextConnection(
            IRPModelElement diagram,
            IRPModelElement testedParticipant) {
        List<IRPModelElement> representedElements =
                RhapsodyModelQueries.diagramSemanticElements(diagram);

        for (IRPModelElement representedElement : representedElements) {
            if (!(representedElement instanceof IRPFlow)
                    || !RhapsodyModelQueries.isNewTerm(
                            representedElement,
                            ProfileNewTerms.OPERATIONALFLOW)) {
                continue;
            }

            IRPFlow flow = (IRPFlow) representedElement;
            IRPModelElement firstParticipant =
                    directOperationalContextParticipant(
                            RhapsodyModelQueries.end1Element(flow),
                            RhapsodyModelQueries.end1FlowPort(flow));
            IRPModelElement secondParticipant =
                    directOperationalContextParticipant(
                            RhapsodyModelQueries.end2Element(flow),
                            RhapsodyModelQueries.end2FlowPort(flow));

            if (isValidOperationalContextPair(
                    testedParticipant,
                    firstParticipant,
                    secondParticipant,
                    representedElements)) {
                return true;
            }

            if (isValidOperationalContextPair(
                    testedParticipant,
                    secondParticipant,
                    firstParticipant,
                    representedElements)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Resolves the direct owner represented by one Operational Flow end.
     * A port owned by a Function nested below an Operational System is not
     * promoted to that Operational System because the opposite endpoint must
     * itself be an Operational System or Stakeholder.
     */
    private IRPModelElement directOperationalContextParticipant(
            IRPModelElement flowEnd,
            IRPSysMLPort flowPort) {
        if (flowPort != null) {
            IRPModelElement portOwner = flowPort.getOwner();
            return isOperationalContextParticipant(portOwner)
                    ? portOwner
                    : null;
        }

        if (isOperationalContextParticipant(flowEnd)) {
            return flowEnd;
        }

        if (flowEnd instanceof IRPSysMLPort) {
            IRPModelElement portOwner = flowEnd.getOwner();
            return isOperationalContextParticipant(portOwner)
                    ? portOwner
                    : null;
        }

        return null;
    }

    private boolean isValidOperationalContextPair(
            IRPModelElement testedParticipant,
            IRPModelElement candidateTestedParticipant,
            IRPModelElement otherParticipant,
            List<IRPModelElement> representedElements) {
        return RhapsodyModelQueries.sameElement(
                        testedParticipant,
                        candidateTestedParticipant)
                && isOperationalContextParticipant(otherParticipant)
                && !RhapsodyModelQueries.sameElement(
                        testedParticipant,
                        otherParticipant)
                && containsElement(
                        representedElements,
                        otherParticipant);
    }

    private boolean isOperationalContextParticipant(
            IRPModelElement element) {
        return RhapsodyModelQueries.isNewTerm(
                        element,
                        ProfileNewTerms.OPERATIONALSYSTEM)
                || RhapsodyModelQueries.isNewTerm(
                        element,
                        ProfileNewTerms.STAKEHOLDER);
    }

    public CheckOutcome checkOperationalSequenceSystemOfInterest(IRPModelElement element) {
        if (!RhapsodyModelQueries.isNewTerm(element, ProfileNewTerms.OPERATIONALSEQUENCEDIAGRAM)) {
            return CheckOutcome.passed();
        }
        List<IRPModelElement> systemsOfInterest = new ArrayList<>();
        for (IRPModelElement represented : RhapsodyModelQueries.diagramSemanticElements(element)) {
            if (RhapsodyModelQueries.isNewTerm(represented, ProfileNewTerms.OPERATIONALSYSTEM)
                    && RhapsodyModelQueries.hasStereotypeNamed(represented, "SystemOfInterest")) {
                systemsOfInterest.add(represented);
            }
        }
        systemsOfInterest = RhapsodyModelQueries.distinct(systemsOfInterest);
        if (systemsOfInterest.size() == 1) return CheckOutcome.passed();
        return CheckOutcome.failed(element,
                "Operational Sequence Diagram " + RhapsodyModelQueries.elementLabel(element)
                        + " must contain exactly one Operational System stereotyped <<SystemOfInterest>>; found "
                        + systemsOfInterest.size() + '.');
    }

    public CheckOutcome checkOperationalSequenceFlowTyping(IRPModelElement element) {
        if (!RhapsodyModelQueries.isNewTerm(
                element,
                ProfileNewTerms.OPERATIONALSEQUENCEDIAGRAM)) {
            return CheckOutcome.passed();
        }

        List<IRPModelElement> failedElements = new ArrayList<>();
        for (IRPMessage message : RhapsodyModelQueries.sequenceMessages(element)) {
            if (!RhapsodyModelQueries.isDataFlowMessage(message)) {
                continue;
            }

            List<IRPModelElement> associatedFlowItems = new ArrayList<>();
            for (IRPDependency dependency : RhapsodyModelQueries.dependenciesFrom(message)) {
                IRPModelElement dependencyTarget = dependency.getDependsOn();
                if (RhapsodyModelQueries.isNewTerm(
                        dependencyTarget,
                        ProfileNewTerms.FLOWITEM)) {
                    associatedFlowItems.add(dependencyTarget);
                }
            }
            associatedFlowItems = RhapsodyModelQueries.distinct(associatedFlowItems);

            if (associatedFlowItems.isEmpty()) {
                failedElements.add(message);
                continue;
            }

            IRPClassifier sourceSystem = operationalSystemOfRole(message.getSource());
            IRPClassifier targetSystem = operationalSystemOfRole(message.getTarget());
            if (sourceSystem == null || targetSystem == null) {
                failedElements.add(message);
                failedElements.addAll(associatedFlowItems);
                continue;
            }

            for (IRPModelElement flowItem : associatedFlowItems) {
                if (!isConveyedByOperationalFlowBetween(
                        element,
                        flowItem,
                        sourceSystem,
                        targetSystem)) {
                    failedElements.add(message);
                    failedElements.add(flowItem);
                }
            }
        }

        if (failedElements.isEmpty()) {
            return CheckOutcome.passed();
        }
        failedElements.add(element);
        return CheckOutcome.failed(
                RhapsodyModelQueries.distinct(failedElements),
                "Every DataFlow message must have a dependency to a Flow Item, and the same Flow Item must be conveyed by an Operational Flow connecting the Operational Systems of the source and target lifelines.");
    }

    private boolean isConveyedByOperationalFlowBetween(
            IRPModelElement context,
            IRPModelElement flowItem,
            IRPClassifier sourceSystem,
            IRPClassifier targetSystem) {
        for (IRPFlow flow : RhapsodyModelQueries.flowsByNewTerm(
                context,
                ProfileNewTerms.OPERATIONALFLOW)) {
            boolean connectsSystems =
                    RhapsodyModelQueries.flowTouchesElement(flow, sourceSystem)
                            && RhapsodyModelQueries.flowTouchesElement(flow, targetSystem);
            if (!connectsSystems) {
                continue;
            }
            for (IRPModelElement conveyedItem :
                    RhapsodyModelQueries.asModelElements(flow.getConveyed())) {
                if (RhapsodyModelQueries.sameElement(conveyedItem, flowItem)) {
                    return true;
                }
            }
        }
        return false;
    }

    public CheckOutcome checkUnconnectedOperationalFlowPort(IRPModelElement element) {
        return checkUnconnectedFlowPort(element, ProfileNewTerms.OPERATIONALFLOW,
                Set.of(ProfileNewTerms.OPERATIONALSYSTEM, ProfileNewTerms.STAKEHOLDER));
    }
    public CheckOutcome checkUnconnectedFunctionalFlowPort(IRPModelElement element) {
        return checkUnconnectedFlowPort(element, ProfileNewTerms.FUNCTIONALFLOW, Set.of(ProfileNewTerms.FUNCTION));
    }
    public CheckOutcome checkUnconnectedLogicalFlowPort(IRPModelElement element) {
        return checkUnconnectedFlowPort(element, ProfileNewTerms.LOGICALFLOW, Set.of(ProfileNewTerms.LOGICALSYSTEM));
    }

    private CheckOutcome checkUnconnectedFlowPort(IRPModelElement element, String flowTerm, Set<String> acceptedOwnerTerms) {
        if (!(element instanceof IRPSysMLPort) || !RhapsodyModelQueries.isNewTerm(element, ProfileNewTerms.FLOW_PORT)) {
            return CheckOutcome.passed();
        }
        IRPModelElement owner = element.getOwner();
        boolean acceptedOwner = acceptedOwnerTerms.stream().anyMatch(term -> RhapsodyModelQueries.isNewTerm(owner, term));
        if (!acceptedOwner) return CheckOutcome.passed();
        for (IRPFlow flow : RhapsodyModelQueries.flowsByNewTerm(element, flowTerm)) {
            if (RhapsodyModelQueries.flowUsesPort(flow, (IRPSysMLPort) element)) return CheckOutcome.passed();
        }
        return CheckOutcome.failed(element,
                RhapsodyModelQueries.elementLabel(element) + " is not connected to any " + flowTerm + '.');
    }

    public CheckOutcome checkUnusedFlowItem(IRPModelElement element) {
        if (!RhapsodyModelQueries.isNewTerm(element, ProfileNewTerms.FLOWITEM)) return CheckOutcome.passed();
        for (IRPModelElement candidate : RhapsodyModelQueries.findByNewTerm(element, ProfileNewTerms.FLOW_PORT)) {
            if (candidate instanceof IRPSysMLPort
                    && RhapsodyModelQueries.sameElement(((IRPSysMLPort) candidate).getType(), element)) {
                return CheckOutcome.passed();
            }
        }
        for (String flowTerm : List.of(ProfileNewTerms.GENERICFLOW, ProfileNewTerms.OPERATIONALFLOW,
                ProfileNewTerms.FUNCTIONALFLOW, ProfileNewTerms.LOGICALFLOW, ProfileNewTerms.TECHNICALFLOW)) {
            for (IRPFlow flow : RhapsodyModelQueries.flowsByNewTerm(element, flowTerm)) {
                for (IRPModelElement conveyed : RhapsodyModelQueries.asModelElements(flow.getConveyed())) {
                    if (RhapsodyModelQueries.sameElement(conveyed, element)) return CheckOutcome.passed();
                }
            }
        }
        return CheckOutcome.failed(element, RhapsodyModelQueries.elementLabel(element) + " is an unused Flow Item.");
    }

    public CheckOutcome checkUnnestedFunction(IRPModelElement element) {
        return checkNestedConvention(element, ProfileNewTerms.FUNCTION, ProfileNewTerms.FUNCTIONALBEHAVIORDIAGRAM, "Nest Function");
    }
    public CheckOutcome checkUnnestedLogicalSystem(IRPModelElement element) {
        return checkNestedConvention(element, ProfileNewTerms.LOGICALSYSTEM, ProfileNewTerms.LOGICALARCHITECTUREDIAGRAM, "Nest Logical System");
    }
    public CheckOutcome checkUnnestedTechnicalComponent(IRPModelElement element) {
        return checkNestedConvention(element, ProfileNewTerms.TECHNICALCOMPONENT, ProfileNewTerms.TECHNICALARCHITECTUREDIAGRAM, "Nest Technical Component");
    }

    private CheckOutcome checkNestedConvention(IRPModelElement element, String elementTerm, String diagramTerm, String operationName) {
        if (!RhapsodyModelQueries.isNewTerm(element, elementTerm)) return CheckOutcome.passed();
        if (!RhapsodyModelQueries.isRepresentedInAnyDiagram(element, diagramTerm)) return CheckOutcome.passed();
        IRPModelElement owner = element.getOwner();
        boolean nestedByOwner = owner != null && RhapsodyModelQueries.isNewTerm(owner, elementTerm);
        boolean explicitMarker = RhapsodyModelQueries.hasBooleanTagOrProperty(element,
                "SafranChecker.Nested", "Nested", "IsNested");
        if (nestedByOwner || explicitMarker) return CheckOutcome.passed();
        return CheckOutcome.failed(element,
                RhapsodyModelQueries.elementLabel(element) + " is displayed in " + diagramTerm
                        + " but no result of the " + operationName
                        + " operation could be identified. The implementation accepts nesting under another "
                        + elementTerm + " or an explicit Nested/IsNested marker.");
    }

    public CheckOutcome checkFunctionWithReferenceCompatibility(IRPModelElement element) {
        return checkWithReferenceCompatibility(
                element,
                ProfileNewTerms.FUNCTIONWITHREFERENCE,
                ProfileNewTerms.REFERENCESFUNCTION,
                ProfileNewTerms.FUNCTION,
                PortComparisonMode.FLOW_PORT);
    }

    public CheckOutcome checkLogicalSystemBreakdownRepresentation(IRPModelElement element) {
        return checkRepresentation(element, ProfileNewTerms.LOGICALSYSTEM, ProfileNewTerms.LOGICALBREAKDOWNSTRUCTURE);
    }

    public CheckOutcome checkLogicalSystemWithReferenceCompatibility(IRPModelElement element) {
        return checkWithReferenceCompatibility(
                element,
                ProfileNewTerms.LOGICALSYSTEMWITHREFERENCE,
                ProfileNewTerms.REFERENCESLOGICALSYSTEM,
                ProfileNewTerms.LOGICALSYSTEM,
                PortComparisonMode.FLOW_PORT);
    }

    public CheckOutcome checkTechnicalComponentBreakdownRepresentation(IRPModelElement element) {
        return checkRepresentation(element, ProfileNewTerms.TECHNICALCOMPONENT, ProfileNewTerms.TECHNICALBREAKDOWNDIAGRAM);
    }

    public CheckOutcome checkTechnicalComponentWithReferenceCompatibility(IRPModelElement element) {
        return checkWithReferenceCompatibility(
                element,
                ProfileNewTerms.TECHNICALCOMPONENTWITHREFERENCE,
                ProfileNewTerms.REFERENCESTECHNICALCOMPONENT,
                ProfileNewTerms.TECHNICALCOMPONENT,
                PortComparisonMode.TECHNICAL_PORT);
    }

    private CheckOutcome checkWithReferenceCompatibility(
            IRPModelElement element,
            String withReferenceTerm,
            String referenceRelationTerm,
            String referencedElementTerm,
            PortComparisonMode portComparisonMode) {
        if (!(element instanceof IRPClassifier)
                || !RhapsodyModelQueries.isNewTerm(element, withReferenceTerm)) {
            return CheckOutcome.passed();
        }

        IRPClassifier withReference = (IRPClassifier) element;
        List<IRPGeneralization> referenceRelations = new ArrayList<>();
        for (IRPGeneralization relation : RhapsodyModelQueries.generalizationsByNewTerm(
                element,
                referenceRelationTerm)) {
            if (RhapsodyModelQueries.sameElement(
                    relation.getDerivedClass(),
                    withReference)) {
                referenceRelations.add(relation);
            }
        }

        if (referenceRelations.size() != 1) {
            List<IRPModelElement> failed = new ArrayList<>(referenceRelations);
            failed.add(element);
            return CheckOutcome.failed(
                    RhapsodyModelQueries.distinct(failed),
                    RhapsodyModelQueries.elementLabel(element)
                            + " must own exactly one "
                            + referenceRelationTerm
                            + "; found "
                            + referenceRelations.size()
                            + '.');
        }

        IRPGeneralization referenceRelation = referenceRelations.get(0);
        IRPClassifier referencedElement = referenceRelation.getBaseClass();
        if (referencedElement == null
                || !RhapsodyModelQueries.isNewTerm(
                        referencedElement,
                        referencedElementTerm)) {
            return CheckOutcome.failed(
                    List.of(element, referenceRelation),
                    referenceRelationTerm
                            + " must target exactly one "
                            + referencedElementTerm
                            + '.');
        }

        List<String> differences = portComparisonMode == PortComparisonMode.FLOW_PORT
                ? compareReferencedFlowPorts(referencedElement, withReference)
                : compareReferencedTechnicalPorts(referencedElement, withReference);
        if (differences.isEmpty()) {
            return CheckOutcome.passed();
        }

        return CheckOutcome.failed(
                List.of(element, referenceRelation, referencedElement),
                withReferenceTerm
                        + " incompatibility: "
                        + String.join("; ", differences));
    }

    private List<String> compareReferencedFlowPorts(
            IRPClassifier referencedElement,
            IRPClassifier withReference) {
        List<IRPSysMLPort> referencedPorts = flowPorts(referencedElement);
        List<IRPSysMLPort> availablePorts = flowPorts(withReference);
        List<IRPSysMLPort> localPorts = localFlowPorts(withReference);
        List<String> differences = new ArrayList<>();

        for (IRPSysMLPort localPort : localPorts) {
            List<IRPModelElement> redefinedElements =
                    RhapsodyModelQueries.redefinedElements(localPort);
            if (redefinedElements.size() != 1
                    || !(redefinedElements.get(0) instanceof IRPSysMLPort)
                    || !containsElement(referencedPorts, redefinedElements.get(0))) {
                differences.add(
                        "local Flow Port '"
                                + localPort.getName()
                                + "' does not redefine exactly one Flow Port of the referenced element");
                continue;
            }

            IRPSysMLPort originalPort = (IRPSysMLPort) redefinedElements.get(0);
            if (!Objects.equals(
                    originalPort.getPortDirection(),
                    localPort.getPortDirection())) {
                differences.add(
                        "direction mismatch for redefined Flow Port '"
                                + localPort.getName()
                                + "'");
            }
            if (!sameOptionalType(originalPort.getType(), localPort.getType())) {
                differences.add(
                        "Flow Item type mismatch for redefined Flow Port '"
                                + localPort.getName()
                                + "'");
            }
        }

        for (IRPSysMLPort referencedPort : referencedPorts) {
            if (!containsElement(availablePorts, referencedPort)
                    && !isRedefinedByAny(referencedPort, localPorts)) {
                differences.add(
                        "referenced Flow Port '"
                                + referencedPort.getName()
                                + "' is not available in the With Reference element");
            }
        }
        return differences;
    }

    private List<String> compareReferencedTechnicalPorts(
            IRPClassifier referencedElement,
            IRPClassifier withReference) {
        List<IRPPort> referencedPorts = technicalPorts(referencedElement);
        List<IRPPort> availablePorts = technicalPorts(withReference);
        List<IRPPort> localPorts = localTechnicalPorts(withReference);
        List<String> differences = new ArrayList<>();

        for (IRPPort localPort : localPorts) {
            List<IRPModelElement> redefinedElements =
                    RhapsodyModelQueries.redefinedElements(localPort);
            if (redefinedElements.size() != 1
                    || !(redefinedElements.get(0) instanceof IRPPort)
                    || !containsElement(referencedPorts, redefinedElements.get(0))) {
                differences.add(
                        "local Technical Port '"
                                + localPort.getName()
                                + "' does not redefine exactly one Technical Port of the referenced component");
                continue;
            }

            IRPPort originalPort = (IRPPort) redefinedElements.get(0);
            if (!sameOptionalType(
                    originalPort.getContract(),
                    localPort.getContract())) {
                differences.add(
                        "port contract mismatch for redefined Technical Port '"
                                + localPort.getName()
                                + "'");
            }
        }

        for (IRPPort referencedPort : referencedPorts) {
            if (!containsElement(availablePorts, referencedPort)
                    && !isRedefinedByAny(referencedPort, localPorts)) {
                differences.add(
                        "referenced Technical Port '"
                                + referencedPort.getName()
                                + "' is not available in the With Reference component");
            }
        }
        return differences;
    }

    private List<IRPSysMLPort> flowPorts(IRPClassifier classifier) {
        List<IRPSysMLPort> result = new ArrayList<>();
        for (IRPModelElement port : RhapsodyModelQueries.classifierPorts(classifier)) {
            if (port instanceof IRPSysMLPort
                    && RhapsodyModelQueries.isNewTerm(
                            port,
                            ProfileNewTerms.FLOW_PORT)) {
                result.add((IRPSysMLPort) port);
            }
        }
        return result;
    }

    private List<IRPSysMLPort> localFlowPorts(IRPClassifier classifier) {
        List<IRPSysMLPort> result = new ArrayList<>();
        for (IRPModelElement port : RhapsodyModelQueries.localClassifierPorts(classifier)) {
            if (port instanceof IRPSysMLPort
                    && RhapsodyModelQueries.isNewTerm(
                            port,
                            ProfileNewTerms.FLOW_PORT)) {
                result.add((IRPSysMLPort) port);
            }
        }
        return result;
    }

    private List<IRPPort> technicalPorts(IRPClassifier classifier) {
        List<IRPPort> result = new ArrayList<>();
        for (IRPModelElement port : RhapsodyModelQueries.classifierPorts(classifier)) {
            if (port instanceof IRPPort
                    && RhapsodyModelQueries.isNewTerm(
                            port,
                            ProfileNewTerms.TECHNICALPORT)) {
                result.add((IRPPort) port);
            }
        }
        return result;
    }

    private List<IRPPort> localTechnicalPorts(IRPClassifier classifier) {
        List<IRPPort> result = new ArrayList<>();
        for (IRPModelElement port : RhapsodyModelQueries.localClassifierPorts(classifier)) {
            if (port instanceof IRPPort
                    && RhapsodyModelQueries.isNewTerm(
                            port,
                            ProfileNewTerms.TECHNICALPORT)) {
                result.add((IRPPort) port);
            }
        }
        return result;
    }

    private boolean containsElement(
            List<? extends IRPModelElement> elements,
            IRPModelElement expected) {
        for (IRPModelElement element : elements) {
            if (RhapsodyModelQueries.sameElement(element, expected)) {
                return true;
            }
        }
        return false;
    }

    private boolean isRedefinedByAny(
            IRPModelElement original,
            List<? extends IRPModelElement> localPorts) {
        for (IRPModelElement localPort : localPorts) {
            for (IRPModelElement redefined :
                    RhapsodyModelQueries.redefinedElements(localPort)) {
                if (RhapsodyModelQueries.sameElement(redefined, original)) {
                    return true;
                }
            }
        }
        return false;
    }

    public CheckOutcome checkUseCaseDiagramRepresentation(IRPModelElement element) {
        if (!RhapsodyModelQueries.isNewTerm(element, ProfileNewTerms.USE_CASE)) {
            return CheckOutcome.passed();
        }
        for (IRPModelElement candidate : RhapsodyModelQueries.allProjectElements(element)) {
            if (!RhapsodyModelQueries.isNewTerm(
                    candidate,
                    ProfileNewTerms.USE_CASEDIAGRAM)) {
                continue;
            }
            IRPModelElement diagramOwner = candidate.getOwner();
            if (!RhapsodyModelQueries.isNewTerm(
                    diagramOwner,
                    ProfileNewTerms.LIFECYCLEPHASE)) {
                continue;
            }
            for (IRPModelElement represented :
                    RhapsodyModelQueries.diagramSemanticElements(candidate)) {
                if (RhapsodyModelQueries.sameElement(represented, element)) {
                    return CheckOutcome.passed();
                }
            }
        }
        return CheckOutcome.failed(
                element,
                RhapsodyModelQueries.elementLabel(element)
                        + " is not represented in a Use_Case Diagram contained directly in a Lifecycle Phase.");
    }

    /**
     * Checks only outgoing include and extend dependencies whose source is the
     * evaluated Use_Case.
     *
     * <p>The Rhapsody API defines {@code IRPDependency.getDependent()} as the
     * source and {@code getDependsOn()} as the target. A Use_Case that is only
     * the target of a dependency created by another element must therefore not
     * be reported by this checker.</p>
     *
     * @param element Use_Case evaluated by Rhapsody
     * @return passed when every outgoing include or extend targets a Use_Case
     */
    public CheckOutcome checkUseCaseIncludeExtendTarget(
            IRPModelElement element) {
        if (!(element instanceof IRPUseCase)
                || !RhapsodyModelQueries.isNewTerm(
                        element,
                        ProfileNewTerms.USE_CASE)) {
            return CheckOutcome.passed();
        }

        List<IRPModelElement> invalidDependencies =
                new ArrayList<>();

        for (IRPModelElement candidate :
                RhapsodyModelQueries.allProjectElements(element)) {
            if (!(candidate instanceof IRPDependency)
                    || !RhapsodyModelQueries.relationLooksLikeIncludeOrExtend(
                            candidate)) {
                continue;
            }

            IRPDependency dependency = (IRPDependency) candidate;

            if (!RhapsodyModelQueries.sameElement(
                    dependency.getDependent(),
                    element)) {
                continue;
            }

            IRPModelElement dependencyTarget =
                    dependency.getDependsOn();

            if (!RhapsodyModelQueries.isNewTerm(
                    dependencyTarget,
                    ProfileNewTerms.USE_CASE)) {
                invalidDependencies.add(dependency);
            }
        }

        if (invalidDependencies.isEmpty()) {
            return CheckOutcome.passed();
        }

        invalidDependencies.add(element);
        return CheckOutcome.failed(
                RhapsodyModelQueries.distinct(invalidDependencies),
                "Every outgoing UML include or extend dependency from a "
                        + "Use_Case must target another Use_Case.");
    }

    public CheckOutcome checkUseCaseSatisfaction(IRPModelElement element) {
        if (!RhapsodyModelQueries.isNewTerm(element, ProfileNewTerms.USE_CASE)) return CheckOutcome.passed();
        for (IRPDependency dependency : RhapsodyModelQueries.dependenciesByNewTerm(element, ProfileNewTerms.SATISFIES)) {
            IRPModelElement other = RhapsodyModelQueries.otherDependencyEnd(dependency, element);
            if (other != null && RhapsodyModelQueries.isNewTerm(other, ProfileNewTerms.FUNCTION)) return CheckOutcome.passed();
        }
        return CheckOutcome.failed(element, RhapsodyModelQueries.elementLabel(element) + " is not satisfied by any Function.");
    }

    public CheckOutcome checkUseCaseExternalActorAssociation(IRPModelElement element) {
        if (!(element instanceof IRPClassifier)
                || !RhapsodyModelQueries.isNewTerm(
                        element,
                        ProfileNewTerms.USE_CASE)) {
            return CheckOutcome.passed();
        }

        for (IRPModelElement relationElement : RhapsodyModelQueries.asModelElements(
                ((IRPClassifier) element).getRelations())) {
            if (!(relationElement instanceof IRPRelation)
                    || !isAcceptedUseCaseAssociation((IRPRelation) relationElement)) {
                continue;
            }
            IRPClassifier other = ((IRPRelation) relationElement).getOtherClass();
            if (RhapsodyModelQueries.isNewTerm(
                    other,
                    ProfileNewTerms.STAKEHOLDER)
                    || RhapsodyModelQueries.isNewTerm(
                            other,
                            ProfileNewTerms.OPERATIONALSYSTEM)) {
                return CheckOutcome.passed();
            }
        }

        return CheckOutcome.failed(
                element,
                RhapsodyModelQueries.elementLabel(element)
                        + " is not connected to a Stakeholder or Operational System by a Directed Association or standard UML Association.");
    }

    private boolean isAcceptedUseCaseAssociation(IRPRelation relation) {
        if (RhapsodyModelQueries.isNewTerm(
                relation,
                ProfileNewTerms.DIRECTED_ASSOCIATION)) {
            return true;
        }
        String relationType = "";
        try {
            relationType = relation.getRelationType();
        } catch (RuntimeException ignored) { }
        String metaClass = "";
        try {
            metaClass = relation.getMetaClass();
        } catch (RuntimeException ignored) { }
        String normalizedRelationType = RhapsodyModelQueries.normalize(relationType)
                .replace(" ", "");
        String normalizedMetaClass = RhapsodyModelQueries.normalize(metaClass)
                .replace(" ", "");
        return "association".equals(normalizedRelationType)
                || "association".equals(normalizedMetaClass)
                || "directedassociation".equals(normalizedRelationType)
                || "directedassociation".equals(normalizedMetaClass);
    }

    public CheckOutcome checkOperationalSequenceFlowPresence(IRPModelElement element) {
        if (!RhapsodyModelQueries.isNewTerm(
                element,
                ProfileNewTerms.OPERATIONALSEQUENCEDIAGRAM)) {
            return CheckOutcome.passed();
        }

        List<IRPModelElement> representedSystems = new ArrayList<>();
        for (IRPModelElement represented :
                RhapsodyModelQueries.diagramSemanticElements(element)) {
            if (RhapsodyModelQueries.isNewTerm(
                    represented,
                    ProfileNewTerms.OPERATIONALSYSTEM)) {
                representedSystems.add(represented);
            }
        }
        representedSystems = RhapsodyModelQueries.distinct(representedSystems);
        if (representedSystems.size() < 2) {
            return CheckOutcome.passed();
        }

        for (IRPMessage message : RhapsodyModelQueries.sequenceMessages(element)) {
            if (!RhapsodyModelQueries.isDataFlowMessage(message)) {
                continue;
            }
            IRPClassifier sourceSystem = operationalSystemOfRole(message.getSource());
            IRPClassifier targetSystem = operationalSystemOfRole(message.getTarget());
            if (sourceSystem != null
                    && targetSystem != null
                    && !RhapsodyModelQueries.sameElement(sourceSystem, targetSystem)) {
                return CheckOutcome.passed();
            }
        }

        return CheckOutcome.failed(
                element,
                RhapsodyModelQueries.elementLabel(element)
                        + " contains at least two represented Operational Systems but no DataFlow exchange between their lifelines.");
    }

    public CheckOutcome checkOperationalSequenceConditionMark(IRPModelElement element) {
        if (!RhapsodyModelQueries.isNewTerm(
                element,
                ProfileNewTerms.OPERATIONALSEQUENCEDIAGRAM)) {
            return CheckOutcome.passed();
        }

        List<IRPModelElement> systemsOfInterest = new ArrayList<>();
        for (IRPModelElement represented :
                RhapsodyModelQueries.diagramSemanticElements(element)) {
            if (RhapsodyModelQueries.isNewTerm(
                    represented,
                    ProfileNewTerms.OPERATIONALSYSTEM)
                    && RhapsodyModelQueries.hasStereotypeNamed(
                            represented,
                            "SystemOfInterest")) {
                systemsOfInterest.add(represented);
            }
        }
        systemsOfInterest = RhapsodyModelQueries.distinct(systemsOfInterest);

        List<IRPMessage> conditionMarks = new ArrayList<>();
        for (IRPMessage message : RhapsodyModelQueries.sequenceMessages(element)) {
            if (RhapsodyModelQueries.isConditionMark(message)) {
                conditionMarks.add(message);
            }
        }
        if (conditionMarks.isEmpty()) {
            return CheckOutcome.passed();
        }
        if (systemsOfInterest.size() != 1) {
            return CheckOutcome.failed(
                    element,
                    "Condition Marks cannot be validated because the Operational Sequence Diagram does not contain exactly one Operational System stereotyped <<SystemOfInterest>>.");
        }

        IRPModelElement systemOfInterest = systemsOfInterest.get(0);
        List<IRPModelElement> invalidMarks = new ArrayList<>();
        for (IRPMessage conditionMark : conditionMarks) {
            IRPModelElement operatingMode = conditionMark.getFormalType();
            if (!RhapsodyModelQueries.isNewTerm(
                    operatingMode,
                    ProfileNewTerms.OPERATINGMODE)
                    || !RhapsodyModelQueries.ownerChainContains(
                            operatingMode,
                            systemOfInterest)) {
                invalidMarks.add(conditionMark);
            }
        }

        if (invalidMarks.isEmpty()) {
            return CheckOutcome.passed();
        }
        invalidMarks.add(element);
        return CheckOutcome.failed(
                RhapsodyModelQueries.distinct(invalidMarks),
                "Every Condition Mark must use getFormalType() to reference an Operating Mode owned by the Operational System stereotyped <<SystemOfInterest>>.");
    }

    public CheckOutcome checkOperationalSequenceActionBlocks(IRPModelElement element) {
        if (!RhapsodyModelQueries.isNewTerm(
                element,
                ProfileNewTerms.OPERATIONALSEQUENCEDIAGRAM)) {
            return CheckOutcome.passed();
        }

        List<IRPMessage> messages = new ArrayList<>(
                RhapsodyModelQueries.sequenceMessages(element));
        messages.sort(RhapsodyModelQueries::compareSequenceNumbers);
        List<IRPModelElement> failedElements = new ArrayList<>();

        for (IRPMessage message : messages) {
            if (!RhapsodyModelQueries.isActionBlock(message)) {
                continue;
            }
            IRPModelElement referencedElement = message.getFormalType();
            if (!RhapsodyModelQueries.isNewTerm(
                    referencedElement,
                    ProfileNewTerms.FUNCTION)) {
                continue;
            }

            IRPClassifierRole actionLifeline =
                    RhapsodyModelQueries.messageLifeline(message);
            IRPClassifier lifelineSystem = operationalSystemOfRole(actionLifeline);
            if (lifelineSystem == null
                    || !RhapsodyModelQueries.ownerChainContains(
                            referencedElement,
                            lifelineSystem)) {
                failedElements.add(message);
                failedElements.add(referencedElement);
                continue;
            }

            IRPMessage precedingConditionMark = lastPrecedingConditionMark(
                    messages,
                    message,
                    actionLifeline);
            if (precedingConditionMark == null) {
                continue;
            }

            IRPModelElement currentMode = precedingConditionMark.getFormalType();
            if (!RhapsodyModelQueries.isNewTerm(
                    currentMode,
                    ProfileNewTerms.OPERATINGMODE)) {
                continue;
            }
            if (!hasActivatedFunctionDependency(currentMode, referencedElement)) {
                failedElements.add(message);
                failedElements.add(precedingConditionMark);
                failedElements.add(currentMode);
                failedElements.add(referencedElement);
            }
        }

        if (failedElements.isEmpty()) {
            return CheckOutcome.passed();
        }
        failedElements.add(element);
        return CheckOutcome.failed(
                RhapsodyModelQueries.distinct(failedElements),
                "Every Action Block that references a Function must use a Function owned by its lifeline Operational System. When a preceding Condition Mark defines a current Operating Mode, an Activated Function dependency from that mode to the Function must also exist.");
    }

    private IRPMessage lastPrecedingConditionMark(
            List<IRPMessage> orderedMessages,
            IRPMessage actionBlock,
            IRPClassifierRole actionLifeline) {
        IRPMessage lastConditionMark = null;
        for (IRPMessage candidate : orderedMessages) {
            if (RhapsodyModelQueries.compareSequenceNumbers(
                    candidate,
                    actionBlock) >= 0) {
                break;
            }
            if (!RhapsodyModelQueries.isConditionMark(candidate)) {
                continue;
            }
            IRPClassifierRole conditionLifeline =
                    RhapsodyModelQueries.messageLifeline(candidate);
            if (RhapsodyModelQueries.sameElement(
                    conditionLifeline,
                    actionLifeline)) {
                lastConditionMark = candidate;
            }
        }
        return lastConditionMark;
    }

    private boolean hasActivatedFunctionDependency(
            IRPModelElement operatingMode,
            IRPModelElement function) {
        for (IRPDependency dependency : RhapsodyModelQueries.dependenciesByNewTerm(
                operatingMode,
                ProfileNewTerms.ACTIVATEDFUNCTION)) {
            if (RhapsodyModelQueries.sameElement(
                    dependency.getDependent(),
                    operatingMode)
                    && RhapsodyModelQueries.sameElement(
                            dependency.getDependsOn(),
                            function)) {
                return true;
            }
        }
        return false;
    }

    private IRPClassifier operationalSystemOfRole(IRPClassifierRole role) {
        IRPClassifier formalClassifier = RhapsodyModelQueries.formalClassifier(role);
        if (formalClassifier != null
                && RhapsodyModelQueries.isNewTerm(
                        formalClassifier,
                        ProfileNewTerms.OPERATIONALSYSTEM)) {
            return formalClassifier;
        }
        return null;
    }

    public CheckOutcome checkLifecyclePhaseRepresentation(IRPModelElement element) {
        return checkRepresentation(element, ProfileNewTerms.LIFECYCLEPHASE, ProfileNewTerms.LIFECYCLEDIAGRAM);
    }

    public CheckOutcome checkLifecyclePhaseConnectivity(IRPModelElement element) {
        if (!RhapsodyModelQueries.isNewTerm(
                element,
                ProfileNewTerms.LIFECYCLEPHASE)) {
            return CheckOutcome.passed();
        }
        for (IRPDependency transition : RhapsodyModelQueries.dependenciesByNewTerm(
                element,
                ProfileNewTerms.LIFECYCLEPHASETRANSITION)) {
            IRPModelElement other =
                    RhapsodyModelQueries.otherDependencyEnd(transition, element);
            if (RhapsodyModelQueries.isNewTerm(
                    other,
                    ProfileNewTerms.STARTPHASE)
                    || RhapsodyModelQueries.isNewTerm(
                            other,
                            ProfileNewTerms.LIFECYCLEPHASE)
                    || RhapsodyModelQueries.isNewTerm(
                            other,
                            ProfileNewTerms.ENDPHASE)) {
                return CheckOutcome.passed();
            }
        }
        return CheckOutcome.failed(
                element,
                RhapsodyModelQueries.elementLabel(element)
                        + " is not connected by a Lifecycle Phase Transition to a Start Phase, Lifecycle Phase or End Phase.");
    }

    public CheckOutcome checkOperatingModeRepresentation(IRPModelElement element) {
        return checkRepresentation(element, ProfileNewTerms.OPERATINGMODE, ProfileNewTerms.OPERATINGMODECHART);
    }

    public CheckOutcome checkOperatingModeTransitions(IRPModelElement element) {
        if (!(element instanceof IRPStateVertex) || !RhapsodyModelQueries.isNewTerm(element, ProfileNewTerms.OPERATINGMODE)) return CheckOutcome.passed();
        IRPStateVertex state = (IRPStateVertex) element;
        int incoming = state.getInTransitions() == null ? 0 : state.getInTransitions().getCount();
        int outgoing = state.getOutTransitions() == null ? 0 : state.getOutTransitions().getCount();
        if (incoming > 0 && outgoing > 0) return CheckOutcome.passed();
        return CheckOutcome.failed(element,
                RhapsodyModelQueries.elementLabel(element) + " must have at least one incoming and one outgoing transition; incoming="
                        + incoming + ", outgoing=" + outgoing + '.');
    }

    public CheckOutcome checkOperatingModeChartInitialState(IRPModelElement element) {
        if (!(element instanceof IRPStatechart) || !RhapsodyModelQueries.isNewTerm(element, ProfileNewTerms.OPERATINGMODECHART)) return CheckOutcome.passed();
        for (IRPModelElement chartElement : RhapsodyModelQueries.diagramElements(element)) {
            if (chartElement instanceof IRPTransition && ((IRPTransition) chartElement).isDefaultTransition() == 1) return CheckOutcome.passed();
            if (chartElement instanceof IRPConnector) {
                String connectorType = ((IRPConnector) chartElement).getConnectorType();
                String normalized = RhapsodyModelQueries.normalize(connectorType);
                if (normalized.contains("initial") || normalized.contains("default")) return CheckOutcome.passed();
            }
        }
        return CheckOutcome.failed(element,
                RhapsodyModelQueries.elementLabel(element) + " has neither an initial pseudostate nor a default transition.");
    }

    public CheckOutcome checkOperatingModeChartFinalState(IRPModelElement element) {
        if (!(element instanceof IRPStatechart)
                || !RhapsodyModelQueries.isNewTerm(
                        element,
                        ProfileNewTerms.OPERATINGMODECHART)) {
            return CheckOutcome.passed();
        }
        for (IRPModelElement chartElement :
                RhapsodyModelQueries.diagramElements(element)) {
            if (chartElement instanceof IRPConnector
                    && ((IRPConnector) chartElement).isTerminationConnector() == 1) {
                return CheckOutcome.passed();
            }
            String metaClass = "";
            try {
                metaClass = chartElement.getMetaClass();
            } catch (RuntimeException ignored) { }
            if ("terminationstate".equals(
                    RhapsodyModelQueries.normalize(metaClass).replace(" ", ""))) {
                return CheckOutcome.passed();
            }
            if (chartElement instanceof IRPState) {
                String stateType = ((IRPState) chartElement).getStateType();
                if ("terminationstate".equals(
                        RhapsodyModelQueries.normalize(stateType).replace(" ", ""))) {
                    return CheckOutcome.passed();
                }
            }
        }
        return CheckOutcome.failed(
                element,
                RhapsodyModelQueries.elementLabel(element)
                        + " does not contain a TerminationState.");
    }

    public CheckOutcome checkOperationalSystemDiagramRepresentation(IRPModelElement element) {
        return checkRepresentation(element, ProfileNewTerms.OPERATIONALSYSTEM,
                ProfileNewTerms.CONTEXTDIAGRAM, ProfileNewTerms.ENVIRONMENTDIAGRAM);
    }

    public CheckOutcome checkConnectedFlowPortNames(IRPModelElement element) {
        if (!(element instanceof IRPFlow)) return CheckOutcome.passed();
        boolean supported = RhapsodyModelQueries.isNewTerm(element, ProfileNewTerms.OPERATIONALFLOW)
                || RhapsodyModelQueries.isNewTerm(element, ProfileNewTerms.FUNCTIONALFLOW)
                || RhapsodyModelQueries.isNewTerm(element, ProfileNewTerms.LOGICALFLOW)
                || RhapsodyModelQueries.isNewTerm(element, ProfileNewTerms.TECHNICALFLOW);
        if (!supported) return CheckOutcome.passed();
        IRPFlow flow = (IRPFlow) element;
        IRPSysMLPort firstPort = RhapsodyModelQueries.end1FlowPort(flow);
        IRPSysMLPort secondPort = RhapsodyModelQueries.end2FlowPort(flow);
        if (firstPort == null || secondPort == null) return CheckOutcome.passed();
        String firstName = firstPort.getName() == null ? "" : firstPort.getName();
        String secondName = secondPort.getName() == null ? "" : secondPort.getName();
        if (firstName.equals(secondName)) return CheckOutcome.passed();
        return CheckOutcome.failed(List.of(flow, firstPort, secondPort),
                "Connected Flow Port names differ: '" + firstName + "' versus '" + secondName + "'.");
    }

    private CheckOutcome checkRepresentation(IRPModelElement element, String elementTerm, String... diagramTerms) {
        if (!RhapsodyModelQueries.isNewTerm(element, elementTerm)) return CheckOutcome.passed();
        if (RhapsodyModelQueries.isRepresentedInAnyDiagram(element, diagramTerms)) return CheckOutcome.passed();
        return CheckOutcome.failed(element,
                RhapsodyModelQueries.elementLabel(element) + " is not represented in any accepted diagram: "
                        + String.join(", ", diagramTerms) + '.');
    }

    private enum PortComparisonMode {
        FLOW_PORT,
        TECHNICAL_PORT
    }

    private String elementLabels(List<? extends IRPModelElement> elements) {
        if (elements.isEmpty()) return "<none>";
        List<String> labels = new ArrayList<>();
        for (IRPModelElement element : elements) labels.add(RhapsodyModelQueries.elementLabel(element));
        return String.join(", ", labels);
    }
}
