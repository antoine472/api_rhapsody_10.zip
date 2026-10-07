package utils;

import com.telelogic.rhapsody.core.IRPApplication;
import logging.RhapsodyLogger;

public final class UndoTrace {
    private static int step = 0;
    private static Integer lastCanUndo = null;
    private static Integer lastCanRedo = null;

    private UndoTrace() {}

    public static void mark(IRPApplication app, RhapsodyLogger log, String label) {
        int canUndo = safeCanUndo(app);
        int canRedo = safeCanRedo(app);
        String err = safeErrorMessage(app);

        // log toujours, ou seulement si changement (décommente selon besoin)
        // boolean changed = (lastCanUndo == null || canUndo != lastCanUndo || lastCanRedo == null || canRedo != lastCanRedo || (err != null && !err.isBlank()));
        // if (!changed) return;

        lastCanUndo = canUndo;
        lastCanRedo = canRedo;

        log.debug(String.format("[UNDO-TRACE %03d] %s | canUndo=%d canRedo=%d err='%s'",
                ++step, label, canUndo, canRedo, err));
    }

    private static int safeCanUndo(IRPApplication app) {
        try { return app.canUndo(); } catch (Exception e) { return -1; }
    }

    private static int safeCanRedo(IRPApplication app) {
        try { return app.canRedo(); } catch (Exception e) { return -1; }
    }

    private static String safeErrorMessage(IRPApplication app) {
        try { return app.errorMessage(); } catch (Exception e) { return ""; }
    }
}