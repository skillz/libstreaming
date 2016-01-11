package net.majorkernelpanic.streaming.video.source;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;

public class ProjectionVideoSource extends ActivityVideoSource {

    MediaProjectionManager mMediaProjectionManager;
    MediaProjection mMediaProjection;
    VirtualDisplay mVirtualDisplay;
    int mResultCode;
    Intent mResultData;
    private ActivityCallbacks mActivityCallbacks;

    // requires API 21
    @SuppressLint("NewApi")
    public ProjectionVideoSource(Activity activity, int resultCode, Intent resultData) {
        super(activity);

        mResultCode = resultCode;
        mResultData = resultData;

        mMediaProjectionManager = (MediaProjectionManager) activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        mActivityCallbacks = new ActivityCallbacks();

        activity.getApplication().registerActivityLifecycleCallbacks(mActivityCallbacks);
    }

    // requires API 21
    @SuppressLint("NewApi")
    @Override
    public void afterStart() {
        mMediaProjection = mMediaProjectionManager.getMediaProjection(mResultCode, mResultData);
        mVirtualDisplay = mMediaProjection.createVirtualDisplay(
                "skillz",
                mStream.getVideoQuality().resX,
                mStream.getVideoQuality().resY,
                DisplayMetrics.DENSITY_LOW,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mSurface,
                null, // callback
                null  // handler
        );
    }

    // requires API 21
    @SuppressLint("NewApi")
    @Override
    public void afterStop() {
        super.afterStop();

        if (mVirtualDisplay != null) {
            mVirtualDisplay.release();
            mVirtualDisplay = null;
        }

        if (mMediaProjection != null) {
            mMediaProjection.stop();
            mMediaProjection = null;
        }

        if (mActivityCallbacks != null) {
            mActivity.getApplication().unregisterActivityLifecycleCallbacks(mActivityCallbacks);
            mActivityCallbacks = null;
        }
    }

    class ActivityCallbacks implements Application.ActivityLifecycleCallbacks {
        @Override
        public void onActivityCreated(Activity activity, Bundle savedInstanceState) {

        }

        @Override
        public void onActivityStarted(Activity activity) {
            Log.i("SKILLZ", "Started");
        }

        @Override
        public void onActivityResumed(Activity activity) {
            Log.i("SKILLZ", "Resumed");
        }

        @Override
        public void onActivityPaused(Activity activity) {
            Log.i("SKILLZ", "Paused");
        }

        @Override
        public void onActivityStopped(Activity activity) {
            Log.i("SKILLZ", "Stopped");
        }

        @Override
        public void onActivitySaveInstanceState(Activity activity, Bundle outState) {

        }

        @Override
        public void onActivityDestroyed(Activity activity) {

        }
    }
}
