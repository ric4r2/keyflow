package keyflow.course;

import java.util.ArrayList;
import java.util.List;

/**
 * One card of a lesson. Cards are deliberately small (one idea each):
 * READ explains, QUIZ checks recall, TYPE/FIX/WRITE practise code, VIM drills editing.
 */
public final class Exercise {

    public enum Kind {
        READ, QUIZ, TYPE, FIX, WRITE, VIM;

        public boolean isPractice() {
            return this != READ;
        }

        public boolean usesEditor() {
            return this == TYPE || this == FIX || this == WRITE || this == VIM;
        }
    }

    public String id;
    public Kind kind;
    public String title = "";
    /** Short explanation (light markdown: `code`, **bold**, "- " bullets). */
    public String text = "";
    public String task = "";
    public String hint = "";
    public String explain = "";
    /** Code shown read-only (READ and QUIZ cards) or the code to reproduce (TYPE). */
    public String code = "";
    /** Initial editor content (VIM / FIX / WRITE). */
    public String start = "";
    /** Expected buffer for VIM drills. */
    public String target;
    /** Accepted answers for TYPE / FIX / WRITE. */
    public final List<String> answers = new ArrayList<>();
    /** Regexes that must match (WRITE / FIX validated by compiling). */
    public final List<String> checks = new ArrayList<>();
    public final List<String> options = new ArrayList<>();
    public int correct = -1;
    /** Reference key sequence in Vim notation. */
    public String solution = "";
    public int par;
    public int cursorRow, cursorCol;
    public int goalRow = -1, goalCol = -1;
    /** How to compile code: class, body, stmt or none. */
    public String compile = "none";
    public int since = 11;
    public Lesson lesson;

    public boolean hasGoal() {
        return goalRow >= 0;
    }

    public String kindLabel() {
        switch (kind) {
            case READ: return "Learn";
            case QUIZ: return "Recall";
            case TYPE: return "Type it";
            case FIX: return "Fix it";
            case WRITE: return "Write it";
            default: return "Vim drill";
        }
    }

    @Override
    public String toString() {
        return id + " " + title;
    }
}
