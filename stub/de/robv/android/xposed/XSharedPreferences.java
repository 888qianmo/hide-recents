package de.robv.android.xposed;

import java.util.Set;

/** 编译期占位。 */
public class XSharedPreferences {
    public XSharedPreferences(String packageName, String prefFileName) {
    }

    public void reload() {
    }

    public boolean getBoolean(String key, boolean def) {
        return def;
    }

    public String getString(String key, String def) {
        return def;
    }

    public int getInt(String key, int def) {
        return def;
    }

    public Set<String> getStringSet(String key, Set<String> def) {
        return def;
    }

    public boolean makeWorldReadable() {
        return true;
    }
}
