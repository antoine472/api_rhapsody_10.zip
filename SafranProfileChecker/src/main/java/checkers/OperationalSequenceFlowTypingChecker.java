// modificationCounter = 1
// lastChange: 2026-07-17 - Implemented checker 210753.

package checkers;

import com.safran.checker.core.SafranChecker;
import com.telelogic.rhapsody.core.IRPCollection;
import com.telelogic.rhapsody.core.IRPExternalCheckRegistry;
import com.telelogic.rhapsody.core.IRPModelElement;

public final class OperationalSequenceFlowTypingChecker extends SafranChecker {
    public OperationalSequenceFlowTypingChecker(IRPExternalCheckRegistry connectionPoint) {
        super(connectionPoint);
    }

    @Override
    public boolean check(IRPModelElement elementToCheck, IRPCollection failedElements) {
        return executeRule(elementToCheck, failedElements, rules()::checkOperationalSequenceFlowTyping);
    }

    @Override
    public boolean getCompleteness() {
        return false;
    }
}
