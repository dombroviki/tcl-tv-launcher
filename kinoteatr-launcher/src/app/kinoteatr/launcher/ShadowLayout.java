package app.kinoteatr.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import android.widget.LinearLayout;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * LinearLayout that paints soft drop shadows under its children from pre-blurred bitmaps.
 * Each shadow is blurred once (at quarter resolution) and cached, so a frame only costs one
 * texture blit per child — unlike elevation shadows, which the GPU recomputes every frame.
 * Only children with a background get a shadow, so labels and spacers are skipped.
 * The shadow follows the child's scale and translation, so focus animations carry it along.
 */
public class ShadowLayout extends LinearLayout {

    /** CSS-like box-shadow: offset y, blur, spread (negative shrinks), opacity, corner radius. */
    public static final class Spec {
        final float dy, blur, spread, alpha, radius;
        final boolean glass;
        /** @param glass true for see-through children: the shadow is cut out under them, like CSS. */
        public Spec(float dy, float blur, float spread, float alpha, float radius, boolean glass) {
            this.dy = dy; this.blur = blur; this.spread = spread; this.alpha = alpha; this.radius = radius; this.glass = glass;
        }
    }

    private static final float K = 0.25f; // bitmaps are blurred at quarter size and stretched back
    private static final Map<String, Bitmap> CACHE = new HashMap<>();

    private final Map<View, Spec> specs = new WeakHashMap<>();
    // Per-child lookup, so a frame does no string building or hashing of specs (that churned the GC).
    private final Map<View, Bitmap> cached = new WeakHashMap<>();
    private final Map<View, Integer> cachedW = new WeakHashMap<>(), cachedH = new WeakHashMap<>();
    private final Paint blit = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final RectF dst = new RectF();
    private final Path hole = new Path();
    private Spec all;

    public ShadowLayout(Context c) {
        super(c);
        setWillNotDraw(false);
        setClipChildren(false);
        setClipToPadding(false);
    }

    /** Shadow for every child that has a background. */
    public void shadowAll(Spec s) { all = s; invalidate(); }

    /** Shadow for one child; overrides {@link #shadowAll}. */
    public void shadow(View child, Spec s) { specs.put(child, s); invalidate(); }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        for (int i = 0; i < getChildCount(); i++) {
            View ch = getChildAt(i);
            Spec s = specs.containsKey(ch) ? specs.get(ch) : all;
            if (s == null || ch.getVisibility() != VISIBLE || ch.getBackground() == null) continue;
            if (ch.isFocused() && ch.getTranslationZ() > 0) continue; // it already casts a live elevation shadow
            int w = ch.getWidth(), h = ch.getHeight();
            if (w == 0 || h == 0) continue;
            Bitmap b = cached.get(ch);
            if (b == null || cachedW.get(ch) != w || cachedH.get(ch) != h) {
                b = bitmap(w, h, s);
                cached.put(ch, b);
                cachedW.put(ch, w);
                cachedH.put(ch, h);
            }
            float shrink = -s.spread, pad = s.blur * 1.5f;
            canvas.save();
            canvas.translate(ch.getLeft(), ch.getTop());
            canvas.concat(ch.getMatrix());
            if (s.glass) {
                hole.reset();
                hole.addRoundRect(0, 0, w, h, s.radius, s.radius, Path.Direction.CW);
                canvas.clipOutPath(hole);
            }
            dst.set(shrink - pad, shrink + s.dy - pad, w - shrink + pad, h - shrink + s.dy + pad);
            blit.setAlpha(Math.round(255 * ch.getAlpha()));
            canvas.drawBitmap(b, null, dst, blit);
            canvas.restore();
        }
        super.dispatchDraw(canvas);
    }

    private static Bitmap bitmap(int w, int h, Spec s) {
        String key = w + "x" + h + "/" + s.dy + "/" + s.blur + "/" + s.spread + "/" + s.alpha + "/" + s.radius;
        Bitmap b = CACHE.get(key);
        if (b != null) return b;
        float shrink = -s.spread, pad = s.blur * 1.5f;
        float bw = (w - 2 * shrink + 2 * pad) * K, bh = (h - 2 * shrink + 2 * pad) * K;
        b = Bitmap.createBitmap(Math.max(1, Math.round(bw)), Math.max(1, Math.round(bh)), Bitmap.Config.ALPHA_8);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Math.round(255 * s.alpha) << 24);
        // CSS blur B ≈ gaussian sigma B/2; Android's radius maps to sigma ≈ 0.577·r.
        p.setMaskFilter(new BlurMaskFilter(Math.max(1f, s.blur * K * 0.87f), BlurMaskFilter.Blur.NORMAL));
        float r = s.radius * K;
        new Canvas(b).drawRoundRect(pad * K, pad * K, bw - pad * K, bh - pad * K, r, r, p);
        CACHE.put(key, b);
        return b;
    }
}
