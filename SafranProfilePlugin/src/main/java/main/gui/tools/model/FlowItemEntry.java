package main.gui.tools.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import com.telelogic.rhapsody.core.IRPModelElement;

/**
 * Immutable snapshot of a single Flow Item, pre-fetched on the Rhapsody plugin thread.
 * All string fields are plain Java — safe to access from the EDT and from unit tests.
 * {@code element} is null in unit tests.
 *
 * <p>{@code pathElements} is aligned 1:1 with {@code pkgPath}: the model element of
 * each ancestor segment (package, Flow Item, Function, System...), or null when the
 * ancestor is unknown. The selector uses it to draw the real concept icon of every
 * container node, exactly like the Rhapsody browser.</p>
 */
public record FlowItemEntry(
        String                key,            // stable GUID or path-based fallback key
        String                name,           // display name (getDisplayName or getName)
        String                stereotypeLabel,// "«DataFlow»" or "" -- never null
        List<String>          pkgPath,        // ["FlowPkg", "SubPkg"] -- root-to-parent chain
        IRPModelElement       element,        // null in unit tests
        List<IRPModelElement> pathElements    // same size as pkgPath; entries may be null
) {
    public FlowItemEntry {
        pkgPath = List.copyOf(pkgPath);
        if (stereotypeLabel == null) stereotypeLabel = "";
        pathElements = alignedElements(pathElements, pkgPath.size());
    }

    /** Convenience constructor: ancestor elements unknown (all null). */
    public FlowItemEntry(String key, String name, String stereotypeLabel,
                         List<String> pkgPath, IRPModelElement element) {
        this(key, name, stereotypeLabel, pkgPath, element, null);
    }

    /**
     * Returns an unmodifiable list of exactly {@code size} elements: the given
     * elements (null-tolerant, unlike {@link List#copyOf}) truncated or padded
     * with null so the list always lines up with {@code pkgPath}.
     */
    private static List<IRPModelElement> alignedElements(List<IRPModelElement> src, int size) {
        List<IRPModelElement> out = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            out.add(src != null && i < src.size() ? src.get(i) : null);
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * Full label used for search matching:
     *   «DataFlow» FlowPkg › SubPkg › ItemName
     */
    public String displayLabel() {
        StringBuilder sb = new StringBuilder();
        if (!stereotypeLabel.isBlank()) sb.append(stereotypeLabel).append(' ');
        for (String seg : pkgPath) sb.append(seg).append(" \u203A ");
        sb.append(name);
        return sb.toString();
    }

    /**
     * Returns true if ALL tokens appear anywhere in the lowercase display label.
     * Empty token array always returns true (no filter active).
     */
    public boolean matches(String[] lowerTokens) {
        if (lowerTokens.length == 0) return true;
        String hay = displayLabel().toLowerCase(Locale.ROOT);
        for (String t : lowerTokens) if (!hay.contains(t)) return false;
        return true;
    }
}
