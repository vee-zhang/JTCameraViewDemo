package com.laikang.jtcameraview;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Size;
import android.view.TextureView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.laikang.jtcameraview.Constants.CAMERA_FACING_BACK;
import static com.laikang.jtcameraview.Constants.CAMERA_FACING_FRONT;

public class JTCameraView extends TextureView {

    private static final String TAG = "FtdView";
    private boolean isCameraCanBeUse;
    private boolean isAutoFocus = true;
    private int mFlash = Constants.FLASH_AUTO;

    private Camera mCamera;
    private Camera.Parameters mCameraParameters;

    private int mFacingFrontCameraId = -1;
    private Camera.CameraInfo mFacingFrontCameraInfo;
    private int mFacingBackCameraId = -1;
    private Camera.CameraInfo mFacingBackCameraInfo;
    private int mCameraId = -1;
    private Camera.CameraInfo mCameraInfo;

    private int mDisplayOrientation;
    private int mPictureOrientation;
    private int mPreviewOrientation;
    private Camera.Size mPreviewSize;
    private Camera.Size mPictureSize;
    private boolean isShowingPreview;

    private final AtomicBoolean isPictureCaptureInProgress = new AtomicBoolean(false);

    public boolean isShowingPreview() {
        return isShowingPreview;
    }

    private Size mSize;

    private CameraStateListener mListener;

    public void setListener(CameraStateListener mListener) {
        this.mListener = mListener;
    }

    private int mCameraFacing = Camera.CameraInfo.CAMERA_FACING_FRONT;

    private DisplayOrientationDetector mDisplayOrientationDetector;

    public JTCameraView(Context context) {
        this(context, null);
    }

    public JTCameraView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public JTCameraView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public JTCameraView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        isCameraCanBeUse = checkCameraHardware(context);
        if (!isCameraCanBeUse) {
            Log.i(TAG, "FtdView: 相机不可用，请检查设备是否故障，或是否有配备摄像头组件！");
            return;
        }
        setupCameraInfo();
        initTextureView();
    }

    public boolean autoPreview;

    /**
     * View初始化设置
     */
    private void initTextureView() {
        this.setFocusable(true);
        this.setFocusableInTouchMode(true);
        this.setKeepScreenOn(true);//保持屏幕长亮
        this.setSurfaceTextureListener(new SurfaceTextureListener() {
            /**
             * surface在创建的时候调用
             */
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                setupSizeCache(width, height);
                startPreview();
            }

            /**
             * surface尺寸发生改变的时候调用，如横竖屏切换。
             **/
            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                setupSizeCache(width, height);
                startPreview();
            }

            /**
             * surface被销毁的时候调用，如退出游戏画面，一般在该方法中停止绘图线程。
             */
            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                releaseCamera();
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {

            }
        });
    }

    /**
     * 获取当前摄像头是前置还是后置
     *
     * @return 前置：CAMERA_FACING_FRONT，后置：CAMERA_FACING_BACK
     */
    @Constants.CameraFacing
    public int getCameraFacing() {
        return mCameraFacing == Camera.CameraInfo.CAMERA_FACING_FRONT ? CAMERA_FACING_FRONT : CAMERA_FACING_BACK;
    }

    /**
     * 转为后置（前置）摄像头
     *
     * @param facing
     */
    public void setCameraFacing(@Constants.CameraFacing int facing) {
        if (facing == CAMERA_FACING_FRONT) {
            if (mCameraFacing == Camera.CameraInfo.CAMERA_FACING_FRONT || mFacingFrontCameraId < 0) {
                return;
            }
            mCameraId = mFacingFrontCameraId;
        } else {
            if (mCameraFacing == Camera.CameraInfo.CAMERA_FACING_BACK || mFacingBackCameraId < 0) {
                return;
            }
            mCameraId = mFacingBackCameraId;
        }
        mCameraFacing = facing;
        startPreview();
    }

    private void setupSizeCache(int width, int height) {
        mSize = new Size(width, height);
    }

    /**
     * 设置方向
     *
     * @param displayOrientation
     */
    private void setDisplayOrientation(int displayOrientation) {
        mDisplayOrientation = displayOrientation;
        if (isCameraOpened()) {
            mPictureOrientation = calcCameraRotation(displayOrientation);
            mPreviewOrientation = calcDisplayOrientation(displayOrientation);
            mCamera.setParameters(mCameraParameters);
            mCamera.setDisplayOrientation(mPreviewOrientation);
        }
    }

    /**
     * 计算输出的jpeg图像的方向（需旋转角度）
     *
     * @param screenOrientationDegrees
     * @return
     */
    private int calcCameraRotation(int screenOrientationDegrees) {
        if (mCameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            return (mCameraInfo.orientation + screenOrientationDegrees) % 360;
        } else {  // back-facing
            final int landscapeFlip = isLandscape(screenOrientationDegrees) ? 180 : 0;
            return (mCameraInfo.orientation + screenOrientationDegrees + landscapeFlip) % 360;
        }
    }

    /**
     * 计算预览图像的方向（需旋转角度）
     *
     * @param screenOrientationDegrees
     * @return
     */
    private int calcDisplayOrientation(int screenOrientationDegrees) {
        if (mCameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            return (360 - (mCameraInfo.orientation + screenOrientationDegrees) % 360) % 360;
        } else {  // back-facing
            return (mCameraInfo.orientation - screenOrientationDegrees + 360) % 360;
        }
    }

    /**
     * 如果是横向
     *
     * @param orientationDegrees
     * @return
     */
    private boolean isLandscape(int orientationDegrees) {
        return (orientationDegrees == Constants.LANDSCAPE_90 || orientationDegrees == Constants.LANDSCAPE_270);
    }

    /**
     * 检查设备是否有相机
     */
    private boolean checkCameraHardware(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA);
    }

    /**
     * 获取Camera系统的信息
     */
    @SuppressLint("NewApi")
    private void setupCameraInfo() {
        int sensorCount = Camera.getNumberOfCameras();
        for (int i = 0; i < sensorCount; i++) {
            final Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
            Camera.getCameraInfo(i, cameraInfo);
            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                mFacingFrontCameraId = i;
                mFacingFrontCameraInfo = cameraInfo;
            } else if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                mFacingBackCameraId = i;
                mFacingBackCameraInfo = cameraInfo;
            }
        }
        if (mCameraId != -1) {
            return;
        }
        if (mFacingFrontCameraId == -1) {//只有后置摄像头，如山寨机，或其他奇葩设备
            mCameraId = mFacingBackCameraId;
            mCameraInfo = mFacingBackCameraInfo;
        } else if (mFacingBackCameraId == -1) {//只有前置摄像头，如来康镜，触控一体机，或其他奇葩设备
            mCameraId = mFacingFrontCameraId;
            mCameraInfo = mFacingFrontCameraInfo;
        } else if (mCameraFacing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            mCameraId = mFacingFrontCameraId;
            mCameraInfo = mFacingFrontCameraInfo;
        } else {
            mCameraId = mFacingBackCameraId;
            mCameraInfo = mFacingBackCameraInfo;
        }
    }

    /**
     * 开启对应的相机
     *
     * @param cameraId
     */
    private void openCamera(int cameraId) {
        if (mCamera != null) {
            releaseCamera();
        }
        mCamera = Camera.open(cameraId);
        mCameraParameters = mCamera.getParameters();
        if (mListener != null) {
            mListener.onCameraOpend();
        }
    }

    private boolean isCameraOpened() {
        return mCamera != null;
    }

    /**
     * 预览图像（surface）处理
     */
    private boolean inited;

    private void configureTransform() {
        if (inited) {
            return;
        }
        if (mSize == null || mPreviewSize == null) {
            return;
        }
        Matrix matrix = getTransform(new Matrix());

        float sWidth = (float) mSize.getWidth();
        float sHeight = (float) mSize.getHeight();

        float pWidth, pHeight;
        if (isLandscape(mPreviewOrientation)) {
            pWidth = (float) mPreviewSize.width;
            pHeight = (float) mPreviewSize.height;
        } else {
            pWidth = (float) mPreviewSize.height;
            pHeight = (float) mPreviewSize.width;
        }

        float previewRatio = pHeight / pWidth;

        float wScale = 1f;
        float hScale = 1f;

//        float widthDiffer = sWidth - pWidth;
//        float heightDiffer = sHeight - pHeight;

//        if (widthDiffer > 0 && heightDiffer < 0) {
//            hScale = sWidth * previewRatio / sHeight;
//        } else if (widthDiffer < 0 && heightDiffer > 0) {
//            wScale = sHeight / previewRatio / sWidth;
//        } else {

        if (sHeight / sWidth > pHeight / pWidth) {
            wScale = sHeight / previewRatio / sWidth;
        } else {
            hScale = sWidth * previewRatio / sHeight;
        }
//        }

        matrix.postScale(wScale, hScale, sWidth / 2, sHeight / 2);
        setTransform(matrix);
        inited = true;
    }


    /**
     * 调整相机参数
     */
    private void adjustCameraParameters() {
        this.mPreviewSize = chooseOptimalSize(mCameraParameters.getSupportedPreviewSizes(), mSize.getWidth(), mSize.getHeight());
        this.mPictureSize = chooseOptimalSize(mCameraParameters.getSupportedPictureSizes(), mSize.getWidth(), mSize.getHeight());
        mCameraParameters.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
        mCameraParameters.setPictureSize(mPictureSize.width, mPictureSize.height);
        mCameraParameters.setRotation(calcCameraRotation(mDisplayOrientation));
        setAutoFocus(isAutoFocus);
        setFlashInternal(mFlash);
        mCamera.setParameters(mCameraParameters);
        mCamera.setDisplayOrientation(calcDisplayOrientation(mDisplayOrientation));
    }

    /**
     * 开启/关闭自动对焦
     *
     * @param autoFocus true:开  false:关
     */
    private void setAutoFocus(boolean autoFocus) {
        isAutoFocus = autoFocus;
        if (isCameraOpened()) {
            final List<String> modes = mCameraParameters.getSupportedFocusModes();
            if (autoFocus && modes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                mCameraParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            } else if (modes.contains(Camera.Parameters.FOCUS_MODE_FIXED)) {
                mCameraParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_FIXED);
            } else if (modes.contains(Camera.Parameters.FOCUS_MODE_INFINITY)) {
                mCameraParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_INFINITY);
            } else {
                mCameraParameters.setFocusMode(modes.get(0));
            }
        }
    }

    /**
     * 设置闪光灯模式
     */
    public void setFlashInternal(@Constants.FlashMode int flash) {
        if (flash == mFlash) {
            return;
        }
        if (isCameraOpened()) {
            List<String> modes = mCameraParameters.getSupportedFlashModes();
            String mode = getFlashMode(flash);
            if (modes != null && modes.contains(mode)) {
                mCameraParameters.setFlashMode(mode);
                mFlash = flash;
            }
            String currentMode = getFlashMode(mFlash);
            if (modes == null || !modes.contains(currentMode)) {
                mCameraParameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                mFlash = Constants.FLASH_OFF;
            }
        } else {
            mFlash = flash;
        }
    }

    private String getFlashMode(@Constants.FlashMode int mode) {
        switch (mode) {
            case Constants.FLASH_ON:
                return Camera.Parameters.FLASH_MODE_ON;
            case Constants.FLASH_TORCH:
                return Camera.Parameters.FLASH_MODE_TORCH;
            case Constants.FLASH_AUTO:
                return Camera.Parameters.FLASH_MODE_AUTO;
            case Constants.FLASH_RED_EYE:
                return Camera.Parameters.FLASH_MODE_RED_EYE;
            default:
                return Camera.Parameters.FLASH_MODE_OFF;
        }
    }

    /**
     * 选择合适的成像尺寸
     *
     * @param sizeList
     * @param width
     * @return
     */
    @SuppressWarnings("SuspiciousNameCombination")
    private Camera.Size chooseOptimalSize(List<Camera.Size> sizeList, int width, int height) {
        if (sizeList == null || width <= 0) {
            return null;
        }
        ArrayList<Camera.Size> list = new ArrayList<>(10);
        //去除长款相等的尺寸，这种尺寸容易引起图片倒伏
        for (Camera.Size size : sizeList) {
            if (size.height != size.width) {
                list.add(size);
            }
        }
        int displayWidth = width;
        int displayHeight = height;
        if (mPreviewOrientation - mDisplayOrientation > 0) {
            displayWidth = height;
            displayHeight = width;
        }
        CameraSizeComparator comparator = new CameraSizeComparator(displayWidth, displayHeight);

//        Log.d(TAG, "chooseOptimalSizes: \n");
        StringBuffer sb = new StringBuffer();
        for (Camera.Size size : sizeList) {
            sb.append(size.height).append("*").append(size.width).append("\n");
        }
//        Log.d(TAG, "chooseOptimalSize: \n" + sb.toString());
        return Collections.max(list, comparator);
    }

    /**
     * 计算长宽比
     *
     * @param size
     * @return
     */
    private float getResolutionRatio(Camera.Size size) {
        return size.height / size.width;
    }

    /**
     * 释放摄像头
     */
    public void releaseCamera() {
        if (mCamera != null) {
            mCamera.setPreviewCallback(null);//todo
            mCamera.lock();//todo 这俩是啥意思要查一下
            mCamera.release();
            mCamera = null;
            if (mListener != null) {
                mListener.onCameraClosed();
            }
        }
    }

    /**
     * 启动预览
     */
    byte[] imageData;

    public void startPreview() {
        openCamera(mCameraId);
        adjustCameraParameters();
        configureTransform();
        try {
            mCamera.setPreviewCallback(new Camera.PreviewCallback() {
                @Override
                public void onPreviewFrame(byte[] data, Camera camera) {
                    imageData = data;
                }
            });
            mCamera.setPreviewTexture(getSurfaceTexture());
            mCamera.startPreview();
            isShowingPreview = true;
            if (mListener != null) {
                mListener.onPreviewStart();
            }
        } catch (Exception e) {
            Log.e(TAG, "startPreview: ", e);
        }
    }

    /**
     * 停止预览,画面会定格
     */
    public void stopPreview() {
        if (mCamera != null) {
            mCamera.stopPreview();
        }
        isShowingPreview = false;
        if (mListener != null) {
            mListener.onPreviewStop();
        }
        releaseCamera();
    }

    /**
     * 拍照
     */
    public void takePicture() {
        if (!isCameraOpened()) {
            throw new IllegalStateException(
                    "Camera is not ready. Call start() before takePicture().");
        }
        if (this.isAutoFocus) {
            mCamera.cancelAutoFocus();
            mCamera.autoFocus(new Camera.AutoFocusCallback() {
                @Override
                public void onAutoFocus(boolean success, Camera camera) {
                    takePictureInternal();
                }
            });
        } else {
            takePictureInternal();
        }
    }

    private void takePictureInternal() {
        if (isPictureCaptureInProgress.getAndSet(true)) {
            return;
        }
        Log.e(TAG, "takePictureInternal: 当值为"+isPictureCaptureInProgress+"时调用" );
        mCamera.takePicture(new Camera.ShutterCallback() {

            @Override
            public void onShutter() {
                if (mListener != null) {
                    mListener.onShutter();
                }
            }
        }, null, null, new Camera.PictureCallback() {
            @Override
            public void onPictureTaken(final byte[] data, Camera camera) {
                isPictureCaptureInProgress.set(false);

                Bitmap rawBitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                rawBitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos);
                Bitmap rotedBitmap;

                if (mCameraFacing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                    rotedBitmap = BitmapUtils.mirror(rawBitmap);
                } else {
                    rotedBitmap = BitmapUtils.rotate(rawBitmap, 180f);
                }
                rawBitmap.recycle();
                try {
                    baos.flush();
                    baos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                if (mListener != null) {
                    mListener.onCupture(rotedBitmap);
                }
                camera.cancelAutoFocus();
                camera.startPreview();
            }
        });

    }

    /**
     * 截取预览帧图像
     *
     * @param file
     */
    public void cut(File file) {
        YuvImage image = new YuvImage(this.imageData, mCameraParameters.getPreviewFormat(), mPreviewSize.width, mPreviewSize.height, null);
        FileOutputStream fos = null;
        Rect rect = new Rect(0, 0, mPreviewSize.width, mPreviewSize.height);
        try {
            fos = new FileOutputStream(file);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        image.compressToJpeg(rect, 100, fos);
        mListener.onCut(file);
    }

    private static class BitmapUtils {
        public static Bitmap mirror(Bitmap bitmap) {
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            Matrix matrix = new Matrix();
            if (width >= height) {
                matrix.setRotate(-90f);
            }
            matrix.postScale(-1f, 1f);


            return Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);
        }

        public static Bitmap rotate(Bitmap bitmap, Float degree) {
            Matrix matrix = new Matrix();
            matrix.postRotate(degree);
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        }

        public static Bitmap scale(Bitmap bitmap, int w) {
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            int h = w * height / width;

            return Bitmap.createScaledBitmap(bitmap, w, h, true);
        }
    }

//    @Override
//    protected void onAttachedToWindow() {
//        super.onAttachedToWindow();
//        if (!isInEditMode()) {
//            mDisplayOrientationDetector.enable(ViewCompat.getDisplay(this));
//        }
//    }
//
//    @Override
//    protected void onDetachedFromWindow() {
//        if (!isInEditMode()) {
//            mDisplayOrientationDetector.disable();
//        }
//        super.onDetachedFromWindow();
//    }

    /**
     * 相机成像尺寸比较器
     */
    private class CameraSizeComparator implements Comparator<Camera.Size> {
        private float mWdith;
        private float mHeight;
        private float mRatio;
        private float mArea;

        public CameraSizeComparator(int width, int height) {
            mWdith = (float) width;
            mHeight = (float) height;
            mRatio = mWdith / mHeight;
            mArea = mWdith * mHeight;
        }

        @Override
        public int compare(Camera.Size o1, Camera.Size o2) {

            float heightDiffer1 = Math.abs(mHeight - o1.height);
            float heightDiffer2 = Math.abs(mHeight - o2.height);
            float widthDiffer1 = Math.abs(mWdith - o1.width);
            float widthDiffer2 = Math.abs(mWdith - o2.width);
            if (heightDiffer1 < heightDiffer2 && widthDiffer1 < widthDiffer2) {
                return 1;
            } else {
                return -1;
            }
        }
    }
}
