# 后台卡片隐藏（HideRecentsCard）— LSPosed 模块

在本机（一加 PJZ110 / Android 17 / SDK 37 / arm64）上隐藏「最近任务（后台卡片）」里的指定应用。
过滤发生在 `system_server`，被勾选的任务不会进入列表，也不会把快照交给 Launcher / SystemUI。

- 包名：`com.yg.hidecard`
- **推荐作用域：`android`（系统框架）** —— LSPosed 作用域页会自动标记/勾选
- minSdk 26 · targetSdk 34 · 纯 framework，无 androidx、无 Gradle 依赖
- 产物：`build/HideRecentsCard-1.0.apk`（25 KB）

## 推荐作用域怎么来的

LSPosed 的 `ModuleUtil.getScopeList()` 读的是模块 manifest 里的 `xposedscope`：

```java
int id = app.metaData.getInt("xposedscope");              // 资源 id 形式
if (id != 0) list = res.getStringArray(id);
else {
    String s = app.metaData.getString("xposedscope");     // 字符串形式
    if (s != null) list = Arrays.asList(s.split(";"));
}
```

所以本模块直接用字符串形式声明，多个作用域用 `;` 分隔：

```xml
<meta-data android:name="xposedscope" android:value="android" />
```

这样 LSPosed 作用域页里「系统框架」会被标成推荐并默认勾选，不需要资源表、也不需要 aapt2。
（`META-INF/xposed/scope.list` 那套是 libxposed 新 API 模块的写法，老 API 模块别混用。）

## 界面

参照 [PUIInstaller](https://github.com/sakurakingfifth-debug/PUIInstaller) 的 Material3 卡片风格重写：
背景 `#F0F1F3`、20dp 圆角白卡、强调色 `#BE3455`（PUI 洋红）、圆形勾选点、
自己圆角化的应用图标、深色模式跟随系统（`#000` 背景 / `#1A1A1A` 卡片 / `#FFB1C1` 强调）。

- 顶栏：标题 + 启用模块开关（写 `enabled`，hook 侧实时读）
- 状态卡：已隐藏数量 + 推荐作用域说明 + 「推荐」标签
- 搜索框：圆角灰底，按应用名 / 包名过滤
- 列表：白卡行、42dp 圆角图标、名称 + 包名 + 勾选圆点；点击整行切换（立即落盘）
- 底部：强调色圆角主按钮「保存并生效」

预览：`preview.png`（按实际布局参数还原的示意图，非真机截图）

## 原理

多任务列表的数据来源只有一条主链路：

```
Launcher/SystemUI  --binder-->  IActivityTaskManager.getRecentTasks()
                                      |
                    ActivityTaskManagerService#getRecentTasks
                                      |
                       RecentTasksServiceImpl#getRecentTasks        (Android 14+)
                          /  RecentTasks#getRecentTasks             (Android 9~13)
```

模块在 system_server 里对这条链路上的**所有**列表出口做 `afterHookedMethod`，把
`baseIntent` / `topActivity` / `origActivity` / `realActivity` 命中黑名单的
`RecentTaskInfo`、`RunningTaskInfo` 移出返回列表。

- 多处同时 hook 幂等：上层删掉的项，下层自然不存在
- 同样 hook `ActivityTaskManagerService#getTasks`，兼顾走 `getTasks` 拉后台的 ROM
- 返回 `List` 或 `ParceledListSlice` 两种形态都处理
- 类名走候选表，一个都没命中时会把候选类里名字含 `recent` 的方法打进日志

## 构建

本机 arm64，官方 build-tools 里的 `aapt2` / `zipalign` 只有 x86_64 版跑不了，
所以构建链是：`javac` → `d8`（java 版）→ 自制二进制清单 → `zip` → `apksigner`（java 版）。

```bash
apt-get install -y openjdk-17-jdk-headless
bash tools/setup-sdk.sh    # platform-35 + build-tools 35.0.0（只用 d8/apksigner/apkanalyzer）
bash build.sh               # -> build/HideRecentsCard-1.0.apk
```

`tools/gen_axml.py` 把 `app/src/main/AndroidManifest.xml` 编成系统可解析的 AXML，
因此不需要 aapt2；对应地 APK 里没有 `resources.arsc`，界面全部代码创建。

## 安装与启用

1. 安装 `HideRecentsCard-1.0.apk`（`Download/DSH-Go/HideRecentsCard/`）
2. LSPosed → 模块 → 启用「后台卡片隐藏」；作用域里「系统框架」会显示为推荐
3. 重启手机
4. 打开模块勾选应用 → 「保存并生效」

## 生效验证

```bash
adb logcat -s LSPosed | grep HideCard
```

正常输出 `system_server 已载入 sdk=37` 和各 hook 命中条数；只出现
「!! 没有命中任何方法」时，紧跟的 dump 日志会列出候选类里含 `recent` 的方法名，
按它补 `HookEntry.TARGETS` 再编一次即可。

## 已知边界

- Android 17 需要能正常注入 `system_server` 的 LSPosed（Zygisk / KernelSU 版）
- APK 无 `resources.arsc`，LSPosed 列表里模块名会显示包名 `com.yg.hidecard`；
  要显示中文名/图标需要再塞一个最小资源表（可以让界面里显示 `label` 引用）
- 已渲染过的旧卡片快照可能残留在 SystemUI 内存，重启一次彻底干净
- 只隐藏后台卡片，不改变应用运行状态
