package com.caca.copypoly.client;

/** Reverse MouseHandler's GUI coordinate conversion before applying the game's sensitivity curve. */
final class CameraMouseInput {
    private CameraMouseInput() {}
    static double rawDelta(double guiDelta,int windowExtent,int guiExtent) {
        return guiDelta*windowExtent/Math.max(1,guiExtent);
    }
    static double multiplier(double sensitivity) {
        // Float constants deliberately match MouseHandler.turnPlayer.
        double base=sensitivity*.6F+.2F;
        return base*base*base*8;
    }
}
