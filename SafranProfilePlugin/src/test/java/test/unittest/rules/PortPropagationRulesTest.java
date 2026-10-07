package test.unittest.rules;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import tools.rules.PortPropagationRules;
import tools.rules.PortPropagationRules.Containment;

public class PortPropagationRulesTest {

    // ---------------------------------------------------------------------
    // normalizeDir
    // ---------------------------------------------------------------------

    @Test
    void normalizeDir_shouldHandleNullBlankAndUnspecified() {
        assertNull(PortPropagationRules.normalizeDir(null));
        assertNull(PortPropagationRules.normalizeDir(""));
        assertNull(PortPropagationRules.normalizeDir("   "));
        assertNull(PortPropagationRules.normalizeDir("Unspecified"));
        assertNull(PortPropagationRules.normalizeDir("  Unspecified  "));
    }

    @Test
    void normalizeDir_shouldNormalizeInOutVariants() {
        assertEquals("In", PortPropagationRules.normalizeDir("in"));
        assertEquals("Out", PortPropagationRules.normalizeDir("OUT"));
        assertEquals("InOut", PortPropagationRules.normalizeDir("InOut"));

        // variantes robustes (doivent passer si tu as appliqué le patch)
        assertEquals("InOut", PortPropagationRules.normalizeDir("IN_OUT"));
        assertEquals("InOut", PortPropagationRules.normalizeDir("In_Out"));
        assertEquals("InOut", PortPropagationRules.normalizeDir("In/Out"));
        assertEquals("InOut", PortPropagationRules.normalizeDir("In-Out"));
        assertEquals("InOut", PortPropagationRules.normalizeDir("In Out"));
    }

    // ---------------------------------------------------------------------
    // containerPath + containmentOf
    // ---------------------------------------------------------------------

    @Test
    void containerPath_shouldSupportClassicDoubleColon() {
        // ...::Owner::port
        List<String> p = PortPropagationRules.containerPath("a::b::Owner::port");
        assertEquals(List.of("a", "b", "Owner"), p);
    }

    @Test
    void containerPath_shouldSupportOwnerDotPortInLastSegment() {
        // ...::Owner.port
        List<String> p = PortPropagationRules.containerPath("a::b::Owner.port");
        assertEquals(List.of("a", "b", "Owner"), p);
    }

    @Test
    void containerPath_shouldSupportOwnerDotPortWithoutDoubleColon() {
        // Owner.port
        List<String> p = PortPropagationRules.containerPath("Owner.port");
        assertEquals(List.of("Owner"), p);
    }

    @Test
    void containmentOf_shouldDetectClickedAncestorOrOtherAncestor() {
        // clicked ancestor of other
        Containment c1 = PortPropagationRules.containmentOf(
                "a::b::Parent.portA",
                "a::b::Parent::Child.portB"
        );
        assertEquals(Containment.CLICKED_ANCESTOR, c1);

        // other ancestor of clicked
        Containment c2 = PortPropagationRules.containmentOf(
                "a::b::Parent::Child.portA",
                "a::b::Parent.portB"
        );
        assertEquals(Containment.OTHER_ANCESTOR, c2);

        // none
        Containment c3 = PortPropagationRules.containmentOf(
                "a::b::X.portA",
                "a::b::Y.portB"
        );
        assertEquals(Containment.NONE, c3);
    }

    // ---------------------------------------------------------------------
    // computeTargetPortDirection
    // Branches à couvrir :
    // - srcDir null/blank => null
    // - xor border => flip=false
    // - sameDepth => flip=true
    // - sameBranch containment => flip=false
    // - else => flip=true
    // + InOut ne flip pas
    // ---------------------------------------------------------------------

    @Test
    void computeTargetPortDirection_shouldReturnNullWhenSrcDirMissing() {
        assertNull(PortPropagationRules.computeTargetPortDirection(
                null, false, false, "a::b::X.port", "a::b::Y.port"
        ));
        assertNull(PortPropagationRules.computeTargetPortDirection(
                "   ", false, false, "a::b::X.port", "a::b::Y.port"
        ));
        assertNull(PortPropagationRules.computeTargetPortDirection(
                "Unspecified", false, false, "a::b::X.port", "a::b::Y.port"
        ));
    }

    @Test
    void computeTargetPortDirection_xorBorder_shouldNotFlip() {
        // srcBorder ^ tgtBorder => flip=false => target=src
        assertEquals("Out", PortPropagationRules.computeTargetPortDirection(
                "Out", true, false, "a::b::X.port", "a::b::Y.port"
        ));
        assertEquals("In", PortPropagationRules.computeTargetPortDirection(
                "In", false, true, "a::b::X.port", "a::b::Y.port"
        ));
    }

    @Test
    void computeTargetPortDirection_sameDepth_shouldFlip() {
        // même profondeur (owners path même taille), pas xor => flip=true
        // a::b::X.port  vs a::b::Y.port => containerPath size == 3
        assertEquals("In", PortPropagationRules.computeTargetPortDirection(
                "Out", false, false, "a::b::X.port", "a::b::Y.port"
        ));
        assertEquals("Out", PortPropagationRules.computeTargetPortDirection(
                "In", false, false, "a::b::X.port", "a::b::Y.port"
        ));
    }

    @Test
    void computeTargetPortDirection_sameBranchContainment_shouldNotFlip() {
        // clicked (src) est ancêtre de tgt => sameBranch=true et sameDepth=false => flip=false
        assertEquals("Out", PortPropagationRules.computeTargetPortDirection(
                "Out", false, false,
                "a::b::Parent.portA",
                "a::b::Parent::Child.portB"
        ));

        // et l'inverse : tgt ancêtre de src => sameBranch=true => flip=false
        assertEquals("In", PortPropagationRules.computeTargetPortDirection(
                "In", false, false,
                "a::b::Parent::Child.portA",
                "a::b::Parent.portB"
        ));
    }

    @Test
    void computeTargetPortDirection_differentBranchDifferentDepth_shouldFlip() {
        // pas xor, pas sameDepth, pas sameBranch => flip=true
        assertEquals("In", PortPropagationRules.computeTargetPortDirection(
                "Out", false, false,
                "a::b::P1::C1.port",
                "a::b::P2.port"
        ));
    }

    @Test
    void computeTargetPortDirection_inOutShouldNeverFlip() {
        assertEquals("InOut", PortPropagationRules.computeTargetPortDirection(
                "InOut", false, false, "a::b::X.port", "a::b::Y.port"
        ));
        assertEquals("InOut", PortPropagationRules.computeTargetPortDirection(
                "IN_OUT", false, false, "a::b::X.port", "a::b::Y.port"
        ));
    }

    // ---------------------------------------------------------------------
    // computeFlowDirectionFromClickedPort
    // Branches :
    // 1) src null/blank => null
    // 2) src InOut => bidirectional
    // 3) non ambigu Out/In et In/Out
    // 4) ambiguity + containment
    // 5) no containment + other InOut
    // 6) ambiguity => null
    // ---------------------------------------------------------------------

    @Test
    void computeFlowDirection_srcMissing_returnsNull() {
        assertNull(PortPropagationRules.computeFlowDirectionFromClickedPort(
                null, "In", true, false, false
        ));
        assertNull(PortPropagationRules.computeFlowDirectionFromClickedPort(
                "   ", "In", true, false, false
        ));
        assertNull(PortPropagationRules.computeFlowDirectionFromClickedPort(
                "Unspecified", "In", true, false, false
        ));
    }

    @Test
    void computeFlowDirection_srcInOut_returnsBidirectional() {
        assertEquals(PortPropagationRules.FLOW_BIDIR,
                PortPropagationRules.computeFlowDirectionFromClickedPort(
                        "InOut", "Out", true, false, false
                )
        );
    }

    @Test
    void computeFlowDirection_nonAmbiguous_outToIn() {
        // clicked = end1
        assertEquals(PortPropagationRules.FLOW_TO_END2,
                PortPropagationRules.computeFlowDirectionFromClickedPort(
                        "Out", "In", true, false, false
                )
        );

        // clicked = end2 => towardOther = toEnd1
        assertEquals(PortPropagationRules.FLOW_TO_END1,
                PortPropagationRules.computeFlowDirectionFromClickedPort(
                        "Out", "In", false, false, false
                )
        );
    }

    @Test
    void computeFlowDirection_nonAmbiguous_inWithOtherOut_meansTowardClicked() {
        // clicked = end1 => towardClicked = toEnd1
        assertEquals(PortPropagationRules.FLOW_TO_END1,
                PortPropagationRules.computeFlowDirectionFromClickedPort(
                        "In", "Out", true, false, false
                )
        );

        // clicked = end2 => towardClicked = toEnd2
        assertEquals(PortPropagationRules.FLOW_TO_END2,
                PortPropagationRules.computeFlowDirectionFromClickedPort(
                        "In", "Out", false, false, false
                )
        );
    }

    @Test
    void computeFlowDirection_ambiguous_useContainment_srcOut_goesToAncestor() {
        // clicked=end1
        // clicked ancestor => flow toward clicked
        assertEquals(PortPropagationRules.FLOW_TO_END1,
                PortPropagationRules.computeFlowDirectionFromClickedPort(
                        "Out", "Out", true,
                        true,  // isContainment
                        true   // clickedIsAncestor
                )
        );

        // clicked descendant => flow toward other (ancestor)
        assertEquals(PortPropagationRules.FLOW_TO_END2,
                PortPropagationRules.computeFlowDirectionFromClickedPort(
                        "Out", "Out", true,
                        true,
                        false  // clickedIsAncestor=false => other ancestor
                )
        );
    }

    @Test
    void computeFlowDirection_ambiguous_useContainment_srcIn_goesToDescendant() {
        // clicked=end1
        // clicked ancestor => descendant = other => towardOther
        assertEquals(PortPropagationRules.FLOW_TO_END2,
                PortPropagationRules.computeFlowDirectionFromClickedPort(
                        "In", "In", true,
                        true,
                        true
                )
        );

        // clicked descendant => descendant = clicked => towardClicked
        assertEquals(PortPropagationRules.FLOW_TO_END1,
                PortPropagationRules.computeFlowDirectionFromClickedPort(
                        "In", "In", true,
                        true,
                        false
                )
        );
    }

    @Test
    void computeFlowDirection_noContainment_otherInOut_followClickedRule() {
        // other InOut
        // src Out => towardOther
        assertEquals(PortPropagationRules.FLOW_TO_END2,
                PortPropagationRules.computeFlowDirectionFromClickedPort(
                        "Out", "InOut", true, false, false
                )
        );
        // src In => towardClicked
        assertEquals(PortPropagationRules.FLOW_TO_END1,
                PortPropagationRules.computeFlowDirectionFromClickedPort(
                        "In", "InOut", true, false, false
                )
        );
    }

    @Test
    void computeFlowDirection_ambiguous_noContainment_returnsNull() {
        assertNull(PortPropagationRules.computeFlowDirectionFromClickedPort(
                "Out", "Out", true, false, false
        ));
        assertNull(PortPropagationRules.computeFlowDirectionFromClickedPort(
                "In", "In", true, false, false
        ));
        assertNull(PortPropagationRules.computeFlowDirectionFromClickedPort(
                "Out", null, true, false, false
        ));
        assertNull(PortPropagationRules.computeFlowDirectionFromClickedPort(
                "In", "Unspecified", true, false, false
        ));
    }

    // ---------------------------------------------------------------------
    // Unknown / unusable values
    // ---------------------------------------------------------------------

    @Test
    void unknownDirection_isKeptAsIs_andNeverFlipped() {
        assertEquals("Sideways", PortPropagationRules.normalizeDir(" Sideways "));
        assertEquals("Sideways", PortPropagationRules.flipDirSafe("Sideways"));
        assertEquals("InOut", PortPropagationRules.flipDirSafe("in-out"));
        assertNull(PortPropagationRules.flipDirSafe("None"));
        assertEquals("Sideways", PortPropagationRules.computeTargetPortDirection(
                "Sideways", false, false, "A::B.p1", "A::C.p2"));
    }

    @Test
    void computeFlowDirection_unknownClickedDirection_withContainment_returnsNull() {
        assertNull(PortPropagationRules.computeFlowDirectionFromClickedPort(
                "Sideways", "In", true, true, true));
    }

    @Test
    void computeFlowDirectionForBorderPair_unusableDirections_returnNull() {
        assertNull(PortPropagationRules.computeFlowDirectionForBorderPair(
                "Unspecified", "Sideways", true, true));
        assertNull(PortPropagationRules.computeFlowDirectionForBorderPair(
                null, null, false, false));
    }

    @Test
    void containerPath_degenerateNames_giveEmptyOrWholePrefix() {
        assertEquals(List.of(), PortPropagationRules.containerPath(null));
        assertEquals(List.of(), PortPropagationRules.containerPath("  "));
        assertEquals(List.of(), PortPropagationRules.containerPath("port"));
        assertEquals(List.of(), PortPropagationRules.containerPath(".port"));
        assertEquals(List.of(), PortPropagationRules.containerPath("Owner."));
        assertEquals(List.of("Pkg"), PortPropagationRules.containerPath("Pkg::Owner."));
    }

    @Test
    void isPrefixPath_nullsAndLongerPrefix_areFalse() {
        assertFalse(PortPropagationRules.isPrefixPath(null, List.of("A")));
        assertFalse(PortPropagationRules.isPrefixPath(List.of("A"), null));
        assertFalse(PortPropagationRules.isPrefixPath(List.of("A", "B"), List.of("A")));
        assertTrue(PortPropagationRules.isPrefixPath(List.of(), List.of("A")));
    }
}