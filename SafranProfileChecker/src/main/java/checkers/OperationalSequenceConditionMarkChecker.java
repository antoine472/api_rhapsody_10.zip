// modificationCounter = 1
// lastChange: 2026-07-17 - Implemented checker 206496.

package checkers;

import com.safran.checker.core.SafranChecker;
import com.telelogic.rhapsody.core.IRPCollection;
import com.telelogic.rhapsody.core.IRPExternalCheckRegistry;
import com.telelogic.rhapsody.core.IRPModelElement;

public final class OperationalSequenceConditionMarkChecker extends SafranChecker {
    public OperationalSequenceConditionMarkChecker(IRPExternalCheckRegistry connectionPoint) {
        super(connectionPoint);
    }

    @Override
    public boolean check(IRPModelElement elementToCheck, IRPCollection failedElements) {
        return executeRule(elementToCheck, failedElements, rules()::checkOperationalSequenceConditionMark);
    }

    @Override
    public boolean getCompleteness() {
        return false;
    }
}
