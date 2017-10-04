package org.mozilla.geckoviewvr;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.google.vr.cardboard.DisplaySynchronizer;
import com.google.vr.ndk.base.AndroidCompat;
import com.google.vr.ndk.base.GvrApi;
import com.google.vr.ndk.base.GvrLayout;

import org.mozilla.gecko.GeckoView;
import org.mozilla.gecko.GeckoViewInterfaces;
import org.mozilla.gecko.GeckoViewSettings;

public class MainActivity extends Activity {
    private static final String LOGTAG = "GeckoViewVR";

    private static final String DEFAULT_URL = "https://webvr.info/samples/03-vr-presentation.html";

    private FrameLayout mContainer;
    private GeckoView mGeckoView;
    private GvrLayout mGVRLayout;
    private GvrApi mGVRApi;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        if (AndroidCompat.setVrModeEnabled(MainActivity.this, true)) {
            AndroidCompat.setSustainedPerformanceMode(MainActivity.this, true);
        }

        // We're always in landscape and fullscreen
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        setFullScreen(true);

        // Keep the screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mContainer = (FrameLayout)findViewById(R.id.container);

        GeckoView.setLayerViewGVRDelegate(new MyGVRDelegate());
        mGeckoView = (GeckoView)findViewById(R.id.geckoview);
        mGeckoView.getSettings().setBoolean(GeckoViewSettings.USE_MULTIPROCESS, false);

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
        if (mGVRLayout != null) {
            mGVRLayout.onPause();
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mGVRLayout != null) {
            mGVRLayout.onResume();
            setFullScreen(true);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mGVRLayout != null) {
            mGVRLayout.shutdown();
        }
    }

    private boolean stopPresenting() {
        if (mGVRLayout == null) {
            return false;
        }

        GeckoView.setGVRContextAndSurface(0, null);

        mContainer.removeView(mGVRLayout);
        mGVRLayout.shutdown();
        mGVRLayout = null;
        return true;
    }

    private void loadFromIntent(final Intent intent) {
        final Uri uri = intent.getData();
        mGeckoView.loadUri(uri != null ? uri.toString() : DEFAULT_URL);
    }

    private void setFullScreen(boolean fullScreen) {
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
        if (fullScreen) {
            flags |= View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        }

        getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    private class MyGVRDelegate implements GeckoViewInterfaces.GVRDelegate {
        public long getGVRContext() {
            createGVRApi();
            if (mGVRApi == null) {
                Log.e(LOGTAG, "Failed to create GvrApi");
                return 0;
            }
            return mGVRApi.getNativeGvrContext();
        }

        public boolean enableVRMode() {
            // Create a GvrLayout
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

            // Tell gecko about the new GvrContext
            GeckoView.setGVRContextAndSurface(mGVRLayout.getGvrApi().getNativeGvrContext(), null);
            return true;
        }

        public void disableVRMode() {
            stopPresenting();
        }
    }

    private void createGVRApi() {
        if (mGVRApi != null) {
            return;
        }
        Context context = getApplicationContext();
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = windowManager.getDefaultDisplay();
        DisplaySynchronizer synchronizer = new DisplaySynchronizer(context, display);

        mGVRApi = new GvrApi(context, synchronizer);
    }

}