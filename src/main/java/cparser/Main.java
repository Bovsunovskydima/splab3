package cparser;

import cparser.ast.Node;
import cparser.parse.TracingParser;
import cparser.web.WebServer;
import java.awt.Desktop;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        boolean server = args.length == 0 || Arrays.stream(args)
                .anyMatch(a -> a.equals("--server") || a.equals("--port") || a.equals("--no-browser"));
        if (server) {
            int port = 8080;
            boolean open = true;
            for (int i = 0; i < args.length; i++) {
                if (args[i].equals("--port")) {
                    port = Integer.parseInt(args[++i]);
                } else if (args[i].equals("--no-browser")) {
                    open = false;
                }
            }
            WebServer.start(port);
            String url = "http://localhost:" + port + "/";
            out.println("Сервер запущено: " + url);
            out.println("Зупинити: Ctrl+C");
            if (open) {
                openBrowser(url);
            }
            return;
        }
        cli(args, out);
    }

    private static void openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
            }
        } catch (Exception ignored) {
        }
    }

    private static void cli(String[] args, PrintStream out) throws IOException {
        Path file = null;
        boolean tokens = false;
        boolean ast = false;
        boolean trace = false;
        boolean js = false;
        boolean run = false;
        Path jsOut = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--tokens" -> tokens = true;
                case "--ast" -> ast = true;
                case "--trace" -> trace = true;
                case "--js" -> js = true;
                case "--run" -> run = true;
                case "-o" -> jsOut = Path.of(args[++i]);
                default -> file = Path.of(args[i]);
            }
        }
        if (file == null) {
            out.println("Використання: java -jar c-parser.jar [файл.c] [--tokens] [--ast] [--trace] [--js] [--run] [-o out.js]");
            out.println("Без аргументів запускається вебінтерфейс на http://localhost:8080");
            return;
        }
        if (!tokens && !ast && !trace && !js && !run && jsOut == null) {
            ast = true;
            run = true;
        }
        String source = Files.readString(file, StandardCharsets.UTF_8);
        Compilation.Options d = Compilation.Options.defaults();
        Compilation c = Compilation.compile(source, new Compilation.Options(trace, 100_000, run, d.maxSteps(), d.timeoutMs()));
        if (tokens) {
            out.println("== Токени ==");
            for (TokenInfo t : c.tokens) {
                out.printf("%4d:%-3d %-14s %s%n", t.line(), t.column(), t.type(), t.text());
            }
        }
        if (trace) {
            out.println("== Траса LALR(1)-розбору ==");
            for (TracingParser.Step s : c.trace) {
                out.printf("%5d  %-7s %-30s | %s%n", s.n(), s.action(), s.input(), s.detail());
            }
        }
        if (ast && c.program != null) {
            out.println("== AST ==");
            printTree(c.program, "", "", out);
        }
        for (Diagnostic diagnostic : c.diagnostics) {
            out.println(diagnostic);
        }
        if (js && c.javascript != null) {
            out.println("== JavaScript ==");
            out.println(c.javascript);
        }
        if (jsOut != null && c.javascript != null) {
            Files.writeString(jsOut, c.javascript, StandardCharsets.UTF_8);
            out.println("JavaScript збережено у " + jsOut);
        }
        if (run && c.run != null) {
            out.println("== Виконання ==");
            out.print(c.run.output());
            if (c.run.error() != null) {
                out.println("Помилка виконання (рядок " + c.run.errorLine() + "): " + c.run.error());
            } else {
                out.println("[main повернула " + c.run.exitCode() + ", кроків: " + c.run.steps() + "]");
            }
        }
        if (c.hasErrors()) {
            System.exit(1);
        }
    }

    private static void printTree(Node node, String prefix, String role, PrintStream out) {
        String type = node instanceof cparser.ast.Expr e && e.type != null ? " : " + e.type : "";
        String r = role.isEmpty() ? "" : role + ": ";
        out.println(prefix + r + node.kind() + (node.label().equals(node.kind()) ? "" : " " + node.label()) + type);
        List<Node.Child> children = node.children();
        String next = prefix.replace("├─", "│ ").replace("└─", "  ");
        for (int i = 0; i < children.size(); i++) {
            boolean last = i == children.size() - 1;
            printTree(children.get(i).node(), next + (last ? "└─" : "├─"), children.get(i).role(), out);
        }
    }
}
