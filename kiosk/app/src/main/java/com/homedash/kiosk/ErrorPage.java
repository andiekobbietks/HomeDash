package com.homedash.kiosk;

import android.util.Base64;

import java.nio.charset.StandardCharsets;

/** A self-contained page shown while the planner can't be reached. */
final class ErrorPage {

    private ErrorPage() {}

    static String dataUri(int errorCode, long retrySeconds) {
        String html = "<!DOCTYPE html><html><head><meta charset='utf-8'>"
                + "<meta name='viewport' content='width=device-width'>"
                + "<style>html,body{height:100%;margin:0;background:#111;color:#bbb;"
                + "font:28px sans-serif;display:flex;align-items:center;justify-content:center;"
                + "text-align:center}small{display:block;margin-top:12px;font-size:16px;color:#666}</style>"
                + "</head><body><div>Can't reach the planner<small>Retrying every "
                + retrySeconds + " seconds &middot; error " + errorCode + "</small></div></body></html>";
        return "data:text/html;base64,"
                + Base64.encodeToString(html.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
    }
}
