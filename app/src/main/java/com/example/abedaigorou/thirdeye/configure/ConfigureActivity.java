package com.example.abedaigorou.thirdeye.configure;

/**
 * Created by abedaigorou on 2017/08/29.
 */

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Paint;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.AppLaunchChecker;
import android.util.Log;

import com.example.abedaigorou.thirdeye.CaptureManager;
import com.example.abedaigorou.thirdeye.ImageUtils;
import com.example.abedaigorou.thirdeye.R;
import com.example.abedaigorou.thirdeye.configure.CameraConfigure.CameraConfigureFragment;
import com.example.abedaigorou.thirdeye.configure.CameraConfigure.PreviewFragment;
import com.example.abedaigorou.thirdeye.configure.VRConfigure.GLPreviewFragment;
import com.example.abedaigorou.thirdeye.configure.VRConfigure.VRConfigureFragment;

import org.opencv.android.InstallCallbackInterface;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.util.HashSet;
import java.util.Set;

public class ConfigureActivity extends Activity implements VRConfigureFragment.VRConfigureEventListener,CameraConfigureFragment.CameraConfigureEventListener,
        PreviewFragment.PreviewFragmentEventListener
{
    private Bitmap bitmap;
    private int width,height,afMode;
    private Mat rgbaMatOut,bgrMat,mYuvMat;
    PreviewFragment previewFragment;
    CameraConfigureFragment configureFragment;
    GLPreviewFragment glPreviewFragment;
    VRConfigureFragment vrConfigureFragment;
    private CaptureManager manager;
    private CaptureManager.CaptureEventListener listener;
    private final String TAG="ConfigureActivity";
    public final static String INTENTTAG="Intent_ConfigureActivity";
    public final static String BUNDLETAG="Bundle_ConfigureActivity";
    public final static int REQUEST_CODE_CAMERA=0;
    public final static int REQUEST_CODE_VR=1;
    public final static int REQUEST_CODE_FIRSTTIME=2;
    private SharedPreferences sharedPreferences;
    private int requestCode;
    private static ConfigureActivity instance;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sharedPreferences=PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        setContentView(R.layout.activity_configure);
        Bundle request=getIntent().getExtras();

        requestCode=request.getInt(INTENTTAG);

        bitmap=null;
        instance=this;
    }

    public static ConfigureActivity getInstance(){
        return instance;
    }

    private void init(){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {

                // Fragmentの追加や削除といった変更を行う際は、Transactionを利用します
                FragmentTransaction transaction = getFragmentManager().beginTransaction();
                // 新しく追加を行うのでaddを使用します
                // 他にも、メソッドにはreplace removeがあります
                // メソッドの1つ目の引数は対象のViewGroupのID、2つ目の引数は追加するfragment
                Fragment fragment1 = getFragmentManager().findFragmentById(R.id.container1);
                Fragment fragment2=  getFragmentManager().findFragmentById(R.id.container2);

                switch (requestCode){
                    case REQUEST_CODE_FIRSTTIME:
                    case REQUEST_CODE_CAMERA:

                        if(fragment1!=null&&fragment1 instanceof PreviewFragment){
                            previewFragment=(PreviewFragment)fragment1;
                        }else{
                            previewFragment=PreviewFragment.createInstance(bitmap);
                            transaction.add(R.id.container1,previewFragment);
                        }

                        if(fragment2!=null&&fragment2 instanceof CameraConfigureFragment){
                            configureFragment=(CameraConfigureFragment)fragment2;
                        }else{
                            configureFragment=CameraConfigureFragment.createInstance(1920,1080);
                            transaction.add(R.id.container2,configureFragment);
                        }
                        break;

                    case REQUEST_CODE_VR:
                        if(fragment1!=null&&fragment1 instanceof GLPreviewFragment){
                            glPreviewFragment=(GLPreviewFragment)fragment1;
                        }else{
                            glPreviewFragment=new GLPreviewFragment();
                            transaction.add(R.id.container1,glPreviewFragment);
                        }

                        if(fragment2!=null&&fragment2 instanceof VRConfigureFragment){
                            vrConfigureFragment=(VRConfigureFragment) fragment2;
                        }else{
                            vrConfigureFragment=new VRConfigureFragment();
                            transaction.add(R.id.container2,vrConfigureFragment);
                        }
                        break;
                }
                transaction.commit();
            }
        });
    }

    @Override
    public void onResume(){
        super.onResume();
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_1_0, this, new LoaderCallbackInterface() {
            @Override
            public void onManagerConnected(int status) {
                if(status== LoaderCallbackInterface.SUCCESS) {
                    Log.i("a", "success");
                    rgbaMatOut = new Mat();
                    bgrMat = new Mat(height, width, CvType.CV_8UC4);
                    init();

                }
            }

            @Override
            public void onPackageInstall(int operation, InstallCallbackInterface callback) {
                Log.i("a","install");
            }
        });
    }

    @Override
    public void onImageSizeConfigured(String imageSize) {
        Log.i(TAG,"onImageSizeConfigured");

        int [] sizes= ConfigureUtils.getSplitedInt(imageSize,"x");
        if(sizes.length<2){
            return;
        }
        if(!(sizes[0]==CaptureManager.getWidth()&&sizes[1]==CaptureManager.getHeight())) {
            width=sizes[0];
            height=sizes[1];
            manager.setImageSize(imageSize);
            bgrMat=new Mat(sizes[1],sizes[0], CvType.CV_8UC4);
        }
    }

    @Override
    public void onAutoFocusConfigured(int afMode) {
        if(manager.getAFMODE()!=afMode)
            manager.setAFMode(afMode);
    }

    @Override
    public void onFocusDistanceConfigured(float focusdist) {
        if(focusdist!=manager.getLENSDIST()) {
            manager.setFocusDistance(focusdist);
        }
    }

    @Override
    public void onPreviewViewCreated() {
        Log.i(TAG,"onViewCreated");
    }

    @Override
    public void onCameraConfigureViewCreated() {
        listener=new CaptureManager.CaptureEventListener() {
            @Override
            public void onTakeImage(final byte[] data) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mYuvMat = ImageUtils.ByteToMat(data, width, height);
                        Imgproc.cvtColor(mYuvMat, bgrMat, Imgproc.COLOR_YUV2BGR_I420);
                        Imgproc.cvtColor(bgrMat, rgbaMatOut, Imgproc.COLOR_BGR2RGBA, 0);
                        bitmap = Bitmap.createBitmap(bgrMat.cols(), bgrMat.rows(), Bitmap.Config.ARGB_8888);
                        Utils.matToBitmap(rgbaMatOut, bitmap);
                        previewFragment.setImageData(bitmap);
                    }
                });
            }

            @Override
            public void onConfigured() {

            }

            @Override
            public void onOpened() {

            }
        };
        if(requestCode==REQUEST_CODE_FIRSTTIME){
            //初回起動
            manager=CaptureManager.newInstance(this,listener);
            SharedPreferences.Editor editor=sharedPreferences.edit();
            Set<String> sizesSet=new HashSet<>();
            String[] stsize=manager.getAvailableImageSize();

            for(String e:stsize)
                sizesSet.add(e);

            //デフォルト画像サイズ(最小)、デフォルトAFモード(AFOFF)、利用可能画像サイズ
            editor.putString(getString(R.string.key_size_preference),stsize[stsize.length-1]);
            editor.putString(getString(R.string.key_autofocus_preference),"0");
            editor.putStringSet(getString(R.string.key_availableimagesizes_preference),sizesSet);
            editor.apply();
        }else{
            //二回目以降
            manager=CaptureManager.getInstance();
            manager.setListener(listener);
        }

        configureFragment.setImageSizes(manager.getAvailableImageSize());
        configureFragment.setMaxFocus(manager.getMaxFocus());
        if(manager.getHardwareLebel()==2){
            configureFragment.setEnabled(false,R.string.key_focus_preference);
        }

        int[] isize=ConfigureUtils.getConfiguredSize(getApplicationContext(),sharedPreferences);
        width=isize[0];
        height=isize[1];
        afMode=ConfigureUtils.getConfiguredAFMode(getApplicationContext(),sharedPreferences);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // パーミッションが必要な処理
            Log.i(TAG,"permissionAccepted");
            manager.start("0",width,height,afMode);
        } else {
            // パーミッションが得られなかった時
            // 処理を中断する・エラーメッセージを出す・アプリケーションを終了する等
        }
    }

    @Override
    public void onVRConfigureViewCreated() {

    }

    @Override
    public void onCameraPositionConfigured(int progress) {

    }

    @Override
    public void onDevideLongitudeConfigured(int devide) {

    }

    @Override
    public void onDevideLatitudeConfigured(int devide) {

    }
}

