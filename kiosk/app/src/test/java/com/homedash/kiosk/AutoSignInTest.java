package com.homedash.kiosk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class AutoSignInTest {

    private static final String HOST = "your-app-name.4.azurestaticapps.net";
    private static final String AUTH = "https://" + HOST + "/.auth/login/aad?post_login_redirect_uri=";
    private static final long MIN = 60_000;

    private AutoSignIn auto() {
        return new AutoSignIn(HOST, 2 * MIN);
    }

    // Regression: after a restart the kiosk sat on the planner's "Sign in with Microsoft" page.
    @Test public void pressesSignInOnThePlannersLoginPage() {
        assertEquals(AUTH + "%2Fdisplay", auto().redirectFor("https://" + HOST + "/login?next=%2Fdisplay", 0));
        assertEquals(AUTH + "%2Fdisplay", auto().redirectFor("https://" + HOST + "/login", 0));
        assertEquals(AUTH + "%2Fdisplay", auto().redirectFor("https://" + HOST + "/login?next=%2F", 0));
        assertEquals(AUTH + "%2Fdisplay", auto().redirectFor("https://" + HOST + "/login/", 0));
    }

    @Test public void keepsAPlainLocalNext() {
        assertEquals(AUTH + "%2Fcalendar", auto().redirectFor("https://" + HOST + "/login?next=%2Fcalendar", 0));
    }

    @Test public void ignoresUnsafeNextValues() {
        for (String next : new String[] {"%2F%2Fevil.example", "https%3A%2F%2Fevil.example", "%2F%5Cevil.example",
                "%2Fa%3Fb%3Dc", "%2Flogin", "javascript%3Aalert(1)"}) {
            assertEquals(next, AUTH + "%2Fdisplay", auto().redirectFor("https://" + HOST + "/login?next=" + next, 0));
        }
    }

    @Test public void leavesOtherPagesAlone() {
        assertNull(auto().redirectFor("https://" + HOST + "/display", 0));
        assertNull(auto().redirectFor("https://" + HOST + "/", 0));
        assertNull(auto().redirectFor("https://" + HOST + "/loginx", 0));
        assertNull(auto().redirectFor("https://" + HOST + "/not-a-member", 0));
        assertNull(auto().redirectFor("https://login.live.com/login", 0));
        assertNull(auto().redirectFor("https://evil.example/login", 0));
        assertNull(auto().redirectFor("http://" + HOST + "/login", 0));
        assertNull(auto().redirectFor(null, 0));
        assertNull(auto().redirectFor("data:text/html,x", 0));
        assertNull(auto().redirectFor("https://" + HOST + "/login?next=%ZZ", 0));   // malformed URL: leave it
    }

    @Test public void triesAtMostOnceEveryTwoMinutes() {
        AutoSignIn a = auto();
        String login = "https://" + HOST + "/login?next=%2Fdisplay";
        assertNotNull(a.redirectFor(login, 10 * MIN));
        assertNull(a.redirectFor(login, 10 * MIN + 30_000));     // came back to /login: don't loop
        assertNull(a.redirectFor(login, 12 * MIN - 1));
        assertNotNull(a.redirectFor(login, 12 * MIN));
    }
}
