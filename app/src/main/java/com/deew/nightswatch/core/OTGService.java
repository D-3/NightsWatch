package com.deew.nightswatch.core;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.hardware.usb.UsbDevice;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.Gravity;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import com.bird.IntelligentVehicle.BirdIntelligentVehicle;
import com.deew.nightswatch.R;
import com.deew.nightswatch.live.URLUtils;
import com.serenegiant.usb.DeviceFilter;
import com.serenegiant.usb.IButtonCallback;
import com.serenegiant.usb.IFrameCallback;
import com.serenegiant.usb.IStatusCallback;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.USBMonitor.OnDeviceConnectListener;
import com.serenegiant.usb.USBMonitor.UsbControlBlock;
import com.serenegiant.usb.UVCCamera;
import com.serenegiant.usb.widget.UVCCameraTextureView;
import com.tencent.rtmp.ITXLivePushListener;
import com.tencent.rtmp.TXLiveConstants;
import com.tencent.rtmp.TXLivePushConfig;
import com.tencent.rtmp.TXLivePusher;

import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.tencent.rtmp.TXLiveConstants.CUSTOM_MODE_AUDIO_CAPTURE;
import static com.tencent.rtmp.TXLiveConstants.CUSTOM_MODE_VIDEO_CAPTURE;

/**
 * Created by DeW on 2017/3/15.
 */

public class OTGService extends Service implements ITXLivePushListener {

    private final String TAG = OTGService.class.getSimpleName();

    // for thread pool
    private static final int CORE_POOL_SIZE = 1;        // initial/minimum threads
    private static final int MAX_POOL_SIZE = 4;            // maximum threads
    private static final int KEEP_ALIVE_TIME = 10;        // time periods while keep the idle thread
    protected static final ThreadPoolExecutor EXECUTER
            = new ThreadPoolExecutor(CORE_POOL_SIZE, MAX_POOL_SIZE, KEEP_ALIVE_TIME,
            TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());

    // for accessing USB and USB camera
    private USBMonitor mUSBMonitor;
    private UVCCamera mUVCCamera;
    private UVCCameraTextureView mUVCCameraView;
    private Surface mPreviewSurface;

    private int mHeight = 360;
    private int mWidth = 640;
    private WindowManager mWindowManager;
    private View mPreviewContainer;

    private TXLivePushConfig mLivePushConfig;
    private TXLivePusher mLivePusher;
    private boolean mVideoPublish = false;

    private BirdIntelligentVehicle mBirdIntelligentVehicle = new BirdIntelligentVehicle();

    private Context mContext;

    @Override
    public void onCreate() {
        super.onCreate();
        mContext = this;
        //关键类的创建，放在所有操作之前
        mWindowManager = (WindowManager) getApplicationContext().getSystemService(WINDOW_SERVICE);
        mUSBMonitor = new USBMonitor(this, mOnDeviceConnectListener);
        mLivePusher = new TXLivePusher(getApplicationContext());
        mLivePushConfig = new TXLivePushConfig();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent.getAction() != null && intent.getAction().equals("start_otg")) {

            //先打开预览窗口，避免先打开摄像头，回调预览时，窗口依然没有初始化而报错
            initPreviewWindow();
            //初始化OTG摄像头，回调中进行下一步操作
            initOtgCamera();
            //只要Pusher和PushConfig初始化后即可开启推流，随便什么时候都可以调用；和预览互不关联，可单独使用。
            startPublishRtmp();
        } else {
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        //停止推流
        stopPublishRtmp();

        //关闭预览，释放相机
        stopPreviewAndReleaseCamera();

        //移除预览窗口
        removePreviewWindow();

        mLivePusher = null;
        mLivePushConfig = null;
        mUSBMonitor = null;
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


    private WindowManager.LayoutParams getPreviewLayoutParams() {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams();
        params.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        params.gravity = Gravity.RIGHT;
        //悬浮窗起点坐标
        params.x = 0;
        params.y = 0;
        //悬浮窗大小
        params.width = 200;
        params.height = 200;
        return params;
    }

    public void requestPermission() {
        final List<DeviceFilter> filter = DeviceFilter.getDeviceFilters(mContext, R.xml.device_filter);
        List<UsbDevice> usbDevices = mUSBMonitor.getDeviceList(filter.get(0));
        Log.d(TAG, "getDevices=" + (usbDevices == null ? null : usbDevices.size()));
        if (usbDevices != null && usbDevices.size() > 0) {
            Log.d(TAG, "device=" + usbDevices.get(0).getDeviceName());
            mUSBMonitor.requestPermission((usbDevices.get(0)));
        }
    }

    private final OnDeviceConnectListener mOnDeviceConnectListener = new OnDeviceConnectListener() {
        @Override
        public void onAttach(final UsbDevice device) {
            Log.d(TAG, "onAttach device=" + device == null ? null : device.getDeviceName());

            Toast.makeText(mContext, "USB_DEVICE_ATTACHED", Toast.LENGTH_SHORT).show();

            requestPermission();
        }

        @Override
        public void onConnect(final UsbDevice device, final USBMonitor.UsbControlBlock ctrlBlock, final boolean createNew) {
            Log.d(TAG, "onConnect device=" + device);

            openCameraAndStartPreview(ctrlBlock);
        }

        @Override
        public void onDisconnect(final UsbDevice device, final UsbControlBlock ctrlBlock) {
            Log.d(TAG, "onDisconnect device=" + device == null ? null : device.getDeviceName());

            // XXX you should check whether the comming device equal to camera device that currently using
            if (mUVCCamera != null) {
                mUVCCamera.close();
                if (mPreviewSurface != null) {
                    mPreviewSurface.release();
                    mPreviewSurface = null;
                }
            }
        }

        @Override
        public void onCancel(UsbDevice device) {
            Log.d(TAG, "onCancel device=" + device == null ? null : device.getDeviceName());
        }

        @Override
        public void onDettach(final UsbDevice device) {
            Log.d(TAG, "onDettach device=" + device == null ? null : device.getDeviceName());

            Toast.makeText(mContext, "USB_DEVICE_DETACHED", Toast.LENGTH_SHORT).show();
        }

    };

    // if you need frame data as byte array on Java side, you can use this callback method with UVCCamera#setFrameCallback
    // if you need to create Bitmap in IFrameCallback, please refer following snippet.
    private final IFrameCallback mIFrameCallback = new IFrameCallback() {
        @Override
        public void onFrame(byte[] frame) {
            Log.d(TAG, "back onPreviewFrame " + frame);
            mLivePusher.sendCustomVideoData(frame, TXLivePusher.YUV_420SP, mWidth, mHeight);
        }

    };


    /**
     * 开始推流，在推流之前确保摄像头已经初始化并且开始预览
     *
     * @return 推流是否已开启
     */
    private boolean startPublishRtmp() {
        Log.d(TAG, "startPublishRtmp");

        // TODO: 2017/3/17  开始推流之前可先判断设备是否打开，是否有预览数据

        int customModeType = 0;

        //设置自定义视频采集逻辑 （自定义视频采集逻辑不要调用startPreview）
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
        mLivePusher.startPusher(URLUtils.getSmallUrl(this));

        return true;
    }

    /**
     * 初始化摄像头
     */
    private void initOtgCamera() {
        Log.d(TAG, "initOtgCamera");
        Log.d(TAG, "open bird otg and register usb monitor");
        mBirdIntelligentVehicle.BirdOpenOtg(getApplicationContext());
        //监听usb, 有usb事件(设备连接，断开等)便会触发OnDeviceConnectListener回调
        mUSBMonitor.register();
    }

    /**
     * 初始化预览窗口
     */
    private void initPreviewWindow() {
        Log.d(TAG, "initPreviewWindow");
        if (mPreviewContainer == null) {
            mPreviewContainer = View.inflate(this, R.layout.otg_container, null);
            mUVCCameraView = (UVCCameraTextureView) mPreviewContainer.findViewById(R.id.preview);
            mUVCCameraView.setAspectRatio(UVCCamera.DEFAULT_PREVIEW_WIDTH / (float) UVCCamera.DEFAULT_PREVIEW_HEIGHT);
            mWindowManager.addView(mPreviewContainer, getPreviewLayoutParams());
        }
    }

    /**
     * 打开摄像头并开始预览，应先确保预览窗口已经初始化
     *
     * @param ctrlBlock
     */
    private void openCameraAndStartPreview(final USBMonitor.UsbControlBlock ctrlBlock) {
        Log.d(TAG, "openCameraAndStartPreview ");
        if (mUVCCamera != null) {
            Log.d(TAG, "destroy old UVCCamera first");
            mUVCCamera.destroy();
        }
        mUVCCamera = new UVCCamera();
        EXECUTER.execute(new Runnable() {
            @Override
            public void run() {
                mUVCCamera.open(ctrlBlock);
                mUVCCamera.setStatusCallback(new IStatusCallback() {
                    @Override
                    public void onStatus(final int statusClass, final int event, final int selector,
                                         final int statusAttribute, final ByteBuffer data) {
                        Log.d(TAG, "onStatus(statusClass=" + statusClass
                                + "; " +
                                "event=" + event + "; " +
                                "selector=" + selector + "; " +
                                "statusAttribute=" + statusAttribute + "; " +
                                "data=...)");
                    }
                });
                mUVCCamera.setButtonCallback(new IButtonCallback() {
                    @Override
                    public void onButton(final int button, final int state) {
                        Log.d(TAG, "onButton(button=" + button + "; " +
                                "state=" + state + ")");
                    }
                });
//					mUVCCamera.setPreviewTexture(mUVCCameraView.getSurfaceTexture());
                if (mPreviewSurface != null) {
                    mPreviewSurface.release();
                    mPreviewSurface = null;
                }
                try {
//                        mUVCCamera.setPreviewSize(UVCCamera.DEFAULT_PREVIEW_WIDTH, UVCCamera.DEFAULT_PREVIEW_HEIGHT, UVCCamera.FRAME_FORMAT_MJPEG);
                    mUVCCamera.setPreviewSize(640,
                            480,
                            UVCCamera.FRAME_FORMAT_YUYV);
                } catch (final IllegalArgumentException e) {
                    // fallback to YUV mode
                    try {
                        mUVCCamera.setPreviewSize(UVCCamera.DEFAULT_PREVIEW_WIDTH, UVCCamera.DEFAULT_PREVIEW_HEIGHT, UVCCamera.DEFAULT_PREVIEW_MODE);
                    } catch (final IllegalArgumentException e1) {
                        mUVCCamera.destroy();
                        mUVCCamera = null;
                    }
                }
                if (mUVCCamera != null) {
                    final SurfaceTexture st = mUVCCameraView.getSurfaceTexture();
                    if (st != null) {
                        mPreviewSurface = new Surface(st);
                    }
                    mUVCCamera.setPreviewDisplay(mPreviewSurface);
                    mUVCCamera.setFrameCallback(mIFrameCallback, UVCCamera.PIXEL_FORMAT_NV21/*UVCCamera.PIXEL_FORMAT_RGB565, UVCCamera.PIXEL_FORMAT_NV21*/);
                    mUVCCamera.startPreview();
                }
            }
        });
    }

    /**
     * 停止推流
     */
    private void stopPublishRtmp() {
        Log.d(TAG, "stopPublishRtmp");
        mVideoPublish = false;

        //停止推流
        if (mLivePusher != null) {
            mLivePusher.setPushListener(null);
            mLivePusher.stopPusher();
        }

        //清除设置的暂停推流的图片（即收看端看到的图片）
        if (mLivePushConfig != null) {
            mLivePushConfig.setPauseImg(null);
        }
    }

    /**
     * 停止预览，释放相机
     *
     * @return
     */
    public synchronized boolean stopPreviewAndReleaseCamera() {
        Log.d(TAG, "stopPreviewAndReleaseCamera");

        //先关闭预览，释放UVCCamera
        if (mUVCCamera != null) {
            mUVCCamera.stopPreview();
            mUVCCamera.close();
            mUVCCamera.destroy();
            mUVCCamera = null;
        }

        //释放预览surface
        if (mPreviewSurface != null) {
            mPreviewSurface.release();
            mPreviewSurface = null;
        }

        //关闭usb监听
        if (mUSBMonitor != null) {
            mUSBMonitor.unregister();
            mUSBMonitor.destroy();
        }

        //调用波导给的方法关闭usb摄像头，防止影响usb的其他功能，如usb调试
        mBirdIntelligentVehicle.BirdCloseOtg(getApplicationContext());
        return true;
    }

    /**
     * 移除预览窗口
     */
    private void removePreviewWindow() {
        Log.d(TAG, "removePreviewWindow");

        //先释放Surface
        if (mPreviewSurface != null) {
            Log.d(TAG, "release PreviewSurface");
            mPreviewSurface.release();
            mPreviewSurface = null;
        }

        //移除View
        if (mPreviewContainer != null) {
            Log.d(TAG, "remove container");
            mWindowManager.removeView(mPreviewContainer);
            mUVCCameraView = null;
            mPreviewContainer = null;
        }
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
            if (event == TXLiveConstants.PUSH_ERR_OPEN_CAMERA_FAIL) {
                stopPublishRtmp();
            }
        }

        if (event == TXLiveConstants.PUSH_ERR_NET_DISCONNECT) {
            stopPublishRtmp();
        } else if (event == TXLiveConstants.PUSH_WARNING_HW_ACCELERATION_FAIL) {
            Toast.makeText(this.getApplicationContext(), param.getString(TXLiveConstants.EVT_DESCRIPTION), Toast.LENGTH_SHORT).show();
            mLivePushConfig.setHardwareAcceleration(false);
            mLivePusher.setConfig(mLivePushConfig);
        } else if (event == TXLiveConstants.PUSH_ERR_SCREEN_CAPTURE_UNSURPORT) {
            stopPublishRtmp();
        } else if (event == TXLiveConstants.PUSH_ERR_SCREEN_CAPTURE_START_FAILED) {
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
        Log.d(TAG, "Current status, CPU:" + status.getString(TXLiveConstants.NET_STATUS_CPU_USAGE) +
                ", RES:" + status.getInt(TXLiveConstants.NET_STATUS_VIDEO_WIDTH) + "*" + status.getInt(TXLiveConstants.NET_STATUS_VIDEO_HEIGHT) +
                ", SPD:" + status.getInt(TXLiveConstants.NET_STATUS_NET_SPEED) + "Kbps" +
                ", FPS:" + status.getInt(TXLiveConstants.NET_STATUS_VIDEO_FPS) +
                ", ARA:" + status.getInt(TXLiveConstants.NET_STATUS_AUDIO_BITRATE) + "Kbps" +
                ", VRA:" + status.getInt(TXLiveConstants.NET_STATUS_VIDEO_BITRATE) + "Kbps");
        if (mLivePusher != null) {
            mLivePusher.onLogRecord("[net state]:\n" + str + "\n");
        }
    }

    //公用打印辅助函数
    protected String getNetStatusString(Bundle status) {
        String str = String.format("%-14s %-14s %-12s\n%-14s %-14s %-12s\n%-14s %-14s %-12s\n%-14s %-12s",
                "CPU:" + status.getString(TXLiveConstants.NET_STATUS_CPU_USAGE),
                "RES:" + status.getInt(TXLiveConstants.NET_STATUS_VIDEO_WIDTH) + "*" + status.getInt(TXLiveConstants.NET_STATUS_VIDEO_HEIGHT),
                "SPD:" + status.getInt(TXLiveConstants.NET_STATUS_NET_SPEED) + "Kbps",
                "JIT:" + status.getInt(TXLiveConstants.NET_STATUS_NET_JITTER),
                "FPS:" + status.getInt(TXLiveConstants.NET_STATUS_VIDEO_FPS),
                "ARA:" + status.getInt(TXLiveConstants.NET_STATUS_AUDIO_BITRATE) + "Kbps",
                "QUE:" + status.getInt(TXLiveConstants.NET_STATUS_CODEC_CACHE) + "|" + status.getInt(TXLiveConstants.NET_STATUS_CACHE_SIZE),
                "DRP:" + status.getInt(TXLiveConstants.NET_STATUS_CODEC_DROP_CNT) + "|" + status.getInt(TXLiveConstants.NET_STATUS_DROP_SIZE),
                "VRA:" + status.getInt(TXLiveConstants.NET_STATUS_VIDEO_BITRATE) + "Kbps",
                "SVR:" + status.getString(TXLiveConstants.NET_STATUS_SERVER_IP),
                "AVRA:" + status.getInt(TXLiveConstants.NET_STATUS_SET_VIDEO_BITRATE));
        return str;
    }

    StringBuffer mLogMsg = new StringBuffer("");
    private final int mLogMsgLenLimit = 3000;

    //公用打印辅助函数
    protected void appendEventLog(int event, String message) {
        String str = "receive event: " + event + ", " + message;
        Log.d(TAG, str);
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");
        String date = sdf.format(System.currentTimeMillis());
        while (mLogMsg.length() > mLogMsgLenLimit) {
            int idx = mLogMsg.indexOf("\n");
            if (idx == 0)
                idx = 1;
            mLogMsg = mLogMsg.delete(0, idx);
        }
        mLogMsg = mLogMsg.append("\n" + "[" + date + "]" + message);
    }

}
