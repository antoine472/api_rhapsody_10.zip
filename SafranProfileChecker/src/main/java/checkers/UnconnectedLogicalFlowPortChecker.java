// modificationCounter = 1
// lastChange: 2026-07-17 - Implemented checker 210763.

package checkers;

import com.safran.checker.core.SafranChecker;
import com.telelogic.rhapsody.core.IRPCollection;
import com.telelogic.rhapsody.core.IRPExternalCheckRegistry;
import com.telelogic.rhapsody.core.IRPModelElement;

public final class UnconnectedLogicalFlowPortChecker extends SafranChecker {
    public UnconnectedLogicalFlowPortChecker(IRPExternalCheckRegistry connectionPoint) {
        super(connectionPoint);
    }

    @Override
    public boolean check(IRPModelElement elementToCheck, IRPCollection failedElements) {
        return executeRule(elementToCheck, failedElements, rules()::checkUnconnectedLogicalFlowPort);
    }

    @Override
    public boolean getCompleteness() {
        return true;
    }
}
