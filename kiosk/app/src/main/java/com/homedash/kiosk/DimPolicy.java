package com.homedash.kiosk;

/**
 * Decides whether the screen should be dimmed. The frame has no motion sensor,
 * so "someone is here" means a touch, or the room lights coming on.
 *
 * - Dims after {@code idleMs} without activity.
 * - Dims straight away when the room goes dark, unless touched since.
 * - Activity is a touch, or the room going from dark to light.
 *
 * Darkness uses two thresholds (hysteresis) so a reading hovering around one
 * value doesn't flicker the screen.
 */
public final class DimPolicy {

    private final long idleMs;
    private final float darkBelowLux;
    private final float lightAboveLux;

    private long lastActivityMs;
    private long darkSinceMs = Long.MIN_VALUE;
    private boolean dark = false;

    public DimPolicy(long idleMs, float darkBelowLux, float lightAboveLux, long nowMs) {
        if (darkBelowLux >= lightAboveLux) throw new IllegalArgumentException("dark threshold must be below light threshold");
        this.idleMs = idleMs;
        this.darkBelowLux = darkBelowLux;
        this.lightAboveLux = lightAboveLux;
        this.lastActivityMs = nowMs;   // start bright
    }

    public void onTouch(long nowMs) {
        lastActivityMs = nowMs;
    }

    public void onLux(float lux, long nowMs) {
        if (!dark && lux < darkBelowLux) {
            dark = true;
            darkSinceMs = nowMs;
        } else if (dark && lux > lightAboveLux) {
            dark = false;
            lastActivityMs = nowMs;   // lights on counts as someone arriving
        }
    }

    public boolean isDark() {
        return dark;
    }

    public boolean shouldDim(long nowMs) {
        if (nowMs - lastActivityMs >= idleMs) return true;
        // "<=": booting in a dark room dims, as start-up counts as activity at that same moment.
        return dark && lastActivityMs <= darkSinceMs;
    }
}
