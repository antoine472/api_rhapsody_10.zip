package test.unittest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import test.manual.MonitoringPlan;
import test.manual.MonitoringPlan.DiagramIndex;
import test.manual.MonitoringPlan.Kind;
import test.manual.MonitoringPlan.Move;
import test.manual.MonitoringPlan.Tile;

/**
 * Verifie le plan de OrganizeMonitoring contre le profil du depot, sans Rhapsody :
 * types de diagrammes couverts, elements a deplacer presents, images presentes,
 * vignettes de la page Home sans chevauchement et dans leur bande.
 */
public class MonitoringPlanTest {

	/** Profil du depot (le programme le modifie), chemin relatif au projet SafranProfilePlugin. */
	private static final Path PROFILE_DIR = Paths.get("../SafranArchitectureProfile");
	private static final Path PROFILE_UNIT = PROFILE_DIR
			.resolve("SafranArchitectureProfile_rpy/SafranArchitectureProfile.sbsx");
	private static final Path IMAGES = PROFILE_DIR.resolve("Icons/Home");

	/** Metaclasses de base des New Terms de diagramme (les matrices ne sont pas des diagrammes). */
	private static final Set<String> DIAGRAM_BASES = Set.of("ObjectModelDiagram", "StructureDiagram",
			"SequenceDiagram", "UseCaseDiagram", "ActivityDiagram", "Statechart");

	private static String profile() throws IOException {
		// le fichier est en UTF-8 ; les caracteres inconnus sont remplaces, sans effet sur les noms cherches
		return new String(Files.readAllBytes(PROFILE_UNIT), StandardCharsets.UTF_8);
	}

	/** Libelle ("Name") de chaque New Term dont la metaclasse de base est un diagramme. */
	private static Set<String> diagramNewTermLabels(String text) {
		Set<String> labels = new HashSet<>();
		Matcher st = Pattern.compile("<IStereotype type=\"e\">(.*?)</IStereotype>", Pattern.DOTALL).matcher(text);
		while (st.find()) {
			String body = st.group(1);
			if (!body.contains("<_newTerm type=\"a\">1</_newTerm>")) continue;
			Matcher base = Pattern.compile("<_m2Classes type=\"c\">.*?<value>([^<]*)</value>", Pattern.DOTALL)
					.matcher(body);
			Matcher label = Pattern.compile("<_Name type=\"a\">Name</_Name>\\s*<_Value type=\"a\">([^<]*)</_Value>")
					.matcher(body);
			if (base.find() && DIAGRAM_BASES.contains(base.group(1)) && label.find()) labels.add(label.group(1));
		}
		return labels;
	}

	/** Noms des elements d'une classe XML (ITableInstance, ITableLayout, IDiagram). */
	private static Set<String> names(String text, String xmlClass) {
		Set<String> names = new HashSet<>();
		Matcher m = Pattern.compile("<" + xmlClass + " type=\"e\">\\s*<_id type=\"a\">[^<]*</_id>(.*?)</" + xmlClass + ">",
				Pattern.DOTALL).matcher(text);
		while (m.find()) {
			Matcher n = Pattern.compile("<_name type=\"a\">([^<]*)</_name>").matcher(m.group(1));
			if (n.find()) names.add(n.group(1));
		}
		return names;
	}

	@Test
	void everyDiagramTypeOfTheProfileHasAnIndexTable() throws IOException {
		Set<String> labels = diagramNewTermLabels(profile());
		Set<String> planned = MonitoringPlan.DIAGRAM_INDEX_TABLES.stream().map(DiagramIndex::elementType)
				.collect(Collectors.toSet());
		// tous les types de diagramme du profil, et rien d'autre
		assertEquals(22, labels.size(), "nombre de New Terms de diagramme : " + labels);
		assertEquals(labels, planned);
	}

	@Test
	void existingIndexTablesAndTemplateArePresent() throws IOException {
		Set<String> views = names(profile(), "ITableInstance");
		assertTrue(views.contains(MonitoringPlan.TEMPLATE_VIEW));
		for (DiagramIndex d : MonitoringPlan.DIAGRAM_INDEX_TABLES) {
			// une table a creer n'existe pas encore ; une table existante doit exister
			assertEquals(!d.created(), views.contains(d.view()), d.view());
		}
	}

	@Test
	void everyElementToMoveExists() throws IOException {
		String text = profile();
		Set<String> views = names(text, "ITableInstance");
		Set<String> layouts = names(text, "ITableLayout");
		Set<String> diagrams = names(text, "IDiagram");
		for (Move m : MonitoringPlan.moves()) {
			Set<String> pool = m.kind() == Kind.VIEW ? views : m.kind() == Kind.LAYOUT ? layouts : diagrams;
			assertTrue(pool.contains(m.name()), m.kind() + " absent du profil : " + m.name());
		}
	}

	@Test
	void eachElementIsMovedOnceAndIntoAKnownFolder() {
		List<Move> moves = MonitoringPlan.moves();
		Set<String> keys = new HashSet<>();
		for (Move m : moves) {
			assertTrue(keys.add(m.kind() + ":" + m.name()), "deplace deux fois : " + m.name());
			String root = m.folder().split("/")[0];
			assertTrue(Set.of(MonitoringPlan.OK, MonitoringPlan.NOK, MonitoringPlan.TBD).contains(root), m.folder());
		}
	}

	@Test
	void tileImagesExist() throws IOException {
		Set<String> files;
		try (Stream<Path> s = Files.list(IMAGES)) {
			// Windows ignore la casse : comparaison en minuscules
			files = s.map(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
		}
		for (DiagramIndex d : MonitoringPlan.DIAGRAM_INDEX_TABLES) {
			if (d.image() != null) assertTrue(files.contains(d.image().toLowerCase(Locale.ROOT)), d.image());
			// une table creee a forcement une image
			if (d.created()) assertTrue(d.image() != null, d.view());
		}
	}

	@Test
	void newLayoutNamesAreFree() throws IOException {
		Set<String> layouts = names(profile(), "ITableLayout");
		for (DiagramIndex d : MonitoringPlan.DIAGRAM_INDEX_TABLES) {
			if (d.created()) assertFalse(layouts.contains(d.layoutName()), d.layoutName());
		}
	}

	@Test
	void tilesStayInsideTheirBandWithoutOverlap() {
		for (int band = 0; band < MonitoringPlan.LEVELS.length; band++) {
			int n = MonitoringPlan.tablesOfBand(band).size();
			List<Tile> tiles = MonitoringPlan.layoutBand(band, n);
			assertEquals(n, tiles.size());
			int top = MonitoringPlan.BAND_TOP[band];
			for (int i = 0; i < n; i++) {
				Tile t = tiles.get(i);
				// sous le bandeau de titre, dans la bande, a gauche de la colonne de droite
				assertTrue(t.y() >= top + MonitoringPlan.TITLE_HEIGHT, "titre recouvert, bande " + band);
				assertTrue(t.y() + t.h() <= top + MonitoringPlan.BAND_HEIGHT, "deborde en bas, bande " + band);
				assertTrue(t.x() >= MonitoringPlan.BAND_LEFT && t.x() + t.w() <= MonitoringPlan.BAND_RIGHT,
						"deborde sur les cotes, bande " + band);
				if (i > 0) {
					Tile prev = tiles.get(i - 1);
					assertEquals(MonitoringPlan.GAP, t.x() - (prev.x() + prev.w()), "ecart irregulier");
					assertEquals(prev.w(), t.w(), "largeurs differentes dans une bande");
				}
			}
		}
	}

	@Test
	void layoutOfSevenTilesIsCentered() {
		List<Tile> tiles = MonitoringPlan.layoutBand(0, 7);
		int left = tiles.get(0).x() - MonitoringPlan.BAND_LEFT;
		int right = MonitoringPlan.BAND_RIGHT - (tiles.get(6).x() + tiles.get(6).w());
		assertTrue(Math.abs(left - right) <= 1, left + " / " + right);
	}
}
