package main.gui.tools;

import java.awt.Window;

import javax.swing.Icon;

import com.telelogic.rhapsody.core.IRPModelElement;

/**
 * Type-specific configuration for {@link FlowItemSelectorDialog}, so the same
 * dialog (tree + search + rename/delete/move/create) serves Flow Items,
 * Functions, Modes, etc.
 *
 * @param title           dialog title
 * @param itemIcon        icon for leaf (selectable) nodes
 * @param createMetaClass new-term / UDMC passed to {@code addNewAggr} when
 *                        creating an element ("Flow Item", "Function", …);
 *                        {@code null} or blank hides the Create row
 * @param typeLabel       human label used in messages/buttons ("Flow Item", …)
 * @param onApply         called with the tree selection by both the dialog's
 *                        "OK" and "Apply" buttons so the caller commits the
 *                        change into the model right away; "Apply" then keeps
 *                        the dialog open (Rhapsody-native OK/Apply semantics).
 *                        Required by {@link FlowItemSelectorDialog}. Runs on the
 *                        selector worker thread (never the EDT), outside any
 *                        Rhapsody callback, inside its own undo transaction,
 *                        with the entry key (GUID, to re-fetch the element on
 *                        that thread; the element proxy is the fallback for an
 *                        item created in the dialog) and the open selector on
 *                        Apply (owner for any dialog the callback shows), null
 *                        on OK. Must be idempotent and must not throw.
 * @param anyContainerIsTarget true: every container node of the tree (package,
 *                        system, function...) can receive a created or dragged
 *                        element; false: only Flow Packages and entries can
 *                        (Flow Item rules)
 */
public record SelectorSpec(String title, Icon itemIcon, String createMetaClass, String typeLabel,
        ApplyAction onApply, boolean anyContainerIsTarget) {

    /** OK / Apply action on the selected entry. */
    @FunctionalInterface
    public interface ApplyAction {
        /** @return true when the model was changed */
        boolean apply(String key, IRPModelElement element, Window owner);
    }

    public SelectorSpec {
        if (title == null || title.isBlank()) title = "Select";
        if (typeLabel == null || typeLabel.isBlank()) typeLabel = "item";
    }

    public SelectorSpec(String title, Icon itemIcon, String createMetaClass, String typeLabel) {
        this(title, itemIcon, createMetaClass, typeLabel, null, false);
    }

    /** True when this type supports in-dialog creation. */
    public boolean canCreateType() {
        return createMetaClass != null && !createMetaClass.isBlank();
    }

    /** Convenience factory for the Flow Item case. */
    public static SelectorSpec flowItem(String title, Icon icon) {
        return new SelectorSpec(title, icon, "Flow Item", "Flow Item");
    }

    /** Flow Item selector with OK / Apply: both run {@code onApply} on the selected item. */
    public static SelectorSpec flowItem(String title, Icon icon, ApplyAction onApply) {
        return new SelectorSpec(title, icon, "Flow Item", "Flow Item", onApply, false);
    }
}
