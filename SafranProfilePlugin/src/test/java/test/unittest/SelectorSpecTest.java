package test.unittest;

import org.junit.jupiter.api.Test;

import main.gui.tools.SelectorSpec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure unit tests of the selector configuration ({@link SelectorSpec}).
 */
class SelectorSpecTest {

    @Test
    void constructor_blankTitleAndTypeLabel_getDefaults() {
        SelectorSpec spec = new SelectorSpec("  ", null, "Function", null);

        assertEquals("Select", spec.title());
        assertEquals("item", spec.typeLabel());
        assertNull(spec.onApply(), "onApply is optional on the record (the dialog requires it)");
        assertFalse(spec.anyContainerIsTarget());
    }

    @Test
    void canCreateType_onlyWithANonBlankMetaClass() {
        assertTrue(new SelectorSpec("T", null, "Function", "Function").canCreateType());
        assertFalse(new SelectorSpec("T", null, " ", "Function").canCreateType());
        assertFalse(new SelectorSpec("T", null, null, "Function").canCreateType());
    }

    @Test
    void flowItem_factories_createFlowItems_withOrWithoutApply() {
        SelectorSpec.ApplyAction action = (key, element, owner) -> true;

        SelectorSpec plain = SelectorSpec.flowItem("Pick", null);
        SelectorSpec withApply = SelectorSpec.flowItem("Pick", null, action);

        assertEquals("Flow Item", plain.createMetaClass());
        assertEquals("Flow Item", plain.typeLabel());
        assertNull(plain.onApply());
        assertSame(action, withApply.onApply());
        assertFalse(withApply.anyContainerIsTarget(), "Flow Items only go into Flow Packages or items");
    }
}
