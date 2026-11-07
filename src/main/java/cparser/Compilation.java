package cparser;

import cparser.ast.Program;
import cparser.codegen.JsGenerator;
import cparser.gen.Lexer;
import cparser.gen.Sym;
import cparser.grammar.GrammarInfo;
import cparser.parse.TracingParser;
import cparser.runtime.Interpreter;
import cparser.semantic.Analyzer;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java_cup.runtime.Symbol;

public final class Compilation {
    public record Options(boolean trace, int traceLimit, boolean run, long maxSteps, long timeoutMs) {
        public static Options defaults() {
            return new Options(true, 5000, true, 50_000_000L, 10_000L);
        }
    }

    public final String source;
    public final List<TokenInfo> tokens = new ArrayList<>();
    public final List<Diagnostic> diagnostics = new ArrayList<>();
    public final Map<String, Long> timings = new HashMap<>();
    public List<TracingParser.Step> trace = List.of();
    public int traceTotal;
    public Program program;
    public boolean syntaxOk;
    public boolean semanticOk;
    public String javascript;
    public Interpreter.Result run;

    private Compilation(String source) {
        this.source = source;
    }

    public static Compilation compile(String source, Options options) {
        Compilation c = new Compilation(source);
        Map<String, String> lexemes = c.lex();
        c.parse(options, lexemes);
        if (c.program != null && c.syntaxOk) {
            long t = System.nanoTime();
            c.diagnostics.addAll(Analyzer.analyze(c.program));
            c.timings.put("semantic", (System.nanoTime() - t) / 1000);
            c.semanticOk = c.diagnostics.stream().noneMatch(Diagnostic::isError);
        }
        if (c.semanticOk) {
            long t = System.nanoTime();
            c.javascript = JsGenerator.generate(c.program);
            c.timings.put("codegen", (System.nanoTime() - t) / 1000);
            if (options.run() && Analyzer.hasMain(c.program)) {
                c.run = Interpreter.run(c.program, options.maxSteps(), options.timeoutMs());
            }
        }
        return c;
    }

    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(Diagnostic::isError);
    }

    private Map<String, String> lex() {
        long t = System.nanoTime();
        Map<String, String> lexemes = new HashMap<>();
        Lexer lexer = new Lexer(new StringReader(source));
        try {
            while (true) {
                Symbol s = lexer.next_token();
                if (s.sym == Sym.EOF) {
                    break;
                }
                String text = s.sym == Sym.STRING_LIT ? quote((String) s.value) : lexer.yytext();
                tokens.add(new TokenInfo(TracingParser.terminalName(s.sym), text, s.left, s.right));
                lexemes.put(s.left + ":" + s.right, text);
            }
        } catch (LexerException e) {
            diagnostics.add(Diagnostic.error("lexer", e.line, e.column, e.getMessage()));
        } catch (Exception e) {
            diagnostics.add(Diagnostic.error("lexer", 1, 1, String.valueOf(e.getMessage())));
        }
        timings.put("lexer", (System.nanoTime() - t) / 1000);
        return lexemes;
    }

    private void parse(Options options, Map<String, String> lexemes) {
        long t = System.nanoTime();
        TracingParser parser = new TracingParser(new Lexer(new StringReader(source)), GrammarInfo.get(),
                lexemes, options.trace() ? options.traceLimit() : 0);
        boolean lexerFailed = hasErrors();
        try {
            Symbol result = parser.debug_parse();
            if (result != null && result.value instanceof Program p) {
                program = p;
            }
            if (parser.diagnostics().isEmpty()) {
                parser.recordAccept();
            }
        } catch (LexerException e) {
            if (!lexerFailed) {
                diagnostics.add(Diagnostic.error("lexer", e.line, e.column, e.getMessage()));
            }
        } catch (TracingParser.Abort e) {
            diagnostics.add(Diagnostic.error("parser", 0, 0, e.getMessage()));
        } catch (Exception e) {
            diagnostics.add(Diagnostic.error("parser", 0, 0, "Внутрішня помилка розбору: " + e));
        }
        diagnostics.addAll(0, parser.diagnostics());
        syntaxOk = !hasErrors();
        trace = parser.steps();
        traceTotal = parser.totalSteps();
        timings.put("parser", (System.nanoTime() - t) / 1000);
    }

    private static String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char ch : s.toCharArray()) {
            switch (ch) {
                case '\n' -> sb.append("\\n");
                case '\t' -> sb.append("\\t");
                case '\r' -> sb.append("\\r");
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                default -> sb.append(ch);
            }
        }
        return sb.append('"').toString();
    }
}
