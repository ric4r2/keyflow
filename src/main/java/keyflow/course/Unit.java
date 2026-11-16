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

    public List<Exercise> allExercises() {
        List<Exercise> out = new ArrayList<>();
        for (Lesson l : lessons) out.addAll(l.exercises);
        return out;
    }
}
