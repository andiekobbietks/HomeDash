package com.homedash.kiosk;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
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

    private static GeckoRuntime runtime;   // one per process, as GeckoView requires

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
        if (session != null) session.close();
        super.onDestroy();
    }
}
