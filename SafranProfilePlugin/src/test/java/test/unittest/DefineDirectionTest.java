package test.unittest;

import org.junit.Assert;
import org.junit.Test;

import com.telelogic.rhapsody.core.IRPClass;
import com.telelogic.rhapsody.core.IRPCollection;
import com.telelogic.rhapsody.core.IRPSysMLPort;

import main.constants.Direction;
import test.RhapsodyBaseTest;
import tools.DefineDirection;

public class DefineDirectionTest extends RhapsodyBaseTest {

	private DefineDirection tool;

	public DefineDirectionTest() {
		super("./src/test/resources/DefineInOut/DefineInOut.rpyx");
	}


	/**
	 * Selected object: FlowPort without type (is implicit) defined as input
	 */
	@Test
	public void implicitFlowPortAsInput() {
		// Arrange
		tool = new DefineDirection(rhapsodyApplication,Direction.In);

		IRPSysMLPort flowPort = (IRPSysMLPort) project.findElementByGUID("GUID b8aede04-fa9c-461a-9492-3084f4970788");
		Assert.assertNotNull("No FlowPort object in the model", flowPort);

		IRPClass untypedFlowItem = (IRPClass) project.findElementByGUID("GUID 0731c9aa-fd94-40fc-8d27-2770943d6384");
		Assert.assertNotNull("No Flow Item (untyped) object in the model", untypedFlowItem);

		IRPCollection modelElements = rhapsodyApplication.createNewCollection();
		modelElements.addItem(flowPort);
		rhapsodyApplication.selectModelElements(modelElements);
		
		// Act
		tool.execute();

		// Assert
		Assert.assertTrue(untypedFlowItem.equals(flowPort.getType()));
		Assert.assertTrue("In".equals(flowPort.getPortDirection()));
	}

	/**
	 * Selected object: FlowPort without type (is implicit) defined as output
	 */
	@Test
	public void implicitFlowPortAsOutput() {
		// Arrange
		tool = new DefineDirection(rhapsodyApplication,Direction.Out);

		IRPSysMLPort flowPort = (IRPSysMLPort) project.findElementByGUID("GUID b8aede04-fa9c-461a-9492-3084f4970788");
		Assert.assertNotNull("No FlowPort object in the model", flowPort);
		
		IRPClass untypedFlowItem = (IRPClass) project.findElementByGUID("GUID 0731c9aa-fd94-40fc-8d27-2770943d6384");
		Assert.assertNotNull("No Flow Item (untyped) object in the model", untypedFlowItem);
		
		IRPCollection modelElements = rhapsodyApplication.createNewCollection();
		modelElements.addItem(flowPort);
		rhapsodyApplication.selectModelElements(modelElements);

		// Act
		tool.execute();

		// Assert
		Assert.assertTrue(untypedFlowItem.equals(flowPort.getType()));
		Assert.assertTrue("Out".equals(flowPort.getPortDirection()));
	}

	/**
	 * Selected object: FlowPort without type (is implicit) defined as bidirectional
	 */
	@Test
	public void implicitFlowPortAsInOut() {
		// Arrange
		tool = new DefineDirection(rhapsodyApplication,Direction.InOut);
		
		IRPSysMLPort flowPort = (IRPSysMLPort) project.findElementByGUID("GUID b8aede04-fa9c-461a-9492-3084f4970788");
		Assert.assertNotNull("No FlowPort object in the model", flowPort);
		
		IRPClass untypedFlowItem = (IRPClass) project.findElementByGUID("GUID 0731c9aa-fd94-40fc-8d27-2770943d6384");
		Assert.assertNotNull("No Flow Item (untyped) object in the model", untypedFlowItem);

		IRPCollection modelElements = rhapsodyApplication.createNewCollection();
		modelElements.addItem(flowPort);
		rhapsodyApplication.selectModelElements(modelElements);
		
		// Act
		tool.execute();

		// Assert
		Assert.assertTrue(untypedFlowItem.equals(flowPort.getType()));
		Assert.assertTrue("InOut".equals(flowPort.getPortDirection()));
	}
	
	/**
	 * Selected object: output FlowPort with existing type defined as bidirectional
	 */
	@Test
	public void typedFlowPortAsInOut() {
		// Arrange
		tool = new DefineDirection(rhapsodyApplication,Direction.InOut);
		
		IRPSysMLPort flowPort = (IRPSysMLPort) project.findElementByGUID("GUID 8a3303ee-f755-46ae-9673-c7c6896dd51b");
		Assert.assertNotNull("No FlowPort object in the model", flowPort);
		
		IRPClass existingType = (IRPClass) project.findElementByGUID("GUID 4c5e2f8a-aefa-45c6-b7c5-16d25c41336c");
		Assert.assertNotNull("No Flow Item object in the model", existingType);

		IRPCollection modelElements = rhapsodyApplication.createNewCollection();
		modelElements.addItem(flowPort);
		rhapsodyApplication.selectModelElements(modelElements);
		
		// Act
		tool.execute();

		// Assert
		Assert.assertTrue(existingType.equals(flowPort.getType()));
		Assert.assertTrue("InOut".equals(flowPort.getPortDirection()));
	}
}
