package com.laikang.jtcameraviewdemo;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.Camera;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.laikang.jtcameraview.JTCameraListener;
import com.laikang.jtcameraview.Constants;
import com.laikang.jtcameraview.JTCameraView;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import static com.laikang.jtcameraview.Constants.CAMERA_FACING_BACK;
import static com.laikang.jtcameraview.Constants.CAMERA_FACING_FRONT;

@SuppressWarnings("deprecation")
public class MainActivity extends AppCompatActivity implements
        JTCameraListener,
        Spinner.OnItemSelectedListener,
        SeekBar.OnSeekBarChangeListener,
        Camera.FaceDetectionListener,
        CoverView.Listener {
    private static final String TAG = "MainActivity";

    private static final int REQUEST_CAMERA_PERMISSION = 100;

    private static final String[] options = new String[]{"自动", "开启", "常开", "抗红眼", "关闭"};

    private JTCameraView mJTCameraView;
    private CoverView mCoverView;
    private ImageView iv;
    private Spinner flashSpinner;
    private Spinner sceneSpinner;
    private Spinner whiteBalanceSpinner;
    private Spinner colorEffectSpinner;
    private SeekBar expotureSeekBar;
    private TextView seekBarTitle;
    private SeekBar zoomSeekBar;

    private ArrayAdapter<String> flashAdapter;
    private ArrayAdapter<String> sceneAdapter;
    private ArrayAdapter<String> whiteBalanceAdapter;
    private ArrayAdapter<String> colorEffectAdapter;

    private List<String> sceneModeList = new ArrayList<>();
    private List<String> whiteBalanceModeList = new ArrayList<>();
    private List<String> colorModeList = new ArrayList<>();

    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;

    private MainHandler mMainHandler;

    private int mFacing = CAMERA_FACING_FRONT;

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        switch (parent.getId()) {
            case R.id.spinner_flash:
                setFlashMode(position);
                break;
            case R.id.spinner_scene_mode:
                setSceneMode(position);
                break;
        }
    }

    /**
     * 设置闪关灯模式看这里
     *
     * @param position
     */
    private void setFlashMode(int position) {
        switch (position) {
            case 1:
                mJTCameraView.setFlashInternal(Constants.FLASH_ON);
                break;
            case 2:
                mJTCameraView.setFlashInternal(Constants.FLASH_TORCH);
                break;
            case 3:
                mJTCameraView.setFlashInternal(Constants.FLASH_RED_EYE);
                break;
            case 4:
                mJTCameraView.setFlashInternal(Constants.FLASH_OFF);
            default://默认为自动模式
                mJTCameraView.setFlashInternal(Constants.FLASH_AUTO);
        }
    }

    private void setSceneMode(int position) {
        mJTCameraView.setSceneMode(sceneModeList.get(position));
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (!seekBar.isPressed()) {
            return;
        }
        int value;
        switch (seekBar.getId()) {
            case R.id.seekBar_zoom:
                value = progress > mJTCameraView.getMaxZoom() ? mJTCameraView.getMaxZoom() : progress;
                mJTCameraView.setZoom(value);
                break;
            case R.id.seekBar_expoture:
                value = mJTCameraView.getMinExposureCompensation() + progress;
                mJTCameraView.setExposure(value);
                seekBarTitle.setText("曝光度:" + value);
                break;
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onFaceDetection(Camera.Face[] faces, Camera camera) {
//        mCoverView.update(faces);
    }

    @Override
    public void onFocus(Rect rect) {
        mJTCameraView.focus(rect);
    }


    private static class MainHandler extends Handler {
        private WeakReference<MainActivity> mainActivity;

        public MainHandler(MainActivity mainActivity) {
            this.mainActivity = new WeakReference<>(mainActivity);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            String path = (String) msg.obj;
            File img = new File(path);
            Glide.with(mainActivity.get()).load(img).skipMemoryCache(true).diskCacheStrategy(DiskCacheStrategy.NONE).into(mainActivity.get().iv);
        }
    }

    @Override
    public void onGetFaces(JTCameraView.Face[] faces) {
        mCoverView.update(faces);
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        flashSpinner = findViewById(R.id.spinner_flash);
        flashAdapter = new ArrayAdapter<>(this, R.layout.item, options);
        flashSpinner.setAdapter(flashAdapter);
        flashSpinner.setOnItemSelectedListener(this);

        sceneSpinner = findViewById(R.id.spinner_scene_mode);
        sceneSpinner.setOnItemSelectedListener(this);
        sceneAdapter = new ArrayAdapter<String>(this, R.layout.item, sceneModeList);
        sceneSpinner.setAdapter(sceneAdapter);

        whiteBalanceSpinner = findViewById(R.id.spinner_white_balance_mode);
        whiteBalanceSpinner.setOnItemSelectedListener(this);
        whiteBalanceAdapter = new ArrayAdapter<String>(this, R.layout.item, whiteBalanceModeList);
        whiteBalanceSpinner.setAdapter(whiteBalanceAdapter);

        colorEffectSpinner = findViewById(R.id.spinner_color_effect);
        colorEffectSpinner.setOnItemSelectedListener(this);
        colorEffectAdapter = new ArrayAdapter<String>(this, R.layout.item, colorModeList);
        colorEffectSpinner.setAdapter(colorEffectAdapter);

        expotureSeekBar = findViewById(R.id.seekBar_expoture);
        expotureSeekBar.setOnSeekBarChangeListener(this);
        seekBarTitle = findViewById(R.id.tv4);

        zoomSeekBar = findViewById(R.id.seekBar_zoom);
        zoomSeekBar.setOnSeekBarChangeListener(this);

        mJTCameraView = findViewById(R.id.ftdv);
        mJTCameraView.setListener(this);
        iv = findViewById(R.id.iv);
        mMainHandler = new MainHandler(this);
        mCoverView = findViewById(R.id.cover);
        mCoverView.setListener(this);
    }


    /**
     * 预览看这里
     *
     * @param v
     */
    public void startPreview(View v) {
        if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        } else {
            mJTCameraView.startPreview();
        }
    }

    /**
     * 拍照看这里
     *
     * @param v
     */
    public void takePicture(View v) {
        mJTCameraView.takePicture();
    }

    /**
     * 停止预览看这里
     *
     * @param v
     */
    public void stopPreview(View v) {
        mJTCameraView.stopPreview();
    }

    /**
     * 切换前后摄像头
     *
     * @param v
     */
    public void changeFacing(View v) {
        if (mFacing == CAMERA_FACING_FRONT) {
            mFacing = CAMERA_FACING_BACK;
        } else {
            mFacing = CAMERA_FACING_FRONT;
        }
        mJTCameraView.setCameraFacing(mFacing);
        mCoverView.clean();
    }

    @Override
    public void onCameraOpend() {
        //只有摄像头开启后才能拿到硬件支持信息，当切换摄像头时这个方法会重复调用
        this.sceneModeList.clear();
        this.whiteBalanceModeList.clear();
        this.colorModeList.clear();

        this.sceneModeList.addAll(mJTCameraView.getSceneModeList());
        this.sceneAdapter.notifyDataSetChanged();
        this.whiteBalanceModeList.addAll(mJTCameraView.getWhiteBalanceModeList());
        this.whiteBalanceAdapter.notifyDataSetChanged();
        this.colorModeList.addAll(mJTCameraView.getColorEffectModeList());
        this.colorEffectAdapter.notifyDataSetChanged();

        int expotureMaxValue = mJTCameraView.getMaxExposureCompensation() - mJTCameraView.getMinExposureCompensation();
        this.expotureSeekBar.setMax(expotureMaxValue);
        this.zoomSeekBar.setMax(mJTCameraView.getMaxZoom());
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
                    Message msg = mMainHandler.obtainMessage();
                    msg.obj = file.getPath();
                    mMainHandler.sendMessage(msg);
                } catch (IOException e) {
                    Log.w(TAG, "图像文件写入失败： " + file, e);
                }

            }
        });
    }

    @Override
    public void onCut(File file) {

    }

    @Override
    public void onCameraClosed() {
        showToast("摄像头已关闭");
    }

    private void showToast(String str) {
        Toast.makeText(this, str, Toast.LENGTH_SHORT);
    }

    private Handler getBackgroundHandler() {
        if (mBackgroundHandler == null) {
            mBackgroundThread = new HandlerThread("background");
            mBackgroundThread.start();
            mBackgroundHandler = new Handler(mBackgroundThread.getLooper()) {
                @Override
                public void handleMessage(Message msg) {
                    super.handleMessage(msg);
                    String path = (String) msg.obj;
                    File img = new File(path);
                    Glide.with(iv).load(img).into(iv);
                }
            };
        }
        return mBackgroundHandler;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mBackgroundThread == null) {
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
