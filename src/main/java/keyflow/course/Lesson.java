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

    public List<Exercise> practiceCards() {
        List<Exercise> out = new ArrayList<>();
        for (Exercise e : exercises) if (e.kind.isPractice()) out.add(e);
        return out;
    }
}
