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
 * Ses descendants sont replaces selon l'orientation choisie :</p>
 * <ul>
 *   <li>{@link Orientation#VERTICAL} : arbre indente, comme les diagrammes de
 *       GenerateLBS. Les enfants sont empiles sous le parent, decales de
 *       l'indentation (par defaut {@link #INDENT}) vers la droite. Le lien part du milieu du bord gauche
 *       de l'enfant et rejoint une "epine" verticale placee a x + largeur/8
 *       du parent (meme regle que {@link D2Rectangle}) ;</li>
 *   <li>{@link Orientation#HORIZONTAL} : organigramme. Les enfants sont alignes
 *       sur une rangee sous le parent, centree sous lui. Les liens partent du
 *       milieu du haut de chaque enfant et se rejoignent sur une ligne
 *       horizontale commune ("bus") avant de monter au milieu du bas du parent.</li>
 * </ul>
 *
 * <p><b>Profondeur</b> : seuls les niveaux 1 a N sous le bloc de depart sont
 * reorganises ({@link #ALL_LEVELS} pour tous). Au-dela, chaque sous-arbre
 * garde sa disposition et se deplace d'un bloc avec son ancetre de niveau N ;
 * son encombrement est pris en compte pour eviter les chevauchements.</p>
 *
 * <p><b>Espacements</b> : reglables par l'utilisateur ({@link Spacing}). Leur
 * sens depend de l'orientation :</p>
 * <ul>
 *   <li>Vertical : horizontal = decalage d'un niveau (indentation),
 *       vertical = ecart entre deux blocs empiles ;</li>
 *   <li>Horizontal : horizontal = ecart entre deux freres,
 *       vertical = ecart entre un parent et la rangee de ses enfants.</li>
 * </ul>
 *
 * <p>La taille des blocs n'est jamais modifiee : seule leur position change.</p>
 */
public final class TreeDiagramLayout {

	/** Orientation de la mise en page des descendants. */
	public enum Orientation { VERTICAL, HORIZONTAL }

	/** Profondeur illimitee : tous les niveaux sont reorganises (le "*" de Generate LBS). */
	public static final int ALL_LEVELS = -1;

	/** Decalage horizontal d'un niveau en mode vertical, par defaut (HORIZONTAL_OFFSET de GenerateLBS). */
	public static final int INDENT = 100;

	/** Ecart vertical entre deux blocs empiles en mode vertical, par defaut (VERTICAL_SPACING de GenerateLBS). */
	public static final int V_GAP = 20;

	/** Ecart horizontal entre deux freres en mode horizontal, par defaut. */
	public static final int H_GAP = 40;

	/** Ecart vertical entre un parent et la rangee de ses enfants en mode horizontal, par defaut. */
	public static final int LEVEL_GAP = 60;

	/** Plus petit espacement accepte, pour que les liens restent lisibles. */
	public static final int MIN_SPACING = 10;

	/** Plus grand espacement accepte. */
	public static final int MAX_SPACING = 1000;

	/**
	 * Espacements d'une mise en page, en unites du diagramme Rhapsody.
	 *
	 * @param horizontal Vertical : decalage d'un niveau ; Horizontal : ecart entre freres
	 * @param vertical   Vertical : ecart entre blocs empiles ; Horizontal : ecart entre niveaux
	 */
	public record Spacing(int horizontal, int vertical) {

		/** Les valeurs sont ramenees entre MIN_SPACING et MAX_SPACING. */
		public Spacing {
			horizontal = clamp(horizontal);
			vertical = clamp(vertical);
		}

		/** Espacements par defaut d'une orientation (ceux de Generate LBS en vertical). */
		public static Spacing defaults(Orientation orientation) {
			return orientation == Orientation.VERTICAL
					? new Spacing(INDENT, V_GAP)
					: new Spacing(H_GAP, LEVEL_GAP);
		}

		private static int clamp(int value) {
			return Math.max(MIN_SPACING, Math.min(MAX_SPACING, value));
		}
	}

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

		/** Plus petit rectangle contenant ce rectangle et {@code other}. */
		public Box union(Box other) {
			int nx = Math.min(x, other.x);
			int ny = Math.min(y, other.y);
			return new Box(nx, ny, Math.max(right(), other.right()) - nx,
					Math.max(bottom(), other.bottom()) - ny);
		}
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

		/** Niveau sous le bloc de depart (1 = enfant direct), renseigne par {@link TreeDiagramLayout#layout}. */
		public int depth;

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

		/** Deplacement applique par la mise en page, en x. */
		public int dx() { return box.x() - original.x(); }

		/** Deplacement applique par la mise en page, en y. */
		public int dy() { return box.y() - original.y(); }
	}

	// ======================================================================
	// Mise en page
	// ======================================================================

	/** Mise en page sur tous les niveaux, espacements par defaut. */
	public static List<Node> layout(Node start, Orientation orientation) {
		return layout(start, orientation, ALL_LEVELS);
	}

	/** Mise en page avec les espacements par defaut. Voir {@link #layout(Node, Orientation, int, Spacing)}. */
	public static List<Node> layout(Node start, Orientation orientation, int maxDepth) {
		return layout(start, orientation, maxDepth, Spacing.defaults(orientation));
	}

	/**
	 * Replace les descendants de {@code start} ; {@code start} lui-meme ne
	 * bouge pas.
	 *
	 * @param maxDepth nombre de niveaux reorganises sous start (1 = enfants
	 *                 directs seulement), ou {@link #ALL_LEVELS}
	 * @param spacing  espacements (voir {@link Spacing}) ; null = valeurs par defaut
	 * @return tous les descendants de start (reorganises ou deplaces en bloc),
	 *         dans l'ordre de parcours, sans start
	 */
	public static List<Node> layout(Node start, Orientation orientation, int maxDepth, Spacing spacing) {
		Spacing sp = (spacing != null) ? spacing : Spacing.defaults(orientation);
		List<Node> placed = new ArrayList<>();
		Set<Node> visited = new HashSet<>();
		visited.add(start);
		start.depth = 0;

		if (orientation == Orientation.VERTICAL) {
			placeVertical(start, start.box.bottom() + sp.vertical(), 1, maxDepth, sp, visited, placed);
		} else {
			placeHorizontalChildren(start, 1, maxDepth, sp, visited, placed);
		}
		return placed;
	}

	/**
	 * Vrai si le bloc fait partie des niveaux reorganises (son lien vers son
	 * parent est alors redessine) ; faux s'il a seulement suivi son ancetre.
	 */
	public static boolean isArranged(Node node, int maxDepth) {
		return maxDepth == ALL_LEVELS || node.depth <= maxDepth;
	}

	/** Vrai si les descendants de ce niveau ne sont plus reorganises mais suivent en bloc. */
	private static boolean isLastArrangedLevel(int level, int maxDepth) {
		return maxDepth != ALL_LEVELS && level >= maxDepth;
	}

	/**
	 * Nombre de niveaux sous {@code start} (1 si seulement des enfants directs).
	 * Sert a proposer les profondeurs utiles a l'utilisateur.
	 */
	public static int subtreeDepth(Node start) {
		return subtreeDepth(start, new HashSet<>());
	}

	private static int subtreeDepth(Node node, Set<Node> seen) {
		if (!seen.add(node)) return 0;
		int deepest = 0;
		for (Node child : node.children) deepest = Math.max(deepest, 1 + subtreeDepth(child, seen));
		return deepest;
	}

	/**
	 * Mode vertical : chaque enfant est place sous le precedent (ou sous le
	 * parent pour le premier), decale de l'indentation, puis ses descendants sont
	 * places dessous avant de passer au frere suivant. Au dernier niveau
	 * reorganise, le sous-arbre de l'enfant est deplace en bloc.
	 *
	 * @return la prochaine ordonnee libre sous le sous-arbre
	 */
	private static int placeVertical(Node parent, int cursorY, int level, int maxDepth,
			Spacing sp, Set<Node> visited, List<Node> placed) {
		int x = parent.box.x() + indentFor(parent, sp);
		for (Node child : parent.children) {
			if (!visited.add(child)) continue; // protection contre un cycle
			child.depth = level;

			if (isLastArrangedLevel(level, maxDepth)) {
				// Le bloc entier (enfant + descendants) doit commencer a droite
				// de l'indentation et sous le curseur, meme si des descendants
				// debordent a gauche ou au-dessus de l'enfant
				Box bounds = subtreeBounds(child);
				int newX = x + (child.box.x() - bounds.x());
				int newY = cursorY + (child.box.y() - bounds.y());
				moveWithDescendants(child, newX, newY, level, visited, placed);
				cursorY = bounds.bottom() + child.dy() + sp.vertical();
			} else {
				child.box = child.box.moveTo(x, cursorY);
				placed.add(child);
				cursorY = child.box.bottom() + sp.vertical();
				cursorY = placeVertical(child, cursorY, level + 1, maxDepth, sp, visited, placed);
			}
		}
		return cursorY;
	}

	/**
	 * Mode horizontal : la rangee des enfants est centree sous le parent ;
	 * chaque enfant recoit une "bande" aussi large que son sous-arbre, et
	 * il est centre dans sa bande. Au dernier niveau reorganise, le bloc
	 * (enfant + descendants) est centre dans la bande et deplace d'un seul tenant.
	 */
	private static void placeHorizontalChildren(Node parent, int level, int maxDepth,
			Spacing sp, Set<Node> visited, List<Node> placed) {
		List<Node> row = new ArrayList<>();
		for (Node child : parent.children) {
			if (visited.add(child)) row.add(child); // protection contre un cycle
		}
		if (row.isEmpty()) return;

		// Largeur totale de la rangee : bandes des enfants + ecarts
		int total = sp.horizontal() * (row.size() - 1);
		for (Node child : row) total += bandWidth(child, level, maxDepth, sp, new HashSet<>());

		int left = parent.box.centerX() - total / 2;
		int top = parent.box.bottom() + sp.vertical();
		for (Node child : row) {
			child.depth = level;
			int band = bandWidth(child, level, maxDepth, sp, new HashSet<>());

			if (isLastArrangedLevel(level, maxDepth)) {
				// Bloc centre dans sa bande, son bord haut aligne sur la rangee
				Box bounds = subtreeBounds(child);
				int blockLeft = left + (band - bounds.w()) / 2;
				int newX = blockLeft + (child.box.x() - bounds.x());
				int newY = top + (child.box.y() - bounds.y());
				moveWithDescendants(child, newX, newY, level, visited, placed);
			} else {
				child.box = child.box.moveTo(left + (band - child.box.w()) / 2, top);
				placed.add(child);
				placeHorizontalChildren(child, level + 1, maxDepth, sp, visited, placed);
			}
			left += band + sp.horizontal();
		}
	}

	/** Largeur de la bande d'un enfant en mode horizontal. */
	private static int bandWidth(Node node, int level, int maxDepth, Spacing sp, Set<Node> seen) {
		if (!seen.add(node)) return node.box.w();
		// Dernier niveau reorganise : le bloc garde sa forme, sa largeur est son encombrement
		if (isLastArrangedLevel(level, maxDepth)) return subtreeBounds(node).w();
		if (node.children.isEmpty()) return node.box.w();

		int total = sp.horizontal() * (node.children.size() - 1);
		for (Node child : node.children) total += bandWidth(child, level + 1, maxDepth, sp, seen);
		return Math.max(node.box.w(), total);
	}

	/**
	 * Indentation effective sous un parent en mode vertical : la valeur demandee,
	 * mais jamais moins que l'epine du parent + MIN_SPACING, pour que les
	 * enfants restent a droite de l'epine et que les liens soient lisibles.
	 */
	public static int indentFor(Node parent, Spacing sp) {
		int spineOffset = spineX(parent.box) - parent.box.x();
		return Math.max(sp.horizontal(), spineOffset + MIN_SPACING);
	}

	/** Encombrement d'un bloc et de tous ses descendants, a leur position actuelle. */
	public static Box subtreeBounds(Node node) {
		Box bounds = node.box;
		Set<Node> seen = new HashSet<>();
		List<Node> stack = new ArrayList<>(List.of(node));
		while (!stack.isEmpty()) {
			Node n = stack.remove(stack.size() - 1);
			if (!seen.add(n)) continue;
			bounds = bounds.union(n.box);
			stack.addAll(n.children);
		}
		return bounds;
	}

	/**
	 * Place {@code node} en (newX, newY) et deplace tous ses descendants du
	 * meme vecteur : le sous-arbre garde exactement sa disposition.
	 */
	private static void moveWithDescendants(Node node, int newX, int newY, int level,
			Set<Node> visited, List<Node> placed) {
		int dx = newX - node.box.x();
		int dy = newY - node.box.y();
		node.box = node.box.moveTo(newX, newY);
		placed.add(node);

		List<Node> stack = new ArrayList<>(node.children);
		List<Integer> levels = new ArrayList<>();
		for (int i = 0; i < stack.size(); i++) levels.add(level + 1);
		while (!stack.isEmpty()) {
			Node n = stack.remove(stack.size() - 1);
			int depth = levels.remove(levels.size() - 1);
			if (!visited.add(n)) continue; // protection contre un cycle
			n.depth = depth;
			n.box = n.box.moveTo(n.box.x() + dx, n.box.y() + dy);
			placed.add(n);
			for (Node c : n.children) {
				stack.add(c);
				levels.add(depth + 1);
			}
		}
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

	/** Copie de la liste de points decalee de (dx, dy). */
	public static List<int[]> translated(List<int[]> points, int dx, int dy) {
		List<int[]> result = new ArrayList<>();
		for (int[] p : points) result.add(new int[] { p[0] + dx, p[1] + dy });
		return result;
	}

	/** Format de la propriete graphique Polygon d'un lien : "n,x1,y1,x2,y2,...". */
	public static String toPolygon(List<int[]> points) {
		StringBuilder sb = new StringBuilder().append(points.size());
		for (int[] p : points) sb.append(',').append(p[0]).append(',').append(p[1]);
		return sb.toString();
	}

	/**
	 * Lit une valeur de la propriete Polygon ("n,x1,y1,x2,y2,...").
	 *
	 * @return la liste des points, ou null si la valeur est vide ou mal formee
	 */
	public static List<int[]> parsePolygon(String value) {
		if (value == null || value.isBlank()) return null;
		try {
			String[] parts = value.split(",");
			int n = Integer.parseInt(parts[0].trim());
			if (n < 2 || parts.length != 1 + 2 * n) return null;
			List<int[]> points = new ArrayList<>();
			for (int i = 0; i < n; i++) {
				points.add(new int[] {
						Integer.parseInt(parts[1 + 2 * i].trim()),
						Integer.parseInt(parts[2 + 2 * i].trim()) });
			}
			return points;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** Format "x,y" des proprietes graphiques Position, SourcePosition, TargetPosition. */
	public static String toPoint(int[] point) {
		return point[0] + "," + point[1];
	}
}
