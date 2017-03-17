package com.deew.nightswatch.core.camera.view;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.IOException;
import java.util.List;

/**
 * A basic Camera preview class
 *
 * Created by DeW on 2017/3/14.
 */

public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {
    private static final String TAG = CameraPreview.class.getSimpleName();

    private SurfaceHolder mHolder;
    private Camera mCamera;

    public CameraPreview(Context context, Camera camera, int facing) {
        super(context);
        if(camera == null){
            throw new IllegalArgumentException("can't create a preview without a camera instance");
        }

        mCamera = camera;
        Camera.Parameters params =  mCamera.getParameters();
//        List<String> focusModes = params.getSupportedFocusModes();
//        if(focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)){
//            params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
//        }
        params.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
        //前置摄像头不支持连续对焦
        if(facing == Camera.CameraInfo.CAMERA_FACING_BACK){
            params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);//1连续对焦
        }
        params.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_AUTO);
        params.setJpegQuality(90);
        params.setPreviewFormat(ImageFormat.YV12);
        params.setPreviewSize(640, 480);
        List<Camera.Size> si = params.getSupportedPreviewSizes();
        for (int i = 0; i < si.size(); i++) {
            Log.d(TAG, "摄像头支持：" + si.get(i).width + "*" + si.get(i).height);
        }
        mCamera.setParameters(params);
        // Install a SurfaceHolder.Callback so we get notified when the
        // underlying surface is created and destroyed.
        mHolder = getHolder();
        mHolder.addCallback(this);
        // deprecated setting, but required on Android versions prior to 3.0
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        Log.d(TAG, "surfaceCreated");
        // The Surface has been created, now tell the camera where to draw the preview.
        try {
            mCamera.setPreviewDisplay(surfaceHolder);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int format, int w, int h) {
        Log.d(TAG, "surfaceChanged format=" + format + " w=" + w + " h=" + h);

        // If your preview can change or rotate, take care of those events here.
        // Make sure to stop the preview before resizing or reformatting it.

        if(mHolder.getSurface() == null){
            // preview surface does not exist
            return;
        }

        // stop preview before making changes
        try {
            mCamera.stopPreview();
        }catch (Exception e){
            e.printStackTrace();
        }

        // TODO: 2017/3/14 set preview size and make any resize, rotate or reformatting changes here

        //start preview with new settings
        try {
            mCamera.setPreviewDisplay(mHolder);
            mCamera.setPreviewCallback(new Camera.PreviewCallback() {
                @Override
                public void onPreviewFrame(byte[] bytes, Camera camera) {
                    Log.d(TAG, "onPreviewFrame " + bytes);
                }
            });
            mCamera.startPreview();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        Log.d(TAG, "surfaceDestroyed");
        // empty. Take care of releasing the Camera preview in your activity.
    }


}
