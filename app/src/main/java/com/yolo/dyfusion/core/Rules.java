package com.yolo.dyfusion.core;

import java.util.*;
import java.util.regex.*;

public final class Rules {
  private Rules() {}

  public static Set<String> tokens(String s) {
    Set<String> r = new LinkedHashSet<>();
    for (String t : (s == null ? "" : s).split("[,，\\n\\r]")) {
      t = t.trim();
      if (!t.isEmpty()) r.add(t);
    }
    return r;
  }

  public static boolean contains(String text, String words) {
    String t = (text == null ? "" : text).toLowerCase(Locale.ROOT);
    for (String w : tokens(words)) if (t.contains(w.toLowerCase(Locale.ROOT))) return true;
    return false;
  }

  public static boolean exact(String text, String words) {
    return tokens(words).contains(text == null ? "" : text);
  }

  public static String cleanAt(String s) {
    return s.replaceAll("(?<![\\w@])@[^\\s@]+", "").replaceAll("[ \\t]{2,}", " ").trim();
  }

  public static String safeName(String s) {
    String r =
        (s == null ? "" : s)
            .replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_")
            .replaceAll("[. ]+$", "")
            .trim();
    if (r.isEmpty() || r.equals(".") || r.equals("..")) r = "media";
    if (r.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\\..*)?")) r = "_" + r;
    return r.substring(0, Math.min(100, r.length()));
  }

  public static String folder(String s) {
    String v = (s == null ? "" : s).replace('\\', '/').trim();
    if (v.isEmpty()) v = "DyFusion";
    if (v.startsWith("/") || v.contains(":") || v.length() > 120)
      throw new IllegalArgumentException("必须是 Download 下的相对子目录");
    for (String p : v.split("/", -1))
      if (p.isEmpty() || p.equals(".") || p.equals(".."))
        throw new IllegalArgumentException("目录不能包含空段、. 或 ..");
    return v;
  }

  public static double number(Object o, double fallback) {
    try {
      double n = Double.parseDouble(String.valueOf(o));
      return Double.isFinite(n) ? n : fallback;
    } catch (Exception e) {
      return fallback;
    }
  }

  public static boolean blocked(
      boolean ad,
      boolean live,
      boolean image,
      boolean commerce,
      String text,
      String uid,
      long likes,
      long duration,
      Map<String, Object> c) {
    if (!bool(c, "enabled", true)) return false;
    if (ad && bool(c, "filter_ads", false)
        || live && bool(c, "filter_live", false)
        || image && bool(c, "filter_images", false)
        || commerce && bool(c, "filter_commerce", false)) return true;
    if (contains(text, String.valueOf(c.getOrDefault("filter_keywords", "")))
        || exact(uid, String.valueOf(c.getOrDefault("filter_uids", "")))) return true;
    if (!live && !image) {
      long min = (long) number(c.get("min_likes"), 0),
          lo = (long) number(c.get("min_duration"), 0) * 1000,
          hi = (long) number(c.get("max_duration"), 0) * 1000;
      if (likes >= 0 && min > 0 && likes < min) return true;
      if (duration >= 0 && (lo > 0 && duration < lo || hi > 0 && duration > hi)) return true;
    }
    return false;
  }

  public static boolean bool(Map<String, Object> m, String k, boolean f) {
    Object v = m.get(k);
    return v instanceof Boolean ? (Boolean) v : f;
  }
}
