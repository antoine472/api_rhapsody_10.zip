package main.gui.tools;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.InputMap;
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
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import com.telelogic.rhapsody.core.IRPApplication;
import com.telelogic.rhapsody.core.IRPClassifier;
import com.telelogic.rhapsody.core.IRPCollection;
import com.telelogic.rhapsody.core.IRPModelElement;
import com.telelogic.rhapsody.core.IRPPackage;
import com.telelogic.rhapsody.core.IRPStereotype;

/**
 * Sélecteur Swing cache-first pour des éléments Rhapsody.
 *
 * Principes :
 * - aucun appel API Rhapsody pendant que la fenêtre est visible ;
 * - tous les noms, chemins, stéréotypes et conteneurs sont lus avant l'affichage ;
 * - la création est exécutée seulement après fermeture visuelle de la fenêtre ;
 * - la création conserve l'ancien comportement : owner.addNewAggr(type, name).
 */
public class ItemTreeSelector {

	private static final String FLOW_PKG_UDMC = "F - Flow Package";
	private static final String HIDE_STEREOTYPE_NAME = "Flow Item";

	private final IRPApplication rhApp;
	private final String title;
	private final Icon itemIcon;
	private final CreateContext createCtx;
	private final SelectorConfig cfg;

	private final Icon rootAnchorIcon;
	private final String rootAnchorKey;

	private final Map<String, Snapshot> snapshotByKey = new HashMap<>();
	private final Map<String, Boolean> flowPkgCache = new HashMap<>();

	private final List<Entry> allEntries;

	public ItemTreeSelector(IRPApplication rhApp, List<IRPModelElement> items, Icon itemIcon, String name) {
	this(rhApp, items, itemIcon, name, SelectorConfig.flowItemsDefault());
}

public ItemTreeSelector(IRPApplication rhApp,
		List<IRPModelElement> items,
		Icon itemIcon,
		String name,
		CreateContext createCtx) {
	this(rhApp, items, itemIcon, name, createCtx, SelectorConfig.flowItemsDefault());
}

public ItemTreeSelector(IRPApplication rhApp,
		List<IRPModelElement> items,
		Icon itemIcon,
		String name,
		SelectorConfig cfg) {
	this(rhApp, items, itemIcon, name, CreateContext.fromConfig(cfg), cfg);
}

public ItemTreeSelector(IRPApplication rhApp,
		List<IRPModelElement> items,
		Icon itemIcon,
		String name,
		CreateContext createCtx,
		SelectorConfig cfg) {

	this.rhApp = rhApp;
	this.itemIcon = itemIcon;
	this.title = (name == null || name.isBlank()) ? "Select item..." : name;
	this.cfg = (cfg == null) ? SelectorConfig.flowItemsDefault() : cfg;
	this.rootAnchorIcon = this.cfg.rootAnchorIcon;
	this.rootAnchorKey = this.cfg.rootAnchor != null ? safeKey(this.cfg.rootAnchor) : null;
	this.createCtx = createCtx != null && createCtx.enabled ? createCtx.snapshot() : null;

	setLookAndFeel();

	List<IRPModelElement> safeItems = items == null ? List.of() : new ArrayList<>(items);
	List<Entry> tmp = new ArrayList<>();

	if (this.cfg.useFilteredPathHierarchy && this.cfg.rootAnchor != null) {
		buildFilteredPathEntries(safeItems, tmp);
	} else {
		buildOwnerChainEntries(safeItems, tmp);
	}

	tmp.sort(Comparator.comparing(Entry::pseudoPath, String.CASE_INSENSITIVE_ORDER));
	this.allEntries = Collections.unmodifiableList(tmp);
}
	public IRPModelElement showSelectionDialog() {
		if (allEntries.isEmpty()) {
			String msg = cfg != null && cfg.restrictToFlowPackage
					? "No Flow Items found under a package with userDefinedMetaClass == \"" + FLOW_PKG_UDMC + "\"."
					: "No selectable items found.";

			JOptionPane.showMessageDialog(null, msg, "Info", JOptionPane.INFORMATION_MESSAGE);
			return null;
		}

		return showDialog();
	}

	private void buildOwnerChainEntries(List<IRPModelElement> items, List<Entry> entries) {
		for (IRPModelElement el : items) {
			if (el == null) continue;

			try {
				String mc = safe(el.getMetaClass());
				if ("Package".equalsIgnoreCase(mc)) continue;

				IRPModelElement root = null;

				if (cfg.restrictToFlowPackage) {
					root = findFlowPackageRootCached(el);
					if (root == null) continue;
				} else if (cfg.rootAnchor != null) {
					if (!isAncestorOrSame(cfg.rootAnchor, el)) continue;
					root = cfg.rootAnchor;
				}

				Snapshot leafSnap = snapshotFor(el, true);
				List<Snapshot> owners = computeOwnerChainWithBrowserGroups(el, leafSnap, root);

				if (owners == null) continue;

				entries.add(new Entry(leafSnap, owners));
			} catch (Exception ignore) {
				// Un élément invalide ne doit pas empêcher l'affichage du sélecteur.
			}
		}
	}

	private void buildFilteredPathEntries(List<IRPModelElement> items, List<Entry> entries) {
		Snapshot rootSnap = snapshotFor(cfg.rootAnchor, false);

		class Tmp {
			final Snapshot leaf;
			final List<String> tokens;

			Tmp(Snapshot leaf, List<String> tokens) {
				this.leaf = leaf;
				this.tokens = tokens;
			}
		}

		List<Tmp> tmpItems = new ArrayList<>();
		Map<String, Snapshot> snapByLogicalKey = new HashMap<>();

		for (IRPModelElement el : items) {
			if (el == null) continue;

			Snapshot leafSnap = snapshotFor(el, true);
			List<String> tokens = computeModeLogicalTokens(el);

			tmpItems.add(new Tmp(leafSnap, tokens));

			String logicalKey = String.join("/", tokens).toLowerCase(Locale.ROOT);
			snapByLogicalKey.putIfAbsent(logicalKey, leafSnap);
		}

		for (Tmp item : tmpItems) {
			List<Snapshot> owners = new ArrayList<>();

			if (cfg.includeRootAnchorNode) {
				owners.add(rootSnap);
			}

			StringBuilder sb = new StringBuilder();

			for (int i = 0; i < item.tokens.size() - 1; i++) {
				if (i > 0) sb.append("/");
				sb.append(item.tokens.get(i));

				String pKey = sb.toString().toLowerCase(Locale.ROOT);
				Snapshot parentSnap = snapByLogicalKey.get(pKey);

				if (parentSnap == null) {
					String vKey = "virt:" + rootSnap.key + ":" + pKey;
					parentSnap = virtualSnapshot(vKey, item.tokens.get(i));
				}

				owners.add(parentSnap);
			}

			entries.add(new Entry(item.leaf, owners));
		}
	}

	public static final class SelectorConfig {

		public final boolean allowCreate;
		public final String createType;
		public final boolean restrictToFlowPackage;
		public final IRPModelElement rootAnchor;
		public final boolean includeRootAnchorNode;
		public final Icon rootAnchorIcon;
		public final Set<String> containerUdmcLower;
		public final Set<String> containerMetaClassLower;
		public final Map<String, String> browserGroupByChildUdmcLower;
		public final boolean useFilteredPathHierarchy;
		public final Set<String> skipPathTokensLower;

		private SelectorConfig(boolean restrictToFlowPackage,
				IRPModelElement rootAnchor,
				boolean includeRootAnchorNode,
				Icon rootAnchorIcon,
				Set<String> containerUdmcLower,
				Set<String> containerMetaClassLower,
				Map<String, String> browserGroupByChildUdmcLower,
				boolean useFilteredPathHierarchy,
				Set<String> skipPathTokensLower,
				boolean allowCreate,
				String createType) {

			this.restrictToFlowPackage = restrictToFlowPackage;
			this.rootAnchor = rootAnchor;
			this.includeRootAnchorNode = includeRootAnchorNode;
			this.rootAnchorIcon = rootAnchorIcon;
			this.containerUdmcLower = containerUdmcLower == null ? Set.of() : Set.copyOf(containerUdmcLower);
			this.containerMetaClassLower = containerMetaClassLower == null ? Set.of() : Set.copyOf(containerMetaClassLower);
			this.browserGroupByChildUdmcLower = browserGroupByChildUdmcLower == null ? Map.of() : Map.copyOf(browserGroupByChildUdmcLower);
			this.useFilteredPathHierarchy = useFilteredPathHierarchy;
			this.skipPathTokensLower = skipPathTokensLower == null ? Set.of() : Set.copyOf(skipPathTokensLower);
			this.allowCreate = allowCreate;
			this.createType = safe(createType);
		}

		public static SelectorConfig flowItemsDefault() {
			return new SelectorConfig(
					true,
					null,
					true,
					null,
					Set.of("flow item"),
					Set.of(),
					Map.of(),
					false,
					Set.of(),

					true,          // allowCreate
					"Flow Item"    // createType
			);
		}

		public static SelectorConfig functionBrowser(
				IRPModelElement representedClass,
				Icon representedClassIcon) {

			return new SelectorConfig(
					false,
					representedClass,
					true,
					representedClassIcon,
					Set.of("function", "function with reference"),
					Set.of(),
					Map.of(),
					false,
					Set.of(),

					true,          // allowCreate
					"Function"     // createType
			);
		}

		public static SelectorConfig modeBrowser(
				IRPModelElement representedClass,
				Icon representedClassIcon) {

			Set<String> skip = Set.of(
					"states",
					"operating mode chart",
					"operatingmodechartdiagram",
					"operating mode chart diagram");

			Set<String> metaContainers = Set.of(
					"statechart",
					"state",
					"state machine",
					"region");

			return new SelectorConfig(
					false,
					representedClass,
					true,
					representedClassIcon,
					Set.of(),
					metaContainers,
					Map.of(),
					true,
					skip,

					true,      // allowCreate
					"Mode"     // createType
			);
		}

		public static SelectorConfig underRoot(
				IRPModelElement root,
				boolean includeRoot) {

			return new SelectorConfig(
					false,
					root,
					includeRoot,
					null,
					Set.of(),
					Set.of(),
					Map.of(),
					false,
					Set.of(),

					false,     // allowCreate
					""         // createType
			);
		}
	}

	public static final class CreateContext {
		public static CreateContext fromConfig(SelectorConfig cfg) {
			if (cfg == null) return null;
			if (!cfg.allowCreate) return null;
			if (safe(cfg.createType).isBlank()) return null;

			return new CreateContext(
					true,
					null,
					cfg.createType,
					null,
					false,
					false);
		}
		public final boolean enabled;
		public final IRPModelElement sourceDataflowOrMessage;
		public final String flowItemUdmcOrStereoName;
		public final String suggestedNameOverride;
		public final boolean copyStereotypesFromSource;
		public final boolean copyNewTerms;

		private String suggestedName;
		private List<IRPStereotype> stereotypesToCopy;
		private List<String> stereotypeNamesToCopy;

		public CreateContext(IRPModelElement source, String flowItemUdmcOrStereoName) {
			this(true, source, flowItemUdmcOrStereoName, null, true, false);
		}

		public CreateContext(boolean enabled,
				IRPModelElement sourceDataflowOrMessage,
				String flowItemUdmcOrStereoName,
				String suggestedNameOverride,
				boolean copyStereotypesFromSource,
				boolean copyNewTerms) {

			this.enabled = enabled;
			this.sourceDataflowOrMessage = sourceDataflowOrMessage;
			this.flowItemUdmcOrStereoName = flowItemUdmcOrStereoName;
			this.suggestedNameOverride = suggestedNameOverride;
			this.copyStereotypesFromSource = copyStereotypesFromSource;
			this.copyNewTerms = copyNewTerms;
		}

		
		private CreateContext snapshot() {
			if (!enabled) return this;

			String nm = safe(suggestedNameOverride);

			if (nm.isBlank() && sourceDataflowOrMessage != null) {
				try {
					nm = safe(sourceDataflowOrMessage.getName());

					if (nm.isBlank()) {
						nm = safe(sourceDataflowOrMessage.getDisplayName());
					}
				} catch (Exception ignore) {
					// Le nom suggéré reste vide si la source est illisible.
				}
			}

			suggestedName = sanitizeName(nm);

			List<IRPStereotype> stsToCopy = new ArrayList<>();
			List<String> names = new ArrayList<>();

			if (copyStereotypesFromSource && sourceDataflowOrMessage != null) {
				try {
					IRPCollection sts = sourceDataflowOrMessage.getStereotypes();

					if (sts != null && sts.getCount() > 0) {
						@SuppressWarnings("unchecked")
						List<Object> list = sts.toList();

						Set<String> seen = new LinkedHashSet<>();

						for (Object o : list) {
							if (!(o instanceof IRPStereotype st)) continue;

							if (!copyNewTerms) {
								try {
									if (st.getIsNewTerm() == 1) continue;
								} catch (Exception ignore) {
									// Si l'information est indisponible, on continue le filtrage par nom.
								}
							}

							String sname = safe(st.getName());
							if (sname.isBlank()) continue;

							if (flowItemUdmcOrStereoName != null
									&& sname.equalsIgnoreCase(flowItemUdmcOrStereoName)) {
								continue;
							}

							if (HIDE_STEREOTYPE_NAME.equalsIgnoreCase(sname)) continue;

							String key = sname.toLowerCase(Locale.ROOT);

							if (seen.add(key)) {
								stsToCopy.add(st);
								names.add(sname);
							}
						}
					}
				} catch (Exception ignore) {
					// La copie des stéréotypes est optionnelle.
				}
			}

			stereotypesToCopy = List.copyOf(stsToCopy);
			stereotypeNamesToCopy = List.copyOf(names);

			return this;
		}

		public String getSuggestedName() {
			return safe(suggestedName);
		}

		public List<IRPStereotype> getStereotypesToCopy() {
			return stereotypesToCopy == null ? List.of() : stereotypesToCopy;
		}

		public List<String> getStereotypeNamesToCopy() {
			return stereotypeNamesToCopy == null ? List.of() : stereotypeNamesToCopy;
		}

		private static String sanitizeName(String s) {
			return safe(s).trim().replaceAll("\\s+", " ");
		}
	}

	private IRPModelElement showDialog() {
		final JDialog dialog = new JDialog((Frame) null, title, true);

		dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		dialog.setLayout(new BorderLayout(10, 10));
		dialog.setMinimumSize(new Dimension(900, 580));
		dialog.setLocationByPlatform(false);
		dialog.setLocationRelativeTo(null);
		dialog.setAlwaysOnTop(false);

		JPanel top = new JPanel(new BorderLayout(8, 8));
		top.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));
		top.add(new JLabel("Search:"), BorderLayout.WEST);

		JTextField search = new JTextField();
		search.setToolTipText("Type to filter. Double-click to select.");
		top.add(search, BorderLayout.CENTER);

		DefaultMutableTreeNode root = new DefaultMutableTreeNode("Items");
		DefaultTreeModel model = new DefaultTreeModel(root);

		JTree tree = new JTree(model);
		tree.setRootVisible(false);
		tree.setShowsRootHandles(true);
		tree.setRowHeight(22);
		tree.setCellRenderer(new SafeTreeRenderer(itemIcon, rootAnchorIcon, rootAnchorKey));

		JScrollPane scroll = new JScrollPane(tree);
		scroll.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));

		JTextField selectedPath = new JTextField();
		selectedPath.setEditable(false);

		JButton btnSelect = new JButton("Select");
		JButton btnCancel = new JButton("Cancel");
		btnSelect.setEnabled(false);

		JPanel createPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		JTextField createName = new JTextField(26);
		JButton btnCreate = new JButton("Create");
		JLabel createHint = new JLabel();

		boolean createEnabled = createCtx != null;

		if (createEnabled) {
			createHint.setText("Create Flow Item:");
			createName.setText(createCtx.getSuggestedName());
			createName.setToolTipText("Name of the new Flow Item.");
			btnCreate.setEnabled(false);

			createPanel.add(createHint);
			createPanel.add(createName);
			createPanel.add(btnCreate);
		}

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		buttons.add(btnCancel);
		buttons.add(btnSelect);

		JPanel bottom = new JPanel(new BorderLayout(10, 10));
		bottom.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));

		JPanel selPanel = new JPanel(new BorderLayout(6, 6));
		selPanel.add(new JLabel("Selected:"), BorderLayout.WEST);
		selPanel.add(selectedPath, BorderLayout.CENTER);

		bottom.add(selPanel, BorderLayout.CENTER);
		bottom.add(buttons, BorderLayout.EAST);

		if (createEnabled) {
			bottom.add(createPanel, BorderLayout.WEST);
		}

		dialog.add(top, BorderLayout.NORTH);
		dialog.add(scroll, BorderLayout.CENTER);
		dialog.add(bottom, BorderLayout.SOUTH);

		rebuildTree(root, model, allEntries);
		expandToDepth(tree, 3);
		selectFirstSelectable(tree);

		final Timer debounce = new Timer(200, null);
		debounce.setRepeats(false);

		final AtomicBoolean closing = new AtomicBoolean(false);
		final AtomicBoolean busyCreate = new AtomicBoolean(false);

		Runnable smoothClose = () -> {
			if (!closing.compareAndSet(false, true)) return;

			debounce.stop();

			try {
				dialog.setAlwaysOnTop(false);
			} catch (Exception ignore) {
				// Fermeture robuste.
			}

			try {
				dialog.setVisible(false);
			} catch (Exception ignore) {
				// Fermeture robuste.
			}

			SwingUtilities.invokeLater(() -> {
				try {
					dialog.dispose();
				} catch (Exception ignore) {
					// Fermeture robuste.
				}
			});
		};

		tree.addTreeSelectionListener(e -> {
			if (closing.get()) return;

			Entry sel = getSelectedEntry(tree);
			btnSelect.setEnabled(sel != null);
			selectedPath.setText(sel != null ? sel.pseudoPath() : "");

			if (createEnabled) {
				Snapshot owner = getSelectedOwnerSnapshot(tree);
				btnCreate.setEnabled(owner != null && owner.canOwnFlowItems);
			}
		});

		dialog.getRootPane().setDefaultButton(btnSelect);

		InputMap im = dialog.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
		ActionMap am = dialog.getRootPane().getActionMap();

		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");
		am.put("cancel", new AbstractAction() {
			private static final long serialVersionUID = 1L;

			@Override
			public void actionPerformed(java.awt.event.ActionEvent e) {
				if (!closing.get()) {
					btnCancel.doClick();
				}
			}
		});

		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK), "focusSearch");
		am.put("focusSearch", new AbstractAction() {
			private static final long serialVersionUID = 1L;

			@Override
			public void actionPerformed(java.awt.event.ActionEvent e) {
				search.requestFocusInWindow();
			}
		});

		if (createEnabled) {
			createName.addActionListener(e -> {
				if (!closing.get() && btnCreate.isEnabled()) {
					btnCreate.doClick();
				}
			});
		}

		final AtomicReference<String> lastQuery = new AtomicReference<>("");

		debounce.addActionListener(evt -> {
			if (closing.get()) return;

			String q = safe(search.getText()).trim().toLowerCase(Locale.ROOT);

			if (Objects.equals(lastQuery.get(), q)) return;

			lastQuery.set(q);

			String[] tokens = q.isBlank() ? new String[0] : q.split("\\s+");

			List<Entry> filtered = new ArrayList<>();

			for (Entry en : allEntries) {
				if (en.matches(tokens)) {
					filtered.add(en);
				}
			}

			rebuildTree(root, model, filtered);
			expandToDepth(tree, 3);
			selectFirstSelectable(tree);
		});

		search.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) {
				if (!closing.get()) debounce.restart();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				if (!closing.get()) debounce.restart();
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				if (!closing.get()) debounce.restart();
			}
		});

		AtomicReference<IRPModelElement> result = new AtomicReference<>(null);

		btnSelect.addActionListener(e -> {
			if (closing.get()) return;

			Entry en = getSelectedEntry(tree);
			result.set(en != null ? en.leaf.element : null);

			SwingUtilities.invokeLater(smoothClose);
		});

		btnCancel.addActionListener(e -> {
			if (closing.get()) return;

			result.set(null);
			SwingUtilities.invokeLater(smoothClose);
		});

		tree.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (closing.get()) return;

				if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
					Entry en = getSelectedEntry(tree);

					if (en != null) {
						SwingUtilities.invokeLater(btnSelect::doClick);
					}
				}
			}
		});

		if (createEnabled) {
			btnCreate.addActionListener(e -> {
				if (closing.get()) return;
				if (!busyCreate.compareAndSet(false, true)) return;

				Snapshot ownerSnap = getSelectedOwnerSnapshot(tree);
				if (ownerSnap == null || !ownerSnap.canOwnFlowItems) {
					busyCreate.set(false);
					Toolkit.getDefaultToolkit().beep();
					return;
				}

				String nm = safe(createName.getText()).trim();
				if (nm.isBlank()) {
					busyCreate.set(false);
					Toolkit.getDefaultToolkit().beep();
					return;
				}

				debounce.stop();

				try {
					dialog.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
				} catch (Exception ignore) {}

				try {
					IRPModelElement created = createFlowItemNewTerm(ownerSnap.element, nm, createCtx);
					addCreatedElementToTree(tree, model, ownerSnap, created);

					// Ne pas faire result.set(created), sinon la fenetre peut se fermer.
					// result.set(created);

					createName.setText("");
					dialog.setCursor(Cursor.getDefaultCursor());

					busyCreate.set(false);
					btnCreate.setEnabled(true);

					SwingUtilities.invokeLater(createName::requestFocusInWindow);

				} catch (Exception ex) {
					SwingUtilities.invokeLater(() -> {
						JOptionPane.showMessageDialog(
								null,
								"Failed to create Flow Item:\n" + ex.getMessage(),
								"Create Flow Item",
								JOptionPane.ERROR_MESSAGE);

						try {
							dialog.setCursor(Cursor.getDefaultCursor());
						} catch (Exception ignore) {}

						busyCreate.set(false);
						btnCreate.setEnabled(true);
					});
				}
			});
		}

		dialog.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				debounce.stop();
			}

			@Override
			public void windowClosed(WindowEvent e) {
				debounce.stop();
			}
		});

		SwingUtilities.invokeLater(search::requestFocusInWindow);
		dialog.pack();
		centerOnPrimaryScreen(dialog);
		dialog.setVisible(true);

		return result.get();
	}

	private void rebuildTree(DefaultMutableTreeNode root, DefaultTreeModel model, List<Entry> entries) {
		root.removeAllChildren();

		Map<String, DefaultMutableTreeNode> nodes = new HashMap<>();

		for (Entry en : entries) {
			DefaultMutableTreeNode parent = root;

			for (Snapshot owner : en.owners) {
				DefaultMutableTreeNode node = nodes.get(owner.key);

				if (node == null) {
					node = new DefaultMutableTreeNode(owner);
					nodes.put(owner.key, node);
					parent.add(node);
				} else if (node.getParent() == null) {
					parent.add(node);
				}

				parent = node;
			}

			DefaultMutableTreeNode leafNode = nodes.get(en.leaf.key);

			if (leafNode == null) {
				leafNode = new DefaultMutableTreeNode(en.leaf);
				nodes.put(en.leaf.key, leafNode);
				parent.add(leafNode);
			} else if (leafNode.getParent() == null) {
				parent.add(leafNode);
			}
		}

		model.reload(root);
	}

	private Entry getSelectedEntry(JTree tree) {
		TreePath p = tree.getSelectionPath();

		if (p == null) return null;

		DefaultMutableTreeNode n = (DefaultMutableTreeNode) p.getLastPathComponent();
		Object uo = n.getUserObject();

		if (!(uo instanceof Snapshot snap)) return null;
		if (!snap.selectable) return null;

		for (Entry e : allEntries) {
			if (e.leaf.key.equals(snap.key)) {
				return e;
			}
		}

		return null;
	}

	private Snapshot getSelectedOwnerSnapshot(JTree tree) {
		TreePath p = tree.getSelectionPath();

		if (p == null) return null;

		DefaultMutableTreeNode n = (DefaultMutableTreeNode) p.getLastPathComponent();
		Object uo = n.getUserObject();

		return uo instanceof Snapshot s ? s : null;
	}

	private void selectFirstSelectable(JTree tree) {
		Object r = tree.getModel().getRoot();

		if (!(r instanceof DefaultMutableTreeNode root)) return;

		DefaultMutableTreeNode node = findFirstSelectable(root);

		if (node != null) {
			TreePath p = new TreePath(node.getPath());
			tree.setSelectionPath(p);
			tree.scrollPathToVisible(p);
		}
	}

	private DefaultMutableTreeNode findFirstSelectable(DefaultMutableTreeNode node) {
		Object uo = node.getUserObject();

		if (uo instanceof Snapshot s && s.selectable) {
			return node;
		}

		for (int i = 0; i < node.getChildCount(); i++) {
			DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(i);
			DefaultMutableTreeNode found = findFirstSelectable(child);

			if (found != null) {
				return found;
			}
		}

		return null;
	}

	private void expandToDepth(JTree tree, int depth) {
		for (int i = 0; i < tree.getRowCount(); i++) {
			TreePath p = tree.getPathForRow(i);

			if (p != null && p.getPathCount() <= depth + 1) {
				tree.expandPath(p);
			}
		}
	}

	/**
	 * Crée l'élément via le New Term Rhapsody fourni dans le CreateContext.
	 * Cette méthode conserve volontairement l'ancien comportement.
	 */
	private IRPModelElement createFlowItemNewTerm(IRPModelElement owner, String name, CreateContext ctx) {
		if (owner == null) {
			throw new IllegalArgumentException("Owner is null.");
		}

		if (ctx == null) {
			throw new IllegalArgumentException("CreateContext is null.");
		}

		String type = safe(ctx.flowItemUdmcOrStereoName).trim();

		if (type.isBlank()) {
			throw new IllegalArgumentException("flowItemUdmcOrStereoName is empty.");
		}

		String nm = safe(name).trim();

		if (nm.isBlank()) {
			throw new IllegalArgumentException("Name is empty.");
		}

		IRPModelElement created;

		try {
			created = owner.addNewAggr(type, nm);
		} catch (Exception ex) {
			throw new RuntimeException("addNewAggr(\"" + type + "\", \"" + nm + "\") failed: "
					+ ex.getMessage(), ex);
		}

		if (created == null) {
			throw new RuntimeException("addNewAggr returned null.");
		}

		for (IRPStereotype st : ctx.getStereotypesToCopy()) {
			try {
				created.addSpecificStereotype(st);
			} catch (Exception e1) {
				try {
					String meta = safe(created.getMetaClass());
					String stName = safe(st.getName());

					if (!stName.isBlank()) {
						created.addStereotype(stName, meta);
					}
				} catch (Exception ignore) {
					// La copie des stéréotypes ne doit pas annuler la création.
				}
			}
		}

		return created;
	}

	private boolean isAncestorOrSame(IRPModelElement possibleAncestor, IRPModelElement el) {
		if (possibleAncestor == null || el == null) return false;
		if (possibleAncestor.equals(el)) return true;

		IRPModelElement o;

		try {
			o = el.getOwner();
		} catch (Exception e) {
			return false;
		}

		int guard = 0;

		while (o != null && guard++ < 250) {
			if (possibleAncestor.equals(o)) {
				return true;
			}

			try {
				o = o.getOwner();
			} catch (Exception e) {
				return false;
			}
		}

		return false;
	}

	private static final class Snapshot {
		final IRPModelElement element;
		final String key;
		final String name;
		final String stereoPrefix;
		final boolean selectable;
		final boolean canOwnFlowItems;
		final String metaClassLower;
		final String udmcLower;
		final String displayTextLower;

		Snapshot(IRPModelElement element,
				String key,
				String name,
				String stereoPrefix,
				boolean selectable,
				boolean canOwnFlowItems,
				String metaClassLower,
				String udmcLower) {

			this.element = element;
			this.key = key;
			this.name = name;
			this.stereoPrefix = stereoPrefix;
			this.selectable = selectable;
			this.canOwnFlowItems = canOwnFlowItems;
			this.metaClassLower = metaClassLower;
			this.udmcLower = udmcLower;
			this.displayTextLower = (stereoPrefix + name).toLowerCase(Locale.ROOT);
		}

		String displayText() {
			return stereoPrefix + name;
		}
	}

	private static final class Entry {
		final Snapshot leaf;
		final List<Snapshot> owners;
		final String pseudoPathLower;
		final String pseudoPath;

		Entry(Snapshot leaf, List<Snapshot> owners) {
			this.leaf = leaf;
			this.owners = List.copyOf(owners);

			StringBuilder sb = new StringBuilder();

			for (Snapshot s : owners) {
				sb.append(s.name).append("::");
			}

			sb.append(leaf.name);

			this.pseudoPath = sb.toString();
			this.pseudoPathLower = pseudoPath.toLowerCase(Locale.ROOT);
		}

		String pseudoPath() {
			return pseudoPath;
		}

		boolean matches(String[] tokens) {
			if (tokens.length == 0) return true;

			String hay = pseudoPathLower + " " + leaf.displayTextLower;

			for (String t : tokens) {
				if (!hay.contains(t)) {
					return false;
				}
			}

			return true;
		}
	}

	private Snapshot snapshotFor(IRPModelElement el, boolean selectable) {
		String key = safeKey(el);

		Snapshot cached = snapshotByKey.get(key);

		if (cached != null) {
			if (selectable && !cached.selectable) {
				Snapshot upgraded = new Snapshot(
						cached.element,
						cached.key,
						cached.name,
						cached.stereoPrefix,
						true,
						cached.canOwnFlowItems,
						cached.metaClassLower,
						cached.udmcLower);

				snapshotByKey.put(key, upgraded);
				return upgraded;
			}

			return cached;
		}

		String name = safeDisplayName(el);
		String stereoPrefix = buildStereotypePrefix(el);
		boolean canOwn = canOwnFlowItems(el);

		String mc = "";
		String udmc = "";

		try {
			mc = safe(el.getMetaClass()).toLowerCase(Locale.ROOT);
		} catch (Exception ignore) {
			// Métaclasse inconnue.
		}

		try {
			udmc = safe(el.getUserDefinedMetaClass()).toLowerCase(Locale.ROOT);
		} catch (Exception ignore) {
			// UDMC inconnue.
		}

		Snapshot s = new Snapshot(el, key, name, stereoPrefix, selectable, canOwn, mc, udmc);
		snapshotByKey.put(key, s);

		return s;
	}

	private boolean canOwnFlowItems(IRPModelElement el) {
		try {
			String mc = safe(el.getMetaClass());
			String udmc = safe(el.getUserDefinedMetaClass());

			if ("Package".equalsIgnoreCase(mc)) return true;
			if ("Subsystem".equalsIgnoreCase(mc)) return true;
			if ("Flow Item".equalsIgnoreCase(udmc)) return true;

			if (el instanceof IRPClassifier) return true;
			if (el instanceof IRPPackage) return true;
		} catch (Exception ignore) {
			// Si Rhapsody ne répond pas, on considère que le noeud ne peut pas créer.
		}

		return false;
	}

	private IRPModelElement findFlowPackageRootCached(IRPModelElement el) {
		IRPModelElement owner;

		try {
			owner = el.getOwner();
		} catch (Exception e) {
			return null;
		}

		int guard = 0;

		while (owner != null && guard++ < 120) {
			try {
				String mc = safe(owner.getMetaClass());

				if ("Project".equalsIgnoreCase(mc)) {
					return null;
				}

				if ("Package".equalsIgnoreCase(mc)) {
					String pKey = safeKey(owner);
					Boolean isFlow = flowPkgCache.get(pKey);

					if (isFlow == null) {
						String udmc = safe(owner.getUserDefinedMetaClass());
						isFlow = FLOW_PKG_UDMC.equalsIgnoreCase(udmc);
						flowPkgCache.put(pKey, isFlow);
					}

					if (Boolean.TRUE.equals(isFlow)) {
						return owner;
					}
				}

				owner = owner.getOwner();
			} catch (Exception e) {
				return null;
			}
		}

		return null;
	}

	private List<Snapshot> computeOwnerChainWithBrowserGroups(IRPModelElement el,
			Snapshot leafSnap,
			IRPModelElement root) {

		List<Snapshot> chain = new ArrayList<>();

		IRPModelElement owner;

		try {
			owner = el.getOwner();
		} catch (Exception e) {
			return null;
		}

		int guard = 0;

		while (owner != null && guard++ < 250) {
			String mc = "";
			String udmc = "";

			try {
				mc = safe(owner.getMetaClass());

				if ("Project".equalsIgnoreCase(mc)) {
					break;
				}

				udmc = safe(owner.getUserDefinedMetaClass());
			} catch (Exception e) {
				break;
			}

			if (root != null && owner.equals(root)) {
				chain.add(snapshotFor(owner, false));
				break;
			}

			String mcLower = mc.toLowerCase(Locale.ROOT);
			String udmcLower = udmc.toLowerCase(Locale.ROOT);

			boolean isContainer =
					"package".equals(mcLower)
					|| "subsystem".equals(mcLower)
					|| cfg.containerMetaClassLower.contains(mcLower)
					|| cfg.containerUdmcLower.contains(udmcLower);

			if (isContainer) {
				chain.add(snapshotFor(owner, false));
			}

			try {
				owner = owner.getOwner();
			} catch (Exception e) {
				break;
			}
		}

		Collections.reverse(chain);

		if (root != null) {
			if (chain.isEmpty() || chain.get(0).element == null || !chain.get(0).element.equals(root)) {
				return null;
			}

			if (!cfg.includeRootAnchorNode) {
				chain = new ArrayList<>(chain.subList(1, chain.size()));
			}
		}

		if (!cfg.browserGroupByChildUdmcLower.isEmpty()) {
			chain = decorateWithBrowserGroups(chain, leafSnap);
		}

		return chain;
	}

	private List<Snapshot> decorateWithBrowserGroups(List<Snapshot> containers, Snapshot leafSnap) {
		if (containers == null) return null;
		if (containers.isEmpty()) return containers;

		List<Snapshot> out = new ArrayList<>();

		for (int i = 0; i < containers.size(); i++) {
			Snapshot parent = containers.get(i);
			out.add(parent);

			Snapshot child = i + 1 < containers.size() ? containers.get(i + 1) : leafSnap;
			String groupLabel = cfg.browserGroupByChildUdmcLower.get(child.udmcLower);

			if (groupLabel == null || groupLabel.isBlank()) continue;

			if ("package".equals(parent.metaClassLower) || "subsystem".equals(parent.metaClassLower)) {
				continue;
			}

			String gKey = parent.key + "::grp::" + groupLabel.toLowerCase(Locale.ROOT);
			out.add(virtualSnapshot(gKey, groupLabel));
		}

		return out;
	}

	private List<String> splitFullPathTokens(String fullPath) {
		if (fullPath == null) return List.of();

		String[] parts = fullPath.split("::");
		List<String> out = new ArrayList<>();

		for (String p : parts) {
			String t = safe(p).trim();

			if (!t.isBlank()) {
				out.add(t);
			}
		}

		return out;
	}

	private List<String> computeModeLogicalTokens(IRPModelElement el) {
		String fp;

		try {
			fp = el.getFullPathName();
		} catch (Exception e) {
			fp = "";
		}

		List<String> tokens = splitFullPathTokens(fp);

		String rootName = cfg.rootAnchor != null ? safeDisplayName(cfg.rootAnchor) : "";
		int idx = -1;

		for (int i = 0; i < tokens.size(); i++) {
			if (tokens.get(i).equalsIgnoreCase(rootName)) {
				idx = i;
			}
		}

		if (idx >= 0) {
			tokens = tokens.subList(idx + 1, tokens.size());
		}

		List<String> out = new ArrayList<>();

		for (String t : tokens) {
			String tl = t.toLowerCase(Locale.ROOT);

			if (cfg.skipPathTokensLower.contains(tl)) continue;
			if (tl.contains("modechart")) continue;
			if (tl.contains("diagram")) continue;
			if ("states".equals(tl)) continue;

			out.add(t);
		}

		if (out.isEmpty()) {
			out.add(safeDisplayName(el));
		}

		return out;
	}

	private String buildStereotypePrefix(IRPModelElement el) {
		try {
			IRPCollection sts = el.getStereotypes();

			if (sts == null || sts.getCount() == 0) {
				return "";
			}

			@SuppressWarnings("unchecked")
			List<Object> list = sts.toList();

			List<String> names = new ArrayList<>();

			for (Object o : list) {
				if (!(o instanceof IRPStereotype st)) continue;

				try {
					if (st.getIsNewTerm() == 1) continue;
				} catch (Exception ignore) {
					// En cas de doute, on lit le nom du stéréotype.
				}

				String n = safe(st.getName());

				if (n.isBlank()) continue;
				if (HIDE_STEREOTYPE_NAME.equalsIgnoreCase(n)) continue;

				names.add(n);
			}

			if (names.isEmpty()) {
				return "";
			}

			return "«" + String.join(", ", names) + "» ";
		} catch (Exception e) {
			return "";
		}
	}

	private void setLookAndFeel() {
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (Exception ignore) {
			// Le look and feel système est optionnel.
		}
	}

	private static String safeDisplayName(IRPModelElement el) {
		try {
			String display = safe(el.getDisplayName());

			if (!display.isBlank()) {
				return display;
			}
		} catch (Exception ignore) {
			// Fallback sur getName.
		}

		try {
			String nm = safe(el.getName());

			if (!nm.isBlank()) {
				return nm;
			}
		} catch (Exception ignore) {
			// Fallback final.
		}

		return "<unnamed>";
	}

	private static String safeKey(IRPModelElement el) {
		if (el == null) return "null";

		try {
			String g = el.getGUID();

			if (g != null && !g.isBlank()) {
				return g;
			}
		} catch (Exception ignore) {
			// Fallback sur le chemin complet.
		}

		try {
			String fp = el.getFullPathName();

			if (fp != null && !fp.isBlank()) {
				return "fp:" + fp;
			}
		} catch (Exception ignore) {
			// Fallback sur métaclasse + nom.
		}

		String mc = "";
		String n = "";

		try {
			mc = safe(el.getMetaClass());
		} catch (Exception ignore) {
			// Métaclasse inconnue.
		}

		try {
			n = safe(el.getName());
		} catch (Exception ignore) {
			// Nom inconnu.
		}

		return "mc:" + mc + ":n:" + n;
	}

	private static String safe(String s) {
		return s == null ? "" : s;
	}

	private Snapshot virtualSnapshot(String key, String label) {
		Snapshot cached = snapshotByKey.get(key);

		if (cached != null) {
			return cached;
		}

		Snapshot s = new Snapshot(
				null,
				key,
				label,
				"",
				false,
				false,
				"virtual",
				"");

		snapshotByKey.put(key, s);

		return s;
	}
	/**
	 * Centre la fenêtre sur l'ecran principal.
	 */
	private static void centerOnPrimaryScreen(JDialog dialog) {
		Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
		Dimension size = dialog.getSize();

		int x = Math.max(0, (screen.width - size.width) / 2);
		int y = Math.max(0, (screen.height - size.height) / 2);

		dialog.setLocation(x, y);
	}
	
	private static class SafeTreeRenderer extends DefaultTreeCellRenderer {
		private static final long serialVersionUID = 1L;

		private final Icon leafIcon;
		private final Icon rootIcon;
		private final String rootKey;

		SafeTreeRenderer(Icon leafIcon, Icon rootIcon, String rootKey) {
			this.leafIcon = leafIcon;
			this.rootIcon = rootIcon;
			this.rootKey = rootKey;
		}

		@Override
		public Component getTreeCellRendererComponent(JTree tree,
				Object value,
				boolean sel,
				boolean expanded,
				boolean leaf,
				int row,
				boolean hasFocus) {

			super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

			if (value instanceof DefaultMutableTreeNode n) {
				Object uo = n.getUserObject();

				if (uo instanceof Snapshot snap) {
					setText(snap.displayText());

					if (rootIcon != null && rootKey != null && rootKey.equals(snap.key)) {
						setIcon(rootIcon);
					} else if (snap.selectable && leafIcon != null) {
						setIcon(leafIcon);
					}
				}
			}

			return this;
		}
	}

/**
 * Ajoute l'element cree dans l'arbre visible sans fermer la fenetre.
 */
private void addCreatedElementToTree(JTree tree,
		DefaultTreeModel model,
		Snapshot ownerSnap,
		IRPModelElement created) {

	if (tree == null || model == null || ownerSnap == null || created == null) return;

	DefaultMutableTreeNode root = (DefaultMutableTreeNode) model.getRoot();
	DefaultMutableTreeNode ownerNode = findNodeBySnapshotKey(root, ownerSnap.key);

	if (ownerNode == null) return;

	Snapshot createdSnap = snapshotFor(created, true);
	DefaultMutableTreeNode createdNode = new DefaultMutableTreeNode(createdSnap);

	ownerNode.add(createdNode);
	model.reload(ownerNode);

	TreePath path = new TreePath(createdNode.getPath());
	tree.expandPath(new TreePath(ownerNode.getPath()));
	tree.setSelectionPath(path);
	tree.scrollPathToVisible(path);
}

/**
 * Recherche un noeud par cle Snapshot.
 */
private DefaultMutableTreeNode findNodeBySnapshotKey(DefaultMutableTreeNode node, String key) {
	if (node == null || key == null) return null;

	Object uo = node.getUserObject();

	if (uo instanceof Snapshot snap && key.equals(snap.key)) {
		return node;
	}

	for (int i = 0; i < node.getChildCount(); i++) {
		DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(i);
		DefaultMutableTreeNode found = findNodeBySnapshotKey(child, key);

		if (found != null) return found;
	}

	return null;
}
}