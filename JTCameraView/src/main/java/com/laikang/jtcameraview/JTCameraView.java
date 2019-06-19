package com.laikang.jtcameraview;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.TextureView;

import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static com.laikang.jtcameraview.Constants.CAMERA_FACING_BACK;
import static com.laikang.jtcameraview.Constants.CAMERA_FACING_FRONT;

public class JTCameraView extends TextureView implements TextureView.SurfaceTextureListener {

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
    private static int mCameraId = -1;
    private static Camera.CameraInfo mCameraInfo;

    private int mDisplayOrientation;
    private int mPictureOrientation;
    private int mPreviewOrientation;
    private Camera.Size mPreviewSize;
    private static boolean isShowingPreview;

    private int mWidth;
    private int mHeight;

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
        initTextureView();
        setupCameraInfo();
        openCamera(mCameraId);
        mDisplayOrientationDetector = new DisplayOrientationDetector(getContext()) {
            @Override
            public void onDisplayOrientationChanged(int displayOrientation) {
                if (isShowingPreview) {
                    mCamera.stopPreview();
                }
                setDisplayOrientation(displayOrientation);
            }
        };
    }

    /**
     * View初始化设置
     */
    private void initTextureView() {
        this.setFocusable(true);
        this.setFocusableInTouchMode(true);
        this.setKeepScreenOn(true);//保持屏幕长亮
        this.setSurfaceTextureListener(this);
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
        openCamera(mCameraId);
        adjustCameraParameters(mWidth, mHeight);
        configureTransform(mWidth, mHeight);
        if (isShowingPreview) {
            startPreview();
        }
    }

    private void setupSizeCache(int width, int height) {
        mWidth = width;
        mHeight = height;
    }

    /**
     * surface在创建的时候调用
     */
    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        setupSizeCache(width, height);
        configureCameraEnv(surface, width, height);
    }

    /**
     * surface尺寸发生改变的时候调用，如横竖屏切换。
     **/
    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        setupSizeCache(width, height);
        configureCameraEnv(surface, width, height);
    }

    /**
     * surface被销毁的时候调用，如退出游戏画面，一般在该方法中停止绘图线程。
     */
    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {

    }

    /**
     * 配置相机整体环境
     *
     * @param surface
     * @param width
     * @param height
     */
    private void configureCameraEnv(SurfaceTexture surface, int width, int height) {
        if (isShowingPreview) {
            mCamera.stopPreview();
        }
        adjustCameraParameters(width, height);
        configureTransform(width, height);
        if (isShowingPreview) {
            startPreview(surface);
        }
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
//            mCameraParameters.setRotation(mPictureOrientation);
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
     *
     * @param surfaceWidth
     * @param surfaceHeight
     */
    private void configureTransform(int surfaceWidth, int surfaceHeight) {
        if (mCameraParameters == null || mCameraInfo == null) {
            return;
        }
        Matrix matrix = getTransform(new Matrix());

        float sWidth = (float) surfaceWidth;
        float sHeight = (float) surfaceHeight;

        float pWidth, pHeight;
        if (mPreviewOrientation % 180 == 0) {
            pWidth = (float) mPreviewSize.width;
            pHeight = (float) mPreviewSize.height;
        } else {
            pWidth = (float) mPreviewSize.height;
            pHeight = (float) mPreviewSize.width;
        }

        float previewRatio = pHeight / pWidth;

        float wScale = 1f;
        float hScale = 1f;

        float widthDiffer = sWidth - pWidth;
        float heightDiffer = sHeight - pHeight;

        if (widthDiffer > heightDiffer) {
            hScale = sWidth * previewRatio / sHeight;
        } else {
            wScale = sHeight / previewRatio / sWidth;
        }
        //按照摄像头预览的长宽比，对surface进行缩放，保证图像不被拉伸的基础上让surface不留白边
        matrix.setScale(wScale, hScale);
        setTransform(matrix);
    }


    /**
     * 调整相机参数
     */
    private void adjustCameraParameters(int width, int height) {
        this.mPreviewSize = chooseOptimalSize(mCameraParameters.getSupportedPreviewSizes(), width, height);
//        Camera.Size pictureSize = chooseOptimalSize(mCameraParameters.getSupportedPictureSizes(), width, height);
        mCameraParameters.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
//        mCameraParameters.setPictureSize(pictureSize.width, pictureSize.height);
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
            if (isShowingPreview) {
                mCamera.stopPreview();
            }
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
            if (isShowingPreview) {
                startPreview();
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
    private Camera.Size chooseOptimalSize(List<Camera.Size> sizeList, int width, int height) {
        if (sizeList == null || width <= 0) {
            return null;
        }
        int displayWidth = width;
        int displayHeight = height;
        //todo 这里可能会出问题
        if (mPreviewOrientation - mDisplayOrientation > 0) {
            displayWidth = height;
            displayHeight = width;
        }
        CameraSizeComparator comparator = new CameraSizeComparator(displayWidth, displayHeight);

        return Collections.max(sizeList, comparator);
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
    private void releaseCamera() {
        if (mCamera != null) {
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
    public void startPreview() {
        startPreview(getSurfaceTexture());
    }

    private void startPreview(SurfaceTexture surface) {
        try {
            mCamera.setPreviewTexture(surface);
            mCamera.startPreview();
            isShowingPreview = true;
            if (mListener != null) {
                mListener.onPreviewStart();
            }
        } catch (IOException e) {
            Log.e(TAG, "startPreview: ", e);
        }
    }

    /**
     * 停止预览,画面会定格
     */
    public void stopPreview() {
        mCamera.stopPreview();
        isShowingPreview = false;
        if (mListener != null) {
            mListener.onPreviewStop();
        }
    }

    /**
     * 拍照
     */
    public void takePicture() {
        mCamera.takePicture(new Camera.ShutterCallback() {

            @Override
            public void onShutter() {
                if (mListener != null) {
                    mListener.onShutter();
                }
            }
        }, null, new Camera.PictureCallback() {
            @Override
            public void onPictureTaken(final byte[] data, Camera camera) {
                Bitmap rawBitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                Bitmap bitmap;
                if (mCameraFacing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                    bitmap = BitmapUtils.mirror(rawBitmap);
                } else {
                    bitmap = BitmapUtils.rotate(rawBitmap, 180f);
                }
                rawBitmap.recycle();
                if (mListener != null) {
                    mListener.onCupture(bitmap);
                }
//                camera.cancelAutoFocus();
                camera.startPreview();
            }
        });
    }

    private static class BitmapUtils {
        public static Bitmap mirror(Bitmap bitmap) {
            Matrix matrix = new Matrix();
            matrix.postScale(-1f, 1f);
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        }

        public static Bitmap rotate(Bitmap bitmap, Float degree) {
            Matrix matrix = new Matrix();
            matrix.postRotate(degree);
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!isInEditMode()) {
            mDisplayOrientationDetector.enable(ViewCompat.getDisplay(this));
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (!isInEditMode()) {
            mDisplayOrientationDetector.disable();
        }
        super.onDetachedFromWindow();
    }

    /**
     * 相机成像尺寸比较器
     */
    private class CameraSizeComparator implements Comparator<Camera.Size> {
        private float mWdith;
        private float mHeight;

        public CameraSizeComparator(int width, int height) {
            mWdith = (float) width;
            mHeight = (float) height;
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
