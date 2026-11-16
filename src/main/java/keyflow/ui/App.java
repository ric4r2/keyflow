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
    private Runnable rebuild = this::showHome;

    // focus timer state (survives screen changes)
    private Timer timer;
    private int secondsLeft;
    private boolean onBreak, running;
    private JLabel timerLabel;
    private Ui.Button timerButton;
    private int cardsThisSprint;

    public App(Catalog catalog, Progress progress) {
        this.catalog = catalog;
        this.progress = progress;
        Theme.set(progress.theme());
        Theme.setBaseSize(progress.fontSize());
        frame = new JFrame("Keyflow");
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
        secondsLeft = progress.focusMinutes() * 60;
    }

    public JFrame frame() {
        return frame;
    }

    public void start() {
        showHome();
        frame.setVisible(true);
    }

    // ------------------------------------------------------------------ navigation

    private void show(JComponent content, String crumb, boolean showBack, Runnable rebuilder) {
        rebuild = rebuilder;
        root.removeAll();
        root.setBackground(Theme.get().bg);
        root.add(topBar(crumb, showBack), BorderLayout.NORTH);
        root.add(content, BorderLayout.CENTER);
        screen = content;
        root.revalidate();
        root.repaint();
    }

    public void showHome() {
        show(new HomeScreen(this), "", false, this::showHome);
    }

    public void showSubject(Subject s) {
        progress.setLastSubject(s.id);
        show(new SubjectScreen(this, s), s.name, true, () -> showSubject(s));
    }

    public void showSettings() {
        show(new SettingsScreen(this), "Settings", true, this::showSettings);
    }

    public void startLesson(Lesson l) {
        progress.setLastLesson(l);
        progress.setLastSubject(l.unit.subject.id);
        LessonPlayer p = new LessonPlayer(this, l.unit.subject.name + "  ·  " + l.title, LessonPlayer.withWarmUp(this, l), l);
        show(p, l.unit.subject.name, true, () -> startLesson(l));
        p.begin();
    }

    public void startReview() {
        List<Exercise> due = progress.dueCards(catalog, 15);
        if (due.isEmpty()) {
            showHome();
            return;
        }
        LessonPlayer p = new LessonPlayer(this, "Review  ·  " + due.size() + " cards", due, null);
        show(p, "Review", true, this::startReview);
        p.begin();
    }

    /** Re-create the current screen (after a theme or font change). */
    public void refresh() {
        Theme.set(progress.theme());
        Theme.setBaseSize(progress.fontSize());
        rebuild.run();
    }

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

    private JComponent topBar(String crumb, boolean showBack) {
        Theme t = Theme.get();
        JPanel bar = new JPanel();
        bar.setLayout(new BoxLayout(bar, BoxLayout.X_AXIS));
        bar.setBackground(t.bg);
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, t.border),
                BorderFactory.createEmptyBorder(10, 20, 10, 20)));
        JLabel logo = Ui.label("keyflow", 1.05f, Font.BOLD, t.text);
        logo.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        logo.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                showHome();
            }
        });
        bar.add(logo);
        if (showBack && !crumb.isEmpty()) {
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
        bar.add(Ui.ghost("Settings", this::showSettings).size(0.85f));
        updateTimerLabel();
        return bar;
    }

    private void toggleTimer() {
        if (timer == null) {
            timer = new Timer(1000, e -> tick());
        }
        running = !running;
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

    public JComponent currentScreen() {
        return screen;
    }
}
