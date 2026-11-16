package keyflow.course;

import java.util.ArrayList;
import java.util.List;

/** A chapter of a subject (for example "Objects and Classes"). */
public final class Unit {
    public String id;
    public String title = "";
    public String description = "";
    public String book = "";
    public Subject subject;
    public final List<Lesson> lessons = new ArrayList<>();

    /** The companion book's chapter number ("Core Java Vol. I, Chapter 3" gives 3), or 0. */
    public int chapter() {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("Chapter (\\d+)").matcher(book);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    /** "Chapter 3" for book-based units, otherwise "Unit 1" (position in the subject). */
    public String label() {
        return chapter() > 0 ? "Chapter " + chapter() : "Unit " + (subject.units.indexOf(this) + 1);
    }

    public List<Exercise> allExercises() {
        List<Exercise> out = new ArrayList<>();
        for (Lesson l : lessons) out.addAll(l.exercises);
        return out;
    }
}
