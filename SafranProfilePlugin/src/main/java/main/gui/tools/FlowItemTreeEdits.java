package main.gui.tools;

import java.util.ArrayList;
import java.util.List;

import com.telelogic.rhapsody.core.IRPModelElement;

import main.gui.tools.model.FlowItemEntry;

/**
 * Pure working-model updates for the Flow Item selector tree.
 *
 * <p>When a Flow Item is renamed or moved, the tree is rebuilt from each
 * entry's {@code pkgPath}. Because a Flow Item's children carry that item's
 * name inside their own {@code pkgPath}, updating only the renamed/moved entry
 * would orphan its descendants under a phantom folder bearing the old name.
 * {@link #relocate} rewrites the item <em>and</em> every descendant so the
 * composition/hierarchy is preserved.</p>
 */
public final class FlowItemTreeEdits {

    private FlowItemTreeEdits() {}

    /**
     * Returns a copy of {@code entries} in which the entry identified by
     * {@code itemKey} and all of its descendants are relocated from
     * {@code oldFullPath} to {@code newFullPath}, and the item itself is renamed
     * to {@code newName}. Entries outside the subtree are returned unchanged.
     *
     * @param oldFullPath full path of the item before the edit (parent path + old name)
     * @param newFullPath full path of the item after the edit  (parent path + new name)
     */
    public static List<FlowItemEntry> relocate(
            List<FlowItemEntry> entries, String itemKey,
            List<String> oldFullPath, List<String> newFullPath, String newName) {
        return relocate(entries, itemKey, oldFullPath, newFullPath, newName, null);
    }

    /**
     * Same as {@link #relocate(List, String, List, List, String)}, additionally
     * carrying the model elements of the new parent path ({@code newParentElements},
     * aligned with {@code newFullPath} minus its last segment; may be null or
     * shorter, missing slots become null) so the relocated item and its
     * descendants keep the ancestor elements used to draw container icons.
     */
    public static List<FlowItemEntry> relocate(
            List<FlowItemEntry> entries, String itemKey,
            List<String> oldFullPath, List<String> newFullPath, String newName,
            List<IRPModelElement> newParentElements) {

        List<String> newParent = newFullPath.subList(0, newFullPath.size() - 1);
        List<IRPModelElement> parentElems = new ArrayList<>(newParent.size());
        for (int i = 0; i < newParent.size(); i++) {
            parentElems.add(newParentElements != null && i < newParentElements.size()
                    ? newParentElements.get(i) : null);
        }

        // Element of the relocated item: its descendants' paths pass through it.
        IRPModelElement itemElement = null;
        for (FlowItemEntry e : entries) {
            if (e.key().equals(itemKey)) { itemElement = e.element(); break; }
        }

        List<FlowItemEntry> out = new ArrayList<>(entries.size());

        for (FlowItemEntry e : entries) {
            if (e.key().equals(itemKey)) {
                // The item itself: new parent path + new name.
                out.add(new FlowItemEntry(e.key(), newName, e.stereotypeLabel(),
                        new ArrayList<>(newParent), e.element(), parentElems));
            } else if (startsWith(e.pkgPath(), oldFullPath)) {
                // A descendant: swap the old subtree prefix for the new one.
                List<String> np = new ArrayList<>(newFullPath);
                np.addAll(e.pkgPath().subList(oldFullPath.size(), e.pkgPath().size()));
                List<IRPModelElement> ne = new ArrayList<>(parentElems);
                ne.add(itemElement);
                ne.addAll(e.pathElements().subList(oldFullPath.size(), e.pathElements().size()));
                out.add(new FlowItemEntry(e.key(), e.name(), e.stereotypeLabel(), np, e.element(), ne));
            } else {
                out.add(e);
            }
        }
        return out;
    }

    private static boolean startsWith(List<String> path, List<String> prefix) {
        if (path.size() < prefix.size()) return false;
        return path.subList(0, prefix.size()).equals(prefix);
    }
}
