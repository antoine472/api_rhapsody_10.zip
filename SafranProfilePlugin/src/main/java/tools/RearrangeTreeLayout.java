package tools;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.telelogic.rhapsody.core.IRPApplication;
import com.telelogic.rhapsody.core.IRPDependency;
import com.telelogic.rhapsody.core.IRPDiagram;
import com.telelogic.rhapsody.core.IRPGraphEdge;
import com.telelogic.rhapsody.core.IRPGraphElement;
import com.telelogic.rhapsody.core.IRPGraphNode;
import com.telelogic.rhapsody.core.IRPModelElement;

import main.constants.RhpMetaClassConstants;
import main.gui.tools.Toast;
import main.gui.tools.TreeLayoutOrientationDialog;
import utils.BlockTextFit;
import utils.TreeDiagramLayout;
import utils.TreeDiagramLayout.Box;
import utils.TreeDiagramLayout.Node;
import utils.TreeDiagramLayout.Orientation;
import utils.TreeDiagramLayout.Spacing;

/**
 * Rearrange Tree Layout : reorganise les descendants de l'element selectionne
 * dans un diagramme d'arbre (Logical, Functional ou Technical Breakdown
 * Structure), verticalement ou horizontalement.
 *
 * <p><b>Comportement</b> :</p>
 * <ul>
 *   <li>l'element selectionne garde sa position, ainsi que tout ce qui n'est pas
 *       dans son sous-arbre (son parent, ses freres, les autres arbres) ;</li>
 *   <li>Vertical : les descendants sont empiles en arbre indente, comme les
 *       diagrammes produits par Generate LBS / FBS / TBS ;</li>
 *   <li>Horizontal : les descendants sont disposes en organigramme, chaque
 *       rangee d'enfants etant centree sous son parent ;</li>
 *   <li>les liens du sous-arbre sont redessines (proprietes graphiques
 *       SourcePosition, TargetPosition et Polygon) ;</li>
 *   <li>l'utilisateur choisit aussi le nombre de niveaux reorganises
 *       ("*" pour tous, comme dans Generate LBS) ; au-dela, chaque sous-arbre
 *       garde sa disposition et se deplace d'un bloc avec son ancetre, ses
 *       liens etant decales du meme vecteur ;</li>
 *   <li>les espacements (horizontal et vertical) sont reglables dans la
 *       boite de dialogue, et memorises par orientation pendant la session ;</li>
 *   <li>en option (case a cocher), la largeur des blocs reorganises est
 *       ajustee a leur nom affiche, pour qu'il tienne sur une ligne ; sinon
 *       la taille des blocs n'est pas modifiee ;</li>
 *   <li>les freres sont ranges dans l'ordre du modele
 *       (getNestedElementsByMetaClass), comme dans Generate LBS.</li>
 * </ul>
 *
 * <p>On peut combiner les orientations : appliquer Horizontal sur la racine,
 * puis Vertical sur un de ses enfants, qui reste en place pendant que ses
 * propres descendants passent en arbre indente. Reappliquer une orientation
 * sur la racine remet tout son sous-arbre dans cette orientation.</p>
 *
 * <p>La commande est annulable : le plugin l'execute dans une transaction,
 * un Ctrl+Z annule l'ensemble des deplacements.</p>
 *
 * <p><b>Limite</b> : les blocs situes hors du sous-arbre ne sont pas deplaces ;
 * si le sous-arbre reorganise les chevauche, un avertissement est ecrit dans
 * le log.</p>
 */
public class RearrangeTreeLayout extends RhapsodyTool {

	public static final String COMMAND = "Safran Toolkit...\\Rearrange Tree Layout";

	/**
	 * Nom du bouton de la barre d'outils Rhapsody (entree name54 du .hep). Rhapsody
	 * transmet ce nom au plugin quand on clique sur le bouton : il est donc
	 * enregistre comme alias de la meme commande dans SafranProfilePlugin.
	 */
	public static final String TOOLBAR_COMMAND = "Rearrange Tree Layout";

	/** Derniers choix, preselectionnes a l'ouverture suivante (session Rhapsody). */
	private static Orientation lastOrientation = Orientation.VERTICAL;
	private static int lastDepth = TreeDiagramLayout.ALL_LEVELS;
	/** Derniers espacements, par orientation (mis a jour par la boite de dialogue sur Apply). */
	private static final Map<Orientation, Spacing> lastSpacings = new EnumMap<>(Orientation.class);
	/** Dernier etat de la case "Fit width to the displayed name". */
	private static boolean lastFitWidth = false;

	/** Type graphique d'un lien de composition : sa source est l'enfant. */
	private static final String CONTAIN_ARROW = "ContainArrow";

	public RearrangeTreeLayout(IRPApplication rpyApp) {
		super(rpyApp);
	}

	/**
	 * Un lien du diagramme entre deux blocs de l'arbre.
	 *
	 * @param sourceIsChild vrai si la source du lien Rhapsody est l'enfant ;
	 *                      le Polygon doit alors aller de l'enfant au parent
	 */
	private record TreeLink(IRPGraphEdge edge, Node parent, Node child, boolean sourceIsChild) {}

	@Override
	public void execute() {
		rhpLog.info("Start - " + COMMAND);

		// ------------------------------------------------------------------
		// 1) Element selectionne et diagramme
		// ------------------------------------------------------------------
		IRPModelElement selected = rhApp.getSelectedElement();
		if (selected == null) {
			Toast.showToast("Select an element in the breakdown diagram.", 3500);
			return;
		}

		IRPDiagram diagram = findDiagram(selected);
		if (diagram == null) {
			Toast.showToast("No diagram found. Open the breakdown diagram and select the element in it.", 4000);
			return;
		}
		rhpLog.info("Selected: " + selected.getName() + " | diagram: " + diagram.getName());

		// ------------------------------------------------------------------
		// 2) Lecture des blocs et des liens, construction de l'arbre
		// ------------------------------------------------------------------
		@SuppressWarnings("unchecked")
		List<IRPGraphElement> elements = diagram.getGraphicalElements().toList();

		Map<String, IRPGraphNode> graphNodes = new LinkedHashMap<>();
		Map<String, Node> nodes = collectNodes(elements, graphNodes);
		List<TreeLink> links = collectLinks(elements, nodes, graphNodes);

		Node start = nodes.get(selected.getGUID());
		if (start == null) {
			Toast.showToast("The selected element is not drawn in diagram " + diagram.getName() + ".", 4000);
			return;
		}
		if (start.children.isEmpty()) {
			Toast.showToast(selected.getName() + " has no child in this diagram.", 3500);
			return;
		}

		// ------------------------------------------------------------------
		// 3) Choix de l'orientation et du nombre de niveaux
		// ------------------------------------------------------------------
		TreeLayoutOrientationDialog.Choice choice = askChoice(selected.getName(),
				TreeDiagramLayout.subtreeDepth(start));
		if (choice == null) {
			rhpLog.info("Cancelled by user.");
			return;
		}
		Orientation orientation = choice.orientation();
		int depth = choice.depth();
		Spacing spacing = choice.spacing();

		// ------------------------------------------------------------------
		// 4) Calcul : ordre du modele, largeur des blocs, puis mise en page
		// ------------------------------------------------------------------
		sortSubtreeByModelOrder(start, graphNodes, new HashSet<>());

		// Largeur ajustee au nom AVANT la mise en page : les positions, l'epine
		// et les bandes sont calculees avec les nouvelles largeurs
		int resized = choice.fitWidth() ? fitWidthsToNames(start, depth, graphNodes) : 0;

		List<Node> placed = TreeDiagramLayout.layout(start, orientation, depth, spacing);
		Set<Node> subtree = new HashSet<>(placed);
		warnOverlaps(nodes, start, subtree);

		// Trace actuel des liens qui suivront leur bloc : lu AVANT de deplacer
		// les blocs, car Rhapsody peut recalculer un lien quand un bloc bouge
		Map<TreeLink, List<int[]>> followingLinks = new IdentityHashMap<>();
		for (TreeLink link : links) {
			if (!subtree.contains(link.child()) || TreeDiagramLayout.isArranged(link.child(), depth)) continue;
			List<int[]> points = TreeDiagramLayout.parsePolygon(readProperty(link.edge(), "Polygon"));
			if (points != null) followingLinks.put(link, points);
		}

		// ------------------------------------------------------------------
		// 5) Ecriture : blocs d'abord, puis liens du sous-arbre
		// ------------------------------------------------------------------
		int movedCount = 0;
		for (Node n : placed) {
			if (!n.moved()) continue;
			IRPGraphNode gn = graphNodes.get(n.key);
			// Largeur d'abord (si ajustee), position ensuite : la position ecrite
			// en dernier fixe le coin haut gauche, quelle que soit la facon dont
			// Rhapsody applique le changement de largeur
			if (n.box.w() != n.original.w()) {
				gn.setGraphicalProperty("Width", String.valueOf(n.box.w()));
			}
			gn.setGraphicalProperty("Position", n.box.x() + "," + n.box.y());
			movedCount++;
		}

		int redrawn = 0;
		int shifted = 0;
		for (TreeLink link : links) {
			// Seuls les liens vers un enfant du sous-arbre changent ; le lien
			// entre l'element selectionne et son propre parent ne bouge pas
			if (!subtree.contains(link.child())) continue;

			if (TreeDiagramLayout.isArranged(link.child(), depth)) {
				redrawLink(link, orientation);
				redrawn++;
			} else if (followingLinks.containsKey(link)) {
				// Lien sous le dernier niveau reorganise : meme forme, decale
				// du meme vecteur que les deux blocs qu'il relie
				shiftLink(link, followingLinks.get(link));
				shifted++;
			}
		}

		rhpLog.info("End - " + COMMAND + " (" + orientation + ", depth "
				+ (depth == TreeDiagramLayout.ALL_LEVELS ? "*" : String.valueOf(depth))
				+ ", spacing " + spacing.horizontal() + "/" + spacing.vertical()
				+ (choice.fitWidth() ? ", fit width" : "") + "): "
				+ movedCount + " block(s) moved, " + redrawn + " link(s) redrawn, "
				+ shifted + " link(s) shifted"
				+ (choice.fitWidth() ? ", " + resized + " block(s) resized." : "."));
	}

	// ======================================================================
	// Lecture du diagramme
	// ======================================================================

	/**
	 * Collecte les blocs representant un element de modele (le cadre du
	 * diagramme n'en a pas et est ignore). Cle = GUID de l'element de modele.
	 * Si un element est dessine deux fois, seul le premier bloc est retenu.
	 */
	private Map<String, Node> collectNodes(List<IRPGraphElement> elements, Map<String, IRPGraphNode> graphNodes) {
		Map<String, Node> nodes = new LinkedHashMap<>();
		for (IRPGraphElement ge : elements) {
			if (!(ge instanceof IRPGraphNode)) continue;
			IRPModelElement mo = ge.getModelObject();
			Box box = readBox(ge);
			if (mo == null || box == null) continue;

			String key = mo.getGUID();
			if (nodes.containsKey(key)) {
				rhpLog.warn("Element drawn twice, second block ignored: " + mo.getName());
				continue;
			}
			nodes.put(key, new Node(key, box));
			graphNodes.put(key, (IRPGraphNode) ge);
		}
		return nodes;
	}

	/**
	 * Collecte les liens de l'arbre et relie parents et enfants.
	 * <p>
	 * Si le diagramme contient des liens de composition (Type graphique
	 * ContainArrow), seuls ceux-ci forment l'arbre : les autres liens
	 * (associations, dependances...) ne sont ni utilises ni redessines.
	 * Sinon, tous les liens entre deux blocs sont utilises.
	 * </p>
	 * <p>Le parent est determine dans cet ordre :</p>
	 * <ol>
	 *   <li>Type graphique ContainArrow : la source est l'enfant ;</li>
	 *   <li>sinon l'appartenance dans le modele (getOwner) ;</li>
	 *   <li>en dernier recours la geometrie : l'enfant est le bloc le plus bas.</li>
	 * </ol>
	 * <p>Un enfant n'a qu'un parent, et un lien qui fermerait une boucle est ignore.</p>
	 */
	private List<TreeLink> collectLinks(List<IRPGraphElement> elements, Map<String, Node> nodes,
			Map<String, IRPGraphNode> graphNodes) {

		// Liens entre deux blocs connus, en notant ceux qui sont des compositions
		List<IRPGraphEdge> edges = new ArrayList<>();
		List<Boolean> containment = new ArrayList<>();
		boolean anyContainment = false;
		for (IRPGraphElement ge : elements) {
			if (!(ge instanceof IRPGraphEdge)) continue;
			IRPGraphEdge edge = (IRPGraphEdge) ge;
			Node source = nodeOf(edge.getSource(), nodes);
			Node target = nodeOf(edge.getTarget(), nodes);
			if (source == null || target == null || source == target) continue;

			boolean isContain = isContainArrow(edge);
			anyContainment |= isContain;
			edges.add(edge);
			containment.add(isContain);
		}

		List<TreeLink> links = new ArrayList<>();
		for (int i = 0; i < edges.size(); i++) {
			IRPGraphEdge edge = edges.get(i);
			if (anyContainment && !containment.get(i)) {
				rhpLog.debug("Not a containment link, left as is: "
						+ edge.getSource().getModelObject().getName() + " -> "
						+ edge.getTarget().getModelObject().getName());
				continue;
			}

			Node source = nodeOf(edge.getSource(), nodes);
			Node target = nodeOf(edge.getTarget(), nodes);
			boolean sourceIsChild = containment.get(i) || sourceIsChild(source, target, graphNodes);
			Node parent = sourceIsChild ? target : source;
			Node child  = sourceIsChild ? source : target;

			if (child.parent != null && child.parent != parent) {
				rhpLog.warn("Block already has a parent, extra link ignored: " + child.key);
				continue;
			}
			if (child.parent == null) {
				if (isAncestor(child, parent)) {
					rhpLog.warn("Link would create a loop, ignored: " + child.key + " -> " + parent.key);
					continue;
				}
				parent.addChild(child);
			}
			links.add(new TreeLink(edge, parent, child, sourceIsChild));
		}
		return links;
	}

	/** Vrai si le lien a le type graphique ContainArrow (composition). */
	private static boolean isContainArrow(IRPGraphEdge edge) {
		try {
			return CONTAIN_ARROW.equals(edge.getGraphicalProperty("Type").getValue());
		} catch (Exception e) {
			return false; // propriete absente pour ce type de lien
		}
	}

	/** Vrai si {@code candidate} est {@code node} ou l'un de ses ancetres. */
	private static boolean isAncestor(Node candidate, Node node) {
		Set<Node> seen = new HashSet<>();
		for (Node n = node; n != null && seen.add(n); n = n.parent) {
			if (n == candidate) return true;
		}
		return false;
	}

	/**
	 * Pour un lien qui n'est pas une composition : determine si la source est
	 * l'enfant, par l'appartenance dans le modele, sinon par la geometrie.
	 */
	private boolean sourceIsChild(Node source, Node target, Map<String, IRPGraphNode> graphNodes) {
		// Appartenance dans le modele
		try {
			IRPModelElement sourceOwner = graphNodes.get(source.key).getModelObject().getOwner();
			IRPModelElement targetOwner = graphNodes.get(target.key).getModelObject().getOwner();
			if (sourceOwner != null && target.key.equals(sourceOwner.getGUID())) return true;
			if (targetOwner != null && source.key.equals(targetOwner.getGUID())) return false;
		} catch (Exception ignore) {
			// proprietaire illisible : critere suivant
		}

		// Geometrie
		return source.box.y() > target.box.y();
	}

	/** Bloc connu correspondant a une extremite de lien, ou null. */
	private static Node nodeOf(IRPGraphElement ge, Map<String, Node> nodes) {
		if (!(ge instanceof IRPGraphNode)) return null;
		IRPModelElement mo = ge.getModelObject();
		return mo == null ? null : nodes.get(mo.getGUID());
	}

	/** Lit Position, Width et Height d'un bloc ; null si une valeur est illisible. */
	private Box readBox(IRPGraphElement ge) {
		try {
			String[] pos = ge.getGraphicalProperty("Position").getValue().split(",");
			int w = Integer.parseInt(ge.getGraphicalProperty("Width").getValue().trim());
			int h = Integer.parseInt(ge.getGraphicalProperty("Height").getValue().trim());
			return new Box(Integer.parseInt(pos[0].trim()), Integer.parseInt(pos[1].trim()), w, h);
		} catch (Exception e) {
			rhpLog.debug("Unreadable block geometry: " + e.getMessage());
			return null;
		}
	}

	// ======================================================================
	// Calcul
	// ======================================================================

	/**
	 * Trie les enfants de chaque bloc du sous-arbre dans l'ordre du modele
	 * (getNestedElementsByMetaClass, comme Generate LBS). Un enfant absent de
	 * cette liste passe a la fin, dans son ordre vertical actuel.
	 */
	private void sortSubtreeByModelOrder(Node parent, Map<String, IRPGraphNode> graphNodes, Set<Node> visited) {
		if (!visited.add(parent)) return;

		Map<String, Integer> rank = new HashMap<>();
		try {
			@SuppressWarnings("unchecked")
			List<IRPModelElement> nested = graphNodes.get(parent.key).getModelObject()
					.getNestedElementsByMetaClass(RhpMetaClassConstants.CLASS, 0).toList();
			for (int i = 0; i < nested.size(); i++) rank.put(nested.get(i).getGUID(), i);
		} catch (Exception e) {
			rhpLog.debug("No model order for " + parent.key + ": " + e.getMessage());
		}

		parent.children.sort(Comparator
				.comparingInt((Node n) -> rank.getOrDefault(n.key, Integer.MAX_VALUE))
				.thenComparingInt(n -> n.original.y())
				.thenComparingInt(n -> n.original.x()));

		for (Node child : parent.children) sortSubtreeByModelOrder(child, graphNodes, visited);
	}

	/**
	 * Ajuste la largeur des blocs reorganises a leur nom affiche (niveaux 1 a
	 * maxDepth sous start). Le bloc selectionne et les blocs plus profonds, qui
	 * suivent leur ancetre sans changer de forme, gardent leur taille. La hauteur
	 * n'est jamais modifiee.
	 *
	 * @return le nombre de blocs dont la largeur change
	 */
	private int fitWidthsToNames(Node start, int maxDepth, Map<String, IRPGraphNode> graphNodes) {
		int resized = 0;
		Set<Node> seen = new HashSet<>();
		seen.add(start);
		List<Node> level = new ArrayList<>(start.children);
		for (int depth = 1; !level.isEmpty()
				&& (maxDepth == TreeDiagramLayout.ALL_LEVELS || depth <= maxDepth); depth++) {
			List<Node> next = new ArrayList<>();
			for (Node n : level) {
				if (!seen.add(n)) continue; // protection contre un cycle
				IRPGraphNode gn = graphNodes.get(n.key);
				String text = displayedName(gn.getModelObject());
				BlockTextFit.TextStyle style = readTextStyle(gn);
				int width = BlockTextFit.fittedWidth(text, style);
				if (width != n.box.w()) {
					n.box = new Box(n.box.x(), n.box.y(), width, n.box.h());
					resized++;
				}
				rhpLog.debug("Fit width: '" + text + "' -> " + width + " (" + style.fontName() + " "
						+ style.sizePt() + (style.bold() ? " bold" : "") + ")");
				next.addAll(n.children);
			}
			level = next;
		}
		return resized;
	}

	/** Texte affiche dans le bloc : le libelle (label) s'il existe, sinon le nom. */
	private static String displayedName(IRPModelElement element) {
		if (element == null) return "";
		String label = null;
		try { label = element.getDisplayName(); } catch (Exception ignore) { /* nom par defaut */ }
		return (label != null && !label.isBlank()) ? label : element.getName();
	}

	/**
	 * Police du nom d'un bloc, lue dans les proprietes de format du profil :
	 * Format.&lt;Metaclasse&gt;.Font.Font, .Font.Size et
	 * .Font.Weight@Child.NameCompartment@Name (700 = gras). La metaclasse est
	 * celle du new term sans espaces (ex. "LogicalSystem"), ou suivie de "_"
	 * comme "Function_", puis la metaclasse Rhapsody (ex. "Class").
	 * A defaut : Arial 12 gras, valeur du profil Safran pour les blocs.
	 */
	private BlockTextFit.TextStyle readTextStyle(IRPGraphNode gn) {
		IRPModelElement mo = gn.getModelObject();
		List<String> keys = new ArrayList<>();
		try {
			keys = BlockTextFit.formatMetaclassKeys(mo.getUserDefinedMetaClass(), mo.getMetaClass());
		} catch (Exception ignore) {
			// metaclasse illisible : valeurs par defaut
		}

		for (String key : keys) {
			String font = readFormat(gn, mo, "Format." + key + ".Font.Font");
			if (font == null || font.isBlank()) continue;
			int size = parseIntOr(readFormat(gn, mo, "Format." + key + ".Font.Size"),
					BlockTextFit.TextStyle.DEFAULT.sizePt());
			String weight = readFormat(gn, mo, "Format." + key + ".Font.Weight@Child.NameCompartment@Name");
			boolean bold = (weight == null || weight.isBlank())
					? BlockTextFit.TextStyle.DEFAULT.bold()
					: parseIntOr(weight, 400) >= 700;
			return new BlockTextFit.TextStyle(font, size, bold);
		}
		return BlockTextFit.TextStyle.DEFAULT;
	}

	/**
	 * Valeur d'une propriete de format : d'abord sur l'element graphique (qui
	 * porte un eventuel format local), sinon sur l'element de modele.
	 * Null si la propriete n'existe pas.
	 */
	private static String readFormat(IRPGraphNode gn, IRPModelElement mo, String key) {
		try {
			String v = gn.getPropertyValue(key);
			if (v != null && !v.isBlank()) return v;
		} catch (Exception ignore) {
			// propriete absente sur l'element graphique
		}
		try {
			return mo.getPropertyValue(key);
		} catch (Exception e) {
			return null;
		}
	}

	private static int parseIntOr(String value, int fallback) {
		try {
			return Integer.parseInt(value.trim());
		} catch (Exception e) {
			return fallback;
		}
	}

	/** Ecrit dans le log les blocs hors sous-arbre que la nouvelle mise en page chevauche. */
	private void warnOverlaps(Map<String, Node> nodes, Node start, Set<Node> subtree) {
		for (Node inside : subtree) {
			for (Node outside : nodes.values()) {
				if (outside == start || subtree.contains(outside)) continue;
				if (intersects(inside.box, outside.box)) {
					rhpLog.warn("Rearranged block overlaps a block outside the subtree: "
							+ inside.key + " / " + outside.key);
				}
			}
		}
	}

	/** Vrai si deux rectangles se chevauchent. */
	private static boolean intersects(Box a, Box b) {
		return a.x() < b.right() && b.x() < a.right() && a.y() < b.bottom() && b.y() < a.bottom();
	}

	// ======================================================================
	// Ecriture des liens
	// ======================================================================

	/**
	 * Redessine un lien : extremites puis trace complet (Polygon). Le Polygon
	 * va de la source a la cible du lien ; les points sont calcules de l'enfant
	 * vers le parent, puis inverses si la source est le parent.
	 */
	private void redrawLink(TreeLink link, Orientation orientation) {
		List<int[]> points = TreeDiagramLayout.linkPointsFromChild(link.child(), orientation);
		if (!link.sourceIsChild()) points = TreeDiagramLayout.reversed(points);

		String polygon = TreeDiagramLayout.toPolygon(points);
		link.edge().setGraphicalProperty("SourcePosition", TreeDiagramLayout.toPoint(points.get(0)));
		link.edge().setGraphicalProperty("TargetPosition", TreeDiagramLayout.toPoint(points.get(points.size() - 1)));
		link.edge().setGraphicalProperty("Polygon", polygon);
		rhpLog.debug("Link to " + link.child().key + " -> " + polygon);
	}

	/**
	 * Decale un lien qui suit son bloc : son trace d'origine est translate
	 * du deplacement de l'enfant (identique a celui du parent, deplace en bloc).
	 */
	private void shiftLink(TreeLink link, List<int[]> originalPoints) {
		List<int[]> points = TreeDiagramLayout.translated(originalPoints,
				link.child().dx(), link.child().dy());
		link.edge().setGraphicalProperty("SourcePosition", TreeDiagramLayout.toPoint(points.get(0)));
		link.edge().setGraphicalProperty("TargetPosition", TreeDiagramLayout.toPoint(points.get(points.size() - 1)));
		link.edge().setGraphicalProperty("Polygon", TreeDiagramLayout.toPolygon(points));
	}

	/** Valeur d'une propriete graphique, ou null si elle est illisible. */
	private static String readProperty(IRPGraphElement ge, String name) {
		try {
			return ge.getGraphicalProperty(name).getValue();
		} catch (Exception e) {
			return null;
		}
	}

	// ======================================================================
	// Interface et recherche du diagramme
	// ======================================================================

	/**
	 * Demande l'orientation, le nombre de niveaux et les espacements dans la boite du plugin,
	 * affichee sur l'ecran de Rhapsody et au premier plan. Les derniers choix
	 * sont preselectionnes.
	 *
	 * @param maxDepth nombre de niveaux sous l'element selectionne
	 * @return le choix, ou null si l'utilisateur annule
	 */
	private TreeLayoutOrientationDialog.Choice askChoice(String elementName, int maxDepth) {
		TreeLayoutOrientationDialog.Choice choice =
				TreeLayoutOrientationDialog.ask(elementName, lastOrientation, maxDepth, lastDepth,
						lastSpacings, lastFitWidth);
		if (choice != null) {
			lastOrientation = choice.orientation();
			lastDepth = choice.depth();
			lastFitWidth = choice.fitWidth();
		}
		return choice;
	}

	/**
	 * Diagramme ou se trouve l'element : celui de la selection graphique en
	 * priorite, sinon un diagramme rattache a l'element ou a un de ses
	 * proprietaires par une dependance (cas des diagrammes Generate LBS / FBS / TBS).
	 */
	private IRPDiagram findDiagram(IRPModelElement element) {
		try {
			IRPDiagram active = rhApp.getDiagramOfSelectedElement();
			if (active != null) return active;
		} catch (Exception ignore) {
			// pas de selection graphique : recherche par dependance
		}

		IRPModelElement current = element;
		while (current != null) {
			@SuppressWarnings("unchecked")
			List<IRPModelElement> refs = current.getReferences().toList();
			for (IRPModelElement ref : refs) {
				if (ref instanceof IRPDependency) {
					IRPModelElement dependent = ((IRPDependency) ref).getDependent();
					if (dependent instanceof IRPDiagram) return (IRPDiagram) dependent;
				}
			}
			current = current.getOwner();
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

	@Override
	public boolean isInteractive() {
		return true;
	}
}
