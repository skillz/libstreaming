package net.majorkernelpanic.streaming.video.source;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.os.Handler;
import android.view.Surface;
import android.view.View;

import net.majorkernelpanic.streaming.MediaStream;
import net.majorkernelpanic.streaming.hw.NV21Convertor;

import java.util.Map;
import java.util.Random;

public class ActivityVideoSource extends VideoSource {

    protected Activity mActivity;
    protected Surface mSurface;
    protected Handler mHandler;
    protected Runnable mRunnable;
    protected int mRefreshRate = 50;

    public ActivityVideoSource(Activity activity) {
        super();
        mActivity = activity;
        mHandler = new Handler();
        mRunnable = null;
    }

    @Override
    public void beforeMediaRecorder() {
        startSurface();
    }

    @Override
    public void initializeMediaRecorder(MediaRecorder mediaRecorder) {
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);

        // After setVideoEncoder?
        if (mSurfaceView != null) mediaRecorder.setPreviewDisplay(mSurfaceView.getHolder().getSurface());
    }

    // Requires API 21
    @SuppressLint("NewApi")
    @Override
    public void afterMediaRecorder(MediaRecorder mediaRecorder) {
        mSurface = mediaRecorder.getSurface();
    }

    @Override
    public void beforeEncodeWithMediaCodecMethod1() {
        startSurface();
    }

    @Override
    public void afterEncodeWithMediaCodecMethod1(NV21Convertor convertor, MediaCodec mediaCodec) {
    }

    @Override
    public void beforeEncodeWithMediaCodecMethod2() {
        startSurface();
    }

    @Override
    public void afterEncodeWithMediaCodecMethod2(MediaCodec mediaCodec) {
    }

    // requires API 16
    @SuppressLint("NewApi")
    @Override
    public void initializeMediaFormat(MediaFormat mediaFormat) {
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
    }

    // requires API 18
    @SuppressLint("NewApi")
    @Override
    public void beforeMediaCodecStart(MediaCodec mediaCodec) {
        mSurface = mediaCodec.createInputSurface();
    }

    @Override
    public void beforeTestMediaCodecApi() {
        startSurface();
    }

    @Override
    public Map<String, Object> beforeTestMediaRecorderApi() {
        startSurface();

        return null;
    }

    // Requires API 21
    @SuppressLint("NewApi")
    @Override
    public Map<String, Object> afterTestMediaRecorderApiPrepared(Map<String, Object> state, MediaRecorder mediaRecorder) {
        mSurface = mediaRecorder.getSurface();

        return state;
    }

    // Requires API 21
    @SuppressLint("NewApi")
    @Override
    public Map<String, Object> afterTestMediaRecorderApiStarted(Map<String, Object> state, MediaRecorder mediaRecorder) {
        Random random = new Random();

        Canvas canvas = mSurface.lockCanvas(null);
        Paint paint = new Paint();
        paint.setColor(Color.rgb(random.nextInt(255), random.nextInt(255), random.nextInt(255)));
        canvas.drawLine(random.nextFloat() * 100.0f, random.nextFloat() * 100.0f, random.nextFloat() * 100.0f, random.nextFloat() * 100.0f, paint);

        mSurface.unlockCanvasAndPost(canvas);

        return state;
    }

    @Override
    public void afterTestMediaRecorderApi(Map<String, Object> state) {
        if (mSurface != null) mSurface.release();
        mSurface = null;
    }

    @Override
    public void beforeStart() {

    }

    @Override
    public void afterStart() {
        mRunnable = new Runnable() {
            //Random random = new Random();

            @Override
            public void run() {
                Canvas canvas = null;

                try {
                    if (mStream.isStreaming() && mSurface != null) {
                        canvas = mSurface.lockCanvas(null);
                        View view = mActivity.findViewById(android.R.id.content);
                        view.setDrawingCacheEnabled(true);
                        Bitmap bitmap = view.getDrawingCache();
                        Paint paint = new Paint();
                        paint.setAntiAlias(true);
                        paint.setFilterBitmap(true);
                        canvas.drawBitmap(bitmap, null, new Rect(0, 0, mStream.getVideoQuality().resX, mStream.getVideoQuality().resY), paint);
                    }
                } catch (Exception e) {
                    // TODO: handle exception
                } finally{
                    if (canvas != null) mSurface.unlockCanvasAndPost(canvas);
                    if (mRunnable != null) mHandler.postDelayed(this, mRefreshRate);
                }
            }
        };

        mHandler.postDelayed(mRunnable, mRefreshRate);
    }

    @Override
    public void beforeStop() {
        if (mRunnable != null) mHandler.removeCallbacks(mRunnable);
        mRunnable = null;
    }

    @Override
    public void afterStop() {
        if (mSurface != null) mSurface.release();
        mSurface = null;
    }

    @Override
    public void startPreview() {
        startSurface();
    }

    @Override
    public void stopPreview() {

    }

    @Override
    public boolean isColorFormatValid(int format) {
        return format == MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface;
    }

    private void startSurface() {
        mStream.setUpdated(false);

        if (mStream.getStreamingMethod() == MediaStream.MODE_MEDIACODEC_API_2) {
            mSurfaceView.startGLThread();
        }
    }
}
