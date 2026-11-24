package tools;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.JOptionPane;

import com.telelogic.rhapsody.core.IRPApplication;
import com.telelogic.rhapsody.core.IRPClass;
import com.telelogic.rhapsody.core.IRPClassifier;
import com.telelogic.rhapsody.core.IRPCollection;
import com.telelogic.rhapsody.core.IRPDependency;
import com.telelogic.rhapsody.core.IRPDiagram;
import com.telelogic.rhapsody.core.IRPGeneralization;
import com.telelogic.rhapsody.core.IRPGraphEdge;
import com.telelogic.rhapsody.core.IRPGraphElement;
import com.telelogic.rhapsody.core.IRPGraphNode;
import com.telelogic.rhapsody.core.IRPModelElement;
import com.telelogic.rhapsody.core.IRPPackage;
import com.telelogic.rhapsody.core.IRPProject;
import com.telelogic.rhapsody.core.IRPUnit;

import main.constants.RhpMetaClassConstants;
import main.constants.Types;
import utils.D2Rectangle;
import utils.ScreenDialogs;

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
 * <p><b>Start from a function</b> (Function / Function With Reference): the
 * function is the root, then its nested functions recursively. A Function With
 * Reference shows the children of its "References Function" target.</p>
 *
 * <p><b>Start from an Operational System or a Logical System</b>: the system is
 * the root, then its functions. In addition, each function is expanded with the
 * children of every function that redefines it ("Redefines Function"
 * generalization owned by the lower-level function and pointing to it). The
 * redefining functions are not drawn: only their children are attached, and the
 * rule is applied transitively and at every level. The diagram is named after the
 * system's System Functional Behavior, or after the system when it has none.</p>
 *
 * <p><b>Location</b>: a new FBS is always created in the first
 * "B - Functional Analysis Package" located directly under the project that is
 * not read-only. When there is none, such a package is created under the
 * project.</p>
 *
 * @author s570589
 */
public class GenerateFBS extends RhapsodyTool {

	public static final String COMMAND = "Safran Toolkit...\\Generate FBS";
	private static final String COMMAND_TITLE = "Generate FBS";

	private static final int FUNCTION_HEIGHT = 100;
	private static final int FUNCTION_WIDTH = 300;
	private static final int VERTICAL_SPACING = 20;
	private static final int HORIZONTAL_OFFSET = 100;
	private static final String FUNCTION = "Function";
	private static final String FUNCTION_WITH_REFERENCE = "Function With Reference";
	private static final String REFERENCES_FUNCTION = "References Function";
	private static final String REDEFINES_FUNCTION = "Redefines Function";
	private static final String OPERATIONAL_SYSTEM = "Operational System";
	private static final String LOGICAL_SYSTEM = "Logical System";
	private static final String SYSTEM_FUNCTIONAL_BEHAVIOR = "System Functional Behavior";
	private static final String FBS_DIAGRAM = "Functional Breakdown Structure";
	private static final String FUNCTIONAL_ANALYSIS_PACKAGE = "B - Functional Analysis Package";
	private static final String DEFAULT_FA_PACKAGE_NAME = "FunctionalAnalysis";

	private IRPDiagram attachedDiagram = null;
	/** Diagram to select in the browser once the command is completed. */
	private IRPDiagram diagramToLocate = null;
	private IRPModelElement startPoint = null;
	private int currentYPosition = 0;
	private IRPCollection graphElements;
	/** True when started from a system: "Redefines Function" links are followed. */
	private boolean followRedefinitions = false;
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
		diagramToLocate = null;
		startPoint = rhApp.getSelectedElement();
		currentYPosition = 0;
		graphElements = rhApp.createNewCollection();
		virtualContainments.clear();
		followRedefinitions = false;

		if (startPoint == null) {
			rhpLog.error("No model element selected.");
			ScreenDialogs.message("Please select a model element to generate FBS.",
					COMMAND_TITLE, JOptionPane.WARNING_MESSAGE);
			return;
		}

		rhpLog.info("User selected element: " + startPoint.getName());
		followRedefinitions = isSystem(startPoint);
		if (followRedefinitions) {
			rhpLog.info("FBS started from system " + startPoint.getName()
					+ ": Redefines Function links will be followed.");
		}

		// Try to find existing FBS diagram linked to the element
		attachedDiagram = findAttachedFbs(startPoint);
		if (attachedDiagram != null) {
			rhpLog.info("Found existing FBS diagram: " + attachedDiagram.getName());
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
			if (followRedefinitions) {
				// Keep the label in sync with a renamed System Functional Behavior.
				attachedDiagram.setDisplayName(diagramDisplayName());
			}
			createDiagram(maxDepth);
		} else {
			rhpLog.info("User cancelled diagram update.");
		}
	}

	/**
	 * Creates a new diagram in the target Functional Analysis Package.
	 */
	private void createNewDiagram(int maxDepth) {
		IRPPackage parentPackage = findOrCreateFunctionalAnalysisPackage();
		if (parentPackage == null) {
			rhpLog.error("No writable Functional Analysis Package available.");
			ScreenDialogs.message(
					"Cannot find or create a Functional Analysis Package to create the FBS.",
					COMMAND_TITLE, JOptionPane.ERROR_MESSAGE);
			return;
		}

		if (followRedefinitions) {
			attachedDiagram = (IRPDiagram) addUniqueAggr(parentPackage, FBS_DIAGRAM, diagramName(), "_FBS");
			attachedDiagram.setDisplayName(diagramDisplayName());
		} else {
			attachedDiagram = (IRPDiagram) addUniqueAggr(parentPackage, FBS_DIAGRAM, startPoint.getName(), "_FBS");
			attachedDiagram.setDisplayName(startPoint.getDisplayName());
		}
		rhpLog.info("Created new FBS diagram: " + attachedDiagram.getName()
				+ " in package " + parentPackage.getName());
		createDiagram(maxDepth);
		attachedDiagram.addDependencyTo(startPoint);
	}

	/**
	 * User-facing confirmation if diagram exists.
	 */
	private boolean alertUser() {
		return ScreenDialogs.confirm("FBS already exists. Do you want to update it?",
				"FBS Diagram Exists");
	}

	/**
	 * Prompt the user to select a recursion depth. The dialog is shown on the
	 * screen where Rhapsody is used and stays on top of it.
	 * @return Max depth or -1 for unlimited, null if cancelled
	 */
	private Integer selectMaxDepthInJavaWindow() {
		String[] options = { "*", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10" };

		String selected = (String) ScreenDialogs.showInput(
				"Select recursion depth for functions:",
				"FBS Diagram Settings", options, options[0]);

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
		diagramToLocate = attachedDiagram;
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

		// Elements whose nested functions are displayed under this node.
		List<IRPModelElement> containers = collectContainers(element, path);

		Set<String> drawnChildren = new HashSet<String>();
		for (IRPModelElement container : containers) {
			@SuppressWarnings("unchecked")
			List<IRPModelElement> nested = container
					.getNestedElementsByMetaClass(RhpMetaClassConstants.CLASS, 0).toList();
			for (IRPModelElement child : nested) {
				if (!isFunction(child)) continue;
				if (!drawnChildren.add(child.getGUID())) continue;
				addFunctionToDiagram(child, x + HORIZONTAL_OFFSET,
						currentYPosition, remainingDepth == -1 ? -1 : remainingDepth - 1,
						path, node);
			}
		}
	}

	/**
	 * Returns the elements whose nested functions are displayed under the node of
	 * {@code element}:
	 * <ul>
	 * <li>the element itself, or its "References Function" target when it is a
	 * Function With Reference (the target is never drawn);</li>
	 * <li>when started from a system, the same for every function that redefines
	 * it, transitively (the redefining functions are never drawn).</li>
	 * </ul>
	 * GUIDs of traversed bases and redefining functions are added to {@code path}
	 * so that cycles are cut in the descendants.
	 */
	private List<IRPModelElement> collectContainers(IRPModelElement element, Set<String> path) {
		List<IRPModelElement> containers = new ArrayList<IRPModelElement>();
		Set<String> visited = new HashSet<String>();
		Deque<IRPModelElement> queue = new ArrayDeque<IRPModelElement>();
		queue.add(element);
		visited.add(element.getGUID());

		while (!queue.isEmpty()) {
			IRPModelElement current = queue.poll();

			IRPModelElement container = current;
			if (FUNCTION_WITH_REFERENCE.equals(current.getUserDefinedMetaClass())) {
				IRPClass base = findReferencedFunction(current);
				if (base == null) {
					rhpLog.warn("FBS reference has no References Function target: "
							+ current.getName());
					container = null;
				} else if (path.contains(base.getGUID())) {
					rhpLog.warn("FBS reference cycle detected: " + current.getName()
							+ " -> " + base.getName() + "; branch skipped.");
					container = null;
				} else {
					path.add(base.getGUID());
					container = base;
				}
			}
			if (container != null) {
				containers.add(container);
			}

			if (!followRedefinitions) continue;

			List<IRPClass> redefiners = findRedefiningFunctions(current);
			if (container != null && container != current) {
				redefiners.addAll(findRedefiningFunctions(container));
			}
			for (IRPClass redefiner : redefiners) {
				String guid = redefiner.getGUID();
				if (!visited.add(guid)) continue;
				if (path.contains(guid)) {
					rhpLog.warn("FBS redefinition cycle detected: " + redefiner.getName()
							+ " -> " + current.getName() + "; branch skipped.");
					continue;
				}
				rhpLog.debug("FBS: " + redefiner.getName() + " redefines "
						+ current.getName() + "; attaching its children.");
				path.add(guid);
				queue.add(redefiner);
			}
		}
		return containers;
	}

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
	 * Functions that redefine {@code target}: classes holding a generalization
	 * stereotyped "Redefines Function" whose base is {@code target}.
	 */
	private List<IRPClass> findRedefiningFunctions(IRPModelElement target) {
		List<IRPClass> result = new ArrayList<IRPClass>();
		if (!(target instanceof IRPClassifier)) return result;

		String targetGuid = target.getGUID();
		IRPCollection derived;
		try {
			derived = ((IRPClassifier) target).getDerivedClassifiers();
		} catch (Exception e) {
			rhpLog.warn("FBS: cannot read derived classifiers of " + target.getName() + ": " + e);
			return result;
		}
		if (derived == null) return result;

		for (Object item : derived.toList()) {
			if (!(item instanceof IRPClass)) continue;
			IRPClass candidate = (IRPClass) item;
			if (!isFunction(candidate)) continue;

			IRPCollection generalizations = candidate.getGeneralizations();
			if (generalizations == null) continue;
			for (Object g : generalizations.toList()) {
				if (!(g instanceof IRPGeneralization)) continue;
				IRPGeneralization generalization = (IRPGeneralization) g;
				if (!REDEFINES_FUNCTION.equals(generalization.getUserDefinedMetaClass())) continue;
				IRPClassifier base = generalization.getBaseClass();
				if (base != null && targetGuid.equals(base.getGUID())) {
					result.add(candidate);
					break;
				}
			}
		}
		return result;
	}

	private boolean isFunction(IRPModelElement element) {
		String metaClass = element.getUserDefinedMetaClass();
		return FUNCTION.equals(metaClass) || FUNCTION_WITH_REFERENCE.equals(metaClass);
	}

	private boolean isSystem(IRPModelElement element) {
		String metaClass = element.getUserDefinedMetaClass();
		return OPERATIONAL_SYSTEM.equals(metaClass) || LOGICAL_SYSTEM.equals(metaClass);
	}

	/**
	 * Existing FBS linked to the element by a dependency (diagram -> element).
	 * Only Functional Breakdown Structure diagrams are considered, so another
	 * breakdown diagram (e.g. the LBS of a Logical System) is never overwritten.
	 */
	private IRPDiagram findAttachedFbs(IRPModelElement element) {
		@SuppressWarnings("unchecked")
		List<IRPModelElement> referencesList = element.getReferences().toList();
		for (IRPModelElement reference : referencesList) {
			if (!(reference instanceof IRPDependency)) continue;
			IRPModelElement dependent = ((IRPDependency) reference).getDependent();
			if (dependent instanceof IRPDiagram
					&& FBS_DIAGRAM.equals(dependent.getUserDefinedMetaClass())) {
				return (IRPDiagram) dependent;
			}
		}
		return null;
	}

	/** System Functional Behavior directly owned by the selected system, if any. */
	private IRPModelElement findSystemFunctionalBehavior() {
		@SuppressWarnings("unchecked")
		List<Object> nested = startPoint.getNestedElements().toList();
		for (Object item : nested) {
			if (item instanceof IRPModelElement && SYSTEM_FUNCTIONAL_BEHAVIOR
					.equals(((IRPModelElement) item).getUserDefinedMetaClass())) {
				return (IRPModelElement) item;
			}
		}
		return null;
	}

	/** System mode: name of the System Functional Behavior, else of the system. */
	private String diagramName() {
		IRPModelElement sfb = findSystemFunctionalBehavior();
		return sfb != null ? sfb.getName() : startPoint.getName();
	}

	private String diagramDisplayName() {
		IRPModelElement sfb = findSystemFunctionalBehavior();
		return sfb != null ? sfb.getDisplayName() : startPoint.getDisplayName();
	}

	/**
	 * Creates an element with the requested name. If the name is already taken in
	 * the owner (e.g. another FBS for a homonymous function, or the LBS of the same
	 * Logical System), a suffix is added to the internal name. For diagrams the
	 * display name still shows the requested name.
	 */
	private IRPModelElement addUniqueAggr(IRPModelElement owner, String type,
			String name, String suffix) {
		try {
			return owner.addNewAggr(type, name);
		} catch (Exception e) {
			rhpLog.warn("FBS: name '" + name + "' already used in "
					+ owner.getName() + "; using a suffixed name.");
		}
		for (int i = 1; ; i++) {
			String candidate = name + suffix + (i == 1 ? "" : String.valueOf(i));
			try {
				return owner.addNewAggr(type, candidate);
			} catch (Exception e) {
				if (i >= 50) throw new IllegalStateException(
						"Cannot create " + type + " named " + name, e);
			}
		}
	}

	/**
	 * First "B - Functional Analysis Package" directly under the project that is
	 * not read-only. If there is none, a new one is created under the project.
	 */
	private IRPPackage findOrCreateFunctionalAnalysisPackage() {
		IRPProject project = startPoint.getProject();
		if (project == null) project = rhApp.activeProject();
		if (project == null) return null;

		@SuppressWarnings("unchecked")
		List<Object> packages = project
				.getNestedElementsByMetaClass(RhpMetaClassConstants.PACKAGE, 0).toList();
		for (Object item : packages) {
			if (!(item instanceof IRPPackage)) continue;
			IRPPackage pkg = (IRPPackage) item;
			if (!FUNCTIONAL_ANALYSIS_PACKAGE.equals(pkg.getUserDefinedMetaClass())) continue;
			if (isReadOnly(pkg)) {
				rhpLog.debug("FBS: skipping read-only package " + pkg.getName());
				continue;
			}
			rhpLog.info("FBS target package: " + pkg.getName());
			return pkg;
		}

		rhpLog.warn("FBS: no writable Functional Analysis Package under the project;"
				+ " creating one.");
		try {
			IRPPackage created = (IRPPackage) addUniqueAggr(project,
					FUNCTIONAL_ANALYSIS_PACKAGE, DEFAULT_FA_PACKAGE_NAME, "_");
			rhpLog.info("FBS: created Functional Analysis Package " + created.getName());
			return created;
		} catch (Exception e) {
			rhpLog.error("FBS: cannot create a Functional Analysis Package: " + e);
			return null;
		}
	}

	/** A package is read-only when it, or the unit it is saved in, is read-only. */
	private boolean isReadOnly(IRPPackage pkg) {
		try {
			if (pkg.isReadOnly() == Types.Unit.READ_ONLY) return true;
		} catch (Exception e) {
			rhpLog.debug("FBS: cannot read isReadOnly of " + pkg.getName() + ": " + e);
		}
		try {
			IRPUnit unit = pkg.getSaveUnit();
			if (unit != null && unit.isReadOnly() == Types.Unit.READ_ONLY) return true;
		} catch (Exception e) {
			rhpLog.debug("FBS: cannot read save unit of " + pkg.getName() + ": " + e);
		}
		return false;
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
	 * Selects the generated diagram in the browser so the user sees where it
	 * was created. Done after the command, when the browser refresh is restored.
	 */
	@Override
	public void afterExecute() {
		if (diagramToLocate == null) return;
		try {
			diagramToLocate.locateInBrowser();
		} catch (Exception e) {
			rhpLog.warn("Cannot locate diagram in browser: " + e);
		} finally {
			diagramToLocate = null;
		}
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