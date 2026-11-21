import java.awt.*;
import java.io.InputStream;

/**
 * Drawing helpers that give the UI a Five-Nights-at-Freddy's look:
 * pixel-ish text with a drop shadow, thin white outlined boxes, the translucent flip bar with
 * chevrons, green/yellow/red usage bars, a battery-style gauge and a white camera frame.
 *
 * Fonts: drop a .ttf at Resources/font.ttf and it is used everywhere automatically.
 * (Pixel fonts such as "Press Start 2P" or "VCR OSD Mono" look right; check each font's licence.)
 */
public final class UI {
    private static Font base = new Font(Font.MONOSPACED, Font.BOLD, 12);

    private UI() {
    }

    // ---------- Fonts ----------
    /** Load a custom font from the classpath. Silently keeps the default if it is missing. */
    public static void loadFont(String resourceName) {
        try (InputStream in = ResourceLoader.load(resourceName)) {
            if (in != null) {
                base = Font.createFont(Font.TRUETYPE_FONT, in);
                System.out.println("Loaded custom font " + resourceName);
            }
        } catch (Exception e) {
            System.out.println("Could not load font " + resourceName + ": " + e);
        }
    }

    public static Font font(float size) {
        return base.deriveFont(size);
    }

    // ---------- Text ----------
    public static void text(Graphics2D g, String s, int x, int y, float size, Color c) {
        g.setFont(font(size));
        g.setColor(new Color(0, 0, 0, 170));
        g.drawString(s, x + 2, y + 2); // drop shadow
        g.setColor(c);
        g.drawString(s, x, y);
    }

    public static int textWidth(Graphics2D g, String s, float size) {
        return g.getFontMetrics(font(size)).stringWidth(s);
    }

    public static void textRight(Graphics2D g, String s, int rightX, int y, float size, Color c) {
        text(g, s, rightX - textWidth(g, s, size), y, size, c);
    }

    public static void textCenter(Graphics2D g, String s, int centerX, int y, float size, Color c) {
        text(g, s, centerX - textWidth(g, s, size) / 2, y, size, c);
    }

    // ---------- Boxes / buttons ----------
    /** OVERLOADED: enabled button. */
    public static void box(Graphics2D g, Rectangle r, String label) {
        box(g, r, label, true);
    }

    /** OVERLOADED: button that can be greyed out (like the MUTE CALL button). */
    public static void box(Graphics2D g, Rectangle r, String label, boolean enabled) {
        g.setColor(new Color(0, 0, 0, enabled ? 175 : 120));
        g.fillRect(r.x, r.y, r.width, r.height);
        Color c = enabled ? Color.WHITE : new Color(255, 255, 255, 90);
        g.setColor(c);
        g.setStroke(new BasicStroke(2));
        g.drawRect(r.x, r.y, r.width, r.height);
        g.setStroke(new BasicStroke(1));
        float size = 18f;
        g.setFont(font(size));
        int ty = r.y + (r.height + g.getFontMetrics().getAscent() - g.getFontMetrics().getDescent()) / 2;
        textCenter(g, label, r.x + r.width / 2, ty, size, c);
    }

    /** The wide translucent bar with chevrons that you hover to flip the monitor. */
    public static void flipBar(Graphics2D g, Rectangle r, boolean hot) {
        int a = hot ? 235 : 150;
        g.setColor(new Color(0, 0, 0, 110));
        g.fillRoundRect(r.x, r.y, r.width, r.height, 10, 10);
        g.setStroke(new BasicStroke(2));
        g.setColor(new Color(255, 255, 255, a));
        g.drawRoundRect(r.x, r.y, r.width, r.height, 10, 10);
        int cx = r.x + r.width / 2, cy = r.y + r.height / 2, half = r.width / 7;
        for (int k = 0; k < 2; k++) { // two stacked, flat chevrons
            int yy = cy - 10 + k * 9;
            g.drawLine(cx - half, yy, cx, yy + 8);
            g.drawLine(cx, yy + 8, cx + half, yy);
        }
        g.setStroke(new BasicStroke(1));
    }

    // ---------- Gauges ----------
    /** Green, green, yellow, then red - one bar per active device. */
    public static void usageBars(Graphics2D g, int x, int y, int count) {
        for (int i = 0; i < Math.min(count, 6); i++) {
            Color c = i < 2 ? new Color(30, 210, 30) : i == 2 ? new Color(235, 205, 20) : new Color(225, 35, 35);
            g.setColor(c);
            g.fillRect(x + i * 18, y, 14, 26);
        }
    }

    /** FNAF 2 style battery icon with 4 segments. */
    public static void battery(Graphics2D g, int x, int y, int w, int h, double fraction, Color fill) {
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(2));
        g.drawRect(x, y, w, h);
        g.fillRect(x + w, y + h / 4, 6, h / 2); // nub
        g.setStroke(new BasicStroke(1));
        int segs = 4, sw = (w - 8) / segs;
        int lit = (int) Math.ceil(Math.max(0, Math.min(1, fraction)) * segs - 1e-9);
        for (int s = 0; s < lit; s++) {
            g.setColor(fill);
            g.fillRect(x + 4 + s * sw, y + 4, sw - 3, h - 8);
        }
    }

    // ---------- Screen effects ----------
    public static void frameBorder(Graphics2D g, int w, int h, int inset) {
        g.setColor(new Color(255, 255, 255, 230));
        g.setStroke(new BasicStroke(3));
        g.drawRect(inset, inset, w - 2 * inset, h - 2 * inset);
        g.setStroke(new BasicStroke(1));
    }

    public static void vignette(Graphics2D g, int w, int h, int edgeAlpha) {
        float radius = (float) Math.hypot(w, h) / 2f;
        g.setPaint(new RadialGradientPaint(new Point(w / 2, h / 2), radius, new float[] { 0.5f, 1f },
                new Color[] { new Color(0, 0, 0, 0), new Color(0, 0, 0, edgeAlpha) }));
        g.fillRect(0, 0, w, h);
    }
}