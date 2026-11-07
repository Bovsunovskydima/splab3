package cparser.parse;

import cparser.Diagnostic;
import cparser.gen.Parser;
import cparser.gen.Sym;
import cparser.grammar.GrammarInfo;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java_cup.runtime.Scanner;
import java_cup.runtime.Symbol;

public final class TracingParser extends Parser {
    public record Step(int n, String action, List<String> symbols, List<Integer> states, String input, String detail) {
    }

    public static final class Abort extends Exception {
        public Abort() {
            super("Розбір зупинено: не вдалося відновитися після синтаксичної помилки", null, false, false);
        }
    }

    private final GrammarInfo grammar;
    private final Map<String, String> lexemes;
    private final int limit;
    private final List<Step> steps = new ArrayList<>();
    private final List<Diagnostic> diagnostics = new ArrayList<>();
    private final Map<Symbol, String> names = new IdentityHashMap<>();
    private String pendingLhs;
    private int total;

    public TracingParser(Scanner scanner, GrammarInfo grammar, Map<String, String> lexemes, int limit) {
        super(scanner);
        this.grammar = grammar;
        this.lexemes = lexemes;
        this.limit = limit;
    }

    public List<Step> steps() {
        return steps;
    }

    public List<Diagnostic> diagnostics() {
        return diagnostics;
    }

    public int totalSteps() {
        return total;
    }

    public static String terminalName(int id) {
        return id >= 0 && id < Sym.terminalNames.length ? Sym.terminalNames[id] : "#" + id;
    }

    private String describe(Symbol token) {
        if (token == null) {
            return "";
        }
        if (token.sym == Sym.EOF) {
            return "EOF";
        }
        String text = lexemes.get(token.left + ":" + token.right);
        String name = terminalName(token.sym);
        return text == null || text.equals(name) ? name : name + " " + text;
    }

    private void record(String action, String detail) {
        total++;
        if (steps.size() >= limit) {
            return;
        }
        if (pendingLhs != null && !stack.isEmpty()) {
            names.put((Symbol) stack.peek(), pendingLhs);
            pendingLhs = null;
        }
        List<String> symbols = new ArrayList<>();
        List<Integer> states = new ArrayList<>();
        for (int i = 0; i < stack.size(); i++) {
            Symbol s = (Symbol) stack.get(i);
            states.add(s.parse_state);
            if (i == 0) {
                symbols.add("$");
            } else {
                symbols.add(names.getOrDefault(s, s.sym == error_sym() ? "error" : "?"));
            }
        }
        steps.add(new Step(steps.size() + 1, action, symbols, states, describe(cur_token), detail));
    }

    @Override
    public void debug_message(String message) {
    }

    @Override
    public void debug_stack() {
    }

    @Override
    public void dump_stack() {
    }

    @Override
    public void debug_shift(Symbol token) {
        record("shift", "перенесення " + terminalName(token.sym) + ", перехід у стан " + token.parse_state);
        names.put(token, terminalName(token.sym));
    }

    @Override
    public void debug_reduce(int production, int lhs, int size) {
        GrammarInfo.Production p = grammar.production(production);
        record("reduce", "[" + production + "] " + p.text());
        pendingLhs = p.lhs();
    }

    public void recordAccept() {
        record("accept", "вхід розпізнано, програма синтаксично коректна");
    }

    @Override
    public void syntax_error(Symbol token) {
        List<String> expected = new ArrayList<>();
        try {
            for (Integer id : expected_token_ids()) {
                if (id != Sym.error) {
                    expected.add(terminalName(id));
                }
            }
        } catch (RuntimeException ignored) {
        }
        String found = token.sym == Sym.EOF ? "кінець файлу" : "токен " + describe(token);
        StringBuilder message = new StringBuilder("Синтаксична помилка: неочікуваний " + found);
        if (!expected.isEmpty()) {
            List<String> shown = expected.size() > 10 ? expected.subList(0, 10) : expected;
            message.append("; очікувалось: ").append(String.join(", ", shown));
            if (expected.size() > 10) {
                message.append(", ...");
            }
        }
        diagnostics.add(Diagnostic.error("parser", Math.max(token.left, 1), Math.max(token.right, 1), message.toString()));
        record("error", message.toString());
    }

    @Override
    public void report_error(String message, Object info) {
    }

    @Override
    public void report_fatal_error(String message, Object info) throws Exception {
        throw new Abort();
    }

    @Override
    public void unrecovered_syntax_error(Symbol token) throws Exception {
        throw new Abort();
    }
}
