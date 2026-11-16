package keyflow.course;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/** Compiles Java snippets in memory with the JDK compiler and compares code ignoring formatting. */
public final class JavaChecker {

    private JavaChecker() {
    }

    public static final class Result {
        public final boolean ok;
        public final boolean skipped;
        public final List<String> errors;
        public final String note;

        Result(boolean ok, boolean skipped, List<String> errors, String note) {
            this.ok = ok;
            this.skipped = skipped;
            this.errors = errors;
            this.note = note;
        }
    }

    private static final String IMPORTS = "import java.util.*;\nimport java.util.function.*;\nimport java.util.stream.*;\n"
            + "import java.util.concurrent.*;\nimport java.util.concurrent.atomic.*;\nimport java.time.*;\nimport java.math.*;\nimport java.nio.file.*;\n";
    private static final int IMPORT_LINES = IMPORTS.split("\n").length;
    private static final Pattern PUBLIC_TYPE = Pattern.compile(
            "public\\s+(?:(?:final|abstract|sealed|non-sealed|static|strictfp)\\s+)*(?:class|interface|enum|record|@interface)\\s+(\\w+)");

    public static boolean compilerAvailable() {
        try {
            return ToolProvider.getSystemJavaCompiler() != null;
        } catch (Throwable t) {
            return false;
        }
    }

    public static int runtimeVersion() {
        try {
            return Runtime.version().feature();
        } catch (Throwable t) {
            return 11;
        }
    }

    /** Can code needing Java {@code since} be compiled by this JVM? */
    public static boolean canCompile(int since) {
        return compilerAvailable() && runtimeVersion() >= since;
    }

    public static Result compile(String code, String mode, int since) {
        if (mode == null || mode.equals("none")) return new Result(true, true, Collections.emptyList(), "No compilation needed.");
        if (!compilerAvailable()) {
            return new Result(true, true, Collections.emptyList(), "No Java compiler found: run the trainer with a JDK (not a JRE) to enable compile checks.");
        }
        if (runtimeVersion() < since) {
            return new Result(true, true, Collections.emptyList(),
                    "This snippet needs Java " + since + "+ but the trainer runs on Java " + runtimeVersion() + ": compile check skipped.");
        }
        String source;
        int offset;
        String className = "Main";
        switch (mode) {
            case "stmt":
                source = IMPORTS + "class Main {\npublic static void main(String[] args) throws Exception {\n" + code + "\n}\n}\n";
                offset = IMPORT_LINES + 2;
                break;
            case "body":
                source = IMPORTS + "class Main {\n" + code + "\n}\n";
                offset = IMPORT_LINES + 1;
                break;
            default:
                source = IMPORTS + code + "\n";
                offset = IMPORT_LINES;
                Matcher m = PUBLIC_TYPE.matcher(code);
                if (m.find()) className = m.group(1);
        }
        try {
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            DiagnosticCollector<JavaFileObject> diags = new DiagnosticCollector<>();
            StandardJavaFileManager std = compiler.getStandardFileManager(diags, Locale.ENGLISH, null);
            JavaFileManager fm = new ForwardingJavaFileManager<JavaFileManager>(std) {
                @Override
                public JavaFileObject getJavaFileForOutput(Location location, String name, JavaFileObject.Kind kind, FileObject sibling) {
                    return new SimpleJavaFileObject(URI.create("mem:///" + name.replace('.', '/') + kind.extension), kind) {
                        @Override
                        public OutputStream openOutputStream() {
                            return new ByteArrayOutputStream();
                        }
                    };
                }
            };
            final String src = source;
            JavaFileObject file = new SimpleJavaFileObject(URI.create("string:///" + className + ".java"), JavaFileObject.Kind.SOURCE) {
                @Override
                public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                    return src;
                }
            };
            List<String> options = new ArrayList<>();
            options.add("-proc:none");
            options.add("-nowarn");
            options.add("-Xlint:none");
            Boolean ok = compiler.getTask(null, fm, diags, options, null, Collections.singletonList(file)).call();
            List<String> errors = new ArrayList<>();
            for (Diagnostic<? extends JavaFileObject> d : diags.getDiagnostics()) {
                if (d.getKind() != Diagnostic.Kind.ERROR) continue;
                long line = d.getLineNumber() - offset;
                String msg = d.getMessage(Locale.ENGLISH);
                int nl = msg.indexOf('\n');
                if (nl > 0) msg = msg.substring(0, nl);
                errors.add((line >= 1 ? "Line " + line + ": " : "") + msg);
            }
            fm.close();
            return new Result(Boolean.TRUE.equals(ok), false, errors, Boolean.TRUE.equals(ok) ? "Compiles cleanly." : errors.size() + " compile error(s).");
        } catch (Throwable t) {
            return new Result(true, true, Collections.emptyList(), "Compiler unavailable: " + t.getMessage());
        }
    }

    /** Collapse formatting so that only meaningful differences remain. */
    public static String normalize(String code) {
        String t = code.replaceAll("\\s+", " ").trim();
        StringBuilder sb = new StringBuilder(t.length());
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c == ' ') {
                char p = sb.length() > 0 ? sb.charAt(sb.length() - 1) : ' ';
                char n = i + 1 < t.length() ? t.charAt(i + 1) : ' ';
                if (isWord(p) && isWord(n)) sb.append(' ');
                continue;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private static boolean isWord(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }

    public static boolean matchesAny(String code, List<String> answers) {
        String n = normalize(code);
        for (String a : answers) if (normalize(a).equals(n)) return true;
        return false;
    }

    /** Which regex checks fail for the code (empty list = all pass). */
    public static List<String> failedChecks(String code, List<String> checks) {
        List<String> failed = new ArrayList<>();
        for (String c : checks) {
            if (!Pattern.compile(c, Pattern.MULTILINE).matcher(code).find()) failed.add(c);
        }
        return failed;
    }

    /** Full validation of a fix-it attempt. */
    public static boolean accepts(Exercise ex, String code, Result compiled) {
        if (!ex.answers.isEmpty() && matchesAny(code, ex.answers)) return true;
        if (ex.checks.isEmpty()) return false;
        if (!failedChecks(code, ex.checks).isEmpty()) return false;
        return compiled == null || compiled.skipped || compiled.ok;
    }
}
