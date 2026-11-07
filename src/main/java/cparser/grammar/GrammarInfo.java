package cparser.grammar;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public final class GrammarInfo {
    private static final Pattern CODE_BLOCK = Pattern.compile("\\{:.*?:}", Pattern.DOTALL);
    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern LINE_COMMENT = Pattern.compile("//[^\\n]*");

    private static GrammarInfo instance;

    public final String source;
    public final String startSymbol;
    public final List<String> terminals = new ArrayList<>();
    public final List<String> nonterminals = new ArrayList<>();
    public final List<String> precedence = new ArrayList<>();
    public final List<Production> productions = new ArrayList<>();

    public record Production(int index, String lhs, List<String> rhs, String precedence) {
        public String text() {
            return lhs + " ::= " + (rhs.isEmpty() ? "ε" : String.join(" ", rhs));
        }
    }

    public static synchronized GrammarInfo get() {
        if (instance == null) {
            instance = new GrammarInfo(resource("/grammar/c.cup"));
        }
        return instance;
    }

    public static String resource(String path) {
        try (InputStream in = GrammarInfo.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Не знайдено ресурс " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public GrammarInfo(String source) {
        this.source = source;
        String text = CODE_BLOCK.matcher(source).replaceAll(" ");
        text = BLOCK_COMMENT.matcher(text).replaceAll(" ");
        text = LINE_COMMENT.matcher(text).replaceAll(" ");
        String start = null;
        List<String[]> rules = new ArrayList<>();
        for (String raw : text.split(";")) {
            String statement = raw.trim().replaceAll("\\s+", " ");
            if (statement.isEmpty() || statement.startsWith("package ") || statement.startsWith("import ")) {
                continue;
            }
            if (statement.startsWith("terminal ")) {
                terminals.addAll(names(statement.substring("terminal ".length())));
            } else if (statement.startsWith("non terminal ")) {
                nonterminals.addAll(names(statement.substring("non terminal ".length())));
            } else if (statement.startsWith("precedence ")) {
                precedence.add(statement.substring("precedence ".length()));
            } else if (statement.startsWith("start with ")) {
                start = statement.substring("start with ".length()).trim();
            } else if (statement.contains("::=")) {
                String[] parts = statement.split("::=", 2);
                rules.add(new String[] {parts[0].trim(), parts[1]});
            }
        }
        startSymbol = start != null ? start : rules.get(0)[0];
        productions.add(new Production(0, "$START", List.of(startSymbol, "EOF"), null));
        for (String[] rule : rules) {
            for (String alternative : rule[1].split("\\|", -1)) {
                List<String> rhs = new ArrayList<>();
                String prec = null;
                String[] tokens = alternative.trim().isEmpty() ? new String[0] : alternative.trim().split(" ");
                for (int i = 0; i < tokens.length; i++) {
                    if (tokens[i].equals("%prec")) {
                        prec = tokens[++i];
                        continue;
                    }
                    int colon = tokens[i].indexOf(':');
                    rhs.add(colon >= 0 ? tokens[i].substring(0, colon) : tokens[i]);
                }
                productions.add(new Production(productions.size(), rule[0], rhs, prec));
            }
        }
    }

    private static List<String> names(String declaration) {
        String[] parts = declaration.split(",");
        List<String> result = new ArrayList<>();
        for (int i = 0; i < parts.length; i++) {
            String[] words = parts[i].trim().split(" ");
            result.add(i == 0 ? words[words.length - 1] : parts[i].trim());
        }
        return result;
    }

    public Production production(int index) {
        if (index >= 0 && index < productions.size()) {
            return productions.get(index);
        }
        return new Production(index, "?", Arrays.asList("?"), null);
    }
}
