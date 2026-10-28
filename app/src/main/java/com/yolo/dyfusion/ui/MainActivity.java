package com.yolo.dyfusion.ui;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.os.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import com.yolo.dyfusion.config.*;
import com.yolo.dyfusion.media.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

public final class MainActivity extends Activity {
  private static final int INK = 0xFF172B3A, MUTED = 0xFF566879, BLUE = 0xFF176E9B, BG = 0xFFEDF3F7;
  private LinearLayout body, root;
  private TextView status;
  private JSONObject config;
  private String group = "基础", search = "";
  private EditText searchBox;
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private final Handler main = new Handler(Looper.getMainLooper());
  private int activeJobs;

  public static boolean isModuleActive() {
    return false;
  }

  @Override
  public void onCreate(Bundle saved) {
    super.onCreate(saved);
    config = Catalog.read(this);
    root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(BG);
    root.setPadding(dp(18), dp(12), dp(18), 0);
    setContentView(root);
    TextView title = text("抖+", 27, INK);
    title.setTypeface(null, android.graphics.Typeface.BOLD);
    root.addView(title);
    status = text("独立整合 · 0.3.3 详情页入口修复\n抖音 40.7.0 | Android 14 参考环境", 13, MUTED);
    status.setPadding(0, dp(7), 0, dp(14));
    root.addView(status);
    HorizontalScrollView navigation = new HorizontalScrollView(this);
    navigation.setHorizontalScrollBarEnabled(false);
    LinearLayout nav = new LinearLayout(this);
    for (String label : new String[] {"功能", "解析 / 下载", "适配诊断", "配置"}) {
      Button button = new Button(this);
      button.setText(label);
      button.setTextSize(13);
      button.setOnClickListener(
          v -> {
            if (label.equals("功能")) settings();
            else if (label.equals("解析 / 下载")) parser();
            else if (label.equals("适配诊断")) diagnostics();
            else backups();
          });
      nav.addView(button);
    }
    navigation.addView(nav);
    root.addView(navigation);
    ScrollView scroll = new ScrollView(this);
    body = new LinearLayout(this);
    body.setOrientation(LinearLayout.VERTICAL);
    body.setPadding(0, dp(10), 0, dp(24));
    scroll.addView(body);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    settings();
    handle(getIntent());
  }

  @Override
  public void onResume() {
    super.onResume();
    config = Catalog.read(this);
  }

  @Override
  public void onNewIntent(Intent i) {
    super.onNewIntent(i);
    setIntent(i);
    handle(i);
  }

  private void handle(Intent i) {
    try {
      String media = i.getStringExtra("media_json");
      if (media != null) {
        if (media.length() > 128 * 1024) throw new IllegalArgumentException("媒体数据过大");
        showMedia(new JSONObject(media));
      } else if (Intent.ACTION_SEND.equals(i.getAction()))
        parserWith(i.getStringExtra(Intent.EXTRA_TEXT));
      else if (Intent.ACTION_VIEW.equals(i.getAction()) && i.getData() != null)
        parserWith(i.getData().toString());
    } catch (Exception e) {
      error(e);
    }
  }

  private int dp(int n) {
    return (int) (getResources().getDisplayMetrics().density * n + .5f);
  }

  private TextView text(String s, int size, int color) {
    TextView t = new TextView(this);
    t.setText(s);
    t.setTextSize(size);
    t.setTextColor(color);
    return t;
  }

  private void section(String s) {
    TextView t = text(s, 19, INK);
    t.setTypeface(null, android.graphics.Typeface.BOLD);
    t.setPadding(0, dp(12), 0, dp(8));
    body.addView(t);
  }

  private void note(String s) {
    TextView t = text(s, 13, MUTED);
    t.setPadding(0, dp(6), 0, dp(12));
    t.setTextIsSelectable(true);
    body.addView(t);
  }

  private void action(String s, Runnable r) {
    Button b = new Button(this);
    b.setText(s);
    b.setTextColor(BLUE);
    b.setAllCaps(false);
    b.setOnClickListener(v -> r.run());
    body.addView(b, new LinearLayout.LayoutParams(-1, dp(52)));
  }

  private void toast(String s) {
    Toast.makeText(this, s, Toast.LENGTH_LONG).show();
  }

  private void error(Throwable e) {
    new AlertDialog.Builder(this)
        .setTitle("未完成操作")
        .setMessage(String.valueOf(e.getMessage()))
        .setPositiveButton("知道了", null)
        .show();
  }

  private void settings() {
    body.removeAllViews();
    section("功能设置");
    note("评论分组已移除。标签移除后重新排位；相关搜索、合集可分别控制播放与暂停透明度；手势默认不干预原行为。");
    HorizontalScrollView h = new HorizontalScrollView(this);
    LinearLayout tabs = new LinearLayout(this);
    Set<String> groups = new LinkedHashSet<>();
    JSONArray a = Catalog.all(this);
    for (int i = 0; i < a.length(); i++) groups.add(a.optJSONObject(i).optString("group"));
    for (String g : groups) {
      Button b = new Button(this);
      b.setText(g);
      b.setTextSize(12);
      b.setTextColor(g.equals(group) ? BLUE : MUTED);
      b.setOnClickListener(
          v -> {
            group = g;
            search = "";
            settings();
          });
      tabs.addView(b);
    }
    h.addView(tabs);
    body.addView(h);
    searchBox = new EditText(this);
    searchBox.setSingleLine(true);
    searchBox.setTextSize(14);
    searchBox.setHint("在当前分组搜索功能");
    searchBox.setText(search);
    body.addView(searchBox);
    LinearLayout list = new LinearLayout(this);
    list.setOrientation(LinearLayout.VERTICAL);
    body.addView(list);
    renderRows(list);
    searchBox.addTextChangedListener(
        new TextWatcher() {
          public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

          public void onTextChanged(CharSequence s, int start, int before, int count) {
            search = s.toString();
            renderRows(list);
          }

          public void afterTextChanged(Editable e) {}
        });
  }

  private void renderRows(LinearLayout list) {
    list.removeAllViews();
    JSONArray a = Catalog.all(this);
    int count = 0;
    for (int i = 0; i < a.length(); i++) {
      JSONObject f = a.optJSONObject(i);
      if (!f.optString("group").equals(group) || !f.optString("title").contains(search)) continue;
      count++;
      String key = f.optString("key"), type = f.optString("type");
      LinearLayout row = new LinearLayout(this);
      row.setGravity(Gravity.CENTER_VERTICAL);
      row.setPadding(dp(8), dp(13), dp(8), dp(13));
      row.setBackgroundColor(-1);
      LinearLayout labels = new LinearLayout(this);
      labels.setOrientation(LinearLayout.VERTICAL);
      TextView title = text(f.optString("title"), 16, INK);
      labels.addView(title);
      String state = "已编码 / 宿主效果以诊断与实测为准";
      TextView summary = text(state + "\n" + f.optString("summary"), 12, MUTED);
      summary.setPadding(0, dp(5), dp(8), 0);
      labels.addView(summary);
      row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
      if (key.equals("gestures")) {
        TextView value = text("设置九宫格", 13, BLUE);
        row.addView(value);
        row.setOnClickListener(v -> gestureGrid());
      } else if (type.equals("labels")) {
        TextView value = text("多选", 13, BLUE);
        row.addView(value);
        row.setOnClickListener(v -> labelsDialog(f));
      } else if (type.equals("choice")) {
        TextView value = text(qualityName(config.optString("quality_mode", "native")), 13, BLUE);
        row.addView(value);
        row.setOnClickListener(v -> qualityDialog());
      } else if (type.equals("controls")) {
        TextView value = text("打开方法", 13, BLUE);
        row.addView(value);
        row.setOnClickListener(
            v ->
                new AlertDialog.Builder(this)
                    .setTitle("控件扫描与预览")
                    .setMessage(
                        "在抖音普通作品页通过音量键菜单、悬浮球或分享面板打开“抖+”，选择“扫描与预览控件”。扫描只记录"
                            + " ID、资源名和类，不收集正文或账户信息。未命中的控件可按实际扫描结果单独隐藏。")
                    .setPositiveButton("知道了", null)
                    .show());
      } else if (type.equals("bool")) {
        TextView value = text("设置九宫格", 13, BLUE);
        row.addView(value);
        row.setOnClickListener(v -> gestureGrid());
      } else if (type.equals("labels")) {
        TextView value = text("多选", 13, BLUE);
        row.addView(value);
        row.setOnClickListener(v -> labelsDialog(f));
      } else if (type.equals("choice")) {
        TextView value = text(qualityName(config.optString("quality_mode", "native")), 13, BLUE);
        row.addView(value);
        row.setOnClickListener(v -> qualityDialog());
      } else if (type.equals("controls")) {
        TextView value = text("打开方法", 13, BLUE);
        row.addView(value);
        row.setOnClickListener(
            v ->
                new AlertDialog.Builder(this)
                    .setTitle("控件扫描与预览")
                    .setMessage(
                        "在抖音普通作品页通过音量键菜单、悬浮球或分享面板打开“抖+”，选择“扫描与预览控件”。扫描只记录"
                            + " ID、资源名和类，不收集正文或账户信息。未命中的控件可按实际扫描结果单独隐藏。")
                    .setPositiveButton("知道了", null)
                    .show());
      } else if (type.equals("bool")) {
        Switch sw = new Switch(this);
        sw.setContentDescription(f.optString("title"));
        sw.setChecked(config.optBoolean(key));
        sw.setOnCheckedChangeListener(
            (v, checked) -> {
              try {
                JSONObject next = new JSONObject(config.toString());
                next.put(key, checked);
                if (key.equals("auto_next") && checked) next.put("pause_at_end", false);
                if (key.equals("pause_at_end") && checked) next.put("auto_next", false);
                if (key.equals("video_center") && checked) next.put("video_offset_dp", 0);
                Catalog.save(this, next);
                config = next;
              } catch (Exception e) {
                v.setOnCheckedChangeListener(null);
                sw.setChecked(config.optBoolean(key));
                settings();
                error(e);
              }
            });
        row.addView(sw);
      } else {
        TextView value = text(config.optString(key, ""), 13, BLUE);
        String shown = String.valueOf(config.opt(key));
        value.setText(
            shown.isEmpty() ? "设置" : shown.length() > 14 ? shown.substring(0, 14) + "…" : shown);
        row.addView(value);
        row.setOnClickListener(v -> edit(f));
      }
      list.addView(row);
      View line = new View(this);
      line.setBackgroundColor(BG);
      list.addView(line, new LinearLayout.LayoutParams(-1, dp(2)));
    }
    if (count == 0) {
      TextView t = text("没有匹配功能", 14, MUTED);
      list.addView(t);
    }
  }

  private static final String[] GESTURE_ACTIONS = {
    "default",
    "ignore",
    "menu",
    "play_pause",
    "speed",
    "auto_next",
    "media",
    "download",
    "clean",
    "settings",
    "comment"
  };
  private static final String[] GESTURE_LABELS = {
    "默认（保留抖音行为）",
    "忽略该手势",
    "打开抖+菜单",
    "播放 / 暂停",
    "切换默认倍速开关",
    "开关自动下一条",
    "作品媒体 / 信息",
    "选择原画下载",
    "切换清爽模式",
    "打开模块设置",
    "打开评论"
  };

  private String gestureLabel(String key) {
    for (int n = 0; n < GESTURE_ACTIONS.length; n++)
      if (GESTURE_ACTIONS[n].equals(key)) return GESTURE_LABELS[n];
    return GESTURE_LABELS[0];
  }

  @android.annotation.SuppressLint("SetTextI18n")
  private void gestureGrid() {
    body.removeAllViews();
    section("九宫格手势");
    note("双击和长按分别设置；默认区域保留抖音手势。点击控件、边缘系统手势和滑动优先保留。先配置动作，再开启“启用九宫格手势”。");
    JSONObject grid = config.optJSONObject("gestures");
    if (grid == null) grid = new JSONObject();
    final JSONObject current = grid;
    String[] names = {"左上", "中上", "右上", "左中", "正中", "右中", "左下", "中下", "右下"};
    for (int rowIndex = 0; rowIndex < 3; rowIndex++) {
      LinearLayout row = new LinearLayout(this);
      for (int column = 0; column < 3; column++) {
        int index = rowIndex * 3 + column;
        JSONObject cell = current.optJSONObject(String.valueOf(index));
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setTextSize(12);
        b.setTextColor(BLUE);
        b.setText(
            names[index]
                + "\n双击："
                + gestureLabel(cell == null ? "default" : cell.optString("double", "default"))
                + "\n长按："
                + gestureLabel(cell == null ? "default" : cell.optString("long", "default")));
        b.setOnClickListener(
            v ->
                new AlertDialog.Builder(this)
                    .setTitle(names[index] + "区域")
                    .setItems(
                        new String[] {"设置双击", "设置长按"},
                        (d, which) -> chooseGesture(index, which == 0 ? "double" : "long"))
                    .show());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(140), 1);
        lp.setMargins(dp(2), dp(2), dp(2), dp(2));
        row.addView(b, lp);
      }
      body.addView(row);
    }
    action(
        config.optBoolean("gestures_enabled", false) ? "关闭九宫格手势" : "开启九宫格手势",
        () -> {
          try {
            JSONObject next = new JSONObject(config.toString());
            next.put("gestures_enabled", !config.optBoolean("gestures_enabled", false));
            Catalog.save(this, next);
            config = next;
            gestureGrid();
          } catch (Exception e) {
            error(e);
          }
        });
    action("返回功能设置", this::settings);
  }

  private void chooseGesture(int cell, String type) {
    new AlertDialog.Builder(this)
        .setTitle(type.equals("double") ? "双击动作" : "长按动作")
        .setItems(
            GESTURE_LABELS,
            (d, index) -> {
              try {
                JSONObject next = new JSONObject(config.toString());
                JSONObject grid = next.optJSONObject("gestures");
                if (grid == null) grid = new JSONObject();
                JSONObject value = grid.optJSONObject(String.valueOf(cell));
                if (value == null) value = new JSONObject();
                value.put(type, GESTURE_ACTIONS[index]);
                grid.put(String.valueOf(cell), value);
                next.put("gestures", grid);
                Catalog.save(this, next);
                config = next;
                gestureGrid();
              } catch (Exception e) {
                error(e);
              }
            })
        .show();
  }

  private String qualityName(String mode) {
    return mode.equals("highest") ? "最高可用档" : mode.equals("direct") ? "最高档直链" : "跟随抖音";
  }

  private void qualityDialog() {
    String[] modes = {"native", "highest", "direct"};
    new AlertDialog.Builder(this)
        .setTitle("默认播放画质")
        .setMessage("最高可用档与直链可能增加流量；最高可用不保证是原始上传文件。重新进入作品后生效。")
        .setSingleChoiceItems(
            new String[] {"跟随抖音", "最高可用档", "最高档文件直链"},
            Arrays.asList(modes).indexOf(config.optString("quality_mode", "native")),
            (d, which) -> {
              try {
                JSONObject next = new JSONObject(config.toString());
                next.put("quality_mode", modes[which]);
                Catalog.save(this, next);
                config = next;
                d.dismiss();
                settings();
              } catch (Exception e) {
                error(e);
              }
            })
        .setNegativeButton("取消", null)
        .show();
  }

  private void labelsDialog(JSONObject feature) {
    String key = feature.optString("key");
    LinkedHashSet<String> options = new LinkedHashSet<>();
    JSONArray supplied = feature.optJSONArray("options");
    if (supplied != null)
      for (int n = 0; n < supplied.length(); n++) options.add(supplied.optString(n));
    else if (key.equals("hide_top_tabs"))
      options.addAll(Arrays.asList("直播", "热点", "团购", "精选", "经验", "同城", "关注", "商城", "推荐"));
    else options.addAll(Arrays.asList("首页", "朋友", "发布", "消息", "我", "商城", "同城", "探索"));
    Set<String> chosen = com.yolo.dyfusion.core.Rules.tokens(config.optString(key, ""));
    options.addAll(chosen);
    String[] names = options.toArray(new String[0]);
    boolean[] checked = new boolean[names.length];
    for (int i = 0; i < names.length; i++) checked[i] = chosen.contains(names[i]);
    new AlertDialog.Builder(this)
        .setTitle(feature.optString("title"))
        .setMultiChoiceItems(names, checked, (d, n, value) -> checked[n] = value)
        .setNeutralButton("手动输入", (d, which) -> edit(feature))
        .setNegativeButton("取消", null)
        .setPositiveButton(
            "保存",
            (d, which) -> {
              try {
                List<String> selected = new ArrayList<>();
                for (int n = 0; n < names.length; n++) if (checked[n]) selected.add(names[n]);
                JSONObject next = new JSONObject(config.toString());
                next.put(key, android.text.TextUtils.join(",", selected));
                Catalog.save(this, next);
                config = next;
                settings();
              } catch (Exception e) {
                error(e);
              }
            })
        .show();
  }

  private void edit(JSONObject f) {
    String key = f.optString("key"), type = f.optString("type");
    EditText e = new EditText(this);
    e.setText(String.valueOf(config.opt(key)));
    if (type.equals("int") || type.equals("number"))
      e.setInputType(
          android.text.InputType.TYPE_CLASS_NUMBER
              | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
              | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
    else e.setMaxLines(8);
    AlertDialog d =
        new AlertDialog.Builder(this)
            .setTitle(f.optString("title"))
            .setMessage(f.optString("summary"))
            .setView(e)
            .setNegativeButton("取消", null)
            .setPositiveButton("保存", null)
            .create();
    d.setOnShowListener(
        x ->
            d.getButton(-1)
                .setOnClickListener(
                    v -> {
                      try {
                        Object value = e.getText().toString();
                        if (type.equals("int")) value = Integer.parseInt((String) value);
                        if (type.equals("number")) value = Double.parseDouble((String) value);
                        JSONObject next = new JSONObject(config.toString());
                        next.put(key, value);
                        Catalog.save(this, next);
                        config = next;
                        d.dismiss();
                        settings();
                      } catch (Exception ex) {
                        e.setError(ex.getMessage());
                      }
                    }));
    d.show();
  }

  private void parser() {
    parserWith("");
  }

  private void parserWith(String input) {
    body.removeAllViews();
    section("公开分享解析");
    note("参考抖析的链接输入与媒体选择流程，独立实现。不使用第三方解析服务器，不发送 Cookie，也不绕过登录、验证码或付费限制。公开页面协议变化时会显示失败。");
    EditText e = new EditText(this);
    e.setHint("粘贴抖音分享文字或 HTTPS 链接");
    e.setText(input);
    e.setMinLines(3);
    e.setTextSize(14);
    body.addView(e);
    action(
        "解析分享链接",
        () -> {
          if (!config.optBoolean("parse_share", true)) {
            toast("公开分享解析开关已关闭");
            return;
          }
          String s = e.getText().toString();
          toast("正在请求公开分享页面");
          worker.execute(
              () -> {
                try {
                  JSONObject media = PublicParser.parse(s);
                  main.post(() -> showMedia(media));
                } catch (Throwable ex) {
                  main.post(() -> error(ex));
                }
              });
        });
    note("在抖音内启用“显示模块悬浮球”或“音量键组合菜单”，也可直接打开当前作品实际媒体，无需公开页面解析。下载只在你选择后入队。");
  }

  private void showMedia(JSONObject media) {
    body.removeAllViews();
    section(media.optString("title").isEmpty() ? "当前作品媒体" : media.optString("title"));
    note(
        "作者："
            + media.optString("author")
            + "\n作品 ID："
            + media.optString("id")
            + "\n"
            + media.optString("source"));
    JSONArray items = media.optJSONArray("items");
    if (items == null || items.length() == 0) {
      note("未提取到可用媒体。可能类字段已变化，或作品本身没有公开媒体地址。请导出诊断用于适配。");
      return;
    }
    for (int i = 0; i < items.length(); i++) {
      JSONObject item = items.optJSONObject(i);
      if (item == null) continue;
      String label = item.optString("label");
      if (!config.optBoolean("download_images", true) && !label.startsWith("视频")) continue;
      if (!config.optBoolean("choose_quality", true) && label.startsWith("视频") && i > 0) continue;
      final int index = i + 1;
      section(index + ". " + item.optString("label"));
      TextView url = text(item.optString("url"), 12, MUTED);
      url.setMaxLines(3);
      url.setTextIsSelectable(true);
      body.addView(url);
      action("下载这项媒体", () -> download(media, item, index));
      action(
          "复制这项直链",
          () -> {
            ((android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE))
                .setPrimaryClip(ClipData.newPlainText("媒体", item.optString("url")));
            toast("已复制；直链可能有时效");
          });
    }
    action(
        "选择全部媒体下载",
        () ->
            new AlertDialog.Builder(this)
                .setTitle("确认批量下载")
                .setMessage("将把 " + items.length() + " 个实际地址加入系统下载队列，可能消耗流量和存储。只下载你有权保存的内容。")
                .setNegativeButton("取消", null)
                .setPositiveButton(
                    "开始下载",
                    (d, w) -> {
                      for (int n = 0; n < items.length(); n++)
                        download(media, items.optJSONObject(n), n + 1);
                    })
                .show());
  }

  private void download(JSONObject media, JSONObject item, int index) {
    if (++activeJobs > 20) {
      activeJobs--;
      toast("待提交任务过多，请稍后");
      return;
    }
    toast("检查真实媒体类型并提交系统下载队列");
    worker.execute(
        () -> {
          try {
            long id = Downloads.enqueue(this, media, item, index);
            main.post(() -> toast("已加入下载队列，ID " + id + "。这不代表下载已完成。"));
          } catch (Throwable e) {
            main.post(() -> error(e));
          } finally {
            main.post(() -> activeJobs--);
          }
        });
  }

  private void diagnostics() {
    body.removeAllViews();
    section("适配诊断");
    note(
        "独立页面激活检测："
            + (isModuleActive() ? "框架已 Hook 本应用" : "未检测到本应用 Hook；宿主是否生效以报告为准")
            + "\n本应用不自动开启或改写其他模块的作用域。");
    String s = getSharedPreferences("runtime", 0).getString("report", "");
    if (s.isEmpty()) {
      note("还没有抖音运行报告。请在 Vekta / LSPosed 中启用本模块，仅勾选抖音和本模块，再强制停止并打开抖音；随后回到这里刷新。");
    } else
      try {
        JSONObject j = new JSONObject(s);
        note(
            "宿主："
                + j.optString("host")
                + "\n版本："
                + j.optString("version")
                + "\n最近报告："
                + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
                    .format(new Date(getSharedPreferences("runtime", 0).getLong("updated", 0))));
        for (String key : new String[] {"installed", "observed", "failures", "ui", "quality"}) {
          section(
              key.equals("installed")
                  ? "已注册 Hook（不是效果验证）"
                  : key.equals("observed")
                      ? "已观测触发"
                      : key.equals("ui")
                          ? "播放页 / 性能诊断"
                          : key.equals("quality") ? "实际画质源诊断" : "未命中 / 运行异常");
          JSONObject values = j.optJSONObject(key);
          if (values != null && values.length() > 0) {
            Iterator<String> it = values.keys();
            while (it.hasNext()) {
              String k = it.next();
              note(k + ": " + values.opt(k));
            }
          } else note("暂无记录");
        }
      } catch (Exception e) {
        error(e);
      }
    action("刷新报告", this::diagnostics);
    action(
        "复制诊断 JSON",
        () -> {
          ((android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE))
              .setPrimaryClip(ClipData.newPlainText("诊断", s));
          toast("已复制，不包含作品或账号数据");
        });
  }

  private void backups() {
    body.removeAllViews();
    section("配置管理");
    note("本地 JSON 导入 / 导出。格式校验失败时不更改当前配置。只接受当前版本的配置键，不复制参考模块的账号或授权信息。");
    action(
        "导出配置文件",
        () -> {
          Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
          i.setType("application/json");
          i.putExtra(Intent.EXTRA_TITLE, "dyfusion-settings.json");
          i.addCategory(Intent.CATEGORY_OPENABLE);
          startActivityForResult(i, 10);
        });
    action(
        "导入配置文件",
        () -> {
          Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
          i.setType("*/*");
          i.addCategory(Intent.CATEGORY_OPENABLE);
          startActivityForResult(i, 11);
        });
    action(
        "关闭全部修改功能",
        () -> {
          try {
            JSONObject j = new JSONObject(config.toString());
            j.put("enabled", false);
            Catalog.save(this, j);
            config = j;
            toast("已关闭总开关。宿主正常时约 2 秒同步；可重启抖音确认。");
          } catch (Exception e) {
            error(e);
          }
        });
    section("最近系统下载状态");
    long id = getSharedPreferences("runtime", 0).getLong("last_download_id", -1);
    if (id < 0) note("未提交过下载任务。");
    else
      try (Cursor c =
          ((DownloadManager) getSystemService(DOWNLOAD_SERVICE))
              .query(new DownloadManager.Query().setFilterById(id))) {
        if (c != null && c.moveToFirst()) {
          int state = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
          note(
              "ID "
                  + id
                  + "："
                  + (state == DownloadManager.STATUS_SUCCESSFUL
                      ? "系统报告已完成"
                      : state == DownloadManager.STATUS_FAILED
                          ? "失败，原因代码 "
                              + c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                          : "排队 / 下载中 / 暂停"));
        } else note("系统中未找到该下载任务。");
      } catch (Exception e) {
        note("无法查询：" + e.getMessage());
      }
  }

  @Override
  protected void onActivityResult(int req, int result, Intent data) {
    super.onActivityResult(req, result, data);
    if (result != RESULT_OK || data == null || data.getData() == null) return;
    try {
      if (req == 10) {
        try (OutputStream o = getContentResolver().openOutputStream(data.getData(), "wt")) {
          if (o == null) throw new IOException("无法写入文件");
          o.write(config.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        toast("配置已导出");
      } else if (req == 11) {
        JSONObject j;
        try (InputStream in = getContentResolver().openInputStream(data.getData())) {
          if (in == null) throw new IOException("无法读取文件");
          j = new JSONObject(PublicParser.readBounded(in, 65536));
        }
        Catalog.save(this, j);
        config = Catalog.read(this);
        toast("配置已导入");
        settings();
      }
    } catch (Exception e) {
      error(e);
    }
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    worker.shutdown();
  }
}
