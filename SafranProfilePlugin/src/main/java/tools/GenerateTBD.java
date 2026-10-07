package tools;

import java.util.List;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.UIManager;

import com.telelogic.rhapsody.core.IRPApplication;
import com.telelogic.rhapsody.core.IRPCollection;
import com.telelogic.rhapsody.core.IRPDependency;
import com.telelogic.rhapsody.core.IRPDiagram;
import com.telelogic.rhapsody.core.IRPGraphEdge;
import com.telelogic.rhapsody.core.IRPGraphElement;
import com.telelogic.rhapsody.core.IRPGraphNode;
import com.telelogic.rhapsody.core.IRPModelElement;
import com.telelogic.rhapsody.core.IRPPackage;

import main.constants.RhpMetaClassConstants;
import utils.D2Rectangle;

/**
 * <b>Generate TBS (Technical Breakdown Structure) Diagram</b>
 * <p>
 * Tool for Rhapsody that automatically generates or updates a technical breakdown diagram
 * by analyzing the nested Technical  hierarchy within the model.
 * </p>
 *
 * <p>
 * It handles both initial diagram creation and updates to an existing diagram.
 * </p>
 * 
 * <p><b>User Workflow:</b><br>
 * 1. Select a technical element<br>
 * 2. Run the tool using contextual menu<br>
 * 3. Choose maximum recursion depth<br>
 * 4. Diagram is generated or updated
 * </p>
 *
 * @author s570589
 */
public class GenerateTBD extends RhapsodyTool {

	public static final String COMMAND = "Safran Toolkit...\\Generate TBS";

	private static final int TECHNICAL_HEIGHT = 100;
	private static final int TECHNICAL_WIDTH = 300;
	private static final int VERTICAL_SPACING = 20;
	private static final int HORIZONTAL_OFFSET = 100;

	private IRPDiagram attachedDiagram = null;
	private IRPModelElement startPoint = null;
	private int currentYPosition = 0;
	private IRPCollection graphElements;

	public GenerateTBD(IRPApplication rpyApp) {
		super(rpyApp);
	}

	/**
	 * Main entry point for the tool. Handles both the creation and update of a TBD diagram.
	 */
	@Override
	public void execute() {
		rhpLog.debug("Starting execution of: " + COMMAND);

		attachedDiagram = null;
		startPoint = rhApp.getSelectedElement();
		currentYPosition = 0;
		graphElements = rhApp.createNewCollection();

		if (startPoint == null) {
			rhpLog.error("No model element selected.");
			JOptionPane.showMessageDialog(null, "Please select a model element to generate TBD.");
			return;
		}

		rhpLog.info("User selected element: " + startPoint.getName());

		// Try to find existing TBD diagram linked to the element
		@SuppressWarnings("unchecked")
		List<IRPModelElement> referencesList = startPoint.getReferences().toList();

		for (IRPModelElement reference : referencesList) {
			if (reference instanceof IRPDependency) {
				IRPDependency dependency = (IRPDependency) reference;
				if (dependency.getDependent() instanceof IRPDiagram) {
					attachedDiagram = (IRPDiagram) dependency.getDependent();
					rhpLog.info("Found existing TBD diagram: " + attachedDiagram.getName());
					break;
				}
			}
		}

		// Update or create diagram
		if (attachedDiagram != null) {
			updatedExistingDiagram();
		} else {
			createNewDiagram();
		}

		rhpLog.debug("Completed execution of: " + COMMAND);
	}

	/**
	 * Updates an existing diagram after user confirmation.
	 */
	private void updatedExistingDiagram() {
		if (alertUser()) {
			rhpLog.info("User confirmed diagram update.");
			attachedDiagram.removeGraphElements(attachedDiagram.getGraphicalElements());
			createDiagram();
		} else {
			rhpLog.info("User cancelled diagram update.");
		}
	}

	/**
	 * Creates a new diagram under the selected package.
	 */
	private void createNewDiagram() {
		IRPPackage parentPackage = findEnclosingPackage(startPoint);
		if (parentPackage == null) {
			rhpLog.error("No package found in element hierarchy.");
			JOptionPane.showMessageDialog(null, "Cannot find a containing package to create diagram.");
			return;
		}

		attachedDiagram = (IRPDiagram) parentPackage.addNewAggr("Technical Breakdown Diagram", startPoint.getName());
		attachedDiagram.setDisplayName(startPoint.getDisplayName());
		rhpLog.info("Created new TBD diagram: " + attachedDiagram.getName());
		createDiagram();
		attachedDiagram.addDependencyTo(startPoint);
	}

	/**
	 * User-facing confirmation if diagram exists.
	 */
	private boolean alertUser() {
		return JOptionPane.showConfirmDialog(null,
				"TBD already exists. Do you want to update it?",
				"TBD Diagram Exists",
				JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
	}

	/**
	 * Prompt the user to select a recursion depth.
	 * @return Max depth or -1 for unlimited, null if cancelled
	 */
	private Integer selectMaxDepthInJavaWindow() {
	    String[] options = { "*", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10" };

	    try {
	        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
	    } catch (Exception e) {
	        rhpLog.error("Could not set native look and feel: " + e);
	    }

	    // Create a hidden always-on-top frame to anchor the dialog
	    JFrame frame = new JFrame();
	    frame.setUndecorated(true);
	    frame.setAlwaysOnTop(true);
	    frame.setLocationRelativeTo(null); // Center on default screen
	    frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
	    frame.setVisible(true); // Necessary to bring it to front

	    // Show the dialog, blocking here until user input is provided
	    String selected = (String) JOptionPane.showInputDialog(
	            frame,
	            "Select recursion depth for technical component:",
	            "TBD Diagram Settings",
	            JOptionPane.QUESTION_MESSAGE,
	            null,
	            options,
	            options[0]);

	    // Clean up
	    frame.dispose();

	    if (selected == null) {
	        rhpLog.info("User cancelled depth selection.");
	        return null;
	    }

	    return selected.equals("*") ? -1 : Integer.parseInt(selected);
	}

	/**
	 * Entry point for recursive diagram creation.
	 */
	private void createDiagram() {
		Integer maxDepth = selectMaxDepthInJavaWindow();
		if (maxDepth == null) return;

		rhpLog.debug("Starting diagram creation with max depth: " + maxDepth);

		addTechnicalComponenetToDiagram(startPoint, 50, currentYPosition + 80, 1, maxDepth);
		
		attachedDiagram.completeRelations(graphElements, 1);
		rhpLog.debug("Completed containment relations.");
		repositionRelations();
		attachedDiagram.openDiagram();
		rhpLog.debug("Diagram creation completed.");
	}



	/**
	 * Recursively adds Technical Component to the diagram.
	 */
	private int addTechnicalComponenetToDiagram(IRPModelElement element, int x, int y, int level, int maxDepth) {
		if (maxDepth == 0) return 0;
		
		
		rhpLog.debug("Adding technical : " + element.getName() + " at level " + level + ", position: (" + x + "," + y + ")");

		IRPGraphNode node = attachedDiagram.addNewNodeForElement(element, x, y, TECHNICAL_WIDTH, TECHNICAL_HEIGHT);
		graphElements.addGraphicalItem(node);

		int nextX = x + HORIZONTAL_OFFSET;
		int nextY = y + TECHNICAL_HEIGHT + VERTICAL_SPACING;
		currentYPosition = nextY;

		@SuppressWarnings("unchecked")
		List<IRPModelElement> nested = element.getNestedElementsByMetaClass(RhpMetaClassConstants.CLASS, 0).toList();

		for (IRPModelElement child : nested) {
			if ("Technical Component".equals(child.getUserDefinedMetaClass())) {
				addTechnicalComponenetToDiagram(child, nextX, currentYPosition, level + 1, maxDepth == -1 ? -1 : maxDepth - 1);
			}
			else if ("Technical Component With Reference".equals(child.getUserDefinedMetaClass())) {
				addTechnicalComponenetToDiagram(child, nextX, currentYPosition, level + 1, maxDepth == -1 ? -1 : maxDepth - 1);
			}
		}
		return TECHNICAL_HEIGHT;
	}

	/**
	 * Repositions edge source and target points based on node geometry.
	 */
	private void repositionRelations() {
		rhpLog.debug("Starting reposition of relations.");

		@SuppressWarnings("unchecked")
		List<IRPGraphElement> elements = attachedDiagram.getGraphicalElements().toList();

		for (IRPGraphElement element : elements) {
			if (element instanceof IRPGraphEdge) {
				IRPGraphEdge edge = (IRPGraphEdge) element;

				D2Rectangle source = new D2Rectangle(
						edge.getSource().getGraphicalProperty("Width").getValue(),
						edge.getSource().getGraphicalProperty("Height").getValue(),
						edge.getSource().getGraphicalProperty("Position").getValue());

				D2Rectangle target = new D2Rectangle(
						edge.getTarget().getGraphicalProperty("Width").getValue(),
						edge.getTarget().getGraphicalProperty("Height").getValue(),
						edge.getTarget().getGraphicalProperty("Position").getValue());

				rhpLog.debug("Repositioning edge - Source: " + source.getSourcePosition() + ", Target: " + target.getTargetPosition());

				edge.setGraphicalProperty("SourcePosition", source.getSourcePosition());
				edge.setGraphicalProperty("TargetPosition", target.getTargetPosition());
			}
		}

		rhpLog.debug("Finished repositioning relations.");
	}

	/**
	 * Utility to find the enclosing IRPPackage of an element.
	 */
	private IRPPackage findEnclosingPackage(IRPModelElement element) {
		while (element != null) {
			if (element instanceof IRPPackage) {
				return (IRPPackage) element;
			}
			element = element.getOwner();
		}
		return null;
	}

	@Override
	public String commandName() {
		return COMMAND;
	}

	@Override
	public boolean isUndoable() {
		return true;
	}
}
