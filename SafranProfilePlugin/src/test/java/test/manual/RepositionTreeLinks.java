package test.manual;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.telelogic.rhapsody.core.IRPApplication;
import com.telelogic.rhapsody.core.IRPDiagram;
import com.telelogic.rhapsody.core.IRPGraphEdge;
import com.telelogic.rhapsody.core.IRPGraphElement;
import com.telelogic.rhapsody.core.IRPGraphNode;
import com.telelogic.rhapsody.core.IRPModelElement;
import com.telelogic.rhapsody.core.RhapsodyAppServer;

/**
 * Programme de test : repositionne les liens d'un diagramme d'arbre
 * (LBS, FBS...) en "peigne", a partir de la position ACTUELLE des blocs.
 * Les blocs ne sont pas deplaces, seuls les liens sont redessines.
 *
 * <p><b>Ce que le diagnostic a appris</b> (DumpDiagramGraphicalProperties) :</p>
 * <ul>
 *   <li>un lien de composition a Type = ContainArrow et LineStyle = Rectilinear ;</li>
 *   <li>sa source est l'ENFANT et sa cible le PARENT ;</li>
 *   <li>son trace est dans la propriete Polygon, au format
 *       "nbPoints,x1,y1,x2,y2,..." ; le premier point vaut SourcePosition
 *       et le dernier TargetPosition ;</li>
 *   <li>Rhapsody place le coude horizontal a mi-hauteur entre l'enfant et
 *       le parent, donc a une hauteur differente pour chaque enfant :
 *       c'est ce qui donne des lignes qui se croisent.</li>
 * </ul>
 *
 * <p><b>Trois styles de trace</b>, choisis par argument :</p>
 * <ul>
 *   <li>I (defaut) : arbre INDENTE, comme les FBS/LBS generes par le plugin
 *       (enfants decales a droite et empiles sous le parent). Memes ancrages
 *       que utils.D2Rectangle : le lien part du milieu du bord gauche de
 *       l'enfant, va a gauche jusqu'a une "epine" verticale placee a
 *       x + largeur/8 du parent, puis monte jusqu'au bas du parent.
 *       Tous les freres partagent donc la meme epine ;</li>
 *   <li>V : arbre de haut en bas en "peigne" : tous les enfants d'un parent
 *       partagent une ligne horizontale (bus) placee a mi-distance entre le
 *       bas du parent et le haut de l'enfant le plus proche ;</li>
 *   <li>H : meme peigne, mais de gauche a droite.</li>
 * </ul>
 * <p>Note : la propriete "Points" utilisee dans un essai precedent n'existe
 * pas sur un lien (absente de getAllGraphicalProperties) ; c'est Polygon
 * qui porte le trace.</p>
 *
 * <p><b>Utilisation</b> : ouvrir le diagramme dans Rhapsody, cliquer sur un bloc
 * DANS le diagramme, puis Run As, Java Application. Arguments optionnels :</p>
 * <ul>
 *   <li>I (defaut), V ou H : style de trace (voir ci-dessus) ;</li>
 *   <li>DRY : affiche les traces calcules sans rien modifier.</li>
 * </ul>
 * <p>Exemple : "I DRY". Toute la modification est dans UNE transaction :
 * un seul Ctrl+Z dans Rhapsody l'annule.</p>
 */
public class RepositionTreeLinks {

	/** Style de trace des liens. */
	private enum Style { INDENTED, VERTICAL, HORIZONTAL }

	/** Rectangle d'un bloc dans le diagramme (coordonnees du diagramme). */
	private record Box(int x, int y, int w, int h) {
		int centerX() { return x + w / 2; }
		int centerY() { return y + h / 2; }
		int bottom()  { return y + h; }
		int right()   { return x + w; }
	}

	/** Un lien a redessiner, avec son parent et son enfant deja identifies. */
	private record TreeLink(IRPGraphEdge edge, Box parent, Box child,
			boolean sourceIsChild, String parentKey) {}

	public static void main(String[] args) {
		// Lecture des arguments : style de trace et mode "a blanc"
		Style style = Style.INDENTED;
		boolean dryRun = false;
		for (String a : args) {
			if ("I".equalsIgnoreCase(a)) style = Style.INDENTED;
			if ("V".equalsIgnoreCase(a)) style = Style.VERTICAL;
			if ("H".equalsIgnoreCase(a)) style = Style.HORIZONTAL;
			if ("DRY".equalsIgnoreCase(a)) dryRun = true;
		}
		// Le seul style dont l'arbre va de gauche a droite
		boolean horizontal = (style == Style.HORIZONTAL);
		System.out.println("Style : " + style
				+ (dryRun ? " | mode DRY (aucune modification)" : ""));

		// 1) Connexion au Rhapsody ouvert et recuperation du diagramme
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

		// 2) Collecte des liens entre deux blocs, en identifiant parent et enfant
		List<TreeLink> links = collectLinks(diagram, horizontal);
		if (links.isEmpty()) {
			System.out.println("Aucun lien entre deux blocs dans ce diagramme.");
			return;
		}

		// 3) Position du bus de chaque parent : a mi-distance entre le parent
		//    et l'enfant le plus proche, pour que tous ses liens le partagent
		Map<String, Integer> busByParent = computeBuses(links, horizontal);

		// 4) Calcul puis application du nouveau trace de chaque lien.
		//    Une seule transaction : un Ctrl+Z annule l'ensemble.
		if (!dryRun) app.startUndoTransaction();
		try {
			for (TreeLink link : links) {
				int bus = busByParent.get(link.parentKey());
				List<int[]> points = (style == Style.INDENTED)
						? buildIndentedPoints(link)
						: buildPoints(link, bus, horizontal);
				String polygon = toPolygon(points);

				System.out.println();
				System.out.println("Lien " + nameOf(link.edge().getSource())
						+ " -> " + nameOf(link.edge().getTarget()));
				System.out.println("    avant : " + link.edge().getGraphicalProperty("Polygon").getValue());
				System.out.println("    calcul: " + polygon);

				if (dryRun) continue;

				int[] first = points.get(0);
				int[] last = points.get(points.size() - 1);
				// Extremites d'abord, puis le trace complet en dernier,
				// pour que Polygon ne soit pas recalcule apres coup
				link.edge().setGraphicalProperty("SourcePosition", first[0] + "," + first[1]);
				link.edge().setGraphicalProperty("TargetPosition", last[0] + "," + last[1]);
				link.edge().setGraphicalProperty("Polygon", polygon);

				// Relecture : montre ce que Rhapsody a reellement accepte
				System.out.println("    apres : " + link.edge().getGraphicalProperty("Polygon").getValue());
			}
		} finally {
			// La transaction est toujours fermee, meme en cas d'exception
			if (!dryRun) app.endUndoTransaction();
		}

		if (!dryRun) app.refreshAllViews();
		System.out.println();
		System.out.println("Termine : " + links.size() + " lien(s) traite(s).");
	}

	/**
	 * Parcourt les elements graphiques du diagramme et garde les liens dont
	 * la source et la cible sont des blocs. Le parent est le bloc situe le
	 * plus haut (arbre vertical) ou le plus a gauche (arbre horizontal) :
	 * cela marche quel que soit le sens du lien dans le modele.
	 */
	private static List<TreeLink> collectLinks(IRPDiagram diagram, boolean horizontal) {
		List<TreeLink> links = new ArrayList<>();

		@SuppressWarnings("unchecked")
		List<IRPGraphElement> elements = diagram.getGraphicalElements().toList();

		for (IRPGraphElement ge : elements) {
			if (!(ge instanceof IRPGraphEdge)) continue;
			IRPGraphEdge edge = (IRPGraphEdge) ge;

			if (!(edge.getSource() instanceof IRPGraphNode)
					|| !(edge.getTarget() instanceof IRPGraphNode)) continue;

			Box source = boxOf(edge.getSource());
			Box target = boxOf(edge.getTarget());
			if (source == null || target == null) continue;

			// La source est l'enfant si elle est "apres" la cible sur l'axe de l'arbre
			boolean sourceIsChild = horizontal ? source.x() > target.x() : source.y() > target.y();
			Box parent = sourceIsChild ? target : source;
			Box child  = sourceIsChild ? source : target;
			IRPGraphElement parentElement = sourceIsChild ? edge.getTarget() : edge.getSource();

			links.add(new TreeLink(edge, parent, child, sourceIsChild, keyOf(parentElement, parent)));
		}
		return links;
	}

	/**
	 * Pour chaque parent, calcule la coordonnee du bus : y en vertical, x en horizontal.
	 * Le bus est place a mi-distance entre le bord du parent et le bord
	 * de son enfant le plus proche.
	 */
	private static Map<String, Integer> computeBuses(List<TreeLink> links, boolean horizontal) {
		Map<String, Integer> nearestChild = new HashMap<>();
		Map<String, Integer> parentEdge = new HashMap<>();

		for (TreeLink l : links) {
			int childStart = horizontal ? l.child().x() : l.child().y();
			nearestChild.merge(l.parentKey(), childStart, Math::min);
			parentEdge.put(l.parentKey(), horizontal ? l.parent().right() : l.parent().bottom());
		}

		Map<String, Integer> buses = new HashMap<>();
		for (Map.Entry<String, Integer> e : parentEdge.entrySet()) {
			int bus = (e.getValue() + nearestChild.get(e.getKey())) / 2;
			buses.put(e.getKey(), bus);
		}
		return buses;
	}

	/**
	 * Construit les points du trace, dans le sens source vers cible du lien.
	 * Vertical  : haut de l'enfant, bus, bas du parent.
	 * Horizontal: gauche de l'enfant, bus, droite du parent.
	 */
	private static List<int[]> buildPoints(TreeLink l, int bus, boolean horizontal) {
		int[] childAnchor;
		int[] parentAnchor;
		List<int[]> fromChild = new ArrayList<>();

		if (horizontal) {
			childAnchor  = new int[] { l.child().x(), l.child().centerY() };
			parentAnchor = new int[] { l.parent().right(), l.parent().centerY() };
			fromChild.add(childAnchor);
			if (childAnchor[1] != parentAnchor[1]) {
				// Deux coudes : on rejoint le bus, on le suit, puis on va au parent
				fromChild.add(new int[] { bus, childAnchor[1] });
				fromChild.add(new int[] { bus, parentAnchor[1] });
			}
			fromChild.add(parentAnchor);
		} else {
			childAnchor  = new int[] { l.child().centerX(), l.child().y() };
			parentAnchor = new int[] { l.parent().centerX(), l.parent().bottom() };
			fromChild.add(childAnchor);
			if (childAnchor[0] != parentAnchor[0]) {
				fromChild.add(new int[] { childAnchor[0], bus });
				fromChild.add(new int[] { parentAnchor[0], bus });
			}
			fromChild.add(parentAnchor);
		}

		// Si la source du lien est le parent, on inverse l'ordre des points
		if (!l.sourceIsChild()) {
			List<int[]> reversed = new ArrayList<>();
			for (int i = fromChild.size() - 1; i >= 0; i--) reversed.add(fromChild.get(i));
			return reversed;
		}
		return fromChild;
	}

	/**
	 * Trace de l'arbre indente, dans le sens source vers cible du lien.
	 * Ancrages identiques a utils.D2Rectangle :
	 * enfant = milieu du bord gauche (getSourcePosition),
	 * parent = bas, a x + largeur/8 (getTargetPosition).
	 * Le coude est a l'intersection de l'epine du parent et de la ligne
	 * horizontale de l'enfant : c'est le point "TargetX, SourceY" du
	 * commentaire de RearrangeLBS_VerticalTree.
	 */
	private static List<int[]> buildIndentedPoints(TreeLink l) {
		int spineX = l.parent().x() + l.parent().w() / 8;

		List<int[]> fromChild = new ArrayList<>();
		fromChild.add(new int[] { l.child().x(), l.child().centerY() });  // bord gauche de l'enfant
		fromChild.add(new int[] { spineX, l.child().centerY() });         // coude sur l'epine
		fromChild.add(new int[] { spineX, l.parent().bottom() });         // bas du parent

		// Si la source du lien est le parent, on inverse l'ordre des points
		if (!l.sourceIsChild()) {
			List<int[]> reversed = new ArrayList<>();
			for (int i = fromChild.size() - 1; i >= 0; i--) reversed.add(fromChild.get(i));
			return reversed;
		}
		return fromChild;
	}

	/** Met les points au format de la propriete Polygon : "n,x1,y1,x2,y2,...". */
	private static String toPolygon(List<int[]> points) {
		StringBuilder sb = new StringBuilder().append(points.size());
		for (int[] p : points) sb.append(',').append(p[0]).append(',').append(p[1]);
		return sb.toString();
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

	/** Cle unique d'un parent : GUID de son element de modele, sinon sa position. */
	private static String keyOf(IRPGraphElement ge, Box box) {
		IRPModelElement mo = ge.getModelObject();
		return mo != null ? mo.getGUID() : box.x() + "," + box.y();
	}

	/** Nom de l'element de modele represente, pour l'affichage. */
	private static String nameOf(IRPGraphElement ge) {
		IRPModelElement mo = ge.getModelObject();
		return mo != null ? mo.getName() : "<sans nom>";
	}
}
