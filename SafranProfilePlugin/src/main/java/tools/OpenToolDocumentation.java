package tools;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import com.telelogic.rhapsody.core.IRPApplication;
import com.telelogic.rhapsody.core.IRPHyperLink;
import com.telelogic.rhapsody.core.IRPModelElement;
import com.telelogic.rhapsody.core.IRPProject;

/**
 * Bouton de la barre d'outils "Open Tool Documentation" : ouvre dans le navigateur
 * la meme page que la vignette "Tool Documentation" de la page Home.
 *
 * <p>L'adresse est lue au moment du clic dans l'hyperlien de cette vignette : si
 * l'adresse de la vignette change, le bouton suit sans modifier le plugin.</p>
 */
public class OpenToolDocumentation extends RhapsodyTool {

	/** Nom de la commande (name56 du fichier .hep). */
	public static final String COMMAND = "Open Tool Documentation";

	/** GUID de l'hyperlien "Tool Documentation" de la page Home. */
	public static final String DOCUMENTATION_LINK_GUID = "GUID 97b8f0fb-a4da-4491-9b6b-a42eeb9b8143";

	public OpenToolDocumentation(IRPApplication rpyApp) {
		super(rpyApp);
	}

	@Override
	public String commandName() {
		return COMMAND;
	}

	@Override
	public void execute() {
		String url = documentationUrl();
		if (url == null) {
			rhpLog.error("Tool Documentation link is not present in the project, or has no address.");
			return;
		}
		rhpLog.debug("Open Tool Documentation requested: " + url);
		try {
			browse(url);
		} catch (IOException | URISyntaxException | UnsupportedOperationException e) {
			rhpLog.error("Cannot open Tool Documentation (" + url + "): " + e.getMessage());
		}
	}

	/** Adresse de l'hyperlien de la vignette, ou null si l'hyperlien ou son adresse manque. */
	protected String documentationUrl() {
		IRPProject project = rhApp.activeProject();
		IRPModelElement link = project == null ? null : project.findElementByGUID(DOCUMENTATION_LINK_GUID);
		if (!(link instanceof IRPHyperLink)) {
			return null;
		}
		String url = ((IRPHyperLink) link).getURL();
		return url == null || url.isBlank() ? null : url.trim();
	}

	/**
	 * Ouvre l'adresse dans le navigateur par defaut du poste. Isolee pour les tests,
	 * qui la remplacent afin de ne pas lancer de navigateur.
	 */
	protected void browse(String url) throws IOException, URISyntaxException {
		if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
			Desktop.getDesktop().browse(new URI(url));
			return;
		}
		// JVM sans java.awt.Desktop : navigateur par defaut de Windows
		new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();
	}

	@Override
	public boolean isUndoable() {
		// ouverture d'une page web : rien a annuler
		return false;
	}
}
