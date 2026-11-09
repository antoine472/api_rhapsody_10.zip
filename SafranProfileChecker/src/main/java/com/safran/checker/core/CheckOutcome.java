// modificationCounter = 1
// lastChange: 2026-07-17 - Created immutable result object for checker evaluations.

package com.safran.checker.core;

import com.telelogic.rhapsody.core.IRPModelElement;
import java.util.ArrayList;
import java.util.List;

public final class CheckOutcome {
    private static final CheckOutcome PASSED = new CheckOutcome(true, List.of(), "");

    private final boolean passed;
    private final List<IRPModelElement> failedElements;
    private final String comment;

    private CheckOutcome(boolean passed, List<IRPModelElement> failedElements, String comment) {
        this.passed = passed;
        this.failedElements = List.copyOf(failedElements);
        this.comment = comment == null ? "" : comment;
    }

    public static CheckOutcome passed() { return PASSED; }

    public static CheckOutcome failed(IRPModelElement element, String comment) {
        return failed(element == null ? List.of() : List.of(element), comment);
    }

    public static CheckOutcome failed(List<? extends IRPModelElement> elements, String comment) {
        List<IRPModelElement> nonNullElements = new ArrayList<>();
        if (elements != null) {
            for (IRPModelElement element : elements) {
                if (element != null && !nonNullElements.contains(element)) {
                    nonNullElements.add(element);
                }
            }
        }
        return new CheckOutcome(false, nonNullElements, comment);
    }

    public boolean isPassed() { return passed; }
    public List<IRPModelElement> getFailedElements() { return failedElements; }
    public String getComment() { return comment; }
}
