package keyflow.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.function.IntConsumer;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import keyflow.data.Progress;

/** A few settings, each one a clear row. */
final class SettingsScreen extends JPanel {

    SettingsScreen(App app) {
        super(new BorderLayout());
        Theme t = Theme.get();
        setBackground(t.bg);
        Progress pr = app.progress;
        Ui.Page page = new Ui.Page(new Ui.Column(720, 14));
        page.setBorder(BorderFactory.createEmptyBorder(32, 28, 48, 28));
        page.add(Ui.title("Settings"));

        page.add(row("Vim keys in code exercises",
                "On: edit code with Vim (great practice). Off: a plain editor, to focus only on Java.",
                toggle(pr.vimKeys(), v -> { pr.setVimKeys(v); app.refresh(); })));
        page.add(row("Theme", "Light (warm paper) reads best; dark has less glare at night.",
                choice(new String[]{"Light", "Dark"}, "dark".equals(pr.theme()) ? 1 : 0,
                        i -> { pr.setTheme(i == 1 ? "dark" : "light"); app.refresh(); })));
        page.add(row("Text size", pr.fontSize() + " px",
                stepper(() -> { pr.setFontSize(pr.fontSize() - 1); app.refresh(); }, () -> { pr.setFontSize(pr.fontSize() + 1); app.refresh(); })));
        page.add(row("Focus sprint", pr.focusMinutes() + " minutes, then a " + pr.breakMinutes() + " minute break. Short sprints help keep attention.",
                stepper(() -> { pr.setFocusMinutes(pr.focusMinutes() - 5); app.refresh(); }, () -> { pr.setFocusMinutes(pr.focusMinutes() + 5); app.refresh(); })));
        page.add(row("Break length", pr.breakMinutes() + " minutes",
                stepper(() -> { pr.setBreakMinutes(pr.breakMinutes() - 1); app.refresh(); }, () -> { pr.setBreakMinutes(pr.breakMinutes() + 1); app.refresh(); })));
        page.add(row("Daily goal", pr.dailyGoal() + " XP. Small goals are easier to start.",
                stepper(() -> { pr.setDailyGoal(pr.dailyGoal() - 10); app.refresh(); }, () -> { pr.setDailyGoal(pr.dailyGoal() + 10); app.refresh(); })));
        page.add(row("Progress file", Progress.dataDir().resolve("progress.properties").toString(),
                Ui.secondary("Reset progress", () -> {
                    if (app.confirm.test("Erase all progress? This cannot be undone.")) {
                        pr.resetAll();
                        app.refresh();
                    }
                })));
        add(Ui.scroll(page), BorderLayout.CENTER);
    }

    private static JComponent row(String title, String desc, JComponent control) {
        Theme t = Theme.get();
        Ui.Card c = new Ui.Card(new BorderLayout(18, 0), 16);
        JPanel left = Ui.clear(null);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        JLabel l = Ui.label(title, 1.0f, Font.BOLD, t.text);
        left.add(l);
        JLabel d = Ui.muted("<html><div style='width:360px'>" + desc + "</div></html>");
        left.add(d);
        c.add(left, BorderLayout.CENTER);
        JPanel right = Ui.clear(new FlowLayout(FlowLayout.RIGHT, 6, 6));
        right.add(control);
        c.add(right, BorderLayout.EAST);
        return c;
    }

    private static JComponent toggle(boolean on, java.util.function.Consumer<Boolean> set) {
        return choice(new String[]{"Off", "On"}, on ? 1 : 0, i -> set.accept(i == 1));
    }

    private static JComponent choice(String[] opts, int selected, IntConsumer pick) {
        JPanel p = Ui.clear(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        for (int i = 0; i < opts.length; i++) {
            final int idx = i;
            p.add(i == selected ? Ui.primary(opts[i], () -> { }) : Ui.secondary(opts[i], () -> pick.accept(idx)));
        }
        return p;
    }

    private static JComponent stepper(Runnable minus, Runnable plus) {
        JPanel p = Ui.clear(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        p.add(Ui.secondary("−", minus));
        p.add(Ui.secondary("+", plus));
        return p;
    }
}
