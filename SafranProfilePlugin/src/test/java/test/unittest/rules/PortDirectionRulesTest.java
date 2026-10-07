package test.unittest.rules;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import tools.rules.PortPropagationRules;

class PortDirectionRulesTest {

    @Test
    void borderXorInternal_doesNotFlip() {
        String newDir = PortPropagationRules.computeTargetPortDirection(
                "Out",
                true,  // src border
                false, // tgt internal
                "A::B::p1",
                "A::C::p2"
        );
        assertEquals("Out", newDir);
    }

    @Test
    void sameDepth_nonBorder_flips() {
        String newDir = PortPropagationRules.computeTargetPortDirection(
                "Out",
                false, false,
                "A::B::p1", // container [A,B]
                "A::C::p2"  // container [A,C] => sameDepth
        );
        assertEquals("In", newDir);
    }

    @Test
    void sameBranch_differentDepth_doesNotFlip() {
        String newDir = PortPropagationRules.computeTargetPortDirection(
                "Out",
                false, false,
                "A::B::C::p1", // container [A,B,C]
                "A::B::p2"     // container [A,B] => prefix => sameBranch
        );
        assertEquals("Out", newDir);
    }

    @Test
    void differentBranches_differentDepth_flips() {
        String newDir = PortPropagationRules.computeTargetPortDirection(
                "Out",
                false, false,
                "A::B::C::p1",
                "A::D::p2"
        );
        assertEquals("In", newDir);
    }

    @Test
    void inOut_neverFlips() {
        String newDir = PortPropagationRules.computeTargetPortDirection(
                "InOut",
                false, false,
                "A::B::p1",
                "A::C::p2"
        );
        assertEquals("InOut", newDir);
    }

    @Test
    void unknownDirection_returnsNull() {
        assertNull(PortPropagationRules.computeTargetPortDirection(
                "Unspecified",
                false, false,
                "A::B::p1",
                "A::C::p2"
        ));
    }

    @Test
    void blankSourceDirection_returnsNull() {
        assertNull(PortPropagationRules.computeTargetPortDirection(
                "  ", false, false, "A::B::p1", "A::C::p2"
        ));
    }
}