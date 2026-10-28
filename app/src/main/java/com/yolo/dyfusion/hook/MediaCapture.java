package com.yolo.dyfusion.hook;

import java.util.*;
import org.json.*;

final class MediaCapture {
  static JSONObject capture(Object aweme) throws JSONException {
    JSONObject out = new JSONObject();
    String id = Host.text(Host.read(aweme, "aid", "aweme_id"));
    Object author = Host.read(aweme, "author");
    out.put("id", id);
    out.put("title", Host.text(Host.read(aweme, "desc", "item_title", "title")));
    out.put("author", Host.text(Host.read(author, "nickname")));
    out.put("share", "https://www.douyin.com/video/" + id);
    JSONArray items = new JSONArray();
    Set<String> seen = new HashSet<>();
    Object video = Host.read(aweme, "video");
    List<QualitySources.Source> sources = QualitySources.list(aweme);
    int qualityIndex = 0;
    for (QualitySources.Source source : sources) {
      if (source.urls.isEmpty()) continue;
      String label = "视频 " + source.label() + (qualityIndex++ == 0 ? "（最高可用）" : "");
      add(items, seen, source.urls.get(0), label, "video/mp4", "mp4", source.bitrate);
      JSONObject item = items.optJSONObject(items.length() - 1);
      if (item != null) {
        item.put("width", source.width);
        item.put("height", source.height);
        item.put("fps", source.fps);
        item.put("size", source.size);
        item.put("best", qualityIndex == 1);
      }
    }
    add(
        items,
        seen,
        Host.firstUrl(Host.read(video, "cover", "originCover", "origin_cover")),
        "封面",
        "image/jpeg",
        "jpg",
        0);
    Object images = Host.read(aweme, "images", "image_post_info");
    if (!(images instanceof List<?>)) images = Host.read(images, "images");
    if (images instanceof List<?>) {
      int n = 0;
      for (Object image : (List<?>) images) {
        Object display =
            Host.read(image, "displayImage", "display_image", "originImage", "origin_image");
        String u = Host.firstUrl(display == null ? image : display);
        add(items, seen, u, "图片 " + (++n), "image/jpeg", "jpg", 0);
      }
    }
    Object music = Host.read(aweme, "music");
    add(
        items,
        seen,
        Host.firstUrl(Host.read(music, "playUrl", "play_url")),
        "背景音乐（不是视频完整音轨）",
        "audio/mpeg",
        "mp3",
        0);
    out.put("items", items);
    out.put("source", "宿主已加载的作品数据；清晰度由实际码率区分，不保证一定含原始上传文件。");
    return out;
  }

  private static void add(
      JSONArray a, Set<String> s, String u, String label, String mime, String ext, long rate)
      throws JSONException {
    if (!u.startsWith("https://") || !s.add(u) || a.length() >= 100) return;
    JSONObject j = new JSONObject();
    j.put("url", u);
    j.put("label", label);
    j.put("mime", mime);
    j.put("ext", ext);
    j.put("bitrate", rate);
    a.put(j);
  }
}
