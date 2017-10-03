package org.mozilla.geckoviewvr;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.app.Activity;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.util.Log;

import android.content.Context;
import android.view.Display;
import android.view.WindowManager;

import java.util.Set;

import com.google.vr.cardboard.DisplaySynchronizer;
import com.google.vr.ndk.base.AndroidCompat;
import com.google.vr.ndk.base.GvrLayout;
import com.google.vr.ndk.base.GvrApi;


import org.mozilla.gecko.GeckoView;
import org.mozilla.gecko.GeckoViewInterfaces;
import org.mozilla.gecko.GeckoViewSettings;
//import org.mozilla.gecko.PrefsHelper;

public class MainActivity extends Activity {

    private static final String CATAGORY_DAYDREAM = "com.google.intent.category.DAYDREAM";
    private static final String ACTION_VIEW = "android.intent.action.VIEW";

    private static final String DEFAULT_URL = "https://webvr.info/samples/03-vr-presentation.html"; // "https://bluemarvin.github.io/cycles";
    private static final String USE_MULTIPROCESS_EXTRA = "use_multiprocess";
    private GeckoView mGeckoView;
    private GvrLayout mGVRLayout;
    private SurfaceView mSurfaceView;
    private GvrApi mGVRApi;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (AndroidCompat.setVrModeEnabled(MainActivity.this, true)) {
            // Async reprojection decouples the app framerate from the display framerate,
            // allowing immersive interaction even at the throttled clockrates set by
            // sustained performance mode.
            AndroidCompat.setSustainedPerformanceMode(MainActivity.this, true);
        } else {
            Log.e("reb", "FAILED: AndroidCompat.setVrModeEnabled");
        }
        //   mGVRLayout = new GvrLayout(this);
        //   mGVRLayout.setAsyncReprojectionEnabled(true);
        //   GeckoView.setGVRContext(mGVRLayout.getGvrApi().getNativeGvrContext());
        //   createGVRApi();
        GeckoView.setLayerViewGVRDelegate(new MyGVRDelegate());


        //    PrefsHelper.setPref("webgl.enable-surface-texture", true);
        //   mGVRLayout = new GvrLayout(MainActivity.this);
        mGeckoView = (GeckoView)findViewById(R.id.geckoview);
        mGeckoView.setContentListener(new MyGeckoViewContent());
        mGeckoView.getSettings().setBoolean(GeckoViewSettings.USE_MULTIPROCESS, false);
        //mSurfaceView = new SurfaceView(this);
        //SurfaceHolder holder = mSurfaceView.getHolder();
        //holder.addCallback(new MySurfaceListener());
        //mGVRLayout.setPresentationView(mSurfaceView);

        loadFromIntent(getIntent());
    }

    @Override
    protected void onNewIntent(final Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        final String action = intent.getAction();
        if (ACTION_VIEW.equals(action)) {
            if (intent.getData() != null) {
                loadFromIntent(intent);
            }
        } else {
            Set<String> cat = intent.getCategories();
            if (cat.contains(CATAGORY_DAYDREAM)) {
                Log.e("reb", "got catagory " + CATAGORY_DAYDREAM + " " + action);
                //GeckoView.setGVRContextAndSurface(mGVRLayout.getGvrApi().getNativeGvrContext(), null);
            } else {
                Log.e("reb", "*** Got unhandled intent: " + cat.toArray(new String[cat.size()])[0] + " " + action);
            }
        }
    }

    @Override
    public void onBackPressed() {
        mGeckoView.exitFullScreen();
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
        }
        setImmersiveSticky();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Destruction order is important; shutting down the GvrLayout will detach
        // the GLSurfaceView and stop the GL thread, allowing safe shutdown of
        // native resources from the UI thread.
        if (mGVRLayout != null) {
            mGVRLayout.shutdown();
        }
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
        int mRequestedOrientation;

        public long getGVRContext() {
            createGVRApi();
            if (mGVRApi == null) {
                Log.e("reb", "Failed to create GvrApi");
                return 0;
            }
            return mGVRApi.getNativeGvrContext();
        }

        public boolean enableVRMode() {
            //if (mGVRLayout != null) {
            //    return true;
            //}
            if (AndroidCompat.setVrModeEnabled(MainActivity.this, true)) {
                // Async reprojection decouples the app framerate from the display framerate,
                // allowing immersive interaction even at the throttled clockrates set by
                // sustained performance mode.
                AndroidCompat.setSustainedPerformanceMode(MainActivity.this, true);
            } else {
                Log.e("reb", "FAILED: AndroidCompat.setVrModeEnabled");
            }
            mGVRLayout = new GvrLayout(MainActivity.this);
            mGVRLayout.setAsyncReprojectionEnabled(true);
            mGVRLayout.setPresentationView(new FrameLayout(MainActivity.this)); // mSurfaceView);
            mRequestedOrientation = getRequestedOrientation();
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

            Log.e("reb", "hide mGeckoView");
            mGeckoView.hideSurface();
            setImmersiveSticky();
            Log.e("reb", "setContentView(mGVRLayout)");
            setContentView(mGVRLayout);
            mGVRLayout.onResume();
            GeckoView.setGVRContextAndSurface(mGVRLayout.getGvrApi().getNativeGvrContext(), null);
            return true;
        }

        public void disableVRMode() {
            GeckoView.setGVRContextAndSurface(0, null);
            getWindow().getDecorView().setSystemUiVisibility(0);
            Log.e("reb", "remove mGVRLayout");
            ((ViewGroup) mGVRLayout.getParent()).removeView(mGVRLayout);
            MainActivity.this.setRequestedOrientation(mRequestedOrientation);
            mGVRLayout.shutdown();
            mGVRLayout = null;
            AndroidCompat.setVrModeEnabled(MainActivity.this, false);

            Log.e("reb", "show mGeckoView");
            mGeckoView.showSurface();
        }
    }

    private class MySurfaceListener implements SurfaceHolder.Callback {
        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width,
                                   int height) {
            Log.e("reb", "GVR surface changed:" + width + "x" + height);
            if (mGVRLayout == null) {
                Log.e("reb", "Unable to set surface, mGVRLayout is null");
                return;
            }
            GeckoView.setGVRContextAndSurface(mGVRLayout.getGvrApi().getNativeGvrContext(),
                    mSurfaceView.getHolder().getSurface());
            mGVRLayout.onResume();
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            Log.e("reb", "GVR surface created");
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            Log.e("reb", "GVR surface destroyed");
            GeckoView.setGVRContextAndSurface(0, null);
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