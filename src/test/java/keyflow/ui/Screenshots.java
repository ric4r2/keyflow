package keyflow.ui;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.InvocationTargetException;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

import keyflow.course.Catalog;
import keyflow.course.Exercise;
import keyflow.course.Lesson;
import keyflow.data.Progress;

/** Drives the real UI and saves README screenshots to docs/images. Run: java -cp build/test-classes keyflow.ui.Screenshots */
public final class Screenshots {

    private static App app;

    public static void main(String[] args) throws Exception {
        System.setProperty("keyflow.home", new File("build/screenshot-home").getAbsolutePath());
        System.setProperty("awt.useSystemAAFontSettings", "on");
        File out = new File(args.length > 0 ? args[0] : "docs/images");
        out.mkdirs();
        Catalog catalog = Catalog.load();
        Progress pr = Progress.memory();
        // a learner a few days in
        int n = 0;
        for (Lesson l : catalog.subject("java").allLessons()) {
            if (n++ >= 9) break;
            pr.setLessonDone(l);
            for (Exercise e : l.exercises) if (e.kind.isPractice()) pr.record(e, 3);
        }
        int v = 0;
        for (Lesson l : catalog.subject("vim").allLessons()) {
            if (v++ >= 5) break;
            pr.setLessonDone(l);
        }
        pr.setLastLesson(catalog.lesson("J3.6"));
        on(() -> {
            app = new App(catalog, pr);
            app.frame().setSize(1180, 820);
            app.start();
        });
        pause();
        shot(out, "home.png");

        on(() -> app.showSubject(catalog.subject("java")));
        shot(out, "subject.png");

        // a READ card with code
        on(() -> app.startLesson(catalog.lesson("J3.6")));
        shot(out, "learn-card.png");

        // a quiz answered correctly
        on(() -> player().advance());
        on(() -> player().pickOption(catalog.lesson("J3.6").exercises.get(1).correct));
        shot(out, "quiz.png");

        // a fix-it card in the Vim editor
        on(() -> {
            app.startLesson(catalog.lesson("J3.6"));
            LessonPlayer p = player();
            while (p.currentCard() != null && p.currentCard().kind != Exercise.Kind.FIX) p.advance();
            CodeEditor ed = p.currentEditor();
            ed.engine().setCursor(1, 0);
            for (String k : keyflow.vim.VimEngine.tokenize("f=")) ed.engine().key(k);
            ed.repaint();
        });
        shot(out, "fix-it.png");

        // a vim drill, half way
        on(() -> {
            app.startLesson(catalog.lesson("V9.3"));
            LessonPlayer p = player();
            p.advance();
            CodeEditor ed = p.currentEditor();
            for (String k : keyflow.vim.VimEngine.tokenize("ci(")) ed.engine().key(k);
            ed.repaint();
        });
        shot(out, "vim-drill.png");

        // dark theme
        on(() -> {
            pr.setTheme("dark");
            app.refresh();
            app.startLesson(catalog.lesson("V10.2"));
            LessonPlayer p = player();
            p.advance();
            CodeEditor ed = p.currentEditor();
            for (String k : keyflow.vim.VimEngine.tokenize("*N")) ed.engine().key(k);
            ed.repaint();
        });
        shot(out, "dark-vim.png");
        on(() -> app.showHome());
        shot(out, "dark-home.png");
        ImageIO.write(AppIcon.image(256), "png", new File(out, "icon.png"));
        System.out.println("  icon.png");
        System.out.println("Screenshots written to " + out.getAbsolutePath());
        System.exit(0);
    }

    private static LessonPlayer player() {
        return (LessonPlayer) app.currentScreen();
    }

    private static void on(Runnable r) throws InvocationTargetException, InterruptedException {
        SwingUtilities.invokeAndWait(r);
    }

    private static void pause() throws InterruptedException {
        Thread.sleep(500);
    }

    private static void shot(File dir, String name) throws Exception {
        pause();
        final BufferedImage[] img = new BufferedImage[1];
        on(() -> {
            JComponent c = (JComponent) app.frame().getContentPane();
            c.revalidate();
            c.validate();
            // Paint at the screen's scale: text is laid out with the screen's font metrics, and
            // painting it at another scale squeezes spaces (e.g. before inline code).
            java.awt.geom.AffineTransform tx = app.frame().getGraphicsConfiguration().getDefaultTransform();
            double sx = tx.getScaleX(), sy = tx.getScaleY();
            BufferedImage bi = new BufferedImage((int) Math.ceil(c.getWidth() * sx), (int) Math.ceil(c.getHeight() * sy), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = bi.createGraphics();
            g.scale(sx, sy);
            c.paint(g);
            g.dispose();
            img[0] = bi;
        });
        ImageIO.write(img[0], "png", new File(dir, name));
        System.out.println("  " + name);
    }
}
