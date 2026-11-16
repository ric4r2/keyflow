package keyflow.ui;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import keyflow.course.Exercise;
import keyflow.course.Lesson;
import keyflow.course.Subject;
import keyflow.course.Unit;

/** The map of a subject: chapters and their micro-lessons. */
final class SubjectScreen extends JPanel {

    SubjectScreen(App app, Subject s) {
        super(new BorderLayout());
        Theme t = Theme.get();
        setBackground(t.bg);
        Ui.Page page = new Ui.Page(new Ui.Column(820, 14));
        page.setBorder(BorderFactory.createEmptyBorder(32, 28, 48, 28));
        page.add(Ui.title(s.name));
        page.add(Ui.prose(s.description, 1.0f, t.muted));
        if (!s.book.isEmpty()) page.add(Ui.prose("**Companion book.** " + s.book, 0.85f, t.muted));

        Lesson next = app.progress.nextLesson(s);
        for (Unit u : s.units) {
            JLabel h = Ui.label(u.title, 1.1f, Font.BOLD, t.text);
            h.setBorder(BorderFactory.createEmptyBorder(18, 2, 0, 0));
            page.add(h);
            if (!u.description.isEmpty()) page.add(Ui.prose(u.description + (u.book.isEmpty() ? "" : "   (" + u.book + ")"), 0.85f, t.muted));
            for (Lesson l : u.lessons) page.add(lessonRow(app, l, l == next));
        }
        add(Ui.scroll(page), BorderLayout.CENTER);
    }

    private static JComponent lessonRow(App app, Lesson l, boolean isNext) {
        Theme t = Theme.get();
        boolean done = app.progress.lessonDone(l);
        Ui.Card c = new Ui.Card(new BorderLayout(14, 0), 12, 12);
        if (isNext) c.colors(() -> Theme.get().surface, () -> Theme.get().accent);
        JLabel mark = Ui.label(done ? "✓" : isNext ? "▶" : "○", 1.0f, Font.BOLD, done ? t.success : isNext ? t.accent : t.border);
        c.add(mark, BorderLayout.WEST);
        JPanel mid = Ui.clear(null);
        mid.setLayout(new BoxLayout(mid, BoxLayout.Y_AXIS));
        mid.add(Ui.label(l.title, 0.98f, done ? Font.PLAIN : Font.BOLD, done ? t.muted : t.text));
        int practice = 0;
        for (Exercise e : l.exercises) if (e.kind.isPractice()) practice++;
        String meta = l.exercises.size() + " cards  ·  " + practice + " practice" + (l.book.isEmpty() ? "" : "  ·  " + l.book);
        mid.add(Box.createVerticalStrut(2));
        mid.add(Ui.muted(meta));
        c.add(mid, BorderLayout.CENTER);
        c.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        Ui.onClick(c, () -> app.startLesson(l));
        return c;
    }
}
