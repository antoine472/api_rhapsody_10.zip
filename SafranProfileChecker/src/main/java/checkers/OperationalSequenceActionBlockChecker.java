// modificationCounter = 1
// lastChange: 2026-07-20 - Implemented checker 206489 on Operational Sequence Diagram Action Blocks.

package checkers;

import com.safran.checker.core.SafranChecker;
import com.telelogic.rhapsody.core.IRPCollection;
import com.telelogic.rhapsody.core.IRPExternalCheckRegistry;
import com.telelogic.rhapsody.core.IRPModelElement;

public final class OperationalSequenceActionBlockChecker extends SafranChecker {
    public OperationalSequenceActionBlockChecker(
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
                rules()::checkOperationalSequenceActionBlocks);
    }

    @Override
    public boolean getCompleteness() {
        return false;
    }
}
