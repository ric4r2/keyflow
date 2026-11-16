package keyflow.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Colour and type for the whole app.
 *
 * <p>Design rules (see README "Design"): body text reaches at least 7:1 contrast (WCAG AAA), secondary
 * text at least 4.5:1, no pure black or white (reduces glare and halation), one muted accent colour for
 * the primary action, and low-saturation syntax colours so code is readable without shouting.</p>
 */
public final class Theme {

    public final String name;
    public final boolean dark;
    public final Color bg, surface, surface2, border, text, muted, accent, onAccent, accentSoft;
    public final Color success, successSoft, error, errorSoft, warn;
    public final Color codeBg, codeText, gutter, cursor, selection, search, goal;
    public final Color kw, str, comment, num, annotation, type;

    private Theme(String name, boolean dark, int... c) {
        this.name = name;
        this.dark = dark;
        int i = 0;
        bg = new Color(c[i++]); surface = new Color(c[i++]); surface2 = new Color(c[i++]); border = new Color(c[i++]);
        text = new Color(c[i++]); muted = new Color(c[i++]); accent = new Color(c[i++]); onAccent = new Color(c[i++]);
        accentSoft = new Color(c[i++]);
        success = new Color(c[i++]); successSoft = new Color(c[i++]); error = new Color(c[i++]); errorSoft = new Color(c[i++]);
        warn = new Color(c[i++]);
        codeBg = new Color(c[i++]); codeText = new Color(c[i++]); gutter = new Color(c[i++]); cursor = new Color(c[i++]);
        selection = new Color(c[i++]); search = new Color(c[i++]); goal = new Color(c[i++]);
        kw = new Color(c[i++]); str = new Color(c[i++]); comment = new Color(c[i++]); num = new Color(c[i++]);
        annotation = new Color(c[i++]); type = new Color(c[i]);
    }

    /** Warm paper: best for reading long explanations. */
    public static final Theme LIGHT = new Theme("light", false,
            0xF7F5F0, 0xFFFEFB, 0xEFECE5, 0xE2DED5,     // bg, surface, surface2, border
            0x23262B, 0x5B626C, 0x2F6690, 0xFBFAF7,     // text, muted, accent, onAccent
            0xE3ECF4,                                   // accentSoft
            0x2D7650, 0xE4F0E8, 0xA6423D, 0xF6E5E3,     // success, successSoft, error, errorSoft
            0x7D6219,                                   // warn
            0xF2EFE8, 0x23262B, 0x8C9199, 0x2F6690,     // codeBg, codeText, gutter, cursor
            0xD5E2EE, 0xF3E3B5, 0x2D7650,               // selection, search, goal
            0x75399A, 0x2D7650, 0x737983, 0x9A4E25,     // keyword, string, comment, number
            0x7D6219, 0x2F6690);                        // annotation, type

    /** Soft night: low glare, good separation of syntax colours. */
    public static final Theme DARK = new Theme("dark", true,
            0x191B1F, 0x202328, 0x272B31, 0x30353C,
            0xDCDFE4, 0x9BA2AC, 0x8AB4D8, 0x14202B,
            0x26313C,
            0x8CC79F, 0x1F2D25, 0xE39A94, 0x33221F,
            0xD8B86A,
            0x1D2024, 0xDCDFE4, 0x6B727C, 0x8AB4D8,
            0x2E3C4A, 0x4A4024, 0x8CC79F,
            0xC3A6E0, 0xA8D1A1, 0x7D8590, 0xE0B58A,
            0xD8B86A, 0x8AB4D8);

    private static Theme current = LIGHT;
    private static int baseSize = 17;
    private static String uiFamily, monoFamily;

    public static Theme get() {
        return current;
    }

    public static void set(String name) {
        current = "dark".equals(name) ? DARK : LIGHT;
    }

    public static void setBaseSize(int s) {
        baseSize = s;
    }

    public static int base() {
        return baseSize;
    }

    // ------------------------------------------------------------------ fonts

    private static String pick(String... families) {
        Set<String> avail;
        try {
            avail = new HashSet<>(Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        } catch (Throwable t) {
            avail = new HashSet<>();
        }
        for (String f : families) if (avail.contains(f)) return f;
        return null;
    }

    private static String uiFamily() {
        if (uiFamily == null) {
            String f = pick("Inter", "Segoe UI", "SF Pro Text", ".AppleSystemUIFont", "Helvetica Neue", "Ubuntu", "Cantarell", "Noto Sans", "DejaVu Sans");
            uiFamily = f == null ? Font.SANS_SERIF : f;
        }
        return uiFamily;
    }

    private static String monoFamily() {
        if (monoFamily == null) {
            String f = pick("JetBrains Mono", "Cascadia Mono", "Cascadia Code", "SF Mono", "Menlo", "Consolas", "Ubuntu Mono", "DejaVu Sans Mono", "Liberation Mono");
            monoFamily = f == null ? Font.MONOSPACED : f;
        }
        return monoFamily;
    }

    /** UI font, size relative to the base reading size. */
    public static Font ui(float rel, int style) {
        return new Font(uiFamily(), style, Math.round(baseSize * rel));
    }

    public static Font mono(float rel) {
        return new Font(monoFamily(), Font.PLAIN, Math.round(baseSize * rel));
    }

    // ------------------------------------------------------------------ colour maths

    /** WCAG relative luminance. */
    public static double luminance(Color c) {
        double[] v = {c.getRed() / 255.0, c.getGreen() / 255.0, c.getBlue() / 255.0};
        for (int i = 0; i < 3; i++) v[i] = v[i] <= 0.03928 ? v[i] / 12.92 : Math.pow((v[i] + 0.055) / 1.055, 2.4);
        return 0.2126 * v[0] + 0.7152 * v[1] + 0.0722 * v[2];
    }

    /** WCAG contrast ratio between two colours (1..21). */
    public static double contrast(Color a, Color b) {
        double la = luminance(a), lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    public static Color alpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
    }
}
