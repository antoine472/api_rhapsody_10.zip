package test.unittest;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.telelogic.rhapsody.core.IRPClass;
import com.telelogic.rhapsody.core.IRPCollection;
import com.telelogic.rhapsody.core.IRPFlow;
import com.telelogic.rhapsody.core.IRPGeneralization;
import com.telelogic.rhapsody.core.IRPModelElement;

import test.RhapsodyBaseTest;
import tools.UpdateFunctionAllocation;

public class FunctionAllocationTest extends RhapsodyBaseTest {

	private UpdateFunctionAllocation tool;

	public FunctionAllocationTest() {
		super("./src/test/resources/FunctionAllocation/FunctionAllocation.rpyx");
		tool = new UpdateFunctionAllocation(rhapsodyApplication);
	}
	
	/**
	 * Selected object: Logical System without Functions with a single allocation dependency
	 */
	@Test
	public void logicalSystemWithoutFunctionSingleAllocation() {
		// Arrange
		IRPClass logicalSystem = (IRPClass) project.findElementByGUID("GUID 552aca54-0390-4dc0-ada1-8ede2928065b");
		Assert.assertNotNull("No Selected Object", logicalSystem);
		
		IRPClass function = (IRPClass) project.findElementByGUID("GUID 12b77319-0faa-4acf-bbac-115e57ea0e1c");
		Assert.assertNotNull("No Selected Object", function);
		
		
		IRPCollection modelElements = rhapsodyApplication.createNewCollection();
		modelElements.addItem(logicalSystem);
		rhapsodyApplication.selectModelElements(modelElements);
				
		// Act
		tool.execute();
		
		// Assert
		List<IRPClass> listFunctions = logicalSystem.getNestedClassifiers().toList();
		Assert.assertFalse(listFunctions.isEmpty());
		
		List<IRPGeneralization> listGeneralization = listFunctions.get(0).getGeneralizations().toList();
		
		Assert.assertFalse(listGeneralization.isEmpty());
		
		Assert.assertTrue(listGeneralization.get(0).getBaseClass().equals(function));
	}
	
	/**
	 * Selected object: Logical System without Functions with multiples allocations dependencies
	 */
	@Test
	public void logicalSystemWithoutFunctionsMultipleAllocations() {
		// Arrange
		IRPClass logicalSystem = (IRPClass) project.findElementByGUID("GUID f643d6c0-67b3-41f3-a0d9-fbaec29ae3cc");
		Assert.assertNotNull("No Selected Object", logicalSystem);
		
		IRPCollection modelElements = rhapsodyApplication.createNewCollection();
		modelElements.addItem(logicalSystem);
		rhapsodyApplication.selectModelElements(modelElements);
				
		// Act
		tool.execute();
		
		// Assert
		List<IRPClass> listFunctions = logicalSystem.getNestedClassifiers().toList();
		Assert.assertTrue(listFunctions.size() == 3);
	}
	
	/**
	 * Selected object: Logical System with Functions and multiples allocations dependencies
	 */
	@Test
	public void logicalSystemWithFunctionsMultipleAllocations() {
		// Arrange
		IRPClass logicalSystem = (IRPClass) project.findElementByGUID("GUID df4f8722-54f7-408e-9079-f43aead623c4");
		Assert.assertNotNull("No Selected Object", logicalSystem);
		
		IRPCollection modelElements = rhapsodyApplication.createNewCollection();
		modelElements.addItem(logicalSystem);
		rhapsodyApplication.selectModelElements(modelElements);
				
		// Act
		tool.execute();
		
		// Assert
		List<IRPClass> listFunctions = logicalSystem.getNestedClassifiers().toList();
		Assert.assertTrue(listFunctions.size() == 3);
	}
	
	/**
	 * Selected object: Logical System without Functions with a single allocation dependency
	 * The logical system will have a Function with the same label and generalization link
	 */
	@Test
	public void checkLabelFunctionAllocation() {
		// Arrange
		IRPClass logicalSystem = (IRPClass) project.findElementByGUID("GUID 552aca54-0390-4dc0-ada1-8ede2928065b");
		Assert.assertNotNull("No Selected Object", logicalSystem);
		
		IRPClass function = (IRPClass) project.findElementByGUID("GUID 12b77319-0faa-4acf-bbac-115e57ea0e1c");
		Assert.assertNotNull("No Selected Object", function);
		
		
		IRPCollection modelElements = rhapsodyApplication.createNewCollection();
		modelElements.addItem(logicalSystem);
		rhapsodyApplication.selectModelElements(modelElements);
				
		// Act
		tool.execute();
		
		// Assert
		List<IRPClass> listFunctions = logicalSystem.getNestedClassifiers().toList();
		Assert.assertFalse(listFunctions.isEmpty());
		
		List<IRPGeneralization> listGeneralization = listFunctions.get(0).getGeneralizations().toList();
		
		Assert.assertFalse(listGeneralization.isEmpty());
		
		Assert.assertTrue(listGeneralization.get(0).getBaseClass().equals(function));
		
		Assert.assertTrue(listGeneralization.get(0).getDisplayName().equals(function.getDisplayName()));
		
	}
}
