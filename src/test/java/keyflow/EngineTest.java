package keyflow;

import keyflow.vim.VimEngine;

/** Behavioural tests for the Vim engine. Each case: start text, cursor, keys, expected text (and optionally cursor). */
final class EngineTest {

    private EngineTest() {
    }

    static void run(TestRunner t) {
        // --- basic motions & deletes
        t.vim("x deletes char", "hello", 0, 0, "x", "ello");
        t.vim("count x", "hello", 0, 0, "3x", "lo");
        t.vim("dw", "foo bar baz", 0, 0, "dw", "bar baz");
        t.vim("dw at end of line keeps newline", "foo bar\nnext", 0, 4, "dw", "foo \nnext");
        t.vim("d2w", "one two three four", 0, 0, "d2w", "three four");
        t.vim("2dw", "one two three four", 0, 0, "2dw", "three four");
        t.vim("dW", "foo.bar baz", 0, 0, "dW", "baz");
        t.vim("de", "foo bar", 0, 0, "de", " bar");
        t.vim("db", "foo bar", 0, 4, "db", "bar");
        t.vim("d$", "foo bar", 0, 3, "d$", "foo");
        t.vim("D", "foo bar", 0, 3, "D", "foo");
        t.vim("d0", "foo bar", 0, 4, "d0", "bar");
        t.vim("d^", "    foo bar", 0, 8, "d^", "    bar");
        t.vim("dd", "a\nb\nc", 1, 0, "dd", "a\nc");
        t.vim("3dd", "a\nb\nc\nd", 0, 0, "3dd", "d");
        t.vim("dj", "a\nb\nc", 0, 0, "dj", "c");
        t.vim("dk", "a\nb\nc", 2, 0, "dk", "a");
        t.vim("dG", "a\nb\nc", 1, 0, "dG", "a");
        t.vim("dgg", "a\nb\nc", 1, 0, "dgg", "c");
        t.vim("w motion", "foo bar", 0, 0, "wx", "foo ar");
        t.vim("w over punctuation", "foo.bar", 0, 0, "wx", "foobar");
        t.vim("W over punctuation", "foo.bar baz", 0, 0, "Wx", "foo.bar az");
        t.vim("e motion", "foo bar", 0, 0, "ex", "fo bar");
        t.vim("b motion", "foo bar", 0, 5, "bx", "foo ar");
        t.vim("ge motion", "foo bar", 0, 5, "gex", "fo bar");
        t.vim("w across lines", "foo\nbar", 0, 0, "wx", "foo\nar");
        t.vim("w stops at empty line", "foo\n\nbar", 0, 0, "wiX<Esc>", "foo\nX\nbar");
        t.vim("$ and x", "abc", 0, 0, "$x", "ab");
        t.vim("0 motion", "abc", 0, 2, "0x", "bc");
        t.vim("^ motion", "   abc", 0, 5, "^x", "   bc");
        t.vim("G", "a\nb\nc", 0, 0, "Gx", "a\nb\n");
        t.vim("3G", "a\nb\nc\nd", 0, 0, "3Gx", "a\nb\n\nd");
        t.vim("gg", "a\nb\nc", 2, 0, "ggx", "\nb\nc");
        t.vim("j keeps column", "abcd\nefgh", 0, 2, "jx", "abcd\nefh");
        t.vim("j short line then long", "abcd\nx\nefgh", 0, 3, "jjx", "abcd\nx\nefg");
        t.vim("$ then j sticks to end", "ab\nabcdef", 0, 0, "$jx", "ab\nabcde");
        t.vim("f", "a,b,c", 0, 0, "f,x", "ab,c");
        t.vim("2f", "a,b,c", 0, 0, "2f,x", "a,bc");
        t.vim("t", "abc;", 0, 0, "t;x", "ab;");
        t.vim("F", "a,b,c", 0, 4, "F,x", "a,bc");
        t.vim("T", "a,b,c", 0, 4, "T,x", "a,b,");
        t.vim("; repeat", "a,b,c", 0, 0, "f,;x", "a,bc");
        t.vim(", repeat back", "a,b,c,d", 0, 0, "f,;;,x", "a,bc,d");
        t.vim("dt", "foo(bar)", 0, 0, "dt(", "(bar)");
        t.vim("df", "foo(bar)", 0, 0, "df(", "bar)");
        t.vim("dT", "foo(bar)", 0, 7, "dT(", "foo()");
        t.vim("% match", "if (a(b)) x", 0, 0, "%x", "if (a(b) x");
        t.vim("d%", "f(a, b);", 0, 1, "d%", "f;");
        t.vim("} motion", "a\nb\n\nc", 0, 0, "}iX<Esc>", "a\nb\nX\nc");
        t.vim("d} linewise", "a\nb\n\nc", 0, 0, "d}", "\nc");
        t.vim("{ motion", "a\n\nb\nc", 3, 0, "{iX<Esc>", "a\nX\nb\nc");

        // --- change, replace, case
        t.vim("cw", "foo bar", 0, 0, "cwbaz<Esc>", "baz bar");
        t.vim("cw at word end", "foo bar", 0, 2, "cwX<Esc>", "foX bar");
        t.vim("c2w", "one two three", 0, 0, "c2wX<Esc>", "X three");
        t.vim("ce", "foo bar", 0, 0, "ceX<Esc>", "X bar");
        t.vim("cc keeps indent", "    foo\nbar", 0, 0, "ccX<Esc>", "    X\nbar");
        t.vim("C", "foo bar", 0, 4, "CX<Esc>", "foo X");
        t.vim("s", "foo", 0, 0, "sX<Esc>", "Xoo");
        t.vim("S", "  foo\nbar", 0, 3, "SX<Esc>", "  X\nbar");
        t.vim("r", "foo", 0, 0, "rX", "Xoo");
        t.vim("3r", "foooo", 0, 1, "3rX", "fXXXo");
        t.vim("R", "foobar", 0, 0, "RXY<Esc>", "XYobar");
        t.vim("R backspace restores", "abc", 0, 0, "RXY<BS><BS>Z<Esc>", "Zbc");
        t.vim("~", "abc", 0, 0, "~~", "ABc");
        t.vim("g~w", "abc def", 0, 0, "g~w", "ABC def");
        t.vim("gUiw", "abc def", 0, 5, "gUiw", "abc DEF");
        t.vim("guu", "ABC DEF", 0, 0, "guu", "abc def");
        t.vim("gUU", "abc", 0, 0, "gUU", "ABC");
        t.vim("J", "foo\n    bar", 0, 0, "J", "foo bar");
        t.vim("3J", "a\nb\nc\nd", 0, 0, "3J", "a b c\nd");
        t.vim("gJ", "foo\n  bar", 0, 0, "gJ", "foo  bar");
        t.vim("J before paren", "foo(\n)", 0, 0, "J", "foo()");

        // --- insert commands
        t.vim("i", "bar", 0, 0, "ifoo<Esc>", "foobar");
        t.vim("a", "bar", 0, 0, "aX<Esc>", "bXar");
        t.vim("I", "   bar", 0, 5, "IX<Esc>", "   Xbar");
        t.vim("A", "bar", 0, 0, "A;<Esc>", "bar;");
        t.vim("o autoindent", "    foo", 0, 0, "obar<Esc>", "    foo\n    bar");
        t.vim("O", "foo", 0, 0, "Obar<Esc>", "bar\nfoo");
        t.vim("3i", "", 0, 0, "3iab<Esc>", "ababab");
        t.vim("insert BS joins", "ab\ncd", 1, 0, "i<BS><Esc>", "abcd");
        t.vim("insert CR splits", "abcd", 0, 2, "i<CR><Esc>", "ab\ncd");
        t.vim("insert C-w", "foo bar", 0, 7, "a<C-w><Esc>", "foo ");
        t.vim("insert Tab", "x", 0, 0, "i<Tab><Esc>", "    x");
        t.vim("cursor after Esc moves left", "abc", 0, 0, "Axy<Esc>x", "abcx");

        // --- yank & put
        t.vim("yyp", "foo\nbar", 0, 0, "yyp", "foo\nfoo\nbar");
        t.vim("yyP", "foo\nbar", 1, 0, "yyP", "foo\nbar\nbar");
        t.vim("ddp swaps lines", "a\nb\nc", 0, 0, "ddp", "b\na\nc");
        t.vim("xp swaps chars", "ab", 0, 0, "xp", "ba");
        t.vim("yiw P", "foo bar", 0, 0, "yiwwP", "foo foobar");
        t.vim("dw then P (w on last word goes to its end)", "foo bar", 0, 0, "dwwP", "bafoo r");
        t.vim("3p", "x", 0, 0, "yl3p", "xxxx");
        t.vim("named register", "foo\nbar", 0, 0, "\"ayyj\"ap", "foo\nbar\nfoo");
        t.vim("append register", "a\nb\nc", 0, 0, "\"ayyj\"Ayy\"ap", "a\nb\na\nb\nc");
        t.vim("yank register 0 survives delete", "foo\nbar", 0, 0, "yyjdd\"0p", "foo\nfoo");
        t.vim("black hole", "foo\nbar", 0, 0, "yyj\"_ddp", "foo\nfoo");
        t.vim("yw", "foo bar", 0, 0, "ywP", "foo foo bar");
        t.vim("y$", "foo bar", 0, 4, "y$0P", "barfoo bar");

        // --- undo / redo / dot
        t.vim("u", "foo", 0, 0, "xu", "foo");
        t.vim("redo", "foo", 0, 0, "xxuu<C-r>", "oo");
        t.vim("undo insert as one", "foo", 0, 0, "Abar<Esc>u", "foo");
        t.vim("dot repeats dw", "a b c d", 0, 0, "dw..", "d");
        t.vim("dot repeats insert", "x\ny", 0, 0, "A;<Esc>j.", "x;\ny;");
        t.vim("dot with count", "abcdef", 0, 0, "x3.", "ef");
        t.vim("dot repeats cw", "foo foo", 0, 0, "cwbar<Esc>w.", "bar bar");
        t.vim("dot repeats ciw via n", "let x = x + x", 0, 4, "*NciwY<Esc>n.n.", "let Y = Y + Y");
        t.vim("dot repeats >>", "a", 0, 0, ">>.", "        a");

        // --- text objects
        t.vim("diw", "foo bar baz", 0, 5, "diw", "foo  baz");
        t.vim("daw", "foo bar baz", 0, 5, "daw", "foo baz");
        t.vim("daw at end", "foo bar", 0, 5, "daw", "foo");
        t.vim("ciw", "int count = 0;", 0, 6, "ciwtotal<Esc>", "int total = 0;");
        t.vim("di\"", "s = \"hello world\";", 0, 7, "di\"", "s = \"\";");
        t.vim("ci\" from before", "x(\"abc\")", 0, 0, "ci\"Z<Esc>", "x(\"Z\")");
        t.vim("da\"", "say \"hi\" now", 0, 5, "da\"", "say now");
        t.vim("ci'", "c = 'a';", 0, 5, "ci'b<Esc>", "c = 'b';");
        t.vim("di(", "foo(a, b)", 0, 5, "di(", "foo()");
        t.vim("da(", "foo(a, b);", 0, 5, "da(", "foo;");
        t.vim("ci( on paren", "foo(a)", 0, 3, "ci(x<Esc>", "foo(x)");
        t.vim("ci( nested", "f(a(b), c)", 0, 4, "ci(X<Esc>", "f(a(X), c)");
        t.vim("2ci(", "f(a(b), c)", 0, 4, "2ci(X<Esc>", "f(X)");
        t.vim("di[", "a[i + 1]", 0, 3, "di[", "a[]");
        t.vim("di<", "List<String> x", 0, 6, "di<", "List<> x");
        t.vim("di{ single line", "{ a; }", 0, 2, "di{", "{}");
        t.vim("di{ block linewise", "void f() {\n    a();\n    b();\n}", 1, 4, "di{", "void f() {\n}");
        t.vim("ci{ block", "if (x) {\n    a();\n}", 1, 0, "ci{b();<Esc>", "if (x) {\n    b();\n}");
        t.vim("yi{ from brace line", "f() {\n    x;\n}", 0, 4, "yi{Gp", "f() {\n    x;\n}\n    x;");
        t.vim("dip", "a\nb\n\nc", 0, 0, "dip", "\nc");
        t.vim("dap", "a\nb\n\nc", 0, 0, "dap", "c");
        t.vim("diW", "x foo.bar y", 0, 4, "diW", "x  y");

        // --- visual mode
        t.vim("v d", "abcdef", 0, 1, "vlld", "aef");
        t.vim("v e y P", "foo bar", 0, 0, "veyP", "foofoo bar");
        t.vim("V d", "a\nb\nc", 0, 0, "Vjd", "c");
        t.vim("V >", "a\nb", 0, 0, "Vj>", "    a\n    b");
        t.vim("V <", "    a\n    b", 0, 0, "Vj<", "a\nb");
        t.vim("v iw c", "foo bar", 0, 5, "viwcX<Esc>", "foo X");
        t.vim("v U", "abc", 0, 0, "vlU", "ABc");
        t.vim("v ~", "aBc", 0, 0, "v$~", "AbC");
        t.vim("V J", "a\nb\nc", 0, 0, "VjjJ", "a b c");
        t.vim("v p replaces", "foo bar", 0, 0, "yiwwviwp", "foo foo");
        t.vim("v o", "abcdef", 0, 2, "vlohd", "aef");
        t.vim("v r", "abcd", 0, 0, "vllrx", "xxxd");
        t.vim("V :s", "a1\na2\na3", 0, 0, "Vj:s/a/b/<CR>", "b1\nb2\na3");
        t.vim("gv reselect", "abc", 0, 0, "vl<Esc>gvd", "c");
        t.vim("v i( ", "f(a, b)", 0, 3, "vi(d", "f()");
        t.vim("V A", "a\nb", 0, 0, "VjAX<Esc>", "a\nbX");

        // --- search
        t.vim("/ search", "foo bar baz", 0, 0, "/baz<CR>x", "foo bar az");
        t.vim("? search", "foo bar baz", 0, 10, "?bar<CR>x", "foo ar baz");
        t.vim("n repeats", "a x a x a", 0, 0, "/a<CR>nx", "a x a x ");
        t.vim("N", "a x a x a", 0, 0, "/a<CR>nNx", "a x  x a");
        t.vim("search wraps", "x a\nb", 1, 0, "/a<CR>x", "x \nb");
        t.vim("* word", "foo bar foo", 0, 0, "*x", "foo bar oo");
        t.vim("# word", "foo bar foo", 0, 8, "#x", "oo bar foo");
        t.vim("d/pattern", "foo bar baz", 0, 0, "d/baz<CR>", "baz");
        t.vim("c/pattern", "foo bar baz", 0, 0, "c/bar<CR>X <Esc>", "X bar baz");
        t.vim("search regex", "a1 b22 c333", 0, 0, "/\\d\\{3}<CR>x", "a1 b22 c33");
        t.vim("search word boundary", "cat concat cat", 0, 0, "/\\<cat\\><CR>x", "cat concat at");
        t.vim("search BS edits", "ab ac", 0, 0, "/ab<BS>c<CR>x", "ab c");
        t.vim("search Esc cancels", "abc", 0, 0, "/b<Esc>x", "bc");

        // --- marks & jumps
        t.vim("mark and jump", "a\nb\nc", 0, 0, "majj'ax", "\nb\nc");
        t.vim("mark backtick (exclusive rule)", "abc\nd", 0, 2, "majd`a", "ab\nd");
        t.vim("d'a linewise", "a\nb\nc\nd", 0, 0, "majjd'a", "d");
        t.vim("y`a charwise", "hello world", 0, 0, "mawy`a$p", "hello worldhello ");

        // --- macros
        t.vim("macro", "1\n2\n3", 0, 0, "qaA,<Esc>jq@a@@", "1,\n2,\n3,");
        t.vim("macro with count", "a\nb\nc\nd", 0, 0, "qaI- <Esc>jq3@a", "- a\n- b\n- c\n- d");
        t.vim("macro stops at error", "a\nb", 0, 0, "qaA!<Esc>jq5@a", "a!\nb!");
        t.vim("recursive macro", "x\nx\nx\nx", 0, 0, "qaqqarYj@aq@a", "Y\nY\nY\nY");
        t.vim("macro transforms", "foo = 1\nbar = 2", 0, 0, "qa0dwAX<Esc>jq@a", "= 1X\n= 2X");

        // --- counts & numbers
        t.vim("C-a", "x = 9;", 0, 0, "<C-a>", "x = 10;");
        t.vim("5 C-a", "x = 9;", 0, 0, "5<C-a>", "x = 14;");
        t.vim("C-x negative", "x = 0;", 0, 0, "<C-x>", "x = -1;");
        t.vim("C-a minus as operator", "i-1", 0, 0, "<C-a>", "i-2");
        t.vim("3w", "a b c d", 0, 0, "3wx", "a b c ");
        t.vim("count j", "a\nb\nc\nd", 0, 0, "2jx", "a\nb\n\nd");

        // --- ex commands
        t.vim(":s", "foo foo", 0, 0, ":s/foo/bar/<CR>", "bar foo");
        t.vim(":s g", "foo foo", 0, 0, ":s/foo/bar/g<CR>", "bar bar");
        t.vim(":%s", "a\na\nb", 0, 0, ":%s/a/x/<CR>", "x\nx\nb");
        t.vim(":range s", "a\na\na", 0, 0, ":2,3s/a/x/<CR>", "a\nx\nx");
        t.vim(":s groups", "John Smith", 0, 0, ":s/\\(\\w\\+\\) \\(\\w\\+\\)/\\2, \\1/<CR>", "Smith, John");
        t.vim(":s very magic", "John Smith", 0, 0, ":s/\\v(\\w+) (\\w+)/\\2 \\1/<CR>", "Smith John");
        t.vim(":s &", "cat", 0, 0, ":s/cat/[&]/<CR>", "[cat]");
        t.vim(":s newline", "a,b", 0, 0, ":s/,/\\r/<CR>", "a\nb");
        t.vim(":s other delim", "a/b", 0, 0, ":s#/#-#<CR>", "a-b");
        t.vim(":g d", "keep\ndrop x\nkeep\ndrop y", 0, 0, ":g/drop/d<CR>", "keep\nkeep");
        t.vim(":v d", "keep\ndrop x\nkeep", 0, 0, ":v/keep/d<CR>", "keep\nkeep");
        t.vim(":g norm", "a\nb", 0, 0, ":g/./norm A;<CR>", "a;\nb;");
        t.vim(":g s", "x1\ny1\nx2", 0, 0, ":g/x/s/\\d/N/<CR>", "xN\ny1\nxN");
        t.vim(":norm range", "a\nb\nc", 0, 0, ":%norm I// <CR>", "// a\n// b\n// c");
        t.vim(":norm with dd", "a\nb\nc", 0, 0, ":%norm dd<CR>", "");
        t.vim(":d", "a\nb\nc", 0, 0, ":2d<CR>", "a\nc");
        t.vim(":m0", "a\nb\nc", 2, 0, ":m0<CR>", "c\na\nb");
        t.vim(":m+1", "a\nb\nc", 0, 0, ":m+1<CR>", "b\na\nc");
        t.vim(":t.", "a\nb", 0, 0, ":t.<CR>", "a\na\nb");
        t.vim(":sort", "c\na\nb", 0, 0, ":sort<CR>", "a\nb\nc");
        t.vim(":sort u", "b\na\nb", 0, 0, ":sort u<CR>", "a\nb");
        t.vim(":j", "a\nb", 0, 0, ":j<CR>", "a b");
        t.vim(":>", "a", 0, 0, ":><CR>", "    a");
        t.vim(":goto line", "a\nb\nc", 0, 0, ":3<CR>x", "a\nb\n");
        t.vim("V :norm", "a\nb\nc", 0, 0, "Vj:norm A!<CR>", "a!\nb!\nc");
        t.vim("undo :%s", "a\na", 0, 0, ":%s/a/b/<CR>u", "a\na");
        t.vim("undo :g norm as one", "a\nb", 0, 0, ":g/./norm A;<CR>u", "a\nb");
        t.vim("& repeat", "a a\na a", 0, 0, ":s/a/b/<CR>j&", "b a\nb a");
        t.vim("count :", "a\nb\nc", 0, 0, "2:s/$/;/<CR>", "a;\nb;\nc");

        // --- C-r in insert
        t.vim("insert C-r register", "foo", 0, 0, "yiwA <C-r>\"<Esc>", "foo foo");

        // --- misc behaviour
        t.vim("Esc cancels operator", "abc", 0, 0, "d<Esc>x", "bc");
        t.vim("invalid key ignored", "abc", 0, 0, "Qx", "bc");
        t.vim("h at col 0 fails op", "abc", 0, 0, "dhx", "bc");
        t.check("keystroke count", () -> {
            VimEngine e = new VimEngine("foo bar");
            e.keys("dw");
            return e.getKeystrokes() == 2;
        });
        t.check("cursor after dd is first non-blank", () -> {
            VimEngine e = new VimEngine("a\n    b");
            e.keys("dd");
            return e.getRow() == 0 && e.getCol() == 4;
        });
        t.check("tokenize notation", () -> VimEngine.tokenize("ci\"x<Esc><C-r>a<lt>").size() == 8);
        t.check("visual bounds", () -> {
            VimEngine e = new VimEngine("abc");
            e.keys("vl");
            int[] b = e.getVisualBounds();
            return b != null && b[1] == 0 && b[3] == 1;
        });
        t.check("command line display", () -> {
            VimEngine e = new VimEngine("abc");
            e.keys(":s/a");
            return ":s/a".equals(e.getCommandLine());
        });
        t.check("pending display", () -> {
            VimEngine e = new VimEngine("abc");
            e.keys("2d");
            return "2d".equals(e.getPendingKeys());
        });
        t.check("recording indicator", () -> {
            VimEngine e = new VimEngine("abc");
            e.keys("qa");
            boolean rec = Character.valueOf('a').equals(e.getRecordingRegister());
            e.keys("q");
            return rec && e.getRecordingRegister() == null;
        });
    }
}
