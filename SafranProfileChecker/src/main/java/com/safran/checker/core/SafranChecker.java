// modificationCounter = 1
// lastChange: 2026-07-17 - Implemented generic external checker lifecycle, configuration and failure reporting.

package com.safran.checker.core;

import com.safran.checker.config.CheckerDefinition;
import com.safran.checker.service.CheckerRuleService;
import com.telelogic.rhapsody.core.IRPCollection;
import com.telelogic.rhapsody.core.IRPExternalCheckRegistry;
import com.telelogic.rhapsody.core.IRPModelElement;
import com.telelogic.rhapsody.core.RPExternalCheck;

public abstract class SafranChecker extends RPExternalCheck {
    private final IRPExternalCheckRegistry connectionPoint;
    private final CheckerDefinition definition;
    private final CheckerRuleService ruleService;

    protected SafranChecker(IRPExternalCheckRegistry connectionPoint) {
        if (connectionPoint == null) {
            throw new IllegalArgumentException("External checker registry must not be null.");
        }
        this.connectionPoint = connectionPoint;
        this.definition = SafranCheckerRuntime.getConfigurationRepository()
                .findDefinitionByJavaClassName(getClass().getSimpleName());
        this.ruleService = CheckerRuleService.getInstance();
        if (!connect(connectionPoint)) {
            throw new IllegalStateException("Rhapsody rejected external checker registration for " + getClass().getName());
        }
        SafranCheckerRuntime.getLogger().info(
                "Connected checker " + definition.getId() + " / " + definition.getJavaClassName());
    }

    protected final CheckerRuleService rules() { return ruleService; }

    protected final boolean executeRule(
            IRPModelElement elementToCheck,
            IRPCollection failedElements,
            ModelRule rule) {
        try {
            CheckOutcome outcome = rule.evaluate(elementToCheck);
            if (outcome.isPassed()) {
                return true;
            }
            for (IRPModelElement failedElement : outcome.getFailedElements()) {
                failedElements.addItem(failedElement);
            }
            if (!outcome.getComment().isBlank()) {
                connectionPoint.appendFailedElementsComments(outcome.getComment());
            }
            return false;
        } catch (RuntimeException exception) {
            String message = "Checker " + definition.getId() + " failed to evaluate element "
                    + safeElementName(elementToCheck) + ". The checker returns NOK to avoid hiding the evaluation error.";
            SafranCheckerRuntime.getLogger().error(message, exception);
            if (elementToCheck != null) {
                failedElements.addItem(elementToCheck);
            }
            connectionPoint.appendFailedElementsComments(message + " See Rhapsody Output > Log.");
            return false;
        }
    }

    private String safeElementName(IRPModelElement element) {
        if (element == null) {
            return "<null>";
        }
        try {
            return element.getFullPathName();
        } catch (RuntimeException ignored) {
            return "<unavailable>";
        }
    }

    @Override public final String getDomain() { return definition.getDomain(); }
    @Override public final String getMetaclasses() { return definition.getCommaSeparatedMetaClasses(); }
    @Override public final String getName() { return definition.getName(); }
    @Override public final String getSeverity() { return definition.getSeverity(); }
    @Override public final boolean getShouldCallFromCG() { return definition.shouldCallFromCodeGeneration(); }

    @Override
    public void doExit() {
        disconnect();
    }
}
