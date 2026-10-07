package test.unittest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

import com.telelogic.rhapsody.core.IRPDiagram;
import com.telelogic.rhapsody.core.IRPGraphElement;
import com.telelogic.rhapsody.core.IRPSysMLPort;

import main.constants.Types.Direction;
import test.RhapsodyBaseTest;
import tools.PropagatePortProperties;

public class DirectionPortPropagationTest extends RhapsodyBaseTest {

    private PropagatePortProperties tool;
    private IRPDiagram logicalDiagram;

    public DirectionPortPropagationTest() {
        super("./src/test/resources/PortPropagate/PortPropagate.rpyx");
        // IMPORTANT: ne pas instancier tool ici (rhapsodyApplication peut ne pas être prêt)
    }

    @BeforeEach
    @Override
    public void setUp() {
        super.setUp();

        // Init tool APRES init Rhapsody (sinon risque de NPE / log loop / state incomplet)
        tool = new PropagatePortProperties(rhapsodyApplication);

        logicalDiagram = (IRPDiagram) project.findElementByGUID("GUID 806e0920-8eb1-4648-8710-1d1236e6bd0b");
        Assertions.assertNotNull(logicalDiagram, "Logical diagram not found");
        logicalDiagram.openDiagram();
    }

    /**
     * Selected object: input flowport_12 on the frame of the logical system to port of a direct logical system child
     */
    @Test
    public void InputFramePortToPortPropagateDirection() {
        IRPSysMLPort selectedPort =
            (IRPSysMLPort) project.findElementByGUID("GUID 19b5ae8c-41cf-441d-9037-2284fd3dcc9d");
        Assertions.assertNotNull(selectedPort, "No Selected Object");

        IRPSysMLPort modifiedTarget =
            (IRPSysMLPort) project.findElementByGUID("GUID 226d9a20-5f97-435b-9988-f5631eb391d6");
        Assertions.assertNotNull(modifiedTarget, "No Target Object");

        setDirectionOnlyConfig();
        selectPortForTool(selectedPort);

        tool.execute();

        Assertions.assertEquals(Direction.IN, modifiedTarget.getPortDirection());
    }

    @Test
    public void InputFramePortToNestedPortPropagateDirection() {
        IRPSysMLPort selectedPort =
            (IRPSysMLPort) project.findElementByGUID("GUID 6654d91e-bfcb-4ae2-a748-70ffb70964ab");
        Assertions.assertNotNull(selectedPort, "No Selected Object");

        IRPSysMLPort modifiedTarget =
            (IRPSysMLPort) project.findElementByGUID("GUID 30647911-a735-4fae-bb1f-2083a93ba8d8");
        Assertions.assertNotNull(modifiedTarget, "No Target Object");

        IRPGraphElement selectedGraphElement =
            (IRPGraphElement) logicalDiagram.getCorrespondingGraphicElements(selectedPort).getItem(1);
        selectGraphElementInRhapsody(selectedGraphElement);

        setDirectionOnlyConfig();

        tool.execute();

        Assertions.assertEquals(Direction.IN, modifiedTarget.getPortDirection());
    }

    @Test
    public void OutputPortToFramePortPropagateDirection() {
        IRPSysMLPort selectedPort =
            (IRPSysMLPort) project.findElementByGUID("GUID 4b987159-9911-43b7-9786-e152a526ffc2");
        Assertions.assertNotNull(selectedPort, "No Selected Object");

        IRPSysMLPort modifiedTarget =
            (IRPSysMLPort) project.findElementByGUID("GUID 180c8f5e-9cc8-4d90-9ce8-db93d3bbe7c4");
        Assertions.assertNotNull(modifiedTarget, "No Target Object");

        IRPGraphElement selectedGraphElement =
            (IRPGraphElement) logicalDiagram.getCorrespondingGraphicElements(selectedPort).getItem(1);
        selectGraphElementInRhapsody(selectedGraphElement);

        setDirectionOnlyConfig();

        tool.execute();

        Assertions.assertEquals(Direction.OUT, modifiedTarget.getPortDirection());
    }

    @Test
    public void OutputPortToPortPropagateDirection() {
        IRPSysMLPort selectedPort =
            (IRPSysMLPort) project.findElementByGUID("GUID f7e44065-d1a1-4b61-8ffb-5bd444619a31");
        Assertions.assertNotNull(selectedPort, "No Selected Object");

        IRPSysMLPort modifiedTarget =
            (IRPSysMLPort) project.findElementByGUID("GUID 90a6799d-26aa-41e1-99ff-22bef74858af");
        Assertions.assertNotNull(modifiedTarget, "No Target Object");

        IRPGraphElement selectedGraphElement =
            (IRPGraphElement) logicalDiagram.getCorrespondingGraphicElements(selectedPort).getItem(1);
        selectGraphElementInRhapsody(selectedGraphElement);

        setDirectionOnlyConfig();

        tool.execute();

        Assertions.assertEquals(Direction.IN, modifiedTarget.getPortDirection());
    }

    @Test
    public void NestedOutputPortToFramePortPropagateDirection() {
        IRPSysMLPort selectedPort =
            (IRPSysMLPort) project.findElementByGUID("GUID c095baa8-f2d3-4b00-8aa5-18c32bb381db");
        Assertions.assertNotNull(selectedPort, "No Selected Object");

        IRPSysMLPort modifiedTarget =
            (IRPSysMLPort) project.findElementByGUID("GUID 70f5b6b3-9283-4c35-b3d9-f2dadbb34c86");
        Assertions.assertNotNull(modifiedTarget, "No Target Object");

        IRPGraphElement selectedGraphElement =
            (IRPGraphElement) logicalDiagram.getCorrespondingGraphicElements(selectedPort).getItem(1);
        selectGraphElementInRhapsody(selectedGraphElement);

        setDirectionOnlyConfig();

        tool.execute();

        Assertions.assertEquals(Direction.OUT, modifiedTarget.getPortDirection());
    }

    @Test
    public void NestedOutputPortToPortPropagateDirection() {
        IRPSysMLPort selectedPort =
            (IRPSysMLPort) project.findElementByGUID("GUID 5cb20744-25ff-43d9-9a2a-f004d8e3c2bc");
        Assertions.assertNotNull(selectedPort, "No Selected Object");

        IRPSysMLPort modifiedTarget =
            (IRPSysMLPort) project.findElementByGUID("GUID 8f506f62-8131-42d7-bcb1-721aea227b9d");
        Assertions.assertNotNull(modifiedTarget, "No Target Object");

        setDirectionOnlyConfig();
        selectPortForTool(selectedPort);

        tool.execute();

        Assertions.assertEquals(Direction.IN, modifiedTarget.getPortDirection());
    }

    @Test
    public void NestedOutputPortToParentPortPropagateDirection() {
        IRPSysMLPort selectedPort =
            (IRPSysMLPort) project.findElementByGUID("GUID b42d099a-1036-4c28-ad13-c0914ae52b55");
        Assertions.assertNotNull(selectedPort, "No Selected Object");

        IRPSysMLPort modifiedTarget =
            (IRPSysMLPort) project.findElementByGUID("GUID bb4c3f4c-4057-4cb7-b78c-940443f36921");
        Assertions.assertNotNull(modifiedTarget, "No Target Object");

        IRPGraphElement selectedGraphElement =
            (IRPGraphElement) logicalDiagram.getCorrespondingGraphicElements(selectedPort).getItem(1);
        selectGraphElementInRhapsody(selectedGraphElement);

        setDirectionOnlyConfig();

        tool.execute();

        Assertions.assertEquals(Direction.OUT, modifiedTarget.getPortDirection());
    }

    @Test
    public void FrameBiDirectionalPortToPortPropagateDirection() {
        IRPSysMLPort selectedPort =
            (IRPSysMLPort) project.findElementByGUID("GUID 3e6d3be8-8444-4cfa-9e47-19008c06c7cf");
        Assertions.assertNotNull(selectedPort, "No Selected Object");

        IRPSysMLPort modifiedTarget =
            (IRPSysMLPort) project.findElementByGUID("GUID 53fd4e00-71c7-49f8-bf9f-8c9eaf3cf016");
        Assertions.assertNotNull(modifiedTarget, "No Target Object");

        IRPGraphElement selectedGraphElement =
            (IRPGraphElement) logicalDiagram.getCorrespondingGraphicElements(selectedPort).getItem(1);
        selectGraphElementInRhapsody(selectedGraphElement);

        setDirectionOnlyConfig();

        tool.execute();

        Assertions.assertEquals(Direction.IN_OUT, modifiedTarget.getPortDirection());
    }

    @Test
    public void BiDirectionalPortToFramePortPropagateDirection() {
        IRPSysMLPort selectedPort =
            (IRPSysMLPort) project.findElementByGUID("GUID e0c99915-670f-479f-a57c-294369485553");
        Assertions.assertNotNull(selectedPort, "No Selected Object");

        IRPSysMLPort modifiedTarget =
            (IRPSysMLPort) project.findElementByGUID("GUID 4c0ca6e7-a5ea-465f-a284-cf34a13d10bd");
        Assertions.assertNotNull(modifiedTarget, "No Target Object");

        IRPGraphElement selectedGraphElement =
            (IRPGraphElement) logicalDiagram.getCorrespondingGraphicElements(selectedPort).getItem(1);
        selectGraphElementInRhapsody(selectedGraphElement);

        setDirectionOnlyConfig();

        tool.execute();

        Assertions.assertEquals(Direction.IN_OUT, modifiedTarget.getPortDirection());
    }

    @Test
    public void BiDirectionalPortToPortPropagateDirection() {
        IRPSysMLPort selectedPort =
            (IRPSysMLPort) project.findElementByGUID("GUID c4ce1eeb-4958-47d7-ac3d-79fa35d85289");
        Assertions.assertNotNull(selectedPort, "No Selected Object");

        IRPSysMLPort modifiedTarget =
            (IRPSysMLPort) project.findElementByGUID("GUID fcf73e38-6c05-4f35-80c7-6534af3fc1ae");
        Assertions.assertNotNull(modifiedTarget, "No Target Object");

        IRPGraphElement selectedGraphElement =
            (IRPGraphElement) logicalDiagram.getCorrespondingGraphicElements(selectedPort).getItem(1);
        selectGraphElementInRhapsody(selectedGraphElement);

        setDirectionOnlyConfig();

        tool.execute();

        Assertions.assertEquals(Direction.IN_OUT, modifiedTarget.getPortDirection());
    }

    private void setDirectionOnlyConfig() {
        project.setPropertyValue("SafranToolsSettings.PortPropagation.NamePropagation", "False");
        project.setPropertyValue("SafranToolsSettings.PortPropagation.LabelPropagation", "False");
        project.setPropertyValue("SafranToolsSettings.PortPropagation.DescriptionPropagation", "False");
        project.setPropertyValue("SafranToolsSettings.PortPropagation.StereotypesPropagation", "False");

        project.setPropertyValue("SafranToolsSettings.PortPropagation.TypePropagation", "True");
        project.setPropertyValue("SafranToolsSettings.PortPropagation.DirectionPropagation", "True");

        project.setPropertyValue("SafranToolsSettings.PortPropagation.ConveysFlowUpdate", "False");
    }

    private void selectPortForTool(IRPSysMLPort port) {
        logicalDiagram.openDiagram();

        IRPGraphElement ge =
            (IRPGraphElement) logicalDiagram.getCorrespondingGraphicElements(port).getItem(1);
        selectGraphElementInRhapsody(ge);

        if (!rhapsodyApplication.getListOfSelectedElements().toList().contains(port)) {
            selectModelElementInRhapsody(port);
            logicalDiagram.openDiagram();
            selectGraphElementInRhapsody(ge);
        }
    }
}