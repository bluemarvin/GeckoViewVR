/* -*- Mode: Java; c-basic-offset: 4; tab-width: 20; indent-tabs-mode: nil; -*-
 * vim: ts=4 sw=4 expandtab:
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.geckoviewvr;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;

import java.util.Locale;

import com.google.vr.cardboard.DisplaySynchronizer;
import com.google.vr.ndk.base.AndroidCompat;
import com.google.vr.ndk.base.GvrApi;
import com.google.vr.ndk.base.GvrLayout;
import com.google.vr.sdk.base.Constants;

import org.mozilla.gecko.GeckoSession;
import org.mozilla.gecko.GeckoSessionSettings;
import org.mozilla.gecko.GeckoView;
import org.mozilla.gecko.GeckoVRManager;
import org.mozilla.gecko.util.GeckoBundle;

public class MainActivity extends Activity {
    private static final String LOGTAG = "GeckoViewVR";

    private static final String DEFAULT_URL = "https://webvr.info/samples/03-vr-presentation.html";
    private static final int REQUEST_PERMISSIONS = 2;
    /* package */ static final int REQUEST_FILE_PICKER = 1;
    private FrameLayout mContainer;
    private GeckoView mGeckoView;
    private GeckoSession mGeckoSession;
    private GvrLayout mGVRLayout;
    private GvrApi mGVRApi;
    private EditText mURLBar;
    private int mOriginalRequestedOrientation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        if (AndroidCompat.setVrModeEnabled(MainActivity.this, true)) {
            AndroidCompat.setSustainedPerformanceMode(MainActivity.this, true);
        }

        mOriginalRequestedOrientation = getRequestedOrientation();

        // We're always in fullscreen
        setFullScreen();

        // Keep the screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mContainer = findViewById(R.id.container);

        GeckoVRManager.setGVRDelegate(new MyGVRDelegate());
        mGeckoView = findViewById(R.id.geckoview);
        mGeckoSession = new GeckoSession();
        mGeckoView.setSession(mGeckoSession);
        mGeckoSession.setNavigationListener(new Navigation());
        final BasicGeckoViewPrompt prompt = new BasicGeckoViewPrompt(this);
        prompt.filePickerRequestCode = REQUEST_FILE_PICKER;
        mGeckoSession.setPromptDelegate(prompt);
        final MyGeckoViewPermission permission = new MyGeckoViewPermission();
        permission.androidPermissionRequestCode = REQUEST_PERMISSIONS;
        mGeckoSession.setPermissionDelegate(permission);
        mGeckoView.getSettings().setBoolean(GeckoSessionSettings.USE_MULTIPROCESS, false);
        //mGeckoView.getSettings().setBoolean(GeckoSessionSettings.USE_REMOTE_DEBUGGER, true);
        mGeckoView.requestFocus();
        setupUI();
        loadFromIntent(getIntent());
    }

    @Override
    protected void onNewIntent(final Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        final String action = intent.getAction();
        if (Intent.ACTION_VIEW.equals(action)) {
            if (intent.getData() != null) {
                loadFromIntent(intent);
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (!stopPresenting()) {
            super.onBackPressed();
        }
    }

    @Override
    protected void onPause() {
        if (mGVRApi != null) {
            GeckoVRManager.setGVRPaused(true);
        }

        if (mGVRLayout != null) {
            mGVRLayout.onPause();
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mGVRApi != null) {
            GeckoVRManager.setGVRPaused(false);
        }

        if (mGVRLayout != null) {
            mGVRLayout.onResume();
            setFullScreen();
        }
    }

    @Override
    protected void onDestroy() {
        setRequestedOrientation(mOriginalRequestedOrientation);
        super.onDestroy();
        if (mGVRLayout != null) {
            GeckoVRManager.setGVRPresentingContext(0);
            mGVRLayout.shutdown();
            mGVRLayout = null;
        }
        if (mGVRApi != null) {
            GeckoVRManager.cleanupGVRNonPresentingContext();
        }
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode,
                                    final Intent data) {
        if (requestCode == REQUEST_FILE_PICKER) {
            final BasicGeckoViewPrompt prompt = (BasicGeckoViewPrompt)
                    mGeckoSession.getPromptDelegate();
            prompt.onFileCallbackResult(resultCode, data);
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onRequestPermissionsResult(final int requestCode,
                                           final String[] permissions,
                                           final int[] grantResults) {
        Log.e(LOGTAG,"Got onRequestPermissionsResult");
        if (requestCode == REQUEST_PERMISSIONS) {
            final MyGeckoViewPermission permission = (MyGeckoViewPermission)
                    mGeckoSession.getPermissionDelegate();
            permission.onRequestPermissionsResult(permissions, grantResults);
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private boolean stopPresenting() {
        setRequestedOrientation(mOriginalRequestedOrientation);
        if (mGVRLayout == null) {
            return false;
        }

        GeckoVRManager.setGVRPresentingContext(0);

        mContainer.removeView(mGVRLayout);
        mGVRLayout.shutdown();
        mGVRLayout = null;

        return true;
    }

    private void loadFromIntent(final Intent intent) {
        final Uri uri = intent.getData();
        if (intent.hasCategory(Constants.DAYDREAM_CATEGORY)) {
            Log.e(LOGTAG,"Intent has DAYDREAM_CATEGORY");
            return;
        }
        Log.e(LOGTAG, "Load URI from intent: " + (uri != null ? uri.toString() : DEFAULT_URL));
        String uriValue = (uri != null ? uri.toString() : DEFAULT_URL);
        mURLBar.setText(uriValue);
        mGeckoSession.loadUri(uriValue);
    }

    public void setFullScreen() {
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;


        getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    private class MyGVRDelegate implements GeckoVRManager.GVRDelegate {
        public long createNonPresentingContext() {
            createGVRApi();
            if (mGVRApi == null) {
                Log.e(LOGTAG, "Failed to create GvrApi");
                return 0;
            }
            return mGVRApi.getNativeGvrContext();
        }

        public void destroyNonPresentingContext() {
            if (mGVRApi == null) {
                return;
            }

            mGVRApi.shutdown();
            mGVRApi = null;
        }

        public boolean enableVRMode() {
            // Create a GvrLayout
            if (mGVRLayout != null) {
                return true;
            }

            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            mGVRLayout = new GvrLayout(MainActivity.this);
            mGVRLayout.setAsyncReprojectionEnabled(true);
            mGVRLayout.setPresentationView(new FrameLayout(MainActivity.this));
            mGVRLayout.getUiLayout().setCloseButtonListener(new Runnable() {
                @Override
                public void run() {
                    stopPresenting();
                }
            });

            // Put it on top of the GeckoView
            mContainer.addView(mGVRLayout,
                    new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                                                 FrameLayout.LayoutParams.MATCH_PARENT));
            mGVRLayout.onResume();
            setFullScreen();
            GeckoVRManager.setGVRPresentingContext(mGVRLayout.getGvrApi().getNativeGvrContext());
            return true;
        }

        public void disableVRMode() {
            stopPresenting();
        }
    }

    private class Navigation implements GeckoSession.NavigationListener {
        public void onLocationChange(GeckoSession session, String url) {
            mURLBar.setText(url);
        }
        public void onCanGoBack(GeckoSession session, boolean canGoBack){

        }
        public void onCanGoForward(GeckoSession session, boolean canGoForward){

        }
        public boolean onLoadUri(GeckoSession session, String uri, TargetWindow where) {
            return false;
        }
    }

    private void setupUI() {
        ImageButton reloadButton = findViewById(R.id.reloadButton);
        mURLBar = findViewById(R.id.urlBar);

        reloadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mGeckoSession != null) {
                    mGeckoSession.reload();
                }
            }
        });

        mURLBar.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                if (i == EditorInfo.IME_ACTION_NEXT) {
                    String uri = textView.getText().toString();
                    Log.e(LOGTAG, "Got URI: " + uri);
                    mGeckoSession.loadUri(uri);
                    setFullScreen();
                }
                return false;
            }
        });
    }

    private void createGVRApi() {
        if (mGVRApi != null) {
            return;
        }
        Context context = getApplicationContext();
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (windowManager  == null) {
            return;
        }
        Display display = windowManager.getDefaultDisplay();
        DisplaySynchronizer synchronizer = new DisplaySynchronizer(context, display);

        mGVRApi = new GvrApi(context, synchronizer);
    }

    private class MyGeckoViewPermission implements GeckoSession.PermissionDelegate {

        int androidPermissionRequestCode = 1;
        private Callback mCallback;

        void onRequestPermissionsResult(final String[] permissions,
                                               final int[] grantResults) {
            if (mCallback == null) {
                return;
            }

            final Callback cb = mCallback;
            mCallback = null;
            for (final int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    // At least one permission was not granted.
                    cb.reject();
                    return;
                }
            }
            Log.e(LOGTAG, "Permission Granted!");
            cb.grant();
        }

        @Override
        public void requestAndroidPermissions(final GeckoSession session, final String[] permissions,
                                              final Callback callback) {
            mCallback = callback;
            requestPermissions(permissions, androidPermissionRequestCode);
        }

        @Override
        public void requestContentPermission(final GeckoSession session, final String uri,
                                             final String type, final String access,
                                             final Callback callback) {
            final int resId;
            if ("geolocation".equals(type)) {
                resId = R.string.request_geolocation;
            } else if ("desktop-notification".equals(type)) {
                resId = R.string.request_notification;
            } else {
                Log.w(LOGTAG, "Unknown permission: " + type);
                callback.reject();
                return;
            }

            final String title = getString(resId, Uri.parse(uri).getAuthority());
            final BasicGeckoViewPrompt prompt = (BasicGeckoViewPrompt)
                    mGeckoSession.getPromptDelegate();
            prompt.promptForPermission(session, title, callback);
        }


        private void normalizeMediaName(final GeckoBundle[] sources) {
            if (sources == null) {
                return;
            }
            for (final GeckoBundle source : sources) {
                final String mediaSource = source.getString("mediaSource");
                String name = source.getString("name");
                if ("camera".equals(mediaSource)) {
                    if (name.toLowerCase(Locale.ENGLISH).contains("front")) {
                        name = getString(R.string.media_front_camera);
                    } else {
                        name = getString(R.string.media_back_camera);
                    }
                } else if (!name.isEmpty()) {
                    continue;
                } else if ("microphone".equals(mediaSource)) {
                    name = getString(R.string.media_microphone);
                } else {
                    name = getString(R.string.media_other);
                }
                source.putString("name", name);
            }
        }

        @Override
        public void requestMediaPermission(final GeckoSession session, final String uri,
                                           final GeckoBundle[] video,
                                           final GeckoBundle[] audio,
                                           final MediaCallback callback) {
            final String host = Uri.parse(uri).getAuthority();
            final String title;
            if (audio == null) {
                title = getString(R.string.request_video, host);
            } else if (video == null) {
                title = getString(R.string.request_audio, host);
            } else {
                title = getString(R.string.request_media, host);
            }

            normalizeMediaName(video);
            normalizeMediaName(audio);

            final BasicGeckoViewPrompt prompt = (BasicGeckoViewPrompt)
                    mGeckoSession.getPromptDelegate();
            prompt.promptForMedia(session, title, video, audio, callback);
        }
    }
}
