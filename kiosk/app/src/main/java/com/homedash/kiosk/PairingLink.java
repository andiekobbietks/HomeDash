package com.homedash.kiosk;

import java.net.URI;
import java.util.Locale;

/**
 * Checks a pairing link sent to the kiosk over USB (FamilyPlanner S34):
 *   adb shell am start -n com.homedash.kiosk/.KioskActivity -d 'https://<planner>/display/pair#k=<key>'
 * Only the planner's own pairing page with a well-formed key after the "#" is accepted. The key
 * must be in the fragment, which is never sent to a server, so a query string is refused.
 */
public final class PairingLink {

    private final String plannerHost;

    public PairingLink(String plannerHost) {
        this.plannerHost = plannerHost.toLowerCase(Locale.ROOT);
    }

    /** The link to load, or null to ignore it. */
    public String validate(String link) {
        if (link == null) return null;
        final URI u;
        try {
            u = new URI(link);
        } catch (Exception e) {
            return null;
        }
        if (!"https".equalsIgnoreCase(u.getScheme())) return null;
        if (u.getUserInfo() != null || u.getPort() != -1 || u.getHost() == null) return null;
        if (!u.getHost().toLowerCase(Locale.ROOT).equals(plannerHost)) return null;
        if (!"/display/pair".equals(u.getPath()) || u.getRawQuery() != null) return null;
        String fragment = u.getRawFragment();
        if (fragment == null || !fragment.matches("k=[A-Za-z0-9_-]{43}")) return null;
        return link;
    }
}
