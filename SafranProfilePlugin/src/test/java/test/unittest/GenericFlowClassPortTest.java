package test.unittest;

import org.junit.Assert;
import org.junit.Test;

import com.telelogic.rhapsody.core.IRPClass;
import com.telelogic.rhapsody.core.IRPCollection;
import com.telelogic.rhapsody.core.IRPDiagram;
import com.telelogic.rhapsody.core.IRPFlow;
import com.telelogic.rhapsody.core.IRPModelElement;
import com.telelogic.rhapsody.core.IRPStereotype;
import com.telelogic.rhapsody.core.IRPSysMLPort;

import main.constants.ProfilConstants;
import test.RhapsodyBaseTest;
import tools.GenericFlow;

public class GenericFlowClassPortTest extends RhapsodyBaseTest {

	private GenericFlow tool;

	public GenericFlowClassPortTest() {
		super("./src/test/resources/GenericFlowClassPort/GenericFlowClassPort.rpyx");
		tool = new GenericFlow(rhapsodyApplication);
	}
	
	private void openDiagramTimer(IRPDiagram diagram) {
		
		try {
			diagram.openDiagram();
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Diagram: Operational Context
	 * Selected object: Directional Generic Flow 
	 * Relation: Operational System and Operational System Port(Type+Stereotyped) 
	 */
	@Test
	public void directionalGenericFlow_OperationalSystem_OperationalSystemPort() {
		// Arrange
		final String GUID_FLOW_ITEM = "GUID 3cd6e0a3-8153-4248-8244-66fbee624ddf";
		
		final String GUID_GENERIC_FLOW_1 = "GUID ef21e092-2166-410c-8b38-a10779de6fbe";
		
		IRPFlow genericFlow = (IRPFlow) project.findElementByGUID(GUID_GENERIC_FLOW_1);
		IRPClass flowItem = (IRPClass) project.findElementByGUID(GUID_FLOW_ITEM);
		
		IRPDiagram diagram = (IRPDiagram) genericFlow.getReferences().getItem(1);

		openDiagramTimer(diagram);
		
		IRPCollection graphElements = diagram.getCorrespondingGraphicElements(genericFlow);
		rhapsodyApplication.selectGraphElements(graphElements);
		
		Assert.assertNotNull("Generic Flow Model Element does not exist in the model", genericFlow);
		
		// Act
		tool.execute();
		
		// Assert
		IRPModelElement source = genericFlow.getEnd1();
		IRPModelElement target = genericFlow.getEnd2();
		
		// check Port
		Assert.assertTrue("End1 Flow is not a Flow Port",source instanceof IRPSysMLPort);
		Assert.assertTrue("End2 Flow is not a Flow Port", target instanceof IRPSysMLPort);
		
		// check Type
		Assert.assertTrue("SourcePort is not typed", flowItem.equals(((IRPSysMLPort) source).getType()));
		Assert.assertTrue("TargetPort is not typed", flowItem.equals(((IRPSysMLPort) target).getType()));

		// check Direction
		Assert.assertTrue("SourcePort is not Output", "Out".equals(((IRPSysMLPort) source).getPortDirection()));
		Assert.assertTrue("TargetPort is not Input", "In".equals(((IRPSysMLPort) target).getPortDirection()));
		
		// Check if the source object has the specific "hydraulic" stereotype
		boolean sourceHasHydraulicStereotype = source.getStereotypes().toList().stream()
		    .anyMatch(stereotype -> ((IRPStereotype) stereotype).getGUID().equals(ProfilConstants.GUID_SAFRAN_HYDRAULIC));

		// Check if the source object has the specific "hydraulic" stereotype
		boolean targetHasHydraulicStereotype = target.getStereotypes().toList().stream()
		    .anyMatch(stereotype -> ((IRPStereotype) stereotype).getGUID().equals(ProfilConstants.GUID_SAFRAN_HYDRAULIC));

		Assert.assertTrue(sourceHasHydraulicStereotype);
		Assert.assertTrue(targetHasHydraulicStereotype);
		
		// Check if the source object has the specific "hydraulic" stereotype
		boolean genericFlowHasHydraulicStereotype = genericFlow.getStereotypes().toList().stream()
		    .anyMatch(stereotype -> ((IRPStereotype) stereotype).getGUID().equals(ProfilConstants.GUID_SAFRAN_HYDRAULIC));
		
		Assert.assertTrue(genericFlowHasHydraulicStereotype);
				
		Assert.assertFalse("Conveyed shall not be not empty",genericFlow.getConveyed().toList().isEmpty());
		Assert.assertTrue("Not an Operational flow", "Operational Flow".equals(genericFlow.getUserDefinedMetaClass()));
	}
	
	/**
	 * Diagram: Operational Context
	 * Selected object: Directional Generic Flow 
	 * Relation: Operational System and Stakeholder Port(Type+Stereotyped) 
	 */
	@Test
	public void directionalGenericFlow_OperationalSystem_StakeholderPort() {
		// Arrange
		final String GUID_FLOW_ITEM = "GUID 3cd6e0a3-8153-4248-8244-66fbee624ddf";
		
		final String GUID_GENERIC_FLOW_2 = "GUID ccf2e7b0-d8b1-4ccc-b92a-b248571a5fd3";
		
		IRPFlow genericFlow = (IRPFlow) project.findElementByGUID(GUID_GENERIC_FLOW_2);
		IRPClass flowItem = (IRPClass) project.findElementByGUID(GUID_FLOW_ITEM);
		
		IRPDiagram diagram = (IRPDiagram) genericFlow.getReferences().getItem(1);

		openDiagramTimer(diagram);
		
		IRPCollection graphElements = diagram.getCorrespondingGraphicElements(genericFlow);
		rhapsodyApplication.selectGraphElements(graphElements);
		
		Assert.assertNotNull("Generic Flow Model Element does not exist in the model", genericFlow);
		
		// Act
		tool.execute();
		
		// Assert
		IRPModelElement source = genericFlow.getEnd1();
		IRPModelElement target = genericFlow.getEnd2();
		
		// check Port
		Assert.assertTrue("End1 Flow is not a Flow Port",source instanceof IRPSysMLPort);
		Assert.assertTrue("End2 Flow is not a Flow Port", target instanceof IRPSysMLPort);
		
		// check Type
		Assert.assertTrue("SourcePort is not typed", flowItem.equals(((IRPSysMLPort) source).getType()));
		Assert.assertTrue("TargetPort is not typed", flowItem.equals(((IRPSysMLPort) target).getType()));

		// check Direction
		Assert.assertTrue("SourcePort is not Output", "Out".equals(((IRPSysMLPort) source).getPortDirection()));
		Assert.assertTrue("TargetPort is not Input", "In".equals(((IRPSysMLPort) target).getPortDirection()));
		
		// Check if the source port has the specific stereotype
		boolean sourceHasStereotype = source.getStereotypes().toList().stream()
		    .anyMatch(stereotype -> ((IRPStereotype) stereotype).getGUID().equals(ProfilConstants.GUID_SAFRAN_PNEUMATIC));

		// Check if the target port has the specific stereotype
		boolean targetHasStereotype = target.getStereotypes().toList().stream()
		    .anyMatch(stereotype -> ((IRPStereotype) stereotype).getGUID().equals(ProfilConstants.GUID_SAFRAN_PNEUMATIC));

		Assert.assertTrue(sourceHasStereotype);
		Assert.assertTrue(targetHasStereotype);
		
		// Check if the source object has the specific stereotype
		boolean genericFlowHasStereotype = genericFlow.getStereotypes().toList().stream()
		    .anyMatch(stereotype -> ((IRPStereotype) stereotype).getGUID().equals(ProfilConstants.GUID_SAFRAN_PNEUMATIC));
		
		Assert.assertTrue(genericFlowHasStereotype);
				
		Assert.assertFalse("Conveyed shall not be not empty",genericFlow.getConveyed().toList().isEmpty());
		Assert.assertTrue("Not an Operational flow", "Operational Flow".equals(genericFlow.getUserDefinedMetaClass()));
	}
	
	/**
	 * Diagram: Operational Context
	 * Selected object: Bidirectional Generic Flow 
	 * Relation: Operational System and Operational System Port(Type+Stereotyped) 
	 */
	@Test
	public void bidirectionalGenericFlow_OperationalSystem_StakeholderPort() {
		// Arrange
		final String GUID_FLOW_ITEM = "GUID 3cd6e0a3-8153-4248-8244-66fbee624ddf";
		
		final String GUID_GENERIC_FLOW_3 = "GUID ac070b4b-7b1f-4388-8204-4a46ddc73cd3";
		
		IRPFlow genericFlow = (IRPFlow) project.findElementByGUID(GUID_GENERIC_FLOW_3);
		IRPClass flowItem = (IRPClass) project.findElementByGUID(GUID_FLOW_ITEM);
		
		IRPDiagram diagram = (IRPDiagram) genericFlow.getReferences().getItem(1);

		openDiagramTimer(diagram);
		
		IRPCollection graphElements = diagram.getCorrespondingGraphicElements(genericFlow);
		rhapsodyApplication.selectGraphElements(graphElements);
		
		Assert.assertNotNull("Generic Flow Model Element does not exist in the model", genericFlow);
		
		// Act
		tool.execute();
		
		// Assert
		IRPModelElement source = genericFlow.getEnd1();
		IRPModelElement target = genericFlow.getEnd2();
		
		// check Port
		Assert.assertTrue("End1 Flow is not a Flow Port",source instanceof IRPSysMLPort);
		Assert.assertTrue("End2 Flow is not a Flow Port", target instanceof IRPSysMLPort);
		
		// check Type
		Assert.assertTrue("SourcePort is not typed", flowItem.equals(((IRPSysMLPort) source).getType()));
		Assert.assertTrue("TargetPort is not typed", flowItem.equals(((IRPSysMLPort) target).getType()));

		// check Direction
		Assert.assertTrue("SourcePort is not InOut", "InOut".equals(((IRPSysMLPort) source).getPortDirection()));
		Assert.assertTrue("TargetPort is not InOut", "InOut".equals(((IRPSysMLPort) target).getPortDirection()));
		
		// Check if the source port has the specific stereotype
		boolean sourceHasStereotype = source.getStereotypes().toList().stream()
		    .anyMatch(stereotype -> ((IRPStereotype) stereotype).getGUID().equals(ProfilConstants.GUID_SAFRAN_INFORMATION));

		// Check if the target port has the specific stereotype
		boolean targetHasStereotype = target.getStereotypes().toList().stream()
		    .anyMatch(stereotype -> ((IRPStereotype) stereotype).getGUID().equals(ProfilConstants.GUID_SAFRAN_INFORMATION));

		Assert.assertTrue(sourceHasStereotype);
		Assert.assertTrue(targetHasStereotype);
		
		// Check if the source object has the specific stereotype
		boolean genericFlowHasStereotype = genericFlow.getStereotypes().toList().stream()
		    .anyMatch(stereotype -> ((IRPStereotype) stereotype).getGUID().equals(ProfilConstants.GUID_SAFRAN_INFORMATION));
		
		Assert.assertTrue(genericFlowHasStereotype);
				
		Assert.assertFalse("Conveyed shall not be not empty",genericFlow.getConveyed().toList().isEmpty());
		Assert.assertTrue("Not an Operational flow", "Operational Flow".equals(genericFlow.getUserDefinedMetaClass()));
	}

	
	/**
	 * Diagram: SOI - System Functional Behavior
	 * Selected object: Directional Generic Flow 
	 * Relation: Function and Operational System Port(Type+Stereotyped) 
	 */
	@Test
	public void directionalGenericFlow_Function_OperationalSystemPort() {
		// Arrange
		final String GUID_FLOW_ITEM = "GUID 3cd6e0a3-8153-4248-8244-66fbee624ddf";
		
		final String GUID_GENERIC_FLOW_4 = "GUID e059894d-dc03-4c0e-9ed4-f20ba09510a5";
		
		IRPFlow genericFlow = (IRPFlow) project.findElementByGUID(GUID_GENERIC_FLOW_4);
		IRPClass flowItem = (IRPClass) project.findElementByGUID(GUID_FLOW_ITEM);
		
		IRPDiagram diagram = (IRPDiagram) genericFlow.getReferences().getItem(1);

		openDiagramTimer(diagram);
		
		IRPCollection graphElements = diagram.getCorrespondingGraphicElements(genericFlow);
		rhapsodyApplication.selectGraphElements(graphElements);
		
		Assert.assertNotNull("Generic Flow Model Element does not exist in the model", genericFlow);
		
		// Act
		tool.execute();
		
		// Assert
		IRPModelElement source = genericFlow.getEnd1();
		IRPModelElement target = genericFlow.getEnd2();
		
		// check Port
		Assert.assertTrue("End1 Flow is not a Flow Port",source instanceof IRPSysMLPort);
		Assert.assertTrue("End2 Flow is not a Flow Port", target instanceof IRPSysMLPort);
		
		// check Type
		Assert.assertTrue("SourcePort is not typed", flowItem.equals(((IRPSysMLPort) source).getType()));
		Assert.assertTrue("TargetPort is not typed", flowItem.equals(((IRPSysMLPort) target).getType()));

		// check Direction
		Assert.assertTrue("SourcePort is not Output", "Out".equals(((IRPSysMLPort) source).getPortDirection()));
		Assert.assertTrue("TargetPort is not Output", "Out".equals(((IRPSysMLPort) target).getPortDirection()));
		
		// Check if the source port has the specific stereotype
		boolean sourceHasStereotype = source.getStereotypes().toList().stream()
		    .anyMatch(stereotype -> ((IRPStereotype) stereotype).getGUID().equals(ProfilConstants.GUID_SAFRAN_HYDRAULIC));

		// Check if the target port has the specific stereotype
		boolean targetHasStereotype = target.getStereotypes().toList().stream()
		    .anyMatch(stereotype -> ((IRPStereotype) stereotype).getGUID().equals(ProfilConstants.GUID_SAFRAN_HYDRAULIC));

		Assert.assertTrue(sourceHasStereotype);
		Assert.assertTrue(targetHasStereotype);
		
		// Check if the source object has the specific stereotype
		boolean genericFlowHasStereotype = genericFlow.getStereotypes().toList().stream()
		    .anyMatch(stereotype -> ((IRPStereotype) stereotype).getGUID().equals(ProfilConstants.GUID_SAFRAN_HYDRAULIC));
		
		Assert.assertTrue(genericFlowHasStereotype);
				
		Assert.assertFalse("Conveyed shall not be not empty",genericFlow.getConveyed().toList().isEmpty());
		Assert.assertTrue("Not an Functional flow", "Functional Flow".equals(genericFlow.getUserDefinedMetaClass()));
	}
	
	
	/**
	 * Diagram: SOI - System Functional Behavior
	 * Selected object: Bidirectional Generic Flow 
	 * Relation: Function and Operational System Port(Type+Stereotyped) 
	 */
	@Test
	public void bidirectionalGenericFlow_Function_OperationalSystemPort() {
		// Arrange
		final String GUID_FLOW_ITEM = "GUID 3cd6e0a3-8153-4248-8244-66fbee624ddf";
		
		final String GUID_GENERIC_FLOW_5 = "GUID 1528b19c-b28c-4877-9d8c-7b410de26e3c";
		
		IRPFlow genericFlow = (IRPFlow) project.findElementByGUID(GUID_GENERIC_FLOW_5);
		IRPClass flowItem = (IRPClass) project.findElementByGUID(GUID_FLOW_ITEM);
		
		IRPDiagram diagram = (IRPDiagram) genericFlow.getReferences().getItem(1);

		openDiagramTimer(diagram);
		
		IRPCollection graphElements = diagram.getCorrespondingGraphicElements(genericFlow);
		rhapsodyApplication.selectGraphElements(graphElements);
		
		Assert.assertNotNull("Generic Flow Model Element does not exist in the model", genericFlow);
		
		// Act
		tool.execute();
		
		// Assert
		IRPModelElement source = genericFlow.getEnd1();
		IRPModelElement target = genericFlow.getEnd2();
		
		// check Port
		Assert.assertTrue("End1 Flow is not a Flow Port",source instanceof IRPSysMLPort);
		Assert.assertTrue("End2 Flow is not a Flow Port", target instanceof IRPSysMLPort);
		
		// check Type
		Assert.assertTrue("SourcePort is not typed", flowItem.equals(((IRPSysMLPort) source).getType()));
		Assert.assertTrue("TargetPort is not typed", flowItem.equals(((IRPSysMLPort) target).getType()));

		// check Direction
		Assert.assertTrue("SourcePort is not InOut", "InOut".equals(((IRPSysMLPort) source).getPortDirection()));
		Assert.assertTrue("TargetPort is not InOut", "InOut".equals(((IRPSysMLPort) target).getPortDirection()));
		
		// Check if the source port has the specific stereotype
		boolean sourceHasStereotype = source.getStereotypes().toList().stream()
		    .anyMatch(stereotype -> ((IRPStereotype) stereotype).getGUID().equals(ProfilConstants.GUID_SAFRAN_ELECTRICAL));

		// Check if the target port has the specific stereotype
		boolean targetHasStereotype = target.getStereotypes().toList().stream()
		    .anyMatch(stereotype -> ((IRPStereotype) stereotype).getGUID().equals(ProfilConstants.GUID_SAFRAN_ELECTRICAL));

		Assert.assertTrue(sourceHasStereotype);
		Assert.assertTrue(targetHasStereotype);
		
		// Check if the source object has the specific stereotype
		boolean genericFlowHasStereotype = genericFlow.getStereotypes().toList().stream()
		    .anyMatch(stereotype -> ((IRPStereotype) stereotype).getGUID().equals(ProfilConstants.GUID_SAFRAN_ELECTRICAL));
		
		Assert.assertTrue(genericFlowHasStereotype);
				
		Assert.assertFalse("Conveyed shall not be not empty",genericFlow.getConveyed().toList().isEmpty());
		Assert.assertTrue("Not an Functional flow", "Functional Flow".equals(genericFlow.getUserDefinedMetaClass()));
	}
	
	
	/**
	 * Diagram: Functional Behavior Diagram
	 * Selected object: Directional Generic Flow 
	 * Relation: Function and Function Port(Type+Stereotyped) 
	 */
	@Test
	public void directionalGenericFlow_Function_FunctionPort() {
		// Arrange
		final String GUID_FLOW_ITEM = "GUID 3cd6e0a3-8153-4248-8244-66fbee624ddf";
		
		final String GUID_GENERIC_FLOW_6 = "GUID 5344eea0-03af-425a-b08f-d6364666ed2b";
		
		IRPFlow genericFlow = (IRPFlow) project.findElementByGUID(GUID_GENERIC_FLOW_6);
		IRPClass flowItem = (IRPClass) project.findElementByGUID(GUID_FLOW_ITEM);
		
		IRPDiagram diagram = (IRPDiagram) genericFlow.getReferences().getItem(1);

		openDiagramTimer(diagram);
		
		IRPCollection graphElements = diagram.getCorrespondingGraphicElements(genericFlow);
		rhapsodyApplication.selectGraphElements(graphElements);
		
		Assert.assertNotNull("Generic Flow Model Element does not exist in the model", genericFlow);
		
		// Act
		tool.execute();
		
		// Assert
		IRPModelElement source = genericFlow.getEnd1();
		IRPModelElement target = genericFlow.getEnd2();
		
		// check Port
		Assert.assertTrue("End1 Flow is not a Flow Port",source instanceof IRPSysMLPort);
		Assert.assertTrue("End2 Flow is not a Flow Port", target instanceof IRPSysMLPort);
		
		// check Type
		Assert.assertTrue("SourcePort is not typed", flowItem.equals(((IRPSysMLPort) source).getType()));
		Assert.assertTrue("TargetPort is not typed", flowItem.equals(((IRPSysMLPort) target).getType()));

		// check Direction
		Assert.assertTrue("SourcePort is not Output", "Out".equals(((IRPSysMLPort) source).getPortDirection()));
		Assert.assertTrue("TargetPort is not Input", "In".equals(((IRPSysMLPort) target).getPortDirection()));
		
		// Check if the source port has the specific stereotype
		boolean sourceHasStereotype = source.getStereotypes().toList().stream()
		    .anyMatch(stereotype -> ((IRPStereotype) stereotype).getGUID().equals(ProfilConstants.GUID_SAFRAN_HYDRAULIC));

		// Check if the target port has the specific stereotype
		boolean targetHasStereotype = target.getStereotypes().toList().stream()
		    .anyMatch(stereotype -> ((IRPStereotype) stereotype).getGUID().equals(ProfilConstants.GUID_SAFRAN_HYDRAULIC));

		Assert.assertTrue(sourceHasStereotype);
		Assert.assertTrue(targetHasStereotype);
		
		// Check if the source object has the specific stereotype
		boolean genericFlowHasStereotype = genericFlow.getStereotypes().toList().stream()
		    .anyMatch(stereotype -> ((IRPStereotype) stereotype).getGUID().equals(ProfilConstants.GUID_SAFRAN_HYDRAULIC));
		
		Assert.assertTrue(genericFlowHasStereotype);
				
		Assert.assertFalse("Conveyed shall not be not empty",genericFlow.getConveyed().toList().isEmpty());
		Assert.assertTrue("Not an Functional flow", "Functional Flow".equals(genericFlow.getUserDefinedMetaClass()));
	}
	
	
	/**
	 * Diagram: Functional Behavior Diagram
	 * Selected object: Bidirectional Generic Flow 
	 * Relation: Function and Function Port(Type+Stereotyped) 
	 */
	@Test
	public void bidirectionalGenericFlow_Function_FunctionPort() {
		// Arrange
		final String GUID_FLOW_ITEM = "GUID 3cd6e0a3-8153-4248-8244-66fbee624ddf";
		
		final String GUID_GENERIC_FLOW_7 = "GUID 23677f40-4685-43f5-bd6e-e51e438b2b66";
		
		IRPFlow genericFlow = (IRPFlow) project.findElementByGUID(GUID_GENERIC_FLOW_7);
		IRPClass flowItem = (IRPClass) project.findElementByGUID(GUID_FLOW_ITEM);
		
		IRPDiagram diagram = (IRPDiagram) genericFlow.getReferences().getItem(1);

		openDiagramTimer(diagram);
		
		IRPCollection graphElements = diagram.getCorrespondingGraphicElements(genericFlow);
		rhapsodyApplication.selectGraphElements(graphElements);
		
		Assert.assertNotNull("Generic Flow Model Element does not exist in the model", genericFlow);
		
		// Act
		tool.execute();
		
		// Assert
		IRPModelElement source = genericFlow.getEnd1();
		IRPModelElement target = genericFlow.getEnd2();
		
		// check Port
		Assert.assertTrue("End1 Flow is not a Flow Port",source instanceof IRPSysMLPort);
		Assert.assertTrue("End2 Flow is not a Flow Port", target instanceof IRPSysMLPort);
		
		// check Type
		Assert.assertTrue("SourcePort is not typed", flowItem.equals(((IRPSysMLPort) source).getType()));
		Assert.assertTrue("TargetPort is not typed", flowItem.equals(((IRPSysMLPort) target).getType()));

		// check Direction
		Assert.assertTrue("SourcePort is not InOut", "InOut".equals(((IRPSysMLPort) source).getPortDirection()));
		Assert.assertTrue("TargetPort is not InOut", "InOut".equals(((IRPSysMLPort) target).getPortDirection()));
		
		// Check if the source port has the specific stereotype
		boolean sourceHasStereotype = source.getStereotypes().toList().stream()
		    .anyMatch(stereotype -> ((IRPStereotype) stereotype).getGUID().equals(ProfilConstants.GUID_SAFRAN_ELECTRICAL));

		// Check if the target port has the specific stereotype
		boolean targetHasStereotype = target.getStereotypes().toList().stream()
		    .anyMatch(stereotype -> ((IRPStereotype) stereotype).getGUID().equals(ProfilConstants.GUID_SAFRAN_ELECTRICAL));

		Assert.assertTrue(sourceHasStereotype);
		Assert.assertTrue(targetHasStereotype);
		
		// Check if the source object has the specific stereotype
		boolean genericFlowHasStereotype = genericFlow.getStereotypes().toList().stream()
		    .anyMatch(stereotype -> ((IRPStereotype) stereotype).getGUID().equals(ProfilConstants.GUID_SAFRAN_ELECTRICAL));
		
		Assert.assertTrue(genericFlowHasStereotype);
				
		Assert.assertFalse("Conveyed shall not be not empty",genericFlow.getConveyed().toList().isEmpty());
		Assert.assertTrue("Not an Functional flow", "Functional Flow".equals(genericFlow.getUserDefinedMetaClass()));
	}
	
	/**
	 * Diagram: Logical Architecture Diagram
	 * Selected object: Directional Generic Flow 
	 * Relation: Function and Logical System Port(Type+Stereotyped) 
	 */
	@Test
	public void directionalGenericFlow_Function_LogicalSystemPort() {
		// Arrange
		final String GUID_FLOW_ITEM = "GUID 3cd6e0a3-8153-4248-8244-66fbee624ddf";
		
		final String GUID_GENERIC_FLOW_8 = "GUID 8ac28de8-0e47-4b2d-8912-1f8645a18e1e";
		
		IRPFlow genericFlow = (IRPFlow) project.findElementByGUID(GUID_GENERIC_FLOW_8);
		IRPClass flowItem = (IRPClass) project.findElementByGUID(GUID_FLOW_ITEM);
		
		IRPDiagram diagram = (IRPDiagram) genericFlow.getReferences().getItem(1);

		openDiagramTimer(diagram);
		
		IRPCollection graphElements = diagram.getCorrespondingGraphicElements(genericFlow);
		rhapsodyApplication.selectGraphElements(graphElements);
		
		Assert.assertNotNull("Generic Flow Model Element does not exist in the model", genericFlow);
		
		// Act
		tool.execute();
		
		// Assert
		IRPModelElement source = genericFlow.getEnd1();
		IRPModelElement target = genericFlow.getEnd2();
		
		// check Port
		Assert.assertTrue("End1 Flow is not a Flow Port",source instanceof IRPSysMLPort);
		Assert.assertTrue("End2 Flow is not a Flow Port", target instanceof IRPSysMLPort);
		
		// check Type
		Assert.assertTrue("SourcePort is not typed", flowItem.equals(((IRPSysMLPort) source).getType()));
		Assert.assertTrue("TargetPort is not typed", flowItem.equals(((IRPSysMLPort) target).getType()));

		// check Direction
		Assert.assertTrue("SourcePort is not Output", "Out".equals(((IRPSysMLPort) source).getPortDirection()));
		Assert.assertTrue("TargetPort is not Output", "Out".equals(((IRPSysMLPort) target).getPortDirection()));
		
		// Check if the source port has the specific stereotype
		boolean sourceHasStereotype = source.getStereotypes().toList().stream()
		    .anyMatch(stereotype -> ((IRPStereotype) stereotype).getGUID().equals(ProfilConstants.GUID_SAFRAN_MECHANICAL));

		// Check if the target port has the specific stereotype
		boolean targetHasStereotype = target.getStereotypes().toList().stream()
		    .anyMatch(stereotype -> ((IRPStereotype) stereotype).getGUID().equals(ProfilConstants.GUID_SAFRAN_MECHANICAL));

		Assert.assertTrue(sourceHasStereotype);
		Assert.assertTrue(targetHasStereotype);
		
		// Check if the source object has the specific stereotype
		boolean genericFlowHasStereotype = genericFlow.getStereotypes().toList().stream()
		    .anyMatch(stereotype -> ((IRPStereotype) stereotype).getGUID().equals(ProfilConstants.GUID_SAFRAN_MECHANICAL));
		
		Assert.assertTrue(genericFlowHasStereotype);
				
		Assert.assertFalse("Conveyed shall not be not empty",genericFlow.getConveyed().toList().isEmpty());
		Assert.assertTrue("Not an Functional flow", "Functional Flow".equals(genericFlow.getUserDefinedMetaClass()));
	}
	
	
	/**
	 * Diagram: Logical Architecture Diagram
	 * Selected object: Bidirectional Generic Flow 
	 * Relation: Function and Logical System Port(Type+Stereotyped) 
	 */
	@Test
	public void bidirectionalGenericFlow_Function_LogicalSystemPort() {
		// Arrange
		final String GUID_FLOW_ITEM = "GUID 3cd6e0a3-8153-4248-8244-66fbee624ddf";
		
		final String GUID_GENERIC_FLOW_9 = "GUID 7ab158e8-1579-4047-8485-b8ad812fcfc4";
		
		IRPFlow genericFlow = (IRPFlow) project.findElementByGUID(GUID_GENERIC_FLOW_9);
		IRPClass flowItem = (IRPClass) project.findElementByGUID(GUID_FLOW_ITEM);
		
		IRPDiagram diagram = (IRPDiagram) genericFlow.getReferences().getItem(1);

		openDiagramTimer(diagram);
		
		IRPCollection graphElements = diagram.getCorrespondingGraphicElements(genericFlow);
		rhapsodyApplication.selectGraphElements(graphElements);
		
		Assert.assertNotNull("Generic Flow Model Element does not exist in the model", genericFlow);
		
		// Act
		tool.execute();
		
		// Assert
		IRPModelElement source = genericFlow.getEnd1();
		IRPModelElement target = genericFlow.getEnd2();
		
		// check Port
		Assert.assertTrue("End1 Flow is not a Flow Port",source instanceof IRPSysMLPort);
		Assert.assertTrue("End2 Flow is not a Flow Port", target instanceof IRPSysMLPort);
		
		// check Type
		Assert.assertTrue("SourcePort is not typed", flowItem.equals(((IRPSysMLPort) source).getType()));
		Assert.assertTrue("TargetPort is not typed", flowItem.equals(((IRPSysMLPort) target).getType()));

		// check Direction
		Assert.assertTrue("SourcePort is not InOut", "InOut".equals(((IRPSysMLPort) source).getPortDirection()));
		Assert.assertTrue("TargetPort is not InOut", "InOut".equals(((IRPSysMLPort) target).getPortDirection()));
		
		// Check if the source port has the specific stereotype
		boolean sourceHasStereotype = source.getStereotypes().toList().stream()
		    .anyMatch(stereotype -> ((IRPStereotype) stereotype).getGUID().equals(ProfilConstants.GUID_SAFRAN_PNEUMATIC));

		// Check if the target port has the specific stereotype
		boolean targetHasStereotype = target.getStereotypes().toList().stream()
		    .anyMatch(stereotype -> ((IRPStereotype) stereotype).getGUID().equals(ProfilConstants.GUID_SAFRAN_PNEUMATIC));

		Assert.assertTrue(sourceHasStereotype);
		Assert.assertTrue(targetHasStereotype);
		
		// Check if the source object has the specific stereotype
		boolean genericFlowHasStereotype = genericFlow.getStereotypes().toList().stream()
		    .anyMatch(stereotype -> ((IRPStereotype) stereotype).getGUID().equals(ProfilConstants.GUID_SAFRAN_PNEUMATIC));
		
		Assert.assertTrue(genericFlowHasStereotype);
				
		Assert.assertFalse("Conveyed shall not be not empty",genericFlow.getConveyed().toList().isEmpty());
		Assert.assertTrue("Not an Functional flow", "Functional Flow".equals(genericFlow.getUserDefinedMetaClass()));
	}
	
	/**
	 * Diagram: Logical Architecture Diagram
	 * Selected object: Directional Generic Flow 
	 * Relation: Logical System and Logical System Port(Type+Stereotyped) 
	 */
	@Test
	public void directionalGenericFlow_LogicalSystem_LogicalSystemPort() {
		// Arrange
		final String GUID_FLOW_ITEM = "GUID 3cd6e0a3-8153-4248-8244-66fbee624ddf";
		
		final String GUID_GENERIC_FLOW_10 = "GUID fd62b7cd-2667-4a27-81ff-0a239dd521fb";
		
		IRPFlow genericFlow = (IRPFlow) project.findElementByGUID(GUID_GENERIC_FLOW_10);
		IRPClass flowItem = (IRPClass) project.findElementByGUID(GUID_FLOW_ITEM);
		
		IRPDiagram diagram = (IRPDiagram) genericFlow.getReferences().getItem(1);

		openDiagramTimer(diagram);
		
		IRPCollection graphElements = diagram.getCorrespondingGraphicElements(genericFlow);
		rhapsodyApplication.selectGraphElements(graphElements);
		
		Assert.assertNotNull("Generic Flow Model Element does not exist in the model", genericFlow);
		
		// Act
		tool.execute();
		
		// Assert
		IRPModelElement source = genericFlow.getEnd1();
		IRPModelElement target = genericFlow.getEnd2();
		
		// check Port
		Assert.assertTrue("End1 Flow is not a Flow Port",source instanceof IRPSysMLPort);
		Assert.assertTrue("End2 Flow is not a Flow Port", target instanceof IRPSysMLPort);
		
		// check Type
		Assert.assertTrue("SourcePort is not typed", flowItem.equals(((IRPSysMLPort) source).getType()));
		Assert.assertTrue("TargetPort is not typed", flowItem.equals(((IRPSysMLPort) target).getType()));

		// check Direction
		Assert.assertTrue("SourcePort is not Output", "Out".equals(((IRPSysMLPort) source).getPortDirection()));
		Assert.assertTrue("TargetPort is not In", "In".equals(((IRPSysMLPort) target).getPortDirection()));
		
		// Check if the source port has the specific stereotype
		boolean sourceHasStereotype = source.getStereotypes().toList().stream()
		    .anyMatch(stereotype -> ((IRPStereotype) stereotype).getGUID().equals(ProfilConstants.GUID_SAFRAN_MECHANICAL));

		// Check if the target port has the specific stereotype
		boolean targetHasStereotype = target.getStereotypes().toList().stream()
		    .anyMatch(stereotype -> ((IRPStereotype) stereotype).getGUID().equals(ProfilConstants.GUID_SAFRAN_MECHANICAL));

		Assert.assertTrue(sourceHasStereotype);
		Assert.assertTrue(targetHasStereotype);
		
		// Check if the source object has the specific stereotype
		boolean genericFlowHasStereotype = genericFlow.getStereotypes().toList().stream()
		    .anyMatch(stereotype -> ((IRPStereotype) stereotype).getGUID().equals(ProfilConstants.GUID_SAFRAN_MECHANICAL));
		
		Assert.assertTrue(genericFlowHasStereotype);
				
		Assert.assertFalse("Conveyed shall not be not empty",genericFlow.getConveyed().toList().isEmpty());
		Assert.assertTrue("Not an Logical flow", "Logical Flow".equals(genericFlow.getUserDefinedMetaClass()));
	}
	
	
	/**
	 * Diagram: Logical Architecture Diagram
	 * Selected object: Bidirectional Generic Flow 
	 * Relation: Logical System and Logical System Port(Type+Stereotyped) 
	 */
	@Test
	public void bidirectionalGenericFlow_LogicalSystem_LogicalSystemPort() {
		// Arrange
		final String GUID_FLOW_ITEM = "GUID 3cd6e0a3-8153-4248-8244-66fbee624ddf";
		
		final String GUID_GENERIC_FLOW_11 = "GUID 733744ef-295b-46b7-b403-966f88ad060f";
		
		IRPFlow genericFlow = (IRPFlow) project.findElementByGUID(GUID_GENERIC_FLOW_11);
		IRPClass flowItem = (IRPClass) project.findElementByGUID(GUID_FLOW_ITEM);
		
		IRPDiagram diagram = (IRPDiagram) genericFlow.getReferences().getItem(1);

		openDiagramTimer(diagram);
		
		IRPCollection graphElements = diagram.getCorrespondingGraphicElements(genericFlow);
		rhapsodyApplication.selectGraphElements(graphElements);
		
		Assert.assertNotNull("Generic Flow Model Element does not exist in the model", genericFlow);
		
		// Act
		tool.execute();
		
		// Assert
		IRPModelElement source = genericFlow.getEnd1();
		IRPModelElement target = genericFlow.getEnd2();
		
		// check Port
		Assert.assertTrue("End1 Flow is not a Flow Port",source instanceof IRPSysMLPort);
		Assert.assertTrue("End2 Flow is not a Flow Port", target instanceof IRPSysMLPort);
		
		// check Type
		Assert.assertTrue("SourcePort is not typed", flowItem.equals(((IRPSysMLPort) source).getType()));
		Assert.assertTrue("TargetPort is not typed", flowItem.equals(((IRPSysMLPort) target).getType()));

		// check Direction
		Assert.assertTrue("SourcePort is not InOut", "InOut".equals(((IRPSysMLPort) source).getPortDirection()));
		Assert.assertTrue("TargetPort is not InOut", "InOut".equals(((IRPSysMLPort) target).getPortDirection()));
		
		// Check if the source port has the specific stereotype
		boolean sourceHasStereotype = source.getStereotypes().toList().stream()
		    .anyMatch(stereotype -> ((IRPStereotype) stereotype).getGUID().equals(ProfilConstants.GUID_SAFRAN_HYDRAULIC));

		// Check if the target port has the specific stereotype
		boolean targetHasStereotype = target.getStereotypes().toList().stream()
		    .anyMatch(stereotype -> ((IRPStereotype) stereotype).getGUID().equals(ProfilConstants.GUID_SAFRAN_HYDRAULIC));

		Assert.assertTrue(sourceHasStereotype);
		Assert.assertTrue(targetHasStereotype);
		
		// Check if the source object has the specific stereotype
		boolean genericFlowHasStereotype = genericFlow.getStereotypes().toList().stream()
		    .anyMatch(stereotype -> ((IRPStereotype) stereotype).getGUID().equals(ProfilConstants.GUID_SAFRAN_HYDRAULIC));
		
		Assert.assertTrue(genericFlowHasStereotype);
				
		Assert.assertFalse("Conveyed shall not be not empty",genericFlow.getConveyed().toList().isEmpty());
		Assert.assertTrue("Not an Logical flow", "Logical Flow".equals(genericFlow.getUserDefinedMetaClass()));
	}
	
	
	/**
	 * Diagram: Logical Architecture Diagram
	 * Selected object: Directional Generic Flow 
	 * Relation: Logical System and Operational System Port(Type+Stereotyped) 
	 */
	@Test
	public void directionalGenericFlow_LogicalSystem_OperationalSystemPort() {
		// Arrange
		final String GUID_FLOW_ITEM = "GUID 3cd6e0a3-8153-4248-8244-66fbee624ddf";
		
		final String GUID_GENERIC_FLOW_12 = "GUID 101898cd-9335-4b9e-9824-4dfa3acccc4f";
		
		IRPFlow genericFlow = (IRPFlow) project.findElementByGUID(GUID_GENERIC_FLOW_12);
		IRPClass flowItem = (IRPClass) project.findElementByGUID(GUID_FLOW_ITEM);
		
		IRPDiagram diagram = (IRPDiagram) genericFlow.getReferences().getItem(1);

		openDiagramTimer(diagram);
		
		IRPCollection graphElements = diagram.getCorrespondingGraphicElements(genericFlow);
		rhapsodyApplication.selectGraphElements(graphElements);
		
		Assert.assertNotNull("Generic Flow Model Element does not exist in the model", genericFlow);
		
		// Act
		tool.execute();
		
		// Assert
		IRPModelElement source = genericFlow.getEnd1();
		IRPModelElement target = genericFlow.getEnd2();
		
		// check Port
		Assert.assertTrue("End1 Flow is not a Flow Port",source instanceof IRPSysMLPort);
		Assert.assertTrue("End2 Flow is not a Flow Port", target instanceof IRPSysMLPort);
		
		// check Type
		Assert.assertTrue("SourcePort is not typed", flowItem.equals(((IRPSysMLPort) source).getType()));
		Assert.assertTrue("TargetPort is not typed", flowItem.equals(((IRPSysMLPort) target).getType()));

		// check Direction
		Assert.assertTrue("SourcePort is not Output", "Out".equals(((IRPSysMLPort) source).getPortDirection()));
		Assert.assertTrue("TargetPort is not Output", "Out".equals(((IRPSysMLPort) target).getPortDirection()));
		
		// Check if the source port has the specific stereotype
		boolean sourceHasStereotype = source.getStereotypes().toList().stream()
		    .anyMatch(stereotype -> ((IRPStereotype) stereotype).getGUID().equals(ProfilConstants.GUID_SAFRAN_HYDRAULIC));

		// Check if the target port has the specific stereotype
		boolean targetHasStereotype = target.getStereotypes().toList().stream()
		    .anyMatch(stereotype -> ((IRPStereotype) stereotype).getGUID().equals(ProfilConstants.GUID_SAFRAN_HYDRAULIC));

		Assert.assertTrue(sourceHasStereotype);
		Assert.assertTrue(targetHasStereotype);
		
		// Check if the source object has the specific stereotype
		boolean genericFlowHasStereotype = genericFlow.getStereotypes().toList().stream()
		    .anyMatch(stereotype -> ((IRPStereotype) stereotype).getGUID().equals(ProfilConstants.GUID_SAFRAN_HYDRAULIC));
		
		Assert.assertTrue(genericFlowHasStereotype);
				
		Assert.assertFalse("Conveyed shall not be not empty",genericFlow.getConveyed().toList().isEmpty());
		Assert.assertTrue("Not an Logical flow", "Logical Flow".equals(genericFlow.getUserDefinedMetaClass()));
	}
	
	
	/**
	 * Diagram: Logical Architecture Diagram
	 * Selected object: Bidirectional Generic Flow 
	 * Relation: Logical System and Operational System Port(Type+Stereotyped) 
	 */
	@Test
	public void bidirectionalGenericFlow_LogicalSystem_OperationalSystemPort() {
		// Arrange
		final String GUID_FLOW_ITEM = "GUID 3cd6e0a3-8153-4248-8244-66fbee624ddf";
		
		final String GUID_GENERIC_FLOW_13 = "GUID ff8b1f7a-b716-4c7c-9fab-4c1869bffca6";
		
		IRPFlow genericFlow = (IRPFlow) project.findElementByGUID(GUID_GENERIC_FLOW_13);
		IRPClass flowItem = (IRPClass) project.findElementByGUID(GUID_FLOW_ITEM);
		
		IRPDiagram diagram = (IRPDiagram) genericFlow.getReferences().getItem(1);

		openDiagramTimer(diagram);
		
		IRPCollection graphElements = diagram.getCorrespondingGraphicElements(genericFlow);
		rhapsodyApplication.selectGraphElements(graphElements);
		
		Assert.assertNotNull("Generic Flow Model Element does not exist in the model", genericFlow);
		
		// Act
		tool.execute();
		
		// Assert
		IRPModelElement source = genericFlow.getEnd1();
		IRPModelElement target = genericFlow.getEnd2();
		
		// check Port
		Assert.assertTrue("End1 Flow is not a Flow Port",source instanceof IRPSysMLPort);
		Assert.assertTrue("End2 Flow is not a Flow Port", target instanceof IRPSysMLPort);
		
		// check Type
		Assert.assertTrue("SourcePort is not typed", flowItem.equals(((IRPSysMLPort) source).getType()));
		Assert.assertTrue("TargetPort is not typed", flowItem.equals(((IRPSysMLPort) target).getType()));

		// check Direction
		Assert.assertTrue("SourcePort is not InOut", "InOut".equals(((IRPSysMLPort) source).getPortDirection()));
		Assert.assertTrue("TargetPort is not InOut", "InOut".equals(((IRPSysMLPort) target).getPortDirection()));
		
		// Check if the source port has the specific stereotype
		boolean sourceHasStereotype = source.getStereotypes().toList().stream()
		    .anyMatch(stereotype -> ((IRPStereotype) stereotype).getGUID().equals(ProfilConstants.GUID_SAFRAN_ELECTRICAL));

		// Check if the target port has the specific stereotype
		boolean targetHasStereotype = target.getStereotypes().toList().stream()
		    .anyMatch(stereotype -> ((IRPStereotype) stereotype).getGUID().equals(ProfilConstants.GUID_SAFRAN_ELECTRICAL));

		Assert.assertTrue(sourceHasStereotype);
		Assert.assertTrue(targetHasStereotype);
		
		// Check if the source object has the specific stereotype
		boolean genericFlowHasStereotype = genericFlow.getStereotypes().toList().stream()
		    .anyMatch(stereotype -> ((IRPStereotype) stereotype).getGUID().equals(ProfilConstants.GUID_SAFRAN_ELECTRICAL));
		
		Assert.assertTrue(genericFlowHasStereotype);
				
		Assert.assertFalse("Conveyed shall not be not empty",genericFlow.getConveyed().toList().isEmpty());
		Assert.assertTrue("Not an Logical flow", "Logical Flow".equals(genericFlow.getUserDefinedMetaClass()));
	}
	
	/**
	 * Diagram: Logical Architecture Diagram
	 * Selected object: Directional Generic Flow 
	 * Relation: Logical System and <b>PARENT</b> Logical System Port(Type+Stereotyped) 
	 */
	@Test
	public void directionalGenericFlow_LogicalSystem_ParentLogicalSystemPort() {
		// Arrange
		final String GUID_FLOW_ITEM = "GUID 3cd6e0a3-8153-4248-8244-66fbee624ddf";
		
		final String GUID_GENERIC_FLOW_14 = "GUID 75f91bee-c58d-4d6e-9096-28385fc191b5";
		
		IRPFlow genericFlow = (IRPFlow) project.findElementByGUID(GUID_GENERIC_FLOW_14);
		IRPClass flowItem = (IRPClass) project.findElementByGUID(GUID_FLOW_ITEM);
		
		IRPDiagram diagram = (IRPDiagram) genericFlow.getReferences().getItem(1);

		openDiagramTimer(diagram);
		
		IRPCollection graphElements = diagram.getCorrespondingGraphicElements(genericFlow);
		rhapsodyApplication.selectGraphElements(graphElements);
		
		Assert.assertNotNull("Generic Flow Model Element does not exist in the model", genericFlow);
		
		// Act
		tool.execute();
		
		// Assert
		IRPModelElement source = genericFlow.getEnd1();
		IRPModelElement target = genericFlow.getEnd2();
		
		// check Port
		Assert.assertTrue("End1 Flow is not a Flow Port",source instanceof IRPSysMLPort);
		Assert.assertTrue("End2 Flow is not a Flow Port", target instanceof IRPSysMLPort);
		
		// check Type
		Assert.assertTrue("SourcePort is not typed", flowItem.equals(((IRPSysMLPort) source).getType()));
		Assert.assertTrue("TargetPort is not typed", flowItem.equals(((IRPSysMLPort) target).getType()));

		// check Direction
		Assert.assertTrue("SourcePort is not Output", "Out".equals(((IRPSysMLPort) source).getPortDirection()));
		Assert.assertTrue("TargetPort is not Output", "Out".equals(((IRPSysMLPort) target).getPortDirection()));
		
		// Check if the source port has the specific stereotype
		boolean sourceHasStereotype = source.getStereotypes().toList().stream()
		    .anyMatch(stereotype -> ((IRPStereotype) stereotype).getGUID().equals(ProfilConstants.GUID_SAFRAN_HYDRAULIC));

		// Check if the target port has the specific stereotype
		boolean targetHasStereotype = target.getStereotypes().toList().stream()
		    .anyMatch(stereotype -> ((IRPStereotype) stereotype).getGUID().equals(ProfilConstants.GUID_SAFRAN_HYDRAULIC));

		Assert.assertTrue(sourceHasStereotype);
		Assert.assertTrue(targetHasStereotype);
		
		// Check if the source object has the specific stereotype
		boolean genericFlowHasStereotype = genericFlow.getStereotypes().toList().stream()
		    .anyMatch(stereotype -> ((IRPStereotype) stereotype).getGUID().equals(ProfilConstants.GUID_SAFRAN_HYDRAULIC));
		
		Assert.assertTrue(genericFlowHasStereotype);
				
		Assert.assertFalse("Conveyed shall not be not empty",genericFlow.getConveyed().toList().isEmpty());
		Assert.assertTrue("Not an Logical flow", "Logical Flow".equals(genericFlow.getUserDefinedMetaClass()));
	}

}
