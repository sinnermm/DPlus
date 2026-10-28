package com.yolo.dyfusion.media;

import android.app.DownloadManager;
import android.content.*;
import android.net.Uri;
import android.os.Environment;
import com.yolo.dyfusion.config.Catalog;
import com.yolo.dyfusion.core.Rules;
import java.net.*;
import java.util.*;
import org.json.*;

public final class Downloads {
  public static void allowed(URL u) {
    String h = u.getHost().toLowerCase(Locale.ROOT);
    boolean allowed = false;
    for (String d :
        new String[] {
          "douyin.com",
          "iesdouyin.com",
          "douyinvod.com",
          "douyinpic.com",
          "byteimg.com",
          "bytedance.com",
          "bytedance.net",
          "ibytedtos.com",
          "amemv.com",
          "snssdk.com",
          "volccdn.com"
        }) if (h.equals(d) || h.endsWith("." + d)) allowed = true;
    if (!u.getProtocol().equals("https")
        || u.getUserInfo() != null
        || u.getPort() != -1 && u.getPort() != 443
        || !allowed) throw new IllegalArgumentException("媒体地址不属于允许的官方 CDN 域名");
  }

  public static long enqueue(Context c, JSONObject media, JSONObject item, int index)
      throws Exception {
    String url = item.getString("url");
    URL u = new URL(url);
    allowed(u);
    String mime = "application/octet-stream";
    boolean resolved = false;
    for (int i = 0; i < 6; i++) {
      allowed(u);
      HttpURLConnection connection = (HttpURLConnection) u.openConnection();
      connection.setRequestMethod("HEAD");
      connection.setConnectTimeout(8000);
      connection.setReadTimeout(8000);
      connection.setInstanceFollowRedirects(false);
      connection.setRequestProperty(
          "User-Agent",
          "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/128.0 Mobile Safari/537.36");
      try {
        int status = connection.getResponseCode();
        if (status >= 300 && status < 400) {
          String loc = connection.getHeaderField("Location");
          if (loc == null) throw new java.io.IOException("下载地址重定向无目标");
          u = new URL(u, loc);
          allowed(u);
          continue;
        }
        if (status >= 200 && status < 300) {
          String type = connection.getContentType();
          if (type != null) mime = type.split(";")[0].trim().toLowerCase(Locale.ROOT);
        }
        resolved = true;
        break;
      } finally {
        connection.disconnect();
      }
    }
    if (!resolved) throw new java.io.IOException("下载地址重定向次数过多");
    if (mime.startsWith("text/") || mime.equals("application/json"))
      throw new java.io.IOException("地址返回网页或错误数据，不是媒体文件");
    String extension = extension(mime);
    JSONObject config = Catalog.read(c);
    String name =
        config
            .optString("filename_template", "{author}_{id}_{quality}")
            .replace("{author}", media.optString("author"))
            .replace("{title}", media.optString("title"))
            .replace("{id}", media.optString("id"))
            .replace("{quality}", item.optString("label"))
            .replace("{index}", String.valueOf(index));
    name = Rules.safeName(name) + "_" + System.currentTimeMillis() + "_" + index + "." + extension;
    String folder = Rules.folder(config.optString("download_folder", "DyFusion"));
    DownloadManager.Request req = new DownloadManager.Request(Uri.parse(u.toString()));
    req.setTitle(name);
    req.setMimeType(mime);
    req.setAllowedOverMetered(true);
    req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, folder + "/" + name);
    req.setNotificationVisibility(
        config.optBoolean("download_notification", true)
            ? DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            : DownloadManager.Request.VISIBILITY_VISIBLE);
    long id = ((DownloadManager) c.getSystemService(Context.DOWNLOAD_SERVICE)).enqueue(req);
    c.getSharedPreferences("runtime", 0).edit().putLong("last_download_id", id).apply();
    return id;
  }

  public static String extension(String mime) {
    switch (mime) {
      case "video/mp4":
        return "mp4";
      case "video/webm":
        return "webm";
      case "image/jpeg":
        return "jpg";
      case "image/png":
        return "png";
      case "image/webp":
        return "webp";
      case "image/gif":
        return "gif";
      case "audio/mpeg":
        return "mp3";
      case "audio/mp4":
      case "audio/x-m4a":
        return "m4a";
      case "audio/aac":
        return "aac";
      case "audio/wav":
      case "audio/x-wav":
        return "wav";
      case "audio/ogg":
        return "ogg";
      default:
        return "bin";
    }
  }
}
