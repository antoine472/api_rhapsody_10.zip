package test.unittest;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.telelogic.rhapsody.core.IRPClass;
import com.telelogic.rhapsody.core.IRPCollection;
import com.telelogic.rhapsody.core.IRPDiagram;
import com.telelogic.rhapsody.core.IRPPackage;

import test.RhapsodyBaseTest;
import tools.GenerateLBS;

public class LogicalBreakdownStructureTest extends RhapsodyBaseTest {

    private GenerateLBS tool;

    public LogicalBreakdownStructureTest() {
        super("./src/test/resources/LogicalBreakdownStructure/LogicalBreakdownStructure.rpyx");
        //  NE PAS initialiser 'tool' ici : rhapsodyApplication est encore null
    }

    /**
     *  Surcharge de setUp() pour initialiser 'tool' APRÈS @BeforeAll
     * (rhapsodyApplication est garanti non-null à ce stade)
     */
    @Override
    @BeforeEach
    public void setUp() {
        super.setUp(); // charge le project via loadProject()
        tool = new GenerateLBS(rhapsodyApplication); //  safe ici
    }

    /**
     * Selected object: Function with nested functions and referenced function
     */
    @Test //  org.junit.jupiter.api.Test
    public void logicalsystemWithNestedLogicalAndReferencedSystem() {
        // Arrange
        IRPClass selectedLogicalsystemmain =
            (IRPClass) project.findElementByGUID("GUID 8435bea3-4f79-4c53-adb4-2ab2a9b55075");
        Assertions.assertNotNull(selectedLogicalsystemmain, "No Selected Object"); //  JUnit 5

        IRPClass nestedlogicalsystemchild1 =
            (IRPClass) project.findElementByGUID("GUID 69855bc9-39d7-4163-bdf8-3dd1c2042898");
        Assertions.assertNotNull(nestedlogicalsystemchild1, "No Selected Object");

        IRPClass nestedlogicalsystemchild2 =
            (IRPClass) project.findElementByGUID("GUID 823d22e9-8a80-4cec-a06a-8114125bede3");
        Assertions.assertNotNull(nestedlogicalsystemchild2, "No Selected Object");
        
        IRPClass nestedlogicalsystemwithreference_1 =
                (IRPClass) project.findElementByGUID("GUID 283c7f27-9a88-4e75-864d-33b54b282c23");
            Assertions.assertNotNull(nestedlogicalsystemwithreference_1, "No Selected Object");
            
            // TODO : To be updated after Rhapsody 10.0.3 /!\
//        IRPCollection modelElements = rhapsodyApplication.createNewCollection();
//        modelElements.addItem(selectedLogicalsystemmain);
//        rhapsodyApplication.selectModelElements(modelElements);
           
        selectedLogicalsystemmain.locateInBrowser();
        
        // Act
        tool.execute();

        // Assert
        IRPPackage logicalPackage = (IRPPackage) selectedLogicalsystemmain.getOwner();
        IRPDiagram generatedLBS = (IRPDiagram) logicalPackage.getObjectModelDiagrams().getItem(1);

        List<?> representedLogicalsystem1 =
            generatedLBS.getCorrespondingGraphicElements(nestedlogicalsystemchild1).toList();
        List<?> representedNestedLogicalsystem2 =
            generatedLBS.getCorrespondingGraphicElements(nestedlogicalsystemchild2).toList();
        List<?> representedNestedsystemwithreference_1 =
                generatedLBS.getCorrespondingGraphicElements(nestedlogicalsystemwithreference_1).toList();
        

        //  JUnit 5 : message en second paramètre
        Assertions.assertFalse(representedLogicalsystem1.isEmpty(),
            "Function is not present in the diagram");
        
        Assertions.assertFalse(representedNestedLogicalsystem2.isEmpty(),
            "Referenced Function is not present in the diagram");
        
        Assertions.assertFalse(representedNestedsystemwithreference_1.isEmpty(),
                "Referenced Function is not present in the diagram");
    }
}