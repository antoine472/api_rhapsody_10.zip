package test.unittest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.telelogic.rhapsody.core.IRPApplication;
import com.telelogic.rhapsody.core.IRPClass;
import com.telelogic.rhapsody.core.IRPDiagram;
import com.telelogic.rhapsody.core.IRPHyperLink;
import com.telelogic.rhapsody.core.IRPProject;

import tools.OpenAnalysisTables;
import tools.OpenToolDocumentation;
import tools.ReturnHome;

/**
 * Boutons de la barre d'outils Open Analysis Tables et Open Tool Documentation :
 * comportement (Rhapsody simule par Mockito) et declaration dans le .hep et le profil.
 */
public class ToolbarButtonsTest {

	private static final Path PROFILE_RPY = Paths.get("../SafranArchitectureProfile/SafranArchitectureProfile_rpy");
	private static final Path ICONS = Paths.get("../SafranArchitectureProfile/Icons");

	/** Application dont le projet rend l'element donne pour un GUID. */
	private static IRPApplication appWith(String guid, Object element) {
		IRPApplication app = mock(IRPApplication.class);
		IRPProject project = mock(IRPProject.class);
		when(app.activeProject()).thenReturn(project);
		when(project.findElementByGUID(guid)).thenReturn((com.telelogic.rhapsody.core.IRPModelElement) element);
		return app;
	}

	/** Outil de documentation qui note l'adresse au lieu d'ouvrir un navigateur. */
	private static final class RecordingDocumentation extends OpenToolDocumentation {
		final List<String> opened = new ArrayList<>();

		RecordingDocumentation(IRPApplication app) {
			super(app);
		}

		@Override
		protected void browse(String url) {
			opened.add(url);
		}

		String url() {
			return documentationUrl();
		}
	}

	// ------------------------------------------------------------------ Analysis Tables

	@Test
	void analysisTablesButtonOpensThePage() {
		IRPDiagram page = mock(IRPDiagram.class);
		new OpenAnalysisTables(appWith(OpenAnalysisTables.ANALYSIS_TABLES_GUID, page)).execute();
		verify(page).openDiagram();
	}

	@Test
	void analysisTablesButtonWithoutThePageDoesNothing() {
		// page absente : pas d'exception
		new OpenAnalysisTables(appWith(OpenAnalysisTables.ANALYSIS_TABLES_GUID, null)).execute();
		// element de mauvaise nature sous ce GUID : rien n'est ouvert
		IRPClass notADiagram = mock(IRPClass.class);
		new OpenAnalysisTables(appWith(OpenAnalysisTables.ANALYSIS_TABLES_GUID, notADiagram)).execute();
		verify(notADiagram, never()).getName();
	}

	// ------------------------------------------------------------------ Tool Documentation

	@Test
	void documentationButtonOpensTheAddressOfTheHomeTile() {
		IRPHyperLink link = mock(IRPHyperLink.class);
		when(link.getURL()).thenReturn("  https://example.org/doc  ");
		RecordingDocumentation tool = new RecordingDocumentation(
				appWith(OpenToolDocumentation.DOCUMENTATION_LINK_GUID, link));
		tool.execute();
		assertEquals(List.of("https://example.org/doc"), tool.opened);
	}

	@Test
	void documentationButtonWithoutAddressOpensNothing() {
		IRPHyperLink blank = mock(IRPHyperLink.class);
		when(blank.getURL()).thenReturn(" ");
		RecordingDocumentation tool = new RecordingDocumentation(
				appWith(OpenToolDocumentation.DOCUMENTATION_LINK_GUID, blank));
		tool.execute();
		assertTrue(tool.opened.isEmpty());
		assertNull(new RecordingDocumentation(appWith(OpenToolDocumentation.DOCUMENTATION_LINK_GUID, null)).url());
	}

	// ------------------------------------------------------------------ declaration

	@Test
	void guidsDesignateTheAnalysisTablesPageAndTheDocumentationLink() throws IOException {
		String text = new String(Files.readAllBytes(PROFILE_RPY.resolve("SafranArchitectureProfile.sbsx")),
				StandardCharsets.UTF_8);
		assertTrue(Pattern.compile("<IDiagram type=\"e\">\\s*<_id type=\"a\">"
				+ Pattern.quote(OpenAnalysisTables.ANALYSIS_TABLES_GUID)
				+ "</_id>(?:(?!</IDiagram>).)*?<_name type=\"a\">Analysis Tables</_name>", Pattern.DOTALL)
				.matcher(text).find(), "GUID de la page Analysis Tables");
		Matcher link = Pattern.compile("<IMHyperLink type=\"e\">\\s*<_id type=\"a\">"
				+ Pattern.quote(OpenToolDocumentation.DOCUMENTATION_LINK_GUID) + "</_id>(.*?)</IMHyperLink>",
				Pattern.DOTALL).matcher(text);
		assertTrue(link.find(), "GUID de l'hyperlien Tool Documentation");
		assertTrue(link.group(1).contains("<_linkDispName type=\"a\">Tool Documentation</_linkDispName>"));
		assertTrue(link.group(1).contains("<_linkTarget type=\"a\">http"), "adresse web");
	}

	@Test
	void everyToolbarButtonOfTheHepIsDeclaredWithAnExistingIcon() throws IOException {
		String hep = new String(Files.readAllBytes(PROFILE_RPY.resolve("SafranArchitectureProfile.hep")),
				Charset.forName("Cp1252"));
		List<String> buttons = new ArrayList<>();
		Matcher m = Pattern.compile("isToolbarButton(\\d+)=1").matcher(hep);
		while (m.find()) {
			String n = m.group(1);
			Matcher name = Pattern.compile("(?m)^name" + n + "=(.+?)\\r?$").matcher(hep);
			Matcher icon = Pattern.compile("(?m)^pluginIcon" + n + "=\\.\\.\\\\Icons\\\\(.+?)\\r?$").matcher(hep);
			assertTrue(name.find() && icon.find(), "bouton " + n + " incomplet");
			assertTrue(Files.exists(ICONS.resolve(icon.group(1))), "icone absente : " + icon.group(1));
			buttons.add(name.group(1));
		}
		// le nom du bouton est la commande que le plugin recoit
		assertTrue(buttons.contains(ReturnHome.COMMAND));
		assertTrue(buttons.contains(OpenAnalysisTables.COMMAND));
		assertTrue(buttons.contains(OpenToolDocumentation.COMMAND));
	}
}
