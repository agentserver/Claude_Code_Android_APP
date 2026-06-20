package com.portalagent.automation;

public enum AutomationRiskLevel {
    LOW,
    MEDIUM,
    HIGH;

    public static AutomationRiskLevel fromString(String value) {
        if (value == null) return MEDIUM;
        try {
            return AutomationRiskLevel.valueOf(value);
        } catch (IllegalArgumentException e) {
            return MEDIUM;
        }
    }
}
