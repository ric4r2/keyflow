package keyflow.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.JComponent;

import keyflow.vim.VimEngine;

/**
 * Painted code editor backed by {@link VimEngine}. Works in Vim mode or, with Vim keys switched off,
 * as a plain editor. Can also be a read-only code viewer.
 */
public final class CodeEditor extends JComponent {

    private static final Set<String> KEYWORDS = new HashSet<>(Arrays.asList(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const", "continue",
            "default", "do", "double", "else", "enum", "extends", "final", "finally", "float", "for", "goto", "if",
            "implements", "import", "instanceof", "int", "interface", "long", "native", "new", "package", "private",
            "protected", "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
            "throw", "throws", "transient", "try", "void", "volatile", "while", "var", "record", "sealed", "permits",
            "yield", "true", "false", "null"));

    private VimEngine engine;
    private final boolean readOnly;
    private boolean vim = true;
    private boolean showStatus = true;
    private int goalRow = -1, goalCol = -1;
    private List<String> compareTo;
    private int topLine;
    private int minRows = 3, maxRows = 22;
    private Runnable onChange, onSubmit;
    private String statusRight = "";
    private boolean flash;

    public CodeEditor(String text, boolean readOnly) {
        this.engine = new VimEngine(text);
        this.readOnly = readOnly;
        setFocusable(!readOnly);
        setFocusTraversalKeysEnabled(false);
        setOpaque(false);
        if (readOnly) showStatus = false;
        if (!readOnly) installInput();
    }

    // ------------------------------------------------------------------ configuration

    public VimEngine engine() {
        return engine;
    }

    public void setEngine(VimEngine e) {
        engine = e;
        engine.setPlainMode(!vim && !readOnly);
        topLine = 0;
        repaint();
        revalidate();
    }

    public void setVim(boolean on) {
        vim = on;
        if (!readOnly) engine.setPlainMode(!on);
        repaint();
    }

    public boolean isVim() {
        return vim;
    }

    public void setGoal(int r, int c) {
        goalRow = r;
        goalCol = c;
        repaint();
    }

    /** Tint lines that differ from these (used by the target preview). */
    public void setCompareTo(List<String> other) {
        compareTo = other;
        repaint();
    }

    public void setRows(int min, int max) {
        minRows = min;
        maxRows = max;
        revalidate();
    }

    public void setShowStatus(boolean s) {
        showStatus = s;
        revalidate();
    }

    public void setOnChange(Runnable r) {
        onChange = r;
    }

    public void setOnSubmit(Runnable r) {
        onSubmit = r;
    }

    public void setStatusRight(String s) {
        statusRight = s == null ? "" : s;
        repaint();
    }

    public String getText() {
        return engine.getText();
    }

    // ------------------------------------------------------------------ geometry

    private Font font() {
        return Theme.mono(0.92f);
    }

    private int lineH() {
        return getFontMetrics(font()).getHeight() + 4;
    }

    private int charW() {
        return getFontMetrics(font()).charWidth('m');
    }

    private int gutterW() {
        int digits = Math.max(2, String.valueOf(engine.getLines().size()).length());
        return charW() * (digits + 2) + 6;
    }

    private int statusH() {
        return showStatus ? lineH() + 10 : 0;
    }

    private static final int PAD = 12;

    private int visibleRows() {
        return Math.max(1, (getHeight() - statusH() - PAD * 2) / lineH());
    }

    @Override
    public Dimension getPreferredSize() {
        int rows = Math.max(minRows, Math.min(maxRows, engine.getLines().size() + (readOnly ? 0 : 1)));
        int maxLen = 20;
        for (String l : engine.getLines()) maxLen = Math.max(maxLen, l.length());
        return new Dimension(gutterW() + PAD * 2 + charW() * Math.min(maxLen + 2, 90), rows * lineH() + PAD * 2 + statusH());
    }

    // ------------------------------------------------------------------ input

    private void installInput() {
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                int row = topLine + (e.getY() - PAD) / lineH();
                int col = Math.max(0, (e.getX() - PAD - gutterW() + charW() / 2) / charW());
                if (row >= 0 && row < engine.getLines().size() && (engine.getMode() == VimEngine.Mode.NORMAL || engine.getMode() == VimEngine.Mode.INSERT)) {
                    engine.setCursor(row, col);
                    repaint();
                }
            }
        });
        addMouseWheelListener(e -> {
            int max = Math.max(0, engine.getLines().size() - visibleRows());
            topLine = Math.max(0, Math.min(max, topLine + e.getWheelRotation() * 3));
            repaint();
        });
        addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                repaint();
            }

            @Override
            public void focusLost(FocusEvent e) {
                repaint();
            }
        });
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                String k = special(e);
                if (k == null) return;
                e.consume();
                if (k.equals("submit")) {
                    if (onSubmit != null) onSubmit.run();
                    return;
                }
                if (k.equals("paste")) {
                    try {
                        String s = (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
                        engine.paste(s);
                        changed();
                    } catch (Exception ignored) {
                        // nothing to paste
                    }
                    return;
                }
                send(k);
            }

            @Override
            public void keyTyped(KeyEvent e) {
                char c = e.getKeyChar();
                boolean ctrlOnly = (e.isControlDown() && !e.isAltDown()) || e.isMetaDown();
                if (c == KeyEvent.CHAR_UNDEFINED || c < 32 || c == 127 || ctrlOnly) return;
                e.consume();
                send(String.valueOf(c));
            }
        });
    }

    /** Map a key press to Vim notation (null = handled by keyTyped or ignored). */
    private String special(KeyEvent e) {
        int code = e.getKeyCode();
        boolean ctrl = e.isControlDown() && !e.isAltDown();   // AltGr = Ctrl+Alt on Windows: not a Ctrl chord
        boolean meta = e.isMetaDown();
        if ((ctrl || meta) && code == KeyEvent.VK_ENTER) return "submit";
        if ((ctrl && e.isShiftDown() && code == KeyEvent.VK_V) || (meta && code == KeyEvent.VK_V)) return "paste";
        if (!vim && ctrl && code == KeyEvent.VK_V) return "paste";
        switch (code) {
            case KeyEvent.VK_ESCAPE: return "<Esc>";
            case KeyEvent.VK_ENTER: return "<CR>";
            case KeyEvent.VK_BACK_SPACE: return "<BS>";
            case KeyEvent.VK_TAB: return "<Tab>";
            case KeyEvent.VK_DELETE: return "<Del>";
            case KeyEvent.VK_LEFT: return "<Left>";
            case KeyEvent.VK_RIGHT: return "<Right>";
            case KeyEvent.VK_UP: return "<Up>";
            case KeyEvent.VK_DOWN: return "<Down>";
            case KeyEvent.VK_HOME: return "<Home>";
            case KeyEvent.VK_END: return "<End>";
            default:
                break;
        }
        if (ctrl && code == KeyEvent.VK_OPEN_BRACKET) return "<Esc>";
        if (ctrl && code >= KeyEvent.VK_A && code <= KeyEvent.VK_Z) {
            return "<C-" + Character.toLowerCase((char) code) + ">";
        }
        return null;
    }

    private void send(String k) {
        engine.key(k);
        if (engine.consumeBell()) {
            flash = true;
            javax.swing.Timer t = new javax.swing.Timer(120, ev -> {
                flash = false;
                repaint();
            });
            t.setRepeats(false);
            t.start();
        }
        changed();
    }

    private void changed() {
        ensureVisible();
        repaint();
        revalidate();
        if (onChange != null) onChange.run();
    }

    private void ensureVisible() {
        int vis = visibleRows();
        int r = engine.getRow();
        int so = Math.min(3, vis / 3);
        if (r < topLine + so) topLine = Math.max(0, r - so);
        if (r > topLine + vis - 1 - so) topLine = Math.max(0, r - vis + 1 + so);
        engine.setViewport(topLine, vis);
    }

    // ------------------------------------------------------------------ painting

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        Ui.hints(g);
        Theme t = Theme.get();
        int w = getWidth(), h = getHeight();
        g.setColor(t.codeBg);
        g.fillRoundRect(0, 0, w - 1, h - 1, 14, 14);
        boolean focused = isFocusOwner();
        g.setColor(flash ? t.error : (focused ? Theme.alpha(t.accent, 140) : t.border));
        g.setStroke(new BasicStroke(focused || flash ? 1.6f : 1f));
        g.drawRoundRect(0, 0, w - 1, h - 1, 14, 14);

        Font f = font();
        g.setFont(f);
        FontMetrics fm = g.getFontMetrics();
        int lh = lineH(), cw = charW(), gw = gutterW();
        int x0 = PAD + gw;
        List<String> lines = engine.getLines();
        int vis = visibleRows();
        if (!readOnly) engine.setViewport(topLine, vis);
        boolean showRelative = vim && !readOnly;
        boolean inBlockComment = blockCommentStateBefore(lines, topLine);
        g.setClip(1, 1, w - 2, h - statusH() - 1);
        for (int i = 0; i < vis + 1 && topLine + i < lines.size(); i++) {
            int r = topLine + i;
            int y = PAD + i * lh;
            int base = y + (lh - fm.getHeight()) / 2 + fm.getAscent();
            String line = lines.get(r);

            if (compareTo != null && (r >= compareTo.size() || !compareTo.get(r).replaceAll("\\s+$", "").equals(line.replaceAll("\\s+$", "")))) {
                g.setColor(t.dark ? new Color(70, 60, 30, 90) : new Color(243, 227, 181, 140));
                g.fillRect(PAD, y, w - PAD * 2, lh);
            }
            if (!readOnly && r == engine.getRow() && focused) {
                g.setColor(Theme.alpha(t.accent, t.dark ? 18 : 14));
                g.fillRect(PAD + gw - 6, y, w - PAD * 2 - gw + 6, lh);
            }
            // gutter
            String num;
            if (showRelative && r != engine.getRow()) num = String.valueOf(Math.abs(r - engine.getRow()));
            else num = String.valueOf(r + 1);
            g.setColor(r == engine.getRow() && !readOnly ? t.muted : t.gutter);
            g.drawString(num, PAD + gw - 10 - fm.stringWidth(num), base);

            // search matches and selection
            for (int[] m : engine.getSearchMatches(r)) {
                g.setColor(t.search);
                g.fillRect(x0 + m[0] * cw, y + 1, (m[1] - m[0]) * cw, lh - 2);
            }
            if (engine.isVisual()) {
                int[] b = engine.getVisualBounds();
                if (r >= b[0] && r <= b[2]) {
                    int sc = b[4] == 1 || r > b[0] ? 0 : b[1];
                    int ec = b[4] == 1 || r < b[2] ? Math.max(line.length(), 1) : Math.min(b[3] + 1, Math.max(line.length(), 1));
                    g.setColor(t.selection);
                    g.fillRect(x0 + sc * cw, y + 1, Math.max(cw, (ec - sc) * cw), lh - 2);
                }
            }
            // goal marker
            if (r == goalRow) {
                g.setColor(t.goal);
                g.setStroke(new BasicStroke(1.8f));
                g.drawRoundRect(x0 + goalCol * cw - 1, y + 1, cw + 1, lh - 3, 5, 5);
            }
            inBlockComment = drawHighlighted(g, line, x0, base, cw, inBlockComment, t);
            // cursor
            if (!readOnly && r == engine.getRow()) drawCursor(g, line, x0, y, cw, lh, base, focused, t);
        }
        g.setClip(null);
        if (showStatus) drawStatus(g, w, h, t);
        g.dispose();
    }

    private void drawCursor(Graphics2D g, String line, int x0, int y, int cw, int lh, int base, boolean focused, Theme t) {
        int c = engine.getCol();
        int cx = x0 + c * cw;
        VimEngine.Mode m = engine.getMode();
        g.setColor(focused ? t.cursor : t.gutter);
        if (m == VimEngine.Mode.INSERT) {
            g.fillRect(cx, y + 2, 2, lh - 4);
        } else if (m == VimEngine.Mode.REPLACE) {
            g.fillRect(cx, y + lh - 4, cw, 3);
        } else {
            if (focused) {
                g.fillRect(cx, y + 1, cw, lh - 2);
                if (c < line.length()) {
                    g.setColor(t.codeBg);
                    g.drawString(String.valueOf(line.charAt(c)), cx, base);
                }
            } else {
                g.drawRect(cx, y + 1, cw - 1, lh - 3);
            }
        }
    }

    private void drawStatus(Graphics2D g, int w, int h, Theme t) {
        int sh = statusH();
        int y = h - sh;
        g.setColor(t.border);
        g.drawLine(PAD, y, w - PAD, y);
        Font sf = Theme.ui(0.78f, Font.BOLD);
        g.setFont(sf);
        FontMetrics fm = g.getFontMetrics();
        int base = y + (sh - fm.getHeight()) / 2 + fm.getAscent();
        String left;
        Color chipBg;
        Color chipFg;
        if (!vim) {
            left = "PLAIN";
            chipBg = t.surface2;
            chipFg = t.muted;
        } else {
            switch (engine.getMode()) {
                case INSERT: left = "INSERT"; chipBg = t.successSoft; chipFg = t.success; break;
                case REPLACE: left = "REPLACE"; chipBg = t.errorSoft; chipFg = t.error; break;
                case VISUAL: left = "VISUAL"; chipBg = t.accentSoft; chipFg = t.accent; break;
                case VISUAL_LINE: left = "V-LINE"; chipBg = t.accentSoft; chipFg = t.accent; break;
                default: left = "NORMAL"; chipBg = t.surface2; chipFg = t.muted;
            }
        }
        int cwid = fm.stringWidth(left) + 16;
        g.setColor(chipBg);
        g.fillRoundRect(PAD, base - fm.getAscent() - 3, cwid, fm.getHeight() + 6, 10, 10);
        g.setColor(chipFg);
        g.drawString(left, PAD + 8, base);
        int x = PAD + cwid + 12;
        g.setFont(Theme.mono(0.8f));
        fm = g.getFontMetrics();
        String mid;
        if (!vim) mid = "Ctrl+Enter to check";
        else if (engine.getCommandLine() != null) mid = engine.getCommandLine() + "█";
        else if (!engine.getPendingKeys().isEmpty()) mid = engine.getPendingKeys();
        else if (!engine.getMessage().isEmpty()) mid = engine.getMessage();
        else if (engine.getRecordingRegister() != null) mid = "recording @" + engine.getRecordingRegister();
        else mid = "";
        g.setColor(engine.getCommandLine() != null ? t.text : t.muted);
        int rightW = fm.stringWidth(statusRight);
        String clipped = mid;
        while (clipped.length() > 0 && fm.stringWidth(clipped) > w - x - rightW - PAD * 3) clipped = clipped.substring(0, clipped.length() - 1);
        g.drawString(clipped, x, base);
        g.setColor(t.muted);
        g.drawString(statusRight, w - PAD - rightW, base);
    }

    // ------------------------------------------------------------------ syntax highlighting

    private static boolean blockCommentStateBefore(List<String> lines, int upto) {
        boolean in = false;
        for (int r = 0; r < upto && r < lines.size(); r++) {
            String s = lines.get(r);
            int i = 0;
            while (i < s.length()) {
                if (in) {
                    int e = s.indexOf("*/", i);
                    if (e < 0) break;
                    in = false;
                    i = e + 2;
                } else {
                    int lc = s.indexOf("//", i), bc = s.indexOf("/*", i);
                    if (bc < 0 || (lc >= 0 && lc < bc)) break;
                    in = true;
                    i = bc + 2;
                }
            }
        }
        return in;
    }

    /** Draw one line with simple Java highlighting; returns whether a block comment continues. */
    private boolean drawHighlighted(Graphics2D g, String s, int x0, int base, int cw, boolean inComment, Theme t) {
        int i = 0, n = s.length();
        while (i < n) {
            int start = i;
            Color c;
            if (inComment) {
                int e = s.indexOf("*/", i);
                i = e < 0 ? n : e + 2;
                if (e >= 0) inComment = false;
                c = t.comment;
            } else {
                char ch = s.charAt(i);
                if (ch == '/' && i + 1 < n && s.charAt(i + 1) == '/') {
                    i = n;
                    c = t.comment;
                } else if (ch == '/' && i + 1 < n && s.charAt(i + 1) == '*') {
                    int e = s.indexOf("*/", i + 2);
                    i = e < 0 ? n : e + 2;
                    if (e < 0) inComment = true;
                    c = t.comment;
                } else if (ch == '"' || ch == '\'') {
                    i++;
                    while (i < n && s.charAt(i) != ch) {
                        if (s.charAt(i) == '\\') i++;
                        i++;
                    }
                    i = Math.min(n, i + 1);
                    c = t.str;
                } else if (Character.isDigit(ch)) {
                    while (i < n && (Character.isLetterOrDigit(s.charAt(i)) || s.charAt(i) == '_' || s.charAt(i) == '.')) i++;
                    c = t.num;
                } else if (ch == '@' && i + 1 < n && Character.isLetter(s.charAt(i + 1))) {
                    i++;
                    while (i < n && Character.isJavaIdentifierPart(s.charAt(i))) i++;
                    c = t.annotation;
                } else if (Character.isJavaIdentifierStart(ch)) {
                    while (i < n && Character.isJavaIdentifierPart(s.charAt(i))) i++;
                    String word = s.substring(start, i);
                    if (KEYWORDS.contains(word)) c = t.kw;
                    else if (Character.isUpperCase(word.charAt(0))) c = t.type;
                    else c = t.codeText;
                } else {
                    i++;
                    c = t.codeText;
                }
            }
            g.setColor(c);
            String piece = s.substring(start, i);
            for (int k = 0; k < piece.length(); k++) {
                char pc = piece.charAt(k);
                if (pc != ' ' && pc != '\t') g.drawString(String.valueOf(pc), x0 + (start + k) * cw, base);
            }
        }
        return inComment;
    }

    /** Hint for screen readers / tests. */
    @Override
    public String toString() {
        return "CodeEditor[" + engine.getText().length() + " chars, vim=" + vim + "]";
    }

    static boolean isCtrl(InputEvent e) {
        return e.isControlDown() && !e.isAltDown();
    }
}
