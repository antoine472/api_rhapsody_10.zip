package metamodel;

import com.telelogic.rhapsody.core.IRPApplication;
import com.telelogic.rhapsody.core.RhapsodyRuntimeException;

import logging.RhapsodyLogger;

/**
 * Petit wrapper de transaction "undo" Rhapsody, partagé par les handlers qui
 * modifient le modèle (ex. {@link ReferencesFunction}).
 *
 * <p>Toute mutation de modèle déclenchée par un listener devrait être encadrée
 * par une transaction undo, afin d'être annulable d'un seul Ctrl+Z côté
 * utilisateur et de rester cohérente si l'API lève une exception.</p>
 *
 * <p>Note : {@code Flow} possède déjà son propre wrapper interne équivalent
 * ({@code safeUndoMutate}). On ne le refactorise pas ici pour ne pas toucher au
 * code déjà validé ; cette classe sert au NOUVEAU code.</p>
 */
public final class RhapsodyTx {

    private static final RhapsodyLogger rhpLog = RhapsodyLogger.getInstance();

    private RhapsodyTx() {
    }

    @FunctionalInterface
    public interface Mutation {
        void run();
    }

    /**
     * Exécute {@code m} dans une transaction undo si {@code app} est disponible.
     *
     * @return true si la mutation s'est exécutée sans {@link RhapsodyRuntimeException},
     *         false sinon (l'appelant peut alors décider de re-tenter plus tard).
     */
    public static boolean mutate(IRPApplication app, String label, Mutation m) {
        if (app == null) {
            try {
                m.run();
                return true;
            } catch (RhapsodyRuntimeException ex) {
                rhpLog.warn("[Tx] " + label + " failed (no app): " + ex.getMessage());
                return false;
            }
        }

        boolean txStarted = false;
        try {
            try {
                app.startUndoTransaction();
                txStarted = true;
            } catch (Exception ignore) {
                // transaction non créée : on tente quand même la mutation
            }

            m.run();
            return true;

        } catch (RhapsodyRuntimeException ex) {
            rhpLog.warn("[Tx] " + label + " failed: " + ex.getMessage());
            return false;

        } finally {
            if (txStarted) {
                try {
                    app.endUndoTransaction();
                } catch (Exception ignore) {
                    // rien à faire
                }
            }
        }
    }
}
