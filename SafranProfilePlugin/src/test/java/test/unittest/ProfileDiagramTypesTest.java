package test.unittest;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import tools.GenerateTBD;

/**
 * Verifie que les types de diagrammes crees par Generate LBS / FBS / TBS
 * existent bien comme new terms dans le profil (copie des ressources de test).
 * Pur Java : lit le fichier du profil, ne lance pas Rhapsody.
 */
public class ProfileDiagramTypesTest {

	/** Unite principale du profil, dans les ressources de test (chemin relatif au projet). */
	private static final Path PROFILE_UNIT = Paths.get(
			"src/test/resources/SafranArchitectureProfile/SafranArchitectureProfile_rpy/SafranArchitectureProfile.sbsx");

	/**
	 * Valeurs de la propriete "Name" des new terms : bloc XML
	 * &lt;_Name&gt;Name&lt;/_Name&gt; suivi de &lt;_Value&gt;...&lt;/_Value&gt;.
	 */
	private static final Pattern NEW_TERM_NAME = Pattern.compile(
			"<_Name type=\"a\">Name</_Name>\\s*<_Value type=\"a\">([^<]*)</_Value>");

	@Test
	void generateTbsCreatesADiagramTypeDefinedInTheProfile() throws IOException {
		Set<String> types = readNewTermNames();
		assertTrue(types.contains(GenerateTBD.DIAGRAM_TYPE),
				"Type de diagramme inconnu du profil : " + GenerateTBD.DIAGRAM_TYPE);
	}

	@Test
	void breakdownDiagramTypesUsedByGenerateLbsAndFbsExist() throws IOException {
		Set<String> types = readNewTermNames();
		// Valeurs passees a addNewAggr par GenerateLBS et GenerateFBS
		assertTrue(types.contains("Logical Breakdown Structure"));
		assertTrue(types.contains("Functional Breakdown Structure"));
	}

	private static Set<String> readNewTermNames() throws IOException {
		String content = new String(Files.readAllBytes(PROFILE_UNIT), StandardCharsets.UTF_8);
		Set<String> names = new HashSet<>();
		Matcher m = NEW_TERM_NAME.matcher(content);
		while (m.find()) names.add(m.group(1));
		return names;
	}
}
