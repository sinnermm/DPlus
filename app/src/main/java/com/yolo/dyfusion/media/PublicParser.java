package com.yolo.dyfusion.media;

import com.yolo.dyfusion.core.ShareLinks;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;
import org.json.*;

public final class PublicParser {
  private static final String UA =
      "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/128.0 Mobile Safari/537.36";

  public static JSONObject parse(String input) throws Exception {
    URL url = new URL(ShareLinks.extract(input));
    String body = null;
    String finalUrl = url.toString();
    for (int n = 0; n < 6; n++) {
      ShareLinks.requireOfficial(url);
      HttpURLConnection c = (HttpURLConnection) url.openConnection();
      c.setConnectTimeout(12000);
      c.setReadTimeout(12000);
      c.setInstanceFollowRedirects(false);
      c.setRequestProperty("User-Agent", UA);
      try {
        int status = c.getResponseCode();
        if (status >= 300 && status < 400) {
          String next = c.getHeaderField("Location");
          if (next == null) throw new IOException("分享链接重定向没有地址");
          url = new URL(url, next);
          ShareLinks.requireOfficial(url);
          continue;
        }
        if (status != 200) throw new IOException("分享页面 HTTP " + status);
        finalUrl = url.toString();
        try (InputStream in = c.getInputStream()) {
          body = readBounded(in, 4 * 1024 * 1024);
        }
        break;
      } finally {
        c.disconnect();
      }
    }
    if (body == null) throw new IOException("分享链接重定向次数过多");
    String expected = ShareLinks.id(finalUrl);
    List<Object> roots = new ArrayList<>();
    try {
      roots.add(new JSONObject(body));
    } catch (JSONException ignored) {
    }
    for (String marker :
        new String[] {"window._ROUTER_DATA", "window.__INITIAL_STATE__", "window._SSR_DATA"}) {
      int from = 0;
      while ((from = body.indexOf(marker, from)) >= 0) {
        int start = body.indexOf('{', from);
        String json = ShareLinks.jsonObjectAt(body, start);
        try {
          if (!json.isEmpty()) roots.add(new JSONObject(json));
        } catch (JSONException ignored) {
        }
        from += marker.length();
      }
    }
    Matcher m =
        Pattern.compile(
                "<script[^>]*id=[\"']RENDER_DATA[\"'][^>]*>(.*?)</script>",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE)
            .matcher(body);
    if (m.find())
      try {
        roots.add(new JSONObject(URLDecoder.decode(m.group(1), "UTF-8")));
      } catch (Exception ignored) {
      }
    JSONObject aweme = null;
    for (Object root : roots) {
      aweme = find(root, expected, 0, new int[] {0});
      if (aweme != null) break;
    }
    if (aweme == null)
      throw new IOException("公开分享页未提供作品媒体数据。可能需要验证、登录或页面协议已改变；可在抖音播放页使用“当前作品媒体”，不绕过访问限制。");
    JSONObject out = fromAweme(aweme);
    out.put("share", finalUrl);
    return out;
  }

  public static String readBounded(InputStream in, int max) throws IOException {
    ByteArrayOutputStream b = new ByteArrayOutputStream();
    byte[] buf = new byte[8192];
    int n;
    while ((n = in.read(buf)) != -1) {
      if (b.size() + n > max) throw new IOException("响应超过大小限制");
      b.write(buf, 0, n);
    }
    return new String(b.toByteArray(), StandardCharsets.UTF_8);
  }

  private static JSONObject find(Object node, String expected, int depth, int[] count)
      throws JSONException {
    if (depth > 24 || ++count[0] > 20000) return null;
    if (node instanceof JSONObject) {
      JSONObject j = (JSONObject) node;
      String id = j.optString("aweme_id");
      if (!id.isEmpty()
          && (expected.isEmpty() || id.equals(expected))
          && (j.has("video") || j.has("images"))) return j;
      Iterator<String> it = j.keys();
      while (it.hasNext()) {
        JSONObject got = find(j.opt(it.next()), expected, depth + 1, count);
        if (got != null) return got;
      }
    } else if (node instanceof JSONArray) {
      JSONArray a = (JSONArray) node;
      for (int i = 0; i < Math.min(2000, a.length()); i++) {
        JSONObject got = find(a.opt(i), expected, depth + 1, count);
        if (got != null) return got;
      }
    }
    return null;
  }

  public static JSONObject fromAweme(JSONObject a) throws JSONException {
    JSONObject out = new JSONObject();
    out.put("id", a.optString("aweme_id"));
    out.put("title", a.optString("desc", a.optString("title")));
    JSONObject author = a.optJSONObject("author");
    out.put("author", author == null ? "" : author.optString("nickname"));
    JSONArray items = new JSONArray();
    Set<String> seen = new HashSet<>();
    JSONObject video = a.optJSONObject("video");
    if (video != null) {
      JSONArray rates = video.optJSONArray("bit_rate");
      if (rates != null)
        for (int i = 0; i < rates.length(); i++) {
          JSONObject rate = rates.optJSONObject(i);
          if (rate != null)
            add(
                items,
                seen,
                first(rate.optJSONObject("play_addr")),
                "视频 " + rate.optLong("bit_rate") + " bps",
                rate.optLong("bit_rate"));
        }
      for (String key : new String[] {"play_addr", "play_addr_h264", "play_addr_bytevc1"})
        add(items, seen, first(video.optJSONObject(key)), "视频播放流", 0);
      add(items, seen, first(video.optJSONObject("origin_cover")), "封面", 0);
      add(items, seen, first(video.optJSONObject("cover")), "封面", 0);
    }
    JSONArray images = a.optJSONArray("images");
    if (images != null)
      for (int i = 0; i < images.length(); i++) {
        JSONObject image = images.optJSONObject(i);
        if (image != null) add(items, seen, first(image), "图片 " + (i + 1), 0);
      }
    JSONObject music = a.optJSONObject("music");
    if (music != null)
      add(items, seen, first(music.optJSONObject("play_url")), "背景音乐（不是视频完整音轨）", 0);
    if (items.length() == 0) throw new JSONException("没有公开 HTTPS 媒体地址");
    out.put("items", items);
    out.put("source", "公开分享页实际返回的媒体地址；未伪造清晰度选项，地址可能过期。");
    return out;
  }

  private static String first(JSONObject j) {
    if (j == null) return "";
    JSONArray a = j.optJSONArray("url_list");
    if (a != null)
      for (int i = 0; i < a.length(); i++)
        if (a.optString(i).startsWith("https://")) return a.optString(i);
    return "";
  }

  private static void add(JSONArray a, Set<String> seen, String url, String label, long rate)
      throws JSONException {
    if (!url.startsWith("https://") || !seen.add(url)) return;
    JSONObject item = new JSONObject();
    item.put("url", url);
    item.put("label", label);
    item.put("bitrate", rate);
    item.put("mime", "application/octet-stream");
    item.put("ext", "bin");
    a.put(item);
  }
}
