package org.mozilla.geckoviewvr;

import android.content.Intent;
import android.net.Uri;
import android.app.Activity;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.util.Log;

import com.google.vr.ndk.base.AndroidCompat;
import com.google.vr.ndk.base.GvrLayout;


import org.mozilla.gecko.GeckoView;
import org.mozilla.gecko.GeckoViewInterfaces;
import org.mozilla.gecko.GeckoViewSettings;
import org.mozilla.gecko.PrefsHelper;

public class MainActivity extends Activity {

    private static final String DEFAULT_URL = "https://webvr.info/samples/03-vr-presentation.html"; // "https://bluemarvin.github.io/cycles";
    private static final String USE_MULTIPROCESS_EXTRA = "use_multiprocess";
    private GeckoView mGeckoView;
    private GvrLayout mGVRLayout;
    private SurfaceView mSurfaceView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mGVRLayout = new GvrLayout(this);
        mGVRLayout.setAsyncReprojectionEnabled(true);
        GeckoView.setGVRContext(mGVRLayout.getGvrApi().getNativeGvrContext());
        GeckoView.setLayerViewGVRDelegate(new MyGVRDelegate());

        //    PrefsHelper.setPref("webgl.enable-surface-texture", true);
        mGeckoView = (GeckoView) findViewById(R.id.geckoview);
        mGeckoView.setContentListener(new MyGeckoViewContent());
        mGeckoView.getSettings().setBoolean(GeckoViewSettings.USE_MULTIPROCESS, false);
        mSurfaceView = new SurfaceView(this);
        SurfaceHolder holder = mSurfaceView.getHolder();
        holder.addCallback(new MySurfaceListener());
        mGVRLayout.setPresentationView(mSurfaceView);

        loadFromIntent(getIntent());
    }

    @Override
    protected void onNewIntent(final Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);

        if (intent.getData() != null) {
            loadFromIntent(intent);
        }
    }

    @Override
    public void onBackPressed () {
        mGeckoView.exitFullScreen();
    }

    @Override
    protected void onPause() {
        mGVRLayout.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mGVRLayout.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Destruction order is important; shutting down the GvrLayout will detach
        // the GLSurfaceView and stop the GL thread, allowing safe shutdown of
        // native resources from the UI thread.
        mGVRLayout.shutdown();
    }
    private void loadFromIntent(final Intent intent) {
        final Uri uri = intent.getData();
        mGeckoView.loadUri(uri != null ? uri.toString() : DEFAULT_URL);
    }

    private void setImmersiveSticky() {
        getWindow()
                .getDecorView()
                .setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    private class MyGeckoViewContent implements GeckoView.ContentListener {
        @Override
        public void onTitleChange(GeckoView view, String title) {
            //Log.i(LOGTAG, "Content title changed to " + title);
        }

        @Override
        public void onFullScreen(final GeckoView view, final boolean fullScreen) {
            if (fullScreen) {
                setImmersiveSticky();
            } else {
                getWindow().getDecorView().setSystemUiVisibility(0);
            }
        }

        @Override
        public void onContextMenu(GeckoView view, int screenX, int screenY,
                                  String uri, String elementSrc) {

        }
    }

    private class MyGVRDelegate implements GeckoViewInterfaces.GVRDelegate {
        public long getGVRContext() {
            return mGVRLayout.getGvrApi().getNativeGvrContext();
        }

        public boolean enableVRMode() {
            if (AndroidCompat.setVrModeEnabled(MainActivity.this, true)) {
                // Async reprojection decouples the app framerate from the display framerate,
                // allowing immersive interaction even at the throttled clockrates set by
                // sustained performance mode.
                AndroidCompat.setSustainedPerformanceMode(MainActivity.this, true);
            } else {
                Log.e("reb", "FAILED: AndroidCompat.setVrModeEnabled");
            }
            Log.e("reb", "hide mGeckoView");
            //mGeckoView.setVisibility(View.GONE);
            mGeckoView.hideSurface();
            ((ViewGroup)mGeckoView.getParent()).removeView(mGeckoView);
            setImmersiveSticky();
            Log.e("reb","setContentView(mGVRLayout)");
            setContentView(mGVRLayout);
            mGVRLayout.onResume();
            Log.e("reb","GeckoView.setGVRIsReady(true)");
            GeckoView.setGVRIsReady(true);
            return true;
        }

        public void disableVRMode() {
            getWindow().getDecorView().setSystemUiVisibility(0);
            Log.e("reb","remove mGVRLayout");
            ((ViewGroup)mGVRLayout.getParent()).removeView(mGVRLayout);
            Log.e("reb", "show mGeckoView");
            // mGeckoView.setVisibility(View.VISIBLE);
            mGeckoView.showSurface();
            GeckoView.setGVRIsReady(false);
        }
    }
    private class MySurfaceListener implements SurfaceHolder.Callback {
        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width,
                                   int height) {
            Log.e("reb", "GVR surface changed:" + width + "x" + height);
            GeckoView.setGVRSurface(mSurfaceView.getHolder().getSurface());
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            Log.e("reb", "GVR surface created");
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            Log.e("reb", "GVR surface destroyed");
            GeckoView.setGVRSurface(null);
        }
    }
}
