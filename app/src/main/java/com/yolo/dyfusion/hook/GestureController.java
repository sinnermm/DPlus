package com.yolo.dyfusion.hook;

import android.app.Activity;
import android.graphics.Rect;
import android.os.*;
import android.view.*;
import android.widget.TextView;
import com.yolo.dyfusion.core.GestureMath;
import de.robv.android.xposed.*;
import java.lang.reflect.Member;
import org.json.*;

/** Observe ordinary gestures. Only explicitly customized second taps/long holds are consumed. */
final class GestureController {
  final RuntimeState r;
  final UiController ui;
  Activity activity;
  Member method;
  MotionEvent down;
  float x0, y0, lastX, lastY;
  int cell = -1, lastCell = -1;
  long lastUp;
  boolean tracking, consuming, doublePending, moved;
  String doubleAction = "default", longActionName = "default";
  Runnable hold;

  GestureController(RuntimeState r, UiController ui) {
    this.r = r;
    this.ui = ui;
  }

  void install() {
    XposedHelpers.findAndHookMethod(
        Activity.class,
        "dispatchTouchEvent",
        MotionEvent.class,
        new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(MethodHookParam p) {
            try {
              if (!r.b("gestures_enabled") || !r.feedVisible || r.activity.get() != p.thisObject) {
                if (tracking) cancel();
                return;
              }
              Activity a = (Activity) p.thisObject;
              MotionEvent e = (MotionEvent) p.args[0];
              int action = e.getActionMasked();
              float slop = ViewConfiguration.get(a).getScaledTouchSlop();
              if (action == MotionEvent.ACTION_DOWN) {
                cancel();
                if (excluded(a, e)) return;
                View decor = a.getWindow().getDecorView();
                cell = GestureMath.cell(e.getX(), e.getY(), decor.getWidth(), decor.getHeight());
                doubleAction = read(cell, "double");
                longActionName = read(cell, "long");
                if (doubleAction.equals("default") && longActionName.equals("default")) return;
                tracking = true;
                activity = a;
                method = p.method;
                down = MotionEvent.obtain(e);
                x0 = e.getX();
                y0 = e.getY();
                moved = false;
                if (!doubleAction.equals("default")
                    && cell == lastCell
                    && e.getEventTime() - lastUp <= ViewConfiguration.getDoubleTapTimeout()
                    && GestureMath.tap(x0, y0, lastX, lastY, slop * 4)) {
                  consuming = true;
                  doublePending = true;
                  lastUp = 0;
                  p.setResult(true);
                } else if (!longActionName.equals("default")) {
                  hold =
                      () -> {
                        if (tracking && !moved && !consuming) {
                          nativeCancel();
                          consuming = true;
                          ui.dispatchAction(longActionName);
                        }
                      };
                  r.main.postDelayed(
                      hold, Math.max(300, ViewConfiguration.getLongPressTimeout() - 70));
                }
              } else if (tracking) {
                if (action == MotionEvent.ACTION_POINTER_DOWN) {
                  nativeCancel();
                  cancel();
                  return;
                }
                if (action == MotionEvent.ACTION_MOVE
                    && !GestureMath.tap(e.getX(), e.getY(), x0, y0, slop * 1.7f)) {
                  moved = true;
                  stopHold();
                  if (doublePending) {
                    nativeDown();
                    doublePending = false;
                    consuming = false;
                  }
                }
                if (action == MotionEvent.ACTION_UP) {
                  stopHold();
                  if (consuming) {
                    p.setResult(true);
                    if (doublePending && !moved) ui.dispatchAction(doubleAction);
                  } else if (!moved) {
                    lastUp = e.getEventTime();
                    lastCell = cell;
                    lastX = e.getX();
                    lastY = e.getY();
                  }
                  finish();
                } else if (action == MotionEvent.ACTION_CANCEL) {
                  if (consuming) p.setResult(true);
                  finish();
                } else if (consuming) p.setResult(true);
              }
            } catch (Throwable e) {
              cancel();
              r.fail("gestures", e);
            }
          }
        });
    r.installed("gestures", 1);
  }

  String read(int c, String type) {
    JSONObject g = r.config.optJSONObject("gestures"),
        v = g == null ? null : g.optJSONObject(String.valueOf(c));
    return v == null ? "default" : v.optString(type, "default");
  }

  boolean excluded(Activity a, MotionEvent e) {
    float density = a.getResources().getDisplayMetrics().density;
    View decor = a.getWindow().getDecorView();
    if (e.getX() < density * 8 || e.getX() > decor.getWidth() - density * 8) return true;
    TextView ball = ui.buttons.get(a);
    if (ball != null && inside(ball, e)) return true;
    for (java.util.Map.Entry<View, String> entry : ui.nodes.entrySet()) {
      View v = entry.getKey();
      if (v.isShown() && v.getAlpha() > .2f && v.isClickable() && inside(v, e)) return true;
    }
    return false;
  }

  boolean inside(View v, MotionEvent e) {
    Rect rect = new Rect();
    return v.getGlobalVisibleRect(rect) && rect.contains((int) e.getRawX(), (int) e.getRawY());
  }

  void nativeCancel() {
    if (down == null) return;
    MotionEvent e = MotionEvent.obtain(down);
    e.setAction(MotionEvent.ACTION_CANCEL);
    try {
      XposedBridge.invokeOriginalMethod(method, activity, new Object[] {e});
    } catch (Throwable ignored) {
    } finally {
      e.recycle();
    }
  }

  void nativeDown() {
    if (down == null) return;
    try {
      XposedBridge.invokeOriginalMethod(method, activity, new Object[] {down});
    } catch (Throwable ignored) {
    }
  }

  void stopHold() {
    if (hold != null) r.main.removeCallbacks(hold);
    hold = null;
  }

  void finish() {
    tracking = false;
    consuming = false;
    doublePending = false;
    stopHold();
    if (down != null) down.recycle();
    down = null;
  }

  void cancel() {
    finish();
  }
}
