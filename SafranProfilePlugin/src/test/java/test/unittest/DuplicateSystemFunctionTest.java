package test.unittest;

import org.junit.Assert;
import org.junit.Test;

import com.telelogic.rhapsody.core.IRPClass;
import com.telelogic.rhapsody.core.IRPCollection;

import test.RhapsodyBaseTest;
import tools.DuplicateServiceFunction;

public class DuplicateSystemFunctionTest extends RhapsodyBaseTest {

	private DuplicateServiceFunction tool;

	public DuplicateSystemFunctionTest() {
		super("./src/test/resources/FunctionalSystemFunction/FunctionalSystemFunction.rpyx");
		tool = new DuplicateServiceFunction(rhapsodyApplication);
	}


	/**
	 * Selected object: Functional System
	 */
	@Test
	public void createRedefinedFunctionInFunctionalSystem() {
		// Arrange
		IRPClass selectedFunctionalSystem = (IRPClass) project.findElementByGUID("GUID d60c7a10-a5f1-4de0-90d6-99cbe1f19590");
		Assert.assertNotNull("No Functional System object in the model", selectedFunctionalSystem);

		IRPCollection modelElements = rhapsodyApplication.createNewCollection();
		modelElements.addItem(selectedFunctionalSystem);
		rhapsodyApplication.selectModelElements(modelElements);
		
		// Act
		tool.execute();

		// Assert
		Assert.assertTrue(selectedFunctionalSystem.getNestedClassifiers().getCount() == 2);
	}

}
