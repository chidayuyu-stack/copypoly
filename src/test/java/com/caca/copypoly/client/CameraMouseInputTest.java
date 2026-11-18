package com.caca.copypoly.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CameraMouseInputTest {
    @Test void samePhysicalMouseTravelIsIndependentOfGuiScale() {
        for(int scale=1;scale<=6;scale++) {
            assertEquals(120,CameraMouseInput.rawDelta(120.0/scale,1920,1920/scale),1e-9);
            assertEquals(-60,CameraMouseInput.rawDelta(-60.0/scale,1080,1080/scale),1e-9);
        }
    }
    @Test void reversesRoundedGuiDimensionsAndUsesWindowCoordinatesForHighDpi() {
        assertEquals(37,CameraMouseInput.rawDelta(37.0*641/1921,1921,641),1e-9);
        assertEquals(-21,CameraMouseInput.rawDelta(-21.0*361/1081,1081,361),1e-9);
        assertEquals(80,CameraMouseInput.rawDelta(40,1280,640),1e-9);
    }
    @Test void followsVanillaSensitivityCurveAcrossSliderRange() {
        assertEquals(.064,CameraMouseInput.multiplier(0),1e-7);
        assertEquals(1,CameraMouseInput.multiplier(.5),1e-6);
        assertEquals(4.096,CameraMouseInput.multiplier(1),1e-6);
        assertTrue(CameraMouseInput.multiplier(.25)<CameraMouseInput.multiplier(.5));
    }
}
