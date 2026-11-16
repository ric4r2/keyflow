package keyflow;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import keyflow.vim.VimEngine;

/** Minimal dependency-free test runner: {@code java -cp ... keyflow.TestRunner}. */
public final class TestRunner {

    private int passed;
    private final List<String> failures = new ArrayList<>();

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true");
        TestRunner t = new TestRunner();
        EngineTest.run(t);
        CurriculumTest.run(t);
        ThemeTest.run(t);
        keyflow.ui.NavigationTest.run(t);
        System.out.println();
        for (String f : t.failures) System.out.println("FAIL " + f);
        System.out.println(t.passed + " passed, " + t.failures.size() + " failed");
        System.exit(t.failures.isEmpty() ? 0 : 1);
    }

    void vim(String name, String start, int r, int c, String keys, String expected) {
        try {
            VimEngine e = new VimEngine(start);
            e.setCursor(r, c);
            e.keys(keys);
            String got = e.getText();
            if (got.equals(expected)) {
                passed++;
            } else {
                failures.add(name + "\n     keys:     " + keys + "\n     expected: " + show(expected) + "\n     got:      " + show(got));
            }
        } catch (Throwable ex) {
            failures.add(name + " threw " + ex);
            ex.printStackTrace();
        }
    }

    public void check(String name, BooleanSupplier s) {
        try {
            if (s.getAsBoolean()) passed++;
            else failures.add(name);
        } catch (Throwable ex) {
            failures.add(name + " threw " + ex);
            ex.printStackTrace();
        }
    }

    public void fail(String msg) {
        failures.add(msg);
    }

    void pass() {
        passed++;
    }

    static String show(String s) {
        return "\"" + s.replace("\n", "\\n") + "\"";
    }
}
