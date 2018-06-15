package com.example.sewl.androidthingssample;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import java.util.List;
import java.util.Map;


/**
 * Created by mderrick on 10/16/17.
 */

public class ImageClassificationThread extends Thread {

    public static final int RESET_CODE     = 101;
    public static final int CLASSIFY_CODE  = 102;

    private static final String TAG = ImageClassificationThread.class.getSimpleName();

    private final Map<String, TensorFlowImageClassifier> classifiers;

    private final StandbyController standbyController;

    private Handler handler;

    private long currentTime;

    private boolean classifyingImage;

    public String results_formatted;

    public static String results_to_display;

    public ImageClassificationThread(StandbyController standbyController, Map<String, TensorFlowImageClassifier> classifiers, LightRingControl lightRingControl) {
        super("imageClassificationThread");
        this.standbyController = standbyController;
        this.classifiers = classifiers;
        currentTime = System.currentTimeMillis();
    }

    @Override
    public void run() {
        Looper.prepare();
        handler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                if (msg.arg1 == RESET_CODE) {
                    standbyController.reset();
                } else {
                    if (!classifyingImage) {
                        classifyImage((Bitmap) msg.obj);

                    }
                }
            }
        };
        Looper.loop();
        Looper.myLooper().quit();
    }

    public void classifyImage(Bitmap bitmap) {
        Log.i(TAG, "Took: " + (System.currentTimeMillis() - currentTime));
        currentTime = System.currentTimeMillis();

        TensorFlowImageClassifier classifier = classifiers.get(standbyController.getClassifierKey());
        if (classifier != null) {
            final List<Classifier.Recognition> results = classifier.doRecognize(bitmap);
            if (results.size() > 0) {
                standbyController.run(results.get(0).getTitle(), results);
                Log.i(TAG, "Results: " + results);
            }
            classifyingImage = false;
            //remove the [2] format substrings
            results_formatted = results.toString().replaceAll("\\[\\d+\\]\\ ", "");
            //replace the commas with new lines
            results_formatted = results_formatted.toString().replaceAll("\\, ", "\n");
            //remove the [ and ]
            results_formatted = results_formatted.toString().replaceAll("\\]|\\[", "\n");
            //replace the word negative with none
            results_formatted = results_formatted.toString().replaceAll("negative", "nothing");
            //replace % with % confidence
            results_formatted = results_formatted.toString().replaceAll("%", "% confidence");
            results_to_display = "Android Things operating system\non a NXP i.MX7D microcontroller\nrunning TensorFlow on-device\ntook " + (System.currentTimeMillis() - currentTime) +
                    " milliseconds\nto classify what the camera sees as\n" +
                    results_formatted;
        }
    }

    public Handler getHandler() {
        return handler;
    }
}
