package test.unittest;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;

import org.junit.jupiter.api.Test;

import utils.TreeDiagramLayout;
import utils.TreeDiagramLayout.Box;
import utils.TreeDiagramLayout.Node;
import utils.TreeDiagramLayout.Orientation;

/**
 * Tests de la geometrie de Rearrange Tree Layout ({@link TreeDiagramLayout}).
 * Pur Java : ne lance pas Rhapsody.
 *
 * <p>Le cas de reference reproduit le diagramme LBS attendu :</p>
 * <pre>
 * logicalsystem_1 (25,32)
 *   logicalsystem_11
 *   logicalsystem_12
 *   logicalsystem_7
 *     logicalsystem_8
 *     logicalsystem_9
 *     logicalsystem_14
 * </pre>
 * <p>Tous les blocs font 300 x 100, comme ceux de Generate LBS.</p>
 */
public class TreeDiagramLayoutTest {

	/** Bloc 300 x 100 place n'importe ou : la mise en page doit le deplacer. */
	private static Node block(String key, int x, int y) {
		return new Node(key, new Box(x, y, 300, 100));
	}

	/** Construit l'arbre de reference, blocs volontairement en desordre. */
	private static Node referenceTree() {
		Node root = block("ls_1", 25, 32);
		Node ls11 = block("ls_11", 400, 900);
		Node ls12 = block("ls_12", 0, 500);
		Node ls7  = block("ls_7", 700, 50);
		root.addChild(ls11);
		root.addChild(ls12);
		root.addChild(ls7);
		ls7.addChild(block("ls_8", 10, 10));
		ls7.addChild(block("ls_9", 20, 20));
		ls7.addChild(block("ls_14", 30, 30));
		return root;
	}

	@Test
	void vertical_reproducesGenerateLbsLayout() {
		Node root = referenceTree();

		List<Node> placed = TreeDiagramLayout.layout(root, Orientation.VERTICAL);

		// Ordre de parcours : enfants empiles, sous-arbre de ls_7 apres lui
		assertEquals(6, placed.size());
		int[][] expected = {
				{ 125, 152 },  // ls_11 : decale de 100, 20 sous la racine (32 + 100 + 20)
				{ 125, 272 },  // ls_12
				{ 125, 392 },  // ls_7
				{ 225, 512 },  // ls_8  : decale de 100 sous ls_7
				{ 225, 632 },  // ls_9
				{ 225, 752 },  // ls_14
		};
		for (int i = 0; i < expected.length; i++) {
			Node n = placed.get(i);
			assertEquals(expected[i][0], n.box.x(), n.key + " x");
			assertEquals(expected[i][1], n.box.y(), n.key + " y");
		}
	}

	@Test
	void selectedBlockNeverMoves() {
		Node root = referenceTree();
		TreeDiagramLayout.layout(root, Orientation.VERTICAL);
		assertFalse(root.moved(), "Le bloc selectionne doit garder sa position");

		Node root2 = referenceTree();
		TreeDiagramLayout.layout(root2, Orientation.HORIZONTAL);
		assertFalse(root2.moved(), "Le bloc selectionne doit garder sa position");
	}

	@Test
	void blockSizesAreKept() {
		Node root = new Node("root", new Box(0, 0, 250, 84));
		Node child = new Node("child", new Box(500, 500, 204, 78));
		root.addChild(child);

		TreeDiagramLayout.layout(root, Orientation.VERTICAL);

		assertEquals(204, child.box.w());
		assertEquals(78, child.box.h());
	}

	@Test
	void vertical_linkGoesFromChildLeftToParentSpine() {
		Node root = referenceTree();
		TreeDiagramLayout.layout(root, Orientation.VERTICAL);
		Node ls11 = root.children.get(0);

		List<int[]> points = TreeDiagramLayout.linkPointsFromChild(ls11, Orientation.VERTICAL);

		// Epine : 25 + 300/8 = 62 ; milieu de ls_11 : 152 + 50 = 202 ; bas racine : 132
		assertEquals("3,125,202,62,202,62,132", TreeDiagramLayout.toPolygon(points));
	}

	@Test
	void horizontal_childrenRowIsCenteredUnderParent() {
		Node root = new Node("root", new Box(500, 0, 300, 100));
		Node a = block("a", 0, 0);
		Node b = block("b", 0, 0);
		root.addChild(a);
		root.addChild(b);

		TreeDiagramLayout.layout(root, Orientation.HORIZONTAL);

		// Rangee : 300 + 40 + 300 = 640, centree sous 650 -> commence a 330
		assertEquals(330, a.box.x());
		assertEquals(670, b.box.x());
		// 60 sous le bas du parent
		assertEquals(160, a.box.y());
		assertEquals(160, b.box.y());
	}

	@Test
	void horizontal_subtreeGetsABandAsWideAsItsChildren() {
		Node root = new Node("root", new Box(500, 0, 300, 100));
		Node a = block("a", 0, 0);
		Node b = block("b", 0, 0);
		Node a1 = block("a1", 0, 0);
		Node a2 = block("a2", 0, 0);
		root.addChild(a);
		root.addChild(b);
		a.addChild(a1);
		a.addChild(a2);

		TreeDiagramLayout.layout(root, Orientation.HORIZONTAL);

		// Bande de a = 640, bande de b = 300 : total 980 centre sous 650 -> 160
		assertEquals(330, a.box.x());  // centre dans sa bande : 160 + (640 - 300) / 2
		assertEquals(840, b.box.x());  // 160 + 640 + 40
		assertEquals(160, a1.box.x());
		assertEquals(500, a2.box.x());
		assertEquals(320, a1.box.y()); // 160 + 100 + 60
	}

	@Test
	void horizontal_linkUsesCommonBus() {
		Node root = new Node("root", new Box(500, 0, 300, 100));
		Node a = block("a", 0, 0);
		Node b = block("b", 0, 0);
		root.addChild(a);
		root.addChild(b);
		TreeDiagramLayout.layout(root, Orientation.HORIZONTAL);

		// Bus a mi-distance entre le bas du parent (100) et le haut des enfants (160)
		assertEquals(130, TreeDiagramLayout.busY(root));
		List<int[]> points = TreeDiagramLayout.linkPointsFromChild(a, Orientation.HORIZONTAL);
		assertEquals("4,480,160,480,130,650,130,650,100", TreeDiagramLayout.toPolygon(points));
	}

	@Test
	void reversedInvertsPointOrder() {
		List<int[]> points = List.of(new int[] { 1, 2 }, new int[] { 3, 4 });
		List<int[]> reversed = TreeDiagramLayout.reversed(points);
		assertArrayEquals(new int[] { 3, 4 }, reversed.get(0));
		assertArrayEquals(new int[] { 1, 2 }, reversed.get(1));
	}
}
