package org.mozilla.geckoviewvr;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
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

import com.google.vr.cardboard.DisplaySynchronizer;
import com.google.vr.ndk.base.AndroidCompat;
import com.google.vr.ndk.base.GvrApi;
import com.google.vr.ndk.base.GvrLayout;
import com.google.vr.sdk.base.Constants;

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
    private ImageButton mReloadButton;
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
        setFullScreen(true);

        // Keep the screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mContainer = (FrameLayout)findViewById(R.id.container);

        GeckoView.setGVRDelegate(new MyGVRDelegate());
        mGeckoView = (GeckoView)findViewById(R.id.geckoview);
        mGeckoView.getSettings().setBoolean(GeckoViewSettings.USE_MULTIPROCESS, false);
        mGeckoView.setNavigationListener(new MyNavigationListener());
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
            GeckoView.setGVRPaused(true);
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
            GeckoView.setGVRPaused(false);
        }

        if (mGVRLayout != null) {
            mGVRLayout.onResume();
            setFullScreen(true);
        }
    }

    @Override
    protected void onDestroy() {
        setRequestedOrientation(mOriginalRequestedOrientation);
        super.onDestroy();
        if (mGVRLayout != null) {
            GeckoView.setGVRPresentingContext(0);
            mGVRLayout.shutdown();
            mGVRLayout = null;
        }
        if (mGVRApi != null) {
            GeckoView.cleanupGVRNonPresentingContext();
        }
    }

    /*
    @Override
    protected void onSaveInstanceState(Bundle outState) {

    }

    @Override
    protected void onRestoreInstanceState (Bundle savedInstanceState) {

    }
    */

    private boolean stopPresenting() {
        setRequestedOrientation(mOriginalRequestedOrientation);
        if (mGVRLayout == null) {
            return false;
        }

        GeckoView.setGVRPresentingContext(0);

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
        mGeckoView.loadUri(uriValue);
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
        public long createGVRNonPresentingContext() {
            createGVRApi();
            if (mGVRApi == null) {
                Log.e(LOGTAG, "Failed to create GvrApi");
                return 0;
            }
            return mGVRApi.getNativeGvrContext();
        }

        public void destroyGVRNonPresentingContext() {
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

            GeckoView.setGVRPresentingContext(mGVRLayout.getGvrApi().getNativeGvrContext());
            return true;
        }

        public void disableVRMode() {
            stopPresenting();
        }
    }

    private class MyNavigationListener implements GeckoView.NavigationListener {
        public void onLocationChange(GeckoView view, String url) {
            mURLBar.setText(url);
        }
        public void onCanGoBack(GeckoView view, boolean canGoBack){

        }
        public void onCanGoForward(GeckoView view, boolean canGoForward){

        }
        public boolean onLoadUri(GeckoView view, String uri, TargetWindow where) {
            return false;
        }
    }

    private void setupUI() {
        mReloadButton = (ImageButton)findViewById(R.id.reloadButton);
        mURLBar = (EditText)findViewById(R.id.urlBar);

        mReloadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mGeckoView != null) {
                    mGeckoView.reload();
                }
            }
        });

        mURLBar.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                //    if ((i == EditorInfo.IME_NULL) && (keyEvent.getAction() == KeyEvent.ACTION_UP)) {
                if (i == EditorInfo.IME_ACTION_NEXT) {
                    String uri = textView.getText().toString();
                    Log.e(LOGTAG, "Got URI: " + uri);
                    mGeckoView.loadUri(uri);
                    setFullScreen(true);
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
        Display display = windowManager.getDefaultDisplay();
        DisplaySynchronizer synchronizer = new DisplaySynchronizer(context, display);

        mGVRApi = new GvrApi(context, synchronizer);
    }

}