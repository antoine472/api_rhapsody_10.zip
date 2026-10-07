package main.gui.tools;

import com.telelogic.rhapsody.core.IRPModelElement;

/**
 * Pure, side-effect-only Rhapsody mutations used by {@link FlowItemSelectorDialog}
 * to rename, delete and move (reparent) the selected Flow Item.
 *
 * <p>Each method is a thin, defensively-guarded wrapper around a single Rhapsody
 * API call. It never throws: bad input and API failures are returned as an
 * {@link OpResult} so the Swing layer can surface a message and stay open.</p>
 *
 * <p>Threading: called on the selector worker thread ({@link SelectorWorker}),
 * never on the EDT, outside any Rhapsody callback, each call in its own undo
 * transaction: the change shows in the Rhapsody browser right away and one
 * Ctrl+Z there reverts it.</p>
 */
public final class FlowItemEditOps {

    private FlowItemEditOps() {}

    /** Outcome of a mutation: {@code ok} true on success, otherwise {@code error} explains why. */
    public record OpResult(boolean ok, String error) {
        public static OpResult success()            { return new OpResult(true, null); }
        public static OpResult failure(String error){ return new OpResult(false, error); }
    }

    /**
     * Renames {@code element} to {@code newName} (trimmed) and clears its
     * Label/DisplayName override so it follows the new Name — otherwise the
     * browser/diagrams would keep showing the old label after the rename
     * (same convention as {@code RedefinedPortsService.applyRenameToMother}:
     * a blank DisplayName means "follow the Name"). The Label sync is
     * best-effort: a failure there does not fail the rename itself.
     */
    public static OpResult rename(IRPModelElement element, String newName) {
        if (element == null) return OpResult.failure("No element to rename.");
        if (newName == null || newName.isBlank()) return OpResult.failure("Name must not be blank.");
        try {
            element.setName(newName.trim());
        } catch (Exception e) {
            return OpResult.failure("Rename failed: " + e.getMessage());
        }
        try {
            element.setDisplayName("");
        } catch (Exception ignore) {
            // Name rename already succeeded; some element kinds may not support DisplayName.
        }
        return OpResult.success();
    }

    /** Deletes {@code element} from the project. */
    public static OpResult delete(IRPModelElement element) {
        if (element == null) return OpResult.failure("No element to delete.");
        try {
            element.deleteFromProject();
            return OpResult.success();
        } catch (Exception e) {
            return OpResult.failure("Delete failed: " + e.getMessage());
        }
    }

    /** Reparents {@code element} under {@code newOwner}. */
    public static OpResult move(IRPModelElement element, IRPModelElement newOwner) {
        if (element == null) return OpResult.failure("No element to move.");
        if (newOwner == null) return OpResult.failure("No target owner selected.");
        try {
            element.setOwner(newOwner);
            return OpResult.success();
        } catch (Exception e) {
            return OpResult.failure("Move failed: " + e.getMessage());
        }
    }
}
