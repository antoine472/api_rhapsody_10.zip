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

public class GenericFlowPortClassTest extends RhapsodyBaseTest {

	private GenericFlow tool;

	public GenericFlowPortClassTest() {
		super("./src/test/resources/GenericFlowPortClass/GenericFlowPortClass.rpyx");
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
	 * Relation: Operational System Port(Type+Stereotyped) and Operational System  
	 */
	@Test
	public void directionalGenericFlow_OperationalSystemPort_OperationalSystem() {
		// Arrange
		final String GUID_FLOW_ITEM = "GUID 3cd6e0a3-8153-4248-8244-66fbee624ddf";
		
		final String GUID_GENERIC_FLOW_1 = "GUID 4a7f5e06-c441-4321-a38b-0232820ef38b";
		
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
	 * Relation: Stakeholder Port(Type+Stereotyped) and Operational System
	 */
	@Test
	public void directionalGenericFlow_StakeholderPort_OperationalSystem() {
		// Arrange
		final String GUID_FLOW_ITEM = "GUID 3cd6e0a3-8153-4248-8244-66fbee624ddf";
		
		final String GUID_GENERIC_FLOW_2 = "GUID a093e254-c80b-4837-a346-2d13846baf4e";
		
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
	 * Diagram: Operational Context
	 * Selected object: Bidirectional Generic Flow 
	 * Relation: Operational System Port(Type+Stereotyped) and Operational System  
	 */
	@Test
	public void bidirectionalGenericFlow_OperationalSystem_StakeholderPort() {
		// Arrange
		final String GUID_FLOW_ITEM = "GUID 3cd6e0a3-8153-4248-8244-66fbee624ddf";
		
		final String GUID_GENERIC_FLOW_3 = "GUID cb38c678-cc82-4b78-b192-5c9379190adb";
		
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
		Assert.assertTrue("Not an Operational flow", "Operational Flow".equals(genericFlow.getUserDefinedMetaClass()));
	}

	
	/**
	 * Diagram: SOI - System Functional Behavior
	 * Selected object: Directional Generic Flow 
	 * Relation: Operational System Port(Type+Stereotyped) and Function 
	 */
	@Test
	public void directionalGenericFlow_OperationalSystemPort_Function() {
		// Arrange
		final String GUID_FLOW_ITEM = "GUID 3cd6e0a3-8153-4248-8244-66fbee624ddf";
		
		final String GUID_GENERIC_FLOW_4 = "GUID 0345e3a2-add3-49d2-82c1-fbd552210722";
		
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
		Assert.assertTrue("SourcePort is not Input", "In".equals(((IRPSysMLPort) source).getPortDirection()));
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
	 * Diagram: SOI - System Functional Behavior
	 * Selected object: Bidirectional Generic Flow 
	 * Relation: Operational System Port(Type+Stereotyped) and Function  
	 */
	@Test
	public void bidirectionalGenericFlow_OperationalSystemPort_Function() {
		// Arrange
		final String GUID_FLOW_ITEM = "GUID 3cd6e0a3-8153-4248-8244-66fbee624ddf";
		
		final String GUID_GENERIC_FLOW_5 = "GUID 04cb6946-dc92-45b7-b154-38c439eb3e0e";
		
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
	 * Relation: Function Port(Type+Stereotyped) and Function
	 */
	@Test
	public void directionalGenericFlow_FunctionPort_Function() {
		// Arrange
		final String GUID_FLOW_ITEM = "GUID 3cd6e0a3-8153-4248-8244-66fbee624ddf";
		
		final String GUID_GENERIC_FLOW_6 = "GUID f1c090b7-0033-4b77-8d75-22ea78337354";
		
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
	 * Relation: Function Port(Type+Stereotyped) and Function 
	 */
	@Test
	public void bidirectionalGenericFlow_FunctionPort_Function() {
		// Arrange
		final String GUID_FLOW_ITEM = "GUID 3cd6e0a3-8153-4248-8244-66fbee624ddf";
		
		final String GUID_GENERIC_FLOW_7 = "GUID 25d2bebb-12d4-4ab0-b2ce-33978c3ae76f";
		
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
	 * Relation: Logical System Port(Type+Stereotyped) and Function
	 */
	@Test
	public void directionalGenericFlow_LogicalSystemPort_Function() {
		// Arrange
		final String GUID_FLOW_ITEM = "GUID 3cd6e0a3-8153-4248-8244-66fbee624ddf";
		
		final String GUID_GENERIC_FLOW_8 = "GUID 6c4e12ff-9118-4ad4-8941-fdb2cb1e3056";
		
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
		Assert.assertTrue("SourcePort is not Input", "In".equals(((IRPSysMLPort) source).getPortDirection()));
		Assert.assertTrue("TargetPort is not Input", "In".equals(((IRPSysMLPort) target).getPortDirection()));
		
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
	 * Relation: Logical System Port(Type+Stereotyped) and Function  
	 */
	@Test
	public void bidirectionalGenericFlow_LogicalSystemPort_Function() {
		// Arrange
		final String GUID_FLOW_ITEM = "GUID 3cd6e0a3-8153-4248-8244-66fbee624ddf";
		
		final String GUID_GENERIC_FLOW_9 = "GUID 29a6b722-afde-4339-b817-71b77eed1afb";
		
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
	 * Relation: Logical System Port(Type+Stereotyped) and Logical System
	 */
	@Test
	public void directionalGenericFlow_LogicalSystemPort_LogicalSystem() {
		// Arrange
		final String GUID_FLOW_ITEM = "GUID 3cd6e0a3-8153-4248-8244-66fbee624ddf";
		
		final String GUID_GENERIC_FLOW_10 = "GUID 6d7241b8-aa2b-44d7-8b83-d34faa8f3e00";
		
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
		Assert.assertTrue("TargetPort is not Input", "In".equals(((IRPSysMLPort) target).getPortDirection()));
		
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
		
		final String GUID_GENERIC_FLOW_11 = "GUID 3a05b534-d661-4c52-8fbd-0cd2092438a5";
		
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
	 * Relation: Operational System Port(Type+Stereotyped) and Logical System
	 */
	@Test
	public void directionalGenericFlow_LogicalSystem_OperationalSystemPort() {
		// Arrange
		final String GUID_FLOW_ITEM = "GUID 3cd6e0a3-8153-4248-8244-66fbee624ddf";
		
		final String GUID_GENERIC_FLOW_12 = "GUID 589f2e75-1a06-4e1a-9250-25b1c3dfd9a5";
		
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
		Assert.assertTrue("SourcePort is not Input", "In".equals(((IRPSysMLPort) source).getPortDirection()));
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
		
		final String GUID_GENERIC_FLOW_13 = "GUID 04e661f3-7a81-4c8a-a038-4e451937f0a5";
		
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
	 * Relation: <b>PARENT</b> Logical System Port(Type+Stereotyped) and Logical System  
	 */
	@Test
	public void directionalGenericFlow_LogicalSystem_ParentLogicalSystemPort() {
		// Arrange
		final String GUID_FLOW_ITEM = "GUID 3cd6e0a3-8153-4248-8244-66fbee624ddf";
		
		final String GUID_GENERIC_FLOW_14 = "GUID 35f1aa4f-bd68-40c1-b5f6-1835684f6b08";
		
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
		Assert.assertTrue("SourcePort is not Input", "In".equals(((IRPSysMLPort) source).getPortDirection()));
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
		Assert.assertTrue("Not an Logical flow", "Logical Flow".equals(genericFlow.getUserDefinedMetaClass()));
	}

}
