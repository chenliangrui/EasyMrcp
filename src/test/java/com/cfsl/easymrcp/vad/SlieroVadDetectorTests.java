package com.cfsl.easymrcp.vad;

import ai.onnxruntime.OrtException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SlieroVadDetectorTests {

    private static final String MODEL_PATH = Paths.get("src/main/resources/silero_vad.onnx").toAbsolutePath().toString();
    private static final float DELTA = 1.0e-6f;

    @Test
    void updatesNoiseFloorWithEmaWhenNotTriggered() throws Exception {
        SlieroVadDetector detector = createDetector();
        try {
            invokeUpdateNoiseFloorIfNeeded(detector, 0.05f);

            assertEquals(0.012f, getFloatField(detector, "noiseFloorEnergy"), DELTA);
            assertEquals(0.0168f, getFloatField(detector, "energyThreshold"), DELTA);
        } finally {
            detector.close();
        }
    }

    @Test
    void updatesNoiseFloorEvenForSpeechLikeBackgroundWhenNotTriggered() throws Exception {
        SlieroVadDetector detector = createDetector();
        try {
            invokeUpdateNoiseFloorIfNeeded(detector, 0.05f);
            invokeUpdateNoiseFloorIfNeeded(detector, 0.50f);

            assertEquals(0.0364f, getFloatField(detector, "noiseFloorEnergy"), DELTA);
            assertEquals(0.05096f, getFloatField(detector, "energyThreshold"), DELTA);
        } finally {
            detector.close();
        }
    }

    @Test
    void ignoresNoiseFloorUpdatesWhileTriggered() throws Exception {
        SlieroVadDetector detector = createDetector();
        try {
            setBooleanField(detector, "triggered", true);
            setFloatField(detector, "noiseFloorEnergy", 0.02f);

            invokeUpdateNoiseFloorIfNeeded(detector, 0.50f);

            assertEquals(0.02f, getFloatField(detector, "noiseFloorEnergy"), DELTA);
            assertEquals(0.028f, getFloatField(detector, "energyThreshold"), DELTA);
        } finally {
            detector.close();
        }
    }

    @Test
    void keepsMinimumThresholdFloor() throws Exception {
        SlieroVadDetector detector = createDetector();
        try {
            setFloatField(detector, "noiseFloorEnergy", 0.001f);

            invokeUpdateNoiseFloorIfNeeded(detector, 0.0f);

            assertEquals(0.00095f, getFloatField(detector, "noiseFloorEnergy"), DELTA);
            assertEquals(0.01f, getFloatField(detector, "energyThreshold"), DELTA);
        } finally {
            detector.close();
        }
    }

    private SlieroVadDetector createDetector() throws OrtException {
        return new SlieroVadDetector(MODEL_PATH, 0.4f, 0.8f, 8000, 300, 500, 0.01f, 1.4f);
    }

    private void invokeUpdateNoiseFloorIfNeeded(SlieroVadDetector detector, float rmsEnergy) throws Exception {
        Method method = SlieroVadDetector.class.getDeclaredMethod("updateNoiseFloorIfNeeded", float.class);
        method.setAccessible(true);
        method.invoke(detector, rmsEnergy);
    }

    private float getFloatField(Object target, String fieldName) throws Exception {
        Field field = SlieroVadDetector.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getFloat(target);
    }

    private void setFloatField(Object target, String fieldName, float value) throws Exception {
        Field field = SlieroVadDetector.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setFloat(target, value);
    }

    private void setBooleanField(Object target, String fieldName, boolean value) throws Exception {
        Field field = SlieroVadDetector.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setBoolean(target, value);
    }
}
