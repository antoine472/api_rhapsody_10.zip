// modificationCounter = 1
// lastChange: 2026-07-17 - Created validated runtime context shared by dynamically loaded checkers.

package com.safran.checker.core;

import com.safran.checker.config.CheckerConfigurationRepository;
import com.safran.checker.log.RhapsodyLogger;

public final class SafranCheckerRuntime {
    private static CheckerConfigurationRepository configurationRepository;
    private static RhapsodyLogger logger;

    private SafranCheckerRuntime() { }

    public static synchronized void initialize(
            CheckerConfigurationRepository repository,
            RhapsodyLogger runtimeLogger) {
        configurationRepository = repository;
        logger = runtimeLogger;
    }

    public static synchronized void clear() {
        configurationRepository = null;
        logger = null;
    }

    public static synchronized CheckerConfigurationRepository getConfigurationRepository() {
        if (configurationRepository == null) {
            throw new IllegalStateException("SafranChecker runtime has not been initialized.");
        }
        return configurationRepository;
    }

    public static synchronized RhapsodyLogger getLogger() {
        if (logger == null) {
            throw new IllegalStateException("SafranChecker logger has not been initialized.");
        }
        return logger;
    }
}
