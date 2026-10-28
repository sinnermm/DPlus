import com.yolo.dyfusion.core.*;
import java.util.*;

public final class CoreTest {
  static int checks;

  static void ok(boolean b, String name) {
    checks++;
    if (!b) throw new AssertionError(name);
  }

  static void rejects(Runnable r, String name) {
    try {
      r.run();
      throw new AssertionError(name);
    } catch (IllegalArgumentException expected) {
      checks++;
    }
  }

  public static void main(String[] a) throws Exception {
    ok(Rules.tokens("a,b，c\nd").size() == 4, "token delimiters");
    ok(Rules.tokens("a,a,").size() == 1, "dedup");
    ok(Rules.contains("HELLO 世界", "hello,不可能"), "casefold");
    ok(!Rules.contains("Hello", ""), "empty keyword");
    ok(Rules.exact("123", "123,456"), "exact UID");
    ok(!Rules.exact("12", "123"), "no partial UID");
    ok(Rules.safeName("a/b:c?d\"").equals("a_b_c_d_"), "filename unsafe chars");
    ok(Rules.safeName("..").equals("media"), "dots");
    ok(Rules.safeName("CON").equals("_CON"), "reserved filename");
    ok(Rules.safeName("x".repeat(300)).length() == 100, "bounded name");
    ok(Rules.folder("DyFusion/Video").equals("DyFusion/Video"), "relative folder");
    rejects(() -> Rules.folder("../escape"), "parent");
    rejects(() -> Rules.folder("/absolute"), "absolute");
    rejects(() -> Rules.folder("C:/absolute"), "drive");
    rejects(() -> Rules.folder("a//b"), "empty segment");
    Map<String, Object> c = new HashMap<>();
    c.put("enabled", true);
    c.put("filter_ads", true);
    ok(Rules.blocked(true, false, false, false, "", "", -1, -1, c), "ad filter");
    c.put("enabled", false);
    ok(!Rules.blocked(true, false, false, false, "", "", 0, 0, c), "disabled");
    c.put("enabled", true);
    c.put("min_likes", 20);
    ok(!Rules.blocked(false, false, false, false, "", "", -1, -1, c), "unknown safe");
    ok(Rules.blocked(false, false, false, false, "", "", 10, 5000, c), "min likes");
    c.put("min_likes", 0);
    c.put("max_duration", 5);
    ok(Rules.blocked(false, false, false, false, "", "", 100, 5001, c), "duration ms");
    ok(!Rules.blocked(false, true, false, false, "", "", 100, 999999, c), "live duration ignored");
    String link = ShareLinks.extract("分享 https://v.douyin.com/abc/。 长按复制");
    ok(link.equals("https://v.douyin.com/abc/"), "share trim");
    rejects(() -> ShareLinks.extract("https://douyin.com.evil.test/"), "spoof suffix");
    rejects(() -> ShareLinks.extract("https://evil.douyin.com@evil.test/"), "userinfo spoof");
    rejects(() -> ShareLinks.extract("http://v.douyin.com/abc"), "HTTP");
    ok(
        ShareLinks.id("https://www.douyin.com/video/123456789012345").equals("123456789012345"),
        "video ID");
    ok(ShareLinks.id("https://v.douyin.com/abc/").isEmpty(), "no invented ID");
    String json = "{\"a\":\"} \\\" {\",\"b\":{\"c\":1}} trailing";
    ok(
        ShareLinks.jsonObjectAt(json, 0).equals(json.substring(0, json.indexOf(" trailing"))),
        "balanced JSON");
    ok(ShareLinks.jsonObjectAt("{bad", 0).isEmpty(), "incomplete JSON");
    ok(GestureMath.cell(0, 0, 300, 600) == 0, "grid upper left");
    ok(GestureMath.cell(150, 300, 300, 600) == 4, "grid center");
    ok(GestureMath.cell(299, 599, 300, 600) == 8, "grid lower right");
    ok(GestureMath.cell(300, 500, 300, 600) == -1, "grid outside");
    ok(GestureMath.cell(0, 0, 0, 600) == -1, "grid no dimensions");
    ok(GestureMath.tap(10, 10, 11, 11, 3), "tap slop");
    ok(!GestureMath.tap(10, 10, 100, 100, 3), "swipe not tap");
    ok(
        QualityRank.compare(1920, 1080, 30, 200, 10, 1280, 720, 60, 300, 20) > 0,
        "resolution before bitrate");
    ok(
        QualityRank.compare(1920, 1080, 60, 200, 10, 1920, 1080, 30, 300, 20) > 0,
        "fps before bitrate");
    ok(
        QualityRank.compare(1920, 1080, 30, 300, 10, 1920, 1080, 30, 200, 20) > 0,
        "bitrate before bytes");
    ok(QualityRank.compare(0, 0, 0, 0, 0, 0, 0, 0, 0, 0) == 0, "unknown ranks tie");
    ok(
        Math.abs(VideoPlacement.offset(0, 2800, 328.5f, 2628, .75f, 0) - 86) < .01,
        "center transformed surface");
    ok(
        Math.abs(VideoPlacement.offset(0, 2800, 950, 700, 1, 0) - 100) < .01,
        "center horizontal video");
    ok(
        Math.abs(VideoPlacement.offset(144, 2656, 800, 700, 1, 35) - 357) < .01,
        "center coordinate origin and micro offset");
    ok(VideoPlacement.offset(0, 0, 0, 0, 0, 12) == 12, "invalid dimensions fallback");
    System.out.println("PASS " + checks + " core assertions");
  }
}
