package keyflow;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import keyflow.course.Catalog;
import keyflow.course.Exercise;
import keyflow.course.Lesson;
import keyflow.vim.VimEngine;

/**
 * The Vim track must be progressive: every key a drill's reference solution uses has to be taught
 * in the {@code keys:} line (shown on every drill) of that lesson or an earlier one. Text typed in insert
 * mode, counts, and arguments (the char after f, t, r, m, q, @, ", ' and `) are not commands.
 */
final class KeyOrderTest {

    private static final Set<String> TAKES_ARG = new HashSet<>(Arrays.asList("f", "t", "F", "T", "r", "m", "`", "'", "q", "@", "\""));
    private static final Set<String> IGNORE_WORDS = new HashSet<>(Arrays.asList("flag", "everything"));
    private static final Pattern EX_NAME = Pattern.compile("^[%.$,0-9]*(?:'[a-z<>],?'?[a-z<>]?)?([a-z]*)");

    private KeyOrderTest() {
    }

    static void run(TestRunner t) {
        Catalog c = Catalog.load();
        Set<String> known = new HashSet<>();
        int drills = 0;
        for (Lesson l : c.subject("vim").allLessons()) {
            learn(known, l.keys);
            for (Exercise e : l.exercises) {
                if (e.kind != Exercise.Kind.VIM) continue;
                drills++;
                Set<String> missing = new LinkedHashSet<>();
                for (String k : commands(e)) if (!known.contains(k)) missing.add(k);
                if (missing.isEmpty()) t.pass();
                else t.fail("key order: " + e.id + " (" + l.title + ") uses keys not taught yet: " + missing + "   solution: " + e.solution);
            }
        }
        System.out.println("Key order: " + drills + " Vim drills checked against keys taught so far");
    }

    /** Add the keys named in a keys line or an intro code span. */
    static void learn(Set<String> known, String text) {
        for (String item : text.trim().split("\\s+")) {
            if (item.isEmpty() || IGNORE_WORDS.contains(item)) continue;
            String s = item.replace("[count]", "").replace("[N]", "");
            s = s.replaceAll("Ctrl-(.)", "<C-$1>").replace("Esc", "<Esc>");
            if (s.startsWith(":")) {
                Matcher m = EX_NAME.matcher(s.substring(1));
                m.find();
                known.add(":");
                known.add(m.group(1).isEmpty() ? ":N" : ":" + m.group(1));
                continue;
            }
            try {
                known.addAll(VimEngine.tokenize(s));
            } catch (RuntimeException ignored) {
                // not key notation (prose in a code span)
            }
        }
    }

    /** The command keys a solution presses, found by replaying it in the engine. */
    static List<String> commands(Exercise e) {
        VimEngine eng = new VimEngine(e.start);
        eng.setCursor(e.cursorRow, e.cursorCol);
        List<String> out = new java.util.ArrayList<>();
        boolean argNext = false;
        StringBuilder line = null;
        for (String tok : VimEngine.tokenize(e.solution)) {
            VimEngine.Mode mode = eng.getMode();
            boolean inLine = eng.getCommandLine() != null;
            boolean pendingEmpty = eng.getPendingKeys().isEmpty();
            if (inLine) {
                if (tok.equals("<CR>")) {
                    String cmd = line == null ? "" : line.toString();
                    if (cmd.startsWith(":")) {
                        Matcher m = EX_NAME.matcher(cmd.substring(1));
                        m.find();
                        out.add(m.group(1).isEmpty() ? ":N" : ":" + m.group(1));
                    }
                    line = null;
                } else if (line != null) {
                    line.append(tok);
                }
            } else if (mode == VimEngine.Mode.INSERT || mode == VimEngine.Mode.REPLACE) {
                if (tok.startsWith("<") && !tok.equals("<BS>") && !tok.equals("<CR>") && !tok.equals("<Tab>")) out.add(tok);
            } else if (argNext) {
                argNext = false;
            } else if (tok.matches("[1-9]") || (tok.equals("0") && !pendingEmpty && eng.getPendingKeys().matches(".*[0-9]"))) {
                // a count
            } else {
                out.add(tok);
                if (tok.equals(":") || tok.equals("/") || tok.equals("?")) line = new StringBuilder(tok);
            }
            eng.key(tok);
            if (!inLine && mode != VimEngine.Mode.INSERT && mode != VimEngine.Mode.REPLACE && TAKES_ARG.contains(tok)) {
                String p = eng.getPendingKeys();
                argNext = !p.isEmpty() && p.endsWith(tok);
            }
        }
        return out;
    }
}
