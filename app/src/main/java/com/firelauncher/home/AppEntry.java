package com.firelauncher.home;

import android.content.ComponentName;
import android.content.pm.ActivityInfo;

/** One launchable app, as found by AppLoader. */
public final class AppEntry {

    public final String packageName;
    public final String label;
    public final ComponentName component;
    /** Intent category this entry was found under (LAUNCHER or LEANBACK_LAUNCHER). */
    public final String category;
    public final ActivityInfo activityInfo;

    public AppEntry(String packageName, String label, ComponentName component,
                    String category, ActivityInfo activityInfo) {
        this.packageName = packageName;
        this.label = label;
        this.component = component;
        this.category = category;
        this.activityInfo = activityInfo;
    }
}
