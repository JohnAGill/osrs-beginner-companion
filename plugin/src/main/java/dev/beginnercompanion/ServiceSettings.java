package dev.beginnercompanion;

import java.net.URI;

/** Fail closed before any observations leave the plugin. */
final class ServiceSettings {
    final URI endpoint;
    private final boolean enabled;
    private final String problem;

    private ServiceSettings(URI endpoint, boolean enabled, String problem) {
        this.endpoint = endpoint; this.enabled = enabled; this.problem = problem;
    }

    static ServiceSettings hosted(String url, boolean enabled) {
        return parse(url, enabled, false);
    }

    static ServiceSettings development(String url) { return parse(url, true, true); }

    private static ServiceSettings parse(String url, boolean enabled, boolean development) {
        try {
            URI base = URI.create(url.strip());
            boolean loopback = "127.0.0.1".equals(base.getHost());
            if (base.getHost() == null || base.getUserInfo() != null || base.getQuery() != null
                || base.getFragment() != null || !(base.getPath().isEmpty() || "/".equals(base.getPath()))
                || base.getPort() == 0 || base.getPort() > 65535
                || !(development ? loopback && "http".equals(base.getScheme()) : "https".equals(base.getScheme()))) {
                throw new IllegalArgumentException();
            }
            return new ServiceSettings(base.resolve("/v1/goals/evaluate"), enabled, null);
        } catch (RuntimeException invalid) {
            return new ServiceSettings(null, false, development ? "Invalid local development service URL."
                : "Set a valid HTTPS planner origin in the plugin settings (no path, credentials or query).");
        }
    }

    boolean canSend() { return enabled && endpoint != null; }

    String disclosure() {
        if (problem != null) return "Planner is off. " + problem;
        String destination = endpoint.getScheme() + "://" + endpoint.getRawAuthority();
        return (enabled ? "Planner enabled: " : "Planner is off. Enable Share progress in plugin settings to use ")
            + destination + ". Sends skill levels/XP, quest and diary progress, observed unlocks, membership, "
            + "preferences, completion history and recommendation feedback. No character name, account hash or credentials. "
            + "The service receives your IP address. Saved goals and tasks stay on this device.";
    }
}
