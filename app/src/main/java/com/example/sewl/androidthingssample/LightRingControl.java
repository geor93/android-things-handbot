package com.example.sewl.androidthingssample;

import android.graphics.Color;
import android.util.Log;

import com.google.android.things.contrib.driver.apa102.Apa102;

import java.io.IOException;

/**
 * Created by mderrick on 10/16/17.
 */

public class LightRingControl {

    private static final String TAG = LightRingControl.class.getSimpleName();

    public static final long FAST_PULSE_DELAY_MILLIS  = 4;
    private static final int NUMBER_OF_LEDS           = 24;
    private static final int PULSE_DELAY              = 10;
    private static final int NUMBER_OF_LED_STEPS      = 720;
    private static final float SWIRL_SECONDS          = 0.5f;

    private static final long FLASH_DELAY             = 1;

    private Thread ledThread;

    private Apa102 mLedstrip;

    private int totalPulsesToRun = 0;

    public LightRingControl() {
    }

    public void init() {
        try {
            mLedstrip = new Apa102(BoardDefaults.DEFAULT_SPI_BUS, Apa102.Mode.RBG, Apa102.Direction.NORMAL);
            mLedstrip.setBrightness(BoardDefaults.LED_BRIGHTNESS);
        } catch (IOException e) { }
    }

    public void runSwirl(int pulsesToRun, final int color, final float totalPulseSeconds) {
        final float[] hsv = new float[3];
        final long totalMillis = Math.round((totalPulseSeconds / (float) (NUMBER_OF_LED_STEPS)) * 1000);
        Color.RGBToHSV(Color.red(color), Color.green(color), Color.blue(color), hsv);
        this.totalPulsesToRun = pulsesToRun;
        ledThread = new Thread(new Runnable() {
            @Override
            public void run() {
                int numberOfRuns = 0;
                while(numberOfRuns < totalPulsesToRun) {
                    swirl(hsv[0], totalMillis);
                    numberOfRuns++;
                }
                stopLedThread();
            }
        });
        ledThread.start();
    }

    public void runSwirl(int pulsesToRun, final int color) {
        runSwirl(pulsesToRun, color, SWIRL_SECONDS);
    }

    private void swirl(float ledColor, long pulseDelay) {
        int[] colors = new int[NUMBER_OF_LEDS];
        for (int i = 0; i <= NUMBER_OF_LED_STEPS; i+=2) {
            float t = i/(NUMBER_OF_LED_STEPS * 0.5f);
            for (int j = 0; j < NUMBER_OF_LEDS; j++) {
                float offset = (float)j / (float) NUMBER_OF_LEDS;
                double lightness = 0.3f * Math.sin(t * Math.PI - 2*offset);
                int color = Color.HSVToColor(new float[]{ ledColor, 1.0f, (float) lightness});
                colors[j] = color;
            }

            try {
                mLedstrip.write(colors);
            } catch (IOException e) {
                e.printStackTrace();
            }
            sleep(pulseDelay);
        }
    }

    public void setRPSScore(int me, int them) {
        int ledsPerMark = NUMBER_OF_LEDS/3;
        int[] colors = new int[NUMBER_OF_LEDS];

        for (int i = 0; i < me*ledsPerMark; i++) {
            colors[i] = Color.GREEN;
        }
        for (int i = NUMBER_OF_LEDS - 1; i > (NUMBER_OF_LEDS - 1 - them*ledsPerMark); i--) {
            colors[i] = Color.RED;
        }
        try {
            mLedstrip.write(colors);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void showMatchingLights(int number, int wrong) {
        int ledsPerMark = NUMBER_OF_LEDS/8;
        int[] colors = new int[NUMBER_OF_LEDS];
        final float[] hsv = new float[3];
        Color.RGBToHSV(Color.red(Color.RED), Color.green(Color.RED), Color.blue(Color.RED), hsv);
        int red = Color.HSVToColor(new float[]{hsv[0], 1.0f, 0.6f});
        Color.RGBToHSV(Color.red(Color.GREEN), Color.green(Color.GREEN), Color.blue(Color.GREEN), hsv);
        int green = Color.HSVToColor(new float[]{hsv[0], 1.0f, 0.6f});

        for (int i = 0; i < number; i++) {
            colors[shiftLedIndex(i*ledsPerMark)] = Color.BLACK;
            colors[shiftLedIndex(i*ledsPerMark + 1)] = green;
            colors[shiftLedIndex(i*ledsPerMark + 2)] = Color.BLACK;
        }
        for (int i = number; i < number + wrong; i++) {
            colors[shiftLedIndex(i*ledsPerMark)] = Color.BLACK;
            colors[shiftLedIndex(i*ledsPerMark + 1)] = red;
            colors[shiftLedIndex(i*ledsPerMark + 2)] = Color.BLACK;
        }

        writeToLEDStrip(colors);
    }

    private void writeToLEDStrip(int[] colors) {
        try {
            mLedstrip.write(colors);
        } catch (IOException e) {
            Log.e(TAG, "Failed to write to LED strip", e);
        }
    }

    private int shiftLedIndex(int index) {
        return (index + Math.round(NUMBER_OF_LEDS * 0.70f)) % NUMBER_OF_LEDS;
    }

    public void runPulse(int pulses, int color) {
        stopLedThread();
        totalPulsesToRun = pulses;
        final float[] hsv = new float[3];
        Color.RGBToHSV((color >> 16)& 0xFF, (color >> 8) & 0xFF, color & 0xFF, hsv);
        ledThread = new Thread(new Runnable() {
            @Override
            public void run() {
                int numberOfRuns = 0;
                while(numberOfRuns < totalPulsesToRun) {
                    illuminate(hsv[0], PULSE_DELAY);
                    deluminate(hsv[0], PULSE_DELAY);
                    numberOfRuns++;
                }
                stopLedThread();
            }
        });
        ledThread.start();
    }

    public void flash(int pulses, int color) {
        stopLedThread();
        totalPulsesToRun = pulses;
        final float[] hsv = new float[3];
        Color.RGBToHSV((color >> 16)& 0xFF, (color >> 8) & 0xFF, color & 0xFF, hsv);
        ledThread = new Thread(new Runnable() {
            @Override
            public void run() {
                int numberOfRuns = 0;
                while(numberOfRuns < totalPulsesToRun) {
                    illuminate(hsv[0], FLASH_DELAY);
                    deluminate(hsv[0], FLASH_DELAY);
                    numberOfRuns++;
                }
                stopLedThread();
            }
        });
        ledThread.start();
    }

    public void runScorePulse(int pulses, final int right, final int wrong) {
        stopLedThread();
        totalPulsesToRun = pulses;
        final float[] redHsv = new float[3];
        final float[] greenHsv = new float[3];
        final int ledsPerMark = NUMBER_OF_LEDS/8;
        Color.RGBToHSV(Color.red(Color.RED), Color.green(Color.RED), Color.blue(Color.RED), redHsv);
        Color.RGBToHSV(Color.red(Color.GREEN), Color.green(Color.GREEN), Color.blue(Color.GREEN), greenHsv);

        ledThread = new Thread(new Runnable() {
            @Override
            public void run() {
                int numberOfRuns = 0;
                while(numberOfRuns < totalPulsesToRun) {
                    int[] colors = new int[NUMBER_OF_LEDS];
                    for (int i = 0; i < 170; i+=2) {
                        float t = i/360.0f;
                        int pulseGreen = Color.HSVToColor(new float[]{ greenHsv[0], 1.0f, t*0.6f});
                        for (int j = 0; j < right; j++) {
                            colors[shiftLedIndex(j * ledsPerMark)] = Color.BLACK;
                            colors[shiftLedIndex(j * ledsPerMark + 1)] = pulseGreen;
                            colors[shiftLedIndex(j * ledsPerMark + 2)] = Color.BLACK;
                        }
                        int pulseRed = Color.HSVToColor(new float[] { redHsv[0], 1.0f, t*0.6f});
                        for (int j = right; j < right + wrong; j++) {
                            colors[shiftLedIndex(j * ledsPerMark)] = Color.BLACK;
                            colors[shiftLedIndex(j * ledsPerMark + 1)] = pulseRed;
                            colors[shiftLedIndex(j * ledsPerMark + 2)] = Color.BLACK;
                        }

                        writeToLEDStrip(colors);
                        sleep(FAST_PULSE_DELAY_MILLIS);
                    }

                    for (int i = 170; i >= 0; i-=2) {
                        float t = i/360.0f;
                        int pulseGreen = Color.HSVToColor(new float[]{ greenHsv[0], 1.0f, t*0.6f});
                        for (int j = 0; j < right; j++) {
                            colors[shiftLedIndex(j * ledsPerMark)] = Color.BLACK;
                            colors[shiftLedIndex(j * ledsPerMark + 1)] = pulseGreen;
                            colors[shiftLedIndex(j * ledsPerMark + 2)] = Color.BLACK;
                        }
                        int pulseRed = Color.HSVToColor(new float[]{ redHsv[0], 1.0f, t*0.6f});
                        for (int j = right; j < right + wrong; j++) {
                            colors[shiftLedIndex(j * ledsPerMark)] = Color.BLACK;
                            colors[shiftLedIndex(j * ledsPerMark + 1)] = pulseRed;
                            colors[shiftLedIndex(j * ledsPerMark + 2)] = Color.BLACK;
                        }
                        try {
                            mLedstrip.write(colors);
                        } catch (IOException e) {}

                        sleep(FAST_PULSE_DELAY_MILLIS);
                    }
                    numberOfRuns++;
                }
                stopLedThread();
            }
        });
        ledThread.start();
    }

    private void illuminate(float ledColor, long delay) {
        int[] colors = new int[NUMBER_OF_LEDS];
        for (int i = 0; i < 170; i+=2) {
            float t = i/360.0f;
            for (int j = 0; j < NUMBER_OF_LEDS; j++) {
                int color = Color.HSVToColor(new float[]{ ledColor, 1.0f, t});
                colors[j] = color;
            }

            writeToLEDStrip(colors);
            sleep(delay);
        }
    }

    private void deluminate(float ledColor, long delay) {
        int[] colors = new int[NUMBER_OF_LEDS];
        for (int i = 170; i >= 0; i-=2) {
            float t = i/360.0f;
            for (int j = 0; j < NUMBER_OF_LEDS; j++) {
                int color = Color.HSVToColor(new float[]{ ledColor, 1.0f, t});
                colors[j] = color;
            }
            try {
                mLedstrip.write(colors);
            } catch (IOException e) {
            }

            sleep(delay);
        }
    }

    private void sleep(long delay) {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Log.e(TAG, "Failed to sleep", e);
        }
    }

    private void stopLedThread() {
        if (ledThread != null) {
            ledThread.interrupt();
            ledThread = null;
            writeToLEDStrip(new int[NUMBER_OF_LEDS]);
        }
    }

    public void setColor(int color) {
        int[] colors = new int[NUMBER_OF_LEDS];

        for (int i = 0; i < NUMBER_OF_LEDS; i++) {
            colors[i] = color;
        }
        writeToLEDStrip(colors);
    }
}
