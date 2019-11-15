# JTCameraView

## 一个View帮你搞定Android自定义相机

### 功能一览

1. 预览
2. 拍照
3. 自动对焦
4. 手动对焦
5. 白平衡调节
6. 情景模式调节
7. 缩放设置
8. 闪光灯模式设置
9. 滤镜设置
10. 曝光度设置
11. 多摄像头切换
12. 横竖屏支持

### 集成

```groovy
implementation 'com.william:JTCameraView:2.0.0-beta'
```

### 权限

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
<uses-feature android:name="android.hardware.camera" />
<uses-feature android:name="android.hardware.camera.autofocus" />
<uses-permission android:name="android.permission.FLASHLIGHT"/>
```

### 使用

```xml
<com.laikang.jtcameraview.JTCameraView
    android:layout_width="match_parent"
    android:layout_height="match_parent" />
```

你没看错，这就是一个单纯的View！你可以任意改变他的小小，它会根据当前尺寸自动设置合适的分辨率，且尽力做到「所见即所得」。

#### 预览

```java
public void startPreview(View v) {
    if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
    } else {
        mJTCameraView.startPreview();
    }
}
```

**注意：JTCameraView一切的功能都是以预览为基础，所以必须先预览！当然在预览前要添加权限。**

#### 停止预览

```java
mJTCameraView.stopPreview();
```

#### 拍照

```java
mJTCameraView.takePicture();
```

#### 摄像头切换

```java
mJTCameraView.setCameraFacing(mFacing);
```

#### 设置缩放

```java
mJTCameraView.setZoom(value);
```

#### 设置曝光度

```java
mJTCameraView.setExposure(value);
```

#### 设置场景

```java
mJTCameraView.setSceneMode(value);
```

#### 设置闪光灯

```java
mJTCameraView.setFlashInternal
```

#### 人脸识别等更多功能说不清除，请看Demo

### 版本说明

1.0.4版本是通过测试的一个里程碑版本，解决了目前遇到的所有兼容性问题：

- 很多机型上前置摄像头拍照有镜面翻转问题。
- 三星机型拍照倒置。
- 小米机型拍照倒置。

### 测试通过机型

- 小米 5
- 小米 8
- 乐视 1S
- 三星 S6
- vivo X21
- 华为 mate 20X
- 华为 mate S
- 华为 mate 8
- 荣耀 Note 8

### FUTURE

- 横竖屏切换（这个其实已做完，但某些机型存在问题，没时间搞，所以撤掉了
- 闪光灯
- 对焦模式
- 手动对焦
- 人脸跟踪
- 笑脸拍照
- 录像
- EFI编辑
- 手动调整参数，比如白平衡等。。

### FAQ

1. 为何不用kotlin？

    虽然google官方在大力推进kotlin，并且kotlin的应用如雨后春笋，但目前大多数项目还是用java编写的。那么作为一个开源库，存在的意义就是被应用的更加广泛，那么就不得不更好的兼容老项目。
    
2. 为何不使用camera2的api，或者直接使用更新的CameraX？

    其实这个项目一开始就是用camera2写的，但是写完了之后在不同的机型上发现了很多兼容性问题，而且现阶段厂商对camera2的支持性并不好。以我自己的华为mate20X为例，作为2018年旗舰手机，对camera2的支持性竟然为「低」。
    
    当然在面对问题的时候，我们不喜欢以各种理由为借口，绕过或者遗留问题，所以我也是尽可能的查官文或者google解决方案，后来发现google自己的pixcel系列机型都存在问题，so...希望有一天厂商做好准备后，我会很高兴的采用camera2来重写。至于CameraX，只是基于camera2的封装罢了。
    
3. 与google开源的CameraView有什么区别？

    其实我在写的时候，很多地方都参照了cameraView，大家可以发现很多代码、命名都与cameraView相同。
    
    CameraView有一点不好，就是在5.0版本以上系统会强制使用camera2，那么就带来了兼容性问题。
    
    另外CameraView本质上是双层View嵌套，那么就存在着页面重复绘制问题。所以我采用单TextureView的方式来实现，从而保证了轻量化。
    
    CameraView单纯的只是摄像头调用，然并卵，并没有解决令我们头疼的兼容性问题。所以JTCamera在拍照之后处理了我们测试中遇到的一些问题，帮助苦逼的开发者（对，就是你）更快的集成摄像头功能。
    
4. 为何功能这么少？

    因为目前只有我一个人来开发维护，你懂的。任何开源项目的由来都只有一个：方便自己的基础上服务他人。目前我在项目中只需要用到这些功能，所以暂时只开发了这么多，但我保证以后会慢慢更新。也希望朋友们在JTCamera的基础开发其他功能，然后找我合并！
    
    