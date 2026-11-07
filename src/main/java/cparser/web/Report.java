package cparser.web;

import cparser.Compilation;
import cparser.Diagnostic;
import cparser.TokenInfo;
import cparser.ast.Expr;
import cparser.ast.Node;
import cparser.grammar.GrammarInfo;
import cparser.parse.TracingParser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Report {
    private Report() {
    }

    public static Map<String, Object> compilation(Compilation c) {
        Map<String, Object> m = new LinkedHashMap<>();
        List<Object> tokens = new ArrayList<>();
        for (TokenInfo t : c.tokens) {
            tokens.add(map("type", t.type(), "text", t.text(), "line", t.line(), "column", t.column()));
        }
        m.put("tokens", tokens);
        List<Object> diagnostics = new ArrayList<>();
        for (Diagnostic d : c.diagnostics) {
            diagnostics.add(map("severity", d.severity(), "phase", d.phase(), "line", d.line(),
                    "column", d.column(), "message", d.message()));
        }
        m.put("diagnostics", diagnostics);
        int[] count = new int[1];
        m.put("ast", c.program == null ? null : ast(c.program, "root", count));
        m.put("nodes", count[0]);
        List<Object> steps = new ArrayList<>();
        for (TracingParser.Step s : c.trace) {
            steps.add(map("n", s.n(), "action", s.action(), "symbols", s.symbols(), "states", s.states(),
                    "input", s.input(), "detail", s.detail()));
        }
        m.put("trace", map("steps", steps, "total", c.traceTotal));
        m.put("syntaxOk", c.syntaxOk);
        m.put("semanticOk", c.semanticOk);
        m.put("javascript", c.javascript);
        m.put("preludeLength", c.javascript == null ? 0 : GrammarInfo.resource("/js/prelude.js").length() + 1);
        if (c.run != null) {
            m.put("run", map("output", c.run.output(), "exitCode", c.run.exitCode(), "error", c.run.error(),
                    "errorLine", c.run.errorLine(), "steps", c.run.steps(), "millis", c.run.millis()));
        } else {
            m.put("run", null);
        }
        m.put("timings", c.timings);
        return m;
    }

    private static Map<String, Object> ast(Node node, String role, int[] count) {
        count[0]++;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kind", node.kind());
        m.put("label", node.label());
        m.put("category", node.category());
        m.put("role", role);
        m.put("line", node.line);
        m.put("column", node.column);
        if (node instanceof Expr e && e.type != null) {
            m.put("type", e.type.toString());
        }
        List<Object> children = new ArrayList<>();
        for (Node.Child child : node.children()) {
            children.add(ast(child.node(), child.role(), count));
        }
        m.put("children", children);
        return m;
    }

    public static Map<String, Object> grammar(GrammarInfo g) {
        List<Object> productions = new ArrayList<>();
        for (GrammarInfo.Production p : g.productions) {
            productions.add(map("index", p.index(), "lhs", p.lhs(), "rhs", p.rhs(), "precedence", p.precedence()));
        }
        return map("productions", productions, "terminals", g.terminals, "nonterminals", g.nonterminals,
                "precedence", g.precedence, "start", g.startSymbol, "cup", g.source,
                "flex", GrammarInfo.resource("/grammar/c.flex"));
    }

    public static Map<String, Object> map(Object... pairs) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            m.put((String) pairs[i], pairs[i + 1]);
        }
        return m;
    }
}
