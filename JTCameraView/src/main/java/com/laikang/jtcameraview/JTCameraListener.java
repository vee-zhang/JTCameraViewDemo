package com.laikang.jtcameraview;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.hardware.Camera;

import java.io.File;
import java.util.List;

public interface JTCameraListener {

    void onCameraOpend();

    void onPreviewStart();

    void onPreviewStop();

    void onShutter();

    void onCupture(Bitmap bitmap);

    void onCut(File file);

    void onCameraClosed();

    void onGetFaces(JTCameraView.Face[] faces);
}
