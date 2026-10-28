package com.yolo.dyfusion.hook;

import android.app.Activity;
import android.content.*;
import android.os.*;
import com.yolo.dyfusion.config.*;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

final class RuntimeState {
  final Context context;
  final String hostVersion;
  volatile JSONObject config = new JSONObject(),
      uiStatus = new JSONObject(),
      qualityStatus = new JSONObject();
  volatile Map<String, Object> ruleConfig = Collections.emptyMap();
  volatile long configRevision;
  volatile Object current, panel, engine;
  volatile boolean paused = true, feedVisible;
  WeakReference<Object> selectedHolder = new WeakReference<>(null);
  UiController ui;
  WeakReference<Activity> activity = new WeakReference<>(null);
  final Handler main = new Handler(Looper.getMainLooper());
  final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
  final Map<String, Integer> installed = new ConcurrentHashMap<>();
  final Map<String, Long> observed = new ConcurrentHashMap<>();
  final Map<String, String> failures = new ConcurrentHashMap<>();
  volatile boolean dirty = true;
  boolean nextPending;
  long completionAt;
  String lastConfig = "";

  RuntimeState(Context c) {
    context = c;
    String v = "unknown";
    try {
      v = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
    } catch (Exception ignored) {
    }
    hostVersion = v;
    worker.scheduleWithFixedDelay(this::sync, 0, 2, TimeUnit.SECONDS);
  }

  int qualityMode() {
    String mode = s("quality_mode");
    return mode.equals("highest") ? 1 : mode.equals("direct") ? 2 : 0;
  }

  boolean on() {
    return config.optBoolean("enabled", true);
  }

  boolean b(String k) {
    return on() && config.optBoolean(k, false);
  }

  String s(String k) {
    return config.optString(k, "");
  }

  int n(String k, int d) {
    return config.optInt(k, d);
  }

  float speed() {
    return (float) Math.max(.25, Math.min(4, config.optDouble("default_speed", 1)));
  }

  void setCurrent(Object a) {
    if (a != null && a != current) {
      String oldId = Host.text(Host.read(current, "aid", "aweme_id"));
      String nextId = Host.text(Host.read(a, "aid", "aweme_id"));
      if (!nextId.isEmpty() && nextId.equals(oldId)) {
        current = a;
        return;
      }
      current = a;
      hit("current_changed");
      if (ui != null) ui.requestRefresh();
    }
  }

  void sync() {
    try {
      Bundle b = context.getContentResolver().call(SettingsProvider.URI, "settings", null, null);
      String json = b == null ? "" : b.getString("json", "");
      if (!json.isEmpty() && !lastConfig.equals(json)) {
        JSONObject next = new JSONObject(json);
        Map<String, Object> values = new HashMap<>();
        Iterator<String> it = next.keys();
        while (it.hasNext()) {
          String key = it.next();
          values.put(key, next.get(key));
        }
        config = next;
        ruleConfig = Collections.unmodifiableMap(values);
        lastConfig = json;
        configRevision++;
        if (ui != null) ui.requestRefresh();
      }
      if (dirty) {
        dirty = false;
        JSONObject report = new JSONObject();
        report.put("host", context.getPackageName());
        report.put("version", hostVersion);
        report.put("module", "抖+");
        report.put("moduleVersion", "0.3.3-preview");
        report.put("installed", new JSONObject(installed));
        report.put("observed", new JSONObject(observed));
        report.put("failures", new JSONObject(failures));
        report.put("ui", uiStatus);
        report.put("quality", qualityStatus);
        report.put("notice", "Hook、控件命中、效果验证分别记录；不包含作品正文或账号数据。");
        Bundle e = new Bundle();
        e.putString("json", report.toString());
        context.getContentResolver().call(SettingsProvider.URI, "report", null, e);
      }
    } catch (Throwable e) {
      android.util.Log.w("DyFusion", "配置桥暂不可用: " + e.getClass().getSimpleName());
    }
  }

  void update(String key, Object value) {
    worker.execute(
        () -> {
          try {
            JSONObject next = new JSONObject(config.toString());
            next.put(key, value);
            Bundle bundle = new Bundle();
            bundle.putString("json", next.toString());
            context.getContentResolver().call(SettingsProvider.URI, "update", null, bundle);
            sync();
          } catch (Throwable e) {
            fail("settings_update", e);
          }
        });
  }

  void installed(String k, int count) {
    installed.merge(k, count, Integer::sum);
    dirty = true;
  }

  void hit(String k) {
    observed.merge(k, 1L, Long::sum);
    dirty = true;
  }

  void fail(String k, Throwable e) {
    failures.put(k, e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()));
    dirty = true;
    android.util.Log.w("DyFusion", k + " " + failures.get(k));
  }

  void complete(Object player) {
    if (!on() || !feedVisible || current == null || SystemClock.uptimeMillis() - completionAt < 800)
      return;
    completionAt = SystemClock.uptimeMillis();
    hit("completion");
    if (b("pause_at_end")) {
      try {
        Host.call(player, "pause");
        paused = true;
        if (ui != null) ui.requestRefresh();
      } catch (Throwable e) {
        fail("pause_at_end", e);
      }
    } else if (b("auto_next") && !nextPending) {
      nextPending = true;
      Object expected = current;
      main.postDelayed(
          () -> {
            nextPending = false;
            if (!b("auto_next") || !feedVisible || expected != current || panel == null) return;
            try {
              int i = ((Number) Host.call(panel, "getCurrentIndex")).intValue(),
                  next = i + n("next_direction", 1);
              if (next >= 0) {
                Host.call(panel, "setCurrentItem", next, true);
                hit("auto_next");
              }
            } catch (Throwable e) {
              fail("auto_next", e);
            }
          },
          n("next_delay_ms", 0));
    }
  }

  void openMedia(Activity a, String json) {
    Intent i = new Intent();
    i.setClassName("com.yolo.dyfusion", "com.yolo.dyfusion.ui.MainActivity");
    i.putExtra("media_json", json);
    a.startActivity(i);
  }
}
