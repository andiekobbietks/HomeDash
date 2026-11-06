package com.homedash.kiosk;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AllowlistTest {

    private final Allowlist allowlist = new Allowlist("your-app-name.4.azurestaticapps.net");

    @Test public void allowsThePlanner() {
        assertTrue(allowlist.allows("https://your-app-name.4.azurestaticapps.net/"));
        assertTrue(allowlist.allows("https://your-app-name.4.azurestaticapps.net/.auth/login/aad?post_login_redirect_uri=/"));
        assertTrue(allowlist.allows("https://YOUR-APP-NAME.4.azurestaticapps.net./display"));
    }

    // S34: the frame is paired with a display key, so it never needs Microsoft or the SWA identity host.
    @Test public void blocksMicrosoftSignInNowTheFrameHasADisplayKey() {
        assertFalse(allowlist.allows("https://login.microsoftonline.com/common/oauth2/v2.0/authorize?x=1"));
        assertFalse(allowlist.allows("https://login.live.com/oauth20_authorize.srf"));
        assertFalse(allowlist.allows("https://account.live.com/proofs/Verify"));
        assertFalse(allowlist.allows("https://signup.live.com/signup"));
        assertFalse(allowlist.allows("https://identity.4.azurestaticapps.net/.redirect/aad"));
        assertTrue(allowlist.allows("about:blank"));
    }

    @Test public void blocksOtherSites() {
        assertFalse(allowlist.allows("https://www.google.com/"));
        assertFalse(allowlist.allows("https://other-app.4.azurestaticapps.net/"));
        assertFalse(allowlist.allows("https://aimor.example/"));
        assertFalse(allowlist.allows("https://www.microsoft.com/"));
    }

    @Test public void blocksLookalikes() {
        assertFalse(allowlist.allows("https://your-app-name.4.azurestaticapps.net.evil.example/"));
        assertFalse(allowlist.allows("https://evil.example/your-app-name.4.azurestaticapps.net"));
        assertFalse(allowlist.allows("https://your-app-name.4.azurestaticapps.net@evil.example/"));
        assertFalse(allowlist.allows("https://notlogin.live.com.evil.example/"));
        assertFalse(allowlist.allows("https://evillogin.live.com/"));
    }

    @Test public void blocksOtherSchemesAndPorts() {
        assertFalse(allowlist.allows("http://your-app-name.4.azurestaticapps.net/"));
        assertFalse(allowlist.allows("https://your-app-name.4.azurestaticapps.net:8443/"));
        assertFalse(allowlist.allows("file:///sdcard/"));
        assertFalse(allowlist.allows("javascript:alert(1)"));
        assertFalse(allowlist.allows("data:text/html,hi"));
        assertFalse(allowlist.allows("about:config"));
        assertFalse(allowlist.allows("resource://android/"));
        assertFalse(allowlist.allows(null));
        assertFalse(allowlist.allows("not a url"));
    }
}
