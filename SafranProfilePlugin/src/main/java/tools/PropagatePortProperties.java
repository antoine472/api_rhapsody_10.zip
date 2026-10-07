package tools;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.telelogic.rhapsody.core.IRPApplication;
import com.telelogic.rhapsody.core.IRPClass;
import com.telelogic.rhapsody.core.IRPClassifier;
import com.telelogic.rhapsody.core.IRPCollection;
import com.telelogic.rhapsody.core.IRPDiagram;
import com.telelogic.rhapsody.core.IRPFlow;
import com.telelogic.rhapsody.core.IRPGraphNode;
import com.telelogic.rhapsody.core.IRPModelElement;
import com.telelogic.rhapsody.core.IRPStereotype;
import com.telelogic.rhapsody.core.IRPSysMLPort;

import main.constants.ProfilConstants;
import main.constants.Types;
import tools.rules.PortPropagationRules;

/**
 * FlowPort Property Propagation Tool
 * => propagation globale modèle (via references/flows), diagram uniquement pour aider la décision "border" si dispo.
 */
public class PropagatePortProperties extends RhapsodyTool {

    public static final String COMMAND = "Safran Toolkit...\\Propagate properties";

    private boolean propagateLabel;
    private boolean propagateName;
    private boolean propagateStereotypes;
    private boolean propagateType;
    private boolean propagateDirection;
    private boolean propagateConveyed;
    private boolean propagateDescription;

    private IRPDiagram activeDiagram;

    private final Map<String, String> propertyKeys = new HashMap<>();
    private IRPClass untyped;

    // -------- Flow direction literals (Rhapsody) --------
    private static final String FLOW_TO_END1 = "toEnd1";
    private static final String FLOW_TO_END2 = "toEnd2";
    private static final String FLOW_BIDIR  = "bidirectional";

    public PropagatePortProperties(IRPApplication rhapsodyApp) {
        super(rhapsodyApp);

        propertyKeys.put("ConveysFlowUpdate", "SafranToolsSettings.PortPropagation.ConveysFlowUpdate");
        propertyKeys.put("DescriptionPropagation", "SafranToolsSettings.PortPropagation.DescriptionPropagation");
        propertyKeys.put("DirectionPropagation", "SafranToolsSettings.PortPropagation.DirectionPropagation");
        propertyKeys.put("LabelPropagation", "SafranToolsSettings.PortPropagation.LabelPropagation");
        propertyKeys.put("NamePropagation", "SafranToolsSettings.PortPropagation.NamePropagation");
        propertyKeys.put("StereotypesPropagation", "SafranToolsSettings.PortPropagation.StereotypesPropagation");
        propertyKeys.put("TypePropagation", "SafranToolsSettings.PortPropagation.TypePropagation");
    }

    private void loadSettings() {
        propagateConveyed    = getProjectBoolean("ConveysFlowUpdate");
        propagateDescription = getProjectBoolean("DescriptionPropagation");
        propagateDirection   = getProjectBoolean("DirectionPropagation");
        propagateLabel       = getProjectBoolean("LabelPropagation");
        propagateName        = getProjectBoolean("NamePropagation");
        propagateStereotypes = getProjectBoolean("StereotypesPropagation");
        propagateType        = getProjectBoolean("TypePropagation");
    }

    private boolean getProjectBoolean(String key) {
        String value = rhApp.activeProject().getPropertyValue(propertyKeys.get(key));
        return "True".equalsIgnoreCase(value);
    }

    @Override
    public void execute() {
        rhpLog.debug("Starting FlowPort property propagation...");

        loadSettings();

        // diagram = uniquement pour border-detect si dispo (sinon null => fallback)
        activeDiagram = rhApp.getDiagramOfSelectedElement();

        untyped = (IRPClass) rhApp.activeProject().findElementByGUID(ProfilConstants.UNTYPED_GUID);

        @SuppressWarnings("unchecked")
        List<IRPModelElement> selectedElements = rhApp.getListOfSelectedElements().toList();

        for (IRPModelElement element : selectedElements) {
            if (element instanceof IRPSysMLPort) {
                propagateFromPort((IRPSysMLPort) element);
            }
        }

        rhpLog.debug("FlowPort property propagation completed.");
    }

    private void propagateFromPort(IRPSysMLPort sourcePort) {
        rhpLog.debug("Processing SysMLPort: " + safePath(sourcePort));

        @SuppressWarnings("unchecked")
        List<IRPModelElement> references = sourcePort.getReferences().toList();

        for (IRPModelElement reference : references) {
            if (!(reference instanceof IRPFlow)) continue;

            IRPFlow flow = (IRPFlow) reference;

            IRPSysMLPort end1 = flow.getEnd1SysMLPort();
            IRPSysMLPort end2 = flow.getEnd2SysMLPort();

            if (end1 != null && end1.equals(sourcePort)) {
                propagateProperties(sourcePort, end2, flow);
            } else if (end2 != null && end2.equals(sourcePort)) {
                propagateProperties(sourcePort, end1, flow);
            }
        }
    }

    private boolean isReadOnly(IRPSysMLPort port) {
        return port != null && port.isReadOnly() == Types.Unit.READ_ONLY;
    }

    private void propagateProperties(IRPSysMLPort sourcePort, IRPSysMLPort targetPort, IRPFlow flow) {
        if (sourcePort == null || targetPort == null || flow == null) return;

        boolean targetReadOnly = isReadOnly(targetPort);
        if (targetReadOnly) {
            rhpLog.warn("Target port is Read Only (RO): " + safePath(targetPort));
        }

     // Type + conveyed
        if (propagateType) {
            IRPClassifier sourceType = sourcePort.getType();

            // 1) Propagation type (port) : inchangée
            if (sourceType != null && !targetReadOnly) {
                try { targetPort.setType(sourceType); }
                catch (Throwable t) {
                    rhpLog.warn("Failed to set target type: tgt=" + safePath(targetPort) + " err=" + t.getMessage());
                }
            }

            // 2) Conveyed sur flow : toujours nettoyer Untyped + ne jamais ajouter Untyped
            if (propagateConveyed) {
                // ✅ nettoyage systématique, même si sourceType == null
                cleanupUntypedConveyed(flow);

                if (sourceType != null && !isUntypedClassifier(sourceType)) {
                    try {
                        if (!hasConveyedByGuid(flow, sourceType)) {
                            flow.addConveyed(sourceType);
                        }
                    } catch (Throwable t) {
                        rhpLog.warn("Conveyed update failed on flow=" + safePath(flow) + " : " + t.getMessage());
                    }
                } else {
                    rhpLog.debug("Skip conveyed add: source type is null or Untyped for flow " + safePath(flow));
                }
            }
        }

        // Direction (ports + sync flow)
        if (propagateDirection) {
            if (targetPort.isTypelessObject() == 1 && untyped != null) {
                try { targetPort.setType(untyped); } catch (Throwable ignore) {}
            }
            propagateDirection(sourcePort, targetPort, flow);
        }

        // Stereotypes (robuste)
        if (propagateStereotypes) {
            propagateStereotypesSafe(sourcePort, targetPort, flow, targetReadOnly);
        }

        // Label, name, description
        if (!targetReadOnly) {
            if (propagateLabel)       targetPort.setDisplayName(sourcePort.getDisplayName());
            if (propagateName)        targetPort.setName(sourcePort.getName());
            if (propagateDescription) targetPort.setDescription(sourcePort.getDescription());
        }
    }

    // =========================================================
    // Stereotypes (robuste: ne pas crasher si non applicable)
    // =========================================================
    private void propagateStereotypesSafe(IRPSysMLPort sourcePort, IRPSysMLPort targetPort, IRPFlow flow, boolean targetReadOnly) {
        List<IRPStereotype> sourceStereotypes = filterStereotypes(sourcePort.getStereotypes());
        List<IRPStereotype> targetStereotypes = filterStereotypes(targetPort.getStereotypes());
        List<IRPStereotype> flowStereotypes   = filterStereotypes(flow.getStereotypes());

        // Add missing
        for (IRPStereotype st : sourceStereotypes) {
            if (!targetReadOnly && !targetStereotypes.contains(st)) {
                try {
                    targetPort.addSpecificStereotype(st);
                } catch (Throwable t) {
                    rhpLog.warn("Skip stereotype on targetPort (not applicable?): st=" + safeGuid(st)
                            + " target=" + safePath(targetPort) + " err=" + t.getMessage());
                }
            }
            if (!flowStereotypes.contains(st)) {
                try {
                    flow.addSpecificStereotype(st);
                } catch (Throwable t) {
                    // C’est ton cas: stéréotype port-only => opérationnelle flow pas supportée
                    rhpLog.warn("Skip stereotype on flow (not applicable?): st=" + safeGuid(st)
                            + " flow=" + safePath(flow) + " err=" + t.getMessage());
                }
            }
        }

        // Remove extra on target port
        if (!targetReadOnly) {
            for (IRPStereotype st : new ArrayList<>(targetStereotypes)) {
                if (!sourceStereotypes.contains(st)) {
                    try { targetPort.removeStereotype(st); }
                    catch (Throwable t) {
                        rhpLog.warn("Failed to remove stereotype from targetPort: st=" + safeGuid(st)
                                + " target=" + safePath(targetPort) + " err=" + t.getMessage());
                    }
                }
            }
        }

        // Remove extra on flow
        for (IRPStereotype st : new ArrayList<>(flowStereotypes)) {
            if (!sourceStereotypes.contains(st)) {
                try { flow.removeStereotype(st); }
                catch (Throwable t) {
                    rhpLog.warn("Failed to remove stereotype from flow: st=" + safeGuid(st)
                            + " flow=" + safePath(flow) + " err=" + t.getMessage());
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private List<IRPStereotype> filterStereotypes(IRPCollection collection) {
        if (collection == null) return List.of();
        return (List<IRPStereotype>) collection.toList().stream()
                .map(e -> (IRPStereotype) e)
                .filter(s -> {
                    try { return ((IRPStereotype) s).getIsNewTerm() != 1; }
                    catch (Throwable t) { return true; }
                })
                .collect(Collectors.toList());
    }

    // =========================================================
    // Direction : global modèle + fallback (diagram border si dispo)
    // =========================================================
    private void propagateDirection(IRPSysMLPort sourcePort, IRPSysMLPort targetPort, IRPFlow flow) {

        boolean sourceIsEnd1 = flow.getEnd1SysMLPort() != null && flow.getEnd1SysMLPort().equals(sourcePort);

        if (isReadOnly(targetPort)) {
            rhpLog.warn("Skip direction propagation: target port is Read Only: " + safePath(targetPort));
            return;
        }

        // Border status (diagram) : requis pour appliquer les règles "port contour"
        Boolean srcBorder = isPortOnDiagramBorderMaybe(sourcePort);
        Boolean tgtBorder = isPortOnDiagramBorderMaybe(targetPort);

        // Normalisation direction source (uniquement pour la propagation des ports)
        String srcDir = normalizePortDir(sourcePort.getPortDirection());

        // --- A) Propagation direction des PORTS (inchangée : si srcDir null => on ne touche pas aux ports)
        if (srcDir != null && !srcDir.isBlank()) {

            boolean flip = shouldFlipHybrid(sourcePort, targetPort);
            String newPortDir = flip ? flipDirectionSafe(srcDir) : srcDir;

            String before = targetPort.getPortDirection();
            try {
                targetPort.setPortDirection(newPortDir);
            } catch (Throwable t) {
                rhpLog.warn("Failed to set port direction: tgt=" + safePath(targetPort) + " err=" + t.getMessage());
                return;
            }

            rhpLog.debug("Port dir set: tgt=" + safePath(targetPort)
                    + " | " + before + " -> " + newPortDir
                    + " | flip=" + flip
                    + " | activeDiagram=" + (activeDiagram != null));
        } else {
            rhpLog.debug("Skip port-direction propagation (srcDir missing): clicked=" + safePath(sourcePort)
                    + " rawDir=" + sourcePort.getPortDirection());
        }

        // --- B) Sync direction du FLOW : priorité aux règles "port contour" si XOR border connu
        String newFlowDir = null;

        if (srcBorder != null && tgtBorder != null && (srcBorder.booleanValue() ^ tgtBorder.booleanValue())) {
            newFlowDir = PortPropagationRules.computeFlowDirectionForBorderPair(
                    sourcePort.getPortDirection(),
                    targetPort.getPortDirection(),
                    sourceIsEnd1,
                    srcBorder.booleanValue() // clickedIsBorder
            );

            if (newFlowDir != null) {
                setFlowDirectionIfNeeded(flow, newFlowDir, "border-rules");
                return; // priorité : on ne repasse pas par les rules génériques
            }
            // si null => on tombe sur fallback (rare : 2 dirs implicites)
        }

        // --- C) Fallback : règles génériques existantes (nécessitent srcDir connu)
        if (srcDir == null || srcDir.isBlank()) {
            rhpLog.debug("Flow sync skipped (srcDir missing and no border-rule decision): flow=" + safePath(flow)
                    + " | clicked=" + safePath(sourcePort) + " rawDir=" + sourcePort.getPortDirection()
                    + " | otherRawDir=" + targetPort.getPortDirection()
                    + " | current=" + flow.getDirection());
            return;
        }

        ContainmentInfo ci = computeContainment(sourcePort, targetPort);

        newFlowDir = PortPropagationRules.computeFlowDirectionFromClickedPort(
                srcDir,
                normalizePortDir(targetPort.getPortDirection()),
                sourceIsEnd1,
                ci.isContainment,
                ci.ancestorIsClicked
        );

        if (newFlowDir != null) {
            setFlowDirectionIfNeeded(flow, newFlowDir, "rules");
        } else {
            rhpLog.debug("Flow sync skipped (rules returned null): flow=" + safePath(flow)
                    + " | clicked=" + safePath(sourcePort) + " dir=" + srcDir
                    + " | other=" + safePath(targetPort) + " dir=" + targetPort.getPortDirection()
                    + " | current=" + flow.getDirection());
        }
    }

    private void setFlowDirectionIfNeeded(IRPFlow flow, String newDir, String reason) {
        if (newDir == null) return;

        String current = null;
        try { current = flow.getDirection(); } catch (Throwable ignore) {}

        if (current == null || !newDir.equalsIgnoreCase(current)) {
            rhpLog.debug("Flow dir sync: " + safePath(flow)
                    + " | " + current + " -> " + newDir
                    + " | reason=" + reason);
            try { flow.setDirection(newDir); }
            catch (Throwable t) {
                rhpLog.warn("Failed to set flow direction: flow=" + safePath(flow) + " err=" + t.getMessage());
            }
        }
    }

    // =========================================================
    // Border detection (nullable) + containment fallback
    // =========================================================

    private IRPGraphNode safeGraphicalParent(IRPGraphNode node) {
        try { return (IRPGraphNode) node.getGraphicalParent(); }
        catch (Throwable e) { return null; }
    }

    private IRPModelElement safeModelObject(IRPGraphNode node) {
        try { return node.getModelObject(); }
        catch (Throwable e) { return null; }
    }

    /**
     * Returns:
     *  - Boolean.TRUE  => border
     *  - Boolean.FALSE => not border
     *  - null          => unknown (no activeDiagram / no graphic element found)
     */
    private Boolean isPortOnDiagramBorderMaybe(IRPSysMLPort port) {
        if (port == null || activeDiagram == null) return null;

        try {
            IRPCollection col = activeDiagram.getCorrespondingGraphicElements(port);
            if (col == null || col.getCount() == 0) return null; // unknown

            for (int i = 1; i <= col.getCount(); i++) {
                Object o = col.getItem(i);
                if (!(o instanceof IRPGraphNode)) continue;

                IRPGraphNode portNode = (IRPGraphNode) o;
                IRPGraphNode parent = safeGraphicalParent(portNode);
                if (parent == null) continue;

                if (safeModelObject(parent) == null) return Boolean.TRUE;

                IRPGraphNode grandParent = safeGraphicalParent(parent);
                if (grandParent != null && safeModelObject(grandParent) == null) return Boolean.TRUE;
            }

            return Boolean.FALSE;
        } catch (Throwable t) {
            return null;
        }
    }

    // ===== Containment stable (owner-chain) + fallback path =====

    private IRPModelElement safeOwner(IRPModelElement e) {
        try { return (e != null) ? e.getOwner() : null; }
        catch (Throwable t) { return null; }
    }

    private String safeGuid(IRPModelElement e) {
        try { return (e != null) ? e.getGUID() : null; }
        catch (Throwable t) { return null; }
    }

    private boolean isStrictAncestor(IRPModelElement ancestor, IRPModelElement element) {
        if (ancestor == null || element == null) return false;

        String a = safeGuid(ancestor);
        if (a == null) return false;

        for (IRPModelElement cur = safeOwner(element); cur != null; cur = safeOwner(cur)) {
            String g = safeGuid(cur);
            if (a.equals(g)) return true;
        }
        return false;
    }

    private IRPModelElement portContext(IRPSysMLPort p) {
        try { return (p != null) ? p.getOwner() : null; }
        catch (Throwable t) { return null; }
    }
    
 // Détection robuste "Untyped" même si untyped == null
    private boolean isUntypedClassifier(IRPClassifier c) {
        if (c == null) return false;

        try {
            // 1) priorité GUID constant (fiable)
            if (ProfilConstants.UNTYPED_GUID != null && ProfilConstants.UNTYPED_GUID.equals(c.getGUID())) {
                return true;
            }
        } catch (Throwable ignore) {}

        // 2) fallback si l'objet 'untyped' a été résolu
        try {
            if (untyped != null && untyped.getGUID().equals(c.getGUID())) {
                return true;
            }
        } catch (Throwable ignore) {}

        // 3) fallback de sécurité (si GUID introuvable en runtime)
        try {
            String n = c.getName();
            if (n != null && "Untyped".equalsIgnoreCase(n.trim())) return true;
        } catch (Throwable ignore) {}

        return false;
    }

    private void cleanupUntypedConveyed(IRPFlow flow) {
        if (flow == null) return;
        try {
            @SuppressWarnings("unchecked")
            List<Object> conveyed = flow.getConveyed().toList();

            for (Object o : new ArrayList<>(conveyed)) {
                if (o instanceof IRPClassifier c && isUntypedClassifier(c)) {
                    try { flow.removeConveyed(c); }
                    catch (Throwable t) {
                        rhpLog.warn("Failed to remove Untyped conveyed: flow=" + safePath(flow)
                                + " conveyed=" + safePath((IRPModelElement)c) + " err=" + t.getMessage());
                    }
                }
            }
        } catch (Throwable t) {
            rhpLog.warn("cleanupUntypedConveyed failed: flow=" + safePath(flow) + " err=" + t.getMessage());
        }
    }

    private boolean hasConveyedByGuid(IRPFlow flow, IRPClassifier type) {
        if (flow == null || type == null) return false;
        String gid;
        try { gid = type.getGUID(); } catch (Throwable t) { return false; }
        if (gid == null) return false;

        try {
            for (Object o : flow.getConveyed().toList()) {
                if (o instanceof IRPClassifier c) {
                    try {
                        if (gid.equals(c.getGUID())) return true;
                    } catch (Throwable ignore) {}
                }
            }
        } catch (Throwable ignore) {}
        return false;
    }

    private static class ContainmentInfo {
        final boolean isContainment;
        final boolean ancestorIsClicked;
        ContainmentInfo(boolean isContainment, boolean ancestorIsClicked) {
            this.isContainment = isContainment;
            this.ancestorIsClicked = ancestorIsClicked;
        }
    }

    private ContainmentInfo computeContainment(IRPSysMLPort clickedPort, IRPSysMLPort otherPort) {
        IRPModelElement cCtx = portContext(clickedPort);
        IRPModelElement oCtx = portContext(otherPort);

        if (cCtx != null && oCtx != null) {
            if (isStrictAncestor(cCtx, oCtx)) return new ContainmentInfo(true, true);
            if (isStrictAncestor(oCtx, cCtx)) return new ContainmentInfo(true, false);
            return new ContainmentInfo(false, false);
        }

        // fallback path heuristic
        List<String> cPath = getContainerPath(clickedPort != null ? clickedPort.getFullPathName() : null);
        List<String> oPath = getContainerPath(otherPort != null ? otherPort.getFullPathName() : null);

        if (isPrefixPath(cPath, oPath) && cPath.size() < oPath.size()) return new ContainmentInfo(true, true);
        if (isPrefixPath(oPath, cPath) && oPath.size() < cPath.size()) return new ContainmentInfo(true, false);

        return new ContainmentInfo(false, false);
    }

    private boolean shouldFlipHybrid(IRPSysMLPort src, IRPSysMLPort tgt) {

        // 1) border override (only if we can determine both)
        Boolean srcBorder = isPortOnDiagramBorderMaybe(src);
        Boolean tgtBorder = isPortOnDiagramBorderMaybe(tgt);

        if (srcBorder != null && tgtBorder != null) {
            if (srcBorder.booleanValue() ^ tgtBorder.booleanValue()) {
                return false; // keep border<->internal semantics
            }
        }

        // 2) containment (model-stable)
        IRPModelElement srcCtx = portContext(src);
        IRPModelElement tgtCtx = portContext(tgt);

        if (srcCtx != null && tgtCtx != null) {
            boolean delegation = isStrictAncestor(srcCtx, tgtCtx) || isStrictAncestor(tgtCtx, srcCtx);
            return !delegation; // delegation => no flip, peers => flip
        }

        // 3) fallback to path heuristic (dot-aware)
        return shouldFlipByPathHeuristic(src, tgt);
    }

    private boolean shouldFlipByPathHeuristic(IRPSysMLPort src, IRPSysMLPort tgt) {
        List<String> srcOwnerPath = getContainerPath(src != null ? src.getFullPathName() : null);
        List<String> tgtOwnerPath = getContainerPath(tgt != null ? tgt.getFullPathName() : null);

        boolean sameDepth  = srcOwnerPath.size() == tgtOwnerPath.size();
        boolean sameBranch = isPrefixPath(srcOwnerPath, tgtOwnerPath) || isPrefixPath(tgtOwnerPath, srcOwnerPath);

        if (sameDepth) return true;
        if (sameBranch) return false;

        return true; // default flip
    }

    // dot-aware container path
    private List<String> getContainerPath(String fullPathName) {
        if (fullPathName == null || fullPathName.isBlank()) return List.of();

        int dot = fullPathName.lastIndexOf('.');
        if (dot >= 0) {
            String ownerPath = fullPathName.substring(0, dot);
            return Arrays.asList(ownerPath.split("::"));
        }

        List<String> parts = Arrays.asList(fullPathName.split("::"));
        if (parts.size() <= 1) return List.of();
        return parts.subList(0, parts.size() - 1);
    }

    private boolean isPrefixPath(List<String> prefix, List<String> full) {
        if (prefix == null || full == null) return false;
        if (prefix.size() > full.size()) return false;
        for (int i = 0; i < prefix.size(); i++) {
            if (!prefix.get(i).equals(full.get(i))) return false;
        }
        return true;
    }

    private String normalizePortDir(String d) {
        return PortPropagationRules.normalizeDir(d);
    }

    private String flipDirectionSafe(String dir) {
        String d = normalizePortDir(dir);
        if (d == null) return null;

        if ("InOut".equalsIgnoreCase(d)) return d;
        if ("In".equalsIgnoreCase(d))  return "Out";
        if ("Out".equalsIgnoreCase(d)) return "In";

        return d;
    }

    private String safePath(IRPModelElement e) {
        try { return (e != null) ? e.getFullPathName() : "null"; }
        catch (Throwable t) { return "<?>"; }
    }

    private String safeGuid(IRPStereotype st) {
        try { return (st != null) ? st.getGUID() : "null"; }
        catch (Throwable t) { return "<?>"; }
    }

    @Override public boolean isUndoable() { return true; }
    @Override public String commandName() { return COMMAND; }
}