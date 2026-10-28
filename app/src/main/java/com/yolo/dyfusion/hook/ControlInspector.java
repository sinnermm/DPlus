package com.yolo.dyfusion.hook;

import android.app.*;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import java.lang.ref.*;
import java.util.*;
import org.json.*;

/** In-host, ID-only inspection: no user text, account identifiers or media URLs are collected. */
final class ControlInspector {
  final UiController ui;
  final List<View> controls = new ArrayList<>();
  int index;
  Dialog dialog;
  TextView description;
  FrameLayout overlay;
  View outline;

  ControlInspector(UiController ui) {
    this.ui = ui;
  }

  void show() {
    Activity a = ui.r.activity.get();
    if (a == null || ui.activeFeed == null) return;
    collect(ui.activeFeed, 0);
    controls.sort(Comparator.comparingInt(v -> v.getId()));
    if (controls.isEmpty()) {
      Toast.makeText(a, "当前作品没有可保存的控件 ID", Toast.LENGTH_SHORT).show();
      return;
    }
    LinearLayout box = new LinearLayout(a);
    box.setOrientation(LinearLayout.VERTICAL);
    int dp = (int) (a.getResources().getDisplayMetrics().density * 16);
    box.setPadding(dp, dp, dp, dp);
    description = new TextView(a);
    description.setTextColor(0xFF172B3A);
    description.setTextSize(13);
    box.addView(description);
    LinearLayout buttons = new LinearLayout(a);
    for (String label : new String[] {"上一个", "下一个", "刷新列表"}) {
      Button b = new Button(a);
      b.setText(label);
      b.setTextSize(12);
      b.setOnClickListener(
          v -> {
            if (label.equals("上一个")) index = (index + controls.size() - 1) % controls.size();
            else if (label.equals("下一个")) index = (index + 1) % controls.size();
            else {
              controls.clear();
              collect(ui.activeFeed, 0);
              index = 0;
            }
            render();
          });
      buttons.addView(b, new LinearLayout.LayoutParams(0, -2, 1));
    }
    box.addView(buttons);
    for (String label : new String[] {"隐藏这个控件", "恢复这个控件", "播放时透明度", "暂停时透明度", "高亮预览"}) {
      Button b = new Button(a);
      b.setText(label);
      b.setAllCaps(false);
      b.setOnClickListener(
          v -> {
            if (controls.isEmpty()) return;
            if (label.equals("隐藏这个控件")) save("hidden", true);
            else if (label.equals("恢复这个控件")) save("hidden", false);
            else if (label.equals("高亮预览")) highlight();
            else alpha(label.startsWith("播放") ? "play_alpha" : "pause_alpha");
          });
      box.addView(b);
    }
    dialog = new Dialog(a);
    dialog.setContentView(box);
    Window w = dialog.getWindow();
    if (w != null) {
      w.setBackgroundDrawableResource(android.R.color.white);
      w.setDimAmount(.12f);
    }
    dialog.setOnDismissListener(v -> removeHighlight());
    dialog.show();
    if (w != null) w.setLayout((int) (a.getResources().getDisplayMetrics().widthPixels * .9f), -2);
    render();
  }

  void collect(View v, int depth) {
    if (v == null || depth > 65 || controls.size() >= 250) return;
    if (v != ui.activeFeed
        && v.getId() != View.NO_ID
        && v.getId() != 0
        && !UiProfile.named(
            v, "SurfaceView", "TextureView", "VideoPinchViewContainer", "LongPressLayout")
        && v.getHeight() > 0
        && v.getHeight() < v.getRootView().getHeight() * .45f) controls.add(v);
    if (v instanceof ViewGroup) {
      ViewGroup g = (ViewGroup) v;
      for (int i = 0; i < g.getChildCount(); i++) collect(g.getChildAt(i), depth + 1);
    }
  }

  @android.annotation.SuppressLint("SetTextI18n")
  void render() {
    removeHighlight();
    if (controls.isEmpty()) {
      description.setText("当前列表已空");
      return;
    }
    index = Math.min(index, controls.size() - 1);
    View v = controls.get(index);
    String id = String.valueOf(v.getId());
    JSONObject rules = ui.r.config.optJSONObject("custom_controls"),
        rule = rules == null ? null : rules.optJSONObject(id);
    description.setText(
        (index + 1)
            + " / "
            + controls.size()
            + "\n资源："
            + UiProfile.entry(v)
            + "\n类："
            + v.getClass().getName()
            + "\nID："
            + id
            + "\n尺寸："
            + v.getWidth()
            + "×"
            + v.getHeight()
            + "\n隐藏："
            + (rule != null && rule.optBoolean("hidden"))
            + "\n播放 / 暂停透明度："
            + (rule == null ? 100 : rule.optInt("play_alpha", 100))
            + " / "
            + (rule == null ? 100 : rule.optInt("pause_alpha", 100)));
  }

  void save(String field, Object value) {
    try {
      JSONObject next = new JSONObject(ui.r.config.toString());
      JSONObject rules = next.optJSONObject("custom_controls");
      if (rules == null) rules = new JSONObject();
      String id = String.valueOf(controls.get(index).getId());
      JSONObject rule = rules.optJSONObject(id);
      if (rule == null) rule = new JSONObject();
      rule.put(field, value);
      rules.put(id, rule);
      next.put("custom_controls", rules);
      ui.r.update("custom_controls", rules);
      ui.r.main.postDelayed(this::render, 500);
    } catch (Throwable e) {
      ui.r.fail("control_inspector", e);
    }
  }

  @android.annotation.SuppressLint("SetTextI18n")
  void alpha(String field) {
    Activity a = ui.r.activity.get();
    if (a == null) return;
    EditText e = new EditText(a);
    e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
    e.setText("100");
    new AlertDialog.Builder(a)
        .setTitle("透明度 0–100")
        .setView(e)
        .setNegativeButton("取消", null)
        .setPositiveButton(
            "保存",
            (d, which) -> {
              try {
                int value = Integer.parseInt(e.getText().toString());
                if (value < 0 || value > 100) throw new IllegalArgumentException("范围为 0–100");
                save(field, value);
              } catch (Exception ex) {
                Toast.makeText(a, ex.getMessage(), Toast.LENGTH_SHORT).show();
              }
            })
        .show();
  }

  void highlight() {
    Activity a = ui.r.activity.get();
    if (a == null) return;
    View root = a.findViewById(android.R.id.content);
    if (!(root instanceof FrameLayout)) return;
    Rect rect = new Rect();
    controls.get(index).getGlobalVisibleRect(rect);
    if (rect.isEmpty()) return;
    removeHighlight();
    overlay = (FrameLayout) root;
    outline = new View(a);
    GradientDrawable bg = new GradientDrawable();
    bg.setColor(0x22176E9B);
    bg.setStroke(4, 0xFF176E9B);
    outline.setBackground(bg);
    outline.setClickable(false);
    int[] origin = new int[2];
    root.getLocationOnScreen(origin);
    FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(rect.width(), rect.height());
    lp.leftMargin = rect.left - origin[0];
    lp.topMargin = rect.top - origin[1];
    overlay.addView(outline, lp);
    ui.r.main.postDelayed(this::removeHighlight, 1800);
  }

  void removeHighlight() {
    if (outline != null && outline.getParent() instanceof ViewGroup)
      ((ViewGroup) outline.getParent()).removeView(outline);
    outline = null;
    overlay = null;
  }
}
