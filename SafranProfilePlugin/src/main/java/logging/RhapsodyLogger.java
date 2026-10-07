package logging;

import com.telelogic.rhapsody.core.IRPApplication;

public final class RhapsodyLogger {

    public enum LogLevel { DEBUG, INFO, WARN, ERROR, NONE }

    private static final RhapsodyLogger INSTANCE = new RhapsodyLogger();
    private static final ThreadLocal<Boolean> IN_LOG = ThreadLocal.withInitial(() -> false);

    private volatile IRPApplication rhpApp;
    private volatile LogLevel thresholdLevel = LogLevel.INFO;

    private RhapsodyLogger() {}

    public static RhapsodyLogger getInstance() {
        return INSTANCE;
    }

    public void initialize(IRPApplication app) {
        this.rhpApp = app;

        // Ne jamais supposer qu'un projet est ouvert
        try {
            var prj = (app != null) ? app.activeProject() : null;
            if (prj != null) {
                String level = prj.getPropertyValue("General.Model.ThresholdLevel");
                if (level != null && !level.isBlank()) {
                    setThresholdLevel(LogLevel.valueOf(level.trim().toUpperCase()));
                }
            }
        } catch (Throwable t) {
            System.err.println("Failed to read threshold level from Rhapsody: " + t.getMessage());
        }
    }

    public void setThresholdLevel(LogLevel level) {
        this.thresholdLevel = (level == null) ? LogLevel.INFO : level;
    }

    public void debug(String msg) { log(LogLevel.DEBUG, msg, null); }
    public void info (String msg) { log(LogLevel.INFO , msg, null); }
    public void warn (String msg) { log(LogLevel.WARN , msg, null); }
    public void error(String msg) { log(LogLevel.ERROR, msg, null); }
    public void error(String msg, Throwable t) { log(LogLevel.ERROR, msg, t); }

    private boolean isEnabled(LogLevel level) {
        if (thresholdLevel == LogLevel.NONE) return false;
        return level.ordinal() >= thresholdLevel.ordinal();
    }

    private void log(LogLevel level, String msg, Throwable t) {
        if (!isEnabled(level)) return;

        if (Boolean.TRUE.equals(IN_LOG.get())) {
            System.err.println(format(level, msg));
            if (t != null) t.printStackTrace(System.err);
            return;
        }

        IN_LOG.set(true);
        try {
            write(format(level, msg));
            if (t != null) t.printStackTrace(System.err);
        } catch (Throwable fail) {
            System.err.println("Logger failure: " + fail.getMessage());
            fail.printStackTrace(System.err);
        } finally {
            IN_LOG.set(false);
        }
    }

    private String format(LogLevel level, String message) {
        String ts = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));

        String msg = (message == null) ? "null" : message;
        // évite String.format
        return "[" + pad5(level.name()) + "] " + ts + " - " + msg;
    }

    private String pad5(String s) {
        if (s == null) return "null ";
        if (s.length() >= 5) return s;
        return "     ".substring(s.length()) + s;
    }

    private void write(String line) {
        IRPApplication app = this.rhpApp;
        if (app == null) {
            System.err.println(line);
            return;
        }
        try {
            app.writeToOutputWindow("Log", line + "\n");
        } catch (Throwable e) {
            System.err.println("Failed to write to Rhapsody log: " + e.getMessage());
        }
    }
}