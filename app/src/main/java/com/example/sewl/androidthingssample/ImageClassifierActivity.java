package com.example.sewl.androidthingssample;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.sewl.androidthingssample.cloud.CloudPublisherService;
import com.google.android.things.contrib.driver.button.Button;
import com.google.android.things.contrib.driver.button.ButtonInputDriver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ImageClassifierActivity extends Activity
                                     implements ImageReader.OnImageAvailableListener,
                                                CameraHandler.CameraReadyListener {

    private static final String TAG = "ImageClassifierActivity";

    private Map<String, TensorFlowImageClassifier> classifiers = new HashMap();

    private ImageClassificationThread imageClassificationThread;

    private TensorFlowImageClassifier rpsTensorFlowClassifier;

    private TensorFlowImageClassifier spidermanOkClassifier;

    private TensorFlowImageClassifier loserThreeClassifier;

    private TensorFlowImageClassifier oneRockClassifier;

    private TensorFlowImageClassifier mirrorClassifier;

    private SettingsRepository settingsRepository;

    private ButtonInputDriver mButtonInputDriver;

    private ImagePreprocessor imagePreprocessor;

    private StandbyController standbyController;

    private LightRingControl lightRingControl;

    private STATES currentState = STATES.IDLE;

    private HandlerThread mBackgroundThread;

    private SoundController soundController;

    private ButtonInputDriver resetButton;

    private HandController handController;

    private CameraHandler mCameraHandler;

    private Handler mBackgroundHandler;

    public ImageView mImage;
    public TextView mResultText;

    private CloudPublisherService mPublishService;

    private int keyPresses = 0;

    private enum STATES {
        IDLE,
        STARTUP,
        CONFIGURE,
        RUN
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_camera);
        mImage = findViewById(R.id.imageView);
        mResultText = findViewById(R.id.resultText);

        initializeServiceIfNeeded();

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
        mirrorClassifier = new TensorFlowImageClassifier(ImageClassifierActivity.this, TensorflowImageOperations.MIRROR_MODEL, TensorflowImageOperations.MIRROR_LABELS);

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
        classifiers.put(Signs.HANG_LOOSE, mirrorClassifier);
        classifiers.put("rps", rpsTensorFlowClassifier);
        //line below edited by stephen hawes on 12/12/17
        classifiers.put("mirror", rpsTensorFlowClassifier);
        classifiers.put("simon_says", rpsTensorFlowClassifier);

        mBackgroundThread = new HandlerThread("BackgroundThread");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
        mBackgroundHandler.post(mInitializeOnBackground);

        imageClassificationThread = new ImageClassificationThread(standbyController, classifiers, lightRingControl);
        imageClassificationThread.start();

        lightRingControl.setColor(Color.BLACK);

        runHandInit();
        setupButtons();
    }

    private void runHandInit() {
        handController.loose();
        Handler handInitHandler = new Handler();
        handInitHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                handController.moveToRPSReady();
            }
        }, 600);
        handInitHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                handController.loose();
            }
        }, 1200);
        handInitHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                currentState = STATES.RUN;
            }
        }, 10000);
    }

    private void initializeServiceIfNeeded() {
        if (mPublishService == null) {
            try {
                // Bind to the service
                Intent intent = new Intent(this, CloudPublisherService.class);
                bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);
                Log.e(TAG, "Connecting to CloudPublisherService");
            } catch (Throwable t) {
                Log.e(TAG, "Could not connect to the service, will try again later", t);
            }
        }
    }

    private void setupButtons() {
        try {
            mButtonInputDriver = new ButtonInputDriver(BoardDefaults.CONFIG_BUTTON_GPIO, Button.LogicState.PRESSED_WHEN_HIGH, KeyEvent.KEYCODE_SPACE);
            resetButton = new ButtonInputDriver(BoardDefaults.RESET_BUTTON_GPIO, Button.LogicState.PRESSED_WHEN_HIGH, KeyEvent.KEYCODE_E);
            mButtonInputDriver.register();
            resetButton.register();
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
        if (keyCode == KeyEvent.KEYCODE_SPACE) {
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
        } else if (keyCode == KeyEvent.KEYCODE_E) {
            if (imageClassificationThread != null && imageClassificationThread.isAlive()) {
                Message msg = new Message();
                msg.arg1 = ImageClassificationThread.RESET_CODE;
                imageClassificationThread.getHandler().sendMessage(msg);
            }
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

                //https://stackoverflow.com/questions/14294287/only-the-original-thread-that-created-a-view-hierarchy-can-touch-its-views
                //Update screen attached to the microcontroller
                runOnUiThread(new Runnable() {
                    public void run() {
                        mResultText.setText(ImageClassificationThread.results_to_display);
                        mImage.setImageBitmap(bitmap);

                        //This is from the SensorHubActivity example
                        initializeServiceIfNeeded();
                        //connectToAvailableSensors();
                        if (mPublishService != null) {
                            List<SensorData> sensorsData = new ArrayList<>();
                            //addBmx280Readings(sensorsData);
                            Log.d(TAG, "collected continuous sensor data: " + sensorsData);
                            mPublishService.logSensorData(sensorsData);
                            Log.i(TAG, "Sensor publishing");
                        }


                    }
                });


            }
            if (bitmap != null) {
                Message message = new Message();
                message.obj = bitmap;
                imageClassificationThread.getHandler().sendMessage(message);
            }
            mCameraHandler.takePicture();

        }
    }

    private void collectContinuousSensors() {
        if (mPublishService != null) {
            List<SensorData> sensorsData = new ArrayList<>();
            //addBmx280Readings(sensorsData);
            Log.d(TAG, "collected continuous sensor data: " + sensorsData);
            mPublishService.logSensorData(sensorsData);
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

    /**
     * Callback for service binding, passed to bindService()
     */
    private ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            CloudPublisherService.LocalBinder binder = (CloudPublisherService.LocalBinder) service;
            mPublishService = binder.getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mPublishService = null;
        }
    };
}
