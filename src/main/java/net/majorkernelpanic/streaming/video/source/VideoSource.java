package net.majorkernelpanic.streaming.video.source;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.util.Log;
import android.view.SurfaceHolder;

import net.majorkernelpanic.streaming.gl.SurfaceView;
import net.majorkernelpanic.streaming.hw.NV21Convertor;
import net.majorkernelpanic.streaming.video.VideoStream;

import java.util.Map;

public abstract class VideoSource {
    protected final static String TAG = "VideoSource";

    protected SurfaceView mSurfaceView = null;
    protected SurfaceHolder.Callback mSurfaceHolderCallback = null;
    protected boolean mSurfaceReady = false;
    protected boolean mPreviewStarted = false;
    protected int mCameraImageFormat;
    protected VideoStream mStream;

    public abstract void beforeMediaRecorder();

    public abstract void initializeMediaRecorder(MediaRecorder mediaRecorder);

    public abstract void afterMediaRecorder(MediaRecorder mediaRecorder);

    public abstract void beforeEncodeWithMediaCodecMethod1();

    public abstract void afterEncodeWithMediaCodecMethod1(final NV21Convertor convertor, final MediaCodec mediaCodec);

    public abstract void beforeEncodeWithMediaCodecMethod2();

    public abstract void afterEncodeWithMediaCodecMethod2(MediaCodec mediaCodec);

    public abstract void initializeMediaFormat(MediaFormat mediaFormat);

    public abstract void beforeMediaCodecStart(MediaCodec mediaCodec);

    public abstract void beforeTestMediaCodecApi();

    public abstract Map<String, Object> beforeTestMediaRecorderApi();

    public abstract Map<String, Object> afterTestMediaRecorderApiPrepared(Map<String, Object> state, MediaRecorder mediaRecorder);

    public abstract Map<String, Object> afterTestMediaRecorderApiStarted(Map<String, Object> state, MediaRecorder mediaRecorder);

    public abstract void afterTestMediaRecorderApi(Map<String, Object> state);

    public abstract void beforeStart();

    public abstract void afterStart();

    public abstract void beforeStop();

    public abstract void afterStop();

    public abstract void startPreview();

    public abstract void stopPreview();

    public abstract boolean isColorFormatValid(int format);

    public void setStream(VideoStream stream) {
        mStream = stream;
    }

    /**
     * Sets a Surface to show a preview of recorded media (video).
     * You can call this method at any time and changes will take effect next time you call {@link VideoStream#start()}.
     */
    public synchronized void setSurfaceView(SurfaceView view) {
        mSurfaceView = view;
        if (mSurfaceHolderCallback != null && mSurfaceView != null && mSurfaceView.getHolder() != null) {
            mSurfaceView.getHolder().removeCallback(mSurfaceHolderCallback);
        }
        if (mSurfaceView != null && mSurfaceView.getHolder() != null) {
            mSurfaceHolderCallback = new SurfaceHolder.Callback() {
                @Override
                public void surfaceDestroyed(SurfaceHolder holder) {
                    mSurfaceReady = false;
                    mStream.stopPreview();
                    Log.d(TAG, "Surface destroyed!");
                }
                @Override
                public void surfaceCreated(SurfaceHolder holder) {
                    mSurfaceReady = true;
                }
                @Override
                public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                    Log.d(TAG,"Surface Changed!");
                }
            };
            mSurfaceView.getHolder().addCallback(mSurfaceHolderCallback);
            mSurfaceReady = true;
        }
    }

    public void setImageFormat(int imageFormat) {
        mCameraImageFormat = imageFormat;
    }

}
