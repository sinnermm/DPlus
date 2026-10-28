package com.yolo.dyfusion.core;

import java.util.*;

public final class GestureMath {
  public static final List<String> ACTIONS =
      Collections.unmodifiableList(
          Arrays.asList(
              "default",
              "ignore",
              "menu",
              "play_pause",
              "speed",
              "auto_next",
              "media",
              "download",
              "clean",
              "settings",
              "comment"));

  public static int cell(float x, float y, int width, int height) {
    if (width <= 0 || height <= 0 || x < 0 || y < 0 || x >= width || y >= height) return -1;
    return Math.min(2, (int) (y * 3 / height)) * 3 + Math.min(2, (int) (x * 3 / width));
  }

  public static boolean tap(float x, float y, float x0, float y0, float slop) {
    double dx = x - x0, dy = y - y0;
    return dx * dx + dy * dy <= slop * slop;
  }
}
