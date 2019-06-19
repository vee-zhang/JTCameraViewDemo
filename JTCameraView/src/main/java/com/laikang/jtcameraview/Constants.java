package com.laikang.jtcameraview;


import android.support.annotation.IntDef;

public interface Constants {

    int CAMERA_FACING_BACK = 0;
    int CAMERA_FACING_FRONT = 1;

    @IntDef({CAMERA_FACING_BACK, CAMERA_FACING_FRONT})
    @interface CameraFacing {
    }

    int FLASH_OFF = 0;
    int FLASH_ON = 1;
    int FLASH_TORCH = 2;
    int FLASH_AUTO = 3;
    int FLASH_RED_EYE = 4;

    @IntDef({FLASH_OFF, FLASH_ON, FLASH_TORCH, FLASH_AUTO, FLASH_RED_EYE})
    @interface FlashMode {
    }

    int LANDSCAPE_90 = 90;
    int LANDSCAPE_270 = 270;
}
