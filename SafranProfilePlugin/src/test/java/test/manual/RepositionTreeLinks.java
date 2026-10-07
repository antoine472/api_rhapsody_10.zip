package test.manual;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.telelogic.rhapsody.core.IRPApplication;
import com.telelogic.rhapsody.core.IRPDiagram;
import com.telelogic.rhapsody.core.IRPGraphEdge;
import com.telelogic.rhapsody.core.IRPGraphElement;
import com.telelogic.rhapsody.core.IRPGraphNode;
import com.telelogic.rhapsody.core.IRPModelElement;
import com.telelogic.rhapsody.core.RhapsodyAppServer;

/**
 * Programme de test : redessine les liens d'un diagramme d'arbre (LBS, FBS...)
 * et, en option, replace d'abord les blocs en arbre indente.
 *
 * <p><b>Ce que les diagnostics ont appris</b> :</p>
 * <ul>
 *   <li>un lien de composition a la propriete graphique Type = ContainArrow ;
 *       sa source est l'ENFANT et sa cible le PARENT ;</li>
 *   <li>le trace est dans la propriete Polygon, format "n,x1,y1,x2,y2,..." ;
 *       Rhapsody accepte qu'on l'ecrive (a 1 pixel pres : il colle
 *       l'extremite sur la bordure du bloc) ;</li>
 *   <li>la propriete "Points" n'existe pas sur un lien.</li>
 * </ul>
 *
 * <p><b>Trois styles de trace</b> :</p>
 * <ul>
 *   <li>I (defaut) : arbre INDENTE des FBS/LBS. Memes ancrages que
 *       utils.D2Rectangle : milieu du bord gauche de l'enfant, coude sur une
 *       "epine" verticale a x + largeur/8 du parent, bas du parent ;</li>
 *   <li>V : peigne de haut en bas (ligne horizontale commune aux freres) ;</li>
 *   <li>H : peigne de gauche a droite.</li>
 * </ul>
 *
 * <p><b>Arguments</b> (dans n'importe quel ordre) :</p>
 * <ul>
 *   <li>I, V ou H : style de trace ;</li>
 *   <li>LAYOUT : replace d'abord les blocs en arbre indente (style I seulement),
 *       a l'identique de GenerateLBS : blocs de 300 x 100, decalage de 100 par
 *       niveau, 20 d'ecart vertical, freres dans l'ordre du modele
 *       (getNestedElementsByMetaClass). Si l'element selectionne a des enfants
 *       dans le diagramme, seul son sous-arbre est replace et il garde sa
 *       position ; sinon tous les arbres du diagramme sont replaces ;</li>
 *   <li>DRY : affiche les calculs sans rien modifier.</li>
 * </ul>
 * <p>Exemple : "I LAYOUT DRY". Toutes les modifications sont dans UNE
 * transaction : un seul Ctrl+Z dans Rhapsody les annule.</p>
 *
 * <p><b>Utilisation</b> : ouvrir le diagramme, cliquer sur un bloc DANS le
 * diagramme, puis Run As, Java Application.</p>
 */
public class RepositionTreeLinks {

	/** Style de trace des liens. */
	private enum Style { INDENTED, VERTICAL, HORIZONTAL }

	/** Decalage horizontal d'un enfant par rapport a son parent (comme GenerateLBS). */
	private static final int INDENT = 100;

	/** Ecart vertical entre deux blocs empiles (comme GenerateLBS). */
	private static final int V_GAP = 20;

	/** Taille standard d'un bloc apres LAYOUT (LOGICAL_WIDTH / LOGICAL_HEIGHT de GenerateLBS). */
	private static final int NODE_WIDTH = 300;
	private static final int NODE_HEIGHT = 100;

	/** Rectangle d'un bloc dans le diagramme (coordonnees du diagramme). */
	private record Box(int x, int y, int w, int h) {
		int centerX() { return x + w / 2; }
		int centerY() { return y + h / 2; }
		int bottom()  { return y + h; }
		int right()   { return x + w; }
	}

	/**
	 * Un bloc du diagramme. Le rectangle est modifiable : la mise en page
	 * calcule d'abord les nouvelles positions en memoire, puis les applique.
	 */
	private static final class Node {
		final IRPGraphNode graph;
		final String key;
		final String label;
		final Box original;
		Box box;
		Node parent;
		final List<Node> children = new ArrayList<>();

		Node(IRPGraphNode graph, String key, String label, Box box) {
			this.graph = graph;
			this.key = key;
			this.label = label;
			this.original = box;
			this.box = box;
		}
	}

	/** Un lien entre deux blocs, avec son parent et son enfant identifies. */
	private record TreeLink(IRPGraphEdge edge, Node parent, Node child, boolean sourceIsChild) {}

	public static void main(String[] args) {
		// ------------------------------------------------------------------
		// Lecture des arguments
		// ------------------------------------------------------------------
		Style style = Style.INDENTED;
		boolean dryRun = false;
		boolean layout = false;
		for (String a : args) {
			if ("I".equalsIgnoreCase(a)) style = Style.INDENTED;
			if ("V".equalsIgnoreCase(a)) style = Style.VERTICAL;
			if ("H".equalsIgnoreCase(a)) style = Style.HORIZONTAL;
			if ("DRY".equalsIgnoreCase(a)) dryRun = true;
			if ("LAYOUT".equalsIgnoreCase(a)) layout = true;
		}
		if (layout && style != Style.INDENTED) {
			System.out.println("LAYOUT n'est disponible qu'avec le style I : option ignoree.");
			layout = false;
		}
		boolean horizontal = (style == Style.HORIZONTAL);
		System.out.println("Style : " + style
				+ (layout ? " | LAYOUT" : "")
				+ (dryRun ? " | DRY (aucune modification)" : ""));

		// ------------------------------------------------------------------
		// 1) Connexion au Rhapsody ouvert et recuperation du diagramme
		// ------------------------------------------------------------------
		IRPApplication app = RhapsodyAppServer.getActiveRhapsodyApplication();
		if (app == null) {
			System.out.println("Aucun Rhapsody ouvert.");
			return;
		}
		IRPDiagram diagram = app.getDiagramOfSelectedElement();
		if (diagram == null) {
			System.out.println("Aucun diagramme : selectionnez un bloc DANS le diagramme.");
			return;
		}
		System.out.println("Diagramme : " + diagram.getName());

		// ------------------------------------------------------------------
		// 2) Lecture des blocs et des liens, construction de l'arbre
		// ------------------------------------------------------------------
		@SuppressWarnings("unchecked")
		List<IRPGraphElement> elements = diagram.getGraphicalElements().toList();

		Map<String, Node> nodes = collectNodes(elements);
		List<TreeLink> links = collectLinks(elements, nodes, horizontal);
		if (links.isEmpty()) {
			System.out.println("Aucun lien entre deux blocs dans ce diagramme.");
			return;
		}

		// ------------------------------------------------------------------
		// 3) Mise en page des blocs (en memoire seulement a ce stade)
		// ------------------------------------------------------------------
		if (layout) {
			// Element selectionne : point de depart du reamenagement s'il a des enfants
			IRPModelElement selected = app.getSelectedElement();
			Node start = (selected == null) ? null : nodes.get(selected.getGUID());
			if (start != null && start.children.isEmpty()) start = null;
			System.out.println("Depart : " + (start != null ? start.label : "tous les arbres du diagramme"));
			layoutIndented(nodes.values(), start);
			System.out.println();
			System.out.println("===== BLOCS =====");
			for (Node n : nodes.values()) {
				if (!n.box.equals(n.original)) {
					System.out.println(n.label + " : " + n.original.x() + "," + n.original.y()
							+ " (" + n.original.w() + "x" + n.original.h() + ") -> "
							+ n.box.x() + "," + n.box.y() + " (" + n.box.w() + "x" + n.box.h() + ")");
				}
			}
		}

		// Position du bus de chaque parent (styles V et H)
		Map<Node, Integer> buses = computeBuses(links, horizontal);

		// ------------------------------------------------------------------
		// 4) Application : blocs d'abord, puis liens, dans UNE transaction
		// ------------------------------------------------------------------
		System.out.println();
		System.out.println("===== LIENS =====");
		if (!dryRun) app.startUndoTransaction();
		int done = 0;
		try {
			// Les blocs sont deplaces en premier : Rhapsody peut alors
			// recalculer les liens a sa facon, mais on les reecrit juste apres
			if (layout && !dryRun) {
				for (Node n : nodes.values()) {
					if (!n.box.equals(n.original)) {
						n.graph.setGraphicalProperty("Position", n.box.x() + "," + n.box.y());
						n.graph.setGraphicalProperty("Width", String.valueOf(n.box.w()));
						n.graph.setGraphicalProperty("Height", String.valueOf(n.box.h()));
					}
				}
			}

			for (TreeLink link : links) {
				System.out.println();
				System.out.println("Lien " + link.child().label + " -> parent " + link.parent().label);

				// En style indente, le trace n'a de sens que si l'enfant est
				// sous le parent et a droite de son epine ; sinon on n'y touche pas
				if (style == Style.INDENTED && !isIndentedPositionValid(link)) {
					System.out.println("    IGNORE : l'enfant n'est pas sous le parent et a droite de"
							+ " son epine. Utilisez l'option LAYOUT.");
					continue;
				}

				List<int[]> points = (style == Style.INDENTED)
						? buildIndentedPoints(link)
						: buildCombPoints(link, buses.get(link.parent()), horizontal);
				String polygon = toPolygon(points);

				System.out.println("    avant : " + link.edge().getGraphicalProperty("Polygon").getValue());
				System.out.println("    calcul: " + polygon);
				if (dryRun) continue;

				int[] first = points.get(0);
				int[] last = points.get(points.size() - 1);
				// Extremites d'abord, puis le trace complet en dernier
				link.edge().setGraphicalProperty("SourcePosition", first[0] + "," + first[1]);
				link.edge().setGraphicalProperty("TargetPosition", last[0] + "," + last[1]);
				link.edge().setGraphicalProperty("Polygon", polygon);
				System.out.println("    apres : " + link.edge().getGraphicalProperty("Polygon").getValue());
				done++;
			}
		} finally {
			// La transaction est toujours fermee, meme en cas d'exception
			if (!dryRun) app.endUndoTransaction();
		}

		if (!dryRun) app.refreshAllViews();
		System.out.println();
		System.out.println("Termine : " + links.size() + " lien(s) trouve(s), " + done + " modifie(s).");
	}

	// ======================================================================
	// Lecture du diagramme
	// ======================================================================

	/**
	 * Collecte les blocs qui representent un element de modele (le cadre du
	 * diagramme n'en a pas et est ignore). Cle = GUID de l'element de modele.
	 * Limite connue : si un meme element est dessine deux fois, seul le
	 * premier bloc est pris en compte.
	 */
	private static Map<String, Node> collectNodes(List<IRPGraphElement> elements) {
		Map<String, Node> nodes = new LinkedHashMap<>();
		for (IRPGraphElement ge : elements) {
			if (!(ge instanceof IRPGraphNode)) continue;
			IRPModelElement mo = ge.getModelObject();
			Box box = boxOf(ge);
			if (mo == null || box == null) continue;
			nodes.putIfAbsent(mo.getGUID(), new Node((IRPGraphNode) ge, mo.getGUID(), labelOf(mo), box));
		}
		return nodes;
	}

	/**
	 * Collecte les liens entre deux blocs connus et relie parents et enfants.
	 * Le parent est determine dans cet ordre :
	 * <ol>
	 *   <li>Type = ContainArrow : la source est l'enfant (vu au diagnostic) ;</li>
	 *   <li>sinon, si l'element source appartient a l'element cible (getOwner),
	 *       la source est l'enfant, et inversement ;</li>
	 *   <li>en dernier recours, la geometrie : l'enfant est le bloc le plus
	 *       bas (ou le plus a droite en style H).</li>
	 * </ol>
	 */
	private static List<TreeLink> collectLinks(List<IRPGraphElement> elements,
			Map<String, Node> nodes, boolean horizontal) {
		List<TreeLink> links = new ArrayList<>();

		for (IRPGraphElement ge : elements) {
			if (!(ge instanceof IRPGraphEdge)) continue;
			IRPGraphEdge edge = (IRPGraphEdge) ge;

			Node source = nodeOf(edge.getSource(), nodes);
			Node target = nodeOf(edge.getTarget(), nodes);
			if (source == null || target == null || source == target) continue;

			boolean sourceIsChild = sourceIsChild(edge, source, target, horizontal);
			Node parent = sourceIsChild ? target : source;
			Node child  = sourceIsChild ? source : target;

			// Un enfant n'a qu'un parent dans un arbre : on garde le premier lien
			if (child.parent != null && child.parent != parent) {
				System.out.println("ATTENTION : " + child.label + " a deja le parent "
						+ child.parent.label + ", lien vers " + parent.label + " ignore.");
				continue;
			}
			if (child.parent == null) {
				child.parent = parent;
				parent.children.add(child);
			}
			links.add(new TreeLink(edge, parent, child, sourceIsChild));
		}
		return links;
	}

	/** Determine si la source du lien est l'enfant (voir collectLinks). */
	private static boolean sourceIsChild(IRPGraphEdge edge, Node source, Node target, boolean horizontal) {
		// 1) Type graphique du lien
		try {
			if ("ContainArrow".equals(edge.getGraphicalProperty("Type").getValue())) return true;
		} catch (Exception ignore) {
			// propriete absente sur ce type de lien : on passe au critere suivant
		}

		// 2) Appartenance dans le modele
		IRPModelElement sourceOwner = source.graph.getModelObject().getOwner();
		IRPModelElement targetOwner = target.graph.getModelObject().getOwner();
		if (sourceOwner != null && target.key.equals(sourceOwner.getGUID())) return true;
		if (targetOwner != null && source.key.equals(targetOwner.getGUID())) return false;

		// 3) Geometrie
		return horizontal ? source.box.x() > target.box.x() : source.box.y() > target.box.y();
	}

	// ======================================================================
	// Mise en page des blocs : arbre indente
	// ======================================================================

	/**
	 * Replace les blocs en arbre indente, en memoire. La racine garde sa
	 * position (coin haut gauche) ; tous les blocs prennent la taille standard ;
	 * les descendants sont empiles dessous, decales de INDENT par niveau.
	 *
	 * @param allNodes tous les blocs du diagramme
	 * @param start    bloc selectionne a reamenager avec son sous-arbre,
	 *                 ou null pour reamenager tous les arbres du diagramme
	 */
	private static void layoutIndented(Iterable<Node> allNodes, Node start) {
		List<Node> roots = new ArrayList<>();
		if (start != null) {
			roots.add(start);
		} else {
			// Racines : blocs sans parent qui ont au moins un enfant, de haut en bas
			for (Node n : allNodes) {
				if (n.parent == null && !n.children.isEmpty()) roots.add(n);
			}
			roots.sort(Comparator.comparingInt((Node n) -> n.box.y()).thenComparingInt(n -> n.box.x()));
		}

		Set<Node> visited = new HashSet<>();
		int cursorY = Integer.MIN_VALUE;
		for (Node root : roots) {
			// Une racine ne doit pas chevaucher l'arbre precedent
			int y = Math.max(root.box.y(), cursorY);
			root.box = new Box(root.box.x(), y, NODE_WIDTH, NODE_HEIGHT);
			visited.add(root);
			cursorY = placeChildren(root, root.box.bottom() + V_GAP, visited);
		}
	}

	/**
	 * Place les enfants d'un parent les uns sous les autres, puis leurs propres
	 * enfants (parcours en profondeur). Retourne la prochaine ordonnee libre.
	 */
	private static int placeChildren(Node parent, int cursorY, Set<Node> visited) {
		sortChildrenByModelOrder(parent);

		for (Node child : parent.children) {
			if (!visited.add(child)) continue; // protection contre un cycle
			child.box = new Box(parent.box.x() + INDENT, cursorY, NODE_WIDTH, NODE_HEIGHT);
			cursorY = child.box.bottom() + V_GAP;
			cursorY = placeChildren(child, cursorY, visited);
		}
		return cursorY;
	}

	/**
	 * Trie les enfants dans l'ordre ou le modele les fournit, c'est-a-dire
	 * l'ordre de getNestedElementsByMetaClass("Class", 0) utilise par
	 * GenerateLBS. Un enfant absent de cette liste passe a la fin, dans
	 * son ordre vertical actuel.
	 */
	private static void sortChildrenByModelOrder(Node parent) {
		Map<String, Integer> rank = new HashMap<>();
		try {
			@SuppressWarnings("unchecked")
			List<IRPModelElement> nested = parent.graph.getModelObject()
					.getNestedElementsByMetaClass("Class", 0).toList();
			for (int i = 0; i < nested.size(); i++) rank.put(nested.get(i).getGUID(), i);
		} catch (Exception ignore) {
			// pas d'ordre de modele disponible : seul l'ordre vertical s'applique
		}
		parent.children.sort(Comparator
				.comparingInt((Node n) -> rank.getOrDefault(n.key, Integer.MAX_VALUE))
				.thenComparingInt(n -> n.original.y()));
	}

	// ======================================================================
	// Calcul des traces
	// ======================================================================

	/** Position x de l'epine verticale d'un parent (meme regle que D2Rectangle). */
	private static int spineX(Node parent) {
		return parent.box.x() + parent.box.w() / 8;
	}

	/** Vrai si l'enfant est sous le parent et a droite de son epine. */
	private static boolean isIndentedPositionValid(TreeLink l) {
		return l.child().box.x() > spineX(l.parent())
				&& l.child().box.centerY() > l.parent().box.bottom();
	}

	/**
	 * Trace de l'arbre indente : bord gauche de l'enfant, coude sur l'epine,
	 * bas du parent. Les points sont donnes dans le sens source vers cible.
	 */
	private static List<int[]> buildIndentedPoints(TreeLink l) {
		int spine = spineX(l.parent());
		List<int[]> fromChild = new ArrayList<>();
		fromChild.add(new int[] { l.child().box.x(), l.child().box.centerY() });
		fromChild.add(new int[] { spine, l.child().box.centerY() });
		fromChild.add(new int[] { spine, l.parent().box.bottom() });
		return orient(fromChild, l.sourceIsChild());
	}

	/**
	 * Trace en peigne : l'enfant rejoint le bus commun a ses freres,
	 * le suit, puis rejoint le parent.
	 */
	private static List<int[]> buildCombPoints(TreeLink l, int bus, boolean horizontal) {
		Box c = l.child().box;
		Box p = l.parent().box;
		List<int[]> fromChild = new ArrayList<>();
		if (horizontal) {
			fromChild.add(new int[] { c.x(), c.centerY() });
			if (c.centerY() != p.centerY()) {
				fromChild.add(new int[] { bus, c.centerY() });
				fromChild.add(new int[] { bus, p.centerY() });
			}
			fromChild.add(new int[] { p.right(), p.centerY() });
		} else {
			fromChild.add(new int[] { c.centerX(), c.y() });
			if (c.centerX() != p.centerX()) {
				fromChild.add(new int[] { c.centerX(), bus });
				fromChild.add(new int[] { p.centerX(), bus });
			}
			fromChild.add(new int[] { p.centerX(), p.bottom() });
		}
		return orient(fromChild, l.sourceIsChild());
	}

	/**
	 * Le Polygon doit aller de la source a la cible du lien : si la source
	 * est le parent, on inverse les points calcules depuis l'enfant.
	 */
	private static List<int[]> orient(List<int[]> fromChild, boolean sourceIsChild) {
		if (sourceIsChild) return fromChild;
		List<int[]> reversed = new ArrayList<>();
		for (int i = fromChild.size() - 1; i >= 0; i--) reversed.add(fromChild.get(i));
		return reversed;
	}

	/**
	 * Bus de chaque parent pour les styles V et H : a mi-distance entre le
	 * bord du parent et le bord de son enfant le plus proche.
	 */
	private static Map<Node, Integer> computeBuses(List<TreeLink> links, boolean horizontal) {
		Map<Node, Integer> nearestChild = new HashMap<>();
		for (TreeLink l : links) {
			int start = horizontal ? l.child().box.x() : l.child().box.y();
			nearestChild.merge(l.parent(), start, Math::min);
		}
		Map<Node, Integer> buses = new HashMap<>();
		for (Map.Entry<Node, Integer> e : nearestChild.entrySet()) {
			Box p = e.getKey().box;
			int parentEdge = horizontal ? p.right() : p.bottom();
			buses.put(e.getKey(), (parentEdge + e.getValue()) / 2);
		}
		return buses;
	}

	/** Format de la propriete Polygon : "n,x1,y1,x2,y2,...". */
	private static String toPolygon(List<int[]> points) {
		StringBuilder sb = new StringBuilder().append(points.size());
		for (int[] p : points) sb.append(',').append(p[0]).append(',').append(p[1]);
		return sb.toString();
	}

	// ======================================================================
	// Utilitaires
	// ======================================================================

	/** Bloc connu correspondant a une extremite de lien, ou null. */
	private static Node nodeOf(IRPGraphElement ge, Map<String, Node> nodes) {
		if (!(ge instanceof IRPGraphNode)) return null;
		IRPModelElement mo = ge.getModelObject();
		return mo == null ? null : nodes.get(mo.getGUID());
	}

	/** Lit Position, Width et Height d'un bloc ; null si une valeur est illisible. */
	private static Box boxOf(IRPGraphElement ge) {
		try {
			String[] pos = ge.getGraphicalProperty("Position").getValue().split(",");
			int w = Integer.parseInt(ge.getGraphicalProperty("Width").getValue().trim());
			int h = Integer.parseInt(ge.getGraphicalProperty("Height").getValue().trim());
			return new Box(Integer.parseInt(pos[0].trim()), Integer.parseInt(pos[1].trim()), w, h);
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * Libelle pour la console : nom de l'element, suivi du nom affiche dans
	 * le diagramme s'il est different (les deux peuvent diverger).
	 */
	private static String labelOf(IRPModelElement mo) {
		String name = mo.getName();
		String display = mo.getDisplayName();
		return (display == null || display.isEmpty() || display.equals(name))
				? name : name + " (affiche : " + display + ")";
	}
}
