package com.homedash.kiosk;

import java.net.URI;
import java.util.Locale;

/**
 * Decides which pages the kiosk may navigate to: the planner and nothing else.
 * The frame is paired with a display key (FamilyPlanner S34), so it never needs
 * Microsoft sign-in, and nobody at the touchscreen can wander off to other sites.
 *
 * This only gates navigations (top-level pages and frames). Images, scripts
 * and API calls a page loads are limited by the planner's own CSP and, if
 * fitted, the network allowlist on the IoT router.
 */
public final class Allowlist {

    private final String plannerHost;

    public Allowlist(String plannerHost) {
        this.plannerHost = plannerHost.toLowerCase(Locale.ROOT);
    }

    public boolean allows(String uri) {
        if (uri == null) return false;
        if (uri.equals("about:blank")) return true;

        final URI parsed;
        try {
            parsed = new URI(uri);
        } catch (Exception e) {
            return false;
        }
        if (!"https".equalsIgnoreCase(parsed.getScheme())) return false;
        if (parsed.getUserInfo() != null) return false;   // https://planner@evil.example/
        int port = parsed.getPort();
        if (port != -1 && port != 443) return false;

        String host = parsed.getHost();
        if (host == null) return false;
        host = host.toLowerCase(Locale.ROOT);
        if (host.endsWith(".")) host = host.substring(0, host.length() - 1);

        return host.equals(plannerHost);
    }
}
