package com.yolo.dyfusion.hook;

import com.yolo.dyfusion.core.Rules;
import de.robv.android.xposed.*;
import java.lang.reflect.*;
import java.util.*;
import org.json.*;

final class HookInstaller {
  final RuntimeState r;
  final ClassLoader cl;

  HookInstaller(RuntimeState state, ClassLoader loader) {
    r = state;
    cl = loader;
  }

  interface Action {
    void run(XC_MethodHook.MethodHookParam p) throws Throwable;
  }

  void methods(String cls, String name, String key, boolean before, Action action) {
    try {
      Class<?> c = Class.forName(cls, false, cl);
      int count = 0;
      for (Method m : c.getDeclaredMethods())
        if (m.getName().equals(name)) {
          m.setAccessible(true);
          XposedBridge.hookMethod(
              m,
              new XC_MethodHook() {
                void safe(MethodHookParam p) {
                  try {
                    action.run(p);
                  } catch (Throwable e) {
                    r.fail(key, e);
                  }
                }

                @Override
                protected void beforeHookedMethod(MethodHookParam p) {
                  if (before) safe(p);
                }

                @Override
                protected void afterHookedMethod(MethodHookParam p) {
                  if (!before) safe(p);
                }
              });
          count++;
        }
      if (count == 0) throw new NoSuchMethodException(name);
      r.installed(key, count);
    } catch (Throwable e) {
      r.fail(key, e);
    }
  }

  void install() {
    methods(
        "com.ss.android.ugc.aweme.feed.adapter.VideoViewHolder",
        "onViewHolderSelected",
        "feed_holder",
        false,
        p -> {
          r.selectedHolder = new java.lang.ref.WeakReference<>(p.thisObject);
          Object a = Host.call(p.thisObject, "getAweme");
          if (a != null) r.setCurrent(a);
          r.hit("feed_holder");
          if (r.ui != null) r.ui.requestScan();
        });
    methods(
        "com.ss.android.ugc.aweme.feed.adapter.VideoViewHolder",
        "onViewHolderUnSelected",
        "feed_holder_release",
        false,
        p -> {
          if (r.selectedHolder.get() == p.thisObject) {
            r.selectedHolder.clear();
            if (r.ui != null) r.ui.requestScan();
          }
        });
    methods(
        "com.ss.android.ugc.aweme.feed.model.FeedItemList",
        "getItems",
        "feed_filter",
        false,
        p -> {
          if (!activeFilters() || !(p.getResult() instanceof List<?>)) return;
          List<?> original = (List<?>) p.getResult();
          List<Object> kept = new ArrayList<>();
          for (Object a : original) if (!blocked(a)) kept.add(a);
          if (kept.size() != original.size()) {
            p.setResult(kept);
            r.hit("feed_filter");
          }
        });
    methods(
        "com.ss.android.ugc.aweme.feed.panel.BaseListFragmentPanel",
        "getCurrentAweme",
        "current_media",
        false,
        p -> {
          Object result = p.getResult();
          if (r.selectedHolder.get() == null || r.current == null) {
            r.panel = p.thisObject;
            r.setCurrent(result);
          } else if (result == r.current) r.panel = p.thisObject;
          // Do not let preloading or offscreen panel getters replace the selected holder's work.

        });
    methods(
        "com.ss.android.ugc.aweme.feed.panel.BaseListFragmentPanel",
        "onVideoPlayerEvent",
        "player_events",
        false,
        p -> {
          r.panel = p.thisObject;
          // getCurrentAweme and holder-selection hooks already maintain current; do not repeat
          // reflection per player event.
        });
    methods(
        "com.ss.android.ugc.aweme.feed.plato.core.FeedComponentGroup",
        "interceptDoubleClick",
        "double_tap",
        true,
        p -> {
          if (r.b("disable_double_like") && r.feedVisible) {
            p.setResult(true);
            r.hit("double_tap");
          }
        });
    methods(
        "com.ss.ttm.player.PlaybackParams",
        "setSpeed",
        "speed",
        true,
        p -> {
          if (r.b("force_speed") && r.feedVisible && p.args.length == 1) {
            p.args[0] = r.speed();
            r.hit("speed");
          }
        });
    methods(
        "com.ss.ttvideoengine.TTVideoEngine",
        "play",
        "playback",
        false,
        p -> {
          if (r.current == null) return;
          r.engine = p.thisObject;
          r.paused = false;
          if (r.ui != null) r.ui.requestRefresh();
          if (r.b("force_speed")) setSpeed(p.thisObject);
          r.hit("playback");
        });
    methods(
        "com.ss.ttvideoengine.TTVideoEngine",
        "pause",
        "pause",
        false,
        p -> {
          if (r.feedVisible && r.engine == p.thisObject) {
            r.paused = true;
            if (r.ui != null) r.ui.requestRefresh();
            r.hit("pause");
          }
        });
    methods(
        "com.ss.ttvideoengine.TTVideoEngine",
        "setLooping",
        "completion_loop",
        true,
        p -> {
          if (r.feedVisible && (r.b("pause_at_end") || r.b("auto_next"))) p.args[0] = false;
        });
    methods(
        "com.ss.ttvideoengine.TTVideoEngine",
        "setListener",
        "completion_listener",
        true,
        p -> {
          if (p.args.length != 1 || p.args[0] == null) return;
          Object delegate = p.args[0];
          Class<?> iface = ((Method) p.method).getParameterTypes()[0];
          if (!iface.isInterface()) return;
          Object player = p.thisObject;
          p.args[0] =
              Proxy.newProxyInstance(
                  cl,
                  new Class<?>[] {iface},
                  (proxy, m, args) -> {
                    Object result;
                    try {
                      result = m.invoke(delegate, args);
                    } catch (InvocationTargetException e) {
                      throw e.getCause();
                    }
                    if (m.getName().equals("onCompletion")) {
                      try {
                        r.main.post(() -> r.complete(player));
                      } catch (Throwable e) {
                        r.fail("completion", e);
                      }
                    }
                    return result;
                  });
        });
    methods(
        "com.ss.android.ugc.aweme.feed.adapter.VideoViewHolder",
        "bind",
        "highest_play_bind",
        true,
        p -> {
          if (p.args.length > 0) QualitySources.apply(r, p.args[0]);
        });
    r.worker.schedule(
        () ->
            r.main.post(
                () -> {
                  if (r.n("dpi", 0) > 0 && r.on()) {
                    android.util.DisplayMetrics d = r.context.getResources().getDisplayMetrics();
                    d.densityDpi = r.n("dpi", 0);
                    d.density = d.densityDpi / 160f;
                    d.scaledDensity = d.density;
                    r.installed("dpi", 1);
                  }
                }),
        3,
        java.util.concurrent.TimeUnit.SECONDS);
  }

  void setSpeed(Object engine) {
    try {
      Class<?> params = Class.forName("com.ss.ttm.player.PlaybackParams", false, cl);
      Object p = params.getDeclaredConstructor().newInstance();
      Host.call(p, "setSpeed", r.speed());
      Host.call(engine, "setPlaybackParams", p);
    } catch (Throwable e) {
      r.fail("speed", e);
    }
  }

  boolean activeFilters() {
    return r.on()
        && (r.b("filter_ads")
            || r.b("filter_live")
            || r.b("filter_images")
            || r.b("filter_commerce")
            || !r.s("filter_keywords").isEmpty()
            || !r.s("filter_uids").isEmpty()
            || r.n("min_likes", 0) > 0
            || r.n("min_duration", 0) > 0
            || r.n("max_duration", 0) > 0);
  }

  boolean blocked(Object a) throws JSONException {
    Object video = Host.read(a, "video"),
        stat = Host.read(a, "statistics"),
        user = Host.read(a, "author");
    long type = Host.number(Host.read(a, "awemeType", "aweme_type"), -1);
    boolean ad =
        Host.yes(Host.read(a, "isAd", "is_ad", "isAds", "is_ads"))
            || Host.read(a, "awemeRawAd", "aweme_raw_ad") != null;
    boolean live = type == 101;
    boolean image =
        type == 68
            || Host.read(a, "imagePostInfo", "image_post_info") != null
            || Host.read(a, "images") instanceof List<?>
                && !((List<?>) Host.read(a, "images")).isEmpty();
    Object promotion = Host.read(a, "promotion", "promotions");
    boolean commerce =
        promotion != null
            && (!(promotion instanceof java.util.Collection<?>)
                || !((java.util.Collection<?>) promotion).isEmpty());
    Map<String, Object> config = r.ruleConfig;
    return Rules.blocked(
            ad,
            live,
            image,
            commerce,
            Host.text(Host.read(a, "desc", "item_title", "title")),
            Host.text(Host.read(user, "uid")),
            Host.number(Host.read(stat, "diggCount", "digg_count"), -1),
            Host.number(Host.read(video, "duration"), -1),
            config)
        || r.on()
            && Rules.exact(Host.text(Host.read(user, "secUid", "sec_uid")), r.s("filter_uids"));
  }
}
