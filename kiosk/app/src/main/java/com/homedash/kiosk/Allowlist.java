package com.homedash.kiosk;

import java.net.URI;
import java.util.Locale;

/**
 * Decides which pages the kiosk may navigate to: the planner itself, and the
 * Microsoft sign-in pages that Static Web Apps redirects to. Everything else
 * is refused, so nobody at the touchscreen can wander off to other sites.
 *
 * This only gates navigations (top-level pages and frames). Images, scripts
 * and API calls a page loads are limited by the planner's own CSP and, if
 * fitted, the network allowlist on the IoT router.
 */
public final class Allowlist {

    /** Microsoft account and Entra ID sign-in, including MFA and their CDNs. */
    private static final String[] SIGN_IN_SUFFIXES = {
        "login.microsoftonline.com",
        "login.microsoft.com",
        "login.live.com",
        "account.live.com",
        "account.microsoft.com",
        "msauth.net",
        "msftauth.net",
    };

    private static final String SWA_SUFFIX = ".azurestaticapps.net";

    private final String plannerHost;
    /**
     * Static Web Apps' shared sign-in host for the planner's region, which
     * /.auth/login/aad passes through before Microsoft: for
     * "name.4.azurestaticapps.net" it is "identity.4.azurestaticapps.net".
     */
    private final String identityHost;

    public Allowlist(String plannerHost) {
        this.plannerHost = plannerHost.toLowerCase(Locale.ROOT);
        int dot = this.plannerHost.indexOf('.');
        this.identityHost = (this.plannerHost.endsWith(SWA_SUFFIX) && dot > 0)
                ? "identity" + this.plannerHost.substring(dot)
                : null;
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

        if (host.equals(plannerHost)) return true;
        if (identityHost != null
                && (host.equals(identityHost) || host.equals("identity" + SWA_SUFFIX))) return true;
        for (String suffix : SIGN_IN_SUFFIXES) {
            if (host.equals(suffix) || host.endsWith("." + suffix)) return true;
        }
        return false;
    }
}
