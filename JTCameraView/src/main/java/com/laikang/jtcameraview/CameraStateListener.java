package com.laikang.jtcameraview;

import android.graphics.Bitmap;

public interface CameraStateListener {

    void onCameraOpend();
    void onPreviewStart();
    void onPreviewStop();
    void onShutter();
    void onCupture(Bitmap bitmap);
    void onCameraClosed();
}
