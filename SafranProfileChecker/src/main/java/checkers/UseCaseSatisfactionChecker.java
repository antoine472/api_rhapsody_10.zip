// modificationCounter = 1
// lastChange: 2026-07-17 - Implemented checker 206482.

package checkers;

import com.safran.checker.core.SafranChecker;
import com.telelogic.rhapsody.core.IRPCollection;
import com.telelogic.rhapsody.core.IRPExternalCheckRegistry;
import com.telelogic.rhapsody.core.IRPModelElement;

public final class UseCaseSatisfactionChecker extends SafranChecker {
    public UseCaseSatisfactionChecker(IRPExternalCheckRegistry connectionPoint) {
        super(connectionPoint);
    }

    @Override
    public boolean check(IRPModelElement elementToCheck, IRPCollection failedElements) {
        return executeRule(elementToCheck, failedElements, rules()::checkUseCaseSatisfaction);
    }

    @Override
    public boolean getCompleteness() {
        return true;
    }
}
