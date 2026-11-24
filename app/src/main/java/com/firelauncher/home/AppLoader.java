package com.firelauncher.home;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Finds every app that can be launched from a home screen. Call from a background thread. */
public final class AppLoader {

    private AppLoader() {
    }

    public static List<AppEntry> loadAll(Context context) {
        PackageManager pm = context.getPackageManager();
        String self = context.getPackageName();
        Map<String, AppEntry> map = new LinkedHashMap<String, AppEntry>();

        // Fire TV apps usually declare LEANBACK_LAUNCHER; phone-style apps and
        // sideloaded apps declare LAUNCHER. Amazon's home shows both, so do we.
        // Leanback first so a TV-optimised activity wins when an app has both.
        collect(pm, Intent.CATEGORY_LEANBACK_LAUNCHER, self, map);
        collect(pm, Intent.CATEGORY_LAUNCHER, self, map);

        List<AppEntry> list = new ArrayList<AppEntry>(map.values());
        Collections.sort(list, new Comparator<AppEntry>() {
            @Override
            public int compare(AppEntry a, AppEntry b) {
                return a.label.compareToIgnoreCase(b.label);
            }
        });
        return list;
    }

    private static void collect(PackageManager pm, String category, String self,
                                Map<String, AppEntry> map) {
        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.addCategory(category);
        List<ResolveInfo> infos = pm.queryIntentActivities(intent, 0);
        if (infos == null) {
            return;
        }
        for (int i = 0; i < infos.size(); i++) {
            ResolveInfo ri = infos.get(i);
            ActivityInfo ai = ri.activityInfo;
            if (ai == null) {
                continue;
            }
            String pkg = ai.packageName;
            if (pkg.equals(self) || map.containsKey(pkg)) {
                continue;
            }
            CharSequence cs = ri.loadLabel(pm);
            String label = (cs != null && cs.length() > 0) ? cs.toString() : pkg;
            map.put(pkg, new AppEntry(pkg, label, new ComponentName(pkg, ai.name), category, ai));
        }
    }
}
