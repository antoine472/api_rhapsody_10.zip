package main.gui.tools;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.KeyboardFocusManager;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Logger;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DropMode;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.TransferHandler;
import javax.swing.WindowConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import com.telelogic.rhapsody.core.IRPModelElement;

import main.gui.tools.model.FlowItemEntry;
import main.gui.tools.model.FlowItemScanResult;
import main.gui.tools.model.PackageEntry;

/**
 * Purpose-built, non-modal selector dialog (select, create, rename, delete,
 * move) shared by the "Select ... definition" tools. Uses a JTree to mirror
 * the Rhapsody browser hierarchy.
 *
 * Design:
 *  - JTree whose structure is built from {@link FlowItemEntry#pkgPath()} + name.
 *  - Each node (container or leaf) shows the Rhapsody browser icon of its element
 *    via getIconFileName(), like the Rhapsody browser (folder / itemIcon as fallback).
 *  - Search filters visible entries and rebuilds the tree on each keystroke (debounced).
 *  - No Rhapsody API calls on the EDT while the dialog is visible, except, during
 *    a tree rebuild, icon resolution via getIconFileName() memoized per concept
 *    (never during repaint: the renderer reads NodeData.icon).
 *  - The "Create in" field mirrors the currently selected tree node (package or Flow Item).
 *
 * Live behaviour (Rhapsody-native OK / Apply):
 *  - {@link #open()} shows the dialog and returns at once; the tool's menu
 *    command returns too, so Rhapsody is idle while the selector is open.
 *  - OK applies the selection and closes; Apply applies it and keeps the dialog
 *    open; Escape / close box just close it. Nothing is ever discarded: every
 *    OK, Apply, create, rename, delete and move is a job of the shared
 *    {@link SelectorWorker}, run right away off the EDT in its own undo
 *    transaction, so the browser and the diagrams show it immediately and
 *    Ctrl+Z in Rhapsody reverts it one job at a time.
 *  - While one of its jobs runs, the dialog shows a wait cursor and ignores
 *    its actions (OK, Apply, edits, Escape, tree rebuilds).
 *
 * Threading contract:
 *  - {@link #open()} is called from the Rhapsody plugin thread (T1), which
 *    returns right after.
 *  - The tree and its working entries are touched on the EDT only; the worker
 *    hands its results back through {@code SelectorWorker} completion callbacks.
 */
public final class FlowItemSelectorDialog {

    // -- Static state --------------------------------------------------------

    private static final Logger LOG = Logger.getLogger(FlowItemSelectorDialog.class.getName());

    private static volatile String lastSelectedKey = null;

    // -- Instance fields -----------------------------------------------------

    private final FlowItemScanResult data;
    private final SelectorSpec       spec;
    private final SelectorWorker     worker;
    private final String             title;
    private final Icon               itemIcon;

    /** Set true once any OK/Apply/create/rename/delete/move changed the model. */
    private final AtomicBoolean modelMutated = new AtomicBoolean(false);

    /** Called (on the EDT) after each job that changed the model; may be null. */
    private volatile Runnable onModelMutated;

    /** True while one of this dialog's jobs runs: every dialog action is ignored until it ends. */
    private final AtomicBoolean busy = new AtomicBoolean(false);

    /** True if the model was changed during this dialog session. */
    public boolean wasModelMutated() { return modelMutated.get(); }

    // -- Constructor ---------------------------------------------------------

    /**
     * @param data   scanned entries and packages shown in the tree
     * @param spec   type-specific configuration; its {@link SelectorSpec#onApply()}
     *               must be set (OK and Apply run it)
     * @param worker runs every model change of this dialog (see {@link SelectorWorker#shared})
     */
    public FlowItemSelectorDialog(FlowItemScanResult data, SelectorSpec spec, SelectorWorker worker) {
        if (spec == null || spec.onApply() == null) {
            throw new IllegalArgumentException("The selector needs a SelectorSpec with an onApply action");
        }
        if (worker == null) throw new IllegalArgumentException("The selector needs a SelectorWorker");
        this.data     = (data != null) ? data : FlowItemScanResult.empty();
        this.spec     = spec;
        this.worker   = worker;
        this.title    = this.spec.title();
        this.itemIcon = this.spec.itemIcon();
    }

    // -- Public API ----------------------------------------------------------

    /**
     * Shows the dialog and returns at once (non-modal). With nothing to show, a
     * message is displayed instead and nothing opens.
     */
    public void open() {
        open(null);
    }

    /**
     * Same as {@link #open()}; {@code onModelMutated} (may be null) runs on the
     * EDT after each job that changed the model (OK, Apply, create, rename,
     * delete, move), e.g. to invalidate a caller's cache while the dialog is
     * still open.
     */
    public void open(Runnable onModelMutated) {
        this.onModelMutated = onModelMutated;
        if (data.entries().isEmpty() && !data.hasFlowPackages()) {
            JOptionPane.showMessageDialog(null,
                    "No " + spec.typeLabel() + " found in the model.",
                    title, JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        buildDialog().setVisible(true);   // non-modal: returns at once
    }

    // -- Worker plumbing -----------------------------------------------------

    /** Records a model change and notifies the caller (EDT). */
    private void modelChanged() {
        modelMutated.set(true);
        Runnable hook = onModelMutated;
        if (hook != null) {
            try {
                hook.run();
            } catch (Exception e) {
                LOG.warning("onModelMutated hook failed: " + e);
            }
        }
    }

    /**
     * Queues the caller's OK / Apply action on {@code sel}; {@code owner}
     * parents any dialog it shows (the open selector on Apply, null on OK).
     * {@code afterwards} (may be null) runs on the EDT once it is done.
     */
    private void submitApply(FlowItemEntry sel, Window owner, Runnable afterwards) {
        String key = sel.key();
        IRPModelElement element = sel.element();
        worker.submit("Apply " + spec.typeLabel(),
                () -> spec.onApply().apply(key, element, owner),
                outcome -> {
                    if (outcome.failed()) {
                        Toast.showToast("Apply failed: " + outcome.error(), 4500);
                    } else if (Boolean.TRUE.equals(outcome.value())) {
                        modelChanged();
                    }
                    if (afterwards != null) afterwards.run();
                });
    }

    /** Result of an in-dialog Create: the element and its tree key (GUID when readable). */
    private record Created(IRPModelElement element, String key) {}

    // -- Dialog construction -------------------------------------------------

    private JDialog buildDialog() {

        JDialog dialog = new JDialog((Frame) null, title, false);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setLayout(new BorderLayout(8, 8));
        dialog.setMinimumSize(new Dimension(700, 460));
        dialog.setAlwaysOnTop(true);

        // Search
        JTextField searchField = new JTextField();
        searchField.setToolTipText("Type to filter (space = AND). Enter to select, Esc to close.");

        JPanel searchPanel = new JPanel(new BorderLayout(6, 0));
        searchPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 0, 8));
        searchPanel.add(new JLabel("Search:"), BorderLayout.WEST);
        searchPanel.add(searchField, BorderLayout.CENTER);

        // Package name lookup for tree node owner resolution
        Map<String, PackageEntry> pkgByName = new LinkedHashMap<>();
        for (PackageEntry pe : data.flowPackages()) pkgByName.put(pe.name(), pe);

        // Mutable working copy of the entries (EDT only). create / rename /
        // delete / move update this list once the worker has done the change,
        // and every tree rebuild (including search filtering) reads from it,
        // so edits survive re-filtering.
        List<FlowItemEntry> workingEntries = new ArrayList<>(data.entries());

        // Tree
        DefaultMutableTreeNode treeRoot = buildTree(workingEntries, null, pkgByName, spec.anyContainerIsTarget());
        DefaultTreeModel treeModel = new DefaultTreeModel(treeRoot);

        JTree tree = new JTree(treeModel);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setCellRenderer(new EntryRenderer(itemIcon));
        tree.setRowHeight(22);

        JScrollPane scroll = new JScrollPane(tree);
        scroll.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(4, 8, 0, 8),
                BorderFactory.createLineBorder(UiKit.HAIR)));
        scroll.getViewport().setBackground(UiKit.SURFACE);

        expandTopLevel(tree, treeRoot);

        // Create row (only when the type supports creation and a target exists)
        boolean canCreate = spec.canCreateType()
                && (spec.anyContainerIsTarget() || !data.flowPackages().isEmpty());
        JTextField createName = new JTextField(22);
        createName.setToolTipText("Name for the new " + spec.typeLabel());

        // Non-editable field that mirrors the currently selected tree node
        JTextField inField = new JTextField(22);
        inField.setEditable(false);
        inField.setForeground(UiKit.INK2);
        inField.setToolTipText("Target parent (follows the tree selection)");

        JButton btnCreate = UiKit.neutral("Create");
        btnCreate.setEnabled(false);

        // Bottom buttons: Rhapsody-native OK / Apply (OK is the default button).
        // OK applies the current selection and closes; Apply applies it and
        // keeps the dialog open.
        JButton btnOk = UiKit.primary("OK");
        JButton btnApply = UiKit.neutral("Apply");
        btnOk.setEnabled(false);
        btnApply.setEnabled(false);

        // Edit actions -- operate on the currently selected Flow Item.
        JButton btnRename = UiKit.neutral("Rename\u2026");
        JButton btnDelete = UiKit.neutral("Delete\u2026");
        btnRename.setEnabled(false);
        btnDelete.setEnabled(false);

        JPanel editRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        editRow.add(new JLabel("Edit:"));
        editRow.add(btnRename);
        editRow.add(btnDelete);
        editRow.add(UiKit.muted("  (F2: rename \u00B7 Del: delete \u00B7 drag onto a package/item: move)"));

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttonRow.add(btnOk);
        buttonRow.add(btnApply);

        JPanel bottomPanel = new JPanel(new BorderLayout(8, 4));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(4, 8, 8, 8));
        bottomPanel.add(editRow,   BorderLayout.NORTH);
        bottomPanel.add(buttonRow, BorderLayout.EAST);

        if (canCreate) {
            JPanel createRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            createRow.add(new JLabel("Create:"));
            createRow.add(createName);
            createRow.add(new JLabel("in"));
            createRow.add(inField);
            createRow.add(btnCreate);
            bottomPanel.add(createRow, BorderLayout.WEST);
        }

        dialog.add(searchPanel, BorderLayout.NORTH);
        dialog.add(scroll,      BorderLayout.CENTER);
        dialog.add(bottomPanel, BorderLayout.SOUTH);

        // Restore last selection
        restoreLastSelection(tree, treeRoot);

        // Behaviour wiring
        AtomicBoolean closing = new AtomicBoolean(false);

        Runnable closeDialog = () -> {
            if (!closing.compareAndSet(false, true)) return;
            dialog.setAlwaysOnTop(false);
            dialog.setVisible(false);
            SwingUtilities.invokeLater(dialog::dispose);
        };

        // A job of this dialog starts / ends: wait cursor, actions ignored meanwhile.
        Runnable beginBusy = () -> {
            busy.set(true);
            dialog.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        };
        Runnable endBusy = () -> {
            busy.set(false);
            dialog.setCursor(Cursor.getDefaultCursor());
        };

        // Enable/disable buttons according to the current selection.
        Runnable syncButtons = () -> {
            boolean hasEntry = getSelectedEntry(tree) != null;
            boolean editable = isEditableSelected(tree);
            btnOk.setEnabled(hasEntry);
            btnApply.setEnabled(hasEntry);
            btnRename.setEnabled(editable);
            btnDelete.setEnabled(editable);
            inField.setText(getSelectedOwnerName(tree));
        };

        // Rebuilds the tree from workingEntries, applying the current search
        // filter and restoring selection (by the given key, else the previously
        // selected key, else the first entry).
        Consumer<String> rebuild = (String selectKey) -> {
            String query = searchField.getText();
            FlowItemEntry prevSel = getSelectedEntry(tree);
            String prevKey = (selectKey != null) ? selectKey
                           : (prevSel != null)   ? prevSel.key()
                           : null;
            DefaultMutableTreeNode newRoot = buildTree(workingEntries, query, pkgByName, spec.anyContainerIsTarget());
            treeModel.setRoot(newRoot);
            if (query == null || query.isBlank()) {
                expandTopLevel(tree, newRoot);
            } else {
                expandAll(tree, newRoot);
            }
            if (prevKey == null || !selectByKey(tree, newRoot, prevKey)) {
                selectFirst(tree, newRoot);
            }
            syncButtons.run();
        };

        // Debounced search filter
        Timer[] debounceRef = new Timer[1];
        Timer debounce = new Timer(180, e -> {
            // Tree rebuilds call the Rhapsody API (icons): never while a job of
            // this dialog runs.
            if (busy.get()) { debounceRef[0].restart(); return; }
            rebuild.accept(null);
        });
        debounceRef[0] = debounce;
        debounce.setRepeats(false);

        searchField.getDocument().addDocumentListener(onChange(() -> {
            if (!closing.get()) debounce.restart();
        }));

        // Tree selection -> enable/disable buttons + sync inField
        tree.addTreeSelectionListener(e -> syncButtons.run());
        // Sync initial state: restoreLastSelection ran before listener was wired
        syncButtons.run();

        // Create name -> enable/disable Create button
        if (canCreate) {
            createName.getDocument().addDocumentListener(onChange(() ->
                btnCreate.setEnabled(!createName.getText().trim().isBlank())));
        }

        // Actions

        // OK: close, then the worker applies the selection (its dialogs, if any,
        // stand alone since the selector is gone).
        Runnable doOk = () -> {
            if (busy.get()) return;
            FlowItemEntry sel = getSelectedEntry(tree);
            if (sel == null) return;
            lastSelectedKey = sel.key();
            closeDialog.run();
            submitApply(sel, null, null);
        };

        // Apply: the worker applies the selection while the dialog stays open,
        // so the engineer can pick a different item and apply again.
        Runnable doApply = () -> {
            FlowItemEntry sel = getSelectedEntry(tree);
            if (sel == null || !busy.compareAndSet(false, true)) return;
            dialog.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            lastSelectedKey = sel.key();
            submitApply(sel, dialog, endBusy);   // the open selector owns any dialog it shows
        };

        Runnable doCreate = () -> {
            if (busy.get()) return;
            String nm = createName.getText().trim();
            if (nm.isBlank()) return;
            TreePath selPath = tree.getSelectionPath();
            IRPModelElement owner = getOwnerElement(tree);
            if (selPath == null || owner == null) return;

            // The new item is nested under the selected node: its package path is
            // the segment names along the selected path.
            List<String> pkgPath = pathNames(selPath);
            List<IRPModelElement> pathElements = pathElementsAlong(selPath);
            String metaClass = spec.createMetaClass();

            beginBusy.run();
            worker.submit("Create " + spec.typeLabel(),
                    () -> {
                        IRPModelElement created = owner.addNewAggr(metaClass, nm);
                        if (created == null) return null;
                        String key = null;
                        try { key = created.getGUID(); } catch (Exception ignore) {}
                        if (key == null || key.isBlank()) key = "created-" + System.nanoTime();
                        return new Created(created, key);
                    },
                    outcome -> {
                        endBusy.run();
                        if (outcome.failed()) {
                            LOG.severe("Failed to create " + spec.typeLabel() + " '" + nm + "': " + outcome.error());
                            JOptionPane.showMessageDialog(dialog,
                                    "Failed to create " + spec.typeLabel() + ":\n" + outcome.error().getMessage(),
                                    "Create " + spec.typeLabel(), JOptionPane.ERROR_MESSAGE);
                            return;
                        }
                        Created created = outcome.value();
                        if (created == null) {
                            LOG.severe("addNewAggr returned null for name '" + nm + "'");
                            return;
                        }
                        workingEntries.add(new FlowItemEntry(created.key(), nm, "", pkgPath,
                                created.element(), pathElements));
                        modelChanged();
                        createName.setText("");
                        rebuild.accept(created.key());
                    });
        };

        // --- Edit actions --------------------------------------------------

        Runnable doRename = () -> {
            if (busy.get()) return;
            FlowItemEntry sel = getSelectedEntry(tree);
            if (sel == null || sel.element() == null) return;
            String current = sel.name();
            Object input = JOptionPane.showInputDialog(dialog, "New name:", "Rename " + spec.typeLabel(),
                    JOptionPane.PLAIN_MESSAGE, null, null, current);
            if (input == null) return; // cancelled
            String newName = input.toString().trim();
            if (newName.isBlank() || newName.equals(current)) return;

            beginBusy.run();
            worker.submit("Rename " + spec.typeLabel(),
                    () -> FlowItemEditOps.rename(sel.element(), newName),
                    outcome -> {
                        endBusy.run();
                        if (!editSucceeded(dialog, outcome, "Rename " + spec.typeLabel())) return;
                        // Relocate the item AND its descendants so nested Flow Items keep
                        // their place under the renamed parent (not orphaned under the old name).
                        List<String> oldFull = new ArrayList<>(sel.pkgPath()); oldFull.add(current);
                        List<String> newFull = new ArrayList<>(sel.pkgPath()); newFull.add(newName);
                        List<FlowItemEntry> updated = FlowItemTreeEdits.relocate(
                                workingEntries, sel.key(), oldFull, newFull, newName, sel.pathElements());
                        workingEntries.clear();
                        workingEntries.addAll(updated);
                        modelChanged();
                        rebuild.accept(sel.key());
                    });
        };

        Runnable doDelete = () -> {
            if (busy.get()) return;
            FlowItemEntry sel = getSelectedEntry(tree);
            if (sel == null || sel.element() == null) return;
            int ans = JOptionPane.showConfirmDialog(dialog,
                    "Delete " + spec.typeLabel() + " \"" + sel.name() + "\" from the model?",
                    "Delete " + spec.typeLabel(), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (ans != JOptionPane.YES_OPTION) return;

            beginBusy.run();
            worker.submit("Delete " + spec.typeLabel(),
                    () -> FlowItemEditOps.delete(sel.element()),
                    outcome -> {
                        endBusy.run();
                        if (!editSucceeded(dialog, outcome, "Delete " + spec.typeLabel())) return;
                        workingEntries.removeIf(en -> en.key().equals(sel.key()));
                        modelChanged();
                        rebuild.accept(null);
                    });
        };

        // No explicit Cancel button (Rhapsody-native dialogs rely on the window's
        // close box / Escape for that). Escape / close box only close the
        // selector: everything already applied or edited stays in Rhapsody
        // (Ctrl+Z there reverts it one step at a time).
        Runnable doClose = () -> {
            if (busy.get()) return;   // a job is running and may show a dialog owned by this one
            closeDialog.run();
        };

        // Title-bar close box -- same as Escape.
        dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        dialog.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { doClose.run(); }
        });

        btnOk.addActionListener(e -> doOk.run());
        btnApply.addActionListener(e -> doApply.run());
        btnRename.addActionListener(e -> doRename.run());
        btnDelete.addActionListener(e -> doDelete.run());
        if (canCreate) {
            btnCreate.addActionListener(e -> doCreate.run());
            createName.addActionListener(e -> { if (btnCreate.isEnabled()) doCreate.run(); });
        }

        // Double-click or Enter on tree node
        tree.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    if (getSelectedEntry(tree) != null) doOk.run();
                }
            }
        });
        tree.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "select");
        tree.getActionMap().put("select", action(doOk));
        tree.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0), "renameItem");
        tree.getActionMap().put("renameItem", action(doRename));
        tree.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "deleteItem");
        tree.getActionMap().put("deleteItem", action(doDelete));

        // Drag & drop: drag a Flow Item and drop it onto a package or another
        // Flow Item to reparent it (setOwner to the drop target's element).
        tree.setDragEnabled(true);
        tree.setDropMode(DropMode.ON);
        tree.setTransferHandler(new TransferHandler() {
            @Override public int getSourceActions(JComponent c) { return MOVE; }

            @Override protected Transferable createTransferable(JComponent c) {
                FlowItemEntry sel = getSelectedEntry(tree);
                if (sel == null || sel.element() == null) return null;
                return new FlowItemTransferable(sel);
            }

            @Override public boolean canImport(TransferSupport support) {
                if (!support.isDrop() || FIE_FLAVOR == null
                        || !support.isDataFlavorSupported(FIE_FLAVOR)) return false;
                JTree.DropLocation dl = (JTree.DropLocation) support.getDropLocation();
                return ownerElementAt(dl.getPath()) != null;
            }

            @Override public boolean importData(TransferSupport support) {
                if (busy.get() || !canImport(support)) return false;
                try {
                    FlowItemEntry dragged =
                            (FlowItemEntry) support.getTransferable().getTransferData(FIE_FLAVOR);
                    JTree.DropLocation dl = (JTree.DropLocation) support.getDropLocation();
                    TreePath targetPath = dl.getPath();
                    IRPModelElement targetOwner = ownerElementAt(targetPath);
                    if (dragged == null || dragged.element() == null || targetOwner == null) return false;
                    if (targetOwner.equals(dragged.element())) return false; // no drop onto itself

                    List<String> targetNames = pathNames(targetPath);
                    List<IRPModelElement> targetElements = pathElementsAlong(targetPath);
                    beginBusy.run();
                    worker.submit("Move " + spec.typeLabel(),
                            () -> FlowItemEditOps.move(dragged.element(), targetOwner),
                            outcome -> {
                                endBusy.run();
                                if (!editSucceeded(dialog, outcome, "Move " + spec.typeLabel())) return;
                                // Relocate the item AND its descendants under the drop target.
                                List<String> oldFull = new ArrayList<>(dragged.pkgPath()); oldFull.add(dragged.name());
                                List<String> newFull = new ArrayList<>(targetNames); newFull.add(dragged.name());
                                List<FlowItemEntry> updated = FlowItemTreeEdits.relocate(
                                        workingEntries, dragged.key(), oldFull, newFull, dragged.name(),
                                        targetElements);
                                workingEntries.clear();
                                workingEntries.addAll(updated);
                                modelChanged();
                                rebuild.accept(dragged.key());
                            });
                    return true;   // the drop is accepted; the tree follows once the move is done
                } catch (Exception ex) {
                    LOG.severe("Move via drag & drop failed: " + ex.getMessage());
                    return false;
                }
            }
        });

        // Escape anywhere in dialog
        dialog.getRootPane()
              .getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
              .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");
        dialog.getRootPane().getActionMap().put("cancel", action(doClose));

        // Ctrl+F -> focus search
        dialog.getRootPane()
              .getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
              .put(KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK), "focusSearch");
        dialog.getRootPane().getActionMap().put("focusSearch",
                action(() -> searchField.requestFocusInWindow()));

        dialog.getRootPane().setDefaultButton(btnOk);
        SwingUtilities.invokeLater(searchField::requestFocusInWindow);
        dialog.pack();
        centerOnRhapsodyScreen(dialog);
        return dialog;
    }

    /**
     * True when an edit job (rename / delete / move) succeeded; otherwise shows
     * its error in a dialog owned by the selector and logs it.
     */
    private static boolean editSucceeded(JDialog dialog, SelectorWorker.Outcome<FlowItemEditOps.OpResult> outcome,
            String what) {
        String error = null;
        if (outcome.failed()) {
            error = what + " failed: " + outcome.error().getMessage();
        } else if (outcome.value() == null) {
            error = what + " failed.";
        } else if (!outcome.value().ok()) {
            error = outcome.value().error();
        }
        if (error == null) return true;
        LOG.severe(error);
        JOptionPane.showMessageDialog(dialog, error, what, JOptionPane.ERROR_MESSAGE);
        return false;
    }

    // -- Tree model building -------------------------------------------------

    /**
     * Builds a tree from a list of entries, optionally filtered by {@code query}.
     *
     * Tree structure mirrors pkgPath: the first segment is the Flow Package root
     * (folder), subsequent segments are parent Flow Items or sub-packages, and the
     * final node is the entry itself. Intermediate nodes that are themselves entries
     * (Flow Items that contain nested Flow Items) are shown with their concept icon
     * and remain selectable.
     *
     * Each node carries an {@code ownerElement} so that "Create in" can resolve
     * the Rhapsody parent regardless of whether the node is a package or a Flow Item.
     */
    private static DefaultMutableTreeNode buildTree(
            List<FlowItemEntry> entries, String query,
            Map<String, PackageEntry> pkgByName, boolean anyContainerIsTarget) {

        DefaultMutableTreeNode root = new DefaultMutableTreeNode();

        // Pre-build full-path -> entry for all entries (needed to annotate intermediate nodes)
        Map<List<String>, FlowItemEntry> entryByFullPath = new LinkedHashMap<>();
        for (FlowItemEntry e : entries) {
            List<String> fp = new ArrayList<>(e.pkgPath());
            fp.add(e.name());
            entryByFullPath.put(fp, e);
        }

        // Determine which entries to show
        List<FlowItemEntry> toShow;
        boolean filtering = (query != null && !query.isBlank());
        if (filtering) {
            String[] tokens = query.trim().toLowerCase(Locale.ROOT).split("\\s+");
            toShow = new ArrayList<>();
            for (FlowItemEntry e : entries) {
                if (e.matches(tokens)) toShow.add(e);
            }
        } else {
            toShow = entries;
        }

        // Map: full path -> tree node (prevents duplicate nodes for shared ancestors)
        Map<List<String>, DefaultMutableTreeNode> nodeByPath = new LinkedHashMap<>();

        for (FlowItemEntry entry : toShow) {
            List<String> fullPath = new ArrayList<>(entry.pkgPath());
            fullPath.add(entry.name());

            DefaultMutableTreeNode parent = root;

            for (int depth = 1; depth <= fullPath.size(); depth++) {
                List<String> pathKey = new ArrayList<>(fullPath.subList(0, depth));
                DefaultMutableTreeNode node = nodeByPath.get(pathKey);
                if (node == null) {
                    boolean isLeaf    = (depth == fullPath.size());
                    boolean isPkgRoot = (depth == 1) && !isLeaf;
                    String  segName   = fullPath.get(depth - 1);
                    // The concept icon is resolved HERE (build time, concept-cached),
                    // never in the renderer: repaints must make no Rhapsody API call.
                    NodeData nd;
                    if (isLeaf) {
                        nd = new NodeData(entry.name(), entry, entry.element(), isPkgRoot,
                                entry.element(), conceptIcon(entry.element()));
                    } else if (!filtering) {
                        FlowItemEntry iEntry = entryByFullPath.get(pathKey);
                        // ownerElement (create/drop authority): unchanged rule, an entry
                        // container is its own element, a package root is looked up,
                        // anything else is not a target.
                        IRPModelElement owner = (iEntry != null) ? iEntry.element()
                                             : anyContainerIsTarget ? containerTarget(pathElementAt(entry, depth))
                                             : ownerFromPkg(segName, isPkgRoot, pkgByName);
                        // iconElement (icon only): the entry's own element, else the
                        // ancestor recorded by the scanner, so every container shows
                        // its real browser icon without becoming a target.
                        IRPModelElement iconEl = (iEntry != null) ? iEntry.element()
                                               : pathElementAt(entry, depth);
                        nd = new NodeData(segName, iEntry, owner, isPkgRoot, iconEl,
                                conceptIcon(iconEl != null ? iconEl : owner));
                    } else {
                        // Filtered: intermediate nodes are non-selectable context nodes
                        // (entry == null); ownerElement as before (package roots only),
                        // iconElement from the ancestor chain so the real icon shows.
                        IRPModelElement owner  = anyContainerIsTarget
                                ? containerTarget(pathElementAt(entry, depth))
                                : ownerFromPkg(segName, isPkgRoot, pkgByName);
                        IRPModelElement iconEl = pathElementAt(entry, depth);
                        nd = new NodeData(segName, null, owner, isPkgRoot, iconEl,
                                conceptIcon(iconEl != null ? iconEl : owner));
                    }
                    node = new DefaultMutableTreeNode(nd);
                    nodeByPath.put(pathKey, node);
                    parent.add(node);
                } else if (depth < fullPath.size()
                        && node.getUserObject() instanceof NodeData existing
                        && existing.iconElement() == null) {
                    // An earlier entry created this container without knowing its
                    // element; adopt it from this entry for the icon only.
                    IRPModelElement iconEl = pathElementAt(entry, depth);
                    if (iconEl != null) {
                        node.setUserObject(new NodeData(existing.name(), existing.entry(),
                                existing.ownerElement(), existing.isPkgRoot(), iconEl,
                                conceptIcon(iconEl)));
                    }
                }
                parent = node;
            }
        }

        return root;
    }

    // -- Tree navigation helpers ---------------------------------------------

    /** Returns the FlowItemEntry of the currently selected tree node, or null. */
    private static FlowItemEntry getSelectedEntry(JTree tree) {
        TreePath sel = tree.getSelectionPath();
        if (sel == null) return null;
        Object last = sel.getLastPathComponent();
        if (!(last instanceof DefaultMutableTreeNode tn)) return null;
        if (!(tn.getUserObject() instanceof NodeData nd)) return null;
        return nd.entry(); // null if folder/package node
    }

    /** Returns the ownerElement of the currently selected tree node, or null. */
    private static IRPModelElement getOwnerElement(JTree tree) {
        TreePath sel = tree.getSelectionPath();
        if (sel == null) return null;
        if (!(sel.getLastPathComponent() instanceof DefaultMutableTreeNode tn)) return null;
        if (!(tn.getUserObject() instanceof NodeData nd)) return null;
        return nd.ownerElement();
    }

    /** Returns the display name of the current owner, or "" if none. */
    private static String getSelectedOwnerName(JTree tree) {
        TreePath sel = tree.getSelectionPath();
        if (sel == null) return "";
        if (!(sel.getLastPathComponent() instanceof DefaultMutableTreeNode tn)) return "";
        if (!(tn.getUserObject() instanceof NodeData nd)) return "";
        return nd.ownerElement() != null ? nd.name() : "";
    }

    /** True when the selection is an editable Flow Item (has a real model element). */
    private static boolean isEditableSelected(JTree tree) {
        FlowItemEntry sel = getSelectedEntry(tree);
        return sel != null && sel.element() != null;
    }

    /** ownerElement of the node at {@code path} (package element or Flow Item element), or null. */
    private static IRPModelElement ownerElementAt(TreePath path) {
        if (path == null) return null;
        if (!(path.getLastPathComponent() instanceof DefaultMutableTreeNode tn)) return null;
        if (!(tn.getUserObject() instanceof NodeData nd)) return null;
        return nd.ownerElement();
    }

    /** Segment names along a tree path (excluding the hidden root) -- the package path. */
    private static List<String> pathNames(TreePath path) {
        List<String> names = new ArrayList<>();
        Object[] comps = path.getPath();
        for (int i = 1; i < comps.length; i++) {
            if (comps[i] instanceof DefaultMutableTreeNode tn
                    && tn.getUserObject() instanceof NodeData nd) {
                names.add(nd.name());
            }
        }
        return names;
    }

    /**
     * Model elements along a tree path (excluding the hidden root), aligned with
     * {@link #pathNames(TreePath)}, for the icon chain of a created/moved entry:
     * each node's iconElement (else its ownerElement); null when it has neither.
     */
    private static List<IRPModelElement> pathElementsAlong(TreePath path) {
        List<IRPModelElement> els = new ArrayList<>();
        Object[] comps = path.getPath();
        for (int i = 1; i < comps.length; i++) {
            if (comps[i] instanceof DefaultMutableTreeNode tn
                    && tn.getUserObject() instanceof NodeData nd) {
                els.add(nd.iconElement() != null ? nd.iconElement() : nd.ownerElement());
            }
        }
        return els;
    }

    /** Ancestor element of {@code entry} at 1-based path depth {@code depth}, or null. */
    private static IRPModelElement pathElementAt(FlowItemEntry entry, int depth) {
        List<IRPModelElement> els = entry.pathElements();
        int i = depth - 1;
        return (i >= 0 && i < els.size()) ? els.get(i) : null;
    }


    /** Expands only the top-level children of root (the Flow Package nodes). */
    private static void expandTopLevel(JTree tree, DefaultMutableTreeNode root) {
        for (int i = 0; i < root.getChildCount(); i++) {
            DefaultMutableTreeNode pkg = (DefaultMutableTreeNode) root.getChildAt(i);
            tree.expandPath(new TreePath(pkg.getPath()));
        }
    }

    /** Recursively expands every node in the tree (used after filtering). */
    private static void expandAll(JTree tree, DefaultMutableTreeNode root) {
        for (int i = 0; i < root.getChildCount(); i++) {
            expandAllFrom(tree, (DefaultMutableTreeNode) root.getChildAt(i));
        }
    }

    private static void expandAllFrom(JTree tree, DefaultMutableTreeNode node) {
        tree.expandPath(new TreePath(node.getPath()));
        for (int i = 0; i < node.getChildCount(); i++) {
            expandAllFrom(tree, (DefaultMutableTreeNode) node.getChildAt(i));
        }
    }

    // -- Last-selection restore ----------------------------------------------

    private void restoreLastSelection(JTree tree, DefaultMutableTreeNode root) {
        String key = lastSelectedKey;
        if (key == null || !selectByKey(tree, root, key)) {
            selectFirst(tree, root);
        }
    }

    /** Selects the node whose entry has the given key; returns true if found. */
    private static boolean selectByKey(JTree tree, DefaultMutableTreeNode node, String key) {
        if (node.getUserObject() instanceof NodeData nd
                && nd.entry() != null && key.equals(nd.entry().key())) {
            TreePath path = new TreePath(node.getPath());
            tree.setSelectionPath(path);
            tree.scrollPathToVisible(path);
            return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (selectByKey(tree, (DefaultMutableTreeNode) node.getChildAt(i), key)) return true;
        }
        return false;
    }

    /** Selects the first node that has a non-null entry (depth-first). */
    private static void selectFirst(JTree tree, DefaultMutableTreeNode root) {
        DefaultMutableTreeNode first = findFirstEntry(root);
        if (first != null) {
            TreePath path = new TreePath(first.getPath());
            tree.setSelectionPath(path);
            tree.scrollPathToVisible(path);
        }
    }

    private static DefaultMutableTreeNode findFirstEntry(DefaultMutableTreeNode node) {
        if (node.getUserObject() instanceof NodeData nd && nd.entry() != null) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            DefaultMutableTreeNode found = findFirstEntry((DefaultMutableTreeNode) node.getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }

    // -- Static helpers ------------------------------------------------------

    /**
     * Resolves the ownerElement for a non-leaf tree node.
     * For package-root nodes (isPkgRoot == true), looks up the PackageEntry by name.
     * For intermediate Flow Item nodes, the caller provides the element directly.
     */
    private static IRPModelElement ownerFromPkg(
            String segName, boolean isPkgRoot, Map<String, PackageEntry> pkgByName) {
        if (!isPkgRoot) return null;
        PackageEntry pe = pkgByName.get(segName);
        return (pe != null) ? pe.element() : null;
    }

    /** {@code el} as a create/drop target, or null for the project (it cannot own them). */
    private static IRPModelElement containerTarget(IRPModelElement el) {
        if (el == null) return null;
        try {
            if ("Project".equalsIgnoreCase(el.getMetaClass())) return null;
        } catch (Exception e) {
            return null;
        }
        return el;
    }

    private static DocumentListener onChange(Runnable r) {
        return new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e)  { r.run(); }
            @Override public void removeUpdate(DocumentEvent e)  { r.run(); }
            @Override public void changedUpdate(DocumentEvent e) { r.run(); }
        };
    }

    private static AbstractAction action(Runnable r) {
        return new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { r.run(); }
        };
    }

    /**
     * Centres {@code dialog} on the same screen as Rhapsody's main window.
     *
     * <p>Strategy (in order of preference):
     * <ol>
     *   <li><b>Option A</b> — use the AWT window that held keyboard focus just before
     *       the dialog was constructed ({@code KeyboardFocusManager.getActiveWindow()}).
     *       If that window is non-null and visible, delegate to
     *       {@link JDialog#setLocationRelativeTo(Component)} which centres the dialog
     *       on the same screen device as the active window.</li>
     *   <li><b>Option B</b> — if Option A yields nothing useful, find the screen whose
     *       bounds contain the mouse cursor and manually centre the dialog there.</li>
     *   <li><b>Final fallback</b> — primary-screen centre via
     *       {@code setLocationRelativeTo(null)}.</li>
     * </ol>
     *
     * <p>All positioning failures are swallowed silently — a misplaced dialog is
     * always preferable to a crash.
     */
    private static void centerOnRhapsodyScreen(JDialog dialog) {
        // Option A — use the window that was active just before the dialog opened
        try {
            Window active = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
            if (active != null && active.isVisible()) {
                dialog.setLocationRelativeTo(active);
                return;
            }
        } catch (Exception ignore) {}

        // Option B — fallback: screen that contains the mouse cursor
        try {
            Point mouse = MouseInfo.getPointerInfo().getLocation();
            for (GraphicsDevice gd :
                    GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
                Rectangle bounds = gd.getDefaultConfiguration().getBounds();
                if (bounds.contains(mouse)) {
                    int x = bounds.x + Math.max(0, (bounds.width  - dialog.getWidth())  / 2);
                    int y = bounds.y + Math.max(0, (bounds.height - dialog.getHeight()) / 2);
                    dialog.setLocation(x, y);
                    return;
                }
            }
        } catch (Exception ignore) {}

        // Final fallback — primary screen centre
        dialog.setLocationRelativeTo(null);
    }

    // -- Drag & drop ---------------------------------------------------------

    /** Intra-JVM local-object flavor carrying the dragged {@link FlowItemEntry}. */
    private static final DataFlavor FIE_FLAVOR = makeLocalFlavor();

    private static DataFlavor makeLocalFlavor() {
        try {
            return new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType
                    + ";class=" + FlowItemEntry.class.getName());
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    /** Carries a {@link FlowItemEntry} by reference within the JVM (never serialized). */
    private static final class FlowItemTransferable implements Transferable {
        private final FlowItemEntry entry;

        FlowItemTransferable(FlowItemEntry entry) { this.entry = entry; }

        @Override public DataFlavor[] getTransferDataFlavors() {
            return (FIE_FLAVOR != null) ? new DataFlavor[]{ FIE_FLAVOR } : new DataFlavor[0];
        }

        @Override public boolean isDataFlavorSupported(DataFlavor flavor) {
            return FIE_FLAVOR != null && FIE_FLAVOR.equals(flavor);
        }

        @Override public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (FIE_FLAVOR == null || !FIE_FLAVOR.equals(flavor)) throw new UnsupportedFlavorException(flavor);
            return entry;
        }
    }

    // -- Node data -----------------------------------------------------------

    /**
     * User object stored in each tree node.
     * entry == null       -> package/folder node, not selectable as "Select".
     * entry != null       -> Flow Item node, selectable.
     * ownerElement != null-> this node can be used as parent for a new Flow Item
     *                        (create target / drop target / "Create in"). Never set
     *                        for icon purposes only.
     * isPkgRoot           -> top-level Flow Package node.
     * iconElement         -> element whose Rhapsody browser icon this node shows
     *                        (ancestor recorded by the scanner); icon-only, it never
     *                        widens the create/drop target set. May be null.
     * icon                -> the resolved concept icon, computed at build time by
     *                        conceptIcon(entry element / iconElement / ownerElement);
     *                        display-only, may be null. The renderer reads ONLY this
     *                        field for icons (no Rhapsody API call during repaint).
     * Icons: icon, else folder (no entry) / itemIcon (entry).
     */
    private record NodeData(
            String           name,
            FlowItemEntry    entry,
            IRPModelElement  ownerElement,
            boolean          isPkgRoot,
            IRPModelElement  iconElement,
            Icon             icon) {
    }

    // -- Concept icons -------------------------------------------------------

    /** Folder holding the profile's concept icons (same scheme as the other tool icons). */
    private static final String ICON_DIR = "../SafranArchitectureProfile/Icons/";

    /** Sentinel cached for icon paths that were probed and could not be loaded. */
    private static final Icon MISSING = new ImageIcon();

    /**
     * Raw getIconFileName() value -> loaded icon (or {@link #MISSING}): decode dedup.
     * Touched only from {@link #buildTree} (T1 for the initial build, EDT for rebuilds;
     * never concurrently: the initial build completes before open() returns).
     */
    private static final Map<String, Icon> ICON_CACHE = new HashMap<>();

    /**
     * Concept key ("nt:&lt;New Term&gt;" or "mc:&lt;metaclass&gt;") -> resolved icon (or
     * {@link #MISSING}). Memoizes {@link #resolveIconRaw} so getIconFileName() (a
     * COM/JNI call) runs at most once per distinct concept for the JVM session,
     * lazily, only for concepts actually shown. Same threading as {@link #ICON_CACHE}.
     */
    private static final Map<String, Icon> ICON_BY_CONCEPT = new HashMap<>();

    /** Image extensions Swing's ImageIcon can decode directly. */
    private static final List<String> SWING_IMAGE_EXTS = List.of(".png", ".gif", ".jpg", ".jpeg");

    /**
     * Concept icon of a model element, memoized per concept: the New Term name when
     * the element has one, else its base metaclass. Elements of the same concept share
     * the same browser icon, so the element-level resolver ({@link #resolveIconRaw})
     * runs once per concept, not once per element. When the key cannot be computed
     * the element is resolved directly, uncached. Never throws.
     */
    private static Icon conceptIcon(IRPModelElement el) {
        if (el == null) return null;
        String key = conceptKey(el);
        if (key == null) return resolveIconRaw(el);

        Icon cached = ICON_BY_CONCEPT.get(key);
        if (cached != null) return (cached == MISSING) ? null : cached;

        Icon icon = resolveIconRaw(el);
        ICON_BY_CONCEPT.put(key, (icon != null) ? icon : MISSING);
        return icon;
    }

    /** "nt:&lt;udmc&gt;" for a New Term element, "mc:&lt;metaclass&gt;" otherwise, null if unknown. */
    private static String conceptKey(IRPModelElement el) {
        try {
            String udmc = el.getUserDefinedMetaClass();
            if (udmc != null && !udmc.isBlank()) return "nt:" + udmc.trim();
        } catch (Exception ignore) {}
        try {
            String mc = el.getMetaClass();
            if (mc != null && !mc.isBlank()) return "mc:" + mc.trim();
        } catch (Exception ignore) {}
        return null;
    }

    /**
     * Resolves the Rhapsody browser icon of a model element, exactly as the Rhapsody
     * browser shows it: {@code IRPModelElement.getIconFileName()} returns a profile
     * {@code .ico} for New Terms (loaded as the matching {@code .png} deployed next
     * to it or in {@link #ICON_DIR}), a built-in {@code .gif} for base metaclasses,
     * or an empty string. Returns null when nothing is available or loadable so the
     * caller can fall back to its default icon. Never throws. Element-level; callers
     * go through {@link #conceptIcon} so this runs once per concept.
     */
    private static Icon resolveIconRaw(IRPModelElement el) {
        if (el == null) return null;
        try {
            String raw;
            try {
                raw = el.getIconFileName();
            } catch (Exception ex) {
                raw = null;
            }
            if (raw == null) return null;
            raw = raw.trim();
            if (raw.isEmpty()) return null;

            Icon cached = ICON_CACHE.get(raw);
            if (cached != null) return (cached == MISSING) ? null : cached;

            Icon loaded = loadIconFor(raw);
            ICON_CACHE.put(raw, (loaded != null) ? loaded : MISSING);
            return loaded;
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Turns a raw icon path from Rhapsody into a loadable Swing icon, or null.
     * Candidates, in order: the file as given (when its extension is Swing-readable)
     * in its own folder then in {@link #ICON_DIR}; then the same base name with a
     * {@code .png} extension (Swing cannot decode {@code .ico}, but the matching
     * PNG is deployed) in its own folder then in {@link #ICON_DIR}.
     */
    private static Icon loadIconFor(String raw) {
        String norm = raw.replace('\\', '/');
        int slash = norm.lastIndexOf('/');
        String base = (slash >= 0) ? norm.substring(slash + 1) : norm;
        String dir  = (slash >= 0) ? norm.substring(0, slash + 1) : "";
        if (base.isEmpty()) return null;

        String lower = base.toLowerCase(Locale.ROOT);
        int dot = base.lastIndexOf('.');
        String stem = (dot > 0) ? base.substring(0, dot) : base;

        List<String> candidates = new ArrayList<>(4);
        if (SWING_IMAGE_EXTS.stream().anyMatch(lower::endsWith)) {
            if (!dir.isEmpty()) candidates.add(dir + base);
            candidates.add(ICON_DIR + base);
        }
        String png = stem + ".png";
        if (!dir.isEmpty()) candidates.add(dir + png);
        candidates.add(ICON_DIR + png);

        for (String path : candidates) {
            try {
                if (new File(path).isFile()) {
                    ImageIcon ii = new ImageIcon(path);
                    if (ii.getIconWidth() > 0) return ii;
                }
            } catch (Exception ignore) {}
        }
        return null;
    }

    // -- Cell renderer -------------------------------------------------------

    private static final class EntryRenderer extends DefaultTreeCellRenderer {
        private final Icon itemIcon;

        EntryRenderer(Icon icon) { this.itemIcon = icon; }

        @Override
        public Component getTreeCellRendererComponent(
                JTree tree, Object value, boolean sel, boolean expanded,
                boolean leaf, int row, boolean hasFocus) {

            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

            if (!(value instanceof DefaultMutableTreeNode tn)) return this;
            if (!(tn.getUserObject() instanceof NodeData nd)) return this;

            if (nd.entry() != null && !nd.entry().stereotypeLabel().isBlank()) {
                setText(nd.entry().stereotypeLabel() + " " + nd.name());
            } else {
                setText(nd.name());
            }

            // Icons were resolved at build time (NodeData.icon): no Rhapsody API call here.
            Icon ic = nd.icon();
            if (nd.entry() != null) {
                // Selectable item: real concept icon, else the spec's item icon
                setIcon(ic != null ? ic
                        : (itemIcon != null && itemIcon.getIconWidth() > 0 ? itemIcon
                                                                           : getDefaultLeafIcon()));
            } else {
                // Container node: its browser icon, else folder
                setIcon(ic != null ? ic : (expanded ? getDefaultOpenIcon() : getDefaultClosedIcon()));
            }
            return this;
        }
    }
}
