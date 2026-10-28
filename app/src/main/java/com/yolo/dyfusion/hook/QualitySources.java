package com.yolo.dyfusion.hook;

import com.yolo.dyfusion.core.QualityRank;
import java.util.*;
import org.json.*;

final class QualitySources {
  static final class Source {
    final Object rate, address;
    final long width, height, fps, bitrate, size;
    final List<String> urls;
    final String format;

    Source(Object r, Object a) {
      rate = r;
      address = a;
      urls = Host.urls(a);
      width =
          Host.number(Host.read(a, "width"), Host.number(Host.read(r, "videoWidth", "width"), 0));
      height =
          Host.number(
              Host.read(a, "height"), Host.number(Host.read(r, "videoHeight", "height"), 0));
      fps = Host.number(Host.read(r, "FPS", "fps", "fPS"), 0);
      bitrate = Host.number(Host.read(r, "bitRate", "bit_rate"), 0);
      size =
          Host.number(
              Host.read(a, "dataSize", "data_size", "size"), Host.number(Host.read(r, "size"), 0));
      format = Host.text(Host.read(r, "format", "gearName", "gear_name"));
    }

    String label() {
      String dimensions =
          width > 0 && height > 0 ? width + "x" + height : bitrate > 0 ? bitrate + " bps" : "实际播放流";
      return dimensions + (fps > 0 ? " @" + fps : "");
    }
  }

  static List<Source> list(Object aweme) {
    Object video = Host.read(aweme, "video");
    List<Source> out = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    Object rates = Host.read(video, "bit_rate", "bitRate", "bitRateList");
    if (rates instanceof List<?>)
      for (Object r : (List<?>) rates) {
        Object address = Host.read(r, "play_addr", "playAddr");
        Source s = new Source(r, address);
        if (!s.urls.isEmpty() && seen.add(s.urls.get(0))) out.add(s);
      }
    for (String key : new String[] {"play_addr", "play_addr_h264", "play_addr_bytevc1"}) {
      Source s = new Source(null, Host.read(video, key));
      if (!s.urls.isEmpty() && seen.add(s.urls.get(0))) out.add(s);
    }
    out.sort(
        (a, b) ->
            -QualityRank.compare(
                a.width, a.height, a.fps, a.bitrate, a.size, b.width, b.height, b.fps, b.bitrate,
                b.size));
    return out;
  }

  private static final Map<Object, Map<String, Object>> original =
      Collections.synchronizedMap(new WeakHashMap<>());

  static void apply(RuntimeState r, Object aweme) {
    Object video = Host.read(aweme, "video");
    if (video == null) {
      skip(r, "no_video", 0);
      return;
    }
    if (!r.on() || r.qualityMode() == 0) {
      restore(video);
      return;
    }
    if (Host.yes(Host.read(video, "is_drm_source", "isDrmSource"))) {
      skip(r, "protected_drm", 0);
      return;
    }
    try {
      if (original.containsKey(video)) restore(video);
      List<Source> sources = list(aweme);
      if (sources.isEmpty()) {
        skip(r, "no_official_media_address", 0);
        return;
      }
      Source best = sources.get(0);
      Object defaultAddress = Host.read(video, "play_addr");
      if (r.qualityMode() == 2) {
        List<String> direct = best.urls;
        List<String> progressive = new ArrayList<>();
        for (String url : direct)
          if (!url.toLowerCase(Locale.ROOT).contains(".m3u8")
              && !url.toLowerCase(Locale.ROOT).contains(".mpd")) progressive.add(url);
        if (progressive.isEmpty()) {
          r.fail("highest_play", new IllegalStateException("最高档没有完整文件直链，不替换播放"));
          return;
        }
      }
      if (defaultAddress == null) {
        skip(r, "no_primary_player_address", sources.size());
        return;
      }
      Map<String, Object> fields =
          original.computeIfAbsent(
              video,
              v -> {
                Map<String, Object> save = new HashMap<>();
                save.put("play_addr", Host.read(v, "play_addr"));
                save.put("play_addr_h264", Host.read(v, "play_addr_h264"));
                save.put("play_addr_265", Host.read(v, "play_addr_265"));
                save.put("bit_rate", Host.read(v, "bit_rate"));
                return save;
              });
      Object replacement = defaultAddress.getClass().getDeclaredConstructor().newInstance();
      for (Class<?> c = defaultAddress.getClass(); c != null; c = c.getSuperclass())
        for (java.lang.reflect.Field f : c.getDeclaredFields())
          if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())
              && !java.lang.reflect.Modifier.isFinal(f.getModifiers())) {
            f.setAccessible(true);
            f.set(replacement, f.get(defaultAddress));
          }
      List<String> selectedUrls = new ArrayList<>(best.urls);
      if (r.qualityMode() == 2)
        selectedUrls.removeIf(
            url ->
                url.toLowerCase(Locale.ROOT).contains(".m3u8")
                    || url.toLowerCase(Locale.ROOT).contains(".mpd"));
      Host.write(replacement, selectedUrls, "urlList", "url_list");
      try {
        Host.write(replacement, null, "ratioUri");
      } catch (Throwable ignored) {
      }
      for (String dimension : new String[] {"width", "height", "size", "dataSize"})
        try {
          Object val = Host.read(best.address, dimension);
          if (val != null) Host.write(replacement, val, dimension);
        } catch (Throwable ignored) {
        }
      Object uri = Host.read(best.address, "uri");
      if (uri != null) Host.write(replacement, uri, "uri");
      try {
        Host.write(
            replacement,
            best.address == null ? 0L : Host.number(Host.read(best.address, "size"), 0),
            "size");
      } catch (Throwable ignored) {
      }
      if (best.rate != null) {
        try {
          Host.write(
              replacement,
              new ArrayList<>(Collections.singletonList(best.rate)),
              "bitRate",
              "bit_rate");
        } catch (Throwable ignored) {
        }
        Host.write(video, new ArrayList<>(Collections.singletonList(best.rate)), "bit_rate");
      }
      Host.write(video, replacement, "play_addr");
      r.hit("highest_play");
      JSONObject q = new JSONObject();
      q.put("mode", r.qualityMode() == 2 ? "highest_direct_url" : "highest_available");
      q.put("dimensions", best.width + "x" + best.height);
      q.put("fps", best.fps);
      q.put("bitrate", best.bitrate);
      q.put("candidates", sources.size());
      q.put("warning", "最高可用不等于原始上传文件");
      r.qualityStatus = q;
      r.dirty = true;
    } catch (Throwable e) {
      restore(video);
      r.fail("highest_play", e);
    }
  }

  static void skip(RuntimeState r, String reason, int candidates) {
    if (!r.on() || r.qualityMode() == 0) return;
    try {
      JSONObject state = new JSONObject();
      state.put("mode", r.s("quality_mode"));
      state.put("skipReason", reason);
      state.put("candidates", candidates);
      r.qualityStatus = state;
      r.dirty = true;
    } catch (JSONException ignored) {
    }
  }

  static void restore(Object video) {
    Map<String, Object> save = original.remove(video);
    if (save != null)
      for (Map.Entry<String, Object> e : save.entrySet())
        try {
          Host.write(video, e.getValue(), e.getKey());
        } catch (Throwable ignored) {
        }
  }
}
