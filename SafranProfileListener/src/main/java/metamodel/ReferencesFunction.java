package metamodel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.telelogic.rhapsody.core.IRPApplication;
import com.telelogic.rhapsody.core.IRPGeneralization;
import com.telelogic.rhapsody.core.IRPGraphElement;
import com.telelogic.rhapsody.core.IRPModelElement;
import com.telelogic.rhapsody.core.IRPProject;
import com.telelogic.rhapsody.core.RhapsodyAppServer;

/**
 * BESOIN A — Redéfinition automatique des ports à la création d'un lien de
 * référence ({@code "References Function"} / {@code "References Logical System"},
 * métaclasse {@code Generalization}).
 *
 * <p>Déclenché explicitement depuis
 * {@code ApplicationListener.afterAddElement(...)} via
 * {@link #handleCreated(IRPModelElement, IRPApplication)} (test
 * {@code getUserDefinedMetaClass()}, indépendant du nom interne du stéréotype),
 * puis délègue la logique de redéfinition à {@link RedefinedPortsService}.</p>
 *
 * <p>Cette classe reste instanciable par {@link SafranFactory} par cohérence,
 * mais {@code afterAdded()} est volontairement un no-op : le déclenchement passe
 * par {@code handleCreated} pour éviter toute double exécution et rester robuste
 * quel que soit le nom du New Term.</p>
 *
 * <p>Exécution : la redéfinition est TOUJOURS différée hors des callbacks
 * listener via le timer {@link #ASYNC} (voir sa javadoc — sinon le browser
 * n'est pas notifié des créations). Si l'extrémité dérivée du lien n'est pas
 * encore résolue, la file {@code PENDING} re-tente (timer + filet de sécurité
 * {@code onSelectionChanged}).</p>
 */
public class ReferencesFunction extends ModelElement {

    private static final String REF_FUNCTION = "References Function";
    private static final String REF_LOGICAL_SYSTEM = "References Logical System";

    /** File des redéfinitions différées : GUID de la généralisation -> nb de tentatives. */
    private static final Map<String, Integer> PENDING = new HashMap<>();
    private static final int MAX_ATTEMPTS = 15;
    private static boolean PUMPING = false;

    /**
     * Timer applicatif (thread démon) utilisé pour exécuter la redéfinition EN
     * DEHORS des callbacks RPApplicationListener.
     *
     * <p>Pourquoi : une mutation faite pendant un callback de notification
     * (afterAddElement, onSelectionChanged, ...) est exécutée alors que Rhapsody
     * déroule sa boucle d'événements interne ; les événements « élément créé »
     * produits par le plugin sont avalés et le browser n'est jamais notifié
     * (ports créés mais invisibles dans l'arborescence). Le même code exécuté
     * hors callback — comme le prototype PriseEnMain en client COM externe —
     * déclenche la mise à jour automatique du browser. Il n'existe d'ailleurs
     * aucune API « refreshBrowser » : Rhapsody compte sur ces événements.</p>
     */
    private static final java.util.Timer ASYNC =
            new java.util.Timer("SafranPortRedefinition", true);

    /** Délai avant exécution hors-callback (laisse Rhapsody terminer sa notification). */
    private static final long ASYNC_DELAY_MS = 400;

    public ReferencesFunction(IRPModelElement modelElement) {
        super(modelElement);
    }

    // afterAdded() hérité : no-op volontaire (cf. javadoc de classe).

    // ------------------------------------------------------------------
    // Entrée listener (appelée depuis ApplicationListener.afterAddElement)
    // ------------------------------------------------------------------

    /** Vrai si l'élément créé est un lien de référence géré. */
    public static boolean isReferenceLink(IRPModelElement created) {
        if (!(created instanceof IRPGeneralization)) return false;
        try {
            String udm = created.getUserDefinedMetaClass();
            return REF_FUNCTION.equals(udm) || REF_LOGICAL_SYSTEM.equals(udm);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Traite la création d'un lien de référence : redéfinit les ports de la
     * classe dérivée (la référence). Si l'extrémité dérivée n'est pas prête,
     * met en file pour re-tentative.
     */
    public static void handleCreated(IRPModelElement created, IRPApplication app) {
        if (!isReferenceLink(created)) return;
        // On NE redéfinit JAMAIS pendant un callback listener (afterAddElement,
        // onSelectionChanged, ...) : les événements de modèle émis par nos
        // créations y sont avalés et le browser n'est pas notifié (cf. ASYNC).
        // On enfile puis on programme l'exécution HORS callback via le timer.
        enqueue(safeGuid(created));
        scheduleAsyncPump(app);
    }

    /** Programme un pump hors-callback (thread timer), avec l'app fournie. */
    private static void scheduleAsyncPump(final IRPApplication app) {
        final IRPApplication target = (app != null)
                ? app
                : safeActiveApp();
        try {
            ASYNC.schedule(new java.util.TimerTask() {
                @Override
                public void run() {
                    try {
                        pumpPending(target);
                    } catch (Exception e) {
                        rhpLog.warn("[ReferencesFunction] async pump failed: " + e.getMessage());
                    }
                }
            }, ASYNC_DELAY_MS);
        } catch (Exception e) {
            rhpLog.warn("[ReferencesFunction] cannot schedule async pump: " + e.getMessage());
            // Filet de sécurité : le pump de onSelectionChanged reprendra la file.
        }
    }

    private static IRPApplication safeActiveApp() {
        try { return RhapsodyAppServer.getActiveRhapsodyApplication(); }
        catch (Exception e) { return null; }
    }

    /**
     * @return true si la redéfinition est complète ; false si elle doit être
     *         RE-TENTÉE (extrémités du lien pas prêtes, ou échec COM transitoire
     *         pendant que Rhapsody est occupé — fréquent quand l'utilisateur
     *         enchaîne les créations). L'opération étant idempotente, la
     *         re-tentative ne crée aucun doublon.
     */
    private static boolean redefine(IRPApplication app, IRPGeneralization gen) {
        IRPModelElement derived = safeDerived(gen); // la référence (FunctionWithReference / LogicalSystemReference)
        if (derived == null) {
            rhpLog.warn("[ReferencesFunction] derived class not ready yet -> defer");
            return false;
        }

        // Redéfinition encadrée par une transaction undo (annulable en un Ctrl+Z).
        // Exécutée hors callback (thread ASYNC) : les événements de création sont
        // émis normalement et le browser se met à jour tout seul, de façon
        // incrémentale. Surtout NE PAS appeler allowBrowserRefresh(1) ici : cet
        // appel déclenche une reconstruction globale de l'arbre (le browser
        // entier "bouge").
        final boolean[] complete = { false };
        boolean txOk = RhapsodyTx.mutate(app, "ReferencesFunction.redefine",
                () -> complete[0] = RedefinedPortsService.redefinePorts(derived));

        // Cas "l'élément est DÉJÀ posé dans un diagramme au moment où le lien
        // est créé (depuis l'arbo)" : Rhapsody affiche alors ports hérités +
        // ports redéfinis. On programme un nettoyage des représentations
        // graphiques existantes de la référence (best-effort : en cas de raté,
        // le scan auto-réparant au clic reste le filet de sécurité).
        if (txOk && complete[0]) {
            scheduleDiagramCleanup(app, derived);
        }
        return txOk && complete[0];
    }

    /**
     * Programme (hors callback, après un délai laissant Rhapsody dessiner les
     * ports fraîchement créés) le remplacement, sur TOUTES les représentations
     * existantes de la référence en diagramme, de chaque port hérité dessiné
     * par son port redéfini à la même place ; les flows de la définition
     * dessinés dessus sont copiés sur le port redéfini, les flows de référence
     * reconnectés (transaction undo propre à chaque diagramme, voir
     * {@link RedefinedPortsService#hideRedefinedPorts(IRPApplication, List)}).
     */
    private static void scheduleDiagramCleanup(final IRPApplication app,
                                               final IRPModelElement reference) {
        try {
            ASYNC.schedule(new java.util.TimerTask() {
                @Override
                public void run() {
                    try {
                        List<IRPGraphElement> representations =
                                RedefinedPortsService.getGraphicalRepresentations(reference);
                        if (!representations.isEmpty()) {
                            RedefinedPortsService.hideRedefinedPorts(app, representations);
                        }
                    } catch (Exception e) {
                        rhpLog.warn("[ReferencesFunction] diagram cleanup failed: " + e.getMessage());
                    }
                }
            }, ASYNC_DELAY_MS);
        } catch (Exception e) {
            rhpLog.warn("[ReferencesFunction] cannot schedule diagram cleanup: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // File différée + pump (rejoué depuis onSelectionChanged)
    // ------------------------------------------------------------------

    public static boolean hasPending() {
        synchronized (ReferencesFunction.class) {
            return !PENDING.isEmpty();
        }
    }

    private static synchronized void enqueue(String genGuid) {
        if (genGuid == null) return;
        PENDING.putIfAbsent(genGuid, 0);
    }

    public static void pumpPending(IRPApplication app) {
        if (app == null) return;

        synchronized (ReferencesFunction.class) {
            if (PENDING.isEmpty() || PUMPING) return;
            PUMPING = true;
        }

        try {
            IRPProject prj = safeActiveProject(app);

            List<String> guids;
            synchronized (ReferencesFunction.class) {
                guids = new ArrayList<>(PENDING.keySet());
            }

            for (String g : guids) {
                boolean done = true;

                if (prj != null) {
                    IRPModelElement el = safeFind(prj, g);
                    if (el instanceof IRPGeneralization) {
                        done = redefine(app, (IRPGeneralization) el);
                    }
                    // sinon : lien supprimé -> done = true (retiré de la file)
                }

                synchronized (ReferencesFunction.class) {
                    if (done) {
                        PENDING.remove(g);
                    } else {
                        int attempts = PENDING.getOrDefault(g, 0) + 1;
                        if (attempts >= MAX_ATTEMPTS) {
                            rhpLog.error("[ReferencesFunction] Redefinition aborted (max attempts) genGuid=" + g);
                            PENDING.remove(g);
                        } else {
                            PENDING.put(g, attempts);
                        }
                    }
                }
            }
        } finally {
            synchronized (ReferencesFunction.class) {
                PUMPING = false;
            }
        }

        // Re-tentatives restantes (ex. derived pas encore résolu) : re-programmer
        // un pump hors-callback plutôt que d'attendre un onSelectionChanged.
        if (hasPending()) {
            scheduleAsyncPump(app);
        }
    }

    public static void clearPendingForTests() {
        synchronized (ReferencesFunction.class) {
            PENDING.clear();
            PUMPING = false;
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static IRPModelElement safeDerived(IRPGeneralization gen) {
        try { return gen.getDerivedClass(); } catch (Exception e) { return null; }
    }

    private static IRPProject safeActiveProject(IRPApplication app) {
        try { return app.activeProject(); } catch (Exception e) { return null; }
    }

    private static IRPModelElement safeFind(IRPProject prj, String guid) {
        try { return prj.findElementByGUID(guid); } catch (Exception e) { return null; }
    }

    private static String safeGuid(IRPModelElement el) {
        try { return el != null ? el.getGUID() : null; } catch (Exception e) { return null; }
    }
}
