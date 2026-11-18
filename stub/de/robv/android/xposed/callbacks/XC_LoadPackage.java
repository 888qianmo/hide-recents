package de.robv.android.xposed.callbacks;

import android.content.pm.ApplicationInfo;

/** 编译期占位。 */
public class XC_LoadPackage {
    public static class LoadPackageParam {
        public String packageName;
        public String processName;
        public ClassLoader classLoader;
        public boolean isFirstApplication;
        public ApplicationInfo appInfo;
    }
}
