package main;

import com.telelogic.rhapsody.core.IRPApplication;
import com.telelogic.rhapsody.core.IRPDiagram;
import com.telelogic.rhapsody.core.IRPModelElement;
import com.telelogic.rhapsody.core.IRPProject;
import com.telelogic.rhapsody.core.RPApplicationListener;
import com.telelogic.rhapsody.core.RhapsodyAppServer;

import logging.RhapsodyLogger;
import metamodel.Flow;
import metamodel.FunctionWithReference;
import metamodel.ModelElement;
import metamodel.ReferencesFunction;
import metamodel.SafranFactory;

public class ApplicationListener extends RPApplicationListener {

    private final IRPApplication rhpApplication;
    private final SafranFactory factory;

    private static RhapsodyLogger rhpLog = RhapsodyLogger.getInstance();

    public ApplicationListener(IRPApplication rhpApp) {
        this.rhpApplication = rhpApp;
        this.connect(this.rhpApplication);
        this.factory = new SafranFactory();
    }

    @Override
    public String getId() {
        return "Safran Application Listener";
    }

    @Override
    public boolean afterAddElement(IRPModelElement pModelElement) {
        if (pModelElement == null) return false;
        // Si l'élément créé est un HyperLink, ouvrir automatiquement sa fenêtre Features.
        if (isHyperLink(pModelElement)) {
            try {
                // 0 = réutilise une fenêtre Features ouverte, ou en ouvre une nouvelle si nécessaire.
                pModelElement.openFeaturesDialog(0);
                rhpLog.info("[AppListener] Features opened for HyperLink: "
                        + pModelElement.getFullPathName());
            } catch (Exception e) {
                rhpLog.warn("[AppListener] Cannot open Features for HyperLink: "
                        + e.getMessage());
            }
        }
        // BESOIN A — Redéfinition des ports à la création d'un lien de référence
        // ("References Function" / "References Logical System"). Déclenché ici
        // via test getUserDefinedMetaClass() (robuste, indépendant du nom du New Term).
        if (ReferencesFunction.isReferenceLink(pModelElement)) {
            IRPApplication app = (rhpApplication != null)
                    ? rhpApplication
                    : RhapsodyAppServer.getActiveRhapsodyApplication();
            ReferencesFunction.handleCreated(pModelElement, app);
        }

        ModelElement created = factory.create(pModelElement);
        if (created == null) return false;

        created.afterAdded();
        return true;
    }

    private boolean isHyperLink(IRPModelElement modelElement) {
    try {
        // Vérifie la métaclasse Rhapsody de l'élément créé.
        String metaClass = modelElement.getMetaClass();

        // Vérifie aussi la métaclasse utilisateur si elle existe.
        String userMetaClass = modelElement.getUserDefinedMetaClass();

        return "HyperLink".equals(metaClass)
                || "Hyperlink".equals(metaClass)
                || "HyperLink".equals(userMetaClass)
                || "Hyperlink".equals(userMetaClass);

    } catch (Exception e) {
        // En cas d'erreur API Rhapsody, on considère que ce n'est pas un HyperLink.
        return false;
    }
}

	@Override
    public boolean onSelectionChanged() {
        IRPApplication app = (rhpApplication != null)
                ? rhpApplication
                : RhapsodyAppServer.getActiveRhapsodyApplication();

        if (app == null) return false;

        // 1) Flow : pump des reowners différés (existant).
        try {
            if (Flow.hasPendingReowners()) {
                Flow.pumpReowners(app);
            }
        } catch (Exception e) {
            rhpLog.warn("[AppListener] Flow pump failed: " + e.getMessage());
        }

        // 2) BESOIN A — ReferencesFunction : retry des réconciliations de ports
        //    différées (extrémités du lien pas prêtes au moment de la création).
        try {
            if (ReferencesFunction.hasPending()) {
                ReferencesFunction.pumpPending(app);
            }
        } catch (Exception e) {
            rhpLog.warn("[AppListener] ReferencesFunction pump failed: " + e.getMessage());
        }

        // 3) BESOIN B — FunctionWithReference : masquage des ports hérités des
        //    références déposées dans un diagramme (drop inféré via la sélection ;
        //    multi-sélection gérée à l'intérieur). Fast-exit interne si aucune
        //    sélection graphique.
        try {
            FunctionWithReference.processDroppedSelection(app);
        } catch (Exception e) {
            rhpLog.warn("[AppListener] FunctionWithReference masking failed: " + e.getMessage());
        }

        return false;
    }

    // --- (return false) ---
    @Override public boolean beforeProjectOpen(String projectPath) { return false; }
    @Override public boolean afterProjectOpen(IRPProject pProject) { return false; }
    @Override public boolean afterProjectClose(String bstrProjectName) { return false; }
    @Override public boolean beforeProjectClose(IRPProject pProject) { return false; }
    @Override public boolean onDiagramOpen(IRPDiagram pDiagram) { return false; }
    @Override public boolean onFeaturesOpen(IRPModelElement pModelElement) { return false; }
    @Override public boolean onElementsChanged(String GUIDs) { return false; }
    @Override public boolean beforeDeleteElement(IRPModelElement modelElement) { return false; }

	@Override
	public boolean onDoubleClick(IRPModelElement pModelElement) {
		// TODO Auto-generated method stub
		return false;
	}
}
