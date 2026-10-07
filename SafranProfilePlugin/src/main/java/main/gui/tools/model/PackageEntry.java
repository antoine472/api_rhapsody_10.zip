package main.gui.tools.model;

import com.telelogic.rhapsody.core.IRPModelElement;

/**
 * Immutable snapshot of a Flow Package that can own newly created Flow Items.
 * Used to populate the "Create in:" combo box. {@code element} is null in unit tests.
 */
public record PackageEntry(
        String          key,
        String          name,
        String          fullPath,
        IRPModelElement element   // null in unit tests
) {
    /** JComboBox uses toString() for the label. */
    @Override
    public String toString() { return name; }
}
