package keyflow.course;

import java.util.ArrayList;
import java.util.List;

/** A career path that strings subjects together, e.g. Backend = Java + Linux + SQL + Vim. */
public final class LearningPath {
    public String id;
    public String name = "";
    public String description = "";
    /** Subject ids in the recommended order. */
    public final List<String> steps = new ArrayList<>();
}
