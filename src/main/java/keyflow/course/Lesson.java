package keyflow.course;

import java.util.ArrayList;
import java.util.List;

/** A micro-lesson: a handful of cards around one idea (about 3 to 8 minutes). */
public final class Lesson {
    public String id;
    public String title = "";
    public String intro = "";
    public String keys = "";
    /** Where to read more, e.g. "Core Java Vol. I, section 3.3". */
    public String book = "";
    public Unit unit;
    public final List<Exercise> exercises = new ArrayList<>();

    /** The book section(s) this lesson covers, e.g. "Section 3.5" or "Sections 4.1-4.2", or "". */
    public String section() {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(sections?) ([0-9][0-9.\\-]*)").matcher(book);
        if (!m.find()) return "";
        return Character.toUpperCase(m.group(1).charAt(0)) + m.group(1).substring(1) + " " + m.group(2).replace("-", "–");
    }

    public List<Exercise> practiceCards() {
        List<Exercise> out = new ArrayList<>();
        for (Exercise e : exercises) if (e.kind.isPractice()) out.add(e);
        return out;
    }
}
