package test.unittest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;

import com.telelogic.rhapsody.core.IRPCollection;
import com.telelogic.rhapsody.core.IRPDiagram;
import com.telelogic.rhapsody.core.IRPFlow;
import com.telelogic.rhapsody.core.IRPGraphElement;
import com.telelogic.rhapsody.core.IRPModelElement;
import com.telelogic.rhapsody.core.IRPStereotype;
import com.telelogic.rhapsody.core.IRPSysMLPort;
import main.constants.ProfilConstants;
import test.RhapsodyBaseTest;
import tools.PropagatePortProperties;

public class PortPropagationTest extends RhapsodyBaseTest {

    private static final String MODEL_PATH =
            "./src/test/resources/PortPropagate/PortPropagate.rpyx";

    // ⚠️ Reprend le diagram GUID que tu utilises déjà ailleurs
    private static final String LOGICAL_DIAGRAM_GUID =
            "GUID a90510a5-51d2-4df7-ba52-f861f85d2b41";

    private PropagatePortProperties tool;
    private IRPDiagram logicalDiagram;

    public PortPropagationTest() {
        super(MODEL_PATH);
    }

    @BeforeEach
    void initToolAndDiagram() {
        // project est déjà chargé par RhapsodyBaseTest.@BeforeEach
        tool = new PropagatePortProperties(rhapsodyApplication);

        logicalDiagram = (IRPDiagram) project.findElementByGUID(LOGICAL_DIAGRAM_GUID);
        Assertions.assertNotNull(logicalDiagram, "Logical diagram not found: " + LOGICAL_DIAGRAM_GUID);

        // Important : diagram ouvert => getDiagramOfSelectedElement + border detect plus fiables
        logicalDiagram.openDiagram();
    }

    @Test
    public void propagatePortProperties() {
        // Arrange
        IRPSysMLPort selectedPort = (IRPSysMLPort)
                project.findElementByGUID(("GUID 2dc352b1-eba8-4925-9234-938011674bca"));
        Assertions.assertNotNull(selectedPort, "No Selected Object");

        IRPModelElement expectedType =
                project.findElementByGUID(("GUID 3cd6e0a3-8153-4248-8244-66fbee624ddf"));
        Assertions.assertNotNull(expectedType, "Expected type element not found");

        IRPSysMLPort sourcePort = (IRPSysMLPort)
                project.findElementByGUID(("GUID 6ae66bd4-be00-447d-9529-0340a17f5727"));
        Assertions.assertNotNull(sourcePort, "No Source Object");

        IRPSysMLPort modifiedTarget = (IRPSysMLPort)
                project.findElementByGUID(("GUID 73e81df5-9708-477a-9c99-0b1f96a8386c"));
        Assertions.assertNotNull(modifiedTarget, "No Target Object");

        IRPSysMLPort connectedTarget = (IRPSysMLPort)
                project.findElementByGUID(("GUID d73755d4-affe-4493-801c-a478dd23066b"));
        Assertions.assertNotNull(connectedTarget, "No Connected Target Object");

        setFullPropagationConfig();
        project.setPropertyValue("SafranToolsSettings.PortPropagation.NamePropagation", "False");
        project.setPropertyValue("SafranToolsSettings.PortPropagation.LabelPropagation", "False");

        // Sélection (graph si possible) => diagram actif pour border detect
        selectPortForTool(selectedPort);

        // Vérifie que la propagation est bien "globale" (hors diagramme actif)
        // Au moins un des ports cibles (modifiedTarget / connectedTarget) doit être absent graphiquement sur logicalDiagram.
        IRPCollection g1 = logicalDiagram.getCorrespondingGraphicElements(modifiedTarget);
        IRPCollection g2 = logicalDiagram.getCorrespondingGraphicElements(connectedTarget);
        boolean modifiedAbsent = (g1 == null || g1.getCount() == 0);
        boolean connectedAbsent = (g2 == null || g2.getCount() == 0);
        Assertions.assertTrue(modifiedAbsent || connectedAbsent,
                "Test invalide : au moins une cible doit \u00EAtre hors diagramme actif (multi-diagrammes)");

        // Calcul des ports réellement connectés via flows (références du selectedPort)
        List<IRPSysMLPort> connected = connectedPortsViaFlows(selectedPort);

        // sanity : on attend que ces ports soient réellement connectés
        Assertions.assertTrue(connected.stream().anyMatch(p -> p.equals(modifiedTarget)),
                "modifiedTarget n'est pas connect\u00E9 au selectedPort via un IRPFlow (r\u00E9f\u00E9rences)");
        Assertions.assertTrue(connected.stream().anyMatch(p -> p.equals(connectedTarget)),
                "connectedTarget (ex-unModifiedTarget) n'est pas connect\u00E9 au selectedPort via un IRPFlow (r\u00E9f\u00E9rences)");
        Assertions.assertTrue(connected.stream().anyMatch(p -> p.equals(sourcePort)),
                "sourcePort n'est pas connect\u00E9 au selectedPort via un IRPFlow (r\u00E9f\u00E9rences)");

        String selectedDir = selectedPort.getPortDirection();
        String expectedPeerDir = flipDir(selectedDir); // External1 vs SOI => peers => flip attendu

        // Act
        tool.execute();

        // Assert : tous les ports connectés sont mis à jour (propagation globale)
        // 1) ports internes (delegation) : même direction que selectedPort
        Assertions.assertEquals(expectedType, modifiedTarget.getType(), "Type should be propagated to modifiedTarget");
        Assertions.assertEquals(selectedDir, modifiedTarget.getPortDirection(), "Direction should match selectedPort (delegation)");
        Assertions.assertTrue(hasStereotypeByGuid(modifiedTarget, ProfilConstants.GUID_SAFRAN_PNEUMATIC),
                "modifiedTarget should have stereotype PNEUMATIC");
        Assertions.assertEquals(selectedPort.getDescription(), modifiedTarget.getDescription(),
                "Description should be propagated to modifiedTarget");

        Assertions.assertEquals(expectedType, connectedTarget.getType(), "Type should be propagated to connectedTarget");
        Assertions.assertEquals(selectedDir, connectedTarget.getPortDirection(), "Direction should match selectedPort (delegation)");
        Assertions.assertTrue(hasStereotypeByGuid(connectedTarget, ProfilConstants.GUID_SAFRAN_PNEUMATIC),
                "connectedTarget should have stereotype PNEUMATIC");
        Assertions.assertEquals(selectedPort.getDescription(), connectedTarget.getDescription(),
                "Description should be propagated to connectedTarget");

        // 2) port externe (peer) : flip attendu
        Assertions.assertEquals(expectedType, sourcePort.getType(), "Type should be propagated to sourcePort");
        Assertions.assertEquals(expectedPeerDir, sourcePort.getPortDirection(), "sourcePort direction should be flipped vs selectedPort");
        Assertions.assertTrue(hasStereotypeByGuid(sourcePort, ProfilConstants.GUID_SAFRAN_PNEUMATIC),
                "sourcePort should have stereotype PNEUMATIC");
    }

    @Test
    public void propagateDirection() {
        // Arrange
        IRPSysMLPort innerPort = (IRPSysMLPort)
                project.findElementByGUID("GUID 91f31b7f-28bc-4889-9ac3-057f4bad957f");
        Assertions.assertNotNull(innerPort, "No Inner port");

        IRPSysMLPort outerPort = (IRPSysMLPort)
                project.findElementByGUID("GUID 9d74caf8-097b-475c-b800-c927338faa58");
        Assertions.assertNotNull(outerPort, "No Outer port");

        setDirectionOnlyConfig();
        selectPortForTool(innerPort);

        // (optionnel debug)
        System.out.println("SRC=" + innerPort.getFullPathName() + " dir=" + innerPort.getPortDirection());
        System.out.println("TGT=" + outerPort.getFullPathName() + " dir(before)=" + outerPort.getPortDirection());

        // Act
        tool.execute();

        // Assert
        System.out.println("TGT dir(after)=" + outerPort.getPortDirection());
        Assertions.assertEquals("Out", outerPort.getPortDirection(),
                "Outer port direction should be Out");
    }

    // -----------------------
    // Helpers
    // -----------------------

    private void setDirectionOnlyConfig() {
        project.setPropertyValue("SafranToolsSettings.PortPropagation.NamePropagation", "False");
        project.setPropertyValue("SafranToolsSettings.PortPropagation.LabelPropagation", "False");
        project.setPropertyValue("SafranToolsSettings.PortPropagation.DescriptionPropagation", "False");
        project.setPropertyValue("SafranToolsSettings.PortPropagation.StereotypesPropagation", "False");

        project.setPropertyValue("SafranToolsSettings.PortPropagation.TypePropagation", "True");
        project.setPropertyValue("SafranToolsSettings.PortPropagation.DirectionPropagation", "True");

        // évite les effets de bord
        project.setPropertyValue("SafranToolsSettings.PortPropagation.ConveysFlowUpdate", "False");
    }

    private void setFullPropagationConfig() {
        project.setPropertyValue("SafranToolsSettings.PortPropagation.NamePropagation", "True");
        project.setPropertyValue("SafranToolsSettings.PortPropagation.LabelPropagation", "True");
        project.setPropertyValue("SafranToolsSettings.PortPropagation.DescriptionPropagation", "True");
        project.setPropertyValue("SafranToolsSettings.PortPropagation.StereotypesPropagation", "True");
        project.setPropertyValue("SafranToolsSettings.PortPropagation.TypePropagation", "True");
        project.setPropertyValue("SafranToolsSettings.PortPropagation.DirectionPropagation", "True");

        // selon ta MAJ : neutraliser conveyed si pas testé ici
        project.setPropertyValue("SafranToolsSettings.PortPropagation.ConveysFlowUpdate", "False");
    }

    private void selectPortForTool(IRPSysMLPort port) {
        // 1) diagram doit être actif
        if (logicalDiagram != null) {
            try { logicalDiagram.openDiagram(); } catch (Throwable ignore) {}
        }

        // 2) sélection graphique si possible (le tool s’appuie souvent sur l’active diagram)
        IRPGraphElement ge = tryGetGraphElement(logicalDiagram, port);
        if (ge != null) {
            selectGraphElementInRhapsody(ge);
            return;
        }

        // 3) fallback : sélection modèle
        selectModelElementInRhapsody(port);

        // 4) re-tente une sélection graphique après (si l’API se “réveille”)
        ge = tryGetGraphElement(logicalDiagram, port);
        if (ge != null) {
            try { logicalDiagram.openDiagram(); } catch (Throwable ignore) {}
            selectGraphElementInRhapsody(ge);
        }
    }

    private IRPGraphElement tryGetGraphElement(IRPDiagram diagram, IRPSysMLPort port) {
        if (diagram == null || port == null) return null;
        try {
            var col = diagram.getCorrespondingGraphicElements(port);
            if (col == null || col.getCount() == 0) return null;
            Object item = col.getItem(1); // 1-based
            return (item instanceof IRPGraphElement) ? (IRPGraphElement) item : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private boolean hasStereotypeByGuid(IRPSysMLPort port, String stereotypeGuid) {
        if (port == null || stereotypeGuid == null) return false;
        try {
            for (Object o : port.getStereotypes().toList()) {
                if (o instanceof IRPStereotype) {
                    IRPStereotype st = (IRPStereotype) o;
                    if (stereotypeGuid.equals(st.getGUID())) return true;
                }
            }
        } catch (Throwable ignore) {}
        return false;
    }

    private void assertUnmodified(IRPSysMLPort selectedPort, IRPSysMLPort unModifiedTarget) {
        // Type
        Assertions.assertNotEquals(selectedPort.getType(), unModifiedTarget.getType(),
                "Unmodified target type changed");

        // Stereotype
        Assertions.assertFalse(
                hasStereotypeByGuid(unModifiedTarget, ProfilConstants.GUID_SAFRAN_PNEUMATIC),
                "Unmodified target stereotype changed"
        );

        // Name and Label
        Assertions.assertNotEquals(selectedPort.getName(), unModifiedTarget.getName(),
                "Unmodified target name changed");
        Assertions.assertNotEquals(selectedPort.getDisplayName(), unModifiedTarget.getDisplayName(),
                "Unmodified target display name changed");
    }

    private void assertSourcePort(IRPSysMLPort selectedPort, IRPSysMLPort sourcePort) {
        Assertions.assertEquals(selectedPort.getType(), sourcePort.getType(), "Source type mismatch");
        Assertions.assertEquals("In", sourcePort.getPortDirection(), "Source direction mismatch");

        Assertions.assertTrue(
                hasStereotypeByGuid(sourcePort, ProfilConstants.GUID_SAFRAN_PNEUMATIC),
                "Source should have stereotype PNEUMATIC"
        );
    }
    
 // ---- à ajouter dans PortPropagationTest ----

    private static String gid(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.startsWith("GUID")) t = t.substring(4).trim();
        if (t.startsWith("{") && t.endsWith("}")) t = t.substring(1, t.length() - 1).trim();
        return t;
    }

    private List<IRPSysMLPort> connectedPortsViaFlows(IRPSysMLPort selectedPort) {
        List<IRPSysMLPort> targets = new ArrayList<>();
        try {
            for (Object o : selectedPort.getReferences().toList()) {
                if (!(o instanceof IRPFlow flow)) continue;

                IRPSysMLPort e1 = flow.getEnd1SysMLPort();
                IRPSysMLPort e2 = flow.getEnd2SysMLPort();
                if (e1 == null || e2 == null) continue;

                if (e1.equals(selectedPort)) targets.add(e2);
                else if (e2.equals(selectedPort)) targets.add(e1);
            }
        } catch (Throwable ignore) {}
        return targets;
    }

    private String flipDir(String d) {
        if (d == null) return null;
        if ("InOut".equalsIgnoreCase(d)) return "InOut";
        if ("In".equalsIgnoreCase(d)) return "Out";
        if ("Out".equalsIgnoreCase(d)) return "In";
        return d;
    }
}