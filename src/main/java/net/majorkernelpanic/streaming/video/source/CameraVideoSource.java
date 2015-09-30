package net.majorkernelpanic.streaming.video.source;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.hardware.Camera;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;

import net.majorkernelpanic.streaming.MediaStream;
import net.majorkernelpanic.streaming.exceptions.CameraInUseException;
import net.majorkernelpanic.streaming.exceptions.InvalidSurfaceException;
import net.majorkernelpanic.streaming.gl.SurfaceView;
import net.majorkernelpanic.streaming.hw.NV21Convertor;
import net.majorkernelpanic.streaming.video.VideoQuality;
import net.majorkernelpanic.streaming.video.VideoStream;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class CameraVideoSource extends VideoSource {
    protected Camera mCamera;
    protected Thread mCameraThread;
    protected Looper mCameraLooper;

    protected boolean mCameraOpenedManually = true;
    protected int mCameraId = 0;
    protected boolean mFlashEnabled = false;
    protected boolean mUnlocked = false;

    public CameraVideoSource() {
        this(Camera.CameraInfo.CAMERA_FACING_BACK);
    }

    public CameraVideoSource(int camera) {
        setCamera(camera);
    }

    @Override
    public void beforeMediaRecorder() {
        // Reopens the camera if needed
        destroyCamera();
        createCamera();

        // The camera must be unlocked before the MediaRecorder can use it
        unlockCamera();
    }

    @Override
    public void afterMediaRecorder(MediaRecorder mediaRecorder) {
    }

    @Override
    public void beforeEncodeWithMediaCodecMethod1() {
        // Updates the parameters of the camera if needed
        createCamera();
        updateCamera();

        // Estimates the frame rate of the camera
        measureFramerate();

        // Starts the preview if needed
        if (!mPreviewStarted) {
            try {
                mCamera.startPreview();
                mPreviewStarted = true;
            } catch (RuntimeException e) {
                destroyCamera();
                throw e;
            }
        }
    }

    @SuppressLint("NewApi")
    @Override
    public void afterEncodeWithMediaCodecMethod1(final NV21Convertor convertor, final MediaCodec mediaCodec) {
        Camera.PreviewCallback callback = new Camera.PreviewCallback() {
            long now = System.nanoTime()/1000, oldnow = now, i=0;
            ByteBuffer[] inputBuffers = mediaCodec.getInputBuffers();
            @Override
            public void onPreviewFrame(byte[] data, Camera camera) {
                oldnow = now;
                now = System.nanoTime()/1000;
                if (i++>3) {
                    i = 0;
                    //Log.d(TAG,"Measured: "+1000000L/(now-oldnow)+" fps.");
                }
                try {
                    int bufferIndex = mediaCodec.dequeueInputBuffer(500000);
                    if (bufferIndex>=0) {
                        inputBuffers[bufferIndex].clear();
                        if (data == null) Log.e(TAG,"Symptom of the \"Callback buffer was too small\" problem...");
                        else convertor.convert(data, inputBuffers[bufferIndex]);
                        mediaCodec.queueInputBuffer(bufferIndex, 0, inputBuffers[bufferIndex].position(), now, 0);
                    } else {
                        Log.e(TAG,"No buffer available !");
                    }
                } finally {
                    mCamera.addCallbackBuffer(data);
                }
            }
        };

        for (int i=0;i<10;i++) mCamera.addCallbackBuffer(new byte[convertor.getBufferSize()]);
        mCamera.setPreviewCallbackWithBuffer(callback);
    }

    @Override
    public void initializeMediaRecorder(MediaRecorder mediaRecorder) {
        mediaRecorder.setCamera(mCamera);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);

        // After setVideoEncoder?
        mediaRecorder.setPreviewDisplay(mSurfaceView.getHolder().getSurface());
    }

    @Override
    public void beforeEncodeWithMediaCodecMethod2() {
        // Updates the parameters of the camera if needed
        createCamera();
        updateCamera();

        // Estimates the frame rate of the camera
        measureFramerate();
    }

    @SuppressLint("NewApi")
    @Override
    public void afterEncodeWithMediaCodecMethod2(MediaCodec mediaCodec) {
        Surface surface = mediaCodec.createInputSurface();
        // removed cast to majorkernelpanic SurfaceView, problem????
        mSurfaceView.addMediaCodecSurface(surface);
    }

    @Override
    public void initializeMediaFormat(MediaFormat mediaFormat) {

    }

    @Override
    public void beforeMediaCodecStart(MediaCodec mediaCodec) {

    }

    @Override
    public void beforeTestMediaCodecApi() {
        createCamera();
        updateCamera();
    }

    @Override
    public Map<String, Object> beforeTestMediaRecorderApi() {
        Map<String, Object> state = new HashMap<>();

        // Save flash state & set it to false so that led remains off while testing h264
        state.put("savedFlashState", mFlashEnabled);

        mFlashEnabled = false;

        state.put("previewStarted", mPreviewStarted);
        state.put("cameraOpen", mCamera != null);

        createCamera();

        // Stops the preview if needed
        if (mPreviewStarted) {
            lockCamera();
            try {
                mCamera.stopPreview();
            } catch (Exception e) {}
            mPreviewStarted = false;
        }

        try {
            Thread.sleep(100);
        } catch (InterruptedException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }

        unlockCamera();

        return state;
    }

    @Override
    public Map<String, Object> afterTestMediaRecorderApiPrepared(Map<String, Object> state, MediaRecorder mediaRecorder) {
        return state;
    }

    @Override
    public Map<String, Object> afterTestMediaRecorderApiStarted(Map<String, Object> state, MediaRecorder mediaRecorder) {
        return state;
    }

    @Override
    public void afterTestMediaRecorderApi(Map<String, Object> state) {
        Boolean cameraOpen = (Boolean)state.get("cameraOpen");

        lockCamera();

        if (!cameraOpen) destroyCamera();

        // Restore flash state
        mFlashEnabled = (Boolean)state.get("savedFlashState");

        if ((Boolean)state.get("previewStarted")) {
            // If the preview was started before the test, we try to restart it.
            try {
                mStream.startPreview();
            } catch (Exception e) {}
        }
    }

    @Override
    public void beforeStart() {
        if (!mPreviewStarted) mCameraOpenedManually = false;
    }

    @Override
    public void afterStart() {

    }

    @Override
    public void beforeStop() {
        if (mCamera != null) {
            if (mStream.getStreamingMethod() == MediaStream.MODE_MEDIACODEC_API) {
                mCamera.setPreviewCallbackWithBuffer(null);
            }
            if (mStream.getStreamingMethod() == MediaStream.MODE_MEDIACODEC_API_2) {
                ((SurfaceView) mSurfaceView).removeMediaCodecSurface();
            }
        }
    }

    @Override
    public void afterStop() {
        if (mCamera != null) {
            // We need to restart the preview
            if (!mCameraOpenedManually) {
                destroyCamera();
            } else {
                try {
                    mStream.startPreview();
                } catch (RuntimeException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void startPreview() {
        mCameraOpenedManually = true;
        if (!mPreviewStarted) {
            createCamera();
            updateCamera();
        }
    }

    @Override
    public void stopPreview() {
        mCameraOpenedManually = false;
    }

    /**
     * Sets the camera that will be used to capture video.
     * You can call this method at any time and changes will take effect next time you start the stream.
     * @param camera Can be either CameraInfo.CAMERA_FACING_BACK or CameraInfo.CAMERA_FACING_FRONT
     */
    public void setCamera(int camera) {
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        int numberOfCameras = Camera.getNumberOfCameras();
        for (int i=0;i<numberOfCameras;i++) {
            Camera.getCameraInfo(i, cameraInfo);
            if (cameraInfo.facing == camera) {
                mCameraId = i;
                break;
            }
        }
    }

    /**	Switch between the front facing and the back facing camera of the phone.
     * If {@link VideoStream#startPreview()} has been called, the preview will be  briefly interrupted.
     * If {@link VideoStream#start()} has been called, the stream will be  briefly interrupted.
     * You should not call this method from the main thread if you are already streaming.
     * @throws IOException
     * @throws RuntimeException
     **/
    public void switchCamera() throws RuntimeException, IOException {
        if (Camera.getNumberOfCameras() == 1) throw new IllegalStateException("Phone only has one camera !");
        boolean streaming = mStream.isStreaming();
        boolean previewing = mCamera != null && mCameraOpenedManually;
        mCameraId = (mCameraId == Camera.CameraInfo.CAMERA_FACING_BACK) ? Camera.CameraInfo.CAMERA_FACING_FRONT : Camera.CameraInfo.CAMERA_FACING_BACK;
        setCamera(mCameraId);
        mStream.stopPreview();
        mFlashEnabled = false;
        if (previewing) mStream.startPreview();
        if (streaming) mStream.start();
    }

    /**
     * Returns the id of the camera currently selected.
     * Can be either {@link Camera.CameraInfo#CAMERA_FACING_BACK} or
     * {@link Camera.CameraInfo#CAMERA_FACING_FRONT}.
     */
    public int getCamera() {
        return mCameraId;
    }

    /** Turns the LED on or off if phone has one. */
    public synchronized void setFlashState(boolean state) {
        // If the camera has already been opened, we apply the change immediately
        if (mCamera != null) {

            if (mStream.isStreaming() && mStream.getStreamingMethod() == MediaStream.MODE_MEDIARECORDER_API) {
                lockCamera();
            }

            Camera.Parameters parameters = mCamera.getParameters();

            // We test if the phone has a flash
            if (parameters.getFlashMode()==null) {
                // The phone has no flash or the choosen camera can not toggle the flash
                throw new RuntimeException("Can't turn the flash on!");
            } else {
                parameters.setFlashMode(state? Camera.Parameters.FLASH_MODE_TORCH: Camera.Parameters.FLASH_MODE_OFF);
                try {
                    mCamera.setParameters(parameters);
                    mFlashEnabled = state;
                } catch (RuntimeException e) {
                    mFlashEnabled = false;
                    throw new RuntimeException("Can't turn the flash on!");
                } finally {
                    if (mStream.isStreaming() && mStream.getStreamingMethod() == MediaStream.MODE_MEDIARECORDER_API) {
                        unlockCamera();
                    }
                }
            }
        } else {
            mFlashEnabled = state;
        }
    }

    /**
     * Toggles the LED of the phone if it has one.
     * You can get the current state of the flash with {@link CameraVideoSource#getFlashState()}.
     */
    public synchronized void toggleFlash() {
        setFlashState(!mFlashEnabled);
    }

    /** Indicates whether or not the flash of the phone is on. */
    public boolean getFlashState() {
        return mFlashEnabled;
    }

    /**
     * Opens the camera in a new Looper thread so that the preview callback is not called from the main thread
     * If an exception is thrown in this Looper thread, we bring it back into the main thread.
     * @throws RuntimeException Might happen if another app is already using the camera.
     */
    private void openCamera() throws RuntimeException {
        final Semaphore lock = new Semaphore(0);
        final RuntimeException[] exception = new RuntimeException[1];
        mCameraThread = new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare();
                mCameraLooper = Looper.myLooper();
                try {
                    mCamera = Camera.open(mCameraId);
                } catch (RuntimeException e) {
                    exception[0] = e;
                } finally {
                    lock.release();
                    Looper.loop();
                }
            }
        });
        mCameraThread.start();
        lock.acquireUninterruptibly();
        if (exception[0] != null) throw new CameraInUseException(exception[0].getMessage());
    }

    protected synchronized void createCamera() throws RuntimeException {
        if (mSurfaceView == null)
            throw new InvalidSurfaceException("Invalid surface !");
        if (mSurfaceView.getHolder() == null || !mSurfaceReady)
            throw new InvalidSurfaceException("Invalid surface !");

        if (mCamera == null) {
            openCamera();
            mStream.setUpdated(false);
            mUnlocked = false;
            mCamera.setErrorCallback(new Camera.ErrorCallback() {
                @Override
                public void onError(int error, Camera camera) {
                    // On some phones when trying to use the camera facing front the media server will die
                    // Whether or not this callback may be called really depends on the phone
                    if (error == Camera.CAMERA_ERROR_SERVER_DIED) {
                        // In this case the application must release the camera and instantiate a new one
                        Log.e(TAG, "Media server died !");
                        // We don't know in what thread we are so stop needs to be synchronized
                        mCameraOpenedManually = false;
                        mStream.stop();
                    } else {
                        Log.e(TAG,"Error unknown with the camera: "+error);
                    }
                }
            });

            try {

                // If the phone has a flash, we turn it on/off according to mFlashEnabled
                // setRecordingHint(true) is a very nice optimization if you plane to only use the Camera for recording
                Camera.Parameters parameters = mCamera.getParameters();
                if (parameters.getFlashMode()!=null) {
                    parameters.setFlashMode(mFlashEnabled? Camera.Parameters.FLASH_MODE_TORCH: Camera.Parameters.FLASH_MODE_OFF);
                }
                parameters.setRecordingHint(true);
                mCamera.setParameters(parameters);
                mCamera.setDisplayOrientation(mStream.getOrientation());

                try {
                    if (mStream.getStreamingMethod() == MediaStream.MODE_MEDIACODEC_API_2) {
                        mSurfaceView.startGLThread();
                        mCamera.setPreviewTexture(mSurfaceView.getSurfaceTexture());
                    } else {
                        mCamera.setPreviewDisplay(mSurfaceView.getHolder());
                    }
                } catch (IOException e) {
                    throw new InvalidSurfaceException("Invalid surface !");
                }

            } catch (RuntimeException e) {
                destroyCamera();
                throw e;
            }

        }
    }

    protected synchronized void destroyCamera() {
        if (mCamera != null) {
            if (mStream.isStreaming()) mStream.stopMediaEncoder();
            lockCamera();
            mCamera.stopPreview();
            try {
                mCamera.release();
            } catch (Exception e) {
                Log.e(TAG,e.getMessage()!=null?e.getMessage():"unknown error");
            }
            mCamera = null;
            mCameraLooper.quit();
            mUnlocked = false;
            mPreviewStarted = false;
        }
    }

    protected synchronized void updateCamera() throws RuntimeException {

        // The camera is already correctly configured
        if (mStream.getUpdated()) return;

        if (mPreviewStarted) {
            mPreviewStarted = false;
            mCamera.stopPreview();
        }

        Camera.Parameters parameters = mCamera.getParameters();
        mStream.setActiveVideoQuality(VideoQuality.determineClosestSupportedResolution(parameters, mStream.getActiveVideoQuality()));
        int[] max = VideoQuality.determineMaximumSupportedFramerate(parameters);

        VideoQuality quality = mStream.getActiveVideoQuality();

        double ratio = (double)quality.resX/(double)quality.resY;
        mSurfaceView.requestAspectRatio(ratio);

        parameters.setPreviewFormat(mCameraImageFormat);
        parameters.setPreviewSize(quality.resX, quality.resY);
        parameters.setPreviewFpsRange(max[0], max[1]);

        try {
            mCamera.setParameters(parameters);
            mCamera.setDisplayOrientation(mStream.getOrientation());
            mCamera.startPreview();
            mPreviewStarted = true;
            mStream.setUpdated(true);
        } catch (RuntimeException e) {
            destroyCamera();
            throw e;
        }
    }

    protected void lockCamera() {
        if (mUnlocked) {
            Log.d(TAG,"Locking camera");
            try {
                mCamera.reconnect();
            } catch (Exception e) {
                Log.e(TAG,e.getMessage());
            }
            mUnlocked = false;
        }
    }

    protected void unlockCamera() {
        if (!mUnlocked) {
            Log.d(TAG,"Unlocking camera");
            try {
                mCamera.unlock();
            } catch (Exception e) {
                Log.e(TAG,e.getMessage());
            }
            mUnlocked = true;
        }
    }

    /**
     * Computes the average frame rate at which the preview callback is called.
     * We will then use this average frame rate with the MediaCodec.
     * Blocks the thread in which this function is called.
     */
    private void measureFramerate() {
        final Semaphore lock = new Semaphore(0);

        final Camera.PreviewCallback callback = new Camera.PreviewCallback() {
            int i = 0, t = 0;
            long now, oldnow, count = 0;
            @Override
            public void onPreviewFrame(byte[] data, Camera camera) {
                i++;
                now = System.nanoTime()/1000;
                if (i>3) {
                    t += now - oldnow;
                    count++;
                }
                if (i>20) {
                    mStream.getActiveVideoQuality().framerate = (int) (1000000/(t/count)+1);
                    lock.release();
                }
                oldnow = now;
            }
        };

        mCamera.setPreviewCallback(callback);

        try {
            lock.tryAcquire(2, TimeUnit.SECONDS);
            Log.d(TAG, "Actual framerate: " + mStream.getActiveVideoQuality().framerate);
            SharedPreferences mSettings = mStream.getPreferences();
            if (mSettings != null) {
                VideoQuality mRequestedQuality = mStream.getVideoQuality();
                SharedPreferences.Editor editor = mSettings.edit();
                editor.putInt(MediaStream.PREF_PREFIX + "fps" + mRequestedQuality.framerate+","+mCameraImageFormat+","+mRequestedQuality.resX+mRequestedQuality.resY, mStream.getActiveVideoQuality().framerate);
                editor.commit();
            }
        } catch (InterruptedException e) {}

        mCamera.setPreviewCallback(null);

    }
}
