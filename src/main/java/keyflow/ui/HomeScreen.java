package keyflow.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalTime;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import keyflow.course.LearningPath;
import keyflow.course.Lesson;
import keyflow.course.Subject;
import keyflow.data.Progress;

/** Start screen. One obvious next step, everything else is secondary. */
final class HomeScreen extends JPanel {

    HomeScreen(App app) {
        super(new BorderLayout());
        Theme t = Theme.get();
        setBackground(t.bg);
        Progress pr = app.progress;

        Ui.Page page = new Ui.Page(new Ui.Column(820, 22));
        page.setBorder(BorderFactory.createEmptyBorder(36, 28, 48, 28));

        int hour = LocalTime.now().getHour();
        String hello = hour < 12 ? "Good morning" : hour < 18 ? "Good afternoon" : "Good evening";
        page.add(Ui.title(hello));
        page.add(Ui.muted(statsLine(pr)));

        // --- continue card
        Lesson next = app.continueLesson();
        Ui.Card cont = new Ui.Card(new BorderLayout(18, 0), 22);
        JPanel txt = Ui.clear(null);
        txt.setLayout(new BoxLayout(txt, BoxLayout.Y_AXIS));
        if (next != null) {
            txt.add(Ui.label("CONTINUE", 0.72f, Font.BOLD, t.accent));
            txt.add(Box.createVerticalStrut(6));
            txt.add(Ui.label(next.title, 1.2f, Font.BOLD, t.text));
            txt.add(Box.createVerticalStrut(4));
            txt.add(Ui.muted(next.unit.subject.name + "  ·  " + next.unit.title + "  ·  " + next.exercises.size() + " cards"));
            cont.add(txt, BorderLayout.CENTER);
            JPanel btn = Ui.clear(new FlowLayout(FlowLayout.RIGHT, 0, 6));
            btn.add(Ui.primary("Start", () -> app.startLesson(next)).size(1.05f));
            cont.add(btn, BorderLayout.EAST);
        } else {
            txt.add(Ui.label("Everything is done. Impressive!", 1.2f, Font.BOLD, t.text));
            cont.add(txt, BorderLayout.CENTER);
        }
        page.add(cont);

        // --- review + daily goal
        JPanel row = Ui.clear(new GridLayout(1, 2, 16, 0));
        int due = pr.dueCount(app.catalog);
        Ui.Card review = new Ui.Card(new BorderLayout(10, 8), 18);
        review.add(Ui.label("Review", 1.0f, Font.BOLD, t.text), BorderLayout.NORTH);
        review.add(Ui.muted(due == 0 ? "Nothing due. Spaced repetition will bring cards back." : due + " cards are ready to refresh your memory."), BorderLayout.CENTER);
        if (due > 0) {
            JPanel b = Ui.clear(new FlowLayout(FlowLayout.LEFT, 0, 0));
            b.add(Ui.secondary("Review " + Math.min(due, 15), app::startReview));
            review.add(b, BorderLayout.SOUTH);
        }
        row.add(review);
        Ui.Card goal = new Ui.Card(new BorderLayout(10, 10), 18);
        goal.add(Ui.label("Today", 1.0f, Font.BOLD, t.text), BorderLayout.NORTH);
        Ui.Bar bar = new Ui.Bar(8).color(() -> Theme.get().success);
        bar.setValue(pr.todayXp() / (double) pr.dailyGoal());
        goal.add(bar, BorderLayout.CENTER);
        goal.add(Ui.muted(pr.todayXp() + " / " + pr.dailyGoal() + " XP  ·  streak " + pr.streak() + (pr.streak() == 1 ? " day" : " days")), BorderLayout.SOUTH);
        row.add(goal);
        page.add(row);

        // --- subjects
        page.add(section("Subjects"));
        JPanel grid = Ui.clear(new GridLayout(0, 3, 14, 14));
        for (Subject s : app.catalog.subjects) grid.add(subjectTile(app, s));
        page.add(grid);

        // --- paths
        page.add(section("Paths"));
        for (LearningPath p : app.catalog.paths) page.add(pathRow(app, p));

        add(Ui.scroll(page), BorderLayout.CENTER);
    }

    private static String statsLine(Progress pr) {
        return "Level " + pr.level() + "  ·  " + pr.xp() + " XP  ·  " + pr.cardsMastered() + " cards mastered";
    }

    private static JComponent section(String name) {
        JLabel l = Ui.label(name.toUpperCase(), 0.75f, Font.BOLD, Theme.get().muted);
        l.setBorder(BorderFactory.createEmptyBorder(14, 2, 0, 0));
        return l;
    }

    private static JComponent subjectTile(App app, Subject s) {
        Theme t = Theme.get();
        boolean ok = s.isAvailable();
        Ui.Card c = new Ui.Card(new BorderLayout(6, 6), 16);
        if (!ok) c.colors(() -> Theme.get().bg, () -> Theme.get().border);
        JPanel top = Ui.clear(new BorderLayout());
        top.add(Ui.label(s.name, 1.0f, Font.BOLD, ok ? t.text : t.muted), BorderLayout.WEST);
        top.add(Ui.chip(s.category, ok ? t.accent : t.muted, ok ? t.accentSoft : t.surface2), BorderLayout.EAST);
        c.add(top, BorderLayout.NORTH);
        if (ok) {
            int done = app.progress.lessonsDone(s), total = s.allLessons().size();
            Ui.Bar bar = new Ui.Bar(6);
            bar.setValue(total == 0 ? 0 : done / (double) total);
            c.add(bar, BorderLayout.CENTER);
            c.add(Ui.muted(done + " / " + total + " lessons"), BorderLayout.SOUTH);
            c.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            c.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    app.showSubject(s);
                }
            });
        } else {
            c.add(Ui.muted("Coming soon"), BorderLayout.SOUTH);
        }
        return c;
    }

    private static JComponent pathRow(App app, LearningPath p) {
        Theme t = Theme.get();
        Ui.Card c = new Ui.Card(new BorderLayout(14, 6), 14);
        JPanel left = Ui.clear(null);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.add(Ui.label(p.name, 0.98f, Font.BOLD, t.text));
        left.add(Box.createVerticalStrut(3));
        left.add(Ui.muted(p.description));
        c.add(left, BorderLayout.WEST);
        JPanel steps = Ui.clear(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        for (String id : p.steps) {
            Subject s = app.catalog.subject(id);
            if (s == null) continue;
            boolean ok = s.isAvailable();
            Color fg = ok ? t.accent : t.muted, bg = ok ? t.accentSoft : t.surface2;
            JLabel chip = Ui.chip(s.name, fg, bg);
            if (ok) {
                chip.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                chip.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        app.showSubject(s);
                    }
                });
            }
            steps.add(chip);
        }
        c.add(steps, BorderLayout.EAST);
        return c;
    }
}
