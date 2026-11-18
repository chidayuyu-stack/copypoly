package com.caca.copypoly.client;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldEditorCameraTest {
    private static void near(Vec3 expected,Vec3 actual) {assertTrue(expected.distanceTo(actual)<1e-6,()->expected+" != "+actual);}
    @Test void fliesAlongViewDirectionAndUsesWorldVerticalForElevation() {
        near(new Vec3(0,0,.2),WorldEditorCamera.step(0,0,1,0,0,.2));
        near(new Vec3(-.2,0,0),WorldEditorCamera.step(90,0,1,0,0,.2));
        near(new Vec3(0,.2,0),WorldEditorCamera.step(0,-90,1,0,0,.2));
        near(new Vec3(-.2,0,0),WorldEditorCamera.step(0,0,0,1,0,.2));
        near(new Vec3(0,-.2,0),WorldEditorCamera.step(123,27,0,0,-1,.2));
    }
    @Test void diagonalFlightKeepsConstantSpeedAndNeutralInputStopsImmediately() {
        assertEquals(.2,WorldEditorCamera.step(145,20,1,1,1,.2).length(),1e-7);
        near(Vec3.ZERO,WorldEditorCamera.step(145,20,0,0,0,.2));
        var slow=WorldEditorCamera.step(145,20,1,0,0,.2);
        near(slow.scale(3),WorldEditorCamera.step(145,20,1,0,0,.6));
        near(slow.scale(-1),WorldEditorCamera.step(145,20,-1,0,0,.2));
    }
}
