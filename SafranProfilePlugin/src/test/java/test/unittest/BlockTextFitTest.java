package test.unittest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

	// ======================================================================
	// Police des blocs LBS, FBS et TBS
	// ======================================================================

	@Test
	void formatKeys_coverProfileNamingRules() {
		assertEquals(List.of("LogicalSystem", "LogicalSystem_", "Class"),
				BlockTextFit.formatMetaclassKeys("Logical System", "Class"));
		// "Function" est nomme "Function_" dans le profil
		assertTrue(BlockTextFit.formatMetaclassKeys("Function", "Class").contains("Function_"));
		// Sans new term : metaclasse Rhapsody seule
		assertEquals(List.of("Class"), BlockTextFit.formatMetaclassKeys("", "Class"));
	}

	/**
	 * Pour chaque type de bloc des trois arbres (LBS, FBS, TBS), au moins une
	 * des cles essayees par l'outil existe dans le fichier de format du profil
	 * et definit une police. Garantit que l'ajustement de largeur utilise la
	 * vraie police du profil et non la valeur par defaut.
	 */
	@Test
	void everyBreakdownBlockTypeHasAFontInTheProfile() throws IOException {
		Map<String, String> fonts = readProfileFonts(PROFILE_FORMAT_FILE);
		String[] blockTypes = {
				"Logical System", "Logical System With Reference",
				"Function", "Function With Reference", "Functional System",
				"Technical Component", "Technical Component With Reference" };
		for (String type : blockTypes) {
			boolean found = false;
			for (String key : BlockTextFit.formatMetaclassKeys(type, "Class")) {
				if (fonts.containsKey(key)) found = true;
			}
			assertTrue(found, "Pas de police dans le profil pour " + type);
		}
	}

	@Test
	void functionNamesNeedWiderBlocksThanLogicalSystems() {
		// Profil : Function en Arial 14 gras, Logical System en Arial 12 gras
		TextStyle function = new TextStyle("Arial", 14, true);
		assertTrue(BlockTextFit.fittedWidth("function_10", function)
				> BlockTextFit.fittedWidth("function_10", ARIAL_12_BOLD));
	}

	/** Fichier de format du profil, dans les ressources de test (chemin relatif au projet). */
	private static final Path PROFILE_FORMAT_FILE = Paths.get(
			"src/test/resources/SafranArchitectureProfile/SafranArchitectureProfile_rpy/SafranML_FormatSubject.prp");

	/**
	 * Lit les metaclasses du fichier de format qui definissent "Font.Font".
	 * Format du fichier : lignes "Metaclass X" puis "Property Font.Font String ...".
	 */
	private static Map<String, String> readProfileFonts(Path file) throws IOException {
		Map<String, String> fonts = new HashMap<>();
		String current = null;
		for (String line : Files.readAllLines(file, StandardCharsets.ISO_8859_1)) {
			String s = line.trim();
			if (s.startsWith("Metaclass ")) {
				current = s.substring("Metaclass ".length()).trim();
			} else if (current != null && s.startsWith("Property Font.Font ")) {
				fonts.put(current, s);
			}
		}
		return fonts;
	}
}
