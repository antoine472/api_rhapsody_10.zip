package main.gui.tools.model;

import java.util.List;

/**
 * Immutable result of scanning the Rhapsody model for Flow Items.
 * Built on the Rhapsody plugin thread (T1); safe to read from the EDT.
 */
public record FlowItemScanResult(
        List<FlowItemEntry> entries,        // selectable Flow Items, sorted by displayLabel
        List<PackageEntry>  flowPackages,   // available create-targets ("F - Flow Package" nodes)
        boolean             hasFlowPackages
) {
    public FlowItemScanResult {
        entries      = List.copyOf(entries);
        flowPackages = List.copyOf(flowPackages);
    }

    public static FlowItemScanResult empty() {
        return new FlowItemScanResult(List.of(), List.of(), false);
    }
}
