package keyflow.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Callable;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;

import keyflow.TestRunner;
import keyflow.course.Catalog;
import keyflow.course.Exercise;
import keyflow.course.Lesson;
import keyflow.course.Subject;
import keyflow.data.Progress;

/**
 * Drives the real screens (headless, no window) through every way a user can move around the app:
 * clicking tiles, rows, options and buttons the way the mouse does, the keyboard shortcuts, and the
 * history (Back, logo, Esc, Alt+Left).
 */
public final class NavigationTest {

    private static Catalog catalog;
    private static TestRunner t;

    private NavigationTest() {
    }

    public static void run(TestRunner runner) {
        t = runner;
        catalog = Catalog.load();
        Subject java = catalog.subject("java"), vim = catalog.subject("vim");
        Lesson vim1 = vim.allLessons().get(0), java1 = java.allLessons().get(0);

        // ---------------------------------------------------------------- home
        ui("start: home screen, nothing to go back to", () -> {
            App a = app();
            return a.currentScreen() instanceof HomeScreen && !a.canGoBack() && find(a, "← Back") == null
                    && a.historyKeys().equals(Arrays.asList("home"));
        });
        ui("back on home stays on home", () -> {
            App a = app();
            a.back();
            return a.currentScreen() instanceof HomeScreen && a.historyKeys().equals(Arrays.asList("home"));
        });
        ui("home: click a subject tile opens its map", () -> {
            App a = app();
            clickText(a, "Vim");
            return a.currentScreen() instanceof SubjectScreen && a.historyKeys().equals(Arrays.asList("home", "subject:vim"))
                    && find(a, "← Back") != null;
        });
        ui("home: a 'coming soon' subject does nothing", () -> {
            App a = app();
            clickText(a, "Python");
            return a.currentScreen() instanceof HomeScreen;
        });
        ui("home: click a path's subject chip opens the subject", () -> {
            App a = app();
            Component chip = null;
            for (Component c : all(a.currentScreen())) {
                if (c instanceof JLabel && "Java".equals(((JLabel) c).getText()) && c.getParent() != null
                        && c.getParent().getParent() != null && isPathRow(c)) chip = c;
            }
            if (chip == null) return false;
            mouse(chip);
            return a.historyKeys().equals(Arrays.asList("home", "subject:java"));
        });
        ui("home: Start opens the next lesson", () -> {
            App a = app();
            mouse(find(a, "Start"));
            return a.currentScreen() instanceof LessonPlayer && a.historyKeys().size() == 2
                    && a.historyKeys().get(1).equals("lesson:" + java1.id);
        });
        ui("home: Enter opens the next lesson", () -> {
            App a = app();
            a.currentScreen().getActionMap().get("kf-continue").actionPerformed(null);
            return a.currentScreen() instanceof LessonPlayer;
        });
        ui("home: Continue resumes the lesson you left", () -> {
            App a = app();
            a.startLesson(catalog.lesson("J3.6"));
            a.showHome();
            mouse(find(a, "Start"));
            return a.historyKeys().get(1).equals("lesson:J3.6");
        });

        // ---------------------------------------------------------------- subject map
        ui("subject: click a lesson row opens the lesson", () -> {
            App a = app();
            a.showSubject(vim);
            clickText(a, vim1.title);
            return a.currentScreen() instanceof LessonPlayer
                    && a.historyKeys().equals(Arrays.asList("home", "subject:vim", "lesson:" + vim1.id));
        });
        ui("subject: Back returns home", () -> {
            App a = app();
            a.showSubject(java);
            mouse(find(a, "← Back"));
            return a.currentScreen() instanceof HomeScreen && !a.canGoBack();
        });
        ui("subject: Esc returns home", () -> {
            App a = app();
            a.showSubject(java);
            esc(a);
            return a.currentScreen() instanceof HomeScreen;
        });
        ui("subject: the logo returns home", () -> {
            App a = app();
            a.showSubject(java);
            clickLogo(a);
            return a.currentScreen() instanceof HomeScreen && a.historyKeys().equals(Arrays.asList("home"));
        });

        // ---------------------------------------------------------------- leaving a lesson (reported bug)
        ui("lesson: Back returns to the subject map", () -> {
            App a = app();
            a.showSubject(vim);
            a.startLesson(vim1);
            mouse(find(a, "← Back"));
            return a.currentScreen() instanceof SubjectScreen && a.historyKeys().equals(Arrays.asList("home", "subject:vim"));
        });
        ui("lesson: Back twice returns home", () -> {
            App a = app();
            a.showSubject(vim);
            a.startLesson(vim1);
            a.back();
            a.back();
            return a.currentScreen() instanceof HomeScreen;
        });
        ui("lesson opened from home: Back returns home", () -> {
            App a = app();
            a.startLesson(vim1);
            a.back();
            return a.currentScreen() instanceof HomeScreen;
        });
        ui("lesson: Alt+Left goes back", () -> {
            App a = app();
            a.showSubject(vim);
            a.startLesson(vim1);
            key(a, "alt LEFT");
            return a.currentScreen() instanceof SubjectScreen;
        });
        ui("lesson: Esc does not leave the lesson (Esc belongs to Vim)", () -> {
            App a = app();
            a.showSubject(vim);
            a.startLesson(vim1);
            esc(a);
            return a.currentScreen() instanceof LessonPlayer;
        });
        ui("lesson: the logo returns home", () -> {
            App a = app();
            a.showSubject(vim);
            a.startLesson(vim1);
            clickLogo(a);
            return a.currentScreen() instanceof HomeScreen && !a.canGoBack();
        });
        ui("lesson: reopening its subject pops back instead of looping", () -> {
            App a = app();
            a.showSubject(vim);
            a.startLesson(vim1);
            a.showSubject(vim);
            return a.historyKeys().equals(Arrays.asList("home", "subject:vim"));
        });

        // ---------------------------------------------------------------- settings (reported bug)
        ui("settings from home: Back returns home", () -> {
            App a = app();
            mouse(find(a, "Settings"));
            boolean onSettings = a.currentScreen() instanceof SettingsScreen;
            mouse(find(a, "← Back"));
            return onSettings && a.currentScreen() instanceof HomeScreen;
        });
        ui("settings: Esc goes back", () -> {
            App a = app();
            a.showSettings();
            esc(a);
            return a.currentScreen() instanceof HomeScreen;
        });
        ui("settings: no Settings button on the settings screen, and no duplicates", () -> {
            App a = app();
            a.showSettings();
            boolean hidden = find(a, "Settings") == null;
            a.showSettings();
            return hidden && a.historyKeys().equals(Arrays.asList("home", "settings"));
        });
        ui("settings from a lesson: Back returns to the same card", () -> {
            App a = app();
            a.showSubject(vim);
            a.startLesson(vim1);
            LessonPlayer p = player(a);
            p.advance();
            Exercise card = p.currentCard();
            mouse(find(a, "Settings"));
            mouse(find(a, "← Back"));
            return a.currentScreen() == p && p.currentCard() == card;
        });
        ui("settings: changing the theme stays on settings", () -> {
            App a = app();
            a.showSettings();
            mouse(find(a, "Dark"));
            boolean ok = a.currentScreen() instanceof SettingsScreen && "dark".equals(a.progress.theme()) && Theme.get().dark;
            mouse(find(a, "Light"));
            return ok && !Theme.get().dark;
        });
        ui("settings: theme change, then Back restyles the lesson at the same card", () -> {
            App a = app();
            a.startLesson(vim1);
            LessonPlayer p = player(a);
            p.advance();
            p.advance();
            Exercise card = p.currentCard();
            a.showSettings();
            mouse(find(a, "Dark"));
            a.back();
            LessonPlayer q = player(a);
            boolean ok = q != null && q != p && q.currentCard() == card && q.queueSize() == p.queueSize();
            a.progress.setTheme("light");
            a.refresh();
            return ok;
        });
        ui("settings: text size change, then Back keeps the lesson", () -> {
            App a = app();
            a.startLesson(java1);
            player(a).advance();
            Exercise card = player(a).currentCard();
            a.showSettings();
            mouse(find(a, "+"));
            a.back();
            boolean ok = player(a).currentCard() == card;
            a.progress.setFontSize(17);
            a.refresh();
            return ok;
        });
        ui("settings: Vim keys off applies to the lesson's code editor", () -> {
            App a = app();
            Lesson withCode = firstLessonWith(java, Exercise.Kind.FIX);
            a.startLesson(withCode);
            LessonPlayer p = player(a);
            while (p.currentCard() != null && p.currentCard().kind != Exercise.Kind.FIX) p.advance();
            a.showSettings();
            boolean wasOn = a.progress.vimKeys();
            mouse(find(a, "Off"));
            a.back();
            CodeEditor ed = player(a).currentEditor();
            return wasOn && ed != null && !ed.isVim() && player(a).currentCard().kind == Exercise.Kind.FIX;
        });
        ui("lesson Vim toggle keeps typed code when visiting settings", () -> {
            App a = app();
            Lesson withCode = firstLessonWith(java, Exercise.Kind.FIX);
            a.startLesson(withCode);
            LessonPlayer p = player(a);
            while (p.currentCard().kind != Exercise.Kind.FIX) p.advance();
            mouse(find(a, "Vim keys:"));
            p.currentEditor().type("abc");
            String typed = p.currentEditor().getText();
            a.showSettings();
            a.back();
            return a.currentScreen() == p && p.currentEditor().getText().equals(typed) && !p.currentEditor().isVim();
        });
        ui("settings: reset asks first; Cancel keeps progress", () -> {
            App a = app();
            a.progress.setLessonDone(java1);
            a.confirm = msg -> false;
            a.showSettings();
            mouse(find(a, "Reset progress"));
            return a.progress.lessonDone(java1);
        });
        ui("settings: reset confirmed erases progress and stays on settings", () -> {
            App a = app();
            a.progress.setLessonDone(java1);
            a.confirm = msg -> true;
            a.showSettings();
            mouse(find(a, "Reset progress"));
            return !a.progress.lessonDone(java1) && a.currentScreen() instanceof SettingsScreen;
        });
        ui("settings: steppers stay inside their limits", () -> {
            App a = app();
            a.showSettings();
            for (int i = 0; i < 40; i++) {
                List<Ui.Button> minus = findAll(a, "−");
                for (Ui.Button b : minus) b.click();
            }
            return a.progress.fontSize() == 13 && a.progress.focusMinutes() == 5 && a.progress.breakMinutes() == 1
                    && a.progress.dailyGoal() == 10;
        });

        // ---------------------------------------------------------------- playing cards
        ui("quiz: clicking the option text answers it", () -> {
            App a = app();
            Lesson l = firstLessonWith(java, Exercise.Kind.QUIZ);
            a.startLesson(l);
            LessonPlayer p = player(a);
            while (p.currentCard().kind != Exercise.Kind.QUIZ) p.advance();
            Exercise q = p.currentCard();
            clickTextIn(a, firstWords(q.options.get(q.correct)));
            return p.cardDone() && "Continue".equals(p.primaryButton().getText());
        });
        ui("quiz: number keys answer, Enter continues", () -> {
            App a = app();
            Lesson l = firstLessonWith(java, Exercise.Kind.QUIZ);
            a.startLesson(l);
            LessonPlayer p = player(a);
            while (p.currentCard().kind != Exercise.Kind.QUIZ) p.advance();
            int pos = p.position();
            key(p, String.valueOf(p.currentCard().correct + 1));
            boolean answered = p.cardDone();
            key(p, "ENTER");
            return answered && p.position() == pos + 1;
        });
        ui("quiz: Enter does nothing before an answer", () -> {
            App a = app();
            Lesson l = firstLessonWith(java, Exercise.Kind.QUIZ);
            a.startLesson(l);
            LessonPlayer p = player(a);
            while (p.currentCard().kind != Exercise.Kind.QUIZ) p.advance();
            int pos = p.position();
            key(p, "ENTER");
            return p.position() == pos && !p.cardDone();
        });
        ui("quiz: a wrong answer comes back at the end", () -> {
            App a = app();
            Lesson l = firstLessonWith(java, Exercise.Kind.QUIZ);
            a.startLesson(l);
            LessonPlayer p = player(a);
            while (p.currentCard().kind != Exercise.Kind.QUIZ) p.advance();
            int size = p.queueSize();
            Exercise q = p.currentCard();
            p.pickOption(q.correct == 0 ? 1 : 0);
            return p.queueSize() == size + 1;
        });
        ui("vim drill: solving it enables Continue", () -> {
            App a = app();
            a.startLesson(vim1);
            LessonPlayer p = player(a);
            while (p.currentCard().kind != Exercise.Kind.VIM) p.advance();
            p.currentEditor().type(p.currentCard().solution);
            return p.cardDone() && p.primaryButton().isEnabled() && "Continue".equals(p.primaryButton().getText());
        });
        ui("vim drill: F5 resets the buffer", () -> {
            App a = app();
            a.startLesson(vim1);
            LessonPlayer p = player(a);
            while (p.currentCard().kind != Exercise.Kind.VIM) p.advance();
            p.currentEditor().type("x");
            key(p, "F5");
            return p.currentEditor().getText().equals(p.currentCard().start) && p.currentEditor().engine().getKeystrokes() == 0;
        });
        ui("code card: Show answer completes it and it comes back", () -> {
            App a = app();
            a.startLesson(firstLessonWith(java, Exercise.Kind.FIX));
            LessonPlayer p = player(a);
            while (p.currentCard().kind != Exercise.Kind.FIX) p.advance();
            int size = p.queueSize();
            mouse(find(a, "Show answer"));
            return p.cardDone() && p.queueSize() == size + 1;
        });

        // ---------------------------------------------------------------- finishing
        for (Subject s : Arrays.asList(vim, java)) {
            Lesson first = s.allLessons().get(0), second = s.allLessons().get(1);
            ui(s.id + ": finishing a lesson marks it done and offers the next one", () -> {
                App a = app();
                a.showSubject(s);
                a.startLesson(first);
                playToEnd(player(a));
                return player(a).finished() && a.progress.lessonDone(first)
                        && player(a).primaryButton().getText().startsWith("Next:") && find(a, "Back to " + s.name) != null;
            });
            ui(s.id + ": Next replaces the finished lesson, Back still leads to the map", () -> {
                App a = app();
                a.showSubject(s);
                a.startLesson(first);
                playToEnd(player(a));
                key(player(a), "ENTER");
                boolean next = a.historyKeys().equals(Arrays.asList("home", "subject:" + s.id, "lesson:" + second.id));
                a.back();
                return next && a.currentScreen() instanceof SubjectScreen;
            });
            ui(s.id + ": 'Back to subject' after a lesson opened from home", () -> {
                App a = app();
                a.startLesson(first);
                playToEnd(player(a));
                mouse(find(a, "Back to " + s.name));
                boolean ok = a.historyKeys().equals(Arrays.asList("home", "subject:" + s.id));
                a.back();
                return ok && a.currentScreen() instanceof HomeScreen;
            });
        }
        ui("every lesson in every subject can be played to the end", () -> {
            App a = app();
            List<String> stuck = new ArrayList<>();
            for (Subject s : catalog.subjects) {
                if (!s.isAvailable()) continue;
                for (Lesson l : s.allLessons()) {
                    a.startLesson(l);
                    if (!playToEnd(player(a))) stuck.add(l.id);
                    a.showHome();
                }
            }
            if (!stuck.isEmpty()) t.fail("lessons that could not be finished: " + stuck);
            return stuck.isEmpty();
        });
        ui("warm-up: a lesson after a done one starts with a recall card from it", () -> {
            App a = app();
            Lesson prev = java.allLessons().get(0), l = java.allLessons().get(1);
            a.progress.setLessonDone(prev);
            a.startLesson(l);
            Exercise c = player(a).currentCard();
            return c.lesson == prev && c.kind == Exercise.Kind.QUIZ;
        });
        ui("warm-up in a Vim lesson comes before the intro", () -> {
            App a = app();
            // Vim lessons have no quizzes of their own, so build the queue the way withWarmUp would
            Lesson l = vim.allLessons().get(1);
            Exercise recall = firstLessonWith(java, Exercise.Kind.QUIZ).exercises.stream()
                    .filter(e -> e.kind == Exercise.Kind.QUIZ).findFirst().get();
            List<Exercise> cards = new ArrayList<>(l.exercises);
            cards.add(0, recall);
            LessonPlayer p = new LessonPlayer(a, "test", cards, l);
            p.begin();
            Exercise first = p.currentCard();
            p.advance();
            Exercise second = p.currentCard();
            return first == recall && second.id.endsWith(".intro");
        });

        // ---------------------------------------------------------------- review
        ui("review with nothing due stays home", () -> {
            App a = app();
            a.startReview();
            return a.currentScreen() instanceof HomeScreen && !a.canGoBack();
        });
        ui("review: due cards play, Back returns home, finishing offers Home", () -> {
            App a = new App(catalog, dueProgress(java1));
            a.start();
            layout(a);
            Ui.Button review = find(a, "Review ");
            if (review == null) return false;
            mouse(review);
            boolean inReview = a.historyKeys().equals(Arrays.asList("home", "review"));
            a.back();
            boolean back = a.currentScreen() instanceof HomeScreen;
            a.startReview();
            playToEnd(player(a));
            boolean home = "Home".equals(player(a).primaryButton().getText());
            key(player(a), "ENTER");
            return inReview && back && home && a.currentScreen() instanceof HomeScreen;
        });
    }

    // ------------------------------------------------------------------ helpers

    private static void ui(String name, Callable<Boolean> body) {
        t.check("nav: " + name, () -> {
            final boolean[] ok = new boolean[1];
            final Throwable[] err = new Throwable[1];
            try {
                SwingUtilities.invokeAndWait(() -> {
                    try {
                        ok[0] = body.call();
                    } catch (Throwable e) {
                        err[0] = e;
                    }
                });
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            if (err[0] != null) throw new RuntimeException(err[0]);
            return ok[0];
        });
    }

    private static App app() {
        App a = new App(catalog, Progress.memory());
        Theme.set("light");
        a.start();
        layout(a);
        return a;
    }

    private static Progress dueProgress(Lesson l) throws Exception {
        File dir = new File("build/nav-test-home");
        dir.mkdirs();
        Properties p = new Properties();
        for (Exercise e : l.exercises) {
            if (!e.kind.isPractice()) continue;
            p.setProperty("c." + e.id + ".seen", "1");
            p.setProperty("c." + e.id + ".box", "1");
            p.setProperty("c." + e.id + ".due", "0");
        }
        try (OutputStream out = new FileOutputStream(new File(dir, "progress.properties"))) {
            p.store(out, null);
        }
        String old = System.getProperty("keyflow.home");
        System.setProperty("keyflow.home", dir.getAbsolutePath());
        try {
            Progress pr = Progress.load();
            return pr;
        } finally {
            if (old == null) System.clearProperty("keyflow.home");
            else System.setProperty("keyflow.home", old);
        }
    }

    private static LessonPlayer player(App a) {
        layout(a);
        return a.currentScreen() instanceof LessonPlayer ? (LessonPlayer) a.currentScreen() : null;
    }

    /** Answer every card correctly (or via Show answer when there is no reference answer). */
    private static boolean playToEnd(LessonPlayer p) {
        for (int guard = 0; guard < 400 && !p.finished(); guard++) {
            Exercise e = p.currentCard();
            if (!p.cardDone()) {
                switch (e.kind) {
                    case READ:
                        break;
                    case QUIZ:
                        p.pickOption(e.correct);
                        break;
                    case VIM:
                        p.currentEditor().type(e.solution);
                        if (!p.cardDone()) return false;
                        break;
                    default:
                        if (!e.answers.isEmpty()) {
                            p.currentEditor().engine().setText(e.answers.get(0));
                            p.primaryButton().click();
                        }
                        if (!p.cardDone()) {
                            Ui.Button show = null;
                            for (Component c : all(p)) {
                                if (c instanceof Ui.Button && "Show answer".equals(((Ui.Button) c).getText())) show = (Ui.Button) c;
                            }
                            if (show == null) return false;
                            show.click();
                        }
                }
            }
            p.primaryButton().click();
        }
        return p.finished();
    }

    private static Lesson firstLessonWith(Subject s, Exercise.Kind kind) {
        for (Lesson l : s.allLessons()) for (Exercise e : l.exercises) if (e.kind == kind) return l;
        throw new IllegalStateException("no lesson with " + kind);
    }

    private static String firstWords(String md) {
        String plain = md.replace("`", "").replace("*", "");
        return plain.length() > 12 ? plain.substring(0, 12) : plain;
    }

    private static boolean isPathRow(Component c) {
        for (Component p = c; p != null; p = p.getParent()) {
            if (p instanceof Ui.Card) {
                for (Component k : all((Container) p)) {
                    if (k instanceof JLabel && "Backend Developer".equals(((JLabel) k).getText())) return true;
                }
                return false;
            }
        }
        return false;
    }

    static void layout(App a) {
        JComponent root = a.rootPane();
        root.setSize(1180, 820);
        doLayout(root);
    }

    private static void doLayout(Component c) {
        if (c instanceof Container) {
            ((Container) c).doLayout();
            for (Component k : ((Container) c).getComponents()) doLayout(k);
        }
    }

    private static List<Component> all(Container c) {
        List<Component> out = new ArrayList<>();
        for (Component k : c.getComponents()) {
            out.add(k);
            if (k instanceof Container) out.addAll(all((Container) k));
        }
        return out;
    }

    private static boolean showing(Component c, Component root) {
        for (Component p = c; p != null && p != root; p = p.getParent()) if (!p.isVisible()) return false;
        return true;
    }

    private static Ui.Button find(App a, String prefix) {
        List<Ui.Button> b = findAll(a, prefix);
        for (Ui.Button x : b) if (x.getText().equals(prefix)) return x;   // exact label wins over a prefix
        return b.isEmpty() ? null : b.get(0);
    }

    private static List<Ui.Button> findAll(App a, String prefix) {
        layout(a);
        List<Ui.Button> out = new ArrayList<>();
        for (Component c : all(a.rootPane())) {
            if (c instanceof Ui.Button && ((Ui.Button) c).getText().startsWith(prefix) && showing(c, a.rootPane())) out.add((Ui.Button) c);
        }
        return out;
    }

    /** Click the first label or text block in the screen that shows this text, like the mouse would. */
    private static void clickText(App a, String text) {
        layout(a);
        for (Component c : all(a.currentScreen())) {
            String s = c instanceof JLabel ? ((JLabel) c).getText() : null;
            if (text.equals(s)) {
                mouse(c);
                return;
            }
        }
        throw new IllegalStateException("no text " + text);
    }

    /** Like clickText, but also matches buttons and text panes containing the text. */
    private static void clickTextIn(App a, String text) {
        layout(a);
        for (Component c : all(a.currentScreen())) {
            String s = c instanceof JLabel ? ((JLabel) c).getText()
                    : c instanceof JTextComponent ? ((JTextComponent) c).getText()
                    : c instanceof Ui.Button ? ((Ui.Button) c).getText() : null;
            if (s != null && (c instanceof Ui.Button ? s.equals(text) : s.contains(text)) && showing(c, a.rootPane())) {
                mouse(c);
                return;
            }
        }
        throw new IllegalStateException("no text " + text);
    }

    private static void clickLogo(App a) {
        layout(a);
        for (Component c : all(a.rootPane())) {
            if (c instanceof JLabel && "keyflow".equals(((JLabel) c).getText())) {
                mouse(c);
                return;
            }
        }
        throw new IllegalStateException("no logo");
    }

    /** A press and release, with the mouse moving a couple of pixels in between (a normal human click). */
    private static void mouse(Component c) {
        if (c == null) throw new IllegalStateException("nothing to click");
        int x = Math.max(1, c.getWidth() / 2), y = Math.max(1, c.getHeight() / 2);
        long now = System.currentTimeMillis();
        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_PRESSED, now, InputEvent.BUTTON1_DOWN_MASK, x, y, 1, false, MouseEvent.BUTTON1));
        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_RELEASED, now + 80, 0, x + 2, y, 1, false, MouseEvent.BUTTON1));
    }

    private static void esc(App a) {
        key(a, "ESCAPE");
    }

    private static void key(App a, String stroke) {
        key(a.rootPane(), stroke);
    }

    private static void key(JComponent c, String stroke) {
        Object name = c.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(KeyStroke.getKeyStroke(stroke));
        if (name == null) throw new IllegalStateException("no binding for " + stroke);
        c.getActionMap().get(name).actionPerformed(null);
    }
}
