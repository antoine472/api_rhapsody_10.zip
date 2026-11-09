// modificationCounter = 3
//
// changelog:
// - 2026-07-17 - Created common Rhapsody model traversal and identity helpers.
// - 2026-07-20 - Added exact sequence-message, local-port and reference-query helpers.
// - 2026-07-24 - Added direct diagram representation, hierarchy, exclusivity and controlled structural diagnostics.

package com.safran.checker.service;

import com.safran.checker.core.SafranCheckerRuntime;
import com.telelogic.rhapsody.core.IRPActionBlock;
import com.telelogic.rhapsody.core.IRPClassifier;
import com.telelogic.rhapsody.core.IRPClassifierRole;
import com.telelogic.rhapsody.core.IRPCollaboration;
import com.telelogic.rhapsody.core.IRPCollection;
import com.telelogic.rhapsody.core.IRPConditionMark;
import com.telelogic.rhapsody.core.IRPDependency;
import com.telelogic.rhapsody.core.IRPDiagram;
import com.telelogic.rhapsody.core.IRPFlow;
import com.telelogic.rhapsody.core.IRPGeneralization;
import com.telelogic.rhapsody.core.IRPGraphElement;
import com.telelogic.rhapsody.core.IRPMessage;
import com.telelogic.rhapsody.core.IRPModelElement;
import com.telelogic.rhapsody.core.IRPProject;
import com.telelogic.rhapsody.core.IRPRelation;
import com.telelogic.rhapsody.core.IRPSequenceDiagram;
import com.telelogic.rhapsody.core.IRPStatechart;
import com.telelogic.rhapsody.core.IRPStereotype;
import com.telelogic.rhapsody.core.IRPSysMLPort;
import com.telelogic.rhapsody.core.IRPTag;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Provides reusable, defensive accessors for traversing and comparing
 * Rhapsody model elements used by the Safran checker business rules.
 */
public final class RhapsodyModelQueries {
    private static final Set<String> REPORTED_STRUCTURAL_FAILURES =
            Collections.synchronizedSet(new LinkedHashSet<>());

    private RhapsodyModelQueries() { }

    public static List<IRPModelElement> asModelElements(IRPCollection collection) {
        if (collection == null) {
            return List.of();
        }
        List<IRPModelElement> elements = new ArrayList<>();
        for (Object item : collection.toList()) {
            IRPModelElement modelElement = unwrapModelElement(item);
            if (modelElement != null) {
                elements.add(modelElement);
            }
        }
        return elements;
    }

    public static IRPModelElement unwrapModelElement(Object item) {
        if (item instanceof IRPGraphElement) {
            return ((IRPGraphElement) item).getModelObject();
        }
        return item instanceof IRPModelElement ? (IRPModelElement) item : null;
    }

    public static List<IRPModelElement> allProjectElements(IRPModelElement context) {
        if (context == null) {
            return List.of();
        }
        IRPProject project = context.getProject();
        if (project == null) {
            return List.of(context);
        }
        List<IRPModelElement> elements = new ArrayList<>();
        elements.add(project);
        elements.addAll(asModelElements(project.getNestedElementsRecursive()));
        return distinct(elements);
    }

    public static List<IRPModelElement> distinct(List<? extends IRPModelElement> elements) {
        Map<String, IRPModelElement> byIdentity = new LinkedHashMap<>();
        for (IRPModelElement element : elements) {
            if (element != null) {
                byIdentity.putIfAbsent(identity(element), element);
            }
        }
        return new ArrayList<>(byIdentity.values());
    }

    public static String identity(IRPModelElement element) {
        if (element == null) {
            return "<null>";
        }
        try {
            String guid = element.getGUID();
            if (guid != null && !guid.isBlank()) {
                return guid;
            }
        } catch (RuntimeException ignored) { }
        return element.getClass().getName() + '@' + System.identityHashCode(element);
    }

    public static boolean sameElement(IRPModelElement first, IRPModelElement second) {
        return first != null && second != null && identity(first).equals(identity(second));
    }

    public static boolean isNewTerm(IRPModelElement element, String expectedNewTerm) {
        if (element == null || expectedNewTerm == null || expectedNewTerm.isBlank()) {
            return false;
        }
        String expected = canonical(expectedNewTerm);
        try {
            if (expected.equals(canonical(element.getUserDefinedMetaClass()))) {
                return true;
            }
        } catch (RuntimeException ignored) { }
        try {
            IRPStereotype newTermStereotype = element.getNewTermStereotype();
            if (newTermStereotype != null && expected.equals(canonical(newTermStereotype.getName()))) {
                return true;
            }
        } catch (RuntimeException ignored) { }
        try {
            for (IRPModelElement stereotype : asModelElements(element.getStereotypes())) {
                if (expected.equals(canonical(stereotype.getName()))) {
                    return true;
                }
            }
        } catch (RuntimeException ignored) { }
        return expected.equals(canonical(element.getMetaClass()));
    }

    public static boolean hasStereotypeNamed(IRPModelElement element, String stereotypeName) {
        if (element == null || stereotypeName == null) {
            return false;
        }
        String expected = canonical(stereotypeName);
        try {
            for (IRPModelElement stereotype : asModelElements(element.getStereotypes())) {
                if (expected.equals(canonical(stereotype.getName()))) {
                    return true;
                }
            }
        } catch (RuntimeException ignored) { }
        return false;
    }

    public static String normalize(String value) {
        return value == null ? "" : value.replace('_', ' ').replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    private static String canonical(String value) {
        return normalize(value).replace(" ", "");
    }

    public static List<IRPModelElement> findByNewTerm(IRPModelElement context, String newTerm) {
        List<IRPModelElement> result = new ArrayList<>();
        for (IRPModelElement element : allProjectElements(context)) {
            if (isNewTerm(element, newTerm)) {
                result.add(element);
            }
        }
        return result;
    }

    /**
     * Returns the semantic model elements explicitly present in a diagram.
     *
     * <p>This method does not infer represented classifiers from lifelines and
     * does not add the diagram owner. It therefore remains suitable for
     * representation checkers such as Logical System breakdown coverage.</p>
     */
    public static List<IRPModelElement> diagramElements(IRPModelElement diagramElement) {
        try {
            if (diagramElement instanceof IRPDiagram) {
                return asModelElements(((IRPDiagram) diagramElement).getElementsInDiagram());
            }
            if (diagramElement instanceof IRPStatechart) {
                return asModelElements(((IRPStatechart) diagramElement).getElementsInDiagram());
            }
        } catch (RuntimeException exception) {
            reportStructuralApiFailure(
                    "read diagram elements",
                    diagramElement,
                    exception);
        }
        return List.of();
    }

    public static List<IRPModelElement> diagramSemanticElements(IRPModelElement diagramElement) {
        List<IRPModelElement> semanticElements = new ArrayList<>();
        for (IRPModelElement diagramItem : diagramElements(diagramElement)) {
            semanticElements.add(diagramItem);
            if (diagramItem instanceof IRPClassifierRole) {
                IRPClassifier formalClassifier = ((IRPClassifierRole) diagramItem).getFormalClassifier();
                if (formalClassifier != null) {
                    semanticElements.add(formalClassifier);
                }
            }
            if (diagramItem instanceof IRPMessage) {
                IRPModelElement formalType = ((IRPMessage) diagramItem).getFormalType();
                if (formalType != null) {
                    semanticElements.add(formalType);
                }
            }
        }
        return distinct(semanticElements);
    }

    public static boolean isRepresentedInAnyDiagram(IRPModelElement element, String... diagramNewTerms) {
        if (element == null) {
            return false;
        }
        Set<String> acceptedDiagrams = new LinkedHashSet<>();
        Collections.addAll(acceptedDiagrams, diagramNewTerms);
        for (IRPModelElement candidate : allProjectElements(element)) {
            boolean accepted = acceptedDiagrams.stream().anyMatch(term -> isNewTerm(candidate, term));
            if (!accepted) {
                continue;
            }
            for (IRPModelElement represented : diagramSemanticElements(candidate)) {
                if (sameElement(element, represented)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static List<IRPFlow> flowsByNewTerm(IRPModelElement context, String flowNewTerm) {
        List<IRPFlow> flows = new ArrayList<>();
        for (IRPModelElement element : findByNewTerm(context, flowNewTerm)) {
            if (element instanceof IRPFlow) {
                flows.add((IRPFlow) element);
            }
        }
        return flows;
    }

    public static IRPSysMLPort end1FlowPort(IRPFlow flow) {
        try {
            return flow.getEnd1SysMLPort();
        } catch (RuntimeException exception) {
            reportStructuralApiFailure("read Flow end1 SysML port", flow, exception);
            return null;
        }
    }

    public static IRPSysMLPort end2FlowPort(IRPFlow flow) {
        try {
            return flow.getEnd2SysMLPort();
        } catch (RuntimeException exception) {
            reportStructuralApiFailure("read Flow end2 SysML port", flow, exception);
            return null;
        }
    }

    public static IRPModelElement end1Element(IRPFlow flow) {
        try {
            return flow.getEnd1();
        } catch (RuntimeException exception) {
            reportStructuralApiFailure("read Flow end1 element", flow, exception);
            return null;
        }
    }

    public static IRPModelElement end2Element(IRPFlow flow) {
        try {
            return flow.getEnd2();
        } catch (RuntimeException exception) {
            reportStructuralApiFailure("read Flow end2 element", flow, exception);
            return null;
        }
    }

    public static boolean flowUsesPort(IRPFlow flow, IRPSysMLPort port) {
        return sameElement(end1FlowPort(flow), port) || sameElement(end2FlowPort(flow), port);
    }

    public static boolean flowTouchesElement(IRPFlow flow, IRPModelElement element) {
        if (sameElement(end1Element(flow), element) || sameElement(end2Element(flow), element)) {
            return true;
        }
        IRPSysMLPort firstPort = end1FlowPort(flow);
        IRPSysMLPort secondPort = end2FlowPort(flow);
        return ownerChainContains(firstPort, element) || ownerChainContains(secondPort, element);
    }

    public static boolean ownerChainContains(IRPModelElement element, IRPModelElement expectedOwner) {
        if (element == null || expectedOwner == null) {
            return false;
        }
        IRPModelElement current = element;
        Set<String> visited = new LinkedHashSet<>();
        while (current != null && visited.add(identity(current))) {
            if (sameElement(current, expectedOwner)) {
                return true;
            }
            current = safeOwner(current);
        }
        return false;
    }

    public static IRPModelElement firstOwnerWithNewTerm(IRPModelElement element, String newTerm) {
        IRPModelElement current = element;
        Set<String> visited = new LinkedHashSet<>();
        while (current != null && visited.add(identity(current))) {
            if (isNewTerm(current, newTerm)) {
                return current;
            }
            current = safeOwner(current);
        }
        return null;
    }

    public static boolean hasBooleanTagOrProperty(IRPModelElement element, String... names) {
        for (String name : names) {
            try {
                String propertyValue = element.getPropertyValue(name);
                if (isTrue(propertyValue)) {
                    return true;
                }
            } catch (RuntimeException ignored) { }
            try {
                IRPTag tag = element.getTag(name);
                if (tag != null && isTrue(tag.getValue())) {
                    return true;
                }
            } catch (RuntimeException ignored) { }
        }
        return false;
    }

    private static boolean isTrue(String value) {
        String normalized = normalize(value);
        return normalized.equals("true") || normalized.equals("yes") || normalized.equals("1");
    }

    public static List<IRPDependency> dependenciesByNewTerm(IRPModelElement context, String newTerm) {
        List<IRPDependency> dependencies = new ArrayList<>();
        for (IRPModelElement element : findByNewTerm(context, newTerm)) {
            if (element instanceof IRPDependency) {
                dependencies.add((IRPDependency) element);
            }
        }
        return dependencies;
    }

    public static List<IRPGeneralization> generalizationsByNewTerm(IRPModelElement context, String newTerm) {
        List<IRPGeneralization> generalizations = new ArrayList<>();
        for (IRPModelElement element : findByNewTerm(context, newTerm)) {
            if (element instanceof IRPGeneralization) {
                generalizations.add((IRPGeneralization) element);
            }
        }
        return generalizations;
    }

    public static IRPModelElement otherDependencyEnd(IRPDependency dependency, IRPModelElement element) {
        IRPModelElement dependent = dependency.getDependent();
        IRPModelElement dependsOn = dependency.getDependsOn();
        if (sameElement(dependent, element)) {
            return dependsOn;
        }
        if (sameElement(dependsOn, element)) {
            return dependent;
        }
        return null;
    }

    public static List<IRPMessage> sequenceMessages(IRPModelElement diagramElement) {
        if (!(diagramElement instanceof IRPSequenceDiagram)) {
            return List.of();
        }
        IRPCollaboration collaboration;
        try {
            collaboration = ((IRPSequenceDiagram) diagramElement).getLogicalCollaboration();
        } catch (RuntimeException exception) {
            return List.of();
        }
        if (collaboration == null) {
            return List.of();
        }
        List<IRPMessage> messages = new ArrayList<>();
        try {
            for (IRPModelElement element : asModelElements(collaboration.getMessages())) {
                if (element instanceof IRPMessage) {
                    messages.add((IRPMessage) element);
                }
            }
        } catch (RuntimeException ignored) { }
        return distinctMessages(messages);
    }

    private static List<IRPMessage> distinctMessages(List<IRPMessage> messages) {
        Map<String, IRPMessage> byIdentity = new LinkedHashMap<>();
        for (IRPMessage message : messages) {
            if (message != null) {
                byIdentity.putIfAbsent(identity(message), message);
            }
        }
        return new ArrayList<>(byIdentity.values());
    }

    public static boolean isDataFlowMessage(IRPMessage message) {
        return messageKindMatches(message, "DataFlow");
    }

    public static boolean isConditionMark(IRPMessage message) {
        return message instanceof IRPConditionMark
                || messageKindMatches(message, "ConditionMark")
                || messageKindMatches(message, "Condition Mark");
    }

    public static boolean isActionBlock(IRPMessage message) {
        return message instanceof IRPActionBlock
                || messageKindMatches(message, "ActionBlock")
                || messageKindMatches(message, "SDActionBlock");
    }

    private static boolean messageKindMatches(IRPMessage message, String expectedKind) {
        if (message == null) {
            return false;
        }
        String expected = canonical(expectedKind);
        try {
            if (expected.equals(canonical(message.getMetaClass()))) {
                return true;
            }
        } catch (RuntimeException ignored) { }
        try {
            return expected.equals(canonical(message.getMessageType()));
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static IRPClassifierRole messageLifeline(IRPMessage message) {
        if (message == null) {
            return null;
        }
        try {
            IRPClassifierRole source = message.getSource();
            IRPClassifierRole target = message.getTarget();
            if (source != null && target != null && sameElement(source, target)) {
                return source;
            }
            if (source != null && (target == null || isActionBlock(message) || isConditionMark(message))) {
                return source;
            }
            return target != null ? target : source;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    public static IRPClassifier formalClassifier(IRPClassifierRole role) {
        if (role == null) {
            return null;
        }
        try {
            return role.getFormalClassifier();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    public static int compareSequenceNumbers(IRPMessage first, IRPMessage second) {
        return compareSequenceNumberText(sequenceNumber(first), sequenceNumber(second));
    }

    public static String sequenceNumber(IRPMessage message) {
        if (message == null) {
            return "";
        }
        try {
            String value = message.getSequenceNumber();
            return value == null ? "" : value.trim();
        } catch (RuntimeException exception) {
            return "";
        }
    }

    private static int compareSequenceNumberText(String first, String second) {
        String[] firstParts = first == null ? new String[0] : first.split("[\\.:-]");
        String[] secondParts = second == null ? new String[0] : second.split("[\\.:-]");
        int length = Math.max(firstParts.length, secondParts.length);
        for (int index = 0; index < length; index++) {
            int firstPart = index < firstParts.length ? parseSequencePart(firstParts[index]) : 0;
            int secondPart = index < secondParts.length ? parseSequencePart(secondParts[index]) : 0;
            int comparison = Integer.compare(firstPart, secondPart);
            if (comparison != 0) {
                return comparison;
            }
        }
        return String.valueOf(first).compareTo(String.valueOf(second));
    }

    private static int parseSequencePart(String value) {
        if (value == null) {
            return 0;
        }
        String digits = value.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException exception) {
            return Integer.MAX_VALUE;
        }
    }

    public static List<IRPModelElement> localClassifierPorts(IRPClassifier classifier) {
        if (classifier == null) {
            return List.of();
        }
        List<IRPModelElement> result = new ArrayList<>();
        try {
            for (IRPModelElement element : asModelElements(
                    classifier.getNestedElementsByMetaClass("SysMLPort", 0))) {
                if (sameElement(element.getOwner(), classifier)) {
                    result.add(element);
                }
            }
        } catch (RuntimeException exception) {
            reportStructuralApiFailure(
                    "read local SysML ports",
                    classifier,
                    exception);
        }
        try {
            for (IRPModelElement element : asModelElements(
                    classifier.getNestedElementsByMetaClass("Port", 0))) {
                if (sameElement(element.getOwner(), classifier)) {
                    result.add(element);
                }
            }
        } catch (RuntimeException exception) {
            reportStructuralApiFailure(
                    "read local UML ports",
                    classifier,
                    exception);
        }
        return distinct(result);
    }

    public static List<IRPModelElement> redefinedElements(IRPModelElement element) {
        if (element == null) {
            return List.of();
        }
        try {
            return distinct(asModelElements(element.getRedefines()));
        } catch (RuntimeException exception) {
            reportStructuralApiFailure("read redefined elements", element, exception);
            return List.of();
        }
    }

    public static List<IRPDependency> dependenciesFrom(IRPModelElement source) {
        if (source == null) {
            return List.of();
        }
        List<IRPDependency> dependencies = new ArrayList<>();
        for (IRPModelElement candidate : allProjectElements(source)) {
            if (candidate instanceof IRPDependency) {
                IRPDependency dependency = (IRPDependency) candidate;
                if (sameElement(dependency.getDependent(), source)) {
                    dependencies.add(dependency);
                }
            }
        }
        return dependencies;
    }

    /**
     * Returns the ports that belong to a classifier. Recursive traversal is
     * intentionally avoided so that ports of nested Functions or Systems are
     * not mistaken for ports of the referenced definition.
     */
    public static List<IRPModelElement> classifierPorts(
            IRPClassifier classifier) {
        if (classifier == null) {
            return List.of();
        }
        List<IRPModelElement> result = new ArrayList<>();
        try {
            result.addAll(asModelElements(classifier.getPorts()));
        } catch (RuntimeException exception) {
            reportStructuralApiFailure(
                    "read classifier ports",
                    classifier,
                    exception);
        }
        try {
            result.addAll(asModelElements(
                    classifier.getNestedElementsByMetaClass("SysMLPort", 0)));
        } catch (RuntimeException exception) {
            reportStructuralApiFailure(
                    "read direct classifier SysML ports",
                    classifier,
                    exception);
        }
        try {
            result.addAll(asModelElements(
                    classifier.getNestedElementsByMetaClass("Port", 0)));
        } catch (RuntimeException exception) {
            reportStructuralApiFailure(
                    "read direct classifier UML ports",
                    classifier,
                    exception);
        }
        return distinct(result);
    }

    public static List<IRPModelElement> nestedByNewTerm(IRPModelElement owner, String newTerm) {
        if (owner == null) {
            return List.of();
        }
        List<IRPModelElement> result = new ArrayList<>();
        for (IRPModelElement nested : asModelElements(owner.getNestedElementsRecursive())) {
            if (isNewTerm(nested, newTerm)) {
                result.add(nested);
            }
        }
        return distinct(result);
    }

    /**
     * Returns {@code true} when the supplied element is explicitly present in
     * at least one accepted diagram. The diagram owner and lifeline formal
     * classifiers are intentionally not inferred.
     */
    public static boolean isDirectlyRepresentedInAnyDiagram(
            IRPModelElement element,
            String... diagramNewTerms) {
        if (element == null) {
            return false;
        }
        Set<String> acceptedDiagrams = new LinkedHashSet<>();
        Collections.addAll(acceptedDiagrams, diagramNewTerms);
        for (IRPModelElement candidate : allProjectElements(element)) {
            boolean accepted = acceptedDiagrams.stream()
                    .anyMatch(term -> isNewTerm(candidate, term));
            if (!accepted) {
                continue;
            }
            for (IRPModelElement represented : diagramElements(candidate)) {
                if (sameElement(element, represented)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns every accepted diagram that explicitly represents an element.
     */
    public static List<IRPModelElement> directRepresentationDiagrams(
            IRPModelElement element,
            String... diagramNewTerms) {
        if (element == null) {
            return List.of();
        }
        Set<String> acceptedDiagrams = new LinkedHashSet<>();
        Collections.addAll(acceptedDiagrams, diagramNewTerms);
        List<IRPModelElement> result = new ArrayList<>();
        for (IRPModelElement candidate : allProjectElements(element)) {
            boolean accepted = acceptedDiagrams.stream()
                    .anyMatch(term -> isNewTerm(candidate, term));
            if (!accepted) {
                continue;
            }
            for (IRPModelElement represented : diagramElements(candidate)) {
                if (sameElement(element, represented)) {
                    result.add(candidate);
                    break;
                }
            }
        }
        return distinct(result);
    }

    /**
     * Returns {@code true} when {@code ancestor} owns {@code descendant}
     * directly or indirectly. The two arguments must be different elements.
     */
    public static boolean isAncestorOf(
            IRPModelElement ancestor,
            IRPModelElement descendant) {
        if (ancestor == null || descendant == null || sameElement(ancestor, descendant)) {
            return false;
        }
        IRPModelElement current = safeOwner(descendant);
        Set<String> visited = new LinkedHashSet<>();
        while (current != null && visited.add(identity(current))) {
            if (sameElement(ancestor, current)) {
                return true;
            }
            current = safeOwner(current);
        }
        return false;
    }

    /**
     * Resolves an owner without hiding structural API failures.
     */
    public static IRPModelElement safeOwner(IRPModelElement element) {
        if (element == null) {
            return null;
        }
        try {
            return element.getOwner();
        } catch (RuntimeException exception) {
            reportStructuralApiFailure("read element owner", element, exception);
            return null;
        }
    }

    /**
     * Returns the names of stereotypes whose {@code Exclusive} tag is true.
     */
    public static Set<String> exclusiveStereotypeNames(IRPModelElement element) {
        Set<String> names = new LinkedHashSet<>();
        if (element == null) {
            return names;
        }
        try {
            for (IRPModelElement stereotypeElement : asModelElements(element.getStereotypes())) {
                if (!(stereotypeElement instanceof IRPStereotype)) {
                    continue;
                }
                IRPStereotype stereotype = (IRPStereotype) stereotypeElement;
                if (hasExclusiveFlag(stereotype)) {
                    names.add(canonical(stereotype.getName()));
                }
            }
        } catch (RuntimeException exception) {
            reportStructuralApiFailure("read applied stereotypes", element, exception);
        }
        return names;
    }

    private static boolean hasExclusiveFlag(IRPStereotype stereotype) {
        try {
            IRPTag exclusiveTag = stereotype.getTag("Exclusive");
            if (exclusiveTag != null && isTrue(exclusiveTag.getValue())) {
                return true;
            }
        } catch (RuntimeException exception) {
            reportStructuralApiFailure(
                    "read Exclusive stereotype tag",
                    stereotype,
                    exception);
        }
        try {
            return isTrue(stereotype.getPropertyValue("Exclusive"));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    /**
     * Returns the lifeline roles explicitly materialized in a sequence diagram.
     */
    public static List<IRPClassifierRole> sequenceLifelines(
            IRPModelElement diagramElement) {
        List<IRPClassifierRole> result = new ArrayList<>();
        for (IRPModelElement diagramItem : diagramElements(diagramElement)) {
            if (diagramItem instanceof IRPClassifierRole) {
                result.add((IRPClassifierRole) diagramItem);
            }
        }
        return distinctClassifierRoles(result);
    }

    private static List<IRPClassifierRole> distinctClassifierRoles(
            List<IRPClassifierRole> roles) {
        Map<String, IRPClassifierRole> byIdentity = new LinkedHashMap<>();
        for (IRPClassifierRole role : roles) {
            if (role != null) {
                byIdentity.putIfAbsent(identity(role), role);
            }
        }
        return new ArrayList<>(byIdentity.values());
    }

    /**
     * Emits one controlled diagnostic per structural operation, element and
     * exception type. Repeated model-check passes therefore do not flood the
     * Rhapsody Output window.
     */
    public static void reportStructuralApiFailure(
            String operation,
            IRPModelElement context,
            RuntimeException exception) {
        String key = operation
                + '|'
                + identity(context)
                + '|'
                + exception.getClass().getName();
        if (!REPORTED_STRUCTURAL_FAILURES.add(key)) {
            return;
        }
        String message = "Rhapsody API diagnostic while attempting to "
                + operation
                + " on "
                + elementLabel(context)
                + ": "
                + exception.getClass().getSimpleName()
                + " - "
                + (exception.getMessage() == null
                        ? "<no message>"
                        : exception.getMessage());
        try {
            SafranCheckerRuntime.getLogger().warning(message);
        } catch (RuntimeException loggerUnavailable) {
            System.err.println(message);
        }
    }

    public static String elementLabel(IRPModelElement element) {
        if (element == null) {
            return "<null>";
        }
        try {
            String fullPath = element.getFullPathName();
            if (fullPath != null && !fullPath.isBlank()) {
                return fullPath;
            }
        } catch (RuntimeException ignored) { }
        try { return element.getName(); } catch (RuntimeException ignored) { return "<unnamed>"; }
    }

    public static boolean relationLooksLikeIncludeOrExtend(IRPModelElement relation) {
        StringBuilder text = new StringBuilder();
        try { text.append(' ').append(relation.getName()); } catch (RuntimeException ignored) { }
        try { text.append(' ').append(relation.getUserDefinedMetaClass()); } catch (RuntimeException ignored) { }
        if (relation instanceof IRPRelation) {
            try { text.append(' ').append(((IRPRelation) relation).getRelationType()); } catch (RuntimeException ignored) { }
            try { text.append(' ').append(((IRPRelation) relation).getRelationLabel()); } catch (RuntimeException ignored) { }
        }
        try {
            for (IRPModelElement stereotype : asModelElements(relation.getStereotypes())) {
                text.append(' ').append(stereotype.getName());
            }
        } catch (RuntimeException ignored) { }
        String normalized = normalize(text.toString());
        return normalized.contains("include") || normalized.contains("extend");
    }
}
