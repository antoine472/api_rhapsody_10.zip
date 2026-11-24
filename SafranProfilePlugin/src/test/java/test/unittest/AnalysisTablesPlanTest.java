package test.unittest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import org.junit.jupiter.api.Test;

import com.telelogic.rhapsody.core.IRPClass;
import com.telelogic.rhapsody.core.IRPCollection;
import com.telelogic.rhapsody.core.IRPFlow;
import com.telelogic.rhapsody.core.IRPModelElement;
import com.telelogic.rhapsody.core.IRPPort;
import com.telelogic.rhapsody.core.IRPStereotype;
import com.telelogic.rhapsody.core.IRPSysMLPort;

import test.manual.AnalysisTablesPlan;
import test.manual.AnalysisTablesPlan.Getter;
import test.manual.AnalysisTablesPlan.TableUpdate;

/**
 * Verifie le plan de UpdateAnalysisTables contre le profil du depot, sans Rhapsody :
 * toutes les vignettes de la page Analysis Tables sont couvertes, les elements existent,
 * les motifs n'utilisent que des termes du profil v2, les colonnes visees existent,
 * tous les getters compilent et les getters des ICD rendent le bon resultat (Mockito).
 */
public class AnalysisTablesPlanTest {

	private static final Path PROFILE_DIR = Paths.get("../SafranArchitectureProfile/SafranArchitectureProfile_rpy");
	private static final Path PROFILE_UNIT = PROFILE_DIR.resolve("SafranArchitectureProfile.sbsx");

	/** Metaclasses standard acceptees dans un motif. */
	private static final Set<String> STD = Set.of("Package", "Class", "State", "Dependency", "Link", "Port", "Object");

	/** Element du .sbsx : classe XML, nom, corps. */
	private record Element(String xmlClass, String name, String body) {
	}

	private static String profile() throws IOException {
		return new String(Files.readAllBytes(PROFILE_UNIT), StandardCharsets.UTF_8);
	}

	/** Elements a GUID du fichier, indexes par GUID. */
	private static Map<String, Element> elements(String text) {
		Map<String, Element> map = new HashMap<>();
		Matcher m = Pattern.compile("<(I[A-Za-z]+) type=\"e\">\\s*<_id type=\"a\">([^<]+)</_id>(.*?)</\\1>",
				Pattern.DOTALL).matcher(text);
		while (m.find()) {
			Matcher n = Pattern.compile("<_name type=\"a\">([^<]*)</_name>").matcher(m.group(3));
			map.put(m.group(2), new Element(m.group(1), n.find() ? n.group(1) : "", m.group(3)));
		}
		return map;
	}

	private static Element byName(Map<String, Element> all, String xmlClass, String name) {
		return all.values().stream().filter(e -> e.xmlClass().equals(xmlClass) && e.name().equals(name)).findFirst()
				.orElse(null);
	}

	/** Layout d'une vue (LayoutHandle). */
	private static Element layoutOf(Map<String, Element> all, Element view) {
		Matcher m = Pattern.compile("<LayoutHandle type=\"r\">.*?<_hid type=\"a\">([^<]+)</_hid>", Pattern.DOTALL)
				.matcher(view.body());
		return m.find() ? all.get(m.group(1)) : null;
	}

	/** Colonnes d'un layout : nom et label de contexte, dans l'ordre. */
	private static List<String[]> columns(Element layout) {
		List<String[]> cols = new ArrayList<>();
		Matcher dc = Pattern.compile("<DataColumns type=\"c\">(.*?)</DataColumns>", Pattern.DOTALL)
				.matcher(layout.body());
		if (!dc.find()) return cols;
		Matcher d = Pattern.compile("<TableDataDefinition type=\"e\">(.*?)</TableDataDefinition>", Pattern.DOTALL)
				.matcher(dc.group(1));
		while (d.find()) {
			cols.add(new String[] { tag(d.group(1), "_columnName"), tag(d.group(1), "_contextPatternLabel") });
		}
		return cols;
	}

	private static String tag(String body, String tag) {
		Matcher m = Pattern.compile("<" + tag + " type=\"a\">([^<]*)</" + tag + ">").matcher(body);
		return m.find() ? m.group(1).replace("&amp;", "&") : "";
	}

	/** Noms de stereotypes de tous les fichiers du profil. */
	private static Set<String> stereotypes() throws IOException {
		Set<String> names = new HashSet<>();
		try (var files = Files.list(PROFILE_DIR)) {
			for (Path f : files.filter(p -> p.toString().endsWith(".sbsx")).toList()) {
				String text = new String(Files.readAllBytes(f), StandardCharsets.UTF_8);
				Matcher m = Pattern.compile(
						"<IStereotype type=\"e\">\\s*<_id type=\"a\">[^<]*</_id>.*?<_name type=\"a\">([^<]*)</_name>",
						Pattern.DOTALL).matcher(text);
				while (m.find()) names.add(m.group(1));
			}
		}
		return names;
	}

	/** Libelles des New Terms sans espaces ("Function", "FlowPort"...). */
	private static Set<String> labelsWithoutSpaces(String text) {
		Set<String> labels = new HashSet<>();
		Matcher m = Pattern.compile("<_Name type=\"a\">Name</_Name>\\s*<_Value type=\"a\">([^<]*)</_Value>")
				.matcher(text);
		while (m.find()) labels.add(m.group(1).replace(" ", ""));
		return labels;
	}

	// ------------------------------------------------------------------ couverture

	@Test
	void everyTileOfTheAnalysisTablesPageIsUpdated() throws IOException {
		String text = profile();
		Map<String, Element> all = elements(text);
		// proprietaire de chaque hyperlien
		Map<String, String> ownerOf = new HashMap<>();
		for (Element e : all.values()) {
			Matcher a = Pattern.compile("<AggregatesList type=\"e\">(.*?)</AggregatesList>", Pattern.DOTALL)
					.matcher(e.body());
			if (a.find()) {
				Matcher v = Pattern.compile("<value>([^<]+)</value>").matcher(a.group(1));
				while (v.find()) ownerOf.put(v.group(1), e.name());
			}
		}
		// vignettes du diagramme Analysis Tables : hyperliens poses dans son graphique
		Element page = byName(all, "IDiagram", "Analysis Tables");
		String guid = all.entrySet().stream().filter(x -> x.getValue() == page).findFirst().get().getKey();
		int start = text.indexOf("<CGIClassChart type=\"e\">", text.indexOf(guid + "</_id>"));
		int end = text.indexOf("</IDiagram>", start);
		Set<String> tiles = new HashSet<>();
		Matcher h = Pattern.compile("<_hm2Class type=\"a\">IMHyperLink</_hm2Class>\\s*<_hid type=\"a\">([^<]+)</_hid>")
				.matcher(text.substring(start, end));
		while (h.find()) tiles.add(ownerOf.get(h.group(1)));
		tiles.remove("Home");
		// vues du plan ; la vignette Logical ICD est aujourd'hui portee par LogicalICDView
		Set<String> planned = new HashSet<>();
		for (TableUpdate t : AnalysisTablesPlan.TABLES) {
			planned.add("LogicalICD".equals(t.base()) ? "LogicalICDView" : t.oldView());
		}
		assertEquals(12, tiles.size(), "vignettes : " + tiles);
		assertEquals(tiles, planned);
	}

	@Test
	void viewsAndLayoutsOfThePlanExist() throws IOException {
		Map<String, Element> all = elements(profile());
		for (TableUpdate t : AnalysisTablesPlan.TABLES) {
			Element view = byName(all, "ITableInstance", t.oldView());
			assertNotNull(view, "vue absente : " + t.oldView());
			Element layout = layoutOf(all, view);
			assertNotNull(layout, "vue sans layout : " + t.oldView());
			// le layout de la vue est celui que le plan modifie ou remplace
			String expected = t.cloneOf() != null ? t.replacedLayout() : t.oldLayout();
			assertEquals(expected, layout.name(), t.label());
			if (t.cloneOf() != null) assertNotNull(AnalysisTablesPlan.byBase(t.cloneOf()), t.cloneOf());
		}
		assertNotNull(byName(all, "ITableLayout", AnalysisTablesPlan.FUNCTION_QUERY_OLD));
	}

	@Test
	void newNamesAreUniqueAndFree() throws IOException {
		Map<String, Element> all = elements(profile());
		Set<String> names = new HashSet<>();
		for (TableUpdate t : AnalysisTablesPlan.TABLES) {
			assertTrue(names.add(t.newView()) && names.add(t.newLayout()), t.base());
			assertTrue(t.newLayout().endsWith("_SafranMLv2_Layout") && t.newView().endsWith("_SafranMLv2_View"));
			assertNull(byName(all, "ITableLayout", t.newLayout()), t.newLayout());
			assertNull(byName(all, "ITableInstance", t.newView()), t.newView());
		}
	}

	// ------------------------------------------------------------------ motifs et colonnes

	@Test
	void patternsUseOnlyTermsOfTheProfile() throws IOException {
		String text = profile();
		Set<String> stereos = stereotypes();
		Set<String> labels = labelsWithoutSpaces(text);
		List<String> patterns = new ArrayList<>();
		for (TableUpdate t : AnalysisTablesPlan.TABLES) {
			if (t.contextPattern() != null) patterns.add(t.contextPattern());
		}
		patterns.add(AnalysisTablesPlan.OPERATIONAL_ICD_PATTERN);
		patterns.add(AnalysisTablesPlan.FUNCTION_QUERY_PATTERN);
		for (String p : patterns) {
			// stereotypes cites dans .stereotype(A|B)
			Matcher st = Pattern.compile("\\.stereotype\\(([^)]*)\\)").matcher(p);
			while (st.find()) {
				for (String s : st.group(1).split("\\|")) assertTrue(stereos.contains(s), s + " dans " + p);
			}
			// termes du chemin : stereotype, libelle de New Term sans espaces ou metaclasse standard
			for (String term : p.replaceAll("\\.stereotype\\([^)]*\\)|\\.metaclass\\([^)]*\\)", "").split("[,|]")) {
				String bare = term.replaceAll("\\{[^}]*\\}", "").replaceAll("[*+]$", "").trim();
				if (bare.isEmpty() || bare.endsWith(":")) continue;
				assertTrue(stereos.contains(bare) || labels.contains(bare) || STD.contains(bare),
						"terme inconnu " + bare + " dans " + p);
			}
		}
	}

	@Test
	void columnsUseLabelsOfTheirPatternAndTargetedColumnsExist() throws IOException {
		Map<String, Element> all = elements(profile());
		Element ref = byName(all, "ITableLayout", "OperationalICDNewProfile_Layout");
		for (TableUpdate t : AnalysisTablesPlan.TABLES) {
			Element layout = t.cloneOf() != null ? ref : byName(all, "ITableLayout", t.oldLayout());
			List<String[]> cols = columns(layout);
			String pattern = t.contextPattern() != null ? t.contextPattern()
					: "LogicalICD".equals(t.base()) || "TechnicalICD".equals(t.base()) ? null
							: currentPattern(layout);
			if (pattern != null) {
				List<String> labels = AnalysisTablesPlan.labels(pattern);
				for (String[] c : cols) {
					String label = t.contextLabels().getOrDefault(c[1], c[1]);
					assertTrue(label.isEmpty() || labels.contains(label), t.label() + " : label " + label);
				}
			}
			for (int i : t.columnNames().keySet()) assertTrue(i < cols.size(), t.label() + " colonne " + i);
			for (Map.Entry<Integer, Getter> g : t.getters().entrySet()) {
				assertEquals(g.getValue().header().trim(), cols.get(g.getKey())[0].trim(),
						t.label() + " colonne " + g.getKey());
			}
		}
	}

	private static String currentPattern(Element layout) {
		Matcher m = Pattern.compile("<_Name type=\"a\">ContextPattern</_Name>\\s*<_Value type=\"a\">([^<]*)")
				.matcher(layout.body());
		return m.find() ? m.group(1) : null;
	}

	// ------------------------------------------------------------------ getters

	/** Compile un getter dans une classe qui reproduit la methode generee par Rhapsody ; null sans compilateur. */
	private static Class<?> compile(String className, Getter g) throws Exception {
		JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
		if (javac == null) return null;
		String ret = switch (g.cellType()) {
		case AnalysisTablesPlan.STRING -> "String";
		case AnalysisTablesPlan.MODEL_ELEMENT -> "IRPModelElement";
		default -> "List<IRPModelElement>";
		};
		String src = "package gen;\nimport com.telelogic.rhapsody.core.*;\nimport java.util.*;\n"
				+ "public class " + className + " {\n public " + ret
				+ " get(IRPModelElement cellElement, Integer row, Integer column) {\n" + g.code() + "\n }\n}\n";
		Path dir = Files.createTempDirectory("getters");
		Path file = dir.resolve("gen/" + className + ".java");
		Files.createDirectories(file.getParent());
		Files.writeString(file, src, StandardCharsets.UTF_8);
		int rc = javac.run(null, null, null, "-nowarn", "-encoding", "UTF-8", "-cp",
				System.getProperty("java.class.path"), "-d", dir.toString(), file.toString());
		assertEquals(0, rc, "getter ne compile pas : " + className + "\n" + g.code());
		URLClassLoader loader = new URLClassLoader(new URL[] { dir.toUri().toURL() },
				AnalysisTablesPlanTest.class.getClassLoader());
		return loader.loadClass("gen." + className);
	}

	private static Object call(Class<?> c, IRPModelElement cell) throws Exception {
		Method m = c.getMethod("get", IRPModelElement.class, Integer.class, Integer.class);
		return m.invoke(c.getDeclaredConstructor().newInstance(), cell, 0, 0);
	}

	@Test
	void everyGetterCompiles() throws Exception {
		int n = 0;
		for (TableUpdate t : AnalysisTablesPlan.TABLES) {
			for (Map.Entry<Integer, Getter> g : t.getters().entrySet()) {
				compile(t.base() + "_C" + g.getKey(), g.getValue());
				n++;
			}
		}
		assertTrue(n >= 15, "nombre de getters : " + n);
	}

	@Test
	void icdGettersReadSysmlPortsLikeTheReference() throws Exception {
		Map<Integer, Getter> g = AnalysisTablesPlan.byBase("OperationalICD").getters();
		Class<?> dirA = compile("DirA", g.get(4));
		if (dirA == null) return; // pas de compilateur (JRE) : test sans objet
		Class<?> typeB = compile("TypeB", g.get(9));
		Class<?> stereoA = compile("StereoA", g.get(6));
		IRPFlow flow = mock(IRPFlow.class);
		IRPSysMLPort a = mock(IRPSysMLPort.class);
		IRPSysMLPort b = mock(IRPSysMLPort.class);
		IRPClass typeOfB = mock(IRPClass.class);
		when(flow.getEnd1SysMLPort()).thenReturn(a);
		when(flow.getEnd2SysMLPort()).thenReturn(b);
		when(a.getPortDirection()).thenReturn("Out");
		when(b.getType()).thenReturn(typeOfB);
		// stereotypes de A : un New Term (masque) et un stereotype ajoute (garde)
		IRPStereotype newTerm = mock(IRPStereotype.class);
		IRPStereotype added = mock(IRPStereotype.class);
		when(newTerm.getIsNewTerm()).thenReturn(1);
		when(added.getIsNewTerm()).thenReturn(0);
		IRPCollection stereos = mock(IRPCollection.class);
		when(stereos.toList()).thenReturn(List.of(newTerm, added));
		when(a.getStereotypes()).thenReturn(stereos);
		assertEquals("Out", call(dirA, flow));
		assertEquals(typeOfB, call(typeB, flow));
		assertEquals(List.of(added), call(stereoA, flow));
		// element qui n'est pas un flux : cellule vide
		assertNull(call(dirA, mock(IRPClass.class)));
		assertNull(call(typeB, mock(IRPClass.class)));
	}

	@Test
	void icdGettersReadStandardPortsOfTechnicalConnections() throws Exception {
		Map<Integer, Getter> g = AnalysisTablesPlan.byBase("OperationalICD").getters();
		Class<?> typeA = compile("TypeA", g.get(5));
		if (typeA == null) return;
		Class<?> dirB = compile("DirB", g.get(10));
		Class<?> stereoB = compile("StereoB", g.get(8));
		// Technical Connection : ports standards (Technical Port), pas de port SysML
		IRPFlow flow = mock(IRPFlow.class);
		IRPPort a = mock(IRPPort.class);
		IRPPort b = mock(IRPPort.class);
		IRPClass typeOfA = mock(IRPClass.class);
		when(flow.getEnd1Port()).thenReturn(a);
		when(flow.getEnd2Port()).thenReturn(b);
		when(a.getOtherClass()).thenReturn(typeOfA);
		IRPCollection none = mock(IRPCollection.class);
		when(none.toList()).thenReturn(List.of());
		when(b.getStereotypes()).thenReturn(none);
		assertEquals(typeOfA, call(typeA, flow));
		// un port standard n'a pas de direction
		assertNull(call(dirB, flow));
		assertEquals(List.of(), call(stereoB, flow));
		// flux sans port a l'extremite : cellule vide, pas d'exception
		IRPFlow bare = mock(IRPFlow.class);
		assertNull(call(typeA, bare));
		assertNull(call(stereoB, bare));
	}

	@Test
	void logicalSystemPortsIncludeSysmlPortsWithoutDuplicates() throws Exception {
		Getter g = AnalysisTablesPlan.byBase("LogicalSystem").getters().get(2);
		Class<?> ports = compile("Ports", g);
		if (ports == null) return;
		IRPClass system = mock(IRPClass.class);
		IRPPort standard = mock(IRPPort.class);
		IRPSysMLPort flowPort = mock(IRPSysMLPort.class);
		IRPCollection std = mock(IRPCollection.class);
		IRPCollection sysml = mock(IRPCollection.class);
		when(std.toList()).thenReturn(List.of(standard));
		when(sysml.toList()).thenReturn(List.of(flowPort));
		when(system.getPorts()).thenReturn(std);
		when(system.getNestedElementsByMetaClass("SysMLPort", 0)).thenReturn(sysml);
		assertEquals(List.of(standard, flowPort), call(ports, system));
		assertNull(call(ports, mock(IRPFlow.class)));
	}

	@Test
	void copiesRemapEveryFlowAndDiagramLabelOfTheReference() throws IOException {
		Element ref = byName(elements(profile()), "ITableLayout", "OperationalICDNewProfile_Layout");
		Set<String> refLabels = new HashSet<>();
		for (String[] c : columns(ref)) refLabels.add(c[1]);
		for (TableUpdate t : AnalysisTablesPlan.TABLES) {
			if (t.cloneOf() == null) continue;
			// chaque label de la reference est remplace (sinon la colonne serait vide)
			for (String l : refLabels) {
				if (!l.isEmpty()) assertTrue(t.contextLabels().containsKey(l), t.label() + " : " + l);
			}
			assertFalse(t.contextPattern().contains("OperationalFlow"), t.label());
		}
	}
}
