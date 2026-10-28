package com.yolo.dyfusion.core;

public final class QualityRank {
  public static int compare(
      long w,
      long h,
      long fps,
      long rate,
      long size,
      long w2,
      long h2,
      long fps2,
      long rate2,
      long size2) {
    long area = Math.max(0, w) * Math.max(0, h), area2 = Math.max(0, w2) * Math.max(0, h2);
    int c = Long.compare(area, area2);
    if (c == 0) c = Long.compare(Math.max(0, fps), Math.max(0, fps2));
    if (c == 0) c = Long.compare(Math.max(0, rate), Math.max(0, rate2));
    if (c == 0) c = Long.compare(Math.max(0, size), Math.max(0, size2));
    return c;
  }
}
