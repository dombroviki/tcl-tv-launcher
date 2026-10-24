package app.kinoteatr.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.Choreographer;
import android.view.View;

/**
 * Slowly drifting colour blobs behind the home screen.
 * The blobs are soft, so they are rendered in software into a 160×90 bitmap and the GPU
 * only stretches one texture per frame. Updates run at ~30 fps during a cross-fade,
 * ~10 fps at rest, and stop completely when the launcher is paused.
 */
public class AmbientView extends View implements Choreographer.FrameCallback {

    private static final int BW = 160, BH = 90; // blobs are so soft that 160×90 stretched looks identical

    // x, y, radius (fractions of width/height), drift speed x, drift speed y, phase, colour slot
    private static final float[][] BLOBS = {
            {.72f, .30f, .55f, .05f, .04f, 0f, 1},
            {.92f, .62f, .45f, .04f, .06f, 2f, 2},
            {.45f, .12f, .40f, .06f, .03f, 4f, 3},
            {.60f, .55f, .35f, .03f, .05f, 1f, 1},
    };

    private final float[][] cur = new float[4][3];
    private final float[][] tgt = new float[4][3];
    private final RadialGradient[] shaders = new RadialGradient[BLOBS.length];
    private final Paint blobPaint = new Paint();
    private final Paint blitPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Matrix matrix = new Matrix();
    private final Bitmap buf = Bitmap.createBitmap(BW, BH, Bitmap.Config.ARGB_8888);
    private final Canvas bufCanvas = new Canvas(buf);
    private final Rect dst = new Rect();
    private final long start = SystemClock.uptimeMillis();
    private boolean hasColors, running, changing, shadersDirty = true;
    private long lastFrameNanos;

    public AmbientView(Context c) {
        super(c);
        blobPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SCREEN));
        buf.eraseColor(0xff000000);
    }

    public void setColors(int[] c) {
        if (hasColors && same(c)) { poke(); return; } // same app again: no cross-fade to the same colour
        for (int i = 0; i < 4; i++) {
            tgt[i][0] = (c[i] >> 16) & 0xff;
            tgt[i][1] = (c[i] >> 8) & 0xff;
            tgt[i][2] = c[i] & 0xff;
            if (!hasColors) System.arraycopy(tgt[i], 0, cur[i], 0, 3);
        }
        hasColors = true;
        changing = true;
        shadersDirty = true;
        render();
        poke();
    }

    private boolean same(int[] c) {
        for (int i = 0; i < 4; i++)
            if (tgt[i][0] != ((c[i] >> 16) & 0xff) || tgt[i][1] != ((c[i] >> 8) & 0xff) || tgt[i][2] != (c[i] & 0xff))
                return false;
        return true;
    }

    public void start() {
        running = true;
        poke();
    }

    public void stop() {
        running = false;
        looping = false;
        Choreographer.getInstance().removeFrameCallback(this);
    }

    /** Keeps the backdrop moving for a while after the last remote press, then it freezes to save CPU. */
    public void poke() {
        awakeUntil = SystemClock.uptimeMillis() + AWAKE_MS;
        if (running && !looping) {
            looping = true;
            Choreographer.getInstance().postFrameCallback(this);
        }
    }

    private static final long AWAKE_MS = 20_000;
    private long awakeUntil;
    private boolean looping;

    @Override
    public void doFrame(long now) {
        if (!running) { looping = false; return; }
        if (!changing && SystemClock.uptimeMillis() > awakeUntil) { looping = false; return; }
        long minGap = changing ? 33_000_000L : 100_000_000L;
        if (now - lastFrameNanos >= minGap) {
            lastFrameNanos = now;
            render();
        }
        Choreographer.getInstance().postFrameCallback(this);
    }

    private static int rgb(float[] c, float a) {
        return ((int) (a * 255) << 24) | ((int) c[0] << 16) | ((int) c[1] << 8) | (int) c[2];
    }

    private void render() {
        if (!hasColors) return;
        if (changing) {
            float diff = 0;
            for (int i = 0; i < 4; i++)
                for (int j = 0; j < 3; j++) {
                    float d = tgt[i][j] - cur[i][j];
                    cur[i][j] += d * 0.12f;
                    diff = Math.max(diff, Math.abs(d));
                }
            if (diff < 0.6f) {
                for (int i = 0; i < 4; i++) System.arraycopy(tgt[i], 0, cur[i], 0, 3);
                changing = false;
            }
            shadersDirty = true;
        }
        if (shadersDirty) {
            for (int i = 0; i < BLOBS.length; i++) {
                float[] col = cur[(int) BLOBS[i][6]];
                shaders[i] = new RadialGradient(0, 0, BLOBS[i][2] * BW,
                        new int[]{rgb(col, .8f), rgb(col, .26f), rgb(col, 0f)},
                        new float[]{0f, .5f, 1f}, Shader.TileMode.CLAMP);
            }
            shadersDirty = false;
        }
        bufCanvas.drawColor(rgb(cur[0], 1f), PorterDuff.Mode.SRC);
        float t = (SystemClock.uptimeMillis() - start) / 1000f;
        for (int i = 0; i < BLOBS.length; i++) {
            float[] b = BLOBS[i];
            float x = (b[0] + (float) Math.sin(t * b[3] + b[5]) * .08f) * BW;
            float y = (b[1] + (float) Math.cos(t * b[4] + b[5]) * .08f) * BH;
            matrix.setTranslate(x, y);
            shaders[i].setLocalMatrix(matrix);
            blobPaint.setShader(shaders[i]);
            bufCanvas.drawRect(0, 0, BW, BH, blobPaint);
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        dst.set(0, 0, getWidth(), getHeight());
        canvas.drawBitmap(buf, null, dst, blitPaint);
    }
}
