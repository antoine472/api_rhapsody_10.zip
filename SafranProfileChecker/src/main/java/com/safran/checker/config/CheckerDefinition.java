// modificationCounter = 1
// lastChange: 2026-07-17 - Created immutable external checker configuration model.

package com.safran.checker.config;

import java.util.List;
import java.util.Objects;

public final class CheckerDefinition {
    private final String id;
    private final String javaClassName;
    private final String domain;
    private final String name;
    private final String severity;
    private final boolean enabled;
    private final boolean completeness;
    private final boolean shouldCallFromCodeGeneration;
    private final List<String> metaClasses;

    public CheckerDefinition(
            String id,
            String javaClassName,
            String domain,
            String name,
            String severity,
            boolean enabled,
            boolean completeness,
            boolean shouldCallFromCodeGeneration,
            List<String> metaClasses) {
        this.id = requireText(id, "id");
        this.javaClassName = requireText(javaClassName, "javaClassName");
        this.domain = requireText(domain, "domain");
        this.name = validateCheckerName(name);
        this.severity = validateSeverity(severity);
        this.enabled = enabled;
        this.completeness = completeness;
        this.shouldCallFromCodeGeneration = shouldCallFromCodeGeneration;
        this.metaClasses = List.copyOf(Objects.requireNonNull(metaClasses, "metaClasses"));
        if (this.metaClasses.isEmpty()) {
            throw new IllegalArgumentException("Checker " + javaClassName + " has no configured metaclass or new term.");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing mandatory checker field: " + fieldName);
        }
        return value.trim();
    }

    private static String validateCheckerName(String value) {
        String checkedValue = requireText(value, "name");
        if (checkedValue.contains(",")) {
            throw new IllegalArgumentException("Rhapsody external checker names cannot contain commas: " + checkedValue);
        }
        return checkedValue;
    }

    private static String validateSeverity(String value) {
        String checkedValue = requireText(value, "severity");
        if (!checkedValue.equals("Error") && !checkedValue.equals("Warning") && !checkedValue.equals("Info")) {
            throw new IllegalArgumentException("Unsupported severity: " + checkedValue);
        }
        return checkedValue;
    }

    public String getId() { return id; }
    public String getJavaClassName() { return javaClassName; }
    public String getDomain() { return domain; }
    public String getName() { return name; }
    public String getSeverity() { return severity; }
    public boolean isEnabled() { return enabled; }
    public boolean isCompleteness() { return completeness; }
    public boolean shouldCallFromCodeGeneration() { return shouldCallFromCodeGeneration; }
    public List<String> getMetaClasses() { return metaClasses; }
    public String getCommaSeparatedMetaClasses() { return String.join(",", metaClasses); }
}
