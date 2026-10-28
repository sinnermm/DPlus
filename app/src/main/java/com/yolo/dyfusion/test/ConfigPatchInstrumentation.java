package com.yolo.dyfusion.test;

import android.app.Instrumentation;
import android.os.Bundle;
import com.yolo.dyfusion.config.Catalog;
import org.json.*;

/** Debug-only explicit patch helper; no host app access and no hidden automatic defaults. */
public final class ConfigPatchInstrumentation extends Instrumentation {
  String patch;

  @Override
  public void onCreate(Bundle args) {
    super.onCreate(args);
    patch = args.getString("patch_b64", "");
    start();
  }

  @Override
  public void onStart() {
    Bundle out = new Bundle();
    try {
      JSONObject before = Catalog.read(getTargetContext());
      JSONObject input =
          new JSONObject(
              new String(
                  android.util.Base64.decode(patch, android.util.Base64.DEFAULT),
                  java.nio.charset.StandardCharsets.UTF_8));
      JSONObject validated = Catalog.validate(getTargetContext(), input);
      java.util.Iterator<String> it = validated.keys();
      while (it.hasNext()) {
        String key = it.next();
        before.put(key, validated.get(key));
      }
      Catalog.save(getTargetContext(), before);
      out.putString("result", "PATCHED " + validated.length() + " known module settings");
      finish(-1, out);
    } catch (Throwable e) {
      out.putString("result", "FAIL: " + e);
      finish(0, out);
    }
  }
}
