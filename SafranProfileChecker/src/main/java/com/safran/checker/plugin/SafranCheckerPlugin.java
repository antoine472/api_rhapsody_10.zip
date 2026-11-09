// modificationCounter = 1
// lastChange: 2026-07-17 - Implemented dynamic checker loading, registration and plugin lifecycle.

package com.safran.checker.plugin;

import com.safran.checker.config.CheckerConfigurationRepository;
import com.safran.checker.config.CheckerDefinition;
import com.safran.checker.core.SafranChecker;
import com.safran.checker.core.SafranCheckerRuntime;
import com.safran.checker.log.RhapsodyLogger;
import com.telelogic.rhapsody.core.IRPApplication;
import com.telelogic.rhapsody.core.IRPExternalCheckRegistry;
import com.telelogic.rhapsody.core.RPUserPlugin;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

public final class SafranCheckerPlugin extends RPUserPlugin {
    private static final String CHECKER_PACKAGE_PREFIX = "checkers.";

    private final List<SafranChecker> registeredCheckers = new ArrayList<>();
    private RhapsodyLogger logger;

    @Override
    public void RhpPluginInit(IRPApplication rpyApplication) {
        logger = new RhapsodyLogger(rpyApplication);
        logger.info("Initializing SafranChecker plugin.");
        try {
            CheckerConfigurationRepository repository = CheckerConfigurationRepository.loadNextToClass(SafranCheckerPlugin.class);
            SafranCheckerRuntime.initialize(repository, logger);
            IRPExternalCheckRegistry registry = rpyApplication.getExternalCheckerRegistry();
            int enabledCount = 0;
            for (CheckerDefinition definition : repository.getDefinitions()) {
                if (!definition.isEnabled()) {
                    logger.info("Checker disabled by configuration: " + definition.getId() + " / " + definition.getJavaClassName());
                    continue;
                }
                registeredCheckers.add(instantiateChecker(definition, registry));
                enabledCount++;
            }
            logger.info("SafranChecker initialized with " + enabledCount + " enabled checker(s). Configuration: "
                    + repository.getConfigurationPath());
        } catch (RuntimeException exception) {
            logger.error("SafranChecker initialization failed.", exception);
            cleanupRegisteredCheckers();
            SafranCheckerRuntime.clear();
            throw exception;
        }
    }

    private SafranChecker instantiateChecker(CheckerDefinition definition, IRPExternalCheckRegistry registry) {
        String qualifiedClassName = CHECKER_PACKAGE_PREFIX + definition.getJavaClassName();
        try {
            Class<?> rawClass = Class.forName(qualifiedClassName);
            if (!SafranChecker.class.isAssignableFrom(rawClass)) {
                throw new IllegalArgumentException(qualifiedClassName + " does not extend SafranChecker.");
            }
            @SuppressWarnings("unchecked")
            Class<? extends SafranChecker> checkerClass = (Class<? extends SafranChecker>) rawClass;
            Constructor<? extends SafranChecker> constructor = checkerClass.getConstructor(IRPExternalCheckRegistry.class);
            return constructor.newInstance(registry);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot instantiate checker " + definition.getId() + " using " + qualifiedClassName, exception);
        }
    }

    @Override public void RhpPluginInvokeItem() { }
    @Override public void OnMenuItemSelect(String menuItem) { }
    @Override public void OnTrigger(String trigger) { }

    @Override
    public boolean RhpPluginCleanup() {
        cleanupRegisteredCheckers();
        SafranCheckerRuntime.clear();
        if (logger != null) logger.info("SafranChecker cleanup completed.");
        return true;
    }

    @Override
    public void RhpPluginFinalCleanup() {
        cleanupRegisteredCheckers();
        SafranCheckerRuntime.clear();
    }

    private void cleanupRegisteredCheckers() {
        for (SafranChecker checker : registeredCheckers) {
            try { checker.doExit(); }
            catch (RuntimeException exception) {
                if (logger != null) logger.error("Cannot disconnect checker " + checker.getClass().getName(), exception);
            }
        }
        registeredCheckers.clear();
    }
}
