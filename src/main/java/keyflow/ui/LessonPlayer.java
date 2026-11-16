package keyflow.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import keyflow.course.Exercise;
import keyflow.course.JavaChecker;
import keyflow.course.Lesson;
import keyflow.vim.VimEngine;

/**
 * Plays a list of cards one at a time. Designed for attention: one idea per screen, one primary
 * button, instant feedback, and missed cards come back at the end until they are right.
 */
final class LessonPlayer extends JPanel {

    private final App app;
    private final String heading;
    private final Lesson lesson;
    private final List<Exercise> queue;
    private final Set<String> requeued = new HashSet<>();
    private final Set<Exercise> warmUps = new HashSet<>();
    private final int originalSize;
    private int index = -1, firstTryRight, practiced, xp;
    private final long startMs = System.currentTimeMillis();

    // per-card state
    private Exercise card;
    private boolean done, revealed;
    private int tries, hints;
    private CodeEditor editor;
    private Ui.Card feedback;
    private JPanel optionsPanel;

    private final Ui.Page page = new Ui.Page(new Ui.Column(780, 18));
    private final JScrollPane scroll;
    private final Ui.Bar bar = new Ui.Bar(5);
    private final JLabel counter;
    private final Ui.Button primary = Ui.primary("Continue", () -> { });
    private final Ui.Button hintBtn = Ui.ghost("Hint", this::showHint);
    private final Ui.Button answerBtn = Ui.ghost("Show answer", this::reveal);
    private final Ui.Button resetBtn = Ui.ghost("Reset", this::resetVim);
    private final Ui.Button vimToggle;

    LessonPlayer(App app, String heading, List<Exercise> cards, Lesson lesson) {
        super(new BorderLayout());
        this.app = app;
        this.heading = heading;
        this.lesson = lesson;
        this.queue = new ArrayList<>(cards);
        if (lesson != null && !lesson.intro.isEmpty() && lesson.exercises.stream().noneMatch(e -> e.kind == Exercise.Kind.READ)) {
            Exercise intro = new Exercise();
            intro.kind = Exercise.Kind.READ;
            intro.id = lesson.id + ".intro";
            intro.title = lesson.title;
            intro.text = lesson.intro + (lesson.keys.isEmpty() ? "" : "\n**Keys in this lesson:** `" + lesson.keys + "`");
            intro.lesson = lesson;
            queue.add(0, intro);
        }
        this.originalSize = queue.size();
        Theme t = Theme.get();
        setBackground(t.bg);

        JPanel head = Ui.clear(new BorderLayout(14, 6));
        head.setBorder(BorderFactory.createEmptyBorder(14, 28, 6, 28));
        head.add(Ui.muted(heading), BorderLayout.WEST);
        counter = Ui.muted("");
        head.add(counter, BorderLayout.EAST);
        head.add(bar, BorderLayout.SOUTH);
        add(head, BorderLayout.NORTH);

        page.setBorder(BorderFactory.createEmptyBorder(26, 28, 40, 28));
        scroll = Ui.scroll(page);
        add(scroll, BorderLayout.CENTER);

        vimToggle = Ui.ghost("", this::toggleVim).size(0.85f);
        JPanel foot = new JPanel(new BorderLayout());
        foot.setBackground(t.bg);
        foot.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, t.border),
                BorderFactory.createEmptyBorder(12, 28, 12, 28)));
        JPanel left = Ui.clear(new FlowLayout(FlowLayout.LEFT, 4, 0));
        left.add(hintBtn);
        left.add(answerBtn);
        left.add(resetBtn);
        left.add(vimToggle);
        foot.add(left, BorderLayout.WEST);
        JPanel right = Ui.clear(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        right.add(primary.size(1.0f));
        foot.add(right, BorderLayout.EAST);
        add(foot, BorderLayout.SOUTH);

        bind("ENTER", primary::click);
        for (int i = 1; i <= 6; i++) {
            final int n = i - 1;
            bind(String.valueOf(i), () -> pickOption(n));
        }
        bind("F5", this::resetVim);
    }

    /** Prepend one recall question from the previous lesson: retrieval practice as a warm-up. */
    static List<Exercise> withWarmUp(App app, Lesson l) {
        List<Exercise> out = new ArrayList<>(l.exercises);
        List<Lesson> all = l.unit.subject.allLessons();
        int i = all.indexOf(l);
        if (i > 0 && app.progress.lessonDone(all.get(i - 1))) {
            List<Exercise> quizzes = new ArrayList<>();
            for (Exercise e : all.get(i - 1).exercises) if (e.kind == Exercise.Kind.QUIZ) quizzes.add(e);
            if (!quizzes.isEmpty()) out.add(0, quizzes.get(new Random().nextInt(quizzes.size())));
        }
        return out;
    }

    void begin() {
        if (lesson != null && !queue.isEmpty() && !lesson.exercises.contains(queue.get(0)) && queue.get(0).lesson != lesson) {
            warmUps.add(queue.get(0));
        }
        next();
    }

    private void bind(String key, Runnable r) {
        String name = "kf-" + key;
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key), name);
        getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                r.run();
            }
        });
    }

    // ------------------------------------------------------------------ flow

    private void next() {
        index++;
        bar.setValue(index / (double) Math.max(1, queue.size()));
        if (index >= queue.size()) {
            finish();
            return;
        }
        card = queue.get(index);
        done = false;
        revealed = false;
        tries = 0;
        hints = 0;
        editor = null;
        optionsPanel = null;
        counter.setText((index + 1) + " / " + queue.size());
        render();
    }

    private void render() {
        Theme t = Theme.get();
        page.removeAll();
        Exercise e = card;

        JPanel tags = Ui.clear(new FlowLayout(FlowLayout.LEFT, 0, 0));
        tags.add(Ui.chip(warmUps.contains(e) ? "Warm-up recall" : e.kindLabel(), t.accent, t.accentSoft));
        if (e.lesson != null && e.lesson != lesson) {
            tags.add(Box.createHorizontalStrut(10));
            tags.add(Ui.muted(e.lesson.unit.subject.name + "  ·  " + e.lesson.title));
        }
        page.add(tags);

        String question = e.title;
        if (!question.isEmpty()) page.add(Ui.prose("**" + question + "**", 1.32f, t.text));

        hintBtn.setVisible(!e.hint.isEmpty());
        answerBtn.setVisible(e.kind != Exercise.Kind.READ && e.kind != Exercise.Kind.QUIZ);
        answerBtn.setText(e.kind == Exercise.Kind.VIM ? "Show solution" : "Show answer");
        resetBtn.setVisible(e.kind == Exercise.Kind.VIM);
        boolean codeCard = e.kind == Exercise.Kind.TYPE || e.kind == Exercise.Kind.FIX || e.kind == Exercise.Kind.WRITE;
        vimToggle.setVisible(codeCard);
        vimToggle.setText("Vim keys: " + (app.progress.vimKeys() ? "on" : "off"));

        switch (e.kind) {
            case READ:
                if (!e.text.isEmpty()) page.add(Ui.prose(e.text, 1.0f, t.text));
                if (!e.code.isEmpty()) page.add(codeView(e.code));
                primary.setText("Continue");
                primary.setEnabled(true);
                primary.setAction(this::advanceRead);
                break;
            case QUIZ:
                if (!e.text.isEmpty()) page.add(Ui.prose(e.text, 1.0f, t.text));
                if (!e.code.isEmpty()) page.add(codeView(e.code));
                optionsPanel = Ui.clear(null);
                optionsPanel.setLayout(new BoxLayout(optionsPanel, BoxLayout.Y_AXIS));
                for (int i = 0; i < e.options.size(); i++) {
                    optionsPanel.add(optionRow(i, e.options.get(i), null));
                    optionsPanel.add(Box.createVerticalStrut(10));
                }
                page.add(optionsPanel);
                primary.setText("Choose an answer");
                primary.setEnabled(false);
                break;
            case VIM:
                page.add(Ui.prose(e.task, 1.0f, t.text));
                if (e.lesson != null && !e.lesson.keys.isEmpty()) page.add(Ui.muted("Keys: " + e.lesson.keys));
                editor = new CodeEditor(e.start, false);
                editor.setVim(true);
                editor.engine().setCursor(e.cursorRow, e.cursorCol);
                if (e.hasGoal()) editor.setGoal(e.goalRow, e.goalCol);
                editor.setRows(3, 16);
                editor.setOnChange(this::vimChanged);
                page.add(editor);
                if (!e.hasGoal() || !Checks.sameText(e.start, e.target)) {
                    page.add(Ui.label("TARGET", 0.72f, Font.BOLD, t.muted));
                    CodeEditor target = codeView(e.target);
                    target.setCompareTo(null);
                    page.add(target);
                } else {
                    page.add(Ui.muted("Move the cursor onto the green box."));
                }
                updateVimStatus();
                primary.setText("Solve to continue");
                primary.setEnabled(false);
                primary.setAction(() -> { });
                break;
            default:
                page.add(Ui.prose(e.task, 1.0f, t.text));
                if (e.kind == Exercise.Kind.TYPE) {
                    page.add(codeView(e.code));
                    page.add(Ui.muted(app.progress.vimKeys() ? "Type it below: press i to insert, Esc when done, Ctrl+Enter to check." : "Type it below, then press Ctrl+Enter to check."));
                }
                editor = new CodeEditor(e.kind == Exercise.Kind.TYPE ? "" : e.start, false);
                editor.setVim(app.progress.vimKeys());
                editor.setRows(e.kind == Exercise.Kind.TYPE ? Math.max(3, e.code.split("\n").length + 1) : 4, 20);
                editor.setOnSubmit(this::checkCode);
                editor.setStatusRight(e.kind.name().toLowerCase());
                page.add(editor);
                primary.setText("Check");
                primary.setEnabled(true);
                primary.setAction(this::checkCode);
        }
        feedback = new Ui.Card(new BorderLayout(), 16);
        feedback.setVisible(false);
        page.add(feedback);
        page.revalidate();
        page.repaint();
        SwingUtilities.invokeLater(() -> {
            scroll.getVerticalScrollBar().setValue(0);
            if (editor != null) editor.requestFocusInWindow();
            else requestFocusInWindow();
        });
    }

    private CodeEditor codeView(String code) {
        CodeEditor v = new CodeEditor(code, true);
        int n = code.split("\n", -1).length;
        v.setRows(Math.min(n, 18), Math.min(n, 18));
        return v;
    }

    private void advanceRead() {
        if (!card.id.endsWith(".intro")) app.progress.markRead(card.id);
        next();
    }

    private void complete(int stars) {
        done = true;
        practiced++;
        if (stars >= 3 || (stars > 0 && tries <= 1 && hints == 0)) firstTryRight++;
        if (!card.id.endsWith(".intro")) xp += app.progress.record(card, stars);
        if (stars <= 0 && requeued.add(card.id)) queue.add(card);
        app.cardDone();
        primary.setText("Continue");
        primary.setEnabled(true);
        primary.setAction(this::next);
        counter.setText((index + 1) + " / " + queue.size());
    }

    private void showFeedback(boolean good, String md) {
        Theme t = Theme.get();
        feedback.removeAll();
        feedback.colors(() -> good ? Theme.get().successSoft : Theme.get().errorSoft, () -> null);
        feedback.add(Ui.prose(md, 0.95f, good ? (t.dark ? t.text : t.text) : t.text), BorderLayout.CENTER);
        JPanel stripe = new JPanel();
        stripe.setBackground(good ? t.success : t.error);
        stripe.setPreferredSize(new java.awt.Dimension(4, 4));
        feedback.add(stripe, BorderLayout.WEST);
        ((BorderLayout) feedback.getLayout()).setHgap(14);
        feedback.setVisible(true);
        page.revalidate();
        page.repaint();
        SwingUtilities.invokeLater(() -> feedback.scrollRectToVisible(new java.awt.Rectangle(0, 0, feedback.getWidth(), feedback.getHeight() + 40)));
    }

    // ------------------------------------------------------------------ quiz

    private JComponent optionRow(int i, String text, Boolean state) {
        Theme t = Theme.get();
        Ui.Card row = new Ui.Card(new BorderLayout(14, 0), 12, 12);
        Color fill = state == null ? t.surface : state ? t.successSoft : t.errorSoft;
        Color stroke = state == null ? t.border : state ? t.success : t.error;
        row.colors(() -> fill, () -> stroke);
        row.add(Ui.label(String.valueOf(i + 1), 0.85f, Font.BOLD, t.muted), BorderLayout.WEST);
        row.add(Ui.prose(text, 1.0f, t.text), BorderLayout.CENTER);
        if (state == null) {
            row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            row.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    pickOption(i);
                }
            });
        }
        return row;
    }

    void pickOption(int i) {
        if (card == null || card.kind != Exercise.Kind.QUIZ || done || i >= card.options.size()) return;
        boolean right = i == card.correct;
        tries++;
        optionsPanel.removeAll();
        for (int k = 0; k < card.options.size(); k++) {
            Boolean st = k == card.correct ? Boolean.TRUE : (k == i ? Boolean.FALSE : null);
            JComponent r = st == null ? dimmed(k, card.options.get(k)) : optionRow(k, card.options.get(k), st);
            optionsPanel.add(r);
            optionsPanel.add(Box.createVerticalStrut(10));
        }
        showFeedback(right, (right ? "**Right.** " : "**Not quite.** This card will come back at the end. ") + card.explain);
        complete(right ? (requeued.contains(card.id) ? 2 : 3) : 0);
    }

    private JComponent dimmed(int k, String text) {
        Theme t = Theme.get();
        Ui.Card row = new Ui.Card(new BorderLayout(14, 0), 12, 12);
        row.colors(() -> Theme.get().bg, () -> Theme.get().border);
        row.add(Ui.label(String.valueOf(k + 1), 0.85f, Font.BOLD, t.border), BorderLayout.WEST);
        row.add(Ui.prose(text, 1.0f, t.muted), BorderLayout.CENTER);
        return row;
    }

    // ------------------------------------------------------------------ code cards

    private void checkCode() {
        if (done || editor == null) return;
        String code = editor.getText();
        tries++;
        Exercise e = card;
        if (!e.answers.isEmpty() && JavaChecker.matchesAny(code, e.answers)) {
            codeSolved();
            return;
        }
        if (e.kind == Exercise.Kind.TYPE) {
            showFeedback(false, typeDiff(code, e.code));
            return;
        }
        primary.setText("Checking…");
        primary.setEnabled(false);
        new SwingWorker<JavaChecker.Result, Void>() {
            @Override
            protected JavaChecker.Result doInBackground() {
                return JavaChecker.compile(code, e.compile, e.since);
            }

            @Override
            protected void done() {
                JavaChecker.Result r;
                try {
                    r = get();
                } catch (Exception ex) {
                    r = null;
                }
                if (card != e || LessonPlayer.this.done) return;
                primary.setText("Check");
                primary.setEnabled(true);
                if (JavaChecker.accepts(e, code, r)) {
                    codeSolved();
                    return;
                }
                StringBuilder sb = new StringBuilder("**Not yet.** ");
                if (r != null && !r.skipped && !r.ok) {
                    sb.append("The compiler says:");
                    for (String err : r.errors.subList(0, Math.min(3, r.errors.size()))) sb.append("\n- `").append(err.replace('`', '\'')).append('`');
                } else if (!e.checks.isEmpty()) {
                    sb.append("It does not do everything the task asks yet.");
                } else {
                    sb.append(r != null && r.ok && !r.skipped ? "It compiles, but it is not the expected fix." : "It does not match the expected code.");
                }
                if (!e.hint.isEmpty()) sb.append("\nTry the hint, or keep going.");
                showFeedback(false, sb.toString());
            }
        }.execute();
    }

    private void codeSolved() {
        int stars = revealed ? 0 : (tries <= 1 && hints == 0 ? 3 : hints == 0 ? 2 : 1);
        showFeedback(true, "**Correct.** " + (card.explain.isEmpty() ? "Nicely done." : card.explain));
        complete(stars);
    }

    private static String typeDiff(String got, String expected) {
        List<String> a = nonBlank(got), b = nonBlank(expected);
        for (int i = 0; i < b.size(); i++) {
            if (i >= a.size()) return "**Keep going.** Next line: `" + b.get(i).trim() + "`";
            if (!JavaChecker.normalize(a.get(i)).equals(JavaChecker.normalize(b.get(i)))) {
                return "**Line " + (i + 1) + " differs.** Expected: `" + b.get(i).trim() + "`";
            }
        }
        return "**Almost.** There are extra lines at the end.";
    }

    private static List<String> nonBlank(String s) {
        List<String> out = new ArrayList<>();
        for (String l : s.split("\n")) if (!l.trim().isEmpty()) out.add(l);
        return out;
    }

    private void toggleVim() {
        boolean v = !app.progress.vimKeys();
        app.progress.setVimKeys(v);
        vimToggle.setText("Vim keys: " + (v ? "on" : "off"));
        if (editor != null && card.kind != Exercise.Kind.VIM) {
            editor.setVim(v);
            editor.requestFocusInWindow();
        }
    }

    // ------------------------------------------------------------------ vim drills

    private void vimChanged() {
        updateVimStatus();
        if (done || card.kind != Exercise.Kind.VIM) return;
        VimEngine eng = editor.engine();
        if (Checks.vimSolved(card, eng)) {
            int k = eng.getKeystrokes(), par = Math.max(1, card.par);
            int stars = revealed ? 0 : k <= par ? 3 : k <= Math.ceil(par * 1.5) ? 2 : 1;
            if (hints > 0 && stars > 2) stars = 2;
            String verdict = stars == 3 ? "**Solved at par.**" : stars == 2 ? "**Solved.** Close to par." : stars == 1 ? "**Solved.** Now try fewer keys next time." : "**Solved with help.** It will come back for practice.";
            showFeedback(true, verdict + " " + k + " keys (par " + par + "). Reference: `" + card.solution + "`" + (card.explain.isEmpty() ? "" : "\n" + card.explain));
            complete(stars);
        }
    }

    private void updateVimStatus() {
        if (editor == null || card.kind != Exercise.Kind.VIM) return;
        List<String> log = editor.engine().getKeyLog();
        StringBuilder sb = new StringBuilder();
        for (int i = Math.max(0, log.size() - 10); i < log.size(); i++) sb.append(log.get(i).equals(" ") ? "␣" : log.get(i)).append(' ');
        editor.setStatusRight(sb.toString().trim() + "   " + editor.engine().getKeystrokes() + "/" + card.par);
    }

    private void resetVim() {
        if (card == null || card.kind != Exercise.Kind.VIM || done || editor == null) return;
        VimEngine eng = new VimEngine(card.start);
        eng.setCursor(card.cursorRow, card.cursorCol);
        editor.setEngine(eng);
        editor.setVim(true);
        updateVimStatus();
        editor.requestFocusInWindow();
    }

    // ------------------------------------------------------------------ hint / reveal

    private void showHint() {
        if (card == null || card.hint.isEmpty() || done) return;
        hints++;
        showFeedback(true, "**Hint.** " + card.hint);
        feedback.colors(() -> Theme.get().accentSoft, () -> null);
        if (editor != null) editor.requestFocusInWindow();
    }

    private void reveal() {
        if (card == null || done) return;
        revealed = true;
        Theme t = Theme.get();
        if (card.kind == Exercise.Kind.VIM) {
            showFeedback(false, "**Solution:** `" + card.solution + "`\nReset (F5) and try it yourself to finish the card.");
        } else {
            String ans = card.answers.isEmpty() ? "" : card.answers.get(0);
            feedback.removeAll();
            feedback.colors(() -> Theme.get().errorSoft, () -> null);
            JPanel col = Ui.clear(new Ui.Column(2000, 10));
            col.add(Ui.prose("**One correct answer.** Study it, then continue. This card will come back later.", 0.95f, t.text));
            col.add(codeView(ans));
            feedback.add(col, BorderLayout.CENTER);
            feedback.setVisible(true);
            page.revalidate();
            page.repaint();
            complete(0);
        }
    }

    // ------------------------------------------------------------------ end

    private void finish() {
        Theme t = Theme.get();
        page.removeAll();
        if (lesson != null) app.progress.setLessonDone(lesson);
        int mins = (int) Math.max(1, (System.currentTimeMillis() - startMs) / 60000);
        page.add(Ui.chip(lesson != null ? "Lesson complete" : "Review complete", t.success, t.successSoft));
        page.add(Ui.prose("**" + (lesson != null ? lesson.title : "Nice work") + "**", 1.5f, t.text));
        String acc = practiced == 0 ? "" : "  ·  " + Math.round(100.0 * firstTryRight / practiced) + "% right first time";
        page.add(Ui.prose(originalSize + " cards  ·  " + mins + " min  ·  +" + xp + " XP" + acc, 1.0f, t.muted));
        if (lesson != null && !lesson.book.isEmpty()) {
            page.add(Ui.prose("Want more depth? Read **" + lesson.book + "** in Core Java, Volume I.", 0.95f, t.muted));
        }
        page.add(Ui.prose("Take a breath. Short breaks help the new knowledge settle.", 0.95f, t.muted));
        hintBtn.setVisible(false);
        answerBtn.setVisible(false);
        resetBtn.setVisible(false);
        vimToggle.setVisible(false);
        counter.setText("");
        bar.setValue(1);
        Lesson nextLesson = lesson == null ? null : app.progress.nextLesson(lesson.unit.subject);
        if (nextLesson != null) {
            primary.setText("Next: " + shorten(nextLesson.title));
            primary.setEnabled(true);
            primary.setAction(() -> app.startLesson(nextLesson));
            page.add(Ui.secondary("Back to " + lesson.unit.subject.name, () -> app.showSubject(lesson.unit.subject)));
        } else {
            primary.setText("Home");
            primary.setEnabled(true);
            primary.setAction(app::showHome);
        }
        page.revalidate();
        page.repaint();
    }

    private static String shorten(String s) {
        return s.length() > 28 ? s.substring(0, 27) + "…" : s;
    }

    /** Skip to the next card (used by the screenshot tool). */
    void advance() {
        next();
    }

    CodeEditor currentEditor() {
        return editor;
    }

    Exercise currentCard() {
        return card;
    }

    @Override
    public String toString() {
        return "LessonPlayer[" + heading + ", " + Arrays.asList(index, queue.size()) + "]";
    }
}
