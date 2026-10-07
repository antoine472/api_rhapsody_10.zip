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

public class GenericFlowClassClassTest extends RhapsodyBaseTest {

	private GenericFlow tool;

	public GenericFlowClassClassTest() {
		super("./src/test/resources/GenericFlowClassClass/GenericFlowClassClass.rpyx");
		tool = new GenericFlow(rhapsodyApplication);
	}
	
	private void openDiagramTimer(IRPDiagram diagram) {
		
		try {
			diagram.openDiagram();
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			// TODO Bloc catch auto-généré
			e.printStackTrace();
		}
	}
	
	/**
	 * Diagram: Operational Context
	 * Selected object: Directional Generic Flow Untyped
	 * Relation: Operational System and Operational System
	 */
	@Test
	public void directionalUntypedGenericFlow_OperationalSystem_OperationalSystem() {
		// Arrange
		final String GUID_GENERIC_FLOW_1 = "GUID 4a7f5e06-c441-4321-a38b-0232820ef38b";
		
		IRPFlow genericFlow = (IRPFlow) project.findElementByGUID(GUID_GENERIC_FLOW_1);
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
		
		Assert.assertTrue("End1 Flow is not a Flow Port",source instanceof IRPSysMLPort);
		Assert.assertTrue("End2 Flow is not a Flow Port", target instanceof IRPSysMLPort);
		
		String sourceTypeGUID = ((IRPSysMLPort) source).getType().getGUID();
		String targetTypeGUID = ((IRPSysMLPort) target).getType().getGUID();
		
		Assert.assertTrue("SourcePort is not Untyped", ProfilConstants.GUID_PREDIFINED_UNTYPED.equals(sourceTypeGUID));
		Assert.assertTrue("SourcePort is not Untyped", ProfilConstants.GUID_PREDIFINED_UNTYPED.equals(targetTypeGUID));

		Assert.assertTrue("SourcePort is not Output", "Out".equals(((IRPSysMLPort) source).getPortDirection()));
		Assert.assertTrue("SourcePort is not Input", "In".equals(((IRPSysMLPort) target).getPortDirection()));
		
		Assert.assertTrue("Conveyed shall be empty",genericFlow.getConveyed().toList().isEmpty());
		Assert.assertTrue("Not an operational flow", "Operational Flow".equals(genericFlow.getUserDefinedMetaClass()));
	}
	
	
	/**
	 * Diagram: Operational Context
	 * Selected object: Directional Generic Flow Untyped
	 * Relation: Stakeholder and Operational System
	 */
	@Test
	public void directionalUntypedGenericFlow_Stakeholder_OperationalSystem() {
		// Arrange
		final String GUID_GENERIC_FLOW_2 = "GUID a093e254-c80b-4837-a346-2d13846baf4e";
		
		IRPFlow genericFlow = (IRPFlow) project.findElementByGUID(GUID_GENERIC_FLOW_2);

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
		
		Assert.assertTrue("End1 Flow is not a Flow Port",source instanceof IRPSysMLPort);
		Assert.assertTrue("End2 Flow is not a Flow Port", target instanceof IRPSysMLPort);
		
		String sourceTypeGUID = ((IRPSysMLPort) source).getType().getGUID();
		String targetTypeGUID = ((IRPSysMLPort) target).getType().getGUID();
		
		Assert.assertTrue("SourcePort is not Untyped", ProfilConstants.GUID_PREDIFINED_UNTYPED.equals(sourceTypeGUID));
		Assert.assertTrue("SourcePort is not Untyped", ProfilConstants.GUID_PREDIFINED_UNTYPED.equals(targetTypeGUID));
		
		Assert.assertTrue("SourcePort is not Output", "Out".equals(((IRPSysMLPort) source).getPortDirection()));
		Assert.assertTrue("SourcePort is not Input", "In".equals(((IRPSysMLPort) target).getPortDirection()));
		
		Assert.assertTrue("Conveyed shall be empty",genericFlow.getConveyed().toList().isEmpty());
		Assert.assertTrue("Not an operational flow", "Operational Flow".equals(genericFlow.getUserDefinedMetaClass()));
	}
	
	/**
	 * Diagram: Operational Context
	 * Selected object: Directional Generic Flow Untyped
	 * Relation: Operational System and Operational System
	 */
	@Test
	public void bidirectionalTypedGenericFlow_OperationalSystem_OperationalSystem() {
		// Arrange
		final String GUID_FLOW_ITEM = "GUID 3cd6e0a3-8153-4248-8244-66fbee624ddf";
		
		final String GUID_GENERIC_FLOW_3 = "GUID f53c9e22-fe33-4c62-a25a-919544ed69f3";
		
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
		
		Assert.assertTrue("End1 Flow is not a Flow Port",source instanceof IRPSysMLPort);
		Assert.assertTrue("End2 Flow is not a Flow Port", target instanceof IRPSysMLPort);
		
		Assert.assertTrue("SourcePort is Untyped", flowItem.equals(((IRPSysMLPort) source).getType()));
		Assert.assertTrue("TargetPort is Untyped", flowItem.equals(((IRPSysMLPort) target).getType()));
		
		Assert.assertTrue("SourcePort is not InOut", "InOut".equals(((IRPSysMLPort) source).getPortDirection()));
		Assert.assertTrue("SourcePort is not InOut", "InOut".equals(((IRPSysMLPort) target).getPortDirection()));
		
		// Check if the source object has the specific stereotype
		boolean sourceHasStereotype = source.getStereotypes().toList().stream()
		    .anyMatch(stereotype -> ((IRPStereotype) stereotype).getGUID().equals(ProfilConstants.GUID_SAFRAN_PNEUMATIC));

		// Check if the source object has the specific stereotype
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
	 * Diagram: Functional Behavior Diagram
	 * Selected object: Directional Generic Flow Untyped
	 * Relation: Function and Function
	 */
	@Test
	public void directionalUntypedGenericFlow_Function_Function() {
		// Arrange
		final String GUID_GENERIC_FLOW_2 = "GUID f1c090b7-0033-4b77-8d75-22ea78337354";
		
		IRPFlow genericFlow = (IRPFlow) project.findElementByGUID(GUID_GENERIC_FLOW_2);

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
		
		Assert.assertTrue("End1 Flow is not a Flow Port",source instanceof IRPSysMLPort);
		Assert.assertTrue("End2 Flow is not a Flow Port", target instanceof IRPSysMLPort);
		
		String sourceTypeGUID = ((IRPSysMLPort) source).getType().getGUID();
		String targetTypeGUID = ((IRPSysMLPort) target).getType().getGUID();
		
		Assert.assertTrue("SourcePort is not Untyped", ProfilConstants.GUID_PREDIFINED_UNTYPED.equals(sourceTypeGUID));
		Assert.assertTrue("SourcePort is not Untyped", ProfilConstants.GUID_PREDIFINED_UNTYPED.equals(targetTypeGUID));
		
		Assert.assertTrue("SourcePort is not Output", "Out".equals(((IRPSysMLPort) source).getPortDirection()));
		Assert.assertTrue("SourcePort is not Input", "In".equals(((IRPSysMLPort) target).getPortDirection()));
		
		Assert.assertTrue("Conveyed shall be empty",genericFlow.getConveyed().toList().isEmpty());
		Assert.assertTrue("Not an Functional flow", "Functional Flow".equals(genericFlow.getUserDefinedMetaClass()));
	}
	
	/**
	 * Diagram: Functional Behavior Diagram
	 * Selected object: Directional Generic Flow typed
	 * Relation: Operational System and Operational System
	 */
	@Test
	public void bidirectionalTypedGenericFlow_Function_Function() {
		// Arrange
		final String GUID_FLOW_ITEM = "GUID 3cd6e0a3-8153-4248-8244-66fbee624ddf";
		
		final String GUID_GENERIC_FLOW_3 = "GUID 57650c06-e2f2-4951-aae6-168f1beee73d";
		
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
		
		Assert.assertTrue("End1 Flow is not a Flow Port",source instanceof IRPSysMLPort);
		Assert.assertTrue("End2 Flow is not a Flow Port", target instanceof IRPSysMLPort);
		
		Assert.assertTrue("SourcePort is Untyped", flowItem.equals(((IRPSysMLPort) source).getType()));
		Assert.assertTrue("TargetPort is Untyped", flowItem.equals(((IRPSysMLPort) target).getType()));
		
		Assert.assertTrue("SourcePort is not InOut", "InOut".equals(((IRPSysMLPort) source).getPortDirection()));
		Assert.assertTrue("SourcePort is not InOut", "InOut".equals(((IRPSysMLPort) target).getPortDirection()));
		
		// Check if the source object has the specific stereotype
		boolean sourceHasStereotype = source.getStereotypes().toList().stream()
		    .anyMatch(stereotype -> ((IRPStereotype) stereotype).getGUID().equals(ProfilConstants.GUID_SAFRAN_PNEUMATIC));

		// Check if the source object has the specific stereotype
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
	 * Diagram: Functional Behavior Diagram
	 * Selected object: Directional Generic Flow Multiple Typed
	 * Relation: Function and Function
	 */
	@Test
	public void directionalMultipletypedGenericFlow_Function_Function() {
		// Arrange
		final String GUID_GENERIC_FLOW_2 = "GUID b32cb2a7-2928-4325-bee5-2a0c3ad828ed";
		
		IRPFlow genericFlow = (IRPFlow) project.findElementByGUID(GUID_GENERIC_FLOW_2);

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
		
		Assert.assertTrue("End1 Flow is not a Flow Port",source instanceof IRPSysMLPort);
		Assert.assertTrue("End2 Flow is not a Flow Port", target instanceof IRPSysMLPort);
		
		String sourceTypeGUID = ((IRPSysMLPort) source).getType().getGUID();
		String targetTypeGUID = ((IRPSysMLPort) target).getType().getGUID();
		
		Assert.assertTrue("SourcePort is not Untyped", ProfilConstants.GUID_PREDIFINED_UNTYPED.equals(sourceTypeGUID));
		Assert.assertTrue("SourcePort is not Untyped", ProfilConstants.GUID_PREDIFINED_UNTYPED.equals(targetTypeGUID));
		
		Assert.assertTrue("SourcePort is not Output", "Out".equals(((IRPSysMLPort) source).getPortDirection()));
		Assert.assertTrue("SourcePort is not Input", "In".equals(((IRPSysMLPort) target).getPortDirection()));
		
		Assert.assertFalse("Conveyed shall not be not empty",genericFlow.getConveyed().toList().isEmpty());
		Assert.assertTrue("Not an Functional flow", "Functional Flow".equals(genericFlow.getUserDefinedMetaClass()));
	}
	
	/**
	 * Diagram: Logical Architecture Diagram
	 * Selected object: Directional Generic Flow Untyped
	 * Relation: Logical System and Logical System
	 */
	@Test
	public void directionalUntypedGenericFlow_LogicalSystem_LogicalSystem() {
		// Arrange
		final String GUID_GENERIC_FLOW_2 = "GUID 6d7241b8-aa2b-44d7-8b83-d34faa8f3e00";
		
		IRPFlow genericFlow = (IRPFlow) project.findElementByGUID(GUID_GENERIC_FLOW_2);

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
		
		Assert.assertTrue("End1 Flow is not a Flow Port",source instanceof IRPSysMLPort);
		Assert.assertTrue("End2 Flow is not a Flow Port", target instanceof IRPSysMLPort);
		
		String sourceTypeGUID = ((IRPSysMLPort) source).getType().getGUID();
		String targetTypeGUID = ((IRPSysMLPort) target).getType().getGUID();
		
		Assert.assertTrue("SourcePort is not Untyped", ProfilConstants.GUID_PREDIFINED_UNTYPED.equals(sourceTypeGUID));
		Assert.assertTrue("SourcePort is not Untyped", ProfilConstants.GUID_PREDIFINED_UNTYPED.equals(targetTypeGUID));
		
		Assert.assertTrue("SourcePort is not Output", "Out".equals(((IRPSysMLPort) source).getPortDirection()));
		Assert.assertTrue("SourcePort is not Input", "In".equals(((IRPSysMLPort) target).getPortDirection()));
		
		Assert.assertTrue("Conveyed shall be empty",genericFlow.getConveyed().toList().isEmpty());
		Assert.assertTrue("Not an Logical flow", "Logical Flow".equals(genericFlow.getUserDefinedMetaClass()));
	}
	
	/**
	 * Diagram: Logical Architecture Diagram
	 * Selected object: bidirectional Generic Flow typed
	 * Relation: Logical System and Logical System
	 */
	@Test
	public void bidirectionalTypedGenericFlow_LogicalSystem_LogicalSystem() {
		// Arrange
		final String GUID_FLOW_ITEM = "GUID 3cd6e0a3-8153-4248-8244-66fbee624ddf";
		
		final String GUID_GENERIC_FLOW_3 = "GUID b588f4d8-1136-4307-8ef5-e5546c36c318";
		
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
		
		Assert.assertTrue("End1 Flow is not a Flow Port",source instanceof IRPSysMLPort);
		Assert.assertTrue("End2 Flow is not a Flow Port", target instanceof IRPSysMLPort);
		
		Assert.assertTrue("SourcePort is Untyped", flowItem.equals(((IRPSysMLPort) source).getType()));
		Assert.assertTrue("TargetPort is Untyped", flowItem.equals(((IRPSysMLPort) target).getType()));
		
		Assert.assertTrue("SourcePort is not InOut", "InOut".equals(((IRPSysMLPort) source).getPortDirection()));
		Assert.assertTrue("SourcePort is not InOut", "InOut".equals(((IRPSysMLPort) target).getPortDirection()));
		
		// Check if the source object has the specific stereotype
		boolean sourceHasStereotype = source.getStereotypes().toList().stream()
		    .anyMatch(stereotype -> ((IRPStereotype) stereotype).getGUID().equals(ProfilConstants.GUID_SAFRAN_PNEUMATIC));

		// Check if the source object has the specific stereotype
		boolean targetHasStereotype = target.getStereotypes().toList().stream()
		    .anyMatch(stereotype -> ((IRPStereotype) stereotype).getGUID().equals(ProfilConstants.GUID_SAFRAN_PNEUMATIC));

		Assert.assertTrue(sourceHasStereotype);
		Assert.assertTrue(targetHasStereotype);
		
		// Check if the source object has the specific stereotype
		boolean genericFlowHasStereotype = genericFlow.getStereotypes().toList().stream()
		    .anyMatch(stereotype -> ((IRPStereotype) stereotype).getGUID().equals(ProfilConstants.GUID_SAFRAN_PNEUMATIC));
		
		Assert.assertTrue(genericFlowHasStereotype);
		
		Assert.assertFalse("Conveyed shall not be not empty",genericFlow.getConveyed().toList().isEmpty());
		Assert.assertTrue("Not an Logical flow", "Logical Flow".equals(genericFlow.getUserDefinedMetaClass()));
	}
	
}
