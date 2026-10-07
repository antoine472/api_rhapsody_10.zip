package tools;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import com.telelogic.rhapsody.core.IRPApplication;
import com.telelogic.rhapsody.core.IRPClassifier;
import com.telelogic.rhapsody.core.IRPCollection;
import com.telelogic.rhapsody.core.IRPGraphElement;
import com.telelogic.rhapsody.core.IRPModelElement;

import main.gui.tools.Toast;
import main.gui.tools.UiKit;
import tools.RedefinedPortsService.PortPlan;

/**
 * <b>Update Redefined Ports</b>
 * <p>
 * Reconciles the redefined {@code Flow Port}s of a reference
 * ({@code FunctionWithReference} / {@code LogicalSystemReference}) with its
 * parent Function / Logical System: adds missing ports, re-copies type /
 * direction / stereotypes, adds the redefinition link. Reuses
 * {@link RedefinedPortsService}.
 * </p>
 *
 * <p><b>Scope</b> (resolved from the browser selection):</p>
 * <ul>
 *   <li>a selected <b>reference</b> -&gt; itself;</li>
 *   <li>a selected <b>Function / Logical System</b> parent -&gt; its derived
 *       references ({@code getDerivedClassifiers()}).</li>
 * </ul>
 *
 * <p><b>Nothing is ever deleted or renamed silently.</b> Adds and property
 * updates apply automatically. Redefinitions whose name differs from the
 * mother port (realign name?) and ports with no match in the mother (delete?)
 * are listed in a per-port checkbox dialog, all unchecked by default; only the
 * ports the engineer ticks are renamed / deleted, the others are left as is.
 * Flows drawn on the inherited graphical ports that get replaced are listed in
 * the same dialog: a flow of the definition is copied onto the redefined port
 * on Apply and kept untouched, a flow already attached to a reference is
 * reconnected, nothing is deleted; a flow left as is is listed with why.
 * The undo transaction and refresh handling are managed by
 * {@code SafranProfilePlugin} (this tool is {@code isUndoable} and
 * {@code isInteractive}).</p>
 */
public class UpdateRedefinedPorts extends RhapsodyTool {

	/** Corresponds to the name given in the HEP file. */
	public static final String COMMAND = "Safran Toolkit...\\Update Redefined Ports";

	public UpdateRedefinedPorts(IRPApplication rpyApp) {
		super(rpyApp);
	}

	@Override
	public void execute() {

		// 1) Resolve target references from the selection.
		List<IRPModelElement> targets = resolveTargets();
		if (targets.isEmpty()) {
			rhpLog.info("[Update Redefined Ports] No reference in selection.");
			Toast.showToast("Select a reference, or a Function / Logical System.", 4000);
			return;
		}

		// 2) Compute plans (dry-run) + log the differences.
		Map<IRPModelElement, PortPlan> plans = new LinkedHashMap<IRPModelElement, PortPlan>();
		List<PortPlan.RenameCandidate> renames = new ArrayList<PortPlan.RenameCandidate>();
		List<PortPlan.OrphanCandidate> orphans = new ArrayList<PortPlan.OrphanCandidate>();
		int totalAdd = 0, totalUpdate = 0, changed = 0;
		// Port names per reference name, shown in the dialog's "applied automatically" zone.
		Map<String, List<String>> addsByRef = new LinkedHashMap<String, List<String>>();
		Map<String, List<String>> updatesByRef = new LinkedHashMap<String, List<String>>();

		for (IRPModelElement ref : targets) {
			PortPlan plan = RedefinedPortsService.computePlan(ref);
			plans.put(ref, plan);
			totalAdd += plan.toAdd.size();
			totalUpdate += plan.toUpdate.size();
			if (!plan.toAdd.isEmpty()) {
				addsByRef.computeIfAbsent(plan.referenceName, k -> new ArrayList<String>()).addAll(plan.toAdd);
			}
			if (!plan.toUpdate.isEmpty()) {
				updatesByRef.computeIfAbsent(plan.referenceName, k -> new ArrayList<String>()).addAll(plan.toUpdate);
			}
			renames.addAll(plan.renameCandidates);
			orphans.addAll(plan.orphanCandidates);
			if (plan.hasChanges()) {
				changed++;
				logPlan(plan);
			}
		}

		// Flows drawn on the inherited ports that will be replaced, handled by the
		// graphical cleanup (step 5): a definition flow is copied onto the
		// redefined port and kept, a reference flow is reconnected, nothing is
		// deleted. "ref : flow" lines; a drawn trait is listed once (a trait
		// between two references is seen from both boxes).
		List<String> copyLines = new ArrayList<String>();
		List<String> reconnectLines = new ArrayList<String>();
		List<String> leftLines = new ArrayList<String>();
		List<RedefinedPortsService.FlowPlan> seenTraits = new ArrayList<RedefinedPortsService.FlowPlan>();
		Set<String> seenFlows = new HashSet<String>();
		for (IRPModelElement ref : targets) {
			try {
				for (IRPGraphElement ge : RedefinedPortsService.getGraphicalRepresentations(ref)) {
					for (RedefinedPortsService.FlowPlan f : RedefinedPortsService.planFlowReconnections(ge)) {
						if (alreadyListed(seenTraits, f)) continue;
						seenTraits.add(f);
						String guid = safeGuid(f.flow);
						if (guid == null) guid = "id:" + System.identityHashCode(f.flow);
						if (!seenFlows.add(guid + "|" + f.referenceName + "|" + f.portName)) continue;
						String line = f.referenceName + " : " + f.describe();
						switch (f.kind) {
							case COPY: copyLines.add(line); break;
							case RECONNECT: reconnectLines.add(line); break;
							default: leftLines.add(line); break;
						}
					}
				}
			} catch (Exception e) {
				rhpLog.warn("[Update Redefined Ports] flow scan failed for " + safeName(ref)
						+ ": " + e.getMessage());
			}
		}
		if (!copyLines.isEmpty()) {
			rhpLog.info("[Update Redefined Ports] flows to copy: " + copyLines);
		}
		if (!reconnectLines.isEmpty()) {
			rhpLog.info("[Update Redefined Ports] flows to reconnect: " + reconnectLines);
		}
		if (!leftLines.isEmpty()) {
			rhpLog.info("[Update Redefined Ports] flows left as is: " + leftLines);
		}

		if (changed == 0 && copyLines.isEmpty() && reconnectLines.isEmpty() && leftLines.isEmpty()) {
			rhpLog.info("[Update Redefined Ports] Already up to date: "
					+ targets.size() + " reference(s), no change.");
			Toast.showToast("References are already up to date.", 3000);
			return;
		}

		// 3) Confirmation dialog whenever there is anything to do (adds, updates,
		//    renames, orphans or flows to copy / reconnect): the engineer always sees
		//    what will happen and can cancel. Renames / orphans stay opt-in inside the dialog.
		List<PortPlan.RenameCandidate> selectedRenames = new ArrayList<PortPlan.RenameCandidate>();
		List<PortPlan.OrphanCandidate> selectedOrphans = new ArrayList<PortPlan.OrphanCandidate>();

		if (!addsByRef.isEmpty() || !updatesByRef.isEmpty() || !renames.isEmpty() || !orphans.isEmpty()
				|| !copyLines.isEmpty() || !reconnectLines.isEmpty() || !leftLines.isEmpty()) {
			boolean applied = showDecisionDialog(addsByRef, updatesByRef, renames, orphans,
					selectedRenames, selectedOrphans, copyLines, reconnectLines, leftLines);
			if (!applied) {
				rhpLog.info("[Update Redefined Ports] Cancelled by user.");
				return;
			}
		}

		// 4) Apply (the plugin already wraps this in an undo transaction).
		//    a. Renames the engineer ticked.
		for (PortPlan.RenameCandidate c : selectedRenames) {
			rhpLog.info("[Update Redefined Ports] " + c.referenceName + " : rename '"
					+ c.currentName + "' -> '" + c.motherName + "'");
			RedefinedPortsService.applyRenameToMother(c.port, c.motherPort);
		}
		//    b. Orphans the engineer ticked (unticked ones are kept).
		for (PortPlan.OrphanCandidate c : selectedOrphans) {
			rhpLog.info("[Update Redefined Ports] " + c.referenceName + " : delete orphan '"
					+ c.name + "'");
			RedefinedPortsService.deleteOrphanPort(c.port);
		}
		//    c. Add / update / link (never deletes anything).
		for (IRPModelElement ref : plans.keySet()) {
			boolean ok = RedefinedPortsService.redefinePorts(ref);
			if (!ok) {
				rhpLog.warn("[Update Redefined Ports] Incomplete reconciliation for "
						+ safeName(ref) + " (retry).");
			}
		}
		// 5) Graphical cleanup of diagrams showing the processed references: each
		//    inherited port is replaced by its redefined port at the same place,
		//    the definition flows drawn on it are copied onto the redefined port
		//    (and kept), the reference flows reconnected (never deleted).
		for (IRPModelElement ref : targets) {
			try {
				List<IRPGraphElement> reps = RedefinedPortsService.getGraphicalRepresentations(ref);
				if (!reps.isEmpty()) {
					RedefinedPortsService.hideRedefinedPorts(rhApp, reps);
				}
			} catch (Exception e) {
				rhpLog.warn("[Update Redefined Ports] Diagram cleanup failed for "
						+ safeName(ref) + ": " + e.getMessage());
			}
		}

		int renamed = selectedRenames.size();
		int deleted = selectedOrphans.size();
		rhpLog.info("[Update Redefined Ports] Done: " + targets.size()
				+ " reference(s) - +" + totalAdd + " / ~" + totalUpdate
				+ " / renamed " + renamed + " (of " + renames.size() + ")"
				+ " / deleted " + deleted + " (of " + orphans.size() + ")"
				+ " / flows to copy " + copyLines.size() + ", to reconnect " + reconnectLines.size()
				+ ", left " + leftLines.size() + ".");
		List<String> done = new ArrayList<String>();
		if (totalAdd > 0)     done.add(plural(totalAdd, "port") + " added");
		if (totalUpdate > 0)  done.add(plural(totalUpdate, "port") + " updated");
		if (renamed > 0)      done.add(plural(renamed, "port") + " renamed");
		if (deleted > 0)      done.add(plural(deleted, "port") + " deleted");
		// The flow counts are the planned ones (the swap reports port graphics,
		// not flows): say so, the log has the outcome of each flow.
		boolean flows = !copyLines.isEmpty() || !reconnectLines.isEmpty();
		if (flows) {
			done.add("flows handled as listed (" + copyLines.size() + " to copy, "
					+ reconnectLines.size() + " to reconnect)");
		}
		Toast.showToast(done.isEmpty()
				? "Update Redefined Ports: nothing changed."
				: "Update Redefined Ports: " + String.join(", ", done) + "."
						+ (flows ? "\nFlow details in the log." : ""), 3500);
	}

	/** "1 port", "3 ports". */
	private static String plural(int n, String noun) {
		return n + " " + noun + (n > 1 ? "s" : "");
	}

	/** True when a plan for the same drawn trait was already listed (seen from another reference). */
	private static boolean alreadyListed(List<RedefinedPortsService.FlowPlan> seen, RedefinedPortsService.FlowPlan f) {
		for (RedefinedPortsService.FlowPlan s : seen) {
			if (s.sameTrait(f)) return true;
		}
		return false;
	}

	// ------------------------------------------------------------------
	// Scope resolution
	// ------------------------------------------------------------------

	private List<IRPModelElement> resolveTargets() {
		List<IRPModelElement> targets = new ArrayList<IRPModelElement>();

		IRPCollection selection;
		try {
			selection = rhApp.getListOfSelectedElements();
		} catch (Exception e) {
			return targets;
		}
		if (selection == null) return targets;

		for (Object o : selection.toList()) {
			if (!(o instanceof IRPModelElement)) continue;
			IRPModelElement el = (IRPModelElement) o;

			// Case 1: the selected element is itself a reference.
			if (RedefinedPortsService.isReferenceClass(el)) {
				addUnique(targets, el);
				continue;
			}

			// Case 2: it is a parent -> add its derived references.
			if (el instanceof IRPClassifier) {
				IRPCollection derived;
				try {
					derived = ((IRPClassifier) el).getDerivedClassifiers();
				} catch (Exception e) {
					derived = null;
				}
				if (derived != null) {
					for (Object d : derived.toList()) {
						if (d instanceof IRPModelElement
								&& RedefinedPortsService.isReferenceClass((IRPModelElement) d)) {
							addUnique(targets, (IRPModelElement) d);
						}
					}
				}
			}
		}
		return targets;
	}

	private void addUnique(List<IRPModelElement> list, IRPModelElement el) {
		String guid = safeGuid(el);
		for (IRPModelElement existing : list) {
			if (guid != null && guid.equals(safeGuid(existing))) return;
		}
		list.add(el);
	}

	// ------------------------------------------------------------------
	// Logging & confirmation
	// ------------------------------------------------------------------

	private void logPlan(PortPlan plan) {
		StringBuilder sb = new StringBuilder("[Update Redefined Ports] ");
		sb.append(plan.referenceName).append(" : +").append(plan.toAdd.size());
		if (!plan.toAdd.isEmpty()) sb.append(" ").append(plan.toAdd);
		sb.append(" / ~").append(plan.toUpdate.size());
		if (!plan.toUpdate.isEmpty()) sb.append(" ").append(plan.toUpdate);
		sb.append(" / rename? ").append(plan.renameCandidates.size());
		if (!plan.renameCandidates.isEmpty()) {
			List<String> names = new ArrayList<String>();
			for (PortPlan.RenameCandidate c : plan.renameCandidates) {
				names.add(c.currentName + "->" + c.motherName);
			}
			sb.append(" ").append(names);
		}
		sb.append(" / orphan? ").append(plan.orphanCandidates.size());
		if (!plan.orphanCandidates.isEmpty()) {
			List<String> names = new ArrayList<String>();
			for (PortPlan.OrphanCandidate c : plan.orphanCandidates) {
				names.add(c.name);
			}
			sb.append(" ").append(names);
		}
		rhpLog.info(sb.toString());
	}

	// -- Decision dialog palette: local names kept for the call sites, values come
	//    from the shared UiKit tokens (single source of truth, validated contrasts).
	private static final Color ACCENT      = UiKit.ACCENT;
	private static final Color DANGER      = UiKit.DANGER;
	private static final Color INK         = UiKit.INK;
	private static final Color INK2        = UiKit.INK2;
	private static final Color HAIR        = UiKit.HAIR;
	private static final Color SURFACE     = UiKit.SURFACE;
	private static final Color ACCENT_WEAK = UiKit.ACCENT_WEAK;
	private static final Color DANGER_WEAK = UiKit.DANGER_WEAK;
	private static final Color GROUP_BG    = UiKit.GROUP_BG;
	private static final Cursor HAND       = UiKit.HAND;

	private static final int ZONE1_MAX_HEIGHT = 170;
	private static final int ZONE2_MAX_HEIGHT = 130;

	/**
	 * Modal per-port decision dialog. Blocks the Rhapsody plugin thread until
	 * the engineer clicks Apply or Cancel (or closes the window / presses Esc).
	 * <p>
	 * Zone 1 ("Ports to rename or delete") lists the rename / orphan candidates
	 * grouped by reference, all unticked (opt-in), with a live filter and a
	 * tri-state group box per reference; it is left out when there is none.
	 * Zone 2 ("Applied automatically") is a read-only recap, per reference, of
	 * what Apply does anyway: adds / updates and the flow copies / reconnections.
	 * </p>
	 *
	 * @param addsByRef       ports to add, per reference name
	 * @param updatesByRef    ports to update from the definition, per reference name
	 * @param selectedRenames OUT - rename candidates ticked by the engineer
	 * @param selectedOrphans OUT - orphan candidates ticked by the engineer
	 * @param copyLines       definition flows drawn on inherited ports, copied onto
	 *                        the redefined ports on Apply, "referenceName : text"
	 * @param reconnectLines  reference flows drawn on inherited ports, reconnected
	 *                        to the redefined ports on Apply, "referenceName : flowName"
	 * @param leftLines       flows drawn on inherited ports but left as is,
	 *                        "referenceName : flowName (reason)"
	 * @return true if Apply was clicked; false on Cancel / Esc / close (apply nothing)
	 */
	private boolean showDecisionDialog(
			Map<String, List<String>> addsByRef, Map<String, List<String>> updatesByRef,
			List<PortPlan.RenameCandidate> renames,
			List<PortPlan.OrphanCandidate> orphans,
			List<PortPlan.RenameCandidate> selectedRenames,
			List<PortPlan.OrphanCandidate> selectedOrphans,
			List<String> copyLines, List<String> reconnectLines, List<String> leftLines) {

		final List<String> copy = copyLines != null ? copyLines : new ArrayList<String>();
		final List<String> reconnect = reconnectLines != null ? reconnectLines : new ArrayList<String>();
		final List<String> left = leftLines != null ? leftLines : new ArrayList<String>();
		final List<String> flows = new ArrayList<String>(copy);
		flows.addAll(reconnect);
		flows.addAll(left);

		final JDialog dialog = new JDialog((Frame) null, "Update Redefined Ports", true);   // modal
		dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		dialog.setAlwaysOnTop(true);

		JPanel root = new JPanel(new BorderLayout(0, 12));
		root.setBorder(BorderFactory.createEmptyBorder(14, 16, 12, 16));
		dialog.setContentPane(root);

		// -- Zone 1 model: one opt-in row per candidate, grouped by reference ---
		final DecisionList list = new DecisionList();
		for (PortPlan.RenameCandidate c : renames) {
			list.group(c.referenceName).rows.add(
					new DecisionRow(c.currentName + "  \u2192  " + c.motherName, c, null));
		}
		for (PortPlan.OrphanCandidate c : orphans) {
			list.group(c.referenceName).rows.add(new DecisionRow(c.name, null, c));
		}
		final boolean hasRows = list.total() > 0;
		final boolean hasAutomatic = !addsByRef.isEmpty() || !updatesByRef.isEmpty() || !flows.isEmpty();
		final Runnable refresh = new Runnable() {
			@Override
			public void run() { list.updateCounts(); }
		};

		// -- North: recap banner + filter ---------------------------------------
		JPanel header = new JPanel();
		header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
		header.add(recapBanner(referenceCount(addsByRef, updatesByRef, renames, orphans, flows),
				hasRows, hasAutomatic));

		final JTextField filter = new JTextField();
		filter.setName("urp.filter");
		filter.setColumns(24);
		filter.setMargin(new Insets(3, 6, 3, 6));
		filter.setForeground(INK);
		filter.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) { list.applyFilter(filter.getText()); }
			@Override
			public void removeUpdate(DocumentEvent e) { list.applyFilter(filter.getText()); }
			@Override
			public void changedUpdate(DocumentEvent e) { list.applyFilter(filter.getText()); }
		});
		if (hasRows) {
			header.add(Box.createVerticalStrut(10));
			header.add(filterRow(filter));
		}
		root.add(header, BorderLayout.NORTH);

		// -- Center: zone 1 (opt-in) + zone 2 (read-only) -----------------------
		JPanel center = new JPanel();
		center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));

		// Zone 1, only when there is something to tick:
		// PORTS TO RENAME OR DELETE   n of total ticked .......... Tick all  Untick all
		if (hasRows) {
			JPanel z1Head = new JPanel();
			z1Head.setLayout(new BoxLayout(z1Head, BoxLayout.X_AXIS));
			z1Head.setOpaque(false);
			z1Head.add(UiKit.sectionTitle("PORTS TO RENAME OR DELETE", ACCENT));
			list.countLabel.setForeground(INK2);
			list.countLabel.setText(list.countText());
			z1Head.add(Box.createHorizontalStrut(10));
			z1Head.add(list.countLabel);
			z1Head.add(Box.createHorizontalGlue());
			z1Head.add(UiKit.link("Tick all", e -> list.setAll(true)));
			z1Head.add(Box.createHorizontalStrut(6));
			z1Head.add(UiKit.link("Untick all", e -> list.setAll(false)));
			center.add(fixHeight(z1Head));
			center.add(Box.createVerticalStrut(4));

			// Grouped rows in a capped scroll.
			JPanel listPanel = new JPanel();
			listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
			listPanel.setBackground(SURFACE);
			for (DecisionGroup g : list.groups.values()) {
				listPanel.add(groupPanel(g, refresh));
			}
			list.content = listPanel;

			// NORTH slot: full viewport width, preferred height only (rows never stretch).
			JPanel view = new JPanel(new BorderLayout());
			view.setBackground(SURFACE);
			view.add(listPanel, BorderLayout.NORTH);

			JScrollPane scroll = new JScrollPane(view,
					JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
					JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
			scroll.setBorder(BorderFactory.createLineBorder(HAIR));
			scroll.getViewport().setBackground(SURFACE);
			scroll.getVerticalScrollBar().setUnitIncrement(16);
			Dimension lp = listPanel.getPreferredSize();
			scroll.setPreferredSize(new Dimension(Math.max(lp.width + 24, 480),
					Math.min(lp.height + 4, ZONE1_MAX_HEIGHT)));
			scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, ZONE1_MAX_HEIGHT));
			scroll.setAlignmentX(Component.LEFT_ALIGNMENT);
			center.add(scroll);
		}
		// Zone 2 (read-only recap of what Apply does anyway) only when there is
		// actually something to report: adds, updates, or flows to copy / reconnect / leave.
		if (hasAutomatic) {
			if (hasRows) center.add(Box.createVerticalStrut(12));
			JPanel z2Head = new JPanel();
			z2Head.setLayout(new BoxLayout(z2Head, BoxLayout.X_AXIS));
			z2Head.setOpaque(false);
			z2Head.add(UiKit.sectionTitle("APPLIED AUTOMATICALLY", INK2));
			z2Head.add(Box.createHorizontalGlue());
			center.add(fixHeight(z2Head));
			center.add(Box.createVerticalStrut(4));
			center.add(zoneTwo(addsByRef, updatesByRef, copy, reconnect, left));
		}

		root.add(center, BorderLayout.CENTER);

		// -- South: hint + Apply (default) + Cancel -----------------------------
		final boolean[] applied = { false };

		JButton btnApply = UiKit.primary("Apply");
		JButton btnCancel = new JButton("Cancel");
		btnApply.addActionListener(e -> { applied[0] = true; dialog.dispose(); });
		btnCancel.addActionListener(e -> dialog.dispose());
		// Comfortably wide neutral button, same height as Apply (avoids "Ca..." on Windows L&F/DPI).
		btnCancel.setPreferredSize(new Dimension(
				Math.max(btnCancel.getPreferredSize().width + 28, 96),
				btnApply.getPreferredSize().height));

		JLabel hint = UiKit.muted("Esc or Cancel: nothing is changed");
		hint.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 10));

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		buttons.add(hint);
		buttons.add(btnApply);
		buttons.add(btnCancel);
		root.add(buttons, BorderLayout.SOUTH);

		dialog.getRootPane().setDefaultButton(btnApply);
		UiKit.onEscape(dialog, dialog::dispose);

		// -- Size (adaptive: fits content, capped so long lists scroll) ----------
		dialog.pack();
		Dimension pref = dialog.getSize();
		int w = Math.min(Math.max(pref.width, 560), 800);
		int h = Math.min(Math.max(pref.height, 200), 620);
		dialog.setSize(w, h);

		utils.DialogPlacement.centerOnActiveScreen(dialog);
		dialog.setVisible(true);   // blocks until Apply / Cancel / Esc / close

		if (!applied[0]) {
			return false;
		}

		// Checkboxes keep their state after dispose(): collect the ticked rows of
		// ALL groups, whatever the current filter / collapse state.
		list.collectChecked(selectedRenames, selectedOrphans);
		return true;
	}

	// ------------------------------------------------------------------
	// Decision dialog: model of zone 1 (rows grouped by reference)
	// ------------------------------------------------------------------

	/** One opt-in row of zone 1: exactly one of {@code rename} / {@code orphan} is set. */
	private static final class DecisionRow {
		final String text;
		final JCheckBox box = new JCheckBox();
		final JPanel panel = new JPanel();
		final PortPlan.RenameCandidate rename;
		final PortPlan.OrphanCandidate orphan;

		DecisionRow(String text, PortPlan.RenameCandidate rename, PortPlan.OrphanCandidate orphan) {
			this.text = text != null ? text : "";
			this.rename = rename;
			this.orphan = orphan;
		}
	}

	/** The rows of one reference, with their header widgets (disclosure + tri-state box). */
	private static final class DecisionGroup {
		final String reference;
		final List<DecisionRow> rows = new ArrayList<DecisionRow>();
		final JCheckBox groupBox = new JCheckBox();
		final JLabel disclosure = new JLabel("\u25BE");
		JPanel wrapper;        // header + rowsContainer; hidden by the filter when no row matches
		JPanel rowsContainer;  // hidden by the disclosure toggle
		boolean expanded = true;

		DecisionGroup(String reference) {
			this.reference = reference;
		}

		/** Tri-state: all ticked -> checked, none -> unchecked, partial -> dash icon. */
		void reflect(int checked, Icon dash) {
			if (!rows.isEmpty() && checked == rows.size()) {
				groupBox.setIcon(null);
				groupBox.setSelected(true);
			} else if (checked == 0) {
				groupBox.setIcon(null);
				groupBox.setSelected(false);
			} else {
				groupBox.setSelected(false);
				groupBox.setIcon(dash);
			}
		}

		void setExpanded(boolean value) {
			expanded = value;
			disclosure.setText(value ? "\u25BE" : "\u25B8");
			if (rowsContainer != null) rowsContainer.setVisible(value);
			if (wrapper != null) {
				wrapper.revalidate();
				wrapper.repaint();
			}
		}
	}

	/** All groups of zone 1 + the live "n / total cochés" label. */
	private static final class DecisionList {
		final Map<String, DecisionGroup> groups = new LinkedHashMap<String, DecisionGroup>();
		final JLabel countLabel = new JLabel();
		final Icon dashIcon = new DashIcon();
		JPanel content;   // vertical box of the group wrappers (revalidated after a filter)

		DecisionGroup group(String reference) {
			String key = reference != null ? reference : "?";
			DecisionGroup g = groups.get(key);
			if (g == null) {
				g = new DecisionGroup(key);
				groups.put(key, g);
			}
			return g;
		}

		int total() {
			int n = 0;
			for (DecisionGroup g : groups.values()) n += g.rows.size();
			return n;
		}

		int checked() {
			int n = 0;
			for (DecisionGroup g : groups.values()) {
				for (DecisionRow r : g.rows) if (r.box.isSelected()) n++;
			}
			return n;
		}

		String countText() {
			return checked() + " of " + total() + " ticked";
		}

		/** Single refresh point (EDT): live count + tri-state group boxes. */
		void updateCounts() {
			if (!SwingUtilities.isEventDispatchThread()) {
				SwingUtilities.invokeLater(new Runnable() {
					@Override
					public void run() { updateCounts(); }
				});
				return;
			}
			for (DecisionGroup g : groups.values()) {
				int n = 0;
				for (DecisionRow r : g.rows) if (r.box.isSelected()) n++;
				g.reflect(n, dashIcon);
			}
			countLabel.setText(countText());
		}

		/** "Tout cocher" / "Aucun": every row of every group, then one refresh. */
		void setAll(boolean selected) {
			for (DecisionGroup g : groups.values()) {
				for (DecisionRow r : g.rows) r.box.setSelected(selected);
			}
			updateCounts();
		}

		/**
		 * View-only filter: a row stays visible when its reference or its text
		 * contains the query (case-insensitive); a group with no visible row is
		 * hidden. Never touches the checkbox states.
		 */
		void applyFilter(String raw) {
			String q = raw == null ? "" : raw.trim().toLowerCase();
			for (DecisionGroup g : groups.values()) {
				boolean refHit = q.isEmpty() || g.reference.toLowerCase().contains(q);
				boolean any = false;
				for (DecisionRow r : g.rows) {
					boolean hit = refHit || r.text.toLowerCase().contains(q);
					r.panel.setVisible(hit);
					any |= hit;
				}
				if (g.wrapper != null) g.wrapper.setVisible(any);
			}
			if (content != null) {
				content.revalidate();
				content.repaint();
			}
		}

		/** Ticked rows of ALL groups, whatever the filter / collapse state. */
		void collectChecked(List<PortPlan.RenameCandidate> outRenames,
				List<PortPlan.OrphanCandidate> outOrphans) {
			for (DecisionGroup g : groups.values()) {
				for (DecisionRow r : g.rows) {
					if (!r.box.isSelected()) continue;
					if (r.rename != null) outRenames.add(r.rename);
					else if (r.orphan != null) outOrphans.add(r.orphan);
				}
			}
		}
	}

	// ------------------------------------------------------------------
	// Decision dialog: widgets
	// ------------------------------------------------------------------

	/**
	 * Recap banner: white card, hairline border, 3px accent left edge. Says what
	 * differs, then what the engineer has to do.
	 */
	private static JPanel recapBanner(int refs, boolean hasChoices, boolean hasAutomatic) {
		JPanel banner = new JPanel(new BorderLayout());
		banner.setBackground(SURFACE);
		banner.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(0, 3, 0, 0, ACCENT),
				BorderFactory.createCompoundBorder(
						BorderFactory.createMatteBorder(1, 0, 1, 1, HAIR),
						BorderFactory.createEmptyBorder(8, 11, 8, 11))));
		String what = refs == 1
				? "<b>1 reference</b> differs from its definition."
				: "<b>" + refs + " references</b> differ from their definition.";
		String how;
		if (hasChoices && hasAutomatic) {
			how = "Tick the ports to rename or delete. The other changes are applied automatically.";
		} else if (hasChoices) {
			how = "Tick the ports to rename or delete. Unticked ports are left as they are.";
		} else {
			how = "The changes below are applied when you click Apply.";
		}
		JLabel text = new JLabel("<html>" + what + "<br><font color='" + hex(INK2) + "'>" + how + "</font></html>");
		text.setForeground(INK);
		banner.add(text, BorderLayout.CENTER);
		return fixHeight(banner);
	}

	/** "Filtrer par référence ou port" + the live filter field. */
	private static JPanel filterRow(JTextField field) {
		JPanel row = new JPanel(new BorderLayout(8, 0));
		row.setOpaque(false);
		JLabel label = new JLabel("Filter by reference or port");
		label.setForeground(INK2);
		row.add(label, BorderLayout.WEST);
		row.add(field, BorderLayout.CENTER);
		return fixHeight(row);
	}

	/** Group block: header (disclosure + tri-state box + bold name + count) over its rows. */
	private static JPanel groupPanel(final DecisionGroup g, final Runnable onChange) {
		JPanel wrapper = new JPanel();
		wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
		wrapper.setOpaque(false);
		wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);

		JPanel head = new JPanel();
		head.setLayout(new BoxLayout(head, BoxLayout.X_AXIS));
		head.setBackground(GROUP_BG);
		head.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(0, 0, 1, 0, HAIR),
				BorderFactory.createEmptyBorder(3, 6, 3, 8)));

		g.disclosure.setForeground(INK2);
		g.disclosure.setCursor(HAND);
		g.disclosure.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 6));
		g.disclosure.setToolTipText("Show or hide the ports of this reference");
		g.disclosure.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) { g.setExpanded(!g.expanded); }
		});

		g.groupBox.setOpaque(false);
		g.groupBox.setToolTipText("Tick or untick all the ports of this reference");
		g.groupBox.addActionListener(e -> {
			boolean v = g.groupBox.isSelected();
			for (DecisionRow r : g.rows) r.box.setSelected(v);
			onChange.run();
		});

		JLabel name = new JLabel(g.reference);
		name.setFont(name.getFont().deriveFont(Font.BOLD));
		name.setForeground(INK);
		JLabel count = new JLabel("(" + g.rows.size() + ")");
		count.setForeground(INK2);
		count.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 0));

		head.add(g.disclosure);
		head.add(g.groupBox);
		head.add(Box.createHorizontalStrut(4));
		head.add(name);
		head.add(count);
		head.add(Box.createHorizontalGlue());

		JPanel rows = new JPanel();
		rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
		rows.setBackground(SURFACE);
		rows.setAlignmentX(Component.LEFT_ALIGNMENT);
		for (DecisionRow r : g.rows) {
			rows.add(rowPanel(r, onChange));
		}

		g.wrapper = wrapper;
		g.rowsContainer = rows;
		wrapper.add(fixHeight(head));
		wrapper.add(rows);
		return wrapper;
	}

	/** One candidate row: [ ] BADGE text - unticked by default (opt-in). */
	private static JPanel rowPanel(final DecisionRow r, final Runnable onChange) {
		JPanel p = r.panel;
		p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));
		p.setBackground(SURFACE);
		p.setBorder(BorderFactory.createEmptyBorder(1, 30, 1, 8));

		r.box.setOpaque(false);
		r.box.setSelected(false);
		r.box.addActionListener(e -> onChange.run());

		Badge badge = r.rename != null
				? new Badge("RENAME", ACCENT, ACCENT_WEAK)
				: new Badge("DELETE", DANGER, DANGER_WEAK);

		JLabel text = new JLabel(r.text);
		text.setForeground(INK);
		text.setCursor(HAND);
		text.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) { r.box.doClick(); }
		});

		// Why the port is listed, in plain words.
		JLabel reason = UiKit.muted(r.rename != null ? "(as in the definition)" : "(not in the definition)");
		reason.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));

		p.add(r.box);
		p.add(Box.createHorizontalStrut(4));
		p.add(badge);
		p.add(Box.createHorizontalStrut(8));
		p.add(text);
		p.add(reason);
		p.add(Box.createHorizontalGlue());
		return fixHeight(p);
	}

	/**
	 * Zone 2 body: what Apply does anyway, one line per reference under each
	 * heading: ports to add, ports to update, flows to copy, flows to reconnect,
	 * flows left as is.
	 */
	private static JScrollPane zoneTwo(Map<String, List<String>> adds, Map<String, List<String>> updates,
			List<String> copy, List<String> reconnect, List<String> left) {
		JPanel body = new JPanel();
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setBackground(SURFACE);
		body.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));

		if (!adds.isEmpty()) {
			body.add(recapHeading("Ports to add", portCount(adds), null, false));
			for (Map.Entry<String, List<String>> en : adds.entrySet()) {
				body.add(recapItem(en.getKey(), String.join(", ", en.getValue())));
			}
		}
		if (!updates.isEmpty()) {
			if (!adds.isEmpty()) body.add(Box.createVerticalStrut(6));
			body.add(recapHeading("Ports to update from the definition", portCount(updates),
					"type, direction, stereotypes", false));
			for (Map.Entry<String, List<String>> en : updates.entrySet()) {
				body.add(recapItem(en.getKey(), String.join(", ", en.getValue())));
			}
		}
		if (!copy.isEmpty()) {
			if (!adds.isEmpty() || !updates.isEmpty()) body.add(Box.createVerticalStrut(6));
			body.add(recapHeading("Flows to copy onto the redefined ports", copy.size(),
					"the definition flows are kept", false));
			for (Map.Entry<String, List<String>> en : flowsByReference(copy).entrySet()) {
				body.add(recapItem(en.getKey(), String.join(", ", en.getValue())));
			}
		}
		if (!reconnect.isEmpty()) {
			if (!adds.isEmpty() || !updates.isEmpty() || !copy.isEmpty()) body.add(Box.createVerticalStrut(6));
			body.add(recapHeading("Flows to reconnect", reconnect.size(),
					"onto the redefined ports, same place", false));
			for (Map.Entry<String, List<String>> en : flowsByReference(reconnect).entrySet()) {
				body.add(recapItem(en.getKey(), String.join(", ", en.getValue())));
			}
		}
		if (!left.isEmpty()) {
			if (!adds.isEmpty() || !updates.isEmpty() || !copy.isEmpty() || !reconnect.isEmpty()) {
				body.add(Box.createVerticalStrut(6));
			}
			body.add(recapHeading("Flows left as is", left.size(),
					"not modified; their trait leaves this diagram with the inherited port", false));
			for (Map.Entry<String, List<String>> en : flowsByReference(left).entrySet()) {
				body.add(recapItem(en.getKey(), String.join(", ", en.getValue())));
			}
		}

		JPanel view = new JPanel(new BorderLayout());
		view.setBackground(SURFACE);
		view.add(body, BorderLayout.NORTH);

		JScrollPane scroll = new JScrollPane(view,
				JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
				JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		scroll.setBorder(BorderFactory.createLineBorder(HAIR));
		scroll.getViewport().setBackground(SURFACE);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		Dimension bp = body.getPreferredSize();
		scroll.setPreferredSize(new Dimension(Math.max(bp.width + 24, 480),
				Math.min(bp.height + 4, ZONE2_MAX_HEIGHT)));
		scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, ZONE2_MAX_HEIGHT));
		scroll.setAlignmentX(Component.LEFT_ALIGNMENT);
		return scroll;
	}

	/** Zone 2 heading, e.g. "Ports to add (5)" or "Flows to copy onto the redefined ports (3), the definition flows are kept". */
	private static JLabel recapHeading(String title, int count, String detail, boolean danger) {
		String head = "<b>" + title + "</b> (" + count + ")";
		if (danger) head = "<font color='" + hex(DANGER) + "'>" + head + "</font>";
		JLabel l = new JLabel("<html>" + head + (detail != null ? ", " + esc(detail) : "") + "</html>");
		l.setForeground(INK2);
		l.setAlignmentX(Component.LEFT_ALIGNMENT);
		return l;
	}

	/** One indented recap line: the reference in bold, then its ports (or flow count). */
	private static JLabel recapItem(String reference, String text) {
		JLabel l = new JLabel("<html><b>" + esc(reference) + "</b>: " + esc(text) + "</html>");
		l.setForeground(INK2);
		l.setAlignmentX(Component.LEFT_ALIGNMENT);
		l.setBorder(BorderFactory.createEmptyBorder(1, 14, 0, 0));
		return l;
	}

	/** Total number of ports across the references of a zone 2 section. */
	private static int portCount(Map<String, List<String>> byRef) {
		int n = 0;
		for (List<String> ports : byRef.values()) n += ports.size();
		return n;
	}

	/** Model text for an HTML label: a name with {@code <} or {@code &} must not break it. */
	private static String esc(String s) {
		if (s == null) return "";
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	/** "#RRGGBB" of a palette color, for the HTML labels. */
	private static String hex(Color c) {
		return String.format("#%06X", c.getRGB() & 0xFFFFFF);
	}

	/** Left-aligned, capped to its preferred height (no vertical stretch in a Y box). */
	private static JPanel fixHeight(JPanel p) {
		p.setAlignmentX(Component.LEFT_ALIGNMENT);
		p.setMaximumSize(new Dimension(Integer.MAX_VALUE, p.getPreferredSize().height));
		return p;
	}

	/** Number of distinct references with any change: adds, updates, renames, orphans or flows. */
	private static int referenceCount(Map<String, List<String>> adds, Map<String, List<String>> updates,
			List<PortPlan.RenameCandidate> renames, List<PortPlan.OrphanCandidate> orphans,
			List<String> flows) {
		Set<String> refs = new HashSet<String>();
		refs.addAll(adds.keySet());
		refs.addAll(updates.keySet());
		for (PortPlan.RenameCandidate c : renames) refs.add(c.referenceName != null ? c.referenceName : "?");
		for (PortPlan.OrphanCandidate c : orphans) refs.add(c.referenceName != null ? c.referenceName : "?");
		for (String line : flows) refs.add(flowReference(line));
		return refs.size();
	}

	/** "referenceName : flowName" -> "referenceName". */
	private static String flowReference(String line) {
		if (line == null) return "?";
		int i = line.indexOf(" : ");
		return i < 0 ? line : line.substring(0, i);
	}

	/** "referenceName : text" lines grouped per reference, in first-seen order. */
	private static Map<String, List<String>> flowsByReference(List<String> lines) {
		Map<String, List<String>> perRef = new LinkedHashMap<String, List<String>>();
		for (String line : lines) {
			String ref = flowReference(line);
			String text = (line.length() > ref.length() + 3) ? line.substring(ref.length() + 3) : line;
			perRef.computeIfAbsent(ref, k -> new ArrayList<String>()).add(text);
		}
		return perRef;
	}

	/** Small rounded "kind" badge (RENAME / DELETE). */
	private static final class Badge extends JLabel {
		private static final long serialVersionUID = 1L;
		private final Color fill;

		Badge(String text, Color fg, Color fill) {
			super(text);
			this.fill = fill;
			setOpaque(false);
			setForeground(fg);
			setFont(getFont().deriveFont(Font.BOLD, Math.max(9f, getFont().getSize2D() - 2f)));
			setBorder(BorderFactory.createEmptyBorder(1, 6, 1, 6));
		}

		@Override
		protected void paintComponent(Graphics g) {
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(fill);
			g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
			g2.dispose();
			super.paintComponent(g);
		}
	}

	/** 13x13 filled accent square with a white dash: "partial" state of a group box. */
	private static final class DashIcon implements Icon {
		@Override
		public int getIconWidth() { return 13; }

		@Override
		public int getIconHeight() { return 13; }

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y) {
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(ACCENT);
			g2.fillRoundRect(x, y, 13, 13, 3, 3);
			g2.setColor(Color.WHITE);
			g2.fillRect(x + 3, y + 6, 7, 2);
			g2.dispose();
		}
	}

	private static String safeName(IRPModelElement el) {
		try { return el != null ? el.getName() : "null"; } catch (Exception e) { return "<?>"; }
	}

	private static String safeGuid(IRPModelElement el) {
		try { return el != null ? el.getGUID() : null; } catch (Exception e) { return null; }
	}

	@Override
	public String commandName() {
		return COMMAND;
	}

	@Override
	public boolean isUndoable() {
		return true;
	}

	@Override
	public boolean isInteractive() {
		return true;
	}
}
