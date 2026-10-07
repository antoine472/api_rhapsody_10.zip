package tools;

import java.awt.Window;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import com.telelogic.rhapsody.core.IRPApplication;
import com.telelogic.rhapsody.core.IRPClass;
import com.telelogic.rhapsody.core.IRPClassifier;
import com.telelogic.rhapsody.core.IRPCollection;
import com.telelogic.rhapsody.core.IRPGeneralization;
import com.telelogic.rhapsody.core.IRPGraphElement;
import com.telelogic.rhapsody.core.IRPModelElement;
import com.telelogic.rhapsody.core.IRPProject;
import com.telelogic.rhapsody.core.IRPStereotype;
import com.telelogic.rhapsody.core.IRPSysMLPort;

import main.constants.ProfileMetaClassConstants;
import main.constants.RhpMetaClassConstants;
import main.gui.tools.FlowItemSelectorDialog;
import main.gui.tools.SelectorSpec;
import main.gui.tools.SelectorWorker;
import main.gui.tools.Toast;
import main.gui.tools.model.FlowItemScanResult;
import main.gui.tools.model.RhapsodyFlowItemScanner;

/**
 * <b>Set Reference Definition</b>
 * <p>
 * Right-click on a reference class ({@code Function With Reference} /
 * {@code Logical System With Reference}) in the browser: lets the engineer
 * pick the <i>mother</i> classifier ({@code Function} resp.
 * {@code Logical System}) in the filtered selector dialog, creates the
 * {@code "References Function"} / {@code "References Logical System"}
 * generalization to that mother and mirrors the mother's ports onto the
 * reference ({@link RedefinedPortsService#redefinePorts(IRPModelElement)}).
 * </p>
 *
 * <p><b>Nothing is renamed.</b> The only deletions are the previous
 * "References ..." generalization when the engineer replaces an existing
 * mother, and only after an explicit YES confirmation; the ports mirrored from
 * the previous mother are then deleted too (except any reused, same name, by
 * the new mother's mirror).</p>
 *
 * <p>The selector is non-modal and the command returns at once (the tool is
 * not {@code isUndoable} at plugin level). OK and Apply link the chosen mother
 * on the selector worker thread (never the EDT, where these API calls hang),
 * outside any Rhapsody callback, each in its own undo transaction: the browser
 * and the diagrams show the link and the mirrored ports right away, the
 * listener sees them, and one Ctrl+Z reverts one Apply. Escape / close box
 * just close the selector. After each successful OK / Apply the diagram
 * cleanup of the inherited ports runs a little later, in its own undo
 * transaction.</p>
 */
public class SetReferenceDefinition extends RhapsodyTool {

	/** Corresponds to the name given in the HEP file. */
	public static final String COMMAND = "Safran Toolkit...\\Set Reference Definition";

	private static final String LOG = "[Set Reference Definition] ";

	/** Same stereotype as {@link UpdateFunctionDefinition} ("References Function", New Term on Generalization). */
	private static final String GUID_FUNCTION_REFERENCE_STEREOTYPE = "GUID 33d8930c-eb78-48c8-bb70-0a76eec71123";

	/** "References Logical System" (New Term on Generalization), _id of stereotype ReferencesLogicalSystem in SafranArchitectureProfile. */
	private static final String GUID_LOGICAL_SYSTEM_REFERENCE_STEREOTYPE = "GUID deaeba2b-dc91-480e-9ae0-5e9e13d4615e";

	/** User-defined metaclasses of the reference generalizations (same strings as RedefinedPortsService). */
	private static final String REF_FUNCTION = "References Function";
	private static final String REF_LOGICAL_SYSTEM = "References Logical System";

	private static final Icon FUNCTION_ICON =
			new ImageIcon("../SafranArchitectureProfile/Icons/FunctionSingleIcon.png");

	/** System icon of the profile; null when the file is not deployed (the selector then
	 *  uses its default leaf icon: a missing system icon must not masquerade as a Function). */
	private static final Icon LOGICAL_SYSTEM_ICON =
			iconOrFallback("../SafranArchitectureProfile/Icons/SystemSingleIcon.png", null);

	/** Kind of reference selected by the engineer. */
	private enum RefKind {
		FUNCTION(ProfileMetaClassConstants.FUNCTION, "Function", REF_FUNCTION,
				GUID_FUNCTION_REFERENCE_STEREOTYPE),
		LOGICAL_SYSTEM(ProfileMetaClassConstants.LOGICAL_SYSTEM, "Logical System", REF_LOGICAL_SYSTEM,
				GUID_LOGICAL_SYSTEM_REFERENCE_STEREOTYPE);

		final String motherMetaClass;   // UDMC of the candidate mothers
		final String typeLabel;         // human label for the dialog
		final String referenceUdmc;     // UDMC of the reference generalization
		final String stereotypeGuid;    // stereotype applied on the generalization

		RefKind(String motherMetaClass, String typeLabel, String referenceUdmc, String stereotypeGuid) {
			this.motherMetaClass = motherMetaClass;
			this.typeLabel = typeLabel;
			this.referenceUdmc = referenceUdmc;
			this.stereotypeGuid = stereotypeGuid;
		}
	}

	public SetReferenceDefinition(IRPApplication rpyApp) {
		super(rpyApp);
	}

	@Override
	public void execute() {
		rhpLog.debug("Executing command: " + COMMAND);

		// 1) Selection must be a reference class (Function With Reference / Logical System With Reference).
		IRPModelElement sel = null;
		try {
			sel = rhApp.getSelectedElement();
		} catch (Exception ignore) {
		}
		if (!(sel instanceof IRPClass)) {
			rhpLog.info(LOG + "Selection is not a class.");
			Toast.showToast("Select a Function With Reference or a Logical System With Reference.", 4000);
			return;
		}
		IRPClass reference = (IRPClass) sel;
		String refName = safeName(reference);

		String selUdmc = safeUdmc(reference);
		RefKind refKind;
		if (ProfileMetaClassConstants.FUNCTION_WITH_REFERENCE.equals(selUdmc)) {
			refKind = RefKind.FUNCTION;
		} else if (ProfileMetaClassConstants.LOGICAL_SYSTEM_WITH_REFERENCE.equals(selUdmc)) {
			refKind = RefKind.LOGICAL_SYSTEM;
		} else {
			rhpLog.info(LOG + refName + " : not a reference class (UDMC='" + selUdmc + "').");
			Toast.showToast("Select a Function With Reference or a Logical System With Reference.", 4000);
			return;
		}

		IRPProject project = null;
		try {
			project = rhApp.activeProject();
		} catch (Exception ignore) {
		}
		if (project == null) {
			rhpLog.error(LOG + "No active project.");
			Toast.showToast("No active project.", 3500);
			return;
		}

		// 2) Reference stereotype must be resolvable BEFORE any model change.
		IRPStereotype referenceStereotype = resolveStereotype(project, refKind.stereotypeGuid);
		if (referenceStereotype == null) {
			rhpLog.error(LOG + "Stereotype '" + refKind.referenceUdmc + "' not found in the project ("
					+ refKind.stereotypeGuid + "). Is the Safran Architecture Profile loaded?");
			Toast.showToast("Stereotype \"" + refKind.referenceUdmc + "\" not found in the project.", 4500);
			return;
		}

		// 3) Candidate mothers: every class whose UDMC is exactly "Function" / "Logical System"
		//    (excludes the references themselves and the selected element).
		List<IRPModelElement> candidates = collectCandidates(project, refKind.motherMetaClass, reference);
		if (candidates.isEmpty()) {
			rhpLog.info(LOG + "No " + refKind.typeLabel + " found in the project.");
			Toast.showToast("No " + refKind.typeLabel + " found in the project.", 3500);
			return;
		}

		// 4) Non-blocking selector: this command returns right away, so no Rhapsody
		//    callback or plugin transaction spans the dialog session. OK and Apply
		//    run applyMother on the selector worker thread, each in its own undo
		//    transaction: the browser shows each change and the listener sees it.
		String refGuid = guidOf(reference);
		if (refGuid == null) {
			rhpLog.error(LOG + refName + " : no GUID, cannot open the selector.");
			Toast.showToast("Cannot read the GUID of " + refName + ".", 3500);
			return;
		}
		String[] lastLinkedKey = { null };   // mother linked by the last OK / Apply (worker thread only)
		FlowItemScanResult data = RhapsodyFlowItemScanner.fromRoot(candidates, project, false);
		Icon icon = (refKind == RefKind.FUNCTION) ? FUNCTION_ICON : LOGICAL_SYSTEM_ICON;
		SelectorSpec spec = new SelectorSpec("Select " + refKind.typeLabel + " definition",
				icon, refKind.motherMetaClass, refKind.typeLabel,
				(motherKey, motherElement, owner) -> {
					boolean justLinked = motherKey != null && motherKey.equals(lastLinkedKey[0]);
					boolean ok = applyMother(refGuid, refKind, motherKey, motherElement, owner, justLinked);
					if (ok) {
						lastLinkedKey[0] = motherKey;
						// The link is in place and its transaction is about to end:
						// replace the inherited ports on the diagrams, live.
						scheduleDiagramCleanup(refGuid);
					}
					return ok;
				},
				true);
		new FlowItemSelectorDialog(data, spec, SelectorWorker.shared(rhApp)).open();

		rhpLog.debug("Command execution complete: " + COMMAND + " (selector left open)");
	}

	// ------------------------------------------------------------------
	// OK / Apply (selector worker thread, outside any Rhapsody callback)
	// ------------------------------------------------------------------

	/** Delay before the diagram cleanup, so Rhapsody has drawn the new ports (as the listener). */
	private static final long DIAGRAM_CLEANUP_DELAY_MS = 400;

	private static final java.util.Timer DIAGRAM_CLEANUP =
			new java.util.Timer("SafranSetReferenceDiagram", true);

	/**
	 * Points the reference at the chosen mother. Model elements are re-fetched
	 * by GUID on this thread (the model may have changed while the selector was
	 * open; a deleted reference or mother is reported and nothing changes).
	 * Never throws: every failure goes to the Rhapsody log and a toast.
	 *
	 * @param justLinked true when this same mother is the last one this session
	 *                   linked: finding it in place is then a quiet no-op
	 * @return true when the reference link is in place
	 */
	private boolean applyMother(String refGuid, RefKind refKind, String motherKey,
			IRPModelElement motherElement, Window owner, boolean justLinked) {
		try {
			rhpLog.debug(LOG + "Apply on thread '" + Thread.currentThread().getName()
					+ "', mother key " + motherKey);
			IRPProject project = rhApp.activeProject();

			IRPModelElement refEl = findByGuid(project, refGuid);
			if (!(refEl instanceof IRPClass reference)) {
				rhpLog.error(LOG + "Reference " + refGuid + " not found (deleted?).");
				Toast.showToast("The reference no longer exists.", 3500);
				return false;
			}
			String refName = safeName(reference);

			IRPModelElement mother = findByGuid(project, motherKey);
			if (mother == null && isGuid(motherKey)) {
				rhpLog.error(LOG + "Selected " + refKind.typeLabel + " " + motherKey + " not found (deleted?).");
				Toast.showToast("The selected " + refKind.typeLabel + " no longer exists.", 3500);
				return false;
			}
			if (mother == null) mother = motherElement;   // created in the dialog: no GUID key
			String motherName = safeName(mother);
			if (!(mother instanceof IRPClass motherClass)) {
				rhpLog.error(LOG + "Selected " + refKind.typeLabel + " '" + motherName + "' is not a class.");
				Toast.showToast("Selected element is not a class: " + motherName, 3500);
				return false;
			}
			if (sameElement(mother, reference)) {
				rhpLog.info(LOG + refName + " cannot reference itself.");
				Toast.showToast("A reference cannot reference itself.", 3500);
				return false;
			}

			IRPStereotype referenceStereotype = resolveStereotype(project, refKind.stereotypeGuid);
			if (referenceStereotype == null) {
				rhpLog.error(LOG + "Stereotype '" + refKind.referenceUdmc + "' not found ("
						+ refKind.stereotypeGuid + ").");
				Toast.showToast("Stereotype \"" + refKind.referenceUdmc + "\" not found in the project.", 4500);
				return false;
			}

			// Existing mother? (read-only; the confirmation comes before any change)
			IRPGeneralization oldGen = findReferenceGeneralization(reference);
			rhpLog.debug(LOG + refName + " : current mother link " + (oldGen == null ? "none" : "found"));
			List<IRPSysMLPort> oldMirrorPorts = new ArrayList<IRPSysMLPort>();
			if (oldGen != null) {
				IRPModelElement oldMother = null;
				try { oldMother = oldGen.getBaseClass(); } catch (Exception ignore) {}
				String oldMotherName = safeName(oldMother);

				if (oldMother != null && sameElement(oldMother, motherClass)) {
					rhpLog.info(LOG + refName + " already references " + oldMotherName + ".");
					// OK right after an Apply of this mother: nothing to do, no toast.
					if (!justLinked) {
						Toast.showToast(refName + " already references " + oldMotherName + ".", 3500);
					}
					return false;
				}

				// Collected BEFORE the old link goes: their "redefines" point at its ports.
				oldMirrorPorts.addAll(RedefinedPortsService.portsRedefining(reference, oldMother));

				if (!confirmReplaceMother(owner, refName, oldMotherName, motherName, portNames(oldMirrorPorts))) {
					rhpLog.info(LOG + "Cancelled by user (keep mother '" + oldMotherName + "').");
					return false;
				}
			}

			return relinkMother(reference, refName, refKind,
					referenceStereotype, motherClass, motherName, oldGen, oldMirrorPorts);
		} catch (Throwable t) {
			rhpLog.error(LOG + "Apply failed: " + t);
			Toast.showToast("Set Reference Definition failed: " + t, 4500);
			return false;
		}
	}

	/**
	 * Deletes {@code oldGen} (confirmed; may be null), links the reference to
	 * {@code motherClass} with the reference stereotype, mirrors the mother's
	 * ports and removes the ports mirrored from the previous mother.
	 *
	 * @return true when the reference link is in place
	 */
	private boolean relinkMother(IRPClass reference, String refName, RefKind refKind,
			IRPStereotype referenceStereotype, IRPClass motherClass, String motherName,
			IRPGeneralization oldGen, List<IRPSysMLPort> oldMirrorPorts) {
		if (oldGen != null) {
			rhpLog.info(LOG + refName + " : delete previous reference generalization");
			try {
				oldGen.deleteFromProject();
			} catch (Exception e) {
				rhpLog.error(LOG + "Cannot delete the previous reference generalization: " + e.getMessage());
				Toast.showToast("Cannot delete the previous \"References\" link: " + e.getMessage(), 4500);
				return false;
			}
		}

		// Create (or reuse) the generalization to the mother and apply the reference stereotype.
		// Mirrors UpdateFunctionDefinition.ensureFunctionWithRefIsReference.
		IRPGeneralization gen = findGeneralizationTo(reference, motherClass);
		if (gen == null) {
			try {
				reference.addGeneralization((IRPClassifier) motherClass);
			} catch (Exception e) {
				rhpLog.error(LOG + "addGeneralization failed: " + e.getMessage());
				Toast.showToast("Cannot create the generalization to " + motherName + ": " + e.getMessage(), 4500);
				return false;
			}
			rhpLog.debug(LOG + refName + " : generalization added to " + safeFullPath(motherClass));
			gen = findGeneralizationTo(reference, motherClass);
		} else {
			rhpLog.debug(LOG + refName + " : generalization to " + motherName + " already exists, reused.");
		}
		if (gen == null) {
			rhpLog.error(LOG + "Generalization to " + motherName + " not found after creation.");
			Toast.showToast("Generalization to " + motherName + " could not be created.", 4500);
			return false;
		}
		try {
			gen.addSpecificStereotype(referenceStereotype);
			rhpLog.debug(LOG + refName + " : stereotype '" + refKind.referenceUdmc + "' applied on the generalization.");
		} catch (Exception e) {
			rhpLog.error(LOG + "Cannot apply stereotype '" + refKind.referenceUdmc + "': " + e.getMessage());
			Toast.showToast("Cannot apply stereotype \"" + refKind.referenceUdmc + "\": " + e.getMessage(), 4500);
			return false;
		}

		// Mirror the mother's ports onto the reference (additive / idempotent).
		boolean portsOk = false;
		try {
			portsOk = RedefinedPortsService.redefinePorts(reference);
		} catch (Exception e) {
			rhpLog.warn(LOG + refName + " : redefine ports failed - " + e.getMessage());
		}

		// Remove the ports mirrored from the previous mother, except any the new
		// mirror reused (same name -> it now redefines a port of the new mother;
		// deleting it would also drop its flows). Only after a complete mirror:
		// otherwise a same-name port may not be reused yet.
		if (!portsOk && !oldMirrorPorts.isEmpty()) {
			rhpLog.warn(LOG + refName + " : port mirror incomplete, ports of the previous mother kept"
					+ " - run \"Update Redefined Ports\".");
		} else {
			int removed = 0;
			for (IRPSysMLPort old : oldMirrorPorts) {
				if (RedefinedPortsService.redefinesPortOf(old, motherClass)) continue;
				rhpLog.info(LOG + refName + " : delete port '" + safeName(old)
						+ "' mirrored from the previous mother");
				RedefinedPortsService.deleteOrphanPort(old);
				removed++;
			}
			if (removed > 0) {
				rhpLog.info(LOG + refName + " : " + removed + " port(s) of the previous mother removed.");
			}
		}

		if (portsOk) {
			rhpLog.info(LOG + "Done: " + refName + " -> " + motherName + " (ports mirrored).");
			Toast.showToast("Set reference definition: " + refName + " -> " + motherName + " (ports mirrored)", 3500);
		} else {
			rhpLog.warn(LOG + "Done: " + refName + " -> " + motherName
					+ " but the port mirror is incomplete - run \"Update Redefined Ports\".");
			Toast.showToast("Set reference definition: " + refName + " -> " + motherName
					+ "\nPorts incomplete: run \"Update Redefined Ports\".", 4500);
		}
		return true;
	}

	/**
	 * Replaces the inherited graphical ports on every diagram showing the
	 * reference (redefined port at the same place; a definition flow drawn on
	 * the replaced port is copied onto the redefined port and kept untouched, a
	 * reference flow is reconnected), after a delay and outside the Apply's
	 * undo transaction (same as the listener's scheduleDiagramCleanup): the
	 * listener cannot do it here, because the reference link has no stereotype
	 * yet when Rhapsody notifies its creation. After the delay the cleanup is
	 * queued on the selector worker, so it runs in its OWN undo transaction and
	 * never overlaps a following Apply.
	 */
	private void scheduleDiagramCleanup(String refGuid) {
		DIAGRAM_CLEANUP.schedule(new java.util.TimerTask() {
			@Override
			public void run() {
				SelectorWorker.shared(rhApp).submit("Diagram cleanup", () -> {
					try {
						IRPModelElement reference = findByGuid(rhApp.activeProject(), refGuid);
						if (reference == null) return false;
						List<IRPGraphElement> reps = RedefinedPortsService.getGraphicalRepresentations(reference);
						if (reps.isEmpty()) return false;
						RedefinedPortsService.hideRedefinedPorts(rhApp, reps);
						return true;
					} catch (Throwable t) {
						rhpLog.warn(LOG + "diagram cleanup failed: " + t);
						return false;
					}
				}, null);
			}
		}, DIAGRAM_CLEANUP_DELAY_MS);
	}

	private static IRPModelElement findByGuid(IRPProject project, String guid) {
		if (project == null || !isGuid(guid)) return null;
		try {
			return project.findElementByGUID(guid);
		} catch (Exception e) {
			return null;
		}
	}

	// ------------------------------------------------------------------
	// Candidates
	// ------------------------------------------------------------------

	/**
	 * Every class of the project whose user-defined metaclass equals EXACTLY
	 * {@code motherMetaClass} ("Function" / "Logical System"), excluding
	 * {@code exclude} (the selected reference) and any function/system that lives
	 * inside a reference's mirrored tree (see {@link #isNestedUnderReference}):
	 * a mother must be a real element, not one nested under a reference.
	 */
	private List<IRPModelElement> collectCandidates(IRPProject project, String motherMetaClass,
			IRPModelElement exclude) {
		List<IRPModelElement> found = new ArrayList<IRPModelElement>();

		List<?> allClasses;
		try {
			allClasses = project.getNestedElementsByMetaClass(RhpMetaClassConstants.CLASS, 1).toList();
		} catch (Exception e) {
			rhpLog.error(LOG + "Cannot scan the project classes: " + e.getMessage());
			return found;
		}

		for (Object o : allClasses) {
			if (!(o instanceof IRPModelElement)) continue;
			IRPModelElement el = (IRPModelElement) o;
			try {
				if (!motherMetaClass.equals(el.getUserDefinedMetaClass())) continue;
				if (sameElement(el, exclude)) continue;
				if (isNestedUnderReference(el)) continue;   // inside a reference: not a valid mother
				found.add(el);
			} catch (Exception ignore) {
			}
		}

		rhpLog.debug(LOG + "Candidates (" + motherMetaClass + "): " + found.size());
		return found;
	}

	/**
	 * True when any owner of {@code el} (up to the project root) is a reference class
	 * ("Function With Reference" / "Logical System With Reference"). A function/system
	 * that lives inside a reference belongs to that reference's mirrored tree and is
	 * never a valid mother, so it is excluded from the candidate list (this also stops
	 * the enclosing reference from showing up as a folder in the selector).
	 */
	private static boolean isNestedUnderReference(IRPModelElement el) {
		IRPModelElement cur;
		try { cur = el.getOwner(); } catch (Exception e) { return false; }
		int guard = 0;
		while (cur != null && guard++ < 200) {
			String udmc = safeUdmc(cur);
			if (ProfileMetaClassConstants.FUNCTION_WITH_REFERENCE.equals(udmc)
					|| ProfileMetaClassConstants.LOGICAL_SYSTEM_WITH_REFERENCE.equals(udmc)) {
				return true;
			}
			try { cur = cur.getOwner(); } catch (Exception e) { break; }
		}
		return false;
	}

	// ------------------------------------------------------------------
	// Generalizations
	// ------------------------------------------------------------------

	/**
	 * The reference generalization of {@code reference} (UDMC "References Function"
	 * / "References Logical System"), or null. Mirrors
	 * {@code RedefinedPortsService.resolveMotherClassifier} but returns the
	 * generalization itself (needed to delete it).
	 */
	private static IRPGeneralization findReferenceGeneralization(IRPClass reference) {
		IRPCollection generalisations;
		try { generalisations = reference.getGeneralizations(); }
		catch (Exception e) { return null; }
		if (generalisations == null) return null;

		for (Object object : generalisations.toList()) {
			if (!(object instanceof IRPGeneralization)) continue;
			IRPGeneralization g = (IRPGeneralization) object;
			String udm;
			try { udm = g.getUserDefinedMetaClass(); } catch (Exception e) { continue; }
			if (REF_FUNCTION.equals(udm) || REF_LOGICAL_SYSTEM.equals(udm)) {
				return g;
			}
		}
		return null;
	}

	/** Generalization of {@code child} whose base is {@code base}, or null (same as UpdateFunctionDefinition). */
	private static IRPGeneralization findGeneralizationTo(IRPClass child, IRPClass base) {
		if (child == null || base == null) return null;

		IRPCollection gens;
		try { gens = child.getGeneralizations(); }
		catch (Exception e) { return null; }
		if (gens == null || gens.getCount() == 0) return null;

		for (Object o : gens.toList()) {
			if (!(o instanceof IRPGeneralization)) continue;
			IRPGeneralization g = (IRPGeneralization) o;
			IRPModelElement bc = null;
			try { bc = g.getBaseClass(); } catch (Exception ignore) {}
			if (bc != null && sameElement(bc, base)) return g;
		}
		return null;
	}

	// ------------------------------------------------------------------
	// Stereotype
	// ------------------------------------------------------------------

	/** Resolves a stereotype of the active project by GUID (same call as UpdateFunctionDefinition). */
	private IRPStereotype resolveStereotype(IRPProject project, String guid) {
		try {
			IRPModelElement el = project.findElementByGUID(guid);
			if (el instanceof IRPStereotype) return (IRPStereotype) el;
		} catch (Exception e) {
			rhpLog.warn(LOG + "findElementByGUID(" + guid + ") failed: " + e.getMessage());
		}
		return null;
	}

	// ------------------------------------------------------------------
	// Confirmation
	// ------------------------------------------------------------------

	/**
	 * Modal YES/NO confirmation before replacing an existing mother. Blocks the
	 * selector worker thread until the engineer answers. Built and shown on the
	 * EDT; on Apply it is owned by the still-open selector ({@code owner}), else
	 * (OK: the selector is already closed) it stands alone on top of Rhapsody.
	 *
	 * @return true only if the engineer clicked YES
	 */
	private boolean confirmReplaceMother(Window owner, String refName, String oldMotherName, String newMotherName,
			List<String> oldPortNames) {
		StringBuilder ports = new StringBuilder();
		if (oldPortNames.isEmpty()) {
			ports.append("No port mirrored from ").append(escapeHtml(oldMotherName)).append(" to remove.<br>");
		} else {
			ports.append("<b>The ").append(oldPortNames.size()).append(" port(s) mirrored from ")
				 .append(escapeHtml(oldMotherName))
				 .append(" will be deleted</b> (with their flows), unless the new mother has a port of the same name:<br>");
			for (String n : oldPortNames) ports.append("&nbsp;&nbsp;&bull; ").append(escapeHtml(n)).append("<br>");
		}
		String message = "<html><body style='width: 420px'>"
				+ "<b>" + escapeHtml(refName) + "</b> already references <b>" + escapeHtml(oldMotherName) + "</b>.<br><br>"
				+ "Replace the mother by <b>" + escapeHtml(newMotherName) + "</b>?<br><br>"
				+ "The current \"References\" link to " + escapeHtml(oldMotherName) + " will be removed.<br>"
				+ ports
				+ "<br>Nothing is renamed."
				+ "</body></html>";

		boolean[] yes = { false };
		Runnable ask = () -> {
			JOptionPane pane = new JOptionPane(message, JOptionPane.WARNING_MESSAGE, JOptionPane.YES_NO_OPTION);
			JDialog dialog = pane.createDialog(owner, "Set Reference Definition");
			if (owner == null) {
				dialog.setAlwaysOnTop(true);
				utils.DialogPlacement.centerOnActiveScreen(dialog);
			}
			dialog.setVisible(true);   // blocks until YES / NO / close
			dialog.dispose();
			Object value = pane.getValue();
			yes[0] = value instanceof Integer && ((Integer) value).intValue() == JOptionPane.YES_OPTION;
		};
		if (SwingUtilities.isEventDispatchThread()) {
			ask.run();
		} else {
			try {
				SwingUtilities.invokeAndWait(ask);
			} catch (Exception e) {
				rhpLog.error(LOG + "Confirmation dialog failed: " + e.getMessage());
				return false;
			}
		}
		return yes[0];
	}

	private static List<String> portNames(List<IRPSysMLPort> ports) {
		List<String> names = new ArrayList<String>();
		for (IRPSysMLPort p : ports) names.add(safeName(p));
		return names;
	}

	private static String escapeHtml(String s) {
		if (s == null) return "";
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	// ------------------------------------------------------------------
	// Utilities
	// ------------------------------------------------------------------

	/** {@code new ImageIcon(path)} when the file exists, else {@code fallback} (a missing file renders blank, never throws). */
	private static Icon iconOrFallback(String path, Icon fallback) {
		try {
			if (new File(path).isFile()) return new ImageIcon(path);
		} catch (Exception ignore) {
		}
		return fallback;
	}

	private static boolean sameElement(IRPModelElement a, IRPModelElement b) {
		if (a == b) return true;
		if (a == null || b == null) return false;
		try {
			String ga = a.getGUID();
			return ga != null && !ga.isBlank() && ga.equals(b.getGUID());
		} catch (Exception e) {
			return false;
		}
	}

	private static String safeName(IRPModelElement el) {
		try { return el != null ? el.getName() : "null"; } catch (Exception e) { return "<?>"; }
	}

	private static String safeFullPath(IRPModelElement el) {
		try { return el != null ? el.getFullPathName() : "null"; } catch (Exception e) { return safeName(el); }
	}

	private static String safeUdmc(IRPModelElement el) {
		try { return el != null ? el.getUserDefinedMetaClass() : null; } catch (Exception e) { return null; }
	}

	@Override
	public String commandName() {
		return COMMAND;
	}

	/**
	 * False: the command only opens the non-modal selector and returns. Each
	 * OK / Apply, each in-dialog edit and each diagram cleanup then runs on the
	 * selector worker in its own undo transaction (live change, one Ctrl+Z each).
	 */
	@Override
	public boolean isUndoable() {
		return false;
	}

	/** This tool opens dialogs (the selector and the replace-mother confirmation). */
	@Override
	public boolean isInteractive() {
		return true;
	}
}
