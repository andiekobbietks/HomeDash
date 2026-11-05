package com.homedash.kiosk;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Locale;

/**
 * Presses FamilyPlanner's "Sign in with Microsoft" button for the frame.
 *
 * The Static Web Apps sign-in cookie is a session cookie, so it is gone after
 * every kiosk restart. The planner's service worker then serves the app from
 * cache, the app finds no user and shows its own /login page. Going straight to
 * /.auth/login/aad lets Microsoft finish sign-in silently when it remembers the
 * account ("Stay signed in?" answered Yes).
 *
 * Rate-limited so that a failure can't loop: after one automatic attempt the
 * login page is left alone for a while, and someone can tap the button.
 */
public final class AutoSignIn {

    static final String DEFAULT_NEXT = "/display";

    private final String plannerHost;
    private final long minIntervalMs;
    private long lastAttemptMs = Long.MIN_VALUE / 2;

    public AutoSignIn(String plannerHost, long minIntervalMs) {
        this.plannerHost = plannerHost.toLowerCase(Locale.ROOT);
        this.minIntervalMs = minIntervalMs;
    }

    /** Returns the sign-in URL to load instead, or null to leave the page alone. */
    public String redirectFor(String url, long nowMs) {
        String target = signInUrlFor(url);
        if (target == null) return null;
        if (nowMs - lastAttemptMs < minIntervalMs) return null;
        lastAttemptMs = nowMs;
        return target;
    }

    /** The sign-in URL if {@code url} is the planner's /login page, else null. */
    String signInUrlFor(String url) {
        if (url == null) return null;
        final URI u;
        try {
            u = new URI(url);
        } catch (Exception e) {
            return null;
        }
        if (!"https".equalsIgnoreCase(u.getScheme()) || u.getHost() == null) return null;
        if (!u.getHost().toLowerCase(Locale.ROOT).equals(plannerHost)) return null;
        String path = u.getPath();
        if (!"/login".equals(path) && !"/login/".equals(path)) return null;

        String next = safeNext(u.getRawQuery());
        return "https://" + plannerHost + "/.auth/login/aad?post_login_redirect_uri="
                + encode(next);
    }

    /** The app's ?next= value if it is a plain local path, otherwise the wall page. */
    static String safeNext(String rawQuery) {
        if (rawQuery == null) return DEFAULT_NEXT;
        for (String pair : rawQuery.split("&")) {
            if (!pair.startsWith("next=")) continue;
            String next;
            try {
                // The (String, String) forms: the Charset overloads need Android 13, the frame runs 6.
                next = URLDecoder.decode(pair.substring(5), "UTF-8");
            } catch (Exception e) {
                return DEFAULT_NEXT;
            }
            // Only same-site paths: no "//host", no "/\host", no scheme, nothing odd.
            if (next.startsWith("/") && !next.startsWith("//") && !next.startsWith("/\\")
                    && next.matches("[A-Za-z0-9/_\\-.]*") && !next.equals("/login")) {
                return next.equals("/") ? DEFAULT_NEXT : next;
            }
            return DEFAULT_NEXT;
        }
        return DEFAULT_NEXT;
    }

    private static String encode(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new AssertionError(e);   // UTF-8 is always supported
        }
    }
}
