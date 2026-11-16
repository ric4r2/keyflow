package keyflow;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import keyflow.course.Catalog;
import keyflow.course.Exercise;
import keyflow.course.Lesson;

/**
 * The Java track must be progressive: a practice card (type, fix, write) may only use a construct
 * that a Learn or Recall card has already shown, in that lesson or an earlier one.
 */
final class ConceptOrderTest {

    private static final Map<String, Pattern> CONSTRUCTS = new LinkedHashMap<>();

    static {
        c("if", "\\bif\\s*\\(");
        c("for loop", "\\bfor\\s*\\(");
        c("enhanced for", "for\\s*\\([^;)]*:");
        c("while", "\\bwhile\\s*\\(");
        c("switch", "\\bswitch\\b");
        c("ternary", "\\?[^?:\"]*:");
        c("++ / +=", "\\+\\+|\\+=");
        c("array", "\\w\\[\\]|new \\w+\\[");
        c("class declaration", "\\bclass\\s+\\w");
        c("new object", "\\bnew [A-Z]\\w*\\(");
        c("extends", "\\bextends\\b");
        c("interface", "\\binterface\\b");
        c("enum", "\\benum\\b");
        c("record", "\\brecord\\s+\\w+\\(");
        c("try / catch", "\\btry\\s*[{(]");
        c("throw", "\\bthrow\\b");
        c("lambda", "->");
        c("method reference", "\\w::\\w");
        c("generics", "<[A-Z]\\w*(, ?[A-Z]\\w*)?>");
        c("var", "\\bvar\\s+\\w+\\s*=");
        c("instanceof", "\\binstanceof\\b");
        c("printf / format", "printf|String\\.format");
        c("Scanner", "\\bScanner\\b");
        c("StringBuilder", "\\bStringBuilder\\b");
        c("ArrayList", "\\bArrayList\\b");
        c("HashMap", "\\bHashMap\\b");
        c("stream", "\\.stream\\(\\)");
        c("Optional", "\\bOptional\\b");
        c("Thread", "\\bThread\\b");
        c("synchronized", "\\bsynchronized\\b");
        c("Math", "\\bMath\\.");
        c("final", "\\bfinal\\b");
        c("char", "\\bchar\\b");
        c("long / double", "\\b(long|double)\\b");
        c("boolean", "\\bboolean\\b");
    }

    private static void c(String name, String regex) {
        CONSTRUCTS.put(name, Pattern.compile(regex));
    }

    private ConceptOrderTest() {
    }

    static void run(TestRunner t) {
        Catalog cat = Catalog.load();
        Set<String> shown = new HashSet<>();
        int checked = 0;
        for (Lesson l : cat.subject("java").allLessons()) {
            StringBuilder teach = new StringBuilder();
            for (Exercise e : l.exercises) {
                if (e.kind == Exercise.Kind.READ || e.kind == Exercise.Kind.QUIZ) {
                    teach.append(e.title).append('\n').append(e.text).append('\n').append(e.code).append('\n').append(e.explain).append('\n');
                    for (String o : e.options) teach.append(o).append('\n');
                }
            }
            teach.append(l.intro);
            for (Map.Entry<String, Pattern> c : CONSTRUCTS.entrySet()) if (c.getValue().matcher(teach).find()) shown.add(c.getKey());
            for (Exercise e : l.exercises) {
                if (!e.kind.isPractice() || e.kind == Exercise.Kind.QUIZ) continue;
                checked++;
                String use = e.code + "\n" + e.start + "\n" + String.join("\n", e.answers);
                Set<String> missing = new java.util.LinkedHashSet<>();
                for (Map.Entry<String, Pattern> c : CONSTRUCTS.entrySet()) {
                    if (c.getValue().matcher(use).find() && !shown.contains(c.getKey())) missing.add(c.getKey());
                }
                if (missing.isEmpty()) t.pass();
                else t.fail("concept order: " + e.id + " (" + l.title + ") uses " + missing + " before a Learn card shows it");
            }
        }
        System.out.println("Concept order: " + checked + " Java practice cards checked against constructs shown so far");
    }
}
