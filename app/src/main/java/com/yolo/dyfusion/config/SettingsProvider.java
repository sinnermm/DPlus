package com.yolo.dyfusion.config;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import java.util.*;

public final class SettingsProvider extends ContentProvider {
  public static final Uri URI = Uri.parse("content://com.yolo.dyfusion.bridge");
  public static final Set<String> HOSTS =
      new HashSet<>(
          Arrays.asList(
              "com.ss.android.ugc.aweme",
              "com.ss.android.ugc.aweme.lite",
              "com.ss.android.ugc.aweme.mobile"));

  @Override
  public boolean onCreate() {
    return true;
  }

  private void checkCaller() {
    if (Binder.getCallingUid() == android.os.Process.myUid()) return;
    String[] ps = getContext().getPackageManager().getPackagesForUid(Binder.getCallingUid());
    if (ps != null) for (String p : ps) if (HOSTS.contains(p)) return;
    throw new SecurityException("Bridge 仅限模块和目标宿主读取");
  }

  @Override
  public Bundle call(String method, String arg, Bundle extras) {
    checkCaller();
    Bundle b = new Bundle();
    if ("settings".equals(method)) {
      b.putString("json", Catalog.read(getContext()).toString());
      return b;
    }
    if ("update".equals(method)) {
      String s = extras == null ? "" : extras.getString("json", "");
      if (s.length() > 32768) throw new IllegalArgumentException("配置过大");
      try {
        Catalog.save(getContext(), new org.json.JSONObject(s));
      } catch (org.json.JSONException e) {
        throw new IllegalArgumentException(e);
      }
      return b;
    }
    if ("report".equals(method)) {
      String s = extras == null ? "" : extras.getString("json", "");
      if (s.length() > 32768) throw new IllegalArgumentException("诊断过大");
      getContext()
          .getSharedPreferences("runtime", 0)
          .edit()
          .putString("report", s)
          .putLong("updated", System.currentTimeMillis())
          .apply();
      return b;
    }
    throw new IllegalArgumentException("未知调用");
  }

  @Override
  public Cursor query(Uri u, String[] p, String s, String[] a, String o) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getType(Uri u) {
    return "application/json";
  }

  @Override
  public Uri insert(Uri u, ContentValues v) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int delete(Uri u, String s, String[] a) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int update(Uri u, ContentValues v, String s, String[] a) {
    throw new UnsupportedOperationException();
  }
}
