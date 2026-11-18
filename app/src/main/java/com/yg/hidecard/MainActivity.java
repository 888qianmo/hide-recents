package com.yg.hidecard;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.yg.hidecard.ui.CheckDot;
import com.yg.hidecard.ui.IconCache;
import com.yg.hidecard.ui.Ui;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 配置界面。视觉参照 PUIInstaller 的 Material3 卡片风格：
 * 背景 #F0F1F3、20dp 圆角白卡、#BE3455 强调色、圆形勾选点。
 * 全部代码建视图（APK 无资源表，构建链不需要 aapt2）。
 */
public class MainActivity extends Activity {

    private static final class AppEntry {
        final String pkg;
        final String label;
        final boolean system;

        AppEntry(String pkg, String label, boolean system) {
            this.pkg = pkg;
            this.label = label;
            this.system = system;
        }
    }

    private Ui ui;
    private SharedPreferences sp;
    private final List<AppEntry> all = new ArrayList<>();
    private final List<AppEntry> shown = new ArrayList<>();
    private final Set<String> checked = new LinkedHashSet<>();
    private String keyword = "";

    private AppsAdapter adapter;
    private TextView statValue;
    private TextView statCaption;
    private TextView saveBtn;
    private Switch enableSwitch;

    @Override
    protected void onCreate(Bundle b) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(b);
        ui = new Ui(this);
        Window w = getWindow();
        w.setStatusBarColor(ui.bg);
        if (!ui.dark) {
            w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
        sp = getSharedPreferences(HookEntry.PREFS, MODE_PRIVATE);
        Set<String> saved = sp.getStringSet(HookEntry.KEY_HIDDEN, null);
        if (saved != null) {
            checked.addAll(saved);
        }
        buildUi();
        loadApps();
        rebuild();
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }

    private TextView label(String text, float sp, int color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        if (bold) {
            tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
        }
        return tv;
    }

    private LinearLayout card() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setBackground(Ui.solid(this, ui.card, 20));
        return v;
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(ui.bg);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(10), dp(16), dp(6));
        root.addView(col, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // ---------- 顶栏 ----------
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        col.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(label("后台卡片隐藏", 22, ui.text, true));
        TextView sub = label("最近任务里不出现勾选的应用", 12, ui.sub, false);
        sub.setPadding(0, dp(3), 0, 0);
        titles.addView(sub);
        bar.addView(titles, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        enableSwitch = new Switch(this);
        enableSwitch.setText("启用");
        enableSwitch.setTextSize(12);
        enableSwitch.setTextColor(ui.sub);
        enableSwitch.setChecked(sp.getBoolean(HookEntry.KEY_ENABLED, true));
        enableSwitch.setPadding(0, 0, 0, 0);
        try {
            enableSwitch.setThumbTintList(ColorStateList.valueOf(ui.accent));
            enableSwitch.setTrackTintList(ColorStateList.valueOf(
                    ui.dark ? 0x55FFFFFF : 0x22000000));
        } catch (Throwable ignored) {
        }
        enableSwitch.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
                sp.edit().putBoolean(HookEntry.KEY_ENABLED, isChecked).apply();
            }
        });
        bar.addView(enableSwitch);

        // ---------- 状态卡 ----------
        LinearLayout stat = card();
        stat.setPadding(dp(16), dp(14), dp(14), dp(14));
        LinearLayout.LayoutParams statLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statLp.topMargin = dp(14);
        col.addView(stat, statLp);

        LinearLayout statRow = new LinearLayout(this);
        statRow.setOrientation(LinearLayout.HORIZONTAL);
        statRow.setGravity(Gravity.CENTER_VERTICAL);
        stat.addView(statRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        statValue = label("0", 26, ui.text, true);
        statRow.addView(statValue);

        LinearLayout statTexts = new LinearLayout(this);
        statTexts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams stLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        stLp.leftMargin = dp(10);
        statRow.addView(statTexts, stLp);
        statCaption = label("个应用已隐藏", 13, ui.text, false);
        statTexts.addView(statCaption);
        TextView statSub = label("推荐作用域 · 系统框架（android）", 11, ui.sub, false);
        statSub.setPadding(0, dp(2), 0, 0);
        statTexts.addView(statSub);

        TextView chip = label("推荐", 10, ui.chipText, true);
        chip.setPadding(dp(8), dp(3), dp(8), dp(3));
        chip.setBackground(Ui.solid(this, ui.chipBg, 8));
        statRow.addView(chip);

        // ---------- 搜索 ----------
        LinearLayout searchBox = new LinearLayout(this);
        searchBox.setOrientation(LinearLayout.HORIZONTAL);
        searchBox.setGravity(Gravity.CENTER_VERTICAL);
        searchBox.setBackground(Ui.solid(this, ui.cardAlt, 24));
        searchBox.setPadding(dp(14), 0, dp(14), 0);
        LinearLayout.LayoutParams sbLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        sbLp.topMargin = dp(12);
        col.addView(searchBox, sbLp);

        TextView glass = label("🔍", 13, ui.sub, false);
        searchBox.addView(glass);

        final EditText search = new EditText(this);
        search.setBackground(null);
        search.setHint("搜索应用名或包名");
        search.setHintTextColor(ui.sub);
        search.setTextColor(ui.text);
        search.setTextSize(14);
        search.setSingleLine(true);
        search.setPadding(dp(8), 0, 0, 0);
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        searchBox.addView(search, sLp);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b2, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b2, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                keyword = s.toString().trim();
                rebuild();
            }
        });

        // ---------- 列表 ----------
        final ListView list = new ListView(this);
        list.setDivider(new ColorDrawable(0x00000000));
        list.setDividerHeight(dp(10));
        list.setSelector(new ColorDrawable(0x00000000));
        list.setClipToPadding(false);
        list.setPadding(0, dp(4), 0, dp(96));
        list.setVerticalScrollBarEnabled(false);
        adapter = new AppsAdapter(this);
        list.setAdapter(adapter);
        LinearLayout.LayoutParams listLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        listLp.topMargin = dp(4);
        col.addView(list, listLp);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                AppEntry e = shown.get(position);
                if (!checked.remove(e.pkg)) {
                    checked.add(e.pkg);
                }
                persist();
                adapter.notifyDataSetChanged();
                updateStat();
            }
        });

        // ---------- 底部按钮 ----------
        FrameLayout bottom = new FrameLayout(this);
        bottom.setPadding(dp(16), dp(8), dp(16), dp(22));
        FrameLayout.LayoutParams bLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bLp.gravity = Gravity.BOTTOM;
        root.addView(bottom, bLp);

        saveBtn = label("保存并生效", 15, ui.onAccent, true);
        saveBtn.setGravity(Gravity.CENTER);
        saveBtn.setBackground(Ui.solid(this, ui.accent, 26));
        bottom.addView(saveBtn, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
        saveBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                persist();
                saveBtn.setText("已保存 ✓");
                Toast.makeText(MainActivity.this,
                        "已保存 " + checked.size() + " 个应用，回后台重进卡片生效",
                        Toast.LENGTH_SHORT).show();
                saveBtn.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        saveBtn.setText("保存并生效");
                    }
                }, 1400L);
            }
        });

        setContentView(root);
    }

    private void persist() {
        sp.edit()
                .putBoolean(HookEntry.KEY_ENABLED, enableSwitch == null || enableSwitch.isChecked())
                .putStringSet(HookEntry.KEY_HIDDEN, new HashSet<>(checked))
                .apply();
    }

    private void updateStat() {
        statValue.setText(String.valueOf(checked.size()));
        statCaption.setText(checked.isEmpty() ? "个应用已隐藏 · 还没选" : "个应用已隐藏");
    }

    private void rebuild() {
        shown.clear();
        String k = keyword.toLowerCase(Locale.ROOT);
        for (AppEntry e : all) {
            if (k.isEmpty()
                    || e.label.toLowerCase(Locale.ROOT).contains(k)
                    || e.pkg.toLowerCase(Locale.ROOT).contains(k)) {
                shown.add(e);
            }
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        updateStat();
    }

    private void loadApps() {
        PackageManager pm = getPackageManager();
        Set<String> seen = new HashSet<>();
        // 一次查询拿到所有有桌面入口的包名，避免逐包 IPC 拖慢首屏
        Set<String> launchable = new HashSet<>();
        try {
            Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            for (ResolveInfo ri : pm.queryIntentActivities(main, 0)) {
                if (ri.activityInfo != null) {
                    launchable.add(ri.activityInfo.packageName);
                }
            }
        } catch (Throwable ignored) {
        }
        List<ApplicationInfo> apps;
        try {
            apps = pm.getInstalledApplications(0);
        } catch (Throwable t) {
            apps = Collections.emptyList();
        }
        for (ApplicationInfo ai : apps) {
            if (ai.packageName.equals(getPackageName())) {
                continue;
            }
            boolean system = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            if (!launchable.contains(ai.packageName) && !checked.contains(ai.packageName)) {
                continue;
            }
            if (!seen.add(ai.packageName)) {
                continue;
            }
            CharSequence lbl = ai.loadLabel(pm);
            all.add(new AppEntry(ai.packageName,
                    lbl == null ? ai.packageName : lbl.toString(), system));
        }
        final Collator col = Collator.getInstance(Locale.CHINA);
        Collections.sort(all, new Comparator<AppEntry>() {
            @Override
            public int compare(AppEntry a, AppEntry b) {
                if (a.system != b.system) {
                    return a.system ? -1 : 1;
                }
                return col.compare(a.label, b.label);
            }
        });
    }

    private Drawable rippleRow() {
        Drawable content = Ui.solid(this, ui.card, 20);
        int mask = ui.dark ? 0x33FFFFFF : 0x1A000000;
        return new RippleDrawable(ColorStateList.valueOf(mask), content, null);
    }

    private final class AppsAdapter extends BaseAdapter {
        private final Context ctx;
        private final int iconSize;

        AppsAdapter(Context ctx) {
            this.ctx = ctx;
            this.iconSize = dp(42);
        }

        @Override
        public int getCount() {
            return shown.size();
        }

        @Override
        public Object getItem(int position) {
            return shown.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout row;
            ImageView icon;
            TextView name;
            TextView pkg;
            CheckDot dot;
            if (convertView == null) {
                row = new LinearLayout(ctx);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(12), dp(11), dp(14), dp(11));
                row.setBackground(rippleRow());

                icon = new ImageView(ctx);
                row.addView(icon, new LinearLayout.LayoutParams(iconSize, iconSize));

                LinearLayout texts = new LinearLayout(ctx);
                texts.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                tLp.leftMargin = dp(12);
                row.addView(texts, tLp);

                name = label("", 15, ui.text, false);
                texts.addView(name);
                pkg = label("", 11, ui.sub, false);
                pkg.setPadding(0, dp(2), 0, 0);
                pkg.setSingleLine(true);
                texts.addView(pkg);

                dot = new CheckDot(ctx, 24);
                dot.setColors(ui.accent, ui.outline, ui.dark ? 0xFF101010 : 0xFFFFFFFF);
                LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                dLp.leftMargin = dp(10);
                row.addView(dot, dLp);

                row.setTag(new Object[]{icon, name, pkg, dot});
                convertView = row;
            } else {
                row = (LinearLayout) convertView;
                Object[] tags = (Object[]) row.getTag();
                icon = (ImageView) tags[0];
                name = (TextView) tags[1];
                pkg = (TextView) tags[2];
                dot = (CheckDot) tags[3];
            }
            AppEntry e = shown.get(position);
            name.setText(e.label);
            pkg.setText(e.pkg);
            icon.setImageBitmap(IconCache.get(ctx, e.pkg, iconSize));
            dot.setChecked(checked.contains(e.pkg));
            return convertView;
        }
    }
}
