package app.kinoteatr.launcher;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.Collator;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final String[] DEFAULT_FAV = {
            "top.rootu.lampa", "org.smarttube.stable", "ru.yourok.torrserve", "net.gtvbox.vimuhd",
            "com.spotify.tv.android", "com.valvesoftware.steamlink", "ru.radiationx.anilibria.app.tv"};
    private static final int MAX_FAV = 7;
    private static final int GRID_COLS = 8;
    private static final Set<String> HIDDEN = new HashSet<>(Arrays.asList(
            "app.kinoteatr.launcher", "com.android.tv.settings"));

    /** Known apps: eyebrow, tagline, four ambient colours. */
    private static final Map<String, Object[]> INFO = new HashMap<>();
    static {
        INFO.put("top.rootu.lampa", new Object[]{"Кино и сериалы", "Каталог фильмов и сериалов с поиском по всем источникам.", new int[]{0xff0d0b18, 0xff5b3df5, 0xff9a7bff, 0xff2a1f5c}});
        INFO.put("org.smarttube.stable", new Object[]{"Видео", "YouTube без рекламы, с SponsorBlock и 4K.", new int[]{0xff160607, 0xffe0262b, 0xffff6a3d, 0xff4a0f22}});
        INFO.put("ru.yourok.torrserve", new Object[]{"Торренты", "Смотреть торренты сразу, без скачивания целиком.", new int[]{0xff02140f, 0xff0fbf8a, 0xff46e0c8, 0xff0a4d6b}});
        INFO.put("net.gtvbox.vimuhd", new Object[]{"Плеер", "Медиатека с домашнего сервера и флешек, Dolby Vision и HDR10+.", new int[]{0xff150c03, 0xffff8a00, 0xffffc04d, 0xff6a2a00}});
        INFO.put("com.spotify.tv.android", new Object[]{"Музыка", "Музыка и подкасты на всю комнату.", new int[]{0xff03140a, 0xff1db954, 0xff9be15d, 0xff0b3d2a}});
        INFO.put("com.valvesoftware.steamlink", new Object[]{"Игры", "Игры с компьютера на большом экране.", new int[]{0xff030b1a, 0xff1b6dff, 0xff4fc3ff, 0xff1a1f6b}});
        INFO.put("ru.radiationx.anilibria.app.tv", new Object[]{"Аниме", "Аниме с озвучкой, новые серии каждую неделю.", new int[]{0xff180508, 0xffe8333a, 0xffff8a7a, 0xff5a1030}});
        INFO.put("com.google.android.youtube.tv", new Object[]{"Видео", "Официальное приложение YouTube.", new int[]{0xff120606, 0xffd61f1f, 0xffff6b6b, 0xff3a0a12}});
        INFO.put("com.netflix.ninja", new Object[]{"Кино и сериалы", "Сериалы и фильмы Netflix.", new int[]{0xff0d0303, 0xffb00710, 0xffe50914, 0xff2a0306}});
        INFO.put("org.videolan.vlc", new Object[]{"Плеер", "Плеер, который открывает всё.", new int[]{0xff140a02, 0xffff8800, 0xffffb04d, 0xff4a2400}});
        INFO.put("com.android.vending", new Object[]{"Магазин", "Установить новые приложения.", new int[]{0xff0a0d12, 0xff1a73e8, 0xff34a853, 0xff0c2440}});
    }
    private static final int[] INPUT_COLORS = {0xff06080d, 0xff3a4a66, 0xff8aa0c8, 0xff151c2c};

    private static final int INK = 0xfff4f5f8, INK2 = 0xadf4f5f8, INK3 = 0x6bf4f5f8;

    static final class App {
        String pkg, label;
        ComponentName component;
        boolean leanback;
        ActivityInfo info;
    }

    private float s;
    private FrameLayout root;
    private AmbientView ambient;
    private LinearLayout hero, lower, tray, inputs, grid;
    // Separate layers: hero swaps text, heroWrap fades when scrolling. One animation per layer,
    // so scrolling can never cancel a text swap halfway (that left the title stale after Home).
    private FrameLayout heroWrap;
    private View status;
    private TextView hEyebrow, hTitle, hTag, clock, date, weather;
    private SharedPreferences prefs;
    private final List<App> apps = new ArrayList<>();
    private final Map<String, Drawable> art = new HashMap<>();
    private final Set<String> artIsIcon = new HashSet<>();
    private final Map<String, int[]> palette = new HashMap<>();
    private final ExecutorService io = Executors.newFixedThreadPool(2);
    private final Handler ui = new Handler(Looper.getMainLooper());
    private String focusedKey;
    private int heroToken;

    private int px(float v) { return Math.round(v * s); }

    // Baked shadows (see ShadowLayout). Status pills are deliberately the softest.
    private ShadowLayout.Spec tileShadow() { return new ShadowLayout.Spec(px(12), px(26), -px(6), .55f, px(18), false); }
    private ShadowLayout.Spec trayShadow() { return new ShadowLayout.Spec(px(40), px(90), -px(20), .6f, px(34), true); }
    private ShadowLayout.Spec pillShadow() { return new ShadowLayout.Spec(px(10), px(24), -px(8), .4f, px(18), true); }
    private ShadowLayout.Spec statusShadow() { return new ShadowLayout.Spec(px(5), px(16), -px(6), .2f, px(29), true); }

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        s = getResources().getDisplayMetrics().widthPixels / 1920f;
        prefs = getSharedPreferences("home", MODE_PRIVATE);
        buildChrome();
        setContentView(root);
        loadApps();
        rebuildRows(null);
        applyConfig(getIntent());
        tickClock();

        IntentFilter time = new IntentFilter();
        time.addAction(Intent.ACTION_TIME_TICK);
        time.addAction(Intent.ACTION_TIME_CHANGED);
        time.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(timeReceiver, time);

        IntentFilter pkgs = new IntentFilter();
        pkgs.addAction(Intent.ACTION_PACKAGE_ADDED);
        pkgs.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pkgs.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pkgs.addDataScheme("package");
        registerReceiver(packageReceiver, pkgs);
    }

    @Override
    protected void onResume() {
        super.onResume();
        ambient.start();
        tickClock();
        rebuildInputs();
        refreshWeather();
    }

    @Override
    protected void onPause() {
        ambient.stop();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(timeReceiver);
        unregisterReceiver(packageReceiver);
        io.shutdownNow();
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (applyConfig(intent)) return;
        // Home pressed while already home: drop any menu or move in progress, back to the first favourite.
        closeMenu(false);
        if (moving != null) finishMove();
        if (tray.getChildCount() > 0) tray.getChildAt(0).requestFocus();
    }

    @Override
    public void onBackPressed() { /* home has nowhere to go back to */ }

    private final BroadcastReceiver timeReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { tickClock(); }
    };

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            String pkg = i.getData() != null ? i.getData().getSchemeSpecificPart() : null;
            if (pkg != null) { art.remove(pkg); artIsIcon.remove(pkg); palette.remove(pkg); }
            loadApps();
            rebuildRows(focusedKey);
        }
    };

    // ---------------------------------------------------------------- static chrome

    private GradientDrawable pill(int fill, int stroke, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(px(radius));
        if (stroke != 0) g.setStroke(Math.max(1, px(1.5f)), stroke);
        return g;
    }

    private TextView text(float size, int color, int style, String family) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(size));
        t.setTextColor(color);
        t.setTypeface(Typeface.create(family, style));
        t.setIncludeFontPadding(false);
        return t;
    }

    private void buildChrome() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.setClipChildren(false);

        ambient = new AmbientView(this);
        root.addView(ambient, new FrameLayout.LayoutParams(-1, -1));

        View shade = new View(this);
        GradientDrawable left = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0x8c000000, 0x1f000000, 0x00000000});
        GradientDrawable bottom = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00000000, 0x00000000, 0x4d000000, 0xd1000000});
        shade.setBackground(new LayerDrawable(new Drawable[]{left, bottom}));
        root.addView(shade, new FrameLayout.LayoutParams(-1, -1));

        // status pills, top right
        ShadowLayout statusS = new ShadowLayout(this);
        status = statusS;
        statusS.setGravity(Gravity.CENTER_VERTICAL);
        statusS.shadowAll(statusShadow());
        weather = text(26, INK2, Typeface.NORMAL, "sans-serif");
        weather.setBackground(pill(0x14ffffff, 0x24ffffff, 999));
        weather.setPadding(px(24), px(14), px(24), px(14));
        weather.setVisibility(View.GONE);
        statusS.addView(weather);
        LinearLayout dc = new LinearLayout(this);
        dc.setGravity(Gravity.CENTER_VERTICAL);
        dc.setBackground(pill(0x14ffffff, 0x24ffffff, 999));
        dc.setPadding(px(24), px(14), px(24), px(14));
        date = text(26, INK2, Typeface.NORMAL, "sans-serif");
        clock = text(30, INK, Typeface.NORMAL, "sans-serif-medium");
        clock.setFontFeatureSettings("tnum");
        clock.setShadowLayer(px(5), 0, px(1), 0x33000000);
        dc.addView(date);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, -2);
        clp.leftMargin = px(14);
        dc.addView(clock, clp);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-2, -2);
        dlp.leftMargin = px(22);
        statusS.addView(dc, dlp);
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        slp.topMargin = px(52);
        slp.rightMargin = px(96);
        root.addView(status, slp);

        // hero, top left
        hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hEyebrow = text(22, INK2, Typeface.NORMAL, "sans-serif-medium");
        hEyebrow.setLetterSpacing(.2f);
        hEyebrow.setAllCaps(true);
        hEyebrow.setShadowLayer(px(6), 0, px(2), 0x73000000);
        hTitle = text(108, INK, Typeface.NORMAL, "sans-serif");
        try {
            hTitle.setTypeface(Typeface.createFromAsset(getAssets(), "fonts/Unbounded-Bold.ttf"));
        } catch (RuntimeException e) {
            hTitle.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        }
        hTitle.setLetterSpacing(-.01f);
        hTitle.setShadowLayer(px(15), 0, px(6), 0x59000000);
        hTitle.setMaxLines(2);
        hTitle.setEllipsize(TextUtils.TruncateAt.END);
        hTag = text(30, INK2, Typeface.NORMAL, "sans-serif");
        hTag.setLineSpacing(0, 1.25f);
        hTag.setMaxWidth(px(780));
        hTag.setMaxLines(2);
        hTag.setShadowLayer(px(6), 0, px(2), 0x73000000);
        hero.addView(hEyebrow);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-2, -2);
        tlp.topMargin = px(18);
        hero.addView(hTitle, tlp);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(-2, -2);
        glp.topMargin = px(22);
        hero.addView(hTag, glp);
        FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(px(1000), -2);
        hlp.leftMargin = px(96);
        hlp.topMargin = px(190);
        heroWrap = new FrameLayout(this);
        heroWrap.addView(hero, new FrameLayout.LayoutParams(-1, -2));
        root.addView(heroWrap, hlp);

        // lower area: tray, inputs, all apps. It slides up when focus goes into the grid.
        ShadowLayout lowerS = new ShadowLayout(this);
        lower = lowerS;
        lower.setOrientation(LinearLayout.VERTICAL);
        lower.setClipChildren(false);
        lower.setClipToPadding(false);

        ShadowLayout trayS = new ShadowLayout(this);
        trayS.shadowAll(tileShadow());
        tray = trayS;
        tray.setClipChildren(false);
        tray.setClipToPadding(false);
        tray.setPadding(px(30), px(30), px(30), px(30));
        tray.setBackground(pill(0x6b1c1e26, 0x24ffffff, 34));
        LinearLayout.LayoutParams trp = new LinearLayout.LayoutParams(-1, -2);
        trp.leftMargin = trp.rightMargin = px(64);
        lower.addView(tray, trp);
        lowerS.shadow(tray, trayShadow());

        ShadowLayout inputsS = new ShadowLayout(this);
        inputsS.shadowAll(pillShadow());
        inputs = inputsS;
        inputs.setGravity(Gravity.CENTER_VERTICAL);
        inputs.setClipChildren(false);
        inputs.setClipToPadding(false);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(-1, -2);
        ilp.leftMargin = ilp.rightMargin = px(96);
        ilp.topMargin = px(46);
        lower.addView(inputs, ilp);

        TextView allLabel = label("Все приложения");
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-2, -2);
        alp.leftMargin = px(96);
        alp.topMargin = px(52);
        lower.addView(allLabel, alp);

        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(-1, -2);
        gp.leftMargin = gp.rightMargin = px(96);
        gp.topMargin = px(18);
        gp.bottomMargin = px(80);
        lower.addView(grid, gp);

        // Taller than the screen on purpose: the grid must lay out at full height and slide into view.
        FrameLayout.LayoutParams llp = new FrameLayout.LayoutParams(-1, px(4000));
        llp.topMargin = px(612);
        root.addView(lower, llp);
    }

    private TextView label(String s) {
        TextView t = text(20, INK3, Typeface.NORMAL, "sans-serif-medium");
        t.setLetterSpacing(.18f);
        t.setAllCaps(true);
        t.setText(s);
        return t;
    }

    // ---------------------------------------------------------------- apps

    private void loadApps() {
        PackageManager pm = getPackageManager();
        apps.clear();
        Set<String> seen = new HashSet<>();
        Intent lb = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo r : pm.queryIntentActivities(lb, 0)) {
            ActivityInfo ai = r.activityInfo;
            if (HIDDEN.contains(ai.packageName) || !seen.add(ai.packageName)) continue;
            App a = new App();
            a.pkg = ai.packageName;
            a.label = String.valueOf(r.loadLabel(pm));
            a.component = new ComponentName(ai.packageName, ai.name);
            a.leanback = true;
            a.info = ai;
            apps.add(a);
        }
        final Collator col = Collator.getInstance(new Locale("ru"));
        apps.sort((x, y) -> col.compare(x.label, y.label));
    }

    private List<String> favourites() {
        String saved = prefs.getString("fav", null);
        List<String> list = new ArrayList<>(saved == null ? Arrays.asList(DEFAULT_FAV)
                : Arrays.asList(TextUtils.split(saved, ",")));
        Set<String> installed = new HashSet<>();
        for (App a : apps) installed.add(a.pkg);
        list.retainAll(installed);
        return list;
    }

    private App app(String pkg) {
        for (App a : apps) if (a.pkg.equals(pkg)) return a;
        return null;
    }

    private void toggleFavourite(App a) {
        List<String> fav = favourites();
        if (!fav.remove(a.pkg)) {
            if (fav.size() >= MAX_FAV) return; // the menu never offers this, but stay safe
            fav.add(a.pkg);
        }
        prefs.edit().putString("fav", TextUtils.join(",", fav)).apply();
        rebuildRows("app:" + a.pkg);
    }

    private void rebuildRows(String keepFocus) {
        tray.removeAllViews();
        grid.removeAllViews();
        List<String> fav = favourites();

        int trayInner = px(1920 - 128 - 60);
        int favW = (trayInner - px(22) * (MAX_FAV - 1)) / MAX_FAV;
        int favH = favW * 9 / 16;
        for (int i = 0; i < fav.size(); i++) {
            View t = tile(app(fav.get(i)), favW, favH, 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(favW, favH);
            if (i > 0) lp.leftMargin = px(22);
            tray.addView(t, lp);
        }

        int gridW = px(1920 - 192);
        int cellW = (gridW - px(22) * (GRID_COLS - 1)) / GRID_COLS;
        int cellH = cellW * 9 / 16;
        LinearLayout row = null;
        int n = 0, rowIndex = 0;
        for (App a : apps) {
            if (fav.contains(a.pkg)) continue;
            if (n % GRID_COLS == 0) {
                ShadowLayout rowS = new ShadowLayout(this);
                rowS.shadowAll(tileShadow());
                row = rowS;
                row.setClipChildren(false);
                row.setClipToPadding(false);
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
                if (n > 0) rp.topMargin = px(26);
                grid.addView(row, rp);
                rowIndex = 2 + n / GRID_COLS;
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(cellW, cellH);
            if (n % GRID_COLS > 0) lp.leftMargin = px(22);
            row.addView(tile(a, cellW, cellH, rowIndex), lp);
            n++;
        }

        pinEdges(tray);
        for (int i = 0; i < grid.getChildCount(); i++) pinEdges((ViewGroup) grid.getChildAt(i));

        View target = keepFocus == null ? null : root.findViewWithTag(keepFocus);
        if (target == null && tray.getChildCount() > 0) target = tray.getChildAt(0);
        if (target != null) target.requestFocus();
    }

    private View tile(final App a, int w, int h, final int row) {
        final FrameLayout t = new FrameLayout(this);
        t.setTag("app:" + a.pkg);
        t.setContentDescription(a.label);
        t.setFocusable(true);
        t.setClickable(true);
        t.setBackground(pill(0xff15161b, 0, 18));
        final float radius = px(18);
        t.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
            }
        });
        t.setClipToOutline(true);
        t.setId(View.generateViewId());

        final ImageView iv = new ImageView(this);
        t.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        showArt(a, iv, h);

        t.setOnFocusChangeListener((v, has) -> {
            focusAnim(v, has, 1.13f);
            if (has && v == moving) markMoving(v);
            else if (has && moving == null) onFocusApp(a, row); // neighbours briefly get focus while a tile moves
        });
        t.setOnClickListener(v -> launch(a));
        t.setOnLongClickListener(v -> { openMenu(a, row == 0); return true; });
        t.setLongClickable(false); // long press is detected from key repeats in dispatchKeyEvent
        return t;
    }

    // ---------------------------------------------------------------- reordering favourites

    private View moving;

    private boolean skipNextUp, longFired;

    private void startMove(View v) {
        moving = v;
        skipNextUp = false;
        markMoving(v);
        App a = appForTile(v);
        setHero("Перемещение", a != null ? a.label : "", "Двигайте пультом влево или вправо. OK — сохранить.");
    }

    private App appForTile(View v) {
        Object tag = v.getTag();
        return tag instanceof String && ((String) tag).startsWith("app:") ? app(((String) tag).substring(4)) : null;
    }

    // ---------------------------------------------------------------- context menu (long press)

    private FrameLayout menuLayer;
    private String menuReturnTag;

    private void openMenu(final App a, boolean inFavourites) {
        closeMenu(false);
        menuReturnTag = "app:" + a.pkg;
        menuLayer = new FrameLayout(this);
        menuLayer.setBackgroundColor(0xa6000000);
        menuLayer.setClickable(true);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(px(36), px(36), px(36), px(28));
        panel.setBackground(pill(0xf21a1c24, 0x24ffffff, 32));
        panel.setClipChildren(false);
        panel.setClipToPadding(false);

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        Drawable d = art.get(a.pkg);
        if (d != null) {
            FrameLayout frame = new FrameLayout(this);
            frame.setBackground(pill(0xff15161b, 0, 14));
            frame.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
            frame.setClipToOutline(true);
            ImageView iv = new ImageView(this);
            applyArt(iv, d, artIsIcon.contains(a.pkg), px(108));
            frame.addView(iv, new FrameLayout.LayoutParams(-1, -1));
            LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(px(192), px(108));
            flp.rightMargin = px(26);
            head.addView(frame, flp);
        }
        TextView name = text(40, INK, Typeface.BOLD, "sans-serif");
        name.setText(a.label);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        head.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-1, -2);
        hlp.bottomMargin = px(26);
        panel.addView(head, hlp);

        List<View> items = new ArrayList<>();
        items.add(menuItem("Открыть", () -> launch(a)));
        if (inFavourites) {
            items.add(menuItem("Переместить", () -> {
                View t = root.findViewWithTag("app:" + a.pkg);
                if (t != null) startMove(t);
            }));
            items.add(menuItem("Убрать из избранного", () -> toggleFavourite(a)));
        } else if (favourites().size() < MAX_FAV) {
            items.add(menuItem("Добавить в избранное", () -> toggleFavourite(a)));
        } else {
            items.add(menuNote("В избранном нет места — сначала уберите оттуда одно приложение"));
        }
        items.add(menuItem("О приложении", () -> start(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.parse("package:" + a.pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), a.label)));
        if ((a.info.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
            items.add(menuItem("Удалить", () -> start(new Intent(Intent.ACTION_DELETE,
                    android.net.Uri.parse("package:" + a.pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), a.label)));
        }

        // Explicit up/down chain so focus never has to be guessed, and it never leaves the panel.
        List<View> focusable = new ArrayList<>();
        for (View v : items) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = px(10);
            panel.addView(v, lp);
            if (v.isFocusable()) focusable.add(v);
        }
        for (int i = 0; i < focusable.size(); i++) {
            View v = focusable.get(i);
            v.setNextFocusLeftId(v.getId());
            v.setNextFocusRightId(v.getId());
            v.setNextFocusUpId(focusable.get(Math.max(0, i - 1)).getId());
            v.setNextFocusDownId(focusable.get(Math.min(focusable.size() - 1, i + 1)).getId());
        }
        View first = focusable.get(0);

        menuLayer.addView(panel, new FrameLayout.LayoutParams(px(760), -2, Gravity.CENTER));
        root.addView(menuLayer, new FrameLayout.LayoutParams(-1, -1));
        menuLayer.setAlpha(0f);
        panel.setScaleX(.96f);
        panel.setScaleY(.96f);
        menuLayer.animate().alpha(1f).setDuration(140).start();
        panel.animate().scaleX(1f).scaleY(1f).setDuration(180).setInterpolator(new DecelerateInterpolator(2f)).start();
        first.requestFocus();
        // Everything behind the menu is out of reach for the remote until the menu closes.
        lower.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
    }

    private View menuItem(String label, final Runnable action) {
        final TextView t = text(28, INK, Typeface.NORMAL, "sans-serif");
        t.setId(View.generateViewId());
        t.setText(label);
        t.setPadding(px(28), px(20), px(28), px(20));
        t.setFocusable(true);
        t.setClickable(true);
        final GradientDrawable idle = pill(0x00000000, 0, 16);
        final GradientDrawable on = pill(0xebffffff, 0, 16);
        t.setBackground(idle);
        t.setOnFocusChangeListener((v, has) -> {
            t.setBackground(has ? on : idle);
            t.setTextColor(has ? 0xff0b0c10 : INK);
        });
        t.setOnClickListener(v -> { closeMenu(true); action.run(); });
        return t;
    }

    private View menuNote(String label) {
        TextView t = text(24, INK3, Typeface.NORMAL, "sans-serif");
        t.setText(label);
        t.setPadding(px(28), px(18), px(28), px(18));
        return t;
    }

    private void closeMenu(boolean restoreFocus) {
        if (menuLayer == null) return;
        lower.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        // Hand focus back to the tile *before* the menu disappears. Removing the focused menu item
        // first leaves the window without focus for a moment; Android then focuses the first
        // favourite on its own and the screen scrolls back to the top.
        if (restoreFocus && menuReturnTag != null) {
            View t = root.findViewWithTag(menuReturnTag);
            if (t != null) t.requestFocus();
        }
        root.removeView(menuLayer);
        menuLayer = null;
    }

    private void markMoving(View v) {
        GradientDrawable ring = new GradientDrawable();
        ring.setCornerRadius(px(18));
        ring.setStroke(px(10), 0xffffc940);
        ring.setColor(0x26ffc940);
        v.setForeground(ring);
        v.animate().scaleX(1.2f).scaleY(1.2f).translationY(-px(10)).setDuration(160)
                .setUpdateListener(x -> { if (v.getParent() instanceof View) ((View) v.getParent()).invalidate(); }).start();
    }

    private void shiftMoving(int dir) {
        int from = tray.indexOfChild(moving);
        int to = from + dir;
        if (from < 0 || to < 0 || to >= tray.getChildCount()) return;
        ViewGroup.LayoutParams lp = moving.getLayoutParams();
        tray.removeViewAt(from);
        tray.addView(moving, to, lp);
        for (int i = 0; i < tray.getChildCount(); i++) {
            View c = tray.getChildAt(i);
            ((LinearLayout.LayoutParams) c.getLayoutParams()).leftMargin = i == 0 ? 0 : px(22);
            c.setNextFocusLeftId(View.NO_ID);
            c.setNextFocusRightId(View.NO_ID);
        }
        pinEdges(tray);
        tray.requestLayout();
        moving.requestFocus();
        markMoving(moving);
    }

    private void finishMove() {
        View v = moving;
        moving = null;
        List<String> order = new ArrayList<>();
        for (int i = 0; i < tray.getChildCount(); i++) {
            Object tag = tray.getChildAt(i).getTag();
            if (tag instanceof String && ((String) tag).startsWith("app:")) order.add(((String) tag).substring(4));
        }
        prefs.edit().putString("fav", TextUtils.join(",", order)).apply();
        focusAnim(v, v.hasFocus(), 1.13f);
        App a = appForTile(v);
        if (a != null) onFocusApp(a, 0);
    }

    /** Left on the first item and right on the last item of a row stay put instead of jumping rows. */
    private static void pinEdges(ViewGroup row) {
        View first = null, last = null;
        for (int i = 0; i < row.getChildCount(); i++) {
            View c = row.getChildAt(i);
            if (!c.isFocusable()) continue;
            if (first == null) first = c;
            last = c;
        }
        if (first != null) first.setNextFocusLeftId(first.getId());
        if (last != null) last.setNextFocusRightId(last.getId());
    }

    private void focusAnim(View v, boolean has, float scale) {
        v.animate().cancel();
        v.animate().scaleX(has ? scale : 1f).scaleY(has ? scale : 1f)
                .translationZ(has ? px(24) : 0).translationY(0)
                .setUpdateListener(a -> { if (v.getParent() instanceof View) ((View) v.getParent()).invalidate(); })
                .setDuration(has ? 200 : 160).setInterpolator(new DecelerateInterpolator(2f)).start();
        if (has) {
            GradientDrawable ring = new GradientDrawable();
            ring.setCornerRadius(px(18));
            ring.setStroke(px(8), Color.WHITE);
            ring.setColor(Color.TRANSPARENT);
            v.setForeground(ring);
        } else {
            v.setForeground(null);
        }
    }

    private void showArt(final App a, final ImageView iv, final int h) {
        Drawable cached = art.get(a.pkg);
        if (cached != null) {
            applyArt(iv, cached, artIsIcon.contains(a.pkg), h);
            return;
        }
        io.execute(() -> {
            PackageManager pm = getPackageManager();
            Drawable d = a.info.loadBanner(pm);
            if (d == null) d = a.info.applicationInfo.loadBanner(pm);
            boolean icon = d == null;
            if (icon) d = a.info.loadIcon(pm);
            int[] pal = palette.containsKey(a.pkg) ? null : extractPalette(d);
            final Drawable fd = d;
            ui.post(() -> {
                art.put(a.pkg, fd);
                if (icon) artIsIcon.add(a.pkg);
                if (pal != null && !INFO.containsKey(a.pkg)) palette.put(a.pkg, pal);
                applyArt(iv, fd, icon, h);
                if (("app:" + a.pkg).equals(focusedKey)) ambient.setColors(colorsFor(a.pkg));
            });
        });
    }

    private void applyArt(ImageView iv, Drawable d, boolean icon, int h) {
        Drawable.ConstantState cs = d.getConstantState();
        iv.setImageDrawable(cs != null ? cs.newDrawable(getResources()) : d);
        if (icon) {
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int p = h / 4;
            iv.setPadding(p, p, p, p);
        } else {
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setPadding(0, 0, 0, 0);
        }
    }

    /** Picks the most vivid colour of the banner and builds an ambient palette around it. */
    private static int[] extractPalette(Drawable src) {
        try {
            Drawable d = src.getConstantState() != null ? src.getConstantState().newDrawable().mutate() : src;
            Bitmap bm = Bitmap.createBitmap(32, 18, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bm);
            d.setBounds(0, 0, 32, 18);
            d.draw(c);
            float[] hsv = new float[3], best = null;
            float bestScore = 0;
            for (int y = 0; y < 18; y++)
                for (int x = 0; x < 32; x++) {
                    int p = bm.getPixel(x, y);
                    if (Color.alpha(p) < 200) continue;
                    Color.colorToHSV(p, hsv);
                    float score = hsv[1] * hsv[2];
                    if (hsv[2] > .25f && score > bestScore) { bestScore = score; best = hsv.clone(); }
                }
            bm.recycle();
            if (best == null || bestScore < .12f) return new int[]{0xff0a0c12, 0xff4a5670, 0xff9aa6c0, 0xff1c2233};
            float hue = best[0];
            return new int[]{
                    Color.HSVToColor(new float[]{hue, Math.min(1, best[1] * .7f), .08f}),
                    Color.HSVToColor(new float[]{hue, best[1], Math.max(.7f, best[2])}),
                    Color.HSVToColor(new float[]{(hue + 15) % 360, best[1] * .55f, 1f}),
                    Color.HSVToColor(new float[]{(hue + 330) % 360, best[1], .4f})};
        } catch (Throwable t) {
            return null;
        }
    }

    private int[] colorsFor(String pkg) {
        Object[] info = INFO.get(pkg);
        if (info != null) return (int[]) info[2];
        int[] p = palette.get(pkg);
        return p != null ? p : new int[]{0xff0a0c12, 0xff4a5670, 0xff9aa6c0, 0xff1c2233};
    }

    private void onFocusApp(App a, int row) {
        focusedKey = "app:" + a.pkg;
        Object[] info = INFO.get(a.pkg);
        String eyebrow = info != null ? (String) info[0] : category(a.info.applicationInfo);
        String tag = info != null ? (String) info[1] : "";
        setHero(eyebrow, a.label, tag);
        ambient.setColors(colorsFor(a.pkg));
        slideTo(row);
    }

    private static String category(ApplicationInfo ai) {
        switch (ai.category) {
            case ApplicationInfo.CATEGORY_VIDEO: return "Видео";
            case ApplicationInfo.CATEGORY_AUDIO: return "Музыка";
            case ApplicationInfo.CATEGORY_GAME: return "Игры";
            case ApplicationInfo.CATEGORY_IMAGE: return "Фото";
            case ApplicationInfo.CATEGORY_NEWS: return "Новости";
            default: return "Приложение";
        }
    }

    private void launch(App a) {
        Intent i = new Intent(Intent.ACTION_MAIN)
                .addCategory(a.leanback ? Intent.CATEGORY_LEANBACK_LAUNCHER : Intent.CATEGORY_LAUNCHER)
                .setComponent(a.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        start(i, a.label);
    }

    private void start(Intent i, String what) {
        try {
            startActivity(i);
        } catch (ActivityNotFoundException | SecurityException e) {
            toast("Не получилось открыть: " + what);
        }
    }

    // ---------------------------------------------------------------- inputs

    private void rebuildInputs() {
        String keep = focusedKey;
        inputs.removeAllViews();
        inputs.addView(label("Источники"), new LinearLayout.LayoutParams(-2, -2));

        TvInputManager tim = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
        List<TvInputInfo> hdmi = new ArrayList<>();
        TvInputInfo tuner = null;
        if (tim != null) {
            for (TvInputInfo info : tim.getTvInputList()) {
                if (info.getType() == TvInputInfo.TYPE_HDMI && info.isPassthroughInput()) hdmi.add(info);
                else if (info.getType() == TvInputInfo.TYPE_TUNER && tuner == null
                        && info.getServiceInfo().packageName.startsWith("com.tcl")) tuner = info;
            }
        }
        // Hardware ids grow with the port number on this TV, so sorting by id gives HDMI 1, 2, 3, 4.
        hdmi.sort((x, y) -> x.getId().compareTo(y.getId()));
        boolean first = true;
        // No "connected" badge: this TV reports the same state for every port and sees no CEC devices,
        // so any connected/disconnected hint would be a guess.
        for (final TvInputInfo info : hdmi) {
            String port = String.valueOf(info.loadLabel(this));
            CharSequence custom = info.loadCustomLabel(this);
            final String name = TextUtils.isEmpty(custom) || port.contentEquals(custom) ? port : port + " · " + custom;
            View v = source("⮕", name, null, () -> {
                Intent i = new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(info.getId()))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                start(i, name);
            }, name, "Нажмите OK, чтобы переключиться на этот вход.");
            addSource(v, first);
            first = false;
        }
        Intent tv = getPackageManager().getLaunchIntentForPackage("com.tcl.tv");
        if (tuner != null && tv != null) {
            final Intent fTv = tv.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            addSource(source("◉", "Эфир", null, () -> start(fTv, "Эфир"), "Эфир", "Эфирные и кабельные каналы."), first);
            first = false;
        }
        View gear = source("⚙", "Настройки", null,
                () -> start(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), "Настройки"),
                "Настройки", "Изображение, звук, сеть и всё остальное.");
        View spacer = new View(this);
        inputs.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        inputs.addView(gear, new LinearLayout.LayoutParams(-2, -2));
        pinEdges(inputs);

        if (keep != null && keep.startsWith("src:")) {
            View t = inputs.findViewWithTag(keep);
            if (t != null) t.requestFocus();
        }
    }

    private String inputLabel(TvInputInfo info) {
        CharSequence c = info.loadCustomLabel(this);
        if (TextUtils.isEmpty(c)) c = info.loadLabel(this);
        return String.valueOf(c);
    }

    private void addSource(View v, boolean first) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.leftMargin = px(first ? 36 : 18);
        inputs.addView(v, lp);
    }

    private View source(String glyph, final String name, String sub, final Runnable action,
                        final String heroTitle, final String heroTag) {
        final TextView t = text(24, INK2, Typeface.NORMAL, "sans-serif");
        t.setTag("src:" + name);
        t.setId(View.generateViewId());
        if (sub == null) {
            t.setText(name);
        } else {
            // A green dot in front of inputs that have a device connected.
            android.text.SpannableString sp = new android.text.SpannableString(sub + "  " + name);
            sp.setSpan(new android.text.style.ForegroundColorSpan(0xff3ddc84), 0, sub.length(), 0);
            t.setText(sp);
        }
        t.setPadding(px(26), px(16), px(26), px(16));
        t.setFocusable(true);
        t.setClickable(true);
        final GradientDrawable idle = pill(0x14ffffff, 0x24ffffff, 18);
        final GradientDrawable on = pill(0xebffffff, 0, 18);
        t.setBackground(idle);
        t.setOnFocusChangeListener((v, has) -> {
            t.setBackground(has ? on : idle);
            t.setTextColor(has ? 0xff0b0c10 : INK2);
            v.animate().scaleX(has ? 1.08f : 1f).scaleY(has ? 1.08f : 1f).translationZ(has ? px(16) : 0)
                    .setUpdateListener(x -> inputs.invalidate()).setDuration(180).start();
            if (has) {
                focusedKey = "src:" + name;
                setHero(name.equals("Настройки") ? "Система" : "Источник сигнала", heroTitle, heroTag);
                ambient.setColors(INPUT_COLORS);
                slideTo(1);
            }
        });
        t.setOnClickListener(v -> action.run());
        return t;
    }

    // ---------------------------------------------------------------- hero, scrolling

    private String heroShown; // what the hero shows, or is already animating towards

    private void setHero(final String eyebrow, final String title, final String tag) {
        // Focus coming back to the same tile (closing the menu, returning from an app) must not
        // fade the title out and back in.
        String key = eyebrow + '\n' + title + '\n' + tag;
        if (key.equals(heroShown)) return;
        heroShown = key;
        android.util.Log.v("Kinoteatr", "hero swap: " + title);
        final int token = ++heroToken;
        hero.animate().cancel();
        hero.animate().alpha(0f).translationY(px(10)).setDuration(120).withEndAction(() -> {
            if (token != heroToken) return;
            hEyebrow.setText(eyebrow);
            hTitle.setText(title);
            hTitle.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(title.length() > 11 ? 72 : 108));
            hTag.setText(tag);
            hTag.setVisibility(TextUtils.isEmpty(tag) ? View.GONE : View.VISIBLE);
            hero.animate().alpha(1f).translationY(0).setDuration(200).start();
        }).start();
    }

    private int slidRow = -1;

    /**
     * Scrolls the lower area so the focused row is in view. In the grid the header (title, clock,
     * weather) fades away so nothing slides underneath it, and on the lowest rows the favourites
     * shelf fades too instead of being cut by the top edge of the screen.
     */
    private void slideTo(int row) {
        if (row == slidRow) return;
        slidRow = row;
        boolean deep = row >= 2;
        float y = deep ? -px(330 + (row - 2) * 132) : 0;
        if (deep) {
            // Don't scroll past the end: the last row stops near the bottom edge, no empty band below.
            float minY = root.getHeight() - px(110) - (px(612) + grid.getBottom());
            y = Math.min(-px(330), Math.max(y, minY));
        }
        float trayTop = px(612) + y;
        lower.animate().translationY(y).setDuration(380).setInterpolator(new DecelerateInterpolator(2.2f)).start();
        heroWrap.animate().alpha(deep ? 0f : 1f).setDuration(260).start();
        status.animate().alpha(deep ? 0f : 1f).setDuration(260).start();
        // lower paints the tray's baked shadow, so it has to redraw while the tray fades.
        tray.animate().alpha(trayTop < px(130) ? 0f : 1f).setDuration(260)
                .setUpdateListener(a -> lower.invalidate()).start();
    }

    // ---------------------------------------------------------------- clock, weather

    private void tickClock() {
        Date now = new Date();
        Locale ru = new Locale("ru");
        clock.setText(new SimpleDateFormat("HH:mm", ru).format(now));
        date.setText(new SimpleDateFormat("EEE, d MMMM", ru).format(now));
    }

    private long weatherAt;

    /**
     * Weather city is set once by the setup script:
     * am start -n app.kinoteatr.launcher/.MainActivity --es city Минск --es lat 53.90 --es lon 27.56
     */
    private boolean applyConfig(Intent i) {
        if (i == null || !i.hasExtra("city")) return false;
        try {
            prefs.edit().putString("city", i.getStringExtra("city"))
                    .putFloat("lat", Float.parseFloat(i.getStringExtra("lat")))
                    .putFloat("lon", Float.parseFloat(i.getStringExtra("lon"))).apply();
        } catch (RuntimeException e) {
            return false;
        }
        weatherAt = 0;
        refreshWeather();
        return true;
    }

    private void refreshWeather() {
        if (System.currentTimeMillis() - weatherAt < 30 * 60_000L) return;
        weatherAt = System.currentTimeMillis();
        io.execute(() -> {
            HttpURLConnection c = null;
            try {
                URL u = new URL(String.format(Locale.US,
                        "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f&current=temperature_2m,weather_code",
                        prefs.getFloat("lat", 53.90f), prefs.getFloat("lon", 27.56f)));
                final String city = prefs.getString("city", "Минск");
                c = (HttpURLConnection) u.openConnection();
                c.setConnectTimeout(5000);
                c.setReadTimeout(5000);
                StringBuilder sb = new StringBuilder();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()))) {
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line);
                }
                JSONObject cur = new JSONObject(sb.toString()).getJSONObject("current");
                long t = Math.round(cur.getDouble("temperature_2m"));
                int code = cur.getInt("weather_code");
                final String txt = weatherGlyph(code) + "  " + (t > 0 ? "+" : "") + t + "°  " + city;
                ui.post(() -> { weather.setText(txt); weather.setVisibility(View.VISIBLE); });
            } catch (Exception e) {
                weatherAt = 0;
            } finally {
                if (c != null) c.disconnect();
            }
        });
    }

    private static String weatherGlyph(int code) {
        if (code == 0) return "☀";
        if (code <= 3) return "⛅";
        if (code <= 48) return "☁";
        if (code <= 67 || (code >= 80 && code <= 82)) return "☔";
        if (code <= 77 || code == 85 || code == 86) return "❄";
        return "⚡";
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getAction() == KeyEvent.ACTION_DOWN) ambient.poke();
        int kc = e.getKeyCode();
        boolean ok = kc == KeyEvent.KEYCODE_DPAD_CENTER || kc == KeyEvent.KEYCODE_ENTER;
        if (ok && moving == null) {
            View f = getCurrentFocus();
            boolean onTile = f != null && f.getTag() instanceof String && ((String) f.getTag()).startsWith("app:");
            if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 1 && onTile) {
                longFired = true;
                f.setPressed(false);
                f.performLongClick();
                skipNextUp = false; // the release is swallowed below
                return true;
            }
            if (longFired) {
                if (e.getAction() == KeyEvent.ACTION_UP) longFired = false;
                return true; // swallow the rest of the long press so it never becomes a click
            }
        }
        if (moving != null && longFired) {
            if (e.getAction() == KeyEvent.ACTION_UP && ok) longFired = false;
            if (ok) return true;
        }
        if (moving != null) {
            int k = e.getKeyCode();
            if (e.getAction() == KeyEvent.ACTION_DOWN) {
                if (k == KeyEvent.KEYCODE_DPAD_LEFT) shiftMoving(-1);
                else if (k == KeyEvent.KEYCODE_DPAD_RIGHT) shiftMoving(1);
            } else if (e.getAction() == KeyEvent.ACTION_UP && (k == KeyEvent.KEYCODE_DPAD_CENTER
                    || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_BACK)) {
                if (skipNextUp && k != KeyEvent.KEYCODE_BACK) skipNextUp = false;
                else finishMove();
            }
            return true; // every key belongs to the move while a tile is picked up
        }
        if (menuLayer != null && kc == KeyEvent.KEYCODE_BACK) {
            if (e.getAction() == KeyEvent.ACTION_UP) closeMenu(true);
            return true;
        }
        // Remotes that do have a Menu key open the same context menu as a long press.
        if (kc == KeyEvent.KEYCODE_MENU && e.getAction() == KeyEvent.ACTION_UP && menuLayer == null) {
            View f = getCurrentFocus();
            App a = f != null ? appForTile(f) : null;
            if (a != null) {
                openMenu(a, f.getParent() == tray);
                return true;
            }
        }
        return super.dispatchKeyEvent(e);
    }
}
