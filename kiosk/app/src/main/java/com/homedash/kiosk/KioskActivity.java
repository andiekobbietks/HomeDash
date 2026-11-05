package com.homedash.kiosk;

import android.app.Activity;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.ContentBlocking;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoRuntimeSettings;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;
import org.mozilla.geckoview.GeckoView;
import org.mozilla.geckoview.WebRequestError;

/**
 * Full-screen home screen that shows the FamilyPlanner dashboard and nothing else.
 */
public class KioskActivity extends Activity {

    private static final String TAG = "HomeDashKiosk";
    private static final long RETRY_MS = 30_000;              // after a failed load
    private static final long ROUTINE_RELOAD_MS = 6 * 3600_000L; // keeps a long-running page fresh

    // Dimming: the frame has a light sensor but no motion sensor (see DimPolicy).
    private static final long IDLE_DIM_MS = 5 * 60_000L;
    private static final float DIM_BRIGHTNESS = 0.05f;   // about 5% backlight, still readable
    private static final float DARK_BELOW_LUX = 5f;      // calibrate: README "Dimming"
    private static final float LIGHT_ABOVE_LUX = 15f;
    private static final long DIM_CHECK_MS = 5_000;

    private static GeckoRuntime runtime;   // one per process, as GeckoView requires

    private DimPolicy dimPolicy;
    private boolean dimmed = false;
    private boolean swallowingWakeTouch = false;
    private SensorManager sensorManager;

    private final SensorEventListener lightListener = new SensorEventListener() {
        @Override public void onSensorChanged(SensorEvent event) {
            boolean wasDark = dimPolicy.isDark();
            dimPolicy.onLux(event.values[0], SystemClock.elapsedRealtime());
            if (wasDark != dimPolicy.isDark()) {
                Log.i(TAG, "Room is now " + (dimPolicy.isDark() ? "dark" : "light") + " at " + event.values[0] + " lux");
            }
            updateDimming();
        }
        @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    };

    private final Runnable dimCheck = new Runnable() {
        @Override public void run() {
            updateDimming();
            handler.postDelayed(this, DIM_CHECK_MS);
        }
    };

    private final Allowlist allowlist = new Allowlist(BuildConfig.PLANNER_HOST);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private GeckoView view;
    private GeckoSession session;

    private final Runnable goHome = new Runnable() {
        @Override public void run() {
            if (session != null) session.loadUri(BuildConfig.START_URL);
        }
    };

    private final Runnable routineReload = new Runnable() {
        @Override public void run() {
            goHome.run();
            handler.postDelayed(this, ROUTINE_RELOAD_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        view = new GeckoView(this);
        setContentView(view);

        if (runtime == null) {
            GeckoRuntimeSettings settings = new GeckoRuntimeSettings.Builder()
                    .remoteDebuggingEnabled(false)
                    .aboutConfigEnabled(false)
                    .consoleOutput(false)
                    .contentBlocking(new ContentBlocking.Settings.Builder()
                            // Safe Browsing would contact Google; the allowlist does that job here.
                            .safeBrowsing(ContentBlocking.SafeBrowsing.NONE)
                            .build())
                    .build();
            runtime = GeckoRuntime.create(getApplicationContext(), settings);
        }

        openSession();
        handler.postDelayed(routineReload, ROUTINE_RELOAD_MS);

        dimPolicy = new DimPolicy(IDLE_DIM_MS, DARK_BELOW_LUX, LIGHT_ABOVE_LUX, SystemClock.elapsedRealtime());
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        Sensor light = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        if (light != null) {
            sensorManager.registerListener(lightListener, light, SensorManager.SENSOR_DELAY_NORMAL);
        } else {
            Log.w(TAG, "No light sensor; dimming on touch timeout only");
        }
        handler.postDelayed(dimCheck, DIM_CHECK_MS);
    }

    private void updateDimming() {
        boolean shouldDim = dimPolicy.shouldDim(SystemClock.elapsedRealtime());
        if (shouldDim == dimmed) return;
        dimmed = shouldDim;
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = dimmed
                ? DIM_BRIGHTNESS
                : WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;   // back to the system setting
        getWindow().setAttributes(lp);
        Log.i(TAG, dimmed ? "Dimmed" : "Brightened");
    }

    /** A touch on a dimmed screen only wakes it; the whole gesture is swallowed. */
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            boolean wasDimmed = dimmed;
            dimPolicy.onTouch(SystemClock.elapsedRealtime());
            updateDimming();
            swallowingWakeTouch = wasDimmed;
        }
        if (swallowingWakeTouch) {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                swallowingWakeTouch = false;
            }
            return true;
        }
        return super.dispatchTouchEvent(event);
    }

    private void openSession() {
        session = new GeckoSession(new GeckoSessionSettings.Builder()
                .usePrivateMode(false)   // keep the sign-in cookie across restarts
                .allowJavascript(true)
                .build());

        session.setNavigationDelegate(new GeckoSession.NavigationDelegate() {
            @Override
            public GeckoResult<AllowOrDeny> onLoadRequest(GeckoSession s, LoadRequest request) {
                if (allowlist.allows(request.uri)) return GeckoResult.allow();
                Log.w(TAG, "Blocked navigation to " + request.uri);
                return GeckoResult.deny();
            }

            @Override
            public GeckoResult<GeckoSession> onNewSession(GeckoSession s, String uri) {
                // No pop-ups or new tabs: open allowed links in place, drop the rest.
                if (allowlist.allows(uri)) s.loadUri(uri);
                return null;
            }

            @Override
            public GeckoResult<String> onLoadError(GeckoSession s, String uri, WebRequestError error) {
                Log.w(TAG, "Load error " + error.code + " for " + uri + ", retrying");
                scheduleRetry();
                // Gecko's own about:neterror page is outside the allowlist, so show ours.
                return GeckoResult.fromValue(ErrorPage.dataUri(error.code, RETRY_MS / 1000));
            }
        });

        session.setContentDelegate(new GeckoSession.ContentDelegate() {
            @Override public void onCrash(GeckoSession s) { restartSession(); }
            @Override public void onKill(GeckoSession s) { restartSession(); }
        });

        session.open(runtime);
        view.setSession(session);
        session.loadUri(BuildConfig.START_URL);
    }

    private void scheduleRetry() {
        handler.removeCallbacks(goHome);
        handler.postDelayed(goHome, RETRY_MS);
    }

    private void restartSession() {
        Log.w(TAG, "Content process died, reopening the session");
        if (session != null) session.close();
        openSession();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemBars();
    }

    private void hideSystemBars() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override
    public void onBackPressed() {
        // Never leave the kiosk; just step back within the allowed pages.
        if (session != null) session.goBack();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (sensorManager != null) sensorManager.unregisterListener(lightListener);
        if (session != null) session.close();
        super.onDestroy();
    }
}
