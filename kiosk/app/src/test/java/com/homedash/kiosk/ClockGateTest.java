package com.homedash.kiosk;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ClockGateTest {

    private static final long BUILT = 1_793_000_000_000L;   // early November 2026
    private final ClockGate gate = new ClockGate(BUILT);

    // Regression: the frame boots at 1 January 1970. Signing in then gave Microsoft's
    // "Stay signed in" cookies a 1971 expiry, so every restart needed a full sign-in.
    @Test public void distrustsTheResetClockAtBoot() {
        assertFalse(gate.isTrustworthy(0));
        assertFalse(gate.isTrustworthy(39_265));            // 39 s after boot, as found on the frame
        assertFalse(gate.isTrustworthy(86_400_000L * 365));  // a year into 1970
    }

    @Test public void distrustsAnyTimeBeforeTheBuild() {
        assertFalse(gate.isTrustworthy(BUILT - 1));
    }

    @Test public void trustsTheBuildTimeAndLater() {
        assertTrue(gate.isTrustworthy(BUILT));
        assertTrue(gate.isTrustworthy(BUILT + 86_400_000L * 365 * 5));
    }
}
