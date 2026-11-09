// modificationCounter = 1
// lastChange: 2026-07-20 - Implemented checker 207924 for Function With Reference.

package checkers;

import com.safran.checker.core.SafranChecker;
import com.telelogic.rhapsody.core.IRPCollection;
import com.telelogic.rhapsody.core.IRPExternalCheckRegistry;
import com.telelogic.rhapsody.core.IRPModelElement;

public final class FunctionWithReferenceCompatibilityChecker extends SafranChecker {
    public FunctionWithReferenceCompatibilityChecker(
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
                rules()::checkFunctionWithReferenceCompatibility);
    }

    @Override
    public boolean getCompleteness() {
        return false;
    }
}
