package test.unittest.rules;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import tools.rules.PortPropagationRules;
import tools.rules.PortPropagationRules.Containment;

class FlowDirectionRulesTest {

    @Test
    void clickedInOut_setsBidirectional() {
        String d = PortPropagationRules.computeFlowDirectionFromClickedPort(
                "InOut", "Out", true,
                false, false
        );
        assertEquals(PortPropagationRules.FLOW_BIDIR, d);
    }

    @Test
    void outToIn_setsTowardOther() {
        // clicked = end1, clicked Out, other In => direction toward other = toEnd2
        String d = PortPropagationRules.computeFlowDirectionFromClickedPort(
                "Out", "In", true,
                false, false
        );
        assertEquals(PortPropagationRules.FLOW_TO_END2, d);
    }

    @Test
    void inToOut_setsTowardClicked() {
        // clicked = end1, clicked In, other Out => direction toward clicked = toEnd1
        String d = PortPropagationRules.computeFlowDirectionFromClickedPort(
                "In", "Out", true,
                false, false
        );
        assertEquals(PortPropagationRules.FLOW_TO_END1, d);
    }

    @Test
    void ambiguous_outOut_withContainment_clickedIsAncestor_srcOut_goesToAncestor() {
        // clicked end1, clicked ancestor, src Out => toward ancestor => toward clicked => toEnd1
        String d = PortPropagationRules.computeFlowDirectionFromClickedPort(
                "Out", "Out", true,
                true, true
        );
        assertEquals(PortPropagationRules.FLOW_TO_END1, d);
    }

    @Test
    void ambiguous_outOut_withContainment_clickedIsDescendant_srcOut_goesToAncestor() {
        // clicked end1, clicked descendant => ancestor = other => toward other => toEnd2
        String d = PortPropagationRules.computeFlowDirectionFromClickedPort(
                "Out", "Out", true,
                true, false
        );
        assertEquals(PortPropagationRules.FLOW_TO_END2, d);
    }

    @Test
    void ambiguous_inIn_withContainment_clickedIsAncestor_srcIn_goesToDescendant() {
        // clicked ancestor => descendant = other => toward other => toEnd2
        String d = PortPropagationRules.computeFlowDirectionFromClickedPort(
                "In", "In", true,
                true, true
        );
        assertEquals(PortPropagationRules.FLOW_TO_END2, d);
    }

    @Test
    void ambiguous_inIn_withContainment_clickedIsDescendant_srcIn_goesToDescendant() {
        // clicked descendant => descendant = clicked => toward clicked => toEnd1
        String d = PortPropagationRules.computeFlowDirectionFromClickedPort(
                "In", "In", true,
                true, false
        );
        assertEquals(PortPropagationRules.FLOW_TO_END1, d);
    }

    @Test
    void otherIsInOut_noContainment_followClickedRule() {
        // other InOut, src Out => toward other
        String d = PortPropagationRules.computeFlowDirectionFromClickedPort(
                "Out", "InOut", true,
                false, false
        );
        assertEquals(PortPropagationRules.FLOW_TO_END2, d);

        // other InOut, src In => toward clicked
        String d2 = PortPropagationRules.computeFlowDirectionFromClickedPort(
                "In", "InOut", true,
                false, false
        );
        assertEquals(PortPropagationRules.FLOW_TO_END1, d2);
    }

    @Test
    void ambiguous_withoutContainment_returnsNull() {
        assertNull(PortPropagationRules.computeFlowDirectionFromClickedPort(
                "Out", "", true,
                false, false
        ));
        assertNull(PortPropagationRules.computeFlowDirectionFromClickedPort(
                "In", "In", true,
                false, false
        ));
    }

    @Test
    void containmentDetection_worksOnPaths() {
        Containment c1 = PortPropagationRules.containmentOf(
                "A::B::p",          // container [A,B]
                "A::B::C::q"        // container [A,B,C]
        );
        assertEquals(Containment.CLICKED_ANCESTOR, c1);

        Containment c2 = PortPropagationRules.containmentOf(
                "A::B::C::p",
                "A::B::q"
        );
        assertEquals(Containment.OTHER_ANCESTOR, c2);

        Containment c3 = PortPropagationRules.containmentOf(
                "A::B::p",
                "A::C::q"
        );
        assertEquals(Containment.NONE, c3);
    }
}