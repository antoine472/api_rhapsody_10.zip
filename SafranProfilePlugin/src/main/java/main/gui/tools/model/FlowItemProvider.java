package main.gui.tools.model;

/**
 * Seam between the Rhapsody API and the Swing dialog.
 * Production implementation: {@link RhapsodyFlowItemScanner}.
 * Test implementation: a simple lambda or stub returning pre-built data.
 */
@FunctionalInterface
public interface FlowItemProvider {
    /** Called on the Rhapsody plugin thread (T1). Never from the EDT. */
    FlowItemScanResult scan();
}
