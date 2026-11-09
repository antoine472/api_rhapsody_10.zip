package logging;

import com.telelogic.rhapsody.core.IRPApplication;

public class RhapsodyLogger {

	private static IRPApplication rhpApp;
	private LogLevel thresholdLevel = LogLevel.INFO; // Default threshold level
	private static final RhapsodyLogger instance = new RhapsodyLogger();

    public enum LogLevel {
        DEBUG,
        INFO,
        WARN,
        ERROR,
        NONE // No messages logged to Rhapsody
    }

	private RhapsodyLogger() {

	}

	public void initialize(IRPApplication app) {
		rhpApp = app;

        // Optionally, read threshold level from a Rhapsody property
        try {
            String level = rhpApp.activeProject().getPropertyValue("General.Model.ThresholdLevel");
            if (level != null) {
                setThresholdLevel(LogLevel.valueOf(level.toUpperCase()));
            }
        } catch (Exception e) {
            warn("Failed to read threshold level from Rhapsody: " + e.getMessage());
        }

	}

    public void setThresholdLevel(LogLevel level) {
        this.thresholdLevel = level;
    }

    /**
     * Checks if the given log level is enabled based on the current threshold.
     */
    private boolean isLevelEnabled(LogLevel level) {
        if (thresholdLevel == LogLevel.NONE) {
            return false;
        }
        return level.ordinal() >= thresholdLevel.ordinal();
    }

	public void info(String message) {
		if (isLevelEnabled(LogLevel.INFO)) {
			writeToRhapsodyLog(formatLogMessage("INFO", message));
		}
	}

	public void debug(String message) {
		if (isLevelEnabled(LogLevel.DEBUG)) {
			writeToRhapsodyLog(formatLogMessage("DEBUG", message));
		}
	}

	public void error(String message) {
		if (isLevelEnabled(LogLevel.ERROR)) {
			writeToRhapsodyLog(formatLogMessage("ERROR", message));
		}
	}

	public void warn(String message) {
		if (isLevelEnabled(LogLevel.WARN)) {
			writeToRhapsodyLog(formatLogMessage("WARNING", message));
		}
	}

    public static RhapsodyLogger getInstance() {
        return instance;
    }

    /**
     * Formats the log message to match Log4j2 output.
     * Example: [INFO ] 2024-12-09 11:01:54.705 [main] - Log message here
     */
    private String formatLogMessage(String level, String message) {
        String timestamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
        String threadName = Thread.currentThread().getName();

        return String.format("[%5s] %s [%s] - %s", level, timestamp, threadName, message);
    }

    /**
     * Writes a formatted log message to the Rhapsody output window.
     */
    private void writeToRhapsodyLog(String message) {
    	if (rhpApp != null) {
    		rhpApp.writeToOutputWindow("Log", message + "\n");
    	}
    }

}
