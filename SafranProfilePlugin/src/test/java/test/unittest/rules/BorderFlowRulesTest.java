package test.unittest.rules;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import tools.rules.PortPropagationRules;

class BorderFlowRulesTest {

    @Test
    void clickedBorder_out_flowTowardBorder() {
        String d = PortPropagationRules.computeFlowDirectionForBorderPair(
                "Out", "In",
                true,   // clicked=end1
                true    // clicked is border
        );
        assertEquals(PortPropagationRules.FLOW_TO_END1, d);
    }

    @Test
    void clickedBorder_in_flowTowardInternal() {
        String d = PortPropagationRules.computeFlowDirectionForBorderPair(
                "In", "Out",
                true,   // clicked=end1 (border=end1)
                true
        );
        assertEquals(PortPropagationRules.FLOW_TO_END2, d); // internal=end2
    }

    @Test
    void clickedInternal_out_flowTowardBorder() {
        String d = PortPropagationRules.computeFlowDirectionForBorderPair(
                "Out", "In",
                true,   // clicked=end1 (internal=end1 => border=end2)
                false   // clicked is internal
        );
        assertEquals(PortPropagationRules.FLOW_TO_END2, d);
    }

    @Test
    void clickedInternal_in_flowTowardInternal_clicked() {
        String d = PortPropagationRules.computeFlowDirectionForBorderPair(
                "In", "Out",
                true,   // clicked=end1 internal=end1
                false
        );
        assertEquals(PortPropagationRules.FLOW_TO_END1, d);
    }

    @Test
    void bidirectional_kept() {
        String d = PortPropagationRules.computeFlowDirectionForBorderPair(
                "InOut", "Out",
                true, true
        );
        assertEquals(PortPropagationRules.FLOW_BIDIR, d);
    }

    @Test
    void clickedUnknown_otherKnown_followOther() {
        String d = PortPropagationRules.computeFlowDirectionForBorderPair(
                "Unspecified", "Out",
                true,   // clicked=end1, clicked border
                true
        );
        // other=Out => Out => toward border => end1
        assertEquals(PortPropagationRules.FLOW_TO_END1, d);
    }

    @Test
    void bothUnknown_returnsNull() {
        assertNull(PortPropagationRules.computeFlowDirectionForBorderPair(
                "Unspecified", "None",
                true, true
        ));
    }
}