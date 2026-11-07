package app.kinoteatr.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.service.dreams.DreamService;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Screensaver: a black screen with a large clock, the date and the last known weather, centred.
 * Only the digits light up, which on a mini-LED keeps almost the whole backlight off.
 * It never touches the network: weather comes from the launcher's cache. Any key closes it.
 */
public class ClockDream extends DreamService {

    private TextView clock, meta;

    private final BroadcastReceiver tick = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { update(); }
    };

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(true);

        float s = getResources().getDisplayMetrics().widthPixels / 1920f;
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setGravity(Gravity.CENTER_HORIZONTAL);

        clock = new TextView(this);
        clock.setTextSize(TypedValue.COMPLEX_UNIT_PX, 280 * s);
        clock.setTextColor(0xe6f4f5f8);
        clock.setIncludeFontPadding(false);
        clock.setSingleLine(true); // a wrapped "21:" + hidden "47" was the first-run bug
        clock.setLetterSpacing(-.01f);
        clock.setFontFeatureSettings("tnum");
        try {
            clock.setTypeface(Typeface.createFromAsset(getAssets(), "fonts/Jost-ExtraLight.ttf"));
        } catch (RuntimeException e) {
            clock.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        }
        clock.setGravity(Gravity.CENTER_HORIZONTAL);
        block.addView(clock, new LinearLayout.LayoutParams(-1, -2));

        meta = new TextView(this);
        meta.setTextSize(TypedValue.COMPLEX_UNIT_PX, 30 * s);
        meta.setTextColor(0x75f4f5f8);
        meta.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        meta.setGravity(Gravity.CENTER_HORIZONTAL);
        meta.setSingleLine(true);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(-1, -2);
        mlp.topMargin = Math.round(26 * s);
        block.addView(meta, mlp);

        // Full-width block, text centred inside: the width never depends on text set later.
        root.addView(block, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
        setContentView(root);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        update();
        IntentFilter f = new IntentFilter(Intent.ACTION_TIME_TICK);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(tick, f);
    }

    @Override
    public void onDreamingStopped() {
        try { unregisterReceiver(tick); } catch (IllegalArgumentException ignored) { }
        super.onDreamingStopped();
    }

    private void update() {
        Locale ru = new Locale("ru");
        Date now = new Date();
        clock.setText(new SimpleDateFormat("HH:mm", ru).format(now));
        String date = new SimpleDateFormat("EEEE, d MMMM", ru).format(now);
        String w = getSharedPreferences("home", MODE_PRIVATE).getString("weatherText", "");
        meta.setText(TextUtils.isEmpty(w) ? date : date + "   ·   " + w);
    }
}
