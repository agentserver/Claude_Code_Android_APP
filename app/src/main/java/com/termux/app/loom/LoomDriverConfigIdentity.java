package com.termux.app.loom;

public final class LoomDriverConfigIdentity {

    public final String serverUrl;
    public final String sandboxId;
    public final String workspaceId;
    public final String shortId;

    private LoomDriverConfigIdentity(
            String serverUrl,
            String sandboxId,
            String workspaceId,
            String shortId) {
        this.serverUrl = clean(serverUrl);
        this.sandboxId = clean(sandboxId);
        this.workspaceId = clean(workspaceId);
        this.shortId = clean(shortId);
    }

    public static LoomDriverConfigIdentity empty() {
        return new LoomDriverConfigIdentity("", "", "", "");
    }

    public static LoomDriverConfigIdentity parse(String yaml) {
        if (yaml == null || yaml.trim().isEmpty()) return empty();

        String section = "";
        String serverUrl = "";
        String sandboxId = "";
        String workspaceId = "";
        String shortId = "";
        String[] lines = yaml.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        for (String line : lines) {
            if (line == null) continue;
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            if (!line.startsWith(" ") && !line.startsWith("\t")) {
                if (trimmed.endsWith(":")) {
                    section = trimmed.substring(0, trimmed.length() - 1).trim();
                } else {
                    section = "";
                }
                continue;
            }
            int colon = trimmed.indexOf(':');
            if (colon <= 0) continue;
            String key = trimmed.substring(0, colon).trim();
            String value = unquote(trimmed.substring(colon + 1).trim());
            if ("server".equals(section) && "url".equals(key)) {
                serverUrl = value;
            } else if ("credentials".equals(section) && "sandbox_id".equals(key)) {
                sandboxId = value;
            } else if ("credentials".equals(section) && "workspace_id".equals(key)) {
                workspaceId = value;
            } else if ("credentials".equals(section) && "short_id".equals(key)) {
                shortId = value;
            }
        }
        return new LoomDriverConfigIdentity(serverUrl, sandboxId, workspaceId, shortId);
    }

    public boolean hasRemoteIdentity() {
        return !workspaceId.isEmpty() || !sandboxId.isEmpty() || !shortId.isEmpty();
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static String unquote(String raw) {
        String value = clean(raw);
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                value = value.substring(1, value.length() - 1);
            }
        }
        return value.replace("\\\"", "\"").replace("\\\\", "\\").trim();
    }
}
