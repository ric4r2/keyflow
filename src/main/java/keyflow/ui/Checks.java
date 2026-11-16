package keyflow.ui;

import keyflow.course.Exercise;
import keyflow.vim.VimEngine;

/** Shared completion checks for Vim drills. */
public final class Checks {

    private Checks() {
    }

    /** Compare texts ignoring trailing whitespace at line ends. */
    public static boolean sameText(String a, String b) {
        return strip(a).equals(strip(b));
    }

    private static String strip(String s) {
        return s.replace("\r\n", "\n").replaceAll("[ \t]+(?=\n|$)", "");
    }

    public static boolean vimSolved(Exercise e, VimEngine eng) {
        if (!eng.isIdle()) return false;
        if (!sameText(eng.getText(), e.target)) return false;
        return !e.hasGoal() || (eng.getRow() == e.goalRow && eng.getCol() == e.goalCol);
    }
}
