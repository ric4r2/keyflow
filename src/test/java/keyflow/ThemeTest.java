package keyflow;

import java.awt.Color;

import keyflow.ui.Theme;

/** Checks the colour design rules: readable contrast in both themes. */
final class ThemeTest {

    private ThemeTest() {
    }

    static void run(TestRunner t) {
        for (Theme th : new Theme[]{Theme.LIGHT, Theme.DARK}) {
            ratio(t, th, "text on bg", th.text, th.bg, 7.0);
            ratio(t, th, "text on surface", th.text, th.surface, 7.0);
            ratio(t, th, "code text on code bg", th.codeText, th.codeBg, 7.0);
            ratio(t, th, "muted on bg", th.muted, th.bg, 4.5);
            ratio(t, th, "muted on surface", th.muted, th.surface, 4.5);
            ratio(t, th, "button text on accent", th.onAccent, th.accent, 4.5);
            ratio(t, th, "accent on surface", th.accent, th.surface, 4.5);
            ratio(t, th, "success on successSoft", th.success, th.successSoft, 4.5);
            ratio(t, th, "error on errorSoft", th.error, th.errorSoft, 4.5);
            ratio(t, th, "keyword on code bg", th.kw, th.codeBg, 4.5);
            ratio(t, th, "string on code bg", th.str, th.codeBg, 4.5);
            ratio(t, th, "number on code bg", th.num, th.codeBg, 4.5);
            ratio(t, th, "type on code bg", th.type, th.codeBg, 4.5);
            ratio(t, th, "annotation on code bg", th.annotation, th.codeBg, 4.5);
            ratio(t, th, "comment on code bg", th.comment, th.codeBg, 3.5);
            t.check(th.name + ": no pure black or white", () ->
                    !th.bg.equals(Color.WHITE) && !th.bg.equals(Color.BLACK) && !th.text.equals(Color.BLACK) && !th.text.equals(Color.WHITE));
        }
    }

    private static void ratio(TestRunner t, Theme th, String what, Color fg, Color bg, double min) {
        double r = Theme.contrast(fg, bg);
        t.check(String.format("%s: %s contrast %.2f >= %.1f", th.name, what, r, min), () -> r >= min);
    }
}
