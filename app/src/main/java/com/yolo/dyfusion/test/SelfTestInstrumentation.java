package com.yolo.dyfusion.test;

import android.app.Instrumentation;
import android.os.Bundle;
import com.yolo.dyfusion.config.*;
import com.yolo.dyfusion.core.*;
import com.yolo.dyfusion.media.*;
import org.json.*;

public final class SelfTestInstrumentation extends Instrumentation {
  @Override
  public void onCreate(Bundle b) {
    super.onCreate(b);
    start();
  }

  @android.annotation.SuppressLint("ResourceType")
  @Override
  public void onStart() {
    Bundle result = new Bundle();
    int count = 0;
    try {
      JSONObject defaults = Catalog.defaults(getTargetContext());
      Catalog.validate(getTargetContext(), defaults);
      if (!defaults.has("hide_top_bar")
          || !defaults.has("hide_bottom_bar")
          || !defaults.has("hide_feed_collect")
          || !defaults.has("hide_mix_bar")
          || !defaults.has("hide_related_search")
          || !defaults.has("play_alpha_music")
          || !defaults.has("pause_alpha_music"))
        throw new AssertionError("new switches not registered");
      count++;
      JSONObject bad = new JSONObject();
      bad.put("default_speed", 999);
      try {
        Catalog.validate(getTargetContext(), bad);
        throw new AssertionError("speed range accepted");
      } catch (IllegalArgumentException expected) {
      }
      count++;
      bad = new JSONObject();
      bad.put("private_anti_recall", true);
      try {
        Catalog.validate(getTargetContext(), bad);
        throw new AssertionError("unknown key accepted");
      } catch (IllegalArgumentException expected) {
      }
      count++;
      bad = new JSONObject();
      bad.put("play_alpha_top", 101);
      try {
        Catalog.validate(getTargetContext(), bad);
        throw new AssertionError("range accepted");
      } catch (IllegalArgumentException expected) {
      }
      count++;
      bad = new JSONObject();
      bad.put("download_folder", "../escape");
      try {
        Catalog.validate(getTargetContext(), bad);
        throw new AssertionError("path accepted");
      } catch (IllegalArgumentException expected) {
      }
      count++;
      JSONObject fixture =
          new JSONObject(
              "{\"aweme_id\":\"123456789012345\",\"desc\":\"test\",\"video\":{\"play_addr\":{\"url_list\":[\"https://v.example.douyinvod.com/sample\"]},\"cover\":{\"url_list\":[\"https://img.example.douyinpic.com/cover\"]}}}");
      JSONObject parsed = PublicParser.fromAweme(fixture);
      if (parsed.getJSONArray("items").length() != 2) throw new AssertionError("parse fixture");
      count++;
      Downloads.allowed(new java.net.URL("https://v.example.douyinvod.com/sample"));
      count++;
      try {
        Downloads.allowed(new java.net.URL("https://127.0.0.1/a"));
        throw new AssertionError("IP accepted");
      } catch (IllegalArgumentException expected) {
      }
      count++;
      Bundle got =
          getTargetContext()
              .getContentResolver()
              .call(SettingsProvider.URI, "settings", null, null);
      if (got == null || !got.containsKey("json")) throw new AssertionError("provider");
      count++;
      final Throwable[] uiFailure = new Throwable[1];
      runOnMainSync(
          () -> {
            try {
              android.view.View v = new android.view.View(getTargetContext());
              v.setAlpha(.8f);
              v.setEnabled(true);
              v.setClickable(true);
              com.yolo.dyfusion.hook.UiEdits edits = new com.yolo.dyfusion.hook.UiEdits();
              edits.begin();
              edits.hide(v);
              edits.finish();
              if (v.getVisibility() != android.view.View.INVISIBLE
                  || v.getAlpha() != 0
                  || v.isEnabled()
                  || v.isClickable()) throw new AssertionError("hide does not block target");
              v.setAlpha(1);
              v.setVisibility(android.view.View.VISIBLE);
              edits.enforce();
              if (v.getVisibility() != android.view.View.INVISIBLE || v.getAlpha() != 0)
                throw new AssertionError("draw guard");
              if (!edits.originallyVisible(v))
                throw new AssertionError("hidden target lost from rescan");
              edits.begin();
              edits.finish();
              if (v.getVisibility() != android.view.View.VISIBLE
                  || Math.abs(v.getAlpha() - .8f) > .001
                  || !v.isEnabled()
                  || !v.isClickable()) throw new AssertionError("restore original");
              edits.begin();
              edits.alpha(v, 50, true);
              edits.finish();
              if (Math.abs(v.getAlpha() - .4f) > .001)
                throw new AssertionError("alpha relative baseline");
              edits.begin();
              edits.alpha(v, 50, true);
              edits.alpha(v, 50, true);
              edits.finish();
              if (Math.abs(v.getAlpha() - .4f) > .001)
                throw new AssertionError("duplicate factor accumulation");
              edits.restore();
              if (edits.size() != 0) throw new AssertionError("restore leak");
              v.setVisibility(android.view.View.GONE);
              edits.begin();
              edits.hide(v);
              edits.finish();
              edits.restore();
              if (v.getVisibility() != android.view.View.GONE)
                throw new AssertionError("gone baseline");
              android.widget.FrameLayout page = new android.widget.FrameLayout(getTargetContext());
              page.setId(2131374345);
              page.layout(0, 0, 1260, 2800);
              java.lang.reflect.Method identify =
                  Class.forName("com.yolo.dyfusion.hook.UiProfile")
                      .getDeclaredMethod("target", android.view.View.class, String.class);
              identify.setAccessible(true);
              if (!identify.invoke(null, page, "40.7.0").equals(""))
                throw new AssertionError("full-page reused ID hidden");
              android.view.View control = new android.view.View(getTargetContext());
              control.setId(2131374345);
              page.addView(control);
              control.layout(920, 1400, 1258, 1635);
              if (!identify.invoke(null, control, "40.7.0").equals("like"))
                throw new AssertionError("actual narrow like missed");
              control.setId(2131371070);
              if (!identify.invoke(null, control, "40.7.0").equals("collect"))
                throw new AssertionError("collection container not mapped");
              page.setId(2131371070);
              if (!identify.invoke(null, page, "40.7.0").equals(""))
                throw new AssertionError("full-page collection ID hidden");
              java.lang.reflect.Method region =
                  Class.forName("com.yolo.dyfusion.hook.UiProfile")
                      .getDeclaredMethod(
                          "region", android.view.View.class, String.class, String.class);
              region.setAccessible(true);
              android.view.View top = new android.view.View(getTargetContext());
              top.setId(2131372171);
              page.addView(top);
              top.layout(0, 148, 1260, 298);
              if (!region.invoke(null, top, "", "40.7.0").equals("top"))
                throw new AssertionError("top strip mapping");
              android.view.View bottom =
                  new android.view.View(getTargetContext()) {
                    @Override
                    public void getLocationOnScreen(int[] location) {
                      location[0] = 0;
                      location[1] = 2634;
                    }
                  };
              bottom.setId(2131392899);
              page.addView(bottom);
              bottom.layout(0, 0, 1260, 166);
              if (!region.invoke(null, bottom, "", "40.7.0").equals("bottom"))
                throw new AssertionError("bottom strip mapping");
              bottom.setId(2131366986);
              if (!region.invoke(null, bottom, "", "40.7.0").equals("bottom"))
                throw new AssertionError("bottom skin mapping");
              control.setId(2131389320);
              if (!identify.invoke(null, control, "40.7.0").equals("listen_disc"))
                throw new AssertionError("listen turntable target");
              control.setId(2131366243);
              if (!identify.invoke(null, control, "40.7.0").equals("detail_back"))
                throw new AssertionError("detail back target");
              if (!identify.invoke(null, control, "40.6.0").equals(""))
                throw new AssertionError("cross-version guessed ID");
            } catch (Throwable e) {
              uiFailure[0] = e;
            }
          });
      if (uiFailure[0] != null) throw uiFailure[0];
      count += 18;
      for (int n = 0; n < Catalog.all(getTargetContext()).length(); n++)
        if (Catalog.all(getTargetContext()).getJSONObject(n).getString("type").equals("planned"))
          throw new AssertionError("pending entry remains");
      count++;

      JSONObject migrated =
          Catalog.migrate(
              new JSONObject(
                  "{\"comment_copy_menu\":true,\"hide_comment_reply\":true,\"highest_play\":true,\"floating_menu\":false}"));
      if (migrated.has("comment_copy_menu")
          || migrated.has("hide_comment_reply")
          || migrated.has("highest_play")
          || !migrated.optString("quality_mode").equals("highest")
          || migrated.optBoolean("floating_menu", true))
        throw new AssertionError("migration lost retained keys");
      count++;
      JSONObject wrong = new JSONObject("{\"quality_mode\":\"invented\"}");
      try {
        Catalog.validate(getTargetContext(), wrong);
        throw new AssertionError("quality enum accepted");
      } catch (IllegalArgumentException expected) {
      }
      count++;
      wrong = new JSONObject("{\"gestures\":{\"9\":{\"double\":\"menu\"}}}");
      try {
        Catalog.validate(getTargetContext(), wrong);
        throw new AssertionError("bad grid cell accepted");
      } catch (IllegalArgumentException expected) {
      }
      count++;
      wrong = new JSONObject("{\"gestures\":{\"4\":{\"double\":\"spam\"}}}");
      try {
        Catalog.validate(getTargetContext(), wrong);
        throw new AssertionError("bad grid action accepted");
      } catch (IllegalArgumentException expected) {
      }
      count++;
      wrong = new JSONObject("{\"custom_controls\":{\"2131374345\":{\"play_alpha\":101}}}");
      try {
        Catalog.validate(getTargetContext(), wrong);
        throw new AssertionError("alpha rule accepted");
      } catch (IllegalArgumentException expected) {
      }
      count++;
      wrong = new JSONObject("{\"custom_controls\":{\"bad\":{\"hidden\":true}}}");
      try {
        Catalog.validate(getTargetContext(), wrong);
        throw new AssertionError("non-ID rule accepted");
      } catch (IllegalArgumentException expected) {
      }
      count++;
      JSONObject good =
          new JSONObject(
              "{\"gestures\":{\"4\":{\"double\":\"menu\",\"long\":\"default\"}},\"custom_controls\":{\"2131374345\":{\"play_alpha\":0,\"pause_alpha\":100,\"hidden\":false}},\"quality_mode\":\"direct\"}");
      Catalog.validate(getTargetContext(), good);
      count++;
      final Throwable[] tabFailure = new Throwable[1];
      runOnMainSync(
          () -> {
            try {
              android.widget.LinearLayout row = new android.widget.LinearLayout(getTargetContext());
              row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
              String[] names = {"首页", "商城", "关注"};
              android.widget.TextView[] cells = new android.widget.TextView[3];
              for (int n = 0; n < 3; n++) {
                android.widget.TextView t = new android.widget.TextView(getTargetContext());
                t.setText(names[n]);
                t.setClickable(true);
                t.setEnabled(true);
                row.addView(t, new android.widget.LinearLayout.LayoutParams(100, 50));
                cells[n] = t;
              }
              row.measure(
                  android.view.View.MeasureSpec.makeMeasureSpec(
                      300, android.view.View.MeasureSpec.EXACTLY),
                  android.view.View.MeasureSpec.makeMeasureSpec(
                      50, android.view.View.MeasureSpec.EXACTLY));
              row.layout(0, 0, 300, 50);
              com.yolo.dyfusion.hook.TabRows tabs = new com.yolo.dyfusion.hook.TabRows();
              if (tabs.apply(row, "商城") != 1
                  || cells[1].getVisibility() != android.view.View.GONE
                  || cells[1].isEnabled()
                  || cells[1].isClickable()) throw new AssertionError("tab not collapsed");
              if (cells[0].getRight() != cells[2].getLeft() || cells[2].getRight() != 300)
                throw new AssertionError("tab gap remains");
              tabs.restore();
              if (cells[1].getVisibility() != android.view.View.VISIBLE
                  || !cells[1].isEnabled()
                  || !cells[1].isClickable()) throw new AssertionError("tab restore");
              android.view.View v = new android.view.View(getTargetContext());
              v.setTranslationY(5);
              v.setScaleX(.8f);
              v.setScaleY(.7f);
              com.yolo.dyfusion.hook.UiEdits edit = new com.yolo.dyfusion.hook.UiEdits();
              edit.begin();
              edit.moveY(v, 40);
              edit.scale(v, .5f);
              edit.finish();
              if (Math.abs(v.getTranslationY() - 45) > .01
                  || Math.abs(v.getScaleX() - .4f) > .01
                  || Math.abs(v.getScaleY() - .35f) > .01) throw new AssertionError("player edits");
              edit.begin();
              edit.finish();
              if (Math.abs(v.getTranslationY() - 5) > .01
                  || Math.abs(v.getScaleX() - .8f) > .01
                  || Math.abs(v.getScaleY() - .7f) > .01)
                throw new AssertionError("player restore");
            } catch (Throwable ex) {
              tabFailure[0] = ex;
            }
          });
      if (tabFailure[0] != null) throw tabFailure[0];
      count += 5;
      final Throwable[] positionFailure = new Throwable[1];
      runOnMainSync(
          () -> {
            try {
              android.view.View v = new android.view.View(getTargetContext());
              v.setTranslationY(0);
              com.yolo.dyfusion.hook.UiEdits edits = new com.yolo.dyfusion.hook.UiEdits();
              edits.begin();
              edits.moveY(v, 86);
              edits.finish();
              if (edits.appliedOffset(v) != 86) throw new AssertionError("existing offset missing");
              v.setTranslationY(0);
              if (edits.appliedOffset(v) != 0)
                throw new AssertionError("native reset treated as previous offset");
              edits.begin();
              edits.moveY(v, 86);
              edits.finish();
              if (v.getTranslationY() != 86)
                throw new AssertionError("offset accumulated after native reset");
              edits.restore();
              if (v.getTranslationY() != 0)
                throw new AssertionError("native baseline not restored");
            } catch (Throwable ex) {
              positionFailure[0] = ex;
            }
          });
      if (positionFailure[0] != null) throw positionFailure[0];
      count += 4;
      result.putString(
          "result",
          "PASS "
              + count
              + " Android assertions; no host data accessed; tests do not enable features");
      finish(-1, result);
    } catch (Throwable e) {
      result.putString("result", "FAIL after " + count + ": " + e);
      finish(0, result);
    }
  }
}
