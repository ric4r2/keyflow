package keyflow.ui;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;

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

/**
 * The map of a subject, drawn as a path: a progress rail on the left that turns green as you go,
 * a checkpoint circle per lesson and a milestone square per unit. Seeing the green line grow is
 * the point: visible progress is motivating.
 */
final class SubjectScreen extends JPanel {

    static final int RAIL_W = 46;

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
        page.add(summary(app, s, next));

        // the path: one column with no gaps, so the rail is one continuous line
        JPanel path = Ui.clear(new Ui.Column(820, 0));
        JComponent nextRow = null;
        boolean prevDone = true;   // the line starts "lit": you are standing at the start
        for (Unit u : s.units) {
            int done = 0;
            for (Lesson l : u.lessons) if (app.progress.lessonDone(l)) done++;
            path.add(unitRow(u, done, prevDone));
            for (Lesson l : u.lessons) {
                boolean isDone = app.progress.lessonDone(l);
                JComponent r = lessonRow(app, l, isDone, l == next, prevDone);
                if (l == next) nextRow = r;
                path.add(r);
                prevDone = isDone;
            }
        }
        page.add(path);
        add(Ui.scroll(page), BorderLayout.CENTER);
        if (nextRow != null && next.unit != s.units.get(0)) {
            // open the map where you are, with a little of the finished path above it
            final JComponent target = nextRow;
            page.addComponentListener(new java.awt.event.ComponentAdapter() {
                @Override
                public void componentResized(java.awt.event.ComponentEvent e) {
                    java.awt.Rectangle view = page.getVisibleRect();
                    if (view.height <= 0 || target.getHeight() <= 0) return;
                    page.removeComponentListener(this);   // only on first layout: never fight the user's scrolling
                    java.awt.Rectangle r = javax.swing.SwingUtilities.convertRectangle(target.getParent(), target.getBounds(), page);
                    page.scrollRectToVisible(new java.awt.Rectangle(0, Math.max(0, r.y - view.height / 3), 1, view.height));
                }
            });
        }
    }

    // ------------------------------------------------------------------ summary

    private static JComponent summary(App app, Subject s, Lesson next) {
        Theme t = Theme.get();
        int total = s.allLessons().size(), done = app.progress.lessonsDone(s);
        int pct = percent(done, total);
        int unitsDone = 0;
        for (Unit u : s.units) {
            boolean all = !u.lessons.isEmpty();
            for (Lesson l : u.lessons) all &= app.progress.lessonDone(l);
            if (all) unitsDone++;
        }
        Ui.Card c = new Ui.Card(new BorderLayout(22, 8), 20);
        JLabel big = Ui.label(pct + "%", 2.3f, Font.BOLD, t.success);
        c.add(big, BorderLayout.WEST);
        JPanel right = Ui.clear(null);
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        right.add(Ui.label(done + " of " + total + " lessons  ·  " + unitsDone + " of " + s.units.size() + " units complete", 1.0f, Font.BOLD, t.text));
        right.add(Box.createVerticalStrut(10));
        Ui.Bar bar = new Ui.Bar(10).color(() -> Theme.get().success);
        bar.setValue(total == 0 ? 0 : done / (double) total);
        bar.setAlignmentX(LEFT_ALIGNMENT);
        right.add(bar);
        right.add(Box.createVerticalStrut(10));
        right.add(Ui.muted(encouragement(app, s, next, done)));
        c.add(right, BorderLayout.CENTER);
        return c;
    }

    private static String encouragement(App app, Subject s, Lesson next, int done) {
        if (next == null) return "Every checkpoint reached. Review keeps it fresh.";
        int left = 0;
        for (Lesson l : next.unit.lessons) if (!app.progress.lessonDone(l)) left++;
        String milestone = left == 1 ? "1 lesson to the next milestone" : left + " lessons to the next milestone";
        if (done == 0) return "Every expert started at lesson one. " + milestone + ".";
        return milestone + ": " + next.unit.title + ".";
    }

    static int percent(int done, int total) {
        return total == 0 ? 0 : (int) Math.floor(100.0 * done / total);
    }

    // ------------------------------------------------------------------ rows

    private static JComponent unitRow(Unit u, int done, boolean lineLit) {
        Theme t = Theme.get();
        int total = u.lessons.size();
        boolean complete = total > 0 && done == total;
        JPanel row = Ui.clear(new BorderLayout(14, 0));
        row.add(new Rail(Rail.Node.MILESTONE, lineLit, complete, done > 0, complete), BorderLayout.WEST);
        JPanel mid = Ui.clear(null);
        mid.setLayout(new BoxLayout(mid, BoxLayout.Y_AXIS));
        mid.setBorder(BorderFactory.createEmptyBorder(22, 0, 8, 0));
        JPanel head = Ui.clear(new BorderLayout(10, 0));
        head.add(Ui.label(u.title, 1.1f, Font.BOLD, t.text), BorderLayout.CENTER);
        String chip = complete ? "Complete" : percent(done, total) + "%  ·  " + done + "/" + total;
        head.add(Ui.chip(chip, done > 0 ? t.success : t.muted, done > 0 ? t.successSoft : t.surface2), BorderLayout.EAST);
        head.setAlignmentX(LEFT_ALIGNMENT);
        mid.add(head);
        if (!u.description.isEmpty()) {
            JComponent d = Ui.prose(u.description + (u.book.isEmpty() ? "" : "   (" + u.book + ")"), 0.85f, t.muted);
            d.setAlignmentX(LEFT_ALIGNMENT);
            mid.add(Box.createVerticalStrut(4));
            mid.add(d);
        }
        row.add(mid, BorderLayout.CENTER);
        return row;
    }

    private static JComponent lessonRow(App app, Lesson l, boolean done, boolean isNext, boolean lineLit) {
        Theme t = Theme.get();
        JPanel row = Ui.clear(new BorderLayout(14, 0));
        row.add(new Rail(isNext ? Rail.Node.NEXT : Rail.Node.LESSON, lineLit, done, false, done), BorderLayout.WEST);

        JPanel pad = Ui.clear(new BorderLayout());
        pad.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
        Ui.Card c = new Ui.Card(new BorderLayout(14, 0), 12, 12);
        if (isNext) c.colors(() -> Theme.get().surface, () -> Theme.get().accent);
        else if (done) c.colors(() -> Theme.get().bg, () -> Theme.get().border);
        JPanel mid = Ui.clear(null);
        mid.setLayout(new BoxLayout(mid, BoxLayout.Y_AXIS));
        if (isNext) {
            mid.add(Ui.label("UP NEXT", 0.68f, Font.BOLD, t.accent));
            mid.add(Box.createVerticalStrut(3));
        }
        mid.add(Ui.label(l.title, 0.98f, done ? Font.PLAIN : Font.BOLD, done ? t.muted : t.text));
        int practice = 0;
        for (Exercise e : l.exercises) if (e.kind.isPractice()) practice++;
        String meta = l.exercises.size() + " cards  ·  " + practice + " practice" + (l.book.isEmpty() ? "" : "  ·  " + l.book);
        mid.add(Box.createVerticalStrut(2));
        mid.add(Ui.muted(meta));
        if (!l.keys.isEmpty() && !"everything".equals(l.keys.trim())) {
            // the keys a lesson teaches, visible on the map: easier to remember what each step was about
            JLabel keys = Ui.label("Keys:  " + l.keys.trim().replaceAll("\\s+", "   "), 0.82f, Font.PLAIN, done ? t.muted : t.accent);
            keys.setFont(Theme.mono(0.8f));
            mid.add(Box.createVerticalStrut(4));
            mid.add(keys);
        }
        c.add(mid, BorderLayout.CENTER);
        if (done) c.add(Ui.label("Redo", 0.8f, Font.PLAIN, t.muted), BorderLayout.EAST);
        pad.add(c, BorderLayout.CENTER);
        row.add(pad, BorderLayout.CENTER);
        Ui.onClick(row, () -> app.startLesson(l));
        return row;
    }

    // ------------------------------------------------------------------ the rail

    /** Paints one segment of the progress line and its checkpoint. */
    static final class Rail extends JComponent {
        enum Node { LESSON, NEXT, MILESTONE }

        final Node node;
        final boolean topLit, bottomLit, partial, complete;

        Rail(Node node, boolean topLit, boolean bottomLit, boolean partial, boolean complete) {
            this.node = node;
            this.topLit = topLit;
            this.bottomLit = bottomLit;
            this.partial = partial;
            this.complete = complete;
            setOpaque(false);
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(RAIL_W, 40);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            Ui.hints(g);
            Theme t = Theme.get();
            int cx = RAIL_W / 2, h = getHeight();
            int cy = node == Node.MILESTONE ? Math.min(h / 2, 34) : h / 2;
            g.setStroke(new BasicStroke(4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
            g.setColor(topLit ? t.success : t.border);
            g.drawLine(cx, 0, cx, cy);
            g.setColor(bottomLit ? t.success : t.border);
            g.drawLine(cx, cy, cx, h);

            if (node == Node.MILESTONE) {
                int s = 28;
                int x = cx - s / 2, y = cy - s / 2;
                g.setColor(complete ? t.success : partial ? t.successSoft : t.bg);
                g.fillRoundRect(x, y, s, s, 9, 9);
                g.setStroke(new BasicStroke(2.5f));
                g.setColor(complete || partial ? t.success : t.border);
                g.drawRoundRect(x, y, s, s, 9, 9);
                if (complete) check(g, cx, cy, 7, t.onAccent);
                else star(g, cx, cy, partial ? t.success : t.muted);
            } else if (node == Node.NEXT) {
                int r = 12;
                g.setColor(t.bg);
                g.fillOval(cx - r, cy - r, 2 * r, 2 * r);
                g.setStroke(new BasicStroke(3f));
                g.setColor(t.accent);
                g.drawOval(cx - r, cy - r, 2 * r, 2 * r);
                g.fillOval(cx - 5, cy - 5, 10, 10);
            } else {
                int r = 11;
                if (complete) {
                    g.setColor(t.success);
                    g.fillOval(cx - r, cy - r, 2 * r, 2 * r);
                    check(g, cx, cy, 5, t.onAccent);
                } else {
                    g.setColor(t.bg);
                    g.fillOval(cx - r, cy - r, 2 * r, 2 * r);
                    g.setStroke(new BasicStroke(2.5f));
                    g.setColor(t.border);
                    g.drawOval(cx - r, cy - r, 2 * r, 2 * r);
                }
            }
            g.dispose();
        }

        private static void check(Graphics2D g, int cx, int cy, int s, Color c) {
            g.setColor(c);
            g.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawPolyline(new int[]{cx - s, cx - s / 3, cx + s}, new int[]{cy, cy + s * 2 / 3, cy - s * 2 / 3}, 3);
        }

        /** A small flag-like mark for milestones that are not complete yet. */
        private static void star(Graphics2D g, int cx, int cy, Color c) {
            g.setColor(c);
            g.setFont(Theme.ui(0.7f, Font.BOLD));
            FontMetrics fm = g.getFontMetrics();
            String s = "⚑";
            if (g.getFont().canDisplay('⚑')) {
                g.drawString(s, cx - fm.stringWidth(s) / 2, cy + fm.getAscent() / 2 - 1);
            } else {
                g.fillRect(cx - 4, cy - 4, 8, 8);
            }
        }
    }
}
