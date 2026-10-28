package com.yolo.dyfusion.hook;

import android.app.*;
import android.content.*;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;
import de.robv.android.xposed.*;
import java.lang.ref.WeakReference;
import java.util.*;
import org.json.*;

final class UiController implements Application.ActivityLifecycleCallbacks {
  final RuntimeState r;
  final Application app;
  final UiEdits edits = new UiEdits();
  final TabRows tabs = new TabRows();
  final WeakHashMap<Activity, TextView> buttons = new WeakHashMap<>();
  final WeakHashMap<Window, Bars> windows = new WeakHashMap<>();
  final Map<String, Integer> matches = new LinkedHashMap<>();
  final IdentityHashMap<View, String> nodes = new IdentityHashMap<>();
  final IdentityHashMap<View, String> navigation = new IdentityHashMap<>();
  final GestureController gestures;
  WeakReference<View> observedRoot = new WeakReference<>(null);
  ViewTreeObserver.OnGlobalLayoutListener layoutListener;
  int lastFeedChildren = -1;
  Object lastScannedWork;
  boolean running, refreshQueued, needsScan = true, clean, layoutScanQueued;
  View activeFeed, playerView;
  long volumePlus, revision = -1, lastScan, scanDurationUs;
  int scans, nodeCount, frames, barsWrites;
  float lastSpeed = -1;
  JSONObject lastStatus = new JSONObject(), playerGeometry = new JSONObject();

  static final class Bars {
    final int flags, status, nav, visibility;
    int appliedVisibility = Integer.MIN_VALUE;

    Bars(Window w) {
      flags = w.getAttributes().flags;
      status = w.getStatusBarColor();
      nav = w.getNavigationBarColor();
      visibility = w.getDecorView().getSystemUiVisibility();
    }
  }

  UiController(RuntimeState state, Application a) {
    r = state;
    app = a;
    r.ui = this;
    gestures = new GestureController(r, this);
  }

  void install() {
    app.registerActivityLifecycleCallbacks(this);
    gestures.install();
    new ShareEntry(r).install();
    new PanelMenus(r).install();
    r.installed("scoped_ui", 1);
    XposedHelpers.findAndHookMethod(
        Activity.class,
        "dispatchKeyEvent",
        KeyEvent.class,
        new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(MethodHookParam p) {
            KeyEvent e = (KeyEvent) p.args[0];
            if (!r.b("volume_menu")
                || !r.feedVisible
                || r.activity.get() != p.thisObject
                || e.getAction() != KeyEvent.ACTION_DOWN
                || e.getRepeatCount() != 0) return;
            if (e.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP)
              volumePlus = SystemClock.uptimeMillis();
            else if (e.getKeyCode() == KeyEvent.KEYCODE_VOLUME_DOWN
                && SystemClock.uptimeMillis() - volumePlus < 900) {
              volumePlus = 0;
              menu((Activity) p.thisObject);
            }
          }
        });
  }

  final Runnable heartbeat =
      new Runnable() {
        public void run() {
          if (!running) return;
          refresh(false);
          r.main.postDelayed(this, 1400);
        }
      };

  void requestRefresh() {
    if (refreshQueued) return;
    refreshQueued = true;
    r.main.postDelayed(
        () -> {
          refreshQueued = false;
          if (running) refresh(false);
        },
        45);
  }

  void requestScan() {
    needsScan = true;
    requestRefresh();
  }

  View selectedRoot(View decor) {
    Object holder = r.selectedHolder.get();
    if (holder == null && r.panel != null)
      try {
        holder = Host.call(r.panel, "getCurrentViewHolderP");
      } catch (Throwable ignored) {
      }
    if (holder != null) {
      Object root = Host.read(holder, "mRootView", "w", "mQuickRoot", "z");
      if (root instanceof View) {
        View v = (View) root;
        if (v.isAttachedToWindow()
            && v.getRootView() == decor.getRootView()
            && v.getWidth() > 0
            && v.getGlobalVisibleRect(new Rect())) return v;
      }
    }
    return null;
  }

  boolean marker(View v, int depth) {
    if (depth > 65 || v.getVisibility() != View.VISIBLE) return false;
    if (UiProfile.named(v, "LongPressLayout", "FeedRightScaleView", "VideoPinchViewContainer"))
      return true;
    if (v instanceof ViewGroup)
      for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++)
        if (marker(((ViewGroup) v).getChildAt(i), depth + 1)) return true;
    return false;
  }

  void refresh(boolean force) {
    Activity a = r.activity.get();
    if (a == null || a.isFinishing()) return;
    try {
      View decor = a.getWindow().getDecorView();
      View feed = selectedRoot(decor);
      boolean changedRoot = feed != activeFeed;
      if (changedRoot) {
        r.main.postDelayed(
            () -> {
              if (running) {
                needsScan = true;
                requestRefresh();
              }
            },
            700);
        activeFeed = feed;
        needsScan = true;
        playerView = null;
      }
      r.feedVisible = r.current != null && (feed != null || nodes.isEmpty() && marker(decor, 0));
      if (!r.on() || !r.feedVisible) {
        edits.restore();
        tabs.restore();
        restoreWindow(a.getWindow());
        removeButton(a);
        unobserve();
        nodes.clear();
        playerView = null;
        lastSpeed = -1;
        publish(a, 0);
        return;
      }
      observe(decor);
      if (revision != r.configRevision) {
        revision = r.configRevision;
        needsScan = true;
        tabs.restore();
      }
      if (needsScan || force) {
        needsScan = false;
        long start = System.nanoTime();
        scan(decor);
        scanDurationUs = (System.nanoTime() - start) / 1000;
        lastScan = SystemClock.uptimeMillis();
        scans++;
      }
      applyCached(decor);
      bars(a.getWindow());
      if (r.b("floating_menu")) button(a);
      else removeButton(a);
      if (r.b("force_speed") && r.engine != null && lastSpeed != r.speed()) {
        lastSpeed = r.speed();
        new HookInstaller(r, a.getClassLoader()).setSpeed(r.engine);
      }
      publish(a, edits.size());
    } catch (Throwable e) {
      r.fail("scoped_ui", e);
    }
  }

  boolean aDetail() {
    Activity a = r.activity.get();
    return a != null && a.getClass().getName().contains("Detail");
  }

  @android.annotation.SuppressLint("ResourceType")
  void scan(View decor) {
    nodes.clear();
    matches.clear();
    nodeCount = 0;
    scanNode(activeFeed == null ? decor : activeFeed, "", 0);
    if (activeFeed != null) {
      navigation.clear();
      View top = decor.findViewById(2131372171);
      if (aDetail()) {
        for (int id : new int[] {2131388617, 2131366243, 2131396192}) {
          View control = decor.findViewById(id);
          if (control != null)
            bind(
                control,
                "target:"
                    + (id == 2131388617
                        ? "detail_search"
                        : id == 2131366243 ? "detail_back" : "comment_input"));
        }
      }
      if (top != null) scanNav(top, 0);
      View skin = decor.findViewById(2131366986);
      if (skin != null) {
        View branch = skin;
        for (int i = 0; i < 4 && branch.getParent() instanceof View; i++) {
          View parent = (View) branch.getParent();
          if (parent.getHeight() > decor.getHeight() * .25f) break;
          branch = parent;
        }
        scanNav(branch, 0);
      }
    }
    playerView = findPlayer();
    lastFeedChildren =
        activeFeed instanceof ViewGroup ? ((ViewGroup) activeFeed).getChildCount() : -1;
    lastScannedWork = r.current;
  }

  void scanNode(View v, String area, int depth) {
    if (depth > 75 || ++nodeCount > 6000 || "DyFusionButton".equals(v.getTag())) return;
    // Inspect descendant IDs even if a parent was made transparent/invisible by another component.
    // Edits still preserve each target original state and remain scoped to the selected holder.
    String target = UiProfile.target(v, r.hostVersion),
        own = UiProfile.region(v, target, r.hostVersion);
    if (!target.isEmpty()) bind(v, "target:" + target);
    if (!own.isEmpty()) bind(v, "area:" + own);
    if (v instanceof TextView) {
      String text = ((TextView) v).getText().toString();
      if (text.startsWith("相关搜索") || text.startsWith("搜索：") || text.startsWith("搜索:")) {
        View c = infoContainer(v);
        bind(c, "info:search");
      }
      if (text.trim().equals("拍同款")) {
        ViewParent parent = v.getParent();
        if (parent instanceof View) {
          View turntable = (View) parent;
          if (turntable.getWidth() < v.getRootView().getWidth() * .35f
              && turntable.getHeight() < v.getRootView().getHeight() * .25f)
            bind(turntable, "info:template");
        }
      }
      if (text.trim().equals("合集") || text.startsWith("合集·") || text.startsWith("合集：")) {
        View c = infoContainer(v);
        bind(c, "info:mix");
      }
    }
    if (v instanceof TextView && !UiProfile.entry(v).equals("desc")) {
      String label = ((TextView) v).getText().toString().trim();
      String extra =
          label.equals("全屏观看")
              ? "fullscreen"
              : label.equals("附近吃喝玩乐")
                  ? "nearby"
                  : label.equals("我的朋友") ? "friends" : label.equals("直播发现") ? "live_discovery" : "";
      if (!extra.isEmpty()) bind(smallItem(v), "target:" + extra);
    }
    if (activeFeed != null
        && v != activeFeed
        && v.getId() != View.NO_ID
        && v.getId() != 0
        && !UiProfile.named(
            v, "SurfaceView", "TextureView", "VideoPinchViewContainer", "LongPressLayout")
        && v.getHeight() < v.getRootView().getHeight() * .45f) {
      JSONObject controls = r.config.optJSONObject("custom_controls");
      if (controls != null && controls.has(String.valueOf(v.getId())))
        bind(v, "custom:" + v.getId());
    }
    if (v instanceof ViewGroup) {
      ViewGroup g = (ViewGroup) v;
      for (int n = 0; n < g.getChildCount(); n++)
        scanNode(g.getChildAt(n), own.isEmpty() ? area : own, depth + 1);
    }
  }

  View smallItem(View v) {
    View best = v;
    for (ViewParent p = v.getParent(); p instanceof View; p = p.getParent()) {
      View c = (View) p;
      if (c == activeFeed || c.getHeight() > v.getRootView().getHeight() * .20f) break;
      best = c;
      if (c.isClickable()) break;
    }
    return best;
  }

  @android.annotation.SuppressLint("ResourceType")
  View infoContainer(View v) {
    View best = v;
    for (ViewParent p = v.getParent(); p instanceof View; p = p.getParent()) {
      View candidate = (View) p;
      if (candidate == activeFeed || candidate.getHeight() > v.getRootView().getHeight() * .20f)
        break;
      if (candidate.getWidth() > v.getRootView().getWidth() * .65f) best = candidate;
      if (r.hostVersion.equals("40.7.0") && candidate.getId() == 2131367344) return candidate;
    }
    return best;
  }

  void bind(View v, String role) {
    String old = nodes.get(v);
    if (old == null) nodes.put(v, role);
    else if (!Arrays.asList(old.split(";")).contains(role)) nodes.put(v, old + ";" + role);
  }

  void cacheNav(View v, String role) {
    String old = navigation.get(v);
    if (old == null) navigation.put(v, role);
    else if (!Arrays.asList(old.split(";")).contains(role)) navigation.put(v, old + ";" + role);
  }

  void scanNav(View v, int depth) {
    if (depth > 70 || "DyFusionButton".equals(v.getTag())) return;
    String region = UiProfile.region(v, "", r.hostVersion),
        row = UiProfile.tabRow(v, r.hostVersion);
    if (region.equals("top") || region.equals("bottom")) {
      bind(v, "area:" + region);
      cacheNav(v, "area:" + region);
    }
    if (r.b("hide_detail_search")
        && r.activity.get() != null
        && r.activity.get().getClass().getName().contains("Detail")) {
      CharSequence description = v.getContentDescription();
      if (description != null
          && description.toString().startsWith("搜索")
          && v.getHeight() < v.getRootView().getHeight() * .2f) bind(v, "target:detail_search");
    }
    if (!row.isEmpty()) {
      bind(v, "row:" + row);
      cacheNav(v, "row:" + row);
    }
    if (v instanceof ViewGroup) {
      ViewGroup g = (ViewGroup) v;
      for (int n = 0; n < g.getChildCount(); n++) scanNav(g.getChildAt(n), depth + 1);
    }
  }

  View findPlayer() {
    Object holder = r.selectedHolder.get();
    if (holder != null) {
      Object p = Host.read(holder, "e", "baseFeedPlayerView");
      try {
        Object v = Host.call(p, "getVideoView");
        if (v instanceof View) return (View) v;
      } catch (Throwable ignored) {
      }
      Object v = Host.read(holder, "x", "mPlayerContainer");
      if (v instanceof View) return (View) v;
    }
    return null;
  }

  void applyCached(View decor) {
    float previousOffset = playerView == null ? 0 : edits.appliedOffset(playerView);
    float baselineScale = playerView == null ? 1 : edits.originalScaleY(playerView);
    edits.begin();
    matches.clear();
    Set<View> seenAreas = Collections.newSetFromMap(new IdentityHashMap<>());
    for (Map.Entry<View, String> e : nodes.entrySet()) {
      View v = e.getKey();
      if (!v.isAttachedToWindow()) continue;
      for (String role : e.getValue().split(";")) {
        String key = role.substring(role.indexOf(':') + 1);
        if (role.startsWith("row:")) {
          if (v instanceof ViewGroup) {
            int hidden =
                tabs.apply(
                    (ViewGroup) v, r.s(key.equals("top") ? "hide_top_tabs" : "hide_bottom_tabs"));
            if (hidden > 0) matches.put("removed_" + key + "_tabs", hidden);
          }
          continue;
        }
        matches.merge(key, 1, Integer::sum);
        if (role.startsWith("area:")) {
          if (key.equals("top") && r.b("hide_top_bar")
              || key.equals("bottom") && r.b("hide_bottom_bar")
              || clean) edits.hide(v);
          if (!parentArea(v, key, seenAreas)) {
            edits.alpha(
                v,
                r.n((r.paused ? "pause" : "play") + "_alpha_" + key, 100),
                r.config.optBoolean("zero_unclickable", true));
            seenAreas.add(v);
          }
        }
        if (role.startsWith("target:")) {
          if (r.b("hide_feed_" + key)
              || (key.equals("detail_search") && r.b("hide_detail_search")
                  || key.equals("detail_back") && r.b("hide_detail_back"))
              || key.equals("comment_input") && r.b("hide_comment_input_bar")
              || key.equals("listen_disc") && r.b("hide_listen_disc")
              || key.equals("music") && r.b("hide_bottom_bar")
              || clean) edits.hide(v);
          if (key.equals("music"))
            edits.alpha(
                v,
                r.n((r.paused ? "pause" : "play") + "_alpha_music", 100),
                r.config.optBoolean("zero_unclickable", true));
          if (key.equals("rings") && r.b("block_ring_click")) edits.disable(v);
        }
        if (role.startsWith("custom:")) {
          JSONObject rules = r.config.optJSONObject("custom_controls"),
              entry = rules == null ? null : rules.optJSONObject(key);
          if (entry != null) {
            if (entry.optBoolean("hidden")) edits.hide(v);
            edits.alpha(
                v,
                entry.optInt(r.paused ? "pause_alpha" : "play_alpha", 100),
                r.config.optBoolean("zero_unclickable", true));
          }
        }
        if (role.startsWith("info:")) {
          edits.alpha(
              v,
              r.n((r.paused ? "pause" : "play") + "_alpha_" + key, 100),
              r.config.optBoolean("zero_unclickable", true));
          if (clean
              || key.equals("search") && r.b("hide_related_search")
              || key.equals("template") && r.b("hide_bottom_bar")
              || key.equals("mix") && r.b("hide_mix_bar")) edits.hide(v);
        }
      }
    }
    if (playerView != null && playerView.isAttachedToWindow()) {
      if (r.b("fit_x") || r.b("fit_y")) {
        float ratio = 1;
        if (r.b("fit_x") && playerView.getWidth() > 0)
          ratio = Math.min(ratio, decor.getWidth() / (float) playerView.getWidth());
        if (r.b("fit_y") && playerView.getHeight() > 0)
          ratio = Math.min(ratio, decor.getHeight() / (float) playerView.getHeight());
        if (ratio < 1) edits.scale(playerView, ratio);
      }
      float micro = r.n("video_offset_dp", 0) * decor.getResources().getDisplayMetrics().density;
      int[] screen = new int[2], location = new int[2];
      decor.getLocationOnScreen(screen);
      playerView.getLocationOnScreen(location);
      float baselineTop = location[1] - previousOffset;
      float shift = micro;
      if (r.b("video_center"))
        shift =
            com.yolo.dyfusion.core.VideoPlacement.offset(
                screen[1],
                decor.getHeight(),
                baselineTop,
                playerView.getHeight(),
                playerView.getScaleY(),
                micro);
      if (Math.abs(shift) > .1f) edits.moveY(playerView, shift);
      try {
        JSONObject geometry = new JSONObject();
        geometry.put("layer", playerView.getClass().getName());
        geometry.put("width", playerView.getWidth());
        geometry.put("height", playerView.getHeight());
        geometry.put("scaleY", playerView.getScaleY());
        geometry.put("baselineTop", baselineTop);
        geometry.put("offsetPx", shift);
        geometry.put(
            "visibleCenterY",
            baselineTop + playerView.getHeight() * playerView.getScaleY() / 2f + shift);
        geometry.put("screenCenterY", screen[1] + decor.getHeight() / 2f);
        playerGeometry = geometry;
      } catch (JSONException ignored) {
      }
    }
    edits.finish();
    tabs.enforce();
  }

  boolean parentArea(View v, String key, Set<View> applied) {
    for (ViewParent p = v.getParent(); p instanceof View; p = p.getParent())
      if (applied.contains(p)
          || nodes.get(p) != null && Arrays.asList(nodes.get(p).split(";")).contains("area:" + key))
        return true;
    return false;
  }

  void observe(View decor) {
    if (observedRoot.get() == decor) return;
    unobserve();
    observedRoot = new WeakReference<>(decor);
    layoutListener =
        () -> {
          if (!running || layoutScanQueued) return;
          int children =
              activeFeed instanceof ViewGroup ? ((ViewGroup) activeFeed).getChildCount() : -1;
          boolean changed =
              children != lastFeedChildren
                  || lastScannedWork != r.current
                  || SystemClock.uptimeMillis() - lastScan > 6500;
          if (!changed) return;
          layoutScanQueued = true;
          r.main.postDelayed(
              () -> {
                layoutScanQueued = false;
                if (running) {
                  needsScan = true;
                  requestRefresh();
                }
              },
              220);
        };
    decor.getViewTreeObserver().addOnGlobalLayoutListener(layoutListener);
  }

  void unobserve() {
    View v = observedRoot.get();
    if (v != null && layoutListener != null && v.getViewTreeObserver().isAlive())
      v.getViewTreeObserver().removeOnGlobalLayoutListener(layoutListener);
    observedRoot.clear();
    layoutListener = null;
  }

  void publish(Activity a, int modified) throws JSONException {
    JSONObject status = new JSONObject();
    status.put("activity", a.getClass().getSimpleName());
    status.put("feedVisible", r.feedVisible);
    status.put("holderRoot", activeFeed != null);
    status.put("scan", scans);
    status.put("lastScanUs", scanDurationUs);
    status.put("cachedTargets", nodes.size());
    status.put("scannedNodes", nodeCount);
    status.put("matched", new JSONObject(matches));
    status.put("modified", modified);
    status.put("removedTabs", tabs.hidden());
    status.put("barsWrites", barsWrites);
    status.put("perFrameScan", false);
    status.put("navigationTargets", navigation.size());
    status.put("floatingBall", r.b("floating_menu"));
    status.put("playerGeometry", playerGeometry);
    status.put(
        "playerLayer", playerView == null ? "not_found" : playerView.getClass().getSimpleName());
    status.put("warning", r.feedVisible ? "" : "未识别普通视频播放页");
    if (!status.toString().equals(lastStatus.toString())) {
      r.uiStatus = status;
      lastStatus = status;
      r.dirty = true;
    }
  }

  void bars(Window w) {
    Bars b = windows.computeIfAbsent(w, Bars::new);
    int flags = b.visibility;
    boolean status = r.b("immersive_status"), nav = r.b("transparent_navigation");
    if (status) flags |= View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
    if (nav) flags |= View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
    if (r.b("hide_status") && !r.paused)
      flags |= View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
    if (r.b("hide_navigation") && !r.paused)
      flags |= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
    int sc = status ? android.graphics.Color.TRANSPARENT : b.status,
        nc = nav ? android.graphics.Color.TRANSPARENT : b.nav;
    if (w.getStatusBarColor() != sc) w.setStatusBarColor(sc);
    if (w.getNavigationBarColor() != nc) w.setNavigationBarColor(nc);
    if (w.getDecorView().getSystemUiVisibility() != flags) {
      w.getDecorView().setSystemUiVisibility(flags);
      barsWrites++;
    }
    b.appliedVisibility = flags;
  }

  void restoreWindow(Window w) {
    Bars b = windows.remove(w);
    if (b != null) {
      w.setFlags(b.flags, WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
      w.setStatusBarColor(b.status);
      w.setNavigationBarColor(b.nav);
      w.getDecorView().setSystemUiVisibility(b.visibility);
    }
  }

  void button(Activity a) {
    if (buttons.containsKey(a)) return;
    View root = a.findViewById(android.R.id.content);
    if (!(root instanceof FrameLayout)) return;
    TextView b = new TextView(a);
    b.setText("抖+");
    b.setTextColor(-1);
    b.setTextSize(13);
    b.setGravity(Gravity.CENTER);
    GradientDrawable background = new GradientDrawable();
    background.setColor(0xCC176E9B);
    background.setShape(GradientDrawable.OVAL);
    b.setBackground(background);
    b.setTag("DyFusionButton");
    int dp = (int) (a.getResources().getDisplayMetrics().density * 44);
    FrameLayout.LayoutParams p =
        new FrameLayout.LayoutParams(dp, dp, Gravity.START | Gravity.CENTER_VERTICAL);
    p.leftMargin = 8;
    b.setOnClickListener(v -> menu(a));
    ((FrameLayout) root).addView(b, p);
    buttons.put(a, b);
  }

  void removeButton(Activity a) {
    TextView b = buttons.remove(a);
    if (b != null && b.getParent() instanceof ViewGroup) ((ViewGroup) b.getParent()).removeView(b);
  }

  void menu(Activity a) {
    new AlertDialog.Builder(a)
        .setTitle("抖+")
        .setItems(
            new String[] {"作品画质 / 下载", "扫描与预览控件", "复制当前作品分享链接", "模块设置"},
            (d, which) -> {
              try {
                if (which == 0) {
                  if (r.current == null) throw new IllegalStateException("没有获取到当前作品");
                  r.openMedia(a, MediaCapture.capture(r.current).toString());
                } else if (which == 1) {
                  inspector();
                } else if (which == 2) {
                  String id = Host.text(Host.read(r.current, "aid", "aweme_id"));
                  if (id.isEmpty()) throw new IllegalStateException("没有作品 ID");
                  ((android.content.ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE))
                      .setPrimaryClip(
                          ClipData.newPlainText("作品", "https://www.douyin.com/video/" + id));
                  Toast.makeText(a, "已复制", Toast.LENGTH_SHORT).show();
                } else {
                  Intent i = new Intent();
                  i.setClassName("com.yolo.dyfusion", "com.yolo.dyfusion.ui.MainActivity");
                  a.startActivity(i);
                }
              } catch (Throwable e) {
                Toast.makeText(a, "操作失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
                r.fail("quick_menu", e);
              }
            })
        .show();
  }

  void inspector() {
    new ControlInspector(this).show();
  }

  void openSettings(Activity a) {
    Intent i = new Intent();
    i.setClassName("com.yolo.dyfusion", "com.yolo.dyfusion.ui.MainActivity");
    a.startActivity(i);
  }

  void togglePlayback() {
    try {
      if (r.engine != null) Host.call(r.engine, r.paused ? "play" : "pause");
    } catch (Throwable e) {
      r.fail("gesture_playback", e);
    }
  }

  void dispatchAction(String action) {
    Activity a = r.activity.get();
    if (a == null || !r.feedVisible) return;
    try {
      switch (action) {
        case "menu":
          menu(a);
          break;
        case "settings":
          openSettings(a);
          break;
        case "media":
        case "download":
          r.openMedia(a, MediaCapture.capture(r.current).toString());
          break;
        case "play_pause":
          togglePlayback();
          break;
        case "speed":
          r.update("force_speed", !r.b("force_speed"));
          break;
        case "auto_next":
          if (!r.b("auto_next")) r.update("pause_at_end", false);
          r.update("auto_next", !r.b("auto_next"));
          break;
        case "clean":
          clean = !clean;
          requestRefresh();
          break;
        case "comment":
          showComments();
          break;
        default:
          break;
      }
      r.hit("gesture_" + action);
    } catch (Throwable e) {
      r.fail("gesture", e);
    }
  }

  void showComments() throws Throwable {
    View candidate = null;
    for (Map.Entry<View, String> e : nodes.entrySet())
      if (e.getValue().contains("target:comment")) {
        candidate = e.getKey();
        if (candidate.isClickable()) break;
      }
    if (candidate == null) throw new IllegalStateException("没有定位到评论入口");
    boolean enabled = candidate.isEnabled(), click = candidate.isClickable();
    candidate.setEnabled(true);
    candidate.setClickable(true);
    candidate.performClick();
    candidate.setEnabled(enabled);
    candidate.setClickable(click);
  }

  @Override
  public void onActivityResumed(Activity a) {
    r.activity = new WeakReference<>(a);
    needsScan = true;
    r.hit("ui_resumed");
    if (!running) {
      running = true;
      r.main.post(heartbeat);
    }
  }

  @Override
  public void onActivityPaused(Activity a) {
    if (r.activity.get() == a) {
      running = false;
      r.main.removeCallbacks(heartbeat);
      gestures.cancel();
      r.feedVisible = false;
      edits.restore();
      tabs.restore();
      restoreWindow(a.getWindow());
      removeButton(a);
      unobserve();
      nodes.clear();
      navigation.clear();
    }
  }

  @Override
  public void onActivityDestroyed(Activity a) {
    removeButton(a);
    restoreWindow(a.getWindow());
  }

  @Override
  public void onActivityCreated(Activity a, Bundle b) {}

  @Override
  public void onActivityStarted(Activity a) {}

  @Override
  public void onActivityStopped(Activity a) {}

  @Override
  public void onActivitySaveInstanceState(Activity a, Bundle b) {}
}
