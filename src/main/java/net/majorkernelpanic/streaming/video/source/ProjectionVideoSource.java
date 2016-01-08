package net.majorkernelpanic.streaming.video.source;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.util.DisplayMetrics;

public class ProjectionVideoSource extends ActivityVideoSource {

    MediaProjectionManager mMediaProjectionManager;
    MediaProjection mMediaProjection;
    VirtualDisplay mVirtualDisplay;
    int mResultCode;
    Intent mResultData;
    DisplayMetrics mMetrics;

    // requires API 21
    @SuppressLint("NewApi")
    public ProjectionVideoSource(Activity activity, int resultCode, Intent resultData) {
        super(activity);

        mResultCode = resultCode;
        mResultData = resultData;

        mMediaProjectionManager = (MediaProjectionManager) activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        mMetrics = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getMetrics(mMetrics);
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

        mRunnable = new Runnable() {
            @Override
            public void run() {
                if (mRunnable != null) mHandler.postDelayed(this, mRefreshRate);
            }
        };

        mHandler.postDelayed(mRunnable, mRefreshRate);
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
    }
}
