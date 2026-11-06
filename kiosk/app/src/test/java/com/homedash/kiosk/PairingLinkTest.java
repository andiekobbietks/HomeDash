package com.homedash.kiosk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class PairingLinkTest {

    private static final String HOST = "your-app-name.4.azurestaticapps.net";
    private static final String KEY = "AbCdEfGhIjKlMnOpQrStUvWxYz0123456789-_abcde";   // 43 chars
    private final PairingLink pairing = new PairingLink(HOST);

    @Test public void acceptsThePlannersPairingLink() {
        String link = "https://" + HOST + "/display/pair#k=" + KEY;
        assertEquals(link, pairing.validate(link));
    }

    @Test public void rejectsOtherHostsPathsAndSchemes() {
        assertNull(pairing.validate("https://evil.example/display/pair#k=" + KEY));
        assertNull(pairing.validate("https://" + HOST + ".evil.example/display/pair#k=" + KEY));
        assertNull(pairing.validate("https://" + HOST + "@evil.example/display/pair#k=" + KEY));
        assertNull(pairing.validate("http://" + HOST + "/display/pair#k=" + KEY));
        assertNull(pairing.validate("https://" + HOST + ":8443/display/pair#k=" + KEY));
        assertNull(pairing.validate("https://" + HOST + "/display#k=" + KEY));
        assertNull(pairing.validate("https://" + HOST + "/login#k=" + KEY));
        assertNull(pairing.validate("javascript:alert(1)"));
        assertNull(pairing.validate(null));
    }

    @Test public void rejectsAMissingOrMalformedKey() {
        assertNull(pairing.validate("https://" + HOST + "/display/pair"));
        assertNull(pairing.validate("https://" + HOST + "/display/pair#k="));
        assertNull(pairing.validate("https://" + HOST + "/display/pair#k=short"));
        assertNull(pairing.validate("https://" + HOST + "/display/pair#k=" + KEY + "x"));
        assertNull(pairing.validate("https://" + HOST + "/display/pair?k=" + KEY));   // query would reach server logs
        assertNull(pairing.validate("https://" + HOST + "/display/pair?x=1#k=" + KEY));
    }
}
