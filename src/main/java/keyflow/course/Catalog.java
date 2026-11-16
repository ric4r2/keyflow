package keyflow.course;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import keyflow.vim.VimEngine;

/**
 * Loads every subject and path from {@code /curriculum/index.txt}. Adding a language, a terminal
 * or a career path only requires new text files, no code.
 *
 * <p>Card file format:</p>
 * <pre>
 * @unit id | Title            desc: / book:
 * @lesson id | Title          intro: / keys: / book:
 * @card kind | Title          kind = read, quiz, type, fix, write, vim
 *   text: / task: / hint: / explain: / option: / option*: (correct) / solution: / par:
 *   cursor: r c / goal: r c / compile: class|body|stmt|none / since: N / check: regex
 *   code: | start: | target: | answer:   followed by verbatim lines and a line "@end"
 * </pre>
 */
public final class Catalog {

    public final List<Subject> subjects = new ArrayList<>();
    public final List<LearningPath> paths = new ArrayList<>();
    private final Map<String, Exercise> cards = new LinkedHashMap<>();
    private final Map<String, Lesson> lessons = new LinkedHashMap<>();

    public static Catalog load() {
        Catalog c = new Catalog();
        c.parseIndex(resource("/curriculum/index.txt"));
        for (Subject s : c.subjects) {
            for (String f : s.files) {
                for (Unit u : parseCards(resource("/curriculum/" + f), s)) s.units.add(u);
            }
            for (Unit u : s.units) {
                for (Lesson l : u.lessons) {
                    c.lessons.put(l.id, l);
                    for (Exercise e : l.exercises) {
                        if (c.cards.put(e.id, e) != null) throw new IllegalStateException("duplicate card id " + e.id);
                    }
                }
            }
        }
        return c;
    }

    public Subject subject(String id) {
        for (Subject s : subjects) if (s.id.equals(id)) return s;
        return null;
    }

    public Exercise card(String id) {
        return cards.get(id);
    }

    public Lesson lesson(String id) {
        return lessons.get(id);
    }

    public List<Exercise> allCards() {
        return new ArrayList<>(cards.values());
    }

    public List<Lesson> allLessons() {
        return new ArrayList<>(lessons.values());
    }

    // ------------------------------------------------------------------ index

    private void parseIndex(String text) {
        Subject subject = null;
        LearningPath path = null;
        String[] lines = text.replace("\r\n", "\n").split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (line.startsWith("@subject")) {
                String[] p = line.substring(8).split("\\|");
                subject = new Subject();
                subject.id = p[0].trim();
                subject.name = p.length > 1 ? p[1].trim() : subject.id;
                subject.category = p.length > 2 ? p[2].trim() : "language";
                subjects.add(subject);
                path = null;
                continue;
            }
            if (line.startsWith("@path")) {
                String[] p = line.substring(5).split("\\|");
                path = new LearningPath();
                path.id = p[0].trim();
                path.name = p.length > 1 ? p[1].trim() : path.id;
                paths.add(path);
                subject = null;
                continue;
            }
            int colon = line.indexOf(':');
            if (colon <= 0) throw err("index", i, "cannot parse: " + line);
            String key = line.substring(0, colon).trim(), val = line.substring(colon + 1).trim();
            if (subject != null) {
                switch (key) {
                    case "desc": subject.description = append(subject.description, val); break;
                    case "book": subject.book = val; break;
                    case "status": subject.status = val; break;
                    case "files": for (String f : val.split(",")) if (!f.trim().isEmpty()) subject.files.add(f.trim()); break;
                    default: throw err("index", i, "unknown subject field " + key);
                }
            } else if (path != null) {
                switch (key) {
                    case "desc": path.description = append(path.description, val); break;
                    case "steps": for (String s : val.split(",")) if (!s.trim().isEmpty()) path.steps.add(s.trim()); break;
                    default: throw err("index", i, "unknown path field " + key);
                }
            } else {
                throw err("index", i, "field outside @subject/@path");
            }
        }
    }

    // ------------------------------------------------------------------ cards

    public static List<Unit> parseCards(String text, Subject subject) {
        String src = subject == null ? "cards" : subject.id;
        List<Unit> units = new ArrayList<>();
        Unit unit = null;
        Lesson lesson = null;
        Exercise ex = null;
        String[] lines = text.replace("\r\n", "\n").split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String raw = lines[i];
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (line.startsWith("@unit")) {
                String[] p = header(line.substring(5));
                unit = new Unit();
                unit.id = p[0];
                unit.title = p[1];
                unit.subject = subject;
                units.add(unit);
                lesson = null;
                ex = null;
                continue;
            }
            if (line.startsWith("@lesson")) {
                if (unit == null) throw err(src, i, "@lesson outside @unit");
                String[] p = header(line.substring(7));
                lesson = new Lesson();
                lesson.id = p[0];
                lesson.title = p[1];
                lesson.unit = unit;
                unit.lessons.add(lesson);
                ex = null;
                continue;
            }
            if (line.startsWith("@card")) {
                if (lesson == null) throw err(src, i, "@card outside @lesson");
                String[] p = header(line.substring(5));
                ex = new Exercise();
                try {
                    ex.kind = Exercise.Kind.valueOf(p[0].toUpperCase());
                } catch (IllegalArgumentException e) {
                    throw err(src, i, "unknown card kind " + p[0]);
                }
                ex.title = p[1].equals(p[0]) ? "" : p[1];
                ex.lesson = lesson;
                ex.id = lesson.id + "." + (lesson.exercises.size() + 1);
                lesson.exercises.add(ex);
                continue;
            }
            if (line.equals("start:") || line.equals("target:") || line.equals("code:") || line.equals("answer:")) {
                if (ex == null) throw err(src, i, "block outside @card");
                StringBuilder sb = new StringBuilder();
                int j = i + 1;
                boolean first = true;
                while (j < lines.length && !lines[j].equals("@end")) {
                    if (!first) sb.append('\n');
                    sb.append(lines[j]);
                    first = false;
                    j++;
                }
                if (j >= lines.length) throw err(src, i, "unterminated block (missing @end)");
                String block = sb.toString();
                switch (line) {
                    case "start:": ex.start = block; break;
                    case "target:": ex.target = block; break;
                    case "code:": ex.code = block; break;
                    default: ex.answers.add(block);
                }
                i = j;
                continue;
            }
            int colon = line.indexOf(':');
            if (colon <= 0) throw err(src, i, "cannot parse: " + line);
            String key = line.substring(0, colon).trim();
            String val = line.substring(colon + 1).trim();
            if (ex != null) {
                switch (key) {
                    case "text": ex.text = append(ex.text, val); break;
                    case "task": ex.task = append(ex.task, val); break;
                    case "hint": ex.hint = append(ex.hint, val); break;
                    case "explain": ex.explain = append(ex.explain, val); break;
                    case "option": ex.options.add(val); break;
                    case "option*": ex.correct = ex.options.size(); ex.options.add(val); break;
                    case "solution": ex.solution = raw.substring(raw.indexOf(':') + 1).trim(); break;
                    case "par": ex.par = Integer.parseInt(val); break;
                    case "cursor": { int[] rc = pair(val, src, i); ex.cursorRow = rc[0]; ex.cursorCol = rc[1]; break; }
                    case "goal": { int[] rc = pair(val, src, i); ex.goalRow = rc[0]; ex.goalCol = rc[1]; break; }
                    case "compile": ex.compile = val; break;
                    case "since": ex.since = Integer.parseInt(val); break;
                    case "check": ex.checks.add(val); break;
                    default: throw err(src, i, "unknown card field " + key);
                }
            } else if (lesson != null) {
                switch (key) {
                    case "intro": lesson.intro = append(lesson.intro, val); break;
                    case "keys": lesson.keys = val; break;
                    case "book": lesson.book = val; break;
                    default: throw err(src, i, "unknown lesson field " + key);
                }
            } else if (unit != null) {
                switch (key) {
                    case "desc": unit.description = append(unit.description, val); break;
                    case "book": unit.book = val; break;
                    default: throw err(src, i, "unknown unit field " + key);
                }
            } else {
                throw err(src, i, "field outside unit");
            }
        }
        for (Unit u : units) {
            for (Exercise e : u.allExercises()) {
                if (e.kind == Exercise.Kind.VIM && e.target == null) e.target = e.start;
                if (e.kind == Exercise.Kind.TYPE && e.answers.isEmpty()) e.answers.add(e.code);
                if (e.par <= 0 && !e.solution.isEmpty()) e.par = VimEngine.tokenize(e.solution).size();
            }
        }
        return units;
    }

    public static String resource(String path) {
        try (InputStream in = Catalog.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("Missing resource " + path);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String append(String a, String b) {
        return a.isEmpty() ? b : a + "\n" + b;
    }

    private static String[] header(String s) {
        int bar = s.indexOf('|');
        if (bar < 0) return new String[]{s.trim(), s.trim()};
        return new String[]{s.substring(0, bar).trim(), s.substring(bar + 1).trim()};
    }

    private static int[] pair(String v, String src, int line) {
        String[] p = v.trim().split("\\s+");
        if (p.length != 2) throw err(src, line, "expected 'row col'");
        return new int[]{Integer.parseInt(p[0]), Integer.parseInt(p[1])};
    }

    private static IllegalStateException err(String src, int line, String msg) {
        return new IllegalStateException(src + " line " + (line + 1) + ": " + msg);
    }
}
