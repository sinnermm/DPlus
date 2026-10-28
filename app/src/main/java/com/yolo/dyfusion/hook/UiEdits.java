package com.yolo.dyfusion.hook;

import android.view.View;
import java.util.*;

/** Idempotent, reversible edits; unchanged values do not cause redraw or relayout. */
public final class UiEdits {
  private static final class Change {
    final float alpha, scaleX, scaleY;
    float y;
    final int visibility;
    final boolean enabled, clickable;
    boolean wanted, hide, collapse, disable, move;
    float factor = 1, offset, scale = 1;
    boolean scaling;

    Change(View v) {
      alpha = v.getAlpha();
      y = v.getTranslationY();
      scaleX = v.getScaleX();
      scaleY = v.getScaleY();
      visibility = v.getVisibility();
      enabled = v.isEnabled();
      clickable = v.isClickable();
    }

    void values(View v, float a, int visible, boolean e, boolean click, float shift) {
      if (Math.abs(v.getAlpha() - a) > .0001f) v.setAlpha(a);
      if (v.getVisibility() != visible) v.setVisibility(visible);
      if (v.isEnabled() != e) v.setEnabled(e);
      if (v.isClickable() != click) v.setClickable(click);
      if (Math.abs(v.getTranslationY() - shift) > .1f) v.setTranslationY(shift);
    }

    void restore(View v) {
      values(v, alpha, visibility, enabled, clickable, y);
      if (Math.abs(v.getScaleX() - scaleX) > .0001f) v.setScaleX(scaleX);
      if (Math.abs(v.getScaleY() - scaleY) > .0001f) v.setScaleY(scaleY);
    }

    void apply(View v) {
      values(
          v,
          alpha * factor,
          collapse ? View.GONE : hide && visibility == View.VISIBLE ? View.INVISIBLE : visibility,
          disable ? false : enabled,
          disable ? false : clickable,
          y + (move ? offset : 0));
      float x = scaleX * (scaling ? scale : 1), z = scaleY * (scaling ? scale : 1);
      if (Math.abs(v.getScaleX() - x) > .0001f) v.setScaleX(x);
      if (Math.abs(v.getScaleY() - z) > .0001f) v.setScaleY(z);
    }
  }

  private final WeakHashMap<View, Change> edits = new WeakHashMap<>();

  public void begin() {
    for (Change c : edits.values()) {
      c.wanted = false;
      c.hide = false;
      c.collapse = false;
      c.disable = false;
      c.factor = 1;
      c.move = false;
      c.offset = 0;
      c.scaling = false;
      c.scale = 1;
    }
  }

  public void alpha(View v, int percent, boolean zeroUnclickable) {
    if (percent >= 100) return;
    Change c = edits.computeIfAbsent(v, Change::new);
    c.wanted = true;
    c.factor = Math.min(c.factor, Math.max(0, percent) / 100f);
    if (percent == 0 && zeroUnclickable) {
      c.hide = true;
      c.disable = true;
    }
  }

  public void hide(View v) {
    Change c = edits.computeIfAbsent(v, Change::new);
    c.wanted = true;
    c.factor = 0;
    c.hide = true;
    c.disable = true;
  }

  public void collapse(View v) {
    Change c = edits.computeIfAbsent(v, Change::new);
    c.wanted = true;
    c.collapse = true;
    c.disable = true;
  }

  public void moveY(View v, float offset) {
    Change c = edits.computeIfAbsent(v, Change::new);
    c.wanted = true;
    c.move = true;
    c.offset = offset;
  }

  public void scale(View v, float factor) {
    Change c = edits.computeIfAbsent(v, Change::new);
    c.wanted = true;
    c.scaling = true;
    c.scale = Math.max(.1f, Math.min(1f, factor));
  }

  public void disable(View v) {
    Change c = edits.computeIfAbsent(v, Change::new);
    c.wanted = true;
    c.disable = true;
  }

  public float appliedOffset(View v) {
    Change c = edits.get(v);
    if (c == null || !c.move) return 0;
    if (Math.abs(v.getTranslationY() - (c.y + c.offset)) > .5f) {
      c.y = v.getTranslationY();
      c.move = false;
      c.offset = 0;
      return 0;
    }
    return c.offset;
  }

  public float originalScaleX(View v) {
    Change c = edits.get(v);
    return c == null ? v.getScaleX() : c.scaleX;
  }

  public float originalScaleY(View v) {
    Change c = edits.get(v);
    return c == null ? v.getScaleY() : c.scaleY;
  }

  public float originalY(View v) {
    Change c = edits.get(v);
    return c == null ? v.getTranslationY() : c.y;
  }

  public void finish() {
    Iterator<Map.Entry<View, Change>> it = edits.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry<View, Change> e = it.next();
      if (!e.getValue().wanted) {
        e.getValue().restore(e.getKey());
        it.remove();
      } else e.getValue().apply(e.getKey());
    }
  }

  public void enforce() {
    for (Map.Entry<View, Change> e : edits.entrySet()) e.getValue().apply(e.getKey());
  }

  public void restore() {
    for (Map.Entry<View, Change> e : edits.entrySet()) e.getValue().restore(e.getKey());
    edits.clear();
  }

  public boolean originallyVisible(View v) {
    Change c = edits.get(v);
    return (c == null ? v.getVisibility() : c.visibility) == View.VISIBLE;
  }

  public int size() {
    return edits.size();
  }
}
