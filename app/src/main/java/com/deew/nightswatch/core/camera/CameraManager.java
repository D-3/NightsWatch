package com.deew.nightswatch.core.camera;

import android.hardware.Camera;

import com.serenegiant.usb.UVCCamera;

/**
 * Created by DeW on 2017/3/14.
 */

public class CameraManager {
    private static final String TAG = CameraManager.class.getSimpleName();

    public static final int CAMERA_TYPE_BACK_FACING = 0;
    public static final int CAMERA_TYPE_AVIN = 1;
    public static final int CAMERA_TYPE_OTG = 2;

    private Camera mBackFacingCamera;
    private Camera mAvinCamera;
    private UVCCamera mOtgCamera;



}
