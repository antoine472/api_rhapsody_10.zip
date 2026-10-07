package test.unittest;

import org.junit.jupiter.api.Test;

import com.telelogic.rhapsody.core.IRPApplication;
import com.telelogic.rhapsody.core.IRPClass;
import com.telelogic.rhapsody.core.IRPCollection;
import com.telelogic.rhapsody.core.IRPDiagram;
import com.telelogic.rhapsody.core.IRPFlow;
import com.telelogic.rhapsody.core.IRPGraphElement;
import com.telelogic.rhapsody.core.IRPGraphNode;
import com.telelogic.rhapsody.core.IRPGraphicalProperty;
import com.telelogic.rhapsody.core.IRPModelElement;
import com.telelogic.rhapsody.core.IRPSysMLPort;

import utils.D2Coordinates;
import utils.D2Rectangle;
import utils.DiagramFlowRedraw;
import utils.PortCreation;

import static test.unittest.RhpTestMocks.collectionOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests of the small helpers of the {@code utils} package that
 * hold decisions: edge anchor geometry ({@link D2Rectangle}), the next free
 * port name ({@link PortCreation}) and the redraw of a flow edge
 * ({@link DiagramFlowRedraw}).
 */
class UtilsTest {

    // ------------------------------------------------------------------
    // D2Coordinates / D2Rectangle
    // ------------------------------------------------------------------

    @Test
    void d2Rectangle_anchors_sourceMidLeft_targetBottomEighth() {
        D2Rectangle r = new D2Rectangle(" 80 ", "40", "100, 20");

        assertEquals("100,40", r.getSourcePosition());   // x, y + h/2
        assertEquals("110,60", r.getTargetPosition());   // x + w/8, y + h
    }

    @Test
    void d2Coordinates_parsesTrimmedIntegers() {
        assertEquals("D2Coordinates{X=150, Y=20}", new D2Coordinates(" 150 , 20 ").toString());
    }

    @Test
    void d2Coordinates_andRectangle_rejectMalformedInput() {
        assertThrows(IllegalArgumentException.class, () -> new D2Coordinates("150"));
        assertThrows(IllegalArgumentException.class, () -> new D2Coordinates("1,2,3"));
        assertThrows(IllegalArgumentException.class, () -> new D2Coordinates("a,2"));
        assertThrows(IllegalArgumentException.class, () -> new D2Rectangle("x", "40", "0,0"));
        assertThrows(IllegalArgumentException.class, () -> new D2Rectangle("-1", "40", "0,0"));
        assertThrows(IllegalArgumentException.class, () -> new D2Rectangle("10", "40", "bad"));
    }

    // ------------------------------------------------------------------
    // PortCreation
    // ------------------------------------------------------------------

    private static IRPModelElement named(String name) {
        IRPModelElement el = mock(IRPModelElement.class);
        when(el.getName()).thenReturn(name);
        return el;
    }

    @Test
    void createPort_usesTheNextIndexAfterTheHighestPNumber() {
        IRPClass owner = mock(IRPClass.class);
        doReturn(collectionOf(named("p_1"), named("p_3"), named("p_x"), named("speed"), named("p_")))
                .when(owner).getPorts();
        IRPSysMLPort created = mock(IRPSysMLPort.class);
        when(owner.addNewAggr("Flow Port", "p_4")).thenReturn(created);

        assertSame(created, PortCreation.createPort(owner));
    }

    @Test
    void createPort_firstPortIsP1() {
        IRPClass owner = mock(IRPClass.class);
        doReturn(collectionOf()).when(owner).getPorts();

        PortCreation.createPort(owner);

        verify(owner).addNewAggr("Flow Port", "p_1");
    }

    // ------------------------------------------------------------------
    // DiagramFlowRedraw
    // ------------------------------------------------------------------

    private static IRPGraphicalProperty prop(String value) {
        IRPGraphicalProperty p = mock(IRPGraphicalProperty.class);
        when(p.getValue()).thenReturn(value);
        return p;
    }

    private static IRPGraphNode node(String position, String width, String height) {
        IRPGraphicalProperty pos = prop(position);
        IRPGraphicalProperty w = prop(width);
        IRPGraphicalProperty h = prop(height);
        IRPGraphNode n = mock(IRPGraphNode.class);
        doReturn(pos).when(n).getGraphicalProperty("Position");
        doReturn(w).when(n).getGraphicalProperty("Width");
        doReturn(h).when(n).getGraphicalProperty("Height");
        return n;
    }

    @Test
    void redrawFlow_removesTheOldEdges_drawsOneBetweenTheNodeCentres() {
        IRPApplication app = mock(IRPApplication.class);
        IRPCollection removal = mock(IRPCollection.class);
        when(removal.getCount()).thenReturn(1);
        when(app.createNewCollection()).thenReturn(removal);
        IRPDiagram diagram = mock(IRPDiagram.class);
        IRPFlow flow = mock(IRPFlow.class);
        IRPGraphElement oldEdge = mock(IRPGraphElement.class);
        doReturn(collectionOf(oldEdge, "not a graphic")).when(diagram).getCorrespondingGraphicElements(flow);
        IRPGraphNode src = node("10,20", "100", "50");
        IRPGraphNode tgt = node(" 200 , 0 ", "40", "30");

        DiagramFlowRedraw.redrawFlowOnDiagram(app, diagram, flow, src, tgt);

        verify(removal).addGraphicalItem(oldEdge);
        verify(diagram).removeGraphElements(removal);
        verify(diagram).addNewEdgeForElement(flow, src, 60, 45, tgt, 220, 15);
    }

    @Test
    void redrawFlow_noOldEdge_unreadableGeometry_drawsAtOrigin() {
        IRPApplication app = mock(IRPApplication.class);
        IRPDiagram diagram = mock(IRPDiagram.class);
        IRPFlow flow = mock(IRPFlow.class);
        doReturn(collectionOf()).when(diagram).getCorrespondingGraphicElements(flow);
        IRPGraphNode src = mock(IRPGraphNode.class); // no graphical property at all
        IRPGraphNode tgt = node("garbage", "w", "h");

        DiagramFlowRedraw.redrawFlowOnDiagram(app, diagram, flow, src, tgt);

        verify(app, never()).createNewCollection();
        verify(diagram, never()).removeGraphElements(any());
        verify(diagram).addNewEdgeForElement(flow, src, 0, 0, tgt, 0, 0);
    }
}
