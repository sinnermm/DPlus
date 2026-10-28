package com.yolo.dyfusion.hook;

import android.app.*;
import android.view.*;
import android.widget.*;
import de.robv.android.xposed.*;
import java.util.*;

/** Only decorates dialogs positively identified as a work's share panel. */
final class ShareEntry {
  final RuntimeState r;
  final WeakHashMap<Dialog, Boolean> attached = new WeakHashMap<>();

  ShareEntry(RuntimeState r) {
    this.r = r;
  }

  void install() {
    XposedHelpers.findAndHookMethod(
        Dialog.class,
        "show",
        new XC_MethodHook() {
          @Override
          protected void afterHookedMethod(MethodHookParam p) {
            Dialog dialog = (Dialog) p.thisObject;
            r.main.postDelayed(() -> add(dialog), 120);
            r.main.postDelayed(() -> add(dialog), 450);
            r.main.postDelayed(() -> add(dialog), 950);
          }
        });
    r.installed("share_entry", 1);
  }

  @android.annotation.SuppressLint("ResourceType")
  void add(Dialog d) {
    if (!r.on()
        || !r.b("share_entry")
        || !d.isShowing()
        || attached.containsKey(d)
        || r.current == null
        || r.ui == null) return;
    Activity a = r.activity.get();
    if (a == null || d.getWindow() == null) return;
    View root = d.getWindow().getDecorView();
    List<TextView> texts = new ArrayList<>();
    collect(root, texts, 0);
    boolean marker = false, link = false;
    for (TextView t : texts) {
      String text = t.getText().toString().trim();
      if (text.equals("分享给") || text.equals("分享到") || text.equals("分享")) marker = true;
      if (text.equals("复制链接") || text.equals("分享链接")) link = true;
    }
    if (!marker) return;
    View known = root.findViewById(2131380639);
    if (!link && known == null) return;
    ViewGroup target = known instanceof ViewGroup ? (ViewGroup) known : null;
    for (TextView t : texts) {
      if (target != null) break;
      if (!t.getText().toString().trim().matches("分享给|分享到")) continue;
      ViewParent p = t.getParent();
      for (int n = 0; n < 4 && p instanceof View; n++, p = p.getParent()) {
        if (p instanceof LinearLayout
            && ((LinearLayout) p).getOrientation() == LinearLayout.VERTICAL) {
          target = (ViewGroup) p;
          break;
        }
      }
      if (target != null) break;
    }
    if (target == null) {
      for (TextView t : texts)
        if (t.getText().toString().trim().equals("复制链接")
            || t.getText().toString().trim().equals("分享链接")) {
          ViewParent p = t.getParent();
          for (int n = 0; n < 5 && p instanceof View; n++, p = p.getParent())
            if (p instanceof LinearLayout
                && ((LinearLayout) p).getOrientation() == LinearLayout.HORIZONTAL) {
              target = (ViewGroup) p;
              break;
            }
          if (target != null) break;
        }
    }
    if (target == null) return;
    if (target.findViewWithTag("DPlusShareEntry") != null) {
      attached.put(d, true);
      return;
    }
    LinearLayout box = new LinearLayout(a);
    box.setTag("DPlusShareEntry");
    box.setOrientation(LinearLayout.VERTICAL);
    box.setGravity(Gravity.CENTER);
    int dp = (int) (a.getResources().getDisplayMetrics().density * 16);
    box.setPadding(dp, dp / 2, dp, dp / 2);
    TextView icon = new TextView(a);
    icon.setText("抖+");
    icon.setTextSize(18);
    icon.setTextColor(0xFF176E9B);
    icon.setGravity(Gravity.CENTER);
    TextView label = new TextView(a);
    label.setText("抖+功能");
    label.setTextSize(12);
    label.setTextColor(0xFF172B3A);
    box.addView(icon);
    box.addView(label);
    box.setOnClickListener(
        v -> {
          d.dismiss();
          r.ui.menu(a);
        });
    boolean horizontal = false;
    try {
      horizontal = ((Number) Host.call(target, "getOrientation")).intValue() == 0;
    } catch (Throwable ignored) {
    }
    if (horizontal) {
      View originalChild = target.getChildCount() > 0 ? target.getChildAt(0) : null;
      target.addView(box, 0);
      ViewGroup.LayoutParams boxParams = box.getLayoutParams();
      boxParams.width = (int) (a.getResources().getDisplayMetrics().density * 68);
      boxParams.height = -1;
      box.setLayoutParams(boxParams);
      if (originalChild != null) {
        ViewGroup.LayoutParams originalParams = originalChild.getLayoutParams();
        originalParams.width = 0;
        try {
          Host.write(originalParams, 1f, "weight");
        } catch (Throwable ignored) {
        }
        originalChild.setLayoutParams(originalParams);
      }
    } else if (target instanceof LinearLayout
        && ((LinearLayout) target).getOrientation() == LinearLayout.VERTICAL) {
      box.setOrientation(LinearLayout.HORIZONTAL);
      target.addView(
          box,
          new LinearLayout.LayoutParams(
              -1, (int) (a.getResources().getDisplayMetrics().density * 44)));
    } else target.addView(box, new ViewGroup.LayoutParams(-2, -2));
    attached.put(d, true);
    r.hit("share_entry_visible");
  }

  void collect(View v, List<TextView> out, int depth) {
    if (depth > 40 || out.size() > 250) return;
    if (v instanceof TextView) out.add((TextView) v);
    if (v instanceof ViewGroup) {
      ViewGroup g = (ViewGroup) v;
      for (int i = 0; i < g.getChildCount(); i++) collect(g.getChildAt(i), out, depth + 1);
    }
  }
}
