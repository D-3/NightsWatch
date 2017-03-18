package com.deew.nightswatch.core;

import android.app.Service;
import android.content.Intent;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import com.deew.nightswatch.R;
import com.deew.nightswatch.live.URLUtils;
import com.tencent.rtmp.ITXLivePushListener;
import com.tencent.rtmp.TXLiveConstants;
import com.tencent.rtmp.TXLivePushConfig;
import com.tencent.rtmp.TXLivePusher;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.List;

import static com.tencent.rtmp.TXLiveConstants.CUSTOM_MODE_AUDIO_CAPTURE;
import static com.tencent.rtmp.TXLiveConstants.CUSTOM_MODE_VIDEO_CAPTURE;

/**
 * Created by DeW on 2017/3/15.
 */

public class AvinService extends Service implements SurfaceHolder.Callback, ITXLivePushListener {

    private final String TAG = AvinService.class.getSimpleName();

    private WindowManager mWindowManager;
    private TXLivePushConfig mLivePushConfig;
    private TXLivePusher mLivePusher;

    @Override
    public void onCreate() {
        mWindowManager = (WindowManager) getApplicationContext().getSystemService(WINDOW_SERVICE);
        View cameraContainer = View.inflate(this, R.layout.camera_container, null);
        SurfaceView surfaceView = (SurfaceView) cameraContainer.findViewById(R.id.preview);
        mHolder = surfaceView.getHolder();
        mHolder.addCallback(this);
        mWindowManager.addView(cameraContainer, getPreviewLayoutParams());

        mLivePusher     = new TXLivePusher(getApplicationContext());
        mLivePushConfig = new TXLivePushConfig();
        mVideoPublish = startPublishRtmp();

        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private Camera mCamera;
    private Camera.Parameters mParameters;
    private SurfaceHolder mHolder;
    private boolean isOpen = false;

    /**
     * @param tagInfo
     * @return 得到特定camera info的id
     */
    private int getCameraId(int tagInfo) {
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        // 开始遍历摄像头，得到camera info
        int cameraId, cameraCount;
        Log.d(TAG,  "cameraCount: " + Camera.getNumberOfCameras());
        for (cameraId = 0, cameraCount = Camera.getNumberOfCameras(); cameraId < cameraCount; cameraId++) {
            Camera.getCameraInfo(cameraId, cameraInfo);
            Log.d(TAG,  "cameraInfo: " + cameraInfo.facing);
            if (cameraInfo.facing == tagInfo) {
                break;
            }
        }
        return cameraId;
    }

    private int mHeight = 540;
    private int mWidth = 960;
    private boolean openCamera(int tagInfo) {
        Log.d(TAG,  "openCamera");
        // 尝试开启摄像头
        try {
            mCamera = Camera.open(getCameraId(tagInfo));
            mParameters = mCamera.getParameters(); //配置参数
            mParameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
            if(tagInfo == Camera.CameraInfo.CAMERA_FACING_BACK){
                mParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);//1连续对焦
            }
            mParameters.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_AUTO);
            mParameters.setJpegQuality(90);
            mParameters.setPreviewFormat(ImageFormat.YV12);
            mParameters.setPreviewSize(mWidth, mHeight);
            mCamera.setParameters(mParameters);
            isOpen = true;
            List<Camera.Size> si = mParameters.getSupportedPreviewSizes();
            for (int i = 0; i < si.size(); i++) {
                Log.d(TAG, "摄像头" + tagInfo + "支持：" + si.get(i).width + "*" + si.get(i).height);
            }

            mCamera.setPreviewDisplay(mHolder);
            mCamera.setPreviewCallback(new Camera.PreviewCallback() {
                @Override
                public void onPreviewFrame(byte[] data, Camera camera) {
                    Log.d(TAG, "front onPreviewFrame " + data);
                    mLivePusher.sendCustomVideoData(data, TXLivePusher.YUV_420P, mWidth, mHeight);
                }
            });
            mCamera.startPreview();
        } catch (RuntimeException e) {
            releaseCameraAndStopPreview();
            Log.d(TAG,  e.getMessage());
            return false;
        } catch (IOException e){
            releaseCameraAndStopPreview();
            Log.d(TAG,  e.getMessage());
            return false;
        }
        // 开启后置失败
        if (mCamera == null) {
            return false;
        }

        return true;
    }

    public synchronized boolean releaseCameraAndStopPreview() {
        Log.d(TAG, "stopPreviewAndReleaseCamera----");
        if (mCamera != null) {
            isOpen = false;
            try {
                mCamera.stopPreview();
                mCamera.release();
                mCamera = null;
            } catch (Exception e) {
                e.printStackTrace();
                Log.d(TAG,  e.getMessage());
            }
            return true;
        } else {
            return false;
        }
    }

//    private void intiPreview(){
//        View cameraContainer = View.inflate(this, R.layout.camera_container, null);
//        SurfaceView surfaceView = (SurfaceView) cameraContainer.findViewById(R.id.preview);
//        SurfaceHolder holder = surfaceView.getHolder();
//        holder.addCallback(this);
//        mWindowManager.addView(cameraContainer, getPreviewLayoutParams());
//    }

    private WindowManager.LayoutParams getPreviewLayoutParams(){
        WindowManager.LayoutParams params = new WindowManager.LayoutParams();
        params.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        params.gravity = Gravity.RIGHT;
        //悬浮窗起点坐标
        params.x = 0;
        params.y = 0;
        //悬浮窗大小
        params.width = 640;
        params.height = WindowManager.LayoutParams.MATCH_PARENT;
        return params;
    }


    private boolean mVideoPublish = false;

    private boolean startPublishRtmp(){
        int customModeType = 0;

        //【示例代码1】设置自定义视频采集逻辑 （自定义视频采集逻辑不要调用startPreview）
        customModeType |= CUSTOM_MODE_VIDEO_CAPTURE;
        customModeType |= CUSTOM_MODE_AUDIO_CAPTURE;

        mLivePushConfig.setCustomModeType(customModeType);
        mLivePushConfig.setVideoResolution(TXLiveConstants.VIDEO_RESOLUTION_TYPE_960_540);
        mLivePushConfig.setAutoAdjustBitrate(false);
        mLivePushConfig.setVideoBitrate(1500);
        mLivePushConfig.setVideoFPS(15);
        mLivePushConfig.setVideoEncodeGop(3);
        mLivePushConfig.setHardwareAcceleration(true);
        mLivePusher.setConfig(mLivePushConfig);
        mLivePusher.setPushListener(this);
        mLivePusher.startPusher(URLUtils.getBigUrl(this));

        return true;
    }

    private void stopPublishRtmp() {

        mVideoPublish = false;

        releaseCameraAndStopPreview();
        mLivePusher.setPushListener(null);
        mLivePusher.stopPusher();
        // TODO: 2017/3/16 close preview

        if(mLivePushConfig != null) {
            mLivePushConfig.setPauseImg(null);
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        Log.d(TAG, "surfaceCreated");

        //这里不设置的重新赋值的话
        mHolder = surfaceHolder;
        openCamera(Camera.CameraInfo.CAMERA_FACING_FRONT);
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int format, int w, int h) {
        Log.d(TAG, "surfaceChanged format=" + format + " w=" + w + " h=" + h);

//        // If your preview can change or rotate, take care of those events here.
//        // Make sure to stop the preview before resizing or reformatting it.
//
//        if(surfaceHolder.getSurface() == null){
//            // preview surface does not exist
//            return;
//        }
//
//        // stop preview before making changes
//        try {
//            mCamera.stopPreview();
//        }catch (Exception e){
//            e.printStackTrace();
//        }
//
//        // TODO: 2017/3/14 set preview size and make any resize, rotate or reformatting changes here
//
//        //start preview with new settings
//        try {
//            mCamera.setPreviewDisplay(surfaceHolder);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        Log.d(TAG, "surfaceDestroyed");
        // empty. Take care of releasing the Camera preview in your activity.
    }

    @Override
    public void onPushEvent(int event, Bundle param) {
        String msg = param.getString(TXLiveConstants.EVT_DESCRIPTION);
        appendEventLog(event, msg);
//        if (mScrollView.getVisibility() == View.VISIBLE){
//            mLogViewEvent.setText(mLogMsg);
//            scroll2Bottom(mScrollView, mLogViewEvent);
//        }
//        if (mLivePusher != null) {
//            mLivePusher.onLogRecord("[event:" + event + "]" + msg + "\n");
//        }
        //错误还是要明确的报一下
        if (event < 0) {
            Toast.makeText(this.getApplicationContext(), param.getString(TXLiveConstants.EVT_DESCRIPTION), Toast.LENGTH_SHORT).show();
            if(event == TXLiveConstants.PUSH_ERR_OPEN_CAMERA_FAIL){
                stopPublishRtmp();
            }
        }

        if (event == TXLiveConstants.PUSH_ERR_NET_DISCONNECT) {
            stopPublishRtmp();
        }
        else if (event == TXLiveConstants.PUSH_WARNING_HW_ACCELERATION_FAIL) {
            Toast.makeText(this.getApplicationContext(), param.getString(TXLiveConstants.EVT_DESCRIPTION), Toast.LENGTH_SHORT).show();
            mLivePushConfig.setHardwareAcceleration(false);
//            mBtnHWEncode.setBackgroundResource(R.drawable.quick2);
            mLivePusher.setConfig(mLivePushConfig);
//            mHWVideoEncode = false;
        }
        else if (event == TXLiveConstants.PUSH_ERR_SCREEN_CAPTURE_UNSURPORT) {
            stopPublishRtmp();
        }
        else if (event == TXLiveConstants.PUSH_ERR_SCREEN_CAPTURE_START_FAILED) {
            stopPublishRtmp();
        } else if (event == TXLiveConstants.PUSH_EVT_CHANGE_RESOLUTION) {
            Log.d(TAG, "change resolution to " + param.getInt(TXLiveConstants.EVT_PARAM2) + ", bitrate to" + param.getInt(TXLiveConstants.EVT_PARAM1));
        } else if (event == TXLiveConstants.PUSH_EVT_CHANGE_BITRATE) {
            Log.d(TAG, "change bitrate to" + param.getInt(TXLiveConstants.EVT_PARAM1));
        }
    }

    @Override
    public void onNetStatus(Bundle status) {
        String str = getNetStatusString(status);
        Log.d(TAG, "net status, " + str);
//        mLogViewStatus.setText(str);
        Log.d(TAG, "Current status, CPU:"+status.getString(TXLiveConstants.NET_STATUS_CPU_USAGE)+
                ", RES:"+status.getInt(TXLiveConstants.NET_STATUS_VIDEO_WIDTH)+"*"+status.getInt(TXLiveConstants.NET_STATUS_VIDEO_HEIGHT)+
                ", SPD:"+status.getInt(TXLiveConstants.NET_STATUS_NET_SPEED)+"Kbps"+
                ", FPS:"+status.getInt(TXLiveConstants.NET_STATUS_VIDEO_FPS)+
                ", ARA:"+status.getInt(TXLiveConstants.NET_STATUS_AUDIO_BITRATE)+"Kbps"+
                ", VRA:"+status.getInt(TXLiveConstants.NET_STATUS_VIDEO_BITRATE)+"Kbps");
//        if (mLivePusher != null){
//            mLivePusher.onLogRecord("[net state]:\n"+str+"\n");
//        }
    }

    //公用打印辅助函数
    protected String getNetStatusString(Bundle status) {
        String str = String.format("%-14s %-14s %-12s\n%-14s %-14s %-12s\n%-14s %-14s %-12s\n%-14s %-12s",
                "CPU:"+status.getString(TXLiveConstants.NET_STATUS_CPU_USAGE),
                "RES:"+status.getInt(TXLiveConstants.NET_STATUS_VIDEO_WIDTH)+"*"+status.getInt(TXLiveConstants.NET_STATUS_VIDEO_HEIGHT),
                "SPD:"+status.getInt(TXLiveConstants.NET_STATUS_NET_SPEED)+"Kbps",
                "JIT:"+status.getInt(TXLiveConstants.NET_STATUS_NET_JITTER),
                "FPS:"+status.getInt(TXLiveConstants.NET_STATUS_VIDEO_FPS),
                "ARA:"+status.getInt(TXLiveConstants.NET_STATUS_AUDIO_BITRATE)+"Kbps",
                "QUE:"+status.getInt(TXLiveConstants.NET_STATUS_CODEC_CACHE)+"|"+status.getInt(TXLiveConstants.NET_STATUS_CACHE_SIZE),
                "DRP:"+status.getInt(TXLiveConstants.NET_STATUS_CODEC_DROP_CNT)+"|"+status.getInt(TXLiveConstants.NET_STATUS_DROP_SIZE),
                "VRA:"+status.getInt(TXLiveConstants.NET_STATUS_VIDEO_BITRATE)+"Kbps",
                "SVR:"+status.getString(TXLiveConstants.NET_STATUS_SERVER_IP),
                "AVRA:"+status.getInt(TXLiveConstants.NET_STATUS_SET_VIDEO_BITRATE));
        return str;
    }

    StringBuffer          mLogMsg = new StringBuffer("");
    private final int mLogMsgLenLimit = 3000;

    //公用打印辅助函数
    protected void appendEventLog(int event, String message) {
        String str = "receive event: " + event + ", " + message;
        Log.d(TAG, str);
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");
        String date = sdf.format(System.currentTimeMillis());
        while(mLogMsg.length() >mLogMsgLenLimit ){
            int idx = mLogMsg.indexOf("\n");
            if (idx == 0)
                idx = 1;
            mLogMsg = mLogMsg.delete(0,idx);
        }
        mLogMsg = mLogMsg.append("\n" + "["+date+"]" + message);
    }

//    private WindowManager mWindowManager;
//
//    @Override
//    public void onCreate() {
//        mWindowManager = (WindowManager) getApplicationContext().getSystemService(WINDOW_SERVICE);
//        super.onCreate();
//    }
//
//    @Override
//    public int onStartCommand(Intent intent, int flags, int startId) {
//        intiPreview(getCamera(CAMERA_TYPE_AVIN), Camera.CameraInfo.CAMERA_FACING_FRONT);
//        return super.onStartCommand(intent, flags, startId);
//    }
//
//    @Nullable
//    @Override
//    public IBinder onBind(Intent intent) {
//        return null;
//    }
//
//    private void intiPreview(Camera camera, int facing){
////        View cameraContainer = View.inflate(this, R.layout.camera_container, null);
////        ViewGroup content = (ViewGroup) cameraContainer.findViewById(R.id.content);
////        CameraPreview preview = new CameraPreview(this.getApplicationContext(), camera, facing);
////        content.addView(preview);
////        mWindowManager.addView(cameraContainer, getPreviewLayoutParams());
//    }
//
//    private WindowManager.LayoutParams getPreviewLayoutParams(){
//        WindowManager.LayoutParams params = new WindowManager.LayoutParams();
//        params.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
//        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
//        params.gravity = Gravity.RIGHT;
//        //悬浮窗起点坐标
//        params.x = 0;
//        params.y = 0;
//        //悬浮窗大小
//        params.width = 640;
//        params.height = WindowManager.LayoutParams.MATCH_PARENT;
//        return params;
//    }
//
//    private Camera getCamera(int cameraType){
//        int cameraCount = Camera.getNumberOfCameras();
//        if(cameraCount <= 0){
//            return null;
//        }
//
//        switch(cameraType){
//            case CAMERA_TYPE_BACK_FACING:
//                return openCamera(Camera.CameraInfo.CAMERA_FACING_BACK);
//            case CameraManager.CAMERA_TYPE_AVIN:
//                return openCamera(Camera.CameraInfo.CAMERA_FACING_FRONT);
//            case CameraManager.CAMERA_TYPE_OTG:
//                break;
//        }
//
//        return null;
//    }
//
//    private Camera openCamera(int cameraId){
//        Log.d(TAG, "openCamera cameraId=" + cameraId);
//        Camera camera = null;
//        try{
//            camera = Camera.open(cameraId);
//        }catch (Exception e){
//            e.printStackTrace();
//            Log.e(TAG, "fail to open camera " + cameraId);
//        }
//        return  camera;
//    }
}
