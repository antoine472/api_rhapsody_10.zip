// modificationCounter = 1
// lastChange: 2026-07-17 - Created functional contract used by checker classes.

package com.safran.checker.core;

import com.telelogic.rhapsody.core.IRPModelElement;

@FunctionalInterface
public interface ModelRule {
    CheckOutcome evaluate(IRPModelElement elementToCheck);
}
