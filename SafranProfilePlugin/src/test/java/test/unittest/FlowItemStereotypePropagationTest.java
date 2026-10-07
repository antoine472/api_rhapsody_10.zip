package test.unittest;

import org.junit.Assert;
import org.junit.Test;

import com.telelogic.rhapsody.core.*;
import test.RhapsodyBaseTest;
import tools.FlowItemStereotypePropagation;

public class FlowItemStereotypePropagationTest extends RhapsodyBaseTest {

	private FlowItemStereotypePropagation tool;

	public FlowItemStereotypePropagationTest() {
		super("./src/test/resources/FlowItemStereotypePropagate/FlowItemStereotypePropagate.rpyx");
		tool = new FlowItemStereotypePropagation(rhapsodyApplication);
	}
	//  Cas 1 : OK - FlowPort typé "FlowPort_Energie" - Propagation sur un Flow Port sans stéréotype initial
	@Test
	public void shouldPropagateElectricalToCleanFlowPort() {
		// Arrange
		IRPClass flowItem = (IRPClass) project.findElementByGUID("GUID 3cd6e0a3-8153-4248-8244-66fbee624ddf"); // Flow Item "FlowPort_Energie"
		IRPModelElement targetPort = project.findElementByGUID("GUID 2dc352b1-eba8-4925-9234-938011674bca"); //flowport_type_Energie_without_stereotype

		Assert.assertNotNull("FlowItem is missing", flowItem);
		Assert.assertNotNull("Target FlowPort is missing", targetPort);

		// Sélection du flow item dans Rhapsody
		IRPCollection selection = rhapsodyApplication.createNewCollection();
		selection.addItem(flowItem);
		rhapsodyApplication.selectModelElements(selection);

		
		// Act
		tool.execute();

		// Assert
		assertStereotypeApplied(targetPort, "Electrical");
	}

	


	//  Cas 2 : NOK - FlowPort typé "FlowPort_Air" - Ne doit rien faire si plusieurs stéréotypes exclusifs
	@Test
	public void shouldNotPropagateIfFlowItemHasMultipleExclusiveStereotypes() {
	    // Arrange
	    IRPClass flowItem = (IRPClass) project.findElementByGUID("GUID 5414fa6d-f9e4-4ea9-8f5c-348dbf4f52f8"); // Flow item "FlowPort_Air"
	    IRPModelElement targetPort = project.findElementByGUID("GUID 8c496f6d-d43a-41ba-afbe-cdb1b79ff05b");   // flowport_typed_Air

	    Assert.assertNotNull("Flow item with multiple exclusives is missing", flowItem);
	    Assert.assertNotNull("Target flow port is missing", targetPort);

	    // Sélectionne le flow item dans Rhapsody
	    IRPCollection selection = rhapsodyApplication.createNewCollection();
	    selection.addItem(flowItem);
	    rhapsodyApplication.selectModelElements(selection);

	    // Act
	    tool.execute();

	    // Assert
	    assertStereotypeNotApplied(targetPort, "Mechanical");
	    assertStereotypeNotApplied(targetPort, "Pneumatic");
	}



	// Cas 3 : OK - FlowPort typé "FlowPort_Energie" et déja stéréotypé "Thermal" (non exclusif)
	//Ajouter Electrical en gardant Thermal (non exclusif)
	@Test
	public void shouldPreserveThermalAndAddElectrical() {
	    // Arrange
	    IRPClass flowItem = (IRPClass) project.findElementByGUID("GUID 3cd6e0a3-8153-4248-8244-66fbee624ddf"); // Flow Item "FlowPort_Energie"
	    IRPModelElement targetPort = project.findElementByGUID("GUID e1e29c6f-2bb5-453e-8cf3-799fb8dfb2c9");   // flowport_typed_Energie_and_stereotyped_Thermal

	    Assert.assertNotNull("Flow item is missing", flowItem);
	    Assert.assertNotNull("Target flow port is missing", targetPort);

	    // Avant propagation
	    Assert.assertTrue("Thermal should already be present", hasStereotype(targetPort, "Thermal"));
	    Assert.assertFalse("Electrical should not be present before propagation", hasStereotype(targetPort, "Electrical"));

	    // Sélectionne le flow item
	    IRPCollection selection = rhapsodyApplication.createNewCollection();
	    selection.addItem(flowItem);
	    rhapsodyApplication.selectModelElements(selection);

	    // Act
	    tool.execute();

	    // Assert
	    assertStereotypeApplied(targetPort, "Electrical");  // Electrical doit être ajouté
	    assertStereotypeApplied(targetPort, "Thermal");     // Thermal doit rester
	}



	// Cas 4 : OK - FlowPort typé "FlowPort_Energie" et déja stéréotypé "Hydraulic" (exclusif)
	//Remplacement d'un stéréotype exclusif existant (Hydraulic -> Electrical)
	@Test
	public void shouldReplaceExclusiveStereotype() {
	    // Arrange
	    IRPClass flowItem = (IRPClass) project.findElementByGUID("GUID 3cd6e0a3-8153-4248-8244-66fbee624ddf"); // Flow Item "FlowPort_Energie"
	    IRPModelElement targetPort = project.findElementByGUID("GUID 1ef64f4c-cd69-45e6-9a50-8d4506df2dfc");  // flowport_typed_Energie_and_streotyped_Hydraulic

	    Assert.assertNotNull("Flow item is missing", flowItem);
	    Assert.assertNotNull("Target flow port is missing", targetPort);

	    // Avant propagation
	    Assert.assertTrue("Hydraulic should be present before propagation", hasStereotype(targetPort, "Hydraulic"));
	    Assert.assertFalse("Electrical should not be present before propagation", hasStereotype(targetPort, "Electrical"));

	    // Sélection du flow item
	    IRPCollection selection = rhapsodyApplication.createNewCollection();
	    selection.addItem(flowItem);
	    rhapsodyApplication.selectModelElements(selection);

	    // Act
	    tool.execute();

	    // Assert après propagation
	    assertStereotypeApplied(targetPort, "Electrical");   // Electrical doit être appliqué
	    assertStereotypeNotApplied(targetPort, "Hydraulic"); // Hydraulic doit être supprimé
	}


	// Cas 5 : NOK - FlowPort non typé 
	// Ne pas propager de stéréotype à un port sans type
	@Test
	public void shouldNotPropagateToUntypedFlowPort() {
	    // Arrange
	    IRPClass flowItem = (IRPClass) project.findElementByGUID("GUID 3cd6e0a3-8153-4248-8244-66fbee624ddf"); // Flow Item "FlowPort_Energie"
	    IRPModelElement untypedPort = project.findElementByGUID("GUID 8b8f23ef-804f-4b49-ac79-e90cc80f0797");  // flowport2_whithout_type

	    Assert.assertNotNull("Flow item is missing", flowItem);
	    Assert.assertNotNull("Untyped Flow Port is missing", untypedPort);

	    // Sélectionner le FlowItem dans Rhapsody
	    IRPCollection selection = rhapsodyApplication.createNewCollection();
	    selection.addItem(flowItem);
	    rhapsodyApplication.selectModelElements(selection);

	    // Act
	    tool.execute();

	    // Assert : aucun stéréotype "Electrical" ne doit être appliqué
	    assertStereotypeNotApplied(untypedPort, "Electrical");
	}


	// Cas 6 : OK - DataFlow typé avec le flow item "DataFlow_Energie"
	//Propagation uniquement sur Data Flow
	@Test
	public void shouldOnlyPropagateToSpecificDataFlowTarget() {
	    // Arrange
	    IRPClass flowItem = (IRPClass) project.findElementByGUID("GUID eae66002-0489-4124-90e4-8c1f9186204a"); // FlowItem "DataFlow_Energie"
	    IRPModelElement dataFlowTarget = project.findElementByGUID("GUID 2c1cda7d-66d7-4fdb-8f65-b45ff366b620"); // DataFlow typed with flow item "DataFlowEnergie

	    Assert.assertNotNull("Flow item is missing", flowItem);
	    Assert.assertNotNull("Target of Data Flow is missing", dataFlowTarget);

	    // Sélectionner FlowItem dans Rhapsody
	    IRPCollection selection = rhapsodyApplication.createNewCollection();
	    selection.addItem(flowItem);
	    rhapsodyApplication.selectModelElements(selection);

	    // Act
	    tool.execute();

	    // Assert
	    assertStereotypeApplied(dataFlowTarget, "Electrical");
	}




	//Cas 7 (NOK) : Le flow typé par un Flow Item avec plusieurs stéréotypes exclusifs
	//(ici : "Mechanical" et "Pneumatic") ne doit recevoir aucun stéréotype.
	@Test
	public void shouldNotPropagateIfFlowIsTypedByItemWithMultipleExclusiveStereotypes() {
	    // Arrange
	    IRPClass flowItem = (IRPClass) project.findElementByGUID("GUID 9e1f6c28-eb16-4993-bec6-750ba16be303"); // FlowItem: "Flow_Air" (Mechanical, Pneumatic)
	    IRPModelElement flow = project.findElementByGUID("GUID 25fc781f-ba4c-4000-9a7f-ba83a9a93470");          // Flow typed by Flow_Air

	    Assert.assertNotNull("Flow item with multiple exclusive stereotypes is missing", flowItem);
	    Assert.assertNotNull("Flow element is missing", flow);

	    // Sélection du FlowItem
	    IRPCollection selection = rhapsodyApplication.createNewCollection();
	    selection.addItem(flowItem);
	    rhapsodyApplication.selectModelElements(selection);

	    // Act
	    tool.execute();

	    // Assert
	    assertStereotypeNotApplied(flow, "Mechanical");
	    assertStereotypeNotApplied(flow, "Pneumatic");
	}

	
	//  Cas 8 : OK - Propagation sur un Flow sans stéréotype initial et typé avec le flow item : Flow_Energie
	// doit récupéré le stéréotype "Electrical"
	@Test
	public void shouldPropagateElectricalToCleanFlow() {
	    // Arrange
	    IRPClass flowItem = (IRPClass) project.findElementByGUID("GUID 3a06f168-1073-4583-a71f-9f72b8f8eac6"); // FlowItem: Flow_Energie (Electrical)
	    IRPModelElement flow = project.findElementByGUID("GUID 4749e1d3-6df6-4f13-aa46-520d9cddac0a");     // Clean Flow (typed Flow_Energie)

	    Assert.assertNotNull("Flow item is missing", flowItem);
	    Assert.assertNotNull("Flow element is missing", flow);

	    // Sélection du FlowItem dans Rhapsody
	    IRPCollection selection = rhapsodyApplication.createNewCollection();
	    selection.addItem(flowItem);
	    rhapsodyApplication.selectModelElements(selection);

	    // Act
	    tool.execute();

	    // Assert
	    assertStereotypeApplied(flow, "Electrical");
	}



	private void assertStereotypeApplied(IRPModelElement element, String stereotypeName) {
		boolean hasStereotype = element.getStereotypes().toList().stream()
				.filter(s -> s instanceof IRPStereotype)
				.map(s -> ((IRPStereotype) s).getName())
				.anyMatch(name -> name.equals(stereotypeName));

		Assert.assertTrue("Expected stereotype '" + stereotypeName + "' not found on " + element.getFullPathName(), hasStereotype);
	}

	private void assertStereotypeNotApplied(IRPModelElement element, String stereotypeName) {
	    boolean hasStereotype = element.getStereotypes().toList().stream()
	        .filter(s -> s instanceof IRPStereotype)
	        .map(s -> ((IRPStereotype) s).getName())
	        .anyMatch(name -> name.equals(stereotypeName));

	    Assert.assertFalse("Unexpected stereotype '" + stereotypeName + "' found on " + element.getFullPathName(), hasStereotype);
	}


	private boolean hasStereotype(IRPModelElement element, String stereotypeName) {
		IRPCollection stereotypes = element.getStereotypes();
		for (Object s : stereotypes.toList()) {
			if (s instanceof IRPStereotype) {
				IRPStereotype st = (IRPStereotype) s;
				if (st.getName().equals(stereotypeName)) {
					return true;
				}
			}
		}
		return false;
	}
}
