package test.unittest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import utils.BlockTextFit;
import utils.BlockTextFit.TextStyle;

/**
 * Tests du calcul de largeur des blocs ({@link BlockTextFit}).
 * Pur Java : ne lance pas Rhapsody et n'a pas besoin d'ecran.
 */
public class BlockTextFitTest {

	private static final TextStyle ARIAL_12_BOLD = TextStyle.DEFAULT;

	/**
	 * Calibrage sur un diagramme LBS reel (Arial 12 gras) : "logicalsystem_10"
	 * etait coupe sur deux lignes dans un bloc de 150 et tenait sur une ligne
	 * dans un bloc de 204. La largeur calculee doit donc etre entre les deux.
	 */
	@Test
	void calibration_matchesObservedRhapsodyBlocks() {
		int width = BlockTextFit.fittedWidth("logicalsystem_10", ARIAL_12_BOLD);
		assertTrue(width > 150, "Trop etroit : le nom serait coupe (" + width + ")");
		assertTrue(width <= 204, "Plus large que necessaire (" + width + ")");
	}

	@Test
	void longerNameGivesWiderBlock() {
		int shortName = BlockTextFit.fittedWidth("logicalsystem_10", ARIAL_12_BOLD);
		int longName = BlockTextFit.fittedWidth("logicalsystem_10_with_a_much_longer_name", ARIAL_12_BOLD);
		assertTrue(longName > shortName);
	}

	@Test
	void boldIsNeverNarrowerThanPlain() {
		TextStyle plain = new TextStyle("Arial", 12, false);
		assertTrue(BlockTextFit.fittedWidth("logicalsystem_10", ARIAL_12_BOLD)
				>= BlockTextFit.fittedWidth("logicalsystem_10", plain));
	}

	@Test
	void widthIsRoundedAndBounded() {
		int width = BlockTextFit.fittedWidth("logicalsystem_10", ARIAL_12_BOLD);
		assertEquals(0, width % BlockTextFit.ROUND_TO, "Arrondi a la dizaine");

		// Nom tres court ou vide : largeur minimale
		assertEquals(BlockTextFit.MIN_WIDTH, BlockTextFit.fittedWidth("A", ARIAL_12_BOLD));
		assertEquals(BlockTextFit.MIN_WIDTH, BlockTextFit.fittedWidth("", ARIAL_12_BOLD));

		// Nom tres long : largeur maximale
		String veryLong = "x".repeat(500);
		assertEquals(BlockTextFit.MAX_WIDTH, BlockTextFit.fittedWidth(veryLong, ARIAL_12_BOLD));
	}

	@Test
	void invalidStyleFallsBackToDefaults() {
		TextStyle invalid = new TextStyle("", 0, true);
		assertEquals("Arial", invalid.fontName());
		assertEquals(12, invalid.sizePt());
	}
}
