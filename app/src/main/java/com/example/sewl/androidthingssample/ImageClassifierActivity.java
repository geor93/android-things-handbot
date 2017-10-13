package com.example.sewl.androidthingssample;

import android.app.Activity;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import java.util.List;

public class ImageClassifierActivity extends Activity
                                     implements ImageReader.OnImageAvailableListener,
                                                CameraHandler.CameraReadyListener,
                                                ImageClassificationAsyncTask.ClassificationAvailableListener {

    private ImagePreprocessor imagePreprocessor;

    private CameraHandler mCameraHandler;

    private TensorFlowImageClassifier tensorFlowClassifier;

    private TensorFlowImageClassifier rpsTensorFlowClassifier;

    private HandlerThread mBackgroundThread;

    private Handler mBackgroundHandler;

    private long currentTime;

    private HandController handController;

    private StandbyController standbyController;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        init();
    }

    private void init() {
        mBackgroundThread = new HandlerThread("BackgroundThread");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
        mBackgroundHandler.post(mInitializeOnBackground);
        handController = new HandController();
        handController.init();
        standbyController = new StandbyController();
        standbyController.init(handController);
//        handController.test();
    }

    private Runnable mInitializeOnBackground = new Runnable() {
        @Override
        public void run() {
            imagePreprocessor = new ImagePreprocessor();
            mCameraHandler = CameraHandler.getInstance();
            mCameraHandler.initializeCamera(
                    ImageClassifierActivity.this, mBackgroundHandler,
                    ImageClassifierActivity.this);
            mCameraHandler.setCameraReadyListener(ImageClassifierActivity.this);
            tensorFlowClassifier = new TensorFlowImageClassifier(ImageClassifierActivity.this, Helper.MODEL_FILE, Helper.LABELS_FILE);
            rpsTensorFlowClassifier = new TensorFlowImageClassifier(ImageClassifierActivity.this, Helper.RPS_MODEL_FILE, Helper.RPS_LABELS_FILE);
        }
    };

    @Override
    public void onImageClassificationAvailable(List<Classifier.Recognition> classifications) {
        if (classifications.size() > 0) {
            Log.i("ACTION", "action: " + classifications);
//            handController.handleAction(classifications.get(0).getTitle());
            if (standbyController != null) {
                standbyController.run(classifications.get(0).getTitle());
            }
        }
    }

    @Override
    public void onImageAvailable(ImageReader reader) {
//        new ImageClassificationAsyncTask(
//                imagePreprocessor, tensorFlowClassifier, this).execute(reader);
        new ImageClassificationAsyncTask(
                imagePreprocessor, rpsTensorFlowClassifier, this).execute(reader);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (mBackgroundThread != null) mBackgroundThread.quit();
        } catch (Throwable t) { }

        handController.shutdown();
        mBackgroundThread = null;
        mBackgroundHandler = null;

        try {
            if (mCameraHandler != null) mCameraHandler.shutDown();
        } catch (Throwable t) { }
        try {
            if (tensorFlowClassifier != null) tensorFlowClassifier.destroyClassifier();
        } catch (Throwable t) { }
    }

    @Override
    public void onCameraReady() {
        mCameraHandler.takePicture();
    }
}
