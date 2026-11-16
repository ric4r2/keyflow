package keyflow.data;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;

import keyflow.course.Catalog;
import keyflow.course.Exercise;
import keyflow.course.Lesson;
import keyflow.course.Subject;

/**
 * Everything the learner has done, saved after every card in {@code ~/.keyflow/progress.properties}.
 * Spaced repetition uses Leitner boxes: a correct answer moves a card up a box (longer interval),
 * a wrong answer sends it back to box 1.
 */
public final class Progress {

    /** Days until the next review for boxes 0..6. */
    private static final int[] INTERVALS = {0, 1, 2, 4, 8, 16, 32};

    private final Path file;
    private final Properties p = new Properties();

    private Progress(Path file) {
        this.file = file;
    }

    public static Path dataDir() {
        String override = System.getProperty("keyflow.home");
        if (override != null) return Paths.get(override);
        return Paths.get(System.getProperty("user.home"), ".keyflow");
    }

    public static Progress load() {
        Progress pr = new Progress(dataDir().resolve("progress.properties"));
        try (InputStream in = Files.newInputStream(pr.file)) {
            pr.p.load(in);
        } catch (IOException ignored) {
            // first run
        }
        return pr;
    }

    /** In-memory progress that is never written (tests, previews). */
    public static Progress memory() {
        return new Progress(null);
    }

    public synchronized void save() {
        if (file == null) return;
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling("progress.tmp");
            try (OutputStream out = Files.newOutputStream(tmp)) {
                p.store(out, "Keyflow progress");
            }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            System.err.println("Could not save progress: " + e.getMessage());
        }
    }

    public void resetAll() {
        p.clear();
        save();
    }

    // ------------------------------------------------------------------ settings

    public boolean vimKeys() { return bool("set.vimKeys", true); }
    public void setVimKeys(boolean v) { put("set.vimKeys", v); }

    public String theme() { return p.getProperty("set.theme", "light"); }
    public void setTheme(String t) { p.setProperty("set.theme", t); save(); }

    public int fontSize() { return num("set.fontSize", 17); }
    public void setFontSize(int v) { put("set.fontSize", Math.max(13, Math.min(26, v))); }

    public int focusMinutes() { return num("set.focus", 15); }
    public void setFocusMinutes(int v) { put("set.focus", Math.max(5, Math.min(60, v))); }

    public int breakMinutes() { return num("set.break", 3); }
    public void setBreakMinutes(int v) { put("set.break", Math.max(1, Math.min(30, v))); }

    public int dailyGoal() { return num("set.goal", 40); }
    public void setDailyGoal(int v) { put("set.goal", Math.max(10, Math.min(500, v))); }

    // ------------------------------------------------------------------ cards

    public int box(String id) { return num("c." + id + ".box", 0); }
    public boolean seen(String id) { return p.containsKey("c." + id + ".seen"); }
    public long due(String id) { return lng("c." + id + ".due", 0); }
    public int stars(String id) { return num("c." + id + ".stars", 0); }
    public int wrongCount(String id) { return num("c." + id + ".wrong", 0); }

    public static long today() {
        return LocalDate.now().toEpochDay();
    }

    public void markRead(String id) {
        p.setProperty("c." + id + ".seen", "1");
        save();
    }

    /**
     * Record an attempt. Stars: 0 = failed or answer revealed, 1..3 = solved (3 = first try / at par).
     * Returns the XP earned.
     */
    public int record(Exercise e, int stars) {
        String k = "c." + e.id;
        p.setProperty(k + ".seen", "1");
        int box = box(e.id);
        if (stars <= 0) {
            box = 1;
            p.setProperty(k + ".wrong", String.valueOf(wrongCount(e.id) + 1));
        } else if (stars == 1) {
            box = Math.max(1, box);
        } else {
            box = Math.min(INTERVALS.length - 1, box + 1);
        }
        p.setProperty(k + ".box", String.valueOf(box));
        p.setProperty(k + ".due", String.valueOf(today() + INTERVALS[box]));
        if (stars > stars(e.id)) p.setProperty(k + ".stars", String.valueOf(stars));
        int xp = stars <= 0 ? 1 : 2 + 2 * stars;
        addXp(xp);
        save();
        return xp;
    }

    public boolean isDue(String id) {
        return seen(id) && box(id) > 0 && due(id) <= today();
    }

    /** Practice cards that are due for review, most overdue / weakest first. */
    public List<Exercise> dueCards(Catalog c, int max) {
        List<Exercise> out = new ArrayList<>();
        for (Exercise e : c.allCards()) if (e.kind.isPractice() && isDue(e.id)) out.add(e);
        out.sort(Comparator.comparingLong((Exercise e) -> due(e.id)).thenComparingInt(e -> box(e.id)));
        if (out.size() > max) out = new ArrayList<>(out.subList(0, max));
        Collections.shuffle(out);
        return out;
    }

    public int dueCount(Catalog c) {
        int n = 0;
        for (Exercise e : c.allCards()) if (e.kind.isPractice() && isDue(e.id)) n++;
        return n;
    }

    // ------------------------------------------------------------------ lessons & subjects

    public boolean lessonDone(Lesson l) {
        return p.containsKey("l." + l.id + ".done");
    }

    public void setLessonDone(Lesson l) {
        p.setProperty("l." + l.id + ".done", "1");
        save();
    }

    public int lessonsDone(Subject s) {
        int n = 0;
        for (Lesson l : s.allLessons()) if (lessonDone(l)) n++;
        return n;
    }

    public String lastLesson() { return p.getProperty("last.lesson"); }
    public void setLastLesson(Lesson l) { p.setProperty("last.lesson", l.id); save(); }

    public String lastSubject() { return p.getProperty("last.subject", "java"); }
    public void setLastSubject(String id) { p.setProperty("last.subject", id); save(); }

    /** The next lesson to study in a subject: first not-done lesson. */
    public Lesson nextLesson(Subject s) {
        for (Lesson l : s.allLessons()) if (!lessonDone(l)) return l;
        return null;
    }

    // ------------------------------------------------------------------ xp, streak, history

    public int xp() { return num("xp", 0); }

    public int xpOn(long day) { return num("day." + day, 0); }

    public int todayXp() { return xpOn(today()); }

    private void addXp(int n) {
        long t = today();
        p.setProperty("xp", String.valueOf(xp() + n));
        p.setProperty("day." + t, String.valueOf(xpOn(t) + n));
        long last = lng("streak.last", 0);
        if (last != t) {
            int streak = last == t - 1 ? num("streak", 0) + 1 : 1;
            p.setProperty("streak", String.valueOf(streak));
            p.setProperty("streak.last", String.valueOf(t));
        }
    }

    public int streak() {
        long last = lng("streak.last", 0);
        return last >= today() - 1 ? num("streak", 0) : 0;
    }

    public int level() {
        return (int) Math.floor((1 + Math.sqrt(1 + 8.0 * xp() / 100)) / 2);
    }

    public int cardsMastered() {
        int n = 0;
        for (String k : p.stringPropertyNames()) {
            if (k.endsWith(".box") && Integer.parseInt(p.getProperty(k)) >= 3) n++;
        }
        return n;
    }

    // ------------------------------------------------------------------ helpers

    private boolean bool(String k, boolean d) {
        String v = p.getProperty(k);
        return v == null ? d : Boolean.parseBoolean(v);
    }

    private int num(String k, int d) {
        try {
            return Integer.parseInt(p.getProperty(k, String.valueOf(d)));
        } catch (NumberFormatException e) {
            return d;
        }
    }

    private long lng(String k, long d) {
        try {
            return Long.parseLong(p.getProperty(k, String.valueOf(d)));
        } catch (NumberFormatException e) {
            return d;
        }
    }

    private void put(String k, Object v) {
        p.setProperty(k, String.valueOf(v));
        save();
    }
}
