package keyflow.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * The Keyflow icon, drawn in code so it is sharp at every size and identical on every OS:
 * a keycap with a "k" and a green Vim block cursor, on the app's calm blue. Small sizes drop the
 * keycap and keep only the letter and cursor, so the icon stays readable at 16 px.
 */
public final class AppIcon {

    private static final Color BLUE_TOP = new Color(0x3A78A6);
    private static final Color BLUE_BOTTOM = new Color(0x245276);
    private static final Color CAP_FACE = new Color(0xFBFAF7);
    private static final Color CAP_SIDE = new Color(0xD8D1C3);
    private static final Color INK = new Color(0x23557A);
    private static final Color GREEN = new Color(0x2D7650);
    private static final Color GREEN_LIGHT = new Color(0x5FBF8A);

    private static final int[] SIZES = {16, 20, 24, 32, 40, 48, 64, 128, 256, 512};

    private AppIcon() {
    }

    /** All sizes, for {@code JFrame.setIconImages}: the OS picks the best one. */
    public static List<Image> images() {
        List<Image> out = new ArrayList<>();
        for (int s : SIZES) out.add(image(s));
        return out;
    }

    public static BufferedImage image(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        paint(g, size);
        g.dispose();
        return img;
    }

    public static void paint(Graphics2D g, int size) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        double s = size;

        // background: a soft squircle in the app's accent blue
        double m = s * 0.03;
        g.setPaint(new GradientPaint(0, 0, BLUE_TOP, 0, (float) s, BLUE_BOTTOM));
        g.fill(new RoundRectangle2D.Double(m, m, s - 2 * m, s - 2 * m, s * 0.44, s * 0.44));

        if (size < 32) {
            // tiny: just the letter and the cursor, as bold as possible
            letter(g, s, 0.22, 0.24, 0.56, 0.135, CAP_FACE);
            cursor(g, s, 0.62, 0.43, 0.15, 0.37, GREEN_LIGHT);
            return;
        }

        // keycap: a darker base with the lighter face on top, like a real key
        double cx = s * 0.17, cy = s * 0.16, cw = s * 0.66, ch = s * 0.66, arc = s * 0.17;
        g.setColor(new Color(0, 0, 0, 40));
        g.fill(new RoundRectangle2D.Double(cx, cy + s * 0.05, cw, ch, arc, arc));
        g.setColor(CAP_SIDE);
        g.fill(new RoundRectangle2D.Double(cx, cy + s * 0.025, cw, ch, arc, arc));
        g.setPaint(new GradientPaint(0, (float) cy, CAP_FACE, 0, (float) (cy + ch), new Color(0xF1EEE7)));
        g.fill(new RoundRectangle2D.Double(cx, cy, cw, ch - s * 0.03, arc, arc));

        letter(g, s, 0.29, 0.27, 0.42, 0.085, INK);
        cursor(g, s, 0.585, 0.45, 0.095, 0.24, GREEN);
    }

    /** A geometric lowercase k: stem, arm and leg with round ends. */
    private static void letter(Graphics2D g, double s, double x, double y, double h, double stroke, Color c) {
        g.setColor(c);
        g.setStroke(new BasicStroke((float) (s * stroke), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        double x0 = s * x, top = s * y, bottom = s * (y + h);
        double mid = s * (y + h * 0.62);
        double w = s * h * 0.52;
        g.draw(new Line2D.Double(x0, top, x0, bottom));
        g.draw(new Line2D.Double(x0 + w, s * (y + h * 0.3), x0 + s * stroke * 0.25, mid));
        g.draw(new Line2D.Double(x0 + w * 0.42, s * (y + h * 0.5), x0 + w, bottom));
    }

    /** The Vim block cursor: "you are here, keep going". */
    private static void cursor(Graphics2D g, double s, double x, double y, double w, double h, Color c) {
        g.setColor(c);
        g.fill(new RoundRectangle2D.Double(s * x, s * y, s * w, s * h, s * w * 0.35, s * w * 0.35));
    }
}
