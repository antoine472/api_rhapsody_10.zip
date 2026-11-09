// modificationCounter = 1
// lastChange: 2026-07-20 - Implemented checker 207927 for Technical Component With Reference.

package checkers;

import com.safran.checker.core.SafranChecker;
import com.telelogic.rhapsody.core.IRPCollection;
import com.telelogic.rhapsody.core.IRPExternalCheckRegistry;
import com.telelogic.rhapsody.core.IRPModelElement;

public final class TechnicalComponentWithReferenceCompatibilityChecker extends SafranChecker {
    public TechnicalComponentWithReferenceCompatibilityChecker(
            IRPExternalCheckRegistry connectionPoint) {
        super(connectionPoint);
    }

    @Override
    public boolean check(
            IRPModelElement elementToCheck,
            IRPCollection failedElements) {
        return executeRule(
                elementToCheck,
                failedElements,
                rules()::checkTechnicalComponentWithReferenceCompatibility);
    }

    @Override
    public boolean getCompleteness() {
        return false;
    }
}
