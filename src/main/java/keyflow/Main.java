package keyflow;

import java.awt.EventQueue;

import javax.swing.JOptionPane;
import javax.swing.UIManager;

import keyflow.course.Catalog;
import keyflow.data.Progress;
import keyflow.ui.App;

/** Entry point: {@code java -jar keyflow.jar}. */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        System.setProperty("sun.java2d.uiScale.enabled", "true");
        System.setProperty("apple.awt.application.name", "Keyflow");
        EventQueue.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
            } catch (Exception ignored) {
                // default look and feel is fine
            }
            try {
                new App(Catalog.load(), Progress.load()).start();
            } catch (RuntimeException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(null, "Keyflow could not start:\n" + e.getMessage(), "Keyflow", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        });
    }
}
