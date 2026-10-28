package com.yolo.dyfusion.hook;

import android.view.View;
import java.util.*;

/** Version-bound resource IDs checked against the user's exact 40.7.0 base.apk. */
final class UiProfile {
  private static final java.util.concurrent.ConcurrentHashMap<Class<?>, Set<String>> CLASS_NAMES =
      new java.util.concurrent.ConcurrentHashMap<>();
  private static final java.util.concurrent.ConcurrentHashMap<Integer, String> RESOURCE_NAMES =
      new java.util.concurrent.ConcurrentHashMap<>();

  static boolean named(View v, String... names) {
    Set<String> known =
        CLASS_NAMES.computeIfAbsent(
            v.getClass(),
            type -> {
              Set<String> out = new HashSet<>();
              for (Class<?> c = type; c != null; c = c.getSuperclass()) out.add(c.getSimpleName());
              return out;
            });
    for (String name : names) if (known.contains(name)) return true;
    return false;
  }

  static String target(View v, String version) {
    if (version.equals("40.7.0")) {
      boolean action = narrowAction(v);
      switch (v.getId()) {
        case 2131374345:
        case 2131374358:
          if (action) return "like";
          break;
        case 2131371412:
        case 2131371282:
          if (action) return "comment";
          break;
        case 2131377254:
          if (v.getWidth() < v.getRootView().getWidth() * .8f) return "detail_search";
          break;
        case 2131388617:
          if (v.getHeight() < v.getRootView().getHeight() * .18f) return "detail_search";
          break;
        case 2131389320:
          if (narrowAction(v)) return "listen_disc";
          break;
        case 2131366243:
          if (narrowAction(v)) return "detail_back";
          break;
        case 2131396192:
          if (v.getHeight() > 0 && v.getHeight() < v.getRootView().getHeight() * .18f)
            return "comment_input";
          break;
        case 2131371070: // Actual collection outer layout ewg
        case 2131370062: // Actual clickable collection layout d_j
          if (action) return "collect";
          break;
        case 2131400515:
        case 2131401927:
          if (action) return "share";
          break;
        case 2131366223:
        case 2131366221:
        case 2131415727:
          if (action) return "avatar";
          break;
        case 2131383171:
          if (v.getHeight() < v.getRootView().getHeight() * .3f) return "title_tag";
          break;
        case 2131370907:
          if (v.getHeight() < v.getRootView().getHeight() * .3f) return "coauthors";
          break;
        case 2131379756:
          if (action) return "follow";
          break;
        case 2131394647:
        case 2131394009:
          if (action) return "music";
          break;
        case 2131378165:
          if (v.getHeight() < v.getRootView().getHeight() * .4f) return "desc";
          break;
        case 2131367379:
        case 2131377635:
          if (v.getHeight() > 0
              && v.getHeight() < v.getResources().getDisplayMetrics().density * 32)
            return "seekbar";
          break;
      }
    }
    String resource = entry(v);
    if (resource.equals("co_creators_group")) return "coauthors";
    if (resource.equals("title_tag_container")) return "title_tag";
    if (resource.contains("color_ring")
        || resource.contains("colorring")
        || named(v, "AvatarColorRingView")) return "rings";
    if (named(v, "FeedAvatarView")) return "avatar";
    if (named(v, "VideoDiggView", "IconDiggView")) return "like";
    if (named(v, "VideoCommentCountView", "FeedCommentImageView")) return "comment";
    if (named(v, "FeedCollectAnimationView", "FeedCollectView")) return "collect";
    if (named(v, "VideoShareView", "FeedShareImageView", "FeedShareIconView")) return "share";
    if (named(v, "MusicCoverView", "BaseVideoMusicView")) return "music";
    if (named(v, "AwemeIntroInfoLayout")) return "desc";
    if (named(v, "ProSeekBarView", "FeedSeekBar")) return "seekbar";
    if (named(v, "FollowLazyDmtStatusView")) return "follow";
    return "";
  }

  static boolean narrowAction(View v) {
    return v.getWidth() > 0
        && v.getWidth() < v.getRootView().getWidth() * .5f
        && v.getHeight() > 0
        && v.getHeight() < v.getRootView().getHeight() * .4f;
  }

  @android.annotation.SuppressLint("ResourceType")
  static String region(View v, String target, String version) {
    if (version.equals("40.7.0")) {
      if (v.getId() == 2131402150 && narrowAction(v)) return "right";
      if (v.getId() == 2131366370 && v.getHeight() < v.getRootView().getHeight() * .4f)
        return "left";
      if (v.getId() == 2131372171 && v.getHeight() < v.getRootView().getHeight() * .25f)
        return "top";
      if ((v.getId() == 2131392899 || v.getId() == 2131366986)
          && v.getHeight() > 0
          && v.getHeight() < v.getRootView().getHeight() * .2f) {
        int[] p = new int[2];
        v.getLocationOnScreen(p);
        if (p[1] > v.getRootView().getHeight() * .65f) return "bottom";
      }
    }
    if (named(v, "MainTitleBarContainer", "MultiTabsContainer")) return "top";
    if (named(v, "MainBottomTabContainer")) return "bottom";
    if (named(v, "FeedRightScaleView")) return "right";
    if (target.equals("desc")) return "left";
    return "";
  }

  @android.annotation.SuppressLint("ResourceType")
  static String tabRow(View v, String version) {
    if (version.equals("40.7.0")) {
      if (v.getId() == 2131402378 && v instanceof android.view.ViewGroup) return "top";
      if (v.getId() == 2131392899
          && v instanceof android.view.ViewGroup
          && v.getHeight() > 0
          && v.getHeight() < v.getRootView().getHeight() * .20f) return "bottom";
    }
    return "";
  }

  static String entry(View v) {
    int id = v.getId();
    if (id == View.NO_ID || id == 0) return "";
    return RESOURCE_NAMES.computeIfAbsent(
        id,
        key -> {
          try {
            return v.getResources().getResourceEntryName(key).toLowerCase(Locale.ROOT);
          } catch (Throwable e) {
            return "";
          }
        });
  }
}
