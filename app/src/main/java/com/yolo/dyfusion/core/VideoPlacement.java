package com.yolo.dyfusion.core;

/** Center the transformed visible layer, not its unscaled layout box. */
public final class VideoPlacement {
  private VideoPlacement() {}

  public static float offset(
      float screenTop,
      float screenHeight,
      float baselineTop,
      float layerHeight,
      float scaleY,
      float microOffset) {
    if (!Float.isFinite(screenTop)
        || !Float.isFinite(screenHeight)
        || !Float.isFinite(baselineTop)
        || !Float.isFinite(layerHeight)
        || !Float.isFinite(scaleY)
        || screenHeight <= 0
        || layerHeight <= 0
        || scaleY <= 0) return microOffset;
    return screenTop + screenHeight / 2f - (baselineTop + layerHeight * scaleY / 2f) + microOffset;
  }
}
