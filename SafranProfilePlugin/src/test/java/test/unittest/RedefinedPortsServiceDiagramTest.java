package test.unittest;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.telelogic.rhapsody.core.IRPApplication;
import com.telelogic.rhapsody.core.IRPClass;
import com.telelogic.rhapsody.core.IRPClassifier;
import com.telelogic.rhapsody.core.IRPCollection;
import com.telelogic.rhapsody.core.IRPDiagram;
import com.telelogic.rhapsody.core.IRPFlow;
import com.telelogic.rhapsody.core.IRPGeneralization;
import com.telelogic.rhapsody.core.IRPGraphEdge;
import com.telelogic.rhapsody.core.IRPGraphElement;
import com.telelogic.rhapsody.core.IRPModelElement;
import com.telelogic.rhapsody.core.IRPSysMLPort;

import tools.RedefinedPortsService;

import static test.unittest.RhpTestMocks.collectionOf;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests (no live Rhapsody) of the purely graphical side of
 * {@link RedefinedPortsService}: which port graphics of a reference are
 * hidden, and the list variant of {@code hideRedefinedPorts}. The model is
 * never changed by these helpers.
 *
 * <p>A diagram is a mock whose {@code getGraphicalElements()} lists the port
 * graphics (children of the reference graphic) and the flow edges.</p>
 */
class RedefinedPortsServiceDiagramTest {

    private static final String REF_FUNCTION = "References Function";

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    /** Graphic of a port model element, child of {@code parent}. */
    private static IRPGraphElement portGraphic(IRPGraphElement parent, IRPModelElement model) {
        IRPGraphElement g = mock(IRPGraphElement.class);
        when(g.getGraphicalParent()).thenReturn(parent);
        when(g.getModelObject()).thenReturn(model);
        return g;
    }

    /** A "Flow Port" model element redefining {@code redefined}. */
    private static IRPSysMLPort flowPort(String name, IRPModelElement owner, IRPModelElement... redefined) {
        IRPSysMLPort p = mock(IRPSysMLPort.class);
        when(p.getName()).thenReturn(name);
        when(p.getUserDefinedMetaClass()).thenReturn("Flow Port");
        when(p.getOwner()).thenReturn(owner);
        doReturn(collectionOf((Object[]) redefined)).when(p).getRedefines();
        return p;
    }

    private static IRPClass clazz(String guid) {
        IRPClass c = mock(IRPClass.class);
        when(c.getGUID()).thenReturn(guid);
        doReturn(collectionOf()).when(c).getGeneralizations();
        return c;
    }

    private static IRPGeneralization generalization(String udmc, IRPClassifier base) {
        IRPGeneralization g = mock(IRPGeneralization.class);
        when(g.getUserDefinedMetaClass()).thenReturn(udmc);
        when(g.getBaseClass()).thenReturn(base);
        return g;
    }

    private static IRPGraphEdge edge(IRPGraphElement src, IRPGraphElement tgt, IRPModelElement model) {
        IRPGraphEdge e = mock(IRPGraphEdge.class);
        when(e.getSource()).thenReturn(src);
        when(e.getTarget()).thenReturn(tgt);
        when(e.getModelObject()).thenReturn(model);
        return e;
    }

    private static IRPFlow flow(String guid) {
        IRPFlow f = mock(IRPFlow.class);
        when(f.getGUID()).thenReturn(guid);
        return f;
    }

    /** Graphic of {@code model} in {@code diagram}. */
    private static IRPGraphElement nodeIn(IRPDiagram diagram, IRPModelElement model) {
        IRPGraphElement node = mock(IRPGraphElement.class);
        when(node.getDiagram()).thenReturn(diagram);
        when(node.getModelObject()).thenReturn(model);
        return node;
    }

    private static void diagramShows(IRPDiagram diagram, Object... graphics) {
        doReturn(collectionOf(graphics)).when(diagram).getGraphicalElements();
    }

    // ------------------------------------------------------------------
    // portsToHide (port graphic detection)
    // ------------------------------------------------------------------

    @Test
    void portsToHide_keepsOnlyTheRedefinedMotherPortsOfThisNode() {
        IRPDiagram diagram = mock(IRPDiagram.class);
        IRPGraphElement refNode = nodeIn(diagram, mock(IRPClass.class));
        IRPGraphElement otherNode = nodeIn(diagram, mock(IRPClass.class));

        IRPSysMLPort motherPort = flowPort("P1", null);
        IRPSysMLPort mirror = flowPort("P1", null, motherPort);
        IRPModelElement sysmlPort = mock(IRPModelElement.class); // plain SysMLPort metaclass
        when(sysmlPort.getMetaClass()).thenReturn("SysMLPort");
        doReturn(collectionOf()).when(sysmlPort).getRedefines();
        IRPModelElement notAPort = mock(IRPModelElement.class);
        when(notAPort.getMetaClass()).thenReturn("Attribute");

        IRPGraphElement gMother = portGraphic(refNode, motherPort);
        IRPGraphElement gMirror = portGraphic(refNode, mirror);
        IRPGraphElement gSysml = portGraphic(refNode, sysmlPort);
        IRPGraphElement gAttr = portGraphic(refNode, notAPort);
        IRPGraphElement gNoModel = portGraphic(refNode, null);
        IRPGraphElement gTopLevel = portGraphic(null, motherPort);
        IRPGraphElement gOnOtherNode = portGraphic(otherNode, motherPort);
        diagramShows(diagram, gMother, gMirror, gSysml, gAttr, gNoModel, gTopLevel, gOnOtherNode,
                "not a graphic");

        List<IRPGraphElement> hidden = RedefinedPortsService.portsToHide(refNode);

        assertEquals(List.of(gMother), hidden);
    }

    @Test
    void portsToHide_nodeWithoutDiagramOrModel_givesNothing() {
        IRPGraphElement noDiagram = mock(IRPGraphElement.class);
        when(noDiagram.getModelObject()).thenReturn(mock(IRPClass.class));
        IRPGraphElement noModel = nodeIn(mock(IRPDiagram.class), null);

        assertTrue(RedefinedPortsService.portsToHide(noDiagram).isEmpty());
        assertTrue(RedefinedPortsService.portsToHide(noModel).isEmpty());
        assertTrue(RedefinedPortsService.portsToHide(null).isEmpty());
    }

    // ------------------------------------------------------------------
    // hideRedefinedPorts (list of representations)
    // ------------------------------------------------------------------

    @Test
    void hideRedefinedPortsList_removesMotherPortGraphics_fromTheDiagram() {
        IRPApplication app = mock(IRPApplication.class);
        IRPCollection removal = mock(IRPCollection.class);
        when(app.createNewCollection()).thenReturn(removal);
        IRPDiagram diagram = mock(IRPDiagram.class);
        IRPGraphElement refNode = nodeIn(diagram, mock(IRPClass.class));
        IRPSysMLPort motherPort = flowPort("P1", null);
        IRPSysMLPort mirror = flowPort("P1", null, motherPort);
        IRPGraphElement gMother = portGraphic(refNode, motherPort);
        IRPGraphElement gMirror = portGraphic(refNode, mirror);
        diagramShows(diagram, gMother, gMirror);

        int removed = RedefinedPortsService.hideRedefinedPorts(app, Arrays.asList(refNode, null));

        assertEquals(1, removed);
        verify(removal).addGraphicalItem(gMother);
        verify(removal, never()).addGraphicalItem(gMirror);
        verify(diagram).removeGraphElements(removal);
    }

    @Test
    void hideRedefinedPortsList_nothingRedefined_orNoRepresentation_returnsZero() {
        IRPApplication app = mock(IRPApplication.class);
        IRPDiagram diagram = mock(IRPDiagram.class);
        IRPGraphElement refNode = nodeIn(diagram, mock(IRPClass.class));
        diagramShows(diagram, portGraphic(refNode, flowPort("P1", null)));

        assertEquals(0, RedefinedPortsService.hideRedefinedPorts(app, List.of(refNode)));
        assertEquals(0, RedefinedPortsService.hideRedefinedPorts(app, new ArrayList<IRPGraphElement>()));
        assertEquals(0, RedefinedPortsService.hideRedefinedPorts(app, (List<IRPGraphElement>) null));
        verify(app, never()).createNewCollection();
    }

    @Test
    void hideRedefinedPortsList_noCollection_removesNothing() {
        IRPApplication app = mock(IRPApplication.class);
        when(app.createNewCollection()).thenReturn(null);
        IRPDiagram diagram = mock(IRPDiagram.class);
        IRPGraphElement refNode = nodeIn(diagram, mock(IRPClass.class));
        IRPSysMLPort motherPort = flowPort("P1", null);
        diagramShows(diagram, portGraphic(refNode, motherPort),
                portGraphic(refNode, flowPort("P1", null, motherPort)));

        RedefinedPortsService.hideRedefinedPorts(app, List.of(refNode));

        verify(diagram, never()).removeGraphElements(any());
    }

    // ------------------------------------------------------------------
    // inheritedGraphicalPorts
    // ------------------------------------------------------------------

    @Test
    void inheritedGraphicalPorts_keepsPortsOwnedByTheMotherOrItsAncestors() {
        IRPClass base = clazz("GUID-ig-base");
        IRPClass mother = clazz("GUID-ig-mother");
        doReturn(collectionOf(generalization("Redefines Function", base))).when(mother).getGeneralizations();
        IRPClass reference = clazz("GUID-ig-ref");
        doReturn(collectionOf(generalization(REF_FUNCTION, mother))).when(reference).getGeneralizations();

        IRPDiagram diagram = mock(IRPDiagram.class);
        IRPGraphElement refNode = nodeIn(diagram, reference);
        IRPGraphElement gMother = portGraphic(refNode, flowPort("M1", mother));
        IRPGraphElement gBase = portGraphic(refNode, flowPort("B1", base));
        IRPGraphElement gOwn = portGraphic(refNode, flowPort("R1", reference));
        IRPGraphElement gUnowned = portGraphic(refNode, flowPort("U1", null));
        diagramShows(diagram, gMother, gBase, gOwn, gUnowned);

        assertEquals(List.of(gMother, gBase), RedefinedPortsService.inheritedGraphicalPorts(refNode));
    }

    @Test
    void inheritedGraphicalPorts_notAReference_givesNothing() {
        IRPDiagram diagram = mock(IRPDiagram.class);
        IRPGraphElement plainClassNode = nodeIn(diagram, clazz("GUID-ig-plain"));
        IRPGraphElement nonClassNode = nodeIn(diagram, mock(IRPModelElement.class));
        IRPGraphElement unreadable = mock(IRPGraphElement.class);
        when(unreadable.getModelObject()).thenThrow(new RuntimeException("COM"));

        assertTrue(RedefinedPortsService.inheritedGraphicalPorts(plainClassNode).isEmpty());
        assertTrue(RedefinedPortsService.inheritedGraphicalPorts(nonClassNode).isEmpty());
        assertTrue(RedefinedPortsService.inheritedGraphicalPorts(unreadable).isEmpty());
        assertTrue(RedefinedPortsService.inheritedGraphicalPorts(null).isEmpty());
    }

    // ------------------------------------------------------------------
    // getGraphicalRepresentations: skipped and failing references
    // ------------------------------------------------------------------

    @Test
    void getGraphicalRepresentations_skipsNonDiagramsAndUnreadableDiagrams() {
        IRPModelElement el = mock(IRPModelElement.class);
        IRPGraphElement ok = mock(IRPGraphElement.class);
        IRPDiagram good = mock(IRPDiagram.class);
        doReturn(collectionOf(ok, "not a graphic")).when(good).getCorrespondingGraphicElements(el);
        IRPDiagram failing = mock(IRPDiagram.class);
        when(failing.getCorrespondingGraphicElements(el)).thenThrow(new RuntimeException("COM"));
        IRPDiagram empty = mock(IRPDiagram.class);
        doReturn(collectionOf()).when(empty).getCorrespondingGraphicElements(el);
        IRPDiagram none = mock(IRPDiagram.class); // returns null
        doReturn(collectionOf(mock(IRPModelElement.class), failing, empty, none, good))
                .when(el).getReferences();

        assertEquals(List.of(ok), RedefinedPortsService.getGraphicalRepresentations(el));
    }

    @Test
    void getGraphicalRepresentations_unreadableReferences_givesEmptyList() {
        IRPModelElement el = mock(IRPModelElement.class);
        when(el.getReferences()).thenThrow(new RuntimeException("COM"));

        assertTrue(RedefinedPortsService.getGraphicalRepresentations(el).isEmpty());
    }
}
