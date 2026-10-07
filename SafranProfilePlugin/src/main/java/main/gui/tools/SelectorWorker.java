package main.gui.tools;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import javax.swing.SwingUtilities;

import com.telelogic.rhapsody.core.IRPApplication;

import logging.RhapsodyLogger;

/**
 * Serial worker of the selector dialogs: runs every model change requested
 * from a selector (OK, Apply, create, rename, delete, move) off the Swing EDT
 * and outside any Rhapsody callback, one job at a time, each in its own
 * Rhapsody undo transaction.
 *
 * <p>Why a worker thread: heavy Rhapsody API work issued from the EDT hangs,
 * and work issued inside the plugin's menu callback is only drawn once the
 * callback returns. The selector tools return from their menu command right
 * after showing the dialog, and the jobs run here afterwards (the pattern of
 * the listener's timers): Rhapsody redraws the browser and the diagrams after
 * each job, the listener sees each change, and Ctrl+Z reverts one job at a
 * time.</p>
 *
 * <p>Why one shared thread for all selectors: Rhapsody's undo transaction is
 * global, not per thread, so two selectors applying at the same time must
 * never overlap. Jobs of every open selector queue here in FIFO order.</p>
 *
 * <p>Transaction handling follows the listener's {@code Flow.safeUndoMutate}:
 * {@code startUndoTransaction}, then {@code errorMessage()} (non-blank means
 * no transaction was created and {@code endUndoTransaction} is skipped); the
 * job's failure (any Throwable) is logged and reported to the caller, and the
 * transaction is always ended when it was started. A job that changes nothing
 * (OK right after Apply on the same item) leaves an empty transaction, which
 * creates no undo entry.</p>
 */
public final class SelectorWorker {

    /** A model change run on the worker thread; the value is handed to the caller. */
    @FunctionalInterface
    public interface Job<T> {
        T run() throws Exception;
    }

    /** What a job produced: its value, or the Throwable that stopped it. */
    public record Outcome<T>(T value, Throwable error) {
        public boolean failed() {
            return error != null;
        }
    }

    private static final RhapsodyLogger LOG = RhapsodyLogger.getInstance();

    private static SelectorWorker shared;

    private final Executor jobs;
    private final Executor ui;
    private final IRPApplication app;

    /**
     * @param jobs runs the jobs, one at a time, in order (never the EDT)
     * @param ui   runs the completion callbacks (the EDT in production)
     * @param app  Rhapsody application owning the undo transactions; null runs
     *             the jobs without any transaction
     */
    SelectorWorker(Executor jobs, Executor ui, IRPApplication app) {
        this.jobs = jobs;
        this.ui = ui;
        this.app = app;
    }

    /** The worker shared by every selector of the plugin (one daemon thread). */
    public static synchronized SelectorWorker shared(IRPApplication app) {
        if (shared == null) {
            Executor executor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "SafranSelectorWorker");
                t.setDaemon(true);
                return t;
            });
            shared = new SelectorWorker(executor, SwingUtilities::invokeLater, app);
        }
        return shared;
    }

    /**
     * Queues {@code job}: it runs on the worker thread inside one undo
     * transaction, then {@code onDone} (may be null) runs on the UI executor
     * with its outcome. Never throws to the caller.
     */
    public <T> void submit(String label, Job<T> job, Consumer<Outcome<T>> onDone) {
        jobs.execute(() -> {
            Outcome<T> outcome = runInTransaction(label, job);
            if (onDone != null) {
                ui.execute(() -> onDone.accept(outcome));
            }
        });
    }

    private <T> Outcome<T> runInTransaction(String label, Job<T> job) {
        boolean started = begin(label);
        T value = null;
        Throwable error = null;
        try {
            value = job.run();
        } catch (Throwable t) {
            error = t;
            LOG.error("[Selector] " + label + " failed: " + t);
        } finally {
            if (started) end(label);
        }
        return new Outcome<T>(value, error);
    }

    private boolean begin(String label) {
        if (app == null) return false;
        try {
            app.startUndoTransaction();
            String err = safeError();
            if (err.isEmpty()) return true;
            LOG.warn("[Selector] " + label + ": undo transaction not created: " + err);
            return false;
        } catch (Exception e) {
            LOG.warn("[Selector] " + label + ": startUndoTransaction failed: " + e.getMessage());
            return false;
        }
    }

    private void end(String label) {
        try {
            app.endUndoTransaction();
            String err = safeError();
            if (!err.isEmpty()) LOG.debug("[Selector] " + label + ": endUndoTransaction: " + err);
        } catch (Exception e) {
            LOG.warn("[Selector] " + label + ": endUndoTransaction failed: " + e.getMessage());
        }
    }

    private String safeError() {
        try {
            String err = app.errorMessage();
            return err == null ? "" : err.trim();
        } catch (Exception e) {
            return "";
        }
    }
}
