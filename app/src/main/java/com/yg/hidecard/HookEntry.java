package com.yg.hidecard;

import android.content.ComponentName;
import android.content.Intent;
import android.os.SystemClock;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 入口：注入 system_server（android 进程），在最近任务列表返回前把指定包名的卡片过滤掉。
 *
 * 多版本 hook 点（任一命中即可，全部 hook 时过滤幂等）：
 *   ActivityTaskManagerService#getRecentTasks / #getTasks      —— Android 10 ~ 17 binder 出口
 *   RecentTasksServiceImpl#getRecentTasks                       —— Android 14+
 *   RecentTasks#getRecentTasks / #getRecentTasksForUser          —— Android 9 ~ 13
 *   RecentTasksImpl#getRecentTasks                              —— 兜底
 */
public class HookEntry implements IXposedHookLoadPackage {

    public static final String MODULE_PKG = "com.yg.hidecard";
    public static final String PREFS = "hidecard";
    public static final String KEY_ENABLED = "enabled";
    public static final String KEY_HIDDEN = "hidden_pkgs";
    public static final String KEY_MATCH_TOP = "match_top_activity";

    private static final String TAG = "[HideCard] ";

    /** 类名 -> 要 hook 的方法名。 */
    private static final String[][] TARGETS = {
            {"com.android.server.wm.ActivityTaskManagerService", "getRecentTasks", "getTasks"},
            {"com.android.server.wm.RecentTasksServiceImpl", "getRecentTasks", "getRecentTasksForUser"},
            {"com.android.server.wm.RecentTasks", "getRecentTasks", "getRecentTasksForUser"},
            {"com.android.server.wm.RecentTasksImpl", "getRecentTasks"},
    };

    private static volatile Set<String> sHidden = Collections.emptySet();
    private static volatile boolean sEnabled = true;
    private static volatile boolean sMatchTop = true;
    private static volatile long sNextReload = 0L;
    private static XSharedPreferences sPrefs;
    private static boolean sStarted = false;
    private static int sHookedMethods = 0;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        if (!"android".equals(lp.packageName) || !"android".equals(lp.processName)) {
            return;
        }
        synchronized (HookEntry.class) {
            if (sStarted) {
                return;
            }
            sStarted = true;
        }
        reloadPrefs(true);
        XposedBridge.log(TAG + "system_server 已载入 sdk=" + android.os.Build.VERSION.SDK_INT);

        for (String[] row : TARGETS) {
            String cls = row[0];
            for (int i = 1; i < row.length; i++) {
                hookOne(lp.classLoader, cls, row[i]);
            }
        }
        if (sHookedMethods == 0) {
            XposedBridge.log(TAG + "!! 没有命中任何方法，记录类结构用于排查");
            dumpRecentClasses(lp.classLoader);
        } else {
            XposedBridge.log(TAG + "共 hook " + sHookedMethods + " 个方法");
        }
    }

    private void hookOne(ClassLoader cl, String className, String method) {
        Class<?> clazz;
        try {
            clazz = XposedHelpers.findClass(className, cl);
        } catch (Throwable t) {
            return;
        }
        if (clazz == null) {
            return;
        }
        try {
            Set<XC_MethodHook.Unhook> hs = XposedBridge.hookAllMethods(
                    clazz, method, new FilterHook(className + "#" + method));
            if (!hs.isEmpty()) {
                sHookedMethods += hs.size();
                XposedBridge.log(TAG + "hook " + className + "#" + method + " x" + hs.size());
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "hook 失败 " + className + "#" + method + ": " + t);
        }
    }

    /** 一个都没命中时打印类里返回 List 的方法名，方便按机型补点。 */
    private void dumpRecentClasses(ClassLoader cl) {
        for (String[] row : TARGETS) {
            try {
                Class<?> c = XposedHelpers.findClass(row[0], cl);
                if (c == null) {
                    XposedBridge.log(TAG + "缺类 " + row[0]);
                    continue;
                }
                StringBuilder sb = new StringBuilder();
                for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
                    if (m.getName().toLowerCase().contains("recent")) {
                        sb.append(m.getName()).append('(').append(m.getParameterCount()).append(") ");
                    }
                }
                XposedBridge.log(TAG + row[0] + " => " + sb);
            } catch (Throwable t) {
                XposedBridge.log(TAG + "dump 失败 " + row[0] + ": " + t);
            }
        }
    }

    /** 列表出口统一过滤。 */
    private static final class FilterHook extends XC_MethodHook {
        private final String from;

        FilterHook(String from) {
            this.from = from;
        }

        @Override
        protected void afterHookedMethod(MethodHookParam param) {
            try {
                reloadPrefs(false);
                if (!sEnabled || sHidden.isEmpty()) {
                    return;
                }
                Object result = param.getResult();
                if (result == null) {
                    return;
                }
                if (result instanceof List) {
                    ArrayList<Object> out = filterList((List<?>) result);
                    if (out != null) {
                        param.setResult(out);
                    }
                    return;
                }
                // 部分版本 / 部分方法用 ParceledListSlice 传列表
                if (result.getClass().getName().endsWith("ParceledListSlice")) {
                    Object inner = callNoArg(result, "getList");
                    if (inner instanceof List) {
                        ArrayList<Object> out = filterList((List<?>) inner);
                        if (out != null) {
                            Object slice = result.getClass()
                                    .getConstructor(List.class)
                                    .newInstance(out);
                            param.setResult(slice);
                        }
                    }
                }
            } catch (Throwable t) {
                XposedBridge.log(TAG + "过滤异常 " + from + ": " + t);
            }
        }

        /** 有删除则返回过滤后的新列表，否则 null。 */
        private ArrayList<Object> filterList(List<?> src) {
            ArrayList<Object> out = new ArrayList<>(src.size());
            int removed = 0;
            for (Object item : src) {
                if (shouldHide(item)) {
                    removed++;
                } else {
                    out.add(item);
                }
            }
            if (removed == 0) {
                return null;
            }
            XposedBridge.log(TAG + from + " 隐藏 " + removed + " 个卡片，剩 " + out.size());
            return out;
        }
    }

    private static Object callNoArg(Object target, String method) {
        try {
            return target.getClass().getMethod(method).invoke(target);
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean shouldHide(Object item) {
        if (item == null) {
            return false;
        }
        Set<String> hidden = sHidden;
        for (String pkg : packagesOf(item)) {
            if (pkg != null && hidden.contains(pkg)) {
                return true;
            }
        }
        return false;
    }

    /** 任务所属包名，按可信度收集。 */
    private static Set<String> packagesOf(Object info) {
        Set<String> out = new HashSet<>(4);
        Object base = readField(info, "baseIntent");
        if (base instanceof Intent) {
            Intent it = (Intent) base;
            ComponentName cn = it.getComponent();
            if (cn != null) {
                out.add(cn.getPackageName());
            } else if (it.getPackage() != null) {
                out.add(it.getPackage());
            }
        }
        if (sMatchTop) {
            addComponent(out, readField(info, "topActivity"));
            addComponent(out, readField(info, "origActivity"));
            addComponent(out, readField(info, "realActivity"));
        }
        return out;
    }

    private static void addComponent(Set<String> out, Object cn) {
        if (cn instanceof ComponentName) {
            String p = ((ComponentName) cn).getPackageName();
            if (p != null) {
                out.add(p);
            }
        }
    }

    private static Object readField(Object target, String name) {
        try {
            Class<?> c = target.getClass();
            while (c != null) {
                try {
                    Field f = c.getDeclaredField(name);
                    f.setAccessible(true);
                    return f.get(target);
                } catch (NoSuchFieldException e) {
                    c = c.getSuperclass();
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static void reloadPrefs(boolean force) {
        long now = SystemClock.elapsedRealtime();
        if (!force && now < sNextReload) {
            return;
        }
        sNextReload = now + 1500L;
        try {
            if (sPrefs == null) {
                sPrefs = new XSharedPreferences(MODULE_PKG, PREFS);
                try {
                    sPrefs.makeWorldReadable();
                } catch (Throwable ignored) {
                }
            }
            sPrefs.reload();
            sEnabled = sPrefs.getBoolean(KEY_ENABLED, true);
            sMatchTop = sPrefs.getBoolean(KEY_MATCH_TOP, true);
            Set<String> raw = sPrefs.getStringSet(KEY_HIDDEN, null);
            sHidden = (raw == null || raw.isEmpty())
                    ? Collections.<String>emptySet()
                    : new HashSet<>(raw);
        } catch (Throwable t) {
            XposedBridge.log(TAG + "读配置失败: " + t);
        }
    }
}
