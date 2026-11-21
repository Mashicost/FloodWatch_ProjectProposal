import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * Animated security-camera static: film grain, scanlines, a rolling bar and a vignette.
 *
 * By default it generates a few low-res noise frames at startup (no assets needed).
 * If you have real footage, export it as numbered frames and call setFrames() with them.
 *
 * ENCAPSULATION: frames and animation state are private; Main only calls update()/draw()/pulse().
 * OVERLOADING:   draw(g, w, h) uses a light default alpha, draw(g, w, h, alpha) lets you choose.
 */
public class StaticOverlay {
    private static final int GEN_W = 384, GEN_H = 216, GEN_FRAMES = 12;
    private static final double FPS = 18; // how fast the noise changes
    private static final float LIGHT_ALPHA = 0.16f; // overlay on a working camera
    private static final double BAR_SPEED = 140; // px/sec the rolling bar moves

    private BufferedImage[] frames;
    private final Random rng = new Random();
    private int current = 0;
    private double acc = 0;
    private double barY = 0;
    private double pulseLeft = 0;

    public StaticOverlay() {
        frames = generateFrames();
    }

    /** Replace the generated noise with your own frames (e.g. exported from a video). */
    public void setFrames(BufferedImage[] custom) {
        if (custom != null && custom.length > 0) {
            frames = custom;
            current = 0;
        }
    }

    public int getFrameCount() {
        return frames.length;
    }

    /** Brief burst of heavy static, e.g. when switching cameras. */
    public void pulse(double seconds) {
        pulseLeft = Math.max(pulseLeft, seconds);
    }

    public void update(double dt) {
        acc += dt;
        double step = 1.0 / FPS;
        if (acc >= step) {
            acc = 0;
            int next = rng.nextInt(frames.length); // random order avoids a visible loop
            if (frames.length > 1 && next == current) next = (next + 1) % frames.length;
            current = next;
        }
        barY += BAR_SPEED * dt;
        if (pulseLeft > 0) pulseLeft -= dt;
    }

    /** OVERLOADED: light overlay for a working camera. */
    public void draw(Graphics2D g, int w, int h) {
        draw(g, w, h, LIGHT_ALPHA);
    }

    /** OVERLOADED: custom strength. 1.0 = fully covers whatever is underneath (dead camera). */
    public void draw(Graphics2D g, int w, int h, float alpha) {
        float a = pulseLeft > 0 ? Math.max(alpha, 0.65f) : alpha;
        Composite old = g.getComposite();

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(1f, a)));
        g.drawImage(frames[current], 0, 0, w, h, null);
        g.setComposite(old);

        drawScanlines(g, w, h, a >= 0.99f ? 70 : 45);
        drawBar(g, w, h, a >= 0.99f ? 40 : 22);
        drawVignette(g, w, h);
    }

    // ---------- Effects ----------
    private void drawScanlines(Graphics2D g, int w, int h, int alpha) {
        g.setColor(new Color(0, 0, 0, alpha));
        for (int y = 0; y < h; y += 4) g.fillRect(0, y, w, 1);
    }

    private void drawBar(Graphics2D g, int w, int h, int alpha) {
        int barH = 70;
        double y = (barY % (h + barH)) - barH;
        g.setPaint(new GradientPaint(0, (float) y, new Color(255, 255, 255, 0),
                0, (float) (y + barH / 2), new Color(255, 255, 255, alpha)));
        g.fillRect(0, (int) y, w, barH / 2);
        g.setPaint(new GradientPaint(0, (float) (y + barH / 2), new Color(255, 255, 255, alpha),
                0, (float) (y + barH), new Color(255, 255, 255, 0)));
        g.fillRect(0, (int) (y + barH / 2), w, barH / 2);
    }

    private void drawVignette(Graphics2D g, int w, int h) {
        float radius = (float) Math.hypot(w, h) / 2f;
        RadialGradientPaint p = new RadialGradientPaint(new Point(w / 2, h / 2), radius,
                new float[] { 0.55f, 1f }, new Color[] { new Color(0, 0, 0, 0), new Color(0, 0, 0, 150) });
        g.setPaint(p);
        g.fillRect(0, 0, w, h);
    }

    // ---------- Procedural noise ----------
    private BufferedImage[] generateFrames() {
        BufferedImage[] out = new BufferedImage[GEN_FRAMES];
        int[] px = new int[GEN_W * GEN_H];
        for (int f = 0; f < GEN_FRAMES; f++) {
            for (int i = 0; i < px.length; i++) {
                int v = (int) (128 + rng.nextGaussian() * 55);
                if (rng.nextInt(400) == 0) v = 255; // bright specks
                v = Math.max(0, Math.min(255, v));
                px[i] = (v << 16) | (v << 8) | v;
            }
            // horizontal tearing: a few rows are brighter and shifted
            for (int t = 0; t < 4; t++) {
                int row = rng.nextInt(GEN_H);
                int shift = rng.nextInt(40) - 20;
                for (int x = 0; x < GEN_W; x++) {
                    int src = row * GEN_W + Math.floorMod(x + shift, GEN_W);
                    int v = Math.min(255, (px[src] & 0xFF) + 50);
                    px[row * GEN_W + x] = (v << 16) | (v << 8) | v;
                }
            }
            BufferedImage img = new BufferedImage(GEN_W, GEN_H, BufferedImage.TYPE_INT_RGB);
            img.setRGB(0, 0, GEN_W, GEN_H, px, 0, GEN_W);
            out[f] = img;
        }
        return out;
    }
}