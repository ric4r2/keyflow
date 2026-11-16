package keyflow.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Supplier;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingConstants;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

/** Small, flat, theme-aware Swing building blocks. */
public final class Ui {

    private Ui() {
    }

    public static void hints(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    // ------------------------------------------------------------------ labels

    public static JLabel label(String text, float rel, int style, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.ui(rel, style));
        l.setForeground(color);
        return l;
    }

    public static JLabel title(String text) {
        return label(text, 1.45f, Font.BOLD, Theme.get().text);
    }

    public static JLabel muted(String text) {
        return label(text, 0.85f, Font.PLAIN, Theme.get().muted);
    }

    /** A transparent panel. */
    public static JPanel clear(LayoutManager lm) {
        JPanel p = new JPanel(lm);
        p.setOpaque(false);
        return p;
    }

    public static JScrollPane scroll(JComponent content) {
        JScrollPane sp = new JScrollPane(content);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.getViewport().setBackground(Theme.get().bg);
        sp.setBackground(Theme.get().bg);
        sp.getVerticalScrollBar().setUnitIncrement(24);
        sp.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        sp.getVerticalScrollBar().setPreferredSize(new Dimension(10, 0));
        sp.getVerticalScrollBar().setUI(new MinimalScrollBarUI());
        return sp;
    }

    // ------------------------------------------------------------------ buttons

    /** Flat button. Kinds: primary (accent fill), secondary (outline), ghost (text only). */
    public static final class Button extends JComponent {
        public enum Kind { PRIMARY, SECONDARY, GHOST }

        private String text;
        private Kind kind;
        private Runnable action;
        private boolean hover, enabled = true;
        private float rel = 0.95f;

        public Button(String text, Kind kind, Runnable action) {
            this.text = text;
            this.kind = kind;
            this.action = action;
            setFocusable(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }

                private boolean down;

                @Override
                public void mousePressed(MouseEvent e) {
                    down = SwingUtilities.isLeftMouseButton(e);
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    // a press and release on the button counts, even if the mouse moved a little in between
                    boolean hit = down && contains(e.getPoint());
                    down = false;
                    if (hit) click();
                }
            });
        }

        public Button size(float rel) {
            this.rel = rel;
            return this;
        }

        public void setText(String t) {
            text = t;
            revalidate();
            repaint();
        }

        public String getText() {
            return text;
        }

        public void setKind(Kind k) {
            kind = k;
            repaint();
        }

        public void setAction(Runnable a) {
            action = a;
        }

        public void click() {
            if (enabled && action != null) action.run();
        }

        @Override
        public void setEnabled(boolean e) {
            enabled = e;
            setCursor(Cursor.getPredefinedCursor(e ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            repaint();
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(Theme.ui(rel, Font.BOLD));
            int padX = kind == Kind.GHOST ? 10 : 22;
            return new Dimension(fm.stringWidth(text) + padX * 2, fm.getHeight() + 18);
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            hints(g);
            Theme t = Theme.get();
            int w = getWidth(), h = getHeight(), arc = 12;
            Color fg;
            switch (kind) {
                case PRIMARY:
                    g.setColor(enabled ? (hover ? t.accent.darker() : t.accent) : t.border);
                    g.fillRoundRect(0, 0, w, h, arc, arc);
                    fg = enabled ? t.onAccent : t.muted;
                    break;
                case SECONDARY:
                    g.setColor(hover && enabled ? t.surface2 : t.surface);
                    g.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);
                    g.setColor(t.border);
                    g.setStroke(new BasicStroke(1.2f));
                    g.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
                    fg = enabled ? t.text : t.muted;
                    break;
                default:
                    if (hover && enabled) {
                        g.setColor(t.surface2);
                        g.fillRoundRect(0, 0, w, h, arc, arc);
                    }
                    fg = enabled ? t.muted : t.border;
            }
            g.setFont(Theme.ui(rel, kind == Kind.GHOST ? Font.PLAIN : Font.BOLD));
            FontMetrics fm = g.getFontMetrics();
            g.setColor(fg);
            g.drawString(text, (w - fm.stringWidth(text)) / 2, (h - fm.getHeight()) / 2 + fm.getAscent());
            g.dispose();
        }
    }

    /**
     * Make a whole card clickable, including its children: text panes would otherwise swallow the
     * click, and a press-release pair counts even when the mouse moves slightly (unlike mouseClicked).
     */
    public static void onClick(JComponent target, Runnable r) {
        MouseAdapter m = new MouseAdapter() {
            private boolean down;

            @Override
            public void mousePressed(MouseEvent e) {
                down = SwingUtilities.isLeftMouseButton(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                boolean hit = down && target.contains(SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), target));
                down = false;
                if (hit) r.run();
            }
        };
        attach(target, m);
    }

    private static void attach(java.awt.Component c, MouseAdapter m) {
        c.addMouseListener(m);
        c.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        if (c instanceof javax.swing.text.JTextComponent) ((javax.swing.text.JTextComponent) c).setHighlighter(null);
        if (c instanceof java.awt.Container) {
            for (java.awt.Component k : ((java.awt.Container) c).getComponents()) attach(k, m);
        }
    }

    public static Button primary(String text, Runnable r) {
        return new Button(text, Button.Kind.PRIMARY, r);
    }

    public static Button secondary(String text, Runnable r) {
        return new Button(text, Button.Kind.SECONDARY, r);
    }

    public static Button ghost(String text, Runnable r) {
        return new Button(text, Button.Kind.GHOST, r);
    }

    // ------------------------------------------------------------------ card surface

    /** Rounded surface panel. */
    public static class Card extends JPanel {
        private Supplier<Color> fill = () -> Theme.get().surface;
        private Supplier<Color> stroke = () -> Theme.get().border;
        private final int arc;

        public Card(LayoutManager lm, int pad) {
            this(lm, pad, 16);
        }

        public Card(LayoutManager lm, int pad, int arc) {
            super(lm);
            this.arc = arc;
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(pad, pad + 4, pad, pad + 4));
        }

        public Card colors(Supplier<Color> fill, Supplier<Color> stroke) {
            this.fill = fill;
            this.stroke = stroke;
            return this;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            hints(g);
            g.setColor(fill.get());
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            Color s = stroke.get();
            if (s != null) {
                g.setColor(s);
                g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            }
            g.dispose();
        }
    }

    /** Thin horizontal progress bar. */
    public static final class Bar extends JComponent {
        private double value;
        private Supplier<Color> color = () -> Theme.get().accent;

        public Bar(int height) {
            setPreferredSize(new Dimension(100, height));
            setMinimumSize(new Dimension(10, height));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        }

        public void setValue(double v) {
            value = Math.max(0, Math.min(1, v));
            repaint();
        }

        public Bar color(Supplier<Color> c) {
            color = c;
            return this;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            hints(g);
            int h = getHeight(), w = getWidth();
            g.setColor(Theme.get().surface2);
            g.fillRoundRect(0, 0, w, h, h, h);
            g.setColor(color.get());
            int fw = (int) Math.round(w * value);
            if (fw > 0) g.fillRoundRect(0, 0, Math.max(fw, h), h, h, h);
            g.dispose();
        }
    }

    /** Small rounded tag. */
    public static JLabel chip(String text, Color fg, Color bg) {
        JLabel l = new JLabel(text, SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                hints(g);
                g.setColor(bg);
                g.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                g.dispose();
                super.paintComponent(g0);
            }
        };
        l.setFont(Theme.ui(0.75f, Font.BOLD));
        l.setForeground(fg);
        l.setBorder(BorderFactory.createEmptyBorder(3, 10, 3, 10));
        return l;
    }

    // ------------------------------------------------------------------ reading text

    /**
     * Render light markdown for reading: one paragraph per line, "- " bullets, `inline code`, **bold**.
     * Generous line spacing and paragraph gaps help keep your place.
     */
    public static JTextPane prose(String md, float rel, Color color) {
        JTextPane tp = new JTextPane();
        tp.setEditable(false);
        tp.setFocusable(false);
        tp.setOpaque(false);
        tp.setBorder(BorderFactory.createEmptyBorder());
        tp.setMargin(new Insets(0, 0, 0, 0));
        StyledDocument doc = tp.getStyledDocument();
        Theme t = Theme.get();
        Font f = Theme.ui(rel, Font.PLAIN);
        SimpleAttributeSet base = new SimpleAttributeSet();
        StyleConstants.setFontFamily(base, f.getFamily());
        StyleConstants.setFontSize(base, f.getSize());
        StyleConstants.setForeground(base, color);
        SimpleAttributeSet para = new SimpleAttributeSet();
        StyleConstants.setLineSpacing(para, 0.32f);
        StyleConstants.setSpaceBelow(para, Math.round(f.getSize() * 0.75f));
        String[] lines = md.split("\n");
        try {
            for (int li = 0; li < lines.length; li++) {
                String line = lines[li];
                boolean bullet = line.startsWith("- ");
                if (bullet) line = line.substring(2);
                int start = doc.getLength();
                if (bullet) doc.insertString(doc.getLength(), "•  ", base);
                appendInline(doc, line, base, t, f);
                if (li < lines.length - 1) doc.insertString(doc.getLength(), "\n", base);
                SimpleAttributeSet pa = new SimpleAttributeSet(para);
                if (bullet) {
                    StyleConstants.setLeftIndent(pa, f.getSize() * 1.2f);
                    StyleConstants.setFirstLineIndent(pa, -f.getSize() * 1.0f);
                    StyleConstants.setSpaceBelow(pa, Math.round(f.getSize() * 0.35f));
                }
                if (li == lines.length - 1) StyleConstants.setSpaceBelow(pa, 0);
                doc.setParagraphAttributes(start, Math.max(1, doc.getLength() - start), pa, false);
            }
        } catch (BadLocationException e) {
            throw new IllegalStateException(e);
        }
        return tp;
    }

    private static void appendInline(StyledDocument doc, String s, SimpleAttributeSet base, Theme t, Font f) throws BadLocationException {
        int i = 0;
        while (i < s.length()) {
            if (s.charAt(i) == '`') {
                int j = s.indexOf('`', i + 1);
                if (j > i) {
                    SimpleAttributeSet code = new SimpleAttributeSet(base);
                    Font m = Theme.mono(f.getSize() / (float) Theme.base() * 0.92f);
                    StyleConstants.setFontFamily(code, m.getFamily());
                    StyleConstants.setFontSize(code, m.getSize());
                    StyleConstants.setForeground(code, t.dark ? t.kw : t.kw);
                    StyleConstants.setBackground(code, t.codeBg);
                    doc.insertString(doc.getLength(), s.substring(i + 1, j), code);
                    i = j + 1;
                    continue;
                }
            }
            if (s.startsWith("**", i)) {
                int j = s.indexOf("**", i + 2);
                if (j > i) {
                    SimpleAttributeSet b = new SimpleAttributeSet(base);
                    StyleConstants.setBold(b, true);
                    doc.insertString(doc.getLength(), s.substring(i + 2, j), b);
                    i = j + 2;
                    continue;
                }
            }
            int next = s.length();
            int a = s.indexOf('`', i), b = s.indexOf("**", i);
            if (a >= 0 && a > i) next = Math.min(next, a);
            if (b >= 0 && b > i) next = Math.min(next, b);
            if (next == i) next = i + 1;
            doc.insertString(doc.getLength(), s.substring(i, next), base);
            i = next;
        }
    }

    // ------------------------------------------------------------------ layout

    /**
     * Stacks children vertically inside a centred column of at most {@code maxWidth} pixels:
     * long lines are hard to track, so reading width is capped (about 70 characters).
     */
    public static final class Column implements LayoutManager {
        private final int maxWidth, gap;

        public Column(int maxWidth, int gap) {
            this.maxWidth = maxWidth;
            this.gap = gap;
        }

        private int width(Container c) {
            Insets in = c.getInsets();
            int avail = c.getWidth() - in.left - in.right;
            if (avail <= 0) avail = maxWidth;
            return Math.min(maxWidth, avail);
        }

        private int childHeight(Component ch, int w) {
            if (ch instanceof JTextPane || ch instanceof Container && ((Container) ch).getLayout() instanceof Column) {
                ch.setSize(w, Short.MAX_VALUE);
            } else {
                ch.setSize(w, ch.getPreferredSize().height);
            }
            return ch.getPreferredSize().height;
        }

        @Override
        public void layoutContainer(Container c) {
            Insets in = c.getInsets();
            int w = width(c);
            int x = in.left + Math.max(0, (c.getWidth() - in.left - in.right - w) / 2);
            int y = in.top;
            for (Component ch : c.getComponents()) {
                if (!ch.isVisible()) continue;
                int h = childHeight(ch, w);
                Dimension max = ch.getMaximumSize();
                int cw = w;
                int cx = x;
                if (max != null && max.width < w && !(ch instanceof JTextPane) && !(ch instanceof JPanel)) {
                    cw = Math.min(w, ch.getPreferredSize().width);
                    if (ch instanceof JComponent && ((JComponent) ch).getAlignmentX() == Component.CENTER_ALIGNMENT) {
                        cx = x + (w - cw) / 2;
                    }
                }
                ch.setBounds(cx, y, cw, h);
                y += h + gap;
            }
        }

        @Override
        public Dimension preferredLayoutSize(Container c) {
            Insets in = c.getInsets();
            int w = width(c);
            int h = in.top + in.bottom;
            int n = 0;
            for (Component ch : c.getComponents()) {
                if (!ch.isVisible()) continue;
                h += childHeight(ch, w);
                n++;
            }
            if (n > 1) h += gap * (n - 1);
            return new Dimension(w + in.left + in.right, h);
        }

        @Override
        public Dimension minimumLayoutSize(Container c) {
            return new Dimension(200, preferredLayoutSize(c).height);
        }

        @Override
        public void addLayoutComponent(String name, Component comp) {
        }

        @Override
        public void removeLayoutComponent(Component comp) {
        }
    }

    /** A JPanel that tracks the viewport width when put in a scroll pane. */
    public static final class Page extends JPanel implements javax.swing.Scrollable {
        public Page(LayoutManager lm) {
            super(lm);
            setOpaque(true);
            setBackground(Theme.get().bg);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(java.awt.Rectangle r, int o, int d) {
            return 24;
        }

        @Override
        public int getScrollableBlockIncrement(java.awt.Rectangle r, int o, int d) {
            return r.height - 48;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return getParent() != null && getParent().getHeight() > getPreferredSize().height;
        }
    }

    /** Scrollbar without arrows: a thin rounded thumb. */
    static final class MinimalScrollBarUI extends javax.swing.plaf.basic.BasicScrollBarUI {
        @Override
        protected void configureScrollBarColors() {
            thumbColor = Theme.get().border;
            trackColor = Theme.get().bg;
        }

        private JComponent zero() {
            JPanel p = new JPanel();
            p.setPreferredSize(new Dimension(0, 0));
            return p;
        }

        @Override
        protected javax.swing.JButton createDecreaseButton(int o) {
            javax.swing.JButton b = new javax.swing.JButton();
            b.setPreferredSize(new Dimension(0, 0));
            return b;
        }

        @Override
        protected javax.swing.JButton createIncreaseButton(int o) {
            return createDecreaseButton(o);
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, java.awt.Rectangle r) {
            g.setColor(Theme.get().bg);
            g.fillRect(r.x, r.y, r.width, r.height);
        }

        @Override
        protected void paintThumb(Graphics g0, JComponent c, java.awt.Rectangle r) {
            if (r.isEmpty()) return;
            Graphics2D g = (Graphics2D) g0.create();
            hints(g);
            g.setColor(Theme.get().border);
            g.fillRoundRect(r.x + 2, r.y + 2, r.width - 4, r.height - 4, 8, 8);
            g.dispose();
        }
    }
}
