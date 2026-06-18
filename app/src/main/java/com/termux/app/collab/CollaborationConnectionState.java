package com.termux.app.collab;

import com.termux.app.AssistantProvider;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class CollaborationConnectionState {

    public static final String DRIVER_STATUS_MISSING = "missing";
    public static final String DRIVER_STATUS_VALID = "valid";
    public static final String DRIVER_STATUS_STALE = "stale";
    public static final String DRIVER_STATUS_BINDING = "binding";
    public static final String DRIVER_STATUS_FAILED = "failed";

    private CollaborationConnectionState() {
    }

    public static String computeDriverFingerprint(
            AssistantProvider provider,
            String serverUrl,
            String workspaceId,
            String deviceName,
            String driverName,
            String driverConfigPath,
            String mcpConfigPath) {
        String providerId = provider == null ? AssistantProvider.CODEX.id : provider.id;
        String raw = join(
            providerId,
            clean(serverUrl),
            clean(workspaceId),
            clean(deviceName),
            clean(driverName),
            clean(driverConfigPath),
            clean(mcpConfigPath));
        return sha256(raw);
    }

    public static String driverBindingStatus(
            String savedFingerprint,
            String currentFingerprint,
            String savedStatus) {
        String status = clean(savedStatus);
        if (DRIVER_STATUS_BINDING.equals(status)) return DRIVER_STATUS_BINDING;
        if (DRIVER_STATUS_FAILED.equals(status)) return DRIVER_STATUS_FAILED;
        if (DRIVER_STATUS_STALE.equals(status)) return DRIVER_STATUS_STALE;

        String saved = clean(savedFingerprint);
        String current = clean(currentFingerprint);
        if (saved.isEmpty() || current.isEmpty()) return DRIVER_STATUS_MISSING;
        if (!saved.equals(current)) return DRIVER_STATUS_STALE;
        if (DRIVER_STATUS_VALID.equals(status)) return DRIVER_STATUS_VALID;
        return DRIVER_STATUS_MISSING;
    }

    public static boolean canStartRole(String role, String driverStatus) {
        String safeRole = clean(role);
        if ("observer".equals(safeRole)) return true;
        return DRIVER_STATUS_VALID.equals(clean(driverStatus));
    }

    public static String driverStatusAfterCredentialProbe(String driverStatus, boolean credentialsValid) {
        String status = clean(driverStatus);
        if (DRIVER_STATUS_VALID.equals(status) && !credentialsValid) {
            return DRIVER_STATUS_STALE;
        }
        return status;
    }

    public static boolean hasWorkspaceIdentity(String workspaceId) {
        return !clean(workspaceId).isEmpty();
    }

    public static boolean canBindDriver(String serverUrl, String workspaceId) {
        return !clean(serverUrl).isEmpty() || hasWorkspaceIdentity(workspaceId);
    }

    private static String join(String... values) {
        StringBuilder sb = new StringBuilder();
        for (String value : values) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(value == null ? "" : value);
        }
        return sb.toString();
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                out.append(String.format("%02x", b & 0xff));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
