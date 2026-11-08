package app.kinoteatr.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.Choreographer;
import android.view.View;


/**
 * Backdrop: an almost black screen with one soft glow of the focused app's colour under the
 * favourites shelf. Nothing moves; when focus changes the colour cross-fades, then it stays still.
 *
 * Banding fix (seen on the mini-LED panel): the glow is drawn by the GPU at full resolution
 * (no stretched low-res bitmap), with a bell-shaped falloff instead of linear steps and dithering on.
 * No grain overlay: on the mini-LED it read as grey noise, so the black stays truly black.
 */
public class AmbientView extends View implements Choreographer.FrameCallback {

    private static final int BASE = 0xff000000; // true black: on mini-LED the backlight there stays off
    // Glow centre and radii as fractions of the screen (matches the approved mockup).
    private static final float CX = .5f, CY = .833f, RX = .55f, RY = .35f;
    private static final float ALPHA = .42f;
    private static final int STOPS = 16;

    private final float[] cur = new float[3];
    private final float[] tgt = new float[3];
    private final Paint glowPaint = new Paint(Paint.DITHER_FLAG);
    private final Matrix matrix = new Matrix();
    private final int[] colors = new int[STOPS];
    private final float[] positions = new float[STOPS];
    private final float[] falloff = new float[STOPS];
    private boolean hasColors, running, changing, looping, shaderDirty = true;
    private long lastFrameNanos;

    public AmbientView(Context c) {
        super(c);
        glowPaint.setDither(true);
        // Gaussian-like bell that reaches exactly 0 at the edge: no visible rim.
        float e = (float) Math.exp(-4);
        for (int i = 0; i < STOPS; i++) {
            float t = i / (float) (STOPS - 1);
            positions[i] = t;
            falloff[i] = ((float) Math.exp(-4 * t * t) - e) / (1 - e);
        }

    }

    /** Uses the second colour of an app palette (its vivid tone) for the glow. */
    public void setColors(int[] palette) {
        int c = palette[1];
        float r = (c >> 16) & 0xff, g = (c >> 8) & 0xff, b = c & 0xff;
        if (hasColors && tgt[0] == r && tgt[1] == g && tgt[2] == b) return;
        tgt[0] = r; tgt[1] = g; tgt[2] = b;
        if (!hasColors) {
            System.arraycopy(tgt, 0, cur, 0, 3);
            hasColors = true;
            shaderDirty = true;
            invalidate();
            return;
        }
        changing = true;
        loop();
    }

    public void start() { running = true; if (changing) loop(); }

    public void stop() {
        running = false;
        looping = false;
        Choreographer.getInstance().removeFrameCallback(this);
    }

    /** Kept for callers; the backdrop doesn't drift, so there is nothing to wake. */
    public void poke() { }

    private void loop() {
        if (running && !looping) {
            looping = true;
            Choreographer.getInstance().postFrameCallback(this);
        }
    }

    @Override
    public void doFrame(long now) {
        if (!running || !changing) { looping = false; return; }
        if (now - lastFrameNanos >= 33_000_000L) {
            lastFrameNanos = now;
            float diff = 0;
            for (int j = 0; j < 3; j++) {
                float d = tgt[j] - cur[j];
                cur[j] += d * 0.14f;
                diff = Math.max(diff, Math.abs(d));
            }
            if (diff < 0.6f) { System.arraycopy(tgt, 0, cur, 0, 3); changing = false; }
            shaderDirty = true;
            invalidate();
        }
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        canvas.drawColor(BASE);
        if (!hasColors) return;
        if (shaderDirty) {
            int rgb = ((int) cur[0] << 16) | ((int) cur[1] << 8) | (int) cur[2];
            for (int i = 0; i < STOPS; i++) colors[i] = (Math.round(ALPHA * falloff[i] * 255) << 24) | rgb;
            RadialGradient g = new RadialGradient(0, 0, 1f, colors, positions, Shader.TileMode.CLAMP);
            matrix.setScale(RX * w, RY * h);
            matrix.postTranslate(CX * w, CY * h);
            g.setLocalMatrix(matrix);
            glowPaint.setShader(g);
            shaderDirty = false;
        }
        canvas.drawRect(0, 0, w, h, glowPaint);
    }
}
