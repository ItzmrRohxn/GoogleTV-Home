package com.firelauncher.home;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/** Tiny wrapper around SharedPreferences: favorites, hidden apps, recents, featured app. */
public class Prefs {

    private static final String FILE = "launcher_prefs";
    private static final String K_FAV = "favorites";
    private static final String K_HIDDEN = "hidden";
    private static final String K_RECENT = "recent";
    private static final String K_FEATURED = "featured";
    private static final String K_FAV_SLOT = "favorite_slot_";
    private static final int MAX_RECENT = 12;
    private static final String SEP = "\n";

    private final SharedPreferences sp;

    public Prefs(Context context) {
        sp = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    private List<String> getList(String key) {
        List<String> out = new ArrayList<String>();
        String raw = sp.getString(key, "");
        if (raw == null || raw.length() == 0) {
            return out;
        }
        String[] parts = raw.split(SEP);
        for (int i = 0; i < parts.length; i++) {
            String s = parts[i];
            if (s.length() > 0 && !out.contains(s)) {
                out.add(s);
            }
        }
        return out;
    }

    private void putList(String key, List<String> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                sb.append(SEP);
            }
            sb.append(list.get(i));
        }
        sp.edit().putString(key, sb.toString()).apply();
    }

    // ---- favorites ----
    public List<String> getFavorites() {
        return getList(K_FAV);
    }

    public boolean isFavorite(String pkg) {
        return getList(K_FAV).contains(pkg);
    }

    /** @return true if the app is a favorite after the call. */
    public boolean toggleFavorite(String pkg) {
        List<String> list = getList(K_FAV);
        boolean now;
        if (list.contains(pkg)) {
            list.remove(pkg);
            now = false;
        } else {
            list.add(pkg);
            now = true;
        }
        putList(K_FAV, list);
        return now;
    }

    // ---- fixed home-screen favorite slots ----
    public String getFavoriteSlot(int slot) {
        return sp.getString(K_FAV_SLOT + slot, null);
    }

    public void setFavoriteSlot(int slot, String pkg) {
        if (pkg == null) {
            sp.edit().remove(K_FAV_SLOT + slot).apply();
        } else {
            sp.edit().putString(K_FAV_SLOT + slot, pkg).apply();
        }
    }

    public List<String> getFavoriteSlots() {
        List<String> out = new ArrayList<String>();
        List<String> old = getFavorites();
        for (int i = 0; i < 5; i++) {
            String pkg = getFavoriteSlot(i);
            if (pkg == null && i < old.size()) pkg = old.get(i);
            out.add(pkg);
        }
        return out;
    }

    // ---- hidden ----
    public List<String> getHidden() {
        return getList(K_HIDDEN);
    }

    public void hide(String pkg) {
        List<String> list = getList(K_HIDDEN);
        if (!list.contains(pkg)) {
            list.add(pkg);
            putList(K_HIDDEN, list);
        }
    }

    public void unhideAll() {
        sp.edit().remove(K_HIDDEN).apply();
    }

    // ---- recents ----
    public List<String> getRecent() {
        return getList(K_RECENT);
    }

    public void pushRecent(String pkg) {
        List<String> list = getList(K_RECENT);
        list.remove(pkg);
        list.add(0, pkg);
        while (list.size() > MAX_RECENT) {
            list.remove(list.size() - 1);
        }
        putList(K_RECENT, list);
    }

    public void clearFavoritesAndRecent() {
        sp.edit().remove(K_FAV).remove(K_RECENT).apply();
    }

    // ---- featured banner app ----
    public String getFeatured() {
        return sp.getString(K_FEATURED, null);
    }

    public void setFeatured(String pkg) {
        if (pkg == null) {
            sp.edit().remove(K_FEATURED).apply();
        } else {
            sp.edit().putString(K_FEATURED, pkg).apply();
        }
    }
}
