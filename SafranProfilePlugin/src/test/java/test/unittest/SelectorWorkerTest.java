package test.unittest;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.telelogic.rhapsody.core.IRPApplication;

import main.gui.tools.SelectorWorker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests (no live Rhapsody, no EDT) of {@link SelectorWorker}: the
 * serial worker that runs every OK / Apply / in-dialog edit of the selectors,
 * each in its own undo transaction. The job executor and the UI executor are
 * injected: a direct executor shows the transaction order, a queue shows the
 * FIFO serialisation.
 */
class SelectorWorkerTest {

    private static final Executor DIRECT = Runnable::run;

    /** Records every call made on the mocked application and the job, in order. */
    private static IRPApplication appWithoutError() {
        IRPApplication app = mock(IRPApplication.class);
        when(app.errorMessage()).thenReturn("");
        return app;
    }

    private static SelectorWorker direct(IRPApplication app) {
        return newWorker(DIRECT, DIRECT, app);
    }

    /** The constructor is package-private on purpose: tests reach it through this hook. */
    private static SelectorWorker newWorker(Executor jobs, Executor ui, IRPApplication app) {
        try {
            var ctor = SelectorWorker.class.getDeclaredConstructor(Executor.class, Executor.class, IRPApplication.class);
            ctor.setAccessible(true);
            return ctor.newInstance(jobs, ui, app);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    // -- transaction order ----------------------------------------------------

    @Test
    void job_runsBetweenStartAndEndOfItsOwnUndoTransaction() {
        IRPApplication app = appWithoutError();
        List<String> trace = new ArrayList<>();
        AtomicReference<SelectorWorker.Outcome<Boolean>> done = new AtomicReference<>();

        direct(app).submit("Apply", () -> {
            // The transaction is open while the job runs: start was called, end not yet.
            verify(app, times(1)).startUndoTransaction();
            verify(app, never()).endUndoTransaction();
            trace.add("job");
            return true;
        }, done::set);

        InOrder order = inOrder(app);
        order.verify(app).startUndoTransaction();
        order.verify(app).errorMessage();
        order.verify(app).endUndoTransaction();
        assertEquals(List.of("job"), trace);
        assertNotNull(done.get());
        assertFalse(done.get().failed());
        assertEquals(Boolean.TRUE, done.get().value());
    }

    @Test
    void eachSubmit_getsItsOwnTransaction_andNeverUndoes() {
        IRPApplication app = appWithoutError();
        SelectorWorker worker = direct(app);

        worker.submit("Apply 1", () -> true, null);
        worker.submit("Apply 2", () -> false, null);   // idempotent no-op: still its own transaction
        worker.submit("Rename", () -> "renamed", null);

        verify(app, times(3)).startUndoTransaction();
        verify(app, times(3)).endUndoTransaction();
        verify(app, never()).undo();
    }

    @Test
    void failingJob_stillEndsTheTransaction_andReportsTheError() {
        IRPApplication app = appWithoutError();
        AtomicReference<SelectorWorker.Outcome<Boolean>> done = new AtomicReference<>();

        direct(app).submit("Apply", () -> { throw new IllegalStateException("boom"); }, done::set);

        InOrder order = inOrder(app);
        order.verify(app).startUndoTransaction();
        order.verify(app).endUndoTransaction();
        assertTrue(done.get().failed());
        assertNull(done.get().value());
        assertEquals("boom", done.get().error().getMessage());
        verify(app, never()).undo();
    }

    @Test
    void transactionNotCreated_runsTheJob_withoutEndingAnything() {
        IRPApplication app = mock(IRPApplication.class);
        when(app.errorMessage()).thenReturn("Transaction was not created");
        List<String> trace = new ArrayList<>();

        direct(app).submit("Apply", () -> { trace.add("job"); return true; }, null);

        verify(app).startUndoTransaction();
        verify(app, never()).endUndoTransaction();
        assertEquals(List.of("job"), trace);
    }

    @Test
    void startThrowing_runsTheJob_withoutEndingAnything() {
        IRPApplication app = mock(IRPApplication.class);
        doThrow(new RuntimeException("no app")).when(app).startUndoTransaction();
        List<String> trace = new ArrayList<>();

        direct(app).submit("Apply", () -> { trace.add("job"); return true; }, null);

        verify(app, never()).endUndoTransaction();
        assertEquals(List.of("job"), trace);
    }

    @Test
    void endThrowing_isSwallowed_andTheOutcomeStillReachesTheCaller() {
        IRPApplication app = appWithoutError();
        doThrow(new RuntimeException("end failed")).when(app).endUndoTransaction();
        AtomicReference<SelectorWorker.Outcome<Boolean>> done = new AtomicReference<>();

        direct(app).submit("Apply", () -> true, done::set);

        assertNotNull(done.get());
        assertFalse(done.get().failed());
    }

    @Test
    void withoutApplication_jobsRunWithoutTransaction() {
        AtomicReference<SelectorWorker.Outcome<String>> done = new AtomicReference<>();

        newWorker(DIRECT, DIRECT, null).submit("Create", () -> "created", done::set);

        assertEquals("created", done.get().value());
    }

    // -- serialisation ---------------------------------------------------------

    @Test
    void jobsRunInSubmissionOrder_oneAtATime_andCompletionsFollowOnTheUiExecutor() {
        IRPApplication app = appWithoutError();
        Deque<Runnable> jobQueue = new ArrayDeque<>();
        Deque<Runnable> uiQueue = new ArrayDeque<>();
        SelectorWorker worker = newWorker(jobQueue::add, uiQueue::add, app);
        List<String> trace = new ArrayList<>();

        worker.submit("Apply A", () -> { trace.add("A"); return true; }, o -> trace.add("done A"));
        worker.submit("Apply B", () -> { trace.add("B"); return true; }, o -> trace.add("done B"));

        assertTrue(trace.isEmpty(), "nothing runs on the caller's thread");
        verify(app, never()).startUndoTransaction();

        jobQueue.pollFirst().run();
        assertEquals(List.of("A"), trace);
        verify(app, times(1)).startUndoTransaction();
        verify(app, times(1)).endUndoTransaction();
        assertEquals(1, uiQueue.size(), "completion A is queued for the UI, not run on the worker");

        jobQueue.pollFirst().run();
        assertEquals(List.of("A", "B"), trace);
        verify(app, times(2)).startUndoTransaction();
        verify(app, times(2)).endUndoTransaction();

        while (!uiQueue.isEmpty()) uiQueue.pollFirst().run();
        assertEquals(List.of("A", "B", "done A", "done B"), trace);
        verify(app, never()).undo();
    }

    @Test
    void shared_isOneWorkerForEverySelector() {
        IRPApplication app = appWithoutError();
        assertTrue(SelectorWorker.shared(app) == SelectorWorker.shared(app));
    }
}
