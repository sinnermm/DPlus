package com.yolo.dyfusion.config;

import android.content.*;
import com.yolo.dyfusion.core.Rules;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

public final class Catalog {
  public static final String PREFS = "settings";
  private static JSONArray entries;
  private static final Set<String> RETIRED =
      new HashSet<>(
          Arrays.asList(
              "comment_keywords",
              "comment_copy_menu",
              "clean_at",
              "comment_time",
              "comment_text_color",
              "comment_bg_color",
              "hide_comment_avatar",
              "hide_comment_like",
              "hide_comment_reply"));

  public static JSONObject migrate(JSONObject old) throws JSONException {
    JSONObject copy = new JSONObject(old.toString());
    for (String key : RETIRED) copy.remove(key);
    if (copy.has("highest_play")) {
      if (!copy.has("quality_mode"))
        copy.put("quality_mode", copy.optBoolean("highest_play") ? "highest" : "native");
      copy.remove("highest_play");
    }
    return copy;
  }

  public static synchronized JSONArray all(Context c) {
    if (entries == null)
      try (InputStream in = c.getAssets().open("features.json")) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) != -1) bytes.write(buf, 0, n);
        entries = new JSONArray(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    return entries;
  }

  public static JSONObject defaults(Context c) {
    JSONObject j = new JSONObject();
    JSONArray a = all(c);
    for (int i = 0; i < a.length(); i++) {
      JSONObject f = a.optJSONObject(i);
      try {
        j.put(f.getString("key"), f.get("default"));
      } catch (JSONException e) {
        throw new IllegalStateException(e);
      }
    }
    return j;
  }

  public static JSONObject read(Context c) {
    JSONObject d = defaults(c);
    String s = c.getSharedPreferences(PREFS, 0).getString("json", "{}");
    try {
      JSONObject original = new JSONObject(s);
      JSONObject migrated = migrate(original);
      JSONObject p = validate(c, migrated);
      if (!migrated.toString().equals(original.toString()))
        c.getSharedPreferences(PREFS, 0).edit().putString("json", migrated.toString()).apply();
      Iterator<String> it = p.keys();
      while (it.hasNext()) {
        String k = it.next();
        d.put(k, p.get(k));
      }
    } catch (Exception ignored) {
    }
    return d;
  }

  public static JSONObject validate(Context c, JSONObject input) throws JSONException {
    JSONObject output = new JSONObject();
    JSONArray a = all(c);
    Set<String> known = new HashSet<>();
    for (int i = 0; i < a.length(); i++) {
      JSONObject f = a.getJSONObject(i);
      String k = f.getString("key"), t = f.getString("type");
      known.add(k);
      if (!input.has(k)) continue;
      Object v = input.get(k);
      if (t.equals("grid")) {
        if (!(v instanceof JSONObject)) throw new IllegalArgumentException("手势设置必须是 JSON 对象");
        JSONObject grid = (JSONObject) v;
        Iterator<String> cells = grid.keys();
        while (cells.hasNext()) {
          String cell = cells.next();
          if (!cell.matches("[0-8]")) throw new IllegalArgumentException("区域必须是 0–8");
          Object actions = grid.get(cell);
          if (!(actions instanceof JSONObject)) throw new IllegalArgumentException("区域动作必须是对象");
          Iterator<String> names = ((JSONObject) actions).keys();
          while (names.hasNext()) {
            String name = names.next();
            if (!name.equals("double") && !name.equals("long"))
              throw new IllegalArgumentException("未知手势类型");
            Object action = ((JSONObject) actions).get(name);
            if (!(action instanceof String)
                || !com.yolo.dyfusion.core.GestureMath.ACTIONS.contains(action))
              throw new IllegalArgumentException("未知手势动作");
          }
        }
      }
      if (t.equals("controls")) {
        if (!(v instanceof JSONObject)) throw new IllegalArgumentException("控件规则必须为对象");
        JSONObject rules = (JSONObject) v;
        if (rules.length() > 200) throw new IllegalArgumentException("控件规则过多");
        Iterator<String> ids = rules.keys();
        while (ids.hasNext()) {
          String id = ids.next();
          if (!id.matches("[0-9]{1,10}")
              || Long.parseLong(id) <= 0
              || Long.parseLong(id) > Integer.MAX_VALUE)
            throw new IllegalArgumentException("控件 ID 不正确");
          JSONObject item = rules.optJSONObject(id);
          if (item == null) throw new IllegalArgumentException("控件规则不正确");
          Iterator<String> ks = item.keys();
          while (ks.hasNext()) {
            String field = ks.next();
            Object value = item.get(field);
            if (field.equals("hidden")) {
              if (!(value instanceof Boolean)) throw new IllegalArgumentException("hidden 必须为布尔值");
            } else if (field.equals("play_alpha") || field.equals("pause_alpha")) {
              if (!(value instanceof Number)
                  || ((Number) value).doubleValue() != ((Number) value).intValue()
                  || ((Number) value).intValue() < 0
                  || ((Number) value).intValue() > 100)
                throw new IllegalArgumentException("透明度为 0–100 的整数");
            } else throw new IllegalArgumentException("未知控件字段");
          }
        }
      }
      if (t.equals("choice")) {
        if (!(v instanceof String) || !Arrays.asList("native", "highest", "direct").contains(v))
          throw new IllegalArgumentException("未知画质模式");
      }
      if (t.equals("bool") && !(v instanceof Boolean))
        throw new IllegalArgumentException(f.optString("title") + "必须是布尔值");
      if (t.equals("int") || t.equals("number")) {
        if (!(v instanceof Number)) throw new IllegalArgumentException(k + "必须是数值");
        double n = ((Number) v).doubleValue();
        if (!Double.isFinite(n)
            || n < f.optDouble("min")
            || n > f.optDouble("max")
            || t.equals("int") && n != Math.rint(n)) throw new IllegalArgumentException(k + "超出范围");
        if (k.equals("next_direction") && n != 1 && n != -1)
          throw new IllegalArgumentException("方向只能是 1 或 -1");
        if (k.equals("dpi") && n != 0 && n < 120)
          throw new IllegalArgumentException("DPI 只能为 0 或 120–640");
        if (t.equals("int")) v = (int) n;
      }
      if (t.equals("text") || t.equals("labels")) {
        if (!(v instanceof String) || ((String) v).length() > 4096)
          throw new IllegalArgumentException(k + "文本格式不正确或过长");
        if (k.endsWith("_color")
            && !((String) v).isEmpty()
            && !((String) v).matches("#[0-9a-fA-F]{6}"))
          throw new IllegalArgumentException("颜色须为 #RRGGBB 或留空");
        if (k.equals("download_folder")) v = Rules.folder((String) v);
        if (k.equals("hide_top_tabs")
            && Rules.exact("推荐", (String) v)
            && Rules.exact("关注", (String) v)) throw new IllegalArgumentException("推荐与关注至少保留一个");
      }
      output.put(k, v);
    }
    Iterator<String> it = input.keys();
    while (it.hasNext()) {
      String k = it.next();
      if (!known.contains(k)) throw new IllegalArgumentException("未知配置项: " + k);
    }
    return output;
  }

  public static void save(Context c, JSONObject j) throws JSONException {
    JSONObject v = validate(c, migrate(j));
    if (v.optBoolean("auto_next") && v.optBoolean("pause_at_end"))
      throw new IllegalArgumentException("自动下一条与结束暂停不能同时开启");
    if (v.optInt("max_duration", 0) > 0 && v.optInt("min_duration", 0) > v.optInt("max_duration"))
      throw new IllegalArgumentException("最短时长不能大于最长时长");
    if (!c.getSharedPreferences(PREFS, 0).edit().putString("json", v.toString()).commit())
      throw new IllegalStateException("配置写入失败");
  }
}
