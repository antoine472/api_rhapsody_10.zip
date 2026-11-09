package metamodel;

import java.util.ArrayList;
import java.util.List;

import com.telelogic.rhapsody.core.IRPApplication;
import com.telelogic.rhapsody.core.IRPCollection;
import com.telelogic.rhapsody.core.IRPGraphElement;
import com.telelogic.rhapsody.core.IRPModelElement;

/**
 * BESOIN B — Correction du bug d'affichage au drag &amp; drop.
 *
 * <p>Quand une ou plusieurs références ({@code FunctionWithReference} /
 * {@code LogicalSystemReference}) sont déposées dans un diagramme, Rhapsody
 * affiche EN DOUBLE les ports redéfinis et les ports hérités de la classe mère.
 * On remplace ici chaque port hérité dessiné par son port redéfini, à la même
 * place (le port modèle hérité reste, seul son graphique quitte le diagramme).
 * Les flows dessinés sur le port remplacé sont pris en charge : un flow de la
 * définition est copié sur le port redéfini et jamais modifié, un flow déjà
 * rattaché à une référence est reconnecté ; ces changements de modèle sont
 * faits dans une transaction undo ({@link RhapsodyTx}). Rien n'est supprimé ni
 * renommé.</p>
 *
 * <p><b>Déclenchement</b> : {@code RPApplicationListener} n'expose aucun callback
 * « drag &amp; drop » ; le drop est <i>inféré</i> via
 * {@code ApplicationListener.onSelectionChanged()} (le/les nœud(s) déposé(s)
 * devien(nen)t la sélection graphique). Multi-sélection gérée.</p>
 *
 * <p><b>Exécution différée</b> : le retrait est programmé ~400 ms plus tard sur
 * un timer (hors callback), pour laisser Rhapsody finir de dessiner les ports du
 * drop — un scan trop précoce ne trouverait rien à retirer.</p>
 *
 * <p><b>Pas de cache "déjà traité"</b> : le retrait est idempotent (une fois les
 * ports hérités retirés, un re-scan ne trouve rien et ne fait rien). Un cache par
 * élément+diagramme empêchait le nettoyage lors d'un re-drop du même élément et
 * transformait un raté transitoire en échec permanent. Chaque sélection d'une
 * référence re-déclenche donc un scan : soit no-op, soit correcteur
 * (auto-réparant).</p>
 *
 * <p>La logique de retrait graphique est portée par {@link RedefinedPortsService}.</p>
 */
public class FunctionWithReference extends ModelElement {

    /** Timer démon partagé : exécute le retrait hors callback, après le rendu du drop. */
    private static final java.util.Timer ASYNC =
            new java.util.Timer("SafranPortHiding", true);

    /** Délai avant scan (laisse Rhapsody finir de dessiner les ports déposés). */
    private static final long ASYNC_DELAY_MS = 400;

    public FunctionWithReference(IRPModelElement modelElement) {
        super(modelElement);
    }

    // afterAdded() hérité : no-op volontaire (le masquage est graphique, au drop).

    // ------------------------------------------------------------------
    // Entrée listener (appelée depuis ApplicationListener.onSelectionChanged)
    // ------------------------------------------------------------------

    /**
     * Capture les références présentes dans la sélection graphique courante et
     * programme le retrait de leurs ports hérités (hors callback, différé).
     */
    public static void processDroppedSelection(IRPApplication app) {
        if (app == null) return;

        IRPCollection selection;
        try {
            selection = app.getSelectedGraphElements();
        } catch (Exception e) {
            return;
        }
        if (selection == null) return;

        List<?> items;
        try {
            items = selection.toList();
        } catch (Exception e) {
            return;
        }
        // Fast exit : rien de sélectionné graphiquement (cas ultra-fréquent).
        if (items == null || items.isEmpty()) return;

        // Capture immédiate des candidats (références uniquement).
        final List<IRPGraphElement> candidates = new ArrayList<IRPGraphElement>();
        for (Object o : items) {
            if (!(o instanceof IRPGraphElement)) continue;
            IRPGraphElement ge = (IRPGraphElement) o;

            IRPModelElement mo = safeModelObject(ge);
            if (mo == null) continue;

            // Filtre : uniquement les classes de référence
            // (portant une généralisation "References Function" / "References Logical System").
            if (!RedefinedPortsService.isReferenceClass(mo)) continue;

            candidates.add(ge);
        }
        if (candidates.isEmpty()) return;

        // Retrait différé, hors callback (les ports du drop finissent de se
        // dessiner pendant le délai). Idempotent : re-scan = no-op si déjà propre.
        try {
            ASYNC.schedule(new java.util.TimerTask() {
                @Override
                public void run() {
                    for (IRPGraphElement ge : candidates) {
                        try {
                            RedefinedPortsService.hideRedefinedPorts(app, ge);
                        } catch (Exception e) {
                            rhpLog.warn("[FunctionWithReference] deferred hide failed: "
                                    + e.getMessage());
                        }
                    }
                }
            }, ASYNC_DELAY_MS);
        } catch (Exception e) {
            rhpLog.warn("[FunctionWithReference] cannot schedule hide: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static IRPModelElement safeModelObject(IRPGraphElement ge) {
        try { return ge.getModelObject(); } catch (Exception e) { return null; }
    }
}
