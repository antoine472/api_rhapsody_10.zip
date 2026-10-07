package main.gui.tools.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.telelogic.rhapsody.core.IRPModelElement;
import com.telelogic.rhapsody.core.IRPProject;
import com.telelogic.rhapsody.core.IRPStereotype;

import main.constants.ProfileMetaClassConstants;
import main.constants.RhpMetaClassConstants;

/**
 * Scans the active Rhapsody project and builds a {@link FlowItemScanResult}.
 *
 * THREADING: must be called on the Rhapsody plugin thread (T1).
 * Never call this from the EDT or a SwingWorker background thread.
 *
 * PERFORMANCE: integrates with the session-level cache in FlowItemSelectionSupport.
 * Call that cache first; only create a new scanner instance on a cache miss.
 * Use {@link #fromCandidates} when the item list is already cached — it only rescans
 * packages (fast) and skips the item scan entirely.
 */
public final class RhapsodyFlowItemScanner implements FlowItemProvider {

    private static final String FLOW_PKG_UDMC = "F - Flow Package";

    private final IRPProject project;

    public RhapsodyFlowItemScanner(IRPProject project) {
        this.project = project;
    }

    // ── FlowItemProvider implementation ──────────────────────────────────────

    @Override
    public FlowItemScanResult scan() {
        if (project == null) return FlowItemScanResult.empty();

        // Fast-fail: no Flow Packages → nothing to show
        Map<String, PackageEntry> pkgByKey = scanFlowPackages(project);
        if (pkgByKey.isEmpty()) return FlowItemScanResult.empty();

        List<IRPModelElement> items = new ArrayList<>();
        try {
            List<?> classes = project.getNestedElementsByMetaClass(RhpMetaClassConstants.CLASS, 1).toList();
            for (Object o : classes) {
                if (!(o instanceof IRPModelElement el)) continue;
                try {
                    if (ProfileMetaClassConstants.FLOW_ITEM.equals(el.getUserDefinedMetaClass())) {
                        items.add(el);
                    }
                } catch (Exception ignore) {}
            }
        } catch (Exception ignore) {}

        List<FlowItemEntry> entries = buildEntriesFromElements(items, true);
        entries.sort(Comparator.comparing(FlowItemEntry::displayLabel, String.CASE_INSENSITIVE_ORDER));

        return new FlowItemScanResult(entries, new ArrayList<>(pkgByKey.values()), true);
    }

    // ── Static factory ────────────────────────────────────────────────────────

    /**
     * Builds a {@link FlowItemScanResult} from a pre-fetched item list (e.g., from
     * the session-level cache in {@code FlowItemSelectionSupport}) and a fresh package
     * scan. Avoids rescanning all items when they are already cached — only packages
     * are re-examined (fast, typically &lt;10 nodes).
     *
     * @param rawItems     pre-filtered list of Flow Item {@link IRPModelElement}s
     * @param project      active project — used only for the package scan (Create combo)
     * @param enableCreate if {@code true}, flowPackages is populated to show the
     *                     "Create in:" combo row; if {@code false} the row is hidden
     */
    public static FlowItemScanResult fromCandidates(
            List<IRPModelElement> rawItems, IRPProject project, boolean enableCreate) {
        if (project == null) return FlowItemScanResult.empty();

        // Package scan is only needed for the Create combo box
        Map<String, PackageEntry> pkgByKey = enableCreate ? scanFlowPackages(project) : Map.of();

        // filterByFlowPkg=false: all pre-cached items are shown even if package scan is skipped
        List<FlowItemEntry> entries = buildEntriesFromElements(rawItems, false);
        entries.sort(Comparator.comparing(FlowItemEntry::displayLabel, String.CASE_INSENSITIVE_ORDER));

        List<PackageEntry> packages = enableCreate ? new ArrayList<>(pkgByKey.values()) : List.of();
        boolean hasPkgs = enableCreate ? !pkgByKey.isEmpty() : hasFlowPackageAncestor(rawItems);
        return new FlowItemScanResult(entries, packages, hasPkgs);
    }

    /**
     * Builds a {@link FlowItemScanResult} for a pre-collected list of elements
     * (Functions, Modes, …) whose hierarchy is expressed relative to a single
     * {@code root} anchor (e.g. the represented system). Each entry's package
     * path is the chain of owners from {@code root} down to the element's parent.
     *
     * @param items        pre-filtered elements to list (already domain-scanned by the caller)
     * @param root         hierarchy anchor; owners are collected up to and including it
     * @param enableCreate if {@code true}, {@code root} is offered as a create target
     */
    public static FlowItemScanResult fromRoot(
            List<IRPModelElement> items, IRPModelElement root, boolean enableCreate) {

        List<FlowItemEntry> entries = new ArrayList<>();
        if (items != null) {
            for (IRPModelElement el : items) {
                if (el == null) continue;
                try {
                    OwnerChain chain = pathToRoot(el, root);
                    entries.add(new FlowItemEntry(
                            stableKey(el), plainName(el), buildStereoLabel(el),
                            chain.names(), el, chain.elements()));
                } catch (Exception ignore) {}
            }
        }
        entries.sort(Comparator.comparing(FlowItemEntry::displayLabel, String.CASE_INSENSITIVE_ORDER));

        List<PackageEntry> targets;
        boolean hasContainers;
        if (enableCreate && root != null) {
            targets = List.of(new PackageEntry(stableKey(root), plainName(root), safeFullPath(root), root));
            hasContainers = true;
        } else {
            targets = List.of();
            hasContainers = !entries.isEmpty();
        }
        return new FlowItemScanResult(entries, targets, hasContainers);
    }

    /**
     * Owner chain from a root down to an element's parent: segment names and, aligned
     * 1:1, the ancestor model elements (so the selector can draw each container's
     * real concept icon). Both lists are root-first.
     */
    private record OwnerChain(List<String> names, List<IRPModelElement> elements) {}

    /** Owner chain from {@code root} (inclusive) down to {@code el}'s parent. */
    private static OwnerChain pathToRoot(IRPModelElement el, IRPModelElement root) {
        List<String> path = new ArrayList<>();
        List<IRPModelElement> elems = new ArrayList<>();
        IRPModelElement cur = el.getOwner();
        int guard = 0;
        while (cur != null && guard++ < 200) {
            if (sameElement(cur, root)) { path.add(plainName(cur)); elems.add(cur); break; }
            if ("Project".equalsIgnoreCase(safeMetaClass(cur))) break;
            path.add(plainName(cur));
            elems.add(cur);
            cur = cur.getOwner();
        }
        Collections.reverse(path);
        Collections.reverse(elems);
        return new OwnerChain(path, elems);
    }

    private static boolean sameElement(IRPModelElement a, IRPModelElement b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        try {
            String ga = a.getGUID(), gb = b.getGUID();
            if (ga != null && !ga.isBlank() && ga.equals(gb)) return true;
        } catch (Exception ignore) {}
        return false;
    }

    private static String safeFullPath(IRPModelElement el) {
        try { String fp = el.getFullPathName(); if (fp != null) return fp; } catch (Exception ignore) {}
        return "";
    }

    // ── Shared scanning helpers ───────────────────────────────────────────────

    /**
     * Pre-scans all packages with UDMC == "F - Flow Package" — used only for the
     * "Create in:" combo box. Path building in {@link #buildEntriesFromElements} does
     * NOT use this map; it checks UDMC directly on each ancestor to avoid COM proxy
     * identity-hash-code mismatches between different Java wrappers of the same element.
     */
    private static Map<String, PackageEntry> scanFlowPackages(IRPProject project) {
        Map<String, PackageEntry> pkgByKey = new LinkedHashMap<>();
        try {
            List<?> pkgs = project.getNestedElementsByMetaClass(RhpMetaClassConstants.PACKAGE, 1).toList();
            for (Object o : pkgs) {
                if (!(o instanceof IRPModelElement el)) continue;
                try {
                    String udmc = el.getUserDefinedMetaClass();
                    if (!FLOW_PKG_UDMC.equalsIgnoreCase(udmc)) continue;
                    String key  = stableKey(el);
                    String name = plainName(el);
                    String fp   = "";
                    try { fp = el.getFullPathName(); } catch (Exception ignore) {}
                    pkgByKey.put(key, new PackageEntry(key, name, fp, el));
                } catch (Exception ignore) {}
            }
        } catch (Exception ignore) {}
        return pkgByKey;
    }

    /**
     * Builds {@link FlowItemEntry} objects by walking each element's owner chain
     * and checking UDMC directly — no pre-scanned map lookup.
     *
     * <p>Checking UDMC directly on each ancestor avoids the COM proxy identity-hash-code
     * mismatch bug: two Java calls on the same Rhapsody element can return different
     * Java proxy objects. If {@code getGUID()} fails, {@code stableKey()} falls back to
     * {@code System.identityHashCode}, making map lookups silently fail. Checking UDMC
     * on the live reference is always safe and mirrors how the old {@code ItemTreeSelector}
     * worked ({@code findFlowPackageRootCached}).
     *
     * @param filterByFlowPkg if {@code true}, items not nested under any Flow Package
     *                         are silently dropped (used by {@link #scan()}).
     *                         If {@code false}, all items are included with an empty
     *                         path when no Flow Package ancestor is found
     *                         (used by {@link #fromCandidates}).
     */
    private static List<FlowItemEntry> buildEntriesFromElements(
            List<IRPModelElement> items, boolean filterByFlowPkg) {

        List<FlowItemEntry> entries = new ArrayList<>();
        for (IRPModelElement el : items) {
            if (el == null) continue;
            try {
                List<String> pathReversed = new ArrayList<>();
                List<IRPModelElement> elemsReversed = new ArrayList<>();
                boolean foundFlowPkg = false;

                IRPModelElement cur = el.getOwner();
                int guard = 0;
                while (cur != null && guard++ < 100) {
                    String mc = safeMetaClass(cur);
                    if ("Project".equalsIgnoreCase(mc)) break;

                    if ("Package".equalsIgnoreCase(mc)) {
                        // Direct UDMC check — never fails due to proxy identity differences
                        String udmc = "";
                        try { udmc = cur.getUserDefinedMetaClass(); } catch (Exception ignore) {}
                        pathReversed.add(plainName(cur));
                        elemsReversed.add(cur);
                        if (FLOW_PKG_UDMC.equalsIgnoreCase(udmc)) {
                            foundFlowPkg = true;
                            break;
                        }
                    } else {
                        // Non-Package ancestor — include if it is itself a Flow Item
                        String udmc = "";
                        try { udmc = cur.getUserDefinedMetaClass(); } catch (Exception ignore) {}
                        if (ProfileMetaClassConstants.FLOW_ITEM.equals(udmc)) {
                            pathReversed.add(plainName(cur));
                            elemsReversed.add(cur);
                        }
                    }
                    cur = cur.getOwner();
                }

                if (filterByFlowPkg && !foundFlowPkg) continue;

                Collections.reverse(pathReversed);
                Collections.reverse(elemsReversed);
                entries.add(new FlowItemEntry(
                        stableKey(el),
                        plainName(el),
                        buildStereoLabel(el),
                        foundFlowPkg ? pathReversed : List.of(),
                        el,
                        foundFlowPkg ? elemsReversed : List.of()
                ));
            } catch (Exception ignore) {}
        }
        return entries;
    }

    /** Quick check: do any items in the list have at least one Flow Package ancestor? */
    private static boolean hasFlowPackageAncestor(List<IRPModelElement> items) {
        for (IRPModelElement el : items) {
            if (el == null) continue;
            try {
                IRPModelElement cur = el.getOwner();
                int guard = 0;
                while (cur != null && guard++ < 100) {
                    String mc = safeMetaClass(cur);
                    if ("Project".equalsIgnoreCase(mc)) break;
                    if ("Package".equalsIgnoreCase(mc)) {
                        try {
                            if (FLOW_PKG_UDMC.equalsIgnoreCase(cur.getUserDefinedMetaClass())) return true;
                        } catch (Exception ignore) {}
                    }
                    cur = cur.getOwner();
                }
            } catch (Exception ignore) {}
        }
        return false;
    }

    // ── Element helpers ───────────────────────────────────────────────────────

    private static String buildStereoLabel(IRPModelElement el) {
        try {
            List<String> names = new ArrayList<>();
            for (Object o : el.getStereotypes().toList()) {
                if (!(o instanceof IRPStereotype st)) continue;
                try { if (st.getIsNewTerm() == 1) continue; } catch (Exception ignore) {}
                String n = "";
                try { n = st.getName(); } catch (Exception ignore) {}
                if (n == null || n.isBlank() || ProfileMetaClassConstants.FLOW_ITEM.equalsIgnoreCase(n)) continue;
                names.add(n);
            }
            return names.isEmpty() ? "" : "<<" + String.join(", ", names) + ">>";
        } catch (Exception e) { return ""; }
    }

    /**
     * Returns the element name as displayed in Rhapsody.
     * Prefers {@code getDisplayName()} (user-visible label) with {@code getName()}
     * as fallback if the display name is blank.
     */
    private static String plainName(IRPModelElement el) {
        try { String d = el.getDisplayName();  if (d != null && !d.isBlank()) return d; } catch (Exception ignore) {}
        try { String n = el.getName();         if (n != null && !n.isBlank()) return n; } catch (Exception ignore) {}
        return "<unnamed>";
    }

    private static String stableKey(IRPModelElement el) {
        try { String g = el.getGUID();         if (g  != null && !g.isBlank())  return g;         } catch (Exception ignore) {}
        try { String fp = el.getFullPathName(); if (fp != null && !fp.isBlank()) return "fp:" + fp; } catch (Exception ignore) {}
        return "id:" + System.identityHashCode(el);
    }

    private static String safeMetaClass(IRPModelElement el) {
        try { return el.getMetaClass(); } catch (Exception e) { return ""; }
    }
}
