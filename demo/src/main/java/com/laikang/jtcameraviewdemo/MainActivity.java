package com.laikang.jtcameraviewdemo;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.laikang.jtcameraview.CameraStateListener;
import com.laikang.jtcameraview.JTCameraView;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import static com.laikang.jtcameraview.Constants.CAMERA_FACING_BACK;
import static com.laikang.jtcameraview.Constants.CAMERA_FACING_FRONT;

public class MainActivity extends AppCompatActivity implements CameraStateListener {
    private static final String TAG = "MainActivity";

    private static final int REQUEST_CAMERA_PERMISSION = 100;

    private JTCameraView mJTCameraView;

    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;

    private int mFacing = CAMERA_FACING_FRONT;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mJTCameraView = findViewById(R.id.ftdv);
        mJTCameraView.setListener(this);
    }

    /**
     * 预览看这里
     * @param v
     */
    public void startPreview(View v){
        if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        } else {
            mJTCameraView.startPreview();
        }
    }

    /**
     * 拍照看这里
     * @param v
     */
    public void takePicture(View v){
        mJTCameraView.takePicture();
    }

    /**
     * 停止预览看这里
     * @param v
     */
    public void stopPreview(View v){
        mJTCameraView.stopPreview();
    }

    /**
     * 切换前后摄像头
     * @param v
     */
    public void changeFacing(View v){
        if (mFacing == CAMERA_FACING_FRONT){
            mFacing = CAMERA_FACING_BACK;
        } else {
            mFacing = CAMERA_FACING_FRONT;
        }
        mJTCameraView.setCameraFacing(mFacing);
    }

    @Override
    public void onCameraOpend() {
        showToast("摄像头已开启");
    }

    @Override
    public void onPreviewStart() {
        showToast("已启动预览");
    }

    @Override
    public void onPreviewStop() {
        showToast("已停止预览");
    }

    @Override
    public void onShutter() {
        showToast("在这里播放快门声音或者拍照提示");
    }

    @Override
    public void onCupture(final Bitmap bitmap) {
        showToast("获取拍照后的图像信息，需要自己保存");
        getBackgroundHandler().post(new Runnable() {
            @Override
            public void run() {
                File file = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "picture.jpg");
                try {
                    OutputStream os = new FileOutputStream(file);
                    BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file));
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bos);//将图片压缩的流里面
                    os.flush();
                    os.close();
                    bitmap.recycle();
                } catch (IOException e) {
                    Log.w(TAG, "图像文件写入失败： " + file, e);
                }
            }
        });
    }

    @Override
    public void onCameraClosed() {
        showToast("摄像头已关闭");
    }

    private void showToast(String str){
        Toast.makeText(this,str,Toast.LENGTH_SHORT);
    }

    private Handler getBackgroundHandler() {
        if (mBackgroundHandler == null) {
            mBackgroundThread = new HandlerThread("background");
            mBackgroundThread.start();
            mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
        }
        return mBackgroundHandler;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mBackgroundThread == null){
            return;
        }
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            Log.e(TAG, "close: 后台线程关闭失败：", e);
        }
    }
}
