package com.firelauncher.home;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The launcher's one and only screen: featured banner on top, horizontal rows below.
 * Written for API 22 (Android 5.1 / Fire OS 5): no AndroidX, no Google Play Services.
 */
public class MainActivity extends Activity {

    private static final String CUSTOM_DIR = "FireLauncher";
    private static final String HOME_ALIAS = "com.firelauncher.home.HomeAlias";

    private static final int TYPE_APP = 0;
    private static final int TYPE_HOME_TOGGLE = 1;
    private static final int TYPE_SETTINGS = 2;
    private static final int TYPE_OPTIONS = 3;

    /** What a focusable tile stands for. Stored as the tile's tag. */
    private static final class TileInfo {
        int type;
        AppEntry entry;
        String key;
        int rowIndex;
        View rowView;
        HorizontalScrollView hsv;
        View item;
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    private Prefs prefs;
    private PackageManager pm;
    private IconLoader icons;
    private float density;

    private ScrollView scroll;
    private LinearLayout content;
    private View banner;
    private ImageView wallpaperView;
    private ImageView bannerImage;
    private ImageView bannerIcon;
    private TextView bannerTitle;
    private TextView bannerSubtitle;

    private List<AppEntry> allApps = new ArrayList<AppEntry>();
    private final List<View> tiles = new ArrayList<View>();
    private boolean dirty = true;
    private boolean loading = false;
    private boolean resumed = false;
    private boolean destroyed = false;
    private boolean firstBuild = true;
    private String lastFocusKey;
    private boolean receiverRegistered = false;

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            dirty = true;
            if (resumed) {
                refresh();
            }
        }
    };

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        pm = getPackageManager();
        prefs = new Prefs(this);
        density = getResources().getDisplayMetrics().density;
        icons = new IconLoader(Math.round(96 * density));

        scroll = (ScrollView) findViewById(R.id.scroll);
        content = (LinearLayout) findViewById(R.id.content);
        banner = findViewById(R.id.banner);
        wallpaperView = (ImageView) findViewById(R.id.wallpaper);
        bannerImage = (ImageView) findViewById(R.id.banner_image);
        bannerIcon = (ImageView) findViewById(R.id.banner_icon);
        bannerTitle = (TextView) findViewById(R.id.banner_title);
        bannerSubtitle = (TextView) findViewById(R.id.banner_subtitle);

        banner.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBannerClicked();
            }
        });
        banner.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    lastFocusKey = null;
                    scroll.smoothScrollTo(0, 0);
                }
            }
        });

        content.setAlpha(0f);
        loadImagesInBackground();

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_REPLACED);
        f.addDataScheme("package");
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(packageReceiver, f, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(packageReceiver, f);
        }
        receiverRegistered = true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        if (dirty) {
            refresh();
        }
    }

    @Override
    protected void onPause() {
        resumed = false;
        super.onPause();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Home button pressed while we are already open: jump back to the top.
        if (intent != null && intent.hasCategory(Intent.CATEGORY_HOME)) {
            goToTop();
        }
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_BACKGROUND) {
            icons.clear();
        }
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        if (receiverRegistered) {
            try {
                unregisterReceiver(packageReceiver);
            } catch (Throwable ignored) {
            }
        }
        handler.removeCallbacksAndMessages(null);
        worker.shutdownNow();
        icons.shutdown();
        super.onDestroy();
    }

    // ------------------------------------------------------------------ remote control

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            // The "three lines" button on the Fire TV remote.
            View focus = getCurrentFocus();
            if (focus != null && focus.getTag() instanceof TileInfo) {
                TileInfo info = (TileInfo) focus.getTag();
                if (info.entry != null) {
                    showAppMenu(info.entry);
                    return true;
                }
            }
            showOptionsDialog();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        boolean atTop = scroll.getScrollY() < 4 && banner.isFocused();
        if (!atTop) {
            goToTop();
            return;
        }
        // Already at the top. If we are acting as the Home screen there is nowhere to go;
        // if we were started from the Apps list, Back simply closes us.
        if (!isHomeEnabled()) {
            super.onBackPressed();
        }
    }

    private void goToTop() {
        banner.requestFocus();
        scroll.smoothScrollTo(0, 0);
    }

    // ------------------------------------------------------------------ images

    private void loadImagesInBackground() {
        final int wallW = 1280;
        final int wallH = 720;
        final int banW = 960;
        final int banH = 220;
        try {
            worker.execute(new Runnable() {
                @Override
                public void run() {
                    Bitmap wall = loadCustomImage("wallpaper", wallW, wallH);
                    if (wall == null) {
                        wall = BitmapUtil.decodeResource(getResources(), R.drawable.wallpaper_default, wallW, wallH);
                    }
                    Bitmap ban = loadCustomImage("banner", banW, banH);
                    if (ban == null) {
                        ban = BitmapUtil.decodeResource(getResources(), R.drawable.banner_default, banW, banH);
                    }
                    final Bitmap w = wall;
                    final Bitmap b = ban;
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (destroyed) {
                                return;
                            }
                            if (w != null) {
                                wallpaperView.setImageBitmap(w);
                            }
                            if (b != null) {
                                bannerImage.setImageBitmap(b);
                            }
                        }
                    });
                }
            });
        } catch (Throwable ignored) {
        }
    }

    /** Looks for /sdcard/FireLauncher/<name>.jpg|.jpeg|.png and decodes it small. */
    private Bitmap loadCustomImage(String name, int reqW, int reqH) {
        try {
            File dir = new File(Environment.getExternalStorageDirectory(), CUSTOM_DIR);
            String[] exts = {".jpg", ".jpeg", ".png"};
            for (int i = 0; i < exts.length; i++) {
                File f = new File(dir, name + exts[i]);
                if (f.isFile() && f.canRead()) {
                    Bitmap b = BitmapUtil.decodeFile(f.getAbsolutePath(), reqW, reqH);
                    if (b != null) {
                        return b;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    // ------------------------------------------------------------------ loading + building rows

    private void refresh() {
        if (loading || destroyed) {
            return;
        }
        loading = true;
        dirty = false;
        try {
            worker.execute(new Runnable() {
                @Override
                public void run() {
                    List<AppEntry> list;
                    try {
                        list = AppLoader.loadAll(MainActivity.this);
                    } catch (Throwable t) {
                        list = new ArrayList<AppEntry>();
                    }
                    final List<AppEntry> result = list;
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            loading = false;
                            if (destroyed) {
                                return;
                            }
                            allApps = result;
                            rebuild();
                            if (dirty && resumed) {
                                refresh();
                            }
                        }
                    });
                }
            });
        } catch (Throwable t) {
            loading = false;
        }
    }

    private void rebuild() {
        // Remove everything below the banner (index 0).
        while (content.getChildCount() > 1) {
            content.removeViewAt(1);
        }
        tiles.clear();

        Map<String, AppEntry> byPkg = new HashMap<String, AppEntry>();
        for (int i = 0; i < allApps.size(); i++) {
            byPkg.put(allApps.get(i).packageName, allApps.get(i));
        }
        Set<String> hidden = new HashSet<String>(prefs.getHidden());

        List<AppEntry> favorites = new ArrayList<AppEntry>();
        List<String> favPkgs = prefs.getFavorites();
        for (int i = 0; i < favPkgs.size(); i++) {
            AppEntry e = byPkg.get(favPkgs.get(i));
            if (e != null) {
                favorites.add(e);
            }
        }

        List<AppEntry> recent = new ArrayList<AppEntry>();
        List<String> recentPkgs = prefs.getRecent();
        for (int i = 0; i < recentPkgs.size() && recent.size() < 8; i++) {
            AppEntry e = byPkg.get(recentPkgs.get(i));
            if (e != null && !hidden.contains(e.packageName)) {
                recent.add(e);
            }
        }

        List<AppEntry> apps = new ArrayList<AppEntry>();
        for (int i = 0; i < allApps.size(); i++) {
            AppEntry e = allApps.get(i);
            if (!hidden.contains(e.packageName)) {
                apps.add(e);
            }
        }

        int rowIndex = 0;
        if (!favorites.isEmpty()) {
            addAppRow("Favorites", "fav", favorites, rowIndex++);
        }
        if (!recent.isEmpty()) {
            addAppRow("Recently used", "rec", recent, rowIndex++);
        }
        addAppRow("Apps", "all", apps, rowIndex++);
        addSystemRow(rowIndex);

        updateBanner(byPkg);

        // Put focus back where the user was, or on the first tile.
        View target = null;
        if (lastFocusKey != null) {
            for (int i = 0; i < tiles.size(); i++) {
                Object tag = tiles.get(i).getTag();
                if (tag instanceof TileInfo && lastFocusKey.equals(((TileInfo) tag).key)) {
                    target = tiles.get(i);
                    break;
                }
            }
        }
        if (target == null) {
            if (firstBuild || lastFocusKey == null) {
                target = tiles.isEmpty() ? banner : tiles.get(0);
            }
        }
        if (target != null) {
            target.requestFocus();
        }
        if (firstBuild) {
            firstBuild = false;
            scroll.scrollTo(0, 0);
            content.animate().alpha(1f).setDuration(260).start();
        }
    }

    private void addAppRow(String title, String section, List<AppEntry> entries, int rowIndex) {
        if (entries.isEmpty()) {
            return;
        }
        final View row = LayoutInflater.from(this).inflate(R.layout.row_section, content, false);
        ((TextView) row.findViewById(R.id.row_title)).setText(title);
        HorizontalScrollView hsv = (HorizontalScrollView) row.findViewById(R.id.row_scroll);
        LinearLayout items = (LinearLayout) row.findViewById(R.id.row_items);
        for (int i = 0; i < entries.size(); i++) {
            AppEntry e = entries.get(i);
            TileInfo info = new TileInfo();
            info.type = TYPE_APP;
            info.entry = e;
            info.key = section + ":" + e.packageName;
            info.rowIndex = rowIndex;
            info.rowView = row;
            info.hsv = hsv;
            View item = makeTile(items, info, e.label);
            icons.bind(pm, e, (ImageView) item.findViewById(R.id.tile_icon));
        }
        content.addView(row);
    }

    private void addSystemRow(int rowIndex) {
        final View row = LayoutInflater.from(this).inflate(R.layout.row_section, content, false);
        ((TextView) row.findViewById(R.id.row_title)).setText("System");
        HorizontalScrollView hsv = (HorizontalScrollView) row.findViewById(R.id.row_scroll);
        LinearLayout items = (LinearLayout) row.findViewById(R.id.row_items);

        String homeLabel = isHomeEnabled() ? "Home button: ON" : "Home button: OFF";
        addSystemTile(items, row, hsv, rowIndex, TYPE_HOME_TOGGLE, "sys:home", homeLabel, IconLoader.KIND_HOME);
        addSystemTile(items, row, hsv, rowIndex, TYPE_SETTINGS, "sys:settings", "Settings", IconLoader.KIND_SETTINGS);
        addSystemTile(items, row, hsv, rowIndex, TYPE_OPTIONS, "sys:options", "Launcher options", IconLoader.KIND_OPTIONS);
        content.addView(row);
    }

    private void addSystemTile(LinearLayout items, View row, HorizontalScrollView hsv, int rowIndex,
                               int type, String key, String label, int iconKind) {
        TileInfo info = new TileInfo();
        info.type = type;
        info.key = key;
        info.rowIndex = rowIndex;
        info.rowView = row;
        info.hsv = hsv;
        View item = makeTile(items, info, label);
        ((ImageView) item.findViewById(R.id.tile_icon)).setImageBitmap(icons.systemIcon(iconKind));
    }

    /** Inflates one tile, wires up click / long-press / focus animation and adds it to the row. */
    private View makeTile(LinearLayout parent, final TileInfo info, String label) {
        final View item = LayoutInflater.from(this).inflate(R.layout.item_app, parent, false);
        final View card = item.findViewById(R.id.tile_card);
        final TextView labelView = (TextView) item.findViewById(R.id.tile_label);
        labelView.setText(label);
        info.item = item;
        card.setTag(info);

        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onTileClicked(info);
            }
        });
        if (info.type == TYPE_APP) {
            card.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    showAppMenu(info.entry);
                    return true;
                }
            });
        }
        card.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                float s = hasFocus ? 1.10f : 1.0f;
                card.animate().scaleX(s).scaleY(s).setDuration(130).start();
                labelView.animate().alpha(hasFocus ? 1f : 0f).setDuration(130).start();
                if (hasFocus) {
                    lastFocusKey = info.key;
                    scrollToTile(info);
                }
            }
        });

        parent.addView(item);
        tiles.add(card);
        return item;
    }

    /** Smoothly keeps the focused tile near the middle of its row and the row near the top. */
    private void scrollToTile(TileInfo info) {
        if (info.hsv != null && info.item != null) {
            View inner = info.hsv.getChildAt(0);
            int center = inner.getLeft() + info.item.getLeft() + info.item.getWidth() / 2;
            int targetX = center - info.hsv.getWidth() / 2;
            info.hsv.smoothScrollTo(Math.max(0, targetX), 0);
        }
        int targetY;
        if (info.rowIndex == 0) {
            targetY = 0; // first row fits on screen together with the banner
        } else {
            targetY = Math.max(0, info.rowView.getTop() - Math.round(90 * density));
        }
        scroll.smoothScrollTo(0, targetY);
    }

    // ------------------------------------------------------------------ banner

    private void updateBanner(Map<String, AppEntry> byPkg) {
        String pkg = prefs.getFeatured();
        AppEntry featured = (pkg == null) ? null : byPkg.get(pkg);
        if (featured != null) {
            bannerTitle.setText(featured.label);
            bannerSubtitle.setText("Featured app. Press Select to open.");
            bannerIcon.setVisibility(View.VISIBLE);
            icons.bind(pm, featured, bannerIcon);
        } else {
            bannerTitle.setText("Welcome home");
            bannerSubtitle.setText("Press Select to open an app. Hold Select or press Menu for options.");
            bannerIcon.setVisibility(View.GONE);
        }
    }

    private void onBannerClicked() {
        String pkg = prefs.getFeatured();
        if (pkg != null) {
            for (int i = 0; i < allApps.size(); i++) {
                if (allApps.get(i).packageName.equals(pkg)) {
                    launch(allApps.get(i));
                    return;
                }
            }
        }
        View next = banner.focusSearch(View.FOCUS_DOWN);
        if (next != null) {
            next.requestFocus();
        }
    }

    // ------------------------------------------------------------------ actions

    private void onTileClicked(TileInfo info) {
        switch (info.type) {
            case TYPE_APP:
                launch(info.entry);
                break;
            case TYPE_HOME_TOGGLE:
                showHomeDialog();
                break;
            case TYPE_SETTINGS:
                openSettings();
                break;
            case TYPE_OPTIONS:
                showOptionsDialog();
                break;
            default:
                break;
        }
    }

    private void launch(AppEntry e) {
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(e.category);
        i.setComponent(e.component);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            startActivity(i);
            prefs.pushRecent(e.packageName);
            dirty = true;
        } catch (Throwable t) {
            toast("Could not open " + e.label);
        }
    }

    private void openSettings() {
        Intent[] tries = new Intent[]{
                new Intent(Settings.ACTION_SETTINGS),
                new Intent().setComponent(new ComponentName("com.amazon.tv.settings",
                        "com.amazon.tv.settings.tv.TvSettingsActivity")),
                new Intent().setComponent(new ComponentName("com.android.settings",
                        "com.android.settings.Settings"))
        };
        for (int i = 0; i < tries.length; i++) {
            try {
                tries[i].addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(tries[i]);
                return;
            } catch (Throwable ignored) {
            }
        }
        toast("Settings could not be opened from here. Open it from the Amazon home screen.");
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    private AlertDialog.Builder dialog() {
        return new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert);
    }

    private void showAppMenu(final AppEntry e) {
        final boolean fav = prefs.isFavorite(e.packageName);
        final boolean isFeatured = e.packageName.equals(prefs.getFeatured());
        final String[] items = new String[]{
                "Open",
                fav ? "Remove from Favorites" : "Add to Favorites",
                isFeatured ? "Remove as featured banner" : "Show in featured banner",
                "Hide from this launcher",
                "App info"
        };
        dialog().setTitle(e.label).setItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int which) {
                switch (which) {
                    case 0:
                        launch(e);
                        break;
                    case 1:
                        prefs.toggleFavorite(e.packageName);
                        refresh();
                        break;
                    case 2:
                        prefs.setFeatured(isFeatured ? null : e.packageName);
                        refresh();
                        break;
                    case 3:
                        prefs.hide(e.packageName);
                        toast("Hidden. Bring it back from Launcher options.");
                        lastFocusKey = null;
                        refresh();
                        break;
                    case 4:
                        openAppInfo(e.packageName);
                        break;
                    default:
                        break;
                }
            }
        }).show();
    }

    private void openAppInfo(String pkg) {
        try {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + pkg));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Throwable t) {
            toast("App info is not available on this device.");
        }
    }

    private void showOptionsDialog() {
        final String[] items = new String[]{
                "Show all hidden apps",
                "Clear favorites and recents",
                "Remove featured app from banner",
                "How to set my own wallpaper and banner",
                "About"
        };
        dialog().setTitle("Launcher options").setItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int which) {
                switch (which) {
                    case 0:
                        prefs.unhideAll();
                        refresh();
                        break;
                    case 1:
                        prefs.clearFavoritesAndRecent();
                        refresh();
                        break;
                    case 2:
                        prefs.setFeatured(null);
                        refresh();
                        break;
                    case 3:
                        dialog().setTitle("Custom wallpaper and banner")
                                .setMessage("Copy your pictures to the Fire TV folder\n"
                                        + "    Internal storage / FireLauncher\n\n"
                                        + "using these exact names:\n"
                                        + "    wallpaper.jpg  (best: 1920x1080)\n"
                                        + "    banner.jpg  (best: 1800x400)\n\n"
                                        + "PNG also works. Then close and reopen this launcher.\n"
                                        + "Delete the files to go back to the built-in pictures.")
                                .setPositiveButton("OK", null).show();
                        break;
                    case 4:
                        dialog().setTitle("TV Home 1.0")
                                .setMessage("A lightweight launcher for the Fire TV Stick 2nd generation.\n"
                                        + "No Google services. It never changes or removes Amazon's own home screen.")
                                .setPositiveButton("OK", null).show();
                        break;
                    default:
                        break;
                }
            }
        }).show();
    }

    // ------------------------------------------------------------------ Home button integration

    private boolean isHomeEnabled() {
        try {
            int state = pm.getComponentEnabledSetting(new ComponentName(this, HOME_ALIAS));
            return state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
        } catch (Throwable t) {
            return false;
        }
    }

    private void setHomeEnabled(boolean enabled) {
        try {
            pm.setComponentEnabledSetting(new ComponentName(this, HOME_ALIAS),
                    enabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                            : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
        } catch (Throwable t) {
            toast("Could not change the setting.");
        }
        dirty = true;
        refresh();
    }

    private void showHomeDialog() {
        final boolean on = isHomeEnabled();
        if (on) {
            dialog().setTitle("Home button integration is ON")
                    .setMessage("This launcher is registered as a possible Home screen.\n\n"
                            + "Turn it OFF to go back to the normal state. Nothing else was ever changed.")
                    .setPositiveButton("Turn OFF", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int w) {
                            setHomeEnabled(false);
                        }
                    })
                    .setNegativeButton("Cancel", null).show();
        } else {
            dialog().setTitle("Home button (experimental)")
                    .setMessage("This only tells Android that TV Home may be used as a Home screen. "
                            + "It does not touch Amazon's launcher or any system file.\n\n"
                            + "After turning it ON, press the Home button on your remote. "
                            + "If Fire TV asks which Home app to use, choose TV Home. "
                            + "If the Amazon home screen still opens, Fire OS 5 does not allow "
                            + "changing it: turn this OFF again and keep opening TV Home from Apps.")
                    .setPositiveButton("Turn ON", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int w) {
                            setHomeEnabled(true);
                        }
                    })
                    .setNegativeButton("Cancel", null).show();
        }
    }
}
