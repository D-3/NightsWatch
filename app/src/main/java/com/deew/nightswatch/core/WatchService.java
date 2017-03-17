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
 * Created by DeW on 2017/3/14.
 */

public class WatchService extends Service implements SurfaceHolder.Callback, ITXLivePushListener {

    private final String TAG = WatchService.class.getSimpleName();

    private Camera mCamera;
    private Camera.Parameters mCamParameters;

    private boolean isOpen = false;

    private WindowManager mWindowManager;
    private View mPreviewContainer;
    private SurfaceHolder mHolder;
    private int mHeight = 480;
    private int mWidth = 640;

    private TXLivePushConfig mLivePushConfig;
    private TXLivePusher mLivePusher;
    private boolean mVideoPublish = false;

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if(intent.getAction() != null && intent.getAction().equals("start_main")) {
            mWindowManager = (WindowManager) getApplicationContext().getSystemService(WINDOW_SERVICE);
            mPreviewContainer = View.inflate(this, R.layout.camera_container, null);
            SurfaceView surfaceView = (SurfaceView) mPreviewContainer.findViewById(R.id.preview);
            SurfaceHolder holder = surfaceView.getHolder();
            holder.addCallback(this);
            mWindowManager.addView(mPreviewContainer, getPreviewLayoutParams());

            mLivePusher     = new TXLivePusher(getApplicationContext());
            mLivePushConfig = new TXLivePushConfig();
            startPublishRtmp();
        }else{
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopPublishRtmp();
        mLivePusher = null;
        mLivePushConfig = null;

    }

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

    private boolean openCamera(int tagInfo) {
        Log.d(TAG,  "openCamera");
        // 尝试开启摄像头
        try {
            mCamera = Camera.open(getCameraId(tagInfo));
            mCamParameters = mCamera.getParameters(); //配置参数
            mCamParameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
            if(tagInfo == Camera.CameraInfo.CAMERA_FACING_BACK){
                mCamParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);//1连续对焦
            }
            mCamParameters.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_AUTO);
            mCamParameters.setJpegQuality(90);
            mCamParameters.setPreviewFormat(ImageFormat.YV12);
            mCamParameters.setPreviewSize(mWidth, mHeight);
            mCamera.setParameters(mCamParameters);
            isOpen = true;
            List<Camera.Size> si = mCamParameters.getSupportedPreviewSizes();
            for (int i = 0; i < si.size(); i++) {
                Log.d(TAG, "摄像头" + tagInfo + "支持：" + si.get(i).width + "*" + si.get(i).height);
            }

            mCamera.setPreviewDisplay(mHolder);

            mCamera.setPreviewCallback(new Camera.PreviewCallback() {
                @Override
                public void onPreviewFrame(byte[] data, Camera camera) {
                    Log.d(TAG, "back onPreviewFrame " + data);
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

    private void removePreviewWindow(){
        if(mPreviewContainer != null){
            mWindowManager.removeView(mPreviewContainer);
        }
    }

    public synchronized boolean releaseCameraAndStopPreview() {
        Log.d(TAG, "releaseCameraAndStopPreview----");

        if (mCamera != null) {
            isOpen = false;
            try {
                mCamera.stopPreview();
                mCamera.release();
                mCamera = null;
                mCamParameters = null;
            } catch (Exception e) {
                e.printStackTrace();
                Log.d(TAG,  e.getMessage());
            }
            removePreviewWindow();
            return true;
        } else {
            removePreviewWindow();
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
        params.gravity = Gravity.LEFT;
        //悬浮窗起点坐标
        params.x = 0;
        params.y = 0;
        //悬浮窗大小
        params.width = 200;
        params.height = 200;
        return params;
    }


    private boolean startPublishRtmp(){
        int customModeType = 0;

        //【示例代码1】设置自定义视频采集逻辑 （自定义视频采集逻辑不要调用startPreview）
        customModeType |= CUSTOM_MODE_VIDEO_CAPTURE;
        customModeType |= CUSTOM_MODE_AUDIO_CAPTURE;

        mLivePushConfig.setCustomModeType(customModeType);
        mLivePushConfig.setVideoResolution(TXLiveConstants.VIDEO_RESOLUTION_TYPE_640_360);
        mLivePushConfig.setAutoAdjustBitrate(false);
        mLivePushConfig.setVideoBitrate(800);
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
        if(mLivePusher != null){
            mLivePusher.setPushListener(null);
            mLivePusher.stopPusher();
        }

        if(mLivePushConfig != null) {
            mLivePushConfig.setPauseImg(null);
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        Log.d(TAG, "surfaceCreated");

        mHolder = surfaceHolder;
        openCamera(Camera.CameraInfo.CAMERA_FACING_BACK);
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int format, int w, int h) {
        Log.d(TAG, "surfaceChanged format=" + format + " w=" + w + " h=" + h);
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

        if (mLivePusher != null) {
            mLivePusher.onLogRecord("[event:" + event + "]" + msg + "\n");
        }
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
            mLivePusher.setConfig(mLivePushConfig);
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
}
