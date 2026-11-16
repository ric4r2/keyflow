package keyflow;

import java.util.List;

import keyflow.course.Catalog;
import keyflow.course.Exercise;
import keyflow.course.JavaChecker;
import keyflow.course.Subject;
import keyflow.ui.Checks;
import keyflow.vim.VimEngine;

/** Validates every card of every subject: Vim solutions must solve their drills, Java answers must compile. */
final class CurriculumTest {

    private CurriculumTest() {
    }

    static void run(TestRunner t) {
        Catalog catalog;
        try {
            catalog = Catalog.load();
        } catch (RuntimeException e) {
            t.fail("curriculum failed to load: " + e.getMessage());
            return;
        }
        for (Subject s : catalog.subjects) {
            if (s.isAvailable() == s.units.isEmpty() && "available".equals(s.status)) {
                t.fail("subject " + s.id + " is marked available but has no units");
            }
        }
        for (keyflow.course.LearningPath p : catalog.paths) {
            for (String step : p.steps) {
                if (catalog.subject(step) == null) t.fail("path " + p.id + " references unknown subject " + step);
            }
        }
        int[] counts = new int[Exercise.Kind.values().length];
        for (Exercise e : catalog.allCards()) {
            counts[e.kind.ordinal()]++;
            String where = e.kind + " " + e.id + " (" + e.title + ")";
            switch (e.kind) {
                case READ:
                    if (e.text.isEmpty() && e.code.isEmpty()) t.fail(where + ": empty");
                    else t.pass();
                    break;
                case QUIZ:
                    checkQuiz(t, e, where);
                    break;
                case VIM:
                    checkVim(t, e, where);
                    break;
                default:
                    checkCode(t, e, where);
            }
        }
        StringBuilder sb = new StringBuilder("Curriculum: " + catalog.allLessons().size() + " lessons, ");
        for (Exercise.Kind k : Exercise.Kind.values()) sb.append(counts[k.ordinal()]).append(' ').append(k.name().toLowerCase()).append(", ");
        sb.append(JavaChecker.compilerAvailable() ? "compiler on Java " + JavaChecker.runtimeVersion() : "no compiler");
        System.out.println(sb);
    }

    private static void checkQuiz(TestRunner t, Exercise e, String where) {
        if (e.title.isEmpty() && e.text.isEmpty()) t.fail(where + ": no question");
        else if (e.options.size() < 2) t.fail(where + ": needs at least 2 options");
        else if (e.correct < 0) t.fail(where + ": no correct option (use option*:)");
        else if (e.explain.isEmpty()) t.fail(where + ": missing explanation");
        else t.pass();
        long stars = e.options.stream().filter(o -> o.startsWith("*")).count();
        if (stars > 0) t.fail(where + ": option text starts with *");
    }

    private static void checkCode(TestRunner t, Exercise e, String where) {
        if (e.task.isEmpty()) t.fail(where + ": missing task");
        if (e.answers.isEmpty() && e.checks.isEmpty()) {
            t.fail(where + ": needs answer or check");
            return;
        }
        if (e.kind == Exercise.Kind.TYPE && e.code.contains("\t")) t.fail(where + ": tab in code");
        if (e.kind != Exercise.Kind.TYPE && !e.answers.isEmpty() && JavaChecker.matchesAny(e.start, e.answers)) {
            t.fail(where + ": start already matches an answer");
        }
        for (String a : e.answers) {
            JavaChecker.Result r = JavaChecker.compile(a, e.compile, e.since);
            if (!r.ok) t.fail(where + ": answer does not compile: " + r.errors + "\n" + a);
            else t.pass();
            List<String> failed = JavaChecker.failedChecks(a, e.checks);
            if (!failed.isEmpty()) t.fail(where + ": answer fails checks " + failed);
        }
        if (e.kind != Exercise.Kind.TYPE && !e.checks.isEmpty()) {
            JavaChecker.Result r = JavaChecker.compile(e.start, e.compile, e.since);
            if (JavaChecker.accepts(e, e.start, r)) t.fail(where + ": the starting code is already accepted");
        }
    }

    private static void checkVim(TestRunner t, Exercise e, String where) {
        if (e.solution.isEmpty()) {
            t.fail(where + ": missing solution");
            return;
        }
        if (e.task.isEmpty()) t.fail(where + ": missing task");
        if (!e.hasGoal() && Checks.sameText(e.start, e.target)) t.fail(where + ": start equals target and no goal");
        VimEngine eng = new VimEngine(e.start);
        eng.setCursor(e.cursorRow, e.cursorCol);
        if (eng.getRow() != e.cursorRow || eng.getCol() != e.cursorCol) {
            t.fail(where + ": start cursor is outside the text");
            return;
        }
        if (Checks.vimSolved(e, eng)) {
            t.fail(where + ": already solved before any key");
            return;
        }
        eng.keys(e.solution);
        if (Checks.vimSolved(e, eng)) {
            t.pass();
        } else {
            t.fail(where + ": solution '" + e.solution + "' did not solve it"
                    + "\n     expected: " + TestRunner.show(e.target) + (e.hasGoal() ? " cursor " + e.goalRow + "," + e.goalCol : "")
                    + "\n     got:      " + TestRunner.show(eng.getText()) + " cursor " + eng.getRow() + "," + eng.getCol());
        }
    }
}
