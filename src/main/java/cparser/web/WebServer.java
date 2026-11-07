package cparser.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import cparser.Compilation;
import cparser.grammar.GrammarInfo;
import cparser.tools.ConflictLab;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

public final class WebServer {
    private static final Map<String, String> TYPES = Map.of(
            "html", "text/html; charset=utf-8",
            "js", "text/javascript; charset=utf-8",
            "css", "text/css; charset=utf-8",
            "svg", "image/svg+xml",
            "c", "text/plain; charset=utf-8");

    private WebServer() {
    }

    public static HttpServer start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/", WebServer::staticFile);
        server.createContext("/api/analyze", exchange -> handle(exchange, () -> analyze(exchange)));
        server.createContext("/api/examples", exchange -> handle(exchange, () -> list("/examples/index.txt")));
        server.createContext("/api/example", exchange -> handle(exchange, () -> text(exchange, "/examples/")));
        server.createContext("/api/grammar", exchange -> handle(exchange, () -> Report.grammar(GrammarInfo.get())));
        server.createContext("/api/conflict-presets", exchange -> handle(exchange, () -> list("/conflicts/index.txt")));
        server.createContext("/api/conflict-preset", exchange -> handle(exchange, () -> text(exchange, "/conflicts/")));
        server.createContext("/api/conflicts", exchange -> handle(exchange, () -> conflicts(exchange)));
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
        return server;
    }

    private interface Action {
        Object run() throws Exception;
    }

    private static void handle(HttpExchange exchange, Action action) throws IOException {
        try {
            Object result = action.run();
            send(exchange, 200, "application/json; charset=utf-8", Json.write(result).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            String body = Json.write(Report.map("error", String.valueOf(e.getMessage())));
            send(exchange, 500, "application/json; charset=utf-8", body.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static Object analyze(HttpExchange exchange) throws IOException {
        String source = body(exchange);
        Map<String, String> q = query(exchange);
        Compilation.Options defaults = Compilation.Options.defaults();
        Compilation.Options options = new Compilation.Options(!"0".equals(q.get("trace")), defaults.traceLimit(),
                !"0".equals(q.get("run")), defaults.maxSteps(), defaults.timeoutMs());
        return Report.compilation(Compilation.compile(source, options));
    }

    private static Object conflicts(HttpExchange exchange) throws Exception {
        String grammar = body(exchange);
        Map<String, String> q = query(exchange);
        int expect = Integer.parseInt(q.getOrDefault("expect", "0"));
        boolean states = "1".equals(q.get("states"));
        boolean tables = "1".equals(q.get("tables"));
        return ConflictLab.toMap(ConflictLab.run(grammar, expect, states, tables));
    }

    private static Object list(String index) {
        List<Object> items = new ArrayList<>();
        for (String line : GrammarInfo.resource(index).split("\n")) {
            String[] parts = line.trim().split("\\|");
            if (parts.length >= 2) {
                items.add(Report.map("name", parts[0], "title", parts[1],
                        "expect", parts.length > 2 ? Integer.parseInt(parts[2]) : 0));
            }
        }
        return items;
    }

    private static Object text(HttpExchange exchange, String folder) {
        String name = query(exchange).getOrDefault("name", "");
        if (!name.matches("[A-Za-z0-9_\\-]+\\.(c|cup)")) {
            throw new IllegalArgumentException("Некоректна назва файлу");
        }
        return Report.map("name", name, "text", GrammarInfo.resource(folder + name));
    }

    private static void staticFile(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (path.equals("/")) {
            path = "/index.html";
        }
        if (path.contains("..")) {
            send(exchange, 400, "text/plain", new byte[0]);
            return;
        }
        try (InputStream in = WebServer.class.getResourceAsStream("/web" + path)) {
            if (in == null) {
                send(exchange, 404, "text/plain; charset=utf-8", "Not found".getBytes(StandardCharsets.UTF_8));
                return;
            }
            String ext = path.substring(path.lastIndexOf('.') + 1);
            send(exchange, 200, TYPES.getOrDefault(ext, "application/octet-stream"), in.readAllBytes());
        }
    }

    private static String body(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Map<String, String> query(HttpExchange exchange) {
        Map<String, String> result = new HashMap<>();
        String raw = exchange.getRequestURI().getRawQuery();
        if (raw == null) {
            return result;
        }
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                result.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
            }
        }
        return result;
    }

    private static void send(HttpExchange exchange, int status, String type, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        if (body.length > 0) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        }
        exchange.close();
    }
}
