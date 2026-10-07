package utils;

import com.telelogic.rhapsody.core.*;

public final class DiagramFlowRedraw {

    private DiagramFlowRedraw() {}

    public static void redrawFlowOnDiagram(IRPApplication app,
                                           IRPDiagram diagram,
                                           IRPFlow flow,
                                           IRPGraphNode srcNode,
                                           IRPGraphNode trgNode) {

        // 1) remove existing graphical edges for this flow on this diagram
        IRPCollection existing = diagram.getCorrespondingGraphicElements(flow);
        if (existing != null && existing.getCount() > 0) {
            IRPCollection toRemove = app.createNewCollection();
            for (Object o : existing.toList()) {
                if (o instanceof IRPGraphElement) {
                    toRemove.addGraphicalItem((IRPGraphElement) o);
                }
            }
            if (toRemove.getCount() > 0) {
                diagram.removeGraphElements(toRemove);
            }
        }

        // 2) create new edge representing the flow
        int[] c1 = centerOf(srcNode);
        int[] c2 = centerOf(trgNode);

        diagram.addNewEdgeForElement(flow, srcNode, c1[0], c1[1], trgNode, c2[0], c2[1]);
    }

    private static int[] centerOf(IRPGraphNode node) {
        int x = 0, y = 0, w = 0, h = 0;

        try {
            String pos = node.getGraphicalProperty("Position").getValue(); // "x,y"
            String[] p = pos.split(",");
            x = Integer.parseInt(p[0].trim());
            y = Integer.parseInt(p[1].trim());
        } catch (Exception ignore) {}

        try { w = Integer.parseInt(node.getGraphicalProperty("Width").getValue()); } catch (Exception ignore) {}
        try { h = Integer.parseInt(node.getGraphicalProperty("Height").getValue()); } catch (Exception ignore) {}

        return new int[] { x + (w / 2), y + (h / 2) };
    }
}