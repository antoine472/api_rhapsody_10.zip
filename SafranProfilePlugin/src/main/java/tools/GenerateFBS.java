package tools;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.UIManager;

import com.telelogic.rhapsody.core.IRPApplication;
import com.telelogic.rhapsody.core.IRPClass;
import com.telelogic.rhapsody.core.IRPCollection;
import com.telelogic.rhapsody.core.IRPDependency;
import com.telelogic.rhapsody.core.IRPDiagram;
import com.telelogic.rhapsody.core.IRPGeneralization;
import com.telelogic.rhapsody.core.IRPGraphEdge;
import com.telelogic.rhapsody.core.IRPGraphElement;
import com.telelogic.rhapsody.core.IRPGraphNode;
import com.telelogic.rhapsody.core.IRPModelElement;
import com.telelogic.rhapsody.core.IRPPackage;

import main.constants.RhpMetaClassConstants;
import utils.D2Rectangle;

/**
 * <b>Generate FBS (Functional Breakdown Structure) Diagram</b>
 * <p>
 * Tool for Rhapsody that automatically generates or updates a functional breakdown diagram
 * by analyzing the nested function hierarchy within the model.
 * </p>
 *
 * <p>
 * It handles both initial diagram creation and updates to an existing diagram.
 * </p>
 * 
 * <p><b>User Workflow:</b><br>
 * 1. Select a functional element<br>
 * 2. Run the tool using contextual menu<br>
 * 3. Choose maximum recursion depth<br>
 * 4. Diagram is generated or updated
 * </p>
 *
 * @author s570589
 */
public class GenerateFBS extends RhapsodyTool {

	public static final String COMMAND = "Safran Toolkit...\\Generate FBS";

	private static final int FUNCTION_HEIGHT = 100;
	private static final int FUNCTION_WIDTH = 300;
	private static final int VERTICAL_SPACING = 20;
	private static final int HORIZONTAL_OFFSET = 100;
	private static final String FUNCTION = "Function";
	private static final String FUNCTION_WITH_REFERENCE = "Function With Reference";
	private static final String REFERENCES_FUNCTION = "References Function";

	private IRPDiagram attachedDiagram = null;
	private IRPModelElement startPoint = null;
	private int currentYPosition = 0;
	private IRPCollection graphElements;
	private final List<IRPGraphNode[]> virtualContainments = new ArrayList<IRPGraphNode[]>();

	public GenerateFBS(IRPApplication rpyApp) {
		super(rpyApp);
	}

	/**
	 * Main entry point for the tool. Handles both the creation and update of a FBS diagram.
	 */
	@Override
	public void execute() {
		rhpLog.debug("Starting execution of: " + COMMAND);

		attachedDiagram = null;
		startPoint = rhApp.getSelectedElement();
		currentYPosition = 0;
		graphElements = rhApp.createNewCollection();
		virtualContainments.clear();

		if (startPoint == null) {
			rhpLog.error("No model element selected.");
			JOptionPane.showMessageDialog(null, "Please select a model element to generate FBS.");
			return;
		}

		rhpLog.info("User selected element: " + startPoint.getName());

		// Try to find existing FBS diagram linked to the element
		@SuppressWarnings("unchecked")
		List<IRPModelElement> referencesList = startPoint.getReferences().toList();

		for (IRPModelElement reference : referencesList) {
			if (reference instanceof IRPDependency) {
				IRPDependency dependency = (IRPDependency) reference;
				if (dependency.getDependent() instanceof IRPDiagram) {
					attachedDiagram = (IRPDiagram) dependency.getDependent();
					rhpLog.info("Found existing FBS diagram: " + attachedDiagram.getName());
					break;
				}
			}
		}

		// Ask before changing an existing diagram or creating a new one.
		Integer maxDepth = selectMaxDepthInJavaWindow();
		if (maxDepth == null) return;

		if (attachedDiagram != null) {
			updatedExistingDiagram(maxDepth);
		} else {
			createNewDiagram(maxDepth);
		}

		rhpLog.debug("Completed execution of: " + COMMAND);
	}

	/**
	 * Updates an existing diagram after user confirmation.
	 */
	private void updatedExistingDiagram(int maxDepth) {
		if (alertUser()) {
			rhpLog.info("User confirmed diagram update.");
			attachedDiagram.removeGraphElements(attachedDiagram.getGraphicalElements());
			createDiagram(maxDepth);
		} else {
			rhpLog.info("User cancelled diagram update.");
		}
	}

	/**
	 * Creates a new diagram under the selected package.
	 */
	private void createNewDiagram(int maxDepth) {
		IRPPackage parentPackage = findEnclosingPackage(startPoint);
		if (parentPackage == null) {
			rhpLog.error("No package found in element hierarchy.");
			JOptionPane.showMessageDialog(null, "Cannot find a containing package to create diagram.");
			return;
		}

		attachedDiagram = (IRPDiagram) parentPackage.addNewAggr("Functional Breakdown Structure", startPoint.getName());
		attachedDiagram.setDisplayName(startPoint.getDisplayName());
		rhpLog.info("Created new FBS diagram: " + attachedDiagram.getName());
		createDiagram(maxDepth);
		attachedDiagram.addDependencyTo(startPoint);
	}

	/**
	 * User-facing confirmation if diagram exists.
	 */
	private boolean alertUser() {
		return JOptionPane.showConfirmDialog(null,
				"FBS already exists. Do you want to update it?",
				"FBS Diagram Exists",
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
	            "Select recursion depth for functions:",
	            "FBS Diagram Settings",
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
	private void createDiagram(int maxDepth) {
		rhpLog.debug("Starting diagram creation with max depth: " + maxDepth);

		addFunctionToDiagram(startPoint, 50, currentYPosition + 80, maxDepth,
				new HashSet<String>(), null);

		// Pure breakdown view: do not add model flows or port connectors.
		removePortRepresentations();
		createVirtualContainments();
		repositionRelations();
		attachedDiagram.openDiagram();
		rhpLog.debug("Diagram creation completed.");
	}

	/**
	 * Adds the visible function or reference, then recursively expands its children.
	 * A Function With Reference displays its base class's children under the
	 * reference node itself: the referenced base class is never drawn.
	 *
	 * The visited GUIDs are path-local, so the same target may legitimately be
	 * expanded in multiple independent branches without a false cycle warning.
	 */
	private void addFunctionToDiagram(IRPModelElement element, int x, int y,
			int remainingDepth, Set<String> ancestors, IRPGraphNode virtualParent) {
		if (remainingDepth == 0 || element == null) return;

		String elementGuid = element.getGUID();
		if (ancestors.contains(elementGuid)) {
			rhpLog.warn("FBS cycle detected at " + element.getName() + "; branch skipped.");
			return;
		}
		Set<String> path = new HashSet<String>(ancestors);
		path.add(elementGuid);

		rhpLog.debug("Adding function: " + element.getName()
				+ " at position: (" + x + "," + y + ")");
		IRPGraphNode node = attachedDiagram.addNewNodeForElement(
				element, x, y, FUNCTION_WIDTH, FUNCTION_HEIGHT);
		graphElements.addGraphicalItem(node);
		if (virtualParent != null) {
			virtualContainments.add(new IRPGraphNode[] { virtualParent, node });
		}

		currentYPosition = y + FUNCTION_HEIGHT + VERTICAL_SPACING;
		if (remainingDepth == 1) return;

		IRPModelElement container = element;
		if (FUNCTION_WITH_REFERENCE.equals(element.getUserDefinedMetaClass())) {
			IRPClass base = findReferencedFunction(element);
			if (base == null) {
				rhpLog.warn("FBS reference has no References Function target: "
						+ element.getName());
				return;
			}
			if (path.contains(base.getGUID())) {
				rhpLog.warn("FBS reference cycle detected: " + element.getName()
						+ " -> " + base.getName() + "; branch skipped.");
				return;
			}
			path.add(base.getGUID());
			container = base;
		}

		@SuppressWarnings("unchecked")
		List<IRPModelElement> nested = container
				.getNestedElementsByMetaClass(RhpMetaClassConstants.CLASS, 0).toList();
		for (IRPModelElement child : nested) {
			String metaClass = child.getUserDefinedMetaClass();
			if (FUNCTION.equals(metaClass) || FUNCTION_WITH_REFERENCE.equals(metaClass)) {
				addFunctionToDiagram(child, x + HORIZONTAL_OFFSET,
						currentYPosition, remainingDepth == -1 ? -1 : remainingDepth - 1,
						path, node);
			}
		}
	}

	/**
	 * Draw only hierarchical containment arrows (native and referenced).
	 * Their graphical representation does not modify the Safran model.
	 */
	/**
	 * Remove port graphics automatically exposed when an existing Logical System
	 * is dropped into a diagram. Removing their graphical representation does not
	 * delete the underlying Flow Port or change the Safran model.
	 */
	private void removePortRepresentations() {
		IRPCollection toRemove = rhApp.createNewCollection();
		int count = 0;

		@SuppressWarnings("unchecked")
		List<IRPGraphElement> elements = attachedDiagram.getGraphicalElements().toList();
		for (IRPGraphElement graphical : elements) {
			IRPModelElement model = graphical.getModelObject();
			if (model == null) continue;
			String udm = model.getUserDefinedMetaClass();
			String meta = model.getMetaClass();
			if ("Flow Port".equals(udm) || "SysMLPort".equals(meta)
					|| "Port".equals(meta)) {
				toRemove.addGraphicalItem(graphical);
				count++;
			}
		}
		if (count > 0) {
			attachedDiagram.removeGraphElements(toRemove);
		}
		rhpLog.info("FBS: removed " + count + " port graphical representations.");
	}

	private void createVirtualContainments() {
		for (IRPGraphNode[] relation : virtualContainments) {
			IRPGraphNode parent = relation[0];
			IRPGraphNode child = relation[1];
			// Match the direction of Rhapsody's native containment edges:
			// source = child (left/middle), target = parent (bottom).
			// Reversing these ends makes repositionRelations() route the
			// connector along the hierarchy gutter, not through other nodes.
			attachedDiagram.addNewEdgeByType("Containment Arrow",
					child, 0, FUNCTION_HEIGHT / 2,
					parent, FUNCTION_WIDTH / 8, FUNCTION_HEIGHT);
		}
		rhpLog.debug("Created " + virtualContainments.size()
				+ " graphical containment arrows for referenced functions.");
	}

	/** Resolve only the Safran "References Function" generalization. */
	private IRPClass findReferencedFunction(IRPModelElement reference) {
		if (!(reference instanceof IRPClass)) return null;

		IRPCollection generalizations = ((IRPClass) reference).getGeneralizations();
		if (generalizations == null) return null;

		for (Object item : generalizations.toList()) {
			if (!(item instanceof IRPGeneralization)) continue;
			IRPGeneralization generalization = (IRPGeneralization) item;
			if (REFERENCES_FUNCTION.equals(generalization.getUserDefinedMetaClass())) {
				return (IRPClass) generalization.getBaseClass();
			}
		}
		return null;
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