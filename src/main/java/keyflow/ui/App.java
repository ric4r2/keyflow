package keyflow.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;

import keyflow.course.Catalog;
import keyflow.course.Exercise;
import keyflow.course.Lesson;
import keyflow.course.Subject;
import keyflow.data.Progress;

/** Main window: a quiet top bar (home, focus timer, settings) and one screen at a time. */
public final class App {

    public final Catalog catalog;
    public final Progress progress;
    private final JFrame frame;
    private final JPanel root = new JPanel(new BorderLayout());
    private JComponent screen;

    // focus timer state (survives screen changes)
    private Timer timer;
    private int secondsLeft;
    private boolean onBreak, running, timerFresh = true;
    private JLabel timerLabel;
    private Ui.Button timerButton;
    private int cardsThisSprint;

    public App(Catalog catalog, Progress progress) {
        this.catalog = catalog;
        this.progress = progress;
        Theme.set(progress.theme());
        Theme.setBaseSize(progress.fontSize());
        installKeys();
        secondsLeft = progress.focusMinutes() * 60;
        if (java.awt.GraphicsEnvironment.isHeadless()) {   // tests drive the screens without a window
            frame = null;
            return;
        }
        frame = new JFrame("Keyflow");
        frame.setIconImages(AppIcon.images());
        setDockIcon();
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                progress.save();
                frame.dispose();
                System.exit(0);
            }
        });
        frame.setContentPane(root);
        Dimension scr = Toolkit.getDefaultToolkit().getScreenSize();
        frame.setSize(Math.min(1180, scr.width - 80), Math.min(860, scr.height - 80));
        frame.setMinimumSize(new Dimension(760, 560));
        frame.setLocationRelativeTo(null);
    }

    /** macOS dock / Linux taskbars that read the app icon instead of the window's. */
    private static void setDockIcon() {
        try {
            if (java.awt.Taskbar.isTaskbarSupported()) {
                java.awt.Taskbar tb = java.awt.Taskbar.getTaskbar();
                if (tb.isSupported(java.awt.Taskbar.Feature.ICON_IMAGE)) tb.setIconImage(AppIcon.image(512));
            }
        } catch (RuntimeException ignored) {
            // not supported on this desktop: the window icon is enough
        }
    }

    /** Alt+Left goes back anywhere; Esc goes back on list screens (in lessons Esc belongs to Vim). */
    private void installKeys() {
        javax.swing.InputMap im = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        im.put(javax.swing.KeyStroke.getKeyStroke("alt LEFT"), "kf-back");
        im.put(javax.swing.KeyStroke.getKeyStroke("ESCAPE"), "kf-esc");
        root.getActionMap().put("kf-back", action(this::back));
        root.getActionMap().put("kf-esc", action(() -> {
            if (canGoBack() && !(screen instanceof LessonPlayer)) back();
        }));
    }

    private static javax.swing.Action action(Runnable r) {
        return new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                r.run();
            }
        };
    }

    public JFrame frame() {
        return frame;
    }

    public void start() {
        showHome();
        if (frame != null) frame.setVisible(true);
    }

    // ------------------------------------------------------------------ navigation
    //
    // A history stack: the top entry is the screen on screen. Home clears it, every other screen
    // is pushed, and Back pops. Opening a screen that is already in the stack pops back to it,
    // so Subject -> Lesson -> "Back to subject" never builds a loop.

    private static final class Entry {
        final String key, crumb;
        final java.util.function.Function<JComponent, JComponent> factory;
        final boolean keepAlive;   // lesson players keep their place when you come back
        JComponent comp;
        String stamp;

        Entry(String key, String crumb, java.util.function.Function<JComponent, JComponent> factory, boolean keepAlive) {
            this.key = key;
            this.crumb = crumb;
            this.factory = factory;
            this.keepAlive = keepAlive;
        }
    }

    private final java.util.ArrayDeque<Entry> history = new java.util.ArrayDeque<>();

    private enum Nav { ROOT, PUSH, REPLACE }

    private void navigate(Nav how, Entry e) {
        if (how == Nav.ROOT) {
            history.clear();
        } else if (contains(e.key)) {
            while (!history.peek().key.equals(e.key)) history.pop();
            history.pop();
        } else if (how == Nav.REPLACE && !history.isEmpty()) {
            history.pop();
        }
        history.push(e);
        build(e, null);
    }

    private boolean contains(String key) {
        for (Entry e : history) if (e.key.equals(key)) return true;
        return false;
    }

    private void build(Entry e, JComponent previous) {
        e.comp = e.factory.apply(previous);
        e.stamp = stamp();
        display(e);
    }

    /** Settings that change how screens look; a kept screen is rebuilt when they differ. */
    private String stamp() {
        return progress.theme() + "|" + progress.fontSize() + "|" + progress.vimKeys();
    }

    private void display(Entry e) {
        root.removeAll();
        root.setBackground(Theme.get().bg);
        root.add(topBar(), BorderLayout.NORTH);
        root.add(e.comp, BorderLayout.CENTER);
        screen = e.comp;
        root.revalidate();
        root.repaint();
        if (e.comp instanceof LessonPlayer) ((LessonPlayer) e.comp).focusCard();
    }

    /** Go back one screen (top-bar Back, Alt+Left, or Esc outside lessons). */
    public void back() {
        if (history.size() <= 1) {
            showHome();
            return;
        }
        history.pop();
        Entry e = history.peek();
        if (e.keepAlive && e.comp != null) {
            if (stamp().equals(e.stamp)) display(e);
            else build(e, e.comp);          // restyle, keep the lesson position
        } else {
            build(e, null);                 // lists are cheap: rebuild so they show fresh progress
        }
    }

    /** The current screen applied a setting itself (e.g. the lesson's Vim toggle): no rebuild needed for it. */
    void settingsAppliedHere() {
        if (!history.isEmpty()) history.peek().stamp = stamp();
    }

    public boolean canGoBack() {
        return history.size() > 1;
    }

    /** Keys of the screens in the history, bottom first (for tests). */
    java.util.List<String> historyKeys() {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (java.util.Iterator<Entry> it = history.descendingIterator(); it.hasNext(); ) out.add(it.next().key);
        return out;
    }

    public void showHome() {
        navigate(Nav.ROOT, new Entry("home", "", prev -> new HomeScreen(this), false));
    }

    public void showSubject(Subject s) {
        progress.setLastSubject(s.id);
        navigate(Nav.PUSH, new Entry("subject:" + s.id, s.name, prev -> new SubjectScreen(this, s), false));
    }

    /** Leave a finished lesson for its subject map; the finished lesson is not kept in the history. */
    public void leaveLessonFor(Subject s) {
        progress.setLastSubject(s.id);
        navigate(Nav.REPLACE, new Entry("subject:" + s.id, s.name, prev -> new SubjectScreen(this, s), false));
    }

    public void showSettings() {
        navigate(Nav.PUSH, new Entry("settings", "Settings", prev -> new SettingsScreen(this), false));
    }

    public void startLesson(Lesson l) {
        openLesson(l, Nav.PUSH);
    }

    /** From a lesson's finish screen: the next lesson takes its place, so Back still leads to the map. */
    public void startNextLesson(Lesson l) {
        openLesson(l, Nav.REPLACE);
    }

    private void openLesson(Lesson l, Nav how) {
        progress.setLastLesson(l);
        progress.setLastSubject(l.unit.subject.id);
        navigate(how, new Entry("lesson:" + l.id, l.unit.subject.name, prev -> {
            if (prev instanceof LessonPlayer) return new LessonPlayer((LessonPlayer) prev);
            LessonPlayer p = new LessonPlayer(this, l.unit.subject.name + "  ·  " + l.title, LessonPlayer.withWarmUp(this, l), l);
            p.begin();
            return p;
        }, true));
    }

    public void startReview() {
        List<Exercise> due = progress.dueCards(catalog, 15);
        if (due.isEmpty()) {
            showHome();
            return;
        }
        navigate(Nav.PUSH, new Entry("review", "Review", prev -> {
            if (prev instanceof LessonPlayer) return new LessonPlayer((LessonPlayer) prev);
            LessonPlayer p = new LessonPlayer(this, "Review  ·  " + due.size() + " cards", due, null);
            p.begin();
            return p;
        }, true));
    }

    /** Re-create the current screen (after a theme or font change). */
    public void refresh() {
        Theme.set(progress.theme());
        Theme.setBaseSize(progress.fontSize());
        if (timerFresh && !running) secondsLeft = progress.focusMinutes() * 60;
        Entry e = history.peek();
        if (e == null) showHome();
        else build(e, e.comp);
    }

    /** Asks the user to confirm a destructive action; replaceable in tests. */
    java.util.function.Predicate<String> confirm = msg -> javax.swing.JOptionPane.showConfirmDialog(root, msg, "Keyflow",
            javax.swing.JOptionPane.OK_CANCEL_OPTION) == javax.swing.JOptionPane.OK_OPTION;

    public Lesson continueLesson() {
        Subject s = catalog.subject(progress.lastSubject());
        if (s == null || !s.isAvailable()) s = catalog.subject("java");
        String last = progress.lastLesson();
        Lesson l = last == null ? null : catalog.lesson(last);
        if (l != null && !progress.lessonDone(l)) return l;
        Lesson next = progress.nextLesson(s);
        if (next != null) return next;
        for (Subject other : catalog.subjects) {
            if (other.isAvailable() && progress.nextLesson(other) != null) return progress.nextLesson(other);
        }
        return null;
    }

    /** Called by the lesson player after every card: focus sprints count cards. */
    public void cardDone() {
        cardsThisSprint++;
    }

    // ------------------------------------------------------------------ top bar & focus timer

    private JComponent topBar() {
        Theme t = Theme.get();
        JPanel bar = new JPanel();
        bar.setLayout(new BoxLayout(bar, BoxLayout.X_AXIS));
        bar.setBackground(t.bg);
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, t.border),
                BorderFactory.createEmptyBorder(10, 20, 10, 20)));
        JLabel logo = Ui.label("keyflow", 1.05f, Font.BOLD, t.text);
        logo.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        Ui.onClick(logo, () -> showHome());
        if (canGoBack()) {
            bar.add(Ui.ghost("← Back", this::back).size(0.9f));
            bar.add(Box.createHorizontalStrut(10));
        }
        bar.add(logo);
        String crumb = history.isEmpty() ? "" : history.peek().crumb;
        if (!crumb.isEmpty()) {
            bar.add(Box.createHorizontalStrut(14));
            bar.add(Ui.label("/   " + crumb, 0.9f, Font.PLAIN, t.muted));
        }
        bar.add(Box.createHorizontalGlue());
        timerLabel = Ui.label("", 0.88f, Font.PLAIN, t.muted);
        timerLabel.setFont(Theme.mono(0.85f));
        bar.add(timerLabel);
        bar.add(Box.createHorizontalStrut(6));
        timerButton = Ui.ghost("", this::toggleTimer).size(0.85f);
        bar.add(timerButton);
        bar.add(Box.createHorizontalStrut(6));
        if (history.isEmpty() || !history.peek().key.equals("settings")) bar.add(Ui.ghost("Settings", this::showSettings).size(0.85f));
        updateTimerLabel();
        return bar;
    }

    private void toggleTimer() {
        if (timer == null) {
            timer = new Timer(1000, e -> tick());
        }
        running = !running;
        timerFresh = false;
        if (running) timer.start();
        else timer.stop();
        updateTimerLabel();
    }

    private void tick() {
        secondsLeft--;
        if (secondsLeft <= 0) {
            running = false;
            timer.stop();
            Toolkit.getDefaultToolkit().beep();
            if (!onBreak) {
                onBreak = true;
                secondsLeft = progress.breakMinutes() * 60;
                timerLabel.setText("Sprint done: " + cardsThisSprint + " cards. Break time  ");
                timerButton.setText("Start break");
                cardsThisSprint = 0;
                return;
            } else {
                onBreak = false;
                secondsLeft = progress.focusMinutes() * 60;
                timerLabel.setText("Break over. Ready?  ");
                timerButton.setText("Start focus");
                return;
            }
        }
        updateTimerLabel();
    }

    private void updateTimerLabel() {
        if (timerLabel == null) return;
        int m = secondsLeft / 60, s = secondsLeft % 60;
        String time = String.format("%02d:%02d", m, s);
        if (running) {
            timerLabel.setText((onBreak ? "break " : "focus ") + time + "  ");
            timerButton.setText("Pause");
        } else {
            timerLabel.setText(time + "  ");
            timerButton.setText(onBreak ? "Start break" : "Start focus");
        }
    }

    JComponent rootPane() {
        return root;
    }

    public JComponent currentScreen() {
        return screen;
    }
}
