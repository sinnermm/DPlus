package com.yolo.dyfusion.hook;

import android.view.*;
import android.widget.*;
import com.yolo.dyfusion.core.Rules;
import de.robv.android.xposed.*;
import java.util.*;

/** Remove whole clickable items and redistribute the surviving hitboxes, not only labels. */
public final class TabRows {
  private static final class Item {
    final int visibility, width;
    final boolean enabled, clickable;
    final ViewGroup.LayoutParams params;

    Item(View v) {
      visibility = v.getVisibility();
      width = v.getWidth();
      enabled = v.isEnabled();
      clickable = v.isClickable();
      params = copy(v.getLayoutParams());
    }

    void restore(View v) {
      v.setVisibility(visibility);
      v.setEnabled(enabled);
      v.setClickable(clickable);
      if (params != null) v.setLayoutParams(copy(params));
    }
  }

  private static final class Row {
    final Map<View, Item> original = new LinkedHashMap<>();
    final Set<View> hidden = Collections.newSetFromMap(new IdentityHashMap<>());
    boolean aligning;
    String names = "";
    int children = -1;
  }

  private final WeakHashMap<ViewGroup, Row> rows = new WeakHashMap<>();
  private final Set<Class<?>> hooked = new HashSet<>();

  public int apply(ViewGroup group, String names) {
    Row row = rows.computeIfAbsent(group, g -> new Row());
    if (row.names.equals(names) && row.children == group.getChildCount()) {
      align(group, row);
      return row.hidden.size();
    }
    row.names = names;
    row.children = group.getChildCount();
    row.hidden.clear();
    Set<String> denied = Rules.tokens(names);
    for (int n = 0; n < group.getChildCount(); n++) {
      View child = group.getChildAt(n);
      row.original.putIfAbsent(child, new Item(child));
      if (labelMatches(child, denied, 0)) row.hidden.add(child);
    }
    if (row.hidden.size() == group.getChildCount() && group.getChildCount() > 0)
      row.hidden.remove(group.getChildAt(0));
    for (Map.Entry<View, Item> e : row.original.entrySet()) {
      View v = e.getKey();
      if (v.getParent() != group) continue;
      if (row.hidden.contains(v)) {
        if (v.getVisibility() != View.GONE) v.setVisibility(View.GONE);
        v.setEnabled(false);
        v.setClickable(false);
      } else {
        Item state = e.getValue();
        if (v.getVisibility() != state.visibility) v.setVisibility(state.visibility);
        v.setEnabled(state.enabled);
        v.setClickable(state.clickable);
      }
    }
    align(group, row);
    if (!row.hidden.isEmpty()) hookLayout(group);
    return row.hidden.size();
  }

  private void hookLayout(ViewGroup g) {
    if (hooked.contains(g.getClass())) return;
    try {
      for (Class<?> c = g.getClass(); c != null && c != View.class; c = c.getSuperclass())
        try {
          java.lang.reflect.Method m =
              c.getDeclaredMethod(
                  "onLayout", boolean.class, int.class, int.class, int.class, int.class);
          m.setAccessible(true);
          XposedBridge.hookMethod(
              m,
              new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam p) {
                  Row row = rows.get(p.thisObject);
                  if (row != null && !row.hidden.isEmpty()) align((ViewGroup) p.thisObject, row);
                }
              });
          hooked.add(g.getClass());
          return;
        } catch (NoSuchMethodException ignored) {
        }
    } catch (Throwable ignored) {
    }
  }

  private static boolean labelMatches(View v, Set<String> denied, int depth) {
    if (depth > 12) return false;
    if (v instanceof TextView && denied.contains(((TextView) v).getText().toString().trim()))
      return true;
    if (v instanceof ViewGroup)
      for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++)
        if (labelMatches(((ViewGroup) v).getChildAt(i), denied, depth + 1)) return true;
    return false;
  }

  private static void align(ViewGroup g, Row row) {
    if (row.aligning || row.hidden.isEmpty() || g.getWidth() <= 0) return;
    row.aligning = true;
    try {
      List<View> visible = new ArrayList<>();
      for (int i = 0; i < g.getChildCount(); i++) {
        View v = g.getChildAt(i);
        if (!row.hidden.contains(v) && v.getVisibility() != View.GONE) visible.add(v);
      }
      if (visible.isEmpty()) return;
      int left = g.getPaddingLeft(), width = g.getWidth() - left - g.getPaddingRight();
      boolean rtl = g.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
      for (int i = 0; i < visible.size(); i++) {
        View v = visible.get(i);
        int pos = rtl ? visible.size() - 1 - i : i;
        int l = left + width * pos / visible.size(),
            r = left + width * (pos + 1) / visible.size(),
            h = Math.max(1, v.getHeight());
        if (v.getMeasuredWidth() != r - l)
          v.measure(
              View.MeasureSpec.makeMeasureSpec(r - l, View.MeasureSpec.EXACTLY),
              View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        if (v.getLeft() != l || v.getRight() != r) v.layout(l, v.getTop(), r, v.getTop() + h);
      }
    } finally {
      row.aligning = false;
    }
  }

  public void enforce() {
    for (Map.Entry<ViewGroup, Row> e : rows.entrySet()) {
      for (View v : e.getValue().hidden)
        if (v.getParent() == e.getKey() && v.getVisibility() != View.GONE)
          v.setVisibility(View.GONE);
      align(e.getKey(), e.getValue());
    }
  }

  public void restore() {
    for (Row row : rows.values())
      for (Map.Entry<View, Item> e : row.original.entrySet()) e.getValue().restore(e.getKey());
    rows.clear();
  }

  public int hidden() {
    int n = 0;
    for (Row r : rows.values()) n += r.hidden.size();
    return n;
  }

  static ViewGroup.LayoutParams copy(ViewGroup.LayoutParams p) {
    if (p == null) return null;
    if (p instanceof LinearLayout.LayoutParams)
      return new LinearLayout.LayoutParams((LinearLayout.LayoutParams) p);
    if (p instanceof FrameLayout.LayoutParams)
      return new FrameLayout.LayoutParams((FrameLayout.LayoutParams) p);
    if (p instanceof android.widget.RelativeLayout.LayoutParams)
      return new android.widget.RelativeLayout.LayoutParams(
          (android.widget.RelativeLayout.LayoutParams) p);
    if (p instanceof ViewGroup.MarginLayoutParams)
      return new ViewGroup.MarginLayoutParams((ViewGroup.MarginLayoutParams) p);
    return new ViewGroup.LayoutParams(p);
  }
}
