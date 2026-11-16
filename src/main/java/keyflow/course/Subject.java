package keyflow.course;

import java.util.ArrayList;
import java.util.List;

/** Something to learn: a programming language, a terminal, or a tool such as Vim. */
public final class Subject {
    public String id;
    public String name = "";
    /** language, terminal or tool. */
    public String category = "language";
    public String description = "";
    public String book = "";
    /** available or planned. */
    public String status = "available";
    public final List<String> files = new ArrayList<>();
    public final List<Unit> units = new ArrayList<>();

    public boolean isAvailable() {
        return "available".equals(status) && !units.isEmpty();
    }

    public List<Exercise> allExercises() {
        List<Exercise> out = new ArrayList<>();
        for (Unit u : units) out.addAll(u.allExercises());
        return out;
    }

    public List<Lesson> allLessons() {
        List<Lesson> out = new ArrayList<>();
        for (Unit u : units) out.addAll(u.lessons);
        return out;
    }
}
