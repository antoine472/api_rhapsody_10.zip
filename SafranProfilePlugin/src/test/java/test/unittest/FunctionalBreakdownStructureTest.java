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
import tools.GenerateFBS;

public class FunctionalBreakdownStructureTest extends RhapsodyBaseTest {

	private GenerateFBS tool;

	public FunctionalBreakdownStructureTest() {
		super("./src/test/resources/FunctionalBreakdownStructure/FunctionalBreakdownStructure.rpyx");
		tool = new GenerateFBS(rhapsodyApplication);
	}
	
	/**
	 * Selected object: Function with nested functions and referenced function
	 */
	@Test
	public void functionWithNestedFunctionAndReferencedFunction() {
		// Arrange
		IRPClass selectedFunction = (IRPClass) project.findElementByGUID("GUID 143b3942-4985-4fb1-b791-e69c986ccc37");
		Assertions.assertNotNull( selectedFunction, "No Selected Object");
		
		IRPClass nestedFunction = (IRPClass) project.findElementByGUID("GUID 15f23466-1718-47f2-bd3e-927a8ee0de9a");
		Assertions.assertNotNull( nestedFunction, "No Selected Object");
		
		IRPClass nestedReferencedFunction = (IRPClass) project.findElementByGUID("GUID 6ce4adbb-adfb-45b0-a03e-3cbd8cde7417");
		Assertions.assertNotNull( nestedReferencedFunction, "No Selected Object");
		
		// TODO : To be updated after Rhapsody 10.0.3 /!\
//		IRPCollection modelElements = rhapsodyApplication.createNewCollection();
//		modelElements.addItem(selectedFunction);
//		rhapsodyApplication.selectModelElements(modelElements);
				
		// Act
		tool.execute();
		
		// Assert
		IRPPackage functionalPackage = (IRPPackage) selectedFunction.getOwner();
		IRPDiagram generatedFBS = (IRPDiagram) functionalPackage.getObjectModelDiagrams().getItem(1);
		
		List<?> representedFunctions = generatedFBS.getCorrespondingGraphicElements(nestedFunction).toList();
		List<?> representedNestedFunctions = generatedFBS.getCorrespondingGraphicElements(nestedReferencedFunction).toList();
		Assertions.assertFalse(representedFunctions.isEmpty(), "Function is not present in the diagram");
		Assertions.assertFalse(representedNestedFunctions.isEmpty(), "Referenced Function is not present in the diagram");
	}
}
