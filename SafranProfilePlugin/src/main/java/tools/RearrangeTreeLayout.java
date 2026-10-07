package tools;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.JOptionPane;
import javax.swing.UIManager;

import com.telelogic.rhapsody.core.IRPApplication;
import com.telelogic.rhapsody.core.IRPDependency;
import com.telelogic.rhapsody.core.IRPDiagram;
import com.telelogic.rhapsody.core.IRPGraphEdge;
import com.telelogic.rhapsody.core.IRPGraphElement;
import com.telelogic.rhapsody.core.IRPGraphNode;
import com.telelogic.rhapsody.core.IRPModelElement;

/**
 * Rearranges children of a selected element (or the entire tree if root is selected).
 *
 * Partial, compact layout: when you rearrange element E's children from vertical to
 * horizontal (or vice versa), E's siblings reflow upward/downward to fill the space
 * efficiently. Only E and its subtree are repositioned on the selected axis; siblings
 * and parent remain at their original depth.
 */
public class RearrangeTreeLayout extends RhapsodyTool {

    public static final String COMMAND = "Safran Toolkit...\\Rearrange Tree Layout";

    private static final int NODE_WIDTH  = 300;
    private static final int NODE_HEIGHT = 100;
    private static final int H_SPACING   = 40;
    private static final int V_SPACING   = 60;
    private static final int MARGIN_X    = 50;
    private static final int MARGIN_Y    = 50;

    private boolean horizontal;
    private IRPDiagram diagram;
    private IRPModelElement selectedElement;

    public RearrangeTreeLayout(IRPApplication rpyApp) {
        super(rpyApp);
    }

    @Override
    public void execute() {
        try {
            rhpLog.info("Start - RearrangeTreeLayout (partial layout)");

            selectedElement = rhApp.getSelectedElement();
            if (selectedElement == null) {
                JOptionPane.showMessageDialog(null, "Please select an element in the diagram.");
                return;
            }
            rhpLog.info("Selected element: " + selectedElement.getName());

            diagram = findDiagram(selectedElement);
            if (diagram == null) {
                JOptionPane.showMessageDialog(null,
                        "No diagram found.\nOpen or activate the breakdown diagram first.");
                return;
            }
            rhpLog.info("Diagram found: " + diagram.getName());

            if (!askOrientation()) return;

            TreeNode root = buildTreeFromEdges();
            if (root == null) {
                JOptionPane.showMessageDialog(null,
                        "Cannot detect a tree structure in the diagram.");
                return;
            }

            // Find the tree node corresponding to the selected element
            TreeNode selectedNode = findNode(root, selectedElement);
            if (selectedNode == null) {
                JOptionPane.showMessageDialog(null,
                        "Selected element not found in diagram tree.");
                return;
            }
            rhpLog.info("Selected node in tree: " + selectedNode.element.getName());

            // Partial layout: re-arrange only the selected node's children
            if (selectedNode.children.isEmpty()) {
                JOptionPane.showMessageDialog(null, "Selected element has no children.");
                return;
            }

            rhpLog.info("Performing partial layout on: " + selectedNode.element.getName());

            // Assign breadth only to the selected subtree
            assignBreadth(selectedNode);

            // Place the selected node's children according to new orientation
            placeChildren(selectedNode, 0);

            // Reflow siblings and parent to fill space compactly
            reflowFromParent(selectedNode);

            applyPositions(root);

            diagram.openDiagram();
            rhpLog.info("End - RearrangeTreeLayout");
        } catch (Exception ex) {
            rhpLog.error("EXCEPTION: " + ex.getMessage());
            ex.printStackTrace();
            JOptionPane.showMessageDialog(null, "Error: " + ex.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Tree construction FROM EDGES
    // -------------------------------------------------------------------------

    private TreeNode buildTreeFromEdges() {
        Map<String, TreeNode> nodesByGuid = new HashMap<>();
        @SuppressWarnings("unchecked")
        List<IRPGraphElement> graphElements = diagram.getGraphicalElements().toList();

        rhpLog.info("Total graph elements: " + graphElements.size());

        for (IRPGraphElement ge : graphElements) {
            if (ge instanceof IRPGraphNode) {
                IRPGraphNode gn = (IRPGraphNode) ge;
                IRPModelElement mo = gn.getModelObject();
                if (mo == null) continue;
                nodesByGuid.put(mo.getGUID(), new TreeNode(mo, gn));
                rhpLog.info("Node: " + mo.getName());
            }
        }
        rhpLog.info("Total nodes collected: " + nodesByGuid.size());
        if (nodesByGuid.isEmpty()) return null;

        List<String[]> edges = new ArrayList<>();
        int edgeCount = 0;
        for (IRPGraphElement ge : graphElements) {
            if (!(ge instanceof IRPGraphEdge)) continue;
            edgeCount++;
            IRPGraphEdge edge = (IRPGraphEdge) ge;
            IRPModelElement s = nodeModel(edge.getSource());
            IRPModelElement t = nodeModel(edge.getTarget());
            if (s == null || t == null) continue;
            if (s.getGUID().equals(t.getGUID())) continue;
            edges.add(new String[] { s.getGUID(), t.getGUID() });
            rhpLog.info("Edge: " + s.getName() + " -> " + t.getName());
        }
        rhpLog.info("Total edges found: " + edgeCount + ", valid: " + edges.size());
        if (edges.isEmpty()) return null;

        boolean sourceIsParent = orientationGivesSingleRoot(nodesByGuid, edges, true);
        if (!sourceIsParent && !orientationGivesSingleRoot(nodesByGuid, edges, false)) {
            sourceIsParent = true;
        }
        rhpLog.info("Final orientation: sourceIsParent=" + sourceIsParent);

        Set<String> hasParent = new HashSet<>();
        for (String[] e : edges) {
            String parent = sourceIsParent ? e[0] : e[1];
            String child  = sourceIsParent ? e[1] : e[0];
            TreeNode p = nodesByGuid.get(parent);
            TreeNode c = nodesByGuid.get(child);
            if (p == null || c == null) continue;
            p.children.add(c);
            c.parent = p;
            hasParent.add(child);
            rhpLog.info("Linked: " + p.element.getName() + " -> " + c.element.getName());
        }

        TreeNode root = null;
        for (TreeNode n : nodesByGuid.values()) {
            if (!hasParent.contains(n.element.getGUID())) {
                if (root == null) {
                    root = n;
                    rhpLog.info("Found root: " + root.element.getName());
                }
            }
        }
        if (root == null) return null;

        sortChildren(root);
        return root;
    }

    private boolean orientationGivesSingleRoot(
            Map<String, TreeNode> nodes, List<String[]> edges, boolean sourceIsParent) {
        Set<String> childGuids = new HashSet<>();
        for (String[] e : edges) {
            childGuids.add(sourceIsParent ? e[1] : e[0]);
        }
        int roots = 0;
        for (String guid : nodes.keySet()) {
            if (!childGuids.contains(guid)) roots++;
        }
        return roots == 1;
    }

    private void sortChildren(TreeNode node) {
        node.children.sort((a, b) -> Integer.compare(currentOrderKey(a), currentOrderKey(b)));
        for (TreeNode c : node.children) sortChildren(c);
    }

    private int currentOrderKey(TreeNode n) {
        try {
            String pos = n.graphNode.getGraphicalProperty("Position").getValue();
            String[] xy = pos.split(",");
            int x = Integer.parseInt(xy[0].trim());
            int y = Integer.parseInt(xy[1].trim());
            return horizontal ? x : y;
        } catch (Exception ignore) {
            return 0;
        }
    }

    private IRPModelElement nodeModel(IRPGraphElement ge) {
        if (ge instanceof IRPGraphNode) {
            return ((IRPGraphNode) ge).getModelObject();
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Partial Layout Algorithm
    // -------------------------------------------------------------------------

    private void assignBreadth(TreeNode v) {
        for (TreeNode c : v.children) assignBreadth(c);

        if (v.children.isEmpty()) {
            v.breadth = nodeSecondarySize();
        } else {
            double total = 0;
            for (TreeNode c : v.children) total += c.breadth;
            total += siblingSpacing() * (v.children.size() - 1);
            v.breadth = Math.max(nodeSecondarySize(), total);
        }
        rhpLog.info("breadth " + v.element.getName() + ": " + v.breadth);
    }

    // Place only the children of the selected node
    private void placeChildren(TreeNode parent, double bandStart) {
        if (parent.children.isEmpty()) return;

        double childrenTotal = 0;
        for (TreeNode c : parent.children) childrenTotal += c.breadth;
        childrenTotal += siblingSpacing() * (parent.children.size() - 1);

        // Center children around bandStart
        double childStart = bandStart - childrenTotal / 2.0 + parent.breadth / 2.0;
        for (TreeNode c : parent.children) {
            placeNodeAndDescendants(c, childStart, parent.depth + 1);
            childStart += c.breadth + siblingSpacing();
        }
    }

    private void placeNodeAndDescendants(TreeNode v, double bandStart, int depth) {
        v.depth = depth;
        v.secondary = bandStart;

        for (TreeNode c : v.children) {
            placeNodeAndDescendants(c, bandStart, depth + 1);
        }
        rhpLog.info("place " + v.element.getName() + ": secondary=" + v.secondary);
    }

    // Reflow siblings to fill space compactly
    private void reflowFromParent(TreeNode selectedNode) {
        if (selectedNode.parent == null) return;

        TreeNode parent = selectedNode.parent;
        double bandStart = 0;

        for (TreeNode sibling : parent.children) {
            if (sibling == selectedNode) {
                // Skip, already placed
                bandStart += sibling.breadth + siblingSpacing();
            } else {
                placeNodeAndDescendants(sibling, bandStart, parent.depth + 1);
                bandStart += sibling.breadth + siblingSpacing();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Apply positions
    // -------------------------------------------------------------------------

    private void applyPositions(TreeNode node) {
        int x, y;
        if (horizontal) {
            x = (int) (MARGIN_X + node.depth * (NODE_WIDTH + H_SPACING));
            y = (int) (MARGIN_Y + node.secondary);
        } else {
            x = (int) (MARGIN_X + node.secondary);
            y = (int) (MARGIN_Y + node.depth * (NODE_HEIGHT + V_SPACING));
        }
        rhpLog.info("apply " + node.element.getName() + ": (" + x + ", " + y + ")");
        node.graphNode.setGraphicalProperty("Position", x + "," + y);
        for (TreeNode child : node.children) applyPositions(child);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private TreeNode findNode(TreeNode root, IRPModelElement element) {
        if (root.element.equals(element)) return root;
        for (TreeNode c : root.children) {
            TreeNode found = findNode(c, element);
            if (found != null) return found;
        }
        return null;
    }

    private boolean askOrientation() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignore) {}

        Object[] options = { "Vertical", "Horizontal" };
        int choice = JOptionPane.showOptionDialog(null,
                "Rearrange '" + selectedElement.getName() + "' children as:",
                "Orientation",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null, options, options[0]);
        if (choice < 0) return false;
        horizontal = (choice == 1);
        return true;
    }

    private double nodeSecondarySize() {
        return horizontal ? NODE_HEIGHT : NODE_WIDTH;
    }

    private double siblingSpacing() {
        return horizontal ? V_SPACING : H_SPACING;
    }

    private IRPDiagram findDiagram(IRPModelElement element) {
        try {
            IRPDiagram active = rhApp.getDiagramOfSelectedElement();
            if (active != null) return active;
        } catch (Exception ignore) {}

        @SuppressWarnings("unchecked")
        List<IRPModelElement> refs = element.getReferences().toList();
        for (IRPModelElement ref : refs) {
            if (ref instanceof IRPDependency) {
                IRPDependency dep = (IRPDependency) ref;
                if (dep.getDependent() instanceof IRPDiagram) {
                    return (IRPDiagram) dep.getDependent();
                }
            }
        }
        IRPModelElement owner = element.getOwner();
        if (owner != null) return findDiagram(owner);
        return null;
    }

    // -------------------------------------------------------------------------
    // TreeNode
    // -------------------------------------------------------------------------

    private static class TreeNode {
        final IRPModelElement element;
        final IRPGraphNode    graphNode;
        final List<TreeNode>  children = new ArrayList<>();

        TreeNode parent;
        double   breadth;
        double   secondary;
        int      depth;

        TreeNode(IRPModelElement element, IRPGraphNode graphNode) {
            this.element   = element;
            this.graphNode = graphNode;
        }
    }

    @Override
    public String commandName() { return COMMAND; }

    @Override
    public boolean isUndoable() { return true; }

    @Override
    public boolean isInteractive() { return true; }
}
