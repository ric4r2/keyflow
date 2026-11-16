package keyflow.vim;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * A self-contained Vim emulator used by the trainer.
 *
 * <p>It implements the Vim "grammar": {@code ["x][count]operator[count]motion|textobject},
 * normal / insert / replace / visual / visual-line modes, registers, undo/redo, the dot
 * command, macros, marks, searches and a useful subset of Ex commands
 * ({@code :s :g :v :norm :d :y :m :t :j :sort :> :<}).</p>
 *
 * <p>Keys are fed one at a time as strings: a single character ({@code "d"}, {@code " "}) or a
 * special key in Vim notation ({@code "<Esc>"}, {@code "<CR>"}, {@code "<BS>"}, {@code "<C-r>"}).
 * Use {@link #tokenize(String)} to convert a notation string like {@code "ciwfoo<Esc>"}.</p>
 */
public final class VimEngine {

    public enum Mode { NORMAL, INSERT, REPLACE, VISUAL, VISUAL_LINE }

    public static final int SHIFT_WIDTH = 4;
    public static final int HALF_PAGE = 10;

    /** Contents of a register. */
    public static final class Register {
        public final String text;
        public final boolean linewise;

        Register(String text, boolean linewise) {
            this.text = text;
            this.linewise = linewise;
        }
    }

    // ------------------------------------------------------------------ internal types

    private static final class Incomplete extends RuntimeException {
        final String line;

        Incomplete(String line) {
            super(null, null, false, false);
            this.line = line;
        }
    }

    private static final class Invalid extends RuntimeException {
        Invalid() {
            super(null, null, false, false);
        }
    }

    private static final Incomplete NEED_MORE = new Incomplete(null);
    private static final Invalid BAD = new Invalid();

    private static final int EXCL = 0, INCL = 1, LINE = 2;

    private static final class Motion {
        final int r, c, type;
        boolean keepWant, wantEnd, jump;

        Motion(int r, int c, int type) {
            this.r = r;
            this.c = c;
            this.type = type;
        }
    }

    private static final class Range {
        final boolean linewise;
        final int sr, er, from, to;

        private Range(boolean linewise, int sr, int er, int from, int to) {
            this.linewise = linewise;
            this.sr = sr;
            this.er = er;
            this.from = from;
            this.to = to;
        }

        static Range lines(int sr, int er) {
            return new Range(true, Math.min(sr, er), Math.max(sr, er), 0, 0);
        }

        static Range chars(int from, int to) {
            return new Range(false, 0, 0, Math.min(from, to), Math.max(from, to));
        }

        boolean empty() {
            return !linewise && to <= from;
        }
    }

    private static final class Snapshot {
        final List<String> lines;
        final int row, col;

        Snapshot(List<String> lines, int row, int col) {
            this.lines = lines;
            this.row = row;
            this.col = col;
        }
    }

    private static final class DotRecord {
        final List<String> reg;
        final int count;
        final List<String> rest;

        DotRecord(List<String> reg, int count, List<String> rest) {
            this.reg = reg;
            this.count = count;
            this.rest = rest;
        }
    }

    // ------------------------------------------------------------------ state

    private List<String> lines = new ArrayList<>();
    private int row, col, wantCol;
    private Mode mode = Mode.NORMAL;
    private int vRow, vCol;
    private Mode lastVisualMode;
    private int lvr1, lvc1, lvr2, lvc2;

    private final List<String> pending = new ArrayList<>();
    private int ti;
    private String lineDisplay;
    private String message = "";
    private boolean bell;

    private final Map<Character, Register> registers = new HashMap<>();
    private final Map<Character, int[]> marks = new HashMap<>();
    private final Deque<Snapshot> undo = new ArrayDeque<>();
    private final Deque<Snapshot> redo = new ArrayDeque<>();
    private int suppressUndo;

    private DotRecord lastDot, recordingDot;
    private boolean changed, insertEntered, noDot;

    private Character macroReg;
    private final List<String> macroBuf = new ArrayList<>();
    private Character lastMacro;

    private String lastSearch;
    private boolean lastSearchForward = true;
    private boolean hlsearch;
    private String cachedPatternSrc;
    private Pattern cachedPattern;

    private String lastFindCmd, lastFindChar;
    private String lastSubPattern, lastSubReplacement, lastSubFlags = "";
    private String lastEx;
    private String lastInserted;

    private int insertCount = 1;
    private String insertKind = "i";
    private final List<String> insertKeys = new ArrayList<>();
    private boolean ctrlRPending;
    private final Deque<Character> replaced = new ArrayDeque<>();

    private final List<Runnable> deferred = new ArrayList<>();
    private int depth;
    private boolean abortReplay;

    private int keystrokes;
    private boolean plain;
    private final List<String> keyLog = new ArrayList<>();
    private int viewTop = 0, viewHeight = -1;

    // ------------------------------------------------------------------ public API

    public VimEngine() {
        setText("");
    }

    public VimEngine(String text) {
        setText(text);
    }

    /** Replace the whole buffer and reset cursor, mode, undo history and the keystroke counter. */
    public void setText(String text) {
        lines = splitLines(text == null ? "" : text);
        row = col = wantCol = 0;
        mode = Mode.NORMAL;
        pending.clear();
        lineDisplay = null;
        message = "";
        undo.clear();
        redo.clear();
        keystrokes = 0;
        keyLog.clear();
    }

    public String getText() {
        return String.join("\n", lines);
    }

    public List<String> getLines() {
        return Collections.unmodifiableList(lines);
    }

    public int getRow() {
        return row;
    }

    public int getCol() {
        return col;
    }

    public Mode getMode() {
        return mode;
    }

    public boolean isVisual() {
        return mode == Mode.VISUAL || mode == Mode.VISUAL_LINE;
    }

    /** True when in normal mode with no partially typed command. */
    public boolean isIdle() {
        return mode == Mode.NORMAL && pending.isEmpty();
    }

    public void setCursor(int r, int c) {
        row = Math.max(0, Math.min(r, lines.size() - 1));
        col = c;
        clampCursor();
        wantCol = col;
    }

    public int getKeystrokes() {
        return keystrokes;
    }

    public List<String> getKeyLog() {
        return Collections.unmodifiableList(keyLog);
    }

    /** Keys typed so far for an unfinished command (Vim's "showcmd"). */
    public String getPendingKeys() {
        if (lineDisplay != null) return "";
        StringBuilder sb = new StringBuilder();
        for (String p : pending) sb.append(p);
        return sb.toString();
    }

    /** The command line being typed (":..." or "/..."), or null. */
    public String getCommandLine() {
        return lineDisplay;
    }

    public String getMessage() {
        return message;
    }

    /** Returns true once if the last key produced an error (beep). */
    public boolean consumeBell() {
        boolean b = bell;
        bell = false;
        return b;
    }

    public Character getRecordingRegister() {
        return macroReg;
    }

    public Register getRegister(char c) {
        return registers.get(Character.toLowerCase(c));
    }

    /** Tell the engine which lines are visible (used by H, M, L). */
    public void setViewport(int top, int height) {
        viewTop = top;
        viewHeight = height;
    }

    /**
     * Plain mode turns the engine into an ordinary editor: always inserting, Esc ignored,
     * Ctrl-z / Ctrl-y undo and redo. Used when the learner switches Vim keys off.
     */
    public void setPlainMode(boolean on) {
        plain = on;
        pending.clear();
        lineDisplay = null;
        mode = on ? Mode.INSERT : Mode.NORMAL;
        clampCursor();
    }

    public boolean isPlainMode() {
        return plain;
    }

    /** Insert text at the cursor (clipboard paste). Only works while typing text. */
    public void paste(String text) {
        if (text == null || text.isEmpty()) return;
        if (mode != Mode.INSERT) return;
        saveUndo();
        insertText(text.replace("\r\n", "\n").replace("\r", "\n").replace("\t", "    "));
    }

    /** Feed one key typed by the user (counts towards the keystroke score). */
    public void key(String k) {
        if (k == null || k.isEmpty()) return;
        keystrokes++;
        keyLog.add(k);
        if (keyLog.size() > 60) keyLog.remove(0);
        if (macroReg != null) macroBuf.add(k);
        if (lineDisplay == null) message = "";
        feed(k);
    }

    /** Feed a whole notation string, e.g. {@code "dwi<Esc>"}. */
    public void keys(String notation) {
        for (String k : tokenize(notation)) key(k);
    }

    /** Selection bounds {sr, sc, er, ec, linewise(0/1)} in visual mode, otherwise null. */
    public int[] getVisualBounds() {
        if (!isVisual()) return null;
        int sr = vRow, sc = vCol, er = row, ec = col;
        if (er < sr || (er == sr && ec < sc)) {
            int t = sr; sr = er; er = t;
            t = sc; sc = ec; ec = t;
        }
        return new int[]{sr, sc, er, ec, mode == Mode.VISUAL_LINE ? 1 : 0};
    }

    public boolean isSelected(int r, int c) {
        int[] b = getVisualBounds();
        if (b == null) return false;
        if (r < b[0] || r > b[2]) return false;
        if (b[4] == 1) return true;
        if (r == b[0] && c < b[1]) return false;
        if (r == b[2] && c > b[3]) return false;
        return true;
    }

    /** Highlighted search matches on a line as {start, end} pairs. */
    public List<int[]> getSearchMatches(int r) {
        List<int[]> out = new ArrayList<>();
        if (!hlsearch || lastSearch == null || r < 0 || r >= lines.size()) return out;
        Pattern p;
        try {
            p = compileCached(lastSearch);
        } catch (RuntimeException e) {
            return out;
        }
        Matcher m = p.matcher(lines.get(r));
        while (m.find()) {
            if (m.end() > m.start()) out.add(new int[]{m.start(), m.end()});
            if (out.size() > 200) break;
        }
        return out;
    }

    // ------------------------------------------------------------------ key notation

    /** Split Vim key notation into individual key tokens. */
    public static List<String> tokenize(String s) {
        List<String> out = new ArrayList<>();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '<') {
                int j = s.indexOf('>', i);
                if (j > i + 1) {
                    String norm = normalizeSpecial(s.substring(i + 1, j));
                    if (norm != null) {
                        out.add(norm);
                        i = j + 1;
                        continue;
                    }
                }
            }
            if (c == '\n') out.add("<CR>");
            else if (c == '\t') out.add("<Tab>");
            else if (c != '\r') out.add(String.valueOf(c));
            i++;
        }
        return out;
    }

    /** Join tokens back into notation. */
    public static String toNotation(List<String> keys) {
        StringBuilder sb = new StringBuilder();
        for (String k : keys) sb.append(k);
        return sb.toString();
    }

    private static String normalizeSpecial(String name) {
        String n = name.toLowerCase();
        switch (n) {
            case "esc": case "c-[": return "<Esc>";
            case "cr": case "enter": case "return": return "<CR>";
            case "bs": case "backspace": return "<BS>";
            case "tab": return "<Tab>";
            case "del": case "delete": return "<Del>";
            case "space": return " ";
            case "lt": return "<";
            case "bar": return "|";
            case "bslash": return "\\";
            case "left": return "<Left>";
            case "right": return "<Right>";
            case "up": return "<Up>";
            case "down": return "<Down>";
            case "home": return "<Home>";
            case "end": return "<End>";
            case "insert": return "<Insert>";
            default:
                if (n.length() == 3 && n.charAt(0) == 'c' && n.charAt(1) == '-' && Character.isLetter(n.charAt(2))) {
                    return "<C-" + n.charAt(2) + ">";
                }
                return null;
        }
    }

    // ------------------------------------------------------------------ dispatch

    private void feed(String k) {
        if (plain) {
            feedPlain(k);
            return;
        }
        switch (mode) {
            case INSERT:
                feedInsert(k);
                break;
            case REPLACE:
                feedReplace(k);
                break;
            default:
                feedNormal(k);
        }
    }

    private void feedPlain(String k) {
        mode = Mode.INSERT;
        switch (k) {
            case "<Esc>":
            case "<C-c>":
                return;
            case "<C-z>":
                doUndo();
                mode = Mode.INSERT;
                return;
            case "<C-y>":
                doRedo();
                mode = Mode.INSERT;
                return;
            default:
                if (k.length() == 1 && k.equals(" ") || k.equals("<CR>") || k.equals("<BS>") || k.equals("<Del>") || undo.isEmpty()) saveUndo();
                insertKey(k);
        }
    }

    private void feedNormal(String k) {
        boolean esc = k.equals("<Esc>") || k.equals("<C-c>");
        if (lineDisplay != null) {
            if (esc) {
                pending.clear();
                lineDisplay = null;
                return;
            }
            if (k.equals("<BS>") || k.equals("<C-h>")) {
                pending.remove(pending.size() - 1);
                runPending();
                if (lineDisplay == null) pending.clear();
                return;
            }
        } else if (esc && !pending.isEmpty()) {
            pending.clear();
            return;
        }
        pending.add(k);
        runPending();
    }

    private void runPending() {
        ti = 0;
        lineDisplay = null;
        try {
            executeCommand();
            pending.clear();
        } catch (Incomplete e) {
            lineDisplay = e.line;
            return;
        } catch (Invalid e) {
            pending.clear();
            bell = true;
            if (depth > 0) abortReplay = true;
        }
        runDeferred();
    }

    private void runDeferred() {
        while (!deferred.isEmpty()) {
            List<Runnable> copy = new ArrayList<>(deferred);
            deferred.clear();
            for (Runnable r : copy) r.run();
        }
    }

    private void replay(List<String> tokens, int times) {
        depth++;
        try {
            if (depth > 40) return;
            for (int t = 0; t < times && !abortReplay; t++) {
                for (String k : tokens) {
                    if (abortReplay) break;
                    feed(k);
                }
            }
        } finally {
            depth--;
            if (depth == 0) abortReplay = false;
        }
    }

    private String next() {
        if (ti >= pending.size()) throw NEED_MORE;
        return pending.get(ti++);
    }

    private String readLine(String prefix) {
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (ti >= pending.size()) throw new Incomplete(prefix + sb);
            String t = pending.get(ti++);
            if (t.equals("<CR>")) return sb.toString();
            if (t.length() == 1) sb.append(t);
            else if (t.equals("<Tab>")) sb.append('\t');
        }
    }

    private static boolean isDigit(String t) {
        return t.length() == 1 && t.charAt(0) >= '0' && t.charAt(0) <= '9';
    }

    private void executeCommand() {
        changed = false;
        insertEntered = false;
        noDot = false;
        String reg = null;
        int regEnd = 0;
        String t = next();
        if (t.equals("\"")) {
            String r = next();
            if (r.length() != 1 || !validRegister(r.charAt(0))) throw BAD;
            reg = r;
            regEnd = 2;
            t = next();
        }
        int count = 0;
        boolean hasCount = false;
        while (isDigit(t) && (hasCount || !t.equals("0"))) {
            count = Math.min(count * 10 + (t.charAt(0) - '0'), 99999);
            hasCount = true;
            t = next();
        }
        int cmdStart = ti - 1;
        int n = hasCount ? count : 1;
        if (isVisual()) {
            executeVisual(t, reg, n, hasCount);
            return;
        }
        executeNormal(t, reg, n, hasCount);
        if (noDot) return;
        if (insertEntered) {
            recordingDot = new DotRecord(new ArrayList<>(pending.subList(0, regEnd)), hasCount ? count : 0,
                    new ArrayList<>(pending.subList(cmdStart, pending.size())));
        } else if (changed) {
            lastDot = new DotRecord(new ArrayList<>(pending.subList(0, regEnd)), hasCount ? count : 0,
                    new ArrayList<>(pending.subList(cmdStart, pending.size())));
        }
    }

    private static boolean isOperator(String c) {
        switch (c) {
            case "d": case "c": case "y": case ">": case "<": case "g~": case "gu": case "gU":
                return true;
            default:
                return false;
        }
    }

    private static boolean validRegister(char c) {
        return Character.isLetterOrDigit(c) || "\"-_+*./".indexOf(c) >= 0;
    }

    // ------------------------------------------------------------------ normal mode

    private void executeNormal(String t, String reg, int n, boolean hasCount) {
        String c = t;
        if (c.equals("g") || c.equals("z") || c.equals("Z")) c = t + next();
        if (isOperator(c)) {
            operator(c, reg, n, hasCount);
            return;
        }
        Motion m = motion(c, n, hasCount, false);
        if (m != null) {
            moveTo(m);
            return;
        }
        String L = line(row);
        int len = L.length();
        switch (c) {
            case "<Esc>":
            case "<C-c>":
                return;
            case "x":
            case "<Del>": {
                if (len == 0) throw BAD;
                applyOperator("d", Range.chars(idx(row, col), idx(row, Math.min(col + n, len))), reg);
                return;
            }
            case "X": {
                if (col == 0) throw BAD;
                applyOperator("d", Range.chars(idx(row, Math.max(0, col - n)), idx(row, col)), reg);
                return;
            }
            case "s":
                applyOperator("c", Range.chars(idx(row, col), idx(row, Math.min(col + n, len))), reg);
                return;
            case "S":
                applyOperator("c", Range.lines(row, Math.min(row + n - 1, last())), reg);
                return;
            case "C":
            case "D": {
                int er = Math.min(row + n - 1, last());
                applyOperator(c.equals("C") ? "c" : "d", Range.chars(idx(row, col), idx(er, len(er))), reg);
                return;
            }
            case "Y":
                applyOperator("y", Range.lines(row, Math.min(row + n - 1, last())), reg);
                return;
            case "r": {
                String chr = charOf(next());
                if (chr == null || col + n > len) throw BAD;
                saveUndo();
                if (chr.equals("\n")) {
                    lines.set(row, L.substring(0, col));
                    lines.add(row + 1, L.substring(col + n));
                    row++;
                    col = 0;
                } else {
                    lines.set(row, L.substring(0, col) + chr.repeat(n) + L.substring(col + n));
                    col = col + n - 1;
                }
                changed = true;
                wantCol = col;
                return;
            }
            case "~": {
                if (len == 0) throw BAD;
                saveUndo();
                int end = Math.min(col + n, len);
                lines.set(row, L.substring(0, col) + transformCase(L.substring(col, end), '~') + L.substring(end));
                col = Math.min(end, len - 1);
                wantCol = col;
                changed = true;
                return;
            }
            case "J":
            case "gJ": {
                if (row >= last()) throw BAD;
                saveUndo();
                join(row, Math.min(row + Math.max(2, n) - 1, last()), c.equals("J"));
                changed = true;
                return;
            }
            case "p":
            case "P":
            case "gp":
            case "gP":
                put(reg, n, c.endsWith("P"), c.startsWith("g"));
                return;
            case "i":
            case "<Insert>":
                beginInsert(n, "i", true);
                return;
            case "a":
                beginInsert(n, "a", true);
                if (len > 0) col = Math.min(col + 1, len);
                return;
            case "I":
                col = fnb(row);
                beginInsert(n, "I", true);
                return;
            case "gI":
                col = 0;
                beginInsert(n, "I", true);
                return;
            case "A":
                beginInsert(n, "A", true);
                col = len;
                return;
            case "o":
            case "O": {
                saveUndo();
                String ind = leadingWs(L);
                int at = c.equals("o") ? row + 1 : row;
                lines.add(at, ind);
                row = at;
                col = ind.length();
                changed = true;
                beginInsert(n, c, false);
                return;
            }
            case "R":
                saveUndo();
                mode = Mode.REPLACE;
                replaced.clear();
                insertKeys.clear();
                insertEntered = true;
                return;
            case "v":
                enterVisual(Mode.VISUAL);
                return;
            case "V":
                enterVisual(Mode.VISUAL_LINE);
                return;
            case "gv": {
                if (lastVisualMode == null) throw BAD;
                mode = lastVisualMode;
                vRow = Math.min(lvr1, last());
                vCol = lvc1;
                row = Math.min(lvr2, last());
                col = lvc2;
                clampCursor();
                return;
            }
            case "u":
                noDot = true;
                for (int i = 0; i < n; i++) if (!doUndo()) break;
                return;
            case "<C-r>":
                noDot = true;
                for (int i = 0; i < n; i++) if (!doRedo()) break;
                return;
            case "U":
                noDot = true;
                message = "U (undo line) is not supported here: use u";
                return;
            case ".":
                dotRepeat(hasCount ? n : 0);
                return;
            case "q": {
                noDot = true;
                if (macroReg != null) {
                    char rc = macroReg;
                    macroReg = null;
                    if (!macroBuf.isEmpty()) macroBuf.remove(macroBuf.size() - 1);
                    String text = toNotation(macroBuf);
                    if (Character.isUpperCase(rc)) {
                        char lo = Character.toLowerCase(rc);
                        Register old = registers.get(lo);
                        registers.put(lo, new Register((old == null ? "" : old.text) + text, false));
                    } else {
                        registers.put(rc, new Register(text, false));
                    }
                    macroBuf.clear();
                    return;
                }
                String r = next();
                if (r.length() != 1 || !(Character.isLetterOrDigit(r.charAt(0)) || r.equals("\""))) throw BAD;
                macroReg = r.charAt(0);
                macroBuf.clear();
                return;
            }
            case "@": {
                noDot = true;
                String r = next();
                if (r.equals(":")) {
                    if (lastEx == null) throw BAD;
                    final String ex = lastEx;
                    final int times = n;
                    deferred.add(() -> {
                        for (int i = 0; i < times; i++) {
                            try {
                                exCommand(ex);
                            } catch (Invalid e) {
                                bell = true;
                                break;
                            }
                        }
                    });
                    return;
                }
                char rc;
                if (r.equals("@")) {
                    if (lastMacro == null) throw BAD;
                    rc = lastMacro;
                } else {
                    if (r.length() != 1) throw BAD;
                    rc = Character.toLowerCase(r.charAt(0));
                }
                Register reg2 = rc == '.' ? (lastInserted == null ? null : new Register(lastInserted, false)) : registers.get(rc);
                if (reg2 == null) throw BAD;
                lastMacro = rc;
                final List<String> toks = tokenize(reg2.text);
                final int times = n;
                deferred.add(() -> replay(toks, times));
                return;
            }
            case "m": {
                noDot = true;
                String r = next();
                if (r.length() != 1 || !Character.isLetter(r.charAt(0))) throw BAD;
                marks.put(r.charAt(0), new int[]{row, col});
                return;
            }
            case "<C-a>":
            case "<C-x>":
                increment(c.equals("<C-a>") ? n : -n);
                return;
            case "zz": case "zt": case "zb": case "z.": case "z<CR>": case "z-":
                return;
            case "ZZ":
            case "ZQ":
                message = "In real Vim " + c + " would quit. Here, keep practicing!";
                return;
            case ":": {
                noDot = true;
                String prefix = hasCount ? ".,.+" + (n - 1) : "";
                String typed = readLine(":" + prefix);
                exCommand(prefix + typed);
                return;
            }
            case "&": {
                if (lastSubPattern == null) throw BAD;
                saveUndo();
                substituteWith(row, row, lastSubPattern, lastSubReplacement, "", false);
                return;
            }
            case "g&": {
                if (lastSubPattern == null) throw BAD;
                saveUndo();
                substituteWith(0, last(), lastSubPattern, lastSubReplacement, lastSubFlags, false);
                return;
            }
            case "<C-g>":
                message = "line " + (row + 1) + " of " + lines.size() + " --" + (100 * (row + 1) / lines.size()) + "%-- col " + (col + 1);
                return;
            default:
                throw BAD;
        }
    }

    // ------------------------------------------------------------------ motions

    private Motion motion(String c, int n, boolean hasCount, boolean forOp) {
        int len = len(row);
        switch (c) {
            case "h":
            case "<Left>":
            case "<BS>":
            case "<C-h>":
                if (col == 0) throw BAD;
                return new Motion(row, Math.max(0, col - n), EXCL);
            case "l":
            case "<Right>":
            case " ": {
                int max = forOp ? len : Math.max(0, len - 1);
                if (col >= max) throw BAD;
                return new Motion(row, Math.min(col + n, max), EXCL);
            }
            case "j":
            case "<Down>":
            case "<C-n>":
            case "<C-j>": {
                if (row >= last()) throw BAD;
                Motion m = new Motion(Math.min(row + n, last()), 0, LINE);
                m.keepWant = true;
                return m;
            }
            case "k":
            case "<Up>":
            case "<C-p>": {
                if (row <= 0) throw BAD;
                Motion m = new Motion(Math.max(row - n, 0), 0, LINE);
                m.keepWant = true;
                return m;
            }
            case "+":
            case "<CR>": {
                if (row >= last()) throw BAD;
                int r = Math.min(row + n, last());
                return new Motion(r, fnb(r), LINE);
            }
            case "-": {
                if (row <= 0) throw BAD;
                int r = Math.max(row - n, 0);
                return new Motion(r, fnb(r), LINE);
            }
            case "_": {
                int r = Math.min(row + n - 1, last());
                return new Motion(r, fnb(r), LINE);
            }
            case "0":
            case "<Home>":
                return new Motion(row, 0, EXCL);
            case "^":
                return new Motion(row, fnb(row), EXCL);
            case "$":
            case "<End>": {
                int r = Math.min(row + n - 1, last());
                Motion m = new Motion(r, Math.max(0, len(r) - 1), INCL);
                m.wantEnd = true;
                return m;
            }
            case "|":
                return new Motion(row, Math.min(n - 1, Math.max(0, len - 1)), EXCL);
            case "w":
            case "W":
                return wordForward(c.equals("W"), n, forOp);
            case "b":
            case "B": {
                String f = flat();
                int i = idx(row, col);
                for (int k = 0; k < n; k++) i = wordBack(f, i, c.equals("B"));
                return motionAt(i, EXCL);
            }
            case "e":
            case "E": {
                String f = flat();
                int i = idx(row, col);
                for (int k = 0; k < n; k++) i = wordEnd(f, i, c.equals("E"));
                return motionAt(i, INCL);
            }
            case "ge":
            case "gE": {
                String f = flat();
                int i = idx(row, col);
                for (int k = 0; k < n; k++) i = wordEndBack(f, i, c.equals("gE"));
                return motionAt(i, INCL);
            }
            case "gg":
            case "G": {
                int r = hasCount ? Math.max(0, Math.min(n - 1, last())) : (c.equals("gg") ? 0 : last());
                Motion m = new Motion(r, fnb(r), LINE);
                m.jump = true;
                return m;
            }
            case "f":
            case "F":
            case "t":
            case "T": {
                String chr = charOf(next());
                if (chr == null || chr.equals("\n")) throw BAD;
                lastFindCmd = c;
                lastFindChar = chr;
                return find(c, chr, n, false);
            }
            case ";":
            case ",": {
                if (lastFindCmd == null) throw BAD;
                String cmd = lastFindCmd;
                if (c.equals(",")) {
                    switch (cmd) {
                        case "f": cmd = "F"; break;
                        case "F": cmd = "f"; break;
                        case "t": cmd = "T"; break;
                        default: cmd = "t";
                    }
                }
                return find(cmd, lastFindChar, n, true);
            }
            case "%": {
                if (hasCount) {
                    int r = Math.max(0, Math.min((n * lines.size() + 99) / 100 - 1, last()));
                    return new Motion(r, fnb(r), LINE);
                }
                String L = line(row);
                int k = col;
                while (k < L.length() && "()[]{}".indexOf(L.charAt(k)) < 0) k++;
                if (k >= L.length()) throw BAD;
                String f = flat();
                int j = matchBracket(f, idx(row, k));
                if (j < 0) throw BAD;
                Motion m = motionAt(j, INCL);
                m.jump = true;
                return m;
            }
            case "}": {
                int r = row;
                for (int k = 0; k < n; k++) {
                    while (r <= last() && isBlank(r)) r++;
                    while (r <= last() && !isBlank(r)) r++;
                }
                Motion m = r > last() ? new Motion(last(), len(last()), EXCL) : new Motion(r, 0, EXCL);
                m.jump = true;
                return m;
            }
            case "{": {
                int r = row;
                for (int k = 0; k < n; k++) {
                    while (r >= 0 && isBlank(r)) r--;
                    while (r >= 0 && !isBlank(r)) r--;
                }
                Motion m = new Motion(Math.max(r, 0), 0, EXCL);
                m.jump = true;
                return m;
            }
            case "H":
            case "M":
            case "L": {
                int top = Math.max(0, Math.min(viewTop, last()));
                int h = viewHeight <= 0 ? lines.size() : viewHeight;
                int bottom = Math.min(last(), top + h - 1);
                int r;
                if (c.equals("H")) r = Math.min(bottom, top + n - 1);
                else if (c.equals("L")) r = Math.max(top, bottom - n + 1);
                else r = (top + bottom) / 2;
                Motion m = new Motion(r, fnb(r), LINE);
                m.jump = true;
                return m;
            }
            case "<C-d>":
            case "<C-f>": {
                int r = Math.min(last(), row + (c.equals("<C-d>") ? HALF_PAGE : HALF_PAGE * 2));
                return new Motion(r, fnb(r), LINE);
            }
            case "<C-u>":
            case "<C-b>": {
                int r = Math.max(0, row - (c.equals("<C-u>") ? HALF_PAGE : HALF_PAGE * 2));
                return new Motion(r, fnb(r), LINE);
            }
            case "n":
            case "N": {
                if (lastSearch == null) {
                    message = "E35: No previous regular expression";
                    throw BAD;
                }
                hlsearch = true;
                return searchMotion(lastSearch, lastSearchForward ^ c.equals("N"), n);
            }
            case "*":
            case "#": {
                String w = wordUnderCursor();
                if (w == null) throw BAD;
                lastSearch = "\\<" + w + "\\>";
                lastSearchForward = c.equals("*");
                hlsearch = true;
                String L = line(row);
                int s = col;
                while (s < L.length() && !isWordChar(L.charAt(s))) s++;
                while (s > 0 && isWordChar(L.charAt(s - 1))) s--;
                int save = col;
                col = s;
                try {
                    return searchMotion(lastSearch, lastSearchForward, n);
                } finally {
                    col = save;
                }
            }
            case "/":
            case "?": {
                String p = readLine(c);
                if (p.isEmpty()) {
                    if (lastSearch == null) throw BAD;
                    p = lastSearch;
                }
                lastSearch = p;
                lastSearchForward = c.equals("/");
                hlsearch = true;
                return searchMotion(p, lastSearchForward, n);
            }
            case "'":
            case "`": {
                String mk = next();
                int[] p = getMark(mk);
                if (p == null) {
                    message = "E20: Mark not set";
                    throw BAD;
                }
                int r = Math.min(p[0], last());
                Motion m = c.equals("'") ? new Motion(r, fnb(r), LINE) : new Motion(r, Math.min(p[1], len(r)), EXCL);
                m.jump = true;
                return m;
            }
            default:
                return null;
        }
    }

    private Motion wordForward(boolean big, int n, boolean forOp) {
        String f = flat();
        int i = idx(row, col);
        for (int k = 0; k < n; k++) {
            int j = nextWordStart(f, i, big);
            if (forOp && k == n - 1) {
                int lineEnd = f.indexOf('\n', i);
                if (lineEnd < 0) lineEnd = f.length();
                if (j > lineEnd) j = lineEnd;
            }
            i = j;
        }
        return motionAt(i, EXCL);
    }

    /** The {@code cw} special case: change to the end of the current word. */
    private Motion changeWordMotion(boolean big, int n) {
        String f = flat();
        int i = idx(row, col);
        int c = cls(f.charAt(i), big);
        while (i + 1 < f.length() && f.charAt(i + 1) != '\n' && cls(f.charAt(i + 1), big) == c) i++;
        for (int k = 1; k < n; k++) i = wordEnd(f, i, big);
        return motionAt(i, INCL);
    }

    private Motion find(String cmd, String ch, int n, boolean repeat) {
        String L = line(row);
        boolean fwd = cmd.equals("f") || cmd.equals("t");
        boolean till = cmd.equals("t") || cmd.equals("T");
        int c = col;
        for (int k = 0; k < n; k++) {
            int start = fwd ? c + 1 : c - 1;
            if (till && repeat && k == 0) start = fwd ? c + 2 : c - 2;
            int found;
            if (fwd) found = start >= L.length() ? -1 : L.indexOf(ch, start);
            else found = start < 0 ? -1 : L.lastIndexOf(ch, start);
            if (found < 0) throw BAD;
            c = found;
        }
        if (till) c = fwd ? c - 1 : c + 1;
        return new Motion(row, c, fwd ? INCL : EXCL);
    }

    private Motion searchMotion(String pat, boolean fwd, int n) {
        Pattern p = compileOrFail(pat, false);
        String f = flat();
        int i = idx(row, col);
        for (int k = 0; k < n; k++) {
            int j = searchIdx(p, f, i, fwd);
            if (j < 0) {
                message = "E486: Pattern not found: " + pat;
                throw BAD;
            }
            i = j;
        }
        Motion m = motionAt(i, EXCL);
        m.jump = true;
        return m;
    }

    private int searchIdx(Pattern p, String f, int i, boolean fwd) {
        Matcher m = p.matcher(f);
        if (fwd) {
            int from = i + 1;
            while (from <= f.length() && m.find(from)) {
                if (m.end() > m.start() || m.start() > i) return m.start();
                from = m.start() + 1;
            }
            if (m.find(0)) {
                message = "search hit BOTTOM, continuing at TOP";
                return m.start();
            }
            return -1;
        }
        int best = -1, lastAny = -1;
        while (m.find()) {
            if (m.start() < i) best = m.start();
            lastAny = m.start();
            if (m.end() == m.start() && m.end() >= f.length()) break;
        }
        if (best >= 0) return best;
        if (lastAny >= 0) message = "search hit TOP, continuing at BOTTOM";
        return lastAny;
    }

    private void moveTo(Motion m) {
        if (m.jump) marks.put('\'', new int[]{row, col});
        row = Math.max(0, Math.min(m.r, last()));
        if (m.keepWant) {
            int len = len(row);
            col = wantCol == Integer.MAX_VALUE ? len : wantCol;
        } else {
            col = m.c;
        }
        clampCursor();
        if (!m.keepWant) wantCol = m.wantEnd ? Integer.MAX_VALUE : col;
    }

    private Motion motionAt(int index, int type) {
        int[] p = pos(index);
        return new Motion(p[0], p[1], type);
    }

    // word scanning over the flattened buffer -------------------------------------

    private static boolean isWs(char c) {
        return c == ' ' || c == '\t' || c == '\n';
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private static int cls(char c, boolean big) {
        if (isWs(c)) return 0;
        if (big) return 1;
        return isWordChar(c) ? 2 : 1;
    }

    private static int nextWordStart(String f, int i, boolean big) {
        int n = f.length();
        if (i >= n) return n;
        int c = cls(f.charAt(i), big);
        if (c != 0) while (i < n && f.charAt(i) != '\n' && cls(f.charAt(i), big) == c) i++;
        while (i < n) {
            char ch = f.charAt(i);
            if (ch == '\n') {
                i++;
                if (i < n && f.charAt(i) == '\n') return i;
                continue;
            }
            if (ch == ' ' || ch == '\t') {
                i++;
                continue;
            }
            break;
        }
        return i;
    }

    private static int wordEnd(String f, int i, boolean big) {
        int n = f.length();
        i++;
        while (i < n && isWs(f.charAt(i))) i++;
        if (i >= n) return Math.max(0, n - 1);
        int c = cls(f.charAt(i), big);
        while (i + 1 < n && f.charAt(i + 1) != '\n' && cls(f.charAt(i + 1), big) == c) i++;
        return i;
    }

    private static int wordBack(String f, int i, boolean big) {
        i--;
        while (i >= 0 && isWs(f.charAt(i))) {
            if (f.charAt(i) == '\n' && (i == 0 || f.charAt(i - 1) == '\n')) return i;
            i--;
        }
        if (i < 0) return 0;
        int c = cls(f.charAt(i), big);
        while (i - 1 >= 0 && f.charAt(i - 1) != '\n' && cls(f.charAt(i - 1), big) == c) i--;
        return i;
    }

    private static int wordEndBack(String f, int i, boolean big) {
        if (i < f.length()) {
            int c = cls(f.charAt(i), big);
            if (c != 0) while (i >= 0 && f.charAt(i) != '\n' && cls(f.charAt(i), big) == c) i--;
        }
        while (i >= 0 && isWs(f.charAt(i))) {
            if (f.charAt(i) == '\n' && (i == 0 || f.charAt(i - 1) == '\n')) return i;
            i--;
        }
        return Math.max(i, 0);
    }

    private static int matchBracket(String f, int i) {
        char ch = f.charAt(i);
        String opens = "([{", closes = ")]}";
        int oi = opens.indexOf(ch), ci = closes.indexOf(ch);
        if (oi >= 0) {
            char cl = closes.charAt(oi);
            int depth = 0;
            for (int k = i + 1; k < f.length(); k++) {
                char x = f.charAt(k);
                if (x == ch) depth++;
                else if (x == cl) {
                    if (depth == 0) return k;
                    depth--;
                }
            }
        } else if (ci >= 0) {
            char op = opens.charAt(ci);
            int depth = 0;
            for (int k = i - 1; k >= 0; k--) {
                char x = f.charAt(k);
                if (x == ch) depth++;
                else if (x == op) {
                    if (depth == 0) return k;
                    depth--;
                }
            }
        }
        return -1;
    }

    private String wordUnderCursor() {
        String L = line(row);
        int c = col;
        while (c < L.length() && !isWordChar(L.charAt(c))) c++;
        if (c >= L.length()) return null;
        int s = c, e = c;
        while (s > 0 && isWordChar(L.charAt(s - 1))) s--;
        while (e < L.length() && isWordChar(L.charAt(e))) e++;
        return L.substring(s, e);
    }

    // ------------------------------------------------------------------ operators

    private void operator(String op, String reg, int n1, boolean hc1) {
        String t = next();
        int c2 = 0;
        boolean hc2 = false;
        while (isDigit(t) && (hc2 || !t.equals("0"))) {
            c2 = Math.min(c2 * 10 + (t.charAt(0) - '0'), 99999);
            hc2 = true;
            t = next();
        }
        int n = n1 * (hc2 ? c2 : 1);
        boolean hasCount = hc1 || hc2;
        String lastCh = op.substring(op.length() - 1);
        Range range;
        String mc = t;
        if (t.equals("g") && !op.equals("g")) mc = "g" + next();
        if (t.equals(lastCh) || (op.length() == 2 && mc.equals("g" + lastCh))) {
            range = Range.lines(row, Math.min(row + n - 1, last()));
        } else if (t.equals("i") || t.equals("a")) {
            range = textObject(t.equals("i"), next(), n);
            if (range == null) throw BAD;
        } else {
            Motion m;
            if (op.equals("c") && (mc.equals("w") || mc.equals("W")) && col < len(row) && !isWs(line(row).charAt(col))) {
                m = changeWordMotion(mc.equals("W"), n);
            } else {
                m = motion(mc, n, hasCount, true);
            }
            if (m == null) throw BAD;
            range = rangeFrom(m);
        }
        applyOperator(op, range, reg);
    }

    private Range rangeFrom(Motion m) {
        int sr = row, sc = col, er = m.r, ec = m.c;
        if (m.type == LINE) return Range.lines(sr, er);
        if (er < sr || (er == sr && ec < sc)) {
            int t = sr; sr = er; er = t;
            t = sc; sc = ec; ec = t;
        }
        sc = Math.min(sc, len(sr));
        int from = idx(sr, sc);
        int to;
        if (m.type == EXCL) {
            if (er > sr && ec == 0) {
                if (sc <= fnb(sr) || len(sr) == 0) return Range.lines(sr, er - 1);
                to = idx(er, 0) - 1;
            } else {
                to = idx(er, Math.min(ec, len(er)));
            }
        } else {
            to = idx(er, Math.min(ec + 1, len(er)));
        }
        return Range.chars(from, Math.max(from, to));
    }

    private void applyOperator(String op, Range r, String reg) {
        switch (op) {
            case "y": {
                if (r.empty()) return;
                storeYank(reg, textOf(r), r.linewise);
                if (r.linewise) {
                    if (r.sr < row) row = r.sr;
                    int nl = r.er - r.sr + 1;
                    if (nl > 2) message = nl + " lines yanked";
                } else {
                    int[] p = pos(r.from);
                    row = p[0];
                    col = p[1];
                }
                clampCursor();
                wantCol = col;
                return;
            }
            case "d": {
                if (r.empty()) return;
                saveUndo();
                storeDelete(reg, textOf(r), r.linewise);
                deleteRange(r);
                changed = true;
                if (r.linewise && r.er - r.sr + 1 > 2) message = (r.er - r.sr + 1) + " fewer lines";
                return;
            }
            case "c": {
                saveUndo();
                if (r.linewise) {
                    storeDelete(reg, textOf(r), true);
                    String indent = leadingWs(line(r.sr));
                    for (int k = r.er; k > r.sr; k--) lines.remove(k);
                    lines.set(r.sr, indent);
                    row = r.sr;
                    col = indent.length();
                } else {
                    if (!r.empty()) storeDelete(reg, textOf(r), false);
                    String f = flat();
                    setFlat(f.substring(0, r.from) + f.substring(r.to));
                    int[] p = pos(r.from);
                    row = p[0];
                    col = p[1];
                }
                changed = true;
                beginInsert(1, "c", false);
                return;
            }
            case ">":
            case "<": {
                int sr = r.linewise ? r.sr : pos(r.from)[0];
                int er = r.linewise ? r.er : pos(Math.max(r.from, r.to - 1))[0];
                saveUndo();
                shift(sr, er, op.equals(">") ? 1 : -1);
                row = sr;
                col = fnb(sr);
                wantCol = col;
                changed = true;
                return;
            }
            default: {
                char kind = op.charAt(op.length() - 1);
                if (r.empty()) return;
                saveUndo();
                if (r.linewise) {
                    for (int k = r.sr; k <= r.er; k++) lines.set(k, transformCase(lines.get(k), kind));
                    if (row != r.sr) col = 0;
                    row = r.sr;
                } else {
                    String f = flat();
                    setFlat(f.substring(0, r.from) + transformCase(f.substring(r.from, r.to), kind) + f.substring(r.to));
                    int[] p = pos(r.from);
                    row = p[0];
                    col = p[1];
                }
                clampCursor();
                wantCol = col;
                changed = true;
            }
        }
    }

    private void deleteRange(Range r) {
        if (r.linewise) {
            for (int k = r.er; k >= r.sr; k--) lines.remove(k);
            if (lines.isEmpty()) lines.add("");
            row = Math.min(r.sr, last());
            col = fnb(row);
        } else {
            String f = flat();
            setFlat(f.substring(0, r.from) + f.substring(r.to));
            int[] p = pos(r.from);
            row = p[0];
            col = p[1];
        }
        clampCursor();
        wantCol = col;
    }

    private String textOf(Range r) {
        if (r.linewise) return String.join("\n", lines.subList(r.sr, r.er + 1)) + "\n";
        return flat().substring(r.from, r.to);
    }

    private static String transformCase(String s, char kind) {
        if (kind == 'u') return s.toLowerCase();
        if (kind == 'U') return s.toUpperCase();
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            if (Character.isUpperCase(c)) sb.append(Character.toLowerCase(c));
            else if (Character.isLowerCase(c)) sb.append(Character.toUpperCase(c));
            else sb.append(c);
        }
        return sb.toString();
    }

    private void shift(int sr, int er, int dir) {
        for (int k = sr; k <= er; k++) {
            String L = lines.get(k);
            if (dir > 0) {
                if (!L.isEmpty()) lines.set(k, " ".repeat(SHIFT_WIDTH) + L);
            } else {
                int rm = 0;
                if (L.startsWith("\t")) rm = 1;
                else while (rm < SHIFT_WIDTH && rm < L.length() && L.charAt(rm) == ' ') rm++;
                lines.set(k, L.substring(rm));
            }
        }
    }

    private void join(int sr, int er, boolean spaces) {
        StringBuilder sb = new StringBuilder(lines.get(sr));
        int joinCol = 0;
        for (int k = sr + 1; k <= er; k++) {
            String nx = lines.get(k);
            if (spaces) {
                String nt = nx.replaceFirst("^[ \t]+", "");
                int sl = sb.length();
                if (nt.isEmpty()) {
                    joinCol = Math.max(0, sl - 1);
                } else if (sl == 0 || sb.charAt(sl - 1) == ' ' || sb.charAt(sl - 1) == '\t' || nt.startsWith(")")) {
                    joinCol = sl;
                    sb.append(nt);
                } else {
                    joinCol = sl;
                    sb.append(' ').append(nt);
                }
            } else {
                joinCol = Math.max(0, sb.length() - 1);
                sb.append(nx);
            }
        }
        lines.set(sr, sb.toString());
        for (int k = er; k > sr; k--) lines.remove(k);
        row = sr;
        col = joinCol;
        clampCursor();
        wantCol = col;
    }

    private void put(String reg, int n, boolean before, boolean gp) {
        Register r = regFor(reg);
        if (r == null || r.text.isEmpty()) {
            message = "E353: Nothing in register " + (reg == null ? "\"" : reg);
            throw BAD;
        }
        saveUndo();
        if (r.linewise) {
            String t = r.text.endsWith("\n") ? r.text.substring(0, r.text.length() - 1) : r.text;
            List<String> one = splitLines(t);
            List<String> all = new ArrayList<>();
            for (int i = 0; i < n; i++) all.addAll(one);
            int at = before ? row : row + 1;
            lines.addAll(at, all);
            if (gp) {
                row = Math.min(at + all.size(), last());
                col = 0;
            } else {
                row = at;
                col = fnb(at);
            }
        } else {
            String text = r.text.repeat(n);
            String f = flat();
            int at = idx(row, col) + ((before || len(row) == 0) ? 0 : 1);
            setFlat(f.substring(0, at) + text + f.substring(at));
            int[] p;
            if (gp) p = pos(at + text.length());
            else if (text.contains("\n")) p = pos(at);
            else p = pos(at + text.length() - 1);
            row = p[0];
            col = p[1];
        }
        changed = true;
        clampCursor();
        wantCol = col;
    }

    // registers -------------------------------------------------------------------

    private Register regFor(String reg) {
        char ch = reg == null ? '"' : reg.charAt(0);
        if (ch == '.') return lastInserted == null ? null : new Register(lastInserted, false);
        if (ch == '/') return lastSearch == null ? null : new Register(lastSearch, false);
        return registers.get(Character.toLowerCase(ch));
    }

    private void setNamed(char ch, Register r) {
        if (Character.isUpperCase(ch)) {
            char lo = Character.toLowerCase(ch);
            Register old = registers.get(lo);
            if (old == null) {
                registers.put(lo, r);
            } else {
                boolean lw = old.linewise || r.linewise;
                String a = old.text;
                if (lw && !a.endsWith("\n")) a += "\n";
                String b = r.text;
                if (lw && !b.endsWith("\n")) b += "\n";
                registers.put(lo, new Register(a + b, lw));
            }
        } else {
            registers.put(ch, r);
        }
    }

    private void storeYank(String reg, String text, boolean linewise) {
        if ("_".equals(reg)) return;
        Register r = new Register(text, linewise);
        if (reg != null && !reg.equals("\"")) setNamed(reg.charAt(0), r);
        else registers.put('0', r);
        registers.put('"', reg != null && Character.isUpperCase(reg.charAt(0)) ? registers.get(Character.toLowerCase(reg.charAt(0))) : r);
    }

    private void storeDelete(String reg, String text, boolean linewise) {
        if ("_".equals(reg)) return;
        Register r = new Register(text, linewise);
        if (reg != null && !reg.equals("\"")) {
            setNamed(reg.charAt(0), r);
        } else if (linewise || text.contains("\n")) {
            for (char c = '9'; c > '1'; c--) {
                Register prev = registers.get((char) (c - 1));
                if (prev != null) registers.put(c, prev);
            }
            registers.put('1', r);
        } else {
            registers.put('-', r);
        }
        registers.put('"', reg != null && Character.isUpperCase(reg.charAt(0)) ? registers.get(Character.toLowerCase(reg.charAt(0))) : r);
    }

    // ------------------------------------------------------------------ text objects

    private Range textObject(boolean inner, String obj, int n) {
        switch (obj) {
            case "w":
                return wordObject(inner, false, n);
            case "W":
                return wordObject(inner, true, n);
            case "\"":
            case "'":
            case "`":
                return quoteObject(inner, obj.charAt(0));
            case "(":
            case ")":
            case "b":
                return bracketObject(inner, '(', ')', n);
            case "{":
            case "}":
            case "B":
                return bracketObject(inner, '{', '}', n);
            case "[":
            case "]":
                return bracketObject(inner, '[', ']', n);
            case "<":
            case ">":
                return bracketObject(inner, '<', '>', n);
            case "p":
                return paragraphObject(inner, n);
            default:
                throw BAD;
        }
    }

    private Range wordObject(boolean inner, boolean big, int n) {
        String L = line(row);
        int len = L.length();
        if (len == 0) return null;
        int c = Math.min(col, len - 1);
        int k = cls(L.charAt(c), big);
        int s = c, e = c;
        while (s > 0 && cls(L.charAt(s - 1), big) == k) s--;
        while (e + 1 < len && cls(L.charAt(e + 1), big) == k) e++;
        for (int i = 1; i < n && e + 1 < len; i++) {
            int k2 = cls(L.charAt(e + 1), big);
            e++;
            while (e + 1 < len && cls(L.charAt(e + 1), big) == k2) e++;
        }
        if (!inner) {
            if (k != 0) {
                if (e + 1 < len && isWs(L.charAt(e + 1))) {
                    while (e + 1 < len && isWs(L.charAt(e + 1))) e++;
                } else {
                    while (s > 0 && isWs(L.charAt(s - 1))) s--;
                }
            } else if (e + 1 < len) {
                int k2 = cls(L.charAt(e + 1), big);
                e++;
                while (e + 1 < len && cls(L.charAt(e + 1), big) == k2) e++;
            }
        }
        int base = idx(row, 0);
        return Range.chars(base + s, base + e + 1);
    }

    private Range quoteObject(boolean inner, char q) {
        String L = line(row);
        List<Integer> qs = new ArrayList<>();
        for (int i = 0; i < L.length(); i++) {
            if (L.charAt(i) == q && (i == 0 || L.charAt(i - 1) != '\\')) qs.add(i);
        }
        int c = col, open = -1, close = -1;
        int at = qs.indexOf(c);
        if (at >= 0) {
            if (at % 2 == 0) {
                if (at + 1 < qs.size()) {
                    open = c;
                    close = qs.get(at + 1);
                }
            } else {
                open = qs.get(at - 1);
                close = c;
            }
        } else {
            Integer before = null, after = null;
            for (int p : qs) {
                if (p < c) before = p;
                else if (p > c && after == null) after = p;
            }
            if (before != null && after != null) {
                open = before;
                close = after;
            } else if (before == null && after != null) {
                int ai = qs.indexOf(after);
                if (ai + 1 < qs.size()) {
                    open = after;
                    close = qs.get(ai + 1);
                }
            }
        }
        if (open < 0) return null;
        int base = idx(row, 0);
        if (inner) return Range.chars(base + open + 1, base + close);
        int s = open, e = close + 1;
        if (e < L.length() && isWs(L.charAt(e))) {
            while (e < L.length() && isWs(L.charAt(e))) e++;
        } else {
            while (s > 0 && isWs(L.charAt(s - 1))) s--;
        }
        return Range.chars(base + s, base + e);
    }

    private Range bracketObject(boolean inner, char o, char cl, int n) {
        String f = flat();
        int ci = idx(row, Math.min(col, len(row)));
        int open = -1, close = -1;
        int start = ci;
        boolean atCursor = true;
        for (int k = 0; k < n; k++) {
            open = findOpen(f, start, o, cl, atCursor);
            if (open < 0) return null;
            close = findClose(f, open, o, cl);
            if (close < 0) return null;
            start = open - 1;
            atCursor = false;
        }
        if (!inner) return Range.chars(open, close + 1);
        int from = open + 1, to = close;
        if (from < f.length() && f.charAt(from) == '\n') {
            int ls = f.lastIndexOf('\n', close - 1) + 1;
            if (f.substring(ls, close).trim().isEmpty()) {
                int sr = pos(open)[0] + 1, er = pos(close)[0] - 1;
                if (sr <= er) return Range.lines(sr, er);
                return Range.chars(from, from);
            }
            from++;
        }
        return Range.chars(from, to);
    }

    private static int findOpen(String f, int i, char o, char cl, boolean atCursor) {
        int k = i;
        if (atCursor && k >= 0 && k < f.length()) {
            if (f.charAt(k) == o) return k;
            if (f.charAt(k) == cl) k--;
        }
        int depth = 0;
        for (; k >= 0; k--) {
            char ch = f.charAt(k);
            if (ch == cl) depth++;
            else if (ch == o) {
                if (depth == 0) return k;
                depth--;
            }
        }
        return -1;
    }

    private static int findClose(String f, int open, char o, char cl) {
        int depth = 0;
        for (int k = open + 1; k < f.length(); k++) {
            char ch = f.charAt(k);
            if (ch == o) depth++;
            else if (ch == cl) {
                if (depth == 0) return k;
                depth--;
            }
        }
        return -1;
    }

    private Range paragraphObject(boolean inner, int n) {
        boolean b = isBlank(row);
        int s = row, e = row;
        while (s > 0 && isBlank(s - 1) == b) s--;
        while (e < last() && isBlank(e + 1) == b) e++;
        for (int i = 1; i < n && e < last(); i++) {
            boolean b2 = isBlank(e + 1);
            e++;
            while (e < last() && isBlank(e + 1) == b2) e++;
        }
        if (!inner) {
            if (e < last()) {
                boolean b2 = isBlank(e + 1);
                e++;
                while (e < last() && isBlank(e + 1) == b2) e++;
            } else if (s > 0) {
                boolean b2 = isBlank(s - 1);
                s--;
                while (s > 0 && isBlank(s - 1) == b2) s--;
            }
        }
        return Range.lines(s, e);
    }

    // ------------------------------------------------------------------ insert / replace

    private void beginInsert(int count, String kind, boolean save) {
        if (save) saveUndo();
        mode = Mode.INSERT;
        insertCount = Math.max(1, count);
        insertKind = kind;
        insertKeys.clear();
        insertEntered = true;
        ctrlRPending = false;
    }

    private void feedInsert(String k) {
        if (recordingDot != null) recordingDot.rest.add(k);
        if (k.equals("<Esc>") || k.equals("<C-c>")) {
            endInsert();
            return;
        }
        insertKeys.add(k);
        insertKey(k);
    }

    private void endInsert() {
        List<String> keysCopy = new ArrayList<>(insertKeys);
        for (int i = 1; i < insertCount && i < 1000; i++) {
            if (insertKind.equals("o") || insertKind.equals("O")) insertKey("<CR>");
            for (String k : keysCopy) insertKey(k);
        }
        StringBuilder sb = new StringBuilder();
        for (String k : keysCopy) {
            if (k.length() == 1) sb.append(k);
            else if (k.equals("<CR>")) sb.append('\n');
            else if (k.equals("<BS>") && sb.length() > 0) sb.setLength(sb.length() - 1);
        }
        lastInserted = sb.toString();
        mode = Mode.NORMAL;
        if (col > 0) col--;
        clampCursor();
        wantCol = col;
        if (recordingDot != null) {
            lastDot = recordingDot;
            recordingDot = null;
        }
    }

    private void insertKey(String k) {
        if (ctrlRPending) {
            ctrlRPending = false;
            if (k.length() == 1) {
                Register r = regFor(k);
                if (r != null) insertText(r.text);
            }
            return;
        }
        String L = line(row);
        switch (k) {
            case "<CR>":
            case "<C-m>":
            case "<C-j>": {
                String before = L.substring(0, col), after = L.substring(col).replaceFirst("^[ \t]+", "");
                String indent = leadingWs(before);
                if (before.trim().isEmpty()) before = "";
                lines.set(row, before);
                lines.add(row + 1, indent + after);
                row++;
                col = indent.length();
                return;
            }
            case "<BS>":
            case "<C-h>":
                if (col > 0) {
                    lines.set(row, L.substring(0, col - 1) + L.substring(col));
                    col--;
                } else if (row > 0) {
                    int pl = len(row - 1);
                    lines.set(row - 1, lines.get(row - 1) + L);
                    lines.remove(row);
                    row--;
                    col = pl;
                }
                return;
            case "<Del>":
                if (col < L.length()) lines.set(row, L.substring(0, col) + L.substring(col + 1));
                else if (row < last()) {
                    lines.set(row, L + lines.get(row + 1));
                    lines.remove(row + 1);
                }
                return;
            case "<Tab>":
                insertText(" ".repeat(SHIFT_WIDTH - (col % SHIFT_WIDTH)));
                return;
            case "<C-w>": {
                if (col == 0) {
                    insertKey("<BS>");
                    return;
                }
                int c = col;
                while (c > 0 && isWs(L.charAt(c - 1))) c--;
                if (c > 0) {
                    int k2 = cls(L.charAt(c - 1), false);
                    while (c > 0 && !isWs(L.charAt(c - 1)) && cls(L.charAt(c - 1), false) == k2) c--;
                }
                lines.set(row, L.substring(0, c) + L.substring(col));
                col = c;
                return;
            }
            case "<C-u>": {
                int s = Math.min(leadingWs(L).length(), col);
                if (s == col) s = 0;
                lines.set(row, L.substring(0, s) + L.substring(col));
                col = s;
                return;
            }
            case "<C-t>":
                shift(row, row, 1);
                col += line(row).length() - L.length();
                return;
            case "<C-d>":
                shift(row, row, -1);
                col = Math.max(0, col + line(row).length() - L.length());
                return;
            case "<C-r>":
                ctrlRPending = true;
                return;
            case "<Left>":
                if (col > 0) col--;
                return;
            case "<Right>":
                if (col < L.length()) col++;
                return;
            case "<Up>":
                if (row > 0) row--;
                col = Math.min(col, len(row));
                return;
            case "<Down>":
                if (row < last()) row++;
                col = Math.min(col, len(row));
                return;
            case "<Home>":
                col = 0;
                return;
            case "<End>":
                col = L.length();
                return;
            default:
                if (k.length() == 1) insertText(k);
        }
    }

    private void insertText(String s) {
        String f = flat();
        int at = idx(row, col);
        setFlat(f.substring(0, at) + s + f.substring(at));
        int[] p = pos(at + s.length());
        row = p[0];
        col = p[1];
    }

    private void feedReplace(String k) {
        if (recordingDot != null) recordingDot.rest.add(k);
        if (k.equals("<Esc>") || k.equals("<C-c>")) {
            mode = Mode.NORMAL;
            if (col > 0) col--;
            clampCursor();
            wantCol = col;
            if (recordingDot != null) {
                lastDot = recordingDot;
                recordingDot = null;
            }
            return;
        }
        String L = line(row);
        if (k.equals("<BS>")) {
            if (col > 0) {
                col--;
                if (!replaced.isEmpty()) {
                    char orig = replaced.pop();
                    L = line(row);
                    if (orig == 0) lines.set(row, L.substring(0, col) + L.substring(col + 1));
                    else lines.set(row, L.substring(0, col) + orig + L.substring(col + 1));
                }
            }
            return;
        }
        if (k.equals("<CR>")) {
            lines.set(row, L.substring(0, col));
            lines.add(row + 1, L.substring(col));
            row++;
            col = 0;
            replaced.clear();
            return;
        }
        String chr = k.equals("<Tab>") ? "\t" : (k.length() == 1 ? k : null);
        if (chr == null) return;
        if (col < L.length()) {
            replaced.push(L.charAt(col));
            lines.set(row, L.substring(0, col) + chr + L.substring(col + 1));
        } else {
            replaced.push((char) 0);
            lines.set(row, L + chr);
        }
        col++;
    }

    // ------------------------------------------------------------------ visual mode

    private void enterVisual(Mode m) {
        mode = m;
        vRow = row;
        vCol = col;
    }

    private void exitVisual() {
        int[] b = getVisualBounds();
        if (b != null) {
            lastVisualMode = mode;
            lvr1 = vRow;
            lvc1 = vCol;
            lvr2 = row;
            lvc2 = col;
            marks.put('<', new int[]{b[0], b[4] == 1 ? 0 : b[1]});
            marks.put('>', new int[]{b[2], b[4] == 1 ? Math.max(0, len(b[2]) - 1) : b[3]});
        }
        mode = Mode.NORMAL;
        clampCursor();
    }

    private Range vRange() {
        int[] b = getVisualBounds();
        if (b[4] == 1) return Range.lines(b[0], b[2]);
        int from = idx(b[0], Math.min(b[1], len(b[0])));
        int to = idx(b[2], Math.min(b[3], len(b[2]))) + 1;
        return Range.chars(from, Math.min(to, flat().length()));
    }

    private Range toLinewise(Range r) {
        if (r.linewise) return r;
        return Range.lines(pos(r.from)[0], pos(Math.max(r.from, r.to - 1))[0]);
    }

    private void executeVisual(String t, String reg, int n, boolean hasCount) {
        String c = t;
        if (c.equals("g") || c.equals("z")) c = t + next();
        switch (c) {
            case "<Esc>":
            case "<C-c>":
                exitVisual();
                return;
            case "v":
                if (mode == Mode.VISUAL) exitVisual();
                else mode = Mode.VISUAL;
                return;
            case "V":
                if (mode == Mode.VISUAL_LINE) exitVisual();
                else mode = Mode.VISUAL_LINE;
                return;
            case "o":
            case "O": {
                int tr = vRow, tc = vCol;
                vRow = row;
                vCol = col;
                row = tr;
                col = tc;
                return;
            }
            case "i":
            case "a": {
                Range r = textObject(c.equals("i"), next(), n);
                if (r == null) throw BAD;
                if (r.linewise) {
                    mode = Mode.VISUAL_LINE;
                    vRow = r.sr;
                    vCol = 0;
                    row = r.er;
                    col = 0;
                } else {
                    if (r.to <= r.from) throw BAD;
                    int[] a = pos(r.from), b = pos(r.to - 1);
                    vRow = a[0];
                    vCol = a[1];
                    row = b[0];
                    col = b[1];
                }
                return;
            }
            case "d": case "x": case "<Del>":
                vOp("d", reg, false);
                return;
            case "X": case "D":
                vOp("d", reg, true);
                return;
            case "y":
                vOp("y", reg, false);
                return;
            case "Y":
                vOp("y", reg, true);
                return;
            case "c": case "s":
                vOp("c", reg, false);
                return;
            case "C": case "S": case "R":
                vOp("c", reg, true);
                return;
            case "~": case "g~":
                vOp("g~", reg, false);
                return;
            case "u": case "gu":
                vOp("gu", reg, false);
                return;
            case "U": case "gU":
                vOp("gU", reg, false);
                return;
            case ">":
            case "<": {
                Range r = toLinewise(vRange());
                exitVisual();
                saveUndo();
                for (int i = 0; i < n; i++) shift(r.sr, r.er, c.equals(">") ? 1 : -1);
                row = r.sr;
                col = fnb(row);
                wantCol = col;
                changed = true;
                return;
            }
            case "J":
            case "gJ": {
                Range r = toLinewise(vRange());
                int er = r.er == r.sr ? Math.min(r.sr + 1, last()) : r.er;
                exitVisual();
                if (er == r.sr) throw BAD;
                saveUndo();
                join(r.sr, er, c.equals("J"));
                changed = true;
                return;
            }
            case "r": {
                String chr = charOf(next());
                if (chr == null || chr.equals("\n")) throw BAD;
                Range r = vRange();
                exitVisual();
                saveUndo();
                if (r.linewise) {
                    for (int k = r.sr; k <= r.er; k++) lines.set(k, chr.repeat(lines.get(k).length()));
                    row = r.sr;
                    col = 0;
                } else {
                    String f = flat();
                    StringBuilder mid = new StringBuilder();
                    for (char ch : f.substring(r.from, r.to).toCharArray()) mid.append(ch == '\n' ? "\n" : chr);
                    setFlat(f.substring(0, r.from) + mid + f.substring(r.to));
                    int[] p = pos(r.from);
                    row = p[0];
                    col = p[1];
                }
                clampCursor();
                changed = true;
                return;
            }
            case "p":
            case "P":
                visualPut(reg);
                return;
            case "I":
            case "A": {
                Range r = vRange();
                exitVisual();
                if (r.linewise) {
                    row = c.equals("I") ? r.sr : r.er;
                    col = c.equals("I") ? fnb(row) : len(row);
                } else {
                    int[] p = c.equals("I") ? pos(r.from) : pos(Math.min(r.to, flat().length()));
                    row = p[0];
                    col = p[1];
                }
                beginInsert(1, "i", true);
                return;
            }
            case ":":
                exitVisual();
                deferred.add(() -> {
                    for (String k : tokenize(":'<,'>")) feed(k);
                });
                return;
            default: {
                Motion m = motion(c, n, hasCount, false);
                if (m == null) throw BAD;
                moveTo(m);
            }
        }
    }

    private void vOp(String op, String reg, boolean forceLine) {
        Range r = vRange();
        if (forceLine) r = toLinewise(r);
        exitVisual();
        applyOperator(op, r, reg);
    }

    private void visualPut(String reg) {
        Register r = regFor(reg);
        if (r == null) throw BAD;
        Range sel = vRange();
        exitVisual();
        saveUndo();
        String deleted = textOf(sel);
        if (sel.linewise) {
            for (int k = sel.er; k >= sel.sr; k--) lines.remove(k);
            String t = r.text.endsWith("\n") ? r.text.substring(0, r.text.length() - 1) : r.text;
            lines.addAll(Math.min(sel.sr, lines.size()), splitLines(t));
            row = sel.sr;
            col = fnb(row);
        } else {
            String f = flat();
            String text = r.linewise ? "\n" + (r.text.endsWith("\n") ? r.text.substring(0, r.text.length() - 1) : r.text) + "\n" : r.text;
            setFlat(f.substring(0, sel.from) + text + f.substring(sel.to));
            int[] p = r.linewise ? pos(sel.from + 1) : pos(sel.from + Math.max(0, text.length() - 1));
            row = p[0];
            col = p[1];
        }
        storeDelete(null, deleted, sel.linewise);
        changed = true;
        clampCursor();
        wantCol = col;
    }

    // ------------------------------------------------------------------ undo / dot / numbers

    private void saveUndo() {
        if (suppressUndo > 0) return;
        if (!undo.isEmpty() && undo.peek().lines.equals(lines)) return;
        undo.push(new Snapshot(new ArrayList<>(lines), row, col));
        if (undo.size() > 1000) undo.removeLast();
        redo.clear();
    }

    private boolean doUndo() {
        while (!undo.isEmpty() && undo.peek().lines.equals(lines)) undo.pop();
        if (undo.isEmpty()) {
            message = "Already at oldest change";
            return false;
        }
        Snapshot s = undo.pop();
        redo.push(new Snapshot(new ArrayList<>(lines), row, col));
        lines = new ArrayList<>(s.lines);
        row = s.row;
        col = s.col;
        clampCursor();
        wantCol = col;
        return true;
    }

    private boolean doRedo() {
        if (redo.isEmpty()) {
            message = "Already at newest change";
            return false;
        }
        Snapshot s = redo.pop();
        undo.push(new Snapshot(new ArrayList<>(lines), row, col));
        lines = new ArrayList<>(s.lines);
        row = s.row;
        col = s.col;
        clampCursor();
        wantCol = col;
        return true;
    }

    private void dotRepeat(int newCount) {
        noDot = true;
        if (lastDot == null) throw BAD;
        List<String> toks = new ArrayList<>(lastDot.reg);
        int cnt = newCount > 0 ? newCount : lastDot.count;
        if (cnt > 0) for (char ch : Integer.toString(cnt).toCharArray()) toks.add(String.valueOf(ch));
        toks.addAll(lastDot.rest);
        deferred.add(() -> replay(toks, 1));
    }

    private void increment(int delta) {
        String L = line(row);
        Matcher m = Pattern.compile("\\d+").matcher(L);
        while (m.find()) {
            if (m.end() <= col) continue;
            int s = m.start();
            if (s > 0 && L.charAt(s - 1) == '-' && (s - 1 == 0 || !isWordChar(L.charAt(s - 2)))) s--;
            long v;
            try {
                v = Long.parseLong(L.substring(s, m.end())) + delta;
            } catch (NumberFormatException e) {
                throw BAD;
            }
            saveUndo();
            String num = Long.toString(v);
            lines.set(row, L.substring(0, s) + num + L.substring(m.end()));
            col = s + num.length() - 1;
            wantCol = col;
            changed = true;
            return;
        }
        throw BAD;
    }

    // ------------------------------------------------------------------ Ex commands

    private Invalid exErr(String msg) {
        message = msg;
        return BAD;
    }

    private void exCommand(String cmd) {
        lastEx = cmd;
        String s = stripLeading(cmd);
        while (s.startsWith(":")) s = stripLeading(s.substring(1));
        if (s.trim().isEmpty()) return;
        int[] p = {0};
        int a1 = -1, a2 = -1;
        boolean hasRange = false;
        if (s.charAt(0) == '%') {
            a1 = 0;
            a2 = last();
            p[0] = 1;
            hasRange = true;
        } else {
            Integer x = parseAddress(s, p);
            if (x != null) {
                hasRange = true;
                a1 = x;
                a2 = x;
                if (p[0] < s.length() && (s.charAt(p[0]) == ',' || s.charAt(p[0]) == ';')) {
                    p[0]++;
                    Integer y = parseAddress(s, p);
                    if (y == null) throw exErr("E14: Invalid address");
                    a2 = y;
                }
            }
        }
        if (a1 > a2) {
            int t = a1; a1 = a2; a2 = t;
        }
        String rest = stripLeading(s.substring(p[0]));
        if (rest.trim().isEmpty()) {
            if (hasRange) {
                marks.put('\'', new int[]{row, col});
                row = Math.max(0, Math.min(a2, last()));
                col = fnb(row);
                wantCol = col;
            }
            return;
        }
        int k = 0;
        while (k < rest.length() && Character.isLetter(rest.charAt(k))) k++;
        String name = k == 0 ? rest.substring(0, 1) : rest.substring(0, k);
        String arg = rest.substring(k == 0 ? 1 : k);
        // ":s" family may be followed directly by a delimiter like ":s#a#b#"
        if (name.length() > 1 && name.startsWith("s") && !isKnownCommand(name)) {
            name = "s";
            arg = rest.substring(1);
        }
        boolean bang = arg.startsWith("!");
        if (bang && !name.equals("s")) arg = arg.substring(1);
        boolean needsLines = !name.equals("m") && !name.equals("t");
        if (hasRange && needsLines && (a1 < 0 || a2 > last())) throw exErr("E16: Invalid range");
        int r1 = hasRange ? a1 : row, r2 = hasRange ? a2 : row;
        switch (name) {
            case "s": case "su": case "substitute":
                substitute(r1, r2, arg);
                return;
            case "&": case "&&": {
                if (lastSubPattern == null) throw exErr("E35: No previous regular expression");
                saveUndo();
                substituteWith(r1, r2, lastSubPattern, lastSubReplacement, name.equals("&&") || arg.contains("&") ? lastSubFlags : arg.trim(), false);
                return;
            }
            case "d": case "de": case "del": case "delete": {
                saveUndo();
                Range r = Range.lines(r1, r2);
                String reg = arg.trim().isEmpty() ? null : arg.trim().substring(0, 1);
                storeDelete(reg, textOf(r), true);
                deleteRange(r);
                changed = true;
                return;
            }
            case "y": case "ya": case "yank": {
                String reg = arg.trim().isEmpty() ? null : arg.trim().substring(0, 1);
                storeYank(reg, textOf(Range.lines(r1, r2)), true);
                return;
            }
            case "m": case "mo": case "move": {
                if (r1 < 0 || r2 > last()) throw exErr("E16: Invalid range");
                Integer dest = parseAddress(arg.trim(), new int[]{0});
                if (dest == null || dest < -1 || dest > last()) throw exErr("E14: Invalid address");
                if (dest >= r1 && dest < r2) throw exErr("E134: Cannot move a range of lines into itself");
                if (dest >= r1 && dest <= r2) return;
                saveUndo();
                List<String> block = new ArrayList<>(lines.subList(r1, r2 + 1));
                for (int i = r2; i >= r1; i--) lines.remove(i);
                int d = dest > r2 ? dest - block.size() : dest;
                lines.addAll(d + 1, block);
                row = d + block.size();
                col = fnb(row);
                changed = true;
                return;
            }
            case "t": case "co": case "copy": {
                if (r1 < 0 || r2 > last()) throw exErr("E16: Invalid range");
                Integer dest = parseAddress(arg.trim(), new int[]{0});
                if (dest == null || dest < -1 || dest > last()) throw exErr("E14: Invalid address");
                saveUndo();
                List<String> block = new ArrayList<>(lines.subList(r1, r2 + 1));
                lines.addAll(dest + 1, block);
                row = dest + block.size();
                col = fnb(row);
                changed = true;
                return;
            }
            case "j": case "join": {
                if (!hasRange || r1 == r2) r2 = Math.min(r1 + 1, last());
                if (r2 == r1) throw BAD;
                saveUndo();
                join(r1, r2, !bang);
                changed = true;
                return;
            }
            case ">":
            case "<": {
                int times = 1;
                for (char ch : arg.toCharArray()) if (ch == name.charAt(0)) times++;
                saveUndo();
                for (int i = 0; i < times; i++) shift(r1, r2, name.equals(">") ? 1 : -1);
                row = r2;
                col = fnb(row);
                changed = true;
                return;
            }
            case "norm": case "normal":
                normalCommand(r1, r2, arg.startsWith(" ") ? arg.substring(1) : arg);
                return;
            case "g": case "global":
                global(hasRange ? r1 : 0, hasRange ? r2 : last(), arg, bang);
                return;
            case "v": case "vglobal":
                global(hasRange ? r1 : 0, hasRange ? r2 : last(), arg, true);
                return;
            case "sor": case "sort":
                sort(hasRange ? r1 : 0, hasRange ? r2 : last(), arg, bang);
                return;
            case "noh": case "nohlsearch":
                hlsearch = false;
                return;
            case "w": case "write": case "up": case "update":
                message = "\"practice.java\" written (practice mode: nothing is really saved)";
                return;
            case "q": case "quit": case "wq": case "x": case "xit": case "qa": case "wqa":
                message = "You typed :" + name + " correctly! (Quitting is disabled in the trainer.)";
                return;
            case "u": case "un": case "undo":
                doUndo();
                return;
            case "red": case "redo":
                doRedo();
                return;
            case "p": case "print":
                return;
            case "reg": case "registers": case "di": case "display": {
                StringBuilder sb = new StringBuilder();
                for (char rc : "\"0123456789abcdefghijklmnopqrstuvwxyz-".toCharArray()) {
                    Register r = registers.get(rc);
                    if (r != null) {
                        String t = r.text.replace("\n", "^J");
                        if (t.length() > 24) t = t.substring(0, 24) + "...";
                        sb.append('"').append(rc).append(' ').append(t).append("   ");
                    }
                }
                message = sb.length() == 0 ? "All registers are empty" : sb.toString().trim();
                return;
            }
            case "marks": {
                StringBuilder sb = new StringBuilder();
                for (Map.Entry<Character, int[]> e : marks.entrySet()) {
                    sb.append(e.getKey()).append(':').append(e.getValue()[0] + 1).append(' ');
                }
                message = sb.length() == 0 ? "No marks set" : sb.toString().trim();
                return;
            }
            case "set": case "se":
                message = "Options are preset: expandtab shiftwidth=4 autoindent relativenumber hlsearch";
                return;
            case "h": case "help":
                message = "Open the Cheat Sheet from the main menu for help.";
                return;
            default:
                throw exErr("E492: Not an editor command: " + s);
        }
    }

    private static String stripLeading(String s) {
        int i = 0;
        while (i < s.length() && (s.charAt(i) == ' ' || s.charAt(i) == '	')) i++;
        return s.substring(i);
    }

    private static boolean isKnownCommand(String name) {
        switch (name) {
            case "su": case "substitute": case "sor": case "sort": case "set": case "se":
                return true;
            default:
                return false;
        }
    }

    private Integer parseAddress(String s, int[] p) {
        int i = p[0];
        while (i < s.length() && s.charAt(i) == ' ') i++;
        Integer base = null;
        if (i < s.length()) {
            char ch = s.charAt(i);
            if (Character.isDigit(ch)) {
                int j = i;
                while (j < s.length() && Character.isDigit(s.charAt(j))) j++;
                base = Integer.parseInt(s.substring(i, Math.min(j, i + 7))) - 1;
                i = j;
            } else if (ch == '.') {
                base = row;
                i++;
            } else if (ch == '$') {
                base = last();
                i++;
            } else if (ch == '\'' && i + 1 < s.length()) {
                int[] m = getMark(String.valueOf(s.charAt(i + 1)));
                if (m == null) throw exErr("E20: Mark not set");
                base = m[0];
                i += 2;
            }
        }
        while (i < s.length() && (s.charAt(i) == '+' || s.charAt(i) == '-')) {
            char sign = s.charAt(i);
            i++;
            int j = i;
            while (j < s.length() && Character.isDigit(s.charAt(j))) j++;
            int off = j > i ? Integer.parseInt(s.substring(i, Math.min(j, i + 7))) : 1;
            i = j;
            if (base == null) base = row;
            base += sign == '+' ? off : -off;
        }
        p[0] = i;
        return base;
    }

    private int[] getMark(String m) {
        if (m.length() != 1) return null;
        char c = m.charAt(0);
        if (c == '`') c = '\'';
        return marks.get(c);
    }

    /** Split on the first {@code max-1} unescaped delimiters. */
    private static List<String> splitDelim(String s, char d, int max) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (out.size() < max - 1) {
                if (c == '\\' && i + 1 < s.length()) {
                    char nx = s.charAt(i + 1);
                    if (nx == d) cur.append(d);
                    else cur.append(c).append(nx);
                    i++;
                    continue;
                }
                if (c == d) {
                    out.add(cur.toString());
                    cur.setLength(0);
                    continue;
                }
            }
            cur.append(c);
        }
        out.add(cur.toString());
        return out;
    }

    private void substitute(int r1, int r2, String arg) {
        if (arg.isEmpty() || Character.isLetterOrDigit(arg.charAt(0)) || arg.charAt(0) == ' ') {
            if (lastSubPattern == null) throw exErr("E35: No previous regular expression");
            saveUndo();
            substituteWith(r1, r2, lastSubPattern, lastSubReplacement, arg.trim(), false);
            return;
        }
        char d = arg.charAt(0);
        List<String> parts = splitDelim(arg.substring(1), d, 3);
        String pat = parts.get(0);
        String rep = parts.size() > 1 ? parts.get(1) : "";
        String flags = parts.size() > 2 ? parts.get(2).trim() : "";
        if (pat.isEmpty()) {
            if (lastSearch == null) throw exErr("E35: No previous regular expression");
            pat = lastSearch;
        }
        lastSubPattern = pat;
        lastSubReplacement = rep;
        lastSubFlags = flags;
        lastSearch = pat;
        saveUndo();
        substituteWith(r1, r2, pat, rep, flags, false);
    }

    private int substituteWith(int r1, int r2, String pat, String rep, String flags, boolean silent) {
        boolean global = flags.contains("g");
        Pattern pt = compileOrFail(pat, flags.contains("i"));
        int count = 0, changedLines = 0, lastRow = -1;
        for (int r = r1; r <= r2 && r < lines.size(); r++) {
            String L = lines.get(r);
            Matcher m = pt.matcher(L);
            StringBuilder sb = new StringBuilder();
            int c = 0, lastEnd = 0;
            while (m.find()) {
                sb.append(L, lastEnd, m.start()).append(expandReplacement(m, rep));
                lastEnd = m.end();
                c++;
                if (!global) break;
            }
            if (c == 0) continue;
            sb.append(L.substring(lastEnd));
            String res = sb.toString();
            count += c;
            changedLines++;
            if (res.contains("\n")) {
                String[] parts = res.split("\n", -1);
                lines.set(r, parts[0]);
                for (int q = 1; q < parts.length; q++) lines.add(r + q, parts[q]);
                r += parts.length - 1;
                r2 += parts.length - 1;
            } else {
                lines.set(r, res);
            }
            lastRow = r;
        }
        if (count == 0) {
            if (silent) return 0;
            throw exErr("E486: Pattern not found: " + pat);
        }
        row = lastRow;
        col = fnb(row);
        wantCol = col;
        changed = true;
        if (!silent && count > 1) message = count + " substitutions on " + changedLines + " line" + (changedLines == 1 ? "" : "s");
        return count;
    }

    /** Expand a Vim replacement for one match: groups 1-9, the whole match, newline/tab and the case modifiers (u l U L E). */
    static String expandReplacement(Matcher m, String rep) {
        StringBuilder out = new StringBuilder();
        int oneShot = 0, caseMode = 0;
        for (int i = 0; i < rep.length(); i++) {
            char c = rep.charAt(i);
            String piece;
            if (c == '\\' && i + 1 < rep.length()) {
                char d = rep.charAt(++i);
                if (Character.isDigit(d)) {
                    int g = d - '0';
                    piece = g <= m.groupCount() && m.group(g) != null ? m.group(g) : "";
                } else {
                    switch (d) {
                        case 'n': case 'r': piece = "\n"; break;
                        case 't': piece = "\t"; break;
                        case 'u': oneShot = 1; continue;
                        case 'l': oneShot = -1; continue;
                        case 'U': caseMode = 1; continue;
                        case 'L': caseMode = -1; continue;
                        case 'E': case 'e': caseMode = 0; continue;
                        default: piece = String.valueOf(d);
                    }
                }
            } else if (c == '&') {
                piece = m.group();
            } else {
                piece = String.valueOf(c);
            }
            for (int k = 0; k < piece.length(); k++) {
                char ch = piece.charAt(k);
                if (oneShot != 0) {
                    ch = oneShot > 0 ? Character.toUpperCase(ch) : Character.toLowerCase(ch);
                    oneShot = 0;
                } else if (caseMode != 0) {
                    ch = caseMode > 0 ? Character.toUpperCase(ch) : Character.toLowerCase(ch);
                }
                out.append(ch);
            }
        }
        return out.toString();
    }

    private void normalCommand(int r1, int r2, String keys) {
        final List<String> toks = tokenize(keys);
        final int start = r1, end = r2;
        deferred.add(() -> {
            saveUndo();
            suppressUndo++;
            int base = lines.size();
            try {
                for (int k = 0; k <= end - start; k++) {
                    int target = start + k + (lines.size() - base);
                    if (target < 0 || target > last()) break;
                    mode = Mode.NORMAL;
                    pending.clear();
                    lineDisplay = null;
                    row = target;
                    col = 0;
                    wantCol = 0;
                    abortReplay = false;
                    depth++;
                    try {
                        for (String t : toks) {
                            if (abortReplay) break;
                            feed(t);
                        }
                    } finally {
                        depth--;
                    }
                    if (mode == Mode.INSERT || mode == Mode.REPLACE || isVisual()) feed("<Esc>");
                    pending.clear();
                    lineDisplay = null;
                }
            } finally {
                suppressUndo--;
                abortReplay = false;
            }
        });
    }

    private void global(int r1, int r2, String arg, boolean invert) {
        if (arg.isEmpty()) throw exErr("E476: Invalid command");
        char d = arg.charAt(0);
        List<String> parts = splitDelim(arg.substring(1), d, 2);
        String pat = parts.get(0);
        if (pat.isEmpty()) pat = lastSearch;
        if (pat == null) throw exErr("E35: No previous regular expression");
        String sub = parts.size() > 1 ? parts.get(1).trim() : "";
        if (sub.isEmpty()) sub = "p";
        Pattern pt = compileOrFail(pat, false);
        List<Integer> rows = new ArrayList<>();
        for (int r = r1; r <= r2 && r < lines.size(); r++) {
            if (pt.matcher(lines.get(r)).find() != invert) rows.add(r);
        }
        if (rows.isEmpty()) {
            message = "Pattern not found: " + pat;
            return;
        }
        lastSearch = pat;
        saveUndo();
        suppressUndo++;
        deferred.add(() -> suppressUndo++);
        try {
            for (int q = rows.size() - 1; q >= 0; q--) {
                int r = rows.get(q);
                if (r > last()) continue;
                row = r;
                col = 0;
                try {
                    if (sub.startsWith("s") && !sub.startsWith("sort")) {
                        String sarg = sub.substring(1);
                        if (sub.startsWith("substitute")) sarg = sub.substring(10);
                        if (sarg.isEmpty()) continue;
                        char sd = sarg.charAt(0);
                        List<String> sp = splitDelim(sarg.substring(1), sd, 3);
                        String sp0 = sp.get(0).isEmpty() ? pat : sp.get(0);
                        substituteWith(r, r, sp0, sp.size() > 1 ? sp.get(1) : "", sp.size() > 2 ? sp.get(2) : "", true);
                    } else {
                        exCommand(sub);
                    }
                } catch (Invalid e) {
                    // keep going like Vim does for :g
                }
            }
        } finally {
            suppressUndo--;
            deferred.add(() -> suppressUndo--);
        }
        changed = true;
    }

    private void sort(int r1, int r2, String arg, boolean reverse) {
        List<String> sub = new ArrayList<>(lines.subList(r1, r2 + 1));
        boolean icase = arg.contains("i"), numeric = arg.contains("n"), unique = arg.contains("u");
        Comparator<String> cmp;
        if (numeric) {
            Pattern num = Pattern.compile("-?\\d+");
            cmp = Comparator.comparingLong(s -> {
                Matcher m = num.matcher(s);
                return m.find() ? Long.parseLong(m.group()) : Long.MIN_VALUE;
            });
        } else if (icase) {
            cmp = String.CASE_INSENSITIVE_ORDER;
        } else {
            cmp = Comparator.naturalOrder();
        }
        sub.sort(reverse ? cmp.reversed() : cmp);
        if (unique) sub = new ArrayList<>(new LinkedHashSet<>(sub));
        saveUndo();
        for (int r = r2; r >= r1; r--) lines.remove(r);
        lines.addAll(r1, sub);
        row = r1;
        col = 0;
        changed = true;
    }

    // ------------------------------------------------------------------ regex

    private Pattern compileCached(String pat) {
        if (!pat.equals(cachedPatternSrc)) {
            cachedPattern = Pattern.compile(toJavaRegex(pat));
            cachedPatternSrc = pat;
        }
        return cachedPattern;
    }

    private Pattern compileOrFail(String pat, boolean icase) {
        try {
            return Pattern.compile(toJavaRegex(pat), icase ? Pattern.CASE_INSENSITIVE : 0);
        } catch (PatternSyntaxException e) {
            throw exErr("E486: Invalid pattern: " + pat);
        }
    }

    /** Convert a Vim "magic" (or {@code \v} very-magic) pattern to a Java regex. */
    public static String toJavaRegex(String p) {
        boolean very = false, icase = false;
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < p.length()) {
            char c = p.charAt(i);
            if (c == '\\' && i + 1 < p.length()) {
                char d = p.charAt(i + 1);
                i += 2;
                if (d == 'v') { very = true; continue; }
                if (d == 'm' || d == 'M' || d == 'V') { very = false; continue; }
                if (d == 'c') { icase = true; continue; }
                if (d == 'C') continue;
                if (very) {
                    if (Character.isLetterOrDigit(d)) appendClass(sb, d);
                    else sb.append('\\').append(d);
                    continue;
                }
                switch (d) {
                    case '<': case '>': sb.append("\\b"); break;
                    case '(': case ')': case '|': case '+': sb.append(d); break;
                    case '?': case '=': sb.append('?'); break;
                    case '{': {
                        int close = p.indexOf('}', i);
                        if (close < 0) { sb.append("\\{"); break; }
                        String inside = p.substring(i, close);
                        i = close + 1;
                        if (inside.endsWith("\\")) inside = inside.substring(0, inside.length() - 1);
                        if (inside.startsWith("-")) {
                            String q = inside.substring(1);
                            sb.append(q.isEmpty() ? "*?" : "{" + q + "}?");
                        } else {
                            sb.append(inside.isEmpty() ? "*" : "{" + inside + "}");
                        }
                        break;
                    }
                    default:
                        if (Character.isLetterOrDigit(d)) appendClass(sb, d);
                        else sb.append('\\').append(d);
                }
                continue;
            }
            if (very) {
                if (c == '<' || c == '>') sb.append("\\b");
                else if (c == '=') sb.append('?');
                else sb.append(c);
            } else {
                if ("(){}|+?".indexOf(c) >= 0) sb.append('\\').append(c);
                else sb.append(c);
            }
            i++;
        }
        return (icase ? "(?i)" : "") + sb;
    }

    private static void appendClass(StringBuilder sb, char d) {
        switch (d) {
            case 'n': sb.append("\\n"); break;
            case 't': sb.append("\\t"); break;
            case 's': case 'S': case 'd': case 'D': case 'w': case 'W':
                sb.append('\\').append(d); break;
            case 'a': sb.append("[A-Za-z]"); break;
            case 'l': sb.append("[a-z]"); break;
            case 'u': sb.append("[A-Z]"); break;
            case 'x': sb.append("[0-9A-Fa-f]"); break;
            case 'h': sb.append("[A-Za-z_]"); break;
            case 'k': case 'i': sb.append("[A-Za-z0-9_]"); break;
            default:
                if (Character.isDigit(d)) sb.append('\\').append(d);
                else sb.append(d);
        }
    }

    // ------------------------------------------------------------------ buffer helpers

    private static List<String> splitLines(String s) {
        return new ArrayList<>(Arrays.asList(s.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)));
    }

    private String line(int r) {
        return lines.get(r);
    }

    private int len(int r) {
        return lines.get(r).length();
    }

    private int last() {
        return lines.size() - 1;
    }

    private String flat() {
        return String.join("\n", lines);
    }

    private void setFlat(String s) {
        lines = splitLines(s);
    }

    private int idx(int r, int c) {
        int i = 0;
        for (int k = 0; k < r; k++) i += lines.get(k).length() + 1;
        return i + c;
    }

    private int[] pos(int index) {
        int r = 0;
        int i = Math.max(0, index);
        while (r < lines.size() - 1 && i > lines.get(r).length()) {
            i -= lines.get(r).length() + 1;
            r++;
        }
        return new int[]{r, Math.min(i, lines.get(r).length())};
    }

    private boolean isBlank(int r) {
        return lines.get(r).trim().isEmpty();
    }

    private int fnb(int r) {
        String L = lines.get(r);
        for (int i = 0; i < L.length(); i++) {
            if (L.charAt(i) != ' ' && L.charAt(i) != '\t') return i;
        }
        return Math.max(0, L.length() - 1);
    }

    private static String leadingWs(String s) {
        int i = 0;
        while (i < s.length() && (s.charAt(i) == ' ' || s.charAt(i) == '\t')) i++;
        return s.substring(0, i);
    }

    private static String charOf(String tok) {
        if (tok.length() == 1) return tok;
        if (tok.equals("<Tab>")) return "\t";
        if (tok.equals("<CR>")) return "\n";
        return null;
    }

    private void clampCursor() {
        row = Math.max(0, Math.min(row, last()));
        int len = len(row);
        int max = (mode == Mode.INSERT || mode == Mode.REPLACE) ? len : Math.max(0, len - 1);
        col = Math.max(0, Math.min(col, max));
    }
}
