package com.homedash.kiosk;

/**
 * Says whether the wall clock can be trusted yet.
 *
 * The frame has no clock battery: every boot starts at 1 January 1970 until
 * network time arrives. Gecko caps cookie lifetimes at 400 days from "now", so
 * a sign-in made before the clock syncs stores Microsoft's "Stay signed in"
 * cookies with a 1971 expiry. They are thrown away the moment the real date
 * arrives, and every restart needs a full sign-in with a second factor.
 *
 * The browser must therefore not start until the clock is at least the date the
 * app was built, which a real clock always is and a reset one never is.
 */
public final class ClockGate {

    private final long notBeforeMs;

    public ClockGate(long notBeforeMs) {
        this.notBeforeMs = notBeforeMs;
    }

    public boolean isTrustworthy(long wallClockMs) {
        return wallClockMs >= notBeforeMs;
    }
}
