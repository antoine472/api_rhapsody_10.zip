package tools;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.WindowConstants;

import com.telelogic.rhapsody.core.IRPApplication;
import com.telelogic.rhapsody.core.IRPCollection;
import com.telelogic.rhapsody.core.IRPDiagram;
import com.telelogic.rhapsody.core.IRPGraphElement;
import com.telelogic.rhapsody.core.IRPModelElement;

import main.gui.tools.Toast;
import main.gui.tools.UiKit;

/**
 * <b>Clean Redefined Ports in Diagram</b>
 * <p>
 * Cleans a Structure Diagram: for each Safran reference
 * ({@code FunctionWithReference} / {@code LogicalSystemReference}) shown in
 * the diagram, replaces each <i>inherited</i> port by its redefined port drawn
 * at the same place (exactly like the drag &amp; drop behaviour). A flow of the
 * definition drawn on it is <b>copied</b> onto the redefined port (same
 * characteristics, name and label from the two boxes it joins) and kept
 * untouched; a flow already attached to a reference is reconnected. Nothing is
 * deleted. Reuses {@link RedefinedPortsService}.
 * </p>
 *
 * <p><b>Scope</b>:</p>
 * <ul>
 *   <li>the references among the selected graphical elements, if any;</li>
 *   <li>otherwise every reference shown in the active diagram.</li>
 * </ul>
 *
 * <p><b>Nothing is changed before confirmation.</b> A modal dialog lists the
 * inherited ports to replace and the flows to copy, reconnect or left as is; the
 * engineer clicks Apply or Cancel. The undo transaction and refresh handling
 * are managed by {@code SafranProfilePlugin} (this tool is {@code isUndoable}
 * and {@code isInteractive}).</p>
 */
public class CleanRedefinedPortsInDiagram extends RhapsodyTool {

	/** Corresponds to the name given in the HEP file. */
	public static final String COMMAND = "Safran Toolkit...\\Clean Redefined Ports in Diagram";

	private static final String LOG = "[Clean Redefined Ports] ";

	public CleanRedefinedPortsInDiagram(IRPApplication rpyApp) {
		super(rpyApp);
	}

	/** What will be done for one reference representation in the diagram. */
	private static final class RefClean {
		final IRPGraphElement ge;
		final IRPModelElement refModel;
		final String refName;
		final List<IRPGraphElement> inheritedPorts;   // ports hérités à remplacer
		final List<String> copy;                      // definition flows to copy: "name -> copy R1_p_2_X_q on p_2 (...)"
		final List<String> reconnect;                 // reference flows to reconnect: names
		final List<String> left;                      // flows left as is: "name (reason)"

		RefClean(IRPGraphElement ge, IRPModelElement refModel, String refName,
				List<IRPGraphElement> inheritedPorts, List<String> copy, List<String> reconnect, List<String> left) {
			this.ge = ge;
			this.refModel = refModel;
			this.refName = refName;
			this.inheritedPorts = inheritedPorts;
			this.copy = copy;
			this.reconnect = reconnect;
			this.left = left;
		}

		int portCount() { return inheritedPorts.size(); }

		boolean hasWork() {
			return !inheritedPorts.isEmpty() || !copy.isEmpty() || !reconnect.isEmpty() || !left.isEmpty();
		}
	}

	@Override
	public void execute() {

		// 1) Resolve scope: selected references, else every reference of the active diagram.
		List<IRPGraphElement> refGraphElements = resolveReferences();
		if (refGraphElements == null) {
			return;   // no active diagram (already logged / toasted)
		}
		if (refGraphElements.isEmpty()) {
			rhpLog.info(LOG + "No reference in the diagram.");
			Toast.showToast("No reference to clean in this diagram.", 3500);
			return;
		}

		// 2) Dry-run plan (NO model change yet).
		List<RefClean> plan = new ArrayList<RefClean>();
		int totalRefs = 0;
		List<String> allPortLines = new ArrayList<String>();
		List<String> allCopyLines = new ArrayList<String>();
		List<String> allReconnectLines = new ArrayList<String>();
		List<String> allLeftLines = new ArrayList<String>();
		// A trait between two references of the diagram (R1.p to R2.p) is seen from
		// both boxes: listed once, under the first one (its copy name holds both).
		List<RedefinedPortsService.FlowPlan> seenTraits = new ArrayList<RedefinedPortsService.FlowPlan>();

		for (IRPGraphElement ge : refGraphElements) {
			IRPModelElement refModel = safeModelObject(ge);
			String refName = safeName(refModel);

			List<IRPGraphElement> inherited;
			try {
				inherited = RedefinedPortsService.inheritedGraphicalPorts(ge);
			} catch (Exception e) {
				rhpLog.warn(LOG + refName + " : cannot list inherited ports - " + e.getMessage());
				inherited = new ArrayList<IRPGraphElement>();
			}

			List<String> copy = new ArrayList<String>();
			List<String> reconnect = new ArrayList<String>();
			List<String> left = new ArrayList<String>();
			try {
				for (RedefinedPortsService.FlowPlan f : RedefinedPortsService.planFlowReconnections(ge)) {
					if (alreadyListed(seenTraits, f)) continue;
					seenTraits.add(f);
					switch (f.kind) {
						case COPY: copy.add(f.describe()); break;
						case RECONNECT: reconnect.add(f.describe()); break;
						default: left.add(f.describe()); break;
					}
				}
			} catch (Exception e) {
				rhpLog.warn(LOG + refName + " : cannot list attached flows - " + e.getMessage());
			}

			RefClean rc = new RefClean(ge, refModel, refName, inherited, copy, reconnect, left);
			plan.add(rc);

			if (rc.hasWork()) {
				totalRefs++;
				for (IRPGraphElement gp : rc.inheritedPorts) {
					allPortLines.add(rc.refName + " : " + safeName(safeModelObject(gp)));
				}
				for (String fn : rc.copy) allCopyLines.add(rc.refName + " : " + fn);
				for (String fn : rc.reconnect) allReconnectLines.add(rc.refName + " : " + fn);
				for (String fn : rc.left) allLeftLines.add(rc.refName + " : " + fn);
				rhpLog.info(LOG + rc.refName + " : redefine + replace " + rc.portCount()
						+ " inherited port(s), copy " + rc.copy + ", reconnect " + rc.reconnect
						+ ", leave " + rc.left);
			}
		}

		// 3) Nothing to do.
		if (totalRefs == 0) {
			rhpLog.info(LOG + "Diagram already clean: " + refGraphElements.size()
					+ " reference(s), nothing to replace, copy or reconnect.");
			Toast.showToast("Diagram already clean.", 3000);
			return;
		}

		// 4) Confirmation BEFORE any change.
		boolean applied = showConfirmDialog(totalRefs, allPortLines, allCopyLines, allReconnectLines, allLeftLines);
		if (!applied) {
			rhpLog.info(LOG + "Cancelled by user.");
			return;
		}

		// 5) Apply (the plugin already wraps this in an undo transaction).
		int hidden = 0;
		int redefined = 0;
		for (RefClean rc : plan) {
			if (!rc.hasWork()) continue;

			// a. Ensure the reference mirrors the mother (create/complete the
			//    redefined ports + links), exactly like the drag & drop. Additive.
			try {
				if (RedefinedPortsService.redefinePorts(rc.refModel)) redefined++;
			} catch (Exception e) {
				rhpLog.warn(LOG + rc.refName + " : redefine ports failed - " + e.getMessage());
			}

			// b. Replace each inherited port by its redefined port, drawn at the same
			//    place; copy the definition flows drawn on it onto the redefined port
			//    and reconnect the reference flows (an inherited port without
			//    redefinition stays drawn, with its flows).
			try {
				hidden += RedefinedPortsService.hideRedefinedPorts(rhApp, rc.ge);
			} catch (Exception e) {
				rhpLog.warn(LOG + rc.refName + " : hiding inherited ports failed - " + e.getMessage());
			}
		}

		// Flow counts are the planned ones (the swap reports port graphics, not
		// flows): the log has the outcome of each flow.
		boolean flows = !allCopyLines.isEmpty() || !allReconnectLines.isEmpty();
		String summary = totalRefs + " reference(s), " + hidden + " inherited port(s) replaced"
				+ (flows ? ", flows handled as listed (" + allCopyLines.size() + " to copy, "
						+ allReconnectLines.size() + " to reconnect)." : ".");
		rhpLog.info(LOG + "Done: " + summary);
		Toast.showToast("Clean Redefined Ports: " + summary + (flows ? "\nFlow details in the log." : ""), 3500);
	}

	/** True when a plan for the same drawn trait was already listed under another reference. */
	private static boolean alreadyListed(List<RedefinedPortsService.FlowPlan> seen, RedefinedPortsService.FlowPlan f) {
		for (RedefinedPortsService.FlowPlan s : seen) {
			if (s.sameTrait(f)) return true;
		}
		return false;
	}

	// ------------------------------------------------------------------
	// Scope resolution
	// ------------------------------------------------------------------

	/**
	 * References to clean, as graphical elements.
	 *
	 * @return the selected references if any; else every reference of the active
	 *         diagram; {@code null} when there is no active diagram (already
	 *         logged and toasted).
	 */
	private List<IRPGraphElement> resolveReferences() {
		List<IRPGraphElement> refs = new ArrayList<IRPGraphElement>();

		// a. Selected graphical elements that are references.
		IRPCollection selGE = null;
		try {
			selGE = rhApp.getSelectedGraphElements();
		} catch (Exception ignore) {
		}
		if (selGE != null) {
			for (Object o : selGE.toList()) {
				if (!(o instanceof IRPGraphElement)) continue;
				IRPGraphElement ge = (IRPGraphElement) o;
				IRPModelElement m = safeModelObject(ge);
				if (m != null && RedefinedPortsService.isReferenceClass(m)) {
					refs.add(ge);
				}
			}
		}
		if (!refs.isEmpty()) {
			return refs;
		}

		// b. Nothing relevant selected: every reference of the active diagram.
		IRPDiagram diagram = null;
		try {
			diagram = rhApp.getDiagramOfSelectedElement();
			if (diagram == null) {
				IRPModelElement sel = rhApp.getSelectedElement();
				if (sel instanceof IRPDiagram) diagram = (IRPDiagram) sel;
			}
		} catch (Exception ignore) {
		}
		if (diagram == null) {
			rhpLog.warn(LOG + "No active diagram.");
			Toast.showToast("Open/right-click inside a Structure Diagram.", 4000);
			return null;
		}

		IRPCollection elements = null;
		try {
			elements = diagram.getGraphicalElements();
		} catch (Exception ignore) {
		}
		if (elements != null) {
			for (Object o : elements.toList()) {
				if (!(o instanceof IRPGraphElement)) continue;
				IRPGraphElement ge = (IRPGraphElement) o;
				IRPModelElement m = safeModelObject(ge);
				// A reference is a class (block), never a port: isReferenceClass is enough.
				if (m != null && RedefinedPortsService.isReferenceClass(m)) {
					refs.add(ge);
				}
			}
		}
		return refs;
	}

	// ------------------------------------------------------------------
	// Confirmation
	// ------------------------------------------------------------------

	/**
	 * Modal confirmation dialog. Blocks the Rhapsody plugin thread until the
	 * engineer clicks Apply or Cancel (or closes the window).
	 *
	 * @return true if Apply was clicked; false on Cancel / Esc / close (apply nothing)
	 */
	private boolean showConfirmDialog(int totalRefs, List<String> portLines, List<String> copyLines,
			List<String> reconnectLines, List<String> leftLines) {

		final JDialog dialog = new JDialog((Frame) null, "Clean Redefined Ports", true);   // modal
		dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		dialog.setAlwaysOnTop(true);

		JPanel root = new JPanel(new BorderLayout(0, 12));
		root.setBorder(BorderFactory.createEmptyBorder(14, 16, 12, 16));
		dialog.setContentPane(root);

		// -- Header ------------------------------------------------------------
		JPanel header = new JPanel();
		header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));

		JLabel intro = new JLabel("Clean redefined ports in this diagram");
		intro.setFont(intro.getFont().deriveFont(Font.BOLD, intro.getFont().getSize2D() + 3f));
		intro.setForeground(UiKit.INK);
		intro.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel sub = new JLabel("<html>Each inherited port is replaced by its redefined port, at the same place. "
				+ "Flows of the definition are copied onto it and kept; nothing is deleted or renamed.</html>");
		sub.setForeground(UiKit.INK2);
		sub.setAlignmentX(Component.LEFT_ALIGNMENT);
		sub.setBorder(BorderFactory.createEmptyBorder(3, 0, 0, 0));

		header.add(intro);
		header.add(sub);
		root.add(header, BorderLayout.NORTH);

		// -- Summary -----------------------------------------------------------
		JPanel lines = new JPanel();
		lines.setLayout(new BoxLayout(lines, BoxLayout.Y_AXIS));
		lines.setBackground(UiKit.SURFACE);
		lines.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

		JLabel counts = new JLabel("References to clean: " + totalRefs
				+ "     Inherited ports to replace: " + portLines.size());
		counts.setForeground(UiKit.INK);
		counts.setAlignmentX(Component.LEFT_ALIGNMENT);
		lines.add(counts);

		if (!portLines.isEmpty()) {
			JLabel portHead = UiKit.sectionTitle("Ports to replace (" + portLines.size() + "):", UiKit.INK2);
			portHead.setAlignmentX(Component.LEFT_ALIGNMENT);
			portHead.setBorder(BorderFactory.createEmptyBorder(10, 0, 2, 0));
			lines.add(portHead);
			for (String pl : portLines) {
				JLabel row = new JLabel("  - " + pl);
				row.setForeground(UiKit.INK2);
				row.setAlignmentX(Component.LEFT_ALIGNMENT);
				row.setBorder(BorderFactory.createEmptyBorder(1, 0, 1, 0));
				lines.add(row);
			}
		}

		addSection(lines, "Flows to copy", copyLines, "the definition flows are kept");
		addSection(lines, "Flows to reconnect", reconnectLines, "onto the redefined ports, same place");
		addSection(lines, "Flows left as is", leftLines,
				"not modified; their trait leaves this diagram with the inherited port");
		if (copyLines.isEmpty() && reconnectLines.isEmpty() && leftLines.isEmpty()) {
			JLabel none = new JLabel("No flow on the replaced ports.");
			none.setForeground(UiKit.INK2);
			none.setAlignmentX(Component.LEFT_ALIGNMENT);
			none.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
			lines.add(none);
		}

		JScrollPane scroll = new JScrollPane(lines,
				JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
				JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		scroll.setBorder(BorderFactory.createLineBorder(UiKit.HAIR));
		scroll.getViewport().setBackground(UiKit.SURFACE);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		root.add(scroll, BorderLayout.CENTER);

		// -- Buttons: hint + Apply (default) + Cancel ---------------------------
		final boolean[] applied = { false };

		JButton btnApply = UiKit.primary("Apply");
		JButton btnCancel = UiKit.neutral("Cancel");
		btnApply.addActionListener(e -> { applied[0] = true; dialog.dispose(); });
		btnCancel.addActionListener(e -> dialog.dispose());

		JLabel hint = UiKit.muted("Esc closes without changes");
		hint.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 10));

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		buttons.add(hint);
		buttons.add(btnApply);
		buttons.add(btnCancel);
		root.add(buttons, BorderLayout.SOUTH);
		dialog.getRootPane().setDefaultButton(btnApply);
		UiKit.onEscape(dialog, () -> dialog.dispose());   // Esc = Cancel (applied stays false)

		// -- Size (adaptive: fits content, capped so long lists scroll) --------
		dialog.pack();
		Dimension pref = dialog.getSize();
		int w = Math.min(Math.max(pref.width, 520), 760);
		int h = Math.min(Math.max(pref.height, 170), 560);
		dialog.setSize(w, h);

		utils.DialogPlacement.centerOnActiveScreen(dialog);
		dialog.setVisible(true);   // blocks until Apply / Cancel / close

		return applied[0];
	}

	// ------------------------------------------------------------------
	// Utilities
	// ------------------------------------------------------------------

	private static IRPModelElement safeModelObject(IRPGraphElement ge) {
		try { return ge != null ? ge.getModelObject() : null; } catch (Exception e) { return null; }
	}

	private static String safeName(IRPModelElement el) {
		try { return el != null ? el.getName() : "null"; } catch (Exception e) { return "<?>"; }
	}

	@Override
	public String commandName() {
		return COMMAND;
	}

	@Override
	public boolean isUndoable() {
		return true;
	}

	/**
	 * This tool opens a modal confirmation dialog. Returning true tells
	 * SafranProfilePlugin not to hold browser/GE refresh frozen across the dialog
	 * interaction, preventing a multi-second flush delay on dismiss.
	 */
	@Override
	public boolean isInteractive() {
		return true;
	}

	/** A titled list of lines ("reference : text") with a short detail under the title, left out when empty. */
	private static void addSection(JPanel lines, String title, List<String> rows, String detail) {
		if (rows.isEmpty()) return;
		JLabel head = UiKit.sectionTitle(title + " (" + rows.size() + "):", UiKit.INK2);
		head.setAlignmentX(Component.LEFT_ALIGNMENT);
		head.setBorder(BorderFactory.createEmptyBorder(10, 0, 2, 0));
		lines.add(head);
		if (detail != null && !detail.isEmpty()) {
			JLabel note = UiKit.muted("  " + detail);
			note.setAlignmentX(Component.LEFT_ALIGNMENT);
			note.setBorder(BorderFactory.createEmptyBorder(0, 0, 2, 0));
			lines.add(note);
		}
		for (String r : rows) {
			JLabel row = new JLabel("  - " + r);
			row.setForeground(UiKit.INK2);
			row.setAlignmentX(Component.LEFT_ALIGNMENT);
			row.setBorder(BorderFactory.createEmptyBorder(1, 0, 1, 0));
			lines.add(row);
		}
	}
}
