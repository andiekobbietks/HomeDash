package com.homedash.kiosk;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DimPolicyTest {

    private static final long MIN = 60_000;
    private static final long IDLE = 5 * MIN;

    private DimPolicy policy() {
        return new DimPolicy(IDLE, 5f, 15f, 0);
    }

    @Test public void startsBrightAndDimsAfterFiveIdleMinutes() {
        DimPolicy p = policy();
        p.onLux(200, 0);
        assertFalse(p.shouldDim(0));
        assertFalse(p.shouldDim(IDLE - 1));
        assertTrue(p.shouldDim(IDLE));
    }

    @Test public void touchRestartsTheTimer() {
        DimPolicy p = policy();
        p.onTouch(4 * MIN);
        assertFalse(p.shouldDim(8 * MIN));
        assertTrue(p.shouldDim(9 * MIN));
    }

    @Test public void touchWakesADimmedScreen() {
        DimPolicy p = policy();
        assertTrue(p.shouldDim(10 * MIN));
        p.onTouch(10 * MIN);
        assertFalse(p.shouldDim(10 * MIN));
    }

    @Test public void dimsAtOnceWhenTheRoomGoesDark() {
        DimPolicy p = policy();
        p.onLux(200, 0);
        p.onTouch(1 * MIN);
        p.onLux(1, 2 * MIN);
        assertTrue(p.isDark());
        assertTrue(p.shouldDim(2 * MIN));
    }

    @Test public void touchInTheDarkWakesForFiveMinutes() {
        DimPolicy p = policy();
        p.onLux(1, 0);
        assertTrue(p.shouldDim(1 * MIN));
        p.onTouch(1 * MIN);
        assertFalse(p.shouldDim(1 * MIN));
        assertFalse(p.shouldDim(6 * MIN - 1));
        assertTrue(p.shouldDim(6 * MIN));
    }

    @Test public void lightsComingOnBrightensForFiveMinutes() {
        DimPolicy p = policy();
        p.onLux(1, 0);
        assertTrue(p.shouldDim(20 * MIN));
        p.onLux(200, 20 * MIN);
        assertFalse(p.shouldDim(20 * MIN));
        assertTrue(p.shouldDim(25 * MIN));
    }

    @Test public void readingsBetweenThresholdsDontFlicker() {
        DimPolicy p = policy();
        p.onLux(200, 0);
        p.onLux(10, 1 * MIN);   // between 5 and 15: still light
        assertFalse(p.isDark());
        p.onLux(3, 2 * MIN);    // dark
        p.onLux(10, 3 * MIN);   // between: stays dark
        assertTrue(p.isDark());
        assertTrue(p.shouldDim(3 * MIN));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsInvertedThresholds() {
        new DimPolicy(IDLE, 20f, 10f, 0);
    }
}
