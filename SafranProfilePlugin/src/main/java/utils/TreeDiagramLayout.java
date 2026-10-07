package utils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Geometrie de la mise en page d'un arbre dans un diagramme (LBS, FBS...).
 * <p>
 * Cette classe ne depend pas de l'API Rhapsody : elle travaille sur des
 * rectangles et des listes de points. L'outil {@code tools.RearrangeTreeLayout}
 * lit le diagramme, appelle ces methodes, puis ecrit le resultat dans Rhapsody.
 * Elle peut donc etre testee en JUnit sans lancer Rhapsody.
 * </p>
 *
 * <p><b>Principe</b> : le bloc de depart (l'element selectionne) ne bouge pas.
 * Seuls ses descendants sont replaces, selon l'orientation choisie :</p>
 * <ul>
 *   <li>{@link Orientation#VERTICAL} : arbre indente, comme les diagrammes de
 *       GenerateLBS. Les enfants sont empiles sous le parent, decales de
 *       {@link #INDENT} vers la droite. Le lien part du milieu du bord gauche
 *       de l'enfant et rejoint une "epine" verticale placee a x + largeur/8
 *       du parent (meme regle que {@link D2Rectangle}) ;</li>
 *   <li>{@link Orientation#HORIZONTAL} : organigramme. Les enfants sont alignes
 *       sur une rangee sous le parent, centree sous lui. Les liens partent du
 *       milieu du haut de chaque enfant et se rejoignent sur une ligne
 *       horizontale commune ("bus") avant de monter au milieu du bas du parent.</li>
 * </ul>
 * <p>La taille des blocs n'est jamais modifiee : seule leur position change.</p>
 */
public final class TreeDiagramLayout {

	/** Orientation de la mise en page des descendants. */
	public enum Orientation { VERTICAL, HORIZONTAL }

	/** Decalage horizontal d'un niveau en mode vertical (HORIZONTAL_OFFSET de GenerateLBS). */
	public static final int INDENT = 100;

	/** Ecart vertical entre deux blocs empiles en mode vertical (VERTICAL_SPACING de GenerateLBS). */
	public static final int V_GAP = 20;

	/** Ecart horizontal entre deux freres en mode horizontal. */
	public static final int H_GAP = 40;

	/** Ecart vertical entre un parent et la rangee de ses enfants en mode horizontal. */
	public static final int LEVEL_GAP = 60;

	private TreeDiagramLayout() {
		// classe utilitaire : pas d'instance
	}

	// ======================================================================
	// Modele de donnees
	// ======================================================================

	/** Rectangle d'un bloc, en coordonnees du diagramme (coin haut gauche + taille). */
	public record Box(int x, int y, int w, int h) {
		public int centerX() { return x + w / 2; }
		public int centerY() { return y + h / 2; }
		public int bottom()  { return y + h; }
		public int right()   { return x + w; }

		/** Meme taille, autre position. */
		public Box moveTo(int newX, int newY) { return new Box(newX, newY, w, h); }
	}

	/**
	 * Un bloc de l'arbre. La cle identifie le bloc pour l'appelant (par
	 * exemple le GUID de l'element de modele). L'ordre de {@link #children}
	 * est l'ordre d'affichage des freres : l'appelant le trie avant la mise en page.
	 */
	public static final class Node {
		public final String key;
		public final Box original;
		public Box box;
		public Node parent;
		public final List<Node> children = new ArrayList<>();

		public Node(String key, Box box) {
			this.key = key;
			this.original = box;
			this.box = box;
		}

		/** Ajoute un enfant et renseigne son parent. */
		public void addChild(Node child) {
			child.parent = this;
			children.add(child);
		}

		/** Vrai si la mise en page a deplace ce bloc. */
		public boolean moved() {
			return !box.equals(original);
		}
	}

	// ======================================================================
	// Mise en page
	// ======================================================================

	/**
	 * Replace tous les descendants de {@code start} ; {@code start} lui-meme
	 * ne bouge pas.
	 *
	 * @return les descendants de start, dans l'ordre de parcours (sans start)
	 */
	public static List<Node> layout(Node start, Orientation orientation) {
		List<Node> placed = new ArrayList<>();
		Set<Node> visited = new HashSet<>();
		visited.add(start);

		if (orientation == Orientation.VERTICAL) {
			placeVertical(start, start.box.bottom() + V_GAP, visited, placed);
		} else {
			placeHorizontalChildren(start, visited, placed);
		}
		return placed;
	}

	/**
	 * Mode vertical : chaque enfant est place sous le precedent (ou sous le
	 * parent pour le premier), decale de INDENT, puis ses propres descendants
	 * sont places dessous avant de passer au frere suivant.
	 *
	 * @return la prochaine ordonnee libre sous le sous-arbre
	 */
	private static int placeVertical(Node parent, int cursorY, Set<Node> visited, List<Node> placed) {
		for (Node child : parent.children) {
			if (!visited.add(child)) continue; // protection contre un cycle
			child.box = child.box.moveTo(parent.box.x() + INDENT, cursorY);
			placed.add(child);
			cursorY = child.box.bottom() + V_GAP;
			cursorY = placeVertical(child, cursorY, visited, placed);
		}
		return cursorY;
	}

	/**
	 * Mode horizontal : la rangee des enfants est centree sous le parent ;
	 * chaque enfant recoit une "bande" aussi large que son sous-arbre, et
	 * il est centre dans sa bande.
	 */
	private static void placeHorizontalChildren(Node parent, Set<Node> visited, List<Node> placed) {
		List<Node> row = new ArrayList<>();
		for (Node child : parent.children) {
			if (visited.add(child)) row.add(child); // protection contre un cycle
		}
		if (row.isEmpty()) return;

		// Largeur totale de la rangee : bandes des enfants + ecarts
		int total = H_GAP * (row.size() - 1);
		for (Node child : row) total += subtreeWidth(child, new HashSet<>());

		int left = parent.box.centerX() - total / 2;
		int top = parent.box.bottom() + LEVEL_GAP;
		for (Node child : row) {
			int band = subtreeWidth(child, new HashSet<>());
			child.box = child.box.moveTo(left + (band - child.box.w()) / 2, top);
			placed.add(child);
			placeHorizontalChildren(child, visited, placed);
			left += band + H_GAP;
		}
	}

	/** Largeur necessaire a un sous-arbre en mode horizontal. */
	private static int subtreeWidth(Node node, Set<Node> seen) {
		if (!seen.add(node) || node.children.isEmpty()) return node.box.w();
		int total = H_GAP * (node.children.size() - 1);
		for (Node child : node.children) total += subtreeWidth(child, seen);
		return Math.max(node.box.w(), total);
	}

	// ======================================================================
	// Trace des liens
	// ======================================================================

	/** Abscisse de l'epine verticale d'un parent en mode vertical (regle de D2Rectangle). */
	public static int spineX(Box parent) {
		return parent.x() + parent.w() / 8;
	}

	/**
	 * Ordonnee du bus horizontal d'un parent en mode horizontal : a mi-distance
	 * entre le bas du parent et le haut de son enfant le plus haut.
	 */
	public static int busY(Node parent) {
		int nearest = Integer.MAX_VALUE;
		for (Node child : parent.children) nearest = Math.min(nearest, child.box.y());
		if (nearest == Integer.MAX_VALUE) nearest = parent.box.bottom() + LEVEL_GAP;
		return (parent.box.bottom() + nearest) / 2;
	}

	/**
	 * Points du lien entre un enfant et son parent, de l'enfant vers le parent.
	 * Si la source du lien Rhapsody est le parent, utiliser {@link #reversed(List)}.
	 */
	public static List<int[]> linkPointsFromChild(Node child, Orientation orientation) {
		Box c = child.box;
		Box p = child.parent.box;
		List<int[]> points = new ArrayList<>();

		if (orientation == Orientation.VERTICAL) {
			int spine = spineX(p);
			points.add(new int[] { c.x(), c.centerY() });   // milieu du bord gauche de l'enfant
			points.add(new int[] { spine, c.centerY() });   // coude sur l'epine
			points.add(new int[] { spine, p.bottom() });    // bas du parent
		} else {
			int bus = busY(child.parent);
			points.add(new int[] { c.centerX(), c.y() });   // milieu du haut de l'enfant
			if (c.centerX() != p.centerX()) {
				points.add(new int[] { c.centerX(), bus }); // monte jusqu'au bus
				points.add(new int[] { p.centerX(), bus }); // suit le bus
			}
			points.add(new int[] { p.centerX(), p.bottom() }); // milieu du bas du parent
		}
		return points;
	}

	/** Copie de la liste de points dans l'ordre inverse. */
	public static List<int[]> reversed(List<int[]> points) {
		List<int[]> result = new ArrayList<>();
		for (int i = points.size() - 1; i >= 0; i--) result.add(points.get(i));
		return result;
	}

	/** Format de la propriete graphique Polygon d'un lien : "n,x1,y1,x2,y2,...". */
	public static String toPolygon(List<int[]> points) {
		StringBuilder sb = new StringBuilder().append(points.size());
		for (int[] p : points) sb.append(',').append(p[0]).append(',').append(p[1]);
		return sb.toString();
	}

	/** Format "x,y" des proprietes graphiques Position, SourcePosition, TargetPosition. */
	public static String toPoint(int[] point) {
		return point[0] + "," + point[1];
	}
}
