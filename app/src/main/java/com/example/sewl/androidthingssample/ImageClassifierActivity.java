package com.example.sewl.androidthingssample;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.view.KeyEvent;

import com.google.android.things.contrib.driver.button.Button;
import com.google.android.things.contrib.driver.button.ButtonInputDriver;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ImageClassifierActivity extends Activity
                                     implements ImageReader.OnImageAvailableListener,
                                                CameraHandler.CameraReadyListener {

    public static final String CONFIG_BUTTON_GPPIO = "GPIO_33";

    private Map<String, TensorFlowImageClassifier> classifiers = new HashMap();

    private ImageClassificationThread imageClassificationThread;

    private TensorFlowImageClassifier rpsTensorFlowClassifier;

    private TensorFlowImageClassifier spidermanOkClassifier;

    private TensorFlowImageClassifier loserThreeClassifier;

    private TensorFlowImageClassifier oneRockClassifier;

    private SettingsRepository settingsRepository;

    private ButtonInputDriver mButtonInputDriver;

    private ImagePreprocessor imagePreprocessor;

    private StandbyController standbyController;

    private LightRingControl lightRingControl;

    private STATES currentState = STATES.IDLE;

    private HandlerThread mBackgroundThread;

    private SoundController soundController;

    private HandController handController;

    private CameraHandler mCameraHandler;

    private Handler mBackgroundHandler;

    private int keyPresses = 0;

    private enum STATES {
        IDLE,
        STARTUP,
        CONFIGURE
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        init();
    }

    private void init() {
        settingsRepository = new SettingsRepository(this);
        standbyController = new StandbyController();
        soundController = new SoundController(this);
        lightRingControl = new LightRingControl();
        lightRingControl.init();
        imagePreprocessor = new ImagePreprocessor();
        handController = new HandController();
        handController.init(settingsRepository);

        // Create Tensorflow classifiers
        rpsTensorFlowClassifier = new TensorFlowImageClassifier(ImageClassifierActivity.this, TensorflowImageOperations.RPS_MODEL_FILE, TensorflowImageOperations.RPS_LABELS_FILE);
        spidermanOkClassifier = new TensorFlowImageClassifier(ImageClassifierActivity.this, TensorflowImageOperations.SPIDERMAN_OK_MODEL, TensorflowImageOperations.SPIDERMAN_OK_LABELS);
        loserThreeClassifier = new TensorFlowImageClassifier(ImageClassifierActivity.this, TensorflowImageOperations.LOSER_THREE_MODEL, TensorflowImageOperations.LOSER_THREE_LABELS);
        oneRockClassifier = new TensorFlowImageClassifier(ImageClassifierActivity.this, TensorflowImageOperations.ONE_ROCK_MODEL, TensorflowImageOperations.ONE_ROCK_LABELS);

        standbyController.init(handController, lightRingControl, soundController);

        // Use a different specific classifier for actions that don't play well together
        classifiers.put(Signs.SPIDERMAN, spidermanOkClassifier);
        classifiers.put(Signs.THREE, loserThreeClassifier);
        classifiers.put(Signs.OK, spidermanOkClassifier);
        classifiers.put(Signs.ROCK, rpsTensorFlowClassifier);
        classifiers.put(Signs.PAPER, rpsTensorFlowClassifier);
        classifiers.put(Signs.SCISSORS, rpsTensorFlowClassifier);
        classifiers.put(Signs.LOSER, loserThreeClassifier);
        classifiers.put(Signs.ONE, oneRockClassifier);
        classifiers.put("rps", oneRockClassifier);
        classifiers.put("mirror", oneRockClassifier);
        classifiers.put("simon_says", rpsTensorFlowClassifier);

        mBackgroundThread = new HandlerThread("BackgroundThread");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
        mBackgroundHandler.post(mInitializeOnBackground);

        imageClassificationThread = new ImageClassificationThread(standbyController, classifiers, lightRingControl);
        imageClassificationThread.start();

        lightRingControl.setColor(Color.BLACK);

        runHandInit();
        setupConfigButton();
    }

    private void runHandInit() {
        handController.loose();
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                handController.moveToRPSReady();
            }
        }, 600);
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                handController.loose();
            }
        }, 1200);
    }

    private void setupConfigButton() {
        try {
            mButtonInputDriver = new ButtonInputDriver(CONFIG_BUTTON_GPPIO, Button.LogicState.PRESSED_WHEN_HIGH, KeyEvent.KEYCODE_SPACE);
            mButtonInputDriver.register();
        } catch (IOException e) {}
    }

    private Runnable mInitializeOnBackground = new Runnable() {
        @Override
        public void run() {
            mCameraHandler = CameraHandler.getInstance();
            mCameraHandler.initializeCamera(
                    ImageClassifierActivity.this, mBackgroundHandler,
                    ImageClassifierActivity.this);
            mCameraHandler.setCameraReadyListener(ImageClassifierActivity.this);
        }
    };

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        keyPresses++;
        if (keyPresses >= 3 && currentState == STATES.STARTUP) {
            currentState = STATES.CONFIGURE;
            lightRingControl.setColor(Color.CYAN);
            soundController.playSound(SoundController.SOUNDS.CORRECT);
            runFlexForearmTest();
        } else if (currentState == STATES.CONFIGURE) {
            settingsRepository.incrementForearmOffset();
            runFlexForearmTest();
        }
        return super.onKeyUp(keyCode, event);
    }

    private void runFlexForearmTest() {
        handController.forearm.flex();
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                handController.forearm.loose();
            }
        }, 500);
    }

    @Override
    public void onImageAvailable(ImageReader reader) {
        if (imageClassificationThread != null && imageClassificationThread.isAlive()) {
            final Bitmap bitmap;
            try (Image image = reader.acquireLatestImage()) {
                bitmap = imagePreprocessor.preprocessImage(image);
            }
            if (bitmap != null) {
                Message message = new Message();
                message.obj = bitmap;
                imageClassificationThread.getHandler().sendMessage(message);
            }
            mCameraHandler.takePicture();
        }
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
            if (rpsTensorFlowClassifier != null) rpsTensorFlowClassifier.destroyClassifier();
            if (spidermanOkClassifier != null) spidermanOkClassifier.destroyClassifier();
            if (loserThreeClassifier != null) loserThreeClassifier.destroyClassifier();
            if (oneRockClassifier != null) oneRockClassifier.destroyClassifier();
        } catch (Throwable t) { }
    }

    @Override
    public void onCameraReady() {
        mCameraHandler.takePicture();
    }
}
