package tools.strategies;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.Icon;

import com.telelogic.rhapsody.core.IRPApplication;
import com.telelogic.rhapsody.core.IRPClass;
import com.telelogic.rhapsody.core.IRPCollection;
import com.telelogic.rhapsody.core.IRPModelElement;
import com.telelogic.rhapsody.core.IRPProject;
import com.telelogic.rhapsody.core.IRPStereotype;

import logging.RhapsodyLogger;
import main.constants.ProfileMetaClassConstants;
import main.constants.RhpMetaClassConstants;
import main.gui.tools.FlowItemSelectorDialog;
import main.gui.tools.SelectorSpec;
import main.gui.tools.SelectorWorker;
import main.gui.tools.Toast;
import main.gui.tools.model.FlowItemEntry;
import main.gui.tools.model.FlowItemScanResult;
import main.gui.tools.model.RhapsodyFlowItemScanner;

public final class FlowItemSelectionSupport {

    private FlowItemSelectionSupport() {}

    // ── PERF-2: session-level cache ───────────────────────────────────────────
    // Flow Items rarely change during a session. Caching the full project scan
    // (which costs ~8 s for N=150 items) amortises the cost to a single scan per
    // project lifetime. The cache is keyed by project GUID and auto-invalidated
    // when a new Flow Item is created via the dialog.
    private static volatile String               cachedProjectGuid = null;
    private static volatile List<IRPModelElement> cachedFlowItems  = null;

    /**
     * Collects all Flow Items in the project, with session-level caching keyed
     * on the project GUID. Returns a cached list on subsequent calls for the
     * same project.
     */
    public static List<IRPModelElement> findFlowItems(IRPProject project, RhapsodyLogger log) {
        if (project == null) return List.of();

        String projectGuid = safeProjectGuid(project);

        if (projectGuid != null
                && projectGuid.equals(cachedProjectGuid)
                && cachedFlowItems != null) {
            if (log != null) log.debug("findFlowItems: " + cachedFlowItems.size() + " items (cache)");
            return cachedFlowItems;
        }

        if (log != null) log.debug("Scanning project for Flow Items (UDMC == "
                + ProfileMetaClassConstants.FLOW_ITEM + ")...");

        List<IRPModelElement> flowItems = new ArrayList<>();
        boolean scanned = false;
        try {
            List<?> allClasses = project.getNestedElementsByMetaClass(RhpMetaClassConstants.CLASS, 1).toList();
            for (Object obj : allClasses) {
                if (obj instanceof IRPClass clazz) {
                    try {
                        if (ProfileMetaClassConstants.FLOW_ITEM.equals(clazz.getUserDefinedMetaClass())) {
                            flowItems.add(clazz);
                        }
                    } catch (Exception ignore) {}
                }
            }
            scanned = true;
        } catch (Exception ex) {
            if (log != null) log.error("findFlowItems failed: " + ex.getMessage());
        }

        if (log != null) log.debug("Flow Items found: " + flowItems.size());

        List<IRPModelElement> result = Collections.unmodifiableList(flowItems);
        // Only a complete scan is cached: after a failed one (transient COM
        // error) the next call scans again instead of serving an empty list.
        if (scanned) {
            cachedProjectGuid = projectGuid;
            cachedFlowItems   = result;
        }
        return result;
    }

    /**
     * Clears the session cache. Call this when a new Flow Item has been created
     * so the next invocation rescans the project.
     */
    public static void invalidateCache() {
        cachedProjectGuid = null;
        cachedFlowItems   = null;
    }

    /**
     * OK / Apply of a Flow Item selector: assigns the chosen Flow Item to the
     * tool's target. Runs on the selector worker thread (never the EDT),
     * outside any Rhapsody callback, inside its own undo transaction. Must be
     * idempotent (OK right after Apply on the same item changes nothing) and
     * must not throw.
     */
    @FunctionalInterface
    public interface FlowItemAssignment {
        /**
         * @param target   the tool's target (message, port or flow), re-fetched
         *                 by GUID just before the call
         * @param flowItem the chosen Flow Item, re-fetched by GUID (or the
         *                 proxy of an item created in the dialog)
         * @return true when the model was changed
         */
        boolean assign(IRPModelElement target, IRPModelElement flowItem);
    }

    /**
     * Opens {@link FlowItemSelectorDialog} with OK / Apply and returns at once.
     * Both buttons run {@code assignment} on the selected Flow Item; Apply
     * keeps the dialog open, Escape / close box just close it (nothing is
     * discarded). A Flow Item created in the dialog first gets the stereotypes
     * of {@code target}, once per session.
     *
     * <p>Threading: called on the Rhapsody plugin thread (T1), which returns
     * right away. OK / Apply run later on the shared {@link SelectorWorker},
     * each in its own undo transaction, so the browser shows the change live.
     * The target and the Flow Item are re-fetched by GUID on that thread; a
     * deleted one is reported (log + toast) and nothing is changed.</p>
     *
     * <p>Any rename/delete/move/create or applied change invalidates the
     * session cache while the dialog is open, so the next
     * {@link #findFlowItems} rescans.</p>
     *
     * @param target the element the Flow Item is assigned to (and the source
     *               of the stereotypes copied onto a created Flow Item)
     */
    public static void selectAndAssignFlowItem(IRPApplication app,
                                               List<IRPModelElement> candidates,
                                               Icon icon,
                                               String title,
                                               boolean enableCreate,
                                               IRPModelElement target,
                                               boolean copyStereotypesFromSource,
                                               boolean copyNewTerms,
                                               FlowItemAssignment assignment,
                                               RhapsodyLogger log) {
        try {
            IRPProject project = app.activeProject();

            // Build scan result from cache: only rescans packages (fast),
            // reuses the pre-fetched item list from the caller.
            FlowItemScanResult scanResult =
                    RhapsodyFlowItemScanner.fromCandidates(candidates, project, enableCreate);

            // Keys of the listed Flow Items: any other key is an item created in the dialog.
            Set<String> listedKeys = new HashSet<>();
            for (FlowItemEntry e : scanResult.entries()) listedKeys.add(e.key());
            // Created items that already got the source's stereotypes (not again on OK after Apply).
            Set<String> initialized = new HashSet<>();

            String targetGuid = safeGuid(target);
            String targetLabel = describe(target);
            if (targetGuid == null && log != null) {
                log.warn("selectAndAssignFlowItem: no GUID for " + targetLabel + ", its proxy is used as is");
            }

            SelectorSpec spec = SelectorSpec.flowItem(title, icon, (key, element, owner) -> {
                if (element == null) return false;
                IRPProject prj = safeActiveProject(app);

                IRPModelElement liveTarget = target;
                if (targetGuid != null) {
                    liveTarget = findByGuid(prj, targetGuid);
                    if (liveTarget == null) {
                        reportFailure(log, "The " + targetLabel + " no longer exists.");
                        return false;
                    }
                }
                IRPModelElement liveItem = element;
                if (isGuid(key)) {
                    liveItem = findByGuid(prj, key);
                    if (liveItem == null) {
                        reportFailure(log, "The selected Flow Item no longer exists.");
                        return false;
                    }
                }

                boolean changed = false;
                if (copyStereotypesFromSource
                        && !listedKeys.contains(key) && initialized.add(key)) {
                    changed = copySimpleStereotypes(liveTarget, liveItem, copyNewTerms, log);
                }
                int failuresBefore = FAILURES.get();
                boolean assigned = assignment.assign(liveTarget, liveItem);
                // No success toast over a failure toast (same spot on screen).
                if (assigned && FAILURES.get() == failuresBefore) {
                    Toast.showToast("Flow Item definition set: " + safeName(liveItem), 3500);
                }
                return changed || assigned;
            });

            // Non-blocking: any rename/delete/move/create or apply done in the
            // dialog makes the session cache stale right away -- rescan on the
            // next open, even while this selector is still showing.
            new FlowItemSelectorDialog(scanResult, spec, SelectorWorker.shared(app))
                    .open(FlowItemSelectionSupport::invalidateCache);

        } catch (Exception ex) {
            if (log != null) log.error("selectAndAssignFlowItem failed: " + ex.getMessage());
        }
    }

    // ── Live elements ─────────────────────────────────────────────────────────

    /** True for a Rhapsody GUID key ("GUID ..."); false for a dialog-created key. */
    static boolean isGuid(String key) {
        return key != null && key.startsWith("GUID");
    }

    /** GUID of {@code el}, or null when it cannot be read. */
    static String safeGuid(IRPModelElement el) {
        try {
            String g = el != null ? el.getGUID() : null;
            return (g == null || g.isBlank()) ? null : g;
        } catch (Exception e) {
            return null;
        }
    }

    private static IRPProject safeActiveProject(IRPApplication app) {
        try { return app != null ? app.activeProject() : null; } catch (Exception e) { return null; }
    }

    /** Element of {@code project} with this GUID, or null (not a GUID, no project, deleted). */
    static IRPModelElement findByGuid(IRPProject project, String guid) {
        if (project == null || !isGuid(guid)) return null;
        try {
            return project.findElementByGUID(guid);
        } catch (Exception e) {
            return null;
        }
    }

    /** "<metaclass> <name>" of {@code el} (e.g. "SysMLPort p_in"), for messages. */
    static String describe(IRPModelElement el) {
        String kind = "element";
        try {
            String mc = el != null ? el.getMetaClass() : null;
            if (mc != null && !mc.isBlank()) kind = mc.trim();
        } catch (Exception ignore) {}
        return kind + " " + safeName(el);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Copies non-new-term stereotypes from {@code source} to {@code target},
     * skipping those already on it. If {@code copyNewTerms} is true, new-term
     * stereotypes are also copied.
     *
     * @return true when a stereotype was added
     */
    private static boolean copySimpleStereotypes(IRPModelElement source,
                                                 IRPModelElement target,
                                                 boolean copyNewTerms,
                                                 RhapsodyLogger log) {
        boolean changed = false;
        try {
            List<IRPStereotype> present = stereotypesOf(target, true);
            for (Object o : source.getStereotypes().toList()) {
                if (!(o instanceof IRPStereotype st)) continue;
                try {
                    if (!copyNewTerms && st.getIsNewTerm() == 1) continue;
                    if (containsSame(present, st)) continue;
                    target.addSpecificStereotype(st);
                    changed = true;
                } catch (Exception ignore) {}
            }
        } catch (Exception ex) {
            if (log != null) log.error("copySimpleStereotypes failed: " + ex.getMessage());
        }
        return changed;
    }

    private static String safeProjectGuid(IRPProject project) {
        try { String g = project.getGUID(); if (g != null && !g.isBlank()) return g; } catch (Exception ignore) {}
        try { return project.getFullPathName(); } catch (Exception ignore) {}
        return null;
    }

    /** Stereotypes of {@code el}; New Terms only when {@code withNewTerms}. Empty if none. */
    private static List<IRPStereotype> stereotypesOf(IRPModelElement el, boolean withNewTerms) {
        List<IRPStereotype> out = new ArrayList<>();
        IRPCollection all = el.getStereotypes();
        if (all == null) return out;
        for (Object o : all.toList()) {
            if (!(o instanceof IRPStereotype st)) continue;
            if (!withNewTerms && st.getIsNewTerm() == 1) continue;
            out.add(st);
        }
        return out;
    }

    private static boolean containsSame(List<? extends IRPModelElement> list, IRPModelElement el) {
        for (IRPModelElement x : list) {
            if (sameElement(x, el)) return true;
        }
        return false;
    }

    // ── Shared by the OK / Apply actions ──────────────────────────────────────

    /** Same Rhapsody element: same proxy or same GUID (two proxies of one element differ). */
    public static boolean sameElement(IRPModelElement a, IRPModelElement b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        try {
            String ga = a.getGUID();
            return ga != null && !ga.isBlank() && ga.equals(b.getGUID());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Makes the non-new-term stereotypes of {@code target} those of
     * {@code flowItem}: removes the others, adds the missing ones, leaves
     * new terms alone. A target already in sync is not touched. Never throws:
     * a failure is logged and shown, and the sync stops there.
     *
     * @return true when a stereotype was added or removed
     */
    public static boolean syncStereotypes(IRPModelElement target, IRPModelElement flowItem, RhapsodyLogger log) {
        boolean changed = false;
        try {
            List<IRPStereotype> wanted  = stereotypesOf(flowItem, false);
            List<IRPStereotype> current = stereotypesOf(target, false);
            for (IRPStereotype st : current) {
                if (containsSame(wanted, st)) continue;
                target.removeStereotype(st);
                changed = true;
            }
            for (IRPStereotype st : wanted) {
                if (containsSame(current, st)) continue;
                target.addSpecificStereotype(st);
                changed = true;
            }
        } catch (Exception ex) {
            reportFailure(log, "Cannot update the stereotypes of " + safeName(target), ex);
        }
        return changed;
    }

    /** Failures reported so far: an OK / Apply that reported one shows no success toast. */
    private static final AtomicInteger FAILURES = new AtomicInteger();

    /** Logs and shows (toast) a failed OK / Apply step. Never throws. */
    public static void reportFailure(RhapsodyLogger log, String message) {
        report(log, message, message);
    }

    /** Same, for a step that threw {@code t}. */
    public static void reportFailure(RhapsodyLogger log, String what, Throwable t) {
        report(log, what + ": " + t, what + ": " + t.getMessage());
    }

    private static void report(RhapsodyLogger log, String logLine, String toast) {
        FAILURES.incrementAndGet();
        if (log != null) log.error(logLine);
        try {
            Toast.showToast(toast, 4500);
        } catch (Exception ignore) {}
    }

    /**
     * Safely copies {@code name} and {@code displayName} from {@code flowItem} to {@code target}.
     * Each field is only written when the source value is non-null and non-blank
     * and differs from the target's.
     * Failures are logged individually and never propagate.
     *
     * @return true when the name or the display name was changed
     */
    public static boolean syncNames(IRPModelElement target, IRPModelElement flowItem, RhapsodyLogger log) {
        if (target == null || flowItem == null) return false;
        boolean changed = false;
        try {
            String name = null;
            try { name = flowItem.getName(); } catch (Exception ignore) {}
            if (name != null && !name.isBlank() && !name.equals(target.getName())) {
                target.setName(name);
                changed = true;
            }
        } catch (Exception ex) {
            if (log != null) log.error("syncNames: setName failed on '"
                    + safeName(target) + "': " + ex.getMessage());
        }
        try {
            String dn = null;
            try { dn = flowItem.getDisplayName(); } catch (Exception ignore) {}
            if (dn != null && !dn.isBlank() && !dn.equals(target.getDisplayName())) {
                target.setDisplayName(dn);
                changed = true;
            }
        } catch (Exception ex) {
            if (log != null) log.error("syncNames: setDisplayName failed on '"
                    + safeName(target) + "': " + ex.getMessage());
        }
        return changed;
    }

    /** Q-3: centralised safe-name helper — avoids duplication across strategy classes. */
    public static String safeName(IRPModelElement el) {
        if (el == null) return "<null>";
        try { String dn = el.getDisplayName(); if (dn != null && !dn.isBlank()) return dn; } catch (Exception ignore) {}
        try { String n  = el.getName();        if (n  != null && !n.isBlank())  return n;  } catch (Exception ignore) {}
        return "<unnamed>";
    }
}
