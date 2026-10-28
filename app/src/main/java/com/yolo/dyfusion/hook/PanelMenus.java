package com.yolo.dyfusion.hook;

import android.app.Dialog;
import android.view.*;
import android.widget.*;
import com.yolo.dyfusion.core.Rules;
import de.robv.android.xposed.*;
import java.util.*;

/** Exact-label menu decoration, no action callbacks are invoked and no contacts are recorded. */
final class PanelMenus {
  final RuntimeState r;
  final WeakHashMap<Dialog, UiEdits> edits = new WeakHashMap<>();

  PanelMenus(RuntimeState r) {
    this.r = r;
  }

  void install() {
    XposedHelpers.findAndHookMethod(
        Dialog.class,
        "show",
        new XC_MethodHook() {
          @Override
          protected void afterHookedMethod(MethodHookParam p) {
            Dialog d = (Dialog) p.thisObject;
            r.main.postDelayed(() -> filter(d), 160);
            r.main.postDelayed(() -> filter(d), 450);
          }
        });
    XposedHelpers.findAndHookMethod(
        Dialog.class,
        "dismiss",
        new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(MethodHookParam p) {
            UiEdits e = edits.remove(p.thisObject);
            if (e != null) e.restore();
          }
        });
    r.installed("panel_filters", 1);
  }

  void filter(Dialog d) {
    if (!d.isShowing() || d.getWindow() == null || r.current == null) return;
    View root = d.getWindow().getDecorView();
    List<TextView> labels = new ArrayList<>();
    scan(root, labels, 0);
    boolean share = false, longMenu = false;
    int matches = 0;
    for (TextView t : labels) {
      String s = t.getText().toString().trim();
      if (s.equals("分享给") || s.equals("分享到")) share = true;
      if (s.equals("倍速") || s.equals("清屏播放") || s.equals("缓存视频")) matches++;
    }
    longMenu =
        !share
            && (matches >= 2
                || d.getClass().getName().toLowerCase(Locale.ROOT).contains("longpress"));
    if (!share && !longMenu) return;
    String denied = r.s(share ? "hide_share_items" : "hide_long_items");
    UiEdits change = edits.computeIfAbsent(d, key -> new UiEdits());
    change.begin();
    if (r.on()) {
      for (TextView t : labels) {
        String value = t.getText().toString().trim();
        if (Rules.exact(value, denied)) {
          View target = item(t, root);
          change.collapse(target);
        }
      }
      if (share && r.b("hide_share_friends")) hideFriends(root, change, 0);
    }
    change.finish();
    if (change.size() > 0) r.hit(share ? "share_items_removed" : "long_items_removed");
  }

  View item(View v, View root) {
    View result = v;
    for (ViewParent p = v.getParent(); p instanceof View; p = p.getParent()) {
      View c = (View) p;
      if (c == root
          || c.getHeight() > root.getHeight() * .28f
          || c.getWidth() > root.getWidth() * .58f) break;
      result = c;
      if (c.isClickable()) break;
    }
    return result;
  }

  @android.annotation.SuppressLint("ResourceType")
  void hideFriends(View v, UiEdits changes, int depth) {
    if (depth > 40) return;
    if (r.hostVersion.equals("40.7.0") && v.getId() == 2131372365) {
      changes.collapse(v);
      return;
    }
    String id = UiProfile.entry(v).toLowerCase(Locale.ROOT);
    if (id.contains("share_contact")
        || id.contains("share_friend")
        || id.contains("share_user")
        || id.equals("contact_list")
        || id.equals("avatar_horizontal_list_view")) {
      changes.collapse(v);
      return;
    }
    if (v instanceof ViewGroup)
      for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++)
        hideFriends(((ViewGroup) v).getChildAt(i), changes, depth + 1);
  }

  void scan(View v, List<TextView> labels, int depth) {
    if (depth > 40 || labels.size() > 300) return;
    if (v instanceof TextView) labels.add((TextView) v);
    if (v instanceof ViewGroup)
      for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++)
        scan(((ViewGroup) v).getChildAt(i), labels, depth + 1);
  }
}
