package metamodel;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.telelogic.rhapsody.core.IRPApplication;
import com.telelogic.rhapsody.core.IRPCollection;
import com.telelogic.rhapsody.core.IRPDiagram;
import com.telelogic.rhapsody.core.IRPFlow;
import com.telelogic.rhapsody.core.IRPGraphElement;
import com.telelogic.rhapsody.core.IRPModelElement;
import com.telelogic.rhapsody.core.IRPProject;
import com.telelogic.rhapsody.core.IRPSysMLPort;
import com.telelogic.rhapsody.core.RhapsodyAppServer;
import com.telelogic.rhapsody.core.RhapsodyRuntimeException;

public class Flow extends ModelElement {

    protected final IRPFlow flow;

    // DEV trace toggle: -Dsafran.flow.trace=true
    private static final String TRACE_PROP = "safran.flow.trace";

    // Reowner queue
    private static final Map<String, ReownerJob> JOBS = new HashMap<>();
    private static final int MAX_ATTEMPTS = 15;
    private static boolean PUMPING = false;

    public Flow(IRPFlow modelElement) {
        super(modelElement);
        this.flow = modelElement;
    }

    @Override
    public void afterAdded() {
        super.afterAdded();

        dev("afterAdded flow=" + describe(flow)
                + " owner=" + describe(safeOwner(flow))
                + " end1=" + describe(safeEnd(flow, 1))
                + " end2=" + describe(safeEnd(flow, 2)));

        // 1) Duplicate check only when BOTH ends are SysML ports
        if (bothEndsArePorts(flow)) {
            checkDuplicateOnPortsWithDialog();
        } else {
            dev("dupCheck skipped (ends not both ports)");
        }

        // 2) Reowner (opt-in by subclass)
        if (!needsDiagramReowner()) return;

        IRPApplication app = safeActiveApp();
        if (app == null) {
            warnUser("[Flow] Reowner skipped: no active IRPApplication.");
            return;
        }

        IRPDiagram creationDiagram = resolveCreationDiagramStrict(app, flow);
        if (creationDiagram == null) {
            warnUser("[Flow] Reowner skipped: cannot resolve creation diagram reliably.");
            return;
        }

        IRPModelElement desiredOwner = safeOwner(creationDiagram);
        if (desiredOwner == null) {
            warnUser("[Flow] Reowner skipped: diagram owner is null. diagram=" + safePath(creationDiagram));
            return;
        }

        IRPModelElement currentOwner = safeOwner(flow);
        if (currentOwner != null && currentOwner.equals(desiredOwner)) {
            dev("already under desired owner => nothing to do");
            return;
        }

        // (A) Handle name collision BEFORE trying to move
        ensureUniqueNameUnderOwner(desiredOwner, flow);

        // (B) Try immediate setOwner (fast path)
        boolean movedNow = safeUndoMutate(app, "setOwner-immediate", () -> flow.setOwner(desiredOwner));

        if (movedNow) {
            IRPModelElement after = safeOwner(flow);
            if (after != null && after.equals(desiredOwner)) {
                infoUser("[Flow] Flow moved to: " + safePath(desiredOwner) + " (name=" + safeName(flow) + ")");
                return;
            }
            // Not stable yet => fall back to queue
            dev("owner not stable after immediate setOwner => enqueue");
        } else {
            warnUser("[Flow] Immediate reowner failed (may be locked). Will retry.");
        }

        // (C) Plan B: enqueue for retry
        enqueueReowner(flow.getGUID(), desiredOwner.getGUID(), safePath(creationDiagram));
    }

    /**
     * Subclasses opt-in for reowner-by-diagram behavior.
     */
    protected boolean needsDiagramReowner() {
        return false;
    }

    // ---------------------------------------------------------------------
    // Duplicate check (ports only) + user dialog
    // ---------------------------------------------------------------------

    private void checkDuplicateOnPortsWithDialog() {
        try {
            IRPSysMLPort port1 = asPort(safeEnd(flow, 1));
            IRPSysMLPort port2 = asPort(safeEnd(flow, 2));
            if (port1 == null || port2 == null) return;

            List<?> refs = port1.getReferences().toList();
            for (Object obj : refs) {
                if (!(obj instanceof IRPFlow)) continue;

                IRPFlow existing = (IRPFlow) obj;
                if (existing.equals(flow)) continue;

                Flow createdFlow = new Flow(flow);
                Flow existingFlow = new Flow(existing);

                if (createdFlow.equals(existingFlow)) {
                    warnUser("[Flow] Duplicate detected: " + safePath(existing) + " <-> " + safePath(flow));

                    JFrame jf = new JFrame();
                    jf.setAlwaysOnTop(true);

                    int response = JOptionPane.showConfirmDialog(jf,
                            "A similar flow already exists: " + existing.getFullPathName() + "\n" +
                                    "Do you want to delete the newly created flow?",
                            "Confirm Deletion",
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.QUESTION_MESSAGE);

                    jf.dispose();

                    if (response == JOptionPane.YES_OPTION) {
                        flow.deleteFromProject();
                        JOptionPane.showMessageDialog(null, "Duplicate flow deleted.");
                        return;
                    } else {
                        JOptionPane.showMessageDialog(null, "Duplicate flow not deleted.");
                    }
                }
            }
        } catch (Exception e) {
            errorUser("[Flow] Duplicate check failed: " + e.getMessage());
            dev("duplicate check exception: " + e);
        }
    }

    private static boolean bothEndsArePorts(IRPFlow f) {
        return asPort(safeEnd(f, 1)) != null && asPort(safeEnd(f, 2)) != null;
    }

    private static IRPSysMLPort asPort(IRPModelElement el) {
        return (el instanceof IRPSysMLPort) ? (IRPSysMLPort) el : null;
    }

    // ---------------------------------------------------------------------
    // equals/hashCode : only valid when BOTH ends are SysML ports
    // ---------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Flow)) return false;

        Flow other = (Flow) o;

        IRPSysMLPort thisSource = asPort(safeEnd(flow, 1));
        IRPSysMLPort thisTarget = asPort(safeEnd(flow, 2));
        IRPSysMLPort otherSource = asPort(safeEnd(other.flow, 1));
        IRPSysMLPort otherTarget = asPort(safeEnd(other.flow, 2));

        if (thisSource == null || thisTarget == null || otherSource == null || otherTarget == null) return false;
        return thisSource.equals(otherSource) && thisTarget.equals(otherTarget);
    }

    @Override
    public int hashCode() {
        IRPSysMLPort source = asPort(safeEnd(flow, 1));
        IRPSysMLPort target = asPort(safeEnd(flow, 2));
        if (source == null || target == null) return 0;
        return 31 * source.hashCode() + target.hashCode();
    }

    // ---------------------------------------------------------------------
    // Diagram resolution (strict + safe)
    // ---------------------------------------------------------------------

    /**
     * Strictly resolve the creation diagram:
     * 1) If selected element == this flow => use getDiagramOfSelectedElement()
     * 2) Else if selected graph element maps to this flow => use that diagram
     * 3) Else fallback: if exactly ONE diagram references the flow => use it
     * Otherwise => null (too risky)
     */
    private static IRPDiagram resolveCreationDiagramStrict(IRPApplication app, IRPFlow f) {
        // 1) Strong signal: selected element is the flow
        try {
            IRPModelElement sel = app.getSelectedElement();
            if (sel != null && sel.equals(f)) {
                IRPDiagram d = app.getDiagramOfSelectedElement();
                if (d != null) return d;
            }
        } catch (Exception ignore) {}

        // 2) Selected graph element that maps to this flow
        try {
            IRPCollection selGraph = app.getSelectedGraphElements();
            if (selGraph != null) {
                for (Object o : selGraph.toList()) {
                    if (o instanceof IRPGraphElement) {
                        IRPGraphElement ge = (IRPGraphElement) o;
                        IRPModelElement mo = ge.getModelObject();
                        if (mo != null && mo.equals(f)) {
                            IRPDiagram d = ge.getDiagram();
                            if (d != null) return d;
                        }
                    }
                }
            }
        } catch (Exception ignore) {}

        // 3) Fallback: exactly one referencing diagram
        try {
            @SuppressWarnings("unchecked")
            List<IRPModelElement> refs = f.getReferences().toList();
            IRPDiagram single = null;
            int count = 0;
            for (IRPModelElement ref : refs) {
                if (ref instanceof IRPDiagram) {
                    count++;
                    single = (IRPDiagram) ref;
                }
            }
            if (count == 1) return single;
        } catch (Exception ignore) {}

        return null;
    }

    // ---------------------------------------------------------------------
    // Name collision handling
    // ---------------------------------------------------------------------

    private static void ensureUniqueNameUnderOwner(IRPModelElement desiredOwner, IRPFlow f) {
        String baseName = safeName(f);
        if (baseName == null) return;

        IRPModelElement collision = findNameCollision(desiredOwner, baseName, f);
        if (collision == null) return;

        int k = 1;
        String candidate;
        do {
            candidate = baseName + "__moved_" + k;
            k++;
        } while (findNameCollision(desiredOwner, candidate, f) != null);

        infoUser("[Flow] Auto-rename to avoid collision: '" + baseName + "' -> '" + candidate + "'");
        try {
            f.setName(candidate);
        } catch (Exception e) {
            warnUser("[Flow] Rename failed: " + e.getMessage());
        }
    }

    private static IRPModelElement findNameCollision(IRPModelElement owner, String name, IRPModelElement self) {
        if (owner == null || name == null) return null;

        // Fast: same metaclass
        try {
            String meta = self.getMetaClass();
            IRPModelElement found = owner.findNestedElement(name, meta);
            if (found != null && !found.equals(self)) return found;
        } catch (Exception ignore) {}

        // Safe scan: cross-metaclass collision
        try {
            IRPCollection nested = owner.getNestedElements();
            if (nested != null) {
                for (Object o : nested.toList()) {
                    if (o instanceof IRPModelElement el) {
                        if (name.equals(safeName(el)) && !el.equals(self)) return el;
                    }
                }
            }
        } catch (Exception ignore) {}

        return null;
    }

    // ---------------------------------------------------------------------
    // Reowner queue + pump (Plan B)
    // ---------------------------------------------------------------------

    private static class ReownerJob {
        final String flowGuid;
        final String ownerGuid;
        final String diagramPath;
        int attempts = 0;
        long nextTryAtMs = 0;
        boolean userWarned = false;

        ReownerJob(String flowGuid, String ownerGuid, String diagramPath) {
            this.flowGuid = flowGuid;
            this.ownerGuid = ownerGuid;
            this.diagramPath = diagramPath;
            this.nextTryAtMs = System.currentTimeMillis();
        }
    }

    public static boolean hasPendingReowners() {
        synchronized (Flow.class) { return !JOBS.isEmpty(); }
    }

    public static synchronized void enqueueReowner(String flowGuid, String ownerGuid, String diagramPath) {
        if (flowGuid == null || ownerGuid == null) return;

        ReownerJob existing = JOBS.get(flowGuid);
        if (existing == null) {
            JOBS.put(flowGuid, new ReownerJob(flowGuid, ownerGuid, diagramPath));
            dev("enqueueReowner flowGuid=" + flowGuid + " ownerGuid=" + ownerGuid + " diagram=" + diagramPath);
        } else if (!ownerGuid.equals(existing.ownerGuid)) {
            JOBS.put(flowGuid, new ReownerJob(flowGuid, ownerGuid, diagramPath));
            warnUser("[Flow] Target owner changed during creation; reowner updated.");
        }
    }

    /**
     * Called from ApplicationListener.onSelectionChanged() (filtered).
     */
    public static void pumpReowners(IRPApplication app) {
        if (app == null) return;

        // Re-entrance guard + fast exit
        synchronized (Flow.class) {
            if (JOBS.isEmpty()) return;
            if (PUMPING) return;
            PUMPING = true;
        }

        try {
            List<ReownerJob> toProcess = new ArrayList<>();
            synchronized (Flow.class) {
                long now = System.currentTimeMillis();
                for (ReownerJob j : JOBS.values()) {
                    if (j.nextTryAtMs <= now) toProcess.add(j);
                }
            }

            for (ReownerJob job : toProcess) {
                boolean done = tryReownerOnce(app, job);
                synchronized (Flow.class) {
                    if (done) {
                        JOBS.remove(job.flowGuid);
                    } else {
                        job.attempts++;
                        if (job.attempts >= MAX_ATTEMPTS) {
                            errorUser("[Flow] Reowner aborted (max attempts). flowGuid=" + job.flowGuid);
                            JOBS.remove(job.flowGuid);
                        } else {
                            job.nextTryAtMs = System.currentTimeMillis() + (1000L * (job.attempts + 1));
                        }
                    }
                }
            }
        } finally {
            synchronized (Flow.class) { PUMPING = false; }
        }
    }

    private static boolean tryReownerOnce(IRPApplication app, ReownerJob job) {
        IRPProject p = safeActiveProject(app);
        if (p == null) return false;

        IRPModelElement fEl = p.findElementByGUID(job.flowGuid);
        IRPModelElement oEl = p.findElementByGUID(job.ownerGuid);

        if (!(fEl instanceof IRPFlow)) return true; // flow deleted
        if (oEl == null) return true; // owner deleted

        IRPFlow f = (IRPFlow) fEl;

        IRPModelElement currentOwner = safeOwner(f);
        if (currentOwner != null && currentOwner.equals(oEl)) return true;

        // Name collision again (rare but safe)
        ensureUniqueNameUnderOwner(oEl, f);

        boolean ok = safeUndoMutate(app, "setOwner-pump", () -> f.setOwner(oEl));
        if (!ok) {
            if (!job.userWarned) {
                warnUser("[Flow] Reowner pending (Rhapsody may lock element). Will retry.");
                job.userWarned = true;
            }
            dev("pump setOwner failed flowGuid=" + job.flowGuid + " diagram=" + job.diagramPath
                    + " app.error=" + safeAppError(app));
            return false;
        }

        IRPModelElement after = safeOwner(f);
        boolean stable = (after != null && after.equals(oEl));
        if (stable) {
            infoUser("[Flow] Flow moved (retry) to: " + safePath(oEl) + " (name=" + safeName(f) + ")");
            return true;
        }
        return false;
    }

    // ---------------------------------------------------------------------
    // Safe undo wrapper
    // ---------------------------------------------------------------------

    @FunctionalInterface
    private interface Mutator { void run(); }

    private static boolean safeUndoMutate(IRPApplication app, String label, Mutator m) {
        boolean txStarted = false;
        try {
            try {
                app.startUndoTransaction();
                String startErr = safeAppError(app);
                if (startErr == null || startErr.isEmpty()) txStarted = true;
                else dev(label + ": tx not created: " + startErr);
            } catch (Exception ex) {
                dev(label + ": startUndoTransaction threw: " + ex.getMessage());
            }

            m.run();
            String opErr = safeAppError(app);
            if (opErr != null && !opErr.isEmpty()) dev(label + ": app.errorMessage=" + opErr);

            return true;

        } catch (RhapsodyRuntimeException ex) {
            dev(label + " failed: " + ex.getMessage());
            return false;

        } finally {
            if (txStarted) {
                try { app.endUndoTransaction(); } catch (Exception ex) { dev(label + ": end tx failed: " + ex.getMessage()); }
            }
        }
    }

    private static String safeAppError(IRPApplication app) {
        try { return app.errorMessage(); } catch (Exception e) { return ""; }
    }

    // ---------------------------------------------------------------------
    // Utilities
    // ---------------------------------------------------------------------

    private static IRPApplication safeActiveApp() {
        try { return RhapsodyAppServer.getActiveRhapsodyApplication(); }
        catch (Exception e) { return null; }
    }

    private static IRPProject safeActiveProject(IRPApplication app) {
        try { return app.activeProject(); }
        catch (Exception e) { return null; }
    }

    private static IRPModelElement safeOwner(IRPModelElement el) {
        try { return el != null ? el.getOwner() : null; }
        catch (Exception e) { return null; }
    }

    private static IRPModelElement safeEnd(IRPFlow f, int which) {
        try {
            if (f == null) return null;
            return (which == 1) ? f.getEnd1() : f.getEnd2();
        } catch (Exception e) {
            return null;
        }
    }

    private static String safeName(IRPModelElement el) {
        try { return el != null ? el.getName() : null; }
        catch (Exception e) { return null; }
    }

    private static String safePath(IRPModelElement el) {
        try { return el != null ? el.getFullPathName() : "null"; }
        catch (Exception e) { return "<?>"; }
    }

    private static String describe(IRPModelElement el) {
        if (el == null) return "null";
        try {
            return "{type=" + el.getClass().getName()
                    + ", name=" + el.getName()
                    + ", path=" + el.getFullPathName()
                    + ", guid=" + el.getGUID()
                    + "}";
        } catch (Exception e) {
            return "{type=" + el.getClass().getName() + "}";
        }
    }

    // ---------------------------------------------------------------------
    // Logging helpers (user vs dev)
    // ---------------------------------------------------------------------

    private static boolean isTraceEnabled() {
        try { return "true".equalsIgnoreCase(System.getProperty(TRACE_PROP)); }
        catch (Exception e) { return false; }
    }

    private static void dev(String msg) {
        if (isTraceEnabled()) rhpLog.debug("[Flow:DEV] " + msg);
    }

    private static void infoUser(String msg) {
        rhpLog.info(msg);
    }

    private static void warnUser(String msg) {
        rhpLog.warn(msg);
    }

    private static void errorUser(String msg) {
        rhpLog.error(msg);
    }

    public static synchronized void enqueueReowner(String flowGuid, String ownerGuid) {
        enqueueReowner(flowGuid, ownerGuid, "<unknown>");
    }

    public static void clearPendingReownersForTests() {
        synchronized (Flow.class) {
            JOBS.clear();
            PUMPING = false; // au cas où
        }
    }
}
