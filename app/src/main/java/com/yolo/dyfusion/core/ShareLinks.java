package com.yolo.dyfusion.core;

import java.net.*;
import java.util.regex.*;

public final class ShareLinks {
  private ShareLinks() {}

  public static String extract(String s) {
    Matcher m =
        Pattern.compile("https://[^\\s<>\"“”]+", Pattern.CASE_INSENSITIVE)
            .matcher(s == null ? "" : s);
    while (m.find()) {
      String u = m.group().replaceAll("[，。！!；;）)]+$", "");
      try {
        requireOfficial(new URL(u));
        return u;
      } catch (Exception ignored) {
      }
    }
    throw new IllegalArgumentException("未找到抖音官方 HTTPS 分享链接");
  }

  public static void requireOfficial(URL url) {
    String h = url.getHost().toLowerCase(java.util.Locale.ROOT);
    if (!url.getProtocol().equals("https")
        || url.getPort() != -1 && url.getPort() != 443
        || url.getUserInfo() != null
        || !(h.equals("douyin.com")
            || h.endsWith(".douyin.com")
            || h.equals("iesdouyin.com")
            || h.endsWith(".iesdouyin.com")))
      throw new IllegalArgumentException("只接受抖音官方 HTTPS 域名");
  }

  public static String id(String s) {
    Matcher m =
        Pattern.compile("(?:/video/|/note/|/share/video/|modal_id=|aweme_id=)([0-9]{10,25})")
            .matcher(s);
    return m.find() ? m.group(1) : "";
  }

  public static String jsonObjectAt(String s, int start) {
    if (start < 0 || start >= s.length() || s.charAt(start) != '{') return "";
    int depth = 0;
    boolean str = false, escape = false;
    for (int i = start; i < s.length(); i++) {
      char c = s.charAt(i);
      if (str) {
        if (escape) escape = false;
        else if (c == '\\') escape = true;
        else if (c == '\"') str = false;
      } else {
        if (c == '\"') str = true;
        else if (c == '{') depth++;
        else if (c == '}' && --depth == 0) return s.substring(start, i + 1);
      }
    }
    return "";
  }
}
