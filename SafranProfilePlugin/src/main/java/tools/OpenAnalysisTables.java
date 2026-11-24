package tools;

import com.telelogic.rhapsody.core.IRPApplication;
import com.telelogic.rhapsody.core.IRPDiagram;
import com.telelogic.rhapsody.core.IRPModelElement;
import com.telelogic.rhapsody.core.IRPProject;

/**
 * Bouton de la barre d'outils "Open Analysis Tables" : ouvre la page
 * Monitoring::Analysis Tables du profil, comme "Return home" ouvre la page Home.
 *
 * <p>La page est retrouvee par son GUID au moment du clic (et non au chargement
 * du plugin) : le bouton fonctionne meme si le profil est charge apres le plugin,
 * et le rangement du package Monitoring ne change pas le GUID.</p>
 */
public class OpenAnalysisTables extends RhapsodyTool {

	/** Nom de la commande (name55 du fichier .hep). */
	public static final String COMMAND = "Open Analysis Tables";

	/** GUID du diagramme Monitoring::Analysis Tables du profil. */
	public static final String ANALYSIS_TABLES_GUID = "GUID 91028bb9-491f-4a5b-97ce-bfb0e0e3e344";

	public OpenAnalysisTables(IRPApplication rpyApp) {
		super(rpyApp);
	}

	@Override
	public String commandName() {
		return COMMAND;
	}

	@Override
	public void execute() {
		IRPProject project = rhApp.activeProject();
		IRPModelElement page = project == null ? null : project.findElementByGUID(ANALYSIS_TABLES_GUID);
		// Profil absent ou page supprimee : message, sans exception
		if (!(page instanceof IRPDiagram)) {
			rhpLog.error("Analysis Tables page is not present in the project.");
			return;
		}
		rhpLog.debug("Open Analysis Tables page requested.");
		((IRPDiagram) page).openDiagram();
	}

	@Override
	public boolean isUndoable() {
		// simple navigation : rien a annuler
		return false;
	}
}
