// modificationCounter = 1
// lastChange: 2026-07-17 - Created centralized Rhapsody Output Log logger.

package com.safran.checker.log;

import com.telelogic.rhapsody.core.IRPApplication;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class RhapsodyLogger {
    private static final String OUTPUT_WINDOW = "Log";
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final IRPApplication application;

    public RhapsodyLogger(IRPApplication application) {
        if (application == null) {
            throw new IllegalArgumentException("Rhapsody application must not be null.");
        }
        this.application = application;
    }

    public synchronized void info(String message) { write("INFO", message, null); }
    public synchronized void warning(String message) { write("WARN", message, null); }
    public synchronized void error(String message, Throwable throwable) { write("ERROR", message, throwable); }

    private void write(String level, String message, Throwable throwable) {
        StringBuilder output = new StringBuilder();
        output.append('[').append(TIMESTAMP_FORMAT.format(LocalDateTime.now())).append("] ")
                .append('[').append(level).append("] ")
                .append(message == null ? "" : message);
        if (throwable != null) {
            output.append(" | ").append(throwable.getClass().getSimpleName()).append(": ")
                    .append(throwable.getMessage() == null ? "<no message>" : throwable.getMessage());
            StringWriter stackTrace = new StringWriter();
            throwable.printStackTrace(new PrintWriter(stackTrace));
            output.append(System.lineSeparator()).append(stackTrace);
        }
        output.append(System.lineSeparator());
        try {
            application.writeToOutputWindow(OUTPUT_WINDOW, output.toString());
        } catch (RuntimeException loggingFailure) {
            System.err.print(output);
            if (throwable != null) {
                throwable.printStackTrace(System.err);
            }
        }
    }
}
