package com.example.sewl.androidthingssample;

import android.os.Build;

/**
 * Created by mderrick on 11/20/17.
 */

@SuppressWarnings("WeakerAccess")
public interface BoardDefaults {
     String DEVICE_RPI3 = "rpi3";
     String DEVICE_IMX6UL_PICO = "imx6ul_pico";
     String DEVICE_IMX7D_PICO = "imx7d_pico";

    static String getI2cBusForSensors() {
        switch (Build.DEVICE) {
            case DEVICE_RPI3:
                return "I2C1";
            case DEVICE_IMX6UL_PICO:
                return "I2C2";
            case DEVICE_IMX7D_PICO:
                return "I2C1";
            default:
                throw new IllegalArgumentException("Unknown device: " + Build.DEVICE);
        }
    }

    static String getGPIOForMotionDetector() {
        switch (Build.DEVICE) {
            case DEVICE_RPI3:
                return "BCM21";
            case DEVICE_IMX6UL_PICO:
                return "GPIO4_IO20";
            case DEVICE_IMX7D_PICO:
                return "GPIO6_IO14";
            default:
                throw new IllegalArgumentException("Unknown device: " + Build.DEVICE);
        }
    }

        interface HandPinout {
            int THUMB = 8;
            int RING = 0;
            int MIDDLE = 2;
            int INDEX = 4;
            int PINKY = 5;
            int WRIST = 9;

            // If facing the hand to play RPS
            int FOREARM_ON_USER_RIGHT = 12;
            int FOREARM_ON_USER_LEFT = 13;
        }

        String CONFIG_BUTTON_GPIO = "GPIO_33";
        String RESET_BUTTON_GPIO = "GPIO_35";
        String DEFAULT_SPI_BUS = "SPI3.0";

        int LED_BRIGHTNESS = 28;
        float DEFAULT_VOLUME = 1f;
}